package com.example.sporty.Modules.AddPost

import com.example.sporty.Modules.Maps.PickLocationActivity
import android.app.Activity
import android.app.DatePickerDialog
import android.app.TimePickerDialog
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
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.example.sporty.R
import com.example.sporty.databinding.FragmentAddPostBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.squareup.picasso.Callback
import com.squareup.picasso.Picasso
import java.util.Calendar
import java.util.UUID

class AddPostFragment : Fragment() {

    private var _binding: FragmentAddPostBinding? = null
    private val binding get() = _binding!!

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var storage: FirebaseStorage
    private var imageUri: Uri? = null

    private val LOCATION_PICKER_REQUEST = 1001

    private val args: AddPostFragmentArgs by navArgs()

    private lateinit var pickImageLauncher: ActivityResultLauncher<Intent>
    private lateinit var locationPickerLauncher: ActivityResultLauncher<Intent>  // Declare the launcher for location picker
    private var selectedLatitude: Double? = null
    private var selectedLongitude: Double? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddPostBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        storage = FirebaseStorage.getInstance()

        // Initialize the ActivityResultLauncher for Location Picker
        locationPickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val data = result.data
                val address = data?.getStringExtra("address")
                selectedLatitude = data?.getDoubleExtra("latitude", 0.0)
                selectedLongitude = data?.getDoubleExtra("longitude", 0.0)

