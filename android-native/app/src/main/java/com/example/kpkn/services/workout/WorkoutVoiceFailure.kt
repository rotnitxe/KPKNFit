package com.example.kpkn.services.workout

enum class WorkoutVoiceFailureCode {
    MODEL_CORRUPT,
    STORAGE,
    OUT_OF_MEMORY,
    NATIVE_OR_JNA,
    MIC_BUSY,
    IPC_DEATH,
    ANDROID_RECOGNIZER_UNAVAILABLE,
}

data class WorkoutVoiceFailure(
    val code: WorkoutVoiceFailureCode,
    val message: String,
    val terminal: Boolean,
)

internal fun classifyVoiceFailure(message: String): WorkoutVoiceFailure {
    val lower = message.lowercase()
    val code = when {
        "binder" in lower || "proceso de voz" in lower -> WorkoutVoiceFailureCode.IPC_DEATH
        "memoria" in lower || "outofmemory" in lower -> WorkoutVoiceFailureCode.OUT_OF_MEMORY
        "espacio" in lower || "storage" in lower -> WorkoutVoiceFailureCode.STORAGE
        "modelo" in lower && ("corrupt" in lower || "incomplet" in lower) -> WorkoutVoiceFailureCode.MODEL_CORRUPT
        "micrófono" in lower || "microfono" in lower -> WorkoutVoiceFailureCode.MIC_BUSY
        "fallback" in lower || "recognizer" in lower -> WorkoutVoiceFailureCode.ANDROID_RECOGNIZER_UNAVAILABLE
        else -> WorkoutVoiceFailureCode.NATIVE_OR_JNA
    }
    return WorkoutVoiceFailure(
        code = code,
        message = message,
        terminal = code in setOf(
            WorkoutVoiceFailureCode.IPC_DEATH,
            WorkoutVoiceFailureCode.OUT_OF_MEMORY,
            WorkoutVoiceFailureCode.MODEL_CORRUPT,
            WorkoutVoiceFailureCode.NATIVE_OR_JNA,
        ),
    )
}
