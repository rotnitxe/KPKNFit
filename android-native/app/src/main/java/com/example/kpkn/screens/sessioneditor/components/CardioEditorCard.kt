package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.CardioBlockType
import com.example.kpkn.data.models.CardioCatalog
import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.CardioFeaturedTemplates
import com.example.kpkn.data.models.CardioIntensity
import com.example.kpkn.data.models.HiitWorkTarget
import com.example.kpkn.domain.cardio.CardioAuthoringShape
import com.example.kpkn.domain.cardio.CardioIntervalProgramBuilder
import com.example.kpkn.domain.cardio.CardioPrescriptionFormatter
import com.example.kpkn.domain.cardio.CardioRepeatGrammar
import com.example.kpkn.domain.cardio.CardioTypeHistory
import com.example.kpkn.domain.cardio.CardioUniformRepeat
import com.example.kpkn.domain.cardio.CardioValueKind
import com.example.kpkn.domain.cardio.CardioZones
import com.example.kpkn.domain.workout.CardioProgressionEngine
import com.example.kpkn.domain.workout.CardioProgressionInput
import com.example.kpkn.screens.workout.CardioZoneColors
import com.example.kpkn.ui.design.motion.KpknAnimation
import com.example.kpkn.ui.design.motion.KpknDirection
import com.example.kpkn.ui.design.motion.KpknDuration
import com.example.kpkn.ui.design.motion.KpknFill
import com.example.kpkn.ui.design.motion.KpknMotion
import com.example.kpkn.ui.design.motion.kpknAnimate
import com.example.kpkn.ui.design.motion.kpknKeyframes
import kotlin.math.roundToInt

/*
 * Pulido v1 · CARDIO DEL EDITOR · la tarjeta de un cardio abierto (puntos 6, 7 y 11). Es el cuerpo de `ceBody` de `scenes-ce.js`: Forma →
 * magnitudes → esfuerzo → objetivos → accesos, en una sola columna de 18 dp. Sin la dosis repetida en grande, sin el chip «Al final», sin el
 * carrusel de plantillas (van en la hoja «Plantillas») y sin el panel «Avanzado» (ritmo / FC / vatios / velocidad / inclinación / RPM sueltos,
 * el chip HIIT/SIT y la distancia dentro de Series: el motor no los usaba o los pisaba; los campos del modelo siguen en los datos).
 * Su firma es la de siempre menos lo del chip: la usan el editor de la sesión y el cajón «Editar series» de la sesión en vivo.
 */

/** `ce-in`: opacidad 0 → 1 y 8 dp de subida (familia `entrada-sube` con el valor exacto de la fila: 260 ms, sin relleno). */
private val CeInKeyframes = kpknKeyframes(
    "ce-in",
    "@keyframes ce-in { from { opacity: 0; transform: translateY(8px); } to { opacity: 1; transform: none; } }",
) {
    at(0f) { alpha(0f); translateY(8f) }
    at(1f) { alpha(1f); transformNone() }
}

/** La entrada del cuerpo de la tarjeta (`.ce-bd`, ce-A01): 0,26 s con `estandar`. */
internal val CardioBodyEnter = KpknAnimation(CeInKeyframes, KpknDuration(260), KpknMotion.Estandar, KpknDuration(0), 1f, KpknDirection.Normal, KpknFill.None)

/** Lo que la tarjeta tiene abierto encima: un selector, la zona de pulso o una de sus hojas. */
private sealed interface CardLayer {
    class Value(val request: CardioValueRequest) : CardLayer
    class Zone(val current: Int, val apply: (Int) -> Unit) : CardLayer
    data object Protocol : CardLayer
    data object Avisos : CardLayer
    data object Templates : CardLayer
    data object BlockMenu : CardLayer
}

