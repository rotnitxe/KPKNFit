package com.example.kpkn.domain.competitions

import com.example.kpkn.data.competitions.PowerliftingFederationCatalog
import com.example.kpkn.data.models.CompetitionAttempt
import com.example.kpkn.data.models.CompetitionAttemptResult
import com.example.kpkn.data.models.CompetitionEquipment
import com.example.kpkn.data.models.CompetitionMovementType
import com.example.kpkn.data.models.CompetitionPhoto
import com.example.kpkn.data.models.CompetitionRecord
import com.example.kpkn.data.models.CompetitionRecordMode
import com.example.kpkn.data.models.CompetitionRecordStatus
import com.example.kpkn.data.models.CompetitionTechnicalBlock
import com.example.kpkn.data.models.CustomCompetitionMetric
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.PowerliftingCompetitionDetails
import com.example.kpkn.domain.exercises.SmartExerciseCreator
import java.util.UUID

/**
 * El borrador de una competencia en los flujos del lienzo Registros v4 (c-next, c-log, c-place, c-feel). Sustituye al asistente de
 * powerlifting: sirve a las 28 disciplinas y ya no borra el diario ni apaga los avisos al guardar (C-S13).
 */
object CompetitionDraft {
    /** Fotos y vídeos por competencia (el álbum de siempre). */
    const val MAX_MEDIA = 8

    /** Las estaciones de Hyrox (c-mixed): los ocho kilómetros y las ocho estaciones, en su orden. */
    val hyroxStations = listOf(
        "8 × 1 km", "SkiErg", "Sled push", "Sled pull", "Burpee broad jump", "Remo", "Farmers carry", "Sandbag lunges", "Wall balls",
    )

    fun emptyAttempts(): List<CompetitionAttempt> = (1..3).map { number ->
        CompetitionAttempt(id = UUID.randomUUID().toString(), attemptNumber = number, resultType = CompetitionAttemptResult.PENDING)
    }

    /** Las filas de la tabla de intentos de cada disciplina de carga (c-log, c-lift). */
    fun loadSeeds(discipline: CompetitionDiscipline): List<Pair<CompetitionMovementType, String>> = when (discipline) {
        CompetitionDiscipline.POWERLIFTING -> listOf(
            CompetitionMovementType.SQUAT to "Sentadilla",
            CompetitionMovementType.BENCH to "Press banca",
            CompetitionMovementType.DEADLIFT to "Peso muerto",
        )
        CompetitionDiscipline.WEIGHTLIFTING -> listOf(
            CompetitionMovementType.SNATCH to "Arrancada",
            CompetitionMovementType.CLEAN_AND_JERK to "Dos tiempos",
        )
        CompetitionDiscipline.CALISTHENICS -> listOf(
            CompetitionMovementType.CUSTOM to "Dominada lastrada",
            CompetitionMovementType.CUSTOM to "Fondo lastrado",
        )
        else -> emptyList()
    }

    /** Competencia nueva: «Voy a competir» (PLANNED) o «Ya competí» (resultado). Avisos como en c-next: 1 semana y 48 h encendidos. */
    fun createEmpty(
        discipline: CompetitionDiscipline,
        id: String = UUID.randomUUID().toString(),
        sexCategory: String? = null,
        bodyweightKg: Double? = null,
    ): CompetitionRecord {
        val base = CompetitionRecord(
            id = id,
            title = "",
            sportType = discipline.legacy,
            discipline = discipline.id,
            recordMode = CompetitionRecordMode.HYBRID,
            status = CompetitionRecordStatus.PLANNED,
            reminderOneWeekEnabled = true,
            reminder48hEnabled = true,
            reminderStartEnabled = false,
            bodyweightKg = bodyweightKg?.takeIf { discipline.hasWeighIn },
            powerliftingDetails = if (discipline == CompetitionDiscipline.POWERLIFTING) {
                PowerliftingCompetitionDetails(equipment = CompetitionEquipment.CLASSIC, sexCategory = sexCategory, division = "Open")
            } else null,
            customMetrics = if (discipline == CompetitionDiscipline.HYROX) {
                hyroxStations.map { CustomCompetitionMetric(id = UUID.randomUUID().toString(), label = it, value = "") }
            } else emptyList(),
        )
        return ensureBlocks(base, discipline)
    }

