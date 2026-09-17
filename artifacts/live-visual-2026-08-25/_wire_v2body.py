from pathlib import Path

p = Path(r"C:\Users\valen\Documents\KPKNFit\android-native\app\src\main\java\com\example\kpkn\screens\workout\WorkoutV2Body.kt")
t = p.read_text(encoding="utf-8")

start = t.find("                    WorkoutSetPager(")
if start < 0:
    raise SystemExit("SetPager not found")

i = start
depth = 0
end = None
while i < len(t):
    ch = t[i]
    if ch == "(":
        depth += 1
    elif ch == ")":
        depth -= 1
        if depth == 0:
            end = i + 1
            while end < len(t) and t[end] in " \t":
                end += 1
            if end < len(t) and t[end] == "\n":
                end += 1
            break
    i += 1
if end is None:
    raise SystemExit("end not found")

new = r'''                    val canAddSet = !currentExercise.isCardio && currentSupersetGroupId == null
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
                            LiveRoadmapStepper(
                                elements = timelineElements,
                                activeElementIndex = activeTimelineElementIndex,
                                onSelectPage = { pageIndex ->
                                    val targetPage = setPagerPages.getOrNull(pageIndex)
                                    if (targetPage != null) {
                                        val targetExerciseId = targetPage.exerciseId ?: currentExercise.id
                                        val key = when (targetPage.type) {
                                            LivePageType.CARDIO -> WorkoutStepRules.cardioStepKey(targetExerciseId)
                                            LivePageType.NORMAL -> WorkoutStepRules.workingStepKey(targetExerciseId, targetPage.setIndex, targetPage.side)
                                        }
                                        if (key.isNotBlank()) {
                                            viewModel.selectWorkoutStep(key)
                                        }
                                    }
                                },
                                onLongPressPage = { pageIndex ->
                                    val page = setPagerPages.getOrNull(pageIndex) ?: return@LiveRoadmapStepper
                                    val exId = page.exerciseId ?: currentExercise.id
                                    viewModel.showSeriesTypeSheet(exId, page.setIndex, null)
                                },
                                onAddSet = if (canAddSet) {{ viewModel.addSetToCurrentExercise() }} else null,
                                reportActiveNodeAnchor = { coords -> reportActiveNodeAnchor(coords) },
                            )
                        },
                        card = {
'''

t2 = t[:start] + new + t[end:]
hp = t2.find("                    HorizontalPager(", start)
if hp < 0:
    raise SystemExit("HorizontalPager not found after insert")
else_start = t2.rfind("} else {", start, hp)
if else_start < 0:
    raise SystemExit("else before HorizontalPager not found")

j = else_start + len("} else {")
depth = 1
while j < len(t2) and depth > 0:
    if t2[j] == "{":
        depth += 1
    elif t2[j] == "}":
        depth -= 1
    j += 1
else_end = j

close = """
                        }
                    )
"""
t3 = t2[:else_end] + close + t2[else_end:]
out = Path(r"C:\Users\valen\Documents\KPKNFit\artifacts\live-visual-2026-08-25\_V2Body_wired.kt")
out.write_text(t3, encoding="utf-8", newline="\n")
print("OK wired temp", out, "start", start, "else_end", else_end, "len", len(t3))
# Check for NULs
if "\x00" in t3:
    print("WARNING null bytes in output")
