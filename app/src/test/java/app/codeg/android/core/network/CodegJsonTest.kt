package app.codeg.android.core.network

import app.codeg.android.core.model.AgentType
import app.codeg.android.core.model.ConnectBody
import app.codeg.android.core.model.ContentBlock
import app.codeg.android.core.model.ConversationDetail
import app.codeg.android.core.model.ConversationStatus
import app.codeg.android.core.model.ConversationSummary
import app.codeg.android.core.model.ConversationTurnsPage
import app.codeg.android.core.model.FolderDetail
import app.codeg.android.core.model.GetFolderConversationBody
import app.codeg.android.core.model.GetFolderConversationTurnsBody
import app.codeg.android.core.model.ListConversationsBody
import app.codeg.android.core.model.QuestionAnswer
import app.codeg.android.core.model.TurnRole
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Locks down codeg's split casing: requests camelCase, responses snake_case. */
class CodegJsonTest {

    @Test
    fun `request bodies encode camelCase and omit nulls`() {
        val json = CodegJson.request.encodeToString(
            ConnectBody(agentType = AgentType.CLAUDE_CODE, workingDir = "/work"),
        )
        assertTrue(json.contains("\"agentType\":\"claude_code\""))
        assertTrue(json.contains("\"workingDir\":\"/work\""))
        // null optionals are omitted (Swift `encodeIfPresent` parity).
        assertFalse(json.contains("sessionId"))
        assertFalse(json.contains("preferredModeId"))
    }

    @Test
    fun `list conversations request encodes agentType filter`() {
        val json = CodegJson.request.encodeToString(
            ListConversationsBody(search = "login", agentType = "grok", sortBy = "updated"),
        )
        assertTrue(json.contains("\"agentType\":\"grok\""))
        assertTrue(json.contains("\"search\":\"login\""))
        assertFalse(json.contains("folderIds"))
    }

    @Test
    fun `non-null defaults are still encoded`() {
        // declined defaults to false but must be sent (encodeDefaults = true).
        val json = CodegJson.request.encodeToString(QuestionAnswer())
        assertTrue(json.contains("\"declined\":false"))
    }

    @Test
    fun `folder response decodes snake_case keys`() {
        val folder = CodegJson.response.decodeFromString<FolderDetail>(
            """{"id":1,"name":"web","path":"/srv/web","git_branch":"main",
               "default_agent_type":"codex","sort_order":3,"color":"#fff","is_chat":true}""",
        )
        assertEquals("main", folder.gitBranch)
        assertEquals(AgentType.CODEX, folder.defaultAgentType)
        assertEquals(3, folder.sortOrder)
        assertTrue(folder.isChat)
    }

    @Test
    fun `folder response also accepts camelCase isChat`() {
        val folder = CodegJson.response.decodeFromString<FolderDetail>(
            """{"id":2,"name":"chats","path":"/chats","isChat":true}""",
        )
        assertTrue(folder.isChat)
    }

    @Test
    fun `conversation summary decodes enums and counts`() {
        val c = CodegJson.response.decodeFromString<ConversationSummary>(
            """{"id":7,"folder_id":2,"title":"Fix auth","agent_type":"gemini",
               "status":"in_progress","message_count":5,
               "created_at":"2024-01-02T03:04:05Z","updated_at":"2024-01-02T03:04:06Z"}""",
        )
        assertEquals(2, c.folderId)
        assertEquals(AgentType.GEMINI, c.agentType)
        assertEquals(ConversationStatus.IN_PROGRESS, c.status)
        assertEquals(5, c.messageCount)
        assertEquals("Fix auth", c.trimmedTitle)
        assertEquals(0, c.childCount)
        assertEquals(null, c.parentId)
    }

    @Test
    fun `conversation summary decodes delegation parent and child count`() {
        val c = CodegJson.response.decodeFromString<ConversationSummary>(
            """{"id":8,"folder_id":2,"title":"Child","agent_type":"grok",
               "status":"in_progress","message_count":1,"child_count":2,
               "parent_id":7,"parent_tool_use_id":"tool-1","delegation_call_id":"call-1",
               "created_at":"2024-01-02T03:04:05Z","updated_at":"2024-01-02T03:04:06Z"}""",
        )
        assertEquals(2, c.childCount)
        assertEquals(7, c.parentId)
        assertEquals("tool-1", c.parentToolUseId)
        assertEquals("call-1", c.delegationCallId)
    }

    @Test
    fun `unknown enum values fall back instead of throwing`() {
        val c = CodegJson.response.decodeFromString<ConversationSummary>(
            """{"id":1,"folder_id":1,"agent_type":"brand_new_agent","status":"weird_status",
               "message_count":0,"created_at":"2024-01-02T03:04:05Z","updated_at":"2024-01-02T03:04:05Z"}""",
        )
        assertEquals(AgentType.CLAUDE_CODE, c.agentType)
        assertEquals(ConversationStatus.OTHER, c.status)
    }

    @Test
    fun `bare JSON integer decodes (create_conversation)`() {
        assertEquals(42, CodegJson.response.decodeFromString(Int.serializer(), "42"))
    }

