import SwiftUI

private let LearnCardDark = Color(hex: 0x1C1C1E)

/// Swift translation of HomeWikiLabSection.kt
public struct HomeWikiLabSection: View {
    let onNavigate: (String) -> Void
    
    @State private var dailyConcept: TrainingConcept = TRAINING_CONCEPTS_DATABASE[0]
    
    public init(onNavigate: @escaping (String) -> Void) {
        self.onNavigate = onNavigate
    }
    
    public var body: some View {
        VStack {
            if !TRAINING_CONCEPTS_DATABASE.isEmpty {
                VStack(spacing: 8) {
                    // Header Row of the Section Card
                    HStack {
                        HStack(spacing: 8) {
                            // Icon background circle
                            ZStack {
                                Circle()
                                    .fill(dailyConcept.category.color.opacity(0.22))
                                    .frame(width: 30, height: 30)
                                
                                WikiIcon(tint: dailyConcept.category.color, size: 16)
                            }
                            
                            VStack(alignment: .leading, spacing: 2) {
                                Text("ENCICLOPEDIA")
                                    .font(.system(size: 13, weight: .black))
                                    .tracking(1.4)
                                    .foregroundColor(.white)
                                
                                Text("Enciclopedia del entrenamiento")
                                    .font(.system(size: 10))
                                    .foregroundColor(Color.white.opacity(0.42))
                            }
                        }
                        
                        Spacer()
                        
                        // Search Button (Surface)
                        Button(action: {
                            onNavigate("wikilab_home") // equivalent route
                        }) {
                            Image(systemName: "magnifyingglass")
                                .font(.system(size: 14, weight: .bold))
                                .foregroundColor(dailyConcept.category.color)
                                .padding(7)
                                .background(dailyConcept.category.color.opacity(0.18))
                                .cornerRadius(10)
                        }
                        .buttonStyle(PlainButtonStyle())
                    }
                    
                    // Daily Concept Inner Card
                    Button(action: {
                        onNavigate("wikilab_concept_\(dailyConcept.id)")
                    }) {
                        VStack(alignment: .leading, spacing: 6) {
                            HStack(spacing: 5) {
                                Image(systemName: "lightbulb.fill")
                                    .font(.system(size: 12))
                                    .foregroundColor(dailyConcept.category.color)
                                
                                Text("CONCEPTO DEL DÍA")
                                    .font(.system(size: 10, weight: .black))
                                    .tracking(1)
                                    .foregroundColor(dailyConcept.category.color)
                            }
                            
                            Text(dailyConcept.name)
                                .font(.system(size: 14, weight: .black))
                                .foregroundColor(.white)
                            
                            Text(dailyConcept.shortDescription)
                                .font(.system(size: 12))
                                .foregroundColor(Color.white.opacity(0.56))
                                .lineLimit(2)
                                .multilineTextAlignment(.leading)
                                .frame(maxWidth: .infinity, alignment: .leading)
                            
                            HStack {
                                // Category Pill
                                Text(dailyConcept.category.label)
                                    .font(.system(size: 10, weight: .bold))
                                    .foregroundColor(dailyConcept.category.color)
                                    .padding(.horizontal, 7)
                                    .padding(.vertical, 2)
                                    .background(dailyConcept.category.color.opacity(0.16))
                                    .cornerRadius(5)
                                
                                Spacer()
                                
                                // "Leer más" Button action
                                HStack(spacing: 2) {
                                    Text("Leer más")
                                        .font(.system(size: 10, weight: .bold))
                                        .foregroundColor(dailyConcept.category.color)
                                    
                                    Image(systemName: "arrow.right")
                                        .font(.system(size: 10, weight: .bold))
                                        .foregroundColor(dailyConcept.category.color)
                                }
                            }
                        }
                        .padding(12)
                        .background(Color(hex: 0x242426))
                        .cornerRadius(12)
                    }
                    .buttonStyle(PlainButtonStyle())
                }
                .padding(14)
                .background(LearnCardDark)
                .cornerRadius(18)
                .padding(.horizontal, 24)
                .onAppear {
                    updateDailyConcept()
                }
                // Check if date changed every 60 seconds
                .onReceive(Timer.publish(every: 60, on: .main, in: .common).autoconnect()) { _ in
                    updateDailyConcept()
                }
            }
        }
    }
    
    private func updateDailyConcept() {
        let daysSinceEpoch = Int(Date().timeIntervalSince1970 / 86400)
        let index = daysSinceEpoch % TRAINING_CONCEPTS_DATABASE.count
        self.dailyConcept = TRAINING_CONCEPTS_DATABASE[index]
    }
}

#Preview {
    ZStack {
        Color.black.ignoresSafeArea()
        HomeWikiLabSection(onNavigate: { _ in })
    }
}
