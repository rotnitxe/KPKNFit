package com.example.kpkn.screens.nutrition.checkin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.nutrition.checkin.*
import com.example.kpkn.domain.nutrition.expenditure.*
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.*
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun NutritionCheckinCard(onStart: () -> Unit, modifier: Modifier = Modifier) {
    KpknModule(modifier.fillMaxWidth().testTag("nutrition-checkin-card"), accent = SymbolPalette.energia) {
        KpknModuleHeader(label = "Revisión semanal")
        Spacer(Modifier.height(10.dp))
        Text("Tu check-in semanal está listo", style = KpknType.title, color = WizardColors.text)
        Spacer(Modifier.height(8.dp))
        Text("Revisa tus registros y decide si quieres ajustar tu objetivo.", style = KpknType.body, color = WizardColors.textMuted)
        Spacer(Modifier.height(16.dp))
        KpknPrimaryPill("Empezar", onStart, Modifier.fillMaxWidth(), tag = "nutrition-checkin-start", icon = KpknLineIcons.ChevronRight)
    }
}

fun expenditureStateLabel(state: ExpenditureState): String = when (state) {
    ExpenditureState.STARTING -> "Iniciando"
    ExpenditureState.LEARNING -> "Aprendiendo"
    ExpenditureState.STABLE -> "Estable"
    ExpenditureState.PAUSED -> "Pausado"
}

fun exclusionReasonLabel(reason: ExclusionReason): String = when (reason) {
    ExclusionReason.NO_LOG -> "Sin registro ni ayuno confirmado"
    ExclusionReason.PARTIAL -> "Registro parcial"
    ExclusionReason.UNREVIEWED_LOW -> "Día bajo pendiente de revisión"
    ExclusionReason.IMPLAUSIBLE -> "Cantidad no válida para estimar"
    ExclusionReason.FUTURE -> "Día futuro"
}

@Composable
fun CheckinEstimateDetails(estimate: ExpenditureEstimate?, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (estimate == null) {
            Text("Sin estimación disponible", style = KpknType.bodyStrong, color = WizardColors.text)
        } else {
            Text(expenditureStateLabel(estimate.state), style = KpknType.bodyStrong, color = SymbolPalette.energia)
            KpknMetric("≈ ${estimate.kcal.roundToInt()}", "kcal/día", style = KpknType.metricL)
            Text("Banda de incertidumbre: ${estimate.lowKcal.roundToInt()}–${estimate.highKcal.roundToInt()} kcal/día",
                style = KpknType.caption, color = WizardColors.textMuted)
            estimate.pauseReason?.let {
                Text(if (it == PauseReason.INSUFFICIENT_LOGGING) "Faltan días de registro completos." else "Faltan pesajes recientes.",
                    style = KpknType.body, color = WizardColors.textMuted)
            }
            Text("Datos hasta ${shortDate(estimate.date)} · ${estimate.quality.weighIns} pesajes",
                style = KpknType.caption, color = WizardColors.textMuted)
            estimate.trend?.let {
                Text(trendLabel(it.trendKg), style = KpknType.bodyStrong, color = WizardColors.text)
            }
        }
        Text("El gasto estimado aproxima la energía que usas al día a partir de tus registros y tu tendencia de peso. Es una estimación, no una medición.",
            style = KpknType.body, color = WizardColors.textMuted)
    }
}

/**
 * Lo propio del módulo de pesaje cuando el gasto estimado ya se muestra en otro sitio de la página:
 * solo los pesajes usados y la tendencia, sin repetir estado, cifra, banda ni la nota de estimación.
 */
