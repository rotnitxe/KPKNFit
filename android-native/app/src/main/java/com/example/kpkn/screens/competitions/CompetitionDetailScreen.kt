package com.example.kpkn.screens.competitions

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.kpkn.data.competitions.PowerliftingFederationCatalog
import com.example.kpkn.data.models.CompetitionRecord
import com.example.kpkn.data.models.CompetitionRecordStatus
import com.example.kpkn.domain.competitions.CompetitionAttempts
import com.example.kpkn.domain.competitions.CompetitionDiscipline
import com.example.kpkn.domain.competitions.CompetitionFeeling
import com.example.kpkn.domain.competitions.CompetitionFormat
import com.example.kpkn.domain.competitions.CompetitionInsights
import com.example.kpkn.domain.competitions.CompetitionPlaceHonors
import com.example.kpkn.domain.competitions.CompetitionResultForm
import com.example.kpkn.domain.competitions.liftLabel
import com.example.kpkn.screens.competitions.design.CompAttemptGrid
import com.example.kpkn.screens.competitions.design.CompBackRow
import com.example.kpkn.screens.competitions.design.CompBig
import com.example.kpkn.screens.competitions.design.CompBoutRow
import com.example.kpkn.screens.competitions.design.CompCap
import com.example.kpkn.screens.competitions.design.CompCenteredAction
import com.example.kpkn.screens.competitions.design.CompColors
import com.example.kpkn.screens.competitions.design.CompCountdown
import com.example.kpkn.screens.competitions.design.CompGroupLabel
import com.example.kpkn.screens.competitions.design.CompIconButton
import com.example.kpkn.screens.competitions.design.CompKpis
import com.example.kpkn.screens.competitions.design.CompLabel
import com.example.kpkn.screens.competitions.design.CompMedal
import com.example.kpkn.screens.competitions.design.CompMenuRow
import com.example.kpkn.screens.competitions.design.CompModHeader
import com.example.kpkn.screens.competitions.design.CompModule
import com.example.kpkn.screens.competitions.design.CompNextCard
import com.example.kpkn.screens.competitions.design.CompOpeners
import com.example.kpkn.screens.competitions.design.CompPhotoGrid
import com.example.kpkn.screens.competitions.design.CompPill
import com.example.kpkn.screens.competitions.design.CompRatioRow
import com.example.kpkn.screens.competitions.design.CompScreen
import com.example.kpkn.screens.competitions.design.CompSheetSub
import com.example.kpkn.screens.competitions.design.CompSheetTitle
import com.example.kpkn.screens.competitions.design.CompSplits
import com.example.kpkn.screens.competitions.design.CompTextAction
import com.example.kpkn.screens.competitions.design.CompToggleRow
import com.example.kpkn.screens.competitions.design.CompType
import com.example.kpkn.screens.competitions.design.CompetitionIcons
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.LineCoverArt
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.LineCoverBackground
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.LineCoverSpec
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.LineCoverStyle
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.ui.design.KpknSurface
import java.time.LocalDate

/**
 * La ficha de una competencia: la de resultado (c-detail, con c-menu, c-marks y c-compare) o la de una próxima (c-run-next, c-bb).
 * [onStartFlow] abre un recorrido (id, modo) y [onShare] es el punto de conexión con Historias.
 */
