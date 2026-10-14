package com.example.kpkn.screens.sessioneditor.design

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.screens.sessioneditor.CardioTimelineSegment
import com.example.kpkn.screens.sessioneditor.SessionCardioDose
import com.example.kpkn.ui.components.SwipeToDeleteCard
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknType

/**
 * Bloque de cardio plegado (SESSION_EDITOR_V1 §2, punto 7): 64 dp con el icono del tipo, el nombre en `bodyStrong` y la dosis en
 * `caption` tabular («30 min · zona 2»). En intervalos y HIIT, debajo, la mini línea de tiempo: una barra por tramo, con el esfuerzo en
 * `energia` y la pausa en blanco al 12 %. La flecha gira al desplegar (sin giro con movimiento reducido).
 *
 * Sin estado: quien la usa guarda [expanded] y pinta debajo el editor de hoy (`CardioEditorCard`). Sin relleno propio: el módulo lo pone
 * el trozo de la parte que la contiene. Pulsar la figura o la flecha despliega; la pulsación larga en la figura arrastra; la del nombre
 * abre las acciones rápidas; deslizar borra.
 */
@Composable
internal fun SessionCardioModule(
    name: String,
    details: CardioDetails,
    expanded: Boolean,
    onToggle: () -> Unit,
    onLongPress: () -> Unit,
    onDragStart: (Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    chips: String? = null,
    dragEnabled: Boolean = true,
) {
    val haptics = LocalHapticFeedback.current
    val reduced = wizardReducedMotion()
    val turn by animateFloatAsState(targetValue = if (expanded) 180f else 0f, label = "sessionCardioTurn")
    // Con movimiento reducido la flecha cambia de golpe, sin giro.
    val angle = if (reduced) (if (expanded) 180f else 0f) else turn
    val dose = remember(details) { SessionCardioDose.of(details) }
    val timeline = remember(details) { SessionCardioDose.timelineOf(details) }
    var gripOrigin by remember { mutableStateOf(Offset.Zero) }
    val latestOnDragStart = rememberUpdatedState(onDragStart)
    val latestOnDrag = rememberUpdatedState(onDrag)
    val latestOnDragEnd = rememberUpdatedState(onDragEnd)
    val latestOnDragCancel = rememberUpdatedState(onDragCancel)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 64.dp)
            .padding(start = 20.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Figura del tipo: asa de arrastre (pulsación larga) y, tocada, despliega.
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
            Icon(
                imageVector = details.type.kpknIcon(),
                contentDescription = null,
                tint = WizardColors.text,
                modifier = Modifier.size(22.dp),
            )
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
                    // Fondo del módulo: la caja del deslizamiento no se ve dentro de la parte.
                    .background(KpknSurface.module, RoundedCornerShape(12.dp))
                    .combinedClickable(onClick = onToggle, onLongClick = onLongPress)
                    .padding(vertical = 10.dp)
                    .semantics { stateDescription = if (expanded) "Desplegado" else "Plegado" },
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val nameLine = if (chips.isNullOrBlank()) name else "$name · $chips"
                Text(
                    text = nameLine,
                    style = KpknType.bodyStrong,
                    color = WizardColors.text,
                    // Hasta dos líneas, cortadas entre palabras (KPKN_UI_V2 §5).
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (dose != null) {
                    Text(
                        text = dose,
                        style = KpknType.caption.copy(fontFeatureSettings = "'tnum' 1"),
                        color = WizardColors.textMuted,
                        // La dosis nunca se corta («2:30» entero) al 130 % de letra.
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (timeline.isNotEmpty()) CardioTimelineBar(segments = timeline)
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

/** Icono KPKN de cada tipo de cardio (SE5): carrera, caminar, bici, remo, escaladora y trineo. */
internal fun CardioType.kpknIcon(): ImageVector = when (this) {
    CardioType.TREADMILL, CardioType.RUN_OUTDOOR, CardioType.CURVED_TREADMILL -> KpknLineIcons.Run
    CardioType.WALK, CardioType.ELLIPTICAL -> KpknLineIcons.Walk
    CardioType.BIKE_STATIONARY, CardioType.BIKE_OUTDOOR, CardioType.AIR_BIKE -> KpknLineIcons.Bike
    CardioType.ROW_MACHINE, CardioType.SKI_ERG -> KpknLineIcons.Row
    CardioType.STAIR_CLIMBER -> KpknLineIcons.Stairs
    CardioType.SLED -> KpknLineIcons.Training
}

/**
 * Mini línea de tiempo del bloque: una barra de 6 dp por tramo, con el ancho proporcional a su duración. El esfuerzo va en `energia` y la
 * pausa en blanco al 12 %. Tiene su resumen para lectores de pantalla.
 */
@Composable
private fun CardioTimelineBar(segments: List<CardioTimelineSegment>) {
    val efforts = segments.count { it.work }
    val pauses = segments.size - efforts
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .semantics { contentDescription = "Línea de tiempo: $efforts esfuerzos y $pauses tramos de pausa" },
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        segments.forEach { segment ->
            Box(
                modifier = Modifier
                    .weight(segment.seconds.toFloat())
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (segment.work) SymbolPalette.energia else Color.White.copy(alpha = 0.12f))
                    .testTag(CardioTimelineTag),
            )
        }
    }
}

/** Etiqueta de prueba de cada barra de la mini línea (una por tramo). */
internal const val CardioTimelineTag = "cardio_timeline_bar"
