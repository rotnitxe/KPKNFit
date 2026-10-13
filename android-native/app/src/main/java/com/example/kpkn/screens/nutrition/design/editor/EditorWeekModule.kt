package com.example.kpkn.screens.nutrition.design.editor

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.formatKcalEs
import com.example.kpkn.screens.onboarding.design.weekdayNameEs
import com.example.kpkn.ui.design.KpknShape
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknType
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
            if (LocalDensity.current.fontScale >= EditorLargeFontScale) {
                // Al 130 % de letra, las siete columnas de la franja cortan las kcal: los días pasan a filas.
                EditorDayList(days = weeklyTargets, selectedIndex = chosen, today = today, onSelect = onSelectDay)
            } else {
                WizardDayStrip(
                    days = editorWeekDays(weeklyTargets, today),
                    selectedIndex = chosen,
                    enabled = true,
                    onSelect = onSelectDay,
                )
            }
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

/**
 * Los días en filas para la letra grande: la franja de siete columnas corta las kcal al 130 %. Cada fila lleva el nombre del día (con
 * «hoy» si lo es) y sus kcal; tocarla la elige y, otra vez, la suelta. Sin estado.
 */
@Composable
private fun EditorDayList(
    days: List<NutritionEditorDayTarget>,
    selectedIndex: Int?,
    today: LocalDate,
    onSelect: (Int?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        days.forEachIndexed { index, day ->
            val selected = index == selectedIndex
            val interaction = remember { MutableInteractionSource() }
            val name = weekdayNameEs(day.date.dayOfWeek).replaceFirstChar { it.uppercaseChar() }
            val ink = if (selected) WizardColors.text else WizardColors.textMuted
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
                    .background(if (selected) KpknSurface.inset else Color.Transparent, KpknShape.pill)
                    .selectable(
                        selected = selected,
                        interactionSource = interaction,
                        indication = null,
                        role = Role.Tab,
                        onClick = { onSelect(if (selected) null else index) },
                    )
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (day.date == today) "$name · hoy" else name,
                    style = KpknType.bodyStrong,
                    color = ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${formatKcalEs(day.calorieTargetKcal)} kcal",
                    style = KpknType.caption.copy(fontFeatureSettings = "'tnum' 1"),
                    color = ink,
                    maxLines = 1,
                )
            }
        }
    }
}
