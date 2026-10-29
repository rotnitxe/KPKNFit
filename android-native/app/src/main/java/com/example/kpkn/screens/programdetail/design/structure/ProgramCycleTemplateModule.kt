package com.example.kpkn.screens.programdetail.design.structure

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/**
 * Plantillas de ciclo: un `.mod.tight` con una fila `.mi` por plantilla (icono, nombre y lo que hace) y «Añadir» (`.act`) a la derecha.
 * Solo «Añadir» añade: el resto de la fila no responde, para no crear un ciclo sin querer.
 */
@Composable
internal fun ProgramCycleTemplatesModule(
    templates: List<ProgramCycleTemplate>,
    onAdd: (templateId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    StructureModule(modifier = modifier, tight = true) {
        templates.forEachIndexed { index, template ->
            StructureMenuRow(
                icon = template.icon,
                title = template.name,
                detail = template.description,
                divider = index > 0,
                onClick = {},
                enabled = false,
                trailing = {
                    // Con el nombre en la descripción accesible, «Añadir» no suena igual en cada fila.
                    StructureAction(
                        text = "Añadir",
                        onClick = { onAdd(template.id) },
                        modifier = Modifier.semantics { contentDescription = "Añadir ${template.name}" },
                    )
                },
            )
        }
    }
}
