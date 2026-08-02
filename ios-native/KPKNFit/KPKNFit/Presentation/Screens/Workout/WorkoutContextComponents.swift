import SwiftUI

// MARK: - Enum

enum WorkoutExerciseContextTab: String, CaseIterable {
    case history
    case tags
    case drain
    case energy
    case replace
    case edit
    case rmCalc
}

// MARK: - WorkoutExerciseTabs

struct WorkoutExerciseTabs: View {
    let currentExercise: Exercise
    let currentSet: ExerciseSet
    let currentExerciseInfo: ExerciseMuscleInfo?
    let drain: PredictedDrain
    let exerciseTag: String?
    let profiles: [WorkoutContextProfile]
    let activeProfileId: String?
    let selectedTab: WorkoutExerciseContextTab?
    let onSelectedTabChange: (WorkoutExerciseContextTab?) -> Void
    let onTagSet: (String) -> Void
    let onSelectProfile: (String) -> Void
    let onSaveProfile: (WorkoutContextProfile) -> Void
    let onUpdateExercise: ((Exercise) -> Exercise) -> Void
    let onUpdateCurrentSetPlan: (String, (ExerciseSet) -> ExerciseSet) -> Void
    let onExpandHistory: () -> Void
    let onExpandTags: () -> Void
    let onExpandSetup: () -> Void
    let onExpandReplace: () -> Void
    let onExpandEdit: () -> Void
    let sessionAccentColor: Color
    let sessionEnergy: SessionEnergySummary
    let ghostSet: CompletedSet?
    let rmBodyWeight: Double?
    let rmCurrentLoadMode: LoadModeV2?
    let onRmWeightSelected: ((Double) -> Void)?
    let allowExerciseManagementActions: Bool
    let userTags: [String]
    let exerciseReadiness: ExerciseReadiness?
    let userWorkoutTags: [WorkoutTag]
    let activeMainTagIds: [String]
    let activeSubTagIds: [String]
    let onMainTagToggle: (String) -> Void
    let onSubTagToggle: (String) -> Void
    let onCreateTag: (String) -> Void
    let onDeleteTag: (String) -> Void
    let onAddSubTag: (String, String, SubTagCategory) -> Void
    let onRemoveSubTag: (String, String) -> Void

    init(
        currentExercise: Exercise,
        currentSet: ExerciseSet,
        currentExerciseInfo: ExerciseMuscleInfo? = nil,
        drain: PredictedDrain = PredictedDrain(cns: 0, muscular: 0, spinal: 0),
        exerciseTag: String? = nil,
        profiles: [WorkoutContextProfile] = [],
        activeProfileId: String? = nil,
        selectedTab: WorkoutExerciseContextTab? = nil,
        onSelectedTabChange: @escaping (WorkoutExerciseContextTab?) -> Void = { _ in },
        onTagSet: @escaping (String) -> Void = { _ in },
        onSelectProfile: @escaping (String) -> Void = { _ in },
        onSaveProfile: @escaping (WorkoutContextProfile) -> Void = { _ in },
        onUpdateExercise: @escaping ((Exercise) -> Exercise) -> Void = { _ in },
        onUpdateCurrentSetPlan: @escaping (String, (ExerciseSet) -> ExerciseSet) -> Void = { _, _ in },
        onExpandHistory: @escaping () -> Void = {},
        onExpandTags: @escaping () -> Void = {},
        onExpandSetup: @escaping () -> Void = {},
        onExpandReplace: @escaping () -> Void = {},
        onExpandEdit: @escaping () -> Void = {},
        sessionAccentColor: Color = .blue,
        sessionEnergy: SessionEnergySummary = SessionEnergySummary(),
        ghostSet: CompletedSet? = nil,
        rmBodyWeight: Double? = nil,
        rmCurrentLoadMode: LoadModeV2? = nil,
        onRmWeightSelected: ((Double) -> Void)? = nil,
        allowExerciseManagementActions: Bool = true,
        userTags: [String] = [],
        exerciseReadiness: ExerciseReadiness? = nil,
        userWorkoutTags: [WorkoutTag] = [],
        activeMainTagIds: [String] = [],
        activeSubTagIds: [String] = [],
        onMainTagToggle: @escaping (String) -> Void = { _ in },
        onSubTagToggle: @escaping (String) -> Void = { _ in },
        onCreateTag: @escaping (String) -> Void = { _ in },
        onDeleteTag: @escaping (String) -> Void = { _ in },
        onAddSubTag: @escaping (String, String, SubTagCategory) -> Void = { _, _, _ in },
        onRemoveSubTag: @escaping (String, String) -> Void = { _, _ in }
    ) {
        self.currentExercise = currentExercise
        self.currentSet = currentSet
        self.currentExerciseInfo = currentExerciseInfo
        self.drain = drain
        self.exerciseTag = exerciseTag
        self.profiles = profiles
        self.activeProfileId = activeProfileId
        self.selectedTab = selectedTab
        self.onSelectedTabChange = onSelectedTabChange
        self.onTagSet = onTagSet
        self.onSelectProfile = onSelectProfile
        self.onSaveProfile = onSaveProfile
        self.onUpdateExercise = onUpdateExercise
        self.onUpdateCurrentSetPlan = onUpdateCurrentSetPlan
        self.onExpandHistory = onExpandHistory
        self.onExpandTags = onExpandTags
        self.onExpandSetup = onExpandSetup
        self.onExpandReplace = onExpandReplace
        self.onExpandEdit = onExpandEdit
        self.sessionAccentColor = sessionAccentColor
        self.sessionEnergy = sessionEnergy
        self.ghostSet = ghostSet
        self.rmBodyWeight = rmBodyWeight
        self.rmCurrentLoadMode = rmCurrentLoadMode
        self.onRmWeightSelected = onRmWeightSelected
        self.allowExerciseManagementActions = allowExerciseManagementActions
        self.userTags = userTags
        self.exerciseReadiness = exerciseReadiness
        self.userWorkoutTags = userWorkoutTags
        self.activeMainTagIds = activeMainTagIds
        self.activeSubTagIds = activeSubTagIds
        self.onMainTagToggle = onMainTagToggle
        self.onSubTagToggle = onSubTagToggle
        self.onCreateTag = onCreateTag
        self.onDeleteTag = onDeleteTag
        self.onAddSubTag = onAddSubTag
        self.onRemoveSubTag = onRemoveSubTag
    }

    var body: some View {
        let tagsOverflow = userWorkoutTags.count > 6
        let tabs: [(WorkoutExerciseContextTab, String)] = [
            (.history, "Historial"),
            (.tags, "Etiquetas"),
            (.drain, "Drenaje"),
            (.energy, "Gasto calórico"),
            (.replace, "Reemplazar"),
            (.edit, "Editar"),
            (.rmCalc, "Calc. RM"),
        ].filter { tab, _ in
            allowExerciseManagementActions || (tab != .replace && tab != .edit)
        }

        VStack(spacing: 0) {
            // Readiness + ghost row
            if exerciseReadiness != nil || (selectedTab != .history && ghostSet != nil && (ghostSet?.weight ?? 0) > 0 || (ghostSet?.reps ?? 0) > 0) {
                HStack(spacing: 8) {
                    if let readiness = exerciseReadiness {
                        let score = readiness.overallScore
                        let color: Color = score >= 75 ? Color(hex: 0x4CAF50) : score >= 50 ? Color(hex: 0xFFC107) : Color(hex: 0xFF5252)
                        let label = readinessLabel(score)
                        HStack(spacing: 6) {
                            Circle()
                                .fill(color)
                                .frame(width: 8, height: 8)
                            Text("Prep: \(label) (\(score)%)")
                                .font(.caption)
                                .fontWeight(.bold)
                                .foregroundColor(color)
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 6)
                        .background(color.opacity(0.12))
                        .cornerRadius(8)
                    }
                    if selectedTab != .history, let ghost = ghostSet, (ghost.weight > 0 || ghost.reps > 0) {
                        HStack(spacing: 6) {
                            Image(systemName: "clock")
                                .font(.system(size: 10))
                                .foregroundColor(Color(hex: 0x448AFF))
                            let text = {
                                var s = "Última "
                                if ghost.weight > 0 { s += "\(ghost.weight.toTrimmedNumberString())kg" }
                                if ghost.weight > 0 && ghost.reps > 0 { s += " · " }
                                if ghost.reps > 0 { s += "\(ghost.reps)" }
                                return s
                            }()
                            Text(text)
                                .font(.caption)
                                .fontWeight(.semibold)
                                .foregroundColor(Color(hex: 0x448AFF))
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 6)
                        .background(Color(hex: 0x448AFF).opacity(0.1))
                        .cornerRadius(8)
                        .onTapGesture { onExpandHistory() }
                    }
                }
                .padding(.bottom, 6)
            }

            // Tab bar
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(tabs, id: \.0.rawValue) { tab, title in
                        let isSelected = selectedTab == tab
                        let isDense = tab == .history || tab == .replace || tab == .edit
                        Text(title)
                            .font(.caption)
                            .fontWeight(isSelected ? .black : .medium)
                            .foregroundColor(isSelected ? sessionAccentColor : .secondary)
                            .padding(.horizontal, 14)
                            .padding(.vertical, 8)
                            .background(isSelected ? sessionAccentColor.opacity(0.15) : Color.clear)
                            .cornerRadius(12)
                            .overlay(
                                RoundedRectangle(cornerRadius: 12)
                                    .stroke(isSelected ? sessionAccentColor : Color.gray.opacity(0.3), lineWidth: 1)
                            )
                            .onTapGesture {
                                if isDense {
                                    switch tab {
                                    case .history: onExpandHistory()
                                    case .replace: onExpandReplace()
                                    case .edit: onExpandEdit()
                                    default: break
                                    }
                                } else {
                                    onSelectedTabChange(isSelected ? nil : tab)
                                }
                            }
                    }
                }
                .padding(.vertical, 4)
            }
            .padding(.vertical, 4)

            // Content
            if let tab = selectedTab {
                VStack(spacing: 0) {
                    VStack(spacing: 12) {
                        switch tab {
                        case .tags:
                            WorkoutMultiTagContent(
                                userWorkoutTags: userWorkoutTags,
                                activeMainTagIds: activeMainTagIds,
                                activeSubTagIds: activeSubTagIds,
                                onMainTagToggle: onMainTagToggle,
                                onSubTagToggle: onSubTagToggle,
                                onCreateTag: onCreateTag,
                                onDeleteTag: onDeleteTag,
                                onAddSubTag: onAddSubTag,
                                onRemoveSubTag: onRemoveSubTag,
                                sessionAccentColor: sessionAccentColor,
                                maxVisibleTags: 6
                            )
                            if tagsOverflow {
                                HStack {
                                    Spacer()
                                    Button("Ver todas las etiquetas") { onExpandTags() }
                                        .font(.caption)
                                        .fontWeight(.bold)
                                        .foregroundColor(sessionAccentColor)
                                }
                            }
                        case .drain:
                            WorkoutExerciseDrainContent(
                                drain: drain,
                                involvedMuscles: currentExerciseInfo?.involvedMuscles ?? []
                            )
                        case .rmCalc:
                            WorkoutRmCalcContent(
                                bodyWeight: rmBodyWeight,
                                currentLoadMode: rmCurrentLoadMode,
                                onWeightSelected: onRmWeightSelected != nil ? { w, _ in onRmWeightSelected?(w) } : nil,
                                sessionAccentColor: sessionAccentColor
                            )
                        case .energy:
                            WorkoutSessionEnergyContent(sessionEnergy: sessionEnergy)
                        default:
                            EmptyView()
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 16)
                }
                .frame(maxWidth: .infinity)
                .background(Color(hex: 0x2A2A2A))
                .cornerRadius(16)
                .padding(.top, 4)
                .transition(.opacity.combined(with: .move(edge: .top)))
            }
        }
    }
}

