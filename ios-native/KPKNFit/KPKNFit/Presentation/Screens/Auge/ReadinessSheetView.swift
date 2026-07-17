import SwiftUI

// ─── ReadinessSheetView — Simplified version (without battery overrides) ──────

struct ReadinessSheetView: View {
    let readiness: AugeReadinessVerdict?
    let dashboard: RecoveryDashboard?
    let todayWellbeing: DailyWellbeingLog?
    let onDismiss: () -> Void
    let onSave: (DailyWellbeingLog) -> Void

    var body: some View {
        ReadinessSheetFullView(
            readiness: readiness,
            dashboard: dashboard,
            currentMuscularBattery: nil,
            currentNeuralBattery: nil,
            currentSpinalBattery: nil,
            muscleBatteries: [:],
            todayWellbeing: todayWellbeing,
            isLoading: false,
            onDismiss: onDismiss,
            onSave: { log, _, _, _, _ in onSave(log) }
        )
    }
}

// ─── ReadinessSheetFullView — Full version with battery overrides ─────────────

struct ReadinessSheetFullView: View {
    let readiness: AugeReadinessVerdict?
    let dashboard: RecoveryDashboard?
    let currentMuscularBattery: Int?
    let currentNeuralBattery: Int?
    let currentSpinalBattery: Int?
    let muscleBatteries: [String: Int]
    let todayWellbeing: DailyWellbeingLog?
    let isLoading: Bool
    let onDismiss: () -> Void
    let onSave: (DailyWellbeingLog, Int?, Int?, Int?, [String: Int]) -> Void

    @State private var sleepQuality: Int
    @State private var stressLevel: Int
    @State private var doms: Int
    @State private var motivation: Int
    @State private var userEditedNeural = false
    @State private var userEditedSpinal = false
    @State private var neural: Int
    @State private var spinal: Int
    @State private var muscleAdjustments: [String: Int] = [:]
    @State private var showDetails = false

    init(
        readiness: AugeReadinessVerdict?,
        dashboard: RecoveryDashboard? = nil,
        currentMuscularBattery: Int? = nil,
        currentNeuralBattery: Int? = nil,
        currentSpinalBattery: Int? = nil,
        muscleBatteries: [String: Int] = [:],
        todayWellbeing: DailyWellbeingLog? = nil,
        isLoading: Bool = false,
        onDismiss: @escaping () -> Void,
        onSave: @escaping (DailyWellbeingLog, Int?, Int?, Int?, [String: Int]) -> Void
    ) {
        self.readiness = readiness
        self.dashboard = dashboard
        self.currentMuscularBattery = currentMuscularBattery
        self.currentNeuralBattery = currentNeuralBattery
        self.currentSpinalBattery = currentSpinalBattery
        self.muscleBatteries = muscleBatteries
        self.todayWellbeing = todayWellbeing
        self.isLoading = isLoading
        self.onDismiss = onDismiss
        self.onSave = onSave

        let dashboardNeural = dashboard?.channels.first { $0.id == .SYSTEM }?.score
        let dashboardSpinal = dashboard?.channels.first { $0.id == .STRUCTURE }?.score

        _sleepQuality = State(initialValue: todayWellbeing?.sleepQuality ?? 3)
        _stressLevel = State(initialValue: todayWellbeing?.stressLevel ?? 3)
        _doms = State(initialValue: todayWellbeing?.doms ?? 1)
        _motivation = State(initialValue: todayWellbeing?.motivation ?? 3)
        _neural = State(initialValue: currentNeuralBattery ?? dashboardNeural ?? readiness?.score ?? 100)
        _spinal = State(initialValue: currentSpinalBattery ?? dashboardSpinal ?? 100)
        _muscleAdjustments = State(initialValue: muscleBatteries)
    }

    var dashboardNeural: Int? {
        dashboard?.channels.first { $0.id == .SYSTEM }?.score
    }

    var dashboardSpinal: Int? {
        dashboard?.channels.first { $0.id == .STRUCTURE }?.score
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 20) {
                // Header
                HStack {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("Mis RINGS")
                            .font(.title2)
                            .fontWeight(.black)
                        Text("Confirma cómo llegas hoy y corrige solo lo que no coincida.")
                            .font(.caption)
                            .foregroundColor(AppColors.textSecondary)
                    }
                    Spacer()
                    Button(action: onDismiss) {
                        Image(systemName: "xmark.circle.fill")
                            .font(.title2)
                            .foregroundColor(AppColors.textSecondary)
                    }
                }

