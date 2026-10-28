package com.example.kpkn.screens.workout.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.discomfortKnowledge
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.screens.workout.LinkedDiscomfort
import com.example.kpkn.screens.workout.PostExerciseFeedbackDraft
import com.example.kpkn.screens.workout.RestNextSetSummary
import com.example.kpkn.screens.workout.displayWorkoutExerciseName
import com.example.kpkn.screens.workout.restLastSetQuantityText
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknSetFormat
import java.util.Locale

/*
 * El descanso del Refinamiento 02 tal cual el prototipo (`refinements.css`/`refinements.js`: .v2-rest-card, .eclipse,
 * .v2-rest-controls, .v2-rest-next, .v2-suggestion, .rest-fullscreen, .v2-rest-full-context, .v2-full-game-link, .v2-full-bottom,
 * .v2-feedback, .v2-feedback-clock, .v2-arcade-header y .v2-game-picker). Las medidas son las del CSS; `compact` es la variante
 * `.compact` del prototipo (360 × 740). Nada de esto desplaza: todo cabe en su marco.
 */

internal object RestRef02Tokens {
    val Ink = Color(0xFFF2EEE6)
    val Muted = Color(0xFFB9B5AE)
    val Faint = Color(0xFF92908A)
    val CardFill = Color(0xFF111111)
    val CardBorder = Color(0x0BFFFFFF)
    val ControlFill = Color(0xFF1B1B1B)
    val SuggestionFill = Color(0xFF1A1A1A)
    val SuggestionBorder = Color(0x12FFFFFF)
    val FullBackground = Color(0xFF080808)
    val OnInk = Color(0xFF111111)
}

private val CardShape = RoundedCornerShape(25.dp)

private fun body(size: TextUnit, weight: FontWeight = FontWeight.Normal, lineHeight: TextUnit = TextUnit.Unspecified,
    letterSpacing: TextUnit = TextUnit.Unspecified) =
    TextStyle(fontFamily = WizardFonts.body, fontSize = size, fontWeight = weight, lineHeight = lineHeight, letterSpacing = letterSpacing)

private fun syne(size: TextUnit, weight: FontWeight = FontWeight.Bold, lineHeight: TextUnit = TextUnit.Unspecified,
    letterSpacing: TextUnit = TextUnit.Unspecified) =
    // Syne necesita más alto que su cuerpo: un interlineado más apretado que 1,3 recortaría la cifra (texto completo, ley del 130 %).
    TextStyle(fontFamily = WizardFonts.display, fontSize = size, fontWeight = weight,
        lineHeight = if (lineHeight != TextUnit.Unspecified && lineHeight.value >= size.value * 1.3f) lineHeight else TextUnit.Unspecified,
        letterSpacing = letterSpacing, fontFeatureSettings = "'tnum' 1")

/** «1:32», o «+0:12» en tiempo extra. */
internal fun restRef02Clock(seconds: Int): String =
    (if (seconds < 0) "+" else "") + KpknSetFormat.rest(kotlin.math.abs(seconds))

/** «100 × 8 · RIR 2» (`rr().lastSet` del prototipo): carga, cantidad y el esfuerzo anotado, si lo hay. */
internal fun restRef02LastSetText(set: CompletedSet, customUnit: String?): String {
    val quantity = restLastSetQuantityText(set, customUnit).removeSuffix(" reps")
    val load = set.weight.takeIf { it > 0.0 }?.let {
        if (it % 1.0 == 0.0) it.toInt().toString() else String.format(Locale.forLanguageTag("es"), "%.1f", it)
    }
    val effort = set.rir?.let { "RIR $it" } ?: set.rpe?.let { "RPE ${if (it % 1.0 == 0.0) it.toInt() else it}" }
    return listOfNotNull(if (load != null) "$load × $quantity" else quantity, effort).joinToString(" · ")
}

/** Rótulo de la siguiente serie: «Sentadilla · serie 3» (tarjeta) o «Sentadilla · 3» (pantalla completa). */
internal fun restRef02NextTitle(next: RestNextSetSummary, fallbackName: String, card: Boolean): String {
    if (next.cardio) return next.exerciseName ?: "Cardio"
    val name = (next.exerciseName ?: fallbackName).trim()
    if (name.isEmpty()) return "Serie ${next.setNumber}"
    return if (card) "$name · serie ${next.setNumber}" else "$name · ${next.setNumber}"
}

/**
 * `.eclipse`: pista casi invisible, corona difusa, trazo de progreso en crema con su brillo y la perla de luz en el extremo. El centro
 * lleva el tiempo en Syne y «Descansando», «En pausa» o «Tiempo extra». [showCenter] falso es la versión `.tiny` del feedback.
 */
