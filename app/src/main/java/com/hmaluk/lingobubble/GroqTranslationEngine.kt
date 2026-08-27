package com.hmaluk.lingobubble

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException

class GroqTranslationEngine(
    private val scope: CoroutineScope,
    private val sourceLang: String,
    private val targetLang: String,
    private val groqKey: String,
    private var sendIntervalMs: Long = 2000L
) : TranslationEngine {

    private val _translatedText = MutableStateFlow("")
    override val translatedText: StateFlow<String> = _translatedText.asStateFlow()

    private val _partialText = MutableStateFlow("")
    override val partialText: StateFlow<String> = _partialText.asStateFlow()

    private val _status = MutableStateFlow("Idle")
    override val status: StateFlow<String> = _status.asStateFlow()

    private val client = OkHttpClient()
    
    private val audioBuffer = ByteArrayOutputStream()
    private var lastSendTime = 0L
    
    private var isRunning = false

    fun updateInterval(newIntervalMs: Long) {
        sendIntervalMs = newIntervalMs
    }

    override fun start() {
        if (groqKey.isBlank()) {
            _status.value = "Error: Missing Groq Key"
            return
        }
        isRunning = true
        _status.value = "Connected (Groq)"
        lastSendTime = System.currentTimeMillis()
    }

    override fun stop() {
        isRunning = false
        _status.value = "Idle"
        synchronized(audioBuffer) {
            audioBuffer.reset()
        }
    }

    override fun streamAudio(data: ByteArray, length: Int) {
        if (!isRunning) return

        synchronized(audioBuffer) {
            audioBuffer.write(data, 0, length)
        }

        val currentTime = System.currentTimeMillis()
        if (currentTime - lastSendTime >= sendIntervalMs) {
            val dataToSend = synchronized(audioBuffer) {
                val bytes = audioBuffer.toByteArray()
                audioBuffer.reset()
                bytes
            }
            lastSendTime = currentTime
            if (dataToSend.size > 3200) { // At least 0.1s of audio
                sendToGroq(dataToSend)
            }
        }
    }

    private fun sendToGroq(pcmData: ByteArray) {
        val wavData = addWavHeader(pcmData)
        
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("model", "whisper-large-v3")
            .addFormDataPart("file", "audio.wav", 
                wavData.toRequestBody("audio/wav".toMediaType()))
            .build()

        val url = if (targetLang == "English") {
            "https://api.groq.com/openai/v1/audio/translations"
        } else {
            "https://api.groq.com/openai/v1/audio/transcriptions"
        }

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $groqKey")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("GroqEngine", "API Call failed", e)
                scope.launch {
                    _status.value = "Retrying Groq..."
                    delay(2000)
                    sendToGroq(pcmData)
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()
                if (response.isSuccessful && body != null) {
                    _status.value = "Connected (Groq)"
                    try {
                        val json = JSONObject(body)
                        val text = json.getString("text")
                        scope.launch {
                            if (text.isNotBlank()) {
                                _translatedText.value += "[$targetLang]: $text\n\n"
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("GroqEngine", "JSON Parse failed", e)
                    }
                } else {
                    Log.e("GroqEngine", "Error response: $body")
                    scope.launch { _status.value = "API Error" }
                }
            }
        })
    }

    private fun addWavHeader(pcmData: ByteArray): ByteArray {
        val totalAudioLen = pcmData.size.toLong()
        val totalDataLen = totalAudioLen + 36
        val longSampleRate = 16000L
        val channels = 1
        val byteRate = 16 * longSampleRate * channels / 8

        val header = ByteArray(44)
        header[0] = 'R'.toByte()
        header[1] = 'I'.toByte()
        header[2] = 'F'.toByte()
        header[3] = 'F'.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = (totalDataLen shr 8 and 0xff).toByte()
        header[6] = (totalDataLen shr 16 and 0xff).toByte()
        header[7] = (totalDataLen shr 24 and 0xff).toByte()
        header[8] = 'W'.toByte()
        header[9] = 'A'.toByte()
        header[10] = 'V'.toByte()
        header[11] = 'E'.toByte()
        header[12] = 'f'.toByte()
        header[13] = 'm'.toByte()
        header[14] = 't'.toByte()
        header[15] = ' '.toByte()
        header[16] = 16
        header[17] = 0
        header[18] = 0
        header[19] = 0
        header[20] = 1
        header[21] = 0
        header[22] = channels.toByte()
        header[23] = 0
        header[24] = (longSampleRate and 0xff).toByte()
        header[25] = (longSampleRate shr 8 and 0xff).toByte()
        header[26] = (longSampleRate shr 16 and 0xff).toByte()
        header[27] = (longSampleRate shr 24 and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = (byteRate shr 8 and 0xff).toByte()
        header[30] = (byteRate shr 16 and 0xff).toByte()
        header[31] = (byteRate shr 24 and 0xff).toByte()
        header[32] = (channels * 16 / 8).toByte()
        header[33] = 0
        header[34] = 16
        header[35] = 0
        header[36] = 'd'.toByte()
        header[37] = 'a'.toByte()
        header[38] = 't'.toByte()
        header[39] = 'a'.toByte()
        header[40] = (totalAudioLen and 0xff).toByte()
        header[41] = (totalAudioLen shr 8 and 0xff).toByte()
        header[42] = (totalAudioLen shr 16 and 0xff).toByte()
        header[43] = (totalAudioLen shr 24 and 0xff).toByte()

        val combined = ByteArray(header.size + pcmData.size)
        System.arraycopy(header, 0, combined, 0, header.size)
        System.arraycopy(pcmData, 0, combined, header.size, pcmData.size)
        return combined
    }
}
