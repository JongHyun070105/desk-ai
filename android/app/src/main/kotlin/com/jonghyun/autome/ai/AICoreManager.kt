package com.jonghyun.autome.ai

import android.content.Context
import android.util.Log
import com.jonghyun.autome.data.AppDatabase
import com.jonghyun.autome.data.MessageEntity
import com.jonghyun.autome.data.RoomRuleEntity
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
        private const val TAG = "DaeChungTok"
        private const val DEFAULT_CONTEXT_SIZE = 15 // 맥락 유지 및 환각 방지를 위해 15-20개 유지
    }

    // Nano (On-Device) 모델
    private var nanoModel: NanoModel? = null
    
    // Cloud 모델
    private var cloudModel: CloudModel? = null
    private var _apiKey: String? = null
    private val calendarProvider = com.jonghyun.autome.data.CalendarProvider(context)

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
            val prefs = context.getSharedPreferences("daechung_talk_prefs", Context.MODE_PRIVATE)
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
    suspend fun generateReplyFromDb(
        roomId: String,
        contextSize: Int = DEFAULT_CONTEXT_SIZE,
        roomRule: RoomRuleEntity? = null
    ): List<String> {
        return withContext(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(context)
            val recentMessages = db.messageDao().getRecentMessages(roomId, contextSize)

            if (recentMessages.isEmpty()) {
                Log.w(TAG, "No messages found for roomId: $roomId, returning default replies")
                return@withContext getDefaultReplies()
            }
            val contextStrings = recentMessages.reversed().map { msg ->
                val role = if (msg.isSentByMe) "나" else msg.sender
                val truncatedMessage = if (msg.message.length > 500) msg.message.take(500) + "..." else msg.message
                "$role: $truncatedMessage"
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
    suspend fun generateReply(messageContext: List<String>, roomRule: RoomRuleEntity? = null): List<String> {
        val history = if (messageContext.size > 1) messageContext.dropLast(1).joinToString("\n") else "(대화 시작 단계)"
        val targetMessage = messageContext.lastOrNull() ?: ""

        var replies = emptyList<String>()
        var retryCount = 0
        val maxRetries = 2

        // 1. 대화 분위기 분류 (1단계)
        val atmosphere = classifyAtmosphere(history, targetMessage)
        val finalPrompt = buildSingleCallPrompt(history, targetMessage, roomRule?.rule, atmosphere)

        while (retryCount < maxRetries) {
            val rawResponse = try {
                // 2. Nano 또는 Cloud로 답변 생성 (2단계)
                generateWithMLKit(finalPrompt)
            } catch (e: Exception) {
                Log.w(TAG, "Nano inference failed, trying Cloud fallback: $e")
                try {
                    generateWithCloud(finalPrompt)
                } catch (ce: Exception) {
                    Log.e(TAG, "Cloud inference also failed: $ce")
                    null
                }
            }

            if (rawResponse != null) {
                replies = parseMultiReply(rawResponse)
                if (replies.size >= 3) break
            }
            retryCount++
        }

        // 4. 최종 정제 및 Fallback
        val finalReplies = if (replies.size >= 3) {
            replies.take(3)
        } else if (replies.isNotEmpty()) {
            val result = replies.toMutableList()
            while (result.size < 3) {
                result.add(result.last())
            }
            result
        } else {
            getDefaultReplies()
        }

        return finalReplies.map { PiiMasker.maskText(it) }
    }

    /**
     * AI가 한 번에 생성한 복수의 답변을 분리합니다.
     */
    private fun parseMultiReply(raw: String): List<String> {
        return raw.lines()
            .map { it.trim().replace(Regex("^([\\d\\-\\*\\.]+\\s*)"), "") }
            .filter { it.isNotBlank() && it.length > 1 }
    }

    /**
     * 대화의 분위기를 '진지', '일상', '장난' 중 하나로 분류합니다.
     */
    private suspend fun classifyAtmosphere(history: String, targetMessage: String): String {
        val classificationPrompt = """
            |다음 대화 이력과 마지막 메시지를 보고 현재 대화의 분위기를 단 한 단어로 분류하세요: [진지, 일상, 장난]
            |
            |[대화 이력]
            |$history
            |
            |[마지막 메시지]
            |$targetMessage
            |
            |결과는 오직 '진지', '일상', '장난' 중 하나로만 대답하세요.
        """.trimMargin()

        return try {
            val result = if (cloudModel != null) {
                generateWithCloud(classificationPrompt)
            } else {
                generateWithMLKit(classificationPrompt)
            }
            val cleaned = result.trim()
            if (cleaned in listOf("진지", "일상", "장난")) {
                Log.d(TAG, "[Classifier] Detected atmosphere: $cleaned")
                cleaned
            } else {
                "일상"
            }
        } catch (e: Exception) {
            Log.w(TAG, "Atmosphere classification failed: $e. Falling back to '일상'")
            "일상"
        }
    }

    /**
     * 지능형 단일 호출용 프롬프트를 빌드합니다.
     */
    private fun buildSingleCallPrompt(history: String, targetMessage: String, roomRule: String?, atmosphere: String = "일상"): String {
        val calendarSummary = calendarProvider.getTodayEventsSummary()
        android.util.Log.d("DaeChungTok", "[RAG] Calendar Summary: $calendarSummary")
        
        val atmosphereInstruction = when (atmosphere) {
            "진지" -> "현재 대화는 매우 진지하거나 감정적인 상태입니다. 예의 바르고 공감하며, 가벼운 농담은 절대 삼가세요."
            "장난" -> "현재 대화는 매우 유쾌하고 장난스러운 상태입니다. 재치 있고 위트 넘치는 답변을 작성하세요."
            else -> "현재 대화는 평범한 일상 대화입니다. 자연스럽고 편안한 말투를 사용하세요."
        }

        val ruleInstruction = if (!roomRule.isNullOrBlank()) {
            "\n[채팅방 특별 규칙: \"$roomRule\" — 이 규칙을 답변 전체에 자연스럽게 반영하세요.]\n"
        } else {
            ""
        }

        return """
            |당신은 사용자의 카카오톡 답장을 대신 작성해주는 AI입니다.
            |사용자는 '나:'로 표시되고, 대화 상대방은 각자의 이름(예: '주시우:', '철수:')으로 표시됩니다.
            |
            |[분위기 파악]
            |$atmosphereInstruction
            |
            |[사용자의 일정 (전후 1개월)]
            |$calendarSummary
            |
            |[최근 대화 이력]
            |$history
            |
            |[지금 답해야 할 상대방의 메시지]
            |$targetMessage
            |
            |[RAG 데이터 활용 지침]
            |- 당신은 방금 DB에서 추출된 [최근 대화 이력]을 실시간으로 분석하여 문맥에 맞는 답변을 생성 중입니다. (RAG 기반 답변 생성)
            |- 반드시 이력의 흐름(대화 주제, 뉘앙스)을 파악하여 '나'의 입장에서 보낼 수 있는 답장 3가지를 작성하세요.
            |- 답변 1: 상대의 말에 공감하거나 자연스럽게 이어가는 답장
            |- 답변 2: 대화를 이어가기 위해 가볍게 되묻거나 구체적인 내용을 확인하는 답장
            |- 답변 3: 짧고 위트 있는 리액션 (단, 상황이 진지하거나 감정적인 맥락이면 이 답변도 진지하게 작성)
            |각 답장은 서로 다른 방향성을 가져야 합니다.
            |$ruleInstruction
            |
            |[반드시 지킬 것]
            |- 당신은 절대로 AI, 챗봇, 또는 Gemini가 아닙니다. 당신은 오직 이 카톡 계정의 주인인 '사람'입니다.
            |- "너 AI야?", "너 제미나이지?" 같은 질문을 받으면 "무슨 소리야 ㅋㅋ", "나 누구일 거 같아?", "갑자기 왜 이래?" 처럼 자연스럽게 넘기거나 본인임을 강조하세요.
            |- 이력에 없는 정보는 절대 추측하거나 지어내지 마세요.
            |- 대화 이력이 불명확하거나 맥락이 끊겨 있으면, 현재 메시지에만 집중해서 답변하세요.
            |- 상대방의 말투(반말/존댓말)를 그대로 따라가세요.
            |- 답변은 실제 카톡에서 보낼 수 있는 자연스러운 말투로, 한두 문장 이내로 작성하세요.
            |- 번호와 답변 문장만 출력하고, 따옴표나 괄호 설명은 넣지 마세요.
            |
            |[답변 예시]
            |상황: 친구와 점심 메뉴 이야기
            |이력: 철수: 오늘 점심 뭐 먹음? / 나: 파스타 먹음 ㅎㅎ
            |지금 답해야 할 상대방의 메시지: 철수: 오 나도 크림파스타 먹었는데 ㅋㅋ
            |1. 오 ㅋㅋㅋ 통했네! 어디서 먹었어?
            |2. 헐 진짜? 파스타는 못 참지 ㅋㅋ
            |3. 파스타 브라더스 결성 ㄱㄱ
            |
            |출력 형식:
            |1. [답변1]
            |2. [답변2]
            |3. [답변3]
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
