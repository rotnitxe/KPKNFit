from pathlib import Path

p = Path(r"android-native/app/src/main/java/com/example/kpkn/screens/workout/WorkoutSetPager.kt")
text = p.read_text(encoding="utf-8")

helper = '''
internal fun workoutStepperGapDp(
    availableWidthDp: Float,
    contentWidthWithoutGapsDp: Float,
    gapCount: Int,
    minGapDp: Float = 10f,
    maxGapDp: Float = 36f,
): Float {
    if (gapCount <= 0) return minGapDp
    return ((availableWidthDp - contentWidthWithoutGapsDp) / gapCount).coerceIn(minGapDp, maxGapDp)
}

'''

needle = "}\n\n@Composable\ninternal fun WorkoutSetPager(\n    elements: List<TimelineElement>,"
if needle not in text:
    raise SystemExit("helper insert needle not found")
if "fun workoutStepperGapDp(" not in text:
    text = text.replace(
        needle,
        "}\n" + helper + "@Composable\ninternal fun WorkoutSetPager(\n    elements: List<TimelineElement>,",
        1,
    )

old_open = '''    val scrollState = rememberScrollState()

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp, max = 50.dp)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        shape = RoundedCornerShape(999.dp),
        color = Color.Transparent,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 36.dp, max = 44.dp)
                .padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SetProgressBadge(
                completedCount = completedCount,
                totalCount = totalCount,
                accent = timelineProgressColor,
            )
            Spacer(Modifier.width(6.dp))

            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center,
            ) {
                val availableWidth = maxWidth
                val totalVisibleItems = if (hasRounds) {
                    val expandedSetCount = roundGroups.firstOrNull { it.first.roundIndex == expandedRound }?.second?.size ?: 0
                    roundGroups.size + expandedSetCount
                } else {
                    elements.size
                }
                val estimatedTotalContentWidth = if (hasRounds) {
                    (totalVisibleItems * 36.0).dp
                } else {
                    elements.sumOf { elem ->
                        when (elem) {
                            is TimelineElement.UnilateralSet -> 68.0
                            is TimelineElement.RoundBadge -> 38.0
                            is TimelineElement.MobilityPill, is TimelineElement.WarmupPill -> 40.0
                            else -> 24.0
                        }
                    }.dp
                } +
                    (if (completedPreviousSets > 0) 20.dp else 0.dp) +
                    (if (nextExerciseSetCount > 0) 20.dp else 0.dp)

                val dynamicNormalSpacing = if (!hasRounds && elements.size > 1 && estimatedTotalContentWidth < availableWidth) {
                    ((availableWidth - estimatedTotalContentWidth) / (elements.size + 1)).coerceIn(10.dp, 26.dp)
                } else {
                    10.dp
                }

                val intraRoundSpacing = 6.dp
                val interRoundSpacing = 16.dp
                val collapsedRoundSpacing = 10.dp

                Box(
                    modifier = Modifier
                        .wrapContentWidth()
                        .horizontalScroll(scrollState),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        ContinuousTimelineTrack(
                            progress = timelineFillProgress,
                            fillColor = timelineProgressColor,
                            trackColor = trackColor,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp),
                        )
                    }
'''

