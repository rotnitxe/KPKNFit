package com.example.kpkn.screens.workout.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.domain.workout.LiveRom
import com.example.kpkn.domain.workout.RomZone
import com.example.kpkn.screens.onboarding.design.WizardFonts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import com.example.kpkn.ui.design.depth.KpknShadow
import com.example.kpkn.ui.design.depth.kpknInset
import com.example.kpkn.ui.design.motion.KpknDuration
import com.example.kpkn.ui.design.motion.KpknMotion
import com.example.kpkn.ui.design.motion.animateKpknColorAsState
import com.example.kpkn.ui.design.motion.kpknTween
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

private val SheetInk = Color(0xFFF2EEE6)
private val SheetMuted = Color(0xFFB9B5AE)
private val SheetFaint = Color(0xFF92908A)
private val SheetLine = Color(0x0FFFFFFF)
private val SheetFieldFill = Color(0xFF262628)

/** `.lv-srow .in`: `inset 0 0 0 1px #ffffff0f` (`borde-interior` con el alfa de la fila, 0,06). */
private val FieldInset = KpknShadow.BordeInterior.spec.copyLayer(0) { it.copy(color = Color(0x0FFFFFFF)) }

/**
 * «Detalle del registro» del lienzo (`lvDetailSheet`): repeticiones completas, parciales y con ayuda separadas, el interruptor
 * «Registrar ROM» (En todas las series de este ejercicio: enciende `Exercise.trackRom`, y la tarjeta de detrás cambia al instante) y, con
 * el ROM activo, el campo exacto en %, el deslizador de 10 a 100 %, los tres botones de zona (Parcial 50 · Casi completo 80 · Completo 100) y
 * «Guardar también en el programa» (sin él, el ROM vale solo para esta sesión), y la nota de la serie (segunda página).
 * Edita el mismo borrador que la tarjeta. El ROM solo se anota y se muestra: no excluye la serie de ningún récord.
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
    /** «Guardar también en el programa»: el estado y su cambio. Nulo (modo sin programa) oculta el interruptor. */
    saveToProgram: Boolean = false,
    onSaveToProgramChange: ((Boolean) -> Unit)? = null,
) {
    var notePage by remember { mutableStateOf(false) }
    // pulido:lv-S21 (la hoja es `LiveSheetKit`: sombra `0 -14px 44px #000c` + filete superior; vive en el kit compartido)
    LiveSheet(title = if (notePage) "Nota de la serie" else "Detalle del registro", onDismiss = onDismiss, tag = "live-set-detail-sheet") {
        LiveSheetSubtitle("$exerciseName · serie $setLabel")
        if (notePage) {
            LiveSheetLabeledField("Observación", note, onNote, multiline = true, placeholder = "Escribe una observación…",
                tag = "live-set-detail-note")
            LiveSheetTextAction("Volver al detalle", { notePage = false })
        } else {
            LiveSheetSubtitle("Las repeticiones parciales y con ayuda quedan separadas de las completas.")
            if (showCompleteReps) {
                DetailNumberRow("Repeticiones completas", completeReps, { onCompleteReps(it.filter(Char::isDigit)) }, tag = "live-set-detail-complete")
            }
            DetailNumberRow("Repeticiones parciales", partials.takeIf { it > 0 }?.toString().orEmpty(),
                { onPartials(it.filter(Char::isDigit).toIntOrNull()?.coerceIn(0, 20) ?: 0) }, tag = "live-set-detail-partials")
            DetailNumberRow("Repeticiones con ayuda", assisted.takeIf { it > 0 }?.toString().orEmpty(),
                { onAssisted(it.filter(Char::isDigit).toIntOrNull() ?: 0) }, tag = "live-set-detail-assisted")
            LiveSheetInlineNote(liveSetDetailSummary(completeReps, partials, assisted))
            DetailSwitchRow(
                label = "Registrar ROM",
                small = "En todas las series de este ejercicio",
                checked = romOn,
                onToggle = onRomToggle,
                tag = "live-set-detail-rom-toggle",
            )
            // La sección entra y sale con el alto en .26 s (`estandar`) y la opacidad en .2 s (`suave`). pulido:lv-T07 lv-J15
            AnimatedVisibility(
                visible = romOn,
                enter = expandVertically(kpknTween(KpknDuration(260), KpknMotion.Estandar)) + fadeIn(kpknTween(KpknMotion.D200, KpknMotion.Suave)),
                exit = shrinkVertically(kpknTween(KpknDuration(260), KpknMotion.Estandar)) + fadeOut(kpknTween(KpknMotion.D200, KpknMotion.Suave)),
            ) {
                val current = (rom ?: LiveRomFullPercent).coerceIn(LiveRom.MIN, LiveRom.MAX)
                Column(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("ROM", style = DetailLabel, color = SheetInk)
                        DetailRomField(current, onRom)
                    }
                    RomSlider(current, onRom, Modifier.fillMaxWidth())
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val zone = LiveRom.zone(current)
                        RomZone.entries.forEach { z ->
                            ZoneButton(z, selected = z == zone, onClick = { onRom(z.representative) }, modifier = Modifier.weight(1f))
                        }
                    }
                    if (onSaveToProgramChange != null) {
                        DetailSwitchRow(
                            label = "Guardar también en el programa",
                            small = "Si no, vale solo para esta sesión",
                            checked = saveToProgram,
                            onToggle = onSaveToProgramChange,
                            tag = "live-set-detail-rom-program-toggle",
                        )
                    }
                }
            }
            LiveSheetMenuRow(if (note.isBlank()) "Nota de serie" else "Nota de serie · ${note.take(28)}", { notePage = true },
                icon = KpknLineIcons.Edit)
        }
    }
}

