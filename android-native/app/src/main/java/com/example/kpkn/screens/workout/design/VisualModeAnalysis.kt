package com.example.kpkn.screens.workout.design

import com.example.kpkn.data.db.VideoAnalysisSessionRow
import com.example.kpkn.data.models.VideoAnalysisStatus
import com.example.kpkn.domain.biomechanics.metrics.CountConfidence
import com.example.kpkn.screens.workout.SetVideoAnalysisState

internal sealed interface VideoThumbBadge {
    data class Analyzing(val progress: Int) : VideoThumbBadge
    data class Reps(val count: Int, val approximate: Boolean) : VideoThumbBadge
    data object Inconclusive : VideoThumbBadge
    data object Failed : VideoThumbBadge
}

/** Solo los conteos válidos con confianza suficiente muestran una cifra. */
internal fun videoThumbBadgeFor(
    status: String,
    progressPct: Int,
    repCount: Int?,
    confidence: String?,
): VideoThumbBadge? = when (status) {
    VideoAnalysisStatus.QUEUED.name, VideoAnalysisStatus.RUNNING.name ->
        VideoThumbBadge.Analyzing(progressPct.coerceIn(0, 100))
    VideoAnalysisStatus.DONE.name -> {
        val conf = confidence?.let { name -> CountConfidence.entries.firstOrNull { it.name == name } }
        val count = repCount?.takeIf { it >= 0 }
        when {
            conf == CountConfidence.ALTA && count != null -> VideoThumbBadge.Reps(count, approximate = false)
            conf == CountConfidence.MEDIA && count != null -> VideoThumbBadge.Reps(count, approximate = true)
            else -> VideoThumbBadge.Inconclusive
        }
    }
    VideoAnalysisStatus.FAILED.name -> VideoThumbBadge.Failed
    VideoAnalysisStatus.UNSUPPORTED.name -> null
    else -> null
}

internal fun videoThumbBadgeFor(row: VideoAnalysisSessionRow): VideoThumbBadge? =
    videoThumbBadgeFor(row.status, row.progressPct, row.repCount, row.confidence)

/** La descripción sustituye las etiquetas del vídeo y de su insignia. */
internal fun videoThumbContentDescription(badge: VideoThumbBadge?): String = when (badge) {
    null -> "Vídeo"
    is VideoThumbBadge.Analyzing -> "Vídeo, analizando ${badge.progress.coerceIn(0, 100)}\u00A0%"
    is VideoThumbBadge.Reps -> if (badge.approximate) {
        "Vídeo, unas ${badge.count} repeticiones"
    } else {
        "Vídeo, ${badge.count} repeticiones"
    }
    VideoThumbBadge.Inconclusive -> "Vídeo no concluyente"
    VideoThumbBadge.Failed -> "Vídeo, no se pudo analizar"
}

internal enum class VisualModeAnalysisAction { OPEN, RETRY, NONE }

internal data class VisualModeAnalysisLine(
    val text: String,
    val actionLabel: String? = null,
    val action: VisualModeAnalysisAction = VisualModeAnalysisAction.NONE,
)

/** Se abrevia solo la descripción; el sufijo de la acción nunca se entrega a la elipsis de Text. */
internal fun fitVisualModeAnalysisText(line: VisualModeAnalysisLine, fits: (String) -> Boolean): String {
    if (fits(line.text)) return line.text
    val suffix = line.actionLabel?.takeIf { line.action != VisualModeAnalysisAction.NONE }
        ?.let { " · $it" }.orEmpty()
    val description = line.text.removeSuffix(suffix)
    fun candidate(codePoints: Int): String =
        description.substring(0, description.offsetByCodePoints(0, codePoints))
            .trimEnd(' ', '·', ';', ',', '…') + "…" + suffix
    var low = 0
    var high = description.codePointCount(0, description.length)
    while (low < high) {
        val middle = (low + high + 1) / 2
        if (fits(candidate(middle))) low = middle else high = middle - 1
    }
    return candidate(low)
}