                // Readiness score chip
                if let readiness = readiness {
                    let chipColor = readinessColor(readiness.color)
                    HStack(spacing: 8) {
                        Circle()
                            .fill(chipColor)
                            .frame(width: 10, height: 10)
                        Text("Estado actual: \(readiness.score)% — \(readiness.label)")
                            .font(.subheadline)
                            .fontWeight(.semibold)
                            .foregroundColor(chipColor)
                    }
                    .padding(12)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(chipColor.opacity(0.12))
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                }

                // Dashboard summary
                if let dashboard = dashboard {
                    VStack(alignment: .leading, spacing: 6) {
                        Text(dashboard.headline)
                            .font(.subheadline)
                            .fontWeight(.black)
                        Text(dashboard.summary)
                            .font(.caption)
                            .foregroundColor(AppColors.textSecondary)
                        if let action = readiness?.action, !action.isEmpty {
                            Text(action)
                                .font(.footnote)
                                .fontWeight(.semibold)
                        }
                        Text("Confianza actual: \(readiness?.confidenceLabel ?? dashboard.confidenceLabel)")
                            .font(.caption2)
                            .foregroundColor(AppColors.textSecondary)
                    }
                    .padding(12)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color.white.opacity(0.05))
                    .clipShape(RoundedRectangle(cornerRadius: 14))
                }

                // Advanced correction toggle
                Button(action: { withAnimation { showDetails.toggle() } }) {
                    HStack {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Corrección avanzada")
                                .font(.subheadline)
                                .fontWeight(.bold)
                                .foregroundColor(.white)
                            Text("Energía, columna y músculos implicados. Nunca porcentaje global.")
                                .font(.caption2)
                                .foregroundColor(AppColors.textSecondary)
                        }
                        Spacer()
                        Text(showDetails ? "Ocultar" : "Mostrar")
                            .font(.footnote)
                            .fontWeight(.bold)
                            .foregroundColor(Color(hex: 0x3B82F6))
                    }
                    .padding(.horizontal, 12)
                    .padding(.vertical, 10)
                }
                .background(Color.white.opacity(0.05))
                .clipShape(RoundedRectangle(cornerRadius: 14))

                if showDetails {
                    VStack(spacing: 10) {
                        BatteryAdjustRow(label: "Energía", calculated: currentNeuralBattery, current: neural) {
                            neural = $0; userEditedNeural = true
                        }
                        BatteryAdjustRow(label: "Columna", calculated: currentSpinalBattery, current: spinal) {
                            spinal = $0; userEditedSpinal = true
                        }
                    }

                    Text("El RING muscular global no se edita aquí. Si algo no cuadra, el ajuste es por músculo.")
                        .font(.caption2)
                        .foregroundColor(AppColors.textSecondary)

                    if let channels = dashboard?.channels, !channels.isEmpty {
                        VStack(alignment: .leading, spacing: 8) {
                            Text("Cómo interpretar los rings")
                                .font(.subheadline)
                                .fontWeight(.bold)
                                .foregroundColor(.white)
                            ForEach(channels, id: \.id) { channel in
                                Text("\(channel.title): \(channel.score)% · \(channel.action)")
                                    .font(.caption)
                                    .foregroundColor(AppColors.textSecondary)
                            }
                        }
                        .padding(12)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(Color.white.opacity(0.04))
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                    }

                    if !muscleAdjustments.isEmpty {
                        Text("Músculos principales de la sesión")
                            .font(.subheadline)
                            .fontWeight(.black)
                            .foregroundColor(.white)
                        ForEach(Array(muscleAdjustments.prefix(6).enumerated()), id: \.offset) { _, entry in
                            BatteryAdjustRow(
                                label: entry.key,
                                calculated: muscleBatteries[entry.key],
                                current: entry.value
                            ) { newValue in
                                muscleAdjustments[entry.key] = newValue
                            }
                        }
                    }
                }

                Divider().background(Color.white.opacity(0.15))

                // DotSelectors
                DotSelector(
                    label: "Calidad del sueño",
                    value: $sleepQuality,
                    max: 5,
                    lowLabel: "Muy mal",
                    highLabel: "Excelente",
                    invertColor: false
                )
                DotSelector(
                    label: "Nivel de estrés",
                    value: $stressLevel,
                    max: 5,
                    lowLabel: "Sin estrés",
                    highLabel: "Muy estresado",
                    invertColor: true
                )
                DotSelector(
                    label: "Agujetas / DOMS",
                    value: $doms,
                    max: 5,
                    lowLabel: "Sin agujetas",
                    highLabel: "Muy dolorido",
                    invertColor: true
                )
                DotSelector(
                    label: "Motivación",
                    value: $motivation,
                    max: 5,
                    lowLabel: "Sin ganas",
                    highLabel: "Muy motivado",
                    invertColor: false
                )

                Spacer(minLength: 4)

                // Save button
                Button(action: {
                    let muscleMap = muscleAdjustments
                    let hasManualSystem = userEditedNeural || userEditedSpinal
                    let values = Array(muscleMap.values)
                    let derivedMuscular: Int? = values.isEmpty ? nil : (values.reduce(0, +) / values.count).clamped(to: 0...100)
                    let log = DailyWellbeingLog(
                        id: todayWellbeing?.id ?? UUID().uuidString,
                        date: Self.todayString(),
                        sleepQuality: sleepQuality,
                        stressLevel: stressLevel,
                        doms: doms,
                        motivation: motivation,
                        sleepHours: todayWellbeing?.sleepHours ?? 7.5,
                        manualMuscularBattery: (hasManualSystem || !muscleMap.isEmpty) ? derivedMuscular : nil,
                        manualNeuralBattery: hasManualSystem ? neural.clamped(to: 0...100) : nil,
                        manualSpinalBattery: hasManualSystem ? spinal.clamped(to: 0...100) : nil,
                        manualMuscleBatteries: muscleMap.mapValues { $0.clamped(to: 0...100) }
                    )
                    onSave(log, neural, nil, spinal, muscleAdjustments)
                    onDismiss()
                }) {
                    Text("Guardar y entrenar")
                        .font(.subheadline)
                        .fontWeight(.black)
                        .foregroundColor(.white)
                        .padding(.vertical, 4)
                        .frame(maxWidth: .infinity)
                }
                .padding(.vertical, 8)

                Spacer(minLength: 8)
            }
            .padding(.horizontal, 24)
            .padding(.top, 16)
        }
        .background(AppColors.bgDeepBlack)
    }

    private func readinessColor(_ color: ReadinessColor) -> Color {
        switch color {
        case .GREEN:  return Color(hex: 0x22C55E)
        case .YELLOW: return Color(hex: 0xFACC15)
        case .RED:    return Color(hex: 0xEF4444)
        }
    }

    static func todayString() -> String {
        let f = DateFormatter()
        f.dateFormat = "yyyy-MM-dd"
        return f.string(from: Date())
    }
}

