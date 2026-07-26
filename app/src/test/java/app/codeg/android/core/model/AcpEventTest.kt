package app.codeg.android.core.model

import app.codeg.android.core.network.CodegJson
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Verifies the hand-written, flattened `AcpEvent` / `EventEnvelope` decoders. */
class AcpEventTest {

    private val json: Json = CodegJson.response

    private fun obj(raw: String): JsonObject = json.parseToJsonElement(raw).jsonObject
    private fun event(raw: String): AcpEvent = AcpEvent.fromWire(obj(raw), json)

    @Test
    fun `content delta`() {
        assertEquals(AcpEvent.ContentDelta("hi"), event("""{"type":"content_delta","text":"hi"}"""))
    }

    @Test
    fun `tool call reads snake_case fields`() {
        val e = event(
            """{"type":"tool_call","tool_call_id":"t1","title":"Read file",
               "kind":"read","status":"in_progress","raw_input":"path"}""",
        ) as AcpEvent.ToolCall
        assertEquals("t1", e.id)
        assertEquals("Read file", e.title)
        assertEquals("read", e.kind)
        assertEquals("in_progress", e.status)
        assertEquals("path", e.rawInput)
    }

    @Test
    fun `permission request decodes nested options`() {
        val e = event(
            """{"type":"permission_request","request_id":"r1","tool_call":{"kind":"execute"},
               "options":[{"option_id":"o1","name":"Allow","kind":"allow_once"},
                          {"option_id":"o2","name":"Deny","kind":"reject_once"}]}""",
        ) as AcpEvent.PermissionRequest
        assertEquals("r1", e.requestId)
        assertEquals(2, e.options.size)
        assertEquals("o1", e.options[0].optionId)
        assertFalse(e.options[0].isReject)
        assertTrue(e.options[1].isReject)
    }

    @Test
    fun `question request decodes nested questions`() {
        val e = event(
            """{"type":"question_request","question_id":"q1",
               "questions":[{"id":"a","question":"Which?","header":"H","multi_select":false,
                             "options":[{"label":"X","description":"d"}]}]}""",
        ) as AcpEvent.QuestionRequest
        assertEquals("q1", e.questionId)
        assertEquals(1, e.questions.size)
        assertEquals("Which?", e.questions[0].question)
        assertEquals("X", e.questions[0].options[0].label)
    }

    @Test
    fun `turn complete defaults stop reason`() {
        assertEquals(AcpEvent.TurnComplete("end_turn"), event("""{"type":"turn_complete"}"""))
    }

    @Test
    fun `unknown event type is preserved, not thrown`() {
        assertEquals(AcpEvent.Unknown("bananas"), event("""{"type":"bananas","x":1}"""))
    }

    @Test
    fun `envelope is flattened with seq and connection id`() {
        val env = EventEnvelope.fromWire(
            obj("""{"seq":5,"connection_id":"conn-1","type":"content_delta","text":"x"}"""),
            json,
        )
        assertEquals(5L, env.seq)
        assertEquals("conn-1", env.connectionId)
        assertEquals(AcpEvent.ContentDelta("x"), env.event)
    }
}
