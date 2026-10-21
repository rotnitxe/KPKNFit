package com.example.kpkn.domain.calibration

import com.example.kpkn.data.models.CalibrationScope
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.LoadQuantityConvention
import com.example.kpkn.domain.calculations.CAPACITY_ESTIMATOR_VERSION
import com.example.kpkn.domain.calculations.calculateHybrid1RM
import org.junit.Assert.*
import org.junit.Test

class CalibrationEngineTest {
    private val context = CalibrationContext("bench-config", "barbell-20kg", loadQuantityConvention = LoadQuantityConvention.TOTAL_EXTERNAL)
    private val source = RecordedCalibrationSet("S1", "bench", context, 69.0, 10, declaredRir = 1)
    private val pending = (2..4).map { PendingCalibrationSet("S$it", "bench", context, 60.0, targetReps = 12, targetRir = 2) }
    private val now = 100L * 24 * 60 * 60 * 1000

    @Test fun raisesTheV7SetsUsingTheCal1EstimatorAndTheDeclaredReserve() {
        val decision = calibrate()
        assertEquals(11, decision.effectiveRepsToFailure)
        assertEquals(calculateHybrid1RM(69.0, 11), decision.capacityKg!!, 0.0)
        assertEquals(94.3, decision.capacityKg!!, 0.0)
        assertEquals(88.0, decision.referenceKg!!, 0.0)
        assertEquals(CalibrationReferenceSource.DECLARED_1RM, decision.referenceSource)
        assertEquals(1.071590909090909, decision.ratio!!, 1e-12)
        assertEquals(7.1590909090909, decision.improvementPercent!!, 1e-10)
        assertEquals(1.05, decision.factor, 0.0)
        assertEquals(CalibrationDecisionType.RAISE, decision.type)
        assertEquals(listOf("S2", "S3", "S4"), decision.affectedSets.map { it.setId })
        assertTrue(decision.affectedSets.all { it.beforeKg == 60.0 && it.afterKg == 62.5 })
        assertEquals(CAPACITY_ESTIMATOR_VERSION, decision.estimatorVersion)
        assertEquals(CalibrationPolicy.defaults, decision.policy)
        assertEquals(CalibrationScope.SESSION, decision.scope)
        assertNull(decision.note)
        assertTrue(decision.reason.isNotBlank())
    }

    @Test fun identicalCapacityAndReferenceHoldEvenWithAnOffStepBase() {
        val decision = calibrate(reference = 94.3, sets = pending.map { it.copy(baseLoadKg = 61.0, currentLoadKg = 61.0) })
        assertEquals(1.0, decision.ratio!!, 0.0)
        assertEquals(1.0, decision.factor, 0.0)
        assertEquals(CalibrationDecisionType.HOLD, decision.type)
        assertTrue(decision.affectedSets.all { it.beforeKg == 61.0 && it.afterKg == 61.0 })
    }

    @Test fun theDeadZoneKeepsLoadsUnchangedOnBothSidesOfTheReference() {
        for (ratio in listOf(0.97001, 0.99, 1.0, 1.02499)) {
            val decision = calibrate(reference = 94.3 / ratio)
            assertEquals(ratio, decision.ratio!!, 1e-12)
            assertEquals(1.0, decision.factor, 0.0)
            assertEquals(CalibrationDecisionType.HOLD, decision.type)
            assertTrue(decision.affectedSets.all { it.afterKg == 60.0 })
        }
    }

    @Test fun limitsTheDropBeforeRoundingRatherThanLimitingTheRoundedLoadAgain() {
        val decision = calibrate(recorded = source.copy(externalLoadKg = 60.0, declaredRir = 0))
        assertEquals(80.0, decision.capacityKg!!, 0.0)
        assertEquals(0.9090909090909091, decision.ratio!!, 1e-12)
        assertEquals(0.92, decision.factor, 0.0)
        assertEquals(CalibrationDecisionType.LOWER, decision.type)
        assertTrue(decision.affectedSets.all { it.beforeKg == 60.0 && it.afterKg == 55.0 })
    }

    @Test fun firstReferenceUsesTheExistingInverseWithEachSetsRepsAndReserve() {
        val sets = listOf(pending[0], pending[1].copy(targetReps = 8, targetRir = 1),
            pending[2].copy(targetReps = 5, targetRir = 0))
        val decision = calibrate(reference = null, sets = sets)
        assertEquals(CalibrationDecisionType.FIRST_REFERENCE, decision.type)
        assertNull(decision.referenceKg)
        assertEquals(CalibrationReferenceSource.NONE, decision.referenceSource)
        assertNull(decision.ratio)
        assertNull(decision.improvementPercent)
        assertEquals(1.0, decision.factor, 0.0)
        // Inverse(C=94.3, reps+RIR=14/9/5) = 64.3 / 73.3 / 83.8 before rounding.
        assertEquals(listOf(65.0, 72.5, 85.0), decision.affectedSets.map { it.afterKg })
    }

