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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.CardioBlockType
import com.example.kpkn.data.models.CardioCatalog
import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.CardioFeaturedTemplates
import com.example.kpkn.data.models.CardioHiitConfig
import com.example.kpkn.data.models.CardioIntensity
import com.example.kpkn.data.models.HiitProtocol
import com.example.kpkn.data.models.HiitRestNature
import com.example.kpkn.data.models.HiitWorkTarget
import com.example.kpkn.domain.cardio.CardioAuthoringShape
import com.example.kpkn.domain.cardio.CardioPaceBandEngine
import com.example.kpkn.domain.cardio.CardioPrescriptionFormatter
import com.example.kpkn.domain.cardio.CardioRepeatGrammar
import com.example.kpkn.domain.cardio.CardioTypeHistory
import com.example.kpkn.domain.cardio.CardioUniformRepeat
import com.example.kpkn.domain.workout.CardioProgressionEngine
import com.example.kpkn.domain.workout.CardioProgressionInput
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknChip
import com.example.kpkn.ui.design.KpknMetric
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
 * Editor de un bloque de cardio desplegado (SESSION_EDITOR_V1 §2, punto 7), con piel KPKN: las secciones van separadas con aire y filetes
 * `divider` dentro del módulo, sin cajas. Las plantillas y los ajustes son `KpknChip`; los valores, `CardioValuePill`; el RPE, `metricM`.
 * Firma fija: lo usan el editor de ejercicio y la hoja de estructura de la sesión en vivo.
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
    val catalog = remember(details.type) { CardioCatalog.findByType(details.type) }
    val rpe = details.resolvedRpe().toInt().coerceIn(1, 10)
    var picker by remember { mutableStateOf<CardioEditorPicker?>(null) }
    var showBlocks by remember { mutableStateOf(shape is CardioAuthoringShape.Irregular) }
    var showAdvanced by remember { mutableStateOf(false) }
    val sentence = remember(details) { CardioPrescriptionFormatter.sentence(details) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = sentence,
            style = KpknType.caption,
            color = WizardColors.textMuted,
        )

        if (showPlacementChip && onTogglePlacement != null) {
            KpknChip(
                text = if (cardioFirst) "Al inicio" else "Al final",
                selected = true,
                onClick = onTogglePlacement,
            )
        }

        history?.takeUnless { it.isEmpty }?.let { stats ->
            CardioHistoryPanel(
                history = stats,
                accentColor = accentColor,
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

        CardioSection(title = "Plantillas") {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CardioFeaturedTemplates.all.forEach { template ->
                    val selected = when {
                        template.hiitId != null -> details.hiit != null &&
                            details.hiit!!.workSeconds == com.example.kpkn.data.models.CardioHiitTemplates.findById(template.hiitId)?.toConfig()?.workSeconds &&
                            details.hiit!!.rounds == com.example.kpkn.data.models.CardioHiitTemplates.findById(template.hiitId)?.toConfig()?.rounds
                        else -> !details.hasIntervals() && details.targetDurationSeconds == (template.steadyMinutes ?: 0) * 60
                    }
                    KpknChip(
                        text = template.name,
                        selected = selected,
                        onClick = { onChange(CardioFeaturedTemplates.apply(template.id, details)) },
                    )
                }
            }
        }

        when (shape) {
            is CardioAuthoringShape.Steady -> {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    CardioValuePill(
                        label = "Tiempo",
                        value = details.targetDurationSeconds?.let { CardioPrescriptionFormatter.formatDuration(it) } ?: "Libre",
                        accentColor = accentColor,
                        onClick = { picker = CardioEditorPicker.STEADY_MINUTES },
                    )
                    if (details.supportsDistance) {
                        CardioValuePill(
                            label = "Distancia",
                            value = details.targetDistanceKm?.let { CardioPrescriptionFormatter.formatDistanceKm(it) } ?: "—",
                            accentColor = accentColor,
                            onClick = { picker = CardioEditorPicker.DISTANCE },
                        )
                    }
                    KpknChip(
                        text = if (details.requiresGps) "GPS activo" else "GPS",
                        selected = details.requiresGps,
                        onClick = { onChange(details.copy(requiresGps = !details.requiresGps)) },
                    )
                }
                KpknChip(
                    text = "Añadir series",
                    selected = false,
                    onClick = {
                        onChange(
                            CardioRepeatGrammar.applyUniform(
                                details,
                                CardioUniformRepeat(
                                    warmupSeconds = 180,
                                    workSeconds = 30,
                                    restSeconds = 60,
                                    rounds = 8,
                                    cooldownSeconds = 180,
                                    targetRpe = 9.0,
                                ),
                            ),
                        )
                    },
                )
            }
            is CardioAuthoringShape.Uniform -> {
                val repeat = shape.repeat
                CardioStructureRow(
                    title = "Calentamiento",
                    value = when {
                        repeat.warmupOpen -> "Libre"
                        repeat.warmupSeconds <= 0 -> "Omitir"
                        else -> CardioPrescriptionFormatter.formatDuration(repeat.warmupSeconds)
                    },
                    onValueClick = { picker = CardioEditorPicker.WARMUP },
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
                CardioStructureRow(
                    title = "Repetir",
                    value = "${repeat.rounds}×",
                    onValueClick = null,
                    extras = {
                        CompactRoundStepper(
                            rounds = repeat.rounds,
                            accentColor = accentColor,
                            minRounds = 1,
                            maxRounds = 99,
                            onRoundsChange = { onChange(CardioRepeatGrammar.applyUniform(details, repeat.copy(rounds = it))) },
                        )
                        CardioValuePill(
                            label = "Esfuerzo",
                            value = CardioPrescriptionFormatter.formatDuration(repeat.workSeconds),
                            accentColor = accentColor,
                            onClick = { picker = CardioEditorPicker.WORK },
                        )
                        CardioValuePill(
                            label = "Pausa",
                            value = CardioPrescriptionFormatter.formatDuration(repeat.restSeconds),
                            accentColor = accentColor,
                            onClick = { picker = CardioEditorPicker.REST },
                        )
                    },
                )
                CardioStructureRow(
                    title = "Enfriamiento",
                    value = when {
                        repeat.cooldownOpen -> "Libre"
                        repeat.cooldownSeconds <= 0 -> "Omitir"
                        else -> CardioPrescriptionFormatter.formatDuration(repeat.cooldownSeconds)
                    },
                    onValueClick = { picker = CardioEditorPicker.COOLDOWN },
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
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
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
                    KpknChip(
                        text = "Continuo",
                        selected = false,
                        onClick = {
                            onChange(
                                CardioRepeatGrammar.applySteady(
                                    details,
                                    durationSeconds = details.effectiveDurationSeconds().takeIf { it > 0 } ?: 20 * 60,
                                ),
                            )
                        },
                    )
                    if (details.supportsDistance) {
                        CardioValuePill(
                            label = "Distancia",
                            value = details.targetDistanceKm?.let { CardioPrescriptionFormatter.formatDistanceKm(it) } ?: "—",
                            accentColor = accentColor,
                            onClick = { picker = CardioEditorPicker.DISTANCE },
                        )
                    }
                    KpknChip(
                        text = if (details.requiresGps) "GPS activo" else "GPS",
                        selected = details.requiresGps,
                        onClick = { onChange(details.copy(requiresGps = !details.requiresGps)) },
                    )
                }
            }
            is CardioAuthoringShape.Irregular -> {
                KpknChip(
                    text = if (showBlocks) "Ocultar bloques" else "Ajustar bloques",
                    selected = showBlocks,
                    onClick = { showBlocks = !showBlocks },
                )
            }
        }

        // Esfuerzo percibido: la cifra en `metricM` y su anclaje, con el deslizador de 1 a 10.
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CardioEditorKicker("RPE")
                KpknMetric(value = "$rpe", style = KpknType.metricM)
                Text(
                    text = CardioPrescriptionFormatter.rpeAnchor(rpe),
                    style = KpknType.caption,
                    color = WizardColors.textMuted,
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
                    activeTrackColor = accentColor,
                    inactiveTrackColor = KpknSurface.inset,
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

        KpknTextAction(
            text = if (showAdvanced) "Ocultar avanzado" else "Avanzado",
            onClick = { showAdvanced = !showAdvanced },
            leadingIcon = KpknLineIcons.Sliders,
        )
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

/** Sección dentro del módulo: un filete `divider` arriba, la etiqueta en mayúsculas y su contenido. Sin caja. */
@Composable
private fun CardioSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(KpknSurface.divider))
        CardioEditorKicker(title)
        content()
    }
}

