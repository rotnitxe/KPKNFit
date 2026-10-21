package com.example.kpkn.screens.programdetail.design.structure

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.domain.text.SpanishPlurals
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.programdetail.design.numberWithUnit
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknIconButton
import com.example.kpkn.ui.design.KpknType

/**
 * Un bloque en la hoja de la ruta (P3c). El título es la fase del bloque («Acumulación») con su color; sin fase, el nombre. Debajo va la
 * línea de inicio («Bloque 2 · empieza el mar 3 nov»), y en su propia fila, a la derecha, el «−» y el «+» con las semanas. Tocar el título
 * lleva a la primera semana del bloque. Cada «−» y «+» llama a [onAdjust] con −1 o +1: es el ajuste de siempre, por bloque. No se puede
 * quitar la única semana de un bloque.
 */
@Composable
internal fun ProgramRouteBlockRow(
    block: ProgramRouteBlock,
    onFocus: () -> Unit,
    onAdjust: (deltaWeeks: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button, onClickLabel = "Ver ${block.name}", onClick = onFocus),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(block.color),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = block.phaseLabel ?: block.name,
                style = KpknType.bodyStrong,
                color = WizardColors.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            text = blockStartLine(block),
            style = KpknType.caption.copy(fontFeatureSettings = "'tnum' 1"),
            color = WizardColors.textMuted,
            modifier = Modifier.padding(start = 18.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BlockWeekStepper(name = block.name, weeks = block.weeks, onAdjust = onAdjust)
        }
    }
}

/** «−» · «3 semanas» · «+»: cada botón con su nombre para TalkBack. Con una sola semana, «−» queda apagado. */
@Composable
private fun BlockWeekStepper(
    name: String,
    weeks: Int,
    onAdjust: (deltaWeeks: Int) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (weeks > 1) {
            KpknIconButton(
                icon = KpknLineIcons.Minus,
                contentDescription = "Quitar una semana de $name",
                onClick = { onAdjust(-1) },
            )
        } else {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .semantics {
                        contentDescription = "Quitar una semana de $name, no queda ninguna"
                        disabled()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = KpknLineIcons.Minus,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = WizardColors.textFaint,
                )
            }
        }
        Text(
            text = numberWithUnit(SpanishPlurals.weeks(weeks)),
            style = KpknType.bodyStrong.copy(fontFeatureSettings = "'tnum' 1"),
            color = WizardColors.text,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.widthIn(min = 84.dp),
        )
        KpknIconButton(
            icon = KpknLineIcons.Plus,
            contentDescription = "Añadir una semana a $name",
            onClick = { onAdjust(1) },
        )
    }
}
