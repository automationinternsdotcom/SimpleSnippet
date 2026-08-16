package com.simplesnippet.app.data

import java.util.regex.Pattern

/**
 * Pure text-matching logic for snippet expansion and quick-save.
 * No Android dependencies, so the whole thing is unit-testable on the JVM.
 */
object SnippetMatcher {

    /** A snippet trigger found in text: replace the range [start, endExclusive). */
    data class Match(
        val snippet: Snippet,
        val start: Int,
        val endExclusive: Int,
        val variations: List<String>
    )

    /** A parsed quick-save command, e.g. "(.save:ph:+15550199)". */
    data class SaveCommand(
        val fullMatch: String,
        val trigger: String,
        val content: String
    )

    /**
     * Finds the best snippet match in [text]. Longer triggers win over shorter
     * ones ("..signature" resolves to the "signature" snippet even when "sig"
     * also exists). Snippets with no non-empty variation are skipped without
     * aborting the search. Unless [allowAnywhere], the trigger must sit at the
     * very end of the text.
     */
    fun find(
        text: String,
        prefix: String,
        snippets: List<Snippet>,
        allowAnywhere: Boolean
    ): Match? {
        if (prefix.isEmpty()) return null
        for (s in snippets.sortedByDescending { it.trigger.length }) {
            if (s.trigger.isEmpty()) continue
            val fullTrigger = prefix + s.trigger
            val idx = text.lastIndexOf(fullTrigger)
            if (idx == -1) continue
            val isAtEnd = idx + fullTrigger.length == text.length
            if (!allowAnywhere && !isAtEnd) continue
            val variations = s.contents.filter { it.isNotEmpty() }
            if (variations.isEmpty()) continue
            return Match(s, idx, idx + fullTrigger.length, variations)
        }
        return null
    }

    /** Replaces the matched trigger range in [text] with [replacement]. */
    fun splice(text: String, match: Match, replacement: String): String =
        text.substring(0, match.start) + replacement + text.substring(match.endExclusive)

    /**
     * Parses a quick-save command out of [text]. [pattern] must contain exactly
     * two '%' placeholders (trigger, then content); everything else is matched
     * literally, so metacharacters like the parentheses and dot in the default
     * "(.save:%:%)" are safe. Returns null for a malformed pattern or when no
     * command is present.
     */
    fun findSaveCommand(text: String, pattern: String): SaveCommand? {
        val parts = pattern.split("%")
        if (parts.size != 3 || pattern.length < 5) return null
        val regex = Pattern.compile(
            Pattern.quote(parts[0]) + "(.+?)" + Pattern.quote(parts[1]) + "(.+?)" + Pattern.quote(parts[2])
        )
        val m = regex.matcher(text)
        if (!m.find()) return null
        val fullMatch = m.group(0) ?: return null
        val trigger = m.group(1)?.trim() ?: return null
        val content = m.group(2)?.trim() ?: return null
        if (trigger.isEmpty() || content.isEmpty()) return null
        return SaveCommand(fullMatch, trigger, content)
    }
}
