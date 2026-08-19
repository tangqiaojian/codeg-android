package app.codeg.android.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentMentionTest {

    @Test
    fun `inserts a visible token and serializes it to a structured agent link`() {
        val draft = AgentMentionDraft("请让 @gr 检查", emptyList())

        val inserted = draft.insertMention(3, 6, AgentMentionTarget(AgentType.GROK, "Grok"))

        assertEquals("请让 @Grok 检查", inserted.text)
        assertEquals(3, inserted.mentions.single().start)
        assertEquals(8, inserted.mentions.single().end)
        assertEquals("请让 [@Grok](codeg://agent/grok) 检查", inserted.toWire())
    }

    @Test
    fun `supports multiple mentions in one prompt`() {
        val first = AgentMentionDraft("@", emptyList())
            .insertMention(0, 1, AgentMentionTarget(AgentType.GROK, "Grok"))
        val second = first.insertMention(
            first.text.length,
            first.text.length,
            AgentMentionTarget(AgentType.CODEX, "Codex"),
        )

        assertEquals("@Grok@Codex", second.text)
        assertEquals(
            "[@Grok](codeg://agent/grok)[@Codex](codeg://agent/codex)",
            second.toWire(),
        )
    }

    @Test
    fun `ordinary text edits shift mentions while edits inside a token remove metadata`() {
        val draft = AgentMentionDraft("@Grok says hi", listOf(AgentMention(0, 5, AgentMentionTarget(AgentType.GROK, "Grok"))))

        val shifted = draft.applyTextChange("Please @Grok says hi")
        assertEquals(7, shifted.mentions.single().start)
        assertEquals("Please [@Grok](codeg://agent/grok) says hi", shifted.toWire())

        val edited = shifted.applyTextChange("Please @Gros says hi")
        assertTrue(edited.mentions.isEmpty())
        assertEquals("Please @Gros says hi", edited.toWire())
    }

    @Test
    fun `backspace at a token boundary deletes the whole token`() {
        val draft = AgentMentionDraft(
            "ask @Grok now",
            listOf(AgentMention(4, 9, AgentMentionTarget(AgentType.GROK, "Grok"))),
        )

        val deleted = draft.deleteMentionBeforeCursor(9)
        assertNotNull(deleted)
        val restored = deleted!!
        assertEquals("ask  now", restored.text)
        assertTrue(restored.mentions.isEmpty())
    }

    @Test
    fun `wire references round trip into visible tokens`() {
        val parsed = AgentMentionDraft.fromWire(
            "请让 [@Grok](codeg://agent/grok) 和 [@Codex](codeg://agent/codex) 检查",
        )

        assertEquals("请让 @Grok 和 @Codex 检查", parsed.text)
        assertEquals(listOf(AgentType.GROK, AgentType.CODEX), parsed.mentions.map { it.target.agentType })
        assertEquals(
            "请让 [@Grok](codeg://agent/grok) 和 [@Codex](codeg://agent/codex) 检查",
            parsed.toWire(),
        )
    }

    @Test
    fun `unknown or malformed references remain visible`() {
        val parsed = AgentMentionDraft.fromWire("bad [@Future](codeg://agent/future) [@Grok](not-codeg://agent/grok)")

        assertEquals("bad [@Future](codeg://agent/future) [@Grok](not-codeg://agent/grok)", parsed.text)
        assertNull(parsed.mentions.firstOrNull())
    }

    @Test
    fun `active query only starts at a standalone at sign`() {
        assertEquals(AgentMentionQuery(4, 7, "gr"), findActiveAgentMentionQuery("ask @gr", 7))
        assertEquals(AgentMentionQuery(4, 5, ""), findActiveAgentMentionQuery("ask @", 5))
        assertNull(findActiveAgentMentionQuery("mail@test", 9))
        assertNull(findActiveAgentMentionQuery("ask @gr now", 11))
    }
}
