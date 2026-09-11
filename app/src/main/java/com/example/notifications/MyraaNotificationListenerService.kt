package com.example.notifications

import android.app.Notification
import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class NotificationModel(
    val key: String,
    val packageName: String,
    val appName: String,
    val title: String,
    val message: String,
    val timestamp: Long,
    val canReply: Boolean
)

class MyraaNotificationListenerService : NotificationListenerService() {

    companion object {
        private const val TAG = "NotificationListener"

        @Volatile
        var instance: MyraaNotificationListenerService? = null
            private set

        private val _notifications = MutableStateFlow<List<NotificationModel>>(emptyList())
        val notifications: StateFlow<List<NotificationModel>> = _notifications.asStateFlow()

        val isServiceRunning: Boolean
            get() = instance != null
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        Log.i(TAG, "MyraaNotificationListenerService connected")
        refreshActiveNotifications()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        processNotification(sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn == null) return
        _notifications.value = _notifications.value.filter { it.key != sbn.key }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
        }
    }

    fun refreshActiveNotifications() {
        try {
            val active = activeNotifications ?: return
            val list = mutableListOf<NotificationModel>()
            for (sbn in active) {
                val model = mapNotification(sbn)
                if (model != null && !isSpam(model)) {
                    list.add(model)
                }
            }
            _notifications.value = list.sortedByDescending { it.timestamp }.take(30)
        } catch (e: Exception) {
            Log.e(TAG, "Error refreshing notifications: ${e.message}")
        }
    }

    private fun processNotification(sbn: StatusBarNotification) {
        val model = mapNotification(sbn) ?: return
        if (isSpam(model)) return

        val current = _notifications.value.toMutableList()
        current.removeAll { it.key == model.key }
        current.add(0, model)
        _notifications.value = current.take(30)
        Log.i(TAG, "Processed notification from ${model.appName}: ${model.title} - ${model.message}")
    }

    private fun mapNotification(sbn: StatusBarNotification): NotificationModel? {
        val n = sbn.notification ?: return null
        val extras = n.extras ?: return null

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: ""

        if (title.isBlank() && text.isBlank()) return null

        val pkg = sbn.packageName ?: ""
        val appName = when {
            pkg.contains("whatsapp") -> "WhatsApp"
            pkg.contains("telegram") -> "Telegram"
            pkg.contains("messaging") -> "Messages"
            pkg.contains("gmail") -> "Gmail"
            pkg.contains("instagram") -> "Instagram"
            else -> try {
                val appInfo = packageManager.getApplicationInfo(pkg, 0)
                packageManager.getApplicationLabel(appInfo).toString()
            } catch (e: Exception) {
                pkg.substringAfterLast(".")
            }
        }

        val hasReplyAction = findQuickReplyAction(n) != null

        return NotificationModel(
            key = sbn.key,
            packageName = pkg,
            appName = appName,
            title = title,
            message = text,
            timestamp = sbn.postTime,
            canReply = hasReplyAction
        )
    }

    private fun isSpam(model: NotificationModel): Boolean {
        val content = "${model.title} ${model.message}".lowercase()
        return content.contains("percent off") ||
                content.contains("cashback") ||
                content.contains("discount") ||
                content.contains("sale ends") ||
                content.contains("loan approved") ||
                content.contains("recharge now")
    }

    private fun findQuickReplyAction(notification: Notification): Pair<Notification.Action, RemoteInput>? {
        val actions = notification.actions ?: return null
        for (action in actions) {
            val remoteInputs = action.remoteInputs ?: continue
            for (ri in remoteInputs) {
                if (ri.allowFreeFormInput) {
                    return Pair(action, ri)
                }
            }
        }
        return null
    }

    /**
     * Sends an external reply only AFTER explicit user confirmation!
     */
    fun sendConfirmedReply(key: String, replyText: String): Pair<Boolean, String> {
        val active = activeNotifications ?: return Pair(false, "No active notifications")
        val targetSbn = active.find { it.key == key }
            ?: return Pair(false, "Notification no longer available in status bar")

        val pair = findQuickReplyAction(targetSbn.notification)
            ?: return Pair(false, "This notification does not support in-line replies")

        val (action, remoteInput) = pair
        return try {
            val intent = Intent()
            val bundle = Bundle()
            bundle.putCharSequence(remoteInput.resultKey, replyText)
            RemoteInput.addResultsToIntent(arrayOf(remoteInput), intent, bundle)
            action.actionIntent.send(this, 0, intent)
            Pair(true, "Reply sent successfully: '$replyText'")
        } catch (e: Exception) {
            Log.e(TAG, "Failed sending reply: ${e.message}", e)
            Pair(false, "Failed to dispatch reply: ${e.message}")
        }
    }
}