@Composable
internal fun RestEclipse(
    progress: Float,
    time: String,
    label: String,
    diameter: Dp,
    timeSize: TextUnit,
    labelSize: TextUnit,
    animationsOn: Boolean,
    modifier: Modifier = Modifier,
    showCenter: Boolean = true,
) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = if (animationsOn) tween(800, easing = LinearEasing) else snap(),
        label = "rest-eclipse",
    )
    Box(modifier.size(diameter).testTag("rest-eclipse"), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) { drawEclipse(animated) }
        if (showCenter) {
            Box(
                Modifier.fillMaxSize(0.88f).clip(CircleShape).background(
                    Brush.radialGradient(0.2f to Color(0x30171717), 0.64f to Color.Transparent),
                ),
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = "$label, $time" },
            ) {
                // «+0:24» o «12:30» tienen un carácter más que «1:32»: la cifra baja para seguir dentro del círculo.
                val fitted = if (time.length > 4) timeSize * (4f / time.length) * 1.08f else timeSize
                Text(time, style = syne(fitted, FontWeight.ExtraBold, lineHeight = fitted * 1.2f, letterSpacing = (-1.5).sp),
                    color = RestRef02Tokens.Ink, textAlign = TextAlign.Center,
                    // Ancho completo del círculo: con el espaciado negativo de Syne el ancho intrínseco queda por debajo del párrafo.
                    modifier = Modifier.fillMaxWidth().testTag("rest-eclipse-clock"))
                Text(label, Modifier.fillMaxWidth().padding(top = 10.dp), style = body(labelSize), color = RestRef02Tokens.Faint,
                    textAlign = TextAlign.Center)
            }
        }
    }
}

private fun DrawScope.drawEclipse(progress: Float) {
    // El SVG del prototipo mide 200 de lado con r = 88; los trazos escalan con el diámetro.
    val scale = size.minDimension / 200f
    val radius = 88f * scale
    val center = Offset(size.width / 2f, size.height / 2f)
    val topLeft = Offset(center.x - radius, center.y - radius)
    val arcSize = Size(radius * 2f, radius * 2f)
    val ink = RestRef02Tokens.Ink
    drawCircle(Color(0x0BFFFFFF), radius, center, style = Stroke(1.1f * scale))
    val sweep = 360f * progress
    if (sweep > 0f) {
        // Corona: trazo de 3 con desenfoque de 5 y opacidad 0,15, aproximado con trazos concéntricos cada vez más tenues.
        listOf(3f to .07f, 6f to .045f, 10f to .025f).forEach { (width, alpha) ->
            drawArc(ink.copy(alpha = alpha), -90f, sweep, false, topLeft, arcSize, style = Stroke(width * scale, cap = StrokeCap.Round))
        }
        // Brillo del trazo (drop-shadow de 3 px en blanco al 38 %).
        drawArc(Color.White.copy(alpha = .12f), -90f, sweep, false, topLeft, arcSize, style = Stroke(4f * scale, cap = StrokeCap.Round))
        drawArc(ink, -90f, sweep, false, topLeft, arcSize, style = Stroke(1.1f * scale, cap = StrokeCap.Round))
    }
    // La perla: en el radio del 44 % del diámetro (inset 6 %), girada con el progreso.
    val angle = Math.toRadians((sweep - 90f).toDouble())
    val pearlRadius = size.minDimension * .44f
    val pearl = Offset(center.x + (pearlRadius * kotlin.math.cos(angle)).toFloat(), center.y + (pearlRadius * kotlin.math.sin(angle)).toFloat())
    // box-shadow: 0 0 7px 3px #ffffff40, 0 0 24px 6px #ffffff13 → dos halos radiales que se desvanecen.
    val glow = 7.dp.toPx()
    drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = .07f), Color.Transparent), pearl, glow * 2.2f), glow * 2.2f, pearl)
    drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = .3f), Color.Transparent), pearl, glow), glow, pearl)
    drawCircle(ink, 2.dp.toPx(), pearl)
}

/** `.v2-rest-controls`: −15 s (69), pausa (45) y +15 s (69), radio 11, relleno #1b1b1b. */
@Composable
internal fun RestRef02Controls(
    isPaused: Boolean,
    onDecrease: () -> Unit,
    onTogglePause: () -> Unit,
    onIncrease: () -> Unit,
    height: Dp,
    modifier: Modifier = Modifier,
    pauseEnabled: Boolean = true,
    decreaseEnabled: Boolean = true,
    increaseEnabled: Boolean = true,
) {
    Row(modifier.fillMaxWidth().height(height), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
        RestControlBox(69.dp, "Restar 15 segundos", onDecrease, decreaseEnabled, "rest-minus") {
            Text("−15 s", style = body(12.sp, FontWeight.Medium), color = RestRef02Tokens.Ink)
        }
        RestControlBox(45.dp, if (isPaused) "Continuar descanso" else "Pausar descanso", onTogglePause, pauseEnabled, "rest-pause") {
            Icon(if (isPaused) KpknLineIcons.Play else KpknLineIcons.Pause, null, Modifier.size(18.dp), tint = RestRef02Tokens.Ink)
        }
        RestControlBox(69.dp, "Sumar 15 segundos", onIncrease, increaseEnabled, "rest-plus") {
            Text("+15 s", style = body(12.sp, FontWeight.Medium), color = RestRef02Tokens.Ink)
        }
    }
}

