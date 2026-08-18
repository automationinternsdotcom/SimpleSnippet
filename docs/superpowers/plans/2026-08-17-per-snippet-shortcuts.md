# Per-Snippet Free-Form Shortcuts + Editable Quick-Save Pattern Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the global trigger-prefix with a free-form full shortcut stored on every snippet, and make the in-text quick-save pattern user-editable with real validation.

**Architecture:** All matching and validation logic lives in the existing pure-Kotlin `SnippetMatcher` object (JVM-unit-testable, no Android deps). `AppConfig.normalized()` — the app's single migration hook, called by both the service and the UI — folds the old global prefix into each snippet's trigger and stamps a new `configVersion`. The migrated blob is persisted exactly once, from the UI's `loadConfig()`; the accessibility service's load path stays strictly read-only. UI screens (Settings, Snippets dialog, Test Lab) are re-pointed at the new shape.

**Tech Stack:** Kotlin 2.1, Jetpack Compose (Material 3), Android `AccessibilityService`, Gson, JUnit 4, Gradle (AGP).

## Global Constraints

- The JSON key and Kotlin field name stay `trigger` on `Snippet` (Gson compatibility). All **UI copy** says "shortcut", never "trigger" or "prefix".
- `AppConfig.snippetTriggerPrefix` is **removed from all logic but kept as a deprecated field** so old blobs still parse and a stored custom prefix stays readable for migration.
- `AppConfig.configVersion: Int` has constructor default `2`. Old blobs deserialize with `0` because Gson bypasses Kotlin constructors.
- Migration ordering is load-bearing: `normalized()` must read `snippetTriggerPrefix` **before** the existing null/blank patching rewrites it.
- Migration persistence: one-shot write-back from the UI's `loadConfig()` in `SimpleSnippetApp.kt` when `configVersion` changed during normalization. The service's load path must remain read-only (a write there re-enters its own prefs listener, cancelling debounces and tearing down an open picker).
- Boundary rule: if a shortcut starts with an alphanumeric character, the character immediately before the matched occurrence must be non-alphanumeric or start-of-text. Shortcuts starting with a symbol get no boundary check.
- `allowTriggerAnywhere` and `triggerDebounceMs` stay global and unchanged.
- Quick-save pattern validity: exactly two `%` placeholders, **all three** literal segments non-blank, total length ≥ 5.
- Shortcut validity: non-blank, no whitespace, trimmed on save.
- Legacy tolerance: a pre-existing shortcut that violates the new validation may be re-saved **unchanged**; validation applies only when the shortcut text is modified.
- Quick-save creates the snippet with the name exactly as typed — no prefix prepended.
- Final versioning: `versionCode 2`, `versionName "1.1.0"`.
- Out of scope: per-snippet `allowTriggerAnywhere`, bulk-edit of migrated shortcuts, export/import.

**Build/verify commands (this repo, run from `/Users/openclaw/Code/SimpleSnippet`):**
- Unit tests: `./gradlew testDebugUnitTest`
- Compile check: `./gradlew assembleDebug`

There is no UI test infrastructure beyond one instrumentation smoke test, so UI/service tasks are verified by `assembleDebug` plus the unit suite. Every such task still ships exact code.

**Commit style:** matches repo history — `feat:`, `fix:`, `refactor:`, `docs:`, `chore:`, `test:`. One commit per task.

---

## File Structure

**Modified — data (pure Kotlin, unit-tested):**
- `app/src/main/kotlin/com/simplesnippet/app/data/SnippetMatcher.kt` — matching + all shared validators. New `find` signature, boundary rule, `isValidShortcut`, `isValidSavePattern`.
- `app/src/main/kotlin/com/simplesnippet/app/data/AppConfig.kt` — schema version constant, deprecated prefix field, v1→v2 migration inside `normalized()`, migrated default snippets.

**Modified — service:**
- `app/src/main/kotlin/com/simplesnippet/app/service/SnippetAccessibilityService.kt` — `find` call sites lose the prefix argument; quick-save gains a shortcut-validation gate.

**Modified — UI:**
- `app/src/main/kotlin/com/simplesnippet/app/ui/SimpleSnippetApp.kt` — one-shot migration write-back in `loadConfig()`; passes `config` to `TestScreen`.
- `app/src/main/kotlin/com/simplesnippet/app/ui/screens/SnippetsScreen.kt` — free-form shortcut field with validation, disabled save button, self-expansion warning, usage-card copy.
- `app/src/main/kotlin/com/simplesnippet/app/ui/screens/SettingsScreen.kt` — prefix editor removed, quick-save pattern editor added.
- `app/src/main/kotlin/com/simplesnippet/app/ui/screens/TestScreen.kt` — takes `AppConfig`, derives presets from it.
- `app/src/main/kotlin/com/simplesnippet/app/ui/components/TypingAnimationPreview.kt` — onboarding copy.

**Modified — resources / docs / build:**
- `app/src/main/res/values/strings.xml` — accessibility service description.
- `README.md` — feature and usage copy; the "prefix is configurable / quick-save is fixed" line inverts.
- `app/build.gradle` — `versionCode`, `versionName`.

**Tests:**
- Modified: `app/src/test/kotlin/com/simplesnippet/app/data/SnippetMatcherTest.kt`
- Modified: `app/src/test/kotlin/com/simplesnippet/app/service/ExpansionGuardTest.kt`
- Create: `app/src/test/kotlin/com/simplesnippet/app/data/AppConfigMigrationTest.kt`

**Deliberately untouched:** `app/proguard-rules.pro` already contains `-keep class com.simplesnippet.app.data.** { *; }`, so the new `configVersion` field survives R8 in release builds. No rule change needed.

---

## Task Ordering Rationale

Task 1 changes the `SnippetMatcher.find` signature, which the accessibility service calls in two places. To keep `assembleDebug` green at every commit, Task 1 also performs the **mechanical** call-site edit in the service (dropping the prefix argument and nothing else). The behavioural service change (quick-save validation) is Task 3.

**On line numbers:** every line number in this plan refers to the file as it stands *before* that task begins. Earlier steps within a task shift them. Always match on the quoted "replace this" text, never on the number alone.

---

### Task 1: SnippetMatcher — free-form shortcuts, boundary rule, shared validators

**Files:**
- Modify: `app/src/main/kotlin/com/simplesnippet/app/data/SnippetMatcher.kt`
- Modify (mechanical, keeps the build green): `app/src/main/kotlin/com/simplesnippet/app/service/SnippetAccessibilityService.kt:191-196` and `:253-258`
- Test: `app/src/test/kotlin/com/simplesnippet/app/data/SnippetMatcherTest.kt` (rewritten)
- Test: `app/src/test/kotlin/com/simplesnippet/app/service/ExpansionGuardTest.kt` (three `find` call sites updated)

**Interfaces:**
- Consumes: existing `Snippet(var trigger: String, var contents: MutableList<String>)` from `com.simplesnippet.app.data`.
- Produces:
  - `SnippetMatcher.find(text: String, snippets: List<Snippet>, allowAnywhere: Boolean): SnippetMatcher.Match?` — the `prefix: String` parameter is gone.
  - `SnippetMatcher.Match(snippet: Snippet, start: Int, endExclusive: Int, variations: List<String>)` — unchanged.
  - `SnippetMatcher.splice(text: String, match: Match, replacement: String): String` — unchanged.
  - `SnippetMatcher.findSaveCommand(text: String, pattern: String): SnippetMatcher.SaveCommand?` — unchanged signature; now early-returns via `isValidSavePattern`.
  - `SnippetMatcher.SaveCommand(fullMatch: String, trigger: String, content: String)` — unchanged.
  - `SnippetMatcher.isValidShortcut(shortcut: String): Boolean`
  - `SnippetMatcher.isValidSavePattern(pattern: String): Boolean`

