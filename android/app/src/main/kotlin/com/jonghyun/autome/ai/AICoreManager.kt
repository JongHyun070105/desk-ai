package com.jonghyun.autome.ai

import android.content.Context
import android.util.Log
import com.jonghyun.autome.data.AppDatabase
import com.jonghyun.autome.data.MessageEntity
import com.jonghyun.autome.utils.PiiMasker
import com.google.mlkit.genai.common.DownloadCallback
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.GenAiException
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.PromptPrefix
import com.google.mlkit.genai.prompt.generateContentRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * AICoreManager: ML Kit GenAI Prompt API 기반 On-Device AI 답변 생성 매니저
 *
 * Google ML Kit GenAI Prompt API (Gemini Nano)를 사용하여 온디바이스에서 답변을 생성합니다.
 * 참고 샘플: https://github.com/googlesamples/mlkit/tree/master/android/genai
 *
 * 기획서 요구사항:
 * - 로컬 DB에서 해당 방의 최근 N개 메시지를 조회하여 프롬프트 컨텍스트로 주입
 * - 3가지 페르소나(수락, 거절, 모호함) 텍스트 생성
 * - PII 마스킹 모듈 적용 필수
 */
class AICoreManager(private val context: Context) {
    companion object {
        private const val TAG = "AICoreManager"
        private const val DEFAULT_CONTEXT_SIZE = 10
    }

    // ML Kit GenAI Prompt API 클라이언트
    private var generativeModel: GenerativeModel? = null

    /**
     * GenerativeModel을 초기화합니다.
     * ML Kit GenAI Prompt API의 Generation.getClient()를 사용합니다.
     */
    private fun ensureModel(): GenerativeModel {
        if (generativeModel == null) {
            generativeModel = Generation.getClient()
            Log.d(TAG, "GenerativeModel initialized via Generation.getClient()")
        }
        return generativeModel!!
    }

    /**
     * AI 기능 사용 가능 여부를 확인합니다.
     *
     * @return FeatureStatus (AVAILABLE, DOWNLOADABLE, DOWNLOADING, UNAVAILABLE)
     */
    suspend fun checkStatus(): Int {
        return try {
            ensureModel().checkStatus()
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
            ensureModel().download().collect { status ->
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
     *
     * @param roomId 대화방 ID
     * @param contextSize 컨텍스트로 사용할 최근 메시지 수
     * @return 3가지 페르소나(수락, 거절, 모호함)의 답변 리스트
     */
    suspend fun generateReplyFromDb(roomId: String, contextSize: Int = DEFAULT_CONTEXT_SIZE): List<String> {
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

            Log.d(TAG, "Generating reply with ${contextStrings.size} context messages for room: $roomId")
            generateReply(contextStrings)
        }
    }

    /**
     * 메시지 컨텍스트 리스트를 받아 3가지 페르소나의 답변을 생성합니다.
     *
     * @param messageContext 대화 맥락 문자열 리스트
     * @return 3가지 페르소나(수락, 거절, 모호함)의 답변 리스트
     */
    suspend fun generateReply(messageContext: List<String>): List<String> {
        Log.d(TAG, "Generating replies based on context. length: ${messageContext.size}")

        val contextBlock = messageContext.joinToString("\n")

        // 3가지 페르소나별 프롬프트 생성
        val personaPrompts = listOf(
            buildPersonaPrompt(contextBlock, "수락",
                "상대의 요청이나 제안에 긍정적으로 동의하는 답변을 한국어로 생성하세요. 친근하고 자연스러운 말투로 1~2문장으로 작성하세요."),
            buildPersonaPrompt(contextBlock, "거절",
                "상대의 요청이나 제안을 정중하게 거절하는 답변을 한국어로 생성하세요. 예의 바르지만 확고한 말투로 1~2문장으로 작성하세요."),
            buildPersonaPrompt(contextBlock, "모호함",
                "상대의 요청이나 제안에 대해 애매하게 답변하세요. 한국어로 확답을 피하면서 자연스러운 말투로 1~2문장으로 작성하세요.")
        )

        // 각 페르소나별로 AI 생성 시도, 실패 시 Fallback
        val replies = mutableListOf<String>()
        for ((index, prompt) in personaPrompts.withIndex()) {
            val reply = try {
                generateWithMLKit(prompt)
            } catch (e: Exception) {
                Log.w(TAG, "ML Kit inference failed for persona $index, using fallback: $e")
                getFallbackReply(index)
            }
            replies.add(PiiMasker.maskText(reply))
        }

        return replies
    }

    /**
     * 페르소나별 프롬프트를 빌드합니다.
     */
    private fun buildPersonaPrompt(contextBlock: String, personaName: String, instruction: String): String {
        return """
            |다음은 최근 대화 내역입니다:
            |---
            |$contextBlock
            |---
            |
            |위 대화의 마지막 메시지에 대한 [$personaName] 톤의 답장을 생성하세요.
            |$instruction
            |답장만 출력하세요. 설명이나 접두사 없이 답장 문장만 작성하세요.
        """.trimMargin()
    }

    /**
     * ML Kit GenAI Prompt API를 사용하여 텍스트를 생성합니다.
     *
     * GenerativeModel.generateContent()를 호출하여 Gemini Nano 온디바이스 추론을 수행합니다.
     */
    private suspend fun generateWithMLKit(prompt: String): String {
        val model = ensureModel()

        // Feature status 확인
        val status = model.checkStatus()
        Log.d(TAG, "ML Kit GenAI feature status: $status")

        // 모델이 사용 불가능하면 예외 발생 → 호출부에서 Fallback 처리
        if (status == 0) { // UNAVAILABLE
            throw Exception("ML Kit GenAI Prompt API is not available on this device")
        }

        // 필요 시 모델 다운로드
        if (status == 1 || status == 2) { // DOWNLOADABLE or DOWNLOADING
            downloadModel()
        }

        // 추론 실행
        val request = generateContentRequest(TextPart(prompt)) {
            temperature = 0.8f
            maxOutputTokens = 256
        }
        val response = model.generateContent(request)

        val generatedText = response.candidates.firstOrNull()?.text
        if (generatedText.isNullOrBlank()) {
            throw Exception("Empty response from ML Kit GenAI")
        }

        Log.d(TAG, "ML Kit generated: ${generatedText.take(50)}...")
        return generatedText.trim()
    }

    /**
     * ML Kit 추론이 실패했을 때 사용하는 Fallback 응답
     */
    private fun getFallbackReply(personaIndex: Int): String {
        return when (personaIndex) {
            0 -> "네, 알겠습니다! 확인했어요."      // 수락
            1 -> "죄송한데 지금은 어려울 것 같아요."   // 거절
            2 -> "음, 조금 더 생각해볼게요."          // 모호함
            else -> "확인했습니다."
        }
    }

    /**
     * 컨텍스트 없이 사용하는 기본 응답
     */
    private fun getDefaultReplies(): List<String> {
        return listOf(
            "네, 확인했습니다.",
            "지금은 어렵습니다.",
            "글쎄요, 조금 더 생각해볼게요."
        )
    }

    /**
     * 리소스 정리. 사용 후 반드시 호출 필요.
     */
    fun close() {
        generativeModel?.close()
        generativeModel = null
        Log.d(TAG, "GenerativeModel closed")
    }
}
