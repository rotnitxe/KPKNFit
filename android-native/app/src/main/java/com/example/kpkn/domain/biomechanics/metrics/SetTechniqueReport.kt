package com.example.kpkn.domain.biomechanics.metrics

import com.example.kpkn.domain.biomechanics.kinematics.ViewKind
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfileId
import com.example.kpkn.domain.biomechanics.profiles.CountSignal
import com.example.kpkn.domain.biomechanics.profiles.ProfileTier
import com.example.kpkn.domain.biomechanics.reps.SignalUnit
import kotlinx.serialization.Serializable

// El informe de una serie (§3.5): lo que el motor mide de un vídeo. Es la FUENTE DE VERDAD del análisis: se guarda tal
// cual en `<id>.analysis.json` y la fila de Room se reconstruye desde aquí. Los enums se guardan por NOMBRE: no reordenar
// ni renombrar. Los campos nuevos van AL FINAL con un valor por defecto, para leer informes antiguos.

/** Clase de profundidad de una sentadilla (§7): BELOW si la cadera queda bajo la rodilla (α ≥ +5°), PARALLEL a la par, ABOVE por encima. */
enum class DepthClass { BELOW, PARALLEL, ABOVE }

/** Unidad de la velocidad. MPS solo con el disco calibrado (VT3b); sin calibrar, alturas corporales por segundo o grados por segundo. */
enum class VelocityUnit { MPS, BODY_HEIGHTS_PER_S, DEG_PER_S }

/** De dónde sale la escala en metros: del disco de diámetro conocido, o de ninguna parte. */
enum class ScaleSource { PLATE, NONE }

/**
 * Qué se pudo medir en el vídeo (§3.5). La interfaz solo pinta las líneas cuyo dato está aquí: sin dato, no se pinta.
 * SYMMETRY y VELOCITY_MPS, PEAK_VELOCITY (en m/s) y PATH_ABSOLUTE los marca VT3b cuando hay disco calibrado.
 */
enum class MetricKey {
    REPS, PHASES, TUT, TEMPO, ROM, DEPTH, LOCKOUT, PATH, PATH_ABSOLUTE, TRUNK, VELOCITY_RELATIVE,
    VELOCITY_MPS, PEAK_VELOCITY, SYMMETRY,
}

/**
 * Los cuatro instantes de una repetición, en µs sobre la misma base que `PoseFrameV2.tUs`: sale del reposo, llega al giro,
 * sale del giro y vuelve al reposo. Sin pausa de giro (< 0,25 s), [turnStartUs] = [turnEndUs].
 */
@Serializable
data class RepPhaseTimes(val departUs: Long, val turnStartUs: Long, val turnEndUs: Long, val arriveUs: Long)

/**
 * Una repetición detectada y lo que se midió en ella. Un campo `null` es un dato que no se pudo medir (la vista no lo
 * permite, falta un punto o la repetición no lo tiene), nunca un cero.
 *
 * - [side]: `"left"` o `"right"` en el conteo por lado; `null` si cuentan los dos o no se sabe.
 * - [amplitudeFraction]: recorrido relativo al de una repetición completa (1,0 = completa).
 * - [romDeg]: rango del ángulo de la articulación del perfil durante la repetición.
 * - [depthDeg]: sentadilla, α en el fondo (> 0: cadera bajo la rodilla); flexiones, θE mínimo; fondos, α del hombro
 *   respecto del codo. [depthClass] solo en la sentadilla.
 * - [criteria]: `chinOverBar`, `fullHang`, `lockout`, `pushUpFull`, `dipParallel` y `shallower`; solo están las que el perfil
 *   y la vista permiten medir.
 * - [mcv] y [peakVelocity]: velocidad media y pico de la concéntrica en [VelocityUnit.BODY_HEIGHTS_PER_S] o
 *   [VelocityUnit.DEG_PER_S] (la unidad va en `SetTechniqueReport.velocityUnit`); el pico, solo con fs ≥ 50.
 * - [pathOffsetMean] y [pathDrift]: en la unidad de `SetTechniqueReport.barDeviationUnit` (% de ROM_v sin calibrar); la deriva
 *   lleva signo (+ hacia donde mira el atleta). [pathExcursionFraction]: excursión horizontal / ROM_v, sin unidad.
 * - [pathDeviationRms]: banca, desviación RMS de la trayectoria respecto de la mediana, en % de ROM_v.
 * - [trunkAtTurnDeg], [trunkRangeDeg], [trunkEarlyDeltaDeg] y [trunkMaxDeg]: inclinación del tronco τ (+ hacia delante) en el
 *   giro, su rango, el cambio del inicio de la concéntrica al 30 % de su desplazamiento y su máximo.
 * - [zone] (M4): la zona muscular de una PARTIAL (en estiramiento o acortada); `null` en las demás. Su fracción de recorrido
 *   es [amplitudeFraction].
 * - [amplitude] (M4): el recorrido de la repetición en la unidad de la señal de conteo (`SetTechniqueReport.countUnit`).
 */
