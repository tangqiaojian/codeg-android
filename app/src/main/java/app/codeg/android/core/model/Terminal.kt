package app.codeg.android.core.model

import kotlinx.serialization.Serializable

@Serializable
data class TerminalInfo(
    val id: String,
    val title: String,
)
