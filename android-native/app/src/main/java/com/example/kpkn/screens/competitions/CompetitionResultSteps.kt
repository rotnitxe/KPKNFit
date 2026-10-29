package com.example.kpkn.screens.competitions

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.CompetitionBout
import com.example.kpkn.data.models.CompetitionTechnicalBlock
import com.example.kpkn.data.models.CustomCompetitionMetric
import com.example.kpkn.domain.competitions.CompetitionAttempts
import com.example.kpkn.domain.competitions.CompetitionDiscipline
import com.example.kpkn.domain.competitions.CompetitionFormat
import com.example.kpkn.domain.competitions.CompetitionInsights
import com.example.kpkn.domain.competitions.CompetitionPlaceHonors
import com.example.kpkn.domain.competitions.CompetitionResultForm
import com.example.kpkn.screens.competitions.design.CompAttemptGrid
import com.example.kpkn.screens.competitions.design.CompBackRow
import com.example.kpkn.screens.competitions.design.CompBig
import com.example.kpkn.screens.competitions.design.CompBoutRow
import com.example.kpkn.screens.competitions.design.CompCap
import com.example.kpkn.screens.competitions.design.CompCenteredAction
import com.example.kpkn.screens.competitions.design.CompChips
import com.example.kpkn.screens.competitions.design.CompColors
import com.example.kpkn.screens.competitions.design.CompGroupLabel
import com.example.kpkn.screens.competitions.design.CompInputField
import com.example.kpkn.screens.competitions.design.CompKpis
import com.example.kpkn.screens.competitions.design.CompLabel
import com.example.kpkn.screens.competitions.design.CompMGroup
import com.example.kpkn.screens.competitions.design.CompMedal
import com.example.kpkn.screens.competitions.design.CompModHeader
import com.example.kpkn.screens.competitions.design.CompModule
import com.example.kpkn.screens.competitions.design.CompPChip
import com.example.kpkn.screens.competitions.design.CompPill
import com.example.kpkn.screens.competitions.design.CompRatioRow
import com.example.kpkn.screens.competitions.design.CompScreen
import com.example.kpkn.screens.competitions.design.CompSeg
import com.example.kpkn.screens.competitions.design.CompSheetTitle
import com.example.kpkn.screens.competitions.design.CompSplits
import com.example.kpkn.screens.competitions.design.CompStepTitle
import com.example.kpkn.screens.competitions.design.CompTextAction
import com.example.kpkn.screens.competitions.design.CompTotalBar
import com.example.kpkn.screens.competitions.design.CompType
import com.example.kpkn.screens.competitions.design.CompetitionIcons
import com.example.kpkn.screens.competitions.design.CountUpValue

/** El resultado según la forma de la disciplina (d-all): carga, reloj, combates, partidos, pruebas con marca o tarima. */
@Composable
internal fun CompetitionResultStep(ui: CompetitionFlowUi, viewModel: CompetitionFlowViewModel, onBack: () -> Unit) {
    when (ui.discipline.form) {
        CompetitionResultForm.LOAD -> LoadResult(ui, viewModel, onBack)
        CompetitionResultForm.CLOCK -> ClockResult(ui, viewModel, onBack)
        CompetitionResultForm.COMBAT -> BoutsResult(ui, viewModel, onBack, combat = true)
        CompetitionResultForm.MATCH -> if (ui.discipline == CompetitionDiscipline.CLIMBING) ClimbingResult(ui, viewModel, onBack) else BoutsResult(ui, viewModel, onBack, combat = false)
        CompetitionResultForm.MARKS -> MarksResult(ui, viewModel, onBack)
        CompetitionResultForm.STAGE -> StageResult(ui, viewModel, onBack)
    }
}

private fun titleOf(ui: CompetitionFlowUi): String = ui.record.title.ifBlank { ui.discipline.label }

private fun weightClassLabel(raw: String?): String? = raw?.takeIf { it.isNotBlank() }?.let { CompetitionFormat.weightClass(it) ?: it }

// ─── Carga (c-log, c-lift) ────────────────────────────────────────────────────────────────────────────────────

