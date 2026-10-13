package com.example.kpkn.screens.body.design

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.BodyMetric
import com.example.kpkn.domain.body.BodyHistoryRow
import com.example.kpkn.screens.onboarding.design.WizardBodyFatScale
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardMassUnit
import com.example.kpkn.screens.onboarding.design.WizardWeightRule
import com.example.kpkn.screens.onboarding.design.WizardWeightScale
import com.example.kpkn.screens.onboarding.design.MUSCLE_PERCENT_RANGE
import com.example.kpkn.screens.onboarding.design.formatDecimalEs
import com.example.kpkn.ui.components.KpknAlertDialog
import com.example.kpkn.ui.components.KpknNativeTimePickerDialog
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknChip
import com.example.kpkn.ui.design.KpknMetric
import com.example.kpkn.ui.design.KpknMonthCalendar
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknSecondaryPill
import com.example.kpkn.ui.design.KpknType
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Etiquetas de prueba de la hoja de registro: la hoja, el botón de guardar y el de eliminar. */
const val BODY_RECORD_SHEET_TAG = "body-record-sheet"
const val BODY_RECORD_SAVE_TAG = "body-record-save"
const val BODY_RECORD_DELETE_TAG = "body-record-delete"

/**
 * Hoja de registro de Cuerpo (BODY_V1 §2.8), en `KpknSheet`. Fecha y hora con calendario y reloj, el peso con la regla del wizard en la
 * unidad de Ajustes, el % de grasa y el de músculo con su regla (opcionales), las medidas plegables y «Guardar» como principal.
 *
 * La hoja no guarda nada: escribe en [draft] por [onDraftChange] y la pantalla decide qué hacer en [onSave] y [onDelete]. [source] es la
 * medición que se edita (null si es nueva); con origen Health Connect la hoja es de solo lectura. Sin cifras no se guarda nada: el peso
 * es obligatorio, no se puede registrar en el futuro y una medida fuera de rango bloquea el guardado.
 *
 * @param anchorKg último peso conocido, para situar la regla al empezar. No cuenta como respuesta hasta que el usuario la toca.
 * @param now fecha y hora actuales: límite de la fecha y de la hora elegidas.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BodyRecordSheet(
    draft: BodyRecordDraft,
    onDraftChange: (BodyRecordDraft) -> Unit,
    source: BodyHistoryRow?,
    unit: WizardMassUnit,
    anchorKg: Double?,
    now: LocalDateTime,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val readOnly = source?.fromHealthConnect == true
    val title = when {
        readOnly -> "Medición"
        source == null -> "Nueva medición"
        else -> "Editar medición"
    }
    var calendarOpen by remember { mutableStateOf(false) }
    var timeOpen by remember { mutableStateOf(false) }
    var deleteAsked by remember { mutableStateOf(false) }
    var fatOpen by remember { mutableStateOf(draft.bodyFatPercent != null) }
    var muscleOpen by remember { mutableStateOf(draft.muscleMassPercent != null) }
    var measuresOpen by remember { mutableStateOf(BODY_RECORD_MEASURES.any { draft.valueOf(it) != null }) }
    // Texto de las medidas mientras se escribe: «12,» no se pierde al pasar a número.
    val texts = remember {
        mutableStateMapOf<BodyMetric, String>().apply {
            BODY_RECORD_MEASURES.forEach { put(it, bodyFieldText(draft.valueOf(it))) }
        }
    }
    val errors = BODY_RECORD_MEASURES.associateWith { bodyMeasureError(it, texts[it].orEmpty()) }
    val invalidMeasures = errors.values.any { it != null }
    val future = draft.dateTime.isAfter(now)
    // Sin cambios, no se guarda: una edición debe cambiar algo; una medición nueva ya cambia con el peso.
    val changed = source == null || draft != bodyRecordFrom(source)
    val canSave = !readOnly && draft.weightKg != null && !future && !invalidMeasures && changed
    val date = draft.dateTime.toLocalDate()

    KpknSheet(onDismissRequest = onDismiss, maxHeightFraction = 0.94f) {
        // Cuerpo desplazable: lo que no cabe se desliza; las acciones van fijas al pie de la hoja (fuera de este bloque).
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 4.dp)
                .testTag(BODY_RECORD_SHEET_TAG),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = title, style = KpknType.title, color = WizardColors.text)
                if (readOnly) {
                    Text(
                        text = "Sincronizada con Health Connect. Solo lectura.",
                        style = KpknType.caption,
                        color = WizardColors.textMuted,
                    )
                }
            }

            // Fecha y hora: el día abre el calendario (hasta hoy) y la hora, el reloj.
            if (readOnly) {
                Text(
                    text = "${bodyWeekdayDayEs(date)} · ${bodyTimeEs(draft.dateTime)}",
                    style = KpknType.bodyStrong,
                    color = WizardColors.text,
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KpknChip(
                        text = bodyWeekdayDayEs(date),
                        selected = calendarOpen,
                        onClick = { calendarOpen = !calendarOpen },
                        leadingIcon = KpknLineIcons.Calendar,
                    )
                    KpknChip(
                        text = bodyTimeEs(draft.dateTime),
                        selected = false,
                        onClick = { timeOpen = true },
                        leadingIcon = KpknLineIcons.Clock,
                    )
                }
                if (calendarOpen) {
                    KpknMonthCalendar(
                        selected = date,
                        onSelect = { day ->
                            // El día cambia y la hora se queda como estaba.
                            onDraftChange(draft.copy(dateTime = LocalDateTime.of(day, draft.dateTime.toLocalTime())))
                            calendarOpen = false
                        },
                        today = now.toLocalDate(),
                        maxDate = now.toLocalDate(),
                    )
                }
                if (future) {
                    Text(text = "La hora no puede ser futura.", style = KpknType.caption, color = WizardColors.danger)
                }
            }

            // Peso: la regla del wizard, en la unidad de Ajustes. Sin lectura de origen externo, se puede mover.
            BodySectionLabel("Peso")
            if (readOnly) {
                // Solo lectura: la cifra grande, como en la regla de la edición. Sin dato, «—».
                KpknMetric(
                    value = draft.weightKg?.let { bodyWeightValue(it, unit) } ?: "—",
                    unit = draft.weightKg?.let { unit.code },
                    style = KpknType.metricXL,
                    color = if (draft.weightKg != null) WizardColors.text else WizardColors.textFaint,
                )
            } else {
                WizardWeightRule(
                    unit = unit,
                    valueKg = draft.weightKg ?: anchorKg,
                    onValueChange = { onDraftChange(draft.withValue(BodyMetric.WEIGHT, it)) },
                    min = WizardWeightScale.MIN_KG,
                    max = WizardWeightScale.MAX_KG,
                    emphasized = true,
                )
                if (draft.weightKg == null) {
                    Text(
                        text = "Desliza la regla para elegir el peso.",
                        style = KpknType.caption,
                        color = WizardColors.textMuted,
                    )
                }
            }

            // Porcentajes opcionales: chips para abrirlos y, abiertos, su regla como en el wizard.
            if (readOnly) {
                // Solo los porcentajes que tienen dato: sin dato no se muestra nada.
                draft.bodyFatPercent?.let { BodyRecordReadOnlyLine(label = "% de grasa", value = formatDecimalEs(it, 1), unit = "%") }
                draft.muscleMassPercent?.let { BodyRecordReadOnlyLine(label = "% de músculo", value = formatDecimalEs(it, 1), unit = "%") }
            } else {
                // Un chip por cada porcentaje cerrado: con dos líneas de letra grande, bajan a otra fila y nunca se parten.
                if (!fatOpen || !muscleOpen) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (!fatOpen) {
                            KpknChip(
                                text = "Grasa",
                                selected = false,
                                onClick = { fatOpen = true },
                                leadingIcon = KpknLineIcons.Plus,
                            )
                        }
                        if (!muscleOpen) {
                            KpknChip(
                                text = "Músculo",
                                selected = false,
                                onClick = { muscleOpen = true },
                                leadingIcon = KpknLineIcons.Plus,
                            )
                        }
                    }
                }
                // Abiertos, la regla de cada uno; con los dos, una al lado de la otra.
                if (fatOpen || muscleOpen) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        if (fatOpen) {
                            BodyRecordPercentColumn(
                                name = "Grasa",
                                percent = draft.bodyFatPercent,
                                startPercent = BODY_RECORD_FAT_START_PERCENT,
                                range = WizardBodyFatScale.DEFAULT_RANGE,
                                accessibilityLabel = "Porcentaje de grasa corporal",
                                onRemove = {
                                    fatOpen = false
                                    onDraftChange(draft.withValue(BodyMetric.BODY_FAT_PERCENT, null))
                                },
                                onPercentChange = { onDraftChange(draft.withValue(BodyMetric.BODY_FAT_PERCENT, it)) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (muscleOpen) {
                            BodyRecordPercentColumn(
                                name = "Músculo",
                                percent = draft.muscleMassPercent,
                                startPercent = BODY_RECORD_MUSCLE_START_PERCENT,
                                range = MUSCLE_PERCENT_RANGE,
                                accessibilityLabel = "Porcentaje de músculo",
                                onRemove = {
                                    muscleOpen = false
                                    onDraftChange(draft.withValue(BodyMetric.MUSCLE_MASS_PERCENT, null))
                                },
                                onPercentChange = { onDraftChange(draft.withValue(BodyMetric.MUSCLE_MASS_PERCENT, it)) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            // Medidas: plegadas por defecto; sin ninguna, el bloque se abre al tocarlo.
            if (readOnly) {
                BODY_RECORD_MEASURES.forEach { metric ->
                    draft.valueOf(metric)?.let { value ->
                        BodyRecordReadOnlyLine(label = bodyMeasureLabel(metric), value = formatDecimalEs(value, 1), unit = "cm")
                    }
                }
            } else {
                BodyRecordDisclosure(
                    label = "Medidas",
                    hint = "Opcional",
                    open = measuresOpen,
                    onToggle = { measuresOpen = !measuresOpen },
                )
                if (measuresOpen) {
                    BodyRecordMeasureGrid(
                        texts = texts,
                        errors = errors,
                        onTextChange = { metric, text ->
                            texts[metric] = text
                            onDraftChange(draft.withValue(metric, bodyMeasureValue(metric, text)))
                        },
                    )
                }
                if (invalidMeasures) {
                    Text(
                        text = "Revisa las medidas: deben ser números dentro de rango.",
                        style = KpknType.caption,
                        color = WizardColors.danger,
                    )
                }
            }

        }

        // Acciones fijas al pie de la hoja: en solo lectura solo se cierra; si no, «Guardar» y, si la medición existe, «Eliminar».
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (readOnly) {
                KpknSecondaryPill(text = "Cerrar", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
            } else {
                KpknPrimaryPill(
                    text = "Guardar",
                    onClick = onSave,
                    modifier = Modifier.fillMaxWidth(),
                    tag = BODY_RECORD_SAVE_TAG,
                    enabled = canSave,
                )
                if (source != null) {
                    BodyRecordDeleteAction(onClick = { deleteAsked = true })
                }
            }
        }
    }

    if (timeOpen) {
        KpknNativeTimePickerDialog(
            title = "Hora de la medición",
            initialHour = draft.dateTime.hour,
            initialMinute = draft.dateTime.minute,
            onConfirm = { hour, minute ->
                // Misma hora y minuto: no se toca ni el segundo, así la medición conserva su instante exacto.
                if (hour != draft.dateTime.hour || minute != draft.dateTime.minute) {
                    onDraftChange(draft.copy(dateTime = draft.dateTime.withHour(hour).withMinute(minute).withSecond(0).withNano(0)))
                }
                timeOpen = false
            },
            onDismiss = { timeOpen = false },
        )
    }
    if (deleteAsked) {
        KpknAlertDialog(
            onDismissRequest = { deleteAsked = false },
            title = "¿Eliminar esta medición?",
            text = "Se borran el peso y las medidas de este día y hora. Podrás deshacerlo justo después.",
            confirmLabel = "Eliminar",
            onConfirm = {
                deleteAsked = false
                onDelete()
            },
            dismissLabel = "Cancelar",
            onDismiss = { deleteAsked = false },
        )
    }
}

/** Etiqueta de un bloque de la hoja, en mayúsculas y `textMuted`, como el eyebrow de los módulos. */
@Composable
private fun BodySectionLabel(label: String) {
    Text(
        text = label.uppercase(Locale.forLanguageTag("es")),
        style = KpknType.eyebrow,
        color = WizardColors.textMuted,
    )
}

/** Dato de solo lectura: etiqueta a la izquierda, cifra con su unidad a la derecha. Sin dato, «—». */
@Composable
private fun BodyRecordReadOnlyLine(label: String, value: String?, unit: String) {
    Row(
        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 40.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = KpknType.body, color = WizardColors.textMuted, modifier = Modifier.weight(1f))
        KpknMetric(
            value = value ?: "—",
            unit = if (value != null) unit else null,
            style = KpknType.metricM,
            color = if (value != null) WizardColors.text else WizardColors.textFaint,
        )
    }
}

/** «Eliminar medición»: texto en `danger`, con zona táctil de 48 dp. Pide confirmación en la hoja. */
@Composable
private fun BodyRecordDeleteAction(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .testTag(BODY_RECORD_DELETE_TAG)
            .clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Eliminar medición",
            style = KpknType.action,
            color = WizardColors.danger,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Hora de una medición: «07:40». Compartida con el historial. */
internal fun bodyTimeEs(dateTime: LocalDateTime): String = dateTime.format(DateTimeFormatter.ofPattern("HH:mm"))
