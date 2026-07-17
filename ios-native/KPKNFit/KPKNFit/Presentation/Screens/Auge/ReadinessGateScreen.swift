import SwiftUI

// ─── ReadinessGateScreen ──────────────────────────────────────────────────────

struct ReadinessGateScreen: View {
    let programId: String
    let sessionId: String
    let onReady: () -> Void

    @EnvironmentObject var augeVM: AugeViewModel
    @EnvironmentObject var programStore: ProgramStore

    @State private var neural: Int = 100
    @State private var muscular: Int = 100
    @State private var spinal: Int = 100
    @State private var muscleAdjustments: [String: Int] = [:]
    @State private var selectedDiscomforts: [String] = []
    @State private var userEditedNeural = false
    @State private var userEditedSpinal = false
    @State private var userEditedMuscles: [String: Bool] = [:]
    @State private var initialized = false
    @State private var allowSheetDismiss = false
    @State private var descriptionExpanded = false

    private var currentProgram: Program? {
        programStore.programs.first { $0.id == programId }
    }

    private var sessionName: String {
        guard let program = currentProgram else { return "Sesión" }
        for macro in program.macrocycles {
            for block in macro.blocks {
                for meso in block.mesocycles {
                    for week in meso.weeks {
                        if let s = week.sessions.first(where: { $0.id == sessionId }) {
                            return s.name
                        }
                    }
                }
            }
        }
        return "Sesión"
    }

    private var sessionExercises: [Exercise] {
        guard let program = currentProgram else { return [] }
        return findSessionExercises(program: program, sessionId: sessionId)
    }

    private var sessionMuscleBatteries: [String: Int] {
        var result: [String: Int] = [:]
        for ex in sessionExercises {
            let muscleId = ex.name
            if result[muscleId] == nil {
                result[muscleId] = augeVM.snapshot.perMuscle[muscleId]?.recoveryScore ?? 100
            }
        }
        return result
    }

    private var preparedWord: String {
        guard let gender = programStore.settings.userVitals.gender else { return "preparado(a)" }
        switch gender {
        case .FEMALE: return "preparada"
        case .MALE:   return "preparado"
        case .OTHER:  return "preparado(a)"
        }
    }

