package com.example.kpkn.ui.components.rings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.vector.PathParser
import com.example.kpkn.screens.onboarding.design.entreno.MuscleArtPaths
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknChip
import com.example.kpkn.ui.design.KpknCompactPill
import com.example.kpkn.ui.design.KpknSegmentedControl
import com.example.kpkn.ui.design.KpknSegmentedSize
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknType
import java.util.Locale
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * Ajuste de Músculos sobre un cuerpo (G6, LV10b). Aquí viven el componente [MuscleBodyAdjust] y su dibujo: una silueta limpia en
 * `Canvas` (cabeza, cuello, tronco, brazos y piernas con volumen) y, encima, las zonas de los 13 pilares como formas; la geometría está
 * en `RingAdjustBodyData`. Cada zona se pinta con el valor de su músculo y la elegida late suave con borde de tinta.
 */

private val BodyEdge = Color(0xFF363636)
private val BodyTop = Color(0xFF282828)
private val BodyBottom = Color(0xFF171717)
private val Ink = Color(0xFFF2EEE6)
private val SelectionTrack = Color(0xFF262626)

/** Relleno de los botones − y +: más oscuro que la caja de la fila, como en el prototipo, para que se vean sobre ella. */
private val StepFill = Color(0xFF0E0E0E)

/** Radio de toque de una zona pequeña: medio objetivo táctil de 48 dp. */
private val ZoneTouchRadius = 24.dp

/** Lo que dura cada mitad del giro del cuerpo al pasar de frente a espalda (ms). */
private const val TurnOutMillis = 150
private const val TurnInMillis = 210

/** Un músculo de la lista: su clave tal como la da quien monta el componente, el pilar con zona (si tiene) y su valor. */
private class MuscleEntry(val id: String, val pillar: String?, val value: Int)

/** Las piezas de la silueta y las formas de cada zona, ya como `Path` en unidades del dibujo. Se construyen una vez. */
private class BodyPaths(
    val silhouette: List<Path>,
    val front: Map<String, List<Path>>,
    val back: Map<String, List<Path>>,
    val frontDetails: List<Path>,
    val backDetails: List<Path>,
) {
    fun of(view: RingAdjustBodyView): Map<String, List<Path>> = if (view == RingAdjustBodyView.FRONT) front else back
}

