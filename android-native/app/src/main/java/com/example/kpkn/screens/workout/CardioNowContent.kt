package com.example.kpkn.screens.workout

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.models.CardioBlockType
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.screens.workout.components.LiveSheetTokens
import com.example.kpkn.services.cardio.CardioGpsState
import com.example.kpkn.services.cardio.CardioGpsStatus
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.depth.KpknShadow
import com.example.kpkn.ui.design.depth.kpknShadow
import com.example.kpkn.ui.design.depth.KpknGradient
import com.example.kpkn.ui.design.depth.KpknGradientArgs
import com.example.kpkn.ui.design.depth.kpknGradient
import com.example.kpkn.ui.design.motion.KpknDuration
import com.example.kpkn.ui.design.motion.KpknFamilies
import com.example.kpkn.ui.design.motion.KpknMotion
import com.example.kpkn.ui.design.motion.KpknWaveEdgeFade
import com.example.kpkn.ui.design.motion.KpknWaveField
import com.example.kpkn.ui.design.motion.KpknWaveState
import com.example.kpkn.ui.design.motion.animateKpknFloatAsState
import com.example.kpkn.ui.design.motion.kpknAnimate
import com.example.kpkn.ui.design.motion.rememberKpknClockMs
import kotlin.math.roundToInt

private val CvInk = Color(0xFFF2EEE6)
private val CvAmber = Color(0xFFF7CF73)
private val CvBody = TextStyle(fontFamily = WizardFonts.body)
private val CvNumber = TextStyle(fontFamily = WizardFonts.display, fontWeight = FontWeight.ExtraBold, fontFeatureSettings = "'tnum' 1", platformStyle = PlatformTextStyle(includeFontPadding = false))

