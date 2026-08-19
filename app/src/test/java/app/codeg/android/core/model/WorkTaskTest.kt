package app.codeg.android.core.model

import app.codeg.android.core.network.CodegJson
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkTaskTest {
    @Test
    fun decodesWorkTaskWithSnakeCaseFields() {
        val task = CodegJson.response.decodeFromString<WorkTask>(taskJson())

        assertEquals(12, task.id)
        assertEquals(3, task.folderId)
        assertEquals("Fix login", task.title)
        assertEquals("todo", task.status)
        assertEquals("grok", task.agentType)
        assertEquals(42, task.conversationId)
        assertTrue(task.config is JsonObject)
    }

    @Test
    fun unknownStatusAndFieldsDoNotBreakDecoding() {
        val task = CodegJson.response.decodeFromString<WorkTask>(
            taskJson().replace("\"todo\"", "\"paused_by_server\"")
                .replace("}", ",\"future_field\":true}")
        )

        assertEquals("paused_by_server", task.status)
    }

    @Test
    fun nullConfigIsAllowed() {
        val task = CodegJson.response.decodeFromString<WorkTask>(
            taskJson().replace("\"config\": {\"display_text\": \"Fix login\"}", "\"config\":null")
        )

        assertNull(task.config)
    }

    @Test
    fun createBodyKeepsNestedWorkTaskWireNames() {
        val encoded = CodegJson.request.encodeToString(
            WorkTaskCreateBody(
                draft = WorkTaskDraft(
                    folderId = 3,
                    title = "Fix login",
                    config = WorkTaskConfig(
                        promptBlocks = listOf(PromptInputBlock.Text("Fix login")),
                        displayText = "Fix login",
                        agentType = "grok",
                    ),
                ),
            ),
        )

        assertTrue(encoded.contains("\"folder_id\":3"))
        assertTrue(encoded.contains("\"prompt_blocks\":[{\"type\":\"text\",\"text\":\"Fix login\"}]"))
        assertTrue(encoded.contains("\"display_text\":\"Fix login\""))
        assertTrue(encoded.contains("\"agent_type\":\"grok\""))
    }

    @Test
    fun advancedWorkTaskBodiesKeepServerWireNames() {
        val settings = WorkTaskFolderSettings(
            defaultAgentType = "grok",
            modeId = "plan",
            autoProcess = true,
            maxConcurrent = 2,
            deleteWorktreeDefault = true,
        )
        val encoded = CodegJson.request.encodeToString(
            WorkTaskSettingsSetBody(folderId = 3, settings = settings),
        )

        assertTrue(encoded.contains("\"folderId\":3"))
        assertTrue(encoded.contains("\"default_agent_type\":\"grok\""))
        assertTrue(encoded.contains("\"max_concurrent\":2"))
        assertTrue(encoded.contains("\"delete_worktree_default\":true"))
    }

    @Test
    fun decodesTimelineAndChangedFiles() {
        val event = CodegJson.response.decodeFromString<WorkTaskEvent>(
            """{"id":1,"task_id":12,"kind":"review","actor":"agent","payload":{},"created_at":"2026-08-19T00:00:00Z"}""",
        )
        val file = CodegJson.response.decodeFromString<WorkTaskChangedFile>(
            """{"file":"src/App.kt","additions":4,"deletions":1}""",
        )

        assertEquals(12, event.taskId)
        assertEquals("review", event.kind)
        assertEquals("src/App.kt", file.file)
        assertEquals(4, file.additions)
        assertEquals(1, file.deletions)
    }

    private fun taskJson(): String =
        """
        {
          "id": 12,
          "folder_id": 3,
          "title": "Fix login",
          "config": {"display_text": "Fix login"},
          "status": "todo",
          "failure_reason": null,
          "last_error": null,
          "run_seq": 0,
          "sort_order": 0,
          "worktree_folder_id": null,
          "worktree_missing": false,
          "agent_type": "grok",
          "conversation_id": 42,
          "connection_id": null,
          "base_branch": "main",
          "base_sha": null,
          "work_branch": null,
          "cleanup_state": null,
          "verdict": null,
          "result_summary": null,
          "files_changed": null,
          "additions": null,
          "deletions": null,
          "merge_commit": null,
          "preflight": null,
          "merge_queued": null,
          "archived_at": null,
          "scheduled_at": null,
          "latest_progress": null,
          "created_at": "2026-08-19T00:00:00Z",
          "updated_at": "2026-08-19T00:00:00Z",
          "started_at": null,
          "settled_at": null,
          "finished_at": null
        }
        """.trimIndent()
}
