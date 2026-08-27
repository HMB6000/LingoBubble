package com.hmaluk.lingobubble

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.Toast
import java.nio.ByteBuffer
import java.nio.ByteOrder
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class FloatingService : Service() {

    companion object {
        var isRunning = false
            private set
    }

    private lateinit var windowManager: WindowManager
    private lateinit var composeView: ComposeView
    private lateinit var params: WindowManager.LayoutParams
    private val lifecycleOwner = FloatingServiceLifecycleOwner()
    
    private lateinit var settingsManager: SettingsManager
    
    private var translationEngine = mutableStateOf<TranslationEngine?>(null)
    private var isRecordingState = mutableStateOf(false)
    private var sourceLang = mutableStateOf("Korean")
    private var targetLang = mutableStateOf("English")
    private var engineType = mutableStateOf("Azure")
    private var groqRateMs = 2000L

    // Appearance & State
    private var fontSize = mutableStateOf(20f)
    private var opacity = mutableStateOf(0.6f)
    private var showBackground = mutableStateOf(true)
    private var isSettingsVisible = mutableStateOf(false)
    private var isMinimized = mutableStateOf(false)
    private var audioLevel = mutableStateOf(0f)
    private var showVisualizer = mutableStateOf(true)

    private var mediaProjection: MediaProjection? = null
    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null

    private lateinit var settingsView: ComposeView
    private lateinit var settingsParams: WindowManager.LayoutParams

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val resultCode = intent?.getIntExtra("resultCode", Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        val data = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra("data", Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra("data")
        }
        
        // Use saved settings or intent extras
        sourceLang.value = intent?.getStringExtra("sourceLang") ?: settingsManager.sourceLang
        targetLang.value = intent?.getStringExtra("targetLang") ?: settingsManager.targetLang
        engineType.value = intent?.getStringExtra("engineType") ?: settingsManager.engineType
        groqRateMs = intent?.getLongExtra("groqRate", settingsManager.groqRate.toLong() * 1000) ?: (settingsManager.groqRate.toLong() * 1000)
        showVisualizer.value = intent?.getBooleanExtra("showVisualizer", settingsManager.showVisualizer) ?: settingsManager.showVisualizer

        if (resultCode == Activity.RESULT_OK && data != null) {
            val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val mp = mediaProjectionManager.getMediaProjection(resultCode, data)
            
            mp?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    stopSystemCapture()
                    isRecordingState.value = false
                }
            }, Handler(Looper.getMainLooper()))
            
            mediaProjection = mp
            initializeEngine()
            
            Toast.makeText(this, "LingoBubble Ready", Toast.LENGTH_SHORT).show()
        }

        return super.onStartCommand(intent, flags, startId)
    }

    private fun initializeEngine() {
        translationEngine.value?.stop()
        translationEngine.value = when (engineType.value) {
            "Groq" -> GroqTranslationEngine(
                lifecycleOwner.lifecycleScope, 
                sourceLang.value, 
                targetLang.value, 
                settingsManager.groqKey,
                groqRateMs
            )
            "Whisper" -> WhisperOfflineEngine(this, lifecycleOwner.lifecycleScope, sourceLang.value, targetLang.value)
            else -> AzureTranslationEngine(
                lifecycleOwner.lifecycleScope, 
                sourceLang.value, 
                targetLang.value, 
                settingsManager.azureKey, 
                settingsManager.azureRegion
            )
        }
        if (isRecordingState.value) {
            translationEngine.value?.start()
        }
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        settingsManager = SettingsManager(this)
        
        // Load initial appearance
        fontSize.value = settingsManager.fontSize
        opacity.value = settingsManager.opacity
        showBackground.value = settingsManager.showBackground
        
        startAsForeground()

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        
        lifecycleOwner.onCreate()
        lifecycleOwner.onStart()
        lifecycleOwner.onResume()

        setupMainOverlay()
        setupSettingsOverlay()
    }

    private fun setupMainOverlay() {
        composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)

            setContent {
                MaterialTheme {
                    val engine = translationEngine.value
                    SubtitleOverlay(
                        translatedTextFlow = engine?.translatedText ?: kotlinx.coroutines.flow.MutableStateFlow(""),
                        partialTextFlow = engine?.partialText ?: kotlinx.coroutines.flow.MutableStateFlow(""),
                        statusFlow = engine?.status ?: kotlinx.coroutines.flow.MutableStateFlow("Waiting..."),
                        isRecording = isRecordingState.value,
                        sourceLang = sourceLang.value,
                        targetLang = targetLang.value,
                        fontSize = fontSize.value,
                        opacity = opacity.value,
                        showBackground = showBackground.value,
                        engineType = engineType.value,
                        audioLevel = audioLevel.value,
                        showVisualizer = showVisualizer.value,
                        isMinimized = isMinimized.value,
                        onToggleRecording = { toggleRecording() },
                        onClose = { stopSelf() },
                        onSettingsClick = { toggleSettings() },
                        onMinimizeClick = { toggleMinimize() },
                        onEngineToggle = { toggleEngine() },
                        onDrag = { dx, dy ->
                            params.x += dx
                            params.y += dy
                            windowManager.updateViewLayout(composeView, params)
                        }
                    )
                }
            }
        }

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 1500
        }

        windowManager.addView(composeView, params)
    }

    private fun setupSettingsOverlay() {
        settingsView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(lifecycleOwner)
            setViewTreeViewModelStoreOwner(lifecycleOwner)
            setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            setContent {
                MaterialTheme {
                    QuickSettingsOverlay(
                        fontSize = fontSize.value,
                        opacity = opacity.value,
                        showBackground = showBackground.value,
                        onFontSizeChange = { 
                            fontSize.value = it
                            settingsManager.fontSize = it
                        },
                        onOpacityChange = { 
                            opacity.value = it
                            settingsManager.opacity = it
                        },
                        onBackgroundToggle = { 
                            showBackground.value = it
                            settingsManager.showBackground = it
                        },
                        onClose = { toggleSettings() }
                    )
                }
            }
        }

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        settingsParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }
    }

    private fun toggleSettings() {
        if (isSettingsVisible.value) {
            windowManager.removeView(settingsView)
            isSettingsVisible.value = false
        } else {
            windowManager.addView(settingsView, settingsParams)
            isSettingsVisible.value = true
        }
    }

    private fun toggleMinimize() {
        isMinimized.value = !isMinimized.value
        if (isMinimized.value) {
            params.width = WindowManager.LayoutParams.WRAP_CONTENT
        } else {
            params.width = WindowManager.LayoutParams.MATCH_PARENT
        }
        windowManager.updateViewLayout(composeView, params)
    }

    private fun toggleEngine() {
        engineType.value = if (engineType.value == "Azure") "Groq" else "Azure"
        settingsManager.engineType = engineType.value
        initializeEngine()
    }

    private fun toggleRecording() {
        val engine = translationEngine.value ?: return
        if (isRecordingState.value) {
            stopSystemCapture()
            engine.stop()
            isRecordingState.value = false
        } else {
            engine.start()
            startSystemCapture()
            isRecordingState.value = true
        }
    }

    @SuppressLint("MissingPermission")
    private fun startSystemCapture() {
        val mp = mediaProjection
        val engine = translationEngine.value
        if (mp == null || engine == null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val config = AudioPlaybackCaptureConfiguration.Builder(mp)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                .build()

            val bufferSize = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            
            audioRecord = AudioRecord.Builder()
                .setAudioPlaybackCaptureConfig(config)
                .setAudioFormat(AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(16000)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                    .build())
                .setBufferSizeInBytes(bufferSize * 2)
                .build()

            audioRecord?.startRecording()
            
            recordingJob = lifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
                val buffer = ShortArray(bufferSize / 2)
                val byteBuffer = ByteBuffer.allocate(bufferSize).order(ByteOrder.LITTLE_ENDIAN)
                
                while (isActive && isRecordingState.value) {
                    val read = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                    if (read > 0) {
                        // Calculate RMS level for visualizer (optimized loop)
                        var sum = 0.0
                        for (i in 0 until read) {
                            val sample = buffer[i]
                            sum += sample.toDouble() * sample
                        }
                        val rms = Math.sqrt(sum / read)
                        audioLevel.value = (rms / 32768.0).toFloat().coerceIn(0f, 1f)

                        // Convert to ByteArray efficiently
                        byteBuffer.clear()
                        for (i in 0 until read) {
                            byteBuffer.putShort(buffer[i])
                        }
                        engine.streamAudio(byteBuffer.array(), read * 2)
                    }
                }
            }
        }
    }

    private fun stopSystemCapture() {
        recordingJob?.cancel()
        recordingJob = null
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        audioLevel.value = 0f
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        stopSystemCapture()
        translationEngine.value?.stop()
        mediaProjection?.stop()
        if (::composeView.isInitialized) windowManager.removeView(composeView)
        if (isSettingsVisible.value) windowManager.removeView(settingsView)
        lifecycleOwner.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startAsForeground() {
        val channelId = "lingobubble_service"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "LingoBubble Service", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("LingoBubble active")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(1, notification)
        }
    }
}

class FloatingServiceLifecycleOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = store

    fun onCreate() {
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }
    fun onStart() { lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START) }
    fun onResume() { lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME) }
    fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
    }
}
