package com.example.kpkn.domain.cardio

import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.Session
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CardioSessionSummaryTest {

    @Test
    fun prescribedVersusActualIncludesSplitsAndAuge() {
        val details = CardioDetails(type = CardioType.RUN_OUTDOOR, targetDurationSeconds = 20 * 60, targetDistanceKm = 4.0)
        val session = Session(
            id = "s1",
            name = "Cardio",
            exercises = listOf(
                Exercise(id = "ex1", name = "Carrera", cardioDetails = details),
            ),
        )
        val completed = mapOf(
            "ex1_0" to CompletedSet(
                id = "ex1_0",
                timeSeconds = 18 * 60,
                distanceKm = 3.8,
                rpe = 6.0,
                kmSplitPaces = listOf(280, 290, 300),
            ),
        )
        val lines = CardioSessionSummary.lines(session, completed)
        assertEquals(1, lines.size)
        assertTrue(lines[0].prescribedSentence.contains("20 min"))
        assertTrue(lines[0].actualSummary.contains("18 min"))
        assertEquals(3, lines[0].splitLabels.size)
        assertTrue(lines[0].augeLine!!.startsWith("AUGE cardio"))
    }
}
