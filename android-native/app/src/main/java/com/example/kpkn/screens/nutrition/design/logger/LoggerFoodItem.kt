package com.example.kpkn.screens.nutrition.design.logger

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.FoodCandidate
import com.example.kpkn.data.models.PortionPreset
import com.example.kpkn.domain.nutrition.ClarificationRequest
import com.example.kpkn.domain.nutrition.CookingStateResolver
import com.example.kpkn.domain.nutrition.PortionOption
import com.example.kpkn.domain.nutrition.ResolvedTag
import com.example.kpkn.screens.nutrition.components.GramsEditor
import com.example.kpkn.screens.nutrition.components.foodClarificationPrompt
import com.example.kpkn.screens.nutrition.components.portionPresetLabel
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknIconButton
import com.example.kpkn.ui.design.KpknMetric
import com.example.kpkn.ui.design.KpknSegmentedControl
import com.example.kpkn.ui.design.KpknSegmentedSize
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknType

/** Macro que se ajusta a mano en una fila de alimento. */
enum class LoggerMacro { CALORIES, PROTEIN, CARBS, FATS }

/**
 * Acciones de una fila de alimento. La hoja las resuelve: cambian el borrador, nunca la fila por sí sola.
 */
data class LoggerFoodActions(
    val onToggleExpanded: () -> Unit,
    val onToggleCorrection: () -> Unit,
    val onCorrectionQueryChange: (String) -> Unit,
    val onPickCorrection: (FoodCandidate) -> Unit,
    val onPortionChange: (PortionPreset) -> Unit,
    val onPortionOption: (PortionOption) -> Unit,
    val onGramsChange: (Double) -> Unit,
    val onMacroChange: (LoggerMacro, Double) -> Unit,
    val onRemove: () -> Unit,
    val onOilLevelChange: (String) -> Unit,
    val onCookingChange: (Boolean) -> Unit,
    val onIdentityChoice: (String) -> Unit,
    val onDeclaredComposition: (String) -> Unit,
    val onUnsure: () -> Unit,
)

/**
 * Fila de un alimento interpretado (N3b, NUTRITION_V1 §3): nombre en `bodyStrong` y kcal a la derecha, con «≈» si es una estimación.
 * Debajo, los macros en una línea y la porción en `KpknSegmentedControl`. La aclaración pendiente, «Corregir» y «Quitar» van a la vista;
 * «Ajustar» despliega los gramos, los ± de macros y la ficha. Sin estado: [correcting] y la búsqueda de corrección los guarda la hoja.
 */
