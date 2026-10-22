package com.example.kpkn.screens.sessioneditor.components.sheets

import androidx.compose.foundation.clickable
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.sessionassistant.TimeCoachFatigueDelta
import com.example.kpkn.domain.sessionassistant.TimeCoachSuggestion
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.sessioneditor.SessionEditorUiState
import com.example.kpkn.screens.sessioneditor.design.sessionChipRowEdgeFade
import com.example.kpkn.screens.sessioneditor.frame.FrameRingTone
import com.example.kpkn.screens.sessioneditor.frame.frameTimeRingTone
import com.example.kpkn.screens.sessioneditor.sessionTimeBreakdownCaption
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.components.rings.KpknRingColors
import com.example.kpkn.ui.design.KpknCellInput
import com.example.kpkn.ui.design.KpknChip
import com.example.kpkn.ui.design.KpknMetric
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknModuleHeader
import com.example.kpkn.ui.design.KpknNumberCell
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknSecondaryPill
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknType

/** Qué duración edita el selector de tiempo: el global de la sesión, un grupo o un ejercicio. */
internal sealed class DurationPickerTarget {
    abstract val title: String
    abstract val currentMinutes: Int?

    data class Global(
        override val currentMinutes: Int?,
    ) : DurationPickerTarget() {
        override val title: String = "Presupuesto global"
    }

    data class Part(
        val partId: String,
        override val title: String,
        override val currentMinutes: Int?,
    ) : DurationPickerTarget()

    data class Exercise(
        val exerciseId: String,
        override val title: String,
        override val currentMinutes: Int?,
    ) : DurationPickerTarget()
}

/** Estado del reloj de sesión frente a su límite. Sin límite, el reloj es neutro. */
internal enum class RulesClockStatus { NEUTRAL, OK, ENERGY, OVER }

/**
 * Estado del reloj: `ok` por debajo del 85 % del límite, Energía hasta el 108 % y `danger` por encima. Lo decide la única función de
 * los umbrales, [frameTimeRingTone] (ER1f: antes había una copia en coma flotante aquí). Función pura.
 */
internal fun rulesClockStatusOf(estimatedMinutes: Int, limitMinutes: Int?): RulesClockStatus = when (frameTimeRingTone(estimatedMinutes, limitMinutes)) {
    FrameRingTone.NEUTRAL -> RulesClockStatus.NEUTRAL
    FrameRingTone.OK -> RulesClockStatus.OK
    FrameRingTone.ENERGIA -> RulesClockStatus.ENERGY
    FrameRingTone.DANGER -> RulesClockStatus.OVER
}

/** Color del reloj por estado: `ok` (#43D18C), Energía (#F7CF73) o `danger`; neutro, en tinta atenuada. */
internal fun rulesClockColor(status: RulesClockStatus): Color = when (status) {
    RulesClockStatus.NEUTRAL -> WizardColors.textMuted
    RulesClockStatus.OK -> WizardColors.done
    RulesClockStatus.ENERGY -> KpknRingColors.energia
    RulesClockStatus.OVER -> WizardColors.danger
}


/** Minutos como «45 min», «1 h 30 min» o «Sin límite» (sin límite o cero). */
internal fun formatDurationMinutes(minutes: Int?): String {
    if (minutes == null || minutes <= 0) return "Sin límite"
    val h = minutes / 60
    val m = minutes % 60
    return if (h > 0) "%d h %02d min".format(h, m) else "$m min"
}

/**
 * Pestaña Tiempo de «Reglas y tiempo» (ER1e): el reloj de sesión en grande (anillo frente al límite, con «Estimado», «Límite» y
 * «Diferencia»), las sugerencias para entrar en el límite como módulos, el tiempo de sesión y los tiempos por grupo y ejercicio.
 * Mismos datos y mismas llamadas que el original; solo cambia la piel.
 */
