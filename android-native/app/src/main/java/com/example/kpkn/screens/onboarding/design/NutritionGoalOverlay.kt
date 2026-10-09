package com.example.kpkn.screens.onboarding.design

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.kpkn.screens.onboarding.design.entreno.plan.BlurOverlayDialog
import com.example.kpkn.ui.theme.Syne
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/*
 * Celebración del bloque de nutrición cuando hay una meta de peso (Definir o Volumen): una figura de frente con la
 * comida en órbita, la torre de KPKN que se arma y «Objetivo establecido». Usa las piezas de [ModuleCompleteOverlay]
 * (comida, figura, torre, insignia y fila de etapas) y su mismo contenedor, velo, botón y marcas de prueba.
 */

/** Ventana de la escena, en unidades de la escena (izquierda, arriba, derecha, abajo). */
private val GoalViewBox = Rect(12f, 44f, 188f, 136f)

/** Segundo en el que aparecen el título y la línea, y en el que se habilita «Continuar». */
private const val GOAL_TEXT_AT = 3.5f
private const val GOAL_CTA_AT = 3.9f

/** Radios de la órbita de la comida, por anillo (horizontal y vertical). */
private val GOAL_RX = floatArrayOf(66f, 74f, 82f)
private val GOAL_RY = floatArrayOf(22f, 27f, 30f)

/**
 * Overlay a pantalla completa de la meta. [note] es la fecha estimada («Fecha estimada: entre marzo y abril de 2027»);
 * null = sin fecha. [rootTag] y [ctaTag] son las mismas marcas de prueba que el overlay de siempre.
 */
@Composable
fun NutritionGoalOverlay(
    note: String?,
    onContinue: () -> Unit,
    onDismiss: () -> Unit = onContinue,
    stages: List<OverlayStage> = emptyList(),
    rootTag: String? = null,
    ctaTag: String? = null,
) {
    val scope = rememberCoroutineScope()
    val shownAnim = remember { Animatable(0f) }
    var leaving by remember { mutableStateOf(false) }
    // Un solo aviso por apertura: si la acción no avanza, el overlay vuelve a mostrarse (igual que ModuleCompleteOverlay).
    val leave: (() -> Unit) -> Unit = { action ->
        if (!leaving) {
            leaving = true
            scope.launch {
                shownAnim.animateTo(0f, tween(200))
                action()
                delay(700)
                leaving = false
                shownAnim.animateTo(1f, tween(200))
            }
        }
    }

    BlurOverlayDialog(
        onDismissRequest = { leave(onDismiss) },
        dismissOnBack = true,
        shown = { shownAnim.value },
    ) { blur ->
        val context = LocalContext.current
        val reduceMotion = remember {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }
        // Sin animaciones del sistema se muestra el cuadro final (torre armada, texto y botón).
        val settledT = max(GOAL_CTA_AT + 1f, GOAL_TEXT_AT + 3f)
        var t by remember { mutableFloatStateOf(if (reduceMotion) settledT else 0f) }
        if (!reduceMotion) {
            LaunchedEffect(Unit) {
                val start = withFrameNanos { it }
                while (isActive) withFrameNanos { now -> t = (now - start) / 1_000_000_000f }
            }
        }

        LaunchedEffect(Unit) { shownAnim.animateTo(1f, tween(280)) }
        val shown = shownAnim.value
        val textA by animateFloatAsState(if (t >= GOAL_TEXT_AT) 1f else 0f, tween(450), label = "text")
        val ctaA by animateFloatAsState(if (t >= GOAL_CTA_AT) 1f else 0f, tween(350), label = "cta")

        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .then(if (rootTag != null) Modifier.testTag(rootTag) else Modifier)
                .graphicsLayer { alpha = shown }
                // Sin desenfoque del sistema el fondo tapa casi todo, igual que el overlay de siempre.
                .background(Color(0xFF060606).copy(alpha = if (blur) 0.52f else 0.96f)),
            contentAlignment = Alignment.Center,
        ) {
            val aspect = GoalViewBox.width / GoalViewBox.height
            val sceneWidth = minOf(maxWidth * 0.9f, 380.dp, (maxHeight - 350.dp).coerceAtLeast(150.dp) * aspect)
            Column(
                Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Canvas(
                    Modifier
                        .width(sceneWidth)
                        .aspectRatio(aspect),
                ) {
                    val k = size.width / GoalViewBox.width
                    withTransform({
                        scale(k, k, Offset.Zero)
                        translate(-GoalViewBox.left, -GoalViewBox.top)
                    }) {
                        drawNutritionGoalScene(t)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "Objetivo establecido",
                    // LineBreak.Heading reparte las líneas por igual, como el título del overlay de siempre.
                    style = TextStyle(
                        fontFamily = Syne, fontWeight = FontWeight.ExtraBold, fontSize = 26.sp,
                        letterSpacing = (-0.01).em, lineHeight = 30.sp, color = WizardColors.text,
                        textAlign = TextAlign.Center, lineBreak = LineBreak.Heading,
                    ),
                    modifier = Modifier.graphicsLayer { alpha = textA; translationY = (1f - textA) * 28f },
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Te acompañaremos en todo el camino.",
                    style = TextStyle(
                        fontFamily = Syne, fontWeight = FontWeight.Medium, fontSize = 14.sp,
                        lineHeight = 20.sp, color = WizardColors.text.copy(alpha = 0.72f), textAlign = TextAlign.Center,
                    ),
                    modifier = Modifier.widthIn(max = 280.dp).graphicsLayer { alpha = textA; translationY = (1f - textA) * 16f },
                )
                if (note != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        note,
                        style = WizardTypography.note,
                        color = WizardColors.textMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.widthIn(max = 280.dp).graphicsLayer { alpha = textA; translationY = (1f - textA) * 16f },
                    )
                }
                if (stages.isNotEmpty()) {
                    Spacer(Modifier.height(22.dp))
                    StageTracker(
                        stages = stages,
                        startAt = GOAL_TEXT_AT,
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
                        containerColor = WizardColors.text, contentColor = WizardColors.ctaContent,
                        disabledContainerColor = WizardColors.text, disabledContentColor = WizardColors.ctaContent,
                    ),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .widthIn(min = 168.dp)
                        .then(if (ctaTag != null) Modifier.testTag(ctaTag) else Modifier)
                        .graphicsLayer { alpha = ctaA; translationY = (1f - ctaA) * 20f },
                ) {
                    Text("Continuar", fontFamily = Syne, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                }
            }
        }
    }
}

