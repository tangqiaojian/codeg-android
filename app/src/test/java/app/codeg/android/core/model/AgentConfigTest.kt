package app.codeg.android.core.model

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure-function tests for the agent config codec (extract/reapply round-trips). */
class AgentConfigTest {

    private fun agent(
        type: AgentType,
        env: Map<String, String> = emptyMap(),
        configJson: String? = null,
        modelProviderId: Int? = null,
        codexConfigToml: String? = null,
        codexAuthJson: String? = null,
        opencodeAuthJson: String? = null,
        grokConfigToml: String? = null,
        grokSettings: GrokSettings? = null,
    ) = AcpAgentInfo(
        agentType = type,
        env = env,
        configJson = configJson,
        modelProviderId = modelProviderId,
        codexConfigToml = codexConfigToml,
        codexAuthJson = codexAuthJson,
        opencodeAuthJson = opencodeAuthJson,
        grokConfigToml = grokConfigToml,
        grokSettings = grokSettings,
    )

    // region JSON / env primitives

    @Test
    fun `serialize sorts keys with two-space indent`() {
        val out = JsonConfig.serialize(mapOf("b" to JsonPrimitive("2"), "a" to JsonPrimitive("1")))
        assertEquals("{\n  \"a\": \"1\",\n  \"b\": \"2\"\n}", out)
    }

    @Test
    fun `serialize of empty object is blank`() {
        assertEquals("", JsonConfig.serialize(emptyMap()))
    }

    @Test
    fun `parse rejects non-object`() {
        assertEquals("Native JSON config must be an object", JsonConfig.parse("[1,2]").error)
        assertTrue(JsonConfig.parse("{bad").error != null)
        assertNull(JsonConfig.parse("   ").error)
    }

    @Test
    fun `markRemovedKeysNull nulls dropped keys recursively`() {
        val original = JsonConfig.parse("""{"a":"1","b":"2","env":{"X":"1","Y":"2"}}""").config
        val current = JsonConfig.parse("""{"a":"1","env":{"X":"1"}}""").config
        val merged = JsonConfig.markRemovedKeysNull(original, current)
        assertEquals(JsonNull, merged["b"])
        assertEquals(JsonNull, (merged["env"] as JsonObject)["Y"])
        assertEquals(JsonPrimitive("1"), (merged["env"] as JsonObject)["X"])
    }

    @Test
    fun `envFromConfig drops blanks and trims`() {
        val cfg = JsonConfig.parse("""{"env":{"K":" v ","E":"","N":5}}""").config
        assertEquals(mapOf("K" to "v"), JsonConfig.envFromConfig(cfg))
    }

    @Test
    fun `EnvText round-trips and patch deletes on blank`() {
        val text = EnvText.toText(mapOf("B" to "2", "A" to "1"))
        assertEquals("A=1\nB=2", text)
        assertEquals(mapOf("A" to "1", "B" to "2"), EnvText.parse(text))
        assertEquals("A=1", EnvText.patch(text, mapOf("B" to "")))
        assertEquals("A=1\nB=9", EnvText.patch(text, mapOf("B" to "9")))
    }

    @Test
    fun `EnvText parse ignores comments and blank lines`() {
        assertEquals(mapOf("A" to "1"), EnvText.parse("# comment\n\nA=1\n=bad"))
    }

    // endregion

    // region Claude

    @Test
    fun `claude custom mode extracted from env`() {
        val d = AgentDraft.fromAgent(
            agent(
                AgentType.CLAUDE_CODE,
                env = mapOf(
                    "ANTHROPIC_BASE_URL" to "https://api.test",
                    "ANTHROPIC_AUTH_TOKEN" to "tok",
                    "ANTHROPIC_MODEL" to "claude-x",
                ),
            ),
        )
        assertEquals(ClaudeAuthMode.CUSTOM, d.claudeAuthMode)
        assertEquals("https://api.test", d.apiBaseUrl)
        assertEquals("tok", d.apiKey)
        assertEquals("claude-x", d.claudeMainModel)
    }

