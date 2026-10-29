package com.example.kpkn.screens.nutrition.design.logger

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.ui.components.icons.KpknLineIcons

/** Macro que se corrige a mano en la hoja de ajuste («Aporta»). */
enum class LoggerMacro { CALORIES, PROTEIN, CARBS, FATS }

private val FoodNameStyle = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 19.sp)

/** «2 huevos · 110 g · aprox.» (con «· aprox.» si la cantidad es una estimación). */
fun loggerFoodSubtitle(amountLine: String, approximate: Boolean): String = if (approximate) "$amountLine · aprox." else amountLine

/** «≈ 96» si la cifra es una estimación; «182» si no. */
fun loggerFoodKcal(kcal: Int, approximate: Boolean): String = if (approximate) "≈ $kcal" else kcal.toString()

/**
 * La tarjeta de un alimento (lienzo `.np-food`): plana (superficie 1, radio 24, 14 × 16), el icono de línea en su caja de 40 dp
 * (superficie 2, radio 13; 34 en [compact], la media hoja de Inicio), el nombre (hasta dos líneas, nunca «…»), «150 g · aprox.»,
 * «≈ 297» y «kcal» a la derecha y la barra de macros de 4 dp. Con una duda ([question]) la pregunta va dentro, con su punto de acento
 * mientras no se responde; la respuesta elegida queda marcada ([answered]). Tocar la tarjeta abre su hoja de ajuste ([onOpen]).
 * [onSkip] ya no se usa («Seguir con la estimación» salió: guardar ya sigue con lo estimado); se conserva para quien llama.
 */
@Composable
fun LoggerFoodCard(
    name: String,
    subtitle: String,
    kcal: String,
    protein: Double,
    carbs: Double,
    fats: Double,
    icon: ImageVector,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    question: LoggerQuestion? = null,
    answered: LoggerQuestion? = null,
    onChoice: (String) -> Unit = {},
    onSkip: () -> Unit = {},
    index: Int = 0,
    compact: Boolean = false,
    showKcal: Boolean = true,
) {
    val reduced = wizardReducedMotion()
    val rise = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!reduced) rise.animateTo(1f, tween(600, delayMillis = (if (compact) 120 else 150) + index * (if (compact) 120 else 140), easing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)))
    }
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = rise.value
                translationY = (1f - rise.value) * 10.dp.toPx()
            }
            .clip(shape)
            .background(LoggerColors.surface1, shape)
            .padding(horizontal = if (compact) 12.dp else 16.dp, vertical = if (compact) 10.dp else 14.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onOpen)
                .semantics(mergeDescendants = true) { contentDescription = "$name, $subtitle, $kcal kcal. Ajustar" },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val box = if (compact) 34.dp else 40.dp
            Box(
                modifier = Modifier
                    .size(box)
                    .clip(RoundedCornerShape(if (compact) 11.dp else 13.dp))
                    .background(LoggerColors.surface2),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = LoggerColors.ink)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = name, style = FoodNameStyle, color = LoggerColors.ink)
                Text(text = subtitle, style = LoggerText.cap, color = LoggerColors.faint, modifier = Modifier.padding(top = 2.dp))
            }
            if (showKcal) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = kcal,
                        style = LoggerText.number(16f),
                        color = LoggerColors.ink,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        softWrap = false,
                    )
                    Text(text = "kcal", style = LoggerText.cap, color = LoggerColors.faint, maxLines = 1, softWrap = false)
                }
            }
        }
        // `.np-bar` de 4 dp con 14 dp por encima y 12 por debajo; la pregunta (con su filete) se pega 2 dp más abajo: 14 en total.
        if (!compact) LoggerMacroStrip(protein = protein, carbs = carbs, fats = fats, modifier = Modifier.padding(top = 14.dp, bottom = 12.dp))
        val shown = question ?: answered
        AnimatedVisibility(
            visible = shown != null,
            enter = fadeIn(tween(200)) + expandVertically(),
            exit = fadeOut(tween(200)) + shrinkVertically(),
        ) {
            if (shown != null) {
                LoggerQuestionBlock(
                    question = shown,
                    onChoice = onChoice,
                    onSkip = onSkip,
                    answered = question == null,
                    modifier = Modifier.padding(top = if (compact) 12.dp else 2.dp),
                )
            }
        }
    }
}

/** `.np-bar` de 4 dp: los tres macros por sus calorías (P·4, C·4, G·9) en tres tonos de tinta, con 3 dp entre tramos. */
@Composable
fun LoggerMacroStrip(protein: Double, carbs: Double, fats: Double, modifier: Modifier = Modifier) {
    LoggerMacroSplitBar(protein = protein, carbs = carbs, fats = fats, modifier = modifier, height = 4.dp)
}

/** `.act.np-addfood`: «＋ Añadir otro alimento» como acción simple en tinta, con 4 dp a la izquierda (zona táctil de 48 dp). */
@Composable
fun LoggerAddFoodAction(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .padding(start = 4.dp)
            .defaultMinSize(minHeight = 48.dp)
            .clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(KpknLineIcons.Plus, contentDescription = null, modifier = Modifier.size(16.dp), tint = LoggerColors.ink)
        Text(text = "Añadir otro alimento", style = LoggerText.act, color = LoggerColors.ink, maxLines = 1)
    }
}
