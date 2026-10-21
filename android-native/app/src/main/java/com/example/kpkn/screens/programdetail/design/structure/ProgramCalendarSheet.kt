package com.example.kpkn.screens.programdetail.design.structure

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.KeyDateType
import com.example.kpkn.data.models.ProgramKeyDate
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.screens.programdetail.formatDateRange
import com.example.kpkn.screens.programdetail.formatDayMonth
import com.example.kpkn.screens.programdetail.formatShortDate
import com.example.kpkn.screens.programdetail.keyDateTypeLabel
import com.example.kpkn.screens.programdetail.parseIsoDateExact
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknIconButton
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknShape
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknType
import java.time.LocalDate
import kotlin.math.roundToInt

/** Un bloque de la vista previa: su nombre y su rango de fechas proyectado. */
internal data class ProgramBlockRange(val name: String, val start: LocalDate, val end: LocalDate)

/** Claves de las filas de fecha de la hoja. Solo una puede estar desplegada a la vez; la hoja de render abre una de salida. */
internal const val CALENDAR_ROW_START = "inicio"
internal const val CALENDAR_ROW_COMPETITION = "competicion"
internal const val CALENDAR_ROW_END = "fin"

/** Posición aún no medida de la lista de fechas clave. */
private const val NO_POSITION = Int.MIN_VALUE

/**
 * Hoja «Calendario del programa» (P3a, sustituye a la hoja de fechas con `OutlinedTextField`): inicio, día de la competición y fin, cada
 * uno con el calendario de mes propio; la vista previa de los bloques y los avisos del motor; las otras fechas clave, y «Guardar».
 *
 * Los textos de guardado no cambian: la hoja solo presenta. Con [focusKeyDates], la hoja baja hasta la lista de fechas clave al abrirse
 * (lo pide la baldosa «Fechas clave»). [canSave] y [hasRequiredStart] llegan ya calculados por la pantalla.
 */
@Composable
internal fun ProgramCalendarSheet(
    today: LocalDate,
    timelineStartDate: String,
    competitionDate: String,
    manualEndDate: String,
    projectedEnd: LocalDate?,
    blockRanges: List<ProgramBlockRange>,
    messages: List<String>,
    otherKeyDates: List<ProgramKeyDate>,
    focusKeyDates: Boolean,
    canSave: Boolean,
    hasRequiredStart: Boolean,
    onTimelineStartDateChange: (String) -> Unit,
    onCompetitionDateChange: (String) -> Unit,
    onManualEndDateChange: (String) -> Unit,
    onAddOtherKeyDate: () -> Unit,
    onEditOtherKeyDate: (ProgramKeyDate) -> Unit,
    onDeleteOtherKeyDate: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    initialOpenRow: String? = null,
) {
    val start = parseIsoDateExact(timelineStartDate)
    val competition = parseIsoDateExact(competitionDate)
    val manualEnd = parseIsoDateExact(manualEndDate)
    val reduced = wizardReducedMotion()
    val scroll = rememberScrollState()
    var openRow by rememberSaveable { mutableStateOf(initialOpenRow) }
    // Posiciones en la raíz de la ventana. La altura de la lista dentro del contenido es su posición menos la del contenedor, más el
    // desplazamiento actual: así no hace falta `positionInParent`.
    var containerTop by remember { mutableIntStateOf(0) }
    var keyDatesRootTop by remember { mutableIntStateOf(NO_POSITION) }
    var focused by remember { mutableStateOf(false) }

    LaunchedEffect(focusKeyDates, keyDatesRootTop, containerTop) {
        if (focusKeyDates && !focused && keyDatesRootTop != NO_POSITION) {
            focused = true
            val target = (keyDatesRootTop - containerTop + scroll.value).coerceAtLeast(0)
            if (reduced) scroll.scrollTo(target) else scroll.animateScrollTo(target)
        }
    }

    KpknSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scroll)
                .onGloballyPositioned { containerTop = it.positionInRoot().y.roundToInt() }
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            Text(text = "Calendario del programa", style = KpknType.title, color = WizardColors.text)
            Spacer(Modifier.height(6.dp))
            Text(
                text = "La competición marca su semana entera en el programa.",
                style = KpknType.caption,
                color = WizardColors.textMuted,
            )
            Spacer(Modifier.height(14.dp))

            ProgramDateRow(
                label = "Inicio del programa",
                selected = start,
                today = today,
                expanded = openRow == CALENDAR_ROW_START,
                onToggle = { openRow = toggledRow(openRow, CALENDAR_ROW_START) },
                onPick = { picked ->
                    onTimelineStartDateChange(picked.toString())
                    openRow = null
                },
                onClear = clearAction(start != null) { onTimelineStartDateChange("") },
            )
            ProgramDateRow(
                label = "Día de la competición",
                selected = competition,
                today = today,
                expanded = openRow == CALENDAR_ROW_COMPETITION,
                onToggle = { openRow = toggledRow(openRow, CALENDAR_ROW_COMPETITION) },
                onPick = { picked ->
                    onCompetitionDateChange(picked.toString())
                    openRow = null
                },
                minDate = today,
                onClear = clearAction(competition != null) { onCompetitionDateChange("") },
            )
            ProgramDateRow(
                label = "Fin del programa",
                selected = manualEnd,
                today = today,
                expanded = openRow == CALENDAR_ROW_END,
                onToggle = { openRow = toggledRow(openRow, CALENDAR_ROW_END) },
                onPick = { picked ->
                    onManualEndDateChange(picked.toString())
                    openRow = null
                },
                estimate = projectedEnd?.let { "≈ ${formatShortDate(it)}" },
                emptyValue = "Sin fecha",
                onClear = clearAction(manualEnd != null) { onManualEndDateChange("") },
            )

            Spacer(Modifier.height(20.dp))
            ProgramSectionLabel(text = "Vista previa")
            Spacer(Modifier.height(6.dp))
            if (blockRanges.isEmpty()) {
                Text(
                    text = if (start == null) {
                        "Elige el inicio para ver cuándo empieza cada bloque."
                    } else {
                        "El programa aún no tiene semanas suficientes para proyectar bloques."
                    },
                    style = KpknType.caption,
                    color = WizardColors.textMuted,
                )
            }
            blockRanges.forEach { block ->
                Text(
                    text = "${block.name} · ${formatDateRange(block.start, block.end)}",
                    style = KpknType.caption.copy(fontFeatureSettings = "'tnum' 1"),
                    color = WizardColors.text,
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            }
            messages.forEach { message -> ProgramEngineMessage(text = message) }

            Spacer(Modifier.height(22.dp))
            Column(
                modifier = Modifier.onGloballyPositioned { keyDatesRootTop = it.positionInRoot().y.roundToInt() },
            ) {
                ProgramSectionLabel(text = "Otras fechas clave")
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Exámenes, vacaciones o viajes que cambian tu semana.",
                    style = KpknType.caption,
                    color = WizardColors.textMuted,
                )
                Spacer(Modifier.height(4.dp))
                otherKeyDates.forEach { keyDate ->
                    OtherKeyDateRow(
                        keyDate = keyDate,
                        onEdit = { onEditOtherKeyDate(keyDate) },
                        onDelete = { onDeleteOtherKeyDate(keyDate.id) },
                    )
                }
                KpknTextAction(text = "Añadir fecha clave", onClick = onAddOtherKeyDate)
            }

            Spacer(Modifier.height(20.dp))
            KpknPrimaryPill(
                text = "Guardar calendario",
                onClick = onSave,
                modifier = Modifier.fillMaxWidth(),
                enabled = canSave,
            )
            if (!hasRequiredStart) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Para fijar una competición, elige primero el inicio del programa.",
                    style = KpknType.caption,
                    color = WizardColors.danger,
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** Fila desplegada: si [row] ya estaba abierta se pliega; si no, se despliega esta y se pliega la otra. */
private fun toggledRow(current: String?, row: String): String? = if (current == row) null else row

