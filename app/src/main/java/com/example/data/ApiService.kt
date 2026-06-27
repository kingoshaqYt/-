package com.example.data

import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query
import retrofit2.http.Url

interface SupabaseApi {
    @GET("slider")
    suspend fun getSliders(
        @retrofit2.http.Header("apikey") apiKey: String,
        @retrofit2.http.Header("Authorization") bearerToken: String,
        @retrofit2.http.Query("order") order: String = "id.desc",
        @retrofit2.http.Query("select") select: String = "*"
    ): List<SupabaseSlider>

    @retrofit2.http.DELETE("slider")
    suspend fun deleteSlider(
        @retrofit2.http.Header("apikey") apiKey: String,
        @retrofit2.http.Header("Authorization") bearerToken: String,
        @retrofit2.http.Query("image") imageEq: String
    )

    @retrofit2.http.POST("slider")
    suspend fun insertSlider(
        @retrofit2.http.Header("apikey") apiKey: String,
        @retrofit2.http.Header("Authorization") bearerToken: String,
        @retrofit2.http.Header("Prefer") prefer: String = "return=representation",
        @retrofit2.http.Body slider: Map<String, String>
    )

    @retrofit2.http.POST("feedback")
    suspend fun insertFeedback(
        @retrofit2.http.Header("apikey") apiKey: String,
        @retrofit2.http.Header("Authorization") bearerToken: String,
        @retrofit2.http.Header("Prefer") prefer: String = "return=representation",
        @retrofit2.http.Body feedback: Map<String, Any?>
    )
}

interface FirebaseApi {
    @GET(".json")
    suspend fun getFullDatabase(): Map<String, Any>?

    @GET("live_activity.json")
    suspend fun getLiveActivity(): List<LiveActivityItem>?

    @retrofit2.http.PUT("reclaim_profiles/{uid}.json")
    suspend fun saveProfile(
        @retrofit2.http.Path("uid") uid: String,
        @retrofit2.http.Body profile: Map<String, Any?>
    ): Map<String, Any?>?

    @retrofit2.http.GET("reclaim_profiles/{uid}.json")
    suspend fun getProfile(
        @retrofit2.http.Path("uid") uid: String
    ): Map<String, Any?>?

    @retrofit2.http.PUT("cases/{caseId}.json")
    suspend fun saveCase(
        @retrofit2.http.Path("caseId") caseId: String,
        @retrofit2.http.Body caseData: Map<String, Any?>
    ): Map<String, Any?>?

    @retrofit2.http.GET("cases.json")
    suspend fun getAllCases(): Map<String, Map<String, Any?>>?

    @retrofit2.http.PUT("cases/{caseId}/messages/{msgId}.json")
    suspend fun saveCaseMessage(
        @retrofit2.http.Path("caseId") caseId: String,
        @retrofit2.http.Path("msgId") msgId: String,
        @retrofit2.http.Body msgData: Map<String, Any?>
    ): Map<String, Any?>?

    @retrofit2.http.GET("cases/{caseId}/messages.json")
    suspend fun getCaseMessages(
        @retrofit2.http.Path("caseId") caseId: String
    ): Map<String, Map<String, Any?>>?

    @retrofit2.http.PUT("updates/{updateId}.json")
    suspend fun saveUpdate(
        @retrofit2.http.Path("updateId") updateId: String,
        @retrofit2.http.Body updateData: Map<String, Any?>
    ): Map<String, Any?>?

    @retrofit2.http.DELETE("updates/{updateId}.json")
    suspend fun deleteUpdate(
        @retrofit2.http.Path("updateId") updateId: String
    ): Any?

    @retrofit2.http.GET("updates.json")
    suspend fun getUpdates(): Map<String, Map<String, Any?>>?
}
