package com.example.kpkn.data.models

import com.example.kpkn.data.db.VideoAnalysisEntity
import com.example.kpkn.data.media.WorkoutMediaPrFlags
import com.example.kpkn.domain.biomechanics.kinematics.ViewKind
import com.example.kpkn.domain.biomechanics.metrics.AnalysisFlag
import com.example.kpkn.domain.biomechanics.metrics.CountConfidence
import com.example.kpkn.domain.biomechanics.metrics.ScaleSource
import com.example.kpkn.domain.biomechanics.metrics.SetTechniqueReport
import com.example.kpkn.domain.biomechanics.metrics.TechniqueAnalysisEngine
import com.example.kpkn.domain.biomechanics.metrics.TechniqueObservation
import com.example.kpkn.domain.biomechanics.metrics.VelocityUnit
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfileId
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.text.Normalizer
import java.util.Locale

/** Estado del análisis de un vídeo (`video_analysis.status`, §3.9). Se guarda por nombre: no reordenar ni renombrar. */
enum class VideoAnalysisStatus { QUEUED, RUNNING, DONE, UNSUPPORTED, NEEDS_PROFILE, FAILED }

/**
 * Espejo de la fila `video_analysis` (§3.9) con sus enums ya tipados: lo que la interfaz lee de Room. La FUENTE DE VERDAD
 * es `<id>.analysis.json` (`TechniqueSidecars`); la fila y este resumen se rehacen desde ahí con [toSummary].
 *
 * Solo hay medidas en un análisis DONE: QUEUED y RUNNING traen el estado, y UNSUPPORTED, NEEDS_PROFILE y FAILED, el motivo
 * ([errorCode]). Un `null` en una medida es un dato que no se pudo medir, nunca un cero; sin dato, la línea no se pinta.
 *
 * @property exerciseKey agrupa el hub y las tendencias de ROM y TUT (ver [techniqueExerciseKey]).
 * @property configurationKey clave del perfil carga-velocidad: la referencia de carga solo vale en la misma configuración.
 * @property setKey clave de la serie en la sesión (`<ejercicio>_<serie>[_L|_R]`), o `null` si el vídeo no es de una serie.
 * @property flagsJson las flags del análisis y sus observaciones en JSON compacto (`{"flags":[…],"observations":[…]}`);
 *   se leen con [flags] y [observations].
 * @property reportPath ruta del `<id>.analysis.json`; `null` mientras no hay informe.
 */
