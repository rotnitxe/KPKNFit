import SwiftUI

struct MuscleGroupDetailScreen: View {
    let muscleId: String
    let onNavigateToJoint: (String) -> Void
    let onNavigateToTendon: (String) -> Void
    let onNavigateToExercise: (String) -> Void
    let onBack: () -> Void
    
    private var muscle: MuscleGroupEntity? { WikiLabRepository.shared.getMuscleById(id: muscleId) }
    
    private var bodyPart: String { WikiLabRepository.shared.getBodyPartLabel(bodyPart: muscle?.bodyPart) }
    private var color: Color { bodyPartColor(muscle?.bodyPart) }
    
    private var relatedJointIds: [String] { WikiLabRepository.shared.parseStringList(jsonStr: muscle?.relatedJoints) }
    private var relatedTendonIds: [String] { WikiLabRepository.shared.parseStringList(jsonStr: muscle?.relatedTendons) }
    private var mechFunctions: [String] { WikiLabRepository.shared.parseStringList(jsonStr: muscle?.mechanicalFunctions) }
    private var recExerciseIds: [String] { WikiLabRepository.shared.parseStringList(jsonStr: muscle?.recommendedExercises) }
    
    private var recommendedExercises: [WikiLabExerciseLink] {
        guard let m = muscle else { return [] }
        let direct = resolveWikiLabExerciseLinks(ids: recExerciseIds)
        if !direct.isEmpty { return direct }
        return recommendedExercisesForMuscle(m)
    }
    
