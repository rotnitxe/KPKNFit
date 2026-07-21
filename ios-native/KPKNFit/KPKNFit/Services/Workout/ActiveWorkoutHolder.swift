import Foundation

final class ActiveWorkoutHolder {
    static let shared = ActiveWorkoutHolder()

    private weak var _viewModel: WorkoutViewModel?

    private init() {}

    func set(_ viewModel: WorkoutViewModel) {
        _viewModel = viewModel
    }

    func clear() {
        _viewModel = nil
    }

    func get() -> WorkoutViewModel? {
        _viewModel
    }

    func isActive() -> Bool {
        _viewModel != nil
    }

    func handleAction(_ action: TimerAction) {
        Task { @MainActor in
            _viewModel?.handleTimerAction(action)
        }
    }
}
