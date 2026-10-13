package com.example.kpkn.screens.body.design

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.BodyMetric
import com.example.kpkn.domain.body.validateBodyValue
import com.example.kpkn.domain.nutrition.parseLocalizedNumber
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknTextField
import com.example.kpkn.ui.design.KpknType

/** Nombre de una medida de la hoja, como se lee en su campo. */
internal fun bodyMeasureLabel(metric: BodyMetric): String = when (metric) {
    BodyMetric.WAIST -> "Cintura"
    BodyMetric.HIP -> "Cadera"
    BodyMetric.CHEST -> "Pecho"
    BodyMetric.ARM -> "Brazo"
    BodyMetric.THIGH -> "Muslo"
    BodyMetric.NECK -> "Cuello"
    else -> metric.name
}

/**
 * Error de un campo de medida, o null si está bien. Vacío es válido (la medida es opcional). Usa el mismo rango que el dominio.
 */
internal fun bodyMeasureError(metric: BodyMetric, text: String): String? {
    if (text.isBlank()) return null
    val value = parseLocalizedNumber(text) ?: return "Escribe un número"
    return if (validateBodyValue(metric, value).valid) null else "Fuera de rango"
}

/** Medida con su valor si el texto es válido; null si está vacía o no pasa la validación. */
internal fun bodyMeasureValue(metric: BodyMetric, text: String): Double? =
    if (text.isBlank() || bodyMeasureError(metric, text) != null) null else parseLocalizedNumber(text)

/**
 * Cuadrícula de las medidas en centímetros, dos por fila, con su texto tal como se escribe ([texts]) y su error ([errors]). Sin
 * estado: quien la usa guarda el texto y recibe cada cambio en [onTextChange].
 */
@Composable
fun BodyRecordMeasureGrid(
    texts: Map<BodyMetric, String>,
    errors: Map<BodyMetric, String?>,
    onTextChange: (BodyMetric, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        BODY_RECORD_MEASURES.chunked(2).forEach { pair ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { metric ->
                    KpknTextField(
                        value = texts[metric].orEmpty(),
                        onValueChange = { onTextChange(metric, it) },
                        label = "${bodyMeasureLabel(metric)} (cm)",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        error = errors[metric],
                        modifier = Modifier.weight(1f),
                    )
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/**
 * Fila plegable «Medidas» con su pista («Opcional») y una flecha que gira. Zona táctil de 48 dp, `Role.Button` y el estado
 * «Desplegado» o «Plegado» para TalkBack.
 */
@Composable
fun BodyRecordDisclosure(
    label: String,
    hint: String,
    open: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .semantics { stateDescription = if (open) "Desplegado" else "Plegado" }
            .clickable(role = Role.Button, onClick = onToggle),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = KpknType.bodyStrong, color = WizardColors.text)
        Spacer(Modifier.width(8.dp))
        Text(text = hint, style = KpknType.caption, color = WizardColors.textMuted, modifier = Modifier.weight(1f))
        Icon(
            imageVector = KpknLineIcons.ChevronDown,
            contentDescription = null,
            modifier = Modifier.size(20.dp).rotate(if (open) 180f else 0f),
            tint = WizardColors.textMuted,
        )
    }
}