// MARK: - WorkoutExerciseHistoryContent

struct WorkoutExerciseHistoryContent: View {
    let history: [ExerciseHistoryEntry]
    let activeTag: String?
    let maxEntries: Int?
    let maxSetsPerEntry: Int

    init(history: [ExerciseHistoryEntry], activeTag: String? = nil, maxEntries: Int? = nil, maxSetsPerEntry: Int = 6) {
        self.history = history
        self.activeTag = activeTag
        self.maxEntries = maxEntries
        self.maxSetsPerEntry = maxSetsPerEntry
    }

    @State private var expandedSections: [String: Bool] = [:]

    var body: some View {
        if history.isEmpty {
            VStack {
                Text("Sin historial registrado")
                    .font(.body)
                    .foregroundColor(.secondary)
            }
            .frame(maxWidth: .infinity)
            .padding(32)
            return
        }

        let groupedHistory: [(String, [ExerciseHistoryEntry])] = {
            let grouped = Dictionary(grouping: history) { entry -> String in
                let dateStr = String(entry.date.prefix(10))
                let formatter = ISO8601DateFormatter()
                formatter.formatOptions = [.withFullDate]
                let date = formatter.date(from: dateStr) ?? Date()
                let now = Date()
                let weekAgo = Calendar.current.date(byAdding: .day, value: -7, to: now)!
                let twoWeeksAgo = Calendar.current.date(byAdding: .day, value: -14, to: now)!
                if date >= weekAgo { return "Esta semana" }
                if date >= twoWeeksAgo { return "Semana pasada" }
                if Calendar.current.component(.month, from: date) == Calendar.current.component(.month, from: now) &&
                    Calendar.current.component(.year, from: date) == Calendar.current.component(.year, from: now) {
                    return "Este mes"
                }
                let dateFormatter = DateFormatter()
                dateFormatter.locale = Locale(identifier: "es_CL")
                dateFormatter.dateFormat = "MMMM yyyy"
                return dateFormatter.string(from: date).capitalized
            }
            let order: [String] = ["Esta semana", "Semana pasada", "Este mes"]
            return grouped.keys.sorted { a, b in
                let ai = order.firstIndex(of: a) ?? Int.max
                let bi = order.firstIndex(of: b) ?? Int.max
                if ai != bi { return ai < bi }
                return a > b
            }.map { ($0, grouped[$0]!) }
        }()

        VStack(spacing: 12) {
            ForEach(groupedHistory.indices, id: \.self) { sectionIdx in
                let (groupLabel, entries) = groupedHistory[sectionIdx]
                let isExpanded = Binding<Bool>(
                    get: { expandedSections[groupLabel] ?? (groupLabel == "Esta semana" || groupLabel == "Semana pasada") },
                    set: { expandedSections[groupLabel] = $0 }
                )

                VStack(spacing: 8) {
                    HStack {
                        Text(groupLabel)
                            .font(.subheadline)
                            .fontWeight(.black)
                            .foregroundColor(.blue)
                        Spacer()
                        Image(systemName: isExpanded.wrappedValue ? "chevron.up" : "chevron.down")
                            .font(.system(size: 12))
                            .foregroundColor(.secondary)
                    }
                    .padding(.vertical, 4)
                    .onTapGesture { isExpanded.wrappedValue.toggle() }

                    if isExpanded.wrappedValue {
                        VStack(spacing: 10) {
                            ForEach(entries.indices, id: \.self) { entryIdx in
                                let entry = entries[entryIdx]
                                let isTagMatch = activeTag != nil && entry.tag == activeTag
                                let entryBgColor: Color = {
                                    switch entry.latestHistoryColor {
                                    case .YELLOW: return Color(hex: 0xFFFFF9C4)
                                    case .RED: return Color.red.opacity(0.15)
                                    default:
                                        if isTagMatch { return Color.blue.opacity(0.08) }
                                        return Color(.systemGray6)
                                    }
                                }()
                                VStack(spacing: 6) {
                                    HStack {
                                        HStack(spacing: 6) {
                                            Text(String(entry.date.prefix(10)))
                                                .font(.footnote)
                                                .fontWeight(.bold)
                                            if let tag = entry.tag {
                                                Text(tag)
                                                    .font(.caption)
                                                    .padding(.horizontal, 6)
                                                    .padding(.vertical, 2)
                                                    .background(Color.blue.opacity(0.15))
                                                    .cornerRadius(999)
                                            }
                                        }
                                        Spacer()
                                        if let e1rm = entry.e1rm {
                                            Text("e1RM \(String(format: "%.1f", e1rm)) kg")
                                                .font(.caption)
                                                .foregroundColor(.accentColor)
                                                .fontWeight(.bold)
                                        }
                                    }
                                    let workingSets = entry.sets.filter { !$0.isWarmup }
                                    ForEach(workingSets.prefix(max(maxSetsPerEntry, 0)).indices, id: \.self) { setIdx in
                                        let s = workingSets[setIdx]
                                        let sideLabel: String? = {
                                            if s.side == "left" { return "Izq" }
                                            if s.side == "right" { return "Der" }
                                            return nil
                                        }()
                                        HStack(spacing: 8) {
                                            Text({
                                                var text = ""
                                                if let sl = sideLabel { text += "\(sl) · " }
                                                if s.weight > 0 { text += "\(s.weight)kg" }
                                                if s.weight > 0 && s.reps > 0 { text += " x " }
                                                if s.reps > 0 { text += "\(s.reps) reps" }
                                                if let rpe = s.rpe { text += " · RPE \(rpe)" }
                                                return text
                                            }())
                                            .font(.caption)
                                            if s.isFailure {
                                                Text("F")
                                                    .font(.caption)
                                                    .padding(.horizontal, 4)
                                                    .background(Color.red.opacity(0.2))
                                                    .cornerRadius(999)
                                            }
                                        }
                                    }
                                }
                                .padding(12)
                                .background(entryBgColor)
                                .cornerRadius(12)
                            }
                        }
                    }
                })
            }
        }
    }
}

// MARK: - WorkoutMultiTagContent

struct WorkoutMultiTagContent: View {
    let userWorkoutTags: [WorkoutTag]
    let activeMainTagIds: [String]
    let activeSubTagIds: [String]
    let onMainTagToggle: (String) -> Void
    let onSubTagToggle: (String) -> Void
    let onCreateTag: (String) -> Void
    let onDeleteTag: (String) -> Void
    let onAddSubTag: (String, String, SubTagCategory) -> Void
    let onRemoveSubTag: (String, String) -> Void
    let sessionAccentColor: Color
    let maxVisibleTags: Int

    @State private var createTagText: String = ""
    @State private var showCreateTagField: Bool = false
    @State private var editingTagId: String? = nil
    @State private var addSubTagForTagId: String? = nil
    @State private var subTagName: String = ""
    @State private var subTagCategory: SubTagCategory = .LIBRE

    init(
        userWorkoutTags: [WorkoutTag],
        activeMainTagIds: [String],
        activeSubTagIds: [String],
        onMainTagToggle: @escaping (String) -> Void,
        onSubTagToggle: @escaping (String) -> Void,
        onCreateTag: @escaping (String) -> Void,
        onDeleteTag: @escaping (String) -> Void,
        onAddSubTag: @escaping (String, String, SubTagCategory) -> Void,
        onRemoveSubTag: @escaping (String, String) -> Void,
        sessionAccentColor: Color,
        maxVisibleTags: Int = Int.max
    ) {
        self.userWorkoutTags = userWorkoutTags
        self.activeMainTagIds = activeMainTagIds
        self.activeSubTagIds = activeSubTagIds
        self.onMainTagToggle = onMainTagToggle
        self.onSubTagToggle = onSubTagToggle
        self.onCreateTag = onCreateTag
        self.onDeleteTag = onDeleteTag
        self.onAddSubTag = onAddSubTag
        self.onRemoveSubTag = onRemoveSubTag
        self.sessionAccentColor = sessionAccentColor
        self.maxVisibleTags = maxVisibleTags
    }

