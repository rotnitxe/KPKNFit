import Foundation

struct AugeMuscleResolution {
    let broad: String
    let specific: String?
}

private func normalizeAugeText(_ value: String?) -> String {
    (value ?? "")
        .lowercased()
        .trimmingCharacters(in: .whitespaces)
        .replacingOccurrences(of: "á", with: "a")
        .replacingOccurrences(of: "é", with: "e")
        .replacingOccurrences(of: "í", with: "i")
        .replacingOccurrences(of: "ó", with: "o")
        .replacingOccurrences(of: "ú", with: "u")
        .replacingOccurrences(of: "ü", with: "u")
}

func resolveAugeMuscle(rawMuscle: String, rawEmphasis: String? = nil) -> AugeMuscleResolution {
    let source = normalizeAugeText(rawMuscle)
    let emphasis = normalizeAugeText(rawEmphasis ?? rawMuscle)

    if source.contains("cuello") || source.contains("cervical") || source.contains("neck") {
        return AugeMuscleResolution(broad: "Cuello", specific: nil)
    }
    if source.contains("pectineo") || source.contains("aductor") || source.contains("adductor") {
        return AugeMuscleResolution(broad: "Aductores", specific: nil)
    }
    if source.contains("erector") || source.contains("lumbar") || source.contains("espalda baja") || source.contains("lower back") {
        return AugeMuscleResolution(broad: "Erectores Espinales", specific: nil)
    }
    if source == "core" || source.contains("transverso") || source.contains("serrato") {
        return AugeMuscleResolution(broad: "Core", specific: nil)
    }
    if source.contains("abdomen") || source.contains("abdominal") || source.contains("oblicuo") {
        return AugeMuscleResolution(broad: "Abdomen", specific: nil)
    }
    if source.contains("pantorrilla") || source.contains("gemelo") || source.contains("gastrocnemio") || source.contains("soleo") || source.contains("calf") {
        return AugeMuscleResolution(broad: "Pantorrillas", specific: nil)
    }
    if source.contains("gluteo") || source.contains("gluteos") || source.contains("tensor") {
        return AugeMuscleResolution(broad: "Glúteos", specific: nil)
    }
    if source.contains("isquio") || source.contains("hamstring") || source.contains("femoral") || source.contains("semitendinoso") || source.contains("semimembranoso") {
        return AugeMuscleResolution(broad: "Isquiosurales", specific: nil)
    }
    if source.contains("cuadriceps") || source.contains("cuadricep") || source.contains("quad") || source.contains("vasto") || source.contains("recto femoral") {
        return AugeMuscleResolution(broad: "Cuádriceps", specific: nil)
    }
    if source.contains("antebrazo") || source.contains("forearm") {
        return AugeMuscleResolution(broad: "Antebrazo", specific: nil)
    }
    if source.contains("tricep") {
        return AugeMuscleResolution(broad: "Tríceps", specific: nil)
    }
    if (source.contains("bicep") || source.contains("braquial") || source.contains("braquiorradial")) && !source.contains("femoral") {
        return AugeMuscleResolution(broad: "Bíceps", specific: nil)
    }
    if source.contains("deltoide") || source.contains("deltoides") || source.contains("hombro") || source.contains("shoulder") {
        let specific: String?
        if source.contains("posterior") || emphasis == "posterior" || emphasis == "rear" {
            specific = "Deltoides Posterior"
        } else if source.contains("lateral") || source.contains("medio") || source.contains("medial") || emphasis == "lateral" || emphasis == "medio" {
            specific = "Deltoides Lateral"
        } else if source.contains("anterior") || source.contains("frontal") || emphasis == "anterior" || emphasis == "front" {
            specific = "Deltoides Anterior"
        } else {
            specific = nil
        }
        return AugeMuscleResolution(broad: "Deltoides", specific: specific)
    }
    if source.contains("trapecio") || source.contains("upper back") {
        return AugeMuscleResolution(broad: "Trapecio", specific: nil)
    }
    if source.contains("dorsal") || source.contains("lat") || source.contains("romboide") || source.contains("redondo mayor") || source.contains("espalda") || source.contains("back") {
        return AugeMuscleResolution(broad: "Dorsales", specific: nil)
    }
    if source.contains("pectoral") || source.contains("pecho") || source.contains("chest") {
        return AugeMuscleResolution(broad: "Pectorales", specific: nil)
    }

    let capitalized = rawMuscle.prefix(1).uppercased() + rawMuscle.dropFirst()
    return AugeMuscleResolution(broad: capitalized, specific: nil)
}

func getAugeMuscleDisplayId(rawMuscle: String, rawEmphasis: String? = nil) -> String {
    let resolved = resolveAugeMuscle(rawMuscle: rawMuscle, rawEmphasis: rawEmphasis)
    return resolved.specific ?? resolved.broad
}

func matchesAugeMuscleTarget(rawMuscle: String, target: String, rawEmphasis: String? = nil) -> Bool {
    let targetResolved = resolveAugeMuscle(rawMuscle: target)
    let muscleResolved = resolveAugeMuscle(rawMuscle: rawMuscle, rawEmphasis: rawEmphasis)

    if muscleResolved.broad != targetResolved.broad { return false }
    if targetResolved.specific == nil { return true }

    return getAugeMuscleDisplayId(rawMuscle: rawMuscle, rawEmphasis: rawEmphasis) == targetResolved.specific
}
