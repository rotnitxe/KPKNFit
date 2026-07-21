import Foundation

struct SplitTemplateDayGroup {
    let splitId: String
    let splitName: String
    let dayIndex: Int
    let dayLabel: String
    let templates: [SessionTemplate]
    let estimatedMuscleVolume: [String: Double]
    let warnings: [String]
}

struct FocusTemplateGroup {
    let category: SessionTemplateFocusCategory
    let templates: [SessionTemplate]
}

struct UserTemplateGroup {
    let templates: [SessionTemplate]
}

enum SessionTemplateCatalogPolicy {

    static let weeklyVolumeRanges: [String: ClosedRange<Double>] = [
        "Pectorales": 10.0...14.0,
        "Dorsales": 10.0...14.0,
        "Trapecio": 6.0...10.0,
        "Deltoides": 8.0...12.0,
        "Bíceps": 6.0...10.0,
        "Tríceps": 6.0...10.0,
        "Cuádriceps": 10.0...14.0,
        "Isquiosurales": 8.0...12.0,
        "Glúteos": 8.0...14.0,
        "Pantorrillas": 6.0...12.0,
        "Aductores": 4.0...8.0,
        "Abdomen": 4.0...10.0,
        "Core": 4.0...8.0,
        "Erectores Espinales": 3.0...6.0,
    ]

    static func templatesForSplit(
        _ split: SplitTemplate,
        templates: [SessionTemplate],
        exerciseIndex: [String: ExerciseMuscleInfo] = [:]
    ) -> [SplitTemplateDayGroup] {
        guard split.id != "custom" else { return [] }
        var groups: [SplitTemplateDayGroup] = []

        for (index, dayLabel) in split.pattern.enumerated() {
            if dayLabel.caseInsensitiveCompare("Descanso") == .orderedSame { continue }
            let dayTemplates = templatesForSplitDay(splitId: split.id, dayLabel: dayLabel, templates: templates)
            let representative = dayTemplates.first
            let estimatedVol: [String: Double]
            if let rep = representative, !exerciseIndex.isEmpty {
                estimatedVol = calculateSessionMuscleVolume(session: rep.session, exerciseIndex: exerciseIndex)
            } else {
                estimatedVol = [:]
            }

            var warnings: [String] = []
            if let rep = representative, !exerciseIndex.isEmpty {
                let drain = evaluateTemplateRings(template: rep, exerciseIndex: exerciseIndex)
                let isPl = isPowerliftingTemplate(rep)
                let maxCns = isPl ? 45 : 35
                let maxMuscular = isPl ? 50 : 45
                let maxSpinal = isPl ? 40 : 30
                if drain.cns > maxCns { warnings.append("Fatiga SNC elevada (\(drain.cns)% > \(maxCns)%)") }
                if drain.muscular > maxMuscular { warnings.append("Fatiga Muscular elevada (\(drain.muscular)% > \(maxMuscular)%)") }
                if drain.spinal > maxSpinal { warnings.append("Carga axial/espinal elevada (\(drain.spinal)% > \(maxSpinal)%)") }
            }

            groups.append(SplitTemplateDayGroup(
                splitId: split.id, splitName: split.name,
                dayIndex: index, dayLabel: dayLabel,
                templates: dayTemplates.sorted { $0.sortOrder < $1.sortOrder },
                estimatedMuscleVolume: estimatedVol, warnings: warnings
            ))
        }
        return groups
    }

    static func independentTemplateGroups(_ templates: [SessionTemplate]) -> [FocusTemplateGroup] {
        let independent = templates.filter { $0.splitIds.isEmpty && $0.focusCategory != nil }
        return Dictionary(grouping: independent) { $0.focusCategory! }
            .map { FocusTemplateGroup(category: $0.key, templates: $0.value.sorted { $0.sortOrder < $1.sortOrder }) }
            .sorted { $0.category.rawValue < $1.category.rawValue }
    }

    static func userTemplateGroup(_ templates: [SessionTemplate]) -> UserTemplateGroup {
        UserTemplateGroup(templates: templates
            .filter { $0.sourceType == .user && !$0.isArchived }
            .sorted { $0.name.lowercased() < $1.name.lowercased() })
    }

