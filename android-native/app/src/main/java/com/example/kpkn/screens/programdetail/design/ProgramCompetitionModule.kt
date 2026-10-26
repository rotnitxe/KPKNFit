package com.example.kpkn.screens.programdetail.design

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

@Composable
internal fun ProgramCompetitionModule(state: ProgramCompetitionState,busy: Boolean,onPlan: () -> Unit,onMeetDay: () -> Unit,onReviewResult: () -> Unit,
    onOpenRecord: (() -> Unit)? = null) {
    KpknModule(Modifier.fillMaxWidth()) {
        Text("Competición · ${state.record.title}",style = KpknType.bodyStrong,color = WizardColors.text)
        Text(when {
            state.daysToMeet == 0 -> "Hoy compites"
            state.daysToMeet != null && state.daysToMeet > 0 -> "Faltan ${state.daysToMeet} días"
            state.daysToMeet != null -> "Competición pasada"
            else -> "Sin fecha de competición válida"
        },style = KpknType.body,color = WizardColors.text)
        state.forecast?.let {
            Text(it.message,style = KpknType.body,color = WizardColors.text)
            Text(state.weeklyWeightRateKg?.let { rate -> "Peso declarado en el perfil; tendencia observada en 28 días: $rate kg/semana." }
                ?: "Peso declarado en el perfil; sin tendencia corporal observada suficiente.",style = KpknType.caption,color = WizardColors.textMuted)
        }
        if (state.record.status == CompetitionRecordStatus.COMPLETED) {
            state.resultSummary?.let { summary ->
                Text(summary,style = KpknType.body,color = WizardColors.text)
                if (!busy) KpknCompactPill("Revisar actualización de marcas",onReviewResult,fullWidth = true)
            }
            if (state.record.meetResultAppliedAtMs != null) Text("Resultado aplicado a las referencias del programa.",style = KpknType.caption,color = WizardColors.textMuted)
        } else if (!busy && state.supportsSbdPlanning) {
            KpknCompactPill("Planificar intentos",onPlan,fullWidth = true)
            if (state.daysToMeet == 0 && MeetDayPlan.build(state.record.plannedAttempts).isNotEmpty())
                KpknCompactPill("Modo competición",onMeetDay,filled = false,fullWidth = true)
        }
        if (!state.supportsSbdPlanning) Text("Consulta o registra esta competición en su ficha.",style = KpknType.body,color = WizardColors.text)
        if (!busy && onOpenRecord != null) KpknCompactPill(when {
            state.record.status == CompetitionRecordStatus.COMPLETED -> "Ver resultado"
            state.daysToMeet != null && state.daysToMeet <= 0 -> "Registrar resultado"
            else -> "Ficha de competición"
        },onOpenRecord,filled = false,fullWidth = true)
    }
}

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
            Text("Planificar competición",style = KpknType.bodyStrong,color = WizardColors.text)
            Text("Pesos editables, redondeados a 2,5 kg al proponerlos. Revisa el material y las reglas de tu evento. No son resultados registrados.",style = KpknType.body,color = WizardColors.text)
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
                if (valid && !busy) KpknCompactPill("Revisar planificación",{ reviewing = true },fullWidth = true)
            } else {
                if (attempts.isEmpty()) Text("Se guardará la competición sin intentos planificados.",style = KpknType.body,color = WizardColors.text)
                attempts.forEach { Text("${competitionLiftLabel(it.movementType)} ${it.attemptNumber}: ${it.weightKg} kg · ${it.basis}",style = KpknType.body,color = WizardColors.text) }
                Text("Categoría: ${category.ifBlank { "sin objetivo" }} · peso objetivo: ${target?.let { "$it kg" } ?: "sin objetivo"}",style = KpknType.body,color = WizardColors.text)
                if (!busy) {
                    KpknCompactPill("Guardar planificación",{ onSave(attempts,category.takeIf { it.isNotBlank() },target) },fullWidth = true)
                    KpknCompactPill("Editar planificación",{ reviewing = false },filled = false,fullWidth = true)
                }
            }
            if (busy) Text("Guardando…",style = KpknType.caption,color = WizardColors.textMuted)
            else KpknCompactPill("Volver",onDismiss,filled = false,fullWidth = true)
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
            Text("Modo competición",style = KpknType.bodyStrong,color = WizardColors.text)
            Text("Consulta y simulación local. Al cerrar no se guardan estos resultados; usa el asistente de competición para registrar los reales.",style = KpknType.body,color = WizardColors.text)
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
            KpknCompactPill("Cerrar simulación",onDismiss,fullWidth = true)
        }
    }
}

@Composable
internal fun MeetResultReviewSheet(preview: ProgramRepository.MeetResultPreview,busy: Boolean,onConfirm: () -> Unit,onDismiss: () -> Unit) {
    KpknSheet(onDismissRequest = { if (!busy) onDismiss() },stableHeightFraction = 0.85f) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Revisar marcas de competición",style = KpknType.bodyStrong,color = WizardColors.text)
            Text(preview.summary,style = KpknType.body,color = WizardColors.text)
            val before = preview.original.powerliftingProfile
            val after = preview.result.powerliftingProfile
            Text("Sentadilla: ${before?.squat1RM ?: "sin marca"} → ${after?.squat1RM ?: "sin marca"} kg\nBanca: ${before?.bench1RM ?: "sin marca"} → ${after?.bench1RM ?: "sin marca"} kg\nPeso muerto: ${before?.deadlift1RM ?: "sin marca"} → ${after?.deadlift1RM ?: "sin marca"} kg",style = KpknType.body,color = WizardColors.text)
            Text("Se recalculan las referencias y semanas pendientes de la receta. Las sesiones iniciadas, registradas y manuales se conservan, junto con el resultado original.",style = KpknType.body,color = WizardColors.text)
            if (!busy) {
                KpknCompactPill("Confirmar marcas",onConfirm,fullWidth = true)
                KpknCompactPill("Ahora no",onDismiss,filled = false,fullWidth = true)
            } else Text("Guardando…",style = KpknType.caption,color = WizardColors.textMuted)
        }
    }
}