@Composable
fun CompetitionDetailScreen(
    competitionId: String,
    onBack: () -> Unit,
    onStartFlow: (id: String, mode: String) -> Unit,
    onShare: (String) -> Unit,
) {
    val viewModel: CompetitionDetailViewModel = viewModel(key = "competition-detail:$competitionId") { CompetitionDetailViewModel(competitionId) }
    val ui by viewModel.ui.collectAsState()
    val transient by viewModel.transient.collectAsState()
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var comparing by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(transient.message) {
        transient.message?.let { Toast.makeText(context, it, Toast.LENGTH_SHORT).show(); viewModel.consumeMessage() }
    }
    LaunchedEffect(transient.gone) { if (transient.gone) onBack() }
    BackHandler { if (comparing != null) comparing = null else onBack() }
    val record = ui.record
    if (!ui.ready || record == null) {
        Box(Modifier.fillMaxSize().background(Color.Black))
        return
    }
    val other = comparing?.let { id -> ui.records.firstOrNull { it.id == id } }
    when {
        other != null -> CompetitionCompare(ui, record, other, onBack = { comparing = null }, onPick = { comparing = it })
        record.status == CompetitionRecordStatus.COMPLETED -> CompletedDetail(ui, record, onBack, { menu = true }, { onShare(record.id) })
        else -> PlannedDetail(ui, record, viewModel, onBack, { menu = true }, onStartFlow)
    }
    if (menu) CompetitionMenuSheet(
        ui = ui,
        record = record,
        onDismiss = { menu = false },
        onEdit = { onStartFlow(record.id, if (record.status == CompetitionRecordStatus.COMPLETED) CompetitionFlowMode.EDIT.id else CompetitionFlowMode.NEXT.id) },
        onShare = { onShare(record.id) },
        onCompare = { comparables(ui, record).firstOrNull()?.let { comparing = it.id } },
        onMarks = viewModel::reviewMarks,
        onPlan = { onStartFlow(record.id, CompetitionFlowMode.PLAN.id) },
        onLog = { onStartFlow(record.id, CompetitionFlowMode.LOG.id) },
        onArchive = viewModel::archive,
        onDelete = { confirmDelete = true },
    )
    if (confirmDelete) CompConfirmSheet(
        title = "¿Eliminar «${record.title.ifBlank { ui.discipline.label }}»?",
        sub = "Se borran la competencia, sus intentos y sus fotos. No se puede deshacer.",
        confirm = "Eliminar",
        onConfirm = { viewModel.delete(context) },
        dismiss = "Cancelar",
        onDismiss = { confirmDelete = false },
        danger = true,
    )
    transient.marks?.let { changes ->
        CompSheet(viewModel::dismissMarks) {
            Text("DESPUÉS DE COMPETIR", style = CompType.ey, color = CompColors.muted)
            CompSheetTitle("Tus marcas suben", Modifier.padding(top = 6.dp))
            CompSheetSub("Tu programa «${ui.linkedProgramName.orEmpty()}» usa tus máximos para calcular las cargas. Con estos resultados, las semanas que faltan quedarían así:")
            changes.forEachIndexed { i, change ->
                CompRatioRow(change.movement.liftLabel(), first = i == 0) {
                    Text("${CompetitionFormat.kg(change.before)} →", style = CompType.cap, color = CompColors.faint)
                    val up = change.after != null && change.before != null && change.after > change.before
                    Text(CompetitionFormat.kg(change.after), style = CompType.syne(13, FontWeight.Bold, 1.2f), color = if (up) CompColors.done else CompColors.ink)
                }
            }
            CompPill("Usar en mi programa", viewModel::confirmMarks, Modifier.fillMaxWidth().padding(top = 16.dp), enabled = !transient.busy)
            CompCenteredAction("Ahora no", viewModel::dismissMarks)
        }
    }
}

/** Las competencias con las que se puede comparar: completadas de la misma disciplina (carga o reloj). */
private fun comparables(ui: CompetitionDetailUi, record: CompetitionRecord): List<CompetitionRecord> =
    CompetitionInsights.visible(ui.records).filter {
        it.id != record.id && it.status == CompetitionRecordStatus.COMPLETED && CompetitionPrograms.disciplineResolver(ui.programs)(it) == ui.discipline
    }.sortedByDescending { CompetitionInsights.dateOf(it) }

private fun canCompare(ui: CompetitionDetailUi, record: CompetitionRecord): Boolean =
    (ui.discipline.form == CompetitionResultForm.LOAD || ui.discipline.form == CompetitionResultForm.CLOCK) && comparables(ui, record).isNotEmpty()

// ─── c-detail ─────────────────────────────────────────────────────────────────────────────────────────────────

