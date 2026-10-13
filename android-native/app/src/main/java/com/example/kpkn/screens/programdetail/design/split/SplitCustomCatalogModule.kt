package com.example.kpkn.screens.programdetail.design.split

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
import com.example.kpkn.data.splits.SplitTemplate
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknModuleHeader
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknSecondaryPill
import com.example.kpkn.ui.design.KpknType
import com.example.kpkn.ui.design.kpknEntrance

/**
 * Módulo del reparto propio (P5, sustituye a `CustomSplitCatalogCard`). Sin reparto guardado ofrece crearlo; con uno, su nombre, su
 * diagrama y «Editar» y «Aplicar». [custom] es null mientras el reparto propio no tenga días. Sin estado.
 */
@Composable
internal fun SplitCustomCatalogModule(
    custom: SplitTemplate?,
    startDay: Int,
    onEdit: () -> Unit,
    onApply: () -> Unit,
    modifier: Modifier = Modifier,
) {
    KpknModule(modifier = modifier.fillMaxWidth().kpknEntrance("split-catalog", 0)) {
        KpknModuleHeader(label = "Reparto propio")
        Spacer(Modifier.height(10.dp))
        if (custom == null) {
            Text(
                text = "Crea tu propio reparto",
                style = KpknType.bodyStrong,
                color = WizardColors.text,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Parte de una semana en blanco y define qué hace cada día.",
                style = KpknType.caption,
                color = WizardColors.textMuted,
            )
            Spacer(Modifier.height(14.dp))
            KpknPrimaryPill(
                text = "Crear reparto",
                onClick = onEdit,
                modifier = Modifier.fillMaxWidth(),
                tag = "split_crear_propio",
            )
        } else {
            Text(
                text = custom.name,
                style = KpknType.bodyStrong,
                color = WizardColors.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${splitDaysLabel(splitTrainingDays(custom.pattern))} a la semana",
                style = KpknType.caption,
                color = WizardColors.text,
            )
            Spacer(Modifier.height(14.dp))
            SplitWeekDiagram(pattern = custom.pattern, startDay = startDay)
            Spacer(Modifier.height(14.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                KpknSecondaryPill(
                    text = "Editar",
                    onClick = onEdit,
                    modifier = Modifier.weight(1f),
                    tag = "split_editar_propio",
                )
                Spacer(Modifier.width(8.dp))
                KpknSecondaryPill(
                    text = "Aplicar",
                    onClick = onApply,
                    modifier = Modifier.weight(1f),
                    tag = "split_aplicar_propio",
                )
            }
        }
    }
}