    @Test fun firstReferenceSkipsTargetsWhoseRepetitionsOrReserveAreUnknownOrInvalid() {
        val sets = listOf(pending[0].copy(targetReps = null), pending[1].copy(targetReps = 0),
            pending[2].copy(targetRir = -1))
        val decision = calibrate(reference = null, sets = sets)
        assertEquals(CalibrationDecisionType.FIRST_REFERENCE, decision.type)
        assertEquals(94.3, decision.capacityKg!!, 0.0)
        assertTrue(decision.affectedSets.isEmpty())
    }

    @Test fun roundingCanTurnARiseADropOrAFirstReferenceIntoHold() {
        val sets = listOf(pending[0].copy(baseLoadKg = 10.0, currentLoadKg = 10.0))
        for (reference in listOf(88.0, 110.0)) {
            val decision = calibrate(reference = reference, sets = sets)
            assertEquals(CalibrationDecisionType.HOLD, decision.type)
            assertEquals(listOf(CalibrationLoadChange("S2", 10.0, 10.0)), decision.affectedSets)
            assertTrue(decision.reason.contains("redondeo"))
        }
        val first = calibrate(reference = null, sets = listOf(pending[0].copy(baseLoadKg = 65.0, currentLoadKg = 65.0)))
        assertEquals(CalibrationDecisionType.HOLD, first.type)
        assertNull(first.improvementPercent)
    }

    @Test fun zeroOneAndTwoFullRepsUseAFixedDropWithoutEstimatingCapacity() {
        for (reps in 0..2) {
            val decision = calibrate(recorded = source.copy(fullReps = reps))
            assertEquals(CalibrationEligibility.OK, decision.eligibility)
            assertNull(decision.capacityKg)
            assertNull(decision.effectiveRepsToFailure)
            assertNull(decision.ratio)
            assertNull(decision.improvementPercent)
            assertEquals(0.95, decision.factor, 0.0)
            assertEquals(CalibrationDecisionType.LOW_REPS_DROP, decision.type)
            assertTrue(decision.affectedSets.all { it.beforeKg == 60.0 && it.afterKg == 57.5 })
        }
        assertEquals(CalibrationDecisionType.LOW_REPS_DROP, calibrate(reference = null, recorded = source.copy(fullReps = 2)).type)
    }

    @Test fun lowRepsAlsoHoldWhenTheMaterialRoundsAwayTheDrop() {
        val decision = calibrate(recorded = source.copy(fullReps = 1),
            sets = listOf(pending[0].copy(baseLoadKg = 10.0, currentLoadKg = 10.0)))
        assertEquals(CalibrationDecisionType.HOLD, decision.type)
        assertEquals(10.0, decision.affectedSets.single().afterKg, 0.0)
    }

    @Test fun moreThanTenFullRepsAreCappedBeforeAddingReserveAndAddANote() {
        for (reps in listOf(11, 15, Int.MAX_VALUE)) {
            for (rir in 0..1) {
                val decision = calibrate(recorded = source.copy(fullReps = reps, declaredRir = rir))
                assertEquals(10 + rir, decision.effectiveRepsToFailure)
                assertEquals(if (rir == 0) 92.0 else 94.3, decision.capacityKg!!, 0.0)
                assertTrue(decision.note!!.contains("usa más carga la próxima vez"))
            }
        }
    }

    @Test fun partialsAreNotSubtractedAgainFromCal1FullRepsAndNeverAddCapacity() {
        val decision = calibrate(recorded = source.copy(fullReps = 3, partialReps = 7, declaredRir = 0))
        assertEquals(3, decision.effectiveRepsToFailure)
        assertEquals(73.1, decision.capacityKg!!, 0.0)
        assertNull(decision.note)
    }

    @Test fun failureCountsAsZeroReserveWithoutRequiringARirValue() {
        val decision = calibrate(recorded = source.copy(reachedFailure = true, declaredRir = null))
        assertEquals(CalibrationEligibility.OK, decision.eligibility)
        assertEquals(10, decision.effectiveRepsToFailure)
        assertEquals(92.0, decision.capacityKg!!, 0.0)
        assertEquals(1.0454545454545454, decision.factor, 1e-12)
    }

    @Test fun lastreUsesOnlyTheDeclaredAddedLoad() {
        val lastre = context.copy(loadMode = LoadModeV2.LASTRE, loadQuantityConvention = LoadQuantityConvention.ADDITIONAL_BODYWEIGHT)
        val decision = calibrate(recorded = source.copy(context = lastre, externalLoadKg = 20.0, fullReps = 5, declaredRir = 0),
            reference = null, sets = listOf(pending[0].copy(context = lastre)))
        assertEquals(CalibrationEligibility.OK, decision.eligibility)
        assertEquals(22.5, decision.capacityKg!!, 0.0)
        assertEquals(15.0, decision.affectedSets.single().afterKg, 0.0)
    }

