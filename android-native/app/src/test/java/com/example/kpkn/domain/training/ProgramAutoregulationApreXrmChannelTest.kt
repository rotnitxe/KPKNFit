package com.example.kpkn.domain.training

import com.example.kpkn.data.models.AutoregulationMode
import com.example.kpkn.data.models.AutoregulationProposal
import com.example.kpkn.data.models.AutoregulationProposalKind
import com.example.kpkn.data.models.PendingActionResolutionStatus
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.data.protocols.CatalogIds
import com.example.kpkn.data.protocols.LiftSlot
import com.example.kpkn.data.protocols.ProgressionRule
import com.example.kpkn.data.protocols.TrainingPlanRecipe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

/** Flujos APRE desde registros reales, con referencias físicas y sin tratar el XRM como TM/1RM. */
class ProgramAutoregulationApreXrmChannelTest {
    companion object {
        @BeforeClass @JvmStatic fun setUp() = CatalogCompositionTestSupport.install()
    }

    private val recipe = Cal5TestSupport.recipeOf("apre-mann")

    private fun program(mode: AutoregulationMode, id: String): Program =
        Cal5TestSupport.materialize(recipe, mode, Cal5TestSupport.fullProfile(), id)

    private fun recordedLogs(
        program: Program,
        week: ProgramWeek,
        onlyExerciseId: String? = null,
        set4LoadKg: Double = 155.0,
        set4Reps: Int = 17,
        set3Only: Boolean = false,
    ): List<WorkoutLog> = week.sessions.mapNotNull { session ->
        val exercises = session.allExercises().filter {
            (onlyExerciseId == null || it.id == onlyExerciseId) &&
                resolveLiveApreBlock(it, recipe.id, hasApreProgression = true) != null
        }
        if (exercises.isEmpty()) return@mapNotNull null
        val completed = exercises.map { exercise ->
            val base = Cal5TestSupport.exerciseLog(
                program, week, session, exercise,
                weightOf = { index, planned -> if (index == 3) set4LoadKg else planned.weight },
                repsOf = { index, planned -> when (index) {
                    2 -> 12
                    3 -> set4Reps
                    else -> planned.targetReps ?: 5
                } },
            ).completedExercises.single()
            val sets = base.sets.mapIndexed { index, performed ->
                performed.copy(id = exercise.id + "-event-" + index, setIndex = index)
            }
            base.copy(sets = if (set3Only) listOf(sets[2]) else sets)
        }
        Cal5TestSupport.exerciseLog(program, week, session, exercises.first()).copy(
            id = "log-" + program.id + "-" + week.id + "-" + session.id,
            completedExercises = completed,
        )
    }

    private fun xrmHistory(program: Program, exerciseId: String, proposal: AutoregulationProposal) =
        ExerciseLoadReferenceStore.history(program, exerciseId, proposal.apreXrm!!.reference)
            .filter { it.metadata?.sourceId == proposal.apreXrm!!.reference.metadata?.sourceId }

    @Test
    fun real_s4_without_source_recipe_applies_physical_xrm_and_rematerializes_from_pool() {
        val manual = program(AutoregulationMode.AUTO, "apre-manual").copy(
            sourceRecipe = null, sourceProtocolId = null, powerliftingProfile = null,
        )
        val weeks = Cal5TestSupport.weeksOf(manual)
        val sourceWeek = weeks.first()
        val sourceSquat = Cal5TestSupport.exerciseOf(sourceWeek.sessions.first(), CatalogIds.SQ_LOW)
        val proposals = ProgramAutoregulationEngine.evaluateApreXrmOnly(
            manual, sourceWeek, recordedLogs(manual, sourceWeek),
        )
        val squatProposal = proposals.single { it.apreXrm!!.sourceExerciseId == sourceSquat.id }
        assertEquals(AutoregulationProposalKind.ADJUST_XRM, squatProposal.kind)
        assertEquals(162.5, squatProposal.apreXrm!!.reference.capturedLoadKg!!, 1e-6)
        assertEquals(manual.id, squatProposal.apreXrm!!.reference.sourceProgramId)
        assertNull("sin fecha en el log, la captura conserva fecha desconocida",
            squatProposal.apreXrm!!.reference.capturedAtMs)

        val targetWeek = weeks[1]
        val applied = ProgramAutoregulationEngine.apply(
            program = manual, nextWeekId = targetWeek.id, proposals = proposals, recipe = null,
            executedWeekIds = setOf(sourceWeek.id), nowMs = 100L,
        )
        assertTrue(applied.applied)
        assertNull("XRM no crea perfil TM/1RM", applied.program.powerliftingProfile)
        val updatedTarget = Cal5TestSupport.weeksOf(applied.program)[1]
        val targetSquat = Cal5TestSupport.exerciseOf(updatedTarget.sessions.first(), CatalogIds.SQ_LOW)
        assertEquals(162.5, targetSquat.sets[2].weight!!, 1e-6)
        assertEquals(162.5, targetSquat.sets[3].weight!!, 1e-6)
        assertEquals(81.25, targetSquat.sets[0].weight!!, 1e-6)
        assertEquals(1, xrmHistory(applied.program, sourceSquat.id, squatProposal).size)
        assertEquals(1, xrmHistory(applied.program, targetSquat.id, squatProposal).size)

        val rematerialized = PlanMaterializer.rematerializeWeek(
            program = applied.program, weekId = updatedTarget.id, recipe = recipe,
            metadata = CatalogCompositionTestSupport.metadata, idProvider = Cal5TestSupport.SeqIds(),
            executedWeekIds = setOf(sourceWeek.id),
        )
        val rematerializedSquat =
            Cal5TestSupport.exerciseOf(Cal5TestSupport.weeksOf(rematerialized)[1].sessions.first(), CatalogIds.SQ_LOW)
        assertEquals(162.5, rematerializedSquat.sets[3].weight!!, 1e-6)
    }

