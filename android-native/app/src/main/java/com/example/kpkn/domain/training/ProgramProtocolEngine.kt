package com.example.kpkn.domain.training

import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.BlockGoal
import com.example.kpkn.data.models.BlockProgressionScheme
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.MesocycleGoal
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramStructure
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.ScheduleMode
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SessionPart
import com.example.kpkn.data.models.SimpleProgramKind
import com.example.kpkn.data.models.TrainingMode
import com.example.kpkn.data.models.WarmupSetDefinition
import com.example.kpkn.data.models.WeekExecutionKind
import com.example.kpkn.data.models.alignTemporalMetadata
import com.example.kpkn.data.models.resolvedSchedulePlan
import com.example.kpkn.data.protocols.Protocol
import com.example.kpkn.data.protocols.ProtocolBlock
import com.example.kpkn.data.protocols.ProtocolDayRecipe
import com.example.kpkn.data.protocols.ProtocolExerciseLibrary
import com.example.kpkn.data.protocols.ProtocolLift
import com.example.kpkn.data.protocols.ProtocolLiftFocus
import com.example.kpkn.data.protocols.isVisibleForApplication
import com.example.kpkn.data.splits.SPLIT_TEMPLATES

data class ProtocolSessionPartRecipe(
    val name: String,
    val exerciseCount: Int,
)

/** Receta explícita día → foco → partes para que un protocolo no sea una copia del mismo día. */
data class ProtocolSessionRecipe(
    val dayLabel: String,
    val focus: ProtocolLiftFocus,
    val mainLift: ProtocolLift,
    val accessoryCount: Int,
    val parts: List<ProtocolSessionPartRecipe>,
    val accessoryLifts: List<ProtocolLift> = emptyList(),
    val mainRestSeconds: Int? = null,
    val accessoryRestSeconds: Int? = null,
)

/**
 * Compilador único de protocolos → estructura de programa ejecutable.
 * Usado por la sheet de plantillas/protocolos dentro de MacrocycleEditor; no existe
 * una ruta independiente ProtocolsView. Así ambas superficies futuras comparten
 * el mismo compilador y no crean estructuras divergentes.
 */
object ProgramProtocolEngine {

