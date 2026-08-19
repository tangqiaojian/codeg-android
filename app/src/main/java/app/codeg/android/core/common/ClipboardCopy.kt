package app.codeg.android.core.common

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

/**
 * Writes [text] to the system clipboard.
 *
 * Compose's [androidx.compose.ui.platform.ClipboardManager.setText] is a silent
 * no-op on several OEM Android builds (notably some HyperOS / ColorOS skins),
 * so every copy control in the app goes through this helper instead.
 */
fun copyPlainText(context: Context, text: String, label: String = "text"): Boolean {
    if (text.isEmpty()) return false
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        ?: return false
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    return true
}
