package com.simplesnippet.app.data

data class AppConfig(
    var isAppEnabled: Boolean = false,
    var snippets: MutableList<Snippet> = mutableListOf(),
    var snippetTriggerPrefix: String = "..",
    var saveSnippetPattern: String = "(.save:%:%)",
    var triggerDebounceMs: Long = 400L,
    var allowTriggerAnywhere: Boolean = false
)

data class Snippet(
    var trigger: String = "",
    var contents: MutableList<String> = mutableListOf()
)

/**
 * Restores class invariants after Gson deserialization.
 *
 * Gson constructs objects via Unsafe, bypassing Kotlin constructors and
 * default values — a JSON blob missing a field leaves null in a non-null
 * property with no compiler warning. Call this after every fromJson.
 */
@Suppress("SENSELESS_COMPARISON", "USELESS_ELVIS")
fun AppConfig?.normalized(): AppConfig {
    val config = this ?: return createDefaultConfig()
    if (config.snippets == null) config.snippets = mutableListOf()
    if (config.snippetTriggerPrefix == null || config.snippetTriggerPrefix.isEmpty()) {
        config.snippetTriggerPrefix = ".."
    }
    if (config.saveSnippetPattern == null) config.saveSnippetPattern = "(.save:%:%)"
    if (config.triggerDebounceMs <= 0L) config.triggerDebounceMs = 400L
    config.snippets.removeAll { it == null || it.trigger == null || it.trigger.isBlank() }
    config.snippets.forEach {
        if (it.contents == null) it.contents = mutableListOf()
        // Gson also lets nulls through inside the list itself.
        it.contents.removeAll { c -> c == null }
    }
    return config
}

fun createDefaultConfig(): AppConfig = AppConfig(
    snippets = mutableListOf(
        Snippet("email", mutableListOf("user@example.com")),
        Snippet("sign", mutableListOf("Best regards,\nUser"))
    )
)
