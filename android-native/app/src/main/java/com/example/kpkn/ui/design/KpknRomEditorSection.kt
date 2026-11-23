package com.example.kpkn.ui.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.PlannedRomTarget
import com.example.kpkn.data.models.RomScaleMode
import com.example.kpkn.domain.workout.RomTargetCatalog
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.entreno.SymbolPalette
import kotlin.math.roundToInt

/**
 * Selector de ROM programable (#13): soporta Grados (°), Referencia cualitativa (con presets + etiqueta libre)
 * o Escala 1–10 usando [KpknExpressiveSlider] (cero puntos genéricos).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KpknRomEditorSection(
    target: PlannedRomTarget?,
    onChange: (PlannedRomTarget) -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Recorrido (ROM)",
    planMarkerTarget: PlannedRomTarget? = null,
) {
    val active = target ?: RomTargetCatalog.defaultForMode(RomScaleMode.QUALITATIVE)
    var customLabelInput by remember(active.mode, active.label) {
        mutableStateOf(
            if (active.mode == RomScaleMode.QUALITATIVE &&
                RomTargetCatalog.qualitativePresets.none { it.label.equals(active.label, ignoreCase = true) }
            ) {
                active.label.orEmpty()
            } else {
                ""
            },
        )
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = KpknType.bodyStrong, color = WizardColors.text)
            Text(
                text = RomTargetCatalog.format(active),
                style = KpknType.chip,
                color = SymbolPalette.energia,
            )
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            KpknChip(
                text = "Referencia",
                selected = active.mode == RomScaleMode.QUALITATIVE,
                onClick = { onChange(RomTargetCatalog.defaultForMode(RomScaleMode.QUALITATIVE)) },
            )
            KpknChip(
                text = "Grados (°)",
                selected = active.mode == RomScaleMode.DEGREES,
                onClick = { onChange(RomTargetCatalog.defaultForMode(RomScaleMode.DEGREES)) },
            )
            KpknChip(
                text = "1 a 10",
                selected = active.mode == RomScaleMode.SCALE_1_10,
                onClick = { onChange(RomTargetCatalog.defaultForMode(RomScaleMode.SCALE_1_10)) },
            )
        }

        val planFraction = planMarkerTarget
            ?.takeIf { it.mode == active.mode }
            ?.let { RomTargetCatalog.sliderFraction(it) }

        when (active.mode) {
            RomScaleMode.DEGREES -> {
                val deg = (active.numericValue ?: 90).coerceIn(15, 180)
                KpknExpressiveSlider(
                    value = deg.toFloat(),
                    onValueChange = { next ->
                        val snapped = ((next / 5f).roundToInt() * 5).coerceIn(15, 180)
                        onChange(PlannedRomTarget(mode = RomScaleMode.DEGREES, numericValue = snapped))
                    },
                    valueRange = 15f..180f,
                    steps = 32,
                    minColor = Color(0xFF5B7FA6),
                    midColor = Color(0xFFE6E1D5),
                    maxColor = SymbolPalette.energia,
                    planFraction = planFraction,
                    bubbleLabel = { "${it.roundToInt()}°" },
                    semanticLabel = "Ángulo ROM en grados",
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("15°", style = KpknType.caption, color = WizardColors.textMuted)
                    Text("90°", style = KpknType.caption, color = WizardColors.textMuted)
                    Text("180°", style = KpknType.caption, color = WizardColors.textMuted)
                }
            }
            RomScaleMode.SCALE_1_10 -> {
                val score = (active.numericValue ?: 8).coerceIn(1, 10)
                KpknExpressiveSlider(
                    value = score.toFloat(),
                    onValueChange = { next ->
                        onChange(PlannedRomTarget(mode = RomScaleMode.SCALE_1_10, numericValue = next.roundToInt().coerceIn(1, 10)))
                    },
                    valueRange = 1f..10f,
                    steps = 8,
                    minColor = Color(0xFF5B7FA6),
                    midColor = Color(0xFFE6E1D5),
                    maxColor = Color(0xFF5CE1B8),
                    planFraction = planFraction,
                    bubbleLabel = { "${it.roundToInt()}/10" },
                    semanticLabel = "Recorrido ROM del 1 al 10",
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("1 · Corto", style = KpknType.caption, color = WizardColors.textMuted)
                    Text("10 · Completo", style = KpknType.caption, color = WizardColors.textMuted)
                }
            }
            RomScaleMode.QUALITATIVE -> {
                val presets = RomTargetCatalog.qualitativePresets
                val idx = (active.numericValue ?: presets.indexOfFirst {
                    it.label.equals(active.label, ignoreCase = true)
                }.takeIf { it >= 0 } ?: 2).coerceIn(0, presets.lastIndex)
                KpknExpressiveSlider(
                    value = idx.toFloat(),
                    onValueChange = { next ->
                        val i = next.roundToInt().coerceIn(0, presets.lastIndex)
                        customLabelInput = ""
                        onChange(
                            PlannedRomTarget(
                                mode = RomScaleMode.QUALITATIVE,
                                numericValue = i,
                                label = presets[i].label,
                            ),
                        )
                    },
                    valueRange = 0f..presets.lastIndex.toFloat(),
                    steps = (presets.size - 2).coerceAtLeast(0),
                    minColor = Color(0xFF5B7FA6),
                    midColor = Color(0xFFE6E1D5),
                    maxColor = Color(0xFF5CE1B8),
                    planFraction = planFraction,
                    bubbleLabel = { presets[it.roundToInt().coerceIn(0, presets.lastIndex)].label },
                    semanticLabel = "Referencia de recorrido ROM",
                )
                KpknTextField(
                    value = customLabelInput,
                    onValueChange = { text ->
                        customLabelInput = text
                        if (text.isNotBlank()) {
                            onChange(
                                PlannedRomTarget(
                                    mode = RomScaleMode.QUALITATIVE,
                                    numericValue = idx,
                                    label = text.trim(),
                                ),
                            )
                        } else {
                            onChange(
                                PlannedRomTarget(
                                    mode = RomScaleMode.QUALITATIVE,
                                    numericValue = idx,
                                    label = presets[idx].label,
                                ),
                            )
                        }
                    },
                    label = "Referencia personalizada (opcional)",
                    placeholder = "ej: Tope pin 4, pecho, paralela",
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            RomScaleMode.PERCENT -> {
                val pct = (active.numericValue ?: 100).coerceIn(10, 120)
                KpknExpressiveSlider(
                    value = pct.toFloat(),
                    onValueChange = { next ->
                        val snapped = ((next / 5f).roundToInt() * 5).coerceIn(10, 120)
                        onChange(PlannedRomTarget(mode = RomScaleMode.PERCENT, numericValue = snapped))
                    },
                    valueRange = 10f..120f,
                    steps = 21,
                    minColor = Color(0xFF5B7FA6),
                    midColor = Color(0xFFE6E1D5),
                    maxColor = Color(0xFF5CE1B8),
                    planFraction = planFraction,
                    bubbleLabel = { "${it.roundToInt()}%" },
                    semanticLabel = "Porcentaje de ROM",
                )
            }
        }
    }
}
