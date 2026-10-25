package com.example.kpkn.screens.onboarding.design

/*
 * Prólogo del alta («ANTES DE EMPEZAR»): explica por qué pedimos datos antes de la pantalla de arranque
 * (`ModuleCompleteOverlay` con `KpknModule.INTRO`). Mismo contenedor y velo que los overlays de pantalla completa
 * ([BlurOverlayDialog]) y el mismo botón de llamada a la acción.
 *
 * La torre del logo se arma de abajo arriba (cada disco cae y asienta; 900 ms, escalonados de 150 ms) y, debajo, aparecen
 * las tres razones con su mini-símbolo. «Vamos» se habilita a los 1,8 s. Con movimiento reducido, el cuadro final al instante.
 */

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPen
import com.example.kpkn.screens.onboarding.design.entreno.SymbolStroke
import com.example.kpkn.screens.onboarding.design.entreno.dumbbell
import com.example.kpkn.screens.onboarding.design.entreno.plan.BlurOverlayDialog
import com.example.kpkn.screens.onboarding.design.entreno.plan.OverlayScrim
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Tiempos del prólogo, en segundos desde que aparece. */
private const val PROLOGUE_STAGGER = 0.15f
private const val PROLOGUE_FALL = 0.6f
private const val PROLOGUE_DROP = 22f
private const val PROLOGUE_TEXT_AT = 0.9f
private const val PROLOGUE_NOTE_AT = 1.7f
private const val PROLOGUE_CTA_AT = 1.8f
private const val PROLOGUE_SETTLED = 2.2f
private const val PROLOGUE_REVEAL = 0.3f

/** Instante en que aparece cada razón: la mancuerna, el plato y la torre de RINGS. */
private val PROLOGUE_REASON_AT = listOf(1.2f, 1.35f, 1.5f)

/** Caja de la torre en unidades de la escena de `ModuleCompleteOverlay` (incluye la caída desde arriba). */
private val PROLOGUE_TOWER_BOX = Rect(54f, 76f, 146f, 150f)
private val PROLOGUE_TOWER_WIDTH = 180.dp
private val PROLOGUE_TOWER_INK = listOf(Color(0xFFF2EEE6), Color(0xFFF2EEE6), Color(0xFFF2EEE6))

/** Los tres discos de la torre en el instante [t]: de abajo arriba, cada uno cae y asienta con su rebote. */
private fun prologueTowerDiscs(t: Float): List<Disc> = List(3) { i ->
    val start = PROLOGUE_STAGGER * i
    val land = start + PROLOGUE_FALL
    Disc(
        y = (1f - eInQuad(seg(t, start, land))) * -PROLOGUE_DROP,
        q = .2f * spring(t - land, 6f, 20f),
        a = if (t < start) 0f else 1f,
    )
}

/** Aparece con un fundido y una subida de 8 dp (300 ms) cuando el reloj llega a [at]. */
private fun Modifier.prologueReveal(at: Float, time: () -> Float): Modifier = graphicsLayer {
    val a = eOutCubic(seg(time(), at, at + PROLOGUE_REVEAL))
    alpha = a
    translationY = (1f - a) * 8.dp.toPx()
}

/** Las tres razones, cada una con su mini-símbolo y su acento de módulo. */
private enum class PrologueSymbol(val accent: Color) {
    MUSCULO(SymbolPalette.musculo),
    ENERGIA(SymbolPalette.energia),
    COLUMNA(SymbolPalette.columna),
}

