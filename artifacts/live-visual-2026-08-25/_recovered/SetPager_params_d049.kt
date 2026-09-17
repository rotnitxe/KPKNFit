    onAddSet: (() -> Unit)? = null,
    onLongPressPage: ((Int) -> Unit)? = null,
    drawRail: Boolean = true,
    onActiveAnchorInParent: ((Offset) -> Unit)? = null,
) {
    if (elements.isEmpty()) return

    val activeElement = elements.getOrNull(activeElementIndex)