- [ ] **Step 1: Write the failing tests**

Replace the entire contents of `app/src/test/kotlin/com/simplesnippet/app/data/SnippetMatcherTest.kt` with:

```kotlin
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
}
```

- [ ] **Step 2: Update the three `find` call sites in `ExpansionGuardTest.kt`**

These are in the same module and would otherwise fail to compile, hiding the real failures.

In `app/src/test/kotlin/com/simplesnippet/app/service/ExpansionGuardTest.kt`, replace the body of `self-referential snippet expands once and stops` (currently lines 40-54) with:

```kotlin
    @Test
    fun `self-referential snippet expands once and stops`() {
        // ..loop -> "..loop again": the spliced text still contains the
        // shortcut, so without the guard the echo event would re-match forever.
        val snippets = mutableListOf(Snippet("..loop", mutableListOf("..loop again")))
        val guard = ExpansionGuard()

        val text = "start ..loop"
        val match = SnippetMatcher.find(text, snippets, allowAnywhere = true)!!
        val written = SnippetMatcher.splice(text, match, match.variations[0])
        guard.expectServiceWrite(written)
        assertEquals("start ..loop again", written)

        // The echo of our own write is dropped before matching runs.
        assertTrue(guard.consumeIfServiceWrite(written))
    }
```

and replace the body of `cyclic snippets stop after the first service-authored expansion` (currently lines 56-76) with:

```kotlin
    @Test
    fun `cyclic snippets stop after the first service-authored expansion`() {
        // ..a -> ..b and ..b -> ..a: each expansion's echo is suppressed, so the
        // chain never advances on service-authored text.
        val snippets = mutableListOf(
            Snippet("..a", mutableListOf("..b")),
            Snippet("..b", mutableListOf("..a"))
        )
        val guard = ExpansionGuard()

        val match = SnippetMatcher.find("..a", snippets, allowAnywhere = false)!!
        val written = SnippetMatcher.splice("..a", match, match.variations[0])
        assertEquals("..b", written)
        guard.expectServiceWrite(written)

        val suppressed = guard.consumeIfServiceWrite(written)
        assertTrue(suppressed)
        // Mirrors the service: a suppressed event never reaches the matcher.
        val nextMatch = if (suppressed) null else SnippetMatcher.find(written, snippets, false)
        assertNull(nextMatch)
    }
```

- [ ] **Step 3: Run the tests to verify they fail**

Run: `./gradlew testDebugUnitTest`

Expected: FAIL at compilation with errors like `Too many arguments for public final fun find(...)` / `Unresolved reference: isValidShortcut` / `Unresolved reference: isValidSavePattern`.

- [ ] **Step 4: Write the implementation**

Replace the entire contents of `app/src/main/kotlin/com/simplesnippet/app/data/SnippetMatcher.kt` with:

```kotlin
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
```

- [ ] **Step 5: Fix the two service call sites (mechanical — no behaviour change)**

In `app/src/main/kotlin/com/simplesnippet/app/service/SnippetAccessibilityService.kt`, replace lines 191-196:

```kotlin
            val match = SnippetMatcher.find(
                currentText,
                cfg.snippetTriggerPrefix,
                cfg.snippets,
                cfg.allowTriggerAnywhere
            )
```

with:

```kotlin
            val match = SnippetMatcher.find(
                currentText,
                cfg.snippets,
                cfg.allowTriggerAnywhere
            )
```

and replace lines 253-258:

```kotlin
            val fresh = SnippetMatcher.find(
                freshText,
                cfg.snippetTriggerPrefix,
                listOf(liveSnippet),
                cfg.allowTriggerAnywhere
            ) ?: return
```

with:

```kotlin
            val fresh = SnippetMatcher.find(
                freshText,
                listOf(liveSnippet),
                cfg.allowTriggerAnywhere
            ) ?: return
```

Also update the KDoc on `replaceTrigger` (line 228) so it stays accurate — replace:

```kotlin
     * Re-running SnippetMatcher.find on the fresh text (restricted to the one
     * matched snippet) re-applies the end-of-text anchor and keeps production
     * on the same find/splice code the unit tests exercise.
```

with:

```kotlin
     * Re-running SnippetMatcher.find on the fresh text (restricted to the one
     * live snippet, whose trigger is now the whole shortcut) re-applies the
     * end-of-text anchor and the boundary rule, and keeps production on the
     * same find/splice code the unit tests exercise. The stale-config
     * invalidation below is unchanged.
```

- [ ] **Step 6: Run the tests to verify they pass**

Run: `./gradlew testDebugUnitTest`

Expected: PASS — `BUILD SUCCESSFUL`, all tests in `SnippetMatcherTest` and `ExpansionGuardTest` green.

- [ ] **Step 7: Verify the app still compiles**

Run: `./gradlew assembleDebug`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/kotlin/com/simplesnippet/app/data/SnippetMatcher.kt \
        app/src/main/kotlin/com/simplesnippet/app/service/SnippetAccessibilityService.kt \
        app/src/test/kotlin/com/simplesnippet/app/data/SnippetMatcherTest.kt \
        app/src/test/kotlin/com/simplesnippet/app/service/ExpansionGuardTest.kt
git commit -m "feat: match free-form per-snippet shortcuts with a word-boundary rule"
```

---

### Task 2: AppConfig schema version, v1→v2 migration, and one-shot persistence

**Files:**
- Modify: `app/src/main/kotlin/com/simplesnippet/app/data/AppConfig.kt` (whole file)
- Modify: `app/src/main/kotlin/com/simplesnippet/app/ui/SimpleSnippetApp.kt:36-43` (the `loadConfig` helper) and the import block
- Test: `app/src/test/kotlin/com/simplesnippet/app/data/AppConfigMigrationTest.kt` (new)

**Interfaces:**
- Consumes: nothing from Task 1 (independent), but must not disturb `SnippetMatcher.find`'s expectation that `Snippet.trigger` is a complete shortcut.
- Produces:
  - `const val CURRENT_CONFIG_VERSION = 2` (top-level in package `com.simplesnippet.app.data`)
  - `const val DEFAULT_TRIGGER_PREFIX = ".."` (top-level, same package)
  - `const val DEFAULT_SAVE_PATTERN = "(.save:%:%)"` (top-level, same package)
  - `AppConfig(isAppEnabled: Boolean, snippets: MutableList<Snippet>, snippetTriggerPrefix: String, saveSnippetPattern: String, triggerDebounceMs: Long, allowTriggerAnywhere: Boolean, configVersion: Int)` — `configVersion` is the **last** parameter, defaulting to `CURRENT_CONFIG_VERSION`; `snippetTriggerPrefix` is `@Deprecated` and must not be read outside `normalized()`.
  - `fun AppConfig?.normalized(): AppConfig` — unchanged signature; now also migrates and stamps `configVersion`. It **mutates and returns the same instance**, so callers wanting the pre-migration version must read it before calling.
  - `fun createDefaultConfig(): AppConfig` — unchanged signature; default snippets are now `..email` and `..sign`.

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/kotlin/com/simplesnippet/app/data/AppConfigMigrationTest.kt`:

