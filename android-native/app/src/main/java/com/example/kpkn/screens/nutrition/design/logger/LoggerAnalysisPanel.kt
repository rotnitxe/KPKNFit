package com.example.kpkn.screens.nutrition.design.logger

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import java.util.Locale
import kotlinx.coroutines.launch

private val MsgStyle = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 19.sp)
private val MsgSmallStyle = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 17.sp)

/** Palabras de cantidad que se quitan al nombrar lo encontrado («dos huevos revueltos» → «huevos revueltos»). */
private val QUANTITY_WORDS = Regex(
    "^(?:\\d+(?:[.,]\\d+)?\\s*(?:g|gr|gramos?|ml|kg)?|un|una|uno|unos|unas|dos|tres|cuatro|cinco|seis|medio|media|un poco de|poco de|" +
        "algo de|mucho|mucha|el|la|los|las|mi|mis|de)\\s+",
    RegexOption.IGNORE_CASE,
)

/**
 * El mensaje del plato (f-ana), con lo que de verdad pasa: «Leyendo tu comida» antes de leer nada y «Encontré huevos revueltos y
 * tostadas» con los trozos ya leídos (los dos primeros; «, …» si hay más), siempre con «Estimando cantidades…» debajo. Con
 * [readingIndex] negativo (movimiento reducido) hay un solo mensaje.
 */
fun loggerAnalysisMessage(text: String, readingIndex: Int): Pair<String, String> {
    val small = "Estimando cantidades…"
    if (readingIndex <= 0) return "Leyendo tu comida" to small
    val names = loggerMentionRanges(text).take(readingIndex).map { range ->
        var piece = text.substring(range.first, range.last + 1).trim()
        while (true) {
            val stripped = piece.replaceFirst(QUANTITY_WORDS, "")
            if (stripped == piece || stripped.isBlank()) break
            piece = stripped
        }
        piece.lowercase(Locale.forLanguageTag("es"))
    }.filter { it.isNotBlank() }.distinct()
    if (names.isEmpty()) return "Leyendo tu comida" to small
    val shown = names.take(2)
    val joined = if (shown.size == 1) shown[0] else "${shown[0]} y ${shown[1]}"
    return "Encontré $joined${if (names.size > 2) ", …" else ""}" to small
}

/**
 * «Leyendo tu comida» (lienzo f-ana, `.ana` + `plateAnim`): un plato de líneas que se dibuja (dos círculos y un arco en acento con
 * brillo), un punto que orbita y cuatro alimentos de línea que flotan alrededor ([icons]: los de la comida, o huevo, pan, aguacate y
 * taza). Debajo, el mensaje de [loggerAnalysisMessage]. Con movimiento reducido todo queda quieto y dibujado.
 */
@Composable
fun LoggerAnalysisPanel(
    text: String,
    readingIndex: Int,
    modifier: Modifier = Modifier,
    icons: List<ImageVector> = emptyList(),
    height: Dp = 250.dp,
) {
    val reduced = wizardReducedMotion()
    val shape = RoundedCornerShape(24.dp)
    val (title, small) = loggerAnalysisMessage(text, readingIndex)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(shape)
            .background(LoggerColors.surface1, shape)
            .semantics { contentDescription = "Analizando la comida" },
    ) {
        LoggerPlate(
            icons = (icons + listOf(LoggerIcons.Egg, LoggerIcons.Bread, LoggerIcons.Avocado, LoggerIcons.Cup)).distinct().take(4),
            reduced = reduced,
            modifier = Modifier.align(Alignment.Center),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 18.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AnimatedContent(
                targetState = title,
                transitionSpec = { fadeIn(tween(240)) togetherWith fadeOut(tween(180)) },
                label = "loggerAnalysisMessage",
            ) { message ->
                Text(text = message, style = MsgStyle, color = LoggerColors.muted, textAlign = TextAlign.Center, maxLines = 1)
            }
            Text(text = small, style = MsgSmallStyle, color = LoggerColors.faint, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/** Posiciones (centro, en la retícula 300 × 250 del lienzo) de los cuatro alimentos que flotan. */
private val ICON_SPOTS = listOf(60f to 70f, 230f to 62f, 52f to 170f, 238f to 168f)
private val FLOAT_PERIODS = listOf(3200, 3600, 3000, 3400)
private val FLOAT_DELAYS = listOf(0, 400, 800, 1200)

@Composable
private fun LoggerPlate(icons: List<ImageVector>, reduced: Boolean, modifier: Modifier = Modifier) {
    val draw = remember { listOf(Animatable(if (reduced) 1f else 0f), Animatable(if (reduced) 1f else 0f), Animatable(if (reduced) 1f else 0f)) }
    LaunchedEffect(reduced) {
        if (reduced) return@LaunchedEffect
        draw.forEachIndexed { index, anim ->
            launch { anim.animateTo(1f, tween(1400, delayMillis = index * 250, easing = CubicBezierEasing(0.3f, 0.7f, 0.2f, 1f))) }
        }
    }
    val transition = rememberInfiniteTransition(label = "loggerPlate")
    val orbit by transition.animateFloat(
        initialValue = 0f,
        targetValue = if (reduced) 0f else 360f,
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing)),
        label = "loggerPlateOrbit",
    )
    val floats = FLOAT_PERIODS.mapIndexed { index, period ->
        transition.animateFloat(
            initialValue = 0f,
            targetValue = if (reduced) 0f else -8f,
            animationSpec = infiniteRepeatable(
                tween(period / 2, easing = FastOutSlowInEasing),
                RepeatMode.Reverse,
                initialStartOffset = StartOffset(FLOAT_DELAYS[index]),
            ),
            label = "loggerPlateFloat$index",
        )
    }
    Box(modifier = modifier.size(300.dp, 250.dp)) {
        Canvas(Modifier.size(300.dp, 250.dp)) {
            val u = size.width / 300f
            val c = Offset(150f * u, 125f * u)
            val ink = Color(0xFFF2EEE6)
            drawArc(
                ink.copy(alpha = 0.75f), startAngle = 0f, sweepAngle = 360f * draw[0].value, useCenter = false,
                topLeft = Offset(c.x - 78f * u, c.y - 78f * u), size = Size(156f * u, 156f * u), style = Stroke(1.4f * u),
            )
            drawArc(
                ink.copy(alpha = 0.35f), startAngle = 0f, sweepAngle = 360f * draw[1].value, useCenter = false,
                topLeft = Offset(c.x - 58f * u, c.y - 58f * u), size = Size(116f * u, 116f * u), style = Stroke(1f * u),
            )
            val arcTopLeft = Offset(c.x - 90f * u, c.y - 90f * u)
            val arcSize = Size(180f * u, 180f * u)
            // Sin brillo ni acento: el arco y el punto van en tinta (el acento es solo para las dudas).
            drawArc(
                LoggerColors.macroP, startAngle = 180f, sweepAngle = 180f * draw[2].value, useCenter = false,
                topLeft = arcTopLeft, size = arcSize, style = Stroke(2f * u, cap = StrokeCap.Round),
            )
            rotate(orbit, pivot = c) {
                drawCircle(LoggerColors.macroP, radius = 3f * u, center = Offset(150f * u, 40f * u))
            }
        }
        icons.forEachIndexed { index, icon ->
            val (x, y) = ICON_SPOTS[index]
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFFF2EEE6),
                modifier = Modifier
                    .offset(x = (x - 18f).dp, y = (y - 18f).dp + floats[index].value.dp)
                    .size(36.dp),
            )
        }
    }
}
