import SwiftUI

private let HomeCardDark = Color(hex: 0x1C1C1E)
private let HomeCardDarkAlt = Color(hex: 0x242426)

/// Swift translation of HomeCardsSection.kt
public struct HomeCardsSection: View {
    let viewModel: HomeViewModel
    let onNavigateToCard: (String) -> Void
    let onAddMeal: () -> Void
    
    public init(viewModel: HomeViewModel, onNavigateToCard: @escaping (String) -> Void, onAddMeal: @escaping () -> Void = {}) {
        self.viewModel = viewModel
        self.onNavigateToCard = onNavigateToCard
        self.onAddMeal = onAddMeal
    }
    
    public var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            SectionHeader("Progreso físico y alimentación")
                .padding(.horizontal, 24)
            
            MacroProgressBars(viewModel: viewModel, onAddMeal: onAddMeal)
                .padding(.horizontal, 24)
            
            Spacer().frame(height: 12)
            
            BiometryCardsCarousel(viewModel: viewModel, onNavigateToCard: onNavigateToCard)
            
            Spacer().frame(height: 18)
            
            SectionHeader("Tus ejercicios")
                .padding(.horizontal, 24)
            
            ExerciseMetricCards(viewModel: viewModel, onNavigateToCard: onNavigateToCard)
        }
    }
}

// ─── Macro Progress Bars ────────────────────────────────────────────────────

private struct MacroProgressBars: View {
    let viewModel: HomeViewModel
    let onAddMeal: () -> Void
    
    var body: some View {
        let calGoal = viewModel.dailyCalorieGoal
        let protGoal = viewModel.dailyProteinGoal
        let carbGoal = viewModel.dailyCarbGoal
        let fatGoal = viewModel.dailyFatGoal
        let nutritionToday = viewModel.todayNutritionTotals
        
        let macros = [
            MacroItem(label: "Cal", current: Int(nutritionToday.calories), goal: calGoal, color: Color(hex: 0x60A5FA)),
            MacroItem(label: "Prot", current: Int(nutritionToday.protein), goal: protGoal, color: Color(hex: 0xF87171)),
            MacroItem(label: "Carb", current: Int(nutritionToday.carbs), goal: carbGoal, color: Color(hex: 0xFBBF24)),
            MacroItem(label: "Fat", current: Int(nutritionToday.fats), goal: fatGoal, color: Color(hex: 0xA78BFA))
        ]
        
        Button(action: onAddMeal) {
            VStack(alignment: .leading, spacing: 8) {
                Text("REGISTRO DE HOY")
                    .font(.system(size: 10, weight: .black))
                    .foregroundColor(Color.white.opacity(0.48))
                    .tracking(1.6)
                
                ForEach(macros, id: \.label) { m in
                    VStack(spacing: 4) {
                        HStack {
                            Text(m.label)
                                .font(.system(size: 10, weight: .black))
                                .foregroundColor(Color.white.opacity(0.72))
                            
                            Spacer()
                            
                            Text("\(m.current)/\(m.goal)")
                                .font(.system(size: 10))
                                .foregroundColor(Color.white.opacity(0.46))
                        }
                        
                        ProgressView(value: Double(m.current), total: Double(max(m.goal, 1)))
                            .progressViewStyle(LinearProgressViewStyle(tint: m.color))
                            .frame(height: 4)
                            .clipShape(Capsule())
                    }
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 14)
            .background(HomeCardDark)
            .cornerRadius(22)
        }
        .buttonStyle(PlainButtonStyle())
    }
}

private struct MacroItem {
    let label: String
    let current: Int
    let goal: Int
    let color: Color
}

// ─── Biometry Cards Carousel ────────────────────────────────────────────────

private struct BiometryCardsCarousel: View {
    let viewModel: HomeViewModel
    let onNavigateToCard: (String) -> Void
    
    var body: some View {
        let lastWeight = viewModel.lastWeight
        let lastBodyFat = viewModel.lastBodyFat
        let lastMusclePct = viewModel.lastMusclePct
        let height = viewModel.heightCm
        
        let ffmiValue = (lastWeight != nil && lastBodyFat != nil) ? viewModel.computeNormalizedFfmi(weightKg: lastWeight!, heightCm: height, bodyFatPct: lastBodyFat!) : nil
        let ffmiInterpretation = (lastWeight != nil && lastBodyFat != nil) ? viewModel.computeFfmiInterpretation(weightKg: lastWeight!, heightCm: height, bodyFatPct: lastBodyFat!) : nil
        
        let weightText = lastWeight != nil ? String(format: "%.1f", lastWeight!) : "--"
        let ffmiText = ffmiValue != nil ? String(format: "%.1f", ffmiValue!) : "--"
        let imcText = lastWeight != nil ? (viewModel.computeImc(weightKg: lastWeight!, heightCm: height) != nil ? String(format: "%.1f", viewModel.computeImc(weightKg: lastWeight!, heightCm: height)!) : "--") : "--"
        let fatText = lastBodyFat != nil ? String(format: "%.1f", lastBodyFat!) : "--"
        let muscleText = lastMusclePct != nil ? String(format: "%.1f", lastMusclePct!) : "--"
        
        let cards = [
            BiometryCardData(title: "Peso", value: weightText, unit: "kg", navTarget: "body-progress"),
            BiometryCardData(title: "FFMI", value: ffmiText, unit: ffmiInterpretation ?? "S/D", navTarget: "ffmi"),
            BiometryCardData(title: "IMC", value: imcText, unit: "", navTarget: "imc"),
            BiometryCardData(title: "% Grasa", value: fatText, unit: "%", navTarget: "fat"),
            BiometryCardData(title: "% Músculo", value: muscleText, unit: "%", navTarget: "muscle")
        ]
        
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 12) {
                ForEach(cards, id: \.title) { card in
                    BiometryCard(data: card, onClick: { onNavigateToCard(card.navTarget) })
                }
            }
            .padding(.horizontal, 24)
        }
    }
}

