        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(progress.coerceIn(0.18f, 1f))
                    .align(Alignment.BottomCenter)
                    .background(STEPPER_SOFT_BLUE_FILL.copy(alpha = if (isCompleted) 1f else 0.72f)),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                fontWeight = if (isActive || isCompleted) FontWeight.Black else FontWeight.Bold,
                color = when {
                    isCompleted -> Color.White
                    isActive -> Color.White
                    else -> Color.White.copy(alpha = 0.70f)
                },
                maxLines = 1,
            )
        }