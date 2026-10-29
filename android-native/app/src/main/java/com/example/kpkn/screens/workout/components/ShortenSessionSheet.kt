package com.example.kpkn.screens.workout.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.domain.sessionassistant.ShortenGoal
import com.example.kpkn.domain.sessionassistant.ShortenItemChange
import com.example.kpkn.domain.sessionassistant.UltraFastPreview
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.ui.components.icons.KpknLineIcons

/** Etiqueta de una píldora de objetivo: «−10 min» o «Lo mínimo eficaz». */
internal fun shortenGoalLabel(goal: ShortenGoal): String = goal.minutes?.let { "−$it min" } ?: "Lo mínimo eficaz"

private fun roundMinutes(seconds: Int): Int = (seconds.coerceAtLeast(0) + 30) / 60

/**
 * «Acortar sesión»: el ahorro en cifras grandes, el objetivo en píldoras y una línea por cambio con su interruptor para excluirla.
 * Sin estado: la propuesta la calcula el ViewModel ([UltraFastPreview]) y cada toque se lo devuelve.
 */
@Composable
internal fun ShortenSessionSheet(
    preview: UltraFastPreview?,
    applyEnabled: Boolean,
    onGoal: (ShortenGoal) -> Unit,
    onToggleItem: (String) -> Unit,
    onApply: () -> Unit,
    onQuickRecord: () -> Unit,
    onDismiss: () -> Unit,
) {
    val canApply = applyEnabled && preview != null && preview.savedSeconds >= 30 && preview.items.any { !it.excluded }
    LiveSheet(title = "Acortar sesión", onDismiss = onDismiss, tag = "live-shorten-sheet", footer = { close ->
        LiveSheetPrimary(
            text = preview?.takeIf { it.savedSeconds >= 30 }?.let { "Aplicar · −${roundMinutes(it.savedSeconds)} min" } ?: "Aplicar",
            onClick = onApply,
            enabled = canApply,
            tag = "live-shorten-apply",
        )
        LiveSheetTextAction("Cancelar", close)
    }) { _ ->
        LiveSheetSubtitle("Elige cuánto quieres ahorrar: se ajusta solo lo que falta y lo registrado se conserva.")
        if (preview == null) {
            LiveSheetShortCopy("Calculando la propuesta…")
        } else {
            ShortenSessionFigures(preview)
            if (preview.goalOptions.isNotEmpty()) {
                LiveSheetChoiceRow(
                    options = preview.goalOptions.map { it to shortenGoalLabel(it) },
                    selected = { it == preview.goal },
                    onSelect = onGoal,
                    tagPrefix = "live-shorten-goal",
                )
            }
            if (!preview.targetReached) {
                LiveSheetInlineNote("Con lo que falta, lo máximo razonable es −${roundMinutes(preview.maxSavedSeconds)} min.")
            }
            if (preview.items.isEmpty()) {
                LiveSheetInlineNote("No queda trabajo pendiente que se pueda acortar sin perder lo esencial.")
            } else {
                LiveSheetSectionLabel("Cambios")
                preview.items.forEach { item -> ShortenItemRow(item, onToggle = { onToggleItem(item.itemId) }) }
            }
        }
        LiveSheetMenuRow("Registro rápido de una serie", onQuickRecord, Modifier.padding(top = 12.dp), icon = KpknLineIcons.Check)
    }
}

@Composable
private fun ShortenSessionFigures(preview: UltraFastPreview) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp).testTag("live-shorten-figures"),
        horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.Bottom) {
        listOf("Te quedan" to preview.beforeSeconds, "Con el cambio" to preview.afterSeconds).forEach { (label, seconds) ->
            Column(Modifier.weight(1f)) {
                Text(label.uppercase(), style = TextStyle(fontFamily = WizardFonts.body, fontSize = 10.sp, lineHeight = 13.sp,
                    letterSpacing = .6.sp), color = LiveSheetTokens.Faint)
                Text("${roundMinutes(seconds)} min", Modifier.padding(top = 6.dp),
                    style = TextStyle(fontFamily = WizardFonts.display, fontWeight = FontWeight.Bold, fontSize = 27.sp, lineHeight = 31.sp,
                        letterSpacing = (-0.4).sp), color = WizardColors.text)
            }
        }
    }
}

/** Línea de un cambio: nombre, el cambio en claro y lo que aporta; el interruptor la excluye sin tener que configurar nada. */
@Composable
private fun ShortenItemRow(item: ShortenItemChange, onToggle: () -> Unit) {
    val on = !item.excluded
    Row(Modifier.fillMaxWidth().heightIn(min = 57.dp).liveSheetTopLine()
        .clickable(role = Role.Switch, onClick = onToggle)
        .semantics { toggleableState = ToggleableState(on); stateDescription = if (on) "Incluido" else "Excluido" }
        .testTag("live-shorten-item-${item.itemId}")
        .padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(item.title, style = TextStyle(fontFamily = WizardFonts.body, fontSize = 14.sp, lineHeight = 19.sp,
                textDecoration = if (on) null else TextDecoration.LineThrough),
                color = if (on) WizardColors.text else LiveSheetTokens.Faint)
            Text(if (on) item.detail else "Sin cambios", Modifier.padding(top = 2.dp),
                style = TextStyle(fontFamily = WizardFonts.body, fontSize = 13.sp, lineHeight = 18.sp),
                color = if (on && item.removesExercise) LiveSheetTokens.Danger else LiveSheetTokens.Muted)
        }
        if (on && item.savedSeconds >= 30) {
            Text("−${roundMinutes(item.savedSeconds)} min", style = TextStyle(fontFamily = WizardFonts.display, fontWeight = FontWeight.Bold,
                fontSize = 14.sp, lineHeight = 18.sp), color = WizardColors.text)
        }
        Box(Modifier.size(width = 38.dp, height = 23.dp).clip(RoundedCornerShape(13.dp))
            .background(if (on) Color(0x38F2EEE6) else Color(0x18FFFFFF)).padding(3.dp),
            contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart) {
            Box(Modifier.size(17.dp).clip(CircleShape).background(WizardColors.text))
        }
    }
}
