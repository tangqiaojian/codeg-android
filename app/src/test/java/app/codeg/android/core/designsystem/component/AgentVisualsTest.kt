package app.codeg.android.core.designsystem.component

import androidx.compose.ui.graphics.luminance
import app.codeg.android.core.model.AgentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentVisualsTest {

    @Test
    fun `every agent has its own brand icon`() {
        val icons = AgentType.entries.map(AgentVisuals::icon)

        assertEquals(icons.size, icons.toSet().size)
    }

    @Test
    fun `accents are fully opaque in both themes`() {
        AgentType.entries.forEach { agent ->
            assertEquals(1f, AgentVisuals.accent(agent, isDark = false).alpha, 0f)
            assertEquals(1f, AgentVisuals.accent(agent, isDark = true).alpha, 0f)
        }
    }

    @Test
    fun `theme-following marks swap tint with the theme`() {
        // Grok's and Cursor's monochrome brand marks must not vanish on either
        // surface: dark tint in light theme, light tint in dark theme.
        listOf(AgentType.GROK, AgentType.CURSOR).forEach { agent ->
            val light = AgentVisuals.accent(agent, isDark = false).luminance()
            val dark = AgentVisuals.accent(agent, isDark = true).luminance()

            assertTrue("${agent.displayName} light tint should be dark", light < 0.5f)
            assertTrue("${agent.displayName} dark tint should be light", dark > 0.5f)
        }
    }
}
