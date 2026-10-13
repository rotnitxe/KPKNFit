package com.example.kpkn.ui.components.permissions

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardSpacing
import com.example.kpkn.screens.onboarding.design.WizardTypography
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.onboarding.design.entreno.plan.BlurOverlayDialog
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion

/** Duración del balanceo de la campana: una sola oscilación de ±8°. */
private const val BELL_SWING_MILLIS = 900

/** Esquinas del panel del diálogo. */
private val PanelShape = RoundedCornerShape(28.dp)

/**
 * Diálogo «Activa tus avisos»: pide los permisos de avisos que faltan con la estética del asistente (vidrio, símbolo y tinta
 * de la marca), en lugar del `AlertDialog` de Material. [missing] son los nombres de los permisos que faltan; los decide quien
 * llama. Como antes, no se cierra al tocar fuera ni con «atrás»: solo con una de sus dos acciones.
 */
@Composable
fun RecommendedPermissionsDialog(
    missing: List<String>,
    onOpenSettings: () -> Unit,
    onSkip: () -> Unit,
) {
    // Con movimiento reducido, cuadro final: velo ya visible y campana en reposo, sin balanceo.
    val reduced = wizardReducedMotion()
    val shownAnim = remember(reduced) { Animatable(if (reduced) 1f else 0f) }
    val bellAnim = remember(reduced) { Animatable(0f) }
    LaunchedEffect(reduced) {
        if (!reduced) {
            shownAnim.animateTo(1f, tween(280))
            bellAnim.animateTo(
                0f,
                keyframes {
                    durationMillis = BELL_SWING_MILLIS
                    8f at 240
                    -8f at 600
                    0f at BELL_SWING_MILLIS
                },
            )
        }
    }
    BlurOverlayDialog(
        onDismissRequest = {},
        dismissOnBack = false,
        shown = { shownAnim.value },
    ) { blur ->
        RecommendedPermissionsOverlay(
            missing = missing,
            blur = blur,
            shown = { shownAnim.value },
            bellAngle = { bellAnim.value },
            onOpenSettings = onOpenSettings,
            onSkip = onSkip,
        )
    }
}

/**
 * El contenido del diálogo sin su ventana: el velo y el panel. Lo pinta [RecommendedPermissionsDialog] dentro de
 * [BlurOverlayDialog]; las hojas de render lo llaman directamente. [shown] y [bellAngle] se leen al dibujar, sin recomponer.
 */
@Composable
internal fun RecommendedPermissionsOverlay(
    missing: List<String>,
    blur: Boolean,
    shown: () -> Float,
    bellAngle: () -> Float,
    onOpenSettings: () -> Unit,
    onSkip: () -> Unit,
) {
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = shown() }
            // Con desenfoque del sistema, velo negro al 60 %; sin él, casi opaco para que el texto de atrás no se mezcle.
            .background(WizardColors.background.copy(alpha = if (blur) 0.6f else 0.96f))
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        RecommendedPermissionsPanel(
            missing = missing,
            bellAngle = bellAngle,
            onOpenSettings = onOpenSettings,
            onSkip = onSkip,
            modifier = Modifier.widthIn(max = 420.dp).heightIn(max = maxHeight),
        )
    }
}

/** El panel de vidrio neutro: campana, título, texto, una fila por permiso y las dos acciones. Se desplaza si no cabe. */
@Composable
private fun RecommendedPermissionsPanel(
    missing: List<String>,
    bellAngle: () -> Float,
    onOpenSettings: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .testTag("permissions-dialog")
            .background(WizardColors.glassFill, PanelShape)
            .border(1.dp, WizardColors.glassBorder, PanelShape)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        PermissionBell(angle = bellAngle)
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Activa tus avisos",
            style = WizardTypography.stepTitle,
            color = WizardColors.text,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Te avisamos de tus entrenos y del final de cada descanso, aunque la app esté en segundo plano.",
            style = WizardTypography.body,
            color = WizardColors.textMuted,
        )
        if (missing.incluyeMicrofono()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "El micrófono activa los comandos por voz.",
                style = WizardTypography.body,
                color = WizardColors.textMuted,
            )
        }
        Spacer(Modifier.height(20.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            missing.forEach { PermissionRow(name = it) }
        }
        Spacer(Modifier.height(24.dp))
        PermissionsPill(label = "Ir a Ajustes", tag = "permissions-open-settings", onClick = onOpenSettings)
        Spacer(Modifier.height(4.dp))
        PermissionsTextAction(label = "Ahora no", tag = "permissions-skip", onClick = onSkip)
    }
}

/**
 * Campana de línea de 40 dp en el acento de energía: gancho, cúpula, borde y badajo. Gira alrededor del gancho con [angle]
 * (grados). Lee el ángulo al dibujar, así el balanceo no recompone el panel.
 */
@Composable
private fun PermissionBell(angle: () -> Float) {
    val path = remember { Path() }
    Canvas(
        Modifier
            .size(40.dp)
            .graphicsLayer {
                rotationZ = angle()
                transformOrigin = TransformOrigin(0.5f, 0.1f)
            },
    ) {
        val w = size.width
        val h = size.height
        val line = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        path.reset()
        path.moveTo(w * 0.22f, h * 0.66f)
        path.cubicTo(w * 0.22f, h * 0.40f, w * 0.32f, h * 0.18f, w * 0.5f, h * 0.18f)
        path.cubicTo(w * 0.68f, h * 0.18f, w * 0.78f, h * 0.40f, w * 0.78f, h * 0.66f)
        path.lineTo(w * 0.86f, h * 0.76f)
        path.lineTo(w * 0.14f, h * 0.76f)
        path.close()
        drawPath(path, SymbolPalette.energia, style = line)
        drawCircle(SymbolPalette.energia, radius = w * 0.05f, center = Offset(w * 0.5f, h * 0.1f), style = line)
        val r = w * 0.11f
        drawArc(
            color = SymbolPalette.energia,
            startAngle = 0f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(w * 0.5f - r, h * 0.76f - r),
            size = Size(r * 2f, r * 2f),
            style = line,
        )
    }
}

/** Una fila por permiso que falta: un punto de 6 dp en energía y el nombre en Inter 600 15 sp. Sin caja. */
@Composable
private fun PermissionRow(name: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).background(SymbolPalette.energia, CircleShape))
        Spacer(Modifier.width(12.dp))
        Text(text = name, style = WizardTypography.header, color = WizardColors.text)
    }
}

/** Píldora principal: 56 dp de alto, tinta cálida y texto negro. Se apaga un poco mientras se pulsa. */
@Composable
private fun PermissionsPill(label: String, tag: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        Modifier
            .fillMaxWidth()
            .height(WizardSpacing.ctaHeight)
            .graphicsLayer { alpha = if (pressed) 0.85f else 1f }
            .background(WizardColors.text, RoundedCornerShape(28.dp))
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, style = WizardTypography.cta, color = WizardColors.ctaContent)
    }
}

/** Acción de texto «Ahora no»: 48 dp como mínimo, sin fondo ni borde. */
@Composable
private fun PermissionsTextAction(label: String, tag: String, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = WizardSpacing.touchTarget)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, style = WizardTypography.cta, color = WizardColors.textMuted)
    }
}

/** La lista lleva nombres en texto, no códigos: el micrófono se reconoce por su nombre. */
private fun List<String>.incluyeMicrofono(): Boolean = any { it.contains("micrófono", ignoreCase = true) }
