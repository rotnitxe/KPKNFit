package com.example.kpkn.screens.nutrition.reuse

import androidx.compose.runtime.Composable
import com.example.kpkn.ui.components.KpknAlertDialog

/**
 * El texto de la pregunta de «Repetir ayer» cuando el día ya tiene comidas: dice cuántas hay, cuántas se traen y que lo ya registrado no
 * se toca. [sourceIsYesterday] distingue «ayer» de «el día anterior» (cuando se mira otro día que no es hoy).
 */
internal fun repeatPromptText(existingMeals: Int, incomingMeals: Int, sourceIsYesterday: Boolean): String {
    val here = if (existingMeals == 1) "Este día ya tiene 1 comida." else "Este día ya tiene $existingMeals comidas."
    val source = if (sourceIsYesterday) "de ayer" else "del día anterior"
    val incoming = if (incomingMeals == 1) "1 comida $source" else "$incomingMeals comidas $source"
    return "$here ¿Añadir también $incoming? Lo que ya registraste no se toca."
}

/** «Repetir ayer» sobre un día con comidas: pregunta «Añadir» o «Cancelar». Nunca ofrece reemplazar. */
@Composable
fun RepeatYesterdayDialog(
    existingMeals: Int,
    incomingMeals: Int,
    sourceIsYesterday: Boolean,
    onAdd: () -> Unit,
    onCancel: () -> Unit,
) {
    KpknAlertDialog(
        onDismissRequest = onCancel,
        title = if (sourceIsYesterday) "Repetir ayer" else "Repetir el día anterior",
        text = repeatPromptText(existingMeals, incomingMeals, sourceIsYesterday),
        confirmLabel = "Añadir",
        onConfirm = onAdd,
        dismissLabel = "Cancelar",
        onDismiss = onCancel,
    )
}
