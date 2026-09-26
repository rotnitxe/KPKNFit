package com.example.kpkn.screens.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.onboarding.SetupOptionDefinition
import com.example.kpkn.screens.onboarding.design.WizardChoiceCard
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardSpacing
import com.example.kpkn.screens.onboarding.design.WizardTypography

/**
 * Controles tipados compartidos por los pasos del wizard.
 *
 * Todo stateless: el control recibe valor + callbacks tipados y nunca conoce el
 * ViewModel, la ruta ni la validación. El bloque básico, el repaso final y los
 * bloques de entreno/nutrición/rings pueden reutilizarlos (no se declaran
 * controles equivalentes en otros archivos para evitar duplicados en el mismo
 * paquete).
 *
 * Sin estilos alternos: los campos usan la paleta existente de `design/`
 * (`WizardColors.background` `#1F1F1F`, tarjeta `cardFill`, borde blanco de
 * selección y CTA blanco). No se introduce ninguna gama de color nueva.
 */

/** Campo de texto corto del wizard, con el marco neutro existente. */
@Composable
fun SetupFormTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    testTag: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = setupFormTextFieldColors(),
        modifier = modifier
            .fillMaxWidth()
            .let { base -> if (testTag == null) base else base.testTag(testTag) },
    )
}

@Composable
private fun setupFormTextFieldColors() = OutlinedTextFieldDefaults.colors(
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
 * Tarjetas de una opción cerrada a partir del catálogo
 * `SetupStepDefinitions`: valor estable + etiqueta. La selección se lee fuera
 * (nunca se fabrica una respuesta dentro del control).
 */
@Composable
fun SetupFormChoiceCards(
    options: List<SetupOptionDefinition>,
    isSelected: (String) -> Boolean,
    onOptionClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(WizardSpacing.cardGap),
    ) {
        options.forEach { option ->
            WizardChoiceCard(
                title = option.label,
                selected = isSelected(option.value),
                onClick = { onOptionClick(option.value) },
            )
        }
    }
}

/**
 * Acción «Omitir este paso». Solo se renderiza cuando la definición del paso
 * declara `allowSkip`; el llamado decide, este control nunca oculta ni inventa
 * un salto. `skipStep` no avanza: la continuación sigue siendo el CTA del Host.
 */
@Composable
fun SetupFormSkipAction(
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TextButton(
        onClick = onSkip,
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(text = "Omitir este paso", color = WizardColors.textMuted)
    }
}

/** Nota breve bajo un control (información real, no instrucción de uso). */
@Composable
fun SetupFormCaption(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = WizardTypography.caption,
        color = WizardColors.textFaint,
        modifier = modifier.fillMaxWidth(),
    )
}
