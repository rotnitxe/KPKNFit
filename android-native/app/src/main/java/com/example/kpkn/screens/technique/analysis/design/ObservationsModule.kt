package com.example.kpkn.screens.technique.analysis.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
    embedded: Boolean = false,
) {
    if (items.isEmpty()) return
    val content: @Composable () -> Unit = {
        KpknModuleHeader("Observaciones")
        Spacer(Modifier.height(6.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items.take(3).forEachIndexed { index, item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0x0DFFFFFF))
                        .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(14.dp))
                        .padding(start = 12.dp, top = 8.dp, end = 6.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFF7CF73)),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = item.text,
                        style = KpknType.body,
                        color = WizardColors.text,
                        modifier = Modifier.weight(1f).padding(vertical = 4.dp),
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
    if (embedded) {
        Column(modifier = modifier.fillMaxWidth().testTag(ANALYSIS_OBSERVATIONS_TAG)) { content() }
    } else {
        KpknModule(modifier = modifier.testTag(ANALYSIS_OBSERVATIONS_TAG)) { content() }
    }
}
