package com.example.kpkn.domain.auge

/**
 * Alias del prior de recuperación. El aprendizaje de τ ya no vive aquí: lo hace el EKF por parámetro de
 * [AugeLearner] (paquete 3.2), alimentado por el caso de uso único [ManualGestureLearning] (paquete 3.3).
 * La inversión sesgada (`deriveImpliedRecoveryTime`), el α global, el tope de ±25 % y los actualizadores EMA
 * se retiraron de producción; su copia congelada vive solo en pruebas (`LegacyEmaReference`).
 */
object AugeAdaptiveEngine {

    /** Ventana mínima entre la carga y la sensación para que esta enseñe τ (el cierre a t≈0 solo ancla). */
    const val MIN_HOURS_FOR_TAU_LEARNING = AugeParameters.LEARNER_WINDOW_MIN_HOURS

    /** τ base del perfil de [muscle] en el motor; sin perfil se usa el medio de [AugeParameters]. */
    fun defaultRecoveryHours(muscle: String): Double = AugeParameters.baseTauHours(muscle)
}
