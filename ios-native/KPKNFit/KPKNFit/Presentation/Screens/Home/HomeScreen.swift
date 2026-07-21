import SwiftUI

public struct HomeScreen: View {
    let themeMode: AppThemeMode
    let onThemeChange: (AppThemeMode) -> Void
    let onNavigateToSettings: () -> Void
    let onNavigateToProfile: () -> Void
    let onNavigateToProgram: (String) -> Void
    let onCreateProgram: () -> Void
    let onStartWorkout: (Session, Program) -> Void
    let onResumeWorkout: () -> Void
    let onEditSession: (Session, Program) -> Void
    let onNavigateToCard: (String) -> Void
    let onNavigate: (String) -> Void
    let viewModel: HomeViewModel
    let nutritionViewModel: NutritionViewModel?

    @StateObject private var augeViewModel = AugeViewModel.shared

    @State private var showFoodLogger = false
    @State private var selectedMealForLogger: MealType = .LUNCH
    @State private var scrollOffset: CGFloat = 0

    public init(
        themeMode: AppThemeMode = .system,
        onThemeChange: @escaping (AppThemeMode) -> Void = { _ in },
        onNavigateToSettings: @escaping () -> Void = {},
        onNavigateToProfile: @escaping () -> Void = {},
        onNavigateToProgram: @escaping (String) -> Void = { _ in },
        onCreateProgram: @escaping () -> Void = {},
        onStartWorkout: @escaping (Session, Program) -> Void = { _, _ in },
        onResumeWorkout: @escaping () -> Void = {},
        onEditSession: @escaping (Session, Program) -> Void = { _, _ in },
        onNavigateToCard: @escaping (String) -> Void = { _ in },
        onNavigate: @escaping (String) -> Void = { _ in },
        viewModel: HomeViewModel = HomeViewModel(),
        nutritionViewModel: NutritionViewModel? = nil
    ) {
        self.themeMode = themeMode
        self.onThemeChange = onThemeChange
        self.onNavigateToSettings = onNavigateToSettings
        self.onNavigateToProfile = onNavigateToProfile
        self.onNavigateToProgram = onNavigateToProgram
        self.onCreateProgram = onCreateProgram
        self.onStartWorkout = onStartWorkout
        self.onResumeWorkout = onResumeWorkout
        self.onEditSession = onEditSession
        self.onNavigateToCard = onNavigateToCard
        self.onNavigate = onNavigate
        self.viewModel = viewModel
        self.nutritionViewModel = nutritionViewModel
    }

