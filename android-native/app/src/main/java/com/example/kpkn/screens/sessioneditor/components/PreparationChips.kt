package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.exercises.ReviewedExerciseTechniqueImages
import com.example.kpkn.ui.components.MobilityTechniqueImage
import com.example.kpkn.data.models.MobilityConfig
import com.example.kpkn.data.models.MobilityMode
import com.example.kpkn.data.models.MobilitySeries
import com.example.kpkn.data.models.MobilityUnit
import com.example.kpkn.data.models.WarmupSetDefinition
import com.example.kpkn.domain.workout.WARMUP_EFFECTIVE_THRESHOLD
import com.example.kpkn.domain.workout.recommendedWarmupMax
import com.example.kpkn.domain.workout.warmupBaseLoadKg
import com.example.kpkn.domain.workout.warmupLoadKg
import com.example.kpkn.domain.workout.warmupValidationMessages
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.screens.sessioneditor.EditorMiniField
import com.example.kpkn.screens.sessioneditor.formatEditableNumber
import com.example.kpkn.screens.sessioneditor.formatRestSummary
import com.example.kpkn.screens.sessioneditor.kpknEditorFieldColors
import com.example.kpkn.ui.components.KpknNativeTimePickerDialog
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknSetFormat
import com.example.kpkn.ui.design.KpknType

private val KpknInk = Color(0xFFF2EEE6)
private val KpknInset = Color(0xFF1A1A1A)
private val KpknEnergy = Color(0xFFF7CF73)
private val KpknDanger = Color(0xFFEF6F64)

private fun percentageText(fraction: Double): String =
    formatEditableNumber(fraction * 100.0).replace('.', ',')

private fun kilogramsText(value: Double?): String =
    value?.let { "${formatEditableNumber(it).replace('.', ',')} kg" }.orEmpty()

private fun parseDecimal(raw: String): Double? = raw.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }

private fun normalizePercentage(rawPercentage: Double): Double {
    val asFraction = if (rawPercentage > 1.0) rawPercentage / 100.0 else rawPercentage
    return asFraction.coerceIn(0.1, 0.95)
}

/** Descripción de una dosis de movilidad, como la cuenta el tablero H2/H4. */
internal fun mobilityDoseText(series: MobilitySeries): String {
    val dose = if (mobilityIsTimed(series)) {
        series.durationSeconds?.takeIf { it > 0 }?.let { KpknSetFormat.doseTime(it) } ?: "—"
    } else {
        series.reps?.takeIf { it.isNotBlank() }?.let { "$it reps" } ?: "—"
    }
    return if (series.sets > 1) "${series.sets} × $dose" else dose
}

private fun mobilityIsTimed(series: MobilitySeries): Boolean =
    series.unit == MobilityUnit.SECONDS || (series.unit != MobilityUnit.REPS && (series.durationSeconds ?: 0) > 0)

// ───────────────────────────── fichas ─────────────────────────────

/** Ficha «Añadir» de borde punteado, con la misma medida que las demás. */
@Composable
private fun AddPreparationChip(
    text: String,
    tag: String,
    onClick: () -> Unit,
) {
    val edge = Color.White.copy(alpha = 0.24f)
    Column(
        modifier = Modifier
            .testTag(tag)
            .size(SessionEditorChipMetrics.width, SessionEditorChipMetrics.height)
            .clip(RoundedCornerShape(SessionEditorChipMetrics.radius))
            .clickable(role = Role.Button, onClick = onClick)
            .drawBehind {
                val stroke = 1.dp.toPx()
                drawRoundRect(
                    color = edge,
                    topLeft = Offset(stroke / 2f, stroke / 2f),
                    size = Size(size.width - stroke, size.height - stroke),
                    cornerRadius = CornerRadius(SessionEditorChipMetrics.radius.toPx()),
                    style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))),
                )
            }
            .padding(horizontal = 4.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = KpknLineIcons.Plus,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.62f),
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = text,
            color = Color.White.copy(alpha = 0.72f),
            fontSize = 11.sp,
            lineHeight = 16.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Botón de descanso común de las aproximaciones (H1): aplica a las filas que comparten el descanso más frecuente. */