    fun applyProtocol(
        program: Program,
        protocol: Protocol,
        idProvider: IdProvider = UuidIdProvider,
        enhancedDayDifferentiation: Boolean = false,
    ): Program {
        require(protocol.isVisibleForApplication) {
            "El protocolo '${protocol.id}' no está publicado: falta una receta verificable día por día."
        }
        require(protocol.blocks.isNotEmpty()) {
            "El protocolo '${protocol.id}' no tiene bloques materializables."
        }
        val resolvedSplitId = protocol.defaultSplit
            ?.let(::resolveSplitId)
            ?: program.selectedSplitId?.let(::resolveSplitId)
            ?: error("El protocolo '${protocol.id}' debe declarar defaultSplit o el programa debe tener selectedSplitId")
        val splitPattern = SPLIT_TEMPLATES.first { it.id == resolvedSplitId }.pattern
        val startDay = program.resolvedSchedulePlan().weekStartDay ?: program.startDay ?: 1
        val sessionParts = protocol.sessionCategories.ifEmpty {
            listOf("Parte principal", "Suplementario", "Accesorios")
        }
        var cycleWeekOffset = 0
        val blocks = protocol.blocks.map { protocolBlock ->
            buildBlock(
                protocolBlock = protocolBlock,
                splitPattern = splitPattern,
                sessionParts = sessionParts,
                protocol = protocol,
                idProvider = idProvider,
                cycleWeekOffset = cycleWeekOffset,
                enhancedDayDifferentiation = enhancedDayDifferentiation,
                startDay = startDay,
            ).also {
                cycleWeekOffset += protocolBlock.weeks.coerceAtLeast(1)
            }
        }
        val structure = if (blocks.size > 1) ProgramStructure.COMPLEX else ProgramStructure.SIMPLE
        val trainingDays = SplitApplicationEngine.patternToTrainingDays(splitPattern, startDay)
            .map { it.dayOfWeek }
            .toSet()
        val applied = program.copy(
            structure = structure,
            structureTemplateId = protocol.id,
            simpleProgramKind = if (structure == ProgramStructure.SIMPLE) {
                SimpleProgramKind.CYCLIC
            } else {
                program.simpleProgramKind
            },
            calendarization = if (structure == ProgramStructure.COMPLEX) program.calendarization else null,
            pausedCyclicSnapshot = if (structure == ProgramStructure.SIMPLE) null else program.pausedCyclicSnapshot,
            runState = null,
            loops = emptyList(),
            loopState = null,
            loopOccurrences = emptyList(),
            events = emptyList(),
            calendarBreaks = emptyList(),
            schedulePlan = program.resolvedSchedulePlan().copy(
                mode = if (program.resolvedSchedulePlan().anchorDate.isNullOrBlank()) {
                    ScheduleMode.FLOATING
                } else {
                    program.resolvedSchedulePlan().mode
                },
                trainingDays = trainingDays,
            ),
            selectedSplitId = resolvedSplitId,
            blockSplitSelections = emptyMap(),
            weekSplitSelections = emptyMap(),
            macrocycles = listOf(
                Macrocycle(
                    id = idProvider.newId(),
                    name = protocol.name,
                    blocks = blocks,
                ),
            ),
        ).alignTemporalMetadata()
        // A protocol owns its prescription.  Do not fill a broken recipe with a
        // generic split: that would silently turn a named method into something
        // else.  Calendar dates are just a projection of the materialized days.
        val hydrated = hydrateProgramGoals(applied)
        val executable = if (ProgramCalendarEngine.isCalendarized(hydrated)) {
            ProgramCalendarEngine.materializeWeekDates(hydrated)
        } else {
            hydrated
        }
        return ProgramExecutionContract.requireExecutable(executable)
    }

    /** Attach recorded S/B/D goals to the exact competition configurations. */
    private fun hydrateProgramGoals(program: Program): Program {
        val goals = program.goals ?: return program
        fun referenceFor(exercise: Exercise): Double? {
            val id = listOfNotNull(
                exercise.catalogConfigurationId,
                exercise.canonicalExerciseId,
                exercise.exerciseDbId,
                exercise.exerciseId,
            ).firstOrNull()?.lowercase() ?: return exercise.reference1RM
            val goal = when (id) {
                "low_bar_back_squat__barbell", "high_bar_back_squat__barbell" -> goals.squat1RM
                "bench_press__barbell" -> goals.bench1RM
                "conventional_deadlift__bilateral__barbell" -> goals.deadlift1RM
                else -> null
            }
            return goal?.takeIf { it > 0.0 } ?: exercise.reference1RM
        }
        fun mapSession(session: Session): Session = session.copy(
            exercises = session.exercises.map { it.copy(reference1RM = referenceFor(it)) },
            parts = session.parts.map { part ->
                part.copy(exercises = part.exercises.map { it.copy(reference1RM = referenceFor(it)) })
            },
            sessionB = session.sessionB?.let(::mapSession),
            sessionC = session.sessionC?.let(::mapSession),
            sessionD = session.sessionD?.let(::mapSession),
        )
        return program.copy(
            macrocycles = program.macrocycles.map { macro ->
                macro.copy(blocks = macro.blocks.map { block ->
                    block.copy(mesocycles = block.mesocycles.map { meso ->
                        meso.copy(weeks = meso.weeks.map { week ->
                            week.copy(sessions = week.sessions.map(::mapSession))
                        })
                    })
                })
            },
        )
    }

