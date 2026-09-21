package com.example.kpkn.data.onboarding

import com.example.kpkn.data.db.KpknDatabase
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

enum class SetupDraftScope { FULL, TRAINING_ONLY, NUTRITION_ONLY, RINGS_ONLY, LEGACY_RESUME, UNKNOWN }

data class SetupDraftCandidate(
    val draftId: String,
    val scope: SetupDraftScope,
    val revision: Long,
    val updatedAtEpochMs: Long,
    val catalogRevision: String?,
)

/** Resolves the real persisted draft before navigation; RESUME is not a storage scope. */
class SetupDraftResolver(private val db: KpknDatabase) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun listRecoverable(): List<SetupDraftCandidate> = db.setupDraftDao().getAllDrafts()
        .map { entity ->
            val scopeText = runCatching {
                json.parseToJsonElement(entity.payloadJson).jsonObject["draftScope"]?.jsonPrimitive?.content
            }.getOrNull()
            SetupDraftCandidate(
                draftId = entity.draftId,
                scope = scopeOf(scopeText ?: entity.draftId.substringAfterLast(':')),
                revision = entity.revision,
                updatedAtEpochMs = entity.updatedAtEpochMs,
                catalogRevision = entity.catalogRevision,
            )
        }
        .sortedByDescending { it.updatedAtEpochMs }

    suspend fun resolve(draftId: String?): SetupDraftCandidate? = listRecoverable().firstOrNull { it.draftId == draftId }

    companion object {
        fun scopeOf(value: String): SetupDraftScope = when (value.trim().lowercase()) {
            "full", "setup-wizard:full" -> SetupDraftScope.FULL
            "training_only", "training-only", "setup-wizard:training_only" -> SetupDraftScope.TRAINING_ONLY
            "nutrition_only", "nutrition-only", "setup-wizard:nutrition_only" -> SetupDraftScope.NUTRITION_ONLY
            "rings_only", "rings-only", "setup-wizard:rings_only" -> SetupDraftScope.RINGS_ONLY
            "resume", "setup-wizard:resume" -> SetupDraftScope.LEGACY_RESUME
            else -> SetupDraftScope.UNKNOWN
        }

        fun canonicalDraftId(scope: SetupDraftScope): String = when (scope) {
            SetupDraftScope.FULL, SetupDraftScope.LEGACY_RESUME, SetupDraftScope.UNKNOWN -> "setup-wizard:full"
            SetupDraftScope.TRAINING_ONLY -> "setup-wizard:training_only"
            SetupDraftScope.NUTRITION_ONLY -> "setup-wizard:nutrition_only"
            SetupDraftScope.RINGS_ONLY -> "setup-wizard:rings_only"
        }
    }
}
