    val nodeWidth = StepperPillWidth
    val nodeHeight = if (isActive) StepperPillHeightActive else StepperPillHeight

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
        color = StepperFill,
        border = null,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (isCompleted) "✓" else label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                fontWeight = FontWeight.Black,
                color = StepperOnFill,
                maxLines = 1,
            )
        }
    }
}