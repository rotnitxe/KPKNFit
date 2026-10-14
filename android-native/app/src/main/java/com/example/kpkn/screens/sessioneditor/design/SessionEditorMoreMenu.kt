package com.example.kpkn.screens.sessioneditor.design

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Rect
import com.example.kpkn.ui.design.KpknContextMenu
import com.example.kpkn.ui.design.KpknContextMenuRow

/**
 * Menú «⋯» de la sesión (SESSION_EDITOR_V1 §2, punto 1): un menú de vidrio anclado al botón con cinco acciones. Cada acción cierra el
 * menú (lo hace quien la pasa) y abre lo que ya existe: portada, historial, copiar, plantilla o reglas.
 *
 * @param anchor posición del botón «⋯» en la ventana.
 */
@Composable
fun SessionEditorMoreMenu(
    anchor: Rect,
    onDismiss: () -> Unit,
    onCover: () -> Unit,
    onHistory: () -> Unit,
    onTransfer: () -> Unit,
    onSaveTemplate: () -> Unit,
    onRules: () -> Unit,
) {
    KpknContextMenu(anchor = anchor, rows = 5, onDismiss = onDismiss) {
        KpknContextMenuRow(label = "Portada", onClick = onCover)
        KpknContextMenuRow(label = "Historial de versiones", onClick = onHistory)
        KpknContextMenuRow(label = "Copiar a otras sesiones", onClick = onTransfer)
        KpknContextMenuRow(label = "Guardar como plantilla", onClick = onSaveTemplate)
        KpknContextMenuRow(label = "Reglas de la sesión", onClick = onRules)
    }
}
