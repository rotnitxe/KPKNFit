package com.example.kpkn.screens.competitions

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.kpkn.data.competitions.PowerliftingFederationCatalog
import com.example.kpkn.data.models.CompetitionAttempt
import com.example.kpkn.data.models.CompetitionAttemptResult
import com.example.kpkn.data.models.CompetitionEquipment
import com.example.kpkn.data.models.CompetitionTechnicalBlock
import com.example.kpkn.domain.competitions.AttemptPlanner
import com.example.kpkn.domain.competitions.AttemptStrategy
import com.example.kpkn.domain.competitions.CompetitionAttempts
import com.example.kpkn.domain.competitions.CompetitionDiscipline
import com.example.kpkn.domain.competitions.CompetitionFormat
import com.example.kpkn.domain.competitions.CompetitionInsights
import com.example.kpkn.domain.competitions.CompetitionResultForm
import com.example.kpkn.domain.competitions.CompetitionScoring
import com.example.kpkn.domain.competitions.WeighInPlanner
import com.example.kpkn.domain.competitions.liftLabel
import com.example.kpkn.domain.competitions.shortLabel
import com.example.kpkn.screens.competitions.design.CompAttemptGrid
import com.example.kpkn.screens.competitions.design.CompBackRow
import com.example.kpkn.screens.competitions.design.CompBig
import com.example.kpkn.screens.competitions.design.CompCap
import com.example.kpkn.screens.competitions.design.CompCenteredAction
import com.example.kpkn.screens.competitions.design.CompChips
import com.example.kpkn.screens.competitions.design.CompColors
import com.example.kpkn.screens.competitions.design.CompField
import com.example.kpkn.screens.competitions.design.CompGroupLabel
import com.example.kpkn.screens.competitions.design.CompInputField
import com.example.kpkn.screens.competitions.design.CompLabel
import com.example.kpkn.screens.competitions.design.CompMGroup
import com.example.kpkn.screens.competitions.design.CompModHeader
import com.example.kpkn.screens.competitions.design.CompModule
import com.example.kpkn.screens.competitions.design.CompPChip
import com.example.kpkn.screens.competitions.design.CompPill
import com.example.kpkn.screens.competitions.design.CompScreen
import com.example.kpkn.screens.competitions.design.CompSheetSub
import com.example.kpkn.screens.competitions.design.CompSheetTitle
import com.example.kpkn.screens.competitions.design.CompStepTitle
import com.example.kpkn.screens.competitions.design.CompSteps
import com.example.kpkn.screens.competitions.design.CompToggleRow
import com.example.kpkn.screens.competitions.design.CompTotalBar
import com.example.kpkn.screens.competitions.design.CompType
import com.example.kpkn.screens.competitions.design.CompetitionIcons
import com.example.kpkn.ui.components.KpknNativeTimePickerDialog
import java.time.LocalDate

/**
 * Los recorridos de competencias de Registros v4 en una ruta: [mode] elige el recorrido y [disciplineId] la disciplina de una nueva.
 * [onFinished] abre la ficha de la competencia guardada; [onShare] es el punto de conexión con Historias.
 */
@Composable
fun CompetitionFlowScreen(
    competitionId: String,
    mode: String,
    disciplineId: String?,
    onBack: () -> Unit,
    onFinished: (String) -> Unit,
    onShare: (String) -> Unit,
) {
    val flowMode = CompetitionFlowMode.of(mode)
    val viewModel: CompetitionFlowViewModel = viewModel(key = "competition-flow:$competitionId:$mode:${disciplineId.orEmpty()}") {
        CompetitionFlowViewModel(competitionId, flowMode, disciplineId)
    }
    val ui by viewModel.ui.collectAsState()
    val context = LocalContext.current
    LaunchedEffect(ui.message) {
        ui.message?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show(); viewModel.consumeMessage() }
    }
    LaunchedEffect(ui.finishedId, ui.finishedBack) {
        val id = ui.finishedId ?: return@LaunchedEffect
        if (ui.finishedBack) onBack() else onFinished(id)
    }
    BackHandler { if (!viewModel.back()) onBack() }
    if (!ui.ready) {
        Box(Modifier.fillMaxSize().background(Color.Black))
        return
    }
    val goBack = { if (!viewModel.back()) onBack() }
    when (ui.step) {
        CompetitionFlowStep.DATA -> CompetitionDataStep(ui, viewModel, goBack)
        CompetitionFlowStep.PLAN -> CompetitionPlanStep(ui, viewModel, goBack)
        CompetitionFlowStep.TODAY -> CompetitionTodayStep(ui, viewModel, goBack)
        CompetitionFlowStep.RESULT -> CompetitionResultStep(ui, viewModel, goBack)
        CompetitionFlowStep.PLACE -> CompetitionPlaceStep(ui, viewModel, goBack)
        CompetitionFlowStep.FEEL -> CompetitionFeelStep(ui, viewModel, goBack)
        CompetitionFlowStep.CELE -> CompetitionCelebration(ui, onShare = { onShare(ui.record.id) }, onOpen = { onFinished(ui.record.id) })
    }
}

