package app.codeg.android.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import app.codeg.android.core.model.AgentType

/** Project-owned visual palette and neutral monograms for supported agents. */
object AgentVisuals {

    /** Accessible background colour for the agent's neutral monogram badge. */
    fun accent(agent: AgentType): Color = when (agent) {
        AgentType.CLAUDE_CODE -> Color(0xFFA84343)
        AgentType.CODEX -> Color(0xFF00695C)
        AgentType.OPEN_CODE -> Color(0xFF3F51B5)
        AgentType.GEMINI -> Color(0xFF1565C0)
        AgentType.OPEN_CLAW -> Color(0xFFA34F17)
        AgentType.CLINE -> Color(0xFF3E7D3B)
        AgentType.HERMES -> Color(0xFF6A4C93)
        AgentType.CODE_BUDDY -> Color(0xFF1E5AA8)
        AgentType.KIMI_CODE -> Color(0xFF2B63B8)
        AgentType.PI -> Color(0xFF50555E)
        AgentType.GROK -> Color(0xFF333333)
    }

    /** Short, non-logo label used inside the badge. */
    fun monogram(agent: AgentType): String = when (agent) {
        AgentType.CLAUDE_CODE -> "CC"
        AgentType.CODEX -> "CX"
        AgentType.OPEN_CODE -> "OC"
        AgentType.GEMINI -> "GM"
        AgentType.OPEN_CLAW -> "OW"
        AgentType.CLINE -> "CL"
        AgentType.HERMES -> "HM"
        AgentType.CODE_BUDDY -> "CB"
        AgentType.KIMI_CODE -> "KM"
        AgentType.PI -> "PI"
        AgentType.GROK -> "GR"
    }
}

/**
 * Renders an accessible, project-owned monogram instead of redistributing
 * third-party brand artwork. The surrounding UI supplies the visible agent name;
 * this badge retains its own description for standalone uses.
 */
@Composable
fun AgentIcon(
    agent: AgentType,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val fontSize = with(LocalDensity.current) { (size * 0.38f).toSp() }
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(AgentVisuals.accent(agent))
            .clearAndSetSemantics { contentDescription = agent.displayName },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = AgentVisuals.monogram(agent),
            color = Color.White,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}