    var body: some View {
        ZStack {
            AppColors.bgDeepBlack.ignoresSafeArea()

            VStack(spacing: 14) {
                // Session preview header
                VStack(alignment: .leading, spacing: 6) {
                    Text(sessionName)
                        .font(.title3)
                        .fontWeight(.black)
                        .foregroundColor(.white)
                        .lineLimit(1)
                    Text("Vista previa de tu sesión")
                        .font(.subheadline)
                        .foregroundColor(AppColors.textSecondary)

                    let previewExercises = sessionExercises.prefix(6)
                    if previewExercises.isEmpty {
                        Text("No hay ejercicios cargados para esta sesión.")
                            .font(.subheadline)
                            .foregroundColor(AppColors.textSecondary)
                            .padding(14)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .background(Color.white.opacity(0.06))
                            .clipShape(RoundedRectangle(cornerRadius: 14))
                    } else {
                        ForEach(Array(previewExercises.enumerated()), id: \.offset) { index, exercise in
                            Text("\(index + 1). \(exercise.name)")
                                .font(.body)
                                .foregroundColor(.white)
                                .padding(.horizontal, 14)
                                .padding(.vertical, 12)
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .background(Color.white.opacity(0.05))
                                .clipShape(RoundedRectangle(cornerRadius: 14))
                        }
                    }
                }
                .padding(.horizontal, 16)
                .padding(.top, 14)

                Spacer()
            }

            // Bottom Sheet overlay
            VStack {
                Spacer()
                VStack(spacing: 10) {
                    // Drag handle
                    RoundedRectangle(cornerRadius: 2)
                        .fill(Color.white.opacity(0.3))
                        .frame(width: 40, height: 4)
                        .padding(.top, 8)

                    Text("Antes de empezar tu sesión de entrenamiento, responde lo siguiente:")
                        .font(.subheadline)
                        .fontWeight(.semibold)
                        .foregroundColor(AppColors.textSecondary)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 20)

                    Text("¿Qué tan \(preparedWord) te sientes?")
                        .font(.title2)
                        .fontWeight(.black)
                        .foregroundColor(.white)
                        .multilineTextAlignment(.center)

                    // Instructions toggle
                    Button(action: { withAnimation { descriptionExpanded.toggle() } }) {
                        HStack(spacing: 4) {
                            Text(descriptionExpanded ? "Ocultar instrucciones" : "Ver instrucciones")
                                .font(.caption)
                                .foregroundColor(Color(hex: 0x3B82F6))
                            Image(systemName: descriptionExpanded ? "chevron.up" : "chevron.down")
                                .font(.caption2)
                                .foregroundColor(Color(hex: 0x3B82F6))
                        }
                    }

                    if descriptionExpanded {
                        Text("De acuerdo al sistema de RINGS, este es tu estado a nivel de energía, columna y músculos involucrados para esta sesión. Si no te representan los porcentajes porque consideras que te sientes menos preparado o fresco para esta sesión, puedes cambiar libremente los porcentajes hasta que te identifiquen al 100%. Encima de cada RING, arrastra hacia arriba o abajo para cambiar el porcentaje, y para los músculos, desliza tu dedo hacia izquierda o derecha para ajustar.")
                            .font(.caption)
                            .foregroundColor(AppColors.textSecondary)
                            .multilineTextAlignment(.center)
                            .padding(.horizontal, 20)
                            .transition(.opacity.combined(with: .move(edge: .top)))
                    }

                    // Rings
                    HStack(spacing: 14) {
                        AdjustableRingCompact(
                            title: "Energía",
                            value: $neural,
                            ringColor: Color(hex: 0x448AFF),
                            onValueChange: { neural = $0; userEditedNeural = true }
                        )
                        .frame(maxWidth: .infinity)
                        AdjustableRingCompact(
                            title: "Columna",
                            value: $spinal,
                            ringColor: Color(hex: 0xFFD740),
                            onValueChange: { spinal = $0; userEditedSpinal = true }
                        )
                        .frame(maxWidth: .infinity)
                    }
                    .padding(.horizontal, 14)

                    // Muscle sliders
                    if !muscleAdjustments.isEmpty {
                        Text("Músculos de la sesión")
                            .font(.subheadline)
                            .fontWeight(.black)
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity, alignment: .leading)

                        LazyVGrid(
                            columns: [GridItem(.flexible()), GridItem(.flexible())],
                            spacing: 8
                        ) {
                            ForEach(muscleAdjustments.keys.sorted(), id: \.self) { muscleId in
                                let value = muscleAdjustments[muscleId] ?? 100
                                MinimalMuscleSlider(
                                    muscleLabel: muscleId,
                                    value: value,
                                    onValueChange: { updated in
                                        muscleAdjustments[muscleId] = updated
                                        userEditedMuscles[muscleId] = true
                                    }
                                )
                            }
                        }
                    }

                    // Discomfort selector
                    PreWorkoutDiscomfortSelector(
                        selectedDiscomforts: $selectedDiscomforts
                    )

                    // Save button
                    Button(action: {
                        let base = augeVM.todayWellbeing
                        let log = DailyWellbeingLog(
                            id: base?.id ?? UUID().uuidString,
                            date: Self.todayString(),
                            sleepQuality: base?.sleepQuality ?? 3,
                            stressLevel: base?.stressLevel ?? 3,
                            doms: base?.doms ?? 1,
                            motivation: base?.motivation ?? 3,
                            sleepHours: base?.sleepHours ?? 7.5,
                            moodState: base?.moodState,
                            workIntensity: base?.workIntensity,
                            studyIntensity: base?.studyIntensity,
                            manualMuscularBattery: muscular,
                            manualNeuralBattery: neural,
                            manualSpinalBattery: spinal,
                            manualMuscleBatteries: muscleAdjustments,
                            notes: base?.notes,
                            preWorkoutDiscomforts: selectedDiscomforts
                        )
                        augeVM.saveWellbeing(log)
                        WorkoutReadinessBridge.shared.store(.init(
                            neural: neural,
                            muscular: muscular,
                            spinal: spinal,
                            perMuscle: muscleAdjustments,
                            sleepQuality: base?.sleepQuality
                        ))
                        allowSheetDismiss = true
                        onReady()
                    }) {
                        Text("Guardar y entrenar")
                            .font(.subheadline)
                            .fontWeight(.black)
                            .foregroundColor(.white)
                            .padding(.vertical, 2)
                            .frame(maxWidth: .infinity)
                    }
                }
                .padding(.horizontal, 20)
                .padding(.bottom, 20)
                .background(Color(hex: 0x1E1E1E))
                .clipShape(RoundedRectangle(cornerRadius: 28, style: .continuous))
                .padding(.horizontal, 0)
            }
        }
        .onAppear { initializeState() }
        .onChange(of: augeVM.snapshot.isLoading) { _ in initializeState() }
    }

    private func initializeState() {
        guard !augeVM.snapshot.isLoading, !initialized else { return }
        let snap = augeVM.snapshot
        neural = snap.ringScore(id: .SYSTEM).clamped(to: 0...100)
        muscular = snap.ringScore(id: .MUSCULAR).clamped(to: 0...100)
        spinal = snap.ringScore(id: .STRUCTURE).clamped(to: 0...100)
        muscleAdjustments = sessionMuscleBatteries.mapValues { $0.clamped(to: 0...100) }
        selectedDiscomforts = augeVM.todayWellbeing?.preWorkoutDiscomforts ?? []
        initialized = true
    }

    static func todayString() -> String {
        let f = DateFormatter()
        f.dateFormat = "yyyy-MM-dd"
        return f.string(from: Date())
    }
}

