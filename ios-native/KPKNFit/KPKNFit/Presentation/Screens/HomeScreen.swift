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
    
    // Auge State mock
    @State private var muscularProgress: Double = 0.88
    @State private var sncProgress: Double = 0.82
    @State private var columnaProgress: Double = 0.65
    
    @State private var userName: String = "Carlos"
    @State private var greeting: String = "Buenos días"
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
                    
                    // 2. Rings Section
                    MyRingsView(
                        muscularProgress: muscularProgress,
                        cnsProgress: sncProgress,
                        spinalProgress: columnaProgress
                    )
                    .padding(.vertical, 8)
                    .liquidGlass(cornerRadius: 24, borderOpacity: 0.3)
                    .padding(.horizontal, 24)
                    
                    // 3. Auto-Deload Banner Mock (empty here by default)
                    
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
                        programs: [], // empty for mock setup
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
                .background(.ultraThinMaterial)
                
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
        
        let p = Program(id: "p1", name: "Hipertrofia Funcional", coverImage: "gradient://lagoon", mode: .HYPERTROPHY, totalProgramWeeks: 8)
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
