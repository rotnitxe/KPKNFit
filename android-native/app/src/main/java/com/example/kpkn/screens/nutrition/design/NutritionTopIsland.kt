package com.example.kpkn.screens.nutrition.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.nutrition.NutritionDisplayFormat
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.onboarding.design.formatKcalEs
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknTopIsland

/**
 * Isla superior de Nutrición (KPKN_UI_V2 §3): «Hoy» con «1.320 kcal restantes» y el icono de energía en un círculo `inset`. Sin «+»: el
 * único acceso primario al registro es la isla «Registrar comida» de abajo (N2). Aparece cuando la cabecera sale de la vista.
 */
@Composable
fun NutritionTopIsland(
    visible: Boolean,
    title: String,
    subtitle: String?,
) {
    KpknTopIsland(
        visible = visible,
        title = title,
        subtitle = subtitle,
        leading = {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(KpknSurface.inset),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = KpknLineIcons.Nutrition,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = SymbolPalette.energia,
                )
            }
        },
    )
}

/**
 * Subtítulo de la isla: lo que queda de la meta de kcal («1.320 kcal restantes», «120 kcal de más») o «Sin objetivos». Sin el estado
 * del usuario ya leído no hay subtítulo: no se adelanta un «Sin objetivos» que aún no se sabe.
 */
internal fun nutritionIslandSubtitle(loaded: Boolean, kcal: Int, kcalGoal: Int?, estimate: Boolean): String? {
    if (!loaded) return null
    if (kcalGoal == null) return "Sin objetivos"
    val approx = NutritionDisplayFormat.approxPrefix(estimate)
    return if (kcal > kcalGoal) {
        "$approx${formatKcalEs(kcal - kcalGoal)} kcal de más"
    } else {
        "$approx${formatKcalEs(kcalGoal - kcal)} kcal restantes"
    }
}
