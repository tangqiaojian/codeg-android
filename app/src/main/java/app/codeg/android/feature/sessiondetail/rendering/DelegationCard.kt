package app.codeg.android.feature.sessiondetail.rendering

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Assignment
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.codeg.android.R
import app.codeg.android.core.designsystem.component.AgentAvatar
import app.codeg.android.core.designsystem.theme.CodegTheme
import app.codeg.android.core.model.AgentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject

enum class DelegationCardStatus { STARTING, RUNNING, WAITING, OK, ERR }

data class DelegationCardModel(
    val agentType: AgentType?,
    val task: String?,
    val taskId: String?,
    val status: DelegationCardStatus,
    val childConversationId: Int?,
    val errorText: String?,
    val hasModel: Boolean,
)

data class DelegationActions(
    val onOpenConversation: (Int) -> Unit = {},
    val onOpenTask: (Int) -> Unit = {},
    val taskIdForConversation: (Int) -> Int? = { null },
)

val LocalDelegationActions = staticCompositionLocalOf { DelegationActions() }

/**
 * Pure parser for `delegate_to_agent` cards. Mirrors the web
 * `delegation-card` helpers: input → meta → tool output.
 */
object DelegationCard {

    private val lenient = Json { ignoreUnknownKeys = true; isLenient = true }
    private val wrapperKeys = listOf("arguments", "input", "params", "payload", "_meta", "args")

    fun isDelegationTool(name: String): Boolean {
        val canonical = ToolDerive.display(name).lowercase().replace("_", "").replace("-", "")
        return canonical == "delegatetoagent" || canonical == "delegatetask"
    }

    fun parse(
        input: String?,
        output: String?,
        meta: JsonObject?,
        isError: Boolean,
        state: ToolCallState,
    ): DelegationCardModel {
        val parsedInput = parseInput(input)
        val parsedMeta = parseMeta(meta)
        val parsedOutput = parseToolOutput(output, isError)
        val childConversationId = parsedMeta?.childConversationId
            ?: parsedOutput?.childConversationId
        val status = resolveStatus(parsedMeta, parsedOutput, isError, state)
        val hasModel = parsedMeta != null ||
            parsedInput.agentType != null ||
            !parsedInput.task.isNullOrBlank() ||
            childConversationId != null
        return DelegationCardModel(
            agentType = parsedInput.agentType,
            task = parsedInput.task?.takeIf { it.isNotBlank() } ?: parsedMeta?.task,
            taskId = parsedMeta?.taskId,
            status = status,
            childConversationId = childConversationId,
            errorText = parsedOutput?.errorText,
            hasModel = hasModel,
        )
    }

    private data class ParsedInput(val agentType: AgentType?, val task: String?)
    private data class ParsedMeta(
        val status: DelegationCardStatus,
        val childConversationId: Int?,
        val task: String?,
        val taskId: String?,
    )
    private data class ParsedOutput(
        val kind: Kind,
        val childConversationId: Int?,
        val errorText: String?,
    ) {
        enum class Kind { ACK, OUTCOME }
    }

    private fun parseInput(raw: String?): ParsedInput {
        val root = parseJson(raw) ?: return ParsedInput(null, null)
        val args = findDelegationArgs(root, 0) ?: return ParsedInput(null, null)
        val agent = args.string("agent_type")?.let { AgentType.knownFromWire(it) }
        return ParsedInput(agent, args.string("task") ?: args.string("description"))
    }

    private fun parseMeta(meta: JsonObject?): ParsedMeta? {
        val inner = meta?.get("codeg.delegation") as? JsonObject ?: return null
        val status = when (inner.string("status")) {
            "running", "pending" -> DelegationCardStatus.RUNNING
            "completed", "ok" -> DelegationCardStatus.OK
            "failed", "err" -> DelegationCardStatus.ERR
            else -> return null
        }
        return ParsedMeta(
            status = status,
            childConversationId = inner.int("child_conversation_id"),
            task = inner.string("task_preview"),
            taskId = inner.string("task_id"),
        )
    }

    private fun parseToolOutput(raw: String?, isError: Boolean): ParsedOutput? {
        if (raw.isNullOrBlank()) return null
        val root = parseJson(raw) ?: return null
        interpretReport(root, isError)?.let { return it }
        (root["result"] as? JsonObject)?.let { interpretReport(it, isError)?.let { found -> return found } }
        val content = root["content"] as? JsonArray
        val first = content?.firstOrNull() as? JsonObject
        if (first != null) {
            (first["json"] as? JsonObject)?.let { interpretReport(it, isError)?.let { found -> return found } }
            first.string("text")?.let { text ->
                parseJson(extractEmbeddedJson(text))?.let { interpretReport(it, isError)?.let { found -> return found } }
            }
        }
        return null
    }

