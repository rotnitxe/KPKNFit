package com.example.kpkn.screens.workout.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.discomfortKnowledge
import com.example.kpkn.domain.discomfort.DiscomfortInsight
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.workout.FeedbackInsights
import com.example.kpkn.screens.workout.LinkedDiscomfort
import com.example.kpkn.screens.workout.PostExerciseFeedbackDraft
import com.example.kpkn.screens.workout.RestClockMath
import com.example.kpkn.screens.workout.RestClockShape
import com.example.kpkn.screens.workout.displayWorkoutExerciseName
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknChip
import com.example.kpkn.ui.design.KpknIconButton
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknSetFormat
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknType

/*
 * La tarjeta del descanso tras la última serie (LV6, B3, F4): si la persona minimiza el descanso mientras el feedback sigue pendiente,
 * la tarjeta muestra el MISMO feedback en compacto, dentro de su altura fija: arriba el descanso (la forma pequeña, «2:38 de 4:09» y
 * expandir), en medio la técnica, las molestias con sus chips y el aviso del motor (con desplazamiento si no cabe) y abajo «Saltar» y
 * «Guardar». Lo elegido vive en el borrador compartido con la pantalla completa, y «Guardar» llama a la misma función de guardado.
 */
@Composable
internal fun RestFeedbackCard(
    remainingSeconds: Int,
    totalSeconds: Int,
    shape: RestClockShape,
    feedbackExercises: List<Exercise>,
    draft: PostExerciseFeedbackDraft,
    insights: FeedbackInsights,
    explainDiscomfort: suspend (String) -> DiscomfortInsight?,
    linked: List<LinkedDiscomfort>,
    onSave: () -> Unit,
    onDefer: () -> Unit,
    onExpand: (() -> Unit)?,
    modifier: Modifier = Modifier,
    clockActive: Boolean = true,
    nowMs: Long = System.currentTimeMillis(),
    videoSlot: (@Composable () -> Unit)? = null,
) {
    val safeRemaining = remainingSeconds.coerceAtLeast(0)
    val safeTotal = totalSeconds.coerceAtLeast(safeRemaining).coerceAtLeast(1)
    val compact = com.example.kpkn.screens.workout.liveSessionCardHeight() < 440.dp
    var discomfortsOpen by remember { mutableStateOf(false) }
    val focusName = remember(feedbackExercises) {
        if (feedbackExercises.size == 1) displayWorkoutExerciseName(feedbackExercises.first()) else "esta superserie"
    }
    // `restCard` con el panel `feedback`: «1:32 · DESCANSO» con expandir, el feedback y «Ahora no» / «Guardar feedback» en el pie.
    RestRef02Card(
        compact = compact,
        modifier = modifier,
        tag = "rest-feedback-card",
        footer = { RestRef02FeedbackActions(onDefer = onDefer, onSave = onSave, inCard = true) },
    ) {
        RestRef02Kicker("${KpknSetFormat.rest(safeRemaining)} · DESCANSO", compact, onExpand)
        RestRef02FeedbackBody(
            feedbackExercises = feedbackExercises,
            draft = draft,
            linked = linked,
            wide = false,
            compact = compact,
            onOpenDiscomforts = { discomfortsOpen = true },
        )
    }
    if (discomfortsOpen) {
        RestRef02DiscomfortsSheet(
            draft = draft,
            insights = insights,
            explainDiscomfort = explainDiscomfort,
            focusName = focusName,
            nowMs = nowMs,
            animated = clockActive,
            onDismiss = { discomfortsOpen = false },
        )
    }
}

/**
 * «Molestias del ejercicio» (`feedback-discomforts` del prototipo): la hoja con el buscador, las molestias destacadas y el aviso del
 * motor; cada molestia abre su ficha. Lo elegido va al borrador compartido.
 */
@Composable
internal fun RestRef02DiscomfortsSheet(
    draft: PostExerciseFeedbackDraft,
    insights: FeedbackInsights,
    explainDiscomfort: suspend (String) -> DiscomfortInsight?,
    focusName: String,
    nowMs: Long,
    animated: Boolean,
    onDismiss: () -> Unit,
) {
    var openDiscomfortId by remember { mutableStateOf<String?>(null) }
    LiveSheet(title = "Molestias del ejercicio", onDismiss = onDismiss, tag = "rest-discomforts-sheet") {
        DiscomfortPicker(
            draft = draft,
            insights = insights,
            focusName = focusName,
            nowMs = nowMs,
            onOpenDiscomfort = { openDiscomfortId = it },
        )
    }
    openDiscomfortId?.let { id ->
        val knowledge = discomfortKnowledge(id)
        if (knowledge != null) {
            val insight by produceState<DiscomfortInsight?>(initialValue = null, id, explainDiscomfort) {
                value = explainDiscomfort(id)
            }
            DiscomfortSheet(
                knowledge = knowledge,
                selected = draft.isSelected(id),
                insight = insight,
                onToggleReport = {
                    draft.toggle(id)
                    openDiscomfortId = null
                },
                onDismiss = { openDiscomfortId = null },
                animated = animated,
            )
        }
    }
}

/** «Molestias previas relacionadas» en la tarjeta: una fila por molestia con «Sigue» y «Resuelta» (las mismas del panel completo). */
@Composable
internal fun LinkedDiscomfortsCompact(
    linked: List<LinkedDiscomfort>,
    draft: PostExerciseFeedbackDraft,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = "Molestias previas relacionadas",
            style = KpknType.eyebrow.copy(fontSize = 12.sp, letterSpacing = 1.4.sp),
            color = WizardColors.textMuted,
        )
        linked.forEach { item ->
            val still = draft.stillPresent(item.id)
            // El nombre y dónde se reportó en una sola frase a todo el ancho; «Sigue» y «Resuelta» debajo, sin cortar nada.
            Text(
                text = "${item.label} · en ${item.reportedIn}",
                style = KpknType.caption,
                color = if (still) WizardColors.text else WizardColors.textFaint,
                maxLines = 2,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                KpknChip(text = "Sigue", selected = still, onClick = { draft.setStillPresent(item.id, true) })
                KpknChip(text = "Resuelta", selected = !still, onClick = { draft.setStillPresent(item.id, false) })
            }
        }
    }
}
