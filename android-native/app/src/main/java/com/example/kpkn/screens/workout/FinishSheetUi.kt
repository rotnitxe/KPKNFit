package com.example.kpkn.screens.workout

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.models.SessionChecklistItem
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.screens.workout.components.FreeClockIcons
import com.example.kpkn.screens.workout.components.Lx
import com.example.kpkn.screens.workout.components.LxButton
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.depth.KpknShadow
import com.example.kpkn.ui.design.depth.kpknShadow
import com.example.kpkn.ui.design.motion.KpknAnimation
import com.example.kpkn.ui.design.motion.KpknChannel
import com.example.kpkn.ui.design.motion.KpknCountUp
import com.example.kpkn.ui.design.motion.KpknDuration
import com.example.kpkn.ui.design.motion.KpknFill
import com.example.kpkn.ui.design.motion.KpknMotion
import com.example.kpkn.ui.design.motion.animateKpknColorAsState
import com.example.kpkn.ui.design.motion.animateKpknFloatAsState
import com.example.kpkn.ui.design.motion.kpknAnimate
import com.example.kpkn.ui.design.motion.kpknKeyframes
import com.example.kpkn.ui.design.motion.rememberKpknPose
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/*
 * «Sesión completada» (Pulido v1 · punto 18 · escenas `lx-f-*`): una sola hoja SIN scroll que se degrada por niveles A / B / C. Las tres
 * pestañas de antes desaparecen: Progreso se funde en el resumen y Anotaciones es un panel que entra por la derecha dentro de la misma hoja.
 * Todo lo que se mide sale de `src/lx.css` (bloque «sesión completada»); los tiempos de entrada, de las filas `lx-A*` de `paridad/lx.md`.
 */

private val ModuleBg = Color(0xFF111111)
private val ModuleBorder = Color(0x0DFFFFFF)

/** Cómo se ve la hoja según el nivel, la escala de texto y lo que lleva (las reglas `.lx-tB`, `.lx-tC`, `.lx-big`, `.lx-dense` del lienzo). */
internal class FinishDims(val tier: FinishTier, val big: Boolean, val dense: Boolean, val hasError: Boolean, val roomy: Boolean) {
    val ring = if (big) 46 else if (tier == FinishTier.B) 48 else 50
    val volumeSize = if (big) 34 else when (tier) { FinishTier.A -> 40; FinishTier.B -> 36; FinishTier.C -> 34 }
    val headerTop = when (tier) { FinishTier.A -> 24; FinishTier.B -> 22; FinishTier.C -> 20 }
    val showTileSub = !(tier == FinishTier.C || dense || big)
    val showSpark = !big
    val showLifts = !(tier == FinishTier.C || big)
    val showThumb = tier != FinishTier.C
    val thumbWidth = if (tier == FinishTier.B) 34 else 38
    val shareWidth = if (tier == FinishTier.C) 80 else 84
    val saveHeight = if (tier == FinishTier.C) 50 else 52
    val barsStyle = true
    val muscleRows = Int.MAX_VALUE
    fun showMuscles(hasMuscles: Boolean) = hasMuscles && !(big && (dense || hasError || tier == FinishTier.C))
    val dataPadding = if (tier == FinishTier.C) PaddingValues(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 8.dp)
        else PaddingValues(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 8.dp)
    val musclePadding = if (big) PaddingValues(horizontal = 14.dp, vertical = 8.dp) else if (tier == FinishTier.A) PaddingValues(horizontal = 14.dp, vertical = 12.dp) else PaddingValues(horizontal = 14.dp, vertical = 10.dp)
    val storyPadding = if (tier == FinishTier.C) PaddingValues(6.dp, 6.dp, 8.dp, 6.dp) else PaddingValues(start = 8.dp, top = 8.dp, end = 10.dp, bottom = 8.dp)
}

// ---------------------------------------------------------------------------------------------------------------------------------
// Movimiento del paquete (filas `lx-A*` de la matriz)
// ---------------------------------------------------------------------------------------------------------------------------------

private val Estandar get() = KpknMotion.curve("estandar").easing

/** `.lx-rise` · `lxrise2` 0,55 s `estandar` con su retardo, `forwards`: nace con opacidad 0 y 14 dp más abajo (`lx-A09`, `lx-X08`). */
private val riseCache = HashMap<Int, KpknAnimation>()
private fun rise(delayMs: Int): KpknAnimation = riseCache.getOrPut(delayMs) { makeRise(delayMs, 550) }

private fun makeRise(delayMs: Int, durationMs: Int): KpknAnimation = KpknAnimation(
    kpknKeyframes("lxrise2", "@keyframes lxrise2 { 100% { opacity: 1; transform: none; } }") {
        base(KpknChannel.Alpha, 0f)
        base(KpknChannel.TranslateYDp, 14f)
        at(1f) { alpha(1f); transformNone() }
    },
    KpknDuration(durationMs), Estandar, KpknDuration(delayMs), fill = KpknFill.Forwards,
)

/** `.lx-mu` (`lx-A05`): `lxrise2` 0,5 s con solo opacidad (la fila no lleva `transform` inicial). */
private val fadeUpCache = HashMap<Int, KpknAnimation>()
private fun fadeUp(delayMs: Int): KpknAnimation = fadeUpCache.getOrPut(delayMs) { makeFadeUp(delayMs) }

private fun makeFadeUp(delayMs: Int): KpknAnimation = KpknAnimation(
    kpknKeyframes("lxrise2", "@keyframes lxrise2 { 100% { opacity: 1; transform: none; } }") {
        base(KpknChannel.Alpha, 0f)
        at(1f) { alpha(1f) }
    },
    KpknDuration(500), Estandar, KpknDuration(delayMs), fill = KpknFill.Forwards,
)

/** `lxgrow` (`lx-A06`): `scaleX(0)` → `none` en 0,8 s, `both`, desde el borde izquierdo. */
private val growCache = HashMap<Int, KpknAnimation>()
private fun grow(delayMs: Int): KpknAnimation = growCache.getOrPut(delayMs) { makeGrow(delayMs) }

private fun makeGrow(delayMs: Int): KpknAnimation = KpknAnimation(
    kpknKeyframes("lxgrow", "@keyframes lxgrow { 0% { transform: scaleX(0); } 100% { transform: none; } }") {
        at(0f) { scaleX(0f) }
        at(1f) { transformNone() }
    },
    KpknDuration(800), Estandar, KpknDuration(delayMs), fill = KpknFill.Both,
)

/** `drawln` (`lx-A01`, `lx-A02`, `lx-A08`, `lx-A15`): el trazo se dibuja de 0 a 1 con la curva `trazo`. [progress] devuelve 0..1. */
private fun draw(durationMs: Int, delayMs: Int = 0, curve: String = "trazo"): KpknAnimation = KpknAnimation(
    kpknKeyframes("drawln", "@keyframes drawln { 100% { stroke-dashoffset: 0; } }") {
        base(KpknChannel.DashOffset, 1f)
        at(1f) { dashOffset(0f) }
    },
    KpknDuration(durationMs), KpknMotion.curve(curve).easing, KpknDuration(delayMs), fill = KpknFill.Both,
)

/** `lxfade` (`lx-A10`, `lx-A11`): opacidad 0 → 1 con `suave`. */
private fun fadeIn(durationMs: Int, delayMs: Int): KpknAnimation = KpknAnimation(
    kpknKeyframes("lxfade", "@keyframes lxfade { 0% { opacity: 0; } 100% { opacity: 1; } }") {
        at(0f) { alpha(0f) }
        at(1f) { alpha(1f) }
    },
    KpknDuration(durationMs), KpknMotion.curve("suave").easing, KpknDuration(delayMs), fill = KpknFill.Both,
)

/** `lx-A12`: el indicador de «Guardando…» gira (`lxspin`, 0,8 s lineal, infinito). */
internal val LxSpinAnimation: KpknAnimation by lazy {
    KpknAnimation(
        kpknKeyframes("lxspin", "@keyframes lxspin { 100% { transform: rotate(360deg); } }") { at(1f) { rotate(360f) } },
        KpknDuration(800), KpknMotion.curve("lineal").easing, iterations = KpknAnimation.INFINITE,
    )
}

