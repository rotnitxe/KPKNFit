package com.example.kpkn.screens.technique.analysis.design

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.technique.analysis.CameraTip
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknModuleHeader
import com.example.kpkn.ui.design.KpknType

/** Etiqueta de prueba del módulo «Para medir mejor». */
const val ANALYSIS_TIPS_TAG = "analysis-tips"

/**
 * El módulo «Para medir mejor» (sin estado): hasta 3 consejos de cámara, uno por fila, cada uno con el aviso del análisis que lo
 * provoca («Pon el móvil de lado, perpendicular al movimiento»). Sin consejos no se pinta.
 */
@Composable
fun CameraTipsModule(
    tips: List<CameraTip>,
    modifier: Modifier = Modifier,
) {
    if (tips.isEmpty()) return
    KpknModule(modifier = modifier.testTag(ANALYSIS_TIPS_TAG)) {
        KpknModuleHeader("Para medir mejor")
        tips.take(3).forEachIndexed { index, tip ->
            if (index > 0) AnalysisDivider()
            Row(
                modifier = Modifier.padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AnalysisIconCircle(KpknLineIcons.Info, size = 36.dp, iconSize = 18.dp)
                Spacer(Modifier.width(14.dp))
                Text(text = tip.text, style = KpknType.body, color = WizardColors.text, modifier = Modifier.weight(1f))
            }
        }
    }
}