private val DetailLabel = TextStyle(fontFamily = WizardFonts.body, fontSize = 14.sp, lineHeight = 19.sp)
private val DetailSmall = TextStyle(fontFamily = WizardFonts.body, fontSize = 12.sp, lineHeight = 16.2.sp)
private val DetailFieldText = TextStyle(fontFamily = WizardFonts.body, fontSize = 15.sp, fontWeight = FontWeight.Medium,
    fontFeatureSettings = "'tnum' 1, 'lnum' 1", color = SheetInk, textAlign = TextAlign.End)

/** pulido:lv-S22 (filete interior del campo) */
/** `.lv-srow .in`: el campo corto de la hoja (92 × 42, radio 14, relleno #262628 y filete interior al 6 %). */
@Composable
private fun DetailField(value: String, onValueChange: (String) -> Unit, description: String, modifier: Modifier = Modifier, tag: String? = null,
    minWidth: androidx.compose.ui.unit.Dp = 92.dp, suffix: String? = null) {
    Row(
        modifier.widthIn(min = minWidth).height(42.dp).clip(RoundedCornerShape(14.dp)).background(SheetFieldFill)
            .kpknInset(FieldInset, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = DetailFieldText,
            cursorBrush = SolidColor(SheetInk),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.widthIn(min = 28.dp).fillMaxHeight().semantics { contentDescription = description }
                .then(if (tag != null) Modifier.testTag(tag) else Modifier),
            decorationBox = { inner -> Box(Modifier.fillMaxHeight(), contentAlignment = Alignment.CenterEnd) { inner() } },
        )
        suffix?.let { Text(" $it", style = DetailFieldText.copy(textAlign = TextAlign.Start), color = SheetInk) }
    }
}

/** `.lv-srow`: etiqueta a la izquierda y campo corto a la derecha, en una fila de 56 dp con filete superior. */
@Composable
private fun DetailNumberRow(label: String, value: String, onValueChange: (String) -> Unit, tag: String) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).liveSheetTopLine(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, Modifier.weight(1f), style = DetailLabel, color = SheetInk)
        DetailField(value, onValueChange, label, tag = tag)
    }
}

/** El campo exacto del ROM (10–100 %): se escribe sin saltos y solo entrega valores válidos; el deslizador lo mantiene al día. */
@Composable
private fun DetailRomField(value: Int, onRom: (Int) -> Unit) {
    var text by remember { mutableStateOf(value.toString()) }
    LaunchedEffect(value) { if (text.toIntOrNull() != value) text = value.toString() }
    DetailField(
        value = text,
        onValueChange = { typed ->
            text = typed.filter(Char::isDigit).take(3)
            text.toIntOrNull()?.takeIf { it in LiveRom.MIN..LiveRom.MAX }?.let(onRom)
        },
        description = "ROM, porcentaje exacto",
        tag = "live-set-detail-rom",
        minWidth = 84.dp,
        suffix = "%",
    )
}

/** `.lv-zch button`: Parcial · Casi completo · Completo; la zona del valor actual va en tinta. pulido:lv-S23 lv-T11 */
@Composable
private fun ZoneButton(zone: RomZone, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    // Fondo y texto cambian en .18 s `suave`; el filete interior (`borde-interior`) solo lo lleva el botón sin elegir. pulido:lv-T11
    val background by animateKpknColorAsState(if (selected) SheetInk else Color(0x0BFFFFFF), KpknDuration(180), KpknMotion.Suave)
    val content by animateKpknColorAsState(if (selected) Color(0xFF111111) else SheetMuted, KpknDuration(180), KpknMotion.Suave)
    val shape = RoundedCornerShape(19.dp)
    Box(
        modifier.height(38.dp).clip(shape)
            .background(background)
            .then(if (selected) Modifier else Modifier.kpknInset(KpknShadow.BordeInterior, shape))
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics { this.selected = selected }
            .testTag("live-set-detail-zone-${zone.representative}")
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(zone.label, style = TextStyle(fontFamily = WizardFonts.body, fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium),
            color = content, maxLines = 1, softWrap = false)
    }
}

/** `.lv-srow` con el interruptor del lienzo (`.lv-sw`: 52 × 30, pulgar de 24): etiqueta, nota pequeña debajo y el interruptor. */
@Composable
private fun DetailSwitchRow(label: String, small: String, checked: Boolean, onToggle: (Boolean) -> Unit, tag: String) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).liveSheetTopLine()
            .clickable(role = Role.Switch) { onToggle(!checked) }
            .semantics { toggleableState = ToggleableState(checked); stateDescription = if (checked) "Activado" else "Desactivado" }
            .padding(vertical = 8.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = DetailLabel, color = SheetInk)
            Text(small, Modifier.padding(top = 2.dp), style = DetailSmall, color = SheetFaint)
        }
        RomSwitch(checked)
    }
}
