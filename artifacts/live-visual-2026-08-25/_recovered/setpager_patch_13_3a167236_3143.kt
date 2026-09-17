    val segmentHeight = 36.dp
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