                // Set the selected location to TextView
                binding.tvLocation.text = address
            }
        }

        // Handle Image Picker
        pickImageLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                    imageUri = result.data!!.data
                    binding.ivSelectedImage.setImageURI(imageUri)
                    binding.ivSelectedImage.visibility = View.VISIBLE
                }
            }

        // Set up the title and button text
        val titleText = if (args.post == null) "Add Post" else "Update Post"
        binding.btnAddPost.text = titleText
        binding.tvTitle.text = titleText

        if (args.post !== null) {
            args.post?.let { post ->
                binding.etSportType.setText(post.sportType)
                binding.tvLocation.setText(post.location)
                binding.etCaption.setText(post.caption)
                binding.tvDateTime.text = post.sportyDate.toString()
                imageUri = Uri.parse(post.imageUrl)
                selectedLongitude = post.longitude
                selectedLatitude = post.latitude

                Picasso.get()
                    .load(imageUri)
                    .placeholder(R.drawable.app_logo)
                    .error(R.drawable.app_logo)
                    .into(binding.ivSelectedImage, object : Callback {
                        override fun onSuccess() {
                            Log.d("ProfileFragment", "Image loaded successfully")
                        }

                        override fun onError(e: Exception?) {
                            Log.e("ProfileFragment", "Error loading image", e)
                        }
                    })
            }
        }

        // Image Picker Button
        binding.btnSelectImage.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            pickImageLauncher.launch(intent)
        }

        binding.btnPickDateTime.setOnClickListener {
            showDateTimePicker()
        }

        // Handle Add Post Button
        binding.btnAddPost.setOnClickListener {
            val sportType = binding.etSportType.text.toString().trim()
            val location = binding.tvLocation.text.toString().trim()
            val caption = binding.etCaption.text.toString().trim()
            val postId = args.post?.postId
            val sportyDate = binding.tvDateTime.text.toString().trim()

            if (sportType.isEmpty() || location.isEmpty() || caption.isEmpty() || imageUri == null || sportyDate.isEmpty()) {
                Toast.makeText(context, "All fields are required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Show the progress overlay
            binding.progressOverlay.visibility = View.VISIBLE

            uploadPost(sportType, location, caption, sportyDate , postId)
        }

        // Back Button Logic
        binding.btnBack.setOnClickListener {
            if (args.post !== null) {
                findNavController().navigate(R.id.action_addPost_to_profileFragment)
            } else {
                findNavController().navigate(R.id.action_addPost_to_feedFragment)
            }
        }

        // Location Picker Button
        binding.btnPickLocation.setOnClickListener {
            openLocationPicker()
        }
    }

    private fun openLocationPicker() {
        try {
            Log.d("AddPostFragment", "Trying to open location picker")

            val intent = Intent(requireContext(), PickLocationActivity::class.java)
            locationPickerLauncher.launch(intent)

        } catch (e: Exception) {
            Log.e("AddPostFragment", "Error opening location picker", e)
            Toast.makeText(context, "Failed to open location picker: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showDateTimePicker() {
        val currentDateTime = Calendar.getInstance()
        DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                TimePickerDialog(
                    requireContext(),
                    { _, hourOfDay, minute ->
                        Calendar.getInstance().apply {
                            set(year, month, dayOfMonth, hourOfDay, minute)
                        }
                        binding.tvDateTime.text =
                            "${dayOfMonth}/${month + 1}/${year} ${hourOfDay}:${minute}"
                    },
                    currentDateTime.get(Calendar.HOUR_OF_DAY),
                    currentDateTime.get(Calendar.MINUTE),
                    true
                ).show()
            },
            currentDateTime.get(Calendar.YEAR),
            currentDateTime.get(Calendar.MONTH),
            currentDateTime.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun uploadPost(
        sportType: String,
        location: String,
        caption: String,
        sportDate: String,
        postId: String?
    ) {
        val currImageUri = post()?.imageUrl

        if (currImageUri !== null && currImageUri.toString() == imageUri.toString()) {
            savePostToFirestore(
                sportType,
                location,
                caption,
                currImageUri.toString(),
                postId,
                sportDate,
                selectedLatitude,
                selectedLongitude
            )
        } else {
            val storageRef = storage.reference.child("posts/${UUID.randomUUID()}")
            imageUri?.let {
                storageRef.putFile(it)
                    .addOnSuccessListener { taskSnapshot ->
                        taskSnapshot.storage.downloadUrl.addOnSuccessListener { uri ->
                            // Invalidate the cache for the old image URL
                            currImageUri?.let { oldUri ->
                                Picasso.get().invalidate(oldUri)
                            }
                            // Proceed to save the post with the new image URL
                            savePostToFirestore(
                                sportType,
                                location,
                                caption,
                                uri.toString(),
                                postId,
                                sportDate,
                                selectedLatitude,
                                selectedLongitude
                            )
                        }
                    }
                    .addOnFailureListener {
                        // Hide the progress overlay
                        binding.progressOverlay.visibility = View.GONE
                        Toast.makeText(
                            context,
                            "Image upload failed: ${it.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
            } ?: run {
                // Hide the progress overlay
                binding.progressOverlay.visibility = View.GONE
                Toast.makeText(context, "Image URI is null", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun savePostToFirestore(
        sportType: String,
        location: String,
        caption: String,
        imageUrl: String,
        postId: String?,
        sportDate: String,
        latitude: Double?,
        longitude: Double?
    ) {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            // Hide the progress overlay
            binding.progressOverlay.visibility = View.GONE
            Toast.makeText(context, "User not authenticated", Toast.LENGTH_SHORT).show()
            return
        }

//        val initLikes = 0
//        val initComments = emptyList<Comment>()
        val currPostId = postId ?: UUID.randomUUID().toString()

        val post = hashMapOf(
            "sportType" to sportType,
            "location" to location,
            "caption" to caption,
            "imageUrl" to imageUrl,
            "userId" to currentUser.uid,
            "sportyDate" to sportDate,
            "postId" to currPostId,
            "latitude" to latitude,
            "longitude" to longitude
        )

        val successText =
            if (postId != null) "Post updated successfully" else "Post added successfully"
        val failureText =
            if (postId != null) "Post update failed" else "Post upload failed"

        firestore.collection("posts").document(currPostId).set(post)
            .addOnSuccessListener {
                // Hide the progress overlay
                binding.progressOverlay.visibility = View.GONE
                Toast.makeText(context, successText, Toast.LENGTH_SHORT).show()
                // Invalidate the cache for the new image URL to force reload
                Picasso.get().invalidate(imageUrl)
                if (args.post != null) {
                    findNavController().navigate(R.id.action_addPost_to_profileFragment)
                } else {
                    findNavController().navigate(R.id.action_addPost_to_feedFragment)
                }
            }
            .addOnFailureListener {
                // Hide the progress overlay
                binding.progressOverlay.visibility = View.GONE
                Toast.makeText(context, failureText, Toast.LENGTH_SHORT).show()
            }
    }

    private fun post() = args.post


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
