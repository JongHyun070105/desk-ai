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
                
                // 간단한 카카오톡 대화 정규식 파싱 (예: [이름] [시간] 메시지)
                val regex = Regex("\\[(.+?)\\] \\[(.+?)\\] (.+)")
                val messages = mutableListOf<MessageEntity>()
                
                content.lines().forEach { line ->
                    regex.find(line)?.let { match ->
                        val sender = match.groupValues[1]
                        val message = match.groupValues[3]
                        messages.add(MessageEntity(
                            roomId = "shared_file_import",
                            sender = sender,
                            message = message,
                            timestamp = System.currentTimeMillis(),
                            isSentByMe = sender == "나"
                        ))
                    }
                }
                
                if (messages.isNotEmpty()) {
                    val db = AppDatabase.getDatabase(applicationContext)
                    db.messageDao().insertMessages(messages)
                    android.util.Log.d("AutoMeCaptured", "Shared File Imported: ${messages.size} msgs")
                }
            } catch (e: Exception) {
                android.util.Log.e("AutoMeCaptured", "Failed to process shared file: $e")
            }
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
