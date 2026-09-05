package com.example.kpkn.domain.competitions

import com.example.kpkn.data.competitions.PowerliftingFederationCatalog
import com.example.kpkn.data.models.CompetitionAttempt
import com.example.kpkn.data.models.CompetitionAttemptResult
import com.example.kpkn.data.models.CompetitionEquipment
import com.example.kpkn.data.models.CompetitionMovementType
import com.example.kpkn.data.models.CompetitionRecord
import com.example.kpkn.data.models.CompetitionRecordMode
import com.example.kpkn.data.models.CompetitionRecordStatus
import com.example.kpkn.data.models.CompetitionTechnicalBlock
import com.example.kpkn.data.models.CompetitionTemplateType
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.PowerliftingCompetitionDetails
import com.example.kpkn.domain.exercises.SmartExerciseCreator
import java.util.UUID

object PowerliftingWizardDraft {
    const val JUNK_TECHNICAL_TITLE = "Resultado técnico"

    fun emptyAttempts(): List<CompetitionAttempt> = (1..3).map { number ->
        CompetitionAttempt(
            id = UUID.randomUUID().toString(),
            attemptNumber = number,
            resultType = CompetitionAttemptResult.PENDING,
        )
    }

    fun emptySbdBlocks(): List<CompetitionTechnicalBlock> = listOf(
        block(CompetitionMovementType.SQUAT, "Sentadilla"),
        block(CompetitionMovementType.BENCH, "Press banca"),
        block(CompetitionMovementType.DEADLIFT, "Peso muerto"),
    )

    fun createEmpty(
        id: String = UUID.randomUUID().toString(),
        sexCategory: String? = null,
        bodyweightKg: Double? = null,
    ): CompetitionRecord = CompetitionRecord(
        id = id,
        title = "",
        sportType = CompetitionTemplateType.POWERLIFTING,
        recordMode = CompetitionRecordMode.TECHNICAL,
        status = CompetitionRecordStatus.PLANNED,
        reminderOneWeekEnabled = false,
        reminder48hEnabled = false,
        reminderStartEnabled = false,
        technicalBlocks = emptySbdBlocks(),
        journal = null,
        powerliftingDetails = PowerliftingCompetitionDetails(
            equipment = CompetitionEquipment.RAW,
            sexCategory = sexCategory,
        ),
        bodyweightKg = bodyweightKg,
    )

    fun ensureSbd(record: CompetitionRecord): CompetitionRecord {
        val existing = record.technicalBlocks.associateBy { it.movementType }
        val sbd = emptySbdBlocks().map { seed ->
            val current = existing[seed.movementType]
            if (current == null) {
                seed
            } else {
                current.copy(
                    title = seed.title,
                    attempts = if (current.attempts.isEmpty()) emptyAttempts() else current.attempts.take(3),
                )
            }
        }
        val extras = record.technicalBlocks.filterNot { it.movementType in sbdTypes }
        return record.copy(
            sportType = CompetitionTemplateType.POWERLIFTING,
            recordMode = CompetitionRecordMode.TECHNICAL,
            journal = null,
            reminderOneWeekEnabled = false,
            reminder48hEnabled = false,
            reminderStartEnabled = false,
            technicalBlocks = sbd + extras,
            powerliftingDetails = record.powerliftingDetails ?: PowerliftingCompetitionDetails(
                equipment = CompetitionEquipment.RAW,
            ),
        )
    }

    fun bindExercise(
        block: CompetitionTechnicalBlock,
        exercise: ExerciseMuscleInfo,
    ): CompetitionTechnicalBlock = block.copy(
        exerciseDbId = exercise.id,
        canonicalExerciseId = exercise.id,
        exerciseName = exercise.name,
    )

    fun createCustomExercise(
        name: String,
        catalog: List<ExerciseMuscleInfo>,
    ): ExerciseMuscleInfo {
        val created = SmartExerciseCreator.createAutomatic(name, catalog)
        require(CompetitionExerciseTypeahead.isCustomId(created.id)) {
            "SmartExerciseCreator must mint a custom: id, never a catalog id"
        }
        return created
    }

    fun applyFederation(
        record: CompetitionRecord,
        federationId: String?,
        customName: String? = null,
    ): CompetitionRecord {
        val fed = PowerliftingFederationCatalog.byId(federationId)
        val details = (record.powerliftingDetails ?: PowerliftingCompetitionDetails()).copy(
            pointsFormula = fed?.pointsFormula ?: PowerliftingPointsFormula.DOTS.id,
        )
        return record.copy(
            federationId = if (fed != null) fed.id else PowerliftingFederationCatalog.CUSTOM_ID,
            federation = fed?.name ?: customName?.trim()?.ifBlank { null },
            powerliftingDetails = details,
        )
    }

    fun derivedTitle(record: CompetitionRecord): String {
        val typed = record.title.trim()
        if (typed.isNotEmpty()) return typed
        val year = record.eventDate?.take(4).orEmpty()
        val fed = PowerliftingFederationCatalog.byId(record.federationId)?.shortName
            ?: record.federation?.trim().orEmpty()
        val base = fed.ifBlank { "Powerlifting" }
        return if (year.isBlank()) base else "$base $year"
    }

    fun applyPlace(record: CompetitionRecord, place: Int, trophyId: String?): CompetitionRecord {
        val honor = CompetitionPlaceHonors.fromPlacement(place, trophyId) ?: return record
        return record.copy(
            placement = CompetitionPlaceHonors.placementString(honor.place),
            medal = honor.medal?.id,
            trophyId = honor.trophy?.id,
        )
    }

    fun addExtraLift(record: CompetitionRecord): CompetitionRecord {
        val extra = block(CompetitionMovementType.PRESS, "Press")
        return record.copy(technicalBlocks = record.technicalBlocks + extra)
    }

    fun containsJunkPrefill(record: CompetitionRecord): Boolean =
        record.title.contains(JUNK_TECHNICAL_TITLE, ignoreCase = true) ||
            record.technicalBlocks.any { it.title.equals(JUNK_TECHNICAL_TITLE, ignoreCase = true) }

    private fun block(type: CompetitionMovementType, title: String) = CompetitionTechnicalBlock(
        id = UUID.randomUUID().toString(),
        title = title,
        movementType = type,
        attempts = emptyAttempts(),
    )

    private val sbdTypes = setOf(
        CompetitionMovementType.SQUAT,
        CompetitionMovementType.BENCH,
        CompetitionMovementType.DEADLIFT,
    )
}