// ---------------------------------------------------------------------------------------------------------------------------------
// La hoja
// ---------------------------------------------------------------------------------------------------------------------------------

/** Todo lo que la hoja muestra y pide: el estado vive en [RecordedSessionFinishSheet]; esto solo dibuja (y lo usan las hojas de render). */
internal class FinishSheetState(
    val model: FinishModel,
    val dims: FinishDims,
    val saving: Boolean,
    val errorMessage: String?,
    val blockedMessage: String?,
    val notes: String,
    val photoCount: Int,
    val shareAfterSave: Boolean,
    val panelOpen: Boolean,
    val panelKind: FinishPanelKind = FinishPanelKind.NOTES,
    val checklist: List<SessionChecklistItem>,
    val effortSelected: Double?,
    val storyThumb: androidx.compose.ui.graphics.ImageBitmap?,
    val ridges: List<List<Float>>,
    val ridgeRecordIndex: Int,
    val storyTemplateSummary: String = "General · ejercicios y progreso",
    val pendingSeriesNotice: PendingSeriesNotice? = null,
)

internal enum class FinishPanelKind { NOTES, EFFORT }
private enum class FinishDeckTab(val label: String) { HITOS("Hitos"), COMPARATIVA("Comparativa"), MUSCULOS("Músculos") }

internal class FinishSheetActions(
    val onShare: (Boolean) -> Unit = {},
    val onPrepareStory: () -> Unit = {},
    val onOpenNotes: () -> Unit = {},
    val onOpenRate: () -> Unit = {},
    val onClosePanel: () -> Unit = {},
    val onPhotos: () -> Unit = {},
    val onSave: () -> Unit = {},
    val onBack: () -> Unit = {},
    val onDiscard: () -> Unit = {},
    val onNotes: (String) -> Unit = {},
    val onEffort: (Double?) -> Unit = {},
    val onAddTask: (String) -> Unit = {},
    val onToggleTask: (String) -> Unit = {},
    val onRemoveTask: (String) -> Unit = {},
    val onContinuePending: () -> Unit = {},
)

/** El contenido de la hoja (sin la ventana): arte del encabezado, módulos, acciones y el panel de anotaciones. */
@Composable
internal fun FinishSheetContent(
    state: FinishSheetState,
    actions: FinishSheetActions,
    modifier: Modifier = Modifier,
    scrollState: androidx.compose.foundation.ScrollState = rememberScrollState(),
) {
    val m = state.model
    val d = state.dims
    Box(modifier.fillMaxWidth().clipToBounds().testTag("finish-sheet")) {
        // `.lx-art`: 200 dp de líneas detrás de todo, pegadas arriba (posición absoluta en el lienzo: no empuja nada).
        RidgeArt(state.ridges, state.ridgeRecordIndex, Modifier.fillMaxWidth().height(200.dp).align(Alignment.TopStart))
        Column(Modifier.fillMaxWidth().verticalScroll(scrollState)) {
            Box {
                Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = d.headerTop.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("RESUMEN DE SESIÓN", style = Lx.inter(11.sp, FontWeight.SemiBold, 1.5.sp), color = Lx.Muted)
                    }
                    Text(m.name, Modifier.padding(top = 5.dp, bottom = 3.dp).testTag("finish-title"),
                        style = androidx.compose.ui.text.TextStyle(fontFamily = WizardFonts.display, fontWeight = FontWeight.Bold, fontSize = 25.sp, lineHeight = 27.5.sp, letterSpacing = (-0.6).sp),
                        color = Lx.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        androidx.compose.ui.text.buildAnnotatedString {
                            append("${m.dateLine} · ")
                            pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.SemiBold, color = Lx.Ink)); append(finishDurationLabel(m.durationSeconds, m.minutes)); pop()
                            append(" · ${finishActivityLabel(m)}")
                        },
                        style = Lx.inter(12.5.sp), color = Lx.Muted,
                    )
                }
            }
            val defaultTab = when {
                m.recordList.isNotEmpty() || m.milestones.isNotEmpty() -> FinishDeckTab.HITOS
                m.liftProgress.any { it.best != null } -> FinishDeckTab.COMPARATIVA
                else -> FinishDeckTab.MUSCULOS
            }
            var selectedDeckTab by rememberSaveable(m.name, m.records, m.milestones.size) { mutableStateOf(defaultTab) }
            Column(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (m.sets > 0) DataModule(state, actions)
                m.cardio?.let { CardioRow(it) }
                if (!m.empty && (m.record != null || m.milestones.isNotEmpty())) {
                    FeaturedMilestoneBanner(m) { selectedDeckTab = FinishDeckTab.HITOS }
                }
                if (!m.empty && (m.recordList.isNotEmpty() || m.milestones.isNotEmpty() || m.liftProgress.any { it.best != null } || m.muscles.isNotEmpty())) {
                    FinishDetailDeck(
                        model = m,
                        dims = d,
                        selectedTab = selectedDeckTab,
                        onSelectTab = { selectedDeckTab = it },
                    )
                }
            }
            Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = if (m.empty) 18.dp else 8.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!m.empty) StoryCard(state, actions)
                QuickRow(state, actions)
                state.errorMessage?.let {
                    Row(Modifier.fillMaxWidth().semantics { contentDescription = it }.testTag("finish-error"), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(KpknLineIcons.Info, null, Modifier.padding(top = 1.dp).size(15.dp), tint = Lx.Danger)
                        Text(it, style = Lx.inter(12.sp, line = 16.8.sp), color = Lx.Danger)
                    }
                }
                state.blockedMessage?.let {
                    Text(it, Modifier.fillMaxWidth().testTag("finish-blocked"), style = Lx.inter(12.5.sp, line = 17.5.sp), color = Lx.Faint, textAlign = TextAlign.Center)
                }
                state.pendingSeriesNotice?.let { notice ->
                    Text(notice.message, Modifier.fillMaxWidth().testTag("finish-pending-series"), style = Lx.inter(12.5.sp, line = 17.5.sp), color = Lx.Faint)
                    Text(notice.actionLabel, Modifier.clickable(enabled = !state.saving, onClick = actions.onContinuePending).padding(vertical = 8.dp).testTag("finish-continue-pending"), style = Lx.inter(13.sp, FontWeight.SemiBold), color = Lx.Ink)
                }
                SaveButton(state, actions)
                BackButton(state, actions)
            }
        }
        NotesPanel(state, actions)
    }
}

// ---------------------------------------------------------------------------------------------------------------------------------
// Arte del encabezado: una línea por ejercicio con un pico por serie
// ---------------------------------------------------------------------------------------------------------------------------------

