package com.example.kpkn.domain.training

import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
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
import com.example.kpkn.data.models.alignTemporalMetadata
import com.example.kpkn.data.models.resolvedSchedulePlan
import com.example.kpkn.data.protocols.Protocol
import com.example.kpkn.data.protocols.ProtocolBlock
import com.example.kpkn.data.protocols.ProtocolExerciseLibrary
import com.example.kpkn.data.protocols.ProtocolLift
import com.example.kpkn.data.protocols.ProtocolLiftFocus
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
        val resolvedSplitId = protocol.defaultSplit
            ?.let(::resolveSplitId)
            ?: program.selectedSplitId?.let(::resolveSplitId)
            ?: error("El protocolo '${protocol.id}' debe declarar defaultSplit o el programa debe tener selectedSplitId")
        val splitPattern = SPLIT_TEMPLATES.first { it.id == resolvedSplitId }.pattern
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
            ).also {
                cycleWeekOffset += protocolBlock.weeks.coerceAtLeast(1)
            }
        }
        val structure = if (blocks.size > 1) ProgramStructure.COMPLEX else ProgramStructure.SIMPLE
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
            loops = if (structure == ProgramStructure.SIMPLE) emptyList() else program.loops,
            loopState = if (structure == ProgramStructure.SIMPLE) null else program.loopState,
            loopOccurrences = if (structure == ProgramStructure.SIMPLE) emptyList() else program.loopOccurrences,
            schedulePlan = program.resolvedSchedulePlan().copy(
                mode = if (program.resolvedSchedulePlan().anchorDate.isNullOrBlank()) {
                    ScheduleMode.FLOATING
                } else {
                    program.resolvedSchedulePlan().mode
                },
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
        // Bridge F4: red de seguridad — si algún protocolo quedara sin sesiones
        // ejecutables (p.ej. bloques sin días de entrenamiento), se rellena con
        // sugerencias reales del split declarado. Con contenido propio, es un no-op.
        val split = SessionPrefillBridge.resolveSplit(applied, protocolDefaultSplitId = applied.selectedSplitId)
        return SessionPrefillBridge.prefillIfEmpty(applied, split)
    }

    private fun buildBlock(
        protocolBlock: ProtocolBlock,
        splitPattern: List<String>,
        sessionParts: List<String>,
        protocol: Protocol,
        idProvider: IdProvider,
        cycleWeekOffset: Int,
        enhancedDayDifferentiation: Boolean,
    ): Block {
        val goal = resolveGoal(protocolBlock.goal)
        val totalWeeksInBlock = protocolBlock.weeks.coerceAtLeast(1)
        return Block(
            id = idProvider.newId(),
            name = protocolBlock.name,
            description = buildString {
                append("Intensidad ${protocolBlock.intensityMin}-${protocolBlock.intensityMax}%")
                protocolBlock.volumeModifier?.let { append(" · Volumen ×${"%.2f".format(it)}") }
            },
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
    ): List<Session> {
        val trainingDays = splitPattern
            .mapIndexedNotNull { index, label ->
                if (label.equals("Descanso", ignoreCase = true)) null else (index + 1) to label
            }
            .ifEmpty { listOf(1 to protocol.name) }

        val effectiveParts = if (enhancedDayDifferentiation && parts.size < 3) {
            parts + "Accesorios"
        } else {
            parts
        }

        return trainingDays.mapIndexed { sessionIndex, (dayOfWeek, label) ->
            val isMain = sessionIndex == 0
            val recipe = sessionRecipeForDay(
                dayLabel = label,
                sessionIndex = sessionIndex,
                partNames = effectiveParts,
                enhancedDayDifferentiation = enhancedDayDifferentiation,
            )
            val accessories = ProtocolExerciseLibrary.accessoriesFor(
                recipe.mainLift,
                weekNumber,
                recipe.accessoryCount,
            )
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
                    val lifts = when {
                        partIndex < 2 -> listOf(recipe.mainLift)
                        !enhancedDayDifferentiation -> listOf(
                            liftForPart(partIndex, recipe.mainLift, accessories),
                        )
                        else -> accessories
                            .drop(accessoryCursor)
                            .take(recipePart.exerciseCount)
                            .also { accessoryCursor += it.size }
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
                            )
                        },
                    )
                },
            )
        }
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
    ): Exercise {
        val resolvedLift = if (partIndex == 0 && goal == MesocycleGoal.ACCUMULATION) {
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
            ExerciseSet(
                id = idProvider.newId(),
                targetReps = prescription.reps,
                targetPercentageRM = prescription.percentageRM,
                targetRPE = prescription.rpe,
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
        )
    }

    private fun resolveGoal(raw: String): MesocycleGoal = when (raw.trim().lowercase()) {
        "acumulación", "acumulacion", "accumulation" -> MesocycleGoal.ACCUMULATION
        "intensificación", "intensificacion", "intensification" -> MesocycleGoal.INTENSIFICATION
        "realización", "realizacion", "realization", "pico" -> MesocycleGoal.REALIZATION
        "descarga", "deload" -> MesocycleGoal.DELOAD
        else -> MesocycleGoal.CUSTOM
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