    var body: some View {
        let visibleTags = maxVisibleTags < userWorkoutTags.count ? Array(userWorkoutTags.prefix(maxVisibleTags)) : userWorkoutTags

        VStack(spacing: 10) {
            Text("Etiquetas")
                .font(.subheadline)
                .fontWeight(.bold)

            if userWorkoutTags.isEmpty && !showCreateTagField {
                Text("Sin etiquetas. Crea una para este ejercicio.")
                    .font(.caption)
                    .foregroundColor(.secondary)
            }

            // Tags
            FlexibleStack(spacing: 6) {
                ForEach(visibleTags) { tag in
                    let isActive = activeMainTagIds.contains(tag.id)
                    HStack(spacing: 4) {
                        Text(tag.name)
                            .font(.caption)
                            .foregroundColor(isActive ? .white : .primary)
                            .padding(.leading, 8)
                        Button(action: {
                            editingTagId = editingTagId == tag.id ? nil : tag.id
                        }) {
                            Image(systemName: "ellipsis")
                                .font(.system(size: 8))
                                .padding(4)
                        }
                        .frame(width: 16, height: 16)
                    }
                    .padding(.vertical, 4)
                    .padding(.trailing, 6)
                    .background(isActive ? sessionAccentColor.opacity(0.2) : Color(.systemGray5))
                    .cornerRadius(8)
                    .overlay(
                        RoundedRectangle(cornerRadius: 8)
                            .stroke(isActive ? sessionAccentColor : Color.clear, lineWidth: 1)
                    )
                    .onTapGesture { onMainTagToggle(tag.id) }

                    let activeSubs = tag.subTags.filter { activeSubTagIds.contains($0.id) }
                    ForEach(activeSubs) { subTag in
                        HStack(spacing: 2) {
                            Text(subTag.name)
                                .font(.system(size: 9))
                                .foregroundColor(.white)
                                .padding(.leading, 6)
                            Image(systemName: "xmark")
                                .font(.system(size: 6))
                                .padding(2)
                        }
                        .padding(.vertical, 2)
                        .padding(.trailing, 4)
                        .background(sessionAccentColor.opacity(0.3))
                        .cornerRadius(6)
                        .onTapGesture { onSubTagToggle(subTag.id) }
                    }
                }
            }

            // Edit tag section
            if let tagId = editingTagId, let tag = userWorkoutTags.first(where: { $0.id == tagId }) {
                VStack(spacing: 8) {
                    HStack {
                        Text(tag.name)
                            .font(.footnote)
                            .fontWeight(.bold)
                        Spacer()
                        Button(action: {
                            onDeleteTag(tagId)
                            editingTagId = nil
                        }) {
                            Image(systemName: "trash")
                                .font(.system(size: 10))
                                .foregroundColor(.red)
                        }
                        .frame(width: 20, height: 20)
                    }

                    ForEach(tag.subTags) { sub in
                        let isActive = activeSubTagIds.contains(sub.id)
                        HStack(spacing: 4) {
                            Text(sub.name)
                                .font(.caption)
                                .foregroundColor(isActive ? .white : .primary)
                                .padding(.horizontal, 8)
                                .padding(.vertical, 4)
                                .background(isActive ? sessionAccentColor.opacity(0.2) : Color(.systemGray5))
                                .cornerRadius(8)
                                .onTapGesture { onSubTagToggle(sub.id) }
                            Button(action: { onRemoveSubTag(tagId, sub.id) }) {
                                Image(systemName: "xmark")
                                    .font(.system(size: 8))
                                    .foregroundColor(.secondary)
                            }
                            .frame(width: 18, height: 18)
                        }
                    }

                    if addSubTagForTagId == tagId {
                        TextField("Nombre", text: $subTagName)
                            .textFieldStyle(.plain)
                            .padding(8)
                            .background(Color(.systemGray6))
                            .cornerRadius(8)

                        HStack(spacing: 4) {
                            ForEach([SubTagCategory.MARCA, .SETUP, .TECNICA, .LIBRE], id: \.self) { cat in
                                Text(String(cat.rawValue.prefix(4)))
                                    .font(.caption)
                                    .padding(.horizontal, 8)
                                    .padding(.vertical, 4)
                                    .background(subTagCategory == cat ? sessionAccentColor.opacity(0.2) : Color(.systemGray5))
                                    .cornerRadius(8)
                                    .onTapGesture { subTagCategory = cat }
                            }
                        }

                        HStack(spacing: 8) {
                            Button("Agregar") {
                                if !subTagName.trimmingCharacters(in: .whitespaces).isEmpty {
                                    onAddSubTag(tagId, subTagName, subTagCategory)
                                    subTagName = ""
                                    addSubTagForTagId = nil
                                }
                            }
                            .disabled(subTagName.trimmingCharacters(in: .whitespaces).isEmpty)
                            .buttonStyle(.borderedProminent)
                            .tint(sessionAccentColor)

                            Button("Cancelar") { addSubTagForTagId = nil }
                                .buttonStyle(.plain)
                        }
                    } else {
                        Button(action: { addSubTagForTagId = tagId }) {
                            HStack(spacing: 4) {
                                Image(systemName: "plus")
                                    .font(.system(size: 8))
                                Text("Añadir sub-etiqueta")
                                    .font(.caption)
                            }
                        }
                    }
                }
                .padding(12)
                .background(Color(.systemGray6).opacity(0.5))
                .cornerRadius(12)
            }

            // Create tag
            if showCreateTagField {
                HStack(spacing: 8) {
                    TextField("Nueva etiqueta", text: $createTagText)
                        .textFieldStyle(.plain)
                        .padding(8)
                        .background(Color(.systemGray6))
                        .cornerRadius(8)

                    Button(action: {
                        if !createTagText.trimmingCharacters(in: .whitespaces).isEmpty {
                            onCreateTag(createTagText)
                            createTagText = ""
                            showCreateTagField = false
                        }
                    }) {
                        Image(systemName: "checkmark")
                            .foregroundColor(.white)
                            .padding(8)
                            .background(createTagText.trimmingCharacters(in: .whitespaces).isEmpty ? Color.gray : Color.green)
                            .cornerRadius(8)
                    }
                    .disabled(createTagText.trimmingCharacters(in: .whitespaces).isEmpty)

                    Button(action: { showCreateTagField = false; createTagText = "" }) {
                        Image(systemName: "xmark")
                            .foregroundColor(.secondary)
                            .padding(8)
                    }
                }
            } else {
                Button(action: { showCreateTagField = true }) {
                    HStack(spacing: 4) {
                        Image(systemName: "plus")
                            .font(.system(size: 10))
                        Text("Crear etiqueta")
                            .font(.caption)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(8)
                }
                .buttonStyle(.bordered)
            }
        }
    }
}

// MARK: - ExerciseTagSheetContent

struct ExerciseTagSheetContent: View {
    let currentTag: String?
    let onTagSet: (String) -> Void
    let onDismiss: () -> Void
    let showDismissButton: Bool
    let maxVisibleTags: Int
    let userTags: [String]

    @State private var tagText: String = ""

    init(
        currentTag: String?,
        onTagSet: @escaping (String) -> Void,
        onDismiss: @escaping () -> Void,
        showDismissButton: Bool = true,
        maxVisibleTags: Int = Int.max,
        userTags: [String] = []
    ) {
        self.currentTag = currentTag
        self.onTagSet = onTagSet
        self.onDismiss = onDismiss
        self.showDismissButton = showDismissButton
        self.maxVisibleTags = maxVisibleTags
        self.userTags = userTags
        _tagText = State(initialValue: currentTag ?? "")
    }

    var body: some View {
        VStack(spacing: 10) {
            Text("Tag activo")
                .font(.subheadline)
                .fontWeight(.bold)

            FlexibleStack(spacing: 6) {
                ForEach(Array(Set(userTags)), id: \.self) { tag in
                    Text(tag)
                        .font(.caption)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .background(tagText == tag ? Color.blue.opacity(0.2) : Color(.systemGray5))
                        .cornerRadius(8)
                        .onTapGesture {
                            tagText = tag
                            onTagSet(tag)
                        }
                }
            }

            HStack {
                TextField("Tag personalizado", text: $tagText)
                    .textFieldStyle(.plain)
                    .padding(8)
                    .background(Color(.systemGray6))
                    .cornerRadius(8)

                if !tagText.trimmingCharacters(in: .whitespaces).isEmpty {
                    Button(action: { onTagSet(tagText) }) {
                        Image(systemName: "checkmark")
                            .foregroundColor(.green)
                    }
                }
            }

            if currentTag != nil {
                HStack {
                    Spacer()
                    Button("Limpiar tag") { tagText = ""; onTagSet("") }
                        .buttonStyle(.plain)
                }
            }

            if showDismissButton {
                Button("Listo", action: onDismiss)
                    .buttonStyle(.borderedProminent)
                    .frame(maxWidth: .infinity)
            }
        }
    }
}

// MARK: - WorkoutExerciseSetupContent

struct WorkoutExerciseSetupContent: View {
    let exercise: Exercise
    let currentSet: ExerciseSet
    let profiles: [WorkoutContextProfile]
    let activeProfileId: String?
    let onSelectProfile: (String) -> Void
    let onSaveProfile: (WorkoutContextProfile) -> Void
    let onUpdateExercise: ((Exercise) -> Exercise) -> Void
    let onUpdateSet: (String, (ExerciseSet) -> ExerciseSet) -> Void
    let sessionAccentColor: Color
    let maxVisibleCues: Int
    let exerciseTag: String?

    @State private var showNewProfileDialog: Bool = false
    @State private var newProfileLabel: String = ""
    @State private var hasSetupChanges: Bool = false

    init(
        exercise: Exercise,
        currentSet: ExerciseSet,
        profiles: [WorkoutContextProfile],
        activeProfileId: String?,
        onSelectProfile: @escaping (String) -> Void,
        onSaveProfile: @escaping (WorkoutContextProfile) -> Void,
        onUpdateExercise: @escaping ((Exercise) -> Exercise) -> Void,
        onUpdateSet: @escaping (String, (ExerciseSet) -> ExerciseSet) -> Void,
        sessionAccentColor: Color,
        maxVisibleCues: Int = Int.max,
        exerciseTag: String? = nil
    ) {
        self.exercise = exercise
        self.currentSet = currentSet
        self.profiles = profiles
        self.activeProfileId = activeProfileId
        self.onSelectProfile = onSelectProfile
        self.onSaveProfile = onSaveProfile
        self.onUpdateExercise = onUpdateExercise
        self.onUpdateSet = onUpdateSet
        self.sessionAccentColor = sessionAccentColor
        self.maxVisibleCues = maxVisibleCues
        self.exerciseTag = exerciseTag
    }

    var body: some View {
        let activeProfile = profiles.first { $0.id == activeProfileId }

        VStack(spacing: 12) {
            if !profiles.isEmpty {
                Text("Setups guardados")
                    .font(.footnote)
                    .fontWeight(.bold)

                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(profiles) { profile in
                            let isSelected = profile.id == activeProfileId
                            Text(profile.setupLabel ?? profile.machineBrand ?? "Sin nombre")
                                .font(.caption)
                                .padding(.horizontal, 12)
                                .padding(.vertical, 6)
                                .background(isSelected ? sessionAccentColor.opacity(0.15) : Color(.systemGray6).opacity(0.5))
                                .cornerRadius(8)
                                .overlay(
                                    RoundedRectangle(cornerRadius: 8)
                                        .stroke(isSelected ? sessionAccentColor : Color.clear, lineWidth: 1)
                                )
                                .onTapGesture { onSelectProfile(profile.id) }
                        }
                        Button(action: { showNewProfileDialog = true }) {
                            HStack(spacing: 4) {
                                Image(systemName: "plus")
                                    .font(.system(size: 10))
                                Text("Nuevo")
                                    .font(.caption)
                            }
                            .padding(.horizontal, 12)
                            .padding(.vertical, 6)
                            .frame(height: 32)
                        }
                        .buttonStyle(.bordered)
                    }
                }

                Divider()
                    .background(Color.gray.opacity(0.3))
            } else {
                Button(action: { showNewProfileDialog = true }) {
                    HStack(spacing: 8) {
                        Image(systemName: "plus")
                        Text("Crear primer Setup")
                    }
                    .frame(maxWidth: .infinity)
                    .padding(8)
                }
                .buttonStyle(.bordered)
            }

            // Setup details
            let currentMachine = activeProfile?.machineBrand ?? currentSet.machineBrand ?? ""
            let currentSeat = activeProfile?.setupDetails?.seatPosition ?? exercise.setupDetails?.seatPosition ?? ""
            let currentPin = activeProfile?.setupDetails?.pinPosition ?? exercise.setupDetails?.pinPosition ?? ""
            let currentNotes = activeProfile?.setupDetails?.equipmentNotes ?? exercise.setupDetails?.equipmentNotes ?? ""
            let currentBarWeightKg = activeProfile?.barWeightKg ?? activeProfile?.setupDetails?.barWeightKg ?? exercise.setupDetails?.barWeightKg

