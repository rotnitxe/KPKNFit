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
import com.example.kpkn.data.splits.SPLIT_TEMPLATES

/**
 * Compilador único de protocolos → estructura de programa ejecutable.
 * Usado por ProtocolsView y MacrocycleEditor para evitar constructores divergentes.
 */
object ProgramProtocolEngine {

    fun applyProtocol(
        program: Program,
        protocol: Protocol,
        idProvider: IdProvider = UuidIdProvider,
    ): Program {
        val splitPattern = resolveSplitPattern(protocol.defaultSplit)
        val sessionParts = protocol.sessionCategories.ifEmpty {
            listOf("Parte principal", "Suplementario", "Accesorios")
        }
        val blocks = protocol.blocks.map { protocolBlock ->
            buildBlock(protocolBlock, splitPattern, sessionParts, protocol, idProvider)
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
                mode = if (program.timelineStartDate.isNullOrBlank()) {
                    ScheduleMode.FLOATING
                } else {
                    program.resolvedSchedulePlan().mode
                },
            ),
            selectedSplitId = protocol.defaultSplit?.let { resolveSplitId(it) } ?: program.selectedSplitId,
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
                                splitPattern = splitPattern,
                                parts = sessionParts,
                                protocol = protocol,
                                protocolBlock = protocolBlock,
                                goal = goal,
                                weekNumber = weekNumber,
                                totalWeeksInBlock = totalWeeksInBlock,
                                idProvider = idProvider,
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
    ): List<Session> {
        val trainingDays = splitPattern
            .mapIndexedNotNull { index, label ->
                if (label.equals("Descanso", ignoreCase = true)) null else (index + 1) to label
            }
            .ifEmpty { listOf(1 to protocol.name) }

        return trainingDays.mapIndexed { sessionIndex, (dayOfWeek, label) ->
            val isMain = sessionIndex == 0
            val focus = ProtocolExerciseLibrary.focusForDayLabel(label)
            val mainLift = ProtocolExerciseLibrary.mainLiftFor(focus, sessionIndex)
            val accessoryCount = (parts.size - 2).coerceAtLeast(0)
            val accessories = ProtocolExerciseLibrary.accessoriesFor(mainLift, weekNumber, accessoryCount)
            Session(
                id = idProvider.newId(),
                name = label,
                description = "Prescripción ${protocolBlock.intensityMin}-${protocolBlock.intensityMax}% 1RM" +
                    (protocolBlock.volumeModifier?.let { " · vol ×${"%.2f".format(it)}" } ?: ""),
                dayOfWeek = dayOfWeek,
                assignedDays = listOf(dayOfWeek),
                isMainSession = isMain,
                focus = protocolBlock.goal,
                parts = parts.mapIndexed { partIndex, partName ->
                    val lift = liftForPart(partIndex, mainLift, accessories)
                    SessionPart(
                        id = idProvider.newId(),
                        name = partName,
                        exercises = listOf(
                            prescribedExercise(
                                lift = lift,
                                partIndex = partIndex,
                                goal = goal,
                                protocolBlock = protocolBlock,
                                weekNumber = weekNumber,
                                totalWeeksInBlock = totalWeeksInBlock,
                                idProvider = idProvider,
                            ),
                        ),
                    )
                },
            )
        }
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
        protocolBlock: ProtocolBlock,
        weekNumber: Int,
        totalWeeksInBlock: Int,
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
            weekNumber = weekNumber,
            totalWeeksInBlock = totalWeeksInBlock,
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

    private fun resolveSplitId(raw: String): String {
        if (SPLIT_TEMPLATES.any { it.id == raw }) return raw
        return when (raw.lowercase()) {
            "upper-lower", "ul", "4-day", "4day" -> "ul_x4"
            "ppl" -> "ppl_x6"
            "fullbody", "full-body" -> "fullbody_x3"
            "texas_method", "texas" -> "texas_method"
            else -> raw
        }
    }

    private fun resolveSplitPattern(defaultSplit: String?): List<String> {
        val resolvedId = defaultSplit?.let { resolveSplitId(it) }
        return SPLIT_TEMPLATES.firstOrNull { it.id == resolvedId }?.pattern
            ?: SPLIT_TEMPLATES.firstOrNull { it.id == "ul_x4" }?.pattern.orEmpty()
    }
}
