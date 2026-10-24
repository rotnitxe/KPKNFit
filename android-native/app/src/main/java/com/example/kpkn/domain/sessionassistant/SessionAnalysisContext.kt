package com.example.kpkn.domain.sessionassistant

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ProgramMode
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.TrainingMode
import com.example.kpkn.data.models.TrainingPhase
import com.example.kpkn.data.protocols.SlotRole

enum class AssistantSessionKind { STRENGTH, CARDIO, MIXED, EMPTY }

/** Qué busca la sesión: sale del modo del programa y de que sea de competición; sin programa, GENERAL. */
enum class AssistantObjective { PERFORMANCE, HYPERTROPHY, BALANCED, GENERAL }

enum class AssistantPhase { ACCUMULATION, TRANSFORMATION, REALIZATION, UNSPECIFIED }

/** Papel de un ejercicio en la sesión: los MAIN se protegen; los ACCESSORY son lo primero que un ajuste puede tocar. */
enum class ExerciseRole { MAIN, ACCESSORY, CARDIO }

/**
 * Lo que el asistente sabe de la sesión antes de decidir nada (AUD1 §2.7): tipo, objetivo, fase, papel de cada ejercicio,
 * historial realizado comparable y el tiempo (disponible y estimado con el reloj común). Una sola construcción para todas las reglas.
 */
data class SessionAnalysisContext(
    val kind: AssistantSessionKind,
    val objective: AssistantObjective,
    val phase: AssistantPhase,
    val roles: Map<String, ExerciseRole>,
    /** Tiempo que la persona fijó para la sesión; null = sin límite. */
    val availableSeconds: Int?,
    /** Duración estimada con el estimador común (el mismo reloj que ve en la cabecera del editor). */
    val clockSeconds: Int,
) {
    fun roleOf(exerciseId: String): ExerciseRole = roles[exerciseId] ?: ExerciseRole.ACCESSORY
}

object SessionAnalysisContextBuilder {
    /** Política de confianza inicial (AUD1 §2.7): seis sesiones comparables antes de comparar contra el historial. */
    const val MIN_COMPARABLE_SESSIONS = 6

    /** Umbrales de «movimiento principal» por heurística, solo cuando el plan no marca ninguno de forma explícita. */
    private const val MAIN_HEURISTIC_COUNT = 2

    private val PROTECTED_SLOTS = setOf(SlotRole.T1_MAIN, SlotRole.SPEED, SlotRole.TECHNIQUE)

    fun build(input: SessionAssistantInput, session: Session, clockSeconds: Int): SessionAnalysisContext {
        val exercises = session.allExercises().distinctBy { it.id }
        val strength = exercises.filter { it.cardioDetails == null && it.sets.isNotEmpty() }
        val hasCardio = exercises.any { it.cardioDetails != null }
        val kind = when {
            strength.isEmpty() && !hasCardio -> AssistantSessionKind.EMPTY
            strength.isEmpty() -> AssistantSessionKind.CARDIO
            hasCardio -> AssistantSessionKind.MIXED
            else -> AssistantSessionKind.STRENGTH
        }
        return SessionAnalysisContext(
            kind = kind,
            objective = objectiveOf(input, session),
            phase = when (input.program?.trainingPhase) {
                TrainingPhase.ACCUMULATION -> AssistantPhase.ACCUMULATION
                TrainingPhase.TRANSFORMATION -> AssistantPhase.TRANSFORMATION
                TrainingPhase.REALIZATION -> AssistantPhase.REALIZATION
                null -> AssistantPhase.UNSPECIFIED
            },
            roles = rolesOf(input, exercises),
            availableSeconds = input.targetDurationMinutes?.takeIf { it > 0 }?.times(60),
            clockSeconds = clockSeconds,
        )
    }

    private fun objectiveOf(input: SessionAssistantInput, session: Session): AssistantObjective = when {
        session.isCompetitionSession || session.isMeetDay -> AssistantObjective.PERFORMANCE
        else -> when (input.program?.mode) {
            ProgramMode.POWERLIFTING -> AssistantObjective.PERFORMANCE
            ProgramMode.HYPERTROPHY -> AssistantObjective.HYPERTROPHY
            ProgramMode.POWERBUILDING -> AssistantObjective.BALANCED
            null -> AssistantObjective.GENERAL
        }
    }

    /**
     * Papel de cada ejercicio. Primero lo EXPLÍCITO: movimiento de competición, hueco T1/velocidad/técnica de la receta o
     * prescripción por %RM. Solo si el plan no marca ninguno se usa la heurística: los dos primeros ejercicios de fuerza que son
     * multiarticulares con demanda alta (EFC o carga axial del catálogo). Todo lo demás es accesorio; el cardio, aparte.
     */
    private fun rolesOf(input: SessionAssistantInput, exercises: List<Exercise>): Map<String, ExerciseRole> {
        val strength = exercises.filter { it.cardioDetails == null }
        val explicit = strength.filter { ex ->
            ex.isCompetitionLift ||
                ex.slotRole in PROTECTED_SLOTS ||
                (ex.trainingMode == TrainingMode.RM && ex.sets.any { it.targetPercentageRM != null })
        }.map { it.id }.toSet()
        val mains: Set<String> = if (explicit.isNotEmpty()) {
            explicit
        } else {
            strength.filter { ex ->
                val info = SessionAssistantEngine.resolveExerciseInfo(ex, input.exerciseIndex) ?: return@filter false
                info.articulationType == "MULTIARTICULAR"
            }.take(MAIN_HEURISTIC_COUNT).map { it.id }.toSet()
        }
        return exercises.associate { ex ->
            ex.id to when {
                ex.cardioDetails != null -> ExerciseRole.CARDIO
                ex.id in mains -> ExerciseRole.MAIN
                else -> ExerciseRole.ACCESSORY
            }
        }
    }

}
