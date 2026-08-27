package com.hmaluk.lingobubble

import android.util.Log
import com.microsoft.cognitiveservices.speech.*
import com.microsoft.cognitiveservices.speech.audio.*
import com.microsoft.cognitiveservices.speech.translation.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AzureTranslationEngine(
    private val scope: CoroutineScope,
    private val sourceLang: String,
    private val targetLang: String,
    private val azureKey: String,
    private val azureRegion: String
) : TranslationEngine {

    private val _translatedText = MutableStateFlow("")
    override val translatedText: StateFlow<String> = _translatedText.asStateFlow()

    private val _status = MutableStateFlow("Idle")
    override val status: StateFlow<String> = _status.asStateFlow()

    private val _partialText = MutableStateFlow("")
    override val partialText: StateFlow<String> = _partialText.asStateFlow()

    private var recognizer: TranslationRecognizer? = null
    private var pushStream: PushAudioInputStream? = null
    
    private var retryCount = 0
    private val MAX_RETRIES = 3

    private fun getLanguageCode(lang: String): String {
        return when (lang) {
            "Korean" -> "ko-KR"
            "English" -> "en-US"
            "Spanish" -> "es-ES"
            "French" -> "fr-FR"
            "Japanese" -> "ja-JP"
            "Chinese" -> "zh-CN"
            "German" -> "de-DE"
            else -> "en-US"
        }
    }

    private fun getTargetLanguageCode(lang: String): String {
        return when (lang) {
            "Korean" -> "ko"
            "English" -> "en"
            "Spanish" -> "es"
            "French" -> "fr"
            "Japanese" -> "ja"
            "Chinese" -> "zh-Hans"
            "German" -> "de"
            else -> "en"
        }
    }

    override fun start() {
        if (azureKey.isBlank()) {
            _status.value = "Error: Missing Azure Key"
            return
        }
        retryCount = 0
        connect()
    }

    private fun connect() {
        try {
            _status.value = if (retryCount > 0) "Reconnecting ($retryCount)..." else "Connecting to Azure..."
            
            // Clean up previous if any
            recognizer?.stopContinuousRecognitionAsync()
            recognizer?.close()
            pushStream?.close()

            val config = SpeechTranslationConfig.fromSubscription(azureKey, azureRegion)
            config.speechRecognitionLanguage = getLanguageCode(sourceLang)
            config.addTargetLanguage(getTargetLanguageCode(targetLang))

            pushStream = AudioInputStream.createPushStream()
            val audioConfig = AudioConfig.fromStreamInput(pushStream)

            recognizer = TranslationRecognizer(config, audioConfig)

            recognizer?.recognizing?.addEventListener { _, e ->
                val result = e.result
                if (result.reason == ResultReason.TranslatingSpeech) {
                    val translations = result.translations
                    val targetText = translations.get(getTargetLanguageCode(targetLang))
                    if (!targetText.isNullOrBlank()) {
                        _partialText.value = "[$targetLang]: $targetText"
                    }
                }
            }

            recognizer?.recognized?.addEventListener { _, e ->
                val result = e.result
                if (result.reason == ResultReason.TranslatedSpeech) {
                    _partialText.value = "" // Clear partials
                    val sourceText = result.text
                    val translations = result.translations
                    val targetText = translations.get(getTargetLanguageCode(targetLang))
                    
                    if (!sourceText.isNullOrBlank() && !targetText.isNullOrBlank()) {
                        val line = "[$sourceLang]: $sourceText\n[$targetLang]: $targetText\n"
                        _translatedText.value += line + "\n"
                    }
                }
            }

            recognizer?.canceled?.addEventListener { _, e ->
                Log.e("AzureEngine", "Canceled: ${e.errorDetails}")
                if (e.reason == CancellationReason.Error && retryCount < MAX_RETRIES) {
                    retryCount++
                    scope.launch {
                        _status.value = "Retrying in 2s..."
                        delay(2000)
                        connect()
                    }
                } else {
                    _status.value = "Error: ${e.reason}"
                }
            }

            recognizer?.sessionStarted?.addEventListener { _, _ ->
                Log.d("AzureEngine", "Session started")
                _status.value = "Connected (Azure)"
                retryCount = 0
            }

            recognizer?.sessionStopped?.addEventListener { _, _ ->
                Log.d("AzureEngine", "Session stopped")
                _status.value = "Idle"
            }

            recognizer?.startContinuousRecognitionAsync()
        } catch (e: Exception) {
            Log.e("AzureEngine", "Start failed", e)
            _status.value = "Error: ${e.message}"
        }
    }

    override fun stop() {
        recognizer?.stopContinuousRecognitionAsync()
        recognizer?.close()
        recognizer = null
        pushStream?.close()
        pushStream = null
        _status.value = "Idle"
    }

    override fun streamAudio(data: ByteArray, length: Int) {
        // We still use the full array for safety with the SDK
        pushStream?.write(data.copyOf(length))
    }
}
