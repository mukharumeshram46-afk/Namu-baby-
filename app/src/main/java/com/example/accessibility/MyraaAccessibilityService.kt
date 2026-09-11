package com.example.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject

class MyraaAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "MyraaAccessibility"

        @Volatile
        var instance: MyraaAccessibilityService? = null
            private set

        val isServiceRunning: Boolean
            get() = instance != null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "MyraaAccessibilityService connected and ready for automation")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Active window event monitoring
    }

    override fun onInterrupt() {
        Log.w(TAG, "MyraaAccessibilityService interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
        }
        Log.i(TAG, "MyraaAccessibilityService destroyed")
    }

    /**
     * Reads all accessible UI nodes from the currently active window.
     */
    fun readScreenNodes(): JSONObject {
        val rootNode = rootInActiveWindow ?: return JSONObject().apply {
            put("error", "No active window accessible. Ensure app has accessibility permission and screen is on.")
        }

        return try {
            val result = JSONObject().apply {
                put("packageName", rootNode.packageName?.toString() ?: "unknown")
                put("className", rootNode.className?.toString() ?: "unknown")
                put("nodes", parseNodeHierarchy(rootNode))
            }
            result
        } catch (e: Exception) {
            JSONObject().apply {
                put("error", "Failed reading screen nodes: ${e.message}")
            }
        }
    }

    private fun parseNodeHierarchy(node: AccessibilityNodeInfo): JSONObject {
        val json = JSONObject()
        json.put("text", node.text?.toString() ?: "")
        json.put("contentDescription", node.contentDescription?.toString() ?: "")
        json.put("viewId", node.viewIdResourceName ?: "")
        json.put("className", node.className?.toString() ?: "")
        json.put("clickable", node.isClickable)
        json.put("editable", node.isEditable)
        json.put("enabled", node.isEnabled)
        json.put("scrollable", node.isScrollable)

        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        json.put("bounds", JSONObject().apply {
            put("left", bounds.left)
            put("top", bounds.top)
            put("right", bounds.right)
            put("bottom", bounds.bottom)
            put("centerX", bounds.centerX())
            put("centerY", bounds.centerY())
        })

        if (node.childCount > 0) {
            val childrenArray = JSONArray()
            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                if (child != null) {
                    childrenArray.put(parseNodeHierarchy(child))
                }
            }
            json.put("children", childrenArray)
        }

        return json
    }

    /**
     * Clicks an element by viewId, exact text, content description, or coordinate tapping.
     */
    fun clickElement(targetText: String? = null, viewId: String? = null, x: Float? = null, y: Float? = null): Pair<Boolean, String> {
        val root = rootInActiveWindow
        if (root == null) {
            return Pair(false, "Cannot access active window content")
        }

        // 1. Try finding by viewId
        if (!viewId.isNullOrBlank()) {
            val nodes = root.findAccessibilityNodeInfosByViewId(viewId)
            for (node in nodes) {
                if (performClickOnNode(node)) {
                    return Pair(true, "Clicked element with viewId '$viewId'")
                }
            }
        }

        // 2. Try finding by text
        if (!targetText.isNullOrBlank()) {
            val nodes = root.findAccessibilityNodeInfosByText(targetText)
            for (node in nodes) {
                if (performClickOnNode(node)) {
                    return Pair(true, "Clicked element matching text '$targetText'")
                }
            }

            // High-tolerance recursive search across all nodes
            val matchedNode = findMatchingNodeRecursive(root, targetText)
            if (matchedNode != null && performClickOnNode(matchedNode)) {
                return Pair(true, "Clicked element matching '$targetText'")
            }
        }

        // 3. Fallback to coordinate gesture tap if coordinates provided
        if (x != null && y != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val success = clickCoordinates(x, y)
            return if (success) {
                Pair(true, "Performed tap gesture at ($x, $y)")
            } else {
                Pair(false, "Failed to dispatch tap gesture at ($x, $y)")
            }
        }

        return Pair(false, "Element '${targetText ?: viewId}' not found on screen")
    }

    private fun performClickOnNode(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        while (current != null) {
            if (current.isClickable) {
                return current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            current = current.parent
        }
        // If not clickable directly, try clicking parent bounds via gesture
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        if (bounds.width() > 0 && bounds.height() > 0) {
            return clickCoordinates(bounds.centerX().toFloat(), bounds.centerY().toFloat())
        }
        return false
    }

    private fun findMatchingNodeRecursive(node: AccessibilityNodeInfo, query: String): AccessibilityNodeInfo? {
        val q = query.lowercase()
        val text = node.text?.toString()?.lowercase() ?: ""
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""

        if (text.contains(q) || desc.contains(q)) {
            return node
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                val found = findMatchingNodeRecursive(child, query)
                if (found != null) return found
            }
        }
        return null
    }

    private fun clickCoordinates(x: Float, y: Float): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        val path = Path().apply {
            moveTo(x, y)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, 50)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGesture(gesture, null, null)
    }

    /**
     * Types text into the focused editable field or targeted node.
     */
    fun typeText(text: String, targetText: String? = null, pressEnter: Boolean = false): Pair<Boolean, String> {
        val root = rootInActiveWindow ?: return Pair(false, "Cannot access active window")

        val targetNode: AccessibilityNodeInfo? = if (!targetText.isNullOrBlank()) {
            findMatchingNodeRecursive(root, targetText)
        } else {
            root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: findFirstEditableNode(root)
        }

        if (targetNode == null) {
            return Pair(false, "Could not locate focused or editable input field")
        }

        val arguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        val success = targetNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)

        if (success && pressEnter) {
            // Some apps listen to ACTION_CLICK or enter key
            targetNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }

        return if (success) {
            Pair(true, "Entered text into input field")
        } else {
            Pair(false, "Failed applying ACTION_SET_TEXT to target node")
        }
    }

    private fun findFirstEditableNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isEditable) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                val res = findFirstEditableNode(child)
                if (res != null) return res
            }
        }
        return null
    }

    /**
     * Scrolls content UP, DOWN, LEFT, or RIGHT.
     */
    fun scroll(direction: String): Pair<Boolean, String> {
        val root = rootInActiveWindow ?: return Pair(false, "Cannot access active window")
        val scrollableNode = findFirstScrollableNode(root)

        val dir = direction.uppercase()
        val action = when (dir) {
            "DOWN", "FORWARD" -> AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            "UP", "BACKWARD" -> AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            else -> AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        }

        if (scrollableNode != null) {
            val success = scrollableNode.performAction(action)
            if (success) return Pair(true, "Scrolled $direction successfully")
        }

        // Gesture swipe fallback
        val displayMetrics = resources.displayMetrics
        val width = displayMetrics.widthPixels.toFloat()
        val height = displayMetrics.heightPixels.toFloat()

        val startY = if (dir == "DOWN") height * 0.75f else height * 0.25f
        val endY = if (dir == "DOWN") height * 0.25f else height * 0.75f
        val gestureSuccess = swipeGesture(width / 2f, startY, width / 2f, endY)

        return if (gestureSuccess) {
            Pair(true, "Executed scroll gesture $direction")
        } else {
            Pair(false, "Failed to perform scroll action or gesture")
        }
    }

    private fun findFirstScrollableNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isScrollable) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                val found = findFirstScrollableNode(child)
                if (found != null) return found
            }
        }
        return null
    }

    private fun swipeGesture(startX: Float, startY: Float, endX: Float, endY: Float): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 300))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    /**
     * Executes official global system actions.
     */
    fun performSystemAction(action: String): Pair<Boolean, String> {
        val globalAction = when (action.uppercase()) {
            "BACK" -> GLOBAL_ACTION_BACK
            "HOME" -> GLOBAL_ACTION_HOME
            "RECENTS" -> GLOBAL_ACTION_RECENTS
            "NOTIFICATIONS" -> GLOBAL_ACTION_NOTIFICATIONS
            "QUICK_SETTINGS" -> GLOBAL_ACTION_QUICK_SETTINGS
            "LOCK" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) GLOBAL_ACTION_LOCK_SCREEN else -1
            else -> -1
        }

        if (globalAction == -1) {
            return Pair(false, "System action '$action' is unsupported on this Android version")
        }

        val success = performGlobalAction(globalAction)
        return if (success) {
            Pair(true, "Executed system action: $action")
        } else {
            Pair(false, "Failed executing system action: $action")
        }
    }

    /**
     * Opens an application by package name or resolving common name.
     */
    fun openApp(packageNameOrName: String): Pair<Boolean, String> {
        val pm = packageManager
        val clean = packageNameOrName.trim().lowercase()

        // Common app mappings
        val resolvedPackage = when {
            clean.contains("youtube") -> "com.google.android.youtube"
            clean.contains("whatsapp") -> "com.whatsapp"
            clean.contains("telegram") -> "org.telegram.messenger"
            clean.contains("chrome") -> "com.android.chrome"
            clean.contains("camera") -> "com.android.camera"
            clean.contains("settings") -> "com.android.settings"
            clean.contains("maps") -> "com.google.android.apps.maps"
            clean.contains("gmail") -> "com.google.android.gm"
            clean.contains("messages") || clean.contains("sms") -> "com.google.android.apps.messaging"
            clean.contains(".") -> packageNameOrName.trim()
            else -> packageNameOrName.trim()
        }

        val launchIntent = pm.getLaunchIntentForPackage(resolvedPackage)
        return if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(launchIntent)
            Pair(true, "Launched app: $resolvedPackage")
        } else {
            // Try searching package list
            val installed = pm.getInstalledApplications(0)
            val matched = installed.find {
                it.packageName.lowercase().contains(clean) ||
                        pm.getApplicationLabel(it).toString().lowercase().contains(clean)
            }

            if (matched != null) {
                val intent = pm.getLaunchIntentForPackage(matched.packageName)
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                    return Pair(true, "Launched app: ${matched.packageName}")
                }
            }

            Pair(false, "Package '$packageNameOrName' is not installed or launch intent unavailable.")
        }
    }

    /**
     * Multi-step workflow app chain execution.
     */
    suspend fun executeAppChain(steps: List<ChainStep>): Pair<Boolean, String> {
        val logs = StringBuilder()
        for ((index, step) in steps.withIndex()) {
            logs.append("Step ${index + 1}: ${step.action} on ${step.target} -> ")
            val result = when (step.action.lowercase()) {
                "open_app" -> openApp(step.target)
                "click" -> clickElement(targetText = step.target)
                "type" -> typeText(text = step.input ?: "", targetText = step.target, pressEnter = step.pressEnter)
                "scroll" -> scroll(step.target)
                "system" -> performSystemAction(step.target)
                else -> Pair(false, "Unknown chain action: ${step.action}")
            }
            logs.append(result.second).append("\n")
            if (!result.first) {
                return Pair(false, "Chain aborted at step ${index + 1}: ${result.second}\nLogs: $logs")
            }
            if (step.delayMs > 0) {
                delay(step.delayMs)
            }
        }
        return Pair(true, "Chain completed successfully.\n$logs")
    }
}

data class ChainStep(
    val action: String,
    val target: String,
    val input: String? = null,
    val delayMs: Long = 1000L,
    val pressEnter: Boolean = false
)
