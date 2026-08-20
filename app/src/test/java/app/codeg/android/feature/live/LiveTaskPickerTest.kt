package app.codeg.android.feature.live

import app.codeg.android.core.model.AgentType
import app.codeg.android.core.model.ConversationStatus
import app.codeg.android.core.model.ConversationSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class LiveTaskPickerTest {

    private fun conv(
        id: Int,
        status: ConversationStatus,
        updated: Long,
        title: String = "c$id",
        agent: AgentType = AgentType.GROK,
    ) = ConversationSummary(
        id = id,
        folderId = 1,
        title = title,
        agentType = agent,
        status = status,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.ofEpochSecond(updated),
    )

    @Test
    fun `empty list yields an empty snapshot`() {
        val snap = LiveTaskPicker.pick(emptyList())
        assertNull(snap.conversationId)
        assertTrue(snap.isIdle)
    }

    @Test
    fun `prefers the newest running session over a newer completed one`() {
        val snap = LiveTaskPicker.pick(
            listOf(
                conv(1, ConversationStatus.COMPLETED, updated = 300, title = "done"),
                conv(2, ConversationStatus.IN_PROGRESS, updated = 200, title = "live"),
                conv(3, ConversationStatus.IN_PROGRESS, updated = 100, title = "older-live"),
            ),
        )
        assertEquals(2, snap.conversationId)
        assertEquals("live", snap.title)
        assertEquals(ConversationStatus.IN_PROGRESS, snap.status)
        assertEquals("Grok", snap.agentLabel)
    }

    @Test
    fun `falls back to pending review then the newest conversation`() {
        val review = LiveTaskPicker.pick(
            listOf(
                conv(1, ConversationStatus.COMPLETED, updated = 300),
                conv(2, ConversationStatus.PENDING_REVIEW, updated = 90),
            ),
        )
        assertEquals(2, review.conversationId)

        val idle = LiveTaskPicker.pick(
            listOf(
                conv(1, ConversationStatus.COMPLETED, updated = 10),
                conv(2, ConversationStatus.CANCELLED, updated = 40),
            ),
        )
        assertEquals(2, idle.conversationId)
    }
}
