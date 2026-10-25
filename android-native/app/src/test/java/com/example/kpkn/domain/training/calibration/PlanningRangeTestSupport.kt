package com.example.kpkn.domain.training.calibration

import com.example.kpkn.data.models.*
import com.example.kpkn.data.protocols.*
import com.example.kpkn.domain.training.ExerciseCompositionMetadata
import com.example.kpkn.domain.training.ExerciseCompositionMetadataProvider
import com.example.kpkn.domain.training.cycle.MuscleCheckin
import com.example.kpkn.domain.training.cycle.WeeklyCheckin

internal object PlanningRangeTestSupport {
    val metadata = ExerciseCompositionMetadataProvider { id ->
        if (id != CatalogIds.BP) null else ExerciseCompositionMetadata(id, "Press", "horizontal_push",
            listOf("pectoralis"), emptyList(), null, "BILATERAL", null, "MULTIARTICULAR")
    }

    fun program(dose: Int = 20, weeks: List<Int> = (1..5).toList(), performance: Int = 2, soreness: Int = 2): Program {
        val recipes = (1..6).map { number ->
            val deload = number == 6
            val doses = mutableListOf<Int>()
            var remaining = if (deload) (dose + 1) / 2 else dose
            while (remaining > 0) { val chunk = minOf(8, remaining); doses += chunk; remaining -= chunk }
            WeekRecipe(number, 0, kind = if (deload) WeekExecutionKind.DELOAD else WeekExecutionKind.TRAINING,
                days = doses.mapIndexed { day, sets -> DayRecipe("Día ${day + 1}", id = "day-$day", weekday = day * 2 + 1,
                    slots = listOf(SlotRecipe("chest", SlotRole.T3_ACCESSORY, LiftRef(CatalogIds.BP),
                        List(sets) { SetRecipe(reps = 10, rir = if (deload) 4 else 2) }, 90))) })
        }
        return Program("p", "Plan propio", sourceRecipe = TrainingPlanRecipe("own", recipes, repeats = true),
            macrocycles = listOf(Macrocycle("m", "Macro", blocks = listOf(Block("b", "Bloque", mesocycles = listOf(
                Mesocycle("meso", "Meso", weeks = recipes.map { recipe -> ProgramWeek("w${recipe.weekNumber}", "Semana ${recipe.weekNumber}",
                    progressionIndex = recipe.weekNumber, executionKind = recipe.kind,
                    sessions = recipe.days.mapIndexed { index, day -> Session("s${recipe.weekNumber}-$index", day.label, dayOfWeek = day.weekday) }) }
            )))))),
            runState = ProgramRunState("run", cycleNumber = 2, weekId = "w1", weekInstanceId = "inst_c2_w1"),
            weeklyCheckins = weeks.map { number -> WeeklyCheckin("w${number + 1}", programRunId = "run",
                feedbackWeekId = "w$number", feedbackCycleNumber = 1,
                muscles = listOf(MuscleCheckin("CHEST", performance, soreness))) })
    }
}