private fun buildBodyPaths(): BodyPaths {
    // Same proportional native vectors used by the reviewed HTML; no polygon smoothing/distortion.
    val native = MuscleArtPaths
    fun path(data: String, mirrored: Boolean = false): Path = PathParser().parsePathString(data).toPath().also {
        val transform = android.graphics.Matrix().apply {
            setScale(if (mirrored) -0.86f else 0.86f, 0.86f)
            postTranslate(RingAdjustBodyWidth / 2f, 6f)
        }
        it.asAndroidPath().transform(transform)
    }
    fun pair(data: String) = listOf(path(data), path(data, true))
    val arms = "M46 116 C49 113 54 114 56 120 C59 130 57 143 54 153 C53 158 50 164 47 165 C45 158 44 148 44 139 C43 129 44 121 46 116 Z"
    val rectus = "M2.5 123 C8 121 13 122 15 128 C17 139 15 153 13.5 165 C12 177 9.5 190 3 196 C1.5 179 2.2 162 2 148 C1.8 137 1.5 129 2.5 123 Z"
    val oblique = "M19 120 C25 123 30 133 30 148 C30 166 31 180 31 190 L17 204 C18 185 20 167 20 151 C20 139 20 129 19 120 Z"
    val front = linkedMapOf(
        "Pectorales" to listOf(path(native.CHEST_REGION)),
        "Deltoides" to listOf(path(native.SHOULDERS_REGION)),
        "Bíceps" to pair(arms), "Abdomen" to pair(rectus), "Core" to pair(oblique),
        "Cuádriceps" to listOf(path(native.QUADS_REGION)),
        "Pantorrillas" to pair("M14 316 C18 315 23 319 25 327 C27 336 25 346 22 354 C20 361 18 369 15 371 C13 360 12 349 12 339 C11 329 12 321 14 316 Z"),
    )
    val back = linkedMapOf(
        "Trapecio" to pair("M9 57 C16 63 30 68 43 77 C37 80 29 86 22 94 C13 101 6 110 2 120 L2 74 C4 67 7 62 9 57 Z"),
        "Dorsales" to listOf(path(native.BACK_REGION)),
        "Deltoides" to listOf(path(native.SHOULDERS_REGION)),
        "Tríceps" to pair(arms),
        "Erectores Espinales" to pair("M2 121 C5 121 7 133 8 150 L10 180 L6 198 L2 202 Z"),
        "Glúteos" to listOf(path(native.GLUTES_REGION)),
        "Isquiosurales" to listOf(path(native.HAMSTRINGS_REGION)),
        "Pantorrillas" to listOf(path(native.CALVES_REGION)),
    )
    val armsDetail = pair("M47 120 C49 131 49 145 48 154 M47 167 C50 170 54 170 58 167 M54 182 C56 194 58 209 59 223 M60 238 L61 250 M63 238 L64 249")
    return BodyPaths(
        listOf(path(native.OUTLINE + "Z"), path(native.HEAD)), front, back,
        (native.TORSO_FRONT_CONTEXT + native.LEG_FRONT_CONTEXT + native.CHEST_DETAIL + native.SHOULDERS_DETAIL + native.ABS_DETAIL + native.QUADS_DETAIL).map { path(it) } + armsDetail + pair("M3 138 C7 140 11 140 15 138 M3 155 C7 157 10 157 14 155 M3 173 C6 175 9 175 12 172"),
        (native.TORSO_BACK_CONTEXT + native.LEG_BACK_CONTEXT + native.BACK_DETAIL + native.SHOULDERS_DETAIL + native.GLUTES_DETAIL + native.HAMSTRINGS_DETAIL + native.CALVES_DETAIL).map { path(it) } + armsDetail,
    )
}

private fun bodyPathContains(path: Path, x: Float, y: Float): Boolean {
    val bounds = path.getBounds()
    val region = android.graphics.Region()
    region.setPath(path.asAndroidPath(), android.graphics.Region(bounds.left.toInt() - 1, bounds.top.toInt() - 1, bounds.right.toInt() + 2, bounds.bottom.toInt() + 2))
    return region.contains(x.toInt(), y.toInt())
}

/**
 * Cierra una forma dada como x0, y0, x1, y1… y la suaviza con una Catmull-Rom uniforme (cada tramo es una cúbica cuyos controles salen
 * de los puntos vecinos): contornos orgánicos sin curvas a mano.
 */
private fun smoothClosedPath(points: FloatArray): Path {
    val n = points.size / 2
    fun x(i: Int): Float = points[((i % n) + n) % n * 2]
    fun y(i: Int): Float = points[((i % n) + n) % n * 2 + 1]
    val path = Path()
    path.moveTo(x(0), y(0))
    for (i in 0 until n) {
        path.cubicTo(
            x(i) + (x(i + 1) - x(i - 1)) / 6f, y(i) + (y(i + 1) - y(i - 1)) / 6f,
            x(i + 1) - (x(i + 2) - x(i)) / 6f, y(i + 1) - (y(i + 2) - y(i)) / 6f,
            x(i + 1), y(i + 1),
        )
    }
    path.close()
    return path
}

