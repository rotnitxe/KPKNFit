package com.example.kpkn.screens.programdetail.design.structure

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.example.kpkn.ui.components.KpknSheet
import com.example.kpkn.ui.components.icons.KpknLineIcons

/** Una opción de la biblioteca: su clave, su nombre y la línea de debajo («4 sesiones por semana», «Ondas de 3 semanas…»). */
internal data class ProgramLibraryItem(
    val key: String,
    val name: String,
    val detail: String?,
)

/** «4 sesiones por semana» o «De 3 a 5 sesiones por semana»: los días de una plantilla. Pura. */
internal fun libraryFrequencyLine(frequencies: IntRange): String {
    val first = frequencies.first
    val last = frequencies.last
    return when {
        first == last && first == 1 -> "1 sesión por semana"
        first == last -> "$first sesiones por semana"
        else -> "De $first a $last sesiones por semana"
    }
}

/** La primera frase de una descripción, sin el punto final: la línea de un método de progresión. Null si no hay texto. Pura. */
internal fun libraryMethodLine(description: String): String? {
    val text = description.trim()
    if (text.isEmpty()) return null
    val end = text.indexOfFirst { it == '.' || it == '\n' }
    return (if (end > 0) text.substring(0, end) else text).trim().trimEnd('.')
}

/**
 * e-library, hoja «Aplicar plantilla o método»: el título y su explicación, el grupo «Plantillas» (libro) y el grupo «Métodos de
 * progresión» (tendencia), una fila `.mi` por opción, y al final el interruptor «Sustituir semanas con sesiones». Tocar una opción la
 * aplica con el interruptor como está: apagado rellena las semanas vacías (o crea una copia si ya tienen sesiones); encendido, sustituye.
 */
@Composable
internal fun ProgramTemplatesSheet(
    templates: List<ProgramLibraryItem>,
    methods: List<ProgramLibraryItem>,
    replace: Boolean,
    onReplaceChange: (Boolean) -> Unit,
    onPickTemplate: (key: String) -> Unit,
    onPickMethod: (key: String) -> Unit,
    onDismiss: () -> Unit,
) {
    KpknSheet(onDismissRequest = onDismiss) {
        ProgramTemplatesContent(
            templates = templates,
            methods = methods,
            replace = replace,
            onReplaceChange = onReplaceChange,
            onPickTemplate = onPickTemplate,
            onPickMethod = onPickMethod,
        )
    }
}

/** El contenido de la hoja sin la hoja. */
@Composable
internal fun ProgramTemplatesContent(
    templates: List<ProgramLibraryItem>,
    methods: List<ProgramLibraryItem>,
    replace: Boolean,
    onReplaceChange: (Boolean) -> Unit,
    onPickTemplate: (key: String) -> Unit,
    onPickMethod: (key: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(StructureSheetPadding),
    ) {
        StructureSheetHeader(
            title = "Aplicar plantilla o método",
            subtitle = "Rellena las semanas vacías. Si ya tienen sesiones, eliges si sustituirlas.",
        )
        var first = true
        if (templates.isNotEmpty()) {
            StructureSheetGroup(text = "Plantillas")
            templates.forEach { item ->
                StructureMenuRow(
                    icon = KpknLineIcons.Book,
                    title = item.name,
                    detail = item.detail,
                    divider = !first,
                    onClick = { onPickTemplate(item.key) },
                )
                first = false
            }
        }
        if (methods.isNotEmpty()) {
            StructureSheetGroup(text = "Métodos de progresión")
            methods.forEach { item ->
                StructureMenuRow(
                    icon = KpknLineIcons.TrendUp,
                    title = item.name,
                    detail = item.detail,
                    divider = !first,
                    onClick = { onPickMethod(item.key) },
                )
                first = false
            }
        }
        StructureField(
            label = null,
            onClick = { onReplaceChange(!replace) },
            modifier = Modifier
                .padding(top = 6.dp)
                .semantics {
                    role = Role.Switch
                    stateDescription = if (replace) "Activado" else "Desactivado"
                },
            trailing = { StructureSwitch(checked = replace) },
        ) {
            Text(text = "Sustituir semanas con sesiones", style = StructureFieldValueStyle, color = StructureInk)
        }
    }
}
