import SwiftUI

private struct SanitizedWarmupSet {
    let percentage: Int
    let reps: Int
}

private func sanitizeWarmupPercentage(_ rawPercentage: Double) -> Int {
    return (10.0...95.0).contains(rawPercentage) ? Int(rawPercentage) : 50
}

private func sanitizeWarmupReps(_ rawReps: Int, percentage: Int) -> Int {
    return (1...20).contains(rawReps) ? rawReps : suggestedWarmupRepsForPercentage(percentage)
}

private func suggestedWarmupRepsForPercentage(_ percentage: Int) -> Int {
    if percentage <= 40 { return 10 }
    if percentage <= 60 { return 6 }
    if percentage <= 75 { return 4 }
    if percentage <= 85 { return 2 }
    return 1
}

public struct WorkoutWarmupInlineCard: View {
    let exercise: Exercise
    let workingWeightKg: Double?
    let onToggleComplete: (Bool) -> Void
    let onDismiss: () -> Void
    let sessionAccentColor: Color
    
    @State private var checkedSets: [Bool] = []
    
    public init(
        exercise: Exercise,
        workingWeightKg: Double?,
        onToggleComplete: @escaping (Bool) -> Void,
        onDismiss: @escaping () -> Void,
        sessionAccentColor: Color = Color(red: 255/255.0, green: 179/255.0, blue: 0/255.0)
    ) {
        self.exercise = exercise
        self.workingWeightKg = workingWeightKg
        self.onToggleComplete = onToggleComplete
        self.onDismiss = onDismiss
        self.sessionAccentColor = sessionAccentColor
    }
    
    private var safeWarmupSets: [SanitizedWarmupSet] {
        exercise.warmupSets.map { set in
            let safePct = sanitizeWarmupPercentage(set.percentageOfWorkingWeight)
            return SanitizedWarmupSet(percentage: safePct, reps: sanitizeWarmupReps(set.targetReps, percentage: safePct))
        }
    }
    