    @Test
    fun `claude empty is official subscription, provider id is model provider`() {
        assertEquals(ClaudeAuthMode.OFFICIAL_SUBSCRIPTION, AgentDraft.fromAgent(agent(AgentType.CLAUDE_CODE)).claudeAuthMode)
        assertEquals(
            ClaudeAuthMode.MODEL_PROVIDER,
            AgentDraft.fromAgent(agent(AgentType.CLAUDE_CODE, modelProviderId = 7)).claudeAuthMode,
        )
    }

    @Test
    fun `claude reapply writes env and config-env in lockstep plus effort`() {
        val d = AgentDraft.fromAgent(agent(AgentType.CLAUDE_CODE))
            .copy(apiBaseUrl = "https://api", apiKey = "k", claudeMainModel = "m", claudeEffortLevel = ClaudeEffortLevel.HIGH)
            .reapplied(AgentType.CLAUDE_CODE)
        // flat env
        assertTrue(d.envText.contains("ANTHROPIC_BASE_URL=https://api"))
        assertTrue(d.envText.contains("ANTHROPIC_AUTH_TOKEN=k"))
        // config.env + effort at root
        val cfg = JsonConfig.parse(d.configText).config
        val env = cfg["env"] as JsonObject
        assertEquals(JsonPrimitive("https://api"), env["ANTHROPIC_BASE_URL"])
        assertEquals(JsonPrimitive("m"), env["ANTHROPIC_MODEL"])
        assertEquals(JsonPrimitive("high"), cfg["effortLevel"])
    }

    @Test
    fun `claude reapply is idempotent`() {
        val base = AgentDraft.fromAgent(
            agent(AgentType.CLAUDE_CODE, env = mapOf("ANTHROPIC_BASE_URL" to "https://api", "ANTHROPIC_AUTH_TOKEN" to "k")),
        )
        val once = base.reapplied(AgentType.CLAUDE_CODE)
        val twice = once.reapplied(AgentType.CLAUDE_CODE)
        assertEquals(once, twice)
    }

    @Test
    fun `clearClaudeCredentialAliases strips every alias from env and config-env`() {
        val (cfg, env) = AgentConfig.clearClaudeCredentialAliases(
            configText = """{"env":{"OPENAI_BASE_URL":"x","keep":"y"}}""",
            envText = "OPENAI_BASE_URL=x\nANTHROPIC_AUTH_TOKEN=t\nKEEP=1",
        )
        assertEquals("KEEP=1", env)
        val parsed = JsonConfig.parse(cfg).config
        assertEquals(JsonPrimitive("y"), (parsed["env"] as JsonObject)["keep"])
        assertNull((parsed["env"] as JsonObject)["OPENAI_BASE_URL"])
    }

    @Test
    fun `claude linked provider scrubs typed secrets on reapply`() {
        val d = AgentDraft.fromAgent(agent(AgentType.CLAUDE_CODE, modelProviderId = 3))
            .copy(apiBaseUrl = "https://leak", apiKey = "secret", claudeMainModel = "m")
            .reapplied(AgentType.CLAUDE_CODE)
        assertFalse(d.envText.contains("leak"))
        assertFalse(d.envText.contains("secret"))
    }

    // endregion

    // region Gemini / OpenClaw / Cline / OpenCode

    @Test
    fun `gemini infers api-key mode and reapplies to env`() {
        val d = AgentDraft.fromAgent(agent(AgentType.GEMINI, env = mapOf("GEMINI_API_KEY" to "gk")))
        assertEquals(GeminiAuthMode.GEMINI_API_KEY, d.geminiAuthMode)
        assertEquals("gk", d.geminiApiKey)
        val r = d.copy(model = "gemini-2").reapplied(AgentType.GEMINI)
        assertTrue(r.envText.contains("GEMINI_API_KEY=gk"))
        assertTrue(r.envText.contains("GEMINI_MODEL=gemini-2"))
    }