@Composable
private fun RidgeArt(rows: List<List<Float>>, recordIndex: Int, modifier: Modifier) {
    val pose = rememberKpknPose(remember { draw(1600) })
    Canvas(modifier.graphicsLayer { alpha = 0.34f }) {
        val s = max(size.width / 360f, size.height / 200f)
        val progress = (1f - pose.pose()[KpknChannel.DashOffset]).coerceIn(0f, 1f)
        rows.take(5).forEachIndexed { k, sets ->
            val y0 = 70f + k * 26f
            val n = sets.size
            val record = k == recordIndex
            val baseAlpha = if (record) 0.9f else (0.44f - k * 0.04f).coerceAtLeast(0.18f)
            val color = if (record) Lx.Mus else Lx.Ink
            val width = (if (record) 1.7f else 1.0f) * s
            val steps = 90
            val shown = (steps * progress).toInt()
            var px = 0f
            var py = 0f
            for (i in 0..shown.coerceAtMost(steps)) {
                val x = i * 4f
                var h = 0f
                sets.forEachIndexed { j, v ->
                    val cx = 60f + j * (240f / max(1, n - 1))
                    h += v * 62f * exp(-((x - cx) / (11f + k)).pow(2))
                }
                val y = y0 - h
                if (i > 0) {
                    // Atenuada en el lateral izquierdo para que el título se lea sin interferencias y concentrada arriba a la derecha.
                    val dx = (360f - (x + px) / 2f) / (0.68f * 360f)
                    val dy = ((y + py) / 2f) / 185f
                    val dist = kotlin.math.sqrt(dx * dx + dy * dy)
                    val mask = when { dist <= 0.18f -> 1f; dist >= 0.70f -> 0f; else -> (0.70f - dist) / 0.52f }
                    if (mask > 0f) drawLine(color.copy(alpha = baseAlpha * mask), Offset(px * s, py * s), Offset(x * s, y * s), strokeWidth = width, cap = StrokeCap.Round)
                }
                px = x
                py = y
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------------------------
// Volumen, tendencia y anillos
// ---------------------------------------------------------------------------------------------------------------------------------

@Composable
private fun DataModule(state: FinishSheetState, actions: FinishSheetActions) {
    val m = state.model
    val d = state.dims
    var avg4wMode by rememberSaveable(m.name) { mutableStateOf(false) }
    var activeKpi by rememberSaveable(m.name) { mutableStateOf<String?>(null) }
    val trendPoints = remember(m.trend, m.trendPoints) {
        if (m.trendPoints.size >= 2) m.trendPoints
        else m.trend.mapIndexed { idx, v ->
            FinishTrendPoint(if (idx == m.trend.lastIndex) "Hoy" else "S${idx + 1}", v)
        }
    }
    var selectedTrendIdx by rememberSaveable(m.name, trendPoints.size) { mutableStateOf((trendPoints.size - 1).coerceAtLeast(0)) }

    Column(
        Modifier.fillMaxWidth().kpknAnimate(rise(200)).clip(RoundedCornerShape(22.dp)).background(ModuleBg).border(1.dp, ModuleBorder, RoundedCornerShape(22.dp))
            .padding(d.dataPadding).testTag("finish-data"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("VOLUMEN", style = Lx.inter(10.5.sp, FontWeight.SemiBold, 1.4.sp), color = Lx.Faint)
                if (m.volumeAvg4wDelta != null) {
                    Row(
                        Modifier.clip(RoundedCornerShape(50)).background(Color(0x0DFFFFFF)).padding(2.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        BaselineChip("vs. última", !avg4wMode) { avg4wMode = false }
                        BaselineChip("media 4s", avg4wMode) { avg4wMode = true }
                    }
                }
            }
            Row(Modifier.padding(top = 3.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.Bottom) {
                val volumeStyle = androidx.compose.ui.text.TextStyle(fontFamily = WizardFonts.display, fontWeight = FontWeight.ExtraBold, fontSize = d.volumeSize.sp, lineHeight = d.volumeSize.sp, letterSpacing = (-1.2).sp)
                if (m.empty) Text("0", Modifier.alignByBaseline(), style = volumeStyle, color = Lx.Ink)
                else KpknCountUp(m.volume, style = volumeStyle.copy(color = Lx.Ink), modifier = Modifier.alignByBaseline().testTag("finish-volume"))
                Text("kg", Modifier.alignByBaseline(), style = Lx.inter(15.sp, FontWeight.SemiBold), color = Lx.Muted, maxLines = 1, softWrap = false)
            }
            VolumeDelta(m, avg4wMode)
        }

        if (d.showSpark && trendPoints.size >= 2) {
            val safeIdx = selectedTrendIdx.coerceIn(0, trendPoints.lastIndex)
            val activePt = trendPoints[safeIdx]
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${activePt.dateLabel} · ${finishGrouped(activePt.volume, 0)} kg",
                        style = Lx.num(11.sp, weight = FontWeight.SemiBold),
                        color = if (safeIdx == trendPoints.lastIndex) Lx.Ink else Lx.Muted,
                    )
                    Text("Toca para comparar", style = Lx.inter(10.sp), color = Lx.Dim)
                }
                InteractiveSpark(
                    points = trendPoints.map { it.volume },
                    selectedIndex = safeIdx,
                    onSelectIndex = { selectedTrendIdx = it },
                    modifier = Modifier.fillMaxWidth().height(64.dp).testTag("finish-volume-chart"),
                )
            }
        }

        Row(
            Modifier.fillMaxWidth().drawWithContent {
                drawLine(Lx.Div, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx())
                drawContent()
            }.padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val i0 = 0.55f
            val durationSeconds = m.durationSeconds
            val durationValue = if (durationSeconds in 1..59) durationSeconds.toString() else m.minutes.toString()
            val durationUnit = if (durationSeconds in 1..59) "seg" else "min"
            val targetLabel = m.targetMinutes?.let { "meta $it min" } ?: "vuelta 60 min"
            Tile(
                Modifier.weight(1f),
                m.durationFraction,
                durationValue,
                "Duración",
                "$durationUnit · $targetLabel",
                d,
                i0 + 0.07f,
                false,
                active = activeKpi == "duration",
            ) { activeKpi = if (activeKpi == "duration") null else "duration" }
            Tile(
                Modifier.weight(1f),
                m.setsFraction,
                "${m.sets}",
                "Series",
                "de ${m.plannedSets} planificadas",
                d,
                i0 + 0.14f,
                false,
                active = activeKpi == "sets",
            ) { activeKpi = if (activeKpi == "sets") null else "sets" }
        }

        activeKpi?.let { kpi ->
            val pct = if (kpi == "duration") (m.durationFraction * 100).toInt() else (m.setsFraction * 100).toInt()
            val detailText = if (kpi == "duration") {
                "Tiempo efectivo: ${finishDurationLabel(m.durationSeconds, m.minutes)} ($pct% de la referencia)"
            } else {
                "Cumplimiento: ${m.sets} de ${m.plannedSets} series ($pct%)"
            }
            Text(
                detailText,
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Color(0x0AFFFFFF)).padding(horizontal = 10.dp, vertical = 6.dp),
                style = Lx.inter(11.sp),
                color = Lx.Muted,
            )
        }

        // Ecualizador de Esfuerzo inline (0–10), pre-cargado con el promedio declarado serie por serie en vivo.
        val effectiveEffort = state.effortSelected ?: m.recordedEffort.value
        val isManualEffort = state.effortSelected != null
        Column(
            Modifier.fillMaxWidth().drawWithContent {
                drawLine(Lx.Div, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx())
                drawContent()
            }.padding(top = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("ESFUERZO (sRPE)", style = Lx.inter(10.5.sp, FontWeight.SemiBold, 1.4.sp), color = Lx.Faint)
                    Box(
                        Modifier.clip(RoundedCornerShape(50)).background(Color(0x0DFFFFFF)).padding(horizontal = 7.dp, vertical = 2.dp),
                    ) {
                        Text(
                            if (isManualEffort) "Manual" else if (m.recordedEffort.coveredSets > 0) "Auto · ${m.recordedEffort.coverageLabel}" else "Sin dato",
                            style = Lx.inter(10.sp, FontWeight.Medium),
                            color = Lx.Muted,
                        )
                    }
                    if (isManualEffort) {
                        Text(
                            "↺ auto",
                            Modifier.clip(RoundedCornerShape(50)).background(Color(0x14FFFFFF))
                                .clickable(role = Role.Button) { actions.onEffort(null) }
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                                .testTag("finish-effort-reset"),
                            style = Lx.inter(10.sp, FontWeight.SemiBold),
                            color = Lx.Ink,
                        )
                    }
                }
                Text(
                    effectiveEffort?.let { finishGrouped(it, if (isManualEffort) 0 else 1) } ?: "—",
                    modifier = Modifier.testTag("finish-effort-value"),
                    style = androidx.compose.ui.text.TextStyle(fontFamily = WizardFonts.display, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, letterSpacing = (-0.5).sp),
                    color = Lx.Ink,
                )
            }
            EffortScale(effectiveEffort, actions.onEffort, compact = true, showHeader = false)
        }
    }
}

@Composable
private fun BaselineChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        Modifier.clip(RoundedCornerShape(50))
            .background(if (selected) Lx.Ink else Color.Transparent)
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        style = Lx.inter(10.sp, if (selected) FontWeight.SemiBold else FontWeight.Medium),
        color = if (selected) Lx.OnInk else Lx.Muted,
        maxLines = 1,
    )
}

