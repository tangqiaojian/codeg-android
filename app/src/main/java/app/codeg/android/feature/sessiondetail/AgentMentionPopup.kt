package app.codeg.android.feature.sessiondetail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.codeg.android.R
import app.codeg.android.core.designsystem.component.AgentIcon
import app.codeg.android.core.designsystem.theme.CodegTheme
import app.codeg.android.core.model.AcpAgentInfo

private fun AcpAgentInfo.mentionLabel(): String = name.trim().ifEmpty { agentType.displayName }

private fun AcpAgentInfo.matchesMentionQuery(query: String): Boolean {
    val needle = query.trim()
    if (needle.isEmpty()) return true
    return mentionLabel().contains(needle, ignoreCase = true) ||
        agentType.wire.contains(needle, ignoreCase = true)
}

@Composable
fun AgentMentionPopup(
    expanded: Boolean,
    query: String,
    agents: List<AcpAgentInfo>,
    onSelect: (AcpAgentInfo) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = CodegTheme.colors
    val filtered = agents
        .asSequence()
        .filter { it.available && it.enabled }
        .filter { it.matchesMentionQuery(query) }
        .sortedBy { it.sortOrder }
        .toList()

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = modifier.widthIn(min = 220.dp, max = 340.dp),
    ) {
        if (filtered.isEmpty()) {
            DropdownMenuItem(
                text = {
                    Text(
                        text = stringResource(R.string.compose_agent_mention_empty),
                        color = colors.textSecondary,
                    )
                },
                enabled = false,
                onClick = {},
            )
        } else {
            filtered.forEach { agent ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(
                                text = agent.mentionLabel(),
                                color = colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (agent.description.isNotBlank()) {
                                Text(
                                    text = agent.description,
                                    color = colors.textTertiary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    },
                    leadingIcon = {
                        AgentIcon(agent.agentType, size = 22.dp)
                    },
                    onClick = { onSelect(agent) },
                )
            }
        }
    }
}