data class VideoAnalysisSummary(
    val mediaId: String,
    val status: VideoAnalysisStatus,
    val progressPct: Int,
    val errorCode: String? = null,
    val engineVersion: Int,
    val poseVersion: Int,
    val poseEngine: String? = null,
    val profileId: AnalysisProfileId? = null,
    val exerciseKey: String,
    val configurationKey: String? = null,
    val exerciseName: String? = null,
    val setKey: String? = null,
    val loadKg: Double? = null,
    val recordedAtMs: Long,
    val sourceFps: Double? = null,
    val analyzedFps: Double? = null,
    val viewKind: ViewKind? = null,
    val confidence: CountConfidence? = null,
    val repCount: Int? = null,
    val partialReps: Int? = null,
    val failedReps: Int? = null,
    val tutMs: Long? = null,
    val tempoEccMs: Int? = null,
    val tempoPauseAfterEccMs: Int? = null,
    val tempoConMs: Int? = null,
    val tempoPauseAfterConMs: Int? = null,
    val romMeanDeg: Double? = null,
    val romCvPct: Double? = null,
    val velocityUnit: VelocityUnit? = null,
    val velocityLossPct: Double? = null,
    val mcvFirst: Double? = null,
    val mcvBest: Double? = null,
    val mcvLast: Double? = null,
    val peakBest: Double? = null,
    val scaleSource: ScaleSource? = null,
    val plateDiameterMm: Int? = null,
    val barDeviation: Double? = null,
    val barDeviationUnit: String? = null,
    val trunkMeanDeg: Double? = null,
    val flagsJson: String = EMPTY_FLAGS_JSON,
    val reportPath: String? = null,
    val createdAtMs: Long,
    val updatedAtMs: Long,
) {
    /** Hay informe terminado: las medidas están en la fila. */
    val isDone: Boolean get() = status == VideoAnalysisStatus.DONE

    /** Las flags del análisis; una flag que esta versión no conoce (de otra versión) se ignora. */
    val flags: Set<AnalysisFlag>
        get() = decodeFlagsPayload(flagsJson).flags.mapNotNullTo(linkedSetOf<AnalysisFlag>()) { KNOWN_FLAGS[it] }

    /** Las observaciones de técnica ya redactadas, en el orden del informe. */
    val observations: List<TechniqueObservation> get() = decodeFlagsPayload(flagsJson).observations

    companion object {
        const val EMPTY_FLAGS_JSON = """{"flags":[],"observations":[]}"""

        /**
         * Versión de la traza de pose con la que se midió, mientras VT4c no fije `POSE_VERSION`: quien escriba la fila
         * pasa la real. Subirla pide reanalizar el vídeo al abrirlo.
         */
        const val DEFAULT_POSE_VERSION = 1

        /**
         * Fila sin medidas: un vídeo en cola o en curso (QUEUED, RUNNING) o que no se pudo analizar (UNSUPPORTED,
         * NEEDS_PROFILE, FAILED, con su [errorCode]). Las claves salen del medio y se pasan hechas, igual que en [toSummary].
         */
        fun pending(
            media: WorkoutMedia,
            status: VideoAnalysisStatus,
            exerciseKey: String,
            configurationKey: String?,
            loadKg: Double?,
            nowMs: Long,
            progressPct: Int = 0,
            errorCode: String? = null,
            engineVersion: Int = TechniqueAnalysisEngine.ENGINE_VERSION,
            poseVersion: Int = DEFAULT_POSE_VERSION,
            createdAtMs: Long = nowMs,
        ): VideoAnalysisSummary = VideoAnalysisSummary(
            mediaId = media.id,
            status = status,
            progressPct = progressPct.coerceIn(0, 100),
            errorCode = errorCode,
            engineVersion = engineVersion,
            poseVersion = poseVersion,
            exerciseKey = exerciseKey,
            configurationKey = configurationKey,
            exerciseName = media.exerciseName,
            setKey = media.techniqueSetKey(),
            loadKg = loadKg.finiteOrNull(),
            recordedAtMs = media.createdAtMs,
            createdAtMs = createdAtMs,
            updatedAtMs = nowMs,
        )
    }
}

/**
 * El resumen DONE de un informe (§3.5 → §3.9). Las claves ([exerciseKey], [configurationKey]) las calcula quien llama con
 * [techniqueExerciseKey] y [techniqueConfigurationKey]; [reportPath] es el `<id>.analysis.json` ya escrito.
 *
 * Una cifra que no es finita (NaN, infinito) se guarda como «sin dato»: SQLite no distingue NaN de NULL y un cero sería mentir.
 * [poseVersion], [poseEngine] y [createdAtMs] se pasan solo si se conocen (la fila ya existía, o hay traza de pose).
 */
fun SetTechniqueReport.toSummary(
    media: WorkoutMedia,
    exerciseKey: String,
    configurationKey: String?,
    loadKg: Double?,
    reportPath: String,
    nowMs: Long,
    poseVersion: Int = VideoAnalysisSummary.DEFAULT_POSE_VERSION,
    poseEngine: String? = null,
    createdAtMs: Long = nowMs,
): VideoAnalysisSummary = VideoAnalysisSummary(
    mediaId = media.id,
    status = VideoAnalysisStatus.DONE,
    progressPct = 100,
    errorCode = null,
    engineVersion = engineVersion,
    poseVersion = poseVersion,
    poseEngine = poseEngine,
    profileId = profileId,
    exerciseKey = exerciseKey,
    configurationKey = configurationKey,
    exerciseName = media.exerciseName,
    setKey = media.techniqueSetKey(),
    loadKg = loadKg.finiteOrNull(),
    recordedAtMs = media.createdAtMs,
    sourceFps = sourceFps.finiteOrNull(),
    analyzedFps = analyzedFps.finiteOrNull(),
    viewKind = view,
    confidence = confidence,
    repCount = repCount,
    partialReps = partialReps,
    failedReps = failedReps,
    tutMs = tutMs,
    tempoEccMs = tempo?.eccentricMs,
    tempoPauseAfterEccMs = tempo?.pauseAfterEccentricMs,
    tempoConMs = tempo?.concentricMs,
    tempoPauseAfterConMs = tempo?.pauseAfterConcentricMs,
    romMeanDeg = romMeanDeg.finiteOrNull(),
    romCvPct = romCvPct.finiteOrNull(),
    velocityUnit = velocityUnit,
    velocityLossPct = velocityLossPct.finiteOrNull(),
    mcvFirst = mcvFirst.finiteOrNull(),
    mcvBest = mcvBest.finiteOrNull(),
    mcvLast = mcvLast.finiteOrNull(),
    peakBest = peakBest.finiteOrNull(),
    scaleSource = scale?.source,
    plateDiameterMm = scale?.plateDiameterMm,
    barDeviation = barDeviation.finiteOrNull(),
    barDeviationUnit = barDeviationUnit,
    trunkMeanDeg = trunkMeanDeg.finiteOrNull(),
    flagsJson = encodeFlagsPayload(flags, observations),
    reportPath = reportPath,
    createdAtMs = createdAtMs,
    updatedAtMs = nowMs,
)

