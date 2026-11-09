package com.example.kpkn.screens.sessioneditor

import com.example.kpkn.data.models.*
import com.example.kpkn.data.protocols.*
import com.example.kpkn.domain.training.*
import com.example.kpkn.screens.sessioneditor.components.withApreBlock
import com.example.kpkn.screens.sessioneditor.withoutApreBlock
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

    @Test fun exitingApreToRepsKeepsFourLoadsAndTargetsAndClearsAmrapBehavior() {
        var index = 0
        val original = Exercise("press", "Press", reference1RM = 120.0)
            .withApreBlock(ApreVariant.APRE_6) { "s${++index}" }
        val exited = original.withoutApreBlock()
        assertNull(exited.apreBlock)
        assertEquals(TrainingMode.REPS, exited.trainingMode)
        assertEquals(4, exited.sets.size)
        assertEquals(original.sets.map { it.weight }, exited.sets.map { it.weight })
        assertEquals(listOf(10, 6, 6, 6), exited.sets.map { it.targetReps })
        assertTrue(exited.sets.all { it.purpose == SetPurpose.NONE && it.amrapMinimumReps == null })
    }

    @Test fun invalidApreExitOnlyConvertsSeriesStillNamedByTheMarker() {
        var index = 0
        val original = Exercise("press", "Press", reference1RM = 120.0)
            .withApreBlock(ApreVariant.APRE_6) { "s${++index}" }
        val invalid = original.copy(
            sets = original.sets + ExerciseSet(id = "unrelated", targetReps = 8, purpose = SetPurpose.AMRAP, amrapMinimumReps = 8),
            apreBlock = ApreBlockReference(6, listOf("s1", "missing", "s3", "s4")),
        )
        val exited = invalid.withoutApreBlock()
        assertNull(exited.apreBlock)
        assertEquals(SetPurpose.NONE, exited.sets[1].purpose)
        assertEquals(SetPurpose.NONE, exited.sets[2].purpose)
        assertEquals(6, exited.sets[2].targetReps)
        assertEquals(SetPurpose.AMRAP, exited.sets[4].purpose)
        assertEquals(8, exited.sets[4].amrapMinimumReps)
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

    @Test fun changingSmartLoadBasisRecalculatesOnlyUnbasedRmSuggestions() {
        val before = Exercise(
            id = "bench",
            name = "Bench",
            trainingMode = TrainingMode.RM,
            reference1RM = 100.0,
            referenceTrainingMax = 80.0,
            smartLoadBasis = SmartLoadBasis.ONE_RM,
            sets = listOf(ExerciseSet(id = "smart", targetPercentageRM = 75.0, weight = 75.0)),
        )
        val after = before.copy(smartLoadBasis = SmartLoadBasis.TRAINING_MAX).withExplicitRmLoads(before)
        assertEquals(60.0, after.sets.single().weight!!, 0.0)

        val explicit = before.copy(sets = listOf(before.sets.single().copy(loadBasis = LoadBasis.PERCENT_1RM)))
        val explicitAfter = explicit.copy(smartLoadBasis = SmartLoadBasis.TRAINING_MAX).withExplicitRmLoads(explicit)
        assertEquals(75.0, explicitAfter.sets.single().weight!!, 0.0)
    }
}