```kotlin
package com.simplesnippet.app.data

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Migration has to be exercised through raw JSON: a constructor-built AppConfig
 * is born at CURRENT_CONFIG_VERSION and would test nothing. Gson bypasses Kotlin
 * constructors, which is exactly how a v1 blob ends up with configVersion 0.
 */
class AppConfigMigrationTest {

    private val gson = Gson()

    private fun parse(json: String): AppConfig =
        gson.fromJson(json, AppConfig::class.java)

    private val v1CustomPrefix = """
        {"isAppEnabled":true,
         "snippets":[{"trigger":"email","contents":["user@example.com"]},
                     {"trigger":"sign","contents":["Best regards"]}],
         "snippetTriggerPrefix":"//",
         "saveSnippetPattern":"(.save:%:%)",
         "triggerDebounceMs":400,
         "allowTriggerAnywhere":false}
    """.trimIndent()

    private val v1DefaultPrefix = """
        {"snippets":[{"trigger":"email","contents":["user@example.com"]}],
         "snippetTriggerPrefix":".."}
    """.trimIndent()

    private val v1NoPrefixField = """
        {"snippets":[{"trigger":"email","contents":["user@example.com"]}]}
    """.trimIndent()

    private val v1BlankPrefix = """
        {"snippets":[{"trigger":"email","contents":["user@example.com"]}],
         "snippetTriggerPrefix":""}
    """.trimIndent()

    private val v2Migrated = """
        {"snippets":[{"trigger":"..email","contents":["user@example.com"]}],
         "snippetTriggerPrefix":"..",
         "configVersion":2}
    """.trimIndent()

    @Test
    fun `a v1 blob reports version 0 before normalization and the current version after`() {
        val raw = parse(v1DefaultPrefix)
        assertEquals(0, raw.configVersion)

        val migrated = raw.normalized()

        assertEquals(CURRENT_CONFIG_VERSION, migrated.configVersion)
    }

    @Test
    fun `a v1 blob with a custom prefix folds that prefix into every shortcut`() {
        val migrated = parse(v1CustomPrefix).normalized()

        assertEquals(
            listOf("//email", "//sign"),
            migrated.snippets.map { it.trigger }
        )
    }

    @Test
    fun `a v1 blob with the default prefix folds two dots into every shortcut`() {
        val migrated = parse(v1DefaultPrefix).normalized()

        assertEquals(listOf("..email"), migrated.snippets.map { it.trigger })
    }

    @Test
    fun `a v1 blob missing snippetTriggerPrefix entirely falls back to two dots`() {
        val migrated = parse(v1NoPrefixField).normalized()

        assertEquals(listOf("..email"), migrated.snippets.map { it.trigger })
    }

    @Test
    fun `a v1 blob with a blank prefix falls back to two dots`() {
        val migrated = parse(v1BlankPrefix).normalized()

        assertEquals(listOf("..email"), migrated.snippets.map { it.trigger })
    }

    @Test
    fun `an already-migrated blob is left alone`() {
        val migrated = parse(v2Migrated).normalized()

        assertEquals(listOf("..email"), migrated.snippets.map { it.trigger })
        assertEquals(CURRENT_CONFIG_VERSION, migrated.configVersion)
    }

    @Test
    fun `normalizing twice never double-prefixes`() {
        val once = parse(v1CustomPrefix).normalized()
        val twice = once.normalized()

        assertEquals(listOf("//email", "//sign"), twice.snippets.map { it.trigger })
        assertEquals(CURRENT_CONFIG_VERSION, twice.configVersion)
    }

    @Test
    fun `blank-trigger snippets are dropped before migration instead of being prefixed`() {
        val blob = """
            {"snippets":[{"trigger":"","contents":["x"]},
                         {"trigger":"email","contents":["user@example.com"]}]}
        """.trimIndent()

        val migrated = parse(blob).normalized()

        assertEquals(listOf("..email"), migrated.snippets.map { it.trigger })
    }

    @Test
    fun `the default config is born current and its shortcuts carry the delimiter`() {
        val defaults = createDefaultConfig()

        assertEquals(CURRENT_CONFIG_VERSION, defaults.configVersion)
        assertEquals(listOf("..email", "..sign"), defaults.snippets.map { it.trigger })
        // normalized() must not touch a constructor-built config.
        assertEquals(
            listOf("..email", "..sign"),
            defaults.normalized().snippets.map { it.trigger }
        )
    }

    @Test
    fun `a null blob yields the default config at the current version`() {
        val fromNull: AppConfig? = null

        val config = fromNull.normalized()

        assertEquals(CURRENT_CONFIG_VERSION, config.configVersion)
        assertTrue(config.snippets.isNotEmpty())
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests '*AppConfigMigrationTest*'`

Expected: FAIL at compilation with `Unresolved reference: CURRENT_CONFIG_VERSION` and `Unresolved reference: configVersion`.

- [ ] **Step 3: Write the implementation**

Replace the entire contents of `app/src/main/kotlin/com/simplesnippet/app/data/AppConfig.kt` with:

```kotlin
package com.simplesnippet.app.data

/**
 * Schema version of a config this build writes. Bump it, and add a matching
 * branch to [normalized], with every stored-shape change.
 *
 * v1 (implicit, no field): one global trigger prefix, snippets stored bare.
 * v2: no prefix — each snippet's `trigger` is its complete free-form shortcut.
 */
const val CURRENT_CONFIG_VERSION = 2

/** The v1 default delimiter, and the fallback when a v1 blob has none stored. */
const val DEFAULT_TRIGGER_PREFIX = ".."

const val DEFAULT_SAVE_PATTERN = "(.save:%:%)"

data class AppConfig(
    var isAppEnabled: Boolean = false,
    var snippets: MutableList<Snippet> = mutableListOf(),
    /**
     * Schema v1 only: the global prefix every trigger was matched behind.
     * Kept so old blobs still deserialize and so [normalized] can read a user's
     * custom prefix while migrating. Nothing else may read or write it.
     */
    @Deprecated("Schema v1 only — shortcuts are stored whole in Snippet.trigger")
    var snippetTriggerPrefix: String = DEFAULT_TRIGGER_PREFIX,
    var saveSnippetPattern: String = DEFAULT_SAVE_PATTERN,
    var triggerDebounceMs: Long = 400L,
    var allowTriggerAnywhere: Boolean = false,
    /**
     * Gson bypasses constructors, so a v1 blob deserializes with 0 here while
     * anything this build constructs is born at [CURRENT_CONFIG_VERSION].
     */
    var configVersion: Int = CURRENT_CONFIG_VERSION
)

data class Snippet(
    var trigger: String = "",
    var contents: MutableList<String> = mutableListOf()
)

/**
 * Restores class invariants after Gson deserialization, and migrates old blobs.
 *
 * Gson constructs objects via Unsafe, bypassing Kotlin constructors and
 * default values — a JSON blob missing a field leaves null in a non-null
 * property with no compiler warning. Call this after every fromJson.
 *
 * Mutates and returns the *same* instance. A caller that needs to know whether
 * a migration happened must read `configVersion` before calling.
 */
@Suppress("SENSELESS_COMPARISON", "USELESS_ELVIS", "DEPRECATION")
fun AppConfig?.normalized(): AppConfig {
    val config = this ?: return createDefaultConfig()
    if (config.snippets == null) config.snippets = mutableListOf()
    if (config.saveSnippetPattern == null) config.saveSnippetPattern = DEFAULT_SAVE_PATTERN
    if (config.triggerDebounceMs <= 0L) config.triggerDebounceMs = 400L
    config.snippets.removeAll { it == null || it.trigger == null || it.trigger.isBlank() }
    config.snippets.forEach {
        if (it.contents == null) it.contents = mutableListOf()
        // Gson also lets nulls through inside the list itself.
        it.contents.removeAll { c -> c == null }
    }

    // v1 -> v2: the global prefix folds into every shortcut.
    //
    // This MUST read snippetTriggerPrefix before the null/blank patch below
    // rewrites it. Patch first and a user's custom prefix is silently replaced
    // by "..", migrating every stored shortcut to a delimiter they never typed.
    if (config.configVersion < CURRENT_CONFIG_VERSION) {
        val storedPrefix = config.snippetTriggerPrefix
        val legacyPrefix =
            if (storedPrefix == null || storedPrefix.isBlank()) DEFAULT_TRIGGER_PREFIX
            else storedPrefix
        config.snippets.forEach { s -> s.trigger = legacyPrefix + s.trigger }
        config.configVersion = CURRENT_CONFIG_VERSION
    }

    if (config.snippetTriggerPrefix == null || config.snippetTriggerPrefix.isEmpty()) {
        config.snippetTriggerPrefix = DEFAULT_TRIGGER_PREFIX
    }
    return config
}

/**
 * Constructor-built, so it is already at [CURRENT_CONFIG_VERSION] and never
 * migrates — which is why its shortcuts must carry the delimiter literally.
 */
fun createDefaultConfig(): AppConfig = AppConfig(
    snippets = mutableListOf(
        Snippet("..email", mutableListOf("user@example.com")),
        Snippet("..sign", mutableListOf("Best regards,\nUser"))
    )
)
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew testDebugUnitTest`

