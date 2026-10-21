package com.example.kpkn.screens.nutrition.design.editor

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kpkn.ui.components.KpknGlassVariant
import com.example.kpkn.ui.components.LocalHazeState
import com.example.kpkn.ui.components.kpknGlassOrFallback
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknShape

/**
 * Isla de «Guardar»: el único botón de guardar del editor. Vidrio de isla a la izquierda el cambio que se va a guardar («2.300 → 2.150
 * kcal») y, a la derecha, la píldora. Se habilita con cambios y sin guardado en curso; mientras guarda, la píldora dice «Guardando…».
 */
@Composable
fun EditorSaveIsland(
    changeText: String,
    canSave: Boolean,
    saving: Boolean,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hazeState = LocalHazeState.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .kpknGlassOrFallback(hazeState, KpknShape.island, variant = KpknGlassVariant.ISLAND)
            .padding(start = 20.dp, top = 8.dp, end = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EditorCaption(text = changeText, numeric = true, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        KpknPrimaryPill(
            text = if (saving) "Guardando…" else "Guardar",
            onClick = onSave,
            enabled = canSave,
        )
    }
}
