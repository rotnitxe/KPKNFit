package com.example.kpkn.screens.nutrition.design.logger

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.KpknAlertConfirmButton
import com.example.kpkn.ui.components.KpknAlertDismissButton
import com.example.kpkn.ui.components.KpknGlassDialog
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknIconButton
import com.example.kpkn.ui.design.KpknMetric
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknType
import kotlin.math.ceil
import kotlin.math.floor

/** Un utensilio configurable: su clave, su nombre, su rango en ml y el paso con el que suben y bajan sus botones. */
internal data class LoggerMeasureSpec(
    val key: String,
    val label: String,
    val min: Float,
    val max: Float,
    val step: Float,
)

/** Las nueve medidas de casa, con su rango y su paso (los mismos rangos que tenía el deslizador). */
internal val LoggerMeasureSpecs = listOf(
    LoggerMeasureSpec("cucharadita", "Cucharadita", 3f, 10f, 1f),
    LoggerMeasureSpec("cucharada", "Cucharada", 10f, 25f, 1f),
    LoggerMeasureSpec("cucharon", "Cucharón", 60f, 150f, 5f),
    LoggerMeasureSpec("taza", "Taza", 150f, 400f, 10f),
    LoggerMeasureSpec("vaso", "Vaso", 150f, 400f, 10f),
    LoggerMeasureSpec("plato", "Plato", 150f, 400f, 10f),
    LoggerMeasureSpec("plato_hondo", "Plato hondo", 250f, 600f, 10f),
    LoggerMeasureSpec("bol", "Bol", 200f, 500f, 10f),
    LoggerMeasureSpec("copa", "Copa", 100f, 300f, 5f),
)

/**
 * Medidas de casa (N3b): una fila por utensilio con su volumen en ml y los botones ± de su paso. «Restablecer» vuelve a los valores de
 * fábrica, «Cancelar» cierra sin guardar y «Guardar» escribe lo que cambió. Los valores llegan con [values] y cada cambio sale por
 * [onValueChange] con la clave del utensilio.
 */
@Composable
fun LoggerMeasuresDialog(
    values: Map<String, Float>,
    onValueChange: (String, Float) -> Unit,
    onRestore: () -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    KpknGlassDialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = "Medidas de casa", style = KpknType.title, color = WizardColors.text)
            Text(
                text = "Cuánto cabe en cada utensilio de tu casa. Se usa al escribir «una taza» o «un plato».",
                style = KpknType.caption,
                color = WizardColors.textMuted,
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 484.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                LoggerMeasureSpecs.forEachIndexed { index, spec ->
                    if (index > 0) LoggerFoodDivider()
                    LoggerMeasureRow(
                        spec = spec,
                        current = values[spec.key] ?: spec.min,
                        onStep = { up -> onValueChange(spec.key, stepped(values[spec.key] ?: spec.min, spec, up)) },
                    )
                }
            }
            // «Restablecer» va aparte: con «Cancelar» y «Guardar» en la misma fila, «Guardar» se partía en dos líneas.
            KpknTextAction(text = "Restablecer", onClick = onRestore)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
            ) {
                KpknAlertDismissButton(text = "Cancelar", onClick = onDismiss)
                Spacer(Modifier.width(8.dp))
                KpknAlertConfirmButton(text = "Guardar", onClick = onSave)
            }
        }
    }
}

/** Una medida: el nombre, el valor en `bodyStrong` tabular con su unidad y los botones de menos y más. */
@Composable
private fun LoggerMeasureRow(
    spec: LoggerMeasureSpec,
    current: Float,
    onStep: (Boolean) -> Unit,
) {
    val shown = current.coerceIn(spec.min, spec.max)
    val name = spec.label.lowercase()
    Row(
        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 52.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = spec.label,
            style = KpknType.bodyStrong,
            color = WizardColors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        KpknMetric(
            value = kotlin.math.round(shown).toInt().toString(),
            unit = "ml",
            style = KpknType.bodyStrong.copy(fontFeatureSettings = "'tnum' 1"),
        )
        Spacer(Modifier.width(8.dp))
        KpknIconButton(
            icon = KpknLineIcons.Minus,
            contentDescription = "Menos $name",
            onClick = { onStep(false) },
        )
        KpknIconButton(
            icon = KpknLineIcons.Plus,
            contentDescription = "Más $name",
            onClick = { onStep(true) },
        )
    }
}

/**
 * Valor de un utensilio tras un paso: el siguiente múltiplo del paso hacia arriba o hacia abajo, dentro del rango. Así, un valor que no
 * cae en el paso (153 ml) baja a 150 y sube a 160, sin saltarse ninguno.
 */
internal fun stepped(current: Float, spec: LoggerMeasureSpec, up: Boolean): Float {
    val next = if (up) (floor(current / spec.step) + 1f) * spec.step else (ceil(current / spec.step) - 1f) * spec.step
    return next.coerceIn(spec.min, spec.max)
}
