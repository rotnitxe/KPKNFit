package com.example.kpkn.screens.workout.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import com.example.kpkn.ui.components.icons.KpknLineIcons

/** Recorrido completo: el valor de partida del ROM de una serie cuando el ejercicio lo registra. */
internal const val LiveRomFullPercent = 100

/**
 * ROM con el que nace la serie en la tarjeta (Pulido v1 · 15). Manda lo que ya hay: el borrador o la serie registrada. Sin nada, el 100 %
 * si el ejercicio registra ROM (`Exercise.trackRom`, el interruptor «Registrar ROM» del editor) y NADA si no lo registra: un 100 oculto
 * se enviaría como una medición falsa en cada serie. Siempre entre 0 y 100.
 */
internal fun liveSetInitialRom(trackRom: Boolean, draftRom: Int?, completedRom: Int?): Int? =
    (draftRom ?: completedRom ?: LiveRomFullPercent.takeIf { trackRom })?.coerceIn(0, LiveRomFullPercent)

/** «ROM 80 %»: cómo se dice el ROM de una serie en sus resúmenes (tarjeta, historial, contexto). */
internal fun liveSetRomLabel(rom: Int): String = "ROM ${rom.coerceIn(0, LiveRomFullPercent)} %"

/** Resumen de la nota en línea del prototipo: «8 completas + 2 parciales · 1 con ayuda · ROM 80 %». */
internal fun liveSetDetailSummary(complete: String, partials: Int, assisted: Int, rom: Int? = null): String = buildString {
    append(complete.ifBlank { "0" }).append(" completas")
    if (partials > 0) append(" + ").append(partials).append(" parciales")
    if (assisted > 0) append(" · ").append(assisted).append(" con ayuda")
    if (rom != null) append(" · ").append(liveSetRomLabel(rom))
}

/**
 * «Detalle del registro» del prototipo 02 (`v2-details`): repeticiones completas, parciales y con ayuda separadas, el registro de ROM
 * con su conmutador y la nota de la serie (segunda página, «Nota de la serie»). Edita el mismo borrador que la tarjeta.
 */
@Composable
internal fun LiveSetDetailSheet(
    exerciseName: String,
    setLabel: String,
    completeReps: String,
    onCompleteReps: (String) -> Unit,
    partials: Int,
    onPartials: (Int) -> Unit,
    assisted: Int,
    onAssisted: (Int) -> Unit,
    romOn: Boolean,
    rom: Int?,
    onRomToggle: (Boolean) -> Unit,
    onRom: (Int) -> Unit,
    note: String,
    onNote: (String) -> Unit,
    onDismiss: () -> Unit,
    showCompleteReps: Boolean = true,
) {
    var notePage by remember { mutableStateOf(false) }
    LiveSheet(title = if (notePage) "Nota de la serie" else "Detalle del registro", onDismiss = onDismiss, tag = "live-set-detail-sheet") {
        LiveSheetSubtitle("$exerciseName · serie $setLabel")
        if (notePage) {
            LiveSheetLabeledField("Observación", note, onNote, multiline = true, placeholder = "Escribe una observación…",
                tag = "live-set-detail-note")
            LiveSheetTextAction("Volver al detalle", { notePage = false })
        } else {
            LiveSheetSubtitle("Las repeticiones parciales y con ayuda quedan separadas de las completas.")
            if (showCompleteReps) {
                LiveSheetFormRow("Repeticiones completas", completeReps, { onCompleteReps(it.filter(Char::isDigit)) },
                    tag = "live-set-detail-complete")
            }
            LiveSheetFormRow("Repeticiones parciales", partials.takeIf { it > 0 }?.toString().orEmpty(),
                { onPartials(it.filter(Char::isDigit).toIntOrNull()?.coerceIn(0, 20) ?: 0) }, tag = "live-set-detail-partials")
            LiveSheetFormRow("Repeticiones con ayuda", assisted.takeIf { it > 0 }?.toString().orEmpty(),
                { onAssisted(it.filter(Char::isDigit).toIntOrNull() ?: 0) }, tag = "live-set-detail-assisted")
            LiveSheetInlineNote(liveSetDetailSummary(completeReps, partials, assisted, rom.takeIf { romOn }))
            LiveSheetToggleRow("Registrar ROM", romOn, onRomToggle, tag = "live-set-detail-rom-toggle")
            if (romOn) {
                LiveSheetFormRow("ROM · %", rom?.toString().orEmpty(),
                    { onRom(it.filter(Char::isDigit).toIntOrNull()?.coerceIn(0, 100) ?: 0) }, keyboardType = KeyboardType.Number,
                    tag = "live-set-detail-rom")
            }
            LiveSheetMenuRow(if (note.isBlank()) "Nota de serie" else "Nota de serie · ${note.take(28)}", { notePage = true },
                icon = KpknLineIcons.Edit)
        }
    }
}
