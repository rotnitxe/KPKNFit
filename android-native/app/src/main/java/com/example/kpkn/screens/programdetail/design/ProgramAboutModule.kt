package com.example.kpkn.screens.programdetail.design

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknModuleHeader
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknType

/**
 * «Sobre este programa» (KPKN_UI_V2 §1, P1): un módulo con la procedencia en una línea, dos líneas de notas y, abajo, «Ver más», que
 * abre la hoja con el detalle del plan. Si el programa viene del catálogo, «Ver cómo funciona» abre la hoja de lectura del plan
 * (`plan_details_info_button`).
 *
 * @param provenance la procedencia en una línea («Plan KPKN · Autor»); se corta con elipsis.
 * @param notes las notas del plan; se enseñan dos.
 * @param onOpenPlanInfo null si el programa no viene del catálogo.
 */
@Composable
fun ProgramAboutModule(
    provenance: String?,
    notes: List<String>,
    onOpenPlanInfo: (() -> Unit)?,
    onOpenMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    KpknModule(modifier = modifier) {
        KpknModuleHeader(label = "Sobre este programa")
        Spacer(Modifier.height(8.dp))
        // Sin procedencia que decir (null), la línea no se pinta y las notas suben.
        if (provenance != null) {
            Text(
                text = provenance,
                style = KpknType.bodyStrong,
                color = WizardColors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        notes.take(2).forEachIndexed { index, note ->
            if (provenance != null || index > 0) Spacer(Modifier.height(4.dp))
            Text(
                text = note,
                style = KpknType.caption,
                color = WizardColors.textMuted,
                // Dos líneas como máximo: a 130 % una nota larga se parte entre palabras antes que cortarse a media palabra.
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            KpknTextAction(text = "Ver más", onClick = onOpenMore)
            if (onOpenPlanInfo != null) {
                Spacer(Modifier.width(16.dp))
                KpknTextAction(
                    text = "Ver cómo funciona",
                    onClick = onOpenPlanInfo,
                    modifier = Modifier.testTag("plan_details_info_button"),
                )
            }
        }
    }
}