    @Test fun completedManualAndSourceSetsAreProtectedAndOtherExercisesAndContextsAreExcluded() {
        val foreignContexts = listOf(context.copy(configurationId = "other-config"), context.copy(equipmentKey = "machine"),
            context.copy(side = "left"), context.copy(setupProfileId = "seat-2"),
            context.copy(loadQuantityConvention = LoadQuantityConvention.PER_IMPLEMENT), context.copy(loadMode = LoadModeV2.LASTRE))
        val protected = listOf(pending[0].copy(isCompleted = true), pending[1].copy(isManuallyFixed = true),
            pending[0].copy(setId = "S1"), pending[2].copy(exerciseId = "row")) +
            foreignContexts.mapIndexed { i, ctx -> pending[2].copy(setId = "foreign-$i", context = ctx) }
        val sets = protected + pending[2]
        val decision = calibrate(sets = sets)
        assertEquals(listOf(CalibrationLoadChange("S4", 60.0, 62.5)), decision.affectedSets)
        assertEquals(protected, decision.applyTo(sets).dropLast(1))
    }

    @Test fun applyingAndRecomputingAlwaysUseTheSavedBaseAndAreIdempotent() {
        val decision = calibrate()
        val once = decision.applyTo(pending)
        assertTrue(once.all { it.baseLoadKg == 60.0 && it.currentLoadKg == 62.5 })
        assertEquals(once, decision.applyTo(once))
        assertEquals(decision, calibrate(sets = once))
        assertEquals(once, calibrate(sets = once).applyTo(once))
        assertEquals(once, calibrate(recorded = source.copy(externalLoadKg = 70.0), sets = once).applyTo(once))
    }

    @Test fun aProposalCannotOverwriteASetCompletedFixedOrRebasedAfterItWasCalculated() {
        val changed = listOf(pending[0].copy(isCompleted = true), pending[1].copy(isManuallyFixed = true),
            pending[2].copy(baseLoadKg = 70.0, currentLoadKg = 70.0))
        assertEquals(changed, calibrate().applyTo(changed))
        val foreignCollision = listOf(pending[0].copy(exerciseId = "row"), pending[1].copy(context = context.copy(side = "right")))
        assertEquals(foreignCollision, calibrate().applyTo(foreignCollision))
    }

    @Test fun futureScopeOnlyProposesCapacityAndNeverTouchesTodaysSets() {
        for (reference in listOf(88.0, null)) {
            val decision = calibrate(reference = reference, scope = CalibrationScope.NEXT_SESSIONS)
            assertEquals(94.3, decision.capacityKg!!, 0.0)
            assertEquals(if (reference == null) CalibrationDecisionType.FIRST_REFERENCE else CalibrationDecisionType.RAISE, decision.type)
            assertTrue(decision.affectedSets.isEmpty())
            assertEquals(pending, decision.applyTo(pending))
            assertTrue(decision.reason.contains("hoy se conservan"))
        }
        val lowReps = calibrate(recorded = source.copy(fullReps = 2), scope = CalibrationScope.NEXT_SESSIONS)
        assertEquals(CalibrationDecisionType.NOT_APPLIED, lowReps.type)
        assertNull(lowReps.capacityKg)
        assertEquals(pending, lowReps.applyTo(pending))
    }

    @Test fun equipmentIncrementIsUsedAndTheAppliedPolicyIsRetained() {
        val machine = calibrate(equipment = CalibrationEquipment(1.0))
        assertEquals(63.0, machine.affectedSets.first().afterKg, 0.0)
        val policy = CalibrationPolicy.defaults.copy(version = 2, maximumFactor = 1.03, defaultLoadIncrementKg = 1.0)
        val decision = CalibrationEngine.calibrate(source, pending, references(88.0), now, policy = policy)
        assertEquals(policy, decision.policy)
        assertEquals(1.03, decision.factor, 0.0)
        assertEquals(62.0, decision.affectedSets.first().afterKg, 0.0)
    }

    @Test fun noCompatiblePendingSetsMeansHoldWithNoCrossExerciseFallback() {
        val decision = calibrate(sets = pending.map { it.copy(exerciseId = "row") })
        assertEquals(CalibrationDecisionType.HOLD, decision.type)
        assertEquals(94.3, decision.capacityKg!!, 0.0)
        assertTrue(decision.affectedSets.isEmpty())
    }

    private fun references(kg: Double?, ctx: CalibrationContext = context) = CalibrationReferenceCandidates(
        declaredOneRm = kg?.let { DeclaredCalibrationOneRm(ctx, it) },
    )

    private fun calibrate(
        recorded: RecordedCalibrationSet = source,
        reference: Double? = 88.0,
        sets: List<PendingCalibrationSet> = pending,
        scope: CalibrationScope = CalibrationScope.SESSION,
        equipment: CalibrationEquipment = CalibrationEquipment(),
    ) = CalibrationEngine.calibrate(recorded, sets, references(reference, recorded.context), now, scope, equipment = equipment)
}
