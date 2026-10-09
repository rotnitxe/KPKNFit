package com.example.kpkn.screens.onboarding.design

/*
 * Nota de vidrio «¿Por qué lo preguntamos?»: un bloque anclado bajo el enlace que la abre, con el mismo cristal de la
 * cabecera. Un bloque dentro de la página no puede desenfocar lo que tiene detrás, así que el anfitrión pinta la capa
 * como hermana de la fuente de desenfoque ([WizardGlassNoteLayer]), igual que la cabecera.
 */

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.entreno.plan.plainClickable
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import kotlin.math.max
import kotlin.math.roundToInt

/** Lo que dice una nota abierta y el rectángulo del enlace que la abrió (coordenadas de la raíz). */
internal class WizardGlassNoteContent(val anchor: Rect, val title: String, val body: String)

/**
 * Estado de la nota de vidrio del anfitrión: [show] la abre bajo el enlace [anchor] y [dismiss] la cierra. [note] conserva
 * el último contenido al cerrar, para que la salida se pinte con el mismo texto.
 */
internal class WizardGlassNoteController {
    var isOpen by mutableStateOf(false)
        private set

    var note by mutableStateOf<WizardGlassNoteContent?>(null)
        private set

    fun show(anchor: Rect, title: String, body: String) {
        note = WizardGlassNoteContent(anchor, title, body)
        isOpen = true
    }

    fun dismiss() {
        isOpen = false
    }
}

/** Fuera de un anfitrión no hay capa que pinte la nota: el enlace no hace nada visible (y no falla). */
private val NoWizardGlassNote = WizardGlassNoteController()

/** El controlador de la nota de vidrio, publicado por el anfitrión del wizard. */
internal val LocalWizardGlassNote = staticCompositionLocalOf { NoWizardGlassNote }

/**
 * Fuerza si la nota tiene desenfoque: `null` (lo normal) lo decide la versión de Android; lo pone la hoja de render para dibujar
 * el camino sin desenfoque, que Robolectric no dibuja.
 */
internal val LocalWizardGlassBlur = compositionLocalOf<Boolean?> { null }

/** Radio del bloque, separación con el enlace y relleno interior. */
private val WizardGlassNoteShape = RoundedCornerShape(24.dp)
private val WizardGlassNoteGap = 8.dp
private val WizardGlassNotePadding = 20.dp

/**
 * Velo sobre el cristal y bajo el contenido: con desenfoque (Android 12+) deja ver la página difuminada; sin él (Android < 12,
 * minSdk 24) es opaco, así el texto de la página de atrás no asoma dentro del bloque.
 */
private val WizardGlassNoteVeilBlur = Color(0xFF141414).copy(alpha = 0.55f)
private val WizardGlassNoteVeilPlain = Color(0xFF141414)

/** Dónde cae la nota en la última medida: la escala crece desde el borde del enlace. Lo escribe la medida y lo lee el dibujo. */
private class WizardGlassNotePlacement {
    var pivotX = 0.5f
    var pivotY = 0f
}

/**
 * La capa de la nota de vidrio, que el anfitrión pinta como HERMANA de la fuente de desenfoque. Mientras la nota está abierta,
 * un velo transparente a pantalla completa la cierra al tocar fuera; el bloque va bajo el enlace si cabe, y si no, encima.
 * Entra con escala 0,92→1 y fundido (220 ms, desde el enlace) y sale en 160 ms. Con movimiento reducido, solo fundido.
 * Cerrada y ya fundida no compone nada.
 */
