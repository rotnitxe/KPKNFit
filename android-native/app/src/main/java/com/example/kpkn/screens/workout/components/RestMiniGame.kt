package com.example.kpkn.screens.workout.components

import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.example.kpkn.data.workout.RestGameRecordStore
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.screens.workout.RestClockMath
import com.example.kpkn.screens.workout.RestHazard
import com.example.kpkn.screens.workout.RestSupplement
import com.example.kpkn.screens.workout.RestGameEngine
import com.example.kpkn.screens.workout.RestNextSetSummary
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknCompactPill
import com.example.kpkn.ui.design.KpknIconButton
import com.example.kpkn.ui.design.KpknSetFormat
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknType
import kotlin.math.PI
import kotlin.math.cos

/*
 * El minijuego del descanso (LV6, F8): un corredor salta mancuernas que llegan desde la derecha. Vive DENTRO de la misma superficie del
 * descanso (la pantalla completa o la tarjeta, dentro de su altura): el descanso sigue corriendo arriba y, al llegar a 0, quien lo
 * muestra deja de componerlo y el juego se cierra solo. El juego no toca el estado del descanso.
 */

private val Ink = Color(0xFFF2EEE6)
private val HeadFill = Color(0xFF0B0B0B)
private val DumbbellPlate = Color(0xFFF49A6E)
private val HitRed = Color(0xFFEF6F64)

/** Las piernas del corredor alternan cada 0,28 s (F8). */
private const val GameLegPeriod = 0.28f

/**
 * Escala con la que se dibuja el mundo del juego. La física va en dp lógicos (el salto, la velocidad y los huecos no cambian), pero en
 * un campo alto, como el de la pantalla completa, todo se dibuja más grande para llenarlo: hasta 1,7 veces. El campo de la tarjeta
 * (menos de 300 dp de alto) se dibuja a escala 1.
 */
private fun gameScale(heightDp: Float): Float = (heightDp / 300f).coerceIn(1f, 1.7f)

/** La caja de la partida: el motor (que se puede inyectar, para las hojas de render) y el récord. */
@Composable
private fun rememberRestGame(engine: RestGameEngine?, recordStore: RestGameRecordStore?): Pair<RestGameEngine, RestGameRecordStore> {
    val context = LocalContext.current
    val store = recordStore ?: remember { RestGameRecordStore.from(context) }
    val game = engine ?: remember(store) { RestGameEngine(initialBest = store.best()) }
    return game to store
}

/**
 * El campo de juego: dibuja el motor y lo mueve cuadro a cuadro con el reloj de la pantalla (60 fps, sin librerías). Tocar = saltar.
 * Pausa solo cuando la app pasa a segundo plano (el bucle vive dentro de `repeatOnLifecycle`) y, con [running] en falso, no corre
 * (las hojas de render). Guarda el récord en [store] cada vez que sube.
 */
