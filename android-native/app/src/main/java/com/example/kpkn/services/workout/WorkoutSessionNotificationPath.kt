package com.example.kpkn.services.workout

import com.example.kpkn.data.repository.ProgramRepository

/** Ruta de las notificaciones de entreno sin sesión en curso: la pestaña Entreno, como hasta ahora. */
internal const val WORKOUT_NOTIFICATION_TRAINING_PATH = "training"

/**
 * Ruta de las notificaciones del descanso, la voz y el GPS (KPKN LS3): con una sesión en curso, `workout/<programId>/<sessionId>`, que
 * `DeepLinkRouter` resuelve a la sesión; sin ella, `training`. Función pura, sin Android.
 */
internal fun workoutNotificationPath(programId: String?, sessionId: String?): String {
    val program = programId?.trim().orEmpty()
    val session = sessionId?.trim().orEmpty()
    return if (program.isNotEmpty() && session.isNotEmpty()) {
        "workout/$program/$session"
    } else {
        WORKOUT_NOTIFICATION_TRAINING_PATH
    }
}

/**
 * La ruta de ahora mismo, según la sesión en curso del repositorio: la misma que reanuda la tarjeta flotante de Inicio y Entreno.
 * Sin repositorio inicializado (proceso recién arrancado por una notificación), cae a `training`.
 */
internal fun currentWorkoutNotificationPath(): String {
    val ongoing = runCatching { ProgramRepository.getInstance().ongoingWorkout.value }.getOrNull()
    return ongoing?.let { workoutNotificationPath(it.programId, it.session.id) } ?: WORKOUT_NOTIFICATION_TRAINING_PATH
}