@Composable
private fun LoadResult(ui: CompetitionFlowUi, viewModel: CompetitionFlowViewModel, onBack: () -> Unit) {
    val record = ui.record
    val powerlifting = ui.discipline == CompetitionDiscipline.POWERLIFTING
    var editing by remember { mutableStateOf<Pair<CompetitionTechnicalBlock, Int>?>(null) }
    val totals = loadTotals(ui)
    CompScreen {
        CompBackRow(flowBackTitle(ui), onBack)
        if (powerlifting) {
            FlowResultSteps(ui, viewModel)
            CompStepTitle("¿Cómo te fue?")
            CompCap("Toca un intento para marcarlo válido o nulo. Mantén pulsado para cambiar el peso.", Modifier.padding(top = 6.dp))
        } else {
            CompStepTitle(titleOf(ui))
            val meta = listOfNotNull(weightClassLabel(record.targetWeightClass ?: record.powerliftingDetails?.weightClass)?.let { "$it kg" }, totals.pointsLabel ?: record.category)
            if (meta.isNotEmpty()) CompCap(meta.joinToString(" · "), Modifier.padding(top = 4.dp))
        }
        CompAttemptGrid(
            CompetitionAttempts.rows(record, ui.discipline),
            labelWidth = if (powerlifting) 72.dp else 96.dp,
            onTap = { block, n -> viewModel.toggle(block, n) },
            onLongPress = { block, n -> editing = block to n },
        )
        CompTotalBar(left = {
            CompLabel("Total")
            if (powerlifting) CountUpValue(totals.total ?: 0.0, decimals = 1, size = 44, unit = "kg")
            else CompBig(CompetitionFormat.kg(totals.total), 44, "kg")
        }) {
            if (totals.pointsLabel != null) Column(horizontalAlignment = Alignment.End) {
                CompCap(totals.pointsLabel)
                Text(CompetitionFormat.points(totals.points), style = CompType.syne(20, line = 1.1f), color = CompColors.ink)
                totals.delta?.let { CompPChip("${CompetitionFormat.signedKg(it)} kg", Modifier.padding(top = 6.dp)) }
            }
        }
        CompPill("Continuar", viewModel::next, Modifier.fillMaxWidth().padding(top = 16.dp))
    }
    editing?.let { (block, n) ->
        val current = CompetitionAttempts.rows(ui.record, ui.discipline).firstOrNull { it.id == block.id } ?: block
        AttemptLongPressSheet(ui, viewModel, current, n) { editing = null }
    }
}

// ─── Contra el reloj (c-run) ──────────────────────────────────────────────────────────────────────────────────

