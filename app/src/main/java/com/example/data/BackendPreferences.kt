package com.example.data

import android.content.Context
import android.content.SharedPreferences
import java.util.UUID

/** Which backend the app talks to. Gen 1 is Wendy's own mobile_api.py
 * (the original direct connection, shared model selection with Telegram);
 * Hermes is the gateway Telegram's brain serves through (see HermesApi.kt).
 * The storage key is the exact string written to the persisted settings
 * file, so values must stay stable across releases (a renamed key would
 * silently flip users back to the default). GEN1 is the default on
 * purpose: adding Hermes must never change behavior for someone who
 * hasn't opted in. */
enum class BackendMode(val storageKey: String, val label: String) {
    GEN1("gen1", "Gen 1"),
    HERMES("hermes", "Hermes");

    companion object {
        fun fromStorageKey(key: String?): BackendMode =
            entries.firstOrNull { it.storageKey == key } ?: GEN1
    }
}

/** Persisted Hermes gateway settings -- API key, base URL, and the
 * currently-selected shared Wendy session -- stored in the SAME
 * SharedPreferences file the theme override uses (festo_settings), so
 * app settings live in exactly one store, same as ThemePreferences.
 *
 * Security note, matching the tradeoff already accepted for the Gen 1
 * token: the Hermes API key lives in plain SharedPreferences (app-private
 * storage, no TLS on the wire). This app has no EncryptedSharedPreferences
 * or Keystore wrapper today; the honest options were "reuse the existing
 * store" or "add a security-crypto dependency for one string". Reused.
 * Revisit both if/when the gateway gets TLS.
 */
class BackendPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    fun loadMode(): BackendMode = BackendMode.fromStorageKey(prefs.getString(KEY_BACKEND_MODE, null))

    fun saveMode(mode: BackendMode) {
        prefs.edit().putString(KEY_BACKEND_MODE, mode.storageKey).apply()
    }

    fun loadHermesBaseUrl(): String =
        prefs.getString(KEY_HERMES_BASE_URL, null)?.ifBlank { null } ?: HermesApi.DEFAULT_BASE_URL

    fun saveHermesBaseUrl(url: String) {
        prefs.edit().putString(KEY_HERMES_BASE_URL, url).apply()
    }

    fun loadHermesApiKey(): String =
        prefs.getString(KEY_HERMES_API_KEY, null)?.ifBlank { null } ?: ""

    fun saveHermesApiKey(key: String) {
        prefs.edit().putString(KEY_HERMES_API_KEY, key).apply()
    }

    /** The shared Wendy session the app chats inside (must be the SAME
     * session Telegram uses -- the picker in Settings writes it). Null
     * until the user picks one; chat refuses to send until then. */
    fun loadHermesSessionId(): String? =
        prefs.getString(KEY_HERMES_SESSION_ID, null)?.ifBlank { null }

    fun saveHermesSessionId(id: String?) {
        val editor = prefs.edit()
        if (id.isNullOrBlank()) editor.remove(KEY_HERMES_SESSION_ID) else editor.putString(KEY_HERMES_SESSION_ID, id)
        editor.apply()
    }

    /** Stable per-install key for the optional X-Hermes-Session-Key
     * header. Generated once on first Hermes use, then kept forever --
     * rotating it would just litter the gateway with duplicate sessions. */
    fun loadHermesSessionKey(): String {
        val existing = prefs.getString(KEY_HERMES_SESSION_KEY, null)?.takeIf { it.isNotBlank() }
        if (existing != null) return existing
        val generated = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_HERMES_SESSION_KEY, generated).apply()
        return generated
    }

    companion object {
        private const val PREFS_FILE = "festo_settings"
        private const val KEY_BACKEND_MODE = "backend_mode"
        private const val KEY_HERMES_BASE_URL = "hermes_base_url"
        private const val KEY_HERMES_API_KEY = "hermes_api_key"
        private const val KEY_HERMES_SESSION_ID = "hermes_session_id"
        private const val KEY_HERMES_SESSION_KEY = "hermes_session_key"
    }
}
