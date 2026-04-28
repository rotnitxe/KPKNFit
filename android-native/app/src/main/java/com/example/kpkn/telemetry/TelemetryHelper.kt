package com.example.kpkn.telemetry

import android.content.Context
import android.os.SystemClock
import com.example.kpkn.BuildConfig
import kotlin.math.roundToInt

/**
 * Helper class for logging telemetry events with common parameters
 */
class TelemetryHelper(private val context: Context) {
    
    private val telemetry = KpknTelemetry.getInstance(context)
    
    /**
     * Log app lifecycle events
     */
    fun logAppOpen() {
        context.logKpknEvent(
            TelemetryEvents.APP_OPEN,
            TelemetryParameters.SCREEN_NAME to "main",
            "app_version" to BuildConfig.VERSION_NAME,
            "app_version_code" to BuildConfig.VERSION_CODE
        )
    }
    
    fun logAppForeground() {
        context.logKpknEvent(
            TelemetryEvents.APP_FOREGROUND,
            TelemetryParameters.SCREEN_NAME to "foreground"
        )
    }
    
    fun logAppBackground() {
        context.logKpknEvent(
            TelemetryEvents.APP_BACKGROUND,
            TelemetryParameters.SCREEN_NAME to "background"
        )
    }
    
    /**
     * Log authentication events
     */
    fun logLoginSuccess(userId: String? = null) {
        context.logKpknEvent(
            TelemetryEvents.LOGIN_SUCCESS,
            TelemetryParameters.USER_ID to userId,
            TelemetryParameters.SUCCESS to true
        )
    }
    
    fun logLoginFailed(error: String? = null) {
        context.logKpknEvent(
            TelemetryEvents.LOGIN_FAILED,
            TelemetryParameters.ERROR_MESSAGE to error,
            TelemetryParameters.SUCCESS to false
        )
    }
    
    fun logLogout() {
        context.logKpknEvent(
            TelemetryEvents.LOGOUT,
            TelemetryParameters.SUCCESS to true
        )
    }
    
    /**
     * Log workout events with timing
     */
    fun logWorkoutStart(workoutId: String, workoutName: String, workoutType: String) {
        context.logKpknEvent(
            TelemetryEvents.WORKOUT_START,
            TelemetryParameters.WORKOUT_ID to workoutId,
            TelemetryParameters.WORKOUT_NAME to workoutName,
            TelemetryParameters.WORKOUT_TYPE to workoutType,
            TelemetryParameters.SUCCESS to true
        )
    }
    
    fun logWorkoutEnd(
        workoutId: String,
        durationMs: Long,
        exercisesCount: Int,
        setsCount: Int
    ) {
        val durationSeconds = durationMs / 1000
        context.logKpknEvent(
            TelemetryEvents.WORKOUT_END,
            TelemetryParameters.WORKOUT_ID to workoutId,
            TelemetryParameters.DURATION to durationSeconds,
            TelemetryParameters.ACTION to "complete",
            TelemetryParameters.SUCCESS to true,
            "exercises_count" to exercisesCount,
            "sets_count" to setsCount
        )
    }
    
    fun logWorkoutPause(workoutId: String) {
        context.logKpknEvent(
            TelemetryEvents.WORKOUT_PAUSE,
            TelemetryParameters.WORKOUT_ID to workoutId,
            TelemetryParameters.ACTION to "pause"
        )
    }
    
    fun logWorkoutResume(workoutId: String) {
        context.logKpknEvent(
            TelemetryEvents.WORKOUT_RESUME,
            TelemetryParameters.WORKOUT_ID to workoutId,
            TelemetryParameters.ACTION to "resume"
        )
    }
    
    fun logSetComplete(
        exerciseId: String,
        exerciseName: String,
        setNumber: Int,
        reps: Int,
        weight: Double?
    ) {
        context.logKpknEvent(
            TelemetryEvents.SET_COMPLETE,
            TelemetryParameters.EXERCISE_ID to exerciseId,
            TelemetryParameters.EXERCISE_NAME to exerciseName,
            TelemetryParameters.SET_NUMBER to setNumber,
            TelemetryParameters.REPS to reps,
            TelemetryParameters.WEIGHT to weight
        )
    }
    