/**
 * Ajuste de Músculos (G6): el cuerpo de frente y de espalda (segmentado «Frente · Espalda», que gira la figura al cambiar) con las
 * zonas de los 13 pilares pintadas según su valor (80 o más, verde; 60 o más, ámbar; si no, el tono de Músculos). Tocar una zona la
 * elige; debajo, la zona elegida con su mini-anillo, «Cuádriceps · 54 % · cargado» y − y + de 10 (arrastrar en horizontal sobre la fila
 * afina de uno en uno). Las zonas pequeñas amplían su área táctil a 48 dp y, para TalkBack, cada una es un botón «Cuádriceps 54 %».
 *
 * [values] y [before] van por músculo, con la clave que use quien monta el componente (el pilar del motor u otro nombre que se
 * reduzca a un pilar: «Pecho» es Pectorales); [onValueChange] devuelve esa misma clave. Un pilar que no esté en [values] se pinta sin
 * dato (contorno discontinuo) y se puede tocar igual: se ajusta partiendo de 100. Los músculos sin zona (Antebrazo, Aductores…)
 * salen en una fila de chips bajo el cuerpo y se eligen igual. Los [highlighted] (los de hoy) llevan un borde fino; [initialSelected] o
 * el primero de ellos es la zona elegida al empezar.
 *
 * [onValueCommit] confirma al soltar y permite que el wizard mantenga el arrastre como vista previa local. [estimated] identifica
 * los valores estimados. [onResetSelected] conserva el reinicio individual además del global.
 * Con [onReset] sale «Restablecer» (no hace nada por sí solo: cada pantalla sabe qué es lo automático). Con [boxedSelection] en falso la
 * fila de la zona elegida va sin caja, para las páginas que no llevan tarjetas (el wizard). Valores en la escala visible 0–100.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MuscleBodyAdjust(
    values: Map<String, Int>,
    before: Map<String, Int>,
    onValueChange: (muscleId: String, value: Int) -> Unit,
    modifier: Modifier = Modifier,
    highlighted: Set<String> = emptySet(),
    initialSelected: String? = null,
    bodyHeight: Dp = 360.dp,
    boxedSelection: Boolean = true,
    onReset: (() -> Unit)? = null,
    onValueCommit: ((muscleId: String, value: Int) -> Unit)? = null,
    onResetSelected: ((muscleId: String) -> Unit)? = null,
    edited: Set<String> = emptySet(),
    resetEnabled: Boolean = true,
    estimated: Set<String> = emptySet(),
    onInteractionCancel: (() -> Unit)? = null,
) {
    val reduced = wizardReducedMotion()
    val foreground = rememberRingAdjustForeground()
    var visible by remember { mutableStateOf(false) }
    val clock = rememberRingAdjustClock(running = foreground && visible && !reduced)
    val haptics = LocalHapticFeedback.current
    val paths = remember { buildBodyPaths() }

    // Los músculos que llegan, agrupados por zona del cuerpo; los que no tienen zona van aparte.
    val entries = remember(values) { values.map { (id, value) -> MuscleEntry(id, ringAdjustBodyPillarOf(id), ringAdjustClamp(value)) } }
    val byPillar: Map<String, MuscleEntry> = remember(entries) {
        entries.filter { it.pillar != null }
            .groupBy { it.pillar!! }
            .mapValues { (pillar, list) -> list.firstOrNull { it.id == pillar } ?: list.first() }
    }
    val extras = remember(entries) { entries.filter { it.pillar == null }.sortedBy { it.id } }
    val beforeByKey: Map<String, Int> = remember(before) {
        val out = LinkedHashMap<String, Int>()
        before.forEach { (id, value) ->
            val key = ringAdjustBodyPillarOf(id) ?: id
            if (key !in out || id == key) out[key] = ringAdjustClamp(value)
        }
        out
    }
    val estimatedKeys = estimated.map { ringAdjustBodyPillarOf(it) ?: it }.toSet()
    val highlightedKeys: Set<String> = remember(highlighted) { highlighted.map { ringAdjustBodyPillarOf(it) ?: it }.toSet() }

    fun defaultKeyFor(view: RingAdjustBodyView): String {
        val pillars = RingAdjustBodyData.zonesOf(view).map { it.pillar }
        return pillars.firstOrNull { it in highlightedKeys && it in byPillar }
            ?: pillars.firstOrNull { it in byPillar }
            ?: pillars.first()
    }

    val startKey = remember {
        (initialSelected ?: highlighted.firstOrNull() ?: values.keys.firstOrNull())?.let { ringAdjustBodyPillarOf(it) ?: it }
            ?: "Cuádriceps"
    }
    var selectedKey by remember { mutableStateOf(startKey) }
    var view by remember {
        mutableStateOf(if (startKey in RingAdjustBodyPillars) ringAdjustPreferredView(startKey) else RingAdjustBodyView.FRONT)
    }
    // La figura que se ve va un paso por detrás de la elegida: gira hasta de canto, cambia de lado y termina de girar.
    var shownView by remember { mutableStateOf(view) }
    val turn = remember { Animatable(0f) }
    LaunchedEffect(view) {
        if (reduced) {
            shownView = view
            turn.snapTo(0f)
            return@LaunchedEffect
        }
        if (view == shownView) {
            // Se volvió al lado de partida a mitad del giro: la figura termina de enderezarse.
            if (turn.value != 0f) turn.animateTo(0f, tween(TurnInMillis, easing = LinearOutSlowInEasing))
            return@LaunchedEffect
        }
        turn.animateTo(90f, tween(TurnOutMillis, easing = FastOutLinearInEasing))
        shownView = view
        turn.snapTo(-90f)
        turn.animateTo(0f, tween(TurnInMillis, easing = LinearOutSlowInEasing))
    }

    val selectedIsZone = selectedKey in RingAdjustBodyPillars
    val selectedValue: Int? = if (selectedIsZone) byPillar[selectedKey]?.value else extras.firstOrNull { it.id == selectedKey }?.value
    val selectedId: String = if (selectedIsZone) byPillar[selectedKey]?.id ?: selectedKey else selectedKey
    val selectedBefore: Int? = beforeByKey[selectedKey]
    val selectedLabel = if (selectedIsZone) ringAdjustPillarLabel(selectedKey) else selectedKey

    val currentValue by rememberUpdatedState(selectedValue ?: RingAdjustMax)
    val currentOnChange by rememberUpdatedState(onValueChange)
    val currentOnCommit by rememberUpdatedState(onValueCommit)
    val emit: (Int) -> Unit = { next ->
        val clamped = ringAdjustClamp(next)
        if (clamped != selectedValue) {
            if (selectedValue != null && ringAdjustCrossedTick(selectedValue, clamped)) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
            currentOnChange(selectedId, clamped)
        }
    }

    val commit: (Int) -> Unit = { currentOnCommit?.invoke(selectedId, ringAdjustClamp(it)) }
    fun step(delta: Int) {
        val next = ringAdjustStep(selectedValue ?: RingAdjustMax, delta)
        emit(next)
        commit(next)
    }

    fun selectZone(key: String) {
        selectedKey = key
    }

    fun selectView(next: RingAdjustBodyView) {
        view = next
        // Si la zona elegida no se ve por este lado, se pasa a la primera que sí (la de hoy, si hay).
        if (selectedIsZone && RingAdjustBodyData.zonesOf(next).none { it.pillar == selectedKey }) {
            selectedKey = defaultKeyFor(next)
        }
    }

    // El color de cada zona se anima al cambiar su valor.
    val zoneColors: Map<String, Color> = RingAdjustBodyPillars.associateWith { pillar ->
        val target = byPillar[pillar]?.value?.let { if (it >= 80) Color(0xFF8FB2FF) else Color(0xFFF49A6E) } ?: Color.Transparent
        animateColorAsState(
            targetValue = target,
            animationSpec = if (reduced) tween(0) else tween(350),
            label = "ringAdjustZone",
        ).value
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Toca un músculo",
                style = KpknType.caption,
                color = WizardColors.textFaint,
                modifier = Modifier.weight(1f),
            )
            KpknSegmentedControl(
                options = listOf("Frente", "Espalda"),
                selectedIndex = view.ordinal,
                onSelect = { selectView(RingAdjustBodyView.entries[it]) },
                size = KpknSegmentedSize.Compact,
                modifier = Modifier
                    .width(188.dp)
                    .testTag("ring-adjust-body-view"),
            )
        }

        BodyCanvas(
            paths = paths,
            view = shownView,
            bodyHeight = bodyHeight,
            zoneValues = byPillar,
            zoneColors = zoneColors,
            selectedKey = selectedKey,
            highlightedKeys = highlightedKeys,
            seconds = clock,
            rotation = turn.value,
            onSelect = ::selectZone,
            onVisible = { visible = it },
            estimatedKeys = estimatedKeys,
        )

        if (highlightedKeys.isNotEmpty()) {
            Text(
                text = "Con borde: los músculos de hoy",
                style = KpknType.caption,
                color = WizardColors.textFaint,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }

        if (extras.isNotEmpty()) {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Otros músculos".uppercase(Locale.forLanguageTag("es")),
                    style = KpknType.eyebrow,
                    color = WizardColors.textMuted,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    extras.forEach { extra ->
                        KpknChip(
                            text = "${extra.id} · ${ringAdjustPercent(extra.value)}",
                            selected = selectedKey == extra.id,
                            onClick = { selectZone(extra.id) },
                            modifier = Modifier.testTag("ring-adjust-extra-${extra.id}"),
                        )
                    }
                }
            }
        }

        SelectionRow(
            label = selectedLabel,
            value = selectedValue,
            before = selectedBefore,
            boxed = boxedSelection,
            currentValue = { currentValue },
            onMinus = { step(-RingAdjustMuscleStep) },
            onPlus = { step(RingAdjustMuscleStep) },
            onFine = { emit(it) },
            onCommit = commit,
            estimated = selectedKey in estimatedKeys,
            onCancel = { onInteractionCancel?.invoke() },
        )

        if (onResetSelected != null) {
            KpknCompactPill(
                text = "Restablecer este músculo",
                onClick = { onResetSelected(selectedId) },
                filled = false,
                height = 40.dp,
                enabled = selectedId in edited,
                modifier = Modifier.testTag("ring-adjust-muscle-reset-$selectedId"),
            )
        }
        if (onReset != null) {
            KpknCompactPill(
                text = "Restablecer",
                onClick = onReset,
                enabled = resetEnabled,
                filled = false,
                height = 40.dp,
                modifier = Modifier.testTag("ring-adjust-reset"),
            )
        }
    }
}

/** El dibujo del cuerpo y, encima, un nodo de accesibilidad por zona (sin gestos propios: los toques los resuelve el dibujo). */
@Composable
private fun BodyCanvas(
    paths: BodyPaths,
    view: RingAdjustBodyView,
    bodyHeight: Dp,
    zoneValues: Map<String, MuscleEntry>,
    zoneColors: Map<String, Color>,
    selectedKey: String,
    highlightedKeys: Set<String>,
    seconds: androidx.compose.runtime.State<Float>,
    rotation: Float,
    onSelect: (String) -> Unit,
    onVisible: (Boolean) -> Unit,
    estimatedKeys: Set<String>,
) {
    val bodyWidth = bodyHeight * (RingAdjustBodyWidth / RingAdjustBodyHeight)
    val zones = paths.of(view)
    val localDensity = LocalDensity.current
    val unitDp = bodyHeight.value / RingAdjustBodyHeight
    val currentOnSelect by rememberUpdatedState(onSelect)
    Box(
        modifier = Modifier
            .size(bodyWidth, bodyHeight)
            .testTag("ring-adjust-body")
            .ringAdjustVisible(onVisible)
            .graphicsLayer {
                rotationY = rotation
                cameraDistance = 14f * localDensity.density
            },
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(view) {
                    detectTapGestures { tap ->
                        val pxPerUnit = size.height / RingAdjustBodyHeight
                        val slop = ZoneTouchRadius.toPx() / pxPerUnit
                        val x = tap.x / pxPerUnit; val y = tap.y / pxPerUnit
                        val exact = zones.entries.toList().asReversed().firstOrNull { (_, shapes) -> shapes.any { bodyPathContains(it, x, y) } }?.key
                        val nearby = zones.entries.map { (key, shapes) ->
                            key to shapes.minOf { shape ->
                                val rect = shape.getBounds()
                                val dx = maxOf(rect.left - x, 0f, x - rect.right)
                                val dy = maxOf(rect.top - y, 0f, y - rect.bottom)
                                kotlin.math.sqrt(dx * dx + dy * dy)
                            }
                        }.minByOrNull { it.second }?.takeIf { it.second <= slop }?.first
                        (exact ?: nearby)?.let { currentOnSelect(it) }
                    }
                },
        ) {
            drawBody(
                paths = paths,
                view = view,
                zoneValues = zoneValues,
                zoneColors = zoneColors,
                selectedKey = selectedKey,
                highlightedKeys = highlightedKeys,
                seconds = seconds.value,
            )
        }
        // Un botón de accesibilidad por zona, de al menos 48 dp, centrado en su forma.
        zones.forEach { (pillar, shapes) ->
            val box = remember(view, pillar, shapes) {
                val bounds = shapes.map { it.getBounds() }
                floatArrayOf(bounds.minOf { it.left }, bounds.minOf { it.top }, bounds.maxOf { it.right }, bounds.maxOf { it.bottom })
            }
            val width = max(48f, (box[2] - box[0]) * unitDp).dp
            val height = max(48f, (box[3] - box[1]) * unitDp).dp
            val centerX = (box[0] + box[2]) / 2f * unitDp
            val centerY = (box[1] + box[3]) / 2f * unitDp
            val value = zoneValues[pillar]?.value
            val label = ringAdjustPillarLabel(pillar)
            val isSelected = selectedKey == pillar
            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            ((centerX - width.value / 2f) * localDensity.density).roundToInt(),
                            ((centerY - height.value / 2f) * localDensity.density).roundToInt(),
                        )
                    }
                    .size(width, height)
                    .testTag("ring-adjust-zone-${pillar}")
                    .semantics {
                        contentDescription = when {
                            value == null -> "$label, sin datos"
                            pillar in estimatedKeys -> "$label, estimación ${ringAdjustPercent(value)}"
                            else -> "$label ${ringAdjustPercent(value)}"
                        }
                        role = Role.Button
                        selected = isSelected
                        onClick(label = null) {
                            currentOnSelect(pillar)
                            true
                        }
                    },
            )
        }
    }
}