@Composable
internal fun RestGameArena(
    engine: RestGameEngine,
    store: RestGameRecordStore?,
    modifier: Modifier = Modifier,
    running: Boolean = true,
) {
    val reduced = wizardReducedMotion()
    val density = LocalDensity.current.density
    var frame by remember { mutableLongStateOf(0L) }
    var shownScore by remember(engine) { mutableIntStateOf(engine.score) }
    var shownBest by remember(engine) { mutableIntStateOf(engine.best) }
    var shownLives by remember(engine) { mutableIntStateOf(engine.lives) }
    var shownPower by remember(engine) { mutableStateOf(engine.power) }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(running, lifecycleOwner, engine) {
        if (!running) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var last = 0L
            while (!engine.ended) {
                withInfiniteAnimationFrameNanos { now ->
                    val dt = if (last == 0L) 0f else ((now - last) / 1_000_000_000.0).toFloat()
                    last = now
                    engine.step(dt)
                    if (shownLives != engine.lives) shownLives = engine.lives
                    if (shownPower != engine.power) shownPower = engine.power
                    if (engine.bestChanged) {
                        store?.submit(engine.best)
                        engine.markBestSaved()
                    }
                    if (engine.score != shownScore) shownScore = engine.score
                    if (engine.best != shownBest) shownBest = engine.best
                    frame = now
                }
            }
        }
    }
    // Al irse la pantalla (el descanso acabó, se volvió al descanso o se saltó) el récord queda guardado.
    DisposableEffect(engine, store) {
        onDispose { store?.submit(engine.best) }
    }
    val shape = RoundedCornerShape(28.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .border(1.dp, Color.White.copy(alpha = 0.08f), shape)
            .onSizeChanged { engine.worldWidth = it.width / density / gameScale(it.height / density) }
            .semantics(mergeDescendants = true) {
                contentDescription = "Saltar"
                role = Role.Button
                onClick(label = "Saltar") {
                    engine.jump()
                    true
                }
            }
            .pointerInput(engine) {
                detectTapGestures(onPress = {
                    engine.jump()
                    tryAwaitRelease()
                })
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            // Leer el cuadro aquí hace que cada cuadro nuevo repinte sin recomponer.
            @Suppress("UNUSED_VARIABLE")
            val tick = frame
            drawArena(engine, reduced)
        }
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 16.dp, top = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(text = "Saltos", style = KpknType.caption, color = WizardColors.textMuted)
                Text(text = shownScore.toString(), style = KpknType.metricM, color = WizardColors.text)
            }
            Text(text = "récord $shownBest · ${"♥".repeat(shownLives.coerceAtLeast(0))}", style = KpknType.caption, color = WizardColors.textFaint)
        }
        if (shownLives <= 0) Text("Fin de partida", style = KpknType.title, color = Ink, modifier = Modifier.align(Alignment.Center).background(Color.Black.copy(alpha = .85f)).padding(16.dp))
        if (shownPower != null) Text(if (shownPower == RestSupplement.JUMP) "Salto potente" else "Velocidad", style = KpknType.caption, color = Color(0xFF8FB2FF), modifier = Modifier.align(Alignment.TopEnd).padding(16.dp))
        Text(
            text = "Toca para saltar",
            style = KpknType.caption,
            color = WizardColors.textFaint,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp),
        )
    }
}

private fun DrawScope.drawArena(engine: RestGameEngine, reduced: Boolean) {
    // `d` son los píxeles de un dp lógico del juego: la densidad por la escala del mundo.
    val d = density * gameScale(size.height / density)
    val heightLogical = size.height / d
    // El suelo queda a un cuarto de la altura desde abajo (entre 34 y 92 dp lógicos): hay sitio para el salto de 70 dp en el campo pequeño.
    val groundOffset = (heightLogical * 0.24f).coerceIn(34f, 92f)
    val groundY = size.height - groundOffset * d
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFF1A1612), Color(0xFF0A0A0A)),
            center = Offset(size.width / 2f, 0f),
            radius = size.width * 1.25f,
        ),
    )
    drawLine(Ink.copy(alpha = 0.18f), Offset(0f, groundY), Offset(size.width, groundY), strokeWidth = 2f * d)
    // La segunda línea (tenue) de F8 solo va si debajo queda sitio para ella y para «Toca para saltar» sin cruzarse.
    if (groundOffset >= 60f) {
        drawLine(
            Ink.copy(alpha = 0.06f),
            Offset(0f, groundY + 32f * d),
            Offset(size.width, groundY + 32f * d),
            strokeWidth = 1f * d,
        )
    }
    val top = groundY - (2f + RestGameEngine.OBSTACLE_H) * d
    for (obstacle in engine.obstacles) {
        when (obstacle.kind) {
            RestHazard.DUMBBELL -> drawDumbbell(obstacle.x * d, top, d)
            RestHazard.BENCH -> {
                drawRoundRect(Color(0xFF8FB2FF), Offset(obstacle.x * d, groundY - 32f * d), Size(38f * d, 7f * d), CornerRadius(2f * d))
                drawLine(Ink, Offset((obstacle.x + 5f) * d, groundY - 25f * d), Offset((obstacle.x + 5f) * d, groundY), 3f * d)
                drawLine(Ink, Offset((obstacle.x + 32f) * d, groundY - 25f * d), Offset((obstacle.x + 32f) * d, groundY), 3f * d)
            }
            RestHazard.GAP -> {
                drawRect(Color(0xFF000000), Offset(obstacle.x * d, groundY - 3f * d), Size(obstacle.width * d, 27f * d))
                drawLine(DumbbellPlate, Offset(obstacle.x * d, groundY), Offset(obstacle.x * d, groundY + 20f * d), 2f * d)
                drawLine(DumbbellPlate, Offset((obstacle.x + obstacle.width) * d, groundY), Offset((obstacle.x + obstacle.width) * d, groundY + 20f * d), 2f * d)
            }
        }
    }
    for (item in engine.supplements) {
        val center = Offset(item.x * d, groundY - 45f * d)
        drawCircle(Color(0xFF8FB2FF).copy(alpha = .15f), 12f * d, center)
        drawCircle(if (item.kind == RestSupplement.LIFE) DumbbellPlate else Color(0xFF8FB2FF), 7f * d, center, style = Stroke(2f * d))
        drawLine(Ink, center - Offset(3f * d, 0f), center + Offset(3f * d, 0f), 2f * d)
        if (item.kind == RestSupplement.LIFE) drawLine(Ink, center - Offset(0f, 3f * d), center + Offset(0f, 3f * d), 2f * d)
    }
    drawGameRunner(
        feetX = (RestGameEngine.RUNNER_X + 12f) * d,
        feetY = groundY - engine.height * d,
        d = d,
        seconds = engine.elapsed,
        airborne = !engine.onGround,
    )
    if (!reduced && engine.flash > 0f) {
        drawRect(HitRed.copy(alpha = 0.12f * (engine.flash / RestGameEngine.HIT_FLASH).coerceIn(0f, 1f)))
    }
}