/** El rótulo de la fila de atrás de cada recorrido. */
internal fun flowBackTitle(ui: CompetitionFlowUi): String = when (ui.mode) {
    CompetitionFlowMode.NEXT -> if (ui.step == CompetitionFlowStep.PLAN) "Plan de intentos" else "Voy a competir"
    CompetitionFlowMode.PLAN -> "Plan de intentos"
    CompetitionFlowMode.TODAY -> if (ui.step == CompetitionFlowStep.TODAY) "Hoy compites" else "Ya competí"
    CompetitionFlowMode.EDIT -> "Editar resultado"
    CompetitionFlowMode.DONE, CompetitionFlowMode.LOG -> "Ya competí"
}

/** La barra de pasos del resultado: resultado (o el modo competición), puesto y recuerdos. */
@Composable
internal fun FlowResultSteps(ui: CompetitionFlowUi, viewModel: CompetitionFlowViewModel) {
    val tail = viewModel.steps(ui).filter { it == CompetitionFlowStep.RESULT || it == CompetitionFlowStep.TODAY || it == CompetitionFlowStep.PLACE || it == CompetitionFlowStep.FEEL }
    val index = tail.indexOf(ui.step)
    if (index >= 0) CompSteps(on = index + 1, total = tail.size)
}

// ─── c-next: «¿Dónde compites?» ───────────────────────────────────────────────────────────────────────────────

private fun weightClasses(female: Boolean): List<String> =
    if (female) listOf("47", "52", "57", "63", "69", "76", "84", "84+") else listOf("59", "66", "74", "83", "93", "105", "120", "120+")

private fun liftingClasses(female: Boolean): List<String> =
    if (female) listOf("48", "53", "58", "63", "69", "77", "86", "86+") else listOf("60", "65", "71", "79", "88", "94", "110", "110+")

private val divisions = listOf("Open", "Junior", "Sub-junior", "Máster 1", "Máster 2", "Máster 3")
private val equipments = listOf("Classic" to CompetitionEquipment.CLASSIC, "Rodilleras" to CompetitionEquipment.WRAPS, "Equipado" to CompetitionEquipment.EQUIPPED)
private val runDistances = listOf("5 km" to 5.0, "10 km" to 10.0, "Media maratón" to 21.0975, "Maratón" to 42.195)

