package com.example.kpkn.screens.sessioneditor.design

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.plan.ExercisePattern
import com.example.kpkn.screens.onboarding.design.entreno.plan.PatternGlyph
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.ui.components.SwipeToDeleteCard
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknType

/**
 * Fila plegada de un ejercicio (SESSION_EDITOR_V1 §2, punto 6): 64 dp con la miniatura a la izquierda, el nombre en una línea, la dosis
 * debajo en cifras tabulares y la flecha. Tocar el nombre, la miniatura o la flecha despliega y pliega. La pulsación larga en la miniatura
 * arrastra el ejercicio; la del nombre abre las acciones rápidas. Con [superset] lleva a su izquierda la barra de 3 dp en [accent].
 *
 * Sin estado de negocio: quien la usa decide si está desplegada y qué hace cada gesto. [imageRes] es la imagen del catálogo o nulo; sin
 * imagen se pinta el glifo de [pattern].
 */
@Composable
internal fun SessionExerciseRow(
    name: String,
    dose: String,
    expanded: Boolean,
    accent: Color,
    imageRes: Int?,
    pattern: ExercisePattern,
    onToggle: () -> Unit,
    onLongPress: () -> Unit,
    onDragStart: (Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    chips: String? = null,
    superset: Boolean = false,
    dragEnabled: Boolean = true,
) {
    val haptics = LocalHapticFeedback.current
    val reduced = wizardReducedMotion()
    val turn by animateFloatAsState(targetValue = if (expanded) 180f else 0f, label = "sessionExerciseTurn")
    // Con movimiento reducido la flecha cambia de golpe, sin giro.
    val angle = if (reduced) (if (expanded) 180f else 0f) else turn
    var gripOrigin by remember { mutableStateOf(Offset.Zero) }
    val latestOnDragStart = rememberUpdatedState(onDragStart)
    val latestOnDrag = rememberUpdatedState(onDrag)
    val latestOnDragEnd = rememberUpdatedState(onDragEnd)
    val latestOnDragCancel = rememberUpdatedState(onDragCancel)
    val nameText = remember(name, chips) {
        buildAnnotatedString {
            withStyle(SpanStyle(color = WizardColors.text)) { append(name) }
            if (!chips.isNullOrBlank()) {
                withStyle(SpanStyle(color = WizardColors.textMuted)) { append(" · "); append(chips) }
            }
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 64.dp)
            // La barra de superserie va a la izquierda de toda la fila, y la miniatura se corre para dejarle sitio.
            .drawBehind {
                if (superset) {
                    drawRoundRect(
                        color = accent,
                        // Un píxel de más por cada lado: las filas no dejan hueco entre sus barras.
                        topLeft = Offset(20.dp.toPx(), -1.dp.toPx()),
                        size = Size(3.dp.toPx(), size.height + 2.dp.toPx()),
                        cornerRadius = CornerRadius(1.5.dp.toPx()),
                    )
                }
            }
            .padding(start = if (superset) 32.dp else 20.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Miniatura: asa de arrastre (pulsación larga) y, tocada, despliega.
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(KpknSurface.inset)
                .onGloballyPositioned { gripOrigin = it.boundsInWindow().topLeft }
                .then(
                    if (dragEnabled) {
                        Modifier.pointerInput(Unit) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { offset ->
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    latestOnDragStart.value(gripOrigin + offset)
                                },
                                onDragCancel = {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    latestOnDragCancel.value()
                                },
                                onDragEnd = {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    latestOnDragEnd.value()
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    latestOnDrag.value(Offset(dragAmount.x, dragAmount.y))
                                },
                            )
                        }
                    } else {
                        Modifier
                    },
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Button,
                    onClick = onToggle,
                )
                .semantics { contentDescription = "$name. Arrastra para reordenar" },
            contentAlignment = Alignment.Center,
        ) {
            if (imageRes != null) {
                Image(
                    painter = painterResource(imageRes),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                PatternGlyph(pattern = pattern, accent = accent, modifier = Modifier.size(26.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        SwipeToDeleteCard(
            onDelete = onDelete,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    // Fondo del módulo: la caja del deslizamiento no se ve negra dentro de la parte.
                    .background(KpknSurface.module, RoundedCornerShape(12.dp))
                    .combinedClickable(onClick = onToggle, onLongClick = onLongPress)
                    .padding(top = 10.dp, bottom = 10.dp)
                    .semantics { stateDescription = if (expanded) "Desplegado" else "Plegado" },
            ) {
                Text(
                    text = nameText,
                    style = KpknType.bodyStrong,
                    color = WizardColors.text,
                    // Hasta dos líneas, cortadas entre palabras: la fila crece antes que cortar un nombre a media palabra (KPKN_UI_V2 §5).
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = dose,
                    style = KpknType.caption.copy(fontFeatureSettings = "'tnum' 1"),
                    color = WizardColors.textMuted,
                    // Hasta dos líneas: la dosis nunca se corta («2:30» entero) al 130 % de letra.
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        // La flecha tiene su propia zona táctil de 48 dp.
        Box(
            modifier = Modifier
                .size(48.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Button,
                    onClick = onToggle,
                )
                .semantics { contentDescription = if (expanded) "Plegar $name" else "Desplegar $name" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = KpknLineIcons.ChevronDown,
                contentDescription = null,
                tint = WizardColors.textMuted,
                modifier = Modifier
                    .size(20.dp)
                    .graphicsLayer { rotationZ = angle },
            )
        }
    }
}
