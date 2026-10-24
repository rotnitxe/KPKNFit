import SwiftUI

/// Swift translation of HomeScreen.kt
public struct HomeScreen: View {
    let onNavigateToSettings: () -> Void
    let onNavigateToProfile: () -> Void
    let onNavigateToProgram: (String) -> Void
    let onCreateProgram: () -> Void
    let onStartWorkout: (Session, Program) -> Void
    let onResumeWorkout: () -> Void
    let onEditSession: (Session, Program) -> Void
    let onNavigateToCard: (String) -> Void
    let onNavigate: (String) -> Void

    // ViewModels
    @State private var viewModel = HomeViewModel()
    @State private var showFoodLogger = false

    @State private var userName: String = ""
    @State private var greeting: String = ""
    @State private var todaySessions: [TodaySessionItem] = []
    @State private var hasActiveProgram: Bool = false
    @State private var activeProgramId: String? = nil
    @State private var competitionCountdown: CompetitionCountdown? = nil

    public init(
        onNavigateToSettings: @escaping () -> Void = {},
        onNavigateToProfile: @escaping () -> Void = {},
        onNavigateToProgram: @escaping (String) -> Void = { _ in },
        onCreateProgram: @escaping () -> Void = {},
        onStartWorkout: @escaping (Session, Program) -> Void = { _, _ in },
        onResumeWorkout: @escaping () -> Void = {},
        onEditSession: @escaping (Session, Program) -> Void = { _, _ in },
        onNavigateToCard: @escaping (String) -> Void = { _ in },
        onNavigate: @escaping (String) -> Void = { _ in }
    ) {
        self.onNavigateToSettings = onNavigateToSettings
        self.onNavigateToProfile = onNavigateToProfile
        self.onNavigateToProgram = onNavigateToProgram
        self.onCreateProgram = onCreateProgram
        self.onStartWorkout = onStartWorkout
        self.onResumeWorkout = onResumeWorkout
        self.onEditSession = onEditSession
        self.onNavigateToCard = onNavigateToCard
        self.onNavigate = onNavigate
    }

    public var body: some View {
        ZStack {
            // Absolute Black Base
            AppColors.bgDeepBlack.ignoresSafeArea()

            // Neon Orbs in Background
            GeometryReader { geo in
                NeonOrbView(color: AppColors.neonMagenta)
                    .frame(width: 300, height: 300)
                    .position(x: geo.size.width * 0.8, y: geo.size.height * 0.2)

                NeonOrbView(color: AppColors.neonCyan)
                    .frame(width: 250, height: 250)
                    .position(x: geo.size.width * 0.1, y: geo.size.height * 0.6)
            }
            .ignoresSafeArea()

            // Scrollable List
            ScrollView {
                VStack(spacing: 12) {
                    Spacer().frame(height: 70) // space for custom top bar

                    // 1. Header Section
                    HomeHeaderSection(greeting: greeting, userName: userName)

                    RecordedTrainingHistoryView(logs: ProgramRepository.shared.history)
                    // 4. Competition Countdown Card (if present)
                    if let countdown = competitionCountdown {
                        CompetitionCountdownCard(countdown: countdown, onClick: {
                            onNavigateToProgram(countdown.programId)
                        })
                    }

                    // 5. Home Session Section
                    HomeSessionSection(
                        sessions: todaySessions,
                        hasActiveProgram: hasActiveProgram,
                        currentDayOfWeek: Calendar.current.component(.weekday, from: Date()),
                        perMuscle: [:],
                        onStartWorkout: onStartWorkout,
                        onResumeWorkout: onResumeWorkout,
                        onEditSession: onEditSession
                    )

                    Spacer().frame(height: 8)

                    // 6. Home Cards Section
                    HomeCardsSection(
                        viewModel: viewModel,
                        onNavigateToCard: onNavigateToCard,
                        onAddMeal: { showFoodLogger = true }
                    )

                    Spacer().frame(height: 8)

                    // 7. Home Programs Section
                    HomeProgramsSection(
                        programs: [],
                        activeProgramId: activeProgramId,
                        onProgramClick: onNavigateToProgram,
                        onCreateProgram: onCreateProgram
                    )

                    Spacer().frame(height: 16)

                    // 8. Home WikiLab Section
                    HomeWikiLabSection(onNavigate: onNavigate)

                    Spacer().frame(height: 100)
                }
            }

            // Custom Top Bar overlay (Frosted Glass / Haze effect equivalent)
            VStack {
                HStack {
                    Button(action: onNavigateToProfile) {
                        Image(systemName: "person.crop.circle.fill")
                            .font(.system(size: 24))
                            .foregroundColor(.white)
                    }

                    Spacer()

                    Text("KPKN FIT")
                        .font(.system(.headline, design: .monospaced))
                        .fontWeight(.black)
                        .tracking(2)
                        .foregroundColor(.white)

                    Spacer()

                    Button(action: onNavigateToSettings) {
                        Image(systemName: "gearshape.fill")
                            .font(.system(size: 20))
                            .foregroundColor(.white)
                    }
                }
                .padding(.horizontal, 24)
                .padding(.vertical, 16)
                .background(Color.black.opacity(0.85))

                Spacer()
            }
        }
        .onAppear {
            loadMockData()
        }
    }

