package app.codeg.android.feature.sessiondetail

import app.codeg.android.core.model.ContentBlock
import app.codeg.android.core.model.MessageTurn
import app.codeg.android.core.model.TurnRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class TranscriptSearchTest {

    private fun turn(id: String, role: TurnRole, text: String) = MessageTurn(
        id = id,
        role = role,
        blocks = listOf(ContentBlock.Text(text)),
        timestamp = Instant.EPOCH,
    )

    @Test
    fun `blank query yields no hits`() {
        val turns = listOf(turn("u1", TurnRole.USER, "hello"))
        assertTrue(TranscriptSearch.findHits(turns, "  ").isEmpty())
    }

    @Test
    fun `matches user and assistant text case-insensitively`() {
        val turns = listOf(
            turn("u1", TurnRole.USER, "Please review AuthService"),
            turn("a1", TurnRole.ASSISTANT, "I will inspect authService next."),
            turn("u2", TurnRole.USER, "unrelated"),
        )
        val hits = TranscriptSearch.findHits(turns, "authservice")
        assertEquals(listOf("u1", "a1"), hits.map { it.turnId })
        assertTrue(hits.all { it.snippet.contains("auth", ignoreCase = true) })
    }

    @Test
    fun `skips thinking-only turns and empty blocks`() {
        val turns = listOf(
            MessageTurn(
                id = "t1",
                role = TurnRole.ASSISTANT,
                blocks = listOf(ContentBlock.Thinking("secret plan")),
                timestamp = Instant.EPOCH,
            ),
            turn("u1", TurnRole.USER, "visible plan"),
        )
        assertEquals(listOf("u1"), TranscriptSearch.findHits(turns, "plan").map { it.turnId })
    }
}
