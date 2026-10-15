package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.CardioBlockType
import com.example.kpkn.data.models.CardioCatalog
import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.CardioFeaturedTemplate
import com.example.kpkn.data.models.CardioFeaturedTemplates
import com.example.kpkn.data.models.CardioHiitConfig
import com.example.kpkn.data.models.CardioHiitTemplates
import com.example.kpkn.data.models.CardioIntensity
import com.example.kpkn.data.models.HiitProtocol
import com.example.kpkn.data.models.HiitRestNature
import com.example.kpkn.data.models.HiitWorkTarget
import com.example.kpkn.screens.sessioneditor.design.sessionLazyRowEdgeFade
import com.example.kpkn.domain.cardio.CardioAuthoringShape
import com.example.kpkn.domain.cardio.CardioPaceBandEngine
import com.example.kpkn.domain.cardio.CardioPrescriptionFormatter
import com.example.kpkn.domain.cardio.CardioRepeatGrammar
import com.example.kpkn.domain.cardio.CardioTypeHistory
import com.example.kpkn.domain.cardio.CardioUniformRepeat
import com.example.kpkn.domain.workout.CardioProgressionEngine
import com.example.kpkn.domain.workout.CardioProgressionInput
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.workout.CardioIntervalChart
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknChip
import com.example.kpkn.ui.design.KpknSegmentedControl
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknType
import java.util.Locale
import kotlin.math.roundToInt

private enum class CardioEditorPicker {
    STEADY_MINUTES,
    WARMUP,
    WORK,
    REST,
    COOLDOWN,
    DISTANCE,
    PACE,
    HR,
    WATTS,
    SPEED,
    INCLINE,
    RPM,
    SETS_REST,
    WORK_KCAL,
    WORK_METERS,
}

