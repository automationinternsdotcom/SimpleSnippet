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
