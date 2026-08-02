import SwiftUI

// MARK: - Lens Colors

private let LENS_EXERCISE = Color(hex: 0xC27A7A)
private let LENS_MUSCLE = Color(hex: 0xA07AB0)
private let LENS_JOINT = Color(hex: 0x7A9AB8)
private let LENS_PATTERN = Color(hex: 0x7AAA7A)
private let LENS_CHAIN = Color(hex: 0xC0A870)
private let LENS_CONCEPT = Color(hex: 0x7A9CA8)

// MARK: - Lens Types

private struct WikiLensConcept: Identifiable {
    let key: String
    let id: String
    let label: String
    let type: WikiLensConceptType
    let color: Color
    var id: String { key }
}

private enum WikiLensConceptType {
    case EXERCISE, MUSCLE, JOINT, PATTERN, CHAIN, CONCEPT
}

// MARK: - Main Screen

struct WikiLabHomeScreen: View {
    let searchQuery: String
    let onSearchQueryChange: (String) -> Void
    let onNavigateToExercises: () -> Void
    let onNavigateToMuscleAnatomy: () -> Void
    let onNavigateToJoints: () -> Void
    let onNavigateToMovementPatterns: () -> Void
    let onNavigateToBiomechanics: () -> Void
    let onNavigateToConcepts: () -> Void
    let onNavigateToExercise: (String) -> Void
    let onNavigateToMuscle: (String) -> Void
    let onNavigateToChain: (String) -> Void
    let onNavigateToConcept: (String) -> Void
    let onNavigateToJoint: (String) -> Void
    let onNavigateToPattern: (String) -> Void
    
    @State private var lensScale: CGFloat = 1.0
    @State private var lensOffset: CGSize = .zero
    @State private var lastLensScale: CGFloat = 1.0
    @State private var lastLensOffset: CGSize = .zero
    
    private var isSearching: Bool { !searchQuery.trimmingCharacters(in: .whitespaces).isEmpty }
    
    private var canonicalMuscles: [(MuscleGroupEntity, String)] {
        WikiLabRepository.shared.muscles.compactMap { m in
            guard let canonicalId = canonicalWikiLabMuscleIdFromEntityId(m.id) else { return nil }
            guard let canonical = WikiLabRepository.shared.getMuscleById(id: canonicalId) else { return nil }
            return (canonical, canonicalId)
        }
    }
    
    private var searchResults: [SearchResult] {
        guard isSearching else { return [] }
        let q = searchQuery.lowercased()
        var results: [SearchResult] = []
        
        let matchedExercises = catalogExerciseList
            .filter { calculateSearchScore($0, searchQuery) > 0 }
            .sorted { a, b in
                let sa = calculateSearchScore(a, searchQuery)
                let sb = calculateSearchScore(b, searchQuery)
                if sa != sb { return sa > sb }
                return abs(a.name.count - q.count) < abs(b.name.count - q.count)
            }
            .prefix(5)
        for ex in matchedExercises {
            results.append(SearchResult(id: ex.id, name: ex.name, subtitle: "Ejercicio", type: .EXERCISE, color: Color(hex: 0xE53935)))
        }
        
        let matchedMuscles = WikiLabRepository.shared.muscles
            .filter { $0.name.lowercased().contains(q) || $0.description.lowercased().contains(q) }
            .compactMap { m -> MuscleGroupEntity? in
                guard let cid = canonicalWikiLabMuscleIdFromEntityId(m.id) else { return nil }
                return WikiLabRepository.shared.getMuscleById(id: cid)
            }
            .reduce(into: [String: MuscleGroupEntity]()) { $0[$1.id] = $1 }
            .values
            .prefix(5)
        for m in matchedMuscles {
            results.append(SearchResult(id: m.id, name: m.name, subtitle: "Músculo · \(WikiLabRepository.shared.getBodyPartLabel(bodyPart: m.bodyPart))", type: .MUSCLE, color: Color(hex: 0x9C27B0)))
        }
        
        let matchedJoints = WikiLabRepository.shared.joints
            .filter { $0.name.lowercased().contains(q) || $0.description.lowercased().contains(q) }
            .prefix(4)
        for j in matchedJoints {
            results.append(SearchResult(id: j.id, name: j.name, subtitle: "Articulación · \(WikiLabRepository.shared.getJointTypeLabel(type: j.type))", type: .JOINT, color: Color(hex: 0x1E88E5)))
        }
        
        let matchedPatterns = WikiLabRepository.shared.patterns
            .filter { $0.name.lowercased().contains(q) || $0.description.lowercased().contains(q) }
            .prefix(4)
        for p in matchedPatterns {
            results.append(SearchResult(id: p.id, name: p.name, subtitle: "Patrón de Movimiento", type: .PATTERN, color: Color(hex: 0x43A047)))
        }
        
        let matchedConcepts = searchConcepts(query: searchQuery).prefix(5)
        for c in matchedConcepts {
            results.append(SearchResult(id: c.id, name: c.name, subtitle: "Concepto · \(c.category.label)", type: .CONCEPT, color: c.category.color))
        }
        
        return results
    }
    