@Composable
internal fun CardioEditorCard(
    details: CardioDetails,
    @Suppress("UNUSED_PARAMETER") accentColor: Color = CeColors.col,
    @Suppress("UNUSED_PARAMETER") exerciseName: String? = null,
    onChange: (CardioDetails) -> Unit,
    history: CardioTypeHistory? = null,
    /** Bloque que el inspector de «Intervalos» tiene elegido al abrir (el lienzo abre en el segundo). */
    initialBlockIndex: Int = 0,
) {
    val shape = remember(details) { CardioRepeatGrammar.shape(details) }
    val form = remember(details) { cardioFormOf(details) }
    val catalog = remember(details.type) { CardioCatalog.findByType(details.type) }
    val typeName = catalog?.name ?: CardioPrescriptionFormatter.typeLabel(details.type)
    var layer by remember { mutableStateOf<CardLayer?>(null) }
    var selectedBlock by remember { mutableIntStateOf(initialBlockIndex) }

    fun openValue(request: CardioValueRequest) { layer = CardLayer.Value(request) }
    fun openZone(current: Int, apply: (Int) -> Unit) { layer = CardLayer.Zone(current, apply) }

    Column(
        // ce-A01 `.ce-bd` entrada 0,26 s `estandar` (opacidad y 8 dp de subida) al abrir la tarjeta
        modifier = Modifier.fillMaxWidth().kpknAnimate(CardioBodyEnter),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        // La forma: el rótulo con «Plantillas» a su lado y el segmentado. El rótulo se pega 8 dp al segmentado (margin-bottom: -8px).
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                CeEyebrow("Forma")
                CeLink("Plantillas", onClick = { layer = CardLayer.Templates })
            }
            CeSegmented(
                options = listOf("Continuo", "Series", "Intervalos"),
                selected = form.ordinal,
                onSelect = { index ->
                    switchCardioForm(details, CardioForm.entries[index])?.let { next ->
                        selectedBlock = 0
                        onChange(next)
                    }
                },
            )
        }

        when (shape) {
            is CardioAuthoringShape.Steady -> SteadyForm(
                details = details,
                supportsPace = catalog?.supportsPace == true,
                typeName = typeName,
                onChange = onChange,
                onOpenValue = ::openValue,
                onOpenZone = ::openZone,
                onOpenAvisos = { layer = CardLayer.Avisos },
            )
            is CardioAuthoringShape.Uniform -> SeriesForm(
                details = details,
                repeat = shape.repeat,
                typeName = typeName,
                onChange = onChange,
                onOpenValue = ::openValue,
                onOpenProtocol = { layer = CardLayer.Protocol },
                onOpenAvisos = { layer = CardLayer.Avisos },
            )
            is CardioAuthoringShape.Irregular -> {
                CardioIntervalsSection(
                    details = details,
                    selected = selectedBlock,
                    onSelect = { selectedBlock = it },
                    onChange = onChange,
                    onOpenValue = ::openValue,
                    onOpenZone = ::openZone,
                    onOpenBlockMenu = { layer = CardLayer.BlockMenu },
                    typeName = typeName,
                )
                Column {
                    CeLinkRow(CeIcons.Sun, "Avisos y pantalla", cardioAvisosSummary(details), onClick = { layer = CardLayer.Avisos }, topRule = true)
                }
            }
        }

        history?.takeUnless { it.isEmpty }?.let { stats -> HistoryPanel(details, stats, onChange) }
    }

    when (val current = layer) {
        null -> Unit
        is CardLayer.Value -> {
            val request = current.request
            CardioValueSheet(
                kind = request.kind,
                subtitle = request.subtitle,
                initialN = request.n,
                initialMode = request.mode,
                onApply = { n, mode ->
                    layer = null
                    request.apply(n, mode)
                },
                onDismiss = { layer = null },
            )
        }
        is CardLayer.Zone -> CardioZoneSheet(
            typeName = typeName,
            initialZone = current.current,
            onApply = { zone ->
                layer = null
                current.apply(zone)
            },
            onDismiss = { layer = null },
        )
        CardLayer.Protocol -> {
            val repeat = (shape as? CardioAuthoringShape.Uniform)?.repeat
            if (repeat != null) {
                CardioProtocolSheet(
                    typeName = typeName,
                    dose = cardioDoseLine(details),
                    repeat = repeat,
                    onChange = { onChange(CardioRepeatGrammar.applyUniform(details, it)) },
                    onOpenValue = { kind ->
                        layer = null
                        openValue(protocolRequest(kind, details, repeat, typeName, onChange))
                    },
                    onDismiss = { layer = null },
                )
            }
        }
        CardLayer.Avisos -> CardioAvisosSheet(typeName = typeName, details = details, onChange = onChange, onDismiss = { layer = null })
        CardLayer.Templates -> CardioTemplatesSheet(
            details = details,
            onPickTemplate = { id ->
                layer = null
                selectedBlock = 0
                onChange(CardioFeaturedTemplates.apply(id, details))
                CardioMessages.post("Plantilla aplicada: ${CardioFeaturedTemplates.all.firstOrNull { it.id == id }?.name.orEmpty()}.")
            },
            onPickPattern = { spec ->
                layer = null
                selectedBlock = 0
                CardioMessages.post("Patrón aplicado: ${spec.label}.")
                onChange(
                    CardioIntervalProgramBuilder.buildDetails(
                        pattern = spec.pattern,
                        totalSeconds = details.effectiveDurationSeconds().coerceAtLeast(20 * 60),
                        type = details.type,
                        baseLevel = details.resolvedIntensityLevel(),
                        base = details.copy(hiit = null),
                    ),
                )
            },
            onDismiss = { layer = null },
        )
        CardLayer.BlockMenu -> CardioBlockMenuSheet(
            typeName = typeName,
            details = details,
            selected = selectedBlock,
            onChange = { updated, newSelected ->
                layer = null
                selectedBlock = newSelected
                onChange(updated)
            },
            onDismiss = { layer = null },
        )
    }
}

