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
    val recipe: com.example.kpkn.data.protocols.TrainingPlanRecipe? = null,
    val defaultSplit: String? = null,
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
        name = "PL Principiante 3 días · 12 sem",
        description = "12 semanas, 3 días SBD: LP a intermedio con receta explícita, cero exenciones.",
        emoji = "\uD83C\uDFCB\uFE0F",
        type = ProgramStructure.COMPLEX,
        weeks = 12,
        trackLabel = "Powerlifting",
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
        recipe = KpknAdvancedProgramRecipes.plBeginner12(),
        defaultSplit = "pl_sbd_x3",
    ),
    ProgramTemplateOption(
        id = "power-16-4",
        name = "PL Intermedio 4 días · 16 sem",
        description = "16 semanas, 4 días: hipertrofia específica 5, fuerza 5, pico 4 y taper/test 2.",
        emoji = "\uD83C\uDFCB\uFE0F",
        type = ProgramStructure.COMPLEX,
        weeks = 16,
        trackLabel = "Powerlifting",
        blockNames = listOf("Hipertrofia específica", "Fuerza", "Pico", "Taper/Test"),
        blockWeekCounts = listOf(5, 5, 4, 2),
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
        recipe = KpknAdvancedProgramRecipes.plIntermediate16(),
        defaultSplit = "pl_classic_4",
    ),
    ProgramTemplateOption(
        id = "power-20-5",
        name = "PL Avanzado 5 días · 20 sem",
        description = "20 semanas, 5 días: acumulación 6, transmutación 5, realización 4, pico 3 y taper 2.",
        emoji = "\uD83C\uDFCB\uFE0F",
        type = ProgramStructure.COMPLEX,
        weeks = 20,
        trackLabel = "Powerlifting",
        blockNames = listOf("Acumulación", "Transmutación", "Realización", "Pico", "Taper"),
        blockWeekCounts = listOf(6, 5, 4, 3, 2),
        blockGoals = listOf(
            MesocycleGoal.ACCUMULATION,
            MesocycleGoal.INTENSIFICATION,
            MesocycleGoal.INTENSIFICATION,
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
        recipe = KpknAdvancedProgramRecipes.plAdvanced20(),
        defaultSplit = "pl_hf_bench",
    ),
    ProgramTemplateOption(
        id = "powerbuild-16-4",
        name = "Acumulación + Fuerza + Hipertrofia dirigida + Realización",
        description = "16 semanas totales en 4 bloques. Propuesta larga para fuerza y físico.",
        emoji = "\uD83D\uDD25",
        type = ProgramStructure.COMPLEX,
        weeks = 16,
        trackLabel = "Powerbuilding",
        audienceLabel = "Avanzado",
        blockNames = listOf("Acumulación", "Fuerza", "Hipertrofia dirigida", "Realización"),
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
        recipe = KpknAdvancedProgramRecipes.powerbuilding16(),
        defaultSplit = "ppl_ul",
    ),
    ProgramTemplateOption(
        id = "body-12-3",
        name = "Hipertrofia UL 4 días · 12 sem",
        description = "12 semanas, 4 días upper/lower: volumen 5 + descarga, intensificación 5 + descarga.",
        emoji = "\uD83D\uDCAA",
        type = ProgramStructure.COMPLEX,
        weeks = 12,
        trackLabel = "Culturismo",
        audienceLabel = "Intermedio",
        blockNames = listOf("Volumen", "Descarga", "Intensificación", "Descarga final"),
        blockWeekCounts = listOf(5, 1, 5, 1),
        blockGoals = listOf(
            MesocycleGoal.ACCUMULATION,
            MesocycleGoal.DELOAD,
            MesocycleGoal.INTENSIFICATION,
            MesocycleGoal.DELOAD,
        ),
        blockGoalSemantics = listOf(
            BlockGoal.ACCUMULATION,
            BlockGoal.DELOAD,
            BlockGoal.INTENSIFICATION,
            BlockGoal.DELOAD,
        ),
        recipe = KpknAdvancedProgramRecipes.hypertrophyUl12(),
        defaultSplit = "ul_x4",
    ),
    ProgramTemplateOption(
        id = "body-16-4",
        name = "PPL 6 días · 16 sem",
        description = "16 semanas, 6 días PPL: volumen, especialización, definición y pico de hipertrofia.",
        emoji = "\uD83D\uDCAA",
        type = ProgramStructure.COMPLEX,
        weeks = 16,
        trackLabel = "Culturismo",
        audienceLabel = "Avanzado",
        blockNames = listOf("Volumen largo", "Especialización", "Definición", "Pico de hipertrofia"),
        blockWeekCounts = listOf(4, 4, 4, 4),
        blockGoals = listOf(
            MesocycleGoal.ACCUMULATION,
            MesocycleGoal.ACCUMULATION,
            MesocycleGoal.INTENSIFICATION,
            MesocycleGoal.REALIZATION,
        ),
        blockGoalSemantics = listOf(
            BlockGoal.ACCUMULATION,
            BlockGoal.ACCUMULATION,
            BlockGoal.DENSITY,
            BlockGoal.INTENSIFICATION,
        ),
        recipe = KpknAdvancedProgramRecipes.ppl16(),
        defaultSplit = "ppl_x6",
    ),
    ProgramTemplateOption(
        id = "body-20-5",
        name = "Off-season 5 días · 20 sem",
        description = "20 semanas, 5 días off-season con rotación de énfasis por bloque.",
        emoji = "\uD83D\uDCAA",
        type = ProgramStructure.COMPLEX,
        weeks = 20,
        trackLabel = "Culturismo",
        audienceLabel = "Avanzado",
        blockNames = listOf("Off-season", "Volumen", "Especialización", "Definición", "Pico de hipertrofia"),
        blockWeekCounts = listOf(4, 4, 4, 4, 4),
        blockGoals = listOf(
            MesocycleGoal.ACCUMULATION,
            MesocycleGoal.ACCUMULATION,
            MesocycleGoal.INTENSIFICATION,
            MesocycleGoal.INTENSIFICATION,
            MesocycleGoal.REALIZATION,
        ),
        blockGoalSemantics = listOf(
            BlockGoal.ACCUMULATION,
            BlockGoal.ACCUMULATION,
            BlockGoal.INTENSIFICATION,
            BlockGoal.DENSITY,
            BlockGoal.INTENSIFICATION,
        ),
        recipe = KpknAdvancedProgramRecipes.offSeason20(),
        defaultSplit = "arnold_ul",
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
