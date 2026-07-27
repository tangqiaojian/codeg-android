package app.codeg.android.feature.sessiondetail

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The plan-approval answer is a network round trip, so the turn that produced the
 * approval can reach any terminal state before it returns. These lock down that the
 * revision notes only ever reach the turn they belong to.
 */
class PlanFollowUpTest {

    @Test
    fun `notes park for the originating turn while it is still winding down`() {
        assertEquals(
            PlanFollowUp.PARK,
            planFollowUpAction(sameTurn = true, inFlight = true, stopReason = null, errorMessage = null),
        )
    }

    @Test
    fun `notes go out immediately when that turn finished cleanly meanwhile`() {
        assertEquals(
            PlanFollowUp.SEND_NOW,
            planFollowUpAction(sameTurn = true, inFlight = false, stopReason = "end_turn", errorMessage = null),
        )
    }

    @Test
    fun `notes are dropped when the turn was cancelled or failed`() {
        // Cancelled: `stop()` stamps an error message on the builder.
        assertEquals(
            PlanFollowUp.DROP,
            planFollowUpAction(sameTurn = true, inFlight = false, stopReason = "cancelled", errorMessage = "Cancelled"),
        )
        // Failed before any stop reason landed.
        assertEquals(
            PlanFollowUp.DROP,
            planFollowUpAction(sameTurn = true, inFlight = false, stopReason = null, errorMessage = "boom"),
        )
        // Ended with neither — nothing says it completed, so don't send.
        assertEquals(
            PlanFollowUp.DROP,
            planFollowUpAction(sameTurn = true, inFlight = false, stopReason = null, errorMessage = null),
        )
    }

    @Test
    fun `parked notes are only ever released to the turn that parked them`() {
        // The originating turn can end through a path that never reaches `finalizeTurn`
        // (dead connection reconciled against the server transcript, exhausted
        // reconnect), leaving the notes parked. The key is what stops the NEXT turn's
        // completion from picking them up and sending them.
        val parked = ParkedPlanNotes("live-a", "add tests")
        assertEquals("add tests", parked.notesFor("live-a"))
        assertEquals(null, parked.notesFor("live-b"))
    }

    @Test
    fun `notes never attach to a different, unrelated turn`() {
        // A new turn started while the answer was in flight: parking would deliver the
        // notes on ITS completion, and sending would interrupt it.
        assertEquals(
            PlanFollowUp.DROP,
            planFollowUpAction(sameTurn = false, inFlight = true, stopReason = null, errorMessage = null),
        )
        assertEquals(
            PlanFollowUp.DROP,
            planFollowUpAction(sameTurn = false, inFlight = false, stopReason = "end_turn", errorMessage = null),
        )
    }
}
