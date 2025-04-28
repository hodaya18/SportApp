package com.example.sporty.Modules.Maps


import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.sporty.R
import com.google.firebase.firestore.FirebaseFirestore

import com.example.sporty.databinding.FragmentMapsBinding
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.firebase.firestore.Query
import com.example.sporty.Models.Post
import com.example.sporty.Modules.Adapters.PostAdapter



class MapsFragment : Fragment(), OnMapReadyCallback {

    private var _binding: FragmentMapsBinding? = null
    private val binding get() = _binding!!
    private lateinit var map: GoogleMap
    private var allPosts: List<Post> = emptyList()
    private val db = FirebaseFirestore.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMapsBinding.inflate(inflater, container, false)
        return binding.root
    }

    private fun fetchPosts() {
        try {
            db.collection("posts")
                .orderBy("sportyDate", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener { result ->
                    val posts = result.toObjects(Post::class.java)
                    allPosts = posts
                    // After fetching the posts, update the markers on the map
                    if (::map.isInitialized) {
                        addMarkersToMap()
                    }
                }
                .addOnFailureListener { exception ->
                    Log.e("MapsFragment", "Error getting documents: ", exception)
                }
        } catch (e: Exception) {
            Log.e("MapsFragment", "Unexpected error occurred while fetching posts", e)
        }
    }

    private fun addMarkersToMap() {
        allPosts.forEach { post ->
            val latLng = LatLng(post.latitude, post.longitude)
            map.addMarker(
                MarkerOptions()
                    .position(latLng)
                    .title("${post.caption} ${post.sportType}")
                    .snippet("${post.sportyDate}") 

            )
        }

        if (allPosts.isNotEmpty()) {
            val firstPostLocation = LatLng(allPosts[0].latitude, allPosts[0].longitude)
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(firstPostLocation, 13f))
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as? SupportMapFragment
        mapFragment?.getMapAsync(this)
        try {
            fetchPosts()
        } catch (e: Exception) {
            Log.e("MapsFragment", "Unexpected error occurred while fetching posts", e)
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        // Add markers after the map is ready
        if (allPosts.isNotEmpty()) {
            addMarkersToMap()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
