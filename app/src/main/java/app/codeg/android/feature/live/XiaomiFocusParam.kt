package app.codeg.android.feature.live

import android.app.Notification
import androidx.core.app.NotificationCompat
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
                    put("filterWhenNoPermission", false)
                    put("ticker", ticker)
                    put("aodTitle", ticker)
                    // Expanded focus (OS2/OS3) — text component 1.
                    putJsonObject("baseInfo") {
                        put("type", 1)
                        put("title", title.take(24))
                        put("content", content.take(40))
                    }
                    putJsonObject("param_island") {
                        put("islandProperty", 1)
                        putJsonObject("bigIslandArea") {
                            putJsonObject("imageTextInfoLeft") {
                                put("type", 1)
                                putJsonObject("picInfo") { put("type", 1) }
                                putJsonObject("textInfo") {
                                    put("title", title.take(8))
                                    put("content", content.take(8))
                                }
                            }
                        }
                        putJsonObject("smallIslandArea") {
                            putJsonObject("textInfo") {
                                put("title", ticker.take(4))
                            }
                        }
                    }
                }
            },
        )

    /** Official HyperOS samples mutate extras after [Notification.build]; also
     *  stamp the builder so Compat copies them in. */
    fun attach(builder: NotificationCompat.Builder, json: String): Notification {
        builder.extras.putString(EXTRA_KEY, json)
        val notification = builder.build()
        notification.extras.putString(EXTRA_KEY, json)
        return notification
    }

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
