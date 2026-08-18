package com.simplesnippet.app.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.google.gson.GsonBuilder
import com.simplesnippet.app.data.AppConfig
import com.simplesnippet.app.data.CURRENT_CONFIG_VERSION
import com.simplesnippet.app.data.normalized
import com.simplesnippet.app.service.SnippetAccessibilityService
import com.simplesnippet.app.ui.screens.*

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun SimpleSnippetApp() {
    val context = LocalContext.current
    val gson = remember { GsonBuilder().setPrettyPrinting().create() }
    val prefs = remember(context) {
        context.getSharedPreferences(
            SnippetAccessibilityService.PREFS_NAME,
            Context.MODE_PRIVATE
        )
    }

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

    // Determine initial screen
    val hasSeenOnboarding = prefs.getBoolean("has_seen_onboarding", false)
    var currentScreen by rememberSaveable { mutableStateOf(if (hasSeenOnboarding) "snippets" else "welcome") }
    var previousScreen by rememberSaveable { mutableStateOf("snippets") } // Track previous screen for animation

    // A single state instance for the whole composition: keying remember on
    // currentScreen would discard it on navigation, leaving the listener
    // below writing into a dead MutableState.
    var config by remember { mutableStateOf(loadConfig()) }

    // The accessibility service runs in this same process, so a prefs listener
    // picks up its quick-saves the instant they happen — including while this
    // activity stays resumed (split-screen), where an ON_RESUME reload would
    // miss them and a later save here would clobber the service's write.
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == SnippetAccessibilityService.KEY_CONFIG) config = loadConfig()
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    fun saveConfig(newConfig: AppConfig) {
        config = newConfig
        prefs.edit().putString(SnippetAccessibilityService.KEY_CONFIG, gson.toJson(newConfig)).apply()
    }

    // Custom navigate function to track previous screen
    val navigateTo: (String) -> Unit = { screen ->
        previousScreen = currentScreen
        currentScreen = screen
    }

    BackHandler(enabled = currentScreen != "snippets" && currentScreen != "welcome") {
        when (currentScreen) {
            "permissions" -> navigateTo("settings")
            "test" -> navigateTo("settings")
            else -> navigateTo("snippets")
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        AnimatedContent(
            targetState = currentScreen,
            label = "Screen Animation",
            transitionSpec = {
                // Logic to determine if it's a "back" animation
                val isBackTransition = (targetState == "snippets" && previousScreen != "snippets") ||
                                       (targetState == "settings" && (previousScreen == "permissions" || previousScreen == "test"))

                if (isBackTransition) {
                    slideInHorizontally { fullWidth -> -fullWidth } togetherWith // New screen from left
                    slideOutHorizontally { fullWidth -> fullWidth } // Old screen to right
                } else { // Navigating forward
                    slideInHorizontally { fullWidth -> fullWidth } togetherWith // New screen from right
                    slideOutHorizontally { fullWidth -> -fullWidth } // Old screen to left
                }
            }
        ) { screen ->
            when (screen) {
                "welcome" -> WelcomeScreen(
                    onFinished = {
                        prefs.edit().putBoolean("has_seen_onboarding", true).apply()
                        navigateTo("snippets")
                    }
                )
                "snippets" -> SnippetsScreen(
                    config = config,
                    onSave = { saveConfig(it) },
                    onNavigate = { navigateTo(it) }
                )
                "settings" -> SettingsScreen(
                    config = config,
                    onSave = { saveConfig(it) },
                    onBack = { navigateTo("snippets") },
                    onNavigate = { navigateTo(it) }
                )
                "permissions" -> PermissionsScreen(
                    onFinished = { navigateTo("settings") },
                    isStandalone = true
                )
                "test" -> TestScreen(
                    config = config,
                    onStartTest = { prefs.edit().putBoolean(SnippetAccessibilityService.KEY_TESTING, true).apply() },
                    onStopTest = { prefs.edit().putBoolean(SnippetAccessibilityService.KEY_TESTING, false).apply() },
                    onBack = { navigateTo("settings") }
                )
            }
        }
    }
}
