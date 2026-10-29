package com.example.kpkn.screens.workout.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.animation.ExitTransition
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.example.kpkn.data.workout.RestGameRecordStore
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.screens.workout.RestGameEngine
import com.example.kpkn.screens.workout.RestNextSetSummary
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknSetFormat
import kotlin.math.abs
import kotlin.random.Random

internal enum class RestArcadeMode(val label: String, val hint: String, val key: String) {
    RUNNER("Salta las mancuernas", "Salto con altura variable, doble salto con tiza, «¡Justo!» al rozar y racha que multiplica.", "runner"),
    FLAPPY("Entre las pesas", "Impulsos suaves y huecos que respiran contigo: el fondo marca inhala/exhala. Pasar por el centro encadena «Perfecto».", "flappy"),
    SHOOTER("Escuadrón retro", "Nave que sigues con el dedo sin taparla, oleadas con patrón, jefe cada cuatro y potenciadores.", "shooter"),
}

private val ArcadeInk = GameInk
private val ArcadeBody = TextStyle(fontFamily = WizardFonts.body)
private val ArcadeSyne = TextStyle(fontFamily = WizardFonts.display, fontWeight = FontWeight.Bold)

/**
 * Juegos del descanso (iteración 03, aprobada). Arriba, siempre, el reloj del descanso (`.gtop`: anillo, tiempo, «siguiente», −15 /
 * pausa / +15); el descanso manda: en pausa congela la partida y al llegar a cero la congela con «Volver a la serie» o «Seguir jugando ·
 * cuenta como extra». El selector enseña cada juego con su vista previa animada, su descripción y «Récord N · 3 vidas».
 */
