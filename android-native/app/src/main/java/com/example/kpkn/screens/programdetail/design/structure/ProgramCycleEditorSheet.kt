package com.example.kpkn.screens.programdetail.design.structure

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.LoopType
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknSegmentedControl
import com.example.kpkn.ui.design.KpknSegmentedSize
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknTextField
import com.example.kpkn.ui.design.KpknType

/**
 * Editor de un ciclo (P3c, sustituye al diálogo «Editar Loop»): nombre, tipo y «Repetir cada» en ciclos, con el choque de cadencia debajo
 * del campo. Con un ciclo existente, además sus acciones: posponer la próxima semana, cancelar o reactivar, y eliminar, que confirma la
 * hoja de Ciclos. Guardar exige nombre y una cadencia de al menos 1 que no choque con otro ciclo.
 */
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
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                text = if (loop == null) "Nuevo ciclo" else "Editar ciclo",
                style = KpknType.title,
                color = WizardColors.text,
            )
            Spacer(Modifier.height(16.dp))
            KpknTextField(
                value = title,
                onValueChange = { title = it },
                label = "Nombre",
                placeholder = "Por ejemplo, descarga de verano",
                singleLine = true,
            )
            Spacer(Modifier.height(16.dp))
            ProgramSectionLabel(text = "Tipo")
            Spacer(Modifier.height(8.dp))
            KpknSegmentedControl(
                options = types.map(::cycleTypeLabel),
                selectedIndex = types.indexOf(type).coerceAtLeast(0),
                onSelect = { index -> type = types[index] },
                size = KpknSegmentedSize.Compact,
            )
            Spacer(Modifier.height(16.dp))
            KpknTextField(
                value = cadenceText,
                onValueChange = { cadenceText = it.filter(Char::isDigit) },
                label = "Repetir cada (ciclos)",
                placeholder = DEFAULT_CADENCE.toString(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                error = conflict?.let { cycleConflictLine(it, cadence) },
            )
            if (loop != null) {
                Spacer(Modifier.height(24.dp))
                ProgramSectionLabel(text = "Este ciclo")
                Spacer(Modifier.height(4.dp))
                KpknTextAction(
                    text = "Posponer la próxima semana",
                    onClick = { onAction(ProgramCycleAction.POSTPONE_NEXT) },
                )
                if (loop.cancelled) {
                    KpknTextAction(
                        text = "Reactivar ciclo",
                        onClick = { onAction(ProgramCycleAction.REACTIVATE) },
                    )
                } else {
                    ProgramDangerAction(
                        text = "Cancelar ciclo",
                        onClick = { onAction(ProgramCycleAction.CANCEL) },
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            KpknPrimaryPill(
                text = "Guardar",
                onClick = {
                    onSave(ProgramCycleDraft(loopId = loop?.id, title = title.trim(), type = type, cadence = cadence))
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = canSave,
            )
            if (loop != null) {
                Box(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), contentAlignment = Alignment.Center) {
                    ProgramDangerAction(
                        text = "Eliminar",
                        onClick = { onAction(ProgramCycleAction.DELETE) },
                    )
                }
            }
        }
    }
}

/** La cadencia que se propone a un ciclo nuevo: cada 4 ciclos, como antes. */
private const val DEFAULT_CADENCE = 4
