package com.example.kpkn.screens.sessioneditor.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknChip
import com.example.kpkn.ui.design.KpknIconButton
import com.example.kpkn.ui.design.KpknSegmentedControl
import com.example.kpkn.ui.design.KpknSegmentedSize
import com.example.kpkn.ui.design.KpknType

/*
 * Piezas de la fila de intensidad y de las opciones del ejercicio desplegado (SE3b). Sin estado de negocio: quien las usa decide qué pasa al
 * pulsar cada una.
 */

/**
 * Fila de intensidad (KPKN_UI_V2 §6): el segmentado compacto «RIR · RPE · %» y, a la derecha, «Más» (⋮) con lo que no cabe en las opciones.
 * [selected] es el índice del segmento (0 RIR, 1 RPE, 2 %); negativo no marca ninguno (modos de tiempo, distancia o personalizado). [onMore]
 * recibe el rectángulo del botón para anclar su menú.
 */
@Composable
internal fun SessionIntensityRow(
    selected: Int,
    onSelect: (Int) -> Unit,
    onMore: (Rect) -> Unit,
    modifier: Modifier = Modifier,
) {
    var moreBounds by remember { mutableStateOf(Rect.Zero) }
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        KpknSegmentedControl(
            options = listOf("RIR", "RPE", "%"),
            selectedIndex = selected,
            onSelect = onSelect,
            size = KpknSegmentedSize.Compact,
            modifier = Modifier.weight(1f),
        )
        KpknIconButton(
            icon = KpknLineIcons.More,
            contentDescription = "Más opciones del ejercicio",
            onClick = { onMore(moreBounds) },
            modifier = Modifier.onGloballyPositioned { coords -> moreBounds = coords.boundsInWindow() },
        )
    }
}

/**
 * Fila de opciones del ejercicio (KPKN_UI_V2 §3): «Modo», «Unilateral», «Carga inteligente», «Técnica», «Nota» y «Cambiar ejercicio». En
 * una `FlowRow`, así que baja de línea si no caben. Los chips activos (unilateral, carga inteligente, técnica o nota con texto) se
 * ven seleccionados.
 */
@Composable
internal fun SessionOptionChips(
    modeLabel: String,
    unilateral: Boolean,
    smartLoad: Boolean,
    technique: Boolean,
    note: Boolean,
    onMode: () -> Unit,
    onUnilateral: () -> Unit,
    onSmartLoad: () -> Unit,
    onTechnique: () -> Unit,
    onNote: () -> Unit,
    onReplace: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        KpknChip(text = modeLabel, selected = false, onClick = onMode)
        KpknChip(text = "Unilateral", selected = unilateral, onClick = onUnilateral)
        KpknChip(text = "Carga inteligente", selected = smartLoad, onClick = onSmartLoad)
        KpknChip(text = "Técnica", selected = technique, onClick = onTechnique)
        KpknChip(text = "Nota", selected = note, onClick = onNote)
        KpknChip(text = "Cambiar ejercicio", selected = false, onClick = onReplace)
    }
}

/**
 * Panel «Unilateral», que se despliega bajo su chip. «Ambos lados» apaga la unilateralidad; «Alterno» y «Por lados» la encienden con lados
 * iguales o aparte. Con el ejercicio unilateral sale también el orden de los lados (L/R o R/L).
 *
 * @param choiceIndex 0 ambos lados, 1 alterno, 2 por lados (ver withUnilateralChoice).
 * @param unilateral si el ejercicio ya es unilateral (muestra el orden de los lados).
 * @param sideOrderIndex 0 izquierda primero, 1 derecha primero.
 */
@Composable
internal fun SessionUnilateralPanel(
    choiceIndex: Int,
    unilateral: Boolean,
    sideOrderIndex: Int,
    onChoose: (Int) -> Unit,
    onSideOrder: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        KpknSegmentedControl(
            options = listOf("Ambos lados", "Alterno", "Por lados"),
            selectedIndex = choiceIndex,
            onSelect = onChoose,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(text = unilateralCaption(choiceIndex), style = KpknType.caption, color = WizardColors.textMuted)
        if (unilateral) {
            Text(text = "Orden de los lados", style = KpknType.caption, color = WizardColors.textMuted)
            KpknSegmentedControl(
                options = listOf("L/R", "R/L"),
                selectedIndex = sideOrderIndex,
                onSelect = onSideOrder,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Lo que significa cada opción del panel, dicho en el dato real: lados iguales o lados aparte. */
private fun unilateralCaption(choiceIndex: Int): String = when (choiceIndex) {
    1 -> "Los dos lados con la misma carga: se alternan en cada serie."
    2 -> "Cada lado con su propia carga y repeticiones."
    else -> "El ejercicio se hace con los dos lados a la vez."
}
