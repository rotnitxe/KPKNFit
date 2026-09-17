    Surface(
        modifier = modifier.width(44.dp),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.36f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .width(44.dp)
                .heightIn(max = 480.dp)
                .verticalScroll(scrollState)
                .padding(horizontal = 4.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(nodeSpacing),
        ) {
            Box(
                modifier = Modifier.wrapContentHeight(),
                contentAlignment = Alignment.TopCenter,
            ) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    ContinuousTimelineTrack(
                        progress = timelineFillProgress,
                        fillColor = timelineProgressColor,
                        trackColor = trackColor,
                        modifier = Modifier
                            .width(3.dp)
                            .fillMaxHeight(),
                        vertical = true,
                    )
                }

                Column(
                    modifier = Modifier
                        .wrapContentHeight()
                        .animateContentSize(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMediumLow,
                            )
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(nodeSpacing),
                ) {
                    if (completedPreviousSets > 0) {
                        PreviousCompletedCluster(
                            count = completedPreviousSets,
                            accent = accent,
                        )
                    }

                    if (hasRounds) {
                        roundGroups.forEachIndexed { groupIdx, (badge, sets) ->
                            val isExpanded = badge.roundIndex == expandedRound
                            if (groupIdx > 0) {
                                val prevGroupIsExpanded = roundGroups[groupIdx - 1].first.roundIndex == expandedRound
                                val gap = if (prevGroupIsExpanded) interRoundSpacing else collapsedRoundSpacing
                                Spacer(modifier = Modifier.height(gap - nodeSpacing))
                            }
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.animateContentSize(
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessMediumLow,
                                    )
                                ),
                            ) {
                                RoundBadgeNode(
                                    roundIndex = badge.roundIndex,
                                    isCurrentRound = badge.isCurrentRound,
                                    isExpanded = isExpanded,
                                    isAllDone = badge.isAllDone,
                                    accent = accent,
                                    onClick = {
                                        userToggledRound = badge.roundIndex
                                        onSelectPage(badge.firstPageIndex)
                                    },
                                )

                                AnimatedVisibility(
                                    visible = isExpanded,
                                    enter = fadeIn(animationSpec = tween(220)) + expandVertically(
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioNoBouncy,
                                            stiffness = Spring.StiffnessMediumLow,
                                        ),
                                        expandFrom = Alignment.Top,
                                    ),
                                    exit = fadeOut(animationSpec = tween(150)) + shrinkVertically(
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioNoBouncy,
                                            stiffness = Spring.StiffnessMediumLow,
                                        ),
                                        shrinkTowards = Alignment.Top,
                                    ),
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(intraRoundSpacing),
                                        modifier = Modifier.padding(top = intraRoundSpacing),
                                    ) {
                                        sets.forEach { setElement ->
                                            when (setElement) {
                                                is TimelineElement.BilateralSet -> {
                                                    val isActive = setElement.state == WorkoutSetCardVisualState.ACTIVE
                                                    val isComplete = setElement.state == WorkoutSetCardVisualState.COMPLETED
                                                    val isSkipped = setElement.state == WorkoutSetCardVisualState.SKIPPED
                                                    val accentColor = workoutSetPagerAccent(
                                                        setElement.state,
                                                        MaterialTheme.colorScheme,
                                                        false,
                                                        sessionAccentColor,
                                                    )
                                                    TimelineDot(
                                                        accent = accentColor,
                                                        active = isActive || setElement.isEditing,
                                                        complete = isComplete,
                                                        skipped = isSkipped,
                                                        label = setElement.label,
                                                        modifier = Modifier
                                                            .combinedClickable(
                                                                interactionSource = remember { MutableInteractionSource() },
                                                                indication = null,
                                                                onClick = { onSelectPage(setElement.pageIndex) },
                                                                onLongClick = if (onLongPressPage != null) {
                                                                    { onLongPressPage(setElement.pageIndex) }
                                                                } else null,
                                                            ),
                                                    )
                                                }
                                                is TimelineElement.UnilateralSet -> {
                                                    UnilateralSetStackNode(
                                                        setLabel = setElement.setLabel,
                                                        leftPageIndex = setElement.leftPageIndex,
                                                        leftState = setElement.leftState,
                                                        rightPageIndex = setElement.rightPageIndex,
                                                        rightState = setElement.rightState,
                                                        accent = accent,
                                                        onSelectPage = onSelectPage,
                                                    )
                                                }
                                                is TimelineElement.MobilityPill -> {
                                                    StepperProgressPillNode(
                                                        label = "MOV",
                                                        isActive = setElement.isCurrent,
                                                        isCompleted = setElement.isCompleted,
                                                        progress = if (setElement.isCompleted) 1f else setElement.progress,
                                                        accent = accent,
                                                        onClick = setElement.onSelect,
                                                    )
                                                }
                                                is TimelineElement.WarmupPill -> {
                                                    StepperProgressPillNode(
                                                        label = "APR",
                                                        isActive = setElement.isCurrent,
                                                        isCompleted = setElement.isCompleted,
                                                        progress = if (setElement.isCompleted) 1f else setElement.progress,
                                                        accent = accent,
                                                        onClick = setElement.onSelect,
                                                    )
                                                }
                                                is TimelineElement.RoundBadge -> {}
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        elements.forEach { element ->
                            when (element) {
                                is TimelineElement.BilateralSet -> {
                                    val isActive = element.state == WorkoutSetCardVisualState.ACTIVE
                                    val isComplete = element.state == WorkoutSetCardVisualState.COMPLETED
                                    val isSkipped = element.state == WorkoutSetCardVisualState.SKIPPED
                                    val accentColor = workoutSetPagerAccent(
                                        element.state,
                                        MaterialTheme.colorScheme,
                                        false,
                                        sessionAccentColor,
                                    )
                                    TimelineDot(
                                        accent = accentColor,
                                        active = isActive || element.isEditing,
                                        complete = isComplete,
                                        skipped = isSkipped,
                                        label = element.label,
                                        modifier = Modifier
                                            .combinedClickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null,
                                                onClick = { onSelectPage(element.pageIndex) },
                                                onLongClick = if (onLongPressPage != null) {
                                                    { onLongPressPage(element.pageIndex) }
                                                } else null,
                                            ),
                                    )
                                }
                                is TimelineElement.UnilateralSet -> {
                                    UnilateralSetStackNode(
                                        setLabel = element.setLabel,
                                        leftPageIndex = element.leftPageIndex,
                                        leftState = element.leftState,
                                        rightPageIndex = element.rightPageIndex,
                                        rightState = element.rightState,
                                        accent = accent,
                                        onSelectPage = onSelectPage,
                                    )
                                }
                                is TimelineElement.MobilityPill -> {
                                    StepperProgressPillNode(
                                        label = "MOV",
                                        isActive = element.isCurrent,
                                        isCompleted = element.isCompleted,
                                        progress = if (element.isCompleted) 1f else element.progress,
                                        accent = accent,
                                        onClick = element.onSelect,
                                    )
                                }
                                is TimelineElement.WarmupPill -> {
                                    StepperProgressPillNode(
                                        label = "APR",
                                        isActive = element.isCurrent,
                                        isCompleted = element.isCompleted,
                                        progress = if (element.isCompleted) 1f else element.progress,
                                        accent = accent,
                                        onClick = element.onSelect,
                                    )
                                }
                                is TimelineElement.RoundBadge -> {}
                            }
                        }
                    }

                    if (nextExerciseSetCount > 0) {
                        NextGhostCluster(
                            count = nextExerciseSetCount,
                            accent = accent,
                        )
                    }
                }
            }

            if (onAddSet != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    modifier = Modifier
                        .size(28.dp)
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