            SetupField(label: "Maquina / marca", text: Binding(
                get: { currentMachine },
                set: { newValue in
                    if let profile = activeProfile {
                        onSaveProfile(WorkoutContextProfile(
                            id: profile.id,
                            exerciseKey: profile.exerciseKey,
                            tagId: profile.tagId,
                            setupProfileId: profile.setupProfileId,
                            setupLabel: profile.setupLabel,
                            machineBrand: newValue.isEmpty ? nil : newValue,
                            loadMode: profile.loadMode,
                            linkStrategy: profile.linkStrategy,
                            setupDetails: profile.setupDetails,
                            barWeightKg: profile.barWeightKg,
                            notes: profile.notes,
                            createdAtIso: profile.createdAtIso,
                            lastUsedAtIso: profile.lastUsedAtIso,
                            usageCount: profile.usageCount
                        ))
                    } else {
                        onUpdateSet(currentSet.id) { $0.copy(machineBrand: newValue.isEmpty ? nil : newValue) }
                    }
                }
            ))

            HStack(spacing: 8) {
                SetupField(label: "Asiento", text: Binding(
                    get: { currentSeat },
                    set: { newValue in
                        if let profile = activeProfile {
                            let updatedDetails = ExerciseSetupDetails(
                                seatPosition: newValue.isEmpty ? nil : newValue,
                                pinPosition: profile.setupDetails?.pinPosition,
                                equipmentNotes: profile.setupDetails?.equipmentNotes,
                                barWeightKg: profile.setupDetails?.barWeightKg
                            )
                            onSaveProfile(WorkoutContextProfile(
                                id: profile.id,
                                exerciseKey: profile.exerciseKey,
                                tagId: profile.tagId,
                                setupProfileId: profile.setupProfileId,
                                setupLabel: profile.setupLabel,
                                machineBrand: profile.machineBrand,
                                loadMode: profile.loadMode,
                                linkStrategy: profile.linkStrategy,
                                setupDetails: updatedDetails,
                                barWeightKg: profile.barWeightKg,
                                notes: profile.notes,
                                createdAtIso: profile.createdAtIso,
                                lastUsedAtIso: profile.lastUsedAtIso,
                                usageCount: profile.usageCount
                            ))
                        } else {
                            onUpdateExercise { current in
                                let details = ExerciseSetupDetails(
                                    seatPosition: newValue.isEmpty ? nil : newValue,
                                    pinPosition: current.setupDetails?.pinPosition,
                                    equipmentNotes: current.setupDetails?.equipmentNotes,
                                    barWeightKg: current.setupDetails?.barWeightKg
                                )
                                return current.copy(setupDetails: details)
                            }
                        }
                    }
                ))

                SetupField(label: "Pin", text: Binding(
                    get: { currentPin },
                    set: { newValue in
                        if let profile = activeProfile {
                            let updatedDetails = ExerciseSetupDetails(
                                seatPosition: profile.setupDetails?.seatPosition,
                                pinPosition: newValue.isEmpty ? nil : newValue,
                                equipmentNotes: profile.setupDetails?.equipmentNotes,
                                barWeightKg: profile.setupDetails?.barWeightKg
                            )
                            onSaveProfile(WorkoutContextProfile(
                                id: profile.id, exerciseKey: profile.exerciseKey, tagId: profile.tagId,
                                setupProfileId: profile.setupProfileId, setupLabel: profile.setupLabel,
                                machineBrand: profile.machineBrand, loadMode: profile.loadMode,
                                linkStrategy: profile.linkStrategy, setupDetails: updatedDetails,
                                barWeightKg: profile.barWeightKg, notes: profile.notes,
                                createdAtIso: profile.createdAtIso, lastUsedAtIso: profile.lastUsedAtIso,
                                usageCount: profile.usageCount
                            ))
                        } else {
                            onUpdateExercise { current in
                                let details = ExerciseSetupDetails(
                                    seatPosition: current.setupDetails?.seatPosition,
                                    pinPosition: newValue.isEmpty ? nil : newValue,
                                    equipmentNotes: current.setupDetails?.equipmentNotes,
                                    barWeightKg: current.setupDetails?.barWeightKg
                                )
                                return current.copy(setupDetails: details)
                            }
                        }
                    }
                ))
            }

            SetupField(label: "Peso de barra (kg)", text: Binding(
                get: { currentBarWeightKg?.toTrimmedNumberString() ?? "" },
                set: { value in
                    let parsed = Double(value)
                    if let profile = activeProfile {
                        let updatedDetails = ExerciseSetupDetails(
                            seatPosition: profile.setupDetails?.seatPosition,
                            pinPosition: profile.setupDetails?.pinPosition,
                            equipmentNotes: profile.setupDetails?.equipmentNotes,
                            barWeightKg: parsed
                        )
                        onSaveProfile(WorkoutContextProfile(
                            id: profile.id, exerciseKey: profile.exerciseKey, tagId: profile.tagId,
                            setupProfileId: profile.setupProfileId, setupLabel: profile.setupLabel,
                            machineBrand: profile.machineBrand, loadMode: profile.loadMode,
                            linkStrategy: profile.linkStrategy, setupDetails: updatedDetails,
                            barWeightKg: profile.barWeightKg, notes: profile.notes,
                            createdAtIso: profile.createdAtIso, lastUsedAtIso: profile.lastUsedAtIso,
                            usageCount: profile.usageCount
                        ))
                    } else {
                        onUpdateExercise { exercise in
                            let details = ExerciseSetupDetails(
                                seatPosition: exercise.setupDetails?.seatPosition,
                                pinPosition: exercise.setupDetails?.pinPosition,
                                equipmentNotes: exercise.setupDetails?.equipmentNotes,
                                barWeightKg: parsed
                            )
                            return exercise.copy(setupDetails: details)
                        }
                    }
                }
            ))

            SetupField(label: "Notas de set-up", text: Binding(
                get: { currentNotes },
                set: { newValue in
                    if let profile = activeProfile {
                        let updatedDetails = ExerciseSetupDetails(
                            seatPosition: profile.setupDetails?.seatPosition,
                            pinPosition: profile.setupDetails?.pinPosition,
                            equipmentNotes: newValue.isEmpty ? nil : newValue,
                            barWeightKg: profile.setupDetails?.barWeightKg
                        )
                        onSaveProfile(WorkoutContextProfile(
                            id: profile.id, exerciseKey: profile.exerciseKey, tagId: profile.tagId,
                            setupProfileId: profile.setupProfileId, setupLabel: profile.setupLabel,
                            machineBrand: profile.machineBrand, loadMode: profile.loadMode,
                            linkStrategy: profile.linkStrategy, setupDetails: updatedDetails,
                            barWeightKg: profile.barWeightKg, notes: profile.notes,
                            createdAtIso: profile.createdAtIso, lastUsedAtIso: profile.lastUsedAtIso,
                            usageCount: profile.usageCount
                        ))
                    } else {
                        onUpdateExercise { current in
                            let details = ExerciseSetupDetails(
                                seatPosition: current.setupDetails?.seatPosition,
                                pinPosition: current.setupDetails?.pinPosition,
                                equipmentNotes: newValue.isEmpty ? nil : newValue,
                                barWeightKg: current.setupDetails?.barWeightKg
                            )
                            return current.copy(setupDetails: details)
                        }
                    }
                }
            ))

            let cues = Array(Set(exercise.setupCues + exercise.executionCues)).sorted()
            if !cues.isEmpty {
                Text("Cues")
                    .font(.footnote)
                    .fontWeight(.bold)
                VStack(spacing: 4) {
                    ForEach(cues.prefix(max(maxVisibleCues, 0)), id: \.self) { cue in
                        Text("- \(cue)")
                            .font(.caption)
                            .foregroundColor(.secondary)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }
                }
            }

            if let tag = exerciseTag {
                HStack(spacing: 8) {
                    Image(systemName: "link")
                        .font(.system(size: 12))
                        .foregroundColor(sessionAccentColor)
                    Text("Asociar set-up a \"\(tag)\"")
                        .font(.footnote)
                        .fontWeight(.bold)
                        .foregroundColor(sessionAccentColor)
                    Spacer()
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 10)
                .background(sessionAccentColor.opacity(0.1))
                .cornerRadius(12)
                .onTapGesture {
                    let currentSetupDetails = ExerciseSetupDetails(
                        seatPosition: currentSeat.isEmpty ? nil : currentSeat,
                        pinPosition: currentPin.isEmpty ? nil : currentPin,
                        equipmentNotes: currentNotes.isEmpty ? nil : currentNotes,
                        barWeightKg: currentBarWeightKg
                    )
                    onSaveProfile(WorkoutContextProfile(
                        id: UUID().uuidString,
                        exerciseKey: "",
                        tagId: tag,
                        setupDetails: currentSetupDetails,
                        machineBrand: currentMachine.isEmpty ? nil : currentMachine,
                        barWeightKg: currentBarWeightKg
                    ))
                    hasSetupChanges = false
                }
            }
        }
        .alert("Nuevo Setup", isPresented: $showNewProfileDialog) {
            TextField("Nombre del setup (ej. Maquina SmartFit)", text: $newProfileLabel)
            Button("Guardar") {
                onSaveProfile(WorkoutContextProfile(
                    id: UUID().uuidString,
                    exerciseKey: "",
                    setupLabel: newProfileLabel.isEmpty ? "Nuevo Setup" : newProfileLabel,
                    setupDetails: exercise.setupDetails
                ))
                showNewProfileDialog = false
                newProfileLabel = ""
            }
            .disabled(newProfileLabel.trimmingCharacters(in: .whitespaces).isEmpty)
            Button("Cancelar", role: .cancel) {
                showNewProfileDialog = false
                newProfileLabel = ""
            }
        } message: {
            EmptyView()
        }
    }
}

// MARK: - WorkoutExerciseEditContent

struct WorkoutExerciseEditContent: View {
    let exercise: Exercise
    let maxVisibleSets: Int?
    let onUpdateSet: (String, (ExerciseSet) -> ExerciseSet) -> Void
    let onUpdateExercise: (((Exercise) -> Exercise) -> Void)?
    let onSave: (() -> Void)?
    let saveLabel: String
    let sessionAccentColor: Color

    @State private var trainingMode: TrainingMode
    @State private var weightText: String

    init(
        exercise: Exercise,
        maxVisibleSets: Int? = nil,
        onUpdateSet: @escaping (String, (ExerciseSet) -> ExerciseSet) -> Void,
        onUpdateExercise: (((Exercise) -> Exercise) -> Void)? = nil,
        onSave: (() -> Void)? = nil,
        saveLabel: String = "Guardar cambios",
        sessionAccentColor: Color = .blue
    ) {
        self.exercise = exercise
        self.maxVisibleSets = maxVisibleSets
        self.onUpdateSet = onUpdateSet
        self.onUpdateExercise = onUpdateExercise
        self.onSave = onSave
        self.saveLabel = saveLabel
        self.sessionAccentColor = sessionAccentColor
        _trainingMode = State(initialValue: exercise.trainingMode)
        _weightText = State(initialValue: exercise.consolidatedWeight?.weightKg?.toTrimmedNumberString() ?? "")
    }

