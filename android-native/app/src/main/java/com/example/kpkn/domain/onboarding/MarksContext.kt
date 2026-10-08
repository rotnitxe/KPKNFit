package com.example.kpkn.domain.onboarding

import com.example.kpkn.domain.training.generator.RoutineLevel

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
 * - Halterofilia: sentadilla y, desde el nivel intermedio ([readsOlympicMarks]), también el arranque y los dos tiempos.
 * - El resto (cardio, funcional, culturismo, calistenia, armwrestling): sin marcas.
 */
object MarksContext {

    /**
     * ¿Se preguntan las marcas de arranque y de dos tiempos? Solo si algo las LEE: ningún control escribe un dato que el
     * motor no use. Desde el paquete D1b las reservas de halterofilia programan los levantamientos del lote OL-1 (cargadas,
     * arranques, tirones, enviones y sentadilla de arranque) y cada uno cuelga de la marca de su levantamiento
     * (`PoolEntry.mark = SNATCH | CLEAN_AND_JERK`, con la razón habitual entre la variante y su levantamiento, tomada por
     * debajo): la marca declarada fija su carga y, sin ella, la serie sale con «carga pendiente». `OlympicMarksTest` lo
     * mide con el generador real y falla si este valor deja de coincidir con lo que las reservas leen (ver
     * `docs/WIZARD_ENTRENO_V2.md` §1b).
     */
    const val CATALOG_HAS_OLYMPIC_LIFTS = true

    /**
     * Desde qué nivel el generador programa los levantamientos olímpicos: ninguno es «básico» y todos piden nivel
     * intermedio o avanzado, así que quien empieza o vuelve nunca los recibe y sus marcas no se leerían. `OlympicMarksTest`
     * compara este nivel con el mínimo de las entradas de las reservas que citan esas marcas.
     */
    val OLYMPIC_MIN_LEVEL: RoutineLevel = RoutineLevel.INTERMEDIATE

    /** ¿Un programa de [level] lleva levantamientos olímpicos y, por tanto, lee la marca de arranque y la de dos tiempos? */
    fun readsOlympicMarks(level: RoutineLevel): Boolean =
        CATALOG_HAS_OLYMPIC_LIFTS && level.ordinal >= OLYMPIC_MIN_LEVEL.ordinal

    /**
     * Los levantamientos que se preguntan. [hasOlympicLifts] dice si el programa de esta persona lee las marcas olímpicas
     * ([readsOlympicMarks] de su nivel): solo entonces Halterofilia pregunta también el arranque y los dos tiempos.
     */
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