@Serializable
data class RepReport(
    val index: Int,
    val kind: RepKind,
    val partialKind: PartialKind? = null,
    val side: String? = null,
    val phases: RepPhaseTimes,
    val eccentricMs: Int?,
    val turnPauseMs: Int?,
    val concentricMs: Int?,
    val restPauseMs: Int?,
    val amplitudeFraction: Double,
    val romDeg: Double?,
    val depthClass: DepthClass? = null,
    val depthDeg: Double? = null,
    val criteria: Map<String, Boolean> = emptyMap(),
    val mcv: Double? = null,
    val peakVelocity: Double? = null,
    val pathOffsetMean: Double? = null,
    val pathDrift: Double? = null,
    val pathExcursionFraction: Double? = null,
    val pathDeviationRms: Double? = null,
    val trunkAtTurnDeg: Double? = null,
    val trunkRangeDeg: Double? = null,
    val trunkEarlyDeltaDeg: Double? = null,
    // Añadido por VT3a (§7: «τ máx.»).
    val trunkMaxDeg: Double? = null,
    // Añadidos por M4, con valor por defecto (los informes viejos no los traen).
    val zone: PartialZone? = null,
    val amplitude: Double? = null,
)

/**
 * Escala del vídeo. Sin disco calibrado (VT3a) es [NONE]: no hay metros, solo alturas corporales.
 * [axisRatio] es el eje menor/mayor de la elipse del disco y [parallaxErrorCm] el error de paralaje del desvío absoluto.
 */
@Serializable
data class ScaleInfo(
    val source: ScaleSource,
    val plateDiameterMm: Int?,
    val metersPerPxH: Double?,
    val metersPerPxV: Double?,
    val axisRatio: Double?,
    val parallaxErrorCm: Double?,
) {
    companion object {
        /** Sin escala en metros. */
        val NONE: ScaleInfo = ScaleInfo(ScaleSource.NONE, null, null, null, null, null)
    }
}

/**
 * Una observación de técnica (VT3b): un hecho con su cifra y su repetición. [code] es estable, [text] ya viene redactado,
 * [repIndices] son las repeticiones (índices de `RepReport.index`), [atUs] el instante al que salta el reproductor,
 * [magnitude] su tamaño y [rank] el orden en que se muestran.
 */
@Serializable
data class TechniqueObservation(
    val code: String,
    val text: String,
    val repIndices: List<Int>,
    val atUs: Long?,
    val magnitude: Double,
    val rank: Double,
)

