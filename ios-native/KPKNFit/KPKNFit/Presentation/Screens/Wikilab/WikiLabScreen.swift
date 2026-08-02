import SwiftUI

private struct MuscleCategory {
    let label: String
    let keywords: [String]
    let color: Color
}

private let CATEGORIES: [MuscleCategory] = [
    MuscleCategory(label: "Todos", keywords: [], color: Color(hex: 0x8A9099)),
    MuscleCategory(label: "Pecho", keywords: ["Pectorales"], color: Color(hex: 0x1E88E5)),
    MuscleCategory(label: "Espalda", keywords: ["Dorsales", "Trapecio", "Erectores Espinales"], color: Color(hex: 0x43A047)),
    MuscleCategory(label: "Hombros", keywords: ["Deltoides"], color: Color(hex: 0xFFFF8F00)),
    MuscleCategory(label: "Piernas", keywords: ["Cuádriceps", "Isquiosurales", "Glúteos", "Pantorrillas", "Aductores"], color: Color(hex: 0x9C27B0)),
    MuscleCategory(label: "Brazos", keywords: ["Bíceps", "Tríceps", "Antebrazo"], color: Color(hex: 0xE53935)),
    MuscleCategory(label: "Core", keywords: ["Core", "Abdomen"], color: Color(hex: 0x00ACC1)),
]

struct WikiLabScreen: View {
    let onCreateExercise: () -> Void
    let onOpenExercise: (String) -> Void
    let onBack: () -> Void
    
    @State private var query: String = ""
    @State private var selectedCategory: Int = 0
    
    private var customExercises: [ExerciseMuscleInfo] { CustomExerciseRepository.shared.customExercises }
    
    private var exerciseCatalog: [ExerciseMuscleInfo] {
        let all = catalogExerciseList + customExercises
        var dict = [String: ExerciseMuscleInfo]()
        for ex in all { dict[ex.id.lowercased()] = ex }
        return Array(dict.values)
    }
    
    private var filtered: [ExerciseMuscleInfo] {
        let cat = CATEGORIES[selectedCategory]
        let matches = exerciseCatalog.filter { ex in
            let canonicalInvolved = collapseInvolvedMusclesToCanonical(ex.involvedMuscles)
            let catMatch = cat.keywords.isEmpty || ex.involvedMuscles.contains { m in
                cat.keywords.contains { kw in
                    canonicalMuscleDisplayName(m.muscle, emphasis: m.emphasis).localizedCaseInsensitiveContains(kw)
                }
            }
            let textMatch: Bool
            if query.trimmingCharacters(in: .whitespaces).isEmpty {
                textMatch = true
            } else {
                textMatch = calculateSearchScore(ex, query) > 0 ||
                    canonicalInvolved.contains { $0.muscle.localizedCaseInsensitiveContains(query) }
            }
            return catMatch && textMatch
        }
        let sorted: [ExerciseMuscleInfo]
        if query.trimmingCharacters(in: .whitespaces).isEmpty {
            sorted = matches.sorted { $0.name < $1.name }
        } else {
            sorted = matches.sorted { a, b in
                let sa = calculateSearchScore(a, query)
                let sb = calculateSearchScore(b, query)
                if sa != sb { return sa > sb }
                let da = abs(a.name.count - query.trimmingCharacters(in: .whitespaces).count)
                let db = abs(b.name.count - query.trimmingCharacters(in: .whitespaces).count)
                if da != db { return da < db }
                return a.name < b.name
            }
        }
        return deduplicateCatalogVisualResults(sorted)
    }
    
    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            
            ScrollViewReader { proxy in
                List {
                    headerSection
                    subtitleSection
                    searchBarSection
                    categoryFilterSection
                    resultsCountSection
                    exerciseListSection
                    Color.clear.frame(height: 80).id("bottom-spacer")
                }
                .listStyle(.plain)
                .background(Color.black)
                .scrollContentBackground(.hidden)
                .onChange(of: query) { _ in
                    withAnimation { proxy.scrollTo("top", anchor: .top) }
                }
                .onChange(of: selectedCategory) { _ in
                    withAnimation { proxy.scrollTo("top", anchor: .top) }
                }
            }
            
