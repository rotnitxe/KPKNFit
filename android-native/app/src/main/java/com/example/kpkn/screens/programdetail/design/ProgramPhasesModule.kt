package com.example.kpkn.screens.programdetail.design

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknModuleHeader
import com.example.kpkn.ui.design.KpknType
import java.util.Locale

/** Un bloque del módulo «Fases» (P2): su eyebrow en el color de su fase y sus semanas en orden. */
internal data class ProgramPhaseBlockUi(
    val id: String,
    val eyebrow: String,
    val phase: ProgramPhase?,
    val weeks: List<ProgramPhaseWeekUi>,
)

/**
 * Módulo «Fases» de la pestaña Semana (P2, KPKN_UI_V2 §1): un módulo con cada bloque del programa. El eyebrow de cada bloque va en el
 * color de su fase (Competición lleva bandera) y debajo está su fila de semanas. [onAddWeek], si hay, añade un «+» al final de cada
 * bloque. Sin estado: la elección y la pulsación larga van a la pantalla.
 */
@Composable
internal fun ProgramPhasesModule(
    blocks: List<ProgramPhaseBlockUi>,
    selectedWeekId: String?,
    onSelectWeek: (String) -> Unit,
    onLongPressWeek: (weekId: String, anchor: Rect) -> Unit,
    modifier: Modifier = Modifier,
    onAddWeek: ((blockId: String) -> Unit)? = null,
) {
    KpknModule(modifier = modifier.fillMaxWidth()) {
        KpknModuleHeader(label = "Fases")
        blocks.forEachIndexed { index, block ->
            Spacer(Modifier.height(if (index == 0) 12.dp else 18.dp))
            PhaseEyebrow(block)
            Spacer(Modifier.height(10.dp))
            val add: (() -> Unit)? = onAddWeek?.let { callback -> { callback(block.id) } }
            ProgramWeekStrip(
                weeks = block.weeks,
                selectedWeekId = selectedWeekId,
                onSelectWeek = onSelectWeek,
                onLongPressWeek = onLongPressWeek,
                onAdd = add,
            )
        }
    }
}

/** El eyebrow de un bloque: su nombre en mayúsculas, en el color de su fase (o en `textMuted` si no se sabe). */
@Composable
private fun PhaseEyebrow(block: ProgramPhaseBlockUi) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (block.phase == ProgramPhase.COMPETITION) {
            Icon(
                imageVector = KpknLineIcons.Flag,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = WizardColors.danger,
            )
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = block.eyebrow.uppercase(Locale("es")),
            style = KpknType.eyebrow,
            color = block.phase?.color ?: WizardColors.textMuted,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
