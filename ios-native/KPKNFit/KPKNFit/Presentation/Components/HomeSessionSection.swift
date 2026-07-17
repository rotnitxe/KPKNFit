import SwiftUI

/// Swift translation of HomeSessionSection.kt
public struct HomeSessionSection: View {
    let sessions: [TodaySessionItem]
    let hasActiveProgram: Bool
    let currentDayOfWeek: Int
    let perMuscle: [String: MuscleRecoveryStatus]
    let onStartWorkout: (Session, Program) -> Void
    let onResumeWorkout: () -> Void
    let onEditSession: (Session, Program) -> Void
    
    @State private var activeIndex: Int = 0
    
    public init(
        sessions: [TodaySessionItem],
        hasActiveProgram: Bool = true,
        currentDayOfWeek: Int,
        perMuscle: [String: MuscleRecoveryStatus] = [:],
        onStartWorkout: @escaping (Session, Program) -> Void,
        onResumeWorkout: @escaping () -> Void,
        onEditSession: @escaping (Session, Program) -> Void = { _, _ in }
    ) {
        self.sessions = sessions
        self.hasActiveProgram = hasActiveProgram
        self.currentDayOfWeek = currentDayOfWeek
        self.perMuscle = perMuscle
        self.onStartWorkout = onStartWorkout
        self.onResumeWorkout = onResumeWorkout
        self.onEditSession = onEditSession
    }
    
    public var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            // Determine Title
            let current = sessions.indices.contains(activeIndex) ? sessions[activeIndex] : sessions.first
            let isCurrentToday = current.map { item in
                // Standard day of week check
                item.dayOfWeek == currentDayOfWeek
            } ?? false
            let isCurrentCompleted = current?.isCompleted == true
            let headerTitle = (isCurrentToday && !isCurrentCompleted) ? "Sesión de hoy" : "Próxima sesión"
            
            SectionHeader(headerTitle)
                .padding(.horizontal, 24)
            
            if !hasActiveProgram {
                NoProgramSessionCard()
                    .padding(.horizontal, 24)
            } else if sessions.isEmpty {
                RestDayCard()
                    .padding(.horizontal, 24)
            } else {
                let current = sessions.indices.contains(activeIndex) ? sessions[activeIndex] : sessions[0]
                
                SessionCard(
                    item: current,
                    currentDayOfWeek: currentDayOfWeek,
                    perMuscle: perMuscle,
                    onStart: { onStartWorkout(current.session, current.program) },
                    onResume: onResumeWorkout,
                    onEdit: { onEditSession(current.session, current.program) }
                )
                .padding(.horizontal, 24)
                
                SessionCarousel(
                    sessions: sessions,
                    activeIndex: $activeIndex,
                    onIndexChange: { activeIndex = $0 }
                )
                .padding(.top, 16)
            }
        }
        .onChange(of: sessions.count) {
            activeIndex = 0
        }
    }
}

// ─── Session Card ────────────────────────────────────────────────────────────

private struct SessionCard: View {
    let item: TodaySessionItem
    let currentDayOfWeek: Int
    let perMuscle: [String: MuscleRecoveryStatus]
    let onStart: () -> Void
    let onResume: () -> Void
    let onEdit: () -> Void
    
    @State private var musclesExpanded = false
    
