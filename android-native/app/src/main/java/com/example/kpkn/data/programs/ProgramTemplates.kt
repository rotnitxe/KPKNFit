package com.example.kpkn.data.programs

import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.BlockGoal
import com.example.kpkn.data.models.BlockProgressionScheme
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.MesocycleGoal
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramStructure
import com.example.kpkn.data.models.ProgramWeek
import java.util.UUID

data class ProgramTemplateOption(
    val id: String,
    val name: String,
    val description: String,
    val emoji: String,
    val type: ProgramStructure,
    val weeks: Int,
    val trackLabel: String? = null,
    val audienceLabel: String? = null,
    val isDefault: Boolean = false,
    val blockNames: List<String> = emptyList(),
    val blockWeekCounts: List<Int> = emptyList(),
    val blockGoals: List<MesocycleGoal> = emptyList(),
    /** Semántica explícita de bloque; legacy [blockGoals] sigue siendo el fallback. */
    val blockGoalSemantics: List<BlockGoal> = emptyList(),
)

private fun genericBlockNames(count: Int): List<String> = (1..count).map { "Bloque $it" }

val PROGRAM_TEMPLATES: List<ProgramTemplateOption> = listOf(
    ProgramTemplateOption(
        id = "simple-1",
        name = "1 Semana",
        description = "Programa simple de 1 semana.",
        emoji = "\uD83D\uDCC8",
        type = ProgramStructure.SIMPLE,
        weeks = 1,
        isDefault = true,
        blockNames = listOf("Bloque Único"),
        blockWeekCounts = listOf(1),
        blockGoals = listOf(MesocycleGoal.ACCUMULATION),
    ),
    ProgramTemplateOption(
        id = "simple-ab",
        name = "Semana A/B",
        description = "Programa simple de 2 semanas.",
        emoji = "\uD83C\uDF0A",
        type = ProgramStructure.SIMPLE,
        weeks = 2,
        blockNames = listOf("Bloque A/B"),
        blockWeekCounts = listOf(2),
        blockGoals = listOf(MesocycleGoal.ACCUMULATION),
    ),
    ProgramTemplateOption(
        id = "simple-4",
        name = "4 Semanas",
        description = "Programa simple de 4 semanas.",
        emoji = "\uD83D\uDDD3\uFE0F",
        type = ProgramStructure.SIMPLE,
        weeks = 4,
        blockNames = listOf("Bloque 4 Semanas"),
        blockWeekCounts = listOf(4),
        blockGoals = listOf(MesocycleGoal.ACCUMULATION),
    ),
    ProgramTemplateOption(
        id = "power-12-3",
        name = "Base + Intensificación + Peak",
        description = "12 semanas totales en 3 bloques. Propuesta corta para preparación específica.",
        emoji = "\uD83C\uDFCB\uFE0F",
        type = ProgramStructure.COMPLEX,
        weeks = 12,
        trackLabel = "Powerlifting",
        audienceLabel = "Competición / avanzado",
        blockNames = listOf("Base", "Intensificación", "Peak"),
        blockWeekCounts = listOf(4, 4, 4),
        blockGoals = listOf(
            MesocycleGoal.ACCUMULATION,
            MesocycleGoal.INTENSIFICATION,
            MesocycleGoal.REALIZATION,
        ),
        blockGoalSemantics = listOf(
            BlockGoal.ACCUMULATION,
            BlockGoal.INTENSIFICATION,
            BlockGoal.PEAK,
        ),
    ),
    ProgramTemplateOption(
        id = "power-16-4",
        name = "Acumulación + Fuerza + Peak + Taper",
        description = "16 semanas totales en 4 bloques. Propuesta media con cierre competitivo.",
        emoji = "\uD83C\uDFCB\uFE0F",
        type = ProgramStructure.COMPLEX,
        weeks = 16,
        trackLabel = "Powerlifting",
        audienceLabel = "Competición / avanzado",
        blockNames = listOf("Acumulación", "Fuerza", "Peak", "Taper"),
        blockWeekCounts = listOf(4, 4, 5, 3),
        blockGoals = listOf(
            MesocycleGoal.ACCUMULATION,
            MesocycleGoal.INTENSIFICATION,
            MesocycleGoal.REALIZATION,
            MesocycleGoal.DELOAD,
        ),
        blockGoalSemantics = listOf(
            BlockGoal.ACCUMULATION,
            BlockGoal.INTENSIFICATION,
            BlockGoal.PEAK,
            BlockGoal.TAPER,
        ),
    ),
    ProgramTemplateOption(
        id = "power-20-5",
        name = "Base larga + Fuerza + Específico + Peak + Taper",
        description = "20 semanas totales en 5 bloques. Propuesta larga para ciclos competitivos.",
        emoji = "\uD83C\uDFCB\uFE0F",
        type = ProgramStructure.COMPLEX,
        weeks = 20,
        trackLabel = "Powerlifting",
        audienceLabel = "Competición / avanzado",
        blockNames = listOf("Base larga", "Fuerza", "Específico", "Peak", "Taper"),
        blockWeekCounts = listOf(4, 4, 4, 4, 4),
        blockGoals = listOf(
            MesocycleGoal.ACCUMULATION,
            MesocycleGoal.INTENSIFICATION,
            MesocycleGoal.REALIZATION,
            MesocycleGoal.REALIZATION,
            MesocycleGoal.DELOAD,
        ),
        blockGoalSemantics = listOf(
            BlockGoal.ACCUMULATION,
            BlockGoal.INTENSIFICATION,
            BlockGoal.SPECIFICITY,
            BlockGoal.PEAK,
            BlockGoal.TAPER,
        ),
    ),
    ProgramTemplateOption(
        id = "powerbuild-12-3",
        name = "Base híbrida + Intensificación + Peak",
        description = "12 semanas totales en 3 bloques. Propuesta híbrida entre fuerza y masa.",
        emoji = "\uD83D\uDD25",
        type = ProgramStructure.COMPLEX,
        weeks = 12,
        trackLabel = "Powerbuilding",
        audienceLabel = "Avanzado",
        blockNames = listOf("Base híbrida", "Intensificación", "Peak"),
        blockWeekCounts = listOf(4, 4, 4),
        blockGoals = listOf(
            MesocycleGoal.ACCUMULATION,
            MesocycleGoal.INTENSIFICATION,
            MesocycleGoal.REALIZATION,
        ),
        blockGoalSemantics = listOf(
            BlockGoal.ACCUMULATION,
            BlockGoal.INTENSIFICATION,
            BlockGoal.PEAK,
        ),
    ),
    ProgramTemplateOption(
        id = "powerbuild-16-4",
        name = "Acumulación + Fuerza + Hipertrofia dirigida + Peak",
        description = "16 semanas totales en 4 bloques. Propuesta larga para fuerza y físico.",
        emoji = "\uD83D\uDD25",
        type = ProgramStructure.COMPLEX,
        weeks = 16,
        trackLabel = "Powerbuilding",
        audienceLabel = "Avanzado",
        blockNames = listOf("Acumulación", "Fuerza", "Hipertrofia dirigida", "Peak"),
        blockWeekCounts = listOf(4, 4, 4, 4),
        blockGoals = listOf(
            MesocycleGoal.ACCUMULATION,
            MesocycleGoal.INTENSIFICATION,
            MesocycleGoal.ACCUMULATION,
            MesocycleGoal.REALIZATION,
        ),
        // Hipertrofia dirigida is a specificity block, not a phase
        // regression back to accumulation.  The legacy mesocycle goal stays
        // ACCUMULATION for serialized compatibility, while the executable
        // block semantic keeps the phase order monotonic.
        blockGoalSemantics = listOf(
            BlockGoal.ACCUMULATION,
            BlockGoal.INTENSIFICATION,
            BlockGoal.SPECIFICITY,
            BlockGoal.REALIZATION,
        ),
    ),
    ProgramTemplateOption(
        id = "body-12-3",
        name = "Volumen + Intensificación + Peak",
        description = "12 semanas totales en 3 bloques. Propuesta corta para competir o cerrar etapa.",
        emoji = "\uD83D\uDCAA",
        type = ProgramStructure.COMPLEX,
        weeks = 12,
        trackLabel = "Culturismo",
        audienceLabel = "Competición / avanzado",
        blockNames = listOf("Volumen", "Intensificación", "Peak"),
        blockWeekCounts = listOf(5, 4, 3),
        blockGoals = listOf(
            MesocycleGoal.ACCUMULATION,
            MesocycleGoal.INTENSIFICATION,
            MesocycleGoal.REALIZATION,
        ),
    ),
    ProgramTemplateOption(
        id = "body-16-4",
        name = "Volumen largo + Especialización + Definición + Peak",
        description = "16 semanas totales en 4 bloques. Propuesta media para preparación estética.",
        emoji = "\uD83D\uDCAA",
        type = ProgramStructure.COMPLEX,
        weeks = 16,
        trackLabel = "Culturismo",
        audienceLabel = "Competición / avanzado",
        blockNames = listOf("Volumen largo", "Especialización", "Definición", "Peak"),
        blockWeekCounts = listOf(4, 4, 4, 4),
        blockGoals = listOf(
            MesocycleGoal.ACCUMULATION,
            MesocycleGoal.ACCUMULATION,
            MesocycleGoal.INTENSIFICATION,
            MesocycleGoal.REALIZATION,
        ),
    ),
    ProgramTemplateOption(
        id = "body-20-5",
        name = "Off-season + Volumen + Especialización + Definición + Peak",
        description = "20 semanas totales en 5 bloques. Propuesta larga para preparación estética completa.",
        emoji = "\uD83D\uDCAA",
        type = ProgramStructure.COMPLEX,
        weeks = 20,
        trackLabel = "Culturismo",
        audienceLabel = "Competición / avanzado",
        blockNames = listOf("Off-season", "Volumen", "Especialización", "Definición", "Peak"),
        blockWeekCounts = listOf(4, 4, 4, 4, 4),
        blockGoals = listOf(
            MesocycleGoal.ACCUMULATION,
            MesocycleGoal.ACCUMULATION,
            MesocycleGoal.INTENSIFICATION,
            MesocycleGoal.INTENSIFICATION,
            MesocycleGoal.REALIZATION,
        ),
    ),
)