/** Pulido v1: `.cv-pg` / `.cv-hero` / `.cv-mod`, sin la figura ni el recuadro de iteración 03. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CardioNowContent(
    step: CardioStep?, nextStep: CardioStep?, steps: List<CardioStep>, stepIndex: Int,
    stepFraction: Float, stepLeft: Int, totalLeft: Int, ringZone: Int,
    hr: Int?, maxHr: Int?, shownZone: Int?, plannedRpe: Int, rpeAnchor: String,
    inZoneSeconds: Int, hrDrop: Int?, gpsMode: Boolean, gpsState: CardioGpsState?,
    targetDistanceKm: Double?, distanceKm: Double?, paused: Boolean,
    hrIsManual: Boolean, hrFromWatch: Boolean, manualHrAgeSeconds: Int,
    onAddManualHr: () -> Unit, elapsed: Int, kcal: Double?, estimatedKm: Double?,
    onRetryGps: (() -> Unit)? = null,
    introSeconds: Float = 0f,
) {
    val compact = LocalConfiguration.current.screenHeightDp < 800
    val reduced = wizardReducedMotion()
    // Sombras de texto del lienzo (`.cv-time` 0 2px 26px negro; `.cv-tsub` 0 1px 12px negro). Compose solo admite
    // una por estilo: se usa la de mayor radio, que es la que define el halo sobre las ondas.
    val density = LocalDensity.current
    val clockShadow = Shadow(Color.Black, Offset(0f, with(density) { 2.dp.toPx() }), with(density) { 26.dp.toPx() })
    val subShadow = Shadow(Color.Black, Offset(0f, with(density) { 1.dp.toPx() }), with(density) { 12.dp.toPx() })
    val zone = step?.zone ?: 2
    val color = cardioZoneColor(ringZone)
    val wave = when {
        paused -> KpknWaveState.Pause
        shownZone != null && shownZone > zone -> KpknWaveState.Above
        shownZone != null && shownZone < zone -> KpknWaveState.Below
        steps.size == 1 -> if (zone >= 4) KpknWaveState.Work else KpknWaveState.Cont
        step?.block?.type == CardioBlockType.WARMUP -> KpknWaveState.Warm
        step?.block?.type == CardioBlockType.RECOVER -> KpknWaveState.Recover
        step?.block?.type == CardioBlockType.COOLDOWN -> KpknWaveState.Cool
        else -> KpknWaveState.Work
    }
    val beatPeriodMs = if (hr != null && hr > 0) ((60f / hr.coerceIn(45, 220)) * 1000f).roundToInt() else 2400
    val beatClock = rememberKpknClockMs(
        running = !paused && !reduced,
        key = beatPeriodMs,
        loopMs = beatPeriodMs.toFloat(),
    )
    var previousStep by remember { mutableIntStateOf(stepIndex) }
    var flipGeneration by remember { mutableIntStateOf(0) }
    LaunchedEffect(stepIndex) {
        if (stepIndex != previousStep) {
            previousStep = stepIndex
            flipGeneration++
        }
    }

    val byDistance = gpsMode && targetDistanceKm != null && targetDistanceKm > 0
    val leftKm = if (byDistance) (targetDistanceKm!! - (distanceKm ?: 0.0)).coerceAtLeast(0.0) else null
    val label = leftKm?.let { cardioDecimal(it, 2) } ?: cardioClock(stepLeft)
    val lost = gpsMode && gpsState?.status == CardioGpsStatus.SIGNAL_LOST
    val gpsDenied = gpsMode && (gpsState?.status == CardioGpsStatus.PERMISSION_DENIED || gpsState?.status == CardioGpsStatus.LOCATION_DISABLED)
    val gpsNoted = lost || gpsDenied

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 4.dp, bottom = if (compact) 148.dp else 166.dp)) {
        if (lost) {
            CvGpsNoteBanner(
                icon = KpknLineIcons.Warning,
                title = "Sin señal GPS · seguimos con el tiempo",
                actionLabel = null,
                onAction = null,
            )
        } else if (gpsDenied && onRetryGps != null) {
            CvGpsNoteBanner(
                icon = KpknLineIcons.CardioRoute,
                title = "Ubicación no disponible",
                actionLabel = "Reintentar permiso",
                onAction = onRetryGps,
                actionTag = "cardio-gps-retry",
            )
        }

        BoxWithConstraints(
            Modifier.fillMaxWidth().height(
                (if (steps.size == 1) { if (compact && gpsNoted) 128 else if (compact) 176 else 232 } else if (compact) 150 else 176).dp,
            ).testTag("cardio-clock"),
        ) {
            CvHalo(color, maxHeight, hr = hr, paused = paused)
            // pulido:cv-J01: las 18 crestas usan exactamente cvWaveD y el estado CVW del lienzo.
            KpknWaveField(
                wave,
                Modifier.fillMaxSize(),
                zoneColor = color,
                lines = if (compact) 15 else 18,
                heartRate = if (paused) 0 else hr ?: 0,
                introSeconds = introSeconds,
                edgeFade = KpknWaveEdgeFade.Hero,
            )
            Column(Modifier.fillMaxSize().padding(top = if (compact) 4.dp else 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(when {
                    leftKm != null -> "km restantes · de ${cardioDecimal(targetDistanceKm!!, 2)} km"
                    steps.size > 1 -> "restante del paso · quedan ${cardioClock(totalLeft)}"
                    else -> "tiempo restante"
                }, style = CvBody.copy(fontSize = 12.5.sp, fontWeight = FontWeight.Medium, shadow = subShadow), color = LiveSheetTokens.Muted)
                val font = when {
                    label.length >= 6 -> if (compact) 46 else 54
                    label.length >= 5 -> if (compact) 56 else 68
                    else -> if (compact) 64 else 80
                }
                Text(
                    label,
                    Modifier.testTag("cardio-clock-label").offset(y = (-10).dp)
                        .kpknAnimate(KpknFamilies.EntradaSube.demo, enabled = flipGeneration > 0, restartKey = flipGeneration),
                    style = CvNumber.copy(fontSize = font.sp, lineHeight = font.sp, letterSpacing = (-2.5).sp, shadow = clockShadow),
                    color = CvInk, maxLines = 1, softWrap = false,
                )
                Spacer(Modifier.weight(1f))
                FlowRow(
                    Modifier.padding(start = 16.dp, end = 16.dp, bottom = if (compact) 8.dp else 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    step?.cue?.takeIf(String::isNotBlank)?.let { rawCue ->
                        val cueText = if (gpsMode && !rawCue.startsWith("Objetivo")) "Objetivo $rawCue" else rawCue
                        CvCue(cueText, icon = KpknLineIcons.Speedometer, iconTint = color)
                    }
                    step?.block?.inclinePercent?.let { incline ->
                        CvCue("${cardioDecimal(incline, if (incline % 1.0 == 0.0) 0 else 1)} %", icon = KpknLineIcons.Incline, iconTint = color)
                    }
                    if (gpsMode) {
                        val gpsActive = gpsState?.status == CardioGpsStatus.RECORDING
                        val gpsLabel = when (gpsState?.status) {
                            CardioGpsStatus.RECORDING -> "GPS activo"
                            CardioGpsStatus.SIGNAL_LOST -> "Sin señal GPS"
                            CardioGpsStatus.PAUSED -> "GPS en pausa"
                            else -> "GPS"
                        }
                        CvCue(
                            text = gpsLabel,
                            icon = KpknLineIcons.CardioRoute,
                            iconTint = if (gpsActive) color else CvAmber,
                            warn = !gpsActive,
                        )
                    }
                }
            }
            BoxWithConstraints(Modifier.align(Alignment.BottomCenter).padding(horizontal = 16.dp).offset(y = 1.dp).fillMaxWidth().height(9.dp)) {
                val targetFraction = (leftKm?.let { 1.0 - it / targetDistanceKm!! }?.toFloat() ?: stepFraction).coerceIn(0f, 1f)
                val fraction by animateKpknFloatAsState(targetFraction, KpknDuration(900), KpknMotion.Lineal, label = "cv-bar")
                Box(Modifier.align(Alignment.CenterStart).fillMaxWidth().height(3.dp).clip(CircleShape).background(Color(0x14FFFFFF))) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).background(CvInk).testTag("cardio-clock-progress"))
                }
                Box(Modifier.align(Alignment.CenterStart).offset(x = (maxWidth * fraction - 4.5.dp).coerceIn(0.dp, maxWidth - 9.dp))
                    .size(9.dp).kpknShadow(KpknShadow.ResplandorZona12, CircleShape, color).clip(CircleShape).background(color))
            }
        }
        Spacer(Modifier.height(6.dp))
        CvModule(compact, Modifier.padding(horizontal = 16.dp), warn = shownZone?.takeIf { it != zone }?.let(::cardioZoneColor)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f).then(if (hrIsManual) Modifier.testTag("cardio-hr-manual").clickable(role = Role.Button, onClick = onAddManualHr) else Modifier)) {
                    CvEyebrow(if (hrFromWatch) "Pulso · reloj" else if (hrIsManual) "Pulso · manual" else "Pulso")
                    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        Icon(
                            if (hr == null) KpknLineIcons.HeartOutline else KpknLineIcons.Heart,
                            null,
                            Modifier.size(20.dp).padding(bottom = 4.dp).graphicsLayer {
                                if (hr != null && !paused && !reduced) {
                                    val phase = ((beatClock.value % beatPeriodMs) / beatPeriodMs).coerceIn(0f, 1f)
                                    val s = if (phase < 0.40f) {
                                        val u = KpknMotion.Salida.transform(phase / 0.40f)
                                        1.22f + (1f - 1.22f) * u
                                    } else 1f
                                    scaleX = s
                                    scaleY = s
                                }
                            },
                            tint = if (hr != null) CvInk else LiveSheetTokens.Faint,
                        )
                        Text(hr?.toString() ?: "—", Modifier.testTag("cardio-hr"), style = CvNumber.copy(fontSize = 36.sp, lineHeight = 36.sp, letterSpacing = (-1).sp), color = CvInk)
                        Text(if (hr == null) "sin pulso" else "lpm", Modifier.padding(bottom = 5.dp), style = CvBody.copy(fontSize = 12.sp), color = LiveSheetTokens.Faint)
                        if (hrIsManual) {
                            Icon(KpknLineIcons.Edit, null, Modifier.size(13.dp).padding(bottom = 5.dp), tint = LiveSheetTokens.Muted)
                        }
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    CvEyebrow(if (hr == null) "Guía por esfuerzo" else "Objetivo · Z$zone")
                    Text(if (hr == null) "RPE ${(plannedRpe - 1).coerceAtLeast(1)}–$plannedRpe · $rpeAnchor" else CardioZoneNames[zone - 1],
                        Modifier.padding(top = 6.dp), style = CvBody.copy(fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold), color = CvInk)
                    Text(if (hr == null) "Lo planificado es una guía" else maxHr?.let { "${it * (40 + zone * 10) / 100}–${it * (50 + zone * 10) / 100} lpm" }
                        ?: "${40 + zone * 10}–${50 + zone * 10} % FC máx", Modifier.padding(top = 2.dp), style = CvBody.copy(fontSize = 11.sp), color = LiveSheetTokens.Faint)
                }
            }
            Spacer(Modifier.height(12.dp))
            if (hr != null && maxHr != null) CardioTape((1..5).map { "Z$it" }, CardioZoneColors, setOf(zone - 1), cardioNeedlePercent(hr, maxHr))
            else CardioTape((1..10).map(Int::toString), List(10) { CvInk }, setOf((plannedRpe - 2).coerceAtLeast(0), plannedRpe - 1), null)
            // Con pulso pero sin FC máxima no hay zona calculada: no se pinta un check ni «En tu zona» falsos.
            val zoneUnavailable = hr != null && shownZone == null
            val zoneUnavailableNoMax = zoneUnavailable && maxHr == null
            val zoneUnavailableManual = zoneUnavailable && maxHr != null && hrIsManual
            Row(Modifier.fillMaxWidth().padding(top = if (hr == null) 9.dp else 11.dp).heightIn(min = 20.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    when {
                        hr == null || zoneUnavailable -> KpknLineIcons.Info
                        shownZone == zone -> KpknLineIcons.Check
                        shownZone != null && shownZone > zone -> KpknLineIcons.ChevronDown
                        else -> KpknLineIcons.ChevronUp
                    },
                    null, Modifier.size(16.dp),
                    tint = if (zoneUnavailable) LiveSheetTokens.Muted else CvInk,
                )
                Text(when {
                    hr == null -> "Sin datos de pulso"
                    zoneUnavailableNoMax -> "Zona sin calcular · falta tu FC máxima"
                    zoneUnavailableManual -> "Pulso manual · sin zona en vivo"
                    shownZone != null && shownZone > zone -> "Baja el ritmo"
                    shownZone != null && shownZone < zone -> "Sube el ritmo"
                    step?.block?.type == CardioBlockType.RECOVER -> "Recuperando bien"
                    else -> "En tu zona"
                }, Modifier.weight(1f).then(if (zoneUnavailable) Modifier.testTag("cardio-zone-unavailable") else Modifier),
                    style = CvBody.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                    color = when {
                        zoneUnavailable -> LiveSheetTokens.Muted
                        shownZone != null && shownZone != zone -> cardioZoneColor(shownZone)
                        else -> CvInk
                    })
                if (hr == null) Text("+ Añadir pulso", Modifier.heightIn(min = 48.dp).clip(CircleShape).border(1.dp, Color(0x3DF2EEE6), CircleShape)
                    .clickable(role = Role.Button, onClick = onAddManualHr).padding(horizontal = 15.dp, vertical = 13.dp).testTag("cardio-hr-add"),
                    style = CvBody.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold), color = CvInk)
                else Text(when {
                    zoneUnavailableNoMax -> "anota tu edad o FC máxima para medir la zona"
                    zoneUnavailableManual -> "usa el reloj para zona y tiempo en zona"
                    shownZone != null && shownZone > zone -> "$hr lpm \u00b7 sobre Z$zone"
                    shownZone != null && shownZone < zone -> "$hr lpm \u00b7 bajo Z$zone"
                    hrIsManual -> "anotado hace ${cardioClock(manualHrAgeSeconds)}"
                    step?.block?.type == CardioBlockType.RECOVER && hrDrop != null -> "−$hrDrop lpm en 1 min"
                    else -> "${cardioClock(inZoneSeconds)} en Z$zone"
                }, style = CvBody.copy(fontSize = 11.sp), color = LiveSheetTokens.Faint)
            }
        }
        if (!(compact && steps.size > 1)) {
            val cells = buildList {
                when {
                    gpsMode -> {
                        val pace = gpsState?.paceSecondsPerKm
                        add(Triple("Ritmo", pace?.let(::cardioClock) ?: "—", "/km"))
                        add(Triple("Activo", cardioClock(elapsed), ""))
                    }
                    steps.size > 1 -> {
                        add(Triple("Distancia", estimatedKm?.let { "≈ ${cardioDecimal(it)}" } ?: "—", "km"))
                        add(Triple("Energía", kcal?.let { "≈ ${it.toInt()}" } ?: "—", "kcal"))
                    }
                    else -> {
                        add(Triple("Activo", cardioClock(elapsed), ""))
                        add(Triple("Energía", kcal?.let { "≈ ${it.toInt()}" } ?: "—", "kcal"))
                    }
                }
            }
            CvModule(compact, Modifier.padding(horizontal = 16.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    cells.forEachIndexed { i, cell ->
                        Column(Modifier.weight(1f).then(if (i > 0) Modifier.borderStart() else Modifier).padding(horizontal = 4.dp)) {
                            CvEyebrow(cell.first)
                            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(cell.second, style = CvNumber.copy(fontSize = 24.sp, lineHeight = 28.sp), color = CvInk)
                                if (cell.third.isNotEmpty()) Text(cell.third, style = CvBody.copy(fontSize = 11.sp), color = LiveSheetTokens.Faint)
                            }
                        }
                    }
                }
            }
        }
        if (steps.size > 1) CvModule(compact, Modifier.padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                CvEyebrow("Plan del cardio")
                Text("${cardioClock(elapsed)} / ${cardioClock(steps.sumOf { it.seconds })}", style = CvBody.copy(fontSize = 13.sp), color = CvInk)
            }
            CardioProfile(steps, stepIndex, stepFraction, Modifier.padding(top = 10.dp), height = 40.dp, animateBars = false)
            if (nextStep != null) {
                Row(Modifier.padding(top = 12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CvEyebrow("Siguiente")
                    Box(Modifier.size(7.dp).clip(CircleShape).background(cardioZoneColor(nextStep.zone)))
                    Text(nextStep.name + (nextStep.round?.let { " $it" } ?: ""), Modifier.weight(1f), style = CvBody.copy(fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold), color = CvInk)
                    Text(cardioClock(nextStep.seconds) + nextStep.cue.takeIf { it.isNotBlank() }?.let { " \u00b7 $it" }.orEmpty(), style = CvBody.copy(fontSize = 12.sp), color = LiveSheetTokens.Muted)
                }
            } else {
                Row(Modifier.padding(top = 12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CvEyebrow("Último paso")
                    Text("después guardas el cardio", style = CvBody.copy(fontSize = 12.5.sp), color = LiveSheetTokens.Muted)
                }
            }
        }
    }
}

@Composable
private fun CvGpsNoteBanner(
    icon: ImageVector,
    title: String,
    actionLabel: String?,
    onAction: (() -> Unit)?,
    actionTag: String? = null,
) {
    Row(
        Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp).fillMaxWidth().heightIn(min = 44.dp)
            .clip(RoundedCornerShape(18.dp)).background(Color(0x10FF9B92))
            .border(1.dp, Color(0x30FF9B92), RoundedCornerShape(18.dp))
            .padding(start = 14.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, null, Modifier.size(18.dp), tint = CvInk)
        Text(title, Modifier.weight(1f), style = CvBody.copy(fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold), color = CvInk)
        if (actionLabel != null && onAction != null) {
            Box(
                Modifier.height(36.dp).clip(CircleShape).border(1.dp, Color(0x3DF2EEE6), CircleShape)
                    .clickable(role = Role.Button, onClick = onAction)
                    .then(if (actionTag != null) Modifier.testTag(actionTag) else Modifier)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(actionLabel, style = CvBody.copy(fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold), color = CvInk, maxLines = 1)
            }
        }
    }
}

@Composable
private fun CvEyebrow(text: String) = Text(text.uppercase(), style = CvBody.copy(fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.4.sp), color = LiveSheetTokens.Faint)

@Composable
private fun CvCue(text: String, icon: ImageVector? = null, iconTint: Color = CvInk, warn: Boolean = false) {
    val borderColor = if (warn) Color(0x55F7CF73) else Color(0x1AFFFFFF)
    val textColor = if (warn) CvAmber else CvInk
    Row(
        Modifier.height(34.dp).clip(CircleShape).background(Color(0xCC0A0A0A))
            .border(1.dp, borderColor, CircleShape).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        if (icon != null) Icon(icon, null, Modifier.size(15.dp), tint = iconTint)
        Text(text, style = CvBody.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold), color = textColor, maxLines = 1)
    }
}

@Composable
private fun CvModule(compact: Boolean, modifier: Modifier, warn: Color? = null, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(if (compact) 20.dp else 22.dp)
    Column(modifier.fillMaxWidth().padding(bottom = if (compact) 6.dp else 8.dp)
        .then(if (warn != null) Modifier.kpknShadow(KpknShadow.ResplandorZona28, shape, warn) else Modifier)
        .clip(shape).background(Color(0xFF111111))
        .border(1.dp, warn?.copy(alpha = .55f) ?: Color(0x10FFFFFF), shape)
        .padding(horizontal = if (compact) 14.dp else 16.dp, vertical = if (compact) 11.dp else 14.dp), content = content)
}

private fun Modifier.borderStart() = drawBehind { drawLine(Color(0x10FFFFFF), Offset.Zero, Offset(0f, size.height), 1.dp.toPx()) }

/** El halo elíptico del héroe con latido `cv-beat` sincronizado al pulso real (o 2,4 s / [periodMsOverride]). */
@Composable
internal fun BoxScope.CvHalo(
    color: Color,
    heroHeight: androidx.compose.ui.unit.Dp,
    hr: Int? = null,
    paused: Boolean = false,
    periodMsOverride: Int? = null,
    topFraction: Float = 0.44f,
) {
    val reduced = wizardReducedMotion()
    val periodMs = periodMsOverride ?: if (hr != null && hr > 0) ((60f / hr.coerceIn(45, 220)) * 1000f).roundToInt() else 2400
    val clock = rememberKpknClockMs(
        running = !paused && !reduced,
        key = periodMs,
        loopMs = periodMs.toFloat(),
    )
    Box(
        Modifier.align(Alignment.TopCenter)
            .offset(y = heroHeight * topFraction - 95.dp)
            .size(width = 300.dp, height = 190.dp)
            .graphicsLayer {
                if (paused || reduced) {
                    alpha = 0.20f
                    scaleX = 1f
                    scaleY = 1f
                } else {
                    val phase = ((clock.value % periodMs) / periodMs).coerceIn(0f, 1f)
                    if (phase < 0.45f) {
                        val u = KpknMotion.Salida.transform(phase / 0.45f)
                        alpha = 0.34f + (0.18f - 0.34f) * u
                        val s = 1.06f + (1f - 1.06f) * u
                        scaleX = s
                        scaleY = s
                    } else {
                        val u = KpknMotion.Salida.transform((phase - 0.45f) / 0.55f)
                        alpha = 0.18f + (0.20f - 0.18f) * u
                        scaleX = 1f
                        scaleY = 1f
                    }
                }
            }
            .blur(20.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
            .kpknGradient(KpknGradient.ResplandorZona, args = KpknGradientArgs(zone = color)),
    )
}