@Composable
private fun RestControlBox(width: Dp, description: String, onClick: () -> Unit, enabled: Boolean, tag: String, content: @Composable () -> Unit) {
    Box(
        Modifier.width(width).fillMaxHeight().clip(RoundedCornerShape(11.dp)).background(RestRef02Tokens.ControlFill)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description }.testTag(tag)
            .alpha(if (enabled) 1f else .35f),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** `.v2-suggestion`: «Descanso sugerido · 2:30 ›». Sin [onClick] es la referencia del plan, sin flecha. */
@Composable
internal fun RestRef02SuggestionRow(
    label: String,
    value: String,
    onClick: (() -> Unit)?,
    height: Dp,
    labelSize: TextUnit,
    valueSize: TextUnit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier.fillMaxWidth().heightIn(min = height).clip(shape).background(RestRef02Tokens.SuggestionFill)
            .border(1.dp, RestRef02Tokens.SuggestionBorder, shape)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 10.dp).testTag("rest-suggestion-row"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(label, Modifier.weight(1f), style = body(labelSize), color = RestRef02Tokens.Ink)
        Text(value, style = syne(valueSize), color = RestRef02Tokens.Ink)
        if (onClick != null) Icon(KpknLineIcons.ChevronRight, null, Modifier.size(13.dp), tint = RestRef02Tokens.Muted)
    }
}

/** Qué dice la fila del sugerido: el sugerido pendiente, el ya aplicado o la referencia del plan. */
internal data class RestSuggestionLine(val label: String, val seconds: Int, val opensSheet: Boolean)

internal fun restSuggestionLine(plannedSeconds: Int, totalSeconds: Int, suggestedSeconds: Int?, adaptiveActive: Boolean): RestSuggestionLine =
    when {
        suggestedSeconds != null && !adaptiveActive -> RestSuggestionLine("Descanso sugerido", suggestedSeconds, true)
        adaptiveActive -> RestSuggestionLine("Sugerido aplicado", totalSeconds, false)
        else -> RestSuggestionLine("Descanso del plan", if (plannedSeconds > 0) plannedSeconds else totalSeconds, false)
    }

/**
 * Hoja «Descanso sugerido» (`rest-suggestion`): PLANIFICADO → SUGERIDO, los datos que la explican, «Aplicar m:ss» y «Mantener descanso
 * actual». El temporizador cambia solo si se aplica.
 */
@Composable
internal fun RestSuggestionSheet(
    plannedSeconds: Int,
    suggestedSeconds: Int,
    lastSetText: String?,
    elapsedSeconds: Int,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
) {
    LiveSheet(title = "Descanso sugerido", onDismiss = onDismiss, tag = "rest-suggestion-sheet", footer = null) { close ->
        Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 30.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("PLANIFICADO", Modifier.padding(bottom = 9.dp), style = body(9.sp, letterSpacing = .7.sp), color = RestRef02Tokens.Faint)
                Text(KpknSetFormat.rest(plannedSeconds), style = syne(33.sp), color = RestRef02Tokens.Ink)
            }
            Text("→", style = body(16.sp), color = RestRef02Tokens.Muted)
            Column(horizontalAlignment = Alignment.End) {
                Text("SUGERIDO", Modifier.padding(bottom = 9.dp), style = body(9.sp, letterSpacing = .7.sp), color = RestRef02Tokens.Faint)
                Text(KpknSetFormat.rest(suggestedSeconds), style = syne(33.sp), color = RestRef02Tokens.Ink)
            }
        }
        LiveSheetSubtitle("Sugerencia para este descanso a partir de tu última serie. El temporizador cambia sólo si la aplicas.")
        Column(Modifier.fillMaxWidth().padding(vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            lastSetText?.let { RestFact("Última serie", it) }
            RestFact("Tiempo transcurrido", KpknSetFormat.rest(elapsedSeconds.coerceAtLeast(0)))
            RestFact("Restante tras aplicar", KpknSetFormat.rest((suggestedSeconds - elapsedSeconds).coerceAtLeast(0)))
        }
        LiveSheetShortCopy("La sugerencia de duración no es una medición de recuperación física.")
        LiveSheetPrimary("Aplicar ${KpknSetFormat.rest(suggestedSeconds)}", { onApply(); close() }, tag = "rest-suggestion-apply")
        LiveSheetTextAction("Mantener descanso actual", close)
    }
}

@Composable
private fun RestFact(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f), style = body(11.sp), color = RestRef02Tokens.Faint)
        Text(value, style = body(11.sp, FontWeight.SemiBold), color = RestRef02Tokens.Ink, textAlign = TextAlign.End)
    }
}