@Composable
internal fun WarmupCommonRestButton(
    sets: List<WarmupSetDefinition>,
    onUpdate: (String, (WarmupSetDefinition) -> WarmupSetDefinition) -> Unit,
) {
    if (sets.isEmpty()) return
    val common = remember(sets) {
        sets.groupingBy { it.restBetween ?: 60 }.eachCount().maxByOrNull { it.value }?.key ?: 60
    }
    var showPicker by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .heightIn(min = SessionEditorChipMetrics.minTouchTarget)
            .clip(RoundedCornerShape(24.dp))
            .background(KpknInset)
            .clickable(role = Role.Button) { showPicker = true }
            .padding(horizontal = 10.dp)
            .testTag("warmup-common-rest"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = KpknLineIcons.Clock,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.62f),
            modifier = Modifier.size(15.dp),
        )
        Text(
            text = formatRestSummary(common),
            style = KpknType.chip,
            color = KpknInk,
            maxLines = 1,
        )
    }
    if (showPicker) {
        KpknNativeTimePickerDialog(
            title = "Descanso común de aproximaciones",
            initialHour = (common / 60).coerceIn(0, 23),
            initialMinute = (common % 60).coerceIn(0, 59),
            hint = "Minutos : segundos",
            onConfirm = { minutes, seconds ->
                val target = (minutes * 60 + seconds).coerceAtLeast(0)
                sets.filter { (it.restBetween ?: 60) == common }
                    .forEach { row -> onUpdate(row.id) { it.copy(restBetween = target) } }
                showPicker = false
            },
            onDismiss = { showPicker = false },
        )
    }
}

/** Tiempo global de movilidad en la cabecera del bloque (H2/B6). */
@Composable
internal fun MobilityGlobalTimeButton(
    mobilityConfig: MobilityConfig?,
    onUpdateConfig: (MobilityConfig) -> Unit,
) {
    var showTimer by remember { mutableStateOf(false) }
    val totalMinutes = (mobilityConfig?.totalMinutes ?: 0).coerceAtLeast(0)
    Row(
        modifier = Modifier
            .heightIn(min = SessionEditorChipMetrics.minTouchTarget)
            .clip(RoundedCornerShape(24.dp))
            .background(KpknInset)
            .clickable(role = Role.Button) { showTimer = true }
            .padding(horizontal = 10.dp)
            .testTag("mobility-global-time"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = KpknLineIcons.Clock,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.62f),
            modifier = Modifier.size(15.dp),
        )
        Text(
            text = if (totalMinutes > 0) "Total · $totalMinutes min" else "Definir tiempo",
            style = KpknType.chip,
            color = KpknInk,
            maxLines = 1,
        )
    }
    if (showTimer) {
        KpknNativeTimePickerDialog(
            title = "Tiempo de movilidad",
            initialHour = (totalMinutes / 60).coerceIn(0, 23),
            initialMinute = (totalMinutes % 60).coerceIn(0, 59),
            hint = "Horas : minutos",
            onConfirm = { hour, minute ->
                onUpdateConfig(
                    MobilityConfig(
                        mode = mobilityConfig?.mode ?: MobilityMode.ENFOCADO,
                        totalMinutes = (hour * 60 + minute).coerceAtLeast(1),
                    ),
                )
                showTimer = false
            },
            onDismiss = { showTimer = false },
        )
    }
}

// ───────────────────────────── H1 · aproximación ─────────────────────────────

/**
 * Aproximaciones del ejercicio desplegado (H1): una ficha por aproximación con su porcentaje y su carga, su descanso
 * cuando es una excepción, la ficha «Añadir» y, debajo, el foco con porcentaje, kg, reps, descanso y quitar. Todo vive
 * dentro del ejercicio; no hay controles sueltos fuera.
 *
 * @param workingWeightKg peso de la serie efectiva objetivo: la base real de la aproximación.
 * @param capacityKg capacidad (1RM) que solo se usa si esa serie no tiene peso de trabajo.
 */
