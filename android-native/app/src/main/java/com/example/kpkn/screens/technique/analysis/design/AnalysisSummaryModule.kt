package com.example.kpkn.screens.technique.analysis.design

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.biomechanics.metrics.CountConfidence
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import com.example.kpkn.screens.onboarding.design.wizardReducedMotion
import com.example.kpkn.screens.technique.analysis.AnalysisSummaryUi
import com.example.kpkn.screens.technique.analysis.SummaryTileUi
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknMetric
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknModuleHeader
import com.example.kpkn.ui.design.KpknType

/** Etiqueta de prueba del módulo «Resumen». */
const val ANALYSIS_SUMMARY_TAG = "analysis-summary"

/** Etiqueta de prueba de una celda del resumen (`analysis-tile-REPS`, `analysis-tile-VELOCITY_LOSS`…). */
fun analysisTileTag(id: Enum<*>): String = "analysis-tile-${id.name}"

/**
 * El módulo «Resumen» (sin estado): una rejilla de dos columnas con las cifras del análisis (repeticiones, TUT, tempo, ROM, pérdida
 * de velocidad, velocidad media y desvío de la barra), cada una con su unidad en la misma línea base, su nombre debajo y, en pequeño,
 * lo que la matiza. Debajo, en una línea, la profundidad si se midió.
 *
 * - Sin dato, la celda no existe (no hay ceros de relleno). La pérdida de velocidad sin dato fiable lleva «Sin dato fiable» en lugar
 *   de cifra.
 * - Las estimaciones llevan «≈» (el conteo con confianza no ALTA, la velocidad en m/s, la profundidad).
 * - Con la letra grande (≥ 120 %) la rejilla pasa a una columna: la cifra y su unidad nunca se cortan.
 * - Las cifras enteras cuentan hacia arriba la primera vez que se ven; con movimiento reducido salen ya con su valor final.
 */
@Composable
fun AnalysisSummaryModule(
    summary: AnalysisSummaryUi,
    modifier: Modifier = Modifier,
) {
    val twoColumns = LocalDensity.current.fontScale < LARGE_FONT_SCALE
    KpknModule(modifier = modifier.testTag(ANALYSIS_SUMMARY_TAG)) {
        KpknModuleHeader("Resumen")
        val message = summary.noRepsMessage
        if (message != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AnalysisIconCircle(KpknLineIcons.Info)
                Spacer(Modifier.width(14.dp))
                Text(text = message, style = KpknType.bodyStrong, color = WizardColors.text, modifier = Modifier.weight(1f))
            }
            return@KpknModule
        }
        Spacer(Modifier.height(4.dp))
        val rows = if (twoColumns) summary.tiles.chunked(2) else summary.tiles.map { listOf(it) }
        rows.forEachIndexed { index, row ->
            if (index > 0) Spacer(Modifier.height(22.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                SummaryTile(row[0], Modifier.weight(1f))
                if (twoColumns) {
                    if (row.size > 1) SummaryTile(row[1], Modifier.weight(1f)) else Spacer(Modifier.weight(1f))
                }
            }
        }
        val depth = summary.depthLine
        if (depth != null) {
            Spacer(Modifier.height(18.dp))
            AnalysisDivider()
            Spacer(Modifier.height(14.dp))
            // «Bajo el paralelo en ≈ 7 de 8 · a la par en 1»: cada parte en su línea (partida a media frase deja un «1» huérfano).
            depth.split(DEPTH_SEPARATOR).forEachIndexed { index, part ->
                if (index > 0) Spacer(Modifier.height(2.dp))
                Text(text = part.keepTogether(), style = KpknType.body, color = WizardColors.text)
            }
        }
    }
}

/** Desde esta escala de letra la rejilla del resumen pasa a una columna. */
private const val LARGE_FONT_SCALE = 1.2f

/** Lo que separa las partes de la línea de profundidad. */
private const val DEPTH_SEPARATOR = " · "

/** El espacio que no deja partir la línea (U+00A0). */
private const val NO_BREAK_SPACE = '\u00A0'

/** Como mucho tantas letras tiene el final que se deja junto: más largo se partiría a media palabra en una columna estrecha. */
private const val MAX_GLUED_TAIL = 14

