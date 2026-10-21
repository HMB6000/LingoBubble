package com.hmaluk.lingobubble

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hmaluk.lingobubble.ui.theme.LingoBubbleTheme

data class MainUiState(
    val sourceLang: String,
    val targetLang: String,
    val selectedEngine: String,
    val groqRate: Float,
    val showVisualizer: Boolean,
    val azureKey: String,
    val azureRegion: String,
    val groqKey: String
)

class MainViewModel(private val settingsManager: SettingsManager) : ViewModel() {
    var uiState by mutableStateOf(MainUiState(
        sourceLang = settingsManager.sourceLang,
        targetLang = settingsManager.targetLang,
        selectedEngine = settingsManager.engineType,
        groqRate = settingsManager.groqRate,
        showVisualizer = settingsManager.showVisualizer,
        azureKey = settingsManager.azureKey,
        azureRegion = settingsManager.azureRegion,
        groqKey = settingsManager.groqKey
    ))
        private set

    fun updateSourceLang(lang: String) {
        settingsManager.sourceLang = lang
        uiState = uiState.copy(sourceLang = lang)
    }

    fun updateTargetLang(lang: String) {
        settingsManager.targetLang = lang
        uiState = uiState.copy(targetLang = lang)
    }

    fun updateEngine(engine: String) {
        settingsManager.engineType = engine
        uiState = uiState.copy(selectedEngine = engine)
    }

    fun updateGroqRate(rate: Float) {
        settingsManager.groqRate = rate
        uiState = uiState.copy(groqRate = rate)
    }

    fun updateShowVisualizer(show: Boolean) {
        settingsManager.showVisualizer = show
        uiState = uiState.copy(showVisualizer = show)
    }

    fun updateAzureKey(key: String) {
        settingsManager.azureKey = key
        uiState = uiState.copy(azureKey = key)
    }

    fun updateAzureRegion(region: String) {
        settingsManager.azureRegion = region
        uiState = uiState.copy(azureRegion = region)
    }

    fun updateGroqKey(key: String) {
        settingsManager.groqKey = key
        uiState = uiState.copy(groqKey = key)
    }
}