@Composable
private fun CompetitionDataStep(ui: CompetitionFlowUi, viewModel: CompetitionFlowViewModel, onBack: () -> Unit) {
    val record = ui.record
    val discipline = ui.discipline
    val planned = ui.mode == CompetitionFlowMode.NEXT
    var dateSheet by remember { mutableStateOf(false) }
    var timeDialog by remember { mutableStateOf(false) }
    var fedSheet by remember { mutableStateOf(false) }
    var targetSheet by remember { mutableStateOf(false) }
    val date = CompetitionFormat.parseDate(record.eventDate)
    val female = record.powerliftingDetails?.sexCategory == "female"
    CompScreen {
        CompBackRow(flowBackTitle(ui), onBack)
        if (planned && discipline == CompetitionDiscipline.POWERLIFTING) CompSteps(on = 1, total = 3)
        CompStepTitle(if (planned) "¿Dónde compites?" else "¿Dónde competiste?")
        CompInputField("Nombre", record.title, viewModel::setTitle)
        val weighIn = discipline.hasWeighIn
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CompField("Fecha", if (date == null) "Elegir" else CompetitionFormat.weekdayDate(date), { dateSheet = true }, Modifier.weight(1f), placeholder = date == null)
            when {
                planned -> CompField(
                    if (weighIn) "Hora del pesaje" else if (discipline.form == CompetitionResultForm.CLOCK) "Hora de salida" else "Hora",
                    record.startTime ?: "Elegir", { timeDialog = true }, Modifier.weight(1f), placeholder = record.startTime == null,
                )
                weighIn -> CompInputField("Peso en el pesaje", record.bodyweightKg?.let { CompetitionFormat.kg(it) }.orEmpty(), viewModel::setBodyweight,
                    Modifier.weight(1f), placeholder = "kg", keyboard = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            }
        }
        CompInputField("Lugar", record.location.orEmpty(), viewModel::setLocation)
        when (discipline) {
            CompetitionDiscipline.POWERLIFTING -> {
                val fed = PowerliftingFederationCatalog.byId(record.federationId)
                val formula = CompetitionScoring.formulaFor(record).label
                val value = when {
                    fed != null -> "${fed.shortName} · $formula"
                    !record.federation.isNullOrBlank() -> "${record.federation} · $formula"
                    else -> "Elegir"
                }
                CompField("Federación · puntos", value, { fedSheet = true }, placeholder = value == "Elegir")
                CompMGroup("Categoría")
                val classes = weightClasses(female)
                CompChips(classes.map { CompetitionFormat.weightClass(it).orEmpty() }, { classes[it] == record.powerliftingDetails?.weightClass }) {
                    viewModel.setWeightClass(classes[it])
                }
                CompMGroup("División y equipo")
                CompChips(divisions, { divisions[it] == record.powerliftingDetails?.division }) { viewModel.setDivision(divisions[it]) }
                Spacer(Modifier.height(6.dp))
                CompChips(equipments.map { it.first }, { equipments[it].second == record.powerliftingDetails?.equipment }) {
                    viewModel.setEquipment(equipments[it].second)
                }
            }
            CompetitionDiscipline.WEIGHTLIFTING -> {
                CompMGroup("Categoría")
                val classes = liftingClasses(female)
                CompChips(classes.map { CompetitionFormat.weightClass(it).orEmpty() }, { classes[it] == record.targetWeightClass }) { viewModel.setWeightClass(classes[it]) }
            }
            else -> DisciplineFields(ui, viewModel, planned) { targetSheet = true }
        }
        if (planned) {
            CompMGroup("Te avisamos")
            val firstSub = if (weighIn) "Revisar pesaje e intentos" else "Revisar tu preparación"
            val startSub = when {
                weighIn -> "Con la hora del pesaje"
                discipline.form == CompetitionResultForm.CLOCK -> "Con la hora de salida"
                else -> "Con la hora de la competencia"
            }
            CompToggleRow("Una semana antes", firstSub, record.reminderOneWeekEnabled, { viewModel.setReminders(week = it) }, divider = false)
            CompToggleRow("48 horas antes", "Plan del día listo", record.reminder48hEnabled, { viewModel.setReminders(h48 = it) })
            CompToggleRow("Al empezar", startSub, record.reminderStartEnabled, { viewModel.setReminders(start = it) })
        }
        CompPill("Continuar", viewModel::next, Modifier.fillMaxWidth().padding(top = 16.dp))
    }
    val today = LocalDate.now()
    if (dateSheet) CompDateSheet("Fecha", date, viewModel::setDate, { dateSheet = false },
        min = if (planned) today else null, max = if (planned) null else today)
    if (timeDialog) {
        val (h, m) = record.startTime?.split(":")?.mapNotNull { it.toIntOrNull() }?.takeIf { it.size == 2 }?.let { it[0] to it[1] } ?: (8 to 30)
        KpknNativeTimePickerDialog(title = if (weighInTitle(discipline)) "Hora del pesaje" else "Hora", initialHour = h, initialMinute = m,
            onConfirm = { hh, mm -> viewModel.setStartTime(hh, mm); timeDialog = false }, onDismiss = { timeDialog = false })
    }
    if (fedSheet) CompFederationSheet(record.federationId, record.federation, { id, name -> viewModel.setFederation(id, name) }, { fedSheet = false })
    if (targetSheet) CompDurationSheet("Tu objetivo", record.targetTimeSeconds, viewModel::setTargetTime, { targetSheet = false })
}

