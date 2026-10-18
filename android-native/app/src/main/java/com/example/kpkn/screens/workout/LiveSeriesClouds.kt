package com.example.kpkn.screens.workout

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.ui.components.icons.KpknLineIcons
import com.example.kpkn.ui.design.KpknType

private val SeriesCloudInk = Color(0xFFF2EEE6)
private val SeriesCloudDark = Color(0xFF161616)

/** Compact presentation of the existing timeline; route values always come from its original nodes. */
private data class LiveSeriesCloud(
    val key: String,
    val label: String,
    val description: String,
    val active: Boolean,
    val completed: Boolean = false,
    val skipped: Boolean = false,
    val volumeReplaced: Boolean = false,
    val pageIndex: Int? = null,
    val preparationSelect: (() -> Unit)? = null,
    val preparationProgress: Float? = null,
)

/**
 * Revised live.js/live.css series clouds beneath the set pager.
 * [LiveSetStepperSnapshot.activeElementIndex] indexes the original timeline, not pager pages.
 * RestPill is deliberately absent; removing it must never renumber a destination or a selected node.
 */
@Composable
internal fun LiveSeriesClouds(
    snapshot: LiveSetStepperSnapshot,
    onSelectPage: (Int) -> Unit,
    onLongPressPage: ((Int) -> Unit)? = null,
    onAddSet: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    activePageIndex: Int? = null,
) {
    val clouds = liveSeriesClouds(snapshot, activePageIndex)
    val state = rememberLazyListState()
    val activeIndex = clouds.indexOfFirst { it.active }
    val keys = clouds.map { it.key }
    LaunchedEffect(keys, activeIndex) {
        if (activeIndex >= 0) state.animateScrollToItem(activeIndex)
    }
    LazyRow(
        state = state,
        modifier = modifier.fillMaxWidth().height(35.dp).testTag("live-series-clouds"),
        contentPadding = PaddingValues(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(clouds, key = { it.key }) { cloud ->
            LiveSeriesCloudPill(
                cloud = cloud,
                onClick = { cloud.preparationSelect?.invoke() ?: cloud.pageIndex?.let(onSelectPage) },
                onLongClick = cloud.pageIndex?.let { page -> onLongPressPage?.let { open -> { open(page) } } },
            )
        }
        if (snapshot.canAddSet && onAddSet != null) item(key = "add-series") {
            LiveSeriesAddCloud(onAddSet)
        }
    }
}

private fun liveSeriesClouds(snapshot: LiveSetStepperSnapshot, activePageIndex: Int?): List<LiveSeriesCloud> = buildList {
    snapshot.elements.forEachIndexed { timelineIndex, element ->
        val selectedElement = timelineIndex == snapshot.activeElementIndex
        when (element) {
            is TimelineElement.RestPill -> Unit
            is TimelineElement.MobilityPill -> add(LiveSeriesCloud(
                key = "mobility-$timelineIndex", label = "MOV", description = "Movilidad",
                active = selectedElement || element.isCurrent, completed = element.isCompleted,
                preparationSelect = element.onSelect, preparationProgress = element.progress,
            ))
            is TimelineElement.WarmupPill -> add(LiveSeriesCloud(
                key = "warmup-$timelineIndex", label = "APROX", description = "Aproximación",
                active = selectedElement || element.isCurrent, completed = element.isCompleted,
                preparationSelect = element.onSelect, preparationProgress = element.progress,
            ))
            is TimelineElement.RoundBadge -> add(LiveSeriesCloud(
                key = "round-${element.roundIndex}", label = "R${element.roundIndex + 1}",
                description = "Ronda ${element.roundIndex + 1}", active = selectedElement,
                completed = element.isAllDone, pageIndex = element.firstPageIndex,
            ))
            is TimelineElement.BilateralSet -> add(LiveSeriesCloud(
                key = "bilateral-$timelineIndex-${element.pageIndex}", label = element.label,
                description = seriesRoundDescription(element.label, element.roundIndex),
                active = activePageIndex?.let { it == element.pageIndex } ?: selectedElement,
                completed = element.state == WorkoutSetCardVisualState.COMPLETED,
                skipped = element.state == WorkoutSetCardVisualState.SKIPPED,
                volumeReplaced = element.volumeReplaced, pageIndex = element.pageIndex,
            ))
            is TimelineElement.UnilateralSet -> {
                val label = element.setLabel ?: "S"
                // Both sides retain separate destinations, including when only one side exists.
                element.leftPageIndex?.let { page -> add(LiveSeriesCloud(
                    key = "unilateral-$timelineIndex-left-$page", label = "$label I",
                    description = seriesRoundDescription("$label, lado izquierdo", element.roundIndex),
                    active = activePageIndex?.let { it == page } ?:
                        (selectedElement && (element.leftState == WorkoutSetCardVisualState.ACTIVE || element.rightPageIndex == null)),
                    completed = element.leftState == WorkoutSetCardVisualState.COMPLETED,
                    skipped = element.leftState == WorkoutSetCardVisualState.SKIPPED,
                    volumeReplaced = element.volumeReplaced, pageIndex = page,
                )) }
                element.rightPageIndex?.let { page -> add(LiveSeriesCloud(
                    key = "unilateral-$timelineIndex-right-$page", label = "$label D",
                    description = seriesRoundDescription("$label, lado derecho", element.roundIndex),
                    active = activePageIndex?.let { it == page } ?:
                        (selectedElement && (element.rightState == WorkoutSetCardVisualState.ACTIVE || element.leftPageIndex == null)),
                    completed = element.rightState == WorkoutSetCardVisualState.COMPLETED,
                    skipped = element.rightState == WorkoutSetCardVisualState.SKIPPED,
                    volumeReplaced = element.volumeReplaced, pageIndex = page,
                )) }
            }
        }
    }
}

private fun seriesRoundDescription(label: String, round: Int?): String =
    if (round == null) label else "Ronda ${round + 1}, $label"

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LiveSeriesCloudPill(cloud: LiveSeriesCloud, onClick: () -> Unit, onLongClick: (() -> Unit)?) {
    val ink = if (cloud.active) Color(0xFF0B0B0B) else SeriesCloudInk.copy(alpha = if (cloud.skipped) .38f else 1f)
    Box(
        Modifier.height(35.dp).combinedClickable(
            interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button,
            onClick = onClick, onLongClick = onLongClick,
        ).semantics(mergeDescendants = true) {
            selected = cloud.active
            contentDescription = cloud.description
            stateDescription = buildList {
                if (cloud.active) add("Seleccionada")
                if (cloud.completed) add("Completada")
                if (cloud.skipped) add("Omitida")
                cloud.preparationProgress?.takeIf { !cloud.completed }?.let { add("${(it.coerceIn(0f, 1f) * 100).toInt()} por ciento") }
                if (cloud.volumeReplaced) add("Técnica de volumen reemplazado")
            }.joinToString(", ")
        }.testTag("live-series-cloud-${cloud.key}"),
        contentAlignment = Alignment.Center,
    ) {
        val shape = RoundedCornerShape(50)
        Row(
            Modifier.height(27.dp).widthIn(min = 40.dp).clip(shape)
                .background(if (cloud.active) SeriesCloudInk else SeriesCloudDark)
                .border(1.dp, when {
                    cloud.volumeReplaced -> VolumeReplacedStepperTint
                    cloud.active -> Color.Transparent
                    else -> SeriesCloudInk.copy(alpha = .1f)
                }, shape).padding(horizontal = 11.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (cloud.completed) Icon(KpknLineIcons.Check, null, Modifier.size(13.dp), tint = ink)
            Text(cloud.label, style = KpknType.chip.copy(fontSize = 11.sp, lineHeight = 14.sp), color = ink, maxLines = 1)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LiveSeriesAddCloud(onAddSet: () -> Unit) {
    Box(Modifier.size(35.dp).combinedClickable(
        interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Button,
        onClick = onAddSet,
    ).semantics { contentDescription = "Añadir serie" }.testTag("live-series-cloud-add"), contentAlignment = Alignment.Center) {
        Box(Modifier.size(28.dp).clip(CircleShape).background(SeriesCloudDark), contentAlignment = Alignment.Center) {
            Icon(KpknLineIcons.Plus, null, Modifier.size(16.dp), tint = SeriesCloudInk)
        }
    }
}