Expected: PASS — `BUILD SUCCESSFUL`, including the whole existing suite.

- [ ] **Step 5: Add the one-shot migration write-back in the UI load path**

In `app/src/main/kotlin/com/simplesnippet/app/ui/SimpleSnippetApp.kt`, add this import next to the existing `com.simplesnippet.app.data.*` imports (after line 19, `import com.simplesnippet.app.data.AppConfig`):

```kotlin
import com.simplesnippet.app.data.CURRENT_CONFIG_VERSION
```

Then replace lines 36-43:

```kotlin
    fun loadConfig(): AppConfig = try {
        gson.fromJson(
            prefs.getString(SnippetAccessibilityService.KEY_CONFIG, null),
            AppConfig::class.java
        )
    } catch (e: Exception) {
        null
    }.normalized()
```

with:

```kotlin
    fun loadConfig(): AppConfig {
        val stored = try {
            gson.fromJson(
                prefs.getString(SnippetAccessibilityService.KEY_CONFIG, null),
                AppConfig::class.java
            )
        } catch (e: Exception) {
            null
        }
        // normalized() mutates and returns the same instance, so the stored
        // version has to be captured first: a v1 blob reads 0 here and
        // CURRENT_CONFIG_VERSION after. A missing/unparseable blob yields the
        // constructor-built default, which is already current — treating it as
        // "unchanged" keeps first launch from writing a config nobody asked for.
        val versionBefore = stored?.configVersion ?: CURRENT_CONFIG_VERSION
        val config = stored.normalized()
        if (versionBefore != config.configVersion) {
            // One-shot write-back so the migration survives a restart. Done here
            // and nowhere else: the accessibility service's load path must stay
            // read-only, because writing from it re-enters its own prefs
            // listener, cancelling pending debounces and tearing down an open
            // picker. The listener re-entry here is harmless and self-limiting —
            // the reload sees a current version and writes nothing.
            prefs.edit()
                .putString(SnippetAccessibilityService.KEY_CONFIG, gson.toJson(config))
                .apply()
        }
        return config
    }
```

- [ ] **Step 6: Verify the app compiles**

Run: `./gradlew assembleDebug`

Expected: `BUILD SUCCESSFUL`. Deprecation warnings pointing at `SettingsScreen.kt` (which still reads `snippetTriggerPrefix`) are expected here and are removed in Task 5.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/com/simplesnippet/app/data/AppConfig.kt \
        app/src/main/kotlin/com/simplesnippet/app/ui/SimpleSnippetApp.kt \
        app/src/test/kotlin/com/simplesnippet/app/data/AppConfigMigrationTest.kt
git commit -m "feat: version the config and migrate the trigger prefix into shortcuts"
```

---

### Task 3: Service quick-save shortcut validation

**Files:**
- Modify: `app/src/main/kotlin/com/simplesnippet/app/service/SnippetAccessibilityService.kt` — add one field near line 106 and edit the quick-save block at lines 176-189
- Test: `app/src/test/kotlin/com/simplesnippet/app/data/SnippetMatcherTest.kt` (append one test)

**Interfaces:**
- Consumes: `SnippetMatcher.findSaveCommand(text: String, pattern: String): SnippetMatcher.SaveCommand?`, `SnippetMatcher.SaveCommand.trigger: String`, `SnippetMatcher.SaveCommand.fullMatch: String`, `SnippetMatcher.isValidShortcut(shortcut: String): Boolean` (all from Task 1); `OverlayManager.showToast(message: String)` (existing).
- Produces: no new public API. Behaviour: a quick-save whose parsed shortcut fails `isValidShortcut` is skipped, the field text is left untouched, and one toast is shown per distinct rejected command.

- [ ] **Step 1: Write the failing test**

Append this test to `app/src/test/kotlin/com/simplesnippet/app/data/SnippetMatcherTest.kt`, immediately before the closing brace of the class:

```kotlin
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
```

- [ ] **Step 2: Run the test to verify it passes already, then implement the gate it documents**

Run: `./gradlew testDebugUnitTest --tests '*SnippetMatcherTest*'`

Expected: PASS. This test pins the *composition* the service performs — the validators themselves already exist from Task 1. The behaviour it guards (the service actually refusing the save) is not JVM-testable here, because `SnippetAccessibilityService` is an Android component with no test harness in this project; it is verified by `assembleDebug` and by reading the code below.

- [ ] **Step 3: Add the de-duplication field to the service**

In `app/src/main/kotlin/com/simplesnippet/app/service/SnippetAccessibilityService.kt`, after line 106 (`private var prefsListener: SharedPreferences.OnSharedPreferenceChangeListener? = null`), add:

```kotlin

    // Every keystroke after a malformed quick-save command re-parses the same
    // command, so remember the last one we rejected and toast only once per
    // distinct command instead of once per character typed.
    private var lastRejectedSaveCommand: String? = null
```

- [ ] **Step 4: Add the validation gate to the quick-save block**

Replace lines 176-189 of the same file:

```kotlin
            // Quick-save runs before expansion on purpose: a save payload that
            // contains an existing trigger must be saved, not expanded.
            SnippetMatcher.findSaveCommand(currentText, cfg.saveSnippetPattern)?.let { cmd ->
                val existing = cfg.snippets.find { it.trigger == cmd.trigger }
                if (existing != null) {
                    if (!existing.contents.contains(cmd.content)) existing.contents.add(cmd.content)
                } else {
                    cfg.snippets.add(Snippet(cmd.trigger, mutableListOf(cmd.content)))
                }
                prefs.edit().putString(KEY_CONFIG, gson.toJson(cfg)).apply()
                pasteText(inputNode, currentText.replace(cmd.fullMatch, cmd.content))
                overlayManager.showToast("Snippet '" + cmd.trigger + "' saved!")
                return
            }
