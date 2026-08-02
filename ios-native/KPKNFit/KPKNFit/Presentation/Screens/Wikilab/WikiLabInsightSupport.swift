import SwiftUI

internal struct WikiLabPatternInsight {
    let summary: String
    let setupCues: [String]
    let commonErrors: [String]
    let mobilityDemands: [String]
}

internal struct WikiLabVisualGuide {
    let title: String
    let summary: String
    let bullets: [String]
    let accent: Color
    let icon: Image
}

private let patternInsights: [String: WikiLabPatternInsight] = [
    "horizontal-push": WikiLabPatternInsight(
        summary: "El vector principal viaja por delante del torso, así que la estabilidad escapular y el control del húmero deciden cuánto empuje útil llega al implemento.",
        setupCues: [
            "Fija escápulas antes de iniciar y deja que el pecho reciba la carga.",
            "Mantén antebrazos casi verticales para no regalar brazo de momento en muñeca y codo.",
            "Aprieta el suelo con pies y glúteos para que el tronco no pierda rigidez.",
        ],
        commonErrors: [
            "Codos muy abiertos que desplazan tensión al hombro anterior.",
            "Perder retracción o apoyo torácico en mitad del recorrido.",
            "Rebote sin control en el punto de máxima elongación.",
        ],
        mobilityDemands: [
            "Extensión torácica funcional.",
            "Rotación externa y control anterior del hombro.",
            "Estabilidad escapular bajo fatiga.",
        ]
    ),
    "horizontal-pull": WikiLabPatternInsight(
        summary: "Aquí la palanca útil nace en la escápula y se completa con codo y hombro. Si la cintura escapular no lidera, el tirón se vuelve un simple gesto de brazo.",
        setupCues: [
            "Inicia el gesto con depresión y retracción escapular suave.",
            "Mantén costillas abajo para que el tirón no se convierta en extensión lumbar.",
            "Lleva el codo hacia atrás o hacia la cadera según el ángulo del remo.",
        ],
        commonErrors: [
            "Encoger trapecio superior y perder dorsales.",
            "Compensar con balanceo del torso en cada repetición.",
            "Cerrar el rango final antes de completar la escápula.",
        ],
        mobilityDemands: [
            "Control escapulotorácico.",
            "Bisagra o apoyo estable del tronco.",
            "Rotación humeral libre de pinzamiento.",
        ]
    ),
    "vertical-push": WikiLabPatternInsight(
        summary: "Empujar arriba exige alinear muñeca, codo, hombro y tronco debajo de la carga. Cuanto más limpia la columna, menor fuga de fuerza.",
        setupCues: [
            "Apila costillas sobre pelvis antes de despegar el peso.",
            "Deja que la cabeza pase bajo la carga sin hiperextender la lumbar.",
            "Empuja en línea recta y termina con hombro elevado y escápula rotando hacia arriba.",
        ],
        commonErrors: [
            "Arqueo lumbar para compensar falta de flexión de hombro.",
            "Barra demasiado adelantada, lejos del centro de masa.",
            "Bloqueo final sin rotación escapular suficiente.",
        ],
        mobilityDemands: [
            "Flexión completa de hombro.",
            "Extensión torácica usable.",
            "Capacidad de brace y control glúteo.",
        ]
    ),
    "vertical-pull": WikiLabPatternInsight(
        summary: "En el tirón vertical la carga tiende a separar el húmero del tronco. La dominada buena es una secuencia de depresión escapular, aducción del brazo y estabilidad del core.",
        setupCues: [
            "Crea tensión desde el agarre antes de despegar.",
            "Baja escápulas y después flexiona codos.",
            "Mantén pelvis estable para no convertir el gesto en un columpio.",
        ],
        commonErrors: [
            "Tirar solo con bíceps desde el inicio.",
            "Perder control excéntrico y caer en cada repetición.",
            "Encoger hombros en la parte alta.",
        ],
        mobilityDemands: [
            "Flexión de hombro sin dolor.",
            "Control escapular en depresión y rotación inferior.",
            "Rigidez del tronco para evitar balanceos.",
        ]
    ),
    "squat": WikiLabPatternInsight(
        summary: "La sentadilla reparte demanda entre tobillo, rodilla, cadera y tronco. El patrón cambia mucho con antropometría, barra y base, por eso la técnica útil no siempre se ve idéntica.",
        setupCues: [
            "Encuentra una base que te permita profundidad sin colapsar pies ni pelvis.",
            "Respira y bracea antes del descenso.",
            "Desciende manteniendo la barra sobre el mediopié.",
        ],
        commonErrors: [
            "Talones o arco del pie inestables durante el descenso.",
            "Rodillas que colapsan hacia dentro cuando sube la demanda.",
            "Perder rigidez torácica y dejar que la barra se adelante.",
        ],
        mobilityDemands: [
            "Dorsiflexión de tobillo.",
            "Rotación externa de cadera y control pélvico.",
            "Extensión torácica según variante de barra.",
        ]
    ),
    "hinge": WikiLabPatternInsight(
        summary: "La bisagra separa cadera y columna: la cadera se mueve, la espalda transmite. Cuando ese reparto se pierde, el patrón deja de cargar al posterior y sube el coste lumbar.",
        setupCues: [
            "Lleva la cadera atrás sin abandonar la presión del pie completo.",
            "Mantén el implemento cerca del cuerpo para acortar el brazo de momento.",
            "Piensa en cerrar costillas y pelvis antes de iniciar.",
        ],
        commonErrors: [
            "Flexionar lumbar al buscar más rango.",
            "Alejar la carga del cuerpo durante la fase dura.",
            "Iniciar con rodilla o espalda en vez de con cadera.",
        ],
        mobilityDemands: [
            "Longitud funcional de isquiosurales.",
            "Control de columna neutra bajo tensión.",
            "Tolerancia de agarre y dorsal para fijar la carga.",
        ]
    ),
    "lunge": WikiLabPatternInsight(
        summary: "La estocada desafía control frontal y sagital a la vez. Es un patrón excelente para repartir carga entre piernas, pero castiga rápido la pérdida de equilibrio y alineación.",
        setupCues: [
            "Crea una zancada que te permita bajar vertical y estable.",
            "Mantén pelvis cuadrada durante el descenso.",
            "Empuja el suelo con la pierna adelantada para volver.",
        ],
        commonErrors: [
            "Paso demasiado corto que amontona carga en la rodilla delantera.",
            "Torso colapsado o inclinado sin intención.",
            "Inestabilidad frontal del pie y la cadera.",
        ],
        mobilityDemands: [
            "Flexión de cadera unilateral.",
            "Extensión de cadera de la pierna retrasada.",
            "Estabilidad de pie, rodilla y glúteo medio.",
        ]
    ),
    "extension": WikiLabPatternInsight(
        summary: "Los patrones de extensión generan fuerza alejando segmentos desde flexión previa. La clave es abrir donde toca sin convertirlo en extensión lumbar indiscriminada.",
        setupCues: [
            "Define la articulación que quieres extender antes de iniciar.",
            "Mantén el tronco como base si la extensión es periférica.",
            "Busca recorrido activo, no solo velocidad.",
        ],
        commonErrors: [
            "Compensar extensión de cadera con arco lumbar.",
            "Bloquear la repetición con rebote pasivo.",
            "Perder tensión en la fase excéntrica.",
        ],
        mobilityDemands: [
            "Capacidad de extender sin dolor la articulación objetivo.",
            "Control del core para que la columna no robe movimiento.",
            "Tolerancia tendinosa al punto final del gesto.",
        ]
    ),
    "anti-extension": WikiLabPatternInsight(
        summary: "Anti-extensión no significa inmovilidad absoluta; significa resistir que la caja torácica se abra y la pelvis se desordene cuando la carga intenta arquearte.",
        setupCues: [
            "Apila costillas sobre pelvis antes de empezar.",
            "Respira sin perder presión abdominal circumferencial.",
            "Piensa en alargar el cuerpo mientras resistes la carga.",
        ],
        commonErrors: [
            "Confundir brace con apnea rígida y perder control fino.",
            "Ceder la pelvis en anteversión cuando aumenta el brazo de palanca.",
            "Reducir el ejercicio a hombros o flexores de cadera.",
        ],
        mobilityDemands: [
            "Control lumbopélvico.",
            "Capacidad de hombro si el patrón es por encima de la cabeza.",
            "Tolerancia del core a tensión sostenida.",
        ]
    ),
    "anti-rotation": WikiLabPatternInsight(
        summary: "La carga intenta girarte; tu tarea es dejar pasar fuerza sin que el tronco se retuerza. Cuanto mejor se organizan pies, cadera y parrilla costal, más limpio sale el patrón.",
        setupCues: [
            "Enraíza pies y glúteos antes de recibir la tensión lateral.",
            "Mantén esternón y pelvis mirando al frente.",
            "Respira corto y controlado para no perder el cilindro.",
        ],
        commonErrors: [
            "Rotar hombros aunque la pelvis siga quieta.",
            "Buscar tensión solo con brazos y no con el tronco.",
            "Compensar con desplazamientos laterales del cuerpo.",
        ],
        mobilityDemands: [
            "Estabilidad frontal de cadera.",
            "Control oblicuo y serrato.",
            "Alineación torácica sin rigidez excesiva.",
        ]
    ),
    "rotation": WikiLabPatternInsight(
        summary: "Rotar bien es repartir giro entre pies, caderas, columna torácica y hombros. Cuando una región se queda atrás, otra suele excederse.",
        setupCues: [
            "Define desde dónde quieres producir la rotación principal.",
            "Permite al pie y a la cadera acompañar si el gesto es atlético.",
            "Controla la desaceleración igual que la aceleración.",
        ],
        commonErrors: [
            "Rotar lumbar en exceso por falta de cadera o tórax.",
            "Iniciar demasiado rápido y perder línea de fuerza.",
            "Bloquear pies por completo en patrones que piden transferencia.",
        ],
        mobilityDemands: [
            "Rotación torácica.",
            "Rotación interna y externa de cadera.",
            "Capacidad de desaceleración del core.",
        ]
    ),
    "carry": WikiLabPatternInsight(
        summary: "Las cargas caminadas son una prueba de transmisión de fuerza. El valor está en sostener postura, respiración y simetría mientras la base cambia paso a paso.",
        setupCues: [
            "Agarra y apila antes de dar el primer paso.",
            "Camina con pasos cortos y silenciosos.",
            "Mantén costillas y pelvis estables mientras la carga te intenta inclinar.",
        ],
        commonErrors: [
            "Marchar demasiado rápido y perder control del tronco.",
            "Llevar hombro elevado o escápula inestable bajo carga.",
            "Compensar con inclinación lateral marcada.",
        ],
        mobilityDemands: [
            "Grip y estabilidad escapular.",
            "Resistencia postural del core.",
            "Control unilateral de cadera y pie.",
        ]
    ),
    "jump": WikiLabPatternInsight(
        summary: "El salto combina producción rápida de fuerza y amortiguación. No basta con despegar alto; importa también cómo recibes y redistribuyes la carga al caer.",
        setupCues: [
            "Carga el patrón desde pies, cadera y brazos si la variante lo permite.",
            "Despega proyectando fuerza al suelo, no solo elevando rodillas.",
            "Amortigua con tobillo, rodilla y cadera al aterrizar.",
        ],
        commonErrors: [
            "Aterrizajes ruidosos y rígidos.",
            "Valgo dinámico o colapso del pie en la recepción.",
            "Usar solo rodilla para frenar sin ayuda de cadera.",
        ],
        mobilityDemands: [
            "Elasticidad de tobillo y pie.",
            "Capacidad de absorber fuerza en rodilla y cadera.",
            "Rigidez reactiva del tendón de Aquiles y complejo posterior.",
        ]
    ),
]

