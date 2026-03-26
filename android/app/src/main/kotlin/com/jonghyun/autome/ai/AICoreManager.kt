package com.jonghyun.autome.ai

import android.content.Context
import android.util.Log
import com.jonghyun.autome.data.AppDatabase
import com.jonghyun.autome.data.MessageEntity
import com.jonghyun.autome.utils.PiiMasker
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.prompt.GenerativeModel as NanoModel
import com.google.mlkit.genai.prompt.Generation as NanoGeneration
import com.google.mlkit.genai.prompt.TextPart as NanoTextPart
import com.google.mlkit.genai.prompt.generateContentRequest as nanoRequest
import com.google.ai.client.generativeai.GenerativeModel as CloudModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Properties

/**
 * AICoreManager: ML Kit GenAI (Hybrid) 기반 답변 생성 매니저
 *
 * 기본적으로 On-Device Gemini Nano를 사용하며, 에뮬레이터 환경 등 미지원 시
 * Gemini Cloud API를 Fallback으로 사용하여 테스트 가능하도록 구현합니다.
 */
class AICoreManager(private val context: Context) {
    companion object {
        private const val TAG = "AICoreManager"
        private const val DEFAULT_CONTEXT_SIZE = 50 // RAG 성능 향상을 위해 50개로 확대
    }

    // Nano (On-Device) 모델
    private var nanoModel: NanoModel? = null
    
    // Cloud 모델
    private var cloudModel: CloudModel? = null
    private var _apiKey: String? = null

    init {
        _apiKey = getApiKeyFromProperties()
        if (!_apiKey.isNullOrBlank()) {
            cloudModel = CloudModel(
                modelName = "gemini-2.5-flash-lite",
                apiKey = _apiKey!!
            )
            Log.d(TAG, "Cloud model initialized with API Key")
        }
    }

    /**
     * SharedPreferences에서 Gemini API Key를 가져옵니다.
     */
    private fun getApiKeyFromProperties(): String? {
        return try {
            val prefs = context.getSharedPreferences("autome_prefs", Context.MODE_PRIVATE)
            prefs.getString("gemini_api_key", null)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read API Key from SharedPreferences: $e")
            null
        }
    }

    /**
     * Nano 모델 초기화
     */
    private fun ensureNanoModel(): NanoModel {
        if (nanoModel == null) {
            nanoModel = NanoGeneration.getClient()
            Log.d(TAG, "Nano model initialized via NanoGeneration.getClient()")
        }
        return nanoModel!!
    }

    /**
     * AI 기능 사용 가능 여부를 확인합니다.
     */
    suspend fun checkStatus(): Int {
        return try {
            ensureNanoModel().checkStatus()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check feature status: $e")
            -1 // UNAVAILABLE
        }
    }

