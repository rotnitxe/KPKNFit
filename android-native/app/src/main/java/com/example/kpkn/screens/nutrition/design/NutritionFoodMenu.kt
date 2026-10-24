package com.example.kpkn.screens.nutrition.design

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Rect
import com.example.kpkn.data.models.MealType
import com.example.kpkn.domain.nutrition.MealSlots
import com.example.kpkn.ui.design.KpknContextMenu
import com.example.kpkn.ui.design.KpknContextMenuRow

/**
 * El menú de una fila de registro (N2): «Duplicar», «Mover a…» y «Eliminar» en `danger`; N4 suma «Copiar a…» y «Guardar como plantilla» (opcionales). «Mover a…» despliega las otras comidas
 * ([otherMeals]) en lugar de cambiar nada: lo decide la pantalla con [moveExpanded]. Duplicar, mover y eliminar cierran el menú;
 * «Mover a…» solo lo despliega. Sin estado.
 */
@Composable
fun NutritionFoodMenu(
    anchor: Rect,
    moveExpanded: Boolean,
    otherMeals: List<MealType>,
    onDuplicate: () -> Unit,
    onToggleMove: () -> Unit,
    onMove: (MealType) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    /** N4: «Copiar a…» (abre la hoja de fechas). Null = el menú no lo ofrece. */
    onCopyTo: (() -> Unit)? = null,
    /** N4: «Guardar como plantilla» (pide el nombre). Null = el menú no lo ofrece. */
    onSaveTemplate: (() -> Unit)? = null,
) {
    val baseRows = 3 + (if (onCopyTo != null) 1 else 0) + (if (onSaveTemplate != null) 1 else 0)
    val rows = if (moveExpanded) baseRows + otherMeals.size else baseRows
    KpknContextMenu(anchor = anchor, rows = rows, onDismiss = onDismiss) {
        KpknContextMenuRow(label = "Duplicar", onClick = {
            onDismiss()
            onDuplicate()
        })
        if (onCopyTo != null) {
            KpknContextMenuRow(label = "Copiar a…", onClick = {
                onDismiss()
                onCopyTo()
            })
        }
        KpknContextMenuRow(label = "Mover a…", onClick = onToggleMove)
        if (moveExpanded) {
            otherMeals.forEach { meal ->
                KpknContextMenuRow(
                    label = MealSlots.mealLabel(meal),
                    indented = true,
                    onClick = {
                        onDismiss()
                        onMove(meal)
                    },
                )
            }
        }
        if (onSaveTemplate != null) {
            KpknContextMenuRow(label = "Guardar como plantilla", onClick = {
                onDismiss()
                onSaveTemplate()
            })
        }
        KpknContextMenuRow(label = "Eliminar", destructive = true, onClick = {
            onDismiss()
            onDelete()
        })
    }
}