private struct BiometryCardData {
    let title: String
    let value: String
    let unit: String
    let navTarget: String
}

private struct BiometryCard: View {
    let data: BiometryCardData
    let onClick: () -> Void
    
    var body: some View {
        Button(action: onClick) {
            VStack(alignment: .leading, spacing: 0) {
                Text(data.title)
                    .font(.system(size: 10, weight: .black))
                    .foregroundColor(Color.white.opacity(0.48))
                    .tracking(1.2)
                
                Spacer()
                
                VStack(alignment: .leading, spacing: 0) {
                    HStack(alignment: .bottom, spacing: 2) {
                        Text(data.value)
                            .font(.system(size: 20, weight: .black))
                            .foregroundColor(.white)
                        
                        if data.unit == "%" || data.unit == "kg" {
                            Text(data.unit)
                                .font(.system(size: 10))
                                .foregroundColor(Color.white.opacity(0.44))
                                .padding(.bottom, 4)
                        }
                    }
                    
                    if data.unit != "%" && data.unit != "kg" && !data.unit.isEmpty {
                        Text(data.unit)
                            .font(.system(size: 10, weight: .bold))
                            .foregroundColor(Color.white.opacity(0.5))
                    }
                }
            }
            .padding(14)
            .frame(width: 118, height: 124, alignment: .leading)
            .background(HomeCardDarkAlt)
            .cornerRadius(22)
        }
        .buttonStyle(PlainButtonStyle())
    }
}

// ─── Exercise Metric Cards ──────────────────────────────────────────────────

private struct ExerciseMetricCards: View {
    let viewModel: HomeViewModel
    let onNavigateToCard: (String) -> Void
    
    var body: some View {
        let starCount = viewModel.starTargetsCount
        let historyCount = viewModel.historyCount
        let strengthData = viewModel.getRelativeStrengthData()
        let ipfGlPoints = viewModel.getIpfGlPoints()
        
        let cards = [
            ExerciseCardData(title: "Metas 1RM", mainValue: "\(starCount)", subtitle: "Pendientes", navTarget: "star-targets"),
            ExerciseCardData(title: "Fuerza Relativa", mainValue: String(format: "%.2fx", strengthData.relativeStrength), subtitle: "Total: \(Int(strengthData.totalKg))kg", navTarget: "relative-strength"),
            ExerciseCardData(title: "Historiales", mainValue: "\(historyCount)", subtitle: "Sesiones registradas", navTarget: "history"),
            ExerciseCardData(title: "IPF GL", mainValue: ipfGlPoints > 0.0 ? String(format: "%.0f", ipfGlPoints) : "--", subtitle: "Puntos", navTarget: "ipf-gl")
        ]
        
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 12) {
                ForEach(cards, id: \.title) { card in
                    ExerciseCard(data: card, onClick: { onNavigateToCard(card.navTarget) })
                }
            }
            .padding(.horizontal, 24)
        }
    }
}

private struct ExerciseCardData {
    let title: String
    let mainValue: String
    let subtitle: String
    let navTarget: String
}

private struct ExerciseCard: View {
    let data: ExerciseCardData
    let onClick: () -> Void
    
    var body: some View {
        Button(action: onClick) {
            VStack(alignment: .leading, spacing: 0) {
                Text(data.title)
                    .font(.system(size: 12, weight: .black))
                    .foregroundColor(.white)
                
                Spacer()
                
                VStack(alignment: .leading, spacing: 0) {
                    Text(data.mainValue)
                        .font(.system(size: 20, weight: .black))
                        .foregroundColor(.white)
                    
                    Text(data.subtitle)
                        .font(.system(size: 10, weight: .bold))
                        .foregroundColor(Color.white.opacity(0.48))
                }
            }
            .padding(14)
            .frame(width: 156, height: 106, alignment: .leading)
            .background(HomeCardDarkAlt)
            .cornerRadius(22)
        }
        .buttonStyle(PlainButtonStyle())
    }
}

#Preview {
    ZStack {
        Color.black.ignoresSafeArea()
        HomeCardsSection(viewModel: HomeViewModel(), onNavigateToCard: { _ in })
    }
}
