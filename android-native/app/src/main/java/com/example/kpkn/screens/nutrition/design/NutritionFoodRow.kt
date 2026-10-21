package com.example.kpkn.screens.nutrition.design

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.DailyMacroTotals
import com.example.kpkn.data.models.NutritionLog
import com.example.kpkn.domain.nutrition.NutritionDisplayFormat
import com.example.kpkn.domain.nutrition.computeFoodTotals
import com.example.kpkn.screens.nutrition.components.portionPresetLabel
import com.example.kpkn.screens.nutrition.reuse.QuickEntryTag
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.formatKcalEs
import com.example.kpkn.ui.design.KpknMetric
import com.example.kpkn.ui.design.KpknType
import com.example.kpkn.ui.theme.MacroColors
import java.util.Locale

/** El reparto de energía de un registro entre P, H y G: fracciones que suman 1. */
internal data class MacroEnergyShares(val protein: Float, val carbs: Float, val fat: Float)

/**
 * El reparto de la energía entre P, H y G (4·P, 4·H y 9·G), que es lo que pinta la micro barra. Sin macros que repartir, null: no se
 * pinta barra.
 */
internal fun macroEnergyShares(protein: Double, carbs: Double, fat: Double): MacroEnergyShares? {
    val p = protein.coerceAtLeast(0.0) * 4.0
    val c = carbs.coerceAtLeast(0.0) * 4.0
    val f = fat.coerceAtLeast(0.0) * 9.0
    val total = p + c + f
    if (total <= 0.0) return null
    return MacroEnergyShares(
        protein = (p / total).toFloat(),
        carbs = (c / total).toFloat(),
        fat = (f / total).toFloat(),
    )
}

/** El rango de kcal de un registro, «420–510 kcal», solo si la banda guardada tiene ancho. Sin ancho, null: nunca «500–500». */
internal fun kcalRangeText(totals: DailyMacroTotals): String? {
    val min = totals.caloriesMin ?: return null
    val max = totals.caloriesMax ?: return null
    val low = kotlin.math.round(min).toInt()
    val high = kotlin.math.round(max).toInt()
    return if (low < high) "${formatKcalEs(low)}–${formatKcalEs(high)} kcal" else null
}

/**
 * La porción de un registro, con lo que ya tiene: «150 g» si el alimento lleva cantidad, su porción («Mediano») si no, y «3 alimentos»
 * si son varios. Sin alimentos, null.
 */
internal fun logPortionText(log: NutritionLog): String? {
    val foods = log.foods
    if (foods.isEmpty()) return null
    if (foods.size > 1) return "${foods.size} alimentos"
    val food = foods.single()
    val preset = food.portionPreset
    return when {
        food.amount > 0.0 -> "${formatAmount(food.amount)} ${food.unit}".trim()
        preset != null -> portionPresetLabel(preset)
        else -> null
    }
}

private fun formatAmount(amount: Double): String =
    if (amount % 1.0 == 0.0) amount.toLong().toString() else String.format(Locale.forLanguageTag("es"), "%.1f", amount)

private val NutritionRowKcal: TextStyle = KpknType.bodyStrong.copy(fontFeatureSettings = "'tnum' 1")

