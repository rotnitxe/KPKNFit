package com.example.kpkn.screens.workout.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.workout.RestClockShape
import com.example.kpkn.screens.workout.RestHeadline
import com.example.kpkn.screens.workout.RestNextSetSummary
import com.example.kpkn.screens.workout.RestOverrun
import com.example.kpkn.screens.workout.RestOverrunPolicy
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.components.kpknHazeEffect
import com.example.kpkn.ui.design.KpknIconButton
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknSetFormat
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknType
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.delay

/*
 * «Descanso cumplido» (LV6, F6): lo que se ve cuando el descanso llegó a 0 y lo que sigue es una serie normal. El descanso ya se cerró
 * (la sesión avanzó, la alerta sonó y el servicio paró: eso no cambia); esta capa solo cambia lo que se ve, en la superficie que
 * estuviera (pantalla completa o tarjeta): el tiempo extra cuenta hacia arriba desde el instante en que acabó el descanso.
 */

/** El ámbar de «tiempo extra» (el de Energía). */
private val OvertimeAmber = androidx.compose.ui.graphics.Color(0xFFF7CF73)

/** El tiempo extra, en segundos, que se actualiza solo desde [RestOverrun.endedAtMs] (4 veces por segundo; basta con ver el cambio). */
@Composable
private fun rememberExtraSeconds(overrun: RestOverrun, fixedSeconds: Int?): Int {
    var now by remember(overrun) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(overrun, fixedSeconds) {
        if (fixedSeconds != null) return@LaunchedEffect
        while (true) {
            delay(250L)
            now = System.currentTimeMillis()
        }
    }
    return fixedSeconds ?: RestOverrunPolicy.extraSeconds(overrun, now)
}

/** «Descanso cumplido. Serie 2 de 4: 95 kg × 4–6.» (F6). Sin siguiente serie, solo «Descanso cumplido.». */
private fun overrunSentence(nextSet: RestNextSetSummary?): String =
    if (nextSet == null) "Descanso cumplido." else "Descanso cumplido. ${nextSet.sentence()}"

/** «Empezar la serie 2» (o «Empezar la serie» si no se sabe cuál toca). */
private fun startLabel(nextSet: RestNextSetSummary?): String =
    if (nextSet == null) "Empezar la serie" else "Empezar la serie ${nextSet.setNumber}"

/** «Siguiente / Serie 2 · 95 kg × 4–6 / RIR 2» (F6): la siguiente serie real. Con otro ejercicio, su nombre en el rótulo. */
@Composable
internal fun RestNextBox(
    nextSet: RestNextSetSummary,
    modifier: Modifier = Modifier,
    background: androidx.compose.ui.graphics.Color = KpknSurface.inset,
    dense: Boolean = false,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(background, RoundedCornerShape(18.dp))
            .semantics(mergeDescendants = true) { contentDescription = nextSet.spoken() }
            .padding(horizontal = 14.dp, vertical = if (dense) 6.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = nextSet.exerciseName?.let { "Siguiente · $it" } ?: "Siguiente",
                style = KpknType.caption,
                color = WizardColors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = nextSet.headline(),
                style = KpknType.bodyStrong,
                color = WizardColors.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        nextSet.intensityText?.let {
            Text(
                text = it,
                style = KpknType.caption,
                color = WizardColors.textMuted,
                maxLines = 1,
            )
        }
    }
}

/**
 * «Descanso cumplido» a pantalla completa (F6). La misma capa del descanso (velo al 92 % con haze), con la forma del reloj llena y
 * respirando, «TIEMPO EXTRA +0:24 sobre 4:09», la siguiente serie y «Empezar la serie N». [onStart] cierra la capa; [onExtend] es
 * «Necesito +30 s»; [onMinimize] la pasa a la tarjeta. [extraSecondsOverride] fija el tiempo extra (hojas de render y pruebas).
 */
@Composable
internal fun RestOverrunOverlay(
    overrun: RestOverrun,
    hazeState: HazeState,
    shape: RestClockShape,
    headline: RestHeadline?,
    nextSet: RestNextSetSummary?,
    onStart: () -> Unit,
    onExtend: () -> Unit,
    onMinimize: (() -> Unit)?,
    modifier: Modifier = Modifier,
    clockActive: Boolean = true,
    extraSecondsOverride: Int? = null,
    lastSetText: String? = null,
    onExtendSeconds: ((Int) -> Unit)? = null,
) {
    val extra = rememberExtraSeconds(overrun, extraSecondsOverride)
    // El descanso cumplido del Refinamiento 02 es la misma pantalla completa del descanso en tiempo extra: «Tu descanso se ha
    // cumplido.», el eclipse lleno con «+0:12 · Tiempo extra», «Necesito +30 s» y «Continuar entrenando».
    RestRef02FullscreenScaffold(
        eyebrow = "PAUSA ENTRE SERIES",
        title = headline?.title.orEmpty(),
        onMinimize = onMinimize,
        modifier = modifier.zIndex(6f).testTag("rest-overrun"),
        bottom = {
            RestRef02Button("Necesito +30 s", onExtend, primary = false, modifier = Modifier.fillMaxWidth(), height = 39.dp,
                radius = 23.dp, tag = "rest-overrun-extend")
            RestRef02FullPrimary("Continuar entrenando", onStart, "rest-overrun-start")
        },
    ) { compact ->
        RestRef02FullClockPanel(
            compact = compact,
            remainingSeconds = -extra,
            progress = 1f,
            isPaused = false,
            animationsOn = clockActive,
            onDecrease = {},
            onTogglePause = {},
            onIncrease = { onExtendSeconds?.invoke(15) ?: onExtend() },
            suggestion = null,
            onOpenSuggestion = null,
            lastSetText = lastSetText,
            nextTitle = nextSet?.let { restRef02NextTitle(it, headline?.title.orEmpty(), card = false) },
            nextDose = nextSet?.doseText(),
            onPlay = null,
            overtime = true,
            pauseEnabled = false,
            decreaseEnabled = false,
        )
    }
}

