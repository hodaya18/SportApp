package com.example.sporty.Modules.Feed

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Spinner
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.sporty.Models.Post
import com.example.sporty.Modules.Adapters.PostAdapter
import com.example.sporty.R
import com.example.sporty.api.ApiService
import com.example.sporty.api.City
import com.example.sporty.dao.AppDatabase
import com.example.sporty.databinding.FragmentFeedBinding
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class FeedFragment : Fragment() {

    private var _binding: FragmentFeedBinding? = null
    private val binding get() = _binding!!
    private val db = FirebaseFirestore.getInstance()
    private lateinit var postAdapter: PostAdapter
    private var allPosts: List<Post> = emptyList()
    private lateinit var spinnerCities: Spinner
    private val apiKey = "VeXjtAWbz9fWN9krxW/ySQ==dRp1kcevJdQtywHa"
    private val sharedPreferencesName = "com.example.sporty.PREFERENCES"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentFeedBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        spinnerCities = binding.spinnerCities

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

        setupRecyclerView()
        setupSwipeRefreshLayout()
        loadCachedPosts()
        loadPosts()
        fetchCities()

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

    private fun fetchCities() {
        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.api-ninjas.com/v1/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val apiService = retrofit.create(ApiService::class.java)
        val call = apiService.getCities("IL", 15, apiKey)

        call.enqueue(object : Callback<List<City>> {
            override fun onResponse(call: Call<List<City>>, response: Response<List<City>>) {
                if (response.isSuccessful) {
                    val cities = response.body() ?: emptyList()
                    val cityNames = cities.map { it.name }
                    val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, cityNames)
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    spinnerCities.adapter = adapter
                    // Load saved city selection
                    val sharedPreferences = requireContext().getSharedPreferences(sharedPreferencesName, Context.MODE_PRIVATE)
                    val savedCity = sharedPreferences.getString("selected_city", null)
                    savedCity?.let {
                        val position = cityNames.indexOf(it)
                        if (position >= 0) {
                            spinnerCities.setSelection(position)
                        }
                    }
                } else {
                    Log.e("FeedFragment", "Failed to fetch cities: ${response.message()}")
                }
            }

            override fun onFailure(call: Call<List<City>>, t: Throwable) {
                Log.e("FeedFragment", "Error fetching cities", t)
            }
        })
    }

    override fun onPause() {
        super.onPause()
        // Save selected city
        val selectedCity = spinnerCities.selectedItem?.toString()
        selectedCity?.let {
            val sharedPreferences = requireContext().getSharedPreferences(sharedPreferencesName, Context.MODE_PRIVATE)
            with(sharedPreferences.edit()) {
                putString("selected_city", it)
                apply()
            }
        }
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