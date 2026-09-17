                            is TimelineElement.BilateralSet -> {
                                val isActive = element.state == WorkoutSetCardVisualState.ACTIVE
                                val isComplete = element.state == WorkoutSetCardVisualState.COMPLETED
                                val isSkipped = element.state == WorkoutSetCardVisualState.SKIPPED
                                TimelinePill(
                                    active = isActive || element.isEditing,
                                    complete = isComplete,
                                    skipped = isSkipped,
                                    label = element.label,
                                    onActiveAnchorInParent = onActiveAnchorInParent,
                                    modifier = Modifier
                                        .combinedClickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                            onClick = { onSelectPage(element.pageIndex) },
                                            onLongClick = if (onLongPressPage != null) {
                                                { onLongPressPage(element.pageIndex) }
                                            } else null,
                                        ),
                                )
                            }
                            is TimelineElement.UnilateralSet -> {
                                UnilateralSetStackNode(
                                    setLabel = element.setLabel,
                                    leftPageIndex = element.leftPageIndex,
                                    leftState = element.leftState,
                                    rightPageIndex = element.rightPageIndex,
                                    rightState = element.rightState,
                                    onSelectPage = onSelectPage,
                                    onActiveAnchorInParent = onActiveAnchorInParent,
                                )
                            }