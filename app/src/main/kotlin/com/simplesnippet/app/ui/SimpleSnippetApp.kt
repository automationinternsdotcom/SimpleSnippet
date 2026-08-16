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

    fun loadConfig(): AppConfig = try {
        gson.fromJson(
            prefs.getString(SnippetAccessibilityService.KEY_CONFIG, null),
            AppConfig::class.java
        )
    } catch (e: Exception) {
        null
    }.normalized()

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
                    onStartTest = { prefs.edit().putBoolean(SnippetAccessibilityService.KEY_TESTING, true).apply() },
                    onStopTest = { prefs.edit().putBoolean(SnippetAccessibilityService.KEY_TESTING, false).apply() },
                    onBack = { navigateTo("settings") }
                )
            }
        }
    }
}