    private fun buildBlock(
        protocolBlock: ProtocolBlock,
        splitPattern: List<String>,
        sessionParts: List<String>,
        protocol: Protocol,
        idProvider: IdProvider,
        cycleWeekOffset: Int,
        enhancedDayDifferentiation: Boolean,
        startDay: Int,
    ): Block {
        val goal = resolveGoal(protocolBlock.goal)
        val blockGoal = resolveBlockGoal(protocolBlock.goal)
        val totalWeeksInBlock = protocolBlock.weeks.coerceAtLeast(1)
        return Block(
            id = idProvider.newId(),
            name = protocolBlock.name,
            description = buildString {
                append("Intensidad ${protocolBlock.intensityMin}-${protocolBlock.intensityMax}%")
                protocolBlock.volumeModifier?.let { append(" · Volumen ×${"%.2f".format(it)}") }
            },
            goal = blockGoal,
            progressionScheme = progressionSchemeFor(protocol, blockGoal),
            mesocycles = listOf(
                Mesocycle(
                    id = idProvider.newId(),
                    name = protocolBlock.name,
                    goal = goal,
                    weeks = (1..totalWeeksInBlock).map { weekNumber ->
                        ProgramWeek(
                            id = idProvider.newId(),
                            name = "Semana $weekNumber",
                            description = "Objetivo ${protocolBlock.goal} · ${protocolBlock.intensityMin}-${protocolBlock.intensityMax}% 1RM",
                            progressionIndex = weekNumber,
                            executionKind = if (goal == MesocycleGoal.DELOAD) WeekExecutionKind.DELOAD else WeekExecutionKind.TRAINING,
                            sessions = buildSessions(
                                splitPattern,
                                sessionParts,
                                protocol,
                                protocolBlock,
                                goal,
                                weekNumber,
                                totalWeeksInBlock,
                                idProvider,
                                cycleWeekOffset + weekNumber,
                                enhancedDayDifferentiation,
                                startDay,
                            ),
                        )
                    },
                ),
            ),
        )
    }

    private fun buildSessions(
        splitPattern: List<String>,
        parts: List<String>,
        protocol: Protocol,
        protocolBlock: ProtocolBlock,
        goal: MesocycleGoal,
        weekNumber: Int,
        totalWeeksInBlock: Int,
        idProvider: IdProvider,
        absoluteWeekNumber: Int,
        enhancedDayDifferentiation: Boolean,
        startDay: Int,
    ): List<Session> {
        val trainingDays = SplitApplicationEngine.patternToTrainingDays(splitPattern, startDay)
            .map { it.dayOfWeek to it.label }
            .ifEmpty { listOf(1 to protocol.name) }

        val effectiveParts = if (enhancedDayDifferentiation && parts.size < 3) {
            parts + "Accesorios"
        } else {
            parts
        }

        return trainingDays.mapIndexed { sessionIndex, (dayOfWeek, label) ->
            val isMain = sessionIndex == 0
            val explicitDayRecipe = protocol.dayRecipes.getOrNull(sessionIndex)
            val recipe = explicitDayRecipe?.let { explicitSessionRecipe(it, effectiveParts) }
                ?: sessionRecipeForDay(
                    dayLabel = label,
                    sessionIndex = sessionIndex,
                    partNames = effectiveParts,
                    enhancedDayDifferentiation = enhancedDayDifferentiation,
                )
            val accessories = if (explicitDayRecipe != null) {
                recipe.accessoryLifts
            } else {
                ProtocolExerciseLibrary.accessoriesFor(
                    recipe.mainLift,
                    weekNumber,
                    recipe.accessoryCount,
                )
            }
            var accessoryCursor = 0
            Session(
                id = idProvider.newId(),
                name = label,
                description = "Prescripción ${protocolBlock.intensityMin}-${protocolBlock.intensityMax}% 1RM" +
                    (protocolBlock.volumeModifier?.let { " · vol ×${"%.2f".format(it)}" } ?: ""),
                dayOfWeek = dayOfWeek,
                assignedDays = listOf(dayOfWeek),
                isMainSession = isMain,
                focus = protocolBlock.goal,
                parts = effectiveParts.mapIndexed { partIndex, partName ->
                    val recipePart = recipe.parts[partIndex]
                    val lifts = if (explicitDayRecipe != null) {
                        if (partIndex == 0) listOf(recipe.mainLift)
                        else accessories.drop(accessoryCursor)
                            .take(recipePart.exerciseCount)
                            .also { accessoryCursor += it.size }
                    } else {
                        when {
                            partIndex < 2 -> listOf(recipe.mainLift)
                            !enhancedDayDifferentiation -> listOf(
                                liftForPart(partIndex, recipe.mainLift, accessories),
                            )
                            else -> accessories
                                .drop(accessoryCursor)
                                .take(recipePart.exerciseCount)
                                .also { accessoryCursor += it.size }
                        }
                    }
                    SessionPart(
                        id = idProvider.newId(),
                        name = partName,
                        exercises = lifts.map { lift ->
                            prescribedExercise(
                                lift = lift,
                                partIndex = partIndex,
                                goal = goal,
                                protocol = protocol,
                                protocolBlock = protocolBlock,
                                weekNumber = weekNumber,
                                totalWeeksInBlock = totalWeeksInBlock,
                                absoluteWeekNumber = absoluteWeekNumber,
                                idProvider = idProvider,
                                restSeconds = if (explicitDayRecipe != null) {
                                    if (partIndex == 0) recipe.mainRestSeconds else recipe.accessoryRestSeconds
                                } else null,
                                competitionPart = if (explicitDayRecipe != null) partIndex == 0 else partIndex < 2,
                            )
                        },
                    )
                },
            )
        }
    }