/** El icono `expand` del prototipo: cuatro esquinas. */
@Composable
internal fun RestExpandGlyph(modifier: Modifier = Modifier, color: Color = RestRef02Tokens.Ink) {
    Canvas(modifier.size(14.dp)) {
        val s = size.width
        val l = s * .32f
        val w = 1.4.dp.toPx()
        val c = StrokeCap.Round
        drawLine(color, Offset(0f, l), Offset(0f, 0f), w, c); drawLine(color, Offset(0f, 0f), Offset(l, 0f), w, c)
        drawLine(color, Offset(s - l, 0f), Offset(s, 0f), w, c); drawLine(color, Offset(s, 0f), Offset(s, l), w, c)
        drawLine(color, Offset(s, s - l), Offset(s, s), w, c); drawLine(color, Offset(s, s), Offset(s - l, s), w, c)
        drawLine(color, Offset(l, s), Offset(0f, s), w, c); drawLine(color, Offset(0f, s), Offset(0f, s - l), w, c)
    }
}

/** `.v2-card` del descanso: radio 25, #111, filete blanco al 4 %, contenido 13/18 y pie de 57 (51 en compacto). */
@Composable
internal fun RestRef02Card(
    compact: Boolean,
    modifier: Modifier = Modifier,
    tag: String = "rest-card",
    footer: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val horizontal = if (compact) 15.dp else 18.dp
    Column(
        modifier.fillMaxSize().clip(CardShape).background(RestRef02Tokens.CardFill).border(1.dp, RestRef02Tokens.CardBorder, CardShape)
            .testTag(tag),
    ) {
        Column(
            Modifier.weight(1f).fillMaxWidth()
                .padding(start = horizontal, end = horizontal, top = if (compact) 9.dp else 13.dp, bottom = if (footer == null) 12.dp else 0.dp),
            content = content,
        )
        if (footer != null) {
            Box(
                // Alto mínimo del prototipo (57, 51 en compacto); con la letra grande el pie crece y el contenido, que tiene peso, cede.
                Modifier.fillMaxWidth().heightIn(min = if (compact) 51.dp else 57.dp)
                    .padding(start = horizontal, end = horizontal, top = if (compact) 4.dp else 6.dp, bottom = if (compact) 9.dp else 12.dp),
                contentAlignment = Alignment.Center,
            ) { footer() }
        }
    }
}

/** `.v2-kicker`: «DESCANSO» en 9 sp con el botón de expandir a la derecha. */
@Composable
internal fun RestRef02Kicker(text: String, compact: Boolean, onExpand: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().height(if (compact) 27.dp else 29.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, Modifier.weight(1f).testTag("rest-kicker"), style = body(9.sp, letterSpacing = .65.sp), color = RestRef02Tokens.Muted)
        if (onExpand != null) {
            Box(
                Modifier.size(width = 32.dp, height = 30.dp).clip(RoundedCornerShape(8.dp))
                    .clickable(role = Role.Button, onClick = onExpand).semantics { contentDescription = "Expandir descanso" }
                    .testTag("rest-expand"),
                contentAlignment = Alignment.Center,
            ) { RestExpandGlyph() }
        }
    }
}

/** `.v2-rest-next`: filete superior, «SIGUIENTE», la serie y su dosis en Syne a la derecha. */
@Composable
internal fun RestRef02NextRow(title: String?, dose: String?, compact: Boolean, note: String? = null) {
    Column(
        Modifier.fillMaxWidth().heightIn(min = if (compact) 43.dp else 48.dp).drawBehind {
            drawLine(Color(0x0CFFFFFF), Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx())
        }.padding(vertical = if (compact) 7.dp else 9.dp).testTag("rest-next"),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text("SIGUIENTE", style = body(8.sp, letterSpacing = 1.sp), color = RestRef02Tokens.Faint)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title ?: "Fin de las series", Modifier.weight(1f), style = body(11.sp, FontWeight.SemiBold, lineHeight = 14.sp),
                color = RestRef02Tokens.Ink)
            dose?.let { Text(it, style = syne(12.sp), color = RestRef02Tokens.Muted) }
        }
        note?.let {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                androidx.compose.material3.Icon(com.example.kpkn.ui.components.icons.KpknLineIcons.Run, null, Modifier.size(13.dp), tint = RestRef02Tokens.Faint)
                Text(it, style = body(10.5.sp), color = RestRef02Tokens.Faint)
            }
        }
    }
}

/** Botón del prototipo dentro de una tarjeta (39 dp, radio 14) o a pantalla completa (46 dp, radio 23). */
@Composable
internal fun RestRef02Button(
    text: String,
    onClick: () -> Unit,
    primary: Boolean,
    modifier: Modifier = Modifier,
    // 39 en el prototipo; 44 de mínimo para la zona táctil (accesibilidad), con el mismo radio.
    height: Dp = 44.dp,
    radius: Dp = 14.dp,
    icon: ImageVector? = null,
    tag: String? = null,
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(radius)
    Row(
        modifier.heightIn(min = height).clip(shape)
            .then(if (primary) Modifier.background(RestRef02Tokens.Ink.copy(alpha = if (enabled) 1f else .35f))
            else Modifier.border(1.dp, Color(0x24FFFFFF), shape))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .then(if (tag != null) Modifier.testTag(tag) else Modifier)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp, Alignment.CenterHorizontally),
    ) {
        val tint = if (primary) RestRef02Tokens.OnInk else RestRef02Tokens.Ink
        icon?.let { Icon(it, null, Modifier.size(15.dp), tint = tint) }
        Text(text, style = body(12.sp, FontWeight.SemiBold), color = tint, textAlign = TextAlign.Center)
    }
}