/**
 * `exerciseKey` (§3.9): la definición del catálogo, o el ejercicio canónico, o `"name:"` y el nombre sin acentos ni
 * mayúsculas. Agrupa el hub y las tendencias de ROM y TUT de un mismo ejercicio entre sesiones.
 */
fun WorkoutMedia.techniqueExerciseKey(): String =
    catalogDefinitionId?.takeIf { it.isNotBlank() }
        ?: canonicalExerciseId?.takeIf { it.isNotBlank() }
        ?: ("name:" + normalizeExerciseName(exerciseName))

/**
 * `configurationKey` (§3.9): la configuración exacta del catálogo (`bench_press__barbell`), o `null`. Es la clave del
 * perfil carga-velocidad, porque la referencia de carga solo vale en la misma configuración.
 */
fun WorkoutMedia.techniqueConfigurationKey(): String? = catalogConfigurationId?.takeIf { it.isNotBlank() }

/** Clave de la serie del vídeo en la sesión (la de `workoutSetKey`, con el lado normalizado), o `null` si no es de una serie. */
fun WorkoutMedia.techniqueSetKey(): String? {
    val exercise = exerciseId?.takeIf { it.isNotBlank() } ?: return null
    val index = setIndex ?: return null
    return WorkoutMediaPrFlags.completedSetKey(exercise, index, side)
}

/** La fila de Room de un resumen: los enums se guardan por NOMBRE (la columna de la vista es `viewKind`, no `view`). */
fun VideoAnalysisSummary.toEntity(): VideoAnalysisEntity = VideoAnalysisEntity(
    mediaId = mediaId,
    status = status.name,
    progressPct = progressPct,
    errorCode = errorCode,
    engineVersion = engineVersion,
    poseVersion = poseVersion,
    poseEngine = poseEngine,
    profileId = profileId?.name,
    exerciseKey = exerciseKey,
    configurationKey = configurationKey,
    exerciseName = exerciseName,
    setKey = setKey,
    loadKg = loadKg,
    recordedAtMs = recordedAtMs,
    sourceFps = sourceFps,
    analyzedFps = analyzedFps,
    viewKind = viewKind?.name,
    confidence = confidence?.name,
    repCount = repCount,
    partialReps = partialReps,
    failedReps = failedReps,
    tutMs = tutMs,
    tempoEccMs = tempoEccMs,
    tempoPauseAfterEccMs = tempoPauseAfterEccMs,
    tempoConMs = tempoConMs,
    tempoPauseAfterConMs = tempoPauseAfterConMs,
    romMeanDeg = romMeanDeg,
    romCvPct = romCvPct,
    velocityUnit = velocityUnit?.name,
    velocityLossPct = velocityLossPct,
    mcvFirst = mcvFirst,
    mcvBest = mcvBest,
    mcvLast = mcvLast,
    peakBest = peakBest,
    scaleSource = scaleSource?.name,
    plateDiameterMm = plateDiameterMm,
    barDeviation = barDeviation,
    barDeviationUnit = barDeviationUnit,
    trunkMeanDeg = trunkMeanDeg,
    flagsJson = flagsJson,
    reportPath = reportPath,
    createdAtMs = createdAtMs,
    updatedAtMs = updatedAtMs,
)

