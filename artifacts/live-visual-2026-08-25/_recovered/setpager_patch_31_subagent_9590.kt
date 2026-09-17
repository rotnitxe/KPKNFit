@Composable
private fun StepperProgressPillNode(
    label: String,
    isActive: Boolean,
    isCompleted: Boolean,
    progress: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String = label,
    onActiveAnchorInParent: ((Offset) -> Unit)? = null,
) {
    val kind = when (label) {
        "M", "MOV" -> LiveRoadmapNodeKind.MOBILITY
        "A", "APR" -> LiveRoadmapNodeKind.APPROACH
        else -> LiveRoadmapNodeKind.WORKING
    }
    val short = liveNodeShortLabel(kind, label)
    val a11y = liveNodeContentDescription(kind, short)
    Box(
        modifier = modifier
            .width(WorkoutLiveVisualTokens.RailHitWidth)
            .height(48.dp)
            .semantics { this.contentDescription = a11y.ifBlank { contentDescription } }
            .then(
                if (isActive && onActiveAnchorInParent != null) {
                    Modifier.onGloballyPositioned { coords ->
                        val pos = coords.positionInParent()
                        onActiveAnchorInParent(
                            Offset(pos.x + coords.size.width / 2f, pos.y + coords.size.height / 2f),
                        )
                    }
                } else Modifier,
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .width(WorkoutLiveVisualTokens.RailVisualWidth)
                .height(if (isActive) 36.dp else 32.dp),
            shape = RoundedCornerShape(999.dp),
            color = WorkoutLiveVisualTokens.StepperWhite,
            border = null,
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (isCompleted) "✓" else short,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    fontWeight = FontWeight.Black,
                    color = Color.Black,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun RoundBadgeNode(
    roundIndex: Int,
    isCurrentRound: Boolean,
    isExpanded: Boolean = true,
    isAllDone: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .width(WorkoutLiveVisualTokens.RailHitWidth)
            .height(48.dp)
            .semantics { contentDescription = "Ronda ${roundIndex + 1}" }
            .clip(RoundedCornerShape(999.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .width(WorkoutLiveVisualTokens.RailVisualWidth)
                .height(32.dp),
            shape = RoundedCornerShape(999.dp),
            color = WorkoutLiveVisualTokens.StepperWhite,
            border = null,
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (isAllDone && !isCurrentRound) "✓" else "R${roundIndex + 1}",
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp),
                    fontWeight = FontWeight.Black,
                    color = Color.Black,
                )
            }
        }
    }
}

@Composable
private fun UnilateralSetStackNode(
    setLabel: String?,
    leftPageIndex: Int?,
    leftState: WorkoutSetCardVisualState,
    rightPageIndex: Int?,
    rightState: WorkoutSetCardVisualState,
    onSelectPage: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onActiveAnchorInParent: ((Offset) -> Unit)? = null,
) {
    val shortLabel = setLabel
        ?.replace("Serie ", "")
        ?.takeWhile { it.isDigit() }
        ?.ifBlank { null }
        ?: setLabel?.take(3)
        ?: "S"
    val leftActive = leftState == WorkoutSetCardVisualState.ACTIVE
    val rightActive = rightState == WorkoutSetCardVisualState.ACTIVE

    Box(
        modifier = modifier.width(WorkoutLiveVisualTokens.RailHitWidth),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.width(WorkoutLiveVisualTokens.RailVisualWidth),
            shape = RoundedCornerShape(999.dp),
            color = WorkoutLiveVisualTokens.StepperWhite,
            border = null,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 2.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = shortLabel,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    fontWeight = FontWeight.Black,
                    color = Color.Black,
                    maxLines = 1,
                )
                UnilateralDotNode(
                    label = "L",
                    state = leftState,
                    onClick = { leftPageIndex?.let { onSelectPage(it) } },
                    onActiveAnchorInParent = if (leftActive) onActiveAnchorInParent else null,
                )
                UnilateralDotNode(
                    label = "R",
                    state = rightState,
                    onClick = { rightPageIndex?.let { onSelectPage(it) } },
                    onActiveAnchorInParent = if (rightActive) onActiveAnchorInParent else null,
                )
            }
        }
    }
}

@Composable
private fun UnilateralDotNode(
    label: String,
    state: WorkoutSetCardVisualState,
    onClick: () -> Unit,
    onActiveAnchorInParent: ((Offset) -> Unit)? = null,
) {
    val isComplete = state == WorkoutSetCardVisualState.COMPLETED
    val isActive = state == WorkoutSetCardVisualState.ACTIVE
    Surface(
        modifier = Modifier
            .width(20.dp)
            .height(18.dp)
            .semantics {
                contentDescription = liveNodeContentDescription(
                    LiveRoadmapNodeKind.UNILATERAL_SIDE,
                    label,
                    side = if (label == "L") "left" else "right",
                )
            }
            .then(
                if (isActive && onActiveAnchorInParent != null) {
                    Modifier.onGloballyPositioned { coords ->
                        val pos = coords.positionInParent()
                        onActiveAnchorInParent(
                            Offset(pos.x + coords.size.width / 2f, pos.y + coords.size.height / 2f),
                        )
                    }
                } else Modifier,
            )
            .clip(RoundedCornerShape(999.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        shape = RoundedCornerShape(999.dp),
        color = Color.Black.copy(alpha = 0.08f),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = if (isComplete) "✓" else label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                fontWeight = FontWeight.Black,
                color = Color.Black,
            )
        }
    }
}

@Composable
private fun TimelinePill(
    active: Boolean,
    complete: Boolean,
    skipped: Boolean,
    label: String,
    modifier: Modifier = Modifier,
    onActiveAnchorInParent: ((Offset) -> Unit)? = null,
) {
    val shortLabel = remember(label) {
        liveNodeShortLabel(LiveRoadmapNodeKind.WORKING, label)
    }
    Box(
        modifier = Modifier
            .width(WorkoutLiveVisualTokens.RailHitWidth)
            .height(48.dp)
            .semantics {
                contentDescription = liveNodeContentDescription(LiveRoadmapNodeKind.WORKING, shortLabel)
            }
            .then(
                if (active && onActiveAnchorInParent != null) {
                    Modifier.onGloballyPositioned { coords ->
                        val pos = coords.positionInParent()
                        onActiveAnchorInParent(
                            Offset(pos.x + coords.size.width / 2f, pos.y + coords.size.height / 2f),
                        )
                    }
                } else Modifier,
            )
            .then(modifier),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .width(WorkoutLiveVisualTokens.RailVisualWidth)
                .height(if (active) 36.dp else 32.dp),
            shape = RoundedCornerShape(999.dp),
            color = WorkoutLiveVisualTokens.StepperWhite,
            border = null,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = if (complete && !active) "✓" else shortLabel,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = if (active) 11.sp else 10.sp),
                    fontWeight = FontWeight.Black,
                    color = Color.Black,
                    maxLines = 1,
                )
            }
        }
    }
}