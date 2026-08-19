package app.codeg.android.core.model

import app.codeg.android.core.network.CodegJson
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Ports the Web `session-failures.ts` contract: monotonic upsert, inferred
 * settle, dismiss-vs-recover, and last-user-prompt retry text.
 */
class SessionFailureTest {

    private val json: Json = CodegJson.response

    private fun record(
        id: String,
        revision: Int,
        category: String = "limit",
        severity: String = "warning",
        title: String = "$id@$revision",
        actions: List<String> = listOf("retry"),
        resolved: Boolean = false,
        dismissed: Boolean = false,
        details: String? = null,
    ) = SessionFailureRecord(
        id = id,
        revision = revision,
        category = category,
        severity = severity,
        title = title,
        details = details,
        actions = actions,
        resolved = resolved,
        dismissed = dismissed,
    )

    @Test
    fun `upsert accepts fresh ids and strictly higher revisions in place`() {
        var table = SessionFailures.upsert(emptyList(), record("a", 1))
        table = SessionFailures.upsert(table, record("b", 1))
        table = SessionFailures.upsert(table, record("a", 2, title = "revised"))
        assertEquals(2, table.size)
        assertEquals("revised", table.first { it.id == "a" }.title)
    }

    @Test
    fun `upsert rejects equal and lower revisions by reference`() {
        val table = SessionFailures.upsert(emptyList(), record("a", 2))
        assertSame(table, SessionFailures.upsert(table, record("a", 2)))
        assertSame(table, SessionFailures.upsert(table, record("a", 1)))
    }

    @Test
    fun `merge adopts equal-revision resolved from a snapshot`() {
        val table = SessionFailures.upsert(emptyList(), record("a", 2))
        assertFalse(table[0].resolved)
        val hydrated = SessionFailures.merge(table, listOf(record("a", 2, resolved = true)))
        assertTrue(hydrated[0].resolved)
        assertEquals(2, hydrated[0].revision)
        assertSame(hydrated, SessionFailures.merge(hydrated, listOf(record("a", 2))))
        assertFalse(SessionFailures.merge(hydrated, listOf(record("a", 3)))[0].resolved)
    }

    @Test
    fun `resolved watermark rejects stale upserts and re-arms on a newer revision`() {
        var table = SessionFailures.upsert(emptyList(), record("a", 2))
        table = SessionFailures.settle(table, SessionFailureSettleScope.ALL)
        assertTrue(table[0].resolved)
        assertSame(table, SessionFailures.upsert(table, record("a", 2)))
        assertSame(table, SessionFailures.upsert(table, record("a", 1)))
        val rearmed = SessionFailures.upsert(table, record("a", 3))
        assertFalse(rearmed[0].resolved)
        assertEquals(3, rearmed[0].revision)
    }

    @Test
    fun `settle warnings leaves errors and all settles everything`() {
        val table = listOf(record("w", 1), record("e", 1, severity = "error"))
        val settled = SessionFailures.settle(table, SessionFailureSettleScope.WARNINGS)
        assertTrue(settled.first { it.id == "w" }.resolved)
        assertFalse(settled.first { it.id == "e" }.resolved)
        assertTrue(SessionFailures.settle(settled, SessionFailureSettleScope.ALL).all { it.resolved })
    }

    @Test
    fun `settle retry incidents spares unknown notices and errors`() {
        val table = listOf(
            record("conn", 1, category = "connection"),
            record("notice", 1, category = "unknown"),
            record("err", 1, category = "connection", severity = "error"),
        )
        val settled = SessionFailures.settle(table, SessionFailureSettleScope.RETRY_INCIDENTS)
        assertTrue(settled.first { it.id == "conn" }.resolved)
        assertFalse(settled.first { it.id == "notice" }.resolved)
        assertFalse(settled.first { it.id == "err" }.resolved)
    }

    @Test
    fun `hasSettleableRetryIncident is only unresolved non-unknown warnings`() {
        assertFalse(SessionFailures.hasSettleableRetryIncident(emptyList()))
        assertFalse(SessionFailures.hasSettleableRetryIncident(listOf(record("n", 1, category = "unknown"))))
        assertFalse(SessionFailures.hasSettleableRetryIncident(listOf(record("e", 1, severity = "error"))))
        assertFalse(
            SessionFailures.hasSettleableRetryIncident(
                listOf(record("c", 1, category = "connection", resolved = true)),
            ),
        )
        assertTrue(
            SessionFailures.hasSettleableRetryIncident(listOf(record("c", 1, category = "connection"))),
        )
    }

    @Test
    fun `dismiss marks resolved and dismissed without counting as recovered`() {
        val table = SessionFailures.dismiss(listOf(record("w", 1)), listOf("w"))
        assertTrue(table[0].dismissed)
        assertTrue(table[0].resolved)
        assertNull(SessionFailures.mostRecentRecoveredWarning(table))
    }

