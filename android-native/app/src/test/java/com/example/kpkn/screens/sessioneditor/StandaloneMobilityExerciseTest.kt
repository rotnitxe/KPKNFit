package com.example.kpkn.screens.sessioneditor

import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.MobilityExerciseCatalog
import com.example.kpkn.data.models.TimeProgressionStrategyV3
import com.example.kpkn.data.models.TrainingMode
import com.example.kpkn.data.models.UnitModeV2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StandaloneMobilityExerciseTest {
    @Test fun `normal mobility catalog add creates an independent timed exercise without load`() {
        val movement = checkNotNull(MobilityExerciseCatalog.findById("mob_shoulder_band_rotation"))
        val exercise = catalogExerciseForSession(
            info = ExerciseMuscleInfo(id = movement.id, name = movement.name, category = "Movilidad"),
            history = emptyList(),
            defaults = SessionEditorRuleDefaults(),
            isMeetDay = false,
        )
        val set = exercise.sets.single()
        assertEquals(TrainingMode.TIME, exercise.trainingMode)
        assertEquals(UnitModeV2.TIME, set.unitModeV2)
        assertEquals(movement.durationSeconds, set.targetDuration)
        assertEquals(TimeProgressionStrategyV3.TIME_ONLY, set.timeProgressionStrategyV3)
        assertNull(set.weight)
        assertNull(exercise.reference1RM)
        assertTrue(exercise.mobilitySeries.isEmpty())
    }
}
