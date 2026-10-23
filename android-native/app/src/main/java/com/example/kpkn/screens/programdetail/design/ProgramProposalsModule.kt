package com.example.kpkn.screens.programdetail.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.AutoregulationProposal
import com.example.kpkn.data.models.PendingProgramActionType
import com.example.kpkn.data.models.Program
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.programdetail.NativeProgressionCardUi
import com.example.kpkn.screens.programdetail.TrainingMaxRow
import com.example.kpkn.screens.programdetail.trainingMaxRowOf
import com.example.kpkn.screens.programdetail.usesTrainingMax
import com.example.kpkn.ui.design.KpknCompactPill
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknModuleHeader
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknType

/**
 * Propuestas de la pestaña Semana (P2): la autorregulación pendiente, el TM y la progresión nativa, en un módulo con una fila por
 * propuesta: su texto, «Aplicar» (píldora de 40 dp) y «Descartar» (texto). Los avisos llevan «Entendido». Si no hay nada que decir, no se
 * pinta nada. Conserva sus etiquetas de prueba (`program_proposals_card`, `native_progression_*`). Sin estado.
 */
@Composable
internal fun AutoregulationProposalsCard(
    program: Program,
    nativeCard: NativeProgressionCardUi,
    onAccept: () -> Unit,
    onAcceptProposal: (AutoregulationProposal) -> Unit,
    onReject: () -> Unit,
    onAcceptNative: (String) -> Unit,
    onRejectNative: (String) -> Unit,
    onDismissNativeNotice: (String) -> Unit,
    onOpenTm: () -> Unit,
    onRematerialize: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pending = program.runState?.pendingAction
    val hasAutoregPending = pending?.type == PendingProgramActionType.CONFIRM_AUTOREGULATION
    val needsRematerialize = program.macrocycles.any { macro ->
        macro.blocks.any { it.materializationPending }
    }
    LaunchedEffect(program.id, program.macrocycles.flatMap { it.blocks }.filter { it.materializationPending }.map { it.id }) {
        if (needsRematerialize) onRematerialize()
    }
    val tm = program.powerliftingProfile
    val recipe = program.sourceRecipe
    val usesTm = recipe?.let { usesTrainingMax(it) } ?: (tm != null)
    // Sin máximos y con TM en el programa, la fila invita a fijarlos («Añadir»); si el programa no usa TM, no hay fila.
    val tmRow = trainingMaxRowOf(usesTm, tm)
    if (tmRow == TrainingMaxRow.None && !hasAutoregPending && nativeCard.proposals.isEmpty() && nativeCard.notices.isEmpty() && !needsRematerialize) return

    KpknModule(modifier = modifier.fillMaxWidth().testTag("program_proposals_card")) {
        KpknModuleHeader(label = "Propuestas")
        when (tmRow) {
            is TrainingMaxRow.Value -> {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = tmRow.line,
                        style = KpknType.caption,
                        color = WizardColors.textMuted,
                        modifier = Modifier.weight(1f),
                    )
                    KpknTextAction(text = "Editar", onClick = onOpenTm)
                }
            }
            TrainingMaxRow.Unset -> {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Máximos de entrenamiento sin fijar",
                        style = KpknType.caption,
                        color = WizardColors.textMuted,
                        modifier = Modifier.weight(1f),
                    )
                    KpknTextAction(text = "Añadir", onClick = onOpenTm)
                }
            }
            TrainingMaxRow.None -> Unit
        }
        if (hasAutoregPending) {
            Spacer(Modifier.height(8.dp))
            Text(text = pending.message, style = KpknType.body, color = WizardColors.text)
            pending.proposals.forEach { proposal ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "• ${proposal.explanation}",
                        style = KpknType.caption,
                        color = WizardColors.textMuted,
                        modifier = Modifier.weight(1f),
                    )
                    KpknCompactPill(text = "Aplicar", onClick = { onAcceptProposal(proposal) }, height = 40.dp)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                KpknTextAction(text = "Aceptar todo", onClick = onAccept)
                Spacer(Modifier.width(8.dp))
                KpknTextAction(text = "Rechazar", onClick = onReject)
            }
        }
        nativeCard.pendingLabel?.let { label ->
            Spacer(Modifier.height(12.dp))
            Text(
                text = label,
                style = KpknType.bodyStrong,
                color = WizardColors.text,
                modifier = Modifier.testTag("native_progression_header"),
            )
        }
        nativeCard.proposals.forEach { item ->
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp).testTag("native_progression_item_${item.id}"),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(text = item.title, style = KpknType.bodyStrong, color = WizardColors.text)
                Text(text = item.body, style = KpknType.caption, color = WizardColors.textMuted)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    KpknTextAction(
                        text = "Descartar",
                        onClick = { onRejectNative(item.id) },
                        modifier = Modifier.testTag("native_progression_reject_${item.id}"),
                    )
                    Spacer(Modifier.width(8.dp))
                    KpknCompactPill(
                        text = "Aplicar",
                        onClick = { onAcceptNative(item.id) },
                        height = 40.dp,
                        modifier = Modifier.testTag("native_progression_apply_${item.id}"),
                    )
                }
            }
        }
        nativeCard.notices.forEach { notice ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp).testTag("native_progression_notice_${notice.id}"),
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(text = notice.title, style = KpknType.bodyStrong, color = WizardColors.text)
                    Text(text = notice.body, style = KpknType.caption, color = WizardColors.textMuted)
                }
                KpknTextAction(text = "Entendido", onClick = { onDismissNativeNotice(notice.id) })
            }
        }
        if (needsRematerialize) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Se recalculan las semanas pendientes conservando el trabajo realizado.",
                style = KpknType.caption,
                color = WizardColors.textMuted,
            )
        }
    }
}