@Composable
private fun CompletedDetail(ui: CompetitionDetailUi, record: CompetitionRecord, onBack: () -> Unit, onMenu: () -> Unit, onShare: () -> Unit) {
    val discipline = ui.discipline
    val resolver = CompetitionPrograms.disciplineResolver(ui.programs)
    val medal = CompetitionPlaceHonors.medalOf(record)
    val reduced = wizardReducedMotion()
    Box(Modifier.fillMaxSize().background(KpknSurface.canvas)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Box(Modifier.fillMaxWidth().height(230.dp)) {
                LineCoverArt(LineCoverSpec(LineCoverStyle.RELIEVE, LineCoverBackground.CARBON, 6), Modifier.fillMaxSize(), animate = true, reduced = reduced)
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.3f to Color.Transparent, 1f to Color.Black)))
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 10.dp, end = 10.dp, top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    CompIconButton(CompetitionIcons.Back, "Atrás", onBack, glass = true)
                    CompIconButton(CompetitionIcons.More, "Más opciones", onMenu, glass = true)
                }
            }
            Column(Modifier.padding(horizontal = 16.dp).offset(y = (-56).dp)) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    CompMedal(medal, 70.dp, icon = CompetitionIcons.byKey(if (discipline.form == CompetitionResultForm.LOAD) "trophy" else discipline.icon))
                    Column(Modifier.weight(1f)) {
                        val fed = PowerliftingFederationCatalog.byId(record.federationId)?.shortName ?: record.federation
                        CompLabel(listOfNotNull(CompetitionFormat.date(CompetitionInsights.dateOf(record)), record.location?.takeIf { it.isNotBlank() }, fed?.takeIf { it.isNotBlank() })
                            .joinToString(" · "))
                        Text(record.title.ifBlank { discipline.label }, Modifier.padding(top = 4.dp), style = CompType.stepTitle, color = CompColors.ink)
                    }
                }
                CompKpis(detailKpis(ui, record))
                ResultBody(ui, record)
                val versus = CompetitionInsights.versusPrevious(ui.records, record, resolver)
                if (versus.isNotEmpty()) {
                    CompGroupLabel("Frente a la anterior")
                    CompModule(tight = true) {
                        versus.forEachIndexed { i, row ->
                            CompRatioRow(row.label, first = i == 0) {
                                Text(row.value, style = CompType.syne(13, FontWeight.Bold, 1.2f), color = CompColors.ink)
                                row.delta?.let {
                                    Text(it, style = CompType.inter(13f, FontWeight.SemiBold), color = when (row.deltaSign) {
                                        1 -> CompColors.done; -1 -> CompColors.danger; else -> CompColors.ene
                                    })
                                }
                            }
                        }
                    }
                }
                if (record.photos.isNotEmpty()) {
                    CompGroupLabel("Recuerdos")
                    CompPhotoGrid(record.photos.size, cell = { i -> MediaThumb(record.photos[i], null) }, onAdd = null)
                }
                val journal = record.journal
                val feeling = CompetitionFeeling.byId(journal?.overallFeeling)
                if (feeling != null || !journal?.whatWentWell.isNullOrBlank() || !journal?.learnings.isNullOrBlank()) {
                    CompModule(Modifier.padding(top = 12.dp)) {
                        feeling?.let {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(CompetitionIcons.byKey(it.icon), null, Modifier.size(14.dp), tint = CompColors.muted)
                                Text(it.label.uppercase(java.util.Locale("es")), style = CompType.ey, color = CompColors.muted)
                            }
                        }
                        journal?.whatWentWell?.takeIf { it.isNotBlank() }?.let { Text("«${it.trim().removeSuffix(".")}.»", Modifier.padding(top = 8.dp), style = CompType.body, color = CompColors.ink) }
                        journal?.learnings?.takeIf { it.isNotBlank() }?.let { CompCap("Para la próxima: ${it.trim()}", Modifier.padding(top = 6.dp)) }
                    }
                }
                CompPill("Compartir en historias", onShare, Modifier.fillMaxWidth(), icon = CompetitionIcons.Share)
                Spacer(Modifier.height(40.dp))
                Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            }
        }
    }
}