@Composable
internal fun WarmupPreparationChips(
    exerciseId: String,
    sets: List<WarmupSetDefinition>,
    workingWeightKg: Double?,
    capacityKg: Double?,
    effectiveSetCount: Int,
    onUpdate: (String, (WarmupSetDefinition) -> WarmupSetDefinition) -> Unit,
    onRemove: (String) -> Unit,
    onAdd: () -> Unit,
    initiallyExpandedId: String? = null,
) {
    var expandedId by rememberSaveable(exerciseId) { mutableStateOf(initiallyExpandedId) }
    val ordered = sets
    val commonRest = remember(sets) {
        sets.groupingBy { it.restBetween ?: 60 }.eachCount().maxByOrNull { it.value }?.key ?: 60
    }

    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(SessionEditorChipMetrics.gap),
        contentPadding = PaddingValues(0.dp),
    ) {
        items(ordered, key = { it.id }) { warmup ->
            val index = ordered.indexOfFirst { it.id == warmup.id }
            val fraction = normalizePercentage(warmup.percentageOfWorkingWeight)
            val rest = warmup.restBetween ?: 60
            val suffix = if (rest != commonRest) " · ${formatRestSummary(rest)}" else ""
            val load = warmupLoadKg(fraction, workingWeightKg, capacityKg)
            EditorPreparationChip(
                label = "A${index + 1}$suffix",
                value = "${percentageText(fraction)} %",
                detail = if (load != null) {
                    "${warmup.targetReps} × ${formatEditableNumber(load).replace('.', ',')} kg"
                } else {
                    "${warmup.targetReps} reps"
                },
                valueColor = if (fraction >= WARMUP_EFFECTIVE_THRESHOLD) KpknDanger else KpknInk,
                selected = warmup.id == expandedId,
                tag = "warmup-chip-${warmup.id}",
                onClick = { expandedId = if (expandedId == warmup.id) null else warmup.id },
            )
        }
        item {
            AddPreparationChip(text = "Añadir", tag = "warmup-chip-add", onClick = onAdd)
        }
    }

    val focused = ordered.firstOrNull { it.id == expandedId }
    if (focused == null) {
        Text(
            text = "Sin aproximaciones abiertas. Toca una ficha para ajustar su carga.",
            style = KpknType.caption,
            color = WizardColors.textMuted,
            modifier = Modifier.padding(top = 8.dp),
        )
    } else {
        val focusedIndex = ordered.indexOfFirst { it.id == focused.id }
        WarmupFocusPanel(
            warmup = focused,
            index = focusedIndex,
            count = ordered.size,
            workingWeightKg = workingWeightKg,
            capacityKg = capacityKg,
            onUpdate = onUpdate,
            onRemove = onRemove,
            onPrevious = if (focusedIndex > 0) {
                { expandedId = ordered[focusedIndex - 1].id }
            } else null,
            onNext = if (focusedIndex < ordered.lastIndex) {
                { expandedId = ordered[focusedIndex + 1].id }
            } else null,
            onClose = { expandedId = null },
        )
    }

    warmupValidationMessages(sets, effectiveSetCount).forEach { message ->
        Text(
            text = message,
            style = KpknType.caption,
            color = KpknDanger,
            modifier = Modifier
                .padding(top = 2.dp)
                .testTag("warmup-warning"),
        )
    }
    Text(
        text = "Máximo recomendado: ${recommendedWarmupMax(effectiveSetCount)} aproximaciones para $effectiveSetCount series efectivas.",
        style = KpknType.caption,
        color = WizardColors.textMuted,
        modifier = Modifier.testTag("warmup-max-recommended"),
    )
}

