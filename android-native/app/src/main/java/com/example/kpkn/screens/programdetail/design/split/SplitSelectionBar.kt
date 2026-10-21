package com.example.kpkn.screens.programdetail.design.split

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kpkn.ui.design.KpknPrimaryPill

/**
 * Píldora que flota sobre el catálogo en modo comparar o de selección múltiple (P5): «Comparar 2» o «Aplicar 3 repartos». Va sobre la
 * barra de gestos y deshabilitada mientras no hay lo que haga falta. Quien la usa la sitúa con su [modifier] (`Modifier.align`).
 */
@Composable
internal fun SplitSelectionBar(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        KpknPrimaryPill(
            text = label,
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            tag = "split_barra_accion",
        )
    }
}
