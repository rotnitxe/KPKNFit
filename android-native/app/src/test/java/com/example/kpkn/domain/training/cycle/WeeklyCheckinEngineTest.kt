package com.example.kpkn.domain.training.cycle

import com.example.kpkn.data.models.*
import com.example.kpkn.data.protocols.*
import com.example.kpkn.domain.training.*
import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.Test

class WeeklyCheckinEngineTest {
    @Test fun explicit_deficit_context_reaches_the_weekly_proposal_without_changing_the_source_recipe() {
        val p = pending(program())
        val result = ProgramProgressEngine.applyWeeklyCheckin(p,answer(p),metadata,deficitMode = true)
        val accessory = weeks(result).first().sessions.single().allExercises().first { it.slotRole == SlotRole.T3_ACCESSORY }
        assertTrue(accessory.sets.all { it.targetRIR == 2 })
        assertEquals(p.sourceRecipe,result.sourceRecipe)
        assertEquals(1,result.weeklyCheckins.size)
        assertEquals(weeks(p)[1],weeks(result)[1])
    }
    companion object {
        @JvmStatic @BeforeClass fun install() = CatalogCompositionTestSupport.install()
    }
    private val metadata get() = CatalogCompositionTestSupport.metadata
    private fun weeks(program: Program) = program.macrocycles.flatMap { it.blocks }.flatMap { it.mesocycles }.flatMap { it.weeks }
    private fun program(days: Int = 1, deloadLast: Boolean = false): Program {
        val recipe = TrainingPlanRecipe("checkin-test", (1..2).map { number ->
            WeekRecipe(number, 0, kind = if (deloadLast && number == 2) WeekExecutionKind.DELOAD else WeekExecutionKind.TRAINING, days = (1..days).map { d ->
                DayRecipe("Día $d", id = "day-$d", weekday = d * 2 - 1, slots = listOf(
                    SlotRecipe("sq", SlotRole.T1_MAIN, LiftRef(CatalogIds.SQ_LOW, LiftSlot.SQUAT), List(2) { SetRecipe(reps = 5, rir = 2) }, 180),
                    SlotRecipe("bp", SlotRole.T3_ACCESSORY, LiftRef(CatalogIds.BP), List(3) { SetRecipe(reps = 10, rir = 2) }, 90),
                ))
            })
        })
        var n = 0
        val p = PlanMaterializer.materialize(Program("checkin", "Plan", structure = ProgramStructure.SIMPLE), recipe,
            metadata, object : IdProvider { override fun newId() = "id-${++n}" }, strict = false)
        val first = weeks(p).first()
        return p.copy(runState = ProgramRunState("run", weekId = first.id,
            weekInstanceId = ProgramProgressEngine.instanceIdFor(1, first.id)))
    }
    private fun pending(program: Program): Program {
        val run = program.runState!!
        return program.copy(pendingWeeklyCheckin = WeeklyCheckinRequest(run.weekId!!, run.cycleNumber,
            listOf("CHEST"), programRunId = run.runId))
    }
    private fun answer(program: Program, skipped: Boolean = false): WeeklyCheckin =
        WeeklyCheckin(program.runState!!.weekId!!, programRunId = program.runState.runId,
            feedbackWeekId = program.pendingWeeklyCheckin?.feedbackWeekId,
            feedbackCycleNumber = program.pendingWeeklyCheckin?.feedbackCycleNumber,
            muscles = if (skipped) emptyList() else listOf(MuscleCheckin("CHEST", 1, 1)), skipped = skipped)
    private fun workCount(session: Session) = session.allExercises().filter { it.slotRole == SlotRole.T3_ACCESSORY }
        .sumOf { exercise -> exercise.sets.size }

