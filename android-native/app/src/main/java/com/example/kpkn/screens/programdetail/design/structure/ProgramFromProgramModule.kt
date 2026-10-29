package com.example.kpkn.screens.programdetail.design.structure

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.protocols.PlanLoadReferenceKind
import com.example.kpkn.domain.training.ExerciseTrainingMax
import java.util.Locale

/** Una fila de un grupo de la página (`.mi`): icono, título, detalle y lo que abre. [tag] es su etiqueta de prueba. */
internal data class ProgramFromProgramItem(
    val title: String,
    val detail: String?,
    val icon: ImageVector,
    val tag: String,
    val onClick: () -> Unit,
)

/**
 * Un grupo de la página Estructura: la etiqueta `.group-l` ([label], si hay) y un `.mod.tight` con una fila `.mi` por cosa. Es «Del
 * programa» (cargas de referencia, plantillas y versiones), «Ciclos» o el pie de la rutina. Sin estado: las acciones llegan como lambdas.
 */
@Composable
internal fun ProgramFromProgramModule(
    items: List<ProgramFromProgramItem>,
    modifier: Modifier = Modifier,
    label: String? = "Del programa",
) {
    if (label != null) StructureGroupLabel(text = label)
    StructureModule(modifier = modifier, tight = true) {
        items.forEachIndexed { index, item ->
            StructureMenuRow(
                icon = item.icon,
                title = item.title,
                detail = item.detail,
                divider = index > 0,
                onClick = item.onClick,
                modifier = Modifier.testTag(item.tag),
            )
        }
    }
}

/**
 * «4 ejercicios · falta press militar»: la línea de «Cargas de referencia (TM)». Con dos que faltan, «faltan a y b»; con más, «faltan 3».
 * Los nombres van en minúscula, como en el lienzo. Pura.
 */
internal fun trainingMaxRowLine(count: Int, missingNames: List<String>): String {
    val base = if (count == 1) "1 ejercicio" else "$count ejercicios"
    val names = missingNames.map { it.trim().lowercase(Locale.forLanguageTag("es")) }.filter { it.isNotEmpty() }
    return when {
        missingNames.isEmpty() -> base
        missingNames.size == 1 && names.size == 1 -> "$base · falta ${names[0]}"
        missingNames.size == 2 && names.size == 2 -> "$base · faltan ${names[0]} y ${names[1]}"
        else -> "$base · faltan ${missingNames.size}"
    }
}

/** Los nombres de los ejercicios de [targets] (ids de ejercicio con TM) que aún no tienen TM, sin repetir, en el orden del programa. */
internal fun trainingMaxMissingNames(program: Program, targets: List<String>): List<String> {
    val migrated = ExerciseTrainingMax.migrate(program)
    return ExerciseTrainingMax.exercises(migrated)
        .filter { it.id in targets }
        .filter { exercise -> ExerciseTrainingMax.capturedKg(migrated, exercise, PlanLoadReferenceKind.EXERCISE_TM) == null }
        .map { it.name }
        .distinct()
}
