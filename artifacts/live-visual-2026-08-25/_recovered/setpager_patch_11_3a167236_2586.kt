@Composable
private fun TimelinePill(
    active: Boolean,
    complete: Boolean,
    skipped: Boolean,
    label: String,
    modifier: Modifier = Modifier,
) {
    val width = 20.dp
    val height by animateDpAsState(
        targetValue = if (active) 34.dp else 28.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "setTimelinePillHeight",
    )
    val fillTarget = when {
        active -> Color(0xFF1C1C1E)
        complete -> STEPPER_SOFT_BLUE_WASH
        skipped -> Color(0xFF141416)
        else -> Color(0xFF141416)
    }
    val fillColor by animateColorAsState(
        targetValue = fillTarget,
        animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
        label = "setTimelinePillFill",
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            complete -> STEPPER_SOFT_BLUE.copy(alpha = 0.45f)
            active -> Color.White.copy(alpha = 0.35f)
            skipped -> Color.White.copy(alpha = 0.12f)
            else -> Color.White.copy(alpha = 0.12f)
        },
        animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
        label = "setTimelinePillBorder",
    )
    val borderWidth by animateDpAsState(
        targetValue = if (active) 1.5.dp else 1.dp,
        animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
        label = "setTimelinePillBorderWidth",
    )
    val shortLabel = remember(label) {
        label
            .replace("Serie ", "")
            .takeWhile { it.isDigit() || it == '/' }
            .ifBlank { label.take(2) }
    }
    Surface(
        modifier = modifier
            .width(width)
            .height(height),
        shape = RoundedCornerShape(999.dp),
        color = fillColor,
        border = BorderStroke(
            width = borderWidth,
            color = borderColor,
        ),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = shortLabel,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = if (active) 10.sp else 9.sp),
                fontWeight = FontWeight.Bold,
                color = when {
                    complete -> Color.White.copy(alpha = 0.92f)
                    active -> Color.White
                    skipped -> Color.White.copy(alpha = 0.5f)
                    else -> Color.White.copy(alpha = 0.55f)
                },
                maxLines = 1,
            )
        }
    }
}