    public var body: some View {
        let snapshot = augeViewModel.snapshot
        let muscularProgress = CGFloat(snapshot.ringScore(id: .MUSCULAR)) / 100.0
        let sncProgress = CGFloat(snapshot.ringScore(id: .SYSTEM)) / 100.0
        let columnaProgress = CGFloat(snapshot.ringScore(id: .STRUCTURE)) / 100.0
        let perMuscle = snapshot.perMuscle
        let autoDeloadMessage = snapshot.autoDeloadMessage
        let overtrainedMuscles = viewModel.overtrainedMuscles
        let greeting = viewModel.getGreeting()

        let topMarginItem0: CGFloat = 16
        let topMarginItem1: CGFloat = 4
        let topMarginItem2: CGFloat = 4
        let topMarginItem3: CGFloat = 8

        let greetingProgress = scrollProgress(offset: scrollOffset, itemIndex: 0, topMargin: topMarginItem0)
        let ringsProgress = scrollProgress(offset: scrollOffset, itemIndex: 1, topMargin: topMarginItem1)
        let sessionProgress = scrollProgress(offset: scrollOffset, itemIndex: 2, topMargin: topMarginItem2)
        let nutritionProgress = scrollProgress(offset: scrollOffset, itemIndex: 3, topMargin: topMarginItem3)

        ZStack {
            AppColors.bgDeepBlack.ignoresSafeArea()

            ScrollViewReader { proxy in
                ScrollView {
                    VStack(spacing: 12) {
                        Color.clear.frame(height: 70)
                            .id("spacer-top")

                        HomeHeaderSection(greeting: greeting, userName: viewModel.userName)
                            .id("item-0")

                        HomeRingsSection(
                            muscularProgress: muscularProgress,
                            sncProgress: sncProgress,
                            columnaProgress: columnaProgress,
                            hasActiveProgram: viewModel.hasActiveProgram
                        )
                        .id("item-1")

                        if let msg = autoDeloadMessage, !msg.isEmpty {
                            AutoDeloadBanner(message: msg)
                        }

                        if !overtrainedMuscles.isEmpty {
                            OvertrainedBanner(muscles: overtrainedMuscles)
                        }

                        if let countdown = viewModel.competitionCountdown {
                            CompetitionCountdownCard(
                                countdown: countdown,
                                onClick: { onNavigateToProgram(countdown.programId) }
                            )
                        }

                        HomeSessionSection(
                            sessions: viewModel.todaySessions,
                            hasActiveProgram: viewModel.hasActiveProgram,
                            currentDayOfWeek: currentDayOfWeek(),
                            perMuscle: perMuscle,
                            onStartWorkout: onStartWorkout,
                            onResumeWorkout: onResumeWorkout,
                            onEditSession: onEditSession
                        )
                        .id("item-2")

                        Spacer().frame(height: 8)

                        HomeCardsSection(
                            viewModel: viewModel,
                            onNavigateToCard: onNavigateToCard,
                            onAddMeal: { showFoodLogger = true }
                        )
                        .id("item-3")

                        Spacer().frame(height: 8)

                        HomeProgramsSection(
                            programs: viewModel.activeProgram.map { [$0] } ?? [],
                            activeProgramId: viewModel.activeProgram?.id,
                            onProgramClick: onNavigateToProgram,
                            onCreateProgram: onCreateProgram
                        )

                        Spacer().frame(height: 16)

                        HomeWikiLabSection(onNavigate: onNavigate)

                        Spacer().frame(height: 140)
                    }
                    .background(
                        GeometryReader { geo in
                            Color.clear.preference(
                                key: ScrollOffsetKey.self,
                                value: geo.frame(in: .global).minY
                            )
                        }
                    )
                }
                .onPreferenceChange(ScrollOffsetKey.self) { value in
                    scrollOffset = value
                }
            }

            HomeTopBar(
                themeMode: themeMode,
                onThemeChange: onThemeChange,
                greeting: greeting,
                userName: viewModel.userName,
                greetingProgress: greetingProgress,
                ringsProgress: ringsProgress,
                sessionProgress: sessionProgress,
                nutritionProgress: nutritionProgress,
                hasPrograms: viewModel.hasActiveProgram,
                muscularProgress: muscularProgress,
                sncProgress: sncProgress,
                columnaProgress: columnaProgress,
                todaySessions: viewModel.todaySessions,
                dailyCalorieGoal: viewModel.dailyCalorieGoal,
                consumedCalories: Int(viewModel.todayNutritionTotals.calories),
                onSettingsClick: onNavigateToSettings,
                onStartWorkout: onStartWorkout,
                onCreateProgram: onCreateProgram,
                onAddMeal: { showFoodLogger = true },
                onNavigateToProfile: onNavigateToProfile
            )

            if showFoodLogger {
                FoodLoggerDrawer(
                    isOpen: $showFoodLogger,
                    initialDate: todayDateString(),
                    initialMealType: selectedMealForLogger,
                    initialDescription: nil,
                    initialTab: 0
                )
            }
        }
        .onAppear {
            viewModel.loadFeedbacks()
        }
    }

    private func scrollProgress(offset: CGFloat, itemIndex: Int, topMargin: CGFloat) -> CGFloat {
        let itemOffset = -offset - CGFloat(itemIndex) * 120
        guard itemOffset > -topMargin else { return 0 }
        let cut = itemOffset + topMargin
        let visibleHeight: CGFloat = 120
        return min(cut / visibleHeight, 1.0)
    }

    private func currentDayOfWeek() -> Int {
        let weekday = Calendar.current.component(.weekday, from: Date())
        return weekday == 1 ? 7 : weekday - 1
    }

    private func todayDateString() -> String {
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withFullDate]
        return f.string(from: Date())
    }
}