    @Test fun actual_week_completion_requests_the_next_week_but_not_for_author_or_disabled_setting() {
        fun advance(p: Program, enabled: Boolean = true): Program {
            val w = weeks(p).first()
            val s = w.sessions.single()
            val log = WorkoutLog("log", p.id, s.id, s.name, "2026-01-01T10:00:00Z", 40,
                weekId = w.id, weekInstanceId = p.runState!!.weekInstanceId, cycleNumber = 1, programRunId = "run")
            return ProgramProgressEngine.advanceAfterSessionComplete(p, null, s, p.runState.weekInstanceId!!,
                listOf(log), compositionMetadata = metadata,
                weeklySignals = WeeklyAutoregulationSignals(settings = Settings(algorithmSettings = AlgorithmSettings(weeklyCheckinEnabled = enabled)))).program
        }
        val p = program()
        val result = advance(p)
        assertEquals(weeks(p)[1].id, result.pendingWeeklyCheckin?.weekId)
        assertEquals("run", result.pendingWeeklyCheckin?.programRunId)
        assertEquals(weeks(p).first().id, result.pendingWeeklyCheckin?.feedbackWeekId)
        assertEquals(1, result.pendingWeeklyCheckin?.feedbackCycleNumber)
        assertNull(advance(p.copy(sourceProtocolId = "author")).pendingWeeklyCheckin)
        assertNull(advance(p, enabled = false).pendingWeeklyCheckin)
        val deload = advance(program(deloadLast = true))
        assertNotNull("La descarga puede recoger la respuesta de la última semana de entrenamiento", deload.pendingWeeklyCheckin)
        val answered = ProgramProgressEngine.applyWeeklyCheckin(deload, answer(deload), metadata)
        assertEquals(deload.macrocycles, answered.macrocycles)
        assertTrue(answered.effectiveWeekRecipes.isEmpty())
        assertEquals(1, answered.weeklyCheckins.size)
    }
    @Test fun confirmed_answer_changes_one_occurrence_and_preserves_the_source_recipe() {
        val p = pending(program())
        val result = ProgramProgressEngine.applyWeeklyCheckin(p, answer(p), metadata, nowMs = 123L)
        assertNull(result.pendingWeeklyCheckin)
        assertEquals(1, result.weeklyCheckins.size)
        assertEquals(p.sourceRecipe, result.sourceRecipe)
        assertEquals("WEEKLY_CHECKIN", result.effectiveWeekRecipes.single().appliedProposals.single().kind)
        assertEquals(workCount(weeks(p).first().sessions.single()) + 1, workCount(weeks(result).first().sessions.single()))
        assertEquals(weeks(p)[1], weeks(result)[1])
        assertEquals("Ajuste semanal confirmado", result.programDecisions.last().title)
        // Responder otra vez ya no cambia la receta ni las respuestas, pero queda dicho que no se aplicó.
        val again = ProgramProgressEngine.applyWeeklyCheckin(result, answer(p), metadata)
        assertEquals(result.effectiveWeekRecipes, again.effectiveWeekRecipes)
        assertEquals(result.weeklyCheckins, again.weeklyCheckins)
        assertEquals(result.macrocycles, again.macrocycles)
        assertEquals(UnappliedResponse.TITLE, again.programDecisions.last().title)
        assertEquals("WEEKLY_CHECKIN", again.programDecisions.last().source)
    }
    @Test fun skipping_is_an_original_answer_with_no_effective_recipe() {
        val p = pending(program())
        val result = ProgramProgressEngine.applyWeeklyCheckin(p, answer(p, skipped = true), metadata)
        assertTrue(result.weeklyCheckins.single().skipped)
        assertTrue(result.effectiveWeekRecipes.isEmpty())
        assertEquals(p.macrocycles, result.macrocycles)
        assertEquals("Sin cambios esta semana", result.programDecisions.single().title)
    }
    @Test fun stale_runs_cycles_invalid_answers_and_author_recipes_cannot_change_a_plan() {
        val p = pending(program())
        listOf(answer(p).copy(programRunId = "previous"), answer(p).copy(cycleNumber = 2),
            answer(p).copy(muscles = listOf(MuscleCheckin("CHEST", 8, 1)))).forEach {
            val result = ProgramProgressEngine.applyWeeklyCheckin(p, it, metadata)
            // No cambia el plan ni se pierde la petición vigente, pero el motivo queda registrado y visible.
            assertEquals(p.macrocycles, result.macrocycles)
            assertEquals(p.effectiveWeekRecipes, result.effectiveWeekRecipes)
            assertEquals(p.pendingWeeklyCheckin, result.pendingWeeklyCheckin)
            assertTrue(result.weeklyCheckins.isEmpty())
            assertEquals(p.programDecisions.size + 1, result.programDecisions.size)
            assertEquals(UnappliedResponse.TITLE, result.programDecisions.last().title)
            assertTrue(UnappliedResponse.reason(result.programDecisions.last())!!.isNotBlank())
            assertEquals(UnappliedResponse.reason(result.programDecisions.last()), UnappliedResponse.newReason(p, result))
        }
        val author = p.copy(sourceRecipe = p.sourceRecipe!!.copy(compositionProfile = RecipeCompositionProfile.AUTHORED_EXACT))
        assertSame(author, ProgramProgressEngine.applyWeeklyCheckin(author, answer(author), metadata))
        val original = p.copy(sourceRecipe = p.sourceRecipe!!.copy(provenance = PlanProvenance(category = PlanProvenanceClass.ORIGINAL)))
        assertSame(original, ProgramProgressEngine.applyWeeklyCheckin(original, answer(original), metadata))
    }
    @Test fun completed_sessions_remain_identical_and_only_a_future_accessory_is_adjusted() {
        val p = pending(program(days = 2))
        val old = weeks(p).first()
        val result = ProgramProgressEngine.applyWeeklyCheckin(p, answer(p), metadata,
            executedSessionIds = setOf(old.sessions.first().id))
        val updated = weeks(result).first()
        assertEquals(old.sessions.first(), updated.sessions.first())
        assertEquals(workCount(old.sessions.last()) + 1, workCount(updated.sessions.last()))
        assertEquals(old.sessions.map { it.id }, updated.sessions.map { it.id })
    }
    @Test fun a_fully_executed_week_does_not_record_a_fictitious_adjustment() {
        val p = pending(program())
        val result = ProgramProgressEngine.applyWeeklyCheckin(p, answer(p), metadata,
            executedSessionIds = weeks(p).first().sessions.map { it.id }.toSet())
        assertEquals(p.macrocycles, result.macrocycles)
        assertTrue(result.effectiveWeekRecipes.isEmpty())
        assertEquals("Sin cambios esta semana", result.programDecisions.single().title)
    }

