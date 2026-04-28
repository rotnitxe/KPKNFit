package com.example.kpkn.screens.workout

data class CoachMessage(
    val key: String,
    val title: String,
    val body: String,
    val severity: CoachSeverity,
    val action: CoachAction? = null,
)

enum class CoachSeverity { INFO, WARNING, DANGER, SUCCESS }

enum class CoachAction { REDUCE_INTENSITY, SKIP_EXERCISE, EXTEND_REST, STAY_THE_COURSE }

object WorkoutCoachMessages {
    enum class DrainLevel { XS, LOW, AVERAGE, HIGH }
    enum class ReadinessLevel { XS, LOW, AVERAGE, HIGH }

    private data class CoachKey(
        val drain: DrainLevel,
        val readiness: ReadinessLevel,
        val sessionProgress: SessionPhase,
    )

    private enum class SessionPhase { EARLY, MID, LATE }

    private val COACH_MESSAGES: Map<CoachKey, CoachMessage> = buildMap {
        val drainLevels = DrainLevel.entries
        val readinessLevels = ReadinessLevel.entries
        val phases = SessionPhase.entries

        for (drain in drainLevels) {
            for (readiness in readinessLevels) {
                for (phase in phases) {
                    val (severity, title, body, action) = resolve(drain, readiness, phase)
                    put(CoachKey(drain, readiness, phase), CoachMessage(
                        key = "${drain.name}_${readiness.name}_${phase.name}",
                        title = title,
                        body = body,
                        severity = severity,
                        action = action,
                    ))
                }
            }
        }
    }

    private fun resolve(
        drain: DrainLevel,
        readiness: ReadinessLevel,
        phase: SessionPhase,
    ): CoachMessageComponents {
        val isHighDrain = drain == DrainLevel.HIGH || drain == DrainLevel.AVERAGE
        val isLowReadiness = readiness == ReadinessLevel.XS || readiness == ReadinessLevel.LOW
        val isLateSession = phase == SessionPhase.LATE

        return when {
            isHighDrain && isLowReadiness && isLateSession -> CoachMessageComponents(
                severity = CoachSeverity.DANGER,
                title = "Cuidado: fatiga acumulada + baja disponibilidad",
                body = "Has acumulado fatiga significativa y tu readiness está baja. Considera terminar ejercicios principales aquí y guardar energía.",
                action = CoachAction.REDUCE_INTENSITY,
            )
            isHighDrain && isLowReadiness && !isLateSession -> CoachMessageComponents(
                severity = CoachSeverity.WARNING,
                title = "Fatiga en aumento, readiness limitada",
                body = "El sistema nervioso está trabajando fuerte y la recuperación de hoy no es óptima. Prioriza las series más importantes.",
                action = CoachAction.REDUCE_INTENSITY,
            )
            drain == DrainLevel.HIGH && readiness == ReadinessLevel.AVERAGE && isLateSession -> CoachMessageComponents(
                severity = CoachSeverity.WARNING,
                title = "Cerca del límite — serie final de esta fase",
                body = "Estás drenado pero tu cuerpo responde. Si la próxima serie se siente pesada, no insistas. Termina fuerte pero seguro.",
                action = CoachAction.REDUCE_INTENSITY,
            )
            drain == DrainLevel.HIGH && readiness == ReadinessLevel.HIGH && isLateSession -> CoachMessageComponents(
                severity = CoachSeverity.INFO,
                title = "Drenado pero listo",
                body = "Tu batería está baja pero tu readiness es alta. Recuperas bien. La última serie cuenta.",
                action = CoachAction.STAY_THE_COURSE,
            )
            drain == DrainLevel.HIGH && readiness == ReadinessLevel.HIGH && !isLateSession -> CoachMessageComponents(
                severity = CoachSeverity.INFO,
                title = "Buen ritmo de trabajo",
                body = "Estás drenando pero tu recuperación es excelente. Sigue con el plan, tu cuerpo responde bien.",
                action = CoachAction.STAY_THE_COURSE,
            )
            drain == DrainLevel.AVERAGE && readiness == ReadinessLevel.HIGH && isLateSession -> CoachMessageComponents(
                severity = CoachSeverity.SUCCESS,
                title = "Excelente sesión",
                body = "Drenado moderado con readiness alta. Estás navegando la sesión con eficiencia. ¡A la carga!",
                action = CoachAction.STAY_THE_COURSE,
            )
            drain == DrainLevel.AVERAGE && readiness == ReadinessLevel.LOW -> CoachMessageComponents(
                severity = CoachSeverity.WARNING,
                title = "Recuperación limitada detectada",
                body = "Tu cuerpo no está al 100% hoy. Ajusta la intensidad si algo no se siente bien.",
                action = CoachAction.REDUCE_INTENSITY,
            )
            drain == DrainLevel.LOW && readiness == ReadinessLevel.LOW -> CoachMessageComponents(
                severity = CoachSeverity.INFO,
                title = "Sesión ligera — oportunidad",
                body = "Baja fatiga y readiness moderada. Ideal para trabajar técnica o accesibilidad. ¡Aprovecha!",
                action = CoachAction.STAY_THE_COURSE,
            )
            drain == DrainLevel.LOW && readiness == ReadinessLevel.HIGH -> CoachMessageComponents(
                severity = CoachSeverity.SUCCESS,
                title = "Día óptimo para max effort",
                body = "Baja fatiga y máxima disponibilidad. Ideal para series pesadas o probar RMs.",
                action = CoachAction.STAY_THE_COURSE,
            )
            drain == DrainLevel.XS && readiness == ReadinessLevel.HIGH -> CoachMessageComponents(
                severity = CoachSeverity.SUCCESS,
                title = "¡Energía al máximo!",
                body = "Sesión nueva, cuerpo fresco. Perfecto para series pesadas o explorar nuevos límites.",
                action = CoachAction.STAY_THE_COURSE,
            )
            drain == DrainLevel.XS && readiness == ReadinessLevel.XS -> CoachMessageComponents(
                severity = CoachSeverity.WARNING,
                title = "Disponibilidad reducida",
                body = "Readiness muy baja. Si es posible, considera reprogramar sesiones pesadas para otro día.",
                action = CoachAction.SKIP_EXERCISE,
            )
            drain == DrainLevel.HIGH && readiness == ReadinessLevel.AVERAGE && !isLateSession -> CoachMessageComponents(
                severity = CoachSeverity.INFO,
                title = "Mitad de sesión — evalúa cómo vas",
                body = "Has drenado bastante. Si aún te quedan series pesadas, considera reducir un 5-10% en las últimas.",
                action = CoachAction.EXTEND_REST,
            )
            else -> CoachMessageComponents(
                severity = CoachSeverity.INFO,
                title = "Seguimiento en tiempo real",
                body = "El motor AUGE monitoriza tu fatiga continuamente. Los ajustes de descanso se aplican automáticamente.",
                action = null,
            )
        }
    }