@Composable
private fun VolumeDelta(m: FinishModel, avg4wMode: Boolean = false) {
    val delta = if (avg4wMode) m.volumeAvg4wDelta ?: m.volumeDelta else m.volumeDelta
    val suffix = if (avg4wMode && m.volumeAvg4wDelta != null) "vs. media 4 sem." else "vs. última vez"
    when {
        m.empty -> Text("Sin series todavía", style = Lx.inter(12.5.sp, FontWeight.Medium), color = Lx.Muted)
        delta == null -> Text("Primera vez con esta sesión", style = Lx.inter(12.5.sp, FontWeight.Medium), color = Lx.Muted)
        else -> {
            val down = delta < 0
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (down) KpknLineIcons.TrendDown else KpknLineIcons.TrendUp, null, Modifier.size(14.dp), tint = if (down) Lx.Muted else Lx.Done)
                Text(
                    androidx.compose.ui.text.buildAnnotatedString {
                        append((if (down) "−" else "+") + finishGrouped(abs(delta), 0) + " kg ")
                        pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Normal, fontSize = 11.5.sp, color = Lx.Faint)); append(suffix); pop()
                    },
                    style = Lx.inter(12.5.sp, FontWeight.SemiBold), color = if (down) Lx.Muted else Lx.Done,
                )
            }
        }
    }
}

/** Gráfica interactiva de volumen a ancho completo con selección táctil de sesión. */
@Composable
private fun InteractiveSpark(points: List<Double>, selectedIndex: Int, onSelectIndex: (Int) -> Unit, modifier: Modifier) {
    val line = rememberKpknPose(remember { draw(1600) })
    val area = rememberKpknPose(remember { fadeIn(600, 1000) })
    val dot = rememberKpknPose(remember { fadeIn(300, 1300) })
    var widthPx by remember { mutableStateOf(1f) }
    fun indexAt(x: Float): Int {
        if (points.size <= 1) return 0
        val pad = 10f
        val span = (widthPx - 2 * pad).coerceAtLeast(1f)
        return (((x - pad) / span) * (points.size - 1)).let { kotlin.math.round(it).toInt() }.coerceIn(0, points.lastIndex)
    }
    Canvas(
        modifier
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(points.size) { detectTapGestures { p -> onSelectIndex(indexAt(p.x)) } }
            .pointerInput(points.size) { detectDragGestures { change, _ -> onSelectIndex(indexAt(change.position.x)) } },
    ) {
        val w = size.width
        val h = size.height
        val padX = 10.dp.toPx()
        val padY = 10.dp.toPx()
        val lo = points.min()
        val sp = (points.max() - lo).takeIf { it > 0.0 } ?: 1.0
        fun px(i: Int) = padX + i * (w - 2 * padX) / (points.size - 1)
        fun py(v: Double) = h - padY - ((v - lo) / sp).toFloat() * (h - 2 * padY)
        val path = Path().apply { points.forEachIndexed { i, v -> if (i == 0) moveTo(px(i), py(v)) else lineTo(px(i), py(v)) } }
        val fill = Path().apply {
            addPath(path); lineTo(px(points.size - 1), h); lineTo(px(0), h); close()
        }
        drawPath(fill, Brush.verticalGradient(listOf(Lx.Ink.copy(alpha = 0.16f * area.pose().alpha), Color.Transparent), startY = 0f, endY = h))
        val measure = android.graphics.PathMeasure(path.asAndroidPath(), false)
        val visible = Path()
        measure.getSegment(0f, measure.length * (1f - line.pose()[KpknChannel.DashOffset]).coerceIn(0f, 1f), visible.asAndroidPath(), true)
        drawPath(visible, Lx.Ink, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

        val a = dot.pose().alpha
        points.forEachIndexed { idx, v ->
            val center = Offset(px(idx), py(v))
            if (idx == selectedIndex) {
                drawCircle(Lx.Ink.copy(alpha = 0.25f * a), 8.dp.toPx(), center)
                drawCircle(Lx.Ink.copy(alpha = a), 4.2.dp.toPx(), center)
            } else {
                drawCircle(ModuleBg, 3.dp.toPx(), center)
                drawCircle(Lx.Muted.copy(alpha = 0.8f * a), 3.dp.toPx(), center, style = Stroke(1.5.dp.toPx()))
            }
        }
    }
}

/** Un anillo con su cifra, su nombre y su subtítulo. Se llena en 1,1 s con la curva `trazo` y el retardo propio (`lx-A08`). */
@Composable
private fun Tile(
    modifier: Modifier,
    fraction: Float,
    center: String,
    label: String,
    sub: String,
    d: FinishDims,
    delaySec: Float,
    coral: Boolean,
    active: Boolean = false,
    onClick: (() -> Unit)?,
) {
    Row(
        modifier.kpknAnimate(rise((delaySec * 1000).toInt()))
            .clip(RoundedCornerShape(14.dp))
            .background(if (active) Color(0x12FFFFFF) else Color(0x06FFFFFF))
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val pose = rememberKpknPose(remember(delaySec) { draw(1100, (delaySec * 1000).toInt()) })
        Box(Modifier.size((d.ring - 4).dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val u = size.minDimension / 56f
                drawCircle(Color(0x12FFFFFF), 22f * u, center = Offset(28f * u, 28f * u), style = Stroke(5f * u))
                val sweep = 360f * fraction.coerceIn(0f, 1f) * (1f - pose.pose()[KpknChannel.DashOffset])
                if (sweep > 0f) drawArc(if (coral) Lx.Mus else Lx.Ink, -90f, sweep, false, topLeft = Offset(6f * u, 6f * u), size = Size(44f * u, 44f * u),
                    style = Stroke(5f * u, cap = StrokeCap.Round))
            }
            Text(center, style = Lx.num(14.sp), color = Lx.Ink)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(label, style = Lx.inter(12.sp, FontWeight.SemiBold), color = Lx.Ink, maxLines = 1)
            if (d.showTileSub) Text(sub, style = Lx.inter(10.5.sp), color = Lx.Faint, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------------------------
// Cardio, récord, músculos, mejoras
// ---------------------------------------------------------------------------------------------------------------------------------

@Composable
private fun CardioRow(c: FinishCardio) {
    Row(
        Modifier.fillMaxWidth().kpknAnimate(rise(850)).heightIn(min = 50.dp).clip(RoundedCornerShape(18.dp)).background(ModuleBg).border(1.dp, ModuleBorder, RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp).testTag("finish-cardio"),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(30.dp).clip(CircleShape).background(Color(0x1F8FB2FF)), contentAlignment = Alignment.Center) {
            Icon(KpknLineIcons.Run, null, Modifier.size(18.dp), tint = Color(0xFF8FB2FF))
        }
        Text(c.type + if (c.blockCount > 1) " · " + c.blockCount + " bloques" else "", style = Lx.inter(13.sp, FontWeight.SemiBold), color = Lx.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        listOfNotNull(
            c.durationFormatted,
            c.km?.let { finishGrouped(it, 1) to "km" },
            c.heartRate?.let { "$it" to "lpm" }, c.kcal?.let { "$it" to "kcal" },
        ).forEach { (value, unit) ->
            Text(
                androidx.compose.ui.text.buildAnnotatedString {
                    pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = Lx.Ink)); append(value); pop()
                    append(" $unit")
                },
                style = Lx.num(12.sp, weight = FontWeight.Normal), color = Lx.Faint, maxLines = 1,
            )
        }
    }
}

@Composable
private fun FeaturedMilestoneBanner(model: FinishModel, onOpenMilestones: () -> Unit) {
    val topRecord = model.record
    val topMilestone = model.milestones.firstOrNull()
    val title: String
    val subtitle: String
    when {
        topRecord != null -> {
            title = "${if (topRecord.isInitialReference) "Referencia" else "Récord"} · ${topRecord.exerciseName}"
            subtitle = topRecord.summaryLine
        }
        topMilestone != null -> {
            val prefix = if (topMilestone.kind == "star_goal_reached") "✦ Meta Estrella" else "Hito de sesión"
            title = "$prefix · ${topMilestone.exerciseName}"
            subtitle = topMilestone.label + (topMilestone.detail?.let { " · $it" } ?: "")
        }
        else -> return
    }
    val burst = rememberKpknPose(remember { draw(1000, 950, "estandar") })
    Row(
        Modifier.fillMaxWidth().kpknAnimate(rise(900)).clip(RoundedCornerShape(18.dp))
            .background(Color(0x14F49A6E)).border(1.dp, Color(0x44F49A6E), RoundedCornerShape(18.dp))
            .clickable(role = Role.Button, onClick = onOpenMilestones)
            .padding(start = 9.dp, top = 9.dp, end = 12.dp, bottom = 9.dp).testTag("finish-record"),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(54.dp).graphicsLayer { clip = false }) {
                val u = size.minDimension / 60f
                val p = (1f - burst.pose()[KpknChannel.DashOffset]).coerceIn(0f, 1f)
                for (i in 0 until 16) {
                    val a = i / 16f * 2f * PI.toFloat()
                    val r0 = 15f + (i * 7 % 3) + 0.5f
                    val r1 = 24f + (i * 11 % 10)
                    val x0 = 30f + cos(a) * r0; val y0 = 30f + sin(a) * r0
                    val x1 = x0 + (30f + cos(a) * r1 - x0) * p; val y1 = y0 + (30f + sin(a) * r1 - y0) * p
                    drawLine(Lx.Mus.copy(alpha = if (i % 4 == 0) 1f else 0.6f), Offset(x0 * u, y0 * u), Offset(x1 * u, y1 * u),
                        strokeWidth = (if (i % 4 == 0) 1.8f else 1f) * u, cap = StrokeCap.Round)
                }
            }
            Icon(KpknLineIcons.Trophy, null, Modifier.size(18.dp), tint = Lx.Mus)
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = Lx.inter(13.sp, FontWeight.SemiBold), color = Lx.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, Modifier.padding(top = 2.dp), style = Lx.inter(11.5.sp), color = Lx.Mus, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        val totalMilestones = maxOf(model.recordList.size, model.milestones.size)
        if (totalMilestones > 1) {
            Text(
                "+${totalMilestones - 1}",
                Modifier.clip(RoundedCornerShape(50)).background(Color(0x22F49A6E)).padding(horizontal = 8.dp, vertical = 3.dp),
                style = Lx.num(11.sp, weight = FontWeight.SemiBold),
                color = Lx.Mus,
            )
        }
    }
}

@Composable
private fun FinishDetailDeck(
    model: FinishModel,
    dims: FinishDims,
    selectedTab: FinishDeckTab,
    onSelectTab: (FinishDeckTab) -> Unit,
) {
    val hitoCount = maxOf(model.recordList.size, model.milestones.size)
    Column(
        Modifier.fillMaxWidth().kpknAnimate(rise(700)).clip(RoundedCornerShape(22.dp)).background(ModuleBg)
            .border(1.dp, ModuleBorder, RoundedCornerShape(22.dp)).padding(dims.musclePadding).testTag("finish-deck"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0x0AFFFFFF)).padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            FinishDeckTab.entries.forEach { tab ->
                val selected = tab == selectedTab
                val label = if (tab == FinishDeckTab.HITOS && hitoCount > 0) "${tab.label} ($hitoCount)" else tab.label
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(11.dp))
                        .background(if (selected) Color(0x1CFFFFFF) else Color.Transparent)
                        .clickable(role = Role.Tab) { onSelectTab(tab) }
                        .padding(vertical = 7.dp)
                        .testTag("finish-deck-tab-${tab.name.lowercase()}"),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        style = Lx.inter(11.5.sp, if (selected) FontWeight.SemiBold else FontWeight.Medium),
                        color = if (selected) Lx.Ink else Lx.Muted,
                        maxLines = 1,
                    )
                }
            }
        }
        when (selectedTab) {
            FinishDeckTab.HITOS -> MilestonesDeckPage(model)
            FinishDeckTab.COMPARATIVA -> {
                val comparable = model.liftProgress.any { it.best != null }
                if (comparable) WeeklyComparison(model.liftProgress)
                else Text("Sin ejercicios comparables en esta sesión.", style = Lx.inter(12.sp), color = Lx.Faint)
            }
            FinishDeckTab.MUSCULOS -> {
                if (model.muscles.isNotEmpty()) MusclesModuleContent(model)
                else Text("Sin volumen muscular registrado.", style = Lx.inter(12.sp), color = Lx.Faint)
            }
        }
    }
}

