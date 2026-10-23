package com.example.kpkn.screens.sessioneditor.components.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.Session
import com.example.kpkn.domain.calculations.SessionTimeBreakdown
import com.example.kpkn.domain.auge.DrainReading
import com.example.kpkn.domain.auge.SpinalEvidence
import com.example.kpkn.domain.training.VolumeCalculator
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.sessioneditor.MuscleVolumeRow
import com.example.kpkn.screens.sessioneditor.SessionEditorUiState
import com.example.kpkn.screens.sessioneditor.SessionSubMuscleBreakdownList
import com.example.kpkn.screens.sessioneditor.buildMuscleVolumeRows
import com.example.kpkn.screens.sessioneditor.sessionTimeBreakdownCaption
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.components.rings.KpknRingColors
import com.example.kpkn.ui.components.rings.KpknTowerRings
import com.example.kpkn.ui.components.rings.TowerRingValues
import com.example.kpkn.ui.design.KpknMetric
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknModuleHeader
import com.example.kpkn.ui.design.KpknShape
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknType

/**
 * Pestaña Impacto del asistente (ER1e): la torre de RINGS con sus tres cifras, la duración de la sesión (la misma fuente única), el
 * volumen por músculo en filas con barra (plegado por defecto, como antes) y las calorías estimadas. Mismos datos que el original; solo
 * cambia la piel.
 */
@Composable
internal fun AssistantImpactTab(uiState: SessionEditorUiState) {
    val summary = uiState.augeSummary
    val report = uiState.assistantReport
    val timeBreakdown = uiState.sessionTimeBreakdown
    val energySummary = summary.sessionEnergy
    val session = uiState.session
    val volumeRows = remember(session) { session?.let { buildMuscleVolumeRows(it) }.orEmpty() }
    // Energía = coste del SNC; Músculos = coste muscular; Columna = coste axial (mismo mapeo que en la sesión en vivo).
    // MOT · I2: las cifras salen de la proyección compartida con el motor general, con decimales: cero, «<1» y «sin estimación» se
    // distinguen. Sin proyección (resumen de respaldo) se leen los enteros de siempre.
    val projection = summary.drainProjection
    val columnaReading = projection?.spinalReading ?: DrainReading.of(summary.sessionDrain.spinal.toDouble())
    val musculosReading = projection?.muscularReading ?: DrainReading.of(summary.sessionDrain.muscular.toDouble())
    val energiaReading = projection?.cnsReading ?: DrainReading.of(summary.sessionDrain.cns.toDouble())
    val rings = TowerRingValues(
        columna = columnaReading.ringValue,
        musculos = musculosReading.ringValue,
        energia = energiaReading.ringValue,
    )
    val axialNote = remember(projection, session) { columnAxialNote(projection, session) }

    if (report == null) {
        SheetLoadingSkeleton()
        return
    }

    // 1. RINGS: la torre protagonista, con las tres cifras en Syne.
    KpknModule(modifier = Modifier.fillMaxWidth()) {
        KpknModuleHeader(label = "RINGS de la sesión")
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            KpknTowerRings(
                values = rings,
                modifier = Modifier.size(132.dp),
                animate = false,
            )
            Spacer(Modifier.width(20.dp))
            AssistantRingFigures(
                musculos = musculosReading,
                energia = energiaReading,
                columna = columnaReading,
                modifier = Modifier.weight(1f),
            )
        }
        if (axialNote != null) {
            Spacer(Modifier.height(8.dp))
            SheetCaption(axialNote, color = WizardColors.textFaint)
        }
    }

    // 2. D2: duración y calorías en un solo módulo, lado a lado, con la barra del reparto del tiempo debajo. La duración sale de la misma
    // fuente única que el reloj de Reglas.
    val kcalKnown = energySummary.totalKcal.mid > 0
    KpknModule(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (timeBreakdown != null) {
                Column(modifier = Modifier.weight(1.15f)) {
                    KpknModuleHeader(label = "Duración de la sesión")
                    Spacer(Modifier.height(8.dp))
                    KpknMetric(value = "${timeBreakdown.totalMinutes}", unit = "min", style = KpknType.metricL)
                }
                Box(modifier = Modifier.fillMaxHeight().width(1.dp).background(KpknSurface.divider))
            }
            Column(modifier = Modifier.weight(1f)) {
                KpknModuleHeader(label = "Calorías estimadas")
                Spacer(Modifier.height(8.dp))
                if (kcalKnown) {
                    KpknMetric(value = "${energySummary.totalKcal.mid}", unit = "kcal", style = KpknType.metricL)
                    Spacer(Modifier.height(4.dp))
                    SheetCaption("${energySummary.totalKcal.low}–${energySummary.totalKcal.high}", color = WizardColors.textFaint)
                } else {
                    KpknMetric(value = "—", style = KpknType.metricL, color = WizardColors.textMuted)
                }
            }
        }
        if (timeBreakdown != null) {
            Spacer(Modifier.height(12.dp))
            SessionTimeSegmentBar(timeBreakdown)
            Spacer(Modifier.height(8.dp))
            // El mismo desglose que Reglas › Tiempo, con el calentamiento (ER2b).
            SheetCaption(sessionTimeBreakdownCaption(timeBreakdown))
        }
        // Una sola nota de calorías: la pista de carga si hay estimación, o lo que falta para tenerla.
        val kcalNote = if (kcalKnown) {
            energySummary.notes.firstOrNull {
                it.contains("1RM", ignoreCase = true) || it.contains("carga", ignoreCase = true)
            }
        } else {
            energySummary.notes.firstOrNull {
                it.contains("peso", ignoreCase = true) ||
                    it.contains("1RM", ignoreCase = true) ||
                    it.contains("estimar", ignoreCase = true)
            } ?: "Completa pesos o 1RM en los ejercicios para estimar el gasto calórico"
        }
        if (kcalNote != null) {
            Spacer(Modifier.height(8.dp))
            SheetCaption(kcalNote, color = if (kcalKnown) WizardColors.textMuted else WizardColors.textFaint)
        }
    }

    // 3. Volumen por músculo (colapsado por defecto).
    AssistantVolumeModule(volumeRows = volumeRows, summary = summary, session = session)
}

