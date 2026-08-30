package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.data.ThemeMode
import com.example.data.ThemePreferences
import com.example.data.rememberFestoAppState
import com.example.ui.AppRoot
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // The persisted theme override is read synchronously here, BEFORE
        // any compose content is set, so the very first frame already uses
        // the user's chosen scheme -- no flash of the system default, and
        // the choice survives a real app relaunch.
        val initialThemeMode = ThemePreferences(applicationContext).load()

        setContent {
            // Hoisted out of AppRoot() so MyApplicationTheme below and
            // AppRoot's sheets share ONE FestoAppState instance: the
            // Settings sheet writes appState.themeMode, and the lambda
            // here recomposes the color scheme live in the same frame.
            val appState = rememberFestoAppState(initialThemeMode = initialThemeMode)
            val darkTheme = when (appState.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            MyApplicationTheme(darkTheme = darkTheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppRoot(appState = appState)
                }
            }
        }
    }
}