/**
 * Una fila de un registro (N2, NUTRITION_V1 §2.4): el nombre en `bodyStrong`, la porción en `caption` y las kcal tabulares a la derecha;
 * a 6 dp bajo la porción, una micro barra de 3 dp con el reparto de energía de P, H y G. Una estimación lleva «≈» y, al tocar la cifra,
 * su rango.
 * Tocar la fila la edita ([onOpen]); la pulsación larga pide su menú ([onLongPress], con la posición de la fila en la ventana).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NutritionFoodRow(
    log: NutritionLog,
    onOpen: () -> Unit,
    onLongPress: (Rect) -> Unit,
    modifier: Modifier = Modifier,
    /** Solo para la hoja de render: deja abierto el rango de una estimación desde el principio. */
    rangeOpen: Boolean = false,
) {
    val totals = remember(log) { computeFoodTotals(log.foods) }
    val kcal = kotlin.math.round(totals.calories).toInt()
    val range = kcalRangeText(totals)
    val shares = macroEnergyShares(
        protein = log.foods.sumOf { it.protein },
        carbs = log.foods.sumOf { it.carbs },
        fat = log.foods.sumOf { it.fats },
    )
    val name = log.foods.joinToString(", ") { it.foodName }.ifEmpty { "Comida registrada" }
    // N4: una entrada rápida no tiene porción que mostrar (su cantidad es 1 «porción» interna): lleva la etiqueta «rápida» en su lugar.
    val quick = log.foods.any { it.isQuickEntry }
    val portion = if (quick && log.foods.size == 1) null else logPortionText(log)
    var bounds by remember { mutableStateOf<Rect?>(null) }
    var showRange by remember { mutableStateOf(rangeOpen) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .onGloballyPositioned { bounds = it.boundsInWindow() }
            .combinedClickable(
                onClickLabel = "Editar",
                onLongClickLabel = "Más opciones",
                role = Role.Button,
                onClick = onOpen,
                onLongClick = { bounds?.let(onLongPress) },
            )
            .padding(vertical = 10.dp),
    ) {
        // Arriba: la cifra de kcal queda a la altura del nombre, no centrada respecto a la porción.
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                // Hasta dos líneas: con la columna de kcal de 112 dp, un nombre largo («Pan integral, Queso fresco») no debe cortarse.
                Text(
                    text = name,
                    style = KpknType.bodyStrong,
                    color = WizardColors.text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                portion?.let {
                    Text(
                        text = it,
                        style = KpknType.caption,
                        color = WizardColors.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (quick) QuickEntryTag(Modifier.padding(top = 4.dp))
                // La micro barra va 6 dp bajo la porción (o el nombre), dentro de la columna de texto: la zona táctil de las kcal
                // (48 dp) no le añade hueco.
                if (shares != null) {
                    Spacer(Modifier.height(6.dp))
                    MacroEnergyBar(shares = shares)
                }
            }
            Spacer(Modifier.width(12.dp))
            // La cifra de kcal: si es una estimación con rango, tocarla muestra el rango debajo (zona táctil de 48 dp). Su ancho mínimo
            // (112 dp) fija el de la columna de texto, así que la micro barra mide igual en todas las filas.
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier
                    .testTag("nutrition-kcal-${log.id}")
                    .then(
                        if (range != null) {
                            Modifier.clickable(role = Role.Button, onClickLabel = "Ver rango") { showRange = !showRange }
                        } else {
                            Modifier
                        },
                    )
                    .defaultMinSize(minWidth = 112.dp, minHeight = 48.dp),
            ) {
                KpknMetric(
                    value = NutritionDisplayFormat.approxPrefix(totals.isEstimate) + formatKcalEs(kcal),
                    unit = "kcal",
                    style = NutritionRowKcal,
                )
                if (range != null) {
                    AnimatedVisibility(visible = showRange) {
                        Text(text = range, style = KpknType.caption, color = WizardColors.textMuted, maxLines = 1)
                    }
                }
            }
        }
    }
}

/** La micro barra de 3 dp con el reparto de energía de P, H y G, en los colores de los anillos (`MacroColors`). */
@Composable
private fun MacroEnergyBar(shares: MacroEnergyShares) {
    Canvas(modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp))) {
        var x = 0f
        listOf(
            shares.protein to MacroColors.protein,
            shares.carbs to MacroColors.carbs,
            shares.fat to MacroColors.fat,
        ).forEach { (share, color) ->
            val w = size.width * share
            if (w > 0f) {
                drawRect(color = color, topLeft = Offset(x, 0f), size = Size(w, size.height))
                x += w
            }
        }
    }
}