/** Las tres cifras de la torre: Músculos, Energía y Columna, una por fila, con el color de su anillo y la cifra en `metricM`. */
@Composable
internal fun AssistantRingFigures(
    musculos: DrainReading,
    energia: DrainReading,
    columna: DrainReading,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AssistantRingFigure(name = "Músculos", reading = musculos, color = KpknRingColors.musculos)
        AssistantRingFigure(name = "Energía", reading = energia, color = KpknRingColors.energia)
        AssistantRingFigure(name = "Columna", reading = columna, color = KpknRingColors.columna)
    }
}

@Composable
private fun AssistantRingFigure(name: String, reading: DrainReading, color: Color) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        SheetCaption(text = name, modifier = Modifier.weight(1f), color = WizardColors.textMuted)
        if (reading is DrainReading.Unknown) {
            // Dato desconocido: no se pinta un cero que parezca «sin carga».
            SheetCaption(text = reading.label, color = WizardColors.textMuted)
        } else {
            KpknMetric(value = reading.label, unit = "%", style = KpknType.metricM, color = color)
        }
    }
}

/**
 * Nota bajo las cifras cuando la Columna no se apoya en cargas axiales declaradas: dice de qué ejercicios falta el dato en vez de
 * mostrar un cero. Null si todos los ejercicios de fuerza declaran su carga axial.
 */
internal fun columnAxialNote(
    projection: com.example.kpkn.domain.auge.SessionDrainProjection?,
    session: Session?,
): String? {
    projection ?: return null
    if (!projection.hasWork || projection.spinalEvidence == SpinalEvidence.DECLARED) return null
    val names = projection.axialUnknownExerciseIds.mapNotNull { id ->
        session?.allExercises()?.firstOrNull { it.id == id }?.name
    }.distinct()
    val list = names.take(2).joinToString(", ").let { if (names.size > 2) "$it y otros" else it }
    return when (projection.spinalEvidence) {
        SpinalEvidence.NONE -> "Columna sin estimación: no consta la carga axial de ${list.ifBlank { "estos ejercicios" }}."
        else -> "Columna con datos parciales: no consta la carga axial de ${list.ifBlank { "algunos ejercicios" }}."
    }
}