/** La mancuerna de 30 × 20 dp del prototipo: dos discos de color y una barra de tinta. */
private fun DrawScope.drawDumbbell(left: Float, top: Float, d: Float) {
    drawRoundRect(DumbbellPlate, Offset(left, top + 4f * d), Size(6f * d, 14f * d), CornerRadius(2f * d))
    drawRoundRect(DumbbellPlate, Offset(left + 24f * d, top + 4f * d), Size(6f * d, 14f * d), CornerRadius(2f * d))
    drawRoundRect(Ink, Offset(left + 6f * d, top + 9f * d), Size(18f * d, 4f * d), CornerRadius(2f * d))
}

/**
 * El corredor del juego: la figura del reloj con los dos brazos. Con los pies en ([feetX], [feetY]); las piernas alternan cada 0,28 s
 * en el suelo y quedan abiertas en el aire.
 */
private fun DrawScope.drawGameRunner(feetX: Float, feetY: Float, d: Float, seconds: Float, airborne: Boolean) {
    val stroke = 1.8f * d
    val swing = if (airborne) 24f else 30f * cos(2.0 * PI * seconds / GameLegPeriod).toFloat()
    val hip = Offset(feetX, feetY - 11f * d)
    drawCircle(HeadFill, radius = 4f * d, center = Offset(feetX, feetY - 26f * d))
    drawCircle(Ink, radius = 4f * d, center = Offset(feetX, feetY - 26f * d), style = Stroke(width = stroke))
    drawLine(Ink, Offset(feetX, feetY - 22f * d), hip, strokeWidth = stroke, cap = StrokeCap.Round)
    drawLine(Ink, Offset(feetX, feetY - 19f * d), Offset(feetX + 7f * d, feetY - 15f * d), strokeWidth = stroke, cap = StrokeCap.Round)
    drawLine(Ink, Offset(feetX, feetY - 19f * d), Offset(feetX - 6f * d, feetY - 14f * d), strokeWidth = stroke, cap = StrokeCap.Round)
    rotate(swing, pivot = hip) {
        drawLine(Ink, hip, Offset(feetX, feetY), strokeWidth = stroke, cap = StrokeCap.Round)
    }
    rotate(-swing, pivot = hip) {
        drawLine(Ink, hip, Offset(feetX, feetY), strokeWidth = stroke, cap = StrokeCap.Round)
    }
}

/** Barra de progreso del descanso (F8): 6 dp con el degradado de los tres anillos. */
@Composable
private fun RestGameProgressBar(progress: Float, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(3.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(shape)
            .background(KpknSurface.inset, shape),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .background(
                    Brush.horizontalGradient(listOf(Color(0xFF8FB2FF), Color(0xFFF49A6E), Color(0xFFF7CF73))),
                    shape,
                ),
        )
    }
}

/** «Después: Serie 2 · 95 kg × 4–6»: lo que viene tras el descanso, de la siguiente serie real. */
@Composable
private fun RestGameNextBox(
    nextSet: RestNextSetSummary,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    // Con la letra grande el botón de al lado deja la serie sin sitio: el botón baja debajo del texto.
    val stacked = trailing != null && LocalDensity.current.fontScale > 1.15f
    val boxModifier = modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(18.dp))
        .background(KpknSurface.module, RoundedCornerShape(18.dp))
        .padding(horizontal = 14.dp, vertical = 8.dp)
    val text: @Composable (Modifier) -> Unit = { textModifier ->
        Column(modifier = textModifier) {
            Text(
                text = nextSet.exerciseName?.let { "Después · $it" } ?: "Después",
                style = KpknType.caption,
                color = WizardColors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = nextSet.headline(),
                style = KpknType.bodyStrong,
                color = WizardColors.text,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    if (stacked) {
        Column(modifier = boxModifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            text(Modifier.fillMaxWidth())
            trailing?.invoke()
        }
    } else {
        Row(
            modifier = boxModifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            text(Modifier.weight(1f))
            trailing?.invoke()
        }
    }
}

/**
 * El minijuego a pantalla completa (F8): arriba el descanso que sigue corriendo (tiempo y barra), en medio el campo y abajo qué viene y
 * cómo volver. [onSkip] es el mismo «Saltar descanso» de la pantalla del descanso.
 */
@Composable
internal fun RestGameFullScreen(
    remainingSeconds: Int,
    totalSeconds: Int,
    nextSet: RestNextSetSummary?,
    onBack: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
    recordStore: RestGameRecordStore? = null,
    engine: RestGameEngine? = null,
    running: Boolean = true,
) {
    val (game, store) = rememberRestGame(engine, recordStore)
    RestArcade(
        remainingSeconds = remainingSeconds, totalSeconds = totalSeconds, nextSet = nextSet,
        onBack = onBack, onSkip = onSkip, modifier = modifier,
        recordStore = store, runner = game, running = running, compact = false,
    )
}

/**
 * El minijuego dentro de la tarjeta del descanso, a su altura fija: arriba el tiempo que sigue corriendo, en medio el campo, abajo qué
 * viene y las dos salidas («Saltar» y «Volver»).
 */
@Composable
internal fun RestGameCardContent(
    remainingSeconds: Int,
    totalSeconds: Int,
    nextSet: RestNextSetSummary?,
    onBack: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
    recordStore: RestGameRecordStore? = null,
    engine: RestGameEngine? = null,
    running: Boolean = true,
) {
    val (game, store) = rememberRestGame(engine, recordStore)
    RestArcade(
        remainingSeconds = remainingSeconds, totalSeconds = totalSeconds, nextSet = nextSet,
        onBack = onBack, onSkip = onSkip, modifier = modifier,
        recordStore = store, runner = game, running = running, compact = true,
    )
}

/** «Saltar descanso» como acción de texto, para la pantalla del juego: el mismo `onSkip` del descanso. */
@Composable
private fun RestSkipTextAction(onClick: () -> Unit, modifier: Modifier = Modifier) {
    com.example.kpkn.ui.design.KpknTextAction(
        text = "Saltar descanso",
        onClick = onClick,
        modifier = modifier,
        color = WizardColors.textMuted,
    )
}
