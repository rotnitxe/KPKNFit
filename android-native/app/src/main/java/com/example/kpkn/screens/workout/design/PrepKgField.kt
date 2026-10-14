package com.example.kpkn.screens.workout.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.design.KpknShape
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknType

/**
 * Peso de una aproximación (KPKN LS5b): una cifra editable en `metricM` tabular sobre `inset`, con «kg» en `caption`, todo de 48 dp de
 * alto. Con [suggested], la cifra va en `textMuted`: es una sugerencia, no un dato escrito. Sin estado: [value] y [onValueChange] vienen
 * de quien la monta.
 */
@Composable
fun PrepKgField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    suggested: Boolean = false,
) {
    val tone = if (suggested) WizardColors.textMuted else WizardColors.text
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .clip(KpknShape.pill)
            .background(KpknSurface.inset, KpknShape.pill)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = KpknType.metricM.copy(color = tone, textAlign = TextAlign.End),
            cursorBrush = SolidColor(WizardColors.text),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            singleLine = true,
            modifier = Modifier
                .widthIn(min = 36.dp, max = 48.dp)
                .semantics { contentDescription = "Peso en kilos" },
        )
        Spacer(Modifier.width(3.dp))
        Text(text = "kg", style = KpknType.caption, color = WizardColors.textMuted)
    }
}
