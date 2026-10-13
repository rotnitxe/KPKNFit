package com.example.kpkn.screens.workout.design

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Rect
import com.example.kpkn.ui.design.KpknContextMenu
import com.example.kpkn.ui.design.KpknContextMenuRow

/**
 * Menú «Opciones del ejercicio» de la cabecera (LS2a, §2 punto 3): «Ver historial», «Ultra rápido», «Adaptar» (solo si [onAdapt] existe)
 * y «Superserie» (si [onSuperset] existe). Cada fila cierra el menú y hace su acción. Sin estado.
 */
@Composable
fun WorkoutExerciseMenu(
    anchor: Rect,
    onDismiss: () -> Unit,
    onHistory: () -> Unit,
    onUltraFast: () -> Unit,
    onAdapt: (() -> Unit)?,
    onSuperset: (() -> Unit)?,
) {
    val rows = 2 + listOf(onAdapt, onSuperset).count { it != null }
    KpknContextMenu(anchor = anchor, rows = rows, onDismiss = onDismiss) {
        KpknContextMenuRow(label = "Ver historial", onClick = {
            onDismiss()
            onHistory()
        })
        KpknContextMenuRow(label = "Ultra rápido", onClick = {
            onDismiss()
            onUltraFast()
        })
        if (onAdapt != null) {
            KpknContextMenuRow(label = "Adaptar", onClick = {
                onDismiss()
                onAdapt()
            })
        }
        if (onSuperset != null) {
            KpknContextMenuRow(label = "Superserie", onClick = {
                onDismiss()
                onSuperset()
            })
        }
    }
}