    var body: some View {
        let isToday = item.dayOfWeek == currentDayOfWeek
        let sessionMuscles = getSessionInvolvedMuscles(session: item.session)
        let durationDisplay = getSessionDurationDisplay(session: item.session, log: item.log)
        
        VStack(spacing: 0) {
            // Top Section (Aspect Ratio equivalent / Card visual)
            ZStack {
                // Gradient Background (Yellow -> Magenta)
                LinearGradient(
                    colors: [
                        AppColors.neonYellow,
                        AppColors.neonMagenta
                    ],
                    startPoint: .topLeading,
                    endPoint: .bottomTrailing
                )
                
                // Dark overlay (0.3)
                Color.black.opacity(0.3)
                
                // Top Tag
                VStack(alignment: .leading, spacing: 0) {
                    HStack {
                        Text(isToday && !item.isCompleted ? "Sesión de hoy" : "Próxima sesión")
                            .font(.system(size: 10, weight: .black))
                            .foregroundColor(.white)
                            .padding(.horizontal, 10)
                            .padding(.vertical, 4)
                            .background(Color.white.opacity(0.15))
                            .cornerRadius(999)
                        
                        Spacer()
                    }
                    
                    Spacer()
                    
                    // Bottom Details
                    HStack(alignment: .bottom) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text(item.program.name)
                                .font(.system(size: 10, weight: .black))
                                .foregroundColor(Color.white.opacity(0.6))
                                .tracking(1.5)
                            
                            Text(item.session.name)
                                .font(.system(size: 18, weight: .black))
                                .foregroundColor(.white)
                                .lineLimit(2)
                            
                            HStack(spacing: 4) {
                                Image(systemName: "clock")
                                    .font(.system(size: 12))
                                    .foregroundColor(Color.white.opacity(0.7))
                                
                                Text(durationDisplay)
                                    .font(.system(size: 10, weight: .medium))
                                    .foregroundColor(Color.white.opacity(0.7))
                            }
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        
                        // Action Buttons
                        HStack(spacing: 8) {
                            Button(action: onEdit) {
                                Image(systemName: "pencil")
                                    .font(.system(size: 18))
                                    .foregroundColor(.white)
                                    .frame(width: 36, height: 36)
                                    .background(Color.white.opacity(0.20))
                                    .clipShape(Circle())
                            }
                            .buttonStyle(PlainButtonStyle())
                            
                            Button(action: {
                                if item.isOngoing { onResume() }
                                else { onStart() }
                            }) {
                                Image(systemName: item.isCompleted ? "checkmark" : "play.fill")
                                    .font(.system(size: 24))
                                    .foregroundColor(.black)
                                    .frame(width: 48, height: 48)
                                    .background(item.isCompleted ? Color(hex: 0x66BB6A) : Color.white)
                                    .clipShape(Circle())
                            }
                            .buttonStyle(PlainButtonStyle())
                        }
                    }
                }
                .padding(12)
            }
            .frame(height: 120) // Aspect ratio approximate 16:5.5
            .clipped()
            
            // Involved Muscles Collapsible Header
            if !sessionMuscles.isEmpty {
                Button(action: {
                    withAnimation(.easeInOut) {
                        musclesExpanded.toggle()
                    }
                }) {
                    HStack {
                        Text("Músculos involucrados")
                            .font(.system(size: 10, weight: .bold))
                            .foregroundColor(.white)
                        
                        Spacer()
                        
                        HStack(spacing: 6) {
                            let scores = sessionMuscles.compactMap { perMuscle[$0]?.recoveryScore }
                            let avgRecovery = scores.isEmpty ? Double.nan : Double(scores.reduce(0, +)) / Double(scores.count)
                            
                            Text(avgRecovery.isNaN ? "--%" : "\(Int(avgRecovery))%")
                                .font(.system(size: 10, weight: .black))
                                .foregroundColor(avgRecovery.isNaN ? Color.white.opacity(0.4) : Color.batteryColor(for: Int(avgRecovery)))
                            
                            Image(systemName: musclesExpanded ? "chevron.up" : "chevron.down")
                                .font(.system(size: 12))
                                .foregroundColor(Color.white.opacity(0.5))
                        }
                    }
                    .padding(.horizontal, 12)
                    .padding(.vertical, 10)
                    .background(Color.white.opacity(0.08)) // surfaceVariant copy
                }
                .buttonStyle(PlainButtonStyle())
                
                // Expanded Muscles Grid
                if musclesExpanded {
                    VStack(spacing: 6) {
                        let chunked = sessionMuscles.chunked(into: 3)
                        ForEach(0..<chunked.count, id: \.self) { rowIndex in
                            HStack(spacing: 6) {
                                ForEach(chunked[rowIndex], id: \.self) { muscle in
                                    let score = perMuscle[muscle]?.recoveryScore ?? 100
                                    HStack(spacing: 4) {
                                        Circle()
                                            .fill(Color.batteryColor(for: score))
                                            .frame(width: 6, height: 6)
                                        
                                        Text(muscle)
                                            .font(.system(size: 10, weight: .medium))
                                            .foregroundColor(Color.white.opacity(0.7))
                                            .lineLimit(1)
                                            .frame(maxWidth: .infinity, alignment: .leading)
                                        
                                        Text("\(score)%")
                                            .font(.system(size: 10, weight: .black))
                                            .foregroundColor(Color.batteryColor(for: score))
                                    }
                                    .frame(maxWidth: .infinity)
                                }
                                
                                // Padding if row is incomplete
                                if chunked[rowIndex].count < 3 {
                                    ForEach(0..<(3 - chunked[rowIndex].count), id: \.self) { _ in
                                        Spacer().frame(maxWidth: .infinity)
                                    }
                                }
                            }
                        }
                    }
                    .padding(.horizontal, 12)
                    .padding(.vertical, 8)
                    .background(Color.white.opacity(0.04))
                }
            }
        }
        .background(Color(hex: 0x1C1C1E))
        .cornerRadius(24)
        .shadow(color: .black.opacity(0.1), radius: 8, x: 0, y: 4)
    }
}

// ─── Session Carousel ────────────────────────────────────────────────────────

private struct SessionCarousel: View {
    let sessions: [TodaySessionItem]
    @Binding var activeIndex: Int
    let onIndexChange: (Int) -> Void
    
    var body: some View {
        HStack(spacing: 12) {
            Button(action: {
                let next = activeIndex > 0 ? activeIndex - 1 : sessions.count - 1
                onIndexChange(next)
            }) {
                Image(systemName: "arrow.left")
                    .font(.system(size: 18))
                    .foregroundColor(.white)
                    .frame(width: 32, height: 32)
            }
            
            HStack(spacing: 4) {
                ForEach(0..<sessions.count, id: \.self) { i in
                    let isActive = i == activeIndex
                    Circle()
                        .fill(isActive ? AppColors.neonYellow : Color.white.opacity(0.2))
                        .frame(width: isActive ? 8 : 6, height: isActive ? 8 : 6)
                }
            }
            
            Button(action: {
                let next = activeIndex < sessions.count - 1 ? activeIndex + 1 : 0
                onIndexChange(next)
            }) {
                Image(systemName: "arrow.right")
                    .font(.system(size: 18))
                    .foregroundColor(.white)
                    .frame(width: 32, height: 32)
            }
        }
        .frame(maxWidth: .infinity, alignment: .center)
        .padding(.horizontal, 24)
    }
}

