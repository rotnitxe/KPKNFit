package com.example.kpkn.screens.workout.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.SetOutcomeV2
import com.example.kpkn.data.workout.RestGameRecordStore
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.workout.PendingRestSuggestion
import com.example.kpkn.screens.workout.RestClockMath
import com.example.kpkn.screens.workout.RestClockShape
import com.example.kpkn.screens.workout.RestGameEngine
import com.example.kpkn.screens.workout.RestNextSetSummary
import com.example.kpkn.screens.workout.RestTimerKind
import com.example.kpkn.screens.workout.WorkoutRestModalState
import com.example.kpkn.screens.workout.restLastSetFigureText
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknIconButton
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknSetFormat
import com.example.kpkn.ui.design.KpknShape
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknType

/**
 * Working-set rest as a live pager card (minimized overlay view).
 * Same visual height as series cards: the pager frame locks to the working-set wrap.
 *
 * LV6, F3: la tarjeta recibe las mismas mejoras que la pantalla completa. La forma de RINGS pequeña con su corredor (la MISMA forma que
 * la pantalla completa), el tiempo «2:41 de 4:09», «Jugar», «Siguiente: Serie 2 · 95 kg × 4–6 · RIR 2» (de la serie real que toca),
 * −15 s / Saltar / +15 s y expandir. Sin barra lineal. NUNCA cambia de tamaño: lo que no cabe (la última serie, el descanso sugerido, saltar
 * las series que quedan) va en la franja de en medio, que se desplaza con el borde fundido. Todas las acciones son las de la pantalla
 * completa (`RestLiveActionParity`).
 */
