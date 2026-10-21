package com.example.kpkn.screens.programdetail.design.split

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknTextField

/** Buscador del catálogo (P5): una línea, con la lupa KPKN y el marcador «Buscar un reparto». Sin estado. */
@Composable
internal fun SplitSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    KpknTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        placeholder = "Buscar un reparto",
        singleLine = true,
        leadingIcon = KpknLineIcons.Search,
    )
}