    private var visualGuide: WikiLabVisualGuide? {
        guard let m = muscle else { return nil }
        return buildMuscleGuide(m)
    }
    
    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            
            if let muscle = muscle {
                List {
                    titleSection(muscle: muscle)
                    infoboxSection(muscle: muscle)
                    
                    if let guide = visualGuide {
                        insightCardSection(guide: guide)
                    }
                    
                    anatomySection(muscle: muscle)
                    importanceSection(muscle: muscle)
                    relationsSection
                    exercisesSection
                    aestheticSection(muscle: muscle)
                    
                    Color.clear.frame(height: 80)
                }
                .listStyle(.plain)
                .background(Color.black)
                .scrollContentBackground(.hidden)
            } else {
                VStack(spacing: 8) {
                    Image(systemName: "magnifyingglass")
                        .font(.system(size: 48))
                        .foregroundColor(.white.opacity(0.5))
                    Text("Músculo no encontrado")
                        .font(.system(.title2))
                        .foregroundColor(.white.opacity(0.5))
                }
            }
        }
        .navigationBarHidden(true)
    }
    
    private func titleSection(muscle: MuscleGroupEntity) -> some View {
        HStack(spacing: 8) {
            Button(action: onBack) {
                Image(systemName: "chevron.left")
                    .font(.title3)
                    .foregroundColor(.white)
            }
            Text(muscle.name)
                .font(.system(size: 32, weight: .black, design: .serif))
                .foregroundColor(color)
            Spacer()
        }
        .padding(.vertical, 8)
        .listRowInsets(EdgeInsets())
        .listRowBackground(Color.black)
    }
    
    private func infoboxSection(muscle: MuscleGroupEntity) -> some View {
        VStack(spacing: 8) {
            Text("Ficha Técnica Muscular")
                .font(.system(.title3, design: .serif))
                .fontWeight(.bold)
                .foregroundColor(.white)
                .frame(maxWidth: .infinity)
            Divider().background(Color(hex: 0x2C2C2C))
            
            InfoboxRowView(label: "Parte del Cuerpo", value: bodyPart)
            if let origin = muscle.origin { InfoboxRowView(label: "Origen", value: origin) }
            if let insertion = muscle.insertion { InfoboxRowView(label: "Inserción", value: insertion) }
            if let mev = muscle.mev { InfoboxRowView(label: "MEV (Mínimo Efectivo)", value: "\(mev) series/sem") }
            if let mav = muscle.mav { InfoboxRowView(label: "MAV (Máximo Adaptativo)", value: "\(mav) series/sem") }
            if let mrv = muscle.mrv { InfoboxRowView(label: "MRV (Máximo Recuperable)", value: "\(mrv) series/sem") }
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
    
    private func anatomySection(muscle: MuscleGroupEntity) -> some View {
        let hasAnatomy = muscle.origin != nil || muscle.insertion != nil || !mechFunctions.isEmpty
        guard hasAnatomy else { return AnyView(EmptyView()) }
        
        return AnyView(
            VStack(alignment: .leading, spacing: 8) {
                WikiSectionHeaderView(title: "Anatomía y Función Mecánica")
                
                if let origin = muscle.origin {
                    Text("Origen")
                        .font(.system(.subheadline, design: .serif))
                        .fontWeight(.bold)
                        .foregroundColor(.white)
                    Text(origin)
                        .font(.system(size: 14, design: .serif))
                        .foregroundColor(.white.opacity(0.8))
                        .padding(.leading, 12)
                        .padding(.bottom, 4)
                }
                
                if let insertion = muscle.insertion {
                    Text("Inserción")
                        .font(.system(.subheadline, design: .serif))
                        .fontWeight(.bold)
                        .foregroundColor(.white)
                    Text(insertion)
                        .font(.system(size: 14, design: .serif))
                        .foregroundColor(.white.opacity(0.8))
                        .padding(.leading, 12)
                        .padding(.bottom, 4)
                }
                
                if !mechFunctions.isEmpty {
                    Text("Funciones Mecánicas Principales")
                        .font(.system(.subheadline, design: .serif))
                        .fontWeight(.bold)
                        .foregroundColor(.white)
                    ForEach(mechFunctions, id: \.self) { fn in
                        HStack(spacing: 8) {
                            Image(systemName: "arrow.right")
                                .font(.system(size: 10))
                                .foregroundColor(color)
                            Text(fn)
                                .font(.system(size: 14, design: .serif))
                                .foregroundColor(.white.opacity(0.8))
                        }
                        .padding(.leading, 12)
                    }
                }
            }
            .listRowInsets(EdgeInsets())
            .listRowBackground(Color.black)
        )
    }
    
    private func importanceSection(muscle: MuscleGroupEntity) -> some View {
        let hasImportance = muscle.importanceMovement != nil || muscle.importanceHealth != nil
        guard hasImportance else { return AnyView(EmptyView()) }
        
        return AnyView(
            VStack(alignment: .leading, spacing: 8) {
                WikiSectionHeaderView(title: "Importancia Funcional y Clínica")
                
                if let impMov = muscle.importanceMovement {
                    Text("En el Movimiento")
                        .font(.system(.subheadline, design: .serif))
                        .fontWeight(.bold)
                        .foregroundColor(.white)
                    Text(impMov)
                        .font(.system(size: 14, design: .serif))
                        .foregroundColor(.white.opacity(0.8))
                        .lineSpacing(4)
                        .padding(.leading, 12)
                        .padding(.bottom, 4)
                }
                
                if let impHealth = muscle.importanceHealth {
                    Text("En la Salud y Prevención")
                        .font(.system(.subheadline, design: .serif))
                        .fontWeight(.bold)
                        .foregroundColor(.white)
                    Text(impHealth)
                        .font(.system(size: 14, design: .serif))
                        .foregroundColor(.white.opacity(0.8))
                        .lineSpacing(4)
                        .padding(.leading, 12)
                }
            }
            .listRowInsets(EdgeInsets())
            .listRowBackground(Color.black)
        )
    }
    
    private var relationsSection: some View {
        let hasRelations = !relatedJointIds.isEmpty || !relatedTendonIds.isEmpty
        guard hasRelations else { return AnyView(EmptyView()) }
        
        return AnyView(
            VStack(alignment: .leading, spacing: 8) {
                WikiSectionHeaderView(title: "Relaciones Articulares y Tendinosas")
                
                if !relatedJointIds.isEmpty {
                    Text("Articulaciones Relacionadas")
                        .font(.system(.subheadline, design: .serif))
                        .fontWeight(.bold)
                        .foregroundColor(.white)
                    ForEach(relatedJointIds.compactMap { WikiLabRepository.shared.getJointById(id: $0) }, id: \.id) { joint in
                        HStack(spacing: 10) {
                            Circle()
                                .fill(Color(hex: 0x29B6F6))
                                .frame(width: 6, height: 6)
                            Text(joint.name)
                                .font(.system(size: 14, design: .serif))
                                .foregroundColor(Color(hex: 0x29B6F6))
                                .fontWeight(.bold)
                            Text("(\(WikiLabRepository.shared.getJointTypeLabel(type: joint.type)))")
                                .font(.system(size: 13, design: .serif))
                                .foregroundColor(.white.opacity(0.5))
                        }
                        .padding(.leading, 12)
                        .padding(.vertical, 2)
                        .contentShape(Rectangle())
                        .onTapGesture { onNavigateToJoint(joint.id) }
                    }
                }
                
                if !relatedTendonIds.isEmpty {
                    Text("Tendones Relacionados")
                        .font(.system(.subheadline, design: .serif))
                        .fontWeight(.bold)
                        .foregroundColor(.white)
                        .padding(.top, 4)
                    ForEach(relatedTendonIds.compactMap { WikiLabRepository.shared.getTendonById(id: $0) }, id: \.id) { tendon in
                        HStack(spacing: 10) {
                            Circle()
                                .fill(Color(hex: 0xFFFF8F00))
                                .frame(width: 6, height: 6)
                            Text(tendon.name)
                                .font(.system(size: 14, design: .serif))
                                .foregroundColor(Color(hex: 0xFFFF8F00))
                                .fontWeight(.bold)
                        }
                        .padding(.leading, 12)
                        .padding(.vertical, 2)
                        .contentShape(Rectangle())
                        .onTapGesture { onNavigateToTendon(tendon.id) }
                    }
                }
            }
            .listRowInsets(EdgeInsets())
            .listRowBackground(Color.black)
        )
    }
    
    private var exercisesSection: some View {
        guard !recommendedExercises.isEmpty else { return AnyView(EmptyView()) }
        return AnyView(
            VStack(alignment: .leading, spacing: 8) {
                WikiSectionHeaderView(title: "Ejercicios Recomendados")
                ForEach(recommendedExercises, id: \.id) { exercise in
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
    
    private func aestheticSection(muscle: MuscleGroupEntity) -> some View {
        guard let aesthetic = muscle.aestheticImportance else { return AnyView(EmptyView()) }
        return AnyView(
            VStack(alignment: .leading, spacing: 8) {
                WikiSectionHeaderView(title: "Importancia Estética")
                Text(aesthetic)
                    .font(.system(size: 14, design: .serif))
                    .foregroundColor(.white.opacity(0.8))
                    .lineSpacing(4)
                    .padding(.leading, 12)
            }
            .listRowInsets(EdgeInsets())
            .listRowBackground(Color.black)
        )
    }
}

private func bodyPartColor(_ bodyPart: String?) -> Color {
    switch bodyPart {
    case "upper": return Color(hex: 0x1E88E5)
    case "lower": return Color(hex: 0x43A047)
    case "core": return Color(hex: 0xFFFF8F00)
    case "spine": return Color(hex: 0x9C27B0)
    default: return Color(hex: 0x757575)
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
