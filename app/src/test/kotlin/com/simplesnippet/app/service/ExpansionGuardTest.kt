package com.simplesnippet.app.service

import com.simplesnippet.app.data.Snippet
import com.simplesnippet.app.data.SnippetMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpansionGuardTest {

    @Test
    fun `echo of a service write is suppressed exactly once`() {
        val guard = ExpansionGuard()
        guard.expectServiceWrite("hello world")

        assertTrue(guard.consumeIfServiceWrite("hello world"))
        // The same text typed again later is real user input.
        assertFalse(guard.consumeIfServiceWrite("hello world"))
    }

    @Test
    fun `unrelated event clears the expectation instead of suppressing later input`() {
        val guard = ExpansionGuard()
        guard.expectServiceWrite("expanded")

        assertFalse(guard.consumeIfServiceWrite("something else"))
        // A stale expectation must not swallow a later coincidental match.
        assertFalse(guard.consumeIfServiceWrite("expanded"))
    }

    @Test
    fun `nothing is suppressed when no write is pending`() {
        val guard = ExpansionGuard()
        assertFalse(guard.consumeIfServiceWrite("typed by user"))
    }

    @Test
    fun `self-referential snippet expands once and stops`() {
        // ..loop -> "..loop again": the spliced text still contains the
        // trigger, so without the guard the echo event would re-match forever.
        val snippets = mutableListOf(Snippet("loop", mutableListOf("..loop again")))
        val guard = ExpansionGuard()

        val text = "start ..loop"
        val match = SnippetMatcher.find(text, "..", snippets, allowAnywhere = true)!!
        val written = SnippetMatcher.splice(text, match, match.variations[0])
        guard.expectServiceWrite(written)
        assertEquals("start ..loop again", written)

        // The echo of our own write is dropped before matching runs.
        assertTrue(guard.consumeIfServiceWrite(written))
    }

    @Test
    fun `cyclic snippets stop after the first service-authored expansion`() {
        // a -> ..b and b -> ..a: each expansion's echo is suppressed, so the
        // chain never advances on service-authored text.
        val snippets = mutableListOf(
            Snippet("a", mutableListOf("..b")),
            Snippet("b", mutableListOf("..a"))
        )
        val guard = ExpansionGuard()

        val match = SnippetMatcher.find("..a", "..", snippets, allowAnywhere = false)!!
        val written = SnippetMatcher.splice("..a", match, match.variations[0])
        assertEquals("..b", written)
        guard.expectServiceWrite(written)

        val suppressed = guard.consumeIfServiceWrite(written)
        assertTrue(suppressed)
        // Mirrors the service: a suppressed event never reaches the matcher.
        val nextMatch = if (suppressed) null else SnippetMatcher.find(written, "..", snippets, false)
        assertNull(nextMatch)
    }
}
