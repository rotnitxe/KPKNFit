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
import com.example.kpkn.data.models.PlannedTechnique
import com.example.kpkn.data.models.ReplacementPersistenceScopeV2
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SimpleProgramKind
import com.example.kpkn.data.models.SupersetGroup
import com.example.kpkn.data.models.WeekVariant
import com.example.kpkn.data.models.TechniqueType
import com.example.kpkn.screens.sessioneditor.CatalogSupersetConfig
import com.example.kpkn.screens.sessioneditor.freshPlannedCopy
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
            recipeDayId = "leg",
            recipeSlotId = "squat",
            sets = listOf(ExerciseSet(id = "s0", targetReps = 5)),
        )
        val variantEx = Exercise(
            id = "e1b",
            name = "Prensa",
            exerciseDbId = "leg_press__machine",
            catalogConfigurationId = "leg_press__machine",
            recipeDayId = "leg",
            recipeSlotId = "variant-slot",
            sets = listOf(ExerciseSet(id = "sb0", targetReps = 10)),
        )
        val sessionA = Session(
            id = "sess-1",
            name = "Pierna",
            dayOfWeek = 1,
            exercises = listOf(baseEx),
            sessionB = Session(id = "sess-1-b", name = "Pierna B", exercises = listOf(variantEx)),
            logicalLineageId = "recipe:dated@1/day:leg",
        )
        val futureSession = Session(
            id = "sess-2",
            name = "Pierna",
            dayOfWeek = 1,
            exercises = listOf(baseEx.copy(id = "e2", sets = listOf(ExerciseSet(id = "s2", targetReps = 5)))),
            sessionB = Session(
                id = "sess-2-b",
                name = "Pierna B",
                exercises = listOf(variantEx.copy(id = "e2b", sets = listOf(ExerciseSet(id = "sb2", targetReps = 10)))),
            ),
            logicalLineageId = "recipe:dated@1/day:leg",
        )
        val outsider = futureSession.copy(
            id = "outsider",
            sessionB = futureSession.sessionB?.copy(id = "outsider-b"),
            logicalLineageId = "other-origin",
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
                                    weeks = listOf(
                                        ProgramWeek(id = "w1", name = "Semana 1", sessions = listOf(sessionA)),
                                        ProgramWeek(id = "w2", name = "Semana 2", sessions = listOf(futureSession, outsider)),
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
                id = "leg_extension__machine",
                name = "Extensión",
                equipment = "Máquina",
                catalogRevision = "rev-2",
                catalogDefinitionId = "leg_extension",
                catalogConfigurationId = "leg_extension__machine",
            )
            val finalReplacement = ExerciseMuscleInfo(
                id = "hack_squat__machine",
                name = "Hack Squat",
                equipment = "Máquina",
                catalogRevision = "rev-2",
                catalogDefinitionId = "hack_squat",
                catalogConfigurationId = "hack_squat__machine",
            )
            // A→B is kept for this session only; then B→C must update the original plan slot by stable identity.
            fx.controller.replaceExercise("e1b", replacement, deferPersistencePrompt = true)
            fx.controller.showDeferredReplacementPromptIfNeeded("e1b")
            assertNotNull(fx.state.pendingReplacementPersistencePrompt)
            runBlocking { fx.controller.commitPendingReplacementPersistence(ReplacementPersistenceScopeV2.SESSION_ONLY) }
            assertEquals("leg_extension__machine", fx.state.session!!.sessionB!!.exercises.single().catalogConfigurationId)
            fx.controller.replaceExercise("e1b", finalReplacement, deferPersistencePrompt = true)
            fx.controller.showDeferredReplacementPromptIfNeeded("e1b")
            runBlocking { fx.controller.commitPendingReplacementPersistence(ReplacementPersistenceScopeV2.PERMANENT) }
            runBlocking { fx.controller.awaitPendingPersistence() }

            val weeks = fx.repository.getProgramById(program.id)!!
                .macrocycles.single().blocks.single().mesocycles.single().weeks
            val updatedSession = weeks[0].sessions.single()
            val updatedFuture = weeks[1].sessions[0]
            val updatedOutsider = weeks[1].sessions[1]
            assertEquals("Variant A stays intact", "squat__barbell", updatedSession.exercises.single().catalogConfigurationId)
            assertEquals("Variant B updated", "hack_squat__machine", updatedSession.sessionB!!.exercises.single().catalogConfigurationId)
            assertEquals("Future occurrence in the same origin and variant updates", "hack_squat__machine", updatedFuture.sessionB!!.exercises.single().catalogConfigurationId)
            assertEquals("Another origin stays unchanged", "leg_press__machine", updatedOutsider.sessionB!!.exercises.single().catalogConfigurationId)
            assertEquals("Future variant A stays intact", "squat__barbell", updatedFuture.exercises.single().catalogConfigurationId)
            assertTrue("Session timestamp stamped", updatedSession.lastModifiedAtMs > 0L)
            assertNotNull("Invalidated editor draft timestamp", fx.invalidatedLiveTimestampMs)
        } finally {
            StructuralControllerFixture.close()
        }
    }

    @Test
    fun complexBlock_editUsesFutureLineageVariantAndRecipeSlotNotSimilarSessions() {
        val lineage = "recipe:plan-1@4/day:pull"
        fun pullUp(id: String, slot: String) = Exercise(
            id = id,
            name = "Dominadas",
            exerciseDbId = "pull_up",
            catalogConfigurationId = "pull_up",
            recipeDayId = "pull",
            recipeSlotId = slot,
            sets = listOf(ExerciseSet(id = "$id-set", targetReps = 8)),
        )
        fun day(id: String, first: Exercise, second: Exercise, origin: String = lineage) = Session(
            id = id,
            name = "Tirón",
            dayOfWeek = 1,
            exercises = listOf(first, second),
            logicalLineageId = origin,
        )
        val sWeek1 = day("s-w1", pullUp("w1-slot-a", "slot-a"), pullUp("w1-slot-b", "slot-b"))
        val sWeek2 = day("s-w2", pullUp("w2-slot-a", "slot-a"), pullUp("w2-slot-b", "slot-b"))
        val sWeek3 = day("s-w3", pullUp("w3-slot-a", "slot-a"), pullUp("w3-slot-b", "slot-b"))
        val outsider = day("s-outsider", pullUp("outsider-slot-a", "slot-a"), pullUp("outsider-slot-b", "slot-b"), "other-origin")
        val variantB = day("s-w3-b", pullUp("w3b-slot-a", "slot-a"), pullUp("w3b-slot-b", "slot-b"))
        val change = PendingStructuralChange.AddSet(
            exerciseId = "w2-slot-a",
            exerciseName = "Dominadas",
            exerciseSlot = 0,
            exerciseCanonicalKey = "pull_up",
            targetExercise = LiveExerciseSlotIdentity.from(sWeek2.exercises.first()),
        )
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
                                        ProgramWeek(id = "w1", name = "Semana 1", variant = WeekVariant.A, sessions = listOf(sWeek1)),
                                        ProgramWeek(id = "w2", name = "Semana 2", variant = WeekVariant.A, sessions = listOf(sWeek2)),
                                    ),
                                ),
                            ),
                        ),
                        Block(
                            id = "b2",
                            name = "Bloque 2",
                            mesocycles = listOf(
                                Mesocycle(
                                    id = "meso2",
                                    name = "Meso 2",
                                    weeks = listOf(
                                        ProgramWeek(id = "w3", name = "Semana 3", variant = WeekVariant.A, sessions = listOf(sWeek3, outsider)),
                                        ProgramWeek(id = "w3-b", name = "Semana 3 B", variant = WeekVariant.B, sessions = listOf(variantB)),
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
                // El orden visible se invirtió: el slot de receta conserva su identidad estable.
                session = sWeek2.copy(exercises = sWeek2.exercises.reversed()),
                programId = program.id,
                weekId = "w2",
                activeMode = WeekVariant.A,
                plannedSessionBaseline = sWeek2,
                pendingStructuralPersistence = change,
            ),
            programId = program.id,
        )
        try {
            runBlocking { fx.repository.addProgramNow(program).getOrThrow() }
            runBlocking { fx.controller.commitStructuralPersistence(ReplacementPersistenceScopeV2.BLOCK_MATCHING) }
            runBlocking { fx.controller.awaitPendingPersistence() }

            val updatedProgram = fx.repository.getProgramById(program.id)!!
            val weeks = updatedProgram.macrocycles.single().blocks.flatMap { it.mesocycles }.flatMap { it.weeks }
            fun target(week: Int, session: Int, id: String) = weeks[week].sessions[session].exercises.single { it.id == id }
            assertEquals(1, target(0, 0, "w1-slot-a").sets.size) // Earlier occurrence stays unchanged.
            assertEquals(2, target(1, 0, "w2-slot-a").sets.size)
            assertEquals(1, target(1, 0, "w2-slot-b").sets.size) // Same canonical exercise, another recipe slot.
            assertEquals(2, target(2, 0, "w3-slot-a").sets.size) // Future occurrence with the same lineage and variant.
            assertEquals(1, target(2, 0, "w3-slot-b").sets.size)
            assertEquals(1, target(2, 1, "outsider-slot-a").sets.size) // Same label/structure, different origin.
            assertEquals(1, target(3, 0, "w3b-slot-a").sets.size) // Another variant.
            assertTrue(weeks[1].sessions.single().lastModifiedAtMs > 0L)
            assertTrue(weeks[2].sessions.first().lastModifiedAtMs > 0L)
            assertNull(fx.state.pendingStructuralPersistence)
            val versions = runBlocking { fx.repository.getLiveSessionVersions(program.id, sWeek2.id) }
            assertEquals(2, versions.size)
            assertTrue(versions.any { it.operationId == change.operationId })
            runBlocking {
                fx.repository.commitLiveProgramLineageEditNow(
                    programId = program.id,
                    weekId = "w2",
                    macroIndex = 0,
                    mesoIndex = 0,
                    sessionId = sWeek2.id,
                    variant = WeekVariant.A,
                    operationId = change.operationId,
                    reason = "retry",
                    transform = { error("An idempotent retry must not transform the program again") },
                )
            }
            assertEquals(2, runBlocking { fx.repository.getLiveSessionVersions(program.id, sWeek2.id) }.size)

            // The exercise list in the live state is reversed; the recipe slot finds the right exercise.
            // Within it, the source set ID beats a stale ordinal while a future occurrence with its own
            // set IDs replays the same explicit set slot.
            val removeSet = PendingStructuralChange.RemoveSet(
                exerciseId = "w2-slot-a",
                exerciseName = "Dominadas",
                setIndex = 0,
                targetSetId = "w2-slot-a-set",
                targetExercise = LiveExerciseSlotIdentity.from(sWeek2.exercises.first()),
            )
            fx.state = fx.state.copy(pendingStructuralPersistence = removeSet)
            runBlocking { fx.controller.commitStructuralPersistence(ReplacementPersistenceScopeV2.BLOCK_MATCHING) }
            runBlocking { fx.controller.awaitPendingPersistence() }
            val afterRemove = fx.repository.getProgramById(program.id)!!
                .macrocycles.single().blocks.flatMap { it.mesocycles }.flatMap { it.weeks }
            val sourceSets = afterRemove[1].sessions.single().exercises.first { it.recipeSlotId == "slot-a" }.sets
            assertEquals(1, sourceSets.size)
            assertTrue(sourceSets.none { it.id == "w2-slot-a-set" })
            assertEquals(1, afterRemove[2].sessions.first().exercises.first { it.recipeSlotId == "slot-a" }.sets.size)
            assertEquals(3, runBlocking { fx.repository.getLiveSessionVersions(program.id, sWeek2.id) }.size)
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

    @Test
    fun prescriptionEditReplaysSetRestAndTechniqueByStableSlotAcrossBlocks() {
        val lineage = "recipe:press@2/day:push"
        val beforeA = ExerciseSet(
            id = "source-a",
            targetReps = 10,
            restAfterSeconds = 90,
            plannedSlotId = "push|press|set:0",
        )
        val beforeB = ExerciseSet(id = "source-b", targetReps = 8, plannedSlotId = "push|press|set:1")
        val beforeC = ExerciseSet(id = "source-c", targetReps = 7, plannedSlotId = "push|press|set:2")
        val afterA = beforeA.copy(
            targetReps = 12,
            restAfterSeconds = 120,
            plannedIntensityTechniques = listOf(
                PlannedTechnique(type = TechniqueType.REST_PAUSE, params = mapOf("miniSets" to "2")),
            ),
        )
        val added = beforeC.freshPlannedCopy(newId = { "source-d" }, preserveEmptySlot = false).copy(
            targetReps = 15,
            restAfterSeconds = 60,
        )
        fun exercise(id: String, sets: List<ExerciseSet>) = Exercise(
            id = id,
            name = "Press banca",
            exerciseDbId = "bench_press__barbell",
            catalogConfigurationId = "bench_press__barbell",
            recipeDayId = "push",
            recipeSlotId = "press",
            sets = sets,
        )
        val sourceExercise = exercise("source-exercise", listOf(beforeA, beforeB, beforeC))
        val editedExercise = assignStablePrescriptionSlots(
            sourceExercise,
            sourceExercise.copy(sets = listOf(afterA, beforeC, added)),
        )
        val stableAdded = editedExercise.sets.single { it.id == added.id }
        val sourceSession = Session(
            id = "source-session",
            name = "Empuje",
            exercises = listOf(sourceExercise),
            logicalLineageId = lineage,
        )
        // The future list is deliberately reordered and has different IDs. Planned slots are the only safe mapping.
        val futureExercise = exercise(
            "future-exercise",
            listOf(
                ExerciseSet(id = "future-c", targetReps = 6, plannedSlotId = "push|press|set:2"),
                ExerciseSet(id = "future-b", targetReps = 8, plannedSlotId = "push|press|set:1"),
                ExerciseSet(id = "future-a", targetReps = 8, restAfterSeconds = 60, plannedSlotId = "push|press|set:0"),
            ),
        )
        val futureSession = Session(
            id = "future-session",
            name = "Empuje",
            exercises = listOf(futureExercise),
            logicalLineageId = lineage,
        )
        val outsider = futureSession.copy(id = "outsider-session", logicalLineageId = "other-lineage")
        val change = PendingStructuralChange.UpdateExercisePrescription(
            exerciseId = sourceExercise.id,
            exerciseName = sourceExercise.name,
            targetExercise = LiveExerciseSlotIdentity.from(sourceExercise),
            setDeltas = listOf(
                PrescriptionSetDelta(
                    setOrdinal = 0,
                    targetSetId = beforeA.id,
                    before = SetPrescriptionSnapshot.from(beforeA),
                    after = SetPrescriptionSnapshot.from(afterA),
                    targetPlannedSlotId = beforeA.plannedSlotId,
                ),
            ),
            activeMode = WeekVariant.A,
            sourceLogicalLineageId = lineage,
            setAdditions = listOf(
                PrescriptionSetAddition(
                    setOrdinal = 2,
                    sourceSetId = stableAdded.id,
                    plannedSlotId = stableAdded.plannedSlotId,
                    prescription = SetPrescriptionSnapshot.from(stableAdded),
                ),
            ),
            setRemovals = listOf(PrescriptionSetRemoval(beforeB.id, beforeB.plannedSlotId)),
        )
        val program = Program(
            id = "prog-prescription",
            name = "Empuje",
            structure = ProgramStructure.COMPLEX,
            macrocycles = listOf(
                Macrocycle(
                    id = "macro",
                    name = "Macro",
                    blocks = listOf(
                        Block(
                            id = "block-1",
                            name = "Bloque 1",
                            mesocycles = listOf(
                                Mesocycle(id = "meso-1", name = "Meso 1", weeks = listOf(ProgramWeek("w1", "Semana 1", sessions = listOf(sourceSession)))),
                            ),
                        ),
                        Block(
                            id = "block-2",
                            name = "Bloque 2",
                            mesocycles = listOf(
                                Mesocycle(id = "meso-2", name = "Meso 2", weeks = listOf(ProgramWeek("w2", "Semana 2", sessions = listOf(futureSession, outsider)))),
                            ),
                        ),
                    ),
                ),
            ),
        )
        val fx = StructuralControllerFixture.open(
            WorkoutUiState(
                session = sourceSession,
                programId = program.id,
                weekId = "w1",
                activeMode = WeekVariant.A,
                plannedSessionBaseline = sourceSession,
                pendingStructuralPersistence = change,
            ),
            programId = program.id,
        )
        try {
            runBlocking { fx.repository.addProgramNow(program).getOrThrow() }
            runBlocking { fx.controller.commitStructuralPersistence(ReplacementPersistenceScopeV2.BLOCK_MATCHING) }
            runBlocking { fx.controller.awaitPendingPersistence() }

            val saved = fx.repository.getProgramById(program.id)!!
            val weeks = saved.macrocycles.single().blocks.flatMap { it.mesocycles }.flatMap { it.weeks }
            val sourceSets = weeks[0].sessions.single().exercises.single().sets
            val futureSets = weeks[1].sessions.first().exercises.single().sets
            val sourceUpdatedA = sourceSets.single { it.plannedSlotId == beforeA.plannedSlotId }
            val futureUpdatedA = futureSets.single { it.plannedSlotId == beforeA.plannedSlotId }

            assertEquals(
                listOf("push|press|set:0", "push|press|set:2", "push|press|set:3"),
                sourceSets.map { it.plannedSlotId },
            )
            assertEquals(12, sourceUpdatedA.targetReps)
            assertEquals(120, sourceUpdatedA.restAfterSeconds)
            assertEquals(afterA.plannedIntensityTechniques, sourceUpdatedA.plannedIntensityTechniques)
            assertEquals(10, futureUpdatedA.targetReps) // +2 prescription delta over the future week’s own target.
            assertEquals(120, futureUpdatedA.restAfterSeconds)
            assertEquals(afterA.plannedIntensityTechniques, futureUpdatedA.plannedIntensityTechniques)
            assertEquals(15, futureSets.last().targetReps)
            assertEquals(60, futureSets.last().restAfterSeconds)
            assertEquals("other-lineage", weeks[1].sessions[1].logicalLineageId)
            assertEquals(3, weeks[1].sessions[1].exercises.single().sets.size)
            assertNull(fx.state.pendingStructuralPersistence)
        } finally {
            StructuralControllerFixture.close()
        }
    }

    @Test
    fun prescriptionReplayRejectsAmbiguousFutureSetInsteadOfUsingOrdinal() {
        val lineage = "legacy:press-lineage"
        val before = ExerciseSet(id = "source-set", targetReps = 10)
        val after = before.copy(targetReps = 12)
        fun session(id: String, exerciseId: String, set: ExerciseSet) = Session(
            id = id,
            name = "Press",
            exercises = listOf(
                Exercise(
                    id = exerciseId,
                    name = "Press banca",
                    recipeDayId = "push",
                    recipeSlotId = "press",
                    sets = listOf(set),
                ),
            ),
            logicalLineageId = lineage,
        )
        val source = session("source-session", "source-exercise", before)
        val future = session("future-session", "future-exercise", ExerciseSet(id = "future-set", targetReps = 8))
        val change = PendingStructuralChange.UpdateExercisePrescription(
            exerciseId = "source-exercise",
            exerciseName = "Press banca",
            targetExercise = LiveExerciseSlotIdentity.from(source.exercises.single()),
            setDeltas = listOf(
                PrescriptionSetDelta(
                    setOrdinal = 0,
                    targetSetId = before.id,
                    before = SetPrescriptionSnapshot.from(before),
                    after = SetPrescriptionSnapshot.from(after),
                ),
            ),
            activeMode = WeekVariant.A,
            sourceLogicalLineageId = lineage,
        )
        val program = Program(
            id = "prog-ambiguous-set",
            name = "Press",
            structure = ProgramStructure.COMPLEX,
            macrocycles = listOf(
                Macrocycle(
                    id = "macro",
                    name = "Macro",
                    blocks = listOf(
                        Block(
                            id = "block-1",
                            name = "Bloque 1",
                            mesocycles = listOf(Mesocycle(id = "meso-1", name = "Meso 1", weeks = listOf(ProgramWeek("w1", "Semana 1", sessions = listOf(source))))),
                        ),
                        Block(
                            id = "block-2",
                            name = "Bloque 2",
                            mesocycles = listOf(Mesocycle(id = "meso-2", name = "Meso 2", weeks = listOf(ProgramWeek("w2", "Semana 2", sessions = listOf(future))))),
                        ),
                    ),
                ),
            ),
        )
        val fx = StructuralControllerFixture.open(
            WorkoutUiState(
                session = source,
                programId = program.id,
                weekId = "w1",
                activeMode = WeekVariant.A,
                plannedSessionBaseline = source,
                pendingStructuralPersistence = change,
            ),
            programId = program.id,
        )
        try {
            runBlocking { fx.repository.addProgramNow(program).getOrThrow() }
            runBlocking { fx.controller.commitStructuralPersistence(ReplacementPersistenceScopeV2.BLOCK_MATCHING) }
            runBlocking { fx.controller.awaitPendingPersistence() }

            val persisted = fx.repository.getProgramById(program.id)!!
            val weeks = persisted.macrocycles.single().blocks.flatMap { it.mesocycles }.flatMap { it.weeks }
            assertEquals(10, weeks.first().sessions.single().exercises.single().sets.single().targetReps)
            assertEquals(8, weeks.last().sessions.single().exercises.single().sets.single().targetReps)
            assertNotNull("Ambiguous legacy slot keeps the mutation pending", fx.state.pendingStructuralPersistence)
            assertTrue(fx.rejectedMessages.isNotEmpty())
        } finally {
            StructuralControllerFixture.close()
        }
    }
}