@ViewBuilder
internal func WikiLabInsightCard(title: String, accent: Color, icon: Image, summary: String, bullets: [String] = [], footer: String? = nil) -> some View {
    VStack(alignment: .leading, spacing: 8) {
        HStack(spacing: 6) {
            icon
                .resizable()
                .renderingMode(.template)
                .frame(width: 15, height: 15)
                .foregroundColor(accent)
            Text(title)
                .font(.system(size: 11, weight: .black, design: .serif))
                .tracking(0.8)
                .foregroundColor(.white.opacity(0.8))
        }
        
        Text(summary)
            .font(.system(size: 13, design: .serif))
            .foregroundColor(.white.opacity(0.7))
            .lineSpacing(4)
        
        ForEach(bullets, id: \.self) { bullet in
            HStack(alignment: .top, spacing: 10) {
                Circle()
                    .fill(accent)
                    .frame(width: 7, height: 7)
                    .padding(.top, 6)
                Text(bullet)
                    .font(.system(size: 13, design: .serif))
                    .foregroundColor(.white.opacity(0.8))
                    .lineSpacing(4)
            }
        }
        
        if let footer = footer, !footer.isEmpty {
            Text(footer)
                .font(.system(size: 11, design: .serif))
                .foregroundColor(.white.opacity(0.5))
        }
    }
    .frame(maxWidth: .infinity, alignment: .leading)
    .padding(16)
    .background(Color(hex: 0x141414))
    .overlay(
        RoundedRectangle(cornerRadius: 4)
            .stroke(Color(hex: 0x2C2C2C), lineWidth: 1)
    )
}

