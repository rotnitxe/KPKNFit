package com.example.kpkn.domain.training

import com.example.kpkn.data.models.CompetitionMovementType
import com.example.kpkn.data.models.CompetitionRecord
import com.example.kpkn.data.models.CompetitionRecordMode
import com.example.kpkn.data.models.CompetitionRecordStatus
import com.example.kpkn.data.models.CompetitionTechnicalBlock
import com.example.kpkn.data.models.CompetitionTemplateType
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.isCompetitionMeet
import com.example.kpkn.domain.exercises.resolvedCanonicalExerciseId

/**
 * Sincroniza el plan de una sesión de competición hacia su [CompetitionRecord] enlazado.
 *
 * [CompetitionRecord] es la fuente de verdad (SSoT) para el evento, los intentos y la
 * bitácora. La sesión solo aporta metadatos de planificación (fecha, movimientos, formato).
 * Por eso este merge nunca pisa `attempts`, `bestValidWeightKg` ni `bestValidMark` de bloques
 * ya existentes, y preserva cualquier bloque técnico del record que no tenga un ejercicio
 * correspondiente en la sesión (p. ej. bloques SBD auto-generados o agregados manualmente
 * desde CompetitionScreen).
 */
object CompetitionSessionSync {

    fun merge(
        session: Session,
        existingRecord: CompetitionRecord?,
        programId: String,
        weekId: String,
    ): CompetitionRecord? {
        if (!session.isCompetitionMeet) return null
        val recordId = session.competitionRecordId ?: return null
        val details = session.competitionDetails

        val base = existingRecord ?: CompetitionRecord(
            id = recordId,
            title = session.name.ifBlank { "Competición" },
            eventDate = details?.competitionDate,
            sportType = session.competitionSportType ?: CompetitionTemplateType.CUSTOM,
            recordMode = session.competitionRecordMode ?: CompetitionRecordMode.HYBRID,
            status = CompetitionRecordStatus.PLANNED,
            plannedProgramId = programId,
            plannedSessionId = session.id,
            plannedWeekId = weekId,
            keyDateId = session.competitionKeyDateId,
        )

        val sessionBlocks = session.exercises.map { exercise ->
            val existingBlock = base.technicalBlocks.firstOrNull { block ->
                block.exerciseDbId == exercise.exerciseDbId ||
                    block.canonicalExerciseId == exercise.resolvedCanonicalExerciseId() ||
                    block.exerciseName.equals(exercise.name, ignoreCase = true)
            }
            CompetitionTechnicalBlock(
                id = existingBlock?.id ?: exercise.id,
                title = exercise.name.ifBlank { "Movimiento" },
                movementType = existingBlock?.movementType ?: CompetitionMovementType.CUSTOM,
                exerciseDbId = exercise.exerciseDbId ?: exercise.exerciseId,
                canonicalExerciseId = exercise.resolvedCanonicalExerciseId(),
                exerciseName = exercise.name,
                resultUnit = existingBlock?.resultUnit,
                attempts = existingBlock?.attempts.orEmpty(),
                bestValidWeightKg = existingBlock?.bestValidWeightKg,
                bestValidMark = existingBlock?.bestValidMark,
                notes = existingBlock?.notes,
            )
        }
        val sessionBlockIds = sessionBlocks.map { it.id }.toSet()
        val untouchedBlocks = base.technicalBlocks.filterNot { it.id in sessionBlockIds }
        val mergedBlocks = untouchedBlocks + sessionBlocks

        return base.copy(
            title = session.name.ifBlank { base.title },
            eventDate = details?.competitionDate ?: base.eventDate,
            startTime = details?.startTime ?: base.startTime,
            location = details?.location ?: base.location,
            federation = details?.federation ?: base.federation,
            category = details?.category ?: base.category,
            bodyweightKg = details?.targetBodyweightKg ?: session.meetBodyweight ?: base.bodyweightKg,
            notes = details?.strategyNotes ?: base.notes,
            sportType = session.competitionSportType ?: base.sportType,
            recordMode = session.competitionRecordMode ?: base.recordMode,
            plannedProgramId = base.plannedProgramId ?: programId,
            plannedSessionId = base.plannedSessionId ?: session.id,
            plannedWeekId = base.plannedWeekId ?: weekId,
            keyDateId = base.keyDateId ?: session.competitionKeyDateId,
            reminderOneWeekEnabled = details?.reminderOneWeekEnabled ?: base.reminderOneWeekEnabled,
            reminder48hEnabled = details?.reminder48hEnabled ?: base.reminder48hEnabled,
            reminderStartEnabled = details?.reminderStartEnabled ?: base.reminderStartEnabled,
            technicalBlocks = mergedBlocks,
        )
    }
}
