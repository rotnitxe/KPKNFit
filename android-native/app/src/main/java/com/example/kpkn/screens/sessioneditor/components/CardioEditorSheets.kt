package com.example.kpkn.screens.sessioneditor.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.cardio.cueFlags
import com.example.kpkn.data.models.CardioBlockType
import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.CardioFeaturedTemplates
import com.example.kpkn.data.models.CardioIntervalPatternSpec
import com.example.kpkn.data.models.CardioIntervalPrograms
import com.example.kpkn.data.models.HiitRestNature
import com.example.kpkn.data.models.HiitWorkTarget
import com.example.kpkn.domain.cardio.CardioPrescriptionFormatter
import com.example.kpkn.domain.cardio.CardioUniformRepeat
import com.example.kpkn.domain.cardio.CardioValueKind
import com.example.kpkn.domain.cardio.CardioZones
import com.example.kpkn.screens.workout.CardioZoneColors
import com.example.kpkn.screens.workout.CardioZoneNames

/*
 * Pulido v1 · CARDIO DEL EDITOR · las hojas de la tarjeta: «Zona de pulso», «Protocolo», «Avisos y pantalla» y «Plantillas»
 * (`ceZoneSheet`, `ceProtoSheet`, `ceAvisosSheet`, `ceTplSheet` de `scenes-ce.js`).
 */

// ───────────────────────────── Zona de pulso (ce-zona) ─────────────────────────────

/** `.ce-zs`: elegir la zona de pulso del cardio (0 = sin objetivo). Se guarda el punto medio de la zona como `targetHrPercent`. */
@Composable
internal fun CardioZoneSheet(
    typeName: String,
    initialZone: Int,
    onApply: (zone: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var zone by remember { mutableIntStateOf(initialZone) }
    CeSheet(onDismiss = onDismiss) {
        CeSheetHeader("Zona de pulso", typeName)
        CeHint("Tu pulso objetivo, sobre tu frecuencia máxima. El cardio en vivo te avisa si te sales.", Modifier.padding(top = 0.dp))
        Column(Modifier.fillMaxWidth()) {
            for (z in 0..5) {
                val on = zone == z
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 54.dp)
                        .clickable(role = Role.RadioButton) { zone = z }
                        .semantics { stateDescription = if (on) "Elegida" else "No elegida" }
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(if (z == 0) Color.White.copy(alpha = 0.149f) else CardioZoneColors[z - 1]),
                    )
                    Text(
                        if (z == 0) "Sin objetivo" else "Z$z · ${CardioZoneNames[z - 1]}",
                        modifier = Modifier.weight(1f),
                        style = edText(15f, if (on) FontWeight.SemiBold else FontWeight.Medium, EdColors.ink, 19.5f),
                    )
                    if (z > 0) Text(CardioZones.percentRangeLabels[z - 1], style = edText(12.5f, FontWeight.Medium, EdColors.faint, 16f, true))
                    Box(Modifier.width(20.dp), contentAlignment = Alignment.Center) {
                        if (on) CeIcon(CeIcons.Check, 18.dp, EdColors.ink)
                    }
                }
            }
        }
        CeSheetActions("Aplicar", { onApply(zone) }, secondary = "Cancelar", onSecondary = onDismiss)
    }
}

// ───────────────────────────── Protocolo (ce-protocolo) ─────────────────────────────