    private fun explicitSessionRecipe(
        dayRecipe: ProtocolDayRecipe,
        partNames: List<String>,
    ): ProtocolSessionRecipe {
        require(dayRecipe.mainRestSeconds >= 180) {
            "La receta '${dayRecipe.dayLabel}' debe descansar al menos 180s en el principal."
        }
        require(dayRecipe.accessoryRestSeconds > 0) {
            "La receta '${dayRecipe.dayLabel}' debe declarar descanso de accesorios."
        }
        val main = ProtocolExerciseLibrary.fromConfigurationId(dayRecipe.mainLiftConfigurationId)
        val accessories = dayRecipe.accessoryExerciseConfigurationIds.map(
            ProtocolExerciseLibrary::fromConfigurationId,
        )
        val safeParts = partNames.ifEmpty { listOf("Principal", "Accesorios específicos") }
        var remaining = accessories.size
        val parts = safeParts.mapIndexed { index, name ->
            val count = when {
                index == 0 -> 1
                index == safeParts.lastIndex -> remaining
                else -> 0
            }
            remaining -= count
            ProtocolSessionPartRecipe(name = name, exerciseCount = count)
        }
        return ProtocolSessionRecipe(
            dayLabel = dayRecipe.dayLabel,
            focus = ProtocolExerciseLibrary.focusForDayLabel(dayRecipe.dayLabel),
            mainLift = main,
            accessoryCount = accessories.size,
            parts = parts,
            accessoryLifts = accessories,
            mainRestSeconds = dayRecipe.mainRestSeconds,
            accessoryRestSeconds = dayRecipe.accessoryRestSeconds,
        )
    }

    fun sessionRecipeForDay(
        dayLabel: String,
        sessionIndex: Int,
        partNames: List<String>,
        enhancedDayDifferentiation: Boolean = true,
    ): ProtocolSessionRecipe {
        val focus = ProtocolExerciseLibrary.focusForDayLabel(dayLabel)
        val mainLift = ProtocolExerciseLibrary.mainLiftFor(focus, sessionIndex)
        val accessoryCount = if (enhancedDayDifferentiation) {
            when (focus) {
                ProtocolLiftFocus.SQUAT, ProtocolLiftFocus.DEADLIFT -> 3
                ProtocolLiftFocus.BENCH,
                ProtocolLiftFocus.OVERHEAD_PRESS,
                ProtocolLiftFocus.PULL -> 2
                ProtocolLiftFocus.GENERAL -> 1
            }
        } else {
            (partNames.size - 2).coerceAtLeast(0)
        }
        val safeParts = if (enhancedDayDifferentiation && partNames.size < 3) {
            partNames + "Accesorios"
        } else {
            partNames
        }
        var remainingAccessories = accessoryCount
        val parts = safeParts.mapIndexed { index, name ->
            val exerciseCount = when {
                index < 2 -> 1
                !enhancedDayDifferentiation -> 1
                index == safeParts.lastIndex -> remainingAccessories
                else -> 1.coerceAtMost(remainingAccessories)
            }
            if (index >= 2) remainingAccessories = (remainingAccessories - exerciseCount).coerceAtLeast(0)
            ProtocolSessionPartRecipe(name = name, exerciseCount = exerciseCount)
        }
        return ProtocolSessionRecipe(
            dayLabel = dayLabel,
            focus = focus,
            mainLift = mainLift,
            accessoryCount = accessoryCount,
            parts = parts,
        )
    }