    var body: some View {
        let sets = maxVisibleSets.map { Array(exercise.sets.prefix($0)) } ?? exercise.sets
        let consolidatedWeight = exercise.consolidatedWeight?.weightKg

        VStack(spacing: 10) {
            Text("Datos del ejercicio")
                .font(.headline)
                .fontWeight(.black)
                .foregroundColor(.white)
                .frame(maxWidth: .infinity, alignment: .leading)

            HStack(spacing: 8) {
                VStack(spacing: 4) {
                    Text("Carga base (kg)")
                        .font(.caption)
                        .foregroundColor(.white.opacity(0.6))
                        .frame(maxWidth: .infinity, alignment: .leading)
                    TextField("", text: $weightText)
                        .font(.body)
                        .fontWeight(.bold)
                        .foregroundColor(.white)
                        .keyboardType(.decimalPad)
                        .padding(8)
                        .background(Color.clear)
                        .overlay(
                            RoundedRectangle(cornerRadius: 8)
                                .stroke(Color.white.opacity(0.2), lineWidth: 1)
                        )
                }
                .padding(10)
                .background(Color(hex: 0x1E1E1E))
                .cornerRadius(12)

                VStack(spacing: 4) {
                    Text("Modo")
                        .font(.caption)
                        .foregroundColor(.white.opacity(0.6))
                        .frame(maxWidth: .infinity, alignment: .leading)
                    HStack(spacing: 4) {
                        ForEach([TrainingMode.REPS, .TIME, .DISTANCE, .RM], id: \.self) { mode in
                            Text({
                                switch mode {
                                case .REPS: return "Carga"
                                case .TIME: return "Tiempo"
                                case .DISTANCE: return "Dist"
                                case .RM: return "RM"
                                default: return mode.rawValue
                                }
                            }())
                            .font(.system(size: 10))
                            .padding(.horizontal, 6)
                            .padding(.vertical, 4)
                            .background(trainingMode == mode ? Color.blue : Color(hex: 0x2A2A2A))
                            .foregroundColor(trainingMode == mode ? .white : .white)
                            .cornerRadius(6)
                            .onTapGesture {
                                trainingMode = mode
                                onUpdateExercise? { $0.copy(trainingMode: mode) }
                            }
                        }
                    }
                }
                .padding(10)
                .background(Color(hex: 0x1E1E1E))
                .cornerRadius(12)
            }

            if let cw = consolidatedWeight {
                HStack {
                    Text("RM estimado")
                        .font(.caption)
                        .foregroundColor(.white.opacity(0.6))
                    Spacer()
                    Text("\(cw.toTrimmedNumberString()) kg")
                        .fontWeight(.black)
                        .foregroundColor(.blue)
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 8)
                .background(Color.blue.opacity(0.1))
                .cornerRadius(12)
            }

            ForEach(Array(sets.enumerated()), id: \.element.id) { index, set in
                WorkoutSetEditCard(
                    set: set,
                    index: index,
                    onUpdateSet: onUpdateSet,
                    sessionAccentColor: sessionAccentColor
                )
            }

            if let save = onSave {
                Button(action: save) {
                    HStack(spacing: 8) {
                        Image(systemName: "square.and.arrow.down")
                            .font(.system(size: 14))
                        Text(saveLabel)
                            .fontWeight(.bold)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(12)
                    .background(Color.blue)
                    .foregroundColor(.white)
                    .cornerRadius(14)
                }
            }
        }
    }
}

// MARK: - ExerciseSetupSheetContent

struct ExerciseSetupSheetContent: View {
    let exercise: Exercise
    let currentSet: ExerciseSet?
    let currentTag: String?
    let profiles: [WorkoutContextProfile]
    let activeProfileId: String?
    let onTagSet: (String) -> Void
    let onSelectProfile: (String) -> Void
    let onSaveProfile: (WorkoutContextProfile) -> Void
    let onUpdateExercise: ((Exercise) -> Exercise) -> Void
    let onUpdateSet: (String, (ExerciseSet) -> ExerciseSet) -> Void
    let onDismiss: () -> Void
    let sessionAccentColor: Color
    let showTagControls: Bool
    let showDismissButton: Bool
    let maxVisibleCues: Int
    let userTags: [String]

    @State private var tagText: String = ""

    init(
        exercise: Exercise,
        currentSet: ExerciseSet?,
        currentTag: String?,
        profiles: [WorkoutContextProfile],
        activeProfileId: String?,
        onTagSet: @escaping (String) -> Void,
        onSelectProfile: @escaping (String) -> Void,
        onSaveProfile: @escaping (WorkoutContextProfile) -> Void,
        onUpdateExercise: @escaping ((Exercise) -> Exercise) -> Void,
        onUpdateSet: @escaping (String, (ExerciseSet) -> ExerciseSet) -> Void,
        onDismiss: @escaping () -> Void,
        sessionAccentColor: Color,
        showTagControls: Bool = true,
        showDismissButton: Bool = true,
        maxVisibleCues: Int = 6,
        userTags: [String] = []
    ) {
        self.exercise = exercise
        self.currentSet = currentSet
        self.currentTag = currentTag
        self.profiles = profiles
        self.activeProfileId = activeProfileId
        self.onTagSet = onTagSet
        self.onSelectProfile = onSelectProfile
        self.onSaveProfile = onSaveProfile
        self.onUpdateExercise = onUpdateExercise
        self.onUpdateSet = onUpdateSet
        self.onDismiss = onDismiss
        self.sessionAccentColor = sessionAccentColor
        self.showTagControls = showTagControls
        self.showDismissButton = showDismissButton
        self.maxVisibleCues = maxVisibleCues
        self.userTags = userTags
        _tagText = State(initialValue: currentTag ?? "")
    }

    var body: some View {
        let mergedUserTags = Array(Set(userTags))

        VStack(spacing: 10) {
            if showTagControls {
                Text("Tag activo")
                    .font(.subheadline)
                    .fontWeight(.black)

                FlexibleStack(spacing: 6) {
                    ForEach(mergedUserTags, id: \.self) { tag in
                        Text(tag)
                            .font(.caption)
                            .padding(.horizontal, 8)
                            .padding(.vertical, 4)
                            .background(tagText == tag ? Color.blue.opacity(0.2) : Color(.systemGray5))
                            .cornerRadius(8)
                            .onTapGesture {
                                tagText = tag
                                onTagSet(tag)
                            }
                    }
                }

                HStack {
                    TextField("Tag personalizado", text: $tagText)
                        .textFieldStyle(.plain)
                        .padding(8)
                        .background(Color(.systemGray6))
                        .cornerRadius(8)

                    if !tagText.trimmingCharacters(in: .whitespaces).isEmpty {
                        Button(action: { onTagSet(tagText) }) {
                            Image(systemName: "checkmark")
                                .foregroundColor(.green)
                        }
                    }
                }
            }

            if let set = currentSet {
                Divider()
                    .padding(.vertical, 4)

                WorkoutExerciseSetupContent(
                    exercise: exercise,
                    currentSet: set,
                    profiles: profiles,
                    activeProfileId: activeProfileId,
                    onSelectProfile: onSelectProfile,
                    onSaveProfile: onSaveProfile,
                    onUpdateExercise: onUpdateExercise,
                    onUpdateSet: onUpdateSet,
                    sessionAccentColor: sessionAccentColor,
                    maxVisibleCues: maxVisibleCues,
                    exerciseTag: currentTag
                )
            }

            if showDismissButton {
                Button("Listo", action: onDismiss)
                    .buttonStyle(.borderedProminent)
                    .frame(maxWidth: .infinity)
            }
        }
    }
}

// MARK: - WorkoutSetEditCard

struct WorkoutSetEditCard: View {
    let set: ExerciseSet
    let index: Int
    let onUpdateSet: (String, (ExerciseSet) -> ExerciseSet) -> Void
    let sessionAccentColor: Color

    @State private var expanded: Bool = false

