package com.example.tools

import android.content.Context
import android.util.Log
import com.example.accessibility.MyraaAccessibilityService
import com.example.browser.BrowserAgent
import com.example.health.DeviceHealthMonitor
import com.example.location.LocationHelper
import com.example.memory.MemoryRepository
import com.example.personality.MoodEngine
import com.example.personality.MyraaMood
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ToolRouter(
    private val context: Context,
    private val scope: CoroutineScope,
    private val memoryRepository: MemoryRepository,
    private val browserAgent: BrowserAgent,
    private val locationHelper: LocationHelper,
    private val healthMonitor: DeviceHealthMonitor,
    private val moodEngine: MoodEngine,
    private val onThemeChanged: (String) -> Unit
) {
    companion object {
        private const val TAG = "ToolRouter"
    }

    suspend fun executeTool(name: String, args: Map<String, Any?>): Map<String, Any?> = withContext(Dispatchers.Default) {
        Log.i(TAG, "Executing tool: $name with args: $args")
        try {
            when (name) {
                // 1. Android OS Tools
                "android_openApp" -> {
                    val target = args["packageName"]?.toString() ?: ""
                    val a11y = MyraaAccessibilityService.instance
                    if (a11y != null) {
                        val result = a11y.openApp(target)
                        mapOf("status" to if (result.first) "success" else "failed", "message" to result.second)
                    } else {
                        mapOf("status" to "failed", "message" to "Accessibility Service is not enabled. Please enable MYRAA in Settings > Accessibility.")
                    }
                }

                "android_clickElement" -> {
                    val text = args["targetText"]?.toString()
                    val viewId = args["viewId"]?.toString()
                    val a11y = MyraaAccessibilityService.instance
                    if (a11y != null) {
                        val result = a11y.clickElement(targetText = text, viewId = viewId)
                        mapOf("status" to if (result.first) "success" else "failed", "message" to result.second)
                    } else {
                        mapOf("status" to "failed", "message" to "Accessibility Service not enabled")
                    }
                }

                "android_typeText" -> {
                    val text = args["text"]?.toString() ?: ""
                    val a11y = MyraaAccessibilityService.instance
                    if (a11y != null) {
                        val result = a11y.typeText(text = text, pressEnter = true)
                        mapOf("status" to if (result.first) "success" else "failed", "message" to result.second)
                    } else {
                        mapOf("status" to "failed", "message" to "Accessibility Service not enabled")
                    }
                }

                "android_scroll" -> {
                    val direction = args["direction"]?.toString() ?: "DOWN"
                    val a11y = MyraaAccessibilityService.instance
                    if (a11y != null) {
                        val result = a11y.scroll(direction)
                        mapOf("status" to if (result.first) "success" else "failed", "message" to result.second)
                    } else {
                        mapOf("status" to "failed", "message" to "Accessibility Service not enabled")
                    }
                }

                "android_systemAction" -> {
                    val action = args["action"]?.toString() ?: "BACK"
                    val a11y = MyraaAccessibilityService.instance
                    if (a11y != null) {
                        val result = a11y.performSystemAction(action)
                        mapOf("status" to if (result.first) "success" else "failed", "message" to result.second)
                    } else {
                        mapOf("status" to "failed", "message" to "Accessibility Service not enabled")
                    }
                }

                "android_readScreenNodes" -> {
                    val a11y = MyraaAccessibilityService.instance
                    if (a11y != null) {
                        val nodesJson = a11y.readScreenNodes()
                        mapOf("status" to "success", "screenData" to nodesJson.toString())
                    } else {
                        mapOf("status" to "failed", "message" to "Accessibility Service not enabled")
                    }
                }

                // 2. In-App Browser Tools
                "browserOpen" -> {
                    val url = args["url"]?.toString() ?: "https://duckduckgo.com"
                    val res = browserAgent.openUrl(url)
                    mapOf("status" to if (res.first) "success" else "failed", "message" to res.second)
                }

                "browserSearch" -> {
                    val query = args["query"]?.toString() ?: ""
                    val res = browserAgent.search(query)
                    mapOf("status" to if (res.first) "success" else "failed", "message" to res.second)
                }

                "browserMediaControl" -> {
                    val action = args["action"]?.toString() ?: "toggle"
                    val res = browserAgent.executeMediaControl(action)
                    mapOf("status" to if (res.first) "success" else "failed", "message" to res.second)
                }

                // 3. Memory & Recollection Core
                "saveCustomMemory" -> {
                    val category = args["category"]?.toString() ?: "preference"
                    val text = args["text"]?.toString() ?: ""
                    if (text.isNotBlank()) {
                        memoryRepository.saveMemory(category, text)
                        moodEngine.setMood(MyraaMood.PROUD)
                        mapOf("status" to "success", "message" to "Saved new recollection: '$text' in category '$category'")
                    } else {
                        mapOf("status" to "failed", "message" to "Memory text was empty")
                    }
                }

                // 4. Ambient Atmosphere
                "changeBackground" -> {
                    val color = args["color"]?.toString() ?: "violet"
                    onThemeChanged(color)
                    moodEngine.setMood(MyraaMood.PLAYFUL)
                    mapOf("status" to "success", "message" to "Theme atmosphere changed to $color")
                }

                // 5. Location Context
                "getLocationContext" -> {
                    val loc = locationHelper.getCurrentLocation()
                    if (loc != null) {
                        mapOf(
                            "status" to "success",
                            "city" to loc.city,
                            "country" to loc.country,
                            "address" to loc.address,
                            "latitude" to loc.latitude,
                            "longitude" to loc.longitude
                        )
                    } else {
                        mapOf("status" to "failed", "message" to "Location permission not granted or GPS unavailable")
                    }
                }

                // 6. Device Health & Realme 12 Pro Monitor
                "getDeviceHealth" -> {
                    healthMonitor.refreshHealth()
                    val h = healthMonitor.healthState.value
                    mapOf(
                        "status" to "success",
                        "batteryPercent" to h.batteryPercent,
                        "isCharging" to h.isCharging,
                        "temperatureCelsius" to h.temperatureCelsius,
                        "ramAvailableMb" to h.ramAvailableMb,
                        "ramTotalMb" to h.ramTotalMb,
                        "isLowRam" to h.isLowRam,
                        "isBatteryOptimized" to h.isBatteryOptimized
                    )
                }

                else -> {
                    mapOf("status" to "failed", "message" to "Unknown tool function: $name")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error running tool $name: ${e.message}", e)
            mapOf("status" to "error", "message" to (e.message ?: "Execution error"))
        }
    }
}
