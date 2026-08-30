package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.IntensityMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IntensityCarouselLogicTest {

    @Test
    fun rirCarousel_endsWithFailure_withoutRpe() {
        val items = buildIntensityCarouselItems(
            plannedIntensityMode = IntensityMode.RIR,
            reportedIntensityMode = IntensityMode.RIR,
            targetRir = 2,
            targetRpe = 8.0,
        )
        assertEquals("0", items.first().display)
        assertEquals("10", items[items.lastIndex - 1].display)
        assertTrue(items.last().isFailure)
        assertFalse(items.any { it.mode == IntensityMode.RPE })
    }

    @Test
    fun rpeCarousel_endsWithFailure_withoutRir() {
        val items = buildIntensityCarouselItems(
            plannedIntensityMode = IntensityMode.RPE,
            reportedIntensityMode = IntensityMode.RPE,
            targetRir = null,
            targetRpe = 8.0,
        )
        assertEquals("0", items.first().display)
        assertEquals("10", items[items.lastIndex - 1].display)
        assertTrue(items.last().isFailure)
        assertFalse(items.any { it.mode == IntensityMode.RIR })
    }

    @Test
    fun plannedFailureCarousel_includesRpeFailureAndRir() {
        val items = buildIntensityCarouselItems(
            plannedIntensityMode = IntensityMode.FAILURE,
            reportedIntensityMode = IntensityMode.FAILURE,
            targetRir = 2,
            targetRpe = 8.0,
        )
        val failureIndex = items.indexOfFirst { it.isFailure }
        assertTrue(failureIndex > 0)
        assertTrue(failureIndex < items.lastIndex)
        assertTrue(items.take(failureIndex).all { it.mode == IntensityMode.RPE })
        assertTrue(items.drop(failureIndex + 1).all { it.mode == IntensityMode.RIR })
    }

    @Test
    fun indexFromState_selectsFailureWhenReached() {
        val items = buildIntensityCarouselItems(
            plannedIntensityMode = IntensityMode.RPE,
            reportedIntensityMode = IntensityMode.RPE,
            targetRir = null,
            targetRpe = 8.0,
        )
        val failureIndex = items.indexOfFirst { it.isFailure }
        assertEquals(
            failureIndex,
            intensityCarouselIndexFromState(
                items = items,
                reachedFailure = true,
                intensityText = "",
                reportedIntensityMode = IntensityMode.RPE,
            ),
        )
    }

    @Test
    fun selectionFromFailureItem_setsReachedFailure() {
        val selection = intensitySelectionFromCarouselItem(
            IntensityCarouselItem(
                display = INTENSITY_CAROUSEL_FAILURE_LABEL,
                isFailure = true,
            ),
        )
        assertTrue(selection.reachedFailure)
        assertEquals("", selection.intensityText)
    }

    @Test
    fun selectionFromRirItem_preservesRirMode() {
        val selection = intensitySelectionFromCarouselItem(
            IntensityCarouselItem(
                display = "2",
                mode = IntensityMode.RIR,
                numericValue = 2.0,
            ),
        )
        assertFalse(selection.reachedFailure)
        assertEquals(IntensityMode.RIR, selection.reportedIntensityMode)
        assertEquals("2", selection.intensityText)
    }

    @Test
    fun indexFromState_emptyText_selectsPlannedRpe() {
        val items = buildIntensityCarouselItems(
            plannedIntensityMode = IntensityMode.RPE,
            reportedIntensityMode = IntensityMode.RPE,
            targetRir = null,
            targetRpe = 8.0,
        )
        val index = intensityCarouselIndexFromState(
            items = items,
            reachedFailure = false,
            intensityText = "",
            reportedIntensityMode = IntensityMode.RPE,
            plannedRpe = 8.0,
        )
        assertEquals("8", items[index].display)
        assertEquals(IntensityMode.RPE, items[index].mode)
    }

    @Test
    fun indexFromState_emptyText_selectsPlannedRir() {
        val items = buildIntensityCarouselItems(
            plannedIntensityMode = IntensityMode.RIR,
            reportedIntensityMode = IntensityMode.RIR,
            targetRir = 2,
            targetRpe = null,
        )
        val index = intensityCarouselIndexFromState(
            items = items,
            reachedFailure = false,
            intensityText = "",
            reportedIntensityMode = IntensityMode.RIR,
            plannedRir = 2,
        )
        assertEquals("2", items[index].display)
        assertEquals(IntensityMode.RIR, items[index].mode)
    }

    @Test
    fun repsIndex_clampsOutOfRange() {
        assertEquals(50, repsCarouselIndexFromValue("999", 50))
        assertEquals(0, repsCarouselIndexFromValue("-3", 50))
    }

    @Test
    fun effectiveWeightText_usesGhostWhenBlankOrZero() {
        assertEquals("80", effectiveCarouselWeightText("", "80"))
        assertEquals("80", effectiveCarouselWeightText("0", "80"))
        assertEquals("72.5", effectiveCarouselWeightText("72.5", "80"))
    }

    @Test
    fun effectiveCarouselSelectedIndex_prefersCenterWhileInFlight() {
        assertEquals(7, effectiveCarouselSelectedIndex(selectedIndex = 3, centeredIndex = 7, itemCount = 10))
        assertEquals(3, effectiveCarouselSelectedIndex(selectedIndex = 3, centeredIndex = 99, itemCount = 10))
    }

    @Test
    fun effectiveRepsText_usesGhostWhenBlankOrZero() {
        assertEquals("10", effectiveCarouselRepsText("", "10"))
        assertEquals("10", effectiveCarouselRepsText("0", "10"))
        assertEquals("8", effectiveCarouselRepsText("8", "10"))
    }

    @Test
    fun effectiveIntensity_usesVisibleCarouselWhenTextBlank() {
        val items = buildIntensityCarouselItems(
            plannedIntensityMode = IntensityMode.RPE,
            reportedIntensityMode = IntensityMode.RPE,
            targetRir = null,
            targetRpe = 8.0,
        )
        val index = intensityCarouselIndexFromState(
            items = items,
            reachedFailure = false,
            intensityText = "",
            reportedIntensityMode = IntensityMode.RPE,
            plannedRpe = 8.0,
        )
        assertEquals(
            8.0,
            effectiveCarouselIntensityValue(
                intensityText = "",
                reachedFailure = false,
                items = items,
                selectedIndex = index,
            )!!,
            0.001,
        )
    }

    @Test
    fun effectiveIntensity_failureUsesTen() {
        assertEquals(
            10.0,
            effectiveCarouselIntensityValue(
                intensityText = "",
                reachedFailure = true,
                items = emptyList(),
                selectedIndex = 0,
            )!!,
            0.001,
        )
    }
}