/** El selector de un objetivo de la hoja «Protocolo» (descanso entre series, kcal o metros por esfuerzo). */
private fun protocolRequest(
    kind: CardioValueKind,
    details: CardioDetails,
    repeat: CardioUniformRepeat,
    typeName: String,
    onChange: (CardioDetails) -> Unit,
): CardioValueRequest = when (kind) {
    CardioValueKind.REST_BETWEEN_SETS -> CardioValueRequest(kind, kind.nOf(repeat.restBetweenSetsSeconds).coerceIn(kind.min, kind.max), typeName) { n, _ ->
        onChange(CardioRepeatGrammar.applyUniform(details, repeat.copy(restBetweenSetsSeconds = kind.valueOf(n))))
    }
    CardioValueKind.WORK_KCAL -> CardioValueRequest(kind, (repeat.workTargetValue?.toInt() ?: 12).coerceIn(kind.min, kind.max), "$typeName · cada esfuerzo") { n, _ ->
        onChange(CardioRepeatGrammar.applyUniform(details, repeat.copy(workTargetType = if (n == 0) HiitWorkTarget.TIME else HiitWorkTarget.KCAL, workTargetValue = n.takeIf { it > 0 }?.toDouble())))
    }
    else -> CardioValueRequest(kind, ((repeat.workTargetValue?.toInt() ?: 200) / 50).coerceIn(kind.min, kind.max), "$typeName · cada esfuerzo") { n, _ ->
        onChange(CardioRepeatGrammar.applyUniform(details, repeat.copy(workTargetType = if (n == 0) HiitWorkTarget.TIME else HiitWorkTarget.DISTANCE, workTargetValue = n.takeIf { it > 0 }?.let { kind.valueOf(it).toDouble() })))
    }
}

// ───────────────────────────── Continuo ─────────────────────────────