/** `.ce-proto`: lo que casi nadie toca del cardio por series: pausa activa o pasiva, series y su descanso, y con qué termina cada esfuerzo. */
@Composable
internal fun CardioProtocolSheet(
    typeName: String,
    dose: String,
    repeat: CardioUniformRepeat,
    onChange: (CardioUniformRepeat) -> Unit,
    onOpenValue: (CardioValueKind) -> Unit,
    onDismiss: () -> Unit,
) {
    CeSheet(onDismiss = onDismiss, modifier = Modifier.verticalScroll(rememberScrollState())) {
        CeSheetHeader("Protocolo", "$typeName · $dose")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CeEyebrow("Pausa entre esfuerzos")
            CeSegmented(
                options = listOf("Activa", "Pasiva"),
                selected = if (repeat.restNature == HiitRestNature.ACTIVE) 0 else 1,
                onSelect = { onChange(repeat.copy(restNature = if (it == 0) HiitRestNature.ACTIVE else HiitRestNature.PASSIVE)) },
            )
            CeHint(
                if (repeat.restNature == HiitRestNature.ACTIVE) "Sigues moviéndote suave. La voz dice «Descanso activo, muévete suave»."
                else "Paras del todo. La voz dice «Descanso, alto total».",
            )
        }
        Column {
            CeRow(topRule = true, controlRow = true) {
                CeRowLabel("Series", "Repites todo el bloque de rondas", bold = true)
                CeStepper(
                    value = repeat.sets,
                    onMinus = { onChange(repeat.copy(sets = (repeat.sets - 1).coerceAtLeast(1))) },
                    onPlus = { onChange(repeat.copy(sets = (repeat.sets + 1).coerceAtMost(5))) },
                    canMinus = repeat.sets > 1,
                    canPlus = repeat.sets < 5,
                    label = "series",
                )
            }
            if (repeat.sets > 1) {
                CeRow(controlRow = true, onClick = { onOpenValue(CardioValueKind.REST_BETWEEN_SETS) }) {
                    CeRowLabel("Descanso entre series", bold = true)
                    CeValue(CardioValueKind.mmss(repeat.restBetweenSetsSeconds), { onOpenValue(CardioValueKind.REST_BETWEEN_SETS) })
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CeEyebrow("Cada esfuerzo termina por")
            CeSegmented(
                options = listOf("Tiempo", "kcal", "Distancia"),
                selected = when (repeat.workTargetType) {
                    HiitWorkTarget.TIME -> 0
                    HiitWorkTarget.KCAL -> 1
                    HiitWorkTarget.DISTANCE -> 2
                },
                onSelect = { i ->
                    val type = listOf(HiitWorkTarget.TIME, HiitWorkTarget.KCAL, HiitWorkTarget.DISTANCE)[i]
                    // Cambiar el objetivo no inventa una cifra: la que hubiera se conserva y, si no hay, la de la maqueta (12 kcal, 200 m).
                    val value = when (type) {
                        HiitWorkTarget.TIME -> null
                        HiitWorkTarget.KCAL -> repeat.workTargetValue?.takeIf { repeat.workTargetType == HiitWorkTarget.KCAL } ?: 12.0
                        HiitWorkTarget.DISTANCE -> repeat.workTargetValue?.takeIf { repeat.workTargetType == HiitWorkTarget.DISTANCE } ?: 200.0
                    }
                    onChange(repeat.copy(workTargetType = type, workTargetValue = value))
                },
            )
            if (repeat.workTargetType == HiitWorkTarget.TIME) {
                CeHint("Por defecto, el tiempo de esfuerzo de la tarjeta.")
            } else {
                val kcal = repeat.workTargetType == HiitWorkTarget.KCAL
                val kind = if (kcal) CardioValueKind.WORK_KCAL else CardioValueKind.WORK_METERS
                Column(Modifier.padding(top = 0.dp)) {
                    CeRow(topRule = true, controlRow = true, onClick = { onOpenValue(kind) }) {
                        CeRowLabel(if (kcal) "kcal por esfuerzo" else "Metros por esfuerzo", bold = true)
                        CeValue(
                            "${repeat.workTargetValue?.toInt() ?: if (kcal) 12 else 200}${if (kcal) " kcal" else " m"}",
                            { onOpenValue(kind) },
                        )
                    }
                }
                CeHint("Cada esfuerzo termina al llegar a la cifra, no al acabar el tiempo.")
            }
        }
        CeSheetActions("Listo", onDismiss)
    }
}

// ───────────────────────────── Avisos y pantalla (ce-avisos) ─────────────────────────────

/** Los tres avisos de fase y la pantalla encendida de un cardio, tal como los guarda `CardioDetails`. */
internal data class CardioAvisos(val beeps: Boolean, val voice: Boolean, val vibration: Boolean, val screen: Boolean)

internal fun CardioDetails.avisos(): CardioAvisos {
    val flags = cueFlags()
    return CardioAvisos(flags.beeps, flags.voice, flags.vibration, keepScreenOn && (hiit?.keepScreenOn != false))
}

/** «Sonidos · Voz · Vibración · Pantalla encendida» (el resumen de la fila de la tarjeta). */
internal fun cardioAvisosSummary(details: CardioDetails): String {
    val a = details.avisos()
    val phased = cardioHasPhases(details)
    val parts = buildList {
        if (phased) {
            if (a.beeps) add("Sonidos")
            if (a.voice) add("Voz")
            if (a.vibration) add("Vibración")
        }
        if (a.screen) add("Pantalla encendida")
    }
    return if (parts.isEmpty()) "Todo apagado" else parts.joinToString(" · ")
}

/** El cardio tiene cambios de fase (series o intervalos): solo entonces hay avisos de fase que dar. */
internal fun cardioHasPhases(details: CardioDetails): Boolean = details.hiit != null || details.hasIntervals()

/** Escribe un aviso de fase en el cardio (en sus datos y, si tiene protocolo por series, también en él). */
internal fun CardioDetails.withBeeps(value: Boolean) = copy(beepsEnabled = value, hiit = hiit?.copy(beepsEnabled = value))
internal fun CardioDetails.withVoice(value: Boolean) = copy(voiceCuesEnabled = value, hiit = hiit?.copy(voiceCuesEnabled = value))
internal fun CardioDetails.withVibration(value: Boolean) = copy(vibrationEnabled = value, hiit = hiit?.copy(vibrationEnabled = value))
internal fun CardioDetails.withKeepScreenOn(value: Boolean) = copy(keepScreenOn = value, hiit = hiit?.copy(keepScreenOn = value))

/** `.ce-av`: sonidos, voz, vibración y pantalla encendida, cada uno con su icono y una línea que dice qué hace. */
@Composable
internal fun CardioAvisosSheet(
    typeName: String,
    details: CardioDetails,
    onChange: (CardioDetails) -> Unit,
    onDismiss: () -> Unit,
) {
    val a = details.avisos()
    val phased = cardioHasPhases(details)
    CeSheet(onDismiss = onDismiss, modifier = Modifier.verticalScroll(rememberScrollState())) {
        CeSheetHeader("Avisos y pantalla", typeName)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CeEyebrow("Avisos de fase")
            Column {
                CeSwitchRow(CeIcons.Beep, "Sonidos", "Pitidos al cambiar de fase y cuenta atrás de 3 segundos.", a.beeps, { onChange(details.withBeeps(!a.beeps)) }, enabled = phased, topRule = true)
                CeSwitchRow(CeIcons.Voice, "Voz", "Dice la fase, la duración y la consigna del bloque.", a.voice, { onChange(details.withVoice(!a.voice)) }, enabled = phased)
                CeSwitchRow(CeIcons.Vib, "Vibración", "Doble vibración al empezar cada esfuerzo.", a.vibration, { onChange(details.withVibration(!a.vibration)) }, enabled = phased)
            }
            CeHint(
                if (phased) "Dependen también de lo que tengas activado en Ajustes."
                else "Los avisos de fase son del cardio por series o por intervalos: en Continuo no hay cambios de fase que avisar.",
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CeEyebrow("Pantalla")
            CeSwitchRow(CeIcons.Sun, "Pantalla encendida", "No se apaga mientras haces este cardio.", a.screen, { onChange(details.withKeepScreenOn(!a.screen)) }, topRule = true)
        }
        CeSheetActions("Listo", onDismiss)
    }
}

// ───────────────────────────── Plantillas (ce-plantillas) ─────────────────────────────

/** Los bloques de un cardio como barras de la gráfica (una vuelta del circuito): nivel del bloque o el de su tipo. */
internal fun cardioBars(details: CardioDetails): List<CeBar> = details.intervalBlocks.map { b ->
    val default = when (b.type) {
        CardioBlockType.WORK -> details.resolvedIntensityLevel()
        CardioBlockType.RECOVER -> 1
        CardioBlockType.WARMUP -> 3
        CardioBlockType.COOLDOWN -> 2
    }
    CeBar(b.type, b.durationSeconds.coerceAtLeast(1), (b.intensityLevel ?: default).coerceIn(1, 10))
}

/** Las barras de un patrón de intervalo (`CardioIntervalPrograms.specs`): ancho = su peso, alto = su nivel. */
internal fun patternBars(spec: CardioIntervalPatternSpec): List<CeBar> =
    spec.units.map { CeBar(it.type, it.durationWeight * 60, (it.intensityLevel * 2).coerceIn(1, 10)) }

/** `.ce-tpl`: las 7 plantillas con su forma y su dosis, y los 7 patrones del modo Intervalos. Elegir una aplica y cierra. */
@Composable
internal fun CardioTemplatesSheet(
    details: CardioDetails,
    onPickTemplate: (String) -> Unit,
    onPickPattern: (CardioIntervalPatternSpec) -> Unit,
    onDismiss: () -> Unit,
) {
    val templates = remember(details) {
        CardioFeaturedTemplates.all.map { template -> Triple(template, CardioFeaturedTemplates.apply(template.id, details), templateSelected(template.id, details)) }
    }
    CeSheet(onDismiss = onDismiss, stableHeightFraction = 0.9f, modifier = Modifier.verticalScroll(rememberScrollState())) {
        CeSheetHeader("Plantillas", "Elige una base y ajústala después.")
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            CeEyebrow("Plantillas")
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                templates.forEach { (template, preview, selected) ->
                    TemplateCard(
                        name = template.name,
                        subtitle = cardioDoseLine(preview),
                        bars = if (preview.hasIntervals()) cardioBars(preview) else null,
                        selected = selected,
                        onClick = { onPickTemplate(template.id) },
                    )
                }
            }
            CeEyebrow("Patrones de intervalo", Modifier.padding(top = 6.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CardioIntervalPrograms.specs.forEach { spec ->
                    TemplateCard(
                        name = spec.label,
                        subtitle = spec.description,
                        bars = patternBars(spec),
                        selected = false,
                        onClick = { onPickPattern(spec) },
                        monospaced = false,
                    )
                }
            }
        }
    }
}

