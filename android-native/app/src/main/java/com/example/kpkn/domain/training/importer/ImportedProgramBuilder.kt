package com.example.kpkn.domain.training.importer

import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramSchedulePlan
import com.example.kpkn.data.models.ProgramStructure
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.RepRange
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SessionOrigin
import com.example.kpkn.data.models.TrainingMode
import com.example.kpkn.data.protocols.LoadBasis
import com.example.kpkn.data.protocols.PlanProvenance
import com.example.kpkn.data.protocols.PlanProvenanceClass
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseConfigurationV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseDefinitionV2
import kotlin.math.roundToInt

/**
 * Construye un [Program] desde el borrador importado. Una semana → SIMPLE; varias → COMPLEX con un bloque.
 *
 * - Sesiones repartidas por los días de entreno dados (ISO: 1 = lunes), por igual si caben.
 * - [resolved] asocia cada nombre importado a una configuración del catálogo (o null). Con [catalog], un ejercicio
 *   resuelto lleva la identidad v2 que pone el editor; sin catálogo, o si la configuración no existe, se guarda con
 *   su nombre libre y sin identidad de catálogo.
 * - Procedencia: `planProvenance` con planId «imported:<programId>» y clase IMPORTED («Programa importado»; no se atribuye autoría).
 */
object ImportedProgramBuilder {
    const val DEFAULT_TITLE = "Mi programa importado"

    fun build(
        draft: ImportedProgramDraft,
        resolved: Map<String, String?>,
        weekdays: List<Int>,
        programId: String,
        catalog: ExerciseCatalogV2? = null,
    ): Program {
        val trainingDays = weekdays.filter { it in 1..7 }.distinct().sorted()
        val configurations = catalog?.let { configurationIndex(it) }.orEmpty()
        val revision = catalog?.catalogRevision
        val weeks = draft.weeks.sortedBy { it.index }.map { week ->
            val weekdayPerSession = assignWeekdays(week.days.size, trainingDays)
            ProgramWeek(
                id = "$programId-w${week.index}",
                name = "Semana ${week.index}",
                sessions = week.days.mapIndexed { dayIndex, day ->
                    buildSession(
                        programId = programId,
                        weekIndex = week.index,
                        dayIndex = dayIndex,
                        day = day,
                        weekday = weekdayPerSession[dayIndex],
                        loadUnit = draft.loadUnit,
                        resolved = resolved,
                        configurations = configurations,
                        revision = revision,
                    )
                },
            )
        }
        val block = Block(
            id = "$programId-block",
            name = "Bloque importado",
            mesocycles = listOf(Mesocycle(id = "$programId-meso", name = "Importado", weeks = weeks)),
        )
        return Program(
            id = programId,
            name = draft.title.trim().ifEmpty { DEFAULT_TITLE },
            structure = if (weeks.size <= 1) ProgramStructure.SIMPLE else ProgramStructure.COMPLEX,
            macrocycles = listOf(Macrocycle(id = "$programId-macro", name = "Mi plan", blocks = listOf(block))),
            startDay = trainingDays.firstOrNull(),
            schedulePlan = if (trainingDays.isEmpty()) {
                null
            } else {
                ProgramSchedulePlan(weekStartDay = trainingDays.first(), trainingDays = trainingDays.toSet())
            },
            planProvenance = PlanProvenance(planId = "imported:$programId", category = PlanProvenanceClass.IMPORTED),
        )
    }

    /** Día ISO de cada sesión de una semana: repartidas por igual si caben; si sobran sesiones, en círculo. */
    internal fun assignWeekdays(sessionCount: Int, trainingDays: List<Int>): List<Int?> {
        if (sessionCount <= 0) return emptyList()
        if (trainingDays.isEmpty()) return List(sessionCount) { null }
        val available = trainingDays.size
        return List(sessionCount) { index ->
            if (sessionCount <= available) {
                val slot = if (sessionCount == 1) {
                    0
                } else {
                    (index * (available - 1).toDouble() / (sessionCount - 1)).roundToInt()
                }
                trainingDays[slot]
            } else {
                trainingDays[index % available]
            }
        }
    }

    private fun buildSession(
        programId: String,
        weekIndex: Int,
        dayIndex: Int,
        day: ImportedDay,
        weekday: Int?,
        loadUnit: ImportLoadUnit,
        resolved: Map<String, String?>,
        configurations: Map<String, Pair<ExerciseDefinitionV2, ExerciseConfigurationV2>>,
        revision: String?,
    ): Session {
        val sessionId = "$programId-w$weekIndex-d${dayIndex + 1}"
        return Session(
            id = sessionId,
            name = day.name.ifBlank { "Día ${dayIndex + 1}" },
            // Los ejercicios seguidos con el mismo nombre son un solo ejercicio de la app (varios grupos de series).
            exercises = groupConsecutive(day.exercises).mapIndexed { exerciseIndex, members ->
                buildExercise(
                    id = "$sessionId-e${exerciseIndex + 1}",
                    members = members,
                    loadUnit = loadUnit,
                    configurationId = resolved[members.first().rawName],
                    configurations = configurations,
                    revision = revision,
                )
            },
            dayOfWeek = weekday,
            assignedDays = listOfNotNull(weekday),
            origin = SessionOrigin.USER_DRAFT,
        )
    }