    /**
     * Asegura las filas de la tabla de intentos de [discipline] sin tocar lo ya escrito (intentos, diario, avisos y fotos se conservan).
     * Fuera de las disciplinas de carga deja el registro como está.
     */
    fun ensureBlocks(record: CompetitionRecord, discipline: CompetitionDiscipline): CompetitionRecord {
        val seeds = loadSeeds(discipline)
        if (seeds.isEmpty()) return record.copy(discipline = discipline.id, sportType = discipline.legacy)
        val remaining = record.technicalBlocks.toMutableList()
        val ordered = seeds.map { (movement, title) ->
            val match = remaining.firstOrNull { it.movementType == movement && (movement != CompetitionMovementType.CUSTOM || it.title == title) }
                ?: remaining.firstOrNull { it.movementType == movement && movement != CompetitionMovementType.CUSTOM }
            if (match != null) {
                remaining.remove(match)
                val attempts = (1..3).map { n ->
                    match.attempts.firstOrNull { it.attemptNumber == n }
                        ?: CompetitionAttempt(id = UUID.randomUUID().toString(), attemptNumber = n)
                }
                match.copy(attempts = attempts)
            } else {
                CompetitionTechnicalBlock(id = UUID.randomUUID().toString(), title = title, movementType = movement, attempts = emptyAttempts())
            }
        }
        return record.copy(
            discipline = discipline.id,
            sportType = discipline.legacy,
            recordMode = CompetitionRecordMode.HYBRID,
            technicalBlocks = ordered + remaining,
        )
    }

    /** «Cambiar movimiento» del menú largo del intento (C-D6): enlaza la fila con un ejercicio del catálogo o propio. */
    fun bindExercise(block: CompetitionTechnicalBlock, exercise: ExerciseMuscleInfo): CompetitionTechnicalBlock = block.copy(
        exerciseDbId = exercise.id,
        canonicalExerciseId = exercise.id,
        exerciseName = exercise.name,
    )

    fun createCustomExercise(name: String, catalog: List<ExerciseMuscleInfo>): ExerciseMuscleInfo {
        val created = SmartExerciseCreator.createAutomatic(name, catalog)
        require(CompetitionExerciseTypeahead.isCustomId(created.id)) { "SmartExerciseCreator must mint a custom: id, never a catalog id" }
        return created
    }

    fun applyFederation(record: CompetitionRecord, federationId: String?, customName: String? = null): CompetitionRecord {
        val fed = PowerliftingFederationCatalog.byId(federationId)
        val details = (record.powerliftingDetails ?: PowerliftingCompetitionDetails()).copy(
            pointsFormula = fed?.pointsFormula ?: PowerliftingPointsFormula.DOTS.id,
        )
        return record.copy(
            federationId = fed?.id ?: PowerliftingFederationCatalog.CUSTOM_ID,
            federation = fed?.name ?: customName?.trim()?.ifBlank { null },
            powerliftingDetails = details,
        )
    }

    /** El nombre que se guarda: el escrito o, si no hay, «Federación año» / «Disciplina año». */
    fun derivedTitle(record: CompetitionRecord, discipline: CompetitionDiscipline): String {
        val typed = record.title.trim()
        if (typed.isNotEmpty()) return typed
        val year = record.eventDate?.take(4).orEmpty()
        val fed = PowerliftingFederationCatalog.byId(record.federationId)?.shortName ?: record.federation?.trim().orEmpty()
        val base = fed.ifBlank { discipline.label }
        return if (year.isBlank()) base else "$base $year"
    }

    /** Añade fotos o vídeos hasta el máximo del álbum. */
    fun addMedia(record: CompetitionRecord, media: List<CompetitionPhoto>): CompetitionRecord =
        record.copy(photos = (record.photos + media).distinctBy { it.id }.take(MAX_MEDIA))

    /** Quita una foto de la lista; quien llama borra además el archivo (las quitadas no quedan huérfanas, c-feel). */
    fun removeMedia(record: CompetitionRecord, mediaId: String): CompetitionRecord =
        record.copy(photos = record.photos.filterNot { it.id == mediaId })

    /** El diario liviano de c-feel: la cara, «Lo mejor del día» y «Para la próxima». No borra el resto del diario. */
    fun withJournal(record: CompetitionRecord, feeling: String? = null, best: String? = null, next: String? = null): CompetitionRecord {
        val journal = record.journal ?: com.example.kpkn.data.models.CompetitionJournal()
        return record.copy(
            journal = journal.copy(
                overallFeeling = feeling ?: journal.overallFeeling,
                whatWentWell = best ?: journal.whatWentWell,
                learnings = next ?: journal.learnings,
            ),
        )
    }
}

/** Las cinco caras de «Cómo te sentiste» (c-feel), con la clave del icono del lienzo. */
enum class CompetitionFeeling(val id: String, val label: String, val icon: String) {
    BAD("mal", "Mal", "sad"),
    MEH("regular", "Regular", "meh"),
    GOOD("bien", "Bien", "smile"),
    GREAT("muy_bien", "Muy bien", "wow"),
    FIRE("en_llamas", "En llamas", "fire"),
    ;

    companion object {
        fun byId(id: String?): CompetitionFeeling? = entries.firstOrNull { it.id == id }
    }
}
