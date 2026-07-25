package com.example.kpkn.screens.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ParseCompletedSetKeyTest {
    @Test
    fun parsesBilateralAndUnilateralKeys() {
        assertEquals(
            ParsedCompletedSetKey("ex_press", 2, null),
            parseCompletedSetKey("ex_press_2"),
        )
        assertEquals(
            ParsedCompletedSetKey("ex_press", 2, "left"),
            parseCompletedSetKey("ex_press_2_L"),
        )
        assertEquals(
            ParsedCompletedSetKey("ex_press", 2, "right"),
            parseCompletedSetKey("ex_press_2_R"),
        )
    }

    @Test
    fun keepsUnderscoresInsideExerciseId() {
        assertEquals(
            ParsedCompletedSetKey("ex_3", 1, "left"),
            parseCompletedSetKey("ex_3_1_L"),
        )
        assertNull(parseCompletedSetKey("broken"))
    }
}