class MainViewModelFactory(private val settingsManager: SettingsManager) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(settingsManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class MainActivity : ComponentActivity() {

    private lateinit var settingsManager: SettingsManager

    private val requestAudioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Toast.makeText(this, "Audio permission granted", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Audio permission is required for translation", Toast.LENGTH_LONG).show()
        }
    }

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Overlay permission granted", Toast.LENGTH_SHORT).show()
        }
    }

    private val projectionPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            startFloatingService(result.resultCode, result.data!!)
        } else {
            Toast.makeText(this, "System audio capture permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsManager = SettingsManager(this)

        setContent {
            val viewModel: MainViewModel = viewModel(factory = MainViewModelFactory(settingsManager))
            val uiState = viewModel.uiState

            LingoBubbleTheme {
                // Auto-start if triggered from Tile
                LaunchedEffect(intent) {
                    if (intent?.getBooleanExtra("auto_start", false) == true) {
                        requestProjectionPermission()
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(
                        sourceLang = uiState.sourceLang,
                        targetLang = uiState.targetLang,
                        selectedEngine = uiState.selectedEngine,
                        groqRate = uiState.groqRate,
                        showVisualizer = uiState.showVisualizer,
                        azureKey = uiState.azureKey,
                        azureRegion = uiState.azureRegion,
                        groqKey = uiState.groqKey,
                        onSourceLangChange = viewModel::updateSourceLang,
                        onTargetLangChange = viewModel::updateTargetLang,
                        onEngineChange = viewModel::updateEngine,
                        onGroqRateChange = viewModel::updateGroqRate,
                        onVisualizerToggle = viewModel::updateShowVisualizer,
                        onAzureKeyChange = viewModel::updateAzureKey,
                        onAzureRegionChange = viewModel::updateAzureRegion,
                        onGroqKeyChange = viewModel::updateGroqKey,
                        onStartService = { requestProjectionPermission() },
                        onRequestAudioPermission = { requestAudioPermission() },
                        onRequestOverlayPermission = { requestOverlayPermission() }
                    )
                }
            }
        }
    }

    private fun requestAudioPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            Toast.makeText(this, "Audio permission already granted", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestOverlayPermission() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            overlayPermissionLauncher.launch(intent)
        } else {
            Toast.makeText(this, "Overlay permission already granted", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestProjectionPermission() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Please grant overlay permission first", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Please grant audio permission first", Toast.LENGTH_SHORT).show()
            return
        }

        val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projectionPermissionLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
    }

    private fun startFloatingService(resultCode: Int, data: Intent) {
        val intent = Intent(this, FloatingService::class.java).apply {
            putExtra("resultCode", resultCode)
            putExtra("data", data)
            putExtra("sourceLang", settingsManager.sourceLang)
            putExtra("targetLang", settingsManager.targetLang)
            putExtra("engineType", settingsManager.engineType)
            putExtra("groqRate", (settingsManager.groqRate * 1000).toLong())
            putExtra("showVisualizer", settingsManager.showVisualizer)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    sourceLang: String,
    targetLang: String,
    selectedEngine: String,
    groqRate: Float,
    showVisualizer: Boolean,
    azureKey: String,
    azureRegion: String,
    groqKey: String,
    onSourceLangChange: (String) -> Unit,
    onTargetLangChange: (String) -> Unit,
    onEngineChange: (String) -> Unit,
    onGroqRateChange: (Float) -> Unit,
    onVisualizerToggle: (Boolean) -> Unit,
    onAzureKeyChange: (String) -> Unit,
    onAzureRegionChange: (String) -> Unit,
    onGroqKeyChange: (String) -> Unit,
    onStartService: () -> Unit,
    onRequestAudioPermission: () -> Unit,
    onRequestOverlayPermission: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val audioGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    val overlayGranted = Settings.canDrawOverlays(context)

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.Language, 
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("LingoBubble", fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(12.dp))
            
            // Language Bridge Section
            LanguageBridge(
                sourceLang = sourceLang,
                targetLang = targetLang,
                onSourceClick = { onSourceLangChange(it) },
                onTargetClick = { onTargetLangChange(it) },
                onSwap = { 
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    val oldSource = sourceLang
                    onSourceLangChange(targetLang)
                    onTargetLangChange(oldSource)
                }
            )

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                "Setup Requirements",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            PermissionCard(
                title = "Audio Recording",
                description = "Required to capture system audio for translation",
                icon = Icons.Rounded.Mic,
                isGranted = audioGranted,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onRequestAudioPermission()
                }
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            PermissionCard(
                title = "Overlay Permission",
                description = "Required to show subtitles over other apps",
                icon = Icons.Rounded.Layers,
                isGranted = overlayGranted,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onRequestOverlayPermission()
                }
            )

            Spacer(modifier = Modifier.height(32.dp))

            EngineCard(
                selectedEngine = selectedEngine,
                groqRate = groqRate,
                onEngineChange = onEngineChange,
                onGroqRateChange = onGroqRateChange
            )

            Spacer(modifier = Modifier.height(24.dp))

            // API Settings Section
            ApiSettingsCard(
                azureKey = azureKey,
                azureRegion = azureRegion,
                groqKey = groqKey,
                onAzureKeyChange = onAzureKeyChange,
                onAzureRegionChange = onAzureRegionChange,
                onGroqKeyChange = onGroqKeyChange
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Visualizer Toggle Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Audio Visualizer", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = showVisualizer, 
                        onCheckedChange = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onVisualizerToggle(it)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onStartService()
                },
                modifier = Modifier.fillMaxWidth().height(64.dp),
                shape = RoundedCornerShape(20.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(28.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text("Start LingoBubble", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            }
            
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
fun LanguageBridge(
    sourceLang: String,
    targetLang: String,
    onSourceClick: (String) -> Unit,
    onTargetClick: (String) -> Unit,
    onSwap: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        LanguageTile(label = "From", lang = sourceLang, onSelect = onSourceClick, modifier = Modifier.weight(1f))
        
        IconButton(
            onClick = onSwap,
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .size(48.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(16.dp))
        ) {
            Icon(
                Icons.Rounded.SwapHoriz, 
                "Swap", 
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(28.dp)
            )
        }
        
        LanguageTile(label = "To", lang = targetLang, onSelect = onTargetClick, modifier = Modifier.weight(1f))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageTile(label: String, lang: String, onSelect: (String) -> Unit, modifier: Modifier) {
    var showSheet by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val sheetState = rememberModalBottomSheetState()
    
    Card(
        onClick = { 
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            showSheet = true 
        },
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f))
            Spacer(modifier = Modifier.height(4.dp))
            Text(lang, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
    
    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = sheetState
        ) {
            LanguagePickerContent {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onSelect(it)
                showSheet = false
            }
        }
    }
}

@Composable
fun LanguagePickerContent(onSelected: (String) -> Unit) {
    val languages = listOf(
        "English" to "🇺🇸", "Korean" to "🇰🇷", "Spanish" to "🇪🇸", 
        "French" to "🇫🇷", "Japanese" to "🇯🇵", "Chinese" to "🇨🇳", 
        "German" to "🇩🇪", "Italian" to "🇮🇹", "Portuguese" to "🇵🇹"
    )
    
    Column(modifier = Modifier.padding(bottom = 32.dp)) {
        Text(
            "Select Language", 
            style = MaterialTheme.typography.titleLarge, 
            modifier = Modifier.padding(24.dp),
            fontWeight = FontWeight.Bold
        )
        
        languages.forEach { (name, flag) ->
            ListItem(
                headlineContent = { Text(name, fontWeight = FontWeight.Medium) },
                leadingContent = { Text(flag, fontSize = 24.sp) },
                modifier = Modifier.clickable { onSelected(name) }
            )
        }
    }
}

@Composable
fun ApiSettingsCard(
    azureKey: String,
    azureRegion: String,
    groqKey: String,
    onAzureKeyChange: (String) -> Unit,
    onAzureRegionChange: (String) -> Unit,
    onGroqKeyChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { 
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    expanded = !expanded 
                }
            ) {
                Icon(Icons.Rounded.VpnKey, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(12.dp))
                Text("API Credentials", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Icon(
                    if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = null
                )
            }
            
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    Text("Azure Speech Service", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = azureKey,
                        onValueChange = onAzureKeyChange,
                        label = { Text("Azure Key") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = azureRegion,
                        onValueChange = onAzureRegionChange,
                        label = { Text("Azure Region (e.g. eastus)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    Text("Groq Cloud", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = groqKey,
                        onValueChange = onGroqKeyChange,
                        label = { Text("Groq API Key") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Your keys are stored locally on your device and are never shared.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun PermissionCard(
    title: String,
    description: String,
    icon: ImageVector,
    isGranted: Boolean,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isGranted) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f),
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isGranted) Icons.Rounded.Check else icon,
                        contentDescription = null,
                        tint = if (isGranted) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!isGranted) {
                Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EngineCard(
    selectedEngine: String,
    groqRate: Float,
    onEngineChange: (String) -> Unit,
    onGroqRateChange: (Float) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.SettingsSuggest, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(12.dp))
                Text("Translation Engine", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val engines = listOf("Azure", "Groq", "Whisper")
                engines.forEachIndexed { index, engine ->
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = engines.size),
                        onClick = { 
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onEngineChange(engine) 
                        },
                        selected = selectedEngine == engine,
                        label = { Text(engine, fontSize = 12.sp) }
                    )
                }
            }
            
            AnimatedVisibility(visible = selectedEngine == "Groq") {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Speed, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Update every ${groqRate.toInt()}s", style = MaterialTheme.typography.labelLarge)
                    }
                    Slider(
                        value = groqRate,
                        onValueChange = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onGroqRateChange(it)
                        },
                        valueRange = 2f..5f,
                        steps = 2
                    )
                }
            }
        }
    }
}