@Composable
private fun ClockResult(ui: CompetitionFlowUi, viewModel: CompetitionFlowViewModel, onBack: () -> Unit) {
    val record = ui.record
    val discipline = ui.discipline
    var timeSheet by remember { mutableStateOf(false) }
    var placesSheet by remember { mutableStateOf(false) }
    var splitsSheet by remember { mutableStateOf(false) }
    val resolver: (com.example.kpkn.data.models.CompetitionRecord) -> CompetitionDiscipline = { r -> if (r.id == record.id) discipline else CompetitionDiscipline.of(r) }
    val jump = discipline == CompetitionDiscipline.JUMP
    val sprint = discipline == CompetitionDiscipline.SPRINT
    CompScreen {
        CompBackRow(flowBackTitle(ui), onBack)
        CompStepTitle(titleOf(ui))
        val sub = listOfNotNull(CompetitionFormat.dayMonth(CompetitionInsights.dateOf(record)),
            if (sprint) record.distanceKm?.let { "${CompetitionFormat.kg(it * 1000)} m" } else if (jump) record.category else record.distanceKm?.let { CompetitionFormat.km(it) })
        CompCap(sub.joinToString(" · "), Modifier.padding(top = 4.dp))
        CompMGroup(if (jump) "Tu marca" else "Tu tiempo")
        val shape = RoundedCornerShape(20.dp)
        Box(
            Modifier.fillMaxWidth().clip(shape).background(Color(0x3A000000)).border(1.dp, Color(0x1AFFFFFF), shape)
                .clickable(role = Role.Button, onClickLabel = if (jump) "Escribir tu marca" else "Escribir tu tiempo") { timeSheet = true }.padding(6.dp),
            contentAlignment = Alignment.Center,
        ) {
            val value = when {
                jump -> record.markMeters?.let { "${CompetitionFormat.kg(it)} m" }
                sprint -> record.finishTimeSeconds?.let { String.format(java.util.Locale.ROOT, "%.2f", it).replace('.', ',') + " s" }
                else -> record.finishTimeSeconds?.let { CompetitionFormat.duration(it) }
            } ?: "—"
            Text(value, Modifier.padding(vertical = 4.dp), style = CompType.syne(40, line = 1.15f), color = CompColors.ink)
        }
        val general = Triple("General", record.overallPlace?.let { CompetitionFormat.thousands(it) } ?: "—", record.overallField?.let { "de ${CompetitionFormat.thousands(it)}" } ?: "puesto")
        val category = Triple("Categoría", record.categoryPlace?.toString() ?: "—", record.category ?: "puesto")
        val kpis = if (jump || sprint) listOf(general, category)
        else listOf(Triple("Ritmo", CompetitionFormat.pace(CompetitionInsights.paceSecondsPerKm(record)), "/km"), general, category)
        Box(Modifier.clickable(role = Role.Button, onClickLabel = "Escribir tus puestos") { placesSheet = true }) { CompKpis(kpis) }
        if (!jump && !sprint) {
            CompModule(Modifier.padding(top = 12.dp), onClick = { splitsSheet = true }) {
                CompModHeader("Por kilómetro") { CompCap("del reloj o a mano") }
                CompSplits(record.splitsSeconds)
                CompCap(CompetitionInsights.fastestSplitCaption(record.splitsSeconds) ?: "Toca para escribir tus parciales.", Modifier.padding(top = 6.dp))
            }
        }
        CompetitionInsights.personalBestDelta(ui.records, record, resolver)?.let { delta ->
            CompTotalBar(Modifier.padding(top = 0.dp), left = {
                CompLabel("Marca personal")
                Text(CompetitionFormat.signedDuration(delta), style = CompType.syne(22, line = 1.1f), color = CompColors.ink)
            }) { if (delta < 0) CompPChip("Nueva MP", icon = CompetitionIcons.Spark) }
        }
        CompPill("Continuar", viewModel::next, Modifier.fillMaxWidth().padding(top = 14.dp))
    }
    if (timeSheet) {
        if (jump) CompTextSheet("Tu marca", "Metros", record.markMeters?.let { CompetitionFormat.kg(it) }.orEmpty(),
            { viewModel.setMark(CompetitionFormat.parseKg(it)) }, { timeSheet = false }, numeric = true)
        else CompDurationSheet("Tu tiempo", record.finishTimeSeconds, viewModel::setFinishTime, { timeSheet = false }, withHundredths = sprint)
    }
    if (placesSheet) PlacesSheet(ui, viewModel) { placesSheet = false }
    if (splitsSheet) CompTextSheet(
        title = "Por kilómetro",
        label = "Un tiempo por kilómetro, separados por comas",
        initial = record.splitsSeconds.joinToString(", ") { CompetitionFormat.pace(it) },
        onConfirm = { text -> viewModel.setSplits(text.split(',', ';', '\n').mapNotNull { CompetitionFormat.parseDuration(it.trim()) }) },
        onDismiss = { splitsSheet = false },
        sub = "Por ejemplo: 4:52, 4:45, 4:41",
    )
}

@Composable
private fun PlacesSheet(ui: CompetitionFlowUi, viewModel: CompetitionFlowViewModel, onDismiss: () -> Unit) {
    val record = ui.record
    var place by remember { mutableStateOf(record.overallPlace?.toString().orEmpty()) }
    var field by remember { mutableStateOf(record.overallField?.toString().orEmpty()) }
    var catPlace by remember { mutableStateOf(record.categoryPlace?.toString().orEmpty()) }
    var catLabel by remember { mutableStateOf(record.category.orEmpty()) }
    val digits = KeyboardOptions(keyboardType = KeyboardType.Number)
    CompSheet(onDismiss) {
        CompSheetTitle("Tus puestos")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CompInputField("General", place, { place = it.filter(Char::isDigit) }, Modifier.weight(1f), "Puesto", digits)
            CompInputField("De", field, { field = it.filter(Char::isDigit) }, Modifier.weight(1f), "Participantes", digits)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CompInputField("Categoría", catPlace, { catPlace = it.filter(Char::isDigit) }, Modifier.weight(1f), "Puesto", digits)
            CompInputField("Nombre", catLabel, { catLabel = it }, Modifier.weight(1f), "M35")
        }
        CompPill("Listo", {
            viewModel.setOverall(place.toIntOrNull(), field.toIntOrNull())
            viewModel.setCategoryPlace(catPlace.toIntOrNull(), catLabel)
            onDismiss()
        }, Modifier.fillMaxWidth().padding(top = 16.dp))
    }
}