/** Pinta la silueta y las zonas de [view]; todo está en las unidades del dibujo (220 × 360) y se escala al lienzo. */
private fun DrawScope.drawBody(
    paths: BodyPaths,
    view: RingAdjustBodyView,
    zoneValues: Map<String, MuscleEntry>,
    zoneColors: Map<String, Color>,
    selectedKey: String,
    highlightedKeys: Set<String>,
    seconds: Float,
) {
    val factor = size.height / RingAdjustBodyHeight
    scale(factor, factor, pivot = Offset.Zero) {
        // Contorno primero (más ancho) y relleno de todas las piezas encima: solo queda el borde exterior de la figura.
        paths.silhouette.forEach { drawPath(it, BodyEdge, style = Stroke(width = 1f)) }
        val fill = Brush.verticalGradient(listOf(BodyTop, BodyBottom), startY = 0f, endY = RingAdjustBodyHeight)
        paths.silhouette.forEach { drawPath(it, brush = fill) }
        // La línea media, muy tenue: el esternón por delante y la columna por detrás.
        val mid = RingAdjustBodyWidth / 2f
        if (view == RingAdjustBodyView.FRONT) {
            drawLine(Color.White.copy(alpha = 0.07f), Offset(mid, 76f), Offset(mid, 176f), strokeWidth = 1f)
        } else {
            drawLine(Color.White.copy(alpha = 0.07f), Offset(mid, 66f), Offset(mid, 214f), strokeWidth = 1f)
        }
        // El latido de la zona elegida: la opacidad sube y baja entre 0,6 y 1 cada 1,4 s (quieta si no corre el reloj).
        val pulse = if (seconds > 0f) 0.8f + 0.2f * sin(2f * PI.toFloat() * seconds / 1.4f) else 1f
        val dash = PathEffect.dashPathEffect(floatArrayOf(3f, 3f))
        clipPath(paths.silhouette.first()) {
        paths.of(view).forEach { (pillar, shapes) ->
            val tone = zoneColors[pillar] ?: Color.Transparent
            val known = zoneValues[pillar] != null
            val isSelected = pillar == selectedKey
            val isToday = pillar in highlightedKeys
            shapes.forEach { shape ->
                if (!known) {
                    drawPath(shape, Color.White.copy(alpha = 0.16f), style = Stroke(width = 1f, pathEffect = dash))
                    if (isSelected) drawPath(shape, Ink.copy(alpha = 0.9f * pulse), style = Stroke(width = 1.8f))
                    return@forEach
                }
                drawPath(shape, tone.copy(alpha = if (isSelected) 0.85f * pulse else 0.45f))
                when {
                    isSelected -> drawPath(shape, Ink.copy(alpha = pulse), style = Stroke(width = 1.8f))
                    isToday -> drawPath(shape, Ink.copy(alpha = 0.6f), style = Stroke(width = 1.2f))
                    else -> drawPath(shape, tone.copy(alpha = 0.5f), style = Stroke(width = 0.9f))
                }
            }
        }
        (if (view == RingAdjustBodyView.FRONT) paths.frontDetails else paths.backDetails).forEach {
            drawPath(it, Ink.copy(alpha = 0.24f), style = Stroke(width = 0.55f, cap = StrokeCap.Round))
        }
        }
    }
}