// ─── Helpers ─────────────────────────────────────────────────────────────────

private func getSessionInvolvedMuscles(session: Session) -> [String] {
    var muscles = Set<String>()
    let exercises = session.exercises + session.parts.flatMap { $0.exercises }
    for ex in exercises {
        let name = ex.name.lowercased()
        if name.contains("banca") || name.contains("chest") || name.contains("pecho") || name.contains("pec") {
            muscles.insert("Pectorales")
        } else if name.contains("sentadilla") || name.contains("squat") || name.contains("cuad") || name.contains("quad") {
            muscles.insert("Cuádriceps")
        } else if name.contains("peso muerto") || name.contains("deadlift") || name.contains("femoral") || name.contains("hamstring") {
            muscles.insert("Femorales")
        } else if name.contains("bicep") {
            muscles.insert("Bíceps")
        } else if name.contains("tricep") {
            muscles.insert("Tríceps")
        } else if name.contains("hombro") || name.contains("shoulder") || name.contains("press militar") {
            muscles.insert("Deltoides")
        } else if name.contains("espalda") || name.contains("row") || name.contains("jalón") || name.contains("pull") {
            muscles.insert("Dorsales")
        } else if name.contains("lumbar") || name.contains("lower back") {
            muscles.insert("Lumbar")
        }
    }
    return Array(muscles).sorted()
}

private func getSessionDurationDisplay(session: Session, log: WorkoutLog?) -> String {
    if let log = log {
        return "\(log.durationMinutes) min promedio"
    }
    
    let exercises = session.exercises + session.parts.flatMap { $0.exercises }
    if exercises.isEmpty { return "Sin datos" }
    
    let totalSets = exercises.reduce(0) { $0 + $1.sets.count }
    let estimatedMinutes = (Double(totalSets) * 3.5) + 10.0
    
    return "~\(Int(estimatedMinutes)) min"
}

extension Array {
    func chunked(into size: Int) -> [[Element]] {
        return stride(from: 0, to: count, by: size).map {
            Array(self[$0 ..< Swift.min($0 + size, count)])
        }
    }
}

// ─── No Program Card ─────────────────────────────────────────────────────────

private struct NoProgramSessionCard: View {
    var body: some View {
        VStack(spacing: 8) {
            Text("Sin programa activo")
                .font(.headline)
                .fontWeight(.black)
                .foregroundColor(.white)
            
            Spacer().frame(height: 4)
            
            Text("Crea un programa de entrenamiento para ver tu sesión del día aquí.")
                .font(.system(size: 12))
                .foregroundColor(Color.white.opacity(0.6))
                .multilineTextAlignment(.center)
        }
        .padding(.horizontal, 24)
        .padding(.vertical, 28)
        .frame(maxWidth: .infinity)
        .background(Color.white.opacity(0.08)) // surfaceVariant copy with alpha 0.5 equivalent
        .cornerRadius(24)
    }
}

// ─── Rest Day Card ───────────────────────────────────────────────────────────

private struct RestDayCard: View {
    var body: some View {
        VStack(spacing: 12) {
            Text("Día de descanso")
                .font(.headline)
                .fontWeight(.black)
                .foregroundColor(.white)
            
            Text("Hoy es tu día de recuperación activa")
                .font(.system(size: 12))
                .foregroundColor(Color.white.opacity(0.6))
                .multilineTextAlignment(.center)
        }
        .padding(24)
        .frame(maxWidth: .infinity)
        .background(Color.white.opacity(0.05)) // secondaryContainer copy with alpha 0.5 equivalent
        .cornerRadius(24)
    }
}

#Preview {
    ZStack {
        Color.black.ignoresSafeArea()
        HomeSessionSection(
            sessions: [
                TodaySessionItem(
                    session: Session(id: "s1", name: "Día de Empuje (Fuerza)", exercises: [
                        Exercise(id: "e1", name: "Press Banca", sets: [ExerciseSet(id: "s1"), ExerciseSet(id: "s2")])
                    ]),
                    program: Program(id: "p1", name: "Ripped Program"),
                    location: SessionLocation(macroIndex: 0, mesoIndex: 0, weekId: "w1"),
                    isCompleted: false,
                    dayOfWeek: 1
                )
            ],
            currentDayOfWeek: 1,
            perMuscle: [
                "Pectorales": MuscleRecoveryStatus(muscleName: "Pectorales", recoveryScore: 85, hoursToRecovery: 12, hoursSinceLastSession: 36, effectiveSets: 8, status: .OPTIMAL)
            ],
            onStartWorkout: { _, _ in },
            onResumeWorkout: {}
        )
    }
}