internal func patternInsightFor(_ patternId: String) -> WikiLabPatternInsight? {
    patternInsights[patternId]
}

internal func buildMuscleGuide(_ muscle: MuscleGroupEntity) -> WikiLabVisualGuide {
    let accent = wikiLabBodyPartAccent(muscle.bodyPart)
    let id = muscle.id.lowercased()
    
    if id.hasPrefix("pectoral") {
        return WikiLabVisualGuide(
            title: "Qué Mirar",
            summary: "En el pectoral importa ver si el hombro sigue una trayectoria limpia y si la caja torácica le da una base estable para empujar o aproximar el brazo.",
            bullets: [
                "Busca si el húmero se acerca al tronco sin hombros adelantados en exceso.",
                "Diferencia si el trabajo viene del pectoral superior, medio o inferior según el ángulo.",
                "Si el codo domina demasiado, el gesto suele migrar a tríceps y deltoides.",
            ],
            accent: accent,
            icon: Image(systemName: "eye")
        )
    }
    
    if id.contains("trapecio") || id.contains("romboides") || id.contains("dorsal") || id == "espalda" {
        return WikiLabVisualGuide(
            title: "Lectura Visual",
            summary: "La espalda rara vez se entiende por un solo plano. Lo útil es mirar si la escápula se mueve con intención y si el tronco sostiene la trayectoria sin balanceos.",
            bullets: [
                "Depresión y retracción no son lo mismo; observa cuál de las dos falla primero.",
                "Un dorsal dominante suele llevar el codo hacia la cadera, no solo hacia atrás.",
                "Cuando el tronco se mueve de más, la espalda deja de ser el motor principal.",
            ],
            accent: accent,
            icon: Image(systemName: "chart.bar.xaxis")
        )
    }
    
    if id.contains("deltoides") || id == "hombros" {
        return WikiLabVisualGuide(
            title: "Lectura Visual",
            summary: "El hombro se ve mejor por trayectorias que por volumen. Observa si la escápula acompaña y si cada porción del deltoides recibe tensión donde corresponde.",
            bullets: [
                "Deltoides anterior: control del brazo por delante del torso.",
                "Deltoides lateral: separación limpia del brazo sin encoger trapecio.",
                "Deltoides posterior: extensión y abducción horizontal con escápula estable.",
            ],
            accent: accent,
            icon: Image(systemName: "eye")
        )
    }
    
    if id.contains("bíceps") || id == "brazos" || id.contains("tríceps") || id.contains("antebrazo") {
        return WikiLabVisualGuide(
            title: "Aplicación Práctica",
            summary: "En brazos conviene distinguir si el músculo mueve, asiste o solo estabiliza. Esa diferencia cambia mucho la elección del ejercicio y el volumen útil.",
            bullets: [
                "El bíceps gana cuando el codo flexiona con hombro estable y supinación real.",
                "El tríceps destaca cuando el codo extiende sin que el torso robe la tarea.",
                "El antebrazo suele limitar por agarre antes que por fatiga visible.",
            ],
            accent: accent,
            icon: Image(systemName: "sparkles")
        )
    }
    
    if id.contains("glúte") || id.contains("isquio") || id.contains("cuádr") || id.contains("aductor") || id == "piernas" || id.contains("pantorr") {
        return WikiLabVisualGuide(
            title: "Qué Mirar",
            summary: "En tren inferior conviene leer el reparto de carga entre pie, rodilla y cadera. Si una zona pierde alineación, otra suele absorber el coste mecánico.",
            bullets: [
                "Cuádriceps: cuánto avanza la rodilla y cuánta estabilidad mantiene el pie.",
                "Glúteos e isquios: si la cadera lidera o solo acompaña al movimiento.",
                "Pantorrillas: calidad del apoyo y rigidez reactiva en el tobillo.",
            ],
            accent: accent,
            icon: Image(systemName: "chart.bar.xaxis")
        )
    }
    
    if id.contains("abdomen") || id == "core" || id.contains("erectores") {
        return WikiLabVisualGuide(
            title: "Aplicación Práctica",
            summary: "El core no siempre se ve por movimiento, sino por ausencia de movimiento no deseado. Lo más útil es mirar si costillas, pelvis y presión interna se mantienen coordinadas.",
            bullets: [
                "Anti-extensión: evita abrir costillas o exagerar la lordosis.",
                "Anti-rotación: mira si hombros y pelvis siguen la misma dirección.",
                "Erectores: sostienen y transfieren, pero no deberían reemplazar a la cadera.",
            ],
            accent: accent,
            icon: Image(systemName: "bolt")
        )
    }
    
    return WikiLabVisualGuide(
        title: "Aplicación Práctica",
        summary: "La mejor lectura visual es comprobar qué articulaciones mueve este músculo y dónde debería sentirse la carga cuando la ejecución es estable.",
        bullets: [
            "Observa si el movimiento sucede en las articulaciones que el músculo cruza.",
            "Compara lado derecho e izquierdo cuando el gesto es unilateral.",
            "Si la tensión se va a otra región, la técnica o la selección probablemente no encaja.",
        ],
        accent: accent,
        icon: Image(systemName: "eye")
    )
}

