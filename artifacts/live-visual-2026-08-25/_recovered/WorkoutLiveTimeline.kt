package com.example.kpkn.screens.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.models.CompletedSet
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.screens.workout.components.WorkoutUiTokens
import com.example.kpkn.ui.theme.DarkBackground

private enum class LiveTimelineKind {
    COMPLETED_SET,
    ACTIVE_SET,
    REST,
    PEEK_SET,
    PEEK_EXERCISE,
}

private data class LiveTimelineStep(
    val kind: LiveTimelineKind,
    val pageIndex: Int? = null,
    val setNumber: Int = 1,
    val sideLabel: String? = null,
    val fillProgress: Float = 0f,
)

@Composable
internal fun WorkoutLiveTimeline(
    pages: List<WorkoutSetSwipePage>,
    currentPageIndex: Int,
    completedSets: Map<String, CompletedSet>,
    currentExercise: Exercise,
    nextExercise: Exercise?,
    isRestRunning: Boolean,
    restFillProgress: Float,
    restContent: @Composable () -> Unit,
    setContent: @Composable (page: WorkoutSetSwipePage, pageIndex: Int, isActive: Boolean, isPeek: Boolean) -> Unit,
    onSelectPage: (Int) -> Unit,
    onSelectNextExercise: (() -> Unit)?,
    peekExerciseContent: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val steps = remember(
        pages,
        currentPageIndex,
        completedSets,
        currentExercise.id,
        isRestRunning,
        restFillProgress,
        nextExercise?.id,
    ) {
        buildLiveTimelineSteps(
            pages = pages,
            currentPageIndex = currentPageIndex,
            completedSets = completedSets,
            currentExercise = currentExercise,
            nextExercise = nextExercise,
            isRestRunning = isRestRunning,
            restFillProgress = restFillProgress,
        )
    }
    val listState = rememberLazyListState()
    val focusIndex = steps.indexOfFirst {
        it.kind == LiveTimelineKind.ACTIVE_SET || it.kind == LiveTimelineKind.REST
    }.coerceAtLeast(0)

    LaunchedEffect(focusIndex, currentExercise.id, isRestRunning) {
        if (steps.isNotEmpty()) {
            listState.animateScrollToItem(focusIndex)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 96.dp, top = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            itemsIndexed(steps, key = { index, step -> "${step.kind}-$index-${step.pageIndex}" }) { _, step ->
                when (step.kind) {
                    LiveTimelineKind.COMPLETED_SET -> {
                        val page = pages.getOrNull(step.pageIndex ?: -1)
                        TimelineStepRow(
                            fillProgress = 1f,
                            isActive = false,
                            isCompleted = true,
                        ) {
                            CompletedSetButton(
                                setNumber = step.setNumber,
                                sideLabel = step.sideLabel,
                                onClick = {
                                    step.pageIndex?.let(onSelectPage)
                                },
                            )
                        }
                    }
                    LiveTimelineKind.ACTIVE_SET -> {
                        val page = pages.getOrNull(step.pageIndex ?: 0) ?: return@itemsIndexed
                        TimelineStepRow(
                            fillProgress = 1f,
                            isActive = true,
                            isCompleted = false,
                        ) {
                            setContent(page, step.pageIndex ?: 0, true, false)
                        }
                    }
                    LiveTimelineKind.REST -> {
                        TimelineStepRow(
                            fillProgress = restFillProgress.coerceIn(0f, 1f),
                            isActive = true,
                            isCompleted = false,
                            isRest = true,
                        ) {
                            restContent()
                        }
                    }
                    LiveTimelineKind.PEEK_SET -> {
                        val page = pages.getOrNull(step.pageIndex ?: -1) ?: return@itemsIndexed
                        TimelineStepRow(
                            fillProgress = 0f,
                            isActive = false,
                            isCompleted = false,
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectPage(step.pageIndex ?: 0) },
                            ) {
                                setContent(page, step.pageIndex ?: 0, false, true)
                            }
                        }
                    }
                    LiveTimelineKind.PEEK_EXERCISE -> {
                        TimelineStepRow(
                            fillProgress = 0f,
                            isActive = false,
                            isCompleted = false,
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = onSelectNextExercise != null) {
                                        onSelectNextExercise?.invoke()
                                    },
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = nextExercise?.name.orEmpty(),
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White.copy(alpha = 0.45f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                peekExerciseContent?.invoke()
                            }
                        }
                    }
                }
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(88.dp)
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to Color.Transparent,
                            0.35f to DarkBackground.copy(alpha = 0.35f),
                            1.0f to DarkBackground.copy(alpha = 0.94f),
                        ),
                    ),
                ),
        )
    }
}

