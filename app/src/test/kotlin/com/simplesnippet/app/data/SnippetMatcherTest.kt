package com.simplesnippet.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SnippetMatcherTest {

    @Test
    fun `basic match finds trigger at end and splices replacement`() {
        val snippets = mutableListOf(Snippet("email", mutableListOf("user@example.com")))
        val text = "hello ..email"

        val match = SnippetMatcher.find(text, "..", snippets, allowAnywhere = false)

        assertNotNull(match)
        val result = SnippetMatcher.splice(text, match!!, match.variations[0])
        assertEquals("hello user@example.com", result)
    }

    @Test
    fun `longest trigger wins when a shorter trigger is a prefix of a longer one`() {
        val snippets = mutableListOf(
            Snippet("sig", mutableListOf("S")),
            Snippet("signature", mutableListOf("SIG"))
        )
        val text = "..signature"

        val match = SnippetMatcher.find(text, "..", snippets, allowAnywhere = false)

        assertNotNull(match)
        assertEquals("signature", match!!.snippet.trigger)
        val result = SnippetMatcher.splice(text, match, match.variations[0])
        assertEquals("SIG", result)
    }

    @Test
    fun `end-of-text rule requires trigger at end unless allowAnywhere is set`() {
        val snippets = mutableListOf(Snippet("email", mutableListOf("user@example.com")))
        val text = "..email and more"

        val strict = SnippetMatcher.find(text, "..", snippets, allowAnywhere = false)
        assertNull(strict)

        val anywhere = SnippetMatcher.find(text, "..", snippets, allowAnywhere = true)
        assertNotNull(anywhere)
        val result = SnippetMatcher.splice(text, anywhere!!, anywhere.variations[0])
        assertEquals("user@example.com and more", result)
    }

    @Test
    fun `no match when prefix is not present in text`() {
        val snippets = mutableListOf(Snippet("email", mutableListOf("user@example.com")))
        val match = SnippetMatcher.find("email", "..", snippets, allowAnywhere = false)
        assertNull(match)
    }

    @Test
    fun `snippet with empty contents is skipped without aborting the search`() {
        val snippets = mutableListOf(
            Snippet("empty", mutableListOf()),
            Snippet("email", mutableListOf("user@example.com"))
        )

        val matchEmail = SnippetMatcher.find("..email", "..", snippets, allowAnywhere = false)
        assertNotNull(matchEmail)
        assertEquals("email", matchEmail!!.snippet.trigger)

        val matchEmpty = SnippetMatcher.find("..empty", "..", snippets, allowAnywhere = false)
        assertNull(matchEmpty)
    }

    @Test
    fun `multi-variation snippet returns variations in order with empties filtered`() {
        val snippets = mutableListOf(Snippet("multi", mutableListOf("a", "", "b", "c")))

        val match = SnippetMatcher.find("..multi", "..", snippets, allowAnywhere = false)

        assertNotNull(match)
        assertEquals(listOf("a", "b", "c"), match!!.variations)
    }

    @Test
    fun `findSaveCommand parses trigger and content from default pattern`() {
        val save = SnippetMatcher.findSaveCommand("note (.save:ph:+15550199) end", "(.save:%:%)")

        assertNotNull(save)
        assertEquals("ph", save!!.trigger)
        assertEquals("+15550199", save.content)
        assertEquals("(.save:ph:+15550199)", save.fullMatch)
    }

    @Test
    fun `findSaveCommand returns null for malformed pattern or blank fields`() {
        val malformedPattern = SnippetMatcher.findSaveCommand("note (.save:ph:+15550199) end", "(.save:%)")
        assertNull(malformedPattern)

        val blankTrigger = SnippetMatcher.findSaveCommand("note (.save: :+15550199) end", "(.save:%:%)")
        assertNull(blankTrigger)

        val blankContent = SnippetMatcher.findSaveCommand("note (.save:ph: ) end", "(.save:%:%)")
        assertNull(blankContent)
    }

    @Test
    fun `custom prefixes match and prefix characters are treated literally not as regex`() {
        val snippets = mutableListOf(Snippet("email", mutableListOf("user@example.com")))

        val slashMatch = SnippetMatcher.find("//email", "//", snippets, allowAnywhere = false)
        assertNotNull(slashMatch)

        val hashMatch = SnippetMatcher.find("ta#email", "ta#", snippets, allowAnywhere = false)
        assertNotNull(hashMatch)

        // "." in the ".." prefix must not act as a regex wildcard: "xx" should not match it.
        val literalDotMatch = SnippetMatcher.find("xxemail", "..", snippets, allowAnywhere = false)
        assertNull(literalDotMatch)
    }
}