    var body: some View {
        VStack(spacing: 10) {
            HStack {
                Text("SERIE \(index + 1)")
                    .font(.caption)
                    .fontWeight(.black)
                    .foregroundColor(sessionAccentColor)
                    .tracking(2)
                Spacer()
                HStack(spacing: 4) {
                    Text("AMRAP")
                        .font(.caption)
                        .padding(.horizontal, 6)
                        .padding(.vertical, 3)
                        .background(set.isAmrap ? sessionAccentColor.opacity(0.2) : Color(.systemGray5))
                        .foregroundColor(set.isAmrap ? sessionAccentColor : .primary)
                        .cornerRadius(6)
                        .onTapGesture { onUpdateSet(set.id) { $0.copy(isAmrap: !$0.isAmrap) } }

                    Text("Fallo")
                        .font(.caption)
                        .padding(.horizontal, 6)
                        .padding(.vertical, 3)
                        .background((set.isFailure || set.intensityMode == .FAILURE) ? Color(hex: 0xFF5252).opacity(0.2) : Color(.systemGray5))
                        .foregroundColor((set.isFailure || set.intensityMode == .FAILURE) ? Color(hex: 0xFF5252) : .primary)
                        .cornerRadius(6)
                        .onTapGesture {
                            onUpdateSet(set.id) {
                                if $0.intensityMode == .FAILURE {
                                    return $0.copy(intensityMode: .RPE, isFailure: false)
                                } else {
                                    return $0.copy(intensityMode: .FAILURE, isFailure: true)
                                }
                            }
                        }
                }
                Button(action: { expanded.toggle() }) {
                    Image(systemName: expanded ? "chevron.up" : "chevron.down")
                        .font(.system(size: 10))
                        .foregroundColor(.white.opacity(0.4))
                }
                .frame(width: 22, height: 22)
            }

            HStack(spacing: 10) {
                VStack(spacing: 4) {
                    Text(set.unitModeV2 == .TIME || set.targetDuration != nil ? "TIEMPO" : "REPS")
                        .font(.caption)
                        .fontWeight(.bold)
                        .foregroundColor(.white.opacity(0.5))
                        .lineLimit(1)
                    HStack(spacing: 0) {
                        HStack(spacing: 0) {
                            Image(systemName: "minus")
                                .font(.system(size: 10))
                                .foregroundColor(.white.opacity(0.6))
                                .frame(width: 30)
                                .onTapGesture {
                                    let c = (set.targetDuration ?? set.targetReps) ?? 0
                                    onUpdateSet(set.id) {
                                        if $0.targetDuration != nil {
                                            return $0.copy(targetDuration: max(0, c - 1))
                                        } else {
                                            return $0.copy(targetReps: max(0, c - 1))
                                        }
                                    }
                                }
                            Text({
                                let val = set.targetDuration ?? set.targetReps
                                if let v = val { return "\(v)" }
                                return "-"
                            }())
                            .font(.title3)
                            .fontWeight(.black)
                            .foregroundColor(.white)
                            .lineLimit(1)
                            .frame(maxWidth: .infinity)
                            Image(systemName: "plus")
                                .font(.system(size: 10))
                                .foregroundColor(sessionAccentColor)
                                .frame(width: 30)
                                .onTapGesture {
                                    let c = (set.targetDuration ?? set.targetReps) ?? 0
                                    onUpdateSet(set.id) {
                                        if $0.targetDuration != nil {
                                            return $0.copy(targetDuration: c + 1)
                                        } else {
                                            return $0.copy(targetReps: c + 1)
                                        }
                                    }
                                }
                        }
                    }
                    .frame(height: 40)
                    .background(Color(hex: 0x252525))
                    .cornerRadius(10)
                }

                VStack(spacing: 4) {
                    Text("CARGA")
                        .font(.caption)
                        .fontWeight(.bold)
                        .foregroundColor(.white.opacity(0.5))
                        .lineLimit(1)
                    HStack(spacing: 0) {
                        Image(systemName: "minus")
                            .font(.system(size: 10))
                            .foregroundColor(.white.opacity(0.6))
                            .frame(width: 30)
                            .onTapGesture {
                                let c = set.weight ?? 0.0
                                onUpdateSet(set.id) { $0.copy(weight: max(0.0, c - 2.5)) }
                            }
                        Text(set.weight?.toTrimmedNumberString() ?? "-")
                            .font(.title3)
                            .fontWeight(.black)
                            .foregroundColor(.white)
                            .lineLimit(1)
                            .frame(maxWidth: .infinity)
                        Image(systemName: "plus")
                            .font(.system(size: 10))
                            .foregroundColor(sessionAccentColor)
                            .frame(width: 30)
                            .onTapGesture {
                                let c = set.weight ?? 0.0
                                onUpdateSet(set.id) { $0.copy(weight: c + 2.5) }
                            }
                    }
                    .frame(height: 40)
                    .background(Color(hex: 0x252525))
                    .cornerRadius(10)
                }
            }

            // Intensity mode
            HStack(spacing: 6) {
                Text("Modo:")
                    .font(.caption)
                    .foregroundColor(.white.opacity(0.5))
                    .lineLimit(1)
                HStack(spacing: 4) {
                    ForEach([(IntensityMode.RPE, "RPE"), (.RIR, "RIR"), (.FAILURE, "Fallo")], id: \.0.rawValue) { mode, label in
                        let isSelected = set.intensityMode == mode || (mode == .FAILURE && set.isFailure)
                        Text(label)
                            .font(.caption)
                            .padding(.horizontal, 6)
                            .padding(.vertical, 3)
                            .background(isSelected ? (mode == .FAILURE ? Color(hex: 0xFF5252).opacity(0.2) : sessionAccentColor.opacity(0.2)) : Color(.systemGray5))
                            .foregroundColor(isSelected ? (mode == .FAILURE ? Color(hex: 0xFF5252) : sessionAccentColor) : .primary)
                            .cornerRadius(6)
                            .onTapGesture {
                                onUpdateSet(set.id) {
                                    switch mode {
                                    case .FAILURE:
                                        return $0.copy(intensityMode: mode, isFailure: true, targetRPE: nil, targetRIR: nil)
                                    case .RIR:
                                        return $0.copy(intensityMode: mode, isFailure: false, targetRPE: nil)
                                    case .RPE:
                                        return $0.copy(intensityMode: mode, isFailure: false, targetRIR: nil)
                                    default:
                                        return $0
                                    }
                                }
                            }
                    }
                }
                if set.intensityMode != .FAILURE {
                    Spacer()
                    HStack(spacing: 0) {
                        Image(systemName: "minus")
                            .font(.system(size: 8))
                            .foregroundColor(.white.opacity(0.6))
                            .frame(width: 26, height: 26)
                            .onTapGesture {
                                let c = set.intensityMode == .RIR ? Double(set.targetRIR ?? 2) : (set.targetRPE ?? 8.0)
                                let s = set.intensityMode == .RIR ? 1.0 : 0.5
                                let n = max(0.0, c - s)
                                onUpdateSet(set.id) {
                                    if $0.intensityMode == .RIR {
                                        return $0.copy(targetRIR: Int(n))
                                    } else {
                                        return $0.copy(targetRPE: n)
                                    }
                                }
                            }
                        Text({
                            if set.intensityMode == .RIR {
                                return set.targetRIR.map { "\($0)" } ?? "-"
                            }
                            return set.targetRPE?.toTrimmedNumberString() ?? "-"
                        }())
                        .font(.body)
                        .fontWeight(.bold)
                        .foregroundColor(.white)
                        .lineLimit(1)
                        .frame(minWidth: 28)
                        .multilineTextAlignment(.center)
                        Image(systemName: "plus")
                            .font(.system(size: 8))
                            .foregroundColor(sessionAccentColor)
                            .frame(width: 26, height: 26)
                            .onTapGesture {
                                let c = set.intensityMode == .RIR ? Double(set.targetRIR ?? 2) : (set.targetRPE ?? 8.0)
                                let s = set.intensityMode == .RIR ? 1.0 : 0.5
                                let n = min(10.0, c + s)
                                onUpdateSet(set.id) {
                                    if $0.intensityMode == .RIR {
                                        return $0.copy(targetRIR: Int(n))
                                    } else {
                                        return $0.copy(targetRPE: n)
                                    }
                                }
                            }
                    }
                }
            }
            .padding(.horizontal, 8)
            .padding(.vertical, 6)
            .background(Color(hex: 0x252525))
            .cornerRadius(10)

            if expanded {
                Divider()
                    .background(Color.white.opacity(0.06))

                Text("MODO DE CARGA")
                    .font(.caption)
                    .fontWeight(.bold)
                    .foregroundColor(.white.opacity(0.5))
                    .tracking(1)

                FlexibleStack(spacing: 6) {
                    ForEach([LoadModeV2.LOAD, .BODYWEIGHT, .LASTRE, .ASSISTED], id: \.self) { mode in
                        let label: String = {
                            switch mode {
                            case .LOAD: return "Carga"
                            case .BODYWEIGHT: return "Peso corporal"
                            case .LASTRE: return "Lastre"
                            case .ASSISTED: return "Asistido"
                            }
                        }()
                        Text(label)
                            .font(.caption)
                            .padding(.horizontal, 8)
                            .padding(.vertical, 4)
                            .background(set.loadModeV2 == mode ? sessionAccentColor.opacity(0.2) : Color(.systemGray5))
                            .cornerRadius(8)
                            .onTapGesture { onUpdateSet(set.id) { $0.copy(loadModeV2: mode) } }
                    }
                }

                Text("UNIDAD")
                    .font(.caption)
                    .fontWeight(.bold)
                    .foregroundColor(.white.opacity(0.5))
                    .tracking(1)

                FlexibleStack(spacing: 6) {
                    ForEach([(UnitModeV2.REPS, "Reps"), (.TIME, "Tiempo"), (.DISTANCE, "Distancia")], id: \.0.rawValue) { mode, label in
                        Text(label)
                            .font(.caption)
                            .padding(.horizontal, 8)
                            .padding(.vertical, 4)
                            .background(set.unitModeV2 == mode ? sessionAccentColor.opacity(0.2) : Color(.systemGray5))
                            .cornerRadius(8)
                            .onTapGesture { onUpdateSet(set.id) { $0.copy(unitModeV2: mode) } }
                    }
                }

                VStack(spacing: 8) {
                    HStack {
                        Text("% RM")
                            .font(.caption)
                            .fontWeight(.bold)
                            .foregroundColor(.white.opacity(0.5))
                            .frame(width: 60, alignment: .leading)
                        Text(":")
                            .font(.caption)
                            .foregroundColor(.white.opacity(0.3))
                        Text(set.targetPercentageRM?.toTrimmedNumberString() ?? "-")
                            .font(.body)
                            .fontWeight(.bold)
                            .foregroundColor(.white)
                            .lineLimit(1)
                        Spacer()
                        HStack(spacing: 0) {
                            Image(systemName: "minus")
                                .font(.system(size: 8))
                                .foregroundColor(.white.opacity(0.6))
                                .frame(width: 28, height: 28)
                                .onTapGesture {
                                    onUpdateSet(set.id) { $0.copy(targetPercentageRM: max(10.0, ($0.targetPercentageRM ?? 50.0) - 5.0)) }
                                }
                            Image(systemName: "plus")
                                .font(.system(size: 8))
                                .foregroundColor(sessionAccentColor)
                                .frame(width: 28, height: 28)
                                .onTapGesture {
                                    onUpdateSet(set.id) { $0.copy(targetPercentageRM: min(100.0, ($0.targetPercentageRM ?? 50.0) + 5.0)) }
                                }
                        }
                    }
                    HStack {
                        Text("Máquina")
                            .font(.caption)
                            .fontWeight(.bold)
                            .foregroundColor(.white.opacity(0.5))
                            .frame(width: 60, alignment: .leading)
                            .lineLimit(1)
                        Text(":")
                            .font(.caption)
                            .foregroundColor(.white.opacity(0.3))
                        Text(set.machineBrand ?? "—")
                            .font(.body)
                            .fontWeight(.bold)
                            .foregroundColor(.white)
                            .lineLimit(1)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }
                }
                .padding(10)
                .background(Color(hex: 0x252525))
                .cornerRadius(8)
            }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 10)
        .background(Color(hex: 0x1A1A1A))
        .cornerRadius(14)
        .overlay(
            RoundedRectangle(cornerRadius: 14)
                .stroke(Color.white.opacity(0.06), lineWidth: 1)
        )
    }
}

// MARK: - WorkoutRmCalcContent

struct WorkoutRmCalcContent: View {
    let bodyWeight: Double?
    let currentLoadMode: LoadModeV2?
    let onWeightSelected: ((Double, LoadModeV2?) -> Void)?
    let sessionAccentColor: Color

    @State private var rmWeightText: String = ""
    @State private var rmRepsText: String = ""

    var body: some View {
        let isAssisted = currentLoadMode == .ASSISTED
        let isLastre = currentLoadMode == .LASTRE

        let weightLabel: String = {
            switch currentLoadMode {
            case .LASTRE: return "Lastre (kg)"
            case .ASSISTED: return "Asistencia (kg)"
            default: return "Peso (kg)"
            }
        }()

        let effectiveLoad: Double = {
            let w = Double(rmWeightText) ?? 0.0
            if isLastre, let bw = bodyWeight, bw > 0 { return bw + w }
            if isAssisted, let bw = bodyWeight, bw > 0 { return max(0.0, bw - w) }
            return w
        }()

        let rmResult: Double? = {
            let r = Int(rmRepsText) ?? 0
            if effectiveLoad > 0 && r > 0 { return calculateHybrid1RM(effectiveLoad, r) }
            return nil
        }()

        let rmTable: [(Int, (Double, Double?, Double?))] = {
            guard let result = rmResult else { return [] }
            let estRms: [(Int, Double)] = (1...10).map { reps in
                let estLoad = result / (1.0 + Double(reps) / 30.0)
                return (reps, estLoad)
            }
            if isAssisted, let bw = bodyWeight, bw > 0 {
                return estRms.map { (reps, load) in
                    let assistance = max(0.0, bw - load)
                    return (reps, (load, assistance, nil))
                }
            } else if isLastre, let bw = bodyWeight, bw > 0 {
                return estRms.map { (reps, load) in
                    let lastre = max(0.0, load - bw)
                    return (reps, (load, nil, lastre))
                }
            } else {
                return estRms.map { (reps, load) in (reps, (load, nil, nil)) }
            }
        }()

        VStack(spacing: 8) {
            HStack(spacing: 8) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(weightLabel)
                        .font(.caption)
                        .foregroundColor(.secondary)
                    TextField("", text: $rmWeightText)
                        .keyboardType(.decimalPad)
                        .padding(8)
                        .background(Color(.systemGray6))
                        .cornerRadius(8)
                }
                VStack(alignment: .leading, spacing: 4) {
                    Text("Reps")
                        .font(.caption)
                        .foregroundColor(.secondary)
                    TextField("", text: $rmRepsText)
                        .keyboardType(.numberPad)
                        .padding(8)
                        .background(Color(.systemGray6))
                        .cornerRadius(8)
                }
            }

