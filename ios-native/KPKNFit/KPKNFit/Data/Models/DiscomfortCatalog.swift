import Foundation

public enum DiscomfortSection: String, Codable {
    case shouldersArms = "Hombro y brazos"
    case spineNeck = "Columna y cuello"
    case hipPelvis = "Cadera y pelvis"
    case knee = "Rodilla"
    case ankleFoot = "Tobillo y pie"
    case general = "General"
}

public struct DiscomfortCatalogEntry: Codable, Identifiable {
    public let id: String
    public let label: String
    public let description: String
    public let section: DiscomfortSection
    public let relatedMuscles: [String]
    public let relatedArticular: [ArticularBattery]

    public init(
        id: String,
        label: String,
        description: String,
        section: DiscomfortSection,
        relatedMuscles: [String] = [],
        relatedArticular: [ArticularBattery] = []
    ) {
        self.id = id
        self.label = label
        self.description = description
        self.section = section
        self.relatedMuscles = relatedMuscles
        self.relatedArticular = relatedArticular
    }
}

public let DISCOMFORT_CATALOG: [DiscomfortCatalogEntry] = [
    DiscomfortCatalogEntry(
        id: "none",
        label: "Sin molestias",
        description: "No percibiste molestias relevantes en este ejercicio.",
        section: .general
    ),
    DiscomfortCatalogEntry(
        id: "shoulder_anterior",
        label: "Hombro anterior",
        description: "Molestia en la parte frontal del hombro, frecuente en empujes y elevaciones.",
        section: .shouldersArms,
        relatedMuscles: ["Deltoides", "Pectorales"],
        relatedArticular: [.SHOULDER]
    ),
    DiscomfortCatalogEntry(
        id: "shoulder_posterior",
        label: "Hombro posterior",
        description: "Molestia en la cara posterior del hombro, común en tirones o rotaciones.",
        section: .shouldersArms,
        relatedMuscles: ["Deltoides", "Trapecio", "Dorsales"],
        relatedArticular: [.SHOULDER]
    ),
    DiscomfortCatalogEntry(
        id: "elbow_medial",
        label: "Codo (cara interna)",
        description: "Molestia en flexores/pronadores del antebrazo cerca del codo.",
        section: .shouldersArms,
        relatedMuscles: ["Antebrazo", "Bíceps"],
        relatedArticular: [.ELBOW]
    ),
    DiscomfortCatalogEntry(
        id: "elbow_lateral",
        label: "Codo (cara externa)",
        description: "Molestia en extensores del antebrazo cerca del epicóndilo.",
        section: .shouldersArms,
        relatedMuscles: ["Antebrazo", "Tríceps"],
        relatedArticular: [.ELBOW]
    ),
    DiscomfortCatalogEntry(
        id: "wrist_hand",
        label: "Muñeca / mano",
        description: "Molestia durante agarre, apoyo o extensión/flexión de muñeca.",
        section: .shouldersArms,
        relatedMuscles: ["Antebrazo"],
        relatedArticular: [.ELBOW]
    ),
    DiscomfortCatalogEntry(
        id: "neck_cervical",
        label: "Cuello / cervical",
        description: "Rigidez o dolor cervical relacionado con carga axial o tensión de trapecio.",
        section: .spineNeck,
        relatedMuscles: ["Cuello", "Trapecio"],
        relatedArticular: [.CERVICAL]
    ),
    DiscomfortCatalogEntry(
        id: "upper_back",
        label: "Espalda alta",
        description: "Molestia en zona torácica alta o entre escápulas.",
        section: .spineNeck,
        relatedMuscles: ["Trapecio", "Dorsales"],
        relatedArticular: [.CERVICAL, .SHOULDER]
    ),
    DiscomfortCatalogEntry(
        id: "lumbar",
        label: "Lumbar",
        description: "Molestia en zona baja de la espalda asociada a bisagra o compresión.",
        section: .spineNeck,
        relatedMuscles: ["Erectores Espinales", "Core"],
        relatedArticular: [.HIP, .LUMBAR]
    ),
    DiscomfortCatalogEntry(
        id: "hip_front",
        label: "Cadera anterior",
        description: "Molestia en flexión de cadera o al final del rango en sentadillas/zancadas.",
        section: .hipPelvis,
        relatedMuscles: ["Cuádriceps", "Aductores", "Core"],
        relatedArticular: [.HIP]
    ),
    DiscomfortCatalogEntry(
        id: "hip_lateral",
        label: "Cadera lateral / glútea",
        description: "Molestia lateral de cadera o glúteo medio durante apoyo unilateral.",
        section: .hipPelvis,
        relatedMuscles: ["Glúteos", "Aductores"],
        relatedArticular: [.HIP]
    ),
    DiscomfortCatalogEntry(
        id: "adductor_groin",
        label: "Aductores / ingle",
        description: "Molestia en la cara interna del muslo o región inguinal.",
        section: .hipPelvis,
        relatedMuscles: ["Aductores"],
        relatedArticular: [.HIP]
    ),
    DiscomfortCatalogEntry(
        id: "hamstring_proximal",
        label: "Isquiosurales proximales",
        description: "Molestia en inserción alta de isquiosurales cerca de cadera.",
        section: .hipPelvis,
        relatedMuscles: ["Isquiosurales", "Glúteos"],
        relatedArticular: [.HIP]
    ),
    DiscomfortCatalogEntry(
        id: "knee_patellar",
        label: "Rodilla anterior",
        description: "Molestia bajo/entorno de la rótula, común en flexión repetida de rodilla.",
        section: .knee,
        relatedMuscles: ["Cuádriceps"],
        relatedArticular: [.KNEE]
    ),
    DiscomfortCatalogEntry(
        id: "knee_medial",
        label: "Rodilla interna/externa",
        description: "Molestia en compartimentos medial o lateral de rodilla.",
        section: .knee,
        relatedMuscles: ["Cuádriceps", "Isquiosurales", "Aductores"],
        relatedArticular: [.KNEE]
    ),
    DiscomfortCatalogEntry(
        id: "achilles",
        label: "Tendón de Aquiles",
        description: "Molestia en tendón de Aquiles durante saltos, carrera o elevaciones de talón.",
        section: .ankleFoot,
        relatedMuscles: ["Pantorrillas"],
        relatedArticular: [.ANKLE]
    ),
    DiscomfortCatalogEntry(
        id: "ankle",
        label: "Tobillo",
        description: "Molestia en estabilidad o movilidad de tobillo bajo carga.",
        section: .ankleFoot,
        relatedMuscles: ["Pantorrillas"],
        relatedArticular: [.ANKLE]
    ),
    DiscomfortCatalogEntry(
        id: "plantar_foot",
        label: "Planta del pie",
        description: "Molestia en arco plantar o apoyo del pie.",
        section: .ankleFoot,
        relatedMuscles: ["Pantorrillas"],
        relatedArticular: [.ANKLE]
    ),
]

public let DISCOMFORT_CATALOG_BY_ID: [String: DiscomfortCatalogEntry] = {
    var dict: [String: DiscomfortCatalogEntry] = [:]
    for entry in DISCOMFORT_CATALOG {
        dict[entry.id] = entry
    }
    return dict
}()

public func discomfortLabel(id: String) -> String {
    return DISCOMFORT_CATALOG_BY_ID[id]?.label ?? id
}