    /**
     * 필요 시 모델을 다운로드합니다.
     */
    suspend fun downloadModel() {
        try {
            ensureNanoModel().download().collect { status ->
                when (status) {
                    is DownloadStatus.DownloadStarted ->
                        Log.d(TAG, "Model download started, bytes: ${status.bytesToDownload}")
                    is DownloadStatus.DownloadProgress ->
                        Log.d(TAG, "Model download progress: ${status.totalBytesDownloaded}")
                    is DownloadStatus.DownloadCompleted ->
                        Log.d(TAG, "Model download completed")
                    is DownloadStatus.DownloadFailed ->
                        Log.e(TAG, "Model download failed: ${status.e}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during model download: $e")
        }
    }

    /**
     * 특정 채팅방의 최근 메시지 컨텍스트를 기반으로 3가지 페르소나의 답변을 생성합니다.
     */
    suspend fun generateReplyFromDb(roomId: String, roomRule: String? = null, contextSize: Int = DEFAULT_CONTEXT_SIZE): List<String> {
        return withContext(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(context)
            val recentMessages = db.messageDao().getRecentMessages(roomId, contextSize)

            if (recentMessages.isEmpty()) {
                Log.w(TAG, "No messages found for roomId: $roomId, returning default replies")
                return@withContext getDefaultReplies()
            }
            val contextStrings = recentMessages.reversed().map { msg ->
                val role = if (msg.isSentByMe) "나" else msg.sender
                "$role: ${msg.message}"
            }
            
            // RAG 작동 확인용 로그
            if (contextStrings.isNotEmpty()) {
                Log.d(TAG, "[RAG] Retrieved ${contextStrings.size} messages for context. Last message: \"${contextStrings.last()}\"")
            }

            Log.d(TAG, "Generating reply with ${contextStrings.size} context messages for room: $roomId")
            generateReply(contextStrings, roomRule)
        }
    }

    /**
     * 메시지 컨텍스트 리스트를 받아 문맥에 최적화된 3가지 답변을 한 번에 생성합니다.
     */
    suspend fun generateReply(messageContext: List<String>, roomRule: String? = null): List<String> {
        Log.d(TAG, "Generating 3 intelligent replies based on context. length: ${messageContext.size}")

        val contextBlock = messageContext.joinToString("\n")
        val prompt = buildSingleCallPrompt(contextBlock, roomRule)

        val rawResponse = try {
            // 1. 먼저 Nano (On-Device) 시도
            generateWithMLKit(prompt)
        } catch (e: Exception) {
            Log.w(TAG, "Nano inference failed, trying Cloud fallback: $e")
            
            // 2. 실패 시 Cloud 시도
            try {
                generateWithCloud(prompt)
            } catch (ce: Exception) {
                Log.e(TAG, "Cloud inference also failed: $ce")
                null
            }
        }

        // 3. 응답 파싱 및 정제
        val replies = if (rawResponse != null) {
            parseMultiReply(rawResponse)
        } else {
            getDefaultReplies()
        }

        return replies.map { PiiMasker.maskText(it) }
    }

    /**
     * AI가 한 번에 생성한 3개의 답변을 분리합니다.
     */
    private fun parseMultiReply(raw: String): List<String> {
        val lines = raw.lines()
            .map { it.trim().replace(Regex("^([\\d\\-\\*\\.]+\\s*)"), "") }
            .filter { it.isNotBlank() && it.length > 1 }
        
        return if (lines.size >= 3) {
            lines.take(3)
        } else if (lines.isNotEmpty()) {
            val result = lines.toMutableList()
            while (result.size < 3) {
                result.add(result.last())
            }
            result
        } else {
            getDefaultReplies()
        }
    }

    /**
     * 지능형 단일 호출용 프롬프트를 빌드합니다.
     */
    private fun buildSingleCallPrompt(contextBlock: String, roomRule: String?): String {
        val ruleInstruction = if (!roomRule.isNullOrBlank()) {
            "\n[특별 규칙: \"$roomRule\"]\n"
        } else {
            ""
        }

        return """
            |당신은 사용자의 원활한 채팅을 도와주는 똑똑한 AI 비서입니다.
            |다음은 최근 대화 내역입니다 (가장 하단이 마지막 메시지입니다):
            |---
            |$contextBlock
            |---
            |
            |**미션**: 위 대화 내역의 흐름을 반영하여 가장 마지막 메시지에 대한 자연스러운 답변 3가지를 생성하세요.
            |**핵심 원칙**:
            |1. **최신 메시지 최우선**: 가장 마지막 메시지의 질문이나 내용에 직접적인 답장을 생성하는 것이 기본입니다.
            |2. **배경지식의 자연스러운 활용**: 이전 대화에서 나온 중요한 정보(좋아하는 노래, 음식, 취향, 별명, 이전 약속 등)가 있다면 이를 답변에 **자연스럽게 녹여내세요**. (예: 영화 약속을 정할 때, "아 아까 좋아한다고 했던 거북이 노래 같은 음악 영화 볼래?" 처럼 맥락을 이어가는 식)
            |3. **대화의 연속성**: 단순히 기계적인 답변이 아니라, 이전 대화를 기억하고 있다는 느낌을 주어 사용자가 친밀감을 느끼게 하세요. 
            |4. **주객전도 금지**: 하지만 현재 주제와 너무 동떨어진 옛날 이야기를 뜬금없이 꺼내지는 마세요. 흐름이 끊기지 않는 선에서만 배경지식을 활용하세요.
            |5. **다양한 페르소나**: 상황에 맞춰 '적극적인 공감', '재치 있는 질문', '간결하고 명확한 대답' 등 3가지 답변이 서로 다른 뉘앙스를 가지게 하세요.
            |$ruleInstruction
            |
            |출력 형식:
            |1. [답변 1]
            |2. [답변 2]
            |3. [답변 3]
            |
            |제약 사항:
            |1. 한국어로만 답변하고, 상대방의 말투(반말/존댓말)를 정확히 따르세요.
            |2. 따옴표나 부연 설명 없이 번호와 답변 문장만 출력하세요.
        """.trimMargin()
    }

    /**
     * ML Kit (Nano) 온디바이스 추론
     */
    private suspend fun generateWithMLKit(prompt: String): String {
        val model = ensureNanoModel()
        val status = model.checkStatus()

        if (status == 0) throw Exception("Nano Unavailable")
        if (status == 1 || status == 2) downloadModel()

        val request = nanoRequest(NanoTextPart(prompt)) {
            temperature = 0.8f
            maxOutputTokens = 256
        }
        val response = model.generateContent(request)
        val text = response.candidates.firstOrNull()?.text
        
        if (text.isNullOrBlank()) throw Exception("Empty Nano response")
        return text.trim()
    }

    /**
     * Gemini Cloud API 추론
     */
    private suspend fun generateWithCloud(prompt: String): String {
        val model = cloudModel ?: throw Exception("Cloud model not initialized (No API Key?)")
        
        val response = model.generateContent(prompt)
        val text = response.text
        
        if (text.isNullOrBlank()) throw Exception("Empty Cloud response")
        Log.d(TAG, "Cloud generated: ${text.take(50)}...")
        return text.trim()
    }

    private fun getFallbackReply(personaIndex: Int): String {
        return when (personaIndex) {
            0 -> "정말요? 좋은 소식이네요! 대박이에요."
            1 -> "그렇군요! 그럼 그 이후에는 어떻게 됐나요?"
            2 -> "아하, 확인했습니다!"
            else -> "네, 알겠습니다."
        }
    }

    private fun getDefaultReplies(): List<String> {
        return listOf("네, 확인했습니다.", "지금은 어렵습니다.", "글쎄요, 조금 더 생각해볼게요.")
    }

    fun close() {
        nanoModel?.close()
        nanoModel = null
        cloudModel = null
        Log.d(TAG, "AICore resources closed")
    }
}
