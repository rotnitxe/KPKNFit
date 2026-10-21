package com.example.kpkn.ui.components

import androidx.compose.runtime.mutableIntStateOf

/**
 * Hojas y diálogos abiertos ahora (Home v1.1, I12). [KpknSheet], [KpknAlertDialog] y [KpknGlassDialog] suben el contador al entrar y
 * lo bajan al salir. La isla superior y la barra de Home lo leen y se ocultan mientras no sea cero: una hoja se pinta en el anfitrión
 * de superposiciones, por encima de la isla, y sin este contador la isla quedaría encima de la hoja.
 */
object KpknModalTracker {
    private val count = mutableIntStateOf(0)

    /** Hojas y diálogos abiertos. Leerlo en composición la recompone al abrir o cerrar uno. */
    val openCount: Int get() = count.intValue

    /** Se ha abierto una hoja o un diálogo (lo llama su `DisposableEffect`). */
    internal fun onOpened() {
        count.intValue += 1
    }

    /** Se ha cerrado una hoja o un diálogo; nunca baja de cero. */
    internal fun onClosed() {
        if (count.intValue > 0) count.intValue -= 1
    }
}
