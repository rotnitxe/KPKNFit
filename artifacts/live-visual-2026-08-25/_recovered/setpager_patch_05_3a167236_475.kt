    val accent = Color.White
    val timelineProgressColor = STEPPER_SOFT_BLUE
    val timelineFillTarget = if (totalCount <= 0) 0f else (completedCount.toFloat() / totalCount).coerceIn(0f, 1f)
    val timelineFillProgress by animateFloatAsState(
        targetValue = timelineFillTarget,
        animationSpec = tween(durationMillis = 520, easing = FastOutSlowInEasing),
        label = "setTimelineContinuousFill",
    )
    val trackColor = Color.White.copy(alpha = 0.14f)