@Composable
private fun MilestonesDeckPage(model: FinishModel) {
    var expandedIdx by rememberSaveable { mutableStateOf<Int?>(null) }
    val records = model.recordList
    val sessionMilestones = model.milestones
    if (records.isEmpty() && sessionMilestones.isEmpty()) {
        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Sin récords nuevos en esta sesión", style = Lx.inter(12.5.sp, FontWeight.Medium), color = Lx.Muted)
            Text("La carga queda registrada para tus comparativas de próxima semana.", style = Lx.inter(11.sp), color = Lx.Faint)
        }
        return
    }
    Column(Modifier.fillMaxWidth().testTag("finish-milestones-list"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        records.forEachIndexed { idx, r ->
            val open = expandedIdx == idx
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0x08FFFFFF))
                    .clickable(role = Role.Button) { expandedIdx = if (open) null else idx }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${if (r.isInitialReference) "Referencia" else "Récord"} · ${r.exerciseName}",
                        Modifier.weight(1f),
                        style = Lx.inter(12.5.sp, FontWeight.SemiBold),
                        color = Lx.Ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        r.improvement?.let { "+${finishGrouped(it, 1)} ${r.unit}" } ?: r.currentLabel,
                        style = Lx.num(11.5.sp, weight = FontWeight.SemiBold),
                        color = Lx.Mus,
                    )
                }
                if (open) {
                    Text(r.summaryLine, style = Lx.inter(11.sp), color = Lx.Muted)
                }
            }
        }
        sessionMilestones.forEachIndexed { idx, ms ->
            val key = 100 + idx
            val open = expandedIdx == key
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0x08FFFFFF))
                    .clickable(role = Role.Button) { expandedIdx = if (open) null else key }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${if (ms.kind == "star_goal_reached") "✦ Meta Estrella" else "Hito"} · ${ms.exerciseName}",
                        Modifier.weight(1f),
                        style = Lx.inter(12.5.sp, FontWeight.SemiBold),
                        color = Lx.Ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(ms.label.ifBlank { "${finishGrouped(ms.value, 1)} kg" }, style = Lx.num(11.5.sp, weight = FontWeight.SemiBold), color = Lx.Done)
                }
                if (open) {
                    Text(ms.detail ?: "Hito alcanzado durante la sesión", style = Lx.inter(11.sp), color = Lx.Muted)
                }
            }
        }
    }
}