@Composable
internal fun RulesTimeTab(
    uiState: SessionEditorUiState,
    setTargetDuration: (Int?) -> Unit,
    onOpenDuration: (DurationPickerTarget) -> Unit,
    onDistributeTargetAcrossParts: () -> Unit,
    onApplyTimeCoachSuggestion: (String) -> Unit,
    onDismissTimeCoachSuggestion: (String) -> Unit,
) {
    val session = uiState.activeVariantSession ?: uiState.session ?: return
    val assignedMinutes = session.parts.sumOf { part ->
        part.targetDurationMinutes
            ?: part.exercises.sumOf { it.targetDurationMinutes ?: 0 }
    } + session.exercises.sumOf { it.targetDurationMinutes ?: 0 }
    val estimated = uiState.sessionTimeBreakdown?.totalMinutes ?: uiState.estimatedDurationMinutes
    val limit = session.targetDurationMinutes
    val gap = if (limit != null && estimated > 0) estimated - limit else null
    val status = rulesClockStatusOf(estimated, limit)
    val statusColor = rulesClockColor(status)
    var groupTimesExpanded by rememberSaveable { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // 1. El reloj de sesión: el anillo de la barra inferior, en grande, con sus tres cifras en Syne.
        KpknModule(modifier = Modifier.fillMaxWidth()) {
            KpknModuleHeader(label = "Reloj de sesión")
            Spacer(Modifier.height(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                RulesClockRing(
                    estimatedMinutes = estimated,
                    limitMinutes = limit,
                    modifier = Modifier.size(128.dp),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    RulesClockStat(
                        label = "Estimado",
                        value = if (estimated > 0) "$estimated" else "—",
                        unit = if (estimated > 0) "min" else null,
                        color = WizardColors.text,
                    )
                    RulesClockStat(
                        label = "Límite",
                        value = limit?.let { "$it" } ?: "Sin límite",
                        unit = limit?.let { "min" },
                        color = WizardColors.text,
                    )
                    RulesClockStat(
                        label = "Diferencia",
                        value = when {
                            gap == null -> "—"
                            gap > 0 -> "+$gap"
                            else -> "$gap"
                        },
                        unit = gap?.let { "min" },
                        color = if (gap == null) WizardColors.textMuted else statusColor,
                    )
                }
            }
            uiState.sessionTimeBreakdown?.let { bd ->
                Spacer(Modifier.height(12.dp))
                // El mismo desglose que el asistente (ER2b).
                SheetCaption(sessionTimeBreakdownCaption(bd))
            }
        }

        // 2. Las sugerencias para entrar en el límite, una por módulo.
        if (uiState.timeCoachSuggestions.isNotEmpty()) {
            SheetEyebrow("Para entrar en tu límite", modifier = Modifier.padding(start = 4.dp))
            uiState.timeCoachSuggestions.forEach { suggestion ->
                RulesTimeCoachModule(
                    suggestion = suggestion,
                    onDismiss = { onDismissTimeCoachSuggestion(suggestion.id) },
                    onApply = { onApplyTimeCoachSuggestion(suggestion.id) },
                )
            }
        } else if (limit != null && gap != null && gap > 0) {
            SheetCaption(
                "Calculando sugerencias… o no hay ajustes seguros aún.",
                modifier = Modifier.padding(start = 4.dp),
            )
        }

        // 3. El tiempo de sesión: los atajos y la cifra que se edita con el selector.
        KpknModule(modifier = Modifier.fillMaxWidth()) {
            KpknModuleHeader(label = "Tiempo de sesión")
            Spacer(Modifier.height(4.dp))
            SheetCaption("Cuenta atrás en el entreno. No corta la sesión.")
            Spacer(Modifier.height(12.dp))
            val sessionTimeScroll = rememberScrollState()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .sessionChipRowEdgeFade(sessionTimeScroll)
                    .horizontalScroll(sessionTimeScroll),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                listOf(30, 45, 60, 90).forEach { mins ->
                    KpknChip(
                        text = "$mins min",
                        selected = session.targetDurationMinutes == mins,
                        onClick = { setTargetDuration(mins) },
                    )
                }
                if (estimated > 0) {
                    KpknChip(
                        text = "Estimado",
                        selected = session.targetDurationMinutes == estimated,
                        onClick = { setTargetDuration(estimated) },
                    )
                }
                KpknChip(
                    text = "Sin límite",
                    selected = session.targetDurationMinutes == null,
                    onClick = { setTargetDuration(null) },
                )
            }
            Spacer(Modifier.height(12.dp))
            RulesTimeCell(
                label = "Tiempo de sesión",
                value = formatDurationMinutes(limit),
                modifier = Modifier.fillMaxWidth(),
                onOpen = { onOpenDuration(DurationPickerTarget.Global(currentMinutes = limit)) },
            )
            val sessionBudget = limit ?: 0
            if (sessionBudget > 0) {
                val isOverBudget = assignedMinutes > sessionBudget
                val remaining = sessionBudget - assignedMinutes
                // D6: barra del reparto del tiempo asignado por grupo; lo que queda libre, en el fondo.
                val partShares = session.parts
                    .map { part ->
                        part.name to (part.targetDurationMinutes ?: part.exercises.sumOf { it.targetDurationMinutes ?: 0 })
                    }
                    .filter { it.second > 0 }
                if (partShares.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    val shadeSteps = listOf(1f, 0.5f, 0.3f, 0.2f)
                    Row(
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(KpknShape.pill),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        partShares.forEachIndexed { index, (_, minutes) ->
                            Box(
                                modifier = Modifier
                                    .weight(minutes.toFloat())
                                    .fillMaxHeight()
                                    .background(WizardColors.text.copy(alpha = shadeSteps[index.coerceAtMost(shadeSteps.lastIndex)])),
                            )
                        }
                        if (remaining > 0) {
                            Box(Modifier.weight(remaining.toFloat()).fillMaxHeight().background(KpknSurface.inset))
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    SheetCaption(partShares.joinToString(" · ") { (name, minutes) -> "$name $minutes min" })
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = if (isOverBudget) {
                        "Asignados $assignedMinutes min; el tiempo de sesión es $sessionBudget min"
                    } else {
                        "$assignedMinutes de $sessionBudget min asignados ($remaining min libres)"
                    },
                    style = KpknType.caption,
                    color = if (isOverBudget) WizardColors.danger else WizardColors.textMuted,
                )
            }
        }

        // 4. Los tiempos por grupo y por ejercicio, en filas. Plegados por defecto, como antes.
        KpknModule(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable(role = Role.Button) { groupTimesExpanded = !groupTimesExpanded },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    SheetEyebrow("Tiempos por grupos y ejercicios")
                    Spacer(Modifier.height(2.dp))
                    SheetCaption(
                        if (groupTimesExpanded) {
                            "Por grupo o ejercicio. El del grupo vale."
                        } else {
                            "Tocar para tiempos por grupo"
                        },
                    )
                }
                Icon(
                    imageVector = if (groupTimesExpanded) KpknLineIcons.ChevronUp else KpknLineIcons.ChevronDown,
                    contentDescription = if (groupTimesExpanded) "Colapsar tiempos" else "Desplegar tiempos",
                    tint = WizardColors.textMuted,
                    modifier = Modifier.size(20.dp),
                )
            }
            if (groupTimesExpanded) {
                Spacer(Modifier.height(12.dp))
                if (session.parts.size >= 2 && (session.targetDurationMinutes ?: 0) > 0) {
                    KpknChip(
                        text = "Repartir global en grupos",
                        selected = false,
                        onClick = onDistributeTargetAcrossParts,
                    )
                    Spacer(Modifier.height(12.dp))
                }
                session.parts.forEach { part ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = part.name,
                            style = KpknType.bodyStrong,
                            color = WizardColors.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        RulesTimeCell(
                            label = part.name,
                            value = formatDurationMinutes(part.targetDurationMinutes),
                            modifier = Modifier.width(112.dp),
                            onOpen = {
                                onOpenDuration(
                                    DurationPickerTarget.Part(
                                        partId = part.id,
                                        title = part.name,
                                        currentMinutes = part.targetDurationMinutes,
                                    ),
                                )
                            },
                        )
                    }
                    part.exercises.forEach { ex ->
                        RulesTimeExerciseRow(
                            name = ex.name,
                            minutes = ex.targetDurationMinutes,
                            onOpen = {
                                onOpenDuration(
                                    DurationPickerTarget.Exercise(
                                        exerciseId = ex.id,
                                        title = ex.name,
                                        currentMinutes = ex.targetDurationMinutes,
                                    ),
                                )
                            },
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                }
                if (session.exercises.isNotEmpty()) {
                    SheetEyebrow("Otros ejercicios")
                    Spacer(Modifier.height(6.dp))
                    session.exercises.forEach { ex ->
                        RulesTimeExerciseRow(
                            name = ex.name,
                            minutes = ex.targetDurationMinutes,
                            onOpen = {
                                onOpenDuration(
                                    DurationPickerTarget.Exercise(
                                        exerciseId = ex.id,
                                        title = ex.name,
                                        currentMinutes = ex.targetDurationMinutes,
                                    ),
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * El anillo del reloj: el arco es lo estimado frente al límite (lleno al 100 %) y su color sigue el umbral. Sin límite, solo la pista.
 * En el centro, los minutos estimados en Syne.
 */
@Composable
internal fun RulesClockRing(
    estimatedMinutes: Int,
    limitMinutes: Int?,
    modifier: Modifier = Modifier,
) {
    val status = rulesClockStatusOf(estimatedMinutes, limitMinutes)
    val color = rulesClockColor(status)
    val ratio = if (limitMinutes != null && limitMinutes > 0) {
        (estimatedMinutes.toFloat() / limitMinutes.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val description = when (status) {
        RulesClockStatus.NEUTRAL -> "Estimado $estimatedMinutes minutos, sin límite"
        RulesClockStatus.OK -> "Estimado $estimatedMinutes minutos de $limitMinutes, dentro del límite"
        RulesClockStatus.ENERGY -> "Estimado $estimatedMinutes minutos de $limitMinutes, cerca del límite"
        RulesClockStatus.OVER -> "Estimado $estimatedMinutes minutos de $limitMinutes, por encima del límite"
    }
    Box(
        modifier = modifier.semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 8.dp.toPx()
            val diameter = size.minDimension - stroke
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            val arc = Size(diameter, diameter)
            drawArc(
                color = Color.White.copy(alpha = 0.10f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arc,
                style = Stroke(width = stroke),
            )
            if (status != RulesClockStatus.NEUTRAL) {
                drawArc(
                    color = color,
                    startAngle = -90f,
                    sweepAngle = 360f * ratio,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arc,
                    style = Stroke(width = stroke),
                )
            }
        }
        KpknMetric(
            value = if (estimatedMinutes > 0) "$estimatedMinutes" else "—",
            unit = if (estimatedMinutes > 0) "min" else null,
            style = KpknType.metricM,
            color = color,
        )
    }
}

@Composable
private fun RulesClockStat(
    label: String,
    value: String,
    unit: String?,
    color: Color,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        SheetEyebrow(label)
        KpknMetric(value = value, unit = unit, style = KpknType.metricM, color = color)
    }
}

/** Una sugerencia para entrar en el límite: qué ahorra, por qué, cómo cambia la fatiga y los dos botones (descartar y aplicar). */
@Composable
private fun RulesTimeCoachModule(
    suggestion: TimeCoachSuggestion,
    onDismiss: () -> Unit,
    onApply: () -> Unit,
) {
    KpknModule(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = suggestion.title,
                style = KpknType.bodyStrong,
                color = WizardColors.text,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "−${suggestion.minutesSaved} min",
                style = KpknType.bodyStrong,
                color = WizardColors.done,
            )
        }
        Spacer(Modifier.height(4.dp))
        SheetCaption(suggestion.explanation)
        Spacer(Modifier.height(12.dp))
        SheetTag(
            text = when (suggestion.fatigueDelta) {
                TimeCoachFatigueDelta.LOWER -> "Fatiga ↓"
                TimeCoachFatigueDelta.SIMILAR -> "Fatiga ≈"
                TimeCoachFatigueDelta.HIGHER -> "Fatiga ↑"
            },
        )
        Spacer(Modifier.height(10.dp))
        // A 130 % de letra, dos píldoras en una fila se cortan («Descar…»): «Aplicar» a todo el ancho y «Descartar» debajo.
        KpknPrimaryPill(text = "Aplicar", onClick = onApply, modifier = Modifier.fillMaxWidth())
        KpknTextAction(
            text = "Descartar",
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
            color = WizardColors.textMuted,
        )
    }
}

/** Una celda de tiempo: la cifra en Syne dentro de la celda `inset`; al tocarla abre el selector de duración. */
@Composable
private fun RulesTimeCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onOpen: () -> Unit,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SheetCaption(label)
        KpknNumberCell(
            value = value,
            input = KpknCellInput.Picker(onOpen = onOpen),
            contentDescription = "$label, $value",
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Fila de un ejercicio con su tiempo: nombre a la izquierda, celda de tiempo a la derecha. */
@Composable
private fun RulesTimeExerciseRow(
    name: String,
    minutes: Int?,
    onOpen: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = name,
            style = KpknType.caption,
            color = WizardColors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        KpknNumberCell(
            value = formatDurationMinutes(minutes),
            input = KpknCellInput.Picker(onOpen = onOpen),
            contentDescription = "Tiempo de $name, ${formatDurationMinutes(minutes)}",
            modifier = Modifier.width(104.dp),
        )
    }
}