    /** Parte 0 = movimiento principal (o su variante técnica en acumulación), parte 1 = mismo movimiento, resto = accesorios reales. */
    private fun liftForPart(partIndex: Int, mainLift: ProtocolLift, accessories: List<ProtocolLift>): ProtocolLift = when (partIndex) {
        0, 1 -> mainLift
        else -> accessories.getOrElse(partIndex - 2) { mainLift }
    }

    /**
     * Ejercicio ejecutable con exerciseDbId real y prescripción de series/reps/%1RM/RPE
     * escalada por [PeriodizationEngine] según el objetivo del bloque.
     */
    private fun prescribedExercise(
        lift: ProtocolLift,
        partIndex: Int,
        goal: MesocycleGoal,
        protocol: Protocol,
        protocolBlock: ProtocolBlock,
        weekNumber: Int,
        totalWeeksInBlock: Int,
        absoluteWeekNumber: Int,
        idProvider: IdProvider,
        restSeconds: Int? = null,
        competitionPart: Boolean = partIndex < 2,
    ): Exercise {
        val resolvedLift = if (protocol.dayRecipes.isEmpty() && partIndex == 0 && goal == MesocycleGoal.ACCUMULATION) {
            ProtocolExerciseLibrary.techniqueVariantFor(lift)
        } else {
            lift
        }
        val baseReps = when (partIndex) {
            0 -> 5
            1 -> 8
            else -> 10
        }
        val prescription = PeriodizationEngine.prescriptionFor(
            goal = goal,
            baseSets = 3,
            baseReps = baseReps,
            volumeModifier = protocolBlock.volumeModifier,
            intensityMin = protocolBlock.intensityMin,
            intensityMax = protocolBlock.intensityMax,
            weekNumber = if (protocol.id == "531-base" && partIndex == 0) absoluteWeekNumber else weekNumber,
            totalWeeksInBlock = totalWeeksInBlock,
            repScheme = if (protocol.id == "531-base" && partIndex == 0) {
                listOf(5, 3, 1, 5)
            } else {
                null
            },
        )
        val sets = (1..prescription.sets).map {
            val mainLift = competitionPart
            ExerciseSet(
                id = idProvider.newId(),
                targetReps = prescription.reps,
                // %1RM is a load anchor for the main/competition lift only.
                // Accessories remain executable as REPS + RPE and cannot
                // accidentally inherit a squat/bench/deadlift 1RM.
                targetPercentageRM = prescription.percentageRM.takeIf { mainLift },
                targetRPE = prescription.rpe.takeIf { !mainLift },
                intensityMode = if (mainLift) IntensityMode.SOLO_RM else IntensityMode.RPE,
            )
        }
        return Exercise(
            id = idProvider.newId(),
            name = resolvedLift.name,
            exerciseDbId = resolvedLift.exerciseDbId,
            exerciseId = resolvedLift.exerciseDbId,
            canonicalExerciseId = resolvedLift.exerciseDbId,
            exerciseFamilyId = resolvedLift.catalogDefinitionId,
            sets = sets,
            catalogRevision = resolvedLift.catalogRevision,
            catalogDefinitionId = resolvedLift.catalogDefinitionId,
            catalogConfigurationId = resolvedLift.exerciseDbId,
            performanceProfileId = resolvedLift.performanceProfileId,
            occurrenceId = idProvider.newId(),
            restTime = restSeconds,
            // Only the competition main lift receives a conservative approach
            // ramp.  reference1RM stays null here and is hydrated from the
            // athlete's recorded history/calibration at execution time.
            warmupSets = if (competitionPart && partIndex == 0 && resolvedLift.isCompetitionLift) {
                listOf(
                    WarmupSetDefinition(idProvider.newId(), 40.0, 5, restBetween = 60),
                    WarmupSetDefinition(idProvider.newId(), 60.0, 3, restBetween = 90),
                    WarmupSetDefinition(idProvider.newId(), 75.0, 1, restBetween = 120),
                )
            } else {
                emptyList()
            },
            trainingMode = if (competitionPart) TrainingMode.RM else TrainingMode.REPS,
            isCompetitionLift = competitionPart && partIndex == 0 && resolvedLift.isCompetitionLift,
        )
    }

