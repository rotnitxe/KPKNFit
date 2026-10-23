package com.example.kpkn.domain.training

import com.example.kpkn.data.protocols.LoadBasis
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
        val slot = first.copy(sets = first.sets.filterNot { it.isWarmup }.take(1).map {
            it.copy(percent = 80.0, loadBasis = LoadBasis.PERCENT_TM, reference = request)
        })
        val recipe = original.copy(weeks = listOf(week.copy(days = listOf(day.copy(
            slots = listOf(slot, slot.copy(id = "other")),
        )))))
        var program = Cal5TestSupport.materialize(recipe)
        val persistedWeek = Cal5TestSupport.weeksOf(program).single()
        val exercises = persistedWeek.sessions.single().allExercises()
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
        val result = Cal5TestSupport.weeksOf(rebuilt).single().sessions.single().allExercises()
        assertEquals(exercises.map { it.id }, result.map { it.id })
        assertEquals(160.0, result[0].referenceTrainingMax!!, 0.0001)
        assertEquals(140.0, result[1].referenceTrainingMax!!, 0.0001)
        assertEquals(128.0, result[0].sets.single().weight!!, 0.0001)
        assertEquals(112.0, result[1].sets.single().weight!!, 0.0001)
        assertEquals(program.exerciseLoadReferences, rebuilt.exerciseLoadReferences)
    }
}