@Composable
fun CheckinWeighInSummary(estimate: ExpenditureEstimate?, modifier: Modifier = Modifier) {
    Column(modifier.testTag("checkin-weighin-summary"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (estimate == null) {
            Text("Aún no hay pesajes para mostrar tu tendencia.", style = KpknType.body, color = WizardColors.textMuted)
        } else {
            Text("Pesajes usados: ${estimate.quality.weighIns}", style = KpknType.bodyStrong, color = WizardColors.text)
            estimate.trend?.let { Text(trendLabel(it.trendKg), style = KpknType.bodyStrong, color = WizardColors.text) }
        }
    }
}

/**
 * [showEstimateSummary] = false cuando la página que aloja el flujo ya muestra el gasto estimado (la calibración):
 * así los módulos de pesaje y objetivo no lo repiten. Con true (vista previa y flujos sueltos) cada módulo lo lleva completo.
 */
@Composable
fun NutritionCheckinContent(
    state: NutritionCheckinUiState,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onPostpone: () -> Unit,
    onDismissModule: (CheckinModule) -> Unit,
    onPartial: (LocalDate) -> Unit,
    onComplete: (LocalDate) -> Unit,
    onFasting: (LocalDate) -> Unit,
    onResumeLogging: () -> Unit,
    onWeighIn: () -> Unit,
    modifier: Modifier = Modifier,
    showEstimateSummary: Boolean = true,
) {
    val plan = state.plan
    val interactive = !state.busy && !state.loading
    Column(modifier.fillMaxWidth().testTag("nutrition-checkin-flow"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (state.loading && plan == null) {
            Text("Preparando tu check-in…", style = KpknType.body, color = WizardColors.textMuted)
        } else if (plan == null) {
            if (state.error == null) Text("Activa un plan de nutrición para preparar tu revisión semanal.", style = KpknType.body, color = WizardColors.textMuted)
        } else if (state.availability != CheckinAvailability.NOW) {
            KpknModule(Modifier.fillMaxWidth()) {
                Text(if (state.availability == CheckinAvailability.DISMISSED) "Tu decisión de esta semana está guardada" else "Tu próxima revisión está programada",
                    style = KpknType.bodyStrong, color = WizardColors.text)
                Text("Día elegido: ${dayLabel(state.checkinDay)}", style = KpknType.body, color = WizardColors.textMuted)
            }
        } else {
            Text("Paso ${state.stepIndex + 1} de ${state.steps.size}", style = KpknType.caption, color = WizardColors.textMuted)
            if (state.loading) Text("Actualizando la estimación…", style = KpknType.caption, color = WizardColors.textMuted)
            KpknProgressBar((state.stepIndex + 1f) / state.steps.size, color = SymbolPalette.energia)
            KpknModule(Modifier.fillMaxWidth().testTag("checkin-module-${state.module}")) {
                Text(moduleTitle(state.module), style = KpknType.title, color = WizardColors.text,
                    modifier = Modifier.semantics { heading() })
                Spacer(Modifier.height(12.dp))
                when (state.module) {
                    CheckinModule.INTRODUCTION -> Text("Esta revisión reúne tus días de registro y pesajes. Tú decides qué días usar y si quieres cambiar el objetivo. Solo aplicaremos una propuesta si la aceptas.",
                        style = KpknType.body, color = WizardColors.textMuted)
                    CheckinModule.LOGGING_BREAK -> {
                        Text(plan.reason ?: "El registro está en pausa; hoy conservamos tu objetivo.", style = KpknType.body, color = WizardColors.textMuted)
                        KpknTextAction("Reanudar registro", onResumeLogging, icon = KpknLineIcons.Plus)
                    }
                    CheckinModule.FASTING -> {
                        Text("Un día vacío queda fuera del cálculo. Confirma un ayuno solo si no comiste ese día; entonces contará como 0 kcal.", style = KpknType.body, color = WizardColors.textMuted)
                        plan.fastingDays.forEach { date ->
                            Spacer(Modifier.height(12.dp))
                            Text(shortDate(date), style = KpknType.bodyStrong, color = WizardColors.text)
                            KpknSecondaryPill("Confirmar ayuno", { onFasting(date) }, Modifier.fillMaxWidth(), tag = "checkin-fasting-$date", enabled = interactive)
                        }
                        Text("Puedes continuar para dejar estos días fuera.", style = KpknType.caption, color = WizardColors.textMuted)
                    }
                    CheckinModule.PARTIAL_DAYS -> {
                        Text("Estos días tienen menos comida registrada de lo habitual. Revisa si falta alguna comida; tus registros se conservan.", style = KpknType.body, color = WizardColors.textMuted)
                        plan.partialDays.forEach { date ->
                            Spacer(Modifier.height(16.dp))
                            Text(shortDate(date), style = KpknType.bodyStrong, color = WizardColors.text)
                            KpknSecondaryPill("Marcar como parcial", { onPartial(date) }, Modifier.fillMaxWidth(), tag = "checkin-partial-$date", enabled = interactive)
                            Spacer(Modifier.height(8.dp))
                            KpknSecondaryPill("Así es mi día", { onComplete(date) }, Modifier.fillMaxWidth(), tag = "checkin-complete-$date", enabled = interactive)
                        }
                    }
                    CheckinModule.WEIGH_IN -> {
                        if (showEstimateSummary) CheckinEstimateDetails(plan.estimate) else CheckinWeighInSummary(plan.estimate)
                        if ((plan.estimate?.quality?.weighIns ?: 0) < ExpenditureParams.V1.minimumWeighIns) {
                            Spacer(Modifier.height(12.dp))
                            Text("Un pesaje reciente ayuda a conocer tu tendencia. Puedes añadirlo ahora o seguir con la revisión.", style = KpknType.body, color = WizardColors.textMuted)
                        }
                        KpknTextAction("Registrar pesaje", onWeighIn, icon = KpknLineIcons.Body)
                    }
                    CheckinModule.TARGET_UPDATE -> {
                        if (showEstimateSummary) {
                            if (CheckinModule.TARGET_UPDATE !in state.preferences.dismissedModules) CheckinEstimateDetails(plan.estimate)
                            Spacer(Modifier.height(16.dp))
                        }
                        CheckinTargetProposal(plan)
                    }
                }
                if (state.module !in state.preferences.dismissedModules && !state.busy) {
                    Spacer(Modifier.height(12.dp))
                    KpknTextAction("No volver a mostrar", { onDismissModule(state.module) }, color = WizardColors.textMuted,
                        modifier = Modifier.testTag("checkin-dismiss-module"))
                }
            }
            if (state.module == CheckinModule.TARGET_UPDATE) {
                KpknPrimaryPill(if (state.busy) "Guardando…" else "Aceptar", onAccept, Modifier.fillMaxWidth(),
                    tag = "checkin-accept", enabled = interactive && plan.proposal != null)
                KpknSecondaryPill("Declinar", onDecline, Modifier.fillMaxWidth(), tag = "checkin-decline", enabled = interactive)
                KpknSecondaryPill("Posponer", onPostpone, Modifier.fillMaxWidth(), tag = "checkin-postpone", enabled = interactive)
            } else {
                KpknPrimaryPill("Continuar", onNext, Modifier.fillMaxWidth(), tag = "checkin-next", enabled = interactive)
            }
            if (state.stepIndex > 0 && !state.busy) KpknTextAction("Paso anterior", onPrevious, leadingIcon = KpknLineIcons.ChevronLeft)
        }
        state.error?.let {
            CheckinErrorNotice(it)
        }
    }
}

/** El aviso se anuncia y entra en la ventana después de medirlo, también dentro de una lista. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CheckinErrorNotice(message: String) {
    val requester = remember { BringIntoViewRequester() }
    var placed by remember(message) { mutableStateOf(false) }
    LaunchedEffect(message, placed) { if (placed) requester.bringIntoView() }
    KpknModule(
        Modifier.fillMaxWidth().testTag("checkin-error").bringIntoViewRequester(requester)
            .onGloballyPositioned { placed = true }.semantics { liveRegion = LiveRegionMode.Polite },
        accent = WizardColors.danger,
    ) {
        Text(message, style = KpknType.bodyStrong, color = WizardColors.text)
    }
}

@Composable
private fun CheckinTargetProposal(plan: CheckinPlan) {
    val proposal = plan.proposal
    if (proposal == null) {
        Text("Hoy conservamos tu objetivo", style = KpknType.bodyStrong, color = WizardColors.text)
        Spacer(Modifier.height(8.dp))
        KpknMetric(plan.basePlan.calorieTarget.takeIf { it > 0 }?.toString() ?: "—", "kcal", style = KpknType.metricL)
        Spacer(Modifier.height(8.dp))
        Text(plan.reason ?: "Aún faltan datos para ajustar.", style = KpknType.body, color = WizardColors.textMuted)
    } else {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text("Actual", style = KpknType.caption, color = WizardColors.textMuted)
                KpknMetric(proposal.beforeKcal.toString(), "kcal", style = KpknType.metricL)
            }
            Column(Modifier.weight(1f)) {
                Text("Propuesto", style = KpknType.caption, color = WizardColors.textMuted)
                KpknMetric(proposal.afterKcal.toString(), "kcal", style = KpknType.metricL)
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(if (proposal.afterKcal == proposal.beforeKcal) "Tu objetivo ya está alineado; puedes confirmarlo sin cambios."
            else "Si aceptas, el objetivo nuevo rige desde mañana (${shortDate(LocalDate.parse(proposal.effectiveFrom))}).",
            style = KpknType.body, color = WizardColors.textMuted)
    }
    plan.estimate?.quality?.let { quality ->
        Spacer(Modifier.height(16.dp))
        Text("Usé ${quality.usedDays} días; excluí ${quality.excludedDates.size}", style = KpknType.bodyStrong,
            color = WizardColors.text, modifier = Modifier.testTag("checkin-quality"))
        val start = plan.estimate.date.minusDays(quality.windowDays.toLong() - 1)
        val used = (0 until quality.windowDays).map { start.plusDays(it.toLong()) }.filterNot { it in quality.excludedDates }
        if (used.isNotEmpty()) Text("Usados: ${used.joinToString { shortDate(it) }}", style = KpknType.caption, color = WizardColors.textMuted)
        quality.excludedDates.entries.groupBy { it.value }.forEach { (reason, days) ->
            Spacer(Modifier.height(8.dp))
            Text("${exclusionReasonLabel(reason)}: ${days.joinToString { shortDate(it.key) }}", style = KpknType.caption, color = WizardColors.textMuted)
        }
    }
}

@Composable
fun CheckinDaySetting(day: DayOfWeek, onSelect: (DayOfWeek) -> Unit, modifier: Modifier = Modifier) {
    KpknModule(modifier.fillMaxWidth()) {
        KpknModuleHeader(label = "Día del check-in")
        DayOfWeek.values().forEach { candidate ->
            KpknRadioRow(candidate == day, dayLabel(candidate).replaceFirstChar { it.titlecase(Locale.forLanguageTag("es")) }, { onSelect(candidate) })
        }
    }
}

private fun moduleTitle(module: CheckinModule): String = when (module) {
    CheckinModule.INTRODUCTION -> "Tu revisión, paso a paso"
    CheckinModule.LOGGING_BREAK -> "Retoma a tu ritmo"
    CheckinModule.FASTING -> "Días sin comida registrada"
    CheckinModule.PARTIAL_DAYS -> "Revisa estos días"
    CheckinModule.WEIGH_IN -> "Tu tendencia de peso"
    CheckinModule.TARGET_UPDATE -> "Tu objetivo para mañana"
}

private fun trendLabel(trendKg: Double): String =
    "Tendencia: ≈ ${String.format(Locale.forLanguageTag("es"), "%.1f", trendKg)} kg"

private fun shortDate(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("dd/MM"))
private fun dayLabel(day: DayOfWeek): String = day.getDisplayName(TextStyle.FULL, Locale.forLanguageTag("es"))
