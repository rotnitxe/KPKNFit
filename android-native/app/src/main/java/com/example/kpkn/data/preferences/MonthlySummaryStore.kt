package com.example.kpkn.data.preferences

import com.example.kpkn.domain.storage.KeyValueStore

/**
 * Resúmenes del mes ya vistos, guardados en [store] (archivo [PreferenceFiles.MONTHLY_SUMMARY]). Las claves (las que construye
 * `summaryKey`) van una por línea, así que no pueden llevar saltos de línea.
 */
class MonthlySummaryStore(private val store: KeyValueStore) {

    /** Claves de los resúmenes ya vistos; vacío si no hay ninguno. */
    fun seenKeys(): Set<String> = store.getString(SEEN)
        .orEmpty()
        .split('\n')
        .filter { it.isNotEmpty() }
        .toSet()

    /** Marca [key] como vista. No hace nada si ya lo estaba. */
    fun markSeen(key: String) {
        val seen = seenKeys()
        if (key in seen) return
        store.putString(SEEN, (seen + key).joinToString(separator = "\n"))
    }

    private companion object {
        const val SEEN = "seen"
    }
}