    private var lensConcepts: [WikiLensConcept] {
        var list: [WikiLensConcept] = []
        var idx = 0
        
        for ex in catalogExerciseList.shuffled().prefix(12) {
            list.append(WikiLensConcept(key: "ex\(idx)", id: ex.id, label: String(ex.name.prefix(12)), type: .EXERCISE, color: LENS_EXERCISE))
            idx += 1
        }
        for (muscle, _) in canonicalMuscles.prefix(10) {
            list.append(WikiLensConcept(key: "mu\(idx)", id: muscle.id, label: String(muscle.name.prefix(14)), type: .MUSCLE, color: LENS_MUSCLE))
            idx += 1
        }
        for j in WikiLabRepository.shared.joints.shuffled().prefix(8) {
            list.append(WikiLensConcept(key: "jo\(idx)", id: j.id, label: String(j.name.prefix(12)), type: .JOINT, color: LENS_JOINT))
            idx += 1
        }
        for p in WikiLabRepository.shared.patterns.shuffled().prefix(6) {
            list.append(WikiLensConcept(key: "pa\(idx)", id: p.id, label: String(p.name.prefix(12)), type: .PATTERN, color: LENS_PATTERN))
            idx += 1
        }
        for ch in WikiLabRepository.shared.chains {
            list.append(WikiLensConcept(key: "ch\(idx)", id: ch.id, label: String(ch.name.prefix(14)), type: .CHAIN, color: chainColor(ch.id)))
            idx += 1
        }
        let conceptShortNames: [String: String] = [
            "volumen-entrenamiento": "Volumen",
            "intensidad": "Intensidad",
            "rir": "RIR",
            "fallo-muscular": "Fallo",
            "tension-mecanica": "Tensión",
            "sobrecarga-progresiva": "Sobrecarga",
            "rom": "ROM",
            "deload": "Deload",
        ]
        for c in TRAINING_CONCEPTS_DATABASE.shuffled().prefix(8) {
            let label = conceptShortNames[c.id] ?? String(c.name.prefix(10))
            list.append(WikiLensConcept(key: "co\(idx)", id: c.id, label: label, type: .CONCEPT, color: LENS_CONCEPT))
            idx += 1
        }
        return list
    }
    
    private var nodeCoords: [String: CGPoint] {
        let count = lensConcepts.count
        guard count > 0 else { return [:] }
        let ringDefs: [(CGFloat, Int?)] = [
            (0.18, 4), (0.30, 6), (0.42, 10), (0.54, 14), (0.66, nil),
        ]
        var coords: [String: CGPoint] = [:]
        var idx = 0
        let centerDp: CGFloat = 225
        let maxRadiusDp: CGFloat = 185
        
        for (ringIndex, (radiusFrac, maxCount)) in ringDefs.enumerated() {
            let effectiveCount: Int
            if let mc = maxCount { effectiveCount = min(mc, count - idx) }
            else { effectiveCount = count - idx }
            guard effectiveCount > 0 else { continue }
            let radius = maxRadiusDp * radiusFrac
            let angleStep = 360.0 / Double(effectiveCount)
            let angleOffset = Double(ringIndex) * 27.0
            for i in 0..<effectiveCount {
                let theta = (angleOffset + Double(i) * angleStep) * .pi / 180.0
                coords[lensConcepts[idx].key] = CGPoint(
                    x: centerDp + radius * CGFloat(cos(theta)),
                    y: centerDp + radius * CGFloat(sin(theta))
                )
                idx += 1
            }
        }
        return coords
    }
    