/**
 * La pantalla completa del descanso (`.rest-fullscreen`): estado arriba, con el ejercicio en Syne y «minimizar a tarjeta»; el contenido
 * del panel en medio y el pie fijo con la acción principal. [compact] sigue a `.compact` cuando el alto disponible es el de 360 × 740.
 */
@Composable
internal fun RestRef02FullscreenScaffold(
    eyebrow: String,
    title: String,
    onMinimize: (() -> Unit)?,
    modifier: Modifier = Modifier,
    bottom: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.(compact: Boolean) -> Unit,
) {
    BoxWithConstraints(
        modifier.fillMaxSize().background(RestRef02Tokens.FullBackground)
            // Capa modal: se tragan los toques sin `clickable`, que fusionaría la semántica de toda la pantalla en un solo botón.
            .pointerInput(Unit) { detectTapGestures { } }
            .statusBarsPadding().navigationBarsPadding().testTag("rest-fullscreen"),
    ) {
        val compact = maxHeight < 720.dp
        val horizontal = if (compact) 22.dp else 24.dp
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = if (compact) 57.dp else 64.dp)
                    .padding(start = if (compact) 20.dp else 23.dp, end = if (compact) 20.dp else 23.dp,
                        top = if (compact) 5.dp else 8.dp, bottom = if (compact) 10.dp else 13.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(eyebrow, Modifier.padding(bottom = 7.dp).testTag("rest-full-eyebrow"), style = body(9.sp, letterSpacing = 1.1.sp),
                        color = RestRef02Tokens.Faint)
                    Text(title, style = syne(18.sp, lineHeight = 21.sp), color = RestRef02Tokens.Ink, modifier = Modifier.testTag("rest-full-title"))
                }
                if (onMinimize != null) {
                    Box(
                        Modifier.size(37.dp).clip(CircleShape).background(Color(0x07FFFFFF))
                            .clickable(role = Role.Button, onClick = onMinimize)
                            .semantics { contentDescription = "Minimizar a tarjeta" }.testTag("rest-minimize"),
                        contentAlignment = Alignment.Center,
                    ) { Icon(KpknLineIcons.ChevronDown, null, Modifier.size(18.dp), tint = RestRef02Tokens.Ink) }
                }
            }
            Column(
                Modifier.weight(1f).fillMaxWidth().padding(start = horizontal, end = horizontal, top = 2.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(if (compact) 9.dp else 12.dp),
            ) { content(compact) }
            if (bottom != null) {
                Column(
                    Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = if (compact) 7.dp else 10.dp,
                        bottom = if (compact) 23.dp else 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    content = bottom,
                )
            }
        }
    }
}

/** La acción principal del pie (`.v2-full-bottom .primary`): 46 dp, radio 16, crema. */
@Composable
internal fun RestRef02FullPrimary(text: String, onClick: () -> Unit, tag: String) =
    RestRef02Button(text, onClick, primary = true, modifier = Modifier.fillMaxWidth(), height = 46.dp, radius = 16.dp, tag = tag)

/**
 * El reloj a pantalla completa (`panel === 'clock'`): la nota, el eclipse de 266 (205), los controles, el sugerido, ÚLTIMA | SIGUIENTE
 * y «Jugar mientras descansas» al fondo.
 */
