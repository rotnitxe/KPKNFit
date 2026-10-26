package com.example.kpkn.screens.programdetail.design

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.Program
import com.example.kpkn.domain.training.ProgramHierarchyIndex
import com.example.kpkn.domain.training.acceptsKpknAdjustments
import com.example.kpkn.domain.training.schedule.*
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.design.KpknType
import com.example.kpkn.ui.design.KpknCompactPill
import com.example.kpkn.ui.design.KpknModule

@Composable
internal fun ProgramScheduleModule(program: Program,request: MissedSessionsRequest?,notice: InactivityNotice?,
    busy: Boolean,onChoice: (MissedSessionChoice) -> Unit,onSoftReturn: () -> Unit) {
    request?.let {
        val weekName = ProgramHierarchyIndex(program).locateWeek(it.weekId)?.week?.name ?: "semana indicada"
        val default = MissedSessionPolicy.defaultChoice(program)
        KpknModule(Modifier.fillMaxWidth()) {
            Text("${it.sessionIds.size} sesiones requeridas sin registro · $weekName",style = KpknType.bodyStrong,color = WizardColors.text)
            Text("Puedes hacerlas esta semana o declararlas saltadas. Saltarlas permite continuar, pero no crea entrenamientos ni mejora tu cobertura registrada.",style = KpknType.body,color = WizardColors.text)
            if (!busy) {
                KpknCompactPill(if (default == MissedSessionChoice.SKIP) "Revisar salto de sesiones" else "Las haré esta semana",
                    { onChoice(default) },fullWidth = true)
                KpknCompactPill(if (default == MissedSessionChoice.SKIP) "Las haré esta semana" else "Revisar salto de sesiones",
                    { onChoice(if (default == MissedSessionChoice.SKIP) MissedSessionChoice.DO_THIS_WEEK else MissedSessionChoice.SKIP) },filled = false,fullWidth = true)
                if (program.acceptsKpknAdjustments()) KpknCompactPill("Revisar vuelta suave",{ onChoice(MissedSessionChoice.SOFT_RETURN) },filled = false,fullWidth = true)
            }
        }
    }
    notice?.let {
        KpknModule(Modifier.fillMaxWidth()) {
            Text(it.message,style = KpknType.body,color = WizardColors.text)
            if (!busy && program.acceptsKpknAdjustments()) KpknCompactPill("Revisar vuelta suave",onSoftReturn,filled = false,fullWidth = true)
        }
    }
}

@Composable
internal fun ProgramScheduleReviewSheet(original: Program,result: Program,busy: Boolean,onConfirm: () -> Unit,onDismiss: () -> Unit) {
    KpknSheet(onDismissRequest = { if (!busy) onDismiss() },stableHeightFraction = 0.9f) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Revisar continuidad",style = KpknType.bodyStrong,color = WizardColors.text)
            val addedDecisions = result.programDecisions.filter { decision -> original.programDecisions.none { it.id == decision.id } }
            addedDecisions.forEach { Text(it.detail,style = KpknType.body,color = WizardColors.text) }
            val oldIndex = ProgramHierarchyIndex(original)
            ProgramHierarchyIndex(result).orderedWeeks().forEach weekLoop@{ location ->
                val before = oldIndex.locateWeek(location.week.id)?.week ?: return@weekLoop
                if (before == location.week) return@weekLoop
                fun count(p: com.example.kpkn.data.models.ProgramWeek) = p.sessions.sumOf { session -> session.allExercises().sumOf { exercise -> exercise.sets.count { !it.isIneffective } } }
                Text("${location.week.name}: ${count(before)} → ${count(location.week)} series",style = KpknType.bodyStrong,color = WizardColors.text)
                location.week.sessions.forEach sessionLoop@{ session ->
                    val previous = before.sessions.firstOrNull { it.id == session.id } ?: return@sessionLoop
                    if (previous == session) return@sessionLoop
                    session.allExercises().forEach exerciseLoop@{ exercise ->
                        val prev = previous.allExercises().firstOrNull { it.id == exercise.id } ?: return@exerciseLoop
                        if (prev == exercise) return@exerciseLoop
                        fun rir(e: com.example.kpkn.data.models.Exercise) = e.sets.mapNotNull { it.targetRIR }.distinct().sorted().joinToString("/").ifEmpty { "sin RIR" }
                        fun percent(e: com.example.kpkn.data.models.Exercise) = e.sets.mapNotNull { it.targetPercentageRM }.distinct().sorted().joinToString("/").ifEmpty { "sin %" }
                        Text("${exercise.name}: ${prev.sets.size} → ${exercise.sets.size} series · RIR ${rir(prev)} → ${rir(exercise)} · porcentajes ${percent(prev)} → ${percent(exercise)}",style = KpknType.body,color = WizardColors.text)
                    }
                }
            }
            val skipped = result.skippedSessionKeys.size - original.skippedSessionKeys.size
            if (skipped > 0) Text("$skipped sesiones se declararán saltadas. Los registros de entrenamiento se conservan.",style = KpknType.body,color = WizardColors.text)
            if (!busy) {
                KpknCompactPill("Confirmar continuidad",onConfirm,fullWidth = true)
                KpknCompactPill("Volver",onDismiss,filled = false,fullWidth = true)
            } else Text("Guardando…",style = KpknType.caption,color = WizardColors.textMuted)
        }
    }
}