    @Test
    fun `gemini switch keeps target-mode fields and clears the rest`() {
        // Start populated as if every field had a value, then switch modes.
        val full = AgentDraft(
            apiBaseUrl = "u", geminiApiKey = "gk", googleApiKey = "ga",
            googleCloudProject = "p", googleCloudLocation = "l", googleApplicationCredentials = "c",
            modelProviderId = 5,
        )
        // gemini_api_key keeps only the gemini key.
        AgentConfig.applyGeminiAuthMode(full, GeminiAuthMode.GEMINI_API_KEY).let {
            assertEquals("gk", it.geminiApiKey)
            assertEquals("", it.apiBaseUrl)
            assertEquals("", it.googleApiKey)
            assertNull(it.modelProviderId)
        }
        // custom keeps url + gemini key.
        AgentConfig.applyGeminiAuthMode(full, GeminiAuthMode.CUSTOM).let {
            assertEquals("u", it.apiBaseUrl)
            assertEquals("gk", it.geminiApiKey)
            assertEquals("", it.googleApiKey)
        }
        // vertex_service_account keeps project/location/credentials.
        AgentConfig.applyGeminiAuthMode(full, GeminiAuthMode.VERTEX_SERVICE_ACCOUNT).let {
            assertEquals("p", it.googleCloudProject)
            assertEquals("l", it.googleCloudLocation)
            assertEquals("c", it.googleApplicationCredentials)
            assertEquals("", it.geminiApiKey)
        }
        // model_provider keeps the provider link (and url/key/google key per iOS).
        AgentConfig.applyGeminiAuthMode(full, GeminiAuthMode.MODEL_PROVIDER).let {
            assertEquals(5, it.modelProviderId)
            assertEquals("", it.googleCloudProject)
        }
        // login_google clears everything.
        AgentConfig.applyGeminiAuthMode(full, GeminiAuthMode.LOGIN_GOOGLE).let {
            assertEquals("", it.apiBaseUrl)
            assertEquals("", it.geminiApiKey)
            assertEquals("", it.googleCloudProject)
            assertNull(it.modelProviderId)
        }
    }

    @Test
    fun `openclaw gateway fields round-trip through env`() {
        val d = AgentDraft.fromAgent(
            agent(AgentType.OPEN_CLAW, env = mapOf("OPENCLAW_GATEWAY_URL" to "wss://g", "OPENCLAW_SESSION_KEY" to "agent:main:main")),
        )
        assertEquals("wss://g", d.openClawGatewayUrl)
        assertEquals("agent:main:main", d.openClawSessionKey)
        val r = d.copy(openClawGatewayToken = "tok").reapplied(AgentType.OPEN_CLAW)
        assertTrue(r.envText.contains("OPENCLAW_GATEWAY_TOKEN=tok"))
    }

    @Test
    fun `openclaw edit is not shadowed by stale config-env`() {
        // config.env wins on extract, so an edit must also clear the stale config.env value
        // or it would resurface (override the new flat env) on the next reload.
        val d0 = AgentDraft.fromAgent(agent(AgentType.OPEN_CLAW, configJson = """{"env":{"OPENCLAW_GATEWAY_URL":"wss://old"}}"""))
        assertEquals("wss://old", d0.openClawGatewayUrl)
        val d1 = d0.copy(openClawGatewayUrl = "wss://new").reapplied(AgentType.OPEN_CLAW)
        assertTrue(d1.envText.contains("OPENCLAW_GATEWAY_URL=wss://new"))
        val cfgEnv = JsonConfig.parse(d1.configText).config["env"] as? JsonObject
        assertNull(cfgEnv?.get("OPENCLAW_GATEWAY_URL"))
        assertEquals("wss://new", AgentConfig.extractOpenClaw(EnvText.parse(d1.envText), d1.configText).gatewayUrl)
    }

