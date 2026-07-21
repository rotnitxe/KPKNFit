import SwiftUI

struct WorkoutReadinessSheet: View {
    let showReadinessSheet: Bool
    let gender: Gender?
    let sessionMuscleStartingBatteries: [String: Int]
    let readinessNeuralStart: Int
    let readinessMuscularStart: Int
    let readinessSpinalStart: Int
    let onSave: (Int, Int?, Int, [String: Int], [String]) -> Void
    let onDismissWithoutVerify: () -> Void
    let patternReadiness: [MovementPatternReadiness]
    let exerciseReadinessMap: [String: ExerciseReadiness]
    let sessionExercises: [Exercise]
    let initialDiscomforts: [String]

    @State private var neural: Int = 100
    @State private var muscular: Int = 100
    @State private var spinal: Int = 100
    @State private var muscleAdjustments: [String: Int] = [:]
    @State private var userEditedNeural: Bool = false
    @State private var userEditedSpinal: Bool = false
    @State private var userEditedMuscles: [String: Bool] = [:]
    @State private var initialized: Bool = false
    @State private var selectedDiscomforts: [String] = []
    @State private var isAdjustExpanded: Bool = false
    @State private var expandedEjercicios: Bool = true
    @State private var isInstructionsExpanded: Bool = false

    var body: some View {
        let _ = setupInitialState()
        let derivedMuscular = muscleAdjustments.isEmpty
            ? min(max(readinessMuscularStart, 0), 100)
            : min(max(Int(muscleAdjustments.values.map(Double.init).reduce(0, +) / Double(max(muscleAdjustments.count, 1))), 0), 100)

        ScrollView {
            VStack(spacing: 16) {
                Capsule()
                    .fill(Color.white.opacity(0.2))
                    .frame(width: 42, height: 4)

                Text("Reporta tu estado antes de entrenar")
                    .font(.system(size: 11, weight: .bold))
                    .foregroundColor(.blue)
                    .tracking(1.5)

                Text("¿Qué tan \(preparedWord) te sientes?")
                    .font(.system(size: 24, weight: .black))
                    .foregroundColor(.white)
                    .multilineTextAlignment(.center)

                HStack(spacing: 10) {
                    ReadinessSummaryCard(title: "Músculos", value: derivedMuscular, color: Color(red: 1, green: 0.32, blue: 0.32))
                    ReadinessSummaryCard(title: "Energía", value: neural, color: Color(red: 0.27, green: 0.53, blue: 1))
                    ReadinessSummaryCard(title: "Columna", value: spinal, color: Color(red: 1, green: 0.84, blue: 0.25))
                }

                Button(action: { isAdjustExpanded.toggle() }) {
                    HStack {
                        Image(systemName: "gearshape.fill")
                            .foregroundColor(.blue)
                        VStack(alignment: .leading) {
                            Text("Ajustar manualmente")
                                .font(.system(size: 14, weight: .bold))
                                .foregroundColor(.white)
                            Text("Modificar porcentajes del sistema RINGS")
                                .font(.system(size: 11))
                                .foregroundColor(.secondary)
                        }
                        Spacer()
                        Image(systemName: isAdjustExpanded ? "chevron.up" : "chevron.down")
                            .foregroundColor(.white)
                    }
                    .padding(16)
                    .background(Color(.systemGray5).opacity(0.15))
                    .clipShape(RoundedRectangle(cornerRadius: 16))
                }

                if isAdjustExpanded {
                    VStack(spacing: 16) {
                        HStack(spacing: 8) {
                            Image(systemName: "info.circle")
                                .foregroundColor(.blue)
                            Text("Arrastra verticalmente sobre los RINGS para modificar Energía (Neural) y Columna (Spinal). Desliza horizontalmente sobre las barras para ajustar la frescura de tus músculos.")
                                .font(.system(size: 12))
                                .foregroundColor(.secondary)
                        }
                        .padding(12)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(Color.blue.opacity(0.08))
                        .clipShape(RoundedRectangle(cornerRadius: 12))

                        HStack(spacing: 14) {
                            AdjustableRingCompactView(title: "Energía", value: $neural, ringColor: Color(red: 0.27, green: 0.53, blue: 1), ringSize: 120)
                            AdjustableRingCompactView(title: "Columna", value: $spinal, ringColor: Color(red: 1, green: 0.84, blue: 0.25), ringSize: 120)
                        }

                        if !muscleAdjustments.isEmpty {
                            Text("Frescura por Músculo")
                                .font(.system(size: 14, weight: .black))
                                .foregroundColor(.white)
                            LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 8) {
                                ForEach(Array(muscleAdjustments.keys.sorted()), id: \.self) { muscleId in
                                    let value = Binding<Int>(
                                        get: { muscleAdjustments[muscleId] ?? 100 },
                                        set: { muscleAdjustments[muscleId] = $0; userEditedMuscles[muscleId] = true }
                                    )
                                    MinimalMuscleSlider(muscleLabel: muscleId, value: value.wrappedValue, onValueChange: { value.wrappedValue = $0 })
                                }
                            }
                        }
                    }
                }

                if !exerciseReadinessMap.isEmpty {
                    VStack(spacing: 0) {
                        Button(action: { expandedEjercicios.toggle() }) {
                            HStack {
                                Image(systemName: "dumbbell.fill")
                                    .foregroundColor(.white.opacity(0.6))
                                Text("Preparación por ejercicio")
                                    .font(.system(size: 14, weight: .semibold))
                                Spacer()
                                Text("\(exerciseReadinessMap.count) ejercicios")
                                    .font(.system(size: 11))
                                    .foregroundColor(.white.opacity(0.5))
                                Image(systemName: expandedEjercicios ? "chevron.up" : "chevron.down")
                                    .foregroundColor(.white.opacity(0.5))
                            }
                            .padding(16)
                        }

                        if expandedEjercicios {
                            VStack(alignment: .leading, spacing: 8) {
                                // Movement patterns
                                let patternsByName = Dictionary(uniqueKeysWithValues: patternReadiness.map { ($0.patternLabel, $0) })
                                if !patternsByName.isEmpty {
                                    Text("Por patrón de movimiento")
                                        .font(.system(size: 11, weight: .semibold))
                                        .foregroundColor(.white.opacity(0.5))
                                    LazyVGrid(columns: [GridItem(.adaptive(minimum: 100))], spacing: 8) {
                                        ForEach(Array(patternsByName.values), id: \.patternLabel) { pattern in
                                            let color: Color = pattern.overallScore >= 75 ? Color(red: 0.30, green: 0.78, blue: 0.31) : pattern.overallScore >= 50 ? Color(red: 1, green: 0.76, blue: 0.03) : Color(red: 1, green: 0.32, blue: 0.32)
                                            HStack(spacing: 6) {
                                                ZStack {
                                                    Circle().fill(color).frame(width: 22, height: 22)
                                                    Text("\(pattern.overallScore)%")
                                                        .font(.system(size: 8, weight: .black))
                                                        .foregroundColor(.white)
                                                }
                                                Text(pattern.patternLabel)
                                                    .font(.system(size: 11))
                                            }
                                            .padding(.horizontal, 12)
                                            .padding(.vertical, 6)
                                            .background(color.opacity(0.12))
                                            .clipShape(Capsule())
                                        }
                                    }
                                }

                                Text("Por ejercicio")
                                    .font(.system(size: 11, weight: .semibold))
                                    .foregroundColor(.white.opacity(0.5))

                                ForEach(Array(exerciseReadinessMap.sorted(by: { $0.key < $1.key })), id: \.key) { (id, readiness) in
                                    let exercise = sessionExercises.first { $0.id == id }
                                    let color: Color = readiness.overallScore >= 75 ? Color(red: 0.30, green: 0.78, blue: 0.31) : readiness.overallScore >= 50 ? Color(red: 1, green: 0.76, blue: 0.03) : Color(red: 1, green: 0.32, blue: 0.32)
                                    HStack(spacing: 10) {
                                        Circle().fill(color).frame(width: 10, height: 10)
                                        Text(exercise?.name ?? id)
                                            .font(.system(size: 12))
                                        Spacer()
                                        Text("\(readiness.overallScore)%")
                                            .font(.system(size: 11, weight: .semibold))
                                            .foregroundColor(color)
                                    }
                                }
                            }
                            .padding(.horizontal, 16)
                            .padding(.bottom, 16)
                        }
                    }
                    .background(Color(.systemGray6))
                    .clipShape(RoundedRectangle(cornerRadius: 16))
                }

                PreWorkoutDiscomfortSelectorView(
                    selectedDiscomforts: $selectedDiscomforts
                )

                Button(action: {
                    onSave(neural, derivedMuscular, spinal, muscleAdjustments, selectedDiscomforts)
                }) {
                    HStack(spacing: 8) {
                        Image(systemName: "checkmark")
                        Text("Confirmar y Entrenar")
                            .fontWeight(.black)
                    }
                    .frame(maxWidth: .infinity, minHeight: 54)
                    .background(Color.blue)
                    .foregroundColor(.white)
                    .clipShape(Capsule())
                }

                Button(action: onDismissWithoutVerify) {
                    Text("Omitir y Entrenar")
                        .fontWeight(.bold)
                        .frame(maxWidth: .infinity, minHeight: 48)
                        .overlay(Capsule().stroke(Color.white.opacity(0.4)))
                }
                .tint(.white.opacity(0.7))
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 20)
        }
    }

    private func setupInitialState() {
        guard !initialized else { return }
        _neural = State(initialValue: readinessNeuralStart)
        _muscular = State(initialValue: readinessMuscularStart)
        _spinal = State(initialValue: readinessSpinalStart)
        muscleAdjustments = [:]
        sessionMuscleStartingBatteries.forEach { muscleAdjustments[$0.key] = min(max($0.value, 0), 100) }
        selectedDiscomforts = initialDiscomforts
        _initialized = State(initialValue: true)
    }

    private var preparedWord: String {
        switch gender {
        case .female: return "preparada"
        case .male: return "preparado"
        default: return "preparado(a)"
        }
    }
}

