package com.example.kpkn.screens.programdetail.design

import androidx.compose.foundation.clickable
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import com.example.kpkn.screens.onboarding.design.WizardFonts
import com.example.kpkn.ui.components.icons.KpknLineIcons
import java.util.Locale
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.*
import com.example.kpkn.data.repository.ProgramRepository
import com.example.kpkn.domain.competitions.*
import com.example.kpkn.screens.programdetail.ProgramCompetitionState
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.design.*

internal fun competitionLiftLabel(movement: CompetitionMovementType): String = when (movement) {
    CompetitionMovementType.SQUAT -> "Sentadilla"
    CompetitionMovementType.BENCH -> "Banca"
    CompetitionMovementType.DEADLIFT -> "Peso muerto"
    else -> movement.name
}

/** La cifra grande del módulo de competición (p-comp): «18» con «días», «Hoy», «Terminada» o nada si no hay fecha válida (C-D3). */
internal data class CompetitionCountdown(val value: String, val unit: String?)

internal fun competitionCountdown(state: ProgramCompetitionState): CompetitionCountdown? {
    val days = state.daysToMeet
    return when {
        state.record.status == CompetitionRecordStatus.COMPLETED -> CompetitionCountdown("Terminada", null)
        days == null -> null
        days > 0 -> CompetitionCountdown("$days", if (days == 1) "día" else "días")
        days == 0 -> CompetitionCountdown("Hoy", null)
        else -> CompetitionCountdown("Terminada", null)
    }
}

/** «Open regional · 16 nov»; sin fecha legible, «Open regional · sin fecha». */
internal fun competitionTitleLine(record: CompetitionRecord): String {
    val date = record.eventDate?.let { runCatching { java.time.LocalDate.parse(it.take(10)) }.getOrNull() }
    return "${record.title.trim()} · ${date?.let { weekDateRange(listOf(it)) } ?: "sin fecha"}"
}

/** «82,5»: un decimal con coma y sin «,0». */
private fun competitionKg(value: Double): String {
    val text = String.format(Locale.forLanguageTag("es-ES"), "%.1f", value)
    return text.removeSuffix(",0")
}

/**
 * «Pesaje 82,5 kg · −0,2 kg/sem»: el peso objetivo del pesaje y la tendencia observada; sin tendencia, solo el peso. Null si no hay peso
 * objetivo.
 */
internal fun competitionWeighInLine(state: ProgramCompetitionState): String? {
    val target = state.forecast?.targetKg ?: state.record.targetBodyweightKg ?: return null
    val rate = state.weeklyWeightRateKg?.takeIf { it.isFinite() }
    val trend = rate?.let { value ->
        val magnitude = String.format(Locale.forLanguageTag("es-ES"), "%.1f", kotlin.math.abs(value))
        val sign = when {
            magnitude == "0,0" -> ""
            value < 0 -> "−"
            else -> "+"
        }
        " · $sign$magnitude kg/sem"
    }.orEmpty()
    return "Pesaje ${competitionKg(target)} kg$trend"
}

/** «185 · 120 · 210»: el primer intento planificado de sentadilla, banca y peso muerto, en ese orden. Null sin intentos. */
internal fun competitionAttemptsLine(attempts: List<PlannedAttempt>): String? {
    val openers = AttemptPlanner.lifts.mapNotNull { lift ->
        attempts.filter { it.movementType == lift }.minByOrNull { it.attemptNumber }?.weightKg
    }
    return openers.takeIf { it.isNotEmpty() }?.joinToString(" · ") { competitionKg(it) }
}

/**
 * Módulo de competición de la portada (lienzo v6, p-comp): eyebrow «Competición» con «Ficha ›», la cuenta atrás en grande, el nombre y la
 * fecha, el pesaje, los intentos a la derecha y «Planificar intentos». El día de la competición añade «Modo competición»; al terminar,
 * «Revisar actualización de marcas».
 */