/** Etiqueta de sección en mayúsculas (kicker, `eyebrow`). */
@Composable
private fun CardioEditorKicker(text: String) {
    Text(
        text = text.uppercase(Locale("es")),
        style = KpknType.eyebrow,
        color = WizardColors.textMuted,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Fila de estructura: el título a la izquierda, su valor a la derecha (tocable si [onValueClick] no es null) y los ajustes debajo. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CardioStructureRow(
    title: String,
    value: String,
    onValueClick: (() -> Unit)?,
    extras: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = KpknType.bodyStrong,
                color = WizardColors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = value,
                style = KpknType.bodyStrong.copy(fontFeatureSettings = "'tnum' 1"),
                color = WizardColors.text,
                maxLines = 1,
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .let { base -> if (onValueClick != null) base.clickable(role = Role.Button, onClickLabel = title, onClick = onValueClick) else base }
                    .padding(vertical = 14.dp),
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) { extras() }
    }
}

/** Memoria del último bloque del mismo tipo y sus marcas, con las acciones «Usar última» y «Progresar». */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CardioHistoryPanel(
    history: CardioTypeHistory,
    accentColor: Color,
    onApplyLast: () -> Unit,
    onApplyProgression: () -> Unit,
) {
    val bands = history.bestPaceSecondsPerKm?.let { CardioPaceBandEngine.fromAnchorPace(it) }
    CardioSection(title = "Memoria") {
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
            history.longestDistanceKm?.let { "Distancia ${CardioPrescriptionFormatter.formatDistanceKm(it)}" },
            history.longestTimeSeconds?.let { "Tiempo ${CardioPrescriptionFormatter.formatDuration(it)}" },
        ).joinToString(" · ")
        if (prBits.isNotBlank()) {
            Text("Marcas · $prBits", style = KpknType.caption, color = accentColor)
        }
        bands?.let {
            Text(
                text = "Bandas · fácil ${CardioPrescriptionFormatter.formatPace(it.easySecondsPerKm)} · tempo ${CardioPrescriptionFormatter.formatPace(it.tempoSecondsPerKm)} · 5K ${CardioPrescriptionFormatter.formatPace(it.fiveKSecondsPerKm)}",
                style = KpknType.caption,
                color = WizardColors.textFaint,
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            KpknChip(text = "Usar última", selected = false, onClick = onApplyLast)
            KpknChip(text = "Progresar", selected = false, onClick = onApplyProgression)
        }
    }
}

