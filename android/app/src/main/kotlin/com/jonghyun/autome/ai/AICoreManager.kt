package com.jonghyun.autome.ai

import android.content.Context
import android.util.Log

class AICoreManager(private val context: Context) {
    companion object {
        private const val TAG = "AICoreManager"
    }

    fun generateReply(messageContext: List<String>): List<String> {
        Log.d(TAG, "AICore generating reply based on context. length: \${messageContext.size}")
        
        // 실제 안드로이드 14 이상 AICore (Gemini Nano) SDK 호출 연결 예정
        // 현재는 모델이 완전히 바인딩되기 전까지 사용할 Fallback 혹은 더미 응답
        return listOf(
            "네, 확인했습니다.",
            "지금은 어렵습니다.",
            "글쎄요, 조금 더 생각해볼게요."
        )
    }
}
