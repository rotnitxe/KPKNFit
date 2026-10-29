package com.example.kpkn.screens.competitions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.kpkn.data.models.CompetitionRecordStatus
import com.example.kpkn.domain.competitions.CompetitionDiscipline
import com.example.kpkn.domain.competitions.CompetitionNextCard
import com.example.kpkn.domain.competitions.CompetitionResultForm
import com.example.kpkn.domain.competitions.CompetitionFormat
import com.example.kpkn.screens.competitions.design.CompBackRow
import com.example.kpkn.screens.competitions.design.CompCap
import com.example.kpkn.screens.competitions.design.CompColors
import com.example.kpkn.screens.competitions.design.CompCountdown
import com.example.kpkn.screens.competitions.design.CompDisciplineButton
import com.example.kpkn.screens.competitions.design.CompGroupLabel
import com.example.kpkn.screens.competitions.design.CompIconButton
import com.example.kpkn.screens.competitions.design.CompLabel
import com.example.kpkn.screens.competitions.design.CompMGroup
import com.example.kpkn.screens.competitions.design.CompMeetRow
import com.example.kpkn.screens.competitions.design.CompModule
import com.example.kpkn.screens.competitions.design.CompNextCard
import com.example.kpkn.screens.competitions.design.CompOpeners
import com.example.kpkn.screens.competitions.design.CompPageTitle
import com.example.kpkn.screens.competitions.design.CompPill
import com.example.kpkn.screens.competitions.design.CompScreen
import com.example.kpkn.screens.competitions.design.CompSeg
import com.example.kpkn.screens.competitions.design.CompSheetTitle
import com.example.kpkn.screens.competitions.design.CompTotalsChart
import com.example.kpkn.screens.competitions.design.CompType
import com.example.kpkn.screens.competitions.design.CompetitionIcons
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.LineCoverBackground
import com.example.kpkn.screens.onboarding.design.entreno.plan.cover.LineCoverStyle

/**
 * La página «Competencias» (c-hub): la próxima con su cuenta atrás y el historial. [openNew] abre «Nueva competencia» al entrar.
 * [onStartFlow] arranca un recorrido (id, modo, disciplina) y [onOpenBodyweight] lleva a tu peso.
 */
@Composable
fun CompetitionsHubScreen(
    openNew: Boolean,
    onBack: () -> Unit,
    onOpenCompetition: (String) -> Unit,
    onStartFlow: (id: String, mode: String, discipline: String?) -> Unit,
    onOpenBodyweight: () -> Unit,
    viewModel: CompetitionsViewModel = viewModel(),
) {
    val ui by viewModel.ui.collectAsState()
    var newSheet by rememberSaveable { mutableStateOf(if (openNew) 0 else -1) }
    val model = ui.model
    CompScreen {
        CompBackRow("", onBack) { CompIconButton(CompetitionIcons.Plus, "Nueva competencia", { newSheet = 0 }) }
        CompPageTitle("Competencias")
        if (model == null || !ui.ready) return@CompScreen
        if (model.isEmpty) {
            CompCap("Registra la próxima o una que ya hiciste: aquí quedan tus totales, medallas y recuerdos.", Modifier.padding(top = 6.dp),
                color = CompColors.muted)
            CompPill("Nueva competencia", { newSheet = 0 }, Modifier.fillMaxWidth().padding(top = 18.dp), icon = CompetitionIcons.Plus)
            return@CompScreen
        }
        val next = model.next
        if (next != null) {
            NextCompetitionCard(next, ui, onOpenCompetition, onStartFlow, onOpenBodyweight)
        } else {
            CompPill("Nueva competencia", { newSheet = 0 }, Modifier.fillMaxWidth().padding(top = 14.dp), icon = CompetitionIcons.Plus)
        }
        if (model.history.isNotEmpty()) {
            CompGroupLabel("Tu historial", model.historyCount)
            if (model.chart.size >= 2) CompModule {
                CompLabel("Total en competición")
                CompTotalsChart(model.chart)
                model.chartCaption?.let { CompCap(it, Modifier.padding(top = 4.dp)) }
            }
            CompModule(tight = true) {
                model.history.forEachIndexed { index, row ->
                    CompMeetRow(row, divider = index > 0, onClick = {
                        val record = ui.records.firstOrNull { it.id == row.id }
                        if (record != null && record.status == CompetitionRecordStatus.PLANNED) onStartFlow(row.id, CompetitionFlowMode.LOG.id, null)
                        else onOpenCompetition(row.id)
                    })
                }
            }
        }
        if (next != null) CompPill("Registrar una competencia", { newSheet = 1 }, Modifier.fillMaxWidth(), primary = false, icon = CompetitionIcons.Plus)
    }
    if (newSheet >= 0) NewCompetitionSheet(
        initialMoment = newSheet,
        preselected = CompetitionDiscipline.preselected(ui.sports),
        groups = CompetitionDiscipline.grouped(ui.sports),
        onContinue = { moment, discipline ->
            newSheet = -1
            onStartFlow(CompetitionFlowViewModel.NEW_ID, if (moment == 0) CompetitionFlowMode.NEXT.id else CompetitionFlowMode.DONE.id, discipline.id)
        },
        onDismiss = { newSheet = -1 },
    )
}

