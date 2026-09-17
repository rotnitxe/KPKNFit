                        } else {
                            val leadPills = elements.takeWhile {
                                it is TimelineElement.MobilityPill || it is TimelineElement.WarmupPill
                            }
                            val bodyElements = elements.drop(leadPills.size)
                            val bilateralBody = bodyElements.filterIsInstance<TimelineElement.BilateralSet>()
                            val useSeriesCapsule = bilateralBody.isNotEmpty() &&
                                bodyElements.all { it is TimelineElement.BilateralSet }

                            leadPills.forEach { element ->
                                when (element) {
                                    is TimelineElement.MobilityPill -> {
                                        StepperProgressPillNode(
                                            label = "MOV",
                                            isActive = element.isCurrent,
                                            isCompleted = element.isCompleted,
                                            progress = if (element.isCompleted) 1f else element.progress,
                                            accent = accent,
                                            onClick = element.onSelect,
                                        )
                                    }
                                    is TimelineElement.WarmupPill -> {
                                        StepperProgressPillNode(
                                            label = "APR",
                                            isActive = element.isCurrent,
                                            isCompleted = element.isCompleted,
                                            progress = if (element.isCompleted) 1f else element.progress,
                                            accent = accent,
                                            onClick = element.onSelect,
                                        )
                                    }
                                    else -> {}
                                }
                            }

                            if (useSeriesCapsule) {
                                SeriesProgressCapsule(
                                    sets = bilateralBody,
                                    onSelectPage = onSelectPage,
                                    onLongPressPage = onLongPressPage,
                                )
                            } else {
                                bodyElements.forEach { element ->
                                    when (element) {
                                        is TimelineElement.BilateralSet -> {
                                            val isActive = element.state == WorkoutSetCardVisualState.ACTIVE
                                            val isComplete = element.state == WorkoutSetCardVisualState.COMPLETED
                                            val isSkipped = element.state == WorkoutSetCardVisualState.SKIPPED
                                            TimelinePill(
                                                active = isActive || element.isEditing,
                                                complete = isComplete,
                                                skipped = isSkipped,
                                                label = element.label,
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
                                                accent = accent,
                                                onSelectPage = onSelectPage,
                                            )
                                        }
                                        is TimelineElement.MobilityPill -> {
                                            StepperProgressPillNode(
                                                label = "MOV",
                                                isActive = element.isCurrent,
                                                isCompleted = element.isCompleted,
                                                progress = if (element.isCompleted) 1f else element.progress,
                                                accent = accent,
                                                onClick = element.onSelect,
                                            )
                                        }
                                        is TimelineElement.WarmupPill -> {
                                            StepperProgressPillNode(
                                                label = "APR",
                                                isActive = element.isCurrent,
                                                isCompleted = element.isCompleted,
                                                progress = if (element.isCompleted) 1f else element.progress,
                                                accent = accent,
                                                onClick = element.onSelect,
                                            )
                                        }
                                        is TimelineElement.RoundBadge -> {}
                                    }
                                }
                            }
                        }