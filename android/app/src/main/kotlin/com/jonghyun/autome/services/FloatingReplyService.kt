package com.jonghyun.autome.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.RemoteInput
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import com.jonghyun.autome.R
import com.jonghyun.autome.data.AppDatabase
import com.jonghyun.autome.data.MessageEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * FloatingReplyService: 시스템 최상단 플로팅 뷰 서비스
 *
 * 기획서 요구사항:
 * - SYSTEM_ALERT_WINDOW 권한으로 최상단 플로팅 뷰에 3가지 생성 텍스트 노출
 * - 터치 시 알림의 RemoteInput 액션을 트리거하여 백그라운드 답장 전송
 */
class FloatingReplyService : Service() {

    companion object {
        private const val TAG = "FloatingReply"
        private const val CHANNEL_ID = "floating_reply_channel"
        private const val NOTIFICATION_ID = 1001

        const val EXTRA_REPLIES = "extra_replies"
        const val EXTRA_REPLY_KEY = "extra_reply_key"
        const val EXTRA_REPLY_PENDING_INTENT = "extra_reply_pending_intent"
        const val EXTRA_SENDER = "extra_sender"
        const val EXTRA_ROOM_ID = "extra_room_id"

        fun createIntent(
            context: Context,
            replies: ArrayList<String>,
            sender: String,
            roomId: String,
            replyKey: String?,
            replyPendingIntent: PendingIntent?
        ): Intent {
            return Intent(context, FloatingReplyService::class.java).apply {
                putStringArrayListExtra(EXTRA_REPLIES, replies)
                putExtra(EXTRA_SENDER, sender)
                putExtra(EXTRA_ROOM_ID, roomId)
                putExtra(EXTRA_REPLY_KEY, replyKey)
                putExtra(EXTRA_REPLY_PENDING_INTENT, replyPendingIntent)
            }
        }
    }

    private var floatingView: View? = null
    private var windowManager: WindowManager? = null
    private var replyKey: String? = null
    private var replyPendingIntent: PendingIntent? = null
    private var roomId: String? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        if (Build.VERSION.SDK_INT >= 34) { // Build.VERSION_CODES.UPSIDE_DOWN_CAKE
            startForeground(
                NOTIFICATION_ID,
                buildForegroundNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, buildForegroundNotification())
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val replies = intent?.getStringArrayListExtra(EXTRA_REPLIES) ?: arrayListOf()
        val sender = intent?.getStringExtra(EXTRA_SENDER) ?: "알 수 없음"
        roomId = intent?.getStringExtra(EXTRA_ROOM_ID)
        replyKey = intent?.getStringExtra(EXTRA_REPLY_KEY)
        replyPendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(EXTRA_REPLY_PENDING_INTENT, PendingIntent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(EXTRA_REPLY_PENDING_INTENT)
        }

        if (replies.size >= 3) {
            showFloatingView(sender, replies)
        } else {
            Log.w(TAG, "Not enough replies to show floating view: ${replies.size}")
            stopSelf()
        }

        return START_NOT_STICKY
    }

    private fun showFloatingView(sender: String, replies: List<String>) {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            x = 0
            y = 150
        }

        if (floatingView == null) {
            floatingView = inflater.inflate(R.layout.floating_reply_layout, null)
            // 드래그 이동 지원
            setupDragMovement(floatingView!!, layoutParams)
            try {
                windowManager?.addView(floatingView, layoutParams)
                Log.d(TAG, "Floating view added for: $sender")
                
                // 1. 나타날 때 슬라이드 업 + 페이드인 애니메이션
                floatingView?.translationY = 300f
                floatingView?.alpha = 0f
                floatingView?.animate()
                    ?.translationY(0f)
                    ?.alpha(1f)
                    ?.setDuration(400)
                    ?.setInterpolator(android.view.animation.DecelerateInterpolator())
                    ?.withEndAction {
                        // 진입 후 아이들 부유 애니메이션 시작
                        startIdleFloatingAnimation()
                    }
                    ?.start()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to add floating view: $e")
                stopSelf()
                return
            }
        }

