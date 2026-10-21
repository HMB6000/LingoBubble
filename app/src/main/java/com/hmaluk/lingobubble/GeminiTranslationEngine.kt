package com.hmaluk.lingobubble

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject

class GeminiTranslationEngine(
    private val scope: CoroutineScope,
    private val sourceLang: String,
    private val targetLang: String,
    private val geminiKey: String
) : TranslationEngine {

    private val _translatedText = MutableStateFlow("")
    override val translatedText: StateFlow<String> = _translatedText.asStateFlow()

    private val _partialText = MutableStateFlow("")
    override val partialText: StateFlow<String> = _partialText.asStateFlow()

    private val _status = MutableStateFlow("Idle")
    override val status: StateFlow<String> = _status.asStateFlow()

    private val client = OkHttpClient()
    private var webSocket: WebSocket? = null
    
    private var isRunning = false
    private var retryCount = 0
    private val MAX_RETRIES = 3

    override fun start() {
        if (geminiKey.isBlank()) {
            _status.value = "Error: Missing Gemini Key"
            return
        }
        isRunning = true
        retryCount = 0
        connect()
    }

    private fun connect() {
        _status.value = if (retryCount > 0) "Reconnecting ($retryCount)..." else "Connecting to Gemini..."
        
        val url = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent?key=$geminiKey"
        val request = Request.Builder().url(url).build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("GeminiEngine", "WebSocket Connected")
                _status.value = "Connected (Gemini)"
                retryCount = 0

                // Send setup message
                val setupMessage = JSONObject().apply {
                    put("setup", JSONObject().apply {
                        put("model", "models/gemini-2.0-flash-exp")
                        put("systemInstruction", JSONObject().apply {
                            put("parts", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("text", "You are a real-time voice translator. Listen to the audio which is in $sourceLang, and instantly translate it to $targetLang. Output ONLY the translated text, nothing else. Wait until a complete sentence or phrase is formed.")
                                })
                            })
                        })
                    })
                }
                webSocket.send(setupMessage.toString())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val json = JSONObject(text)
                    if (json.has("serverContent")) {
                        val serverContent = json.getJSONObject("serverContent")
                        if (serverContent.has("modelTurn")) {
                            val parts = serverContent.getJSONObject("modelTurn").getJSONArray("parts")
                            var currentText = ""
                            for (i in 0 until parts.length()) {
                                val part = parts.getJSONObject(i)
                                if (part.has("text")) {
                                    currentText += part.getString("text")
                                }
                            }
                            
                            if (currentText.isNotBlank()) {
                                scope.launch(Dispatchers.Main) {
                                    _translatedText.value += "[$targetLang]: $currentText\n"
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("GeminiEngine", "Parse error: $text", e)
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d("GeminiEngine", "WebSocket Closed: $reason")
                if (isRunning) {
                    _status.value = "Disconnected"
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("GeminiEngine", "WebSocket Failure", t)
                if (isRunning && retryCount < MAX_RETRIES) {
                    retryCount++
                    scope.launch(Dispatchers.Main) {
                        _status.value = "Retrying in 2s..."
                        delay(2000)
                        connect()
                    }
                } else if (isRunning) {
                    _status.value = "Error: Connection failed"
                }
            }
        })
    }

    override fun stop() {
        isRunning = false
        webSocket?.close(1000, "User stopped")
        webSocket = null
        _status.value = "Idle"
    }

    override fun streamAudio(data: ByteArray, length: Int) {
        if (!isRunning || webSocket == null) return

        // Base64 encode the raw PCM 16-bit 16kHz audio chunk
        val base64Audio = Base64.encodeToString(data, 0, length, Base64.NO_WRAP)
        
        val message = JSONObject().apply {
            put("realtimeInput", JSONObject().apply {
                put("mediaChunks", JSONArray().apply {
                    put(JSONObject().apply {
                        put("mimeType", "audio/pcm;rate=16000")
                        put("data", base64Audio)
                    })
                })
            })
        }
        
        webSocket?.send(message.toString())
    }
}
