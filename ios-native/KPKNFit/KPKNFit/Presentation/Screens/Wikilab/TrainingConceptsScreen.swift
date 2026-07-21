import SwiftUI

// MARK: - Concepts List Screen

struct TrainingConceptsScreen: View {
    let onNavigateToConcept: (String) -> Void
    let onBack: () -> Void
    
    @State private var query: String = ""
    @State private var selectedCategory: ConceptCategory? = nil
    
    private var categories: [ConceptCategory] {
        ConceptCategory.allCases
    }
    
    private var filtered: [TrainingConcept] {
        let bySearch = searchConcepts(query)
        if let cat = selectedCategory {
            return bySearch.filter { $0.category == cat }
        }
        return bySearch
    }
    
    private var grouped: [ConceptCategory: [TrainingConcept]] {
        Dictionary(grouping: filtered) { $0.category }
            .sorted { $0.key.rawValue < $1.key.rawValue }
            .reduce(into: [ConceptCategory: [TrainingConcept]]()) { $0[$1.key] = $1.value }
    }
    
    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            
            List {
                headerSection
                searchSection
                categoryChipsSection
                
                let sortedGroups = grouped.sorted { $0.key.rawValue < $1.key.rawValue }
                ForEach(sortedGroups, id: \.key) { category, concepts in
                    Section {
                        ForEach(concepts) { concept in
                            ConceptListRowView(concept: concept)
                                .contentShape(Rectangle())
                                .onTapGesture { onNavigateToConcept(concept.id) }
                                .listRowInsets(EdgeInsets())
                                .listRowBackground(Color.black)
                                .listRowSeparator(.hidden)
                        }
                    } header: {
                        VStack(alignment: .leading, spacing: 4) {
                            Text(category.label.uppercased())
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
            Text("Conceptos Clave")
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
            Text("Glosario enciclopédico de principios de entrenamiento, biomecánica aplicada y metodologías de fuerza.")
                .font(.system(size: 14, design: .serif))
                .foregroundColor(.white.opacity(0.7))
            
            HStack(spacing: 8) {
                Image(systemName: "magnifyingglass")
                    .foregroundColor(.white.opacity(0.5))
                if query.isEmpty {
                    Text("Buscar concepto...")
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
    
    private var categoryChipsSection: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                Button(action: { selectedCategory = nil }) {
                    Text("Todos")
                        .font(.system(size: 12, weight: selectedCategory == nil ? .bold : .medium))
                        .padding(.horizontal, 12)
                        .padding(.vertical, 6)
                        .background(selectedCategory == nil ? Color.white.opacity(0.1) : Color.clear)
                        .overlay(RoundedRectangle(cornerRadius: 4).stroke(Color.white.opacity(0.2), lineWidth: 1))
                        .clipShape(RoundedRectangle(cornerRadius: 4))
                }
                .buttonStyle(.plain)
                .foregroundColor(selectedCategory == nil ? .white : .white.opacity(0.7))
                
                ForEach(categories, id: \.rawValue) { cat in
                    Button(action: { selectedCategory = selectedCategory == cat ? nil : cat }) {
                        HStack(spacing: 4) {
                            Circle()
                                .fill(cat.color)
                                .frame(width: 8, height: 8)
                            Text(cat.label)
                                .font(.system(size: 12, weight: selectedCategory == cat ? .bold : .medium))
                        }
                        .padding(.horizontal, 12)
                        .padding(.vertical, 6)
                        .background(selectedCategory == cat ? Color.white.opacity(0.1) : Color.clear)
                        .overlay(RoundedRectangle(cornerRadius: 4).stroke(Color.white.opacity(0.2), lineWidth: 1))
                        .clipShape(RoundedRectangle(cornerRadius: 4))
                    }
                    .buttonStyle(.plain)
                    .foregroundColor(selectedCategory == cat ? .white : .white.opacity(0.7))
                }
            }
            .padding(.vertical, 4)
        }
        .listRowInsets(EdgeInsets())
        .listRowBackground(Color.black)
    }
}

private struct ConceptListRowView: View {
    let concept: TrainingConcept
    
    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(concept.name)
                .font(.system(size: 16, weight: .bold, design: .serif))
                .foregroundColor(Color(hex: 0x29B6F6))
            
            Text(concept.shortDescription)
                .font(.system(size: 14, design: .serif))
                .foregroundColor(.white.opacity(0.8))
            
            Divider()
                .background(Color(hex: 0x1A1A1A))
                .padding(.top, 6)
        }
        .padding(.vertical, 12)
    }
}

// MARK: - Concept Detail Screen

struct ConceptDetailScreen: View {
    let conceptId: String
    let onNavigateToConcept: (String) -> Void
    let onBack: () -> Void
    
