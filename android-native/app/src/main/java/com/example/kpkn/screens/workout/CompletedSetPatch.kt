package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.SetTechniqueV2
import com.example.kpkn.data.models.UnitModeV2
import com.example.kpkn.services.workout.VoiceSetEditPatch
import kotlin.math.roundToInt

/** null conserva el campo; cero parciales las borra. El conteo nunca altera segundos ni distancia. */
internal data class CompletedSetPatch(
    val edit: VoiceSetEditPatch = VoiceSetEditPatch(),
    val partialReps: Int? = null,
    val reachedFailure: Boolean? = null,
)

/** Valor lógico usado por el registro y la deuda de repeticiones. */
internal fun logicalReps(full: Double, partials: Int?): Double = full + (partials ?: 0).coerceAtLeast(0) * 0.5

/**
 * Transformación pura compartida por el candidato de Room y su publicación después del commit.
 * Reutiliza la corrección de voz; normaliza también cero completas con parciales y fallo muscular.
 * Conserva los RM estimados, los PR y las puntuaciones calculadas al registrar, igual que la voz.
 * El fallo muscular no convierte la serie en error de ejecución (isFailedSet).
 */
internal fun applyCompletedSetPatch(current: CompletedSet, patch: CompletedSetPatch): CompletedSet {
    val unit = current.recordedPayloadV3?.unitMode ?: when {
        current.timeSeconds != null -> UnitModeV2.TIME
        current.distanceKm != null -> UnitModeV2.DISTANCE
        else -> UnitModeV2.REPS
    }
    val canEditReps = unit == UnitModeV2.REPS
    val requestedFull = patch.edit.metricValue?.takeIf { canEditReps && it >= 0 }
    val editPartials = canEditReps && patch.partialReps != null
    val hasRepEdit = requestedFull != null || editPartials
    val full = requestedFull ?: current.reps
    val partials = (if (editPartials) patch.partialReps else current.partialReps)?.coerceAtLeast(0)
    // Cero completas con parciales es válido; una corrección vacía conserva el registro original.
    if (hasRepEdit && full <= 0 && (partials ?: 0) == 0) return current
    val edit = patch.edit.copy(metricValue = requestedFull?.takeIf { it > 0 })
    var next = applyVoiceEditPatch(current, edit)
    if (hasRepEdit) {
        val isPartial = (partials ?: 0) > 0
        val logical = logicalReps(full.toDouble(), partials)
        fun List<SetTechniqueV2>.withPartials() = filterNot { it == SetTechniqueV2.PARTIALS } +
            if (isPartial) listOf(SetTechniqueV2.PARTIALS) else emptyList()
        next = next.copy(
            reps = full,
            partialReps = partials?.takeIf { it > 0 },
            isPartial = isPartial,
            recordedPayloadV3 = next.recordedPayloadV3?.let {
                it.copy(completedReps = full, partialReps = partials?.takeIf { count -> count > 0 }, techniques = it.techniques.withPartials())
            },
            homologatedResultV3 = next.homologatedResultV3?.let {
                it.copy(actualValue = logical, augeEquivalentReps = logical.roundToInt(), techniques = it.techniques.withPartials())
            },
            setOutcomeV2 = next.setOutcomeV2?.let {
                it.copy(actualValue = logical, augeEquivalentReps = logical.roundToInt(), techniques = it.techniques.withPartials())
            },
        )
    }
    patch.reachedFailure?.let { failure ->
        val clearFailureIntensity = !failure && next.actualIntensityMode == IntensityMode.FAILURE
        val mode = if (failure) IntensityMode.FAILURE else next.actualIntensityMode.takeUnless { clearFailureIntensity }
        val intensity = if (failure || clearFailureIntensity) null else next.actualIntensityValue
        fun List<SetTechniqueV2>.withFailure() = filterNot { it == SetTechniqueV2.FAILURE } +
            if (failure) listOf(SetTechniqueV2.FAILURE) else emptyList()
        next = next.copy(
            isFailure = failure,
            rpe = if (failure || clearFailureIntensity) null else next.rpe,
            rir = if (failure || clearFailureIntensity) null else next.rir,
            actualIntensityMode = mode,
            actualIntensityValue = intensity,
            recordedPayloadV3 = next.recordedPayloadV3?.let {
                it.copy(reachedFailure = failure, actualIntensityMode = mode, actualIntensityValue = intensity, techniques = it.techniques.withFailure())
            },
            homologatedResultV3 = next.homologatedResultV3?.let {
                it.copy(reachedFailure = failure, actualIntensity = if (failure || clearFailureIntensity) null else it.actualIntensity, techniques = it.techniques.withFailure())
            },
            setOutcomeV2 = next.setOutcomeV2?.let {
                it.copy(reachedFailure = failure, actualIntensity = if (failure || clearFailureIntensity) null else it.actualIntensity, techniques = it.techniques.withFailure())
            },
        )
    }
    return if (next == current) current else next
}
