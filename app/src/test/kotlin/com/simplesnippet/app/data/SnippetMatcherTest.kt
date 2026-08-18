package com.simplesnippet.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SnippetMatcherTest {

    // --- matching ---

    @Test
    fun `basic match finds the shortcut at end and splices the replacement`() {
        val snippets = mutableListOf(Snippet("..email", mutableListOf("user@example.com")))
        val text = "hello ..email"

        val match = SnippetMatcher.find(text, snippets, allowAnywhere = false)

        assertNotNull(match)
        val result = SnippetMatcher.splice(text, match!!, match.variations[0])
        assertEquals("hello user@example.com", result)
    }

    @Test
    fun `longest shortcut wins when a shorter one is a prefix of a longer one`() {
        val snippets = mutableListOf(
            Snippet("..sig", mutableListOf("S")),
            Snippet("..signature", mutableListOf("SIG"))
        )
        val text = "..signature"

        val match = SnippetMatcher.find(text, snippets, allowAnywhere = false)

        assertNotNull(match)
        assertEquals("..signature", match!!.snippet.trigger)
        assertEquals("SIG", SnippetMatcher.splice(text, match, match.variations[0]))
    }

    @Test
    fun `end-of-text rule requires the shortcut at the end unless allowAnywhere is set`() {
        val snippets = mutableListOf(Snippet("..email", mutableListOf("user@example.com")))
        val text = "..email and more"

        assertNull(SnippetMatcher.find(text, snippets, allowAnywhere = false))

        val anywhere = SnippetMatcher.find(text, snippets, allowAnywhere = true)
        assertNotNull(anywhere)
        assertEquals(
            "user@example.com and more",
            SnippetMatcher.splice(text, anywhere!!, anywhere.variations[0])
        )
    }

    @Test
    fun `no match when the shortcut is not present in the text`() {
        val snippets = mutableListOf(Snippet("..email", mutableListOf("user@example.com")))
        assertNull(SnippetMatcher.find("email", snippets, allowAnywhere = false))
    }

    @Test
    fun `shortcut characters are matched literally, not as a regex`() {
        val snippets = mutableListOf(Snippet("..email", mutableListOf("user@example.com")))
        // The dots in "..email" must not behave as regex wildcards.
        assertNull(SnippetMatcher.find("xxemail", snippets, allowAnywhere = false))
    }

    @Test
    fun `snippet with empty contents is skipped without aborting the search`() {
        val snippets = mutableListOf(
            Snippet("..empty", mutableListOf()),
            Snippet("..email", mutableListOf("user@example.com"))
        )

        val matchEmail = SnippetMatcher.find("..email", snippets, allowAnywhere = false)
        assertNotNull(matchEmail)
        assertEquals("..email", matchEmail!!.snippet.trigger)

        assertNull(SnippetMatcher.find("..empty", snippets, allowAnywhere = false))
    }

    @Test
    fun `multi-variation snippet returns variations in order with empties filtered`() {
        val snippets = mutableListOf(Snippet("..multi", mutableListOf("a", "", "b", "c")))

        val match = SnippetMatcher.find("..multi", snippets, allowAnywhere = false)

        assertNotNull(match)
        assertEquals(listOf("a", "b", "c"), match!!.variations)
    }

    // --- boundary rule ---

    @Test
    fun `alphanumeric-start shortcut does not expand in the middle of a word`() {
        val snippets = mutableListOf(Snippet("omw", mutableListOf("On my way!")))
        // "shomw" ends with "omw" but the preceding char is alphanumeric.
        assertNull(SnippetMatcher.find("shomw", snippets, allowAnywhere = false))
    }

    @Test
    fun `alphanumeric-start shortcut expands at start-of-text, after a space, and after punctuation`() {
        val snippets = mutableListOf(Snippet("omw", mutableListOf("On my way!")))

        assertNotNull(SnippetMatcher.find("omw", snippets, allowAnywhere = false))
        assertNotNull(SnippetMatcher.find("hey omw", snippets, allowAnywhere = false))
        assertNotNull(SnippetMatcher.find("hey,omw", snippets, allowAnywhere = false))
    }

    @Test
    fun `digit-start shortcut is boundary-checked exactly like a letter-start one`() {
        val snippets = mutableListOf(Snippet("2day", mutableListOf("today")))

        assertNull(SnippetMatcher.find("v2day", snippets, allowAnywhere = false))
        assertNotNull(SnippetMatcher.find("see 2day", snippets, allowAnywhere = false))
    }

    @Test
    fun `symbol-start shortcut still expands directly after a word`() {
        // This is what preserves today's behaviour for migrated snippets.
        val snippets = mutableListOf(Snippet("..email", mutableListOf("user@example.com")))

        val match = SnippetMatcher.find("hello..email", snippets, allowAnywhere = false)

        assertNotNull(match)
        assertEquals(5, match!!.start)
    }

    @Test
    fun `allowAnywhere falls back to an earlier boundary-valid occurrence`() {
        val snippets = mutableListOf(Snippet("omw", mutableListOf("On my way!")))
        val text = "omw then shomw"

        // The last occurrence (inside "shomw") fails the boundary rule.
        assertNull(SnippetMatcher.find(text, snippets, allowAnywhere = false))

        val anywhere = SnippetMatcher.find(text, snippets, allowAnywhere = true)
        assertNotNull(anywhere)
        assertEquals(0, anywhere!!.start)
    }

    // --- quick-save parsing ---

    @Test
    fun `findSaveCommand parses shortcut and content from the default pattern`() {
        val save = SnippetMatcher.findSaveCommand("note (.save:ph:+15550199) end", "(.save:%:%)")

        assertNotNull(save)
        assertEquals("ph", save!!.trigger)
        assertEquals("+15550199", save.content)
        assertEquals("(.save:ph:+15550199)", save.fullMatch)
    }

    @Test
    fun `findSaveCommand returns null for a malformed pattern or blank fields`() {
        assertNull(SnippetMatcher.findSaveCommand("note (.save:ph:+15550199) end", "(.save:%)"))
        assertNull(SnippetMatcher.findSaveCommand("note (.save: :+15550199) end", "(.save:%:%)"))
        assertNull(SnippetMatcher.findSaveCommand("note (.save:ph: ) end", "(.save:%:%)"))
    }

    // --- validators ---

    @Test
    fun `isValidSavePattern accepts two placeholders wrapped in non-blank literals`() {
        assertTrue(SnippetMatcher.isValidSavePattern("(.save:%:%)"))
        assertTrue(SnippetMatcher.isValidSavePattern("<%|%>"))
    }

    @Test
    fun `isValidSavePattern rejects a blank leading, middle, or trailing literal`() {
        assertFalse(SnippetMatcher.isValidSavePattern("%:%)"))         // no leading literal
        assertFalse(SnippetMatcher.isValidSavePattern("(.save:%%)"))   // no middle literal
        assertFalse(SnippetMatcher.isValidSavePattern("(.save:%:%"))   // no trailing literal
        assertFalse(SnippetMatcher.isValidSavePattern("(.save:% %)"))  // whitespace-only middle
    }

    @Test
    fun `isValidSavePattern rejects the wrong number of placeholders`() {
        assertFalse(SnippetMatcher.isValidSavePattern("(.save:%)"))
        assertFalse(SnippetMatcher.isValidSavePattern("(.save:%:%:%)"))
        assertFalse(SnippetMatcher.isValidSavePattern("(.save:name:content)"))
    }

    @Test
    fun `isValidSavePattern rejects patterns too short to hold three literals`() {
        // Any pattern under 5 chars necessarily has a blank literal, so the
        // explicit length floor is a restatement — assert it holds regardless.
        assertFalse(SnippetMatcher.isValidSavePattern("%%"))
        assertFalse(SnippetMatcher.isValidSavePattern("a%%"))
        assertFalse(SnippetMatcher.isValidSavePattern("%a%"))
    }

    @Test
    fun `isValidShortcut accepts free-form shortcuts without whitespace`() {
        assertTrue(SnippetMatcher.isValidShortcut("..email"))
        assertTrue(SnippetMatcher.isValidShortcut(";sig"))
        assertTrue(SnippetMatcher.isValidShortcut("omw"))
    }

    @Test
    fun `isValidShortcut rejects blank shortcuts and any whitespace`() {
        assertFalse(SnippetMatcher.isValidShortcut(""))
        assertFalse(SnippetMatcher.isValidShortcut("   "))
        assertFalse(SnippetMatcher.isValidShortcut("my name"))
        assertFalse(SnippetMatcher.isValidShortcut("tab\there"))
    }

    @Test
    fun `quick-save gate rejects a parsed shortcut that contains whitespace`() {
        // Mirrors the service exactly: findSaveCommand parses, isValidShortcut gates.
        val spaced = SnippetMatcher.findSaveCommand("(.save:my name:+15550199)", "(.save:%:%)")
        assertNotNull(spaced)
        assertEquals("my name", spaced!!.trigger)
        assertFalse(SnippetMatcher.isValidShortcut(spaced.trigger))

        val clean = SnippetMatcher.findSaveCommand("(.save:ph:+15550199)", "(.save:%:%)")
        assertNotNull(clean)
        assertEquals("ph", clean!!.trigger)
        assertTrue(SnippetMatcher.isValidShortcut(clean.trigger))
    }
}
