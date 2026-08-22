package com.example.kpkn.data.secure

import android.content.Context

/**
 * Removes credentials left by versions that exposed a remote nutrition provider.
 * It intentionally knows only the old preference container; no client, key
 * handling or network provider remains active in the app.
 */
object LegacyAiCredentialCleanup {
    private const val LEGACY_PREFS = "kpkn_secure_deepseek"

    fun clear(context: Context) {
        runCatching {
            context.applicationContext
                .getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit()
        }
    }
}