    @Test
    fun `get_folder_conversation request encodes tailTurns`() {
        val json = CodegJson.request.encodeToString(
            GetFolderConversationBody(conversationId = 9, tailTurns = 120),
        )
        assertTrue(json.contains("\"conversationId\":9"))
        assertTrue(json.contains("\"tailTurns\":120"))
        assertFalse(json.contains("fromIndex"))
    }

    @Test
    fun `get_folder_conversation_turns request encodes beforeIndex`() {
        val json = CodegJson.request.encodeToString(
            GetFolderConversationTurnsBody(conversationId = 9, beforeIndex = 40, limit = 120),
        )
        assertTrue(json.contains("\"conversationId\":9"))
        assertTrue(json.contains("\"beforeIndex\":40"))
        assertTrue(json.contains("\"limit\":120"))
    }

    @Test
    fun `windowed conversation detail decodes recent turns and paging metadata`() {
        val detail = CodegJson.response.decodeFromString<ConversationDetail>(
            """{"summary":{"id":7,"folder_id":2,"title":"Fix auth","agent_type":"grok",
               "status":"completed","message_count":50,"kind":"regular","title_locked":true,
               "created_at":"2024-01-02T03:04:05Z","updated_at":"2024-01-02T03:04:06Z"},
               "turns":[{"id":"t-40","role":"user","blocks":[{"type":"text","text":"hello"}],
               "timestamp":"2024-01-02T03:04:05Z"}],
               "turns_offset":40,"turns_total":50,"assistant_turns_before_offset":18,
               "prefix_hash":"cbf29ce484222325"}""",
        )
        assertEquals(1, detail.turns.size)
        assertEquals("hello", (detail.turns.single().blocks.single() as ContentBlock.Text).text)
        assertEquals(40, detail.turnsOffset)
        assertEquals(50, detail.turnsTotal)
        assertEquals(18, detail.assistantTurnsBeforeOffset)
        assertEquals("cbf29ce484222325", detail.prefixHash)
        assertEquals(TurnRole.USER, detail.turns.single().role)
    }

    @Test
    fun `older turns page decodes prepend payload`() {
        val page = CodegJson.response.decodeFromString<ConversationTurnsPage>(
            """{"turns":[{"id":"t-20","role":"assistant","blocks":[{"type":"text","text":"earlier"}],
               "timestamp":"2024-01-02T03:04:01Z"}],
               "turns_offset":20,"turns_total":50,"assistant_turns_before_offset":8,
               "prefix_hash":"aaaaaaaaaaaaaaaa","prefix_hash_before_index":"cbf29ce484222325"}""",
        )
        assertEquals(20, page.turnsOffset)
        assertEquals("aaaaaaaaaaaaaaaa", page.prefixHash)
        assertEquals("cbf29ce484222325", page.prefixHashBeforeIndex)
        assertEquals("earlier", (page.turns.single().blocks.single() as ContentBlock.Text).text)
    }

    @Test
    fun `turn usage and duration decode u64 values that overflow Int`() {
        val detail = CodegJson.response.decodeFromString<ConversationDetail>(
            """{"summary":{"id":1,"folder_id":1,"agent_type":"codex","status":"completed",
               "message_count":1,"created_at":"2024-01-02T03:04:05Z","updated_at":"2024-01-02T03:04:06Z"},
               "session_stats":{"total_tokens":3000000000,"total_duration_ms":3000000000},
               "turns":[{"id":"t1","role":"assistant","blocks":[{"type":"text","text":"ok"}],
               "timestamp":"2024-01-02T03:04:05Z",
               "usage":{"input_tokens":2500000000,"output_tokens":1,
               "cache_creation_input_tokens":0,"cache_read_input_tokens":0},
               "duration_ms":3000000000}]}""",
        )
        assertEquals(3_000_000_000L, detail.sessionStats?.totalDurationMs)
        assertEquals(2_500_000_000L, detail.turns.single().usage?.inputTokens)
        assertEquals(3_000_000_000L, detail.turns.single().durationMs)
    }

    @Test
    fun `tool_result images decode from historical transcripts`() {
        val detail = CodegJson.response.decodeFromString<ConversationDetail>(
            """{"summary":{"id":1,"folder_id":1,"agent_type":"claude_code","status":"completed",
               "message_count":1,"created_at":"2024-01-02T03:04:05Z","updated_at":"2024-01-02T03:04:06Z"},
               "turns":[{"id":"t1","role":"assistant","timestamp":"2024-01-02T03:04:05Z","blocks":[
                 {"type":"tool_use","tool_use_id":"r1","tool_name":"Read","input_preview":"{}"},
                 {"type":"tool_result","tool_use_id":"r1","output_preview":"image",
                  "is_error":false,"images":[{"data":"abc","mime_type":"image/png"}]}
               ]}]}""",
        )
        val result = detail.turns.single().blocks[1] as ContentBlock.ToolResult
        assertEquals("abc", result.images.single().data)
        assertEquals("image/png", result.images.single().mimeType)
    }
}
