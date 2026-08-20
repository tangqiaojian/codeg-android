package app.codeg.android.feature.sessions

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.codeg.android.R
import app.codeg.android.core.designsystem.component.CountBadge
import app.codeg.android.core.designsystem.component.SectionBadgeIcon
import app.codeg.android.core.designsystem.theme.CodegTheme

/**
 * A grouped-list section header shared by the Chats and Activity tabs: a tinted
 * circular [SectionBadgeIcon] + a **prominent** group title (`titleLarge`, larger
 * than the row titles so the grouping reads at a glance) + a trailing [CountBadge],
 * tappable to collapse/expand its rows with a chevron that flips ▾↔▴.
 *
 * The whole row is the toggle target (the native accordion idiom). When [collapsible]
 * is false (an empty group), it renders statically — no chevron, no tap.
 */
@Composable
fun CollapsibleSectionHeader(
    icon: ImageVector,
    tint: Color,
    label: String,
    count: Int,
    collapsed: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    collapsible: Boolean = true,
) {
    val colors = CodegTheme.colors
    val rotation by animateFloatAsState(
        targetValue = if (collapsed) 0f else 180f,
        label = "section-chevron",
    )
    val toggleLabel = stringResource(
        if (collapsed) R.string.sessions_expand else R.string.sessions_collapse,
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 10.dp) // inter-section gap, kept outside the ripple
            .clip(RoundedCornerShape(12.dp))
            .then(
                if (collapsible) {
                    Modifier.clickable(onClickLabel = toggleLabel, role = Role.Button, onClick = onToggle)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        // Same 26dp diameter as the row AgentAvatars (SessionRow), so the group icon
        // matches the agent icons and the big title lines up with the row titles.
        SectionBadgeIcon(icon = icon, tint = tint, size = 18.dp)
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        CountBadge(count)
        if (collapsible) {
            Icon(
                Icons.Rounded.ExpandMore,
                contentDescription = null,
                tint = colors.textTertiary,
                modifier = Modifier.size(22.dp).rotate(rotation),
            )
        }
    }
}

/**
 * Workspace / folder header with a card surface, optional breadcrumb, and
 * indent so the Chats list reads as workspace → folder → session.
 */
@Composable
fun HierarchySectionHeader(
    icon: ImageVector,
    tint: Color,
    label: String,
    subtitle: String?,
    count: Int,
    collapsed: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    collapsible: Boolean = true,
    depth: Int = 0,
    emphasized: Boolean = false,
) {
    val colors = CodegTheme.colors
    val rotation by animateFloatAsState(
        targetValue = if (collapsed) 0f else 180f,
        label = "hierarchy-chevron",
    )
    val toggleLabel = stringResource(
        if (collapsed) R.string.sessions_expand else R.string.sessions_collapse,
    )
    val shape = RoundedCornerShape(if (emphasized) 16.dp else 12.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = (depth * 16).dp, top = if (depth == 0) 10.dp else 6.dp)
            .clip(shape)
            .background(
                if (emphasized) colors.bgElevated.copy(alpha = if (colors.isDark) 0.55f else 0.88f)
                else colors.bgElevated.copy(alpha = 0.28f),
            )
            .border(
                CodegTheme.dimens.hairlineWidth,
                if (emphasized) tint.copy(alpha = 0.35f) else colors.hairline,
                shape,
            )
            .then(
                if (collapsible) {
                    Modifier.clickable(onClickLabel = toggleLabel, role = Role.Button, onClick = onToggle)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        SectionBadgeIcon(icon = icon, tint = tint, size = if (emphasized) 28.dp else 22.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = label,
                style = if (emphasized) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            subtitle?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        CountBadge(count)
        if (collapsible) {
            Icon(
                Icons.Rounded.ExpandMore,
                contentDescription = null,
                tint = colors.textTertiary,
                modifier = Modifier.size(22.dp).rotate(rotation),
            )
        }
    }
}