/** «TIEMPO EXTRA / +0:24 / sobre 4:09» (F6). */
@Composable
private fun OvertimeReadout(
    extraSeconds: Int,
    totalSeconds: Int,
    timeSize: androidx.compose.ui.unit.TextUnit,
    modifier: Modifier = Modifier,
    alignStart: Boolean = false,
) {
    val spoken = "Tiempo extra: ${extraSeconds / 60} minutos y ${extraSeconds % 60} segundos"
    Column(
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = spoken },
        horizontalAlignment = if (alignStart) Alignment.Start else Alignment.CenterHorizontally,
    ) {
        Text(
            text = "TIEMPO EXTRA",
            style = KpknType.eyebrow.copy(letterSpacing = if (alignStart) 1.2.sp else 2.sp),
            color = OvertimeAmber,
            maxLines = 2,
        )
        RestClockText(
            text = RestOverrunPolicy.extraLabel(extraSeconds),
            maxFontSize = timeSize,
            modifier = Modifier.testTag("rest-overtime"),
        )
        Text(
            text = "sobre ${KpknSetFormat.rest(totalSeconds)}",
            style = KpknType.caption,
            color = WizardColors.textMuted,
        )
    }
}

/**
 * «Descanso cumplido» dentro de la tarjeta del descanso, a su altura fija: la misma capa que la pantalla completa, adaptada. En medio,
 * la forma llena con el tiempo extra y la siguiente serie (con desplazamiento si no cabe); abajo, «Empezar la serie N» y «Necesito +30 s».
 */
@Composable
internal fun RestOverrunCardContent(
    overrun: RestOverrun,
    shape: RestClockShape,
    nextSet: RestNextSetSummary?,
    onStart: () -> Unit,
    onExtend: () -> Unit,
    onExpand: (() -> Unit)?,
    modifier: Modifier = Modifier,
    clockActive: Boolean = true,
    extraSecondsOverride: Int? = null,
) {
    val extra = rememberExtraSeconds(overrun, extraSecondsOverride)
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "DESCANSO CUMPLIDO",
                style = KpknType.eyebrow,
                color = WizardColors.textMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
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
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                RestRingsClock(
                    shape = shape,
                    progress = 1f,
                    overtime = true,
                    animationsOn = clockActive,
                    runnerScale = 1.4f,
                    trackColor = CardTrackColor,
                    strokeBoost = 1.15f,
                    modifier = Modifier.size(width = 120.dp, height = 104.dp),
                )
                OvertimeReadout(
                    extraSeconds = extra,
                    totalSeconds = overrun.totalSeconds,
                    timeSize = 40.sp,
                    modifier = Modifier.weight(1f),
                    alignStart = true,
                )
            }
            if (nextSet != null) RestNextBox(nextSet = nextSet)
        }
        RestSkipPill(
            text = startLabel(nextSet),
            onClick = onStart,
            modifier = Modifier.fillMaxWidth(),
            height = 48.dp,
            icon = KpknLineIcons.PlayFilled,
        )
        KpknTextAction(
            text = "Necesito +${RestOverrunPolicy.EXTEND_SECONDS} s",
            onClick = onExtend,
            modifier = Modifier.align(Alignment.CenterHorizontally),
            color = WizardColors.textMuted,
        )
    }
}

/** «Descanso cumplido» como la página del descanso del paginador (el mismo módulo y la misma altura que la tarjeta del descanso). */
@Composable
internal fun RestOverrunCard(
    overrun: RestOverrun,
    shape: RestClockShape,
    nextSet: RestNextSetSummary?,
    onStart: () -> Unit,
    onExtend: () -> Unit,
    onExpand: (() -> Unit)?,
    modifier: Modifier = Modifier,
    clockActive: Boolean = true,
    extraSecondsOverride: Int? = null,
    onExtendSeconds: ((Int) -> Unit)? = null,
    exerciseName: String = "",
) {
    val extra = rememberExtraSeconds(overrun, extraSecondsOverride)
    val compact = com.example.kpkn.screens.workout.liveSessionCardHeight() < 440.dp
    // La tarjeta del descanso en tiempo extra: el mismo marco, el eclipse lleno con «+0:12» y el pie para seguir o pedir 30 s más.
    RestRef02Card(
        compact = compact,
        modifier = modifier,
        tag = "rest-overrun-card",
        footer = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RestRef02Button("Necesito +30 s", onExtend, primary = false, modifier = Modifier.weight(1f), tag = "rest-overrun-extend")
                RestRef02Button("Continuar", onStart, primary = true, modifier = Modifier.weight(1f), tag = "rest-overrun-start")
            }
        },
    ) {
        RestRef02CardClockPanel(
            compact = compact,
            kicker = "DESCANSO · TIEMPO EXTRA",
            remainingSeconds = -extra,
            progress = 1f,
            isPaused = false,
            animationsOn = clockActive,
            onExpand = onExpand,
            onDecrease = {},
            onTogglePause = {},
            onIncrease = { onExtendSeconds?.invoke(15) ?: onExtend() },
            nextTitle = nextSet?.let { restRef02NextTitle(it, exerciseName, card = true) },
            nextDose = nextSet?.doseText(),
            suggestion = null,
            onOpenSuggestion = null,
            pauseEnabled = false,
            decreaseEnabled = false,
        )
    }
}