```

with:

```kotlin
            // Quick-save runs before expansion on purpose: a save payload that
            // contains an existing shortcut must be saved, not expanded.
            SnippetMatcher.findSaveCommand(currentText, cfg.saveSnippetPattern)?.let { cmd ->
                // The same validator the snippet dialog uses, so a quick-save
                // cannot create a shortcut the dialog would have refused.
                if (!SnippetMatcher.isValidShortcut(cmd.trigger)) {
                    if (lastRejectedSaveCommand != cmd.fullMatch) {
                        lastRejectedSaveCommand = cmd.fullMatch
                        overlayManager.showToast("Shortcut can't contain spaces — not saved")
                    }
                    return
                }
                lastRejectedSaveCommand = null
                val existing = cfg.snippets.find { it.trigger == cmd.trigger }
                if (existing != null) {
                    if (!existing.contents.contains(cmd.content)) existing.contents.add(cmd.content)
                } else {
                    cfg.snippets.add(Snippet(cmd.trigger, mutableListOf(cmd.content)))
                }
                prefs.edit().putString(KEY_CONFIG, gson.toJson(cfg)).apply()
                pasteText(inputNode, currentText.replace(cmd.fullMatch, cmd.content))
                overlayManager.showToast("Snippet '" + cmd.trigger + "' saved!")
                return
            }
```

- [ ] **Step 5: Run the full suite and compile**

Run: `./gradlew testDebugUnitTest assembleDebug`

Expected: `BUILD SUCCESSFUL`, all unit tests pass.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/simplesnippet/app/service/SnippetAccessibilityService.kt \
        app/src/test/kotlin/com/simplesnippet/app/data/SnippetMatcherTest.kt
git commit -m "fix: reject quick-saves whose shortcut the snippet dialog would refuse"
```

---

### Task 4: Snippet dialog — free-form shortcut, validation, self-expansion warning

**Files:**
- Modify: `app/src/main/kotlin/com/simplesnippet/app/ui/screens/SnippetsScreen.kt` — import block (after line 32), usage card (lines 177-184), edit dialog (lines 242-316)

**Interfaces:**
- Consumes: `SnippetMatcher.isValidShortcut(shortcut: String): Boolean` (Task 1); `AppConfig.snippets: MutableList<Snippet>`, `AppConfig.saveSnippetPattern: String`, `Snippet(trigger: String, contents: MutableList<String>)` (Task 2).
- Produces: no new API. The screen no longer reads `AppConfig.snippetTriggerPrefix`.

- [ ] **Step 1: Add the SnippetMatcher import**

In `app/src/main/kotlin/com/simplesnippet/app/ui/screens/SnippetsScreen.kt`, after line 31 (`import com.simplesnippet.app.data.Snippet`), add:

```kotlin
import com.simplesnippet.app.data.SnippetMatcher
```

- [ ] **Step 2: Update the usage card copy**

Replace lines 177-184:

```kotlin
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Usage:", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    Text("Type '${config.snippetTriggerPrefix}' + Trigger Name to expand.", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    Spacer(Modifier.height(4.dp))
                    Text("Quick Save:", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    val saveExample = config.saveSnippetPattern.replaceFirst("%", "name").replaceFirst("%", "content")
                    Text("Type '$saveExample' to save instantly.", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
```

with:

```kotlin
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Usage:", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    Text("Type a snippet's shortcut (e.g. ..email) to expand it.", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    Spacer(Modifier.height(4.dp))
                    Text("Quick Save:", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    val saveExample = config.saveSnippetPattern.replaceFirst("%", "shortcut").replaceFirst("%", "content")
                    Text("Type '$saveExample' to save instantly.", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
```

- [ ] **Step 3: Rewrite the edit dialog**

Replace lines 242-316 (the whole `if (showEditDialog) { ... }` block) with:

```kotlin
        if (showEditDialog) {
            // Legacy tolerance: a shortcut migrated from v1 may violate today's
            // rules (whitespace inside a v1 trigger, say). Re-saving it untouched
            // has to stay possible, so validation only applies once the text is
            // actually modified — and an unchanged shortcut is saved verbatim,
            // because trimming it would itself count as a modification.
            val shortcutUnchanged = originalTrigger != null && tTrigger == originalTrigger
            val shortcutToSave = if (shortcutUnchanged) tTrigger else tTrigger.trim()
            val isShortcutValid = shortcutUnchanged || SnippetMatcher.isValidShortcut(shortcutToSave)
            val hasContent = tContents.any { it.isNotBlank() }
            // Runaway risk: expanding into text that still contains the shortcut
            // re-matches on the next keystroke, especially with "expand anywhere"
            // on. Warn, but never block — self-reference is occasionally wanted.
            val selfExpansionRisk = shortcutToSave.isNotEmpty() &&
                tContents.any { it.contains(shortcutToSave) }

            AlertDialog(
                onDismissRequest = { showEditDialog = false },
                title = { Text(if (originalTrigger == null) "New Snippet" else "Edit Snippet") },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = tTrigger,
                            onValueChange = { tTrigger = it },
                            label = { Text("Shortcut (e.g. ..email)") },
                            singleLine = true,
                            isError = !isShortcutValid,
                            supportingText = {
                                Text(
                                    if (isShortcutValid) "Type this anywhere to expand the snippet."
                                    else "Shortcut can't be blank or contain spaces.",
                                    fontSize = 12.sp
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(16.dp))
                        Text("Variations:", fontWeight = FontWeight.Bold, fontSize = 14.sp)

                        Box(modifier = Modifier.weight(1f, fill = false).heightIn(max = 300.dp)) {
                            LazyColumn {
                                itemsIndexed(tContents.toList()) { index, content ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = content,
                                            onValueChange = { tContents[index] = it },
                                            label = { Text("Variation ${index + 1}") },
                                            modifier = Modifier.weight(1f),
                                            minLines = 1
                                        )
                                        if (tContents.size > 1) {
                                            IconButton(onClick = { tContents.removeAt(index) }) {
                                                Icon(Icons.Default.Close, "Remove", tint = MaterialTheme.colorScheme.error)
                                            }
                                        }
                                    }
                                }
                                item {
                                    TextButton(
                                        onClick = { tContents.add("") }
                                    ) {
                                        Icon(Icons.Default.Add, "Add")
                                        Spacer(Modifier.width(8.dp))
                                        Text("Add Variation")
                                    }
                                }
                            }
                        }

                        if (selfExpansionRisk) {
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "A variation contains '$shortcutToSave' — the inserted text may expand again.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        // Disabled rather than silently doing nothing, which is
                        // what the old `if (...)` guard around the save did.
                        enabled = isShortcutValid && hasContent,
                        onClick = {
                            val validContents = tContents.filter { it.isNotBlank() }.toMutableList()
                            val n = snippets.toMutableList()

                            // Duplicate check: creating a new snippet, or renaming
                            // an existing one onto a shortcut already in use.
                            if (shortcutToSave != originalTrigger && n.any { it.trigger == shortcutToSave }) {
                                Toast.makeText(context, "Snippet '$shortcutToSave' already exists!", Toast.LENGTH_SHORT).show()
                                return@Button
                            }

                            if (originalTrigger != null) n.removeIf { it.trigger == originalTrigger }
                            n.removeIf { it.trigger == shortcutToSave }
                            n.add(Snippet(shortcutToSave, contents = validContents))
                            onSave(config.copy(snippets = n))
                            showEditDialog = false
                        }
                    ) { Text("Save") }
                },
                dismissButton = {
                    TextButton(onClick = { showEditDialog = false }) { Text("Cancel") }
                }
            )
        }
```

