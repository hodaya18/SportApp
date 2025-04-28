package com.example.sporty.Modules.Profile

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.sporty.Models.Post
import com.example.sporty.Modules.Adapters.PostAdapter
import com.example.sporty.R
import com.example.sporty.databinding.FragmentProfileBinding
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.UploadTask
import com.squareup.picasso.Callback
import com.squareup.picasso.Picasso
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.IOException

class ProfileFragment : Fragment(), PostAdapter.PostAdapterCallback {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private var imageUri: Uri? = null
    private var isEditingProfile: Boolean = false

    private lateinit var pickImageLauncher: ActivityResultLauncher<Intent>
    private lateinit var postAdapter: PostAdapter
    private val db = FirebaseFirestore.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        // Load profile details
        loadProfileDetails()

        binding.toolbarProfile.btnAddSSportyDate.setOnClickListener {
            val action =
                ProfileFragmentDirections.actionProfileFragmentToAddPostFragment(
                    null
                )
            findNavController().navigate(action)
        }

        binding.toolbarProfile.ivLogo.setOnClickListener {
            findNavController().navigate(R.id.action_profileFragment_to_feedFragment)
        }

        postAdapter = PostAdapter(findNavController(), true, this)
        binding.recyclerViewPosts.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = postAdapter
        }
        loadUserPosts()


        pickImageLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                    imageUri = result.data!!.data
                    binding.ivProfileImage.setImageURI(imageUri)
                }
            }

        binding.btnUpdateImage.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            pickImageLauncher.launch(intent)
        }

        binding.btnEdit.setOnClickListener {
            isEditingProfile = !isEditingProfile
            if (isEditingProfile) {
                // handle to edit mode
                binding.tvUserName.visibility = View.GONE
                binding.etUserName.visibility = View.VISIBLE
                binding.btnUpdateImage.visibility = View.VISIBLE
                binding.etUserName.setText(binding.tvUserName.text)
//                binding.btnEdit.text = getString(R.string.save)
            } else {
                // Save profile details
                val newUserName = binding.etUserName.text.toString().trim()
                if (newUserName.isEmpty()) {
                    Toast.makeText(context, "Name cannot be empty", Toast.LENGTH_SHORT).show()
                } else {
                    saveProfileDetails(newUserName)
                }
           }
        }

        binding.btnLogout.setOnClickListener {
            try {
                auth.signOut()
                findNavController().navigate(R.id.action_profileFragment_to_signInFragment)
                Toast.makeText(context, "Logout successful", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e("ProfileFragment", "Error during logout", e)
                Toast.makeText(context, "Logout failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }


    private fun loadUserPosts() {
        val currentUser = auth.currentUser
        currentUser?.let {
            db.collection("posts")
                .whereEqualTo("userId", it.uid)
                .get()
                .addOnSuccessListener { querySnapshot: QuerySnapshot ->
                    val posts = querySnapshot.toObjects(Post::class.java)
                    postAdapter.submitList(posts)
                }
                .addOnFailureListener { e ->
                    Log.e("ProfileFragment", "Error loading user posts", e)
                    Toast.makeText(context, "Failed to load posts", Toast.LENGTH_SHORT).show()
                }
        }
    }


    private fun loadProfileDetails() {
        val currentUser = auth.currentUser
        currentUser?.let {
            val name = it.displayName
            val imageUrl = it.photoUrl
            binding.tvUserName.text = name
            if (imageUrl != null) {
                Picasso.get()
                    .load(imageUrl)
                    .placeholder(R.drawable.app_logo)
                    .error(R.drawable.app_logo)
                    .into(binding.ivProfileImage, object : Callback {
                        override fun onSuccess() {
                            Log.d("ProfileFragment", "Image loaded successfully")
                        }

                        override fun onError(e: Exception?) {
                            Log.e("ProfileFragment", "Error loading image", e)
                        }
                    })
            } else {
                Picasso.get()
                    .load(R.drawable.app_logo)
                    .into(binding.ivProfileImage)
            }
        }
    }

    private suspend fun uploadPhotoAndGetUrl(uri: Uri): String? {
        val storageRef = FirebaseStorage.getInstance().reference
        val userPhotoRef = storageRef.child("user_photos/${auth.currentUser?.uid}.jpg")

        return try {
            // Upload the file
            val uploadTask: UploadTask = userPhotoRef.putFile(uri)
            uploadTask.await()

            // Get the download URL
            val downloadUrlTask: Task<Uri> = userPhotoRef.downloadUrl
            val downloadUrl: Uri = downloadUrlTask.await()
            downloadUrl.toString()
        } catch (e: IOException) {
            e.printStackTrace()
            Log.e("UploadError", "Upload failed: ${e.message}", e)
            null
        }
    }

    private fun saveProfileDetails(newUserName: String) {
        lifecycleScope.launch {
            val currentUser = auth.currentUser
            currentUser?.let {
                val downloadUrl = imageUri?.let { uri -> uploadPhotoAndGetUrl(uri) }
                val profileUpdates = UserProfileChangeRequest.Builder()
                    .setDisplayName(newUserName)
                    .apply {
                        if (downloadUrl != null) {
                            setPhotoUri(Uri.parse(downloadUrl))
                        }
                    }
                    .build()

                it.updateProfile(profileUpdates)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            // Update Firestore with new user details
                            val userRef = firestore.collection("users").document(currentUser.uid)
                            val userData = hashMapOf(
                                "displayName" to newUserName,
                                "photoUrl" to downloadUrl
                            )
                            userRef.set(userData)

                            binding.tvUserName.text = newUserName
                            binding.tvUserName.visibility = View.VISIBLE
                            binding.etUserName.visibility = View.GONE
                            binding.btnUpdateImage.visibility = View.GONE
                            if (downloadUrl != null) {
                                Picasso.get().load(downloadUrl).into(binding.ivProfileImage)
                            }
                            loadUserPosts()
                            Toast.makeText(
                                context,
                                "Profile updated successfully",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            Toast.makeText(context, "Failed to update profile"+ task.exception?.message, Toast.LENGTH_SHORT)
                                .show()
                        }
                    }
            }
        }
    }

    override fun onPostDeleted(post: Post) {
        loadUserPosts()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}