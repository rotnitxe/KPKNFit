package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.*
import com.example.kpkn.domain.calibration.CalibrationDecisionType
import org.junit.Assert.*
import org.junit.Test

class ApreLateralCalibrationTest {
    @Test fun rightManualMarkerProtectsOnlyRightWhileLeftCanApplyMann() {
        val original = apreDurableExercise(true)
        val exercise = original.copy(sets = original.sets.mapIndexed { i,s ->
            if(i == 3) s.copy(manualLoadRequiredSides = setOf("right")) else s })
        val base = apreDurableState().copy(session = Session("lateral","APRE",exercises = listOf(exercise)))
        fun recorded(side: String) = CompletedSet("s3-$side",weight = 150.0,originalLoad = 150.0,reps = 12,
            purpose = SetPurpose.AMRAP,amrapMinimumReps = 10,setIndex = 2,side = side,loadMode = LoadModeV2.LOAD)
        assertFalse(manuallyFixedCalibrationSet(base,exercise,3,"left"))
        assertTrue(manuallyFixedCalibrationSet(base,exercise,3,"right"))
        assertTrue(manuallyFixedCalibrationSet(base,exercise,3,null))
        val left = recorded("left")
        val state = base.copy(completedSets = mapOf(workoutSetKey(exercise.id,2,"left") to left))
        val decision = buildRecordedApreCalibration(state,exercise,2,left,1_000)!!
        val applied = resolveCalibration(state.copy(calibrationDecisions = listOf(decision)),decision.id,CalibrationStatus.APPLIED,1_001)
        assertEquals(155.0,calibrationLoadForSet(applied,exercise,3,"left")!!.weight,0.0)
        assertNull(calibrationLoadForSet(applied,exercise,3,"right"))
        assertEquals(CalibrationDecisionType.NOT_APPLIED,buildRecordedApreCalibration(base,exercise,2,recorded("right"),1_002)!!.decision.type)
        val bilateral = exercise.copy(sets = exercise.sets.mapIndexed { i,s -> if(i == 3) s.copy(manualLoadRequiredSides = setOf("bilateral")) else s })
        assertTrue(manuallyFixedCalibrationSet(base,bilateral,3,"left"))
        assertTrue(manuallyFixedCalibrationSet(base,bilateral,3,"right"))
    }
    @Test fun s4NeedsRefreshWithoutAnyDecisionAndWithoutExistingSnapshot() {
        assertTrue(needsCorrectedCalibrationRefresh(apreDurableState()))
        assertFalse(needsCorrectedCalibrationRefresh(WorkoutUiState()))
    }
    @Test fun sourcePurposeWarmupAndExplicitMinimumCannotRelabelAnotherVariant() {
        val exercise = apreDurableExercise()
        val state = apreDurableState()
        val source = CompletedSet("source",weight = 150.0,originalLoad = 150.0,reps = 12,
            loadMode = LoadModeV2.LOAD,purpose = SetPurpose.AMRAP,amrapMinimumReps = 10)
        assertNotNull(buildRecordedApreCalibration(state,exercise,2,source,1_000))
        assertNull(buildRecordedApreCalibration(state,exercise,2,source.copy(isWarmup = true),1_000))
        assertNull(buildRecordedApreCalibration(state,exercise,2,source.copy(amrapMinimumReps = 6),1_000))
        assertNull(buildRecordedApreCalibration(state,exercise,2,source.copy(purpose = SetPurpose.NONE),1_000))
        assertNull(buildRecordedApreXrm(state,exercise,3,source.copy(amrapMinimumReps = 6),1_000))
        assertNull(buildRecordedApreXrm(state,exercise,3,source.copy(isWarmup = true),1_000))
        assertNotNull(buildRecordedApreXrm(state,exercise,3,source.copy(amrapMinimumReps = null),1_000))
    }
}
