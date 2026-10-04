package com.example.kpkn.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardShapes
import com.example.kpkn.screens.onboarding.design.WizardTypography

/**
 * Controles compartidos del bloque Entreno (avisos, carga, campo numérico y
 * fila de resumen). Vivían junto a las pantallas del inventario con pesos, que
 * el asistente ya no pregunta (D2.5); `SetupTrainingSteps.kt` los reutiliza sin
 * duplicar estilos.
 */

/** Tono de los avisos en línea del contenido (informativo o bloqueante). */
internal enum class TrainingNoticeTone { INFO, ERROR }

/** Formatea una carga real sin artefactos: 40.0 → "40", 12.5 → "12.5". */
internal fun formatTrainingNumber(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()

/** Campo numérico/texto de un solo línea con teclado localizado. */
@Composable
internal fun TrainingNumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Decimal,
    isError: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        label = { Text(label) },
        isError = isError,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = setupBodyFieldColors(),
        modifier = modifier.fillMaxWidth(),
    )
}

/**
 * Abre un concepto de «Conceptos clave» desde las hojas «Cómo funciona» del asistente (tarjetas de plan y
 * revisión, C.P6). Lo provee `SetupWizardScreen` con el callback que le pasa la navegación; sin proveedor (las
 * pruebas y las vistas previas) vale null y el glosario de la hoja no ofrece el enlace.
 */
internal val LocalOpenConcept = compositionLocalOf<((String) -> Unit)?> { null }

/** Una etiqueta más larga que esto no cabe junto al texto en una fila: el aviso apila el texto y los botones. */
private const val NOTICE_INLINE_LABEL_MAX = 18

/**
 * Aviso en línea del contenido: estado de carga, error con reintento o restricción.
 *
 * Lleva hasta dos acciones (paso A.C4/C.P11: «Sí, tengo rack y banco» y «Confirmar material» conviven). Con una sola
 * etiqueta corta el aviso conserva su fila de siempre (texto a la izquierda, botón a la derecha); con dos acciones o
 * una etiqueta larga el texto ocupa todo el ancho y los botones van debajo, para que ninguno aplaste el mensaje.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TrainingNotice(
    text: String,
    tone: TrainingNoticeTone = TrainingNoticeTone.INFO,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
) {
    val hasPrimary = actionLabel != null && onAction != null
    val hasSecondary = hasPrimary && secondaryActionLabel != null && onSecondaryAction != null
    val stacked = hasSecondary || (actionLabel?.length ?: 0) > NOTICE_INLINE_LABEL_MAX
    val textColor = if (tone == TrainingNoticeTone.ERROR) WizardColors.danger else WizardColors.textMuted
    val frame = Modifier
        .fillMaxWidth()
        .background(WizardColors.cardFill, WizardShapes.card)
        .border(
            width = WizardColors.unselectedBorderWidth,
            color = if (tone == TrainingNoticeTone.ERROR) WizardColors.danger else WizardColors.cardBorder,
            shape = WizardShapes.card,
        )
        .padding(horizontal = 16.dp, vertical = 12.dp)

    if (!stacked) {
        Row(modifier = frame, verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = text,
                style = WizardTypography.bodySmall,
                color = textColor,
                modifier = Modifier.weight(1f),
            )
            if (hasPrimary) {
                TextButton(onClick = requireNotNull(onAction), modifier = Modifier.testTag("training-notice-action")) {
                    Text(requireNotNull(actionLabel), color = WizardColors.text)
                }
            }
        }
        return
    }

    Column(modifier = frame, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = text, style = WizardTypography.bodySmall, color = textColor, modifier = Modifier.fillMaxWidth())
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        ) {
            if (hasPrimary) {
                TextButton(onClick = requireNotNull(onAction), modifier = Modifier.testTag("training-notice-action")) {
                    Text(requireNotNull(actionLabel), color = WizardColors.text)
                }
            }
            if (hasSecondary) {
                TextButton(
                    onClick = requireNotNull(onSecondaryAction),
                    modifier = Modifier.testTag("training-notice-secondary-action"),
                ) {
                    Text(requireNotNull(secondaryActionLabel), color = WizardColors.textMuted)
                }
            }
        }
    }
}

/** Carga en curso del contenido: solo visible mientras la bandera real está activa. */
@Composable
internal fun TrainingLoading(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(WizardColors.cardFill, WizardShapes.card)
            .border(WizardColors.unselectedBorderWidth, WizardColors.cardBorder, WizardShapes.card)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            color = WizardColors.text,
            strokeWidth = 2.dp,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(text, style = WizardTypography.bodySmall, color = WizardColors.textMuted)
    }
}

/** Fila de resumen no interactiva (revisión e hito). */
@Composable
internal fun TrainingSummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(WizardColors.cardFill, WizardShapes.card)
            .border(WizardColors.unselectedBorderWidth, WizardColors.cardBorder, WizardShapes.card)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = WizardTypography.cardSubtitle, color = WizardColors.textMuted, modifier = Modifier.weight(1f))
        Text(value, style = WizardTypography.cardTitle, color = WizardColors.text)
    }
}
