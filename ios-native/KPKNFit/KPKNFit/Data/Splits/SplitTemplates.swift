import Foundation

public enum SplitDayRequirement { case REQUIRED, OPTIONAL }

public struct SplitDayDefinition {
    public let dayKey: String
    public let label: String
    public let foci: [String]
    public let requiredRoles: [String]
    public let ordinal: Int
    public let requirement: SplitDayRequirement
    public let protocolDayKey: String?

    public init(dayKey: String, label: String, foci: [String] = [], requiredRoles: [String] = [], ordinal: Int, requirement: SplitDayRequirement = .REQUIRED, protocolDayKey: String? = nil) {
        self.dayKey = dayKey; self.label = label; self.foci = foci; self.requiredRoles = requiredRoles; self.ordinal = ordinal; self.requirement = requirement; self.protocolDayKey = protocolDayKey
    }
}

public struct SplitTemplate {
    public let id: String
    public let name: String
    public let description: String
    public let tags: [SplitTag]
    public let pattern: [String]
    public let dayDefinitions: [SplitDayDefinition]
    public let difficulty: Difficulty
    public let pros: [String]
    public let cons: [String]
    public let sessionDescriptions: [String: String]

    public init(
        _ id: String,
        _ name: String,
        _ description: String,
        _ tags: [SplitTag] = [],
        _ pattern: [String] = [],
        _ dayDefinitions: [SplitDayDefinition] = [],
        _ difficulty: Difficulty = .INTERMEDIO,
        _ pros: [String] = [],
        _ cons: [String] = [],
        _ sessionDescriptions: [String: String] = [:]
    ) {
        self.id = id
        self.name = name
        self.description = description
        self.tags = tags
        self.pattern = pattern
        self.dayDefinitions = dayDefinitions
        self.difficulty = difficulty
        self.pros = pros
        self.cons = cons
        self.sessionDescriptions = sessionDescriptions
    }

    public func effectiveDayDefinitions() -> [SplitDayDefinition] {
        if !dayDefinitions.isEmpty { return dayDefinitions }
        return pattern.enumerated().map { (index, label) in
            let normalized = label.lowercased().trimmingCharacters(in: .whitespaces)
            let isRest = normalized == "descanso"
            let dayKey: String
            if isRest { dayKey = "rest_\(index)" }
            else if normalized.contains("sentadilla") && normalized.contains("banca") { dayKey = "squat_bench_\(index)" }
            else if normalized.contains("sentadilla") { dayKey = "squat_\(index)" }
            else if normalized.contains("peso muerto") { dayKey = "deadlift_\(index)" }
            else if normalized.contains("banca") { dayKey = "bench_\(index)" }
            else if normalized.contains("sbd") { dayKey = "sbd_\(index)" }
            else { dayKey = "day_\(index)" }
            let foci: [String]
            if normalized.contains("sentadilla") && normalized.contains("banca") { foci = ["SQUAT","BENCH"] }
            else if normalized.contains("sentadilla") { foci = ["SQUAT"] }
            else if normalized.contains("peso muerto") { foci = ["DEADLIFT"] }
            else if normalized.contains("banca") { foci = ["BENCH"] }
            else if normalized.contains("sbd") { foci = ["SQUAT","BENCH","DEADLIFT"] }
            else { foci = [] }
            return SplitDayDefinition(dayKey: dayKey, label: label, foci: foci, requiredRoles: foci.map { "COMPETITION_\($0)" }, ordinal: index, requirement: isRest ? .OPTIONAL : .REQUIRED, protocolDayKey: tags.contains(.POWERLIFTING) && !isRest ? dayKey : nil)
        }
    }
}

public enum SplitTag {
    case RECOMENDADO_KPKN, ALTA_FRECUENCIA, BAJA_FRECUENCIA, BALANCEADO, ALTO_VOLUMEN, ALTA_TOLERANCIA, PERSONALIZADO, POWERLIFTING
}

public enum Difficulty {
    case PRINCIPIANTE, INTERMEDIO, AVANZADO
}

