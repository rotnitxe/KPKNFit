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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.programdetail.design.ProgramPhase
import com.example.kpkn.ui.components.icons.KpknLineIcons
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
 * e-map, módulo «Mapa»: la cabecera con «Fechas» (icono de calendario, en `--muted`), la barra `.tline` (34 dp, radio 12, 2 dp entre
 * tramos, 12 dp por encima) con un tramo por bloque, de ancho proporcional a sus semanas y del color de su fase, y debajo el inicio, la
 * semana en curso y el fin en `.cap` tabular. Sin estado: los tramos y las fechas llegan ya calculados.
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
    StructureModule(modifier = modifier.testTag("estructura_mapa")) {
        StructureModuleHeader(
            label = "Mapa",
            action = { StructureAction(text = "Fechas", onClick = onDates, leadingIcon = KpknLineIcons.Calendar, faint = true) },
        )
        if (segments.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp)
                    .clip(RoundedCornerShape(12.dp)),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                segments.forEach { segment ->
                    Box(
                        modifier = Modifier
                            .weight(segment.weeks.toFloat())
                            .fillMaxHeight()
                            .background(structurePhaseColor(segment.phase)),
                        contentAlignment = Alignment.Center,
                    ) {
                        segment.label?.let { label ->
                            Text(
                                text = label,
                                style = StructureTimelineStyle,
                                color = StructureOnInk,
                                maxLines = 1,
                                overflow = TextOverflow.Clip,
                                softWrap = false,
                            )
                        }
                    }
                }
            }
        }
        if (startLabel != null || nowLabel != null || endLabel != null) {
            Spacer(Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                listOfNotNull(startLabel, nowLabel, endLabel).forEach { label ->
                    Text(
                        text = label,
                        style = StructureCaptionStyle.tabular(),
                        color = StructureFaint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
