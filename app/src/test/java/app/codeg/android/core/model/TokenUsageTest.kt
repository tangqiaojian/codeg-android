package app.codeg.android.core.model

import app.codeg.android.core.network.CodegJson
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TokenUsageTest {
    @Test
    fun decodesReportWithSnakeCaseResponseFields() {
        val report = CodegJson.response.decodeFromString<TokenUsageReport>(reportJson())

        assertEquals("day", report.bucket)
        assertEquals(1234L, report.totals.totalTokens)
        assertEquals("claude_code", report.byAgent.single().key)
        assertEquals(42, report.topConversations.single().conversationId)
        assertEquals(3, report.streak.currentDays)
    }

    @Test
    fun requestUsesCamelCaseFilterKeys() {
        val encoded = CodegJson.request.encodeToString(
            TokenUsageReportBody(
                TokenUsageFilter(
                    folderIds = listOf(7),
                    agentTypes = listOf("grok"),
                    tzOffsetMinutes = 480,
                    comparePrevious = true,
                ),
            ),
        )

        assertTrue(encoded.contains("\"folderIds\":[7]"))
        assertTrue(encoded.contains("\"agentTypes\":[\"grok\"]"))
        assertTrue(encoded.contains("\"tzOffsetMinutes\":480"))
        assertTrue(encoded.contains("\"comparePrevious\":true"))
    }

    private fun reportJson(): String =
        """
        {
          "range_start": null,
          "range_end": null,
          "bucket": "day",
          "totals": {
            "input_tokens": 100,
            "output_tokens": 200,
            "cache_creation_tokens": 300,
            "cache_read_tokens": 634,
            "total_tokens": 1234,
            "turn_count": 4,
            "conversation_count": 2,
            "duration_ms": 5000,
            "active_days": 3
          },
          "previous_totals": null,
          "series": [],
          "by_folder": [],
          "by_agent": [{"key":"claude_code","label":"claude_code","input_tokens":1,"output_tokens":2,"cache_creation_tokens":0,"cache_read_tokens":0,"total_tokens":3,"turn_count":1,"conversation_count":1}],
          "by_model": [],
          "heatmap": [],
          "top_conversations": [{"conversation_id":42,"title":"Ship it","agent_type":"grok","folder_label":"Repo","total_tokens":3,"turn_count":1,"last_activity_at":"2026-08-19T00:00:00Z"}],
          "streak": {"longest_days":5,"current_days":3,"current_ends_on":"2026-08-19"},
          "first_activity_at": null,
          "last_activity_at": null,
          "truncated": false
        }
        """.trimIndent()
}
