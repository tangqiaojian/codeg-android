package app.codeg.android.core.network

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EventStreamTest {
    @Test
    fun parsesLegacyGlobalTerminalOutputFrames() {
        val frame = EventStream.parseFrame(
            """
            {"channel":"terminal://output/term-1","payload":{"terminal_id":"term-1","data":"hello\\n"}}
            """.trimIndent(),
            Json,
        )

        assertTrue(frame is StreamFrame.Global)
        frame as StreamFrame.Global
        assertEquals("terminal://output/term-1", frame.channel)
        assertEquals("hello\\n", frame.payload.jsonObject["data"]?.jsonPrimitive?.content)
    }

    @Test
    fun parsesTaskAndAutomationChangeFrames() {
        assertTrue(EventStream.parseFrame("""{"channel":"task://changed","payload":{}}""", Json) is StreamFrame.Global)
        assertTrue(EventStream.parseFrame("""{"channel":"automation://changed","payload":{}}""", Json) is StreamFrame.Global)
    }
}
