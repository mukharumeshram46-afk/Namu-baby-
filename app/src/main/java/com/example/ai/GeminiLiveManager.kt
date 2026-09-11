package com.example.ai

import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import com.example.personality.MyraaPersonality
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    ERROR
}

data class ToolCall(
    val id: String,
    val name: String,
    val args: Map<String, Any?>
)

class GeminiLiveManager(
    private val scope: CoroutineScope,
    private val onAudioReceived: (ByteArray) -> Unit,
    private val onInterrupted: () -> Unit,
    private val onTranscriptionReceived: (role: String, text: String) -> Unit,
    private val onToolCallReceived: (ToolCall) -> Unit,
    private val onError: (String) -> Unit
) {
    companion object {
        private const val TAG = "GeminiLiveManager"
        private const val LIVE_WS_URL =
            "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent"
        private const val LIVE_MODEL = "models/gemini-2.0-flash-exp"
    }

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private var webSocket: WebSocket? = null
    private var client: OkHttpClient? = null
    private var reconnectJob: Job? = null
    private var shouldKeepConnected = false
    private var activeApiKey: String = ""

    private fun getClient(): OkHttpClient {
        return client ?: OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS) // infinite for websocket
            .writeTimeout(30, TimeUnit.SECONDS)
            .pingInterval(15, TimeUnit.SECONDS)
            .build().also { client = it }
    }

    fun connect(apiKey: String = BuildConfig.GEMINI_API_KEY, systemPrompt: String = MyraaPersonality.BASE_SYSTEM_INSTRUCTION) {
        activeApiKey = apiKey.ifBlank { BuildConfig.GEMINI_API_KEY }
        if (activeApiKey.isBlank() || activeApiKey == "MY_GEMINI_API_KEY") {
            Log.w(TAG, "No valid Gemini API key configured in secrets.")
            _connectionState.value = ConnectionState.ERROR
            onError("Gemini API key is not configured. Please add GEMINI_API_KEY in Secrets or Settings.")
            return
        }

        shouldKeepConnected = true
        _connectionState.value = ConnectionState.CONNECTING

        val requestUrl = "$LIVE_WS_URL?key=$activeApiKey"
        val request = Request.Builder()
            .url(requestUrl)
            .build()

        webSocket = getClient().newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                Log.i(TAG, "Gemini Live WebSocket opened successfully")
                _connectionState.value = ConnectionState.CONNECTED
                sendSetupMessage(ws, systemPrompt)
            }

            override fun onMessage(ws: WebSocket, text: String) {
                handleIncomingMessage(text)
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "WebSocket closing: $code / $reason")
                ws.close(1000, null)
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "WebSocket closed: $code / $reason")
                _connectionState.value = ConnectionState.DISCONNECTED
                if (shouldKeepConnected) {
                    scheduleReconnect(systemPrompt)
                }
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket error: ${t.message}", t)
                _connectionState.value = ConnectionState.ERROR
                onError("Connection error: ${t.message ?: "Failed connecting to Gemini Live"}")
                if (shouldKeepConnected) {
                    scheduleReconnect(systemPrompt)
                }
            }
        })
    }

    private fun scheduleReconnect(systemPrompt: String) {
        reconnectJob?.cancel()
        reconnectJob = scope.launch(Dispatchers.IO) {
            _connectionState.value = ConnectionState.RECONNECTING
            delay(3000)
            if (shouldKeepConnected) {
                Log.i(TAG, "Attempting reconnection to Gemini Live...")
                connect(activeApiKey, systemPrompt)
            }
        }
    }

    fun disconnect() {
        shouldKeepConnected = false
        reconnectJob?.cancel()
        reconnectJob = null
        try {
            webSocket?.close(1000, "User disconnected")
        } catch (e: Exception) {
            Log.w(TAG, "Error closing websocket: ${e.message}")
        }
        webSocket = null
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    /**
     * Send PCM 16kHz audio chunk encoded as base64
     */
    fun sendAudioChunk(pcmData: ByteArray) {
        val ws = webSocket ?: return
        if (_connectionState.value != ConnectionState.CONNECTED) return

        try {
            val base64Data = Base64.encodeToString(pcmData, Base64.NO_WRAP)
            val json = JSONObject().apply {
                put("realtimeInput", JSONObject().apply {
                    put("mediaChunks", JSONArray().apply {
                        put(JSONObject().apply {
                            put("mimeType", "audio/pcm;rate=16000")
                            put("data", base64Data)
                        })
                    })
                })
            }
            ws.send(json.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Error sending audio chunk: ${e.message}")
        }
    }

    /**
     * Send real-time image / screen vision JPEG frame encoded as base64
     */
    fun sendVideoFrame(jpegData: ByteArray) {
        val ws = webSocket ?: return
        if (_connectionState.value != ConnectionState.CONNECTED) return

        try {
            val base64Data = Base64.encodeToString(jpegData, Base64.NO_WRAP)
            val json = JSONObject().apply {
                put("realtimeInput", JSONObject().apply {
                    put("mediaChunks", JSONArray().apply {
                        put(JSONObject().apply {
                            put("mimeType", "image/jpeg")
                            put("data", base64Data)
                        })
                    })
                })
            }
            ws.send(json.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Error sending video frame: ${e.message}")
        }
    }

    /**
     * Send tool execution response back to Gemini Live
     */
    fun sendToolResponse(callId: String, name: String, output: Map<String, Any?>) {
        val ws = webSocket ?: return
        try {
            val outputJson = JSONObject(output)
            val json = JSONObject().apply {
                put("toolResponse", JSONObject().apply {
                    put("functionResponses", JSONArray().apply {
                        put(JSONObject().apply {
                            put("id", callId)
                            put("name", name)
                            put("response", JSONObject().apply {
                                put("output", outputJson)
                            })
                        })
                    })
                })
            }
            ws.send(json.toString())
            Log.i(TAG, "Sent toolResponse for $name ($callId)")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending tool response: ${e.message}")
        }
    }

    private fun sendSetupMessage(ws: WebSocket, systemPrompt: String) {
        try {
            val setupJson = JSONObject().apply {
                put("setup", JSONObject().apply {
                    put("model", LIVE_MODEL)
                    put("generationConfig", JSONObject().apply {
                        put("responseModalities", JSONArray().apply {
                            put("AUDIO")
                        })
                        put("speechConfig", JSONObject().apply {
                            put("voiceConfig", JSONObject().apply {
                                put("prebuiltVoiceConfig", JSONObject().apply {
                                    put("voiceName", "Aoede")
                                })
                            })
                        })
                    })
                    put("systemInstruction", JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", systemPrompt)
                            })
                        })
                    })
                    put("tools", JSONArray().apply {
                        put(JSONObject().apply {
                            put("functionDeclarations", buildToolDeclarations())
                        })
                    })
                })
            }
            ws.send(setupJson.toString())
            Log.i(TAG, "Sent setup packet to Gemini Live")
        } catch (e: Exception) {
            Log.e(TAG, "Failed sending setup: ${e.message}", e)
        }
    }

    private fun handleIncomingMessage(text: String) {
        try {
            val json = JSONObject(text)

            // 1. Check for serverContent
            if (json.has("serverContent")) {
                val serverContent = json.getJSONObject("serverContent")

                // Barge-in / Interruption signal
                if (serverContent.optBoolean("interrupted", false)) {
                    Log.i(TAG, "Model response interrupted by user speech")
                    onInterrupted()
                }

                // Model audio chunk
                if (serverContent.has("modelTurn")) {
                    val modelTurn = serverContent.getJSONObject("modelTurn")
                    val parts = modelTurn.optJSONArray("parts")
                    if (parts != null && parts.length() > 0) {
                        for (i in 0 until parts.length()) {
                            val part = parts.getJSONObject(i)
                            // Audio payload
                            if (part.has("inlineData")) {
                                val inlineData = part.getJSONObject("inlineData")
                                val dataBase64 = inlineData.optString("data", "")
                                if (dataBase64.isNotBlank()) {
                                    val audioBytes = Base64.decode(dataBase64, Base64.NO_WRAP)
                                    onAudioReceived(audioBytes)
                                }
                            }
                            // Text transcription
                            if (part.has("text")) {
                                val modelText = part.getString("text")
                                onTranscriptionReceived("model", modelText)
                            }
                        }
                    }
                }

                // User turn transcription
                if (serverContent.has("userTurn")) {
                    val userTurn = serverContent.getJSONObject("userTurn")
                    val parts = userTurn.optJSONArray("parts")
                    if (parts != null && parts.length() > 0) {
                        for (i in 0 until parts.length()) {
                            val part = parts.getJSONObject(i)
                            if (part.has("text")) {
                                onTranscriptionReceived("user", part.getString("text"))
                            }
                        }
                    }
                }
            }

            // 2. Check for toolCall
            if (json.has("toolCall")) {
                val toolCallObj = json.getJSONObject("toolCall")
                val fCalls = toolCallObj.optJSONArray("functionCalls")
                if (fCalls != null) {
                    for (i in 0 until fCalls.length()) {
                        val fc = fCalls.getJSONObject(i)
                        val callId = fc.optString("id", "")
                        val name = fc.getString("name")
                        val argsObj = fc.optJSONObject("args")
                        val argsMap = mutableMapOf<String, Any?>()
                        argsObj?.keys()?.forEach { key ->
                            argsMap[key] = argsObj.get(key)
                        }
                        onToolCallReceived(ToolCall(callId, name, argsMap))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling incoming live message: ${e.message}", e)
        }
    }

    private fun buildToolDeclarations(): JSONArray {
        val declarations = JSONArray()

        fun addDecl(name: String, desc: String, props: Map<String, String>, req: List<String>) {
            val decl = JSONObject().apply {
                put("name", name)
                put("description", desc)
                put("parameters", JSONObject().apply {
                    put("type", "OBJECT")
                    put("properties", JSONObject().apply {
                        props.forEach { (k, v) ->
                            put(k, JSONObject().apply {
                                put("type", "STRING")
                                put("description", v)
                            })
                        }
                    })
                    put("required", JSONArray(req))
                })
            }
            declarations.put(decl)
        }

        // Android System Tools
        addDecl(
            "android_openApp",
            "Launches an application by package name or common app name (e.g. youtube, whatsapp, chrome, settings).",
            mapOf("packageName" to "Target package name or app name, e.g. com.google.android.youtube or youtube"),
            listOf("packageName")
        )
        addDecl(
            "android_clickElement",
            "Clicks an interactive element on the active screen matching target text, viewId, or coordinates.",
            mapOf("targetText" to "Visible text or description of the element to click", "viewId" to "Optional resource ID"),
            listOf("targetText")
        )
        addDecl(
            "android_typeText",
            "Types text into the active focused input field.",
            mapOf("text" to "Text to enter into field"),
            listOf("text")
        )
        addDecl(
            "android_scroll",
            "Scrolls screen content in direction.",
            mapOf("direction" to "Scroll direction: UP, DOWN, LEFT, RIGHT"),
            listOf("direction")
        )
        addDecl(
            "android_systemAction",
            "Executes Android system navigation action.",
            mapOf("action" to "BACK, HOME, RECENTS, NOTIFICATIONS, LOCK"),
            listOf("action")
        )
        addDecl(
            "android_readScreenNodes",
            "Reads all visible UI text and interactive components from the current screen.",
            emptyMap(),
            emptyList()
        )
        addDecl(
            "takeQuickPhoto",
            "Snaps a quick photo using Camera to analyze what is in front of the device.",
            mapOf("facing" to "FRONT or BACK"),
            listOf("facing")
        )
        addDecl(
            "getLocationContext",
            "Fetches device GPS location coordinates and place context.",
            emptyMap(),
            emptyList()
        )
        addDecl(
            "getDeviceHealth",
            "Inspects battery percentage, temperature, RAM usage and OEM power state.",
            emptyMap(),
            emptyList()
        )
        // Browser Tools
        addDecl(
            "browserOpen",
            "Opens URL in Myraa's integrated tabbed browser.",
            mapOf("url" to "Web address to open"),
            listOf("url")
        )
        addDecl(
            "browserSearch",
            "Performs search inside active browser engine.",
            mapOf("query" to "Search query text"),
            listOf("query")
        )
        addDecl(
            "browserMediaControl",
            "Controls video media playback in browser (play, pause, volume, skip, mute).",
            mapOf("action" to "play, pause, volume, mute, skip"),
            listOf("action")
        )
        // Memory & Personality Tools
        addDecl(
            "saveCustomMemory",
            "Persists user memory fact into durable database core.",
            mapOf(
                "category" to "Category: identity, preference, goal, project, relationship, emotional, behavior",
                "text" to "Concise declarative fact statement"
            ),
            listOf("category", "text")
        )
        addDecl(
            "changeBackground",
            "Shifts visual aura theme color.",
            mapOf("color" to "violet, crimson, emerald, celestial, gold, rose, charcoal"),
            listOf("color")
        )

        return declarations
    }
}
