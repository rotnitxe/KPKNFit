package com.example.kpkn.screens.nutrition.design.logger

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknTextField

/**
 * Campo de búsqueda del registro: una línea con la lupa KPKN delante. La búsqueda se lanza con la acción «Buscar» del teclado
 * ([onImeSearch]).
 */
@Composable
fun LoggerSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onImeSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    KpknTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        placeholder = "Busca un alimento para agregarlo",
        singleLine = true,
        leadingIcon = KpknLineIcons.Search,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onImeSearch() }),
    )
}
