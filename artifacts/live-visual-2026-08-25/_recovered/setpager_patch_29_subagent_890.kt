    // Hit column 48dp; visual rail 23dp nodes. Continuous rail may be drawn by parent stage.
    Box(
        modifier = modifier
            .width(WorkoutLiveVisualTokens.RailHitWidth)
            .fillMaxHeight(),
        contentAlignment = Alignment.TopCenter,
    ) {
        if (drawRail) {
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.White.copy(alpha = 0.25f)),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(WorkoutLiveVisualTokens.NodeGap),
        ) {