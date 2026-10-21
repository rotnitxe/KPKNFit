package com.example.kpkn.domain.calibration

import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.LoadQuantityConvention
import org.junit.Assert.*
import org.junit.Test

class ReferenceResolverTest {
    private val day = 24L * 60 * 60 * 1000
    private val now = 100 * day
    private val context = CalibrationContext("bench-config", "barbell")
    private val accepted = AcceptedCalibrationReference(context, 100.0, now - day)
    private val history = HistoricalCalibrationReference(context, 90.0)
    private val oneRm = DeclaredCalibrationOneRm(context, 88.0)
    private val pr = DeclaredCalibrationPr(context, 60.0, 10)
    private val all = CalibrationReferenceCandidates(listOf(accepted), history, oneRm, pr)

    @Test fun resolvesAllFiveSourcesInTheirRequiredPriority() {
        assertReference(all, 100.0, CalibrationReferenceSource.ACCEPTED_CALIBRATION)
        assertReference(all.copy(acceptedCalibrations = emptyList()), 90.0, CalibrationReferenceSource.HISTORY)
        assertReference(all.copy(acceptedCalibrations = emptyList(), history = null), 88.0, CalibrationReferenceSource.DECLARED_1RM)
        assertReference(all.copy(acceptedCalibrations = emptyList(), history = null, declaredOneRm = null), 80.0, CalibrationReferenceSource.DECLARED_PR)
        assertEquals(ResolvedCalibrationReference(null, CalibrationReferenceSource.NONE), resolve(CalibrationReferenceCandidates()))
    }

    @Test fun choosesTheLatestAcceptedValueRatherThanTheHighestCapacityOrInputOrder() {
        val candidates = all.copy(acceptedCalibrations = listOf(
            accepted.copy(capacityKg = 105.0, acceptedAtEpochMillis = now - 10 * day),
            accepted, accepted.copy(capacityKg = 95.0, acceptedAtEpochMillis = now),
        ))
        assertReference(candidates, 95.0, CalibrationReferenceSource.ACCEPTED_CALIBRATION)
        assertReference(candidates.copy(acceptedCalibrations = candidates.acceptedCalibrations.reversed()), 95.0, CalibrationReferenceSource.ACCEPTED_CALIBRATION)
    }

    @Test fun validityIsStrictlyLessThanFortyTwoDaysAndNeverAcceptsAFutureTimestamp() {
        assertReference(all.copy(acceptedCalibrations = listOf(accepted.copy(acceptedAtEpochMillis = now - 42 * day + 1))),
            100.0, CalibrationReferenceSource.ACCEPTED_CALIBRATION)
        for (timestamp in listOf(now - 42 * day, now - 43 * day, now + 1, -1L)) {
            assertReference(all.copy(acceptedCalibrations = listOf(accepted.copy(acceptedAtEpochMillis = timestamp))),
                90.0, CalibrationReferenceSource.HISTORY)
        }
    }

    @Test fun anOldEstimatorSnapshotIsSkippedInsteadOfComparedAgainstCurrentCapacity() {
        assertReference(all.copy(acceptedCalibrations = listOf(accepted.copy(estimatorVersion = 1))),
            90.0, CalibrationReferenceSource.HISTORY)
        assertReference(all.copy(acceptedCalibrations = emptyList(), history = history.copy(estimatorVersion = 1)),
            88.0, CalibrationReferenceSource.DECLARED_1RM)
        assertReference(all.copy(acceptedCalibrations = listOf(accepted.copy(estimatorVersion = 3))),
            90.0, CalibrationReferenceSource.HISTORY)
    }

    @Test fun everyReferenceSourceRequiresTheExactConfigurationEquipmentSideSetupModeAndConvention() {
        val foreignContexts = listOf(context.copy(configurationId = "row-config"), context.copy(equipmentKey = "machine"),
            context.copy(side = "left"), context.copy(side = "right"), context.copy(setupProfileId = "seat-2"),
            context.copy(loadMode = LoadModeV2.LASTRE), context.copy(loadQuantityConvention = LoadQuantityConvention.PER_IMPLEMENT))
        for (foreign in foreignContexts) {
            val candidates = CalibrationReferenceCandidates(listOf(accepted.copy(context = foreign)),
                history.copy(context = foreign), oneRm.copy(context = foreign), pr.copy(context = foreign))
            assertEquals(ResolvedCalibrationReference(null, CalibrationReferenceSource.NONE), resolve(candidates))
            assertReference(all.copy(acceptedCalibrations = candidates.acceptedCalibrations), 90.0, CalibrationReferenceSource.HISTORY)
        }
    }

    @Test fun invalidCapacitiesFallBackWithoutManufacturingAReference() {
        for (value in listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertReference(all.copy(acceptedCalibrations = listOf(accepted.copy(capacityKg = value))), 90.0, CalibrationReferenceSource.HISTORY)
            assertReference(all.copy(acceptedCalibrations = emptyList(), history = history.copy(capacityKg = value)), 88.0, CalibrationReferenceSource.DECLARED_1RM)
            val candidates = all.copy(acceptedCalibrations = emptyList(), history = null, declaredOneRm = oneRm.copy(capacityKg = value))
            assertReference(candidates, 80.0, CalibrationReferenceSource.DECLARED_PR)
            assertEquals(CalibrationReferenceSource.NONE, resolve(candidates.copy(declaredPr = pr.copy(weightKg = value))).source)
        }
        assertEquals(CalibrationReferenceSource.NONE, resolve(CalibrationReferenceCandidates(declaredPr = pr.copy(reps = 0))).source)
    }

    @Test fun referenceLifetimeComesFromTheAppliedPolicy() {
        val policy = CalibrationPolicy.defaults.copy(referenceValidityDays = 1)
        assertEquals(CalibrationReferenceSource.HISTORY, ReferenceResolver.resolve(context, all, now, policy).source)
    }

    private fun resolve(candidates: CalibrationReferenceCandidates) = ReferenceResolver.resolve(context, candidates, now)
    private fun assertReference(candidates: CalibrationReferenceCandidates, kg: Double, origin: CalibrationReferenceSource) {
        val reference = resolve(candidates)
        assertEquals(kg, reference.capacityKg!!, 0.0)
        assertEquals(origin, reference.source)
    }
}
