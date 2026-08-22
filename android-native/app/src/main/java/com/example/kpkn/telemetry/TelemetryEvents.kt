package com.example.kpkn.telemetry

/**
 * Constants for Firebase Analytics events and parameters
 */
object TelemetryEvents {
    // App lifecycle events
    const val APP_OPEN = "app_open"
    const val APP_FOREGROUND = "app_foreground"
    const val APP_BACKGROUND = "app_background"
    
    // Authentication events
    const val LOGIN_SUCCESS = "login_success"
    const val LOGIN_FAILED = "login_failed"
    const val LOGOUT = "logout"
    
    // Training/Workout events
    const val WORKOUT_START = "workout_start"
    const val WORKOUT_END = "workout_end"
    const val WORKOUT_PAUSE = "workout_pause"
    const val WORKOUT_RESUME = "workout_resume"
    const val WORKOUT_CANCEL = "workout_cancel"
    const val SET_COMPLETE = "set_complete"
    const val REST_START = "rest_start"
    const val REST_END = "rest_end"
    const val EXERCISE_COMPLETE = "exercise_complete"
    
    // Nutrition events
    const val NUTRITION_OPEN = "nutrition_open"
    const val MEAL_LOG_START = "meal_log_start"
    const val MEAL_LOG_COMPLETE = "meal_log_complete"
    const val FOOD_SEARCH = "food_search"
    const val FOOD_ITEM_ADD = "food_item_add"
    const val NUTRITION_PARSE_PHOTO = "nutrition_parse_photo"
    const val NUTRITION_PARSE_SUCCESS = "nutrition_parse_success"
    const val NUTRITION_PARSE_FAILED = "nutrition_parse_failed"
    
    // Program events
    const val PROGRAM_SELECT = "program_select"
    const val PROGRAM_START = "program_start"
    const val PROGRAM_COMPLETE = "program_complete"
    const val PROGRAM_CANCEL = "program_cancel"
    
    // Exercise events
    const val EXERCICE_SEARCH = "exercise_search"
    const val EXERCICE_VIEW_DETAILS = "exercise_view_details"
    const val EXERCICE_ADD_TO_WORKOUT = "exercise_add_to_workout"
    
    // WikiLab events
    const val WIKILAB_OPEN = "wikilab_open"
    const val WIKILAB_EXERCISE_VIEW = "wikilab_exercise_view"
    
    // Settings events
    const val SETTINGS_OPEN = "settings_open"
    const val SETTINGS_CHANGE = "settings_change"
    const val THEME_CHANGE = "theme_change"
    const val LANGUAGE_CHANGE = "language_change"
    
    // Navigation events
    const val NAVIGATION = "navigation"
    const val DEEP_LINK_OPEN = "deep_link_open"
    
    // Health events
    const val HEALTH_CONNECT_SYNC = "health_connect_sync"
    const val HEALTH_DATA_READ = "health_data_read"
    const val HEALTH_DATA_WRITE = "health_data_write"
    
    // AI events
    const val AI_REQUEST = "ai_request"
    const val AI_SUCCESS = "ai_success"
    const val AI_FAILED = "ai_failed"
    
    // Sharing events
    const val SHARE_WORKOUT = "share_workout"
    const val SHARE_PROGRESS = "share_progress"
    const val SHARE_ACHIEVEMENT = "share_achievement"
}

object TelemetryParameters {
    // Common parameters
    const val SCREEN_NAME = "screen_name"
    const val ACTION = "action"
    const val TIMESTAMP = "timestamp"
    const val DURATION = "duration"
    const val SUCCESS = "success"
    
    // User parameters
    const val USER_ID = "user_id"
    const val USER_TYPE = "user_type"
    
    // Workout parameters
    const val WORKOUT_ID = "workout_id"
    const val WORKOUT_NAME = "workout_name"
    const val WORKOUT_TYPE = "workout_type"
    const val EXERCISE_ID = "exercise_id"
    const val EXERCISE_NAME = "exercise_name"
    const val SET_NUMBER = "set_number"
    const val REPS = "reps"
    const val WEIGHT = "weight"
    const val REST_TIME = "rest_time"
    
    // Nutrition parameters
    const val MEAL_TYPE = "meal_type" // breakfast, lunch, dinner, snack
    const val FOOD_ID = "food_id"
    const val FOOD_NAME = "food_name"
    const val CALORIES = "calories"
    const val PROTEIN = "protein"
    const val CARBS = "carbs"
    const val FAT = "fat"
    const val MEAL_ID = "meal_id"
    const val NUTRITION_METHOD = "nutrition_method" // manual, photo, barcode
    
    // Program parameters
    const val PROGRAM_ID = "program_id"
    const val PROGRAM_NAME = "program_name"
    const val PROGRAM_TYPE = "program_type"
    const val PROGRAM_DURATION = "program_duration"
    const val PROGRAM_WEEK = "program_week"
    const val PROGRAM_DAY = "program_day"
    
    // Exercise parameters
    const val EXERCISE_CATEGORY = "exercise_category"
    const val EXERCICE_MUSCLE_GROUP = "exercise_muscle_group"
    
    // AI parameters
    const val AI_TYPE = "ai_type" // nutrition, workout_advice, exercise_info
    /** Legacy event key kept for imported historical exports; Android emits local parser events. */
    const val AI_MODEL = "ai_model"
    const val AI_INPUT = "ai_input"
    const val AI_OUTPUT = "ai_output"
    
    // Health parameters
    const val HEALTH_DATA_TYPE = "health_data_type" // weight, fat, steps, exercise
    const val HEALTH_VALUE = "health_value"
    const val HEALTH_UNIT = "health_unit"
    
    // Error parameters
    const val ERROR_MESSAGE = "error_message"
    const val ERROR_STACK_TRACE = "error_stack_trace"
    const val ERROR_CODE = "error_code"
    
    // Navigation parameters
    const val NAVIGATION_FROM = "navigation_from"
    const val NAVIGATION_TO = "navigation_to"
    const val DEEP_LINK_PATH = "deep_link_path"
}
