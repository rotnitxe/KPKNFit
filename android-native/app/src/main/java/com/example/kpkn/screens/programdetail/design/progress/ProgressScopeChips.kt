package com.example.kpkn.screens.programdetail.design.progress

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.kpkn.ui.design.KpknChip

/**
 * Selector de alcance de la pestaña Progreso: «Programa» siempre, y «Bloque N» o «Semana N» solo si [blockLabel] o [weekLabel] no son
 * nulos. El alcance elegido va en relleno de tinta. La fila se desplaza en horizontal si no cabe. Sin estado: [selected] marca cuál va
 * elegido y [onSelect] recibe el que se toca.
 */
@Composable
internal fun ProgressScopeChips(
    selected: ProgressScope,
    blockLabel: String?,
    weekLabel: String?,
    onSelect: (ProgressScope) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ProgressScopeChip(text = "Programa", scope = ProgressScope.PROGRAM, selected = selected, onSelect = onSelect)
        if (blockLabel != null) {
            ProgressScopeChip(text = blockLabel, scope = ProgressScope.BLOCK, selected = selected, onSelect = onSelect)
        }
        if (weekLabel != null) {
            ProgressScopeChip(text = weekLabel, scope = ProgressScope.WEEK, selected = selected, onSelect = onSelect)
        }
    }
}

/** Una píldora del selector, con la etiqueta de prueba «progress-scope-<NOMBRE>» para localizarla. */
@Composable
private fun ProgressScopeChip(
    text: String,
    scope: ProgressScope,
    selected: ProgressScope,
    onSelect: (ProgressScope) -> Unit,
) {
    KpknChip(
        text = text,
        selected = scope == selected,
        onClick = { onSelect(scope) },
        modifier = Modifier.testTag("progress-scope-${scope.name}"),
    )
}