private fun weighInTitle(discipline: CompetitionDiscipline) = discipline.hasWeighIn

/** Los datos propios de cada forma de resultado en c-next (patrón de c-next: campos y chips). */
@Composable
private fun DisciplineFields(ui: CompetitionFlowUi, viewModel: CompetitionFlowViewModel, planned: Boolean, onTarget: () -> Unit) {
    val record = ui.record
    val discipline = ui.discipline
    when (discipline.form) {
        CompetitionResultForm.CLOCK -> {
            when (discipline) {
                CompetitionDiscipline.SPRINT -> CompInputField("Distancia (m)", record.distanceKm?.let { CompetitionFormat.kg(it * 1000) }.orEmpty(),
                    { viewModel.setDistance(CompetitionFormat.parseKg(it)?.div(1000)) }, keyboard = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                CompetitionDiscipline.JUMP -> CompInputField("Prueba", record.category.orEmpty(), viewModel::setCategory, placeholder = "Longitud, altura, triple…")
                else -> {
                    if (discipline == CompetitionDiscipline.RUNNING || discipline == CompetitionDiscipline.TRAIL) {
                        CompMGroup("Distancia")
                        CompChips(runDistances.map { it.first }, { CompetitionInsights.sameDistance(record.distanceKm, runDistances[it].second) }) {
                            viewModel.setDistance(runDistances[it].second)
                        }
                    }
                    CompInputField("Distancia (km)", record.distanceKm?.let { CompetitionFormat.kg(Math.round(it * 100) / 100.0) }.orEmpty(),
                        { viewModel.setDistance(CompetitionFormat.parseKg(it)) }, keyboard = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    if (planned) CompField("Tu objetivo", record.targetTimeSeconds?.let { CompetitionFormat.duration(it) } ?: "Elegir", onTarget,
                        placeholder = record.targetTimeSeconds == null)
                }
            }
            CompInputField("Categoría", record.category.orEmpty(), viewModel::setCategory, placeholder = "M35, Open…")
        }
        CompetitionResultForm.COMBAT -> {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CompInputField("Categoría de peso", record.targetWeightClass.orEmpty(), viewModel::setWeightClass, Modifier.weight(1f), placeholder = "-82,3",
                    keyboard = KeyboardOptions(keyboardType = KeyboardType.Text))
                CompInputField("Nivel y división", record.category.orEmpty(), viewModel::setCategory, Modifier.weight(1f), placeholder = "Azul · adulto")
            }
        }
        CompetitionResultForm.STAGE -> {
            CompInputField("Categoría", record.category.orEmpty(), viewModel::setCategory, placeholder = "Men’s Physique · -178 cm")
            if (planned) CompInputField("Peso en tarima", record.bodybuildingDetails?.stageWeightKg?.let { CompetitionFormat.kg(it) }.orEmpty(), viewModel::setStageWeight,
                placeholder = "kg", keyboard = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        }
        CompetitionResultForm.MATCH -> if (discipline == CompetitionDiscipline.CLIMBING) {
            CompInputField("Modalidad", record.category.orEmpty(), viewModel::setCategory, placeholder = "Bloque, dificultad, velocidad")
        } else {
            if (discipline == CompetitionDiscipline.TENNIS || discipline == CompetitionDiscipline.PADEL) {
                CompInputField("Pareja", record.partner.orEmpty(), viewModel::setPartner, placeholder = "Opcional")
            }
            CompInputField("Categoría", record.category.orEmpty(), viewModel::setCategory, placeholder = "2.ª categoría")
        }
        CompetitionResultForm.LOAD, CompetitionResultForm.MARKS -> CompInputField("Categoría", record.category.orEmpty(), viewModel::setCategory, placeholder = "Open")
    }
}

// ─── c-plan: «Tus nueve intentos» ─────────────────────────────────────────────────────────────────────────────

private val strategies = listOf(AttemptStrategy.CONSERVATIVE to "Conservadora", AttemptStrategy.TARGET to "Objetivo", AttemptStrategy.AMBITIOUS to "Ambiciosa")

@Composable
private fun CompetitionPlanStep(ui: CompetitionFlowUi, viewModel: CompetitionFlowViewModel, onBack: () -> Unit) {
    val record = ui.record
    var editing by remember { mutableStateOf<Pair<com.example.kpkn.data.models.CompetitionMovementType, Int>?>(null) }
    val strategy = viewModel.strategy(record)
    val rows = AttemptPlanner.lifts.map { movement ->
        CompetitionTechnicalBlock(
            id = movement.name,
            title = movement.liftLabel(),
            movementType = movement,
            attempts = (1..3).map { n ->
                CompetitionAttempt(id = "${movement.name}-$n", attemptNumber = n,
                    weightKg = record.plannedAttempts.firstOrNull { it.movementType == movement && it.attemptNumber == n }?.weightKg)
            },
        )
    }
    val total = CompetitionAttempts.totalIfAllMake(record.plannedAttempts)
    val bodyweight = ui.currentBodyweightKg ?: record.bodyweightKg
    val points = CompetitionScoring.calculatePoints(total, bodyweight, CompetitionScoring.scoringSex(record), CompetitionScoring.formulaFor(record),
        CompetitionScoring.ipfEquipment(record))
    CompScreen {
        CompBackRow(flowBackTitle(ui), onBack)
        if (ui.mode == CompetitionFlowMode.NEXT) CompSteps(on = 2, total = 3)
        CompStepTitle("Tus nueve intentos")
        val marks = AttemptPlanner.lifts.mapNotNull { m -> ui.marks[m]?.let { "${m.shortLabel()} ${CompetitionFormat.kg(it)}" } }
        CompCap(if (marks.isEmpty()) "Sin marcas en tu programa: toca cada intento para escribirlo." else "Desde tus marcas: ${marks.joinToString(" · ")}",
            Modifier.padding(top = 6.dp))
        CompMGroup("Estrategia")
        CompChips(strategies.map { it.second }, { strategies[it].first == strategy }) { viewModel.chooseStrategy(strategies[it].first) }
        CompAttemptGrid(rows, pendingDashed = true, onTap = { block, n -> editing = block.movementType to n })
        CompTotalBar(left = {
            CompLabel("Total si entran")
            CompBig(CompetitionFormat.kg(total), 40, "kg")
        }) {
            Column(horizontalAlignment = Alignment.End) {
                CompCap(CompetitionScoring.formulaFor(record).label)
                Text(points?.let { CompetitionFormat.points(it.value) } ?: "—", style = CompType.syne(20, line = 1.1f), color = CompColors.ink)
            }
        }
        WeighInModule(ui)
        CompPill("Guardar plan", viewModel::next, Modifier.fillMaxWidth().padding(top = 6.dp))
    }
    editing?.let { (movement, n) ->
        CompWeightSheet(
            title = "${movement.liftLabel()} · ${CompetitionFormat.ordinal(n)} intento",
            initialKg = record.plannedAttempts.firstOrNull { it.movementType == movement && it.attemptNumber == n }?.weightKg,
            onConfirm = { viewModel.setPlannedWeight(movement, n, it) },
            onDismiss = { editing = null },
        )
    }
    ui.pendingStrategy?.let { pending ->
        CompSheet(viewModel::dismissStrategy, scroll = false) {
            CompSheetTitle("Cambiaste intentos a mano")
            CompSheetSub("La estrategia «${strategies.first { it.first == pending }.second}» puede recalcularlos todos o respetar los tuyos.")
            CompPill("Mantener los míos", { viewModel.applyStrategy(pending, keepManual = true) }, Modifier.fillMaxWidth().padding(top = 8.dp))
            CompPill("Recalcular todos", { viewModel.applyStrategy(pending, keepManual = false) }, Modifier.fillMaxWidth().padding(top = 8.dp), primary = false)
            CompCenteredAction("Cancelar", viewModel::dismissStrategy)
        }
    }
}

/** El módulo «Pesaje» de c-plan: tu peso de hoy frente al límite y la proyección de [WeighInPlanner]. */
@Composable
internal fun WeighInModule(ui: CompetitionFlowUi) {
    val record = ui.record
    val target = record.targetBodyweightKg ?: CompetitionFormat.weightClassLimit(record.powerliftingDetails?.weightClass ?: record.targetWeightClass) ?: return
    val current = ui.currentBodyweightKg
    val days = CompetitionInsights.daysUntil(record, LocalDate.now())?.takeIf { it >= 0 }?.toInt()
    CompModule(Modifier.padding(top = 12.dp)) {
        CompModHeader("Pesaje") { CompCap("objetivo ≤ ${CompetitionFormat.kg(target)} kg") }
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CompBig(current?.let { CompetitionFormat.kg(it) } ?: "—", 28, "kg")
            if (current != null) {
                val inside = current <= target
                CompPChip(if (inside) "Dentro" else "Por encima", icon = if (inside) CompetitionIcons.Check else null,
                    fill = if (inside) Color(0x1A43D18C) else Color(0x1AFF9B92), ink = if (inside) CompColors.done else CompColors.danger)
            }
        }
        val caption = if (current != null && days != null) CompetitionInsights.weighInCaption(WeighInPlanner.forecast(current, target, ui.weeklyRateKg, days))
        else if (current == null) "Registra tu peso en la pestaña Cuerpo para proyectar el pesaje." else null
        caption?.let { CompCap(it, Modifier.padding(top = 6.dp)) }
    }
}

// ─── c-today: «Hoy compites» ──────────────────────────────────────────────────────────────────────────────────

@Composable
private fun CompetitionTodayStep(ui: CompetitionFlowUi, viewModel: CompetitionFlowViewModel, onBack: () -> Unit) {
    val record = ui.record
    val discipline = ui.discipline
    val current = CompetitionAttempts.current(record, discipline)
    var editing by remember { mutableStateOf<Pair<CompetitionTechnicalBlock, Int>?>(null) }
    CompScreen {
        CompBackRow(flowBackTitle(ui), onBack) {
            Row(
                Modifier.height(28.dp).clip(RoundedCornerShape(14.dp)).background(CompColors.inset).padding(horizontal = 11.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(CompColors.mus))
                Text("En vivo", style = CompType.chip, color = CompColors.muted)
            }
        }
        if (current != null) {
            val label = current.block.movementType.liftLabel(current.block.title)
            CompLabel("$label · ${CompetitionFormat.ordinal(current.attempt.attemptNumber)} intento", color = CompColors.mus)
            CompBig(CompetitionFormat.kg(current.attempt.weightKg), 72, "kg", Modifier.padding(top = 6.dp))
            CompCap(CompetitionAttempts.nextCaption(record, current), Modifier.padding(top = 4.dp))
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CompPill("Válido", { viewModel.decide(CompetitionAttemptResult.GOOD_LIFT) }, Modifier.weight(1f), icon = CompetitionIcons.Check, height = 64.dp,
                    container = CompColors.done, content = Color(0xFF062012), textStyle = CompType.inter(17f, FontWeight.SemiBold, 1.2f))
                CompPill("Nulo", { viewModel.decide(CompetitionAttemptResult.NO_LIFT) }, Modifier.weight(1f), icon = CompetitionIcons.Close, height = 64.dp,
                    container = Color(0x26FF9B92), content = CompColors.danger, border = Color(0x66FF9B92), textStyle = CompType.inter(17f, FontWeight.SemiBold, 1.2f))
            }
        }
        CompGroupLabel("La tabla")
        CompAttemptGrid(
            CompetitionAttempts.rows(record, discipline),
            labelWidth = if (discipline == CompetitionDiscipline.POWERLIFTING) 72.dp else 96.dp,
            currentId = current?.attempt?.id,
            pendingDashed = true,
            onTap = { block, n -> viewModel.toggleToday(block, n) },
            onLongPress = { block, n -> editing = block to n },
        )
        current?.let { CompetitionAttempts.nextLiftWarmup(record, discipline, it) }?.let { (title, line) ->
            CompModule(Modifier.padding(top = 14.dp)) {
                Text(title.uppercase(java.util.Locale("es")), style = CompType.ey, color = CompColors.muted)
                Text(buildAnnotatedString {
                    append("$line ")
                    withStyle(SpanStyle(color = CompColors.faint)) { append("(hasta el 92 % del primero)") }
                }, Modifier.padding(top = 6.dp), style = CompType.inter(13f, line = 1.7f), color = CompColors.faint)
            }
        }
        if (current == null) CompPill("Continuar", viewModel::next, Modifier.fillMaxWidth().padding(top = 16.dp))
    }
    editing?.let { (block, n) ->
        AttemptLongPressSheet(ui, viewModel, block, n) { editing = null }
    }
}

