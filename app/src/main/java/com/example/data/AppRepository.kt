package com.example.data

import android.util.Log
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

class AppRepository {

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .build()

    private val supabaseRetrofit = Retrofit.Builder()
        .baseUrl("https://dcqtckwlczypsgsjksdd.supabase.co/rest/v1/")
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    private val firebaseRetrofit = Retrofit.Builder()
        .baseUrl("https://pubg-unban-accounts-2025-default-rtdb.asia-southeast1.firebasedatabase.app/")
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    private val supabaseApi = supabaseRetrofit.create(SupabaseApi::class.java)
    private val firebaseApi = firebaseRetrofit.create(FirebaseApi::class.java)

    private val supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImRjcXRja3dsY3p5cHNnc2prc2RkIiwicm9sZSI6ImFub24iLCJpYXQiOjE3MzUwMzA3OTUsImV4cCI6MjA1MDYwNjc5NX0.ekW2qfOAo0wzKei-ycVVmVGFugvrayvbh4v4k2YAku8"

    suspend fun fetchSupabaseSliders(): List<SupabaseSlider> {
        return try {
            val list = supabaseApi.getSliders(
                apiKey = supabaseKey,
                bearerToken = "Bearer $supabaseKey"
            )
            Log.d("AppRepository", "Successfully fetched ${list.size} sliders from Supabase REST.")
            if (list.isNotEmpty()) list else getFallbackSliders()
        } catch (e: Exception) {
            Log.e("AppRepository", "Error fetching from Supabase: ${e.message}", e)
            getFallbackSliders()
        }
    }

    suspend fun fetchFirebaseLiveActivity(): List<LiveActivityItem> {
        return try {
            val list = firebaseApi.getLiveActivity()
            if (list != null && list.isNotEmpty()) {
                list
            } else {
                getFallbackActivityFeed()
            }
        } catch (e: Exception) {
            Log.e("AppRepository", "Error fetching from Firebase: ${e.message}", e)
            getFallbackActivityFeed()
        }
    }

    private fun getFallbackSliders(): List<SupabaseSlider> {
        return listOf(
            SupabaseSlider(
                id = 1,
                title = "Account Recovery Guide",
                description = "Master the steps for full visual security and visual recovery loops.",
                image_url = "https://images.unsplash.com/photo-1563986768609-322da13575f3?w=800&auto=format&fit=crop",
                badge = "GUIDE"
            ),
            SupabaseSlider(
                id = 2,
                title = "Email Recovery System",
                description = "Advanced secondary layer mailbox bypass and visual extraction keys.",
                image_url = "https://images.unsplash.com/photo-1544005313-94ddf0286df2?w=800&auto=format&fit=crop",
                badge = "EMAIL"
            ),
            SupabaseSlider(
                id = 3,
                title = "In-Game Recovery Guide",
                description = "Level up your gaming security parameters via live token unbans.",
                image_url = "https://images.unsplash.com/photo-1511512578047-dfb367046420?w=800&auto=format&fit=crop",
                badge = "GAMING"
            ),
            SupabaseSlider(
                id = 4,
                title = "Community Updates",
                description = "Connect with 15k+ unbanned players exchanging recovery logs daily.",
                image_url = "https://images.unsplash.com/photo-1522071820081-009f0129c71c?w=800&auto=format&fit=crop",
                badge = "SOCIAL"
            ),
            SupabaseSlider(
                id = 5,
                title = "Latest Features",
                description = "Discover the dynamic VisionOS ambient light engine sliders.",
                image_url = "https://images.unsplash.com/photo-1550751827-4bd374c3f58b?w=800&auto=format&fit=crop",
                badge = "UPDATED"
            )
        )
    }

    private fun getFallbackActivityFeed(): List<LiveActivityItem> {
        return listOf(
            LiveActivityItem(
                id = "1",
                user = "Support center",
                action = "Replied to ticket ID #99824",
                time = "2 mins ago",
                type = "success"
            ),
            LiveActivityItem(
                id = "2",
                user = "Recovery server 4",
                action = "Completed account unban on PUBG-ID (42231)",
                time = "5 mins ago",
                type = "success"
            ),
            LiveActivityItem(
                id = "3",
                user = "System monitor",
                action = "Automatic cron DB mirror initiated successfully.",
                time = "10 mins ago",
                type = "info"
            ),
            LiveActivityItem(
                id = "4",
                user = "Admin mod",
                action = "Uploaded new visual assets to storage bucket.",
                time = "45 mins ago",
                type = "warning"
            )
        )
    }
}
