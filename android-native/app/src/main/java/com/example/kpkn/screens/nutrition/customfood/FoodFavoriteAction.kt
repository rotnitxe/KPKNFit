package com.example.kpkn.screens.nutrition.customfood

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.nutrition.design.logger.LoggerColors
import com.example.kpkn.screens.nutrition.design.logger.LoggerIconButton
import com.example.kpkn.screens.nutrition.design.logger.LoggerIcons

/**
 * La estrella de favorito del lienzo Registros v4 (`.fav`, s-results y s-detail): 36 dp, llena en energía si está marcada y de línea en
 * gris si no. La selección se anuncia además del cambio de color; el área táctil es de 48 dp.
 */
@Composable
fun FoodFavoriteAction(name: String, selected: Boolean, onToggle: () -> Unit) {
    LoggerIconButton(
        icon = if (selected) LoggerIcons.StarFilled else LoggerIcons.Star,
        contentDescription = if (selected) "Quitar $name de favoritos" else "Añadir $name a favoritos",
        onClick = onToggle,
        modifier = Modifier.semantics { stateDescription = if (selected) "Favorito" else "Sin marcar" },
        tint = if (selected) LoggerColors.ene else LoggerColors.dim,
        size = 36.dp,
        iconSize = 18.dp,
    )
}