/**
 * El menú largo de un intento (c-log): el peso con ±2,5, «Saltado» y, en las filas de un solo movimiento, «Cambiar movimiento» (C-D6).
 */
@Composable
internal fun AttemptLongPressSheet(ui: CompetitionFlowUi, viewModel: CompetitionFlowViewModel, block: CompetitionTechnicalBlock, attemptNumber: Int, onDismiss: () -> Unit) {
    var changing by remember { mutableStateOf(false) }
    val attempt = block.attempts.firstOrNull { it.attemptNumber == attemptNumber }
    if (changing) {
        ChangeMovementSheet(viewModel, block, onDismiss)
        return
    }
    CompWeightSheet(
        title = "${block.movementType.liftLabel(block.title)} · ${CompetitionFormat.ordinal(attemptNumber)} intento",
        initialKg = attempt?.weightKg,
        onConfirm = { viewModel.setAttemptWeight(block, attemptNumber, it) },
        onDismiss = onDismiss,
    ) {
        Spacer(Modifier.height(8.dp))
        com.example.kpkn.screens.competitions.design.CompMenuRow(
            CompetitionIcons.Repeat, if (attempt?.resultType == CompetitionAttemptResult.SKIPPED) "Quitar «Saltado»" else "Saltado", "No lo intentaste",
            onClick = {
                viewModel.setAttemptResult(block, attemptNumber,
                    if (attempt?.resultType == CompetitionAttemptResult.SKIPPED) CompetitionAttemptResult.PENDING else CompetitionAttemptResult.SKIPPED)
                onDismiss()
            }, divider = false,
        )
        if (ui.discipline != CompetitionDiscipline.WEIGHTLIFTING) {
            com.example.kpkn.screens.competitions.design.CompMenuRow(CompetitionIcons.Swap, "Cambiar movimiento", block.exerciseName ?: block.movementType.liftLabel(block.title),
                onClick = { changing = true })
        }
    }
}

