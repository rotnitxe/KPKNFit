package com.example.kpkn.screens.sessioneditor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable

/**
 * Texto de un campo numérico frente al valor del modelo (SE11). Mientras la persona escribe manda su texto («102,» o «102,5»): del
 * modelo solo se copia cuando el número que representa el texto no es el del modelo (un cambio externo). Función pura.
 */
internal fun syncEditableNumber(text: String, modelValue: Double?): String {
    val typed = text.safeDoubleOrNull()
    val sameNumber = if (typed == null || modelValue == null) {
        typed == null && modelValue == null
    } else {
        kotlin.math.abs(typed - modelValue) < 1e-9
    }
    return if (sameNumber) text else formatEditableNumber(modelValue)
}

/**
 * Texto de un campo numérico. La clave es la identidad de quien lo edita, nunca el valor del modelo: así escribir no reinicia el
 * campo ni borra la coma. El valor del modelo solo corrige el texto cuando cambia por fuera del campo.
 */
@Composable
internal fun rememberEditableNumber(key: Any, modelValue: Double?): MutableState<String> {
    val state = rememberSaveable(key) { mutableStateOf(formatEditableNumber(modelValue)) }
    LaunchedEffect(modelValue) {
        state.value = syncEditableNumber(state.value, modelValue)
    }
    return state
}
