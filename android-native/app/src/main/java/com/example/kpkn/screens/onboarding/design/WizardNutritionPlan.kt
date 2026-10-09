package com.example.kpkn.screens.onboarding.design

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.PlanDirection
import com.example.kpkn.domain.nutrition.PlanMacro
import com.example.kpkn.domain.nutrition.PlanTuning
import com.example.kpkn.domain.nutrition.PlanValues
import com.example.kpkn.domain.nutrition.WizardPacePreset
import com.example.kpkn.domain.nutrition.goalDateSpanText
import com.example.kpkn.ui.theme.MacroColors

/**
 * Lo que dibuja el panel del plan de alimentación. Inmutable y sin Android: la parte viva (arrastres en
 * curso, escritura al borrador) la lleva quien lo compone.
 */
@Immutable
data class WizardNutritionPlanModel(
    /** El plan que se ve, con sus límites, ritmo y avisos derivados. */
    val tuning: PlanTuning,
    /** Días del reparto semanal; vacío o un solo día = sin franja. */
    val days: List<WizardNutritionDay> = emptyList(),
    /** Día elegido en la franja (anillos a sus objetivos); null = la media. */
    val selectedDay: Int? = null,
    /** false = solo lectura: deslizadores y ritmo apagados y sin «restablecer». */
    val editable: Boolean = true,
    /** La página está activa: los anillos barren desde 0 la primera vez que lo está. */
    val active: Boolean = true,
    /** Hay un arrastre en curso: la franja de días espera a que se suelte. */
    val dragging: Boolean = false,
    /** Texto de accesibilidad del botón de restablecer. */
    val resetLabel: String = "Restablecer a la recomendación",
    /** Meta de peso: con ella y una dirección que no sea Mantener, el panel añade «Hasta tu objetivo». */
    val goal: WizardNutritionGoal? = null,
)

/** Meta de peso del plan: el peso de hoy y el objetivo, en kg; [unit] es la del alta, para mostrarlos. */
@Immutable
data class WizardNutritionGoal(
    val currentKg: Double,
    val targetKg: Double,
    val unit: WizardMassUnit = WizardMassUnit.KG,
)

/** Acciones del panel. Las de arrastre llegan continuas; [onChangeFinished] al soltar. */
@Immutable
class WizardNutritionPlanCallbacks(
    val onProtein: (Int) -> Unit,
    val onCarbs: (Int) -> Unit,
    val onFat: (Int) -> Unit,
    val onPaceRate: (Double) -> Unit,
    val onPacePreset: (WizardPacePreset) -> Unit,
    val onChangeFinished: () -> Unit,
    val onReset: () -> Unit,
    val onSelectDay: (Int?) -> Unit,
)

/** Lado máximo de los anillos: 260 dp (en una pantalla más estrecha ocupan todo el ancho). */
private val WizardRingsMaxSize = 260.dp

/** Resumen para lectores de pantalla de los anillos: kcal y gramos (el día elegido, si lo hay). */
fun wizardNutritionRingsDescription(shown: PlanValues, day: WizardNutritionDay?): String {
    val prefix = day?.let { "${weekdayNameEs(it.date.dayOfWeek).replaceFirstChar { c -> c.uppercase() }}: " }.orEmpty()
    return prefix + "plan de ${formatKcalEs(shown.kcal)} kilocalorías al día: " +
        "${shown.proteinG} gramos de proteína, ${shown.carbsG} de hidratos y ${shown.fatG} de grasas"
}

/**
 * Panel «Tu plan de alimentación»: anillos con las kcal en grande, franja de días (solo con reparto
 * variable), avisos en vivo, control de ritmo y un deslizador por macro. Sin tarjetas: secciones separadas
 * por un filete, sobre la superficie negra del wizard.
 *
 * Sin estado propio salvo el de animación: todo lo que cambia entra por [model] y sale por [callbacks]. Se
 * dibuja dentro del `verticalScroll` de la página: no hay listas perezosas ni scroll anidado.
 */
