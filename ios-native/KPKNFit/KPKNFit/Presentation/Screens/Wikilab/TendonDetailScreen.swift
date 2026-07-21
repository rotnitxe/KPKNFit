import SwiftUI

struct TendonDetailScreen: View {
    let tendonId: String
    let onNavigateToMuscle: (String) -> Void
    let onNavigateToJoint: (String) -> Void
    let onNavigateToExercise: (String) -> Void
    let onBack: () -> Void
    
    private var tendon: TendonEntity? { WikiLabRepository.shared.getTendonById(id: tendonId) }
    
    private var injuries: [InjuryInfo] {
        WikiLabRepository.shared.parseInjuries(jsonStr: tendon?.commonInjuries)
    }
    
    private var protectionIds: [String] {
        WikiLabRepository.shared.parseStringList(jsonStr: tendon?.protectiveExercises)
    }
    
    private var protectiveExercises: [WikiLabExerciseLink] {
        resolveWikiLabExerciseLinks(ids: protectionIds)
    }
    
    private var tendonGuide: WikiLabVisualGuide? {
        guard let t = tendon else { return nil }
        return buildTendonGuide(t)
    }
    
    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            
            if let tendon = tendon {
                List {
                    headerSection(tendon: tendon)
                    infoboxSection(tendon: tendon)
                    
                    if let guide = tendonGuide {
                        insightCardSection(guide: guide)
                    }
                    
                    relatedMuscleSection
                    relatedJointSection
                    injuriesSection
                    protectiveExercisesSection
                    recoverySection
                    
                    Color.clear.frame(height: 80)
                }
                .listStyle(.plain)
                .background(Color.black)
                .scrollContentBackground(.hidden)
            } else {
                Text("Tendón no encontrado")
                    .foregroundColor(.white)
            }
        }
        .navigationBarHidden(true)
    }
    
    private func headerSection(tendon: TendonEntity) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 8) {
                Button(action: onBack) {
                    Image(systemName: "chevron.left")
                        .font(.title3)
                        .foregroundColor(.white)
                }
                Text(tendon.name)
                    .font(.system(size: 32, weight: .black, design: .serif))
                    .foregroundColor(Color(hex: 0xFFFF8F00))
                Spacer()
            }
            
            if let desc = tendon.description {
                Text(desc)
                    .font(.system(size: 14, design: .serif))
                    .foregroundColor(.white.opacity(0.9))
                    .lineSpacing(4)
            }
        }
        .padding(.vertical, 8)
        .listRowInsets(EdgeInsets())
        .listRowBackground(Color.black)
    }
    
    private func infoboxSection(tendon: TendonEntity) -> some View {
        VStack(spacing: 8) {
            Text("Ficha Técnica Tendinosa")
                .font(.system(.title3, design: .serif))
                .fontWeight(.bold)
                .foregroundColor(.white)
                .frame(maxWidth: .infinity)
            Divider().background(Color(hex: 0x2C2C2C))
            
            InfoboxRowView(label: "Estructura", value: tendon.name)
            if let muscleId = tendon.muscleId {
                InfoboxRowView(label: "Músculo Asociado",
                              value: muscleId.replacingOccurrences(of: "_", with: " ").capitalized)
            }
            if let jointId = tendon.jointId {
                InfoboxRowView(label: "Articulación Asociada",
                              value: jointId.replacingOccurrences(of: "_", with: " ").capitalized)
            }
        }
        .padding(12)
        .background(Color(hex: 0x141414))
        .overlay(RoundedRectangle(cornerRadius: 4).stroke(Color(hex: 0x2C2C2C), lineWidth: 1))
        .listRowInsets(EdgeInsets())
        .listRowBackground(Color.black)
    }
    
    private func insightCardSection(guide: WikiLabVisualGuide) -> some View {
        WikiLabInsightCard(
            title: guide.title,
            accent: guide.accent,
            icon: guide.icon,
            summary: guide.summary,
            bullets: guide.bullets
        )
        .listRowInsets(EdgeInsets())
        .listRowBackground(Color.black)
    }
    
    private var relatedMuscleSection: some View {
        guard let muscleId = tendon?.muscleId else { return AnyView(EmptyView()) }
        let canonicalId = canonicalWikiLabMuscleIdFromEntityId(muscleId) ?? muscleId
        guard let muscle = WikiLabRepository.shared.getMuscleById(id: canonicalId) else { return AnyView(EmptyView()) }
        
        return AnyView(
            VStack(alignment: .leading, spacing: 8) {
                WikiSectionHeaderView(title: "Músculo Relacionado")
                HStack(spacing: 10) {
                    Circle()
                        .fill(Color(hex: 0x9C27B0))
                        .frame(width: 6, height: 6)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(muscle.name)
                            .font(.system(size: 14, design: .serif))
                            .foregroundColor(Color(hex: 0x9C27B0))
                            .fontWeight(.bold)
                        Text(String(muscle.description.prefix(100)) + "...")
                            .font(.system(size: 13, design: .serif))
                            .foregroundColor(.white.opacity(0.6))
                    }
                }
                .padding(.leading, 12)
                .contentShape(Rectangle())
                .onTapGesture { onNavigateToMuscle(canonicalId) }
            }
            .listRowInsets(EdgeInsets())
            .listRowBackground(Color.black)
        )
    }
    
    private var relatedJointSection: some View {
        guard let jointId = tendon?.jointId else { return AnyView(EmptyView()) }
        guard let joint = WikiLabRepository.shared.getJointById(id: jointId) else { return AnyView(EmptyView()) }
        
        return AnyView(
            VStack(alignment: .leading, spacing: 8) {
                WikiSectionHeaderView(title: "Articulación Relacionada")
                HStack(spacing: 10) {
                    Circle()
                        .fill(Color(hex: 0x1E88E5))
                        .frame(width: 6, height: 6)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(joint.name)
                            .font(.system(size: 14, design: .serif))
                            .foregroundColor(Color(hex: 0x1E88E5))
                            .fontWeight(.bold)
                        Text(WikiLabRepository.shared.getJointTypeLabel(type: joint.type))
                            .font(.system(size: 13, design: .serif))
                            .foregroundColor(.white.opacity(0.6))
                    }
                }
                .padding(.leading, 12)
                .contentShape(Rectangle())
                .onTapGesture { onNavigateToJoint(jointId) }
            }
            .listRowInsets(EdgeInsets())
            .listRowBackground(Color.black)
        )
    }
    
    private var injuriesSection: some View {
        guard !injuries.isEmpty else { return AnyView(EmptyView()) }
        
        return AnyView(
            VStack(alignment: .leading, spacing: 8) {
                WikiSectionHeaderView(title: "Lesiones Comunes y Patología")
                ForEach(injuries, id: \.id) { injury in
                    VStack(alignment: .leading, spacing: 4) {
                        Text(injury.name)
                            .font(.system(.subheadline, design: .serif))
                            .fontWeight(.bold)
                            .foregroundColor(Color(hex: 0xE53935))
                        
                        if let desc = injury.description {
                            Text(desc)
                                .font(.system(size: 14, design: .serif))
                                .foregroundColor(.white.opacity(0.8))
                                .lineSpacing(4)
                        }
                        
                        if let contra = injury.contraindications, !contra.isEmpty {
                            Text("Contraindicaciones:")
                                .font(.system(size: 11, design: .serif))
                                .fontWeight(.bold)
                                .foregroundColor(Color(hex: 0xE53935))
                            ForEach(contra, id: \.self) { c in
                                Text("• \(c)")
                                    .font(.system(size: 13, design: .serif))
                                    .foregroundColor(.white.opacity(0.7))
                            }
                        }
                        
                        if let progs = injury.returnProgressions, !progs.isEmpty {
                            Text("Progresión de retorno:")
                                .font(.system(size: 11, design: .serif))
                                .fontWeight(.bold)
                                .foregroundColor(Color(hex: 0x43A047))
                            ForEach(Array(progs.enumerated()), id: \.offset) { j, p in
                                Text("\(j + 1). \(p)")
                                    .font(.system(size: 13, design: .serif))
                                    .foregroundColor(Color(hex: 0x43A047))
                            }
                        }
                    }
                    .padding(.leading, 12)
                    .padding(.vertical, 4)
                }
            }
            .listRowInsets(EdgeInsets())
            .listRowBackground(Color.black)
        )
    }
    
    private var protectiveExercisesSection: some View {
        guard !protectiveExercises.isEmpty else { return AnyView(EmptyView()) }
        
        return AnyView(
            VStack(alignment: .leading, spacing: 8) {
                WikiSectionHeaderView(title: "Ejercicios Protectores")
                ForEach(protectiveExercises, id: \.id) { exercise in
                    HStack(spacing: 10) {
                        Circle()
                            .fill(Color(hex: 0x66BB6A))
                            .frame(width: 6, height: 6)
                        Text(exercise.name)
                            .font(.system(size: 14, design: .serif))
                            .foregroundColor(Color(hex: 0x66BB6A))
                            .fontWeight(.bold)
                        if !exercise.subtitle.isEmpty {
                            Text("· \(exercise.subtitle)")
                                .font(.system(size: 13, design: .serif))
                                .foregroundColor(.white.opacity(0.5))
                        }
                    }
                    .padding(.leading, 12)
                    .padding(.vertical, 2)
                    .contentShape(Rectangle())
                    .onTapGesture { onNavigateToExercise(exercise.id) }
                }
            }
            .listRowInsets(EdgeInsets())
            .listRowBackground(Color.black)
        )
    }
    
    private var recoverySection: some View {
        VStack(alignment: .leading, spacing: 8) {
            WikiSectionHeaderView(title: "Tiempos de Adaptación y Recuperación")
            Text("Los tendones tienen una tasa metabólica más baja que los músculos y requieren de 6 a 12 semanas para adaptarse estructuralmente a cargas nuevas de entrenamiento. En caso de una tendinopatía o sobrecarga, la recuperación fisiológica completa suele tomar de 3 a 6 meses de fortalecimiento progresivo.")
                .font(.system(size: 14, design: .serif))
                .foregroundColor(.white.opacity(0.8))
                .lineSpacing(4)
                .padding(.leading, 12)
        }
        .listRowInsets(EdgeInsets())
        .listRowBackground(Color.black)
    }
}

private func WikiSectionHeaderView(title: String) -> some View {
    VStack(alignment: .leading, spacing: 4) {
        Text(title)
            .font(.system(.title3, design: .serif))
            .fontWeight(.black)
            .foregroundColor(.white)
        Divider().background(Color(hex: 0x2C2C2C))
    }
    .padding(.top, 12)
}

private func InfoboxRowView(label: String, value: String) -> some View {
    HStack {
        Text(label)
            .font(.system(size: 14, design: .serif))
            .fontWeight(.bold)
            .foregroundColor(.white.opacity(0.5))
        Spacer()
        Text(value)
            .font(.system(size: 14, design: .serif))
            .fontWeight(.medium)
            .foregroundColor(.white)
    }
}
