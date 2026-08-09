package com.example.kpkn.services.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportGestureStateMachineTest {
    @Test
    fun `accepts deliberately slow second finger within window`() {
        val machine = ReportGestureStateMachine()
        machine.onInput(ReportGestureInput.Down(0, ReportGesturePointer(1, 0f, 0f)))

        val effects = machine.onInput(
            ReportGestureInput.PointerDown(1_400, ReportGesturePointer(2, 20f, 0f)),
        )

        assertTrue(machine.isArming)
        assertEquals(listOf(ReportGestureEffect.Progress(0f)), effects)
    }

    @Test
    fun `confirms after hold and only releases after final up`() {
        val machine = armedMachine()

        val before = machine.onInput(ReportGestureInput.Tick(2_499))
        assertTrue(before.single() is ReportGestureEffect.Progress)
        assertTrue(machine.isArming)

        val confirmed = machine.onInput(ReportGestureInput.Tick(2_510))
        assertTrue(confirmed.contains(ReportGestureEffect.CancelUnderlying))
        assertTrue(confirmed.contains(ReportGestureEffect.Confirmed))
        assertTrue(machine.isConfirmed)

        val released = machine.onInput(ReportGestureInput.Up(2_511))
        assertEquals(listOf(ReportGestureEffect.Released, ReportGestureEffect.Reset), released)
    }

    @Test
    fun `cancels on movement beyond four times touch slop`() {
        val machine = ReportGestureStateMachine(movementSlopPx = 32f)
        machine.onInput(ReportGestureInput.Down(0, ReportGesturePointer(1, 0f, 0f)))
        machine.onInput(ReportGestureInput.PointerDown(10, ReportGesturePointer(2, 20f, 0f)))

        val effects = machine.onInput(
            ReportGestureInput.Move(
                100,
                listOf(
                    ReportGesturePointer(1, 33f, 0f),
                    ReportGesturePointer(2, 20f, 0f),
                ),
            ),
        )

        assertTrue(effects.contains(ReportGestureEffect.Reset))
        assertTrue(!machine.isArming)
    }

    @Test
    fun `rejects second finger after placement window`() {
        val machine = ReportGestureStateMachine()
        machine.onInput(ReportGestureInput.Down(0, ReportGesturePointer(1, 0f, 0f)))

        machine.onInput(ReportGestureInput.PointerDown(1_501, ReportGesturePointer(2, 20f, 0f)))

        assertTrue(!machine.isArming)
    }

    @Test
    fun `pointer lift before confirmation cancels candidate`() {
        val machine = armedMachine()

        val effects = machine.onInput(ReportGestureInput.PointerUp(1_000, 2))

        assertTrue(effects.contains(ReportGestureEffect.Reset))
        assertTrue(!machine.isArming)
    }

    private fun armedMachine(): ReportGestureStateMachine {
        val machine = ReportGestureStateMachine()
        machine.onInput(ReportGestureInput.Down(0, ReportGesturePointer(1, 0f, 0f)))
        machine.onInput(ReportGestureInput.PointerDown(10, ReportGesturePointer(2, 20f, 0f)))
        return machine
    }
}
