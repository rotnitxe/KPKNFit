package com.example.kpkn.screens.nutrition.design.logger

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.FoodCandidate
import com.example.kpkn.domain.nutrition.NutrientBasis
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknTextField
import com.example.kpkn.ui.design.KpknType

/**
 * Corrección de un alimento (N3b): el campo «Cambiar coincidencia» y las coincidencias que devuelve la búsqueda. [results] nulo significa
 * que la búsqueda aún no ha respondido; una lista vacía, que no hay nada. Elegir una coincidencia llama a [onPick].
 */
@Composable
fun LoggerFoodCorrection(
    query: String,
    results: List<FoodCandidate>?,
    onQueryChange: (String) -> Unit,
    onPick: (FoodCandidate) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        KpknTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = "Cambiar coincidencia",
            singleLine = true,
            leadingIcon = KpknLineIcons.Search,
        )
        when {
            results == null -> Text(text = "Buscando…", style = KpknType.caption, color = WizardColors.textFaint)
            results.isEmpty() -> Text(
                text = "No se encontraron coincidencias.",
                style = KpknType.caption,
                color = WizardColors.textMuted,
            )
            else -> results.forEach { candidate -> LoggerCorrectionRow(candidate = candidate, onPick = onPick) }
        }
    }
}

/** Una coincidencia: nombre en `bodyStrong` y, debajo, su pie (marca, confianza y kcal por su base). Toda la fila es el botón. */
@Composable
private fun LoggerCorrectionRow(candidate: FoodCandidate, onPick: (FoodCandidate) -> Unit) {
    val food = candidate.food
    val basis = kotlin.math.round(NutrientBasis.grams(food)).toInt()
    val kcal = kotlin.math.round(food.calories).toInt()
    val foot = listOfNotNull(loggerSearchCaption(candidate), "$kcal kcal por $basis g").joinToString(" · ")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clickable(role = Role.Button, onClickLabel = "Usar esta coincidencia") { onPick(candidate) }
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = food.name.trim().replaceFirstChar { it.uppercase() },
            style = KpknType.bodyStrong,
            color = WizardColors.text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = foot,
            style = KpknType.caption,
            color = WizardColors.textMuted,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
