package app.codeg.android.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests for the line-based Codex TOML editor + auth.json codec. */
class AgentConfigTomlTest {

    private val sampleToml = """
        model = "gpt-5-codex"
        model_provider = "codeg"
        model_reasoning_effort = "high"

        [model_providers.codeg]
        name = "codeg"
        base_url = "https://api.example/v1"
        wire_api = "responses"
        supports_websockets = true

        [features]
        skills = true
    """.trimIndent()

    @Test
    fun `extractCodexToml reads scalars, provider, and features`() {
        val v = AgentToml.extractCodexToml(sampleToml)
        assertEquals("gpt-5-codex", v.model)
        assertEquals("codeg", v.modelProvider)
        assertEquals(CodexReasoningEffort.HIGH, v.modelReasoningEffort)
        assertEquals("https://api.example/v1", v.providerBaseUrls["codeg"])
        assertEquals(true, v.providerSupportsWebsockets["codeg"])
        assertTrue(v.featureSkills)
        assertTrue(v.providerNames.contains("codeg"))
    }

    @Test
    fun `extractCodex resolves active provider base url and auth key`() {
        val v = AgentToml.extractCodex("""{"OPENAI_API_KEY":"sk-xyz"}""", sampleToml)
        assertEquals("https://api.example/v1", v.apiBaseUrl)
        assertEquals("sk-xyz", v.apiKey)
        assertEquals("gpt-5-codex", v.model)
        assertEquals("codeg", v.modelProvider)
        assertTrue(v.supportsWebsockets)
    }

    @Test
    fun `inferCodexAuthMode distinguishes chatgpt from api key`() {
        assertEquals(CodexAuthMode.API_KEY, AgentToml.inferCodexAuthMode("""{"OPENAI_API_KEY":"sk"}"""))
        assertEquals(CodexAuthMode.CHATGPT_SUBSCRIPTION, AgentToml.inferCodexAuthMode("""{"auth_mode":"chatgpt"}"""))
        assertEquals(CodexAuthMode.CHATGPT_SUBSCRIPTION, AgentToml.inferCodexAuthMode("""{}"""))
    }

    @Test
    fun `patchCodexAuth sets and clears the key`() {
        val set = AgentToml.patchCodexAuth("", "sk-new")
        assertTrue(set.contains("\"OPENAI_API_KEY\": \"sk-new\""))
        val cleared = AgentToml.patchCodexAuth(set, "")
        assertFalse(cleared.contains("OPENAI_API_KEY"))
    }

    @Test
    fun `setRootString inserts, replaces, and removes`() {
        var t = AgentToml.setRootString("", "model", "gpt-5")
        assertEquals("model = \"gpt-5\"", t)
        t = AgentToml.setRootString(t, "model", "gpt-6")
        assertEquals("model = \"gpt-6\"", t)
        t = AgentToml.setRootString(t, "model", "")
        assertEquals("", t)
    }

    @Test
    fun `setRootBool inserts at top and replaces in place`() {
        val t = AgentToml.setRootBool("model = \"x\"", "disable_response_storage", true)
        assertTrue(t.startsWith("disable_response_storage = true"))
        val again = AgentToml.setRootBool(t, "disable_response_storage", true)
        assertEquals(1, Regex("disable_response_storage").findAll(again).count())
    }

    @Test
    fun `upsertSectionBool adds then prunes empty section`() {
        val added = AgentToml.upsertSectionBool("model = \"x\"", "features", "skills", true)
        assertTrue(added.contains("[features]"))
        assertTrue(added.contains("skills = true"))
        val pruned = AgentToml.upsertSectionBool(added, "features", "skills", null)
        assertFalse(pruned.contains("[features]"))
        assertTrue(pruned.contains("model = \"x\""))
    }