private fun buildLiveTimelineSteps(
    pages: List<WorkoutSetSwipePage>,
    currentPageIndex: Int,
    completedSets: Map<String, CompletedSet>,
    currentExercise: Exercise,
    nextExercise: Exercise?,
    isRestRunning: Boolean,
    restFillProgress: Float,
): List<LiveTimelineStep> {
    val steps = mutableListOf<LiveTimelineStep>()
    fun pageCompleted(page: WorkoutSetSwipePage): Boolean {
        val exId = page.exerciseId ?: currentExercise.id
        val key = when (page.side) {
            "left" -> "${exId}_${page.setIndex}_L"
            "right" -> "${exId}_${page.setIndex}_R"
            else -> "${exId}_${page.setIndex}"
        }
        return completedSets.containsKey(key)
    }
    pages.forEachIndexed { index, page ->
        val setNumber = page.setIndex + 1
        val sideLabel = when (page.side) {
            "left" -> "L"
            "right" -> "R"
            else -> null
        }
        val done = pageCompleted(page)
        val isCurrent = index == currentPageIndex
        when {
            isRestRunning && done -> steps.add(
                LiveTimelineStep(
                    kind = LiveTimelineKind.COMPLETED_SET,
                    pageIndex = index,
                    setNumber = setNumber,
                    sideLabel = sideLabel,
                    fillProgress = 1f,
                ),
            )
            !isRestRunning && isCurrent -> steps.add(
                LiveTimelineStep(
                    kind = LiveTimelineKind.ACTIVE_SET,
                    pageIndex = index,
                    setNumber = setNumber,
                    sideLabel = sideLabel,
                    fillProgress = 1f,
                ),
            )
            done && !isCurrent -> steps.add(
                LiveTimelineStep(
                    kind = LiveTimelineKind.COMPLETED_SET,
                    pageIndex = index,
                    setNumber = setNumber,
                    sideLabel = sideLabel,
                    fillProgress = 1f,
                ),
            )
            !done && isCurrent -> steps.add(
                LiveTimelineStep(
                    kind = LiveTimelineKind.ACTIVE_SET,
                    pageIndex = index,
                    setNumber = setNumber,
                    sideLabel = sideLabel,
                ),
            )
        }
    }
    if (isRestRunning) {
        steps.add(
            LiveTimelineStep(
                kind = LiveTimelineKind.REST,
                fillProgress = restFillProgress,
            ),
        )
    }
    val peekPageIndex = if (isRestRunning) {
        pages.indexOfFirst { !pageCompleted(it) }.takeIf { it >= 0 }
    } else {
        (currentPageIndex + 1).takeIf { it in pages.indices }
    }
    if (peekPageIndex != null) {
        val page = pages[peekPageIndex]
        steps.add(
            LiveTimelineStep(
                kind = LiveTimelineKind.PEEK_SET,
                pageIndex = peekPageIndex,
                setNumber = page.setIndex + 1,
                sideLabel = when (page.side) {
                    "left" -> "L"
                    "right" -> "R"
                    else -> null
                },
            ),
        )
    } else if (nextExercise != null) {
        steps.add(LiveTimelineStep(kind = LiveTimelineKind.PEEK_EXERCISE))
    }
    return steps
}

@Composable
private fun TimelineStepRow(
    fillProgress: Float,
    isActive: Boolean,
    isCompleted: Boolean,
    isRest: Boolean = false,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        TimelineCapsule(
            fillProgress = fillProgress,
            isActive = isActive,
            isCompleted = isCompleted,
            isRest = isRest,
            modifier = Modifier.fillMaxHeight(),
        )
        Box(modifier = Modifier.weight(1f)) {
            content()
        }
    }
}

@Composable
private fun TimelineCapsule(
    fillProgress: Float,
    isActive: Boolean,
    isCompleted: Boolean,
    isRest: Boolean,
    modifier: Modifier = Modifier,
) {
    val width = if (isActive) 18.dp else 12.dp
    val minHeight = if (isActive) 72.dp else 36.dp
    val description = when {
        isRest -> "Descanso"
        isCompleted -> "Serie completada"
        isActive -> "Serie activa"
        else -> "Siguiente serie"
    }
    Box(
        modifier = modifier
            .width(width)
            .height(minHeight)
            .semantics { contentDescription = description }
            .clip(RoundedCornerShape(999.dp))
            .background(Color.White.copy(alpha = 0.10f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(fillProgress.coerceIn(0f, 1f))
                .align(Alignment.TopCenter)
                .clip(RoundedCornerShape(999.dp))
                .background(WorkoutUiTokens.LiveStepperBlue),
        )
    }
}

@Composable
private fun CompletedSetButton(
    setNumber: Int,
    sideLabel: String?,
    onClick: () -> Unit,
) {
    val label = buildString {
        append("SERIE $setNumber")
        if (!sideLabel.isNullOrBlank()) append(" $sideLabel")
        append(" COMPLETADA")
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, letterSpacing = 1.1.sp),
        fontWeight = FontWeight.Black,
        color = Color.White.copy(alpha = 0.88f),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp)
            .semantics { contentDescription = label },
    )
}
