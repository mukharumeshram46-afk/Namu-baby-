package com.example.screen

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

class MyraaScreenCaptureService : Service() {

    companion object {
        private const val TAG = "ScreenCaptureService"
        const val CHANNEL_ID = "myraa_screen_capture_channel"
        const val NOTIFICATION_ID = 2001

        const val ACTION_START = "com.example.screen.START"
        const val ACTION_PAUSE = "com.example.screen.PAUSE"
        const val ACTION_RESUME = "com.example.screen.RESUME"
        const val ACTION_STOP = "com.example.screen.STOP"

        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"

        @Volatile
        var instance: MyraaScreenCaptureService? = null
            private set

        private val _isSharing = MutableStateFlow(false)
        val isSharing: StateFlow<Boolean> = _isSharing.asStateFlow()

        private val _isPaused = MutableStateFlow(false)
        val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

        private val _latestFrame = MutableStateFlow<ByteArray?>(null)
        val latestFrame: StateFlow<ByteArray?> = _latestFrame.asStateFlow()
    }

    private val serviceScope = CoroutineScope(Dispatchers.Default)
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var captureJob: Job? = null
    private var frameIntervalMs = 2000L // 0.5 FPS default adaptive

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                val resultData = intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
                if (resultCode != 0 && resultData != null) {
                    startForegroundWithNotification()
                    startCapture(resultCode, resultData)
                } else {
                    stopSelf()
                }
            }
            ACTION_PAUSE -> {
                _isPaused.value = true
                Log.i(TAG, "Screen Vision transmission paused")
            }
            ACTION_RESUME -> {
                _isPaused.value = false
                Log.i(TAG, "Screen Vision transmission resumed")
            }
            ACTION_STOP -> {
                stopCapture()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "MYRAA Screen Vision",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Active screen capture for real-time visual assistance"
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun startForegroundWithNotification() {
        val launchIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SCREEN SHARING ACTIVE")
            .setContentText("Myraa is analyzing your screen in real time.")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startCapture(resultCode: Int, resultData: Intent) {
        val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = mpManager.getMediaProjection(resultCode, resultData)

        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        wm.defaultDisplay.getMetrics(metrics)

        // Scale down dimensions to ~720p to conserve memory & bandwidth
        val maxDim = 960
        var width = metrics.widthPixels
        var height = metrics.heightPixels
        val density = metrics.densityDpi

        if (width > maxDim || height > maxDim) {
            if (width > height) {
                height = (height.toFloat() * maxDim / width).toInt()
                width = maxDim
            } else {
                width = (width.toFloat() * maxDim / height).toInt()
                height = maxDim
            }
        }

        // Align dimensions to 2
        width = (width / 2) * 2
        height = (height / 2) * 2

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "MyraaScreenVisionDisplay",
            width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface, null, null
        )

        _isSharing.value = true
        _isPaused.value = false

        captureJob = serviceScope.launch(Dispatchers.IO) {
            while (isActive && _isSharing.value) {
                if (!_isPaused.value) {
                    processLatestImage(width, height)
                }
                delay(frameIntervalMs)
            }
        }
    }

    private fun processLatestImage(width: Int, height: Int) {
        val reader = imageReader ?: return
        try {
            val image = reader.acquireLatestImage() ?: return
            val planes = image.planes
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * width

            val bitmap = Bitmap.createBitmap(
                width + rowPadding / pixelStride,
                height,
                Bitmap.Config.ARGB_8888
            )
            bitmap.copyPixelsFromBuffer(buffer)
            image.close()

            // Crop out stride padding if needed
            val cleanBitmap = if (rowPadding == 0) {
                bitmap
            } else {
                Bitmap.createBitmap(bitmap, 0, 0, width, height)
            }

            // Compress to JPEG 60%
            val out = ByteArrayOutputStream()
            cleanBitmap.compress(Bitmap.CompressFormat.JPEG, 60, out)
            val jpegBytes = out.toByteArray()
            _latestFrame.value = jpegBytes

            if (cleanBitmap != bitmap) {
                cleanBitmap.recycle()
            }
            bitmap.recycle()
        } catch (e: Exception) {
            Log.w(TAG, "Error acquiring screen frame: ${e.message}")
        }
    }

    fun setAdaptiveFrameRate(highFrequency: Boolean) {
        frameIntervalMs = if (highFrequency) 500L else 2000L
    }

    private fun stopCapture() {
        _isSharing.value = false
        _isPaused.value = false
        captureJob?.cancel()
        captureJob = null

        try {
            virtualDisplay?.release()
            virtualDisplay = null
            imageReader?.close()
            imageReader = null
            mediaProjection?.stop()
            mediaProjection = null
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing MediaProjection: ${e.message}")
        }
    }

    override fun onDestroy() {
        stopCapture()
        if (instance == this) {
            instance = null
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