            VStack {
                Spacer()
                HStack {
                    Spacer()
                    Button(action: onCreateExercise) {
                        Image(systemName: "plus")
                            .font(.title2.weight(.semibold))
                            .foregroundColor(.black)
                            .frame(width: 56, height: 56)
                            .background(AppColors.neonCyan)
                            .clipShape(RoundedRectangle(cornerRadius: 8))
                    }
                    .padding(16)
                }
            }
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
            Text("Ejercicios")
                .font(.system(size: 32, weight: .black, design: .serif))
                .foregroundColor(.white)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.vertical, 8)
        .listRowInsets(EdgeInsets())
        .listRowBackground(Color.black)
        .id("top")
    }
    
    private var subtitleSection: some View {
        Text("Índice de ejercicios y guía anatómica. Selecciona un elemento para ver su nivel, músculos implicados, equipamiento y fatiga.")
            .font(.system(size: 14, design: .serif))
            .foregroundColor(.white.opacity(0.7))
            .listRowInsets(EdgeInsets())
            .listRowBackground(Color.black)
    }
    
    private var searchBarSection: some View {
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
        .listRowInsets(EdgeInsets())
        .listRowBackground(Color.black)
        .padding(.vertical, 4)
    }
    
    private var categoryFilterSection: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(CATEGORIES.indices, id: \.self) { idx in
                    let cat = CATEGORIES[idx]
                    let isSelected = selectedCategory == idx
                    Button(action: { selectedCategory = idx }) {
                        HStack(spacing: 4) {
                            if idx > 0 {
                                Circle()
                                    .fill(cat.color)
                                    .frame(width: 6, height: 6)
                            }
                            Text(cat.label)
                                .font(.system(size: 12, weight: isSelected ? .black : .semibold))
                        }
                        .padding(.horizontal, 12)
                        .padding(.vertical, 6)
                        .background(isSelected ? Color.white.opacity(0.1) : Color.clear)
                        .overlay(RoundedRectangle(cornerRadius: 4).stroke(Color.white.opacity(0.2), lineWidth: 1))
                        .clipShape(RoundedRectangle(cornerRadius: 4))
                    }
                    .buttonStyle(.plain)
                    .foregroundColor(isSelected ? .white : .white.opacity(0.7))
                }
            }
            .padding(.vertical, 4)
        }
        .listRowInsets(EdgeInsets())
        .listRowBackground(Color.black)
    }
    
    private var resultsCountSection: some View {
        Text("\(filtered.count) artículos")
            .font(.system(size: 11, design: .serif))
            .foregroundColor(.white.opacity(0.5))
            .listRowInsets(EdgeInsets())
            .listRowBackground(Color.black)
            .padding(.vertical, 4)
    }
    
    private var exerciseListSection: some View {
        ForEach(filtered, id: \.id) { exercise in
            ExerciseCardView(exercise: exercise)
                .contentShape(Rectangle())
                .onTapGesture { onOpenExercise(exercise.id) }
                .listRowInsets(EdgeInsets())
                .listRowBackground(Color.black)
                .listRowSeparator(.hidden)
        }
    }
}

private struct ExerciseCardView: View {
    let exercise: ExerciseMuscleInfo
    
    private var canonicalInvolved: [InvolvedMuscle] {
        collapseInvolvedMusclesToCanonical(exercise.involvedMuscles)
    }
    
    private var primaryMuscles: [InvolvedMuscle] {
        canonicalInvolved.filter { $0.role == .PRIMARY }
    }
    
    private var secondaryCount: Int {
        canonicalInvolved.filter { $0.role != .PRIMARY }.count
    }
    
    private var equipment: String {
        exercise.equipment ?? "Peso Corporal"
    }
    
    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(exercise.name)
                .font(.system(size: 16, weight: .bold, design: .serif))
                .foregroundColor(Color(hex: 0x29B6F6))
            
            Text("Músculos principales: " + primaryMuscles.map(\.muscle).joined(separator: ", ") + (secondaryCount > 0 ? " (+\(secondaryCount) secundarios)" : ""))
                .font(.system(size: 14, design: .serif))
                .foregroundColor(.white.opacity(0.8))
            
            Text("Equipamiento: \(equipment)")
                .font(.system(size: 11, design: .serif))
                .foregroundColor(.white.opacity(0.5))
            
            Divider()
                .background(Color(hex: 0x1A1A1A))
                .padding(.top, 6)
        }
        .padding(.vertical, 12)
    }
}