/** Ajustes avanzados: ritmo, FC, magnitudes del aparato, series, pausa, objetivo por bloque y las señales del HIIT. */
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
    CardioSection(title = "Avanzado") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CardioValuePill(
                label = "Ritmo",
                value = details.targetPaceSecondsPerKm?.let { CardioPrescriptionFormatter.formatPace(it) } ?: "—",
                accentColor = accentColor,
                onClick = { onOpenPicker(CardioEditorPicker.PACE) },
            )
            CardioValuePill(
                label = "% FC",
                value = details.targetHrPercent?.let { "$it %" } ?: "—",
                accentColor = accentColor,
                onClick = { onOpenPicker(CardioEditorPicker.HR) },
            )
            if (catalogSupportsWatts) {
                CardioValuePill("Vatios", details.intervalBlocks.firstOrNull { it.watts != null }?.watts?.let { "$it W" } ?: "—", accentColor, { onOpenPicker(CardioEditorPicker.WATTS) })
            }
            if (catalogSupportsSpeed) {
                CardioValuePill("Velocidad", details.intervalBlocks.firstOrNull { it.speedKmh != null }?.speedKmh?.let { "$it km/h" } ?: "—", accentColor, { onOpenPicker(CardioEditorPicker.SPEED) })
            }
            if (catalogSupportsIncline) {
                CardioValuePill("Inclinación", details.intervalBlocks.firstOrNull { it.inclinePercent != null }?.inclinePercent?.let { "$it %" } ?: "—", accentColor, { onOpenPicker(CardioEditorPicker.INCLINE) })
            }
            if (catalogSupportsRpm) {
                CardioValuePill("RPM", details.intervalBlocks.firstOrNull { it.rpm != null }?.rpm?.let { "$it" } ?: "—", accentColor, { onOpenPicker(CardioEditorPicker.RPM) })
            }
        }
        if (uniform != null) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CardioEditorKicker("Series")
                CompactRoundStepper(
                    rounds = uniform.sets,
                    accentColor = accentColor,
                    minRounds = 1,
                    maxRounds = 5,
                    onRoundsChange = { onChange(CardioRepeatGrammar.applyUniform(details, uniform.copy(sets = it))) },
                )
                if (uniform.sets > 1) {
                    CardioValuePill(
                        label = "Entre series",
                        value = CardioPrescriptionFormatter.formatDuration(uniform.restBetweenSetsSeconds),
                        accentColor = accentColor,
                        onClick = { onOpenPicker(CardioEditorPicker.SETS_REST) },
                    )
                }
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
                CardioValuePill(
                    label = "kcal por bloque",
                    value = if (uniform.workTargetType == HiitWorkTarget.KCAL) "${uniform.workTargetValue?.toInt() ?: 0}" else "—",
                    accentColor = accentColor,
                    onClick = { onOpenPicker(CardioEditorPicker.WORK_KCAL) },
                )
                CardioValuePill(
                    label = "m por bloque",
                    value = if (uniform.workTargetType == HiitWorkTarget.DISTANCE) "${uniform.workTargetValue?.toInt() ?: 0}" else "—",
                    accentColor = accentColor,
                    onClick = { onOpenPicker(CardioEditorPicker.WORK_METERS) },
                )
            }
            val config = details.hiit ?: CardioHiitConfig()
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                KpknChip(
                    text = "Pitidos",
                    selected = config.beepsEnabled,
                    onClick = { onChange(details.copy(hiit = config.copy(beepsEnabled = !config.beepsEnabled))) },
                )
                KpknChip(
                    text = "Voz",
                    selected = config.voiceCuesEnabled,
                    onClick = { onChange(details.copy(hiit = config.copy(voiceCuesEnabled = !config.voiceCuesEnabled))) },
                )
                KpknChip(
                    text = "Vibración",
                    selected = config.vibrationEnabled,
                    onClick = { onChange(details.copy(hiit = config.copy(vibrationEnabled = !config.vibrationEnabled))) },
                )
            }
        }
    }
}
