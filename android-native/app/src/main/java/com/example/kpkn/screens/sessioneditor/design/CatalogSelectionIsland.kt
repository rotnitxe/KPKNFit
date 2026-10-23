package com.example.kpkn.screens.sessioneditor.design

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.KpknGlassVariant
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.components.kpknGlassOrFallback
import com.example.kpkn.ui.design.KpknIconButton
import com.example.kpkn.ui.design.KpknPrimaryPill
import com.example.kpkn.ui.design.KpknShape
import com.example.kpkn.ui.design.KpknTextAction
import com.example.kpkn.ui.design.KpknType
import dev.chrisbanes.haze.HazeState

/**
 * Resumen de lo elegido en una línea (SE6a): «Press banca, Remo con barra y 1 más». Con dos, «A y B»; con uno, su nombre.
 */
internal fun catalogSelectionSummary(names: List<String>): String = when (names.size) {
    0 -> ""
    1 -> names[0]
    2 -> "${names[0]} y ${names[1]}"
    else -> "${names[0]}, ${names[1]} y ${names.size - 2} más"
}

/** Técnica mantiene un candidato pendiente hasta pulsar «Elegir», también tras recrear la actividad. */
@Composable
internal fun CatalogTechniqueChoiceBar(name: String?, onConfirm: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().background(Color.Black).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("PARA EL ANÁLISIS DEL VÍDEO", style = KpknType.eyebrow, color = WizardColors.textMuted)
        if (name != null) Text(name, style = KpknType.bodyStrong, color = WizardColors.text)
        KpknPrimaryPill(
            text = "Elegir",
            enabled = name != null,
            onClick = onConfirm,
            modifier = Modifier.fillMaxWidth().testTag("catalog_technique_confirm"),
        )
    }
}

/**
 * Franja de la isla de selección (SE6a): entra con un fundido y 8 dp de subida, y sale con un fundido. Con movimiento reducido, solo
 * fundidos. Se pega abajo del área de la lista, sobre la barra de gestos. Sin estado: [visible] decide si está.
 */
@Composable
internal fun BoxScope.CatalogSelectionSlot(
    visible: Boolean,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        enter = if (reducedMotion) {
            fadeIn(animationSpec = tween(durationMillis = 120))
        } else {
            fadeIn(animationSpec = tween(durationMillis = 220)) +
                slideInVertically(animationSpec = tween(durationMillis = 220)) { fullHeight -> fullHeight / 4 }
        },
        exit = fadeOut(animationSpec = tween(durationMillis = if (reducedMotion) 120 else 140)),
    ) {
        content()
    }
}

/**
 * Isla inferior de la selección del catálogo (SE6a, SESSION_EDITOR_V1 §4): vidrio v2 con el resumen en una línea, «Añadir N» como
 * principal y, si la hay, «Superserie» como acción de texto. El botón del chevrón despliega [detail] (lo elegido, para ordenarlo o
 * quitarlo) encima de la fila. Sin estado: quien la usa decide si se ve (hay algo elegido y no hay teclado).
 *
 * @param count lo elegido: es el número de «Añadir N».
 * @param onSuperset nulo si hoy no se puede crear una superserie desde la selección.
 */
@Composable
internal fun CatalogSelectionIsland(
    count: Int,
    summary: String,
    listExpanded: Boolean,
    onToggleList: () -> Unit,
    onAdd: () -> Unit,
    onSuperset: (() -> Unit)?,
    hazeState: HazeState?,
    modifier: Modifier = Modifier,
    detail: @Composable () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .kpknGlassOrFallback(hazeState = hazeState, shape = KpknShape.island, variant = KpknGlassVariant.ISLAND)
            .padding(start = 8.dp, top = 8.dp, end = 8.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (listExpanded) {
            detail()
        }
        Text(
            text = summary,
            style = KpknType.caption,
            color = WizardColors.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            KpknIconButton(
                icon = KpknLineIcons.ChevronDown,
                contentDescription = if (listExpanded) "Ocultar los ejercicios elegidos" else "Ver los ejercicios elegidos",
                onClick = onToggleList,
                modifier = Modifier.graphicsLayer { rotationZ = if (listExpanded) 180f else 0f },
            )
            if (onSuperset != null) {
                KpknTextAction(text = "Superserie", onClick = onSuperset)
            }
            KpknPrimaryPill(
                text = "Añadir $count",
                onClick = onAdd,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
