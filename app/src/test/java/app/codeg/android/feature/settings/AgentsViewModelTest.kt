package app.codeg.android.feature.settings

import app.codeg.android.core.model.AcpAgentInfo
import app.codeg.android.core.model.AgentDraft
import app.codeg.android.core.model.AgentType
import app.codeg.android.core.model.GrokSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regression coverage for the enable-toggle write-payload selection (provider-clobber race). */
class AgentsViewModelTest {

    private fun agent(type: AgentType, env: Map<String, String> = emptyMap(), providerId: Int? = null) =
        AcpAgentInfo(agentType = type, env = env, modelProviderId = providerId)

    @Test
    fun `toggle uses the live row even when its provider id is null`() {
        // Captured snapshot is stale-linked to provider 5; the live row was just unlinked.
        val captured = agent(AgentType.CLAUDE_CODE, env = mapOf("OLD" to "1"), providerId = 5)
        val liveRow = agent(AgentType.CLAUDE_CODE, env = mapOf("NEW" to "2"), providerId = null)

        val src = toggleWriteSource(listOf(liveRow), captured)

        // Must NOT fall back to the stale provider 5 / old env.
        assertNull(src.modelProviderId)
        assertEquals(mapOf("NEW" to "2"), src.env)
    }

    @Test
    fun `toggle keeps a live provider link`() {
        val captured = agent(AgentType.GEMINI, providerId = null)
        val liveRow = agent(AgentType.GEMINI, providerId = 9)
        assertEquals(9, toggleWriteSource(listOf(liveRow), captured).modelProviderId)
    }

    @Test
    fun `toggle falls back to the captured snapshot when no live row exists`() {
        val captured = agent(AgentType.CODEX, env = mapOf("K" to "v"), providerId = 3)
        val src = toggleWriteSource(emptyList(), captured)
        assertEquals(3, src.modelProviderId)
        assertEquals(mapOf("K" to "v"), src.env)
    }

    @Test
    fun `grok dropdown save patches the fresh config, preserving edits since the panel opened`() {
        // Panel opened showing permission_mode=ask; the user flips it to always-approve.
        val snapshot = AcpAgentInfo(
            agentType = AgentType.GROK,
            grokConfigToml = "[ui]\npermission_mode = \"ask\"\n",
            grokSettings = GrokSettings(permissionMode = "ask"),
        )
        val draft = AgentDraft(
            grokConfigTomlText = "[ui]\npermission_mode = \"ask\"\n",
            grokPermissionMode = "always-approve",
        )
        // Meanwhile another window/MCP added a custom model to the LIVE file.
        val fresh = "[ui]\npermission_mode = \"ask\"\n\n[model.corp]\nbase_url = \"https://x\"\n"
        val out = grokConfigTomlForSave(draft, snapshot, fresh)!!
        assertTrue(out.contains("permission_mode = \"always-approve\"")) // user's edit applied
        assertTrue(out.contains("[model.corp]"))                         // fresh external edit preserved
    }

    @Test
    fun `grok api-key-only save writes nothing to config`() {
        val snapshot = AcpAgentInfo(
            agentType = AgentType.GROK,
            grokConfigToml = "[ui]\npermission_mode = \"ask\"\n",
            grokSettings = GrokSettings(permissionMode = "ask"),
        )
        // Dropdowns/raw match the snapshot (only the API key changed, on the env path).
        val draft = AgentDraft(grokConfigTomlText = "[ui]\npermission_mode = \"ask\"\n", grokPermissionMode = "ask")
        assertNull(grokConfigTomlForSave(draft, snapshot, "ignored fresh"))
    }

    @Test
    fun `grok raw edit is sent verbatim and ignores the fresh base`() {
        val snapshot = AcpAgentInfo(agentType = AgentType.GROK, grokConfigToml = "[ui]\npermission_mode = \"ask\"\n")
        val draft = AgentDraft(grokConfigTomlText = "[ui]\npermission_mode = \"always-approve\"\n# mine\n")
        assertEquals(draft.grokConfigTomlText, grokConfigTomlForSave(draft, snapshot, "[different]\n"))
    }
}
