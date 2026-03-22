package com.jonghyun.autome.services

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.jonghyun.autome.ai.AICoreManager
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

            // AI 답변 생성 후 플로팅 뷰 표시 시도
            tryShowFloatingReply(sbn, title)
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

    /**
     * 알림에서 RemoteInput 액션을 추출하고, AI 답변을 생성하여
     * FloatingReplyService로 전달합니다.
     */
    private fun tryShowFloatingReply(sbn: StatusBarNotification?, sender: String) {
        if (sbn == null) return

        scope.launch {
            try {
                // RemoteInput 액션 추출
                val remoteInputInfo = extractRemoteInput(sbn.notification)

                // AI 답변 생성
                val aiManager = AICoreManager(applicationContext)
                val replies = aiManager.generateReplyFromDb("notification_received")

                if (replies.size >= 3) {
                    // 메인 스레드에서 FloatingReplyService 시작
                    val intent = FloatingReplyService.createIntent(
                        context = applicationContext,
                        replies = ArrayList(replies),
                        sender = sender,
                        replyKey = remoteInputInfo?.first,
                        replyPendingIntent = remoteInputInfo?.second
                    )

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        applicationContext.startForegroundService(intent)
                    } else {
                        applicationContext.startService(intent)
                    }
                    Log.d(TAG, "FloatingReplyService started for: $sender")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to show floating reply: $e")
            }
        }
    }

    /**
     * 알림에서 RemoteInput 키와 PendingIntent를 추출합니다.
     *
     * @return Pair<RemoteInput 키, PendingIntent> 또는 null
     */
    private fun extractRemoteInput(notification: Notification): Pair<String, PendingIntent>? {
        notification.actions?.forEach { action ->
            val remoteInputs = action.remoteInputs
            if (remoteInputs != null && remoteInputs.isNotEmpty()) {
                val key = remoteInputs[0].resultKey
                val pendingIntent = action.actionIntent
                if (key != null && pendingIntent != null) {
                    Log.d(TAG, "Found RemoteInput key: $key")
                    return Pair(key, pendingIntent)
                }
            }
        }
        return null
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
    }
}