    var body: some View {
        ScrollViewReader { proxy in
            List {
                // Lens
                Section {
                    conceptLensSection
                        .id("lens")
                } header: {
                    Color.clear.frame(height: 1)
                }
                
                // Stats badges
                Section {
                    compactStatsSection
                } header: {
                    Color.clear.frame(height: 1)
                }
                
                if isSearching {
                    searchResultsSection
                } else {
                    exploreSection
                    featuredConceptsSection
                }
                
                Color.clear.frame(height: 180)
            }
            .listStyle(.plain)
            .background(Color.black)
            .scrollContentBackground(.hidden)
            .onChange(of: searchQuery) { _ in
                withAnimation { proxy.scrollTo("lens", anchor: .top) }
            }
        }
    }
    
    // MARK: - Concept Lens
    
    private var conceptLensSection: some View {
        let lensSize: CGFloat = min(UIScreen.main.bounds.width - 24, 390)
        let contentSize: CGFloat = lensSize * 1.25
        let outerHeight = lensSize + 44
        let arcMargin: CGFloat = 10
        let radiusPx = lensSize / 2
        let contentCenter = contentSize / 2
        let maxRadius = contentCenter * 0.82
        
        return ZStack {
            // Outer container
            VStack(spacing: 0) {
                // Title arc canvas
                Canvas { context, size in
                    let cvW = size.width
                    let cvH = size.height
                    let circleCx = cvW / 2
                    let circleCy = cvH - radiusPx
                    let arcR = radiusPx + arcMargin
                    
                    var text = Text("Enciclopedia")
                        .font(.system(size: cvW * 0.18, weight: .bold, design: .serif))
                        .foregroundColor(.white)
                    let resolved = context.resolve(text)
                    let textSize = resolved.measure(in: CGSize(width: .infinity, height: .infinity))
                    
                    let arcAngle = 110.0
                    let arcStartAngle = 215.0
                    let arcEndAngle = arcStartAngle + arcAngle
                    let arcMidAngle = (arcStartAngle + arcEndAngle) / 2 * .pi / 180
                    
                    let textX = circleCx + arcR * cos(CGFloat(arcMidAngle))
                    let textY = circleCy + arcR * sin(CGFloat(arcMidAngle))
                    
                    context.draw(resolved, at: CGPoint(x: textX, y: textY - textSize.height / 2))
                }
                .frame(width: lensSize, height: 44)
                
                // Circle with lens
                ZStack {
                    Canvas { context, size in
                        let cvW = size.width
                        let cvH = size.height
                        
                        // Background
                        context.fill(Path(CGRect(origin: .zero, size: size)), with: .color(Color(hex: 0x040404)))
                        
                        // Nodes
                        for concept in lensConcepts {
                            guard let coord = nodeCoords[concept.key] else { continue }
                            let dx = coord.x - contentCenter
                            let dy = coord.y - contentCenter
                            let rx = dx * lensScale + lensOffset.width
                            let ry = dy * lensScale + lensOffset.height
                            let dist = sqrt(rx * rx + ry * ry)
                            let progress = min(dist / maxRadius, 1.0)
                            let lensEffect = 1.0 - CGFloat(progress)
                            let scaleX = 0.55 + lensEffect * 0.70
                            let scaleY = 0.55 + lensEffect * 0.70
                            let alpha = 0.15 + lensEffect * 0.85
                            
                            let drawX = coord.x * lensScale + lensOffset.width
                            let drawY = coord.y * lensScale + lensOffset.height
                            
                            var text = Text(concept.label)
                                .font(.system(size: 10 * scaleX, weight: .medium, design: .serif))
                                .foregroundColor(concept.color.opacity(alpha))
                            let resolved = context.resolve(text)
                            let textSize = resolved.measure(in: CGSize(width: .infinity, height: .infinity))
                            context.draw(resolved, at: CGPoint(x: drawX, y: drawY))
                        }
                    }
                    .frame(width: lensSize, height: lensSize)
                    .clipShape(Circle())
                    .overlay(Circle().stroke(Color(hex: 0x161616), lineWidth: 1.5))
                    .gesture(
                        MagnificationGesture()
                            .onChanged { value in
                                let newScale = lastLensScale * value
                                lensScale = min(max(newScale, 0.85), 2.6)
                            }
                            .onEnded { _ in lastLensScale = lensScale }
                    )
                    .simultaneousGesture(
                        DragGesture()
                            .onChanged { value in
                                let panLimit = max((lensScale - 1.0), 0) * lensSize * 0.40 + 40
                                let newWidth = lastLensOffset.width + value.translation.width
                                let newHeight = lastLensOffset.height + value.translation.height
                                lensOffset = CGSize(
                                    width: min(max(newWidth, -panLimit), panLimit),
                                    height: min(max(newHeight, -panLimit), panLimit)
                                )
                            }
                            .onEnded { _ in lastLensOffset = lensOffset }
                    )
                    
                    // Radial gradient vignette
                    let gradient = RadialGradient(
                        colors: [
                            .clear,
                            .clear,
                            Color.black.opacity(0.25),
                            Color.black.opacity(0.70),
                            Color.black,
                        ],
                        center: .center,
                        startRadius: 0,
                        endRadius: radiusPx * 0.88
                    )
                    Circle()
                        .fill(gradient)
                        .frame(width: lensSize, height: lensSize)
                        .allowsHitTesting(false)
                }
                .frame(width: lensSize, height: lensSize)
            }
            .frame(width: lensSize, height: outerHeight)
            .position(x: UIScreen.main.bounds.width / 2 - 12, y: outerHeight / 2)
        }
        .frame(height: outerHeight + 20)
        .padding(.top, 20)
        .listRowInsets(EdgeInsets())
        .listRowBackground(Color.black)
    }
    
