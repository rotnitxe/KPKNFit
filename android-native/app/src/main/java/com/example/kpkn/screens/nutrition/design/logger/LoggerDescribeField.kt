package com.example.kpkn.screens.nutrition.design.logger

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknIconButton
import com.example.kpkn.ui.design.KpknTextField
import com.example.kpkn.ui.design.KpknType

/**
 * Campo «¿Qué comiste?» de la pestaña Describir: texto libre de tres líneas como mínimo, con el marcador de ejemplo y una ayuda en
 * `caption`. Al pulsar «Hecho» del teclado se interpreta ([onImeDone]). El botón de información abre la explicación del registro
 * ([onPrivacyInfo]).
 */
@Composable
fun LoggerDescribeField(
    text: String,
    onTextChange: (String) -> Unit,
    onImeDone: () -> Unit,
    onPrivacyInfo: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        KpknTextField(
            value = text,
            onValueChange = onTextChange,
            label = "¿Qué comiste?",
            placeholder = "Dos huevos con tostadas y un café",
            singleLine = false,
            minLines = 3,
            enabled = enabled,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onImeDone() }),
        )
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Escribe como hablas: cantidades, marcas y cómo lo cocinaste",
                style = KpknType.caption,
                color = WizardColors.textMuted,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp),
            )
            KpknIconButton(
                icon = KpknLineIcons.Info,
                contentDescription = "Cómo funciona el registro de comidas",
                onClick = onPrivacyInfo,
            )
        }
    }
}
