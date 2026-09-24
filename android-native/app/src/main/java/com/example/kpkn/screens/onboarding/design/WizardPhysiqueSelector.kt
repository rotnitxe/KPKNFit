package com.example.kpkn.screens.onboarding.design

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.nutrition.EerSex
import com.example.kpkn.domain.nutrition.bodyFatForSliderPos
import com.example.kpkn.domain.nutrition.physiqueDescForSliderPos
import com.example.kpkn.domain.nutrition.physiqueLabelForSliderPos
import com.example.kpkn.screens.nutrition.VerticalPhysiqueSlider
import com.example.kpkn.screens.nutrition.WizardFemaleFrames
import com.example.kpkn.screens.nutrition.WizardMaleFrames
import kotlin.math.roundToInt

/** Procedencia del dato de grasa corporal. La imagen nunca es una medición. */
enum class WizardBodyFatSource { VISUAL_ESTIMATE, KNOWN_PERCENT }

data class WizardBodyFatValue(
    val percent: Double,
    val source: WizardBodyFatSource,
)

/**
 * Estado actual de grasa corporal sobre los personajes existentes de KPKN.
 *
 * Reglas fijadas:
 * - Es **estado actual**, nunca una meta; el CTA no dice "usar como meta".
 * - La ilustración es una referencia visual, no una medición de la persona.
 * - Cambiar de personaje no altera `equationSex`.
 * - La posición inicial del slider **no** cuenta como respuesta: hay que
 *   confirmar de forma explícita.
 * - El porcentaje conocido puede salirse del rango ilustrado (10–40 %).
 */
@Composable
fun WizardPhysiqueSelector(
    candidate: WizardBodyFatValue?,
    onCandidateChange: (WizardBodyFatValue) -> Unit,
    modifier: Modifier = Modifier,
) {
    var exampleSex by remember { mutableStateOf(EerSex.FEMALE) }
    var position by remember { mutableFloatStateOf(4f) }
    var mode by remember { mutableStateOf(WizardBodyFatSource.VISUAL_ESTIMATE) }
    var knownText by remember { mutableStateOf("") }
    var confirmed by remember { mutableStateOf(false) }

    val visualPercent = bodyFatForSliderPos(position)
    val knownPercent = knownText.replace(',', '.').toDoubleOrNull()
    val effectivePercent = when (mode) {
        WizardBodyFatSource.VISUAL_ESTIMATE -> visualPercent
        WizardBodyFatSource.KNOWN_PERCENT -> knownPercent
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(WizardSpacing.cardGap),
    ) {
        Text(
            text = "Estimación visual de tu estado actual",
            style = WizardTypography.cardTitle,
            color = WizardColors.text,
        )
        Text(
            text = "La ilustración orienta, no mide tu composición. Elige los ejemplos que quieras ver; esto no cambia el sexo usado por la ecuación.",
            style = WizardTypography.cardSubtitle,
            color = WizardColors.textMuted,
        )

        WizardUnitToggle(
            options = listOf(EerSex.FEMALE to "Ejemplos ♀", EerSex.MALE to "Ejemplos ♂"),
            selected = exampleSex to if (exampleSex == EerSex.FEMALE) "Ejemplos ♀" else "Ejemplos ♂",
            label = { it.second },
            onSelected = { exampleSex = it.first },
        )

        val frames = if (exampleSex == EerSex.FEMALE) WizardFemaleFrames else WizardMaleFrames
        val frameIdx = (((position - 1f) / 6f) * (frames.size - 1)).roundToInt().coerceIn(0, frames.lastIndex)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(268.dp)
                .clip(WizardShapes.panel)
                .background(WizardColors.cardFill)
                .border(WizardColors.unselectedBorderWidth, WizardColors.cardBorder, WizardShapes.panel)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(frames[frameIdx]),
                contentDescription = "Ejemplo visual de composición corporal",
                contentScale = ContentScale.Fit,
                modifier = Modifier.weight(1f).fillMaxWidth().height(250.dp),
            )
            VerticalPhysiqueSlider(
                pos = position,
                onPosChange = { position = it },
                height = 232.dp,
                hitWidth = 46.dp,
                tint = WizardColors.ruleCursor,
                withBorder = false,
            )
        }

        Text(physiqueLabelForSliderPos(position), style = WizardTypography.bodySmall, color = WizardColors.text)
        Text(physiqueDescForSliderPos(position), style = WizardTypography.caption, color = WizardColors.textMuted)
        Text(
            text = "≈ ${visualPercent.roundToInt()} % de grasa corporal (estimación visual)",
            style = WizardTypography.caption,
            color = WizardColors.textFaint,
        )

        WizardUnitToggle(
            options = WizardBodyFatSource.entries.toList(),
            selected = mode,
            label = {
                when (it) {
                    WizardBodyFatSource.VISUAL_ESTIMATE -> "Estimación visual"
                    WizardBodyFatSource.KNOWN_PERCENT -> "Conozco mi %"
                }
            },
            onSelected = { mode = it; confirmed = false },
        )

        if (mode == WizardBodyFatSource.KNOWN_PERCENT) {
            TextField(
                value = knownText,
                onValueChange = { knownText = it; confirmed = false },
                label = { Text("Mi porcentaje actual (%)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = WizardColors.cardFill,
                    unfocusedContainerColor = WizardColors.cardFill,
                    focusedTextColor = WizardColors.text,
                    unfocusedTextColor = WizardColors.text,
                    focusedLabelColor = WizardColors.textMuted,
                    unfocusedLabelColor = WizardColors.textMuted,
                    cursorColor = WizardColors.ruleCursor,
                    focusedIndicatorColor = WizardColors.selectedBorder,
                    unfocusedIndicatorColor = WizardColors.cardBorder,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = "Puede estar fuera del rango que muestran las ilustraciones (10–40 %).",
                style = WizardTypography.caption,
                color = WizardColors.textFaint,
            )
        }

        val valid = effectivePercent != null && effectivePercent.isFinite() && effectivePercent in 2.0..75.0
        val label = when {
            effectivePercent == null -> "Confirma tu estado actual"
            else -> "Usar ≈ ${effectivePercent.roundToInt()} % como estado actual"
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(WizardSpacing.touchTarget)
                .clip(WizardShapes.pill)
                .background(
                    when {
                        !valid -> WizardColors.ctaDisabled
                        confirmed -> WizardColors.cardFill
                        else -> WizardColors.cta
                    },
                )
                .border(
                    width = 1.dp,
                    color = if (confirmed) WizardColors.selectedBorder else Color.Transparent,
                    shape = WizardShapes.pill,
                )
                .clickable(enabled = valid) {
                    val percent = effectivePercent ?: return@clickable
                    onCandidateChange(
                        WizardBodyFatValue(
                            percent = percent,
                            source = mode,
                        ),
                    )
                    confirmed = true
                }
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (confirmed) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = WizardColors.ruleCursor,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Text(
                    text = if (confirmed) "Estado actual confirmado" else label,
                    style = WizardTypography.cardTitle,
                    color = when {
                        !valid -> WizardColors.ctaDisabledContent
                        confirmed -> WizardColors.text
                        else -> WizardColors.ctaContent
                    },
                )
            }
        }

        if (confirmed && candidate != null) {
            Text(
                text = "Se guarda como observación estimada de tu estado actual. No crea una meta de grasa ni cambia la ecuación.",
                style = WizardTypography.caption,
                color = WizardColors.textFaint,
            )
        }
    }
}