/**
 * La zona elegida: mini-anillo, «Cuádriceps», «54 % · cargado» (y «antes 66 %» debajo si cambió) y los botones − y + de 10. Arrastrar en
 * horizontal sobre la fila afina el valor de uno en uno. Con la letra grande los botones bajan a su propia línea para que los textos no
 * se partan.
 */
@Composable
private fun SelectionRow(
    label: String,
    value: Int?,
    before: Int?,
    boxed: Boolean,
    currentValue: () -> Int,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    onFine: (Int) -> Unit,
    onCommit: (Int) -> Unit,
    estimated: Boolean,
    onCancel: () -> Unit,
) {
    val localDensity = LocalDensity.current
    val currentOnFine by rememberUpdatedState(onFine)
    val currentOnCommit by rememberUpdatedState(onCommit)
    val currentOnCancel by rememberUpdatedState(onCancel)
    val latestCurrent by rememberUpdatedState(currentValue)
    val stacked = localDensity.fontScale > StackedFontScale
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (boxed) Modifier.background(KpknSurface.inset, shape).padding(horizontal = 12.dp, vertical = 8.dp) else Modifier)
            .testTag("ring-adjust-selection")
            .semantics {
                contentDescription = "Ajuste de $label"
                stateDescription = if (value == null) "Sin datos" else if (estimated) "Estimación ${ringAdjustPercent(value)}" else ringAdjustPercent(value)
                progressBarRangeInfo = ProgressBarRangeInfo((value ?: RingAdjustMax).toFloat(), 0f..100f, 99)
                setProgress { target ->
                    val next = ringAdjustClamp(target.roundToInt())
                    currentOnFine(next)
                    currentOnCommit(next)
                    true
                }
            }
            .pointerInput(Unit) {
                var startValue = 0
                var dragged = 0f
                var lastValue = latestCurrent()
                val pxPerPoint = RingAdjustFineDragDpPerPoint * localDensity.density
                detectHorizontalDragGestures(
                    onDragStart = {
                        startValue = latestCurrent()
                        dragged = 0f
                        lastValue = startValue
                    },
                    onDragEnd = { currentOnCommit(lastValue) },
                    onDragCancel = { currentOnCancel() },
                    onHorizontalDrag = { change, amount ->
                        change.consume()
                        dragged += amount
                        lastValue = ringAdjustFineValue(startValue, dragged, pxPerPoint)
                        currentOnFine(lastValue)
                    },
                )
            },
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MiniRing(value = value, modifier = Modifier.size(46.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = KpknType.bodyStrong,
                    color = WizardColors.text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("ring-adjust-selection-name"),
                )
                Text(
                    text = if (value == null) "Sin datos" else "${if (estimated) "Estimación " else ""}${ringAdjustPercent(value)} · ${ringAdjustMuscleTone(value).word}",
                    style = KpknType.caption,
                    color = WizardColors.textMuted,
                    modifier = Modifier.testTag("ring-adjust-selection-detail"),
                )
                if (value != null && before != null && before != value) {
                    Text(
                        text = "antes ${ringAdjustPercent(before)}",
                        style = KpknType.caption,
                        color = WizardColors.textFaint,
                        modifier = Modifier.testTag("ring-adjust-selection-before"),
                    )
                }
            }
            if (!stacked) {
                StepButton(KpknLineIcons.Minus, "Bajar $RingAdjustMuscleStep", "ring-adjust-muscle-minus", onMinus)
                StepButton(KpknLineIcons.Plus, "Subir $RingAdjustMuscleStep", "ring-adjust-muscle-plus", onPlus)
            }
        }
        if (stacked) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StepButton(KpknLineIcons.Minus, "Bajar $RingAdjustMuscleStep", "ring-adjust-muscle-minus", onMinus)
                StepButton(KpknLineIcons.Plus, "Subir $RingAdjustMuscleStep", "ring-adjust-muscle-plus", onPlus)
            }
        }
    }
}

