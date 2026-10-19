package com.example.kpkn.screens.technique.analysis

import com.example.kpkn.domain.biomechanics.bar.PlatePublicationPolicy

import com.example.kpkn.domain.biomechanics.bar.PlateTrackingContinuity

import androidx.compose.runtime.Immutable
import com.example.kpkn.data.models.WorkoutMedia
import com.example.kpkn.data.repository.CaptureOverrides
import com.example.kpkn.domain.biomechanics.bar.BarTrack
import com.example.kpkn.domain.biomechanics.bar.PlateSeed
import com.example.kpkn.domain.biomechanics.kinematics.ViewKind
import com.example.kpkn.domain.biomechanics.metrics.AnalysisFlag
import com.example.kpkn.domain.biomechanics.metrics.CountConfidence
import com.example.kpkn.domain.biomechanics.metrics.DepthClass
import com.example.kpkn.domain.biomechanics.metrics.MetricKey
import com.example.kpkn.domain.biomechanics.metrics.ObservationText
import com.example.kpkn.domain.biomechanics.metrics.RepKind
import com.example.kpkn.domain.biomechanics.metrics.RepReport
import com.example.kpkn.domain.biomechanics.metrics.ScaleSource
import com.example.kpkn.domain.biomechanics.metrics.SetTechniqueReport
import com.example.kpkn.domain.biomechanics.metrics.VelocityUnit
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfileId
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfiles
import com.example.kpkn.domain.biomechanics.profiles.PathRule
import com.example.kpkn.domain.biomechanics.profiles.UnsupportedReasons
import com.example.kpkn.screens.technique.player.TechniqueOverlayData
import com.example.kpkn.screens.technique.player.TechniqueRepMark
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

// Lo que pinta la pantalla «Análisis de técnica» (VT7b), ya redactado y sin Android: el ViewModel lo arma desde la fila de Room y
// los sidecars, y los composables de `design/` lo pintan tal cual (la hoja de render los alimenta con datos de ejemplo). Aquí viven
// también las reglas de HONESTIDAD de la pantalla, todas en funciones puras que se prueban en la JVM:
//  - sin dato, la línea no se pinta;
//  - la pérdida de velocidad con VELOCITY_RELATIVE pero sin `velocityLossPct` es «sin dato fiable», nunca un 0 %;
//  - m/s solo con `velocityUnit == MPS` y siempre con «≈»;
//  - las observaciones se pintan tal cual las redactó el motor;
//  - las estimaciones (conteo con confianza no ALTA, profundidad, ROM en vista oblicua, desvío sin calibrar) llevan «≈».

/** En qué punto está la carga de la pantalla. */
enum class AnalysisPhase { LOADING, MEDIA_MISSING, READY }

/**
 * Todo lo que la pantalla pinta. [status] dice qué hay del análisis (y [results] lo trae solo con el análisis terminado). [overlay]
 * es la capa del reproductor ya preparada (sus marcas de repetición son las de [results]); [notice] es un aviso de que una acción
 * del usuario no se pudo guardar.
 */
@Immutable
data class TechniqueAnalysisUiState(
    val phase: AnalysisPhase = AnalysisPhase.LOADING,
    val exerciseName: String = "",
    val setInfo: String? = null,
    val videoPath: String? = null,
    /** Proporción ancho/alto del vídeo girado tal como se ve; 0 si no se conoce. */
    val videoAspect: Float = 0f,
    val overlay: TechniqueOverlayData? = null,
    val status: AnalysisStatusUi = AnalysisStatusUi.NotAnalyzed,
    val results: AnalysisResultsUi? = null,
    val exerciseKey: String? = null,
    /** Perfil con el que se midió, para preseleccionarlo al «elegir el tipo de movimiento». */
    val profileId: AnalysisProfileId? = null,
    /** El ejercicio es propio o el perfil vino de una elección del usuario. */
    val canChooseMovement: Boolean = false,
    val notice: String? = null,
) {
    /** Hay un análisis terminado de este ejercicio al que abrir la tendencia. */
    val canOpenTrends: Boolean get() = exerciseKey != null && results != null

    val isAnalyzing: Boolean get() = status is AnalysisStatusUi.Analyzing

    companion object {
        val Loading: TechniqueAnalysisUiState = TechniqueAnalysisUiState()
        val MediaMissing: TechniqueAnalysisUiState = TechniqueAnalysisUiState(phase = AnalysisPhase.MEDIA_MISSING)
    }
}

/** Qué hay del análisis del vídeo según la fila de Room (§3.9). */
sealed interface AnalysisStatusUi {
    /** Análisis terminado: no hay estado que enseñar, solo [AnalysisResultsUi]. */
    data object Done : AnalysisStatusUi

    /** El vídeo no tiene fila de análisis (no se analizó nunca). */
    data object NotAnalyzed : AnalysisStatusUi

    /**
     * QUEUED o RUNNING. [progressPct] va de 0 a 100 y nunca baja; [updating] = se recalcula con un motor nuevo (RECOMPUTE) y no es un
     * análisis desde cero; [queued] = aún en cola (no ha empezado).
     */
    @Immutable
    data class Analyzing(val progressPct: Int, val updating: Boolean, val queued: Boolean) : AnalysisStatusUi

    /** UNSUPPORTED: [reason] es el texto del motivo, ya en español. */
    @Immutable
    data class Unsupported(val reason: String) : AnalysisStatusUi

    /** NEEDS_PROFILE: [guess] es la conjetura del resolver, preseleccionada en la lista. */
    @Immutable
    data class NeedsProfile(val guess: AnalysisProfileId?) : AnalysisStatusUi

