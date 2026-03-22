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
import androidx.core.app.NotificationCompat
import com.jonghyun.autome.R

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

        fun createIntent(
            context: Context,
            replies: ArrayList<String>,
            sender: String,
            replyKey: String?,
            replyPendingIntent: PendingIntent?
        ): Intent {
            return Intent(context, FloatingReplyService::class.java).apply {
                putStringArrayListExtra(EXTRA_REPLIES, replies)
                putExtra(EXTRA_SENDER, sender)
                putExtra(EXTRA_REPLY_KEY, replyKey)
                putExtra(EXTRA_REPLY_PENDING_INTENT, replyPendingIntent)
            }
        }
    }

    private var floatingView: View? = null
    private var windowManager: WindowManager? = null
    private var replyKey: String? = null
    private var replyPendingIntent: PendingIntent? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildForegroundNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val replies = intent?.getStringArrayListExtra(EXTRA_REPLIES) ?: arrayListOf()
        val sender = intent?.getStringExtra(EXTRA_SENDER) ?: "알 수 없음"
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
        // 기존 뷰가 있으면 제거
        removeFloatingView()

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 16
            y = 100
        }

        val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        floatingView = inflater.inflate(R.layout.floating_reply_layout, null)

        // 제목 설정
        floatingView?.findViewById<TextView>(R.id.tvFloatingTitle)?.text = "$sender 에게 답장"

        // 3가지 페르소나 버튼 설정
        val btnReply1 = floatingView?.findViewById<Button>(R.id.btnReply1)
        val btnReply2 = floatingView?.findViewById<Button>(R.id.btnReply2)
        val btnReply3 = floatingView?.findViewById<Button>(R.id.btnReply3)
        val btnClose = floatingView?.findViewById<Button>(R.id.btnClose)

        btnReply1?.text = replies[0]
        btnReply2?.text = replies[1]
        btnReply3?.text = replies[2]

        btnReply1?.setOnClickListener { sendReply(replies[0]) }
        btnReply2?.setOnClickListener { sendReply(replies[1]) }
        btnReply3?.setOnClickListener { sendReply(replies[2]) }
        btnClose?.setOnClickListener {
            removeFloatingView()
            stopSelf()
        }

        // 드래그 이동 지원
        setupDragMovement(floatingView!!, layoutParams)

        try {
            windowManager?.addView(floatingView, layoutParams)
            Log.d(TAG, "Floating view shown with replies for: $sender")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add floating view: $e")
            stopSelf()
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
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send reply: $e")
            }
        } else {
            Log.w(TAG, "No RemoteInput available, reply not sent: $replyText")
        }

        removeFloatingView()
        stopSelf()
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
        floatingView?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
                Log.e(TAG, "Error removing floating view: $e")
            }
        }
        floatingView = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Auto-Me 답장 서비스",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Auto-Me 플로팅 답장 서비스 알림"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Auto-Me")
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
