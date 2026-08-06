package com.example.kpkn.services.workout

import com.example.kpkn.data.models.UnitModeV2
import com.example.kpkn.screens.workout.WorkoutVoiceField
import com.example.kpkn.screens.workout.WorkoutVoiceIntensityKind
import com.example.kpkn.screens.workout.WorkoutVoiceInterpretation
import com.example.kpkn.screens.workout.extractFirstVoiceDecimalNumber
import com.example.kpkn.screens.workout.extractFirstVoiceNumber
import com.example.kpkn.screens.workout.parseWorkoutVoiceTranscript

/**
 * Interpreta un turno de corrección durante la confirmación ("no, era 47.5" /
 * "no, eran ocho" / "no, era cien kilos" / re-dictado "82 por 8") como un
 * PATCH sobre el borrador pendiente, sin reemplazarlo ni perder campos.
 *
 * Pura y testeable: [buildCorrection] produce la corrección y [mergeCorrection]
 * la aplica sobre la interpretación pendiente.
 */
internal object WorkoutVoiceConfirmationCorrections {

    fun buildCorrection(
        draft: WorkoutVoiceInterpretation?,
        text: String,
        isTimeMode: Boolean,
        isUnilateral: Boolean,
        unitMode: UnitModeV2,
        customUnit: String?,
        trackRom: Boolean,
    ): WorkoutVoiceInterpretation? {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return null

        val full = parseWorkoutVoiceTranscript(
            transcript = trimmed,
            isTimeMode = isTimeMode,
            isUnilateral = isUnilateral,
            unitMode = unitMode,
            customUnit = customUnit,
            trackRom = trackRom,
        )
        if (full != null && full.fields.isNotEmpty()) return full

        WorkoutVoiceCommandParser.parseEditLastSet(trimmed)?.let { edit ->
            return editPatchToInterpretation(edit.patch, draft?.weightKg, trimmed)
        }

        return heuristicCorrection(trimmed)
    }

    fun mergeCorrection(
        base: WorkoutVoiceInterpretation,
        patch: WorkoutVoiceInterpretation,
    ): WorkoutVoiceInterpretation = base.copy(
        transcript = mergeTranscripts(base.transcript, patch.transcript),
        weightKg = patch.weightKg ?: base.weightKg,
        metricValue = patch.metricValue ?: base.metricValue,
        metricDecimalValue = patch.metricDecimalValue ?: base.metricDecimalValue,
        intensityValue = patch.intensityValue ?: base.intensityValue,
        intensityKind = patch.intensityKind ?: base.intensityKind,
        side = patch.side ?: base.side,
        reachedFailure = if (WorkoutVoiceField.FAILURE in patch.fields) patch.reachedFailure else base.reachedFailure,
        isFailedSet = if (WorkoutVoiceField.FAILED_SET in patch.fields) patch.isFailedSet else base.isFailedSet,
        helpedReps = patch.helpedReps ?: base.helpedReps,
        loadModeOverride = patch.loadModeOverride ?: base.loadModeOverride,
        dropSets = patch.dropSets.ifEmpty { base.dropSets },
        restPauses = patch.restPauses.ifEmpty { base.restPauses },
        romPercent = patch.romPercent ?: base.romPercent,
        fields = base.fields + patch.fields,
    )

    /** Une dos transcripts evitando duplicar tokens que ya aparecen en el primero. */
    fun mergeTranscripts(base: String, addition: String): String {
        val baseTokens = base.split(' ').filter { it.isNotBlank() }
        val addTokens = addition.split(' ').filter { it.isNotBlank() }
        if (baseTokens.isEmpty()) return addition.trim()
        if (addTokens.isEmpty()) return base.trim()
        val result = baseTokens.toMutableList()
        for (token in addTokens) {
            if (token !in result) result += token
        }
        return result.joinToString(" ")
    }

    private fun editPatchToInterpretation(
        patch: VoiceSetEditPatch,
        draftWeight: Double?,
        transcript: String,
    ): WorkoutVoiceInterpretation {
        val fields = buildSet {
            if (patch.weightKg != null || patch.weightDeltaKg != null) add(WorkoutVoiceField.WEIGHT)
            if (patch.metricValue != null) add(WorkoutVoiceField.VALUE)
            if (patch.intensityValue != null) add(WorkoutVoiceField.INTENSITY)
            if (patch.side != null) add(WorkoutVoiceField.SIDE)
        }
        val resolvedWeight = patch.weightKg
            ?: patch.weightDeltaKg?.let { delta -> (draftWeight ?: 0.0) + delta }
        return WorkoutVoiceInterpretation(
            transcript = transcript,
            weightKg = resolvedWeight,
            metricValue = patch.metricValue,
            metricDecimalValue = patch.metricValue?.toDouble(),
            intensityValue = patch.intensityValue,
            intensityKind = patch.intensityKind,
            side = patch.side,
            fields = fields,
        )
    }

    /**
     * Número suelto en contexto de corrección:
     * - "rir/rpe/reserva" → intensidad.
     * - con "rep/reps/repetición/repeticiones" → reps.
     * - decimal, o con "kilos/peso/carga/kg", o entero sin ninguna pista → peso
     *   (el peso es el campo que más se corrige; "no, eran ocho" sí llega por EditLastSet).
     */
    private fun heuristicCorrection(text: String): WorkoutVoiceInterpretation? {
        val value = extractFirstVoiceDecimalNumber(text) ?: extractFirstVoiceNumber(text)
            ?: return null
        val repWord = text.contains("rep")
        val weightWord = setOf("kilo", "kg", "peso", "carga").any { text.contains(it) }
        val isIntensity = text.contains("rir") || text.contains("rpe") || text.contains("reserva")
        val isDecimal = value % 1.0 != 0.0

        return when {
            isIntensity -> WorkoutVoiceInterpretation(
                transcript = text,
                intensityValue = value,
                intensityKind = if (text.contains("rir") || text.contains("reserva")) {
                    WorkoutVoiceIntensityKind.RIR
                } else {
                    WorkoutVoiceIntensityKind.RPE
                },
                fields = setOf(WorkoutVoiceField.INTENSITY),
            )
            repWord && !weightWord -> WorkoutVoiceInterpretation(
                transcript = text,
                metricValue = value.toInt(),
                metricDecimalValue = value,
                fields = setOf(WorkoutVoiceField.VALUE),
            )
            else -> WorkoutVoiceInterpretation(
                transcript = text,
                weightKg = value,
                fields = setOf(WorkoutVoiceField.WEIGHT),
            )
        }
    }
}