    // MARK: - Compact Stats
    
    private var compactStatsSection: some View {
        HStack(spacing: 12) {
            CompactStatView(value: "\(catalogExerciseList.count)", label: "Ejercicios")
            Text("•").foregroundColor(.white.opacity(0.25))
            CompactStatView(value: "\(WikiLabRepository.shared.muscles.count)", label: "Músculos")
            Text("•").foregroundColor(.white.opacity(0.25))
            CompactStatView(value: "\(TRAINING_CONCEPTS_DATABASE.count)", label: "Conceptos")
        }
        .frame(maxWidth: .infinity)
        .padding(.horizontal, 20)
        .padding(.vertical, 12)
        .listRowInsets(EdgeInsets())
        .listRowBackground(Color.black)
    }
    
    // MARK: - Search Results
    
    private var searchResultsSection: some View {
        Group {
            Text("\(searchResults.count) resultados para \"\(searchQuery)\"")
                .font(.system(size: 12))
                .foregroundColor(.white.opacity(0.7))
                .padding(.horizontal, 20)
                .padding(.vertical, 8)
                .listRowInsets(EdgeInsets())
                .listRowBackground(Color.black)
            
            if searchResults.isEmpty {
                VStack(spacing: 8) {
                    Image(systemName: "magnifyingglass")
                        .font(.system(size: 48))
                        .foregroundColor(.white.opacity(0.5))
                    Text("Sin resultados")
                        .font(.system(.title2))
                        .foregroundColor(.white.opacity(0.7))
                }
                .frame(maxWidth: .infinity)
                .padding(48)
                .listRowInsets(EdgeInsets())
                .listRowBackground(Color.black)
            } else {
                ForEach(searchResults, id: \.id) { result in
                    SearchResultCardView(result: result)
                        .contentShape(Rectangle())
                        .onTapGesture {
                            switch result.type {
                            case .EXERCISE: onNavigateToExercise(result.id)
                            case .MUSCLE: onNavigateToMuscle(result.id)
                            case .JOINT: onNavigateToJoint(result.id)
                            case .PATTERN: onNavigateToPattern(result.id)
                            case .CONCEPT: onNavigateToConcept(result.id)
                            }
                            onSearchQueryChange("")
                        }
                        .listRowInsets(EdgeInsets())
                        .listRowBackground(Color.black)
                        .listRowSeparator(.hidden)
                }
            }
        }
    }
    