/** Continuo: duración y distancia en celdas, GPS en las modalidades de exterior, esfuerzo, objetivos (ritmo y zona) y el acceso a avisos. */
@Composable
private fun SteadyForm(
    details: CardioDetails,
    supportsPace: Boolean,
    typeName: String,
    onChange: (CardioDetails) -> Unit,
    onOpenValue: (CardioValueRequest) -> Unit,
    onOpenZone: (Int, (Int) -> Unit) -> Unit,
    onOpenAvisos: () -> Unit,
) {
    val distance = details.targetDistanceKm?.takeIf { it > 0.0 }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val (timeValue, timeUnit) = durationCellParts(details.targetDurationSeconds)
            CeCell(
                label = "Duración",
                value = timeValue,
                unit = timeUnit,
                dim = distance != null,
                modifier = Modifier.weight(1f),
                onClick = {
                    val kind = CardioValueKind.DURATION
                    onOpenValue(CardioValueRequest(kind, ((details.targetDurationSeconds ?: 0) / 60).coerceIn(kind.min, kind.max), typeName) { n, _ ->
                        onChange(details.copy(targetDurationSeconds = n.takeIf { it > 0 }?.let { it * 60 }))
                    })
                },
            )
            if (details.supportsDistance) {
                val (distanceValue, distanceUnit) = distanceCellParts(details.targetDistanceKm?.takeIf { it > 0.0 })
                CeCell(
                    label = "Distancia",
                    value = distanceValue,
                    unit = distanceUnit,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        val kind = CardioValueKind.DISTANCE
                        onOpenValue(CardioValueRequest(kind, Math.round((details.targetDistanceKm ?: 0.0) * 10).toInt().coerceIn(kind.min, kind.max), typeName) { n, _ ->
                            onChange(details.copy(targetDistanceKm = n.takeIf { it > 0 }?.let { it / 10.0 }))
                        })
                    },
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
        }
        if (distance != null) CeHint("Con distancia, manda la distancia: el tiempo no se lee.")
    }
    if (cardioShowsGpsCell(details)) {
        CeSwitchRow(
            icon = CeIcons.Gps,
            title = "Seguir con GPS",
            sub = if (details.requiresGps) "Distancia y ritmo en vivo." else "Sin GPS: distancia estimada.",
            on = details.requiresGps,
            onToggle = { onChange(details.copy(requiresGps = !details.requiresGps)) },
            topRule = true,
        )
    }
    EffortMeter(details, onChange)
    Column(modifier = Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            CeEyebrow("Objetivos")
            Text("opcional", modifier = Modifier.padding(start = 6.dp), style = edText(10.5f, FontWeight.Medium, EdColors.dim, 12.6f))
        }
        Column {
            var first = true
            if (supportsPace) {
                val pace = details.targetPaceSecondsPerKm
                CeRow(topRule = true, controlRow = true, onClick = {
                    val kind = CardioValueKind.PACE
                    onOpenValue(CardioValueRequest(kind, pace ?: 360, typeName) { n, _ ->
                        onChange(details.copy(targetPaceSecondsPerKm = n.takeIf { it > 0 }))
                    })
                }) {
                    CeRowLabel("Ritmo")
                    CeValue(if (pace != null) "${CardioValueKind.mmss(pace)} /km" else "—", onClick = {
                        val kind = CardioValueKind.PACE
                        onOpenValue(CardioValueRequest(kind, pace ?: 360, typeName) { n, _ ->
                            onChange(details.copy(targetPaceSecondsPerKm = n.takeIf { it > 0 }))
                        })
                    })
                }
                first = false
            }
            val zone = details.targetHrPercent?.let(CardioZones::zoneOfPercent)
            val openZone = {
                onOpenZone(zone ?: 0) { z -> onChange(details.copy(targetHrPercent = z.takeIf { it > 0 }?.let(CardioZones::midpointPercent))) }
            }
            CeRow(topRule = first, controlRow = true, onClick = openZone) {
                CeRowLabel("Zona de pulso")
                CeValue(if (zone != null) "Z$zone" else "—", onClick = openZone, dot = zone?.let { CardioZoneColors[it - 1] })
            }
        }
    }
    Column {
        CeLinkRow(CeIcons.Sun, "Avisos y pantalla", cardioAvisosSummary(details), onClick = onOpenAvisos, topRule = true)
    }
}

/** El esfuerzo del cardio: el RPE de la dosis (o el del protocolo por series) en diez barras con las zonas reales. */
@Composable
private fun EffortMeter(details: CardioDetails, onChange: (CardioDetails) -> Unit) {
    val rpe = details.resolvedRpe().toInt().coerceIn(1, 10)
    CeRpeMeter(rpe = rpe, onChange = { level ->
        val updated = when (val current = CardioRepeatGrammar.shape(details)) {
            is CardioAuthoringShape.Uniform -> CardioRepeatGrammar.applyUniform(details, current.repeat.copy(targetRpe = level.toDouble()))
            else -> details.copy(
                intensityLevel = level,
                intensity = CardioIntensity.fromLevel(level),
                hiit = details.hiit?.copy(targetRpe = level.toDouble()),
            )
        }
        if (updated != details) onChange(updated)
    })
}

// ───────────────────────────── Series ─────────────────────────────

/** `mv` del lienzo: «Libre», «Omitir» o la cifra del calentamiento y el enfriamiento. */
internal fun cardioPhaseText(seconds: Int, open: Boolean): String = when {
    open -> "Libre"
    seconds <= 0 -> "Omitir"
    else -> durationCellParts(seconds).let { (value, unit) -> if (unit != null) "$value $unit" else value }
}

