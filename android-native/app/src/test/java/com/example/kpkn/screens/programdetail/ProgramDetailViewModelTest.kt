package com.example.kpkn.screens.programdetail

import android.content.Context
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.example.kpkn.data.db.KpknDatabase
import com.example.kpkn.data.db.toEntity
import com.example.kpkn.data.db.toCompetitionRecord
import com.example.kpkn.data.models.*
import com.example.kpkn.data.protocols.AutoregulationHook
import com.example.kpkn.data.protocols.AutoregulationHookKind
import com.example.kpkn.data.protocols.CatalogIds
import com.example.kpkn.data.protocols.DayRecipe
import com.example.kpkn.data.protocols.IncrementScope
import com.example.kpkn.data.protocols.LiftSlot
import com.example.kpkn.data.protocols.LoadBasis
import com.example.kpkn.data.protocols.PROTOCOL_LIBRARY
import com.example.kpkn.data.protocols.ProgressionRule
import com.example.kpkn.data.protocols.SetRecipe
import com.example.kpkn.data.protocols.SlotRole
import com.example.kpkn.data.protocols.TrainingPlanRecipe
import com.example.kpkn.data.protocols.day
import com.example.kpkn.data.protocols.percentSets
import com.example.kpkn.data.protocols.slot
import com.example.kpkn.data.protocols.weekRecipe
import com.example.kpkn.data.repository.CompetitionRepository
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.domain.training.CatalogCompositionTestSupport
import com.example.kpkn.domain.training.CompositionMetadataHolder
import com.example.kpkn.domain.training.IdProvider
import com.example.kpkn.domain.training.LoopEngine
import com.example.kpkn.domain.training.PlanMaterializer
import com.example.kpkn.domain.training.ProgramCalendarEngine
import com.example.kpkn.domain.training.ProgramProgressEngine
import com.example.kpkn.domain.training.TrainingMaxResolver
import com.example.kpkn.data.preferences.programSnapshotStore
import com.example.kpkn.domain.training.BlockTransitionEngine
import com.example.kpkn.testsupport.RoomTestRule
import com.example.kpkn.ui.components.SnackbarType
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class ProgramDetailViewModelTest {

    private suspend fun completedMeetProgram(source: Program? = null): Pair<Program,CompetitionRecord> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val competitions = CompetitionRepository.initForTests(context,repository.databaseForTests())
        withTimeout(10_000) { competitions.isReady.first { it } }
        val id = source?.id ?: nextId()
        val p = (source ?: makeProgram(id)).copy(mode = ProgramMode.POWERLIFTING,
            powerliftingProfile = source?.powerliftingProfile ?: PowerliftingProfile(squat1RM = 190.0,bench1RM = 125.0,deadlift1RM = 220.0),
            keyDates = listOf(ProgramKeyDate("meet-date-$id","Meet",KeyDateType.COMPETITION,LocalDate.now().toString(),eventDate = LocalDate.now().toString())))
        repository.addProgram(p)
        repository.flushPendingWrites()
        val linked = withTimeout(10_000) { competitions.records.first { records -> records.any { repository.isCompetitionLinked(p,it) } } }
            .first { repository.isCompetitionLinked(p,it) }
        val completed = linked.copy(status = CompetitionRecordStatus.COMPLETED,sportType = CompetitionTemplateType.POWERLIFTING,
            technicalBlocks = listOf(CompetitionMovementType.SQUAT to 200.0,CompetitionMovementType.BENCH to 120.0,CompetitionMovementType.DEADLIFT to 230.0).map { (movement,kg) ->
                CompetitionTechnicalBlock(movement.name,movement.name,movementType = movement,attempts = listOf(
                    CompetitionAttempt("good-${movement.name}",1,weightKg = kg,resultType = CompetitionAttemptResult.GOOD_LIFT),
                    CompetitionAttempt("bad-${movement.name}",2,weightKg = kg + 50,resultType = CompetitionAttemptResult.NO_LIFT)))
            })
        competitions.upsertNow(completed)
        return repository.getProgramById(p.id)!! to competitions.getById(linked.id)!!
    }

    @Test fun meet_result_requires_confirmation_and_persists_profile_and_applied_marker_together() = runBlocking {
        val (p,record) = completedMeetProgram()
        val competitions = CompetitionRepository.getInstance()
        val vm = vmFor(p.id)
        assertNotNull(withTimeout(10_000) { vm.competitionState.first { it?.resultSummary != null } })
        vm.reviewMeetResult(record.id)
        val preview = withTimeout(10_000) { vm.meetResultPreview.first { it != null } }!!
        assertEquals(190.0,repository.getProgramById(p.id)!!.powerliftingProfile!!.squat1RM!!,0.0)
        assertNull(competitions.getById(record.id)!!.meetResultAppliedAtMs)
        vm.confirmMeetResult()
        val saved = withTimeout(10_000) { repository.programs.first { plans -> plans.any { it.id == p.id && it.powerliftingProfile?.squat1RM == 200.0 } } }.first { it.id == p.id }
        assertEquals(125.0,saved.powerliftingProfile!!.bench1RM!!,0.0)
        assertEquals(230.0,saved.powerliftingProfile.deadlift1RM!!,0.0)
        assertNotNull(competitions.getById(record.id)!!.meetResultAppliedAtMs)
        val stored = repository.databaseForTests().competitionRecordDao().getById(record.id)!!.toCompetitionRecord()
        assertNotNull(stored.meetResultAppliedAtMs)
        assertEquals(record.technicalBlocks,stored.technicalBlocks)
        assertEquals(saved.powerliftingProfile,repository.readPersistedProgramForEditorRecovery(p.id)!!.powerliftingProfile)
        assertTrue(repository.getLogsForProgram(p.id).isEmpty())
        assertFalse(repository.applyMeetResult(p.id,preview))
    }

    @Test fun meet_result_storage_failure_rolls_back_both_entities_and_preserves_the_original_result() = runBlocking {
        val (p,record) = completedMeetProgram()
        val preview = repository.previewMeetResult(p.id,record.id)!!
        repository.meetAfterProgramWriteForTests = { throw IllegalStateException("storage unavailable after program row") }
        try { assertTrue(runCatching { repository.applyMeetResult(p.id,preview) }.isFailure) }
        finally { repository.meetAfterProgramWriteForTests = null }
        assertEquals(p.powerliftingProfile,repository.readPersistedProgramForEditorRecovery(p.id)!!.powerliftingProfile)
        assertEquals(p.powerliftingProfile,repository.getProgramById(p.id)!!.powerliftingProfile)
        val stored = repository.databaseForTests().competitionRecordDao().getById(record.id)!!.toCompetitionRecord()
        assertNull(stored.meetResultAppliedAtMs)
        assertEquals(record.technicalBlocks,stored.technicalBlocks)
        assertEquals(record,CompetitionRepository.getInstance().getById(record.id))
    }

    @Test fun meet_result_rejects_a_changed_record_program_or_started_session_and_unrelated_competition() = runBlocking {
        val (p,record) = completedMeetProgram()
        val competitions = CompetitionRepository.getInstance()
        val first = repository.previewMeetResult(p.id,record.id)!!
        competitions.upsertNow(record.copy(notes = "Edición posterior"))
        assertFalse(repository.applyMeetResult(p.id,first))
        val second = repository.previewMeetResult(p.id,record.id)!!
        assertTrue(repository.mutateProgramNow(p.id) { it.copy(name = "Programa editado") })
        assertFalse(repository.applyMeetResult(p.id,second))
        val third = repository.previewMeetResult(p.id,record.id)!!
        repository.startWorkout(OngoingWorkoutState(programId = p.id,session = weeksOf(repository.getProgramById(p.id)!!).first().sessions.first(),startTime = 99))
        assertFalse(repository.applyMeetResult(p.id,third))
        competitions.upsertNow(record.copy(id = "unrelated-${record.id}",plannedProgramId = "another-program"))
        assertNull(repository.previewMeetResult(p.id,"unrelated-${record.id}"))
        assertEquals(p.powerliftingProfile,repository.getProgramById(p.id)!!.powerliftingProfile)
        assertEquals("Programa editado",repository.getProgramById(p.id)!!.name)
        competitions.upsertNow(competitions.getById(record.id)!!.copy(sportType = CompetitionTemplateType.WEIGHTLIFTING))
        assertNull(repository.previewMeetResult(p.id,record.id))
    }

    @Test fun a_fresh_meet_preview_preserves_an_ongoing_session_and_the_recipe_source() = runBlocking {
        val source = com.example.kpkn.domain.training.block.BlockReviewTestFixture.program().copy(id = nextId(),powerliftingProfile = PowerliftingProfile(bench1RM = 100.0))
        val (p,record) = completedMeetProgram(source)
        repository.startWorkout(OngoingWorkoutState(programId = p.id,session = weeksOf(p).first().sessions.first(),startTime = 55))
        val ongoing = repository.ongoingWorkout.value
        val current = repository.getProgramById(p.id)!!
        val preview = repository.previewMeetResult(p.id,record.id)!!
        assertEquals(weeksOf(current).first().sessions.first(),weeksOf(preview.result).first().sessions.first())
        assertEquals(current.sourceRecipe,preview.result.sourceRecipe)
        assertTrue(repository.applyMeetResult(p.id,preview))
        assertEquals(ongoing,repository.ongoingWorkout.value)
        assertEquals(120.0,repository.getProgramById(p.id)!!.powerliftingProfile!!.bench1RM!!,0.0)
    }

    @Test fun planned_attempts_are_durable_editable_and_guarded_against_a_stale_competition() = runBlocking {
        val (p,completed) = completedMeetProgram()
        val competitions = CompetitionRepository.getInstance()
        competitions.upsertNow(completed.copy(status = CompetitionRecordStatus.PLANNED,technicalBlocks = emptyList()))
        val record = competitions.getById(completed.id)!!
        val attempts = com.example.kpkn.domain.competitions.AttemptPlanner.plan(CompetitionMovementType.SQUAT,200.0)
        val vm = vmFor(p.id)
        vm.saveCompetitionPlan(record,attempts,"83 kg",83.0)
        withTimeout(10_000) { vm.competitionPlanSavedRecordId.first { it == record.id } }
        val saved = repository.databaseForTests().competitionRecordDao().getById(record.id)!!.toCompetitionRecord()
        assertEquals(attempts,saved.plannedAttempts)
        assertEquals(83.0,saved.targetBodyweightKg!!,0.0)
        assertEquals(CompetitionRecordStatus.PLANNED,saved.status)
        assertTrue(saved.technicalBlocks.isEmpty())
        assertNull(saved.meetResultAppliedAtMs)
        assertFalse(competitions.savePlannedAttempts(record.id,attempts,"otro",82.0,record))
        assertEquals("83 kg",competitions.getById(record.id)!!.targetWeightClass)
    }

    private suspend fun seedSeasonNutrition(calories: Int = 2000): NutritionPlan {
        val plan = NutritionPlan(id = "season-${nextId()}",name = "Nutrición",direction = PlanDirection.DEFICIT,calorieTarget = calories,
            calculationSnapshot = NutritionPlanCalculationSnapshot("test","declared EER",result = mapOf("eerKcal" to "2400"),calculatedAt = "2026-10-25T10:00:00Z"))
        val dao = repository.databaseForTests().nutritionDao()
        dao.upsertPlan(plan.toEntity())
        dao.upsertActiveState(com.example.kpkn.data.db.NutritionActiveStateEntity(activePlanId = plan.id))
        return plan
    }

    @Test fun season_context_refreshes_from_committed_nutrition_without_mutating_training_or_accounting() = runBlocking {
        val id = nextId()
        val p = makeProgram(id).copy(runState = ProgramRunState("run-$id",weekId = "${id}_w1"))
        repository.addProgram(p)
        repository.flushPendingWrites()
        val plan = seedSeasonNutrition()
        val vm = vmFor(id)
        val first = withTimeout(10_000) { vm.seasonState.first { it?.input?.calorieTarget == 2000 } }!!
        assertEquals(2400,first.input.maintenanceKcal)
        assertTrue(first.advice.any { it.code == com.example.kpkn.domain.season.SeasonAdviceCode.DEFICIT_CAPS_VOLUME })
        repository.databaseForTests().nutritionDao().upsertPlan(plan.copy(calorieTarget = 2200).toEntity())
        val changed = withTimeout(10_000) { vm.seasonState.first { it?.input?.calorieTarget == 2200 } }!!
        assertFalse(changed.advice.any { it.code == com.example.kpkn.domain.season.SeasonAdviceCode.DEFICIT_CAPS_VOLUME })
        assertEquals(p.macrocycles,repository.getProgramById(id)!!.macrocycles)
        assertTrue(repository.getLogsForProgram(id).isEmpty())
        assertEquals(plan.id,repository.databaseForTests().nutritionDao().getActiveState()?.activePlanId)
    }

    @Test fun nutrition_changed_in_the_commit_lane_invalidates_a_weekly_preview_before_program_write() = runBlocking {
        val id = nextId()
        val base = materializedRestoreProgram(id)
        val week = weeksOf(base).first()
        val p = base.copy(runState = ProgramRunState("run-$id",weekId = week.id),pendingWeeklyCheckin =
            com.example.kpkn.domain.training.cycle.WeeklyCheckinRequest(week.id,programRunId = "run-$id"))
        repository.addProgram(p)
        repository.flushPendingWrites()
        val plan = seedSeasonNutrition()
        val answer = com.example.kpkn.domain.training.cycle.WeeklyCheckin(week.id,programRunId = "run-$id",globalPerformance = 2,globalSoreness = 2)
        val preview = repository.previewWeeklyCheckin(id,answer)!!
        assertTrue(com.example.kpkn.domain.season.SeasonAdvisor.deficitMode(preview.season))
        repository.durableCommitBeforeWriteForTests = {
            runBlocking { repository.databaseForTests().nutritionDao().upsertPlan(plan.copy(calorieTarget = 2200).toEntity()) }
        }
        try { assertFalse(repository.submitWeeklyCheckin(id,preview)) }
        finally { repository.durableCommitBeforeWriteForTests = null }
        assertTrue(repository.getProgramById(id)!!.weeklyCheckins.isEmpty())
        assertTrue(repository.readPersistedProgramForEditorRecovery(id)!!.weeklyCheckins.isEmpty())
        val refreshed = repository.previewWeeklyCheckin(id,answer)!!
        assertFalse(com.example.kpkn.domain.season.SeasonAdvisor.deficitMode(refreshed.season))
        assertTrue(repository.submitWeeklyCheckin(id,refreshed))
        assertEquals(2200,repository.seasonInput(id).calorieTarget)
    }

    private fun heldScheduleProgram(): Pair<Program,WorkoutLog> {
        val id = nextId()
        val base = makeProgram(id)
        val prepared = base.copy(macrocycles = base.macrocycles.map { macro -> macro.copy(blocks = macro.blocks.map { block ->
            block.copy(mesocycles = block.mesocycles.map { meso -> meso.copy(weeks = meso.weeks.map { week ->
                week.copy(sessions = week.sessions.map { session -> session.copy(exercises = listOf(Exercise("e-${session.id}","Press",
                    sets = listOf(ExerciseSet("set-${session.id}",targetReps = 5,targetRIR = 2))))) })
            }) })
        }) },runState = ProgramRunState("schedule-$id",weekId = "${id}_w2",weekInstanceId = "${id}_w2",blockId = "${id}_b1"))
        val current = weeksOf(prepared)[1]
        val log = WorkoutLog("last-$id",id,current.sessions.single().id,"Última sesión","2026-10-25T12:00:00Z",30,
            weekId = current.id,weekInstanceId = current.id,cycleNumber = 1,programRunId = prepared.runState!!.runId)
        val held = ProgramProgressEngine.advanceAfterSessionComplete(prepared,null,current.sessions.single(),current.id,
            listOf(log),compositionMetadata = CatalogCompositionTestSupport.metadata).program
        return held to log
    }

    @Test fun missed_session_preview_does_not_persist_and_confirmed_skip_releases_hold_without_fake_workouts() = runBlocking {
        val (p,log) = heldScheduleProgram()
        repository.addProgram(p)
        repository.addWorkoutLog(log)
        repository.flushPendingWrites()
        val vm = vmFor(p.id)
        val request = withTimeout(10_000) { vm.pendingMissedSessions.first { it != null } }!!
        assertEquals(2,request.sessionIds.size)
        vm.reviewScheduleContinuation(com.example.kpkn.domain.training.schedule.MissedSessionChoice.SKIP)
        val preview = withTimeout(10_000) { vm.schedulePreview.first { it != null } }!!
        assertTrue(repository.getProgramById(p.id)!!.skippedSessionKeys.isEmpty())
        assertEquals("${p.id}_w3",preview.result.runState?.weekId)
        vm.confirmScheduleContinuation()
        val saved = withTimeout(10_000) { repository.programs.first { plans -> plans.any { it.id == p.id && it.skippedSessionKeys.size == 2 } } }.first { it.id == p.id }
        assertEquals("${p.id}_w3",saved.runState?.weekId)
        assertEquals(saved.skippedSessionKeys,repository.readPersistedProgramForEditorRecovery(p.id)!!.skippedSessionKeys)
        assertEquals(listOf(log.id),repository.getLogsForProgram(p.id).map { it.id })
        assertFalse(repository.submitScheduleContinuation(p.id,preview))
    }

    @Test fun schedule_confirmation_rejects_edits_started_sessions_and_changed_original_logs() = runBlocking {
        val (p,log) = heldScheduleProgram()
        repository.addProgram(p)
        repository.addWorkoutLog(log)
        repository.flushPendingWrites()
        val choice = com.example.kpkn.domain.training.schedule.MissedSessionChoice.SKIP
        val first = repository.previewScheduleContinuation(p.id,choice,p.pendingMissedSessions)!!
        assertTrue(repository.mutateProgramNow(p.id) { it.copy(name = "Editado") })
        assertFalse(repository.submitScheduleContinuation(p.id,first))
        val second = repository.previewScheduleContinuation(p.id,choice,p.pendingMissedSessions)!!
        repository.addWorkoutLog(log.copy(notes = "Nota original posterior"))
        repository.flushPendingWrites()
        assertFalse(repository.submitScheduleContinuation(p.id,second))
        val third = repository.previewScheduleContinuation(p.id,choice,p.pendingMissedSessions)!!
        repository.startWorkout(OngoingWorkoutState(programId = p.id,session = weeksOf(p).first().sessions.first(),startTime = 22))
        val ongoing = repository.ongoingWorkout.value
        assertFalse(repository.submitScheduleContinuation(p.id,third))
        assertEquals(ongoing,repository.ongoingWorkout.value)
        assertTrue(repository.getProgramById(p.id)!!.skippedSessionKeys.isEmpty())
        assertEquals("Editado",repository.getProgramById(p.id)!!.name)
    }

    @Test fun choosing_to_do_the_earlier_week_keeps_requirements_until_real_backfilled_workouts_complete_it() = runBlocking {
        val (p,log) = heldScheduleProgram()
        repository.addProgram(p)
        repository.addWorkoutLog(log)
        repository.flushPendingWrites()
        val preview = repository.previewScheduleContinuation(p.id,com.example.kpkn.domain.training.schedule.MissedSessionChoice.DO_THIS_WEEK,p.pendingMissedSessions)!!
        assertTrue(repository.submitScheduleContinuation(p.id,preview))
        assertEquals("${p.id}_w2",repository.getProgramById(p.id)!!.runState?.weekId)
        val missing = weeksOf(p).first()
        missing.sessions.forEachIndexed { i,session ->
            repository.finalizeWorkout(WorkoutLog("backfill-${p.id}-$i",p.id,session.id,session.name,"2026-10-25T15:00:00Z",30,
                weekId = missing.id,weekInstanceId = missing.id,cycleNumber = 1,programRunId = p.runState!!.runId))
        }
        assertEquals("${p.id}_w3",repository.getProgramById(p.id)!!.runState?.weekId)
        assertTrue(repository.getProgramById(p.id)!!.skippedSessionKeys.isEmpty())
        assertEquals(3,repository.getLogsForProgram(p.id).size)
    }

    @Test fun inactivity_notice_tracks_actual_record_dates_and_disappears_after_a_current_record(): Unit = runBlocking {
        val id = nextId()
        val p = makeProgram(id).copy(runState = ProgramRunState("run-$id",weekId = "${id}_w1"))
        repository.addProgram(p)
        val old = WorkoutLog("old-$id",id,"${id}_s1","S",LocalDate.now().minusDays(21).toString(),30)
        repository.addWorkoutLog(old)
        repository.flushPendingWrites()
        val vm = vmFor(id)
        val notice = withTimeout(10_000) { vm.inactivityNotice.first { it != null } }!!
        assertEquals(21,notice.daysSinceLastLog)
        assertEquals(2,notice.level)
        assertTrue(notice.message.contains("no indica si entrenaste"))
        repository.addWorkoutLog(old.copy(id = "today-$id",actualDate = LocalDate.now().toString()))
        withTimeout(10_000) { vm.inactivityNotice.first { it == null } }
    }

    @Test fun soft_return_is_reviewed_and_cannot_rewrite_an_authored_recipe() = runBlocking {
        val p = com.example.kpkn.domain.training.block.BlockReviewTestFixture.program().copy(id = nextId())
        repository.addProgram(p)
        repository.flushPendingWrites()
        val preview = repository.previewScheduleContinuation(p.id,null)!!
        assertEquals(p.sourceRecipe,preview.result.sourceRecipe)
        assertTrue(repository.getProgramById(p.id)!!.effectiveWeekRecipes.isEmpty())
        assertTrue(preview.result.effectiveWeekRecipes.isNotEmpty())
        assertTrue(repository.submitScheduleContinuation(p.id,preview))
        assertNull(repository.previewScheduleContinuation(p.id,null))
        val authored = p.copy(id = nextId(),sourceProtocolId = "author")
        repository.addProgram(authored)
        repository.flushPendingWrites()
        assertNull(repository.previewScheduleContinuation(authored.id,null))
    }

    @Test fun exercise_swap_requires_a_review_and_unknown_equipment_confirmation_before_persisting() = runBlocking {
        val p = com.example.kpkn.domain.training.block.BlockReviewTestFixture.program().copy(id = nextId())
        repository.addProgram(p)
        repository.updateSettings { it.copy(equipmentAvailability = null) }
        repository.flushPendingWrites()
        val session = weeksOf(p).first().sessions.first()
        val exercise = session.allExercises().first()
        val vm = vmFor(p.id)
        vm.openExerciseSwap(session.id,exercise.id) { CatalogCompositionTestSupport.catalog }
        val request = withTimeout(10_000) { vm.exerciseSwapRequest.first { it != null } }!!
        assertTrue(request.requiresEquipmentConfirmation)
        val target = request.options.first()
        assertNull(repository.previewExerciseSwap(request,target.configId,com.example.kpkn.domain.training.SwapScope.THIS_SESSION,false))
        vm.previewExerciseSwap(target.configId,com.example.kpkn.domain.training.SwapScope.THIS_SESSION,true)
        withTimeout(10_000) { vm.exerciseSwapPreview.first { it != null } }
        assertEquals(exercise.catalogConfigurationId,weeksOf(repository.getProgramById(p.id)!!).first().sessions.first().allExercises().first().catalogConfigurationId)
        vm.confirmExerciseSwap()
        val saved = withTimeout(10_000) { repository.programs.first { plans ->
            plans.any { it.id == p.id && it.programDecisions.any { decision -> decision.title == "Ejercicio cambiado" } }
        } }.first { it.id == p.id }
        assertEquals(target.configId,weeksOf(saved).first().sessions.first().allExercises().first().catalogConfigurationId)
        assertEquals(p.sourceRecipe,saved.sourceRecipe)
        assertEquals(1,saved.manualSessionOverrides.size)
        assertTrue(saved.programDecisions.last().detail.contains("Material confirmado"))
        assertTrue(repository.getLogsForProgram(p.id).isEmpty())
    }

    @Test fun exercise_swap_cannot_overwrite_an_edit_or_a_session_started_after_preview() = runBlocking {
        val p = com.example.kpkn.domain.training.block.BlockReviewTestFixture.program().copy(id = nextId())
        repository.addProgram(p)
        repository.updateSettings { it.copy(equipmentAvailability = null) }
        repository.flushPendingWrites()
        val session = weeksOf(p).first().sessions.first()
        val exercise = session.allExercises().first()
        val request = repository.exerciseSwapRequest(p.id,session.id,exercise.id,CatalogCompositionTestSupport.catalog)!!
        val preview = repository.previewExerciseSwap(request,request.options.first().configId,com.example.kpkn.domain.training.SwapScope.WHOLE_PROGRAM,true)!!
        assertTrue(repository.mutateProgramNow(p.id) { it.copy(name = "Edición posterior") })
        assertFalse(repository.submitExerciseSwap(p.id,preview))
        val current = repository.exerciseSwapRequest(p.id,session.id,exercise.id,CatalogCompositionTestSupport.catalog)!!
        val nextPreview = repository.previewExerciseSwap(current,current.options.first().configId,com.example.kpkn.domain.training.SwapScope.WHOLE_PROGRAM,true)!!
        repository.startWorkout(OngoingWorkoutState(programId = p.id,session = session,startTime = 11L))
        val ongoing = repository.ongoingWorkout.value
        assertFalse(repository.submitExerciseSwap(p.id,nextPreview))
        assertEquals("Edición posterior",repository.getProgramById(p.id)!!.name)
        assertEquals(ongoing,repository.ongoingWorkout.value)
        assertTrue(repository.getProgramById(p.id)!!.manualSessionOverrides.isEmpty())
    }

    @Test fun planned_load_uses_the_next_prescribed_week_and_disappears_after_completion(): Unit = runBlocking {
        val id = nextId()
        val p = makeProgram(id).copy(runState = ProgramRunState("run-$id",weekId = "${id}_w1"))
        repository.addProgram(p)
        repository.flushPendingWrites()
        val vm = vmFor(id)
        val projection = withTimeout(10_000) { vm.plannedLoadProjection.first { it != null } }!!
        assertEquals("${id}_w2",projection.weekId)
        assertFalse(projection.nextCycleTemplate)
        assertTrue(repository.mutateProgramNow(id) { it.copy(runState = it.runState!!.copy(status = ProgramRunStatus.COMPLETED)) })
        withTimeout(10_000) { vm.plannedLoadProjection.first { it == null } }
    }

    private fun completedContinuationProgram(): Program {
        val p = com.example.kpkn.domain.training.block.BlockReviewTestFixture.program().copy(id = nextId())
        return com.example.kpkn.domain.training.block.BlockReviewLifecycle.ensureContinuation(
            p.copy(runState = p.runState!!.copy(status = ProgramRunStatus.COMPLETED)),10)
    }

    @Test fun continuation_creates_and_activates_the_reviewed_copy_only_after_confirmation() = runBlocking {
        val p = completedContinuationProgram()
        repository.addProgram(p)
        repository.flushPendingWrites()
        val vm = vmFor(p.id)
        vm.reviewContinuation(p.pendingContinuation!!,com.example.kpkn.domain.training.block.ContinuationChoice.REPEAT_WITH_NEW_MAXES)
        val preview = withTimeout(10_000) { vm.continuationPreview.first { it != null } }!!
        val id = preview.nextProgram!!.id
        assertNull(repository.getProgramById(id))
        assertNotNull(repository.getProgramById(p.id)!!.pendingContinuation)
        vm.confirmContinuation()
        withTimeout(10_000) { vm.uiState.first { it.pendingOpenProgramId == id } }
        repository.flushPendingWrites()
        assertEquals(id,repository.activeProgramState.value!!.programId)
        assertNull(repository.readPersistedProgramForEditorRecovery(p.id)!!.pendingContinuation)
        assertNotNull(repository.readPersistedProgramForEditorRecovery(id))
        assertEquals(ProgramRunStatus.COMPLETED,repository.getProgramById(p.id)!!.runState!!.status)
        assertEquals(p.macrocycles,repository.getProgramById(p.id)!!.macrocycles)
        assertTrue(repository.getLogsForProgram(id).isEmpty())
        assertFalse(repository.submitContinuation(p.id,preview))
    }

    @Test fun a_stale_continuation_cannot_create_a_copy_or_overwrite_an_edit() = runBlocking {
        val p = completedContinuationProgram()
        repository.addProgram(p)
        repository.flushPendingWrites()
        val preview = repository.previewContinuation(p.id,p.pendingContinuation!!,
            com.example.kpkn.domain.training.block.ContinuationChoice.MAINTENANCE_WEEK)!!
        assertTrue(repository.mutateProgramNow(p.id) { it.copy(name = "Nombre actualizado") })
        assertFalse(repository.submitContinuation(p.id,preview))
        assertNull(repository.getProgramById(preview.nextProgram!!.id))
        assertEquals("Nombre actualizado",repository.getProgramById(p.id)!!.name)
        assertNotNull(repository.getProgramById(p.id)!!.pendingContinuation)
    }

    @Test fun continuation_storage_failure_keeps_the_original_choice_and_creates_neither_partial_record() = runBlocking {
        val p = completedContinuationProgram()
        repository.addProgram(p)
        repository.flushPendingWrites()
        val preview = repository.previewContinuation(p.id,p.pendingContinuation!!,
            com.example.kpkn.domain.training.block.ContinuationChoice.MAINTENANCE_WEEK)!!
        repository.continuationAfterCopyWriteForTests = { throw IllegalStateException("storage unavailable after the first row") }
        try {
            assertTrue(runCatching { repository.submitContinuation(p.id,preview) }.isFailure)
        } finally { repository.continuationAfterCopyWriteForTests = null }
        assertNull(repository.readPersistedProgramForEditorRecovery(preview.nextProgram!!.id))
        assertNotNull(repository.readPersistedProgramForEditorRecovery(p.id)!!.pendingContinuation)
        assertNull(repository.getProgramById(preview.nextProgram.id))
    }

    @Test fun choosing_the_catalog_persists_the_choice_and_emits_navigation_without_a_new_program() = runBlocking {
        val p = completedContinuationProgram()
        repository.addProgram(p)
        repository.flushPendingWrites()
        val vm = vmFor(p.id)
        vm.reviewContinuation(p.pendingContinuation!!,com.example.kpkn.domain.training.block.ContinuationChoice.PICK_FROM_CATALOG)
        withTimeout(10_000) { vm.openCatalogRequested.first { it } }
        assertNull(repository.getProgramById(p.id)!!.pendingContinuation)
        assertEquals("Elegir del catálogo",repository.getProgramById(p.id)!!.programDecisions.last().title)
        assertEquals(1,repository.programs.value.size)
        vm.consumeOpenCatalogRequest()
        assertFalse(vm.openCatalogRequested.value)
    }

    @Test fun continuation_cannot_switch_programs_while_a_session_is_in_progress() = runBlocking {
        val p = completedContinuationProgram()
        repository.addProgram(p)
        repository.flushPendingWrites()
        val preview = repository.previewContinuation(p.id,p.pendingContinuation!!,
            com.example.kpkn.domain.training.block.ContinuationChoice.MAINTENANCE_WEEK)!!
        val session = weeksOf(p).first().sessions.first()
        repository.startWorkout(OngoingWorkoutState(programId = p.id,session = session,startTime = 11L))
        val ongoing = repository.ongoingWorkout.value
        assertFalse(repository.submitContinuation(p.id,preview))
        assertNull(repository.getProgramById(preview.nextProgram!!.id))
        assertEquals(ongoing,repository.ongoingWorkout.value)
        assertNotNull(repository.getProgramById(p.id)!!.pendingContinuation)
    }

    private fun pendingBlockProgram(): Program {
        val p = com.example.kpkn.domain.training.block.BlockReviewTestFixture.program().copy(id = nextId())
        val target = com.example.kpkn.domain.training.ProgramHierarchyIndex(p).orderedWeeks()[2]
        val review = com.example.kpkn.domain.training.block.BlockReviewTestFixture.review(p,
            com.example.kpkn.domain.training.block.BlockReviewStatus.DECLARED_DIFFICULTY)
        return p.copy(pendingBlockReview = com.example.kpkn.domain.training.block.BlockReviewProposal(review,
            com.example.kpkn.domain.training.block.NextBlockAdjuster.adjustmentFor(review,target.blockId)))
    }

    @Test fun block_review_preview_requires_confirmation_before_mutating_the_recipe() = runBlocking {
        val p = pendingBlockProgram()
        repository.addProgram(p)
        repository.flushPendingWrites()
        val vm = vmFor(p.id)
        val pending = withTimeout(10_000) { vm.pendingBlockReview.first { it != null } }!!
        vm.reviewBlockAdjustment(pending)
        val preview = withTimeout(10_000) { vm.blockReviewPreview.first { it != null } }!!
        assertTrue(repository.getProgramById(p.id)!!.effectiveWeekRecipes.isEmpty())
        assertNotNull(repository.getProgramById(p.id)!!.pendingBlockReview)
        assertTrue(preview.result.effectiveWeekRecipes.isNotEmpty())
        vm.confirmBlockReview()
        val saved = withTimeout(10_000) { repository.programs.first { plans ->
            plans.any { it.id == p.id && it.pendingBlockReview == null }
        } }.first { it.id == p.id }
        assertEquals(p.sourceRecipe,saved.sourceRecipe)
        assertEquals("Ajuste del bloque aplicado",saved.programDecisions.last().title)
        assertTrue(repository.getLogsForProgram(p.id).isEmpty())
    }

    @Test fun block_review_snapshot_edits_invalidate_a_pending_confirmation() = runBlocking {
        val p = pendingBlockProgram()
        repository.addProgram(p)
        repository.flushPendingWrites()
        val preview = repository.previewBlockReview(p.id,p.pendingBlockReview!!,true)!!
        assertTrue(repository.mutateProgramNow(p.id) { it.copy(name = "Edición posterior") })
        assertFalse(repository.submitBlockReview(p.id,preview))
        assertEquals("Edición posterior",repository.getProgramById(p.id)!!.name)
        assertNotNull(repository.getProgramById(p.id)!!.pendingBlockReview)
        val current = repository.previewBlockReview(p.id,p.pendingBlockReview,true)!!
        assertTrue(repository.submitBlockReview(p.id,current))
        assertFalse(repository.submitBlockReview(p.id,current))
    }

    @Test fun starting_a_session_invalidates_the_old_preview_and_a_new_one_preserves_that_session() = runBlocking {
        val p = pendingBlockProgram()
        repository.addProgram(p)
        repository.flushPendingWrites()
        val preview = repository.previewBlockReview(p.id,p.pendingBlockReview!!,true)!!
        val target = com.example.kpkn.domain.training.ProgramHierarchyIndex(preview.original).orderedWeeks()[2].week.sessions.first()
        repository.startWorkout(OngoingWorkoutState(programId = p.id,session = target,startTime = 11L))
        val ongoing = repository.ongoingWorkout.value
        val startedTarget = com.example.kpkn.domain.training.ProgramHierarchyIndex(repository.getProgramById(p.id)!!).orderedWeeks()[2].week.sessions.first()
        assertFalse(repository.submitBlockReview(p.id,preview))
        val current = repository.previewBlockReview(p.id,p.pendingBlockReview,true)!!
        val changedTarget = com.example.kpkn.domain.training.ProgramHierarchyIndex(current.result).orderedWeeks()[2].week.sessions.first()
        assertEquals(startedTarget,changedTarget)
        assertTrue(repository.submitBlockReview(p.id,current))
        assertEquals(ongoing,repository.ongoingWorkout.value)
    }

    @Test fun cycle_range_proposals_require_confirmation_and_never_rewrite_sessions() = runBlocking {
        val p = com.example.kpkn.domain.training.calibration.PlanningRangeTestSupport.program().copy(id = nextId())
        repository.addProgram(p)
        repository.flushPendingWrites()
        val vm = vmFor(p.id)
        vm.reviewPlanningRanges()
        val preview = withTimeout(10_000) { vm.planningRangePreview.first { it != null } }!!
        assertTrue(repository.getProgramById(p.id)!!.planningVolumeTargets.isEmpty())
        assertEquals(24, preview.proposals.single().proposed.maxSets)
        withTimeout(10_000) { vm.planningVolumeTargetBusy.first { !it } }
        vm.resolvePlanningRanges(accept = true)
        val saved = withTimeout(10_000) { repository.programs.first { list -> list.any { it.id == p.id && it.planningVolumeTargets.isNotEmpty() } } }.first { it.id == p.id }
        assertEquals(p.macrocycles, saved.macrocycles)
        assertEquals(p.sourceRecipe, saved.sourceRecipe)
        assertEquals("WEEKLY_FEEDBACK_CONFIRMED", saved.planningVolumeTargets.single().source)
        assertEquals(5, saved.planningVolumeTargets.single().evidenceWeeks)
        assertNull(repository.previewPlanningRanges(p.id))
        assertFalse(repository.resolvePlanningRanges(p.id, preview, accept = true))
    }

    @Test fun cycle_range_rejection_or_a_stale_preview_cannot_change_targets() = runBlocking {
        val p = com.example.kpkn.domain.training.calibration.PlanningRangeTestSupport.program().copy(id = nextId())
        repository.addProgram(p)
        repository.flushPendingWrites()
        val stale = repository.previewPlanningRanges(p.id)!!
        assertTrue(repository.mutateProgramNow(p.id) { it.copy(name = "Edición posterior") })
        assertFalse(repository.resolvePlanningRanges(p.id, stale, accept = true))
        val current = repository.previewPlanningRanges(p.id)!!
        assertTrue(repository.resolvePlanningRanges(p.id, current, accept = false))
        assertTrue(repository.getProgramById(p.id)!!.planningVolumeTargets.isEmpty())
        assertEquals("Edición posterior", repository.getProgramById(p.id)!!.name)
        assertNull(repository.previewPlanningRanges(p.id))
    }

    @Test fun choosing_a_volume_range_keeps_prescription_and_global_profile_unchanged() = runBlocking {
        val id = nextId()
        val p = materializedRestoreProgram(id)
        repository.addProgram(p)
        repository.flushPendingWrites()
        val profile = repository.settings.value.volumeCalibrationProfile
        val vm = vmFor(id)
        vm.savePlanningVolumeTarget(com.example.kpkn.domain.training.calibration.PlanningVolumeTarget(
            com.example.kpkn.data.programs.KpknMuscleGroup.CHEST, 6, 20, notes = "Mi objetivo"))
        val saved = withTimeout(10_000) { repository.programs.first { list -> list.any { it.id == id && it.planningVolumeTargets.isNotEmpty() } } }.first { it.id == id }
        assertEquals(p.macrocycles, saved.macrocycles)
        assertEquals(p.sourceRecipe, saved.sourceRecipe)
        assertEquals(p.athleteProfileScore, saved.athleteProfileScore)
        assertEquals(profile, repository.settings.value.volumeCalibrationProfile)
        assertEquals("Mi objetivo", saved.planningVolumeTargets.single().notes)
        assertEquals("MANUAL", saved.programDecisions.last().source)
        withTimeout(10_000) { vm.planningVolumeTargetBusy.first { !it } }
        vm.resetPlanningVolumeTarget(com.example.kpkn.data.programs.KpknMuscleGroup.CHEST)
        withTimeout(10_000) { repository.programs.first { list -> list.any { it.id == id && it.planningVolumeTargets.isEmpty() && it.programDecisions.size == 2 } } }
        assertEquals(p.macrocycles, repository.getProgramById(id)!!.macrocycles)
    }

    @Test fun volume_evidence_counts_the_current_run_without_inventing_trait_responses() = runBlocking {
        val id = nextId()
        val p = makeProgram(id).copy(runState = ProgramRunState("run-$id", weekId = "${id}_w1"))
        repository.addProgram(p)
        val date = LocalDate.now().toString()
        repository.addWorkoutLog(WorkoutLog("a-$id", id, "${id}_s1", "S1", date, 40, programRunId = "run-$id"))
        repository.addWorkoutLog(WorkoutLog("b-$id", id, "${id}_s2", "S2", date, 40, programRunId = "previous"))
        repository.flushPendingWrites()
        val summary = withTimeout(10_000) { vmFor(id).volumeEvidence.first { it != null } }!!
        assertEquals(1, summary.recordedSessions)
        assertNull(summary.coveredPlannedSessions)
        assertNull(repository.getProgramById(id)!!.athleteProfileScore)
    }

    @Test fun original_calibration_notes_are_saved_to_the_latest_program_and_profile() = runBlocking {
        val id = nextId()
        repository.addProgram(makeProgram(id))
        repository.flushPendingWrites()
        val result = com.example.kpkn.screens.programdetail.components.buildVolumeCalibration(
            TrainingStyle.POWERLIFTER, 1, 2, 3, 2).copy(notes = "Nota personal")
        assertTrue(repository.mutateProgramNow(id) { it.copy(name = "Edición reciente") })
        repository.updateProgram(repository.getProgramById(id)!!.copy(mode = result.mode, athleteProfileScore = result.score,
            volumeRecommendations = result.recommendations))
        vmFor(id).saveVolumeCalibrationOriginals(result)
        withTimeout(10_000) { repository.settings.first { it.volumeCalibrationProfile?.notes == "Nota personal" } }
        assertEquals("Nota personal", repository.getProgramById(id)!!.volumeCalibrationNotes)
        assertEquals("Edición reciente", repository.getProgramById(id)!!.name)
        assertEquals(result.score, repository.getProgramById(id)!!.athleteProfileScore)
        assertEquals(result.recommendations, repository.getProgramById(id)!!.volumeRecommendations)
        val profile = repository.settings.value.volumeCalibrationProfile!!
        assertEquals(CalibrationResponseState.DECLARED, profile.responses.state)
        val json = com.example.kpkn.data.db.dbJson
        assertEquals(profile, json.decodeFromString<VolumeCalibrationProfile>(json.encodeToString(profile)))
    }

    @Test fun weekly_checkin_uses_the_real_database_json_and_old_defaults() {
        val json = com.example.kpkn.data.db.dbJson
        val p = Program("json-checkin", "Plan", pendingWeeklyCheckin =
            com.example.kpkn.domain.training.cycle.WeeklyCheckinRequest("w", programRunId = "run"))
        assertEquals(p, json.decodeFromString<Program>(json.encodeToString(p)))
        val old = json.decodeFromString<Program>("""{"id":"old","name":"Plan"}""")
        assertNull(old.pendingWeeklyCheckin)
        assertTrue(old.weeklyCheckins.isEmpty())
    }

    @Test fun weekly_checkin_preview_does_not_persist_until_the_person_confirms() = runBlocking {
        val id = nextId()
        val base = materializedRestoreProgram(id)
        val week = weeksOf(base).first()
        val request = com.example.kpkn.domain.training.cycle.WeeklyCheckinRequest(week.id, programRunId = "run-$id")
        val p = base.copy(runState = ProgramRunState("run-$id", weekId = week.id), pendingWeeklyCheckin = request)
        repository.addProgram(p)
        repository.flushPendingWrites()
        val vm = vmFor(id)
        assertEquals(request, withTimeout(10_000) { vm.pendingWeeklyCheckin.first { it != null } })
        vm.previewWeeklyCheckin(com.example.kpkn.domain.training.cycle.WeeklyCheckin(week.id,
            programRunId = "run-$id", globalPerformance = 2, globalSoreness = 2))
        withTimeout(10_000) { vm.weeklyCheckinPreview.first { it != null } }
        assertTrue(repository.getProgramById(id)!!.weeklyCheckins.isEmpty())
        vm.confirmWeeklyCheckin()
        val saved = withTimeout(10_000) { repository.programs.first { plans -> plans.any { it.id == id && it.weeklyCheckins.size == 1 } } }.first { it.id == id }
        assertNull(saved.pendingWeeklyCheckin)
        assertEquals(p.macrocycles, saved.macrocycles)
        assertEquals("Sin cambios esta semana", saved.programDecisions.last().title)
    }

    @Test fun weekly_checkin_cannot_overwrite_an_edit_made_after_the_preview() = runBlocking {
        val id = nextId()
        val base = materializedRestoreProgram(id)
        val weekId = weeksOf(base).first().id
        val p = base.copy(runState = ProgramRunState("run-$id", weekId = weekId), pendingWeeklyCheckin =
            com.example.kpkn.domain.training.cycle.WeeklyCheckinRequest(weekId, programRunId = "run-$id"))
        repository.addProgram(p)
        repository.flushPendingWrites()
        val preview = repository.previewWeeklyCheckin(id, com.example.kpkn.domain.training.cycle.WeeklyCheckin(
            weekId, programRunId = "run-$id", skipped = true))!!
        assertTrue(repository.mutateProgramNow(id) { it.copy(name = "Editado después") })
        assertFalse(repository.submitWeeklyCheckin(id, preview))
        assertEquals("Editado después", repository.getProgramById(id)!!.name)
        assertTrue(repository.getProgramById(id)!!.weeklyCheckins.isEmpty())
    }

    @get:Rule
    val roomRule = RoomTestRule()

    companion object {
        @BeforeClass
        @JvmStatic
        fun installCatalogCompositionSupport() {
            CatalogCompositionTestSupport.install()
        }
    }

    private val testDispatcher: TestDispatcher = UnconfinedTestDispatcher()
    private lateinit var repository: ProgramRepository
    private val viewModelStore = ViewModelStore()
    private var nextViewModelKey = 0

    private fun nextId(): String = "prog_${System.nanoTime()}"

    /** Cada VM va al store de la prueba: [tearDown] lo limpia antes de cerrar repositorios y base. */
    private fun vmFor(id: String): ProgramDetailViewModel =
        ProgramDetailViewModel(id).also { viewModel -> viewModelStore.put("detail-${nextViewModelKey++}", viewModel) }

    @Test
    fun progress_report_reads_program_and_history() = runBlocking {
        val id = nextId()
        val p = makeProgram(id).copy(runState = ProgramRunState("run-$id", weekId = "${id}_w1", blockId = "${id}_b1"))
        repository.addProgram(p)
        repository.addWorkoutLog(WorkoutLog("log-$id", id, "${id}_s1", "S1", "2026-10-22T12:00:00Z", 60, weekId = "${id}_w1"))
        repository.flushPendingWrites()
        val report = withTimeout(10_000) { vmFor(id).progressReport.first { it != null } }!!
        assertEquals(50, report.adherencePercent)
        assertEquals(1, report.position.weekIndex)
        assertEquals(1, report.weeks.first().completedSessions)
    }

    private fun makeProgram(id: String) = Program(
        id = id,
        name = "Test $id",
        structure = ProgramStructure.COMPLEX,
        macrocycles = listOf(
            Macrocycle(
                id = "${id}_mc1", name = "Macro",
                blocks = listOf(
                    Block(
                        id = "${id}_b1", name = "Block 1",
                        mesocycles = listOf(
                            Mesocycle(
                                id = "${id}_m1", name = "Meso 1",
                                goal = MesocycleGoal.ACCUMULATION,
                                weeks = listOf(
                                    ProgramWeek(id = "${id}_w1", name = "W1", sessions = listOf(
                                        Session(id = "${id}_s1", name = "S1"),
                                        Session(id = "${id}_s2", name = "S2"),
                                    )),
                                    ProgramWeek(id = "${id}_w2", name = "W2", sessions = listOf(
                                        Session(id = "${id}_s3", name = "S3"),
                                    )),
                                ),
                            ),
                        ),
                    ),
                    Block(
                        id = "${id}_b2", name = "Block 2",
                        mesocycles = listOf(
                            Mesocycle(
                                id = "${id}_m2", name = "Meso 2",
                                goal = MesocycleGoal.INTENSIFICATION,
                                weeks = listOf(
                                    ProgramWeek(id = "${id}_w3", name = "W3", sessions = listOf(
                                        Session(id = "${id}_s4", name = "S4"),
                                    )),
                                ),
                            ),
                        ),
                    ),
                ),
            )
        ),
    )

    private fun makeSimpleProgram(id: String) = Program(
        id = id,
        name = "Simple $id",
        structure = ProgramStructure.SIMPLE,
        macrocycles = listOf(
            Macrocycle(id = "${id}_mc1", name = "M", blocks = listOf(
                Block(id = "${id}_b1", name = "B", mesocycles = listOf(
                    Mesocycle(id = "${id}_m1", name = "M", weeks = listOf(
                        ProgramWeek(id = "${id}_w1", name = "W", sessions = listOf(
                            Session(id = "${id}_s1", name = "S"),
                        )),
                    )),
                )),
            )),
        ),
    )

    private class RestoreSessionIds : IdProvider {
        private var next = 0
        override fun newId(): String = "restore-${++next}"
    }

    private fun restoreRecipe(programId: String) = TrainingPlanRecipe(
        id = "restore-recipe-$programId",
        weeks = listOf(
            weekRecipe(
                1,
                0,
                "Base",
                BlockGoal.ACCUMULATION,
                listOf(
                    DayRecipe(
                        id = "restore-target-day",
                        label = "Día receta seleccionado",
                        weekday = 1,
                        slots = listOf(
                            slot(
                                "restore-target-bench",
                                SlotRole.T1_MAIN,
                                CatalogIds.BP,
                                percentSets(150, 5 to 75.0, 5 to 75.0),
                                150,
                                LiftSlot.BENCH,
                                isCompetitionLift = true,
                            ),
                        ),
                    ),
                    DayRecipe(
                        id = "restore-sibling-day",
                        label = "Día receta vecino",
                        weekday = 3,
                        slots = listOf(
                            slot(
                                "restore-sibling-bench",
                                SlotRole.T1_MAIN,
                                CatalogIds.BP,
                                percentSets(150, 5 to 70.0, 5 to 70.0),
                                150,
                                LiftSlot.BENCH,
                                isCompetitionLift = true,
                            ),
                        ),
                    ),
                ),
            ),
        ),
        claimedDaysPerWeek = 2,
    )

    private fun materializedRestoreProgram(programId: String): Program = PlanMaterializer.materialize(
        Program(id = programId, name = "Restore $programId", structure = ProgramStructure.COMPLEX),
        restoreRecipe(programId),
        CatalogCompositionTestSupport.metadata,
        RestoreSessionIds(),
        strict = false,
    )

    private fun weeksOf(program: Program) =
        program.macrocycles.flatMap { it.blocks }.flatMap { it.mesocycles }.flatMap { it.weeks }

    private fun replaceRestoreFixtureSessions(
        program: Program,
        weekId: String,
        replacements: Map<String, Session>,
    ): Program = program.copy(
        macrocycles = program.macrocycles.map { macro ->
            macro.copy(
                blocks = macro.blocks.map { block ->
                    block.copy(
                        mesocycles = block.mesocycles.map { meso ->
                            meso.copy(
                                weeks = meso.weeks.map { week ->
                                    if (week.id != weekId) week
                                    else week.copy(sessions = week.sessions.map { replacements[it.id] ?: it })
                                },
                            )
                        },
                    )
                },
            )
        },
    )

    private fun sessionIdentity(session: Session): List<String?> = buildList<String?> {
        add(session.id)
        session.parts.forEach { add(it.id) }
        session.allExercises().forEach { exercise ->
            add(exercise.id)
            add(exercise.occurrenceId)
            exercise.sets.forEach { add(it.id) }
            exercise.warmupSets.forEach { add(it.id) }
        }
    }

    private fun sessionJson(session: Session): String = Json.encodeToString(Session.serializer(), session)

    private data class ManualRestoreFixture(
        val weekId: String,
        val plannedTarget: Session,
        val targetId: String,
        val siblingId: String,
        val siblingJson: String,
    )

    private fun manuallyEdit(session: Session, label: String): Session {
        fun editPrescription(exercise: Exercise) = exercise.copy(
            sets = exercise.sets.map { it.copy(targetReps = 99) },
        )
        return session.copy(
            name = label,
            description = "$label editada manualmente",
            exercises = session.exercises.map(::editPrescription),
            parts = session.parts.map { part -> part.copy(exercises = part.exercises.map(::editPrescription)) },
        )
    }

    private suspend fun seedManualRestoreFixture(programId: String): ManualRestoreFixture {
        val generated = materializedRestoreProgram(programId)
        repository.addProgram(generated)
        repository.flushPendingWrites()

        val persistedPlan = repository.getProgramById(programId) ?: error("programa de prueba ausente")
        assertNotNull("el fixture debe conservar la receta fuente", persistedPlan.sourceRecipe)
        val planWeek = weeksOf(persistedPlan).single()
        val plannedTarget = planWeek.sessions[0]
        val plannedSibling = planWeek.sessions[1]
        val changedSessions = replaceRestoreFixtureSessions(
            persistedPlan,
            planWeek.id,
            mapOf(
                plannedTarget.id to manuallyEdit(plannedTarget, "Sesión personalizada"),
                plannedSibling.id to manuallyEdit(plannedSibling, "Vecina personalizada"),
            ),
        )
        val withTargetOverride = PlanMaterializer.withManualSessionOverride(
            changedSessions,
            plannedTarget.id,
            planWeek.id,
            weekOccurrence = 1,
            recipeDayId = "restore-target-day",
            nowMs = 1L,
        )
        val marked = PlanMaterializer.withManualSessionOverride(
            withTargetOverride,
            plannedSibling.id,
            planWeek.id,
            weekOccurrence = 1,
            recipeDayId = "restore-sibling-day",
            nowMs = 2L,
        )
        repository.updateProgramNow(marked)

        val stored = repository.getProgramById(programId) ?: error("programa editado ausente")
        val storedWeek = weeksOf(stored).single { it.id == planWeek.id }
        val storedSibling = storedWeek.sessions.single { it.id == plannedSibling.id }
        return ManualRestoreFixture(
            weekId = storedWeek.id,
            plannedTarget = plannedTarget,
            targetId = plannedTarget.id,
            siblingId = plannedSibling.id,
            siblingJson = sessionJson(storedSibling),
        )
    }

    private fun siblingWorkoutLog(programId: String, fixture: ManualRestoreFixture, sessionId: String) = WorkoutLog(
        id = "restore-log-$programId",
        programId = programId,
        sessionId = sessionId,
        sessionName = sessionId,
        date = "2026-09-29T10:00:00Z",
        durationMinutes = 45,
        weekId = fixture.weekId,
    )

    @Before
    fun setup() = runBlocking {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        ProgramRepository.initForTests(context)
        repository = ProgramRepository.getInstance()
        withTimeout(10_000) {
            while (!repository.isReady.value) {
                delay(25)
            }
        }
        repository.resetAllStateSync()
    }

    @After
    fun tearDown() {
        // Primero los VM (cancela su viewModelScope), luego los repositorios y solo al final la base.
        viewModelStore.clear()
        CompetitionRepository.closeInstance()
        ProgramRepository.closeInstance()
        KpknDatabase.closeInstance()
        Dispatchers.resetMain()
    }

    // ─── Tab Management ───────────────────────────────────────────────────

    @Test
    fun feedback_never_rescales_authored_volume_references() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val id = nextId()
        val references = listOf(VolumeRecommendation("Pectorales", 8, 12, 18, 3))
        repository.addProgram(makeProgram(id).copy(volumeRecommendations = references))
        val vm = vmFor(id)
        val collector = launch(testDispatcher) { vm.program.collect {} }
        try {
            for ((caseIndex, values) in listOf(1 to 9, 5 to 3).withIndex()) {
                val feedbacks = (1..3).map { day ->
                    PostSessionFeedback(
                        logId = "$id-feedback-$caseIndex-$day",
                        date = "2026-10-${(caseIndex * 3 + day).toString().padStart(2, '0')}",
                        muscleFeedback = mapOf("Pectorales" to MuscleFeedbackEntry(
                            doms = values.first,
                            strengthCapacity = values.second,
                            notes = "Declaración original: no es una prescripción",
                        )),
                    )
                }
                feedbacks.forEach { feedback ->
                    KpknDatabase.getInstance(context).trainingObservationDao().preserve(
                        com.example.kpkn.data.db.TrainingObservationEntity(
                            "feedback:${feedback.logId}", "feedback", feedback.date,
                            Json.encodeToString(feedback), "explicit_self_report;original_scale",
                        ),
                    )
                }
                vm.loadFeedbacks(context)
                withTimeout(10_000) {
                    vm.feedbacks.first { rows -> feedbacks.all { original -> rows.any { it.logId == original.logId } } }
                }
                testDispatcher.scheduler.advanceUntilIdle()
                assertEquals(references, vm.program.value?.volumeRecommendations)
                assertEquals(references, repository.getProgramById(id)?.volumeRecommendations)
                assertTrue(vm.feedbacks.value.containsAll(feedbacks))
            }
        } finally {
            collector.cancel()
        }
    }

    @Test
    fun initial_state_is_semana_subtab() {
        val id = nextId()
        val vm = vmFor(id)
        assertEquals(StructureSubTab.SEMANA, vm.uiState.value.structureSubTab)
    }

    // ─── Block Selection ──────────────────────────────────────────────────

    @Test
    fun selectBlock_updates_selected_block() {
        val id = nextId()
        repository.addProgram(makeProgram(id))
        val vm = vmFor(id)

        vm.selectBlock("${id}_b2")
        assertEquals("${id}_b2", vm.uiState.value.selectedBlockId)
    }

    @Test
    fun selectWeek_updates_selected_week() {
        val id = nextId()
        repository.addProgram(makeProgram(id))
        val vm = vmFor(id)

        vm.selectWeek("${id}_w2")
        assertEquals("${id}_w2", vm.uiState.value.selectedWeekId)
    }

    // ─── Program Actions ──────────────────────────────────────────────────

    @Test
    fun startProgram_creates_active_state() {
        val id = nextId()
        repository.addProgram(makeProgram(id))
        val vm = vmFor(id)

        vm.startProgram()
        assertNotNull(repository.activeProgramState.value)
        assertEquals(id, repository.activeProgramState.value?.programId)
        assertEquals(ProgramStatus.ACTIVE, repository.activeProgramState.value?.status)
    }

    @Test
    fun pauseProgram_changes_status() {
        val id = nextId()
        repository.addProgram(makeProgram(id))
        repository.startProgram(id)
        val vm = vmFor(id)

        vm.pauseProgram()
        assertEquals(ProgramStatus.PAUSED, repository.activeProgramState.value?.status)
        assertEquals(ProgramRunStatus.PAUSED, repository.getProgramById(id)?.runState?.status)
    }

    @Test
    fun pauseProgram_survives_update_and_resume_keeps_week() {
        val id = nextId()
        repository.addProgram(makeProgram(id))
        val vm = vmFor(id)

        vm.startProgram()
        val weekId = repository.activeProgramState.value?.currentWeekId
        assertFalse(weekId.isNullOrBlank())

        vm.pauseProgram()
        assertEquals(ProgramStatus.PAUSED, repository.activeProgramState.value?.status)
        assertEquals(ProgramRunStatus.PAUSED, repository.getProgramById(id)?.runState?.status)

        vm.updateProgram(repository.getProgramById(id)!!.copy(name = "Renamed $id"))
        assertEquals(ProgramStatus.PAUSED, repository.activeProgramState.value?.status)
        assertEquals(ProgramRunStatus.PAUSED, repository.getProgramById(id)?.runState?.status)
        assertEquals(weekId, repository.activeProgramState.value?.currentWeekId)

        vm.resumeProgram()
        assertEquals(ProgramStatus.ACTIVE, repository.activeProgramState.value?.status)
        assertEquals(ProgramRunStatus.ACTIVE, repository.getProgramById(id)?.runState?.status)
        assertEquals(weekId, repository.activeProgramState.value?.currentWeekId)
    }

    @Test
    fun toggleStartPause_resumes_paused_program() = runBlocking {
        val id = nextId()
        repository.addProgram(makeProgram(id))
        val vm = vmFor(id)
        vm.startProgram()
        vm.pauseProgram()
        val pausedCollector = async { vm.isPausedProgram.first { it } }
        withTimeout(5_000) { pausedCollector.await() }
        vm.toggleStartPause()
        assertEquals(ProgramStatus.ACTIVE, repository.activeProgramState.value?.status)
        assertEquals(ProgramRunStatus.ACTIVE, repository.getProgramById(id)?.runState?.status)
    }

    // ─── Derived State ────────────────────────────────────────────────────

    @Test
    fun roadmapBlocks_computed_from_program() {
        val id = nextId()
        repository.addProgram(makeProgram(id))
        val vm = vmFor(id)

        val blocks = vm.roadmapBlocks.value
        assertEquals(2, blocks.size)
        assertEquals("${id}_b1", blocks[0].id)
        assertEquals("${id}_b2", blocks[1].id)
    }

    @Test
    fun totalWeeks_computed() = runBlocking {
        val id = nextId()
        repository.addProgram(makeProgram(id))
        val vm = vmFor(id)
        assertEquals(3, withTimeout(5_000) { vm.totalWeeks.filter { it > 0 }.first() })
    }

    @Test
    fun isSimpleProgram_false_for_multi_block() = runBlocking {
        val id = nextId()
        repository.addProgram(makeProgram(id))
        val vm = vmFor(id)
        assertFalse(withTimeout(5_000) { vm.isSimpleProgram.filter { !it }.first() })
    }

    @Test
    fun isSimpleProgram_true_for_simple() {
        val id = nextId()
        repository.addProgram(makeSimpleProgram(id))
        val vm = vmFor(id)

        assertTrue(vm.isSimpleProgram.value)
    }

    @Test
    fun deleteSession_removes_from_program() {
        val id = nextId()
        repository.addProgram(makeProgram(id))
        val vm = vmFor(id)

        vm.deleteSession("${id}_s1", macroIndex = 0, mesoIndex = 0, weekId = "${id}_w1")

        val updated = repository.getProgramById(id)!!
        val week = updated.macrocycles[0].blocks[0].mesocycles[0].weeks[0]
        assertEquals(1, week.sessions.size)
        assertEquals("${id}_s2", week.sessions[0].id)
    }

    @Test
    fun persist_strips_meet_session_and_preserves_linked_competition_record() = runBlocking {
        val id = nextId()
        val context = ApplicationProvider.getApplicationContext<Context>()
        CompetitionRepository.initForTests(context)
        val competitionRepository = CompetitionRepository.getInstance()
        withTimeout(5_000) { while (!competitionRepository.isReady.value) delay(10) }
        competitionRepository.upsertNow(
            CompetitionRecord(
                id = "${id}_record",
                title = "Meet day",
                plannedProgramId = id,
                plannedSessionId = "${id}_comp",
                plannedWeekId = "${id}_w1",
                journal = CompetitionJournal(overallFeeling = "Buena sensación"),
            )
        )

        val program = makeProgram(id)
        val competitionSession = Session(
            id = "${id}_comp",
            name = "Meet day",
            dayOfWeek = 3,
            isMeetDay = true,
            isCompetitionSession = true,
            competitionRecordId = "${id}_record",
        )
        val withCompetitionSession = program.copy(
            macrocycles = program.macrocycles.mapIndexed { mi, macro ->
                if (mi != 0) macro
                else macro.copy(blocks = macro.blocks.mapIndexed { bi, block ->
                    if (bi != 0) block
                    else block.copy(mesocycles = block.mesocycles.map { meso ->
                        meso.copy(weeks = meso.weeks.map { week ->
                            if (week.id != "${id}_w1") week
                            else week.copy(sessions = week.sessions + competitionSession)
                        })
                    })
                })
            },
        )
        repository.addProgram(withCompetitionSession)

        val updatedWeek = repository.getProgramById(id)!!.macrocycles[0].blocks[0].mesocycles[0].weeks[0]
        assertTrue(updatedWeek.sessions.none { it.id == "${id}_comp" })

        val record = competitionRepository.getById("${id}_record")
        assertNotNull("El record de competición no debería eliminarse", record)
        assertNull(record!!.plannedSessionId)
        assertEquals("Buena sensación", record.journal?.overallFeeling)
    }

    @Test
    fun addSession_appends_to_week() {
        val id = nextId()
        repository.addProgram(makeProgram(id))
        val vm = vmFor(id)

        val newSession = Session(id = "${id}_new", name = "New")
        vm.addSession(macroIndex = 0, mesoIndex = 0, weekId = "${id}_w1", session = newSession)

        val updated = repository.getProgramById(id)!!
        val week = updated.macrocycles[0].blocks[0].mesocycles[0].weeks[0]
        assertEquals(3, week.sessions.size)
        assertEquals("${id}_new", week.sessions[2].id)
    }

    @Test
    fun addWeekToSimpleProgram_appends_week_and_keeps_program_simple() {
        val id = nextId()
        repository.addProgram(makeSimpleProgram(id))
        val vm = vmFor(id)

        vm.addWeekToSimpleProgram()

        val updated = repository.getProgramById(id)!!
        val weeks = updated.macrocycles[0].blocks[0].mesocycles[0].weeks
        assertEquals(2, weeks.size)
        assertEquals("Semana 2", weeks[1].name)
        assertEquals(ProgramStructure.SIMPLE, updated.structure)
        assertEquals(weeks[1].id, vm.uiState.value.selectedWeekId)
    }

    @Test
    fun addWeekToSimpleProgram_calendarized_continues_real_dates_and_titles() {
        val id = nextId()
        val base = makeSimpleProgram(id)
        val datedWeek = base.macrocycles[0].blocks[0].mesocycles[0].weeks[0].copy(
            name = "Semana: 05/21",
            startDate = "2026-05-21",
            endDate = "2026-05-27",
            trainingDayDates = mapOf(4 to "2026-05-21", 1 to "2026-05-25", 3 to "2026-05-27"),
        )
        repository.addProgram(
            base.copy(
                timelineStartDate = "2026-05-21",
                calendarization = ProgramCalendarEngine.defaultSimpleDatedCalendarization(),
                simpleProgramKind = SimpleProgramKind.CALENDARIZED,
                startDay = 4,
                macrocycles = base.macrocycles.map { macro ->
                    macro.copy(
                        blocks = macro.blocks.map { block ->
                            block.copy(
                                mesocycles = block.mesocycles.map { meso ->
                                    meso.copy(weeks = listOf(datedWeek))
                                }
                            )
                        }
                    )
                },
            )
        )
        val vm = vmFor(id)

        vm.addWeekToSimpleProgram()

        val updated = repository.getProgramById(id)!!
        val weeks = updated.macrocycles[0].blocks[0].mesocycles[0].weeks
        assertEquals(2, weeks.size)
        assertEquals("Semana: 05/28", weeks[1].name)
        assertEquals("2026-05-28", weeks[1].startDate)
        assertEquals("2026-06-03", weeks[1].endDate)
        assertEquals(setOf(1, 3, 4), weeks[1].trainingDayDates.keys)
        assertEquals("2026-06-01", weeks[1].trainingDayDates[1])
        assertEquals("2026-06-03", weeks[1].trainingDayDates[3])
        assertEquals("2026-05-28", weeks[1].trainingDayDates[4])
        assertEquals(SimpleProgramKind.CALENDARIZED, updated.simpleProgramKind)
        assertTrue(updated.loops.isEmpty())
    }

    @Test
    fun startSimpleCalendarizedBreak_creates_inclusive_custom_weeks() {
        val id = nextId()
        val base = makeSimpleProgram(id)

        val updated = base.startSimpleCalendarizedBreak(
            startDate = LocalDate.parse("2026-05-20"),
            endDate = LocalDate.parse("2026-06-02"),
            startDayOfWeek = 3,
            trainingDays = setOf(3, 1, 2),
        )

        val weeks = updated.macrocycles[0].blocks[0].mesocycles[0].weeks
        assertEquals(2, weeks.size)
        assertEquals("2026-05-20", weeks[0].startDate)
        assertEquals("2026-05-26", weeks[0].endDate)
        assertEquals("2026-05-20", weeks[0].trainingDayDates[3])
        assertEquals("2026-05-25", weeks[0].trainingDayDates[1])
        assertEquals("2026-05-26", weeks[0].trainingDayDates[2])
        assertEquals("2026-05-27", weeks[1].startDate)
        assertEquals("2026-06-02", weeks[1].endDate)
        assertEquals("2026-06-01", weeks[1].trainingDayDates[1])
        assertEquals("2026-06-02", weeks[1].trainingDayDates[2])
    }

    @Test
    fun startFreshCyclicProgram_restores_visible_roadmap_and_allows_new_week() {
        val id = nextId()
        val base = makeSimpleProgram(id)
        val emptyCalendarized = base.copy(
            simpleProgramKind = SimpleProgramKind.CALENDARIZED,
            calendarization = ProgramCalendarEngine.defaultSimpleDatedCalendarization(),
            timelineStartDate = "2026-05-18",
            macrocycles = base.macrocycles.map { macro ->
                macro.copy(
                    blocks = macro.blocks.map { block ->
                        block.copy(mesocycles = block.mesocycles.map { meso -> meso.copy(weeks = emptyList()) })
                    }
                )
            },
        )
        repository.addProgram(emptyCalendarized)
        val vm = vmFor(id)

        vm.startFreshCyclicProgram()

        val restored = repository.getProgramById(id)!!
        val restoredWeeks = restored.macrocycles[0].blocks[0].mesocycles[0].weeks
        assertEquals(SimpleProgramKind.CYCLIC, restored.simpleProgramKind)
        assertEquals(1, restoredWeeks.size)
        assertEquals(restoredWeeks.first().id, vm.uiState.value.selectedWeekId)
        assertNotNull(vm.uiState.value.selectedBlockId)
        assertEquals(null, restored.calendarization)
        assertEquals(null, restored.timelineStartDate)
        assertEquals(ScheduleMode.FLOATING, restored.schedulePlan?.mode)
        assertEquals(ProgramRunStatus.ACTIVE, restored.runState?.status)
        assertEquals(1, restored.runState?.cycleNumber)
        assertTrue(restored.loopOccurrences.isEmpty())
        assertTrue(restored.calendarBreaks.isEmpty())

        vm.addWeekToSimpleProgram()

        val updated = repository.getProgramById(id)!!
        assertEquals(2, updated.macrocycles[0].blocks[0].mesocycles[0].weeks.size)
    }

    @Test
    fun addWeekToSimpleProgram_can_seed_empty_simple_block() {
        val id = nextId()
        val base = makeSimpleProgram(id)
        repository.addProgram(
            base.copy(
                simpleProgramKind = SimpleProgramKind.CYCLIC,
                macrocycles = base.macrocycles.map { macro ->
                    macro.copy(blocks = macro.blocks.map { block -> block.copy(mesocycles = emptyList()) })
                },
            )
        )
        val vm = vmFor(id)

        vm.addWeekToSimpleProgram()

        val updated = repository.getProgramById(id)!!
        val weeks = updated.macrocycles[0].blocks[0].mesocycles[0].weeks
        assertEquals(1, weeks.size)
        assertEquals("Semana 1", weeks.first().name)
        assertEquals(weeks.first().id, vm.uiState.value.selectedWeekId)
    }

    @Test
    fun addWeekToSimpleProgram_can_seed_empty_simple_macrocycle() {
        val id = nextId()
        val base = makeSimpleProgram(id)
        repository.addProgram(
            base.copy(
                simpleProgramKind = SimpleProgramKind.CYCLIC,
                macrocycles = base.macrocycles.map { macro -> macro.copy(blocks = emptyList()) },
            )
        )
        val vm = vmFor(id)

        vm.addWeekToSimpleProgram()

        val updated = repository.getProgramById(id)!!
        val blocks = updated.macrocycles.first().blocks
        val weeks = blocks.first().mesocycles.first().weeks
        assertEquals(1, blocks.size)
        assertEquals(1, weeks.size)
        assertEquals("Semana 1", weeks.first().name)
        assertEquals(blocks.first().id, vm.uiState.value.selectedBlockId)
        assertEquals(weeks.first().id, vm.uiState.value.selectedWeekId)
    }

    @Test
    fun resolveActiveWeekSelection_maps_instance_id_to_template_week() {
        val weeks = listOf(
            ProgramWeek(id = "w1", name = "Semana 1"),
            ProgramWeek(id = "w2", name = "Semana 2"),
        )
        val active = ActiveProgramState(
            programId = "p",
            status = ProgramStatus.ACTIVE,
            currentWeekId = "inst_c3_w2",
            currentWeekInstanceId = "inst_c3_w2",
            currentCycleNumber = 3,
        )
        assertEquals(
            "w2",
            ProgramDetailViewModel.resolveActiveWeekSelection(active, weeks.map { it.id }),
        )
    }

    @Test
    fun copyWeekSessions_fromRoadmap_replaces_content_but_preserves_target_calendar_identity() {
        val id = nextId()
        val program = makeProgram(id)
        repository.addProgram(
            program.copy(
                macrocycles = program.macrocycles.map { macro ->
                    macro.copy(
                        blocks = macro.blocks.map { block ->
                            block.copy(
                                mesocycles = block.mesocycles.map { meso ->
                                    meso.copy(
                                        weeks = meso.weeks.map { week ->
                                            if (week.id == "${id}_w2") {
                                                week.copy(name = "Semana: 05/25", startDate = "2026-05-25", endDate = "2026-05-31")
                                            } else {
                                                week
                                            }
                                        }
                                    )
                                }
                            )
                        }
                    )
                }
            )
        )
        val vm = vmFor(id)

        val copied = vm.copyWeekSessions(
            sourceWeekId = "${id}_w1",
            targetWeekIds = setOf("${id}_w2"),
            replaceWeekIds = setOf("${id}_w2"),
        )

        val updated = repository.getProgramById(id)!!
        val target = updated.macrocycles[0].blocks[0].mesocycles[0].weeks[1]
        assertTrue(copied)
        assertEquals("Semana: 05/25", target.name)
        assertEquals("2026-05-25", target.startDate)
        assertEquals(2, target.sessions.size)
        assertTrue(target.sessions.none { it.id in listOf("${id}_s1", "${id}_s2") })
    }

    @Test
    fun updateWeekMetadata_does_not_drop_calendar_dates() {
        val id = nextId()
        val program = makeProgram(id)
        repository.addProgram(
            program.copy(
                structure = ProgramStructure.COMPLEX,
                timelineStartDate = "2026-05-18",
                calendarization = ProgramCalendarEngine.defaultCompetitionCalendarization(),
                schedulePlan = ProgramSchedulePlan(
                    anchorDate = "2026-05-18",
                    weekStartDay = 1,
                    trainingDays = setOf(1, 3, 5),
                    mode = ScheduleMode.DATED,
                ),
                macrocycles = program.macrocycles.map { macro ->
                    macro.copy(
                        blocks = macro.blocks.map { block ->
                            block.copy(
                                mesocycles = block.mesocycles.map { meso ->
                                    meso.copy(
                                        weeks = meso.weeks.map { week ->
                                            if (week.id == "${id}_w1") {
                                                week.copy(
                                                    startDate = "2026-05-18",
                                                    endDate = "2026-05-24",
                                                    trainingDayDates = mapOf(1 to "2026-05-18"),
                                                )
                                            } else week
                                        }
                                    )
                                }
                            )
                        }
                    )
                },
            )
        )
        val vm = vmFor(id)

        vm.updateWeekMetadata("${id}_w1", "Semana ancla", "Notas")

        val week = repository.getProgramById(id)!!
            .macrocycles[0].blocks[0].mesocycles[0].weeks[0]
        assertEquals("Semana ancla", week.name)
        assertEquals("Notas", week.description)
        assertEquals("2026-05-18", week.startDate)
        assertEquals("2026-05-24", week.endDate)
        assertEquals("2026-05-18", week.trainingDayDates[1])
    }

    @Test
    fun addWeekToSelectedAdvancedBlock_appends_to_last_mesocycle() {
        val id = nextId()
        val program = makeProgram(id)
        val firstBlock = program.macrocycles[0].blocks[0]
        val withTwoMesos = program.copy(
            macrocycles = program.macrocycles.map { macro ->
                macro.copy(
                    blocks = listOf(
                        firstBlock.copy(
                            mesocycles = firstBlock.mesocycles + Mesocycle(
                                id = "${id}_m_last",
                                name = "Meso last",
                                weeks = listOf(ProgramWeek(id = "${id}_w_last", name = "W last")),
                            ),
                        ),
                    )
                )
            }
        )
        repository.addProgram(withTwoMesos)
        val vm = vmFor(id)
        vm.selectBlock("${id}_b1")

        vm.addWeekToSelectedAdvancedBlock("Semana extra")

        val block = repository.getProgramById(id)!!.macrocycles[0].blocks[0]
        assertEquals(2, block.mesocycles[0].weeks.size)
        assertEquals(2, block.mesocycles[1].weeks.size)
        assertEquals("Semana extra", block.mesocycles[1].weeks.last().name)
    }

    @Test
    fun reorderSessions_swaps_positions() {
        val id = nextId()
        repository.addProgram(makeProgram(id))
        val vm = vmFor(id)

        vm.reorderSessions(weekId = "${id}_w1", fromIndex = 0, toIndex = 1)

        val updated = repository.getProgramById(id)!!
        val week = updated.macrocycles[0].blocks[0].mesocycles[0].weeks[0]
        assertEquals("${id}_s2", week.sessions[0].id)
        assertEquals("${id}_s1", week.sessions[1].id)
    }

    @Test
    fun replaceWeekSessions_persists_dayOfWeek_across_reread() {
        val id = nextId()
        repository.addProgram(makeProgram(id))
        val vm = vmFor(id)
        val weekId = "${id}_w1"
        val moved = listOf(
            Session(id = "${id}_s1", name = "S1", dayOfWeek = 4, assignedDays = listOf(4)),
            Session(id = "${id}_s2", name = "S2", dayOfWeek = 2, assignedDays = listOf(2)),
        )

        vm.replaceWeekSessions(weekId, moved)

        val week = repository.getProgramById(id)!!
            .macrocycles[0].blocks[0].mesocycles[0].weeks[0]
        assertEquals(4, week.sessions.first { it.id == "${id}_s1" }.dayOfWeek)
        assertEquals(listOf(4), week.sessions.first { it.id == "${id}_s1" }.assignedDays)
        val reread = repository.getProgramById(id)!!
            .macrocycles[0].blocks[0].mesocycles[0].weeks[0]
        assertEquals(4, reread.sessions.first { it.id == "${id}_s1" }.dayOfWeek)
    }

    @Test
    fun replaceWeekSessions_resolves_instance_week_id() {
        val id = nextId()
        repository.addProgram(makeProgram(id))
        val vm = vmFor(id)
        val templateWeekId = "${id}_w1"
        val instanceWeekId = com.example.kpkn.domain.training.ProgramProgressEngine.instanceIdFor(1, templateWeekId)
        val moved = listOf(
            Session(id = "${id}_s1", name = "S1", dayOfWeek = 5, assignedDays = listOf(5)),
            Session(id = "${id}_s2", name = "S2", dayOfWeek = 2, assignedDays = listOf(2)),
        )

        vm.replaceWeekSessions(instanceWeekId, moved)

        val week = repository.getProgramById(id)!!
            .macrocycles[0].blocks[0].mesocycles[0].weeks[0]
        assertEquals(templateWeekId, week.id)
        assertEquals(5, week.sessions.first { it.id == "${id}_s1" }.dayOfWeek)
    }

    @Test
    fun replaceWeekSessions_unknown_week_does_not_write() {
        val id = nextId()
        repository.addProgram(makeProgram(id))
        val vm = vmFor(id)
        val before = repository.getProgramById(id)!!

        vm.replaceWeekSessions("missing-week", listOf(
            Session(id = "${id}_s1", name = "S1", dayOfWeek = 7, assignedDays = listOf(7)),
        ))

        val after = repository.getProgramById(id)!!
        assertEquals(
            before.macrocycles[0].blocks[0].mesocycles[0].weeks[0].sessions.map { it.dayOfWeek },
            after.macrocycles[0].blocks[0].mesocycles[0].weeks[0].sessions.map { it.dayOfWeek },
        )
    }

    @Test
    fun updateProgram_replaces_in_repository() {
        val id = nextId()
        repository.addProgram(makeProgram(id))
        val vm = vmFor(id)

        val updated = repository.getProgramById(id)!!.copy(name = "Updated Name")
        vm.updateProgram(updated)

        assertEquals("Updated Name", repository.getProgramById(id)!!.name)
    }

    // ─── Factory ──────────────────────────────────────────────────────────

    @Test
    fun factory_creates_correct_viewmodel() {
        val factory = ProgramDetailViewModel.factory("prog1")
        assertNotNull(factory)
    }

    @Test
    fun acceptAutoregulation_clears_pending_confirm_action() = runBlocking {
        val id = nextId()
        val seeded = makeProgram(id).copy(
            runState = ProgramRunState(
                runId = "run",
                pendingAction = PendingProgramAction(
                    type = PendingProgramActionType.CONFIRM_AUTOREGULATION,
                    message = "AUGE propone bajar TM",
                    proposals = listOf(
                        AutoregulationProposal(
                            kind = AutoregulationProposalKind.ADJUST_TM,
                            liftSlot = "SQUAT",
                            percentDelta = -2.5,
                            explanation = "AMRAP corto",
                        ),
                    ),
                ),
            ),
        )
        repository.addProgram(seeded)
        val vm = vmFor(id)
        assertEquals(PendingProgramActionType.CONFIRM_AUTOREGULATION, vm.program.value?.runState?.pendingAction?.type)
        vm.acceptAutoregulation()
        withTimeout(5_000) {
            repository.programs.first { programs ->
                programs.firstOrNull { it.id == id }?.runState?.pendingAction == null
            }
        }
        withTimeout(5_000) {
            vm.blockTransitionBanner.first { it == null }
        }
        assertNull(repository.getProgramById(id)?.runState?.pendingAction)
        assertNull(vm.blockTransitionBanner.value)
    }

    @Test
    fun restoreManualSessionFromPlan_restores_only_selected_session_and_preserves_sibling_and_history() = runBlocking {
        val id = nextId()
        val fixture = seedManualRestoreFixture(id)
        repository.addWorkoutLog(siblingWorkoutLog(id, fixture, fixture.siblingId))
        repository.flushPendingWrites()
        val historyBefore = repository.getLogsForProgram(id)
        assertEquals(listOf(fixture.siblingId), historyBefore.map { it.sessionId })

        val beforeProgram = repository.getProgramById(id)!!
        val beforeWeek = weeksOf(beforeProgram).single { it.id == fixture.weekId }
        val editedTarget = beforeWeek.sessions.single { it.id == fixture.targetId }
        val siblingBefore = beforeWeek.sessions.single { it.id == fixture.siblingId }
        assertEquals("Sesión personalizada", editedTarget.name)
        val editedPrescription = editedTarget.allExercises().flatMap { it.sets }
        assertEquals(fixture.plannedTarget.allExercises().flatMap { it.sets }.size, editedPrescription.size)
        assertTrue(editedPrescription.isNotEmpty())
        assertTrue(editedPrescription.all { it.targetReps == 99 })
        assertEquals(fixture.siblingJson, sessionJson(siblingBefore))
        assertEquals(setOf(fixture.targetId, fixture.siblingId), beforeProgram.manualSessionOverrides.map { it.sessionId }.toSet())

        val vm = vmFor(id)
        vm.restoreManualSessionFromPlan(fixture.targetId)

        withTimeout(10_000) {
            vm.uiState.first { it.snackbarMessage?.startsWith("Sesión restaurada desde el plan.") == true }
        }
        val restoredProgram = repository.getProgramById(id)!!
        val restoredWeek = weeksOf(restoredProgram).single { it.id == fixture.weekId }
        val restoredTarget = restoredWeek.sessions.single { it.id == fixture.targetId }
        val restoredSibling = restoredWeek.sessions.single { it.id == fixture.siblingId }

        assertEquals("la receta devuelve el contenido completo de la sesión seleccionada", fixture.plannedTarget, restoredTarget)
        assertEquals("los IDs de sesión, ejercicios y series siguen estables", sessionIdentity(fixture.plannedTarget), sessionIdentity(restoredTarget))
        assertEquals("la sesión vecina permanece byte a byte", fixture.siblingJson, sessionJson(restoredSibling))
        assertEquals(listOf(fixture.siblingId), restoredProgram.manualSessionOverrides.map { it.sessionId })
        assertEquals(historyBefore, repository.getLogsForProgram(id))
        assertEquals(fixture.siblingId, repository.getLogsForProgram(id).single().sessionId)
        assertTrue(vm.uiState.value.snackbarMessage.orEmpty().contains("las sesiones vecinas y el historial se conservaron"))
    }

    @Test
    fun restoreManualSessionFromPlan_blocks_logged_session_without_changing_plan_or_history() = runBlocking {
        val id = nextId()
        val fixture = seedManualRestoreFixture(id)
        repository.addWorkoutLog(siblingWorkoutLog(id, fixture, fixture.targetId))
        repository.flushPendingWrites()
        val beforeProgram = repository.getProgramById(id)!!
        val historyBefore = repository.getLogsForProgram(id)

        val vm = vmFor(id)
        vm.restoreManualSessionFromPlan(fixture.targetId)

        withTimeout(10_000) {
            vm.uiState.first {
                it.snackbarMessage == "La sesión ya se inició o tiene registros; su prescripción histórica se conserva."
            }
        }

        assertEquals(beforeProgram, repository.getProgramById(id))
        assertEquals(historyBefore, repository.getLogsForProgram(id))
        assertEquals(fixture.targetId, repository.getLogsForProgram(id).single().sessionId)
        assertTrue(repository.getProgramById(id)!!.manualSessionOverrides.any { it.sessionId == fixture.targetId })
    }

    // ─── Restaurar sesión desde el plan: casos de consolidación ───────────

    private fun restoreDays(targetPercent: Double, siblingPercent: Double): List<DayRecipe> = listOf(
        DayRecipe(
            id = "restore-target-day",
            label = "Día receta seleccionado",
            weekday = 1,
            slots = listOf(
                slot(
                    "restore-target-bench",
                    SlotRole.T1_MAIN,
                    CatalogIds.BP,
                    percentSets(150, 5 to targetPercent, 5 to targetPercent),
                    150,
                    LiftSlot.BENCH,
                    isCompetitionLift = true,
                ),
            ),
        ),
        DayRecipe(
            id = "restore-sibling-day",
            label = "Día receta vecino",
            weekday = 3,
            slots = listOf(
                slot(
                    "restore-sibling-bench",
                    SlotRole.T1_MAIN,
                    CatalogIds.BP,
                    percentSets(150, 5 to siblingPercent, 5 to siblingPercent),
                    150,
                    LiftSlot.BENCH,
                    isCompetitionLift = true,
                ),
            ),
        ),
    )

    private fun twoWeekRestoreRecipe(programId: String) = TrainingPlanRecipe(
        id = "restore-recipe-$programId",
        weeks = listOf(
            weekRecipe(1, 0, "Base", BlockGoal.ACCUMULATION, restoreDays(75.0, 70.0)),
            weekRecipe(2, 0, "Base", BlockGoal.ACCUMULATION, restoreDays(75.0, 70.0)),
        ),
        claimedDaysPerWeek = 2,
    )

    private fun sessionOverride(
        sessionId: String,
        weekId: String?,
        weekOccurrence: Int?,
        recipeDayId: String?,
        scope: ManualOverrideScope = ManualOverrideScope.SESSION,
        reason: String = "Edición manual",
    ) = ManualSessionOverride(
        sessionId = sessionId,
        weekId = weekId,
        weekOccurrence = weekOccurrence,
        recipeDayId = recipeDayId,
        scope = scope,
        reason = reason,
        createdAtMs = 1L,
    )

    private fun prescription(session: Session): List<Triple<Int?, Double?, Double?>> =
        session.allExercises().flatMap { exercise ->
            exercise.sets.map { Triple(it.targetReps, it.targetPercentageRM, it.weight) }
        }

    private fun appendSession(program: Program, weekId: String, session: Session): Program = program.copy(
        macrocycles = program.macrocycles.map { macro ->
            macro.copy(
                blocks = macro.blocks.map { block ->
                    block.copy(
                        mesocycles = block.mesocycles.map { meso ->
                            meso.copy(
                                weeks = meso.weeks.map { week ->
                                    if (week.id == weekId) week.copy(sessions = week.sessions + session) else week
                                },
                            )
                        },
                    )
                },
            )
        },
    )

    /**
     * Fixture general de «Restaurar desde el plan»: receta de una o dos semanas,
     * receta efectiva aceptada para la ocurrencia 2, overrides configurables y un
     * gancho para torcer el programa (receta ausente, sesión creada por el usuario,
     * día movido...). Edita la sesión objetivo y la vecina de la semana probada.
     */
    private suspend fun seedRestoreFixture(
        programId: String,
        twoWeeks: Boolean = false,
        acceptEffectiveRecipe: Boolean = false,
        targetOverrides: (targetId: String, weekId: String, weekOccurrence: Int) -> List<ManualSessionOverride> =
            { targetId, weekId, occurrence -> listOf(sessionOverride(targetId, weekId, occurrence, "restore-target-day")) },
        tweak: (Program, String) -> Program = { program, _ -> program },
    ): ManualRestoreFixture {
        val recipe = if (twoWeeks) twoWeekRestoreRecipe(programId) else restoreRecipe(programId)
        val generated = PlanMaterializer.materialize(
            Program(id = programId, name = "Restore $programId", structure = ProgramStructure.COMPLEX),
            recipe,
            CatalogCompositionTestSupport.metadata,
            RestoreSessionIds(),
            strict = false,
        )
        repository.addProgram(generated)
        repository.flushPendingWrites()
        var program = repository.getProgramById(programId) ?: error("programa de prueba ausente")
        val weekIndex = if (twoWeeks) 1 else 0
        val occurrence = weekIndex + 1
        if (twoWeeks && acceptEffectiveRecipe) {
            // Una propuesta aceptada: la ocurrencia 2 pasa a una receta efectiva 5 puntos más pesada
            // y su semana se reconstruye con ella (como hace la aceptación AUGE).
            val base = recipe.weeks[1]
            val effective = base.copy(
                days = base.days.map { day ->
                    day.copy(
                        slots = day.slots.map { slot ->
                            slot.copy(sets = slot.sets.map { set -> set.copy(percent = set.percent?.plus(5.0)) })
                        },
                    )
                },
            )
            program = PlanMaterializer.withEffectiveWeekRecipe(
                program = program,
                weekOccurrence = occurrence,
                cycleNumber = 1,
                weekRecipe = effective,
                applied = listOf(
                    AppliedRecipeProposal(
                        proposalId = "accepted-effective",
                        kind = "SCALE_WEEK_VOLUME",
                        summary = "aceptada en la prueba",
                        acceptedAtMs = 1L,
                    ),
                ),
            )
            program = PlanMaterializer.rematerializeWeek(
                program = program,
                weekId = weeksOf(program)[weekIndex].id,
                recipe = recipe,
                weekOccurrence = occurrence,
            )
        }
        val week = weeksOf(program)[weekIndex]
        val plannedTarget = week.sessions[0]
        val plannedSibling = week.sessions[1]
        var edited = replaceRestoreFixtureSessions(
            program,
            week.id,
            mapOf(
                plannedTarget.id to manuallyEdit(plannedTarget, "Sesión personalizada"),
                plannedSibling.id to manuallyEdit(plannedSibling, "Vecina personalizada"),
            ),
        )
        edited = edited.copy(
            manualSessionOverrides = edited.manualSessionOverrides +
                targetOverrides(plannedTarget.id, week.id, occurrence),
        )
        edited = PlanMaterializer.withManualSessionOverride(
            edited,
            plannedSibling.id,
            week.id,
            weekOccurrence = occurrence,
            recipeDayId = "restore-sibling-day",
            nowMs = 2L,
        )
        edited = tweak(edited, week.id)
        repository.updateProgramNow(edited)

        val stored = repository.getProgramById(programId) ?: error("programa editado ausente")
        val storedWeek = weeksOf(stored).single { it.id == week.id }
        return ManualRestoreFixture(
            weekId = storedWeek.id,
            plannedTarget = plannedTarget,
            targetId = plannedTarget.id,
            siblingId = plannedSibling.id,
            siblingJson = sessionJson(storedWeek.sessions.single { it.id == plannedSibling.id }),
        )
    }

    private suspend fun restoreAndAwait(vm: ProgramDetailViewModel, sessionId: String, expectedMessage: (String) -> Boolean): String {
        vm.restoreManualSessionFromPlan(sessionId)
        return withTimeout(10_000) {
            vm.uiState.first { state -> state.snackbarMessage?.let(expectedMessage) == true }.snackbarMessage!!
        }
    }

    @Test
    fun restoreManualSessionFromPlan_week_two_with_accepted_effective_recipe_restores_the_effective_prescription() = runBlocking {
        val id = nextId()
        val fixture = seedRestoreFixture(id, twoWeeks = true, acceptEffectiveRecipe = true)
        val before = repository.getProgramById(id)!!
        assertEquals(listOf(2), before.effectiveWeekRecipes.map { it.weekOccurrence })
        val beforeWeek = weeksOf(before).single { it.id == fixture.weekId }
        assertEquals(1, weeksOf(before).indexOfFirst { it.id == fixture.weekId })
        assertTrue(beforeWeek.sessions.single { it.id == fixture.targetId }.allExercises()
            .flatMap { it.sets }.all { it.targetReps == 99 })
        val plannedPercents = fixture.plannedTarget.allExercises().flatMap { it.sets }.map { it.targetPercentageRM }
        assertTrue("la receta efectiva aceptada pesa 80 %, no el 75 % base: $plannedPercents",
            plannedPercents.isNotEmpty() && plannedPercents.all { it == 80.0 })

        val vm = vmFor(id)
        restoreAndAwait(vm, fixture.targetId) { it.startsWith("Sesión restaurada desde el plan.") }

        val restored = repository.getProgramById(id)!!
        val restoredWeek = weeksOf(restored).single { it.id == fixture.weekId }
        val restoredTarget = restoredWeek.sessions.single { it.id == fixture.targetId }
        assertEquals(prescription(fixture.plannedTarget), prescription(restoredTarget))
        assertEquals(fixture.plannedTarget.name, restoredTarget.name)
        assertEquals(sessionIdentity(fixture.plannedTarget), sessionIdentity(restoredTarget))
        assertEquals("la sesión vecina permanece byte a byte", fixture.siblingJson,
            sessionJson(restoredWeek.sessions.single { it.id == fixture.siblingId }))
        assertEquals(listOf(fixture.siblingId), restored.manualSessionOverrides.map { it.sessionId })
        // La semana 1 no se toca.
        assertEquals(weeksOf(before)[0], weeksOf(restored)[0])
        // La receta efectiva aceptada sigue siendo la misma.
        assertEquals(before.effectiveWeekRecipes, restored.effectiveWeekRecipes)
    }

    @Test
    fun restoreManualSessionFromPlan_override_born_from_editor_transfer_restores_the_destination_and_drops_its_receipt() = runBlocking {
        val id = nextId()
        val fixture = seedRestoreFixture(
            id,
            targetOverrides = { targetId, weekId, occurrence ->
                listOf(
                    sessionOverride(
                        targetId, weekId, occurrence, "restore-target-day",
                        reason = "[editor-transfer:transfer-7:abc123] sesión transferida desde otra semana",
                    ),
                )
            },
        )
        assertTrue(repository.getProgramById(id)!!.manualSessionOverrides
            .any { it.sessionId == fixture.targetId && it.reason.contains("[editor-transfer:") })

        val vm = vmFor(id)
        restoreAndAwait(vm, fixture.targetId) { it.startsWith("Sesión restaurada desde el plan.") }

        val restored = repository.getProgramById(id)!!
        val restoredTarget = weeksOf(restored).single { it.id == fixture.weekId }.sessions.single { it.id == fixture.targetId }
        assertEquals(fixture.plannedTarget, restoredTarget)
        assertEquals(listOf(fixture.siblingId), restored.manualSessionOverrides.map { it.sessionId })
        // El recibo de transferencia vivía dentro de la marca: se va con ella.
        assertTrue(restored.manualSessionOverrides.none { it.reason.contains("[editor-transfer:") })
    }

    @Test
    fun restoreManualSessionFromPlan_without_source_recipe_keeps_the_override_and_says_so() = runBlocking {
        val id = nextId()
        val fixture = seedRestoreFixture(id, tweak = { program, _ -> program.copy(sourceRecipe = null) })
        val before = repository.getProgramById(id)!!

        val vm = vmFor(id)
        val message = restoreAndAwait(vm, fixture.targetId) { it.startsWith("No se encontró la receta fuente") }

        assertEquals("No se encontró la receta fuente; no se cambió la sesión.", message)
        assertEquals(before, repository.getProgramById(id))
        assertTrue(repository.getProgramById(id)!!.manualSessionOverrides.any { it.sessionId == fixture.targetId })
    }

    @Test
    fun restoreManualSessionFromPlan_session_without_recipe_counterpart_is_not_reported_as_restored() = runBlocking {
        val id = nextId()
        val userSession = Session(
            id = "user-created-session",
            name = "Mi sesión propia",
            dayOfWeek = 5,
            assignedDays = listOf(5),
            exercises = listOf(
                Exercise(
                    id = "user-created-exercise",
                    name = "Remo propio",
                    sets = listOf(ExerciseSet(id = "user-created-set", targetReps = 10)),
                ),
            ),
        )
        val fixture = seedRestoreFixture(
            id,
            targetOverrides = { _, _, _ -> emptyList() },
            tweak = { program, weekId ->
                appendSession(program, weekId, userSession).copy(
                    manualSessionOverrides = program.manualSessionOverrides +
                        sessionOverride(userSession.id, weekId, 1, recipeDayId = null),
                )
            },
        )
        val before = repository.getProgramById(id)!!
        assertTrue(weeksOf(before).single { it.id == fixture.weekId }.sessions.any { it.id == userSession.id })

        val vm = vmFor(id)
        val message = restoreAndAwait(vm, userSession.id) { it.startsWith("Esta sesión no tiene una sesión equivalente") }

        assertTrue("sin éxito falso: $message", !message.startsWith("Sesión restaurada"))
        assertEquals("ni el plan ni la marca cambian", before, repository.getProgramById(id))
        assertTrue(repository.getProgramById(id)!!.manualSessionOverrides.any { it.sessionId == userSession.id })
    }

    @Test
    fun restoreManualSessionFromPlan_session_moved_to_another_weekday_returns_to_its_recipe_day() = runBlocking {
        val id = nextId()
        val fixture = seedRestoreFixture(
            id,
            tweak = { program, weekId ->
                val week = weeksOf(program).single { it.id == weekId }
                val target = week.sessions[0]
                replaceRestoreFixtureSessions(
                    program,
                    weekId,
                    mapOf(target.id to target.copy(dayOfWeek = 2, assignedDays = listOf(2))),
                )
            },
        )
        val movedBefore = weeksOf(repository.getProgramById(id)!!).single { it.id == fixture.weekId }
            .sessions.single { it.id == fixture.targetId }
        assertEquals(2, movedBefore.dayOfWeek)

        val vm = vmFor(id)
        restoreAndAwait(vm, fixture.targetId) { it.startsWith("Sesión restaurada desde el plan.") }

        val restored = repository.getProgramById(id)!!
        val restoredWeek = weeksOf(restored).single { it.id == fixture.weekId }
        assertEquals("el día de la receta y su prescripción vuelven con la sesión", fixture.plannedTarget,
            restoredWeek.sessions.single { it.id == fixture.targetId })
        assertEquals(1, restoredWeek.sessions.single { it.id == fixture.targetId }.dayOfWeek)
        assertEquals(fixture.siblingJson, sessionJson(restoredWeek.sessions.single { it.id == fixture.siblingId }))
        assertEquals(restoredWeek.sessions.size, restoredWeek.sessions.map { it.id }.distinct().size)
        assertEquals(2, restoredWeek.sessions.size)
    }

    @Test
    fun restoreManualSessionFromPlan_template_future_occurrences_scope_restores_the_session_and_drops_that_scope() = runBlocking {
        val id = nextId()
        val fixture = seedRestoreFixture(
            id,
            targetOverrides = { targetId, _, _ ->
                listOf(
                    sessionOverride(
                        targetId, null, null, null,
                        scope = ManualOverrideScope.TEMPLATE_FUTURE_OCCURRENCES,
                        reason = "Edición de plantilla aplicada a futuras ocurrencias",
                    ),
                )
            },
        )
        assertEquals(
            listOf(ManualOverrideScope.TEMPLATE_FUTURE_OCCURRENCES),
            repository.getProgramById(id)!!.manualSessionOverrides.filter { it.sessionId == fixture.targetId }.map { it.scope },
        )

        val vm = vmFor(id)
        restoreAndAwait(vm, fixture.targetId) { it.startsWith("Sesión restaurada desde el plan.") }

        val restored = repository.getProgramById(id)!!
        assertEquals(fixture.plannedTarget, weeksOf(restored).single { it.id == fixture.weekId }
            .sessions.single { it.id == fixture.targetId })
        assertEquals(listOf(fixture.siblingId), restored.manualSessionOverrides.map { it.sessionId })
    }

    @Test
    fun restoreManualSessionFromPlan_only_drops_the_session_scope_when_a_template_scope_also_exists() = runBlocking {
        val id = nextId()
        val fixture = seedRestoreFixture(
            id,
            targetOverrides = { targetId, weekId, occurrence ->
                listOf(
                    sessionOverride(targetId, weekId, occurrence, "restore-target-day"),
                    sessionOverride(
                        targetId, null, null, null,
                        scope = ManualOverrideScope.TEMPLATE_FUTURE_OCCURRENCES,
                        reason = "Edición de plantilla aplicada a futuras ocurrencias",
                    ),
                )
            },
        )

        val vm = vmFor(id)
        restoreAndAwait(vm, fixture.targetId) { it.startsWith("Sesión restaurada desde el plan.") }

        val restored = repository.getProgramById(id)!!
        assertEquals("el contenido vuelve a la receta aunque el alcance de plantilla siga marcado", fixture.plannedTarget,
            weeksOf(restored).single { it.id == fixture.weekId }.sessions.single { it.id == fixture.targetId })
        assertEquals(
            listOf(ManualOverrideScope.TEMPLATE_FUTURE_OCCURRENCES),
            restored.manualSessionOverrides.filter { it.sessionId == fixture.targetId }.map { it.scope },
        )
        assertTrue(restored.manualSessionOverrides.any { it.sessionId == fixture.siblingId })
    }

    @Test
    fun removeManualSessionOverride_scope_only_removes_the_chosen_scope_and_null_removes_all() {
        val base = Program(
            id = "scope-unit",
            name = "Scope",
            manualSessionOverrides = listOf(
                sessionOverride("s1", null, null, null, ManualOverrideScope.SESSION),
                sessionOverride("s1", null, null, null, ManualOverrideScope.TEMPLATE_FUTURE_OCCURRENCES),
                sessionOverride("s2", null, null, null, ManualOverrideScope.SESSION),
            ),
        )
        fun keys(program: Program) = program.manualSessionOverrides.map { it.sessionId to it.scope }
        assertEquals(
            listOf("s1" to ManualOverrideScope.TEMPLATE_FUTURE_OCCURRENCES, "s2" to ManualOverrideScope.SESSION),
            keys(PlanMaterializer.removeManualSessionOverride(base, "s1", ManualOverrideScope.SESSION)),
        )
        assertEquals(
            listOf("s1" to ManualOverrideScope.SESSION, "s2" to ManualOverrideScope.SESSION),
            keys(PlanMaterializer.removeManualSessionOverride(base, "s1", ManualOverrideScope.TEMPLATE_FUTURE_OCCURRENCES)),
        )
        assertEquals(listOf("s2" to ManualOverrideScope.SESSION), keys(PlanMaterializer.removeManualSessionOverride(base, "s1")))
    }

    @Test
    fun restoreBlockedSessionIds_cover_logged_and_in_progress_sessions_only() = runBlocking {
        val id = nextId()
        val fixture = seedManualRestoreFixture(id)
        val vm = vmFor(id)
        assertEquals(emptySet<String>(), withTimeout(5_000) { vm.restoreBlockedSessionIds.first() })

        repository.addWorkoutLog(siblingWorkoutLog(id, fixture, fixture.siblingId))
        assertEquals(
            setOf(fixture.siblingId),
            withTimeout(5_000) { vm.restoreBlockedSessionIds.first { fixture.siblingId in it } },
        )

        val targetSession = weeksOf(repository.getProgramById(id)!!).single().sessions.single { it.id == fixture.targetId }
        repository.startWorkout(OngoingWorkoutState(programId = id, session = targetSession, startTime = 11L))
        assertEquals(
            setOf(fixture.siblingId, fixture.targetId),
            withTimeout(5_000) { vm.restoreBlockedSessionIds.first { fixture.targetId in it } },
        )
    }

    @Test
    fun replacing_protocol_keeps_program_identity_and_does_not_open_a_copy() = runBlocking {
        com.example.kpkn.domain.training.CatalogCompositionTestSupport.install()
        val id = nextId()
        val original = makeSimpleProgram(id)
        repository.addProgram(original)
        repository.flushPendingWrites()
        val vm = vmFor(id)
        val protocol = com.example.kpkn.data.protocols.PROTOCOL_LIBRARY.first { it.id == "gzclp" }
        vm.applyProtocolOverwrite(protocol, overwrite = true)
        withTimeout(20_000) {
            while (vm.uiState.value.snackbarMessage.isNullOrBlank()) delay(25)
        }
        assertTrue(vm.uiState.value.snackbarMessage.orEmpty(), vm.uiState.value.snackbarMessage.orEmpty().contains("sin crear una copia"))
        assertEquals(listOf(id), repository.programs.value.map { it.id })
        assertEquals(original.name, repository.getProgramById(id)?.name)
        assertEquals(protocol.id, repository.getProgramById(id)?.sourceProtocolId)
        assertNull(vm.uiState.value.pendingOpenProgramId)
    }

    @Test
    fun copying_protocol_is_an_explicit_separate_action() = runBlocking {
        com.example.kpkn.domain.training.CatalogCompositionTestSupport.install()
        val id = nextId()
        val original = makeSimpleProgram(id)
        repository.addProgram(original)
        repository.flushPendingWrites()
        val vm = vmFor(id)
        val protocol = com.example.kpkn.data.protocols.PROTOCOL_LIBRARY.first { it.id == "gzclp" }
        vm.applyProtocolOverwrite(protocol, overwrite = false)
        withTimeout(20_000) {
            while (vm.uiState.value.snackbarMessage.isNullOrBlank()) delay(25)
        }
        assertEquals(2, repository.programs.value.size)
        assertEquals(original.name, repository.getProgramById(id)?.name)
        assertNotEquals(id, vm.uiState.value.pendingOpenProgramId)
        assertNotNull(vm.uiState.value.pendingOpenProgramId)
    }

    @Test
    fun applyProgramTemplate_reselects_week_in_new_graph() = runBlocking {
        com.example.kpkn.domain.training.CatalogCompositionTestSupport.install()
        val id = nextId()
        repository.addProgram(Program(id = id, name = "Vacío", structure = ProgramStructure.SIMPLE))
        val vm = vmFor(id)
        val staleWeek = "${id}_missing_week"
        vm.selectWeek(staleWeek)
        assertEquals(staleWeek, vm.uiState.value.selectedWeekId)

        val template = com.example.kpkn.data.programs.PROGRAM_TEMPLATES.first { it.id == "simple-1" }
        vm.applyProgramTemplate(template)

        withTimeout(15_000) {
            while (true) {
                val program = vm.program.value ?: repository.getProgramById(id)
                val weekId = vm.uiState.value.selectedWeekId
                val weekIds = program?.macrocycles
                    ?.flatMap { it.blocks }
                    ?.flatMap { it.mesocycles }
                    ?.flatMap { it.weeks }
                    ?.map { it.id }
                    .orEmpty()
                if (program != null &&
                    weekId != null &&
                    weekId in weekIds &&
                    weekIds.isNotEmpty() &&
                    program.macrocycles.flatMap { it.blocks }.flatMap { it.mesocycles }.flatMap { it.weeks }
                        .any { week ->
                            week.sessions.any(
                                com.example.kpkn.domain.templates.SessionTemplateEngine::sessionHasExecutableContent,
                            )
                        }
                ) {
                    break
                }
                delay(50)
            }
        }

        val applied = vm.program.value ?: repository.getProgramById(id)!!
        val weekIds = applied.macrocycles
            .flatMap { it.blocks }
            .flatMap { it.mesocycles }
            .flatMap { it.weeks }
            .map { it.id }
            .toSet()
        assertTrue(vm.uiState.value.selectedWeekId in weekIds)
        assertNotNull(vm.uiState.value.snackbarMessage)
    }

    // ─── Copias recuperables (ProgramSnapshotStore fuera de Main) ─────────

    @Test
    fun attachSnapshotStore_loads_saved_copies_and_refresh_picks_up_new_ones() = runBlocking {
        val id = nextId()
        val program = makeSimpleProgram(id)
        repository.addProgram(program)
        val store = programSnapshotStore(ApplicationProvider.getApplicationContext<Context>())
        store.push(program, "copia-1")
        val vm = vmFor(id)

        // La carga ya no es síncrona: el estado llega cuando termina la lectura en IO.
        vm.attachSnapshotStore(store)
        val loaded = withTimeout(5_000) { vm.programSnapshots.first { it.isNotEmpty() } }
        assertEquals(listOf("copia-1"), loaded.map { it.reason })

        store.push(program, "copia-2")
        vm.refreshProgramSnapshots()
        val refreshed = withTimeout(5_000) { vm.programSnapshots.first { it.size == 2 } }
        assertEquals(listOf("copia-1", "copia-2"), refreshed.map { it.reason })
    }

    @Test
    fun replacing_protocol_records_the_previous_plan_as_a_recoverable_copy() = runBlocking {
        com.example.kpkn.domain.training.CatalogCompositionTestSupport.install()
        val id = nextId()
        val original = makeSimpleProgram(id)
        repository.addProgram(original)
        repository.flushPendingWrites()
        val store = programSnapshotStore(ApplicationProvider.getApplicationContext<Context>())
        val vm = vmFor(id)
        vm.attachSnapshotStore(store)
        val protocol = com.example.kpkn.data.protocols.PROTOCOL_LIBRARY.first { it.id == "gzclp" }

        vm.applyProtocolOverwrite(protocol, overwrite = true)
        withTimeout(20_000) {
            while (vm.uiState.value.snackbarMessage.isNullOrBlank()) delay(25)
        }

        val snapshots = withTimeout(5_000) { vm.programSnapshots.first { it.isNotEmpty() } }
        assertEquals(1, snapshots.size)
        assertTrue(snapshots.single().reason.startsWith("Antes de"))
        assertEquals(original.name, snapshots.single().program.name)
        assertEquals(snapshots.map { it.id }, store.list(id).map { it.id })
    }

    // ─── P1 · D2.3 / D2.8: «Reemplazar todo» con plantilla ────────────────

    private suspend fun awaitSnackbar(vm: ProgramDetailViewModel): String {
        withTimeout(20_000) {
            while (vm.uiState.value.snackbarMessage.isNullOrBlank()) delay(25)
        }
        return vm.uiState.value.snackbarMessage.orEmpty()
    }

    private fun simpleTemplate() =
        com.example.kpkn.data.programs.PROGRAM_TEMPLATES.first { it.id == "simple-1" }

    @Test
    fun applyProgramTemplate_overwrite_with_a_failing_snapshot_keeps_the_program_and_reports_it() = runBlocking {
        com.example.kpkn.domain.training.CatalogCompositionTestSupport.install()
        val id = nextId()
        repository.addProgram(makeSimpleProgram(id))
        repository.flushPendingWrites()
        val before = repository.getProgramById(id)!!
        val context = CommitFailingContext(ApplicationProvider.getApplicationContext<Context>())
        val vm = vmFor(id)
        vm.attachSnapshotStore(programSnapshotStore(context))

        vm.applyProgramTemplate(simpleTemplate(), overwrite = true)

        // Antes la excepción del guardado escapaba de runCatching y cerraba la app.
        val message = awaitSnackbar(vm)
        assertTrue(message, message.startsWith("No se pudo guardar la copia recuperable del programa"))
        assertEquals(SnackbarType.DANGER, snackbarTypeFor(message))
        assertTrue("el guardado de la copia sí se intentó", context.preferences.commitAttempts >= 1)
        assertEquals("el programa queda intacto", before, repository.getProgramById(id))
        assertTrue(vm.programSnapshots.value.isEmpty())
        assertNull(vm.uiState.value.pendingOpenProgramId)
    }

    @Test
    fun applyProtocolOverwrite_with_a_failing_snapshot_keeps_the_program_and_reports_it() = runBlocking {
        com.example.kpkn.domain.training.CatalogCompositionTestSupport.install()
        val id = nextId()
        repository.addProgram(makeSimpleProgram(id))
        repository.flushPendingWrites()
        val before = repository.getProgramById(id)!!
        val context = CommitFailingContext(ApplicationProvider.getApplicationContext<Context>())
        val vm = vmFor(id)
        vm.attachSnapshotStore(programSnapshotStore(context))
        val protocol = com.example.kpkn.data.protocols.PROTOCOL_LIBRARY.first { it.id == "gzclp" }

        vm.applyProtocolOverwrite(protocol, overwrite = true)

        // El reemplazo por protocolo ya se comportaba bien: esta prueba lo deja fijado.
        val message = awaitSnackbar(vm)
        assertTrue(message, message.startsWith("No se pudo guardar la copia recuperable del programa"))
        assertEquals("el programa queda intacto", before, repository.getProgramById(id))
        assertTrue(context.preferences.commitAttempts >= 1)
    }

    @Test
    fun applyProgramTemplate_overwrite_without_a_snapshot_store_does_not_replace_the_program() = runBlocking {
        com.example.kpkn.domain.training.CatalogCompositionTestSupport.install()
        val id = nextId()
        repository.addProgram(makeSimpleProgram(id))
        repository.flushPendingWrites()
        val before = repository.getProgramById(id)!!
        val vm = vmFor(id)

        vm.applyProgramTemplate(simpleTemplate(), overwrite = true)

        assertEquals(ProgramDetailViewModel.SNAPSHOT_REQUIRED_MESSAGE, awaitSnackbar(vm))
        assertEquals("sin copia recuperable no se reemplaza nada", before, repository.getProgramById(id))
    }

    @Test
    fun applyProgramTemplate_overwrite_saves_the_previous_plan_as_a_recoverable_copy_and_then_replaces_it() = runBlocking {
        com.example.kpkn.domain.training.CatalogCompositionTestSupport.install()
        val id = nextId()
        val original = makeSimpleProgram(id)
        repository.addProgram(original)
        repository.flushPendingWrites()
        val store = programSnapshotStore(ApplicationProvider.getApplicationContext<Context>())
        val vm = vmFor(id)
        vm.attachSnapshotStore(store)
        val template = simpleTemplate()

        vm.applyProgramTemplate(template, overwrite = true)

        val message = awaitSnackbar(vm)
        assertTrue(message, message.startsWith("Plantilla \"${template.name}\" aplicada"))
        val snapshots = withTimeout(5_000) { vm.programSnapshots.first { it.isNotEmpty() } }
        assertEquals(1, snapshots.size)
        assertEquals("Antes de \"${template.name}\"", snapshots.single().reason)
        assertEquals(original.name, snapshots.single().program.name)
        assertEquals(template.id, repository.getProgramById(id)?.structureTemplateId)
    }

    @Test
    fun applyProgramTemplate_overwrite_is_refused_while_a_session_is_in_progress() = runBlocking {
        com.example.kpkn.domain.training.CatalogCompositionTestSupport.install()
        val id = nextId()
        repository.addProgram(makeSimpleProgram(id))
        repository.flushPendingWrites()
        val store = programSnapshotStore(ApplicationProvider.getApplicationContext<Context>())
        val vm = vmFor(id)
        vm.attachSnapshotStore(store)
        val session = weeksOf(repository.getProgramById(id)!!).first().sessions.first()
        repository.startWorkout(OngoingWorkoutState(programId = id, session = session, startTime = 11L))
        val before = repository.getProgramById(id)!!

        vm.applyProgramTemplate(simpleTemplate(), overwrite = true)

        // Mismo aviso que con protocolo (D2.8).
        assertEquals(ProgramDetailViewModel.SESSION_IN_PROGRESS_MESSAGE, awaitSnackbar(vm))
        assertEquals("el programa no se toca", before, repository.getProgramById(id))
        assertTrue("no se guarda copia de un reemplazo que no ocurrió", store.list(id).isEmpty())
    }

    @Test
    fun applyProgramTemplate_ignores_a_second_tap_while_the_first_is_still_running() = runBlocking {
        com.example.kpkn.domain.training.CatalogCompositionTestSupport.install()
        val id = nextId()
        repository.addProgram(makeSimpleProgram(id))
        repository.flushPendingWrites()
        val store = programSnapshotStore(ApplicationProvider.getApplicationContext<Context>())
        val vm = vmFor(id)
        vm.attachSnapshotStore(store)
        val template = simpleTemplate()

        vm.applyProgramTemplate(template, overwrite = true)
        vm.applyProgramTemplate(template, overwrite = true)

        awaitSnackbar(vm)
        withTimeout(5_000) { vm.programSnapshots.first { it.isNotEmpty() } }
        delay(300)
        assertEquals("dos toques seguidos guardan una sola copia", 1, store.list(id).size)
    }

    // ─── P1 · D2.9: el flujo de comentarios es de solo lectura ────────────

    @Test
    fun feedbacks_are_exposed_read_only() {
        val vm = vmFor(nextId())

        assertFalse("feedbacks no debe ser un MutableStateFlow público", vm.feedbacks is MutableStateFlow<*>)
        assertTrue(vm.feedbacks.value.isEmpty())
    }

    // ─── P1 · H-UI: tarjeta de progresión legible ─────────────────────────

    private fun nativeProposalProgram(id: String, notices: List<NativeProgressionResolution> = emptyList()): Program {
        val configurationId = "bench_press__dumbbell"
        val identity = NativeProgressionIdentity(
            recipeId = "hui-recipe",
            recipeContentVersion = 1,
            configurationId = configurationId,
            loadMode = LoadModeV2.LOAD,
            unitMode = UnitModeV2.REPS,
            side = "bilateral",
            execution = "standard",
            slotPurpose = "F",
            quantityConvention = LoadQuantityConvention.PER_IMPLEMENT,
        )
        val exercise = Exercise(
            id = "$id-ex",
            name = "Press de banca con mancuernas",
            catalogConfigurationId = configurationId,
            recipeDayId = "hui-day",
            recipeSlotId = "hui-slot",
        )
        val proposal = NativeProgressionProposal(
            proposalId = "$id-p1",
            kind = NativeProgressionProposalKind.INCREASE_LOAD,
            identity = identity,
            sourceSessionId = "${id}_s1",
            sourceLogIds = listOf("$id-l1", "$id-l2"),
            targetLoadKg = 22.0,
            explanation = com.example.kpkn.domain.training.NativeProgressionText.loadIncrease(
                times = 2,
                topReps = 12,
                fromKg = 20.0,
                toKg = 22.0,
                unit = "kg por mancuerna",
                assistance = false,
            ),
            createdAtMs = 1L,
        )
        return Program(
            id = id,
            name = "Progresión $id",
            structure = ProgramStructure.COMPLEX,
            macrocycles = listOf(
                Macrocycle(
                    id = "${id}_mc",
                    name = "Macro",
                    blocks = listOf(
                        Block(
                            id = "${id}_b",
                            name = "Bloque",
                            mesocycles = listOf(
                                Mesocycle(
                                    id = "${id}_m",
                                    name = "Meso",
                                    weeks = listOf(
                                        ProgramWeek(
                                            id = "${id}_w",
                                            name = "Semana 1",
                                            sessions = listOf(
                                                Session(id = "${id}_s1", name = "Día 1", exercises = listOf(exercise)),
                                                Session(
                                                    id = "${id}_s2",
                                                    name = "Día 2",
                                                    exercises = listOf(exercise.copy(id = "$id-ex2")),
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
            nativeProgressionProposals = listOf(proposal),
            nativeProgressionAudit = notices,
        )
    }

    @Test
    fun nativeProgressionCard_names_the_exercise_in_plain_language_without_internal_ids() {
        val id = nextId()
        repository.addProgram(nativeProposalProgram(id))
        val vm = vmFor(id)

        val card = vm.nativeProgressionCard.value

        assertEquals(1, card.pendingCount)
        assertEquals("1 progresión por revisar", card.pendingLabel)
        val item = card.proposals.single()
        assertEquals("Press de banca con mancuernas", item.title)
        assertEquals(
            "Lo hiciste dos veces con 12 reps en todas las series. Propuesta: subir de 20 a 22 kg por mancuerna.",
            item.body,
        )
        assertFalse("nunca se imprime el identificador de la configuración", (item.title + item.body).contains("__"))
        assertFalse((item.title + item.body).contains("hui-slot"))
    }

    @Test
    fun rejectNativeProgressionProposal_confirms_with_the_exercise_name() = runBlocking {
        val id = nextId()
        repository.addProgram(nativeProposalProgram(id))
        repository.flushPendingWrites()
        val vm = vmFor(id)

        vm.rejectNativeProgressionProposal("$id-p1")

        assertEquals(
            "Propuesta rechazada. Press de banca con mancuernas se queda como está.",
            awaitSnackbar(vm),
        )
        val audit = repository.getProgramById(id)!!.nativeProgressionAudit.single()
        assertEquals(NativeProgressionResolutionStatus.REJECTED, audit.status)
        assertFalse("rechazar no deja un aviso pendiente de leer", audit.userFacingNotice)
        val card = withTimeout(5_000) { vm.nativeProgressionCard.first { it.proposals.isEmpty() } }
        assertTrue(card.notices.isEmpty())
    }

    @Test
    fun acceptNativeProgressionProposal_that_no_longer_applies_explains_why_and_the_notice_can_be_dismissed() = runBlocking {
        val id = nextId()
        // Sin receta de origen la propuesta ya no tiene a qué aplicarse: caduca sin tocar el plan.
        repository.addProgram(nativeProposalProgram(id))
        repository.flushPendingWrites()
        val vm = vmFor(id)

        vm.acceptNativeProgressionProposal("$id-p1")

        val message = awaitSnackbar(vm)
        assertTrue(message, message.startsWith("No se aplicó la propuesta de Press de banca con mancuernas. "))
        assertEquals(SnackbarType.SUGGESTION, snackbarTypeFor(message))
        val card = withTimeout(5_000) { vm.nativeProgressionCard.first { it.notices.isNotEmpty() } }
        assertEquals("Press de banca con mancuernas", card.notices.single().title)
        assertTrue(card.proposals.isEmpty())

        vm.dismissNativeProgressionNotice("$id-p1")

        withTimeout(5_000) { vm.nativeProgressionCard.first { it.notices.isEmpty() } }
        val audit = repository.getProgramById(id)!!.nativeProgressionAudit.single()
        assertEquals("el registro se conserva", NativeProgressionResolutionStatus.EXPIRED, audit.status)
        assertFalse(audit.userFacingNotice)
    }

    @Test
    fun snackbarTypeFor_marks_refusals_red_and_expired_proposals_as_suggestions() {
        assertEquals(SnackbarType.DANGER, snackbarTypeFor("No se pudo aplicar la plantilla. Intenta de nuevo."))
        assertEquals(SnackbarType.DANGER, snackbarTypeFor(ProgramDetailViewModel.SESSION_IN_PROGRESS_MESSAGE))
        assertEquals(SnackbarType.DANGER, snackbarTypeFor(ProgramDetailViewModel.SNAPSHOT_REQUIRED_MESSAGE))
        assertEquals(
            SnackbarType.SUGGESTION,
            snackbarTypeFor("No se aplicó la propuesta de Remo. Ya no quedan sesiones sin entrenar donde aplicar la propuesta."),
        )
        assertEquals(SnackbarType.SUCCESS, snackbarTypeFor("Propuesta rechazada. Remo se queda como está."))
    }

    // ─── B.S5 · R-04 y R-19: «Guardar TM» y el test de 1RM recalculan las semanas pendientes ───

    private class TmIds : IdProvider {
        private var next = 0
        override fun newId(): String = "tm-${++next}"
    }

    private fun wendlerRecipe(): TrainingPlanRecipe = PROTOCOL_LIBRARY.first { it.id == "wendler-531-bbb" }.recipe!!

    /** 5/3/1 BBB de autor (Simple cíclico de cuatro semanas) con el cursor en la primera semana del ciclo 1. */
    private fun wendlerProgram(id: String, profile: PowerliftingProfile): Program {
        val materialized = PlanMaterializer.materialize(
            Program(id = id, name = "5/3/1 $id"),
            wendlerRecipe(),
            CatalogCompositionTestSupport.metadata,
            TmIds(),
            profile = profile,
        )
        val first = weeksOf(materialized).first()
        return materialized.copy(
            runState = ProgramRunState(
                runId = "$id-run",
                cycleNumber = 1,
                weekId = first.id,
                weekInstanceId = ProgramProgressEngine.instanceIdFor(1, first.id),
            ),
        )
    }

    /** Un registro por sesión de [week]: la semana queda entrenada. */
    private fun trainedLogs(program: Program, week: ProgramWeek): List<WorkoutLog> = week.sessions.map { session ->
        WorkoutLog(
            id = "tm-log-${session.id}",
            programId = program.id,
            sessionId = session.id,
            sessionName = session.name,
            date = "2026-09-30T10:00:00Z",
            durationMinutes = 45,
            weekId = week.id,
            weekInstanceId = ProgramProgressEngine.instanceIdFor(1, week.id),
            cycleNumber = 1,
            programRunId = program.runState?.runId,
        )
    }

    /** Peso de la primera serie del T1 de [configurationId] en [week]. */
    private fun t1Weight(week: ProgramWeek, configurationId: String): Double =
        week.sessions.flatMap { it.allExercises() }
            .first { it.slotRole == SlotRole.T1_MAIN && it.catalogConfigurationId == configurationId }
            .sets.first().weight!!

    /** Lo que devuelve el asistente de TM: solo 1RM con su TM hidratado y las variantes por defecto. */
    private fun wizardProfile(squat: Double?, bench: Double?, deadlift: Double?, percent: Double = 0.90) =
        TrainingMaxResolver.hydrateProfile(
            PowerliftingProfile(squat1RM = squat, bench1RM = bench, deadlift1RM = deadlift),
            percent,
        )

    @Test
    fun updatePowerliftingProfile_merges_the_profile_and_recalculates_only_the_weeks_not_trained() = runBlocking {
        val id = nextId()
        // La banca trae un TM ajustado por una propuesta (110 en lugar de 108); la sentadilla, barra alta.
        val stored = PowerliftingProfile(
            squat1RM = 200.0, bench1RM = 120.0, deadlift1RM = 220.0,
            squatTM = 180.0, benchTM = 110.0, deadliftTM = 198.0,
            squatVariant = SquatVariant.HIGH_BAR,
        )
        val program = wendlerProgram(id, stored)
        repository.addProgram(program)
        trainedLogs(program, weeksOf(program).first()).forEach { repository.addWorkoutLog(it) }
        repository.flushPendingWrites()
        val before = repository.getProgramById(id)!!
        val vm = vmFor(id)

        // Mismo 1RM de banca y de peso muerto; otra sentadilla.
        vm.updatePowerliftingProfile(wizardProfile(squat = 210.0, bench = 120.0, deadlift = 220.0))

        assertEquals("TM actualizado: 3 semanas recalculadas, 1 entrenada intacta", awaitSnackbar(vm))
        val saved = repository.getProgramById(id)!!
        val profile = saved.powerliftingProfile!!
        assertEquals(210.0, profile.squat1RM!!, 0.0)
        assertEquals("la sentadilla recalcula su TM", 189.0, profile.squatTM!!, 1e-9)
        assertEquals("la banca conserva el TM ajustado", 110.0, profile.benchTM!!, 1e-9)
        assertEquals(198.0, profile.deadliftTM!!, 1e-9)
        assertEquals("las variantes del atleta se conservan", SquatVariant.HIGH_BAR, profile.squatVariant)

        val weeksBefore = weeksOf(before)
        val weeksAfter = weeksOf(saved)
        assertEquals(4, weeksAfter.size)
        assertEquals(
            "la semana entrenada queda tal cual",
            weeksBefore.first().sessions.map { sessionJson(it) },
            weeksAfter.first().sessions.map { sessionJson(it) },
        )
        // Primera serie del T1 de cada semana: 65, 70, 75 y 40 % del TM.
        listOf(65.0, 70.0, 75.0, 40.0).forEachIndexed { index, percent ->
            val label = "semana ${index + 1}"
            val squatTm = if (index == 0) 180.0 else 189.0
            assertEquals("sentadilla $label", percent / 100.0 * squatTm, t1Weight(weeksAfter[index], CatalogIds.SQ_LOW), 1e-6)
            assertEquals("banca $label", percent / 100.0 * 110.0, t1Weight(weeksAfter[index], CatalogIds.BP), 1e-6)
            assertEquals("peso muerto $label", percent / 100.0 * 198.0, t1Weight(weeksAfter[index], CatalogIds.DL), 1e-6)
        }
        assertTrue(saved.macrocycles.flatMap { it.blocks }.none { it.materializationPending })
    }

    @Test
    fun updatePowerliftingProfile_with_nothing_trained_recalculates_every_week_of_the_recipe() = runBlocking {
        val id = nextId()
        val program = wendlerProgram(id, PowerliftingProfile(squat1RM = 200.0, bench1RM = 120.0, deadlift1RM = 220.0))
        repository.addProgram(program)
        repository.flushPendingWrites()
        val vm = vmFor(id)

        vm.updatePowerliftingProfile(wizardProfile(squat = 210.0, bench = 125.0, deadlift = 230.0))

        // Sin nada entrenado se recalculan las cuatro semanas y no hay «entrenadas intactas» que contar.
        assertEquals("TM actualizado: 4 semanas recalculadas", awaitSnackbar(vm))
        val firstWeek = weeksOf(repository.getProgramById(id)!!).first()
        assertEquals(0.65 * 189.0, t1Weight(firstWeek, CatalogIds.SQ_LOW), 1e-6)
        assertEquals(0.65 * 112.5, t1Weight(firstWeek, CatalogIds.BP), 1e-6)
        assertEquals(0.65 * 207.0, t1Weight(firstWeek, CatalogIds.DL), 1e-6)
    }

    @Test
    fun updatePowerliftingProfile_never_rebuilds_the_loop_weeks_the_athlete_added() = runBlocking {
        val id = nextId()
        val withLoop = LoopEngine.upsertLoop(
            wendlerProgram(id, PowerliftingProfile(squat1RM = 200.0, bench1RM = 120.0, deadlift1RM = 220.0)),
            Loop(id = "comp", title = "Competición", type = LoopType.COMPETITION, repeatEveryXLoops = 12),
        )
        assertEquals("cuatro semanas de la receta y la de loop", 5, weeksOf(withLoop).size)
        repository.addProgram(withLoop)
        repository.flushPendingWrites()
        val loopWeekBefore = weeksOf(repository.getProgramById(id)!!).single { it.isLoopWeek }
        val vm = vmFor(id)

        vm.updatePowerliftingProfile(wizardProfile(squat = 210.0, bench = 120.0, deadlift = 220.0))

        // La semana de loop no viene de la receta: ni se reconstruye ni se cuenta como recalculada.
        assertEquals("TM actualizado: 4 semanas recalculadas", awaitSnackbar(vm))
        val after = weeksOf(repository.getProgramById(id)!!)
        assertEquals(loopWeekBefore, after.single { it.isLoopWeek })
        assertEquals(0.65 * 189.0, t1Weight(after.first(), CatalogIds.SQ_LOW), 1e-6)
    }

    @Test
    fun updatePowerliftingProfile_leaves_the_auge_deload_block_alone() = runBlocking {
        val id = nextId()
        val materialized = PlanMaterializer.materialize(
            Program(id = id, name = "Test $id"),
            oneRmGateRecipe(),
            CatalogCompositionTestSupport.metadata,
            TmIds(),
            profile = PowerliftingProfile(squat1RM = 200.0, bench1RM = 120.0, deadlift1RM = 220.0),
            strict = false,
        )
        // La «Descarga (auto)» que inserta AUGE entre el pico y la ola no viene de la receta.
        val (withDeload, deloadBlockId) =
            com.example.kpkn.domain.training.appendExternalBlockFixture(materialized, materialized.macrocycles.first().blocks[0].id)!!
        repository.addProgram(withDeload)
        repository.flushPendingWrites()
        val before = repository.getProgramById(id)!!
        val vm = vmFor(id)

        vm.updatePowerliftingProfile(wizardProfile(squat = 210.0, bench = 125.0, deadlift = 230.0))

        // El pico (1 semana) y la ola (2): tres semanas de la receta; la descarga no cuenta.
        assertEquals("TM actualizado: 3 semanas recalculadas", awaitSnackbar(vm))
        val after = repository.getProgramById(id)!!
        assertEquals(
            before.macrocycles.first().blocks.first { it.id == deloadBlockId },
            after.macrocycles.first().blocks.first { it.id == deloadBlockId },
        )
        val recipeWeeks = after.macrocycles.first().blocks.filter { it.id != deloadBlockId }
            .flatMap { block -> block.mesocycles.flatMap { it.weeks } }
        assertEquals(3, recipeWeeks.size)
        recipeWeeks.forEach { week -> assertEquals(0.70 * 189.0, t1Weight(week, CatalogIds.SQ_LOW), 1e-6) }
    }

    @Test
    fun updatePowerliftingProfile_for_a_program_that_no_longer_exists_reports_a_failure_in_red() = runBlocking {
        val vm = vmFor(nextId())

        vm.updatePowerliftingProfile(wizardProfile(squat = 200.0, bench = 120.0, deadlift = 220.0))

        val message = awaitSnackbar(vm)
        assertEquals("No se pudo guardar el TM. El plan y el historial se conservaron.", message)
        assertEquals(SnackbarType.DANGER, snackbarTypeFor(message))
    }

    @Test
    fun updatePowerliftingProfile_without_a_recipe_saves_only_the_profile() = runBlocking {
        val id = nextId()
        repository.addProgram(makeSimpleProgram(id))
        repository.flushPendingWrites()
        val before = repository.getProgramById(id)!!
        val vm = vmFor(id)

        vm.updatePowerliftingProfile(wizardProfile(squat = 200.0, bench = 120.0, deadlift = 220.0))

        assertEquals("TM actualizado", awaitSnackbar(vm))
        val saved = repository.getProgramById(id)!!
        assertEquals(180.0, saved.powerliftingProfile!!.squatTM!!, 1e-9)
        assertEquals(108.0, saved.powerliftingProfile!!.benchTM!!, 1e-9)
        assertEquals("el plan no cambia", weeksOf(before), weeksOf(saved))
    }

    @Test
    fun updatePowerliftingProfile_preserves_reference_and_prescription_when_rebuilding_fails() = runBlocking {
        val id = nextId()
        val program = wendlerProgram(id, PowerliftingProfile(squat1RM = 200.0, bench1RM = 120.0, deadlift1RM = 220.0))
        repository.addProgram(program)
        repository.flushPendingWrites()
        val before = repository.getProgramById(id)!!
        val vm = vmFor(id)

        val installed = CompositionMetadataHolder.current
        CompositionMetadataHolder.current = null
        val message = try {
            vm.updatePowerliftingProfile(wizardProfile(squat = 210.0, bench = 120.0, deadlift = 220.0))
            awaitSnackbar(vm)
        } finally {
            CompositionMetadataHolder.current = installed
        }

        assertEquals("No se pudo guardar el TM. El plan y el historial se conservaron.", message)
        val saved = repository.getProgramById(id)!!
        assertEquals("la referencia anterior se conserva", before.powerliftingProfile, saved.powerliftingProfile)
        assertEquals("las semanas no se tocaron", weeksOf(before), weeksOf(saved))
        assertEquals("no aparece una captura parcialmente aplicada", before.exerciseLoadReferences, saved.exerciseLoadReferences)
    }

    @Test
    fun trainingMaxSavedMessage_agrees_in_number_and_stays_a_success() {
        assertEquals("TM actualizado", ProgramDetailViewModel.trainingMaxSavedMessage(0, 0))
        assertEquals("TM actualizado: 1 semana recalculada", ProgramDetailViewModel.trainingMaxSavedMessage(1, 0))
        assertEquals("TM actualizado: 4 semanas recalculadas", ProgramDetailViewModel.trainingMaxSavedMessage(4, 0))
        assertEquals(
            "TM actualizado: 3 semanas recalculadas, 1 entrenada intacta",
            ProgramDetailViewModel.trainingMaxSavedMessage(3, 1),
        )
        assertEquals(
            "TM actualizado: 1 semana recalculada, 2 entrenadas intactas",
            ProgramDetailViewModel.trainingMaxSavedMessage(1, 2),
        )
        assertEquals(
            "TM actualizado: 0 semanas recalculadas, 3 entrenadas intactas",
            ProgramDetailViewModel.trainingMaxSavedMessage(0, 3),
        )
        assertEquals(SnackbarType.SUCCESS, snackbarTypeFor(ProgramDetailViewModel.trainingMaxSavedMessage(3, 1)))
    }

    // El test de 1RM llega al final de un bloque de pico; la siguiente ola debe usar los 1RM nuevos.

    private fun oneRmGateRecipe(): TrainingPlanRecipe {
        fun gateDay() = day(
            "Día A",
            weekday = 1,
            slots = listOf(
                slot("sq", SlotRole.T1_MAIN, CatalogIds.SQ_LOW, percentSets(180, 5 to 70.0, 5 to 70.0), 180, LiftSlot.SQUAT, isCompetitionLift = true),
                slot("bp", SlotRole.T1_MAIN, CatalogIds.BP, percentSets(180, 5 to 70.0, 5 to 70.0), 180, LiftSlot.BENCH, isCompetitionLift = true),
            ),
        )
        return TrainingPlanRecipe(
            id = "one-rm-gate",
            weeks = listOf(
                weekRecipe(1, 0, "Pico", BlockGoal.REALIZATION, listOf(gateDay())),
                weekRecipe(2, 1, "Ola", BlockGoal.ACCUMULATION, listOf(gateDay())),
                weekRecipe(3, 1, "Ola", BlockGoal.ACCUMULATION, listOf(gateDay())),
            ),
            trainingMaxPercent = 0.90,
            liftSlots = mapOf(LiftSlot.SQUAT to CatalogIds.SQ_LOW, LiftSlot.BENCH to CatalogIds.BP),
        )
    }

    /** Un bloque de pico de una semana y otro de ola de dos, con el test de 1RM pendiente antes de la ola. */
    private fun oneRmGateProgram(id: String): Program {
        val materialized = PlanMaterializer.materialize(
            Program(id = id, name = "Test $id"),
            oneRmGateRecipe(),
            CatalogCompositionTestSupport.metadata,
            TmIds(),
            profile = PowerliftingProfile(squat1RM = 200.0, bench1RM = 120.0, deadlift1RM = 220.0),
            strict = false,
        )
        val first = weeksOf(materialized).first()
        return materialized.copy(
            runState = ProgramRunState(
                runId = "$id-run",
                cycleNumber = 1,
                weekId = first.id,
                weekInstanceId = first.id,
                pendingAction = PendingProgramAction(
                    type = PendingProgramActionType.CONFIRM_1RM_TEST,
                    message = "Test de 1RM",
                    nextBlockId = materialized.macrocycles.first().blocks[1].id,
                ),
            ),
        )
    }

    @Test
    fun recordPendingOneRmTest_updates_the_profile_and_recalculates_the_weeks_after_the_test() = runBlocking {
        val id = nextId()
        val program = com.example.kpkn.domain.training.ExerciseTrainingMax.migrate(oneRmGateProgram(id))
        repository.addProgram(program)
        trainedLogs(program, weeksOf(program).first()).forEach { repository.addWorkoutLog(it) }
        repository.flushPendingWrites()
        assertEquals(
            PendingProgramActionType.CONFIRM_1RM_TEST,
            repository.getProgramById(id)!!.runState?.pendingAction?.type,
        )
        val vm = vmFor(id)

        vm.recordPendingOneRmTest(210.0, 125.0, 230.0)

        assertEquals("1RM registrado. TM actualizado: 2 semanas recalculadas, 1 entrenada intacta", awaitSnackbar(vm))
        val saved = repository.getProgramById(id)!!
        val profile = saved.powerliftingProfile!!
        assertEquals(189.0, profile.squatTM!!, 1e-9)
        assertEquals(112.5, profile.benchTM!!, 1e-9)
        assertEquals(207.0, profile.deadliftTM!!, 1e-9)
        assertEquals(210.0, saved.goals?.squat1RM)
        assertNull(saved.runState?.pendingAction)
        val weeks = weeksOf(saved)
        assertEquals("el pico, ya entrenado, conserva su carga", 0.70 * 180.0, t1Weight(weeks[0], CatalogIds.SQ_LOW), 1e-6)
        assertEquals(0.70 * 108.0, t1Weight(weeks[0], CatalogIds.BP), 1e-6)
        listOf(1, 2).forEach { index ->
            assertEquals("ola, semana $index", 0.70 * 189.0, t1Weight(weeks[index], CatalogIds.SQ_LOW), 1e-6)
            assertEquals(0.70 * 112.5, t1Weight(weeks[index], CatalogIds.BP), 1e-6)
        }
        val exercise = com.example.kpkn.domain.training.ExerciseTrainingMax.exercises(saved)
            .first { it.catalogConfigurationId == CatalogIds.SQ_LOW }
        val reference = com.example.kpkn.domain.training.ExerciseTrainingMax.resolved(saved, exercise,
            com.example.kpkn.data.protocols.PlanLoadReferenceKind.EXERCISE_1RM)!!
        assertEquals(210.0, reference.capturedLoadKg!!, 0.0)
        assertEquals("TEST", reference.metadata!!.origin)
        assertNotNull(reference.capturedAtMs)
        assertTrue(reference.metadata!!.reason!!.contains("test"))
        assertTrue(saved.macrocycles.flatMap { it.blocks }.none { it.materializationPending })
    }

    @Test
    fun recordPendingOneRmTest_failureKeepsProfilePoolCursorAndWeeksThenRetrySucceeds() = runBlocking {
        val id = nextId()
        repository.addProgram(com.example.kpkn.domain.training.ExerciseTrainingMax.migrate(oneRmGateProgram(id)))
        repository.flushPendingWrites()
        val before = repository.getProgramById(id)!!
        val vm = vmFor(id)
        val installed = CompositionMetadataHolder.current
        CompositionMetadataHolder.current = null
        try {
            vm.recordPendingOneRmTest(210.0, 125.0, 230.0)
            assertTrue(awaitSnackbar(vm).startsWith("No se pudo guardar el test"))
        } finally { CompositionMetadataHolder.current = installed }
        assertEquals(before, repository.getProgramById(id))
        assertEquals(PendingProgramActionType.CONFIRM_1RM_TEST, repository.getProgramById(id)!!.runState!!.pendingAction!!.type)
        vm.consumeSnackbarMessage()
        vm.recordPendingOneRmTest(210.0, 125.0, 230.0)
        assertTrue(awaitSnackbar(vm).startsWith("1RM registrado"))
        assertEquals(189.0, repository.getProgramById(id)!!.powerliftingProfile!!.squatTM!!, 0.0)
        assertNull(repository.getProgramById(id)!!.runState!!.pendingAction)
    }

    @Test
    fun recordPendingOneRmTest_roomFailureNeverPublishesAndRetryCommitsTheSameTest() = runBlocking {
        val id = nextId()
        repository.addProgram(com.example.kpkn.domain.training.ExerciseTrainingMax.migrate(oneRmGateProgram(id)))
        repository.flushPendingWrites()
        val before = repository.getProgramById(id)!!
        val beforeRow = repository.databaseForTests().programDao().getById(id)!!
        val vm = vmFor(id)
        val db = repository.databaseForTests().openHelper.writableDatabase
        db.execSQL("CREATE TRIGGER fail_test_insert BEFORE INSERT ON programs BEGIN SELECT RAISE(ABORT, 'test'); END")
        db.execSQL("CREATE TRIGGER fail_test_update BEFORE UPDATE ON programs BEGIN SELECT RAISE(ABORT, 'test'); END")
        vm.recordPendingOneRmTest(210.0,125.0,230.0)
        assertTrue(awaitSnackbar(vm).startsWith("No se pudo guardar el test"))
        assertEquals(before,repository.getProgramById(id))
        assertEquals(beforeRow,repository.databaseForTests().programDao().getById(id))
        db.execSQL("DROP TRIGGER fail_test_insert"); db.execSQL("DROP TRIGGER fail_test_update")
        vm.consumeSnackbarMessage()
        vm.recordPendingOneRmTest(210.0,125.0,230.0)
        assertTrue(awaitSnackbar(vm).startsWith("1RM registrado"))
        assertEquals(210.0,repository.getProgramById(id)!!.powerliftingProfile!!.squat1RM!!,0.0)
        assertNotEquals(beforeRow,repository.databaseForTests().programDao().getById(id))
    }

    @Test
    fun perExerciseTmAwaitFailureKeepsValuePoolAndWeeksAndRetryIsDurable() = runBlocking {
        val id = nextId()
        repository.addProgram(com.example.kpkn.domain.training.ExerciseTrainingMax.migrate(oneRmGateProgram(id)))
        repository.flushPendingWrites()
        val before = repository.getProgramById(id)!!
        val exercise = com.example.kpkn.domain.training.ExerciseTrainingMax.exercises(before).first()
        val vm = vmFor(id)
        val installed = CompositionMetadataHolder.current
        CompositionMetadataHolder.current = null
        try { assertTrue(vm.updateExerciseTrainingMaxAndAwait(exercise.id,195.0).isFailure) }
        finally { CompositionMetadataHolder.current = installed }
        assertEquals(before,repository.getProgramById(id))
        val db = repository.databaseForTests().openHelper.writableDatabase
        db.execSQL("CREATE TRIGGER fail_detail_insert BEFORE INSERT ON programs BEGIN SELECT RAISE(ABORT, 'detail'); END")
        db.execSQL("CREATE TRIGGER fail_detail_update BEFORE UPDATE ON programs BEGIN SELECT RAISE(ABORT, 'detail'); END")
        assertTrue(vm.updateExerciseTrainingMaxAndAwait(exercise.id,195.0).isFailure)
        assertEquals(before,repository.getProgramById(id))
        db.execSQL("DROP TRIGGER fail_detail_insert"); db.execSQL("DROP TRIGGER fail_detail_update")
        assertTrue(vm.updateExerciseTrainingMaxAndAwait(exercise.id,195.0).isSuccess)
        val after = repository.getProgramById(id)!!
        assertEquals(195.0,com.example.kpkn.domain.training.ExerciseTrainingMax.capturedKg(after,exercise,
            com.example.kpkn.data.protocols.PlanLoadReferenceKind.EXERCISE_TM)!!,0.0)
        val row = repository.databaseForTests().programDao().getById(id)!!
        assertTrue(row.data.contains("195"))
        assertNotEquals(before.exerciseLoadReferences,after.exerciseLoadReferences)
    }

    @Test
    fun skipPendingOneRmTest_leaves_the_profile_and_the_weeks_alone() = runBlocking {
        val id = nextId()
        repository.addProgram(oneRmGateProgram(id))
        repository.flushPendingWrites()
        val before = repository.getProgramById(id)!!
        val vm = vmFor(id)

        vm.skipPendingOneRmTest()

        val saved = repository.getProgramById(id)!!
        assertNull(saved.runState?.pendingAction)
        assertEquals(before.powerliftingProfile, saved.powerliftingProfile)
        assertEquals(weeksOf(before), weeksOf(saved))
        assertTrue(saved.macrocycles.flatMap { it.blocks }.none { it.materializationPending })
        assertNull("saltar el test no avisa de ningún TM", vm.uiState.value.snackbarMessage)
    }

    @Test
    fun rematerializePending_rebuilds_only_the_pending_blocks_and_clears_their_flag() = runBlocking {
        val id = nextId()
        val gate = oneRmGateProgram(id)
        val waveId = gate.macrocycles.first().blocks[1].id
        // El TM de sentadilla subió a 189 y solo la ola quedó pendiente de materializar.
        val pending = gate.copy(
            powerliftingProfile = gate.powerliftingProfile!!.copy(squatTM = 189.0),
            macrocycles = gate.macrocycles.map { macro ->
                macro.copy(blocks = macro.blocks.map { if (it.id == waveId) it.copy(materializationPending = true) else it })
            },
        )
        repository.addProgram(pending)
        repository.flushPendingWrites()
        val vm = vmFor(id)

        assertTrue(vm.rematerializePendingAndAwait().isSuccess)

        val weeks = weeksOf(repository.getProgramById(id)!!)
        assertEquals("el bloque que no estaba pendiente no se reconstruye", 0.70 * 180.0, t1Weight(weeks[0], CatalogIds.SQ_LOW), 1e-6)
        assertEquals(0.70 * 189.0, t1Weight(weeks[1], CatalogIds.SQ_LOW), 1e-6)
        assertEquals(0.70 * 189.0, t1Weight(weeks[2], CatalogIds.SQ_LOW), 1e-6)
        assertTrue(repository.getProgramById(id)!!.macrocycles.flatMap { it.blocks }.none { it.materializationPending })
    }

    @Test
    fun rematerializePending_reports_a_failure_in_red_and_keeps_the_plan_and_the_pending_flag() = runBlocking {
        val id = nextId()
        val gate = oneRmGateProgram(id)
        val waveId = gate.macrocycles.first().blocks[1].id
        val pending = gate.copy(
            macrocycles = gate.macrocycles.map { macro ->
                macro.copy(blocks = macro.blocks.map { if (it.id == waveId) it.copy(materializationPending = true) else it })
            },
        )
        repository.addProgram(pending)
        repository.flushPendingWrites()
        val before = repository.getProgramById(id)!!
        val vm = vmFor(id)

        val installed = CompositionMetadataHolder.current
        CompositionMetadataHolder.current = null
        val message = try {
            vm.rematerializePending()
            awaitSnackbar(vm)
        } finally {
            CompositionMetadataHolder.current = installed
        }

        assertEquals("No se pudo recalcular lo pendiente del plan. El plan y el historial se conservaron.", message)
        assertEquals("el aviso se pinta como fallo", SnackbarType.DANGER, snackbarTypeFor(message))
        assertEquals("el plan no cambió", before, repository.getProgramById(id))
    }

    @Test
    fun rematerializePending_roomFailurePreservesProfilePoolWeeksAndFlagThenRetryCommits() = runBlocking {
        val id = nextId()
        val original = oneRmGateProgram(id)
        val waveId = original.macrocycles.first().blocks[1].id
        repository.addProgram(original.copy(
            powerliftingProfile = original.powerliftingProfile!!.copy(squatTM = 189.0),
            macrocycles = original.macrocycles.map { macro -> macro.copy(blocks = macro.blocks.map {
                if (it.id == waveId) it.copy(materializationPending = true) else it
            }) },
        ))
        repository.flushPendingWrites()
        val before = repository.getProgramById(id)!!
        val beforeRow = repository.databaseForTests().programDao().getById(id)
        val vm = vmFor(id)
        val db = repository.databaseForTests().openHelper.writableDatabase
        db.execSQL("CREATE TRIGGER fail_pending_insert BEFORE INSERT ON programs BEGIN SELECT RAISE(ABORT, 'pending'); END")
        db.execSQL("CREATE TRIGGER fail_pending_update BEFORE UPDATE ON programs BEGIN SELECT RAISE(ABORT, 'pending'); END")
        vm.rematerializePending()
        val message = awaitSnackbar(vm)
        assertEquals("No se pudo recalcular lo pendiente del plan. El plan y el historial se conservaron.", message)
        assertEquals(SnackbarType.DANGER, snackbarTypeFor(message))
        assertEquals(before, repository.getProgramById(id))
        assertEquals(beforeRow, repository.databaseForTests().programDao().getById(id))
        db.execSQL("DROP TRIGGER fail_pending_insert")
        db.execSQL("DROP TRIGGER fail_pending_update")
        assertTrue(vm.rematerializePendingAndAwait().isSuccess)
        val saved = repository.getProgramById(id)!!
        assertTrue(saved.macrocycles.flatMap { it.blocks }.none { it.materializationPending })
        assertEquals(0.70 * 180.0, t1Weight(weeksOf(saved)[0], CatalogIds.SQ_LOW), 1e-6)
        assertEquals(0.70 * 189.0, t1Weight(weeksOf(saved)[1], CatalogIds.SQ_LOW), 1e-6)
        assertEquals(0.70 * 189.0, t1Weight(weeksOf(saved)[2], CatalogIds.SQ_LOW), 1e-6)
        assertNotEquals(beforeRow, repository.databaseForTests().programDao().getById(id))
    }

    // ─── B.S6 parte 2a-bis: las puertas de bloque leen los registros y el inventario reales ───

    /**
     * Dos olas de dos semanas con la subida del método por bloque (`CycleIncrement` con `BLOCK`). Con
     * [benchAmrap] la última serie de banca es AMRAP; con [firstWaveGoal] = REALIZATION la ola 1 cierra con
     * la puerta del test de 1RM.
     */
    private fun waveRecipe(firstWaveGoal: BlockGoal, benchAmrap: Boolean): TrainingPlanRecipe {
        fun waveDay() = day(
            "Día A",
            weekday = 1,
            slots = listOf(
                slot("sq", SlotRole.T1_MAIN, CatalogIds.SQ_LOW, percentSets(180, 5 to 70.0, 5 to 70.0), 180, LiftSlot.SQUAT, isCompetitionLift = true),
                slot("bp", SlotRole.T1_MAIN, CatalogIds.BP, percentSets(180, 5 to 70.0, 5 to 70.0, amrapLast = benchAmrap), 180, LiftSlot.BENCH, isCompetitionLift = true),
            ),
        )
        return TrainingPlanRecipe(
            id = "vm-waves",
            weeks = listOf(
                weekRecipe(1, 0, "Ola 1", firstWaveGoal, listOf(waveDay())),
                weekRecipe(2, 0, "Ola 1", firstWaveGoal, listOf(waveDay())),
                weekRecipe(3, 1, "Ola 2", BlockGoal.INTENSIFICATION, listOf(waveDay())),
                weekRecipe(4, 1, "Ola 2", BlockGoal.INTENSIFICATION, listOf(waveDay())),
            ),
            trainingMaxPercent = 0.90,
            liftSlots = mapOf(LiftSlot.SQUAT to CatalogIds.SQ_LOW, LiftSlot.BENCH to CatalogIds.BP),
            progression = ProgressionRule.CycleIncrement(2.5, 5.0, IncrementScope.BLOCK),
        )
    }

    /** Registro de la sesión de [week] con el T1 de [configurationId]: las series del plan y [amrapReps] en la AMRAP. */
    private fun amrapLog(program: Program, week: ProgramWeek, configurationId: String, amrapReps: Int): WorkoutLog {
        val t1 = week.sessions.single().allExercises().first {
            it.slotRole == SlotRole.T1_MAIN && it.catalogConfigurationId == configurationId
        }
        return trainedLogs(program, week).single().copy(
            completedExercises = listOf(
                CompletedExercise(
                    exerciseId = t1.id,
                    exerciseName = t1.name,
                    catalogConfigurationId = t1.catalogConfigurationId,
                    sets = t1.sets.mapIndexed { index, planned ->
                        CompletedSet(
                            id = "set$index",
                            weight = planned.weight ?: 60.0,
                            reps = if (planned.purpose.isAmrap) amrapReps else planned.targetReps ?: 5,
                            amrapPerformed = planned.purpose.isAmrap,
                        )
                    },
                ),
            ),
        )
    }

    /**
     * Las dos olas con la ola 1 entrenada y la puerta que levanta el motor al cerrarla: una descarga de AUGE si
     * [stress] es alto, o el test de 1RM si [firstWaveGoal] es REALIZATION. Con [shortBenchReps] la ola 1 lleva
     * una AMRAP de banca que se queda en esas repeticiones donde el plan pedía 5. Devuelve el programa con la
     * puerta pendiente y los registros de la ola 1. Con el [profile] por defecto los TM son 180 / 108.
     */
    private fun waveGate(
        id: String,
        firstWaveGoal: BlockGoal = BlockGoal.ACCUMULATION,
        stress: Boolean = false,
        shortBenchReps: Int? = null,
        profile: PowerliftingProfile = PowerliftingProfile(squat1RM = 200.0, bench1RM = 120.0),
    ): Pair<Program, List<WorkoutLog>> {
        val materialized = PlanMaterializer.materialize(
            Program(id = id, name = "Olas $id"),
            waveRecipe(firstWaveGoal, benchAmrap = shortBenchReps != null),
            CatalogCompositionTestSupport.metadata,
            TmIds(),
            profile = profile,
            strict = false,
        )
        val weeks = weeksOf(materialized)
        val atLastWeek = materialized.copy(
            runState = ProgramRunState(
                runId = "$id-run",
                cycleNumber = 1,
                weekId = weeks[1].id,
                weekInstanceId = weeks[1].id,
            ),
        )
        val logs = weeks.take(2).flatMap { week ->
            if (shortBenchReps != null && week.id == weeks[0].id) {
                listOf(amrapLog(atLastWeek, week, CatalogIds.BP, shortBenchReps))
            } else {
                trainedLogs(atLastWeek, week)
            }
        }
        val gate = ProgramProgressEngine.advanceAfterSessionComplete(
            program = atLastWeek,
            activeState = null,
            completedSession = weeks[1].sessions.last(),
            weekInstanceId = weeks[1].id,
            logs = logs,
            transitionContext = if (stress) BlockTransitionEngine.TransitionContext() else null,
        )
        return gate.program to logs
    }

    /** Los discos de 1,25 kg hacen que el TM se redondee a múltiplos de 2,5 kg. */
    private fun plates125() = EquipmentInventory(
        plates = listOf(PlateStock(1.25, 2), PlateStock(2.5, 2), PlateStock(5.0, 2), PlateStock(20.0, 2)),
    )

    private fun userNoticeOf(program: Program): String =
        program.nativeProgressionAudit.single { it.userFacingNotice }.reason

    @Test
    fun recordPendingOneRmTest_reads_the_history_and_the_plates_for_the_block_increment() = runBlocking {
        val id = nextId()
        repository.updateSettings { it.copy(equipmentInventory = plates125()) }
        val (gate, logs) = waveGate(id, firstWaveGoal = BlockGoal.REALIZATION, shortBenchReps = 3)
        assertEquals(PendingProgramActionType.CONFIRM_1RM_TEST, gate.runState?.pendingAction?.type)
        repository.addProgram(gate)
        logs.forEach { repository.addWorkoutLog(it) }
        repository.flushPendingWrites()
        val vm = vmFor(id)

        // 1RM de sentadilla 210 (TM 189); el de banca no cambia y conserva su TM de 108.
        vm.recordPendingOneRmTest(210.0, 120.0, 230.0)

        assertTrue(awaitSnackbar(vm).startsWith("1RM registrado"))
        val saved = repository.getProgramById(id)!!
        assertNull(saved.runState?.pendingAction)
        val profile = saved.powerliftingProfile!!
        // Discos de 1,25 kg: 189 + 5 = 194 se redondea a la rejilla de 2,5 kg (195). Sin el inventario sería 194.
        assertEquals(195.0, profile.squatTM!!, 1e-9)
        assertEquals("la banca se mantiene por el AMRAP corto de la ola que se cierra", 108.0, profile.benchTM!!, 1e-9)
        assertEquals(
            "Nuevo bloque: TM sentadilla 189 → 195 kg; banca se mantiene en 108 kg (AMRAP corto).",
            userNoticeOf(saved),
        )
        val weeks = weeksOf(saved)
        assertEquals(0.70 * 195.0, t1Weight(weeks[2], CatalogIds.SQ_LOW), 1e-6)
        assertEquals(0.70 * 108.0, t1Weight(weeks[2], CatalogIds.BP), 1e-6)
        assertTrue(saved.macrocycles.flatMap { it.blocks }.none { it.materializationPending })
    }

    @Test
    fun skipPendingOneRmTest_reads_the_history_and_the_plates_for_the_block_increment() = runBlocking {
        val id = nextId()
        repository.updateSettings { it.copy(equipmentInventory = plates125()) }
        // El TM de sentadilla (181) sube a 186, que no está en la rejilla de 2,5 kg.
        val profile = PowerliftingProfile(squat1RM = 200.0, bench1RM = 120.0, squatTM = 181.0)
        val (gate, logs) = waveGate(id, firstWaveGoal = BlockGoal.REALIZATION, shortBenchReps = 3, profile = profile)
        assertEquals(PendingProgramActionType.CONFIRM_1RM_TEST, gate.runState?.pendingAction?.type)
        repository.addProgram(gate)
        logs.forEach { repository.addWorkoutLog(it) }
        repository.flushPendingWrites()
        val vm = vmFor(id)

        vm.skipPendingOneRmTest()

        val saved = repository.getProgramById(id)!!
        assertNull(saved.runState?.pendingAction)
        val tm = saved.powerliftingProfile!!
        // El test omitido no cambia el TM; el método suma 5 y los discos de 1,25 kg lo llevan a la rejilla.
        assertEquals("181 + 5 = 186 se redondea a 185", 185.0, tm.squatTM!!, 1e-9)
        assertEquals("la banca se mantiene por el AMRAP corto de la ola que se cierra", 108.0, tm.benchTM!!, 1e-9)
        assertEquals(
            "Nuevo bloque: TM sentadilla 181 → 185 kg; banca se mantiene en 108 kg (AMRAP corto).",
            userNoticeOf(saved),
        )
    }

    // ─── B.S5 · R-03: «VER PROPUESTA» abre la hoja de progresiones y no acepta nada ───

    private fun programDetailScreenSource(): String {
        val relative = "main/java/com/example/kpkn/screens/programdetail/ProgramDetailScreen.kt"
        val file = listOf("src/$relative", "../$relative", "app/src/$relative", "android-native/app/src/$relative")
            .map(::File)
            .firstOrNull { it.exists() }
            ?: error("No se encontró ProgramDetailScreen.kt desde ${File(".").absolutePath}")
        return file.readText()
    }

    @Test
    fun verPropuesta_opens_the_progression_sheet_and_never_accepts_the_proposal() {
        val source = programDetailScreenSource()
        // El aviso de transición (módulo KPKN) enseña «Ver propuesta» en tipo frase; su acción principal abre la hoja de progresiones.
        val label = source.indexOf("isAutoreg -> \"Ver propuesta\"")
        assertTrue("el botón Ver propuesta existe", label > 0)
        val handler = source.substring(source.indexOf("onPrimary = {", label), source.indexOf("onSecondary", label))
        val branch = handler.substringAfter("isAutoreg ->").substringBefore("isDeload ->")

        assertTrue(branch, branch.contains("onOpenProposals()"))
        assertFalse(branch, branch.contains("setStructureSubTab"))
        assertFalse(branch, branch.contains("acceptAutoregulation"))
        assertFalse(branch, branch.contains("rejectAutoregulation"))
        assertTrue("«Ver propuesta» abre la hoja PROGRESSION", source.contains("onOpenProposals = { pendingDetail = ProgramPendingKind.PROGRESSION }"))
        // La decisión se toma en la hoja «Pendiente» (progresiones): la tarjeta de propuestas ya no vive en la pestaña Semana.
        val weekBranch = source.substringAfter("if (structureSubTab == StructureSubTab.SEMANA) {")
            .substringBefore("\n        } else {")
        assertTrue("TM se muestra en Semana", weekBranch.contains("ExerciseTrainingMaxPanel("))
        assertFalse("la tarjeta de propuestas sale de Semana", weekBranch.contains("AutoregulationProposalsCard("))
        assertTrue("las propuestas se revisan en la hoja Pendiente", source.contains("ProgramPendingKind.PROGRESSION -> AutoregulationProposalsCard("))
    }

    // ─── B.S5 · usesTrainingMax: la tarjeta de TM sale con toda progresión que lo mueve ───

    private fun recipeWith(
        progression: ProgressionRule = ProgressionRule.None,
        sets: List<SetRecipe> = emptyList(),
        liftSlot: LiftSlot? = LiftSlot.BENCH,
        hooks: List<AutoregulationHook> = emptyList(),
    ): TrainingPlanRecipe = TrainingPlanRecipe(
        id = "uses-tm",
        weeks = listOf(
            weekRecipe(
                1, 0, "Base", BlockGoal.ACCUMULATION,
                listOf(day("A", slots = listOf(slot("s1", SlotRole.T1_MAIN, CatalogIds.BP, sets, 120, liftSlot)))),
            ),
        ),
        progression = progression,
        autoregulationHooks = hooks,
    )

    @Test
    fun usesTrainingMax_is_true_for_every_rule_that_moves_the_tm_and_for_series_prescribed_over_it() {
        listOf(
            ProgressionRule.CycleIncrement(2.5, 5.0),
            ProgressionRule.WeeklyKg(mapOf(2 to 5.0)),
            ProgressionRule.TopSetPr(),
            ProgressionRule.RepMaxAutoregulated,
            ProgressionRule.AmrapDrivenTm(),
            ProgressionRule.RepTargetDrivenTm(),
        ).forEach { rule ->
            assertTrue("${rule::class.simpleName}", usesTrainingMax(recipeWith(progression = rule)))
        }
        assertTrue(usesTrainingMax(recipeWith(hooks = listOf(AutoregulationHook(AutoregulationHookKind.AMRAP_TM)))))
        // Sin progresión, pero con series de trabajo en porcentaje del TM de un levantamiento.
        assertTrue(usesTrainingMax(recipeWith(sets = percentSets(120, 5 to 70.0, 5 to 75.0))))
        assertTrue("5/3/1 de autor", usesTrainingMax(wendlerRecipe()))
    }

    @Test
    fun usesTrainingMax_is_false_when_nothing_in_the_recipe_loads_from_the_tm() {
        assertFalse("sin series ni regla", usesTrainingMax(recipeWith()))
        assertFalse(
            "WeeklyPercent no tiene consumidor",
            usesTrainingMax(recipeWith(progression = ProgressionRule.WeeklyPercent(2.5))),
        )
        assertFalse(
            "series por repeticiones en reserva",
            usesTrainingMax(recipeWith(sets = List(3) { SetRecipe(reps = 8, rir = 2, loadBasis = LoadBasis.RPE) })),
        )
        assertFalse(
            "porcentaje del 1RM: su base no es el TM",
            usesTrainingMax(recipeWith(sets = percentSets(120, 5 to 70.0, basis = LoadBasis.PERCENT_1RM))),
        )
        assertFalse(
            "solo los calentamientos van en porcentaje del TM",
            usesTrainingMax(
                recipeWith(
                    sets = listOf(
                        SetRecipe(reps = 5, percent = 40.0, isWarmup = true),
                        SetRecipe(reps = 8, rir = 2, loadBasis = LoadBasis.RPE),
                    ),
                ),
            ),
        )
        assertFalse(
            "un ejercicio sin levantamiento de competición no lee el TM del perfil",
            usesTrainingMax(recipeWith(sets = percentSets(120, 5 to 70.0, 5 to 75.0), liftSlot = null)),
        )
    }
}
