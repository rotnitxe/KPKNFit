package com.example.kpkn.screens.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.sp
import com.example.kpkn.ui.design.KpknType
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.AugeSnapshot
import com.example.kpkn.data.models.LoadAdvisoryLevel
import com.example.kpkn.data.models.MuscleRecoveryStatus
import com.example.kpkn.data.models.RecoveryChannelId
import com.example.kpkn.data.models.ringScore
import com.example.kpkn.domain.auge.LoadAdvisoryEngine
import com.example.kpkn.domain.auge.RecoveryBands
import com.example.kpkn.domain.auge.getAugeMusclePillarId
import com.example.kpkn.domain.onboarding.RingsCoverage
import com.example.kpkn.screens.home.design.HomeGlyph
import com.example.kpkn.screens.home.design.HomeGlyphButton
import com.example.kpkn.screens.home.design.HomeRingMuscle
import com.example.kpkn.screens.home.design.HomeRingMuscleGrid
import com.example.kpkn.screens.home.design.HomeRingRange
import com.example.kpkn.screens.home.design.HomeRingSparkline
import com.example.kpkn.screens.home.design.HomeTypography
import com.example.kpkn.screens.home.design.RingTowerAspect
import com.example.kpkn.screens.home.design.homeAccent
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardSpacing
import com.example.kpkn.screens.onboarding.design.WizardTypography
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.components.rings.KpknTowerRings
import com.example.kpkn.ui.components.rings.RingAdjustLevel
import com.example.kpkn.ui.components.rings.RingAdjustMuscles
import com.example.kpkn.ui.components.rings.RingAdjustPanel
import com.example.kpkn.ui.components.rings.RingChannel
import com.example.kpkn.ui.components.rings.TowerRingValues
import com.example.kpkn.ui.components.rings.towerRingBandFractions
import com.example.kpkn.ui.design.KpknCompactPill

/** Máximo de músculos en la rejilla del detalle de Músculos. */
private const val MuscleGridMax = 12

/** Ancho del arte del anillo en la hoja; la torre completa guarda su proporción (`RingTowerAspect`). */
private val RingArtWidth = 240.dp

/**
 * Quién guarda el ajuste de músculos sueltos que se hace en el detalle de Músculos (LV10b): recibe pilar → valor visible, solo de los
 * músculos que la persona movió. `HomeScreen` lo da y guarda cada uno como ancla V2 por músculo, la misma ruta que el final del entreno
 * (`AugeViewModel.applyManualBatteries(perMuscleDelta = …)`). Sin él, el detalle de Músculos no ofrece el ajuste (no tendría dónde guardarlo).
 */
val LocalRingMuscleAdjustSave = compositionLocalOf<((Map<String, Int>) -> Unit)?> { null }

/**
 * Detalle de un canal de RINGS: una hoja de vidrio (`KpknSheet`) que, de arriba abajo, muestra el nombre del canal y su cierre,
 * el anillo grande con su cifra y su banda, tu rango habitual, por qué, qué hacer hoy, (solo Músculos) los músculos uno a uno, las
 * últimas sesiones, el aviso del canal y el ajuste oficial (LV10b): «Ajustar» abre la hoja de ajuste en ese canal (el anillo con líquido
 * en Energía y Columna; el cuerpo en Músculos) y «Guardar ajuste» guarda solo si algo cambió. Sin dato ([coverage]) no hay cifra ni
 * ajuste. Etiquetas `home-ring-sheet`, `home-ring-close`, `home-ring-adjust` y `home-ring-save`.
 */