/**
 * Editor de un cardio, en el orden del original: la dosis en grande y la gráfica, el historial y las plantillas, la forma
 * (Continuo · Series · Intervalos), la intensidad (RPE) y «Avanzado». Su firma no cambia: la usan el editor de la sesión y el cajón
 * «Editar series» de la sesión en vivo. Va dentro de un contenedor de la pantalla, así que sus bloques son `inset` y no módulos.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CardioEditorCard(
    details: CardioDetails,
    accentColor: Color,
    exerciseName: String? = null,
    onChange: (CardioDetails) -> Unit,
    cardioFirst: Boolean = false,
    showPlacementChip: Boolean = false,
    onTogglePlacement: (() -> Unit)? = null,
    history: CardioTypeHistory? = null,
) {
    val shape = remember(details) { CardioRepeatGrammar.shape(details) }
    val form = remember(details) { cardioFormOf(details) }
    val catalog = remember(details.type) { CardioCatalog.findByType(details.type) }
    val rpe = details.resolvedRpe().toInt().coerceIn(1, 10)
    val dose = remember(details) { cardioDoseLine(details) }
    var picker by remember { mutableStateOf<CardioEditorPicker?>(null) }
    var showBlocks by remember { mutableStateOf(shape is CardioAuthoringShape.Irregular) }
    var showAdvanced by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        // La prescripción, en grande: el tipo, la dosis en Syne y la gráfica a todo el ancho.
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = CardioPrescriptionFormatter.typeLabel(details.type).uppercase(Locale.forLanguageTag("es")),
                style = KpknType.eyebrow,
                color = WizardColors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = dose,
                style = KpknType.title,
                color = WizardColors.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (details.hasIntervals()) {
                CardioIntervalChart(
                    details = details,
                    accentColor = accentColor,
                    compact = true,
                    showLabels = false,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (showPlacementChip && onTogglePlacement != null) {
                KpknChip(
                    text = if (cardioFirst) "Al inicio" else "Al final",
                    selected = true,
                    onClick = onTogglePlacement,
                )
            }
        }

        history?.takeUnless { it.isEmpty }?.let { stats ->
            CardioHistoryPanel(
                history = stats,
                onApplyLast = {
                    onChange(
                        details.copy(
                            targetDurationSeconds = stats.lastDurationSeconds ?: details.targetDurationSeconds,
                            targetDistanceKm = stats.lastDistanceKm ?: details.targetDistanceKm,
                            intensityLevel = stats.lastRpe?.roundToInt() ?: details.intensityLevel,
                        ),
                    )
                },
                onApplyProgression = {
                    val suggestion = CardioProgressionEngine.suggest(
                        CardioProgressionInput(
                            durationSeconds = stats.lastDurationSeconds ?: details.effectiveDurationSeconds(),
                            distanceKm = stats.lastDistanceKm ?: details.targetDistanceKm,
                            intensity = details.intensity,
                            rpe = stats.lastRpe ?: details.resolvedRpe(),
                        ),
                    )
                    val updated = when (val current = CardioRepeatGrammar.shape(details)) {
                        is CardioAuthoringShape.Uniform -> {
                            val extraRound = if (suggestion.durationSeconds > details.effectiveDurationSeconds()) 1 else 0
                            CardioRepeatGrammar.applyUniform(
                                details,
                                current.repeat.copy(rounds = (current.repeat.rounds + extraRound).coerceIn(1, 99)),
                            )
                        }
                        else -> details.copy(
                            targetDurationSeconds = suggestion.durationSeconds,
                            targetDistanceKm = suggestion.distanceKm,
                            intensity = suggestion.intensity,
                            intensityLevel = suggestion.intensity.defaultRpe.roundToInt().coerceIn(1, 10),
                        )
                    }
                    onChange(updated)
                },
            )
        }

        CardioTemplatesRow(details = details, accentColor = accentColor, onChange = onChange)

        // La forma: el control segmentado y, debajo, las filas de su forma.
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("FORMA", style = KpknType.eyebrow, color = WizardColors.textMuted)
            KpknSegmentedControl(
                options = listOf("Continuo", "Series", "Intervalos"),
                selectedIndex = form.ordinal,
                onSelect = { index ->
                    val target = CardioForm.entries[index]
                    switchCardioForm(details, target)?.let { next ->
                        if (target == CardioForm.INTERVALOS) showBlocks = true
                        onChange(next)
                    }
                },
            )
            when (shape) {
                is CardioAuthoringShape.Steady -> CardioSteadyForm(
                    details = details,
                    onChange = onChange,
                    onOpenPicker = { picker = it },
                )
                is CardioAuthoringShape.Uniform -> CardioSeriesForm(
                    details = details,
                    repeat = shape.repeat,
                    accentColor = accentColor,
                    onChange = onChange,
                    onOpenPicker = { picker = it },
                )
                is CardioAuthoringShape.Irregular -> KpknChip(
                    text = if (showBlocks) "Ocultar bloques" else "Ajustar bloques",
                    selected = showBlocks,
                    onClick = { showBlocks = !showBlocks },
                )
            }
        }

        // La intensidad: el RPE 1–10 como deslizador KPKN, con la cifra en Syne y su zona en palabras.
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("RPE", style = KpknType.eyebrow, color = WizardColors.textMuted)
                Text("$rpe", style = KpknType.metricM, color = WizardColors.text)
                Text(
                    "· ${CardioPrescriptionFormatter.rpeAnchor(rpe)}",
                    style = KpknType.bodyStrong,
                    color = WizardColors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Slider(
                value = rpe.toFloat(),
                onValueChange = { value ->
                    val level = value.roundToInt().coerceIn(1, 10)
                    val updated = when (val current = CardioRepeatGrammar.shape(details)) {
                        is CardioAuthoringShape.Uniform -> CardioRepeatGrammar.applyUniform(
                            details,
                            current.repeat.copy(targetRpe = level.toDouble()),
                        )
                        else -> details.copy(
                            intensityLevel = level,
                            intensity = CardioIntensity.fromLevel(level),
                            hiit = details.hiit?.copy(targetRpe = level.toDouble()),
                        )
                    }
                    onChange(updated)
                },
                valueRange = 1f..10f,
                steps = 8,
                colors = SliderDefaults.colors(
                    thumbColor = WizardColors.text,
                    activeTrackColor = WizardColors.text,
                    inactiveTrackColor = Color.White.copy(alpha = 0.08f),
                    activeTickColor = KpknSurface.canvas,
                    inactiveTickColor = Color.White.copy(alpha = 0.24f),
                ),
            )
        }

        if (shape is CardioAuthoringShape.Irregular && showBlocks) {
            CardioIntervalsEditor(
                details = details,
                accentColor = accentColor,
                onChange = onChange,
            )
        }

        // «Avanzado»: un módulo plegado con su flecha.
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
                    .clickable(role = Role.Button) { showAdvanced = !showAdvanced },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Avanzado", style = KpknType.bodyStrong, color = WizardColors.text)
                Icon(
                    imageVector = if (showAdvanced) KpknLineIcons.ChevronUp else KpknLineIcons.ChevronDown,
                    contentDescription = if (showAdvanced) "Ocultar avanzado" else "Mostrar avanzado",
                    tint = WizardColors.text,
                    modifier = Modifier.size(20.dp),
                )
            }
            if (showAdvanced) {
                CardioAdvancedPanel(
                    details = details,
                    shape = shape,
                    catalogSupportsSpeed = catalog?.supportsSpeed == true,
                    catalogSupportsIncline = catalog?.supportsIncline == true,
                    catalogSupportsRpm = catalog?.supportsRpm == true,
                    catalogSupportsWatts = catalog?.supportsWatts == true,
                    accentColor = accentColor,
                    onChange = onChange,
                    onOpenPicker = { picker = it },
                )
            }
        }
    }

    val uniform = (shape as? CardioAuthoringShape.Uniform)?.repeat
    when (picker) {
        CardioEditorPicker.STEADY_MINUTES -> CardioMinutesWheelDialog(
            title = "Duración",
            initialSeconds = details.targetDurationSeconds ?: 20 * 60,
            accentColor = accentColor,
            onDismiss = { picker = null },
            onConfirm = {
                onChange(details.copy(targetDurationSeconds = it.takeIf { seconds -> seconds > 0 }))
                picker = null
            },
        )
        CardioEditorPicker.WARMUP -> if (uniform != null) CardioMinutesSecondsWheelDialog(
            title = "Calentamiento",
            initialSeconds = uniform.warmupSeconds,
            accentColor = accentColor,
            onDismiss = { picker = null },
            onConfirm = {
                onChange(CardioRepeatGrammar.applyUniform(details, uniform.copy(warmupSeconds = it, warmupOpen = false)))
                picker = null
            },
        )
        CardioEditorPicker.WORK -> if (uniform != null) CardioSecondsWheelDialog(
            title = "Esfuerzo",
            initialSeconds = uniform.workSeconds,
            accentColor = accentColor,
            onDismiss = { picker = null },
            onConfirm = {
                onChange(CardioRepeatGrammar.applyUniform(details, uniform.copy(workSeconds = it.coerceAtLeast(5))))
                picker = null
            },
            minSeconds = 5,
            maxSeconds = 600,
        )
        CardioEditorPicker.REST -> if (uniform != null) CardioSecondsWheelDialog(
            title = "Pausa",
            initialSeconds = uniform.restSeconds,
            accentColor = accentColor,
            onDismiss = { picker = null },
            onConfirm = {
                onChange(CardioRepeatGrammar.applyUniform(details, uniform.copy(restSeconds = it)))
                picker = null
            },
            minSeconds = 0,
            maxSeconds = 600,
        )
        CardioEditorPicker.COOLDOWN -> if (uniform != null) CardioMinutesSecondsWheelDialog(
            title = "Enfriamiento",
            initialSeconds = uniform.cooldownSeconds,
            accentColor = accentColor,
            onDismiss = { picker = null },
            onConfirm = {
                onChange(CardioRepeatGrammar.applyUniform(details, uniform.copy(cooldownSeconds = it, cooldownOpen = false)))
                picker = null
            },
        )
        CardioEditorPicker.DISTANCE -> CardioDistanceWheelDialog(
            title = "Distancia",
            initialKm = details.targetDistanceKm,
            accentColor = accentColor,
            onDismiss = { picker = null },
            onConfirm = {
                onChange(details.copy(targetDistanceKm = it))
                picker = null
            },
        )
        CardioEditorPicker.PACE -> CardioPaceWheelDialog(
            title = "Ritmo objetivo",
            initialSecondsPerKm = details.targetPaceSecondsPerKm,
            accentColor = accentColor,
            onDismiss = { picker = null },
            onConfirm = {
                onChange(
                    details.copy(
                        targetPaceSecondsPerKm = it,
                        intervalBlocks = details.intervalBlocks.map { block ->
                            if (block.type == CardioBlockType.WORK) {
                                block.copy(targetPaceSecondsPerKm = it)
                            } else block
                        },
                    ),
                )
                picker = null
            },
        )
        CardioEditorPicker.HR -> CardioIntWheelDialog(
            title = "% FC",
            initial = details.targetHrPercent ?: 75,
            range = 50..100,
            unit = "%",
            accentColor = accentColor,
            onDismiss = { picker = null },
            onConfirm = {
                onChange(
                    details.copy(
                        targetHrPercent = it,
                        intervalBlocks = details.intervalBlocks.map { block ->
                            if (block.type == CardioBlockType.WORK) {
                                block.copy(targetHrPercent = it)
                            } else block
                        },
                    ),
                )
                picker = null
            },
            allowZeroAsNone = false,
        )
        CardioEditorPicker.WATTS -> CardioIntWheelDialog(
            title = "Vatios",
            initial = details.intervalBlocks.firstOrNull { it.watts != null }?.watts ?: 120,
            range = 0..500,
            unit = "W",
            accentColor = accentColor,
            onDismiss = { picker = null },
            onConfirm = { watts ->
                onChange(details.copy(intervalBlocks = details.intervalBlocks.map { it.copy(watts = watts) }))
                picker = null
            },
        )
        CardioEditorPicker.SPEED -> CardioTenthsWheelDialog(
            title = "Velocidad",
            initial = details.intervalBlocks.firstOrNull { it.speedKmh != null }?.speedKmh ?: 8.0,
            wholeRange = 3..25,
            unit = "km/h",
            accentColor = accentColor,
            onDismiss = { picker = null },
            onConfirm = { speed ->
                onChange(details.copy(intervalBlocks = details.intervalBlocks.map { it.copy(speedKmh = speed) }))
                picker = null
            },
        )
        CardioEditorPicker.INCLINE -> CardioTenthsWheelDialog(
            title = "Inclinación",
            initial = details.intervalBlocks.firstOrNull { it.inclinePercent != null }?.inclinePercent ?: 1.0,
            wholeRange = 0..20,
            unit = "%",
            accentColor = accentColor,
            onDismiss = { picker = null },
            onConfirm = { incline ->
                onChange(details.copy(intervalBlocks = details.intervalBlocks.map { it.copy(inclinePercent = incline) }))
                picker = null
            },
        )
        CardioEditorPicker.RPM -> CardioIntWheelDialog(
            title = "Cadencia",
            initial = details.intervalBlocks.firstOrNull { it.rpm != null }?.rpm ?: 80,
            range = 40..130,
            unit = "rpm",
            accentColor = accentColor,
            onDismiss = { picker = null },
            onConfirm = { rpm ->
                onChange(details.copy(intervalBlocks = details.intervalBlocks.map { it.copy(rpm = rpm) }))
                picker = null
            },
            allowZeroAsNone = false,
        )
        CardioEditorPicker.SETS_REST -> if (uniform != null) CardioMinutesSecondsWheelDialog(
            title = "Descanso entre series",
            initialSeconds = uniform.restBetweenSetsSeconds,
            accentColor = accentColor,
            onDismiss = { picker = null },
            onConfirm = {
                onChange(CardioRepeatGrammar.applyUniform(details, uniform.copy(restBetweenSetsSeconds = it)))
                picker = null
            },
        )
        CardioEditorPicker.WORK_KCAL -> if (uniform != null) CardioIntWheelDialog(
            title = "Objetivo kcal",
            initial = uniform.workTargetValue?.toInt() ?: 12,
            range = 0..80,
            unit = "kcal",
            accentColor = accentColor,
            onDismiss = { picker = null },
            onConfirm = { kcal ->
                onChange(
                    CardioRepeatGrammar.applyUniform(
                        details,
                        uniform.copy(
                            workTargetType = if (kcal == null) HiitWorkTarget.TIME else HiitWorkTarget.KCAL,
                            workTargetValue = kcal?.toDouble(),
                        ),
                    ),
                )
                picker = null
            },
        )
        CardioEditorPicker.WORK_METERS -> if (uniform != null) CardioIntWheelDialog(
            title = "Objetivo metros",
            initial = uniform.workTargetValue?.toInt() ?: 200,
            range = 0..2000,
            unit = "m",
            accentColor = accentColor,
            onDismiss = { picker = null },
            onConfirm = { meters ->
                onChange(
                    CardioRepeatGrammar.applyUniform(
                        details,
                        uniform.copy(
                            workTargetType = if (meters == null) HiitWorkTarget.TIME else HiitWorkTarget.DISTANCE,
                            workTargetValue = meters?.toDouble(),
                        ),
                    ),
                )
                picker = null
            },
        )
        null -> Unit
    }
}

/** Continuo: tiempo, distancia y GPS en celdas, y «Añadir series», que pasa a la forma de series. */
@Composable
private fun CardioSteadyForm(
    details: CardioDetails,
    onChange: (CardioDetails) -> Unit,
    onOpenPicker: (CardioEditorPicker) -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val (timeValue, timeUnit) = durationCellParts(details.targetDurationSeconds)
        CardioMagnitudeCell(
            label = "Tiempo",
            value = timeValue,
            unit = timeUnit,
            onClick = { onOpenPicker(CardioEditorPicker.STEADY_MINUTES) },
            modifier = Modifier.weight(1f),
        )
        if (details.supportsDistance) {
            val (distanceValue, distanceUnit) = distanceCellParts(details.targetDistanceKm)
            CardioMagnitudeCell(
                label = "Distancia",
                value = distanceValue,
                unit = distanceUnit,
                onClick = { onOpenPicker(CardioEditorPicker.DISTANCE) },
                modifier = Modifier.weight(1f),
            )
        }
        if (cardioShowsGpsCell(details)) CardioGpsCell(details = details, onChange = onChange, modifier = Modifier.weight(1f))
    }
    KpknTextAction(
        text = "Añadir series",
        onClick = { switchCardioForm(details, CardioForm.SERIES)?.let(onChange) },
        leadingIcon = KpknLineIcons.Plus,
    )
}

