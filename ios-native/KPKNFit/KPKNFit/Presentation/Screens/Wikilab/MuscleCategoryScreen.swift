import SwiftUI

private struct BodyPartDef {
    let key: String
    let label: String
    let color: Color
}

private let BODY_PARTS: [BodyPartDef] = [
    BodyPartDef(key: "upper", label: "Tren superior", color: Color(hex: 0x1E88E5)),
    BodyPartDef(key: "lower", label: "Tren inferior", color: Color(hex: 0x43A047)),
    BodyPartDef(key: "core", label: "Core", color: Color(hex: 0xFFFF8F00)),
    BodyPartDef(key: "spine", label: "Columna", color: Color(hex: 0x9C27B0)),
]

struct MuscleCategoryScreen: View {
    let onNavigateToMuscle: (String) -> Void
    let onBack: () -> Void
    
    @State private var query: String = ""
    
    private var muscles: [MuscleGroupEntity] { WikiLabRepository.shared.muscles }
    
    private var canonicalMuscles: [MuscleGroupEntity] {
        muscles
            .compactMap { m -> MuscleGroupEntity? in
                guard let canonicalId = canonicalWikiLabMuscleIdFromEntityId(m.id) else { return nil }
                return WikiLabRepository.shared.getMuscleById(id: canonicalId)
            }
            .reduce(into: [String: MuscleGroupEntity]()) { dict, m in dict[m.id] = m }
            .values
            .map { $0 }
    }
    
    private var filtered: [MuscleGroupEntity] {
        if query.trimmingCharacters(in: .whitespaces).isEmpty { return canonicalMuscles }
        return canonicalMuscles.filter {
            $0.name.localizedCaseInsensitiveContains(query) ||
            $0.description.localizedCaseInsensitiveContains(query)
        }
    }
    
    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            
            List {
                headerSection
                searchSection
                
                if query.trimmingCharacters(in: .whitespaces).isEmpty {
                    summaryTableSection
                }
                
                bodyPartSections
                
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
            Text("Atlas Anatómico")
                .font(.system(size: 32, weight: .black, design: .serif))
                .foregroundColor(.white)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.vertical, 8)
        .listRowInsets(EdgeInsets())
        .listRowBackground(Color.black)
    }
    
    private var searchSection: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("Índice de grupos musculares con descripciones anatómicas y referencias de volúmenes de entrenamiento.")
                .font(.system(size: 14, design: .serif))
                .foregroundColor(.white.opacity(0.7))
            
            HStack(spacing: 8) {
                Image(systemName: "magnifyingglass")
                    .foregroundColor(.white.opacity(0.5))
                if query.isEmpty {
                    Text("Buscar artículo...")
                        .font(.system(size: 13, design: .serif))
                        .foregroundColor(.white.opacity(0.3))
                    Spacer()
                } else {
                    TextField("", text: $query)
                        .font(.system(size: 13, design: .serif))
                        .foregroundColor(.white)
                        .autocorrectionDisabled()
                    Button(action: { query = "" }) {
                        Image(systemName: "xmark")
                            .foregroundColor(.white.opacity(0.5))
                    }
                }
            }
            .padding(12)
            .overlay(RoundedRectangle(cornerRadius: 4).stroke(Color.white.opacity(query.isEmpty ? 0.2 : 0.4), lineWidth: 1))
        }
        .listRowInsets(EdgeInsets())
        .listRowBackground(Color.black)
    }
    
    private var summaryTableSection: some View {
        VStack(spacing: 6) {
            Text("Estructura Anatomía")
                .font(.system(.title3, design: .serif))
                .fontWeight(.bold)
                .foregroundColor(.white)
                .frame(maxWidth: .infinity, alignment: .leading)
            Divider().background(Color(hex: 0x2C2C2C))
            
            ForEach(BODY_PARTS, id: \.key) { bp in
                let count = canonicalMuscles.filter { $0.bodyPart == bp.key }.count
                HStack {
                    HStack(spacing: 8) {
                        Circle()
                            .fill(bp.color)
                            .frame(width: 6, height: 6)
                        Text(bp.label)
                            .font(.system(size: 14, design: .serif))
                            .foregroundColor(.white)
                    }
                    Spacer()
                    Text("\(count) artículos")
                        .font(.system(size: 14, design: .serif))
                        .fontWeight(.bold)
                        .foregroundColor(bp.color)
                }
                .padding(.vertical, 4)
            }
        }
        .padding(12)
        .background(Color(hex: 0x141414))
        .listRowInsets(EdgeInsets())
        .listRowBackground(Color.black)
    }
    
    private var bodyPartSections: some View {
        ForEach(BODY_PARTS, id: \.key) { bp in
            let musclesInPart = filtered.filter { $0.bodyPart == bp.key }
            if !musclesInPart.isEmpty {
                Section {
                    ForEach(musclesInPart, id: \.id) { muscle in
                        MuscleAtlasCardView(muscle: muscle, color: bp.color)
                            .contentShape(Rectangle())
                            .onTapGesture { onNavigateToMuscle(muscle.id) }
                            .listRowInsets(EdgeInsets())
                            .listRowBackground(Color.black)
                            .listRowSeparator(.hidden)
                    }
                } header: {
                    VStack(alignment: .leading, spacing: 4) {
                        Text(bp.label.uppercased())
                            .font(.system(.title3, design: .serif))
                            .fontWeight(.black)
                            .foregroundColor(.white)
                            .padding(.top, 12)
                        Divider().background(Color(hex: 0x2C2C2C))
                    }
                    .listRowInsets(EdgeInsets())
                    .listRowBackground(Color.black)
                }
            }
        }
    }
}

private struct MuscleAtlasCardView: View {
    let muscle: MuscleGroupEntity
    let color: Color
    
    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(muscle.name)
                .font(.system(size: 16, weight: .bold, design: .serif))
                .foregroundColor(Color(hex: 0x29B6F6))
            
            Text(muscle.description)
                .font(.system(size: 14, design: .serif))
                .foregroundColor(.white.opacity(0.8))
            
            if let mev = muscle.mev {
                Text("Volumen de Mantenimiento (MEV): \(mev) series semanales")
                    .font(.system(size: 11, design: .serif))
                    .foregroundColor(color)
                    .padding(.top, 2)
            }
            
            Divider()
                .background(Color(hex: 0x1A1A1A))
                .padding(.top, 6)
        }
        .padding(.vertical, 12)
    }
}