    /** Ejercicio de la app a partir de uno o varios grupos de series seguidos (en orden, con sus propias cifras). */
    private fun buildExercise(
        id: String,
        members: List<ImportedExercise>,
        loadUnit: ImportLoadUnit,
        configurationId: String?,
        configurations: Map<String, Pair<ExerciseDefinitionV2, ExerciseConfigurationV2>>,
        revision: String?,
    ): Exercise {
        val entry = configurationId?.let { configurations[it] }
        val definition = entry?.first
        val configuration = entry?.second
        val catalogConfigurationId = configuration?.id
        return Exercise(
            id = id,
            name = definition?.canonicalName ?: members.first().rawName,
            exerciseDbId = catalogConfigurationId,
            exerciseId = catalogConfigurationId,
            canonicalExerciseId = catalogConfigurationId?.lowercase(),
            exerciseFamilyId = catalogConfigurationId?.lowercase(),
            catalogRevision = if (catalogConfigurationId != null) revision else null,
            catalogDefinitionId = definition?.id,
            catalogConfigurationId = catalogConfigurationId,
            performanceProfileId = configuration?.profile?.performanceProfileId,
            trainingMode = TrainingMode.REPS,
            restTime = members.firstNotNullOfOrNull { it.restSeconds },
            sets = buildSets(id, members, loadUnit),
            executionCues = members.mapNotNull { it.notes }.distinct(),
        )
    }

    private fun buildSets(exerciseId: String, members: List<ImportedExercise>, loadUnit: ImportLoadUnit): List<ExerciseSet> =
        members.flatMap { exercise -> setsOf(exercise, loadUnit) }
            .mapIndexed { index, set -> set.copy(id = "$exerciseId-s${index + 1}") }

    /** Series de un grupo, sin id (lo pone [buildSets]); la carga pasa a kilos según la unidad del borrador. */
    private fun setsOf(exercise: ImportedExercise, loadUnit: ImportLoadUnit): List<ExerciseSet> {
        val low = exercise.repsMin.coerceAtLeast(1)
        val high = exercise.repsMax.coerceAtLeast(low)
        val percent = exercise.percent1Rm
        val intensity = when {
            exercise.rpe != null -> IntensityMode.RPE
            exercise.rir != null -> IntensityMode.RIR
            percent != null -> IntensityMode.SOLO_RM
            exercise.loadKg != null -> IntensityMode.LOAD
            else -> null
        }
        val weight = exercise.loadKg?.let { kilograms(it, loadUnit) }
        return List(exercise.sets.coerceAtLeast(1)) {
            ExerciseSet(
                id = "",
                targetReps = high,
                targetRepsRange = RepRange(low, high),
                targetRPE = exercise.rpe,
                targetRIR = exercise.rir,
                intensityMode = intensity,
                targetPercentageRM = percent,
                weight = weight,
                loadBasis = if (percent != null) LoadBasis.PERCENT_1RM else null,
            )
        }
    }

    /** Carga en kilos: con libras, al 0,5 kg más cercano (135 lb → 61 kg). */
    internal fun kilograms(value: Double, unit: ImportLoadUnit): Double =
        if (unit == ImportLoadUnit.LB) (value * LB_TO_KG * 2).roundToInt() / 2.0 else value

    /** Agrupa los ejercicios seguidos con el mismo nombre; si el nombre vuelve más abajo, separado por otro, es otro grupo. */
    internal fun groupConsecutive(exercises: List<ImportedExercise>): List<List<ImportedExercise>> {
        val groups = mutableListOf<MutableList<ImportedExercise>>()
        for (exercise in exercises) {
            val last = groups.lastOrNull()
            if (last != null && last.first().rawName == exercise.rawName) {
                last += exercise
            } else {
                groups += mutableListOf(exercise)
            }
        }
        return groups
    }

    private const val LB_TO_KG = 0.45359237

    private fun configurationIndex(
        catalog: ExerciseCatalogV2,
    ): Map<String, Pair<ExerciseDefinitionV2, ExerciseConfigurationV2>> =
        catalog.families.flatMap { it.definitions }.flatMap { definition ->
            definition.configurations.map { configuration -> configuration.id to (definition to configuration) }
        }.toMap()
}