/** Las tres cifras de la ficha según la forma (total · puntos · pesaje en carga). */
private fun detailKpis(ui: CompetitionDetailUi, record: CompetitionRecord): List<Triple<String, String, String>> {
    val discipline = ui.discipline
    val weighIn = Triple("Pesaje", CompetitionFormat.kg(record.bodyweightKg),
        listOfNotNull("kg", CompetitionFormat.weightClass(record.powerliftingDetails?.weightClass ?: record.targetWeightClass)).joinToString(" · "))
    return when (discipline.form) {
        CompetitionResultForm.LOAD -> {
            val points = CompetitionInsights.points(record, discipline)
            listOfNotNull(
                Triple("Total", CompetitionFormat.kg(CompetitionAttempts.total(record, discipline)), "kg"),
                points?.let { Triple(it.first, CompetitionFormat.points(it.second), "puntos") } ?: Triple("Puesto", placeOf(record), record.category ?: "general"),
                weighIn,
            )
        }
        CompetitionResultForm.CLOCK -> listOf(
            Triple("Tiempo", CompetitionInsights.headline(record, discipline)?.value ?: "—", record.distanceKm?.let { CompetitionFormat.km(it) } ?: ""),
            Triple("General", record.overallPlace?.let { CompetitionFormat.thousands(it) } ?: "—", record.overallField?.let { "de ${CompetitionFormat.thousands(it)}" } ?: "puesto"),
            Triple("Categoría", record.categoryPlace?.toString() ?: "—", record.category ?: "puesto"),
        )
        CompetitionResultForm.COMBAT -> listOf(
            Triple("Balance", "${CompetitionInsights.wins(record)}–${CompetitionInsights.losses(record)}", "${record.bouts.size} combates"),
            Triple("Puesto", placeOf(record), record.category ?: "categoría"),
            weighIn,
        )
        CompetitionResultForm.MATCH -> if (discipline == CompetitionDiscipline.CLIMBING) listOf(
            Triple("Tops", "${record.climbing?.topsDone ?: 0} / ${record.climbing?.topsTotal ?: 0}", "bloques"),
            Triple("Zonas", "${record.climbing?.zonesDone ?: 0} / ${record.climbing?.zonesTotal ?: 0}", "bloques"),
            Triple("Puesto", placeOf(record), record.category ?: "general"),
        ) else listOf(
            Triple("Balance", "${CompetitionInsights.wins(record)}–${CompetitionInsights.losses(record)}", "${record.bouts.size} partidos"),
            Triple("Puesto", placeOf(record), record.category ?: "categoría"),
            Triple("Pareja", record.partner ?: "—", ""),
        )
        CompetitionResultForm.MARKS -> listOf(
            Triple("Tiempo", record.finishTimeSeconds?.let { CompetitionFormat.duration(it) } ?: "—", "total"),
            Triple("Pruebas", record.customMetrics.count { it.value.isNotBlank() }.toString(), "con marca"),
            Triple("Puesto", placeOf(record), record.category ?: "general"),
        )
        CompetitionResultForm.STAGE -> listOf(
            Triple("Puesto", placeOf(record), "puesto"),
            Triple("Categoría", record.category ?: "—", ""),
            Triple("Tarima", CompetitionFormat.kg(record.bodybuildingDetails?.stageWeightKg), "kg"),
        )
    }
}

/** El resultado de la forma en la ficha: la tabla, los parciales, los combates, las pruebas o la tarima. */
@Composable
private fun ResultBody(ui: CompetitionDetailUi, record: CompetitionRecord) {
    val discipline = ui.discipline
    when (discipline.form) {
        CompetitionResultForm.LOAD -> {
            CompGroupLabel("Intentos")
            CompAttemptGrid(CompetitionAttempts.rows(record, discipline), labelWidth = if (discipline == CompetitionDiscipline.POWERLIFTING) 72.dp else 96.dp)
        }
        CompetitionResultForm.CLOCK -> if (record.splitsSeconds.isNotEmpty()) {
            CompModule(Modifier.padding(top = 12.dp)) {
                CompModHeader("Por kilómetro")
                CompSplits(record.splitsSeconds)
                CompetitionInsights.fastestSplitCaption(record.splitsSeconds)?.let { CompCap(it, Modifier.padding(top = 6.dp)) }
            }
        }
        CompetitionResultForm.COMBAT, CompetitionResultForm.MATCH -> if (discipline == CompetitionDiscipline.CLIMBING) Unit else if (record.bouts.isNotEmpty()) {
            CompGroupLabel(if (discipline.form == CompetitionResultForm.COMBAT) "Combates" else "Partidos")
            CompModule(tight = true) { record.bouts.forEachIndexed { i, bout -> CompBoutRow(bout, divider = i > 0, onClick = null) } }
        }
        CompetitionResultForm.MARKS -> if (record.customMetrics.isNotEmpty()) {
            CompGroupLabel("Pruebas")
            CompModule(tight = true) {
                record.customMetrics.forEachIndexed { i, metric ->
                    CompRatioRow(metric.label, first = i == 0) {
                        Text(metric.value.ifBlank { "—" }, style = CompType.syne(13, FontWeight.Bold, 1.2f), color = CompColors.ink)
                    }
                }
            }
        }
        CompetitionResultForm.STAGE -> {
            CompGroupLabel("Resultado")
            CompModule(tight = true) {
                CompRatioRow("Categoría", first = true) { Text(record.category ?: "—", style = CompType.inter(13f, FontWeight.Bold), color = CompColors.ink) }
                CompRatioRow("Rondas") { Text(record.bodybuildingDetails?.rounds ?: "—", style = CompType.inter(13f, FontWeight.Bold), color = CompColors.ink) }
                CompRatioRow("Puesto") { Text(placeOf(record), style = CompType.inter(13f, FontWeight.Bold), color = CompColors.ink) }
            }
        }
    }
}

