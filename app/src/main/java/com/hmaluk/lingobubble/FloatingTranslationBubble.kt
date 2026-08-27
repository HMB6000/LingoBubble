package com.hmaluk.lingobubble

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.StateFlow

@Composable
fun SubtitleOverlay(
    translatedTextFlow: StateFlow<String>,
    partialTextFlow: StateFlow<String>,
    statusFlow: StateFlow<String>,
    isRecording: Boolean,
    sourceLang: String,
    targetLang: String,
    fontSize: Float,
    opacity: Float,
    showBackground: Boolean,
    engineType: String,
    audioLevel: Float, // 0.0 to 1.0
    showVisualizer: Boolean,
    isMinimized: Boolean,
    onToggleRecording: () -> Unit,
    onClose: () -> Unit,
    onSettingsClick: () -> Unit,
    onMinimizeClick: () -> Unit,
    onEngineToggle: () -> Unit,
    onDrag: (Int, Int) -> Unit
) {
    val translatedText by translatedTextFlow.collectAsState()
    val partialText by partialTextFlow.collectAsState()
    val status by statusFlow.collectAsState()
    val haptic = LocalHapticFeedback.current
    
    val displayLines = (translatedText.split("\n").filter { it.isNotBlank() } + partialText)
        .takeLast(2)
        .joinToString("\n")

    Box(
        modifier = Modifier
            .then(if (isMinimized) Modifier.wrapContentSize() else Modifier.fillMaxWidth())
            .wrapContentHeight()
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                ) { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount.x.toInt(), dragAmount.y.toInt())
                }
            }
    ) {
        AnimatedContent(
            targetState = isMinimized,
            transitionSpec = {
                fadeIn() + scaleIn() togetherWith fadeOut() + scaleOut()
            },
            label = "MinimizedToggle"
        ) { minimized ->
            if (minimized) {
                MinimizedBubble(isRecording) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onMinimizeClick()
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Control Bar
                    Surface(
                        modifier = Modifier.wrapContentSize(),
                        shape = RoundedCornerShape(24.dp),
                        color = Color.Black.copy(alpha = 0.5f),
                        tonalElevation = 8.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onToggleRecording()
                            }, modifier = Modifier.size(32.dp)) {
                                Icon(
                                    imageVector = if (isRecording) Icons.Rounded.Mic else Icons.Rounded.MicOff,
                                    contentDescription = "Audio",
                                    tint = if (isRecording) Color.Red else Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            if (showVisualizer && isRecording) {
                                AudioVisualizer(audioLevel)
                                Spacer(modifier = Modifier.width(4.dp))
                            }

                            Text(
                                text = "$sourceLang → $targetLang",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 6.dp)
                            )

                            // Engine Toggle
                            Surface(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onEngineToggle()
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = Color.Cyan.copy(alpha = 0.2f),
                                modifier = Modifier.height(24.dp).padding(horizontal = 4.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp)) {
                                    Text(
                                        text = engineType,
                                        color = Color.Cyan,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            IconButton(onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onMinimizeClick()
                            }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Rounded.Remove, "Minimize", tint = Color.White, modifier = Modifier.size(20.dp))
                            }

                            IconButton(onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onSettingsClick()
                            }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Rounded.Settings, "Settings", tint = Color.White, modifier = Modifier.size(20.dp))
                            }

                            IconButton(onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onClose()
                            }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Rounded.Close, "Close", tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Subtitle Area
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 44.dp)
                            .then(
                                if (showBackground) {
                                    Modifier.background(
                                        Color.Black.copy(alpha = opacity),
                                        RoundedCornerShape(16.dp)
                                    )
                                } else Modifier
                            )
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        if (displayLines.isNotEmpty()) {
                            Text(
                                text = displayLines,
                                color = Color.White,
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontSize = fontSize.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    shadow = Shadow(
                                        color = Color.Black,
                                        offset = Offset(3f, 3f),
                                        blurRadius = 6f
                                    ),
                                    lineHeight = (fontSize * 1.3).sp
                                ),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            Text(
                                text = if (isRecording) "..." else status,
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MinimizedBubble(isRecording: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(48.dp),
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.7f),
        tonalElevation = 12.dp,
        shadowElevation = 6.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Rounded.Translate,
                contentDescription = "LingoBubble",
                tint = if (isRecording) Color.Red else Color.White,
                modifier = Modifier.size(28.dp)
            )
            if (isRecording) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(8.dp)
                        .background(Color.Red, CircleShape)
                )
            }
        }
    }
}

@Composable
fun AudioVisualizer(level: Float) {
    Row(
        modifier = Modifier.padding(horizontal = 4.dp).height(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        repeat(4) { index ->
            val barHeight = when(index) {
                0 -> 4.dp + (10.dp * level * 0.6f)
                1 -> 4.dp + (12.dp * level)
                2 -> 4.dp + (10.dp * level * 0.8f)
                else -> 4.dp + (6.dp * level * 0.4f)
            }
            Box(
                modifier = Modifier
                    .width(2.5.dp)
                    .height(barHeight)
                    .background(Color.White, RoundedCornerShape(1.dp))
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickSettingsOverlay(
    fontSize: Float,
    opacity: Float,
    showBackground: Boolean,
    onFontSizeChange: (Float) -> Unit,
    onOpacityChange: (Float) -> Unit,
    onBackgroundToggle: (Boolean) -> Unit,
    onClose: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    Card(
        modifier = Modifier
            .width(300.dp)
            .padding(16.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(6.dp)),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(12.dp))
                Text("Appearance", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClose()
                }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close", modifier = Modifier.size(20.dp))
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text("Font Size: ${fontSize.toInt()}sp", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Slider(
                value = fontSize, 
                onValueChange = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onFontSizeChange(it)
                }, 
                valueRange = 14f..36f,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically, 
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth()
                ) {
                    Text("Show Background", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = showBackground, 
                        onCheckedChange = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onBackgroundToggle(it)
                        }
                    )
                }
            }
            
            if (showBackground) {
                Spacer(modifier = Modifier.height(20.dp))
                Text("Transparency: ${(opacity * 100).toInt()}%", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Slider(
                    value = opacity, 
                    onValueChange = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onOpacityChange(it)
                    }, 
                    valueRange = 0f..1f,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        }
    }
}
