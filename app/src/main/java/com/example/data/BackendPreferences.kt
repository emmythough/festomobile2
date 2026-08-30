package com.example.data

import android.content.Context
import android.content.SharedPreferences
import java.util.UUID

/** Persisted Hermes gateway settings -- API key, base URL, and the
 * currently-selected shared Wendy session -- stored in the SAME
 * SharedPreferences file the theme override uses (festo_settings), so
 * app settings live in exactly one store, same as ThemePreferences.
 *
 * Security note: the Hermes API key lives in plain SharedPreferences
 * (app-private storage, no TLS on the wire). This app has no
 * EncryptedSharedPreferences or Keystore wrapper today; the honest
 * options were "reuse the existing store" or "add a security-crypto
 * dependency for one string". Reused. Revisit if/when the gateway gets
 * TLS.
 */
class BackendPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    init {
        // One-time migration from the old dual-backend build: releases
        // before the Hermes-only refactor persisted a backend_mode key
        // ("gen1" or "hermes"). The app is a Hermes client now -- there is
        // no mode to restore -- so the stale key is dropped on first
        // launch. Never strands a user: the Hermes gateway config below
        // (URL, API key, shared session) is carried over untouched, so a
        // former Gen-1 user lands straight in the gateway app.
        prefs.edit().remove(KEY_BACKEND_MODE).apply()
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
     * header. Generated once on first use, then kept forever -- rotating
     * it would just litter the gateway with duplicate sessions. */
    fun loadHermesSessionKey(): String {
        val existing = prefs.getString(KEY_HERMES_SESSION_KEY, null)?.takeIf { it.isNotBlank() }
        if (existing != null) return existing
        val generated = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_HERMES_SESSION_KEY, generated).apply()
        return generated
    }

    companion object {
        private const val PREFS_FILE = "festo_settings"

        /** Legacy key from the removed Gen 1 / Hermes mode switch. Not
         * written anymore; read never -- only removed once, above. */
        private const val KEY_BACKEND_MODE = "backend_mode"
        private const val KEY_HERMES_BASE_URL = "hermes_base_url"
        private const val KEY_HERMES_API_KEY = "hermes_api_key"
        private const val KEY_HERMES_SESSION_ID = "hermes_session_id"
        private const val KEY_HERMES_SESSION_KEY = "hermes_session_key"
    }
}