    public var body: some View {
        VStack(spacing: 12) {
            // Header Row
            HStack {
                HStack(spacing: 10) {
                    Image(systemName: "flame.fill")
                        .font(.system(size: 20))
                        .padding(6)
                        .background(sessionAccentColor.opacity(0.15))
                        .clipShape(Circle())
                        .foregroundColor(sessionAccentColor)
                    
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Series de aproximación")
                            .font(.headline)
                            .fontWeight(.black)
                            .foregroundColor(sessionAccentColor)
                        Text("Prepara tus articulaciones y sistema nervioso")
                            .font(.caption2)
                            .foregroundColor(.white.opacity(0.6))
                    }
                }
                
                Spacer()
                
                if let workingWeightKg = workingWeightKg, workingWeightKg > 0 {
                    Text("\(workingWeightKg.toTrimmedNumberString()) kg base")
                        .font(.caption2)
                        .fontWeight(.bold)
                        .foregroundColor(sessionAccentColor)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .background(sessionAccentColor.opacity(0.12))
                        .cornerRadius(8)
                }
            }
            
            // Sets List
            VStack(spacing: 8) {
                ForEach(0..<safeWarmupSets.count, id: \.self) { idx in
                    let set = safeWarmupSets[idx]
                    let warmupKg = (workingWeightKg != nil && workingWeightKg! > 0) ? workingWeightKg! * (Double(set.percentage) / 100.0) : nil
                    let checked = checkedSets.indices.contains(idx) ? checkedSets[idx] : false
                    
                    HStack {
                        HStack(spacing: 8) {
                            Button(action: {
                                if idx < checkedSets.count {
                                    checkedSets[idx].toggle()
                                }
                            }) {
                                Image(systemName: checked ? "checkmark.square.fill" : "square")
                                    .font(.system(size: 24))
                                    .foregroundColor(checked ? sessionAccentColor : Color.white.opacity(0.3))
                            }
                            .buttonStyle(.plain)
                            
                            Text("Aprox. \(idx + 1)")
                                .font(.body)
                                .fontWeight(.bold)
                                .foregroundColor(.white)
                        }
                        
                        Spacer()
                        
                        HStack(spacing: 12) {
                            Text("\(set.percentage)%")
                                .font(.body)
                                .fontWeight(.black)
                                .foregroundColor(sessionAccentColor)
                            
                            Text("\(set.reps) reps")
                                .font(.body)
                                .fontWeight(.medium)
                                .foregroundColor(.white.opacity(0.7))
                            
                            if let warmupKg = warmupKg {
                                Text("\(warmupKg.toTrimmedNumberString()) kg")
                                    .font(.caption)
                                    .fontWeight(.black)
                                    .foregroundColor(sessionAccentColor)
                                    .padding(.horizontal, 8)
                                    .padding(.vertical, 2)
                                    .background(sessionAccentColor.opacity(0.15))
                                    .cornerRadius(6)
                            }
                        }
                    }
                    .padding(.horizontal, 12)
                    .frame(minHeight: 48)
                    .background(checked ? sessionAccentColor.opacity(0.08) : Color.white.opacity(0.03))
                    .cornerRadius(14)
                    .overlay(
                        RoundedRectangle(cornerRadius: 14)
                            .stroke(checked ? sessionAccentColor.opacity(0.25) : Color.white.opacity(0.06), lineWidth: 1)
                    )
                    .contentShape(Rectangle())
                    .onTapGesture {
                        if idx < checkedSets.count {
                            checkedSets[idx].toggle()
                        }
                    }
                }
            }
            
            // Buttons Row
            HStack(spacing: 8) {
                Button(action: onDismiss) {
                    Text("Saltar aproximación")
                        .font(.body)
                        .fontWeight(.bold)
                        .foregroundColor(.white.opacity(0.65))
                        .frame(maxWidth: .infinity)
                        .frame(height: 48)
                        .overlay(RoundedRectangle(cornerRadius: 24).stroke(Color.white.opacity(0.15), lineWidth: 1))
                }
                
                Button(action: {
                    onToggleComplete(true)
                    onDismiss()
                }) {
                    Text("Listo")
                        .font(.body)
                        .fontWeight(.black)
                        .foregroundColor(isLightColor(sessionAccentColor) ? .black : .white)
                        .frame(maxWidth: .infinity)
                        .frame(height: 48)
                        .background(sessionAccentColor)
                        .cornerRadius(24)
                }
            }
        }
        .padding(16)
        .background(Color(red: 20/255.0, green: 20/255.0, blue: 20/255.0))
        .cornerRadius(24)
        .overlay(RoundedRectangle(cornerRadius: 24).stroke(sessionAccentColor.opacity(0.35), lineWidth: 1))
        .padding(.horizontal, 12)
        .padding(.vertical, 6)
        .onAppear {
            checkedSets = Array(repeating: false, count: safeWarmupSets.count)
        }
        .onChange(of: exercise.warmupSets) { newValue in
            checkedSets = Array(repeating: false, count: newValue.count)
        }
    }
}

private func isLightColor(_ color: Color) -> Bool {
    var r: CGFloat = 0
    var g: CGFloat = 0
    var b: CGFloat = 0
    var a: CGFloat = 0
    #if os(iOS)
    UIColor(color).getRed(&r, green: &g, blue: &b, alpha: &a)
    #endif
    let luminance = 0.2126 * r + 0.7152 * g + 0.0722 * b
    return luminance > 0.45
}

public struct WorkoutSupersetWarmupRevealCard: View {
    let exercise: Exercise
    let onClick: () -> Void
    let onDismiss: () -> Void
    let sessionAccentColor: Color
    
    public init(
        exercise: Exercise,
        onClick: @escaping () -> Void,
        onDismiss: @escaping () -> Void,
        sessionAccentColor: Color = Color(red: 255/255.0, green: 179/255.0, blue: 0/255.0)
    ) {
        self.exercise = exercise
        self.onClick = onClick
        self.onDismiss = onDismiss
        self.sessionAccentColor = sessionAccentColor
    }
    
