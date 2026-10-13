package com.example.kpkn.screens.body.design

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardBodyFatRuler
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.formatDecimalEs
import com.example.kpkn.ui.components.icons.KpknLineIcons
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
 * Columna de un porcentaje opcional (grasa o músculo), como en el registro del wizard: cerrada muestra la acción «Añadir»; abierta,
 * el nombre, la cifra y la regla vertical, y «Quitar» la vacía. La regla arranca en [startPercent] sin declarar: solo cuenta cuando el
 * usuario la toca, así que un porcentaje no aparece por abrir la columna.
 */
@Composable
fun BodyRecordPercentColumn(
    name: String,
    addLabel: String,
    open: Boolean,
    percent: Double?,
    startPercent: Double,
    range: IntRange,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
    onPercentChange: (Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        if (open) {
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
                modifier = Modifier.fillMaxWidth().height(PercentRulerHeight),
                range = range,
            )
            KpknTextAction(text = "Quitar", onClick = onRemove)
        } else {
            BodyRecordAddPercent(label = addLabel, onClick = onOpen)
        }
    }
}

/** Acción «Añadir % de …»: hasta dos líneas, con zona táctil de 48 dp, para que no se corte al 130 % de letra. */
@Composable
private fun BodyRecordAddPercent(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = KpknLineIcons.Plus,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = WizardColors.text,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            style = KpknType.action,
            color = WizardColors.text,
            maxLines = 2,
        )
    }
}