@Composable
private fun WarmupFocusPanel(
    warmup: WarmupSetDefinition,
    index: Int,
    count: Int,
    workingWeightKg: Double?,
    capacityKg: Double?,
    onUpdate: (String, (WarmupSetDefinition) -> WarmupSetDefinition) -> Unit,
    onRemove: (String) -> Unit,
    onPrevious: (() -> Unit)?,
    onNext: (() -> Unit)?,
    onClose: () -> Unit,
) {
    val fraction = normalizePercentage(warmup.percentageOfWorkingWeight)
    val suggestedLoad = warmupLoadKg(fraction, workingWeightKg, capacityKg)
    val basis = warmupBaseLoadKg(workingWeightKg, capacityKg)
    var showRestPicker by remember(warmup.id) { mutableStateOf(false) }
    val restSeconds = warmup.restBetween ?: 60
    val shape = RoundedCornerShape(22.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .clip(shape)
            .background(Color(0xFF0E0E0E))
            .border(1.dp, Color.White.copy(alpha = 0.10f), shape)
            .padding(12.dp)
            .testTag("warmup-focus"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Aproximación ${index + 1}",
                    style = KpknType.bodyStrong,
                    color = KpknInk,
                )
                Text(
                    text = if (count == 1) "1 aproximación" else "$count aproximaciones",
                    style = KpknType.caption,
                    color = WizardColors.textMuted,
                )
            }
            PreparationNavButton(
                icon = KpknLineIcons.ChevronLeft,
                contentDescription = "Aproximación anterior",
                enabled = onPrevious != null,
                onClick = { onPrevious?.invoke() },
            )
            PreparationNavButton(
                icon = KpknLineIcons.ChevronRight,
                contentDescription = "Aproximación siguiente",
                enabled = onNext != null,
                onClick = { onNext?.invoke() },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            EditorMiniField(
                label = "Carga (%)",
                value = percentageText(fraction),
                stateKey = "warmup-focus-percent-${warmup.id}",
                keyboardType = KeyboardType.Decimal,
                modifier = Modifier
                    .weight(1f)
                    .testTag("warmup-focus-percent"),
            ) { input ->
                parseDecimal(input)?.let { value ->
                    onUpdate(warmup.id) { it.copy(percentageOfWorkingWeight = (value / 100.0).coerceIn(0.1, 0.95)) }
                }
            }
            EditorMiniField(
                label = "Reps",
                value = warmup.targetReps.toString(),
                stateKey = "warmup-focus-reps-${warmup.id}",
                keyboardType = KeyboardType.Number,
                modifier = Modifier
                    .weight(1f)
                    .testTag("warmup-focus-reps"),
            ) { input ->
                input.trim().toIntOrNull()?.let { value ->
                    onUpdate(warmup.id) { it.copy(targetReps = value.coerceAtLeast(1)) }
                }
            }
            EditorMiniField(
                label = "Kg",
                value = suggestedLoad?.let { formatEditableNumber(it).replace('.', ',') }.orEmpty(),
                stateKey = "warmup-focus-kg-${warmup.id}",
                keyboardType = KeyboardType.Decimal,
                modifier = Modifier
                    .weight(1f)
                    .testTag("warmup-focus-kg"),
            ) { input ->
                val kg = parseDecimal(input)
                if (kg != null && kg > 0.0 && basis != null) {
                    onUpdate(warmup.id) {
                        it.copy(percentageOfWorkingWeight = (kg / basis).coerceIn(0.1, 0.95))
                    }
                }
            }
        }
        Slider(
            value = (fraction * 100.0).toFloat(),
            onValueChange = { value ->
                onUpdate(warmup.id) { it.copy(percentageOfWorkingWeight = (value.toDouble() / 100.0).coerceIn(0.1, 0.95)) }
            },
            valueRange = 10f..95f,
            colors = SliderDefaults.colors(
                thumbColor = KpknEnergy,
                activeTrackColor = KpknEnergy,
                inactiveTrackColor = Color.White.copy(alpha = 0.16f),
            ),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = SessionEditorChipMetrics.minTouchTarget)
                .testTag("warmup-focus-slider"),
        )
        Text(
            text = suggestedLoad?.let { "Sugerida: ${kilogramsText(it)} · entre 10 % y 95 % · al menos 1 repetición" }
                ?: "${percentageText(fraction)} % de la carga de la primera serie efectiva · entre 10 % y 95 %",
            style = KpknType.caption,
            color = WizardColors.textMuted,
        )
        if (fraction >= WARMUP_EFFECTIVE_THRESHOLD) {
            Text(
                text = "Una aproximación al 85 % o más puede comportarse como una serie efectiva; revisa su carga y repeticiones.",
                style = KpknType.caption,
                color = KpknDanger,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = SessionEditorChipMetrics.minTouchTarget)
                .clip(RoundedCornerShape(12.dp))
                .background(KpknInset)
                .clickable(role = Role.Button) { showRestPicker = true }
                .padding(horizontal = 10.dp)
                .testTag("warmup-focus-rest"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = KpknLineIcons.Clock,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.62f),
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = "Descanso · ${formatRestSummary(restSeconds)}",
                style = KpknType.caption,
                color = KpknInk,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FocusAction(text = "Quitar", color = KpknDanger, tag = "warmup-focus-remove") { onRemove(warmup.id) }
            FocusAction(text = "Cerrar", color = KpknInk, tag = "warmup-focus-close", onClick = onClose)
            FocusAction(text = "Listo", color = Color(0xFF0B0B0B), fill = KpknInk, tag = "warmup-focus-done", onClick = onClose)
        }
    }
    if (showRestPicker) {
        KpknNativeTimePickerDialog(
            title = "Descanso de aproximación",
            initialHour = (restSeconds / 60).coerceIn(0, 23),
            initialMinute = (restSeconds % 60).coerceIn(0, 59),
            hint = "Minutos : segundos",
            onConfirm = { minutes, seconds ->
                onUpdate(warmup.id) { it.copy(restBetween = (minutes * 60 + seconds).coerceAtLeast(0)) }
                showRestPicker = false
            },
            onDismiss = { showRestPicker = false },
        )
    }
}

