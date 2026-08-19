package app.codeg.android.feature.sessiondetail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
    modifier: Modifier = Modifier,
) {
    if (queue.isEmpty()) return
    val colors = CodegTheme.colors
    Column(
        modifier
            .fillMaxWidth()
            .background(colors.bgElevated.copy(alpha = 0.92f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            stringResource(R.string.compose_queue_title, queue.size),
            color = colors.textSecondary,
            fontSize = 11.sp,
        )
        queue.forEachIndexed { index, item ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "#${index + 1}  ${item.text}",
                    color = colors.textPrimary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { onRemove(item.id) }) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.compose_queue_remove),
                        tint = colors.textSecondary,
                    )
                }
            }
        }
    }
}
