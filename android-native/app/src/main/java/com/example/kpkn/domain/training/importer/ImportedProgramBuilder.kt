package com.example.kpkn.domain.training.importer

import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSetupDetails
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.ExerciseMuscleInfo
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
 * - [resolved] asocia cada nombre importado a una configuración del catálogo (o null). Si el catálogo está cargado,
 *   se completa la identidad v2; si no está disponible todavía, se conserva la selección estable y el nombre importado.
 *   El límite de guardado vuelve a validar esa identidad antes de escribir en la biblioteca.
 * - Procedencia: `planProvenance` con planId «imported:<programId>» y clase IMPORTED («Programa importado»; no se atribuye autoría).
 *   Conserva el título y el origen web saneado si la persona importó desde una URL.
 */
object ImportedProgramBuilder {
    const val DEFAULT_TITLE = "Mi programa importado"

    /** Configuraciones que necesitan metadatos al construir este borrador; excluye propios y decisiones pendientes. */
    fun catalogConfigurationIds(
        draft: ImportedProgramDraft,
        resolved: Map<String, String?>,
    ): Set<String> = draft.weeks.asSequence()
        .flatMap { it.days.asSequence() }
        .flatMap { it.exercises.asSequence() }
        .mapNotNull { it.catalogSelectionId ?: resolved[it.rawName] }
        .filter { it.isNotBlank() && !it.startsWith("custom:", ignoreCase = true) && !it.equals("own", ignoreCase = true) }
        .map { it.lowercase() }
        .toSet()

    /** Maps each stable source row/day to the IDs produced by [build], including grouped prescriptions. */
    fun sourceMap(
        draft: ImportedProgramDraft,
        resolved: Map<String, String?>,
        programId: String,
    ): ImportedProgramSourceMap {
        val normalized = ImportDraftEditor.withIds(draft)
        val sessions = linkedMapOf<String, String>()
        val exercises = linkedMapOf<String, String>()
        normalized.weeks.sortedBy { it.index }.forEach { week ->
            week.days.forEachIndexed { dayIndex, day ->
                val sessionId = "${programId}-w${week.index}-d${dayIndex + 1}"
                sessions[day.id] = sessionId
                groupConsecutive(day.exercises, resolved).forEachIndexed { exerciseIndex, members ->
                    val programExerciseId = "$sessionId-e${exerciseIndex + 1}"
                    members.forEach { source -> exercises[source.id] = programExerciseId }
                }
            }
        }
        return ImportedProgramSourceMap(sessions, exercises)
    }

    /** No materializar una selección sin resolver o una identidad que ya no existe como si fuera del catálogo. */
    fun unavailableNames(
        draft: ImportedProgramDraft,
        resolved: Map<String, String?>,
        catalog: ExerciseCatalogV2,
        customExercises: List<ExerciseMuscleInfo>,
    ): List<String> {
        val catalogIds = catalog.families.flatMap { it.definitions }.flatMap { it.configurations }
            .map { it.id.lowercase() }.toSet()
        val customIds = customExercises.filter { it.isCustom && it.id.startsWith("custom:", ignoreCase = true) }
            .map { it.id.lowercase() }.toSet()
        return draft.weeks.flatMap { it.days }.flatMap { it.exercises }.filter { exercise ->
            val id = exercise.catalogSelectionId ?: if (resolved.containsKey(exercise.rawName)) {
                resolved[exercise.rawName]
            } else {
                return@filter true
            }
            if (id == null || id.equals("own", ignoreCase = true)) return@filter true
            val normalizedId = id.lowercase()
            normalizedId !in catalogIds && normalizedId !in customIds
        }.map { it.rawName }.distinct()
    }

    /** Validates every exercise in an editor-backed draft, including ones added in SessionEditor. */
    fun unavailableProgramExercises(
        program: Program,
        catalog: ExerciseCatalogV2,
        customExercises: List<ExerciseMuscleInfo>,
    ): List<String> {
        val catalogIds = catalog.families.flatMap { it.definitions }.flatMap { it.configurations }
            .map { it.id.lowercase() }.toSet()
        val customIds = customExercises.filter { it.isCustom && it.id.startsWith("custom:", ignoreCase = true) }
            .map { it.id.lowercase() }.toSet()
        return program.macrocycles.flatMap { macro ->
            macro.blocks.flatMap { block ->
                block.mesocycles.flatMap { meso ->
                    meso.weeks.flatMap { week -> week.sessions.flatMap { it.allExercises() } }
                }
            }
        }.filter { exercise ->
            val catalogId = exercise.catalogConfigurationId?.lowercase()
            val customId = listOfNotNull(exercise.exerciseId, exercise.exerciseDbId)
                .firstOrNull { it.startsWith("custom:", ignoreCase = true) }
                ?.lowercase()
            (catalogId == null || catalogId !in catalogIds) && (customId == null || customId !in customIds)
        }.map { it.name }.distinct()
    }