/** «Quitar» solo cuando hay fecha que quitar; sin fecha, no hay acción. */
private fun clearAction(hasDate: Boolean, onClear: () -> Unit): (() -> Unit)? = if (hasDate) onClear else null

/**
 * Fila de otra fecha clave (56 dp): título en `bodyStrong`, rango en `caption` tabular y el tipo como píldora pequeña. Tocarla edita la
 * fecha; el icono de papelera la elimina.
 */
@Composable
private fun OtherKeyDateRow(keyDate: ProgramKeyDate, onEdit: () -> Unit, onDelete: () -> Unit) {
    val start = parseIsoDateExact(keyDate.startDate)
    val end = parseIsoDateExact(keyDate.endDate)
    val range = when {
        start == null -> "Sin fecha"
        end == null || end == start -> formatDayMonth(start)
        else -> formatDateRange(start, end)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .clickable(role = Role.Button, onClick = onEdit)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = keyDate.title.ifBlank { "Sin nombre" },
                style = KpknType.bodyStrong,
                color = WizardColors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // El tipo va debajo del rango, no en la misma fila: así no se estrecha ni se corta a 130 % de letra.
            Text(
                text = range,
                style = KpknType.caption.copy(fontFeatureSettings = "'tnum' 1"),
                color = WizardColors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            KeyDateTypePill(type = keyDate.type)
        }
        KpknIconButton(
            icon = KpknLineIcons.Trash,
            contentDescription = "Eliminar",
            onClick = onDelete,
            tint = WizardColors.danger,
        )
    }
}

/** Píldora pequeña con el tipo de la fecha clave: solo lectura, con el aspecto del chip de la app. */
@Composable
private fun KeyDateTypePill(type: KeyDateType) {
    Box(
        modifier = Modifier
            .background(KpknSurface.inset, KpknShape.pill)
            .padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        Text(
            text = keyDateTypeLabel(type),
            style = KpknType.caption,
            color = WizardColors.textMuted,
            maxLines = 1,
        )
    }
}
