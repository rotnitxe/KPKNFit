package com.example.kpkn.screens.workout.design
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.unit.sp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.screens.workout.workoutHeaderTitleFontScale
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknIconButton
import com.example.kpkn.ui.design.KpknShape
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknType
import androidx.compose.material3.Text
import java.util.Locale

/** Tamaño del nombre del ejercicio: `display` (32 sp) que se encoge, nunca por debajo de 24 sp. */
private const val NameMaxSp = 32f
private const val NameMinSp = 24f

/**
 * Cabecera del ejercicio (LS2a, §2 punto 3). Una fila con el eyebrow («EJERCICIO 3 DE 6 · TORSO A») a la izquierda y, a la derecha,
 * «Técnica» (si [onTechnique] existe), «Cambiar ejercicio» (si [onChangeExercise] existe) y «Opciones del ejercicio», que entrega su
 * posición por [onOptions]. Debajo, el nombre en `display` con dos líneas como máximo; luego [detailLine] (chips y etiquetas) en
 * `caption`; la fila de datos [targetLine] y [lastLine] en `caption` tabular, cada parte solo si hay dato; y el protocolo en una
 * píldora de texto, sin emoji. Sin estado: el menú lo abre quien recibe el ancla.
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
    val scale = workoutHeaderTitleFontScale(name.length)
    val nameSp = (NameMaxSp * scale).coerceIn(NameMinSp, NameMaxSp)
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
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
        var keep by remember(name, nameSp) { mutableStateOf(name.length) }
        val shownName = if (keep >= name.length) name else name.take(keep).trimEnd() + "…"
        Text(
            text = shownName,
            style = KpknType.display.copy(fontSize = nameSp.sp, lineHeight = (nameSp + 4f).sp),
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
        if (detailLine != null) {
            Text(
                text = detailLine,
                style = KpknType.caption,
                color = WizardColors.textMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
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
        if (protocolLabel != null) {
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .clip(KpknShape.pill)
                    .background(KpknSurface.inset, KpknShape.pill)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
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