new_open = '''    val scrollState = rememberScrollState()
    val addSlotWidth = if (onAddSet != null) 34.dp else 0.dp
    val badgeSlotWidth = 54.dp

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp, max = 56.dp)
            .padding(horizontal = 4.dp, vertical = 2.dp),
    ) {
        val chromeWidth = badgeSlotWidth + 8.dp + addSlotWidth
        val availableWidth = (maxWidth - chromeWidth).coerceAtLeast(0.dp)
        val totalVisibleItems = if (hasRounds) {
            val expandedSetCount = roundGroups.firstOrNull { it.first.roundIndex == expandedRound }?.second?.size ?: 0
            roundGroups.size + expandedSetCount
        } else {
            elements.size
        }
        val estimatedTotalContentWidth = if (hasRounds) {
            (totalVisibleItems * 36.0).dp
        } else {
            elements.sumOf { elem ->
                when (elem) {
                    is TimelineElement.UnilateralSet -> 68.0
                    is TimelineElement.RoundBadge -> 38.0
                    is TimelineElement.MobilityPill, is TimelineElement.WarmupPill -> 40.0
                    else -> 26.0
                }
            }.dp
        } +
            (if (completedPreviousSets > 0) 20.dp else 0.dp) +
            (if (nextExerciseSetCount > 0) 20.dp else 0.dp)

        val gapCount = if (hasRounds) 1 else (elements.size - 1).coerceAtLeast(0)
        val dynamicNormalSpacing = if (!hasRounds && gapCount > 0) {
            workoutStepperGapDp(
                availableWidthDp = availableWidth.value,
                contentWidthWithoutGapsDp = estimatedTotalContentWidth.value,
                gapCount = gapCount,
            ).dp
        } else {
            10.dp
        }
        val nodesWidth = estimatedTotalContentWidth + dynamicNormalSpacing * gapCount
        val overflows = nodesWidth > availableWidth

        val intraRoundSpacing = 6.dp
        val interRoundSpacing = 16.dp
        val collapsedRoundSpacing = 10.dp

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 40.dp, max = 52.dp)
                .padding(horizontal = 2.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!overflows) {
                Spacer(Modifier.weight(1f))
            }
            SetProgressBadge(
                completedCount = completedCount,
                totalCount = totalCount,
                accent = timelineProgressColor,
            )
            Spacer(Modifier.width(8.dp))

            Box(
                modifier = Modifier
                    .then(if (overflows) Modifier.weight(1f).fillMaxHeight() else Modifier.wrapContentWidth())
                    .heightIn(min = 34.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .wrapContentWidth()
                        .then(if (overflows) Modifier.horizontalScroll(scrollState) else Modifier),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 7.dp),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        ContinuousTimelineTrack(
                            progress = timelineFillProgress,
                            fillColor = timelineProgressColor,
                            trackColor = trackColor,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.dp),
                        )
                    }
'''

if old_open not in text:
    raise SystemExit("old_open not found")
text = text.replace(old_open, new_open, 1)

old_close = '''            }

            if (onAddSet != null) {
                Spacer(Modifier.width(4.dp))
                Surface(
                    modifier = Modifier
                        .size(26.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onAddSet() },
                    shape = CircleShape,
                    color = accent.copy(alpha = 0.22f),
                    border = BorderStroke(1.dp, accent.copy(alpha = 0.45f)),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Añadir serie",
                            tint = accent.copy(alpha = 0.88f),
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }
            }
        }
    }
}
'''

new_close = '''            }

            if (onAddSet != null) {
                Spacer(Modifier.width(8.dp))
                AddSetStepperButton(
                    accent = accent,
                    onClick = onAddSet,
                )
            }
            if (!overflows) {
                Spacer(Modifier.weight(1f))
            }
        }
    }
}
'''

if old_close not in text:
    raise SystemExit("old_close not found")
text = text.replace(old_close, new_close, 1)

text = text.replace(
    "val trackColor = Color.White.copy(alpha = 0.12f)",
    "val trackColor = Color.White.copy(alpha = 0.28f)",
    1,
)

button = '''
@Composable
private fun AddSetStepperButton(
    accent: Color,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .size(26.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        shape = CircleShape,
        color = accent.copy(alpha = 0.22f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.45f)),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Añadir serie",
                tint = accent.copy(alpha = 0.88f),
                modifier = Modifier.size(15.dp),
            )
        }
    }
}

'''

if "fun AddSetStepperButton(" not in text:
    marker = "@Composable\nprivate fun ContinuousTimelineTrack("
    if marker not in text:
        raise SystemExit("track marker not found")
    text = text.replace(marker, button + marker, 1)

p.write_text(text, encoding="utf-8")
print("patched ok")