// MARK: - Scroll Offset Preference Key

private struct ScrollOffsetKey: PreferenceKey {
    static var defaultValue: CGFloat = 0
    static func reduce(value: inout CGFloat, nextValue: () -> CGFloat) {
        value = nextValue()
    }
}

// MARK: - Auto-Deload Banner

private struct AutoDeloadBanner: View {
    let message: String

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("Auto-deload sugerido")
                .font(.system(size: 14, weight: .bold))
                .foregroundColor(.white)
            Text(message)
                .font(.system(size: 12))
                .foregroundColor(.white.opacity(0.85))
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 12)
        .padding(.vertical, 10)
        .background(Color.red.opacity(0.45))
        .cornerRadius(12)
        .padding(.horizontal, 16)
    }
}

// MARK: - Overtrained Banner

private struct OvertrainedBanner: View {
    let muscles: [String]

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("\u{26A0}\u{FE0F} Sobreentrenamiento Crónico Detectado")
                .font(.system(size: 14, weight: .bold))
                .foregroundColor(.white)
            Text("El coach detecta fatiga crítica acumulada en: \(muscles.joined(separator: ", ")). Considera reducir las series semanales o tomar un descanso activo.")
                .font(.system(size: 12))
                .foregroundColor(.white.opacity(0.85))
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .background(Color.red.opacity(0.65))
        .cornerRadius(12)
        .padding(.horizontal, 16)
    }
}

// MARK: - HomeTopBar

private struct HomeTopBar: View {
    let themeMode: AppThemeMode
    let onThemeChange: (AppThemeMode) -> Void
    let greeting: String
    let userName: String
    let greetingProgress: CGFloat
    let ringsProgress: CGFloat
    let sessionProgress: CGFloat
    let nutritionProgress: CGFloat
    let hasPrograms: Bool
    let muscularProgress: CGFloat
    let sncProgress: CGFloat
    let columnaProgress: CGFloat
    let todaySessions: [TodaySessionItem]
    let dailyCalorieGoal: Int
    let consumedCalories: Int
    let onSettingsClick: () -> Void
    let onStartWorkout: (Session, Program) -> Void
    let onCreateProgram: () -> Void
    let onAddMeal: () -> Void
    let onNavigateToProfile: () -> Void

