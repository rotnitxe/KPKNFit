package com.example.kpkn.screens.programdetail.design

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.training.SwapKind
import com.example.kpkn.domain.training.SwapOption
import com.example.kpkn.domain.training.SwapScope
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknSecondaryPill
import com.example.kpkn.ui.design.KpknType

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ProgramSwapSheet(
    sourceName: String,
    options: List<SwapOption>,
    requiresEquipmentConfirmation: Boolean,
    previewDetail: String?,
    busy: Boolean,
    onPreview: (String,SwapScope,Boolean) -> Unit,
    onConfirm: () -> Unit,
    onEdit: () -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember(sourceName,options) { mutableStateOf<String?>(null) }
    var scope by remember(sourceName,options) { mutableStateOf(SwapScope.THIS_SESSION) }
    var equipmentConfirmed by remember(sourceName,options) { mutableStateOf(false) }
    KpknSheet(onDismissRequest = { if (!busy) onDismiss() },stableHeightFraction = 0.9f) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ProgramSheetHeader(title = sourceName, eyebrow = "Cambiar ejercicio")
            if (previewDetail != null) {
                Text(previewDetail,style = KpknType.body,color = WizardColors.text)
                if (!busy) {
                    KpknPrimaryPill(text = "Confirmar cambio", onClick = onConfirm, modifier = Modifier.fillMaxWidth())
                    KpknSecondaryPill(text = "Editar selección", onClick = onEdit, modifier = Modifier.fillMaxWidth())
                }
            } else {
                Text("Conserva repeticiones, RIR y descansos. La configuración nueva tendrá una carga por elegir y quedará como personalización manual.",style = KpknType.body,color = WizardColors.textMuted)
                if (options.isEmpty()) Text("Sin alternativas disponibles con estos filtros.",style = KpknType.body,color = WizardColors.text)
                options.forEach { option ->
                    FilterChip(selected = selected == option.configId,enabled = !busy,onClick = { selected = option.configId },
                        modifier = Modifier.fillMaxWidth().testTag("swap-option:${option.configId}"),
                        label = { Column {
                            Text(option.displayName)
                            if (option.variant.isNotBlank()) Text(option.variant,style = KpknType.caption)
                            Text(if (option.kind == SwapKind.EQUIVALENT) "Intención equivalente" else "Mismo patrón",style = KpknType.caption)
                        } })
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(SwapScope.THIS_SESSION to "Esta sesión",SwapScope.REST_OF_BLOCK to "Resto del bloque",SwapScope.WHOLE_PROGRAM to "Todo el programa").forEach { (value,label) ->
                        FilterChip(selected = scope == value,enabled = !busy,onClick = { scope = value },label = { Text(label) })
                    }
                }
                if (requiresEquipmentConfirmation) {
                    Text("El material de este lugar no está declarado. Comprueba la variante que elijas.",style = KpknType.body,color = WizardColors.textMuted)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = equipmentConfirmed,onCheckedChange = { equipmentConfirmed = it },enabled = !busy,
                            modifier = Modifier.testTag("swap-equipment-confirmation"))
                        Text("Tengo el material para esta variante",style = KpknType.body,color = WizardColors.text)
                    }
                }
                selected?.takeIf { !busy && (!requiresEquipmentConfirmation || equipmentConfirmed) }?.let { chosen ->
                    KpknPrimaryPill(text = "Revisar cambio", onClick = { onPreview(chosen,scope,equipmentConfirmed) }, modifier = Modifier.fillMaxWidth())
                }
            }
            if (busy) Text("Preparando…",style = KpknType.caption,color = WizardColors.textMuted)
            else KpknSecondaryPill(text = "Volver", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
        }
    }
}
