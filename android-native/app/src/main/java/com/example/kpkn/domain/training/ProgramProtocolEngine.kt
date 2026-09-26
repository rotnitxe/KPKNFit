package com.example.kpkn.domain.training

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.protocols.Protocol
import com.example.kpkn.data.protocols.isVisibleForApplication
import com.example.kpkn.data.splits.SPLIT_TEMPLATES

/**
 * Compilador único de protocolos → programa ejecutable.
 * Solo acepta protocolos visibles con receta día a día; [PlanMaterializer]
 * es la única fuente de sesiones. La síntesis genérica por etiqueta de día
 * (`sessionRecipeForDay` / `liftForPart` / `PeriodizationEngine.prescriptionFor`)
 * ya no vive aquí.
 */
object ProgramProtocolEngine {

    fun applyProtocol(
        program: Program,
        protocol: Protocol,
        idProvider: IdProvider = UuidIdProvider,
        @Suppress("UNUSED_PARAMETER") enhancedDayDifferentiation: Boolean = false,
        metadata: ExerciseCompositionMetadataProvider? = null,
        exerciseList: List<com.example.kpkn.data.models.ExerciseMuscleInfo>? = null,
        // Opciones del usuario (autoreg/calentamientos) para la PRIMERA
        // materialización; los callers legados quedan intactos con el default.
        defaultOptions: TrainingOptions = TrainingOptions(),
    ): Program {
        require(protocol.isVisibleForApplication) {
            "El protocolo '${protocol.id}' no está publicado: falta una receta verificable día por día."
        }
        require(protocol.blocks.isNotEmpty()) {
            "El protocolo '${protocol.id}' no tiene bloques materializables."
        }
        val recipe = requireNotNull(protocol.recipe?.takeIf { it.weeks.isNotEmpty() }) {
            "El protocolo '${protocol.id}' no tiene receta día a día."
        }
        val resolvedSplitId = protocol.defaultSplit
            ?.let(::resolveSplitId)
            ?: program.selectedSplitId?.let(::resolveSplitId)
            ?: error("El protocolo '${protocol.id}' debe declarar defaultSplit o el programa debe tener selectedSplitId")
        val applied = PlanMaterializer.materialize(
            program = program.copy(
                selectedSplitId = resolvedSplitId,
                sourceProtocolId = protocol.id,
            ),
            recipe = recipe.copy(exemptions = recipe.exemptions + protocol.exemptions),
            metadata = CompositionMetadataHolder.resolve(metadata),
            idProvider = idProvider,
            extraExemptions = protocol.exemptions,
            sourceProtocolId = protocol.id,
            options = defaultOptions,
        )
        val hydrated = hydrateProgramGoals(applied)
        val scaled = scaleToCalibratedVolume(hydrated, exerciseList, idProvider)
        val executable = if (ProgramCalendarEngine.isCalendarized(scaled)) {
            ProgramCalendarEngine.materializeWeekDates(scaled)
        } else {
            scaled
        }
        return ProgramExecutionContract.requireExecutable(executable)
    }

    /**
     * Auto-escala series al MAV calibrado cuando el programa trae calibración.
     * Sin calibrar no se toca nada (la UI pide calibrar antes). Solo clona
     * sets existentes con UUID nuevo; nunca añade ejercicios ni cambia plantillas.
     */
    internal fun scaleToCalibratedVolume(
        program: Program,
        exerciseList: List<com.example.kpkn.data.models.ExerciseMuscleInfo>?,
        idProvider: IdProvider,
    ): Program {
        if (exerciseList == null) return program
        if (!VolumeCalibrationGate.isVolumeCalibrated(program)) return program
        return program.copy(
            macrocycles = program.macrocycles.map { macro ->
                macro.copy(
                    blocks = macro.blocks.map { block ->
                        block.copy(
                            mesocycles = block.mesocycles.map { meso ->
                                meso.copy(
                                    weeks = meso.weeks.map { week ->
                                        val scaled = TemplateVolumeScaler.scaleWeekSessions(
                                            sessions = week.sessions,
                                            exerciseList = exerciseList,
                                            recommendations = program.volumeRecommendations,
                                            idProvider = idProvider,
                                        )
                                        week.copy(sessions = scaled.sessions)
                                    },
                                )
                            },
                        )
                    },
                )
            },
        )
    }

    /** Attach recorded S/B/D goals to the exact competition configurations. */
    private fun hydrateProgramGoals(program: Program): Program {
        val goals = program.goals ?: return program
        fun referenceFor(exercise: Exercise): Double? {
            if (exercise.reference1RM != null && exercise.reference1RM > 0.0) return exercise.reference1RM
            val id = listOfNotNull(
                exercise.catalogConfigurationId,
                exercise.canonicalExerciseId,
                exercise.exerciseDbId,
                exercise.exerciseId,
            ).firstOrNull()?.lowercase() ?: return exercise.reference1RM
            val goal = when (id) {
                "low_bar_back_squat__barbell", "high_bar_back_squat__barbell" -> goals.squat1RM
                "bench_press__barbell" -> goals.bench1RM
                "conventional_deadlift__bilateral__barbell" -> goals.deadlift1RM
                else -> null
            }
            return goal?.takeIf { it > 0.0 } ?: exercise.reference1RM
        }
        fun mapSession(session: Session): Session = session.copy(
            exercises = session.exercises.map { it.copy(reference1RM = referenceFor(it)) },
            parts = session.parts.map { part ->
                part.copy(exercises = part.exercises.map { it.copy(reference1RM = referenceFor(it)) })
            },
            sessionB = session.sessionB?.let(::mapSession),
            sessionC = session.sessionC?.let(::mapSession),
            sessionD = session.sessionD?.let(::mapSession),
        )
        return program.copy(
            macrocycles = program.macrocycles.map { macro ->
                macro.copy(blocks = macro.blocks.map { block ->
                    block.copy(mesocycles = block.mesocycles.map { meso ->
                        meso.copy(weeks = meso.weeks.map { week ->
                            week.copy(sessions = week.sessions.map(::mapSession))
                        })
                    })
                })
            },
        )
    }

    fun resolveSplitId(raw: String): String {
        val normalized = raw.trim().lowercase()
        val resolved = when (normalized) {
            "upper-lower", "ul", "4-day", "4day" -> "ul_x4"
            "ppl" -> "ppl_x6"
            "fullbody", "full-body" -> "fullbody_x3"
            "texas", "texas_method" -> "texas_method"
            "531_4day", "531-4day" -> "531_bbb"
            "cube_4day" -> "cube_method"
            "korte_3day" -> "korte_3x3"
            else -> normalized
        }
        if (SPLIT_TEMPLATES.none { it.id == resolved }) {
            error("Split '$raw' no existe en SPLIT_TEMPLATES y no tiene alias válido")
        }
        return resolved
    }
}