// ─── Próxima (c-run-next, c-bb) ───────────────────────────────────────────────────────────────────────────────

@Composable
private fun PlannedDetail(
    ui: CompetitionDetailUi,
    record: CompetitionRecord,
    viewModel: CompetitionDetailViewModel,
    onBack: () -> Unit,
    onMenu: () -> Unit,
    onStartFlow: (String, String) -> Unit,
) {
    val discipline = ui.discipline
    val today = LocalDate.now()
    val days = CompetitionInsights.daysUntil(record, today)
    var targetSheet by remember { mutableStateOf(false) }
    val title = record.title.ifBlank { discipline.label }
    CompScreen {
        CompBackRow(if (discipline.form == CompetitionResultForm.STAGE) listOfNotNull(title, record.category).joinToString(" · ") else "Voy a competir", onBack) {
            CompIconButton(CompetitionIcons.More, "Más opciones", onMenu)
        }
        when (discipline.form) {
            CompetitionResultForm.STAGE -> {
                val weeks = CompetitionInsights.weeksUntil(record, today)
                CompNextCard(LineCoverStyle.BRUMA, LineCoverBackground.NOCHE, seed = 4, Modifier.padding(top = 4.dp), topPadding = 110.dp) {
                    CompLabel("Faltan", color = CompColors.mus)
                    weeks?.let { CompCountdown(it, "semanas · peak week en la $it", Modifier.padding(top = 6.dp)) }
                }
                val stage = record.bodybuildingDetails?.stageWeightKg
                CompModule {
                    CompModHeader("Hacia la tarima") { CompCap("de tu pestaña Cuerpo") }
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column { CompBig(CompetitionFormat.kg(ui.bodyweightKg), 28, "kg"); CompCap("hoy") }
                        Column { CompBig(CompetitionFormat.kg(stage), 28, "kg", color = CompColors.muted); CompCap("en tarima") }
                        CompetitionInsights.stageRate(ui.bodyweightKg, stage, weeks)?.let { rate ->
                            Column { CompBig(CompetitionFormat.signedKg(Math.round(rate * 10) / 10.0), 20, "kg/sem", color = CompColors.done); CompCap("ritmo necesario") }
                        }
                    }
                }
            }
            CompetitionResultForm.CLOCK -> {
                CompNextCard(LineCoverStyle.ONDAS, LineCoverBackground.NOCHE, seed = 3, Modifier.padding(top = 4.dp), topPadding = 120.dp) {
                    CompLabel("$title · ${CompetitionFormat.dayMonth(CompetitionInsights.dateOf(record))}", color = CompColors.mus)
                    days?.takeIf { it >= 0 }?.let { CompCountdown(it, if (it == 1L) "día" else "días", Modifier.padding(top = 6.dp), countUp = false) }
                }
                CompModule {
                    CompModHeader("Tu objetivo") { CompTextAction("Cambiar", { targetSheet = true }) }
                    CompBig(record.targetTimeSeconds?.let { CompetitionFormat.duration(it) } ?: "—", 40, modifier = Modifier.padding(top = 6.dp))
                    CompetitionInsights.targetCaption(ui.records, record, CompetitionPrograms.disciplineResolver(ui.programs))?.let { CompCap(it, Modifier.padding(top = 4.dp)) }
                }
                val segments = CompetitionInsights.paceSegments(record)
                if (segments.isNotEmpty()) CompModule {
                    Text("PLAN DE RITMOS", style = CompType.ey, color = CompColors.muted)
                    segments.forEachIndexed { i, segment ->
                        CompRatioRow(segment.label, first = false, sub = segment.note) {
                            Text(segment.pace, style = CompType.syne(13, FontWeight.Bold, 1.2f), color = CompColors.ink)
                        }
                    }
                }
            }
            else -> {
                CompNextCard(LineCoverStyle.ORBITAS, LineCoverBackground.NOCHE, seed = 4, Modifier.padding(top = 4.dp)) {
                    CompLabel("Próxima", color = CompColors.mus)
                    days?.takeIf { it >= 0 }?.let { CompCountdown(it, if (it == 1L) "día" else "días", Modifier.padding(top = 6.dp)) }
                    Text(title, Modifier.padding(top = 6.dp), style = CompType.syne(21, FontWeight.Bold, 1.15f), color = CompColors.ink)
                    CompCap(CompetitionInsights.nextMeta(record), Modifier.padding(top = 4.dp))
                    if (discipline.form == CompetitionResultForm.LOAD) {
                        CompOpeners(CompetitionAttempts.rows(record, discipline).mapNotNull { block ->
                            record.plannedAttempts.firstOrNull { it.movementType == block.movementType && it.attemptNumber == 1 }
                                ?.let { block.movementType.liftLabel(block.title).uppercase() to CompetitionFormat.kg(it.weightKg) }
                        })
                        Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (days == 0L) CompPill("Modo competición", { onStartFlow(record.id, CompetitionFlowMode.TODAY.id) }, Modifier.weight(1f), small = true)
                            else if (discipline == CompetitionDiscipline.POWERLIFTING) CompPill("Ver plan del día", { onStartFlow(record.id, CompetitionFlowMode.PLAN.id) }, Modifier.weight(1f), small = true)
                            CompPill("Registrar", { onStartFlow(record.id, CompetitionFlowMode.LOG.id) }, Modifier.weight(1f), primary = false, small = true)
                        }
                    }
                }
            }
        }
        CompModule(tight = true) {
            val parts = listOfNotNull("1 semana".takeIf { record.reminderOneWeekEnabled }, "48 h".takeIf { record.reminder48hEnabled },
                record.startTime?.takeIf { record.reminderStartEnabled }?.let { "${if (discipline.hasWeighIn) "pesaje" else "salida"} a las ${it.removePrefix("0")}" })
            val on = record.reminderOneWeekEnabled || record.reminder48hEnabled || record.reminderStartEnabled
            CompToggleRow("Avisos", parts.joinToString(" · ").ifBlank { "Apagados" }, on, { enabled ->
                viewModel.save { it.copy(reminderOneWeekEnabled = enabled, reminder48hEnabled = enabled, reminderStartEnabled = enabled && it.startTime != null) }
            }, divider = false)
        }
        if (days != null && days < 0 || discipline.form != CompetitionResultForm.LOAD) {
            CompPill("Registrar el resultado", { onStartFlow(record.id, CompetitionFlowMode.LOG.id) }, Modifier.fillMaxWidth().padding(top = 4.dp),
                primary = days != null && days <= 0)
        }
    }
    if (targetSheet) CompDurationSheet("Tu objetivo", record.targetTimeSeconds, { seconds -> viewModel.save { it.copy(targetTimeSeconds = seconds) } }, { targetSheet = false })
}