@Composable
fun LoggerFoodItem(
    tag: ResolvedTag,
    correcting: Boolean,
    correctionQuery: String,
    correctionResults: List<FoodCandidate>?,
    pendingQuestion: ClarificationRequest?,
    matchUncertain: Boolean,
    actions: LoggerFoodActions,
    modifier: Modifier = Modifier,
) {
    val logged = tag.loggedFood
    val name = tag.tag.trim().replaceFirstChar { it.uppercase() }
    val uncertain = tag.isUncertain || logged?.isUncertain == true
    val kcalValue = if (logged == null) {
        "—"
    } else {
        (if (uncertain) "≈ " else "") + kotlin.math.round(logged.calories).toInt().toString()
    }
    val portions = PortionPreset.entries

    Column(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (tag.isExcluded) 0.5f else 1f)
            .padding(vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = name,
                    style = KpknType.bodyStrong,
                    color = WizardColors.text,
                    textDecoration = if (tag.isExcluded) TextDecoration.LineThrough else null,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (tag.isExcluded) {
                    Text(text = "Excluido", style = KpknType.caption, color = WizardColors.danger)
                }
                identityLine(tag)?.let { line ->
                    Text(
                        text = line,
                        style = KpknType.caption,
                        color = WizardColors.textMuted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            KpknMetric(value = kcalValue, unit = "kcal", style = KpknType.metricM)
        }

        if (logged != null) {
            LoggerMacroLine(
                protein = kotlin.math.round(logged.protein).toInt(),
                carbs = kotlin.math.round(logged.carbs).toInt(),
                fats = kotlin.math.round(logged.fats).toInt(),
            )
            val minKcal = logged.caloriesMin?.let { kotlin.math.round(it).toInt() }
            val maxKcal = logged.caloriesMax?.let { kotlin.math.round(it).toInt() }
            if (uncertain && minKcal != null && maxKcal != null) {
                Text(
                    text = "Rango estimado: $minKcal–$maxKcal kcal",
                    style = KpknType.caption,
                    color = WizardColors.textFaint,
                )
            }
        }

        if (logged?.recipeId == null) KpknSegmentedControl(
            options = portions.map { portionPresetLabel(it) },
            selectedIndex = portions.indexOf(tag.portion).coerceAtLeast(0),
            onSelect = { index -> actions.onPortionChange(portions[index]) },
            size = KpknSegmentedSize.Compact,
        )

        pendingQuestion?.let { question ->
            val prompt = foodClarificationPrompt(
                question = question,
                matchedName = tag.interpretationV2?.canonicalIdentity ?: tag.foodItem?.name,
                unresolvedDeclaredAmount = tag.unresolvedDeclaredAmount,
                dryVsCooked = tag.clarificationKind == CookingStateResolver.ClarificationKind.DRY_VS_COOKED,
            )
            LoggerFoodClarification(
                question = question,
                prompt = prompt,
                dryVsCooked = tag.clarificationKind == CookingStateResolver.ClarificationKind.DRY_VS_COOKED,
                unsureEnabled = logged != null && !tag.unresolvedDeclaredAmount,
                onReviewAmount = { if (!tag.isExpanded) actions.onToggleExpanded() },
                onPickOther = { if (!correcting) actions.onToggleCorrection() },
                actions = actions,
            )
        }

        if (correcting) {
            LoggerFoodCorrection(
                query = correctionQuery,
                results = correctionResults,
                onQueryChange = actions.onCorrectionQueryChange,
                onPick = actions.onPickCorrection,
            )
        }

        if (tag.isExpanded) {
            if (tag.amountGrams != null || tag.loggedFood != null) {
                // C8: tope anclado a la masa base y un solo cambio por gesto; el campo numérico también vive en GramsEditor.
                GramsEditor(tag = tag, onGramsChange = actions.onGramsChange)
            }
            if (logged != null) {
                LoggerMacroSteppers(
                    calories = logged.calories,
                    protein = logged.protein,
                    carbs = logged.carbs,
                    fats = logged.fats,
                    onChange = actions.onMacroChange,
                )
                // Un cero puede ser «sin dato» en el modelo: solo se muestra lo que tiene un valor (sin cifras inventadas).
                val facts = listOfNotNull(
                    kotlin.math.round(logged.fiber).toInt().takeIf { it > 0 }?.let { "Fibra: $it g" },
                    kotlin.math.round(logged.sugar).toInt().takeIf { it > 0 }?.let { "Azúcar: $it g" },
                    kotlin.math.round(logged.sodiumMg).toInt().takeIf { it > 0 }?.let { "Sodio: $it mg" },
                )
                if (facts.isNotEmpty()) {
                    Text(
                        text = facts.joinToString(" · "),
                        style = KpknType.caption,
                        color = WizardColors.textFaint,
                    )
                }
            }
            logged?.nutritionReferenceNote?.let { note ->
                Text(text = note, style = KpknType.caption, color = WizardColors.textMuted)
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            KpknTextAction(
                text = "Corregir",
                onClick = actions.onToggleCorrection,
                icon = if (matchUncertain) KpknLineIcons.Info else null,
            )
            Spacer(Modifier.width(16.dp))
            KpknTextAction(
                text = if (tag.isExpanded) "Ocultar ajustes" else "Ajustar",
                onClick = actions.onToggleExpanded,
                icon = KpknLineIcons.ChevronDown,
            )
            Spacer(Modifier.weight(1f))
            KpknIconButton(
                icon = KpknLineIcons.Close,
                contentDescription = "Quitar $name",
                onClick = actions.onRemove,
            )
        }
    }
}

/** Filete de 1 dp entre dos filas de alimento dentro de su módulo (KPKN_UI_V2 §1). */
@Composable
fun LoggerFoodDivider(modifier: Modifier = Modifier) {
    Spacer(modifier = modifier.fillMaxWidth().height(1.dp).background(KpknSurface.divider))
}

/** «Arroz blanco cocido · aprox. 150 g», si la fila sabe qué alimento es y cuánto pesa. */
private fun identityLine(tag: ResolvedTag): String? {
    val result = tag.interpretationV2 ?: return null
    val identity = result.canonicalIdentity ?: "Alimento sin identificar"
    val approx = if (result.isUncertain) "aprox. " else ""
    val food = tag.loggedFood
    if (food?.recipeId != null) {
        val amount = java.text.DecimalFormat("0.##").format(food.amount)
        return "$identity · $approx$amount ${food.unit}"
    }
    return "$identity · $approx${kotlin.math.round(result.observedGrams ?: 0.0).toInt()} g"
}
