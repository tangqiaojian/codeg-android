package app.codeg.android.core.model

/**
 * Plaintext a user would expect to copy from a chat turn: visible text and
 * image-generation prompts. Thinking, tools, and binary images stay out so a
 * Copy action does not dump scratchpads or JSON payloads.
 */
fun copyableTurnText(blocks: List<ContentBlock>): String =
    buildList {
        for (block in blocks) {
            when (block) {
                is ContentBlock.Text ->
                    if (block.text.isNotBlank()) add(block.text.trimEnd())
                is ContentBlock.ImageGeneration ->
                    block.revisedPrompt?.takeIf { it.isNotBlank() }?.let { add(it.trimEnd()) }
                is ContentBlock.Thinking,
                is ContentBlock.Image,
                is ContentBlock.ToolUse,
                is ContentBlock.ToolResult,
                is ContentBlock.Unknown -> Unit
            }
        }
    }.joinToString("\n\n")
