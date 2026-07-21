import SwiftUI

struct ReadinessGateScreen: View {
    let programId: String
    let sessionId: String
    let onReady: () -> Void

    @State private var neural: Int = 100
    @State private var muscular: Int = 100
    @State private var spinal: Int = 100
    @State private var muscleAdjustments: [String: Int] = [:]
    @State private var selectedDiscomforts: [String] = []
    @State private var initialized: Bool = false
    @State private var userEditedNeural: Bool = false
    @State private var userEditedSpinal: Bool = false
    @State private var userEditedMuscles: [String: Bool] = [:]
    @State private var isInstructionsExpanded: Bool = false

    // Simulated data sources - in real app these would come from repositories
    let sessionName: String = "Sesión"
    let sessionExercises: [Exercise] = []
    let sessionMuscleIds: [String] = []
    let sessionMuscleBatteries: [String: Int] = [:]
    let neuralAuto: Int = 100
    let muscularAuto: Int = 100
    let spinalAuto: Int = 100
    let todayWellbeing: DailyWellbeingLog? = nil
    let gender: Gender? = nil

    var body: some View {
        let _ = setupState()
        let preparedWord: String = {
            switch gender {
            case .female: return "preparada"
            case .male: return "preparado"
            default: return "preparado(a)"
            }
        }()

        ZStack {
            Color(.systemBackground).ignoresSafeArea()

            VStack(spacing: 10) {
                Text(sessionName)
                    .font(.system(size: 28, weight: .black))
                    .lineLimit(1)

                Text("Vista previa de tu sesión")
                    .font(.system(size: 14))
                    .foregroundColor(.secondary)

                let previewExercises = sessionExercises.prefix(6)
                if previewExercises.isEmpty {
                    Text("No hay ejercicios cargados para esta sesión.")
                        .font(.subheadline)
                        .foregroundColor(.secondary)
                        .padding(14)
                        .frame(maxWidth: .infinity)
                        .background(Color(.systemGray5).opacity(0.35))
                        .clipShape(RoundedRectangle(cornerRadius: 14))
                } else {
                    ForEach(Array(previewExercises.enumerated()), id: \.offset) { index, exercise in
                        Text("\(index + 1). \(exercise.name)")
                            .font(.system(size: 14))
                            .padding(.horizontal, 14)
                            .padding(.vertical, 12)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .background(Color(.systemGray5).opacity(0.3))
                            .clipShape(RoundedRectangle(cornerRadius: 14))
                    }
                }

                Spacer()
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 14)

            VStack(spacing: 10) {
                Text("Antes de empezar tu sesión de entrenamiento, responde lo siguiente:")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundColor(.secondary)

                Text("¿Qué tan \(preparedWord) te sientes?")
                    .font(.system(size: 22, weight: .black))
                    .foregroundColor(.white)
                    .multilineTextAlignment(.center)

                Button(action: { isInstructionsExpanded.toggle() }) {
                    HStack {
                        Text(isInstructionsExpanded ? "Ocultar instrucciones" : "Ver instrucciones")
                            .font(.system(size: 11))
                            .foregroundColor(.blue)
                        Image(systemName: isInstructionsExpanded ? "chevron.up" : "chevron.down")
                            .foregroundColor(.blue)
                    }
                }

                if isInstructionsExpanded {
                    Text("De acuerdo al sistema de RINGS, este es tu estado a nivel de energía, columna y músculos involucrados para esta sesión. Si no te representan los porcentajes porque consideras que te sientes menos preparado o fresco para esta sesión, puedes cambiar libremente los porcentajes hasta que te identifiquen al 100%. Encima de cada RING, arrastra hacia arriba o abajo para cambiar el porcentaje, y para los músculos, desliza tu dedo hacia izquierda o derecha para ajustar.")
                        .font(.system(size: 12))
                        .foregroundColor(.secondary)
                        .multilineTextAlignment(.leading)
                }

                HStack(spacing: 14) {
                    AdjustableRingCompactView(
                        title: "Energía",
                        value: Binding(
                            get: { neural },
                            set: { neural = $0; userEditedNeural = true }
                        ),
                        ringColor: Color(red: 0.27, green: 0.53, blue: 1),
                        ringSize: 132
                    )
                    AdjustableRingCompactView(
                        title: "Columna",
                        value: Binding(
                            get: { spinal },
                            set: { spinal = $0; userEditedSpinal = true }
                        ),
                        ringColor: Color(red: 1, green: 0.84, blue: 0.25),
                        ringSize: 132
                    )
                }

                if !muscleAdjustments.isEmpty {
                    Text("Músculos de la sesión")
                        .font(.system(size: 14, weight: .black))
                        .foregroundColor(.white)

                    LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 8) {
                        ForEach(Array(muscleAdjustments.keys.sorted()), id: \.self) { muscleId in
                            MinimalMuscleSlider(
                                muscleLabel: muscleId,
                                value: muscleAdjustments[muscleId] ?? 100,
                                onValueChange: { muscleAdjustments[muscleId] = $0; userEditedMuscles[muscleId] = true }
                            )
                        }
                    }
                }

                PreWorkoutDiscomfortSelectorView(selectedDiscomforts: $selectedDiscomforts)

                Button(action: {
                    let log = DailyWellbeingLog(
                        id: todayWellbeing?.id ?? UUID().uuidString,
                        date: ISO8601DateFormatter().string(from: Date()),
                        sleepQuality: todayWellbeing?.sleepQuality ?? 3,
                        stressLevel: todayWellbeing?.stressLevel ?? 3,
                        doms: todayWellbeing?.doms ?? 1,
                        motivation: todayWellbeing?.motivation ?? 3,
                        sleepHours: todayWellbeing?.sleepHours ?? 7.5,
                        moodState: todayWellbeing?.moodState,
                        workIntensity: todayWellbeing?.workIntensity,
                        studyIntensity: todayWellbeing?.studyIntensity,
                        manualMuscularBattery: muscular,
                        manualNeuralBattery: neural,
                        manualSpinalBattery: spinal,
                        manualMuscleBatteries: muscleAdjustments,
                        notes: todayWellbeing?.notes,
                        preWorkoutDiscomforts: selectedDiscomforts
                    )
                    // Save wellbeing and readiness bridge would go here
                    onReady()
                }) {
                    Text("Guardar y entrenar")
                        .font(.system(size: 14, weight: .black))
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                        .background(Color.blue)
                        .foregroundColor(.white)
                        .clipShape(RoundedRectangle(cornerRadius: 16))
                }
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 10)
        }
    }

    private func setupState() {
        guard !initialized else { return }
        neural = min(max(neuralAuto, 0), 100)
        muscular = min(max(muscularAuto, 0), 100)
        spinal = min(max(spinalAuto, 0), 100)
        muscleAdjustments = [:]
        sessionMuscleBatteries.forEach { muscleAdjustments[$0.key] = min(max($0.value, 0), 100) }
        selectedDiscomforts = todayWellbeing?.preWorkoutDiscomforts ?? []
        initialized = true
    }
}