    /** FAILED: [message] es la causa en una frase. */
    @Immutable
    data class Failed(val message: String) : AnalysisStatusUi

    /** DONE, pero `<id>.analysis.json` no se puede leer: hay que volver a calcular. */
    data object ReportMissing : AnalysisStatusUi
}

/** Los módulos de un análisis terminado. Un módulo sin dato es `null` o una lista vacía, y no se pinta. */
@Immutable
data class AnalysisResultsUi(
    val summary: AnalysisSummaryUi,
    val breakdown: RepBreakdownUi?,
    val observations: List<ObservationUi>,
    val plate: PlateModuleUi?,
    val tips: List<CameraTip>,
)

// ─────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────
// Resumen
// ─────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────

enum class SummaryTileId { REPS, TUT, TEMPO, ROM, VELOCITY_LOSS, VELOCITY_MEAN, BAR_PATH }

/**
 * Una celda del resumen: la cifra ([prefix] + [value] + [suffix], con [unit] aparte en la misma línea base), su [label] debajo y
 * las [notes] en pequeño. Con [note] en lugar de cifra (p. ej. «Sin dato fiable») no hay cifra. [countTo] es el entero que cuenta al
 * aparecer (y entonces [value] es su texto).
 */
@Immutable
data class SummaryTileUi(
    val id: SummaryTileId,
    val label: String,
    val value: String = "",
    val prefix: String = "",
    val suffix: String = "",
    val unit: String? = null,
    val countTo: Int? = null,
    val note: String? = null,
    val notes: List<String> = emptyList(),
    /** Solo en las repeticiones: con qué confianza se contaron. */
    val confidence: CountConfidence? = null,
) {
    /** La cifra entera, tal como se lee: «≈ 8», «112°», «3-1-1-0». */
    val figure: String get() = prefix + value + suffix
}

@Immutable
data class AnalysisSummaryUi(
    /** Por qué no hay repeticiones, si no las hay: entonces no hay cifras. */
    val noRepsMessage: String?,
    val tiles: List<SummaryTileUi>,
    /** «Bajo el paralelo en ≈ 7 de 8», si el ejercicio mide profundidad. */
    val depthLine: String?,
)

// ─────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────
// Por repetición
// ─────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────

enum class RepMetricKind(val label: String) { VELOCITY("Velocidad"), ROM("ROM"), TIME("Tiempo") }

/**
 * Una medida de la gráfica por repetición. Todas las listas van en el orden de `overlay.reps` (la posición es la de
 * `TechniquePlayerState.selectedRep`). [heights] es la altura relativa a la mejor (0..1), o `null` si esa repetición no tiene dato.
 */
@Immutable
data class RepMetricUi(
    val kind: RepMetricKind,
    val heights: List<Float?>,
    /** La cifra que se pinta sobre la barra elegida («≈ 0,52 m/s», «104°», «3,4 s»). */
    val valueLabels: List<String?>,
    /** Posición de la barra más alta, o −1. */
    val bestIndex: Int,
    /** Resumen para TalkBack: «Velocidad: la más rápida, la 1; la más lenta, la 8, un 18 % menos». */
    val description: String,
    /** Nota bajo la gráfica, si hace falta. */
    val note: String? = null,
)

/** Una repetición de la gráfica: [number] es el que ve el usuario (desde 1) y [detail] la línea de detalle de la elegida. */
@Immutable
data class RepBarUi(val kind: RepKind, val number: Int, val detail: String)

@Immutable
data class RepBreakdownUi(val metrics: List<RepMetricUi>, val reps: List<RepBarUi>) {
    val hasPartial: Boolean get() = reps.any { it.kind == RepKind.PARTIAL }
    val hasFailed: Boolean get() = reps.any { it.kind == RepKind.FAILED }
}

// ─────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────
// Observaciones, disco y consejos
// ─────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────

/**
 * Una observación tal cual la redactó el motor. [seekUs] es el instante al que salta «Ver» y [repPosition] la posición de su
 * repetición en `overlay.reps` (−1 si no se sabe). Sin ninguno de los dos no hay nada que ver.
 */
@Immutable
data class ObservationUi(val text: String, val seekUs: Long?, val repPosition: Int) {
    val canSee: Boolean get() = seekUs != null || repPosition >= 0
}

enum class PlateModuleState { SUGGESTED, CONFIRMED, NOT_FOUND }

/**
 * El módulo «Velocidad real con el disco». [circle] es el disco en píxeles del fotograma de análisis ([frameWidth] × [frameHeight])
 * en el instante [frameUs]: el que halló el detector (SUGGESTED) o el que confirmó el usuario (CONFIRMED); `null` si no hay (NOT_FOUND).
 * [diameterMm] es el diámetro de partida de los chips. [warnings] son los avisos de la escala, cada uno con su causa.
 */
@Immutable
data class PlateModuleUi(
    val state: PlateModuleState,
    val diameterMm: Int,
    val circle: PlateSeed?,
    val frameUs: Long,
    val frameWidth: Int,
    val frameHeight: Int,
    val inMetersPerSecond: Boolean = false,
    val warnings: List<String> = emptyList(),
)

