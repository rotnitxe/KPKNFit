package com.example.kpkn.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
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
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * HOJA DE DIBUJO de la tendencia del peso (no comprueba nada: escribe PNG). El módulo «Peso» de Cuerpo y la tarjeta «Cuerpo» de Inicio,
 * con pesajes sintéticos que pasan por las funciones reales del dominio, en tres casos (`trend`, `single_weighin`, `outlier`), a 360 dp,
 * con la letra al 100 % y al 130 %, sin movimiento. Solo corre con `KPKN_RENDER_DIR`; sin ella se salta.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h2400dp-xxhdpi", application = android.app.Application::class)
class BodyTrendRenderSheetTest {

    companion object {
        /** Sin `KPKN_RENDER_DIR` la clase se salta antes de arrancar Robolectric con gráficos nativos. */
        @JvmStatic
        @BeforeClass
        fun onlyWhenAskedFor() {
            assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)
        }
    }

    @get:Rule
    val rule = createComposeRule()

    private fun onlyWhenAsked() = assumeTrue("sin KPKN_RENDER_DIR no se dibuja nada", System.getenv("KPKN_RENDER_DIR") != null)

    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 10, 13)
    private val goalKg = 76.0

    private var caseId by mutableStateOf("trend")
    private var fontScale by mutableStateOf(1f)
    private var hostView: View? = null

    private val noise = doubleArrayOf(
        0.2, -0.3, 0.1, 0.3, -0.2, 0.0, 0.25, -0.15, 0.1, -0.3, 0.2, 0.05, -0.25, 0.3,
        -0.1, 0.15, -0.2, 0.0, 0.3, -0.25, 0.1, -0.05, 0.2, -0.3, 0.15, 0.0, -0.1, 0.25,
    )

    private fun weight(id: String, kg: Double, day: LocalDate) = BodyObservation(
        id = id,
        metric = BodyMetric.WEIGHT,
        valueSi = kg,
        unitSi = "kg",
        timestampEpochMs = day.atTime(12, 0).atZone(zone).toInstant().toEpochMilli(),
        zoneId = zone.id,
    )

    private fun descent(spikeKg: Double): List<BodyObservation> = (0 until 28).map { d ->
        weight("r$d", 82.0 - 0.5 / 7.0 * d + noise[d] + if (d == 27) spikeKg else 0.0, today.minusDays(27L - d))
    }

    private fun observationsOf(case: String): List<BodyObservation> = when (case) {
        "single_weighin" -> listOf(weight("r0", 78.4, today))
        "outlier" -> descent(2.0)
        else -> descent(0.0)
    }

    private fun save(name: String) {
        val dir = File(System.getenv("KPKN_RENDER_DIR")!!)
        dir.mkdirs()
        val bounds = rule.onNodeWithTag("sheet").fetchSemanticsNode().boundsInRoot
        val view = hostView!!
        val full = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        rule.runOnUiThread {
            val canvas = Canvas(full)
            canvas.drawColor(android.graphics.Color.BLACK)
            view.draw(canvas)
        }
        val left = bounds.left.toInt().coerceIn(0, full.width - 1)
        val top = bounds.top.toInt().coerceIn(0, full.height - 1)
        val width = bounds.width.toInt().coerceIn(1, full.width - left)
        val height = bounds.height.toInt().coerceIn(1, full.height - top)
        val cropped = Bitmap.createBitmap(full, left, top, width, height)
        FileOutputStream(File(dir, "$name.png")).use { cropped.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun renderTrendSheets() {
        onlyWhenAsked()
        rule.setContent {
            val base = LocalDensity.current
            hostView = LocalView.current
            val observations = observationsOf(caseId)
            val range = BodyChartRange.THREE_MONTHS
            val summary = summarizeWeight(observations, range, ResolvedBodyGoal(GoalMetric.WEIGHT, goalKg), zone, today)
            val series = bodyDailySeries(observationsInLastDays(observations, BodyMetric.WEIGHT, range.days, zone, today), zone)
            val goal = BodyGoal("g", BodyMetric.WEIGHT, goalKg, "kg", createdAtEpochMs = 0L, updatedAtEpochMs = 0L)
            val home = homeBodyState(observations, listOf(goal), 175.0, today, zone)
            CompositionLocalProvider(
                LocalDensity provides Density(base.density, fontScale = fontScale),
                LocalWizardReducedMotion provides true,
            ) {
                Column(Modifier.testTag("sheet").width(360.dp).background(Color.Black)) {
                    BodyWeightModule(
                        summary = summary,
                        goalKg = goalKg,
                        points = buildBodyChartPoints(series, ewmaTrend(series)),
                        range = range,
                        today = today,
                        weightUnit = WizardMassUnit.KG,
                        onRangeChange = {},
                        onAdd = {},
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    HomeBodyToday(state = home, onOpen = {})
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
        for (id in listOf("trend", "single_weighin", "outlier")) {
            for ((scale, suffix) in listOf(1f to "s100", 1.3f to "s130")) {
                rule.runOnUiThread {
                    caseId = id
                    fontScale = scale
                }
                rule.waitForIdle()
                save("tendencia_${id}_$suffix")
            }
        }
    }
}
