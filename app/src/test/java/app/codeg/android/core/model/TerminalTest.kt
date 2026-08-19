package app.codeg.android.core.model

import app.codeg.android.core.network.CodegJson
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalTest {
    @Test
    fun terminalRequestsUseServerCamelCaseKeys() {
        val spawn = CodegJson.request.encodeToString(
            TerminalSpawnBody(
                workingDir = "/tmp/project",
                initialCommand = "pwd",
                terminalId = "term-1",
            ),
        )
        val write = CodegJson.request.encodeToString(TerminalWriteBody("term-1", "ls\n"))
        val resize = CodegJson.request.encodeToString(TerminalResizeBody("term-1", 120, 40))

        assertTrue(spawn.contains("\"workingDir\":\"/tmp/project\""))
        assertTrue(spawn.contains("\"initialCommand\":\"pwd\""))
        assertTrue(spawn.contains("\"terminalId\":\"term-1\""))
        assertTrue(write.contains("\"terminalId\":\"term-1\""))
        assertTrue(resize.contains("\"cols\":120"))
        assertTrue(resize.contains("\"rows\":40"))
    }
}
