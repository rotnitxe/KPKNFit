package com.example.kpkn.screens.nutrition.design.editor

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.nutrition.NutritionEditorDayTarget
import com.example.kpkn.domain.nutrition.NutritionWeeklyDistributionMode
import com.example.kpkn.screens.onboarding.design.WizardDayStrip
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknModuleHeader
import com.example.kpkn.ui.design.KpknRadioRow
import com.example.kpkn.ui.design.KpknSegmentedControl
import com.example.kpkn.ui.design.KpknSegmentedSize
import java.time.LocalDate

/** Reparto de la base en la semana: según el gasto previsto, o uniforme. */
private val WeekOptions = listOf("Según el gasto previsto", "Uniforme")
private val WeekModes = listOf(NutritionWeeklyDistributionMode.VARIABLE, NutritionWeeklyDistributionMode.UNIFORM)

/**
 * Módulo «Reparto semanal»: el modo del reparto y la franja de los días, con las kcal de cada uno. Tocar un día muestra debajo sus macros
 * en una línea; sin base revisada, lo dice. Sin estado: el día elegido llega por [selectedDay].
 */
@Composable
fun EditorWeekModule(
    weeklyDistribution: NutritionWeeklyDistributionMode,
    weeklyTargets: List<NutritionEditorDayTarget>,
    selectedDay: Int?,
    today: LocalDate,
    onWeeklyDistribution: (NutritionWeeklyDistributionMode) -> Unit,
    onSelectDay: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduced = wizardReducedMotion()
    KpknModule(modifier = modifier) {
        KpknModuleHeader(label = "Reparto semanal")
        Spacer(Modifier.height(12.dp))
        if (LocalDensity.current.fontScale >= EditorLargeFontScale) {
            // Al 130 % de letra, los dos segmentos cortarían «Según el gasto previsto»: cada opción va en su fila.
            KpknRadioRow(
                selected = weeklyDistribution == NutritionWeeklyDistributionMode.VARIABLE,
                title = "Según el gasto previsto",
                onClick = { onWeeklyDistribution(NutritionWeeklyDistributionMode.VARIABLE) },
            )
            KpknRadioRow(
                selected = weeklyDistribution == NutritionWeeklyDistributionMode.UNIFORM,
                title = "Uniforme",
                onClick = { onWeeklyDistribution(NutritionWeeklyDistributionMode.UNIFORM) },
            )
        } else {
            KpknSegmentedControl(
                options = WeekOptions,
                selectedIndex = WeekModes.indexOf(weeklyDistribution),
                onSelect = { onWeeklyDistribution(WeekModes[it]) },
                modifier = Modifier.fillMaxWidth(),
                size = KpknSegmentedSize.Compact,
            )
        }
        Spacer(Modifier.height(16.dp))
        if (weeklyTargets.isEmpty()) {
            EditorCaption("Sin base revisada aún no hay reparto que mostrar.")
        } else {
            val chosen = selectedDay?.takeIf { it in weeklyTargets.indices }
            WizardDayStrip(
                days = editorWeekDays(weeklyTargets, today),
                selectedIndex = chosen,
                enabled = true,
                onSelect = onSelectDay,
            )
            Spacer(Modifier.height(12.dp))
            AnimatedContent(
                targetState = chosen,
                transitionSpec = {
                    if (reduced) {
                        fadeIn(animationSpec = tween(durationMillis = 120)) togetherWith
                            fadeOut(animationSpec = tween(durationMillis = 120))
                    } else {
                        fadeIn(animationSpec = tween(durationMillis = 220)) togetherWith
                            fadeOut(animationSpec = tween(durationMillis = 220))
                    }
                },
                label = "editorWeekDayLine",
            ) { index ->
                EditorCaption(
                    text = index?.let { editorDayLine(weeklyTargets[it]) } ?: "Toca un día para ver sus macros.",
                    numeric = index != null,
                )
            }
        }
    }
}