/**
 * Escena de la meta, en el cuadro de [GoalViewBox]: 0–2,3 s la figura de frente con la comida en órbita; 2,3–2,95 s la
 * comida converge y la torre cae y asienta; 3,0 s la insignia. Pública dentro del módulo para las hojas de render.
 */
internal fun DrawScope.drawNutritionGoalScene(t: Float) {
    val cx = 100f
    val cy = 90f
    val enter = eOutCubic(seg(t, 0f, 1f))
    val merge = eInOut(seg(t, 2.3f, 2.95f))

    // La figura de frente aparece en el centro y se desvanece cuando la torre empieza a armarse.
    val figureIn = eOutBack(seg(t, 0f, .8f))
    val figureAlpha = seg(t, 0f, .4f) * (1f - seg(t, 2.3f, 2.7f))

    class Placed(
        val f: Food, val x: Float, val y: Float, val s: Float, val rot: Float, val a: Float, val front: Boolean, val depth: Float,
    )
    val placed = FOODS.mapIndexed { i, f ->
        val ang = f.phase + ORBIT_W[f.orbit] * t
        val rf = (1f + .6f * (1f - enter)) * lerpF(1f, .08f, merge)
        val depth = (sin(ang) + 1f) / 2f
        Placed(
            f,
            cx + GOAL_RX[f.orbit] * rf * cos(ang),
            cy + GOAL_RY[f.orbit] * rf * sin(ang) - (1f - enter) * 16f,
            lerpF(.72f, 1.12f, depth) * lerpF(1f, .25f, merge) * 1.35f,
            sin(t * 1.7f + i) * 12f,
            seg(t, .05f * i, .05f * i + .4f) * lerpF(.55f, 1f, depth) * (1f - seg(t, 2.6f, 2.8f)),
            sin(ang) > 0f,
            depth,
        )
    }.sortedBy { it.depth }

    // La torre: cada anillo cae y asienta; el brillo verde dura un instante y luego queda en tinta.
    val glow = max(0f, spring(t - 2.95f, 2.4f, 0f)) * .7f
    val fill = mix(WizardColors.text, WizardColors.done, glow)
    val discs = (0..2).map { i ->
        val t0 = 2.45f + .15f * i
        val land = t0 + .3f
        var q = .2f * spring(t - land, 6f, 20f)
        for (j in i + 1..2) q += .06f * spring(t - (2.5f + .15f * j), 7f, 22f)
        Disc(
            y = (1f - eInQuad(seg(t, t0, land))) * -40f,
            q = q,
            a = if (t < t0) 0f else 1f,
            s = 1f + .06f * max(0f, spring(t - 2.95f - .06f * i, 5f, 12f)) + .012f * sin(t * 2.2f + i),
        )
    }

    placed.filter { !it.front }.forEach { drawFood(it.f, it.x, it.y, it.s, it.rot, it.a) }
    if (figureAlpha > 0f) {
        drawIcon(
            IC_USER, cx, cy + sin(t * 2f) * .8f * seg(t, .8f, 1.2f), 62f, WizardColors.text, 1.6f,
            lerpF(.85f, 1f, figureIn), figureAlpha,
        )
    }
    // La torre se centra en (100, 90): su origen vertical es 46 unidades por encima del centro de los anillos.
    drawTower(50f, cy - 46f, discs, listOf(fill, fill, fill))
    placed.filter { it.front }.forEach { drawFood(it.f, it.x, it.y, it.s, it.rot, it.a) }
    drawBadge(cx + 16f, cy - 24f, 9f, eOutBack(seg(t, 3.0f, 3.35f)))
}