private struct ReadinessSummaryCard: View {
    let title: String
    let value: Int
    let color: Color

    var body: some View {
        VStack(spacing: 8) {
            Text(title)
                .font(.system(size: 11, weight: .bold))
                .foregroundColor(.secondary)
            ZStack {
                CircularProgressVisual(value: value, color: color, strokeWidth: 4)
                    .frame(width: 54, height: 54)
                Text("\(min(max(value, 0), 100))%")
                    .font(.system(size: 11, weight: .black))
                    .foregroundColor(color)
            }
        }
        .padding(12)
        .frame(maxWidth: .infinity)
        .background(Color(.systemGray5).opacity(0.1))
        .overlay(RoundedRectangle(cornerRadius: 16).stroke(color.opacity(0.2)))
        .clipShape(RoundedRectangle(cornerRadius: 16))
    }
}

struct CircularProgressVisual: View {
    let value: Int
    let color: Color
    let strokeWidth: CGFloat

    @State private var animatedValue: CGFloat = 0

    var body: some View {
        let target = CGFloat(min(max(value, 0), 100)) / 100.0
        Canvas { ctx, size in
            let sw = strokeWidth
            let radius = (min(size.width, size.height) - sw) / 2
            let center = CGPoint(x: size.width / 2, y: size.height / 2)

            ctx.stroke(
                Circle().path(in: CGRect(x: center.x - radius, y: center.y - radius, width: radius * 2, height: radius * 2)),
                with: .color(color.opacity(0.1)),
                lineWidth: sw
            )

            ctx.stroke(
                Path { path in
                    path.addArc(center: center, radius: radius, startAngle: .degrees(-90), endAngle: .degrees(-90 + 360 * Double(animatedValue)), clockwise: false)
                },
                with: .color(color),
                lineWidth: sw
            )
        }
        .onAppear {
            withAnimation(.easeOut(duration: 0.6)) {
                animatedValue = target
            }
        }
        .onChange(of: value) { newValue in
            withAnimation(.easeOut(duration: 0.6)) {
                animatedValue = CGFloat(min(max(newValue, 0), 100)) / 100.0
            }
        }
    }
}