@Composable
internal fun RestArcade(
    remainingSeconds: Int, totalSeconds: Int, nextSet: RestNextSetSummary?,
    onBack: () -> Unit, onSkip: () -> Unit, modifier: Modifier = Modifier,
    recordStore: RestGameRecordStore? = null,
    @Suppress("UNUSED_PARAMETER") runner: RestGameEngine? = null,
    running: Boolean = true, compact: Boolean = false,
    isPaused: Boolean = !running,
    onDecrease: (() -> Unit)? = null,
    onTogglePause: (() -> Unit)? = null,
    onIncrease: (() -> Unit)? = null,
) {
    var mode by remember { mutableStateOf<RestArcadeMode?>(null) }
    var keepPlaying by remember { mutableStateOf(false) }
    val over = remainingSeconds <= 0
    val nextText = nextSet?.let { next -> (next.exerciseName?.let { "$it · " } ?: "") + next.headline() }
    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 9.dp)) {
        RestGameTop(
            remainingSeconds = remainingSeconds,
            totalSeconds = totalSeconds,
            subtitle = when {
                over -> "Descanso cumplido · tiempo extra"
                isPaused -> "Descanso en pausa · el juego también"
                nextText != null -> "Descanso · siguiente: $nextText"
                else -> "Descanso"
            },
            isPaused = isPaused,
            onBack = { if (mode == null) onBack() else mode = null },
            onDecrease = onDecrease?.takeIf { !over },
            onTogglePause = onTogglePause,
            onIncrease = onIncrease?.takeIf { !over },
            compact = compact,
        )
        // Selector ↔ partida: el contenido nuevo se funde sobre el anterior (nada aparece de golpe); con movimiento reducido, corte.
        val reducedMotion = wizardReducedMotion()
        AnimatedContent(
            targetState = mode,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            transitionSpec = { liveContentSwap(reducedMotion) },
            label = "rest-arcade-mode",
        ) { chosen ->
            if (chosen == null) {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 9.dp)) {
                    RestGamePicker(recordStore, compact, Modifier.weight(1f)) { mode = it }
                    if (compact) RestRef02Button("Volver al descanso", onBack, primary = false, modifier = Modifier.fillMaxWidth(), tag = "arcade-return-rest")
                }
            } else {
                key(chosen) {
                    RestGamePlay(
                        mode = chosen,
                        recordStore = recordStore,
                        paused = isPaused,
                        restOver = over && !keepPlaying,
                        nextText = nextText,
                        onBackToSet = onSkip,
                        onKeepPlaying = { keepPlaying = true },
                        onClose = { mode = null },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

/** `.gtop`: el reloj del descanso, que manda sobre el juego. */
@Composable
private fun RestGameTop(
    remainingSeconds: Int,
    totalSeconds: Int,
    subtitle: String,
    isPaused: Boolean,
    onBack: () -> Unit,
    onDecrease: (() -> Unit)?,
    onTogglePause: (() -> Unit)?,
    onIncrease: (() -> Unit)?,
    compact: Boolean = false,
) {
    if (compact) {
        RestGameTopSlim(remainingSeconds, totalSeconds, isPaused, onBack, onDecrease, onTogglePause, onIncrease)
        return
    }
    val shape = RoundedCornerShape(29.dp)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 58.dp).clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xC2333333), Color(0xB3212121))))
            .border(1.dp, Color(0x24FFFFFF), shape)
            .semantics { contentDescription = "Descanso" }
            .padding(start = 2.dp, end = 6.dp).testTag("arcade-rest-top"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(width = 30.dp, height = 44.dp).clickable(role = Role.Button, onClick = onBack)
            .semantics { contentDescription = "Volver" }.testTag("arcade-back"), contentAlignment = Alignment.Center) {
            Icon(KpknLineIcons.ChevronLeft, null, Modifier.size(15.dp), tint = ArcadeInk)
        }
        val fraction = if (remainingSeconds <= 0) 0f else (remainingSeconds.toFloat() / totalSeconds.coerceAtLeast(1)).coerceIn(0f, 1f)
        Canvas(Modifier.size(44.dp)) {
            val r = 19.dp.toPx()
            val stroke = 3.dp.toPx()
            val topLeft = Offset(center.x - r, center.y - r)
            drawCircle(Color(0x14FFFFFF), r, center, style = Stroke(stroke))
            drawArc(ArcadeInk, -90f, 360f * fraction, false, topLeft, Size(r * 2, r * 2), style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Column(Modifier.weight(1f)) {
            Text((if (remainingSeconds < 0) "+" else "") + KpknSetFormat.rest(abs(remainingSeconds)),
                style = ArcadeSyne.copy(fontSize = 18.sp, lineHeight = 23.sp, fontFeatureSettings = "'tnum' 1"), color = ArcadeInk,
                maxLines = 1, softWrap = false, modifier = Modifier.testTag("arcade-rest-clock"))
            // `.gtop small` del prototipo: una sola línea con puntos suspensivos («Descanso · siguiente: Se…»).
            Text(subtitle, style = ArcadeBody.copy(fontSize = 10.sp, lineHeight = 13.sp), color = LiveSheetTokens.Faint,
                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
        GameTopButton("−15", "Quitar 15 segundos", onDecrease, "arcade-rest-minus")
        GameTopButton(null, if (isPaused) "Reanudar descanso" else "Pausar descanso", onTogglePause, "arcade-rest-pause",
            icon = if (isPaused) KpknLineIcons.Play else KpknLineIcons.Pause)
        GameTopButton("+15", "Añadir 15 segundos", onIncrease, "arcade-rest-plus")
    }
}

/**
 * El reloj del descanso dentro de la tarjeta minimizada: una fila fina sin píldora (‹, anillo, tiempo, −15 · pausa · +15) para que el
 * juego se quede con casi toda la tarjeta. Las mismas etiquetas y acciones que la píldora de pantalla completa.
 */
@Composable
private fun RestGameTopSlim(
    remainingSeconds: Int,
    totalSeconds: Int,
    isPaused: Boolean,
    onBack: () -> Unit,
    onDecrease: (() -> Unit)?,
    onTogglePause: (() -> Unit)?,
    onIncrease: (() -> Unit)?,
) {
    Row(
        Modifier.fillMaxWidth().height(44.dp).semantics { contentDescription = "Descanso" }.testTag("arcade-rest-top"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(width = 32.dp, height = 44.dp).clickable(role = Role.Button, onClick = onBack)
            .semantics { contentDescription = "Volver" }.testTag("arcade-back"), contentAlignment = Alignment.Center) {
            Icon(KpknLineIcons.ChevronLeft, null, Modifier.size(15.dp), tint = ArcadeInk)
        }
        val fraction = if (remainingSeconds <= 0) 0f else (remainingSeconds.toFloat() / totalSeconds.coerceAtLeast(1)).coerceIn(0f, 1f)
        Canvas(Modifier.size(22.dp)) {
            val r = 9.dp.toPx()
            val stroke = 2.dp.toPx()
            drawCircle(Color(0x14FFFFFF), r, center, style = Stroke(stroke))
            drawArc(ArcadeInk, -90f, 360f * fraction, false, Offset(center.x - r, center.y - r), Size(r * 2, r * 2),
                style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Text((if (remainingSeconds < 0) "+" else "") + KpknSetFormat.rest(abs(remainingSeconds)), Modifier.weight(1f).testTag("arcade-rest-clock"),
            style = ArcadeSyne.copy(fontSize = 16.sp, lineHeight = 20.sp, fontFeatureSettings = "'tnum' 1"),
            color = if (isPaused) LiveSheetTokens.Muted else ArcadeInk, maxLines = 1, softWrap = false)
        GameTopButton("−15", "Quitar 15 segundos", onDecrease, "arcade-rest-minus", touch = 44.dp, visual = 32.dp)
        GameTopButton(null, if (isPaused) "Reanudar descanso" else "Pausar descanso", onTogglePause, "arcade-rest-pause",
            icon = if (isPaused) KpknLineIcons.Play else KpknLineIcons.Pause, touch = 44.dp, visual = 32.dp)
        GameTopButton("+15", "Añadir 15 segundos", onIncrease, "arcade-rest-plus", touch = 44.dp, visual = 32.dp)
    }
}

@Composable
private fun GameTopButton(text: String?, description: String, onClick: (() -> Unit)?, tag: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null, touch: androidx.compose.ui.unit.Dp = 44.dp,
    visual: androidx.compose.ui.unit.Dp = 36.dp) {
    Box(
        Modifier.size(touch).clickable(enabled = onClick != null, role = Role.Button) { onClick?.invoke() }
            .semantics { contentDescription = description }.testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(visual).clip(CircleShape).background(Color(0x0DFFFFFF)), contentAlignment = Alignment.Center) {
            val ink = ArcadeInk.copy(alpha = if (onClick != null) 1f else .35f)
            if (icon != null) Icon(icon, null, Modifier.size(13.dp), tint = ink)
            else Text(text.orEmpty(), style = ArcadeBody.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold), color = ink)
        }
    }
}

/** `.picker`: tres tarjetas con la vista previa animada, el nombre, la descripción y «Récord N · 3 vidas»; el pie recuerda la regla. */
@Composable
private fun RestGamePicker(recordStore: RestGameRecordStore?, compact: Boolean, modifier: Modifier, onPick: (RestArcadeMode) -> Unit) {
    val reduced = wizardReducedMotion()
    var clock by remember { mutableFloatStateOf(0f) }
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner, reduced) {
        if (reduced) return@LaunchedEffect
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var last = 0L
            while (true) withInfiniteAnimationFrameNanos { now ->
                if (last != 0L) clock += (now - last) / 1_000_000_000f
                last = now
            }
        }
    }
    Column(modifier.fillMaxWidth().padding(top = 2.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        RestArcadeMode.entries.forEach { game ->
            val shape = RoundedCornerShape(22.dp)
            Row(
                Modifier.fillMaxWidth().clip(shape).background(Color(0xFF111111)).border(1.dp, Color(0x10FFFFFF), shape)
                    .clickable(role = Role.Button) { onPick(game) }.padding(if (compact) 10.dp else 13.dp)
                    .then(if (compact) Modifier.semantics { contentDescription = "${game.label}. ${game.hint}" } else Modifier)
                    .testTag("rest-game-${game.name.lowercase()}"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Canvas(Modifier.size(if (compact) 52.dp else 84.dp).clip(RoundedCornerShape(16.dp))) { drawGamePreview(game, clock) }
                Column(Modifier.weight(1f)) {
                    Text(game.label, style = ArcadeSyne.copy(fontSize = 16.sp, lineHeight = 21.sp), color = ArcadeInk)
                    // En la tarjeta minimizada solo caben el nombre y el récord; la descripción la lee el lector de pantalla.
                    if (!compact) Text(game.hint, Modifier.padding(top = 5.dp), style = ArcadeBody.copy(fontSize = 11.sp, lineHeight = 15.sp),
                        color = LiveSheetTokens.Muted)
                    Text("Récord ${restGameBest(recordStore, game).formatEs()} · 3 vidas", Modifier.padding(top = 6.dp),
                        style = ArcadeBody.copy(fontSize = 10.sp), color = LiveSheetTokens.Faint)
                }
            }
        }
        Box(Modifier.weight(1f))
        if (!compact) Text("El descanso manda: al llegar a cero el juego se congela y vuelves a la serie.", Modifier.fillMaxWidth().padding(bottom = 8.dp),
            style = ArcadeBody.copy(fontSize = 10.5.sp, lineHeight = 15.sp), color = LiveSheetTokens.Faint, textAlign = TextAlign.Center)
    }
}

/** Una partida: el lienzo a todo el ancho, el HUD (puntos o récord y las vidas), la pista inicial y las capas de pausa y de fin. */
@Composable
private fun RestGamePlay(
    mode: RestArcadeMode,
    recordStore: RestGameRecordStore?,
    paused: Boolean,
    restOver: Boolean,
    nextText: String?,
    onBackToSet: () -> Unit,
    onKeepPlaying: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier,
) {
    val reduced = wizardReducedMotion()
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    var round by remember { mutableIntStateOf(0) }
    var areaPx by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    val world = remember(mode, round, areaPx) {
        if (areaPx.width <= 0 || areaPx.height <= 0) null
        else newArcadeWorld(mode, areaPx.width / density.density, areaPx.height / density.density, restGameBest(recordStore, mode), reduced)
    }
    var finished by remember(mode, round) { mutableStateOf(false) }
    var finalScore by remember(mode, round) { mutableIntStateOf(0) }
    var touched by remember(mode, round) { mutableStateOf(false) }
    var frame by remember { mutableLongStateOf(0L) }
    val frozen = paused || restOver
    val frozenNow by rememberUpdatedState(frozen)
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(world, owner) {
        val game = world ?: return@LaunchedEffect
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var last = 0L
            while (!finished) withInfiniteAnimationFrameNanos { now ->
                val dt = if (last == 0L) 0f else ((now - last) / 1_000_000_000f).coerceAtMost(.033f)
                last = now
                if (!frozenNow && dt > 0f && game.update(dt)) {
                    finished = true
                    finalScore = game.score
                    restGameSubmit(recordStore, mode, game.score)
                }
                frame = now
            }
        }
    }
    Box(
        modifier.clip(RoundedCornerShape(18.dp)).background(Color.Black).border(1.dp, Color(0x12FFFFFF), RoundedCornerShape(18.dp))
            .onSizeChanged { areaPx = it },
    ) {
        Canvas(
            Modifier.fillMaxSize().testTag("rest-game-canvas-${mode.key}")
                .semantics { contentDescription = "${mode.label}. ${world?.hint.orEmpty()}" }
                .pointerInput(world, frozen, finished) {
                    val game = world ?: return@pointerInput
                    if (frozen || finished) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        touched = true
                        game.press(down.position.x / density.density, down.position.y / density.density)
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            game.move(change.position.x / density.density, change.position.y / density.density)
                            change.consume()
                        }
                        game.release()
                    }
                },
        ) {
            @Suppress("UNUSED_VARIABLE") val tick = frame
            val game = world ?: return@Canvas
            val shake = game.shake
            translate(if (shake > 0f) (Random.nextFloat() * 2f - 1f) * shake * density.density else 0f,
                if (shake > 0f) (Random.nextFloat() * 2f - 1f) * shake * density.density else 0f) {
                scaleToDp { game.draw(this, textMeasurer) }
            }
            if (frozen && !finished) drawRect(Color(0xAA000000))
        }
        world?.let { game ->
            @Suppress("UNUSED_VARIABLE") val tick = frame
            // `.ghud`: puntos (y la racha, el jefe… o el récord) a la izquierda y las tres vidas a la derecha.
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).testTag("rest-game-hud"),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(game.score.formatEs(), style = ArcadeSyne.copy(fontSize = 16.sp, lineHeight = 21.sp), color = ArcadeInk)
                    Text(game.extra().ifBlank { "Récord ${game.best.formatEs()}" }, style = ArcadeBody.copy(fontSize = 11.sp), color = LiveSheetTokens.Faint)
                }
                Row(Modifier.semantics { contentDescription = "${game.lives.coerceAtLeast(0)} vidas" }, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(3) { i ->
                        Box(Modifier.size(8.dp).clip(CircleShape).background(if (i < game.lives) ArcadeInk else Color(0x22FFFFFF)))
                    }
                }
            }
            // `.gmsg`: la pista hasta el primer toque y «En pausa» mientras el descanso está parado.
            val message = when {
                paused && !finished -> "En pausa" to "El descanso está en pausa; el juego también."
                !touched && !finished && !restOver -> null to game.hint
                else -> null
            }
            // La pista y «En pausa» se funden al cambiar; sin mensaje no se pinta nada.
            Crossfade(
                targetState = message,
                modifier = Modifier.align(Alignment.Center),
                animationSpec = liveSpec(reduced, 200),
                label = "rest-game-message",
            ) { shown ->
                shown?.let { (title, body) ->
                    Column(Modifier.padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        title?.let { Text(it, style = ArcadeSyne.copy(fontSize = 18.sp, lineHeight = 23.sp), color = ArcadeInk) }
                        Text(body, Modifier.padding(top = 6.dp), style = ArcadeBody.copy(fontSize = 12.sp, lineHeight = 17.sp), color = LiveSheetTokens.Muted,
                            textAlign = TextAlign.Center)
                    }
                }
            }
        }
        // Las capas de «fin de partida» y «descanso terminado» se funden por encima de la partida en lugar de saltar.
        AnimatedVisibility(visible = finished, enter = liveFadeIn(reduced), exit = ExitTransition.None) {
            GameOverlay(
                eyebrow = "FIN DE LA PARTIDA",
                title = finalScore.formatEs(),
                body = "Récord ${restGameBest(recordStore, mode).formatEs()}. El descanso sigue corriendo arriba; reiniciar la partida no lo toca.",
                primary = "Otra partida" to { round++; Unit },
                secondary = "Cerrar juego" to onClose,
                tag = "rest-game-over",
            )
        }
        AnimatedVisibility(visible = restOver && !finished, enter = liveFadeIn(reduced), exit = liveFadeOut(reduced)) {
            GameOverlay(
                eyebrow = "DESCANSO TERMINADO",
                title = "Siguiente serie",
                body = listOfNotNull(nextText, "Tu partida queda congelada con ${(world?.score ?: 0).formatEs()} puntos.").joinToString(". "),
                primary = "Volver a la serie" to onBackToSet,
                secondary = "Seguir jugando · cuenta como extra" to onKeepPlaying,
                tag = "rest-game-rest-over",
                primaryIcon = KpknLineIcons.Play,
            )
        }
    }
}