// ─── AdjustableRingCompact ────────────────────────────────────────────────────

struct AdjustableRingCompact: View {
    let title: String
    @Binding var value: Int
    let ringColor: Color
    var onValueChange: ((Int) -> Void)?

    @State private var dragStartValue: Int = 0

    var body: some View {
        VStack(spacing: 2) {
            Text(title)
                .font(.caption)
                .fontWeight(.bold)
                .foregroundColor(AppColors.textSecondary)

            ZStack {
                ReadinessRingVisual(value: value, color: ringColor)
                    .frame(width: 132, height: 132)
                VStack(spacing: 0) {
                    Text("\(value)")
                        .font(.title3)
                        .fontWeight(.black)
                        .foregroundColor(ringColor)
                    Text("%")
                        .font(.caption2)
                        .foregroundColor(ringColor.opacity(0.7))
                }
            }
            .frame(width: 132, height: 132)
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { gesture in
                        if dragStartValue == 0 { dragStartValue = value }
                        let step = Int(-gesture.translation.height / 1.25)
                        let newValue = (dragStartValue + step).clamped(to: 0...100)
                        value = newValue
                        onValueChange?(newValue)
                    }
                    .onEnded { _ in dragStartValue = 0 }
            )

            Text("Arrastrar")
                .font(.system(size: 9))
                .foregroundColor(AppColors.textSecondary.opacity(0.5))
        }
    }
}

// ─── ReadinessRingVisual ──────────────────────────────────────────────────────

struct ReadinessRingVisual: View {
    let value: Int
    let color: Color

    @State private var animatedProgress: Double = 0

    var body: some View {
        ZStack {
            Circle()
                .stroke(color.opacity(0.12), lineWidth: 7)
            Circle()
                .trim(from: 0, to: animatedProgress)
                .stroke(color, style: StrokeStyle(lineWidth: 7, lineCap: .round))
                .rotationEffect(.degrees(-90))
                .animation(.easeInOut(duration: 0.4), value: animatedProgress)
        }
        .onAppear {
            animatedProgress = Double(value.clamped(to: 0...100)) / 100.0
        }
        .onChange(of: value) { newValue in
            animatedProgress = Double(newValue.clamped(to: 0...100)) / 100.0
        }
    }
}

// ─── MinimalMuscleSlider ──────────────────────────────────────────────────────

struct MinimalMuscleSlider: View {
    let muscleLabel: String
    let value: Int
    let onValueChange: (Int) -> Void

    private var clamped: Int { value.clamped(to: 0...100) }

    private var accent: Color {
        if clamped >= 80 { return Color(hex: 0x4ADE80) }
        else if clamped >= 55 { return Color.white.opacity(0.6) }
        else { return Color.white.opacity(0.3) }
    }

    var body: some View {
        VStack(spacing: 2) {
            HStack {
                Text(muscleLabel)
                    .font(.caption)
                    .foregroundColor(Color.white.opacity(0.65))
                Spacer()
                Text("\(clamped)%")
                    .font(.caption)
                    .fontWeight(.medium)
                    .foregroundColor(Color.white.opacity(0.55))
            }

            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    RoundedRectangle(cornerRadius: 2)
                        .fill(Color.white.opacity(0.1))
                        .frame(height: 4)
                    RoundedRectangle(cornerRadius: 2)
                        .fill(accent)
                        .frame(width: geo.size.width * CGFloat(clamped) / 100, height: 4)
                }
                .frame(height: 32)
                .contentShape(Rectangle())
                .gesture(
                    DragGesture(minimumDistance: 0)
                        .onChanged { gesture in
                            let newValue = ((gesture.location.x / geo.size.width) * 100)
                                .rounded()
                            onValueChange(Int(newValue).clamped(to: 0...100))
                        }
                )
            }
            .frame(height: 32)
        }
    }
}

