import Foundation

struct MuscleHead {
    let name: String
    let emphasisKeyword: String?
}

struct MuscleAnatomy {
    let canonicalName: String
    let displayName: String
    let heads: [MuscleHead]
}

let ALL_MUSCLES: [MuscleAnatomy] = [
    MuscleAnatomy(
        canonicalName: "Pectorales",
        displayName: "Pectorales",
        heads: [
            MuscleHead(name: "Clavicular/Superior", emphasisKeyword: "superior"),
            MuscleHead(name: "Esternal/Inferior", emphasisKeyword: "inferior"),
            MuscleHead(name: "Plano/Medio", emphasisKeyword: nil),
        ]
    ),
    MuscleAnatomy(
        canonicalName: "Deltoides",
        displayName: "Deltoides",
        heads: [
            MuscleHead(name: "Anterior", emphasisKeyword: "anterior"),
            MuscleHead(name: "Lateral/Medio", emphasisKeyword: "medio"),
            MuscleHead(name: "Posterior", emphasisKeyword: "posterior"),
        ]
    ),
    MuscleAnatomy(
        canonicalName: "Dorsales",
        displayName: "Dorsales",
        heads: []
    ),
    MuscleAnatomy(
        canonicalName: "Trapecio",
        displayName: "Trapecio",
        heads: [
            MuscleHead(name: "Descendente/Superior", emphasisKeyword: "superior"),
            MuscleHead(name: "Transversa/Media", emphasisKeyword: "medio"),
            MuscleHead(name: "Ascendente/Inferior", emphasisKeyword: "inferior"),
        ]
    ),
    MuscleAnatomy(
        canonicalName: "Romboides",
        displayName: "Romboides",
        heads: []
    ),
    MuscleAnatomy(
        canonicalName: "Erectores Espinales",
        displayName: "Erectores Espinales",
        heads: []
    ),
    MuscleAnatomy(
        canonicalName: "Cuádriceps",
        displayName: "Cuádriceps",
        heads: [
            MuscleHead(name: "Recto Femoral", emphasisKeyword: "recto femoral"),
            MuscleHead(name: "Vasto Lateral", emphasisKeyword: nil),
            MuscleHead(name: "Vasto Medial", emphasisKeyword: nil),
            MuscleHead(name: "Vasto Intermedio", emphasisKeyword: nil),
        ]
    ),
    MuscleAnatomy(
        canonicalName: "Isquiosurales",
        displayName: "Isquiosurales",
        heads: []
    ),
    MuscleAnatomy(
        canonicalName: "Glúteos",
        displayName: "Glúteos",
        heads: [
            MuscleHead(name: "Mayor", emphasisKeyword: "mayor"),
            MuscleHead(name: "Medio", emphasisKeyword: "medio"),
            MuscleHead(name: "Menor", emphasisKeyword: nil),
        ]
    ),
    MuscleAnatomy(
        canonicalName: "Aductores",
        displayName: "Aductores",
        heads: []
    ),
    MuscleAnatomy(
        canonicalName: "Pantorrillas",
        displayName: "Pantorrillas",
        heads: [
            MuscleHead(name: "Gastrocnemio", emphasisKeyword: "gastrocnemio"),
            MuscleHead(name: "Sóleo", emphasisKeyword: "sóleo"),
        ]
    ),
    MuscleAnatomy(
        canonicalName: "Bíceps",
        displayName: "Bíceps",
        heads: [
            MuscleHead(name: "Larga", emphasisKeyword: "larga"),
            MuscleHead(name: "Corta", emphasisKeyword: "corta"),
            MuscleHead(name: "Braquial", emphasisKeyword: "braquial"),
        ]
    ),
    MuscleAnatomy(
        canonicalName: "Tríceps",
        displayName: "Tríceps",
        heads: [
            MuscleHead(name: "Larga", emphasisKeyword: "larga"),
            MuscleHead(name: "Lateral", emphasisKeyword: "lateral"),
            MuscleHead(name: "Medial", emphasisKeyword: "medial"),
        ]
    ),
    MuscleAnatomy(
        canonicalName: "Antebrazo",
        displayName: "Antebrazo",
        heads: [
            MuscleHead(name: "Flexores", emphasisKeyword: "flexor"),
            MuscleHead(name: "Extensores", emphasisKeyword: "extensor"),
            MuscleHead(name: "Pronador/Supinador", emphasisKeyword: "pronador"),
        ]
    ),
    MuscleAnatomy(
        canonicalName: "Abdomen",
        displayName: "Abdomen",
        heads: []
    ),
    MuscleAnatomy(
        canonicalName: "Core",
        displayName: "Transverso/Core",
        heads: []
    ),
    MuscleAnatomy(
        canonicalName: "Cuello",
        displayName: "Cuello",
        heads: []
    ),
]

let MUSCLE_BY_CANONICAL: [String: MuscleAnatomy] = Dictionary(uniqueKeysWithValues: ALL_MUSCLES.map { ($0.canonicalName, $0) })
