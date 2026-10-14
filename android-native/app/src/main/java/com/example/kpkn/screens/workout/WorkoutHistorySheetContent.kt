package com.example.kpkn.screens.workout

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.domain.calculations.calculateHybrid1RM
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.workout.design.WorkoutStructureDivider
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknEmptyState
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknModuleHeader
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknType
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Español de Chile para los meses y los días de la semana del historial. */
private val historyLocale: Locale = Locale.Builder().setLanguage("es").setRegion("CL").build()

/** Las series de una sesión en tabular, como «80 × 8 · 80 × 8 · 82,5 × 6». Sin tabular, las cifras bailan al desplazarse. */
private val HistorySetsStyle = KpknType.caption.copy(fontFeatureSettings = "'tnum' 1")

/**
 * Historial del ejercicio (hoja «Historial» de la sesión en vivo): sesiones agrupadas por semana o mes, cada grupo en un módulo con su
 * cabecera y «Mostrar»/«Ocultar». Una fila por sesión: la fecha larga, la etiqueta usada y sus series; la mejor serie (por 1RM estimado)
 * va marcada con su «Mejor intento». Mismos datos que antes; sin dato, no se pinta nada.
 */
@Composable
internal fun ExerciseHistoryContent(
    history: List<ExerciseHistoryEntry>,
    activeTag: String? = null,
) {
    if (history.isEmpty()) {
        KpknEmptyState(
            icon = KpknLineIcons.History,
            title = "Sin historial registrado",
            body = null,
        )
        return
    }

    val grouped = remember(history) {
        history.groupBy { entry ->
            val date = try { LocalDate.parse(entry.date.take(10)) } catch (e: Exception) { LocalDate.now() }
            val now = LocalDate.now()
            when {
                date.isAfter(now.minusWeeks(1)) -> "Esta semana"
                date.isAfter(now.minusWeeks(2)) -> "Semana pasada"
                date.isAfter(now.withDayOfMonth(1)) -> "Este mes"
                else -> {
                    val month = date.month.getDisplayName(java.time.format.TextStyle.FULL, historyLocale)
                        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(historyLocale) else it.toString() }
                    "$month ${date.year}"
                }
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        grouped.forEach { (label, entries) ->
            var expanded by rememberSaveable(label) { mutableStateOf(label == "Esta semana" || label == "Semana pasada") }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                KpknModuleHeader(
                    label = label,
                    action = {
                        KpknTextAction(
                            text = if (expanded) "Ocultar" else "Mostrar",
                            onClick = { expanded = !expanded },
                        )
                    },
                )
                AnimatedVisibility(visible = expanded) {
                    KpknModule(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                        entries.forEachIndexed { index, entry ->
                            if (index > 0) WorkoutStructureDivider()
                            HistoryEntryRow(entry = entry, activeTag = activeTag)
                        }
                    }
                }
            }
        }
    }
}

