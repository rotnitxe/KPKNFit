package com.example.kpkn.screens.nutrition.design.logger

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion

/** Macro que se corrige a mano en la hoja de ajuste («Aporta»). */
enum class LoggerMacro { CALORIES, PROTEIN, CARBS, FATS }

private val FoodNameStyle = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp)
private val FoodKcalStyle = TextStyle(fontFamily = WizardFonts.display, fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 21.sp)
private val FoodKcalUnitStyle = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 15.sp)

/** «2 huevos · 110 g · aprox.» (con «· aprox.» si la cantidad es una estimación). */
fun loggerFoodSubtitle(amountLine: String, approximate: Boolean): String = if (approximate) "$amountLine · aprox." else amountLine

/** «≈ 96» si la cifra es una estimación; «182» si no. */
fun loggerFoodKcal(kcal: Int, approximate: Boolean): String = if (approximate) "≈ $kcal" else kcal.toString()

/**
 * La tarjeta de un alimento (lienzo `foodCard`): icono de línea en su cuadro de 42 dp (34 en [compact], la media hoja de Inicio), el
 * nombre, «2 huevos · 110 g», las kcal en Syne a la derecha y la barra de macros de 4 dp. Con una duda ([question]) la tarjeta toma el
 * tono cálido y la duda va dentro; la respuesta elegida queda marcada ([answered]) y el tono se apaga con una transición. Tocar la
 * tarjeta abre su hoja de ajuste ([onOpen]). Entra subiendo 10 dp con un retraso de 150 + 140 ms por [index].
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
    val asking = question != null
    val shape = RoundedCornerShape(22.dp)
    val border by animateColorAsState(
        if (asking) LoggerColors.ene.copy(alpha = 0.267f) else Color.White.copy(alpha = 0.07f),
        tween(400),
        label = "loggerFoodBorder",
    )
    val warm by animateColorAsState(
        if (asking) LoggerColors.ene.copy(alpha = 0.05f) else LoggerColors.card,
        tween(400),
        label = "loggerFoodWarm",
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = rise.value
                translationY = (1f - rise.value) * 10.dp.toPx()
            }
            .clip(shape)
            .background(Brush.linearGradient(0f to warm, 0.55f to LoggerColors.card, 1f to LoggerColors.card), shape)
            .border(1.dp, border, shape)
            .padding(horizontal = if (compact) 12.dp else 14.dp, vertical = if (compact) 10.dp else 12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClick = onOpen)
                .semantics(mergeDescendants = true) { contentDescription = "$name, $subtitle, $kcal kcal. Ajustar" },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val box = if (compact) 34.dp else 42.dp
            Box(
                modifier = Modifier
                    .size(box)
                    .clip(RoundedCornerShape(if (compact) 11.dp else 14.dp))
                    .background(Color.White.copy(alpha = 0.04f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(if (compact) 20.dp else 24.dp), tint = LoggerColors.ink)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = name, style = FoodNameStyle, color = LoggerColors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = subtitle,
                    style = LoggerText.cap,
                    color = LoggerColors.faint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            if (showKcal) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(text = kcal, style = FoodKcalStyle, color = LoggerColors.ink, textAlign = TextAlign.End, maxLines = 1)
                    Text(text = "kcal", style = FoodKcalUnitStyle, color = LoggerColors.faint)
                }
            }
        }
        if (!compact) LoggerMacroStrip(protein = protein, carbs = carbs, fats = fats, modifier = Modifier.padding(top = 10.dp))
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
                )
            }
        }
    }
}

/** `.mbar`: la barra de 4 dp con los tres macros por sus calorías (4, 4 y 9 por gramo), con 2 dp entre tramos. */
@Composable
fun LoggerMacroStrip(protein: Double, carbs: Double, fats: Double, modifier: Modifier = Modifier) {
    val p = (protein * 4).coerceAtLeast(0.0).toFloat()
    val c = (carbs * 4).coerceAtLeast(0.0).toFloat()
    val f = (fats * 9).coerceAtLeast(0.0).toFloat()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp)),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (p + c + f <= 0f) {
            Box(Modifier.weight(1f).fillMaxHeight().background(Color.White.copy(alpha = 0.063f)))
        } else {
            if (p > 0f) Box(Modifier.weight(p).fillMaxHeight().background(LoggerColors.mus))
            if (c > 0f) Box(Modifier.weight(c).fillMaxHeight().background(LoggerColors.ene))
            if (f > 0f) Box(Modifier.weight(f).fillMaxHeight().background(LoggerColors.col))
        }
    }
}