/** Flecha de 48 dp del foco de preparación; deshabilitada cuando no hay anterior o siguiente. */
@Composable
private fun PreparationNavButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(SessionEditorChipMetrics.minTouchTarget)
            .clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) KpknInk else WizardColors.textFaint,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** Acción de 48 dp del foco (Quitar · Cerrar · Listo). */
@Composable
private fun RowScope.FocusAction(
    text: String,
    color: Color,
    tag: String,
    fill: Color = Color.Transparent,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .weight(1f)
            .heightIn(min = SessionEditorChipMetrics.minTouchTarget)
            .clip(RoundedCornerShape(24.dp))
            .background(fill)
            .border(1.dp, Color.White.copy(alpha = if (fill == Color.Transparent) 0.12f else 0f), RoundedCornerShape(24.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 6.dp)
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = KpknType.chip,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ───────────────────────────── H2/H4 · movilidad ─────────────────────────────

/**
 * Movilidad del ejercicio desplegado (H2): fichas de 85 × 76 con su dosis, la ficha «Añadir» que abre el catálogo
 * (H3) y el foco de la dosis (H4) dentro del propio ejercicio: tiempo o repeticiones, series, notas, reordenar y quitar.
 */
@Composable
internal fun MobilityPreparationChips(
    exerciseId: String,
    series: List<MobilitySeries>,
    onUpdate: (String, (MobilitySeries) -> MobilitySeries) -> Unit,
    onMove: (String, Int) -> Unit,
    onRemove: (String) -> Unit,
    onAdd: () -> Unit,
    initiallyExpandedId: String? = null,
) {
    var expandedId by rememberSaveable(exerciseId) { mutableStateOf(initiallyExpandedId) }

    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(SessionEditorChipMetrics.gap),
        contentPadding = PaddingValues(0.dp),
    ) {
        items(series, key = { it.id }) { mobility ->
            val index = series.indexOfFirst { it.id == mobility.id }
            EditorPreparationChip(
                label = "M${index + 1}",
                value = shortMobilityName(mobility.name),
                detail = mobilityDoseText(mobility),
                valueColor = KpknEnergy,
                selected = mobility.id == expandedId,
                tag = "mobility-chip-${mobility.id}",
                description = "${mobility.name}, ${mobilityDoseText(mobility)}",
                content = {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        MobilityArt(mobility, 20.dp, "chip")
                        Text("M${index + 1}", color = WizardColors.textMuted, fontSize = 10.sp, lineHeight = 12.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(shortMobilityName(mobility.name), color = KpknInk, fontFamily = WizardFonts.body,
                        fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 15.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(mobilityDoseText(mobility), color = KpknEnergy, fontFamily = WizardFonts.display,
                        fontWeight = FontWeight.Bold, fontSize = 12.sp, lineHeight = 17.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                onClick = { expandedId = if (expandedId == mobility.id) null else mobility.id },
            )
        }
        item {
            AddPreparationChip(text = "Añadir", tag = "mobility-chip-add", onClick = onAdd)
        }
    }

    if (series.isNotEmpty()) {
        Text(
            text = mobilityTotalSummary(series),
            style = KpknType.caption,
            color = WizardColors.textMuted,
            modifier = Modifier
                .padding(top = 6.dp)
                .testTag("mobility-total-summary"),
        )
    }

    val focused = series.firstOrNull { it.id == expandedId }
    if (focused == null) {
        Text(
            text = if (series.isEmpty()) {
                "Sin movimientos. Elige uno del catálogo."
            } else {
                "Toca una ficha para ajustar su dosis."
            },
            style = KpknType.caption,
            color = WizardColors.textMuted,
            modifier = Modifier.padding(top = 8.dp),
        )
    } else {
        val focusedIndex = series.indexOfFirst { it.id == focused.id }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0xFF0E0E0E))
                .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(22.dp))
                .padding(12.dp)
                .testTag("mobility-focus"),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Movimiento ${focusedIndex + 1}",
                        style = KpknType.bodyStrong,
                        color = KpknInk,
                    )
                    Text(
                        text = if (series.size == 1) "1 movimiento" else "${series.size} movimientos",
                        style = KpknType.caption,
                        color = WizardColors.textMuted,
                    )
                }
                PreparationNavButton(
                    icon = KpknLineIcons.ChevronLeft,
                    contentDescription = "Movimiento anterior",
                    enabled = focusedIndex > 0,
                    onClick = { if (focusedIndex > 0) expandedId = series[focusedIndex - 1].id },
                )
                PreparationNavButton(
                    icon = KpknLineIcons.ChevronRight,
                    contentDescription = "Movimiento siguiente",
                    enabled = focusedIndex < series.lastIndex,
                    onClick = {
                        if (focusedIndex < series.lastIndex) expandedId = series[focusedIndex + 1].id
                    },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(KpknInset),
                    contentAlignment = Alignment.Center,
                ) {
                    MobilityArt(focused, 36.dp, "focus")
                }
                Text(
                    text = focused.name,
                    style = KpknType.bodyStrong,
                    color = KpknInk,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActiveChipButton(
                    text = "↑ Subir",
                    enabled = focusedIndex > 0,
                    tag = "mobility-move-up",
                ) { onMove(focused.id, -1) }
                ActiveChipButton(
                    text = "↓ Bajar",
                    enabled = focusedIndex < series.lastIndex,
                    tag = "mobility-move-down",
                ) { onMove(focused.id, 1) }
            }
            MobilityDoseEditor(
                movementId = focused.id,
                name = focused.name,
                series = focused,
                onApply = { updated ->
                    onUpdate(focused.id) { updated }
                },
                onRemove = { onRemove(focused.id) },
                onCancel = { expandedId = null },
                showRemove = true,
                showName = false,
            )
        }
    }
}

