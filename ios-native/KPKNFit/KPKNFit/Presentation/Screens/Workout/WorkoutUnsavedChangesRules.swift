import Foundation

enum WorkoutPendingSetAction {
    case navigate(setIdx: Int)
    case edit(setIdx: Int, side: String?)
}

func pendingSetNavigationAction(
    hasPendingDraftChanges: Bool,
    activeSetIdx: Int,
    targetSetIdx: Int
) -> WorkoutPendingSetAction? {
    guard targetSetIdx != activeSetIdx else { return nil }
    return hasPendingDraftChanges ? .navigate(setIdx: targetSetIdx) : nil
}

func pendingSetEditAction(
    hasPendingDraftChanges: Bool,
    isAlreadyEditingCurrentSet: Bool,
    targetSetIdx: Int,
    side: String? = nil
) -> WorkoutPendingSetAction? {
    guard !isAlreadyEditingCurrentSet else { return nil }
    return hasPendingDraftChanges ? .edit(setIdx: targetSetIdx, side: side) : nil
}