internal func buildJointGuide(_ joint: JointEntity) -> WikiLabVisualGuide {
    switch joint.type {
    case "ball-socket":
        return WikiLabVisualGuide(
            title: "Lectura Articular",
            summary: "Las articulaciones esferoideas ganan libertad a costa de control. Lo importante no es solo cuánto se mueven, sino cómo centran la cabeza articular durante la carga.",
            bullets: [
                "Busca si hay rotación y traslación limpias, no solo rango.",
                "Escápula y caja torácica suelen decidir el hombro más que el húmero solo.",
                "Cuando falla el control, aparecen pinzamientos o compensaciones rápidas.",
            ],
            accent: Color(hex: 0x1E88E5),
            icon: Image(systemName: "eye")
        )
    case "hinge":
        return WikiLabVisualGuide(
            title: "Lectura Articular",
            summary: "Las bisagras viven mejor cuando la fuerza entra alineada. Tolera mucha carga, pero castiga pronto el colapso frontal o la pérdida del eje principal.",
            bullets: [
                "Revisa si la línea pie-rodilla-cadera sigue siendo clara bajo fatiga.",
                "Demasiada traslación o valgo suele indicar que otra región dejó de ayudar.",
                "La carga protectora suele venir de progresar control y tolerancia, no de inmovilizar.",
            ],
            accent: Color(hex: 0x1E88E5),
            icon: Image(systemName: "chart.bar.xaxis")
        )
    default:
        return WikiLabVisualGuide(
            title: "Lectura Articular",
            summary: "Esta articulación suele ser un punto de transferencia. Lo útil es observar si deja pasar movimiento y fuerza sin convertirse en el cuello de botella del patrón.",
            bullets: [
                "Pregunta si está guiando el gesto o solo adaptándose a la región vecina.",
                "Compara movilidad disponible con estabilidad bajo carga.",
                "Las molestias repetidas suelen venir de exceso o defecto de movimiento relativo.",
            ],
            accent: Color(hex: 0x1E88E5),
            icon: Image(systemName: "eye")
        )
    }
}

