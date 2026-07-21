import SwiftUI

enum RoadmapMode {
    case compact, expanded
}

struct WorkoutRoadmapBar: View {
    let exercises: [Exercise]
    let parts: [SessionPart]
    let supersetGroups: [SupersetGroup]
    let currentIdx: Int
    let currentSetIdx: Int
    let completedSets: [String: CompletedSet]
    let onSelect: (Int) -> Void
    let onSelectGroup: (String) -> Void
    let onOpenContext: (String) -> Void
    let enableLongPress: Bool
    let sessionAccentColor: Color
    @Binding var mode: RoadmapMode

    @State private var listState: Int = 0

    var body: some View {
        let accentByPartId = Dictionary(uniqueKeysWithValues: parts.compactMap { part in
            if let hex = part.color, let color = Color(hex: hex) {
                return (part.id, color)
            }
            return nil
        })

        let roadmapGroups: [ExerciseRoadmapGroup] = {
            var emitted = Set<String>()
            return exercises.compactMap { exercise in
                let groupId = exercise.supersetGroupRefOrLegacyId()
                if let gid = groupId {
                    guard emitted.insert(gid).inserted else { return nil }
                    return ExerciseRoadmapGroup(groupId: gid, exercises: exercises.filter { $0.supersetGroupRefOrLegacyId() == gid })
                }
                return ExerciseRoadmapGroup(groupId: nil, exercises: [exercise])
            }
        }()

        VStack(spacing: 0) {
            // Drag handle
            Button(action: { mode = mode == .compact ? .expanded : .compact }) {
                HStack(spacing: 4) {
                    Image(systemName: "line.horizontal.3")
                        .font(.system(size: 14))
                    Image(systemName: mode == .expanded ? "chevron.down" : "chevron.up")
                        .font(.system(size: 12))
                }
                .foregroundColor(.white.opacity(0.4))
                .padding(.vertical, 8)
                .frame(maxWidth: .infinity)
            }

            if mode == .expanded {
                ScrollView {
                    VStack(spacing: 0) {
                        HStack {
                            Text("Cockpit de la Sesión")
                                .font(.system(size: 17, weight: .black))
                                .foregroundColor(.white)
                            Spacer()
                            let totalCompleted = exercises.reduce(0) { sum, e in
                                sum + e.sets.indices.reduce(0) { s, sIdx in
                                    s + e.completionKeysForSet(sIdx).filter { completedSets.keys.contains($0) }.count
                                }
                            }
                            let totalSets = exercises.reduce(0) { sum, e in
                                sum + e.sets.indices.reduce(0) { s, sIdx in s + e.completionKeysForSet(sIdx).count }
                            }
                            Text("Progreso total: \(totalCompleted)/\(totalSets)")
                                .font(.system(size: 11, weight: .bold))
                                .foregroundColor(sessionAccentColor)
                                .padding(.horizontal, 10)
                                .padding(.vertical, 4)
                                .background(sessionAccentColor.opacity(0.22))
                                .clipShape(Capsule())
                        }
                        .padding(.horizontal, 16)
                        .padding(.bottom, 12)

                        ForEach(Array(roadmapGroups.enumerated()), id: \.offset) { groupIdx, group in
                            let exercise = group.exercises.first!
                            let idx = exercises.firstIndex(where: { $0.id == exercise.id }) ?? 0
                            let part = parts.first { $0.exercises.contains { $0.id == exercise.id } }
                            let accent = accentByPartId[part?.id ?? ""] ?? sessionAccentColor
                            let completedCount = group.exercises.reduce(0) { sum, e in
                                sum + e.sets.indices.reduce(0) { s, sIdx in s + e.completionKeysForSet(sIdx).filter { completedSets.keys.contains($0) }.count }
                            }
                            let totalSets = group.exercises.reduce(0) { sum, e in
                                sum + e.sets.indices.reduce(0) { s, sIdx in s + e.completionKeysForSet(sIdx).count }
                            }
                            let isAllDone = completedCount >= totalSets && totalSets > 0
                            let isCurrent = group.exercises.contains { $0.id == exercises[safe: currentIdx]?.id }
                            let isLast = groupIdx == roadmapGroups.count - 1

                            HStack(alignment: .top) {
                                VStack(spacing: 0) {
                                    let dotColor: Color = isCurrent ? accent : isAllDone ? Color(red: 0.40, green: 0.73, blue: 0.42) : .white.opacity(0.25)
                                    Circle().fill(dotColor).frame(width: 12, height: 12)
                                        .padding(.top, 18)
                                    if !isLast {
                                        Rectangle()
                                            .fill(Color.white.opacity(0.20))
                                            .frame(width: 2)
                                            .frame(maxHeight: .infinity)
                                    } else {
                                        Spacer(minLength: 18)
                                    }
                                }
                                .frame(width: 36)

                                VStack(spacing: 0) {
                                    if group.groupId == nil || group.exercises.count == 1 {
                                        ExerciseRoadmapCardView(
                                            exercise: exercise,
                                            completedCount: completedCount,
                                            totalCount: totalSets,
                                            isCurrent: isCurrent,
                                            isAllDone: isAllDone,
                                            accent: accent,
                                            groupName: part?.name,
                                            onClick: { onSelect(idx) }
                                        )
                                    } else {
                                        let currentRound = isCurrent ? currentSetIdx + 1 : nil
                                        let totalRounds = group.groupId
                                            .flatMap { gid in supersetGroups.first(where: { $0.id == gid })?.rounds }
                                            .filter { $0 > 0 } ?? (group.exercises.max(by: { $0.sets.count < $1.sets.count })?.sets.count ?? 0)
                                        Text(currentRound.map { "Superserie • Ronda \($0)/\(totalRounds)" } ?? "Superserie • \(totalRounds) Rondas")
                                            .font(.system(size: 9, weight: .black))
                                            .foregroundColor(accent)
                                            .padding(.horizontal, 8)
                                            .padding(.vertical, 2)
                                            .background(accent.opacity(0.15))
                                            .clipShape(RoundedRectangle(cornerRadius: 4))
                                        SupersetRoadmapCardView(
                                            exercises: group.exercises,
                                            roundCount: totalRounds,
                                            completedSets: completedSets,
                                            isCurrent: isCurrent,
                                            isAllDone: isAllDone,
                                            accent: accent,
                                            groupName: part?.name,
                                            currentExerciseId: exercises[safe: currentIdx]?.id,
                                            currentRound: currentRound,
                                            onClick: { group.groupId.map { onSelectGroup($0) } }
                                        )
                                    }
                                }
                                .padding(.bottom, 16)
                            }
                            .padding(.horizontal, 16)
                        }
                    }
                }
                .frame(maxHeight: 380)
            }

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(Array(roadmapGroups.enumerated()), id: \.offset) { groupIdx, group in
                        let exercise = group.exercises.first!
                        let idx = exercises.firstIndex(where: { $0.id == exercise.id }) ?? 0
                        let part = parts.first { $0.exercises.contains { $0.id == exercise.id } }
                        let accent = accentByPartId[part?.id ?? ""] ?? sessionAccentColor
                        let completedCount = group.exercises.reduce(0) { sum, e in
                            sum + e.sets.indices.reduce(0) { s, sIdx in s + e.completionKeysForSet(sIdx).filter { completedSets.keys.contains($0) }.count }
                        }
                        let totalSets = group.exercises.reduce(0) { sum, e in
                            sum + e.sets.indices.reduce(0) { s, sIdx in s + e.completionKeysForSet(sIdx).count }
                        }
                        let isAllDone = completedCount >= totalSets && totalSets > 0
                        let isCurrent = group.exercises.contains { $0.id == exercises[safe: currentIdx]?.id }

                        if group.groupId == nil || group.exercises.count == 1 {
                            ExerciseRoadmapCardView(
                                exercise: exercise,
                                completedCount: completedCount,
                                totalCount: totalSets,
                                isCurrent: isCurrent,
                                isAllDone: isAllDone,
                                accent: accent,
                                groupName: part?.name,
                                onClick: { onSelect(idx) }
                            )
                        } else {
                            SupersetRoadmapCardView(
                                exercises: group.exercises,
                                roundCount: group.groupId
                                    .flatMap { gid in supersetGroups.first(where: { $0.id == gid })?.rounds }
                                    .filter { $0 > 0 } ?? (group.exercises.max(by: { $0.sets.count < $1.sets.count })?.sets.count ?? 0),
                                completedSets: completedSets,
                                isCurrent: isCurrent,
                                isAllDone: isAllDone,
                                accent: accent,
                                groupName: part?.name,
                                currentExerciseId: exercises[safe: currentIdx]?.id,
                                currentRound: isCurrent ? currentSetIdx + 1 : nil,
                                onClick: { group.groupId.map { onSelectGroup($0) } }
                            )
                        }
                    }
                }
                .padding(.bottom, 12)
            }
        }
        .background(Color.black.opacity(0.38))
        .clipShape(RoundedRectangle(cornerRadius: 24))
    }
}

