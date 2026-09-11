package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.DeviceThermostat
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.MyraaApplication
import com.example.ui.theme.MyraaDeepVoid
import com.example.ui.theme.MyraaNeonCyan
import com.example.ui.theme.MyraaSurface
import com.example.ui.theme.MyraaSurfaceElevated

@Composable
fun HealthScreen(
    app: MyraaApplication,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val monitor = app.healthMonitor
    val state by monitor.healthState.collectAsState()

    LaunchedEffect(Unit) {
        monitor.refreshHealth()
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
                Icon(
                    imageVector = Icons.Default.PhoneAndroid,
                    contentDescription = null,
                    tint = MyraaNeonCyan,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text("Device Diagnostics", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text("Target: Realme 12 Pro 5G (Android 14+)", fontSize = 11.sp, color = Color(0xFFA5A1C0))
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Battery Status Card
            item {
                HealthMetricCard(
                    title = "Battery & Power State",
                    subtitle = if (state.isCharging) "Charging active" else "Running on battery",
                    icon = Icons.Default.BatteryChargingFull,
                    iconColor = if (state.isCharging) Color(0xFF00E676) else MyraaNeonCyan,
                    metricValue = "${state.batteryPercent}%",
                    progress = state.batteryPercent / 100f
                )
            }

            // Temperature / Thermal Throttling Card
            item {
                val isHot = state.temperatureCelsius > 42.0f
                HealthMetricCard(
                    title = "Thermal Telemetry",
                    subtitle = if (isHot) "High temperature - thermal throttling risk" else "Nominal operating thermals",
                    icon = Icons.Default.DeviceThermostat,
                    iconColor = if (isHot) Color(0xFFFF5252) else Color(0xFFFFD54F),
                    metricValue = "${state.temperatureCelsius} °C",
                    progress = (state.temperatureCelsius / 60.0f).coerceIn(0f, 1f)
                )
            }

            // RAM Memory Card
            item {
                val usedMb = state.ramTotalMb - state.ramAvailableMb
                val ramProgress = if (state.ramTotalMb > 0) usedMb.toFloat() / state.ramTotalMb.toFloat() else 0.5f
                HealthMetricCard(
                    title = "RAM Memory Allocation",
                    subtitle = "${state.ramAvailableMb} MB available out of ${state.ramTotalMb} MB",
                    icon = Icons.Default.Memory,
                    iconColor = Color(0xFFB388FF),
                    metricValue = "${(ramProgress * 100).toInt()}% Used",
                    progress = ramProgress
                )
            }

            // Realme UI / ColorOS Guidance Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MyraaSurfaceElevated),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = null,
                                tint = Color(0xFFFF80AB),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Realme UI / ColorOS Background Guide",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 14.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "To ensure Myraa's continuous voice and screen vision services are not terminated by Realme's aggressive task killer:\n" +
                                    "1. Settings > Apps > App management > MYRAA AI\n" +
                                    "2. Enable 'Allow Auto-launch' & 'Allow background activity'\n" +
                                    "3. Set Battery usage to 'Don't optimize / Unrestricted'\n" +
                                    "4. In Recent Tasks, lock MYRAA by tapping the 3 dots and selecting 'Lock'.",
                            fontSize = 12.sp,
                            color = Color(0xFFA5A1C0),
                            lineHeight = 18.sp
                        )

                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = {
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MyraaNeonCyan),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Open Realme App Settings", color = MyraaDeepVoid, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HealthMetricCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    metricValue: String,
    progress: Float
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MyraaSurfaceElevated),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(iconColor.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text(subtitle, fontSize = 11.sp, color = Color(0xFFA5A1C0))
                    }
                }
                Text(
                    text = metricValue,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = iconColor
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = iconColor,
                trackColor = Color(0xFF261D42)
            )
        }
    }
}