internal func buildTendonGuide(_ tendon: TendonEntity) -> WikiLabVisualGuide {
    WikiLabVisualGuide(
        title: "Manejo de Carga",
        summary: "Los tendones responden mejor a progresiones consistentes que a picos heroicos. Más que buscar sensaciones, conviene leer tolerancia a carga, irritabilidad al día siguiente y calidad del patrón.",
        bullets: [
            "Dolor estable y tolerable durante la sesión suele ser más manejable que dolor creciente.",
            "La señal útil es cómo responde 24 a 48 horas después, no solo al terminar.",
            "Isométricos, tempo y rango parcial suelen servir como escalones antes de volver al gesto completo.",
        ],
        accent: Color(hex: 0xFFFF8F00),
        icon: Image(systemName: "bandage")
    )
}

internal func recommendedExercisesForMuscle(_ muscle: MuscleGroupEntity, limit: Int = 6) -> [WikiLabExerciseLink] {
    let targets = canonicalExerciseMusclesFor(muscle)
    guard !targets.isEmpty, !catalogExerciseList.isEmpty else { return [] }
    
    return catalogExerciseList
        .compactMap { exercise -> (ExerciseMuscleInfo, Double)? in
            let score = scoreExerciseForMuscle(exercise, targets)
            guard score > 0.0 else { return nil }
            return (exercise, score)
        }
        .sorted { a, b in
            if a.1 != b.1 { return a.1 > b.1 }
            let diffA = a.0.technicalDifficulty ?? Double.greatestFiniteMagnitude
            let diffB = b.0.technicalDifficulty ?? Double.greatestFiniteMagnitude
            if diffA != diffB { return diffA < diffB }
            return a.0.name < b.0.name
        }
        .prefix(limit)
        .map { (exercise, _) in
            WikiLabExerciseLink(
                id: exercise.id,
                name: exercise.name,
                subtitle: [exercise.type, exercise.force, exercise.equipment]
                    .compactMap { $0 }
                    .joined(separator: " · ")
            )
        }
}

