package com.example.kpkn.domain.onboarding

import com.example.kpkn.data.models.DailyWellbeingLog
import com.example.kpkn.data.models.GlobalBatteries
import com.example.kpkn.data.models.RecoveryChannelId
import com.example.kpkn.data.models.WellbeingSource
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
            fun manual(id: RecoveryChannelId) = hasManualChannel(wellbeing, id)
            fun declared(id: RecoveryChannelId) = when (id) {
                RecoveryChannelId.MUSCULAR -> subjective.muscular != null
                RecoveryChannelId.SYSTEM -> subjective.energy != null
                RecoveryChannelId.STRUCTURE -> subjective.structure != null
            }
            return RingsCoverage(
                muscular = channelCoverage(RecoveryChannelId.MUSCULAR, manual(RecoveryChannelId.MUSCULAR), declared(RecoveryChannelId.MUSCULAR), historyIsEmpty, contribution, channelConfidence(RecoveryChannelId.MUSCULAR), score(RecoveryChannelId.MUSCULAR)),
                system = channelCoverage(RecoveryChannelId.SYSTEM, manual(RecoveryChannelId.SYSTEM), declared(RecoveryChannelId.SYSTEM), historyIsEmpty, contribution, channelConfidence(RecoveryChannelId.SYSTEM), score(RecoveryChannelId.SYSTEM)),
                structure = channelCoverage(RecoveryChannelId.STRUCTURE, manual(RecoveryChannelId.STRUCTURE), declared(RecoveryChannelId.STRUCTURE), historyIsEmpty, contribution, channelConfidence(RecoveryChannelId.STRUCTURE), score(RecoveryChannelId.STRUCTURE)),
            )
        }

        /**
         * Cobertura por canal para Home (y cualquier consumidor post-alta) con las
         * MISMAS entradas del motor: historial, evidencia inicial vigente y la fila
         * de check-in guardada. A diferencia de [fromBatteries], cada canal
         * resuelve su procedencia POR CANAL: un check-in parcial (p. ej. solo
         * muscular) no convierte sistema/estructura en «conocidos».
         *
         * @param declaredChannels canales que el check-in declaró realmente
         *   ([declaredCheckInChannels]); se presentan como estimación subjetiva,
         *   nunca como recuperación confirmada.
         */
        fun fromEngineInputs(
            historyIsEmpty: Boolean,
            wellbeing: DailyWellbeingLog?,
            contribution: InitialRecoveryContribution,
            declaredChannels: Set<RecoveryChannelId> = emptySet(),
            channelConfidence: Map<RecoveryChannelId, Int> = emptyMap(),
            score: (RecoveryChannelId) -> Int,
        ): RingsCoverage {
            fun manual(id: RecoveryChannelId) = hasManualChannel(wellbeing, id)
            return RingsCoverage(
                muscular = channelCoverage(RecoveryChannelId.MUSCULAR, manual(RecoveryChannelId.MUSCULAR), RecoveryChannelId.MUSCULAR in declaredChannels, historyIsEmpty, contribution, channelConfidence[RecoveryChannelId.MUSCULAR], score(RecoveryChannelId.MUSCULAR)),
                system = channelCoverage(RecoveryChannelId.SYSTEM, manual(RecoveryChannelId.SYSTEM), RecoveryChannelId.SYSTEM in declaredChannels, historyIsEmpty, contribution, channelConfidence[RecoveryChannelId.SYSTEM], score(RecoveryChannelId.SYSTEM)),
                structure = channelCoverage(RecoveryChannelId.STRUCTURE, manual(RecoveryChannelId.STRUCTURE), RecoveryChannelId.STRUCTURE in declaredChannels, historyIsEmpty, contribution, channelConfidence[RecoveryChannelId.STRUCTURE], score(RecoveryChannelId.STRUCTURE)),
            )
        }

        /**
         * Cobertura derivada de la procedencia que ya publican los motores en
         * [GlobalBatteries]. «Sin calibrar» significa que no hay historial, check-in
         * ni evidencia: se marca «sin datos» en lugar de exponer el 100 % calculado.
         *
         * Nota: [dataLabel] es UNA etiqueta global para los tres canales; solo se
         * usa como último recurso cuando no hay entradas por canal disponibles
         * ([fromEngineInputs] o un `RingsCoverage` explícito).
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

/** ¿Este canal tiene un ajuste manual real en la fila de check-in? */
private fun hasManualChannel(wellbeing: DailyWellbeingLog?, id: RecoveryChannelId): Boolean = when (id) {
    RecoveryChannelId.MUSCULAR ->
        wellbeing?.manualMuscularBattery != null ||
            wellbeing?.manualMuscleBatteries?.isNotEmpty() == true ||
            wellbeing?.manualMuscleOverridesV2?.isNotEmpty() == true
    RecoveryChannelId.SYSTEM -> wellbeing?.manualNeuralBattery != null
    RecoveryChannelId.STRUCTURE -> wellbeing?.manualSpinalBattery != null
}

