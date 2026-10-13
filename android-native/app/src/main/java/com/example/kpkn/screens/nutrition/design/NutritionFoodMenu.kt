package com.example.kpkn.screens.nutrition.design

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Rect
import com.example.kpkn.data.models.MealType
import com.example.kpkn.domain.nutrition.MealSlots
import com.example.kpkn.ui.design.KpknContextMenu
import com.example.kpkn.ui.design.KpknContextMenuRow

/**
 * El menú de una fila de registro (N2): «Duplicar», «Mover a…» y «Eliminar» en `danger`. «Mover a…» despliega las otras comidas
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
) {
    val rows = if (moveExpanded) 3 + otherMeals.size else 3
    KpknContextMenu(anchor = anchor, rows = rows, onDismiss = onDismiss) {
        KpknContextMenuRow(label = "Duplicar", onClick = {
            onDismiss()
            onDuplicate()
        })
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
        KpknContextMenuRow(label = "Eliminar", destructive = true, onClick = {
            onDismiss()
            onDelete()
        })
    }
}