/** Solo identidades guardadas eligen arte revisado. Un movimiento sin correspondencia conserva el símbolo. */
@Composable
private fun MobilityArt(mobility: MobilitySeries, size: Dp, placement: String) {
    val identity = ReviewedExerciseTechniqueImages.mobilityMovementId(
        mobility.catalogConfigurationId, mobility.exerciseDbId, mobility.id,
    )
    val reviewed = remember(identity) { ReviewedExerciseTechniqueImages.mobilityImage(identity) != null }
    if (reviewed) {
        MobilityTechniqueImage(identity, contentDescription = "", modifier = Modifier.size(size)
            .testTag("mobility-$placement-reviewed-art-${mobility.id}"))
    } else {
        Icon(KpknLineIcons.Joint, contentDescription = null, tint = KpknInk, modifier = Modifier.size(size)
            .testTag("mobility-$placement-placeholder-${mobility.id}"))
    }
}

/** Resumen del bloque de movilidad: movimientos, tiempo de trabajo y repeticiones totales. */
internal fun mobilityTotalSummary(series: List<MobilitySeries>): String {
    if (series.isEmpty()) return ""
    val timedSeconds = series.sumOf { if (mobilityIsTimed(it)) (it.durationSeconds ?: 0) * it.sets else 0 }
    val repetitions = series.sumOf {
        if (mobilityIsTimed(it)) 0 else (it.reps?.trim()?.toIntOrNull() ?: 0) * it.sets
    }
    val parts = mutableListOf(if (series.size == 1) "1 movimiento" else "${series.size} movimientos")
    if (timedSeconds > 0) parts += "${KpknSetFormat.doseTime(timedSeconds)} de trabajo"
    if (repetitions > 0) parts += "$repetitions reps"
    return parts.joinToString(" · ")
}

