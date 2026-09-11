package com.example.health

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DeviceHealthState(
    val batteryPercent: Int = 100,
    val isCharging: Boolean = false,
    val temperatureCelsius: Float = 28.0f,
    val ramAvailableMb: Long = 4096,
    val ramTotalMb: Long = 8192,
    val isLowRam: Boolean = false,
    val isBatteryOptimized: Boolean = true
)

class DeviceHealthMonitor(private val context: Context) {

    private val _healthState = MutableStateFlow(DeviceHealthState())
    val healthState: StateFlow<DeviceHealthState> = _healthState.asStateFlow()

    fun refreshHealth() {
        val batteryStatus: Intent? = IntentFilter(Intent.ACTION_BATTERY_CHANGED).let { filter ->
            context.registerReceiver(null, filter)
        }

        val level: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = if (level >= 0 && scale > 0) (level * 100 / scale.toFloat()).toInt() else 100

        val status: Int = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        val tempTenths = batteryStatus?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        val tempCelsius = tempTenths / 10.0f

        val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        actManager.getMemoryInfo(memInfo)

        val totalMb = memInfo.totalMem / (1024 * 1024)
        val availMb = memInfo.availMem / (1024 * 1024)

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val isOptimized = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            !powerManager.isIgnoringBatteryOptimizations(context.packageName)
        } else {
            false
        }

        _healthState.value = DeviceHealthState(
            batteryPercent = batteryPct,
            isCharging = isCharging,
            temperatureCelsius = tempCelsius,
            ramAvailableMb = availMb,
            ramTotalMb = totalMb,
            isLowRam = memInfo.lowMemory,
            isBatteryOptimized = isOptimized
        )
    }

    fun getIgnoreBatteryOptimizationIntent(): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            null
        }
    }
}
