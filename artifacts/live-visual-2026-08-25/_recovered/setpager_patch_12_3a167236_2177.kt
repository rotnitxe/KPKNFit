    val segmentHeight = 52.dp
    val pillWidth = 28.dp
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
                .background(Color(0xFF0B1220))
                .border(BorderStroke(1.5.dp, STEPPER_SOFT_BLUE), RoundedCornerShape(999.dp)),
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .fillMaxHeight(fillProgress.coerceAtLeast(0.12f))
                .clip(RoundedCornerShape(999.dp))
                .background(STEPPER_SOFT_BLUE_FILL),
        )
        Column(modifier = Modifier.fillMaxSize()) {
            sets.forEach { set ->
                val isActive = set.state == WorkoutSetCardVisualState.ACTIVE || set.isEditing
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
                                    BorderStroke(1.5.dp, Color.White.copy(alpha = 0.55f)),
                                    RoundedCornerShape(999.dp),
                                ),
                        )
                    }
                }
            }
        }
    }