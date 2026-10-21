package com.example.kpkn.screens.programdetail.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknSurface
import com.example.kpkn.ui.design.KpknType

/**
 * Una semana de una fila (P2): su número global desde 1 y su estado. [isLoop] y [isCompetition] ponen una marca pequeña (`Repeat` o
 * `Flag`) en el círculo; la fila de Semana no las usa, el módulo de bloque de Estructura (P3b) sí.
 */
internal data class ProgramPhaseWeekUi(
    val id: String,
    val number: Int,
    val isCurrent: Boolean,
    val isDone: Boolean,
    val isLoop: Boolean = false,
    val isCompetition: Boolean = false,
)

/**
 * Fila de semanas (P2): círculos de 40 dp en una `LazyRow`. La elegida se rellena de tinta con el número en negro; la actual lleva anillo
 * de tinta; las hechas, un punto `done` debajo. Al entrar, la elegida queda visible con dos semanas de margen, como antes. [onAdd], si hay,
 * añade un «+» al final. Una pulsación larga entrega la posición del círculo para su menú.
 */
@Composable
internal fun ProgramWeekStrip(
    weeks: List<ProgramPhaseWeekUi>,
    selectedWeekId: String?,
    onSelectWeek: (String) -> Unit,
    onLongPressWeek: (weekId: String, anchor: Rect) -> Unit,
    modifier: Modifier = Modifier,
    onAdd: (() -> Unit)? = null,
) {
    val listState = rememberLazyListState()
    val selectedIndex = weeks.indexOfFirst { it.id == selectedWeekId }
    LaunchedEffect(selectedIndex) {
        if (selectedIndex >= 0) listState.animateScrollToItem(maxOf(0, selectedIndex - 2))
    }
    LazyRow(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(weeks, key = { it.id }) { week ->
            ProgramWeekCircle(
                week = week,
                isSelected = week.id == selectedWeekId,
                onClick = { onSelectWeek(week.id) },
                onLongClick = { anchor -> onLongPressWeek(week.id, anchor) },
            )
        }
        if (onAdd != null) {
            item(key = "add-week") { ProgramAddWeekCircle(onClick = onAdd) }
        }
    }
}

/** Un círculo de semana: 40 dp dentro de una zona táctil de 48 dp, con su punto de «hecha» debajo. */
@Composable
internal fun ProgramWeekCircle(
    week: ProgramPhaseWeekUi,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: (Rect) -> Unit,
    modifier: Modifier = Modifier,
) {
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val stateText = when {
        isSelected -> ", elegida"
        week.isCurrent -> ", en curso"
        week.isDone -> ", hecha"
        else -> ""
    }
    val fill = if (isSelected) WizardColors.text else KpknSurface.inset
    val ink = when {
        isSelected -> WizardColors.ctaContent
        week.isCurrent -> WizardColors.text
        else -> WizardColors.textMuted
    }
    // Marca de la semana (P3b): competición con bandera; ciclo con repetición. Sin ninguna, el círculo queda como en P2.
    val marker = when {
        week.isCompetition -> KpknLineIcons.Flag
        week.isLoop -> KpknLineIcons.Repeat
        else -> null
    }
    val markerTint = if (week.isCompetition) WizardColors.danger else WizardColors.textMuted
    val kindText = when {
        week.isCompetition -> ", de competición"
        week.isLoop -> ", de ciclo"
        else -> ""
    }
    Column(
        modifier = modifier
            .width(48.dp)
            .defaultMinSize(minHeight = 48.dp)
            .onGloballyPositioned { bounds = it.boundsInWindow() }
            .combinedClickable(
                role = Role.Tab,
                onClick = onClick,
                onLongClickLabel = "Copiar o pegar semana",
                onLongClick = { onLongClick(bounds) },
            )
            .semantics {
                contentDescription = "Semana ${week.number}$stateText$kindText"
                selected = isSelected
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // La marca va fuera del círculo recortado, así que cuelga de su esquina sin cortarse.
        Box(modifier = Modifier.size(40.dp)) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(fill, CircleShape)
                    .then(
                        if (week.isCurrent && !isSelected) Modifier.border(2.dp, WizardColors.text, CircleShape) else Modifier,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "${week.number}", style = KpknType.metricM, color = ink, maxLines = 1)
            }
            marker?.let { icon ->
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 4.dp, y = (-4).dp)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(WizardColors.background, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(12.dp), tint = markerTint)
                }
            }
        }
        Spacer(Modifier.height(5.dp))
        Box(
            modifier = Modifier
                .size(4.dp)
                .clip(CircleShape)
                .background(if (week.isDone) WizardColors.done else Color.Transparent, CircleShape),
        )
    }
}

/** Círculo «+» al final de una fila: agrega una semana. Ocupa el mismo alto que un círculo de semana. */
@Composable
private fun ProgramAddWeekCircle(onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(48.dp)
            .defaultMinSize(minHeight = 48.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "Agregar semana" },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(CircleShape).background(KpknSurface.inset, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = KpknLineIcons.Plus,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = WizardColors.textMuted,
            )
        }
        Spacer(Modifier.height(5.dp))
        Spacer(Modifier.size(4.dp))
    }
}
