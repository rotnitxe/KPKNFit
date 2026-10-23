package com.example.kpkn.domain.training

import com.example.kpkn.data.protocols.PlanLoadReference
import com.example.kpkn.data.protocols.PlanLoadReferenceKind
import com.example.kpkn.data.protocols.PlanLoadReferenceMetadata
import com.example.kpkn.data.protocols.PlanLoadReferenceState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.BeforeClass
import org.junit.Test

class PlanMaterializerLegacyReferenceTest {
    companion object {
        @JvmStatic @BeforeClass fun install() { CatalogCompositionTestSupport.install() }
    }

    @Test fun legacySameConfigurationOccurrencesReadTheirOwnLatestCaptureBeforeIdentityAdoption() {
        val original = Cal5TestSupport.recipeOf("wendler-531-bbb")
        val week = original.weeks.first()
        val day = week.days.first()
        assertNull("receta legacy real", day.id)
        val first = day.slots.first()
        val request = PlanLoadReference(PlanLoadReferenceKind.EXERCISE_TM, first.lift.configurationId)
        val supplemental = day.slots.first { it.supplementalOf == first.id }
        assertEquals(first.lift.configurationId, supplemental.lift.configurationId)
        // Keep the published recipe intact: all days, doses, AMRAP purposes and
        // the real BBB repetition linked to t1; only declare the pending bases.
        val recipe = original.copy(weeks = original.weeks.mapIndexed { wi, currentWeek ->
            if (wi != 0) currentWeek else currentWeek.copy(days = currentWeek.days.mapIndexed { di, currentDay ->
                if (di != 0) currentDay else currentDay.copy(slots = currentDay.slots.map { slot ->
                    if (slot.id !in setOf(first.id, supplemental.id)) slot else
                        slot.copy(sets = slot.sets.map { it.copy(reference = request) })
                })
            })
        })
        var program = Cal5TestSupport.materialize(recipe)
        val persistedWeek = Cal5TestSupport.weeksOf(program).first()
        val exercises = persistedWeek.sessions.first().allExercises()
            .filter { it.catalogConfigurationId == first.lift.configurationId }
        assertEquals(2, exercises.size)
        fun capture(index: Int, kg: Double, at: Long) {
            program = ExerciseLoadReferenceStore.appendCapture(program, setOf(exercises[index].id), request.copy(
                state = PlanLoadReferenceState.CAPTURED, capturedLoadKg = kg, capturedAtMs = at,
                metadata = PlanLoadReferenceMetadata(sourceId = "capture-$index-$at"),
            ))
        }
        capture(0, 120.0, 10)
        capture(0, 160.0, 30)
        capture(0, 130.0, 20) // inserted later, but older evidence must not win
        capture(1, 140.0, 40)
        val rebuilt = PlanMaterializer.rematerializeWeek(program, persistedWeek.id, recipe,
            metadata = CatalogCompositionTestSupport.metadata)
        val result = Cal5TestSupport.weeksOf(rebuilt).first().sessions.first().allExercises()
            .filter { it.catalogConfigurationId == first.lift.configurationId }
        assertEquals(exercises.map { it.id }, result.map { it.id })
        assertEquals(160.0, result[0].referenceTrainingMax!!, 0.0001)
        assertEquals(140.0, result[1].referenceTrainingMax!!, 0.0001)
        assertEquals(104.0, result[0].sets.first().weight!!, 0.0001) // 65% of TM160
        assertEquals(70.0, result[1].sets.first().weight!!, 0.0001) // BBB 50% of TM140
        assertEquals(program.exerciseLoadReferences, rebuilt.exerciseLoadReferences)
    }
}
