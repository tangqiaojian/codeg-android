package app.codeg.android.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.codeg.android.core.designsystem.theme.CodegTheme

/**
 * Full-width prominent button in the accent colour (iOS `PrimaryGlassButton`).
 * Shows a spinner while [loading] and disables itself.
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
) {
    val colors = CodegTheme.colors
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CodegTheme.dimens.radiusMd),
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.accent,
            contentColor = colors.onAccent,
            disabledContainerColor = colors.accent.copy(alpha = 0.40f),
            disabledContentColor = colors.onAccent.copy(alpha = 0.70f),
        ),
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = colors.onAccent,
                strokeWidth = 2.dp,
            )
        } else {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            }
            Text(
                text = text,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = if (icon != null) 8.dp else 0.dp),
            )
        }
    }
}

/**
 * iOS `UISegmentedControl`: grey track, white selected pill, 13pt labels.
 */
@Composable
fun CodegSegmented(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = CodegTheme.colors
    val track = if (colors.isDark) Color.White.copy(alpha = 0.08f) else Color(0xFF767680).copy(alpha = 0.10f)
    val selectedFill = if (colors.isDark) Color.White.copy(alpha = 0.16f) else Color.White
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(track)
            .padding(1.dp),
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (selected) selectedFill else Color.Transparent)
                    .clickable { onSelect(index) }
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    fontSize = 12.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (selected) colors.textPrimary else colors.textSecondary,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * Material 3 [FilterChip] themed to the accent: accent-tinted container + accent
 * content when selected, hairline-outlined otherwise. [icon] renders as the leading
 * icon (pass `Icons.Rounded.Check` when selected for the canonical multi-select look).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodegFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val colors = CodegTheme.colors
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        label = { Text(label) },
        leadingIcon = icon?.let {
            { Icon(it, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
        },
        colors = FilterChipDefaults.filterChipColors(
            labelColor = colors.textSecondary,
            iconColor = colors.textSecondary,
            selectedContainerColor = colors.accent.copy(alpha = 0.18f),
            selectedLabelColor = colors.accent,
            selectedLeadingIconColor = colors.accent,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = enabled,
            selected = selected,
            borderColor = colors.surfaceStroke,
            selectedBorderColor = Color.Transparent,
        ),
    )
}
