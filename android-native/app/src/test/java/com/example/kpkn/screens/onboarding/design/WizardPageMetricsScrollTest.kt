package com.example.kpkn.screens.onboarding.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WizardPageMetricsScrollTest {
    @Test
    fun `penultima pagina sin asomo hacia REVIEW_ACTIVATE no deja espacio negro al hacer scroll`() {
        val index = 14
        val header = 160
        val chip = 88
        val viewport = 1900
        val clearance = 180
        val activeHeight = 900
        val limitWithoutPeek = WizardPageMetrics.lockMax(
            index = index,
            headerBottomPx = header,
            chipPx = chip,
            gapPx = 0,
            peekPx = 0,
            clearancePx = clearance,
            viewportPx = viewport,
            activeHeightPx = activeHeight,
        )
        val pinned = WizardPageMetrics.target(index, chip, 0)
        assertEquals(pinned, limitWithoutPeek)
        assertTrue(WizardPageMetrics.openExtra(header + chip, activeHeight, clearance, viewport, chip) == 0)
    }
}
