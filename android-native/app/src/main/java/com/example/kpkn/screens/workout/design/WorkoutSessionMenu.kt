package com.example.kpkn.screens.workout.design

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Rect
import com.example.kpkn.ui.design.KpknContextMenu
import com.example.kpkn.ui.design.KpknContextMenuRow

/**
 * Menú «Más opciones» de la fila superior de la sesión (LS2a, §2 punto 1): «Sesión», «Notas», «Fotos», «Etiquetas», «Modo de voz»,
 * «Activar voz» o «Desactivar voz» según [voiceEnabled], y «Terminar sesión». Cada fila cierra el menú y hace su acción. Sin estado.
 */
@Composable
fun WorkoutSessionMenu(
    anchor: Rect,
    voiceEnabled: Boolean,
    onDismiss: () -> Unit,
    onCockpit: () -> Unit,
    onNotes: () -> Unit,
    onPhotos: () -> Unit,
    onTags: () -> Unit,
    onVoiceMode: () -> Unit,
    onToggleVoice: () -> Unit,
    onFinish: () -> Unit,
) {
    KpknContextMenu(anchor = anchor, rows = 7, onDismiss = onDismiss) {
        KpknContextMenuRow(label = "Sesión", onClick = {
            onDismiss()
            onCockpit()
        })
        KpknContextMenuRow(label = "Notas", onClick = {
            onDismiss()
            onNotes()
        })
        KpknContextMenuRow(label = "Fotos", onClick = {
            onDismiss()
            onPhotos()
        })
        KpknContextMenuRow(label = "Etiquetas", onClick = {
            onDismiss()
            onTags()
        })
        KpknContextMenuRow(label = "Modo de voz", onClick = {
            onDismiss()
            onVoiceMode()
        })
        KpknContextMenuRow(label = if (voiceEnabled) "Desactivar voz" else "Activar voz", onClick = {
            onDismiss()
            onToggleVoice()
        })
        KpknContextMenuRow(label = "Terminar sesión", onClick = {
            onDismiss()
            onFinish()
        })
    }
}
