package com.example.kpkn.screens.nutrition.customfood

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.nutrition.label.*
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.*

internal fun labelBasisTitle(basis: LabelBasis): String = when (basis) {
    LabelBasis.PER_100G -> "Por 100 g"; LabelBasis.PER_100ML -> "Por 100 ml"; LabelBasis.PER_SERVING -> "Por porción"; LabelBasis.UNKNOWN -> "Base no leída"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LabelReadPanel(state: LabelFormState, onRead: () -> Unit, onCancel: () -> Unit, onUndo: () -> Unit, onBasis: (LabelBasis) -> Unit) {
    KpknModule(Modifier.fillMaxWidth().testTag("label-review")) {
        KpknModuleHeader("Leer etiqueta")
        Text("Experimental · Elige una foto de tu galería. La lectura se hace en el dispositivo; revisa cada cifra antes de guardar.",
            style = KpknType.caption, color = WizardColors.textMuted)
        if (state.reading) {
            Text("Leyendo la etiqueta…", style = KpknType.bodyStrong, color = WizardColors.text)
            KpknTextAction("Cancelar lectura", onCancel)
        } else KpknTextAction("Leer etiqueta", onRead, Modifier.testTag("label-pick-image"))
        state.label?.let { label ->
            Text("Base de los valores: ${labelBasisTitle(label.basis)}" + if(label.basis == LabelBasis.PER_SERVING) " de ${state.form.servingAmount} ${state.form.servingUnit}" else "", style = KpknType.bodyStrong, color = WizardColors.text)
            label.servingSize?.let { Text("Porción leída: ${LabelFormAdapter.number(it)} ${label.servingUnit.orEmpty()}", style = KpknType.caption, color = WizardColors.textMuted) }
            if (label.basis == LabelBasis.PER_100ML) Text("El formulario expresa esos valores como una porción de referencia de 100 ml.", style = KpknType.caption, color = WizardColors.textMuted)
            if (state.needsBasisConfirmation) Text("Confirma la base antes de guardar; no se ha supuesto una.", style = KpknType.body, color = WizardColors.danger)
            val available = if (state.needsBasisConfirmation) listOf(LabelBasis.PER_100G, LabelBasis.PER_100ML, LabelBasis.PER_SERVING)
                else (label.columns.keys.filter { it != LabelBasis.UNKNOWN } + label.basis +
                    if (label.servingSize != null || CustomFoodField.SERVING in state.manualFields) listOf(LabelBasis.PER_SERVING, if (state.form.servingUnit == "ml") LabelBasis.PER_100ML else LabelBasis.PER_100G) else emptyList()).distinct()
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                available.forEach { basis -> KpknChip(labelBasisTitle(basis), !state.needsBasisConfirmation && label.basis == basis, { onBasis(basis) }) }
            }
            label.warnings.forEach { Text(it, style = KpknType.caption, color = WizardColors.textMuted) }
            if (state.form.servingAmount.isBlank()) Text("La porción no se leyó. Indica su cantidad para poder guardar.", style = KpknType.caption, color = WizardColors.textMuted)
        }
        state.error?.let { Text(it, style = KpknType.body, color = WizardColors.danger, modifier = Modifier.testTag("label-error")) }
        if (state.canUndo && !state.reading) KpknTextAction("Deshacer lectura", onUndo, Modifier.testTag("label-undo"))
    }
}

@Composable
internal fun LabelSourceMarker(confidence: LabelConfidence) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(if (confidence == LabelConfidence.UNSURE) KpknLineIcons.Info else KpknLineIcons.Check, null,
            tint = WizardColors.textMuted, modifier = Modifier.size(16.dp))
        Text(when (confidence) {
            LabelConfidence.READ -> "Leído de la foto · revisa"
            LabelConfidence.DERIVED -> "Calculado desde la etiqueta · revisa"
            LabelConfidence.UNSURE -> "Dudoso · leído de la foto; revisa"
        }, style = KpknType.caption, color = WizardColors.textMuted)
    }
}

@Composable
internal fun LabelReadingOverlay(onCancel: () -> Unit) {
    BackHandler { onCancel() }
    Box(Modifier.fillMaxSize().background(KpknSurface.canvas.copy(alpha = 0.96f)).pointerInput(Unit) { detectTapGestures {} }, contentAlignment = Alignment.Center) {
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Leyendo la etiqueta…", style = KpknType.title, color = WizardColors.text)
            Text("La imagen se procesa en el dispositivo.", style = KpknType.body, color = WizardColors.textMuted)
            KpknSecondaryPill("Cancelar lectura", onCancel, Modifier.fillMaxWidth())
        }
    }
}