struct AdjustableRingCompactView: View {
    let title: String
    @Binding var value: Int
    let ringColor: Color
    let ringSize: CGFloat

    @State private var dragAccumulator: CGFloat = 0
    @State private var dragStartValue: Int = 0

    var body: some View {
        VStack(spacing: 2) {
            Text(title)
                .font(.system(size: 11, weight: .bold))
                .foregroundColor(.secondary)

            ZStack {
                CircularProgressVisual(value: min(max(value, 0), 100), color: ringColor, strokeWidth: 5)
                VStack(spacing: 0) {
                    Text("\(min(max(value, 0), 100))")
                        .font(.system(size: 20, weight: .black))
                        .foregroundColor(ringColor)
                    Text("%")
                        .font(.system(size: 10))
                        .foregroundColor(ringColor.opacity(0.7))
                }
            }
            .frame(width: ringSize, height: ringSize)
            .gesture(
                DragGesture()
                    .onChanged { gesture in
                        if dragAccumulator == 0 { dragStartValue = value }
                        dragAccumulator += -gesture.translation.height
                        let step = Int(dragAccumulator / 1.25)
                        value = min(max(dragStartValue + step, 0), 100)
                    }
                    .onEnded { _ in
                        dragAccumulator = 0
                    }
            )

            Text("Arrastrar verticalmente")
                .font(.system(size: 8))
                .foregroundColor(.white.opacity(0.5))
        }
    }
}