@Composable
private fun MusclesModuleContent(model: FinishModel) {
    val maxValue = max(1.0, model.muscles.maxOf { it.total })
    var selectedIdx by rememberSaveable(model.name) { mutableStateOf(0) }
    Column(Modifier.fillMaxWidth().testTag("finish-muscles"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("SERIES EFECTIVAS", style = Lx.inter(10.sp, FontWeight.SemiBold, 1.2.sp), color = Lx.Faint)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp, 5.dp).clip(RoundedCornerShape(3.dp)).background(Lx.Ink))
                Text("directas", style = Lx.inter(10.5.sp), color = Lx.Faint)
                Box(Modifier.padding(start = 4.dp).size(12.dp, 5.dp).clip(RoundedCornerShape(3.dp)).background(Color(0x4DF2EEE6)))
                Text("total", style = Lx.inter(10.5.sp), color = Lx.Faint)
            }
        }
        model.muscles.forEachIndexed { i, mu ->
            val delay = 200 + i * 50
            val active = i == selectedIdx
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                    .background(if (active) Color(0x0BFFFFFF) else Color.Transparent)
                    .clickable(role = Role.Button) { selectedIdx = i }
                    .padding(horizontal = 6.dp, vertical = 4.dp)
                    .kpknAnimate(fadeUp(delay)),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(mu.name, Modifier.width(92.dp), style = Lx.inter(12.sp, if (active) FontWeight.SemiBold else FontWeight.Normal), color = if (active) Lx.Ink else Lx.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color(0x12FFFFFF))) {
                    Box(Modifier.fillMaxWidth((mu.total / maxValue).toFloat().coerceIn(0f, 1f)).fillMaxHeight().kpknAnimate(grow(delay), transformOrigin = TransformOrigin(0f, 0.5f)).background(Color(0x4DF2EEE6)))
                    Box(Modifier.fillMaxWidth((mu.direct / maxValue).toFloat().coerceIn(0f, 1f)).fillMaxHeight().kpknAnimate(grow(delay), transformOrigin = TransformOrigin(0f, 0.5f)).background(Lx.Ink))
                }
                Text(finishGrouped(mu.total, 1), Modifier.width(34.dp), style = Lx.num(12.sp), color = Lx.Ink, textAlign = TextAlign.End, maxLines = 1)
            }
        }
        model.muscles.getOrNull(selectedIdx)?.let { sel ->
            val indirect = (sel.total - sel.direct).coerceAtLeast(0.0)
            Text(
                "${sel.name}: ${finishGrouped(sel.direct, 1)} directas + ${finishGrouped(indirect, 1)} indirectas",
                Modifier.padding(top = 4.dp),
                style = Lx.inter(11.sp),
                color = Lx.Muted,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LiftChips(lifts: List<FinishLiftChip>) {
    FlowRow(Modifier.fillMaxWidth().kpknAnimate(rise(900)).testTag("finish-lifts"), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        lifts.forEach { l ->
            val delta = l.deltaKg
            val up = (delta ?: 0.0) > 0.01
            val down = (delta ?: 0.0) < -0.01
            val deltaText = when {
                delta == null -> "—"
                up -> "+" + finishGrouped(delta, 1) + " kg"
                down -> "−" + finishGrouped(abs(delta), 1) + " kg"
                else -> "="
            }
            Row(
                Modifier.height(30.dp).clip(RoundedCornerShape(15.dp)).background(Color(0x0AFFFFFF)).padding(start = if (up || down) 9.dp else 11.dp, end = 11.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                if (up) Icon(KpknLineIcons.TrendUp, null, Modifier.size(14.dp), tint = Lx.Done)
                else if (down) Icon(KpknLineIcons.TrendDown, null, Modifier.size(14.dp), tint = Lx.Muted)
                Text(l.name, style = Lx.inter(12.5.sp, FontWeight.Medium), color = Lx.Muted, maxLines = 1)
                Text(deltaText, style = Lx.num(12.5.sp), color = if (up) Lx.Done else Lx.Muted)
            }
        }
    }
}

@Composable
private fun WeeklyComparison(lifts: List<FinishLiftProgress>) {
    val rows = lifts.filter { it.best != null }
    if (rows.isEmpty()) return
    var useLastSession by rememberSaveable { mutableStateOf(false) }
    var expandedIdx by rememberSaveable { mutableStateOf<Int?>(null) }
    Column(
        Modifier.fillMaxWidth().testTag("finish-week-comparison"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("PROGRESO POR EJERCICIO", style = Lx.inter(10.sp, FontWeight.SemiBold, 1.2.sp), color = Lx.Faint)
            if (rows.any { it.previous != null }) {
                Row(
                    Modifier.clip(RoundedCornerShape(50)).background(Color(0x0DFFFFFF)).padding(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    BaselineChip("Sem. ant.", !useLastSession) { useLastSession = false }
                    BaselineChip("Última", useLastSession) { useLastSession = true }
                }
            }
        }
        rows.forEachIndexed { index, lift ->
            val metric = finishWeeklyMetric(lift)
            val open = expandedIdx == index
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0x07FFFFFF))
                    .clickable(role = Role.Button) { expandedIdx = if (open) null else index }
                    .padding(horizontal = 10.dp, vertical = 8.dp)
                    .testTag("finish-week-chart-row-$index"),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                if (metric == null) {
                    Text(lift.name, style = Lx.inter(12.5.sp, FontWeight.Medium), color = Lx.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Sin métrica comparable para esta modalidad", style = Lx.inter(11.sp), color = Lx.Faint)
                } else {
                    val previous = if (useLastSession) {
                        when (metric.kind) {
                            FinishWeeklyMetricKind.ESTIMATED_ONE_RM -> lift.previous?.estimated1Rm?.takeIf { it > 0.0 }
                            FinishWeeklyMetricKind.ADDED_LOAD,
                            FinishWeeklyMetricKind.ASSISTANCE,
                            -> lift.previous?.weight?.takeIf { it >= 0.0 }
                            FinishWeeklyMetricKind.BODYWEIGHT_REPS,
                            FinishWeeklyMetricKind.TIME_SECONDS,
                            -> lift.previous?.comparableScore?.takeIf { it > 0.0 }
                            FinishWeeklyMetricKind.DISTANCE_KM -> lift.previous?.comparableScore?.div(1000.0)?.takeIf { it > 0.0 }
                        }
                    } else metric.previousValue
                    val maxValue = maxOf(metric.currentValue, previous ?: 0.0).coerceAtLeast(0.01)
                    val currentImproved = previous?.let {
                        if (metric.lowerIsBetter) metric.currentValue < it else metric.currentValue > it
                    } == true
                    val deltaVal = previous?.let { metric.currentValue - it }
                    val deltaTag = lift.deltaKg?.let { kg ->
                        val sign = if (kg > 0.01) "+" else if (kg < -0.01) "−" else "="
                        if (abs(kg) <= 0.01) "=" else "$sign${finishGrouped(abs(kg), 1)} kg"
                    } ?: deltaVal?.let {
                        val pos = if (metric.lowerIsBetter) it < -0.05 else it > 0.05
                        val sign = if (it >= 0) "+" else "−"
                        val unit = if (metric.kind == FinishWeeklyMetricKind.BODYWEIGHT_REPS) " reps" else " kg"
                        "${if (pos) "↑ " else ""}$sign${finishGrouped(abs(it), if (metric.kind == FinishWeeklyMetricKind.BODYWEIGHT_REPS) 0 else 1)}$unit"
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("${lift.name} · ${metric.unitLabel}", Modifier.weight(1f), style = Lx.inter(12.sp, FontWeight.SemiBold), color = Lx.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (deltaTag != null) {
                            Text(deltaTag, style = Lx.num(11.sp, weight = FontWeight.SemiBold), color = if (currentImproved) Lx.Done else Lx.Muted)
                        }
                    }
                    FinishWeeklyBar("Hoy", metric.currentValue, maxValue, metric.display(metric.currentValue), if (currentImproved) Lx.Done else Lx.Ink)
                    if (previous != null) {
                        FinishWeeklyBar(if (useLastSession) "Última" else "Sem. ant.", previous, maxValue, metric.display(previous), Lx.Muted)
                    } else {
                        Text(if (useLastSession) "Sin sesión previa comparable" else "Sin dato comparable la semana anterior", style = Lx.inter(10.5.sp), color = Lx.Faint)
                    }
                    if (open) {
                        Text(
                            "${lift.setCount} series hoy · ${lift.totalHistorySessions} sesiones en historial",
                            style = Lx.inter(10.5.sp),
                            color = Lx.Faint,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FinishWeeklyBar(label: String, value: Double, maxValue: Double, valueLabel: String, color: Color) {
    val fraction = (value / maxValue).toFloat().coerceIn(0.025f, 1f)
    Column(Modifier.fillMaxWidth().semantics { contentDescription = "$label: $valueLabel" }, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = Lx.inter(10.5.sp), color = Lx.Faint, maxLines = 1)
            Text(valueLabel, style = Lx.num(10.5.sp), color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color(0x12FFFFFF))) {
            Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(color, RoundedCornerShape(3.dp)))
        }
    }
}

@Composable
private fun RecordExtra(r: FinishRecord) {
    Row(
        Modifier.fillMaxWidth().testTag("finish-record-extra").padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(KpknLineIcons.Trophy, null, Modifier.size(13.dp), tint = Lx.Mus)
        Text(
            "${if (r.isInitialReference) "Referencia" else "Récord"} · ${r.exerciseName}",
            Modifier.weight(1f), style = Lx.inter(12.sp), color = Lx.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Text(r.summaryLine, style = Lx.inter(10.5.sp), color = Lx.Mus, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

// ---------------------------------------------------------------------------------------------------------------------------------
// Acciones
// ---------------------------------------------------------------------------------------------------------------------------------

@Composable
private fun StoryCard(state: FinishSheetState, actions: FinishSheetActions) {
    val d = state.dims
    Row(
        Modifier.fillMaxWidth().kpknAnimate(rise(1000)).clip(RoundedCornerShape(20.dp)).background(ModuleBg).border(1.dp, ModuleBorder, RoundedCornerShape(20.dp)).padding(d.storyPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.weight(1f).clickable(role = Role.Button, onClick = actions.onPrepareStory).testTag("finish-prepare-story"),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            if (d.showThumb) {
                val shape = RoundedCornerShape(8.dp)
                Box(Modifier.width(d.thumbWidth.dp).height((d.thumbWidth * 16f / 9f).dp).kpknShadow(KpknShadow.BordeInterior, shape).clip(shape).background(Color(0xFF0A0A0A))) {
                    state.storyThumb?.let { androidx.compose.foundation.Image(it, null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop) }
                }
            }
            Column(Modifier.weight(1f)) {
                Text("Preparar historia", style = Lx.inter(14.sp, FontWeight.SemiBold), color = Lx.Ink)
                Text(state.storyTemplateSummary, Modifier.padding(top = 2.dp), style = Lx.inter(12.sp, line = 16.2.sp), color = Lx.Faint, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(KpknLineIcons.ChevronRight, null, Modifier.size(16.dp), tint = Lx.Faint)
        }
        Column(
            Modifier.width(d.shareWidth.dp).drawWithContent {
                drawLine(Lx.Div, Offset(0f, 0f), Offset(0f, size.height), 1.dp.toPx())
                drawContent()
            }.padding(start = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("Compartir al guardar", style = Lx.inter(11.sp, line = 13.75.sp), color = Lx.Faint, textAlign = TextAlign.Center)
            LxSwitch(state.shareAfterSave) { actions.onShare(it) }
        }
    }
}

/** `.lx-sw` (44 × 26): el fondo y la bola se mueven en 200 ms con `suave` (`lx-T07`, `lx-T08`, `lx-X09`). */
@Composable
internal fun LxSwitch(on: Boolean, onChange: (Boolean) -> Unit) {
    val bg by animateKpknColorAsState(if (on) Lx.Ink else Color(0x29FFFFFF))
    val knobX by animateKpknFloatAsState(if (on) 18f else 0f)
    val knobColor by animateKpknColorAsState(if (on) Lx.OnInk else Lx.Muted)
    Box(
        Modifier.size(44.dp, 26.dp).clip(RoundedCornerShape(13.dp)).background(bg)
            .clickable(role = Role.Switch) { onChange(!on) }
            .semantics { stateDescription = if (on) "Activado" else "Desactivado"; contentDescription = "Compartir al guardar" }
            .testTag("finish-share-switch"),
    ) {
        Box(Modifier.padding(top = 3.dp, start = (3f + knobX).dp).size(20.dp).clip(CircleShape).background(knobColor))
    }
}

@Composable
private fun QuickRow(state: FinishSheetState, actions: FinishSheetActions) {
    Row(Modifier.fillMaxWidth().kpknAnimate(rise(1050)), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        QuickButton(Modifier.weight(1f), "Notas", FreeClockIcons.Note, if (state.notes.isNotBlank()) "✓" else null, actions.onOpenNotes, "finish-notes-open")
        QuickButton(Modifier.weight(1f), "Fotos", FreeClockIcons.Image, state.photoCount.takeIf { it > 0 }?.toString(), actions.onPhotos, "finish-photos-open")
    }
}

@Composable
private fun QuickButton(modifier: Modifier, label: String, icon: ImageVector, badge: String?, onClick: () -> Unit, tag: String) {
    Row(
        modifier.height(44.dp).clip(RoundedCornerShape(22.dp)).background(Color(0x0BFFFFFF)).clickable(role = Role.Button, onClick = onClick).testTag(tag),
        horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(17.dp), tint = Lx.Muted)
        Text(label, style = Lx.inter(13.sp, FontWeight.Medium), color = Lx.Muted, maxLines = 1)
        if (badge == "✓") Icon(KpknLineIcons.Check, "con nota", Modifier.size(11.dp), tint = Lx.Done)
        else if (badge != null) Text(badge, style = Lx.num(13.sp), color = Lx.Ink)
    }
}

@Composable
private fun SaveButton(state: FinishSheetState, actions: FinishSheetActions) {
    val disabled = state.saving || state.model.empty
    Row(
        Modifier.fillMaxWidth().height(state.dims.saveHeight.dp).graphicsLayer { alpha = if (disabled) 0.38f else 1f }.clip(RoundedCornerShape(50)).background(Lx.Ink)
            .clickable(enabled = !disabled, role = Role.Button, onClick = actions.onSave).testTag("finish-save"),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state.saving) {
            val spin = rememberKpknPose(LxSpinAnimation)
            // `.lx-spin`: 18 dp, borde de 2,5 dp al 33 % y el tramo superior en #111, girando.
            Canvas(Modifier.size(18.dp).graphicsLayer { rotationZ = spin.pose().rotationDeg }) {
                val w = 2.5.dp.toPx()
                val inset = Size(size.width - w, size.height - w)
                drawArc(Color(0x55111111), 0f, 360f, false, topLeft = Offset(w / 2, w / 2), size = inset, style = Stroke(w))
                drawArc(Lx.OnInk, -135f, 90f, false, topLeft = Offset(w / 2, w / 2), size = inset, style = Stroke(w))
            }
        }
        Text(
            when { state.saving -> "Guardando…"; state.model.empty -> "Sin datos para guardar"; else -> "Guardar sesión" },
            style = Lx.inter(16.sp, FontWeight.SemiBold), color = Lx.OnInk,
        )
    }
}

@Composable
private fun BackButton(state: FinishSheetState, actions: FinishSheetActions) {
    val primary = state.model.empty
    if (primary) {
        Column(Modifier.fillMaxWidth().graphicsLayer { alpha = if (state.saving) 0.38f else 1f }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(24.dp)).background(Lx.Ink)
                    .clickable(enabled = !state.saving, role = Role.Button, onClick = actions.onBack).testTag("finish-back"),
                contentAlignment = Alignment.Center,
            ) { Text("Volver a la sesión", style = Lx.inter(15.sp, FontWeight.SemiBold), color = Lx.OnInk) }
            Box(
                Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(22.dp)).background(Color(0x18F07878))
                    .clickable(enabled = !state.saving, role = Role.Button, onClick = actions.onDiscard).testTag("finish-discard-empty"),
                contentAlignment = Alignment.Center,
            ) { Text("Descartar sesión…", style = Lx.inter(14.sp, FontWeight.SemiBold), color = Lx.Danger) }
        }
    } else {
        Box(
            Modifier.fillMaxWidth().height(34.dp).graphicsLayer { alpha = if (state.saving) 0.38f else 1f }
                .clickable(enabled = !state.saving, role = Role.Button, onClick = actions.onBack).testTag("finish-back"),
            contentAlignment = Alignment.Center,
        ) { Text("Volver a la sesión", style = Lx.inter(14.sp, FontWeight.Medium), color = Lx.Muted) }
    }
}

// ---------------------------------------------------------------------------------------------------------------------------------
// Panel «Anotaciones»
// ---------------------------------------------------------------------------------------------------------------------------------

/** El panel entra desde la derecha dentro de la misma hoja (`lx-T01`: 300 ms `estandar`; reducido: salta). */
@Composable
private fun NotesPanel(state: FinishSheetState, actions: FinishSheetActions) {
    val x by animateKpknFloatAsState(if (state.panelOpen) 0f else 1.04f, KpknDuration(280), Estandar)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val width = constraints.maxWidth.toFloat()
        Column(
            Modifier.fillMaxSize().graphicsLayer { translationX = x * width }.clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(Color(0xFF1B1B1D))
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 18.dp).testTag("finish-notes-panel"),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(Color(0x0DFFFFFF)).clickable(role = Role.Button, onClick = actions.onClosePanel)
                .semantics { contentDescription = if (state.panelKind == FinishPanelKind.NOTES) "Cerrar anotaciones" else "Cerrar valoración" }
                .testTag(if (state.panelKind == FinishPanelKind.NOTES) "finish-notes-back" else "finish-rate-back"),
                    contentAlignment = Alignment.Center,
                ) { Icon(KpknLineIcons.ChevronLeft, null, Modifier.size(18.dp), tint = Lx.Ink) }
                Text(
                    if (state.panelKind == FinishPanelKind.NOTES) "Anotaciones" else "Valorar sesión",
                    style = androidx.compose.ui.text.TextStyle(fontFamily = WizardFonts.display, fontWeight = FontWeight.Bold, fontSize = 21.sp, letterSpacing = (-0.4).sp),
                    color = Lx.Ink,
                )
            }
            if (state.panelKind == FinishPanelKind.NOTES) Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("NOTAS", style = Lx.inter(10.5.sp, FontWeight.SemiBold, 1.4.sp), color = Lx.Faint)
                    Box(Modifier.padding(top = 6.dp).fillMaxWidth().heightIn(min = 84.dp).clip(RoundedCornerShape(16.dp)).background(ModuleBg).padding(12.dp)) {
                        BasicTextField(
                            value = state.notes, onValueChange = actions.onNotes,
                            textStyle = Lx.inter(14.sp, line = 19.6.sp).copy(color = Lx.Ink), cursorBrush = SolidColor(Lx.Ink),
                            modifier = Modifier.fillMaxWidth().testTag("finish-notes"),
                        )
                        if (state.notes.isBlank()) Text("Nota de la sesión", style = Lx.inter(14.sp, line = 19.6.sp), color = Lx.Faint)
                    }
                }
                Column {
                    Text("TAREAS", Modifier.padding(bottom = 2.dp), style = Lx.inter(10.5.sp, FontWeight.SemiBold, 1.4.sp), color = Lx.Faint)
                    state.checklist.forEach { item -> TaskRow(item, actions) }
                    var draft by rememberSaveable { mutableStateOf("") }
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 44.dp).drawWithContent { drawLine(Lx.Div, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx()); drawContent() },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.weight(1f)) {
                            BasicTextField(draft, { draft = it }, singleLine = true, textStyle = Lx.inter(14.sp).copy(color = Lx.Ink), cursorBrush = SolidColor(Lx.Ink), modifier = Modifier.fillMaxWidth())
                            if (draft.isEmpty()) Text("Añadir tarea", style = Lx.inter(14.sp), color = Lx.Dim)
                        }
                        Box(
                            Modifier.height(40.dp).clickable(enabled = draft.isNotBlank(), role = Role.Button) { actions.onAddTask(draft); draft = "" }.padding(horizontal = 8.dp).testTag("finish-add-task"),
                            contentAlignment = Alignment.Center,
                        ) { Text("Añadir", style = Lx.inter(13.sp, FontWeight.Medium), color = if (draft.isBlank()) Lx.Faint else Lx.Ink) }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 54.dp).drawWithContent { drawLine(Lx.Div, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx()); drawContent() }
                        .clickable(role = Role.Button, onClick = actions.onPhotos).testTag("finish-photos-row"),
                    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(38.dp).clip(CircleShape).background(Color(0x0CFFFFFF)), contentAlignment = Alignment.Center) { Icon(FreeClockIcons.Image, null, Modifier.size(18.dp), tint = Lx.Muted) }
                    Text("Fotos y medios de la sesión", Modifier.weight(1f), style = Lx.inter(14.sp, FontWeight.Medium), color = Lx.Ink)
                    Icon(KpknLineIcons.ChevronRight, null, Modifier.size(15.dp), tint = Lx.Faint)
                }
            }
            else Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("ESFUERZO PERCIBIDO", style = Lx.inter(10.5.sp, FontWeight.SemiBold, 1.4.sp), color = Lx.Faint)
                Text("¿Qué tan exigente fue la sesión?", style = Lx.inter(15.sp, FontWeight.Medium), color = Lx.Ink)
                EffortScale(state.effortSelected, actions.onEffort)
                Text("La valoración queda guardada con el resumen de esta sesión.", style = Lx.inter(12.sp, line = 16.sp), color = Lx.Faint)
            }
            LxButton("Listo", actions.onClosePanel, Modifier.fillMaxWidth(), fontSize = 16.sp, tag = "finish-notes-done")
        }
    }
}

