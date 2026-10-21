package com.example.kpkn.screens.technique.hub.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.models.VideoAnalysisSummary
import com.example.kpkn.data.models.WorkoutMedia
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.workout.components.WorkoutMediaThumb
import com.example.kpkn.screens.workout.design.VideoThumbBadge
import com.example.kpkn.screens.workout.design.approxCount
import com.example.kpkn.screens.workout.design.videoThumbBadgeFor
import com.example.kpkn.screens.workout.design.videoThumbContentDescription
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknModuleHeader
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** El análisis aporta el estado; el medio, la miniatura local y su duración. */
data class TechniqueRecentVideo(val analysis: VideoAnalysisSummary, val media: WorkoutMedia? = null)

internal fun techniqueDate(recordedAtMs: Long): String =
    SimpleDateFormat("d\u00A0MMM", Locale.forLanguageTag("es")).format(Date(recordedAtMs))

/** Comparte con el Modo visual la decisión de mostrar cifra, estimación o resultado no concluyente. */
internal fun techniqueConclusion(analysis: VideoAnalysisSummary, compact: Boolean = false): String {
    val badge = videoThumbBadgeFor(analysis.status.name, analysis.progressPct, analysis.repCount, analysis.confidence?.name)
    if (badge !is VideoThumbBadge.Reps) return "vídeo no concluyente"
    val count = if (badge.approximate) approxCount(badge.count) else "${badge.count}"
    if (compact) return "$count ${if (badge.count == 1) "rep" else "reps"}"
    return "$count ${if (badge.count == 1) "repetición" else "repeticiones"} · confianza ${if (badge.approximate) "media" else "alta"}"
}

@Composable
internal fun TechniqueRecentStrip(
    videos: List<TechniqueRecentVideo>,
    size: Dp,
    onOpenAnalysis: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    Row(
        modifier.fillMaxWidth().testTag("technique-recent-strip")
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                val fade = 12.dp.toPx().coerceAtMost(this.size.width / 2)
                if (scroll.canScrollBackward) drawRect(
                    Brush.horizontalGradient(listOf(Color.Transparent, Color.Black), 0f, fade),
                    size = androidx.compose.ui.geometry.Size(fade, this.size.height), blendMode = BlendMode.DstIn,
                )
                if (scroll.canScrollForward) drawRect(
                    Brush.horizontalGradient(listOf(Color.Black, Color.Transparent), this.size.width - fade, this.size.width),
                    topLeft = Offset(this.size.width - fade, 0f),
                    size = androidx.compose.ui.geometry.Size(fade, this.size.height), blendMode = BlendMode.DstIn,
                )
            }.horizontalScroll(scroll),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        videos.forEach { video -> TechniqueRecentThumb(video, size, onOpenAnalysis) }
    }
}

@Composable
private fun TechniqueRecentThumb(video: TechniqueRecentVideo, size: Dp, onOpenAnalysis: (String) -> Unit) {
    val analysis = video.analysis
    val badge = videoThumbBadgeFor(analysis.status.name, analysis.progressPct, analysis.repCount, analysis.confidence?.name)
    val name = analysis.exerciseName?.takeIf(String::isNotBlank) ?: "Ejercicio sin nombre"
    val state = when (analysis.status) {
        com.example.kpkn.data.models.VideoAnalysisStatus.NEEDS_PROFILE -> "Vídeo, elige el ejercicio para analizarlo"
        com.example.kpkn.data.models.VideoAnalysisStatus.UNSUPPORTED -> "Vídeo, análisis no disponible para este ejercicio"
        else -> videoThumbContentDescription(badge, video.media?.durationMs)
    }
    // El margen reserva también el desborde de la insignia: no lo recorta el scroll ni la tarjeta.
    Box(
        Modifier.size(size + 8.dp).testTag("technique-recent-${analysis.mediaId}")
            .clickable(role = Role.Button, onClickLabel = "Abrir análisis", onClick = { onOpenAnalysis(analysis.mediaId) })
            .semantics { contentDescription = "$name, ${techniqueDate(analysis.recordedAtMs)}, $state" },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(size).clearAndSetSemantics {}) {
            Box(Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)).background(KpknSurface.inset)) {
                if (video.media != null) {
                    WorkoutMediaThumb(video.media, Modifier.fillMaxSize(), visualModeOverlay = true, hasAnalysisBadge = badge != null)
                } else {
                    Icon(KpknLineIcons.Play, null, tint = WizardColors.textMuted, modifier = Modifier.align(Alignment.Center).size(20.dp))
                }
            }
            badge?.let {
                TechniqueRecentBadge(it, Modifier.align(Alignment.BottomEnd).offset(4.dp, 4.dp).testTag("technique-badge-${analysis.mediaId}"))
            }
        }
    }
}

/** Mismo overlay estático de UI-1a; el original es privado del reverso de la serie. */
@Composable
private fun TechniqueRecentBadge(badge: VideoThumbBadge, modifier: Modifier) {
    Box(
        modifier.height(16.dp).widthIn(min = 16.dp)
            .background(Color.Black.copy(alpha = 0.72f), CircleShape)
            .border(1.dp, WizardColors.text.copy(alpha = 0.28f), CircleShape).padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (badge is VideoThumbBadge.Analyzing) {
            Canvas(Modifier.size(12.dp)) {
                val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                drawCircle(WizardColors.text.copy(alpha = 0.20f), style = stroke)
                drawArc(WizardColors.text, -90f, 360f * badge.progress / 100f, false, style = stroke)
            }
        } else {
            Text(
                when (badge) {
                    is VideoThumbBadge.Reps -> if (badge.approximate) approxCount(badge.count) else "${badge.count}"
                    VideoThumbBadge.Inconclusive -> "?"
                    VideoThumbBadge.Failed -> "!"
                    else -> ""
                },
                style = KpknType.chip.copy(fontSize = 10.sp, lineHeight = 10.sp),
                color = when (badge) {
                    VideoThumbBadge.Failed -> WizardColors.danger
                    VideoThumbBadge.Inconclusive -> WizardColors.textMuted
                    else -> WizardColors.text
                },
                maxLines = 1,
            )
        }
    }
}

@Composable
internal fun TechniqueHubSummary(
    analyzedVideoCount: Int,
    exerciseCount: Int,
    videos: List<TechniqueRecentVideo>,
    onOpenAnalysis: (String) -> Unit,
) {
    KpknModule(Modifier.fillMaxWidth().testTag("technique-hub-summary")) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
                Text("$analyzedVideoCount", style = KpknType.metricM, color = WizardColors.text)
                Text(if (analyzedVideoCount == 1) "vídeo analizado" else "vídeos analizados", style = KpknType.caption, color = WizardColors.textMuted)
            }
            Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
                Text("$exerciseCount", style = KpknType.metricM, color = WizardColors.text)
                Text(if (exerciseCount == 1) "ejercicio" else "ejercicios", style = KpknType.caption, color = WizardColors.textMuted)
            }
        }
        if (videos.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            KpknModuleHeader(label = "Vídeos recientes")
            TechniqueRecentStrip(videos.take(10), 64.dp, onOpenAnalysis)
        }
    }
}