/** Nombre corto de la ficha (H2): sin el prefijo «Movilidad de» ni artículos que no distinguen un movimiento de otro. */
private fun shortMobilityName(name: String): String {
    val cleaned = name.trim()
        .removePrefix("Movilidad de ")
        .removePrefix("Movilidad ")
        .removePrefix("Estiramiento de ")
        .removePrefix("Estiramiento ")
    val first = cleaned.substringBefore(' ').trim().ifEmpty { name.trim() }
    return first.replaceFirstChar { it.uppercase() }
}

@Composable
private fun RowScope.ActiveChipButton(
    text: String,
    enabled: Boolean,
    tag: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .weight(1f)
            .heightIn(min = SessionEditorChipMetrics.minTouchTarget)
            .clip(RoundedCornerShape(24.dp))
            .background(KpknInset)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 8.dp)
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = KpknType.chip,
            color = if (enabled) KpknInk else WizardColors.textFaint,
            maxLines = 1,
        )
    }
}

/**
 * H4 · dosis de movilidad: por tiempo o por repeticiones, con series y notas. Devuelve la serie completa lista para
 * guardar; el panel de H2 y la hoja de H4 usan la misma pieza.
 */
@Composable
internal fun MobilityDoseEditor(
    movementId: String,
    name: String,
    series: MobilitySeries,
    onApply: (MobilitySeries) -> Unit,
    onRemove: (() -> Unit)? = null,
    onCancel: (() -> Unit)? = null,
    showRemove: Boolean = false,
    showName: Boolean = true,
    modifier: Modifier = Modifier,
) {
    var timed by remember(movementId) { mutableStateOf(mobilityIsTimed(series)) }
    var time by remember(movementId) { mutableStateOf((series.durationSeconds ?: 45).toString()) }
    var reps by remember(movementId) { mutableStateOf(series.reps?.takeIf { it.isNotBlank() } ?: "10") }
    var sets by remember(movementId) { mutableStateOf((series.sets.coerceAtLeast(1)).toString()) }
    var notes by remember(movementId) { mutableStateOf(series.notes.orEmpty()) }
    var error by remember(movementId) { mutableStateOf<String?>(null) }
    val setsValue = sets.trim().toIntOrNull()
    val summary = buildString {
        if ((setsValue ?: 1) > 1) append("$setsValue × ")
        append(if (timed) "${time.trim().ifBlank { "—" }} s" else "${reps.trim().ifBlank { "—" }} reps")
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (name.isNotBlank() && showName) {
            Text(name, style = KpknType.bodyStrong, color = KpknInk, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DoseModeButton(
                text = "Tiempo",
                selected = timed,
                tag = "mobility-dose-time",
                onClick = { timed = true; error = null },
            )
            DoseModeButton(
                text = "Reps",
                selected = !timed,
                tag = "mobility-dose-reps",
                onClick = { timed = false; error = null },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            if (timed) {
                EditorMiniField(
                    label = "Tiempo (s)",
                    value = time,
                    stateKey = "mobility-dose-time-$movementId",
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("mobility-dose-time-field"),
                ) { input ->
                    time = input.filter { it.isDigit() }
                    error = null
                }
            } else {
                EditorMiniField(
                    label = "Repeticiones",
                    value = reps,
                    stateKey = "mobility-dose-reps-$movementId",
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("mobility-dose-reps-field"),
                ) { input ->
                    reps = input.filter { it.isDigit() }
                    error = null
                }
            }
            EditorMiniField(
                label = "Series",
                value = sets,
                stateKey = "mobility-dose-sets-$movementId",
                keyboardType = KeyboardType.Number,
                modifier = Modifier
                    .weight(1f)
                    .testTag("mobility-dose-sets-field"),
            ) { input ->
                sets = input.filter { it.isDigit() }
                error = null
            }
        }
        Text(
            text = summary,
            style = KpknType.bodyStrong,
            color = KpknEnergy,
            modifier = Modifier.testTag("mobility-dose-summary"),
        )
        OutlinedTextField(
            value = notes,
            onValueChange = {
                notes = it
                error = null
            },
            label = { Text("Notas") },
            minLines = 2,
            maxLines = 3,
            shape = RoundedCornerShape(16.dp),
            textStyle = KpknType.caption.copy(color = KpknInk),
            colors = kpknEditorFieldColors(),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 68.dp)
                .testTag("mobility-dose-notes"),
        )
        error?.let {
            Text(it, style = KpknType.caption, color = KpknDanger)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (showRemove && onRemove != null) {
                FocusAction(text = "Quitar", color = KpknDanger, tag = "mobility-dose-remove", onClick = onRemove)
            }
            if (onCancel != null) {
                FocusAction(text = "Cerrar", color = KpknInk, tag = "mobility-dose-cancel", onClick = onCancel)
            }
            FocusAction(
                text = "Guardar",
                color = Color(0xFF0B0B0B),
                fill = KpknInk,
                tag = "mobility-dose-save",
                onClick = {
                    val n = sets.trim().toIntOrNull()
                    val seconds = time.trim().toIntOrNull()
                    val repetitions = reps.trim().toIntOrNull()
                    when {
                        n == null || n < 1 -> error = "Indica al menos 1 serie."
                        timed && (seconds == null || seconds < 1) -> error = "Indica un tiempo de al menos 1 segundo."
                        !timed && (repetitions == null || repetitions < 1) -> error = "Indica las repeticiones."
                        else -> {
                            error = null
                            onApply(
                                series.copy(
                                    sets = n,
                                    unit = if (timed) MobilityUnit.SECONDS else MobilityUnit.REPS,
                                    durationSeconds = if (timed) seconds ?: 0 else null,
                                    reps = if (timed) null else reps.trim(),
                                    notes = notes.trim().ifBlank { null },
                                ),
                            )
                        }
                    }
                },
            )
        }
        if (!timed && reps.isNotBlank()) {
            Text(
                text = "Dosis por repeticiones: $reps por serie.",
                style = KpknType.caption,
                color = WizardColors.textMuted,
            )
        }
    }
}

@Composable
private fun RowScope.DoseModeButton(
    text: String,
    selected: Boolean,
    tag: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .weight(1f)
            .heightIn(min = SessionEditorChipMetrics.minTouchTarget)
            .clip(RoundedCornerShape(24.dp))
            .background(if (selected) KpknInk else KpknInset)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 8.dp)
            .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = KpknType.chip,
            color = if (selected) Color(0xFF0B0B0B) else KpknInk,
            maxLines = 1,
        )
    }
}

// ───────────────────────────── utilidades de prueba/render ─────────────────────────────

/** Ficha de serie efectiva de muestra para las hojas de render (S1–S4 del tablero). */
@Composable
internal fun EffectiveSeriesChipRow(
    rows: List<Triple<String, String, String>>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(SessionEditorChipMetrics.gap),
    ) {
        rows.forEach { (label, load, dose) ->
            EffectiveSeriesChip(label = label, load = load, dose = dose)
        }
    }
}

/** Espacio en blanco de la medida de una ficha, para las pruebas de tamaño. */
@Composable
internal fun PreparationChipSpacer() {
    Spacer(Modifier.size(SessionEditorChipMetrics.width, SessionEditorChipMetrics.height))
}