/** Mini-símbolo de 28 dp: lienzo lógico de 64 unidades, dibujado con el pincel del kit de símbolos (un `SymbolPen` por razón). */
@Composable
private fun PrologueSymbolIcon(symbol: PrologueSymbol) {
    val pen = remember { SymbolPen() }
    Canvas(Modifier.size(28.dp)) {
        pen.begin(this, 1f, symbol.accent)
        val k = size.width / 64f
        pen.scaled(k, k, 0f, 0f) {
            when (symbol) {
                PrologueSymbol.MUSCULO -> pen.dumbbell(32f, 32f, 26f, -28f, heads = pen.accent, handle = pen.accent, w = SymbolStroke.BODY)
                PrologueSymbol.ENERGIA -> prologuePlateAndCutlery(pen)
                PrologueSymbol.COLUMNA -> prologueMiniTower(pen)
            }
        }
    }
}

/** Plato con tenedor a la izquierda y cuchillo a la derecha. */
private fun prologuePlateAndCutlery(pen: SymbolPen) {
    val a = pen.accent
    pen.ring(32f, 32f, 17f, a, SymbolStroke.BODY)
    pen.ring(32f, 32f, 9f, a, SymbolStroke.LINE)
    pen.line(4f, 6f, 4f, 20f, a, SymbolStroke.LINE)
    pen.line(7f, 6f, 7f, 20f, a, SymbolStroke.LINE)
    pen.line(10f, 6f, 10f, 20f, a, SymbolStroke.LINE)
    pen.poly3(4f, 20f, 7f, 24f, 10f, 20f, a, SymbolStroke.LINE)
    pen.line(7f, 24f, 7f, 58f, a, SymbolStroke.BODY)
    pen.line(57f, 6f, 57f, 58f, a, SymbolStroke.BODY)
    pen.curve(57f, 6f, 63f, 18f, 57f, 30f, a, SymbolStroke.LINE)
}

/** La torre del logo en miniatura: sus tres discos de la geometría de `ModuleCompleteOverlay`, reducidos a 56 unidades. */
private fun prologueMiniTower(pen: SymbolPen) {
    val s = 56f / 76f
    pen.shifted(32f - 100f * s, 32f - 121.75f * s) {
        pen.scaled(s, s, 0f, 0f) {
            for (i in 0..2) pen.fillPath(discPath(i, 50f, 72f, Disc()), pen.accent)
        }
    }
}

/** Una razón: su mini-símbolo a la izquierda y su línea, con la aparición escalonada de [at]. */
@Composable
private fun PrologueReason(symbol: PrologueSymbol, text: String, at: Float, time: () -> Float) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .prologueReveal(at, time),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        PrologueSymbolIcon(symbol)
        Text(text, style = WizardTypography.milestoneBody, color = WizardColors.text, modifier = Modifier.weight(1f))
    }
}

/**
 * El contenido del prólogo: la torre, la etiqueta, el título, las tres razones y la nota, con «Vamos» al pie y alineado a la
 * izquierda. [time] es el reloj de la escena en segundos: se lee dentro de los dibujos, así que solo repinta lo que cambia.
 */
