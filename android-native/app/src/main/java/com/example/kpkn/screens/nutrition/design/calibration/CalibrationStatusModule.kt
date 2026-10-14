package com.example.kpkn.screens.nutrition.design.calibration

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.NutritionCalibrationProfile
import com.example.kpkn.domain.nutrition.NutritionCalibrationEngine
import com.example.kpkn.screens.nutrition.calibrationStatusLabel
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.ui.design.KpknMetric
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknModuleHeader
import com.example.kpkn.ui.design.KpknProgressBar
import com.example.kpkn.ui.design.KpknType

/**
 * El módulo de estado de la calibración (N5, NUTRITION_V1 §5): el eyebrow «Ajuste semanal», el estado en `bodyStrong`, la cobertura frente
 * a los días que exige el motor con su barra, los días completos y los pesajes, y el ajuste sugerido en `metricL` con signo. Sin ajuste,
 * dice que aún no hay datos suficientes. Las cifras salen de [NutritionCalibrationEngine]; nada va escrito a mano.
 */
@Composable
fun CalibrationStatusModule(profile: NutritionCalibrationProfile, modifier: Modifier = Modifier) {
    val requiredDays = NutritionCalibrationEngine.MINIMUM_DAYS
    val adjustment = profile.recommendedAdjustmentKcal
    KpknModule(modifier = modifier.fillMaxWidth()) {
        KpknModuleHeader(label = "Ajuste semanal")
        Spacer(Modifier.height(8.dp))
        Text(
            text = calibrationStatusLabel(profile.status),
            style = KpknType.bodyStrong,
            color = WizardColors.text,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Cobertura: ${profile.observedDays} de $requiredDays días",
            style = KpknType.body,
            color = WizardColors.text,
        )
        Spacer(Modifier.height(8.dp))
        KpknProgressBar(
            progress = profile.observedDays.toFloat() / requiredDays,
            color = SymbolPalette.energia,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Días completos: ${profile.completeDays} · Pesajes: ${profile.weightReadings}",
            style = KpknType.caption,
            color = WizardColors.textMuted,
        )
        Spacer(Modifier.height(18.dp))
        Text(text = "Ajuste sugerido", style = KpknType.caption, color = WizardColors.textMuted)
        Spacer(Modifier.height(4.dp))
        if (adjustment != null) {
            KpknMetric(value = signedKcal(adjustment), unit = "kcal", style = KpknType.metricL)
        } else {
            Text(
                text = "Aún no hay datos suficientes para ajustar",
                style = KpknType.body,
                color = WizardColors.textMuted,
            )
        }
    }
}

/** El ajuste con signo y sin separador: «+120», «−120» (con el menos tipográfico) o «0». */
internal fun signedKcal(kcal: Int): String = when {
    kcal > 0 -> "+$kcal"
    kcal < 0 -> "−${-kcal}"
    else -> "0"
}
