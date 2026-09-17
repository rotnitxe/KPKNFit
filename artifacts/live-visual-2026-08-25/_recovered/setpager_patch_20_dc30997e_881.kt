    Surface(
        modifier = modifier
            .width(StepperPillWidth)
            .height(StepperPillHeight)
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
                text = if (isAllDone && !isCurrentRound) "✓" else "R${roundIndex + 1}",
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 9.sp),
                fontWeight = FontWeight.Black,
                color = StepperOnFill,
            )
        }
    }
}