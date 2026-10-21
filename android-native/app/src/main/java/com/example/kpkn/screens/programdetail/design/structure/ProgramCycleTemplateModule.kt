package com.example.kpkn.screens.programdetail.design.structure

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknType

/**
 * Plantillas de ciclo (P7): un solo módulo con una fila por plantilla. Cada fila lleva el icono KPKN en un círculo `inset`, el nombre en
 * `bodyStrong`, lo que hace en `caption` y «Añadir» como acción de texto a la derecha. Antes eran cuatro módulos, cada uno con su botón.
 */
@Composable
internal fun ProgramCycleTemplatesModule(
    templates: List<ProgramCycleTemplate>,
    onAdd: (templateId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    KpknModule(modifier = modifier.fillMaxWidth()) {
        templates.forEachIndexed { index, template ->
            if (index > 0) ProgramRowDivider(modifier = Modifier.padding(vertical = 12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(KpknSurface.inset),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = template.icon,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = WizardColors.text,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = template.name,
                        style = KpknType.bodyStrong,
                        color = WizardColors.text,
                    )
                    Text(
                        text = template.description,
                        style = KpknType.caption,
                        color = WizardColors.textMuted,
                    )
                }
                Spacer(Modifier.width(8.dp))
                // Con el nombre en la descripción accesible, «Añadir» no suena igual en cada fila.
                KpknTextAction(
                    text = "Añadir",
                    onClick = { onAdd(template.id) },
                    modifier = Modifier.semantics { contentDescription = "Añadir ${template.name}" },
                )
            }
        }
    }
}
