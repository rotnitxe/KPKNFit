package com.example.kpkn.screens.technique.hub.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.technique.hub.TechniqueExerciseItem
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Fila del ejercicio con tendencia real; sin dos medidas no dibuja ninguna línea. */
@Composable
fun TechniqueExerciseRow(item: TechniqueExerciseItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val date = SimpleDateFormat("d MMM", Locale.forLanguageTag("es")).format(Date(item.lastRecordedAtMs))
    Column(
        modifier.fillMaxWidth().heightIn(min = 72.dp).clickable(role = Role.Button, onClick = onClick).padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(item.name, style = KpknType.bodyStrong, color = WizardColors.text)
                Text("${item.videoCount} ${if (item.videoCount == 1) "vídeo" else "vídeos"} · último $date", style = KpknType.caption, color = WizardColors.textMuted)
            }
            Icon(KpknLineIcons.ChevronRight, contentDescription = null, tint = WizardColors.textFaint, modifier = Modifier.size(20.dp))
        }
        if (item.sparkline.size >= 2 && item.metricLabel != null) {
            val points = item.sparkline
            val ink = SymbolPalette.musculo
            Text(item.metricLabel, style = KpknType.caption, color = WizardColors.textFaint)
            Canvas(Modifier.fillMaxWidth().size(width = 200.dp, height = 32.dp).semantics {
                contentDescription = "${item.metricLabel}: ${points.joinToString { String.format(Locale.forLanguageTag("es"), "%.1f", it) }}"
            }) {
                val low = points.minOrNull()!!
                val range = (points.maxOrNull()!! - low).coerceAtLeast(1f)
                val path = Path()
                points.forEachIndexed { index, value ->
                    val x = index.toFloat() / points.lastIndex * size.width
                    val y = size.height - 4.dp.toPx() - (value - low) / range * (size.height - 8.dp.toPx())
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, ink, style = Stroke(width = 2.dp.toPx()))
            }
        }
    }
}

