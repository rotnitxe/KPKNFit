package com.example.kpkn.screens.sessioneditor

import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.ApreBlockReference
import com.example.kpkn.data.models.SetPurpose
import com.example.kpkn.data.models.TrainingMode
import com.example.kpkn.domain.training.ApreVariant
import com.example.kpkn.domain.training.resolveLiveApreBlock
import com.example.kpkn.screens.sessioneditor.components.withApreBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionEditorSessionHelpersTest {

    private fun session(id: String, modifiedAt: Long, extraSets: Int = 1): Session = Session(
        id = id,
        name = if (extraSets > 1) "updated" else "original",
        lastModifiedAtMs = modifiedAt,
        exercises = listOf(
            Exercise(
                id = "ex",
                name = "Press",
                sets = (0 until extraSets).map { ExerciseSet(id = "s$it", targetReps = 8) },
            ),
        ),
    )

    @Test
    fun programSessionWinsWhenNewerThanEditorDraft() {
        val program = session("s1", modifiedAt = 2_000L, extraSets = 3)
        val draftSession = session("s1", modifiedAt = 500L, extraSets = 1)
        val draft = PersistedSessionEditorDraft(
            programId = "p",
            sessionId = "s1",
            weekId = "w",
            macroIndex = 0,
            mesoIndex = 0,
            session = draftSession,
            savedAtMs = 1_500L,
        )
        val resolved = resolveNewestSession(existing = program, fallback = program, persistedDraft = draft)
        assertEquals("updated", resolved.name)
        assertEquals(3, resolved.exercises.first().sets.size)
    }

    @Test
    fun draftWinsWhenNewerThanProgram() {
        val program = session("s1", modifiedAt = 1_000L, extraSets = 1)
        val draftSession = session("s1", modifiedAt = 2_000L, extraSets = 2)
        val draft = PersistedSessionEditorDraft(
            programId = "p",
            sessionId = "s1",
            weekId = "w",
            macroIndex = 0,
            mesoIndex = 0,
            session = draftSession,
            savedAtMs = 2_500L,
        )
        val resolved = resolveNewestSession(existing = program, fallback = program, persistedDraft = draft)
        assertEquals(2, resolved.exercises.first().sets.size)
    }

    @Test
    fun missingDraftReturnsProgram() {
        val program = session("s1", modifiedAt = 1L)
        val resolved = resolveNewestSession(existing = program, fallback = program, persistedDraft = null)
        assertSame(program, resolved)
    }

    @Test
    fun dirtyDraftDivergedByLiveCommitProducesExplicitConflictWithoutOverwritingEither() {
        val baseline = session("s1", modifiedAt = 100L, extraSets = 1)
        val dirtyDraftSession = baseline.copy(name = "Mi edición local")
        val liveUpdatedSession = session("s1", modifiedAt = 500L, extraSets = 3)
        val persistedDraft = PersistedSessionEditorDraft(
            programId = "p",
            sessionId = "s1",
            weekId = "w",
            macroIndex = 0,
            mesoIndex = 0,
            session = dirtyDraftSession,
            savedAtMs = 200L,
            baselineSession = baseline,
            baselineLastModifiedAtMs = 100L,
            liveModifiedAtMs = 500L,
        )

        val resolution = resolveNewestSessionWithConflict(
            existing = liveUpdatedSession,
            fallback = liveUpdatedSession,
            persistedDraft = persistedDraft,
        )

        assertEquals("Mi edición local", resolution.session.name)
        assertNotNull("Debe exponer conflicto explícito", resolution.liveConflict)
        assertEquals("Mi edición local", resolution.liveConflict!!.draftSession.name)
        assertEquals(3, resolution.liveConflict!!.programSession.exercises.first().sets.size)
    }

    @Test
    fun nonDirtyDraftYieldsSilentlyToNewerLiveProgramSession() {
        val baseline = session("s1", modifiedAt = 100L, extraSets = 1)
        val liveUpdatedSession = session("s1", modifiedAt = 500L, extraSets = 3)
        val persistedDraft = PersistedSessionEditorDraft(
            programId = "p",
            sessionId = "s1",
            weekId = "w",
            macroIndex = 0,
            mesoIndex = 0,
            session = baseline.copy(),
            savedAtMs = 200L,
            baselineSession = baseline,
            baselineLastModifiedAtMs = 100L,
            liveModifiedAtMs = 500L,
        )

        val resolution = resolveNewestSessionWithConflict(
            existing = liveUpdatedSession,
            fallback = liveUpdatedSession,
            persistedDraft = persistedDraft,
        )

        assertNull("Sin edición sucia en el editor no debe molestar con conflicto", resolution.liveConflict)
        assertEquals(3, resolution.session.exercises.first().sets.size)
    }

    @Test
    fun invalidApreExitPreviewConvertsOnlyUniqueMarkerIdsAndPreservesForeignAmraps() {
        val exercise = Exercise(
            id = "apre-invalid",
            name = "Press",
            trainingMode = TrainingMode.REPS,
            reference1RM = 200.0,
            referenceTrainingMax = 180.0,
            sets = listOf(
                ExerciseSet("s1", targetReps = 5, weight = 100.0),
                ExerciseSet("ambiguous", targetReps = 6, weight = 120.0, purpose = SetPurpose.AMRAP, amrapMinimumReps = 6),
                ExerciseSet("foreign", targetReps = 8, weight = 80.0, purpose = SetPurpose.AMRAP, amrapMinimumReps = 6),
                ExerciseSet("s4", targetReps = 6, weight = 140.0, purpose = SetPurpose.AMRAP, amrapMinimumReps = 6),
            ),
            apreBlock = ApreBlockReference(6, listOf("s1", "ambiguous", "ambiguous", "s4")),
        )

        assertNull(resolveLiveApreBlock(exercise))
        val preview = exercise.apreExitPreview()
        assertEquals(setOf("s1", "s4"), preview.markedSetIds)
        assertEquals(listOf(1, 2), preview.preservedAmrapIndices)
        assertTrue(preview.summary.contains("S1, S4"))
        assertTrue(preview.summary.contains("S2, S3"))
        assertTrue(preview.summary.contains("No se deducen series por posición"))

        val converted = exercise.withoutApreBlock()
        assertEquals(TrainingMode.REPS, converted.trainingMode)
        assertNull(converted.apreBlock)
        assertEquals(SetPurpose.AMRAP, converted.sets[1].purpose)
        assertEquals(6, converted.sets[1].amrapMinimumReps)
        assertEquals(SetPurpose.AMRAP, converted.sets[2].purpose)
        assertEquals(6, converted.sets[2].amrapMinimumReps)
        assertEquals(SetPurpose.NONE, converted.sets[3].purpose)
        assertNull(converted.sets[3].amrapMinimumReps)
        assertEquals(200.0, converted.reference1RM!!, 0.0)
        assertEquals(180.0, converted.referenceTrainingMax!!, 0.0)
    }

    @Test
    fun normalizationDoesNotCalculateLoadsWhileApreMarkerNeedsReview() {
        val exercise = Exercise(
            id = "apre-invalid-loads",
            name = "Press",
            trainingMode = TrainingMode.REPS,
            reference1RM = 200.0,
            sets = listOf(
                ExerciseSet("s1", targetReps = 5),
                ExerciseSet("s2", targetReps = 8),
                ExerciseSet("s3", targetReps = 10, purpose = SetPurpose.AMRAP, amrapMinimumReps = 10),
                ExerciseSet("s4", targetReps = 10, purpose = SetPurpose.AMRAP, amrapMinimumReps = 10),
            ),
            apreBlock = ApreBlockReference(10, listOf("s1", "s2", "missing", "s4")),
        )

        val normalized = exercise.normalizeExercise()

        assertNull(resolveLiveApreBlock(normalized))
        assertEquals(listOf(null, null, null, null), normalized.sets.map { it.weight })
    }

    @Test
    fun repairingApreCreatesFourDistinctIdsAndKeepsRepsAndXrmSeparateFromRmReferences() {
        val exercise = Exercise(
            id = "apre-repair",
            name = "Press",
            trainingMode = TrainingMode.REPS,
            reference1RM = 200.0,
            referenceTrainingMax = 180.0,
            apreBlock = ApreBlockReference(10, listOf("missing")),
            sets = listOf(ExerciseSet("old", targetReps = 8, weight = 80.0, purpose = SetPurpose.AMRAP)),
        )
        var next = 0

        val repaired = exercise.withApreBlock(ApreVariant.APRE_6) { "repaired-${++next}" }

        assertEquals(TrainingMode.REPS, repaired.trainingMode)
        assertEquals(4, repaired.apreBlock!!.setIds.size)
        assertEquals(4, repaired.apreBlock!!.setIds.distinct().size)
        assertEquals(repaired.apreBlock!!.setIds, repaired.sets.map { it.id })
        assertEquals(ApreVariant.APRE_6.xrmReps, resolveLiveApreBlock(repaired)!!.variant.xrmReps)
        assertEquals(200.0, repaired.reference1RM!!, 0.0)
        assertEquals(180.0, repaired.referenceTrainingMax!!, 0.0)
    }

    @Test
    fun restoreExerciseSetAtRound_padsGapsBeforeInserting() {
        val existing = listOf(ExerciseSet(id = "s0", targetReps = 8))
        val restored = ExerciseSet(id = "s2", targetReps = 6)
        val result = restoreExerciseSetAtRound(existing, roundIndex = 2, restored)
        assertEquals(3, result.size)
        assertEquals("s0", result[0].id)
        assertTrue(result[1].isEmptySlot)
        assertEquals("s2", result[2].id)
        assertEquals(6, result[2].targetReps)
    }

    @Test
    fun updateWeekSessions_usesGlobalMesoIndexAcrossBlocks() {
        val program = Program(
            id = "p",
            name = "P",
            macrocycles = listOf(
                Macrocycle(
                    id = "macro",
                    name = "Macro",
                    blocks = listOf(
                        Block(
                            id = "b1",
                            name = "B1",
                            mesocycles = listOf(
                                Mesocycle(
                                    id = "meso0",
                                    name = "M0",
                                    weeks = listOf(
                                        ProgramWeek(
                                            id = "w0",
                                            name = "W0",
                                            sessions = listOf(Session(id = "s0", name = "Keep")),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                        Block(
                            id = "b2",
                            name = "B2",
                            mesocycles = listOf(
                                Mesocycle(
                                    id = "meso1",
                                    name = "M1",
                                    weeks = listOf(
                                        ProgramWeek(
                                            id = "w1",
                                            name = "W1",
                                            sessions = listOf(Session(id = "s1", name = "Old")),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )
        val updated = program.updateWeekSessions(
            macroIndex = 0,
            mesoIndex = 1,
            weekId = "w1",
        ) { sessions ->
            sessions.map { if (it.id == "s1") it.copy(name = "Saved") else it }
        }
        val week0 = updated.macrocycles[0].blocks[0].mesocycles[0].weeks[0]
        val week1 = updated.macrocycles[0].blocks[1].mesocycles[0].weeks[0]
        assertEquals("Keep", week0.sessions.single().name)
        assertEquals("Saved", week1.sessions.single().name)
    }
}
