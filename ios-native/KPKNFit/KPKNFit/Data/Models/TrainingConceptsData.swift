import SwiftUI

public enum ConceptCategory: String, CaseIterable {
    case LOAD_MANAGEMENT
    case INTENSITY
    case FATIGUE
    case HYPERTROPHY
    case MOVEMENT
    case EQUIPMENT
    case QUALITIES
    case PERIODIZATION

    public var label: String {
        switch self {
        case .LOAD_MANAGEMENT: return "Gestión de Carga"
        case .INTENSITY: return "Intensidad y Esfuerzo"
        case .FATIGUE: return "Fatiga y Recuperación"
        case .HYPERTROPHY: return "Mecanismos de Hipertrofia"
        case .MOVEMENT: return "Mecánica del Movimiento"
        case .EQUIPMENT: return "Equipamiento y Medios"
        case .QUALITIES: return "Cualidades Físicas"
        case .PERIODIZATION: return "Periodización"
        }
    }

    public var color: Color {
        switch self {
        case .LOAD_MANAGEMENT: return Color(hex: 0xE53935)
        case .INTENSITY: return Color(hex: 0xFFFF8F00)
        case .FATIGUE: return Color(hex: 0x9C27B0)
        case .HYPERTROPHY: return Color(hex: 0x1E88E5)
        case .MOVEMENT: return Color(hex: 0x43A047)
        case .EQUIPMENT: return Color(hex: 0x00897B)
        case .QUALITIES: return Color(hex: 0x5C6BC0)
        case .PERIODIZATION: return Color(hex: 0x795548)
        }
    }

    public var icon: String {
        switch self {
        case .LOAD_MANAGEMENT: return "barbell"
        case .INTENSITY: return "flame"
        case .FATIGUE: return "recovery"
        case .HYPERTROPHY: return "muscle"
        case .MOVEMENT: return "movement"
        case .EQUIPMENT: return "equipment"
        case .QUALITIES: return "qualities"
        case .PERIODIZATION: return "calendar"
        }
    }
}

public struct TrainingConcept: Identifiable {
    public let id: String
    public let name: String
    public let category: ConceptCategory
    public let shortDescription: String
    public let definition: String
    public let practicalApplication: String
    public let keyPoints: [String]
    public let relatedConcepts: [String]
    public let examples: [String]
    public let commonMistakes: [String]

    public init(
        id: String,
        name: String,
        category: ConceptCategory,
        shortDescription: String,
        definition: String,
        practicalApplication: String,
        keyPoints: [String],
        relatedConcepts: [String] = [],
        examples: [String] = [],
        commonMistakes: [String] = []
    ) {
        self.id = id
        self.name = name
        self.category = category
        self.shortDescription = shortDescription
        self.definition = definition
        self.practicalApplication = practicalApplication
        self.keyPoints = keyPoints
        self.relatedConcepts = relatedConcepts
        self.examples = examples
        self.commonMistakes = commonMistakes
    }
}



/**
 * Training Concepts Database — "Conceptos Clave" del entrenamiento.
 *
 * Cada concepto incluye definición completa, categoría, relevancia práctica,
 * y relaciones con otros conceptos para navegación cruzada tipo Wikipedia.
 */