/** Volumen por músculo: cabecera plegable y, desplegado, una fila por músculo (máximo 12, como antes). */
@Composable
private fun AssistantVolumeModule(
    volumeRows: List<MuscleVolumeRow>,
    summary: com.example.kpkn.screens.sessioneditor.SessionEditorAugeSummary,
    session: Session?,
) {
    var volumeExpanded by rememberSaveable { mutableStateOf(false) }
    KpknModule(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(role = Role.Button) { volumeExpanded = !volumeExpanded },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                SheetEyebrow("Volumen de entreno de la sesión")
                Spacer(Modifier.height(2.dp))
                SheetCaption(
                    text = when {
                        volumeRows.isEmpty() -> "Sin volumen aún"
                        volumeExpanded -> "${volumeRows.size} músculos"
                        else -> "${volumeRows.size} músculos · tocar para ver"
                    },
                    color = WizardColors.textFaint,
                )
            }
            Icon(
                imageVector = if (volumeExpanded) KpknLineIcons.ChevronUp else KpknLineIcons.ChevronDown,
                contentDescription = if (volumeExpanded) "Colapsar volumen" else "Desplegar volumen",
                tint = WizardColors.textMuted,
                modifier = Modifier.size(20.dp),
            )
        }
        if (volumeExpanded) {
            Spacer(Modifier.height(12.dp))
            if (volumeRows.isEmpty()) {
                SheetCaption("Agrega ejercicios a la sesión para ver el volumen por músculo.")
            } else {
                val maxDirect = volumeRows.maxOfOrNull { it.directSets }?.coerceAtLeast(1.0) ?: 1.0
                val maxSecondary = volumeRows.maxOfOrNull { it.secondarySets }?.coerceAtLeast(1.0) ?: 1.0
                val maxStabilizer = volumeRows.maxOfOrNull { it.stabilizerSets }?.coerceAtLeast(1.0) ?: 1.0
                // Sin scroll anidado: el sheet padre ya scrollea (evita jank al abrir).
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    volumeRows.take(12).forEach { row ->
                        AssistantVolumeRow(
                            muscle = row.muscle,
                            directSets = row.directSets,
                            secondarySets = row.secondarySets,
                            stabilizerSets = row.stabilizerSets,
                            intensityLabel = row.intensity.label,
                            maxDirect = maxDirect,
                            maxSecondary = maxSecondary,
                            maxStabilizer = maxStabilizer,
                            energyDrain = summary.muscleDrainPrecise[row.muscle]?.cns
                                ?: (summary.muscleEnergyDrain[row.muscle] ?: 0).toDouble(),
                            spinalDrain = summary.muscleDrainPrecise[row.muscle]?.spinal
                                ?: (summary.muscleSpinalDrain[row.muscle] ?: 0).toDouble(),
                            muscleDrain = summary.muscleDrainPrecise[row.muscle]?.muscular
                                ?: (summary.muscleDrainProjection[row.muscle] ?: 0).toDouble(),
                            session = session,
                        )
                    }
                    if (volumeRows.size > 12) {
                        SheetCaption(
                            "+${volumeRows.size - 12} músculos más en el desglose interno",
                            color = WizardColors.textFaint,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AssistantVolumeRow(
    muscle: String,
    directSets: Double,
    secondarySets: Double,
    stabilizerSets: Double,
    intensityLabel: String,
    maxDirect: Double,
    maxSecondary: Double,
    maxStabilizer: Double,
    energyDrain: Double,
    spinalDrain: Double,
    muscleDrain: Double,
    session: Session?,
) {
    var expanded by remember { mutableStateOf(false) }
    val canonical = VolumeCalculator.normalizeCanonicalMuscleGroup(muscle)
    val hasPortions = canonical in setOf("Deltoides", "Glúteos", "Pectorales")
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 32.dp)
                .then(if (hasPortions) Modifier.clickable(role = Role.Button) { expanded = !expanded } else Modifier),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = muscle,
                style = KpknType.bodyStrong,
                color = WizardColors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (hasPortions) {
                Icon(
                    imageVector = if (expanded) KpknLineIcons.ChevronUp else KpknLineIcons.ChevronDown,
                    contentDescription = null,
                    tint = WizardColors.textMuted,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        // La serie directa lleva el color de Músculos (su anillo); lo indirecto, en neutro.
        AssistantVolumeBar(
            label = "${formatAssistantSets(directSets)} series · Intensidad $intensityLabel",
            progress = (directSets / maxDirect).toFloat().coerceIn(0f, 1f),
            color = KpknRingColors.musculos,
        )
        if (secondarySets > 0.0) {
            AssistantVolumeBar(
                label = "${formatAssistantSets(secondarySets)} series como secundario",
                progress = (secondarySets / maxSecondary).toFloat().coerceIn(0f, 1f),
                color = WizardColors.textMuted,
            )
        }
        if (stabilizerSets > 0.0) {
            AssistantVolumeBar(
                label = "${formatAssistantSets(stabilizerSets)} series como estabilizador",
                progress = (stabilizerSets / maxStabilizer).toFloat().coerceIn(0f, 1f),
                color = WizardColors.textFaint,
            )
        }
        if (expanded && hasPortions && session != null) {
            SessionSubMuscleBreakdownList(
                muscleName = canonical,
                session = session,
                countIndirect = true,
                adjustByIntensity = false,
            )
        }
        // Coste fisiológico de ese volumen: el mismo reparto de anillos que el resumen.
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AssistantDrainChip(label = "Músculo", reading = DrainReading.of(muscleDrain), color = KpknRingColors.musculos)
            AssistantDrainChip(label = "Energía", reading = DrainReading.of(energyDrain), color = KpknRingColors.energia)
            // Un coste de Columna positivo menor de uno sigue visible como «<1 %».
            if (spinalDrain > 0.0) {
                AssistantDrainChip(label = "Columna", reading = DrainReading.of(spinalDrain), color = KpknRingColors.columna)
            }
        }
    }
}

@Composable
private fun AssistantVolumeBar(label: String, progress: Float, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SheetCaption(text = label, color = WizardColors.textMuted)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(KpknShape.pill)
                .background(KpknSurface.inset, KpknShape.pill),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .height(6.dp)
                    .clip(KpknShape.pill)
                    .background(color, KpknShape.pill),
            )
        }
    }
}

