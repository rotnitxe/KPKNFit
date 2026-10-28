package com.example.kpkn.screens.workout.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.RowScope
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknType
import dev.chrisbanes.haze.HazeState

/**
 * Opciones de un ejercicio (LV11, A2; prototipo C2): lo que se abre con la pulsación larga en el carrusel. Una hoja de siempre
 * (`KpknSheet`, vidrio del anfitrión) con el ejercicio arriba (miniatura, nombre en tamaño normal y su dosis con lo hecho) y debajo
 * las opciones: losas compactas en rejilla y, al pie, dos píldoras de contorno («Saltar ejercicio» y «Quitar de la sesión»).
 *
 * Esta pieza solo pinta el marco y la cabecera; [content] pone la rejilla de cada tipo de ejercicio (fuerza, superserie o cardio).
 */
@Composable
internal fun ExerciseOptionsSheet(
    title: String,
    subtitle: String?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") hazeState: HazeState? = null,
    thumbnail: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    // Prototipo 02 (`exercise`): hoja «Opciones del ejercicio» con la cabecera de contexto y la rejilla de dos columnas.
    LiveSheet(title = "Opciones del ejercicio", onDismiss = onDismiss, modifier = modifier, tag = "exercise-options-sheet", footer = null) {
        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            thumbnail?.invoke()
            Column(Modifier.weight(1f)) {
                Text(title, style = KpknType.bodyStrong.copy(fontSize = 14.sp, lineHeight = 19.sp), color = WizardColors.text)
                if (!subtitle.isNullOrBlank()) {
                    Text(subtitle, Modifier.padding(top = 3.dp), style = KpknType.caption.copy(fontSize = 12.sp, lineHeight = 19.sp),
                        color = LiveSheetTokens.Muted)
                }
            }
        }
        content()
    }
}

/** Una opción de la rejilla del cajón: su rótulo, su icono y lo que hace. */
internal data class ExerciseOption(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val danger: Boolean = false,
    val enabled: Boolean = true,
)

/**
 * La rejilla de losas del prototipo C2: tres por fila (dos con la letra por encima del 115 %, para que ningún rótulo se parta a mitad de
 * palabra); las de una fila miden lo mismo (la de la más alta); la última fila se completa con huecos. [columns] fija otra cifra.
 */
