package app.codeg.android.core.model

/** A server-backed agent that can be inserted into a prompt. */
data class AgentMentionTarget(
    val agentType: AgentType,
    val label: String,
)

/** A visible `@label` span in a draft. [end] is exclusive. */
data class AgentMention(
    val start: Int,
    val end: Int,
    val target: AgentMentionTarget,
) {
    val displayText: String
        get() = "@${target.label.removePrefix("@")}"
}

data class AgentMentionQuery(
    val start: Int,
    val end: Int,
    val query: String,
)

/**
 * Returns the standalone `@query` immediately before [cursor], or null when
 * the caret is not inside a mention query.
 */
fun findActiveAgentMentionQuery(text: String, cursor: Int): AgentMentionQuery? {
    if (cursor !in 0..text.length) return null
    var at = cursor - 1
    while (at >= 0 && !text[at].isWhitespace()) {
        if (text[at] == '@') break
        at--
    }
    if (at < 0 || text[at] != '@') return null
    if (at > 0) {
        val boundary = text[at - 1]
        if (boundary.isLetterOrDigit() || boundary == '_') return null
    }
    return AgentMentionQuery(at, cursor, text.substring(at + 1, cursor))
}

/** Visible composer text plus the metadata needed to serialize agent mentions. */
data class AgentMentionDraft(
    val text: String,
    val mentions: List<AgentMention> = emptyList(),
) {
    init {
        require(mentions.zipWithNext().all { (left, right) -> left.end <= right.start }) {
            "Agent mentions must be ordered and non-overlapping"
        }
        require(mentions.all { it.start >= 0 && it.end <= text.length && it.start < it.end }) {
            "Agent mention ranges must be inside the draft"
        }
    }

    fun insertMention(start: Int, end: Int, target: AgentMentionTarget): AgentMentionDraft {
        require(start in 0..text.length)
        require(end in start..text.length)
        val visible = "@${target.label.removePrefix("@")}"
        val updatedText = text.replaceRange(start, end, visible)
        val delta = visible.length - (end - start)
        val updated = mentions.mapNotNull { mention ->
            when {
                mention.end <= start -> mention
                mention.start >= end -> mention.copy(
                    start = mention.start + delta,
                    end = mention.end + delta,
                )
                else -> null
            }
        } + AgentMention(start, start + visible.length, target)
        return AgentMentionDraft(updatedText, updated.sortedBy { it.start })
    }

    /**
     * Applies one Compose text edit. The common prefix/suffix identify the
     * replaced range; mentions outside it shift, while mentions touched by the
     * edit lose metadata rather than attaching to the wrong text.
     */
    fun applyTextChange(newText: String): AgentMentionDraft {
        if (newText == text) return this
        val prefix = commonPrefixLength(text, newText)
        val suffix = commonSuffixLength(text, newText, prefix)
        val oldEnd = text.length - suffix
        val delta = newText.length - text.length
        val updated = mentions.mapNotNull { mention ->
            when {
                mention.end <= prefix -> mention
                mention.start >= oldEnd -> mention.copy(
                    start = mention.start + delta,
                    end = mention.end + delta,
                )
                else -> null
            }
        }
        return AgentMentionDraft(newText, updated)
    }

    /** Removes the whole mention immediately before a collapsed caret. */
    fun deleteMentionBeforeCursor(cursor: Int): AgentMentionDraft? {
        val mention = mentions.firstOrNull { it.end == cursor } ?: return null
        val updatedText = text.removeRange(mention.start, mention.end)
        val updated = mentions.filterNot { it == mention }.map { other ->
            if (other.start >= mention.end) {
                other.copy(
                    start = other.start - (mention.end - mention.start),
                    end = other.end - (mention.end - mention.start),
                )
            } else {
                other
            }
        }
        return AgentMentionDraft(updatedText, updated)
    }

    /** Serializes visible text plus structured links for the Codeg server. */
    fun toWire(): String {
        if (mentions.isEmpty()) return text
        val output = StringBuilder(text.length)
        var cursor = 0
        mentions.forEach { mention ->
            if (mention.start < cursor || text.substring(mention.start, mention.end) != mention.displayText) return@forEach
            output.append(text, cursor, mention.start)
            output.append("[")
            output.append(escapeMarkdown(mention.displayText))
            output.append("](" )
            output.append("codeg://agent/")
            output.append(mention.target.agentType.wire)
            output.append(")")
            cursor = mention.end
        }
        output.append(text, cursor, text.length)
        return output.toString()
    }

    companion object {
        private val AGENT_REFERENCE = Regex("\\[([^]]+)]\\(codeg://agent/([a-z0-9_-]+)\\)")

        fun fromWire(wire: String): AgentMentionDraft {
            val visible = StringBuilder()
            val mentions = mutableListOf<AgentMention>()
            var cursor = 0
            AGENT_REFERENCE.findAll(wire).forEach { match ->
                visible.append(wire, cursor, match.range.first)
                val agent = AgentType.knownFromWire(match.groupValues[2])
                val label = match.groupValues[1]
                if (agent == null || !label.startsWith("@")) {
                    visible.append(match.value)
                } else {
                    val start = visible.length
                    visible.append(label)
                    mentions += AgentMention(
                        start = start,
                        end = visible.length,
                        target = AgentMentionTarget(agent, label.removePrefix("@")),
                    )
                }
                cursor = match.range.last + 1
            }
            visible.append(wire, cursor, wire.length)
            return AgentMentionDraft(visible.toString(), mentions)
        }

        private fun commonPrefixLength(left: String, right: String): Int {
            val limit = minOf(left.length, right.length)
            var index = 0
            while (index < limit && left[index] == right[index]) index++
            return index
        }

        private fun commonSuffixLength(left: String, right: String, prefix: Int): Int {
            val limit = minOf(left.length, right.length) - prefix
            var count = 0
            while (count < limit && left[left.length - count - 1] == right[right.length - count - 1]) count++
            return count
        }

        private fun escapeMarkdown(value: String): String =
            value.replace(Regex("[\\\\`*_~\\[\\]\\(\\)<>]")) { "\\\\${it.value}" }
    }
}
