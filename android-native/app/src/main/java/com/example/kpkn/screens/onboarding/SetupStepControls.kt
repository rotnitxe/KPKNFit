package com.example.kpkn.screens.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.onboarding.SetupStepDefinitions
import com.example.kpkn.domain.onboarding.SetupStepId
import com.example.kpkn.screens.onboarding.design.WizardChoiceCard
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardShapes
import com.example.kpkn.screens.onboarding.design.WizardSpacing
import com.example.kpkn.screens.onboarding.design.WizardTypography

/**
 * Controles compartidos de los pasos de Nutrición y RINGS.
 *
 * La cabecera (pregunta, subtítulo y CTA) la pinta la raíz del wizard desde el
 * catálogo `SetupStepDefinitions`; aquí solo vive el cuerpo del paso. Todas las
 * opciones salen del catálogo (valor estable + etiqueta) para que la UI nunca
 * invente copia ni valores que los motores no entienden.
 *
 * Máximo dos entradas relacionadas visibles por pantalla: las filas editoriales
 * (pesajes, contexto de historia) abren su propio diálogo.
 */

/** Valor estable → enum del motor; null cuando el valor no pertenece al catálogo (nunca un default). */
internal inline fun <reified T : Enum<T>> setupEnumValueOrNull(value: String): T? =
    enumValues<T>().firstOrNull { it.name.equals(value, ignoreCase = true) }

/**
 * Alterna una opción respetando [exclusive] (valores que no se pueden combinar).
 * Seleccionar un valor excluyente descarta el resto; marcar una opción normal
 * descarta los excluyentes ya elegidos; deseleccionar deja el conjunto vacío.
 *
 * Función pura (sin Compose ni ViewModel) vivida aquí para que la UI y el
 * ViewModel compartan el mismo cálculo sin mover el helper a `domain`: el
 * llamante debe pasar SIEMPRE el Set **último** (el VM lo lee dentro de su
 * mutex), nunca el que la tarjeta leyó al componer.
 */
internal fun setupToggleExclusive(
    current: Set<String>,
    value: String,
    exclusive: Set<String>,
): Set<String> {
    val toggled = if (value in current) current - value else current + value
    return if (value in exclusive) {
        toggled.filterTo(mutableSetOf()) { it == value }
    } else {
        toggled.filterTo(mutableSetOf()) { it !in exclusive }
    }
}

/**
 * Selección almacenada del paso. [fallback] cubre borradores migrados desde el
 * flujo legacy, donde la respuesta vive en el dominio y no en `stepSelections`.
 */
internal fun SetupWizardDraft.setupSelectionOf(step: SetupStepId, vararg fallback: String?): Set<String> {
    val stored = selectedValues(step)
    if (stored.isNotEmpty()) return stored
    return fallback.filterNotNull().toSet()
}

/**
 * Tarjetas de selección única del catálogo.
 *
 * Extiende `SetupFormChoiceCards` (control compartido) con subtítulo por
 * tarjeta: varios pasos de nutrición/rings necesitan explicar qué significa
 * cada opción («Automático», «Objetivos propios», «Solo registro») y ese
 * contexto no vive en el catálogo.
 */
@Composable
internal fun SetupBodyChoiceCards(
    step: SetupStepId,
    selected: Set<String>,
    onSelect: (String) -> Unit,
    subtitles: Map<String, String> = emptyMap(),
) {
    val options = SetupStepDefinitions.options(step)
    if (options.isEmpty()) return
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(WizardSpacing.cardGap),
    ) {
        options.forEach { option ->
            WizardChoiceCard(
                title = option.label,
                subtitle = subtitles[option.value],
                selected = option.value in selected,
                onClick = { onSelect(option.value) },
            )
        }
    }
}

/**
 * Tarjetas de selección múltiple con marca de check y valores excluyentes del
 * catálogo.
 *
 * El click emite **solo el valor estable de la tarjeta**: quién alterna es el
 * ViewModel (`toggleStepChoice`), que calcula el conjunto resultante sobre el
 * último borrador dentro de su mutex. La tarjeta nunca deriva el nuevo Set a
 * partir de `selected` (el eco leído al componer), porque dos toques antes del
 * primer persisto compartirían ese Set viejo y perderían el primero.
 */
@Composable
internal fun SetupBodyMultiChoiceCards(
    step: SetupStepId,
    selected: Set<String>,
    onSelect: (String) -> Unit,
    subtitles: Map<String, String> = emptyMap(),
) {
    val definition = SetupStepDefinitions.of(step) ?: return
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(WizardSpacing.cardGap),
    ) {
        definition.options.forEach { option ->
            SetupCheckCard(
                title = option.label,
                subtitle = subtitles[option.value],
                checked = option.value in selected,
                onClick = { onSelect(option.value) },
            )
        }
    }
}