    private val ProtocolLift.isCompetitionLift: Boolean
        get() = exerciseDbId in setOf(
            ProtocolExerciseLibrary.SQUAT_MAIN.exerciseDbId,
            ProtocolExerciseLibrary.LOW_BAR_SQUAT_MAIN.exerciseDbId,
            ProtocolExerciseLibrary.BENCH_MAIN.exerciseDbId,
            ProtocolExerciseLibrary.DEADLIFT_MAIN.exerciseDbId,
        )

    private fun resolveGoal(raw: String): MesocycleGoal = when (raw.trim().lowercase()) {
        "acumulación", "acumulacion", "accumulation" -> MesocycleGoal.ACCUMULATION
        "intensificación", "intensificacion", "intensification" -> MesocycleGoal.INTENSIFICATION
        "realización", "realizacion", "realization", "pico" -> MesocycleGoal.REALIZATION
        "descarga", "deload" -> MesocycleGoal.DELOAD
        // MesocycleGoal predates the block-level TAPER enum.  A taper is a
        // deload prescription with an explicit TAPER block label, so it must
        // receive the reduced sets/reps/RPE policy rather than CUSTOM 7.5.
        "taper" -> MesocycleGoal.DELOAD
        else -> MesocycleGoal.CUSTOM
    }

    private fun resolveBlockGoal(raw: String): BlockGoal = when (raw.trim().lowercase()) {
        "acumulación", "acumulacion", "accumulation" -> BlockGoal.ACCUMULATION
        "intensificación", "intensificacion", "intensification" -> BlockGoal.INTENSIFICATION
        "especificidad", "specificity" -> BlockGoal.SPECIFICITY
        "realización", "realizacion", "realization" -> BlockGoal.REALIZATION
        "pico", "peak" -> BlockGoal.PEAK
        "descarga", "deload" -> BlockGoal.DELOAD
        "taper" -> BlockGoal.TAPER
        "densidad", "metabolitos", "density" -> BlockGoal.DENSITY
        else -> BlockGoal.CUSTOM
    }

    private fun progressionSchemeFor(protocol: Protocol, goal: BlockGoal): BlockProgressionScheme = when {
        protocol.tags.any { it.equals("autoregulacion", true) || it.equals("rpe", true) } -> BlockProgressionScheme.RPE_CAP
        goal == BlockGoal.DENSITY -> BlockProgressionScheme.UNDULATING
        else -> BlockProgressionScheme.PERCENT_RM
    }

    fun resolveSplitId(raw: String): String {
        val normalized = raw.trim().lowercase()
        val resolved = when {
            SPLIT_TEMPLATES.any { it.id == normalized } -> normalized
            else -> when (normalized) {
            "upper-lower", "ul", "4-day", "4day" -> "ul_x4"
            "ppl" -> "ppl_x6"
            "fullbody", "full-body" -> "fullbody_x3"
            "texas_method", "texas" -> "texas_method"
                else -> error("Split '$raw' no existe en SPLIT_TEMPLATES y no tiene alias válido")
            }
        }
        require(SPLIT_TEMPLATES.any { it.id == resolved }) {
            "Split resuelto '$resolved' no existe en SPLIT_TEMPLATES"
        }
        return resolved
    }
}