/** «Cambiar movimiento»: el buscador del catálogo, ahora fuera del recorrido principal (C-D6). */
@Composable
private fun ChangeMovementSheet(viewModel: CompetitionFlowViewModel, block: CompetitionTechnicalBlock, onDismiss: () -> Unit) {
    var query by remember { mutableStateOf("") }
    val results = remember(query) { if (query.isBlank()) emptyList() else viewModel.suggestions(query).take(8) }
    CompSheet(onDismiss) {
        CompSheetTitle("Cambiar movimiento")
        CompSheetSub("La fila «${block.movementType.liftLabel(block.title)}» pasa a contar este ejercicio.")
        CompInputField("Buscar", query, { query = it }, placeholder = "Nombre del ejercicio")
        results.forEach { suggestion ->
            com.example.kpkn.screens.competitions.design.CompSheetOption(suggestion.exercise.name, null, block.exerciseDbId == suggestion.exercise.id,
                onClick = { viewModel.bindExercise(block, suggestion.exercise); onDismiss() })
        }
        if (query.isNotBlank()) {
            CompPill("Usar «${query.trim()}»", { viewModel.useCustomExercise(block, query.trim()); onDismiss() }, Modifier.fillMaxWidth().padding(top = 12.dp), primary = false)
        }
    }
}

/** El total de la tabla, con sus puntos, para la barra (c-log, c-lift). */
internal data class LoadTotals(val total: Double?, val pointsLabel: String?, val points: Double?, val delta: Double?)

internal fun loadTotals(ui: CompetitionFlowUi, record: com.example.kpkn.data.models.CompetitionRecord = ui.record): LoadTotals {
    val discipline = ui.discipline
    val total = CompetitionAttempts.total(record, discipline)
    val scored = if (discipline == CompetitionDiscipline.POWERLIFTING) CompetitionScoring.recalculate(record) else record
    val points = CompetitionInsights.points(scored, discipline)
    val resolver: (com.example.kpkn.data.models.CompetitionRecord) -> CompetitionDiscipline = { r -> if (r.id == record.id) discipline else CompetitionDiscipline.of(r) }
    val previous = CompetitionInsights.previousOf(ui.records, record, resolver).firstOrNull()?.let { CompetitionAttempts.total(it, discipline) }
    return LoadTotals(total, CompetitionInsights.pointsLabel(record, discipline), points?.second, if (total != null && previous != null) total - previous else null)
}

