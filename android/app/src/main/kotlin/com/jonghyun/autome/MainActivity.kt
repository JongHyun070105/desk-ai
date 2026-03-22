package com.jonghyun.autome

import android.content.Intent
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.jonghyun.autome.data.AppDatabase
import com.jonghyun.autome.data.MessageEntity
import com.jonghyun.autome.utils.PiiMasker

class MainActivity : FlutterActivity() {
    private val CHANNEL = "com.jonghyun.autome/native"

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND) {
            val uri = intent.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM)
            if (uri != null) {
                processSharedFile(uri)
            }
        }
    }

    private fun processSharedFile(uri: android.net.Uri) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val inputStream = contentResolver.openInputStream(uri)
                val reader = inputStream?.bufferedReader()
                val content = reader?.readText() ?: ""
                parseAndInsertChatLog(content, "shared_file_import")
            } catch (e: Exception) {
                android.util.Log.e("AutoMeCaptured", "Failed to process shared file: $e")
            }
        }
    }

    private fun processLocalFile(filePath: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val file = java.io.File(filePath)
                val content = file.readText()
                parseAndInsertChatLog(content, "local_file_import")
            } catch (e: Exception) {
                android.util.Log.e("AutoMeCaptured", "Failed to process local file: $e")
            }
        }
    }

    private suspend fun parseAndInsertChatLog(content: String, source: String) {
        // 한 줄씩 파싱하여 다중 라인 메시지 지원
        val dateRegex = Regex("-+ (\\d{4})년 (\\d{1,2})월 (\\d{1,2})일 .+ -+")
        val messageRegex = Regex("^\\[(.+?)\\] \\[(.+?)\\] (.+)$")
        
        val messages = mutableListOf<MessageEntity>()
        var currentSender = ""
        var currentMessage = StringBuilder()
        var currentTimestamp = System.currentTimeMillis() - 100000000 // 과거 시간 부여
        
        content.lines().forEach { line ->
            if (dateRegex.matches(line)) return@forEach
            
            val match = messageRegex.find(line)
            if (match != null) {
                if (currentSender.isNotEmpty()) {
                    messages.add(MessageEntity(
                        roomId = source,
                        sender = currentSender,
                        message = PiiMasker.maskText(currentMessage.toString().trimEnd()),
                        timestamp = currentTimestamp++,
                        isSentByMe = currentSender == "나" || currentSender == "회원님"
                    ))
                    currentMessage.clear()
                }
                currentSender = match.groupValues[1]
                currentMessage.append(match.groupValues[3])
            } else if (line.trim().isNotEmpty() && currentSender.isNotEmpty()) {
                currentMessage.append("\n").append(line)
            }
        }
        
        if (currentSender.isNotEmpty() && currentMessage.isNotEmpty()) {
            messages.add(MessageEntity(
                roomId = source,
                sender = currentSender,
                message = PiiMasker.maskText(currentMessage.toString().trimEnd()),
                timestamp = currentTimestamp,
                isSentByMe = currentSender == "나" || currentSender == "회원님"
            ))
        }
        
        if (messages.isNotEmpty()) {
            val db = AppDatabase.getDatabase(applicationContext)
            db.messageDao().insertMessages(messages)
            android.util.Log.d("AutoMeCaptured", "Chat Log Imported: ${messages.size} msgs from $source")
        }
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler { call, result ->
            when (call.method) {
                "openAccessibilitySettings" -> {
                    startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    result.success(null)
                }
                "openNotificationSettings" -> {
                    startActivity(Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    result.success(null)
                }
                "processFile" -> {
                    val filePath = call.argument<String>("filePath")
                    if (filePath != null) {
                        processLocalFile(filePath)
                        result.success(true)
                    } else {
                        result.error("INVALID_ARGUMENT", "filePath is required", null)
                    }
                }
                "checkServicesEnabled" -> {
                    val accessibilityEnabled = isAccessibilityServiceEnabled()
                    val notificationListenerEnabled = isNotificationListenerServiceEnabled()
                    result.success(accessibilityEnabled && notificationListenerEnabled)
                }
                "getMessageCount" -> {
                    CoroutineScope(Dispatchers.IO).launch {
                        val db = AppDatabase.getDatabase(applicationContext)
                        val count = db.messageDao().getMessageCount()
                        launch(Dispatchers.Main) {
                            result.success(count)
                        }
                    }
                }
                "getLatestMessages" -> {
                    CoroutineScope(Dispatchers.IO).launch {
                        val db = AppDatabase.getDatabase(applicationContext)
                        val messages = db.messageDao().getAllRecentMessages()
                        val resultList = messages.map {
                            mapOf(
                                "sender" to it.sender,
                                "message" to it.message,
                                "timestamp" to it.timestamp,
                                "isSentByMe" to it.isSentByMe
                            )
                        }
                        launch(Dispatchers.Main) {
                            result.success(resultList)
                        }
                    }
                }
                else -> result.notImplemented()
            }
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expectedService = "$packageName/${com.jonghyun.autome.services.AutoMeAccessibilityService::class.java.canonicalName}"
        val enabledServices = android.provider.Settings.Secure.getString(contentResolver, android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        return enabledServices?.contains(expectedService) == true
    }

    private fun isNotificationListenerServiceEnabled(): Boolean {
        val flat = android.provider.Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return flat?.contains(packageName) == true
    }
}