    @Test
    fun `dismiss silences an already-resolved recovered line`() {
        val table = listOf(record("a", 1, resolved = true))
        val next = SessionFailures.dismiss(table, listOf("a"))
        assertTrue(next[0].resolved)
        assertTrue(next[0].dismissed)
        assertNull(SessionFailures.mostRecentRecoveredWarning(next))
    }

    @Test
    fun `dismiss is a no-op for unknown or already-dismissed ids`() {
        val table = listOf(
            record("a", 1, resolved = true, dismissed = true),
            record("b", 1),
        )
        assertSame(table, SessionFailures.dismiss(table, listOf("a")))
        assertSame(table, SessionFailures.dismiss(table, listOf("nope")))
        assertSame(table, SessionFailures.dismiss(table, emptyList()))
    }

    @Test
    fun `active view collapses warnings and never collapses errors`() {
        val view = SessionFailures.activeView(
            listOf(
                record("e1", 1, severity = "error"),
                record("e2", 1, severity = "error"),
                record("w1", 1),
                record("w2", 1),
                record("gone", 1, resolved = true),
            ),
        )
        assertEquals(listOf("e1", "e2"), view.errors.map { it.id })
        assertEquals("w2", view.warning?.id)
        assertEquals(1, view.hiddenWarnings)
        assertEquals(listOf("w1", "w2"), view.warningIds)
    }

    @Test
    fun `known actions keep only retry login new_session`() {
        assertEquals(
            listOf(SessionFailureAction.RETRY, SessionFailureAction.NEW_SESSION),
            SessionFailures.knownActions(record("a", 1, actions = listOf("retry", "sing", "new_session"))),
        )
    }

    @Test
    fun `last user prompt text joins text blocks and skips image-only turns`() {
        val turns = listOf(
            MessageTurn(
                id = "u1",
                role = TurnRole.USER,
                blocks = listOf(ContentBlock.Text("hello"), ContentBlock.Text("world")),
                timestamp = Instant.EPOCH,
            ),
            MessageTurn(
                id = "a1",
                role = TurnRole.ASSISTANT,
                blocks = listOf(ContentBlock.Text("reply")),
                timestamp = Instant.EPOCH,
            ),
            MessageTurn(
                id = "u2",
                role = TurnRole.USER,
                blocks = listOf(ContentBlock.Image(ImageData("abc"))),
                timestamp = Instant.EPOCH,
            ),
        )
        assertEquals("hello\nworld", SessionFailures.lastUserPromptText(turns))
        assertNull(SessionFailures.lastUserPromptText(turns.drop(1)))
    }

    @Test
    fun `session_failure event decodes nested record`() {
        val event = AcpEvent.fromWire(
            json.parseToJsonElement(
                """{"type":"session_failure","record":{"id":"sf1","revision":2,
                   "category":"limit","severity":"error","title":"Rate limited",
                   "details":"try later","actions":["retry","new_session"]}}""",
            ).jsonObject,
            json,
        ) as AcpEvent.SessionFailure
        assertEquals("sf1", event.record.id)
        assertEquals(2, event.record.revision)
        assertEquals("limit", event.record.category)
        assertEquals("error", event.record.severity)
        assertEquals("Rate limited", event.record.title)
        assertEquals("try later", event.record.details)
        assertEquals(listOf("retry", "new_session"), event.record.actions)
    }

    @Test
    fun `turn_retrying event decodes message and status`() {
        val event = AcpEvent.fromWire(
            json.parseToJsonElement(
                """{"type":"turn_retrying","message":"retrying upstream","error_status":429}""",
            ).jsonObject,
            json,
        ) as AcpEvent.TurnRetrying
        assertEquals("retrying upstream", event.message)
        assertEquals(429, event.errorStatus)
    }

    @Test
    fun `snapshot hydrates session_failures and last_error`() {
        val snap = LiveSessionSnapshot.fromWire(
            json.parseToJsonElement(
                """{"connection_id":"c1","session_failures":[
                   {"id":"sf1","revision":1,"category":"connection","severity":"warning","title":"blip"}
                   ],"last_error":{"message":"agent died","code":"crash","details":"stderr"}}""",
            ).jsonObject,
            json,
        )
        assertEquals(1, snap.sessionFailures.size)
        assertEquals("sf1", snap.sessionFailures[0].id)
        assertEquals("agent died", snap.lastError?.message)
        assertEquals("crash", snap.lastError?.code)
        assertEquals("stderr", snap.lastError?.details)
    }

    @Test
    fun `malformed session_failure becomes unknown rather than throwing`() {
        val event = AcpEvent.fromWire(
            json.parseToJsonElement("""{"type":"session_failure"}""").jsonObject,
            json,
        )
        assertTrue(event is AcpEvent.Unknown || (event is AcpEvent.SessionFailure && event.record.id.isEmpty()))
    }
}
