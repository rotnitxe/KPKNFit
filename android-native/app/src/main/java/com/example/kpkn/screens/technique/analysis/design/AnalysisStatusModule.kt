package com.example.kpkn.screens.technique.analysis.design

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfileId
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.screens.technique.analysis.AnalysisStatusUi
import com.example.kpkn.screens.technique.analysis.ALL_PROFILE_COUNT
import com.example.kpkn.screens.technique.analysis.profileOptions
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknMetric
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknModuleHeader
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknProgressBar
import com.example.kpkn.ui.design.KpknRadioRow
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknType

/** Etiqueta de prueba del módulo de estado y de sus botones. */
const val ANALYSIS_STATUS_TAG = "analysis-status"
const val ANALYSIS_PROFILE_ANALYZE_TAG = "analysis-profile-analyze"
const val ANALYSIS_RETRY_TAG = "analysis-retry"
const val ANALYSIS_ANALYZE_TAG = "analysis-analyze"

/**
 * El módulo de estado del análisis (sin estado propio): lo que hay en lugar de los resultados mientras no los hay.
 * - **Analizando** (QUEUED y RUNNING): «Analizando el vídeo…» con la barra y el % (de 0 a 100, sin volver atrás); si es un recálculo
 *   del motor, «Actualizando el análisis…».
 * - **Sin soporte** (UNSUPPORTED): el texto del motivo.
 * - **¿Qué movimiento es?** (NEEDS_PROFILE): la lista de movimientos con la conjetura preseleccionada y «Analizar». Plegada enseña
 *   unos pocos; «Ver todos» la despliega.
 * - **Falló** (FAILED): «No pudimos analizar este vídeo» y «Reintentar».
 * - **Sin analizar** y **sin informe**: «Analizar» y «Reanalizar».
 *
 * Con [AnalysisStatusUi.Done] no pinta nada.
 */
@Composable
fun AnalysisStatusModule(
    status: AnalysisStatusUi,
    modifier: Modifier = Modifier,
    selectedProfile: AnalysisProfileId? = null,
    profilesExpanded: Boolean = false,
    onSelectProfile: (AnalysisProfileId) -> Unit = {},
    onToggleProfiles: () -> Unit = {},
    onConfirmProfile: () -> Unit = {},
    onAnalyze: () -> Unit = {},
    onRetry: () -> Unit = {},
) {
    when (status) {
        AnalysisStatusUi.Done -> Unit
        is AnalysisStatusUi.Analyzing -> AnalyzingBody(status, modifier)
        is AnalysisStatusUi.Unsupported -> MessageBody(
            icon = KpknLineIcons.Info,
            title = status.reason,
            caption = "El vídeo se guarda igual, pero su técnica no se mide.",
            modifier = modifier,
        )
        is AnalysisStatusUi.NeedsProfile -> ProfileBody(
            status = status,
            selected = selectedProfile,
            expanded = profilesExpanded,
            onSelect = onSelectProfile,
            onToggle = onToggleProfiles,
            onConfirm = onConfirmProfile,
            modifier = modifier,
        )
        is AnalysisStatusUi.Failed -> MessageBody(
            icon = KpknLineIcons.Info,
            title = "No pudimos analizar este vídeo",
            caption = status.message,
            modifier = modifier,
            actionText = "Reintentar",
            actionTag = ANALYSIS_RETRY_TAG,
            onAction = onRetry,
        )
        AnalysisStatusUi.NotAnalyzed -> MessageBody(
            icon = KpknLineIcons.Play,
            title = "Este vídeo aún no está analizado",
            caption = "Contamos las repeticiones y medimos la técnica en unos segundos.",
            modifier = modifier,
            actionText = "Analizar",
            actionTag = ANALYSIS_ANALYZE_TAG,
            onAction = onAnalyze,
        )
        AnalysisStatusUi.ReportMissing -> MessageBody(
            icon = KpknLineIcons.Info,
            title = "No encontramos el informe de este análisis",
            caption = "Se puede volver a calcular desde el vídeo.",
            modifier = modifier,
            actionText = "Reanalizar",
            actionTag = ANALYSIS_RETRY_TAG,
            onAction = onRetry,
        )
    }
}

// ── Analizando ──

