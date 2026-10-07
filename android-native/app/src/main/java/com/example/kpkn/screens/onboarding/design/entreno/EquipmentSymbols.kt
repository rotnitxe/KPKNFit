package com.example.kpkn.screens.onboarding.design.entreno

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.onboarding.EquipmentSymbolId
import com.example.kpkn.screens.onboarding.design.WizardTypography
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion

/** Marca de prueba del símbolo de un implemento: `setup-equipment-BARBELL`, … */
internal fun equipmentSymbolTag(id: EquipmentSymbolId): String = "setup-equipment-${id.name}"

/**
 * Lado del lienzo de un símbolo y fracción que ocupa el dibujo: el ícono mide ≈ 60 dp y el resto es margen, que
 * es donde cae la marca de «hecho» (en la esquina del lienzo, así casi no pisa el dibujo).
 */
private val SYMBOL_CANVAS = 76.dp
private const val SYMBOL_ART_SCALE = 60f / 76f

/** A partir de este ancho la cuadrícula pasa de 3 a 4 columnas. */
private val WIDE_GRID = 600.dp

/**
 * «¿Con qué material entrenas?»: cuadrícula de símbolos de implemento sin caja, de 3 columnas (4 en pantallas
 * anchas). Cada celda es el ícono de línea (≈ 56 dp) y su etiqueta (hasta dos líneas); al marcarlo, el ícono se
 * enciende (tinta plena y un detalle en su acento), traza la marca de «hecho» y hace el movimiento propio del
 * implemento en bucle. Los símbolos que NO están marcados son un dibujo estático tenue.
 *
 * [symbols] son los que se muestran (ya filtrados por lugar) y se pintan en ese orden; [selected] puede traer
 * ids que no se muestran (no se tocan). Esta pieza solo pinta: que «Solo peso corporal» sea exclusivo lo decide
 * quien posee el paso. Un único reloj mueve todos los símbolos marcados, solo mientras haya alguno, la
 * cuadrícula se vea y la app esté en primer plano. Cada símbolo es una casilla (`Role.Checkbox`) anunciada como
 * «Barra y discos, seleccionado».
 */
@Composable
fun EquipmentSymbolGrid(
    symbols: List<EquipmentSymbolId>,
    selected: Set<EquipmentSymbolId>,
    onToggle: (EquipmentSymbolId) -> Unit,
    modifier: Modifier = Modifier,
) {
    EquipmentSymbolGrid(symbols, selected, onToggle, wizardReducedMotion(), modifier)
}

/** La cuadrícula con el «reducir movimiento» decidido por quien llama (pruebas y vista previa de depuración). */
@Composable
internal fun EquipmentSymbolGrid(
    symbols: List<EquipmentSymbolId>,
    selected: Set<EquipmentSymbolId>,
    onToggle: (EquipmentSymbolId) -> Unit,
    reduced: Boolean,
    modifier: Modifier = Modifier,
) {
    val foreground = rememberEntrenoForeground()
    var visible by remember { mutableStateOf(true) }
    val anySelected = symbols.any { it in selected }
    val clock = rememberSymbolClock(active = !reduced && foreground && visible && anySelected)
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .onSymbolsVisible { visible = it },
    ) {
        val columns = if (maxWidth >= WIDE_GRID) 4 else 3
        val rows = remember(symbols, columns) { symbols.chunked(columns) }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            for (row in rows) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    for (id in row) {
                        key(id) {
                            SymbolCell(
                                art = equipmentArt(id),
                                label = id.label,
                                tag = equipmentSymbolTag(id),
                                selected = id in selected,
                                clock = clock,
                                reducedMotion = reduced,
                                onToggle = { onToggle(id) },
                                canvasModifier = Modifier.size(SYMBOL_CANVAS),
                                labelStyle = WizardTypography.note,
                                artScale = SYMBOL_ART_SCALE,
                                badgeRadius = 8.dp,
                                labelGap = 2.dp,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    // La última fila, si queda corta, deja los huecos vacíos: las columnas no se reparten de nuevo.
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}