    private fun interpretReport(obj: JsonObject, isError: Boolean): ParsedOutput? {
        val childId = obj.int("child_conversation_id")
        val status = obj.string("status")
        if (status != null) {
            return when (status) {
                "completed" -> ParsedOutput(ParsedOutput.Kind.OUTCOME, childId, null)
                "failed", "canceled" -> ParsedOutput(
                    ParsedOutput.Kind.OUTCOME,
                    childId,
                    obj.string("message") ?: obj.string("error_code") ?: "Delegation failed.",
                )
                else -> ParsedOutput(ParsedOutput.Kind.ACK, childId, null)
            }
        }
        return when (obj.string("kind")) {
            "ok" -> ParsedOutput(ParsedOutput.Kind.OUTCOME, childId, null)
            "err" -> ParsedOutput(
                ParsedOutput.Kind.OUTCOME,
                childId,
                obj.string("message") ?: obj.string("code") ?: "Delegation failed.",
            )
            "ack" -> ParsedOutput(ParsedOutput.Kind.ACK, childId, null)
            else -> if (childId != null) {
                ParsedOutput(if (isError) ParsedOutput.Kind.OUTCOME else ParsedOutput.Kind.ACK, childId, if (isError) obj.string("message") else null)
            } else {
                null
            }
        }
    }

    private fun resolveStatus(
        meta: ParsedMeta?,
        output: ParsedOutput?,
        isError: Boolean,
        state: ToolCallState,
    ): DelegationCardStatus {
        if (isError || state == ToolCallState.ERROR || meta?.status == DelegationCardStatus.ERR) {
            return DelegationCardStatus.ERR
        }
        if (meta?.status == DelegationCardStatus.OK) return DelegationCardStatus.OK
        if (output?.kind == ParsedOutput.Kind.OUTCOME) {
            return if (output.errorText != null) DelegationCardStatus.ERR else DelegationCardStatus.OK
        }
        if (meta?.status == DelegationCardStatus.RUNNING || output?.kind == ParsedOutput.Kind.ACK) {
            return DelegationCardStatus.RUNNING
        }
        if (state == ToolCallState.RUNNING || state == ToolCallState.INPUT_STREAMING) {
            return DelegationCardStatus.STARTING
        }
        return DelegationCardStatus.STARTING
    }

    private fun findDelegationArgs(value: JsonElement, depth: Int): JsonObject? {
        if (depth > 4) return null
        val obj = when (value) {
            is JsonObject -> value
            is JsonPrimitive -> parseJson(value.contentOrNull) ?: return null
            else -> return null
        }
        if (obj.string("task") != null || obj.string("agent_type") != null || obj.string("working_dir") != null) {
            return obj
        }
        for (key in wrapperKeys) {
            obj[key]?.let { child -> findDelegationArgs(child, depth + 1)?.let { return it } }
        }
        return null
    }

    private fun parseJson(raw: String?): JsonObject? {
        if (raw.isNullOrBlank()) return null
        return runCatching { lenient.parseToJsonElement(raw).jsonObject }.getOrNull()
    }

    private fun extractEmbeddedJson(text: String): String? {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return text.substring(start, end + 1)
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun JsonObject.int(key: String): Int? {
        val primitive = this[key] as? JsonPrimitive ?: return null
        primitive.intOrNull?.let { return it }
        return primitive.contentOrNull?.toIntOrDefault()
    }

    private fun String.toIntOrDefault(): Int? = toIntOrNull()
}

@Composable
fun DelegationCardView(
    model: DelegationCardModel,
    modifier: Modifier = Modifier,
) {
    val colors = CodegTheme.colors
    val actions = LocalDelegationActions.current
    val statusColor = when (model.status) {
        DelegationCardStatus.STARTING, DelegationCardStatus.RUNNING, DelegationCardStatus.WAITING -> Color(0xFFE49A39)
        DelegationCardStatus.OK -> Color(0xFF39B77A)
        DelegationCardStatus.ERR -> colors.danger
    }
    val statusLabel = when (model.status) {
        DelegationCardStatus.STARTING -> stringResource(R.string.delegation_status_starting)
        DelegationCardStatus.RUNNING -> stringResource(R.string.delegation_status_running)
        DelegationCardStatus.WAITING -> stringResource(R.string.delegation_status_waiting)
        DelegationCardStatus.OK -> stringResource(R.string.delegation_status_ok)
        DelegationCardStatus.ERR -> stringResource(R.string.delegation_status_err)
    }
    val relatedTaskId = model.childConversationId?.let(actions.taskIdForConversation)
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.bgElevated.copy(alpha = 0.72f))
            .border(0.5.dp, colors.hairline, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (model.agentType != null) {
                AgentAvatar(model.agentType, size = 32.dp)
            } else {
                Icon(
                    Icons.AutoMirrored.Rounded.Assignment,
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(colors.bg)
                        .padding(6.dp),
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = model.agentType?.displayName ?: stringResource(R.string.delegation_unknown_agent),
                        color = colors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Text(
                        text = statusLabel,
                        color = statusColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(statusColor.copy(alpha = 0.14f))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
                model.task?.takeIf { it.isNotBlank() }?.let {
                    Text(it, color = colors.textSecondary, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                model.errorText?.takeIf { it.isNotBlank() }?.let {
                    Text(it, color = colors.danger, fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            model.childConversationId?.let { id ->
                TextButton(onClick = { actions.onOpenConversation(id) }) {
                    Icon(Icons.Rounded.Forum, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(stringResource(R.string.delegation_open_session), modifier = Modifier.padding(start = 4.dp))
                }
            }
            relatedTaskId?.let { taskId ->
                TextButton(onClick = { actions.onOpenTask(taskId) }) {
                    Icon(Icons.AutoMirrored.Rounded.Assignment, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(stringResource(R.string.delegation_open_task), modifier = Modifier.padding(start = 4.dp))
                }
            }
        }
    }
}