    private let boxHeight: CGFloat = 48

    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 10) {
                Image("kpknicon")
                    .resizable()
                    .renderingMode(.template)
                    .frame(width: 43, height: 43)
                    .foregroundColor(.white)

                ZStack(alignment: .topLeading) {
                    Text("\(greeting), \(userName)!")
                        .font(.system(size: 16, weight: .black))
                        .foregroundColor(.white)
                        .lineLimit(1)
                        .opacity(greetingAlpha)
                        .offset(y: greetingSlide)

                    MiniRingsWidget(
                        muscularProgress: muscularProgress,
                        sncProgress: sncProgress,
                        columnaProgress: columnaProgress,
                        hasActiveProgram: hasPrograms
                    )
                    .opacity(ringsAlpha)
                    .offset(y: ringsSlide)

                    MiniSessionCard(
                        hasPrograms: hasPrograms,
                        todaySessions: todaySessions,
                        onStartWorkout: onStartWorkout,
                        onCreateProgram: onCreateProgram
                    )
                    .opacity(sessionAlpha)
                    .offset(y: sessionSlide)

                    MiniNutritionCard(
                        dailyCalorieGoal: dailyCalorieGoal,
                        consumedCalories: consumedCalories,
                        onAddMeal: onAddMeal
                    )
                    .opacity(nutritionAlpha)
                    .offset(y: nutritionSlide)
                }
                .frame(height: boxHeight)

                Spacer(minLength: 0)

                HStack(spacing: 0) {
                    Button(action: onNavigateToProfile) {
                        Image(systemName: "person.circle")
                            .font(.system(size: 22))
                            .foregroundColor(.white)
                    }
                    .frame(width: 44, height: 44)

                    Button(action: onSettingsClick) {
                        Image(systemName: "gearshape")
                            .font(.system(size: 22))
                            .foregroundColor(.white)
                    }
                    .frame(width: 44, height: 44)
                }
            }
            .padding(.horizontal, 16)
            .padding(.top, 4)
            .padding(.bottom, 4)
            .frame(height: 64)
        }
        .frame(maxWidth: .infinity)
        .background(Color.black.opacity(0.85))
        .cornerRadius(24, corners: [.bottomLeft, .bottomRight])
    }

    // Animation computations matching KT logic

    private var greetingAlpha: CGFloat {
        if nutritionProgress > 0 { return 0 }
        if sessionProgress > 0 { return 0 }
        if ringsProgress > 0 { return 1 - ringsProgress }
        return greetingProgress
    }

    private var greetingSlide: CGFloat {
        if nutritionProgress > 0 { return 0 }
        if sessionProgress > 0 { return 0 }
        if ringsProgress > 0 { return -ringsProgress * boxHeight }
        return (1 - greetingProgress) * boxHeight
    }

    private var ringsAlpha: CGFloat {
        if nutritionProgress > 0 { return 0 }
        if sessionProgress > 0 { return 1 - sessionProgress }
        if ringsProgress > 0 { return ringsProgress }
        return 0
    }

    private var ringsSlide: CGFloat {
        if nutritionProgress > 0 { return 0 }
        if sessionProgress > 0 { return -sessionProgress * boxHeight }
        if ringsProgress > 0 { return (1 - ringsProgress) * boxHeight }
        return 0
    }

    private var sessionAlpha: CGFloat {
        if nutritionProgress > 0 { return 1 - nutritionProgress }
        if sessionProgress > 0 { return sessionProgress }
        return 0
    }

    private var sessionSlide: CGFloat {
        if nutritionProgress > 0 { return -nutritionProgress * boxHeight }
        if sessionProgress > 0 { return (1 - sessionProgress) * boxHeight }
        return 0
    }

    private var nutritionAlpha: CGFloat {
        if nutritionProgress > 0 { return nutritionProgress }
        return 0
    }

    private var nutritionSlide: CGFloat {
        if nutritionProgress > 0 { return (1 - nutritionProgress) * boxHeight }
        return 0
    }
}

// MARK: - MiniRingsWidget

private struct MiniRingsWidget: View {
    let muscularProgress: CGFloat
    let sncProgress: CGFloat
    let columnaProgress: CGFloat
    let hasActiveProgram: Bool

    private let ringColors: [Color] = [
        Color(hex: 0xFF5252),
        Color(hex: 0x448AFF),
        Color(hex: 0xFFD740)
    ]

    private let ringColorsDimmed: [Color] = [
        Color(hex: 0x666666),
        Color(hex: 0x888888),
        Color(hex: 0xAAAAAA)
    ]

    var body: some View {
        let colors = hasActiveProgram ? ringColors : ringColorsDimmed
        let values = [muscularProgress, sncProgress, columnaProgress]

        GeometryReader { geo in
            let r = geo.size.height * 0.38
            let strokeW = r * 0.28
            let gap = r * 0.35
            let diameter = r * 2
            let spacing = diameter - gap
            let totalWidth = diameter + spacing * 2
            let startX = (geo.size.width - totalWidth) / 2 + r
            let cy = geo.size.height / 2

            ZStack(alignment: .topLeading) {
                ForEach(0..<3, id: \.self) { i in
                    let cx = startX + spacing * CGFloat(i)
                    let color = colors[i]
                    let progress = values[i]

                    ZStack {
                        Circle()
                            .stroke(color.opacity(0.15), lineWidth: strokeW)
                            .frame(width: diameter, height: diameter)

                        Circle()
                            .trim(from: 0, to: progress)
                            .stroke(color, style: StrokeStyle(lineWidth: strokeW, lineCap: .round))
                            .frame(width: diameter, height: diameter)
                            .rotationEffect(.degrees(-90))
                    }
                    .position(x: cx, y: cy)
                }
            }
        }
        .frame(maxWidth: .infinity)
        .frame(height: 38)
    }
}

// MARK: - MiniSessionCard

private struct MiniSessionCard: View {
    let hasPrograms: Bool
    let todaySessions: [TodaySessionItem]
    let onStartWorkout: (Session, Program) -> Void
    let onCreateProgram: () -> Void

