package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramCalendarization
import com.example.kpkn.data.models.ProgramCalendarizationMode
import com.example.kpkn.data.models.ProgramStructure
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.ReplacementPersistenceScopeV2
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SimpleProgramKind
import com.example.kpkn.data.models.SupersetGroup
import com.example.kpkn.data.models.WeekVariant
import com.example.kpkn.screens.sessioneditor.CatalogSupersetConfig
import com.example.kpkn.testsupport.RoomTestRule
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class WorkoutStructuralPersistenceReplayTest {

    @get:Rule
    val roomRule = RoomTestRule()

    @Test
    fun addSupersetOfExistingIds_writesGroupAndDoesNotNoOp() {
        val press = Exercise(id = "press", name = "Press", sets = listOf(ExerciseSet(id = "p0")))
        val row = Exercise(id = "row", name = "Remo", sets = listOf(ExerciseSet(id = "r0")))
        val programSession = Session(id = "sess", name = "Push", exercises = listOf(press, row))
        val group = SupersetGroup(id = "g1", exerciseOrder = listOf("press", "row"))
        val change = PendingStructuralChange.AddSuperset(
            groupId = "g1",
            afterExerciseId = "press",
            newExerciseIds = listOf("press", "row"),
            newExerciseNames = listOf("Press", "Remo"),
            supersetConfig = CatalogSupersetConfig(rounds = 3, restBetweenExercisesSeconds = 45, restAfterSupersetSeconds = 90),
            group = group,
        )

        val updated = replayAddSupersetOnModeSession(
            modeSession = programSession,
            liveExercisesById = mapOf("press" to press, "row" to row),
            change = change,
            insertAfter = ::insertAfter,
            insertEnd = { session, exercise -> session.copy(exercises = session.exercises + exercise) },
        )

        assertTrue(updated !== programSession)
        val created = updated.allSupersetGroups().firstOrNull { it.id == "g1" }
        assertNotNull(created)
        assertEquals(listOf("press", "row"), created?.exerciseOrder)
        assertEquals(2, updated.exercises.size)
    }

    @Test
    fun dissolveReplay_doesNotCopyLiveWeights() {
        val livePress = Exercise(
            id = "press",
            name = "Press",
            sets = listOf(ExerciseSet(id = "p0", weight = 120.0, targetReps = 8)),
            supersetGroupRef = "g1",
        )
        val programPress = Exercise(
            id = "press",
            name = "Press",
            sets = listOf(ExerciseSet(id = "p0", weight = 80.0, targetReps = 8)),
            supersetGroupRef = "g1",
        )
        val programRow = Exercise(
            id = "row",
            name = "Remo",
            sets = listOf(ExerciseSet(id = "r0", weight = 70.0)),
            supersetGroupRef = "g1",
        )
        val program = Session(
            id = "sess",
            name = "Push",
            exercises = listOf(programPress, programRow),
            supersetGroups = listOf(SupersetGroup(id = "g1", exerciseOrder = listOf("press", "row"))),
        )
        val dissolved = com.example.kpkn.domain.workout.SupersetRules.dissolve(program, "g1")
        assertEquals(80.0, dissolved.exercises.first { it.id == "press" }.sets.first().weight ?: 0.0, 0.001)
        assertTrue(dissolved.allSupersetGroups().none { it.id == "g1" })
        assertEquals(120.0, livePress.sets.first().weight ?: 0.0, 0.001)
    }

    private fun insertAfter(session: Session, currentId: String, newExercise: Exercise): Session {
        val idx = session.exercises.indexOfFirst { it.id == currentId }
        if (idx == -1) return session.copy(exercises = session.exercises + newExercise)
        return session.copy(exercises = session.exercises.toMutableList().apply { add(idx + 1, newExercise) })
    }

    @Test
    fun simpleDatedAndVariantBSession_persistsStructuralAndReplacementChanges() {
        val baseEx = Exercise(
            id = "e1",
            name = "Sentadilla",
            exerciseDbId = "squat__barbell",
            catalogConfigurationId = "squat__barbell",
            sets = listOf(ExerciseSet(id = "s0", targetReps = 5)),
        )
        val variantEx = Exercise(
            id = "e1b",
            name = "Prensa",
            exerciseDbId = "leg_press__machine",
            catalogConfigurationId = "leg_press__machine",
            sets = listOf(ExerciseSet(id = "sb0", targetReps = 10)),
        )
        val sessionA = Session(
            id = "sess-1",
            name = "Pierna",
            dayOfWeek = 1,
            exercises = listOf(baseEx),
            sessionB = Session(id = "sess-1-b", name = "Pierna B", exercises = listOf(variantEx)),
        )
        val program = Program(
            id = "prog-dated",
            name = "Plan Fechado",
            simpleProgramKind = SimpleProgramKind.CALENDARIZED,
            calendarization = ProgramCalendarization(ProgramCalendarizationMode.SIMPLE_DATED),
            macrocycles = listOf(
                Macrocycle(
                    id = "m1",
                    name = "Macro",
                    blocks = listOf(
                        Block(
                            id = "b1",
                            name = "Bloque",
                            mesocycles = listOf(
                                Mesocycle(
                                    id = "meso1",
                                    name = "Meso",
                                    weeks = listOf(ProgramWeek(id = "w1", name = "Semana 1", sessions = listOf(sessionA))),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )
        val fx = StructuralControllerFixture.open(
            WorkoutUiState(
                session = sessionA,
                programId = program.id,
                weekId = "w1",
                activeMode = WeekVariant.B,
                plannedSessionBaseline = sessionA,
            ),
            programId = program.id,
        )
        try {
            runBlocking { fx.repository.addProgramNow(program).getOrThrow() }
            val replacement = ExerciseMuscleInfo(
                id = "hack_squat__machine",
                name = "Hack Squat",
                equipment = "Máquina",
                catalogRevision = "rev-2",
                catalogDefinitionId = "hack_squat",
                catalogConfigurationId = "hack_squat__machine",
            )
            fx.controller.replaceExercise("e1b", replacement, deferPersistencePrompt = true)
            fx.controller.showDeferredReplacementPromptIfNeeded("e1b")
            assertNotNull(fx.state.pendingReplacementPersistencePrompt)
            runBlocking { fx.controller.commitPendingReplacementPersistence(ReplacementPersistenceScopeV2.PERMANENT) }
            runBlocking { fx.controller.awaitPendingPersistence() }

            val updatedSession = fx.repository.getProgramById(program.id)!!
                .macrocycles.single().blocks.single().mesocycles.single().weeks.single().sessions.single()
            assertEquals("Variant A stays intact", "squat__barbell", updatedSession.exercises.single().catalogConfigurationId)
            assertEquals("Variant B updated", "hack_squat__machine", updatedSession.sessionB!!.exercises.single().catalogConfigurationId)
            assertTrue("Session timestamp stamped", updatedSession.lastModifiedAtMs > 0L)
            assertNotNull("Invalidated editor draft timestamp", fx.invalidatedLiveTimestampMs)
        } finally {
            StructuralControllerFixture.close()
        }
    }

    @Test
    fun complexRepeatedBlock_blockMatchingPersistsAllEquivalentWeeksAndStampsManualOverride() {
        val baseEx = Exercise(
            id = "slot-1",
            name = "Dominadas",
            exerciseDbId = "pull_up",
            catalogConfigurationId = "pull_up",
            sets = listOf(ExerciseSet(id = "s0", targetReps = 8)),
        )
        val sWeek1 = Session(id = "s-w1", name = "Tirón", dayOfWeek = 1, exercises = listOf(baseEx))
        val sWeek2 = Session(id = "s-w2", name = "Tirón", dayOfWeek = 1, exercises = listOf(baseEx.copy()))
        val program = Program(
            id = "prog-complex",
            name = "Bloque Avanzado",
            structure = ProgramStructure.COMPLEX,
            macrocycles = listOf(
                Macrocycle(
                    id = "m1",
                    name = "Macro",
                    blocks = listOf(
                        Block(
                            id = "b1",
                            name = "Bloque 1",
                            mesocycles = listOf(
                                Mesocycle(
                                    id = "meso1",
                                    name = "Meso 1",
                                    weeks = listOf(
                                        ProgramWeek(id = "w1", name = "Semana 1", sessions = listOf(sWeek1)),
                                        ProgramWeek(id = "w2", name = "Semana 2", sessions = listOf(sWeek2)),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )
        val fx = StructuralControllerFixture.open(
            WorkoutUiState(
                session = sWeek1,
                programId = program.id,
                weekId = "w1",
                activeMode = WeekVariant.A,
                plannedSessionBaseline = sWeek1,
                pendingStructuralPersistence = PendingStructuralChange.AddSet("slot-1", "Dominadas", exerciseSlot = 0, exerciseCanonicalKey = "pull_up"),
            ),
            programId = program.id,
        )
        try {
            runBlocking { fx.repository.addProgramNow(program).getOrThrow() }
            runBlocking { fx.controller.commitStructuralPersistence(ReplacementPersistenceScopeV2.BLOCK_MATCHING) }
            runBlocking { fx.controller.awaitPendingPersistence() }

            val updatedProgram = fx.repository.getProgramById(program.id)!!
            val weeks = updatedProgram.macrocycles.single().blocks.single().mesocycles.single().weeks
            assertEquals(2, weeks[0].sessions.single().exercises.single().sets.size)
            assertEquals(2, weeks[1].sessions.single().exercises.single().sets.size)
            assertTrue(weeks[0].sessions.single().lastModifiedAtMs > 0L)
            assertTrue(weeks[1].sessions.single().lastModifiedAtMs > 0L)
            assertNull(fx.state.pendingStructuralPersistence)
        } finally {
            StructuralControllerFixture.close()
        }
    }

    @Test
    fun failedProgramWrite_preservesPendingStructuralAndPromptForRetry() {
        val session = Session(id = "sess-1", name = "Push", exercises = listOf(Exercise(id = "ex-1", name = "Press")))
        val program = Program(
            id = "prog-fail",
            name = "Plan",
            macrocycles = listOf(
                Macrocycle(
                    id = "m1",
                    name = "Macro",
                    blocks = listOf(
                        Block(
                            id = "b1",
                            name = "B1",
                            mesocycles = listOf(
                                Mesocycle(
                                    id = "ms1",
                                    name = "M1",
                                    weeks = listOf(ProgramWeek(id = "w1", name = "S1", sessions = listOf(session))),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )
        val fx = StructuralControllerFixture.open(
            WorkoutUiState(
                session = session,
                programId = program.id,
                weekId = "w1",
                pendingStructuralPersistence = PendingStructuralChange.AddSet("ex-1", "Press"),
            ),
            programId = program.id,
        )
        try {
            runBlocking { fx.repository.addProgramNow(program).getOrThrow() }
            // Simula sesión desaparecida/no editable en programa al confirmar guardado -> mutateProgramNow devuelve false.
            val emptied = program.copy(
                macrocycles = listOf(Macrocycle(id = "m1", name = "Macro", blocks = listOf(Block(id = "b1", name = "B1")))),
            )
            runBlocking { fx.repository.mutateProgramNow(program.id) { emptied } }

            runBlocking { fx.controller.commitStructuralPersistence(ReplacementPersistenceScopeV2.PERMANENT) }
            runBlocking { fx.controller.awaitPendingPersistence() }

            assertNotNull("La confirmación pendiente se conserva para reintentar", fx.state.pendingStructuralPersistence)
            assertTrue("Se notifica el rechazo", fx.rejectedMessages.isNotEmpty())
        } finally {
            StructuralControllerFixture.close()
        }
    }
}