public let TRAINING_CONCEPTS_DATABASE: [TrainingConcept] = [

    // ═══════════════════════════════════════════════════════════════════
    // GESTIÓN DE CARGA
    // ═══════════════════════════════════════════════════════════════════

    TrainingConcept(
        id: "volumen-entrenamiento",
        name: "Volumen de Entrenamiento",
        category: ConceptCategory.LOAD_MANAGEMENT,
        shortDescription: "Cantidad total de trabajo realizado en una sesión o semana",
        definition: "El volumen de entrenamiento representa la cantidad total de trabajo mecánico realizado. Se puede cuantificar de varias formas: número total de series efectivas (sets) por grupo muscular por semana, repeticiones totales (series × repeticiones), o tonelaje total (series × repeticiones × peso). En el contexto del entrenamiento de hipertrofia, la métrica más utilizada es el número de series duras (cerca del fallo) por grupo muscular por semana.",
        practicalApplication: "Para hipertrofia, la mayoría de individuos responden bien a 10-20 series semanales por grupo muscular. El volumen debe individualizarse según la capacidad de recuperación, la experiencia del atleta y los objetivos. Es preferible distribuir el volumen en múltiples sesiones semanales en lugar de concentrarlo en una sola.",
        keyPoints: [
            "El volumen óptimo es individual: depende de tu capacidad de recuperación, nivel de entrenamiento y objetivos",
            "El volumen se ajusta progresivamente en función de la respuesta real del cuerpo, no de rangos genéricos",
            "En KPKN el volumen se personaliza sesión a sesión, considerando fatiga acumulada, sueño, nutrición y estrés",
            "Más volumen no siempre es mejor; existe un punto de rendimientos decrecientes que varía por persona",
            "Solo cuentan como volumen efectivo las series que generan estímulo real (cercanas al fallo técnico)",
        ],
        relatedConcepts: ["intensidad", "frecuencia", "fatiga-sistemica", "rir", "fallo-muscular"],
        examples: [
            "10 series de pecho/semana distribuidas en 2 sesiones: 5 series por sesión",
            "Tonelaje: 4×10×80kg: 3200kg de volumen total en ese ejercicio",
        ],
        commonMistakes: [
            "Contar series de calentamiento como volumen efectivo",
            "Agregar volumen sin mejorar la calidad de cada serie",
            "No ajustar el volumen cuando cambian otros estresores (sueño, dieta, estrés)",
        ],
    ),

    TrainingConcept(
        id: "intensidad",
        name: "Intensidad",
        category: ConceptCategory.LOAD_MANAGEMENT,
        shortDescription: "Porcentaje de carga relativa al máximo o nivel de esfuerzo",
        definition: "La intensidad tiene dos acepciones en el entrenamiento. La intensidad relativa se refiere al porcentaje del 1RM (repetición máxima) utilizado — por ejemplo, trabajar al 80% del 1RM. La intensidad del esfuerzo se refiere a cuán cerca se llega del fallo muscular en cada serie, medida típicamente con escalas como RIR o RPE. Ambas dimensiones son cruciales para programar el entrenamiento.",
        practicalApplication: "Para hipertrofia, se recomienda trabajar en un rango del 60-85% del 1RM con 6-15 repeticiones, llegando a 0-3 RIR. Para fuerza máxima, se trabaja al 85-100% del 1RM con 1-5 repeticiones. La intensidad debe manipularse junto con el volumen: cuando la intensidad sube, el volumen generalmente debe bajar.",
        keyPoints: [
            "Intensidad relativa: % del 1RM — cuánto peso respecto al máximo",
            "Intensidad del esfuerzo: proximidad al fallo muscular",
            "Ambas dimensiones son independientes pero interactúan",
            "La hipertrofia es posible en un rango amplio de intensidades (30-85% 1RM) si el esfuerzo es suficiente",
            "La fuerza máxima requiere intensidades altas (>85% 1RM) por especificidad neural",
        ],
        relatedConcepts: ["rir", "rpe", "fallo-muscular", "volumen-entrenamiento", "tension-mecanica"],
        examples: [
            "Press banca con 100kg cuando tu 1RM es 120kg: 83% de intensidad relativa",
            "Hacer 10 repeticiones cuando podrías hacer 12: RIR 2 (intensidad de esfuerzo moderada-alta)",
        ],
        commonMistakes: [
            "Confundir 'entrenar pesado' con 'entrenar intenso' — no son lo mismo",
            "Entrenar siempre al fallo creyendo que más intensidad siempre es mejor",
            "Ignorar la intensidad del esfuerzo y contar solo el peso en la barra",
        ],
    ),

    TrainingConcept(
        id: "frecuencia",
        name: "Frecuencia de Entrenamiento",
        category: ConceptCategory.LOAD_MANAGEMENT,
        shortDescription: "Número de veces que se entrena un grupo muscular por semana",
        definition: "La frecuencia de entrenamiento se refiere a cuántas veces por semana se estimula un grupo muscular o patrón de movimiento. La investigación moderna sugiere que distribuir el volumen semanal en 2-4 sesiones por grupo muscular es más efectivo que concentrarlo en una sola sesión, principalmente porque permite mayor volumen total con mejor calidad de ejecución y menor fatiga acumulada por sesión.",
        practicalApplication: "Para la mayoría de personas, entrenar cada grupo muscular 2-3 veces por semana es óptimo. Esto permite distribuir 10-20 series semanales en bloques manejables de 4-7 series por sesión. Frecuencias más altas (4-6x/semana) pueden ser útiles para grupos musculares retrasados o para atletas avanzados que necesitan alto volumen.",
        keyPoints: [
            "2x/semana por grupo muscular es un mínimo recomendado para hipertrofia",
            "La síntesis proteica muscular se eleva 24-72h post-entrenamiento",
            "Más frecuencia permite mejor distribución del volumen total",
            "Entrenar con demasiada frecuencia sin recuperación adecuada puede ser contraproducente",
            "La frecuencia óptima depende del volumen total, intensidad y capacidad de recuperación",
        ],
        relatedConcepts: ["volumen-entrenamiento", "fatiga-sistemica", "dano-muscular"],
        examples: [
            "Full body 3x/semana: cada músculo se trabaja 3 veces con 4-5 series por sesión",
            "Push/Pull/Legs 2x: cada músculo 2 veces/semana con 6-8 series por sesión",
        ],
        commonMistakes: [
            "Entrenar un músculo solo 1x/semana con volumen excesivo en esa sesión",
            "Aumentar frecuencia sin reducir volumen por sesión (acumular fatiga)",
        ],
    ),

    // ═══════════════════════════════════════════════════════════════════
    // INTENSIDAD Y ESFUERZO
    // ═══════════════════════════════════════════════════════════════════

    TrainingConcept(
        id: "rir",
        name: "RIR (Repeticiones en Reserva)",
        category: ConceptCategory.INTENSITY,
        shortDescription: "Cuántas repeticiones te quedan antes de llegar al fallo",
        definition: "RIR (Reps In Reserve) es una escala subjetiva que mide cuántas repeticiones adicionales podrías haber completado antes de alcanzar el fallo muscular concéntrico. Un RIR de 0 significa que no podrías haber hecho una repetición más (fallo), RIR 1 significa que quedaba exactamente 1 repetición, RIR 2 que quedaban 2, y así sucesivamente. Es la herramienta más práctica para autorregular la intensidad del esfuerzo.",
        practicalApplication: "Para hipertrofia, se recomienda trabajar a RIR 0-3 en la mayoría de series. Las series a RIR 4+ generalmente no generan suficiente estímulo mecánico. Para fuerza, RIR 1-3 con cargas pesadas es lo ideal. La precisión del RIR mejora con la experiencia — los principiantes tienden a sobreestimar su RIR (creen que les quedan 3 repeticiones cuando en realidad podrían hacer 5+).",
        keyPoints: [
            "RIR 0: Fallo muscular concéntrico",
            "RIR 1-2: Zona óptima para hipertrofia (alto estímulo, fatiga manejable)",
            "RIR 3-4: Útil para volumen de mantenimiento o trabajo técnico",
            "RIR 5+ = Generalmente insuficiente para generar adaptaciones de hipertrofia",
            "La precisión del RIR se desarrolla con la experiencia y autoconocimiento",
        ],
        relatedConcepts: ["rpe", "fallo-muscular", "intensidad", "volumen-entrenamiento"],
        examples: [
            "Sentadilla: haces 8 reps y sientes que podrías hacer 2 más → RIR 2",
            "Curl de bíceps: completas 12 reps y no puedes ni una más → RIR 0 (fallo)",
        ],
        commonMistakes: [
            "Principiantes que paran a RIR 5 creyendo estar a RIR 1",
            "Usar el mismo RIR para todos los ejercicios sin considerar el riesgo",
            "No ajustar el peso entre series cuando el RIR cambia significativamente",
        ],
    ),

    TrainingConcept(
        id: "rpe",
        name: "RPE (Percepción del Esfuerzo)",
        category: ConceptCategory.INTENSITY,
        shortDescription: "Escala subjetiva de esfuerzo percibido del 1 al 10",
        definition: "RPE (Rate of Perceived Exertion) en el contexto del entrenamiento de fuerza es una escala del 1 al 10 que mide el esfuerzo percibido. En la escala adaptada de Tuchscherer, RPE 10: fallo muscular (no podías hacer otra repetición), RPE 9: 1 repetición en reserva, RPE 8: 2 en reserva, etc. Es inversa al RIR: RPE: 10 - RIR. Se usa extensivamente en powerlifting y entrenamiento de fuerza para autorregular cargas.",
        practicalApplication: "La programación basada en RPE permite ajustar las cargas diariamente según el estado del atleta. Si un programa indica 'Sentadilla 3×5 @RPE 8', el atleta debe usar un peso con el que complete 5 repeticiones sintiéndose como si pudiera hacer 2 más. Esto es superior a programar porcentajes fijos del 1RM porque se adapta automáticamente a la variabilidad diaria de rendimiento.",
        keyPoints: [
            "RPE 10: Esfuerzo máximo, fallo (RIR 0)",
            "RPE 9: Muy duro, 1 rep en reserva",
            "RPE 8: Duro pero controlado, 2 reps en reserva",
            "RPE 7: Moderado, velocidad clara en la barra",
            "RPE 6: Fácil, peso de calentamiento pesado",
        ],
        relatedConcepts: ["rir", "fallo-muscular", "intensidad", "fatiga-sistemica"],
        examples: [
            "Peso muerto @RPE 8: usas 180kg para 3 reps, podrías hacer 5 totales",
            "Press banca @RPE 9.5: casi fallo pero la última rep fue grindeable",
        ],
        commonMistakes: [
            "No calibrar la escala con intentos reales al fallo",
            "Confundir dificultad cardiovascular con RPE muscular",
        ],
    ),

    TrainingConcept(
        id: "fallo-muscular",
        name: "Fallo Muscular",
        category: ConceptCategory.INTENSITY,
        shortDescription: "Punto donde no puedes completar otra repetición concéntrica",
        definition: "El fallo muscular concéntrico es el punto en una serie donde la fuerza producida por los músculos es insuficiente para superar la resistencia externa, haciendo imposible completar la fase concéntrica (levantamiento) de otra repetición con técnica aceptable. Existen distintos tipos: fallo técnico (la técnica se deteriora significativamente), fallo concéntrico (no puedes levantar el peso) y fallo absoluto (no puedes ni controlar la excéntrica).",
        practicalApplication: "Entrenar al fallo no es necesario para maximizar la hipertrofia — llevar las series a RIR 1-2 es casi igual de efectivo con mucha menos fatiga. Sin embargo, series ocasionales al fallo son útiles para calibrar la percepción del esfuerzo y asegurar que el estímulo es suficiente. Evitar el fallo es especialmente importante en ejercicios multiarticulares pesados (sentadilla, peso muerto) por el riesgo de lesión y la alta fatiga sistémica generada.",
        keyPoints: [
            "Fallo técnico: la forma se degrada — punto ideal para detener la serie",
            "Fallo concéntrico: imposible completar la fase de levantamiento",
            "Fallo absoluto/excéntrico: pérdida de control total (peligroso)",
            "El fallo genera ~2-3x más fatiga que una serie a RIR 2 con similar estímulo",
            "Más relevante en ejercicios de aislamiento que en compuestos pesados",
        ],
        relatedConcepts: ["rir", "rpe", "fatiga-sistemica", "dano-muscular", "tension-mecanica"],
        examples: [
            "Press banca: la barra se queda a mitad de camino en la rep 9 → fallo concéntrico",
            "Extensión de cuádriceps: el peso no sube del punto más bajo → fallo en sticking point",
        ],
        commonMistakes: [
            "Ir al fallo en cada serie de cada ejercicio — genera fatiga excesiva",
            "Usar fallo en ejercicios de alto riesgo (sentadilla, peso muerto) rutinariamente",
            "No distinguir entre fallo técnico y fallo concéntrico",
        ],
    ),

    // ═══════════════════════════════════════════════════════════════════
    // FATIGA Y RECUPERACIÓN
    // ═══════════════════════════════════════════════════════════════════

    TrainingConcept(
        id: "fatiga-sistemica",
        name: "Fatiga Sistémica",
        category: ConceptCategory.FATIGUE,
        shortDescription: "Fatiga acumulada que afecta al organismo completo, no solo al músculo",
        definition: "La fatiga sistémica es la acumulación de estrés que afecta al sistema nervioso central, al eje hormonal y al metabolismo global, más allá del cansancio localizado en un músculo específico. Se manifiesta como reducción general del rendimiento, alteración del sueño, irritabilidad, pérdida de apetito y mayor susceptibilidad a enfermedades. A diferencia de la fatiga muscular local (que se recupera en 48-72h), la fatiga sistémica puede tardar semanas en disiparse.",
        practicalApplication: "La fatiga sistémica debe monitorearse y gestionarse con deloads programados (semanas de descarga cada 4-8 semanas), control del volumen total de entrenamiento, priorización del sueño (7-9h) y gestión del estrés global. Ejercicios como sentadilla pesada y peso muerto generan más fatiga sistémica que ejercicios de aislamiento, y deben programarse con cuidado.",
        keyPoints: [
            "Afecta al sistema nervioso central, endocrino e inmunológico",
            "Se acumula semana a semana si no se gestiona con deloads",
            "Ejercicios compuestos pesados la generan en mayor grado",
            "El sueño, la nutrición y el estrés psicológico la modulan significativamente",
            "Se disipa con deloads (reducción de volumen/intensidad ~40-60%)",
        ],
        relatedConcepts: ["volumen-entrenamiento", "intensidad", "fallo-muscular", "dano-muscular"],
        examples: [
            "Tras 5 semanas de volumen progresivo, sientes que los pesos 'pesan más' aun sin cambiar nada",
            "Después de un mesociclo duro, un deload de 1 semana restaura el rendimiento",
        ],
        commonMistakes: [
            "Ignorar señales de fatiga sistémica y seguir empujando",
            "Confundir fatiga sistémica con falta de motivación",
            "No programar deloads regulares",
        ],
    ),

    TrainingConcept(
        id: "dano-muscular",
        name: "Daño Muscular",
        category: ConceptCategory.FATIGUE,
        shortDescription: "Microlesiones en las fibras musculares causadas por el entrenamiento",
        definition: "El daño muscular inducido por el ejercicio (EIMD) consiste en microlesiones en las fibras musculares, especialmente en las proteínas estructurales como la titina y la desmina. Se produce principalmente durante la fase excéntrica del movimiento y es uno de los tres mecanismos propuestos de hipertrofia (junto con la tensión mecánica y el estrés metabólico). Se manifiesta como DOMS (agujetas), reducción temporal de la fuerza y aumento de marcadores inflamatorios.",
        practicalApplication: "Algo de daño muscular es inevitable y potencialmente beneficioso para la hipertrofia, pero el exceso es contraproducente: aumenta el tiempo de recuperación sin beneficio proporcional. El daño se minimiza con progresiones graduales de volumen/intensidad, frecuencia alta (más exposición = efecto de repetición = menos daño), y evitando cargas excéntricas extremas sin preparación previa.",
        keyPoints: [
            "La fase excéntrica causa más daño que la concéntrica",
            "Las agujetas (DOMS) no son indicador fiable de un buen entrenamiento",
            "El 'efecto de protección por repetición' reduce el daño con la exposición regular",
            "Exceso de daño compromete la capacidad de entrenar en la próxima sesión",
            "Nuevos ejercicios o rangos de movimiento causan más daño inicialmente",
        ],
        relatedConcepts: ["fase-excentrica", "fatiga-sistemica", "tension-mecanica", "frecuencia"],
        examples: [
            "Sentadillas profundas después de semanas sin hacerlas → DOMS severo por 3-4 días",
            "Curl nórdico por primera vez → daño excéntrico significativo en isquiotibiales",
        ],
        commonMistakes: [
            "Buscar agujetas como indicador de efectividad del entrenamiento",
            "Cambiar ejercicios constantemente para 'sorprender al músculo' (más daño innecesario)",
        ],
    ),

    // ═══════════════════════════════════════════════════════════════════
    // MECANISMOS DE HIPERTROFIA
    // ═══════════════════════════════════════════════════════════════════

    TrainingConcept(
        id: "tension-mecanica",
        name: "Tensión Mecánica (y Torque Articular)",
        category: ConceptCategory.HYPERTROPHY,
        shortDescription: "Fuerza y torque aplicado sobre el músculo durante su contracción",
        definition: "La tensión mecánica es la fuerza longitudinal que experimentan las fibras musculares cuando se contraen contra una resistencia o torque articular. Es el mecanismo primario de hipertrofia. Se maximiza combinando una carga que demanda alto esfuerzo con un rango de movimiento donde el brazo de momento externo exige gran torque articular, especialmente en la posición de elongación muscular. La mecanotransducción convierte esta tensión en síntesis proteica.",
        practicalApplication: "Maximizar tensión mecánica no es simplemente 'poner más peso en la barra'. Es asegurar que el Torque que llega a la articulación objetivo es alto. Usa cargas del 60-85% del 1RM con control excéntrico y RIR 0-3. Selecciona ejercicios con perfiles de resistencia que desafíen la longitud óptima del músculo (donde la relación longitud-tensión y el brazo de momento maximicen el estímulo).",
        keyPoints: [
            "Es el mecanismo primario para la hipertrofia muscular (muy por encima del estrés metabólico).",
            "Requiere Torque articular efectivo: Tensión real en la articulación, no solo kilos en las manos.",
            "La tensión pasiva bajo estiramiento es el estímulo más potente conocido actualmente.",
            "La mecanotransducción activa vías de señalización anabólicas (mTOR).",
            "Un rango parcial pesado puede generar menos tensión efectiva que un rango completo con menos peso."
        ],
        relatedConcepts: ["perfil-resistencia", "rom", "fase-excentrica", "fuerza"],
        examples: [
            "Prensa Inclinada: Carga máxima en elongación profunda, alto torque sobre cuádriceps.",
            "Curl concentrado: Máxima tensión al inicio cuando el brazo de momento es más largo."
        ],
        commonMistakes: [
            "Cargar más peso perdiendo Torque sobre el músculo objetivo (compensación con sinergistas o inercia).",
            "Creer que quemazón (lactato) significa alta tensión mecánica (son mecanismos distintos)."
        ],
    ),

    TrainingConcept(
        id: "estres-metabolico",
        name: "Estrés Metabólico",
        category: ConceptCategory.HYPERTROPHY,
        shortDescription: "Acumulación de metabolitos durante series de alta repetición",
        definition: "El estrés metabólico se refiere a la acumulación de subproductos metabólicos (lactato, iones de hidrógeno, fosfato inorgánico) durante contracciones musculares sostenidas, especialmente en series de repeticiones moderadas-altas (12-30 reps). Esta acumulación genera hinchazón celular (cell swelling), aumenta la activación de fibras musculares por fatiga periférica, y puede contribuir a la hipertrofia como mecanismo secundario a la tensión mecánica.",
        practicalApplication: "Se maximiza con series de 12-30 repeticiones con descansos cortos (30-90s), técnicas como series de descenso (drop sets), rest-pause, y superseries. Es especialmente útil como complemento al entrenamiento pesado en ejercicios de aislamiento. El 'pump' es una manifestación del estrés metabólico — útil pero no imprescindible.",
        keyPoints: [
            "Mecanismo secundario de hipertrofia (la tensión mecánica es primaria)",
            "Se genera con repeticiones altas, descansos cortos y tensión continua",
            "Causa 'pump' muscular (hinchazón celular temporal)",
            "Puede contribuir a la hipertrofia via señalización celular",
            "Especialmente útil en ejercicios de aislamiento como 'finisher'",
        ],
        relatedConcepts: ["tension-mecanica", "dano-muscular", "fallo-muscular", "intensidad"],
        examples: [
            "3 series de leg extension 20 reps con 45s de descanso → pump intenso",
            "Drop set en curl de bíceps: 15kg×12 + 10kg×10 + 7kg×12 sin descanso",
        ],
        commonMistakes: [
            "Basar todo el entrenamiento en el pump ignorando la tensión mecánica",
            "Creer que más pump: más hipertrofia",
        ],
    ),

    // ═══════════════════════════════════════════════════════════════════
    // MECÁNICA DEL MOVIMIENTO
    // ═══════════════════════════════════════════════════════════════════

    TrainingConcept(
        id: "rom",
        name: "ROM (Rango de Movimiento)",
        category: ConceptCategory.MOVEMENT,
        shortDescription: "La amplitud total del movimiento articular durante un ejercicio",
        definition: "El ROM (Range of Motion) es la amplitud angular total que recorre una articulación durante un ejercicio. La investigación reciente demuestra que el entrenamiento con ROM completo — y especialmente el entrenamiento en rangos de movimiento largos donde el músculo está en posición de estiramiento — produce mayor hipertrofia que los rangos parciales. Esto se debe a que la tensión mecánica en la posición de estiramiento activa vías de señalización anabólica de forma más potente.",
        practicalApplication: "Prioriza siempre el ROM completo antes de aumentar carga. Las repeticiones parciales pueden tener uso complementario (ej: parciales en la parte baja de la sentadilla), pero no deben reemplazar el ROM completo. Algunos ejercicios son especialmente valiosos por su ROM en estiramiento: sentadilla profunda, press inclinado con mancuernas, curl inclinado, elevación lateral con cable detrás del cuerpo.",
        keyPoints: [
            "ROM completo produce mayor hipertrofia que parciales en la mayoría de estudios",
            "La posición de estiramiento bajo carga es la más efectiva para hipertrofia",
            "Cada articulación tiene un ROM anatómico y un ROM funcional bajo carga",
            "La movilidad limita el ROM — trabajar movilidad es trabajar hipertrofia",
            "Los parciales tienen uso puntual para sticking points o como técnica avanzada",
        ],
        relatedConcepts: ["tension-mecanica", "fase-excentrica", "perfil-resistencia", "carga-axial"],
        examples: [
            "Sentadilla profunda (ROM completo) vs. media sentadilla (ROM parcial)",
            "Press con mancuernas bajando hasta que los codos pasen la línea del torso",
        ],
        commonMistakes: [
            "Cargar más peso a costa de reducir el rango de movimiento",
            "No diferenciar entre limitación de movilidad y ROM del ejercicio",
        ],
    ),

    TrainingConcept(
        id: "perfil-resistencia",
        name: "Perfil de Resistencia y Curvas de Fuerza",
        category: ConceptCategory.MOVEMENT,
        shortDescription: "Cómo varía la dificultad y el brazo de momento a lo largo del recorrido",
        definition: "El perfil de resistencia describe cómo cambia la fuerza que debe generar el músculo a lo largo del rango de movimiento debido a variaciones en el brazo de momento externo (la distancia perpendicular entre la línea de fuerza y la articulación). Al mismo tiempo, el músculo tiene su propia 'curva de fuerza' interna basada en su longitud (relación longitud-tensión) y su brazo de momento interno. Un ejercicio es ideal cuando su perfil de resistencia coincide con la curva de fuerza del músculo.",
        practicalApplication: "Buscar ejercicios con perfil descendente (más difíciles al inicio/estiramiento) produce mayor hipertrofia por la tensión mecánica bajo elongación. Las poleas permiten manipular el brazo de momento externo: ajustando la altura del cable cambias en qué ángulo el cable está a 90° respecto a la extremidad (el punto de máxima resistencia o 'sticking point').",
        keyPoints: [
            "Brazo de Momento: Distancia perpendicular de la fuerza al eje articular. Define el Torque real que siente el músculo.",
            "Perfil descendente: Máxima resistencia en el estiramiento (ej. Sentadilla profunda, Aperturas).",
            "Perfil ascendente: Máxima resistencia al final (ej. Extensión de cuádriceps, Elevación lateral).",
            "Las poleas permiten mover el brazo de momento máximo a cualquier ángulo deseado.",
            "Palancas corporales: Casi todas son de 3er grado (desventaja mecánica, el músculo genera mucha más fuerza que la carga externa)."
        ],
        relatedConcepts: ["rom", "tension-mecanica", "poleas", "fuerza"],
        examples: [
            "Elevación lateral con mancuerna: Brazo de momento nulo abajo, máximo a 90° (perfil ascendente).",
            "Curl predicador vs Curl inclinado: El soporte cambia dónde ocurre el brazo de momento máximo respecto a la gravedad."
        ],
        commonMistakes: [
            "Juzgar un ejercicio solo por el peso levantado ignorando el brazo de momento.",
            "Usar bandas elásticas que sobrecargan el final del movimiento cuando el músculo necesita estímulo en el estiramiento."
        ],
    ),

    TrainingConcept(
        id: "fase-excentrica",
        name: "Fase Excéntrica",
        category: ConceptCategory.MOVEMENT,
        shortDescription: "La fase de descenso controlado donde el músculo se alarga bajo tensión",
        definition: "La fase excéntrica es la porción del movimiento donde el músculo se alarga mientras produce fuerza. Ejemplo: bajar la barra en un press de banca, descender en una sentadilla, o bajar controladamente un curl de bíceps. El músculo es ~20-30% más fuerte en la fase excéntrica que en la concéntrica, lo que permite usar cargas supramáximas (excéntricos acentuados). La excéntrica causa mayor daño muscular y genera señales anabólicas potentes.",
        practicalApplication: "Controlar la fase excéntrica (2-4 segundos) es una de las formas más sencillas de mejorar la calidad del entrenamiento. No dejes que la gravedad haga el trabajo — resiste activamente. Las excéntricas acentuadas (5-6 segundos o con carga supramáxima) son técnicas avanzadas para atletas experimentados. La excéntrica bajo estiramiento es el estímulo más potente para hipertrofia.",
        keyPoints: [
            "El músculo es más fuerte excéntricamente que concéntricamente (~120-130%)",
            "Genera mayor daño muscular que la fase concéntrica",
            "Controlar la excéntrica en 2-4 segundos mejora el estímulo significativamente",
            "Los excéntricos acentuados son herramienta avanzada de sobrecarga",
            "Las negativas lentas son excelentes para rehabilitación de tendones",
        ],
        relatedConcepts: ["fase-concentrica", "isometria", "dano-muscular", "tension-mecanica", "rom"],
        examples: [
            "Press banca: la fase de bajar la barra al pecho: excéntrica del pectoral",
            "Curl nórdico: la excéntrica controlada de isquiotibiales bajo peso corporal",
        ],
        commonMistakes: [
            "Dejar caer el peso en la fase excéntrica (perder tensión y estímulo)",
            "Abusar de excéntricas muy lentas que generan exceso de daño muscular",
        ],
    ),

    TrainingConcept(
        id: "fase-concentrica",
        name: "Fase Concéntrica",
        category: ConceptCategory.MOVEMENT,
        shortDescription: "La fase de levantamiento donde el músculo se acorta produciendo fuerza",
        definition: "La fase concéntrica es la porción del movimiento donde el músculo se acorta mientras genera fuerza para superar la resistencia. Ejemplo: empujar la barra hacia arriba en press de banca, levantarse en la sentadilla, subir el peso en un curl. Es la fase que tradicionalmente se asocia con 'levantar peso'. La intención de velocidad en la concéntrica (empujar/tirar lo más rápido posible) puede aumentar el reclutamiento de unidades motoras de alto umbral.",
        practicalApplication: "En general, la fase concéntrica debe realizarse con intención de máxima velocidad (aunque la barra se mueva lento por el peso). Esta intención explosiva maximiza el reclutamiento de fibras tipo II (las de mayor potencial de hipertrofia). Sin embargo, en ciertos contextos de hipertrofia (series ligeras, aislamiento), una concéntrica controlada de 1-2 segundos también es efectiva.",
        keyPoints: [
            "Es la fase donde se supera la gravedad/resistencia",
            "La intención de velocidad explosiva maximiza el reclutamiento de fibras tipo II",
            "El 'sticking point' es la zona más difícil de la fase concéntrica",
            "Las repeticiones concéntricas puras (sin excéntrica) generan menos fatiga",
            "La velocidad real de la barra disminuye a medida que se acerca al fallo",
        ],
        relatedConcepts: ["fase-excentrica", "isometria", "tension-mecanica", "explosividad"],
        examples: [
            "Sentadilla: la fase de levantarse desde abajo: concéntrica de cuádriceps y glúteos",
            "Dominadas: la fase de subir el cuerpo hacia la barra: concéntrica de dorsal",
        ],
        commonMistakes: [
            "Mover la barra demasiado lento deliberadamente en ejercicios pesados",
            "Usar impulso excesivo que elimina la tensión del músculo objetivo",
        ],
    ),

    TrainingConcept(
        id: "isometria",
        name: "Isometría",
        category: ConceptCategory.MOVEMENT,
        shortDescription: "Contracción muscular sin cambio en la longitud del músculo",
        definition: "Una contracción isométrica ocurre cuando el músculo genera fuerza sin cambiar su longitud — no hay movimiento articular visible. Puede ocurrir manteniendo una posición estática (ej: plancha, pausa en la sentadilla) o intentando mover un objeto inamovible (ej: empujar contra una pared). Las isometrías en posición de estiramiento han demostrado ser particularmente efectivas para hipertrofia y rehabilitación tendinosa.",
        practicalApplication: "Las pausas isométricas (2-3 segundos) en el punto de estiramiento de un ejercicio aumentan el estímulo hipertrófico. Las isometrías de larga duración a baja intensidad son excelentes para rehabilitación de tendones (ej: isométricos de cuádriceps para tendinopatía rotuliana). También son útiles para mejorar el control motor y la consciencia de la posición articular.",
        keyPoints: [
            "No hay movimiento articular pero sí producción de fuerza",
            "Isometría en estiramiento: efectiva para hipertrofia y rehabilitación",
            "Isometría en contracción máxima: efectiva para fuerza en ángulos específicos",
            "Las pausas isométricas eliminan el 'stretch-shortening cycle'",
            "Generan menos fatiga sistémica que las contracciones dinámicas",
        ],
        relatedConcepts: ["fase-excentrica", "fase-concentrica", "tension-mecanica", "pliometria"],
        examples: [
            "Sentadilla con pausa de 3s en el punto más bajo",
            "Plancha abdominal: isometría de todo el core",
            "Wall sit (sentarse contra la pared): isometría de cuádriceps",
        ],
        commonMistakes: [
            "Contener la respiración durante isometrías largas (maniobra de Valsalva excesiva)",
            "Usar solo isometría sin contracciones dinámicas en un programa completo",
        ],
    ),

    TrainingConcept(
        id: "pliometria",
        name: "Pliometría",
        category: ConceptCategory.MOVEMENT,
        shortDescription: "Movimientos explosivos que aprovechan el ciclo estiramiento-acortamiento",
        definition: "La pliometría se refiere a ejercicios que explotan el ciclo estiramiento-acortamiento (CEA) del músculo y el tendón. Cuando un músculo se estira rápidamente (fase excéntrica), almacena energía elástica que puede reutilizarse en la subsiguiente contracción concéntrica, produciendo más fuerza y potencia que una contracción concéntrica aislada. Ejemplos clásicos incluyen saltos, lanzamientos y movimientos balísticos.",
        practicalApplication: "Los ejercicios pliométricos mejoran la potencia, la velocidad y la capacidad reactiva. Son esenciales para deportes que requieren saltos, sprints y cambios de dirección. Se deben programar al inicio de la sesión (cuando el sistema nervioso está fresco), con descansos completos (2-5 minutos), bajo volumen (3-5 series de 3-6 repeticiones) y progresión gradual de la intensidad de impacto.",
        keyPoints: [
            "Aprovecha el ciclo estiramiento-acortamiento del complejo músculo-tendón",
            "El tiempo de contacto con el suelo debe ser mínimo para maximizar la elasticidad",
            "Requiere base de fuerza previa (poder hacer sentadilla 1.5x peso corporal como referencia)",
            "Bajo volumen + alta calidad + descansos completos",
            "Progresión: bilateral → unilateral → profundidad → multidireccional",
        ],
        relatedConcepts: ["explosividad", "elasticidad", "fase-excentrica", "fase-concentrica"],
        examples: [
            "Salto al cajón (box jump): CEA de cuádriceps, glúteos y gemelos",
            "Drop jump: caer desde una altura y rebotar inmediatamente",
            "Pase de pecho con balón medicinal: pliometría de tren superior",
        ],
        commonMistakes: [
            "Hacer pliometría fatigado (riesgo de lesión y pérdida de calidad)",
            "Volumen excesivo de saltos sin base de fuerza previa",
            "Tiempos de contacto largos que eliminan el componente elástico",
        ],
    ),

    TrainingConcept(
        id: "carga-axial",
        name: "Carga Axial",
        category: ConceptCategory.MOVEMENT,
        shortDescription: "Fuerza que se aplica a lo largo del eje longitudinal del cuerpo o segmento",
        definition: "La carga axial es la fuerza que actúa a lo largo del eje longitudinal de un hueso o segmento corporal, generalmente compresiva. En ejercicios de pie con barra (sentadilla, peso muerto, press militar), la columna vertebral soporta carga axial significativa. Esta carga estimula la densidad ósea (ley de Wolff) pero también requiere integridad estructural y estabilización adecuada del core para ser segura.",
        practicalApplication: "Los ejercicios con carga axial son fundamentales para la salud ósea y la fuerza funcional, pero deben programarse con precaución en personas con patología discal o compresión vertebral. Alternar con ejercicios sin carga axial (press en banco, leg press, máquinas) permite acumular volumen sin sobrecargar la columna. La descompresión activa (colgarse de una barra) puede complementar sesiones con alta carga axial.",
        keyPoints: [
            "Compresión a lo largo del eje del hueso (típicamente la columna)",
            "Estimula la osteogénesis (formación de hueso) — ley de Wolff",
            "Sentadilla, peso muerto y press militar son los principales ejercicios con carga axial",
            "Requiere estabilización adecuada del core y técnica correcta",
            "La fatiga del core puede ser el limitante antes que el músculo objetivo",
        ],
        relatedConcepts: ["pesos-libres", "maquinas", "tension-mecanica", "rom"],
        examples: [
            "Sentadilla con barra: carga axial directa sobre la columna vertebral",
            "Leg press: trabaja piernas sin carga axial significativa en la columna",
        ],
        commonMistakes: [
            "Acumular excesiva carga axial en una sesión sin descompresión",
            "Ignorar la carga axial como factor de fatiga en la programación",
        ],
    ),

    // ═══════════════════════════════════════════════════════════════════
    // EQUIPAMIENTO Y MEDIOS
    // ═══════════════════════════════════════════════════════════════════

    TrainingConcept(
        id: "pesos-libres",
        name: "Pesos Libres",
        category: ConceptCategory.EQUIPMENT,
        shortDescription: "Barras, mancuernas y kettlebells — resistencia sin guía de trayectoria",
        definition: "Los pesos libres (barras olímpicas, mancuernas, kettlebells, discos) son implementos que permiten el movimiento en los tres planos del espacio sin restricción de trayectoria. Esto demanda mayor estabilización articular, coordinación intermuscular y activación del core. Son la herramienta más versátil del entrenamiento de fuerza y permiten los movimientos más naturales y transferibles a la vida real.",
        practicalApplication: "Los pesos libres deben ser la base de cualquier programa de fuerza e hipertrofia. Proporcionan el estímulo más completo por ejercicio al involucrar estabilizadores. Sin embargo, requieren mejor técnica y pueden ser limitantes en aislamiento muscular. Combinar pesos libres (para compuestos) con máquinas/cables (para aislamiento) es la estrategia óptima.",
        keyPoints: [
            "Movimiento libre en los 3 planos — máxima demanda de estabilización",
            "Mayor activación de músculos estabilizadores y sinergistas",
            "Permiten progresión lineal precisa (microcargas)",
            "Requieren mayor habilidad técnica y supervisión inicial",
            "Sentadilla, peso muerto, press y remo son los pilares con peso libre",
        ],
        relatedConcepts: ["maquinas", "poleas", "tension-mecanica", "carga-axial"],
        examples: [
            "Sentadilla con barra olímpica: el ejercicio de peso libre por excelencia",
            "Press con mancuernas: permite mayor ROM que la barra en press de banca",
        ],
        commonMistakes: [
            "Usar pesos libres sin dominar la técnica básica",
            "Evitar pesos libres por miedo, perdiendo sus beneficios únicos",
        ],
    ),

    TrainingConcept(
        id: "maquinas",
        name: "Máquinas de Entrenamiento",
        category: ConceptCategory.EQUIPMENT,
        shortDescription: "Equipos con trayectoria guiada para aislamiento muscular controlado",
        definition: "Las máquinas de entrenamiento proporcionan un patrón de movimiento guiado (fijo o con variación predeterminada) que reduce la demanda de estabilización y permite enfocarse en el músculo objetivo. Incluyen máquinas de placas (stack), máquinas de discos (plate-loaded), y multiestaciones. Cada máquina tiene un perfil de resistencia determinado por su sistema de levas y poleas.",
        practicalApplication: "Las máquinas son ideales para: (1) aislamiento muscular donde los estabilizadores son el factor limitante en peso libre, (2) entrenamiento seguro cerca del fallo, (3) principiantes que aún no dominan técnica de peso libre, (4) rehabilitación con control de rango de movimiento, (5) trabajo de alto volumen con menor riesgo. No deben reemplazar completamente a los pesos libres.",
        keyPoints: [
            "Trayectoria guiada: menor demanda de estabilización",
            "Permiten llevar al fallo de forma más segura",
            "El perfil de resistencia varía según el diseño de la máquina",
            "Ideales para aislamiento muscular y trabajo complementario",
            "Menor transferencia a movimientos funcionales que los pesos libres",
        ],
        relatedConcepts: ["pesos-libres", "poleas", "perfil-resistencia", "fallo-muscular"],
        examples: [
            "Leg press: trabaja piernas sin carga axial ni demanda de estabilización del tronco",
            "Pec deck: aislamiento de pectoral sin limitación del tríceps",
        ],
        commonMistakes: [
            "Usar solo máquinas ignorando los beneficios de los pesos libres",
            "No ajustar correctamente la máquina a las proporciones corporales",
        ],
    ),

    TrainingConcept(
        id: "poleas",
        name: "Poleas y Cables",
        category: ConceptCategory.EQUIPMENT,
        shortDescription: "Sistemas de cable que mantienen tensión constante en cualquier ángulo",
        definition: "Los sistemas de poleas y cables redirigen la fuerza de gravedad mediante un cable y una polea, permitiendo aplicar resistencia en cualquier dirección y mantener tensión constante durante todo el rango de movimiento. A diferencia de los pesos libres (donde la gravedad actúa solo verticalmente) y las máquinas (trayectoria fija), las poleas ofrecen libertad de movimiento con tensión continua, siendo el medio de entrenamiento más versátil para ciertos patrones de movimiento.",
        practicalApplication: "Las poleas son excelentes para: rotaciones (wood chops), movimientos horizontales (face pulls, cruces), trabajo unilateral, y ejercicios donde se necesita tensión en el punto de contracción máxima (aperturas de cable vs. mancuerna). Ajustar la altura del cable cambia el perfil de resistencia y el vector de fuerza, permitiendo atacar distintas porciones del músculo.",
        keyPoints: [
            "Tensión constante durante todo el ROM (no depende de la gravedad vertical)",
            "Libertad de trayectoria combinada con resistencia continua",
            "Permiten trabajar en planos horizontales y diagonales",
            "Ideales para rotaciones, aperturas y trabajo de manguito rotador",
            "Ajustar la altura del cable modifica qué porción del músculo se enfatiza",
        ],
        relatedConcepts: ["pesos-libres", "maquinas", "perfil-resistencia", "tension-mecanica"],
        examples: [
            "Face pull con cable alto: tensión constante en deltoides posterior y rotadores",
            "Cruce de cables: tensión en pectoral incluso en la contracción máxima",
        ],
        commonMistakes: [
            "Usar demasiado peso y compensar con impulso corporal",
            "No aprovechar la versatilidad de ángulos que ofrecen las poleas",
        ],
    ),

    // ═══════════════════════════════════════════════════════════════════
    // CUALIDADES FÍSICAS
    // ═══════════════════════════════════════════════════════════════════

    TrainingConcept(
        id: "fuerza",
        name: "Fuerza y Generación de Torque",
        category: ConceptCategory.QUALITIES,
        shortDescription: "Capacidad neuromuscular de ejercer Torque articular contra resistencia",
        definition: "La fuerza es la capacidad del sistema neuromuscular para reclutar unidades motoras y generar contracciones que producen Torque en las articulaciones. Desde la biomecánica, nuestro cuerpo no 'levanta pesos', sino que genera torques (rotaciones) alrededor de los ejes articulares para equilibrar el torque externo. Esta cualidad puede manifestarse como Fuerza Máxima (1RM neural), Fuerza Hipertrófica, o Fuerza Explosiva.",
        practicalApplication: "El entrenamiento de fuerza requiere altas cargas y alta intencionalidad (voluntad de contraer fuerte y rápido). Para reclutar fibras de alto umbral, la velocidad de acortamiento real debe ser lenta (por la carga pesada) pero la intención debe ser explosiva. Para mejorar el torque en un ejercicio específico (fuerza específica), entrena con el mismo perfil de resistencia y brazo de momento que quieres dominar.",
        keyPoints: [
            "Fuerza Interna: Torque muscular aplicado en la inserción ósea (palanca de 3er grado corta).",
            "Fuerza Externa: Peso movido. Un gran peso movido puede significar gran técnica más que pura fuerza muscular.",
            "Relación Fuerza-Velocidad: A mayor velocidad de contracción, menor tensión cruzada en actina/miosina (menor fuerza).",
            "Relación Longitud-Tensión: Un sarcómero produce máxima fuerza a longitudes intermedias/largas, donde el solapamiento óptimo ocurre.",
            "La base de la potencia explosiva es tener una gran Fuerza Máxima."
        ],
        relatedConcepts: ["intensidad", "explosividad", "tension-mecanica", "perfil-resistencia"],
        examples: [
            "Sentadilla: Torque máximo sobre rodillas en el punto bajo (brazo de momento largo de fémur horizontal).",
            "Peso Muerto Sumo: Alterra la ventaja mecánica (palanca corta de tronco) respecto al convencional."
        ],
        commonMistakes: [
            "Confundir 'peso en la barra' con 'fuerza producida'. La técnica de powerlifting acorta brazos de momento externos para mover más kilos, minimizando el torque muscular demandado."
        ],
    ),

    TrainingConcept(
        id: "explosividad",
        name: "Explosividad / Potencia",
        category: ConceptCategory.QUALITIES,
        shortDescription: "Capacidad de generar la máxima fuerza en el menor tiempo posible",
        definition: "La potencia (o explosividad) es el producto de la fuerza por la velocidad (P: F × V). Representa la capacidad del sistema neuromuscular de producir la mayor fuerza posible en el menor tiempo. Es la cualidad más relevante para el rendimiento deportivo (salto, sprint, lanzamiento, golpeo). Se desarrolla con ejercicios olímpicos, balísticos, pliométricos y entrenamiento de fuerza con intención de máxima velocidad.",
        practicalApplication: "Para desarrollar potencia: combinar entrenamiento de fuerza máxima (construir la base de fuerza) con ejercicios de velocidad-fuerza (movimientos olímpicos al 60-80% 1RM), ejercicios balísticos (lanzamientos, saltos) y pliometría. Siempre entrenar potencia al inicio de la sesión cuando el SNC está fresco. Descansos completos (3-5 min) entre series.",
        keyPoints: [
            "Potencia: Fuerza × Velocidad — ambos componentes entrenables",
            "Es la cualidad más determinante en el rendimiento deportivo",
            "Se entrena con cargas moderadas (30-70% 1RM) a máxima velocidad",
            "Requiere base de fuerza previa para ser desarrollada eficientemente",
            "Declina antes que la fuerza con el envejecimiento — prioritaria en personas mayores",
        ],
        relatedConcepts: ["fuerza", "pliometria", "elasticidad", "fase-concentrica"],
        examples: [
            "Clean & jerk olímpico: expresión máxima de potencia con barra",
            "Salto vertical: medida directa de potencia del tren inferior",
        ],
        commonMistakes: [
            "Entrenar potencia en estado de fatiga (pierde el propósito)",
            "No tener base de fuerza suficiente antes de trabajar potencia",
        ],
    ),

    TrainingConcept(
        id: "elasticidad",
        name: "Elasticidad",
        category: ConceptCategory.QUALITIES,
        shortDescription: "Capacidad de almacenar y liberar energía elástica en el complejo músculo-tendón",
        definition: "La elasticidad muscular se refiere a la capacidad del complejo músculo-tendón de almacenar energía mecánica durante un estiramiento rápido (fase excéntrica) y liberarla en la subsiguiente contracción (fase concéntrica). Esta propiedad se amplifica con el ciclo estiramiento-acortamiento y es fundamental en movimientos como correr, saltar y lanzar. Los tendones son los principales almacenadores de energía elástica.",
        practicalApplication: "La elasticidad se mejora con entrenamiento pliométrico progresivo, sprints, saltos reactivos y ejercicios con componente de rebote. El tendón de Aquiles es el ejemplo más claro: almacena ~35% de la energía necesaria para correr. Para mejorar la elasticidad, el énfasis debe estar en minimizar el tiempo de contacto con el suelo y maximizar la rigidez del sistema.",
        keyPoints: [
            "El tendón es el principal almacén de energía elástica",
            "El ciclo estiramiento-acortamiento es el mecanismo clave",
            "Tiempo de contacto corto: mayor aprovechamiento de elasticidad",
            "Es una cualidad entrenable con pliometría y ejercicios reactivos",
            "Fundamental para la economía de carrera y eficiencia del movimiento",
        ],
        relatedConcepts: ["pliometria", "explosividad", "fase-excentrica", "fase-concentrica"],
        examples: [
            "Correr: el tendón de Aquiles almacena y libera energía cada paso",
            "Drop jump: maximiza el uso de la energía elástica del tendón",
        ],
        commonMistakes: [
            "Confundir flexibilidad (rango pasivo) con elasticidad (almacenamiento de energía)",
            "Pausar entre la excéntrica y la concéntrica, disipando la energía elástica",
        ],
    ),

    TrainingConcept(
        id: "resistencia-muscular",
        name: "Resistencia Muscular",
        category: ConceptCategory.QUALITIES,
        shortDescription: "Capacidad de mantener la producción de fuerza durante un período prolongado",
        definition: "La resistencia muscular es la capacidad de un músculo o grupo muscular de mantener contracciones repetidas o sostener una contracción durante un período prolongado contra una resistencia submáxima. Depende de la eficiencia metabólica (capacidad oxidativa de las fibras musculares), la tolerancia a metabolitos (lactato, H+), la capilarización y la proporción de fibras tipo I. Es fundamental para deportes de resistencia y actividades de la vida diaria.",
        practicalApplication: "Se entrena con repeticiones altas (15-30+), series múltiples con descansos cortos (30-90s), circuitos y trabajo por tiempo (AMRAP, EMOM). En el contexto de hipertrofia, trabajar resistencia muscular complementa el trabajo de fuerza al mejorar la capacidad de trabajo y la tolerancia al volumen. Es especialmente relevante para fibras tipo I (soleus, core, antebrazos).",
        keyPoints: [
            "Capacidad de resistir la fatiga durante contracciones repetidas",
            "Depende de capacidad oxidativa, capilarización y tolerancia a metabolitos",
            "Se entrena con alto volumen, descansos cortos y cargas ligeras-moderadas",
            "Complementa el entrenamiento de fuerza y hipertrofia",
            "Las fibras tipo I responden particularmente bien a este tipo de estímulo",
        ],
        relatedConcepts: ["fuerza", "estres-metabolico", "fatiga-sistemica", "volumen-entrenamiento"],
        examples: [
            "Hacer 50 flexiones seguidas: resistencia muscular del tren superior",
            "Plancha de 3 minutos: resistencia isométrica del core",
        ],
        commonMistakes: [
            "Confundir resistencia cardiovascular con resistencia muscular",
            "Entrenar solo resistencia y descuidar la fuerza máxima",
        ],
    ),

    // ═══════════════════════════════════════════════════════════════════
    // PERIODIZACIÓN
    // ═══════════════════════════════════════════════════════════════════

    TrainingConcept(
        id: "sobrecarga-progresiva",
        name: "Sobrecarga Progresiva",
        category: ConceptCategory.PERIODIZATION,
        shortDescription: "Incrementar gradualmente el estímulo para forzar adaptaciones continuas",
        definition: "La sobrecarga progresiva es el principio fundamental del entrenamiento que establece que para que un músculo siga adaptándose (creciendo, haciéndose más fuerte), el estímulo de entrenamiento debe incrementarse progresivamente con el tiempo. Esto puede lograrse aumentando el peso (la forma más directa), las repeticiones, las series, el rango de movimiento, reduciendo el descanso, o mejorando la calidad técnica. Sin sobrecarga progresiva, el cuerpo se adapta al estímulo y deja de progresar.",
        practicalApplication: "La forma más práctica: intenta agregar peso o repeticiones cada semana. Ejemplo: si hiciste 80kg × 8 esta semana, intenta 80kg × 9 o 82.5kg × 8 la próxima. Cuando ya no puedas progresar linealmente (meseta), manipula otras variables (volumen, frecuencia, ejercicios). La progresión debe ser gradual — saltos grandes aumentan el riesgo de lesión y fatiga excesiva.",
        keyPoints: [
            "Principio #1 del entrenamiento: sin progresión, no hay adaptación",
            "Formas de progresar: peso, reps, series, ROM, tempo, calidad técnica",
            "La progresión lineal (más peso cada semana) funciona en principiantes",
            "Intermedios/avanzados requieren periodización más sofisticada",
            "Documentar el entrenamiento es esencial para verificar la progresión",
        ],
        relatedConcepts: ["volumen-entrenamiento", "intensidad", "tension-mecanica", "frecuencia"],
        examples: [
            "Semana 1: 80kg × 8, Semana 2: 80kg × 9, Semana 3: 82.5kg × 8 → progresión lineal",
            "Agregar 1 serie semanal por grupo muscular: progresión de volumen",
        ],
        commonMistakes: [
            "Intentar progresar demasiado rápido (saltos de 5-10kg por semana)",
            "No registrar el entrenamiento, imposibilitando saber si hay progresión",
            "Cambiar de ejercicios constantemente, impidiendo medir progresión en ninguno",
        ],
    ),

    TrainingConcept(
        id: "deload",
        name: "Deload (Semana de Descarga)",
        category: ConceptCategory.PERIODIZATION,
        shortDescription: "Reducción planificada del volumen/intensidad para disipar fatiga acumulada",
        definition: "Un deload es una semana de entrenamiento con reducción deliberada del volumen (40-60% menos series), la intensidad (10-20% menos peso), o ambas, diseñada para disipar la fatiga sistémica acumulada sin perder las adaptaciones logradas. Es el mecanismo que permite la supercompensación: el rendimiento tras un deload suele ser superior al pre-deload porque se manifiestan las adaptaciones que estaban 'enmascaradas' por la fatiga.",
        practicalApplication: "Programa un deload cada 4-8 semanas según la dureza del entrenamiento. Opciones: (A) reducir volumen a la mitad manteniendo intensidad, (B) reducir intensidad 15-20% manteniendo volumen, o (C) reducir ambos moderadamente. No elimines el entrenamiento completamente — el deload mantiene patrones motores y hábito. Señales de que necesitas un deload: estancamiento, fatiga acumulada, pérdida de motivación, dolor articular.",
        keyPoints: [
            "Programar cada 4-8 semanas dependiendo del nivel y la carga de entrenamiento",
            "Reducir volumen ~50% o intensidad ~15-20% (no ambos drásticamente)",
            "Permite la supercompensación — las adaptaciones se expresan tras disipar la fatiga",
            "No es 'no entrenar' — mantiene patrones motores y hábitos",
            "Es una herramienta de recuperación, no un signo de debilidad",
        ],
        relatedConcepts: ["fatiga-sistemica", "sobrecarga-progresiva", "volumen-entrenamiento"],
        examples: [
            "Entrenamiento normal: 4×8 @ 100kg. Deload: 2×8 @ 85kg (menos volumen e intensidad)",
            "5 semanas de volumen progresivo + 1 semana deload: 1 mesociclo completo",
        ],
        commonMistakes: [
            "Saltarse los deloads y acumular fatiga hasta lesionarse o estancarse",
            "Hacer deloads demasiado frecuentes que no permiten acumular estrés suficiente",
            "Entrenar duro durante el deload porque 'te sientes bien' (el propósito es recuperar)",
        ],
    ),

    TrainingConcept(
        id: "especificidad",
        name: "Especificidad",
        category: ConceptCategory.PERIODIZATION,
        shortDescription: "Las adaptaciones son específicas al tipo de estímulo aplicado",
        definition: "El principio de especificidad (principio SAID: Specific Adaptation to Imposed Demands) establece que el cuerpo se adapta de manera específica al tipo de estímulo recibido. Si entrenas fuerza máxima, te haces más fuerte. Si entrenas resistencia, mejoras la resistencia. Si entrenas un movimiento específico, mejoras en ese movimiento. Las adaptaciones son altamente específicas en cuanto a patrón de movimiento, velocidad, ángulo articular y tipo de contracción.",
        practicalApplication: "Para ser fuerte en sentadilla, debes hacer sentadilla (no solo leg press). Para mejorar en un deporte, entrena los patrones de movimiento de ese deporte. Sin embargo, hay transferencia parcial entre estímulos similares: la fuerza en sentadilla transfiere parcialmente a la capacidad de salto. La especificidad guía la selección de ejercicios y la estructura del entrenamiento.",
        keyPoints: [
            "SAID: las adaptaciones son específicas al estímulo impuesto",
            "Especificidad de movimiento, velocidad, ángulo y tipo de contracción",
            "Existe transferencia parcial entre estímulos similares",
            "Principiantes se benefician de variedad; avanzados necesitan más especificidad",
            "La especificidad debe balancearse con la variedad para evitar sobreuso",
        ],
        relatedConcepts: ["sobrecarga-progresiva", "fuerza", "rom", "intensidad"],
        examples: [
            "Para mejorar en press banca: el press banca debe ser prioridad (no solo aperturas)",
            "Para saltar más: combinar sentadillas pesadas con saltos (fuerza + especificidad)",
        ],
        commonMistakes: [
            "Hacer demasiada variedad sin consistencia en los ejercicios principales",
            "Ignorar la especificidad y esperar transferencia total entre ejercicios distintos",
        ],
    ),
]

/** Get all unique categories present in the database */
public func getConceptCategories() -> [ConceptCategory] {
    Array(Set(TRAINING_CONCEPTS_DATABASE.map { $0.category })).sorted { $0.label < $1.label }
}

/** Search concepts by query across name, description and definition */
public func searchConcepts(query: String) -> [TrainingConcept] {
    if query.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty { return TRAINING_CONCEPTS_DATABASE }
    let q = query.lowercased()
    return TRAINING_CONCEPTS_DATABASE.filter { concept in
        concept.name.lowercased().contains(q) ||
        concept.shortDescription.lowercased().contains(q) ||
        concept.definition.lowercased().contains(q) ||
        concept.category.label.lowercased().contains(q)
    }
}
