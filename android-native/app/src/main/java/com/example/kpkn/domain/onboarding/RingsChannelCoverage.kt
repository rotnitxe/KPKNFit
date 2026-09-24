package com.example.kpkn.domain.onboarding

import com.example.kpkn.data.models.DailyWellbeingLog
import com.example.kpkn.data.models.GlobalBatteries
import com.example.kpkn.data.models.RecoveryChannelId
import com.example.kpkn.domain.auge.InitialRecoveryContribution

/**
 * Procedencia del dato que sostiene un canal de RINGS. No estima recuperación:
 * describe de dónde sale el número y cuánta incertidumbre arrastra.
 */
enum class RingsCoverageSource(val label: String, val isEstimated: Boolean) {
    /** Historial real de entrenamiento registrado. */
    REAL_HISTORY("Historial real", false),

    /** Estimación inicial derivada de la evidencia declarada en el alta. */
    INITIAL_ESTIMATE("Estimación inicial", true),

    /** Ajuste manual declarado (el check-in manda ese día, con decaimiento). */
    MANUAL_CHECK_IN("Ajuste manual", false),

    /** Solo sensación subjetiva declarada: nunca recuperación fisiológica confirmada. */
    SUBJECTIVE_SENSATION("Estimación subjetiva", true),

    /** Sin datos. No se afirma ningún porcentaje y no equivale a no haber entrenado. */
    NO_DATA("Sin datos", false),
}

/** Estado de cobertura de un canal (muscular / sistema / estructura). */
data class RingsChannelCoverage(
    val channel: RecoveryChannelId,
    val source: RingsCoverageSource,
    /** Incertidumbre 0-100; 100 = nada seguro. */
    val uncertainty: Int,
    /** Valor a mostrar; `null` cuando no hay datos (nunca un 100 % afirmativo). */
    val score: Int?,
) {
    val isEstimated: Boolean get() = source.isEstimated
    val hasData: Boolean get() = score != null
    val label: String get() = source.label
}

/**
 * Cobertura por canal de los tres RINGS. Con `history = []`, sin evidencia y sin
 * check-in los motores devuelven 100/100/100 con «Sin calibrar»: ese valor no se
 * expone como recuperación confirmada, sino como «sin datos».
 */