    private data class CoachMessageComponents(
        val severity: CoachSeverity,
        val title: String,
        val body: String,
        val action: CoachAction?,
    )

    fun getMessage(
        weightedDrainPct: Double,
        readinessScore: Int?,
        sessionProgress: Double,
    ): CoachMessage {
        val drainLevel = when {
            weightedDrainPct >= 8.0 -> DrainLevel.HIGH
            weightedDrainPct >= 4.0 -> DrainLevel.AVERAGE
            weightedDrainPct >= 1.5 -> DrainLevel.LOW
            else -> DrainLevel.XS
        }
        val readinessLevel = when (readinessScore) {
            null, in 80..100 -> ReadinessLevel.HIGH
            in 60..79 -> ReadinessLevel.AVERAGE
            in 40..59 -> ReadinessLevel.LOW
            else -> ReadinessLevel.XS
        }
        val phase = when {
            sessionProgress < 0.33 -> SessionPhase.EARLY
            sessionProgress < 0.66 -> SessionPhase.MID
            else -> SessionPhase.LATE
        }
        return COACH_MESSAGES[CoachKey(drainLevel, readinessLevel, phase)]
            ?: COACH_MESSAGES.getValue(CoachKey(DrainLevel.LOW, ReadinessLevel.AVERAGE, SessionPhase.MID))
    }

    fun getReadinessScore(
        neural: Int?,
        spinal: Int?,
        muscular: Int?,
    ): Int? {
        val values = listOfNotNull(neural, spinal, muscular)
        return if (values.isNotEmpty()) values.average().toInt() else null
    }
}
