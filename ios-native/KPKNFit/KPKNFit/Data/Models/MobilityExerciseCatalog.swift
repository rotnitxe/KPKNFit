import Foundation

public struct MobilityExercise: Codable, Identifiable {
    public let id: String
    public let name: String
    public let discomfortIds: [String]
    public let description: String
    public let durationSeconds: Int
    public let bodyRegion: String
    public let category: String
    public let instructions: String?

    public init(
        id: String,
        name: String,
        discomfortIds: [String],
        description: String,
        durationSeconds: Int = 60,
        bodyRegion: String,
        category: String = "Movilidad",
        instructions: String? = nil
    ) {
        self.id = id
        self.name = name
        self.discomfortIds = discomfortIds
        self.description = description
        self.durationSeconds = durationSeconds
        self.bodyRegion = bodyRegion
        self.category = category
        self.instructions = instructions
    }
}

public enum MobilityExerciseCatalog {

    private static let catalog: [String: [MobilityExercise]] = [
        "shoulder_anterior": [
            MobilityExercise(id: "mob_shoulder_band_rotation", name: "Rotación externa con banda", discomfortIds: ["shoulder_anterior"], description: "Rotación controlada de hombro con banda de resistencia", durationSeconds: 60, bodyRegion: "shoulder"),
            MobilityExercise(id: "mob_wall_slides", name: "Wall slides", discomfortIds: ["shoulder_anterior"], description: "Deslizamientos de brazos contra la pared", durationSeconds: 45, bodyRegion: "shoulder"),
            MobilityExercise(id: "mob_stick_dislocates", name: "Dislocaciones con palo", discomfortIds: ["shoulder_anterior"], description: "Rotación completa de hombros con palo o banda", durationSeconds: 60, bodyRegion: "shoulder"),
        ],
        "shoulder_posterior": [
            MobilityExercise(id: "mob_crossbody_stretch", name: "Estiramiento cruzado de hombro", discomfortIds: ["shoulder_posterior"], description: "Llevar el brazo cruzado sobre el pecho", durationSeconds: 30, bodyRegion: "shoulder"),
            MobilityExercise(id: "mob_sleeper_stretch", name: "Sleeper stretch", discomfortIds: ["shoulder_posterior"], description: "Estiramiento acostado de lado para rotadores posteriores", durationSeconds: 45, bodyRegion: "shoulder"),
            MobilityExercise(id: "mob_thread_needle", name: "Thread the needle", discomfortIds: ["shoulder_posterior"], description: "Rotación torácica en cuatro puntos", durationSeconds: 45, bodyRegion: "upper"),
        ],
        "elbow_medial": [
            MobilityExercise(id: "mob_wrist_flexor_stretch", name: "Estiramiento de flexores de muñeca", discomfortIds: ["elbow_medial"], description: "Extensión pasiva de muñeca con codo extendido", durationSeconds: 30, bodyRegion: "elbow"),
            MobilityExercise(id: "mob_pronation_supination", name: "Pronación/Supinación con mazo", discomfortIds: ["elbow_medial"], description: "Rotación controlada de antebrazo con martillo o mancuerna ligera", durationSeconds: 45, bodyRegion: "elbow"),
            MobilityExercise(id: "mob_forearm_roller", name: "Rodillo de antebrazo", discomfortIds: ["elbow_medial"], description: "Auto-masaje de antebrazo con rodillo", durationSeconds: 45, bodyRegion: "elbow"),
        ],
        "elbow_lateral": [
            MobilityExercise(id: "mob_wrist_extensor_stretch", name: "Estiramiento de extensores de muñeca", discomfortIds: ["elbow_lateral"], description: "Flexión pasiva de muñeca con codo extendido", durationSeconds: 30, bodyRegion: "elbow"),
            MobilityExercise(id: "mob_finger_extension", name: "Extensión activa de dedos con banda", discomfortIds: ["elbow_lateral"], description: "Apertura y cierre controlado con banda alrededor de los dedos", durationSeconds: 45, bodyRegion: "elbow"),
            MobilityExercise(id: "mob_forearm_supination", name: "Supinación de antebrazo sentado", discomfortIds: ["elbow_lateral"], description: "Apoyar antebrazo en muslo y rotar palma hacia arriba con peso ligero", durationSeconds: 45, bodyRegion: "elbow"),
        ],
        "wrist_hand": [
            MobilityExercise(id: "mob_wrist_circles", name: "Círculos de muñeca", discomfortIds: ["wrist_hand"], description: "Rotaciones completas de muñeca en ambas direcciones", durationSeconds: 30, bodyRegion: "wrist"),
            MobilityExercise(id: "mob_finger_spread", name: "Apertura y cierre de dedos", discomfortIds: ["wrist_hand"], description: "Separar y juntar dedos repetidamente con tensión controlada", durationSeconds: 30, bodyRegion: "wrist"),
            MobilityExercise(id: "mob_wrist_mobilization", name: "Movilización de muñeca en cuatro direcciones", discomfortIds: ["wrist_hand"], description: "Flexión, extensión, desviación radial y cubital asistida", durationSeconds: 45, bodyRegion: "wrist"),
        ],
        "neck_cervical": [
            MobilityExercise(id: "mob_neck_retraction", name: "Retracción cervical (chin tucks)", discomfortIds: ["neck_cervical"], description: "Llevar mentón hacia atrás manteniendo cabeza nivelada", durationSeconds: 30, bodyRegion: "neck"),
            MobilityExercise(id: "mob_neck_lateral_flexion", name: "Flexión lateral de cuello", discomfortIds: ["neck_cervical"], description: "Inclinación lateral controlada de cuello", durationSeconds: 30, bodyRegion: "neck"),
            MobilityExercise(id: "mob_upper_trap_stretch", name: "Estiramiento de trapecio superior", discomfortIds: ["neck_cervical"], description: "Estiramiento sentado del trapecio superior", durationSeconds: 30, bodyRegion: "neck"),
        ],
        "upper_back": [
            MobilityExercise(id: "mob_thoracic_extension", name: "Extensión torácica sobre foam roller", discomfortIds: ["upper_back"], description: "Acostado sobre foam roller a nivel torácico, extender columna", durationSeconds: 60, bodyRegion: "upper"),
            MobilityExercise(id: "mob_open_book", name: "Open book stretch", discomfortIds: ["upper_back"], description: "Acostado de lado, rotar torso abriendo el brazo superior", durationSeconds: 45, bodyRegion: "upper"),
            MobilityExercise(id: "mob_scapular_retraction", name: "Retracción escapular con banda", discomfortIds: ["upper_back"], description: "Juntar omóplatos contra resistencia de banda", durationSeconds: 45, bodyRegion: "upper"),
        ],
        "lumbar": [
            MobilityExercise(id: "mob_cat_cow", name: "Cat-Cow", discomfortIds: ["lumbar"], description: "Flexión y extensión alternada de columna en cuatro puntos", durationSeconds: 60, bodyRegion: "spine"),
            MobilityExercise(id: "mob_pelvic_tilts", name: "Pelvic tilts", discomfortIds: ["lumbar"], description: "Basculaciones pélvicas acostado boca arriba", durationSeconds: 45, bodyRegion: "spine"),
            MobilityExercise(id: "mob_child_pose_rotation", name: "Child's pose con rotación", discomfortIds: ["lumbar"], description: "Posición del niño con alcance rotacional lateral", durationSeconds: 60, bodyRegion: "spine"),
        ],
        "hip_front": [
            MobilityExercise(id: "mob_couch_stretch", name: "Couch stretch", discomfortIds: ["hip_front"], description: "Estiramiento de cadera anterior contra pared/sillón", durationSeconds: 60, bodyRegion: "hip"),
            MobilityExercise(id: "mob_hip_flexor_lunge", name: "Estocada con estiramiento de psoas", discomfortIds: ["hip_front"], description: "Posición de estocada con extensión de cadera", durationSeconds: 45, bodyRegion: "hip"),
            MobilityExercise(id: "mob_90_90_hip", name: "90/90 Hip switch", discomfortIds: ["hip_front"], description: "Rotación interna/externa de cadera en posición 90/90", durationSeconds: 60, bodyRegion: "hip"),
        ],
        "hip_lateral": [
            MobilityExercise(id: "mob_clamshell", name: "Clamshell", discomfortIds: ["hip_lateral"], description: "Apertura lateral de rodillas acostado de lado", durationSeconds: 45, bodyRegion: "hip"),
            MobilityExercise(id: "mob_fire_hydrant", name: "Fire hydrant", discomfortIds: ["hip_lateral"], description: "Elevación lateral de rodilla en cuatro puntos", durationSeconds: 45, bodyRegion: "hip"),
            MobilityExercise(id: "mob_hip_circle_standing", name: "Círculos de cadera de pie", discomfortIds: ["hip_lateral"], description: "Rotaciones completas de cadera en posición de pie", durationSeconds: 45, bodyRegion: "hip"),
        ],
        "adductor_groin": [
            MobilityExercise(id: "mob_frog_stretch", name: "Frog stretch", discomfortIds: ["adductor_groin"], description: "Rodillas abiertas en cuatro puntos, llevar caderas hacia atrás", durationSeconds: 60, bodyRegion: "hip"),
            MobilityExercise(id: "mob_side_lunge_adductor", name: "Estocada lateral con enfoque en aductores", discomfortIds: ["adductor_groin"], description: "Paso lateral profundo manteniendo torso erguido", durationSeconds: 45, bodyRegion: "hip"),
            MobilityExercise(id: "mob_butterfly_stretch", name: "Mariposa", discomfortIds: ["adductor_groin"], description: "Plantas de pies juntas, presionar rodillas hacia el suelo", durationSeconds: 45, bodyRegion: "hip"),
        ],
        "hamstring_proximal": [
            MobilityExercise(id: "mob_straight_leg_raise", name: "Elevación de pierna recta con banda", discomfortIds: ["hamstring_proximal"], description: "Acostado boca arriba, elevar pierna extendida contra banda", durationSeconds: 45, bodyRegion: "leg"),
            MobilityExercise(id: "mob_standing_hamstring_stretch", name: "Estiramiento de isquiotibiales de pie", discomfortIds: ["hamstring_proximal"], description: "Pierna elevada sobre apoyo, inclinar torso hacia adelante", durationSeconds: 45, bodyRegion: "leg"),
            MobilityExercise(id: "mob_slider_hamstring", name: "Deslizamiento de talón (Sliders)", discomfortIds: ["hamstring_proximal"], description: "Acostado boca arriba, deslizar talón hacia glúteo y extender", durationSeconds: 45, bodyRegion: "leg"),
        ],
        "knee_patellar": [
            MobilityExercise(id: "mob_wall_sit", name: "Sentadilla isométrica en pared", discomfortIds: ["knee_patellar"], description: "Posición de sentadilla contra pared manteniendo 90°", durationSeconds: 45, bodyRegion: "knee"),
            MobilityExercise(id: "mob_step_ups_control", name: "Step-ups controlados", discomfortIds: ["knee_patellar"], description: "Subir y bajar de un step con control excéntrico", durationSeconds: 60, bodyRegion: "knee"),
            MobilityExercise(id: "mob_ankle_dorsiflexion", name: "Movilidad de tobillo en pared", discomfortIds: ["knee_patellar"], description: "Dorsiflexión de tobillo contra pared", durationSeconds: 30, bodyRegion: "knee"),
        ],
        "knee_medial": [
            MobilityExercise(id: "mob_banded_patellar_glide", name: "Deslizamiento rotuliano asistido con banda", discomfortIds: ["knee_medial"], description: "Tracción suave de rótula con banda elástica", durationSeconds: 45, bodyRegion: "knee"),
            MobilityExercise(id: "mob_terminal_knee_extension", name: "Extensión terminal de rodilla con banda", discomfortIds: ["knee_medial"], description: "Extensión completa de rodilla contra resistencia ligera", durationSeconds: 45, bodyRegion: "knee"),
            MobilityExercise(id: "mob_heel_slides", name: "Deslizamiento de talón acostado", discomfortIds: ["knee_medial"], description: "Acostado, deslizar talón flexionando y extendiendo rodilla", durationSeconds: 45, bodyRegion: "knee"),
        ],
        "achilles": [
            MobilityExercise(id: "mob_eccentric_heel_drop", name: "Drop excéntrico de talón", discomfortIds: ["achilles"], description: "Elevarse en puntas y bajar lentamente en escalón", durationSeconds: 45, bodyRegion: "ankle"),
            MobilityExercise(id: "mob_calf_stretch_wall", name: "Estiramiento de gemelo contra pared", discomfortIds: ["achilles"], description: "Pierna extendida atrás, talón en suelo, inclinarse hacia adelante", durationSeconds: 45, bodyRegion: "ankle"),
            MobilityExercise(id: "mob_soleus_stretch", name: "Estiramiento de sóleo", discomfortIds: ["achilles"], description: "Pierna flexionada atrás, bajar talón contra el suelo", durationSeconds: 45, bodyRegion: "ankle"),
        ],
        "ankle": [
            MobilityExercise(id: "mob_ankle_alphabet", name: "Alfabeto con tobillo", discomfortIds: ["ankle"], description: "Dibujar el alfabeto con el pie suspendido", durationSeconds: 45, bodyRegion: "ankle"),
            MobilityExercise(id: "mob_ankle_circles", name: "Círculos de tobillo", discomfortIds: ["ankle"], description: "Rotaciones completas de tobillo en ambas direcciones", durationSeconds: 30, bodyRegion: "ankle"),
            MobilityExercise(id: "mob_ankle_band_dorsiflexion", name: "Dorsiflexión asistida con banda", discomfortIds: ["ankle"], description: "Band around ankle, pull foot into dorsiflexion", durationSeconds: 45, bodyRegion: "ankle"),
        ],
        "plantar_foot": [
            MobilityExercise(id: "mob_tennis_ball_roll", name: "Masaje con pelota de tenis", discomfortIds: ["plantar_foot"], description: "Rodar pelota de tenis bajo el pie ejerciendo presión gradual", durationSeconds: 60, bodyRegion: "foot"),
            MobilityExercise(id: "mob_toe_spread", name: "Separación activa de dedos", discomfortIds: ["plantar_foot"], description: "Separar y estirar los dedos del pie activamente", durationSeconds: 30, bodyRegion: "foot"),
            MobilityExercise(id: "mob_towel_curl", name: "Recoger toalla con dedos", discomfortIds: ["plantar_foot"], description: "Usar los dedos del pie para arrugar y recoger una toalla", durationSeconds: 45, bodyRegion: "foot"),
        ],
    ]

    public static func getMobilityForDiscomfort(discomfortId: String) -> [MobilityExercise] {
        return catalog[discomfortId] ?? []
    }

    public static func getMobilityForDiscomforts(discomfortIds: [String]) -> [MobilityExercise] {
        var seen = Set<String>()
        return discomfortIds.flatMap { getMobilityForDiscomfort(discomfortId: $0) }.filter { seen.insert($0.id).inserted }
    }

    public static func getAllMobilityExercises() -> [MobilityExercise] {
        var seen = Set<String>()
        return catalog.values.flatMap { $0 }.filter { seen.insert($0.id).inserted }
    }

    public static func searchMobilityByName(query: String) -> [MobilityExercise] {
        let normalized = query.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        return getAllMobilityExercises().filter { $0.name.lowercased().contains(normalized) }
    }
}
