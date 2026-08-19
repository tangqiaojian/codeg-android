package app.codeg.android.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Locks the web-parity windowed transcript: open the tail, prepend older pages
 * only when the seam fingerprint matches, and treat a missing window as legacy.
 */
class TranscriptWindowTest {

    private fun turn(id: String) = MessageTurn(
        id = id,
        role = TurnRole.USER,
        blocks = listOf(ContentBlock.Text(id)),
        timestamp = Instant.EPOCH,
    )

    @Test
    fun `legacy detail without offset is not windowed`() {
        val detail = ConversationDetail(
            summary = summary(),
            turns = listOf(turn("a")),
        )
        assertNull(TranscriptWindow.from(detail))
    }

    @Test
    fun `windowed detail exposes hasOlder from turns_offset`() {
        val window = TranscriptWindow.from(
            ConversationDetail(
                summary = summary(),
                turns = listOf(turn("t40")),
                turnsOffset = 40,
                turnsTotal = 50,
                prefixHash = "hash-40",
            ),
        )!!
        assertTrue(window.hasOlder)
        assertEquals(40, window.turnsOffset)
        assertEquals(listOf("t40"), window.turns.map { it.id })
    }

    @Test
    fun `prepend joins an older page when the seam hash matches`() {
        val current = TranscriptWindow(
            turns = listOf(turn("t40")),
            turnsOffset = 40,
            turnsTotal = 50,
            prefixHash = "hash-40",
        )
        val page = ConversationTurnsPage(
            turns = listOf(turn("t20"), turn("t21")),
            turnsOffset = 20,
            turnsTotal = 50,
            prefixHash = "hash-20",
            prefixHashBeforeIndex = "hash-40",
        )
        val merged = current.prepend(page)!!
        assertEquals(listOf("t20", "t21", "t40"), merged.turns.map { it.id })
        assertEquals(20, merged.turnsOffset)
        assertEquals("hash-20", merged.prefixHash)
        assertTrue(merged.hasOlder)
    }

    @Test
    fun `prepend rejects a page whose seam hash does not match`() {
        val current = TranscriptWindow(
            turns = listOf(turn("t40")),
            turnsOffset = 40,
            turnsTotal = 50,
            prefixHash = "hash-40",
        )
        val page = ConversationTurnsPage(
            turns = listOf(turn("t20")),
            turnsOffset = 20,
            turnsTotal = 50,
            prefixHash = "hash-20",
            prefixHashBeforeIndex = "rewritten",
        )
        assertNull(current.prepend(page))
    }

    @Test
    fun `offset zero means the window covers the start`() {
        val window = TranscriptWindow(
            turns = listOf(turn("t0")),
            turnsOffset = 0,
            turnsTotal = 10,
            prefixHash = "seed",
        )
        assertFalse(window.hasOlder)
    }

    private fun summary() = ConversationSummary(
        id = 1,
        folderId = 1,
        agentType = AgentType.GROK,
        status = ConversationStatus.COMPLETED,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )
}