// ─── Combates y partidos (c-combat, c-match) ──────────────────────────────────────────────────────────────────

@Composable
private fun BoutsResult(ui: CompetitionFlowUi, viewModel: CompetitionFlowViewModel, onBack: () -> Unit, combat: Boolean) {
    val record = ui.record
    var editing by remember { mutableStateOf<CompetitionBout?>(null) }
    CompScreen {
        CompBackRow(flowBackTitle(ui), onBack)
        val weight = weightClassLabel(record.targetWeightClass)
        CompStepTitle(if (combat && weight != null) "${titleOf(ui)} · $weight kg" else titleOf(ui))
        val meta = if (combat) listOfNotNull(record.category, record.bodyweightKg?.let { "pesaje ${CompetitionFormat.kg(it)} kg" })
        else listOfNotNull(record.partner?.let { "Con $it" }, record.category)
        if (meta.isNotEmpty()) CompCap(meta.joinToString(" · "), Modifier.padding(top = 4.dp))
        CompMGroup(if (combat) "Combates" else "Partidos")
        if (record.bouts.isNotEmpty()) CompModule(tight = true) {
            record.bouts.forEachIndexed { i, bout -> CompBoutRow(bout, divider = i > 0, onClick = { editing = bout }) }
        }
        CompTextAction(if (combat) "Añadir combate" else "Añadir partido", {
            editing = CompetitionBout(viewModel.newBoutId(), round = "", won = true)
        }, Modifier.padding(top = 4.dp), icon = CompetitionIcons.Plus)
        if (combat) {
            val medal = CompetitionPlaceHonors.medalOf(record) ?: CompetitionInsights.boutMedal(record)
            CompTotalBar(left = {
                CompLabel("Balance")
                CompBig("${CompetitionInsights.wins(record)}–${CompetitionInsights.losses(record)}", 40)
            }) { CompMedal(medal, 54.dp) }
        }
        CompPill("Continuar", viewModel::next, Modifier.fillMaxWidth().padding(top = 14.dp))
    }
    editing?.let { bout -> BoutSheet(bout, combat, isNew = record.bouts.none { it.id == bout.id }, onSave = viewModel::upsertBout,
        onRemove = { viewModel.removeBout(bout.id) }, onDismiss = { editing = null }) }
}

@Composable
private fun BoutSheet(bout: CompetitionBout, combat: Boolean, isNew: Boolean, onSave: (CompetitionBout) -> Unit, onRemove: () -> Unit, onDismiss: () -> Unit) {
    var round by remember { mutableStateOf(bout.round) }
    var won by remember { mutableStateOf(bout.won) }
    var method by remember { mutableStateOf(bout.method) }
    val rounds = if (combat) listOf("Octavos", "Cuartos", "Semifinal", "Final") else listOf("Grupo", "Cuartos", "Semifinal", "Final")
    CompSheet(onDismiss) {
        CompSheetTitle(if (combat) "Combate" else "Partido")
        CompSeg(listOf("Ganado", "Perdido"), if (won) 0 else 1, { won = it == 0 }, Modifier.padding(top = 4.dp, bottom = 4.dp))
        CompMGroup("Ronda")
        CompChips(rounds, { rounds[it] == round }) { round = rounds[it] }
        CompInputField("Ronda", round, { round = it }, placeholder = "Ronda 1")
        CompInputField(if (combat) "Cómo fue" else "Marcador", method, { method = it }, placeholder = if (combat) "Puntos 6–2, sumisión, KO…" else "6–3 6–4")
        CompPill("Listo", {
            onSave(bout.copy(round = round.trim().ifBlank { rounds.first() }, won = won, method = method.trim()))
            onDismiss()
        }, Modifier.fillMaxWidth().padding(top = 16.dp))
        if (!isNew) CompCenteredAction("Quitar") { onRemove(); onDismiss() }
    }
}