    fun logRestStart(workoutId: String, restTimeSeconds: Int) {
        context.logKpknEvent(
            TelemetryEvents.REST_START,
            TelemetryParameters.WORKOUT_ID to workoutId,
            TelemetryParameters.REST_TIME to restTimeSeconds
        )
    }
    
    fun logRestEnd(workoutId: String, actualRestTimeSeconds: Int) {
        context.logKpknEvent(
            TelemetryEvents.REST_END,
            TelemetryParameters.WORKOUT_ID to workoutId,
            TelemetryParameters.REST_TIME to actualRestTimeSeconds,
            TelemetryParameters.ACTION to "complete"
        )
    }
    
    /**
     * Log nutrition events
     */
    fun logNutritionOpen() {
        context.logKpknEvent(
            TelemetryEvents.NUTRITION_OPEN,
            TelemetryParameters.SCREEN_NAME to "nutrition"
        )
    }
    
    fun logMealLogStart(mealType: String) {
        context.logKpknEvent(
            TelemetryEvents.MEAL_LOG_START,
            TelemetryParameters.MEAL_TYPE to mealType
        )
    }
    
    fun logMealLogComplete(
        mealId: String,
        mealType: String,
        totalCalories: Double,
        protein: Double,
        carbs: Double,
        fat: Double
    ) {
        context.logKpknEvent(
            TelemetryEvents.MEAL_LOG_COMPLETE,
            TelemetryParameters.MEAL_ID to mealId,
            TelemetryParameters.MEAL_TYPE to mealType,
            TelemetryParameters.CALORIES to totalCalories.roundToInt(),
            TelemetryParameters.PROTEIN to protein.roundToInt(),
            TelemetryParameters.CARBS to carbs.roundToInt(),
            TelemetryParameters.FAT to fat.roundToInt(),
            TelemetryParameters.SUCCESS to true
        )
    }
    
    fun logFoodSearch(query: String) {
        context.logKpknEvent(
            TelemetryEvents.FOOD_SEARCH,
            TelemetryParameters.ACTION to "search",
            TelemetryParameters.AI_INPUT to query
        )
    }
    
    fun logFoodItemAdd(foodId: String, foodName: String, calories: Double?) {
        context.logKpknEvent(
            TelemetryEvents.FOOD_ITEM_ADD,
            TelemetryParameters.FOOD_ID to foodId,
            TelemetryParameters.FOOD_NAME to foodName,
            TelemetryParameters.CALORIES to calories?.roundToInt()
        )
    }
    
    fun logNutritionPhotoParse() {
        context.logKpknEvent(
            TelemetryEvents.NUTRITION_PARSE_PHOTO,
            TelemetryParameters.NUTRITION_METHOD to "photo"
        )
    }
    
    fun logNutritionParseSuccess(foodItemsCount: Int) {
        context.logKpknEvent(
            TelemetryEvents.NUTRITION_PARSE_SUCCESS,
            TelemetryParameters.ACTION to "success",
            TelemetryParameters.SUCCESS to true,
            "food_items_count" to foodItemsCount
        )
    }
    
    fun logNutritionParseFailed(error: String? = null) {
        context.logKpknEvent(
            TelemetryEvents.NUTRITION_PARSE_FAILED,
            TelemetryParameters.ERROR_MESSAGE to error,
            TelemetryParameters.SUCCESS to false
        )
    }
    
    /**
     * Log program events
     */
    fun logProgramSelect(programId: String, programName: String, programType: String) {
        context.logKpknEvent(
            TelemetryEvents.PROGRAM_SELECT,
            TelemetryParameters.PROGRAM_ID to programId,
            TelemetryParameters.PROGRAM_NAME to programName,
            TelemetryParameters.PROGRAM_TYPE to programType
        )
    }
    
    fun logProgramStart(programId: String, week: Int, day: Int) {
        context.logKpknEvent(
            TelemetryEvents.PROGRAM_START,
            TelemetryParameters.PROGRAM_ID to programId,
            TelemetryParameters.PROGRAM_WEEK to week,
            TelemetryParameters.PROGRAM_DAY to day,
            TelemetryParameters.ACTION to "start"
        )
    }
    
