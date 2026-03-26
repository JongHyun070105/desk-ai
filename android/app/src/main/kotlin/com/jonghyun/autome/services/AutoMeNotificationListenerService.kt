package com.jonghyun.autome.services

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
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

    // 지원 패키지: 카카오톡, 구글 메시지, 삼성 메시지, 기본 SMS (사용자 요청 반영)
    private val SUPPORTED_PACKAGES = setOf(
        "com.kakao.talk",                 // 카카오톡
        "com.google.android.apps.messaging", // 구글 메시지
        "com.samsung.android.messaging",  // 삼성 메시지
        "com.android.mms"                 // 기본 SMS
    )

    // 동일한 알림 업데이트로 인한 중복 처리 방지 (깜빡임 해결)
    private val lastCapturedTexts = mutableMapOf<String, String>()
    // AI 답변 생성 쿨다운 관리 (방별 최소 10초 간격)
    private val lastAiReplyTimes = mutableMapOf<String, Long>()
    private val AI_COOLDOWN_MS = 10000L

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return

        val packageName = sbn.packageName ?: ""
        
        // 1. 지원하는 메시징 앱만 처리
        if (!SUPPORTED_PACKAGES.contains(packageName)) {
            // 카테고리가 메시지나 소셜인 경우에도 일단 허용 (유연성)
            val category = sbn.notification?.category
            if (category != Notification.CATEGORY_MESSAGE && category != Notification.CATEGORY_SOCIAL) {
                return
            }
        }

        // 2. 우리 앱 알림 무시
        if (packageName == applicationContext.packageName) {
            return
        }

        val extras = sbn.notification?.extras ?: return
        // MessagingStyle 추출 (단체방 대응)
        val messagingStyle = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(sbn.notification)
        val conversationTitle = messagingStyle?.conversationTitle?.toString()
        val isGroup = extras.getBoolean("android.isGroupConversation")
        // 1순위: MessagingStyle의 단체방 제목, 2순위: android.subText, 3순위: android.summaryText, 4순위: android.title
        val title = if (isGroup) {
            (conversationTitle ?: extras.getCharSequence("android.subText") ?: extras.getCharSequence("android.summaryText") ?: extras.getCharSequence("android.title"))?.toString() ?: "Unknown Group"
        } else {
            extras.getCharSequence("android.title")?.toString() ?: "Unknown"
        }
        val text = extras.getCharSequence("android.text")?.toString() ?: ""

        // 실제 발화자(sender) 추출: MessagingStyle이 있으면 마지막 메시지의 발신자, 없으면 title
        val realSender = if (messagingStyle != null && messagingStyle.messages.isNotEmpty()) {
            messagingStyle.messages.last().person?.name?.toString() ?: title
        } else {
            // 단톡방인 경우 title이 방 이름이므로, 발신자 정보를 찾을 수 없으면 그대로 title 사용 (1:1은 sender가 title과 동일)
            title
        }

        // 3. 제목이나 내용에 시스템성 문구가 포함된 경우 제외
        if (title == "Android 시스템" || title.contains("Auto-Me") || 
            text.contains("running in the background") || text.contains("일기예보")) {
            return
        }

        if (text.isEmpty()) return

        // roomId 생성 (카카오톡의 경우 파일 임포트와 통합을 위해 kakao_상단타이틀 형식 사용)
        val appName = getAppLabel(packageName)
        val roomId = if (packageName == "com.kakao.talk") {
            title // 카카오톡은 접두사 없이 순수 방 이름 사용
        } else {
            "notification_${appName}_$title"
        }

        // 이전에 캡처한 내용과 동일하면 무시 (단순 메타데이터 업데이트 방지)
        if (lastCapturedTexts[roomId] == text) {
            return
        }
        lastCapturedTexts[roomId] = text

        Log.d(TAG, "Captured Notification: pkg=$packageName, Room=$title, Sender=$realSender, Text=$text")

        // DB에 메시지 저장
        saveReceivedMessage(roomId, realSender, text)

        // RemoteInput 추출 후 ReplyActionStore에 저장
        val remoteInputInfo = extractRemoteInput(sbn.notification)
        if (remoteInputInfo != null) {
            ReplyActionStore.put(roomId, ReplyActionStore.ReplyAction(
                replyKey = remoteInputInfo.first,
                pendingIntent = remoteInputInfo.second,
                sender = realSender
            ))
            Log.d(TAG, "RemoteInput stored for roomId=$roomId, sender=$realSender")
        }

        // AI 답변 생성 후 플로팅 뷰 표시
        tryShowFloatingReply(realSender, roomId, remoteInputInfo)
    }

    private fun saveReceivedMessage(roomId: String, sender: String, text: String) {
        val maskedText = PiiMasker.maskText(text)
        scope.launch {
            val db = AppDatabase.getDatabase(applicationContext)
            val message = MessageEntity(
                roomId = roomId,
                sender = sender,
                message = maskedText,
                timestamp = System.currentTimeMillis(),
                isSentByMe = false
            )
            db.messageDao().insertMessage(message)
            Log.d(TAG, "Received message saved to DB: roomId=$roomId, text=$maskedText")
        }
    }

    /**
     * AI 답변을 생성하고 FloatingReplyService로 전달합니다.
     */
    private fun tryShowFloatingReply(
        sender: String,
        roomId: String,
        remoteInputInfo: Pair<String, PendingIntent>?
    ) {
        val now = System.currentTimeMillis()
        val lastTime = lastAiReplyTimes[roomId] ?: 0L
        
        if (now - lastTime < AI_COOLDOWN_MS) {
            Log.d(TAG, "AI Cooldown active for $roomId, skipping reply generation")
            return
        }
        lastAiReplyTimes[roomId] = now

        scope.launch {
            try {
                val db = AppDatabase.getDatabase(applicationContext)
                val rule = db.roomRuleDao().getRuleForRoom(roomId)

                val aiManager = AICoreManager(applicationContext)
                val replies = aiManager.generateReplyFromDb(roomId, roomRule = rule)
                aiManager.close()
                if (replies.size >= 3) {
                    val intent = FloatingReplyService.createIntent(
                        context = applicationContext,
                        replies = ArrayList(replies),
                        sender = sender,
                        roomId = roomId,
                        replyKey = remoteInputInfo?.first,
                        replyPendingIntent = remoteInputInfo?.second
                    )

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        applicationContext.startForegroundService(intent)
                    } else {
                        applicationContext.startService(intent)
                    }
                    Log.d(TAG, "FloatingReplyService started for: $sender (roomId=$roomId) with rule: ${rule ?: "none"}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to show floating reply: $e")
            }
        }
    }

    /**
     * 알림에서 RemoteInput 키와 PendingIntent를 추출합니다.
     */
    private fun extractRemoteInput(notification: Notification): Pair<String, PendingIntent>? {
        // 1. 일반 action 검사
        notification.actions?.forEach { action ->
            val remoteInputs = action.remoteInputs
            if (remoteInputs != null && remoteInputs.isNotEmpty()) {
                val key = remoteInputs[0].resultKey
                val pendingIntent = action.actionIntent
                if (key != null && pendingIntent != null) {
                    Log.d(TAG, "Found RemoteInput key: $key in standard actions")
                    return Pair(key, pendingIntent)
                }
            }
        }
        
        // 2. WearableExtender 검사 (카카오톡 등 일부 앱은 여기에 숨겨둠)
        val wearableExtender = NotificationCompat.WearableExtender(notification)
        wearableExtender.actions.forEach { action ->
            val remoteInputs = action.remoteInputs
            if (remoteInputs != null && remoteInputs.isNotEmpty()) {
                val key = remoteInputs[0].resultKey
                val pendingIntent = action.actionIntent
                if (pendingIntent != null) {
                    Log.d(TAG, "Found RemoteInput key: $key in WearableExtender")
                    return Pair(key, pendingIntent)
                }
            }
        }
        
        return null
    }

    /**
     * 패키지명에서 앱 이름을 추출합니다.
     */
    private fun getAppLabel(packageName: String): String {
        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            // 패키지명에서 마지막 부분 추출
            packageName.substringAfterLast(".")
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
    }
}
