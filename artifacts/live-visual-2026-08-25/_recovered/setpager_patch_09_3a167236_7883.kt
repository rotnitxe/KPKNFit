private val TIMELINE_NODE_SOLID_BG = Color(0xFF141416)
/** Soft premium blue used for stepper progress fill. */
private val STEPPER_SOFT_BLUE = Color(0xFF7E9FCB)
private val STEPPER_SOFT_BLUE_WASH = Color(0xFF7E9FCB).copy(alpha = 0.22f)
private val STEPPER_SOFT_BLUE_FILL = Color(0xFF7E9FCB).copy(alpha = 0.58f)

@Composable
private fun SeriesProgressCapsule(
    sets: List<TimelineElement.BilateralSet>,
    onSelectPage: (Int) -> Unit,
    onLongPressPage: ((Int) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    if (sets.isEmpty()) return
    val completed = sets.count { it.state == WorkoutSetCardVisualState.COMPLETED }
    val fillTarget = (completed.toFloat() / sets.size.toFloat()).coerceIn(0f, 1f)
    val fillProgress by animateFloatAsState(
        targetValue = fillTarget,
        animationSpec = tween(durationMillis = 520, easing = FastOutSlowInEasing),
        label = "seriesCapsuleFill",
    )
    val segmentHeight = 32.dp
    val pillWidth = 22.dp
    val totalHeight = segmentHeight * sets.size

    Box(
        modifier = modifier
            .width(pillWidth)
            .height(totalHeight),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(999.dp))
                .background(Color.White.copy(alpha = 0.10f))
                .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)), RoundedCornerShape(999.dp)),
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .fillMaxHeight(fillProgress)
                .clip(RoundedCornerShape(999.dp))
                .background(STEPPER_SOFT_BLUE_FILL),
        )
        Column(modifier = Modifier.fillMaxSize()) {
            sets.forEach { set ->
                val isActive = set.state == WorkoutSetCardVisualState.ACTIVE || set.isEditing
                val isComplete = set.state == WorkoutSetCardVisualState.COMPLETED
                val shortLabel = remember(set.label) {
                    set.label
                        .replace("Serie ", "")
                        .takeWhile { it.isDigit() || it == '/' }
                        .ifBlank { set.label.take(2) }
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
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
                                .border(
                                    BorderStroke(1.2.dp, Color.White.copy(alpha = 0.42f)),
                                    RoundedCornerShape(999.dp),
                                ),
                        )
                    }
                    Text(
                        text = shortLabel,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = if (isActive) 10.sp else 9.sp),
                        fontWeight = if (isActive || isComplete) FontWeight.Black else FontWeight.Bold,
                        color = when {
                            isActive -> Color.White
                            isComplete -> Color.White.copy(alpha = 0.92f)
                            else -> Color.White.copy(alpha = 0.52f)
                        },
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun StepperProgressPillNode(
    label: String,
    isActive: Boolean,
    isCompleted: Boolean,
    progress: Float,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val nodeWidth = 20.dp
    val nodeHeight = if (isActive) 34.dp else 30.dp

    val bgColor by animateColorAsState(
        targetValue = when {
            isCompleted -> STEPPER_SOFT_BLUE_WASH
            isActive -> Color(0xFF1C1C1E)
            else -> TIMELINE_NODE_SOLID_BG
        },
        animationSpec = tween(320),
        label = "pillBgColor",
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            isCompleted -> STEPPER_SOFT_BLUE.copy(alpha = 0.45f)
            isActive -> Color.White.copy(alpha = 0.35f)
            else -> Color.White.copy(alpha = 0.12f)
        },
        animationSpec = tween(320),
        label = "pillBorderColor",
    )

    Surface(
        modifier = modifier
            .width(nodeWidth)
            .height(nodeHeight)
            .clip(RoundedCornerShape(999.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        shape = RoundedCornerShape(999.dp),
        color = bgColor,
        border = BorderStroke(
            width = if (isActive || isCompleted) 1.2.dp else 1.dp,
            color = borderColor,
        ),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                fontWeight = if (isActive || isCompleted) FontWeight.Black else FontWeight.Bold,
                color = when {
                    isCompleted -> STEPPER_SOFT_BLUE
                    isActive -> Color.White
                    else -> Color.White.copy(alpha = 0.70f)
                },
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun RoundBadgeNode(
    roundIndex: Int,
    isCurrentRound: Boolean,
    isExpanded: Boolean = true,
    isAllDone: Boolean,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .width(22.dp)
            .height(34.dp)
            .clip(RoundedCornerShape(999.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        shape = RoundedCornerShape(999.dp),
        color = when {
            isAllDone -> STEPPER_SOFT_BLUE_WASH
            isCurrentRound || isExpanded -> accent.copy(alpha = 0.22f).compositeOver(TIMELINE_NODE_SOLID_BG)
            else -> TIMELINE_NODE_SOLID_BG
        },
        border = BorderStroke(
            width = if (isCurrentRound || isExpanded) 1.6.dp else 1.dp,
            color = when {
                isAllDone -> STEPPER_SOFT_BLUE
                isCurrentRound || isExpanded -> accent
                else -> Color.White.copy(alpha = 0.22f)
            },
        ),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "R${roundIndex + 1}",
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp),
                fontWeight = FontWeight.Black,
                color = when {
                    isAllDone -> STEPPER_SOFT_BLUE
                    isCurrentRound || isExpanded -> accent
                    else -> Color.White.copy(alpha = 0.65f)
                },
            )
        }
    }
}