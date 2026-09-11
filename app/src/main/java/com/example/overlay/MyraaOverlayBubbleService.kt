package com.example.overlay

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.MainActivity
import com.example.MyraaApplication
import com.example.R
import com.example.personality.MyraaMood
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs

class MyraaOverlayBubbleService : Service(), LifecycleOwner, SavedStateRegistryOwner {

    companion object {
        private const val TAG = "OverlayBubbleService"
        const val ACTION_SHOW = "com.example.overlay.SHOW"
        const val ACTION_HIDE = "com.example.overlay.HIDE"

        @Volatile
        var isOverlayVisible: Boolean = false
            private set

        private val _overlayVisibleFlow = MutableStateFlow(false)
        val overlayVisibleFlow: StateFlow<Boolean> = _overlayVisibleFlow.asStateFlow()
    }

    private var windowManager: WindowManager? = null
    private var overlayContainer: FrameLayout? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW -> showBubble()
            ACTION_HIDE -> hideBubble()
            else -> showBubble()
        }
        return START_NOT_STICKY
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showBubble() {
        if (isOverlayVisible || overlayContainer != null) return

        if (!Settings.canDrawOverlays(this)) {
            Log.w(TAG, "Cannot draw overlays: permission denied")
            return
        }

        try {
            val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            layoutParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 40
                y = 300
            }

            overlayContainer = FrameLayout(this).apply {
                setViewTreeLifecycleOwner(this@MyraaOverlayBubbleService)
                setViewTreeSavedStateRegistryOwner(this@MyraaOverlayBubbleService)
            }

            var initialX = 0
            var initialY = 0
            var initialTouchX = 0f
            var initialTouchY = 0f
            var isClick = true

            overlayContainer?.setOnTouchListener { _, event ->
                val params = layoutParams ?: return@setOnTouchListener false
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        isClick = true
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - initialTouchX).toInt()
                        val dy = (event.rawY - initialTouchY).toInt()
                        if (abs(dx) > 10 || abs(dy) > 10) {
                            isClick = false
                        }
                        params.x = initialX + dx
                        params.y = initialY + dy
                        windowManager?.updateViewLayout(overlayContainer, params)
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (isClick) {
                            handleBubbleTap()
                        }
                        true
                    }
                    else -> false
                }
            }

            val composeView = ComposeView(this).apply {
                setContent {
                    BubbleComposeContent()
                }
            }
            overlayContainer?.addView(composeView)
            windowManager?.addView(overlayContainer, layoutParams)

            isOverlayVisible = true
            _overlayVisibleFlow.value = true
            Log.i(TAG, "Myraa overlay bubble displayed")
        } catch (e: Exception) {
            Log.e(TAG, "Error displaying overlay bubble: ${e.message}", e)
        }
    }

    private fun handleBubbleTap() {
        val app = application as? MyraaApplication
        val isRecording = app?.audioInputManager?.isRecording == true
        if (isRecording) {
            app?.audioInputManager?.stopRecording()
        } else {
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            startActivity(intent)
        }
    }

    @Composable
    private fun BubbleComposeContent() {
        val app = application as? MyraaApplication
        val mood by (app?.moodEngine?.currentMood ?: MutableStateFlow(MyraaMood.IDLE)).collectAsState()
        val isRecording = app?.audioInputManager?.isRecording == true
        val isPlaying = app?.audioOutputManager?.isPlaying == true

        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
        val pulseScale by infiniteTransition.animateFloat(
            initialValue = 0.95f,
            targetValue = if (isPlaying || isRecording) 1.15f else 1.05f,
            animationSpec = infiniteRepeatable(
                animation = tween(if (isPlaying) 400 else if (isRecording) 600 else 1200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bubbleScale"
        )

        Box(
            modifier = Modifier
                .size(68.dp)
                .scale(pulseScale),
            contentAlignment = Alignment.Center
        ) {
            // Glowing atmospheric halo
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            mood.auraColor.copy(alpha = 0.6f),
                            mood.secondaryColor.copy(alpha = 0.2f),
                            Color.Transparent
                        )
                    )
                )
            }

            // Core Avatar Circle
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF0F0C20), Color(0xFF1E1338))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isPlaying) {
                    Icon(
                        imageVector = Icons.Default.VolumeUp,
                        contentDescription = "Myraa Talking",
                        tint = mood.secondaryColor,
                        modifier = Modifier.size(24.dp)
                    )
                } else if (isRecording) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Myraa Listening",
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(24.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "Myraa Companion",
                        tint = mood.auraColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }

    private fun hideBubble() {
        if (overlayContainer != null) {
            try {
                windowManager?.removeView(overlayContainer)
            } catch (e: Exception) {
                Log.w(TAG, "Error removing overlay container: ${e.message}")
            }
            overlayContainer = null
            isOverlayVisible = false
            _overlayVisibleFlow.value = false
            Log.i(TAG, "Myraa overlay bubble hidden")
        }
    }

    override fun onDestroy() {
        hideBubble()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
