package com.example.kpkn.screens.nutrition.design.logger

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.FoodCandidate
import com.example.kpkn.data.models.FoodItem
import com.example.kpkn.domain.nutrition.HouseholdPortions
import com.example.kpkn.domain.nutrition.scaleFoodByPortion
import com.example.kpkn.ui.components.icons.KpknLineIcons
import kotlin.math.roundToInt

/**
 * Fila de un resultado de búsqueda (lienzo `sres`): el nombre y, debajo, «125 g · 1 envase · KPKN»; a la derecha las kcal de esa
 * ración en Syne, la estrella del favorito y el «+». Tocar la fila abre la ficha ([onOpen]); el «+» ([onAdd]) añade con la ración
 * mostrada. Con [onToggleFavorite] nulo no se pinta la estrella. [divider] pone el filete de arriba (la primera fila de un módulo no lo
 * lleva).
 */
@Composable
fun LoggerSearchRow(
    name: String,
    caption: String,
    kcal: Int,
    favorite: Boolean,
    onToggleFavorite: (() -> Unit)?,
    onAdd: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    divider: Boolean = true,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (divider) LoggerRowDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 60.dp)
                .clickable(role = Role.Button, onClickLabel = "Ver $name", onClick = onOpen)
                .padding(horizontal = 2.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = name, style = LoggerText.rowTitle, color = LoggerColors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (caption.isNotBlank()) {
                    Text(
                        text = caption,
                        style = LoggerText.cap,
                        color = LoggerColors.faint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Text(text = kcal.toString(), style = LoggerText.figure(14), color = LoggerColors.ink, maxLines = 1)
            // Las zonas táctiles son de 48 dp; el hueco se descuenta para que lo visible quede a los 12 dp del lienzo.
            if (onToggleFavorite != null) {
                Spacer(Modifier.width(6.dp))
                LoggerIconButton(
                    icon = if (favorite) LoggerIcons.StarFilled else LoggerIcons.Star,
                    contentDescription = if (favorite) "Quitar de favoritos" else "Añadir a favoritos",
                    onClick = onToggleFavorite,
                    tint = if (favorite) LoggerColors.ene else LoggerColors.dim,
                    size = 36.dp,
                    iconSize = 18.dp,
                )
                Spacer(Modifier.width(2.dp))
            } else {
                Spacer(Modifier.width(8.dp))
            }
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clickable(role = Role.Button, onClick = onAdd)
                    .semantics { contentDescription = "Añadir $name" },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.063f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(KpknLineIcons.Plus, contentDescription = null, modifier = Modifier.size(18.dp), tint = LoggerColors.ink)
                }
            }
        }
    }
}

/** La ración con la que un resultado se añade con el «+»: sus gramos, cómo se dice («125 g · 1 envase») y sus kcal. */
internal data class LoggerSearchPortion(val grams: Double, val label: String, val kcal: Int)

/**
 * La ración de [food] al buscar [query]: la MISMA que añade el «+» ([HouseholdPortions.eatenGramsForSearchPick]), con los gramos primero
 * y, si el alimento tiene unidad natural, cuántas son («125 g · 1 envase»). En «ml» si el alimento se mide en volumen. Las kcal son las de
 * esos gramos.
 */
internal fun loggerSearchPortion(food: FoodItem, query: String): LoggerSearchPortion {
    // Una receta sin peso final se mide en raciones: sus kcal son las de una ración, no las de «1 g».
    if (food.recipe != null && food.portionGrams == null) {
        return LoggerSearchPortion(grams = food.servingSize.takeIf { it > 0.0 } ?: 1.0, label = "1 ración", kcal = food.calories.roundToInt())
    }
    val identity = HouseholdPortions.identityForSearchPick(food, query)
    val grams = HouseholdPortions.eatenGramsForSearchPick(identity, query)
    val kcal = scaleFoodByPortion(identity, amountGrams = grams).calories.roundToInt()
    val volume = identity.unit.trim().lowercase() in setOf("ml", "l")
    val mass = "${grams.roundToInt()} ${if (volume) "ml" else "g"}"
    val natural = loggerUnits(identity.name, identity, query).firstOrNull { it.kind == LoggerUnitKind.NATURAL }
    val label = if (natural == null) {
        mass
    } else {
        val value = loggerUnitValue(grams, natural)
        "$mass · ${loggerFormatValue(value, natural)} ${loggerUnitNoun(value, natural)}"
    }
    return LoggerSearchPortion(grams = grams, label = label, kcal = kcal)
}

/** De dónde sale el alimento (S-D6): su marca; si no tiene, «Receta», «Mío» (alimento propio) o «KPKN». */
internal fun loggerSearchOrigin(food: FoodItem): String =
    food.brand?.trim()?.takeIf { it.isNotEmpty() } ?: when {
        food.recipe != null -> "Receta"
        food.isCustom -> "Mío"
        else -> "KPKN"
    }

/** El pie de la fila: «125 g · 1 envase · KPKN». */
internal fun loggerSearchCaption(candidate: FoodCandidate, query: String): String =
    "${loggerSearchPortion(candidate.food, query).label} · ${loggerSearchOrigin(candidate.food)}"