    fun logProgramComplete(
        programId: String,
        durationMs: Long,
        totalWorkouts: Int,
        completedWorkouts: Int
    ) {
        val durationDays = durationMs / (1000 * 60 * 60 * 24)
        context.logKpknEvent(
            TelemetryEvents.PROGRAM_COMPLETE,
            TelemetryParameters.PROGRAM_ID to programId,
            TelemetryParameters.DURATION to durationDays,
            TelemetryParameters.ACTION to "complete",
            "total_workouts" to totalWorkouts,
            "completed_workouts" to completedWorkouts,
            TelemetryParameters.SUCCESS to true
        )
    }
    
    /**
     * Log exercise events
     */
    fun logExerciseSearch(query: String) {
        context.logKpknEvent(
            TelemetryEvents.EXERCICE_SEARCH,
            TelemetryParameters.ACTION to "search",
            TelemetryParameters.AI_INPUT to query
        )
    }
    
    fun logExerciseViewDetails(exerciseId: String, exerciseName: String, category: String?) {
        context.logKpknEvent(
            TelemetryEvents.EXERCICE_VIEW_DETAILS,
            TelemetryParameters.EXERCISE_ID to exerciseId,
            TelemetryParameters.EXERCISE_NAME to exerciseName,
            TelemetryParameters.EXERCISE_CATEGORY to category
        )
    }
    
    fun logExerciseAddToWorkout(exerciseId: String, exerciseName: String) {
        context.logKpknEvent(
            TelemetryEvents.EXERCICE_ADD_TO_WORKOUT,
            TelemetryParameters.EXERCISE_ID to exerciseId,
            TelemetryParameters.EXERCISE_NAME to exerciseName
        )
    }
    
    /**
     * Log navigation events
     */
    fun logNavigation(from: String, to: String) {
        context.logKpknEvent(
            TelemetryEvents.NAVIGATION,
            TelemetryParameters.NAVIGATION_FROM to from,
            TelemetryParameters.NAVIGATION_TO to to
        )
    }
    
    fun logDeepLinkOpen(path: String) {
        context.logKpknEvent(
            TelemetryEvents.DEEP_LINK_OPEN,
            TelemetryParameters.DEEP_LINK_PATH to path
        )
    }
    
    /**
     * Log AI events
     */
    fun logAIRequest(type: String, model: String, input: String? = null) {
        context.logKpknEvent(
            TelemetryEvents.AI_REQUEST,
            TelemetryParameters.AI_TYPE to type,
            TelemetryParameters.AI_MODEL to model,
            TelemetryParameters.AI_INPUT to input
        )
    }
    
    fun logAISuccess(type: String, model: String, output: String? = null) {
        context.logKpknEvent(
            TelemetryEvents.AI_SUCCESS,
            TelemetryParameters.AI_TYPE to type,
            TelemetryParameters.AI_MODEL to model,
            TelemetryParameters.AI_OUTPUT to output?.take(100), // Limit output length
            TelemetryParameters.SUCCESS to true
        )
    }
    
    fun logAIFailed(type: String, model: String, error: String? = null) {
        context.logKpknEvent(
            TelemetryEvents.AI_FAILED,
            TelemetryParameters.AI_TYPE to type,
            TelemetryParameters.AI_MODEL to model,
            TelemetryParameters.ERROR_MESSAGE to error,
            TelemetryParameters.SUCCESS to false
        )
    }
    
    /**
     * Log performance traces
     */
    class PerformanceTrace(private val telemetry: KpknTelemetry, private val name: String) {
        private val startTime = SystemClock.elapsedRealtime()
        
        fun stop() {
            val duration = SystemClock.elapsedRealtime() - startTime
            telemetry.logEvent("performance_trace", 
                TelemetryParameters.ACTION to name,
                TelemetryParameters.DURATION to duration,
                TelemetryParameters.SUCCESS to true
            )
        }
    }
    
    fun startPerformanceTrace(name: String): PerformanceTrace {
        return PerformanceTrace(telemetry, name)
    }
    
    /**
     * Log global errors
     */
    fun logGlobalError(error: Throwable, context: String? = null) {
        telemetry.logException(error, false)
        telemetry.logEvent("global_error",
            TelemetryParameters.ERROR_MESSAGE to error.message,
            TelemetryParameters.ERROR_STACK_TRACE to error.stackTraceToString().take(500),
            TelemetryParameters.ACTION to context
        )
    }
}