// ─── c-menu ───────────────────────────────────────────────────────────────────────────────────────────────────

@Composable
private fun CompetitionMenuSheet(
    ui: CompetitionDetailUi,
    record: CompetitionRecord,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onShare: () -> Unit,
    onCompare: () -> Unit,
    onMarks: () -> Unit,
    onPlan: () -> Unit,
    onLog: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
) {
    val discipline = ui.discipline
    val completed = record.status == CompetitionRecordStatus.COMPLETED
    fun run(action: () -> Unit): () -> Unit = { onDismiss(); action() }
    CompSheet(onDismiss) {
        Row(Modifier.padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            CompMedal(CompetitionPlaceHonors.medalOf(record), 52.dp, icon = CompetitionIcons.byKey(if (discipline.form == CompetitionResultForm.LOAD) "trophy" else discipline.icon))
            Column(Modifier.weight(1f)) {
                CompSheetTitle(record.title.ifBlank { discipline.label })
                val headline = CompetitionInsights.headline(record, discipline)
                CompCap(listOfNotNull(CompetitionFormat.date(CompetitionInsights.dateOf(record)),
                    headline?.takeIf { completed }?.let { if (discipline.form == CompetitionResultForm.LOAD) "${it.value} kg" else it.value }).joinToString(" · "))
            }
        }
        if (completed) {
            CompMenuRow(CompetitionIcons.Edit, "Editar resultado", "Intentos, puesto, fotos y diario", run(onEdit), divider = false)
            CompMenuRow(CompetitionIcons.Share, "Compartir en historias", null, run(onShare))
            if (canCompare(ui, record)) CompMenuRow(CompetitionIcons.Swap, "Comparar con otra", "Elige una de tu historial", run(onCompare))
            if (ui.canUpdateMarks) CompMenuRow(CompetitionIcons.TrendUp, "Actualizar tus marcas", "Usa estos resultados en tu programa", run(onMarks))
        } else {
            CompMenuRow(CompetitionIcons.Edit, "Editar", "Datos, categoría y avisos", run(onEdit), divider = false)
            if (discipline == CompetitionDiscipline.POWERLIFTING) CompMenuRow(CompetitionIcons.Target, "Plan de intentos", "Tus nueve intentos y el pesaje", run(onPlan))
            CompMenuRow(CompetitionIcons.Flag, "Registrar el resultado", "Intentos, puesto, fotos y diario", run(onLog))
        }
        CompMenuRow(CompetitionIcons.Upload, "Archivar", "Sale del historial; no se borra", run(onArchive))
        CompMenuRow(CompetitionIcons.Trash, "Eliminar", "Borra la competencia y sus fotos", run(onDelete), danger = true)
    }
}

