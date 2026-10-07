package com.example.kpkn.domain.training.approach

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.Session

/**
 * Aproximación y movilidad obligatorias (Entreno v2). El calentamiento ya no es una opción del alta: todo programa
 * lleva **series de aproximación solo en los ejercicios pesados** y movilidad previa de las articulaciones que cargan.
 *
 * Reglas (las implementa el paquete D3; este archivo es el contrato y, mientras tanto, una identidad):
 * - El **primer ejercicio** de la sesión, si es pesado, lleva movilidad previa de sus articulaciones y series de
 *   aproximación (rampa sobre la carga de trabajo).
 * - El **segundo y tercer ejercicio** solo llevan aproximación si son pesados (cercanos al 1RM) **y** tocan una
 *   articulación que no cubrió la aproximación/movilidad de un ejercicio anterior de la sesión.
 * - Un ejercicio ligero, de aislamiento o de peso corporal fácil no lleva aproximación.
 * - Las sesiones de un plan de autor conservan lo que el autor ya declaró; solo se completa lo que falta.
 */
object ApproachPlanner {

    /**
     * Devuelve [session] con `Exercise.warmupSets` (aproximación) y `Exercise.mobilitySeries`/`Session.warmup` (movilidad)
     * completados según las reglas de arriba. [infoOf] entrega lo que el motor sabe de cada ejercicio (articulaciones,
     * si es compuesto…); `null` = desconocido (se trata como no pesado).
     */
    fun apply(
        session: Session,
        options: ApproachOptions = ApproachOptions(),
        infoOf: (Exercise) -> ApproachExerciseInfo? = { null },
    ): Session = session
}

/** Nivel de quien entrena: cuanto menos experiencia, más rampa y más movilidad. */
enum class ApproachLevel { NOVICE, INTERMEDIATE, ADVANCED }

data class ApproachOptions(
    val level: ApproachLevel = ApproachLevel.INTERMEDIATE,
    /** Una serie de trabajo con este % del 1RM (o más) cuenta como pesada. */
    val heavyPercentOf1Rm: Double = 80.0,
)

/** Lo que el planificador necesita saber de un ejercicio concreto; lo aporta quien arma la sesión (con el catálogo). */
data class ApproachExerciseInfo(
    /** Articulaciones principales que carga (hombro, cadera, rodilla, tobillo, codo, muñeca, columna…). */
    val joints: Set<String>,
    val isCompound: Boolean,
    /** Admite carga externa significativa; un aislamiento ligero o un peso corporal fácil es `false`. */
    val canBeHeavy: Boolean,
)