@Composable
internal fun ProgramCompetitionModule(state: ProgramCompetitionState,busy: Boolean,onPlan: () -> Unit,onMeetDay: () -> Unit,onReviewResult: () -> Unit,
    onOpenRecord: (() -> Unit)? = null) {
    val completed = state.record.status == CompetitionRecordStatus.COMPLETED
    val countdown = competitionCountdown(state)
    val weighIn = competitionWeighInLine(state)
    val attempts = state.takeIf { it.supportsSbdPlanning }?.let { competitionAttemptsLine(it.record.plannedAttempts) }
    KpknModule(Modifier.fillMaxWidth(), contentPadding = PaddingValues(start = 18.dp, top = 16.dp, end = 18.dp, bottom = 18.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 28.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("COMPETICIÓN", style = CompEyebrowStyle, color = WizardColors.textMuted, modifier = Modifier.weight(1f))
            if (!busy && onOpenRecord != null) {
                Row(
                    Modifier.heightIn(min = 36.dp).semantics(mergeDescendants = true) { contentDescription = "Ficha de competición" }
                        .clickable(role = Role.Button, onClick = onOpenRecord),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text("Ficha", style = CompActionStyle, color = WizardColors.textMuted)
                    Icon(KpknLineIcons.ChevronRight, contentDescription = null, modifier = Modifier.size(16.dp), tint = WizardColors.textMuted)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column {
                countdown?.let { figure ->
                    Text(
                        buildAnnotatedString {
                            append(figure.value)
                            figure.unit?.let { unit -> withStyle(CompNumUnitStyle) { append(" $unit") } }
                        },
                        style = CompNumStyle,
                        color = WizardColors.text,
                    )
                    Spacer(Modifier.height(6.dp))
                }
                Text(competitionTitleLine(state.record), style = CompCapStyle, color = WizardColors.textFaint)
                weighIn?.let { Text(it, style = CompCapStyle, color = WizardColors.textFaint) }
            }
            if (attempts != null) {
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text("Intentos", style = CompCapStyle, color = WizardColors.textFaint)
                    Spacer(Modifier.height(4.dp))
                    Text(attempts, style = CompAttemptsStyle, color = WizardColors.text, textAlign = TextAlign.End)
                }
            }
        }
        if (!busy) {
            when {
                completed -> if (state.resultSummary != null) {
                    ProgramLienzoPill("Revisar actualización de marcas", onReviewResult, Modifier.fillMaxWidth().padding(top = 14.dp), filled = false, small = true)
                }
                state.supportsSbdPlanning -> {
                    ProgramLienzoPill("Planificar intentos", onPlan, Modifier.fillMaxWidth().padding(top = 14.dp), filled = false, small = true)
                    if (state.daysToMeet == 0 && MeetDayPlan.build(state.record.plannedAttempts).isNotEmpty()) {
                        ProgramLienzoPill("Modo competición", onMeetDay, Modifier.fillMaxWidth().padding(top = 8.dp), filled = false, small = true)
                    }
                }
            }
        }
    }
}

/** El eyebrow del módulo (`.ey`): Inter SemiBold 11 sp con 1,2 sp. */
private val CompEyebrowStyle = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 1.2.sp)

/** «Ficha ›» (`.act`): Inter SemiBold 13 sp. */
private val CompActionStyle = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 18.sp)

/** La cifra grande (`.num`): Syne ExtraBold 28 sp con −0,6 sp y cifras tabulares. */
private val CompNumStyle = TextStyle(
    fontFamily = WizardFonts.display,
    fontWeight = FontWeight.ExtraBold,
    fontSize = 28.sp,
    lineHeight = 28.sp,
    letterSpacing = (-0.6).sp,
    fontFeatureSettings = "'tnum' 1",
)

/** La unidad de la cifra (`.num small`): Inter SemiBold 12 sp en `textMuted`. */
private val CompNumUnitStyle = SpanStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 0.sp, color = WizardColors.textMuted)

/** `.cap`: Inter 12 sp. */
private val CompCapStyle = TextStyle(fontFamily = WizardFonts.body, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp)

/** Los intentos: Syne Bold 15 sp con cifras tabulares. */
private val CompAttemptsStyle = TextStyle(fontFamily = WizardFonts.display, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 18.sp, fontFeatureSettings = "'tnum' 1")

/** Las tres estrategias de m-meet, en el orden del segmentado. */
private val attemptStrategies = listOf(
    AttemptStrategy.CONSERVATIVE to "Conservadora",
    AttemptStrategy.TARGET to "Objetivo",
    AttemptStrategy.AMBITIOUS to "Ambiciosa",
)

/** El valor de un campo de intento: «182,5», sin «,0». */
internal fun competitionFieldText(kg: Double): String = competitionKg(kg)

/**
 * «Planificar intentos» (lienzo v6, m-meet): el nombre y la fecha de la competición, la estrategia en el segmentado, los tres intentos de cada
 * levantamiento en una fila (campos de 58 dp), la categoría y el peso objetivo para el pesaje (opcionales) y «Revisar planificación», que se
 * activa con los intentos completos y en orden. La revisión enseña cada intento con su base y deja «Guardar planificación» o «Editar
 * planificación». Nada se guarda hasta «Guardar planificación».
 */
