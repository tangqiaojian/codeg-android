package app.codeg.android.core.designsystem.markdown

import android.widget.Toast
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import app.codeg.android.R
import app.codeg.android.core.common.ChatLink
import app.codeg.android.core.designsystem.theme.CodegTheme

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
    val annotated = rememberInlineMarkdown(raw)
    val context = LocalContext.current
    val fail = stringResource(R.string.chat_link_failed)
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val resolvedColor = if (color == Color.Unspecified) CodegTheme.colors.textPrimary else color
    Text(
        text = annotated,
        fontSize = fontSize,
        lineHeight = lineHeight,
        color = resolvedColor,
        fontWeight = fontWeight,
        fontStyle = fontStyle,
        onTextLayout = { layout = it },
        modifier = modifier.pointerInput(annotated) {
            detectTapGestures { pos ->
                val result = layout ?: return@detectTapGestures
                val offset = result.getOffsetForPosition(pos)
                val url = annotated.getStringAnnotations(URL_TAG, offset, offset).firstOrNull()?.item
                    ?: return@detectTapGestures
                if (!ChatLink.open(context, url)) {
                    Toast.makeText(context, fail, Toast.LENGTH_SHORT).show()
                }
            }
        },
    )
}