- [ ] **Step 4: Verify the app compiles and the unit suite still passes**

Run: `./gradlew testDebugUnitTest assembleDebug`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/simplesnippet/app/ui/screens/SnippetsScreen.kt
git commit -m "feat: free-form shortcut editor with validation and self-expansion warning"
```

---

### Task 5: Settings — drop the prefix field, add the quick-save pattern editor

**Files:**
- Modify: `app/src/main/kotlin/com/simplesnippet/app/ui/screens/SettingsScreen.kt` — import block (after line 22), drafts (lines 32-36), sections 1 and 2 (lines 59-118), the "expand anywhere" subtitle (line 125), the debounce helper text (line 153)

**Line numbers below are from the file as it stands before this task.** Earlier steps shift them, so match on the quoted text, not on the number.

**Interfaces:**
- Consumes: `SnippetMatcher.isValidSavePattern(pattern: String): Boolean` (Task 1); `AppConfig.saveSnippetPattern: String`, `AppConfig.allowTriggerAnywhere: Boolean`, `AppConfig.triggerDebounceMs: Long` (Task 2).
- Produces: no new API. This is the last reader of `AppConfig.snippetTriggerPrefix`; after this task the deprecated field is referenced only inside `AppConfig.normalized()`.

- [ ] **Step 1: Add the SnippetMatcher import**

In `app/src/main/kotlin/com/simplesnippet/app/ui/screens/SettingsScreen.kt`, after line 22 (`import com.simplesnippet.app.data.AppConfig`), add:

```kotlin
import com.simplesnippet.app.data.SnippetMatcher
```

- [ ] **Step 2: Replace the drafts and validation block**

Replace lines 32-36:

```kotlin
    // Drafts are re-keyed on the persisted value so an external config change resyncs them.
    var prefixDraft by remember(config.snippetTriggerPrefix) { mutableStateOf(config.snippetTriggerPrefix) }
    var delayDraft by remember(config.triggerDebounceMs) { mutableStateOf(config.triggerDebounceMs.toFloat()) }

    val isPrefixValid = prefixDraft.isNotBlank() && !prefixDraft.any { it.isWhitespace() }
```

with:

```kotlin
    // Drafts are re-keyed on the persisted value so an external config change resyncs them.
    var savePatternDraft by remember(config.saveSnippetPattern) { mutableStateOf(config.saveSnippetPattern) }
    var delayDraft by remember(config.triggerDebounceMs) { mutableStateOf(config.triggerDebounceMs.toFloat()) }

    // Validate and apply the trimmed value, so surrounding whitespace can never
    // become part of a literal segment of the pattern.
    val savePatternCandidate = savePatternDraft.trim()
    val isSavePatternValid = SnippetMatcher.isValidSavePattern(savePatternCandidate)
    val savePatternPreview = savePatternCandidate
        .replaceFirst("%", "shortcut")
        .replaceFirst("%", "content")