@Composable
fun WizardNutritionPlanPanel(
    model: WizardNutritionPlanModel,
    callbacks: WizardNutritionPlanCallbacks,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = wizardReducedMotion()
    val tuning = model.tuning
    val day = model.selectedDay?.let { model.days.getOrNull(it) }
    val shown = day?.values ?: tuning.values
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(model.active) { if (model.active) appeared = true }
    val loss = (tuning.weeklyChangeKg ?: 0.0) < 0.0

    Column(modifier = modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth()) {
            WizardMacroRings(
                kcal = shown.kcal,
                fractions = macroRingFractions(tuning, shown),
                caption = day?.let { "kcal · ${weekdayNameEs(it.date.dayOfWeek)}" } ?: "kcal al día",
                description = wizardNutritionRingsDescription(shown, day),
                appeared = appeared,
                reducedMotion = reducedMotion,
                // El tope va ANTES de fillMaxWidth: al revés, fillMaxWidth fija el ancho de la página y el tope no actúa.
                modifier = Modifier
                    .align(Alignment.Center)
                    .widthIn(max = WizardRingsMaxSize)
                    .fillMaxWidth()
                    .aspectRatio(1f),
            )
            // Calificado: dentro del Column de arriba el AnimatedVisibility de ColumnScope no se puede llamar desde un Box.
            androidx.compose.animation.AnimatedVisibility(
                visible = model.editable && tuning.isEdited,
                modifier = Modifier.align(Alignment.TopEnd),
                enter = if (reducedMotion) EnterTransition.None else fadeIn(tween(durationMillis = 200)),
                exit = if (reducedMotion) ExitTransition.None else fadeOut(tween(durationMillis = 150)),
            ) {
                IconButton(
                    onClick = callbacks.onReset,
                    modifier = Modifier.testTag("setup-nutrition-reset"),
                ) {
                    Icon(
                        imageVector = Icons.Filled.RestartAlt,
                        contentDescription = model.resetLabel,
                        tint = WizardColors.textMuted,
                    )
                }
            }
        }

        if (model.days.size > 1) {
            Spacer(Modifier.height(8.dp))
            WizardDayStrip(
                days = model.days,
                selectedIndex = model.selectedDay,
                enabled = !model.dragging,
                onSelect = callbacks.onSelectDay,
            )
        }

        Spacer(Modifier.height(8.dp))
        WizardPlanWarnings(warnings = tuning.warnings, loss = loss, reducedMotion = reducedMotion)

        if (tuning.weeklyChangeKg != null) {
            Spacer(Modifier.height(10.dp))
            PanelDivider()
            Spacer(Modifier.height(16.dp))
            WizardPaceGauge(
                tuning = tuning,
                reducedMotion = reducedMotion,
                editable = model.editable,
                onRateChange = callbacks.onPaceRate,
                onRateChangeFinished = callbacks.onChangeFinished,
                onPreset = callbacks.onPacePreset,
            )
        }

        Spacer(Modifier.height(12.dp))
        PanelDivider()
        Spacer(Modifier.height(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            MacroRow(PlanMacro.PROTEIN, "Proteína", MacroColors.protein, "setup-nutrition-protein", model, callbacks)
            MacroRow(PlanMacro.CARBS, "Hidratos", MacroColors.carbs, "setup-nutrition-carbs", model, callbacks)
            MacroRow(PlanMacro.FAT, "Grasas", MacroColors.fat, "setup-nutrition-fat", model, callbacks)
        }

        // «Hasta tu objetivo»: solo con meta y una dirección que la lleve (Definir o Volumen), con el ritmo en vivo.
        val goal = model.goal
        val direction = tuning.context.direction
        val weeklyKg = tuning.weeklyChangeKg
        if (goal != null && weeklyKg != null && direction != null &&
            direction != PlanDirection.MAINTENANCE && direction != PlanDirection.PROFESSIONAL
        ) {
            Spacer(Modifier.height(16.dp))
            PanelDivider()
            Spacer(Modifier.height(16.dp))
            WizardGoalSection(goal = goal, weeklyKg = weeklyKg)
        }
    }
}

/** Sección «Hasta tu objetivo»: curva a ritmo del plan y fecha estimada con el mismo ±15 %. Sin caja, con su título. */
@Composable
private fun WizardGoalSection(goal: WizardNutritionGoal, weeklyKg: Double) {
    val span = remember(goal.currentKg, goal.targetKg, weeklyKg) {
        goalDateSpanText(goal.currentKg, goal.targetKg, weeklyKg)
    }
    Column(modifier = Modifier.fillMaxWidth().testTag("setup-nutrition-goal")) {
        Text(
            text = "Hasta tu objetivo",
            style = WizardTypography.controlLabel,
            color = WizardColors.text,
        )
        Spacer(Modifier.height(8.dp))
        WizardGoalProjection(
            currentKg = goal.currentKg,
            targetKg = goal.targetKg,
            weeklyChangeKg = weeklyKg,
            unit = goal.unit,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (span != null) "Fecha estimada: $span" else "Con este ritmo no llegas a tu objetivo.",
            style = WizardTypography.note,
            color = WizardColors.textMuted,
        )
    }
}

@Composable
private fun MacroRow(
    macro: PlanMacro,
    label: String,
    color: Color,
    tag: String,
    model: WizardNutritionPlanModel,
    callbacks: WizardNutritionPlanCallbacks,
) {
    val tuning = model.tuning
    val values = tuning.values
    val weight = tuning.context.knownWeightKg
    val grams = values.gramsOf(macro)
    val perKg = if (weight != null && macro != PlanMacro.CARBS) {
        "${formatDecimalEs(grams / weight, 1)} g/kg"
    } else {
        null
    }
    WizardMacroSlider(
        label = label,
        color = color,
        grams = grams,
        range = tuning.limits.of(macro),
        reachableMaxG = tuning.reachableMaxOf(macro),
        baselineG = tuning.baseline.gramsOf(macro),
        kcal = when (macro) {
            PlanMacro.PROTEIN -> tuning.proteinKcal
            PlanMacro.CARBS -> tuning.carbsKcal
            PlanMacro.FAT -> tuning.fatKcal
        },
        percent = when (macro) {
            PlanMacro.PROTEIN -> tuning.proteinPercent
            PlanMacro.CARBS -> tuning.carbsPercent
            PlanMacro.FAT -> tuning.fatPercent
        },
        perKgText = perKg,
        enabled = model.editable,
        onGramsChange = when (macro) {
            PlanMacro.PROTEIN -> callbacks.onProtein
            PlanMacro.CARBS -> callbacks.onCarbs
            PlanMacro.FAT -> callbacks.onFat
        },
        onGramsChangeFinished = callbacks.onChangeFinished,
        testTag = tag,
    )
}

/** Filete entre secciones (el mismo del resto de la página larga). */
@Composable
private fun PanelDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(WizardColors.divider),
    )
}
