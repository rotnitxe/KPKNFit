package com.example.kpkn.data.preferences

import android.content.Context
import android.content.SharedPreferences
import com.example.kpkn.domain.exercises.VariantPreferenceStore
import com.example.kpkn.domain.storage.KeyValueStore
import com.example.kpkn.domain.training.ProgramSnapshotStore

/**
 * Adaptador de [KeyValueStore] sobre `SharedPreferences`.
 *
 * El archivo se abre de forma perezosa a propósito: `getSharedPreferences`
 * comprueba directorios en disco y la primera lectura espera la carga del
 * archivo, así que construir el almacén (que se hace desde Compose, en el hilo
 * principal) no toca el disco; el primer acceso real sí.
 */
class SharedPreferencesKeyValueStore(
    private val context: Context,
    private val fileName: String,
) : KeyValueStore {

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(fileName, Context.MODE_PRIVATE)
    }

    override fun getString(key: String): String? = prefs.getString(key, null)

    override fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    override fun putStringDurably(key: String, value: String): Boolean =
        prefs.edit().putString(key, value).commit()
}

/**
 * Nombres de archivo de preferencias. NO cambiar jamás: son los archivos donde
 * los usuarios ya tienen sus copias recuperables y preferencias de variante, y
 * cambiarlos las haría desaparecer sin ningún error. Los fija
 * `PreferenceStoreNamesTest`.
 */
object PreferenceFiles {
    const val PROGRAM_SNAPSHOTS = "program_structure_snapshots"
    const val VARIANT_PREFERENCES = "variant_preferences"
}

/** Copias recuperables del programa sobre `SharedPreferences` (archivo [PreferenceFiles.PROGRAM_SNAPSHOTS]). */
fun programSnapshotStore(context: Context): ProgramSnapshotStore =
    ProgramSnapshotStore(SharedPreferencesKeyValueStore(context, PreferenceFiles.PROGRAM_SNAPSHOTS))

/** Preferencias de variante sobre `SharedPreferences` (archivo [PreferenceFiles.VARIANT_PREFERENCES]). */
fun variantPreferenceStore(context: Context): VariantPreferenceStore =
    VariantPreferenceStore(SharedPreferencesKeyValueStore(context, PreferenceFiles.VARIANT_PREFERENCES))

/** Instancia compartida de la app (misma semántica que el antiguo `getInstance`). */
object PreferenceStores {
    @Volatile
    private var snapshots: ProgramSnapshotStore? = null

    @Volatile
    private var variants: VariantPreferenceStore? = null

    fun programSnapshots(context: Context): ProgramSnapshotStore =
        snapshots ?: synchronized(this) {
            snapshots ?: programSnapshotStore(context.applicationContext).also { snapshots = it }
        }

    fun variantPreferences(context: Context): VariantPreferenceStore =
        variants ?: synchronized(this) {
            variants ?: variantPreferenceStore(context.applicationContext).also { variants = it }
        }
}
