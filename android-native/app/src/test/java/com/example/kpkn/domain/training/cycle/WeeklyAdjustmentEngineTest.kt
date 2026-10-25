package com.example.kpkn.domain.training.cycle

import com.example.kpkn.data.models.VolumeRecommendation
import com.example.kpkn.data.models.WeekExecutionKind
import com.example.kpkn.data.programs.KpknMuscleGroup
import com.example.kpkn.data.protocols.*
import com.example.kpkn.domain.training.ExerciseCompositionMetadata
import com.example.kpkn.domain.training.ExerciseCompositionMetadataProvider
import org.junit.Assert.*
import org.junit.Test

class WeeklyAdjustmentEngineTest {
    private val metadata = ExerciseCompositionMetadataProvider { id ->
        ExerciseCompositionMetadata(id, id, if (id == "row") "horizontal_pull" else "horizontal_push",
            listOf(if (id == "row") "latissimus_dorsi" else "pectoralis"), emptyList(), null, "BILATERAL", null, "MULTIARTICULAR")
    }
    private fun week(sets: Int = 3, rir: Int = 2) = WeekRecipe(1, 0, days = listOf(DayRecipe("A", listOf(
        SlotRecipe("main", SlotRole.T1_MAIN, LiftRef("bench"), List(4) { SetRecipe(reps = 5, rir = 2) }, 180),
        SlotRecipe("accessory", SlotRole.T3_ACCESSORY, LiftRef("row"), List(sets) { SetRecipe(reps = 10, rir = rir) }, 90),
    ))))
    private fun answer(p: Int, s: Int, group: String = "BACK_LATS") =
        WeeklyCheckin("w", muscles = listOf(MuscleCheckin(group, p, s)))
    private fun row(week: WeekRecipe) = week.days.single().slots.last()
    private fun adjust(p: Int, s: Int) = WeeklyAdjustmentEngine.adjust(week(), answer(p, s), metadata,
        // Plan sintético con 3 series: límite explícito 2, no el mínimo editorial de 10.
        listOf(VolumeRecommendation("Dorsales", 2, 4, 25)))

