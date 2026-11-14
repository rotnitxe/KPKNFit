package com.example.kpkn.domain.training

import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.EquipmentInventory
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.Loop
import com.example.kpkn.data.models.LoopType
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.PlateStock
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramStructure
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.Session
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LoopPresetEngineTest {
    private fun program(
        loops: List<Loop> = emptyList(),
        exercise: Exercise = mainExercise(),
    ): Program {
        val session = Session(
            id = "base-session",
            name = "Día principal",
            dayOfWeek = 1,
            isMainSession = true,
            exercises = listOf(exercise),
        )
        return Program(
            id = "program",
            name = "Rutina",
            structure = ProgramStructure.SIMPLE,
            loops = loops,
            macrocycles = listOf(Macrocycle(
                id = "macro",
                name = "Ciclo",
                blocks = listOf(Block(
                    id = "block",
                    name = "Base",
                    mesocycles = listOf(Mesocycle(
                        id = "meso",
                        name = "Base",
                        weeks = listOf(ProgramWeek("week-1", "Semana 1", sessions = listOf(session))),
                    )),
                )),
            )),
        )
    }

    private fun mainExercise() = Exercise(
        id = "squat-plan",
        name = "Sentadilla",
        exerciseDbId = "squat__barbell",
        catalogConfigurationId = "squat__barbell",
        reference1RM = 100.0,
        isCompetitionLift = true,
        sets = listOf(
            ExerciseSet("set-1", targetReps = 5, targetRPE = 8.0, weight = 80.0),
            ExerciseSet("set-2", targetReps = 5, targetRPE = 8.0, weight = 80.0),
            ExerciseSet("set-3", targetReps = 5, targetRPE = 8.0, weight = 80.0),
            ExerciseSet("set-4", targetReps = 5, targetRPE = 8.0, weight = 80.0),
        ),
    )

    @Test
    fun previewUsesTheLastTrainableBaseWeekAndRealPrescriptions() {
        val preview = LoopPresetEngine.preview(program(), "deload-1rm")

        assertEquals("week-1", preview.sourceWeekId)
        assertEquals(4, preview.sourceWorkingSets)
        assertTrue(preview.deloadWorkingSets < preview.sourceWorkingSets)
        assertEquals(1, preview.testMovements.size)
        assertEquals("squat__barbell", preview.testMovements.single().configurationId)
        assertEquals(100.0, preview.testMovements.single().referenceKg!!, 0.001)
        assertTrue(preview.cadenceConflicts.isEmpty())
        assertTrue(preview.collisionNotes.any { it.contains("C8") && it.contains("Descarga") && it.contains("Test 1RM") })
    }

    @Test
    fun combinedPresetBuildsRealDeloadAndSelectedTestSessionsWithFreshIds() {
        var nextId = 0
        val source = program()
        val loops = LoopPresetEngine.materialize(
            program = source,
            presetId = "deload-1rm",
            selectedMovementIds = setOf("squat__barbell"),
            attemptKgByMovement = mapOf("squat__barbell" to 105.0),
            equipmentInventory = EquipmentInventory(plates = listOf(PlateStock(weightKg = 1.25))),
            idProvider = { "generated-${++nextId}" },
        )

        assertEquals(listOf(LoopType.DELOAD, LoopType.ONE_RM_TEST), loops.map { it.type })
        assertEquals(listOf(4, 8), loops.map { it.repeatEveryXLoops })
        val deloadSession = loops[0].sessions.single()
        assertEquals(2, deloadSession.exercises.single().sets.size)
        assertTrue(deloadSession.exercises.single().sets.all { it.weight!! < 80.0 })

        val testSession = loops[1].sessions.single()
        val testExercise = testSession.exercises.single()
        assertEquals("Sentadilla", testExercise.name)
        assertNotEquals("squat-plan", testExercise.id)
        assertEquals(4, testExercise.sets.size)
        assertEquals(105.0, testExercise.sets.last().weight!!, 0.001)
        assertTrue(testExercise.sets.none { it.id.startsWith("set-") })
        assertTrue(testExercise.sets.dropLast(1).all { it.weight!! % 2.5 == 0.0 })
        assertTrue(loops.all { loop -> loop.sessions.all { session -> session.exercises.isNotEmpty() } })
    }

    @Test
    fun cadenceCollisionAndMissingMainMovementCannotProduceLoops() {
        val colliding = program(loops = listOf(
            Loop("existing", "Custom", LoopType.CUSTOM, repeatEveryXLoops = 4),
        ))
        val preview = LoopPresetEngine.preview(colliding, "deload-4")
        assertFalse(preview.cadenceConflicts.isEmpty())
        assertTrue(runCatching {
            LoopPresetEngine.materialize(colliding, "deload-4", emptySet())
        }.isFailure)

        val accessoryOnly = program(exercise = mainExercise().copy(isCompetitionLift = false))
        assertTrue(LoopPresetEngine.preview(accessoryOnly, "1rm-8").testMovements.isEmpty())
        assertTrue(runCatching {
            LoopPresetEngine.materialize(accessoryOnly, "1rm-8", emptySet())
        }.isFailure)

        val overlappingDifferentCadence = program(loops = listOf(
            Loop("every-three", "Personalizado", LoopType.CUSTOM, repeatEveryXLoops = 3),
        ))
        assertTrue(LoopPresetEngine.preview(overlappingDifferentCadence, "deload-1rm")
            .cadenceConflicts.any { it.contains("C12") })
    }
}
