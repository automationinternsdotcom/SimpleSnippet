package com.simplesnippet.app.data

import java.util.regex.Pattern

/**
 * Pure text-matching logic for snippet expansion and quick-save.
 * No Android dependencies, so the whole thing is unit-testable on the JVM.
 */
object SnippetMatcher {

    /** A snippet shortcut found in text: replace the range [start, endExclusive). */
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
     * Finds the best snippet match in [text]. Each snippet's `trigger` is the
     * complete free-form shortcut ("..email", ";sig", "omw") — there is no
     * global prefix. Longer shortcuts win over shorter ones ("..signature"
     * resolves to the "..signature" snippet even when "..sig" also exists).
     * Snippets with no non-empty variation are skipped without aborting the
     * search. Unless [allowAnywhere], the shortcut must sit at the very end of
     * the text.
     */
    fun find(
        text: String,
        snippets: List<Snippet>,
        allowAnywhere: Boolean
    ): Match? {
        for (s in snippets.sortedByDescending { it.trigger.length }) {
            if (s.trigger.isEmpty()) continue
            val idx = findOccurrence(text, s.trigger, allowAnywhere)
            if (idx == -1) continue
            val variations = s.contents.filter { it.isNotEmpty() }
            if (variations.isEmpty()) continue
            return Match(s, idx, idx + s.trigger.length, variations)
        }
        return null
    }

    /**
     * Index of the latest acceptable occurrence of [shortcut] in [text], or -1.
     *
     * Boundary rule: a shortcut that starts with an alphanumeric character
     * ("omw") must not fire mid-word — the character immediately before it has
     * to be non-alphanumeric or start-of-text, or "shomw" would expand.
     * Symbol-led shortcuts ("..email", ";sig") skip the check entirely, which
     * is exactly what keeps "hello..email" expanding for migrated snippets.
     */
    private fun findOccurrence(text: String, shortcut: String, allowAnywhere: Boolean): Int {
        val needsBoundary = shortcut[0].isLetterOrDigit()
        var idx = text.lastIndexOf(shortcut)
        while (idx != -1) {
            val isAtEnd = idx + shortcut.length == text.length
            if (allowAnywhere || isAtEnd) {
                if (!needsBoundary || idx == 0 || !text[idx - 1].isLetterOrDigit()) return idx
            }
            // Only the final occurrence can ever sit at end-of-text, so with the
            // end anchor on there is nothing earlier worth trying.
            if (!allowAnywhere) return -1
            // lastIndexOf returns -1 for a negative fromIndex, ending the loop.
            idx = text.lastIndexOf(shortcut, idx - 1)
        }
        return -1
    }

    /** Replaces the matched shortcut range in [text] with [replacement]. */
    fun splice(text: String, match: Match, replacement: String): String =
        text.substring(0, match.start) + replacement + text.substring(match.endExclusive)

    /**
     * A snippet shortcut is usable when it is non-blank and free of whitespace.
     * Shared by the snippet dialog and the service's quick-save gate so a
     * quick-save cannot create something the dialog would have rejected.
     */
    fun isValidShortcut(shortcut: String): Boolean =
        shortcut.isNotBlank() && shortcut.none { it.isWhitespace() }

    /**
     * A quick-save pattern is usable when it holds exactly two '%' placeholders
     * with three non-blank literal segments around them, and is at least five
     * characters long. Three non-blank literals plus two '%' already imply that
     * length; the floor is kept as an explicit restatement of the rule.
     *
     * Shared by the Settings editor and [findSaveCommand], so an unusable
     * pattern can never reach the regex builder.
     */
    fun isValidSavePattern(pattern: String): Boolean {
        val parts = pattern.split("%")
        return parts.size == 3 && parts.all { it.isNotBlank() } && pattern.length >= 5
    }

    /**
     * Parses a quick-save command out of [text]. [pattern] must satisfy
     * [isValidSavePattern]; everything outside the two '%' placeholders is
     * matched literally, so metacharacters like the parentheses and dot in the
     * default "(.save:%:%)" are safe. Returns null for an unusable pattern or
     * when no command is present.
     */
    fun findSaveCommand(text: String, pattern: String): SaveCommand? {
        if (!isValidSavePattern(pattern)) return null
        val parts = pattern.split("%")
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
