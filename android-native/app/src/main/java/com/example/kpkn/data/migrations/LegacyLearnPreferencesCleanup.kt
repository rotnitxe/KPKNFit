package com.example.kpkn.data.migrations

import android.content.Context

/**
 * One-time removal of the retired Learn feature's local state.
 *
 * The marker is written only after the legacy preference file has been
 * synchronously cleared, so retries are safe after a process interruption.
 * Nutrition's `learned_resolutions` table is intentionally unrelated.
 */
object LegacyLearnPreferencesCleanup {
    private const val LEGACY_PREFS = "learn_prefs"
    private const val MIGRATION_PREFS = "kpkn_migrations"
    private const val MARKER = "learn_prefs_cleared_v1"

    fun clearOnce(context: Context): Boolean {
        val marker = context.getSharedPreferences(MIGRATION_PREFS, Context.MODE_PRIVATE)
        if (marker.getBoolean(MARKER, false)) return false

        val cleared = context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        if (!cleared) return false

        return marker.edit().putBoolean(MARKER, true).commit()
    }
}

fun clearLegacyLearnPreferencesOnce(context: Context): Boolean =
    LegacyLearnPreferencesCleanup.clearOnce(context)