    public var body: some View {
        HStack(spacing: 12) {
            Image(systemName: "flame.fill")
                .font(.system(size: 18))
                .padding(6)
                .background(sessionAccentColor.opacity(0.12))
                .clipShape(Circle())
                .foregroundColor(sessionAccentColor)
            
            VStack(alignment: .leading, spacing: 2) {
                Text("Calentamiento disponible")
                    .font(.body)
                    .fontWeight(.black)
                    .foregroundColor(sessionAccentColor)
                Text(exercise.name)
                    .font(.caption)
                    .foregroundColor(.white.opacity(0.6))
                    .lineLimit(1)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            
            Button(action: onDismiss) {
                Text("Saltar")
                    .font(.body)
                    .fontWeight(.bold)
                    .foregroundColor(.white.opacity(0.6))
            }
            .frame(minHeight: 48)
            
            Button(action: onClick) {
                Text("Comenzar")
                    .font(.caption)
                    .fontWeight(.black)
                    .foregroundColor(isLightColor(sessionAccentColor) ? .black : .white)
                    .padding(.horizontal, 14)
                    .frame(height: 36)
                    .background(sessionAccentColor)
                    .cornerRadius(18)
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .background(Color(red: 20/255.0, green: 20/255.0, blue: 20/255.0))
        .cornerRadius(20)
        .overlay(RoundedRectangle(cornerRadius: 20).stroke(sessionAccentColor.opacity(0.25), lineWidth: 1))
        .padding(.horizontal, 12)
        .padding(.vertical, 4)
    }
}

public struct WorkoutWarmupDisplaySet: Codable {
    public let percentage: Double
    public let reps: Int
    public let targetWeight: Double?
    
    public init(percentage: Double, reps: Int, targetWeight: Double?) {
        self.percentage = percentage
        self.reps = reps
        self.targetWeight = targetWeight
    }
}

public struct WorkoutMobilityChecklistItem: Identifiable, Codable {
    public var id: String { "\(exerciseId)_\(mobility.id)" }
    public let exerciseId: String
    public let exerciseName: String
    public let mobility: MobilitySeries
    
    public init(exerciseId: String, exerciseName: String, mobility: MobilitySeries) {
        self.exerciseId = exerciseId
        self.exerciseName = exerciseName
        self.mobility = mobility
    }
}

public struct WorkoutWarmupChecklistCard: View {
    let exercise: Exercise
    let warmupSets: [WorkoutWarmupDisplaySet]
    let completedKeys: Set<String>
    let activeWarmupSetId: String?
    let onToggleSet: (String, Bool) -> Void
    let onClose: () -> Void
    
    public init(
        exercise: Exercise,
        warmupSets: [WorkoutWarmupDisplaySet],
        completedKeys: Set<String>,
        activeWarmupSetId: String?,
        onToggleSet: @escaping (String, Bool) -> Void,
        onClose: @escaping () -> Void
    ) {
        self.exercise = exercise
        self.warmupSets = warmupSets
        self.completedKeys = completedKeys
        self.activeWarmupSetId = activeWarmupSetId
        self.onToggleSet = onToggleSet
        self.onClose = onClose
    }
    
    private var displaySets: [WorkoutWarmupDisplaySet] {
        if !warmupSets.isEmpty {
            return warmupSets
        }
        return exercise.warmupSets.map { set in
            let percentage = Double(sanitizeWarmupPercentage(set.percentageOfWorkingWeight))
            return WorkoutWarmupDisplaySet(
                percentage: percentage,
                reps: sanitizeWarmupReps(set.targetReps, percentage: Int(percentage)),
                targetWeight: nil
            )
        }
    }
    
    private var allDone: Bool {
        !exercise.warmupSets.isEmpty && exercise.warmupSets.allSatisfy { set in
            completedKeys.contains(exercise.id) || completedKeys.contains("\(exercise.id)_warmup_\(set.id)")
        }
    }
    
    private var completedCount: Int {
        completedKeys.filter { key in
            key.hasPrefix("\(exercise.id)_warmup_") || key == exercise.id
        }.count
    }
    
    public var body: some View {
        VStack(spacing: 12) {
            // Header Row
            HStack(spacing: 10) {
                Image(systemName: "flame.fill")
                    .font(.system(size: 18))
                    .padding(6)
                    .background(Color(red: 68/255.0, green: 138/255.0, blue: 255/255.0).opacity(0.15))
                    .clipShape(Circle())
                    .foregroundColor(Color(red: 68/255.0, green: 138/255.0, blue: 255/255.0))
                
                VStack(alignment: .leading, spacing: 2) {
                    Text("Series de aproximación")
                        .font(.title3)
                        .fontWeight(.black)
                        .foregroundColor(.white)
                    Text(exercise.name)
                        .font(.caption)
                        .foregroundColor(.white.opacity(0.62))
                        .lineLimit(1)
                }
                
                Spacer()
                
                Text("\(min(completedCount, exercise.warmupSets.count))/\(exercise.warmupSets.count)")
                    .font(.caption2)
                    .fontWeight(.black)
                    .foregroundColor(allDone ? Color(red: 102/255.0, green: 187/255.0, blue: 106/255.0) : .white.opacity(0.72))
                    .padding(.horizontal, 9)
                    .padding(.vertical, 4)
                    .background(allDone ? Color(red: 102/255.0, green: 187/255.0, blue: 106/255.0).opacity(0.18) : Color.white.opacity(0.08))
                    .cornerRadius(10)
            }
            
            // Warmup checklist List
            VStack(spacing: 8) {
                ForEach(0..<exercise.warmupSets.count, id: \.self) { index in
                    let set = exercise.warmupSets[index]
                    let display = displaySets.indices.contains(index) ? displaySets[index] : nil
                    let completed = completedKeys.contains(exercise.id) || completedKeys.contains("\(exercise.id)_warmup_\(set.id)")
                    let active = activeWarmupSetId == set.id
                    
                    HStack(spacing: 10) {
                        Button(action: {
                            onToggleSet(set.id, !completed)
                        }) {
                            Image(systemName: completed ? "checkmark.square.fill" : "square")
                                .font(.system(size: 24))
                                .foregroundColor(completed ? Color(red: 68/255.0, green: 138/255.0, blue: 255/255.0) : Color.white.opacity(0.34))
                        }
                        .buttonStyle(.plain)
                        
                        VStack(alignment: .leading, spacing: 6) {
                            HStack {
                                Text("A\(index + 1)")
                                    .font(.body)
                                    .fontWeight(.black)
                                    .foregroundColor(.white)
                                
                                Spacer()
                                
                                if let rest = set.restBetween, rest > 0 {
                                    Text("\(rest)s descanso")
                                        .font(.caption2)
                                        .foregroundColor(.white.opacity(0.52))
                                        .fontWeight(.semibold)
                                }
                            }
                            
                            HStack(spacing: 6) {
                                WarmupMetricChip(text: "\((display?.percentage ?? set.percentageOfWorkingWeight).toTrimmedNumberString())%")
                                WarmupMetricChip(text: "\(display?.reps ?? set.targetReps) reps")
                                if let targetWeight = display?.targetWeight, targetWeight > 0.0 {
                                    WarmupMetricChip(text: "\(targetWeight.toTrimmedNumberString()) kg", emphasized: true)
                                }
                            }
                        }
                    }
                    .padding(.horizontal, 10)
                    .padding(.vertical, 8)
                    .frame(minHeight: 56)
                    .background(
                        active ? Color(red: 68/255.0, green: 138/255.0, blue: 255/255.0).opacity(0.16) :
                        (completed ? Color(red: 102/255.0, green: 187/255.0, blue: 106/255.0).opacity(0.10) : Color.white.opacity(0.045))
                    )
                    .cornerRadius(14)
                    .overlay(
                        RoundedRectangle(cornerRadius: 14)
                            .stroke(
                                active ? Color(red: 68/255.0, green: 138/255.0, blue: 255/255.0).opacity(0.60) :
                                (completed ? Color(red: 102/255.0, green: 187/255.0, blue: 106/255.0).opacity(0.35) : Color.white.opacity(0.08)),
                                lineWidth: active ? 1.5 : 1
                            )
                    )
                    .contentShape(Rectangle())
                    .onTapGesture {
                        onToggleSet(set.id, !completed)
                    }
                }
            }
            
            // Skip button
            Button(action: onClose) {
                Text(allDone ? "Continuar" : "Saltar por ahora")
                    .font(.body)
                    .fontWeight(.bold)
                    .foregroundColor(.white.opacity(0.70))
                    .frame(maxWidth: .infinity)
                    .frame(height: 46)
                    .overlay(RoundedRectangle(cornerRadius: 23).stroke(Color.white.opacity(0.14), lineWidth: 1))
            }
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 14)
        .background(Color(red: 22/255.0, green: 26/255.0, blue: 34/255.0))
        .cornerRadius(24)
        .overlay(RoundedRectangle(cornerRadius: 24).stroke(Color(red: 68/255.0, green: 138/255.0, blue: 255/255.0).opacity(0.24), lineWidth: 1))
    }
}

public struct WorkoutWarmupSheet: View {
    let exercise: Exercise
    let warmupSets: [WorkoutWarmupDisplaySet]
    let workingWeight: Double?
    let isCompleted: Bool
    let onDismiss: () -> Void
    let onMarkCompleted: () -> Void
    
    public init(
        exercise: Exercise,
        warmupSets: [WorkoutWarmupDisplaySet],
        workingWeight: Double?,
        isCompleted: Bool,
        onDismiss: @escaping () -> Void,
        onMarkCompleted: @escaping () -> Void
    ) {
        self.exercise = exercise
        self.warmupSets = warmupSets
        self.workingWeight = workingWeight
        self.isCompleted = isCompleted
        self.onDismiss = onDismiss
        self.onMarkCompleted = onMarkCompleted
    }
    
    private var displaySets: [WorkoutWarmupDisplaySet] {
        if !warmupSets.isEmpty {
            return warmupSets
        }
        return exercise.warmupSets.map { set in
            let percentage = Double(sanitizeWarmupPercentage(set.percentageOfWorkingWeight))
            let reps = sanitizeWarmupReps(set.targetReps, percentage: Int(percentage))
            let targetWeight = (workingWeight != nil && workingWeight! > 0.0) ? (workingWeight! * percentage / 100.0) : nil
            return WorkoutWarmupDisplaySet(percentage: percentage, reps: reps, targetWeight: targetWeight)
        }
    }
    
    public var body: some View {
        VStack(spacing: 12) {
            // Title
            HStack(spacing: 8) {
                Image(systemName: "flame.fill")
                    .font(.system(size: 24))
                    .foregroundColor(Color(red: 255/255.0, green: 179/255.0, blue: 0/255.0))
                Text("Warm-up inteligente")
                    .font(.title3)
                    .fontWeight(.black)
                    .foregroundColor(.white)
            }
            
            Text(exercise.name)
                .font(.body)
                .fontWeight(.bold)
                .foregroundColor(.white.opacity(0.7))
                .multilineTextAlignment(.center)
            
            if let workingWeight = workingWeight, workingWeight > 0.0 {
                Text("\(workingWeight.toTrimmedNumberString()) kg estimados para la serie efectiva")
                    .font(.caption)
                    .fontWeight(.bold)
                    .foregroundColor(Color(red: 255/255.0, green: 179/255.0, blue: 0/255.0))
                    .padding(.horizontal, 12)
                    .padding(.vertical, 6)
                    .background(Color(red: 255/255.0, green: 179/255.0, blue: 0/255.0).opacity(0.08))
                    .cornerRadius(10)
                    .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color(red: 255/255.0, green: 179/255.0, blue: 0/255.0).opacity(0.2), lineWidth: 1))
            }
            
            // Warmup Display List
            VStack(spacing: 8) {
                ForEach(0..<displaySets.count, id: \.self) { index in
                    let set = displaySets[index]
                    let rawWarmup = exercise.warmupSets.indices.contains(index) ? exercise.warmupSets[index] : nil
                    
                    VStack(alignment: .leading, spacing: 4) {
                        HStack {
                            Text("Aproximación \(index + 1)")
                                .font(.body)
                                .fontWeight(.bold)
                                .foregroundColor(.white)
                            
                            Spacer()
                            
                            let specs = [
                                "\(set.percentage.toTrimmedNumberString())%",
                                "\(set.reps) reps",
                                set.targetWeight.map { "\($0.toTrimmedNumberString()) kg" }
                            ].compactMap { $0 }
                            
                            Text(specs.joined(separator: " · "))
                                .font(.body)
                                .fontWeight(.black)
                                .foregroundColor(Color(red: 255/255.0, green: 179/255.0, blue: 0/255.0))
                        }
                        
                        if let rest = rawWarmup?.restBetween, rest > 0 {
                            Text("Descanso recomendado: \(rest)s")
                                .font(.caption2)
                                .foregroundColor(.blue)
                                .fontWeight(.semibold)
                        }
                    }
                    .padding(.horizontal, 14)
                    .padding(.vertical, 10)
                    .background(Color.white.opacity(0.03))
                    .cornerRadius(12)
                    .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color.white.opacity(0.05), lineWidth: 1))
                }
            }
            
            // Buttons
            HStack(spacing: 8) {
                Button(action: onDismiss) {
                    Text(isCompleted ? "Cerrar" : "Omitir")
                        .font(.body)
                        .fontWeight(.bold)
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .frame(height: 48)
                        .overlay(RoundedRectangle(cornerRadius: 24).stroke(Color.white.opacity(0.15), lineWidth: 1))
                }
                
                Button(action: onMarkCompleted) {
                    Text(isCompleted ? "Listo" : "Comenzar")
                        .font(.body)
                        .fontWeight(.black)
                        .foregroundColor(.black)
                        .frame(maxWidth: .infinity)
                        .frame(height: 48)
                        .background(Color(red: 255/255.0, green: 179/255.0, blue: 0/255.0))
                        .cornerRadius(24)
                }
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
    }
}

public struct WorkoutMobilitySeriesCard: View {
    let mobilityItems: [WorkoutMobilityChecklistItem]
    let completedExerciseIds: Set<String>
    let activeMobilityKey: String?
    let onToggleComplete: (String, String, Bool) -> Void
    let onClose: () -> Void
    