private struct ExerciseRoadmapGroup {
    let groupId: String?
    let exercises: [Exercise]
}

private struct ExerciseRoadmapCardView: View {
    let exercise: Exercise
    let completedCount: Int
    let totalCount: Int
    let isCurrent: Bool
    let isAllDone: Bool
    let accent: Color
    let groupName: String?
    let onClick: () -> Void

    @State private var isPressed: Bool = false

    var body: some View {
        let minWidth: CGFloat = {
            switch exercise.name.count {
            case 30...: return 130
            case 22..<30: return 110
            default: return 88
            }
        }()

        Button(action: onClick) {
            HStack(spacing: 6) {
                Text(isAllDone ? "✓" : "\(completedCount)/\(totalCount)")
                    .font(.system(size: 11, weight: .bold))
                    .foregroundColor(isCurrent ? .white : .white)
                    .padding(.horizontal, 6)
                    .padding(.vertical, 2)
                    .background(isCurrent ? Color.white.opacity(0.20) : accent.opacity(0.28))
                    .clipShape(Capsule())
                VStack(alignment: .leading) {
                    Text(exercise.name)
                        .font(.system(size: 11, weight: .bold))
                        .foregroundColor(isCurrent ? .white : .white)
                        .lineLimit(2)
                    if let gn = groupName, !gn.isEmpty {
                        Text(gn)
                            .font(.system(size: 9))
                            .foregroundColor(isCurrent ? .white.opacity(0.6) : .white.opacity(0.6))
                            .lineLimit(1)
                    }
                }
                Spacer()
            }
            .padding(.horizontal, 8)
            .padding(.vertical, 6)
            .frame(minWidth: minWidth, maxWidth: 170, minHeight: groupName != nil ? 60 : 48)
            .background(isCurrent ? accent.opacity(0.86) : isAllDone ? Color(red: 0.40, green: 0.73, blue: 0.42).opacity(0.30) : accent.opacity(0.28))
            .overlay(RoundedRectangle(cornerRadius: 20).stroke(isCurrent ? Color.white.opacity(0.16) : Color.white.opacity(0.22)))
            .clipShape(RoundedRectangle(cornerRadius: 20))
            .scaleEffect(isPressed ? 0.95 : 1.0)
        }
        .simultaneousGesture(
            DragGesture(minimumDistance: 0)
                .onChanged { _ in isPressed = true }
                .onEnded { _ in isPressed = false }
        )
    }
}

