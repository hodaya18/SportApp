package com.example.sporty.Modules.Feed

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Spinner
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.sporty.Models.Post
import com.example.sporty.Modules.Adapters.PostAdapter
import com.example.sporty.R
import com.example.sporty.dao.AppDatabase
import com.example.sporty.databinding.FragmentFeedBinding
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FeedFragment : Fragment() {

    private var _binding: FragmentFeedBinding? = null
    private val binding get() = _binding!!
    private val db = FirebaseFirestore.getInstance()
    private lateinit var postAdapter: PostAdapter
    private var allPosts: List<Post> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentFeedBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)


        binding.toolbarFeed.btnAddSSportyDate.setOnClickListener {
            val action =
                FeedFragmentDirections.actionFeedFragmentToAddPostFragment(
                    null
                )
            findNavController().navigate(action)
        }

        binding.toolbarFeed.btnProfile.setOnClickListener {
            findNavController().navigate(R.id.action_feedFragment_to_profileFragment)
        }
        binding.openMapsButton.setOnClickListener {
            try {
                findNavController().navigate(R.id.mapsFragment)
            } catch (e: Exception) {
                Log.e("AddPostFragment", "Error navigating to Maps Fragment: ${e.message}", e)
            }
        }

        setupRecyclerView()
        setupSwipeRefreshLayout()
        loadCachedPosts()
        loadPosts()

        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                if (!query.isNullOrEmpty()) {
                    searchPosts(query!!)
                }
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                if (!newText.isNullOrEmpty()) {
                    searchPosts(newText!!)
                } else {
                    postAdapter.submitList(allPosts) // Reset to all posts if search bar is empty
                }
                return false
            }
        })
    }


    override fun onPause() {
        super.onPause()
        // Save selected city
    }

    private fun setupRecyclerView() {
        postAdapter = PostAdapter(null, false, null)
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = postAdapter
        }
    }

    private fun setupSwipeRefreshLayout() {
        binding.swipeRefreshLayout.setOnRefreshListener {
            loadPosts()
        }
    }

    private fun loadCachedPosts() {
        lifecycleScope.launch {
            val postDao = AppDatabase.getDatabase(requireContext()).postDao()
            val cachedPosts = withContext(Dispatchers.IO) {
                postDao.getPostsByPostType()
            }
            allPosts = cachedPosts
            postAdapter.submitList(cachedPosts)
        }
    }

    private fun loadPosts() {
        db.collection("posts")
            .get()
            .addOnSuccessListener { result ->
                val posts = result.toObjects(Post::class.java)
                allPosts = posts
                postAdapter.submitList(posts) // Initial data set
                cachePosts(posts)
                binding.swipeRefreshLayout.isRefreshing = false // Stop the refresh animation
            }
            .addOnFailureListener { exception ->
                // Handle the error
                Log.e("FeedFragment", "Error getting documents: ", exception)
                binding.swipeRefreshLayout.isRefreshing = false // Stop the refresh animation
            }
    }

    private fun cachePosts(posts: List<Post>) {
        lifecycleScope.launch {
            val postDao = AppDatabase.getDatabase(requireContext()).postDao()
            withContext(Dispatchers.IO) {
                posts.forEach { post ->
                    postDao.insert(post)
                }
            }
        }
    }

    private fun searchPosts(query: String) {
        val filteredPosts = allPosts.filter { post ->
            // Search logic - check caption, location, and sport type
            post.caption.contains(query, ignoreCase = true) ||
                    post.location.contains(query, ignoreCase = true) ||
                    post.sportType.contains(query, ignoreCase = true)
        }
        postAdapter.submitList(filteredPosts) // Update adapter with filtered data
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}