/** Series: calentamiento, esfuerzo y pausa, las rondas con su stepper, enfriamiento y el protocolo HIIT/SIT. */
@Composable
private fun CardioSeriesForm(
    details: CardioDetails,
    repeat: CardioUniformRepeat,
    accentColor: Color,
    onChange: (CardioDetails) -> Unit,
    onOpenPicker: (CardioEditorPicker) -> Unit,
) {
    CardioStructureRow(
        title = "Calentamiento",
        value = when {
            repeat.warmupOpen -> "Libre"
            repeat.warmupSeconds <= 0 -> "Omitir"
            else -> CardioPrescriptionFormatter.formatDuration(repeat.warmupSeconds)
        },
        onValueClick = { onOpenPicker(CardioEditorPicker.WARMUP) },
        extras = {
            KpknChip(
                text = "Omitir",
                selected = repeat.warmupSeconds <= 0 && !repeat.warmupOpen,
                onClick = {
                    onChange(CardioRepeatGrammar.applyUniform(details, repeat.copy(warmupSeconds = 0, warmupOpen = false)))
                },
            )
            KpknChip(
                text = "Libre",
                selected = repeat.warmupOpen,
                onClick = {
                    onChange(CardioRepeatGrammar.applyUniform(details, repeat.copy(warmupOpen = true, warmupSeconds = 0)))
                },
            )
        },
    )

    // Las rondas, con su stepper, y el esfuerzo y la pausa a los lados.
    // Sin fondo propio: el stepper lleva sus botones en `inset` y no se perderían sobre otro `inset`.
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val (workValue, workUnit) = durationCellParts(repeat.workSeconds)
            CardioMagnitudeCell(
                label = "Esfuerzo",
                value = workValue,
                unit = workUnit,
                onClick = { onOpenPicker(CardioEditorPicker.WORK) },
                modifier = Modifier.weight(1f),
            )
            val (restValue, restUnit) = durationCellParts(repeat.restSeconds)
            CardioMagnitudeCell(
                label = "Pausa",
                value = restValue,
                unit = restUnit,
                onClick = { onOpenPicker(CardioEditorPicker.REST) },
                modifier = Modifier.weight(1f),
            )
        }
        CompactRoundStepper(
            rounds = repeat.rounds,
            accentColor = accentColor,
            onRoundsChange = { onChange(CardioRepeatGrammar.applyUniform(details, repeat.copy(rounds = it))) },
            modifier = Modifier.fillMaxWidth(),
            minRounds = 1,
            maxRounds = 99,
            label = "Rondas",
        )
    }

    CardioStructureRow(
        title = "Enfriamiento",
        value = when {
            repeat.cooldownOpen -> "Libre"
            repeat.cooldownSeconds <= 0 -> "Omitir"
            else -> CardioPrescriptionFormatter.formatDuration(repeat.cooldownSeconds)
        },
        onValueClick = { onOpenPicker(CardioEditorPicker.COOLDOWN) },
        extras = {
            KpknChip(
                text = "Omitir",
                selected = repeat.cooldownSeconds <= 0 && !repeat.cooldownOpen,
                onClick = {
                    onChange(CardioRepeatGrammar.applyUniform(details, repeat.copy(cooldownSeconds = 0, cooldownOpen = false)))
                },
            )
            KpknChip(
                text = "Libre",
                selected = repeat.cooldownOpen,
                onClick = {
                    onChange(CardioRepeatGrammar.applyUniform(details, repeat.copy(cooldownOpen = true, cooldownSeconds = 0)))
                },
            )
        },
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KpknChip(
            text = if (repeat.protocol == HiitProtocol.SIT) "SIT" else "HIIT",
            selected = true,
            onClick = {
                val sit = repeat.protocol != HiitProtocol.SIT
                onChange(
                    CardioRepeatGrammar.applyUniform(
                        details,
                        repeat.copy(
                            protocol = if (sit) HiitProtocol.SIT else HiitProtocol.HIIT,
                            targetRpe = if (sit) 10.0 else 9.0,
                        ),
                    ),
                )
            },
        )
        if (details.supportsDistance) {
            val (distanceValue, distanceUnit) = distanceCellParts(details.targetDistanceKm)
            CardioMagnitudeCell(
                label = "Distancia",
                value = distanceValue,
                unit = distanceUnit,
                onClick = { onOpenPicker(CardioEditorPicker.DISTANCE) },
                modifier = Modifier.weight(1f),
            )
        }
        if (cardioShowsGpsCell(details)) CardioGpsCell(details = details, onChange = onChange, modifier = Modifier.weight(1f))
    }
}