/** Los consejos de cámara, en el orden en que se ofrecen (como mucho 3). [flag] es el aviso que lo provoca. */
enum class CameraTip(val flag: AnalysisFlag?, val text: String) {
    MULTI_PERSON(AnalysisFlag.MULTI_PERSON, "Que solo salgas tú en el encuadre"),
    BACK_VIEW(AnalysisFlag.BACK_VIEW, "Graba de frente o de lado: de espaldas no vemos la cara"),
    OBLIQUE_VIEW(AnalysisFlag.OBLIQUE_VIEW, "Pon el móvil de lado, perpendicular al movimiento"),
    FAR_SIDE_WORKING(AnalysisFlag.FAR_SIDE_WORKING, "Graba el lado que trabaja"),
    SIDE_UNKNOWN(AnalysisFlag.SIDE_UNKNOWN, "En ejercicios alternos, graba de frente"),
    PLATE_ELLIPSE(AnalysisFlag.PLATE_ELLIPSE, "Coloca el móvil a la altura de la barra y de frente al disco"),
    LOW_FPS(AnalysisFlag.LOW_FPS, "Graba a 30 fps o más"),

    /** Sin ningún aviso concreto pero con confianza no ALTA: la guía de siempre del grabador. */
    GENERIC(null, "Graba de lado, con el cuerpo entero a la vista y a 2–4 m"),
}

// ─────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────
// Perfiles (para «¿Qué movimiento es?»)
// ─────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────

@Immutable
data class ProfileOptionUi(val id: AnalysisProfileId, val title: String, val subtitle: String?)

/** El nombre del perfil de análisis en español, como lo reconoce quien entrena. */
internal fun AnalysisProfileId.spanishName(): String = when (this) {
    AnalysisProfileId.SQUAT -> "Sentadilla"
    AnalysisProfileId.LUNGE -> "Zancada"
    AnalysisProfileId.STEP_UP -> "Subida al cajón"
    AnalysisProfileId.MACHINE_SQUAT -> "Sentadilla en máquina"
    AnalysisProfileId.DEADLIFT -> "Peso muerto"
    AnalysisProfileId.HINGE -> "Peso muerto rumano"
    AnalysisProfileId.HIP_THRUST -> "Empuje de cadera"
    AnalysisProfileId.BACK_EXTENSION -> "Extensión de espalda"
    AnalysisProfileId.BENCH -> "Press de banca"
    AnalysisProfileId.DIP -> "Fondos"
    AnalysisProfileId.PUSH_UP -> "Flexiones"
    AnalysisProfileId.OHP -> "Press militar"
    AnalysisProfileId.PULL_VERTICAL -> "Dominadas y jalones"
    AnalysisProfileId.ROW -> "Remo"
    AnalysisProfileId.CURL -> "Curl de bíceps"
    AnalysisProfileId.TRICEPS_EXTENDED_REST -> "Tríceps sobre la cabeza"
    AnalysisProfileId.TRICEPS_FLEXED_REST -> "Tríceps en polea"
    AnalysisProfileId.RAISE -> "Elevaciones"
    AnalysisProfileId.LEG_EXTENSION -> "Extensión de piernas"
    AnalysisProfileId.LEG_CURL -> "Curl femoral"
    AnalysisProfileId.CALF -> "Gemelos de pie"
    AnalysisProfileId.CALF_SEATED -> "Gemelos sentado"
    AnalysisProfileId.SHRUG -> "Encogimientos"
    AnalysisProfileId.FLY -> "Aperturas"
}

/** Una pista de en qué consiste cada movimiento, bajo su nombre. */
internal fun AnalysisProfileId.spanishHint(): String = when (this) {
    AnalysisProfileId.SQUAT -> "Con barra, mancuernas o peso corporal"
    AnalysisProfileId.LUNGE -> "Zancadas y sentadilla búlgara"
    AnalysisProfileId.STEP_UP -> "Con cajón o banco"
    AnalysisProfileId.MACHINE_SQUAT -> "En máquina guiada"
    AnalysisProfileId.DEADLIFT -> "Convencional o sumo"
    AnalysisProfileId.HINGE -> "Rumano y buenos días"
    AnalysisProfileId.HIP_THRUST -> "Con barra o en máquina"
    AnalysisProfileId.BACK_EXTENSION -> "En banco de hiperextensiones"
    AnalysisProfileId.BENCH -> "Con barra o mancuernas"
    AnalysisProfileId.DIP -> "En paralelas o entre bancos"
    AnalysisProfileId.PUSH_UP -> "Con el peso del cuerpo"
    AnalysisProfileId.OHP -> "De pie o sentado"
    AnalysisProfileId.PULL_VERTICAL -> "Dominadas y jalón al pecho"
    AnalysisProfileId.ROW -> "Con barra, mancuerna o polea"
    AnalysisProfileId.CURL -> "Con barra, mancuernas o polea"
    AnalysisProfileId.TRICEPS_EXTENDED_REST -> "Francés y extensión sobre la cabeza"
    AnalysisProfileId.TRICEPS_FLEXED_REST -> "Polea y patada"
    AnalysisProfileId.RAISE -> "Laterales o frontales"
    AnalysisProfileId.LEG_EXTENSION -> "En máquina de cuádriceps"
    AnalysisProfileId.LEG_CURL -> "En máquina de femorales"
    AnalysisProfileId.CALF -> "De pie, en máquina o con peso"
    AnalysisProfileId.CALF_SEATED -> "En máquina, sentado"
    AnalysisProfileId.SHRUG -> "Con barra o mancuernas"
    AnalysisProfileId.FLY -> "Con mancuernas, poleas o máquina"
}