/** Series: la gráfica con el total y cinco filas (calentamiento, esfuerzo, pausa, rondas, enfriamiento), el esfuerzo y los accesos. */
@Composable
private fun SeriesForm(
    details: CardioDetails,
    repeat: CardioUniformRepeat,
    typeName: String,
    onChange: (CardioDetails) -> Unit,
    onOpenValue: (CardioValueRequest) -> Unit,
    onOpenProtocol: () -> Unit,
    onOpenAvisos: () -> Unit,
) {
    fun apply(updated: CardioUniformRepeat) = onChange(CardioRepeatGrammar.applyUniform(details, updated))
    val efforts = repeat.rounds * repeat.sets

    Column {
        CeBlockChart(cardioBars(details), height = 60.dp)
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = EdColors.ink, fontWeight = FontWeight.SemiBold)) {
                    append(CardioPrescriptionFormatter.formatDuration(details.totalIntervalSeconds()))
                }
                append(" en total · $efforts ${if (efforts == 1) "esfuerzo" else "esfuerzos"}")
            },
            modifier = Modifier.padding(top = 10.dp),
            style = edText(13f, FontWeight.Normal, EdColors.muted, 18.2f, true),
        )
    }

    // Calentamiento y enfriamiento tienen tres modos (con duración, libre, omitir); el resto es un valor.
    fun phaseRequest(kind: CardioValueKind, seconds: Int, open: Boolean, write: (Int, Boolean) -> CardioUniformRepeat) {
        val mode = when {
            open -> CardioValueMode.FREE
            seconds <= 0 -> CardioValueMode.OFF
            else -> CardioValueMode.VALUE
        }
        onOpenValue(CardioValueRequest(kind, kind.nOf(seconds).coerceIn(kind.min, kind.max), typeName, mode) { n, m ->
            apply(
                when {
                    m == CardioValueMode.FREE -> write(0, true)
                    m == CardioValueMode.OFF || n == 0 -> write(0, false)
                    else -> write(kind.valueOf(n), false)
                },
            )
        })
    }
    fun valueRequest(kind: CardioValueKind, seconds: Int, write: (Int) -> CardioUniformRepeat) {
        onOpenValue(CardioValueRequest(kind, kind.nOf(seconds).coerceIn(kind.min, kind.max), typeName) { n, _ -> apply(write(kind.valueOf(n))) })
    }

    Column {
        ValueRow("Calentamiento", cardioPhaseText(repeat.warmupSeconds, repeat.warmupOpen), top = true) {
            phaseRequest(CardioValueKind.WARMUP, repeat.warmupSeconds, repeat.warmupOpen) { s, open -> repeat.copy(warmupSeconds = s, warmupOpen = open) }
        }
        ValueRow("Esfuerzo", CardioValueKind.mmss(repeat.workSeconds)) {
            valueRequest(CardioValueKind.WORK, repeat.workSeconds) { repeat.copy(workSeconds = it.coerceAtLeast(5)) }
        }
        ValueRow("Pausa", if (repeat.restSeconds > 0) CardioValueKind.mmss(repeat.restSeconds) else "Sin pausa") {
            valueRequest(CardioValueKind.REST, repeat.restSeconds) { repeat.copy(restSeconds = it) }
        }
        CeRow(controlRow = true) {
            CeRowLabel("Rondas")
            CeStepper(
                value = repeat.rounds,
                onMinus = { apply(repeat.copy(rounds = (repeat.rounds - 1).coerceAtLeast(1))) },
                onPlus = { apply(repeat.copy(rounds = (repeat.rounds + 1).coerceAtMost(99))) },
                canMinus = repeat.rounds > 1,
                canPlus = repeat.rounds < 99,
                label = "rondas",
            )
        }
        ValueRow("Enfriamiento", cardioPhaseText(repeat.cooldownSeconds, repeat.cooldownOpen)) {
            phaseRequest(CardioValueKind.COOLDOWN, repeat.cooldownSeconds, repeat.cooldownOpen) { s, open -> repeat.copy(cooldownSeconds = s, cooldownOpen = open) }
        }
    }

    EffortMeter(details, onChange)

    val target = when (repeat.workTargetType) {
        HiitWorkTarget.TIME -> ""
        HiitWorkTarget.KCAL -> " · por kcal"
        HiitWorkTarget.DISTANCE -> " · por metros"
    }
    Column {
        CeLinkRow(
            CeIcons.Sliders,
            "Protocolo",
            "${repeat.sets} ${if (repeat.sets == 1) "serie" else "series"} · pausa ${if (repeat.restNature == com.example.kpkn.data.models.HiitRestNature.ACTIVE) "activa" else "pasiva"}$target",
            onClick = onOpenProtocol,
            topRule = true,
        )
        CeLinkRow(CeIcons.Sun, "Avisos y pantalla", cardioAvisosSummary(details), onClick = onOpenAvisos)
    }
}