@Composable
internal fun WizardGlassNoteLayer(controller: WizardGlassNoteController, hazeState: HazeState) {
    val reduced = wizardReducedMotion()
    val progress = remember { Animatable(0f) }
    val placement = remember { WizardGlassNotePlacement() }
    val focus = remember { FocusRequester() }
    var layerOrigin by remember { mutableStateOf(Offset.Zero) }
    val open = controller.isOpen
    val content = controller.note

    LaunchedEffect(open) {
        progress.animateTo(if (open) 1f else 0f, tween(durationMillis = if (open) 220 else 160, easing = FastOutSlowInEasing))
    }
    // Al abrir, el foco va al título: TalkBack lo anuncia y la nota se lee desde el principio.
    LaunchedEffect(open) {
        if (open) runCatching { focus.requestFocus() }
    }
    BackHandler(enabled = open) { controller.dismiss() }

    if (content == null || (!open && progress.value <= 0f)) return

    val density = LocalDensity.current
    val gutterPx = with(density) { WizardSpacing.gutter.roundToPx() }
    val gapPx = with(density) { WizardGlassNoteGap.roundToPx() }
    val bottomPx = with(density) { WizardDockClearance.roundToPx() }
    val topPx = WindowInsets.statusBars.getTop(density) + with(density) { WizardHeaderBlockHeight.roundToPx() }
    // Con desenfoque (Android 12+) el velo deja ver la página difuminada; sin él, opaco.
    val blur = LocalWizardGlassBlur.current ?: (Build.VERSION.SDK_INT >= 31)

    Box(modifier = Modifier.fillMaxSize().onGloballyPositioned { layerOrigin = it.positionInRoot() }) {
        // El velo: transparente, solo recoge el toque fuera de la nota. Mientras se cierra, la página vuelve a recibir toques.
        if (open) {
            Box(Modifier.fillMaxSize().pointerInput(controller) { detectTapGestures { controller.dismiss() } })
        }
        Column(
            modifier = Modifier
                .layout { measurable, constraints ->
                    // Ancho de página menos un gutter a cada lado; la altura, la que pida el texto.
                    val width = constraints.maxWidth - 2 * gutterPx
                    val block = measurable.measure(
                        Constraints(minWidth = width, maxWidth = width, minHeight = 0, maxHeight = constraints.maxHeight),
                    )
                    val anchor = content.anchor.translate(-layerOrigin.x, -layerOrigin.y)
                    val below = anchor.bottom + gapPx + block.height <= constraints.maxHeight - bottomPx
                    val wanted = if (below) anchor.bottom + gapPx else anchor.top - gapPx - block.height
                    val y = wanted.roundToInt().coerceIn(topPx, max(topPx, constraints.maxHeight - block.height - bottomPx))
                    placement.pivotX = if (width > 0) ((anchor.center.x - gutterPx) / width).coerceIn(0f, 1f) else 0.5f
                    placement.pivotY = if (below) 0f else 1f
                    layout(constraints.maxWidth, constraints.maxHeight) { block.place(gutterPx, y) }
                }
                .graphicsLayer {
                    val p = progress.value
                    alpha = p
                    if (!reduced) {
                        val s = 0.92f + 0.08f * p
                        scaleX = s
                        scaleY = s
                    }
                    transformOrigin = TransformOrigin(placement.pivotX, placement.pivotY)
                }
                .clip(WizardGlassNoteShape)
                // Cristal: el desenfoque de la página que hay detrás y, encima, el velo (debajo del contenido).
                .hazeEffect(state = hazeState, style = wizardHazeStyle())
                .background(if (blur) WizardGlassNoteVeilBlur else WizardGlassNoteVeilPlain)
                .border(1.dp, WizardColors.glassBorder, WizardGlassNoteShape)
                // Un toque dentro de la nota no llega al velo: solo cierra «Entendido», atrás o tocar fuera.
                .pointerInput(Unit) { detectTapGestures() }
                .semantics { paneTitle = content.title }
                .testTag("setup-glass-note")
                .padding(WizardGlassNotePadding),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    tint = WizardColors.textMuted,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = content.title,
                        style = WizardTypography.controlLabel,
                        color = WizardColors.text,
                        modifier = Modifier
                            .semantics { heading() }
                            .focusRequester(focus)
                            .focusable(),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(text = content.body, style = WizardTypography.note, color = WizardColors.textMuted)
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Box(
                    modifier = Modifier
                        .heightIn(min = WizardSpacing.touchTarget)
                        .defaultMinSize(minWidth = WizardSpacing.touchTarget)
                        .testTag("setup-glass-note-ok")
                        .plainClickable(role = Role.Button, onClickLabel = "Cerrar la nota") { controller.dismiss() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Entendido", style = WizardTypography.controlLabel, color = WizardColors.text)
                }
            }
        }
    }
}
