import SwiftUI

struct PatternsListScreen: View {
    let onNavigateToPattern: (String) -> Void
    let onBack: () -> Void
    
    @State private var patterns: [MovementPatternEntity] = WikiLabRepository.shared.patterns
    
    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            
            List {
                headerSection
                descriptionSection
                sectionHeader
                patternsListSection
                Color.clear.frame(height: 80)
            }
            .listStyle(.plain)
            .background(Color.black)
            .scrollContentBackground(.hidden)
        }
        .navigationBarHidden(true)
    }
    
    private var headerSection: some View {
        HStack(spacing: 8) {
            Button(action: onBack) {
                Image(systemName: "chevron.left")
                    .font(.title3)
                    .foregroundColor(.white)
            }
            Text("Patrones de Movimiento")
                .font(.system(size: 32, weight: .black, design: .serif))
                .foregroundColor(.white)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.vertical, 8)
        .listRowInsets(EdgeInsets())
        .listRowBackground(Color.black)
    }
    
    private var descriptionSection: some View {
        Text("Índice de patrones biomecánicos y cadenas cinéticas del cuerpo humano. Selecciona un artículo para revisar su reclutamiento, demandas y errores técnicos comunes.")
            .font(.system(size: 14, design: .serif))
            .foregroundColor(.white.opacity(0.7))
            .listRowInsets(EdgeInsets())
            .listRowBackground(Color.black)
    }
    
    private var sectionHeader: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("ARTÍCULOS DISPONIBLES")
                .font(.system(.title3, design: .serif))
                .fontWeight(.black)
                .foregroundColor(.white)
            Divider().background(Color(hex: 0x2C2C2C))
        }
        .padding(.top, 12)
        .listRowInsets(EdgeInsets())
        .listRowBackground(Color.black)
    }
    
    private var patternsListSection: some View {
        ForEach(patterns, id: \.id) { pattern in
            PatternRowView(
                pattern: pattern,
                onTap: { onNavigateToPattern(pattern.id) }
            )
            .listRowInsets(EdgeInsets())
            .listRowBackground(Color.black)
            .listRowSeparator(.hidden)
        }
    }
}

private struct PatternRowView: View {
    let pattern: MovementPatternEntity
    let onTap: () -> Void
    
    private var forceTypes: [String] {
        WikiLabRepository.shared.parseStringList(jsonStr: pattern.forceTypes)
    }
    
    private var chainTypes: [String] {
        WikiLabRepository.shared.parseStringList(jsonStr: pattern.chainTypes)
    }
    
    private var muscleCount: Int {
        WikiLabRepository.shared.parseStringList(jsonStr: pattern.primaryMuscles).count
    }
    
    private var exerciseCount: Int {
        resolveWikiLabExerciseLinks(
            ids: WikiLabRepository.shared.parseStringList(jsonStr: pattern.exampleExercises)
        ).count
    }
    
    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(pattern.name)
                .font(.system(size: 16, weight: .bold, design: .serif))
                .foregroundColor(Color(hex: 0x29B6F6))
            
            Text(pattern.description)
                .font(.system(size: 14, design: .serif))
                .foregroundColor(.white.opacity(0.8))
            
            Text("Fuerzas: " + forceTypes.joined(separator: ", ") + " · " + "Cadenas: " + chainTypes.map { chain in
                switch chain {
                case "anterior": return "Cadena Anterior"
                case "posterior": return "Cadena Posterior"
                case "full": return "Cuerpo Completo"
                default: return chain
                }
            }.joined(separator: ", "))
                .font(.system(size: 13, design: .serif))
                .foregroundColor(.white.opacity(0.6))
            
            Text("Motores principales: \(muscleCount) · Ejercicios de ejemplo: \(exerciseCount)")
                .font(.system(size: 11, design: .serif))
                .foregroundColor(.white.opacity(0.5))
            
            Divider()
                .background(Color(hex: 0x1A1A1A))
                .padding(.top, 6)
        }
        .padding(.vertical, 12)
        .contentShape(Rectangle())
        .onTapGesture(perform: onTap)
    }
}