@Composable
internal fun WizardPrologueContent(time: () -> Float, onStart: () -> Unit) {
    val ready by remember { derivedStateOf { time() >= PROLOGUE_CTA_AT } }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("setup-prologue")
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = WizardSpacing.gutter, vertical = 24.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Canvas(Modifier.width(PROLOGUE_TOWER_WIDTH).aspectRatio(PROLOGUE_TOWER_BOX.width / PROLOGUE_TOWER_BOX.height)) {
                val k = size.width / PROLOGUE_TOWER_BOX.width
                withTransform({
                    scale(k, k, Offset.Zero)
                    translate(-PROLOGUE_TOWER_BOX.left, -PROLOGUE_TOWER_BOX.top)
                }) {
                    drawTower(50f, 72f, prologueTowerDiscs(time()), PROLOGUE_TOWER_INK)
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(
                text = "ANTES DE EMPEZAR",
                style = WizardTypography.eyebrow,
                color = WizardColors.textFaint,
                modifier = Modifier.prologueReveal(PROLOGUE_TEXT_AT, time),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Para desbloquear todo KPKN, necesitamos conocerte",
                style = WizardTypography.stepTitle,
                color = WizardColors.text,
                modifier = Modifier.prologueReveal(PROLOGUE_TEXT_AT, time),
            )
            Spacer(Modifier.height(24.dp))
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                PrologueReason(PrologueSymbol.MUSCULO, "Un programa de entreno a tu nivel y a lo que buscas.", PROLOGUE_REASON_AT[0], time)
                PrologueReason(PrologueSymbol.ENERGIA, "Tus calorías del día, para registrar lo que comes.", PROLOGUE_REASON_AT[1], time)
                PrologueReason(PrologueSymbol.COLUMNA, "Tu historial, con resultados y sensaciones registrados.", PROLOGUE_REASON_AT[2], time)
            }
            Spacer(Modifier.height(20.dp))
            Text(
                text = "Te tomará unos minutos.",
                style = WizardTypography.note,
                color = WizardColors.textMuted,
                modifier = Modifier.prologueReveal(PROLOGUE_NOTE_AT, time),
            )
        }
        // El mismo botón de llamada a la acción que el de la pantalla de arranque: píldora circular de tinta cálida.
        Button(
            onClick = onStart,
            enabled = ready,
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = WizardColors.cta,
                contentColor = WizardColors.ctaContent,
                disabledContainerColor = WizardColors.cta,
                disabledContentColor = WizardColors.ctaContent,
            ),
            modifier = Modifier
                .heightIn(min = WizardSpacing.touchTarget)
                .widthIn(min = 168.dp)
                .testTag("setup-prologue-start")
                .graphicsLayer {
                    val a = seg(time(), PROLOGUE_CTA_AT, PROLOGUE_CTA_AT + 0.35f)
                    alpha = eOutCubic(a)
                    translationY = (1f - eOutCubic(a)) * 20f
                },
        ) {
            Text("Vamos", style = WizardTypography.cta)
        }
    }
}

/**
 * El prólogo a pantalla completa, sobre la página del alta. «Vamos» lo cierra con su fundido y deja paso a la pantalla de
 * arranque; atrás del sistema ([onBack]) hace lo mismo que atrás en esa pantalla.
 */
@Composable
fun WizardPrologueOverlay(onStart: () -> Unit, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val shownAnim = remember { Animatable(0f) }
    var leaving by remember { mutableStateOf(false) }
    // Un solo aviso por apertura, como el overlay de arranque: el desvanecido tarda unos milisegundos.
    val leave: (() -> Unit) -> Unit = { action ->
        if (!leaving) {
            leaving = true
            scope.launch {
                shownAnim.animateTo(0f, tween(200))
                action()
                // Si el prólogo sigue aquí, la acción no avanzó: vuelve a mostrarse en lugar de quedar invisible.
                delay(700)
                leaving = false
                shownAnim.animateTo(1f, tween(200))
            }
        }
    }

    BlurOverlayDialog(
        onDismissRequest = { leave(onBack) },
        dismissOnBack = true,
        shown = { shownAnim.value },
    ) { blur ->
        val reduced = wizardReducedMotion()
        // Un único reloj para toda la escena. Con movimiento reducido, el cuadro final desde el primer fotograma.
        var t by remember(reduced) { mutableFloatStateOf(if (reduced) PROLOGUE_SETTLED else 0f) }
        if (!reduced) {
            LaunchedEffect(Unit) {
                val start = withFrameNanos { it }
                while (isActive && t < PROLOGUE_SETTLED) withFrameNanos { now -> t = (now - start) / 1_000_000_000f }
            }
        }
        LaunchedEffect(Unit) { shownAnim.animateTo(1f, tween(280)) }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = shownAnim.value }
                // El mismo velo que el overlay de arranque: sin desenfoque del sistema, debe tapar casi todo.
                .background(OverlayScrim.copy(alpha = if (blur) 0.52f else 0.96f)),
        ) {
            WizardPrologueContent(time = { t }, onStart = { leave(onStart) })
        }
    }
}
