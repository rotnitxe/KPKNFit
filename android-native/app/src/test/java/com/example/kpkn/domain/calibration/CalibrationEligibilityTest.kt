package com.example.kpkn.domain.calibration

import com.example.kpkn.data.models.LoadModeV2
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class CalibrationEligibilityTest(
    @Suppress("unused") private val label: String,
    private val source: RecordedCalibrationSet,
    private val expected: CalibrationEligibility,
) {
    @Test fun rejectsWithAnExplainableReasonAndNoLoadChanges() {
        assertEquals(expected, CalibrationEngine.eligibility(source))
        val sets = listOf(PendingCalibrationSet("S2", "bench", source.context, 60.0, targetReps = 12))
        val decision = CalibrationEngine.calibrate(source, sets, CalibrationReferenceCandidates(), 0L)
        assertEquals(expected, decision.eligibility)
        assertEquals(CalibrationDecisionType.NOT_APPLIED, decision.type)
        assertNull(decision.capacityKg)
        assertNull(decision.ratio)
        assertEquals(1.0, decision.factor, 0.0)
        assertTrue(decision.affectedSets.isEmpty())
        assertTrue(decision.reason.isNotBlank())
        assertEquals(sets, decision.applyTo(sets))
    }

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}") fun cases(): List<Array<Any>> {
            val ctx = CalibrationContext("bench-config", "barbell")
            val valid = RecordedCalibrationSet("S1", "bench", ctx, 69.0, 10, declaredRir = 1)
            return listOf(
                arrayOf("NO_EFFORT", valid.copy(declaredRir = null), CalibrationEligibility.NO_EFFORT),
                arrayOf("RIR_2", valid.copy(declaredRir = 2), CalibrationEligibility.RIR_TOO_HIGH),
                arrayOf("RIR_3", valid.copy(declaredRir = 3), CalibrationEligibility.RIR_TOO_HIGH),
                arrayOf("BODYWEIGHT", valid.copy(context = ctx.copy(loadMode = LoadModeV2.BODYWEIGHT)), CalibrationEligibility.BODYWEIGHT),
                arrayOf("ASSISTED", valid.copy(context = ctx.copy(loadMode = LoadModeV2.ASSISTED)), CalibrationEligibility.NO_EXTERNAL_LOAD),
                arrayOf("ZERO_LOAD", valid.copy(externalLoadKg = 0.0), CalibrationEligibility.NO_EXTERNAL_LOAD),
                arrayOf("NEGATIVE_LOAD", valid.copy(externalLoadKg = -1.0), CalibrationEligibility.NO_EXTERNAL_LOAD),
                arrayOf("NAN_LOAD", valid.copy(externalLoadKg = Double.NaN), CalibrationEligibility.NO_EXTERNAL_LOAD),
                arrayOf("INFINITE_LOAD", valid.copy(externalLoadKg = Double.POSITIVE_INFINITY), CalibrationEligibility.NO_EXTERNAL_LOAD),
                arrayOf("LASTRE_ZERO", valid.copy(context = ctx.copy(loadMode = LoadModeV2.LASTRE), externalLoadKg = 0.0), CalibrationEligibility.NO_EXTERNAL_LOAD),
                arrayOf("PARTIALS_ONLY", valid.copy(fullReps = 0, partialReps = 5), CalibrationEligibility.PARTIALS_ONLY),
                arrayOf("NEGATIVE_REPS", valid.copy(fullReps = -1), CalibrationEligibility.REPS_OUT_OF_RANGE),
                arrayOf("NEGATIVE_PARTIALS", valid.copy(partialReps = -1), CalibrationEligibility.REPS_OUT_OF_RANGE),
                arrayOf("NEGATIVE_RIR", valid.copy(declaredRir = -1), CalibrationEligibility.REPS_OUT_OF_RANGE),
            )
        }
    }
}
