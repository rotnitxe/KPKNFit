package com.example.kpkn.screens.progress.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.domain.training.MuscleVolumePanelBuilder
import com.example.kpkn.domain.training.MuscleVolumeRow
import com.example.kpkn.domain.training.VolumeCountingMode
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.programdetail.design.programVolumeSeries
import com.example.kpkn.screens.progress.MuscleVolumeBodyModel
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknType
import java.util.Locale

/**
 * Desglose de un músculo (T5a): los ejercicios que explican su cifra, con lo que aporta cada uno, en cuántas sesiones apareció y
 * cuándo fue la última. Debajo, una línea de total que ES la cifra de la fila (las líneas se redondean a décimas repartiendo el resto,
 * así que lo que se ve suma lo que se ve). Con +Secundario o +Estabilizador, el reparto por rol; si el músculo tiene cabezas, sus
 * porciones, que comparten series y no se suman. Lo usan la lista y el cuerpo: es el mismo desglose en las dos vistas.
 */
@Composable
fun MuscleVolumeBreakdown(
    row: MuscleVolumeRow,
    unit: String,
    mode: VolumeCountingMode,
    modifier: Modifier = Modifier,
) {
    val tenths = remember(row) { MuscleVolumePanelBuilder.reconcileTenths(row.contributors.map { it.sets }) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("muscle-volume-breakdown-${row.muscleName}"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "Ejercicios que aportan".uppercase(Locale("es")),
            style = KpknType.eyebrow,
            color = WizardColors.textMuted,
        )
        if (row.contributors.isEmpty()) {
            Text(
                text = if (row.hasData) "Sin series de este músculo en esta ventana." else "Sin datos en esta ventana.",
                style = KpknType.caption,
                color = WizardColors.textMuted,
            )
            return@Column
        }
        row.contributors.forEachIndexed { index, contributor ->
            if (index > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(KpknSurface.divider))
            val sessions = if (contributor.sessions == 1) "1 sesión" else "${contributor.sessions} sesiones"
            val last = contributor.lastDate?.let { " · última ${MuscleVolumeBodyModel.shortDate(it)}" }.orEmpty()
            val amount = programVolumeSeries(tenths[index] / 10.0)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics(mergeDescendants = true) {
                        contentDescription = "${contributor.exerciseName}, $amount $unit, $sessions$last"
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = contributor.exerciseName,
                        style = KpknType.body,
                        color = WizardColors.text,
                        maxLines = 2,
                    )
                    Text(
                        text = sessions + last,
                        style = KpknType.caption,
                        color = WizardColors.textMuted,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "$amount $unit",
                    style = KpknType.caption.copy(fontFeatureSettings = "'tnum' 1"),
                    color = WizardColors.textMuted,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(KpknSurface.divider))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {
                    contentDescription = "Total de ${row.muscleName}, ${programVolumeSeries(row.sets ?: 0.0)} $unit"
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Total del músculo",
                style = KpknType.bodyStrong,
                color = WizardColors.text,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = "${programVolumeSeries(row.sets ?: 0.0)} $unit",
                style = KpknType.bodyStrong.copy(fontFeatureSettings = "'tnum' 1"),
                color = WizardColors.text,
                maxLines = 1,
                softWrap = false,
            )
        }

        if (mode != VolumeCountingMode.DIRECT) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Por rol".uppercase(Locale("es")),
                style = KpknType.eyebrow,
                color = WizardColors.textMuted,
            )
            RoleLine("Directo", row.roles.directSets, unit)
            RoleLine("Secundario", row.roles.secondarySets, unit)
            if (mode == VolumeCountingMode.STABILIZER) RoleLine("Estabilizador", row.roles.stabilizerSets, unit)
        }

        if (row.portions.isNotEmpty()) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Por cabeza".uppercase(Locale("es")),
                style = KpknType.eyebrow,
                color = WizardColors.textMuted,
            )
            Text(
                text = "Las cabezas comparten series; no se suman al músculo.",
                style = KpknType.caption,
                color = WizardColors.textMuted,
            )
            row.portions.forEach { portion ->
                val role = when (portion.role) {
                    MuscleRole.PRIMARY -> "Directo"
                    MuscleRole.SECONDARY -> "Secundario"
                    MuscleRole.STABILIZER -> "Estabilizador"
                    MuscleRole.NEUTRALIZER -> null
                }
                RoleLine(listOfNotNull(portion.name, role).joinToString(" · "), portion.sets, unit)
            }
        }
    }
}

/** Una línea nombre + cifra, para roles y cabezas. */
@Composable
private fun RoleLine(label: String, sets: Double, unit: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = "$label, ${programVolumeSeries(sets)} $unit" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = KpknType.body, color = WizardColors.text, modifier = Modifier.weight(1f), maxLines = 2)
        Spacer(Modifier.width(12.dp))
        Text(
            text = "${programVolumeSeries(sets)} $unit",
            style = KpknType.caption.copy(fontFeatureSettings = "'tnum' 1"),
            color = WizardColors.textMuted,
            maxLines = 1,
            softWrap = false,
        )
    }
}
