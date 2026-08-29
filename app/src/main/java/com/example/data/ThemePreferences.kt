package com.example.data

import android.content.Context
import android.content.SharedPreferences

/** User-selectable app theme. The storage key is the exact string written
 * to the persisted settings file, so the values must stay stable across
 * releases (a renamed key would silently reset users back to SYSTEM). */
enum class ThemeMode(val storageKey: String, val label: String) {
    SYSTEM("system", "System"),
    LIGHT("light", "Light"),
    DARK("dark", "Dark");

    companion object {
        fun fromStorageKey(key: String?): ThemeMode =
            entries.firstOrNull { it.storageKey == key } ?: SYSTEM
    }
}

/** Single owner of the app's persisted settings file. FestoAppState reads
 * the initial theme through it (via MainActivity, before any compose
 * content exists) and writes changes through it, so the persistence
 * logic exists in exactly one place. Plain SharedPreferences rather than
 * DataStore: DataStore is not an active dependency in this project (it is
 * commented out in app/build.gradle.kts) and this store is a single
 * string key -- adding a library for that isn't justified. */
class ThemePreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    fun load(): ThemeMode = ThemeMode.fromStorageKey(prefs.getString(KEY_THEME_MODE, null))

    fun save(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.storageKey).apply()
    }

    companion object {
        private const val PREFS_FILE = "festo_settings"
        private const val KEY_THEME_MODE = "theme_mode"
    }
}