/** Una frase y, cuando procede, una acción explícita dentro de ella. */
internal fun visualModeAnalysisLine(state: SetVideoAnalysisState, setIndex: Int): VisualModeAnalysisLine {
    fun extras(partials: Int, failed: Int): String = buildString {
        if (partials > 0) append(" · +$partials ${if (partials == 1) "parcial" else "parciales"}")
        if (failed > 0) append(" · $failed al fallo")
    }

    return when (state) {
        is SetVideoAnalysisState.Analyzing -> VisualModeAnalysisLine(
            text = "Analizando el vídeo de la serie ${setIndex + 1}… ${state.progress.coerceIn(0, 100)}\u00A0%",
            actionLabel = null,
            action = VisualModeAnalysisAction.NONE,
        )
        is SetVideoAnalysisState.Suggest -> when (state.confidence) {
            CountConfidence.ALTA -> {
                val base = if (state.reps == 0 && state.partials == 0 && state.failed > 0) {
                    "El vídeo muestra ${state.failed} ${if (state.failed == 1) "intento" else "intentos"} al fallo"
                } else {
                    "El vídeo cuenta ${state.reps}" + extras(state.partials, state.failed)
                }
                VisualModeAnalysisLine(
                    text = "$base · ver análisis",
                    actionLabel = "ver análisis",
                    action = VisualModeAnalysisAction.OPEN,
                )
            }
            CountConfidence.MEDIA -> VisualModeAnalysisLine(
                text = "El vídeo cuenta ≈${state.reps}" + extras(state.partials, state.failed) + " · revisar",
                actionLabel = "revisar",
                action = VisualModeAnalysisAction.OPEN,
            )
            CountConfidence.BAJA -> VisualModeAnalysisLine(
                text = "Vídeo no concluyente · ver por qué",
                actionLabel = "ver por qué",
                action = VisualModeAnalysisAction.OPEN,
            )
        }
        is SetVideoAnalysisState.Mismatch -> when (state.confidence) {
            CountConfidence.ALTA -> {
                val base = if (state.countsDiffer) {
                    "Registraste ${state.logged}${if (state.loggedPartials > 0) " + ${state.loggedPartials} ${if (state.loggedPartials == 1) "parcial" else "parciales"}" else ""}; el vídeo cuenta ${state.counted}" + extras(state.partials, state.failed)
                } else {
                    "El vídeo muestra ${state.failed} ${if (state.failed == 1) "intento" else "intentos"} al fallo"
                }
                VisualModeAnalysisLine(
                    text = "$base · ver análisis",
                    actionLabel = "ver análisis",
                    action = VisualModeAnalysisAction.OPEN,
                )
            }
            CountConfidence.MEDIA -> VisualModeAnalysisLine(
                text = "El vídeo cuenta ≈${state.counted}" + extras(state.partials, state.failed) + " · revisar",
                actionLabel = "revisar",
                action = VisualModeAnalysisAction.OPEN,
            )
            CountConfidence.BAJA -> VisualModeAnalysisLine(
                text = "Vídeo no concluyente · ver por qué",
                actionLabel = "ver por qué",
                action = VisualModeAnalysisAction.OPEN,
            )
        }
        is SetVideoAnalysisState.Agrees -> VisualModeAnalysisLine(
            text = "El vídeo confirma ${state.counted} · ver análisis",
            actionLabel = "ver análisis",
            action = VisualModeAnalysisAction.OPEN,
        )
        SetVideoAnalysisState.Inconclusive -> VisualModeAnalysisLine(
            text = "Vídeo no concluyente · ver por qué",
            actionLabel = "ver por qué",
            action = VisualModeAnalysisAction.OPEN,
        )
        is SetVideoAnalysisState.Failed -> VisualModeAnalysisLine(
            text = "No se pudo analizar el vídeo · reintentar",
            actionLabel = "reintentar",
            action = VisualModeAnalysisAction.RETRY,
        )
        is SetVideoAnalysisState.Unsupported -> VisualModeAnalysisLine(
            text = state.reasonText,
            actionLabel = null,
            action = VisualModeAnalysisAction.NONE,
        )
    }
}
