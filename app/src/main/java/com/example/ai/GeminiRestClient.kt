package com.example.ai

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import com.example.personality.MyraaPersonality
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

class GeminiRestClient {
    companion object {
        private const val TAG = "GeminiRestClient"
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"
        private const val TEXT_MODEL = "gemini-3.5-flash"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun generateResponse(
        prompt: String,
        imageBitmap: Bitmap? = null,
        systemInstruction: String = MyraaPersonality.BASE_SYSTEM_INSTRUCTION,
        apiKey: String = BuildConfig.GEMINI_API_KEY
    ): Result<String> = withContext(Dispatchers.IO) {
        val key = apiKey.ifBlank { BuildConfig.GEMINI_API_KEY }
        if (key.isBlank() || key == "MY_GEMINI_API_KEY") {
            return@withContext Result.failure(Exception("Gemini API key is not configured."))
        }

        try {
            val partsArray = JSONArray()

            // Optional image part
            if (imageBitmap != null) {
                val outputStream = ByteArrayOutputStream()
                imageBitmap.compress(Bitmap.CompressFormat.JPEG, 75, outputStream)
                val base64Image = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
                partsArray.put(JSONObject().apply {
                    put("inlineData", JSONObject().apply {
                        put("mimeType", "image/jpeg")
                        put("data", base64Image)
                    })
                })
            }

            // Text prompt part
            partsArray.put(JSONObject().apply {
                put("text", prompt)
            })

            val requestBody = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", partsArray)
                    })
                })
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", systemInstruction)
                        })
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.7)
                    put("maxOutputTokens", 1024)
                })
            }

            val endpoint = "$BASE_URL/$TEXT_MODEL:generateContent?key=$key"
            val request = Request.Builder()
                .url(endpoint)
                .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val responseString = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                Log.e(TAG, "API call failed (${response.code}): $responseString")
                return@withContext Result.failure(Exception("Gemini error ${response.code}: $responseString"))
            }

            val json = JSONObject(responseString)
            val candidates = json.optJSONArray("candidates")
            val firstCandidate = candidates?.optJSONObject(0)
            val content = firstCandidate?.optJSONObject("content")
            val parts = content?.optJSONArray("parts")
            val textPart = parts?.optJSONObject(0)?.optString("text", "") ?: ""

            Result.success(textPart)
        } catch (e: Exception) {
            Log.e(TAG, "Exception during Gemini REST call: ${e.message}", e)
            Result.failure(e)
        }
    }
}