/** `.gover`: velo con desenfoque, el rótulo, la cifra o el título en Syne, el texto y las dos salidas. */
@Composable
private fun GameOverlay(
    eyebrow: String,
    title: String,
    body: String,
    primary: Pair<String, () -> Unit>,
    secondary: Pair<String, () -> Unit>,
    tag: String,
    primaryIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    Column(
        Modifier.fillMaxSize().background(Color(0xCC000000))
            .clickable(remember { MutableInteractionSource() }, indication = null) {}
            .padding(horizontal = 30.dp).testTag(tag),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(eyebrow, style = ArcadeBody.copy(fontSize = 9.sp, letterSpacing = 1.sp), color = LiveSheetTokens.Faint)
        Text(title, style = ArcadeSyne.copy(fontSize = 25.sp, lineHeight = 33.sp), color = ArcadeInk, textAlign = TextAlign.Center)
        Text(body, Modifier.padding(bottom = 10.dp), style = ArcadeBody.copy(fontSize = 12.5.sp, lineHeight = 18.sp), color = LiveSheetTokens.Muted,
            textAlign = TextAlign.Center)
        RestRef02Button(primary.first, primary.second, primary = true, modifier = Modifier.width(220.dp), icon = primaryIcon, tag = "$tag-primary")
        RestRef02Button(secondary.first, secondary.second, primary = false, modifier = Modifier.width(220.dp), tag = "$tag-secondary")
    }
}

private fun restGameBest(store: RestGameRecordStore?, mode: RestArcadeMode): Int = store?.best(mode.key) ?: 0

private fun restGameSubmit(store: RestGameRecordStore?, mode: RestArcadeMode, score: Int) {
    store?.submit(mode.key, score)
}

private fun Int.formatEs(): String = java.text.NumberFormat.getIntegerInstance(java.util.Locale.forLanguageTag("es")).format(this)