// ─── Escalada (c-match) ───────────────────────────────────────────────────────────────────────────────────────

@Composable
private fun ClimbingResult(ui: CompetitionFlowUi, viewModel: CompetitionFlowViewModel, onBack: () -> Unit) {
    val record = ui.record
    val climbing = record.climbing ?: com.example.kpkn.data.models.CompetitionClimbingResult()
    var sheet by remember { mutableStateOf<String?>(null) }
    CompScreen {
        CompBackRow(flowBackTitle(ui), onBack)
        CompStepTitle(titleOf(ui))
        record.location?.takeIf { it.isNotBlank() }?.let { CompCap(it, Modifier.padding(top = 4.dp)) }
        CompGroupLabel("Escalada · ${record.category?.lowercase() ?: "bloque"}")
        CompModule(tight = true) {
            Box(Modifier.clickable(role = Role.Button) { sheet = "tops" }) {
                CompRatioRow("Tops", first = true) { Text("${climbing.topsDone} / ${climbing.topsTotal}", style = CompType.inter(13f, FontWeight.Bold), color = CompColors.ink) }
            }
            Box(Modifier.clickable(role = Role.Button) { sheet = "zones" }) {
                CompRatioRow("Zonas") { Text("${climbing.zonesDone} / ${climbing.zonesTotal}", style = CompType.inter(13f, FontWeight.Bold), color = CompColors.ink) }
            }
            Box(Modifier.clickable(role = Role.Button) { sheet = "place" }) {
                CompRatioRow("Puesto") { Text(placeOf(record), style = CompType.inter(13f, FontWeight.Bold), color = CompColors.ink) }
            }
        }
        CompPill("Continuar", viewModel::next, Modifier.fillMaxWidth().padding(top = 14.dp))
    }
    when (sheet) {
        "tops" -> CompTextSheet("Tops", "Conseguidos", climbing.topsDone.toString(), {}, { sheet = null }, numeric = true,
            secondLabel = "De", secondInitial = climbing.topsTotal.toString(),
            onConfirmBoth = { a, b -> viewModel.setClimbing(a.toIntOrNull() ?: 0, b.toIntOrNull() ?: 0, climbing.zonesDone, climbing.zonesTotal) })
        "zones" -> CompTextSheet("Zonas", "Conseguidas", climbing.zonesDone.toString(), {}, { sheet = null }, numeric = true,
            secondLabel = "De", secondInitial = climbing.zonesTotal.toString(),
            onConfirmBoth = { a, b -> viewModel.setClimbing(climbing.topsDone, climbing.topsTotal, a.toIntOrNull() ?: 0, b.toIntOrNull() ?: 0) })
        "place" -> PlaceOfSheet(ui, viewModel) { sheet = null }
    }
}

internal fun placeOf(record: com.example.kpkn.data.models.CompetitionRecord): String {
    val place = CompetitionPlaceHonors.parsePlace(record.placement) ?: return "—"
    return CompetitionFormat.ordinal(place) + (record.fieldSize?.let { " de $it" } ?: "")
}

@Composable
private fun PlaceOfSheet(ui: CompetitionFlowUi, viewModel: CompetitionFlowViewModel, onDismiss: () -> Unit) {
    CompTextSheet("Puesto", "Puesto", CompetitionPlaceHonors.parsePlace(ui.record.placement)?.toString().orEmpty(), {}, onDismiss, numeric = true,
        secondLabel = "De", secondInitial = ui.record.fieldSize?.toString().orEmpty(),
        onConfirmBoth = { a, b -> viewModel.setPlaceOf(a.trim().toIntOrNull(), b.trim().toIntOrNull()) })
}

// ─── Pruebas con marca (c-mixed) ──────────────────────────────────────────────────────────────────────────────