    public init(
        mobilityItems: [WorkoutMobilityChecklistItem],
        completedExerciseIds: Set<String>,
        activeMobilityKey: String?,
        onToggleComplete: @escaping (String, String, Bool) -> Void,
        onClose: @escaping () -> Void
    ) {
        self.mobilityItems = mobilityItems
        self.completedExerciseIds = completedExerciseIds
        self.activeMobilityKey = activeMobilityKey
        self.onToggleComplete = onToggleComplete
        self.onClose = onClose
    }
    
    private var allDone: Bool {
        !mobilityItems.isEmpty && mobilityItems.allSatisfy { item in
            completedExerciseIds.contains("\(item.exerciseId)_\(item.mobility.id)")
        }
    }
    
    public var body: some View {
        VStack(spacing: 12) {
            // Header Row
            HStack {
                HStack(spacing: 10) {
                    Image(systemName: "heart.text.square.fill")
                        .font(.system(size: 22))
                        .foregroundColor(Color(red: 102/255.0, green: 187/255.0, blue: 106/255.0))
                    
                    Text("Movilidad")
                        .font(.title3)
                        .fontWeight(.black)
                        .foregroundColor(.white)
                }
                Spacer()
            }
            
            // Mobility List
            VStack(spacing: 8) {
                ForEach(0..<mobilityItems.count, id: \.self) { idx in
                    let item = mobilityItems[idx]
                    let mob = item.mobility
                    let mobKey = "\(item.exerciseId)_\(mob.id)"
                    let isCompleted = completedExerciseIds.contains(mobKey)
                    let isActive = activeMobilityKey == mobKey
                    
                    HStack(spacing: 10) {
                        Button(action: {
                            onToggleComplete(item.exerciseId, mob.id, !isCompleted)
                        }) {
                            Image(systemName: isCompleted ? "checkmark.square.fill" : "square")
                                .font(.system(size: 24))
                                .foregroundColor(isCompleted ? Color(red: 102/255.0, green: 187/255.0, blue: 106/255.0) : Color.white.opacity(0.3))
                        }
                        .buttonStyle(.plain)
                        
                        VStack(alignment: .leading, spacing: 2) {
                            Text("M\(idx + 1) · \(mob.name)")
                                .font(.body)
                                .fontWeight(.bold)
                                .foregroundColor(.white)
                                .lineLimit(2)
                            
                            let detailText = buildDetailText(mob: mob)
                            if !detailText.isEmpty {
                                let exerciseDistinctCount = Set(mobilityItems.map { $0.exerciseId }).count
                                let titlePart = exerciseDistinctCount > 1 ? item.exerciseName : nil
                                let labelParts = [titlePart, detailText].compactMap { $0 }
                                
                                Text(labelParts.joined(separator: " · "))
                                    .font(.caption2)
                                    .foregroundColor(Color(red: 102/255.0, green: 187/255.0, blue: 106/255.0))
                                    .fontWeight(.semibold)
                            }
                        }
                        
                        Spacer()
                        
                        if let notes = mob.notes, !notes.isEmpty {
                            Text(notes)
                                .font(.caption2)
                                .fontWeight(.medium)
                                .foregroundColor(Color(red: 102/255.0, green: 187/255.0, blue: 106/255.0))
                                .padding(.horizontal, 8)
                                .padding(.vertical, 2)
                                .background(Color(red: 102/255.0, green: 187/255.0, blue: 106/255.0).opacity(0.15))
                                .cornerRadius(6)
                                .lineLimit(1)
                        }
                    }
                    .padding(.horizontal, 12)
                    .padding(.vertical, 8)
                    .frame(minHeight: 48)
                    .background(
                        isActive ? Color(red: 102/255.0, green: 187/255.0, blue: 106/255.0).opacity(0.16) :
                        (isCompleted ? Color(red: 102/255.0, green: 187/255.0, blue: 106/255.0).opacity(0.08) : Color.white.opacity(0.03))
                    )
                    .cornerRadius(12)
                    .overlay(
                        RoundedRectangle(cornerRadius: 12)
                            .stroke(
                                isActive ? Color(red: 102/255.0, green: 187/255.0, blue: 106/255.0).opacity(0.52) : Color.white.opacity(0.05),
                                lineWidth: isActive ? 1.5 : 1
                            )
                    )
                    .contentShape(Rectangle())
                    .onTapGesture {
                        onToggleComplete(item.exerciseId, mob.id, !isCompleted)
                    }
                }
            }
            
            // Footer Buttons
            HStack(spacing: 8) {
                Button(action: onClose) {
                    Text("Cerrar")
                        .font(.body)
                        .fontWeight(.bold)
                        .foregroundColor(.white.opacity(0.65))
                        .frame(maxWidth: .infinity)
                        .frame(height: 48)
                        .overlay(RoundedRectangle(cornerRadius: 24).stroke(Color.white.opacity(0.15), lineWidth: 1))
                }
                
                if !allDone {
                    Button(action: {
                        mobilityItems.forEach { item in
                            onToggleComplete(item.exerciseId, item.mobility.id, true)
                        }
                    }) {
                        Text("Completar todo")
                            .font(.body)
                            .fontWeight(.black)
                            .foregroundColor(.black)
                            .frame(maxWidth: .infinity)
                            .frame(height: 48)
                            .background(Color(red: 102/255.0, green: 187/255.0, blue: 106/255.0))
                            .cornerRadius(24)
                    }
                }
            }
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 14)
        .background(Color(red: 26/255.0, green: 26/255.0, blue: 26/255.0))
        .cornerRadius(24)
        .overlay(RoundedRectangle(cornerRadius: 24).stroke(Color.white.opacity(0.10), lineWidth: 1))
    }
    
    private func buildDetailText(mob: MobilitySeries) -> String {
        var parts: [String] = []
        if let dur = mob.durationSeconds {
            parts.append("\(dur)s")
        }
        if let reps = mob.reps {
            parts.append(reps)
        }
        return parts.joined(separator: " · ")
    }
}