@Composable
internal fun RestLiveCard(
    remainingSeconds: Int,
    totalSeconds: Int,
    sessionAccentColor: Color,
    restState: WorkoutRestModalState?,
    pendingRestSuggestion: PendingRestSuggestion?,
    lastSetOutcome: SetOutcomeV2?,
    lastCompletedSet: CompletedSet?,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    onSkip: () -> Unit,
    onUseAdaptive: (() -> Unit)?,
    onExpand: (() -> Unit)?,
    isAdaptiveActive: Boolean = false,
    skipExerciseLabel: String? = null,
    onSkipExercise: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    shape: RestClockShape = RestClockShape.TOWER,
    nextSet: RestNextSetSummary? = null,
    recordStore: RestGameRecordStore? = null,
    lastCompletedCustomUnit: String? = null,
    clockActive: Boolean = true,
    initiallyPlaying: Boolean = false,
    gameEngine: RestGameEngine? = null,
    videoSlot: (@Composable () -> Unit)? = null,
) {
    // A delayed/restored tick may briefly carry a stale negative value.  The
    // card is a countdown surface, so clamp both the progress and the text;
    // the controller owns any separate "excedido" cue.
    val safeRemaining = remainingSeconds.coerceAtLeast(0)
    val safeTotal = totalSeconds.coerceAtLeast(safeRemaining).coerceAtLeast(1)
    val progress = RestClockMath.progress(safeTotal, safeRemaining)
    val clock = (if (remainingSeconds < 0) "+" else "") + KpknSetFormat.rest(kotlin.math.abs(remainingSeconds))
    var playing by rememberSaveable(restState?.startedAtMs) { mutableStateOf(initiallyPlaying) }
    val kindLabel = if (remainingSeconds < 0) "TIEMPO EXTRA" else when (restState?.kind) {
        RestTimerKind.SUPERSET_INTRA -> "SUPERSERIE"
        RestTimerKind.SUPERSET_ROUND -> "SIGUIENTE RONDA"
        RestTimerKind.WARMUP -> "APROXIMACIÓN"
        RestTimerKind.BETWEEN_SIDES -> "ENTRE LADOS"
        RestTimerKind.STANDARD, null -> "DESCANSO"
    }
    KpknModule(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight(),
        contentPadding = PaddingValues(0.dp),
    ) {
        if (playing) {
            // El juego vive dentro de la tarjeta, a su altura fija. El descanso manda: al llegar a 0 la página deja de componerse y el
            // juego se cierra con ella; «Saltar» es el mismo onSkip de siempre.
            RestGameCardContent(
                remainingSeconds = remainingSeconds,
                totalSeconds = safeTotal,
                nextSet = nextSet,
                onBack = { playing = false },
                onSkip = onSkip,
                recordStore = recordStore,
                engine = gameEngine,
                running = clockActive,
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = kindLabel,
                            style = KpknType.eyebrow,
                            color = WizardColors.textMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (restState?.notificationsEnabled == false) {
                            Text(
                                text = "Sin notificaciones el aviso es solo sonoro.",
                                style = KpknType.caption,
                                color = WizardColors.textMuted,
                            )
                        }
                    }
                    if (onExpand != null) {
                        // La flecha de arriba es la de abajo girada 180 grados: la familia solo tiene el chevron hacia abajo.
                        KpknIconButton(
                            icon = KpknLineIcons.ChevronDown,
                            contentDescription = "Expandir descanso",
                            onClick = onExpand,
                            modifier = Modifier.graphicsLayer { rotationZ = 180f },
                        )
                    }
                }

                RestPlayHighlight(onClick = { playing = true }, animationsOn = clockActive, compact = true)

                // El relator vive en la banda de encima de la tarjeta (WorkoutV2Body), no aquí.
                // La forma de RINGS con su corredor a la izquierda y, a la derecha, el tiempo, «de 4:09» y «Jugar» (F3).
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    RestRingsClock(
                        shape = shape,
                        progress = progress,
                        overtime = remainingSeconds < 0,
                        runnerScale = 1.4f,
                        animationsOn = clockActive,
                        // La pista un poco más clara que el módulo (#111111) y el trazo más grueso: a este tamaño se perderían (F3).
                        trackColor = CardTrackColor,
                        strokeBoost = 1.15f,
                        modifier = Modifier.size(width = 120.dp, height = 104.dp),
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.Start,
                    ) {
                        RestClockText(
                            text = clock,
                            maxFontSize = 40.sp,
                            modifier = Modifier.semantics {
                                contentDescription = if (remainingSeconds < 0) "Tiempo extra, ${kotlin.math.abs(remainingSeconds)} segundos" else RestClockMath.spokenRemaining(safeRemaining)
                            },
                        )
                        Text(
                            text = "de ${KpknSetFormat.rest(safeTotal)}" + when {
                                restState?.isManualOverride == true -> " · manual"
                                isAdaptiveActive -> " · sugerido"
                                else -> ""
                            },
                            style = KpknType.caption,
                            color = WizardColors.textMuted,
                            maxLines = 1,
                        )
                        if (restState != null && restState.plannedSeconds > 0) {
                            Text("Plan de sesión · ${KpknSetFormat.rest(restState.plannedSeconds)}",
                                style = KpknType.caption, color = WizardColors.textMuted,
                                modifier = Modifier.testTag("rest-plan-reference"))
                        }

                    }
                }

                // Franja con desplazamiento: si no cabe todo, su borde inferior se funde (máscara DstIn) en vez de cortarse en recto (LR1c1).
                val middleScroll = rememberScrollState()
                Column(
                    modifier = Modifier
                        .weight(1f, fill = true)
                        .fillMaxWidth()
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            if (middleScroll.canScrollForward) {
                                drawRect(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(Color.Black, Color.Transparent),
                                        startY = size.height - 20.dp.toPx(),
                                        endY = size.height,
                                    ),
                                    blendMode = BlendMode.DstIn,
                                )
                            }
                        }
                        .testTag("rest-card-details").verticalScroll(middleScroll),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    videoSlot?.invoke()
                    RestLiveLastSetSummary(
                        lastCompletedSet = lastCompletedSet ?: pendingRestSuggestion?.lastSet,
                        customUnit = lastCompletedCustomUnit,
                        lastSetOutcome = lastSetOutcome,
                    )

                    if (pendingRestSuggestion != null && onUseAdaptive != null && !isAdaptiveActive) {
                        RestLiveSuggestedPill(
                            value = KpknSetFormat.rest(pendingRestSuggestion.adaptiveSeconds),
                            onClick = onUseAdaptive,
                        )
                    }

                    if (onSkipExercise != null) {
                        RestLiveSecondaryPill(
                            text = skipExerciseLabel ?: "Saltar series restantes e ir al siguiente ejercicio",
                            onClick = onSkipExercise,
                        )
                    }
                }

                if (nextSet != null) RestNextBox(nextSet = nextSet)

                // Tres píldoras hermanas de 44 dp: −15 s y +15 s en `inset`, y «Saltar» en tinta. Caben en 244 dp con la letra al 130 %.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RestStepPill(
                        text = "−15 s",
                        contentDescription = "Restar 15 segundos",
                        onClick = onDecrease,
                        modifier = Modifier.weight(1f),
                    )
                    RestSkipPill(
                        text = "Saltar",
                        onClick = onSkip,
                        modifier = Modifier.weight(1.6f),
                    )
                    RestStepPill(
                        text = "+15 s",
                        contentDescription = "Sumar 15 segundos",
                        onClick = onIncrease,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** Pista de la forma sobre el módulo de la tarjeta: un tono por encima del módulo para que se vea. */
internal val CardTrackColor = Color(0xFF242424)

/** «Última serie» del descanso en página (LR1c1): una caja `inset` con el eyebrow y la cifra «kg × reps», como el módulo de LR1a. */
@Composable
private fun RestLiveLastSetSummary(
    lastCompletedSet: CompletedSet?,
    customUnit: String?,
    lastSetOutcome: SetOutcomeV2?,
) {
    val set = lastCompletedSet ?: return
    // Espacio inseparable solo antes de la unidad («100,5 kg», «6 reps», «45 s»): alrededor de «×» el espacio normal deja que la cifra
    // parta en dos líneas antes que cortarse con elipsis al 130 % (LR1c1).
    val figure = restLastSetFigureText(set, customUnit)
    val outcome = lastSetOutcome ?: set.setOutcomeV2
    val boxShape = WorkoutUiTokens.InnerCardShape

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(boxShape)
            .background(KpknSurface.inset, boxShape)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "ÚLTIMA SERIE",
                style = KpknType.eyebrow,
                color = WizardColors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = figure,
                style = KpknType.bodyStrong.copy(fontFeatureSettings = "'tnum' 1"),
                color = WizardColors.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (outcome?.isContextPr == true || outcome?.isGlobalPr == true) {
            RestLiveTinyBadge("Récord", SymbolPalette.energia)
        }
        if (set.isFailure || set.isFailedSet) {
            RestLiveTinyBadge("Fallo", WizardColors.danger)
        }
    }
}