@Composable
private fun MarksResult(ui: CompetitionFlowUi, viewModel: CompetitionFlowViewModel, onBack: () -> Unit) {
    val record = ui.record
    var timeSheet by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<CustomCompetitionMetric?>(null) }
    var adding by remember { mutableStateOf(false) }
    CompScreen {
        CompBackRow(flowBackTitle(ui), onBack)
        CompStepTitle(listOfNotNull(titleOf(ui), record.category?.takeIf { it.isNotBlank() }).joinToString(" · "))
        CompMGroup("Tiempo total")
        Box(Modifier.clickable(role = Role.Button, onClickLabel = "Escribir el tiempo total") { timeSheet = true }) {
            CompBig(record.finishTimeSeconds?.let { CompetitionFormat.duration(it) } ?: "—", 44)
        }
        if (record.customMetrics.isNotEmpty()) CompModule(Modifier.padding(top = 12.dp)) {
            record.customMetrics.forEachIndexed { i, metric ->
                Box(Modifier.clickable(role = Role.Button) { editing = metric }) {
                    CompRatioRow(metric.label, first = i == 0) {
                        Text(metric.value.ifBlank { "—" } + (metric.unit?.let { " $it" } ?: ""), style = CompType.syne(13, FontWeight.Bold, 1.2f), color = CompColors.ink)
                    }
                }
            }
        }
        if (ui.discipline != CompetitionDiscipline.HYROX) {
            CompTextAction("Añadir prueba", { adding = true }, Modifier.padding(top = 4.dp), icon = CompetitionIcons.Plus)
        }
        CompPill("Continuar", viewModel::next, Modifier.fillMaxWidth().padding(top = 14.dp))
    }
    if (timeSheet) CompDurationSheet("Tiempo total", record.finishTimeSeconds, viewModel::setFinishTime, { timeSheet = false })
    editing?.let { metric ->
        CompTextSheet(metric.label, "Prueba", metric.label, {}, { editing = null }, secondLabel = "Marca (tiempo, kg, repeticiones o metros)",
            secondInitial = metric.value, onConfirmBoth = { label, value -> viewModel.setMetric(metric.id, label, value) })
    }
    if (adding) CompTextSheet("Nueva prueba", "Prueba", "", {}, { adding = false }, secondLabel = "Marca (tiempo, kg, repeticiones o metros)",
        onConfirmBoth = { label, value -> if (label.isNotBlank()) viewModel.setMetric(null, label, value) })
}

// ─── Jueceo en tarima (c-bb) ──────────────────────────────────────────────────────────────────────────────────

@Composable
private fun StageResult(ui: CompetitionFlowUi, viewModel: CompetitionFlowViewModel, onBack: () -> Unit) {
    val record = ui.record
    var sheet by remember { mutableStateOf<String?>(null) }
    CompScreen {
        CompBackRow(flowBackTitle(ui), onBack)
        CompStepTitle(titleOf(ui))
        CompGroupLabel("Resultado")
        CompModule(tight = true) {
            Box(Modifier.clickable(role = Role.Button) { sheet = "category" }) {
                CompRatioRow("Categoría", first = true) { Text(record.category ?: "—", style = CompType.inter(13f, FontWeight.Bold), color = CompColors.ink) }
            }
            Box(Modifier.clickable(role = Role.Button) { sheet = "rounds" }) {
                CompRatioRow("Rondas") { Text(record.bodybuildingDetails?.rounds ?: "—", style = CompType.inter(13f, FontWeight.Bold), color = CompColors.ink) }
            }
            Box(Modifier.clickable(role = Role.Button) { sheet = "place" }) {
                CompRatioRow("Puesto") { Text(placeOf(record), style = CompType.inter(13f, FontWeight.Bold), color = CompColors.ink) }
            }
        }
        CompPill("Continuar", viewModel::next, Modifier.fillMaxWidth().padding(top = 14.dp))
    }
    when (sheet) {
        "category" -> CompTextSheet("Categoría", "Categoría", record.category.orEmpty(), viewModel::setCategory, { sheet = null })
        "rounds" -> CompTextSheet("Rondas", "Rondas", record.bodybuildingDetails?.rounds.orEmpty(), viewModel::setRounds, { sheet = null }, sub = "Por ejemplo: Prejuicio y final")
        "place" -> PlaceOfSheet(ui, viewModel) { sheet = null }
    }
}
