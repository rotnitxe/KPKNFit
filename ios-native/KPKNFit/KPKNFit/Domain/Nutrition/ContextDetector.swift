import Foundation

/// ContextDetector — Detecta contexto implícito en la descripción del usuario.
enum ContextDetector {

    enum MealContext: String {
        case casino = "CASINO"
        case postEntreno = "POST_ENTRENO"
        case powerbuilder = "POWERBUILDER"
        case abuelaChilena = "ABUELA_CHILENA"
        case oficina = "OFICINA"
        case estudiante = "ESTUDIANTE"
        case snack = "SNACK"
        case desayuno = "DESAYUNO"
        case almuerzo = "ALMUERZO"
        case cena = "CENA"
        case general = "GENERAL"

        var portionFactor: Double {
            switch self {
            case .casino: return 1.0
            case .postEntreno: return 1.1
            case .powerbuilder: return 1.5
            case .abuelaChilena: return 1.3
            case .oficina: return 0.8
            case .estudiante: return 1.1
            case .snack: return 0.5
            case .desayuno: return 0.9
            case .almuerzo: return 1.1
            case .cena: return 0.9
            case .general: return 1.0
            }
        }

        var proteinBoost: Double {
            switch self {
            case .postEntreno: return 0.2
            case .powerbuilder: return 0.3
            case .desayuno: return 0.1
            default: return 0.0
            }
        }

        var label: String {
            switch self {
            case .casino: return "Casino"
            case .postEntreno: return "Post-entreno"
            case .powerbuilder: return "Powerbuilder"
            case .abuelaChilena: return "Abuela chilena"
            case .oficina: return "Oficina"
            case .estudiante: return "Estudiante"
            case .snack: return "Snack"
            case .desayuno: return "Desayuno"
            case .almuerzo: return "Almuerzo"
            case .cena: return "Cena"
            case .general: return "General"
            }
        }
    }

    struct ContextResult {
        let primaryContext: MealContext
        let detectedContexts: [MealContext]
        let confidence: Double
        let portionAdjustment: Double
        let proteinAdjustment: Double
    }

    private static let contextPatterns: [MealContext: [String]] = [
        .casino: [
            "casino", "cafetería", "cafeteria", "comedor", "buffet", "menú del día", "menu del dia",
            "del casino", "de la cafetería", "del comedor",
        ],
        .postEntreno: [
            "post-entreno", "post entreno", "post-entrenamiento", "post entrenamiento",
            "recuperación", "recuperacion", "post-sentadillas", "post-pecho",
            "post-espalda", "post-pierna", "post-brazo", "después de entrenar",
            "despues de entrenar", "post workout", "post-workout",
        ],
        .powerbuilder: [
            "powerbuilder", "power builder", "volumen extremo", "masa extrema",
            "desayuno de powerbuilder", "bulking", "volumen sucio",
        ],
        .abuelaChilena: [
            "abuela chilena", "abuela", "contundente", "plato hondo", "tazón grande",
            "plato rebosante", "plato colmado", "plato lleno", "hasta el borde",
            "almuerzo de abuela", "comida de abuela",
        ],
        .oficina: [
            "oficina", "escritorio", "trabajo", "reunión", "reunion", "break de oficina",
            "del trabajo", "en la oficina",
        ],
        .estudiante: [
            "estudiante", "universidad", "facultad", "campus", "barato", "corto de lucas",
            "sobrevivencia", "económico", "economico",
        ],
        .snack: [
            "snack", "colación", "colacion", "merendola", "merienda", "tentempié",
            "tentempie", "piscolabis", "refrigerio", "entre comida", "entre horas",
        ],
        .desayuno: [
            "desayuno", "desayunar", "am", "mañana", "manana", "al despertar",
            "temprano", "de mañana",
        ],
        .almuerzo: [
            "almuerzo", "almorzar", "mediodía", "mediodia", "del mediodía",
            "comida", "de tarde",
        ],
        .cena: [
            "cena", "cenar", "noche", "nocturno", "antes de dormir", "de noche",
            "liviano", "ligero",
        ],
    ]

    static func detect(description: String) -> ContextResult {
        let lower = description.lowercased()
        var detected: [MealContext] = []

        for (context, keywords) in contextPatterns {
            if keywords.contains(where: { lower.contains($0) }) {
                detected.append(context)
            }
        }

        let primary = detected.first ?? .general
        let confidence = detected.isEmpty ? 0.5 : 0.8

        return ContextResult(
            primaryContext: primary,
            detectedContexts: detected,
            confidence: confidence,
            portionAdjustment: primary.portionFactor,
            proteinAdjustment: primary.proteinBoost
        )
    }

    static func adjustPortion(grams: Double, context: MealContext) -> Double {
        grams * context.portionFactor
    }

    static func adjustProtein(protein: Double, context: MealContext) -> Double {
        protein * (1.0 + context.proteinBoost)
    }

    static func getContextProfile(context: MealContext) -> DatasetKnowledgeContext.ContextProfile? {
        DatasetKnowledgeContext.contextProfiles[context.rawValue]
    }
}