fun resolveProgramTemplate(templateId: String?): ProgramTemplateOption {
    return PROGRAM_TEMPLATES.find { it.id == templateId } ?: PROGRAM_TEMPLATES.first()
}

fun ProgramTemplateOption.buildProgramDraft(baseProgram: Program): Program {
    val blockLabels = if (blockNames.isNotEmpty()) blockNames else listOf("Bloque Único")
    val blockDurations = if (blockWeekCounts.isNotEmpty()) blockWeekCounts else listOf(weeks.coerceAtLeast(1))

    val blocks = blockLabels.mapIndexed { index, blockName ->
        val duration = blockDurations.getOrElse(index) { blockDurations.lastOrNull() ?: 1 }.coerceAtLeast(1)
        val goal = blockGoals.getOrElse(index) {
            when {
                index == 0 -> MesocycleGoal.ACCUMULATION
                index == blockLabels.lastIndex -> MesocycleGoal.REALIZATION
                else -> MesocycleGoal.INTENSIFICATION
            }
        }
        val blockGoal = blockGoalSemantics.getOrNull(index) ?: when (goal) {
            MesocycleGoal.ACCUMULATION -> BlockGoal.ACCUMULATION
            MesocycleGoal.INTENSIFICATION -> BlockGoal.INTENSIFICATION
            MesocycleGoal.REALIZATION -> BlockGoal.REALIZATION
            MesocycleGoal.DELOAD -> BlockGoal.DELOAD
            MesocycleGoal.CUSTOM -> BlockGoal.CUSTOM
        }
        val scheme = when (blockGoal) {
            BlockGoal.TAPER, BlockGoal.DELOAD -> BlockProgressionScheme.PERCENT_RM
            BlockGoal.REALIZATION, BlockGoal.PEAK -> BlockProgressionScheme.RPE_CAP
            BlockGoal.SPECIFICITY -> BlockProgressionScheme.PERCENT_RM
            else -> BlockProgressionScheme.PERCENT_RM
        }

        Block(
            id = UUID.randomUUID().toString(),
            name = blockName,
            goal = blockGoal,
            progressionScheme = scheme,
            mesocycles = listOf(
                Mesocycle(
                    id = UUID.randomUUID().toString(),
                    name = blockName,
                    goal = goal,
                    weeks = (1..duration).map { weekIndex ->
                        ProgramWeek(
                            id = UUID.randomUUID().toString(),
                            name = "Semana $weekIndex",
                            progressionIndex = weekIndex,
                        )
                    },
                ),
            ),
        )
    }

    return baseProgram.copy(
        structure = type,
        structureTemplateId = id,
        weekDays = baseProgram.weekDays ?: 7,
        // A new structure owns a new executable cursor. Never carry an active
        // run, loops or calendar break state into a graph with fresh IDs.
        runState = null,
        loops = emptyList(),
        loopState = null,
        loopOccurrences = emptyList(),
        events = emptyList(),
        calendarBreaks = emptyList(),
        pausedCyclicSnapshot = null,
        blockSplitSelections = emptyMap(),
        weekSplitSelections = emptyMap(),
        macrocycles = listOf(
            Macrocycle(
                id = UUID.randomUUID().toString(),
                name = name,
                blocks = blocks,
            ),
        ),
    )
}
