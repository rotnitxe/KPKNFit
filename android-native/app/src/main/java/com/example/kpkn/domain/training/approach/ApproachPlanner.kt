package com.example.kpkn.domain.training.approach

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.MobilitySeries
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.WarmupSetDefinition
import com.example.kpkn.data.models.effectiveRepRange
import com.example.kpkn.data.protocols.SlotRole

/**
 * Aproximación y movilidad obligatorias (Entreno v2). El calentamiento ya no es una opción del alta: todo programa
 * lleva **series de aproximación solo en los ejercicios pesados** y movilidad previa de las articulaciones que cargan.
 *
 * Reglas:
 * - El **primer ejercicio** de fuerza de la sesión lleva siempre aproximación y movilidad: si es pesado (o es un
 *   compuesto con carga y repeticiones moderadas), movilidad previa de sus articulaciones y una rampa sobre la
 *   carga de trabajo; si es ligero, solo una movilidad breve de su articulación principal y ninguna serie.
 * - El **segundo y tercer ejercicio** solo llevan aproximación si son pesados (cercanos al 1RM) **y** tocan una
 *   articulación que no cubrió la aproximación o la movilidad de un ejercicio anterior; entonces llevan una rampa
 *   más corta (1–2 pasos) y movilidad solo de esa articulación nueva. Del cuarto en adelante no se añade nada.
 * - Un ejercicio ligero, de aislamiento o de peso corporal fácil no lleva aproximación.
 * - Las sesiones de un plan de autor conservan lo que el autor ya declaró (`warmupSets`, `mobilitySeries`): solo se
 *   completa lo que falta, nunca se duplica ni se reemplaza.
 *
 * Qué es «pesado» ([ApproachExerciseInfo.canBeHeavy] y una de estas señales de las series de trabajo):
 * - `targetPercentageRM ≥ heavyPercentOf1Rm`; o
 * - series de ≤ 6 repeticiones (el extremo bajo del rango) con RIR ≤ 2 o RPE ≥ 7,5; o
 * - un compuesto principal (rol T1/T2/técnica o levantamiento de competición) de ≤ 8 repeticiones sin otra señal.
 *
 * Puro y determinista, sin `android.*`; idempotente: aplicarlo dos veces es aplicarlo una (la cobertura de
 * articulaciones se lee del estado final de cada ejercicio, así que una segunda pasada decide lo mismo).
 */
object ApproachPlanner {

    /**
     * Devuelve [session] con `Exercise.warmupSets` (aproximación) y `Exercise.mobilitySeries` (movilidad)
     * completados según las reglas de arriba. [infoOf] entrega lo que el motor sabe de cada ejercicio (articulaciones,
     * si es compuesto…); `null` = desconocido (se trata como no pesado).
     */
    fun apply(
        session: Session,
        options: ApproachOptions = ApproachOptions(),
        infoOf: (Exercise) -> ApproachExerciseInfo? = { null },
    ): Session {
        val additions = plan(session, options, infoOf)
        val completed = if (additions.isEmpty()) session else session.withAdditions(additions)
        if (completed.sessionB == null && completed.sessionC == null && completed.sessionD == null) return completed
        return completed.copy(
            sessionB = completed.sessionB?.let { apply(it, options, infoOf) },
            sessionC = completed.sessionC?.let { apply(it, options, infoOf) },
            sessionD = completed.sessionD?.let { apply(it, options, infoOf) },
        )
    }

    // ─── Planificación ──────────────────────────────────────────────────────

    /** Lo que se añade a un ejercicio: solo se aplica a lo que el ejercicio todavía no trae. */
    private data class Addition(
        val warmupSets: List<WarmupSetDefinition>,
        val mobility: List<MobilitySeries>,
    )

    private enum class Load { HEAVY, MODERATE, LIGHT }

