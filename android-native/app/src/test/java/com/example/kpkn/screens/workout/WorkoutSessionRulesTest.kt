package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.PostExerciseFeedback
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramCalendarization
import com.example.kpkn.data.models.ProgramCalendarizationMode
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.ReplacementPersistenceScopeV2
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SessionPart
import com.example.kpkn.data.models.SimpleProgramKind
import com.example.kpkn.data.models.TrainingMode
import com.example.kpkn.data.models.UnilateralMode
import com.example.kpkn.data.models.UnitModeV2
import com.example.kpkn.data.models.WarmupSetDefinition
import com.example.kpkn.domain.workout.LoadSuggestionEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutSessionRulesTest {

    @Test
    fun fatigue_factor_ignores_skipped_sets_progression() {
        assertEquals(1.0, LoadSuggestionEngine.fatigueFactorForPriorCompletedSets(0), 0.0)
        assertEquals(0.8, LoadSuggestionEngine.fatigueFactorForPriorCompletedSets(1), 0.0)
        assertEquals(0.6, LoadSuggestionEngine.fatigueFactorForPriorCompletedSets(2), 0.0)
        assertEquals(0.5, LoadSuggestionEngine.fatigueFactorForPriorCompletedSets(3), 0.0)
        assertEquals(0.5, LoadSuggestionEngine.fatigueFactorForPriorCompletedSets(8), 0.0)
    }

    @Test
    fun editing_state_requires_completed_set_and_keeps_requested_side() {
        val exercise = Exercise(
            id = "press-lateral",
            name = "Press lateral",
            isUnilateral = true,
            sets = listOf(ExerciseSet(id = "set-1")),
        )
        val completedSets = mapOf(
            "press-lateral_0_L" to CompletedSet(id = "done-left", side = "left", weight = 12.0, reps = 10),
            "press-lateral_0_R" to CompletedSet(id = "done-right", side = "right", weight = 10.0, reps = 10),
        )

        val leftState = WorkoutEditingRules.buildEditingState(completedSets, exercise, 0, preferredSide = "left")
        val rightState = WorkoutEditingRules.buildEditingState(completedSets, exercise, 0, preferredSide = "right")
        val missingState = WorkoutEditingRules.buildEditingState(emptyMap(), exercise, 0, preferredSide = "left")

        assertNotNull(leftState)
        assertEquals("left", leftState?.side)
        assertEquals("press-lateral_0_L", leftState?.setKey)
        assertEquals("right", rightState?.side)
        assertEquals("press-lateral_0_R", rightState?.setKey)
        assertNull(missingState)
    }

    @Test
    fun editing_state_treats_configured_unilateral_mode_as_unilateral() {
        val exercise = Exercise(
            id = "split-squat",
            name = "Split squat",
            isUnilateral = false,
            unilateralMode = UnilateralMode.UNILATERAL_DIFFERENTIAL,
            sets = listOf(ExerciseSet(id = "set-1")),
        )
        val completedSets = mapOf(
            "split-squat_0_L" to CompletedSet(id = "done-left", side = "left", weight = 24.0, reps = 8),
            "split-squat_0_R" to CompletedSet(id = "done-right", side = "right", weight = 22.0, reps = 8),
        )

        val editingState = WorkoutEditingRules.buildEditingState(completedSets, exercise, 0, preferredSide = "right")

        assertNotNull(editingState)
        assertEquals("right", editingState?.side)
        assertEquals("split-squat_0_R", editingState?.setKey)
    }

    @Test
    fun bilateral_editing_state_uses_plain_set_key() {
        val exercise = Exercise(
            id = "bench",
            name = "Bench Press",
            sets = listOf(ExerciseSet(id = "set-1")),
        )
        val completedSets = mapOf(
            "bench_0" to CompletedSet(id = "done", weight = 100.0, reps = 5),
        )

        val editingState = WorkoutEditingRules.buildEditingState(completedSets, exercise, 0)

        assertNotNull(editingState)
        assertEquals("bench_0", editingState?.setKey)
        assertNull(editingState?.side)
    }

    @Test
    fun pending_navigation_action_only_appears_with_unsaved_changes() {
        val pending = pendingSetNavigationAction(
            hasPendingDraftChanges = true,
            activeSetIdx = 1,
            targetSetIdx = 2,
        )
        val noneForSameSet = pendingSetNavigationAction(
            hasPendingDraftChanges = true,
            activeSetIdx = 1,
            targetSetIdx = 1,
        )
        val noneWithoutDraft = pendingSetNavigationAction(
            hasPendingDraftChanges = false,
            activeSetIdx = 1,
            targetSetIdx = 2,
        )

        assertTrue(pending is WorkoutPendingSetAction.Navigate)
        assertEquals(2, (pending as WorkoutPendingSetAction.Navigate).setIdx)
        assertNull(noneForSameSet)
        assertNull(noneWithoutDraft)
    }

    @Test
    fun pending_edit_action_respects_editing_state_and_side() {
        val pending = pendingSetEditAction(
            hasPendingDraftChanges = true,
            isAlreadyEditingCurrentSet = false,
            targetSetIdx = 0,
            side = "left",
        )
        val noneWhileEditing = pendingSetEditAction(
            hasPendingDraftChanges = true,
            isAlreadyEditingCurrentSet = true,
            targetSetIdx = 0,
            side = "left",
        )
        val noneWithoutDraft = pendingSetEditAction(
            hasPendingDraftChanges = false,
            isAlreadyEditingCurrentSet = false,
            targetSetIdx = 0,
            side = "left",
        )

        assertTrue(pending is WorkoutPendingSetAction.Edit)
        assertEquals(0, (pending as WorkoutPendingSetAction.Edit).setIdx)
        assertEquals("left", pending.side)
        assertNull(noneWhileEditing)
        assertNull(noneWithoutDraft)
    }

    @Test
    fun live_edit_persistence_scope_only_allows_simple_cyclic_programs() {
        val simpleCyclic = simpleProgram().copy(simpleProgramKind = SimpleProgramKind.CYCLIC)
        val simpleCalendarized = simpleProgram().copy(
            simpleProgramKind = SimpleProgramKind.CALENDARIZED,
            calendarization = ProgramCalendarization(ProgramCalendarizationMode.SIMPLE_DATED),
        )
        val advanced = simpleProgram().copy(
            structure = com.example.kpkn.data.models.ProgramStructure.COMPLEX,
            macrocycles = simpleProgram().macrocycles.map { macro ->
                macro.copy(blocks = macro.blocks + Block(id = "b2", name = "Bloque 2"))
            },
        )
        val advancedCalendarized = advanced.copy(
            timelineStartDate = "2026-05-18",
            calendarization = ProgramCalendarization(ProgramCalendarizationMode.ADVANCED_COMPETITION),
        )

        assertEquals(WorkoutLiveEditPersistenceScope.PERMANENT_ALLOWED, WorkoutEditingRules.liveEditPersistenceScope(simpleCyclic))
        assertEquals(WorkoutLiveEditPersistenceScope.SESSION_ONLY, WorkoutEditingRules.liveEditPersistenceScope(simpleCalendarized))
        assertEquals(WorkoutLiveEditPersistenceScope.SESSION_ONLY, WorkoutEditingRules.liveEditPersistenceScope(advanced))
        assertEquals(WorkoutLiveEditPersistenceScope.SESSION_ONLY, WorkoutEditingRules.liveEditPersistenceScope(advancedCalendarized))
        assertEquals(
            listOf(ReplacementPersistenceScopeV2.SESSION_ONLY, ReplacementPersistenceScopeV2.PERMANENT),
            WorkoutEditingRules.replacementPersistenceOptions(simpleCyclic),
        )
        assertEquals(
            listOf(ReplacementPersistenceScopeV2.SESSION_ONLY),
            WorkoutEditingRules.replacementPersistenceOptions(simpleCalendarized),
        )
        assertEquals(
            listOf(ReplacementPersistenceScopeV2.SESSION_ONLY),
            WorkoutEditingRules.replacementPersistenceOptions(advanced),
        )
        assertEquals(
            listOf(ReplacementPersistenceScopeV2.SESSION_ONLY),
            WorkoutEditingRules.replacementPersistenceOptions(advancedCalendarized),
        )
    }

    @Test
    fun pulse_token_expires_after_short_window() {
        val token = 1_000L

        assertEquals(true, isWorkoutPulseActive(token, nowMs = 1_500L, ttlMs = 1_000L))
        assertEquals(false, isWorkoutPulseActive(token, nowMs = 2_100L, ttlMs = 1_000L))
        assertEquals(false, isWorkoutPulseActive(null, nowMs = 1_500L, ttlMs = 1_000L))
    }

    @Test
    fun live_edit_normalizes_reps_mode_from_stale_time_fields() {
        val exercise = Exercise(
            id = "press",
            name = "Press",
            trainingMode = TrainingMode.REPS,
            sets = listOf(
                ExerciseSet(
                    id = "s1",
                    targetReps = 8,
                    targetDuration = 45,
                    unitModeV2 = UnitModeV2.TIME,
                    plannedTargetV2 = 45.0,
                    targetPercentageRM = 70.0,
                    loadModeV2 = LoadModeV2.LOAD,
                ),
            ),
        )

        val normalized = WorkoutEditingRules.normalizeLiveEditedExercise(exercise).sets.first()

        assertEquals(UnitModeV2.REPS, normalized.unitModeV2)
        assertEquals(8, normalized.targetReps)
        assertNull(normalized.targetDuration)
        assertNull(normalized.plannedTargetV2)
        assertNull(normalized.targetPercentageRM)
        assertEquals(LoadModeV2.LOAD, normalized.loadModeV2)
    }

    @Test
    fun live_edit_defaults_missing_load_mode_to_external_load() {
        val exercise = Exercise(
            id = "row",
            name = "Remo",
            trainingMode = TrainingMode.REPS,
            sets = listOf(ExerciseSet(id = "s1", targetReps = 10, loadModeV2 = null)),
        )

        val normalized = WorkoutEditingRules.normalizeLiveEditedExercise(exercise).sets.first()

        assertEquals(LoadModeV2.LOAD, normalized.loadModeV2)
    }

    @Test
    fun live_edit_preserves_explicit_bodyweight_load_mode() {
        val exercise = Exercise(
            id = "pushup",
            name = "Flexiones",
            trainingMode = TrainingMode.REPS,
            sets = listOf(ExerciseSet(id = "s1", targetReps = 12, loadModeV2 = LoadModeV2.BODYWEIGHT)),
        )

        val normalized = WorkoutEditingRules.normalizeLiveEditedExercise(exercise).sets.first()

        assertEquals(LoadModeV2.BODYWEIGHT, normalized.loadModeV2)
    }

    @Test
    fun continuity_state_prioritizes_superset_partner_in_same_round() {
        val press = Exercise(
            id = "press",
            name = "Press inclinado",
            supersetId = "ss-1",
            sets = listOf(ExerciseSet(id = "1"), ExerciseSet(id = "2")),
        )
        val row = Exercise(
            id = "row",
            name = "Remo pecho",
            supersetId = "ss-1",
            sets = listOf(ExerciseSet(id = "1"), ExerciseSet(id = "2")),
        )
        val session = Session(id = "s", name = "Push Pull", parts = listOf(SessionPart(id = "p1", name = "Bloque A", exercises = listOf(press, row))))

        val state = buildWorkoutContinuityState(
            session = session,
            visibleExercises = listOf(press, row),
            currentExerciseIdx = 0,
            currentSetIdx = 0,
        )

        assertEquals(WorkoutContinuityPhase.SUPERSET, state?.phase)
        assertEquals("Remo pecho", state?.nextExerciseName)
        assertEquals("Serie 1", state?.nextSetLabel)
    }

    @Test
    fun continuity_state_detects_next_block_transition() {
        val squat = Exercise(id = "squat", name = "Sentadilla", sets = listOf(ExerciseSet(id = "1")))
        val curl = Exercise(id = "curl", name = "Curl femoral", sets = listOf(ExerciseSet(id = "1")))
        val session = Session(
            id = "s",
            name = "Pierna",
            parts = listOf(
                SessionPart(id = "a", name = "Fuerza", exercises = listOf(squat)),
                SessionPart(id = "b", name = "Accesorios", exercises = listOf(curl)),
            ),
        )

        val state = buildWorkoutContinuityState(
            session = session,
            visibleExercises = listOf(squat, curl),
            currentExerciseIdx = 0,
            currentSetIdx = 0,
        )

        assertEquals(WorkoutContinuityPhase.NEXT_BLOCK, state?.phase)
        assertEquals("Curl femoral", state?.nextExerciseName)
        assertEquals("Cambio de bloque", state?.eyebrow)
    }

    @Test
    fun continuity_state_marks_session_finish_on_last_station() {
        val deadlift = Exercise(id = "deadlift", name = "Peso muerto", sets = listOf(ExerciseSet(id = "1")))
        val session = Session(id = "s", name = "Pull", exercises = listOf(deadlift))

        val state = buildWorkoutContinuityState(
            session = session,
            visibleExercises = listOf(deadlift),
            currentExerciseIdx = 0,
            currentSetIdx = 0,
        )

        assertEquals(WorkoutContinuityPhase.SESSION_FINISH, state?.phase)
        assertEquals(null, state?.nextExerciseName)
    }

    @Test
    fun continuity_transition_target_detects_new_block_entry() {
        val press = Exercise(id = "press", name = "Press banca", sets = listOf(ExerciseSet(id = "1")))
        val row = Exercise(id = "row", name = "Remo sentado", sets = listOf(ExerciseSet(id = "1")))
        val session = Session(
            id = "s",
            name = "Upper",
            parts = listOf(
                SessionPart(id = "a", name = "Empuje", exercises = listOf(press)),
                SessionPart(id = "b", name = "Traccion", exercises = listOf(row)),
            ),
        )

        val target = buildWorkoutContinuityTransitionTarget(
            session = session,
            visibleExercises = listOf(press, row),
            currentExerciseIdx = 1,
        )

        assertEquals("Nuevo bloque", target?.eyebrow)
        assertEquals("Entras a Traccion", target?.title)
    }

    @Test
    fun completed_previous_exercise_without_feedback_requests_handoff() {
        val press = Exercise(id = "press", name = "Press banca", sets = listOf(ExerciseSet(id = "1")))
        val row = Exercise(id = "row", name = "Remo sentado", sets = listOf(ExerciseSet(id = "1")))
        val completedSets = mapOf(
            "press_0" to CompletedSet(id = "done-1", weight = 80.0, reps = 8),
        )

        val pending = pendingWorkoutFeedbackHandoffExercise(
            visibleExercises = listOf(press, row),
            completedSets = completedSets,
            postExerciseFeedbackByExerciseId = emptyMap(),
            loggedSetKey = "press_0",
            currentExerciseId = "row",
        )

        assertEquals("press", pending?.id)
    }

    @Test
    fun completed_previous_exercise_with_existing_feedback_does_not_request_handoff() {
        val press = Exercise(id = "press", name = "Press banca", sets = listOf(ExerciseSet(id = "1")))
        val row = Exercise(id = "row", name = "Remo sentado", sets = listOf(ExerciseSet(id = "1")))
        val completedSets = mapOf(
            "press_0" to CompletedSet(id = "done-1", weight = 80.0, reps = 8),
        )
        val feedback = mapOf(
            "press" to PostExerciseFeedback(
                exerciseId = "press",
                exerciseName = "Press banca",
                technicalQuality = 8,
            ),
        )

        val pending = pendingWorkoutFeedbackHandoffExercise(
            visibleExercises = listOf(press, row),
            completedSets = completedSets,
            postExerciseFeedbackByExerciseId = feedback,
            loggedSetKey = "press_0",
            currentExerciseId = "row",
        )

        assertEquals(null, pending)
    }

    @Test
    fun workout_set_key_maps_side_variants_consistently() {
        assertEquals("curl_1", workoutSetKey("curl", 1, null))
        assertEquals("curl_1_L", workoutSetKey("curl", 1, "left"))
        assertEquals("curl_1_R", workoutSetKey("curl", 1, "right"))
        assertEquals("curl_1", workoutSetKey("curl", 1, "unexpected"))
    }

    @Test
    fun pulse_window_uses_default_ttl_boundary() {
        val token = 10_000L

        assertTrue(isWorkoutPulseActive(token, nowMs = 12_199L))
        assertEquals(false, isWorkoutPulseActive(token, nowMs = 12_201L))
    }

    @Test
    fun voice_draft_keeps_only_safe_fields_marked() {
        val draft = WorkoutSetDraft(
            weightText = "80",
            valueText = "8",
            selectedSide = "right",
            voiceFields = setOf(
                WorkoutVoiceField.WEIGHT,
                WorkoutVoiceField.VALUE,
                WorkoutVoiceField.SIDE,
            ),
            isDirty = true,
        )

        assertEquals("80", draft.weightText)
        assertEquals("8", draft.valueText)
        assertEquals("right", draft.selectedSide)
        assertEquals(true, WorkoutVoiceField.WEIGHT in draft.voiceFields)
        assertEquals(true, WorkoutVoiceField.VALUE in draft.voiceFields)
        assertEquals(true, WorkoutVoiceField.SIDE in draft.voiceFields)
        assertEquals(false, WorkoutVoiceField.INTENSITY in draft.voiceFields)
    }

    // @Test — Deshabilitado: buildWorkoutWarmupDisplaySets no está implementado aún
    fun warmup_display_sets_round_from_working_weight_anchor() {
        // TODO: Implementar buildWorkoutWarmupDisplaySets o eliminar este test
    }

    private fun simpleProgram(): Program =
        Program(
            id = "program",
            name = "Programa simple",
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
                                            sessions = listOf(Session(id = "session", name = "Día")),
                                        )
                                    ),
                                )
                            ),
                        )
                    ),
                )
            ),
        )
}
