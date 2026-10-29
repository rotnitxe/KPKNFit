package com.example.kpkn.screens.programdetail.design.structure

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.LoopType
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.components.icons.KpknLineIcons

/**
 * Editor de un ciclo, con el patrón de e-block (el lienzo no lo dibuja): «Nombre», «Tipo» con chips y «Repetir cada (ciclos)», con el
 * choque de cadencia debajo del campo. Con un ciclo existente, además el grupo «Este ciclo» en filas `.mi`: posponer la próxima vez,
 * cancelar o reactivar, y «Eliminar ciclo» en `--danger`, que confirma la hoja de Ciclos. Guardar exige nombre y una cadencia de al menos
 * 1 que no choque con otro ciclo.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ProgramCycleEditorSheet(
    loop: ProgramCycleLoop?,
    existing: List<ProgramCycleLoop>,
    onSave: (ProgramCycleDraft) -> Unit,
    onAction: (ProgramCycleAction) -> Unit,
    onDismiss: () -> Unit,
) {
    val types = LoopType.entries
    var title by remember(loop?.id) { mutableStateOf(loop?.title.orEmpty()) }
    var type by remember(loop?.id) { mutableStateOf(loop?.type ?: LoopType.CUSTOM) }
    var cadenceText by remember(loop?.id) { mutableStateOf((loop?.cadence ?: DEFAULT_CADENCE).toString()) }
    val cadence = cadenceText.toIntOrNull()?.coerceAtLeast(1) ?: 0
    val conflict = cycleCadenceConflict(existing, loop?.id, cadence)
    val canSave = title.isNotBlank() && cadence > 0 && conflict == null

    KpknSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(StructureSheetPadding),
        ) {
            StructureSheetHeader(title = if (loop == null) "Nuevo ciclo" else loop.title.ifBlank { "Ciclo" })
            StructureTextField(label = "Nombre", value = title, onValueChange = { title = it }, placeholder = "Por ejemplo, descarga de verano")
            StructureSheetGroup(text = "Tipo")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                types.forEach { option ->
                    StructureChip(text = cycleTypeLabel(option), selected = type == option, onClick = { type = option })
                }
            }
            StructureTextField(
                label = "Repetir cada (ciclos)",
                value = cadenceText,
                onValueChange = { cadenceText = it.filter(Char::isDigit) },
                placeholder = DEFAULT_CADENCE.toString(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.padding(top = 6.dp),
            )
            conflict?.let {
                StructureCaption(text = cycleConflictLine(it, cadence), color = WizardColors.danger, modifier = Modifier.padding(top = 8.dp))
            }
            if (loop != null) {
                StructureSheetGroup(text = "Este ciclo")
                StructureMenuRow(
                    icon = KpknLineIcons.Clock,
                    title = "Posponer la próxima semana",
                    divider = false,
                    onClick = { onAction(ProgramCycleAction.POSTPONE_NEXT) },
                )
                if (loop.cancelled) {
                    StructureMenuRow(icon = KpknLineIcons.Play, title = "Reactivar ciclo", onClick = { onAction(ProgramCycleAction.REACTIVATE) })
                } else {
                    StructureMenuRow(icon = KpknLineIcons.Pause, title = "Cancelar ciclo", onClick = { onAction(ProgramCycleAction.CANCEL) })
                }
            }
            StructurePrimaryPill(
                text = "Guardar",
                onClick = { onSave(ProgramCycleDraft(loopId = loop?.id, title = title.trim(), type = type, cadence = cadence)) },
                enabled = canSave,
                modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
            )
            if (loop != null) {
                StructureDangerAction(text = "Eliminar ciclo", onClick = { onAction(ProgramCycleAction.DELETE) })
            }
        }
    }
}

/** La cadencia que se propone a un ciclo nuevo: cada 4 ciclos, como antes. */
private const val DEFAULT_CADENCE = 4
