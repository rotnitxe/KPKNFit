    val width = StepperPillWidth
    val height = if (active) StepperPillHeightActive else StepperPillHeight
    val shortLabel = remember(label) {
        label
            .replace("Serie ", "")
            .takeWhile { it.isDigit() }
            .ifBlank { label.take(2) }
    }
    Surface(
        modifier = modifier
            .width(width)
            .height(height),
        shape = RoundedCornerShape(999.dp),
        color = StepperFill,
        border = null,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = if (complete && !active) "✓" else shortLabel,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = if (active) 10.sp else 9.sp),
                fontWeight = FontWeight.Black,
                color = StepperOnFill,
                maxLines = 1,
            )
        }
    }
}