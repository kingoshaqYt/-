package com.example.data

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class SupabaseSlider(
    val id: Int? = null,
    val title: String? = null,
    val description: String? = null,
    val image_url: String? = null,       // fallback/existing
    val image: String? = null,           // parsed from real column "image"
    val link: String? = null,            // parsed from real column "link"
    val badge: String? = null
) {
    val resolvedImageUrl: String?
        get() = image ?: image_url
}

@JsonClass(generateAdapter = true)
data class LiveActivityItem(
    val id: String? = null,
    val user: String? = null,
    val action: String? = null,
    val time: String? = null,
    val type: String? = null // "success", "warning", "info"
)
