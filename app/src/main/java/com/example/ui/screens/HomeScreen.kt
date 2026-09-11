package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.ScreenShare
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.StopScreenShare
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.MyraaApplication
import com.example.ai.ConnectionState
import com.example.overlay.MyraaOverlayBubbleService
import com.example.personality.MyraaMood
import com.example.screen.MyraaScreenCaptureService
import com.example.services.MyraaForegroundAudioService
import com.example.ui.components.MyraaStage
import com.example.ui.theme.MyraaDeepVoid
import com.example.ui.theme.MyraaNeonCyan
import com.example.ui.theme.MyraaSurface
import com.example.ui.theme.MyraaSurfaceElevated

@Composable
fun HomeScreen(
    app: MyraaApplication,
    onNavigateToBrowser: () -> Unit,
    onNavigateToMemories: () -> Unit,
    onNavigateToPermissions: () -> Unit,
    onNavigateToHealth: () -> Unit,
    onRequestMicrophonePermission: () -> Unit
) {
    val context = LocalContext.current
    val connectionState by app.geminiLiveManager.connectionState.collectAsState()
    val mood by app.moodEngine.currentMood.collectAsState()
    val userText by app.userTranscription.collectAsState()
    val myraaText by app.myraaTranscription.collectAsState()
    val inputAmp by app.inputAmplitude.collectAsState()
    val outputAmp by app.outputAmplitude.collectAsState()
    val actionStatus by app.lastActionStatus.collectAsState()
    val isSharingScreen by MyraaScreenCaptureService.isSharing.collectAsState()
    val isOverlayActive by MyraaOverlayBubbleService.overlayVisibleFlow.collectAsState()

    var isMicActive by remember { mutableStateOf(app.audioInputManager.isRecording) }

    // Screen capture permission launcher
    val screenCaptureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val startIntent = Intent(context, MyraaScreenCaptureService::class.java).apply {
                action = MyraaScreenCaptureService.ACTION_START
                putExtra(MyraaScreenCaptureService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(MyraaScreenCaptureService.EXTRA_RESULT_DATA, result.data)
            }
            ContextCompat.startForegroundService(context, startIntent)
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulseTransition")
    val micPulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isMicActive) 1.14f else 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isMicActive) 500 else 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "micPulse"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MyraaDeepVoid)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Status Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Live Status Chip
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(MyraaSurfaceElevated)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            when (connectionState) {
                                ConnectionState.CONNECTED -> Color(0xFF00E676)
                                ConnectionState.CONNECTING, ConnectionState.RECONNECTING -> Color(0xFFFFD54F)
                                else -> Color(0xFFFF5252)
                            }
                        )
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = when (connectionState) {
                        ConnectionState.CONNECTED -> "LIVE COMPANION"
                        ConnectionState.CONNECTING -> "CONNECTING..."
                        ConnectionState.RECONNECTING -> "RECONNECTING..."
                        ConnectionState.ERROR -> "OFFLINE / LOCAL"
                        ConnectionState.DISCONNECTED -> "STANDBY"
                    },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            // Realme 12 Pro 5G Optimization Badge
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(MyraaSurfaceElevated)
                    .clickable { onNavigateToHealth() }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.BatteryChargingFull,
                    contentDescription = "Device Health",
                    tint = MyraaNeonCyan,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Realme 12 Pro",
                    fontSize = 11.sp,
                    color = MyraaNeonCyan,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Mood Tag & Aura Indicator
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(mood.auraColor.copy(alpha = 0.25f), mood.secondaryColor.copy(alpha = 0.25f))
                    )
                )
                .border(
                    width = 1.dp,
                    brush = Brush.horizontalGradient(listOf(mood.auraColor, mood.secondaryColor)),
                    shape = RoundedCornerShape(20.dp)
                )
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = mood.auraColor,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Mood: ${mood.displayName}",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Central Anime Companion Stage
        MyraaStage(
            modifier = Modifier
                .size(240.dp)
                .testTag("myraa_stage_avatar"),
            mood = mood,
            inputAmplitude = inputAmp,
            outputAmplitude = outputAmp,
            isUserSpeaking = inputAmp > 0.08f,
            isMyraaSpeaking = outputAmp > 0.04f
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Action Status Banner (if a tool was called)
        AnimatedVisibility(visible = !actionStatus.isNullOrBlank()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MyraaSurfaceElevated),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MyraaNeonCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = actionStatus ?: "",
                        fontSize = 12.sp,
                        color = Color.White,
                        maxLines = 2
                    )
                }
            }
        }

        // Live Subtitles Transcript Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
                .testTag("transcript_card"),
            colors = CardDefaults.cardColors(containerColor = MyraaSurface),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                if (userText.isNotBlank()) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                        Text(
                            text = "You: ",
                            fontWeight = FontWeight.Bold,
                            color = MyraaNeonCyan,
                            fontSize = 13.sp
                        )
                        Text(
                            text = userText,
                            color = Color.White,
                            fontSize = 13.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }

                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    Text(
                        text = "Myraa: ",
                        fontWeight = FontWeight.Bold,
                        color = mood.auraColor,
                        fontSize = 13.sp
                    )
                    Text(
                        text = myraaText.ifBlank { "Main sun rahi hoon... Bolo, kaise help karoon?" },
                        color = Color(0xFFECEBFF),
                        fontSize = 13.sp
                    )
                }
            }
        }

        // Big Holographic Mic Activation Button
        Box(
            modifier = Modifier
                .size(90.dp)
                .scale(micPulse)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            if (isMicActive) Color(0xFFFF007F) else MyraaNeonCyan,
                            if (isMicActive) Color(0xFF7C4DFF) else Color(0xFF0D47A1)
                        )
                    )
                )
                .clickable {
                    if (isMicActive) {
                        app.audioInputManager.stopRecording()
                        isMicActive = false
                    } else {
                        onRequestMicrophonePermission()
                        val success = app.audioInputManager.startRecording()
                        isMicActive = success
                        if (connectionState != ConnectionState.CONNECTED) {
                            app.geminiLiveManager.connect()
                        }
                    }
                }
                .testTag("microphone_toggle_button"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isMicActive) Icons.Default.Mic else Icons.Default.MicOff,
                contentDescription = "Microphone Toggle",
                tint = Color.White,
                modifier = Modifier.size(40.dp)
            )
        }

        Text(
            text = if (isMicActive) "Listening... Tap to Mute" else "Tap to Speak with Myraa",
            color = Color(0xFFA5A1C0),
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
        )

        // Native Capabilities Tool Tray
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            item {
                CapabilityChip(
                    icon = if (isSharingScreen) Icons.Default.StopScreenShare else Icons.Default.ScreenShare,
                    label = if (isSharingScreen) "Stop Vision" else "Screen Vision",
                    tint = if (isSharingScreen) Color(0xFFFF5252) else MyraaNeonCyan,
                    onClick = {
                        if (isSharingScreen) {
                            val stopIntent = Intent(context, MyraaScreenCaptureService::class.java).apply {
                                action = MyraaScreenCaptureService.ACTION_STOP
                            }
                            context.startService(stopIntent)
                        } else {
                            val mpManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                            screenCaptureLauncher.launch(mpManager.createScreenCaptureIntent())
                        }
                    }
                )
            }

            item {
                CapabilityChip(
                    icon = Icons.Default.Layers,
                    label = if (isOverlayActive) "Hide Bubble" else "Float Bubble",
                    tint = Color(0xFFFFD54F),
                    onClick = {
                        if (!Settings.canDrawOverlays(context)) {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            )
                            context.startActivity(intent)
                        } else {
                            val intent = Intent(context, MyraaOverlayBubbleService::class.java).apply {
                                action = if (isOverlayActive) MyraaOverlayBubbleService.ACTION_HIDE else MyraaOverlayBubbleService.ACTION_SHOW
                            }
                            context.startService(intent)
                        }
                    }
                )
            }

            item {
                CapabilityChip(
                    icon = Icons.Default.Language,
                    label = "Web Browser",
                    tint = Color(0xFF00E5FF),
                    onClick = onNavigateToBrowser
                )
            }

            item {
                CapabilityChip(
                    icon = Icons.Default.Psychology,
                    label = "Memories",
                    tint = Color(0xFFFF80AB),
                    onClick = onNavigateToMemories
                )
            }

            item {
                CapabilityChip(
                    icon = Icons.Default.Security,
                    label = "Permissions",
                    tint = Color(0xFF69F0AE),
                    onClick = onNavigateToPermissions
                )
            }

            item {
                CapabilityChip(
                    icon = Icons.Default.CameraAlt,
                    label = "Quick Snap",
                    tint = Color(0xFFEA80FC),
                    onClick = {
                        app.moodEngine.setMood(MyraaMood.EXCITED)
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun CapabilityChip(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .testTag("chip_${label.lowercase().replace(" ", "_")}"),
        colors = CardDefaults.cardColors(containerColor = MyraaSurfaceElevated)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        }
    }
}
