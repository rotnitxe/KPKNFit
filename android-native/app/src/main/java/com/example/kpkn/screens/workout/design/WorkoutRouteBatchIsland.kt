package com.example.kpkn.screens.workout.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.KpknGlassVariant
import com.example.kpkn.ui.components.kpknGlassOrFallback
import com.example.kpkn.ui.design.KpknCompactPill
import com.example.kpkn.ui.design.KpknShape
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknType

/**
 * Isla de vidrio del modo dios de la ruta (KPKN LS4, §4): cuenta lo elegido y ofrece las acciones por lote de siempre como píldoras
 * (superserie con dos o más, omitir, eliminar y ultrarrápido). «Deshacer» aparece si hay cambios que deshacer. Sin estado.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WorkoutRouteBatchIsland(
    selectedCount: Int,
    undoAvailable: Boolean,
    onCreateSuperset: () -> Unit,
    onSkip: () -> Unit,
    onDelete: () -> Unit,
    onUltraFast: () -> Unit,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .kpknGlassOrFallback(null, KpknShape.island, variant = KpknGlassVariant.ISLAND)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = when (selectedCount) {
                    0 -> "Elige ejercicios"
                    1 -> "1 elegido"
                    else -> "$selectedCount elegidos"
                },
                style = KpknType.bodyStrong,
                color = WizardColors.text,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            if (undoAvailable) {
                KpknTextAction(text = "Deshacer", onClick = onUndo)
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            KpknCompactPill(
                text = "Superserie",
                onClick = onCreateSuperset,
                height = 40.dp,
                filled = false,
                enabled = selectedCount >= 2,
            )
            KpknCompactPill(
                text = "Omitir",
                onClick = onSkip,
                height = 40.dp,
                filled = false,
                enabled = selectedCount > 0,
            )
            KpknCompactPill(
                text = "Eliminar",
                onClick = onDelete,
                height = 40.dp,
                filled = false,
                enabled = selectedCount > 0,
            )
            KpknCompactPill(
                text = "Ultrarrápido",
                onClick = onUltraFast,
                height = 40.dp,
                filled = false,
                enabled = selectedCount > 0,
            )
        }
    }
}
