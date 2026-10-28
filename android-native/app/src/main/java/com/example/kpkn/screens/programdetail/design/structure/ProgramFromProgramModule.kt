package com.example.kpkn.screens.programdetail.design.structure

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknModuleHeader
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknType

/**
 * Una fila de «Del programa»: algo que se abre desde la pestaña (las cargas de referencia, las plantillas, las versiones, la ruta, las
 * fechas clave o los ciclos). [tag] es su etiqueta de prueba; con [enabled] = false va al 45 % y no se puede tocar.
 */
internal data class ProgramFromProgramItem(
    val title: String,
    val detail: String?,
    val icon: ImageVector,
    val tag: String,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

/**
 * Módulo «Del programa» de la pestaña Estructura: lo que antes iba en la rejilla de Herramientas, una fila por cada cosa, con su icono, su
 * título, su detalle y una flecha. Sin estado: las acciones llegan como lambdas.
 */
@Composable
internal fun ProgramFromProgramModule(
    items: List<ProgramFromProgramItem>,
    modifier: Modifier = Modifier,
) {
    KpknModule(modifier = modifier) {
        KpknModuleHeader(label = "Del programa")
        Spacer(Modifier.height(4.dp))
        items.forEachIndexed { index, item ->
            ProgramFromProgramRow(item = item, divider = index > 0)
        }
    }
}

/**
 * Una fila de 54 dp como mínimo: el icono en su círculo, el título y el detalle, y una flecha a la derecha. Un filete la separa de la
 * anterior. Apagada, va al 45 % y no responde al toque.
 */
@Composable
private fun ProgramFromProgramRow(item: ProgramFromProgramItem, divider: Boolean) {
    val interaction = remember { MutableInteractionSource() }
    Column(modifier = Modifier.fillMaxWidth()) {
        if (divider) {
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(KpknSurface.divider))
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 54.dp)
                .testTag(item.tag)
                .alpha(if (item.enabled) 1f else 0.45f)
                .semantics(mergeDescendants = true) { if (!item.enabled) disabled() }
                .then(
                    if (item.enabled) {
                        Modifier.clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = item.onClick)
                    } else {
                        Modifier
                    },
                )
                .padding(vertical = 4.dp, horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.06f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(imageVector = item.icon, contentDescription = null, modifier = Modifier.size(19.dp), tint = WizardColors.textMuted)
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = KpknType.bodyStrong,
                    color = WizardColors.text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                item.detail?.takeIf { it.isNotBlank() }?.let { detail ->
                    Spacer(Modifier.height(2.dp))
                    Text(text = detail, style = KpknType.caption, color = WizardColors.textFaint)
                }
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = KpknLineIcons.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = WizardColors.textFaint,
            )
        }
    }
}
