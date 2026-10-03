package com.example.kpkn.screens.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutRecordFabPlacementTest {
    @Test
    fun withoutKeyboard_keepsTheUsualAnchorOverNavigationBarAndDock() {
        val bottom = resolveRecordFabBottomOffsetDp(
            imeBottomDp = 0f,
            navigationBarBottomDp = 24f,
            dockBottomClearanceDp = 140f,
        )

        assertEquals(24f + 140f + 12f, bottom, 0.001f)
    }

    @Test
    fun withKeyboard_sitsTwelveDpAboveTheKeyboardWithoutAddingNavigationBarOrDock() {
        val bottom = resolveRecordFabBottomOffsetDp(
            imeBottomDp = 310f,
            navigationBarBottomDp = 24f,
            dockBottomClearanceDp = 140f,
        )

        assertEquals(310f + 12f, bottom, 0.001f)
    }

    @Test
    fun withKeyboard_isAlwaysAboveTheKeyboardTop() {
        listOf(180f, 260f, 310f, 420f).forEach { ime ->
            val bottom = resolveRecordFabBottomOffsetDp(
                imeBottomDp = ime,
                navigationBarBottomDp = 48f,
                dockBottomClearanceDp = 200f,
            )
            assertTrue("el botón debe quedar sobre el teclado ($ime dp)", bottom >= ime + RECORD_FAB_IME_GAP_DP)
        }
    }

    @Test
    fun withKeyboard_followsTheKeyboardHeightWhileItAnimates() {
        val half = resolveRecordFabBottomOffsetDp(150f, 24f, 140f)
        val full = resolveRecordFabBottomOffsetDp(300f, 24f, 140f)

        assertEquals(150f, full - half, 0.001f)
    }

    @Test
    fun negativeOrZeroKeyboardInsetCountsAsHiddenKeyboard() {
        val hidden = resolveRecordFabBottomOffsetDp(0f, 0f, 140f)
        val negative = resolveRecordFabBottomOffsetDp(-5f, 0f, 140f)

        assertEquals(152f, hidden, 0.001f)
        assertEquals(hidden, negative, 0.001f)
    }
}
