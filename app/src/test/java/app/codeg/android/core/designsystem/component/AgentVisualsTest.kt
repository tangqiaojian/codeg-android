package app.codeg.android.core.designsystem.component

import androidx.compose.ui.graphics.luminance
import app.codeg.android.core.model.AgentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentVisualsTest {

    @Test
    fun `monograms are short and unique`() {
        val monograms = AgentType.entries.map(AgentVisuals::monogram)

        assertEquals(monograms.size, monograms.toSet().size)
        assertTrue(monograms.all { it.length == 2 && it.all(Char::isUpperCase) })
    }

    @Test
    fun `white monogram text meets WCAG AA contrast`() {
        AgentType.entries.forEach { agent ->
            val backgroundLuminance = AgentVisuals.accent(agent).luminance()
            val contrastWithWhite = 1.05f / (backgroundLuminance + 0.05f)

            assertTrue("${agent.displayName} contrast was $contrastWithWhite", contrastWithWhite >= 4.5f)
        }
    }
}
