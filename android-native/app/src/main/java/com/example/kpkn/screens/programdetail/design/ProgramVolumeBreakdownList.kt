package com.example.kpkn.screens.programdetail.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknType
import java.util.Locale

/**
 * Desglose de un músculo desplegado (P4): los ejercicios que aportan volumen, y debajo las cabezas del músculo con sus ejercicios.
 * Cada línea es una fila KPKN: nombre a la izquierda, series semanales a la derecha con números tabulares, y un filete entre filas.
 */
@Composable
fun ProgramVolumeBreakdownList(
    breakdown: ProgramVolumeBreakdown,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 6.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "Ejercicios que aportan volumen".uppercase(Locale("es")),
            style = KpknType.eyebrow,
            color = WizardColors.textMuted,
        )
        if (breakdown.exercises.isEmpty()) {
            Text(
                text = "Sin ejercicios registrados para este músculo en este alcance.",
                style = KpknType.caption,
                color = WizardColors.textMuted,
            )
        } else {
            breakdown.exercises.forEachIndexed { index, line ->
                if (index > 0) BreakdownDivider()
                BreakdownLine(
                    name = line.name,
                    sets = line.sets,
                    nameStyle = KpknType.body,
                )
            }
        }

        if (breakdown.subMuscles.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Por cabeza".uppercase(Locale("es")),
                style = KpknType.eyebrow,
                color = WizardColors.textMuted,
            )
            breakdown.subMuscles.forEach { sub ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    BreakdownLine(name = sub.name, sets = sub.sets, nameStyle = KpknType.bodyStrong)
                    if (sub.exercises.isEmpty()) {
                        Text(
                            text = "Sin ejercicios registrados para esta cabeza.",
                            style = KpknType.caption,
                            color = WizardColors.textMuted,
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    } else {
                        sub.exercises.forEach { line ->
                            BreakdownLine(
                                name = line.name,
                                sets = line.sets,
                                nameStyle = KpknType.caption,
                                indent = 12.dp,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Una línea del desglose: nombre a la izquierda y «N series/sem» a la derecha, con números tabulares. */
@Composable
private fun BreakdownLine(
    name: String,
    sets: Double,
    nameStyle: TextStyle,
    indent: Dp = 0.dp,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = indent),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = name,
            style = nameStyle,
            color = WizardColors.text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = "${programVolumeSeries(sets)} series/sem",
            style = KpknType.caption.copy(fontFeatureSettings = "'tnum' 1"),
            color = WizardColors.textMuted,
            maxLines = 1,
        )
    }
}

/** Filete de 1 dp entre filas del desglose. */
@Composable
private fun BreakdownDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(KpknSurface.divider))
}
