                Surface(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onAddSet() },
                    shape = CircleShape,
                    color = StepperFill,
                    border = null,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Añadir serie",
                            tint = StepperOnFill,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }