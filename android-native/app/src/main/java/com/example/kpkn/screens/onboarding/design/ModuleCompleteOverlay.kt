package com.example.kpkn.screens.onboarding.design

/*
 * KPKN · Overlay de "módulo completado" del wizard (Jetpack Compose).
 * Port 1:1 de kpkn-module-overlay.js (mismos tiempos, geometría y colores), adaptado al wizard:
 *
 *  - [KpknModule.INTRO] es la pantalla de arranque: la misma composición pero sin color de «completado»
 *    (la torre neutra y ninguna etapa marcada).
 *  - Bajo el texto va la fila de etapas ([OverlayStage]): una guía recorre los bloques de uno en uno, marca
 *    los ya completados, celebra el que acaba de cerrarse y deja señalado el siguiente.
 *  - [onContinue] avanza el wizard; [onDismiss] (atrás del sistema) vuelve un paso. Al continuar, el overlay
 *    se desvanece (contenido y desenfoque) antes de avisar.
 *
 * Requisitos: Compose UI 1.5+, Material3. Usa la FontFamily `Syne` de ui/theme/KpknType.kt.
 * Blur real detrás del diálogo en Android 12+ (si el equipo lo soporta); si no, fondo oscuro más opaco.
 */

import android.graphics.BlurMaskFilter
import android.os.Build
import android.provider.Settings
import android.view.WindowManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.example.kpkn.ui.theme.Syne
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

enum class KpknModule(
    val title: String,
    val subtitle: String,
    val textAt: Float,
    val ctaAt: Float,
    val viewBox: Rect, // left, top, right, bottom en unidades de escena
) {
    /** Arranque del wizard: la misma composición, pero la torre neutra y nada marcado como completado. */
    INTRO("Tu recorrido en KPKN", "Cada bloque deja una parte de tu plan lista. Puedes volver atrás cuando quieras.", 1.15f, 1.7f, Rect(34f, 58f, 166f, 152f)),
    BASICOS("Datos básicos guardados", "Ya te conocemos un poco más.", 1.35f, 2.1f, Rect(32f, 22f, 168f, 158f)),
    ENTRENO("Entreno configurado", "Tu plan y tu calendario quedaron definidos.", 3.1f, 3.7f, Rect(38f, 74f, 162f, 170f)),
    NUTRICION("Nutrición configurada", "Tus metas de alimentación quedaron guardadas.", 2.65f, 3.25f, Rect(14f, 46f, 186f, 162f)),
    RINGS("Rings configurados", "Músculo, energía y columna, medidos cada día.", 2.1f, 2.7f, Rect(12f, 20f, 188f, 160f)),
}

// ---------------------------------------------------------------- colores
private val Ink = Color(0xFFF2EEE6)
private val Ok = Color(0xFF43D18C)
private val Dim = Color(0xFF3A3D44)
private val Columna = Color(0xFF8FB2FF)
private val Musculo = Color(0xFFF49A6E)
private val Energia = Color(0xFFF7CF73)
private val Mente = Color(0xFFC9B8FF)
private val OnInk = Color(0xFF0B0B0B)

// ---------------------------------------------------------------- composable público
/**
 * Overlay a pantalla completa con la animación del bloque [module], su título y la fila de [stages].
 *
 * - [onContinue]: «Continuar». Se avisa DESPUÉS de desvanecer el overlay (contenido y desenfoque).
 * - [onDismiss]: atrás del sistema. Por defecto igual que [onContinue].
 * - [stages]: etapas del recorrido; vacía = sin fila.
 * - [rootTag] y [ctaTag]: etiquetas de prueba de la raíz y del botón.
 */
