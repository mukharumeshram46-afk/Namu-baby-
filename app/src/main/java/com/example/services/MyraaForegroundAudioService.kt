package com.example.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.MyraaApplication
import com.example.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MyraaForegroundAudioService : Service() {

    companion object {
        private const val TAG = "ForegroundAudioService"
        const val CHANNEL_ID = "myraa_foreground_audio_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.example.services.START_AUDIO"
        const val ACTION_STOP = "com.example.services.STOP_AUDIO"

        @Volatile
        var isServiceRunning: Boolean = false
            private set

        private val _isRunningFlow = MutableStateFlow(false)
        val isRunningFlow: StateFlow<Boolean> = _isRunningFlow.asStateFlow()
    }

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startForegroundServiceWithNotification()
                acquireWakeLock()
                isServiceRunning = true
                _isRunningFlow.value = true

                // Start recording through application-level audio manager
                val app = application as? MyraaApplication
                app?.audioInputManager?.startRecording()
                Log.i(TAG, "Foreground voice service started")
            }
            ACTION_STOP -> {
                stopForegroundService()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "MYRAA Companion Live",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Continuous voice session and background audio connection"
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun startForegroundServiceWithNotification() {
        val launchIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Myraa is Active")
            .setContentText("Microphone and full duplex live companion link connected.")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Myraa::VoiceWakeLock")
            wakeLock?.acquire(60 * 60 * 1000L) // max 1 hour safety
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing wake lock: ${e.message}")
        }
        wakeLock = null
    }

    private fun stopForegroundService() {
        isServiceRunning = false
        _isRunningFlow.value = false
        releaseWakeLock()

        val app = application as? MyraaApplication
        app?.audioInputManager?.stopRecording()
        Log.i(TAG, "Foreground voice service stopped")
    }

    override fun onDestroy() {
        stopForegroundService()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
