package com.example.kpkn.screens.sessioneditor

import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.ManualSessionOverride
import com.example.kpkn.data.models.ManualOverrideScope
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.BlockGoal
import com.example.kpkn.data.protocols.DayRecipe
import com.example.kpkn.data.protocols.TrainingPlanRecipe
import com.example.kpkn.data.protocols.weekRecipe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionEditorTransferOutcomeTest {
    @Test
    fun appendUsesLiveDestinationIdAndLeavesOtherDaysUntouched() {
        val source = session("source", day = 1, exerciseId = "source-exercise")
        val staleTarget = session("stale-target", day = 2, exerciseId = "stale-exercise")
        val untouched = session("untouched", day = 3, exerciseId = "keep-exercise")
        val savedProgram = program(source, staleTarget, untouched)
        val selectedKey = buildCloneDayOptions(savedProgram, source.id).single { it.dayOfWeek == 2 }.key

        // The destination changed while the transfer sheet was open. Keep the
        // selected week/day key, but resolve the actual session from current data.
        val liveTarget = session("live-target", day = 2, exerciseId = "live-exercise")
        val currentProgram = program(source, liveTarget, untouched)
        val result = applySessionTransfersToProgram(
            program = currentProgram,
            currentSessionId = source.id,
            pending = pending(source, selectedKey, SessionCloneApplyMode.APPEND),
        )

        val target = result.program.findWeekById("week")!!.sessions.single { it.dayOfWeek == 2 }
        assertEquals("live-target", target.id)
        assertEquals(
            listOf("Movimiento live-exercise", "Movimiento source-exercise"),
            target.allExercises().map { it.name },
        )
        val targetExerciseIds = target.allExercises().map { it.id }
        assertTrue("live destination exercise identity is retained", "live-exercise" in targetExerciseIds)
        assertEquals(2, targetExerciseIds.toSet().size)
        assertTrue(target.allExercises().none { it.id == "source-exercise" })
        assertEquals(untouched, result.program.findWeekById("week")!!.sessions.single { it.id == untouched.id })
        assertEquals("live-target", result.affectedTargets.single().session.id)
    }

    @Test
    fun replacePreservesTheExistingDestinationIdentity() {
        val source = session("source", day = 1, exerciseId = "source-exercise")
        val target = session("target", day = 2, exerciseId = "old-exercise")
        val program = program(source, target)
        val key = buildCloneDayOptions(program, source.id).single { it.dayOfWeek == 2 }.key

        val result = applySessionTransfersToProgram(
            program = program,
            currentSessionId = source.id,
            pending = pending(source, key, SessionCloneApplyMode.REPLACE),
        )

        val transferred = result.affectedTargets.single().session
        assertEquals(target.id, transferred.id)
        assertEquals(source.allExercises().map { it.name }, transferred.allExercises().map { it.name })
        assertTrue(transferred.allExercises().none { it.id == "source-exercise" })
        assertTrue(transferred.allExercises().none { it.id == "old-exercise" })
        assertEquals(1, result.program.findWeekById("week")!!.sessions.count { it.dayOfWeek == 2 })
    }

    @Test
    fun emptyDestinationCreatesOneSessionAndRematerializedOptionsFindItsId() {
        val source = session("source", day = 1, exerciseId = "source-exercise")
        val program = program(source)
        val key = buildCloneDayOptions(program, source.id).single { it.dayOfWeek == 2 }.key

        val result = applySessionTransfersToProgram(
            program = program,
            currentSessionId = source.id,
            pending = pending(source, key, SessionCloneApplyMode.APPEND),
        )

        val created = result.affectedTargets.single().session
        assertNotEquals(source.id, created.id)
        assertEquals(2, created.dayOfWeek)
        assertEquals(1, result.program.findWeekById("week")!!.sessions.count { it.dayOfWeek == 2 })
        val rematerializedTarget = buildCloneDayOptions(result.program, source.id).single { it.dayOfWeek == 2 }
        assertEquals(created.id, rematerializedTarget.existingSessionId)
    }

    @Test
    fun vanishedTargetWeekIsReportedAsUnaffectedInsteadOfWritingAStaleSession() {
        val source = session("source", day = 1, exerciseId = "source-exercise")
        val original = program(source)
        val key = buildCloneDayOptions(original, source.id).single { it.dayOfWeek == 2 }.key
        val current = original.copy(macrocycles = emptyList())

        val result = applySessionTransfersToProgram(
            program = current,
            currentSessionId = source.id,
            pending = pending(source, key, SessionCloneApplyMode.REPLACE),
        )

        assertTrue(result.affectedTargets.isEmpty())
        assertEquals(current, result.program)
    }

    @Test
    fun selectedWeekAndDayStillResolveAfterProgramIndicesShift() {
        val source = session("source", day = 1, exerciseId = "source-exercise")
        val original = program(source)
        val selectedKey = buildCloneDayOptions(original, source.id).single { it.dayOfWeek == 2 }.key
        val leadingMacro = Macrocycle(
            id = "leading-macro",
            name = "Nuevo macro",
            blocks = listOf(
                Block(
                    id = "leading-block",
                    name = "Nuevo bloque",
                    mesocycles = listOf(
                        Mesocycle(
                            id = "leading-meso",
                            name = "Nuevo meso",
                            weeks = listOf(ProgramWeek(id = "leading-week", name = "Nueva semana")),
                        ),
                    ),
                ),
            ),
        )
        val current = original.copy(macrocycles = listOf(leadingMacro) + original.macrocycles)
        val currentKey = buildCloneDayOptions(current, source.id).single { it.weekId == "week" && it.dayOfWeek == 2 }.key
        assertNotEquals(selectedKey, currentKey)

        val result = applySessionTransfersToProgram(
            program = current,
            currentSessionId = source.id,
            pending = pending(source, selectedKey, SessionCloneApplyMode.APPEND),
        )

        assertEquals(1, result.affectedTargets.size)
        assertEquals(2, result.affectedTargets.single().session.dayOfWeek)
        assertEquals("week", result.affectedTargets.single().option.weekId)
    }

    @Test
    fun eachTransferredDestinationGetsItsOwnSessionOverrideCoordinates() {
        val source = session("source", day = 1, exerciseId = "source-exercise")
        val destination = session("destination", day = 2, exerciseId = "destination-exercise")
        val program = program(source, destination)
        val key = buildCloneDayOptions(program, source.id).single { it.dayOfWeek == 2 }.key
        val transferred = applySessionTransfersToProgram(
            program = program,
            currentSessionId = source.id,
            pending = pending(source, key, SessionCloneApplyMode.REPLACE),
        )

        val frozen = transferred.freezeTransferredSessionOverrides()
        val target = transferred.affectedTargets.single()
        val override = frozen.manualSessionOverrides.single { it.sessionId == target.session.id }

        assertEquals("week", override.weekId)
        assertEquals(2, override.weekOccurrence)
        assertEquals("recipe-destination-exercise", override.recipeDayId)
        assertEquals(ManualOverrideScope.SESSION, override.scope)
    }

    @Test
    fun appendAndReplacePreserveExistingDestinationRecipeDayBeforeUsingTheCurrentRecipeDay() {
        val source = session("source", day = 1, exerciseId = "source-exercise")
        val destination = session("destination", day = 2, exerciseId = "destination-exercise")
        val program = program(source, destination).copy(
            sourceRecipe = TrainingPlanRecipe(
                id = "current-recipe",
                weeks = listOf(
                    weekRecipe(
                        weekNumber = 2,
                        blockIndex = 0,
                        blockName = "Bloque",
                        blockGoal = BlockGoal.ACCUMULATION,
                        days = listOf(
                            DayRecipe(id = "current-source-day", label = "A", weekday = 1, slots = emptyList()),
                            DayRecipe(id = "current-destination-day", label = "B", weekday = 2, slots = emptyList()),
                        ),
                    ),
                ),
            ),
            manualSessionOverrides = listOf(
                ManualSessionOverride(
                    sessionId = destination.id,
                    weekId = "week",
                    weekOccurrence = 2,
                    recipeDayId = "stored-destination-day",
                    scope = ManualOverrideScope.SESSION,
                    reason = "override anterior del destino",
                ),
            ),
        )
        val key = buildCloneDayOptions(program, source.id).single { it.dayOfWeek == 2 }.key

        listOf(SessionCloneApplyMode.APPEND, SessionCloneApplyMode.REPLACE).forEach { mode ->
            val result = applySessionTransfersToProgram(
                program = program,
                currentSessionId = source.id,
                pending = pending(source, key, mode),
            )
            val frozen = result.freezeTransferredSessionOverrides()

            assertEquals("$mode keeps the captured target recipe day", "stored-destination-day", result.affectedTargets.single().option.destinationRecipeDayId)
            assertEquals(
                "$mode keeps its destination override",
                "stored-destination-day",
                frozen.manualSessionOverrides.single { it.sessionId == destination.id && it.scope == ManualOverrideScope.SESSION }.recipeDayId,
            )
        }
    }

    @Test
    fun retryingTheSameCommittedAppendDoesNotCloneThePayloadTwice() {
        val source = session("source", day = 1, exerciseId = "source-exercise")
        val destination = session("destination", day = 2, exerciseId = "destination-exercise")
        val base = program(source, destination)
        val key = buildCloneDayOptions(base, source.id).single { it.dayOfWeek == 2 }.key
        val pending = pending(source, key, SessionCloneApplyMode.APPEND)

        val first = applySessionTransfersToProgram(base, source.id, pending)
        val committed = first.freezeTransferredSessionOverrides()
        val retry = applySessionTransfersToProgram(committed, source.id, pending)

        assertTrue("la repetición reconoce el recibo durable", retry.affectedTargets.isEmpty())
        assertEquals(1, retry.receiptTargets.size)
        assertEquals(2, retry.program.findWeekById("week")!!.sessions.single { it.dayOfWeek == 2 }.allExercises().size)
        assertEquals("recipe-destination-exercise", retry.program.manualSessionOverrides.single { it.sessionId == "destination" }.recipeDayId)
    }

    private fun pending(source: Session, targetKey: String, mode: SessionCloneApplyMode) =
        PendingTransferToDays(
            targetKeys = setOf(targetKey),
            applyMode = mode,
            sourceSession = source,
        )

    private fun session(id: String, day: Int, exerciseId: String): Session = Session(
        id = id,
        name = "Sesión $id",
        dayOfWeek = day,
        isMainSession = true,
        exercises = listOf(
            Exercise(
                id = exerciseId,
                name = "Movimiento $exerciseId",
                recipeDayId = "recipe-$exerciseId",
                sets = listOf(ExerciseSet(id = "$exerciseId-set", targetReps = 8)),
            ),
        ),
    )

    private fun program(source: Session, vararg otherSessions: Session): Program = Program(
        id = "program",
        name = "Programa",
        macrocycles = listOf(
            Macrocycle(
                id = "macro",
                name = "Macro",
                blocks = listOf(
                    Block(
                        id = "block",
                        name = "Bloque",
                        mesocycles = listOf(
                            Mesocycle(
                                id = "meso",
                                name = "Meso",
                                weeks = listOf(
                                    ProgramWeek(
                                        id = "week",
                                        name = "Semana",
                                        progressionIndex = 2,
                                        sessions = listOf(source) + otherSessions,
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        ),
    )
}