@Composable
private fun TaskRow(item: SessionChecklistItem, actions: FinishSheetActions) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp).drawWithContent { drawLine(Lx.Div, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx()); drawContent() },
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(22.dp).clip(CircleShape).then(if (item.done) Modifier.background(Lx.Ink) else Modifier.border(1.5.dp, Color(0x40FFFFFF), CircleShape))
                .clickable(role = Role.Checkbox) { actions.onToggleTask(item.id) },
            contentAlignment = Alignment.Center,
        ) { if (item.done) Icon(KpknLineIcons.Check, null, Modifier.size(13.dp), tint = Lx.OnInk) }
        Text(item.text, Modifier.weight(1f), style = Lx.inter(14.sp).copy(textDecoration = if (item.done) TextDecoration.LineThrough else null), color = if (item.done) Lx.Faint else Lx.Ink)
        Box(Modifier.size(36.dp).clickable(role = Role.Button) { actions.onRemoveTask(item.id) }.semantics { contentDescription = "Quitar tarea" }, contentAlignment = Alignment.Center) {
            Icon(KpknLineIcons.Close, null, Modifier.size(15.dp), tint = Lx.Faint)
        }
    }
}

/** Escala de 11 barras que suben (20–42 dp en modo compacto o 22–52 dp en panel), se arrastra con el dedo; tocar la marcada otra vez vuelve a auto. */
@Composable
private fun EffortScale(effort: Double?, onEffort: (Double?) -> Unit, compact: Boolean = false, showHeader: Boolean = true) {
    Column {
        if (showHeader) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Text("ESFUERZO DE LA SESIÓN", style = Lx.inter(10.5.sp, FontWeight.SemiBold, 1.4.sp), color = Lx.Faint)
                Text(effort?.let { finishGrouped(it, 0) } ?: "—", style = androidx.compose.ui.text.TextStyle(fontFamily = WizardFonts.display, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp, letterSpacing = (-0.6).sp), color = Lx.Ink)
            }
        }
        var widthPx by remember { mutableStateOf(1f) }
        fun valueAt(x: Float) = ((x / widthPx) * 11f).toInt().coerceIn(0, 10)
        val maxBarHeight = if (compact) 40.dp else 52.dp
        Row(
            Modifier.padding(top = if (showHeader) 8.dp else 2.dp).fillMaxWidth().height(maxBarHeight)
                .pointerInput(effort) { detectTapGestures { p -> val v = valueAt(p.x); onEffort(if (effort != null && Math.round(effort).toInt() == v) null else v.toDouble()) } }
                .pointerInput(Unit) { detectDragGestures { change, _ -> onEffort(valueAt(change.position.x).toDouble()) } }
                .onSizeChanged { widthPx = it.width.toFloat() }
                .testTag("finish-effort-scale"),
            horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom,
        ) {
            val rounded = effort?.let { Math.round(it).toInt() }
            for (i in 0..10) {
                val on = rounded != null && i <= rounded
                // `lx-T06`: el fondo de cada barra cambia en 160 ms con `suave`.
                val barColor by animateKpknColorAsState(if (on) Lx.Ink else Color(0x12FFFFFF), KpknDuration(160))
                val barH = if (compact) (18 + 2 * i).dp else (22 + 3 * i).dp
                Box(
                    Modifier.weight(1f).height(barH).clip(RoundedCornerShape(if (compact) 7.dp else 9.dp)).background(barColor).semantics { contentDescription = "Esfuerzo $i" },
                    contentAlignment = Alignment.BottomCenter,
                ) { Text("$i", Modifier.padding(bottom = if (compact) 2.dp else 4.dp), style = Lx.inter(if (compact) 10.sp else 11.sp, FontWeight.SemiBold), color = if (on) Lx.OnInk else Lx.Faint) }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("0 · muy suave", style = Lx.inter(10.5.sp), color = Lx.Dim); Text("10 · máximo", style = Lx.inter(10.5.sp), color = Lx.Dim)
        }
    }
}
