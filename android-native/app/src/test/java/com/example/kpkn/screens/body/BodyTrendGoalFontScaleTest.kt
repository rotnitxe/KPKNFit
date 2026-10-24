package com.example.kpkn.screens.body

import android.app.Application
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.body.design.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=Application::class,qualifiers="w360dp-h1000dp-mdpi")
class BodyTrendGoalFontScaleTest {
    @get:Rule val rule=createComposeRule()
    @Test fun meta76NoQuedaAtravesadaPorLaLineaConFuente130() {
        val points=listOf(BodyChartPoint(LocalDate.of(2026,10,1),78.0,78.0),BodyChartPoint(LocalDate.of(2026,10,2),74.0,74.0))
        var density=1f
        rule.setContent {
            val current=LocalDensity.current;density=current.density
            CompositionLocalProvider(LocalDensity provides Density(current.density,1.3f)) {
                BodyTrendChart(points,76.0,"kg",Modifier.width(360.dp),includeGoal=true)
            }
        }
        rule.waitForIdle()
        val plot=rule.onNodeWithTag("body-trend-plot",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        val label=rule.onNodeWithTag("body-trend-goal-label",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        val geometry=bodyChartGeometry(points,76.0,plot.width,plot.height,8f*density,true)!!
        val lineY=plot.top+geometry.goalY!!
        assertTrue("Meta debe quedar separada de la línea: $label frente a $lineY",label.bottom<=lineY-density || label.top>=lineY+density)
    }
}