    @Test
    fun `codebuddy api key and region round-trip through env`() {
        val d = AgentDraft.fromAgent(
            agent(AgentType.CODE_BUDDY, env = mapOf("CODEBUDDY_API_KEY" to "sk-cb", "CODEBUDDY_INTERNET_ENVIRONMENT" to "internal")),
        )
        assertEquals("sk-cb", d.apiKey)
        assertEquals(CodeBuddyEnvironment.INTERNAL, d.codeBuddyEnvironment)
        val r = d.reapplied(AgentType.CODE_BUDDY)
        assertTrue(r.envText.contains("CODEBUDDY_API_KEY=sk-cb"))
        assertTrue(r.envText.contains("CODEBUDDY_INTERNET_ENVIRONMENT=internal"))
        assertFalse(r.envText.contains("CODEBUDDY_BASE_URL"))
    }

    @Test
    fun `codebuddy self-hosted derives from base url, strips trailing slash, clears region`() {
        // A non-empty CODEBUDDY_BASE_URL implies self-hosted regardless of the region key.
        val d = AgentDraft.fromAgent(
            agent(AgentType.CODE_BUDDY, env = mapOf("CODEBUDDY_BASE_URL" to "https://cb.example.com", "CODEBUDDY_INTERNET_ENVIRONMENT" to "internal")),
        )
        assertEquals(CodeBuddyEnvironment.SELF_HOSTED, d.codeBuddyEnvironment)
        assertEquals("https://cb.example.com", d.codeBuddyBaseUrl)
        // reapply strips a trailing slash and clears the region key.
        val r = d.copy(codeBuddyBaseUrl = "https://cb.example.com/").reapplied(AgentType.CODE_BUDDY)
        assertTrue(r.envText.contains("CODEBUDDY_BASE_URL=https://cb.example.com"))
        assertFalse(r.envText.contains("CODEBUDDY_BASE_URL=https://cb.example.com/"))
        assertFalse(r.envText.contains("CODEBUDDY_INTERNET_ENVIRONMENT"))
    }

    @Test
    fun `codebuddy overseas clears both region and base url`() {
        val d = AgentDraft.fromAgent(agent(AgentType.CODE_BUDDY, env = mapOf("CODEBUDDY_API_KEY" to "k")))
        assertEquals(CodeBuddyEnvironment.OVERSEAS, d.codeBuddyEnvironment)
        val r = d.reapplied(AgentType.CODE_BUDDY)
        assertFalse(r.envText.contains("CODEBUDDY_INTERNET_ENVIRONMENT"))
        assertFalse(r.envText.contains("CODEBUDDY_BASE_URL"))
    }

    @Test
    fun `codebuddy base url validation gates a self-hosted save`() {
        assertTrue(AgentConfig.isValidCodeBuddyBaseUrl("https://cb.example.com"))
        assertTrue(AgentConfig.isValidCodeBuddyBaseUrl("http://10.0.0.1:8080"))
        assertFalse(AgentConfig.isValidCodeBuddyBaseUrl("notaurl"))
        assertFalse(AgentConfig.isValidCodeBuddyBaseUrl("ftp://x"))
        assertFalse(AgentConfig.isValidCodeBuddyBaseUrl(""))
        val selfHosted = AgentDraft(codeBuddyEnvironment = CodeBuddyEnvironment.SELF_HOSTED, codeBuddyBaseUrl = "notaurl")
        assertTrue(AgentConfig.missingCodeBuddyBaseUrl(AgentType.CODE_BUDDY, selfHosted))
        assertFalse(AgentConfig.missingCodeBuddyBaseUrl(AgentType.CODE_BUDDY, selfHosted.copy(codeBuddyBaseUrl = "https://cb.example.com")))
        // A non-self-hosted CodeBuddy never blocks.
        assertFalse(AgentConfig.missingCodeBuddyBaseUrl(AgentType.CODE_BUDDY, AgentDraft()))
    }

    @Test
    fun `grok seeds api key from XAI_API_KEY and dropdowns from grok settings`() {
        val d = AgentDraft.fromAgent(
            agent(
                AgentType.GROK,
                env = mapOf("XAI_API_KEY" to "xai-123"),
                grokConfigToml = "[ui]\npermission_mode = \"ask\"\n",
                grokSettings = GrokSettings(permissionMode = "ask", defaultReasoningEffort = "high"),
            ),
        )
        assertEquals("xai-123", d.apiKey)
        assertEquals("ask", d.grokPermissionMode)
        assertEquals("high", d.grokReasoningEffort)
        assertEquals("[ui]\npermission_mode = \"ask\"\n", d.grokConfigTomlText)
    }

