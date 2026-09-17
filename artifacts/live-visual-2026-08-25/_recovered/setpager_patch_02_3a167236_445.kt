    Surface(
        modifier = modifier
            .width(44.dp)
            .then(
                if (modifier == Modifier) Modifier.heightIn(min = 220.dp, max = 480.dp)
                else Modifier
            )
            .fillMaxHeight(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.36f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {