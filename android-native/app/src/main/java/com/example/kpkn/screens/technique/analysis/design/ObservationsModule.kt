package com.example.kpkn.screens.technique.analysis.design

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.technique.analysis.ObservationUi
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknModuleHeader
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknType

/** Etiqueta de prueba del módulo «Observaciones»; cada «Ver» lleva la suya con su posición (`analysis-observation-see-0`). */
const val ANALYSIS_OBSERVATIONS_TAG = "analysis-observations"

fun analysisObservationSeeTag(position: Int): String = "analysis-observation-see-$position"

/**
 * El módulo «Observaciones» (sin estado): hasta 3 filas, cada una con el texto del motor TAL CUAL (en `body`) y, a la derecha, «Ver»,
 * que salta al instante de la observación ([onSee]). Sin observaciones no se pinta. Una observación sin instante ni repetición no
 * tiene «Ver».
 */
@Composable
fun ObservationsModule(
    items: List<ObservationUi>,
    onSee: (ObservationUi) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    KpknModule(modifier = modifier.testTag(ANALYSIS_OBSERVATIONS_TAG)) {
        KpknModuleHeader("Observaciones")
        items.take(3).forEachIndexed { index, item ->
            if (index > 0) AnalysisDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.text,
                    style = KpknType.body,
                    color = WizardColors.text,
                    modifier = Modifier.weight(1f).padding(vertical = 12.dp),
                )
                if (item.canSee) {
                    Spacer(Modifier.width(8.dp))
                    KpknTextAction(
                        text = "Ver",
                        onClick = { onSee(item) },
                        modifier = Modifier
                            .defaultMinSize(minWidth = 48.dp)
                            .testTag(analysisObservationSeeTag(index))
                            .semantics { contentDescription = "Ver en el vídeo: ${item.text}" },
                    )
                }
            }
        }
    }
}
