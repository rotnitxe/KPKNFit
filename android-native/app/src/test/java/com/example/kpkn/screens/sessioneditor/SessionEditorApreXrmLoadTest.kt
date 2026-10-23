package com.example.kpkn.screens.sessioneditor

import com.example.kpkn.data.models.*
import com.example.kpkn.data.protocols.*
import com.example.kpkn.domain.training.*
import com.example.kpkn.screens.sessioneditor.components.withApreBlock
import org.junit.Assert.*
import org.junit.Test

class SessionEditorApreXrmLoadTest {
    private fun observedBlock(): Exercise {
        var index = 0
        val block = Exercise("press", "Press", reference1RM = 120.0, referenceTrainingMax = 100.0,
            catalogConfigurationId = CatalogIds.OHP, loadQuantityConvention = LoadQuantityConvention.TOTAL_EXTERNAL)
            .withApreBlock(ApreVariant.APRE_6) { "s${++index}" }
        return block.copy(trainingMode = TrainingMode.RM, sets = block.sets.map { it.copy(targetPercentageRM = 80.0, loadBasis = LoadBasis.PERCENT_TM) },
            loadReference = PlanLoadReference(PlanLoadReferenceKind.OBSERVED_WORKING_SET, CatalogIds.OHP,
                LoadQuantityConvention.TOTAL_EXTERNAL, repMin = 6, repMax = 6,
                state = PlanLoadReferenceState.CAPTURED, capturedLoadKg = 100.0,
                metadata = PlanLoadReferenceMetadata(origin = ExerciseLoadReferenceStore.APRE_XRM, contextKey = "exact")))
    }

    @Test fun aValidObservedApreBlockKeepsItsXrmLoadsWhenTmChanges() {
        val before = observedBlock()
        val after = before.copy(referenceTrainingMax = 200.0).withExplicitRmLoads(before)
        assertEquals(before.sets.map { it.weight }, after.sets.map { it.weight })
        assertEquals(120.0, after.reference1RM!!, 0.0)
    }

    @Test fun anInvalidMarkerDoesNotHideOrdinaryPercentRmEdits() {
        val before = observedBlock().let { it.copy(apreBlock = it.apreBlock!!.copy(setIds = listOf("missing"))) }
        val after = before.copy(referenceTrainingMax = 200.0).withExplicitRmLoads(before)
        assertTrue(after.sets.all { it.weight == 160.0 })
    }

    @Test fun aWorkingReferenceWithAnotherRangeOrOriginDoesNotSuppressRmRecalculation() {
        val original = observedBlock()
        listOf(original.loadReference!!.copy(repMax = 10), original.loadReference!!.copy(
            metadata = PlanLoadReferenceMetadata(origin = "ordinary"))).forEach { reference ->
            val before = original.copy(loadReference = reference)
            val after = before.copy(referenceTrainingMax = 200.0).withExplicitRmLoads(before)
            assertTrue(after.sets.all { it.weight == 160.0 })
        }
    }
}
