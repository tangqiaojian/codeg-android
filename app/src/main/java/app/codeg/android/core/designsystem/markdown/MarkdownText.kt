package app.codeg.android.core.designsystem.markdown

import android.widget.Toast
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import app.codeg.android.R
import app.codeg.android.core.common.ChatLink
import app.codeg.android.core.designsystem.theme.CodegTheme

/**
 * Renders inline markdown. Links are [LinkAnnotation]s so a surrounding
 * [androidx.compose.foundation.text.selection.SelectionContainer] can still
 * select/copy plain text; only the link span itself is tappable.
 */
@Composable
fun MarkdownText(
    raw: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 14.sp,
    lineHeight: TextUnit = 21.sp,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    fontStyle: FontStyle? = null,
) {
    val context = LocalContext.current
    val fail = stringResource(R.string.chat_link_failed)
    val annotated = rememberInlineMarkdown(raw) { url ->
        if (!ChatLink.open(context, url)) {
            Toast.makeText(context, fail, Toast.LENGTH_SHORT).show()
        }
    }
    val resolvedColor = if (color == Color.Unspecified) CodegTheme.colors.textPrimary else color
    Text(
        text = annotated,
        fontSize = fontSize,
        lineHeight = lineHeight,
        color = resolvedColor,
        fontWeight = fontWeight,
        fontStyle = fontStyle,
        modifier = modifier,
    )
}
