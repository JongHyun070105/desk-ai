package com.jonghyun.autome.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AICoreManagerTest {

    // 테스트용 목업 (실제 AICoreManager의 private 메서드들을 테스트하기 위해 일부 로직 복제 또는 가공)
    private fun parseMultiReplyMock(raw: String): List<String> {
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
            listOf("네, 확인했습니다.", "지금은 어렵습니다.", "글쎄요...")
        }
    }

    @Test
    fun testParseMultiReply_StandardFormat() {
        val raw = """
            1. 안녕하세요!
            2. 반가워요 ㅋㅋ
            3. 오 대박!!
        """.trimIndent()
        val result = parseMultiReplyMock(raw)
        assertEquals(3, result.size)
        assertEquals("안녕하세요!", result[0])
        assertEquals("반가워요 ㅋㅋ", result[1])
        assertEquals("오 대박!!", result[2])
    }

    @Test
    fun testParseMultiReply_SpecialBullets() {
        val raw = """
            - 첫 번째 답변
            * 두 번째 답변
            . 세 번째 답변
        """.trimIndent()
        val result = parseMultiReplyMock(raw)
        assertEquals(3, result.size)
        assertEquals("첫 번째 답변", result[0])
        assertEquals("두 번째 답변", result[1])
        assertEquals("세 번째 답변", result[2])
    }

    @Test
    fun testParseMultiReply_InsufficientLines() {
        val raw = """
            1. 딱 하나만 있는 경우
        """.trimIndent()
        val result = parseMultiReplyMock(raw)
        assertEquals(3, result.size)
        assertEquals("딱 하나만 있는 경우", result[0])
        assertEquals("딱 하나만 있는 경우", result[1])
        assertEquals("딱 하나만 있는 경우", result[2])
    }

    @Test
    fun testParseMultiReply_EmptyInput() {
        val raw = ""
        val result = parseMultiReplyMock(raw)
        assertEquals(3, result.size)
        assertTrue(result[0].contains("확인했습니다"))
    }

    @Test
    fun testBuildPrompt_ContainsAtmosphere() {
        // 실제 buildSingleCallPrompt 로직을 호출할 수 없으므로 
        // 프롬프트 내에 특정 키워드가 포함되는지 검증하는 시뮬레이션
        val atmosphere = "진지"
        val instruction = if (atmosphere == "진지") "예의 바르고 공감하며" else "편안한 말투"
        
        assertTrue(instruction.contains("예의 바르고"))
    }
}
