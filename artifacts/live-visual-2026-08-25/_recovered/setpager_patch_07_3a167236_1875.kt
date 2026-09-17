                                                    is TimelineElement.BilateralSet -> {
                                                        val isActive = setElement.state == WorkoutSetCardVisualState.ACTIVE
                                                        val isComplete = setElement.state == WorkoutSetCardVisualState.COMPLETED
                                                        val isSkipped = setElement.state == WorkoutSetCardVisualState.SKIPPED
                                                        TimelinePill(
                                                            active = isActive || setElement.isEditing,
                                                            complete = isComplete,
                                                            skipped = isSkipped,
                                                            label = setElement.label,
                                                            modifier = Modifier
                                                                .combinedClickable(
                                                                    interactionSource = remember { MutableInteractionSource() },
                                                                    indication = null,
                                                                    onClick = { onSelectPage(setElement.pageIndex) },
                                                                    onLongClick = if (onLongPressPage != null) {
                                                                        { onLongPressPage(setElement.pageIndex) }
                                                                    } else null,
                                                                ),
                                                        )
                                                    }