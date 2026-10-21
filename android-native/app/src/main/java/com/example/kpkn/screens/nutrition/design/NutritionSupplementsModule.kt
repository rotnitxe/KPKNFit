package com.example.kpkn.screens.nutrition.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.nutrition.CaffeineLimitEngine
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.onboarding.design.formatDecimalEs
import com.example.kpkn.screens.onboarding.design.formatKcalEs
import com.example.kpkn.ui.design.KpknCheckRow
import com.example.kpkn.ui.design.KpknMetric
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknModuleHeader
import com.example.kpkn.ui.design.KpknSkeletonModule
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknType
import kotlin.math.roundToInt

/**
 * «Suplementos» (NUTRITION_V1 §2.6): la cafeína del día con un medidor de arco frente a su límite, y la creatina con un check. El check
 * marca la dosis de hoy cuando se llega a la dosis del día; si no está hecha, tocarlo registra la dosis rápida (5 g) en el día elegido.
 * Si ya está hecha, tocarlo abre el seguimiento. La racha va en el subtítulo, en `caption`. Sin [loaded] es un esqueleto.
 */
@Composable
fun NutritionSupplementsModule(
    loaded: Boolean,
    caffeineMg: Double,
    caffeineLimits: CaffeineLimitEngine.CaffeineLimits,
    creatineTodayG: Double,
    creatineTargetG: Double,
    creatineStreakDays: Int,
    isToday: Boolean,
    onQuickAddCreatine: () -> Unit,
    onOpenCreatine: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!loaded) {
        KpknSkeletonModule(lines = 2, modifier = modifier)
    } else {
        val safety = caffeineLimits.safetyMaxMg
        val creatineDone = creatineTargetG > 0.0 && creatineTodayG >= creatineTargetG
        KpknModule(modifier = modifier) {
            KpknModuleHeader(label = "Suplementos")
            Spacer(Modifier.height(14.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                NutritionArcGauge(
                    progress = if (safety > 0.0) (caffeineMg / safety).toFloat() else 0f,
                    color = caffeineTone(mg = caffeineMg, limits = caffeineLimits),
                )
                Spacer(Modifier.width(14.dp))
                Column {
                    KpknMetric(value = formatKcalEs(caffeineMg.roundToInt()), unit = "mg", style = KpknType.metricM)
                    Text(
                        text = "Cafeína · de ${formatKcalEs(safety.roundToInt())} mg",
                        style = KpknType.caption,
                        color = WizardColors.textMuted,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(KpknSurface.divider))
            Spacer(Modifier.height(8.dp))
            KpknCheckRow(
                checked = creatineDone,
                title = if (isToday) "Creatina de hoy" else "Creatina del día",
                subtitle = "${formatDecimalEs(creatineTodayG, 1)} de ${formatDecimalEs(creatineTargetG, 1)} g · " +
                    creatineStreakText(creatineStreakDays),
                onClick = { if (creatineDone) onOpenCreatine() else onQuickAddCreatine() },
            )
        }
    }
}

/** Color del medidor de cafeína: `danger` si se pasa del límite de seguridad, `ok` dentro del rango útil y energía en el resto. */
private fun caffeineTone(mg: Double, limits: CaffeineLimitEngine.CaffeineLimits): Color {
    val min = limits.idealMinMg
    val max = limits.idealMaxMg
    return when {
        mg > limits.safetyMaxMg -> WizardColors.danger
        min != null && max != null && mg >= min && mg <= max -> SymbolPalette.ok
        else -> SymbolPalette.energia
    }
}

/** La racha de creatina en palabras: «6 días seguidos», «1 día seguido» o «sin racha». */
private fun creatineStreakText(days: Int): String = when (days) {
    0 -> "sin racha"
    1 -> "1 día seguido"
    else -> "$days días seguidos"
}
