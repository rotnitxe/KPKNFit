package com.example.kpkn.domain.multisport.tracking

import com.example.kpkn.domain.multisport.SportId
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

/**
 * An extension of a native Session or WorkoutLog, never a second activity/log.
 * Unknown engines, versions, sport IDs and purposes remain recoverable JSON. Only a
 * registered, validating engine may interpret them or accept newly authored content.
 */
@Serializable
data class SportTrackingDocument(
    val id: String,
    val engineId: String,
    val version: Int,
    val sportId: String,
    val purpose: String,
    val payload: JsonObject,
    /** NONE: annotation only; ADDITIONAL_BLOCK: serial extra work; SESSION_TOTAL: complete session envelope. */
    val durationContribution: String = SportDurationContribution.NONE.name,
)

enum class SportDocumentPurpose { PLAN, RESULT, OBSERVATION }
enum class SportDurationContribution { NONE, ADDITIONAL_BLOCK, SESSION_TOTAL }

data class SportDocumentIssue(val path: String, val code: String)

interface SportDocumentEngine {
    val id: String
    val version: Int
    val sports: Set<SportId>
    val purposes: Set<SportDocumentPurpose>
    fun validate(payload: JsonObject, purpose: SportDocumentPurpose): List<SportDocumentIssue>
    /** Engines with a sport inside their payload must also bind it to the envelope. */
    fun validateForSport(payload: JsonObject, purpose: SportDocumentPurpose, sport: SportId): List<SportDocumentIssue> =
        validate(payload, purpose)

    /** An authored/estimated plan duration. Null is unavailable, never zero. */
    fun plannedDurationSeconds(payload: JsonObject): Long? = null

    /** Only a recorded whole-session duration, never a round, segment, moving time or sum inferred from marks. */
    fun recordedWholeSessionDurationSeconds(payload: JsonObject): Long? = null
}

sealed interface SportDocumentValidation {
    data object Valid : SportDocumentValidation
    data class InvalidMetadata(val issues: List<SportDocumentIssue>) : SportDocumentValidation
    data class UnsupportedEngine(val id: String) : SportDocumentValidation
    data class UnsupportedVersion(val engineId: String, val version: Int) : SportDocumentValidation
    data class InvalidPayload(val issues: List<SportDocumentIssue>) : SportDocumentValidation
}

data class SportDocumentCollectionIssue(val documentId: String, val validation: SportDocumentValidation)

/** Explicit registrations prevent a new sport name from masquerading as supported logic. */
class SportTrackingRegistry(engines: List<SportDocumentEngine>) {
    private val enginesById: Map<String, SportDocumentEngine>

    init {
        require(engines.all { it.id.isNotBlank() && it.version > 0 })
        require(engines.map { it.id }.distinct().size == engines.size)
        enginesById = engines.associateBy { it.id }
    }

    fun validate(document: SportTrackingDocument): SportDocumentValidation {
        val issues = buildList {
            if (document.id.isBlank()) add(SportDocumentIssue("id", "REQUIRED"))
            if (document.engineId.isBlank()) add(SportDocumentIssue("engineId", "REQUIRED"))
            if (document.version <= 0) add(SportDocumentIssue("version", "POSITIVE_REQUIRED"))
            if (document.sportId.isBlank()) add(SportDocumentIssue("sportId", "REQUIRED"))
            if (document.purpose.isBlank()) add(SportDocumentIssue("purpose", "REQUIRED"))
            if (SportDurationContribution.entries.none { it.name == document.durationContribution })
                add(SportDocumentIssue("durationContribution", "UNSUPPORTED_CONTRIBUTION"))
            if (document.purpose != SportDocumentPurpose.PLAN.name && document.durationContribution != SportDurationContribution.NONE.name)
                add(SportDocumentIssue("durationContribution", "PLAN_ONLY"))
        }
        if (issues.isNotEmpty()) return SportDocumentValidation.InvalidMetadata(issues)
        if (!document.payload.hasFiniteNumbers()) return SportDocumentValidation.InvalidPayload(
            listOf(SportDocumentIssue("payload", "NON_FINITE_NUMBER")),
        )
        val engine = enginesById[document.engineId]
            ?: return SportDocumentValidation.UnsupportedEngine(document.engineId)
        if (document.version != engine.version) {
            return SportDocumentValidation.UnsupportedVersion(document.engineId, document.version)
        }
        val sport = SportId.entries.firstOrNull { it.name == document.sportId }
        val purpose = SportDocumentPurpose.entries.firstOrNull { it.name == document.purpose }
        val mismatch = buildList {
            if (sport == null || sport !in engine.sports) add(SportDocumentIssue("sportId", "UNSUPPORTED_SPORT"))
            if (purpose == null || purpose !in engine.purposes) add(SportDocumentIssue("purpose", "UNSUPPORTED_PURPOSE"))
        }
        if (mismatch.isNotEmpty()) return SportDocumentValidation.InvalidMetadata(mismatch)
        val payloadIssues = try {
            engine.validateForSport(document.payload, requireNotNull(purpose), requireNotNull(sport))
        } catch (_: Exception) {
            listOf(SportDocumentIssue("payload", "CANNOT_DECODE"))
        }
        return if (payloadIssues.isEmpty()) SportDocumentValidation.Valid
        else SportDocumentValidation.InvalidPayload(payloadIssues)
    }

    private fun JsonElement.hasFiniteNumbers(): Boolean = when (this) {
        is JsonObject -> values.all { it.hasFiniteNumbers() }
        is JsonArray -> all { it.hasFiniteNumbers() }
        is JsonPrimitive -> isString || doubleOrNull?.isFinite() != false
        else -> true
    }

    fun validateAll(
        documents: List<SportTrackingDocument>,
        allowedPurposes: Set<SportDocumentPurpose>,
    ): List<SportDocumentCollectionIssue> = buildList {
        val duplicateIds = documents.groupingBy { it.id }.eachCount().filterValues { it > 1 }.keys
        documents.forEach { document ->
            val result = validate(document)
            if (result != SportDocumentValidation.Valid) add(SportDocumentCollectionIssue(document.id, result))
            if (document.id in duplicateIds) add(SportDocumentCollectionIssue(document.id,
                SportDocumentValidation.InvalidMetadata(listOf(SportDocumentIssue("id", "DUPLICATE")))))
            if (allowedPurposes.none { it.name == document.purpose }) add(SportDocumentCollectionIssue(document.id,
                SportDocumentValidation.InvalidMetadata(listOf(SportDocumentIssue("purpose", "WRONG_OWNER")))))
        }
    }

    fun plannedDurationSeconds(document: SportTrackingDocument): Long? {
        if (document.purpose != SportDocumentPurpose.PLAN.name || validate(document) != SportDocumentValidation.Valid) return null
        return try {
            enginesById.getValue(document.engineId).plannedDurationSeconds(document.payload)?.takeIf { it > 0 }
        } catch (_: Exception) { null }
    }

    fun recordedWholeSessionDurationSeconds(document: SportTrackingDocument): Long? {
        if (document.purpose != SportDocumentPurpose.RESULT.name || validate(document) != SportDocumentValidation.Valid) return null
        return try {
            enginesById.getValue(document.engineId).recordedWholeSessionDurationSeconds(document.payload)?.takeIf { it > 0 }
        } catch (_: Exception) { null }
    }
}