@Composable
internal fun CompetitionPlanSheet(state: ProgramCompetitionState,busy: Boolean,
    onSave: (List<PlannedAttempt>,String?,Double?) -> Unit,onDismiss: () -> Unit) {
    val record = state.record
    var strategy by remember(record.id) { mutableStateOf(AttemptStrategy.TARGET) }
    val weights = remember(record.id,record.updatedAtMs) { mutableStateMapOf<Pair<CompetitionMovementType,Int>,String>().apply {
        AttemptPlanner.lifts.forEach { movement ->
            val initial = record.plannedAttempts.filter { it.movementType == movement }.ifEmpty {
                state.expectedMaxes[movement]?.let { AttemptPlanner.plan(movement,it) }.orEmpty()
            }
            (1..3).forEach { n -> put(movement to n,initial.firstOrNull { it.attemptNumber == n }?.weightKg?.let(::competitionFieldText).orEmpty()) }
        }
    } }
    var category by remember(record.id,record.updatedAtMs) { mutableStateOf(record.targetWeightClass.orEmpty()) }
    var targetText by remember(record.id,record.updatedAtMs) { mutableStateOf(record.targetBodyweightKg?.let(::competitionFieldText).orEmpty()) }
    var reviewing by remember(record.id,record.updatedAtMs) { mutableStateOf(false) }
    fun number(text: String): Double? = text.trim().replace(',','.').toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 }
    val target = number(targetText)
    val partial = AttemptPlanner.lifts.any { movement ->
        val values = (1..3).map { weights[movement to it].orEmpty() }
        values.any { it.isNotBlank() } && values.any { number(it) == null }
    }
    val attempts = AttemptPlanner.lifts.flatMap { movement ->
        (1..3).mapNotNull { n -> number(weights[movement to n].orEmpty())?.let { kg ->
            val basis = record.plannedAttempts.firstOrNull { it.movementType == movement && it.attemptNumber == n && it.weightKg == kg }?.basis
                ?: state.expectedMaxes[movement]?.let { AttemptPlanner.plan(movement,it,strategy).firstOrNull { a -> a.attemptNumber == n && a.weightKg == kg }?.basis }
                ?: "Peso elegido manualmente"
            PlannedAttempt(movement,n,kg,basis)
        } }
    }
    val descending = attempts.groupBy { it.movementType }.values.any { values -> values.zipWithNext().any { it.first.weightKg > it.second.weightKg } }
    val valid = !partial && !descending && category.length <= 80 && (targetText.isBlank() || target != null)
    ProgramLienzoSheet(onDismiss = onDismiss, dismissible = !busy) {
        LienzoSheetTitle(
            title = "Planificar intentos",
            eyebrow = competitionTitleLine(record),
            sub = "Pesos editables, redondeados a 2,5 kg. No son resultados registrados.",
        )
        if (!reviewing) {
            LienzoSegmented(
                options = attemptStrategies.map { it.second },
                selectedIndex = attemptStrategies.indexOfFirst { it.first == strategy }.coerceAtLeast(0),
                onSelect = { index ->
                    val value = attemptStrategies[index].first
                    strategy = value
                    state.expectedMaxes.forEach { (movement,max) ->
                        AttemptPlanner.plan(movement,max,value).forEach { weights[movement to it.attemptNumber] = competitionFieldText(it.weightKg) }
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                enabled = !busy,
            )
            AttemptPlanner.lifts.forEach { movement ->
                LienzoRatioRow(label = competitionLiftLabel(movement), trailing = {
                    (1..3).forEach { n ->
                        LienzoTextField(
                            label = null,
                            value = weights[movement to n].orEmpty(),
                            onValueChange = { weights[movement to n] = it },
                            modifier = Modifier.width(58.dp),
                            numeric = true,
                            enabled = !busy,
                            tag = "meet-attempt:${movement.name}:$n",
                            textAlign = TextAlign.Center,
                            valueStyle = LienzoFieldValueStyle.copy(fontSize = 14.sp, fontFeatureSettings = "'tnum' 1"),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                            topGap = 0.dp,
                        )
                    }
                })
            }
            LienzoTextField(
                label = "Categoría objetivo (opcional)",
                value = category,
                onValueChange = { category = it },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy,
                error = category.length > 80,
                tag = "meet-target-class",
            )
            LienzoTextField(
                label = "Peso objetivo para el pesaje (opcional)",
                value = targetText,
                onValueChange = { targetText = it },
                modifier = Modifier.fillMaxWidth(),
                numeric = true,
                suffix = "kg",
                enabled = !busy,
                error = targetText.isNotBlank() && target == null,
                tag = "meet-target-bodyweight",
            )
            ProgramLienzoPill(
                text = "Revisar planificación",
                onClick = { reviewing = true },
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                enabled = valid && !busy,
            )
        } else {
            if (attempts.isEmpty()) Text("Se guardará la competición sin intentos planificados.",style = LienzoCapStyle,color = WizardColors.textFaint)
            attempts.forEach {
                LienzoRatioRow(label = "${competitionLiftLabel(it.movementType)} · ${it.attemptNumber}", sub = it.basis, value = "${competitionKg(it.weightKg)} kg")
            }
            LienzoRatioRow(label = "Categoría", value = category.trim().ifBlank { "Sin objetivo" })
            LienzoRatioRow(label = "Peso para el pesaje", value = target?.let { "${competitionKg(it)} kg" } ?: "Sin objetivo")
            ProgramLienzoPill(
                text = "Guardar planificación",
                onClick = { onSave(attempts,category.trim().takeIf { it.isNotBlank() },target) },
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                enabled = !busy,
            )
            ProgramLienzoPill(
                text = "Editar planificación",
                onClick = { reviewing = false },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                filled = false,
                enabled = !busy,
            )
        }
    }
}

/**
 * «Modo competición» (simulación del día): las mismas funciones con la piel de las hojas m-*: por levantamiento, el calentamiento orientativo
 * y cada intento con sus chips «Válido» / «Nulo» y el siguiente intento. No guarda resultados.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MeetDaySheet(attempts: List<PlannedAttempt>,onDismiss: () -> Unit) {
    val plans = remember(attempts) { MeetDayPlan.build(attempts) }
    val outcomes = remember(attempts) { mutableStateMapOf<Pair<CompetitionMovementType,Int>,CompetitionAttemptResult>() }
    ProgramLienzoSheet(onDismiss = onDismiss) {
        LienzoSheetTitle(
            title = "Modo competición",
            sub = "Consulta y simulación local. Al cerrar no se guardan estos resultados; usa el asistente de competición para registrar los reales.",
        )
        plans.forEach { plan ->
            LienzoGroup(competitionLiftLabel(plan.movement))
            Text("Calentamiento orientativo: " + plan.warmups.joinToString(" · ") { "${competitionKg(it.weightKg)} kg × ${it.reps}" },
                style = LienzoCapStyle,color = WizardColors.textFaint)
            listOf(plan.opener,plan.second,plan.third).forEachIndexed { i,kg ->
                val n = i + 1
                val outcome = outcomes[plan.movement to n]
                LienzoRatioRow(
                    label = "Intento $n · ${competitionKg(kg)} kg",
                    sub = outcome?.let { MeetDayPlan.nextAttempt(plan,n,it)?.let { next -> "Siguiente intento orientativo: ${competitionKg(next)} kg" }
                        ?: "Fin de los intentos planificados para este levantamiento." },
                    trailing = {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(CompetitionAttemptResult.GOOD_LIFT to "Válido",CompetitionAttemptResult.NO_LIFT to "Nulo").forEach { (result,label) ->
                                LienzoChip(text = label, selected = outcome == result, onClick = { outcomes[plan.movement to n] = result },
                                    tag = "meet-simulation:${plan.movement.name}:$n:${result.name}")
                            }
                        }
                    },
                )
            }
        }
        ProgramLienzoPill(text = "Cerrar simulación", onClick = onDismiss, modifier = Modifier.fillMaxWidth().padding(top = 16.dp))
    }
}

/** «Revisar actualización de marcas» (misma lógica, piel m-*): antes → después por levantamiento y «Confirmar marcas» o «Ahora no». */
@Composable
internal fun MeetResultReviewSheet(preview: ProgramRepository.MeetResultPreview,busy: Boolean,onConfirm: () -> Unit,onDismiss: () -> Unit) {
    ProgramLienzoSheet(onDismiss = onDismiss, dismissible = !busy) {
        LienzoSheetTitle(title = "Revisar marcas de competición", eyebrow = "Competición terminada", sub = preview.summary)
        val before = preview.original.powerliftingProfile
        val after = preview.result.powerliftingProfile
        fun mark(kg: Double?) = kg?.let { "${competitionKg(it)} kg" } ?: "sin marca"
        listOf(
            "Sentadilla" to (before?.squat1RM to after?.squat1RM),
            "Banca" to (before?.bench1RM to after?.bench1RM),
            "Peso muerto" to (before?.deadlift1RM to after?.deadlift1RM),
        ).forEach { (label, values) -> LienzoRatioRow(label = label, value = "${mark(values.first)} → ${mark(values.second)}") }
        Text("Se recalculan las referencias y semanas pendientes de la receta. Las sesiones iniciadas, registradas y manuales se conservan, junto con el resultado original.",
            style = LienzoCapStyle,color = WizardColors.textFaint,modifier = Modifier.padding(top = 10.dp))
        ProgramLienzoPill(text = "Confirmar marcas", onClick = onConfirm, modifier = Modifier.fillMaxWidth().padding(top = 16.dp), enabled = !busy)
        ProgramLienzoPill(text = "Ahora no", onClick = onDismiss, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), filled = false, enabled = !busy)
    }
}
