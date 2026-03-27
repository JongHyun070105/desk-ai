package com.jonghyun.autome

import android.content.Intent
import android.os.Bundle
import android.content.Context
import android.app.RemoteInput
import com.jonghyun.autome.ai.AICoreManager
import com.jonghyun.autome.services.ReplyActionStore
import com.jonghyun.autome.services.DaechungAccessibilityService
import com.jonghyun.autome.services.DaechungNotificationService
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.jonghyun.autome.data.AppDatabase
import com.jonghyun.autome.data.MessageEntity
import com.jonghyun.autome.utils.PiiMasker

class MainActivity : FlutterActivity() {
    private val CHANNEL = "com.jonghyun.daechung_talk/native"

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
            val uri = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, android.net.Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM)
            }
            if (uri != null) {
                processSharedFile(uri)
            }
        }
    }

    private fun processSharedFile(uri: android.net.Uri) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                var fileName = "shared_file_import"
                contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1 && cursor.moveToFirst()) {
                        fileName = cursor.getString(nameIndex).removeSuffix(".txt")
                    }
                }

                val inputStream = contentResolver.openInputStream(uri)
                val bytes = inputStream?.readBytes() ?: ByteArray(0)
                val content = readEncodedBytes(bytes)
                
                val isKakao = content.contains("카카오톡 대화") || content.contains("저장한 날짜")
                val roomId = if (isKakao) {
                    val firstLine = content.lines().firstOrNull() ?: ""
                    val extractedName = if (firstLine.contains(" 카카오톡 대화")) {
                        firstLine.substringBefore(" 카카오톡 대화")
                    } else {
                        fileName
                    }
                    "kakao_$extractedName"
                } else {
                    fileName
                }

                parseAndInsertChatLog(content, roomId, "나")
            } catch (e: Exception) {
                android.util.Log.e("DaeChungTok", "Failed to process shared file: $e")
            }
        }
    }

    private suspend fun parseAndInsertChatLog(content: String, source: String, meSenderName: String) {
        val mobileDateRegex = Regex("-+ (\\d{4})년 (\\d{1,2})월 (\\d{1,2})일 .+ -+")
        val mobileMessageRegex = Regex("^\\[(.+?)\\] \\[(.+?)\\] (.+)$")
        val flexiblePcRegex = Regex("(\\d+.+?\\d+:\\d+)\\s*[,:]\\s*(.+?)\\s*[:|,]\\s*(.+)$")
        
        val messages = mutableListOf<MessageEntity>()
        var currentSender = ""
        val currentMessage = StringBuilder()
        var currentTimestamp = System.currentTimeMillis() - 100000000

        content.lines().forEach { line ->
            val trimmedLine = line.trim()
            if (trimmedLine.isEmpty()) return@forEach
            if (mobileDateRegex.matches(trimmedLine)) return@forEach

            val mobileMatch = mobileMessageRegex.find(trimmedLine)
            if (mobileMatch != null) {
                flushCurrentMessage(messages, source, currentSender, currentMessage, currentTimestamp++, meSenderName)
                currentSender = mobileMatch.groupValues[1]
                currentMessage.append(mobileMatch.groupValues[3])
                return@forEach
            }

            val pcMatch = flexiblePcRegex.find(trimmedLine)
            if (pcMatch != null) {
                flushCurrentMessage(messages, source, currentSender, currentMessage, currentTimestamp++, meSenderName)
                currentSender = pcMatch.groupValues[2]
                currentMessage.append(pcMatch.groupValues[3])
                return@forEach
            }
            
            if (currentSender.isNotEmpty()) {
                if (currentMessage.isNotEmpty()) currentMessage.append("\n")
                currentMessage.append(line)
            }
        }
        
        flushCurrentMessage(messages, source, currentSender, currentMessage, currentTimestamp, meSenderName)
        
        if (messages.isNotEmpty()) {
            val db = AppDatabase.getDatabase(applicationContext)
            db.messageDao().insertMessages(messages)
        }
    }

    private fun flushCurrentMessage(messages: MutableList<MessageEntity>, roomId: String, sender: String, messageBuf: StringBuilder, timestamp: Long, meSenderName: String) {
        if (sender.isNotEmpty() && messageBuf.isNotEmpty()) {
            val rawMsg = messageBuf.toString().trim()
            val maskedMsg = com.jonghyun.autome.utils.PiiMasker.maskText(rawMsg)
            messages.add(MessageEntity(
                roomId = roomId, 
                sender = sender, 
                message = maskedMsg, 
                timestamp = timestamp, 
                isSentByMe = (sender == meSenderName || sender == "나" || sender == "회원님")
            ))
            messageBuf.setLength(0)
        }
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler { call, result ->
            when (call.method) {
                "setGeminiApiKey" -> {
                    val apiKey = call.argument<String>("apiKey")
                    if (apiKey != null) {
                        getSharedPreferences("daechung_talk_prefs", Context.MODE_PRIVATE).edit().putString("gemini_api_key", apiKey).apply()
                        result.success(true)
                    } else result.error("INVALID_ARGUMENT", "apiKey is required", null)
                }
                "openAccessibilitySettings" -> {
                    startActivity(Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    result.success(null)
                }
                "openNotificationSettings" -> {
                    startActivity(Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    result.success(null)
                }
                "isIgnoringBatteryOptimizations" -> {
                    val pm = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
                    result.success(pm.isIgnoringBatteryOptimizations(packageName))
                }
                "requestIgnoreBatteryOptimizations" -> {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                        try {
                            val intent = Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                data = android.net.Uri.parse("package:$packageName")
                            }
                            startActivity(intent)
                            result.success(true)
                        } catch (e: Exception) {
                            result.error("SETTING_ERROR", e.message, null)
                        }
                    } else {
                        result.success(true)
                    }
                }
                "getUsageStatsPermission" -> {
                    val appOps = getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
                    val mode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        appOps.unsafeCheckOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
                    } else {
                        appOps.checkOpNoThrow(android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
                    }
                    result.success(mode == android.app.AppOpsManager.MODE_ALLOWED)
                }
                "extractSenders" -> {
                    val filePath = call.argument<String>("filePath")
                    if (filePath != null) {
                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                val file = java.io.File(filePath)
                                val content = readEncodedFile(file)
                                val mobileRegex = Regex("\\[(.+?)\\] \\[(.+?)\\] (.+)$")
                                val pcRegex = Regex("(\\d+.+?\\d+:\\d+)\\s*[,:]\\s*(.+?)\\s*[:|,]\\s*(.+)$")
                                val senders = mutableSetOf<String>()
                                content.lines().forEach { line ->
                                    val trimmed = line.trim()
                                    mobileRegex.find(trimmed)?.let { senders.add(it.groupValues[1]) }
                                    pcRegex.find(trimmed)?.let { senders.add(it.groupValues[2]) }
                                }
                                launch(Dispatchers.Main) { result.success(senders.toList()) }
                            } catch (e: Exception) {
                                launch(Dispatchers.Main) { result.error("FILE_ERROR", e.message, null) }
                            }
                        }
                    } else result.error("INVALID_ARGUMENT", "filePath required", null)
                }
                "processFile" -> {
                    val filePath = call.argument<String>("filePath")
                    val roomNameArg = call.argument<String>("roomName")
                    val meSenderName = call.argument<String>("meSenderName") ?: "나"
                    if (filePath != null) {
                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                val file = java.io.File(filePath)
                                val content = readEncodedFile(file)
                                // 첫 번째 줄에서 방 이름을 추출하거나 수동 지정된 이름 사용
                                val roomName = roomNameArg ?: extractRoomNameFromHeader(content.lines().firstOrNull())
                                val roomId = roomName
                                parseAndInsertChatLog(content, roomId, meSenderName)
                                
                                // 파일 업로드로 등록한 채팅방은 자동으로 '자동 답장 활성화' 처리
                                val db = AppDatabase.getDatabase(applicationContext)
                                if (db.roomRuleDao().getRuleForRoom(roomId) == null) {
                                    db.roomRuleDao().insertRule(com.jonghyun.autome.data.RoomRuleEntity(
                                        roomId = roomId,
                                        rule = "자연스럽게 대화하고, 별도의 규칙은 없습니다.",
                                        isAutoReplyEnabled = true
                                    ))
                                }
                                
                                withContext(Dispatchers.Main) {
                                    result.success(true)
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    result.error("FILE_ERROR", e.message, null)
                                }
                            }
                        }
                    } else {
                        result.error("INVALID_ARGUMENT", "FilePath is null", null)
                    }
                }
                "migrateOldRoomIds" -> {
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val db = AppDatabase.getDatabase(this@MainActivity)
                            db.openHelper.writableDatabase.execSQL(
                                "UPDATE messages SET roomId = SUBSTR(roomId, 7) WHERE roomId LIKE 'kakao_%'"
                            )
                            withContext(Dispatchers.Main) { result.success(true) }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) { result.error("MIGRATION_ERROR", e.message, null) }
                        }
                    }
                }
                "deleteAllMessages" -> {
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val db = AppDatabase.getDatabase(applicationContext)
                            db.messageDao().deleteAllMessages()
                            db.roomRuleDao().deleteAllRules()
                            withContext(Dispatchers.Main) { result.success(true) }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) { result.error("DELETE_ERROR", e.message, null) }
                        }
                    }
                }
                "checkServicesEnabled" -> {
                    result.success(mapOf(
                        "accessibility" to isAccessibilityServiceEnabled(),
                        "notification" to isNotificationListenerServiceEnabled(),
                        "overlay" to android.provider.Settings.canDrawOverlays(applicationContext),
                        "calendar" to (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_CALENDAR) == android.content.pm.PackageManager.PERMISSION_GRANTED)
                    ))
                }
                "openOverlaySettings" -> {
                    startActivity(Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:$packageName")))
                    result.success(null)
                }
                "requestCalendarPermission" -> {
                    androidx.core.app.ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.READ_CALENDAR), 1001)
                    result.success(true)
                }
                "getMessageCount" -> {
                    CoroutineScope(Dispatchers.IO).launch {
                        val count = AppDatabase.getDatabase(applicationContext).messageDao().getMessageCount()
                        launch(Dispatchers.Main) { result.success(count) }
                    }
                }
                "getLatestMessages" -> {
                    CoroutineScope(Dispatchers.IO).launch {
                        val messages = AppDatabase.getDatabase(applicationContext).messageDao().getAllRecentMessages()
                        launch(Dispatchers.Main) { 
                            result.success(messages.map { mapOf("sender" to it.sender, "message" to it.message, "timestamp" to it.timestamp, "isSentByMe" to it.isSentByMe) }) 
                        }
                    }
                }
                "getChatRooms" -> {
                    CoroutineScope(Dispatchers.IO).launch {
                        val rooms = AppDatabase.getDatabase(applicationContext).messageDao().getDistinctRooms()
                        launch(Dispatchers.Main) { 
                            result.success(rooms.map { mapOf("roomId" to it.roomId, "lastSender" to it.lastSender, "lastMessage" to it.lastMessage, "lastTimestamp" to it.lastTimestamp, "messageCount" to it.messageCount) }) 
                        }
                    }
                }
                "getChatMessages" -> {
                    val roomId = call.argument<String>("roomId")
                    if (roomId != null) {
                        CoroutineScope(Dispatchers.IO).launch {
                            val messages = AppDatabase.getDatabase(applicationContext).messageDao().getMessagesForRoomPaged(roomId, call.argument<Int>("limit") ?: 50, call.argument<Int>("offset") ?: 0)
                            launch(Dispatchers.Main) { 
                                result.success(messages.map { mapOf("id" to it.id, "roomId" to it.roomId, "sender" to it.sender, "message" to it.message, "timestamp" to it.timestamp, "isSentByMe" to it.isSentByMe) }) 
                            }
                        }
                    } else result.error("INVALID_ARGUMENT", "roomId required", null)
                }
                "saveRoomRule" -> {
                    val rid = call.argument<String>("roomId")
                    val rule = call.argument<String>("rule")
                    val enabled = call.argument<Boolean>("isAutoReplyEnabled") ?: true
                    if (rid != null && rule != null) {
                        CoroutineScope(Dispatchers.IO).launch {
                            AppDatabase.getDatabase(applicationContext).roomRuleDao().insertRule(
                                com.jonghyun.autome.data.RoomRuleEntity(rid, rule, enabled)
                            )
                            launch(Dispatchers.Main) { result.success(true) }
                        }
                    } else result.error("INVALID_ARGUMENT", "args required", null)
                }
                "getRoomRule" -> {
                    val rid = call.argument<String>("roomId")
                    if (rid != null) {
                        CoroutineScope(Dispatchers.IO).launch {
                            val r = AppDatabase.getDatabase(applicationContext).roomRuleDao().getRuleForRoom(rid)
                            launch(Dispatchers.Main) { 
                                if (r != null) {
                                    result.success(mapOf(
                                        "roomId" to r.roomId,
                                        "rule" to r.rule,
                                        "isAutoReplyEnabled" to r.isAutoReplyEnabled
                                    ))
                                } else {
                                    result.success(null)
                                }
                            }
                        }
                    } else result.error("INVALID_ARGUMENT", "roomId required", null)
                }
                "generateAiReply" -> {
                    val rid = call.argument<String>("roomId")
                    if (rid != null) {
                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                val db = AppDatabase.getDatabase(applicationContext)
                                val rule = db.roomRuleDao().getRuleForRoom(rid)
                                val aiManager = AICoreManager(applicationContext)
                                val reps = aiManager.generateReplyFromDb(rid, roomRule = rule)
                                aiManager.close()
                                launch(Dispatchers.Main) { result.success(reps) }
                            } catch (e: Exception) {
                                launch(Dispatchers.Main) { result.success(listOf("네, 확인했습니다.", "지금은 어렵습니다.", "글쎄요...")) }
                            }
                        }
                    } else result.error("INVALID_ARGUMENT", "roomId required", null)
                }
                "canDirectReply" -> result.success(call.argument<String>("roomId")?.let { ReplyActionStore.hasReplyAction(it) } ?: false)
                "sendDirectReply" -> {
                    val rid = call.argument<String>("roomId")
                    val text = call.argument<String>("text")
                    if (rid != null && text != null) {
                        ReplyActionStore.get(rid)?.let { action ->
                            try {
                                val rb = Bundle().apply { putCharSequence(action.replyKey, text) }
                                val ri = android.app.RemoteInput.Builder(action.replyKey).build()
                                val intent = Intent().apply { android.app.RemoteInput.addResultsToIntent(arrayOf(ri), this, rb) }
                                action.pendingIntent.send(this@MainActivity, 0, intent)
                                CoroutineScope(Dispatchers.IO).launch {
                                    AppDatabase.getDatabase(applicationContext).messageDao().insertMessage(MessageEntity(roomId = rid, sender = "나", message = PiiMasker.maskText(text), timestamp = System.currentTimeMillis(), isSentByMe = true))
                                }
                                result.success(true)
                            } catch (e: Exception) { result.success(false) }
                        } ?: result.success(false)
                    } else result.error("INVALID_ARGUMENT", "args required", null)
                }
                "deleteChatRoom" -> {
                    val rid = call.argument<String>("roomId")
                    if (rid != null) {
                        CoroutineScope(Dispatchers.IO).launch {
                            val db = AppDatabase.getDatabase(applicationContext)
                            db.messageDao().deleteMessagesByRoom(rid)
                            db.roomRuleDao().deleteRule(rid)
                            ReplyActionStore.remove(rid)
                            launch(Dispatchers.Main) { result.success(true) }
                        }
                    } else result.error("INVALID_ARGUMENT", "roomId required", null)
                }
                else -> result.notImplemented()
            }
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expected = "$packageName/${DaechungAccessibilityService::class.java.canonicalName}"
        val enabled = android.provider.Settings.Secure.getString(contentResolver, android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        return enabled?.contains(expected) == true
    }

    private fun isNotificationListenerServiceEnabled(): Boolean {
        val flat = android.provider.Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return flat?.contains(packageName) == true
    }

    private fun extractRoomNameFromHeader(firstLine: String?): String {
        if (firstLine == null) return "Unknown Room"
        return if (firstLine.contains(" 카카오톡 대화")) {
            firstLine.substringBefore(" 카카오톡 대화")
        } else {
            "Shared Chat"
        }
    }

    private fun readEncodedFile(file: java.io.File): String {
        return readEncodedBytes(file.readBytes())
    }

    private fun readEncodedBytes(bytes: ByteArray): String {
        if (bytes.size >= 2) {
            if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
            if (bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        }
        if (bytes.size >= 4 && bytes[1] == 0.toByte() && bytes[3] == 0.toByte()) return String(bytes, Charsets.UTF_16LE)
        return try { String(bytes, Charsets.UTF_8) } catch (e: Exception) {
            try { String(bytes, java.nio.charset.Charset.forName("EUC-KR")) } catch (e2: Exception) { String(bytes) }
        }
    }
}