@Composable
private fun TemplateCard(
    name: String,
    subtitle: String,
    bars: List<CeBar>?,
    selected: Boolean,
    onClick: () -> Unit,
    monospaced: Boolean = true,
) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(CeColors.module, shape)
            .border(1.dp, if (selected) EdColors.ink else Color.White.copy(alpha = 0.051f), shape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { stateDescription = if (selected) "Elegida" else "" }
            .padding(start = 13.dp, end = 15.dp, top = 11.dp, bottom = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(104.dp)) {
            if (bars != null) CeBlockChart(bars, height = 34.dp, sparkline = true) else CeFlatBar(height = 34.dp)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(name, style = edText(14.5f, FontWeight.SemiBold, EdColors.ink, 18f))
            Text(subtitle, style = edText(12f, FontWeight.Normal, EdColors.faint, 16.2f, monospaced))
        }
        if (selected) CeIcon(CeIcons.Check, 18.dp, EdColors.ink)
    }
}

/** Una plantilla figura como elegida si su materialización coincide con lo que hay en el editor (la lógica de siempre). */
internal fun templateSelected(templateId: String, details: CardioDetails): Boolean {
    val template = CardioFeaturedTemplates.all.firstOrNull { it.id == templateId } ?: return false
    val hiitId = template.hiitId
    return if (hiitId != null) {
        val config = com.example.kpkn.data.models.CardioHiitTemplates.findById(hiitId)?.toConfig()
        val hiit = details.hiit
        hiit != null && config != null && hiit.workSeconds == config.workSeconds && hiit.rounds == config.rounds
    } else {
        !details.hasIntervals() && details.targetDurationSeconds == (template.steadyMinutes ?: 0) * 60
    }
}

/** La duración legible de una dosis de series (para el subtítulo de la hoja de protocolo cuando no hay línea de dosis). */
internal fun cardioDurationText(seconds: Int): String = CardioPrescriptionFormatter.formatDuration(seconds)