private struct SupersetRoadmapCardView: View {
    let exercises: [Exercise]
    let roundCount: Int
    let completedSets: [String: CompletedSet]
    let isCurrent: Bool
    let isAllDone: Bool
    let accent: Color
    let groupName: String?
    let currentExerciseId: String?
    let currentRound: Int?
    let onClick: () -> Void

    @State private var isPressed: Bool = false

    var body: some View {
        let safeRounds = max(roundCount, 1)
        let dotSize: CGFloat = 18
        let spacing: CGFloat = 7

        Button(action: onClick) {
            HStack(spacing: 10) {
                VStack(alignment: .leading, spacing: 3) {
                    Text("Superserie")
                        .font(.system(size: 13, weight: .black))
                        .foregroundColor(.white)
                        .lineLimit(2)
                    Text(isAllDone ? "Completada" : currentRound.map { "Ronda \($0)/\(safeRounds)" } ?? "\(safeRounds) rondas")
                        .font(.system(size: 10, weight: .bold))
                        .foregroundColor(isCurrent ? accent : .white.opacity(0.62))
                        .lineLimit(1)
                }
                .frame(minWidth: 82, maxWidth: 104)

                HStack(spacing: spacing) {
                    ForEach(0..<safeRounds, id: \.self) { roundIdx in
                        let roundKeys = exercises.flatMap { $0.completionKeysForSet(roundIdx) }
                        let roundDone = !roundKeys.isEmpty && roundKeys.allSatisfy { completedSets.keys.contains($0) }
                        let isRoundCurrent = isCurrent && currentRound == roundIdx + 1

                        ZStack {
                            Circle()
                                .fill(isRoundCurrent ? accent : roundDone ? Color(red: 0.40, green: 0.73, blue: 0.42) : Color.clear)
                                .frame(width: isRoundCurrent ? 24 : dotSize, height: isRoundCurrent ? 24 : dotSize)
                                .overlay(Circle().stroke(roundDone ? Color(red: 0.40, green: 0.73, blue: 0.42) : Color.white.opacity(0.42), lineWidth: isRoundCurrent ? 0 : 1.4))
                            Text("\(roundIdx + 1)")
                                .font(.system(size: 9, weight: .black))
                                .foregroundColor(isRoundCurrent || roundDone ? .black : .white.opacity(0.70))
                        }
                    }
                }
                .padding(.horizontal, 9)
                .padding(.vertical, 8)
                .background(Color(.systemGray5).opacity(isCurrent ? 0.30 : 0.18))
                .overlay(RoundedRectangle(cornerRadius: 20).stroke(Color.white.opacity(0.20)))
                .clipShape(RoundedRectangle(cornerRadius: 20))
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 9)
            .frame(minHeight: 68)
            .background(Color.white.opacity(isCurrent ? 0.18 : 0.14))
            .overlay(RoundedRectangle(cornerRadius: 20).stroke(
                isCurrent ? accent : isAllDone ? Color(red: 0.40, green: 0.73, blue: 0.42).opacity(0.62) : Color.white.opacity(0.30),
                lineWidth: isCurrent ? 1.5 : 1))
            .clipShape(RoundedRectangle(cornerRadius: 20))
            .scaleEffect(isPressed ? 0.95 : 1.0)
        }
        .simultaneousGesture(
            DragGesture(minimumDistance: 0)
                .onChanged { _ in isPressed = true }
                .onEnded { _ in isPressed = false }
        )
    }
}

