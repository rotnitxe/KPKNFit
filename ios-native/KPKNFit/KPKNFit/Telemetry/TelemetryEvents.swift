enum TelemetryEvents {
    static let appOpen = "app_open"
    static let appForeground = "app_foreground"
    static let appBackground = "app_background"

    static let loginSuccess = "login_success"
    static let loginFailed = "login_failed"
    static let logout = "logout"

    static let workoutStart = "workout_start"
    static let workoutEnd = "workout_end"
    static let workoutPause = "workout_pause"
    static let workoutResume = "workout_resume"
    static let workoutCancel = "workout_cancel"
    static let setComplete = "set_complete"
    static let restStart = "rest_start"
    static let restEnd = "rest_end"
    static let exerciseComplete = "exercise_complete"

    static let nutritionOpen = "nutrition_open"
    static let mealLogStart = "meal_log_start"
    static let mealLogComplete = "meal_log_complete"
    static let foodSearch = "food_search"
    static let foodItemAdd = "food_item_add"
    static let nutritionParsePhoto = "nutrition_parse_photo"
    static let nutritionParseSuccess = "nutrition_parse_success"
    static let nutritionParseFailed = "nutrition_parse_failed"

    static let programSelect = "program_select"
    static let programStart = "program_start"
    static let programComplete = "program_complete"
    static let programCancel = "program_cancel"

    static let exerciceSearch = "exercise_search"
    static let exerciceViewDetails = "exercise_view_details"
    static let exerciceAddToWorkout = "exercise_add_to_workout"

    static let wikilabOpen = "wikilab_open"
    static let wikilabExerciseView = "wikilab_exercise_view"

    static let settingsOpen = "settings_open"
    static let settingsChange = "settings_change"
    static let themeChange = "theme_change"
    static let languageChange = "language_change"

    static let navigation = "navigation"
    static let deepLinkOpen = "deep_link_open"

    static let healthConnectSync = "health_connect_sync"
    static let healthDataRead = "health_data_read"
    static let healthDataWrite = "health_data_write"

    static let aiRequest = "ai_request"
    static let aiSuccess = "ai_success"
    static let aiFailed = "ai_failed"

    static let shareWorkout = "share_workout"
    static let shareProgress = "share_progress"
    static let shareAchievement = "share_achievement"
}

enum TelemetryParameters {
    static let screenName = "screen_name"
    static let action = "action"
    static let timestamp = "timestamp"
    static let duration = "duration"
    static let success = "success"

    static let userId = "user_id"
    static let userType = "user_type"

    static let workoutId = "workout_id"
    static let workoutName = "workout_name"
    static let workoutType = "workout_type"
    static let exerciseId = "exercise_id"
    static let exerciseName = "exercise_name"
    static let setNumber = "set_number"
    static let reps = "reps"
    static let weight = "weight"
    static let restTime = "rest_time"

    static let mealType = "meal_type"
    static let foodId = "food_id"
    static let foodName = "food_name"
    static let calories = "calories"
    static let protein = "protein"
    static let carbs = "carbs"
    static let fat = "fat"
    static let mealId = "meal_id"
    static let nutritionMethod = "nutrition_method"

    static let programId = "program_id"
    static let programName = "program_name"
    static let programType = "program_type"
    static let programDuration = "program_duration"
    static let programWeek = "program_week"
    static let programDay = "program_day"

    static let exerciseCategory = "exercise_category"
    static let exerciceMuscleGroup = "exercise_muscle_group"

    static let aiType = "ai_type"
    static let aiModel = "ai_model"
    static let aiInput = "ai_input"
    static let aiOutput = "ai_output"

    static let healthDataType = "health_data_type"
    static let healthValue = "health_value"
    static let healthUnit = "health_unit"

    static let errorMessage = "error_message"
    static let errorStackTrace = "error_stack_trace"
    static let errorCode = "error_code"

    static let navigationFrom = "navigation_from"
    static let navigationTo = "navigation_to"
    static let deepLinkPath = "deep_link_path"
}