/** Un nombre de enum que esta versión no conoce (una fila de otra versión) se lee como «sin dato», nunca como error. */
private inline fun <reified E : Enum<E>> String?.asEnum(): E? =
    this?.let { name -> enumValues<E>().firstOrNull { it.name == name } }

fun VideoAnalysisEntity.toSummary(): VideoAnalysisSummary = VideoAnalysisSummary(
    mediaId = mediaId,
    // Un estado desconocido no puede quedarse «en curso» para siempre: se muestra como fallo.
    status = status.asEnum<VideoAnalysisStatus>() ?: VideoAnalysisStatus.FAILED,
    progressPct = progressPct,
    errorCode = errorCode,
    engineVersion = engineVersion,
    poseVersion = poseVersion,
    poseEngine = poseEngine,
    profileId = profileId.asEnum<AnalysisProfileId>(),
    exerciseKey = exerciseKey,
    configurationKey = configurationKey,
    exerciseName = exerciseName,
    setKey = setKey,
    loadKg = loadKg,
    recordedAtMs = recordedAtMs,
    sourceFps = sourceFps,
    analyzedFps = analyzedFps,
    viewKind = viewKind.asEnum<ViewKind>(),
    confidence = confidence.asEnum<CountConfidence>(),
    repCount = repCount,
    partialReps = partialReps,
    failedReps = failedReps,
    tutMs = tutMs,
    tempoEccMs = tempoEccMs,
    tempoPauseAfterEccMs = tempoPauseAfterEccMs,
    tempoConMs = tempoConMs,
    tempoPauseAfterConMs = tempoPauseAfterConMs,
    romMeanDeg = romMeanDeg,
    romCvPct = romCvPct,
    velocityUnit = velocityUnit.asEnum<VelocityUnit>(),
    velocityLossPct = velocityLossPct,
    mcvFirst = mcvFirst,
    mcvBest = mcvBest,
    mcvLast = mcvLast,
    peakBest = peakBest,
    scaleSource = scaleSource.asEnum<ScaleSource>(),
    plateDiameterMm = plateDiameterMm,
    barDeviation = barDeviation,
    barDeviationUnit = barDeviationUnit,
    trunkMeanDeg = trunkMeanDeg,
    flagsJson = flagsJson,
    reportPath = reportPath,
    createdAtMs = createdAtMs,
    updatedAtMs = updatedAtMs,
)

/** Minúsculas, sin acentos y con los espacios (también los de no separación) colapsados: «Sentadilla  Búlgara » → «sentadilla bulgara». */
fun normalizeExerciseName(name: String?): String =
    Normalizer.normalize(name.orEmpty(), Normalizer.Form.NFD)
        .replace(COMBINING_MARKS, "")
        .lowercase(Locale.ROOT)
        .trim()
        .replace(WHITESPACE_RUN, " ")

private val COMBINING_MARKS = Regex("\\p{Mn}+")
private val WHITESPACE_RUN = Regex("[\\s\\u00A0]+")
private val KNOWN_FLAGS: Map<String, AnalysisFlag> = AnalysisFlag.entries.associateBy { it.name }

private fun Double?.finiteOrNull(): Double? = this?.takeIf { it.isFinite() }

@Serializable
private data class FlagsPayload(
    val flags: List<String> = emptyList(),
    val observations: List<TechniqueObservation> = emptyList(),
)

// Compacto y con los valores por defecto, como el resto de JSON de la ola; un NaN suelto no debe tirar el análisis entero.
private val flagsJsonCodec = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
    allowSpecialFloatingPointValues = true
}

private fun encodeFlagsPayload(flags: Set<AnalysisFlag>, observations: List<TechniqueObservation>): String =
    flagsJsonCodec.encodeToString(
        FlagsPayload.serializer(),
        FlagsPayload(flags = flags.map { it.name }.sorted(), observations = observations),
    )

private fun decodeFlagsPayload(json: String): FlagsPayload =
    try {
        flagsJsonCodec.decodeFromString(FlagsPayload.serializer(), json)
    } catch (_: IllegalArgumentException) {
        // SerializationException hereda de IllegalArgumentException: un JSON corrupto no es un error de la interfaz.
        FlagsPayload()
    }