/**
 * La celda GPS aparece solo en los cardios al aire libre, los que admiten GPS en el catálogo (ER2b). Un cardio de interior que ya tuviera
 * GPS puesto conserva la celda, para poder quitarlo.
 */
internal fun cardioShowsGpsCell(details: CardioDetails): Boolean =
    details.requiresGps || CardioCatalog.items.any { it.type == details.type && it.requiresGps }

/** GPS como celda: su estado en la cifra y un toque lo cambia. */
@Composable
private fun CardioGpsCell(
    details: CardioDetails,
    onChange: (CardioDetails) -> Unit,
    modifier: Modifier = Modifier,
) {
    CardioMagnitudeCell(
        label = "GPS",
        value = if (details.requiresGps) "Sí" else "No",
        unit = null,
        onClick = { onChange(details.copy(requiresGps = !details.requiresGps)) },
        modifier = modifier,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CardioStructureRow(
    title: String,
    value: String,
    onValueClick: () -> Unit,
    extras: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardioCellShape)
            .background(KpknSurface.inset, CardioCellShape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = KpknType.bodyStrong, color = WizardColors.text)
            Box(
                modifier = Modifier
                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                    .clickable(role = Role.Button, onClick = onValueClick),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Text(value, style = KpknType.metricM, color = WizardColors.text)
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) { extras() }
    }
}

