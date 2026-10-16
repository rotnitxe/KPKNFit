package com.example.kpkn.screens.home

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
import com.example.kpkn.ui.components.rings.RingAdjustSheet
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
            .filter { it.hoursSinceLastSession >= 0 }
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

    KpknSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("home-ring-sheet"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = WizardSpacing.gutter, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = RecoveryBands.channelTitle(channel),
                style = WizardTypography.stepTitle,
                color = accent,
                modifier = Modifier.weight(1f),
            )
            HomeGlyphButton(
                glyph = HomeGlyph.CLOSE,
                description = "Cerrar",
                onClick = onDismiss,
                modifier = Modifier.testTag("home-ring-close"),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = WizardSpacing.gutter)
                .padding(top = 12.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            // El anillo grande, su cifra y su banda. La caja del arte es solo la franja de este anillo (I12): la cifra va justo debajo.
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                val (bandTop, bandBottom) = towerRingBandFractions(ring)
                val towerHeight = RingArtWidth / RingTowerAspect
                Box(
                    modifier = Modifier
                        .width(RingArtWidth)
                        .height(towerHeight * (bandBottom - bandTop))
                        .clipToBounds(),
                ) {
                    // La torre conserva su tamaño y su sitio en el logo; la caja solo deja ver la franja del anillo.
                    KpknTowerRings(
                        values = towerValues,
                        visible = setOf(ring),
                        highlighted = ring,
                        modifier = Modifier.towerBand(towerWidth = RingArtWidth, towerHeight = towerHeight, top = towerHeight * bandTop),
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = shown?.toString() ?: "—",
                        style = WizardTypography.controlValue,
                        color = WizardColors.text,
                    )
                    if (shown != null) {
                        Text(
                            text = " %",
                            style = WizardTypography.header,
                            color = WizardColors.text,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                }
                Text(
                    text = ringCaption(shown, channelCoverage.label, channelCoverage.isEstimated),
                    style = WizardTypography.caption,
                    color = WizardColors.textMuted,
                )
            }

            // Tu rango habitual, o la nota de que aún no hay.
            if (baseline != null) {
                HomeRingRange(range = baseline, score = score, accent = accent, key = channel.name)
            } else {
                Text(
                    text = "Tu normal aparecerá tras unas 4 sesiones con RINGS al inicio.",
                    style = WizardTypography.bodySmall,
                    color = WizardColors.textMuted,
                )
            }

            if (score != null && causes.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DetailEyebrow("POR QUÉ")
                    causes.forEach { cause ->
                        Text(text = cause, style = WizardTypography.bodySmall, color = WizardColors.textMuted)
                    }
                }
            }

            if (score != null) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DetailEyebrow("QUÉ HACER HOY")
                    Text(
                        text = channelSnap?.action?.takeIf { it.isNotBlank() } ?: RecoveryBands.action(channel, score),
                        style = WizardTypography.body,
                        color = WizardColors.text,
                    )
                    hours?.let { h ->
                        Text(
                            text = if (h == 0) "Ya estás en Normal o por encima." else "Vuelve a Normal en ~$h h",
                            style = WizardTypography.bodySmall,
                            color = WizardColors.textMuted,
                        )
                    }
                }
            }

            if (channel == RecoveryChannelId.MUSCULAR && muscles.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    DetailEyebrow("POR MÚSCULO")
                    HomeRingMuscleGrid(muscles = muscles)
                }
            }

            if (spark.size >= 2) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DetailEyebrow("ÚLTIMAS SESIONES")
                    HomeRingSparkline(values = spark, accent = accent, key = channel.name)
                }
            }

            advisory?.let { notice ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(text = notice.title, style = WizardTypography.controlLabel, color = WizardColors.text)
                    Text(text = notice.body, style = WizardTypography.bodySmall, color = WizardColors.textMuted)
                    if (notice.dismissible &&
                        LoadAdvisoryEngine.rank(notice.level) >= LoadAdvisoryEngine.rank(LoadAdvisoryLevel.WATCH)
                    ) {
                        DetailTextAction(text = "Descartar aviso", onClick = { onDismissAdvisory(notice.id) })
                    }
                }
            }

            if (canAdjust) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    DetailEyebrow("¿TE SIENTES DISTINTO?")
                    Text(
                        text = "Si no coincide con cómo te sientes, ajústalo. Tu ajuste manda sobre la estimación de hoy.",
                        style = WizardTypography.note,
                        color = WizardColors.textMuted,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        KpknCompactPill(
                            text = "Ajustar",
                            onClick = { showAdjust = true },
                            filled = false,
                            height = 40.dp,
                            modifier = Modifier.testTag("home-ring-adjust"),
                        )
                        if (changed) {
                            Text(
                                text = adjustmentSummary(isMuscular, adjusted, changedMuscles.size),
                                style = WizardTypography.bodySmall,
                                color = WizardColors.text,
                                modifier = Modifier.testTag("home-ring-adjust-summary"),
                            )
                        }
                    }
                    KpknCompactPill(
                        text = "Guardar ajuste",
                        onClick = {
                            if (isMuscular) {
                                if (changedMuscles.isNotEmpty()) {
                                    // Cada músculo se guarda por su pilar, como el motor lo conoce.
                                    saveMuscles?.invoke(changedMuscles.mapKeys { (id, _) -> getAugeMusclePillarId(id) })
                                    onDismiss()
                                }
                            } else {
                                val value = adjusted
                                if (value != null && value != score) {
                                    onSaveAdjustment(channel, value)
                                    onDismiss()
                                }
                            }
                        },
                        filled = true,
                        height = 44.dp,
                        fullWidth = true,
                        enabled = changed,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("home-ring-save"),
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (showModelUpdateNotice) {
                    Text(
                        text = "Modelo actualizado: Columna ahora mide carga axial (no presses de pecho) y Energía/Columna recuperan con la misma semántica que el aprendizaje.",
                        style = WizardTypography.bodySmall,
                        color = WizardColors.textMuted,
                    )
                }
                val data = (dataLabel ?: snapshot.dashboard.dataLabel)?.let { " · Datos: $it" }.orEmpty()
                Text(
                    text = "Confianza: ${snapshot.dashboard.confidenceLabel}$data",
                    style = WizardTypography.note,
                    color = WizardColors.textFaint,
                )
            }
        }
    }

    if (showAdjust && canAdjust) {
        val level = RingAdjustLevel(value = adjusted ?: score ?: 0, before = score ?: 0, edited = adjusted != null)
        RingAdjustSheet(
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
            onEnergyChange = { adjusted = it },
            onSpineChange = { adjusted = it },
            onMuscleChange = { id, value -> muscleEdits[id] = value },
            onEnergyReset = { adjusted = null },
            onSpineReset = { adjusted = null },
            onMusclesReset = { muscleEdits.clear() },
        )
    }
}

/**
 * Los músculos con sesión registrada de [perMuscle], por nombre y con su recuperación: lo que el cuerpo del ajuste pinta y de lo que
 * parte. Un músculo sin sesión no tiene dato (su 100 % es el valor por defecto del motor) y sale sin datos.
 */
internal fun homeBodyValues(perMuscle: Map<String, MuscleRecoveryStatus>): Map<String, Int> =
    perMuscle.values
        .filter { it.hoursSinceLastSession >= 0 }
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
