package app.codeg.android.feature.sessiondetail.rendering

import app.codeg.android.core.designsystem.diff.DiffFile
import app.codeg.android.core.model.ContentBlock
import app.codeg.android.core.model.ImageData
import app.codeg.android.core.model.MessageTurn

/** The visual lifecycle of a tool call. */
enum class ToolCallState { INPUT_STREAMING, RUNNING, DONE, ERROR }

/**
 * A unified, view-ready model for one tool call. Both the persisted path
 * (`tool_use` + `tool_result` paired) and the live path adapt into this, so the
 * card UI is shared. Derived fields are computed once via [of]. Port of iOS
 * `ToolCallVM`.
 */
data class ToolCallVM(
    val id: String,
    val rawName: String,
    val kind: String,
    val state: ToolCallState,
    val input: String?,
    val output: String?,
    val content: String?,
    val isError: Boolean,
    val displayTitle: String,
    val bucket: ToolKindBucket,
    val diffFiles: List<DiffFile>?,
) {
    val trimmedOutput: String get() = (output ?: "").trim()
    val hasOutput: Boolean get() = trimmedOutput.isNotEmpty()
    val isCommand: Boolean get() = bucket == ToolKindBucket.EXECUTE

    companion object {
        fun of(
            id: String,
            rawName: String,
            kind: String,
            state: ToolCallState,
            input: String?,
            output: String?,
            content: String?,
            isError: Boolean,
        ): ToolCallVM {
            val parsed = ToolDerive.parseJson(input)
            val bucket = ToolDerive.bucket(rawName, kind)
            return ToolCallVM(
                id = id,
                rawName = rawName,
                kind = kind,
                state = state,
                input = input,
                output = output,
                content = content,
                isError = isError,
                displayTitle = ToolDerive.title(rawName, parsed),
                bucket = bucket,
                diffFiles = ToolDerive.diff(bucket, input, output, content, parsed),
            )
        }
    }
}

/** One ordered piece of a rendered assistant turn (static subset; live variants added in M4). */
sealed interface RenderPart {
    data class Text(val text: String) : RenderPart
    data class Reasoning(val text: String) : RenderPart
    data class Tool(val vm: ToolCallVM) : RenderPart
    data class ToolGroup(val items: List<ToolCallVM>, val streaming: Boolean) : RenderPart
    data class Image(val image: ImageData, val caption: String?) : RenderPart
    data class Unknown(val type: String) : RenderPart
}

/** Adapts persisted [MessageTurn]s into `[RenderPart]`. Port of iOS `MessageRender`. */
object MessageRender {

    fun adaptTurn(turn: MessageTurn): List<RenderPart> {
        val blocks = turn.blocks
        val consumed = mutableSetOf<Int>()
        val parts = mutableListOf<RenderPart>()

        blocks.forEachIndexed { idx, block ->
            if (idx in consumed) return@forEachIndexed
            when (block) {
                is ContentBlock.Text ->
                    if (block.text.isNotBlank()) parts.add(RenderPart.Text(block.text))
                is ContentBlock.Thinking ->
                    if (block.text.isNotBlank()) parts.add(RenderPart.Reasoning(block.text))
                is ContentBlock.Image ->
                    parts.add(RenderPart.Image(block.image, null))
                is ContentBlock.ImageGeneration -> {
                    if (block.image != null) parts.add(RenderPart.Image(block.image, block.revisedPrompt))
                    else if (!block.revisedPrompt.isNullOrEmpty()) parts.add(RenderPart.Text(block.revisedPrompt))
                }
                is ContentBlock.ToolUse -> {
                    val resultIdx = findResult(blocks, idx, block.id, consumed)
                    var output: String? = null
                    var isErr = false
                    if (resultIdx != null) {
                        (blocks[resultIdx] as? ContentBlock.ToolResult)?.let {
                            output = it.outputPreview; isErr = it.isError; consumed.add(resultIdx)
                        }
                    }
                    val state = when {
                        resultIdx == null -> ToolCallState.RUNNING
                        isErr -> ToolCallState.ERROR
                        else -> ToolCallState.DONE
                    }
                    parts.add(
                        RenderPart.Tool(
                            // Qualify the synthetic fallback with the turn id: a bare positional
                            // "tool-$idx" would collide across replies once it becomes a node key.
                            ToolCallVM.of(block.id ?: "${turn.id}-tool-$idx", block.name, "", state, block.inputPreview, output, null, isErr),
                        ),
                    )
                }
                is ContentBlock.ToolResult -> {
                    parts.add(
                        RenderPart.Tool(
                            ToolCallVM.of(
                                block.id ?: "${turn.id}-result-$idx", "result", "",
                                if (block.isError) ToolCallState.ERROR else ToolCallState.DONE,
                                null, block.outputPreview, null, block.isError,
                            ),
                        ),
                    )
                }
                is ContentBlock.Unknown -> parts.add(RenderPart.Unknown(block.type))
            }
        }
        return groupConsecutiveTools(parts)
    }

    /** Collapse runs of 2+ consecutive tool calls into a [RenderPart.ToolGroup]. */
    fun groupConsecutiveTools(parts: List<RenderPart>): List<RenderPart> {
        val out = mutableListOf<RenderPart>()
        var run = mutableListOf<ToolCallVM>()

        fun flush() {
            if (run.size >= 2) {
                val streaming = run.any { it.state == ToolCallState.RUNNING || it.state == ToolCallState.INPUT_STREAMING }
                out.add(RenderPart.ToolGroup(run.toList(), streaming))
            } else {
                run.firstOrNull()?.let { out.add(RenderPart.Tool(it)) }
            }
            run = mutableListOf()
        }

        for (part in parts) {
            if (part is RenderPart.Tool && part.vm.bucket != ToolKindBucket.TASK) {
                run.add(part.vm)
            } else {
                flush()
                out.add(part)
            }
        }
        flush()
        return out
    }

    private fun findResult(blocks: List<ContentBlock>, after: Int, toolID: String?, consumed: Set<Int>): Int? {
        if (toolID != null) {
            for (j in (after + 1) until blocks.size) {
                if (j in consumed) continue
                val b = blocks[j]
                if (b is ContentBlock.ToolResult && b.id == toolID) return j
            }
        }
        for (j in (after + 1) until blocks.size) {
            if (j in consumed) continue
            val b = blocks[j]
            if (b is ContentBlock.ToolUse) break
            if (b is ContentBlock.ToolResult) return j
        }
        return null
    }
}
