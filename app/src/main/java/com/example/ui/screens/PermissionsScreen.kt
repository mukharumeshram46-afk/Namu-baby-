package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.accessibility.MyraaAccessibilityService
import com.example.notifications.MyraaNotificationListenerService
import com.example.ui.theme.MyraaDeepVoid
import com.example.ui.theme.MyraaNeonCyan
import com.example.ui.theme.MyraaSurface
import com.example.ui.theme.MyraaSurfaceElevated

@Composable
fun PermissionsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current

    var hasMic by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }
    var hasCamera by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var hasLocation by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }

    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasMic = granted
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCamera = granted
    }
    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasLocation = granted
    }

    val isA11yActive = MyraaAccessibilityService.isServiceRunning
    val isNotifActive = MyraaNotificationListenerService.isServiceRunning
    val isOverlayGranted = Settings.canDrawOverlays(context)

    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    val isBatteryOptimizedIgnored = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        powerManager.isIgnoringBatteryOptimizations(context.packageName)
    } else {
        true
    }

    Scaffold(
        containerColor = MyraaDeepVoid,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MyraaSurface)
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Icon(Icons.Default.Security, contentDescription = null, tint = Color(0xFF69F0AE), modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text("Permission Dashboard", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text("Realme 12 Pro 5G / Android 14+ System Access", fontSize = 11.sp, color = Color(0xFFA5A1C0))
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Accessibility Service
            item {
                PermissionCard(
                    title = "Accessibility Automation Service",
                    description = "Enables automated UI clicking, node reading, typing, and opening apps on voice command.",
                    icon = Icons.Default.Accessibility,
                    isGranted = isA11yActive,
                    buttonLabel = if (isA11yActive) "Active" else "Enable in Settings",
                    onAction = {
                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    }
                )
            }

            // 2. Notification Listener
            item {
                PermissionCard(
                    title = "Notification Intelligence",
                    description = "Reads incoming WhatsApp, Telegram & SMS alerts to prepare quick voice-confirmed replies.",
                    icon = Icons.Default.Notifications,
                    isGranted = isNotifActive,
                    buttonLabel = if (isNotifActive) "Active" else "Enable in Settings",
                    onAction = {
                        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    }
                )
            }

            // 3. Float Over Apps (Overlay)
            item {
                PermissionCard(
                    title = "Overlay Companion Bubble",
                    description = "Displays the animated floating companion bubble over other apps.",
                    icon = Icons.Default.Layers,
                    isGranted = isOverlayGranted,
                    buttonLabel = if (isOverlayGranted) "Active" else "Allow Overlay",
                    onAction = {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        ).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    }
                )
            }

            // 4. Microphone
            item {
                PermissionCard(
                    title = "Audio / Microphone",
                    description = "Captures speech at 16kHz mono PCM for full duplex Gemini Live interaction.",
                    icon = Icons.Default.Mic,
                    isGranted = hasMic,
                    buttonLabel = if (hasMic) "Granted" else "Request Permission",
                    onAction = {
                        micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                )
            }

            // 5. Camera
            item {
                PermissionCard(
                    title = "Camera Vision",
                    description = "Used for Quick Snap and real-time visual analysis of surroundings.",
                    icon = Icons.Default.CameraAlt,
                    isGranted = hasCamera,
                    buttonLabel = if (hasCamera) "Granted" else "Request Permission",
                    onAction = {
                        cameraLauncher.launch(Manifest.permission.CAMERA)
                    }
                )
            }

            // 6. Location
            item {
                PermissionCard(
                    title = "Location Context",
                    description = "Allows Myraa to provide weather, local venue assistance, and geofence alerts.",
                    icon = Icons.Default.LocationOn,
                    isGranted = hasLocation,
                    buttonLabel = if (hasLocation) "Granted" else "Request Permission",
                    onAction = {
                        locationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    }
                )
            }

            // 7. Ignore Battery Optimization (Realme UI / Android 14)
            item {
                PermissionCard(
                    title = "Realme Battery Unrestricted",
                    description = "Prevents Realme UI aggressive battery killer from terminating foreground services.",
                    icon = Icons.Default.BatteryAlert,
                    isGranted = isBatteryOptimizedIgnored,
                    buttonLabel = if (isBatteryOptimizedIgnored) "Unrestricted" else "Whitelist App",
                    onAction = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                data = Uri.parse("package:${context.packageName}")
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        }
                    }
                )
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
    buttonLabel: String,
    onAction: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MyraaSurfaceElevated),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (isGranted) Color(0xFF1B382B) else Color(0xFF381B24)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isGranted) Color(0xFF00E676) else Color(0xFFFF5252),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(
                        text = if (isGranted) "Enabled / Granted" else "Disabled / Required",
                        fontSize = 11.sp,
                        color = if (isGranted) Color(0xFF00E676) else Color(0xFFFF5252),
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(description, fontSize = 12.sp, color = Color(0xFFA5A1C0), lineHeight = 16.sp)

            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onAction,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isGranted) Color(0xFF263238) else MyraaNeonCyan,
                    contentColor = if (isGranted) Color.White else MyraaDeepVoid
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isGranted) Icons.Default.Check else Icons.Default.Close,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(buttonLabel, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