// ─── PreWorkoutDiscomfortSelector ─────────────────────────────────────────────

struct PreWorkoutDiscomfortSelector: View {
    @Binding var selectedDiscomforts: [String]
    @State private var expanded = false

    var body: some View {
        VStack(spacing: 10) {
            Button(action: { withAnimation { expanded.toggle() } }) {
                HStack {
                    Image(systemName: "info.circle")
                        .font(.system(size: 18))
                        .foregroundColor(Color(hex: 0x3B82F6))
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Molestias previas al entrenamiento")
                            .font(.subheadline)
                            .fontWeight(.bold)
                            .foregroundColor(.white)
                        Text(selectedDiscomforts.isEmpty ? "Ninguna reportada" : "\(selectedDiscomforts.count) seleccionada(s)")
                            .font(.caption)
                            .foregroundColor(Color.white.opacity(0.5))
                    }
                    Spacer()
                    Image(systemName: expanded ? "chevron.up" : "chevron.down")
                        .font(.caption)
                        .foregroundColor(Color.white.opacity(0.6))
                }
            }

            if expanded {
                VStack(alignment: .leading, spacing: 12) {
                    let grouped = Dictionary(grouping: DISCOMFORT_CATALOG.filter { $0.id != "none" }, by: \.section)
                    ForEach(grouped.keys.sorted(by: { $0.rawValue < $1.rawValue }), id: \.self) { section in
                        if let items = grouped[section] {
                            Text(section.label)
                                .font(.caption)
                                .fontWeight(.bold)
                                .foregroundColor(Color(hex: 0x3B82F6).opacity(0.8))

                            FlowLayout(spacing: 6) {
                                ForEach(items, id: \.id) { item in
                                    let isSelected = selectedDiscomforts.contains(item.id)
                                    Button(action: {
                                        var list = selectedDiscomforts
                                        if isSelected {
                                            list.removeAll { $0 == item.id }
                                        } else {
                                            list.append(item.id)
                                        }
                                        selectedDiscomforts = list
                                    }) {
                                        Text(item.label)
                                            .font(.system(size: 11))
                                            .foregroundColor(isSelected ? Color(hex: 0x3B82F6) : Color.white.opacity(0.7))
                                            .padding(.horizontal, 10)
                                            .padding(.vertical, 6)
                                            .background(
                                                isSelected
                                                    ? Color(hex: 0x3B82F6).opacity(0.25)
                                                    : Color.clear
                                            )
                                            .overlay(
                                                Capsule()
                                                    .stroke(isSelected ? Color(hex: 0x3B82F6) : Color.white.opacity(0.15), lineWidth: 1)
                                            )
                                            .clipShape(Capsule())
                                    }
                                }
                            }
                        }
                    }
                }
                .transition(.opacity.combined(with: .move(edge: .top)))
            }
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.white.opacity(0.05))
        .clipShape(RoundedRectangle(cornerRadius: 16))
        .overlay(
            RoundedRectangle(cornerRadius: 16)
                .stroke(Color.white.opacity(0.1), lineWidth: 1)
        )
    }
}

// ─── FlowLayout (reuse) ───────────────────────────────────────────────────────

private struct FlowLayout: Layout {
    var spacing: CGFloat = 6

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let result = arrange(proposal: proposal, subviews: subviews)
        return result.size
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        let result = arrange(proposal: proposal, subviews: subviews)
        for (index, position) in result.positions.enumerated() {
            subviews[index].place(at: CGPoint(x: bounds.minX + position.x, y: bounds.minY + position.y), proposal: .unspecified)
        }
    }

    private func arrange(proposal: ProposedViewSize, subviews: Subviews) -> (positions: [CGPoint], size: CGSize) {
        let maxWidth = proposal.width ?? .infinity
        var positions: [CGPoint] = []
        var x: CGFloat = 0
        var y: CGFloat = 0
        var rowHeight: CGFloat = 0
        var totalHeight: CGFloat = 0

        for subview in subviews {
            let size = subview.sizeThatFits(.unspecified)
            if x + size.width > maxWidth && x > 0 {
                x = 0
                y += rowHeight + spacing
                rowHeight = 0
            }
            positions.append(CGPoint(x: x, y: y))
            rowHeight = max(rowHeight, size.height)
            x += size.width + spacing
            totalHeight = y + rowHeight
        }

        return (positions, CGSize(width: maxWidth, height: totalHeight))
    }
}
