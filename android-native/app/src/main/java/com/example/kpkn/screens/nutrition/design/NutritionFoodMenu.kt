package com.example.kpkn.screens.nutrition.design

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.MealType
import com.example.kpkn.data.models.NutritionLog
import com.example.kpkn.domain.nutrition.MealSlots
import com.example.kpkn.domain.nutrition.MealTime
import com.example.kpkn.domain.nutrition.computeFoodTotals
import com.example.kpkn.screens.nutrition.design.logger.LoggerColors
import com.example.kpkn.screens.nutrition.design.logger.LoggerIcons
import com.example.kpkn.screens.nutrition.design.logger.LoggerMenuRow
import com.example.kpkn.screens.nutrition.design.logger.LoggerRowDivider
import com.example.kpkn.screens.nutrition.design.logger.LoggerSheetTitle
import com.example.kpkn.screens.nutrition.design.logger.LoggerText
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.components.icons.KpknLineIcons
import kotlin.math.roundToInt

/**
 * La hoja ⋯ de una comida registrada (lienzo s-rowmenu): el nombre de la comida con «08:40 · 548 kcal · 4 alimentos» y una fila con icono
 * y subtítulo por acción: «Editar» (solo si hay [onEdit]), «Duplicar», «Copiar a…» y «Guardar como plantilla» (solo si existen sus
 * lambdas), «Mover a…» y «Eliminar» (en rojo). «Mover a…» despliega las otras comidas ([otherMeals]) dentro de la hoja: lo decide la
 * pantalla con [moveExpanded]. Todas las acciones menos «Mover a…» cierran la hoja y luego actúan. Sin estado.
 *
 * [anchor] ya no se usa (el menú anclado a la pulsación larga pasó a hoja); se conserva para no tocar a quien llama. [log] da el título
 * y la línea de estado; sin él la hoja se titula «Comida» y no lleva línea.
 */
@Composable
fun NutritionFoodMenu(
    @Suppress("UNUSED_PARAMETER") anchor: Rect,
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
    /** La comida registrada: de ella salen el título, la hora, las kcal y los alimentos. */
    log: NutritionLog? = null,
    /** «Editar» (abre la comida en el registro). Null = la hoja no lo ofrece. */
    onEdit: (() -> Unit)? = null,
) {
    KpknSheet(onDismissRequest = onDismiss, backdropScrim = Color.Black.copy(alpha = 0.67f)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 18.dp, end = 18.dp, bottom = 30.dp),
        ) {
            LoggerSheetTitle(
                title = log?.let { MealSlots.mealLabel(it.mealType) } ?: "Comida",
                sub = log?.let(::foodMenuStatusLine),
            )
            var first = true
            fun divider(): Boolean {
                val show = !first
                first = false
                return show
            }
            if (onEdit != null) {
                LoggerMenuRow(
                    icon = KpknLineIcons.Edit,
                    title = "Editar",
                    divider = divider(),
                    onClick = {
                        onDismiss()
                        onEdit()
                    },
                )
            }
            LoggerMenuRow(
                icon = LoggerIcons.Copy,
                title = "Duplicar",
                subtitle = "En la misma comida",
                divider = divider(),
                onClick = {
                    onDismiss()
                    onDuplicate()
                },
            )
            if (onCopyTo != null) {
                LoggerMenuRow(
                    icon = KpknLineIcons.Calendar,
                    title = "Copiar a…",
                    subtitle = "Hoy, mañana o los próximos días",
                    divider = divider(),
                    onClick = {
                        onDismiss()
                        onCopyTo()
                    },
                )
            }
            LoggerMenuRow(
                icon = KpknLineIcons.Swap,
                title = "Mover a…",
                subtitle = "Otra comida u otro día",
                divider = divider(),
                onClick = onToggleMove,
            )
            if (moveExpanded) {
                otherMeals.forEach { meal ->
                    MoveTargetRow(
                        label = MealSlots.mealLabel(meal),
                        onClick = {
                            onDismiss()
                            onMove(meal)
                        },
                    )
                }
            }
            if (onSaveTemplate != null) {
                LoggerMenuRow(
                    icon = LoggerIcons.Star,
                    title = "Guardar como plantilla",
                    subtitle = "Para repetirla con un toque",
                    divider = divider(),
                    onClick = {
                        onDismiss()
                        onSaveTemplate()
                    },
                )
            }
            LoggerMenuRow(
                icon = KpknLineIcons.Trash,
                title = "Eliminar",
                subtitle = "Se puede deshacer",
                danger = true,
                divider = divider(),
                onClick = {
                    onDismiss()
                    onDelete()
                },
            )
        }
    }
}

/** Una comida de destino de «Mover a…», sangrada bajo el icono de su fila (54 dp) y con la altura mínima táctil. */
@Composable
private fun MoveTargetRow(label: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        LoggerRowDivider(Modifier.padding(start = 54.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(start = 54.dp, end = 2.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(text = label, style = LoggerText.rowTitle, color = LoggerColors.ink)
        }
    }
}

/** «08:40 · 548 kcal · 4 alimentos» (o «1 alimento»). */
internal fun foodMenuStatusLine(log: NutritionLog): String {
    val kcal = computeFoodTotals(log.foods).calories.roundToInt()
    val count = log.foods.size
    val foods = if (count == 1) "1 alimento" else "$count alimentos"
    return "${MealTime.label(log.date)} · $kcal kcal · $foods"
}