/** Los movimientos en el orden en que se ofrecen: los más frecuentes primero. */
private val PROFILE_ORDER: List<AnalysisProfileId> = listOf(
    AnalysisProfileId.SQUAT, AnalysisProfileId.DEADLIFT, AnalysisProfileId.BENCH, AnalysisProfileId.OHP,
    AnalysisProfileId.ROW, AnalysisProfileId.PULL_VERTICAL, AnalysisProfileId.LUNGE, AnalysisProfileId.HINGE,
    AnalysisProfileId.HIP_THRUST, AnalysisProfileId.CURL, AnalysisProfileId.TRICEPS_EXTENDED_REST,
    AnalysisProfileId.TRICEPS_FLEXED_REST, AnalysisProfileId.RAISE, AnalysisProfileId.PUSH_UP, AnalysisProfileId.DIP,
    AnalysisProfileId.MACHINE_SQUAT, AnalysisProfileId.STEP_UP, AnalysisProfileId.BACK_EXTENSION,
    AnalysisProfileId.LEG_EXTENSION, AnalysisProfileId.LEG_CURL, AnalysisProfileId.CALF, AnalysisProfileId.CALF_SEATED,
    AnalysisProfileId.SHRUG, AnalysisProfileId.FLY,
)

/** Cuántos movimientos enseña la lista plegada, contando la conjetura. */
internal const val COLLAPSED_PROFILE_COUNT = 5

/** Todos los movimientos que se pueden elegir. */
internal val ALL_PROFILE_COUNT: Int get() = PROFILE_ORDER.size

/**
 * Los movimientos de la lista «¿Qué movimiento es?»: la conjetura ([guess]) primero y, después, los demás por frecuencia. Plegada,
 * solo los [COLLAPSED_PROFILE_COUNT] primeros (la conjetura incluida, además de [selected] si no estuviera entre ellos).
 */
internal fun profileOptions(guess: AnalysisProfileId?, selected: AnalysisProfileId?, expanded: Boolean): List<ProfileOptionUi> {
    val ordered = buildList {
        if (guess != null) add(guess)
        for (id in PROFILE_ORDER) if (id != guess) add(id)
    }
    val shown = if (expanded) {
        ordered
    } else {
        val head = ordered.take(COLLAPSED_PROFILE_COUNT)
        if (selected != null && selected !in head) head + selected else head
    }
    return shown.map { ProfileOptionUi(it, it.spanishName(), it.spanishHint()) }
}

// ─────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────
// Formato (español: coma decimal, tipo frase)
// ─────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────

internal object AnalysisFormat {
    private val MONTHS = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")

    /** [x] con [decimals] decimales y coma decimal. */
    fun decimal(x: Double, decimals: Int): String =
        String.format(Locale.ROOT, "%.${decimals}f", x).replace('.', ',')

    /** «2,1 s» (un decimal) a partir de milisegundos. */
    fun seconds(ms: Number): String = "${decimal(ms.toDouble() / 1000.0, 1)} s"

    /** «80 kg», «82,5 kg»: sin «,0» si es redondo. */
    fun kg(x: Double): String {
        val rounded = (x * 10.0).roundToInt() / 10.0
        val text = if (rounded == rounded.toLong().toDouble()) rounded.toLong().toString() else decimal(rounded, 1)
        return "$text kg"
    }

    /** «13 oct»; con el año («13 oct 2025») si no es el actual. */
    fun shortDate(epochMs: Long, nowMs: Long, zone: ZoneId): String {
        val date = Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate()
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        val base = "${date.dayOfMonth} ${MONTHS[date.monthValue - 1]}"
        return if (date.year == today.year) base else "$base ${date.year}"
    }
}

/**
 * La línea de la serie bajo el nombre del ejercicio: «Serie 2 · 80 kg × 8 · 13 oct». Cada trozo solo si hay dato; sin datos de la
 * serie queda la fecha. El índice de la serie del medio empieza en 0.
 */
internal fun setInfoOf(media: WorkoutMedia, nowMs: Long, zone: ZoneId): String {
    val parts = ArrayList<String>(3)
    media.setIndex?.takeIf { it >= 0 }?.let { parts.add("Serie ${it + 1}") }
    val weight = media.weightKg?.takeIf { it.isFinite() && it > 0.0 }
    val reps = media.reps?.takeIf { it > 0 }
    when {
        weight != null && reps != null -> parts.add("${AnalysisFormat.kg(weight)} × $reps")
        weight != null -> parts.add(AnalysisFormat.kg(weight))
        reps != null -> parts.add(if (reps == 1) "1 repetición" else "$reps repeticiones")
    }
    parts.add(AnalysisFormat.shortDate(media.createdAtMs, nowMs, zone))
    return parts.joinToString(" · ")
}

// ─────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────
// Estado según la fila
// ─────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────

/** El texto del motivo de un UNSUPPORTED: el código estable de `UnsupportedReasons` pasado a su frase. */
internal fun unsupportedText(errorCode: String?): String =
    UnsupportedReasons.textOf(errorCode ?: UnsupportedReasons.NOT_YET)

/** La causa de un FAILED en una frase (el código es el que dejó el Worker). */
internal fun failureMessage(errorCode: String?): String = when (errorCode) {
    "video_missing" -> "No encontramos el archivo del vídeo."
    "out_of_memory" -> "El móvil se quedó sin memoria al analizarlo."
    "io_error", "sidecar_write_failed", "store_write_failed" -> "No se pudo guardar el resultado. Comprueba el espacio libre del móvil."
    else -> "Algo falló durante el análisis."
}