    private fun plan(
        session: Session,
        options: ApproachOptions,
        infoOf: (Exercise) -> ApproachExerciseInfo?,
    ): Map<String, Addition> {
        val candidates = session.allExercises()
            .distinctBy { it.id }
            .filter { it.isResistance() }
            .take(MAX_POSITIONS)
        if (candidates.isEmpty()) return emptyMap()

        val covered = linkedSetOf<String>()
        val additions = LinkedHashMap<String, Addition>()
        candidates.forEachIndexed { index, exercise ->
            val position = index + 1
            val info = infoOf(exercise)
            var ramp = emptyList<WarmupSetDefinition>()
            var mobility = emptyList<MobilitySeries>()
            if (info != null) {
                val load = classify(exercise, info, options)
                val fresh = info.joints.filterNot { it in covered }
                val veryHeavy = exercise.heaviestPercent()?.let { it >= ApproachRamp.VERY_HEAVY_PERCENT } == true
                if (position == FIRST) {
                    if (load != Load.LIGHT) {
                        if (exercise.mobilitySeries.isEmpty()) {
                            mobility = ApproachMobility.forJoints(
                                ownerName = exercise.name,
                                joints = fresh,
                                budget = ApproachMobility.firstExerciseBudget(options.level),
                            )
                        }
                        if (exercise.warmupSets.isEmpty()) {
                            val steps = if (load == Load.HEAVY) {
                                ApproachRamp.long(options.level, veryHeavy)
                            } else {
                                ApproachRamp.short(options.level, veryHeavy)
                            }
                            ramp = ApproachRamp.definitions(exercise.id, steps)
                        }
                    } else if (exercise.mobilitySeries.isEmpty()) {
                        val main = info.joints.firstOrNull()
                        if (main != null) mobility = ApproachMobility.brief(exercise.name, main)
                    }
                } else if (load == Load.HEAVY && fresh.isNotEmpty()) {
                    if (exercise.mobilitySeries.isEmpty()) {
                        mobility = ApproachMobility.forJoints(
                            ownerName = exercise.name,
                            joints = fresh,
                            budget = ApproachMobility.laterExerciseBudget(options.level),
                        )
                    }
                    if (exercise.warmupSets.isEmpty()) {
                        ramp = ApproachRamp.definitions(exercise.id, ApproachRamp.short(options.level, veryHeavy))
                    }
                }
            }
            // La cobertura sale del estado final del ejercicio (lo que ya traía más lo que se añade): una rampa
            // cubre todas sus articulaciones principales y la movilidad cubre las que mueve cada movimiento.
            val hasRamp = exercise.warmupSets.isNotEmpty() || ramp.isNotEmpty()
            if (hasRamp && info != null) covered += info.joints
            covered += JointMobility.jointsCoveredBy(exercise.mobilitySeries.ifEmpty { mobility })
            if (ramp.isNotEmpty() || mobility.isNotEmpty()) additions[exercise.id] = Addition(ramp, mobility)
        }
        return additions
    }

    /** Un ejercicio de resistencia con series: ni cardio ni una tarjeta de movilidad sin series. */
    private fun Exercise.isResistance(): Boolean = cardioDetails == null && sets.any { !it.isEmptySlot }

    private fun classify(exercise: Exercise, info: ApproachExerciseInfo, options: ApproachOptions): Load {
        if (!info.canBeHeavy) return Load.LIGHT
        val work = exercise.sets.filter { !it.isEmptySlot }
        if (work.isEmpty()) return Load.LIGHT
        val percent = exercise.heaviestPercent()
        val lowestReps = work.mapNotNull { it.effectiveRepRange()?.min }.minOrNull()
        val highestReps = work.mapNotNull { it.effectiveRepRange()?.max }.maxOrNull()
        val rir = work.mapNotNull { it.targetRIR }.minOrNull()
        val rpe = work.mapNotNull { it.targetRPE }.maxOrNull()

        val heavyByPercent = percent != null && percent >= options.heavyPercentOf1Rm
        val heavyByEffort = lowestReps != null && lowestReps <= HEAVY_MAX_LOW_REPS &&
            ((rir != null && rir <= HEAVY_MAX_RIR) || (rpe != null && rpe >= HEAVY_MIN_RPE))
        val isMainCompound = info.isCompound &&
            (exercise.isCompetitionLift || exercise.slotRole in MAIN_ROLES)
        val heavyByMainLift = isMainCompound && (highestReps == null || highestReps <= MAIN_COMPOUND_MAX_REPS)
        if (heavyByPercent || heavyByEffort || heavyByMainLift) return Load.HEAVY
        // Un compuesto con carga en primer lugar (a 8–12 repeticiones) no es «cercano al 1RM», pero sigue siendo un
        // básico: lleva una rampa corta si abre la sesión.
        val moderate = info.isCompound && (highestReps == null || highestReps <= MODERATE_MAX_REPS)
        return if (moderate) Load.MODERATE else Load.LIGHT
    }

    private fun Exercise.heaviestPercent(): Double? =
        sets.filter { !it.isEmptySlot }.mapNotNull { it.targetPercentageRM }.maxOrNull()

    // ─── Aplicación ─────────────────────────────────────────────────────────

    private fun Session.withAdditions(additions: Map<String, Addition>): Session {
        fun Exercise.completed(): Exercise {
            val addition = additions[id] ?: return this
            return copy(
                warmupSets = warmupSets.ifEmpty { addition.warmupSets },
                mobilitySeries = mobilitySeries.ifEmpty { addition.mobility },
            )
        }
        return copy(
            exercises = exercises.map { it.completed() },
            parts = parts.map { part -> part.copy(exercises = part.exercises.map { it.completed() }) },
        )
    }

    private const val FIRST = 1
    private const val MAX_POSITIONS = 3
    private const val HEAVY_MAX_LOW_REPS = 6
    private const val HEAVY_MAX_RIR = 2
    private const val HEAVY_MIN_RPE = 7.5
    private const val MAIN_COMPOUND_MAX_REPS = 8
    private const val MODERATE_MAX_REPS = 12
    private val MAIN_ROLES = setOf(SlotRole.T1_MAIN, SlotRole.T2_SUPPLEMENTAL, SlotRole.TECHNIQUE)
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
