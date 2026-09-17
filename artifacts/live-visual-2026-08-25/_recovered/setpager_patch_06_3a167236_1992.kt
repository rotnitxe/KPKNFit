    val scrollState = rememberScrollState()
    val nodeSpacing = 14.dp
    val intraRoundSpacing = 12.dp
    val interRoundSpacing = 16.dp
    val collapsedRoundSpacing = 12.dp

    Surface(
        modifier = modifier
            .width(48.dp)
            .defaultMinSize(minHeight = 220.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.36f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 220.dp, max = 480.dp)
                .padding(horizontal = 5.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .weight(1f, fill = true)
                    .fillMaxWidth()
                    .heightIn(min = 160.dp),
                contentAlignment = Alignment.TopCenter,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        ContinuousTimelineTrack(
                            progress = timelineFillProgress,
                            fillColor = timelineProgressColor.copy(alpha = 0.72f),
                            trackColor = trackColor,
                            modifier = Modifier
                                .width(3.dp)
                                .fillMaxHeight(),
                            vertical = true,
                        )
                    }