@Composable
fun RingDetailSheet(
    channel: RecoveryChannelId,
    snapshot: AugeSnapshot,
    showModelUpdateNotice: Boolean,
    onDismiss: () -> Unit,
    onDismissAdvisory: (String) -> Unit,
    onModelNoticeShown: () -> Unit,
    coverage: RingsCoverage = snapshot.coverage ?: RingsCoverage.NO_DATA,
    dataLabel: String? = null,
    perMuscle: Map<String, MuscleRecoveryStatus> = emptyMap(),
    onSaveAdjustment: (RecoveryChannelId, Int) -> Unit = { _, _ -> },
) {
    val channelSnap = snapshot.dashboard.channels.firstOrNull { it.id == channel }
    val channelCoverage = coverage.channel(channel)
    // Sin dato no hay cifra: nunca el 100 % por defecto del motor.
    val score: Int? = if (channelCoverage.hasData) snapshot.ringScore(channel) else null
    val accent = channel.homeAccent()
    val ring = channel.ringChannel()
    val baseline = snapshot.personalBaseline[channel]
    val hours = snapshot.hoursToNormal[channel]
    val spark = snapshot.sparkline[channel].orEmpty()
    val advisory = snapshot.advisories.firstOrNull { it.channel == channel }
    val causes = channelSnap?.causes.orEmpty()
    // Músculos del motor con sesión registrada, de menor a mayor. Sin sesión no hay dato y no se listan.
    val muscles = remember(perMuscle) {
        perMuscle.values
            .filter { it.hoursSinceLastSession >= 0 || it.hasEvidence || it.recoveryScore < 100 }
            .sortedBy { it.recoveryScore }
            .take(MuscleGridMax)
            .map { HomeRingMuscle(name = it.muscleName, score = it.recoveryScore) }
    }
    // Ajuste: el valor que dejó la persona en la hoja de ajuste (la torre lo enseña ya) y, en Músculos, los músculos que movió.
    val saveMuscles = LocalRingMuscleAdjustSave.current
    var adjusted by remember(channel, score) { mutableStateOf<Int?>(null) }
    var showAdjust by remember(channel) { mutableStateOf(false) }
    val muscleEdits = remember(channel) { mutableStateMapOf<String, Int>() }
    val bodyBefore = remember(perMuscle) { homeBodyValues(perMuscle) }
    val isMuscular = channel == RecoveryChannelId.MUSCULAR
    val shown: Int? = adjusted ?: score
    val changedMuscles = muscleEdits.filter { (id, value) -> bodyBefore[id] != value }
    val changed = if (isMuscular) changedMuscles.isNotEmpty() else score != null && adjusted != null && adjusted != score
    // El ajuste solo se ofrece con dato y con dónde guardarlo (los músculos sueltos necesitan quien los guarde).
    val canAdjust = score != null && (!isMuscular || saveMuscles != null)
    LaunchedEffect(showModelUpdateNotice) {
        if (showModelUpdateNotice) onModelNoticeShown()
    }
    // La torre enseña solo el canal del detalle; los demás anillos no se pintan.
    val towerValues = TowerRingValues(
        columna = shown.takeIf { ring == RingChannel.COLUMNA },
        musculos = shown.takeIf { ring == RingChannel.MUSCULOS },
        energia = shown.takeIf { ring == RingChannel.ENERGIA },
    )

    var inlineDraft by remember(channel, score) { mutableStateOf((score ?: 0).toString()) }
    var historyCount by remember(channel) { mutableStateOf(7) }
    var selectedPoint by remember(channel, spark) { mutableStateOf(spark.lastIndex.coerceAtLeast(0)) }
    var selectedCause by remember(channel) { mutableStateOf(0) }
    val historyValues = spark.takeLast(historyCount)
    KpknSheet(onDismissRequest = onDismiss, modifier = Modifier.testTag("home-ring-sheet"), stableHeightFraction = .9f) {
        if (showAdjust && canAdjust) {
        val level = RingAdjustLevel(value = adjusted ?: score ?: 0, before = score ?: 0, edited = adjusted != null)
        RingAdjustPanel(
            initialChannel = ring,
            channels = listOf(ring),
            title = "Ajusta ${RecoveryBands.channelTitle(channel)}",
            onDismiss = { showAdjust = false },
            energy = if (ring == RingChannel.ENERGIA) level else RingAdjustLevel(0, 0),
            spine = if (ring == RingChannel.COLUMNA) level else RingAdjustLevel(0, 0),
            muscles = RingAdjustMuscles(
                values = bodyBefore + muscleEdits,
                before = bodyBefore,
                edited = muscleEdits.keys,
            ),
            onEnergyChange = { adjusted = it; inlineDraft = it.toString() },
            onSpineChange = { adjusted = it; inlineDraft = it.toString() },
            onMuscleChange = { id, value -> muscleEdits[id] = value },
            onEnergyReset = { adjusted = null; inlineDraft = (score ?: 0).toString() },
            onSpineReset = { adjusted = null; inlineDraft = (score ?: 0).toString() },
            onMusclesReset = { muscleEdits.clear() },
        )
        } else {
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(RecoveryBands.channelTitle(channel), style = KpknType.title, color = WizardColors.text, modifier = Modifier.weight(1f))
                HomeGlyphButton(HomeGlyph.CLOSE, "Cerrar", onDismiss, modifier = Modifier.testTag("home-ring-close"))
            }
            Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    val (bandTop, bandBottom) = towerRingBandFractions(ring)
                    val towerHeight = RingArtWidth / RingTowerAspect
                    Box(Modifier.width(RingArtWidth).height(towerHeight * (bandBottom - bandTop)).clipToBounds()) {
                        KpknTowerRings(values = towerValues, visible = setOf(ring), highlighted = ring,
                            modifier = Modifier.towerBand(RingArtWidth, towerHeight, towerHeight * bandTop))
                    }
                    Row(verticalAlignment = Alignment.Bottom) {
                        if (canAdjust && !isMuscular) {
                            BasicTextField(
                                value = inlineDraft,
                                onValueChange = { raw -> if (raw.length <= 3 && raw.all { it.isDigit() }) { inlineDraft = raw; adjusted = raw.toIntOrNull()?.takeIf { it in 0..100 } } },
                                textStyle = KpknType.metricL.copy(color = WizardColors.text),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true, modifier = Modifier.width(80.dp).testTag("home-ring-inline-value"),
                            )
                        } else Text(shown?.toString() ?: "—", style = KpknType.metricL, color = WizardColors.text)
                        if (shown != null) Text(" %", style = KpknType.title, color = WizardColors.text)
                    }
                    Text(ringCaption(shown, channelCoverage.label, channelCoverage.isEstimated), style = KpknType.caption, color = WizardColors.textMuted)
                }
                if (spark.size >= 2) {
                    Column(Modifier.fillMaxWidth().background(Color(0xFF111111), androidx.compose.foundation.shape.RoundedCornerShape(22.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Últimos registros", style = KpknType.bodyStrong, color = WizardColors.text)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(7, 30).forEach { count -> KpknCompactPill("$count", onClick = { historyCount = count; selectedPoint = spark.lastIndex }, filled = historyCount == count, height = 30.dp, modifier = Modifier.testTag("home-ring-range-$count")) }
                            }
                        }
                        val localPoint = (selectedPoint - (spark.size - historyValues.size)).coerceIn(0, historyValues.lastIndex)
                        Text("Registro ${selectedPoint + 1} · ${historyValues[localPoint]} %", style = KpknType.action, color = WizardColors.text)
                        InteractiveRingHistory(historyValues, localPoint, { selectedPoint = spark.size - historyValues.size + it }, if (isMuscular) Color(0xFFF49A6E) else Color(0xFF8FB2FF))
                    }
                }
                baseline?.let { HomeRingRange(range = it, score = shown, accent = WizardColors.text, key = channel.name) }
                if (channelCoverage.hasData) {
                    if (causes.isNotEmpty()) {
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            causes.forEachIndexed { index, cause ->
                                KpknCompactPill(cause.substringBefore(':').take(24), onClick = { selectedCause = index }, filled = selectedCause == index, height = 34.dp)
                            }
                        }
                        Text(causes[selectedCause.coerceIn(causes.indices)], style = KpknType.caption, color = WizardColors.textMuted)
                    }
                    channelSnap?.action?.takeIf { it.isNotBlank() }?.let { Text(it, style = KpknType.body, color = WizardColors.text) }
                } else {
                    Text("Sin datos registrados todavía para este canal.", style = KpknType.caption, color = WizardColors.textMuted)
                }
                if (isMuscular && muscles.isNotEmpty()) HomeRingMuscleGrid(muscles)
                advisory?.let {
                    Text(it.title, style = KpknType.bodyStrong, color = WizardColors.text)
                    Text(it.body, style = KpknType.caption, color = WizardColors.textMuted)
                    if (it.dismissible) DetailTextAction("Descartar aviso", { onDismissAdvisory(it.id) })
                }
                if (canAdjust) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        KpknCompactPill("Ajustar", onClick = { showAdjust = true }, filled = false, modifier = Modifier.testTag("home-ring-adjust"))
                        KpknCompactPill("Guardar ajuste", onClick = {
                            if (isMuscular) {
                                if (changedMuscles.isNotEmpty()) { saveMuscles?.invoke(changedMuscles.mapKeys { (id, _) -> getAugeMusclePillarId(id) }); onDismiss() }
                            } else adjusted?.takeIf { it != score }?.let { onSaveAdjustment(channel, it); onDismiss() }
                        }, filled = true, enabled = changed, modifier = Modifier.weight(1f).testTag("home-ring-save"))
                    }
                }
                if (showModelUpdateNotice) Text("Modelo actualizado", style = KpknType.caption, color = WizardColors.textMuted)
                Text("${snapshot.dashboard.confidenceLabel}${(dataLabel ?: snapshot.dashboard.dataLabel)?.let { " · $it" }.orEmpty()}", style = KpknType.caption, color = WizardColors.textFaint)
            }
        }
    }
}

