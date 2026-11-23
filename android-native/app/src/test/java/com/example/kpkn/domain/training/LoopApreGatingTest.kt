package com.example.kpkn.domain.training

import com.example.kpkn.data.models.ApreBlockReference
import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.Session
import com.example.kpkn.screens.programdetail.design.structure.programCycleTemplatesFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LoopApreGatingTest {

    private fun buildApreExercise(id: String = "bench", name: String = "Press de Banca"): Exercise {
        val sets = Apre.block(100.0, ApreVariant.APRE_10).mapIndexed { idx, s ->
            ExerciseSet(
                id = "${id}_s$idx",
                weight = s.loadKg,
                targetReps = s.targetReps,
                amrapMinimumReps = s.minimumReps,
                purpose = s.purpose,
            )
        }
        return Exercise(
            id = id,
            name = name,
            reference1RM = 100.0,
            sets = sets,
            apreBlock = ApreBlockReference(10, sets.map { it.id }),
        )
    }

    private fun buildNormalExercise(id: String = "curl", name: String = "Curl de Bíceps"): Exercise = Exercise(
        id = id,
        name = name,
        sets = listOf(
            ExerciseSet(id = "${id}_1", targetReps = 10, targetRIR = 2, weight = 15.0),
            ExerciseSet(id = "${id}_2", targetReps = 10, targetRIR = 2, weight = 15.0),
        ),
    )

    private fun programWithExercises(vararg exercises: Exercise): Program = Program(
        id = "prog1",
        name = "Rutina",
        macrocycles = listOf(
            Macrocycle(
                id = "m1",
                name = "M1",
                blocks = listOf(
                    Block(
                        id = "b1",
                        name = "B1",
                        mesocycles = listOf(
                            Mesocycle(
                                id = "meso1",
                                name = "Meso 1",
                                weeks = listOf(
                                    ProgramWeek(
                                        id = "w1",
                                        name = "S1",
                                        isLoopWeek = false,
                                        sessions = listOf(
                                            Session(
                                                id = "ses1",
                                                name = "Día 1",
                                                exercises = exercises.toList(),
                                            ),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun apreTemplateIsHiddenWhenProgramHasZeroApreExercisesAndShownWithNamesWhenPresent() {
        val noApreProgram = programWithExercises(buildNormalExercise())
        val apreInNoApre = LoopWaveModulator.apreExercisesInProgram(noApreProgram)
        assertTrue(apreInNoApre.isEmpty())

        val filteredEmpty = programCycleTemplatesFor(emptyList())
        assertFalse(filteredEmpty.any { it.id == "wave:onda-apre-10-6-3" })

        val withApreProgram = programWithExercises(buildApreExercise(), buildNormalExercise())
        val apreExercises = LoopWaveModulator.apreExercisesInProgram(withApreProgram)
        assertEquals(listOf("Press de Banca"), apreExercises.map { it.name })

        val filteredWithApre = programCycleTemplatesFor(apreExercises.map { it.name })
        val apreTemplate = filteredWithApre.first { it.id == "wave:onda-apre-10-6-3" }
        assertTrue(apreTemplate.description.contains("Afecta solo a: Press de Banca"))
    }

    @Test
    fun apreWaveOnlyModulatesExercisesWithValidLiveApreBlock() {
        val aprePreset = LoopWaveModulator.wavePresets().first { it.id == "onda-apre-10-6-3" }
        val apreBench = buildApreExercise()
        val normalCurl = buildNormalExercise()
        // Un ejercicio con marcador APRE roto (solo 1 serie) no tiene resolveLiveApreBlock válido y no debe ser modulado
        val brokenApre = Exercise(
            id = "broken",
            name = "Sentadilla rota",
            apreBlock = ApreBlockReference(10, listOf("b1")),
            sets = listOf(ExerciseSet(id = "b1", targetReps = 10, weight = 60.0)),
        )

        val session = Session(
            id = "ses1",
            name = "Torso",
            exercises = listOf(apreBench, normalCurl, brokenApre),
        )
        val modulated = LoopWaveModulator.applyWaveToSession(
            session = session,
            loop = aprePreset.loop,
            step = aprePreset.loop.waveSteps[1], // Semana 2: APRE 6
        )

        assertEquals(6, modulated.exercises[0].apreBlock?.xrmReps)
        assertEquals(normalCurl, modulated.exercises[1])
        assertEquals(brokenApre, modulated.exercises[2])

        val programWithoutLiveApre = programWithExercises(normalCurl, brokenApre).copy(loops = listOf(aprePreset.loop))
        assertNull(LoopWaveModulator.activeWaveLabel(programWithoutLiveApre, cycleNumber = 2))
    }
}
