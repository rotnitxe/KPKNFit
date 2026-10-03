package com.example.kpkn.domain.storage

/**
 * Almacén de texto por clave, puro (sin `android.*`). `domain/` lo usa para
 * guardar copias y preferencias pequeñas; el adaptador con `SharedPreferences`
 * vive en `data/preferences`, que es quien decide los nombres de archivo.
 */
interface KeyValueStore {
    /** Valor guardado para [key], o null si no existe. */
    fun getString(key: String): String?

    /** Guarda [value] sin esperar al disco (equivale a `apply`). */
    fun putString(key: String, value: String)

    /**
     * Guarda [value] y espera a que quede escrito. Devuelve false si no se pudo
     * persistir (equivale a `commit`): quien lo llama decide cómo avisar.
     */
    fun putStringDurably(key: String, value: String): Boolean
}
