package com.example.kpkn.data.workout

import android.content.Context
import android.content.SharedPreferences

/**
 * Récord del minijuego del descanso (LV6, F8): un solo entero, el mayor número de saltos seguidos. Vive en un archivo de
 * preferencias propio y nunca en Room ni en el log del entreno: es un pasatiempo, no un dato de la sesión.
 */
class RestGameRecordStore(private val prefs: SharedPreferences) {

    /** El récord guardado (0 si todavía no hay). */
    fun best(): Int = prefs.getInt(KEY_BEST, 0).coerceAtLeast(0)

    /** Guarda [score] si supera el récord. Devuelve `true` si lo superó. */
    fun submit(score: Int): Boolean {
        if (score <= best()) return false
        prefs.edit().putInt(KEY_BEST, score).apply()
        return true
    }

    companion object {
        const val FILE_NAME = "rest_game"
        const val KEY_BEST = "best_jumps"

        fun from(context: Context): RestGameRecordStore =
            RestGameRecordStore(context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE))
    }
}
