package com.example.kpkn.screens.programdetail.design.structure

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.programdetail.design.ProgramPhase
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknModuleHeader
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknType
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Un tramo del mapa: un bloque, con sus semanas (su ancho), su fase (su color) y su nombre, si cabe dentro. */
internal data class ProgramMapSegment(
    val id: String,
    val label: String?,
    val weeks: Int,
    val phase: ProgramPhase?,
)

/**
 * Los tramos del mapa, en el orden del programa: uno por bloque con semanas. El nombre de la fase solo va en los bloques de más de una
 * semana, porque en uno de una sola no cabe; un bloque sin semanas no pinta nada. Pura.
 */
internal fun programMapSegments(blocks: List<ProgramBlockUi>): List<ProgramMapSegment> =
    blocks.mapNotNull { block ->
        val weeks = block.weeks.size
        if (weeks <= 0) return@mapNotNull null
        ProgramMapSegment(
            id = block.id,
            label = if (weeks > 1) block.phase?.label else null,
            weeks = weeks,
            phase = block.phase,
        )
    }

/** «Hoy · semana 5»: la semana en curso, con su número global; null si ninguna está en curso. Pura. */
internal fun programMapNowLabel(blocks: List<ProgramBlockUi>): String? {
    val current = blocks.firstNotNullOfOrNull { block -> block.weeks.firstOrNull { it.isCurrent } } ?: return null
    return "Hoy · semana ${current.number}"
}

/** «6 oct»: el día y el mes abreviado en español, sin año ni punto final; null si no hay fecha. Pura. */
internal fun programMapDateLabel(date: LocalDate?): String? = date?.format(MapDateFormat)

/** Día y mes abreviado: «d MMM» en español. */
private val MapDateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", Locale("es"))

/**
 * El mapa del programa en la pestaña Estructura: una barra con un tramo por bloque, de ancho proporcional a sus semanas y del color de su
 * fase, y debajo el inicio, la semana en curso y el fin. «Fechas» abre el calendario del programa. Sin estado: los tramos y las fechas llegan
 * ya calculados.
 */
@Composable
internal fun ProgramMapModule(
    segments: List<ProgramMapSegment>,
    startLabel: String?,
    nowLabel: String?,
    endLabel: String?,
    onDates: () -> Unit,
    modifier: Modifier = Modifier,
) {
    KpknModule(modifier = modifier.testTag("estructura_mapa")) {
        KpknModuleHeader(
            label = "Mapa",
            action = { KpknTextAction(text = "Fechas", onClick = onDates, icon = KpknLineIcons.Calendar) },
        )
        if (segments.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .clip(RoundedCornerShape(8.dp)),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                segments.forEach { segment ->
                    Box(
                        modifier = Modifier
                            .weight(segment.weeks.toFloat())
                            .fillMaxHeight()
                            .background((segment.phase?.color ?: WizardColors.textFaint).copy(alpha = 0.85f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        segment.label?.let { label ->
                            Text(
                                text = label,
                                style = KpknType.caption,
                                color = Color.Black.copy(alpha = 0.8f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
        if (startLabel != null || nowLabel != null || endLabel != null) {
            Spacer(Modifier.height(if (segments.isNotEmpty()) 6.dp else 10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                listOfNotNull(startLabel, nowLabel, endLabel).forEach { MapDateText(text = it) }
            }
        }
    }
}

/** Una fecha o el aviso de la semana en curso, bajo la barra: `caption` en `textMuted`, con cifras tabulares. */
@Composable
private fun MapDateText(text: String) {
    Text(
        text = text,
        style = KpknType.caption.copy(fontFeatureSettings = "'tnum' 1"),
        color = WizardColors.textMuted,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
