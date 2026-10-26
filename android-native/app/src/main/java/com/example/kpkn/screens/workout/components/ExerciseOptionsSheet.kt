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
    hazeState: HazeState? = null,
    thumbnail: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    KpknSheet(onDismissRequest = onDismiss, modifier = modifier, hazeState = hazeState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                thumbnail?.invoke()
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = KpknType.bodyStrong,
                        color = WizardColors.text,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            style = KpknType.caption,
                            color = WizardColors.textMuted,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }
            content()
        }
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
    columns: Int = 0,
) {
    val columnCount = columns.takeIf { it > 0 } ?: if (largeLiveFont()) 2 else 3
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.chunked(columnCount).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                row.forEach { option -> ExerciseOptionTile(option) }
                repeat(columnCount - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun RowScope.ExerciseOptionTile(option: ExerciseOption) {
    LiveOptionTile(
        label = option.label,
        icon = option.icon,
        onClick = option.onClick,
        enabled = option.enabled,
        danger = option.danger,
        minHeight = 72.dp,
        corner = 18.dp,
        background = KpknSurface.modulePressed,
        iconSize = 20.dp,
        modifier = Modifier.weight(1f).fillMaxHeight(),
    )
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
            ExerciseOption("Reemplazar", KpknLineIcons.Swap, onReplace),
            ExerciseOption("Reordenar", KpknLineIcons.Reorder, onReorder),
            ExerciseOption("Crear superserie", KpknLineIcons.Link, onCreateSuperset),
            ExerciseOption("Historial", KpknLineIcons.History, onHistory),
            ExerciseOption("Aplicar técnica", LiveOptionIcons.Bolt, onUltraFast),
            ExerciseOption("Notas", LiveOptionIcons.Note, onNotes),
            ExerciseOption("Etiquetas", KpknLineIcons.Tag, onTags),
            ExerciseOption("Editar", KpknLineIcons.Edit, onEdit),
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
        Text("Miembros".uppercase(), style = KpknType.eyebrow, color = WizardColors.textMuted)
        ExerciseOptionsGrid(
            options = members.map { member -> ExerciseOption(member.name, KpknLineIcons.Link, { onOpenMember(member.id) }) },
            columns = 2,
        )
    }
    LiveOutlinePill(
        text = "Disolver superserie",
        onClick = onDissolve,
        danger = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Las dos píldoras del pie: lado a lado y, con la letra por encima del 115 %, una sobre otra (cada una con su rótulo entero). */
@Composable
private fun ExerciseOptionsFooter(onSkip: () -> Unit, onRemove: () -> Unit, canRemove: Boolean) {
    if (largeLiveFont()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            LiveOutlinePill(text = "Saltar ejercicio", onClick = onSkip, modifier = Modifier.fillMaxWidth())
            LiveOutlinePill(
                text = "Quitar de la sesión",
                onClick = onRemove,
                danger = true,
                enabled = canRemove,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LiveOutlinePill(text = "Saltar ejercicio", onClick = onSkip, modifier = Modifier.weight(1f))
            LiveOutlinePill(
                text = "Quitar de la sesión",
                onClick = onRemove,
                danger = true,
                enabled = canRemove,
                modifier = Modifier.weight(1f),
            )
        }
    }
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