data class RingsCoverage(
    val muscular: RingsChannelCoverage,
    val system: RingsChannelCoverage,
    val structure: RingsChannelCoverage,
) {
    fun channel(id: RecoveryChannelId): RingsChannelCoverage = when (id) {
        RecoveryChannelId.MUSCULAR -> muscular
        RecoveryChannelId.SYSTEM -> system
        RecoveryChannelId.STRUCTURE -> structure
    }

    val hasAnyData: Boolean get() = muscular.hasData || system.hasData || structure.hasData

    companion object {
        /** Cobertura sin datos: no afirma ningún porcentaje. */
        val NO_DATA: RingsCoverage = RingsCoverage(
            muscular = RingsChannelCoverage(RecoveryChannelId.MUSCULAR, RingsCoverageSource.NO_DATA, 100, null),
            system = RingsChannelCoverage(RecoveryChannelId.SYSTEM, RingsCoverageSource.NO_DATA, 100, null),
            structure = RingsChannelCoverage(RecoveryChannelId.STRUCTURE, RingsCoverageSource.NO_DATA, 100, null),
        )

        /**
         * Deriva la cobertura con las mismas entradas que
         * `AugeRecoveryEngine.calculateGlobalBatteries`, reutilizando la resolución
         * de [InitialRecoveryEvidencePolicy] ya calculada por el motor.
         */
        fun evaluate(
            historyIsEmpty: Boolean,
            wellbeing: DailyWellbeingLog?,
            contribution: InitialRecoveryContribution,
            subjective: SetupRingsCheckIn = SetupRingsCheckIn(),
            channelConfidence: (RecoveryChannelId) -> Int? = { null },
            score: (RecoveryChannelId) -> Int,
        ): RingsCoverage {
            fun channel(id: RecoveryChannelId): RingsChannelCoverage {
                val manual = when (id) {
                    RecoveryChannelId.MUSCULAR ->
                        wellbeing?.manualMuscularBattery != null ||
                            wellbeing?.manualMuscleBatteries?.isNotEmpty() == true ||
                            wellbeing?.manualMuscleOverridesV2?.isNotEmpty() == true
                    RecoveryChannelId.SYSTEM -> wellbeing?.manualNeuralBattery != null
                    RecoveryChannelId.STRUCTURE -> wellbeing?.manualSpinalBattery != null
                }
                val declaredSensation = when (id) {
                    RecoveryChannelId.MUSCULAR -> subjective.muscular != null
                    RecoveryChannelId.SYSTEM -> subjective.energy != null
                    RecoveryChannelId.STRUCTURE -> subjective.structure != null
                }
                // Mismo carácter estimado por canal que calcula el motor.
                val estimated = contribution.isEstimated && !manual
                val source = when {
                    estimated -> RingsCoverageSource.INITIAL_ESTIMATE
                    !historyIsEmpty -> RingsCoverageSource.REAL_HISTORY
                    declaredSensation -> RingsCoverageSource.SUBJECTIVE_SENSATION
                    manual -> RingsCoverageSource.MANUAL_CHECK_IN
                    else -> RingsCoverageSource.NO_DATA
                }
                val uncertainty = channelConfidence(id)?.let { 100 - it.coerceIn(0, 100) }
                    ?: when (source) {
                        RingsCoverageSource.REAL_HISTORY -> 20
                        RingsCoverageSource.INITIAL_ESTIMATE -> 100 - contribution.confidence.coerceIn(0, 100)
                        RingsCoverageSource.MANUAL_CHECK_IN -> 40
                        RingsCoverageSource.SUBJECTIVE_SENSATION -> 70
                        RingsCoverageSource.NO_DATA -> 100
                    }
                return RingsChannelCoverage(
                    channel = id,
                    source = source,
                    uncertainty = uncertainty.coerceIn(0, 100),
                    score = if (source == RingsCoverageSource.NO_DATA) null else score(id),
                )
            }
            return RingsCoverage(
                muscular = channel(RecoveryChannelId.MUSCULAR),
                system = channel(RecoveryChannelId.SYSTEM),
                structure = channel(RecoveryChannelId.STRUCTURE),
            )
        }

        /**
         * Cobertura derivada de la procedencia que ya publican los motores en
         * [GlobalBatteries]. «Sin calibrar» significa que no hay historial, check-in
         * ni evidencia: se marca «sin datos» en lugar de exponer el 100 % calculado.
         */
        fun fromBatteries(
            batteries: GlobalBatteries?,
            dataLabel: String? = null,
            channelConfidence: Map<RecoveryChannelId, Int> = emptyMap(),
        ): RingsCoverage {
            val label = dataLabel ?: batteries?.sourceLabel
            val source = when (label) {
                "Estimación inicial" -> RingsCoverageSource.INITIAL_ESTIMATE
                "Historial real" -> RingsCoverageSource.REAL_HISTORY
                "Ajuste manual" -> RingsCoverageSource.MANUAL_CHECK_IN
                else -> RingsCoverageSource.NO_DATA
            }
            fun channel(id: RecoveryChannelId, engineScore: Int?): RingsChannelCoverage {
                val uncertainty = channelConfidence[id]?.let { 100 - it.coerceIn(0, 100) }
                    ?: when (source) {
                        RingsCoverageSource.INITIAL_ESTIMATE -> 100 - (batteries?.sourceConfidence ?: 44).coerceIn(0, 100)
                        RingsCoverageSource.REAL_HISTORY -> 20
                        RingsCoverageSource.MANUAL_CHECK_IN -> 40
                        RingsCoverageSource.SUBJECTIVE_SENSATION -> 70
                        RingsCoverageSource.NO_DATA -> 100
                    }
                return RingsChannelCoverage(
                    channel = id,
                    source = source,
                    uncertainty = uncertainty.coerceIn(0, 100),
                    score = if (source == RingsCoverageSource.NO_DATA) null else engineScore,
                )
            }
            return RingsCoverage(
                muscular = channel(RecoveryChannelId.MUSCULAR, batteries?.muscular),
                system = channel(RecoveryChannelId.SYSTEM, batteries?.cnc),
                structure = channel(RecoveryChannelId.STRUCTURE, batteries?.spinal),
            )
        }
    }
}
