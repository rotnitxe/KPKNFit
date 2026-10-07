package com.example.kpkn.domain.onboarding

/**
 * Qué marcas se preguntan en el paso TRAINING_MAX («¿Conoces tus marcas?») según el objetivo y la experiencia.
 * Es la única regla: la ruta del wizard solo incluye el paso cuando [liftsFor] devuelve algo, y el paso solo pinta
 * esos levantamientos.
 *
 * - Quien empieza (novato) no tiene marcas que declarar: lista vacía.
 * - Fuerza y masa muscular pregunta sentadilla, banca y peso muerto **solo con barra**: sin ella esas marcas no
 *   describen lo que va a entrenar.
 * - Powerlifting y Powerbuilding: sentadilla, banca y peso muerto.
 * - Strongman: peso muerto, sentadilla y press militar.
 * - Halterofilia: sentadilla y, solo si el catálogo ya trae arranque y dos tiempos ([hasOlympicLifts]), también esos.
 * - El resto (cardio, funcional, culturismo, calistenia, armwrestling): sin marcas.
 */
object MarksContext {

    /**
     * El catálogo de ejercicios todavía no tiene arranque ni dos tiempos (ver `docs/WIZARD_ENTRENO_V2.md` §1b). Cuando
     * los incorpore, este es el único valor que cambia.
     */
    const val CATALOG_HAS_OLYMPIC_LIFTS = false

    fun liftsFor(
        profile: TrainingGoalProfile?,
        novice: Boolean,
        hasBarbell: Boolean = true,
        hasOlympicLifts: Boolean = CATALOG_HAS_OLYMPIC_LIFTS,
    ): List<LiftMark> {
        if (profile == null || novice) return emptyList()
        return when (profile) {
            TrainingGoalProfile.STRENGTH_MUSCLE ->
                if (hasBarbell) SQUAT_BENCH_DEADLIFT else emptyList()
            TrainingGoalProfile.POWERLIFTING,
            TrainingGoalProfile.POWERBUILDING -> SQUAT_BENCH_DEADLIFT
            TrainingGoalProfile.STRONGMAN ->
                listOf(LiftMark.DEADLIFT, LiftMark.SQUAT, LiftMark.OVERHEAD_PRESS)
            TrainingGoalProfile.WEIGHTLIFTING ->
                if (hasOlympicLifts) listOf(LiftMark.SQUAT, LiftMark.SNATCH, LiftMark.CLEAN_AND_JERK)
                else listOf(LiftMark.SQUAT)
            TrainingGoalProfile.STRENGTH_CARDIO,
            TrainingGoalProfile.FUNCTIONAL_HEALTH,
            TrainingGoalProfile.BODYBUILDING,
            TrainingGoalProfile.CALISTHENICS,
            TrainingGoalProfile.ARMWRESTLING -> emptyList()
        }
    }

    /** Las tres marcas del powerlifting, que el motor lee de `PowerliftingProfile` (el resto solo viaja en el borrador). */
    val BIG_THREE: Set<LiftMark> = setOf(LiftMark.SQUAT, LiftMark.BENCH, LiftMark.DEADLIFT)

    private val SQUAT_BENCH_DEADLIFT = listOf(LiftMark.SQUAT, LiftMark.BENCH, LiftMark.DEADLIFT)
}