            if let result = rmResult, !rmTable.isEmpty {
                HStack {
                    Text("e1RM: \(String(format: "%.1f", result)) kg")
                        .font(.caption)
                        .fontWeight(.bold)
                        .foregroundColor(Color(hex: 0x4CAF50))
                    if (isAssisted || isLastre), let bw = bodyWeight {
                        Text("Peso corporal: \(bw.toTrimmedNumberString()) kg")
                            .font(.caption)
                            .foregroundColor(.white.opacity(0.6))
                    }
                    Spacer()
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 8)
                .background(Color(hex: 0x1A3A1A))
                .cornerRadius(12)

                Text("Tabla RM")
                    .font(.footnote)
                    .fontWeight(.bold)
                    .foregroundColor(.white.opacity(0.7))
                    .frame(maxWidth: .infinity, alignment: .leading)

                FlexibleStack(spacing: 4) {
                    ForEach(rmTable, id: \.0) { (reps, triple) in
                        let (estLoad, assistance, lastre) = triple
                        let displayWeight: Double = assistance ?? lastre ?? estLoad
                        let suffix: String = {
                            if assistance != nil { return "kg asistencia" }
                            if lastre != nil { return "kg lastre" }
                            return "kg"
                        }()
                        VStack(spacing: 2) {
                            Text("\(reps)RM")
                                .font(.caption)
                                .fontWeight(.bold)
                                .foregroundColor(sessionAccentColor)
                            Text(String(format: "%.1f", displayWeight))
                                .font(.caption)
                                .foregroundColor(.white)
                                .lineLimit(1)
                            Text(suffix)
                                .font(.system(size: 8))
                                .foregroundColor(.white.opacity(0.5))
                                .lineLimit(1)
                        }
                        .padding(.horizontal, 8)
                        .padding(.vertical, 6)
                        .frame(minWidth: 72)
                        .background(Color(hex: 0x2A2A2A))
                        .cornerRadius(10)
                        .overlay(
                            RoundedRectangle(cornerRadius: 10)
                                .stroke(Color.white.opacity(0.08), lineWidth: 1)
                        )
                        .onTapGesture {
                            onWeightSelected?(displayWeight, currentLoadMode)
                        }
                    }
                }
            }
        }
    }
}

// MARK: - WorkoutExerciseDrainContent

struct WorkoutExerciseDrainContent: View {
    let drain: PredictedDrain
    let involvedMuscles: [InvolvedMuscle]

    var body: some View {
        VStack(spacing: 8) {
            HStack(spacing: 8) {
                ForEach([("Energia", drain.cns), ("Columna", drain.spinal)], id: \.0) { label, value in
                    VStack(spacing: 2) {
                        Text(label)
                            .font(.caption)
                            .foregroundColor(.secondary)
                            .frame(maxWidth: .infinity, alignment: .leading)
                        Text("-\(value)%")
                            .font(.title)
                            .fontWeight(.black)
                            .foregroundColor(value >= 20 ? .red : value >= 10 ? .accentColor : .blue)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    .padding(.horizontal, 12)
                    .padding(.vertical, 10)
                    .background(Color(.systemGray6))
                    .cornerRadius(14)
                }
            }

            if !involvedMuscles.isEmpty {
                let roleWeights: [Double] = involvedMuscles.map { inv in
                    switch inv.role {
                    case .PRIMARY: return 1.0
                    case .SECONDARY: return 0.5
                    case .STABILIZER: return 0.25
                    case .NEUTRALIZER: return 0.15
                    }
                }
                let totalWeight = max(0.001, roleWeights.reduce(0, +))

                VStack(spacing: 0) {
                    ForEach(Array(involvedMuscles.enumerated()), id: \.offset) { i, inv in
                        let muscleDrain = Int(((roleWeights[i] / totalWeight) * Double(drain.muscular)).rounded())
                        let dotColor: Color = {
                            switch inv.role {
                            case .PRIMARY: return .red
                            case .SECONDARY: return .blue
                            case .STABILIZER: return .accentColor
                            case .NEUTRALIZER: return .gray
                            }
                        }()
                        HStack {
                            HStack(spacing: 8) {
                                Circle()
                                    .fill(dotColor)
                                    .frame(width: 7, height: 7)
                                Text(inv.muscle)
                                    .font(.caption)
                                    .fontWeight(inv.role == .PRIMARY ? .bold : .regular)
                            }
                            Spacer()
                            Text("-\(muscleDrain)%")
                                .font(.caption)
                                .fontWeight(.semibold)
                                .foregroundColor(muscleDrain >= 20 ? .red : muscleDrain >= 10 ? .accentColor : .secondary)
                        }
                        .padding(.vertical, 5)

                        if i < involvedMuscles.count - 1 {
                            Divider()
                                .background(Color.gray.opacity(0.2))
                        }
                    }
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 6)
                .background(Color(.systemGray6))
                .cornerRadius(12)
            }
        }
    }
}

// MARK: - WorkoutSessionEnergyContent

struct WorkoutSessionEnergyContent: View {
    let sessionEnergy: SessionEnergySummary

    @State private var expanded: Bool = false

    var body: some View {
        let contributions = sessionEnergy.exerciseContributions
        let hasSets = contributions.contains { $0.completedSets > 0 } || sessionEnergy.totalKcal.mid > 0

        VStack(spacing: 10) {
            Text("Calorías de la sesión")
                .font(.subheadline)
                .fontWeight(.bold)
                .foregroundColor(.secondary)
                .frame(maxWidth: .infinity, alignment: .leading)

            if !hasSets {
                Text("Completa series para estimar las calorías")
                    .font(.body)
                    .foregroundColor(.secondary.opacity(0.6))
                    .frame(maxWidth: .infinity, alignment: .leading)
                return
            }

            let confidenceLabel: String = {
                switch sessionEnergy.confidence {
                case .HIGH: return "alta"
                case .MEDIUM: return "media"
                case .LOW: return "baja"
                }
            }()

            HStack(spacing: 8) {
                VStack(spacing: 2) {
                    Text("Total estimado")
                        .font(.caption)
                        .foregroundColor(.blue.opacity(0.8))
                        .frame(maxWidth: .infinity, alignment: .leading)
                    Text("\(sessionEnergy.totalKcal.mid)")
                        .font(.title)
                        .fontWeight(.black)
                        .foregroundColor(.blue)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    Text("\(sessionEnergy.totalKcal.low)–\(sessionEnergy.totalKcal.high) kcal")
                        .font(.caption)
                        .foregroundColor(.blue.opacity(0.7))
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 10)
                .background(Color.blue.opacity(0.1))
                .cornerRadius(14)

                VStack(spacing: 2) {
                    Text("Activo")
                        .font(.caption)
                        .foregroundColor(.secondary)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    Text("\(sessionEnergy.activeKcal.mid) kcal")
                        .font(.headline)
                        .fontWeight(.bold)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 10)
                .background(Color(.systemGray6))
                .cornerRadius(14)
            }

            HStack(spacing: 8) {
                VStack(spacing: 2) {
                    Text("EPOC")
                        .font(.caption)
                        .foregroundColor(.secondary)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    Text("\(sessionEnergy.epocKcal.mid) kcal")
                        .font(.headline)
                        .fontWeight(.bold)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 10)
                .background(Color(.systemGray6))
                .cornerRadius(14)

                VStack(spacing: 2) {
                    Text("Confianza")
                        .font(.caption)
                        .foregroundColor(.accentColor.opacity(0.8))
                        .frame(maxWidth: .infinity, alignment: .leading)
                    Text(confidenceLabel)
                        .font(.caption)
                        .fontWeight(.black)
                        .foregroundColor(.accentColor)
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 10)
                .background(Color.accentColor.opacity(0.1))
                .cornerRadius(14)
            }

            if hasSets {
                let maxForBar = max(1, sessionEnergy.totalKcal.high)
                let fraction = max(0, min(1, Float(sessionEnergy.totalKcal.mid) / Float(maxForBar)))

                VStack(spacing: 4) {
                    HStack {
                        Text("Barra de calorías totales")
                            .font(.caption)
                            .fontWeight(.semibold)
                            .foregroundColor(.secondary)
                        Spacer()
                        Text("\(sessionEnergy.totalKcal.mid) / \(sessionEnergy.totalKcal.high) kcal")
                            .font(.caption)
                            .fontWeight(.bold)
                            .foregroundColor(.blue)
                    }
                    GeometryReader { geo in
                        ZStack(alignment: .leading) {
                            RoundedRectangle(cornerRadius: 5)
                                .fill(Color(.systemGray5))
                                .frame(height: 10)
                            RoundedRectangle(cornerRadius: 5)
                                .fill(Color.blue)
                                .frame(width: geo.size.width * CGFloat(fraction), height: 10)
                        }
                    }
                    .frame(height: 10)
                }
            }

            if let projected = sessionEnergy.projectedTotalKcal {
                Text("Proyección al finalizar: ~\(projected) kcal")
                    .font(.caption)
                    .fontWeight(.semibold)
                    .foregroundColor(.secondary)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 6)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color.blue.opacity(0.08))
                    .cornerRadius(10)
            }

            if !contributions.isEmpty {
                let totalKcal = max(1, contributions.reduce(0) { $0 + $1.totalKcal })

                HStack {
                    Text(expanded ? "Ocultar desglose" : "Ver desglose")
                        .font(.footnote)
                        .fontWeight(.bold)
                        .foregroundColor(.blue)
                    Spacer()
                    Image(systemName: expanded ? "chevron.up" : "chevron.down")
                        .font(.system(size: 12))
                        .foregroundColor(.blue)
                }
                .onTapGesture { expanded.toggle() }

                if expanded {
                    VStack(spacing: 6) {
                        Divider()
                            .background(Color.gray.opacity(0.3))

                        Text("Por ejercicio")
                            .font(.footnote)
                            .fontWeight(.bold)
                            .frame(maxWidth: .infinity, alignment: .leading)

                        ForEach(contributions.indices, id: \.self) { i in
                            let contribution = contributions[i]
                            let fraction = max(0, min(1, Float(contribution.totalKcal) / Float(totalKcal)))

                            VStack(spacing: 4) {
                                HStack {
                                    Text(contribution.exerciseName)
                                        .font(.caption)
                                        .fontWeight(.semibold)
                                        .lineLimit(1)
                                        .frame(maxWidth: .infinity, alignment: .leading)
                                    Text("\(contribution.totalKcal) kcal")
                                        .font(.caption)
                                        .fontWeight(.bold)
                                        .foregroundColor(.blue)
                                }
                                GeometryReader { geo in
                                    ZStack(alignment: .leading) {
                                        RoundedRectangle(cornerRadius: 3)
                                            .fill(Color(.systemGray5))
                                            .frame(height: 6)
                                        RoundedRectangle(cornerRadius: 3)
                                            .fill(Color.blue)
                                            .frame(width: geo.size.width * CGFloat(fraction), height: 6)
                                    }
                                }
                                .frame(height: 6)

                                HStack {
                                    Text("\(String(format: "%.1f", contribution.percentageOfSession))% del total")
                                        .font(.caption)
                                        .foregroundColor(.secondary.opacity(0.7))
                                    Spacer()
                                    Text("\(contribution.completedSets)/\(contribution.totalSets) sets")
                                        .font(.caption)
                                        .foregroundColor(.secondary.opacity(0.7))
                                }
                            }
                            .padding(10)
                            .background(Color(.systemGray6))
                            .cornerRadius(10)
                        }
                    }
                }
            }

