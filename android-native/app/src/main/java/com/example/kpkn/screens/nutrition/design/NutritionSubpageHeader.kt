package com.example.kpkn.screens.nutrition.design

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.design.KpknTopRow
import com.example.kpkn.ui.design.KpknType

/**
 * Cabecera de una subpágina de Nutrición (N5, NUTRITION_V1 §5): la fila de «Atrás» y el [title] en `display`, bajo la barra de estado
 * ([topInset]). Es la cabecera del historial y de la calibración.
 */
@Composable
fun NutritionSubpageHeader(
    title: String,
    topInset: Dp,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(top = topInset)) {
        KpknTopRow(onBack = onBack)
        Text(
            text = title,
            style = KpknType.display,
            color = WizardColors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp).semantics { heading() },
        )
    }
}
