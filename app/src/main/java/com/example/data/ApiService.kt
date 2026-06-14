package com.example.data

import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query
import retrofit2.http.Url

interface SupabaseApi {
    @GET("slider")
    suspend fun getSliders(
        @Header("apikey") apiKey: String,
        @Header("Authorization") bearerToken: String,
        @Query("select") select: String = "*"
    ): List<SupabaseSlider>
}

interface FirebaseApi {
    @GET(".json")
    suspend fun getFullDatabase(): Map<String, Any>?

    @GET("live_activity.json")
    suspend fun getLiveActivity(): List<LiveActivityItem>?
}