    @Test
    fun propose_partial_resolution_updates_message_and_terminal_statuses() {
        val plan = program(AutoregulationMode.PROPOSE, "apre-partial")
        val weeks = Cal5TestSupport.weeksOf(plan)
        val sourceWeek = weeks.first()
        val proposals = ProgramAutoregulationEngine.evaluate(
            plan, sourceWeek, recordedLogs(plan, sourceWeek), recipe, WeeklyAutoregulationSignals(),
        )
        assertTrue(proposals.size >= 2)
        val pendingResult = ProgramAutoregulationEngine.apply(
            program = plan, nextWeekId = weeks[1].id, proposals = proposals, recipe = recipe,
            executedWeekIds = setOf(sourceWeek.id), metadata = CatalogCompositionTestSupport.metadata, nowMs = 200L,
        )
        assertFalse(pendingResult.applied)
        val pending = pendingResult.program.runState!!.pendingAction!!
        assertEquals(proposals.size, pending.proposals.size)
        assertTrue(pending.message.contains("APRE"))
        assertTrue(pendingResult.program.exerciseLoadReferences.isEmpty())

        val accepted = proposals.first()
        val remaining = proposals.drop(1)
        val afterAccept = ProgramAutoregulationEngine.resolvePending(
            pendingResult.program, accept = true, only = accepted,
            executedWeekIds = setOf(sourceWeek.id), nowMs = 201L,
        )
        assertEquals(remaining, afterAccept.runState!!.pendingAction!!.proposals)
        assertEquals(remaining.joinToString(" · ") { it.explanation }, afterAccept.runState!!.pendingAction!!.message)
        assertTrue(afterAccept.runState!!.autoregulationAudit.any {
            it.resolution == PendingActionResolutionStatus.APPLIED
        })

        val rejected = remaining.first()
        val afterReject = ProgramAutoregulationEngine.resolvePending(
            afterAccept, accept = false, only = rejected, nowMs = 202L,
        )
        val stillPending = remaining.drop(1)
        assertEquals(stillPending, afterReject.runState!!.pendingAction?.proposals.orEmpty())
        if (stillPending.isNotEmpty()) assertEquals(
            stillPending.joinToString(" · ") { it.explanation }, afterReject.runState!!.pendingAction!!.message,
        )
        assertTrue(afterReject.runState!!.autoregulationAudit.any {
            it.resolution == PendingActionResolutionStatus.REJECTED
        })

        val expired = ProgramAutoregulationEngine.expirePending(
            pendingResult.program, "Caducada por avance", nowMs = 203L,
        )
        assertNull(expired.runState!!.pendingAction)
        assertTrue(expired.runState!!.autoregulationAudit.any {
            it.resolution == PendingActionResolutionStatus.EXPIRED
        })
    }

