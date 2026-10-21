package com.example.kpkn.screens.home.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardTypography
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette

/** Un músculo de la rejilla de RINGS: su nombre y su recuperación (0–100); solo con dato. */
internal data class HomeRingMuscle(val name: String, val score: Int)

/**
 * Los músculos de un canal uno a uno, en rejilla de 3 columnas: nombre en `caption`, % en Syne y una barra de 3 dp en el acento de
 * músculo. Sin [muscles] no pinta nada. Etiqueta `home-ring-muscle-grid`.
 */
@Composable
internal fun HomeRingMuscleGrid(muscles: List<HomeRingMuscle>, modifier: Modifier = Modifier) {
    if (muscles.isEmpty()) return
    Column(
        modifier = modifier.fillMaxWidth().testTag("home-ring-muscle-grid"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        muscles.chunked(3).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { muscle -> HomeRingMuscleCell(muscle = muscle, modifier = Modifier.weight(1f)) }
                // Las filas incompletas conservan el ancho de las columnas.
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Una celda de la rejilla: nombre, cifra y barra; sus cifras cuentan la primera vez y su barra crece. */
@Composable
private fun HomeRingMuscleCell(muscle: HomeRingMuscle, modifier: Modifier = Modifier) {
    val shown = rememberHomeCount(key = "muscle-${muscle.name}", target = muscle.score)
    val bar = rememberHomeTrace(key = "muscle-bar-${muscle.name}", target = muscle.score.coerceIn(0, 100) / 100f)
    Column(
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = "${muscle.name}, ${muscle.score} %"
        },
    ) {
        // Dos líneas como mucho y sin cortar el nombre: «Isquiotibiales» a 130 % no debe quedarse en «Isquiotibi…».
        Text(
            text = muscle.name,
            style = WizardTypography.caption,
            color = WizardColors.textMuted,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.heightIn(min = 36.dp),
        )
        Text(text = "$shown %", style = HomeTypography.ringValueSmall, color = WizardColors.text, maxLines = 1)
        Spacer(Modifier.height(6.dp))
        Canvas(Modifier.fillMaxWidth().height(3.dp)) {
            drawRect(color = Color.White.copy(alpha = 0.10f), size = size)
            drawRect(color = SymbolPalette.musculo, size = Size(size.width * bar, size.height))
        }
    }
}
