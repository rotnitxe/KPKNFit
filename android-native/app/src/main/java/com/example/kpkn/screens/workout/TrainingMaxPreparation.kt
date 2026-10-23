package com.example.kpkn.screens.workout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.training.ExerciseTrainingMax
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.sessioneditor.formatEditableNumber
import com.example.kpkn.screens.sessioneditor.safeDoubleOrNull
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknTextField
import com.example.kpkn.ui.design.KpknType

/** Preparation never writes a proposed value until the athlete explicitly confirms. */
@Composable
internal fun TrainingMaxPreparation(missing: List<ExerciseTrainingMax.Missing>, enabled: Boolean,
    onConfirm: (String, Double) -> Unit) {
    val first = missing.firstOrNull() ?: return
    var dismissed by rememberSaveable(first.exercise.id) { mutableStateOf(false) }
    var open by remember(first.exercise.id) { mutableStateOf(false) }
    LaunchedEffect(enabled, first.exercise.id) { if (enabled && !dismissed) open = true }
    if (!enabled) return
    if (open) KpknSheet(onDismissRequest = { open = false; dismissed = true }) {
        TrainingMaxEntryBody(first.exercise.name, first.suggestedKg, first.fraction,
            onConfirm = { onConfirm(first.exercise.id, it) },
            onDismiss = { open = false; dismissed = true }, missing = true)
    }
}

/** Shared manual/confirmed TM entry. The suggested value has an explicit source and remains a draft. */
@Composable
internal fun TrainingMaxEntryBody(name: String, suggestedKg: Double?, fraction: Double?,
    onConfirm: (Double) -> Unit, onDismiss: () -> Unit, missing: Boolean = false,
    saving: Boolean = false, error: String? = null) {
    var input by rememberSaveable(name) { mutableStateOf(formatEditableNumber(suggestedKg)) }
    val value = input.safeDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(if (missing) "Falta tu TM de $name" else "TM de $name", style = KpknType.dialogTitle, color = WizardColors.text)
        Text(if (suggestedKg != null && fraction != null)
            "Propuesta: ${formatEditableNumber(fraction * 100).replace('.', ',')} % de tu 1RM. Confirma o ajusta el valor."
            else "Escribe tu máximo de entrenamiento. No se deduce de otra referencia.",
            style = KpknType.body, color = WizardColors.textMuted)
        KpknTextField(value = input, onValueChange = { input = it }, label = "TM (kg)",
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal), enabled = !saving)
        error?.let { Text(it, style = KpknType.body, color = WizardColors.text) }
        KpknPrimaryPill(text = if(saving) "Guardando TM…" else if(error != null) "Reintentar TM" else "Confirmar TM", onClick = { value?.let(onConfirm) }, enabled = value != null && !saving,
            modifier = Modifier.fillMaxWidth())
        KpknTextAction(text = "Ahora no", onClick = { if(!saving) onDismiss() },
            modifier = Modifier.semantics { if(saving) disabled() })
    }
}
