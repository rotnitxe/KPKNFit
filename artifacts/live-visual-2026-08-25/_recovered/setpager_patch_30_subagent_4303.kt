private fun SeriesProgressCapsule(
    sets: List<TimelineElement.BilateralSet>,
    onSelectPage: (Int) -> Unit,
    onLongPressPage: ((Int) -> Unit)?,
    modifier: Modifier = Modifier,
    onActiveAnchorInParent: ((Offset) -> Unit)? = null,
) {
    if (sets.isEmpty()) return
    val segmentHeight = 36.dp
    val pillWidth = WorkoutLiveVisualTokens.RailVisualWidth
    val totalHeight = segmentHeight * sets.size

    Box(
        modifier = modifier
            .width(WorkoutLiveVisualTokens.RailHitWidth)
            .height(totalHeight),
        contentAlignment = Alignment.Center,
    ) {
    Surface(
        modifier = Modifier
            .width(pillWidth)
            .height(totalHeight),
        shape = RoundedCornerShape(999.dp),
        color = WorkoutLiveVisualTokens.StepperWhite,
        border = null,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            sets.forEachIndexed { index, set ->
                val isActive = set.state == WorkoutSetCardVisualState.ACTIVE || set.isEditing
                val isComplete = set.state == WorkoutSetCardVisualState.COMPLETED
                val shortLabel = remember(set.label) {
                    liveNodeShortLabel(LiveRoadmapNodeKind.WORKING, set.label)
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .semantics {
                            contentDescription = liveNodeContentDescription(
                                LiveRoadmapNodeKind.WORKING,
                                shortLabel,
                            )
                        }
                        .then(
                            if (isActive && onActiveAnchorInParent != null) {
                                Modifier.onGloballyPositioned { coords ->
                                    val pos = coords.positionInParent()
                                    val anchor = Offset(
                                        x = pos.x + coords.size.width / 2f,
                                        y = pos.y + coords.size.height / 2f,
                                    )
                                    onActiveAnchorInParent(anchor)
                                }
                            } else Modifier,
                        )
                        .combinedClickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onSelectPage(set.pageIndex) },
                            onLongClick = if (onLongPressPage != null) {
                                { onLongPressPage(set.pageIndex) }
                            } else null,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isActive) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(2.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(Color.Black.copy(alpha = 0.08f)),
                        )
                    }
                    Text(
                        text = if (isComplete && !isActive) "✓" else shortLabel,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = if (isActive) 12.sp else 11.sp),
                        fontWeight = if (isActive || isComplete) FontWeight.Black else FontWeight.Bold,
                        color = if (isActive || isComplete) Color.Black else Color.Black.copy(alpha = 0.65f),
                        maxLines = 1,
                    )
                }

                if (index < sets.size - 1) {
                    Box(
                        modifier = Modifier
                            .size(3.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.30f)),
                    )
                }
            }
        }
    }
    }
}