    private func loadMockData() {
        self.hasActiveProgram = true
        self.activeProgramId = "p1"

        let p = Program(id: "p1", name: "Hipertrofia Funcional", coverImage: "gradient://lagoon", mode: .HYPERTROPHY)
        let s = Session(id: "s1", name: "Push A - Fuerza", exercises: [
            Exercise(id: "e1", name: "Press Banca Plano", sets: [
                ExerciseSet(id: "s1", targetReps: 8, weight: 80.0),
                ExerciseSet(id: "s2", targetReps: 8, weight: 80.0)
            ]),
            Exercise(id: "e2", name: "Press Militar", sets: [
                ExerciseSet(id: "s3", targetReps: 10, weight: 50.0)
            ])
        ])

        self.todaySessions = [
            TodaySessionItem(
                session: s,
                program: p,
                location: SessionLocation(macroIndex: 0, mesoIndex: 0, weekId: "w1"),
                isCompleted: false,
                dayOfWeek: Calendar.current.component(.weekday, from: Date()),
                log: nil,
                isOngoing: false
            )
        ]

        self.competitionCountdown = CompetitionCountdown(
            programId: "p1",
            programName: "Hipertrofia Funcional",
            competitionDate: "2026-11-20",
            competitionDateLabel: "20 Nov",
            daysUntil: 126,
            countdownLabel: "126 días",
            competitionWeekLabel: "Semana 8"
        )
    }
}

// ─── Competition Countdown Card ──────────────────────────────────────────────

private struct CompetitionCountdownCard: View {
    let countdown: CompetitionCountdown
    let onClick: () -> Void

    var body: some View {
        Button(action: onClick) {
            HStack(spacing: 12) {
                // Days circle badge
                ZStack {
                    RoundedRectangle(cornerRadius: 16)
                        .fill(Color(hex: 0xF59E0B))
                        .frame(width: 58, height: 58)

                    VStack(spacing: 0) {
                        Text(countdown.countdownLabel.components(separatedBy: " ").first ?? "0")
                            .font(.system(size: 18, weight: .black))
                            .foregroundColor(.black)

                        Text("COMP")
                            .font(.system(size: 9, weight: .black))
                            .foregroundColor(.black.opacity(0.76))
                    }
                }

                VStack(alignment: .leading, spacing: 3) {
                    Text("Cuenta atrás de competición")
                        .font(.system(size: 13, weight: .black))
                        .foregroundColor(.white)

                    Text(countdown.programName)
                        .font(.system(size: 11, weight: .bold))
                        .foregroundColor(.gray)

                    Text("\(countdown.countdownLabel) · \(countdown.competitionDateLabel)")
                        .font(.system(size: 11, weight: .bold))
                        .foregroundColor(Color(hex: 0xF59E0B))

                    if let week = countdown.competitionWeekLabel {
                        Text("Semana reservada: \(week)")
                            .font(.system(size: 10))
                            .foregroundColor(.gray)
                    }
                }

                Spacer()

                Text("Ver")
                    .font(.system(size: 11, weight: .black))
                    .foregroundColor(Color(hex: 0xF59E0B))
            }
            .padding(14)
            .background(Color(hex: 0xF59E0B).opacity(0.14))
            .cornerRadius(18)
            .padding(.horizontal, 24)
        }
        .buttonStyle(PlainButtonStyle())
    }
}

// Mock structure matching Kotlin CompetitionCountdown
public struct CompetitionCountdown {
    public let programId: String
    public let programName: String
    public let competitionDate: String
    public let competitionDateLabel: String
    public let daysUntil: Int
    public let countdownLabel: String
    public let competitionWeekLabel: String?
}

#Preview {
    HomeScreen()
}