@Composable
fun ModuleCompleteOverlay(
    module: KpknModule,
    onContinue: () -> Unit,
    title: String = module.title,
    subtitle: String = module.subtitle,
    cta: String = "Continuar",
    onDismiss: () -> Unit = onContinue,
    stages: List<OverlayStage> = emptyList(),
    /** Etiquetas de prueba: la raíz del overlay y su botón. */
    rootTag: String? = null,
    ctaTag: String? = null,
) {
    val scope = rememberCoroutineScope()
    val shownAnim = remember { Animatable(0f) }
    var leaving by remember { mutableStateOf(false) }
    // Un solo aviso por apertura: el desvanecido tarda unos milisegundos y un segundo toque no debe repetirlo.
    val leave: (() -> Unit) -> Unit = { action ->
        if (!leaving) {
            leaving = true
            scope.launch {
                shownAnim.animateTo(0f, tween(200))
                action()
                // Si el overlay sigue aquí, la acción no avanzó (se descartó o falló): vuelve a mostrarse en
                // lugar de quedar invisible y bloqueando la pantalla. Si avanzó, esta corrutina se cancela
                // con el overlay y no llega a este punto.
                delay(700)
                leaving = false
                shownAnim.animateTo(1f, tween(200))
            }
        }
    }

    Dialog(
        onDismissRequest = { leave(onDismiss) },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnClickOutside = false,
        ),
    ) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        val blur = Build.VERSION.SDK_INT >= 31 && window?.windowManager?.isCrossWindowBlurEnabled == true
        LaunchedEffect(window, blur) {
            window ?: return@LaunchedEffect
            window.setDimAmount(0f)
            if (Build.VERSION.SDK_INT >= 31 && blur) {
                window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                // El desenfoque entra y sale con el contenido: si no, desaparecería de golpe al cerrar.
                snapshotFlow { shownAnim.value }.collect { v ->
                    window.attributes = window.attributes.also { it.blurBehindRadius = (64 * v).roundToInt() }
                }
            }
        }

        val context = LocalContext.current
        val reduceMotion = remember {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }
        // Sin animaciones del sistema: se muestra el estado final (también el de la fila de etapas).
        val settledT = max(module.ctaAt + 1f, module.textAt + 3f)
        var t by remember(module) { mutableFloatStateOf(if (reduceMotion) settledT else 0f) }
        if (!reduceMotion) {
            LaunchedEffect(module) {
                val start = withFrameNanos { it }
                while (isActive) withFrameNanos { now -> t = (now - start) / 1_000_000_000f }
            }
        }

        LaunchedEffect(Unit) { shownAnim.animateTo(1f, tween(280)) }
        val shown = shownAnim.value
        val textA by animateFloatAsState(if (t >= module.textAt) 1f else 0f, tween(450), label = "text")
        val ctaA by animateFloatAsState(if (t >= module.ctaAt) 1f else 0f, tween(350), label = "cta")

        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .then(if (rootTag != null) Modifier.testTag(rootTag) else Modifier)
                .graphicsLayer { alpha = shown }
                // Sin desenfoque del sistema (Android < 12, ahorro de batería o sin soporte del dispositivo) el fondo debe
                // tapar casi todo: con menos opacidad el texto de la página de atrás se mezcla con el del overlay.
                .background(Color(0xFF060606).copy(alpha = if (blur) 0.52f else 0.96f)),
            contentAlignment = Alignment.Center,
        ) {
            val vb = module.viewBox
            val aspect = vb.width / vb.height
            // Lo que ocupan título, subtítulo, etapas y botón: la escena se encoge si la pantalla es baja.
            val sceneWidth = minOf(maxWidth * 0.9f, 380.dp, (maxHeight - 350.dp).coerceAtLeast(150.dp) * aspect)
            Column(
                Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Canvas(
                    Modifier
                        .width(sceneWidth)
                        .aspectRatio(aspect)
                ) {
                    val k = size.width / vb.width
                    withTransform({
                        scale(k, k, Offset.Zero)
                        translate(-vb.left, -vb.top)
                    }) {
                        when (module) {
                            KpknModule.INTRO -> drawIntro(t)
                            KpknModule.BASICOS -> drawBasicos(t)
                            KpknModule.ENTRENO -> drawEntreno(t)
                            KpknModule.NUTRICION -> drawNutricion(t)
                            KpknModule.RINGS -> drawRings(t, k)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    title,
                    // LineBreak.Heading reparte las líneas por igual (el `text-wrap: balance` de la demo web).
                    style = TextStyle(fontFamily = Syne, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp,
                        letterSpacing = (-0.01).em, lineHeight = 30.sp, color = Ink, textAlign = TextAlign.Center,
                        lineBreak = LineBreak.Heading),
                    modifier = Modifier.graphicsLayer { alpha = textA; translationY = (1f - textA) * 28f },
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    subtitle,
                    style = TextStyle(fontFamily = Syne, fontWeight = FontWeight.Medium, fontSize = 14.sp,
                        lineHeight = 20.sp, color = Ink.copy(alpha = 0.72f), textAlign = TextAlign.Center),
                    modifier = Modifier.widthIn(max = 280.dp).graphicsLayer { alpha = textA; translationY = (1f - textA) * 16f },
                )
                if (stages.isNotEmpty()) {
                    Spacer(Modifier.height(22.dp))
                    StageTracker(
                        stages = stages,
                        startAt = module.textAt,
                        t = { t },
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 380.dp)
                            .graphicsLayer { alpha = textA },
                    )
                }
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = { leave(onContinue) },
                    enabled = ctaA > 0.5f,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Ink, contentColor = OnInk,
                        disabledContainerColor = Ink, disabledContentColor = OnInk,
                    ),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .widthIn(min = 168.dp)
                        .then(if (ctaTag != null) Modifier.testTag(ctaTag) else Modifier)
                        .graphicsLayer { alpha = ctaA; translationY = (1f - ctaA) * 20f },
                ) {
                    Text(cta, fontFamily = Syne, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- fila de etapas con la guía
/** Estado de una etapa del recorrido en la fila inferior del overlay. */
enum class OverlayStageState {
    /** Ya estaba completa antes de este bloque. */
    DONE,

    /** La que acaba de cerrarse: la guía la celebra. */
    JUST_DONE,

    /** La que sigue: la guía se detiene en ella. */
    NEXT,

    /** Falta, sin color. */
    PENDING,
}

data class OverlayStage(val label: String, val state: OverlayStageState)

/** Segundos que tarda la guía en pasar de una etapa a la siguiente, y espera antes de arrancar. */
private const val TRACK_PER_NODE = 0.22f
private const val TRACK_LEAD = 0.1f

/** Hasta qué etapa llega la guía: la siguiente si la hay; si no, la última completada. */
private fun headTarget(stages: List<OverlayStage>): Int {
    val next = stages.indexOfFirst { it.state == OverlayStageState.NEXT }
    if (next >= 0) return next
    return max(stages.indexOfLast { it.state == OverlayStageState.DONE || it.state == OverlayStageState.JUST_DONE }, 0)
}

private fun stageDescription(stage: OverlayStage): String = when (stage.state) {
    OverlayStageState.DONE, OverlayStageState.JUST_DONE -> "${stage.label}, completado"
    OverlayStageState.NEXT -> "${stage.label}, sigue"
    OverlayStageState.PENDING -> "${stage.label}, pendiente"
}

/**
 * La fila de etapas: un riel con un nodo por bloque y una guía luminosa que lo recorre de uno en uno desde el
 * primero. Los completados se encienden en verde con su check al paso de la guía, el que acaba de cerrarse
 * lanza una onda y la guía se detiene en el siguiente, que queda señalado en tinta. Sin etapas completadas
 * (la pantalla de arranque) no hay verde: solo el primer nodo respira en tinta.
 */
@Composable
private fun StageTracker(
    stages: List<OverlayStage>,
    startAt: Float,
    t: () -> Float,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.clearAndSetSemantics {
            contentDescription = "Recorrido: " + stages.joinToString("; ") { stageDescription(it) }
        },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Canvas(Modifier.fillMaxWidth().height(30.dp)) { drawStages(stages, t(), startAt) }
        Row(Modifier.fillMaxWidth()) {
            stages.forEachIndexed { i, stage ->
                StageLabel(stage, litAt = startAt + TRACK_LEAD + i * TRACK_PER_NODE, t = t, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StageLabel(stage: OverlayStage, litAt: Float, t: () -> Float, modifier: Modifier = Modifier) {
    // derivedStateOf: la etiqueta solo se recompone cuando la guía llega a ella, no en cada cuadro.
    val lit by remember(stage, litAt) {
        derivedStateOf { stage.state != OverlayStageState.PENDING && t() >= litAt }
    }
    val color by animateColorAsState(if (lit) Ink.copy(alpha = 0.92f) else Ink.copy(alpha = 0.38f), tween(250), label = "stage")
    Text(
        stage.label,
        modifier = modifier,
        color = color,
        fontSize = 11.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
        textAlign = TextAlign.Center,
    )
}

private fun DrawScope.drawStages(stages: List<OverlayStage>, t: Float, startAt: Float) {
    val n = stages.size
    if (n == 0) return
    val colW = size.width / n
    val cy = size.height / 2f
    val r = 9.dp.toPx()
    val lineW = 2.dp.toPx()
    val target = headTarget(stages)
    // La guía, en unidades de etapa: 0 = sobre la primera, `target` = sobre la que sigue.
    val head = ((t - startAt - TRACK_LEAD) / TRACK_PER_NODE).coerceIn(0f, target.toFloat())
    fun cx(i: Int) = (i + .5f) * colW

    if (n > 1) {
        drawLine(Dim, Offset(cx(0), cy), Offset(cx(n - 1), cy), lineW, StrokeCap.Round)
        if (head > 0f) drawLine(Ok, Offset(cx(0), cy), Offset(cx(0) + head * colW, cy), lineW, StrokeCap.Round)
    }
    stages.forEachIndexed { i, stage ->
        val x = cx(i)
        val reach = startAt + TRACK_LEAD + i * TRACK_PER_NODE // instante en que la guía llega a este nodo
        val k = seg(t, reach, reach + .3f)
        when (stage.state) {
            OverlayStageState.DONE, OverlayStageState.JUST_DONE -> {
                val just = stage.state == OverlayStageState.JUST_DONE
                val pop = 1f + (if (just) .32f else .16f) * sin(PI.toFloat() * k)
                drawCircle(mix(Dim, Ok, seg(t, reach, reach + .2f)), r * pop, Offset(x, cy))
                drawIcon(IC_CHECK, x, cy, r * 2f, Color(0xFF08130D), 2.8f, eOutBack(k), k)
                if (just) {
                    val w = seg(t, reach, reach + .75f)
                    if (w > 0f && w < 1f) {
                        drawCircle(Ok, r * (1f + 1.5f * eOutCubic(w)), Offset(x, cy), alpha = (1f - w) * .55f, style = Stroke(1.6.dp.toPx()))
                    }
                }
            }
            OverlayStageState.NEXT -> {
                drawCircle(Dim, r, Offset(x, cy))
                val ring = k * (.62f + .38f * sin(t * 4.2f))
                drawCircle(Ink, r + 3.dp.toPx(), Offset(x, cy), alpha = ring, style = Stroke(1.6.dp.toPx()))
                drawCircle(Ink, r * .38f, Offset(x, cy), alpha = k)
            }
            OverlayStageState.PENDING -> {
                drawCircle(Dim, r, Offset(x, cy))
                drawCircle(Ink, r * .22f, Offset(x, cy), alpha = .22f)
            }
        }
    }
    // La cabeza de la guía, mientras viaja entre etapas.
    if (head > 0f && head < target) {
        val hx = cx(0) + head * colW
        drawCircle(Ok, 9.dp.toPx(), Offset(hx, cy), alpha = .22f)
        drawCircle(Ok, 3.5.dp.toPx(), Offset(hx, cy))
    }
}

// ---------------------------------------------------------------- utilidades
// (internas: las escenas de la bienvenida comparten estas curvas de movimiento.)
internal fun clamp01(v: Float) = v.coerceIn(0f, 1f)
internal fun lerpF(a: Float, b: Float, t: Float) = a + (b - a) * t
internal fun seg(t: Float, a: Float, b: Float) = clamp01((t - a) / (b - a))
internal fun eOutCubic(t: Float) = 1f - (1f - t).pow(3)
internal fun eInOut(t: Float) = if (t < .5f) 4f * t * t * t else 1f - (-2f * t + 2f).pow(3) / 2f
internal fun eOutBack(t: Float): Float { val c1 = 1.9f; val c3 = c1 + 1f; return 1f + c3 * (t - 1f).pow(3) + c1 * (t - 1f).pow(2) }
internal fun eInQuad(t: Float) = t * t
internal fun spring(dt: Float, k: Float = 7f, w: Float = 22f) = if (dt < 0f) 0f else exp(-k * dt) * cos(w * dt)
internal fun mix(a: Color, b: Color, t: Float) = lerp(a, b, clamp01(t))
private const val TAU = 6.2831855f
private fun parse(d: String): Path = PathParser().parsePathString(d).toPath()

// ---------------------------------------------------------------- la Torre anidada
private class DiscGeo(val cy: Float, val rx: Float, val ry: Float, val off: Float, val dx: Float, val dy: Float)
private val BASE = listOf(
    DiscGeo(61f, 38f, 12f, 2f, 5f, 3.5f),
    DiscGeo(46f, 28f, 9f, 1.5f, 4f, 2.5f),
    DiscGeo(33f, 18f, 6.5f, 1f, 3.5f, 2.2f),
)

private data class Disc(val s: Float = 1f, val q: Float = 0f, val x: Float = 0f, val y: Float = 0f, val a: Float = 1f)

private fun discPath(i: Int, ox: Float, oy: Float, d: Disc): Path {
    val b = BASE[i]
    val cx = 50f + d.x + ox
    val cy = b.cy + d.y + oy
    val rx = b.rx * d.s * (1f + d.q)
    val ry = b.ry * d.s * (1f - d.q)
    val irx = max(.1f, rx - b.dx * d.s)
    val iry = max(.1f, ry * (b.ry - b.dy) / b.ry)
    val icy = cy - b.off * d.s
    return Path().apply {
        fillType = PathFillType.EvenOdd
        addOval(Rect(cx - rx, cy - ry, cx + rx, cy + ry))
        addOval(Rect(cx - irx, icy - iry, cx + irx, icy + iry))
    }
}

private fun DrawScope.drawTower(ox: Float, oy: Float, discs: List<Disc>, fills: List<Color>) {
    discs.forEachIndexed { i, d ->
        if (d.a > 0f) drawPath(discPath(i, ox, oy, d), fills[i], alpha = clamp01(d.a))
    }
}

/** Torre desenfocada para el brillo (usa el Canvas nativo de Android). `pxPerUnit` = escala actual. */
private fun DrawScope.drawTowerGlow(ox: Float, oy: Float, discs: List<Disc>, fills: List<Color>, pxPerUnit: Float) {
    drawIntoCanvas { c ->
        discs.forEachIndexed { i, d ->
            if (d.a <= 0.01f) return@forEachIndexed
            val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = fills[i].copy(alpha = clamp01(d.a)).toArgb()
                maskFilter = BlurMaskFilter(3.2f * pxPerUnit, BlurMaskFilter.Blur.NORMAL)
            }
            c.nativeCanvas.drawPath(discPath(i, ox, oy, d).asAndroidPath(), p)
        }
    }
}

// ---------------------------------------------------------------- íconos (cuadrícula 24, trazo)
private val IC_USER by lazy { listOf(parse("M8 8a4 4 0 1 0 8 0a4 4 0 1 0 -8 0"), parse("M4 21c0-4.4 3.6-7.2 8-7.2s8 2.8 8 7.2")) }
private val IC_CHECK by lazy { listOf(parse("M7 12.5l3.2 3.2L17 8.8")) }
private val IC_BED by lazy { listOf(parse("M3 19V6M3 15h18v4M21 15v-2.5A3.5 3.5 0 0 0 17.5 9H11v6"), parse("M5 11.5a2 2 0 1 0 4 0a2 2 0 1 0 -4 0")) }
private val IC_BRAIN by lazy {
    listOf(
        parse("M11.5 4.5C10 3 7.5 3.4 7 5.4 5 5.4 3.8 7.4 4.6 9.2 3 10.2 3 12.8 4.8 13.8 4.2 16 5.8 18 8 17.8 8.6 19.8 11 20.2 11.5 18.6V4.5Z"),
        parse("M12.5 4.5C14 3 16.5 3.4 17 5.4 19 5.4 20.2 7.4 19.4 9.2 21 10.2 21 12.8 19.2 13.8 19.8 16 18.2 18 16 17.8 15.4 19.8 13 20.2 12.5 18.6V4.5Z"),
        parse("M8 9.5c1 .2 2 1 2 2.5M16 9.5c-1 .2-2 1-2 2.5M7.5 14.5c1-.5 2.2-.4 3 .5M16.5 14.5c-1-.5-2.2-.4-3 .5"),
    )
}
private val IC_SPINE by lazy {
    listOf(
        parse("M10.3 2.5h3.4a1.8 1.8 0 0 1 0 3.6h-3.4a1.8 1.8 0 0 1 0-3.6z"),
        parse("M9.4 7.6h5.2a1.9 1.9 0 0 1 0 3.8h-5.2a1.9 1.9 0 0 1 0-3.8z"),
        parse("M9.8 12.9h4.4a1.8 1.8 0 0 1 0 3.6h-4.4a1.8 1.8 0 0 1 0-3.6z"),
        parse("M10.7 18h2.6a1.7 1.7 0 0 1 0 3.4h-2.6a1.7 1.7 0 0 1 0-3.4z"),
    )
}
private val IC_DUMBBELL by lazy { listOf(parse("M6.5 6.5v11M3.5 9v6M17.5 6.5v11M20.5 9v6M6.5 12h11")) }
private val SPARK by lazy { parse("M0-5L1.2-1.2 5 0 1.2 1.2 0 5-1.2 1.2-5 0-1.2-1.2Z") }

private fun DrawScope.drawIcon(paths: List<Path>, x: Float, y: Float, size: Float, color: Color, sw: Float, s: Float = 1f, alpha: Float = 1f) {
    if (alpha <= 0f || s <= 0f) return
    val k = s * size / 24f
    withTransform({ translate(x, y); scale(k, k, Offset.Zero); translate(-12f, -12f) }) {
        paths.forEach { drawPath(it, color, alpha = clamp01(alpha), style = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)) }
    }
}

private fun DrawScope.drawBadge(x: Float, y: Float, r: Float, s: Float) {
    if (s <= 0f) return
    drawCircle(Ok, r * s, Offset(x, y))
    drawIcon(IC_CHECK, x, y, r * 1.25f, Color(0xFF08130D), 2.8f, s)
}

// ---------------------------------------------------------------- 0 · ARRANQUE (nada completado: sin verde)
private fun DrawScope.drawIntro(t: Float) {
    // Una onda lenta en tinta, solo como respiración de la torre.
    val cycle = (t - 1.2f) / 2.6f
    if (cycle > 0f) {
        val w = cycle % 1f
        val rx = lerpF(38f, 66f, eOutCubic(w)); val ry = lerpF(12f, 21f, eOutCubic(w))
        drawOval(Ink, Offset(100f - rx, 133f - ry), Size(rx * 2, ry * 2), alpha = (1f - w) * .28f, style = Stroke(lerpF(1.6f, .4f, w)))
    }
    val discs = (0..2).map { i ->
        val t0 = .05f + .18f * i; val land = t0 + .3f
        var q = .2f * spring(t - land, 6f, 20f)
        for (j in i + 1..2) q += .06f * spring(t - (.35f + .18f * j), 7f, 22f)
        Disc(y = (1f - eInQuad(seg(t, t0, land))) * -36f, q = q, a = if (t < t0) 0f else 1f,
            s = 1f + .015f * sin(t * 2.4f + i) * seg(t, 1.2f, 1.7f))
    }
    drawTower(50f, 72f, discs, listOf(Ink, Ink, Ink))
}

// ---------------------------------------------------------------- 1 · DATOS BÁSICOS
private fun DrawScope.drawBasicos(t: Float) {
    val d = seg(t, 1.5f, 2.35f)
    if (d > 0f && d < 1f) {
        val rx = lerpF(38f, 82f, eOutCubic(d)); val ry = lerpF(12f, 27f, eOutCubic(d))
        drawOval(Ok, Offset(100f - rx, 133f - ry), Size(rx * 2, ry * 2), alpha = (1f - d) * .9f, style = Stroke(lerpF(2.2f, .5f, d)))
    }
    val glow = max(0f, spring(t - 1.55f, 2.6f, 0f)) * .55f
    val discs = (0..2).map { i ->
        val t0 = .05f + .18f * i; val land = t0 + .3f
        var q = .2f * spring(t - land, 6f, 20f)
        for (j in i + 1..2) q += .06f * spring(t - (.35f + .18f * j), 7f, 22f)
        Disc(y = (1f - eInQuad(seg(t, t0, land))) * -60f, q = q, a = if (t < t0) 0f else 1f,
            s = 1f + .015f * sin(t * 2.4f + i) * seg(t, 2.3f, 2.8f))
    }
    val fill = mix(Ink, Ok, glow)
    drawTower(50f, 72f, discs, listOf(fill, fill, fill))
    val k = seg(t, .75f, 1.25f); val kb = eOutBack(k)
    drawIcon(IC_USER, 100f, 56f + (1f - kb) * 24f + sin(t * 2f) * .8f * seg(t, 2f, 2.5f), 46f,
        mix(Ink, Ok, seg(t, 1.25f, 1.6f)), 2f, lerpF(.55f, 1f, kb), k * 1.6f)
    drawBadge(123f, 40f, 10.5f, eOutBack(seg(t, 1.4f, 1.75f)))
}

// ---------------------------------------------------------------- 2 · ENTRENO
private data class Pose(
    val hip: Offset, val k1: Offset, val f1: Offset, val k2: Offset, val f2: Offset,
    val sh: Offset, val hd: Offset, val bar: Offset, val e1: Offset, val h1: Offset, val e2: Offset, val h2: Offset,
)
private fun lerpO(a: Offset, b: Offset, t: Float) = Offset(lerpF(a.x, b.x, t), lerpF(a.y, b.y, t))
private fun lerpPose(a: Pose, b: Pose, t: Float) = Pose(
    lerpO(a.hip, b.hip, t), lerpO(a.k1, b.k1, t), lerpO(a.f1, b.f1, t), lerpO(a.k2, b.k2, t), lerpO(a.f2, b.f2, t),
    lerpO(a.sh, b.sh, t), lerpO(a.hd, b.hd, t), lerpO(a.bar, b.bar, t), lerpO(a.e1, b.e1, t), lerpO(a.h1, b.h1, t),
    lerpO(a.e2, b.e2, t), lerpO(a.h2, b.h2, t),
)
private const val THIGH = 17f; private const val SHIN = 17f; private const val TORSO = 25f
private const val UARM = 12.5f; private const val FARM = 12f; private const val HEAD = 6.5f
private fun dir(a: Float) = Offset(sin(a), cos(a)) // desde la vertical hacia abajo, + = adelante

private fun ik(a: Offset, b: Offset, l1: Float, l2: Float, sign: Float): Offset {
    val dx = b.x - a.x; val dy = b.y - a.y
    val d = min(hypot(dx, dy), l1 + l2 - .01f)
    val base = atan2(dy, dx)
    val al = acos(((l1 * l1 + d * d - l2 * l2) / (2f * l1 * d)).coerceIn(-1f, 1f))
    return Offset(a.x + l1 * cos(base + sign * al), a.y + l1 * sin(base + sign * al))
}

private fun squat(d: Float): Pose {
    val hip = Offset(100f - 10f * d, 117f + 15f * d); val lean = .12f + .55f * d
    val f1 = Offset(106f, 150f); val f2 = Offset(101f, 150f)
    val sh = hip + Offset(sin(lean), -cos(lean)) * TORSO
    val hd = sh + Offset(sin(lean) * .9f, -cos(lean)) * 10f
    val bar = sh + Offset(-4f, 1f)
    val h1 = bar + Offset(5f, 3f); val h2 = bar + Offset(3f, 4f)
    return Pose(hip, ik(hip, f1, THIGH, SHIN, -1f), f1, ik(hip, f2, THIGH, SHIN, -1f), f2,
        sh, hd, bar, ik(sh, h1, UARM, FARM, 1f), h1, ik(sh, h2, UARM, FARM, 1f), h2)
}

private fun run(ph: Float): Pose {
    val lean = .2f
    val legs = listOf(0f, PI.toFloat()).map { o ->
        val th = .58f * sin(ph + o); val bend = .25f + 1.15f * max(0f, cos(ph + o))
        val k = dir(th) * THIGH
        Pair(k, k + dir(th - bend) * SHIN)
    }
    val low = max(legs[0].second.y, legs[1].second.y)
    val hip = Offset(98f, 148.5f - low - 1.2f * abs(cos(ph)))
    val sh = hip + Offset(sin(lean), -cos(lean)) * TORSO
    val arms = listOf(PI.toFloat(), 0f).map { o ->
        val a = -.65f * sin(ph + o); val e = sh + dir(a) * UARM
        Pair(e, e + dir(a + 1.5f) * FARM)
    }
    return Pose(hip, hip + legs[0].first, hip + legs[0].second, hip + legs[1].first, hip + legs[1].second,
        sh, sh + Offset(sin(lean) * .9f, -cos(lean)) * 10f, sh, arms[0].first, arms[0].second, arms[1].first, arms[1].second)
}

// Offset es una value class: Kotlin no admite `vararg` de ella, de ahí las dos sobrecargas.
private fun poly(a: Offset, b: Offset) = Path().apply { moveTo(a.x, a.y); lineTo(b.x, b.y) }
private fun poly(a: Offset, b: Offset, c: Offset) = Path().apply { moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c.x, c.y) }
private val POST by lazy { parse("M146 153L136 108M128 106l14-4") }

private fun DrawScope.drawEntreno(t: Float) {
    // cinta
    val tr = eOutCubic(seg(t, 2.25f, 2.8f))
    if (tr > 0f) withTransform({ translate((1f - tr) * 30f, 0f) }) {
        drawLine(Ink, Offset(56f, 153f), Offset(150f, 153f), 4f, StrokeCap.Round, alpha = tr)
        drawLine(Ink, Offset(60f, 159f), Offset(146f, 159f), 1.6f, StrokeCap.Round,
            PathEffect.dashPathEffect(floatArrayOf(5f, 7f), -t * 70f), alpha = tr * .55f)
        drawPath(POST, Ink, alpha = tr, style = Stroke(4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
    // líneas de velocidad
    val sa = seg(t, 2.8f, 3.1f)
    if (sa > 0f) for (i in 0..2) {
        val ph = (t * 1.6f + i * .33f) % 1f; val x = 70f - ph * 26f; val y = 112f + i * 9f
        drawLine(Ok, Offset(x, y), Offset(x + 12f * (1f - ph), y), 2f, StrokeCap.Round, alpha = sa * sin(PI.toFloat() * ph) * .8f)
    }
    // pose
    val reps = if (t < 2.2f) .5f - .5f * cos(TAU * max(0f, t - .2f) / 1f) else 0f
    val b = eInOut(seg(t, 2.2f, 2.85f))
    val pose = if (b <= 0f) squat(reps) else lerpPose(squat(0f), run(TAU * (t - 2.2f) / .62f), b)
    val col = mix(Ink, Ok, seg(t, 2.4f, 2.95f))
    // disco de la barra (los Anillos vistos de frente), detrás del cuerpo
    val pf = seg(t, 2.2f, 2.55f)
    val pa = (1f - pf) * seg(t, 0f, .3f)
    if (pa > 0f) withTransform({ translate(pose.bar.x, pose.bar.y - pf * 30f); scale(1f - pf * .6f, 1f - pf * .6f, Offset.Zero) }) {
        listOf(12f, 8f, 4f).forEach { r -> drawCircle(Ink, r, Offset(0f, 12f - r), alpha = pa, style = Stroke(2.4f)) }
    }
    fun limb(p: Path, w: Float, a: Float = 1f) = drawPath(p, col, alpha = a, style = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round))
    limb(poly(pose.sh, pose.e2, pose.h2), 6.5f, .55f)
    limb(poly(pose.hip, pose.k2, pose.f2), 7f, .55f)
    limb(poly(pose.hip, pose.sh), 8f)
    limb(poly(pose.hip, pose.k1, pose.f1), 7.5f)
    limb(poly(pose.sh, pose.e1, pose.h1), 6.5f)
    drawCircle(col, HEAD, pose.hd)
}

// ---------------------------------------------------------------- 3 · NUTRICIÓN
private class FoodPart(val d: String, val color: Long, val stroke: Float = 0f)
private class Food(val parts: List<Pair<Path, FoodPart>>, val orbit: Int, val phase: Float)
private fun food(orbit: Int, phase: Float, vararg p: FoodPart) = Food(p.map { parse(it.d) to it }, orbit, phase)
private val FOODS by lazy {
    listOf(
        food(0, 0f, // manzana
            FoodPart("M0-4c-3-2.4-8-1.6-8 3.6 0 4.6 3.6 8.4 6 8.4 1 0 1.4-.6 2-.6s1 .6 2 .6c2.4 0 6-3.8 6-8.4 0-5.2-5-6-8-3.6z", 0xFFE8574A),
            FoodPart("M0-4c0-2 1-4 3-5", 0xFF7A4A2A, 1.4f),
            FoodPart("M1-6c2-2.6 5-2.6 6-1.4-1.6 1.8-4 2.2-6 1.4z", 0xFF6BBF59)),
        food(0, 2.1f, // pescado
            FoodPart("M-9 0c3-5.5 10-5.5 13 0-3 5.5-10 5.5-13 0z", 0xFF8FB2FF),
            FoodPart("M3.5 0l5.5-4.5v9z", 0xFF8FB2FF),
            FoodPart("M-6.1-1a1.1 1.1 0 1 0 2.2 0a1.1 1.1 0 1 0-2.2 0", 0xFF0B0B0B)),
        food(0, 4.2f, // brócoli
            FoodPart("M-1.6 2h3.2v7.5h-3.2z", 0xFF7FB069),
            FoodPart("M-8.4-.5a4.2 4.2 0 1 0 8.4 0a4.2 4.2 0 1 0-8.4 0", 0xFF4E9A47),
            FoodPart("M0-.5a4.2 4.2 0 1 0 8.4 0a4.2 4.2 0 1 0-8.4 0", 0xFF4E9A47),
            FoodPart("M-4.8-4.5a4.8 4.8 0 1 0 9.6 0a4.8 4.8 0 1 0-9.6 0", 0xFF5FAE53)),
        food(1, 1f, // palta
            FoodPart("M0-9c3.5 0 4.5 4 5.5 7 1.5 4 2 9-5.5 9s-7-5-5.5-9c1-3 2-7 5.5-7z", 0xFF5E9E4A),
            FoodPart("M0-6c2.4 0 3 3 3.8 5.2 1 3 1.2 6.2-3.8 6.2s-4.8-3.2-3.8-6.2C-3-3-2.4-6 0-6z", 0xFFD7E8A2),
            FoodPart("M-2.8 2a2.8 2.8 0 1 0 5.6 0a2.8 2.8 0 1 0-5.6 0", 0xFF8A5A3B)),
        food(1, 4.1f, // huevo
            FoodPart("M-7-2c0-5 5-7 8-5 3-2 8 0 7 5 3 3 0 8-4 8-2 2-6 2-8 0-4 0-6-4-3-8z", 0xFFF7F3EA),
            FoodPart("M-2.9 0a3.4 3.4 0 1 0 6.8 0a3.4 3.4 0 1 0-6.8 0", 0xFFF5B82E)),
        food(2, .4f, // zanahoria
            FoodPart("M-7 8.5L3.5-4.5c1.6-1.8 4.4-.2 3.5 2L-3 9.6c-1 1.4-3.2 1-4-1.1z", 0xFFF08A3C),
            FoodPart("M5.5-4.5l1-4.5M5.5-4.5l4.6-1M5.5-4.5l3.4-3.4", 0xFF6BBF59, 1.6f)),
        food(2, 3.5f, // plátano
            FoodPart("M-8.5-3c2 7 10.5 9 16.5 3 .6-.6 1.5 0 1 .9-5 8.2-15.5 7.2-18.5-3 0-.8.8-1.5 1-.9z", 0xFFF7CF73)),
    )
}
private val ORBIT_W = floatArrayOf(.85f, 1.05f, 1.3f)

private fun DrawScope.drawFood(f: Food, x: Float, y: Float, s: Float, rot: Float, alpha: Float) {
    if (alpha <= 0f) return
    withTransform({ translate(x, y); scale(s, s, Offset.Zero); rotate(rot, Offset.Zero) }) {
        f.parts.forEach { (p, part) ->
            if (part.stroke > 0f) drawPath(p, Color(part.color), alpha = clamp01(alpha), style = Stroke(part.stroke, cap = StrokeCap.Round))
            else drawPath(p, Color(part.color), alpha = clamp01(alpha))
        }
    }
}

private fun DrawScope.drawNutricion(t: Float) {
    val enter = eOutCubic(seg(t, 0f, 1f)); val merge = eInOut(seg(t, 2.25f, 2.8f))
    class Placed(val f: Food, val x: Float, val y: Float, val s: Float, val rot: Float, val a: Float, val front: Boolean, val depth: Float)
    val placed = FOODS.mapIndexed { i, f ->
        val b = BASE[f.orbit]
        val ang = f.phase + ORBIT_W[f.orbit] * t
        val rf = (1f + .9f * (1f - enter)) * lerpF(1f, .12f, merge)
        val depth = (sin(ang) + 1f) / 2f
        Placed(
            f,
            100f + b.rx * 2f * rf * cos(ang),
            b.cy + 56f + b.ry * 2.1f * rf * sin(ang) - (1f - enter) * 20f,
            lerpF(.72f, 1.12f, depth) * lerpF(1f, .25f, merge) * 1.35f,
            sin(t * 1.7f + i) * 12f,
            seg(t, .05f * i, .05f * i + .4f) * lerpF(.55f, 1f, depth) * (1f - seg(t, 2.6f, 2.8f)),
            sin(ang) > 0f, depth,
        )
    }.sortedBy { it.depth }
    placed.filter { !it.front }.forEach { drawFood(it.f, it.x, it.y, it.s, it.rot, it.a) }
    val g = max(0f, spring(t - 2.75f, 2.4f, 0f)) * .7f
    val fill = mix(Ink, Ok, g)
    drawTower(50f, 56f, (0..2).map { i ->
        Disc(s = 1f + .06f * max(0f, spring(t - 2.75f - .06f * i, 5f, 12f)) + .012f * sin(t * 2.2f + i), a = seg(t, .1f * i, .1f * i + .4f))
    }, listOf(fill, fill, fill))
    placed.filter { it.front }.forEach { drawFood(it.f, it.x, it.y, it.s, it.rot, it.a) }
    drawBadge(100f, 66f, 10.5f, eOutBack(seg(t, 2.8f, 3.15f)))
}

// ---------------------------------------------------------------- 4 · RINGS
private class RingIcon(val paths: () -> List<Path>, val color: Color, val x: Float, val y: Float, val at: Float)
private val RING_ICONS = listOf(
    RingIcon({ IC_SPINE }, Columna, 160f, 104f, .3f),
    RingIcon({ IC_DUMBBELL }, Musculo, 140f, 56f, .75f),
    RingIcon({ IC_BED }, Energia, 40f, 104f, 1.2f),
    RingIcon({ IC_BRAIN }, Mente, 60f, 56f, 1.6f),
)
private val SPARKS = listOf(
    Triple(30f, 70f, 0), Triple(172f, 72f, 1), Triple(100f, 30f, 2), Triple(80f, 150f, 0), Triple(126f, 150f, 1), Triple(50f, 132f, 2),
    Triple(152f, 132f, 0), Triple(118f, 34f, 1), Triple(22f, 112f, 2), Triple(178f, 114f, 1), Triple(88f, 44f, 0), Triple(142f, 88f, 2),
)

private fun DrawScope.drawRings(t: Float, pxPerUnit: Float) {
    val ringCols = listOf(Columna, Musculo, Energia)
    val lit = (0..2).map { i -> seg(t, .3f + .45f * i, .7f + .45f * i) }
    val flick = (0..2).map { i -> .78f + .22f * sin(t * 9f + i * 2.1f) * sin(t * 3.3f + i) }
    val st = (0..2).map { i -> Disc(s = 1f + .07f * sin(PI.toFloat() * lit[i]) + .012f * sin(t * 2f + i), a = seg(t, 0f, .3f)) }
    drawTowerGlow(50f, 64f, st.mapIndexed { i, d -> d.copy(a = lit[i] * flick[i] * .9f) }, ringCols, pxPerUnit)
    drawTower(50f, 64f, st, (0..2).map { mix(Dim, ringCols[it], lit[it]) })
    RING_ICONS.forEachIndexed { i, o ->
        val k = eOutBack(seg(t, o.at, o.at + .4f))
        drawIcon(o.paths(), o.x, o.y + sin(t * 2f + i * 1.3f) * 2f * seg(t, o.at + .4f, o.at + .8f), 27f, o.color, 1.9f,
            max(0f, k), seg(t, o.at, o.at + .2f))
    }
    SPARKS.forEachIndexed { i, (x, y, c) ->
        val ph = i * 1.7f
        val tw = max(0f, sin(t * 3.4f + ph)).pow(3) * seg(t, .6f, 1.2f)
        if (tw > 0.01f) withTransform({ translate(x, y); scale(tw * .9f, tw * .9f, Offset.Zero); rotate(t * 40f + ph * 30f, Offset.Zero) }) {
            drawPath(SPARK, ringCols[c], alpha = tw)
        }
    }
}