    fun build(
        draft: ImportedProgramDraft,
        resolved: Map<String, String?>,
        weekdays: List<Int>,
        programId: String,
        catalog: ExerciseCatalogV2? = null,
        customExercises: List<ExerciseMuscleInfo> = emptyList(),
        catalogConfigurationInfo: Map<String, ExerciseMuscleInfo> = emptyMap(),
    ): Program {
        val trainingDays = weekdays.filter { it in 1..7 }.distinct().sorted()
        val configurations = catalog?.let { configurationIndex(it) }.orEmpty()
        val revision = catalog?.catalogRevision
        val customIndex = customExercises.filter { it.isCustom && it.id.startsWith("custom:", ignoreCase = true) }
            .associateBy { it.id.lowercase() }
        val alternatives = catalog?.let { alternativeNotes(draft, resolved, it) }.orEmpty()
        val weeks = draft.weeks.sortedBy { it.index }.map { week ->
            val weekdayPerSession = assignWeekdays(week.days.count { !it.isRestDay }, trainingDays)
            var trainingSessionIndex = 0
            val fallbackWeekdays = week.days.map { day ->
                if (day.isRestDay) null else weekdayPerSession[trainingSessionIndex++]
            }
            ProgramWeek(
                id = "$programId-w${week.index}",
                name = "Semana ${week.index}",
                executionKind = week.executionKind,
                sessions = week.days.mapIndexed { dayIndex, day ->
                    buildSession(
                        programId = programId,
                        weekIndex = week.index,
                        dayIndex = dayIndex,
                        day = day,
                        weekday = day.dayOfWeek?.takeIf { it in 1..7 } ?: fallbackWeekdays[dayIndex],
                        defaultLoadUnit = draft.loadUnit,
                        resolved = resolved,
                        alternatives = alternatives,
                        configurations = configurations,
                        revision = revision,
                        customExercises = customIndex,
                        catalogConfigurationInfo = catalogConfigurationInfo,
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
            planProvenance = PlanProvenance(
                planId = "imported:$programId",
                category = PlanProvenanceClass.IMPORTED,
                sourceTitle = draft.sourceTitle,
                sourceUrl = draft.sourceUrl,
            ),
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
        defaultLoadUnit: ImportLoadUnit,
        resolved: Map<String, String?>,
        alternatives: Map<String, String>,
        configurations: Map<String, Pair<ExerciseDefinitionV2, ExerciseConfigurationV2>>,
        revision: String?,
        customExercises: Map<String, ExerciseMuscleInfo>,
        catalogConfigurationInfo: Map<String, ExerciseMuscleInfo>,
    ): Session {
        val sessionId = "$programId-w$weekIndex-d${dayIndex + 1}"
        val exerciseGroups = groupConsecutive(day.exercises, resolved)
        val builtExercises = exerciseGroups.mapIndexed { exerciseIndex, members ->
            buildExercise(
                id = "$sessionId-e${exerciseIndex + 1}",
                members = members,
                defaultLoadUnit = defaultLoadUnit,
                configurationId = members.first().catalogSelectionId ?: resolved[members.first().rawName],
                alternativeNote = alternatives[members.first().rawName].takeIf {
                    members.first().catalogSelectionId == null ||
                        members.first().catalogSelectionId.equals(resolved[members.first().rawName], ignoreCase = true)
                },
                configurations = configurations,
                revision = revision,
                customExercises = customExercises,
                catalogConfigurationInfo = catalogConfigurationInfo,
            )
        }
        val supersetIndexes = linkedMapOf<String, MutableList<Int>>()
        exerciseGroups.forEachIndexed { index, members ->
            members.first().supersetGroup?.let { group -> supersetIndexes.getOrPut(group) { mutableListOf() } += index }
        }
        val supersetIds = supersetIndexes
            .filterValues { indexes -> indexes.distinct().size >= 2 }
            .mapValues { (group, _) -> "$sessionId-ss-${group.lowercase()}" }
        val exercises = builtExercises.mapIndexed { index, exercise ->
            val group = exerciseGroups[index].first().supersetGroup
            val supersetId = group?.let(supersetIds::get)
            if (supersetId == null) exercise else exercise.copy(supersetId = supersetId, supersetGroupRef = supersetId)
        }
        val supersets = supersetIndexes.mapNotNull { (group, indexes) ->
            val id = supersetIds[group] ?: return@mapNotNull null
            com.example.kpkn.data.models.SupersetGroup(
                id = id,
                exerciseOrder = indexes.distinct().map { exercises[it].id },
            )
        }
        return Session(
            id = sessionId,
            name = day.name.ifBlank { "Día ${dayIndex + 1}" },
            // Los ejercicios seguidos con el mismo nombre son un solo ejercicio de la app (varios grupos de series).
            exercises = exercises,
            supersetGroups = supersets,
            dayOfWeek = weekday,
            assignedDays = listOfNotNull(weekday),
            requirement = if (day.isRestDay) com.example.kpkn.data.models.SessionRequirement.OPTIONAL
                else com.example.kpkn.data.models.SessionRequirement.REQUIRED,
            origin = SessionOrigin.USER_DRAFT,
        )
    }

    /** Ejercicio de la app a partir de uno o varios grupos de series seguidos (en orden, con sus propias cifras). */
    private fun buildExercise(
        id: String,
        members: List<ImportedExercise>,
        defaultLoadUnit: ImportLoadUnit,
        configurationId: String?,
        alternativeNote: String?,
        configurations: Map<String, Pair<ExerciseDefinitionV2, ExerciseConfigurationV2>>,
        revision: String?,
        customExercises: Map<String, ExerciseMuscleInfo>,
        catalogConfigurationInfo: Map<String, ExerciseMuscleInfo>,
    ): Exercise {
        val entry = configurationId?.lowercase()?.let { configurations[it] }
        val custom = configurationId?.lowercase()?.let(customExercises::get)
        val definition = entry?.first
        val configuration = entry?.second
        val customId = custom?.id ?: configurationId?.takeIf { it.startsWith("custom:", ignoreCase = true) }
        val selectedInfo = custom ?: configurationId?.lowercase()?.let(catalogConfigurationInfo::get)
        // Keep a stable selected identity even when the catalog cannot currently supply its metadata. The save
        // boundary revalidates it against the loaded catalog before materializing it in the library.
        val catalogConfigurationId = configuration?.id
            ?: configurationId?.takeUnless {
                it.startsWith("custom:", ignoreCase = true) || it.equals("own", ignoreCase = true)
            }
        val exerciseIdentity = customId ?: catalogConfigurationId
        return Exercise(
            id = id,
            name = selectedInfo?.name ?: definition?.canonicalName ?: members.first().rawName,
            exerciseDbId = exerciseIdentity,
            exerciseId = exerciseIdentity,
            canonicalExerciseId = exerciseIdentity?.lowercase(),
            exerciseFamilyId = definition?.familyId ?: exerciseIdentity?.lowercase(),
            variantName = selectedInfo?.catalogVariantChips?.joinToString(" · ")?.ifBlank { null }
                ?: selectedInfo?.variantName,
            variantGroupId = selectedInfo?.variantGroupId,
            variantGroupName = selectedInfo?.variantGroupName,
            effectiveMuscles = selectedInfo?.involvedMuscles?.takeIf { it.isNotEmpty() },
            selectedExecutionOption = selectedInfo?.executionOptions?.firstOrNull(),
            setupDetails = selectedInfo?.setupDetails?.let { details ->
                ExerciseSetupDetails(
                    seatPosition = details.seatPosition,
                    pinPosition = details.pinPosition,
                    equipmentNotes = details.equipmentNotes,
                )
            },
            catalogRevision = if (configuration != null) selectedInfo?.catalogRevision ?: revision else null,
            catalogDefinitionId = definition?.id ?: selectedInfo?.catalogDefinitionId,
            catalogConfigurationId = catalogConfigurationId ?: selectedInfo?.catalogConfigurationId,
            performanceProfileId = configuration?.profile?.performanceProfileId ?: selectedInfo?.performanceProfileId,
            occurrenceId = id.takeIf { catalogConfigurationId != null || custom != null },
            trainingMode = if (members.first().durationSeconds != null) TrainingMode.TIME else TrainingMode.REPS,
            restTime = members.firstNotNullOfOrNull { it.restSeconds },
            sets = buildSets(id, members, defaultLoadUnit),
            setupCues = selectedInfo?.setupCues.orEmpty(),
            executionCues = (selectedInfo?.executionCues.orEmpty() + members.mapNotNull { it.notes } + listOfNotNull(alternativeNote)).distinct(),
            selectedMovementPattern = selectedInfo?.movementPattern ?: configuration?.profile?.movementPatternId,
        )
    }

    private fun buildSets(exerciseId: String, members: List<ImportedExercise>, defaultLoadUnit: ImportLoadUnit): List<ExerciseSet> =
        members.flatMap { exercise -> setsOf(exercise, exercise.loadUnit ?: defaultLoadUnit) }
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
        return List(exercise.sets.coerceAtLeast(0)) { index ->
            val purpose = if (exercise.purpose.isAmrap &&
                (index == exercise.sets - 1 || exercise.amrapMinimumReps == null)) exercise.purpose
                else com.example.kpkn.data.models.SetPurpose.NONE
            ExerciseSet(
                id = "",
                targetReps = if (exercise.durationSeconds != null || (purpose.isAmrap && exercise.amrapMinimumReps == null)) null else high,
                targetRepsRange = if (exercise.durationSeconds != null || (purpose.isAmrap && exercise.amrapMinimumReps == null)) null else RepRange(low, high),
                targetDuration = exercise.durationSeconds,
                purpose = purpose,
                amrapMinimumReps = if (purpose.isAmrap) exercise.amrapMinimumReps else null,
                targetRPE = exercise.rpe,
                targetRIR = exercise.rir,
                intensityMode = intensity,
                targetPercentageRM = percent,
                weight = weight,
                restAfterSeconds = exercise.restSeconds,
                loadBasis = if (percent != null) LoadBasis.PERCENT_1RM else null,
            )
        }
    }

    /** Carga en kilos: con libras, al 0,5 kg más cercano (135 lb → 61 kg). */
    internal fun kilograms(value: Double, unit: ImportLoadUnit): Double =
        if (unit == ImportLoadUnit.LB) (value * LB_TO_KG * 2).roundToInt() / 2.0 else value

    /** Agrupa los ejercicios seguidos con el mismo nombre; si el nombre vuelve más abajo, separado por otro, es otro grupo. */
    internal fun groupConsecutive(exercises: List<ImportedExercise>, resolved: Map<String, String?> = emptyMap()): List<List<ImportedExercise>> {
        val groups = mutableListOf<MutableList<ImportedExercise>>()
        for (exercise in exercises) {
            val last = groups.lastOrNull()
            if (last != null && last.first().rawName == exercise.rawName &&
                (last.first().durationSeconds != null) == (exercise.durationSeconds != null) &&
                (last.first().catalogSelectionId ?: resolved[last.first().rawName]) ==
                    (exercise.catalogSelectionId ?: resolved[exercise.rawName])) {
                last += exercise
            } else {
                groups += mutableListOf(exercise)
            }
        }
        return groups
    }

    /**
     * Nota «Alternativa: Y.» de cada nombre «X o Y» que se resolvió por X: el emparejador lo reconoce así y la
     * configuración resuelta es la suya. Si la persona eligió otra, no hay nota.
     */
    private fun alternativeNotes(
        draft: ImportedProgramDraft,
        resolved: Map<String, String?>,
        catalog: ExerciseCatalogV2,
    ): Map<String, String> {
        val names = draft.weeks.flatMap { week -> week.days.flatMap { day -> day.exercises.map { it.rawName } } }
            .distinct()
            .filter { resolved[it] != null && splitAlternative(it) != null }
        if (names.isEmpty()) return emptyMap()
        val matcher = ExerciseNameMatcher(catalog)
        return names.mapNotNull { name ->
            val match = matcher.match(name)
            val alternative = match.alternative ?: return@mapNotNull null
            if (match.best?.configurationId != resolved[name]) return@mapNotNull null
            name to "Alternativa: $alternative."
        }.toMap()
    }

    private const val LB_TO_KG = 0.45359237

    private fun configurationIndex(
        catalog: ExerciseCatalogV2,
    ): Map<String, Pair<ExerciseDefinitionV2, ExerciseConfigurationV2>> =
        catalog.families.flatMap { it.definitions }.flatMap { definition ->
            definition.configurations.map { configuration -> configuration.id.lowercase() to (definition to configuration) }
        }.toMap()
}
