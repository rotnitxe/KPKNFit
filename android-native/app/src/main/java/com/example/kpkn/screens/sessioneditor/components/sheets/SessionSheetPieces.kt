package com.example.kpkn.screens.sessioneditor.components.sheets

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.exercises.ExerciseTechniqueImageLookup
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.effectiveRepRange
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknSetFormat
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknType

/** Tope de ejercicios en una superserie: el mismo de `SupersetRules`. */
internal const val SupersetMaxMembers = 4

/**
 * Marca o desmarca [exerciseId] en la selección de la superserie. Con [max] marcados no entra ninguno más. El orden de la lista es el
 * orden en que se marcó, y ese es el orden de la superserie.
 */
internal fun toggleSupersetMember(current: List<String>, exerciseId: String, max: Int = SupersetMaxMembers): List<String> {
    val selected = current.distinct()
    return when {
        exerciseId in selected -> selected - exerciseId
        selected.size >= max -> selected
        else -> selected + exerciseId
    }
}

/**
 * Dosis corta para una fila: «4 × 6–8», «3 × 45 s» o «4 series» cuando las series no coinciden entre sí. Sin series, «Sin series».
 */
internal fun exerciseDoseSummary(exercise: Exercise): String {
    val sets = exercise.sets
    if (sets.isEmpty()) return "Sin series"
    val reps = sets.map { set -> set.effectiveRepRange()?.format() ?: set.targetReps?.toString() }
    val uniqueReps = reps.filterNotNull().distinct()
    if (uniqueReps.size == 1 && reps.all { it != null }) return "${sets.size} × ${uniqueReps.first()}"
    val durations = sets.map { set -> set.targetDuration?.takeIf { it > 0 } }
    val uniqueDurations = durations.filterNotNull().distinct()
    if (uniqueDurations.size == 1 && durations.all { it != null }) {
        return "${sets.size} × ${KpknSetFormat.doseTime(uniqueDurations.first())}"
    }
    return "${sets.size} series"
}

/** Miniatura de [dimension]: la ilustración del catálogo cuando la hay; si no, la inicial del nombre sobre `inset`. */
@Composable
internal fun SessionExerciseThumb(exercise: Exercise, dimension: Dp = 40.dp) {
    val imageRes = remember(exercise.id, exercise.catalogDefinitionId, exercise.catalogConfigurationId, exercise.exerciseDbId) {
        ExerciseTechniqueImageLookup.resolveImageResId(
            catalogDefinitionId = exercise.catalogDefinitionId,
            exerciseDbId = exercise.exerciseDbId,
            exerciseId = exercise.id,
            catalogConfigurationId = exercise.catalogConfigurationId,
            selectedImplementation = exercise.selectedExecutionOption ?: exercise.selectedAspects?.values?.firstOrNull(),
        )
    }
    Box(
        modifier = Modifier
            .size(dimension)
            .clip(RoundedCornerShape(12.dp))
            .background(KpknSurface.inset),
        contentAlignment = Alignment.Center,
    ) {
        if (imageRes != null) {
            Image(
                painter = painterResource(imageRes),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp),
                contentScale = ContentScale.Fit,
            )
        } else {
            Text(
                text = exercise.name.trim().firstOrNull()?.uppercaseChar()?.toString().orEmpty(),
                style = KpknType.bodyStrong,
                color = WizardColors.textMuted,
            )
        }
    }
}

/** Casilla de 20 dp de la app: anillo que pasa a tinta y, marcada, relleno de tinta con el check en negro. */
@Composable
internal fun SessionCheckMark(checked: Boolean) {
    val corner = RoundedCornerShape(6.dp)
    Box(
        modifier = Modifier
            .size(20.dp)
            .clip(corner)
            .background(if (checked) WizardColors.text else Color.Transparent, corner)
            .border(width = 1.75.dp, color = if (checked) WizardColors.text else WizardColors.textFaint, shape = corner),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Icon(
                imageVector = KpknLineIcons.Check,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = WizardColors.ctaContent,
            )
        }
    }
}

/** Filete de 1 dp entre filas de un módulo (KPKN_UI_V2: `divider`). */
@Composable
internal fun SessionSheetDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(KpknSurface.divider),
    )
}

/**
 * Fila de acción de una hoja (KPKN): icono en círculo `inset` de 36 dp y el rótulo en Inter 15. Zona táctil de 56 dp de alto. Con
 * [danger] se pinta en el color de borrar. [tag] es la etiqueta de prueba.
 */
@Composable
internal fun SessionSheetActionRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tag: String? = null,
    danger: Boolean = false,
) {
    val ink = if (danger) WizardColors.danger else WizardColors.text
    Row(
        modifier = modifier
            .then(if (tag != null) Modifier.testTag(tag) else Modifier)
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(KpknSurface.inset),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = ink)
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            style = KpknType.bodyStrong,
            color = ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Fila de la lista de la superserie: casilla, miniatura de 40 dp y el nombre con su dosis debajo. Con [selectable] en falso (ya hay
 * cuatro marcados y esta no está) va al 38 % y no responde.
 */
@Composable
internal fun SupersetPickRow(
    exercise: Exercise,
    checked: Boolean,
    selectable: Boolean,
    onToggle: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp)
            .graphicsLayer { alpha = if (selectable) 1f else 0.38f }
            .toggleable(
                value = checked,
                enabled = selectable,
                role = Role.Checkbox,
                interactionSource = interaction,
                indication = null,
                onValueChange = { onToggle() },
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SessionCheckMark(checked = checked)
        Spacer(Modifier.width(12.dp))
        SessionExerciseThumb(exercise = exercise)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = exercise.name,
                style = KpknType.bodyStrong,
                color = WizardColors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = exerciseDoseSummary(exercise),
                style = KpknType.caption,
                color = WizardColors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
