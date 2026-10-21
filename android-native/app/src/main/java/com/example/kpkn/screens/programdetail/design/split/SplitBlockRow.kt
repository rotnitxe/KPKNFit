package com.example.kpkn.screens.programdetail.design.split

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.splits.SPLIT_TEMPLATES
import com.example.kpkn.data.splits.isVisibleForApplication
import com.example.kpkn.domain.training.SplitBlockOption
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.KpknDropdownMenu
import com.example.kpkn.ui.design.KpknSecondaryPill
import com.example.kpkn.ui.design.KpknType

/**
 * Reparto de un bloque en un programa avanzado (P5): el nombre del bloque en `caption` y, debajo, el reparto que le toca como píldora
 * secundaria. Al tocarla se abre la lista de repartos del catálogo. Sin estado propio de negocio: la elección sube por [onSelect].
 */
@Composable
internal fun SplitBlockRow(
    block: SplitBlockOption,
    selectedSplitId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val choices = SPLIT_TEMPLATES.filter { it.id != "custom" && it.isVisibleForApplication }
    val current = choices.firstOrNull { it.id == selectedSplitId }
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "${block.macroName} · ${block.name}",
            style = KpknType.caption,
            color = WizardColors.textMuted,
        )
        Spacer(Modifier.height(4.dp))
        Box(modifier = Modifier.fillMaxWidth()) {
            KpknSecondaryPill(
                text = current?.name ?: "Elegir reparto",
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth(),
            )
            KpknDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                choices.forEach { split ->
                    DropdownMenuItem(
                        text = { Text(split.name) },
                        onClick = {
                            onSelect(split.id)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}
