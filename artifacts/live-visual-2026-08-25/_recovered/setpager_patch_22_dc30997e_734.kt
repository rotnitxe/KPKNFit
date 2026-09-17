private val StepperFill = WorkoutUiTokens.SoftWhite
private val StepperOnFill = Color.Black
private val StepperPillWidth = 22.dp
private val StepperPillHeight = 30.dp
private val StepperPillHeightActive = 34.dp
private val StepperDotSize = 4.dp

internal sealed class TimelineElement {
    data class RoundBadge(
        val roundIndex: Int,
        val isCurrentRound: Boolean,
        val isAllDone: Boolean,
        val firstPageIndex: Int,
    ) : TimelineElement()

    data class BilateralSet(
        val roundIndex: Int? = null,
        val pageIndex: Int,
        val label: String,
        val state: WorkoutSetCardVisualState,
        val isEditing: Boolean = false,
    ) : TimelineElement()

    data class UnilateralSet(