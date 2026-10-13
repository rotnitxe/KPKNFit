package com.example.kpkn.screens.nutrition.design.logger

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.FoodCandidate
import com.example.kpkn.data.models.SearchConfidence
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknIconButton
import com.example.kpkn.ui.design.KpknMetric
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknType

/** Radio de la fila (el mismo que el del campo de texto): la fila pulsada se tiñe con él. */
private val RowShape = RoundedCornerShape(16.dp)

/**
 * Fila de un resultado de búsqueda (NUTRITION_V1 §3): nombre en `bodyStrong`, marca o nota en `caption`, kcal con su unidad a la derecha
 * y «+» para añadirlo. Toda la fila añade el alimento, y el «+» hace lo mismo. Al pulsar se tiñe de `modulePressed`. [basisGrams] es la
 * cantidad a la que se refieren las kcal («por 100 g»), para no presentar una cifra sin su base.
 */
@Composable
fun LoggerSearchRow(
    name: String,
    kcal: Int,
    basisGrams: Int,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
    caption: String? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 56.dp)
                .clip(RowShape)
                .background(if (pressed) KpknSurface.modulePressed else Color.Transparent, RowShape)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    role = Role.Button,
                    onClickLabel = "Agregar a la comida",
                    onClick = onAdd,
                )
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = name,
                    style = KpknType.bodyStrong,
                    color = WizardColors.text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!caption.isNullOrBlank()) {
                    Text(
                        text = caption,
                        style = KpknType.caption,
                        color = WizardColors.textMuted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                KpknMetric(value = kcal.toString(), unit = "kcal", style = KpknType.metricM)
                Text(
                    text = "por $basisGrams g",
                    style = KpknType.caption,
                    color = WizardColors.textFaint,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.width(4.dp))
            KpknIconButton(icon = KpknLineIcons.Plus, contentDescription = "Añadir", onClick = onAdd)
        }
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(1.dp)
                .background(KpknSurface.divider),
        )
    }
}

/**
 * Pie de un resultado para el registro: la marca si la tiene y, si la coincidencia no es alta, lo dice («Coincidencia media» o
 * «Coincidencia baja»). Nulo si no hay nada que decir.
 */
internal fun loggerSearchCaption(candidate: FoodCandidate): String? {
    val brand = candidate.food.brand?.trim()?.takeIf { it.isNotEmpty() }
    val match = when (candidate.confidence) {
        SearchConfidence.HIGH -> null
        SearchConfidence.MEDIUM -> "Coincidencia media"
        SearchConfidence.LOW -> "Coincidencia baja"
    }
    return listOfNotNull(brand, match).joinToString(" · ").ifEmpty { null }
}
