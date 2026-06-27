package com.example.data

import android.util.Log
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit
import com.example.BuildConfig

data class GeminiPart(
    val text: String? = null
)

data class GeminiContent(
    val parts: List<GeminiPart>
)

data class GeminiRequest(
    val contents: List<GeminiContent>,
    val systemInstruction: GeminiContent? = null
)

data class GeminiCandidate(
    val content: GeminiContent? = null
)

data class GeminiResponse(
    val candidates: List<GeminiCandidate>? = null
)

interface GeminiApi {
    @retrofit2.http.POST("v1beta/models/gemini-1.5-flash:generateContent")
    suspend fun generateContent(
        @retrofit2.http.Query("key") apiKey: String,
        @retrofit2.http.Body request: GeminiRequest
    ): GeminiResponse
}

object GeminiClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    val api: GeminiApi by lazy {
        retrofit.create(GeminiApi::class.java)
    }

    suspend fun callGemini(systemPrompt: String, userPrompt: String): String {
        val apiKey = BuildConfig.GEMINI_API_KEY
        
        val strictSystemPrompt = """
            $systemPrompt
            
            CRITICAL DIRECTIVE:
            - Only provide real data based on the user's input.
            - Do not generate fake, dummy, or hypothetical data.
            - If you do not know the answer, or if the information is not provided in the user's input or the target profile, say 'Data not found'.
        """.trimIndent()

        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            Log.w("GeminiClient", "Gemini API key is blank or contains default placeholder. Falling back.")
            return getFallbackResponse(strictSystemPrompt, userPrompt)
        }

        val request = GeminiRequest(
            contents = listOf(
                GeminiContent(parts = listOf(GeminiPart(text = userPrompt)))
            ),
            systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = strictSystemPrompt)))
        )

        return try {
            val response = api.generateContent(apiKey, request)
            val text = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            text ?: "I am sorry, but I do not have enough context to answer that right now."
        } catch (e: Exception) {
            Log.e("GeminiClient", "Error calling Gemini: ${e.message}", e)
            getFallbackResponse(strictSystemPrompt, userPrompt)
        }
    }

    private fun getFallbackResponse(systemPrompt: String, userPrompt: String): String {
        val lowerPrompt = userPrompt.lowercase()
        val isVance = systemPrompt.contains("Vance")
        
        // Extract real input parameters from the systemPrompt if available
        val nameRegex = "Name:\\s*([^,\\n]+)".toRegex()
        val uidRegex = "UID:\\s*([^,\\n]+)".toRegex()
        val reasonRegex = "Ban Reason:\\s*([^.\\n]+)".toRegex()
        
        val targetName = nameRegex.find(systemPrompt)?.groupValues?.get(1)?.trim() ?: ""
        val targetUid = uidRegex.find(systemPrompt)?.groupValues?.get(1)?.trim() ?: ""
        val banReason = reasonRegex.find(systemPrompt)?.groupValues?.get(1)?.trim() ?: ""

        val agentTag = if (isVance) "🛡️ [Agent Vance]" else "🌐 [Agent Marcus]"

        return when {
            lowerPrompt.contains("name") || lowerPrompt.contains("player") -> {
                if (targetName.isNotBlank() && targetName != "unknown") {
                    "$agentTag: The current target player name is '$targetName'."
                } else {
                    "Data not found"
                }
            }
            lowerPrompt.contains("uid") || lowerPrompt.contains("id") -> {
                if (targetUid.isNotBlank() && targetUid != "unknown") {
                    "$agentTag: The current target PUBG Mobile UID is '$targetUid'."
                } else {
                    "Data not found"
                }
            }
            lowerPrompt.contains("reason") || lowerPrompt.contains("ban") -> {
                if (banReason.isNotBlank() && banReason != "unknown") {
                    "$agentTag: The current ban reason is '$banReason'."
                } else {
                    "Data not found"
                }
            }
            lowerPrompt.contains("who are you") || lowerPrompt.contains("agent") || lowerPrompt.contains("advisor") -> {
                if (isVance) {
                    "$agentTag: I am your Sandbox Specialist Advisor. Current real input config: Name: $targetName, UID: $targetUid, Ban Reason: $banReason."
                } else {
                    "$agentTag: I am your Sync Specialist Advisor. Current real input config: Name: $targetName, UID: $targetUid, Ban Reason: $banReason."
                }
            }
            else -> {
                "$agentTag: I am unable to verify that request at the moment. Please provide more context or try asking a different question."
            }
        }
    }
}
