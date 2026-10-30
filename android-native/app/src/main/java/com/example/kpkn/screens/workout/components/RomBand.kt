package com.example.kpkn.screens.workout.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.domain.workout.LiveRom
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.ui.components.icons.KpknLineIcons
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.graphicsLayer
import com.example.kpkn.ui.design.depth.KpknShadow
import com.example.kpkn.ui.design.depth.kpknRing
import com.example.kpkn.ui.design.depth.kpknInset
import com.example.kpkn.ui.design.depth.kpknShadow
import com.example.kpkn.ui.design.motion.KpknFamilies
import com.example.kpkn.ui.design.motion.KpknMotion
import com.example.kpkn.ui.design.motion.animateKpknColorAsState
import com.example.kpkn.ui.design.motion.animateKpknFloatAsState
import com.example.kpkn.ui.design.motion.kpknAnimate
import com.example.kpkn.ui.design.motion.kpknTween
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.ui.design.motion.KpknDuration

private val RomInk = Color(0xFFF2EEE6)
private val RomFill = Color(0xFFE6E1D5)
private val RomMuted = Color(0xFFB9B5AE)
private val RomCardFill = Color(0xFF111111)
private val RomPad = 10.dp

/** Alto de la banda de ROM de la tarjeta (decisión aprobada): 36 dp, el hueco de la fila «Parciales, ayuda y ROM». */
internal val RomBandHeight = 36.dp

/**
 * Pulido v1 · 15 · `.lv-rom` del lienzo: la banda de 36 dp que sustituye a «Parciales, ayuda y ROM ›» cuando el ejercicio registra ROM.
 * Izquierda, «ROM» y el valor; centro, el deslizador de 10 a 100 % (de 5 en 5, con marcas imantadas en 50, 80 y 100 y el nombre de la
 * zona sobre el pulgar mientras se arrastra); derecha, «Detalle ›» (con un punto si hay parciales o ayuda anotados), que abre la hoja.
 * La tarjeta no cambia de tamaño: la banda cabe en el hueco de esa fila.
 */
@Composable
internal fun RomBand(
    value: Int,
    onValueChange: (Int) -> Unit,
    hasDetail: Boolean,
    onOpenDetail: () -> Unit,
    modifier: Modifier = Modifier,
    hapticsEnabled: Boolean = true,
) {
    Row(
        modifier.fillMaxWidth().height(RomBandHeight).testTag("live-rom-band"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.widthIn(min = 34.dp), verticalArrangement = Arrangement.Center) {
            Text("ROM", style = TextStyle(fontFamily = WizardFonts.body, fontSize = 9.sp, lineHeight = 9.sp, fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp), color = RomMuted, maxLines = 1, softWrap = false)
            Text(
                "${value.coerceIn(LiveRom.MIN, LiveRom.MAX)} %",
                Modifier.padding(top = 2.dp),
                style = TextStyle(fontFamily = WizardFonts.body, fontSize = 13.sp, lineHeight = 15.6.sp, fontWeight = FontWeight.SemiBold,
                    fontFeatureSettings = "'tnum' 1, 'lnum' 1"),
                color = RomInk, maxLines = 1, softWrap = false,
            )
        }
        RomSlider(value, onValueChange, Modifier.weight(1f), hapticsEnabled = hapticsEnabled)
        Box(
            Modifier.height(RomBandHeight)
                .clickable(role = Role.Button, onClickLabel = "Abrir detalle del registro", onClick = onOpenDetail)
                .semantics { contentDescription = "Abrir detalle del registro" }
                .testTag("live-card-detail-open"),
        ) {
            Row(Modifier.align(Alignment.CenterStart), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Detalle", style = TextStyle(fontFamily = WizardFonts.body, fontSize = 11.sp, fontWeight = FontWeight.Medium), color = RomMuted,
                    maxLines = 1, softWrap = false)
                Icon(KpknLineIcons.ChevronRight, null, Modifier.size(12.dp), tint = RomMuted)
            }
            // El punto (`.more i`: top 6, right −2, 6 dp) avisa de que hay parciales o ayuda anotados. pulido:lv-S17
            if (hasDetail) Box(Modifier.align(Alignment.TopEnd).offset(x = 2.dp, y = 6.dp).size(6.dp)
                .background(RomInk, CircleShape).kpknRing(KpknShadow.CorteHoja, CircleShape).testTag("live-rom-detail-dot"))
        }
    }
}

