package com.example.sporty.api

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Header

interface ApiService {
    @GET("city")
    fun getCities(
        @retrofit2.http.Query("country") country: String,
        @retrofit2.http.Query("limit") limit: Int,
        @Header("X-Api-Key") apiKey: String
    ): Call<List<City>>
}
