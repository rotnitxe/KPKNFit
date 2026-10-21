package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.*

internal data class Cal3Fixture(val state: WorkoutUiState, val exercise: Exercise, val completed: CompletedSet,
    val record: CalibrationDecisionRecord)

internal fun cal3Fixture(weight: Double = 69.0, reps: Int = 10, rir: Int? = 1,
    reference: Double? = 88.0, scope: CalibrationScope = CalibrationScope.SESSION,
    mode: LoadModeV2 = LoadModeV2.LOAD, partials: Int = 0): Cal3Fixture {
    val exercise = Exercise("cal3-exercise", "Sentadilla", catalogConfigurationId = "cal3-barbell-squat",
        canonicalExerciseId = "cal3-squat", reference1RM = reference,
        sets = listOf(ExerciseSet("cal3-s1", purpose = SetPurpose.CALIBRATOR, amrapMinimumReps = 6,
            calibrationScope = scope, targetReps = 6, weight = 69.0, loadModeV2 = mode)) +
            (2..4).map { ExerciseSet("cal3-s$it", weight = 60.0, targetReps = 12, targetRIR = 2, loadModeV2 = mode) })
    val base = WorkoutUiState(session = Session("cal3-session", "Fuerza A", exercises = listOf(exercise,
        Exercise("other", "Remo", catalogConfigurationId = "other", sets = listOf(ExerciseSet("cal3-s2", weight = 40.0, targetReps = 8))))),
        startTimeMs = 1_790_000_000_000L)
    val context = calibrationContextForSet(base, exercise, 0, null)
    val completed = CompletedSet("cal3-recorded", weight = weight, originalLoad = weight, reps = reps,
        rir = rir, rpe = rir?.let { 10.0 - it }, partialReps = partials, purpose = SetPurpose.CALIBRATOR,
        amrapMinimumReps = 6, loadMode = mode, contextKey = context.key, setIndex = 0, estimatorVersion = 2)
    val state = base.copy(completedSets = mapOf(workoutSetKey(exercise.id, 0) to completed))
    val record = buildRecordedCalibration(state, exercise, 0, completed, emptyList(), emptyList(), 1_790_000_001_000L)
    return Cal3Fixture(state.copy(calibrationDecisions = listOf(record)), exercise, completed, record)
}