public let SPLIT_TEMPLATES: [SplitTemplate] = [
    SplitTemplate("custom", "Crear desde Cero", "Lienzo en blanco.", [.PERSONALIZADO], Array(repeating: "Descanso", count: 7), .AVANZADO, ["Libertad total de diseño"], ["Requiere conocimiento avanzado"]),
    SplitTemplate("ul_x4", "Upper / Lower x4", "El estándar de oro. Equilibrio perfecto.", [.RECOMENDADO_KPKN, .BALANCEADO], ["Torso", "Pierna", "Descanso", "Torso", "Pierna", "Descanso", "Descanso"], .INTERMEDIO, ["Frecuencia 2x/semana óptima", "48-72h recuperación"], ["Requiere 4 días mínimos"]),
    SplitTemplate("ppl_ul", "PPL + Upper/Lower", "Híbrido de 5 días. Volumen y frecuencia.", [.RECOMENDADO_KPKN, .ALTO_VOLUMEN, .BALANCEADO], ["Torso", "Pierna", "Descanso", "Empuje", "Tirón", "Pierna", "Descanso"], .INTERMEDIO, ["Combina frecuencia UL con PPL", "5 días manejables"], ["Coordinación compleja"]),
    SplitTemplate("fullbody_x3", "Full Body x3", "Alta frecuencia muscular con pocos días de entrenamiento.", [.RECOMENDADO_KPKN, .ALTA_FRECUENCIA], ["Cuerpo Completo A", "Descanso", "Cuerpo Completo B", "Descanso", "Cuerpo Completo C", "Descanso", "Descanso"], .PRINCIPIANTE, ["Frecuencia 3x/semana", "Ideal para aprendizaje motor"], ["Volumen por sesión limitado"]),
    SplitTemplate("ppl_x6", "Push Pull Legs x6", "Máximo volumen. Solo expertos.", [.ALTA_FRECUENCIA, .ALTO_VOLUMEN, .ALTA_TOLERANCIA], ["Empuje", "Tirón", "Pierna", "Empuje", "Tirón", "Pierna", "Descanso"], .AVANZADO, ["Volumen máximo", "Frecuencia 2x/semana"], ["6 días requeridos", "Fatiga extrema"]),
    SplitTemplate("ul_x6", "Upper / Lower x6", "Frecuencia 3 por grupo muscular.", [.ALTA_FRECUENCIA, .ALTA_TOLERANCIA, .ALTO_VOLUMEN], ["Torso", "Pierna", "Torso", "Pierna", "Torso", "Pierna", "Descanso"], .AVANZADO, ["Frecuencia 3x/semana por músculo"], ["Gestión de fatiga CRÍTICA", "Solo avanzados"]),
    SplitTemplate("ppl_arnold", "PPL + Arnold", "PPL + Pecho/Espalda + Hombro/Brazo.", [.ALTO_VOLUMEN, .BALANCEADO, .ALTA_TOLERANCIA], ["Empuje", "Tirón", "Pierna", "Pecho/Espalda", "Hombro/Brazo", "Pierna", "Descanso"], .AVANZADO, ["Énfasis estético", "Brazos y hombros dedicados"], ["5-6 días requeridos", "Volumen de empuje alto"]),
    SplitTemplate("phat_hybrid", "UL x2 + Cuerpo Completo", "Híbrido de 5 días con un cierre full body para puntos débiles.", [.BALANCEADO, .ALTA_FRECUENCIA], ["Torso", "Pierna", "Descanso", "Torso", "Pierna", "Cuerpo Completo", "Descanso"], .INTERMEDIO, ["Frecuencia 2-3x/semana", "Día 6 para puntos débiles"], ["El día full body puede acumular mucha fatiga"]),
    SplitTemplate("ant_post_x4", "Anterior / Posterior x4", "Enfoque en cadenas musculares.", [.RECOMENDADO_KPKN, .BALANCEADO], ["Cadena Anterior", "Cadena Posterior", "Descanso", "Cadena Anterior", "Cadena Posterior", "Descanso", "Descanso"], .INTERMEDIO, ["Enfoque funcional", "Frecuencia 2x/semana"], ["Menos común"]),
    SplitTemplate("arnold_ul", "Arnold + Upper / Lower", "Híbrido estético con cierre Upper/Lower para frecuencia 2 y buen volumen.", [.RECOMENDADO_KPKN, .BALANCEADO, .ALTO_VOLUMEN], ["Pecho/Espalda", "Pierna", "Descanso", "Hombro/Brazo", "Upper", "Lower", "Descanso"], .INTERMEDIO, ["Frecuencia 2x para la mayoría de grupos", "Buen balance entre estética y estructura", "5 días con 2 descansos"], ["Requiere tolerancia media-alta", "Puede cargar hombros y brazos si se abusa del volumen"]),
    SplitTemplate("ant_post_x6", "Anterior / Posterior x6", "Frecuencia agresiva por plano.", [.ALTA_FRECUENCIA, .ALTA_TOLERANCIA], ["Cadena Anterior", "Cadena Posterior", "Cadena Anterior", "Cadena Posterior", "Cadena Anterior", "Cadena Posterior", "Descanso"], .AVANZADO, ["Frecuencia 3x/semana por cadena"], ["Fatiga sistémica extrema"]),
    SplitTemplate("bro_split", "Bro Split Clásico", "Un grupo muscular por día.", [.BAJA_FRECUENCIA, .ALTO_VOLUMEN], ["Pecho", "Espalda", "Piernas", "Hombros", "Brazos", "Descanso", "Descanso"], .PRINCIPIANTE, ["Volumen máximo por sesión", "Foco mental óptimo"], ["Frecuencia 1x/semana SUBÓPTIMA", "NO recomendado para naturales"]),
    SplitTemplate("hybrid_fb_ap", "Híbrido Cuerpo Completo + Ant/Post", "4 días: 2 cuerpo completo + 1 anterior + 1 posterior.", [.BALANCEADO], ["Cuerpo Completo", "Descanso", "Cuerpo Completo", "Descanso", "Anterior", "Posterior", "Descanso"], .INTERMEDIO, ["Combina frecuencia full body con especificidad", "4 días manejables"], ["Coordinación compleja"]),
    SplitTemplate("minimalist_x2", "Minimalista x2", "Dosis mínima efectiva.", [.BAJA_FRECUENCIA, .BALANCEADO], ["Full Body A", "Descanso", "Descanso", "Full Body B", "Descanso", "Descanso", "Descanso"], .PRINCIPIANTE, ["Solo 2 días requeridos", "Máxima eficiencia"], ["Progreso lento", "Volumen limitado"]),
    SplitTemplate("weekend_warrior", "Guerrero de Finde", "Solo fines de semana.", [.BAJA_FRECUENCIA], ["Descanso", "Descanso", "Descanso", "Descanso", "Descanso", "Torso/Full Body", "Pierna/Full Body"], .INTERMEDIO, ["Se adapta a agendas extremas", "Socialmente sostenible"], ["Frecuencia 1x/semana", "Volumen concentrado"]),
    SplitTemplate("glute_focus", "Especialización Glúteo", "3 días de tren inferior.", [.ALTA_FRECUENCIA, .ALTO_VOLUMEN], ["Glúteo/Isquios", "Torso Liviano", "Descanso", "Cuádriceps/Glúteo", "Hombros/Abs", "Glúteo Pump", "Descanso"], .INTERMEDIO, ["Frecuencia 3x/semana glúteo", "Énfasis estético"], ["Desbalance potencial"]),
    SplitTemplate("beach_body", "Torso Dominante", "Enfoque 'Beach Body'.", [.ALTO_VOLUMEN], ["Pecho/Espalda", "Pierna Mantenimiento", "Descanso", "Hombros/Brazos", "Descanso", "Upper Completo", "Descanso"], .INTERMEDIO, ["Énfasis estético", "3 días de torso"], ["Desbalance torso/pierna"]),
    SplitTemplate("fullbody_x5", "Cuerpo Completo x5", "Alta frecuencia estilo noruego.", [.ALTA_FRECUENCIA, .ALTA_TOLERANCIA], ["Cuerpo Completo", "Cuerpo Completo", "Cuerpo Completo", "Cuerpo Completo", "Cuerpo Completo", "Descanso", "Descanso"], .AVANZADO, ["Frecuencia 5x/semana MÁXIMA", "Ideal para fuerza"], ["Fatiga EXTREMA", "Solo avanzados"]),
    SplitTemplate("push_pull_x4", "Push / Pull x4", "Simple y brutal.", [.BALANCEADO], ["Empuje + Cuádriceps", "Tirón + Isquios", "Descanso", "Empuje + Cuádriceps", "Tirón + Isquios", "Descanso", "Descanso"], .INTERMEDIO, ["Frecuencia 2x/semana", "Integración pierna natural"], ["Sesiones de empuje largas"]),
    SplitTemplate("texas_method", "Estilo Texas", "Ondulación diaria.", [.POWERLIFTING, .BAJA_FRECUENCIA], ["Día Volumen (5x5)", "Descanso", "Día Recuperación", "Descanso", "Día Intensidad", "Descanso", "Descanso"], .INTERMEDIO, ["Progresión lineal probada", "Ideal para fuerza"], ["Solo 3 días", "Frecuencia baja para hipertrofia"]),
    SplitTemplate("smolov_base", "Alta Frecuencia Base", "Inspirado en Smolov Jr.", [.POWERLIFTING, .ALTA_FRECUENCIA, .ALTA_TOLERANCIA], ["Sesión 1 (4x9)", "Descanso", "Sesión 2 (5x7)", "Sesión 3 (7x5)", "Descanso", "Sesión 4 (10x3)", "Descanso"], .AVANZADO, ["Volumen EXTREMO", "Ganancias de fuerza rápidas"], ["Solo ciclos cortos", "Fatiga extrema"]),
    SplitTemplate("pl_sbd_x3", "SBD Full Body x3", "Alta especificidad.", [.POWERLIFTING, .ALTA_TOLERANCIA], ["SBD Día 1", "Descanso", "SBD Día 2", "Descanso", "SBD Día 3", "Descanso", "Descanso"], .AVANZADO, ["Especificidad máxima en SBD", "Técnica altamente practicada"], ["Fatiga articular alta", "Solo powerlifters"]),
    SplitTemplate("pl_hf_bench", "PL: Bench Freq 4", "Sq x3, Bp x4, Dl x2.", [.POWERLIFTING, .ALTA_FRECUENCIA], ["Sentadilla/Banca", "Peso Muerto/Banca", "Descanso", "Sentadilla/Banca", "Variante DL/Banca", "Sentadilla/Accesorios", "Descanso"], .AVANZADO, ["Frecuencia 4x/semana banca", "Ideal para especialización"], ["Fatiga de hombros crítica"]),
    SplitTemplate("pl_classic_4", "PL: Clásico 4 Días", "Base sólida de powerlifting.", [.POWERLIFTING, .BALANCEADO], ["Sentadilla/Banca", "Peso Muerto", "Descanso", "Banca Volumen", "Sentadilla/Peso Muerto", "Descanso", "Descanso"], .INTERMEDIO, ["Balance clásico", "4 días manejables", "Ideal para intermedios"], ["Progreso puede estancarse"]),
    SplitTemplate("sheiko_3day", "Sheiko Clásico (3 Días)", "Estilo soviético.", [.POWERLIFTING, .ALTA_FRECUENCIA, .ALTA_TOLERANCIA], ["Sentadilla/Banca", "Descanso", "Peso Muerto/Banca", "Descanso", "Sentadilla/Banca", "Descanso", "Descanso"], .AVANZADO, ["Volumen ALTÍSIMO", "Técnica altamente refinada"], ["Volumen BRUTAL", "Solo avanzados"]),
    SplitTemplate("sheiko_4day", "Sheiko 4 Días", "Volumen distribuido.", [.POWERLIFTING, .ALTA_FRECUENCIA], ["Sentadilla", "Banca", "Descanso", "Peso Muerto", "Banca", "Descanso", "Descanso"], .AVANZADO, ["Volumen distribuido", "Más recuperación"], ["Volumen total brutal"]),
    SplitTemplate("bulgarian_lite", "Método Búlgaro (Lite)", "Alta intensidad diaria.", [.ALTA_FRECUENCIA, .ALTA_TOLERANCIA, .POWERLIFTING], ["SBD Max", "SBD Max", "SBD Max", "SBD Max", "SBD Max", "Descanso", "Descanso"], .AVANZADO, ["Intensidad máxima diaria", "Adaptación neural EXTREMA"], ["Solo élite", "Riesgo ALTÍSIMO"]),
    SplitTemplate("russian_bear", "Oso Ruso", "Volumen brutal con cargas moderadas.", [.ALTO_VOLUMEN, .ALTA_TOLERANCIA], ["Sentadilla/Banca", "Descanso", "Peso Muerto/Press", "Descanso", "Sentadilla/Banca", "Descanso", "Descanso"], .INTERMEDIO, ["Volumen alto con cargas manejables"], ["Volumen total alto", "Requiere buena nutrición"]),
    SplitTemplate("westside_conjugate", "Westside (Conjugado)", "Método Louie Simmons.", [.POWERLIFTING, .BALANCEADO], ["ME Lower", "ME Upper", "Descanso", "DE Lower", "DE Upper", "Descanso", "Descanso"], .AVANZADO, ["Desarrollo fuerza/potencia", "Variación constante"], ["Equipamiento específico", "Curva de aprendizaje alta"]),
    SplitTemplate("coan_split", "Split Ed Coan", "La distribución del GOAT.", [.POWERLIFTING, .BALANCEADO], ["Sentadilla/Pierna", "Descanso", "Press Banca/Pecho", "Peso Muerto/Espalda", "Hombros/Brazos", "Descanso", "Descanso"], .INTERMEDIO, ["Diseñado por el mejor powerlifter", "4 días manejables"], ["Requiere buena recuperación"]),
    SplitTemplate("bill_starr_5x5", "Bill Starr 5x5", "La base del atleta de fuerza.", [.POWERLIFTING, .ALTA_FRECUENCIA], ["Full Body Pesado", "Descanso", "Full Body Liviano", "Descanso", "Full Body Medio", "Descanso", "Descanso"], .PRINCIPIANTE, ["Ondulación de cargas clásica", "3 días manejables"], ["Frecuencia baja para avanzados"]),
    SplitTemplate("cube_method", "Método Cubo", "Rotación de esfuerzos.", [.POWERLIFTING, .BALANCEADO], ["Día Pesado", "Día Explosivo", "Descanso", "Día Repeticiones", "Accesorios Hipertrofia", "Descanso", "Descanso"], .AVANZADO, ["Variedad de estímulos", "Previene estancamientos"], ["Coordinación compleja"]),
    SplitTemplate("dorian_yates", "Blood & Guts (Yates)", "HIT. Bajo volumen, fallo absoluto.", [.BAJA_FRECUENCIA, .ALTA_TOLERANCIA], ["Hombro/Tríceps", "Espalda", "Descanso", "Pecho/Bíceps", "Piernas", "Descanso", "Descanso"], .AVANZADO, ["Intensidad MÁXIMA por serie", "Método de Mr. Olympia"], ["Frecuencia 1x/semana SUBÓPTIMA", "No para principiantes"]),
    SplitTemplate("mentzer_heavy_duty", "Heavy Duty (Mentzer)", "Una serie al fallo y a casa.", [.BAJA_FRECUENCIA], ["Pecho/Espalda", "Descanso", "Descanso", "Piernas", "Descanso", "Descanso", "Hombros/Brazos"], .AVANZADO, ["Volumen MÍNIMO", "Recuperación EXTREMA"], ["Frecuencia bajísima", "NO óptimo para naturales"]),
    SplitTemplate("arnold_classic_6", "Arnold Clásico 6 Días", "La rutina de la 'Enciclopedia'.", [.ALTO_VOLUMEN, .ALTA_FRECUENCIA], ["Pecho/Espalda", "Hombros/Brazos", "Piernas", "Pecho/Espalda", "Hombros/Brazos", "Piernas", "Descanso"], .AVANZADO, ["Volumen ALTÍSIMO", "Método del mejor culturista"], ["6 días requeridos", "Volumen excesivo para naturales"]),
    SplitTemplate("chinese_hybrid", "Híbrido Chino", "Énfasis en Squat y Pull diario.", [.ALTA_FRECUENCIA, .POWERLIFTING, .ALTA_TOLERANCIA], ["Squat/Press", "Pull/Accesorios", "Squat/Press", "Pull/Accesorios", "Squat Max", "Bodybuilding", "Descanso"], .AVANZADO, ["Frecuencia alta de sentadilla", "Método de equipo nacional"], ["Fatiga de sentadilla EXTREMA"]),
    SplitTemplate("531_bbb", "5/3/1 Boring But Big", "El clásico de Wendler.", [.POWERLIFTING, .BALANCEADO, .BAJA_FRECUENCIA], ["Press Militar/Hombro", "Peso Muerto/Espalda", "Descanso", "Press Banca/Pecho", "Sentadilla/Pierna", "Descanso", "Descanso"], .INTERMEDIO, ["Progresión lenta y sostenible", "Ideal para fuerza a largo plazo"], ["Progreso MUY lento"]),
    SplitTemplate("madcow_5x5", "Madcow 5x5", "Progresión lineal avanzada.", [.POWERLIFTING, .BALANCEADO], ["Volumen (5x5)", "Descanso", "Recuperación (Light)", "Descanso", "Intensidad (1x3/1x5)", "Descanso", "Descanso"], .INTERMEDIO, ["Progresión lineal probada", "3 días manejables"], ["Progreso se estanca en avanzados"]),
    SplitTemplate("korte_3x3", "Korte 3x3", "Escuela alemana. Solo SBD.", [.POWERLIFTING, .ALTA_FRECUENCIA], ["SBD (Volumen)", "Descanso", "SBD (Técnica)", "Descanso", "SBD (Intensidad)", "Descanso", "Descanso"], .AVANZADO, ["Especificidad MÁXIMA en SBD"], ["Cero accesorios", "Riesgo de desbalances"]),
    SplitTemplate("gzcl_method", "Método GZCL (Tiered)", "Estructura piramidal T1/T2/T3.", [.POWERLIFTING, .BALANCEADO], ["T1 Sentadilla", "T1 Banca", "Descanso", "T1 Peso Muerto", "T1 Militar", "Descanso", "Descanso"], .INTERMEDIO, ["Estructura clara", "Altamente personalizable"], ["Curva de aprendizaje media"]),
    SplitTemplate("tsa_inter", "TSA Intermedio", "The Strength Athlete. 4 días.", [.POWERLIFTING, .ALTA_FRECUENCIA], ["Sentadilla/Banca", "Peso Muerto/Accesorios", "Descanso", "Banca/Sentadilla Var.", "Peso Muerto/Banca Var.", "Descanso", "Descanso"], .INTERMEDIO, ["Frecuencia 2x/semana por lift", "Método moderno probado"], ["Coordinación compleja"]),
    SplitTemplate("calgary_barbell", "Estilo Calgary", "Alta variedad de ejercicios.", [.POWERLIFTING, .ALTA_FRECUENCIA, .BALANCEADO], ["Sentadilla/Banca", "Peso Muerto/Press", "Descanso", "Sentadilla/Banca (Var.)", "Peso Muerto (Var.)", "Descanso", "Descanso"], .AVANZADO, ["Variedad alta", "Gestión de fatiga precisa"], ["Coordinación compleja"]),
    SplitTemplate("deathbench_spec", "Deathbench (Especialización)", "Especialización EXTREMA en banca.", [.POWERLIFTING, .ALTO_VOLUMEN, .ALTA_TOLERANCIA], ["Banca Volumen", "Descanso", "Tríceps/Hombro", "Descanso", "Banca Intensidad", "Espalda/Bíceps", "Descanso"], .AVANZADO, ["Especialización EXTREMA", "Volumen altísimo de empuje"], ["Fatiga de hombros/codos CRÍTICA", "Solo especialistas"]),
    SplitTemplate("lilliebridge_method", "Método Lilliebridge", "Heavy/Light rotativo.", [.POWERLIFTING, .BAJA_FRECUENCIA], ["Sentadilla/PM Pesado", "Descanso", "Banca Pesada", "Descanso", "Sentadilla/PM Ligero", "Banca Ligera/Acc.", "Descanso"], .AVANZADO, ["Ondulación Heavy/Light", "Énfasis en peso muerto pesado"], ["Fatiga de peso muerto EXTREMA"]),
    SplitTemplate("conjugate_3day", "Conjugado 3 Días", "Westside adaptado.", [.POWERLIFTING, .BAJA_FRECUENCIA], ["Max Effort Lower", "Descanso", "Max Effort Upper", "Descanso", "Dynamic Effort (Full)", "Descanso", "Descanso"], .AVANZADO, ["Westside adaptado a 3 días"], ["Frecuencia más baja", "Curva de aprendizaje"]),
    SplitTemplate("ul_arms", "Upper / Lower + Brazos", "Estructura UL + especialización brazos.", [.ALTO_VOLUMEN], ["Torso", "Pierna", "Descanso", "Torso", "Pierna", "Brazos/Hombros", "Descanso"], .INTERMEDIO, ["Día dedicado de brazos", "4 días base + 1 especializado"], ["5 días requeridos"]),
    SplitTemplate("heavy_light", "Pesado / Liviano x3", "Ondulación de cuerpo completo en 3 días.", [.ALTA_FRECUENCIA, .BALANCEADO], ["Cuerpo Completo Pesado", "Descanso", "Cuerpo Completo Liviano", "Descanso", "Cuerpo Completo Moderado", "Descanso", "Descanso"], .INTERMEDIO, ["Ondulación de cargas clara", "Recuperación óptima"], ["Requiere autorregular bien la intensidad"]),
    SplitTemplate("ppl_x3", "Push / Pull / Legs x3", "PPL minimalista de 3 días para frecuencia moderada y buena recuperación.", [.BALANCEADO, .BAJA_FRECUENCIA], ["Empuje", "Descanso", "Tirón", "Descanso", "Pierna", "Descanso", "Descanso"], .PRINCIPIANTE, ["Muy fácil de organizar", "Sesiones específicas sin exceso de días"], ["Frecuencia 1x por patrón si no se rota semanalmente"]),
    SplitTemplate("fullbody_x4", "Cuerpo Completo x4", "Alta frecuencia con volumen distribuido en cuatro sesiones.", [.ALTA_FRECUENCIA, .BALANCEADO], ["Cuerpo Completo A", "Cuerpo Completo B", "Descanso", "Cuerpo Completo C", "Cuerpo Completo D", "Descanso", "Descanso"], .INTERMEDIO, ["Frecuencia alta sin entrenar todos los días", "Buen puente entre x3 y x5"], ["Puede ser exigente si todas las sesiones son pesadas"]),
    SplitTemplate("ul_fb_x3", "Upper / Lower + Cuerpo Completo", "Tres días: torso, pierna y cuerpo completo para cubrir frecuencia 2 sin mucho calendario.", [.BALANCEADO], ["Torso", "Descanso", "Pierna", "Descanso", "Cuerpo Completo", "Descanso", "Descanso"], .PRINCIPIANTE, ["Frecuencia 2x práctica", "Ideal para agendas de 3 días"], ["El día full body debe mantenerse controlado"]),
]
