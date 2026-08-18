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
    /**
     * Deliberately has no default, and that absence is load-bearing: Kotlin
     * synthesizes a no-arg constructor only when *every* parameter defaults,
     * and Gson prefers such a constructor over Unsafe allocation. With one, a
     * stored v1 blob would deserialize with [configVersion] already set to
     * [CURRENT_CONFIG_VERSION] and silently skip migration. Keep at least one
     * parameter undefaulted; `AppConfigMigrationTest` guards this by asserting
     * a parsed v1 blob reports version 0.
     */
    var snippets: MutableList<Snippet>,
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
