package com.example.kpkn.data.protocols

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtocolExerciseLibraryTest {

    @Test
    fun focusForDayLabel_matches_common_split_day_labels() {
        assertEquals(ProtocolLiftFocus.SQUAT, ProtocolExerciseLibrary.focusForDayLabel("Pierna"))
        // "Sentadilla" tiene prioridad en la heurística: un día mixto se resuelve a SQUAT.
        assertEquals(ProtocolLiftFocus.SQUAT, ProtocolExerciseLibrary.focusForDayLabel("Sentadilla/Banca"))
        assertEquals(ProtocolLiftFocus.DEADLIFT, ProtocolExerciseLibrary.focusForDayLabel("Peso Muerto"))
        assertEquals(ProtocolLiftFocus.BENCH, ProtocolExerciseLibrary.focusForDayLabel("Torso"))
        assertEquals(ProtocolLiftFocus.PULL, ProtocolExerciseLibrary.focusForDayLabel("Tirón"))
        assertEquals(ProtocolLiftFocus.OVERHEAD_PRESS, ProtocolExerciseLibrary.focusForDayLabel("Press Militar/Hombro"))
    }

    @Test
    fun mainLiftFor_returns_real_exerciseDbId_for_every_focus() {
        ProtocolLiftFocus.entries.forEach { focus ->
            val lift = ProtocolExerciseLibrary.mainLiftFor(focus, sessionIndex = 0)
            assertTrue("Focus $focus debe resolver a un exerciseDbId no vacío", lift.exerciseDbId.isNotBlank())
        }
    }

    @Test
    fun accessoriesFor_rotates_slightly_by_week_but_stays_within_pool() {
        val week1 = ProtocolExerciseLibrary.accessoriesFor(ProtocolExerciseLibrary.SQUAT_MAIN, weekNumber = 1, count = 2)
        val week2 = ProtocolExerciseLibrary.accessoriesFor(ProtocolExerciseLibrary.SQUAT_MAIN, weekNumber = 2, count = 2)
        assertEquals(2, week1.size)
        assertEquals(2, week2.size)
        assertTrue(week1.all { it.exerciseDbId.isNotBlank() })
        assertNotEquals(week1, week2)
    }

    @Test
    fun techniqueVariantFor_differs_from_main_lift_for_squat_bench_deadlift() {
        assertNotEquals(ProtocolExerciseLibrary.SQUAT_MAIN, ProtocolExerciseLibrary.techniqueVariantFor(ProtocolExerciseLibrary.SQUAT_MAIN))
        assertNotEquals(ProtocolExerciseLibrary.BENCH_MAIN, ProtocolExerciseLibrary.techniqueVariantFor(ProtocolExerciseLibrary.BENCH_MAIN))
        assertNotEquals(ProtocolExerciseLibrary.DEADLIFT_MAIN, ProtocolExerciseLibrary.techniqueVariantFor(ProtocolExerciseLibrary.DEADLIFT_MAIN))
    }
}
