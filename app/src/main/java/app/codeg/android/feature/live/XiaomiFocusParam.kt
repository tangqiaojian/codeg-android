package app.codeg.android.feature.live

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * HyperOS focus / 超级岛 extras (`miui.focus.param`). Client-side: attach this
 * JSON to a normal [android.app.Notification]. On Xiaomi it can land in the
 * notch island; elsewhere it is ignored and the ongoing notification remains.
 *
 * Full island styling may require Xiaomi’s developer allowlist; `filterWhenNoPermission`
 * is false so we still show a regular notification when focus is off.
 */
object XiaomiFocusParam {
    const val EXTRA_KEY = "miui.focus.param"

    private val json = Json { encodeDefaults = true }

    fun encode(title: String, content: String, ticker: String): String =
        json.encodeToString(
            buildJsonObject {
                putJsonObject("param_v2") {
                    put("business", "codeg_live_task")
                    put("updatable", true)
                    put("enableFloat", false)
                    put("islandFirstFloat", false)
                    put("timeout", 720)
                    put("ticker", ticker)
                    put("aodTitle", ticker)
                    put("title", title)
                    put("content", content)
                    putJsonObject("param_island") {
                        put("islandProperty", 1)
                        putJsonObject("bigIslandArea") {
                            put("textTitle", title.take(16))
                            put("textContent", content.take(24))
                        }
                        putJsonObject("smallIslandArea") {
                            put("textTitle", ticker.take(12))
                        }
                    }
                }
            },
        )

    fun cancel(): String =
        json.encodeToString(
            buildJsonObject {
                putJsonObject("param_v2") {
                    put("business", "codeg_live_task")
                    put("cancel", true)
                    put("updatable", true)
                }
            },
        )
}