    @Test
    fun correction_reversion_appends_latest_revision_and_retry_is_idempotent() {
        val base = program(AutoregulationMode.AUTO, "apre-correction").copy(
            sourceRecipe = null, sourceProtocolId = null,
        )
        val weeks = Cal5TestSupport.weeksOf(base)
        val sourceWeek = weeks.first()
        val targetWeekId = weeks[1].id
        val squat = Cal5TestSupport.exerciseOf(sourceWeek.sessions.first(), CatalogIds.SQ_LOW)
        fun proposal(load: Double, reps: Int) = ProgramAutoregulationEngine.evaluateApreXrmOnly(
            base, sourceWeek, recordedLogs(base, sourceWeek, squat.id, load, reps),
        ).single()

        val first = proposal(155.0, 17)
        val corrected = proposal(155.0, 10)
        val reverted = proposal(155.0, 17)
        var working = ProgramAutoregulationEngine.apply(
            base, targetWeekId, listOf(first), recipe = null, executedWeekIds = setOf(sourceWeek.id), nowMs = 300L,
        ).program
        working = ProgramAutoregulationEngine.apply(
            working, targetWeekId, listOf(corrected), recipe = null, executedWeekIds = setOf(sourceWeek.id), nowMs = 301L,
        ).program
        working = ProgramAutoregulationEngine.apply(
            working, targetWeekId, listOf(reverted), recipe = null, executedWeekIds = setOf(sourceWeek.id), nowMs = 302L,
        ).program
        val history = xrmHistory(working, squat.id, first)
        assertEquals(listOf(0, 1, 2), history.map { it.metadata!!.revision })
        assertEquals(162.5, history.last().capturedLoadKg!!, 1e-6)
        val auditBeforeRetry = working.runState!!.autoregulationAudit
        val retry = ProgramAutoregulationEngine.apply(
            working, targetWeekId, listOf(reverted), recipe = null, executedWeekIds = setOf(sourceWeek.id), nowMs = 303L,
        )
        assertEquals(working, retry.program)
        assertEquals(auditBeforeRetry, retry.program.runState!!.autoregulationAudit)
        assertEquals(3, xrmHistory(retry.program, squat.id, reverted).size)
    }

    @Test
    fun marked_apre_sets_and_stale_signals_never_fall_through_to_tm_in_a_mixed_recipe() {
        val plan = program(AutoregulationMode.AUTO, "apre-mixed")
        val week = Cal5TestSupport.weeksOf(plan).first()
        val squat = Cal5TestSupport.exerciseOf(week.sessions.first(), CatalogIds.SQ_LOW)
        val logs = recordedLogs(plan, week)
        val genuine = ProgramAutoregulationEngine.evaluateApreXrmOnly(plan, week, logs)
            .single { it.apreXrm!!.sourceExerciseId == squat.id }
        val otherRule = recipe.copy(progression = ProgressionRule.AmrapDrivenTm())
        val staleSignal = AmrapHit(
            liftSlot = LiftSlot.SQUAT, percent = 90.0, prescribedReps = 10,
            actualReps = 30, weightKg = 200.0, configurationId = CatalogIds.SQ_LOW,
        )
        val signals = WeeklyAutoregulationSignals(amrapHits = listOf(
            staleSignal, staleSignal.copy(apreXrmCapture = genuine.apreXrm, sourceExerciseId = squat.id),
        ))
        val evaluated = ProgramAutoregulationEngine.evaluate(plan, week, logs, otherRule, signals)
        assertEquals(3, evaluated.size)
        assertTrue(evaluated.all { it.kind == AutoregulationProposalKind.ADJUST_XRM })
        assertTrue(evaluated.none { it.kind == AutoregulationProposalKind.ADJUST_TM })

        val onlyS3 = ProgramAutoregulationEngine.evaluate(
            plan, week, recordedLogs(plan, week, set3Only = true), otherRule, signals,
        )
        assertTrue("la S3 mantiene condición, no es XRM ni TM", onlyS3.isEmpty())
    }

    @Test
    fun auto_applies_xrm_and_keeps_mixed_tm_proposal_pending_without_mutating_profile() {
        val plan = program(AutoregulationMode.AUTO, "apre-mixed-auto")
        val weeks = Cal5TestSupport.weeksOf(plan)
        val sourceWeek = weeks.first()
        val squat = Cal5TestSupport.exerciseOf(sourceWeek.sessions.first(), CatalogIds.SQ_LOW)
        val xrm = ProgramAutoregulationEngine.evaluateApreXrmOnly(
            plan.copy(sourceRecipe = null, sourceProtocolId = null),
            sourceWeek, recordedLogs(plan, sourceWeek, squat.id),
        ).single()
        val tm = AutoregulationProposal(
            kind = AutoregulationProposalKind.ADJUST_TM, liftSlot = LiftSlot.SQUAT.name,
            kgDelta = 2.5, explanation = "Ajustar TM de sentadilla",
        )
        val result = ProgramAutoregulationEngine.apply(
            program = plan, nextWeekId = weeks[1].id, proposals = listOf(xrm, tm), recipe = null,
            executedWeekIds = setOf(sourceWeek.id), nowMs = 400L,
        )
        assertTrue(result.applied)
        assertEquals(plan.powerliftingProfile, result.program.powerliftingProfile)
        assertEquals(listOf(tm), result.program.runState!!.pendingAction!!.proposals)
        assertEquals(tm.explanation, result.program.runState!!.pendingAction!!.message)
        assertTrue(result.program.exerciseLoadReferences.isNotEmpty())
    }
}