/** Por qué un análisis terminado no trae repeticiones (`SetTechniqueReport.noRepsReason`). */
internal fun noRepsMessage(reason: String?): String = when (reason) {
    "no_pose" -> "No vimos a nadie en el vídeo"
    "no_scale" -> "No pudimos calcular la escala del cuerpo"
    "too_short" -> "El vídeo es demasiado corto para contar repeticiones"
    "low_amplitude" -> "El movimiento fue demasiado pequeño para contarlo"
    else -> "No encontramos repeticiones en el vídeo"
}

// ─────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────
// Del informe a los módulos
// ─────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────

/**
 * Los módulos de un análisis terminado. [marks] son las marcas de la capa (`overlay.reps`): las barras y sus posiciones van en ese
 * orden. [usesPlate] dice si el perfil, tal como lo resolvió el análisis, usa el disco; [defaultDiameterMm] es el diámetro de los
 * ajustes; [frameWidth] × [frameHeight] el fotograma de análisis en el que están las coordenadas del disco.
 */
internal fun buildResults(
    report: SetTechniqueReport,
    marks: List<TechniqueRepMark>,
    bar: BarTrack?,
    overrides: CaptureOverrides,
    usesPlate: Boolean,
    defaultDiameterMm: Int,
    frameWidth: Int,
    frameHeight: Int,
): AnalysisResultsUi = AnalysisResultsUi(
    summary = buildSummary(report),
    breakdown = buildBreakdown(report, marks),
    observations = buildObservations(report, marks),
    plate = buildPlate(report, marks, bar, overrides, usesPlate, defaultDiameterMm, frameWidth, frameHeight),
    tips = buildTips(report),
)

private fun CountConfidence.word(): String = when (this) {
    CountConfidence.ALTA -> "Confianza alta"
    CountConfidence.MEDIA -> "Confianza media"
    CountConfidence.BAJA -> "Confianza baja"
}

/** La vista oblicua no da ángulos absolutos fiables: lo que salga de ahí es una estimación. */
private fun SetTechniqueReport.isOblique(): Boolean = view == ViewKind.OBLIQUE || AnalysisFlag.OBLIQUE_VIEW in flags

