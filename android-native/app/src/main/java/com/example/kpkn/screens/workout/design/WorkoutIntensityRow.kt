package com.example.kpkn.screens.workout.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.design.KpknChip
import com.example.kpkn.ui.design.KpknShape
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknType
import kotlin.math.abs

/** Escala de la intensidad de la serie: RIR (repeticiones en reserva) o RPE (esfuerzo percibido). */
enum class WorkoutIntensityScale { RIR, RPE }

/** Una opción de la fila: el texto del chip y el valor que recibe `onSelect`. */
private data class IntensityOption(val label: String, val value: Double)

private val RIR_OPTIONS = listOf(
    IntensityOption("0", 0.0),
    IntensityOption("1", 1.0),
    IntensityOption("2", 2.0),
    IntensityOption("3", 3.0),
    IntensityOption("4+", 4.0),
)

private val RPE_OPTIONS = listOf(
    IntensityOption("6", 6.0),
    IntensityOption("6,5", 6.5),
    IntensityOption("7", 7.0),
    IntensityOption("7,5", 7.5),
    IntensityOption("8", 8.0),
    IntensityOption("8,5", 8.5),
    IntensityOption("9", 9.0),
    IntensityOption("9,5", 9.5),
    IntensityOption("10", 10.0),
)

/**
 * Intensidad de la serie (KPKN LS1): la escala («RIR» o «RPE») en `caption` y, a continuación, un chip por valor, con desplazamiento
 * horizontal si no cabe. El elegido va relleno y, al abrir, la fila se mueve hasta él. «Fallo» va aparte, al final, en `danger` cuando
 * está activo.
 *
 * La pieza solo pinta [selected] y [failure]: quien llama apaga el valor cuando hay fallo. «4+» vale 4,0 y se marca con cualquier
 * valor de 4 o más.
 */
@Composable
fun WorkoutIntensityRow(
    scale: WorkoutIntensityScale,
    selected: Double?,
    failure: Boolean,
    onSelect: (Double) -> Unit,
    onToggleFailure: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = if (scale == WorkoutIntensityScale.RIR) RIR_OPTIONS else RPE_OPTIONS
    fun isChosen(option: IntensityOption): Boolean = when {
        selected == null -> false
        scale == WorkoutIntensityScale.RIR && option.value == 4.0 -> selected >= 4.0
        else -> abs(selected - option.value) < 0.01
    }
    val listState = rememberLazyListState()
    // Al abrir, la fila se desplaza para que el elegido se vea, con un chip de margen a la izquierda.
    val chosenIndex = options.indexOfFirst { isChosen(it) }
    LaunchedEffect(scale) {
        if (chosenIndex > 0) listState.scrollToItem(chosenIndex - 1)
    }
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = scale.name,
            style = KpknType.caption,
            color = WizardColors.textMuted,
            maxLines = 1,
        )
        Spacer(Modifier.width(10.dp))
        LazyRow(
            state = listState,
            modifier = Modifier.weight(1f).fadeScrollableEdges(listState),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items(options) { option ->
                KpknChip(
                    text = option.label,
                    selected = isChosen(option),
                    onClick = { onSelect(option.value) },
                )
            }
            item {
                FailureChip(selected = failure, onClick = onToggleFailure)
            }
        }
    }
}

/**
 * «Fallo»: el mismo chip que [KpknChip] (32 dp, píldora, zona de 48 dp), pero en `danger` cuando está activo, que [KpknChip] no admite.
 */
@Composable
private fun FailureChip(selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val fill by animateColorAsState(
        targetValue = if (selected) WizardColors.danger else KpknSurface.inset,
        animationSpec = tween(durationMillis = 180),
        label = "failureChipFill",
    )
    val ink = if (selected) WizardColors.ctaContent else WizardColors.text
    val edge = if (selected) Color.Transparent else Color.White.copy(alpha = 0.08f)
    Box(
        modifier = Modifier
            .defaultMinSize(minHeight = 48.dp)
            .toggleable(
                value = selected,
                role = Role.Checkbox,
                interactionSource = interaction,
                indication = null,
                onValueChange = { onClick() },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .height(32.dp)
                .clip(KpknShape.pill)
                .background(fill, KpknShape.pill)
                .border(width = 1.dp, color = edge, shape = KpknShape.pill)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Fallo",
                style = KpknType.chip,
                color = ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