/**
 * Resolución de procedencia de UN canal, con la misma precedencia que el motor:
 * estimación inicial (salvo que el canal tenga ajuste manual) → historial real →
 * sensación declarada → ajuste manual → sin datos.
 */
private fun channelCoverage(
    id: RecoveryChannelId,
    manual: Boolean,
    declaredSensation: Boolean,
    historyIsEmpty: Boolean,
    contribution: InitialRecoveryContribution,
    confidence: Int?,
    score: Int?,
): RingsChannelCoverage {
    // Mismo carácter estimado por canal que calcula el motor.
    val estimated = contribution.isEstimated && !manual
    val source = when {
        estimated -> RingsCoverageSource.INITIAL_ESTIMATE
        !historyIsEmpty -> RingsCoverageSource.REAL_HISTORY
        declaredSensation -> RingsCoverageSource.SUBJECTIVE_SENSATION
        manual -> RingsCoverageSource.MANUAL_CHECK_IN
        else -> RingsCoverageSource.NO_DATA
    }
    val uncertainty = confidence?.let { 100 - it.coerceIn(0, 100) }
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
        score = if (source == RingsCoverageSource.NO_DATA) null else score,
    )
}

/**
 * Canales que el check-in REALMENTE declaró en esta fila de wellbeing.
 * `capturedFields` es el registro durable de lo declarado; cuando la fila nació
 * en el alta ([WellbeingSource.ONBOARDING_INITIAL]) un ajuste manual sin campo
 * capturado también identifica su canal (así se preserva la procedencia
 * subjetiva/estimada de la vista previa más allá del alta). Nunca inventa
 * canales: lo no declarado queda fuera y su cobertura sigue siendo «sin datos».
 */
fun declaredCheckInChannels(wellbeing: DailyWellbeingLog?): Set<RecoveryChannelId> {
    wellbeing ?: return emptySet()
    val fields = wellbeing.capturedFields
    val fromOnboarding = wellbeing.source == WellbeingSource.ONBOARDING_INITIAL
    val muscularManual = wellbeing.manualMuscularBattery != null ||
        wellbeing.manualMuscleBatteries.isNotEmpty() ||
        wellbeing.manualMuscleOverridesV2.isNotEmpty()
    return buildSet {
        if ("muscular" in fields || "muscle_batteries" in fields || (fromOnboarding && muscularManual)) {
            add(RecoveryChannelId.MUSCULAR)
        }
        if ("energy" in fields || "energy_rating" in fields || (fromOnboarding && wellbeing.manualNeuralBattery != null)) {
            add(RecoveryChannelId.SYSTEM)
        }
        if ("structure" in fields || (fromOnboarding && wellbeing.manualSpinalBattery != null)) {
            add(RecoveryChannelId.STRUCTURE)
        }
    }
}