/** A partir de esta escala de letra los botones − y + bajan a su línea. */
private const val StackedFontScale = 1.15f

/** Botón − o + de la fila: círculo de 40 dp (relleno oscuro y filete) con una zona táctil de 48 dp. */
@Composable
private fun StepButton(icon: ImageVector, description: String, tag: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .testTag(tag)
            .semantics { contentDescription = description }
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(StepFill, CircleShape)
                .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = WizardColors.text)
        }
    }
}

/** Mini-anillo de la zona elegida: pista y arco del tono del valor con extremos redondeados; sin dato, la pista discontinua. */
@Composable
private fun MiniRing(value: Int?, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val stroke = 7f / 62f * size.minDimension
        val inset = stroke / 2f
        val arcSize = Size(size.width - stroke, size.height - stroke)
        val topLeft = Offset(inset, inset)
        if (value == null) {
            val dash = PathEffect.dashPathEffect(floatArrayOf(stroke * 0.6f, stroke * 0.5f))
            drawArc(SelectionTrack, -90f, 360f, false, topLeft, arcSize, style = Stroke(width = stroke, pathEffect = dash))
            return@Canvas
        }
        drawArc(SelectionTrack, -90f, 360f, false, topLeft, arcSize, style = Stroke(width = stroke))
        val sweep = 360f * value / RingAdjustMax
        if (sweep > 0f) {
            drawArc(
                if (value >= 80) Color(0xFF8FB2FF) else Color(0xFFF49A6E),
                -90f,
                sweep,
                false,
                topLeft,
                arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
    }
}
