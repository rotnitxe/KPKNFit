    val scrollState = rememberScrollState()

    Box(
        modifier = modifier
            .width(48.dp)
            .fillMaxHeight(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .width(2.5.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(999.dp))
                .background(Color.White.copy(alpha = 0.42f)),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {