package com.example.sporty.Modules.Maps

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.LatLngBounds
import android.location.Geocoder
import java.util.Locale
import com.example.sporty.R


class PickLocationActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private var selectedMarker: Marker? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pick_location)

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        // Default location to center
        val defaultLocation = LatLng(32.08, 34.78) // Tel Aviv example
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, 12f))

        mMap.setOnMapClickListener { latLng ->
            selectedMarker?.remove() // Remove previous marker
            selectedMarker = mMap.addMarker(
                MarkerOptions()
                    .position(latLng)
                    .title("Selected Location")
            )

            val geocoder = Geocoder(this, Locale.getDefault())
            val addressList = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1)
            val address = addressList?.firstOrNull()?.getAddressLine(0) ?: "Unknown location"

            val resultIntent = Intent().apply {
                putExtra("address", address)
                putExtra("latitude", latLng.latitude)
                putExtra("longitude", latLng.longitude)
            }
            setResult(Activity.RESULT_OK, resultIntent)
            finish()
        }
    }
}