struct PreWorkoutDiscomfortSelectorView: View {
    @Binding var selectedDiscomforts: [String]
    @State private var expanded: Bool = false

    var body: some View {
        VStack(spacing: 10) {
            Button(action: { expanded.toggle() }) {
                HStack(spacing: 8) {
                    Image(systemName: "info.circle")
                        .foregroundColor(.blue)
                    VStack(alignment: .leading) {
                        Text("Molestias previas al entrenamiento")
                            .font(.system(size: 14, weight: .bold))
                            .foregroundColor(.white)
                        Text(selectedDiscomforts.isEmpty ? "Ninguna reportada" : "\(selectedDiscomforts.count) seleccionada(s)")
                            .font(.system(size: 11))
                            .foregroundColor(.white.opacity(0.5))
                    }
                    Spacer()
                    Image(systemName: expanded ? "chevron.up" : "chevron.down")
                        .foregroundColor(.white.opacity(0.6))
                }
            }

            if expanded {
                let grouped = Dictionary(grouping: DISCOMFORT_CATALOG.filter { $0.id != "none" }) { $0.section }
                ForEach(Array(grouped.keys), id: \.id) { section in
                    Text(section.label)
                        .font(.system(size: 11, weight: .bold))
                        .foregroundColor(.blue.opacity(0.8))
                    LazyVGrid(columns: [GridItem(.adaptive(minimum: 80))], spacing: 6) {
                        ForEach(grouped[section] ?? []) { item in
                            let isSelected = selectedDiscomforts.contains(item.id)
                            Button(action: {
                                if isSelected {
                                    selectedDiscomforts.removeAll { $0 == item.id }
                                } else {
                                    selectedDiscomforts.append(item.id)
                                }
                            }) {
                                Text(item.label)
                                    .font(.system(size: 11))
                                    .foregroundColor(isSelected ? .blue : .white.opacity(0.7))
                                    .padding(.horizontal, 8)
                                    .padding(.vertical, 4)
                                    .background(isSelected ? Color.blue.opacity(0.25) : Color.clear)
                                    .overlay(RoundedRectangle(cornerRadius: 999).stroke(isSelected ? Color.blue : Color.white.opacity(0.15)))
                                    .clipShape(Capsule())
                            }
                        }
                    }
                }
            }
        }
        .padding(14)
        .background(Color.white.opacity(0.05))
        .overlay(RoundedRectangle(cornerRadius: 16).stroke(Color.white.opacity(0.1)))
        .clipShape(RoundedRectangle(cornerRadius: 16))
    }
}