    // MARK: - Explore Section
    
    private var exploreSection: some View {
        Group {
            SectionLabelView("EXPLORAR")
                .padding(.horizontal, 20)
                .padding(.top, 8)
                .padding(.bottom, 8)
                .listRowInsets(EdgeInsets())
                .listRowBackground(Color.black)
            
            VStack(spacing: 10) {
                HStack(spacing: 10) {
                    WikiSectionTileView(title: "Ejercicios", subtitle: "\(catalogExerciseList.count) catalogados", onClick: onNavigateToExercises)
                    WikiSectionTileView(title: "Atlas Anatómico", subtitle: "\(WikiLabRepository.shared.muscles.count) músculos", onClick: onNavigateToMuscleAnatomy)
                }
                HStack(spacing: 10) {
                    WikiSectionTileView(title: "Articulaciones", subtitle: "\(WikiLabRepository.shared.joints.count) principales", onClick: onNavigateToJoints)
                    WikiSectionTileView(title: "Patrones de Fuerza", subtitle: "\(WikiLabRepository.shared.patterns.count) patrones", onClick: onNavigateToMovementPatterns)
                }
                HStack(spacing: 10) {
                    WikiSectionTileView(title: "Biomecánica", subtitle: "Análisis mecánico", onClick: onNavigateToBiomechanics)
                    WikiSectionTileView(title: "Conceptos Clave", subtitle: "\(TRAINING_CONCEPTS_DATABASE.count) lecciones", onClick: onNavigateToConcepts)
                }
            }
            .padding(.horizontal, 16)
            .listRowInsets(EdgeInsets())
            .listRowBackground(Color.black)
        }
    }
    
    // MARK: - Featured Concepts
    
    private var featuredConceptsSection: some View {
        let featured = TRAINING_CONCEPTS_DATABASE.filter {
            ["volumen-entrenamiento", "tension-mecanica", "rir", "sobrecarga-progresiva", "rom", "fallo-muscular"].contains($0.id)
        }
        return Group {
            SectionLabelView("CONCEPTOS DESTACADOS")
                .padding(.horizontal, 20)
                .padding(.top, 24)
                .padding(.bottom, 8)
                .listRowInsets(EdgeInsets())
                .listRowBackground(Color.black)
            
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 10) {
                    ForEach(featured) { concept in
                        FeaturedConceptCardView(name: concept.name, shortDesc: concept.shortDescription)
                            .onTapGesture { onNavigateToConcept(concept.id) }
                    }
                }
                .padding(.horizontal, 16)
            }
            .listRowInsets(EdgeInsets())
            .listRowBackground(Color.black)
        }
    }
}

// MARK: - Subcomponents

private func SectionLabelView(_ text: String) -> some View {
    Text(text)
        .font(.system(size: 11, weight: .black, design: .serif))
        .tracking(1)
        .foregroundColor(.white.opacity(0.8))
}

private struct CompactStatView: View {
    let value: String
    let label: String
    
    var body: some View {
        VStack(spacing: 0) {
            Text(value)
                .font(.system(size: 13, weight: .bold, design: .serif))
                .foregroundColor(.white)
            Text(label)
                .font(.system(size: 9, weight: .semibold))
                .tracking(1)
                .foregroundColor(.white.opacity(0.5))
        }
        .padding(.horizontal, 2)
    }
}