@Composable
private fun InteractiveRingHistory(values: List<Int>, selected: Int, onSelect: (Int) -> Unit, color: Color) {
    val selectedIndex by androidx.compose.runtime.rememberUpdatedState(selected)
    val select by androidx.compose.runtime.rememberUpdatedState(onSelect)
    Column {
        Canvas(Modifier.fillMaxWidth().height(112.dp).testTag("home-ring-history").pointerInput(values) {
            detectTapGestures { tap -> select(((tap.x / size.width) * values.lastIndex).toInt().coerceIn(values.indices)) }
        }) {
            val points = values.mapIndexed { index, value -> Offset(index.toFloat() / values.lastIndex.coerceAtLeast(1) * size.width, size.height * (1f - value.coerceIn(0, 100) / 100f)) }
            val path = Path().apply { points.forEachIndexed { index, point -> if (index == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y) } }
            drawPath(path, color, style = Stroke(2.dp.toPx()))
            points.forEachIndexed { index, point -> drawCircle(if (index == selectedIndex) WizardColors.text else color, if (index == selectedIndex) 5.dp.toPx() else 3.dp.toPx(), point) }
        }
        // Same data points remain independently operable with TalkBack.
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            values.forEachIndexed { index, value -> KpknCompactPill("${index + 1} · $value %", onClick = { onSelect(index) }, filled = index == selected, height = 30.dp, modifier = Modifier.testTag("home-ring-point-$index")) }
        }
    }
}