/** Tarjeta de casilla: mismo lenguaje visual que [WizardChoiceCard] con check y `Role.Checkbox`. */
@Composable
private fun SetupCheckCard(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onClick: () -> Unit,
) {
    val alpha = 1f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = WizardSpacing.touchTarget + 16.dp)
            .background(WizardColors.cardFill, WizardShapes.card)
            .border(
                width = if (checked) WizardColors.selectedBorderWidth else WizardColors.unselectedBorderWidth,
                color = if (checked) WizardColors.selectedBorder else WizardColors.cardBorder,
                shape = WizardShapes.card,
            )
            .semantics {
                role = Role.Checkbox
                this.selected = checked
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = title,
                style = WizardTypography.cardTitle,
                color = WizardColors.text.copy(alpha = alpha),
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = WizardTypography.cardSubtitle,
                    color = WizardColors.textMuted.copy(alpha = alpha),
                )
            }
        }
        SetupCheckMark(checked = checked)
    }
}

@Composable
private fun SetupCheckMark(checked: Boolean) {
    Box(
        modifier = Modifier.size(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .background(WizardColors.markFill, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = WizardColors.markDot,
                    modifier = Modifier.size(14.dp),
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .border(1.5.dp, WizardColors.markBorder, CircleShape),
            )
        }
    }
}

/**
 * Campo de texto corto controlado por el paso. Delega en el control compartido
 * `SetupFormTextField` (mismos colores, sin estilos nuevos) y solo añade el
 * texto de apoyo. [initial] siembra el estado una sola vez: mientras el usuario
 * escribe no se re-bloquea el cursor aunque el número almacenado aún no sea
 * válido (un `70.` a medias, por ejemplo).
 */
@Composable
internal fun SetupBodyTextField(
    label: String,
    initial: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
    helper: String? = null,
) {
    var text by remember { mutableStateOf(initial) }
    Column(modifier = Modifier.fillMaxWidth()) {
        SetupFormTextField(
            value = text,
            onValueChange = { value ->
                text = value
                onValueChange(value)
            },
            label = label,
            keyboardType = keyboardType,
        )
        if (helper != null) {
            SetupFormCaption(text = helper, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/**
 * Colores del campo del wizard (también los usa el editor de inventario).
 * Misma paleta existente: sin gamas nuevas.
 */
@Composable
internal fun setupBodyFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = WizardColors.cardFill,
    unfocusedContainerColor = WizardColors.cardFill,
    focusedTextColor = WizardColors.text,
    unfocusedTextColor = WizardColors.text,
    focusedLabelColor = WizardColors.textMuted,
    unfocusedLabelColor = WizardColors.textMuted,
    cursorColor = WizardColors.ruleCursor,
    focusedBorderColor = WizardColors.selectedBorder,
    unfocusedBorderColor = WizardColors.cardBorder,
)

/**
 * Acción «omitir» para los pasos con `allowSkip`. Solo se muestra cuando el
 * catálogo lo permite y todavía no hay valor: omitir nunca fabrica un dato ni
 * borra una respuesta ya declarada.
 */
@Composable
internal fun SetupBodySkipAction(
    step: SetupStepId,
    vm: SetupWizardViewModel,
    label: String = "Omitir por ahora",
    visible: Boolean = true,
) {
    if (!visible) return
    if (SetupStepDefinitions.of(step)?.allowSkip != true) return
    TextButton(
        onClick = { vm.skipStep(step) },
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = WizardSpacing.touchTarget),
    ) {
        Text(
            text = label,
            style = WizardTypography.cardSubtitle,
            color = WizardColors.textMuted,
        )
    }
}

/** Nota breve bajo un control (contexto honesto, no instrucciones de UI). */
@Composable
internal fun SetupBodyHint(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = WizardTypography.bodySmall,
        color = WizardColors.textMuted,
        modifier = modifier.fillMaxWidth(),
    )
}

/** Texto tenue de apoyo (cifras, unidades, procedencia): mismo control que el resto del wizard. */
@Composable
internal fun SetupBodyCaption(text: String, modifier: Modifier = Modifier) =
    SetupFormCaption(text = text, modifier = modifier)

/** Panel informativo con el mismo lenguaje de tarjeta del wizard. */
@Composable
internal fun SetupBodyInfoPanel(title: String, body: String, modifier: Modifier = Modifier) {
    Surface(
        color = WizardColors.cardFill,
        shape = WizardShapes.card,
        border = BorderStroke(WizardColors.unselectedBorderWidth, WizardColors.cardBorder),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(title, style = WizardTypography.cardTitle, color = WizardColors.text)
            Text(body, style = WizardTypography.cardSubtitle, color = WizardColors.textMuted)
        }
    }
}

/** Acción «Editar …» de los pasos de resultado: vuelve al paso sin borrar respuestas. */
@Composable
internal fun SetupBodyEditAction(label: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.defaultMinSize(minHeight = WizardSpacing.touchTarget),
    ) {
        Text(text = label, style = WizardTypography.cardSubtitle, color = WizardColors.text)
    }
}
