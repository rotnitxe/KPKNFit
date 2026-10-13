package com.example.kpkn.screens.nutrition.design.editor

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.nutrition.NutritionEditorDayTarget
import com.example.kpkn.domain.nutrition.NutritionWeeklyDistributionMode
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.onboarding.design.formatKcalEs
import com.example.kpkn.screens.onboarding.design.weekdayInitialEs
import com.example.kpkn.screens.onboarding.design.weekdayNameEs
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknModuleHeader
import com.example.kpkn.ui.design.KpknSegmentedControl
import com.example.kpkn.ui.design.KpknSegmentedSize
import com.example.kpkn.ui.design.KpknType

/** Reparto de la base en la semana: según el gasto previsto, o uniforme. */
private val WeekOptions = listOf("Según el gasto previsto", "Uniforme")
private val WeekModes = listOf(NutritionWeeklyDistributionMode.VARIABLE, NutritionWeeklyDistributionMode.UNIFORM)

/** Alto del gráfico de barras: la barra del día más alto llega hasta arriba. */
private val BarsHeight = 96.dp

/** Ancho de cada barra. */
private val BarWidth = 14.dp

/** Área táctil de cada día: 48 dp centrados en su columna, aunque la columna sea más estrecha. */
private val TouchWidth = 48.dp

/**
 * Módulo «Reparto semanal»: el modo del reparto y un gráfico de barras con los siete días. Tocar una barra elige el día, y debajo queda su
 * línea con las kcal y los macros; sin base revisada, lo dice. Sin estado: el día elegido llega por [selectedDay].
 */
@Composable
fun EditorWeekModule(
    weeklyDistribution: NutritionWeeklyDistributionMode,
    weeklyTargets: List<NutritionEditorDayTarget>,
    selectedDay: Int?,
    onWeeklyDistribution: (NutritionWeeklyDistributionMode) -> Unit,
    onSelectDay: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduced = wizardReducedMotion()
    KpknModule(modifier = modifier) {
        KpknModuleHeader(label = "Reparto semanal")
        Spacer(Modifier.height(12.dp))
        KpknSegmentedControl(
            options = WeekOptions,
            selectedIndex = WeekModes.indexOf(weeklyDistribution),
            onSelect = { onWeeklyDistribution(WeekModes[it]) },
            modifier = Modifier.fillMaxWidth(),
            size = KpknSegmentedSize.Compact,
        )
        Spacer(Modifier.height(16.dp))
        if (weeklyTargets.isEmpty()) {
            EditorCaption("Sin base revisada aún no hay reparto que mostrar.")
        } else {
            val chosen = selectedDay?.takeIf { it in weeklyTargets.indices }
            EditorWeekBars(targets = weeklyTargets, selectedIndex = chosen, onSelect = onSelectDay)
            Spacer(Modifier.height(12.dp))
            AnimatedContent(
                targetState = chosen,
                transitionSpec = {
                    if (reduced) {
                        EnterTransition.None togetherWith ExitTransition.None
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
 * Los siete días como barras, en un lienzo propio de 96 dp: la altura es proporcional a sus kcal, con un mínimo del 30 %. El día elegido va
 * en tinta y con su cifra encima; el resto, en energía al 70 %. Cada día tiene un área táctil de 48 dp centrada en su columna y su
 * descripción. Con movimiento reducido no hay animación. Sin estado.
 */
@Composable
private fun EditorWeekBars(
    targets: List<NutritionEditorDayTarget>,
    selectedIndex: Int?,
    onSelect: (Int?) -> Unit,
) {
    val fractions = editorBarFractions(targets.map { it.calorieTargetKcal })
    Column(modifier = Modifier.fillMaxWidth()) {
        // La cifra del día elegido, encima de su barra.
        Row(modifier = Modifier.fillMaxWidth()) {
            targets.forEachIndexed { index, day ->
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    if (index == selectedIndex) {
                        Text(
                            text = formatKcalEs(day.calorieTargetKcal),
                            style = KpknType.caption.copy(fontFeatureSettings = "'tnum' 1"),
                            color = WizardColors.text,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.wrapContentWidth(unbounded = true),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(BarsHeight)) {
            val column = maxWidth / targets.size
            Canvas(modifier = Modifier.fillMaxSize()) {
                val barWidth = BarWidth.toPx()
                val bottom = size.height
                fractions.forEachIndexed { index, fraction ->
                    val centerX = (index + 0.5f) * size.width / targets.size
                    val barHeight = bottom * fraction
                    drawRoundRect(
                        color = if (index == selectedIndex) WizardColors.text else SymbolPalette.energia.copy(alpha = 0.7f),
                        topLeft = Offset(centerX - barWidth / 2f, bottom - barHeight),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(barWidth / 2f),
                    )
                }
            }
            targets.forEachIndexed { index, day ->
                val selected = index == selectedIndex
                val interaction = remember { MutableInteractionSource() }
                val name = weekdayNameEs(day.date.dayOfWeek)
                Box(
                    modifier = Modifier
                        .offset(x = column * index + column / 2 - TouchWidth / 2)
                        .size(width = TouchWidth, height = BarsHeight)
                        .selectable(
                            selected = selected,
                            interactionSource = interaction,
                            indication = null,
                            role = Role.Tab,
                            onClick = { onSelect(if (selected) null else index) },
                        )
                        .semantics {
                            contentDescription = "$name, ${formatKcalEs(day.calorieTargetKcal)} kilocalorías"
                        },
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            targets.forEachIndexed { index, day ->
                Text(
                    text = weekdayInitialEs(day.date.dayOfWeek),
                    style = KpknType.caption,
                    color = if (index == selectedIndex) WizardColors.text else WizardColors.textMuted,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
