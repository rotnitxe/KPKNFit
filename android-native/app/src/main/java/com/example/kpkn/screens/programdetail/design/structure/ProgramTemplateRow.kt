package com.example.kpkn.screens.programdetail.design.structure

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.text.SpanishPlurals
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.programdetail.design.numberWithUnit
import com.example.kpkn.ui.design.KpknModule
import com.example.kpkn.ui.design.KpknType

/**
 * Una tarjeta de la biblioteca de plantillas (P3c): el nombre, la descripción, «3 bloques · 12 semanas» y los filtros que cumple. [key] es
 * lo que vuelve al tocarla: quien la pinta sabe a qué plantilla o protocolo corresponde.
 */
internal data class ProgramLibraryCard(
    val key: String,
    val name: String,
    val description: String,
    val caption: String?,
    val traits: ProgramLibraryTraits,
)

/**
 * Un módulo por plantilla o protocolo (P3c): el nombre en `bodyStrong`, la descripción en dos líneas como mucho («…» al cortar) y la
 * línea de bloques y semanas en `caption`. Tocarlo abre la confirmación (o la ficha del protocolo).
 */
@Composable
internal fun ProgramLibraryRow(
    card: ProgramLibraryCard,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    KpknModule(
        modifier = modifier,
        onClick = onClick,
        onClickLabel = "Ver ${card.name}",
    ) {
        Text(
            text = card.name,
            style = KpknType.bodyStrong,
            color = WizardColors.text,
        )
        if (card.description.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = card.description,
                style = KpknType.caption,
                color = WizardColors.textMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        card.caption?.let { caption ->
            Spacer(Modifier.height(6.dp))
            Text(
                text = caption,
                style = KpknType.caption.copy(fontFeatureSettings = "'tnum' 1"),
                color = WizardColors.textMuted,
            )
        }
    }
}

/** «3 bloques · 12 semanas». Sin bloques ni semanas no hay nada que decir: devuelve null y la línea no se pinta. */
internal fun programBlocksAndWeeksLine(blocks: Int, weeks: Int): String? {
    val parts = listOfNotNull(
        blocks.takeIf { it > 0 }?.let { numberWithUnit(SpanishPlurals.blocks(it)) },
        weeks.takeIf { it > 0 }?.let { numberWithUnit(SpanishPlurals.weeks(it)) },
    )
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}