internal struct RecordedTrainingHistoryView: View {
    let logs: [WorkoutLog]
    @State private var weeks = 4
    @State private var sport = ""
    @State private var metric = ""
    @State private var selected: TrainingEvidenceFact?
    var body: some View {
        let nowMs = Int64(Date().timeIntervalSince1970 * 1000)
        let all = TrainingHistoryProjection.facts(logs, nowMs: nowMs)
        let visible = TrainingInsightsEngine.visible(all, nowMs: nowMs, weeks: weeks)
        let sports = Array(Set(visible.map(\.sport))).sorted()
        let filtered = visible.filter { sport.isEmpty || $0.sport == sport }
        let groups = Dictionary(grouping: filtered, by: { "\($0.sport)|\($0.metric)|\($0.unit)" })
        let keys = groups.keys.sorted()
        let key = keys.contains(metric) ? metric : (keys.first ?? "")
        let points = groups[key] ?? []
        VStack(alignment: .leading, spacing: 12) {
            Text("Entrenamiento y respuesta").font(.headline)
            Picker("Periodo", selection: $weeks) {
                ForEach([1,4,12], id: \.self) { Text("\($0) semanas").tag($0) }
            }.pickerStyle(.segmented)
            ScrollView(.horizontal) {
                HStack {
                    Button("Todas las disciplinas") { sport = "" }
                    ForEach(sports, id: \.self) { family in Button(family.replacingOccurrences(of:"_",with:" ")) { sport = family } }
                }
            }
            ScrollView(.horizontal) {
                HStack { ForEach(keys, id: \.self) { item in
                    if let first = groups[item]?.first { Button("\(recordedMetric(first.metric)) (\(recordedUnit(first.unit)))") { metric = item } }
                } }
            }
            if points.isEmpty { Text("Sin valores interpretables en este periodo. No significa descanso ni esfuerzo cero.") }
            else {
                let low = points.map(\.value).min() ?? 0
                let high = points.map(\.value).max() ?? low
                let start = points.map(\.occurredAtMs).min() ?? nowMs
                let end = points.map(\.occurredAtMs).max() ?? start
                Text("\(points.count) registros · \(recordedUnit(points[0].unit))").font(.caption)
                Text("\(low)–\(high) · \(Date(timeIntervalSince1970:Double(start)/1000).formatted(date:.abbreviated,time:.omitted))")
                    .font(.caption)
                GeometryReader { geometry in
                    ZStack {
                        ForEach(points) { point in
                            Button { selected = point } label: { Circle().fill(Color.accentColor).frame(width:16,height:16) }
                                .position(x: 12 + CGFloat(Double(point.occurredAtMs-start)/Double(max(1,end-start))) * max(1,geometry.size.width-24),
                                    y: 12 + CGFloat(1 - (point.value-low)/max(0.000001,high-low)) * max(1,geometry.size.height-24))
                                .accessibilityLabel("\(recordedMetric(point.metric)): \(point.value) \(recordedUnit(point.unit)); registro original")
                        }
                    }
                }.frame(height:160)
                ForEach(points) { point in
                    Button { selected = point } label: {
                        Text("\(Date(timeIntervalSince1970:Double(point.occurredAtMs)/1000).formatted(date:.abbreviated,time:.omitted)) · \(point.value) \(recordedUnit(point.unit))")
                    }
                }
            }
            Text("Toca un punto o su fila para consultar la fuente. El contexto desconocido no se considera comparable.").font(.caption)
            Text("Datos locales. El registro adicional es opcional; cualquier propuesta del programa requiere confirmación.").font(.caption)
        }.padding()
        .sheet(item:$selected) { point in
            let previous = TrainingInsightsEngine.previous(point,history:all,nowMs:nowMs)
            ScrollView { VStack(alignment:.leading,spacing:12) {
                Text(recordedMetric(point.metric)).font(.title2)
                Text("\(point.value) \(recordedUnit(point.unit))")
                Text("Fuente: \(point.sourceId) · sesión \(point.activityId)")
                Text("\(point.sport) · \(point.origin)")
                Text("Método: \(point.method ?? "no indicado") · protocolo: \(point.protocolId ?? "no indicado")")
                ForEach(point.context.keys.sorted(),id: \.self) { key in Text("\(key): \(point.context[key] ?? "")") }
                if let median = TrainingInsightsEngine.median(previous) { Text("Mediana de \(previous.count) anteriores comparables: \(median) \(recordedUnit(point.unit))") }
                else { Text("Sin tres observaciones anteriores comparables. No se estima recuperación ni seguridad.") }
                if let log = logs.first(where:{$0.id == point.activityId}) {
                    Text(log.sessionName); Text(log.date)
                    if let notes = log.notes { Text(notes) }
                    ForEach(log.discomforts,id: \.self) { Text("Molestia registrada: \($0)") }
                }
                Button("Cerrar") { selected = nil }
            }.padding() }
        }
    }
}

private func recordedUnit(_ value:String) -> String {
    ["KILOGRAM":"kg","KILOGRAMS":"kg","POUND":"lb","POUNDS":"lb","METER":"m","METERS":"m","CENTIMETER":"cm","SECOND":"s","SECONDS":"s","MINUTES":"min","MILLISECOND":"ms","BEATS_PER_MINUTE":"lat/min","WATTS":"W","METERS_PER_SECOND":"m/s","SECONDS_PER_KILOMETER":"s/km"][value] ?? value
}
private func recordedMetric(_ value:String) -> String {
    let key = String(value.split(separator:".").last ?? Substring(value))
    return ["actualWorkSeconds":"Tiempo de trabajo","actualWorkingSeconds":"Tiempo de trabajo","distanceMeters":"Distancia","elapsedMillis":"Tiempo transcurrido","movingMillis":"Tiempo en movimiento","elevationGainMeters":"Desnivel positivo","elevationLossMeters":"Desnivel negativo","attemptedCount":"Intentos","successfulCount":"Aciertos","repetitions":"Repeticiones","completedRounds":"Rondas","completedRepetitions":"Repeticiones completadas","holdSeconds":"Tiempo de sostén"][key] ?? (value.contains(".") ? "Valor registrado" : value)
}
