from pathlib import Path

p = Path(r"app/src/main/java/com/example/kpkn/screens/workout/WorkoutV2Body.kt")
t = p.read_text(encoding="utf-8")

old = """                    val canAddSet = isInteractive && !currentExercise.isCardio && !belongsToSuperset
                    WorkoutLiveMatchHeightRow(
                        stageKey = currentExercise.id,
                        pointCount = timelineElements.size,
                        hasAddSet = canAddSet,
                        stepper = {
                    WorkoutSetPager(
                        elements = timelineElements,
                        activeElementIndex = activeTimelineElementIndex,
                        completedCount = totalTimelineCompletedCount,
                        totalCount = totalTimelineSetsCount,
                        modifier = Modifier.fillMaxSize(),
                        drawRail = true,
                        onActiveAnchorInParent = null,
                        onSelectPage = { pageIndex ->
"""

new = """                    val canAddSet = isInteractive && !currentExercise.isCardio && !belongsToSuperset
                    val activeNodeKey = timelineElements.getOrNull(activeTimelineElementIndex)?.let { el ->
                        when (el) {
                            is TimelineElement.MobilityPill -> "mobility"
                            is TimelineElement.WarmupPill -> "warmup"
                            is TimelineElement.BilateralSet -> "set-${el.pageIndex}"
                            is TimelineElement.UnilateralSet -> "uni-${el.leftPageIndex}-${el.rightPageIndex}"
                            is TimelineElement.RoundBadge -> "round-${el.roundIndex}"
                        }
                    }
                    WorkoutLiveConnectedStage(
                        stageKey = currentExercise.id,
                        activeNodeId = activeNodeKey,
                        headerReserve = 0.dp,
                        dockReserve = 24.dp,
                        showAddTail = canAddSet,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        stepper = {
                    WorkoutSetPager(
                        elements = timelineElements,
                        activeElementIndex = activeTimelineElementIndex,
                        completedCount = totalTimelineCompletedCount,
                        totalCount = totalTimelineSetsCount,
                        modifier = Modifier.fillMaxSize(),
                        drawRail = false,
                        onActiveAnchorInParent = { anchor -> reportActiveNodeAnchor(anchor) },
                        onSelectPage = { pageIndex ->
"""

if old not in t:
    print("OPEN MISS")
else:
    t = t.replace(old, new, 1)
    old2 = """                        },
                        instance = {
                    Column(modifier = Modifier.fillMaxWidth()) {
"""
    new2 = """                        },
                        card = {
                    Column(modifier = Modifier.fillMaxWidth()) {
"""
    if old2 not in t:
        print("INSTANCE MISS")
    else:
        t = t.replace(old2, new2, 1)
        print("instance->card ok")
    p.write_text(t, encoding="utf-8")
    print("connected stage wired")
