package com.example.kpkn.domain.training

import android.content.Context
import android.content.SharedPreferences
import com.example.kpkn.data.models.Program
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

class ProgramSnapshotStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun list(programId: String): List<ProgramSnapshot> {
        if (programId.isBlank()) return emptyList()
        val raw = prefs.getString(keyFor(programId), null) ?: return emptyList()
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
        prefs.edit().putString(keyFor(program.id), json.encodeToString(next)).commit()
        return next
    }

    fun restore(programId: String, snapshotId: String): Program? {
        return list(programId).firstOrNull { it.id == snapshotId }?.program
    }

    companion object {
        private const val PREFS_NAME = "program_structure_snapshots"
        private const val MAX_VERSIONS = 10

        @Volatile
        private var instance: ProgramSnapshotStore? = null

        fun getInstance(context: Context): ProgramSnapshotStore {
            return instance ?: synchronized(this) {
                instance ?: ProgramSnapshotStore(context.applicationContext).also { instance = it }
            }
        }

        fun keyFor(programId: String) = "program_$programId"
    }
}