@Composable
internal fun ColumnScope.RestRef02FullClockPanel(
    compact: Boolean,
    remainingSeconds: Int,
    progress: Float,
    isPaused: Boolean,
    animationsOn: Boolean,
    onDecrease: () -> Unit,
    onTogglePause: () -> Unit,
    onIncrease: () -> Unit,
    suggestion: RestSuggestionLine?,
    onOpenSuggestion: (() -> Unit)?,
    lastSetText: String?,
    nextTitle: String?,
    nextDose: String?,
    onPlay: (() -> Unit)?,
    overtime: Boolean = remainingSeconds < 0,
    pauseEnabled: Boolean = true,
    decreaseEnabled: Boolean = true,
) {
    Text(
        if (overtime) "Tu descanso se ha cumplido." else "Una pausa antes de la siguiente.",
        Modifier.fillMaxWidth().padding(top = if (compact) 3.dp else 7.dp),
        style = body(if (compact) 10.sp else 12.sp), color = RestRef02Tokens.Faint, textAlign = TextAlign.Center,
    )
    RestEclipse(
        progress = if (overtime) 1f else progress,
        time = restRef02Clock(remainingSeconds),
        label = when { overtime -> "Tiempo extra"; isPaused -> "En pausa"; else -> "Descansando" },
        diameter = if (compact) 205.dp else 266.dp,
        timeSize = if (compact) 40.sp else 53.sp,
        labelSize = 12.sp,
        animationsOn = animationsOn,
        modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = if (compact) 2.dp else 10.dp).testTag("lr1g-aro"),
    )
    RestRef02Controls(isPaused, onDecrease, onTogglePause, onIncrease, height = 43.dp, pauseEnabled = pauseEnabled,
        decreaseEnabled = decreaseEnabled)
    suggestion?.let {
        RestRef02SuggestionRow(it.label, KpknSetFormat.rest(it.seconds), onOpenSuggestion?.takeIf { _ -> it.opensSheet },
            height = if (compact) 41.dp else 46.dp, labelSize = 12.sp, valueSize = 23.sp)
    }
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min).drawBehind {
            drawLine(Color(0x0EFFFFFF), Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
        }.padding(vertical = if (compact) 10.dp else 14.dp).testTag("rest-full-context"),
        horizontalArrangement = Arrangement.spacedBy(15.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text("ÚLTIMA", style = body(8.sp, letterSpacing = .8.sp), color = RestRef02Tokens.Faint)
            Text(lastSetText ?: "—", Modifier.padding(top = 7.dp), style = syne(14.sp, lineHeight = 18.sp), color = RestRef02Tokens.Ink)
        }
        Box(Modifier.width(1.dp).fillMaxHeight().background(Color(0x15FFFFFF)))
        Column(Modifier.weight(1f).padding(start = 1.dp)) {
            Text("SIGUIENTE", style = body(8.sp, letterSpacing = .8.sp), color = RestRef02Tokens.Faint)
            Text(nextTitle ?: "Fin de las series", Modifier.padding(top = 7.dp), style = syne(14.sp, lineHeight = 18.sp),
                color = RestRef02Tokens.Ink)
            nextDose?.let { Text(it, Modifier.padding(top = 5.dp), style = body(10.sp), color = RestRef02Tokens.Muted) }
        }
    }
    Spacer(Modifier.weight(1f))
    if (onPlay != null) {
        val shape = RoundedCornerShape(16.dp)
        Row(
            Modifier.fillMaxWidth().heightIn(min = if (compact) 41.dp else 47.dp).clip(shape).background(Color(0xFF181818))
                .border(1.dp, Color(0x17FFFFFF), shape).clickable(role = Role.Button, onClick = onPlay).testTag("rest-play-games"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        ) {
            Icon(KpknLineIcons.Play, null, Modifier.size(17.dp), tint = RestRef02Tokens.Ink)
            Text("Jugar mientras descansas", style = body(if (compact) 12.sp else 13.sp, FontWeight.Medium), color = RestRef02Tokens.Ink)
        }
    }
}

/**
 * El descanso dentro de la tarjeta (`restCard`, panel `clock`): kicker con expandir, eclipse de 174 (153), controles, SIGUIENTE y el
 * sugerido al fondo. El pie («Jugar mientras descansas») lo pone quien llama.
 */
@Composable
internal fun ColumnScope.RestRef02CardClockPanel(
    compact: Boolean,
    kicker: String,
    remainingSeconds: Int,
    progress: Float,
    isPaused: Boolean,
    animationsOn: Boolean,
    onExpand: (() -> Unit)?,
    onDecrease: () -> Unit,
    onTogglePause: () -> Unit,
    onIncrease: () -> Unit,
    nextTitle: String?,
    nextDose: String?,
    suggestion: RestSuggestionLine?,
    onOpenSuggestion: (() -> Unit)?,
    pauseEnabled: Boolean = true,
    decreaseEnabled: Boolean = true,
    increaseEnabled: Boolean = true,
    nextNote: String? = null,
) {
    val overtime = remainingSeconds < 0
    RestRef02Kicker(kicker, compact, onExpand)
    RestEclipse(
        progress = if (overtime) 1f else progress,
        time = restRef02Clock(remainingSeconds),
        label = when { overtime -> "Tiempo extra"; isPaused -> "En pausa"; else -> "Descansando" },
        diameter = if (compact) 153.dp else 174.dp,
        timeSize = if (compact) 30.sp else 34.sp,
        labelSize = 9.sp,
        animationsOn = animationsOn,
        modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = if (compact) 0.dp else 4.dp),
    )
    RestRef02Controls(isPaused, onDecrease, onTogglePause, onIncrease, height = if (compact) 34.dp else 37.dp,
        modifier = Modifier.padding(top = 3.dp, bottom = if (compact) 5.dp else 8.dp), pauseEnabled = pauseEnabled,
        decreaseEnabled = decreaseEnabled, increaseEnabled = increaseEnabled)
    RestRef02NextRow(nextTitle, nextDose, compact, nextNote)
    Spacer(Modifier.weight(1f))
    suggestion?.let {
        RestRef02SuggestionRow(it.label, KpknSetFormat.rest(it.seconds), onOpenSuggestion?.takeIf { _ -> it.opensSheet },
            height = if (compact) 35.dp else 38.dp, labelSize = 10.sp, valueSize = 19.sp)
    }
}

