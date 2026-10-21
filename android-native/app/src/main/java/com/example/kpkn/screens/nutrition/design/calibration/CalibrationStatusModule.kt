package com.example.kpkn.screens.nutrition.design.calibration

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknModuleHeader

/** Estado, incertidumbre y calidad del estimador; no modifica el perfil de hábitos guardado. */
@Composable
fun CalibrationStatusModule(
    estimate: com.example.kpkn.domain.nutrition.expenditure.ExpenditureEstimate?,
    modifier: Modifier = Modifier,
) {
    KpknModule(modifier = modifier.fillMaxWidth()) {
        KpknModuleHeader(label = "Gasto estimado")
        Spacer(Modifier.height(12.dp))
        com.example.kpkn.screens.nutrition.checkin.CheckinEstimateDetails(estimate)
    }
}

/** El ajuste con signo y sin separador: «+120», «−120» (con el menos tipográfico) o «0». */
internal fun signedKcal(kcal: Int): String = when {
    kcal > 0 -> "+$kcal"
    kcal < 0 -> "−${-kcal}"
    else -> "0"
}
