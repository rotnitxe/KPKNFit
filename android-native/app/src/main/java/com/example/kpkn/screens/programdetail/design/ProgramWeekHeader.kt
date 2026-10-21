package com.example.kpkn.screens.programdetail.design

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknIconButton
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknType

/**
 * Cabecera de la semana (P2): «Semana 3 · Acumulación» en `title`, con el nombre y la descripción debajo si los tiene. A la derecha, el
 * botón de editar, que abre la hoja de la semana; debajo, «Cambiar reparto», que abre SplitView. Sin estado.
 */
@Composable
internal fun ProgramWeekHeader(
    title: String,
    onEdit: () -> Unit,
    onChangeSplit: () -> Unit,
    modifier: Modifier = Modifier,
    name: String? = null,
    description: String? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = KpknType.title,
                    color = WizardColors.text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!name.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = name,
                        style = KpknType.bodyStrong,
                        color = WizardColors.text,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (!description.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = description,
                        style = KpknType.body,
                        color = WizardColors.textMuted,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            KpknIconButton(
                icon = KpknLineIcons.Edit,
                contentDescription = "Editar semana",
                onClick = onEdit,
            )
        }
        KpknTextAction(
            text = "Cambiar reparto",
            onClick = onChangeSplit,
            icon = KpknLineIcons.ChevronRight,
        )
    }
}
