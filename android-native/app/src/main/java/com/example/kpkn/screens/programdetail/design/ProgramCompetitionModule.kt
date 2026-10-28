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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
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

@OptIn(ExperimentalLayoutApi::class)
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
            (1..3).forEach { n -> put(movement to n,initial.firstOrNull { it.attemptNumber == n }?.weightKg?.toString().orEmpty()) }
        }
    } }
    var category by remember(record.id,record.updatedAtMs) { mutableStateOf(record.targetWeightClass.orEmpty()) }
    var targetText by remember(record.id,record.updatedAtMs) { mutableStateOf(record.targetBodyweightKg?.toString().orEmpty()) }
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
    KpknSheet(onDismissRequest = { if (!busy) onDismiss() },stableHeightFraction = 0.9f) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ProgramSheetHeader(title = "Planificar competición", subtitle = "Pesos editables, redondeados a 2,5 kg al proponerlos. Revisa el material y las reglas de tu evento. No son resultados registrados.")
            if (!reviewing) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(AttemptStrategy.CONSERVATIVE to "Conservadora",AttemptStrategy.TARGET to "Objetivo",AttemptStrategy.AMBITIOUS to "Ambiciosa").forEach { (value,label) ->
                        FilterChip(selected = strategy == value,enabled = !busy,onClick = {
                            strategy = value
                            state.expectedMaxes.forEach { (movement,max) -> AttemptPlanner.plan(movement,max,value).forEach { weights[movement to it.attemptNumber] = it.weightKg.toString() } }
                        },label = { Text(label) })
                    }
                }
                AttemptPlanner.lifts.forEach { movement ->
                    Text(competitionLiftLabel(movement),style = KpknType.bodyStrong,color = WizardColors.text)
                    if (movement !in state.expectedMaxes) Text("Sin referencia comparable: introduce los pesos que elijas.",style = KpknType.caption,color = WizardColors.textMuted)
                    (1..3).forEach { n -> OutlinedTextField(value = weights[movement to n].orEmpty(),
                        onValueChange = { weights[movement to n] = it },enabled = !busy,singleLine = true,
                        label = { Text("${competitionLiftLabel(movement)} · intento $n (kg)") },keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth().testTag("meet-attempt:${movement.name}:$n")) }
                }
                OutlinedTextField(value = category,onValueChange = { category = it },enabled = !busy,label = { Text("Categoría objetivo (opcional)") },modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = targetText,onValueChange = { targetText = it },enabled = !busy,label = { Text("Peso objetivo para el pesaje, kg (opcional)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),modifier = Modifier.fillMaxWidth().testTag("meet-target-bodyweight"))
                if (!valid) Text("Completa los tres intentos de cada levantamiento elegido en orden no decreciente y revisa el peso objetivo.",style = KpknType.body,color = WizardColors.text)
                if (valid && !busy) KpknPrimaryPill(text = "Revisar planificación", onClick = { reviewing = true }, modifier = Modifier.fillMaxWidth())
            } else {
                if (attempts.isEmpty()) Text("Se guardará la competición sin intentos planificados.",style = KpknType.body,color = WizardColors.text)
                attempts.forEach { Text("${competitionLiftLabel(it.movementType)} ${it.attemptNumber}: ${it.weightKg} kg · ${it.basis}",style = KpknType.body,color = WizardColors.text) }
                Text("Categoría: ${category.ifBlank { "sin objetivo" }} · peso objetivo: ${target?.let { "$it kg" } ?: "sin objetivo"}",style = KpknType.body,color = WizardColors.text)
                if (!busy) {
                    KpknPrimaryPill(text = "Guardar planificación", onClick = { onSave(attempts,category.takeIf { it.isNotBlank() },target) }, modifier = Modifier.fillMaxWidth())
                    KpknSecondaryPill(text = "Editar planificación", onClick = { reviewing = false }, modifier = Modifier.fillMaxWidth())
                }
            }
            if (busy) Text("Guardando…",style = KpknType.caption,color = WizardColors.textMuted)
            else KpknSecondaryPill(text = "Volver", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MeetDaySheet(attempts: List<PlannedAttempt>,onDismiss: () -> Unit) {
    val plans = remember(attempts) { MeetDayPlan.build(attempts) }
    val outcomes = remember(attempts) { mutableStateMapOf<Pair<CompetitionMovementType,Int>,CompetitionAttemptResult>() }
    KpknSheet(onDismissRequest = onDismiss,stableHeightFraction = 0.9f) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ProgramSheetHeader(title = "Modo competición", subtitle = "Consulta y simulación local. Al cerrar no se guardan estos resultados; usa el asistente de competición para registrar los reales.")
            plans.forEach { plan ->
                Text(competitionLiftLabel(plan.movement),style = KpknType.bodyStrong,color = WizardColors.text)
                Text("Calentamiento orientativo: " + plan.warmups.joinToString(" · ") { "${it.weightKg} kg × ${it.reps}" },style = KpknType.body,color = WizardColors.text)
                listOf(plan.opener,plan.second,plan.third).forEachIndexed { i,kg ->
                    val n = i + 1
                    val outcome = outcomes[plan.movement to n]
                    Text("Intento $n: $kg kg",style = KpknType.body,color = WizardColors.text)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(CompetitionAttemptResult.GOOD_LIFT to "Válido",CompetitionAttemptResult.NO_LIFT to "Nulo").forEach { (result,label) ->
                            FilterChip(selected = outcome == result,onClick = { outcomes[plan.movement to n] = result },label = { Text(label) },
                                modifier = Modifier.testTag("meet-simulation:${plan.movement.name}:$n:${result.name}"))
                        }
                    }
                    if (outcome != null) Text(MeetDayPlan.nextAttempt(plan,n,outcome)?.let { "Siguiente intento orientativo: $it kg" }
                        ?: "Fin de los intentos planificados para este levantamiento.",style = KpknType.body,color = WizardColors.text)
                }
            }
            KpknPrimaryPill(text = "Cerrar simulación", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
internal fun MeetResultReviewSheet(preview: ProgramRepository.MeetResultPreview,busy: Boolean,onConfirm: () -> Unit,onDismiss: () -> Unit) {
    KpknSheet(onDismissRequest = { if (!busy) onDismiss() },stableHeightFraction = 0.85f) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ProgramSheetHeader(title = "Revisar marcas de competición", subtitle = preview.summary)
            val before = preview.original.powerliftingProfile
            val after = preview.result.powerliftingProfile
            Text("Sentadilla: ${before?.squat1RM ?: "sin marca"} → ${after?.squat1RM ?: "sin marca"} kg\nBanca: ${before?.bench1RM ?: "sin marca"} → ${after?.bench1RM ?: "sin marca"} kg\nPeso muerto: ${before?.deadlift1RM ?: "sin marca"} → ${after?.deadlift1RM ?: "sin marca"} kg",style = KpknType.body,color = WizardColors.text)
            Text("Se recalculan las referencias y semanas pendientes de la receta. Las sesiones iniciadas, registradas y manuales se conservan, junto con el resultado original.",style = KpknType.body,color = WizardColors.text)
            if (!busy) {
                KpknPrimaryPill(text = "Confirmar marcas", onClick = onConfirm, modifier = Modifier.fillMaxWidth())
                KpknSecondaryPill(text = "Ahora no", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
            } else Text("Guardando…",style = KpknType.caption,color = WizardColors.textMuted)
        }
    }
}
