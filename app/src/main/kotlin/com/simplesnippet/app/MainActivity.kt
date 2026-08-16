package com.simplesnippet.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.simplesnippet.app.service.SnippetAccessibilityService
import com.simplesnippet.app.ui.AppTheme
import com.simplesnippet.app.ui.SimpleSnippetApp

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // The Test Lab flag is normally cleared when its screen disposes, but a
        // process death while it is open would leave it stuck on — reset it here
        // so the service never keeps reacting to the app's own text fields.
        getSharedPreferences(SnippetAccessibilityService.PREFS_NAME, MODE_PRIVATE)
            .edit().putBoolean(SnippetAccessibilityService.KEY_TESTING, false).apply()

        setContent {
            AppTheme {
                SimpleSnippetApp()
            }
        }
    }
}