@Composable
private fun AssistantDrainChip(label: String, reading: DrainReading, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
        SheetCaption(text = "$label ${reading.label} %", color = WizardColors.textMuted)
    }
}

private fun formatAssistantSets(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else "%.1f".format(value)

/**
 * Barra de 6 dp con el reparto del tiempo de la sesión (preparación, ejecución, descansos y calentamiento), en neutros y el color de
 * Energía para el calentamiento, como en el tablero D2. Cada tramo pesa lo que sus segundos; los vacíos no se pintan.
 */
@Composable
internal fun SessionTimeSegmentBar(breakdown: SessionTimeBreakdown, modifier: Modifier = Modifier) {
    val segments = listOf(
        breakdown.setupSeconds to WizardColors.text.copy(alpha = 0.35f),
        breakdown.executionSeconds to WizardColors.text,
        breakdown.restSeconds to WizardColors.text.copy(alpha = 0.62f),
        breakdown.warmupSeconds to KpknRingColors.energia,
    ).filter { it.first > 0 }
    if (segments.isEmpty()) return
    Row(
        modifier = modifier.fillMaxWidth().height(6.dp).clip(KpknShape.pill),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        segments.forEach { (seconds, color) ->
            Box(modifier = Modifier.weight(seconds.toFloat()).fillMaxHeight().background(color))
        }
    }
}
