package com.example.kpkn.domain.training

import com.example.kpkn.data.models.ApreBlockReference
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SetPurpose
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class LoopWaveModulatorTest {

    @Test
    fun waveApre10To6To3ModulatesVariantAcrossCycles() {
        val preset = LoopWaveModulator.wavePresets().first { it.id == "onda-apre-10-6-3" }
        val baseSets = Apre.block(100.0, ApreVariant.APRE_10).mapIndexed { idx, s ->
            ExerciseSet(
                id = "s$idx",
                weight = s.loadKg,
                targetReps = s.targetReps,
                amrapMinimumReps = s.minimumReps,
                purpose = s.purpose,
            )
        }
        val exercise = Exercise(
            id = "ex1",
            name = "Sentadilla",
            reference1RM = 100.0,
            sets = baseSets,
            apreBlock = ApreBlockReference(10, baseSets.map { it.id }),
        )
        val week = ProgramWeek(
            id = "w1",
            name = "Base",
            sessions = listOf(Session(id = "ses1", name = "Día 1", exercises = listOf(exercise))),
        )

        val c1 = LoopWaveModulator.applyWaveToWeek(week, preset.loop, cycleNumber = 1)
        val c2 = LoopWaveModulator.applyWaveToWeek(week, preset.loop, cycleNumber = 2)
        val c3 = LoopWaveModulator.applyWaveToWeek(week, preset.loop, cycleNumber = 3)
        val c4 = LoopWaveModulator.applyWaveToWeek(week, preset.loop, cycleNumber = 4)

        assertEquals(10, c1.sessions.first().exercises.first().apreBlock?.xrmReps)
        assertEquals(6, c2.sessions.first().exercises.first().apreBlock?.xrmReps)
        assertEquals(3, c3.sessions.first().exercises.first().apreBlock?.xrmReps)
        // Wraps back to APRE 10 on cycle 4
        assertEquals(10, c4.sessions.first().exercises.first().apreBlock?.xrmReps)
        assertEquals(SetPurpose.AMRAP, c3.sessions.first().exercises.first().sets[2].purpose)
        assertNotNull(c2.sessions.first().exercises.first().sets[2].weight)
    }

    @Test
    fun rirProgressionModulatesTargetRirAcrossFourWeeks() {
        val preset = LoopWaveModulator.wavePresets().first { it.id == "progresion-rir-3-0" }
        val exercise = Exercise(
            id = "ex1",
            name = "Press",
            sets = listOf(ExerciseSet(id = "s1", targetReps = 8, targetRIR = 3)),
        )
        val week = ProgramWeek(
            id = "w1",
            name = "Base",
            sessions = listOf(Session(id = "ses1", name = "Día 1", exercises = listOf(exercise))),
        )
        val w4 = LoopWaveModulator.applyWaveToWeek(week, preset.loop, cycleNumber = 4)
        assertEquals(0, w4.sessions.first().exercises.first().sets.first().targetRIR)
    }
}
