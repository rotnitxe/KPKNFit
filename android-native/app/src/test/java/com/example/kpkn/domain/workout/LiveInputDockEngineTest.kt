package com.example.kpkn.domain.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class LiveInputDockEngineTest {

    @Test
    fun presetsAndCustomStepSanitizationWorkAccurately() {
        assertEquals(listOf(0.5, 1.0, 1.25, 2.0, 2.5, 5.0), LiveInputDockEngine.PRESET_LOAD_STEPS_KG)
        assertEquals(1.5, LiveInputDockEngine.sanitizeCustomStepKg("1,5")!!, 1e-4)
        assertEquals(2.25, LiveInputDockEngine.sanitizeCustomStepKg(" 2.25 ")!!, 1e-4)
        assertEquals(0.1, LiveInputDockEngine.sanitizeCustomStepKg("0,02")!!, 1e-4)
        assertEquals(25.0, LiveInputDockEngine.sanitizeCustomStepKg("30")!!, 1e-4)
        assertNull(LiveInputDockEngine.sanitizeCustomStepKg("abc"))
        assertNull(LiveInputDockEngine.sanitizeCustomStepKg("   "))
        assertEquals(3.75, LiveInputDockEngine.clampStepKg(3.75), 1e-4)
    }

    @Test
    fun snapValueToStepSnapsAccuratelyWithoutFloatingNoise() {
        // Dumbbells with step 2.0 kg
        assertEquals(22.0, LiveInputDockEngine.snapValueToStep(21.3, 2.0), 1e-4)
        assertEquals(24.0, LiveInputDockEngine.snapValueToStep(23.9, 2.0), 1e-4)

        // Barbell with step 2.5 kg
        assertEquals(80.0, LiveInputDockEngine.snapValueToStep(81.2, 2.5), 1e-4)
        assertEquals(82.5, LiveInputDockEngine.snapValueToStep(81.3, 2.5), 1e-4)

        // Micro-loads with step 0.5 kg
        assertEquals(60.0, LiveInputDockEngine.snapValueToStep(60.2, 0.5), 1e-4)
        assertEquals(60.5, LiveInputDockEngine.snapValueToStep(60.4, 0.5), 1e-4)

        // Boundaries
        assertEquals(0.0, LiveInputDockEngine.snapValueToStep(-5.0, 2.5, min = 0.0, max = 500.0), 1e-4)
        assertEquals(500.0, LiveInputDockEngine.snapValueToStep(520.0, 2.5, min = 0.0, max = 500.0), 1e-4)
    }

    @Test
    fun quickJumpDeltasCalculatesExpectedMultiples() {
        val deltas2_5 = LiveInputDockEngine.quickJumpDeltas(LiveInputFieldKind.WEIGHT, 2.5)
        assertEquals(listOf(-5.0, -2.5, 2.5, 5.0), deltas2_5)

        val deltas2_0 = LiveInputDockEngine.quickJumpDeltas(LiveInputFieldKind.WEIGHT, 2.0)
        assertEquals(listOf(-4.0, -2.0, 2.0, 4.0), deltas2_0)

        val deltas0_5 = LiveInputDockEngine.quickJumpDeltas(LiveInputFieldKind.WEIGHT, 0.5)
        assertEquals(listOf(-1.0, -0.5, 0.5, 1.0), deltas0_5)

        val timeDeltas = LiveInputDockEngine.quickJumpDeltas(LiveInputFieldKind.TIME_MM_SS, 1.0)
        assertEquals(listOf(-15.0, -5.0, 5.0, 15.0), timeDeltas)

        val repsDeltas = LiveInputDockEngine.quickJumpDeltas(LiveInputFieldKind.REPS, 1.0)
        assertEquals(listOf(-5.0, -1.0, 1.0, 5.0), repsDeltas)
    }

    @Test
    fun formatQuickDeltaPillRendersCorrectLabels() {
        assertEquals("+2,5", LiveInputDockEngine.formatQuickDeltaPill(2.5, LiveInputFieldKind.WEIGHT))
        assertEquals("−2,5", LiveInputDockEngine.formatQuickDeltaPill(-2.5, LiveInputFieldKind.WEIGHT))
        assertEquals("+5", LiveInputDockEngine.formatQuickDeltaPill(5.0, LiveInputFieldKind.WEIGHT))
        assertEquals("−5", LiveInputDockEngine.formatQuickDeltaPill(-5.0, LiveInputFieldKind.WEIGHT))
        assertEquals("+15s", LiveInputDockEngine.formatQuickDeltaPill(15.0, LiveInputFieldKind.TIME_MM_SS))
        assertEquals("−5s", LiveInputDockEngine.formatQuickDeltaPill(-5.0, LiveInputFieldKind.TIME_MM_SS))
        assertEquals("+1", LiveInputDockEngine.formatQuickDeltaPill(1.0, LiveInputFieldKind.REPS))
        assertEquals("−5", LiveInputDockEngine.formatQuickDeltaPill(-5.0, LiveInputFieldKind.REPS))
    }

    @Test
    fun keypadDraftReplacesOnFirstDigitWhenPendingReplace() {
        val draft = LiveKeypadDraft(text = "80", pendingReplace = true)

        // Typing '7' replaces '80'
        val after7 = draft.onDigit('7')
        assertEquals("7", after7.text)
        assertFalse(after7.pendingReplace)

        // Typing '5' appends to '7'
        val after5 = after7.onDigit('5')
        assertEquals("75", after5.text)

        // Typing comma
        val afterComma = after5.onSeparator(allowDecimal = true, allowColon = false)
        assertEquals("75,", afterComma.text)

        // Typing decimals
        val afterDec1 = afterComma.onDigit('2')
        assertEquals("75,2", afterDec1.text)
        val afterDec2 = afterDec1.onDigit('5')
        assertEquals("75,25", afterDec2.text)

        // Max 2 decimals enforced
        val afterDec3 = afterDec2.onDigit('8')
        assertEquals("75,25", afterDec3.text)
    }

    @Test
    fun keypadDraftHandlesSeparatorAndBackspaceOnPendingReplace() {
        val draft = LiveKeypadDraft(text = "80", pendingReplace = true)
        val commaDraft = draft.onSeparator(allowDecimal = true, allowColon = false)
        assertEquals("0,", commaDraft.text)
        assertFalse(commaDraft.pendingReplace)

        val timeDraft = LiveKeypadDraft(text = "1:00", pendingReplace = true)
            .onSeparator(allowDecimal = false, allowColon = true)
        assertEquals("0:", timeDraft.text)

        val bkDraft = LiveKeypadDraft(text = "80", pendingReplace = true).onBackspace()
        assertEquals("", bkDraft.text)
        assertFalse(bkDraft.pendingReplace)

        val clearDraft = LiveKeypadDraft(text = "10,5", pendingReplace = false).onLongPressClear()
        assertEquals("", clearDraft.text)
    }

    @Test
    fun formatAndParseDockNumberSupportsColonsAndCommas() {
        assertEquals("80", LiveInputDockEngine.formatDockNumber(80.0, allowDecimal = true, allowColon = false))
        assertEquals("82,5", LiveInputDockEngine.formatDockNumber(82.5, allowDecimal = true, allowColon = false))
        assertEquals("81,25", LiveInputDockEngine.formatDockNumber(81.25, allowDecimal = true, allowColon = false))
        assertEquals("1:15", LiveInputDockEngine.formatDockNumber(75.0, allowDecimal = false, allowColon = true))
        assertEquals("0:45", LiveInputDockEngine.formatDockNumber(45.0, allowDecimal = false, allowColon = true))

        assertEquals(90.0, LiveInputDockEngine.parseDockNumber("1:30", allowColon = true)!!, 1e-4)
        assertEquals(45.0, LiveInputDockEngine.parseDockNumber("0:45", allowColon = true)!!, 1e-4)
        assertEquals(82.5, LiveInputDockEngine.parseDockNumber("82,5", allowColon = false)!!, 1e-4)
        assertEquals(82.5, LiveInputDockEngine.parseDockNumber("82.5", allowColon = false)!!, 1e-4)
        assertNull(LiveInputDockEngine.parseDockNumber("—", allowColon = false))
    }
}