private struct WikiSectionTileView: View {
    let title: String
    let subtitle: String
    let onClick: () -> Void
    
    var body: some View {
        Button(action: onClick) {
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.system(.title3, design: .serif))
                    .fontWeight(.bold)
                    .foregroundColor(.white)
                    .lineLimit(1)
                    .minimumScaleFactor(0.7)
                Text(subtitle)
                    .font(.system(size: 11))
                    .foregroundColor(.white.opacity(0.45))
                    .lineLimit(1)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
            .padding(.horizontal, 14)
            .padding(.vertical, 12)
            .background(Color(hex: 0x121212))
            .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color(hex: 0x1E1E1E), lineWidth: 1))
            .clipShape(RoundedRectangle(cornerRadius: 8))
        }
        .buttonStyle(.plain)
        .frame(height: 78)
    }
}

private struct FeaturedConceptCardView: View {
    let name: String
    let shortDesc: String
    
    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("CONCEPTO DESTACADO")
                .font(.system(size: 9, weight: .bold))
                .tracking(1)
                .foregroundColor(.white.opacity(0.45))
            Text(name)
                .font(.system(.subheadline, design: .serif))
                .fontWeight(.bold)
                .foregroundColor(.white)
                .lineLimit(1)
                .minimumScaleFactor(0.7)
            Text(shortDesc)
                .font(.system(size: 11))
                .foregroundColor(.white.opacity(0.55))
                .lineSpacing(2)
                .lineLimit(2)
        }
        .frame(width: 220, alignment: .leading)
        .padding(14)
        .background(Color(hex: 0x121212))
        .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color(hex: 0x1E1E1E), lineWidth: 1))
        .clipShape(RoundedRectangle(cornerRadius: 8))
    }
}

// MARK: - Search Types

private enum SearchResultType {
    case EXERCISE, MUSCLE, JOINT, PATTERN, CONCEPT
}

private struct SearchResult: Identifiable {
    let id: String
    let name: String
    let subtitle: String
    let type: SearchResultType
    let color: Color
}

private struct SearchResultCardView: View {
    let result: SearchResult
    
    var body: some View {
        HStack(spacing: 12) {
            ZStack {
                RoundedRectangle(cornerRadius: 10)
                    .fill(result.color.opacity(0.12))
                    .frame(width: 36, height: 36)
                Image(systemName: iconName(for: result.type))
                    .font(.system(size: 14))
                    .foregroundColor(result.color)
            }
            
            VStack(alignment: .leading, spacing: 2) {
                Text(result.name)
                    .font(.system(.subheadline))
                    .fontWeight(.bold)
                    .foregroundColor(.white)
                    .lineLimit(1)
                Text(result.subtitle)
                    .font(.system(size: 11))
                    .foregroundColor(result.color)
            }
            
            Spacer()
            
            Image(systemName: "chevron.right")
                .font(.system(size: 12))
                .foregroundColor(result.color.opacity(0.5))
        }
        .padding(12)
        .background(Color(hex: 0x1A1A1A))
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .padding(.horizontal, 16)
        .padding(.vertical, 3)
    }
    
    private func iconName(for type: SearchResultType) -> String {
        switch type {
        case .EXERCISE: return "dumbbell"
        case .MUSCLE: return "figure.strengthtraining.functional"
        case .JOINT: return "point.3.connected.trianglepath.dotted"
        case .PATTERN: return "arrow.triangle.2.circlepath"
        case .CONCEPT: return "book"
        }
    }
}

// MARK: - Helper

private func chainColor(_ id: String) -> Color {
    switch id {
    case "tren-superior": return Color(hex: 0x7A9AB8)
    case "tren-inferior": return Color(hex: 0x7AAA7A)
    case "core": return Color(hex: 0xC0A870)
    case "cadena-anterior": return Color(hex: 0xC27A7A)
    case "cadena-posterior": return Color(hex: 0xA07AB0)
    default: return Color(hex: 0x7A7A7A)
    }
}