/** Una cifra, un espacio y su unidad (que no sea el principio de una palabra: «3 segundos» no es «3 s»). */
private val UNIT_AFTER_NUMBER = Regex("(\\d) (m/s|cm|kg|mm|s|%)(?!\\p{L})")

/**
 * Evita las líneas sueltas al partir un texto corto en una columna estrecha: pega «≈» a su cifra, la cifra a su unidad y deja juntas
 * las dos últimas palabras (si caben). Solo cambia el tipo de espacio, no el texto.
 */
internal fun String.keepTogether(): String {
    var text = replace("≈ ", "≈$NO_BREAK_SPACE")
    text = UNIT_AFTER_NUMBER.replace(text) { match -> match.groupValues[1] + NO_BREAK_SPACE + match.groupValues[2] }
    val words = text.split(' ')
    if (words.size < 3) return text
    val last = words[words.size - 1]
    val before = words[words.size - 2]
    if (NO_BREAK_SPACE in last || NO_BREAK_SPACE in before || last.length + before.length + 1 > MAX_GLUED_TAIL) return text
    return words.dropLast(2).joinToString(" ") + " " + before + NO_BREAK_SPACE + last
}

@Composable
private fun SummaryTile(tile: SummaryTileUi, modifier: Modifier) {
    Column(modifier = modifier.testTag(analysisTileTag(tile.id)).semantics(mergeDescendants = true) { }) {
        val note = tile.note
        if (note != null) {
            // Sin cifra: el texto ocupa su sitio y deja la etiqueta a la misma altura que la de la celda de al lado.
            Box(modifier = Modifier.heightIn(min = 32.dp), contentAlignment = Alignment.CenterStart) {
                Text(text = note, style = KpknType.bodyStrong, color = WizardColors.textMuted)
            }
        } else {
            KpknMetric(
                value = countedFigure(tile),
                unit = tile.unit,
                style = KpknType.metricL,
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(text = tile.label, style = KpknType.caption, color = WizardColors.textMuted, maxLines = 2)
        for (line in tile.notes) {
            Spacer(Modifier.height(2.dp))
            NoteLine(text = line, confidence = tile.confidence.takeIf { line.startsWith("Confianza") })
        }
    }
}

/** Una línea pequeña bajo la etiqueta; la de la confianza lleva delante un punto del color de su nivel. */
@Composable
private fun NoteLine(text: String, confidence: CountConfidence?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (confidence != null) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(confidenceColor(confidence)),
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(text = text.keepTogether(), style = KpknType.caption, color = WizardColors.textFaint)
    }
}

private fun confidenceColor(confidence: CountConfidence): Color = when (confidence) {
    CountConfidence.ALTA -> WizardColors.done
    CountConfidence.MEDIA -> SymbolPalette.energia
    CountConfidence.BAJA -> WizardColors.danger
}

/** El texto de la cifra: con [SummaryTileUi.countTo] cuenta de 0 al valor; si no, tal cual. */
@Composable
private fun countedFigure(tile: SummaryTileUi): String {
    val target = tile.countTo
    val number = if (target != null) rememberCountUp(key = "summary-${tile.id.name}", target = target).toString() else tile.value
    return tile.prefix + number + tile.suffix
}

/** Cifras ya contadas en este proceso: la cuenta es una sola vez por proceso. */
private object CountMemory {
    private val counted = HashSet<String>()

    @Synchronized
    fun firstTime(key: String): Boolean = counted.add(key)
}

/**
 * Cuenta de 0 a [target] en 900 ms la primera vez que el proceso muestra [key]; después, o con movimiento reducido, devuelve el valor
 * final sin animar.
 */
@Composable
private fun rememberCountUp(key: String, target: Int): Int {
    val reduced = wizardReducedMotion()
    // La plaza se consume aunque haya movimiento reducido: así la siguiente vez tampoco cuenta.
    val animate = remember(key) { CountMemory.firstTime(key) } && !reduced
    var goal by remember(key) { mutableIntStateOf(if (animate) 0 else target) }
    LaunchedEffect(key, target) { goal = target }
    val spec: AnimationSpec<Int> = if (reduced) snap() else tween(durationMillis = 900, easing = FastOutSlowInEasing)
    return animateIntAsState(targetValue = goal, animationSpec = spec, label = "analysisCount").value
}