    private var concept: TrainingConcept? {
        TRAINING_CONCEPTS_DATABASE.first { $0.id == conceptId }
    }
    
    private var relatedConcepts: [TrainingConcept] {
        guard let c = concept else { return [] }
        return c.relatedConcepts.compactMap { id in
            TRAINING_CONCEPTS_DATABASE.first { $0.id == id }
        }
    }
    
    var body: some View {
        ZStack {
            Color.black.ignoresSafeArea()
            
            if let concept = concept {
                List {
                    headerSection(concept: concept)
                    shortDescriptionSection(concept: concept)
                    infoboxSection(concept: concept)
                    definitionSection(concept: concept)
                    
                    if !concept.keyPoints.isEmpty {
                        keyPointsSection(concept: concept)
                    }
                    
                    practicalApplicationSection(concept: concept)
                    
                    if !concept.examples.isEmpty {
                        examplesSection(concept: concept)
                    }
                    
                    if !concept.commonMistakes.isEmpty {
                        commonMistakesSection(concept: concept)
                    }
                    
                    if !relatedConcepts.isEmpty {
                        relatedConceptsSection
                    }
                    
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
                    Text("Concepto no encontrado")
                        .font(.system(.title2, design: .serif))
                        .foregroundColor(.white.opacity(0.5))
                }
            }
        }
        .navigationBarHidden(true)
    }
    
    private func headerSection(concept: TrainingConcept) -> some View {
        HStack(spacing: 8) {
            Button(action: onBack) {
                Image(systemName: "chevron.left")
                    .font(.title3)
                    .foregroundColor(.white)
            }
            Text(concept.name)
                .font(.system(size: 32, weight: .black, design: .serif))
                .foregroundColor(concept.category.color)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.vertical, 8)
        .listRowInsets(EdgeInsets())
        .listRowBackground(Color.black)
    }
    
    private func shortDescriptionSection(concept: TrainingConcept) -> some View {
        Text(concept.shortDescription)
            .font(.system(size: 14, design: .serif))
            .foregroundColor(.white.opacity(0.9))
            .lineSpacing(4)
            .listRowInsets(EdgeInsets())
            .listRowBackground(Color.black)
    }
    
    private func infoboxSection(concept: TrainingConcept) -> some View {
        WikiConceptInfoboxView(concept: concept)
            .listRowInsets(EdgeInsets())
            .listRowBackground(Color.black)
    }
    
