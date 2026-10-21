package com.example.kpkn.screens.workout.design

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import com.example.kpkn.domain.biomechanics.metrics.CountConfidence
import com.example.kpkn.screens.workout.SetVideoAnalysisState
import org.junit.Assert.*
import org.junit.Test

class WorkoutVideoRepsChipTest {
    @Test fun every_chip_state_matches_the_confidence_table() {
        val cases = listOf(
            SetVideoAnalysisState.Analyzing(42) to VideoChipContent("42\u00A0%", progress = 42),
            SetVideoAnalysisState.Analyzing(-5) to VideoChipContent("0\u00A0%", progress = 0),
            SetVideoAnalysisState.Analyzing(125) to VideoChipContent("100\u00A0%", progress = 100),
            SetVideoAnalysisState.Suggest(8, 0, 0, CountConfidence.ALTA) to VideoChipContent("8"),
            SetVideoAnalysisState.Suggest(8, 1, 0, CountConfidence.ALTA) to VideoChipContent("8 ·1p"),
            SetVideoAnalysisState.Suggest(8, 2, 1, CountConfidence.MEDIA) to VideoChipContent("≈\u00A08"),
            SetVideoAnalysisState.Mismatch(8, 7, 1, 0, CountConfidence.MEDIA) to VideoChipContent("≈\u00A07"),
            SetVideoAnalysisState.Mismatch(8, 7, 0, 0, CountConfidence.ALTA) to VideoChipContent("7", VideoChipTone.ACCENT),
            SetVideoAnalysisState.Mismatch(8, 8, 1, 0, CountConfidence.ALTA) to VideoChipContent("8", VideoChipTone.ACCENT),
            SetVideoAnalysisState.Mismatch(8, 8, 0, 1, CountConfidence.ALTA) to VideoChipContent("8"),
            SetVideoAnalysisState.Agrees(8) to VideoChipContent("✓ 8"),
            SetVideoAnalysisState.Inconclusive to VideoChipContent("?", VideoChipTone.MUTED),
            SetVideoAnalysisState.Suggest(27, 4, 2, CountConfidence.BAJA) to VideoChipContent("?", VideoChipTone.MUTED),
            SetVideoAnalysisState.Mismatch(8, 27, 4, 2, CountConfidence.BAJA) to VideoChipContent("?", VideoChipTone.MUTED),
            SetVideoAnalysisState.Failed("decode") to VideoChipContent("!", VideoChipTone.DANGER),
        )
        cases.forEach { (state, expected) -> assertEquals(state.toString(), expected, workoutVideoChipContent(state)) }
        assertNull(workoutVideoChipContent(SetVideoAnalysisState.Unsupported("Por tiempo")))
    }

    @Test fun menu_and_talkback_use_the_visual_mode_description() {
        val cases = listOf(
            SetVideoAnalysisState.Suggest(8, 1, 1, CountConfidence.ALTA),
            SetVideoAnalysisState.Suggest(8, 1, 1, CountConfidence.MEDIA),
            SetVideoAnalysisState.Mismatch(8, 7, 1, 1, CountConfidence.ALTA, loggedPartials = 1),
            SetVideoAnalysisState.Mismatch(8, 7, 1, 1, CountConfidence.MEDIA),
            SetVideoAnalysisState.Suggest(27, 4, 2, CountConfidence.BAJA),
            SetVideoAnalysisState.Agrees(8), SetVideoAnalysisState.Inconclusive,
            SetVideoAnalysisState.Failed(null), SetVideoAnalysisState.Unsupported("Por tiempo"),
        )
        cases.forEach { state ->
            val line = visualModeAnalysisLine(state, 0)
            val text = line.text.removeSuffix(line.actionLabel?.let { " · $it" }.orEmpty())
            assertEquals(text, workoutVideoRepsText(state))
        }
        assertEquals("Analizando el vídeo… 100\u00A0%", workoutVideoRepsText(SetVideoAnalysisState.Analyzing(105)))
    }

    @Test fun estimated_rom_stays_together_and_low_confidence_never_exposes_it() {
        val high = SetVideoAnalysisState.Suggest(8, 1, 0, CountConfidence.ALTA, romPercent = 95)
        assertTrue(workoutVideoRepsText(high).endsWith("ROM\u00A0≈\u00A095\u00A0%"))
        assertTrue(workoutVideoRepsText(high.copy(confidence = CountConfidence.MEDIA)).endsWith("ROM\u00A0≈\u00A095\u00A0%"))
        assertEquals("Vídeo no concluyente", workoutVideoRepsText(high.copy(confidence = CountConfidence.BAJA)))
    }

    @Test fun popup_fits_the_window_and_keeps_the_trigger_visible() {
        val provider = WorkoutVideoMenuPositionProvider(gap = 14)
        val window = IntSize(360, 880)
        val menu = IntSize(264, 220)
        assertEquals(IntOffset(64, 258), provider.calculatePosition(IntRect(258, 220, 328, 244), window, LayoutDirection.Ltr, menu))
        assertEquals(IntOffset(0, 258), provider.calculatePosition(IntRect(10, 220, 80, 244), window, LayoutDirection.Ltr, menu))
        assertEquals(IntOffset(96, 258), provider.calculatePosition(IntRect(300, 220, 380, 244), window, LayoutDirection.Ltr, menu))
        assertEquals(IntOffset(64, 546), provider.calculatePosition(IntRect(258, 780, 328, 804), window, LayoutDirection.Ltr, menu))
        assertEquals(IntOffset(10, 258), provider.calculatePosition(IntRect(10, 220, 80, 244), window, LayoutDirection.Rtl, menu))
    }
}