private func guessMuscleGroup(exerciseName: String, partName: String?) -> String {
    let lower = exerciseName.lowercased()
    if lower.contains("press") || lower.contains("pecho") || lower.contains("bench") { return "PECHO" }
    if lower.contains("sentadilla") || lower.contains("squat") || lower.contains("prensa") || lower.contains("extensi") || lower.contains("zancada") || lower.contains("búlgar") || lower.contains("quad") { return "CUÁDRICEPS" }
    if lower.contains("peso muerto") || lower.contains("deadlift") || lower.contains("femoral") || lower.contains("isquio") || lower.contains("hip thrust") || lower.contains("glúteo") || lower.contains("curl fem") { return "FEMORAL/GLÚTEOS" }
    if lower.contains("dominadas") || lower.contains("pull up") || lower.contains("remo") || lower.contains("row") || lower.contains("espalda") || lower.contains("jalon") || lower.contains("lat ") { return "ESPALDA" }
    if lower.contains("militar") || lower.contains("hombro") || lower.contains("shoulder") || lower.contains("lateral raise") || lower.contains("vuelos") { return "HOMBROS" }
    if lower.contains("bicep") || lower.contains("tricep") || lower.contains("curl") || lower.contains("brazo") || lower.contains("copa") || lower.contains("fondos") { return "BRAZOS" }
    if lower.contains("abs") || lower.contains("plank") || lower.contains("core") || lower.contains("abdomen") || lower.contains("espin") { return "CORE" }
    return (partName?.uppercased()) ?? "GENERAL"
}

private extension Color {
    init?(hex: String) {
        let hex = hex.trimmingCharacters(in: CharacterSet.alphanumerics.inverted)
        var int: UInt64 = 0
        Scanner(string: hex).scanHexInt64(&int)
        let a, r, g, b: UInt64
        switch hex.count {
        case 6:
            (a, r, g, b) = (255, (int >> 16) & 0xFF, (int >> 8) & 0xFF, int & 0xFF)
        case 8:
            (a, r, g, b) = ((int >> 24) & 0xFF, (int >> 16) & 0xFF, (int >> 8) & 0xFF, int & 0xFF)
        default: return nil
        }
        self.init(red: Double(r) / 255, green: Double(g) / 255, blue: Double(b) / 255, opacity: Double(a) / 255)
    }
}

private extension Exercise {
    func supersetGroupRefOrLegacyId() -> String? {
        supersetGroupRef?.nilIfBlank ?? supersetId?.nilIfBlank
    }

    func isEffectivelyUnilateral() -> Bool {
        unilateralMode != .bilateral || isUnilateral
    }

    func completionKeysForSet(_ setIndex: Int) -> [String] {
        guard sets.indices.contains(setIndex) else { return [] }
        guard isEffectivelyUnilateral() else { return ["\(id)_\(setIndex)"] }
        let set = sets[setIndex]
        let hasLeftOnly = set.leftTarget != nil && set.rightTarget == nil
        let hasRightOnly = set.rightTarget != nil && set.leftTarget == nil
        if hasLeftOnly { return ["\(id)_\(setIndex)_L"] }
        if hasRightOnly { return ["\(id)_\(setIndex)_R"] }
        return ["\(id)_\(setIndex)_L", "\(id)_\(setIndex)_R"]
    }
}

private extension String {
    var nilIfBlank: String? {
        isEmpty ? nil : self
    }
}