    private func definitionSection(concept: TrainingConcept) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            WikiSectionHeaderView(title: "Definición")
            Text(concept.definition)
                .font(.system(size: 14, design: .serif))
                .foregroundColor(.white.opacity(0.8))
                .lineSpacing(4)
                .padding(.leading, 12)
        }
        .listRowInsets(EdgeInsets())
        .listRowBackground(Color.black)
    }
    
    private func keyPointsSection(concept: TrainingConcept) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            WikiSectionHeaderView(title: "Puntos Clave")
            ForEach(concept.keyPoints, id: \.self) { point in
                HStack(alignment: .top, spacing: 10) {
                    Circle()
                        .fill(Color(hex: 0xFFFF8F00))
                        .frame(width: 6, height: 6)
                        .padding(.top, 8)
                    Text(point)
                        .font(.system(size: 14, design: .serif))
                        .foregroundColor(.white.opacity(0.8))
                        .lineSpacing(4)
                }
                .padding(.leading, 12)
            }
        }
        .listRowInsets(EdgeInsets())
        .listRowBackground(Color.black)
    }
    
    private func practicalApplicationSection(concept: TrainingConcept) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            WikiSectionHeaderView(title: "Aplicación Práctica")
            Text(concept.practicalApplication)
                .font(.system(size: 14, design: .serif))
                .foregroundColor(.white.opacity(0.8))
                .lineSpacing(4)
                .padding(.leading, 12)
        }
        .listRowInsets(EdgeInsets())
        .listRowBackground(Color.black)
    }
    
    private func examplesSection(concept: TrainingConcept) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            WikiSectionHeaderView(title: "Ejemplos")
            ForEach(Array(concept.examples.enumerated()), id: \.offset) { i, example in
                HStack(alignment: .top, spacing: 8) {
                    Text("\(i + 1).")
                        .font(.system(size: 14, design: .serif))
                        .fontWeight(.bold)
                        .foregroundColor(Color(hex: 0x1E88E5))
                    Text(example)
                        .font(.system(size: 14, design: .serif))
                        .foregroundColor(.white.opacity(0.8))
                        .lineSpacing(4)
                }
                .padding(.leading, 12)
            }
        }
        .listRowInsets(EdgeInsets())
        .listRowBackground(Color.black)
    }
    
    private func commonMistakesSection(concept: TrainingConcept) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            WikiSectionHeaderView(title: "Errores Comunes")
            ForEach(concept.commonMistakes, id: \.self) { mistake in
                HStack(alignment: .top, spacing: 8) {
                    Image(systemName: "xmark")
                        .font(.system(size: 12))
                        .foregroundColor(Color(hex: 0xE53935))
                        .padding(.top, 2)
                    Text(mistake)
                        .font(.system(size: 14, design: .serif))
                        .foregroundColor(.white.opacity(0.8))
                        .lineSpacing(4)
                }
                .padding(.leading, 12)
            }
        }
        .listRowInsets(EdgeInsets())
        .listRowBackground(Color.black)
    }
    
    private var relatedConceptsSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            WikiSectionHeaderView(title: "Conceptos Relacionados")
            ForEach(relatedConcepts) { related in
                HStack(spacing: 10) {
                    Circle()
                        .fill(related.category.color)
                        .frame(width: 6, height: 6)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(related.name)
                            .font(.system(size: 14, design: .serif))
                            .foregroundColor(Color(hex: 0x29B6F6))
                            .fontWeight(.bold)
                        Text(related.category.label)
                            .font(.system(size: 13, design: .serif))
                            .foregroundColor(.white.opacity(0.5))
                    }
                }
                .padding(.leading, 12)
                .padding(.vertical, 4)
                .contentShape(Rectangle())
                .onTapGesture { onNavigateToConcept(related.id) }
            }
        }
        .listRowInsets(EdgeInsets())
        .listRowBackground(Color.black)
    }
}

// MARK: - Shared Components

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

private func WikiConceptInfoboxView(concept: TrainingConcept) -> some View {
    VStack(spacing: 8) {
        Text("Ficha Técnica Concepto")
            .font(.system(.title3, design: .serif))
            .fontWeight(.bold)
            .foregroundColor(.white)
            .frame(maxWidth: .infinity)
        Divider().background(Color(hex: 0x2C2C2C))
        
        InfoboxRowView(label: "Concepto", value: concept.name)
        InfoboxRowView(label: "Categoría", value: concept.category.label)
    }
    .padding(12)
    .background(Color(hex: 0x141414))
    .overlay(RoundedRectangle(cornerRadius: 4).stroke(Color(hex: 0x2C2C2C), lineWidth: 1))
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