internal fun buildSummary(report: SetTechniqueReport): AnalysisSummaryUi {
    if (report.reps.isEmpty()) {
        return AnalysisSummaryUi(noRepsMessage = noRepsMessage(report.noRepsReason), tiles = emptyList(), depthLine = null)
    }
    val profile = AnalysisProfiles.of(report.profileId)
    val tiles = ArrayList<SummaryTileUi>(7)

    // Repeticiones. Con la confianza no ALTA el conteo es una estimación: «≈ 8» y «Revisa el conteo».
    val estimated = report.confidence != CountConfidence.ALTA
    // Cada cosa en su línea: la columna es estrecha y una línea partida a media frase se lee mal.
    val repNotes = ArrayList<String>(5)
    if (report.partialReps > 0) repNotes.add(if (report.partialReps == 1) "+1 parcial" else "+${report.partialReps} parciales")
    if (report.failedReps > 0) repNotes.add(if (report.failedReps == 1) "1 fallida" else "${report.failedReps} fallidas")
    val left = report.perSideCounts["left"]
    val right = report.perSideCounts["right"]
    if (left != null && right != null) repNotes.add("Izq. $left · der. $right")
    repNotes.add(report.confidence.word())
    when (report.confidence) {
        CountConfidence.ALTA -> Unit
        CountConfidence.MEDIA -> repNotes.add("Revisa el conteo")
        CountConfidence.BAJA -> repNotes.add("Vídeo no concluyente")
    }
    tiles.add(
        SummaryTileUi(
            id = SummaryTileId.REPS,
            label = "Repeticiones",
            value = report.repCount.toString(),
            prefix = if (estimated) "≈ " else "",
            countTo = report.repCount,
            notes = repNotes,
            confidence = report.confidence,
        ),
    )

    // Tiempo bajo tensión.
    val tut = report.tutMs
    if (MetricKey.TUT in report.available && tut != null && tut >= 0L) {
        val total = (tut / 1000.0).roundToInt()
        if (total < 100) {
            tiles.add(SummaryTileUi(SummaryTileId.TUT, "TUT", value = total.toString(), unit = "s", countTo = total, notes = listOf("Tiempo bajo tensión")))
        } else {
            val clock = "${total / 60}:${(total % 60).toString().padStart(2, '0')}"
            tiles.add(SummaryTileUi(SummaryTileId.TUT, "TUT", value = clock, unit = "min", notes = listOf("Tiempo bajo tensión")))
        }
    }

    // Tempo (excéntrica-pausa-concéntrica-pausa, en segundos). Si ninguna fase se midió no hay tempo.
    val tempo = report.tempo
    if (MetricKey.TEMPO in report.available && tempo != null &&
        (tempo.eccentricMs != null || tempo.pauseAfterEccentricMs != null || tempo.concentricMs != null || tempo.pauseAfterConcentricMs != null)
    ) {
        tiles.add(
            SummaryTileUi(
                id = SummaryTileId.TEMPO,
                label = "Tempo",
                value = tempo.label(),
                unit = "s",
                notes = listOf(tempoNote(report.profileId)),
            ),
        )
    }

    // ROM de la articulación del perfil. En vista oblicua es una estimación.
    val rom = report.romMeanDeg
    if (MetricKey.ROM in report.available && rom != null && rom.isFinite()) {
        val sd = report.romSdDeg?.takeIf { it.isFinite() && it >= 0.0 }
        val sdText = sd?.let { if (it < 1.0) AnalysisFormat.decimal(it, 1) else it.roundToInt().toString() }
        val joint = profile.romAngle?.let { "ROM ${ObservationText.ofJoint(it)}" } ?: "ROM"
        tiles.add(
            SummaryTileUi(
                id = SummaryTileId.ROM,
                label = joint,
                value = rom.roundToInt().toString(),
                prefix = if (report.isOblique()) "≈ " else "",
                suffix = "°",
                countTo = if (report.isOblique()) null else rom.roundToInt(),
                notes = if (sdText != null) listOf("± $sdText° entre repeticiones") else emptyList(),
            ),
        )
    }

    // Pérdida de velocidad. Con las barras relativas pero sin pérdida medible es «sin dato fiable», nunca un 0 %.
    val loss = report.velocityLossPct?.takeIf { it.isFinite() }
    val fullCount = report.reps.count { it.kind == RepKind.FULL }
    if (loss != null) {
        val pct = loss.roundToInt().coerceAtLeast(0)
        tiles.add(
            SummaryTileUi(
                id = SummaryTileId.VELOCITY_LOSS,
                label = "Pérdida de velocidad",
                value = pct.toString(),
                unit = "%",
                countTo = pct,
                notes = listOf("Entre la mejor y la última repetición"),
            ),
        )
    } else if (MetricKey.VELOCITY_RELATIVE in report.available) {
        tiles.add(
            SummaryTileUi(
                id = SummaryTileId.VELOCITY_LOSS,
                label = "Pérdida de velocidad",
                note = "Sin dato fiable",
                notes = listOf(if (fullCount < 3) "Hacen falta 3 repeticiones completas" else "El ruido del vídeo no deja medirla"),
            ),
        )
    }

    // Velocidad media: solo en m/s (disco calibrado) y siempre con «≈».
    if (report.velocityUnit == VelocityUnit.MPS) {
        val values = report.reps.filter { it.kind == RepKind.FULL }.mapNotNull { it.mcv?.takeIf { v -> v.isFinite() && v > 0.0 } }
        if (values.isNotEmpty()) {
            val best = report.mcvBest?.takeIf { it.isFinite() && it > 0.0 }
            tiles.add(
                SummaryTileUi(
                    id = SummaryTileId.VELOCITY_MEAN,
                    label = "Velocidad media",
                    value = AnalysisFormat.decimal(values.average(), 2),
                    prefix = "≈ ",
                    unit = "m/s",
                    notes = if (best != null) listOf("Mejor repetición ≈ ${AnalysisFormat.decimal(best, 2)} m/s") else emptyList(),
                ),
            )
        }
    }

    // Desvío de la barra: en cm con el disco calibrado (PATH_ABSOLUTE); si no, en % del recorrido y con «≈».
    val deviation = report.barDeviation?.takeIf { it.isFinite() }
    if (MetricKey.PATH in report.available && deviation != null) {
        val reference = if (profile.pathRule == PathRule.CONSISTENCY) "Respecto de la trayectoria habitual" else "Respecto del medio pie"
        when {
            report.barDeviationUnit == "cm" && MetricKey.PATH_ABSOLUTE in report.available ->
                tiles.add(
                    SummaryTileUi(
                        id = SummaryTileId.BAR_PATH,
                        label = "Desvío de la barra",
                        value = AnalysisFormat.decimal(abs(deviation), 1),
                        unit = "cm",
                        notes = listOf(reference),
                    ),
                )
            report.barDeviationUnit == "%ROM" ->
                tiles.add(
                    SummaryTileUi(
                        id = SummaryTileId.BAR_PATH,
                        label = "Desvío de la barra",
                        value = abs(deviation).roundToInt().toString(),
                        prefix = "≈ ",
                        unit = "%",
                        notes = listOf("Del recorrido. $reference"),
                    ),
                )
            else -> Unit
        }
    }

    return AnalysisSummaryUi(noRepsMessage = null, tiles = tiles, depthLine = depthLine(report))
}

/** Lo que significan las cuatro cifras del tempo: los movimientos que se hacen hacia arriba y hacia abajo hablan de bajada y subida. */
private fun tempoNote(profile: AnalysisProfileId): String {
    val (first, second) = phaseWords(profile)
    return "${first.replaceFirstChar { it.uppercase() }}, pausa, $second, pausa"
}

/**
 * Cómo se llaman las dos fases. En los movimientos verticales, «bajada» y «subida»; donde la dirección se invierte o no es vertical
 * (jalón, remo, polea de tríceps, aperturas), los nombres de la fase: «excéntrica» y «concéntrica».
 */
internal fun phaseWords(profile: AnalysisProfileId): Pair<String, String> = when (profile) {
    AnalysisProfileId.ROW,
    AnalysisProfileId.FLY,
    AnalysisProfileId.PULL_VERTICAL,
    AnalysisProfileId.TRICEPS_FLEXED_REST,
    -> "excéntrica" to "concéntrica"
    else -> "bajada" to "subida"
}

/** «Bajo el paralelo en ≈ 7 de 8» (y, si las hay, las repeticiones a la par y por encima); solo con la profundidad medida. */
private fun depthLine(report: SetTechniqueReport): String? {
    if (MetricKey.DEPTH !in report.available) return null
    val classes = report.reps.filter { it.kind == RepKind.FULL }.mapNotNull { it.depthClass }
    if (classes.isEmpty()) return null
    val below = classes.count { it == DepthClass.BELOW }
    val parallel = classes.count { it == DepthClass.PARALLEL }
    val above = classes.count { it == DepthClass.ABOVE }
    val parts = ArrayList<String>(3)
    parts.add("Bajo el paralelo en ≈ $below de ${classes.size}")
    if (parallel > 0) parts.add("a la par en $parallel")
    if (above > 0) parts.add("por encima en $above")
    return parts.joinToString(" · ")
}