/** La tarjeta «Próxima» del hub. */
@Composable
private fun NextCompetitionCard(
    next: CompetitionNextCard,
    ui: CompetitionsHubUi,
    onOpenCompetition: (String) -> Unit,
    onStartFlow: (String, String, String?) -> Unit,
    onOpenBodyweight: () -> Unit,
) {
    val meetDay = next.daysUntil == 0L
    CompNextCard(LineCoverStyle.ORBITAS, LineCoverBackground.NOCHE, seed = 4, onClick = { onOpenCompetition(next.id) }) {
        CompLabel("Próxima", color = CompColors.mus)
        next.daysUntil?.let { CompCountdown(it, if (it == 1L) "día" else "días", Modifier.padding(top = 6.dp)) }
        Text(next.title, Modifier.padding(top = 6.dp), style = CompType.syne(21, FontWeight.Bold, 1.15f), color = CompColors.ink)
        CompCap(next.meta, Modifier.padding(top = 4.dp))
        CompOpeners(next.openers)
        val load = next.discipline.form == CompetitionResultForm.LOAD
        val weighIn = next.discipline.hasWeighIn
        Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when {
                meetDay && load -> CompPill("Modo competición", { onStartFlow(next.id, CompetitionFlowMode.TODAY.id, null) }, Modifier.weight(1f), small = true)
                next.discipline == CompetitionDiscipline.POWERLIFTING ->
                    CompPill("Ver plan del día", { onStartFlow(next.id, CompetitionFlowMode.PLAN.id, null) }, Modifier.weight(1f), small = true)
                else -> CompPill("Ver la competencia", { onOpenCompetition(next.id) }, Modifier.weight(1f), small = true)
            }
            if (weighIn) {
                CompPill("Pesaje: ${ui.bodyweightKg?.let { CompetitionFormat.kg(it) } ?: "—"} kg", onOpenBodyweight, Modifier.weight(1f), primary = false,
                    small = true, icon = CompetitionIcons.Scale)
            }
        }
    }
}

/** c-new: «Voy a competir» o «Ya competí» y la disciplina, con las de tus programas primero. */
@Composable
private fun NewCompetitionSheet(
    initialMoment: Int,
    preselected: CompetitionDiscipline,
    groups: List<Pair<com.example.kpkn.domain.competitions.CompetitionDisciplineGroup, List<CompetitionDiscipline>>>,
    onContinue: (Int, CompetitionDiscipline) -> Unit,
    onDismiss: () -> Unit,
) {
    var moment by remember { mutableStateOf(initialMoment) }
    var chosen by remember { mutableStateOf(preselected) }
    CompSheet(onDismiss) {
        CompSheetTitle("Nueva competencia")
        CompSeg(listOf("Voy a competir", "Ya competí"), moment, { moment = it }, Modifier.padding(top = 4.dp, bottom = 12.dp))
        groups.forEach { (group, disciplines) ->
            CompMGroup(group.label)
            Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                disciplines.chunked(4).forEachIndexed { rowIndex, row ->
                    Row(Modifier.fillMaxWidth().padding(top = if (rowIndex == 0) 0.dp else 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { d ->
                            CompDisciplineButton(CompetitionIcons.byKey(d.icon), d.label, chosen == d, { chosen = d }, Modifier.weight(1f))
                        }
                        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        CompPill("Continuar", { onContinue(moment, chosen) }, Modifier.fillMaxWidth().padding(top = 18.dp))
    }
}

/** La fila «Competencias» de Perfil (C-D1): la próxima con su cuenta atrás, o tu historial. */
@Composable
fun rememberCompetitionsSummary(viewModel: CompetitionsViewModel = viewModel()): String {
    val ui by viewModel.ui.collectAsState()
    val model = ui.model ?: return ""
    val next = model.next
    return when {
        next != null -> when (val days = next.daysUntil) {
            null -> next.title
            0L -> "${next.title} · hoy"
            1L -> "${next.title} · mañana"
            else -> "${next.title} · en $days días"
        }
        model.history.isNotEmpty() -> if (model.history.size == 1) "1 competencia" else "${model.history.size} competencias"
        else -> "La próxima y las que ya hiciste"
    }
}