@Composable
private fun AnalyzingBody(status: AnalysisStatusUi.Analyzing, modifier: Modifier) {
    val reduced = wizardReducedMotion()
    val pct = status.progressPct.coerceIn(0, 100)
    val shown by animateIntAsState(
        targetValue = pct,
        animationSpec = if (reduced) snap() else tween(durationMillis = 600),
        label = "analysisProgressNumber",
    )
    // Prototipo 02: «Analizando…» con «Analizando repeticiones y amplitud.»; el recálculo y la cola dicen lo suyo.
    val title = if (status.updating) "Actualizando el análisis…" else "Analizando…"
    val showNumber = !status.queued || pct > 0
    val caption = when {
        status.queued && pct == 0 -> "En cola: empieza en cuanto el móvil esté libre."
        status.updating -> "Se recalcula con los datos que ya tenemos."
        else -> "Analizando repeticiones y amplitud."
    }
    KpknModule(modifier = modifier.testTag(ANALYSIS_STATUS_TAG)) {
        KpknModuleHeader("Análisis")
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = title,
                style = KpknType.bodyStrong,
                color = WizardColors.text,
                modifier = Modifier.weight(1f).padding(end = 12.dp),
            )
            if (showNumber) KpknMetric(value = shown.toString(), unit = "%", style = KpknType.metricM)
        }
        Spacer(Modifier.height(14.dp))
        KpknProgressBar(progress = pct / 100f)
        Spacer(Modifier.height(12.dp))
        Text(text = caption, style = KpknType.caption, color = WizardColors.textMuted)
    }
}

// ── Mensaje con una acción ──

@Composable
private fun MessageBody(
    icon: ImageVector,
    title: String,
    caption: String,
    modifier: Modifier,
    actionText: String? = null,
    actionTag: String? = null,
    onAction: () -> Unit = {},
) {
    KpknModule(modifier = modifier.testTag(ANALYSIS_STATUS_TAG)) {
        Row(verticalAlignment = Alignment.Top) {
            AnalysisIconCircle(icon)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f).padding(top = 2.dp)) {
                Text(text = title, style = KpknType.bodyStrong, color = WizardColors.text)
                Spacer(Modifier.height(4.dp))
                Text(text = caption, style = KpknType.caption, color = WizardColors.textMuted)
            }
        }
        if (actionText != null) {
            Spacer(Modifier.height(18.dp))
            KpknPrimaryPill(text = actionText, onClick = onAction, modifier = Modifier.fillMaxWidth(), tag = actionTag)
        }
    }
}

// ── ¿Qué movimiento es? ──

@Composable
private fun ProfileBody(
    status: AnalysisStatusUi.NeedsProfile,
    selected: AnalysisProfileId?,
    expanded: Boolean,
    onSelect: (AnalysisProfileId) -> Unit,
    onToggle: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier,
) {
    val options = profileOptions(status.guess, selected, expanded)
    KpknModule(modifier = modifier.testTag(ANALYSIS_STATUS_TAG)) {
        KpknModuleHeader("Elige el movimiento")
        Text(text = "¿Qué movimiento es?", style = KpknType.dialogTitle, color = WizardColors.text)
        Spacer(Modifier.height(6.dp))
        Text(
            text = "No reconocemos este ejercicio. Elige el que más se parezca y medimos su técnica.",
            style = KpknType.caption,
            color = WizardColors.textMuted,
        )
        Spacer(Modifier.height(8.dp))
        options.forEachIndexed { index, option ->
            if (index > 0) AnalysisDivider()
            KpknRadioRow(
                selected = option.id == selected,
                title = option.title,
                subtitle = option.subtitle,
                onClick = { onSelect(option.id) },
            )
        }
        AnalysisDivider()
        KpknTextAction(
            text = if (expanded) "Ver menos" else "Ver los $ALL_PROFILE_COUNT movimientos",
            onClick = onToggle,
            modifier = Modifier.padding(vertical = 2.dp),
        )
        Spacer(Modifier.height(8.dp))
        KpknPrimaryPill(
            text = "Analizar",
            onClick = onConfirm,
            modifier = Modifier.fillMaxWidth(),
            tag = ANALYSIS_PROFILE_ANALYZE_TAG,
            enabled = selected != null,
        )
    }
}

// ── Piezas compartidas por los módulos de la pantalla ──

/** Círculo `inset` de [size] con un icono de línea KPKN en `textMuted`: el sello de un mensaje o de un consejo. */
@Composable
internal fun AnalysisIconCircle(icon: ImageVector, size: Dp = 44.dp, iconSize: Dp = 22.dp, contentDescription: String? = null) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(KpknSurface.inset)
            .then(if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(iconSize), tint = WizardColors.textMuted)
    }
}

/** Separador de 1 dp dentro de un módulo (`KpknSurface.divider`). */
@Composable
internal fun AnalysisDivider(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth().height(1.dp).background(KpknSurface.divider))
}