// ── Por repetición ──

internal fun buildBreakdown(report: SetTechniqueReport, marks: List<TechniqueRepMark>): RepBreakdownUi? {
    if (marks.size < 2) return null
    val reps: List<RepReport?> = marks.map { mark -> report.reps.firstOrNull { it.index == mark.index } }
    val metrics = listOfNotNull(
        velocityMetric(report, reps),
        romMetric(report, reps),
        timeMetric(marks),
    )
    if (metrics.isEmpty()) return null
    val bars = marks.mapIndexed { i, mark -> RepBarUi(mark.kind, i + 1, repDetail(report, i + 1, mark, reps[i])) }
    return RepBreakdownUi(metrics, bars)
}

private fun relativeHeights(values: List<Double?>): Triple<List<Float?>, Int, Int> {
    val present = values.withIndex().filter { it.value != null }
    val maxValue = present.maxOf { it.value!! }
    val heights = values.map { v -> v?.let { (it / maxValue).toFloat().coerceIn(0f, 1f) } }
    val best = present.maxByOrNull { it.value!! }!!.index
    val worst = present.minByOrNull { it.value!! }!!.index
    return Triple(heights, best, worst)
}

private fun velocityMetric(report: SetTechniqueReport, reps: List<RepReport?>): RepMetricUi? {
    val unit = report.velocityUnit ?: return null
    val values: List<Double?> = reps.map { r -> r?.mcv?.takeIf { it.isFinite() && it > 0.0 } }
    if (values.count { it != null } < 2) return null
    val (heights, best, worst) = relativeHeights(values)
    val top = values[best]!!
    // m/s solo con el disco calibrado y con «≈»; si no, cada barra es un % de la mejor.
    val labels = values.mapIndexed { i, v ->
        when {
            v == null -> null
            unit == VelocityUnit.MPS -> "≈ ${AnalysisFormat.decimal(v, 2)} m/s"
            i == best -> "La mejor"
            else -> "${(v / top * 100.0).roundToInt()} % de la mejor"
        }
    }
    val description = if (best == worst) {
        "Velocidad: sin diferencias entre repeticiones"
    } else {
        val less = ((1.0 - values[worst]!! / top) * 100.0).roundToInt()
        "Velocidad: la más rápida, la ${best + 1}; la más lenta, la ${worst + 1}, un $less % menos"
    }
    return RepMetricUi(
        kind = RepMetricKind.VELOCITY,
        heights = heights,
        valueLabels = labels,
        bestIndex = best,
        description = description,
        note = if (unit == VelocityUnit.MPS) null else "Velocidad relativa a la mejor repetición. Sin el disco calibrado no se da en m/s.",
    )
}

private fun romMetric(report: SetTechniqueReport, reps: List<RepReport?>): RepMetricUi? {
    if (MetricKey.ROM !in report.available) return null
    val values: List<Double?> = reps.map { r -> r?.romDeg?.takeIf { it.isFinite() && it > 0.0 } }
    if (values.count { it != null } < 2) return null
    val (heights, best, worst) = relativeHeights(values)
    val approx = if (report.isOblique()) "≈ " else ""
    val labels = values.map { v -> v?.let { "$approx${it.roundToInt()}°" } }
    val description = if (best == worst) {
        "ROM: sin diferencias entre repeticiones"
    } else {
        "ROM: el mayor, la ${best + 1}, con $approx${values[best]!!.roundToInt()}°; el menor, la ${worst + 1}, con $approx${values[worst]!!.roundToInt()}°"
    }
    return RepMetricUi(RepMetricKind.ROM, heights, labels, best, description)
}

private fun timeMetric(marks: List<TechniqueRepMark>): RepMetricUi? {
    val values: List<Double?> = marks.map { m -> ((m.endUs - m.startUs) / 1_000_000.0).takeIf { it > 0.0 } }
    if (values.count { it != null } < 2) return null
    val (heights, best, worst) = relativeHeights(values)
    val labels = values.map { v -> v?.let { "${AnalysisFormat.decimal(it, 1)} s" } }
    val description = if (best == worst) {
        "Tiempo: todas las repeticiones duran lo mismo"
    } else {
        "Tiempo: la más larga, la ${best + 1}, con ${labels[best]}; la más corta, la ${worst + 1}, con ${labels[worst]}"
    }
    return RepMetricUi(RepMetricKind.TIME, heights, labels, best, description)
}

/** «Rep 4 · bajada 2,1 s · subida 0,9 s · ROM 104° · bajo el paralelo»: solo lo que se midió en esa repetición. */
private fun repDetail(report: SetTechniqueReport, number: Int, mark: TechniqueRepMark, rep: RepReport?): String {
    val parts = ArrayList<String>(7)
    parts.add("Rep $number")
    when (mark.kind) {
        RepKind.PARTIAL -> parts.add("parcial")
        RepKind.FAILED -> parts.add("fallida")
        RepKind.FULL -> Unit
    }
    if (rep == null) return parts.joinToString(" · ")
    val (down, up) = phaseWords(report.profileId)
    rep.eccentricMs?.takeIf { it > 0 }?.let { parts.add("$down ${AnalysisFormat.seconds(it)}") }
    rep.concentricMs?.takeIf { it > 0 }?.let { parts.add("$up ${AnalysisFormat.seconds(it)}") }
    rep.romDeg?.takeIf { it.isFinite() && it > 0.0 }?.let { parts.add("ROM ${if (report.isOblique()) "≈ " else ""}${it.roundToInt()}°") }
    if (report.velocityUnit == VelocityUnit.MPS) {
        rep.mcv?.takeIf { it.isFinite() && it > 0.0 }?.let { parts.add("≈ ${AnalysisFormat.decimal(it, 2)} m/s") }
    }
    when (rep.depthClass) {
        DepthClass.BELOW -> parts.add("bajo el paralelo")
        DepthClass.PARALLEL -> parts.add("a la par")
        DepthClass.ABOVE -> parts.add("por encima del paralelo")
        null -> Unit
    }
    return parts.joinToString(" · ")
}