/** Historial del tipo de cardio: la última vez, los récords y las bandas, con «Usar última» y «Progresar». */
@Composable
private fun CardioHistoryPanel(
    history: CardioTypeHistory,
    onApplyLast: () -> Unit,
    onApplyProgression: () -> Unit,
) {
    val bands = history.bestPaceSecondsPerKm?.let { CardioPaceBandEngine.fromAnchorPace(it) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardioCellShape)
            .background(KpknSurface.inset, CardioCellShape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("HISTORIAL", style = KpknType.eyebrow, color = WizardColors.textMuted)
        val lastBits = listOfNotNull(
            history.lastDurationSeconds?.let { CardioPrescriptionFormatter.formatDuration(it) },
            history.lastDistanceKm?.let { CardioPrescriptionFormatter.formatDistanceKm(it) },
            history.lastPaceSecondsPerKm?.let { CardioPrescriptionFormatter.formatPace(it) },
            history.lastRpe?.let { "RPE ${it.toInt()}" },
        ).joinToString(" · ")
        if (lastBits.isNotBlank()) {
            Text("Última vez · $lastBits", style = KpknType.caption, color = WizardColors.text)
        }
        val prBits = listOfNotNull(
            history.bestPaceSecondsPerKm?.let { "Ritmo ${CardioPrescriptionFormatter.formatPace(it)}" },
            history.longestDistanceKm?.let { "Dist ${CardioPrescriptionFormatter.formatDistanceKm(it)}" },
            history.longestTimeSeconds?.let { "Tiempo ${CardioPrescriptionFormatter.formatDuration(it)}" },
        ).joinToString(" · ")
        if (prBits.isNotBlank()) {
            Text("PRs · $prBits", style = KpknType.caption, color = WizardColors.text)
        }
        bands?.let {
            Text(
                "Bandas · easy ${CardioPrescriptionFormatter.formatPace(it.easySecondsPerKm)} · tempo ${CardioPrescriptionFormatter.formatPace(it.tempoSecondsPerKm)} · 5K ${CardioPrescriptionFormatter.formatPace(it.fiveKSecondsPerKm)}",
                style = KpknType.caption,
                color = WizardColors.textMuted,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KpknChip(text = "Usar última", selected = false, onClick = onApplyLast)
            KpknChip(text = "Progresar", selected = false, onClick = onApplyProgression)
        }
    }
}

/** Plantillas destacadas: una fila de tarjetas con su mini gráfica y su dosis. Tocar una la aplica. */
@Composable
private fun CardioTemplatesRow(
    details: CardioDetails,
    accentColor: Color,
    onChange: (CardioDetails) -> Unit,
) {
    val templates = remember(details) {
        CardioFeaturedTemplates.all.map { template -> template to CardioFeaturedTemplates.apply(template.id, details) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("PLANTILLAS", style = KpknType.eyebrow, color = WizardColors.textMuted)
        val templatesState = rememberLazyListState()
        LazyRow(
            state = templatesState,
            // ER2b: la orilla se funde como en las filas de chips, para que una plantilla cortada no quede en seco.
            modifier = Modifier.sessionLazyRowEdgeFade(templatesState),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(templates, key = { it.first.id }) { (template, preview) ->
                CardioTemplateCard(
                    template = template,
                    preview = preview,
                    selected = templateSelected(template, details),
                    accentColor = accentColor,
                    onClick = { onChange(CardioFeaturedTemplates.apply(template.id, details)) },
                )
            }
        }
    }
}

@Composable
private fun CardioTemplateCard(
    template: CardioFeaturedTemplate,
    preview: CardioDetails,
    selected: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(176.dp)
            .clip(CardioCellShape)
            .background(KpknSurface.inset, CardioCellShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                template.name,
                style = KpknType.bodyStrong,
                color = WizardColors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (selected) {
                Icon(
                    imageVector = KpknLineIcons.Check,
                    contentDescription = "Plantilla elegida",
                    tint = WizardColors.text,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        // Con intervalos, la gráfica de sus bloques; en continuo, una barra del color del esfuerzo.
        if (preview.hasIntervals()) {
            CardioIntervalChart(
                details = preview,
                accentColor = accentColor,
                sparkline = true,
                showLabels = false,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(accentColor.copy(alpha = 0.5f)),
            )
        }
        Text(
            cardioDoseLine(preview),
            style = KpknType.caption,
            color = WizardColors.textMuted,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Una plantilla figura como elegida si su materialización coincide con lo que hay en el editor (la lógica de siempre). */
private fun templateSelected(template: CardioFeaturedTemplate, details: CardioDetails): Boolean {
    val hiitId = template.hiitId
    return if (hiitId != null) {
        val config = CardioHiitTemplates.findById(hiitId)?.toConfig()
        val hiit = details.hiit
        hiit != null && config != null && hiit.workSeconds == config.workSeconds && hiit.rounds == config.rounds
    } else {
        !details.hasIntervals() && details.targetDurationSeconds == (template.steadyMinutes ?: 0) * 60
    }
}

/** «Avanzado»: ritmo, FC, vatios, velocidad, inclinación y cadencia; las series, la pausa, las kcal y los metros; y los avisos. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CardioAdvancedPanel(
    details: CardioDetails,
    shape: CardioAuthoringShape,
    catalogSupportsSpeed: Boolean,
    catalogSupportsIncline: Boolean,
    catalogSupportsRpm: Boolean,
    catalogSupportsWatts: Boolean,
    accentColor: Color,
    onChange: (CardioDetails) -> Unit,
    onOpenPicker: (CardioEditorPicker) -> Unit,
) {
    val uniform = (shape as? CardioAuthoringShape.Uniform)?.repeat
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardioCellShape)
            .background(KpknSurface.inset, CardioCellShape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val (paceValue, paceUnit) = paceCellParts(details.targetPaceSecondsPerKm)
            CardioMagnitudeCell(
                label = "Ritmo",
                value = paceValue,
                unit = paceUnit,
                onClick = { onOpenPicker(CardioEditorPicker.PACE) },
                modifier = Modifier.width(144.dp),
            )
            val hr = details.targetHrPercent
            CardioMagnitudeCell(
                label = "FC",
                value = hr?.toString() ?: "—",
                unit = hr?.let { "%" },
                onClick = { onOpenPicker(CardioEditorPicker.HR) },
                modifier = Modifier.width(144.dp),
            )
            if (catalogSupportsWatts) {
                val watts = details.intervalBlocks.firstOrNull { it.watts != null }?.watts
                CardioMagnitudeCell(
                    label = "Vatios",
                    value = watts?.toString() ?: "—",
                    unit = watts?.let { "W" },
                    onClick = { onOpenPicker(CardioEditorPicker.WATTS) },
                    modifier = Modifier.width(144.dp),
                )
            }
            if (catalogSupportsSpeed) {
                val speed = details.intervalBlocks.firstOrNull { it.speedKmh != null }?.speedKmh
                CardioMagnitudeCell(
                    label = "Velocidad",
                    value = speed?.let { cellNumber(it) } ?: "—",
                    unit = speed?.let { "km/h" },
                    onClick = { onOpenPicker(CardioEditorPicker.SPEED) },
                    modifier = Modifier.width(144.dp),
                )
            }
            if (catalogSupportsIncline) {
                val incline = details.intervalBlocks.firstOrNull { it.inclinePercent != null }?.inclinePercent
                CardioMagnitudeCell(
                    label = "Inclinación",
                    value = incline?.let { cellNumber(it) } ?: "—",
                    unit = incline?.let { "%" },
                    onClick = { onOpenPicker(CardioEditorPicker.INCLINE) },
                    modifier = Modifier.width(144.dp),
                )
            }
            if (catalogSupportsRpm) {
                val rpm = details.intervalBlocks.firstOrNull { it.rpm != null }?.rpm
                CardioMagnitudeCell(
                    label = "RPM",
                    value = rpm?.toString() ?: "—",
                    unit = null,
                    onClick = { onOpenPicker(CardioEditorPicker.RPM) },
                    modifier = Modifier.width(144.dp),
                )
            }
        }
        if (uniform != null) {
            CompactRoundStepper(
                rounds = uniform.sets,
                accentColor = accentColor,
                onRoundsChange = { onChange(CardioRepeatGrammar.applyUniform(details, uniform.copy(sets = it))) },
                modifier = Modifier.fillMaxWidth(),
                minRounds = 1,
                maxRounds = 5,
                label = "Series",
            )
            if (uniform.sets > 1) {
                val (restValue, restUnit) = durationCellParts(uniform.restBetweenSetsSeconds)
                CardioMagnitudeCell(
                    label = "Entre series",
                    value = restValue,
                    unit = restUnit,
                    onClick = { onOpenPicker(CardioEditorPicker.SETS_REST) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                KpknChip(
                    text = if (uniform.restNature == HiitRestNature.PASSIVE) "Pausa pasiva" else "Pausa activa",
                    selected = true,
                    onClick = {
                        val next = if (uniform.restNature == HiitRestNature.ACTIVE) HiitRestNature.PASSIVE else HiitRestNature.ACTIVE
                        onChange(CardioRepeatGrammar.applyUniform(details, uniform.copy(restNature = next)))
                    },
                )
                CardioMagnitudeCell(
                    label = "kcal/bloque",
                    value = if (uniform.workTargetType == HiitWorkTarget.KCAL) "${uniform.workTargetValue?.toInt() ?: 0}" else "—",
                    unit = null,
                    onClick = { onOpenPicker(CardioEditorPicker.WORK_KCAL) },
                    modifier = Modifier.width(144.dp),
                )
                CardioMagnitudeCell(
                    label = "m/bloque",
                    value = if (uniform.workTargetType == HiitWorkTarget.DISTANCE) "${uniform.workTargetValue?.toInt() ?: 0}" else "—",
                    unit = null,
                    onClick = { onOpenPicker(CardioEditorPicker.WORK_METERS) },
                    modifier = Modifier.width(144.dp),
                )
            }
            val config = details.hiit ?: CardioHiitConfig()
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                KpknChip(
                    text = "Beeps",
                    selected = config.beepsEnabled,
                    onClick = { onChange(details.copy(hiit = config.copy(beepsEnabled = !config.beepsEnabled))) },
                )
                KpknChip(
                    text = "Voz",
                    selected = config.voiceCuesEnabled,
                    onClick = { onChange(details.copy(hiit = config.copy(voiceCuesEnabled = !config.voiceCuesEnabled))) },
                )
                KpknChip(
                    text = "Vibra",
                    selected = config.vibrationEnabled,
                    onClick = { onChange(details.copy(hiit = config.copy(vibrationEnabled = !config.vibrationEnabled))) },
                )
            }
        }
    }
}
