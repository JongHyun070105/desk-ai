package com.jonghyun.autome.services

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.jonghyun.autome.data.AppDatabase
import com.jonghyun.autome.data.MessageEntity
import com.jonghyun.autome.utils.PiiMasker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AutoMeNotificationListenerService : NotificationListenerService() {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val TAG = "AutoMeCaptured"

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val extras = sbn?.notification?.extras
        val title = extras?.getString("android.title") ?: "Unknown"
        val text = extras?.getCharSequence("android.text")?.toString() ?: ""

        if (text.isNotEmpty()) {
            Log.d(TAG, "Captured Received Notification: From=$title, Text=$text")
            saveReceivedMessage(title, text)
        }
    }

    private fun saveReceivedMessage(sender: String, text: String) {
        val maskedText = PiiMasker.maskText(text)
        scope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            val message = MessageEntity(
                roomId = "notification_received",
                sender = sender,
                message = maskedText,
                timestamp = System.currentTimeMillis(),
                isSentByMe = false
            )
            db.messageDao().insertMessage(message)
            Log.d(TAG, "Received message saved to DB: $maskedText")
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
    }
}