    @Test fun response_matrix_has_all_sixteen_combinations() {
        for (p in 1..4) for (s in 1..4) {
            val expected = when { p == 4 -> -1 to 1; s == 4 -> -1 to 0; p == 3 || s == 3 -> 0 to 0; p == 1 -> 1 to -1; else -> 1 to 0 }
            assertEquals(expected, WeeklyAdjustmentEngine.decide(p, s))
        }
    }
    @Test fun easy_adds_one_accessory_set_and_one_less_rir() {
        val result = adjust(1, 1)
        assertEquals(KpknMuscleGroup.BACK_LATS, result.changes.single().muscleGroup)
        assertEquals(4, row(result.weekRecipe).sets.size)
        assertTrue(row(result.weekRecipe).sets.all { it.rir == 1 })
        assertEquals(week().days.single().slots.first(), result.weekRecipe.days.single().slots.first())
        assertEquals(1, result.changes.single().setsDelta)
        assertEquals(-1, result.changes.single().rirDelta)
    }
    @Test fun met_plan_adds_one_set_without_changing_rir_and_difficult_keeps_recipe() {
        val result = adjust(2, 2)
        assertEquals(4, row(result.weekRecipe).sets.size)
        assertTrue(row(result.weekRecipe).sets.all { it.rir == 2 })
        assertEquals(week(), adjust(3, 1).weekRecipe)
    }
    @Test fun lower_performance_reduces_one_set_only_within_the_declared_floor() {
        val result = adjust(4, 1)
        assertEquals(2, row(result.weekRecipe).sets.size)
        assertTrue(row(result.weekRecipe).sets.all { it.rir == 3 })
        // No inventa permiso para reducir por debajo del mínimo editorial.
        val bounded = WeeklyAdjustmentEngine.adjust(week(), answer(4, 1), metadata)
        assertEquals(3, row(bounded.weekRecipe).sets.size)
    }
    @Test fun group_without_accessories_and_skipped_answer_change_nothing() {
        assertTrue(WeeklyAdjustmentEngine.adjust(week(), answer(1, 1, "CHEST"), metadata).changes.isEmpty())
        val original = week()
        assertSame(original, WeeklyAdjustmentEngine.adjust(original, answer(1, 1).copy(skipped = true), metadata).weekRecipe)
    }
    @Test fun planning_ceiling_still_allows_rir_proposal() {
        val result = WeeklyAdjustmentEngine.adjust(week(), answer(1, 1), metadata,
            listOf(VolumeRecommendation("Dorsales", 2, 3, 3)))
        assertEquals(3, row(result.weekRecipe).sets.size)
        assertEquals(0, result.changes.single().setsDelta)
        assertEquals(-1, result.changes.single().rirDelta)
        assertTrue(result.changes.single().reason.contains("tope"))
    }
    @Test fun global_answer_uses_accessory_groups_only() {
        assertEquals(adjust(1, 1).weekRecipe, WeeklyAdjustmentEngine.adjust(week(),
            WeeklyCheckin("w", globalPerformance = 1, globalSoreness = 1), metadata).weekRecipe)
    }
    @Test fun invalid_duplicate_and_unknown_answers_are_not_applied() {
        listOf(answer(0, 1), answer(1, 5), answer(1, 1, "lats"),
            answer(1, 1).let { it.copy(muscles = it.muscles + it.muscles) }).forEach {
            assertTrue(WeeklyAdjustmentEngine.adjust(week(), it, metadata).changes.isEmpty())
        }
    }
    @Test fun deload_maximum_sets_and_saturated_rir_do_not_claim_a_change() {
        val full = week(8, 1)
        assertTrue(WeeklyAdjustmentEngine.adjust(full, answer(1, 1), metadata).changes.isEmpty())
        val deload = week().copy(kind = WeekExecutionKind.DELOAD)
        assertSame(deload, WeeklyAdjustmentEngine.adjust(deload, answer(1, 1), metadata).weekRecipe)
    }
    @Test fun warmups_and_main_sets_are_preserved_when_rir_changes_are_disabled() {
        val base = week()
        val old = row(base)
        val warmup = SetRecipe(reps = 10, rir = 4, isWarmup = true)
        val source = base.copy(days = listOf(base.days.single().copy(slots = listOf(base.days.single().slots.first(), old.copy(sets = listOf(warmup) + old.sets)))))
        val result = WeeklyAdjustmentEngine.adjust(source, answer(1, 1), metadata, allowRirChange = false)
        assertEquals(warmup, row(result.weekRecipe).sets.first())
        assertEquals(4, row(result.weekRecipe).sets.count { !it.isWarmup })
        assertTrue(row(result.weekRecipe).sets.filterNot { it.isWarmup }.all { it.rir == 2 })
        assertEquals(0, result.changes.single().rirDelta)
    }

    @Test fun shared_primary_muscles_do_not_add_twice_or_receive_conflicting_changes() {
        val shared = ExerciseCompositionMetadataProvider { id -> metadata.metadata(id)?.let {
            if (id == "row") it.copy(primaryMuscles = listOf("latissimus_dorsi", "pectoralis")) else it
        } }
        val global = WeeklyCheckin("w", globalPerformance = 1, globalSoreness = 1)
        val result = WeeklyAdjustmentEngine.adjust(week(), global, shared)
        assertEquals(4, row(result.weekRecipe).sets.size)
        assertTrue(row(result.weekRecipe).sets.all { it.rir == 1 })
        val conflicting = WeeklyCheckin("w", muscles = listOf(MuscleCheckin("CHEST", 1, 1), MuscleCheckin("BACK_LATS", 3, 3)))
        assertTrue(WeeklyAdjustmentEngine.adjust(week(), conflicting, shared).changes.isEmpty())
    }
}
