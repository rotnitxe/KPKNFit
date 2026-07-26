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
        return program.copy(
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
            macrocycles = listOf(
                Macrocycle(
                    id = idProvider.newId(),
                    name = protocol.name,
                    blocks = blocks,
                ),
            ),
        ).alignTemporalMetadata()
    }

    private fun buildBlock(
        protocolBlock: ProtocolBlock,
        splitPattern: List<String>,
        sessionParts: List<String>,
        protocol: Protocol,
        idProvider: IdProvider,
    ): Block {
        val goal = resolveGoal(protocolBlock.goal)
        val intensityMid = ((protocolBlock.intensityMin + protocolBlock.intensityMax) / 2.0)
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
                    weeks = (1..protocolBlock.weeks.coerceAtLeast(1)).map { weekNumber ->
                        ProgramWeek(
                            id = idProvider.newId(),
                            name = "Semana $weekNumber",
                            description = "Objetivo ${protocolBlock.goal} · ${protocolBlock.intensityMin}-${protocolBlock.intensityMax}% 1RM",
                            sessions = buildSessions(
                                splitPattern = splitPattern,
                                parts = sessionParts,
                                protocol = protocol,
                                protocolBlock = protocolBlock,
                                intensityMid = intensityMid,
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
        intensityMid: Double,
        idProvider: IdProvider,
    ): List<Session> {
        val trainingDays = splitPattern
            .mapIndexedNotNull { index, label ->
                if (label.equals("Descanso", ignoreCase = true)) null else (index + 1) to label
            }
            .ifEmpty { listOf(1 to protocol.name) }

        return trainingDays.mapIndexed { sessionIndex, (dayOfWeek, label) ->
            val isMain = sessionIndex == 0
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
                    SessionPart(
                        id = idProvider.newId(),
                        name = partName,
                        exercises = listOf(
                            placeholderExercise(
                                partName = partName,
                                partIndex = partIndex,
                                intensityMid = intensityMid,
                                idProvider = idProvider,
                            ),
                        ),
                    )
                },
            )
        }
    }

    /**
     * Plantilla ejecutable mínima: un ejercicio por parte con series %1RM,
     * para que el editor/workout no reciba semanas vacías.
     */
    private fun placeholderExercise(
        partName: String,
        partIndex: Int,
        intensityMid: Double,
        idProvider: IdProvider,
    ): Exercise {
        val sets = (1..3).map { setIdx ->
            val pct = (intensityMid - 5.0 + setIdx * 2.5).coerceIn(40.0, 100.0)
            ExerciseSet(
                id = idProvider.newId(),
                targetReps = when (partIndex) {
                    0 -> 5
                    1 -> 8
                    else -> 10
                },
                targetPercentageRM = pct,
                targetRPE = if (partIndex == 0) 8.0 else 7.0,
            )
        }
        return Exercise(
            id = idProvider.newId(),
            name = "$partName · movimiento base",
            sets = sets,
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
