package com.example.kpkn.screens.body.design

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.body.BodyMetricPoint
import com.example.kpkn.screens.onboarding.design.formatDecimalEs
import com.example.kpkn.ui.design.KpknMetric
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknModuleHeader
import com.example.kpkn.ui.design.KpknType
import java.time.YearMonth
import kotlin.math.abs

/**
 * Módulo «Grasa corporal» (BODY_V1 §2.3): la cifra en %, su cambio desde el primer punto del rango, la misma gráfica y, si el objetivo
 * es de grasa, su avance. Sin [current] no se pinta nada. El rango lo elige el módulo de peso. Sin estado propio.
 *
 * @param start primer punto de la serie del rango, de donde sale el cambio.
 * @param goalPercent meta de % de grasa, o null si el objetivo no es de grasa.
 * @param progressPercent avance hacia la meta (0 a 100), o null si no hay línea de base.
 * @param eta mes estimado para la meta, solo con ritmo medido hacia ella.
 */
@Composable
fun BodyFatModule(
    current: Double?,
    start: BodyMetricPoint?,
    points: List<BodyChartPoint>,
    goalPercent: Double?,
    progressPercent: Int?,
    eta: YearMonth?,
    modifier: Modifier = Modifier,
) {
    if (current == null) return
    val remaining = goalPercent?.let { it - current }
    KpknModule(modifier = modifier.fillMaxWidth()) {
        KpknModuleHeader(label = "Grasa corporal")
        Spacer(Modifier.height(10.dp))
        KpknMetric(value = formatDecimalEs(current, 1), unit = "%", style = KpknType.metricL)
        if (start != null) {
            val change = current - start.value
            Spacer(Modifier.height(8.dp))
            BodyChangeLine(
                change = change,
                unit = "puntos",
                since = "desde el ${bodyDayMonthEs(start.date)}",
                tone = bodyChangeTone(change, remaining),
            )
        }
        Spacer(Modifier.height(16.dp))
        BodyTrendChart(
            points = points,
            goal = goalPercent,
            unit = "%",
            name = "grasa corporal",
            includeGoal = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (goalPercent != null) {
            val gap = goalPercent - current
            Spacer(Modifier.height(16.dp))
            val reached = progressPercent == 100 || abs(gap) < BODY_FLAT_CHANGE
            BodyGoalBlock(
                headline = if (reached) "Meta alcanzada" else "Te faltan ${formatDecimalEs(abs(gap), 1)} puntos",
                detail = "Meta: ${bodyTargetEs(goalPercent)} % de grasa · ${bodyEtaText(eta)}",
                progress = progressPercent?.let { it / 100f },
            )
        }
    }
}
