package com.example.kpkn.screens.nutrition.customfood

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknIconButton

/** La selección se anuncia además del cambio de color; área táctil de 48 dp del botón KPKN. */
@Composable
fun FoodFavoriteAction(name: String, selected: Boolean, onToggle: () -> Unit) {
    KpknIconButton(KpknLineIcons.Star,
        if (selected) "Quitar $name de favoritos" else "Añadir $name a favoritos", onToggle,
        modifier = Modifier.semantics { stateDescription = if (selected) "Favorito" else "Sin marcar" },
        filled = selected,
        tint = if (selected) null else WizardColors.textMuted,
    )
}