// ─── c-compare ────────────────────────────────────────────────────────────────────────────────────────────────

@Composable
private fun CompetitionCompare(ui: CompetitionDetailUi, record: CompetitionRecord, other: CompetitionRecord, onBack: () -> Unit, onPick: (String) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    val resolver = CompetitionPrograms.disciplineResolver(ui.programs)
    val rows = CompetitionInsights.compare(record, other, resolver)
    CompScreen {
        CompBackRow("Comparar", onBack)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(record to false, other to true).forEach { (r, pickable) ->
                Box(Modifier.weight(1f)) {
                    CompModule(Modifier.padding(bottom = 0.dp), tight = true, onClick = if (pickable) ({ picking = true }) else null) {
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            CompMedal(CompetitionPlaceHonors.medalOf(r), 40.dp)
                            Text(r.title.ifBlank { ui.discipline.label }, Modifier.padding(top = 8.dp), style = CompType.inter(13f, FontWeight.SemiBold, 1.3f),
                                color = CompColors.ink, textAlign = TextAlign.Center, maxLines = 2)
                            CompCap(CompetitionFormat.date(CompetitionInsights.dateOf(r)))
                        }
                    }
                }
            }
        }
        CompModule(Modifier.padding(top = 12.dp)) {
            rows.forEachIndexed { i, row ->
                CompRatioRow(row.label, first = i == 0) {
                    Text(row.value, Modifier.width(70.dp), style = CompType.syne(13, FontWeight.Bold, 1.2f), color = CompColors.ink, textAlign = TextAlign.End)
                    Text(row.other.orEmpty(), Modifier.width(70.dp), style = CompType.cap, color = CompColors.faint, textAlign = TextAlign.End)
                }
            }
        }
        CompCap("Toca una competencia de arriba para cambiarla.")
    }
    if (picking) CompSheet({ picking = false }) {
        CompSheetTitle("Comparar con")
        comparables(ui, record).forEach { candidate ->
            com.example.kpkn.screens.competitions.design.CompSheetOption(candidate.title.ifBlank { ui.discipline.label },
                CompetitionFormat.date(CompetitionInsights.dateOf(candidate)), candidate.id == other.id,
                onClick = { onPick(candidate.id); picking = false }, leading = { CompMedal(CompetitionPlaceHonors.medalOf(candidate), 38.dp) })
        }
    }
}