    @Test
    fun `patchCodex sets model, effort, provider, and forces disable_response_storage`() {
        val draft = AgentDraft(
            model = "gpt-5-codex",
            apiBaseUrl = "https://api.example/v1",
            codexReasoningEffort = CodexReasoningEffort.MEDIUM,
            codexSupportsWebsockets = true,
        )
        val out = AgentToml.patchCodex("", draft)
        val v = AgentToml.extractCodexToml(out)
        assertEquals("gpt-5-codex", v.model)
        assertEquals(CodexReasoningEffort.MEDIUM, v.modelReasoningEffort)
        assertEquals("codeg", v.modelProvider)
        assertEquals("https://api.example/v1", v.providerBaseUrls["codeg"])
        assertEquals(true, v.providerSupportsWebsockets["codeg"])
        assertTrue(out.contains("disable_response_storage = true"))
    }

    @Test
    fun `patchGrok preserves web-only custom-model and session blocks when a control changes`() {
        // Regression: changing only the two mobile controls must NOT drop config the
        // Android panel never exposes (custom model, endpoint creds, default pointer,
        // compaction threshold) — the whole reason we patch the toml instead of
        // sending grokStructured (which the server would treat as "delete these").
        val base = """
            [ui]
            permission_mode = "ask"
            max_thoughts_width = 120

            [models]
            default = "corp"
            default_reasoning_effort = "low"

            [model.corp]
            model = "corp"
            base_url = "https://grok.corp/v1"
            api_key = "xai-secret"
            context_window = 262144

            [session]
            auto_compact_threshold_percent = 70
        """.trimIndent()
        val out = AgentToml.patchGrok(base, "always-approve", "xhigh")
        // The two exposed controls are updated in place.
        assertTrue(out.contains("permission_mode = \"always-approve\""))
        assertTrue(out.contains("default_reasoning_effort = \"xhigh\""))
        // Everything else survives verbatim.
        assertTrue(out.contains("[model.corp]"))
        assertTrue(out.contains("base_url = \"https://grok.corp/v1\""))
        assertTrue(out.contains("api_key = \"xai-secret\""))
        assertTrue(out.contains("context_window = 262144"))
        assertTrue(out.contains("default = \"corp\""))
        assertTrue(out.contains("auto_compact_threshold_percent = 70"))
        assertTrue(out.contains("max_thoughts_width = 120"))
    }

    @Test
    fun `patchGrok removes a key on use-default and creates sections when absent`() {
        // Empty control = "use default" = remove the key (server parity).
        val base = """
            [ui]
            permission_mode = "ask"

            [models]
            default = "corp"
            default_reasoning_effort = "high"
        """.trimIndent()
        val cleared = AgentToml.patchGrok(base, "", "")
        assertFalse(cleared.contains("permission_mode"))
        assertFalse(cleared.contains("default_reasoning_effort"))
        assertFalse(cleared.contains("[ui]")) // pruned: its only key was removed
        assertTrue(cleared.contains("default = \"corp\"")) // unrelated [models] key kept
        // From empty, both sections are created.
        val fresh = AgentToml.patchGrok("", "ask", "low")
        assertTrue(fresh.contains("[ui]"))
        assertTrue(fresh.contains("permission_mode = \"ask\""))
        assertTrue(fresh.contains("[models]"))
        assertTrue(fresh.contains("default_reasoning_effort = \"low\""))
    }

    @Test
    fun `codex draft round-trips through fromAgent and reapply`() {
        val agent = AcpAgentInfo(
            agentType = AgentType.CODEX,
            codexConfigToml = sampleToml,
            codexAuthJson = """{"OPENAI_API_KEY":"sk-xyz"}""",
        )
        val d = AgentDraft.fromAgent(agent)
        assertEquals(CodexAuthMode.API_KEY, d.codexAuthMode)
        assertEquals("sk-xyz", d.apiKey)
        assertEquals("gpt-5-codex", d.model)
        assertEquals(CodexReasoningEffort.HIGH, d.codexReasoningEffort)
        assertTrue(d.codexSupportsWebsockets)

        // Reapply must preserve the structured values and keep the key in auth.json.
        val r = d.reapplied(AgentType.CODEX)
        val v = AgentToml.extractCodexToml(r.codexConfigTomlText)
        assertEquals("gpt-5-codex", v.model)
        assertEquals(CodexReasoningEffort.HIGH, v.modelReasoningEffort)
        assertTrue(r.codexConfigTomlText.contains("disable_response_storage = true"))
        assertTrue(r.codexAuthJsonText.contains("sk-xyz"))
    }
}