/**
 * Insignia del descanso en página (LR1c1): píldora con el texto en su color y un filete de 1 dp en blanco al 12 %. Va sobre una caja
 * `inset`, así que no lleva relleno propio (si no, se confundiría con el fondo).
 */
@Composable
private fun RestLiveTinyBadge(label: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(KpknShape.pill)
            .border(1.dp, Color.White.copy(alpha = 0.12f), KpknShape.pill)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = KpknType.chip,
            color = color,
            maxLines = 1,
        )
    }
}

/** «Descanso sugerido» (LR1c1): pastilla `inset` con el destello del acento, el nombre y la cifra en Syne. Sin ripple. */
@Composable
private fun RestLiveSuggestedPill(
    value: String,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clip(KpknShape.pill)
            .background(KpknSurface.inset, KpknShape.pill)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // El rótulo puede ocupar dos líneas al 130 % antes que cortarse; la cifra siempre entera.
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = SymbolPalette.musculo,
            )
            Text(
                text = "Descanso sugerido",
                style = KpknType.action,
                color = WizardColors.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = value,
            style = KpknType.metricM,
            color = WizardColors.text,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/**
 * «Saltar series restantes…» (LR1c1): píldora con borde de tinta al 24 %. Puede ocupar dos líneas antes que cortarse con elipsis:
 * la columna del medio tiene scroll, así que no rompe el presupuesto vertical.
 */
@Composable
private fun RestLiveSecondaryPill(
    text: String,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clip(KpknShape.pill)
            .border(1.dp, WizardColors.text.copy(alpha = 0.24f), KpknShape.pill)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 18.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = KpknType.action,
            color = WizardColors.text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}