/**
 * El deslizador del ROM (`.lv-rsl`): pista de 4 dp con el tramo hecho en crema, tres marcas (50, 80, 100) que imantan, pulgar de 20 dp y,
 * mientras se arrastra (y 550 ms después), la burbuja «80 % · Casi completo» sobre el pulgar. Sirve para la banda de la tarjeta y para la
 * hoja «Detalle del registro». Lleva el mismo nombre accesible que el de siempre («ROM, recorrido de la serie»).
 */
@Composable
internal fun RomSlider(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = RomBandHeight,
    hapticsEnabled: Boolean = true,
) {
    val v = value.coerceIn(LiveRom.MIN, LiveRom.MAX)
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val onChange by rememberUpdatedState(onValueChange)
    val current by rememberUpdatedState(v)
    var tipOn by remember { mutableStateOf(false) }
    var touching by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    // `lv-pop` del pulgar (200 ms, `estandar`) en cada valor nuevo que elige la persona; no al abrir. pulido:lv-A11
    var popKey by remember { mutableIntStateOf(0) }
    // La burbuja entra y sale con `opacity` y `transform` en .16 s `suave` (translateY 4 → 0). pulido:lv-T08 lv-X11 lv-X12
    val tipProgress by animateKpknFloatAsState(if (tipOn) 1f else 0f, KpknMotion.D160, KpknMotion.Suave)
    // La burbuja se queda 550 ms tras soltar. pulido:lv-J14 lv-T08 lv-X11 lv-X12
    LaunchedEffect(touching) {
        if (touching) tipOn = true else { delay(550); tipOn = false }
    }
    fun commit(next: Int) {
        if (next != current) {
            popKey++
            onChange(next)
            if (hapticsEnabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    // pulido:lv-S18 (anillo de foco del deslizador)
    Box(
        modifier.height(height).fillMaxWidth().testTag("live-rom-slider")
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .semantics {
                contentDescription = "ROM, recorrido de la serie"
                stateDescription = "$v %, ${LiveRom.zone(v).label}"
                progressBarRangeInfo = ProgressBarRangeInfo(v.toFloat(), LiveRom.MIN.toFloat()..LiveRom.MAX.toFloat(),
                    (LiveRom.MAX - LiveRom.MIN) / LiveRom.STEP - 1)
                setProgress { target -> commit(LiveRom.snap(target)); true }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val pad = RomPad.toPx()
                    fun valueAt(x: Float): Int {
                        val span = (size.width - 2f * pad).coerceAtLeast(1f)
                        return LiveRom.snap(LiveRom.MIN + (x - pad) / span * (LiveRom.MAX - LiveRom.MIN))
                    }
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    touching = true
                    commit(valueAt(down.position.x))
                    drag(down.id) { change -> change.consume(); commit(valueAt(change.position.x)) }
                    touching = false
                }
            },
    ) {
        val padPx = with(density) { RomPad.toPx() }
        BoxWithConstraints(Modifier.fillMaxWidth().height(height)) {
            val widthPx = with(density) { maxWidth.toPx() }
            val span = (widthPx - 2f * padPx).coerceAtLeast(1f)
            fun xOf(mark: Int) = padPx + LiveRom.fraction(mark) * span
            val thumbX = xOf(v)
            // Pista (top 16, 4 dp, blanco al 7 %) y tramo hecho en crema hasta el pulgar.
            Box(Modifier.align(Alignment.TopStart).offset(y = 16.dp).fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp))
                .background(Color(0x12FFFFFF))) {
                Box(Modifier.fillMaxHeight().width(with(density) { thumbX.toDp() }).background(RomFill))
            }
            // Marcas 50 / 80 / 100: las que quedan bajo el tramo hecho se oscurecen para leerse sobre el crema.
            LiveRom.MARKS.forEach { mark ->
                val x = xOf(mark)
                Box(Modifier.offset { IntOffset((x - 1.dp.toPx()).roundToInt(), 12.dp.roundToPx()) }.size(width = 2.dp, height = 12.dp)
                    .background(if (x < thumbX - with(density) { 3.dp.toPx() }) Color(0x99111111) else Color(0x30FFFFFF), RoundedCornerShape(1.dp)))
            }
            // Pulgar: 20 dp crema con un aro de 4 dp del color de la tarjeta. pulido:lv-S19 lv-A11
            Box(
                Modifier.offset { IntOffset((thumbX - 10.dp.toPx()).roundToInt(), 8.dp.roundToPx()) }.size(20.dp)
                    .kpknAnimate(KpknFamilies.Rebote.demo, enabled = popKey > 0, restartKey = popKey)
                    .kpknShadow(KpknShadow.Caida28Capas, CircleShape)
                    .background(RomInk, CircleShape),
            )
            // Anillo de foco (`.lv-rsl:focus-visible`: `0 0 0 2px #f2eee655`, radio 10). pulido:lv-S18
            if (focused) Box(Modifier.matchParentSize().kpknRing(KpknShadow.AnilloFoco, RoundedCornerShape(10.dp)))
            if (tipProgress > 0.001f) {
                // Burbuja de la zona sobre el pulgar (`.lv-rtip`: bottom 34 dp de la pista de 36). pulido:lv-S20
                Text(
                    LiveRom.tipText(v),
                    Modifier.offset { IntOffset(thumbX.roundToInt(), 0) }
                        .graphicsLayer { alpha = tipProgress; translationY = (1f - tipProgress) * 4.dp.toPx() }
                        .layout { measurable, _ ->
                            val placeable = measurable.measure(Constraints())
                            layout(0, 0) { placeable.place(-placeable.width / 2, 2.dp.roundToPx() - placeable.height) }
                        }
                        .kpknShadow(KpknShadow.Caida820Capas, RoundedCornerShape(10.dp))
                        .background(Color(0xFF242426), RoundedCornerShape(10.dp))
                        .kpknInset(KpknShadow.Caida820Capas, RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .testTag("live-rom-tip"),
                    style = TextStyle(fontFamily = WizardFonts.body, fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                    color = RomInk, maxLines = 1, softWrap = false,
                )
            }
        }
    }
}

/**
 * `.lv-sw` del lienzo: el interruptor de 52 × 30 (pulgar de 24) de «Registrar ROM», igual en el editor y en la hoja «Detalle del registro».
 * Apagado: fondo #3A3A3D y pulgar #BDB9B1 a la izquierda; encendido: fondo #8A867C y pulgar crema a la derecha.
 * pulido:lv-T09 lv-T10 lv-X13
 */
@Composable
internal fun RomSwitch(checked: Boolean, modifier: Modifier = Modifier) {
    // Fondo y pulgar cambian de color en .2 s `suave`; el pulgar se desplaza 22 dp en .22 s `estandar`. pulido:lv-T09 lv-T10 lv-X13
    val track by animateKpknColorAsState(if (checked) Color(0xFF8A867C) else Color(0xFF3A3A3D), KpknMotion.D200, KpknMotion.Suave)
    val thumb by animateKpknColorAsState(if (checked) RomInk else Color(0xFFBDB9B1), KpknMotion.D200, KpknMotion.Suave)
    val shift by animateDpAsState(if (checked) 22.dp else 0.dp, if (wizardReducedMotion()) snap() else kpknTween(KpknDuration(220), KpknMotion.Estandar), label = "rom-switch")
    Box(modifier.size(width = 52.dp, height = 30.dp).clip(RoundedCornerShape(15.dp)).background(track).padding(3.dp)) {
        Box(Modifier.offset(x = shift).size(24.dp).background(thumb, CircleShape))
    }
}
