package com.example.kpkn.data.models

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
enum class SharedLoadReferenceKind { ONE_RM, TRAINING_MAX, ESTIMATED_PR, OBSERVED_SESSION }

@Serializable
data class ComparableCapacityObservation(
    val sessionId: String,
    val observedAtMs: Long,
    val capacityKg: Double,
)

/** A projection source for suggestions. It is never a workout record or a rewrite of an observation. */
@Serializable
data class SharedExerciseLoadReference(
    val id: String,
    val exerciseKey: String,
    val configurationId: String,
    val tagId: String?,
    val unitMode: UnitModeV2,
    val loadMode: LoadModeV2,
    val quantityConvention: LoadQuantityConvention,
    val kind: SharedLoadReferenceKind,
    /** Immutable user input or original PR estimate; only edited explicitly. */
    val baseValueKg: Double,
    val sourceAtMs: Long,
    val sourceId: String? = null,
    val prWeightKg: Double? = null,
    val prReps: Int? = null,
    val effortMode: PrEffortMode? = null,
    val effortValue: Double? = null,
    val comparableObservations: List<ComparableCapacityObservation> = emptyList(),
) {
    fun suggestedValueKg(): Double {
        if (kind != SharedLoadReferenceKind.ESTIMATED_PR) return baseValueKg
        val values = comparableObservations.asSequence()
            .filter { it.observedAtMs > sourceAtMs && it.capacityKg.isFinite() && it.capacityKg > 0.0 }
            .distinctBy { it.sessionId }
            .sortedByDescending { it.observedAtMs }
            .take(3)
            .map { it.capacityKg }
            .sorted()
            .toList()
        if (values.size < 2) return baseValueKg
        val middle = values.size / 2
        return if (values.size % 2 == 1) values[middle] else (values[middle - 1] + values[middle]) / 2.0
    }

    fun withComparableObservation(observation: ComparableCapacityObservation): SharedExerciseLoadReference {
        if (kind !in setOf(SharedLoadReferenceKind.ESTIMATED_PR, SharedLoadReferenceKind.OBSERVED_SESSION) ||
            kind == SharedLoadReferenceKind.ESTIMATED_PR && observation.observedAtMs <= sourceAtMs ||
            observation.sessionId.isBlank() || !observation.capacityKg.isFinite() || observation.capacityKg <= 0.0
        ) return this
        val latest = (comparableObservations.filterNot { it.sessionId == observation.sessionId } + observation)
            .sortedByDescending { it.observedAtMs }
            .let { observations ->
                if (kind == SharedLoadReferenceKind.ESTIMATED_PR) observations.filter { it.observedAtMs > sourceAtMs }.take(3)
                else observations
            }
        return copy(comparableObservations = latest)
    }

    fun sameIdentity(other: SharedExerciseLoadReference): Boolean =
        exerciseKey == other.exerciseKey && configurationId == other.configurationId && tagId == other.tagId &&
            unitMode == other.unitMode && loadMode == other.loadMode && quantityConvention == other.quantityConvention &&
            kind == other.kind
}

fun sharedLoadReferenceId(
    exerciseKey: String,
    configurationId: String,
    tagId: String?,
    unitMode: UnitModeV2,
    loadMode: LoadModeV2,
    quantityConvention: LoadQuantityConvention,
    kind: SharedLoadReferenceKind,
): String = UUID.nameUUIDFromBytes(
    listOf(exerciseKey, configurationId, tagId.orEmpty(), unitMode.name, loadMode.name,
        quantityConvention.name, kind.name).joinToString("|").toByteArray(Charsets.UTF_8),
).toString()
