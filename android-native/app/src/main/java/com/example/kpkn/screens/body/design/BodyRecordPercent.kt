package com.example.kpkn.screens.body.design

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardBodyFatRuler
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.formatDecimalEs
import com.example.kpkn.ui.design.KpknMetric
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknType
import java.util.Locale

/** Alto de la regla vertical, como en el registro del wizard. */
private val PercentRulerHeight = 220.dp

/** Punto de partida de cada regla cuando el usuario la abre: el mismo del wizard (20 % de grasa, 40 % de músculo). */
internal const val BODY_RECORD_FAT_START_PERCENT = 20.0
internal const val BODY_RECORD_MUSCLE_START_PERCENT = 40.0

/**
 * Columna de un porcentaje opcional abierto (grasa o músculo), como en el registro del wizard: el nombre en eyebrow («GRASA»,
 * «MÚSCULO»), la cifra con su % en la misma línea y la regla vertical; «Quitar» la vacía. La abre un chip de la hoja. La regla arranca en
 * [startPercent] sin declarar: solo cuenta cuando el usuario la toca, así que un porcentaje no aparece por abrir la columna.
 *
 * @param accessibilityLabel nombre de la regla para TalkBack: «Porcentaje de grasa corporal» o «Porcentaje de músculo».
 */
@Composable
fun BodyRecordPercentColumn(
    name: String,
    percent: Double?,
    startPercent: Double,
    range: IntRange,
    accessibilityLabel: String,
    onRemove: () -> Unit,
    onPercentChange: (Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = name.uppercase(Locale.forLanguageTag("es")),
            style = KpknType.eyebrow,
            color = WizardColors.textMuted,
            maxLines = 1,
        )
        KpknMetric(
            value = percent?.let { formatDecimalEs(it, 1) } ?: "—",
            unit = if (percent != null) "%" else null,
            style = KpknType.metricM,
            color = if (percent != null) WizardColors.text else WizardColors.textFaint,
        )
        Spacer(Modifier.height(8.dp))
        WizardBodyFatRuler(
            percent = percent ?: startPercent,
            declared = percent != null,
            onPercentChange = { onPercentChange(it.toDouble()) },
            modifier = Modifier
                .fillMaxWidth()
                .height(PercentRulerHeight)
                // nc-X05: mask-image linear-gradient(90deg, transparent, rgb(0, 0, 0) 22%, rgb(0, 0, 0) 78%, transparent)
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(
                        brush = Brush.horizontalGradient(
                            0f to Color.Transparent,
                            0.22f to Color.Black,
                            0.78f to Color.Black,
                            1f to Color.Transparent,
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                },
            range = range,
            accessibilityLabel = accessibilityLabel,
        )
        KpknTextAction(text = "Quitar", onClick = onRemove)
    }
}
