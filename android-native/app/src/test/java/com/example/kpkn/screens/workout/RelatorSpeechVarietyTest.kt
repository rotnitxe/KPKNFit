package com.example.kpkn.screens.workout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RelatorSpeechVarietyTest {

    @Test
    fun weightBelowRotatesThenSuppresses() {
        val variants = listOf("A ahora 70 kg", "Bajas a 70 kg", "70 kg, más liviano")
        var memory = RelatorSpeechMemory()
        val picks = mutableListOf<RelatorVariantPick>()
        repeat(4) {
            val pick = pickRelatorVariant(RelatorSpeechBucket.WEIGHT_BELOW, variants, memory)
            picks += pick
            if (!pick.suppressed) memory = memory.record(pick.fingerprint)
        }
        assertEquals(listOf("A ahora 70 kg", "Bajas a 70 kg", "70 kg, más liviano"), picks.take(3).map { it.text })
        assertFalse(picks[0].suppressed)
        assertFalse(picks[1].suppressed)
        assertFalse(picks[2].suppressed)
        assertTrue(picks[3].suppressed)
    }

    @Test
    fun fingerprintIgnoresKilograms() {
        val first = relatorSpeechFingerprint(RelatorSpeechBucket.WEIGHT_BELOW, 0)
        val second = relatorSpeechFingerprint(RelatorSpeechBucket.WEIGHT_BELOW, 0)
        assertEquals(first, second)
        assertEquals("WEIGHT_BELOW:0", first)
    }

    @Test
    fun situateSharesFingerprintFamilyAcrossSetIndexes() {
        assertEquals(
            relatorSpeechFingerprint(RelatorSpeechBucket.IDLE_FIRST_HIST, 0),
            relatorSpeechFingerprint(RelatorSpeechBucket.IDLE_MID, 0),
        )
        assertEquals("IDLE_SITUATE:0", relatorSpeechFingerprint(RelatorSpeechBucket.IDLE_LAST, 0))
    }

    @Test
    fun prDoesNotSilenceWhenPoolExhausted() {
        val variants = listOf("Nuevo PR: RM ≈ 120 kg. Sigue así.")
        var memory = RelatorSpeechMemory()
        repeat(3) {
            val pick = pickRelatorVariant(RelatorSpeechBucket.PR, variants, memory)
            assertFalse(pick.suppressed)
            memory = memory.record(pick.fingerprint)
        }
        val fourth = pickRelatorVariant(RelatorSpeechBucket.PR, variants, memory)
        assertFalse(fourth.suppressed)
        assertEquals(variants[0], fourth.text)
    }

    @Test
    fun assistDoesNotRotateOrSilence() {
        val variants = listOf("Quedan 4 minutos. Convierte a dropsets.")
        var memory = RelatorSpeechMemory()
        val first = pickRelatorVariant(RelatorSpeechBucket.ASSIST_TIME, variants, memory)
        memory = memory.record(first.fingerprint)
        val second = pickRelatorVariant(RelatorSpeechBucket.ASSIST_TIME, variants, memory)
        assertFalse(second.suppressed)
        assertEquals(first.text, second.text)
    }

    @Test
    fun neverRepeatsFingerprintConsecutiveWhenAnotherUnusedExists() {
        val variants = listOf("uno", "dos", "tres")
        val memory = RelatorSpeechMemory().record(relatorSpeechFingerprint(RelatorSpeechBucket.REPS_BELOW, 0))
        val pick = pickRelatorVariant(RelatorSpeechBucket.REPS_BELOW, variants, memory)
        assertEquals("dos", pick.text)
        assertNotEquals(relatorSpeechFingerprint(RelatorSpeechBucket.REPS_BELOW, 0), pick.fingerprint)
    }

    @Test
    fun fourthWeightBelowResolveFallsBackToSituate() {
        val memory = (0..2).fold(RelatorSpeechMemory()) { acc, index ->
            acc.record(relatorSpeechFingerprint(RelatorSpeechBucket.WEIGHT_BELOW, index))
        }
        val line = WorkoutLiveRelator.resolve(
            weightSnap(entered = 70.0, lastLifted = 80.0).copy(speechMemory = memory),
        ).text!!.lowercase()
        assertTrue(line.contains("serie 1"))
        assertFalse(line.contains("bajas a 70"))
        assertFalse(line.contains("ahora 70 kg;"))
    }

    @Test
    fun differentKilosStillSameWeightBelowMold() {
        val first = WorkoutLiveRelator.resolve(weightSnap(entered = 70.0, lastLifted = 80.0))
        val memory = RelatorSpeechMemory().record(first.fingerprint!!)
        val second = WorkoutLiveRelator.resolve(
            weightSnap(entered = 65.0, lastLifted = 80.0).copy(speechMemory = memory),
        )
        assertNotEquals(first.text, second.text)
        assertTrue(second.text!!.lowercase().contains("bajas") || second.text!!.lowercase().contains("liviano"))
    }

    @Test
    fun speechSessionCommitsOnContextChange() {
        val session = RelatorSpeechSession()
        val first = session.resolve(weightSnap(entered = 70.0, lastLifted = 80.0, setKey = "a"))
        val second = session.resolve(weightSnap(entered = 65.0, lastLifted = 80.0, setKey = "b"))
        assertNotEquals(first.text, second.text)
        assertEquals(1, session.memory.fingerprints.size)
        assertEquals(first.fingerprint, session.memory.fingerprints.single())
    }

    @Test
    fun allPredictableVariantsStayWithinBudget() {
        val snap = LiveRelatorSnapshot(
            visible = true,
            phase = RelatorPhase.WORKING,
            family = RelatorFamily.PRESS,
            exerciseDisplayName = "Press banca",
            setIndex = 1,
            setCount = 3,
            hasHistory = true,
            lastChangedField = RelatorChangedField.WEIGHT,
            enteredWeight = 70.0,
            enteredWeightRaw = "70",
            suggestedWeight = 80.0,
            lastLiftedWeight = 80.0,
            plannedReps = 10.0,
            plannedIntensity = 8.0,
            sessionLastSet = RelatorSessionSetMemory(1, 80.0, 8),
            idleCycle = 1,
            axialLoadFactor = 1.0,
        )
        RelatorSpeechBucket.entries.forEach { bucket ->
            WorkoutLiveRelatorCatalog.variantsFor(bucket, snap).forEach { template ->
                val rendered = renderRelatorTemplate(template, snap.shortExerciseName(), RelatorVoice.of(false))
                assertTrue("$bucket too long (${rendered.length}): $rendered", rendered.length <= RELATOR_MAX_LINE_CHARS)
            }
        }
    }

    private fun weightSnap(
        entered: Double,
        lastLifted: Double,
        setKey: String = "test-set",
    ): LiveRelatorSnapshot = LiveRelatorSnapshot(
        visible = true,
        phase = RelatorPhase.WORKING,
        family = RelatorFamily.PRESS,
        exerciseDisplayName = "Press banca",
        setIndex = 0,
        setCount = 3,
        hasHistory = true,
        lastChangedField = RelatorChangedField.WEIGHT,
        enteredWeight = entered,
        enteredWeightRaw = entered.toInt().toString(),
        suggestedWeight = lastLifted,
        lastLiftedWeight = lastLifted,
        plannedReps = 10.0,
        setKey = setKey,
        sessionSpeechKey = "ses",
    )
}
