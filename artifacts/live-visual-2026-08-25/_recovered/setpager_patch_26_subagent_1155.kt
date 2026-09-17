                        is TimelineElement.MobilityPill -> {
                            StepperProgressPillNode(
                                label = "M",
                                isActive = element.isCurrent,
                                isCompleted = element.isCompleted,
                                progress = if (element.isCompleted) 1f else element.progress,
                                onClick = element.onSelect,
                                onActiveAnchorInParent = onActiveAnchorInParent,
                            )
                        }
                        is TimelineElement.WarmupPill -> {
                            StepperProgressPillNode(
                                label = "A",
                                isActive = element.isCurrent,
                                isCompleted = element.isCompleted,
                                progress = if (element.isCompleted) 1f else element.progress,
                                onClick = element.onSelect,
                                onActiveAnchorInParent = onActiveAnchorInParent,
                            )
                        }