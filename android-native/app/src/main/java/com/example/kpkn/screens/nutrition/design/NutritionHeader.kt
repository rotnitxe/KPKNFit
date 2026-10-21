package com.example.kpkn.screens.nutrition.design

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknIconButton
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknType
import com.example.kpkn.ui.tour.TourAnchors
import com.example.kpkn.ui.tour.tourAnchor
import java.util.Locale

/**
 * Cabecera de Nutrición sobre el negro (KPKN_UI_V2 §1): la fecha en eyebrow, «Nutrición» en `display` y, a la derecha, el botón que abre
 * el editor del plan. Sin plan, «Crear plan» ocupa ese sitio. Mientras el estado del usuario no se ha leído ([loaded] false), no hay acción:
 * no se afirma que falte un plan. Va bajo la barra de estado y deja 14 dp hasta el primer módulo.
 */
@Composable
fun NutritionHeader(
    dateLabel: String,
    loaded: Boolean,
    hasActivePlan: Boolean,
    onEditPlan: () -> Unit,
    onCreatePlan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = topInset + 12.dp, end = 12.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = dateLabel.uppercase(Locale("es")),
                style = KpknType.eyebrow,
                color = WizardColors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "Nutrición",
                style = KpknType.display,
                color = WizardColors.text,
                maxLines = 1,
                modifier = Modifier.semantics { heading() },
            )
        }
        if (!loaded) {
            // Sin dato todavía: no hay acción, ni «Crear plan» ni el botón del plan.
        } else if (hasActivePlan) {
            KpknIconButton(
                icon = KpknLineIcons.Sliders,
                contentDescription = "Editar el plan",
                onClick = onEditPlan,
                modifier = Modifier.tourAnchor(TourAnchors.NUTRITION_PLAN),
            )
        } else {
            KpknTextAction(text = "Crear plan", onClick = onCreatePlan)
        }
    }
}
