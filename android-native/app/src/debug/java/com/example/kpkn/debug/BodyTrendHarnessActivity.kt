package com.example.kpkn.debug

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.BodyChartRange
import com.example.kpkn.data.models.BodyGoal
import com.example.kpkn.data.models.BodyMetric
import com.example.kpkn.data.models.BodyObservation
import com.example.kpkn.data.models.GoalMetric
import com.example.kpkn.domain.body.ResolvedBodyGoal
import com.example.kpkn.domain.body.bodyDailySeries
import com.example.kpkn.domain.body.ewmaTrend
import com.example.kpkn.domain.body.observationsInLastDays
import com.example.kpkn.domain.body.summarizeWeight
import com.example.kpkn.screens.body.design.BodyWeightModule
import com.example.kpkn.screens.body.design.buildBodyChartPoints
import com.example.kpkn.screens.home.design.HomeBodyToday
import com.example.kpkn.screens.home.homeBodyState
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.screens.onboarding.design.WizardMassUnit
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.theme.KPKNTheme
import java.time.LocalDate
import java.time.ZoneId

/**
 * Vista previa en memoria de la tendencia del peso (módulo «Peso» de Cuerpo y tarjeta «Cuerpo» de Inicio), con pesajes sintéticos que
 * pasan por las funciones reales del dominio; no escribe nada en la base. Extras: state=trend|single_weighin|outlier, fontScale,
 * reducedMotion, widthDp y unit=kg|lb.
 */
class BodyTrendHarnessActivity : ComponentActivity() {

    /** Ruido diario de ±0,3 kg, determinista. */
    private val noise = doubleArrayOf(
        0.2, -0.3, 0.1, 0.3, -0.2, 0.0, 0.25, -0.15, 0.1, -0.3, 0.2, 0.05, -0.25, 0.3,
        -0.1, 0.15, -0.2, 0.0, 0.3, -0.25, 0.1, -0.05, 0.2, -0.3, 0.15, 0.0, -0.1, 0.25,
    )

    private fun weight(id: String, kg: Double, day: LocalDate, zone: ZoneId) = BodyObservation(
        id = id,
        metric = BodyMetric.WEIGHT,
        valueSi = kg,
        unitSi = "kg",
        timestampEpochMs = day.atTime(12, 0).atZone(zone).toInstant().toEpochMilli(),
        zoneId = zone.id,
    )

    /** 28 pesajes diarios de 82 kg bajando 0,5 kg por semana; [spikeKg] se suma al pesaje de hoy. */
    private fun descent(today: LocalDate, zone: ZoneId, spikeKg: Double): List<BodyObservation> = (0 until 28).map { d ->
        val kg = 82.0 - 0.5 / 7.0 * d + noise[d] + if (d == 27) spikeKg else 0.0
        weight("h$d", kg, today.minusDays(27L - d), zone)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val observations = when (extra("state")) {
            "single_weighin" -> listOf(weight("h0", 78.4, today, zone))
            "outlier" -> descent(today, zone, spikeKg = 2.0)
            else -> descent(today, zone, spikeKg = 0.0)
        }
        val goalKg = 76.0
        val range = BodyChartRange.THREE_MONTHS
        val summary = summarizeWeight(observations, range, ResolvedBodyGoal(GoalMetric.WEIGHT, goalKg), zone, today)
        val series = bodyDailySeries(observationsInLastDays(observations, BodyMetric.WEIGHT, range.days, zone, today), zone)
        val points = buildBodyChartPoints(series, ewmaTrend(series))
        val goal = BodyGoal(
            id = "harness-goal",
            metric = BodyMetric.WEIGHT,
            targetValueSi = goalKg,
            unitSi = "kg",
            createdAtEpochMs = 0L,
            updatedAtEpochMs = 0L,
        )
        val home = homeBodyState(observations, listOf(goal), 175.0, today, zone)
        val scale = extra("fontScale")?.toFloatOrNull()?.takeIf { it.isFinite() }?.coerceIn(0.8f, 2f) ?: 1f
        val width = extra("widthDp")?.toIntOrNull()?.coerceIn(280, 1200) ?: 360
        val reduced = extra("reducedMotion")?.toBooleanStrictOrNull() ?: false
        val unit = if (extra("unit") == "lb") WizardMassUnit.LB else WizardMassUnit.KG
        setContent {
            KPKNTheme {
                val base = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(base.density, scale),
                    LocalWizardReducedMotion provides reduced,
                ) {
                    var rangeChoice by remember { mutableStateOf(range) }
                    Box(Modifier.fillMaxSize().background(KpknSurface.canvas), contentAlignment = Alignment.TopCenter) {
                        Column(
                            modifier = Modifier
                                .widthIn(max = width.dp)
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .verticalScroll(rememberScrollState())
                                .padding(vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            BodyWeightModule(
                                summary = summary,
                                goalKg = goalKg,
                                points = points,
                                range = rangeChoice,
                                today = today,
                                weightUnit = unit,
                                onRangeChange = { rangeChoice = it },
                                onAdd = {},
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                            HomeBodyToday(state = home, onOpen = {})
                        }
                    }
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun extra(key: String): String? = intent.extras?.get(key)?.toString()
}
