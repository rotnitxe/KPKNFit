import SwiftUI

struct WorkoutSetPager: View {
    let items: [WorkoutSetPagerItem]
    let activePageIndex: Int
    let onSelectPage: (Int) -> Void
    var sessionAccentColor: Color? = nil
    var isUnilateral: Bool = false
    var selectedSide: String? = nil
    var sideCompleted: ((Int, String) -> Bool)? = nil
    var completedPreviousSets: Int = 0
    var nextExerciseSetCount: Int = 0
    var onAddSet: (() -> Void)? = nil

    var body: some View {
        if items.isEmpty { EmptyView() }
        let accent = sessionAccentColor ?? .blue
        HStack(spacing: 0) {
            if completedPreviousSets > 0 {
                PreviousCompletedCluster(count: completedPreviousSets, accent: accent)
                    .padding(.trailing, 4)
            }
            ForEach(Array(items.enumerated()), id: \.offset) { index, item in
                let isActive = index == activePageIndex
                let accentColor = workoutSetPagerAccent(state: item.state, isWarmupOrFeedback: item.isWarmupOrFeedback, sessionAccentColor: sessionAccentColor)
                TimelineSegment(
                    accent: accentColor,
                    isActive: isActive,
                    isComplete: item.state == .completed,
                    isSkipped: item.state == .skipped,
                    isFirst: index == 0 && completedPreviousSets <= 0,
                    isLast: index == items.count - 1 && nextExerciseSetCount <= 0,
                    isConnectedPrev: index == 0 && completedPreviousSets > 0,
                    isConnectedNext: index == items.count - 1 && nextExerciseSetCount > 0,
                    label: item.label,
                    sideSpec: isUnilateral ? item.side : nil,
                    selectedSide: selectedSide,
                    sideCompleted: { side in sideCompleted?(item.index, side) == true }
                )
                .onTapGesture { onSelectPage(index) }
            }
            if nextExerciseSetCount > 0 {
                NextGhostCluster(count: nextExerciseSetCount, accent: accent)
                    .padding(.leading, 4)
            }
            if let onAdd = onAddSet {
                Spacer().frame(width: 4)
                Button(action: onAdd) {
                    Image(systemName: "plus")
                        .font(.system(size: 12, weight: .bold))
                        .foregroundColor(accent.opacity(0.78))
                        .frame(width: 22, height: 22)
                        .background(accent.opacity(0.22))
                        .clipShape(Circle())
                        .overlay(Circle().stroke(accent.opacity(0.42), lineWidth: 1))
                }
            }
        }
        .padding(.horizontal, 6)
        .padding(.vertical, 5)
        .frame(maxWidth: .infinity, maxHeight: 38)
        .background(Color(.systemGray6).opacity(0.26))
        .cornerRadius(999)
        .padding(.horizontal, 8)
        .padding(.vertical, 3)
    }
}

private struct TimelineSegment: View {
    let accent: Color
    let isActive: Bool
    let isComplete: Bool
    let isSkipped: Bool
    let isFirst: Bool
    let isLast: Bool
    let isConnectedPrev: Bool
    let isConnectedNext: Bool
    let label: String
    let sideSpec: String?
    let selectedSide: String?
    let sideCompleted: (String) -> Bool

    var body: some View {
        let lineColor: Color = {
            if isComplete { return accent.opacity(0.78) }
            if isSkipped { return accent.opacity(0.26) }
            return Color(.systemGray).opacity(0.22)
        }()
        HStack(spacing: 0) {
            Rectangle()
                .fill(isConnectedPrev ? lineColor : (isFirst ? lineColor.opacity(0.22) : lineColor))
                .frame(maxWidth: .infinity, maxHeight: 2)
                .cornerRadius(999)
            if let sides = sideSpec, !sides.isEmpty {
                SideTimelineCapsule(
                    sides: sides.split(separator: "|").filter { $0 == "left" || $0 == "right" }.map(String.init),
                    accent: accent,
                    active: isActive,
                    complete: isComplete,
                    selectedSide: selectedSide,
                    sideCompleted: sideCompleted
                )
            } else {
                TimelineDot(accent: accent, active: isActive, complete: isComplete, skipped: isSkipped, label: label)
            }
            Rectangle()
                .fill(isConnectedNext ? lineColor : (isLast ? lineColor.opacity(0.22) : lineColor))
                .frame(maxWidth: .infinity, maxHeight: 2)
                .cornerRadius(999)
        }
    }
}

