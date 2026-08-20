package app.codeg.android.feature.sessiondetail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.codeg.android.R
import app.codeg.android.core.designsystem.theme.CodegTheme

@Composable
fun MessageQueueBar(
    queue: List<QueuedPrompt>,
    onRemove: (String) -> Unit,
    onEdit: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (queue.isEmpty()) return
    val colors = CodegTheme.colors
    var expanded by rememberSaveable { mutableStateOf(true) }
    Column(
        modifier
            .fillMaxWidth()
            .background(colors.bgElevated.copy(alpha = 0.92f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.compose_queue_title, queue.size),
                    color = colors.textSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(R.string.compose_queue_sync_hint),
                    color = colors.textTertiary,
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = { expanded = !expanded }) {
                Icon(
                    if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                    contentDescription = stringResource(
                        if (expanded) R.string.compose_queue_collapse else R.string.compose_queue_expand,
                    ),
                    tint = colors.textSecondary,
                )
            }
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                queue.forEachIndexed { index, item ->
                    QueuedPromptRow(
                        index = index,
                        item = item,
                        onRemove = onRemove,
                        onEdit = onEdit,
                    )
                }
            }
        }
    }
}

@Composable
private fun QueuedPromptRow(
    index: Int,
    item: QueuedPrompt,
    onRemove: (String) -> Unit,
    onEdit: (String) -> Unit,
) {
    val colors = CodegTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp),
    ) {
        Text(
            "#${index + 1}",
            color = colors.textTertiary,
            fontSize = 12.sp,
            modifier = Modifier.padding(end = 8.dp),
        )
        Text(
            item.text,
            color = colors.textPrimary,
            fontSize = 13.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .clickable { onEdit(item.id) }
                .padding(vertical = 6.dp),
        )
        IconButton(onClick = { onEdit(item.id) }) {
            Icon(
                Icons.Rounded.Edit,
                contentDescription = stringResource(R.string.compose_queue_edit),
                tint = colors.accent,
            )
        }
        IconButton(onClick = { onRemove(item.id) }) {
            Icon(
                Icons.Rounded.Close,
                contentDescription = stringResource(R.string.compose_queue_remove),
                tint = colors.textSecondary,
            )
        }
    }
}
