package com.example.kpkn.screens.workout

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknModuleHeader
import com.example.kpkn.ui.design.KpknShape
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknType
import java.time.LocalDate
import java.util.Locale

@Composable
internal fun ExerciseHistoryContent(
    history: List<ExerciseHistoryEntry>,
    activeTag: String? = null,
) {
    if (history.isEmpty()) {
        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
            Text("Sin historial registrado", style = KpknType.body, color = WizardColors.textMuted)
        }
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
                    val spanishChile = Locale.Builder().setLanguage("es").setRegion("CL").build()
                    val month = date.month.getDisplayName(java.time.format.TextStyle.FULL, spanishChile)
                        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(spanishChile) else it.toString() }
                    "$month ${date.year}"
                }
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        grouped.forEach { (label, entries) ->
            var expanded by rememberSaveable(label) { mutableStateOf(label == "Esta semana" || label == "Semana pasada") }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Cabecera de módulo: la etiqueta a la izquierda y el chevron a la derecha; toda la fila pliega el grupo.
                KpknModuleHeader(
                    label = label,
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button,
                    ) { expanded = !expanded },
                    action = {
                        Icon(
                            imageVector = if (expanded) KpknLineIcons.ChevronUp else KpknLineIcons.ChevronDown,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = WizardColors.textMuted,
                        )
                    },
                )

                AnimatedVisibility(visible = expanded) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        entries.forEach { entry ->
                            HistoryEntryCard(entry, activeTag)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun HistoryEntryCard(
    entry: ExerciseHistoryEntry,
    activeTag: String?,
) {
    val isTagMatch = activeTag != null && entry.tag == activeTag
    // La sesión de la etiqueta activa se marca con el acento de músculo, no con amarillo.
    KpknModule(
        modifier = Modifier.fillMaxWidth(),
        accent = if (isTagMatch) SymbolPalette.musculo else null,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(entry.date.take(10), style = KpknType.bodyStrong, color = WizardColors.text)
            val tag = entry.tag
            if (tag != null) HistoryTagPill(tag)
        }
        if (!entry.notes.isNullOrBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(entry.notes, style = KpknType.caption, color = WizardColors.textMuted)
        }
        entry.sets.filter { !it.isWarmup }.forEach { set ->
            Spacer(Modifier.height(8.dp))
            HistorySetRow(set)
        }
    }
}

/** Etiqueta de la sesión: píldora `inset` con filete de 1 dp en blanco al 12 % (KPKN_UI_V2 §1, chips sobre un módulo). */
@Composable
private fun HistoryTagPill(text: String) {
    Box(
        modifier = Modifier
            .clip(KpknShape.pill)
            .background(KpknSurface.inset, KpknShape.pill)
            .border(1.dp, Color.White.copy(alpha = 0.12f), KpknShape.pill)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(text, style = KpknType.chip, color = WizardColors.text)
    }
}

/** Una serie del historial: cifras tabulares, coma decimal y «×» con espacios; la fallida va en un aviso de `danger`. */
@Composable
private fun HistorySetRow(set: CompletedSet) {
    val sideLabel = when (set.side) {
        "left" -> "Izq"
        "right" -> "Der"
        else -> null
    }
    if (set.isFailedSet || set.failureReason == "execution_error" || !set.failureReason.isNullOrBlank()) {
        Box(
            modifier = Modifier
                .clip(KpknShape.pill)
                .background(WizardColors.danger.copy(alpha = 0.16f), KpknShape.pill)
                .border(1.dp, WizardColors.danger.copy(alpha = 0.40f), KpknShape.pill)
                .padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Text(
                text = buildString {
                    if (sideLabel != null) append("$sideLabel · ")
                    append("❌ Fallida")
                    val reason = set.failureReason?.takeIf { it.isNotBlank() && it != "execution_error" && it != "Serie marcada como fallida" }
                    if (reason != null) append(": $reason")
                },
                style = KpknType.chip,
                color = WizardColors.danger,
            )
        }
    } else {
        Text(
            text = buildString {
                if (sideLabel != null) append("$sideLabel · ")
                if (set.weight > 0) append("${set.weight.toTrimmedNumberString()} kg")
                if (set.weight > 0 && set.reps > 0) append(" × ")
                if (set.reps > 0) append("${set.reps} reps")
                if (set.rpe != null) append(" · RPE ${set.rpe}")
                if (set.rir != null) append(" · RIR ${set.rir}")
                if ((set.partialReps ?: 0) > 0) append(" · +${set.partialReps} parciales")
                if ((set.assistedReps ?: 0) > 0) append(" · ${set.assistedReps} con ayuda")
            },
            style = KpknType.body.copy(fontFeatureSettings = "'tnum' 1"),
            color = WizardColors.text,
        )
    }
}
