package com.example.kpkn.screens.workout.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknIconButton
import com.example.kpkn.ui.design.KpknShape
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknType
import androidx.compose.material3.Text
import java.util.Locale

/**
 * Cabecera del ejercicio (LS2a, §2 punto 3; LS4 la compacta). Una fila con el eyebrow («EJERCICIO 3 DE 6 · TORSO A») a la izquierda y, a la
 * derecha, «Técnica» (si [onTechnique] existe), «Cambiar ejercicio» (si [onChangeExercise] existe) y «Opciones del ejercicio», que entrega
 * su posición por [onOptions]. Debajo, el nombre en `display` con dos líneas como máximo; luego la línea de detalle (chips y etiquetas) y, a su
 * derecha, el protocolo en una píldora de texto, sin emoji (LS4: ya no ocupa una línea propia); la fila de datos [targetLine] y [lastLine]
 * en `caption` tabular, cada parte solo si hay dato. Sin estado: el menú lo abre quien recibe el ancla.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WorkoutExerciseHeader(
    eyebrow: String,
    name: String,
    detailLine: String?,
    targetLine: String?,
    lastLine: String?,
    protocolLabel: String?,
    onOptions: (Rect) -> Unit,
    modifier: Modifier = Modifier,
    onTechnique: (() -> Unit)? = null,
    onChangeExercise: (() -> Unit)? = null,
) {
    var optionsBounds by remember { mutableStateOf(Rect.Zero) }
    // Por encima de la barra de progreso: su zona táctil sobresale hasta aquí y los botones de esta fila ganan el toque.
    Column(modifier = modifier.zIndex(1f).fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = eyebrow.uppercase(Locale("es")),
                style = KpknType.eyebrow,
                color = WizardColors.textMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (onTechnique != null) {
                KpknIconButton(icon = KpknLineIcons.Info, contentDescription = "Técnica", onClick = onTechnique)
            }
            if (onChangeExercise != null) {
                KpknIconButton(
                    icon = KpknLineIcons.Repeat,
                    contentDescription = "Cambiar ejercicio",
                    onClick = onChangeExercise,
                )
            }
            KpknIconButton(
                icon = KpknLineIcons.More,
                contentDescription = "Opciones del ejercicio",
                onClick = { onOptions(optionsBounds) },
                modifier = Modifier.onGloballyPositioned { optionsBounds = it.boundsInWindow() },
            )
        }
        // Dos líneas como máximo y sin cortar palabras: si el nombre no cabe, se recorta en el último espacio visible y se pone «…».
        var keep by remember(name) { mutableStateOf(name.length) }
        val shownName = if (keep >= name.length) name else name.take(keep).trimEnd() + "…"
        Text(
            text = shownName,
            style = KpknType.title,
            color = WizardColors.text,
            maxLines = 2,
            overflow = TextOverflow.Clip,
            onTextLayout = { layout ->
                if (layout.hasVisualOverflow) {
                    val visibleEnd = layout.getLineEnd(layout.lineCount - 1, visibleEnd = true)
                    val space = shownName.lastIndexOf(' ', startIndex = (visibleEnd - 1).coerceAtLeast(0))
                    if (space > 0 && space < keep) keep = space
                }
            },
            modifier = Modifier.semantics { heading() },
        )
        if (detailLine != null || protocolLabel != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (detailLine != null) {
                    Text(
                        text = detailLine,
                        style = KpknType.caption,
                        color = WizardColors.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                if (protocolLabel != null) {
                    Box(
                        modifier = Modifier
                            .clip(KpknShape.pill)
                            .background(KpknSurface.inset, KpknShape.pill)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(
                            text = protocolLabel,
                            style = KpknType.chip,
                            color = WizardColors.textMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        val dataParts = listOfNotNull(targetLine, lastLine)
        if (dataParts.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                dataParts.forEach { part ->
                    Text(
                        text = part,
                        style = KpknType.caption.copy(fontFeatureSettings = "tnum"),
                        color = WizardColors.textMuted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
