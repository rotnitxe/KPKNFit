package com.example.kpkn.screens.programdetail.components.editor

import androidx.compose.runtime.Composable
import com.example.kpkn.data.models.ProgramKeyDate
import com.example.kpkn.screens.programdetail.design.structure.ProgramKeyDateSheet
import java.time.LocalDate

/**
 * Hoja de fecha clave (P3a): nueva o de edición según [isNew]; [today] es el día de hoy de la pantalla. Los diálogos de bloque, mesociclo
 * y semana viven ahora en `design/structure/` (P3b).
 */
@Composable
internal fun KeyDateDialog(
    keyDate: ProgramKeyDate,
    isNew: Boolean,
    today: LocalDate,
    onSave: (ProgramKeyDate) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) = ProgramKeyDateSheet(
    keyDate = keyDate,
    isNew = isNew,
    today = today,
    onSave = onSave,
    onDelete = onDelete,
    onDismiss = onDismiss,
)
