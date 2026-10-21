package com.example.kpkn.screens.nutrition.design.editor

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.design.KpknTextField
import com.example.kpkn.ui.design.KpknType

/** Cifras tabulares: las columnas de números no bailan al cambiar de valor. */
private const val TabularNumbers = "'tnum' 1"

/**
 * Escala de letra a partir de la cual el editor cambia de disposición: los datos de cuerpo pasan a dos filas y la semana, a dos filas
 * de radio. Al 130 % de letra no caben tres columnas ni los dos segmentos sin cortar la etiqueta.
 */
internal const val EditorLargeFontScale = 1.25f

/** Texto secundario del editor: `caption` en `textMuted`; con [numeric], cifras tabulares. */
@Composable
internal fun EditorCaption(text: String, modifier: Modifier = Modifier, numeric: Boolean = false) {
    Text(
        text = text,
        style = if (numeric) KpknType.caption.copy(fontFeatureSettings = TabularNumbers) else KpknType.caption,
        color = WizardColors.textMuted,
        modifier = modifier,
    )
}

/** Unidad junto a un campo («kcal», «%», «años»): `unit` en `textMuted`. */
@Composable
internal fun EditorUnit(text: String) {
    Text(text = text, style = KpknType.unit, color = WizardColors.textMuted, maxLines = 1)
}

/**
 * Campo numérico del editor. Lo que se escribe es lo que se lee: sin separadores de miles, para que volver a escribir una cifra no la
 * rompa. [label] va encima; [unit], a la derecha dentro del campo.
 */
@Composable
internal fun EditorNumberField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    unit: String? = null,
    placeholder: String? = null,
    keyboard: KeyboardType = KeyboardType.Decimal,
) {
    KpknTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = label,
        placeholder = placeholder,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        trailing = if (unit == null) null else ({ EditorUnit(unit) }),
    )
}
