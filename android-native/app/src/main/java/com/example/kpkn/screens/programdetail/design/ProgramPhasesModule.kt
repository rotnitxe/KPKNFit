package com.example.kpkn.screens.programdetail.design

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.onboarding.design.WizardFonts
import java.util.Locale

/** Un bloque del módulo «Fases» (P2): su eyebrow en el color de su fase y sus semanas en orden. */
internal data class ProgramPhaseBlockUi(
    val id: String,
    val eyebrow: String,
    val phase: ProgramPhase?,
    val weeks: List<ProgramPhaseWeekUi>,
)

/**
 * Los bloques de la hoja «Semanas» (lienzo v6, s-weeks): cada bloque con su rótulo (`.mgroup`) en el color de su fase y debajo su fila de
 * semanas, directamente en la hoja, sin módulo ni cabecera «Fases». [onAddWeek], si hay, añade un «+» al final de cada bloque. Sin estado:
 * la elección y la pulsación larga van a la pantalla.
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
    // Las fases son círculos interactivos: el resumen va en la columna para quien no los recorre uno a uno.
    Column(modifier = modifier.fillMaxWidth().semantics { contentDescription = "Fases del programa. ${phasesSummary(blocks)}" }) {
        blocks.forEachIndexed { index, block ->
            // `.mgroup`: 16 dp arriba (tras el subtítulo, que deja 12, faltan 4) y 8 hasta su fila.
            Spacer(Modifier.height(if (index == 0) 4.dp else 16.dp))
            PhaseEyebrow(block)
            Spacer(Modifier.height(8.dp))
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

/**
 * Resumen de las fases para lectores de pantalla (P7): cada bloque con sus semanas y la que está en curso, separados por «;».
 */
internal fun phasesSummary(blocks: List<ProgramPhaseBlockUi>): String =
    blocks.joinToString(separator = "; ") { block ->
        val count = block.weeks.size
        val weeks = if (count == 1) "1 semana" else "$count semanas"
        val current = block.weeks.firstOrNull { it.isCurrent }?.let { ", semana ${it.number} en curso" }.orEmpty()
        "${block.eyebrow.lowercase(Locale("es"))}, $weeks$current"
    }

/** El rótulo de un bloque (`.mgroup`): su nombre en mayúsculas, en el color de su fase (o en `textFaint` si no se sabe). */
@Composable
private fun PhaseEyebrow(block: ProgramPhaseBlockUi) {
    Text(
        text = block.eyebrow.uppercase(Locale("es")),
        style = PhaseGroupStyle,
        color = block.phase?.color ?: WizardColors.textFaint,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(horizontal = 2.dp),
    )
}

/** `.mgroup`: Inter SemiBold 10,5 / 14 con 1,3 sp de espaciado. */
private val PhaseGroupStyle = TextStyle(
    fontFamily = WizardFonts.body,
    fontWeight = FontWeight.SemiBold,
    fontSize = 10.5.sp,
    lineHeight = 14.sp,
    letterSpacing = 1.3.sp,
)