@Composable
internal fun ExerciseOptionsGrid(
    options: List<ExerciseOption>,
    @Suppress("UNUSED_PARAMETER") columns: Int = 0,
) {
    // Prototipo 02: siempre dos columnas; con la letra grande cada rótulo sigue entero porque la fila crece en alto.
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        options.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                row.forEach { option -> ExerciseOptionTile(option) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun RowScope.ExerciseOptionTile(option: ExerciseOption) {
    val color = if (option.danger) LiveSheetTokens.Danger else LiveSheetTokens.Muted
    Row(Modifier.weight(1f).fillMaxHeight().heightIn(min = 48.dp).liveSheetTopLine()
        .clickable(enabled = option.enabled, role = Role.Button, onClick = option.onClick)
        .semantics(mergeDescendants = true) { contentDescription = option.label }
        .padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(option.icon, null, Modifier.size(20.dp), tint = color.copy(alpha = if (option.enabled) 1f else .4f))
        Text(option.label, style = KpknType.caption.copy(fontSize = 12.sp, lineHeight = 16.sp),
            color = color.copy(alpha = if (option.enabled) 1f else .4f))
    }
}

/**
 * Las opciones de un ejercicio de fuerza (A2): las del prototipo (Reemplazar, Reordenar, Crear superserie, Historial, Ultrarrápido,
 * Notas) y las que la app ya tenía y el prototipo no enseña (Etiquetas, Editar, Agregar ejercicio); al pie, «Saltar ejercicio» (omitir) y
 * «Quitar de la sesión» (con confirmación y «Deshacer», que pide quien llama).
 */
@Composable
internal fun ColumnScope.ExerciseOptionsStrengthContent(
    onReplace: () -> Unit,
    onReorder: () -> Unit,
    onCreateSuperset: () -> Unit,
    onHistory: () -> Unit,
    onUltraFast: () -> Unit,
    onNotes: () -> Unit,
    onTags: () -> Unit,
    onEdit: () -> Unit,
    onAddOther: () -> Unit,
    onSkip: () -> Unit,
    onRemove: () -> Unit,
    canRemove: Boolean = true,
    onRM: (() -> Unit)? = null,
) {
    ExerciseOptionsGrid(
        options = listOf(
            ExerciseOption("Etiquetas", KpknLineIcons.Tag, onTags),
            ExerciseOption("Historial", KpknLineIcons.History, onHistory),
            ExerciseOption("Reemplazar", KpknLineIcons.Swap, onReplace),
            ExerciseOption("Series", KpknLineIcons.Sliders, onEdit),
            ExerciseOption("Orden", KpknLineIcons.Reorder, onReorder),
            ExerciseOption("Crear superserie", KpknLineIcons.Link, onCreateSuperset),
            ExerciseOption("Aplicar técnica", LiveOptionIcons.Bolt, onUltraFast),
            ExerciseOption("Notas", LiveOptionIcons.Note, onNotes),
            ExerciseOption("Agregar ejercicio", KpknLineIcons.Plus, onAddOther),
        ) + listOfNotNull(onRM?.let { ExerciseOption("Calculadora RM", KpknLineIcons.TrendUp, it) }),
    )
    ExerciseOptionsFooter(onSkip = onSkip, onRemove = onRemove, canRemove = canRemove)
}

/** Las opciones de un ejercicio de cardio (A2): sus opciones de siempre con el mismo aspecto y, al pie, lo que antes hacía el modo dios. */
@Composable
internal fun ColumnScope.ExerciseOptionsCardioContent(
    onEdit: () -> Unit,
    onReplace: () -> Unit,
    onSkip: () -> Unit,
    onRemove: () -> Unit,
    canRemove: Boolean = true,
) {
    ExerciseOptionsGrid(
        options = listOf(
            ExerciseOption("Editar cardio", KpknLineIcons.Edit, onEdit),
            ExerciseOption("Reemplazar", KpknLineIcons.Swap, onReplace),
        ),
    )
    ExerciseOptionsFooter(onSkip = onSkip, onRemove = onRemove, canRemove = canRemove)
}

/** Un miembro de una superserie, para su losa. */
internal data class SupersetMemberOption(val id: String, val name: String)

/**
 * Las opciones de una superserie (A2): Ir a la superserie, Rondas, Agregar de sesión y Agregar del catálogo; sus miembros (cada uno abre
 * las opciones de ese ejercicio) y, al pie, «Disolver superserie».
 */
@Composable
internal fun ColumnScope.ExerciseOptionsSupersetContent(
    members: List<SupersetMemberOption>,
    onGo: () -> Unit,
    onRounds: () -> Unit,
    onAddFromSession: () -> Unit,
    onAddFromCatalog: () -> Unit,
    onOpenMember: (String) -> Unit,
    onDissolve: () -> Unit,
) {
    ExerciseOptionsGrid(
        options = listOf(
            ExerciseOption("Ir a la superserie", KpknLineIcons.Route, onGo),
            ExerciseOption("Rondas", KpknLineIcons.Repeat, onRounds),
            ExerciseOption("Agregar de sesión", KpknLineIcons.Plus, onAddFromSession),
            ExerciseOption("Agregar del catálogo", KpknLineIcons.Layers, onAddFromCatalog),
        ),
    )
    if (members.isNotEmpty()) {
        LiveSheetSectionLabel("Miembros")
        ExerciseOptionsGrid(
            options = members.map { member -> ExerciseOption(member.name, KpknLineIcons.Link, { onOpenMember(member.id) }) },
            columns = 2,
        )
    }
    LiveSheetMenuRow("Disolver superserie", onDissolve, icon = KpknLineIcons.Unlink, danger = true, chevron = false)
}

/** Pie del prototipo 02: «Saltar ejercicio» y «Quitar de la sesión» como filas; la segunda en rojo. */
@Composable
private fun ExerciseOptionsFooter(onSkip: () -> Unit, onRemove: () -> Unit, canRemove: Boolean) {
    LiveSheetMenuRow("Saltar ejercicio", onSkip, icon = KpknLineIcons.FrameForward, chevron = false)
    LiveSheetMenuRow("Quitar de la sesión", onRemove, icon = KpknLineIcons.Trash, danger = true, chevron = false, enabled = canRemove)
}

/** La miniatura de una superserie (que no tiene una ilustración propia): su icono de línea sobre `inset`, del mismo tamaño que la de un ejercicio. */
@Composable
internal fun ExerciseOptionsIconThumb(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(KpknSurface.inset),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(24.dp), tint = WizardColors.text)
    }
}
