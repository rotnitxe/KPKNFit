@Composable
private fun UnilateralSetStackNode(
    setLabel: String?,
    leftPageIndex: Int?,
    leftState: WorkoutSetCardVisualState,
    rightPageIndex: Int?,
    rightState: WorkoutSetCardVisualState,
    accent: Color,
    onSelectPage: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasActive = leftState == WorkoutSetCardVisualState.ACTIVE || rightState == WorkoutSetCardVisualState.ACTIVE
    val isAllComplete = leftState == WorkoutSetCardVisualState.COMPLETED && rightState == WorkoutSetCardVisualState.COMPLETED
    val shortLabel = setLabel
        ?.replace("Serie ", "")
        ?.takeWhile { it.isDigit() || it == '/' }
        ?.ifBlank { null }
        ?: setLabel?.take(3)
        ?: "S"

    Surface(
        modifier = modifier.width(24.dp),
        shape = RoundedCornerShape(999.dp),
        color = when {
            isAllComplete -> STEPPER_SOFT_BLUE_WASH
            hasActive -> accent.copy(alpha = 0.22f).compositeOver(TIMELINE_NODE_SOLID_BG)
            else -> TIMELINE_NODE_SOLID_BG
        },
        border = BorderStroke(
            width = if (hasActive) 1.6.dp else 1.dp,
            color = when {
                hasActive -> accent
                isAllComplete -> STEPPER_SOFT_BLUE
                else -> Color.White.copy(alpha = 0.22f)
            },
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 2.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = shortLabel,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp),
                fontWeight = FontWeight.Black,
                color = when {
                    hasActive -> accent
                    isAllComplete -> STEPPER_SOFT_BLUE
                    else -> Color.White.copy(alpha = 0.65f)
                },
                maxLines = 1,
            )
            UnilateralDotNode(
                label = "L",
                state = leftState,
                accent = accent,
                onClick = { leftPageIndex?.let { onSelectPage(it) } },
            )
            UnilateralDotNode(
                label = "R",
                state = rightState,
                accent = accent,
                onClick = { rightPageIndex?.let { onSelectPage(it) } },
            )
        }
    }
}

@Composable
private fun UnilateralDotNode(
    label: String,
    state: WorkoutSetCardVisualState,
    accent: Color,
    onClick: () -> Unit,
) {
    val isActive = state == WorkoutSetCardVisualState.ACTIVE
    val isComplete = state == WorkoutSetCardVisualState.COMPLETED
    val height by animateDpAsState(
        targetValue = if (isActive) 22.dp else 18.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "unilateralPillHeight",
    )
    Surface(
        modifier = Modifier
            .width(16.dp)
            .height(height)
            .clip(RoundedCornerShape(999.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        shape = RoundedCornerShape(999.dp),
        color = when {
            isActive -> Color(0xFF1C1C1E)
            isComplete -> STEPPER_SOFT_BLUE_WASH
            else -> Color(0xFF141416)
        },
        border = BorderStroke(
            width = if (isActive) 1.5.dp else 1.dp,
            color = when {
                isActive -> Color.White.copy(alpha = 0.35f)
                isComplete -> STEPPER_SOFT_BLUE.copy(alpha = 0.45f)
                else -> Color.White.copy(alpha = 0.12f)
            },
        ),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = if (isActive) 9.sp else 8.sp),
                fontWeight = FontWeight.Bold,
                color = if (isComplete) Color.White.copy(alpha = 0.92f) else Color.White.copy(alpha = 0.85f),
            )
        }
    }
}