```

- [ ] **Step 3: Replace sections 1 and 2 with the quick-save pattern editor**

Replace lines 59-118 (from `SettingsSectionHeader("EXPANSION")` through the `Spacer(Modifier.height(16.dp))` that follows the read-only Quick save `Card`):

```kotlin
            SettingsSectionHeader("EXPANSION")

            // 1. Trigger prefix. Applied on confirm, never per keystroke: the accessibility
            // service reloads its config on every prefs change, so persisting a half-typed
            // prefix ("." on the way to "..") would break live expansion until the user finished.
            OutlinedTextField(
                value = prefixDraft,
                onValueChange = { prefixDraft = it },
                label = { Text("Trigger prefix") },
                singleLine = true,
                isError = !isPrefixValid,
                supportingText = {
                    Text(
                        if (isPrefixValid) "${prefixDraft}email → user@example.com"
                        else "Prefix can't be blank or contain spaces",
                        fontSize = 12.sp
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(
                    onClick = { onSave(config.copy(snippetTriggerPrefix = prefixDraft.trim())) },
                    enabled = isPrefixValid && prefixDraft != config.snippetTriggerPrefix
                ) { Text("Apply") }
            }

            Spacer(Modifier.height(16.dp))

            // 2. Quick-save pattern is read-only on purpose: a malformed pattern (wrong number
            // of '%' placeholders) makes SnippetMatcher.findSaveCommand return null, silently
            // disabling quick-save with no visible error.
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Quick save",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        config.saveSnippetPattern.replaceFirst("%", "name").replaceFirst("%", "content"),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Type this in any text field to save a snippet on the spot.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
```

with:

```kotlin
            SettingsSectionHeader("EXPANSION")

            // 1. Quick-save pattern. Applied on confirm, never per keystroke: the accessibility
            // service reloads its config on every prefs change, so persisting a half-typed
            // pattern would silently disable quick-save until the user finished typing.
            // Invalid input can't be applied at all — an unusable pattern makes
            // SnippetMatcher.findSaveCommand return null with no visible error.
            OutlinedTextField(
                value = savePatternDraft,
                onValueChange = { savePatternDraft = it },
                label = { Text("Quick-save pattern") },
                singleLine = true,
                isError = !isSavePatternValid,
                supportingText = {
                    Text(
                        if (isSavePatternValid) "Type '$savePatternPreview' in any field to save a snippet on the spot."
                        else "Needs exactly two % placeholders with text before, between, and after them.",
                        fontSize = 12.sp
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(
                    onClick = { onSave(config.copy(saveSnippetPattern = savePatternCandidate)) },
                    enabled = isSavePatternValid && savePatternCandidate != config.saveSnippetPattern
                ) { Text("Apply") }
            }

            Spacer(Modifier.height(16.dp))
```

- [ ] **Step 4: Update the two remaining "trigger" strings**

Replace line 125 (inside the "Expand anywhere in text" row):

```kotlin
                        "Match triggers mid-text instead of only at the end",
```

with:

```kotlin
                        "Match shortcuts mid-text instead of only at the end",
```

Replace the debounce helper text (line 153):

```kotlin
                "How long to wait after typing before a single-variation snippet auto-expands.",
```

with:

```kotlin
                "How long to wait after typing a shortcut before a single-variation snippet auto-expands.",
```

- [ ] **Step 5: Verify the app compiles with no deprecation warnings from this file**

Run: `./gradlew clean assembleDebug`

Expected: `BUILD SUCCESSFUL`, and no `snippetTriggerPrefix is deprecated` warning pointing at `SettingsScreen.kt`. (`FontWeight`, `Card`, and `CardDefaults` are still used by `SettingsSectionHeader` / `SettingsNavRow`, so no imports become unused.)

- [ ] **Step 6: Run the unit suite**

Run: `./gradlew testDebugUnitTest`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/com/simplesnippet/app/ui/screens/SettingsScreen.kt
git commit -m "feat: make the quick-save pattern editable and drop the trigger prefix setting"
```

---

### Task 6: Test Lab presets derived from the live config

**Files:**
- Modify: `app/src/main/kotlin/com/simplesnippet/app/ui/screens/TestScreen.kt` — imports, signature, presets
- Modify: `app/src/main/kotlin/com/simplesnippet/app/ui/SimpleSnippetApp.kt:126-130` (the `"test"` branch)

**Interfaces:**
- Consumes: `AppConfig.snippets: MutableList<Snippet>`, `AppConfig.saveSnippetPattern: String` (Task 2).
- Produces: `TestScreen(config: AppConfig, onStartTest: () -> Unit, onStopTest: () -> Unit, onBack: () -> Unit)` — `config` is the **first** parameter, matching `SnippetsScreen` and `SettingsScreen`.

- [ ] **Step 1: Add the import and the config parameter**

In `app/src/main/kotlin/com/simplesnippet/app/ui/screens/TestScreen.kt`, after line 16 (`import androidx.compose.ui.unit.sp`), add:

```kotlin
import com.simplesnippet.app.data.AppConfig
```

- [ ] **Step 2: Replace the signature and the hardcoded presets**

Replace lines 18-29:

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TestScreen(onStartTest: () -> Unit, onStopTest: () -> Unit, onBack: () -> Unit) {
    var t by remember { mutableStateOf("") }

    DisposableEffect(Unit) { onStartTest(); onDispose { onStopTest() } }

    val presets = listOf(
        "My address is ..email",
        "..sign",
        "(.save:ph:+1 555 0199)"
    )
```

with:

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TestScreen(
    config: AppConfig,
    onStartTest: () -> Unit,
    onStopTest: () -> Unit,
    onBack: () -> Unit
) {
    var t by remember { mutableStateOf("") }

    DisposableEffect(Unit) { onStartTest(); onDispose { onStopTest() } }

    // Derived from the live config, not hardcoded: shortcuts are free-form now,
    // so a fixed "..email" preset would simply fail to expand for most users.
    // Each preset puts the shortcut at end-of-text after a space, which the
    // default (end-anchored, boundary-checked) matching rules accept.
    val presets = remember(config.snippets, config.saveSnippetPattern) {
        val expandable = config.snippets
            .filter { s -> s.contents.any { c -> c.isNotEmpty() } }
            .take(3)
            .map { s -> "Preview: " + s.trigger }
        val saveExample = config.saveSnippetPattern
            .replaceFirst("%", "ph")
            .replaceFirst("%", "+1 555 0199")
        expandable + saveExample
    }
```

- [ ] **Step 3: Pass the config from the navigator**

In `app/src/main/kotlin/com/simplesnippet/app/ui/SimpleSnippetApp.kt`, replace lines 126-130:

```kotlin
                "test" -> TestScreen(
                    onStartTest = { prefs.edit().putBoolean(SnippetAccessibilityService.KEY_TESTING, true).apply() },
                    onStopTest = { prefs.edit().putBoolean(SnippetAccessibilityService.KEY_TESTING, false).apply() },
                    onBack = { navigateTo("settings") }
                )
```

with:

```kotlin
                "test" -> TestScreen(
                    config = config,
                    onStartTest = { prefs.edit().putBoolean(SnippetAccessibilityService.KEY_TESTING, true).apply() },
                    onStopTest = { prefs.edit().putBoolean(SnippetAccessibilityService.KEY_TESTING, false).apply() },
                    onBack = { navigateTo("settings") }
                )
```

- [ ] **Step 4: Verify the app compiles and the unit suite passes**

Run: `./gradlew testDebugUnitTest assembleDebug`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/simplesnippet/app/ui/screens/TestScreen.kt \
        app/src/main/kotlin/com/simplesnippet/app/ui/SimpleSnippetApp.kt
git commit -m "feat: derive Test Lab presets from the user's own snippets and save pattern"
```

---

### Task 7: Onboarding animation and accessibility service description copy

**Files:**
- Modify: `app/src/main/kotlin/com/simplesnippet/app/ui/components/TypingAnimationPreview.kt:23-27`
- Modify: `app/src/main/res/values/strings.xml:4`

**Interfaces:**
- Consumes: nothing — pure copy. `TypingAnimationPreview()` keeps its zero-argument signature and its single call site in `WelcomeScreen.kt:111`.
- Produces: nothing.

- [ ] **Step 1: Update the onboarding animation scenarios**

In `app/src/main/kotlin/com/simplesnippet/app/ui/components/TypingAnimationPreview.kt`, replace lines 23-27:

```kotlin
    val scenarios = listOf(
        Pair("My address is ..email", "My address is user@example.com"),
        Pair("..sign", "Best regards,\nUser"),
        Pair("(.save:ph:+1 555 0199)", "+1 555 0199")
    )
```

with:

```kotlin
    // Scenario 2 deliberately uses a shortcut with no leading punctuation: there
    // is no global prefix any more, so a shortcut can be any string you like.
    val scenarios = listOf(
        Pair("My address is ..email", "My address is user@example.com"),
        Pair("Any shortcut you like: omw", "Any shortcut you like: On my way!"),
        Pair("(.save:ph:+1 555 0199)", "+1 555 0199")
    )
```

- [ ] **Step 2: Update the accessibility service description**

In `app/src/main/res/values/strings.xml`, replace line 4:

```xml
    <string name="accessibility_service_description">SimpleSnippet watches text fields for your snippet triggers (for example ..email) and replaces them with your saved text. Everything runs on your device — no text is stored or sent anywhere.</string>
```

with:

```xml
    <string name="accessibility_service_description">SimpleSnippet watches text fields for your snippet shortcuts (for example ..email) and replaces them with your saved text. Everything runs on your device — no text is stored or sent anywhere.</string>
```

- [ ] **Step 3: Verify the app compiles**

Run: `./gradlew assembleDebug`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/simplesnippet/app/ui/components/TypingAnimationPreview.kt \
        app/src/main/res/values/strings.xml
git commit -m "docs: say shortcut, not trigger, in onboarding and the service description"
```

---

### Task 8: README and version bump

**Files:**
- Modify: `README.md:15-18`, `README.md:26-32`
- Modify: `app/build.gradle:23-24`

**Interfaces:**
- Consumes: nothing.
- Produces: `versionCode = 2`, `versionName = "1.1.0"` — surfaced in the UI via `BuildConfig.VERSION_NAME` in `SettingsScreen.kt:179`, which needs no change.

- [ ] **Step 1: Update the README features list**

Replace lines 15-18:

```markdown
* **Snippet Expansion** — Type a short trigger and it expands into full text, in any app.
  * Example: `..email` → `user@example.com`
* **Multiple Variations** — Attach several possible expansions to one trigger; a floating picker lets you choose which one to insert.
* **In-Field Quick Save** — Create a new snippet without ever opening the app: type `(.save:name:content)` and it's saved instantly.
```

with:

```markdown
* **Snippet Expansion** — Type a short shortcut and it expands into full text, in any app.
  * Example: `..email` → `user@example.com`
* **Free-Form Shortcuts** — Every snippet owns its whole shortcut, so `..email`, `;sig`, and plain `omw` can all coexist. A shortcut that begins with a letter or digit only fires at a word boundary, so `omw` never expands inside `shomw`.
* **Multiple Variations** — Attach several possible expansions to one shortcut; a floating picker lets you choose which one to insert.
* **In-Field Quick Save** — Create a new snippet without ever opening the app: type `(.save:shortcut:content)` and it's saved instantly.
```

- [ ] **Step 2: Update the README usage table and the configurability line**

Replace lines 26-32:

```markdown
| Action | How |
| :--- | :--- |
| Expand a snippet | Type its trigger, e.g. `..email` |
| Pick a variation | Type a trigger that has multiple saved contents; a picker pops up |
| Quick save a new snippet | Type `(.save:name:content)` anywhere |

The `..` trigger prefix is configurable in **Settings**; the quick-save pattern is fixed at `(.save:name:content)`.
```

with:

```markdown
| Action | How |
| :--- | :--- |
| Expand a snippet | Type its shortcut, e.g. `..email` |
| Pick a variation | Type a shortcut that has multiple saved contents; a picker pops up |
| Quick save a new snippet | Type `(.save:shortcut:content)` anywhere |

Each snippet's shortcut is free-form — set it to whatever you like when you create the snippet. The quick-save pattern is configurable in **Settings**; it just needs two `%` placeholders with text before, between, and after them.

**Upgrading from 1.0.x:** your snippets migrate automatically the first time you open the app. Whatever trigger prefix you had configured is folded into each snippet's shortcut, so everything you already type keeps working.
```

- [ ] **Step 3: Bump the version**

In `app/build.gradle`, replace lines 23-24:

```groovy
        versionCode = 1
        versionName = "1.0.0"
```

with:

```groovy
        versionCode = 2
        versionName = "1.1.0"
```

- [ ] **Step 4: Verify the whole build**

Run: `./gradlew clean testDebugUnitTest assembleDebug`

Expected: `BUILD SUCCESSFUL`, all unit tests pass.

- [ ] **Step 5: Commit**

```bash
git add README.md app/build.gradle
git commit -m "docs: document free-form shortcuts and bump to 1.1.0 (versionCode 2)"
```

---

## Manual Verification (after Task 8)

Not automatable in this project — no UI test infrastructure exists beyond one instrumentation smoke test. Run these on a device or emulator once:

1. **Migration.** Install 1.0.0, create a snippet with trigger `tel`, set the prefix to `//` in Settings, then install this build over it. Open the app: the snippet list shows `//tel`, and typing `//tel` in any app still expands. Force-stop and reopen: it is still `//tel` (the write-back persisted).
2. **Boundary rule.** Create shortcut `omw`. In any field, type `shomw` — nothing expands. Type `hey omw` — it expands.
3. **Symbol shortcut.** Type `hello..email` — it expands, exactly as before the change.
4. **Quick-save validation.** Type `(.save:my name:hi)` — a toast says the shortcut can't contain spaces and nothing is saved; keep typing and the toast does not repeat. Type `(.save:ph:hi)` — it saves.
5. **Editable pattern.** In Settings, change the pattern to `<%|%>`; Apply is enabled. Change it to `<%%>`; the field goes red and Apply is disabled. Apply `<%|%>` and confirm `<ph|hi>` quick-saves in another app.
6. **Dialog.** Open a snippet, blank the shortcut — Save is disabled with an error. Put a space in it — same. Add a variation containing the shortcut — a non-blocking warning appears but Save stays enabled.
7. **Test Lab.** Settings → Test Lab shows presets built from your own snippets plus your own quick-save pattern.

---

## Self-Review

**1. Spec coverage.**

| Spec requirement | Task |
| :--- | :--- |
| `Snippet.trigger` becomes the free-form full shortcut; JSON key unchanged | 1 (matching), 4 (editing) |
| `snippetTriggerPrefix` removed from logic, kept as deprecated field | 2 (deprecated), 5 (last reader removed) |
| `configVersion: Int` with constructor default 2; old blobs get 0 | 2 |
| Migration: prefix (or `..`) prepended when `configVersion < 2`, then stamp 2 | 2 |
| Null-patching must not run before the migration reads the prefix | 2 (ordering + comment + `blank prefix` and `no prefix field` tests) |
| Persistence: one-shot write-back from UI `loadConfig()`; service read-only | 2 |
| `createDefaultConfig()` carries `..email`, `..sign` | 2 |
| `find` drops `prefix`; longest-shortcut-wins preserved | 1 |
| Boundary rule | 1 |
| `replaceTrigger` re-match with no prefix; stale-config semantics preserved | 1 |
| `allowTriggerAnywhere` / `triggerDebounceMs` unchanged | 1, 5 (untouched) |
| `isValidSavePattern` shared by Settings and `findSaveCommand` | 1, 5 |
| Settings draft + Apply + validation + live preview `%`→shortcut/content | 5 |
| Quick-save creates the snippet with the name exactly as typed | 3 (no prefixing added; existing code already used `cmd.trigger` verbatim) |
| Service runs the shortcut validator on `cmd.trigger`, skips with a toast | 3 |
| Settings: prefix field removed, save-pattern editor added | 5 |
| Snippet dialog: free-form, empty by default, non-blank/no-whitespace/trimmed | 4 |
| `isError` + supporting text + disabled save button | 4 |
| Duplicate check unchanged | 4 (still the same toast, same condition shape) |
| Legacy tolerance for unmodified shortcuts | 4 |
| Self-expansion warning (non-blocking) | 4 |
| `TestScreen` receives the config, derives presets | 6 |
| Copy: usage card | 4 |
| Copy: onboarding typing animation | 7 |
| Copy: `strings.xml` service description | 7 |
| Copy: README prefix/quick-save inversion | 8 |
| `versionCode 2`, `versionName "1.1.0"` | 8 |
| Tests: new `find` signature, boundary cases, `isValidSavePattern` table | 1 |
| Tests: migration via raw JSON through Gson (5 listed cases + defaults) | 2 |
| Tests: quick-save whitespace name rejected | 3 |

No gaps found.

**2. Placeholder scan.** No "TBD", "TODO", "similar to Task N", or "add validation"-style hand-waving. Every code step contains complete, paste-ready code; every command has an expected result. Task 4's dialog rewrite reproduces the unchanged variations `LazyColumn` in full rather than saying "keep the existing block", because the surrounding braces change.

**3. Type consistency.** Checked across tasks:
- `SnippetMatcher.find(text, snippets, allowAnywhere)` — 3 args everywhere it appears (Task 1 impl, Task 1 tests, Task 1 service call sites).
- `SnippetMatcher.isValidShortcut(String): Boolean` — defined Task 1, used Tasks 3 and 4.
- `SnippetMatcher.isValidSavePattern(String): Boolean` — defined Task 1, used Task 1 (`findSaveCommand`) and Task 5.
- `CURRENT_CONFIG_VERSION` — defined Task 2, used Task 2's tests and `SimpleSnippetApp.loadConfig()`.
- `DEFAULT_TRIGGER_PREFIX` / `DEFAULT_SAVE_PATTERN` — defined and used only inside Task 2's `AppConfig.kt`.
- `TestScreen(config, onStartTest, onStopTest, onBack)` — parameter order identical in Task 6's definition and its call site.
- `Snippet(trigger, contents)` — unchanged; Task 4 constructs it as `Snippet(shortcutToSave, contents = validContents)`, matching the existing call shape.

**4. Build-green ordering.** Verified: the only compile-breaking change is `find`'s signature (Task 1), whose two production call sites are fixed inside Task 1. `AppConfig.snippetTriggerPrefix` survives through Task 4 (so `SettingsScreen` keeps compiling) and its last reader disappears in Task 5. `TestScreen`'s signature change and its call site are in the same task.
