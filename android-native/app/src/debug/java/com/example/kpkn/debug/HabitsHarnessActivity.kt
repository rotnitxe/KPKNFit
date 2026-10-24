package com.example.kpkn.debug

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
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
import com.example.kpkn.domain.habits.HabitKind
import com.example.kpkn.domain.habits.HabitsSnapshot
import com.example.kpkn.domain.habits.buildHabitsSnapshot
import com.example.kpkn.screens.habits.HabitsScreen
import com.example.kpkn.screens.habits.HabitsUiState
import com.example.kpkn.screens.onboarding.design.LocalWizardReducedMotion
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.theme.KPKNTheme
import java.time.LocalDate

/**
 * Vista previa de Hábitos con datos en memoria: state=empty|streaks|long_gap, fontScale (0,8 a 2), reducedMotion y widthDp.
 * SOLO DEPURACIÓN: no escribe en ningún repositorio ni se integra en main.
 */
class HabitsHarnessActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        val name = extra("state") ?: "streaks"
        val scale = extra("fontScale")?.toFloatOrNull()?.takeIf { it.isFinite() }?.coerceIn(0.8f, 2f) ?: 1f
        val width = extra("widthDp")?.toIntOrNull()?.coerceIn(280, 1200) ?: 360
        val reduced = extra("reducedMotion")?.toBooleanStrictOrNull() ?: false
        val snapshot = harnessSnapshot(name, LocalDate.now())
        setContent {
            KPKNTheme {
                val base = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(base.density, scale),
                    LocalWizardReducedMotion provides reduced,
                ) {
                    var selected by remember { mutableStateOf(HabitKind.MEALS) }
                    Box(Modifier.fillMaxSize().background(KpknSurface.canvas), contentAlignment = Alignment.TopCenter) {
                        HabitsScreen(
                            state = HabitsUiState(selected = selected, snapshot = snapshot),
                            onSelectHabit = { selected = it },
                            onBack = {},
                            modifier = Modifier.widthIn(max = width.dp).fillMaxSize(),
                        )
                    }
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun extra(key: String): String? = intent.extras?.get(key)?.toString()
}

/** Datos inventados solo para ver la pantalla: nada de esto llega a la base ni a un repositorio. */
private fun harnessSnapshot(name: String, today: LocalDate): HabitsSnapshot {
    fun days(range: LongRange): Set<LocalDate> = range.map { today.minusDays(it) }.toSet()
    return when (name) {
        "empty" -> buildHabitsSnapshot(emptySet(), emptySet(), emptyMap(), today)
        "long_gap" -> buildHabitsSnapshot(
            mealDays = days(1L..3L) + days(200L..210L),
            weighInDays = days(0L..0L) + days(120L..120L),
            trainingSets = mapOf(today.minusDays(50L) to 12, today.minusDays(51L) to 12),
            today = today,
        )
        else -> buildHabitsSnapshot(
            mealDays = days(1L..5L) + days(10L..22L) + days(30L..33L),
            weighInDays = days(0L..12L) + days(20L..20L),
            trainingSets = (0L..7L).flatMap { week ->
                listOf(
                    today.minusDays(7L * week + 1L) to if (week % 2L == 0L) 16 else 9,
                    today.minusDays(7L * week + 4L) to 10,
                )
            }.toMap(),
            today = today,
        )
    }
}