// ─── BatteryAdjustRow ─────────────────────────────────────────────────────────

private struct BatteryAdjustRow: View {
    let label: String
    let calculated: Int?
    let current: Int
    let onChange: (Int) -> Void

    var body: some View {
        VStack(spacing: 2) {
            HStack {
                Text(label)
                    .font(.subheadline)
                    .fontWeight(.semibold)
                    .foregroundColor(.white)
                Spacer()
                let calcText = calculated.map { "Calculado: \($0)" } ?? "Calculado: --"
                Text("\(calcText)  |  Tú: \(current)")
                    .font(.caption)
                    .foregroundColor(AppColors.textSecondary)
            }
            Slider(value: Binding(
                get: { Double(current) },
                set: { onChange(Int($0).clamped(to: 0...100)) }
            ), in: 0...100)
            .tint(Color(hex: 0x3B82F6))
        }
    }
}

// ─── DotSelector ──────────────────────────────────────────────────────────────

private struct DotSelector: View {
    let label: String
    @Binding var value: Int
    let max: Int
    let lowLabel: String
    let highLabel: String
    let invertColor: Bool

    var body: some View {
        VStack(spacing: 8) {
            Text(label)
                .font(.subheadline)
                .fontWeight(.semibold)
                .foregroundColor(.white)
            HStack(spacing: 8) {
                ForEach(1...max, id: \.self) { level in
                    let isSelected = level <= value
                    let dotColor = dotColorForLevel(level)
                    Button(action: { value = level }) {
                        Text("\(level)")
                            .font(.footnote)
                            .fontWeight(.black)
                            .foregroundColor(isSelected ? .white : AppColors.textSecondary)
                            .frame(width: 36, height: 36)
                            .background(isSelected ? dotColor : Color.white.opacity(0.1))
                            .clipShape(Circle())
                    }
                }
            }
            HStack {
                Text(lowLabel)
                    .font(.caption)
                    .foregroundColor(AppColors.textSecondary)
                Spacer()
                Text(highLabel)
                    .font(.caption)
                    .foregroundColor(AppColors.textSecondary)
            }
        }
    }

    private func dotColorForLevel(_ level: Int) -> Color {
        if invertColor {
            if level <= 2 { return Color(hex: 0x22C55E) }
            else if level <= 3 { return Color(hex: 0xFACC15) }
            else { return Color(hex: 0xEF4444) }
        } else {
            if level >= 4 { return Color(hex: 0x22C55E) }
            else if level >= 3 { return Color(hex: 0xFACC15) }
            else { return Color(hex: 0xEF4444) }
        }
    }
}

// ─── Helpers (clamped(to:) is defined in WorkoutModels.swift) ─────────────────