/** `ceValRow`: una fila con su rótulo y el valor en un campo que abre el selector. */
@Composable
private fun ValueRow(label: String, value: String, top: Boolean = false, onClick: () -> Unit) {
    CeRow(topRule = top, onClick = onClick, controlRow = true) {
        CeRowLabel(label)
        CeValue(value, onClick)
    }
}

// ───────────────────────────── Historial ─────────────────────────────

/** `.ce-hist`: la última vez y los récords del tipo de cardio, con «Usar última» y «Progresar». */
@Composable
private fun HistoryPanel(details: CardioDetails, stats: CardioTypeHistory, onChange: (CardioDetails) -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    val lastBits = listOfNotNull(
        stats.lastDurationSeconds?.let { CardioPrescriptionFormatter.formatDuration(it) },
        stats.lastDistanceKm?.let { "${cellNumber(it)} km" },
        stats.lastPaceSecondsPerKm?.let { CardioPrescriptionFormatter.formatPace(it) },
        stats.lastRpe?.let { "RPE ${it.toInt()}" },
    ).joinToString(" · ")
    val prBits = listOfNotNull(
        stats.bestPaceSecondsPerKm?.let { "Ritmo ${CardioPrescriptionFormatter.formatPace(it)}" },
        stats.longestDistanceKm?.let { "${cellNumber(it)} km" },
        stats.longestTimeSeconds?.let { CardioPrescriptionFormatter.formatDuration(it) },
    ).joinToString(" · ")
    Column(
        modifier = Modifier.fillMaxWidth().clip(shape).background(CeColors.cell, shape).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        CeEyebrow("Historial")
        if (lastBits.isNotBlank()) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = EdColors.ink, fontWeight = FontWeight.SemiBold)) { append("Última vez") }
                    append(" · $lastBits")
                },
                style = edText(13f, FontWeight.Normal, EdColors.muted, 18.9f, true),
            )
        }
        if (prBits.isNotBlank()) Text("Récords · $prBits", style = edText(13f, FontWeight.Normal, EdColors.muted, 18.9f, true))
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CePill("Usar última", onClick = {
                CardioMessages.post("Aplicado lo de la última vez.")
                onChange(
                    details.copy(
                        targetDurationSeconds = stats.lastDurationSeconds ?: details.targetDurationSeconds,
                        targetDistanceKm = stats.lastDistanceKm ?: details.targetDistanceKm,
                        intensityLevel = stats.lastRpe?.roundToInt() ?: details.intensityLevel,
                    ),
                )
            })
            CePill("Progresar", onClick = {
                CardioMessages.post("Progresión aplicada.")
                onChange(progressedCardio(details, stats))
            })
        }
    }
}

/** «Progresar»: la sugerencia del motor de progresión aplicada a la forma que tiene el cardio (una ronda más en series, más tiempo en continuo). */
internal fun progressedCardio(details: CardioDetails, stats: CardioTypeHistory): CardioDetails {
    val suggestion = CardioProgressionEngine.suggest(
        CardioProgressionInput(
            durationSeconds = stats.lastDurationSeconds ?: details.effectiveDurationSeconds(),
            distanceKm = stats.lastDistanceKm ?: details.targetDistanceKm,
            intensity = details.intensity,
            rpe = stats.lastRpe ?: details.resolvedRpe(),
        ),
    )
    return when (val current = CardioRepeatGrammar.shape(details)) {
        is CardioAuthoringShape.Uniform -> {
            val extraRound = if (suggestion.durationSeconds > details.effectiveDurationSeconds()) 1 else 0
            CardioRepeatGrammar.applyUniform(details, current.repeat.copy(rounds = (current.repeat.rounds + extraRound).coerceIn(1, 99)))
        }
        else -> details.copy(
            targetDurationSeconds = suggestion.durationSeconds,
            targetDistanceKm = suggestion.distanceKm,
            intensity = suggestion.intensity,
            intensityLevel = suggestion.intensity.defaultRpe.roundToInt().coerceIn(1, 10),
        )
    }
}

/**
 * La celda GPS aparece solo en los cardios al aire libre, los que admiten GPS en el catálogo (ER2b). Un cardio de interior que ya tuviera
 * GPS puesto conserva la fila, para poder quitarlo.
 */
internal fun cardioShowsGpsCell(details: CardioDetails): Boolean =
    details.requiresGps || CardioCatalog.items.any { it.type == details.type && it.requiresGps }