// ── Observaciones ──

/** Las observaciones del informe tal cual (como mucho 3), con la posición de su repetición en la capa. */
internal fun buildObservations(report: SetTechniqueReport, marks: List<TechniqueRepMark>): List<ObservationUi> =
    report.observations.take(3).map { observation ->
        val position = observation.repIndices
            .asSequence()
            .map { index -> marks.indexOfFirst { it.index == index } }
            .firstOrNull { it >= 0 } ?: -1
        ObservationUi(text = observation.text, seekUs = observation.atUs, repPosition = position)
    }

// ── Disco ──

/** Los avisos de la escala del disco, cada uno con su causa (van al módulo del disco). */
internal fun plateWarnings(flags: Set<AnalysisFlag>): List<String> = buildList {
    if (AnalysisFlag.PLATE_ELLIPSE in flags) add("El disco se ve ovalado: la cámara no estaba de frente a la barra.")
    if (AnalysisFlag.PLATE_REJECTED in flags) add("Se descartó un disco que no cuadraba con tu posición.")
    if (AnalysisFlag.PARALLAX_SUPPRESSED in flags) add("No se pudo corregir el paralaje: el desvío respecto del medio pie no se muestra.")
    if (AnalysisFlag.PLATE_SCALE_MISMATCH in flags) add("El tamaño del disco no cuadra con tu cuerpo: revisa su diámetro.")
}

/**
 * El módulo del disco, solo si el perfil lo usa ([usesPlate]) o ya hay algo suyo (traza del disco, escala por disco o un disco
 * del usuario). Tres estados: disco hallado sin confirmar (SUGGESTED), confirmado por el usuario (CONFIRMED) o sin disco (NOT_FOUND).
 */
internal fun buildPlate(
    report: SetTechniqueReport,
    marks: List<TechniqueRepMark>,
    bar: BarTrack?,
    overrides: CaptureOverrides,
    usesPlate: Boolean,
    defaultDiameterMm: Int,
    frameWidth: Int,
    frameHeight: Int,
): PlateModuleUi? {
    val calibrated = report.scale?.source == ScaleSource.PLATE
    if (!usesPlate && bar == null && !calibrated && overrides.plateSeed == null) return null

    val confirmedSeed = overrides.plateSeed?.takeIf { it.userConfirmed } ?: bar?.seed?.takeIf { it.userConfirmed }
    val foundSeed = overrides.plateSeed ?: bar?.seed
    val diameter = overrides.plateDiameterMm ?: bar?.plateDiameterMm ?: report.scale?.plateDiameterMm ?: defaultDiameterMm
    val circle = confirmedSeed ?: foundSeed
    val state = when {
        confirmedSeed != null -> PlateModuleState.CONFIRMED
        foundSeed != null -> PlateModuleState.SUGGESTED
        else -> PlateModuleState.NOT_FOUND
    }
    // El fotograma que se enseña: el del propio disco, o el fondo de la 1.ª repetición, o el principio del vídeo.
    val frameUs = circle?.tUs ?: marks.firstOrNull()?.turnUs ?: 0L
    return PlateModuleUi(
        state = state,
        diameterMm = diameter,
        circle = circle,
        frameUs = frameUs,
        frameWidth = frameWidth,
        frameHeight = frameHeight,
        inMetersPerSecond = report.velocityUnit == VelocityUnit.MPS && bar != null && PlatePublicationPolicy.canUsePlate(bar),
        warnings = plateWarnings(report.flags) + when {
            confirmedSeed == null -> emptyList()
            bar?.trackingContinuity == PlateTrackingContinuity.INTERRUPTED -> listOf(
                "Se perdió el seguimiento continuo del disco. Se han omitido sus medidas; usamos la pose cuando hay señal. Pulsa Cambiar para volver a marcarlo.")
            bar?.trackingContinuity != PlateTrackingContinuity.UNINTERRUPTED -> listOf(
                "No podemos comprobar el seguimiento completo del disco. Pulsa Cambiar para volver a marcarlo y recalcular sus medidas.")
            else -> emptyList()
        },
    )
}

// ── Consejos ──

/**
 * Los consejos de cámara: uno por cada aviso que tiene consejo, en orden de importancia y como mucho 3. Con confianza no ALTA y
 * sin ningún aviso con consejo, el consejo general. Con confianza ALTA y sin avisos, ninguno (el módulo no se pinta).
 */
internal fun buildTips(report: SetTechniqueReport): List<CameraTip> {
    val tips = CameraTip.entries.filter { it.flag != null && it.flag in report.flags }.take(3)
    if (tips.isNotEmpty()) return tips
    return if (report.confidence != CountConfidence.ALTA) listOf(CameraTip.GENERIC) else emptyList()
}

/** Un porcentaje de avance acotado a 0..100. */
internal fun clampPercent(value: Int): Int = max(0, value.coerceAtMost(100))
