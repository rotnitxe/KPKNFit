package com.example.kpkn.screens.nutrition.design.logger

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.design.KpknShape
import com.example.kpkn.ui.design.KpknSurface
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/** Un utensilio configurable: su clave, su nombre, su rango en ml y el paso con el que sube y baja. */
internal data class LoggerMeasureSpec(
    val key: String,
    val label: String,
    val min: Float,
    val max: Float,
    val step: Float,
)

/** Las nueve medidas de casa en el orden del lienzo (S-F10), con su rango; los platos siguen en ml (S-D4). */
internal val LoggerMeasureSpecs = listOf(
    LoggerMeasureSpec("taza", "Taza", 150f, 400f, 10f),
    LoggerMeasureSpec("vaso", "Vaso", 150f, 400f, 10f),
    LoggerMeasureSpec("plato", "Plato", 150f, 400f, 10f),
    LoggerMeasureSpec("plato_hondo", "Plato hondo", 250f, 600f, 10f),
    LoggerMeasureSpec("bol", "Bol", 200f, 500f, 10f),
    LoggerMeasureSpec("cucharada", "Cucharada", 10f, 25f, 1f),
    LoggerMeasureSpec("cucharadita", "Cucharadita", 3f, 10f, 1f),
    LoggerMeasureSpec("cucharon", "Cucharón", 60f, 150f, 5f),
    LoggerMeasureSpec("copa", "Copa", 100f, 300f, 5f),
)

/**
 * Mis medidas de casa (lienzo s-measures): una fila de 50 dp por utensilio con su nombre y, a la derecha, un chip con la cantidad
 * («240 ml»). Tocar el chip deja escribir la cantidad; lo escrito entra al instante y se queda dentro del rango del utensilio. «Restablecer» vuelve a los
 * valores de fábrica y «Guardar» escribe lo que cambió. Los valores llegan con [values] y cada cambio sale por [onValueChange] con la
 * clave del utensilio.
 */
@Composable
fun LoggerMeasuresSheet(
    values: Map<String, Float>,
    onValueChange: (String, Float) -> Unit,
    onRestore: () -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    var editingKey by remember { mutableStateOf<String?>(null) }
    KpknSheet(onDismissRequest = onDismiss, backdropScrim = Color.Black.copy(alpha = 0.67f)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(start = 18.dp, end = 18.dp, bottom = 30.dp),
        ) {
            LoggerSheetTitle(
                title = "Mis medidas de casa",
                sub = "Cuando escribas «una taza» o «un plato», usaremos estas cantidades.",
            )
            LoggerMeasureSpecs.forEachIndexed { index, spec ->
                val current = (values[spec.key] ?: spec.min).coerceIn(spec.min, spec.max).roundToInt()
                MeasureRow(
                    spec = spec,
                    current = current,
                    editing = editingKey == spec.key,
                    divider = index > 0,
                    onStartEdit = { editingKey = spec.key },
                    onType = { text ->
                        val typed = text.replace(',', '.').trim().toFloatOrNull()
                        if (typed != null) onValueChange(spec.key, typed.roundToInt().toFloat().coerceIn(spec.min, spec.max))
                    },
                    onFinish = { if (editingKey == spec.key) editingKey = null },
                )
            }
            Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LoggerPill(text = "Restablecer", onClick = onRestore, tone = LoggerPillTone.SECONDARY, small = true, modifier = Modifier.weight(1f))
                LoggerPill(text = "Guardar", onClick = onSave, small = true, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** `.rowx`: el nombre (peso 500) y el chip de 34 dp con la cantidad en cifras tabulares. */
@Composable
private fun MeasureRow(
    spec: LoggerMeasureSpec,
    current: Int,
    editing: Boolean,
    divider: Boolean,
    onStartEdit: () -> Unit,
    onType: (String) -> Unit,
    onFinish: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (divider) LoggerRowDivider()
        Row(
            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 50.dp).padding(vertical = 1.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = spec.label,
                style = LoggerText.rowTitle.copy(fontWeight = FontWeight.Medium),
                color = LoggerColors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            MeasureChip(spec = spec, current = current, editing = editing, onStartEdit = onStartEdit, onType = onType, onFinish = onFinish)
        }
    }
}

@Composable
private fun MeasureChip(
    spec: LoggerMeasureSpec,
    current: Int,
    editing: Boolean,
    onStartEdit: () -> Unit,
    onType: (String) -> Unit,
    onFinish: () -> Unit,
) {
    val text = LoggerText.chip.copy(fontFeatureSettings = "'tnum' 1")
    Box(
        modifier = Modifier
            .defaultMinSize(minHeight = 48.dp)
            .then(
                if (editing) Modifier
                else Modifier
                    .clickable(role = Role.Button, onClickLabel = "Escribir la cantidad", onClick = onStartEdit)
                    .semantics { contentDescription = "${spec.label}: $current ml" },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .height(34.dp)
                .clip(KpknShape.pill)
                .background(if (editing) Color.White.copy(alpha = 0.10f) else KpknSurface.inset, KpknShape.pill)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (!editing) {
                Text(text = "$current ml", style = text, color = LoggerColors.muted, maxLines = 1)
            } else {
                val focus = remember { FocusRequester() }
                val initial = current.toString()
                var field by remember { mutableStateOf(TextFieldValue(initial, TextRange(0, initial.length))) }
                var hadFocus by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { focus.requestFocus() }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BasicTextField(
                        value = field,
                        onValueChange = {
                            field = it
                            onType(it.text)
                        },
                        modifier = Modifier
                            .width(IntrinsicSize.Min)
                            .widthIn(min = 24.dp)
                            .focusRequester(focus)
                            .onFocusChanged { if (it.isFocused) hadFocus = true else if (hadFocus) onFinish() }
                            .semantics { contentDescription = "${spec.label}, en mililitros" },
                        singleLine = true,
                        textStyle = text.copy(color = LoggerColors.ink),
                        cursorBrush = SolidColor(LoggerColors.ink),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { onFinish() }),
                    )
                    Text(text = " ml", style = text, color = LoggerColors.muted, maxLines = 1)
                }
            }
        }
    }
}

/**
 * Valor de un utensilio tras un paso: el siguiente múltiplo del paso hacia arriba o hacia abajo, dentro del rango. Así, un valor que no
 * cae en el paso (153 ml) baja a 150 y sube a 160, sin saltarse ninguno. Ya no lo usan los botones (el lienzo escribe la cantidad); se
 * conserva con su prueba.
 */
internal fun stepped(current: Float, spec: LoggerMeasureSpec, up: Boolean): Float {
    val next = if (up) (floor(current / spec.step) + 1f) * spec.step else (ceil(current / spec.step) - 1f) * spec.step
    return next.coerceIn(spec.min, spec.max)
}