/** `.v2-feedback-clock`: el eclipse diminuto, «1:32» en Syne 30, «de 2:00 · el reloj sigue» y el sugerido como enlace. */
@Composable
internal fun RestRef02FeedbackClock(
    remainingSeconds: Int,
    totalSeconds: Int,
    progress: Float,
    animationsOn: Boolean,
    suggestedSeconds: Int?,
    onOpenSuggestion: (() -> Unit)?,
) {
    Row(
        Modifier.fillMaxWidth().drawBehind {
            drawLine(Color(0x12FFFFFF), Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
        }.padding(bottom = 12.dp).testTag("rest-feedback-clock"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        RestEclipse(progress, restRef02Clock(remainingSeconds), "", 47.dp, 1.sp, 1.sp, animationsOn, showCenter = false)
        Column(Modifier.weight(1f)) {
            Text(restRef02Clock(remainingSeconds), style = syne(30.sp, lineHeight = 32.sp), color = RestRef02Tokens.Ink)
            Text("de ${KpknSetFormat.rest(totalSeconds)} · el reloj sigue", style = body(10.sp), color = RestRef02Tokens.Faint)
        }
        if (suggestedSeconds != null && onOpenSuggestion != null) {
            Box(Modifier.heightIn(min = 40.dp).clickable(role = Role.Button, onClick = onOpenSuggestion), contentAlignment = Alignment.Center) {
                Text("${KpknSetFormat.rest(suggestedSeconds)} sugerido", style = body(10.sp), color = RestRef02Tokens.Faint)
            }
        }
    }
}

/**
 * `.v2-feedback`: «EJERCICIO TERMINADO» y el ejercicio en Syne con la llama en su círculo; «Calidad técnica 7/10» con el deslizador de
 * 1 a 10 («Por mejorar» · «Muy buena»); la fila «Molestias › Sin seleccionar» (abre la hoja de molestias) y las molestias previas de la
 * misma articulación con «Sigue» / «Se resolvió». [wide] es la versión de la pantalla completa.
 */
@Composable
internal fun RestRef02FeedbackBody(
    feedbackExercises: List<Exercise>,
    draft: PostExerciseFeedbackDraft,
    linked: List<LinkedDiscomfort>,
    wide: Boolean,
    compact: Boolean,
    onOpenDiscomforts: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val single = feedbackExercises.size == 1
    val name = if (single) displayWorkoutExerciseName(feedbackExercises.first()) else "Superserie"
    Column(modifier.fillMaxWidth().padding(vertical = 8.dp).testTag("rest-feedback-body")) {
        Row(
            Modifier.fillMaxWidth().padding(top = if (wide) 17.dp else 7.dp, bottom = when { compact -> 10.dp; wide -> 24.dp; else -> 15.dp }),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(if (single) "EJERCICIO TERMINADO" else "SUPERSERIE TERMINADA", style = body(8.sp, letterSpacing = 1.sp),
                    color = RestRef02Tokens.Faint)
                Text(name, Modifier.padding(top = 8.dp), style = syne(if (compact) 22.sp else 23.sp, lineHeight = 25.sp),
                    color = RestRef02Tokens.Ink)
            }
            Box(Modifier.size(45.dp).clip(CircleShape).border(1.dp, Color(0x1EFFFFFF), CircleShape), contentAlignment = Alignment.Center) {
                Icon(KpknLineIcons.Flame, null, Modifier.size(24.dp), tint = RestRef02Tokens.Ink)
            }
        }
        feedbackExercises.forEach { exercise ->
            val value = draft.technical(exercise.id)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (single) "Calidad técnica" else "Calidad técnica · ${displayWorkoutExerciseName(exercise)}",
                    Modifier.weight(1f), style = body(if (wide) 13.sp else 11.sp, FontWeight.Medium), color = RestRef02Tokens.Ink,
                )
                Text(
                    buildAnnotatedString {
                        append(value.toString())
                        withStyle(SpanStyle(fontFamily = WizardFonts.body, fontSize = 10.sp, fontWeight = FontWeight.Normal,
                            color = RestRef02Tokens.Faint)) { append("/10") }
                    },
                    style = syne(when { wide -> 41.sp; compact -> 25.sp; else -> 27.sp }),
                    color = RestRef02Tokens.Ink,
                    modifier = Modifier.testTag("rest-feedback-quality-${exercise.id}"),
                )
            }
            RestQualityRange(
                value = value,
                onValueChange = { draft.setTechnical(exercise.id, it) },
                modifier = Modifier.padding(top = 8.dp).testTag("rest-feedback-slider-${exercise.id}"),
            )
            Row(Modifier.fillMaxWidth()) {
                Text("Por mejorar", Modifier.weight(1f), style = body(9.sp), color = RestRef02Tokens.Faint)
                Text("Muy buena", style = body(9.sp), color = RestRef02Tokens.Faint)
            }
        }
        val labels = draft.discomfortIds.map { com.example.kpkn.data.models.DISCOMFORT_CATALOG_BY_ID[it]?.label ?: discomfortKnowledge(it)?.title ?: it }
        Row(
            Modifier.fillMaxWidth().padding(top = when { compact -> 10.dp; wide -> 22.dp; else -> 16.dp })
                .drawBehind {
                    drawLine(Color(0x10FFFFFF), Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx())
                    drawLine(Color(0x10FFFFFF), Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
                }
                .heightIn(min = 46.dp).clickable(role = Role.Button, onClick = onOpenDiscomforts).padding(horizontal = 4.dp)
                .testTag("rest-feedback-discomforts"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Molestias", style = body(11.sp, FontWeight.Medium), color = RestRef02Tokens.Ink)
            Spacer(Modifier.weight(1f))
            Text(if (labels.isEmpty()) "Sin seleccionar" else labels.joinToString(", "), Modifier.widthIn(max = 160.dp),
                style = body(10.sp), color = RestRef02Tokens.Muted, textAlign = TextAlign.End)
            Icon(KpknLineIcons.ChevronRight, null, Modifier.size(12.dp), tint = RestRef02Tokens.Muted)
        }
        linked.forEach { item ->
            val stillPresent = draft.stillPresent(item.id)
            Column(Modifier.fillMaxWidth().padding(top = when { compact -> 8.dp; wide -> 22.dp; else -> 14.dp })) {
                Text("${item.label} · reportada antes", style = body(10.sp), color = RestRef02Tokens.Faint)
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RestLinkedChoice("Sigue", stillPresent, wide, Modifier.weight(1f)) { draft.setStillPresent(item.id, true) }
                    RestLinkedChoice("Se resolvió", !stillPresent, wide, Modifier.weight(1f)) { draft.setStillPresent(item.id, false) }
                }
            }
        }
        if (wide && !compact) {
            Text("El reloj continúa mientras registras cómo salió el ejercicio.", Modifier.padding(vertical = 12.dp),
                style = body(11.sp, lineHeight = 17.sp), color = RestRef02Tokens.Muted)
        }
    }
}

