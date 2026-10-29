package com.example.kpkn.screens.nutrition.design.logger

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.models.MealType
import com.example.kpkn.domain.nutrition.MealSlots
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import kotlinx.coroutines.launch

/** Lo que tarda en dibujarse el círculo del check (el trazo, 0,5 s después). */
internal const val LoggerSuccessDrawMs = 600

/** f-saved: «Se cierra solo a los 0,8 s». */
internal const val LoggerSuccessHoldMs = 800L

/** Con movimiento reducido el check sale ya dibujado y la espera es la misma. */
internal const val LoggerSuccessHoldReducedMs = 800L

private val SavedTitleStyle = TextStyle(fontFamily = WizardFonts.display, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp, lineHeight = 31.sp)
private val SavedLineStyle = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp)

/** «Desayuno guardado», «Merienda guardada», «Cena guardada»; «Cambios guardados» al editar una comida registrada. */
fun loggerSavedTitle(meal: MealType, editing: Boolean = false): String {
    if (editing) return "Cambios guardados"
    val label = MealSlots.mealLabel(meal)
    val feminine = label.endsWith("a", ignoreCase = true)
    return "$label ${if (feminine) "guardada" else "guardado"}"
}

/**
 * «548 kcal · te quedan 1.752 para hoy» (f-saved): lo que importa justo después de guardar. Sin objetivo, solo las calorías; al llegar o
 * pasarse, «llegaste a tu objetivo de hoy».
 */
fun loggerSavedLine(kcal: Int, remainingToday: Int?): String {
    val head = "${loggerThousands(kcal)} kcal"
    return when {
        remainingToday == null -> head
        remainingToday > 0 -> "$head · te quedan ${loggerThousands(remainingToday)} para hoy"
        else -> "$head · llegaste a tu objetivo de hoy"
    }
}

/**
 * Guardado (lienzo `.savedone`): velo negro al 80 % sobre el registro, el check verde de 140 dp que se dibuja (círculo y luego el trazo,
 * 0,5 s después), [title] en Syne 26 y [line] debajo. Con movimiento reducido sale dibujado.
 */
@Composable
fun LoggerSavedOverlay(title: String, line: String, modifier: Modifier = Modifier) {
    val reduced = wizardReducedMotion()
    val circle = remember { Animatable(if (reduced) 1f else 0f) }
    val tick = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(reduced) {
        if (reduced) return@LaunchedEffect
        val easing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)
        launch { circle.animateTo(1f, tween(LoggerSuccessDrawMs, easing = easing)) }
        launch { tick.animateTo(1f, tween(LoggerSuccessDrawMs, delayMillis = 500, easing = easing)) }
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.8f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .padding(horizontal = 28.dp)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Canvas(Modifier.size(140.dp)) {
            val u = size.width / 120f
            drawArc(
                color = LoggerColors.done,
                startAngle = 0f,
                sweepAngle = 360f * circle.value,
                useCenter = false,
                topLeft = Offset(10f * u, 10f * u),
                size = androidx.compose.ui.geometry.Size(100f * u, 100f * u),
                style = Stroke(width = 3f * u),
            )
            val path = Path().apply {
                moveTo(38f * u, 62f * u)
                lineTo(53f * u, 77f * u)
                lineTo(83f * u, 45f * u)
            }
            val measure = PathMeasure().apply { setPath(path, false) }
            val partial = Path()
            measure.getSegment(0f, measure.length * tick.value, partial, true)
            drawPath(partial, LoggerColors.done, style = Stroke(width = 4f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        Text(text = title, style = SavedTitleStyle, color = LoggerColors.ink, textAlign = TextAlign.Center)
        Text(text = line, style = SavedLineStyle, color = LoggerColors.muted, textAlign = TextAlign.Center)
    }
}
