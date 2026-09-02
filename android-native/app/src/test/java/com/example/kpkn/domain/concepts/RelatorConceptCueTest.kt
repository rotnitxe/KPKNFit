package com.example.kpkn.domain.concepts

import com.example.kpkn.data.models.IntensityMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RelatorConceptCueTest {

    private val v1Ids = listOf(
        "carga-axial",
        "pliometria",
        "isometria",
        "fallo-muscular",
        "rir",
        "rpe",
        "fase-excentrica",
        "estres-metabolico",
        "poleas",
        "maquinas",
        "pesos-libres",
        "fuerza",
    )

    @Test
    fun axialOneSpeaksCargaAxial() {
        val cue = pickRelatorConceptCue(
            RelatorConceptSignals(axialLoadFactor = 1.0, exerciseName = "Sentadilla trasera"),
        )
        assertEquals("carga-axial", cue?.id)
    }

    @Test
    fun axialPointThreeDoesNotSpeakCargaAxial() {
        val cue = pickRelatorConceptCue(
            RelatorConceptSignals(
                axialLoadFactor = 0.3,
                exerciseName = "Press banca",
                intensityMode = IntensityMode.RPE,
                plannedIntensity = 8.0,
            ),
        )
        assertEquals("rpe", cue?.id)
        assertFalse(cue?.lines.orEmpty().any { it.contains("Carga axial", ignoreCase = true) })
    }

    @Test
    fun missingProfileDoesNotInventAxialFromSquatName() {
        val cue = pickRelatorConceptCue(
            RelatorConceptSignals(exerciseName = "Sentadilla trasera"),
        )
        assertNull(cue)
    }

    @Test
    fun axialBeatsRpeOnSameExercise() {
        val cue = pickRelatorConceptCue(
            RelatorConceptSignals(
                axialLoadFactor = 0.8,
                exerciseName = "Press militar",
                intensityMode = IntensityMode.RPE,
                plannedIntensity = 8.0,
            ),
        )
        assertEquals("carga-axial", cue?.id)
    }

    @Test
    fun afterAxialShownFallsThroughToRpe() {
        val cue = pickRelatorConceptCue(
            RelatorConceptSignals(
                axialLoadFactor = 1.0,
                exerciseName = "Sentadilla",
                intensityMode = IntensityMode.RPE,
                plannedIntensity = 8.0,
                shownConceptIds = setOf("carga-axial"),
            ),
        )
        assertEquals("rpe", cue?.id)
    }

    @Test
    fun rirBeatsEquipment() {
        val cue = pickRelatorConceptCue(
            RelatorConceptSignals(
                equipmentId = "cable",
                intensityMode = IntensityMode.RIR,
                plannedIntensity = 2.0,
                exerciseName = "Curl en polea",
            ),
        )
        assertEquals("rir", cue?.id)
    }

    @Test
    fun cableAfterRpeShownSpeaksPoleas() {
        val cue = pickRelatorConceptCue(
            RelatorConceptSignals(
                equipmentId = "cable",
                intensityMode = IntensityMode.RPE,
                plannedIntensity = 8.0,
                exerciseName = "Curl en polea",
                shownConceptIds = setOf("rpe"),
            ),
        )
        assertEquals("poleas", cue?.id)
    }

    @Test
    fun plannedFailureSpeaksFallo() {
        val cue = pickRelatorConceptCue(
            RelatorConceptSignals(plannedFailure = true, exerciseName = "Press banca"),
        )
        assertEquals("fallo-muscular", cue?.id)
    }

    @Test
    fun isoHoldAndPlyoDetectFromSignals() {
        assertEquals(
            "isometria",
            pickRelatorConceptCue(RelatorConceptSignals(hasIsoHold = true, exerciseName = "Plank"))?.id,
        )
        assertEquals(
            "pliometria",
            pickRelatorConceptCue(
                RelatorConceptSignals(exerciseName = "Box jump", movementPatternId = "jump-squat"),
            )?.id,
        )
    }

    @Test
    fun negativesAndHighReps() {
        assertEquals(
            "fase-excentrica",
            pickRelatorConceptCue(RelatorConceptSignals(hasNegatives = true, exerciseName = "Curl"))?.id,
        )
        assertEquals(
            "estres-metabolico",
            pickRelatorConceptCue(
                RelatorConceptSignals(plannedReps = 20.0, exerciseName = "Extensión"),
            )?.id,
        )
    }

    @Test
    fun fuerzaNeedsLowRepsAndCompound() {
        assertEquals(
            "fuerza",
            pickRelatorConceptCue(
                RelatorConceptSignals(
                    isCompound = true,
                    plannedReps = 3.0,
                    shownConceptIds = setOf("rpe"),
                    exerciseName = "Peso muerto",
                ),
            )?.id,
        )
        assertNull(
            pickRelatorConceptCue(
                RelatorConceptSignals(isCompound = true, plannedReps = 10.0, exerciseName = "Peso muerto"),
            ),
        )
    }

    @Test
    fun everyV1ConceptHasFriendlyVariantsWithinBudget() {
        v1Ids.forEach { id ->
            val lines = relatorConceptLines(id)
            assertTrue("$id needs 2+ lines", lines.size >= 2)
            lines.forEach { line ->
                assertTrue("$id blank", line.isNotBlank())
                assertTrue("$id too long (${line.length}): $line", line.length <= 140)
            }
        }
    }

    @Test
    fun nullIntensityModeDoesNotCueRpe() {
        assertNull(
            pickRelatorConceptCue(
                RelatorConceptSignals(
                    plannedIntensity = 8.0,
                    exerciseName = "Press banca",
                ),
            ),
        )
    }

    @Test
    fun shownIdsExhaustedReturnsNull() {
        val cue = pickRelatorConceptCue(
            RelatorConceptSignals(
                equipmentId = "dumbbell",
                shownConceptIds = setOf("pesos-libres"),
                exerciseName = "Curl",
            ),
        )
        assertNull(cue)
    }

    @Test
    fun pickAlwaysReturnsKnownCue() {
        val cue = pickRelatorConceptCue(
            RelatorConceptSignals(axialLoadFactor = 1.0, exerciseName = "Sentadilla"),
        )
        assertNotNull(cue)
        assertTrue(cue!!.lines.isNotEmpty())
    }
}
