package com.example.kpkn.data.work

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** El límite del avance que va a Room: como mucho 1 vez por segundo y solo si sube 5 puntos. JVM puro, con reloj falso. */
class ProgressThrottleTest {
    private var now = 0L
    private fun throttle() = ProgressThrottle(minIntervalMs = 1_000L, minStepPct = 5) { now }

    @Test
    fun the_first_write_needs_a_five_point_step_but_no_wait() {
        val throttle = throttle()

        assertNull(throttle.accept(0))
        assertNull(throttle.accept(4))
        assertEquals(5, throttle.accept(5))
    }

    @Test
    fun a_later_write_needs_both_the_step_and_the_second() {
        val throttle = throttle()
        assertEquals(10, throttle.accept(10))

        now = 500L
        assertNull("sube 10 pero ha pasado medio segundo", throttle.accept(20))
        now = 999L
        assertNull(throttle.accept(20))
        now = 1_000L
        assertNull("ha pasado el segundo pero sube 2", throttle.accept(12))
        assertEquals(20, throttle.accept(20))
        now = 5_000L
        assertNull("sube 4", throttle.accept(24))
        assertEquals(25, throttle.accept(25))
    }

    @Test
    fun it_never_goes_backwards() {
        val throttle = throttle()
        now = 0L
        assertEquals(40, throttle.accept(40))

        now = 10_000L
        assertNull(throttle.accept(40))
        assertNull(throttle.accept(10))
        assertNull(throttle.accept(-20))
        assertEquals(45, throttle.accept(45))
    }

    @Test
    fun it_never_reaches_a_hundred_while_the_analysis_runs() {
        val throttle = throttle()
        assertEquals(90, throttle.accept(90))

        now = 2_000L
        assertEquals("el 100 es el estado DONE", 99, throttle.accept(100))
        now = 9_000L
        assertNull(throttle.accept(100))
        assertNull(throttle.accept(250))
    }

    @Test
    fun a_burst_of_calls_writes_once() {
        val throttle = throttle()
        val written = (0..98).mapNotNull { throttle.accept(it) }

        assertEquals(listOf(5), written)
    }

    @Test
    fun the_defaults_are_one_second_and_five_points() {
        assertEquals(1_000L, ProgressThrottle.MIN_INTERVAL_MS)
        assertEquals(5, ProgressThrottle.MIN_STEP_PCT)
        assertEquals(99, ProgressThrottle.MAX_RUNNING_PCT)
    }
}
