package com.example.kpkn.domain.training

import com.example.kpkn.data.models.Program
import com.example.kpkn.domain.storage.KeyValueStore
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

@Serializable
data class ProgramSnapshot(
    val id: String,
    val programId: String,
    val program: Program,
    val savedAtMs: Long,
    val reason: String,
)

/**
 * Copias recuperables de la estructura de un programa (las últimas [MAX_VERSIONS]).
 *
 * El almacén de texto lo pone `data/preferences` (adaptador `SharedPreferences`,
 * archivo `program_structure_snapshots`), que lo abre de forma perezosa: el
 * constructor se invoca desde Compose (hilo principal) y el primer acceso real
 * ocurre en `list`/`push`/`restore`, que se llaman desde Dispatchers.IO (ver
 * ProgramDetailViewModel). La clave por programa (`program_<id>`) y el JSON no
 * cambian: son los datos que los usuarios ya tienen guardados.
 */
class ProgramSnapshotStore(private val store: KeyValueStore) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun list(programId: String): List<ProgramSnapshot> {
        if (programId.isBlank()) return emptyList()
        val raw = store.getString(keyFor(programId)) ?: return emptyList()
        return runCatching { json.decodeFromString<List<ProgramSnapshot>>(raw) }.getOrDefault(emptyList())
    }

    fun push(program: Program, reason: String): List<ProgramSnapshot> {
        if (program.id.isBlank()) return emptyList()
        val current = list(program.id)
        val snapshot = ProgramSnapshot(
            id = UUID.randomUUID().toString(),
            programId = program.id,
            program = program,
            savedAtMs = System.currentTimeMillis(),
            reason = reason,
        )
        val next = (current + snapshot).takeLast(MAX_VERSIONS)
        check(store.putStringDurably(keyFor(program.id), json.encodeToString(next))) {
            "No se pudo guardar la copia recuperable del programa. No se aplicaron cambios."
        }
        return next
    }

    fun restore(programId: String, snapshotId: String): Program? {
        return list(programId).firstOrNull { it.id == snapshotId }?.program
    }

    companion object {
        private const val MAX_VERSIONS = 10

        fun keyFor(programId: String) = "program_$programId"
    }
}
