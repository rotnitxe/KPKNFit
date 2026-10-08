package com.example.kpkn.screens.onboarding.design

import org.junit.Assert.assertEquals
import org.junit.Test

/** Cuánto puede desplazar la página quien arrastra algo hasta su borde (`pageScrollStep`): con el mismo límite que la persona. */
class WizardPageScrollTest {

    @Test
    fun scrollingDownStopsAtTheLimit() {
        assertEquals(30f, pageScrollStep(value = 100, limit = 500, delta = 30f), 0f)
        assertEquals(20f, pageScrollStep(value = 480, limit = 500, delta = 30f), 0f)
        assertEquals(0f, pageScrollStep(value = 500, limit = 500, delta = 30f), 0f)
    }

    @Test
    fun scrollingUpStopsAtTheStart() {
        assertEquals(-30f, pageScrollStep(value = 100, limit = 500, delta = -30f), 0f)
        assertEquals(-10f, pageScrollStep(value = 10, limit = 500, delta = -30f), 0f)
        assertEquals(0f, pageScrollStep(value = 0, limit = 500, delta = -30f), 0f)
    }

    @Test
    fun aPageBeyondTheLimitNeverGoesFurtherDownButCanComeBack() {
        // El paso activo se encogió y la página quedó más allá del límite: hacia abajo no avanza; hacia arriba sí.
        assertEquals(0f, pageScrollStep(value = 600, limit = 500, delta = 30f), 0f)
        assertEquals(-30f, pageScrollStep(value = 600, limit = 500, delta = -30f), 0f)
    }

    @Test
    fun noMovementOrInvalidInputIsNoScroll() {
        assertEquals(0f, pageScrollStep(100, 500, 0f), 0f)
        assertEquals(0f, pageScrollStep(100, 500, Float.NaN), 0f)
        assertEquals(0f, pageScrollStep(100, 0, 30f), 0f)
    }
}
