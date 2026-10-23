package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknSegmentedControl
import com.example.kpkn.ui.design.KpknShape
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknType

/** Los dos espacios que puede tener una sesión: Fuerza y Cardio. La persona entrena uno, el otro o los dos. */
internal enum class SessionEditorSpace { STRENGTH, CARDIO }

/**
 * Sesión vacía (ED-A · A2): solo dos opciones, Fuerza y Cardio, cada una con su símbolo animado. Cada una hace lo mismo que antes:
 * [onChooseStrength] da de alta el espacio de fuerza y [onChooseCardio] el de cardio. Ninguna se describe en función de la otra.
 */
@Composable
internal fun SessionEditorEmptyState(
    onChooseStrength: () -> Unit,
    onChooseCardio: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("session_editor_empty_state"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "Elige por dónde empezar.",
            style = KpknType.caption,
            color = WizardColors.textMuted,
            modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
        )
        SessionEditorSpaceOption(space = SessionEditorSpace.STRENGTH, onClick = onChooseStrength)
        SessionEditorSpaceOption(space = SessionEditorSpace.CARDIO, onClick = onChooseCardio)
    }
}

/**
 * Una opción que todavía no está en la sesión (la pendiente, tras elegir la otra, o una de las dos de la sesión vacía): un módulo con el
 * símbolo animado de [space] (la barra que se alza para Fuerza, el latido para Cardio), su nombre y el «+». Con movimiento reducido el
 * símbolo queda quieto. Su zona táctil es todo el módulo, de más de 100 dp.
 */
@Composable
internal fun SessionEditorSpaceOption(
    space: SessionEditorSpace,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strength = space == SessionEditorSpace.STRENGTH
    val accent = if (strength) SymbolPalette.musculo else SymbolPalette.columna
    val title = if (strength) "Fuerza" else "Cardio"
    val subtitle = if (strength) "Ejercicios, grupos y superseries" else "Continuo, series o intervalos"
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 104.dp)
            .testTag(if (strength) "session_editor_option_strength" else "session_editor_option_cardio")
            .clip(KpknShape.module)
            .background(KpknSurface.module, KpknShape.module)
            .border(1.dp, KpknSurface.moduleBorder, KpknShape.module)
            .clickable(role = Role.Button, onClickLabel = "Añadir $title", onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AnimatedSpaceSymbol(strength = strength, accent = accent)
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = KpknType.title, color = WizardColors.text)
            Spacer(Modifier.height(2.dp))
            Text(text = subtitle, style = KpknType.caption, color = WizardColors.textMuted)
        }
        Icon(
            imageVector = KpknLineIcons.Plus,
            contentDescription = null,
            tint = WizardColors.text,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** El símbolo de 64 dp: la barra que se alza (Fuerza) o el corazón que late (Cardio). El movimiento se lee al dibujar, sin recomponer. */
@Composable
private fun AnimatedSpaceSymbol(strength: Boolean, accent: Color) {
    val reduced = wizardReducedMotion()
    val phase = if (reduced) {
        null
    } else {
        rememberInfiniteTransition(label = "spaceSymbol").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = if (strength) 2100 else 1500, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "spacePhase",
        )
    }
    Box(
        modifier = Modifier
            .size(64.dp)
            .graphicsLayer {
                val p = phase?.value ?: 0f
                if (strength) {
                    // La barra sube y baja unos dp y se inclina un poco, como si se alzara.
                    translationY = (2f - 6f * p).dp.toPx()
                    rotationZ = -4f + 6f * p
                } else {
                    // El corazón late: crece un 8 % y vuelve.
                    scaleX = 1f + 0.08f * p
                    scaleY = 1f + 0.08f * p
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (strength) BarbellSymbol else HeartPulseSymbol,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(if (strength) 38.dp else 36.dp),
        )
    }
}

private fun symbol(name: String, vararg paths: String): ImageVector {
    val ink = SolidColor(Color.Black)
    val builder = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
    for (d in paths) {
        builder.addPath(
            pathData = PathParser().parsePathString(d).toNodes(),
            stroke = ink,
            strokeLineWidth = 1.7f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }
    return builder.build()
}

private val BarbellSymbol: ImageVector by lazy {
    symbol("KpknEditor.Barbell", "M3 12h18", "M5 8v8", "M8 6v12", "M16 6v12", "M19 8v8")
}

private val HeartPulseSymbol: ImageVector by lazy {
    symbol(
        "KpknEditor.HeartPulse",
        "M20.8 4.6a5.5 5.5 0 0 0-7.8 0L12 5.7l-1.1-1.1a5.5 5.5 0 0 0-7.8 7.8L12 21l8.8-8.6a5.5 5.5 0 0 0 0-7.8Z",
        "M3 11h4l2-4 4 9 2-5h6",
    )
}

/**
 * La colocación del cardio, «Al inicio / Al final» (ED-A · A2): solo existe cuando la sesión tiene Fuerza y Cardio. [atStart] dice cuál
 * está elegido y [onChange] avisa del nuevo.
 */
@Composable
internal fun SessionEditorCardioPlacementControl(
    atStart: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("session_editor_cardio_placement"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "CARDIO EN LA SESIÓN",
            style = KpknType.eyebrow,
            color = WizardColors.textMuted,
            modifier = Modifier.padding(start = 4.dp),
        )
        KpknSegmentedControl(
            options = listOf("Al inicio", "Al final"),
            selectedIndex = if (atStart) 0 else 1,
            onSelect = { index -> onChange(index == 0) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
