package com.example.kpkn.ui.design

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.ui.components.icons.KpknLineIcons

/** Columna de una tabla de series. [label] es lo que se ve y [spoken] lo que lee TalkBack («Reps» se lee «repeticiones»). */
data class KpknSetColumn(
    val key: String,
    val label: String,
    val weight: Float = 1f,
    val spoken: String = label,
)

/** Tipo de serie: de trabajo, de aproximación («A») o drop («D»). Solo cambia el color y lo que lee TalkBack. */
enum class KpknSetKind { WORK, APPROACH, DROP }

/** Lo que hay en una celda de una serie, indicada por la [KpknSetColumn.key] de su columna. */
data class KpknSetCell(
    val value: String,
    val input: KpknCellInput,
    val contentDescription: String,
    val onStep: ((Int) -> Unit)? = null,
    val error: Boolean = false,
)

/** Una serie. [id] es la que recibe [KpknSetTable] al pulsar largo y [label] es lo que se ve en la primera columna («1», «A», «D»). */
data class KpknSetRow(
    val id: String,
    val label: String,
    val kind: KpknSetKind,
    val cells: Map<String, KpknSetCell>,
)

/** Cabecera de la tabla: eyebrow sin mayúsculas forzadas ni tracking, porque las etiquetas son palabras exactas («Descanso»). */
private val SetHeaderStyle = KpknType.eyebrow.copy(letterSpacing = 0.sp)

/** Primera columna: cabe «Serie» al 130 % y su área es el objetivo de la pulsación larga (48 dp). */
private val SetNumberColumn = 48.dp

/**
 * Tabla de series (KPKN_UI_V2 §3, SE1). Sin estado y sin `LazyColumn`: va dentro de la lista del editor.
 * - Cabecera «Serie» y las etiquetas de [columns] en `eyebrow` `textFaint`, centradas sobre sus celdas. Si una etiqueta es más ancha que
 *   su columna (Descanso al 130 %), se desborda a los lados en vez de cortarse.
 * - Cada fila mide 48 dp y separa 8 dp de la siguiente, sin filetes. La primera columna muestra [KpknSetRow.label] en `bodyStrong`
 *   tabular: «A» en `textMuted`, «D» en el músculo. Su pulsación larga llama a [onRowMenu] con el id de la fila.
 * - Cada celda es una [KpknNumberCell]. Sin dato, «–».
 * - Con [onAddSet], pie «Añadir serie». Sin filas, solo ese pie (sin cabecera).
 */
@Composable
fun KpknSetTable(
    columns: List<KpknSetColumn>,
    rows: List<KpknSetRow>,
    modifier: Modifier = Modifier,
    onAddSet: (() -> Unit)? = null,
    onRowMenu: ((rowId: String) -> Unit)? = null,
    addLabel: String = "Añadir serie",
    /** Recibe el rectángulo de la celda del número de cada serie, para anclar su menú. Sin él no se mide nada. */
    onRowBounds: ((rowId: String, bounds: Rect) -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (rows.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SetHeaderLabel(text = "Serie", spoken = "Serie", modifier = Modifier.width(SetNumberColumn))
                columns.forEach { column ->
                    SetHeaderLabel(text = column.label, spoken = column.spoken, modifier = Modifier.weight(column.weight))
                }
            }
        }
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth().height(48.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                KpknSetRowNumber(
                    row = row,
                    onRowMenu = onRowMenu,
                    onBounds = onRowBounds?.let { report -> { rect: Rect -> report(row.id, rect) } },
                )
                columns.forEach { column ->
                    val cell = row.cells[column.key]
                    KpknNumberCell(
                        value = cell?.value.orEmpty(),
                        input = cell?.input ?: KpknCellInput.ReadOnly,
                        contentDescription = cell?.contentDescription ?: "${column.spoken}, sin dato",
                        modifier = Modifier.weight(column.weight).fillMaxHeight(),
                        onStep = cell?.onStep,
                        error = cell?.error ?: false,
                    )
                }
            }
        }
        if (onAddSet != null) {
            KpknTextAction(text = addLabel, onClick = onAddSet, leadingIcon = KpknLineIcons.Plus)
        }
    }
}

/** Etiqueta de cabecera centrada sobre su columna. Puede desbordarla a partes iguales; nunca se corta. */
@Composable
private fun SetHeaderLabel(text: String, spoken: String, modifier: Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = SetHeaderStyle,
            color = WizardColors.textFaint,
            maxLines = 1,
            modifier = Modifier.wrapContentWidth(unbounded = true).semantics { contentDescription = spoken },
        )
    }
}

/** Primera columna de una serie: su número o letra, con el color de su tipo y la pulsación larga del menú. */
@Composable
private fun KpknSetRowNumber(row: KpknSetRow, onRowMenu: ((String) -> Unit)?, onBounds: ((Rect) -> Unit)? = null) {
    val ink = when (row.kind) {
        KpknSetKind.WORK -> WizardColors.text
        KpknSetKind.APPROACH -> WizardColors.textMuted
        KpknSetKind.DROP -> SymbolPalette.musculo
    }
    val spoken = when (row.kind) {
        KpknSetKind.WORK -> "Serie ${row.label}"
        KpknSetKind.APPROACH -> "Serie de aproximación"
        KpknSetKind.DROP -> "Drop set"
    }
    Box(
        modifier = Modifier
            .width(SetNumberColumn)
            .fillMaxHeight()
            .onGloballyPositioned { coords -> onBounds?.invoke(coords.boundsInWindow()) }
            .semantics(mergeDescendants = true) {
                contentDescription = spoken
                if (onRowMenu != null) {
                    customActions = listOf(
                        CustomAccessibilityAction("Opciones de la serie ${row.label}") {
                            onRowMenu(row.id)
                            true
                        },
                    )
                }
            }
            .then(
                if (onRowMenu != null) {
                    Modifier.pointerInput(row.id, onRowMenu) {
                        detectTapGestures(onLongPress = { onRowMenu(row.id) })
                    }
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = row.label,
            style = KpknType.bodyStrong.copy(fontFeatureSettings = "'tnum' 1", textAlign = TextAlign.Center),
            color = ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