private struct TimelineDot: View {
    let accent: Color
    let active: Bool
    let complete: Bool
    let skipped: Bool
    let label: String

    var body: some View {
        let size: CGFloat = active ? 22 : 17
        ZStack {
            Circle()
                .fill(active || complete ? accent : (skipped ? accent.opacity(0.26) : .clear))
                .frame(width: size, height: size)
                .overlay(Circle().stroke(!active && !complete ? accent.opacity(skipped ? 0.22 : 0.52) : .clear, lineWidth: active || complete ? 0 : 1.4))
            Text(label)
                .font(.system(size: active ? 8 : 7, weight: .black))
                .foregroundColor(active || complete ? .black : accent.opacity(0.78))
                .lineLimit(1)
        }
    }
}

private struct PreviousCompletedCluster: View {
    let count: Int
    let accent: Color

    var body: some View {
        let capCount = min(count, 12)
        HStack(spacing: 0) {
            ForEach(0..<capCount, id: \.self) { index in
                if index > 0 {
                    Rectangle()
                        .fill(accent.opacity(0.40))
                        .frame(width: 5, height: 2)
                        .cornerRadius(1)
                }
                Circle()
                    .fill(accent.opacity(0.55))
                    .frame(width: 6, height: 6)
            }
            if count > 12 {
                Spacer().frame(width: 2)
                Circle().fill(accent.opacity(0.35)).frame(width: 4, height: 4)
                Circle().fill(accent.opacity(0.25)).frame(width: 4, height: 4)
            }
            Rectangle()
                .fill(accent.opacity(0.50))
                .frame(width: 8, height: 2)
                .cornerRadius(1)
        }
    }
}

private struct NextGhostCluster: View {
    let count: Int
    let accent: Color

    var body: some View {
        let capCount = min(count, 8)
        HStack(spacing: 0) {
            Rectangle()
                .fill(accent.opacity(0.15))
                .frame(width: 8, height: 2)
                .cornerRadius(1)
            ForEach(0..<capCount, id: \.self) { index in
                Circle()
                    .stroke(accent.opacity(0.25), lineWidth: 1)
                    .frame(width: 8, height: 8)
                if index < capCount - 1 {
                    Rectangle()
                        .fill(accent.opacity(0.12))
                        .frame(width: 5, height: 2)
                        .cornerRadius(1)
                }
            }
            if count > 8 {
                Circle().stroke(accent.opacity(0.16), lineWidth: 0.8).frame(width: 4, height: 4)
                Circle().stroke(accent.opacity(0.10), lineWidth: 0.6).frame(width: 4, height: 4)
            }
        }
    }
}

private struct SideTimelineCapsule: View {
    let sides: [String]
    let accent: Color
    let active: Bool
    let complete: Bool
    let selectedSide: String?
    let sideCompleted: (String) -> Bool

    var body: some View {
        let displaySides = sides.isEmpty ? ["left", "right"] : sides
        HStack(spacing: 5) {
            ForEach(displaySides, id: \.self) { side in
                let sideDone = sideCompleted(side) || complete
                let selected = active && selectedSide == side
                Circle()
                    .fill(sideDone || selected ? accent : .clear)
                    .frame(width: selected ? 10 : 8, height: selected ? 10 : 8)
                    .overlay(Circle().stroke(!sideDone && !selected ? accent.opacity(0.50) : .clear, lineWidth: 1))
            }
        }
        .padding(.horizontal, 7)
        .padding(.vertical, 5)
        .background(active ? accent.opacity(0.18) : (complete ? accent.opacity(0.12) : Color(.systemGray).opacity(0.22)))
        .cornerRadius(999)
        .overlay(RoundedRectangle(cornerRadius: 999).stroke(active ? accent : Color(.systemGray).opacity(0.28), lineWidth: active ? 1.4 : 1))
    }
}