    @Test fun a_manual_session_override_is_not_claimed_as_an_applied_recipe_change() {
        val original = pending(program())
        val week = weeks(original).first()
        val p = original.copy(manualSessionOverrides = listOf(ManualSessionOverride(week.sessions.single().id, weekId = week.id)))
        val result = ProgramProgressEngine.applyWeeklyCheckin(p, answer(p), metadata)
        assertEquals(p.macrocycles, result.macrocycles)
        assertEquals(p.manualSessionOverrides, result.manualSessionOverrides)
        assertTrue(result.effectiveWeekRecipes.isEmpty())
    }
    @Test fun only_explicit_planning_targets_bound_the_confirmed_proposal() {
        val original = pending(program())
        val p = original.copy(planningVolumeTargets = listOf(
            com.example.kpkn.domain.training.calibration.PlanningVolumeTarget(com.example.kpkn.data.programs.KpknMuscleGroup.CHEST, 2, 3)),
            volumeRecommendations = listOf(VolumeRecommendation("Pectorales", 0, 80, 100)))
        val result = ProgramProgressEngine.applyWeeklyCheckin(p, answer(p), metadata)
        assertEquals(workCount(weeks(p).first().sessions.single()), workCount(weeks(result).first().sessions.single()))
        assertTrue(result.programDecisions.last().detail.contains("tope"))
        assertEquals(p.planningVolumeTargets, result.planningVolumeTargets)
        assertEquals(p.volumeRecommendations, result.volumeRecommendations)
        val invalid = p.copy(planningVolumeTargets = p.planningVolumeTargets + p.planningVolumeTargets)
        val rejected = ProgramProgressEngine.applyWeeklyCheckin(invalid, answer(invalid), metadata)
        assertEquals(invalid.macrocycles, rejected.macrocycles)
        assertTrue(rejected.weeklyCheckins.isEmpty())
        assertEquals(UnappliedResponse.TITLE, rejected.programDecisions.last().title)
    }

    @Test fun an_answer_without_a_pending_request_is_reported_and_a_matching_dead_request_is_cleared() {
        val p = program()
        val noRequest = ProgramProgressEngine.applyWeeklyCheckin(p, answer(pending(p)), metadata, nowMs = 5L)
        assertEquals(p.macrocycles, noRequest.macrocycles)
        assertEquals(UnappliedResponse.TITLE, noRequest.programDecisions.single().title)
        assertTrue(noRequest.programDecisions.single().detail.contains("pendiente"))
        // Petición de una semana que ya no existe en la receta: la respuesta no tiene salida, la petición se limpia.
        val base = pending(p)
        val gone = base.copy(pendingWeeklyCheckin = base.pendingWeeklyCheckin!!.copy(weekId = "gone"),
            runState = base.runState!!.copy(weekId = "gone"))
        val lost = ProgramProgressEngine.applyWeeklyCheckin(gone, answer(gone).copy(weekId = "gone"), metadata)
        assertNull(lost.pendingWeeklyCheckin)
        assertEquals(UnappliedResponse.TITLE, lost.programDecisions.last().title)
        assertTrue(lost.weeklyCheckins.isEmpty())
    }
}
