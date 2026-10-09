package com.example.kpkn.screens.onboarding.rings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardSpacing
import com.example.kpkn.screens.onboarding.design.WizardTypography
import com.example.kpkn.screens.onboarding.design.entreno.SymbolArt
import com.example.kpkn.screens.onboarding.design.entreno.SymbolCanvas
import com.example.kpkn.screens.onboarding.design.entreno.SymbolClock

/*
 * Piezas comunes de las pantallas de las preguntas de RINGS: símbolos de elección única (radio), acciones de texto y el
 * interruptor de texto de «No lo sé». Todo sin caja: símbolos sobre la página negra.
 */

/** Reducción de la pulsación de un símbolo mientras se mantiene el dedo encima. */
private const val RADIO_PRESS_SCALE = 0.95f

/**
 * Un símbolo de elección única: dibujo con [SymbolCanvas], etiqueta opcional debajo y `Role.RadioButton`. Sin tarjeta. [description]
 * es lo que lee TalkBack («Músculos: Cargados»); el estado seleccionado lo da `selectable`. Solo el elegido se anima (el reloj
 * [clock] lo gobierna la pantalla).
 */
@Composable
internal fun RingsRadioSymbol(
    art: SymbolArt,
    description: String,
    tag: String,
    selected: Boolean,
    clock: SymbolClock,
    reducedMotion: Boolean,
    onSelect: () -> Unit,
    canvasSize: Dp,
    artScale: Float,
    modifier: Modifier = Modifier,
    label: String? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(
        targetValue = if (pressed && !reducedMotion) RADIO_PRESS_SCALE else 1f,
        animationSpec = if (reducedMotion) snap() else spring(dampingRatio = 0.6f, stiffness = 500f),
        label = "ringsRadioPress",
    )
    Column(
        modifier = modifier
            .testTag(tag)
            .selectable(
                selected = selected,
                interactionSource = interaction,
                indication = null,
                role = Role.RadioButton,
                onClick = onSelect,
            )
            // Va DESPUÉS de `selectable`: conserva su rol y su estado y descarta el texto de la etiqueta (se lee una vez).
            .clearAndSetSemantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.graphicsLayer {
            scaleX = press
            scaleY = press
        }) {
            SymbolCanvas(
                art = art,
                selected = selected,
                clock = clock,
                reducedMotion = reducedMotion,
                modifier = Modifier.height(canvasSize).fillMaxWidth(),
                artScale = artScale,
            )
        }
        if (label != null) {
            Text(
                text = label,
                style = WizardTypography.note,
                color = if (selected) WizardColors.text else WizardColors.textMuted,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
        }
    }
}

/** Acción de texto sin caja, de 48 dp de alto como mínimo, alineada a la derecha (p. ej. «Prefiero no decirlo»). */
@Composable
internal fun RingsTextAction(
    label: String,
    onClick: () -> Unit,
    tag: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .heightIn(min = WizardSpacing.touchTarget)
            .testTag(tag)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Text(text = label, style = WizardTypography.note, color = WizardColors.textMuted)
    }
}

/**
 * «No lo sé» como interruptor de texto: sin caja y con el texto en tinta cuando está activado. Es `Role.Switch`; lo escribe
 * quien lo usa ([onCheckedChange]).
 */
@Composable
internal fun RingsTextSwitch(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    tag: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .heightIn(min = WizardSpacing.touchTarget)
            .testTag(tag)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Text(
            text = label,
            style = WizardTypography.note.copy(fontWeight = if (checked) FontWeight.SemiBold else FontWeight.Normal),
            color = if (checked) WizardColors.text else WizardColors.textMuted,
        )
    }
}

/** Filete de 1 dp entre bloques de la pantalla. */
@Composable
internal fun RingsQuestionDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(WizardColors.divider))
}
