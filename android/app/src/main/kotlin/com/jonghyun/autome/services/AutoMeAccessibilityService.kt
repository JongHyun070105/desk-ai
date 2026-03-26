package com.jonghyun.autome.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.jonghyun.autome.data.AppDatabase
import com.jonghyun.autome.data.MessageEntity
import com.jonghyun.autome.utils.PiiMasker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AutoMeAccessibilityService : AccessibilityService() {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val TAG = "AutoMeAccessibility"

    private var currentRoomCandidate: String = "알 수 없는 방"
    private var lastWindowPackage: String = ""
    private var lastCapturedText: String = ""
    private var lastSavedMessage: String = ""
    private var lastSavedTime: Long = 0
    private var isSendClicked = false
    private var lastSendClickTime: Long = 0
    private var lastTitleSearchTime: Long = 0

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Service Connected - Initializing Optimize Info")
        
        val info = AccessibilityServiceInfo().apply {
            // 카카오톡, 구글/삼성 메시지, 기본 SMS 및 시스템 UI 타겟팅 (범위 확장)
            packageNames = arrayOf(
                "com.kakao.talk", 
                "com.google.android.apps.messaging", 
                "com.samsung.android.messaging", 
                "com.android.mms",
                "com.android.systemui"
            )
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                        AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                        AccessibilityEvent.TYPE_VIEW_CLICKED or
                        AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 100 // 이벤트가 너무 빈번하게 오지 않도록 딜레이 설정
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        this.serviceInfo = info
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val packageName = event.packageName?.toString() ?: ""
        
        // 1. 윈도우/컨텐츠 변경 시 방 이름 트래킹
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            
            val isMsgApp = (packageName == "com.kakao.talk" || 
                            packageName == "com.google.android.apps.messaging" ||
                            packageName == "com.samsung.android.messaging" ||
                            packageName == "com.android.mms")
            
            if (isMsgApp) {
                val now = System.currentTimeMillis()
                // CONTENT_CHANGED가 너무 빈번하므로 최소 500ms 간격으로만 타이틀 탐색 (CPU 최적화)
                if (now - lastTitleSearchTime > 500 || event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                    val detectedRoom = findRoomTitle()
                    if (detectedRoom != "알 수 없는 방") {
                        currentRoomCandidate = detectedRoom
                    }
                    lastTitleSearchTime = now
                }
                lastWindowPackage = packageName
            }
        }

        // 2. 전송 버튼 클릭 감지
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            val viewId = event.source?.viewIdResourceName ?: ""
            val isMsgApp = (packageName == "com.kakao.talk" || 
                            packageName == "com.google.android.apps.messaging" ||
                            packageName == "com.samsung.android.messaging" ||
                            packageName == "com.android.mms")
            
            if (isMsgApp && (viewId.endsWith("send_button") || viewId.endsWith("chat_send_button") || 
                viewId.endsWith("send_message_button_icon") || viewId.endsWith("message_send_button"))) {
                isSendClicked = true
                lastSendClickTime = System.currentTimeMillis()
                Log.d(TAG, "Send button clicked in $packageName")
            }
        }

        // 3. 텍스트 변화 감지 (메시지 캡처)
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) {
            // 지원하는 메시지 앱 내의 입력창(EditText)에서 발생하는 이벤트만 처리
            val msgPackages = setOf(
                "com.kakao.talk", "com.google.android.apps.messaging", 
                "com.samsung.android.messaging", "com.android.mms"
            )
            val isMsgApp = msgPackages.contains(packageName) || msgPackages.contains(lastWindowPackage)
            if (!isMsgApp) return
            
            val source = event.source
            if (source?.className != "android.widget.EditText") {
                return
            }

            val text = event.text.joinToString("")
            
            if (text.isEmpty() && lastCapturedText.isNotBlank()) {
                val timeSinceClick = System.currentTimeMillis() - lastSendClickTime
                val likelySent = isSendClicked || timeSinceClick < 1000

                if (likelySent && lastCapturedText != lastSavedMessage) {
                    var currentRoom = findRoomTitle()
                    if (currentRoom == "알 수 없는 방") {
                        currentRoom = currentRoomCandidate
                    }

                    if (currentRoom != "알 수 없는 방") {
                        saveSentMessage(lastCapturedText, currentRoom)
                        lastSavedMessage = lastCapturedText
                        lastSavedTime = System.currentTimeMillis()
                    }
                }
                isSendClicked = false
                lastCapturedText = ""
            } else if (text.isNotEmpty()) {
                lastCapturedText = text
            }
        }
    }

    private fun findRoomTitle(): String {
        val rootNode = rootInActiveWindow ?: return "알 수 없는 방"
        val candidates = mutableListOf<Pair<String, Int>>()
        collectTitleCandidates(rootNode, candidates)
        
        return candidates.filter { it.first.isNotBlank() && it.first.length < 50 }
            .minByOrNull { it.second }?.first ?: "알 수 없는 방"
    }

    private fun collectTitleCandidates(node: AccessibilityNodeInfo?, candidates: MutableList<Pair<String, Int>>) {
        if (node == null) return
        
        val bounds = android.graphics.Rect()
        node.getBoundsInScreen(bounds)

        if (bounds.top in 50..450) {
            val viewId = node.viewIdResourceName ?: ""
            val isTitleId = viewId.endsWith(":id/title") || 
                            viewId.endsWith(":id/chat_room_name") || 
                            viewId.endsWith(":id/toolbar_title")
            
            if (isTitleId) {
                node.text?.toString()?.let { candidates.add(it to bounds.top) }
            } else if (node.className == "android.widget.TextView" && bounds.height() in 40..150) {
                val txt = node.text?.toString() ?: ""
                if (txt.isNotBlank() && !txt.contains(":") && txt.length < 30) {
                    candidates.add(txt to bounds.top)
                }
            }
        }

        for (i in 0 until node.childCount) {
            collectTitleCandidates(node.getChild(i), candidates)
        }
    }

    private fun saveSentMessage(text: String, roomName: String) {
        val maskedText = PiiMasker.maskText(text)
        val unifiedRoomId = roomName

        scope.launch {
            try {
                val db = AppDatabase.getDatabase(applicationContext)
                db.messageDao().insertMessage(MessageEntity(
                    roomId = unifiedRoomId,
                    sender = "나",
                    message = maskedText,
                    timestamp = System.currentTimeMillis(),
                    isSentByMe = true
                ))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save message: $e")
            }
        }
    }

    override fun onInterrupt() {}
}
