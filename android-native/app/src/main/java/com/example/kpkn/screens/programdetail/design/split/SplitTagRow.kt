package com.example.kpkn.screens.programdetail.design.split

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.splits.SplitTag
import com.example.kpkn.ui.design.KpknChip

/**
 * Etiquetas del catálogo (P5): una fila de [KpknChip] que se desliza, con «Todos» primero. Tocar la etiqueta activa vuelve a «Todos».
 * La fila sale de borde a borde y su primer y último chip respiran con el margen de 16 dp.
 */
@Composable
internal fun SplitTagRow(
    selectedTag: SplitTag?,
    onSelectTag: (SplitTag?) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "todos") {
            KpknChip(
                text = "Todos",
                selected = selectedTag == null,
                onClick = { onSelectTag(null) },
            )
        }
        items(SplitTag.entries, key = { it.name }) { tag ->
            KpknChip(
                text = splitTagLabel(tag),
                selected = selectedTag == tag,
                onClick = { onSelectTag(if (selectedTag == tag) null else tag) },
            )
        }
    }
}