        // 컨텐츠 업데이트 및 항목별 순차 애니메이션
        floatingView?.let { view ->
            view.findViewById<TextView>(R.id.tvFloatingTitle)?.text = "$sender 에게 답장"
            
            val btnReply1 = view.findViewById<Button>(R.id.btnReply1)
            val btnReply2 = view.findViewById<Button>(R.id.btnReply2)
            val btnReply3 = view.findViewById<Button>(R.id.btnReply3)
            val btnClose = view.findViewById<View>(R.id.btnClose)

            val buttons = listOf(btnReply1, btnReply2, btnReply3)
            replies.forEachIndexed { index, reply ->
                buttons.getOrNull(index)?.apply {
                    text = reply
                    alpha = 0f
                    scaleX = 0.8f
                    scaleY = 0.8f
                    
                    // 2. 버튼 순차적 팝인 (Staggered Animation)
                    animate()
                        .alpha(1f)
                        .scaleX(1f)
                        .scaleY(1f)
                        .setStartDelay((index * 100).toLong())
                        .setDuration(300)
                        .setInterpolator(android.view.animation.OvershootInterpolator())
                        .start()

                    setOnClickListener { 
                        // 3. 클릭 피드백 애니메이션
                        animate().scaleX(0.95f).scaleY(0.95f).setDuration(100).withEndAction {
                            animate().scaleX(1f).scaleY(1f).setDuration(100).withEndAction {
                                sendReply(reply)
                            }.start()
                        }.start()
                    }
                }
            }

            btnClose?.setOnClickListener {
                removeFloatingView()
                stopSelf()
            }
        }
    }

    /**
     * 아이들 상태의 미세한 부유 효과
     */
    private fun startIdleFloatingAnimation() {
        floatingView?.let { view ->
            val animator = android.animation.ObjectAnimator.ofFloat(view, "translationY", -10f, 10f).apply {
                duration = 2000
                repeatMode = android.animation.ValueAnimator.REVERSE
                repeatCount = android.animation.ValueAnimator.INFINITE
                interpolator = android.view.animation.AccelerateDecelerateInterpolator()
            }
            animator.start()
        }
    }

    /**
     * RemoteInput을 통해 백그라운드 답장을 전송합니다.
     */
    private fun sendReply(replyText: String) {
        val key = replyKey
        val pendingIntent = replyPendingIntent

        if (key != null && pendingIntent != null) {
            try {
                val remoteInputBundle = Bundle().apply {
                    putCharSequence(key, replyText)
                }
                val remoteInput = RemoteInput.Builder(key).build()
                val replyIntent = Intent().apply {
                    RemoteInput.addResultsToIntent(arrayOf(remoteInput), this, remoteInputBundle)
                }
                pendingIntent.send(this, 0, replyIntent)
                Log.d(TAG, "Reply sent via RemoteInput: $replyText")

                // DB에 보낸 메시지 저장
                saveSentMessage(replyText)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send reply: $e")
            }
        } else {
            Log.w(TAG, "No RemoteInput available, reply not sent: $replyText")
        }

        removeFloatingView()
        stopSelf()
    }

    private fun saveSentMessage(text: String) {
        val currentRoomId = roomId ?: return
        scope.launch {
            try {
                val db = AppDatabase.getDatabase(applicationContext)
                val message = MessageEntity(
                    roomId = currentRoomId,
                    sender = "나", // 본인임을 나타내는 발신자명
                    message = text,
                    timestamp = System.currentTimeMillis(),
                    isSentByMe = true
                )
                db.messageDao().insertMessage(message)
                Log.d(TAG, "Sent message saved to DB: roomId=$currentRoomId, text=$text")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save sent message to DB: $e")
            }
        }
    }

    private fun setupDragMovement(view: View, params: WindowManager.LayoutParams) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX - (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager?.updateViewLayout(view, params)
                    true
                }
                else -> false
            }
        }
    }

    private fun removeFloatingView() {
        floatingView?.let { view ->
            try {
                // 사라질 때 페이드아웃 후 제거
                view.animate().alpha(0f).setDuration(200).withEndAction {
                    try {
                        windowManager?.removeView(view)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error removing view in endAction: $e")
                    }
                }.start()
            } catch (e: Exception) {
                Log.e(TAG, "Error starting fadeout animation: $e")
                windowManager?.removeView(view)
            }
        }
        floatingView = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "대충톡 답장 서비스",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "대충톡 플로팅 답장 서비스 알림"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("대충톡")
            .setContentText("답장 추천 서비스 실행 중")
            .setSmallIcon(android.R.drawable.ic_menu_send)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        removeFloatingView()
        super.onDestroy()
    }
}
