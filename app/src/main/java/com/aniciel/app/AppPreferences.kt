package com.aniciel.app

import android.content.Context

/**
 * Lightweight preferences. Profile is local-only (no account system).
 * Source enable/disable flags let users turn REFERENCE sources off until adapters exist.
 */
class AppPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("aniciel_preferences", Context.MODE_PRIVATE)

    var profileName: String
        get() = prefs.getString("profile_name", "Default") ?: "Default"
        set(value) = prefs.edit().putString("profile_name", value.trim().ifBlank { "Default" }).apply()

    var compactGrid: Boolean
        get() = prefs.getBoolean("compact_grid", false)
        set(value) = prefs.edit().putBoolean("compact_grid", value).apply()

    var playerBackend: String
        get() = prefs.getString("player_backend", PlayerBackend.EXO) ?: PlayerBackend.EXO
        set(v) = prefs.edit().putString("player_backend", v).apply()

    var adultEnabled: Boolean
        get() = prefs.getBoolean("adult_enabled", false)
        set(value) = prefs.edit().putBoolean("adult_enabled", value).apply()

    /** When true, show Continue Watching row on home screens. */
    var showContinueWatching: Boolean
        get() = prefs.getBoolean("show_continue_watching", true)
        set(value) = prefs.edit().putBoolean("show_continue_watching", value).apply()

    fun sourceEnabled(id: String, defaultValue: Boolean = true): Boolean =
        prefs.getBoolean("source_$id", defaultValue)

    fun setSourceEnabled(id: String, enabled: Boolean) {
        prefs.edit().putBoolean("source_$id", enabled).apply()
    }
}