private func scoreExerciseForMuscle(_ exercise: ExerciseMuscleInfo, _ targets: Set<String>) -> Double {
    guard !targets.isEmpty else { return 0.0 }
    
    var score = 0.0
    for involved in exercise.involvedMuscles {
        guard targets.contains(involved.muscle) else { continue }
        switch involved.role {
        case .PRIMARY: score += 4.0
        case .SECONDARY: score += 2.0
        case .STABILIZER: score += 0.8
        case .NEUTRALIZER: score += 0.5
        }
        score += involved.volumeContribution ?? 0.0
    }
    
    guard score > 0.0 else { return 0.0 }
    
    switch exercise.tier {
    case "T1": score *= 1.2
    case "T2": score *= 0.8
    default: score *= 0.3
    }
    
    switch exercise.type {
    case "Básico": score *= 0.9
    case "Accesorio": score *= 0.7
    case "Aislamiento": score *= 0.6
    default: break
    }
    
    return score
}

private func canonicalExerciseMusclesFor(_ muscle: MuscleGroupEntity) -> Set<String> {
    let id = muscle.id.lowercased()
    var result = Set<String>()
    switch true {
    case id.hasPrefix("pectoral"): result.insert("Pectorales")
    case id == "espalda" || id.contains("dorsal") || id.contains("redondo"): result.insert("Dorsales")
    case id == "hombros" || id.contains("deltoides"): result.insert("Deltoides")
    case id == "brazos" || id.contains("tríceps"): result.insert("Tríceps")
    case id == "brazos" || id.contains("bíceps"): result.insert("Bíceps")
    case id.contains("antebrazo"): result.insert("Antebrazo")
    case id == "abdomen": result.insert("Abdomen")
    case id == "core": result.insert("Core")
    case id == "piernas" || id.contains("cuádr"): result.insert("Cuádriceps")
    case id == "piernas" || id.contains("isquio"): result.insert("Isquiosurales")
    case id == "piernas" || id.contains("glúte"): result.insert("Glúteos")
    case id == "piernas" || id.contains("aductor"): result.insert("Aductores")
    case id == "piernas" || id.contains("pantorr"): result.insert("Pantorrillas")
    case id.contains("trapecio"): result.insert("Trapecio")
    case id.contains("romboid"):
        result.insert("Trapecio")
        result.insert("Dorsales")
    case id.contains("erectores") || muscle.bodyPart == "spine": result.insert("Erectores Espinales")
    case id.contains("cuello"): result.insert("Cuello")
    default: break
    }
    return result
}

private func wikiLabBodyPartAccent(_ bodyPart: String?) -> Color {
    switch bodyPart {
    case "upper": return Color(hex: 0x1E88E5)
    case "lower": return Color(hex: 0x43A047)
    case "core": return Color(hex: 0xFFFF8F00)
    case "spine": return Color(hex: 0x9C27B0)
    default: return Color(hex: 0x757575)
    }
}