/**
 * Lo que el motor mide de una serie (§3.5).
 *
 * - [repCount], [partialReps] y [failedReps]: repeticiones FULL, PARTIAL y FAILED; [miniSets]: mini-series (rest-pause).
 * - [perSideCounts]: FULL por lado (`"left"`, `"right"`) en el conteo por lado; vacío en los demás.
 * - [tutMs], [setDurationMs], [restPositionMs] y [tempo]: ver `RepPhases`.
 * - [romMeanDeg], [romSdDeg], [romCvPct] y [romTrendDeg]: sobre las FULL; la tendencia necesita ≥ 4 FULL.
 * - [velocityUnit], [mcvFirst], [mcvBest], [mcvLast], [peakBest] y [velocityLossPct]: sobre las FULL; la pérdida, con ≥ 3.
 * - [trunkMeanDeg]: media de τ en el giro de las FULL.
 * - [barDeviation] y [barDeviationUnit]: `"%ROM"` o `"cm"`; mediana del desvío de la barra respecto del medio pie (sentadilla,
 *   peso muerto, press militar) o de la desviación respecto de la trayectoria mediana (banca), en % de ROM_v.
 * - [available]: qué métricas se midieron; [flags]: avisos del análisis.
 * - [analyzedFps]: cadencia de los fotogramas analizados; [sourceFps], la de la grabación.
 * - [noRepsReason] (VT3a): por qué no hay repeticiones, si no las hay (`no_pose`, `no_scale`, `too_short`, `low_amplitude`…).
 * - [romSlopeDegPerRep] (VT3a): pendiente OLS del ROM de las FULL, en °/rep, con las mismas FULL que [romTrendDeg].
 * - [countSignal] y [countUnit] (M4): la señal de conteo y su unidad. Una referencia de recorrido solo vale entre informes
 *   de la misma señal.
 * - [fullAmplitude] (M4): la mediana del recorrido (`RepReport.amplitude`) de las FULL, en [countUnit]. La referencia
 *   PERSONAL de las series siguientes es la mediana de este valor en las series anteriores (ALTA o MEDIA).
 * - [rangeReference] (M4): la referencia de recorrido que recibió el análisis y si se aplicó.
 * - [partialConfidence] (M4): la confianza propia del número de parciales ([partialReps]): ALTA, el número; MEDIA, «≈»;
 *   BAJA, no se ofrece. `null` sin salida.
 */
@Serializable
data class SetTechniqueReport(
    val engineVersion: Int,
    val profileId: AnalysisProfileId,
    val tier: ProfileTier,
    val view: ViewKind,
    val nearSide: String?,
    val confidence: CountConfidence,
    val confidenceScore: Double,
    val reps: List<RepReport>,
    val repCount: Int,
    val partialReps: Int,
    val failedReps: Int,
    val miniSets: Int,
    val perSideCounts: Map<String, Int> = emptyMap(),
    val tutMs: Long?,
    val setDurationMs: Long?,
    val restPositionMs: Long?,
    val tempo: TempoSummary?,
    val romMeanDeg: Double?,
    val romSdDeg: Double?,
    val romCvPct: Double?,
    val romTrendDeg: Double?,
    val velocityUnit: VelocityUnit?,
    val velocityLossPct: Double?,
    val mcvFirst: Double?,
    val mcvBest: Double?,
    val mcvLast: Double?,
    val peakBest: Double?,
    val scale: ScaleInfo?,
    val trunkMeanDeg: Double?,
    val barDeviation: Double?,
    val barDeviationUnit: String?,
    val observations: List<TechniqueObservation>,
    val available: Set<MetricKey>,
    val flags: Set<AnalysisFlag>,
    val analyzedFps: Double,
    val sourceFps: Double?,
    // Añadidos por VT3a, con valor por defecto.
    val noRepsReason: String? = null,
    val romSlopeDegPerRep: Double? = null,
    // Añadidos por M4, con valor por defecto (los informes viejos no los traen).
    val countSignal: CountSignal? = null,
    val countUnit: SignalUnit? = null,
    val fullAmplitude: Double? = null,
    val rangeReference: RangeReferenceUse? = null,
    val partialConfidence: CountConfidence? = null,
)
