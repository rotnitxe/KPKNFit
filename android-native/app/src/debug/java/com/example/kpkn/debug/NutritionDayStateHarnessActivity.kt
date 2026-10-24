package com.example.kpkn.debug

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.nutrition.expenditure.DayMark
import com.example.kpkn.screens.nutrition.daystate.*
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.theme.KPKNTheme
import java.time.LocalDate

/** Vista previa en memoria: state=unmarked|complete|partial|fasting|suggested, fontScale, reducedMotion y widthDp. */
class NutritionDayStateHarnessActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        val name = intent.getStringExtra("state") ?: "unmarked"
        val initial = NutritionDayState(
            date = LocalDate.of(2026, 10, 20),
            mark = when (name) {
                "complete" -> DayMark.COMPLETE_CONFIRMED
                "partial" -> DayMark.PARTIAL
                "fasting" -> DayMark.FASTING
                else -> DayMark.NONE
            }, hasMeals = name != "fasting", suggested = name == "suggested",
        )
        val scale = extra("fontScale")?.toFloatOrNull()?.takeIf { it.isFinite() }?.coerceIn(0.8f, 2f) ?: 1f
        val width = extra("widthDp")?.toIntOrNull()?.coerceIn(280, 1200) ?: 360
        val reduced = extra("reducedMotion")?.toBooleanStrictOrNull() ?: false
        setContent {
            KPKNTheme {
                val base = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(base.density, scale), LocalWizardReducedMotion provides reduced) {
                    var state by remember { mutableStateOf(initial) }
                    var sheet by remember { mutableStateOf(false) }
                    Box(Modifier.fillMaxSize().background(KpknSurface.canvas), contentAlignment = Alignment.TopCenter) {
                        Column(Modifier.widthIn(max = width.dp).fillMaxWidth().statusBarsPadding().padding(24.dp)) {
                            NutritionDayStateLine(state, { sheet = true },
                                { state = state.copy(mark = DayMark.PARTIAL, suggested = false) },
                                { state = state.copy(mark = DayMark.COMPLETE_CONFIRMED, suggested = false) })
                        }
                        if (sheet) NutritionDayStateSheet(state, {
                            state = state.copy(mark = it, suggested = false)
                            sheet = false
                        }, { sheet = false })
                    }
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun extra(key: String): String? = intent.extras?.get(key)?.toString()
}
