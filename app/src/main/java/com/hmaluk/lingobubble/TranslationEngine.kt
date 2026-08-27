package com.hmaluk.lingobubble

import kotlinx.coroutines.flow.StateFlow

interface TranslationEngine {
    val translatedText: StateFlow<String>
    val partialText: StateFlow<String>
    val status: StateFlow<String>
    
    fun start()
    fun stop()
    fun streamAudio(data: ByteArray, length: Int)
}