    @Test
    fun `grok absent settings default to empty (use default)`() {
        val d = AgentDraft.fromAgent(agent(AgentType.GROK))
        assertEquals("", d.apiKey)
        assertEquals("", d.grokPermissionMode)
        assertEquals("", d.grokReasoningEffort)
        assertEquals("", d.grokConfigTomlText)
    }

    @Test
    fun `grok reapply sets and clears only XAI_API_KEY in env`() {
        val set = AgentDraft(apiKey = "xai-abc").reapplied(AgentType.GROK)
        assertTrue(set.envText.contains("XAI_API_KEY=xai-abc"))
        // Empty key deletes it (EnvText.patch); the structured controls never touch env/config.
        val cleared = AgentDraft(apiKey = "", grokPermissionMode = "ask", grokReasoningEffort = "high")
            .reapplied(AgentType.GROK)
        assertFalse(cleared.envText.contains("XAI_API_KEY"))
        assertFalse(cleared.envText.contains("permission_mode"))
        assertFalse(cleared.configText.contains("permission_mode"))
    }

    @Test
    fun `cline builds a fresh config json`() {
        val d = AgentDraft.fromAgent(
            agent(AgentType.CLINE, configJson = """{"apiProvider":"openrouter","apiKey":"k","model":"m","apiBaseUrl":"u"}"""),
        )
        assertEquals("openrouter", d.clineProvider)
        assertEquals("k", d.clineApiKey)
        val r = d.reapplied(AgentType.CLINE)
        assertEquals("{\n  \"apiBaseUrl\": \"u\",\n  \"apiKey\": \"k\",\n  \"apiProvider\": \"openrouter\",\n  \"model\": \"m\"\n}", r.configText)
    }

    @Test
    fun `opencode main and small model round-trip at config root`() {
        val d = AgentDraft.fromAgent(agent(AgentType.OPEN_CODE, configJson = """{"model":"a","small_model":"b"}"""))
        assertEquals("a", d.openCodeMainModel)
        assertEquals("b", d.openCodeSmallModel)
        val r = d.copy(openCodeSmallModel = "c").reapplied(AgentType.OPEN_CODE)
        val cfg = JsonConfig.parse(r.configText).config
        assertEquals(JsonPrimitive("c"), cfg["small_model"])
    }

    @Test
    fun `ensureOpenCodeProviderNpm fills a default package`() {
        val out = AgentConfig.ensureOpenCodeProviderNpm("""{"provider":{"p1":{"name":"P1"}}}""")
        val cfg = JsonConfig.parse(out).config
        val p1 = (cfg["provider"] as JsonObject)["p1"] as JsonObject
        assertEquals(JsonPrimitive(openCodeNpmOptions[0]), p1["npm"])
    }

    // endregion

    // region Hermes / validation

    @Test
    fun `hermes projection parsed from config json`() {
        val d = AgentDraft.fromAgent(
            agent(AgentType.HERMES, configJson = """{"provider":"anthropic","model":"m","baseUrl":"u","apiKey":"k"}"""),
        )
        assertEquals("anthropic", d.hermesProvider)
        assertEquals("m", d.model)
        assertEquals("u", d.apiBaseUrl)
        assertEquals("k", d.apiKey)
    }

    @Test
    fun `missingModelProvider blocks save only when linked but unselected`() {
        val claude = AgentDraft(claudeAuthMode = ClaudeAuthMode.MODEL_PROVIDER, modelProviderId = null)
        assertTrue(AgentConfig.missingModelProvider(AgentType.CLAUDE_CODE, claude))
        assertFalse(AgentConfig.missingModelProvider(AgentType.CLAUDE_CODE, claude.copy(modelProviderId = 1)))
        assertFalse(AgentConfig.missingModelProvider(AgentType.OPEN_CLAW, claude))
    }

    // endregion
}
