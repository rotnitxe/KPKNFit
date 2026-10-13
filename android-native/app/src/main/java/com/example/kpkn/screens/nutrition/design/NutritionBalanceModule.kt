package com.example.kpkn.screens.nutrition.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.DailyEnergyBalance
import com.example.kpkn.data.models.DailyEnergyStatus
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.onboarding.design.formatKcalEs
import com.example.kpkn.ui.design.KpknMetric
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknModuleHeader
import com.example.kpkn.ui.design.KpknProgressBar
import com.example.kpkn.ui.design.KpknSkeletonModule
import com.example.kpkn.ui.design.KpknType

/**
 * «Balance de hoy» (NUTRITION_V1 §2.5): una barra de lo comido frente a la meta y tres cifras, «Comido», «Entreno» y «Restante», sin
 * repetir ninguna. El gasto de entreno no cambia la meta: el motor no lo resta del consumo (`TrainingEnergyEngine`), así que va como
 * cifra y no como margen. Sin meta no hay barra ni «Restante»: se muestra «—». Sin [loaded] es un esqueleto.
 */
@Composable
fun NutritionBalanceModule(
    loaded: Boolean,
    balance: DailyEnergyBalance,
    modifier: Modifier = Modifier,
) {
    if (!loaded) {
        KpknSkeletonModule(lines = 2, modifier = modifier)
    } else {
        val consumed = balance.consumedKcal
        val burn = balance.trainingBurnKcal
        val target = balance.targetKcal.takeIf { it > 0 }
        val remaining = target?.let { it - consumed }
        KpknModule(modifier = modifier) {
            KpknModuleHeader(label = "Balance de hoy")
            Spacer(Modifier.height(14.dp))
            if (target != null) {
                val over = consumed > target
                KpknProgressBar(
                    progress = consumed.toFloat() / target,
                    color = if (over) WizardColors.danger else SymbolPalette.energia,
                    height = 8.dp,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Meta ${formatKcalEs(target)} kcal",
                        style = KpknType.caption,
                        color = WizardColors.textMuted,
                    )
                    Text(
                        text = balanceStatusLabel(balance.status),
                        style = KpknType.caption,
                        color = balanceStatusColor(balance.status),
                    )
                }
                Spacer(Modifier.height(16.dp))
            } else {
                Text(text = "Sin meta para este día", style = KpknType.caption, color = WizardColors.textMuted)
                Spacer(Modifier.height(12.dp))
            }
            val restanteColor = if (remaining != null && remaining < 0) WizardColors.danger else WizardColors.text
            if (LocalDensity.current.fontScale > NutritionLargeTextScale) {
                // Con la letra grande, tres columnas no caben: cada cifra es una fila, con la etiqueta a la izquierda y la cifra a la derecha.
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    BalanceRow(label = "Comido", value = consumed)
                    if (burn > 0) BalanceRow(label = "Entreno", value = burn, dot = SymbolPalette.musculo)
                    BalanceRow(label = "Restante", value = remaining, valueColor = restanteColor)
                }
            } else {
                Row(modifier = Modifier.fillMaxWidth()) {
                    BalanceFigure(
                        label = "Comido",
                        value = consumed,
                        modifier = Modifier.weight(1f),
                    )
                    if (burn > 0) {
                        BalanceFigure(
                            label = "Entreno",
                            value = burn,
                            dot = SymbolPalette.musculo,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    BalanceFigure(
                        label = "Restante",
                        value = remaining,
                        valueColor = restanteColor,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** Una cifra del balance: su etiqueta (con punto de color si lo tiene) y, debajo, la cifra con «kcal»; «—» si no hay dato. */
@Composable
private fun BalanceFigure(
    label: String,
    value: Int?,
    modifier: Modifier,
    dot: Color? = null,
    valueColor: Color = WizardColors.text,
) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (dot != null) {
                Box(Modifier.size(8.dp).background(dot, CircleShape))
                Spacer(Modifier.width(6.dp))
            }
            Text(text = label, style = KpknType.caption, color = WizardColors.textMuted, maxLines = 1)
        }
        Spacer(Modifier.height(4.dp))
        if (value == null) {
            KpknMetric(value = "—", style = KpknType.metricM, color = WizardColors.textFaint)
        } else {
            KpknMetric(value = formatKcalEs(value), unit = "kcal", style = KpknType.metricM, color = valueColor)
        }
    }
}

/** Una cifra del balance en fila (letra grande): la etiqueta a la izquierda y la cifra con «kcal» a la derecha; «—» si no hay dato. */
@Composable
private fun BalanceRow(
    label: String,
    value: Int?,
    dot: Color? = null,
    valueColor: Color = WizardColors.text,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (dot != null) {
                Box(Modifier.size(8.dp).background(dot, CircleShape))
                Spacer(Modifier.width(6.dp))
            }
            Text(text = label, style = KpknType.caption, color = WizardColors.textMuted, maxLines = 1)
        }
        if (value == null) {
            KpknMetric(value = "—", style = KpknType.metricM, color = WizardColors.textFaint)
        } else {
            KpknMetric(value = formatKcalEs(value), unit = "kcal", style = KpknType.metricM, color = valueColor)
        }
    }
}

/** Lo que dice el motor del día frente a la meta, con palabras neutras. */
private fun balanceStatusLabel(status: DailyEnergyStatus): String = when (status) {
    DailyEnergyStatus.DEFICIT -> "Déficit"
    DailyEnergyStatus.MAINTENANCE -> "Mantenimiento"
    DailyEnergyStatus.SURPLUS -> "Superávit"
}

private fun balanceStatusColor(status: DailyEnergyStatus): Color = when (status) {
    DailyEnergyStatus.DEFICIT -> SymbolPalette.columna
    DailyEnergyStatus.MAINTENANCE -> SymbolPalette.ok
    DailyEnergyStatus.SURPLUS -> WizardColors.danger
}