            if !sessionEnergy.notes.isEmpty {
                ForEach(sessionEnergy.notes, id: \.self) { note in
                    Text(note)
                        .font(.caption)
                        .foregroundColor(.secondary.opacity(0.6))
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
            }
        }
    }
}

// MARK: - ExerciseDrainOverlayHost

struct ExerciseDrainOverlayHost: View {
    let state: ExerciseDrainOverlayStateV2?

    var body: some View {
        if let overlay = state {
            ExerciseDrainOverlayCard(state: overlay)
                .frame(maxWidth: .infinity)
                .frame(maxWidth: 520)
                .transition(.opacity)
        }
    }
}

// MARK: - ExerciseDrainOverlayCard

private struct ExerciseDrainOverlayCard: View {
    let state: ExerciseDrainOverlayStateV2

    var body: some View {
        VStack(spacing: 8) {
            Text("Drenaje de \(state.exerciseName)")
                .font(.footnote)
                .fontWeight(.bold)
                .lineLimit(1)
                .foregroundColor(.secondary)
                .frame(maxWidth: .infinity, alignment: .leading)

            ForEach(Array(state.items.enumerated()), id: \.offset) { index, item in
                ExerciseDrainAnimatedRow(item: item, index: index)
            }
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 12)
        .background(Color(.systemBackground).opacity(0.97))
        .cornerRadius(18)
        .shadow(color: .black.opacity(0.3), radius: 18)
    }
}

// MARK: - ExerciseDrainAnimatedRow

private struct ExerciseDrainAnimatedRow: View {
    let item: ExerciseDrainOverlayItemV2
    let index: Int

    @State private var shouldDrain: Bool = false

    var body: some View {
        let baseFraction: Float = min(1.0, max(0.16, Float(item.delta) / 24.0))
        let accent: Color = {
            switch item.channel {
            case .energy: return Color(hex: 0x58C4FF)
            case .back: return Color(hex: 0xFFB85C)
            case .muscle: return Color(hex: 0xFF6F7D)
            }
        }()

        VStack(spacing: 4) {
            HStack {
                Text(item.label)
                    .font(.caption)
                    .fontWeight(.semibold)
                    .foregroundColor(.primary)
                Spacer()
                Text("-\(item.delta)%")
                    .font(.footnote)
                    .fontWeight(.black)
                    .foregroundColor(accent)
            }
            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    RoundedRectangle(cornerRadius: 999)
                        .fill(Color(.systemGray5))
                        .frame(height: 7)
                    RoundedRectangle(cornerRadius: 999)
                        .fill(accent)
                        .frame(width: geo.size.width * CGFloat(shouldDrain ? 0 : baseFraction), height: 7)
                        .animation(.easeOut(duration: 0.62).delay(Double(index) * 0.045), value: shouldDrain)
                }
            }
            .frame(height: 7)
        }
        .onAppear {
            shouldDrain = true
        }
    }
}

// MARK: - Standalone Functions

func normalizeWorkoutMuscleKey(_ value: String) -> String {
    value
        .lowercased()
        .trimmingCharacters(in: .whitespaces)
        .folding(options: .diacriticInsensitive, locale: nil)
}

func workoutCatalogInfo(exercise: Exercise) -> ExerciseMuscleInfo? {
    let canonicalId = exercise.resolvedCanonicalExerciseId()
    if let info = catalogExerciseIndex()[canonicalId] { return info }
    if let dbId = exercise.exerciseDbId?.lowercased(), let info = catalogExerciseIndex()[dbId] { return info }
    if let exId = exercise.exerciseId?.lowercased(), let info = catalogExerciseIndex()[exId] { return info }
    return nil
}

func workoutCatalogInfo(exercise: CompletedExercise) -> ExerciseMuscleInfo? {
    let canonicalId = exercise.resolvedCanonicalExerciseId()
    if let info = catalogExerciseIndex()[canonicalId] { return info }
    if let dbId = exercise.exerciseDbId?.lowercased(), let info = catalogExerciseIndex()[dbId] { return info }
    if !exercise.exerciseId.isEmpty, let info = catalogExerciseIndex()[exercise.exerciseId.lowercased()] { return info }
    return nil
}

func displayWorkoutMuscleGroup(_ group: String?) -> String? {
    guard let g = group else { return nil }
    switch g {
    case "Pectorales": return "Pecho"
    case "Dorsales": return "Espalda"
    case "Deltoides": return "Hombros"
    case "Antebrazo": return "Antebrazos"
    case "Isquiosurales": return "Isquios"
    case "Abdomen": return "Core"
    default: return g
    }
}

func canonicalWorkoutMuscleColor(_ group: String) -> Color {
    wikilabMuscleColor(group)
}

func workoutOverlayContentColor(_ color: Color) -> Color {
    var r: CGFloat = 0; var g: CGFloat = 0; var b: CGFloat = 0; var a: CGFloat = 0
    UIColor(color).getRed(&r, green: &g, blue: &b, alpha: &a)
    let luminance = 0.299 * r + 0.587 * g + 0.114 * b
    return luminance > 0.55 ? .black : .white
}

func buildExerciseDrainOverlayState(
    exerciseName: String,
    drain: PredictedDrain,
    involvedMuscles: [InvolvedMuscle]
) -> ExerciseDrainOverlayStateV2 {
    var items: [ExerciseDrainOverlayItemV2] = []

    if drain.cns > 0 {
        items.append(ExerciseDrainOverlayItemV2(
            label: "Energia",
            delta: max(1, drain.cns),
            channel: .energy
        ))
    }
    if drain.spinal > 0 {
        items.append(ExerciseDrainOverlayItemV2(
            label: "Columna",
            delta: max(1, drain.spinal),
            channel: .back
        ))
    }
    if drain.muscular > 0 {
        let totalWeight = max(0.001, involvedMuscles.reduce(0.0) { $0 + resolveMuscleVolumeContribution(involvement: $1) })
        let topMuscles = involvedMuscles
            .sorted { resolveMuscleVolumeContribution(involvement: $0) > resolveMuscleVolumeContribution(involvement: $1) }
            .prefix(3)

        let topArray = Array(topMuscles)
        if !topArray.isEmpty && totalWeight > 0 {
            for involved in topArray {
                let label: String = {
                    if let e = involved.emphasis, !e.isEmpty {
                        return "\(involved.muscle) · \(e)"
                    }
                    return involved.muscle
                }()
                let share = resolveMuscleVolumeContribution(involvement: involved) / totalWeight
                let delta = max(1, Int((share * Double(drain.muscular)).rounded()))
                items.append(ExerciseDrainOverlayItemV2(
                    label: label,
                    delta: delta,
                    channel: .muscle
                ))
            }
        } else {
            items.append(ExerciseDrainOverlayItemV2(
                label: "Muscular",
                delta: max(1, drain.muscular),
                channel: .muscle
            ))
        }
    }

    return ExerciseDrainOverlayStateV2(
        key: "\(Date().timeIntervalSince1970)",
        exerciseName: exerciseName,
        items: items
    )
}

func readinessLabel(_ score: Int) -> String {
    if score >= 80 { return "Óptima" }
    if score >= 60 { return "Buena" }
    if score >= 40 { return "Regular" }
    return "Baja"
}

// MARK: - Helper Views

struct SetupField: View {
    let label: String
    @Binding var text: String

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(label)
                .font(.caption)
                .foregroundColor(.secondary)
            TextField("", text: $text)
                .textFieldStyle(.plain)
                .padding(8)
                .background(Color(.systemGray6))
                .cornerRadius(8)
        }
    }
}

// MARK: - FlexibleStack (flow/wrapping layout)

struct FlexibleStack: Layout {
    let spacing: CGFloat

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let maxWidth = proposal.width ?? 0
        var height: CGFloat = 0
        var currentRowWidth: CGFloat = 0
        var currentRowHeight: CGFloat = 0

        for subview in subviews {
            let size = subview.sizeThatFits(.unspecified)
            if currentRowWidth + size.width > maxWidth, currentRowWidth > 0 {
                height += currentRowHeight + spacing
                currentRowWidth = size.width
                currentRowHeight = size.height
            } else {
                currentRowWidth += size.width + spacing
                currentRowHeight = max(currentRowHeight, size.height)
            }
        }
        height += currentRowHeight
        return CGSize(width: maxWidth, height: height)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var x = bounds.minX
        var y = bounds.minY
        var rowHeight: CGFloat = 0

        for subview in subviews {
            let size = subview.sizeThatFits(.unspecified)
            if x + size.width > bounds.maxX, x > bounds.minX {
                y += rowHeight + spacing
                x = bounds.minX
                rowHeight = 0
            }
            subview.place(at: CGPoint(x: x, y: y), proposal: .unspecified)
            x += size.width + spacing
            rowHeight = max(rowHeight, size.height)
        }
    }
}

// MARK: - Wikilab Muscle Color Stub

func wikilabMuscleColor(_ group: String) -> Color {
    switch group.lowercased().trimmingCharacters(in: .whitespaces) {
    case "pectorales", "pecho": return Color(hex: 0xE53935)
    case "dorsales", "espalda": return Color(hex: 0x1E88E5)
    case "cuadriceps", "cuádriceps", "quads": return Color(hex: 0x43A047)
    case "isquiosurales", "isquios", "isquiotibiales": return Color(hex: 0xFB8C00)
    case "gluteos", "glúteos", "glutes": return Color(hex: 0x8E24AA)
    case "deltoides", "hombros", "hombro": return Color(hex: 0xF4511E)
    case "biceps", "bíceps", "brazos": return Color(hex: 0x00ACC1)
    case "triceps", "tríceps": return Color(hex: 0x6D4C41)
    case "antebrazos", "antebrazo", "forearms": return Color(hex: 0x78909C)
    case "trapecios", "trapecio", "traps": return Color(hex: 0x546E7A)
    case "abdomen", "core", "abdominales": return Color(hex: 0xFFD54F)
    case "oblicuos", "oblicuo": return Color(hex: 0xFFA726)
    case "hombro posterior", "deltoides posterior": return Color(hex: 0xD84315)
    case "hombro lateral", "deltoides lateral": return Color(hex: 0xE64A19)
    case "hombro frontal", "deltoides frontal": return Color(hex: 0xF57C00)
    case "gemelos", "soleo", "sóleo", "calf", "pantorrillas": return Color(hex: 0x00897B)
    default: return Color(hex: 0x90A4AE)
    }
}