extension DailyWellbeingLog {
    init(id: String, date: String, sleepQuality: Int, stressLevel: Int, doms: Int, motivation: Int,
         sleepHours: Double, moodState: String?, workIntensity: Int?, studyIntensity: Int?,
         manualMuscularBattery: Int, manualNeuralBattery: Int, manualSpinalBattery: Int,
         manualMuscleBatteries: [String: Int], notes: String?, preWorkoutDiscomforts: [String]) {
        self.id = id
        self.date = date
        self.sleepQuality = sleepQuality
        self.stressLevel = stressLevel
        self.doms = doms
        self.motivation = motivation
        self.sleepHours = sleepHours
        self.moodState = moodState
        self.workIntensity = workIntensity
        self.studyIntensity = studyIntensity
        self.manualMuscularBattery = manualMuscularBattery
        self.manualNeuralBattery = manualNeuralBattery
        self.manualSpinalBattery = manualSpinalBattery
        self.manualMuscleBatteries = manualMuscleBatteries
        self.notes = notes
        self.preWorkoutDiscomforts = preWorkoutDiscomforts
    }
}

private func findSessionExercises(program: Program, sessionId: String) -> [Exercise] {
    for macro in program.macrocycles {
        for block in macro.blocks {
            for meso in block.mesocycles {
                for week in meso.weeks {
                    if let session = week.sessions.first(where: { $0.id == sessionId }) {
                        return session.parts.isEmpty ? session.exercises : session.parts.flatMap { $0.exercises }
                    }
                }
            }
        }
    }
    return []
}