/**
 * Los músculos con sesión registrada de [perMuscle], por nombre y con su recuperación: lo que el cuerpo del ajuste pinta y de lo que
 * parte. Un músculo sin sesión no tiene dato (su 100 % es el valor por defecto del motor) y sale sin datos.
 */
internal fun homeBodyValues(perMuscle: Map<String, MuscleRecoveryStatus>): Map<String, Int> =
    perMuscle.values
        .filter { it.hoursSinceLastSession >= 0 || it.hasEvidence || it.recoveryScore < 100 }
        .associate { it.muscleName to it.recoveryScore.coerceIn(0, 100) }

/** Lo que se ha ajustado, en una línea: «Tu ajuste: 60 %» o «2 músculos ajustados». */
internal fun adjustmentSummary(isMuscular: Boolean, adjusted: Int?, changedMuscles: Int): String = when {
    isMuscular && changedMuscles == 1 -> "1 músculo ajustado"
    isMuscular -> "$changedMuscles músculos ajustados"
    adjusted != null -> "Tu ajuste: $adjusted\u00A0%"
    else -> ""
}

/** Etiqueta de una sección del detalle: `eyebrow` en `textMuted`. */
@Composable
private fun DetailEyebrow(text: String) {
    Text(text = text, style = HomeTypography.eyebrow, color = WizardColors.textMuted)
}

/** Acción de texto del detalle, de al menos 48 dp de alto. */
@Composable
private fun DetailTextAction(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .heightIn(min = WizardSpacing.touchTarget)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(text = text, style = WizardTypography.controlLabel, color = WizardColors.text)
    }
}

/**
 * Coloca la torre de [towerWidth] × [towerHeight] con su borde superior [top] por encima de la caja: la caja recortada enseña la franja
 * que empieza ahí. Va con medidas fijas y en (0, -top), sin pasar por la alineación del padre, así el recorte no se centra (I12).
 */
private fun Modifier.towerBand(towerWidth: Dp, towerHeight: Dp, top: Dp): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(Constraints.fixed(towerWidth.roundToPx(), towerHeight.roundToPx()))
    layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(0, -top.roundToPx()) }
}

/** La banda bajo la cifra; sin dato, «Sin calibrar»; si el valor es una estimación, también su procedencia. */
private fun ringCaption(shown: Int?, sourceLabel: String, estimated: Boolean): String = when {
    shown == null -> "Sin calibrar"
    estimated -> "${RecoveryBands.labelForScore(shown)} · $sourceLabel"
    else -> RecoveryBands.labelForScore(shown)
}

private fun RecoveryChannelId.ringChannel(): RingChannel = when (this) {
    RecoveryChannelId.MUSCULAR -> RingChannel.MUSCULOS
    RecoveryChannelId.SYSTEM -> RingChannel.ENERGIA
    RecoveryChannelId.STRUCTURE -> RingChannel.COLUMNA
}