/** Una sesión del historial: fecha larga y etiqueta en la primera línea; sus series y la nota debajo. */
@Composable
internal fun HistoryEntryRow(
    entry: ExerciseHistoryEntry,
    activeTag: String?,
) {
    val isTagMatch = activeTag != null && entry.tag == activeTag
    val workSets = entry.sets.filter { !it.isWarmup }
    val best = bestHistorySetIndex(workSets)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = historyDateLabel(entry.date),
                style = KpknType.bodyStrong,
                color = WizardColors.text,
                modifier = Modifier.weight(1f),
            )
            entry.tag?.let { tag ->
                Text(
                    text = tag,
                    style = KpknType.caption,
                    color = if (isTagMatch) WizardColors.text else WizardColors.textMuted,
                    modifier = Modifier
                        .background(KpknSurface.inset, RoundedCornerShape(999.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
        if (workSets.isEmpty()) {
            Text(text = "Sin series de trabajo", style = KpknType.caption, color = WizardColors.textMuted)
        } else {
            Text(text = historySetsLine(workSets, best), style = HistorySetsStyle)
            if (best != null) {
                Text(
                    text = "Mejor intento: ${historySetText(workSets[best])}",
                    style = KpknType.caption,
                    color = WizardColors.done,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        if (!entry.notes.isNullOrBlank()) {
            Text(text = entry.notes, style = KpknType.caption, color = WizardColors.textMuted)
        }
    }
}

/** La fecha de la sesión en español, «martes 13 de octubre» (con el año si no es este). Si no se puede leer, se muestra tal cual. */
private fun historyDateLabel(raw: String): String {
    val iso = raw.take(10)
    val date = try { LocalDate.parse(iso) } catch (e: Exception) { return iso }
    val pattern = if (date.year == LocalDate.now().year) "EEEE d 'de' MMMM" else "EEEE d 'de' MMMM 'de' yyyy"
    return DateTimeFormatter.ofPattern(pattern, historyLocale).format(date)
}

/** Una serie como «80 × 8» («82,5 × 6»), con lado, RPE, RIR, parciales y ayuda si los hay, y «(fallida)» si falló. */
private fun historySetText(set: CompletedSet): String {
    val side = when (set.side) {
        "left" -> "Izq "
        "right" -> "Der "
        else -> ""
    }
    val load = if (set.weight > 0) set.weight.toTrimmedNumberString() else null
    val reps = if (set.reps > 0) set.reps.toString() else null
    val core = when {
        load != null && reps != null -> "$load × $reps"
        load != null -> "$load kg"
        reps != null -> "$reps reps"
        else -> "—"
    }
    val extras = buildList {
        set.rpe?.let { add("RPE ${it.toTrimmedNumberString()}") }
        set.rir?.let { add("RIR $it") }
        (set.partialReps ?: 0).takeIf { it > 0 }?.let { add("+$it parciales") }
        (set.assistedReps ?: 0).takeIf { it > 0 }?.let { add("$it con ayuda") }
    }
    val failed = if (isFailedHistorySet(set)) {
        val reason = set.failureReason?.takeIf { it.isNotBlank() && it != "execution_error" && it != "Serie marcada como fallida" }
        if (reason != null) " (fallida: $reason)" else " (fallida)"
    } else {
        ""
    }
    val withExtras = if (extras.isEmpty()) "$side$core" else "$side$core, ${extras.joinToString(", ")}"
    return withExtras + failed
}

/** Una serie fallida: marcada por `isFailedSet`, por el motivo de fallo o por `execution_error`. */
private fun isFailedHistorySet(set: CompletedSet): Boolean =
    set.isFailedSet || set.failureReason == "execution_error" || !set.failureReason.isNullOrBlank()

/**
 * La serie con mayor 1RM estimado entre las válidas (con carga y repeticiones, y no fallidas). Null si ninguna lo es: no se marca nada
 * por defecto.
 */
private fun bestHistorySetIndex(sets: List<CompletedSet>): Int? =
    sets.indices
        .filter { index -> sets[index].let { !isFailedHistorySet(it) && it.weight > 0 && it.reps > 0 } }
        .maxByOrNull { index -> calculateHybrid1RM(sets[index].weight, sets[index].reps) }

/** Las series de una sesión en una línea; la mejor en verde y seminegrita, y las fallidas en `danger`. */
private fun historySetsLine(sets: List<CompletedSet>, best: Int?): AnnotatedString = buildAnnotatedString {
    sets.forEachIndexed { index, set ->
        if (index > 0) append(" · ")
        val text = historySetText(set)
        when {
            index == best -> withStyle(SpanStyle(color = WizardColors.done, fontWeight = FontWeight.SemiBold)) { append(text) }
            isFailedHistorySet(set) -> withStyle(SpanStyle(color = WizardColors.danger)) { append(text) }
            else -> withStyle(SpanStyle(color = WizardColors.textMuted)) { append(text) }
        }
    }
}