    static func templatesForSplitDay(splitId: String, dayLabel: String, templates: [SessionTemplate]) -> [SessionTemplate] {
        let normalizedCandidates = candidateLabelsFor(dayLabel)
        let exact = templates.filter { $0.splitIds.contains(splitId) && $0.splitDayLabels.contains { $0.caseInsensitiveCompare(dayLabel) == .orderedSame } }
        let sameSplitArchetype = templates.filter { $0.splitIds.contains(splitId) && $0.splitDayLabels.contains { normalizedCandidates.contains($0.normalizedLabel()) } }
        let sharedArchetype = templates.filter { !$0.splitIds.isEmpty && $0.splitDayLabels.contains { normalizedCandidates.contains($0.normalizedLabel()) } }
        let independentArchetype = templates.filter { $0.splitIds.isEmpty && $0.focusCategory != nil && focusCategoriesFor(dayLabel).contains($0.focusCategory!) }

        return (exact.map { ($0, 1) } + sameSplitArchetype.map { ($0, 2) } + sharedArchetype.map { ($0, 3) } + independentArchetype.map { ($0, 4) })
            .uniqued(by: { $0.0.id })
            .sorted { $0.1 < $1.1 || ($0.1 == $1.1 && $0.0.sortOrder < $1.0.sortOrder) }
            .map { $0.0 }
    }

    static func calculateSessionMuscleVolume(session: Session, exerciseIndex: [String: ExerciseMuscleInfo]) -> [String: Double] {
        let entries = VolumeCalculator.calculateUnifiedMuscleVolume(sessions: [session], allExerciseInfo: Array(exerciseIndex.values))
        return Dictionary(uniqueKeysWithValues: entries.map { ($0.muscleName, $0.displayVolume) })
    }

    static func evaluateTemplateRings(template: SessionTemplate, exerciseIndex: [String: ExerciseMuscleInfo]) -> PredictedDrain {
        (try? AugeFatigueEngine.calculateAdjustedPredictedDrain(session: template.session, exerciseIndex: exerciseIndex, settings: Settings())) ?? PredictedDrain(cns: 100, muscular: 100, spinal: 100)
    }

    static func isPowerliftingTemplate(_ template: SessionTemplate) -> Bool {
        template.focusCategory == .powerlifting ||
            template.tags.contains { $0.name.contains("POWERLIFTING") || $0.name.contains("SENTADILLA") || $0.name.contains("PESO_MUERTO") || $0.name.contains("BANCA") } ||
            template.id.contains("pl-") || template.id.contains("sbd") || template.id.contains("sheiko") || template.id.contains("texas")
    }

