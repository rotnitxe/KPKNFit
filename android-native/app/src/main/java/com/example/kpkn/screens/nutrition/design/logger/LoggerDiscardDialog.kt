package com.example.kpkn.screens.nutrition.design.logger

import androidx.compose.runtime.Composable

/**
 * Confirmación de descarte (lienzo s-discard): «¿Descartar esta comida?» con cuántos alimentos se pierden —o, al editar, «¿Descartar los
 * cambios?»—. «Descartar» es la salida en rojo; «Seguir editando» no toca nada.
 */
@Composable
fun LoggerDiscardDialog(
    foodCount: Int,
    isEditing: Boolean,
    onDiscard: () -> Unit,
    onKeepEditing: () -> Unit,
) {
    LoggerConfirmCard(
        title = if (isEditing) "¿Descartar los cambios?" else "¿Descartar esta comida?",
        sub = if (isEditing) "La comida seguirá como estaba guardada." else loggerDiscardText(foodCount),
        cancelLabel = "Seguir editando",
        confirmLabel = "Descartar",
        onCancel = onKeepEditing,
        onConfirm = onDiscard,
    )
}

/** La frase de s-discard: «Tienes 4 alimentos sin guardar.», «Tienes 1 alimento sin guardar.» y, sin alimentos, «Lo que escribiste no se guardará.». */
internal fun loggerDiscardText(foodCount: Int): String = when {
    foodCount <= 0 -> "Lo que escribiste no se guardará."
    foodCount == 1 -> "Tienes 1 alimento sin guardar."
    else -> "Tienes $foodCount alimentos sin guardar."
}