/**
 * `.v2-quality-range`: el `input[type=range]` de 1 a 10 con `accent-color` crema: pista fina, tramo hecho en crema y el pulgar redondo.
 * Se toca o se arrastra; TalkBack lo ajusta con la acción de progreso.
 */
@Composable
private fun RestQualityRange(value: Int, onValueChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    val change by rememberUpdatedState(onValueChange)
    fun valueAt(x: Float, width: Int): Int =
        (1 + kotlin.math.round((x / width.coerceAtLeast(1)).coerceIn(0f, 1f) * 9f).toInt()).coerceIn(1, 10)
    Canvas(
        modifier.fillMaxWidth().height(28.dp)
            .pointerInput(Unit) { detectTapGestures { change(valueAt(it.x, size.width)) } }
            .pointerInput(Unit) { detectHorizontalDragGestures { pointer, _ -> pointer.consume(); change(valueAt(pointer.position.x, size.width)) } }
            .semantics {
                contentDescription = "Calidad técnica"
                stateDescription = "$value de 10"
                progressBarRangeInfo = ProgressBarRangeInfo(value.toFloat(), 1f..10f, 8)
                setProgress { target -> change(kotlin.math.round(target).toInt().coerceIn(1, 10)); true }
            },
    ) {
        val track = 4.dp.toPx()
        val thumb = 8.dp.toPx()
        val y = size.height / 2f
        val start = thumb
        val end = size.width - thumb
        val x = start + (end - start) * (value - 1) / 9f
        drawLine(Color(0x33FFFFFF), Offset(start, y), Offset(end, y), track, StrokeCap.Round)
        drawLine(RestRef02Tokens.Ink, Offset(start, y), Offset(x, y), track, StrokeCap.Round)
        drawCircle(RestRef02Tokens.Ink, thumb, Offset(x, y))
    }
}

@Composable
private fun RestLinkedChoice(text: String, selected: Boolean, wide: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(9.dp)
    Box(
        modifier.heightIn(min = if (wide) 43.dp else 32.dp).clip(shape)
            .then(if (selected) Modifier.background(RestRef02Tokens.Ink) else Modifier.border(1.dp, Color(0x18FFFFFF), shape))
            .clickable(role = Role.Button, onClick = onClick).semantics { this.selected = selected },
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = body(10.sp, FontWeight.SemiBold), color = if (selected) RestRef02Tokens.OnInk else RestRef02Tokens.Ink)
    }
}

/** `.v2-split-actions`: «Ahora no» y «Guardar feedback». */
@Composable
internal fun RestRef02FeedbackActions(onDefer: () -> Unit, onSave: () -> Unit, inCard: Boolean, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val height = if (inCard) 39.dp else 46.dp
        val radius = if (inCard) 14.dp else 23.dp
        RestRef02Button("Ahora no", onDefer, primary = false, modifier = Modifier.weight(1f), height = height, radius = radius,
            tag = "feedback-defer")
        RestRef02Button("Guardar feedback", onSave, primary = true, modifier = Modifier.weight(1f), height = height, radius = radius,
            tag = "feedback-save")
    }
}