    private static func candidateLabelsFor(_ dayLabel: String) -> Set<String> {
        let normalized = dayLabel.normalizedLabel()
        var labels: [String] = [normalized]

        func add(_ values: String...) { values.forEach { labels.append($0.normalizedLabel()) } }

        if normalized.contains("empuje") || normalized.contains("push") || normalized.contains("press") || (normalized.contains("pecho") && normalized.contains("triceps")) {
            add("Empuje", "Pecho", "Push", "Press Banca/Pecho", "Pecho/Espalda")
        }
        if normalized.contains("tirón") || normalized.contains("tiron") || normalized.contains("pull") || normalized.contains("espalda") || normalized.contains("bíceps") || normalized.contains("biceps") {
            add("Tirón", "Espalda", "Pull", "Peso Muerto/Espalda", "Espalda/Bíceps", "Cadena Posterior")
        }
        if normalized.contains("pierna") || normalized.contains("lower") || normalized.contains("cuádriceps") || normalized.contains("cuadriceps") {
            add("Pierna", "Piernas", "Lower", "Cuádriceps/Glúteo", "Pierna Mantenimiento", "Sentadilla/Pierna", "Cadena Anterior")
        }
        if normalized.contains("gluteo") || normalized.contains("glúteo") {
            add("Glúteo/Isquios", "Glúteo Pump", "Cuádriceps/Glúteo", "Pierna", "Lower")
        }
        if normalized.contains("torso") || normalized.contains("upper") {
            add("Torso", "Upper", "Upper Completo", "Torso Liviano")
        }
        if normalized.contains("full body") || normalized.contains("cuerpo completo") {
            add("Cuerpo Completo", "Full Body", "Full Body A", "Full Body B", "Cuerpo Completo A", "Cuerpo Completo B", "Cuerpo Completo C", "Cuerpo Completo D", "Full Body Pesado", "Full Body Liviano", "Full Body Medio")
        }
        if normalized.contains("hombro") || normalized.contains("brazo") || normalized.contains("triceps") || normalized.contains("biceps") {
            add("Hombro/Brazo", "Hombros/Brazos", "Brazos/Hombros", "Hombros/Abs", "T1 Militar")
        }
        if normalized.contains("anterior") { add("Cadena Anterior") }
        if normalized.contains("posterior") { add("Cadena Posterior") }
        if normalized.contains("sbd") { add("SBD Día 1", "SBD Día 2", "SBD Día 3", "SBD (Volumen)", "SBD (Técnica)", "SBD (Intensidad)") }
        if normalized.contains("sentadilla") || normalized.contains("squat") { add("Sentadilla/Banca", "Sentadilla/Peso Muerto", "T1 Sentadilla", "Sentadilla") }
        if normalized.contains("banca") || normalized.contains("bench") { add("Banca Volumen", "Banca", "T1 Banca", "Banca/Sentadilla Var.", "Peso Muerto/Banca Var.") }
        if normalized.contains("peso muerto") || normalized.contains("deadlift") || normalized.contains("dl") { add("Peso Muerto", "Peso Muerto/Banca", "Peso Muerto/Accesorios", "Peso Muerto/Press", "Variante DL/Banca", "T1 Peso Muerto") }
        if normalized.contains("volumen") || normalized.contains("recuperación") || normalized.contains("recuperacion") || normalized.contains("intensidad") || normalized.contains("repeticiones") || normalized.contains("explosivo") || normalized.contains("pesado") || normalized.contains("liviano") || normalized.contains("moderado") {
            add("Full Body", "SBD Día 1", "SBD Día 2", "SBD Día 3", "Minimalista · Fuerza")
        }
        if normalized.contains("max effort") || normalized.contains("dynamic effort") || normalized.contains("me ") || normalized.contains("de ") { add("Sentadilla/Banca", "T1 Sentadilla", "T1 Banca") }
        if normalized.contains("bodybuilding") || normalized.contains("accesorios") { add("Upper", "Torso", "Hombro/Brazo") }
        if normalized.contains("sesion") { add("SBD Día 1", "SBD Día 2", "SBD Día 3", "Full Body", "Minimalista · Fuerza") }

        return Set(labels)
    }

    private static func focusCategoriesFor(_ dayLabel: String) -> Set<SessionTemplateFocusCategory> {
        let normalized = dayLabel.normalizedLabel()
        var categories = Set<SessionTemplateFocusCategory>()
        if normalized.contains("pierna") || normalized.contains("lower") || normalized.contains("cuádriceps") || normalized.contains("cuadriceps") { categories.insert(.piernas) }
        if normalized.contains("gluteo") || normalized.contains("glúteo") { categories.insert(.gluteos) }
        if normalized.contains("pecho") || normalized.contains("empuje") || normalized.contains("push") || normalized.contains("banca") { categories.insert(.pecho) }
        if normalized.contains("espalda") || normalized.contains("tirón") || normalized.contains("tiron") || normalized.contains("pull") { categories.insert(.espalda) }
        if normalized.contains("hombro") { categories.insert(.hombros) }
        if normalized.contains("brazo") || normalized.contains("biceps") || normalized.contains("bíceps") || normalized.contains("triceps") { categories.insert(.brazos) }
        if normalized.contains("full") || normalized.contains("cuerpo completo") || normalized.contains("volumen") || normalized.contains("recuperación") || normalized.contains("recuperacion") { categories.insert(.fullBody) }
        if normalized.contains("sbd") || normalized.contains("sentadilla") || normalized.contains("banca") || normalized.contains("peso muerto") || normalized.contains("t1") || normalized.contains("max effort") || normalized.contains("dynamic effort") { categories.insert(.powerlifting) }
        if normalized.contains("minimalista") { categories.insert(.minimalista) }
        if normalized.contains("anterior") || normalized.contains("posterior") || normalized.contains("sesion") { categories.insert(.fullBody) }
        if normalized.contains("recuperación") || normalized.contains("recuperacion") || normalized.contains("light") { categories.insert(.recuperacion) }
        return categories
    }
}

private extension String {
    func normalizedLabel() -> String {
        self.lowercased()
            .replacingOccurrences(of: "á", with: "a")
            .replacingOccurrences(of: "é", with: "e")
            .replacingOccurrences(of: "í", with: "i")
            .replacingOccurrences(of: "ó", with: "o")
            .replacingOccurrences(of: "ú", with: "u")
            .trimmingCharacters(in: .whitespaces)
    }
}