    var body: some View {
        HStack {
            if !hasPrograms {
                Button(action: onCreateProgram) {
                    Text("Crear programa")
                        .font(.system(size: 12, weight: .bold))
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .frame(height: 36)
                        .background(Color.accentColor)
                        .cornerRadius(8)
                }
            } else if todaySessions.isEmpty {
                Text("Día de descanso")
                    .font(.system(size: 13, weight: .bold))
                    .foregroundColor(.white.opacity(0.7))
                    .lineLimit(1)
            } else {
                let session = todaySessions[0]
                Text(session.session.name)
                    .font(.system(size: 13, weight: .bold))
                    .lineLimit(1)
                    .foregroundColor(.white)

                Button(action: { onStartWorkout(session.session, session.program) }) {
                    Image(systemName: "play.fill")
                        .font(.system(size: 14))
                        .foregroundColor(.white)
                }
                .frame(width: 32, height: 32)
            }
        }
        .padding(.horizontal, 8)
        .frame(height: 44)
    }
}

// MARK: - MiniNutritionCard

private struct MiniNutritionCard: View {
    let dailyCalorieGoal: Int
    let consumedCalories: Int
    let onAddMeal: () -> Void

    private var pct: CGFloat {
        guard dailyCalorieGoal > 0 else { return 0 }
        return min(CGFloat(consumedCalories) / CGFloat(dailyCalorieGoal), 1.5)
    }

    private var progressColor: Color {
        if pct < 0.9 { return Color(hex: 0x22C55E) }
        if pct <= 1.1 { return Color.accentColor }
        return Color.red
    }

    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text("Calorías")
                    .font(.system(size: 11, weight: .bold))
                    .foregroundColor(.white.opacity(0.7))

                HStack(spacing: 2) {
                    Text("\(consumedCalories)")
                        .font(.system(size: 15, weight: .black))
                        .foregroundColor(.white)
                    Text("/ \(dailyCalorieGoal)")
                        .font(.system(size: 11, weight: .bold))
                        .foregroundColor(.white.opacity(0.6))
                }

                ProgressView(value: min(pct, 1.0))
                    .tint(progressColor)
                    .padding(.trailing, 8)
            }

            Button(action: onAddMeal) {
                Image(systemName: "plus")
                    .font(.system(size: 14))
                    .foregroundColor(.white)
            }
            .frame(width: 32, height: 32)
        }
        .padding(.horizontal, 8)
        .frame(height: 44)
    }
}

// MARK: - CompetitionCountdownCard

private struct CompetitionCountdownCard: View {
    let countdown: CompetitionCountdown
    let onClick: () -> Void

    var body: some View {
        Button(action: onClick) {
            HStack(spacing: 12) {
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
            .padding(.horizontal, 16)
        }
        .buttonStyle(PlainButtonStyle())
    }
}

// MARK: - Corner Radius Helper

private extension View {
    func cornerRadius(_ radius: CGFloat, corners: UIRectCorner) -> some View {
        clipShape(RoundedCorner(radius: radius, corners: corners))
    }
}

private struct RoundedCorner: Shape {
    var radius: CGFloat = .infinity
    var corners: UIRectCorner = .allCorners

    func path(in rect: CGRect) -> Path {
        let path = UIBezierPath(
            roundedRect: rect,
            byRoundingCorners: corners,
            cornerRadii: CGSize(width: radius, height: radius)
        )
        return Path(path.cgPath)
    }
}

// MARK: - FoodLoggerDrawer Placeholder

private struct FoodLoggerDrawer: View {
    @Binding var isOpen: Bool
    let initialDate: String
    let initialMealType: MealType
    let initialDescription: String?
    let initialTab: Int

    var body: some View {
        Color.black.opacity(0.4)
            .ignoresSafeArea()
            .onTapGesture { isOpen = false }

        VStack {
            Text("Registrar comida")
                .font(.headline)
                .foregroundColor(.white)
            Button("Cerrar") { isOpen = false }
                .foregroundColor(.white)
        }
        .padding()
        .background(Color.gray.opacity(0.9))
        .cornerRadius(16)
        .padding()
    }
}
