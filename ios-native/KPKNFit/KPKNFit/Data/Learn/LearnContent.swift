import Foundation

public let LEARN_MODULES: [LearnModule] = [
    tuPrimeraRutina,
    fundamentosEntrenamiento,
    nutricionDeportiva,
    ringsEntrenamiento,
    mentalidadDeportiva,
    herramientasKpkn,
]

private let tuPrimeraRutina = LearnModule(
    id: "primera-rutina",
    title: "Tu primera rutina en KPKN",
    category: .BEGINNER,
    shortDescription: "Guía paso a paso para crear tu primer programa de entrenamiento",
    icon: "🏋️",
    estimatedMinutes: 12,
    disclaimer: "Este curso es educativo. Consulta un profesional antes de iniciar un programa de entrenamiento.",
    submodules: [
        LearnSubmodule(
            id: "pr-1",
            title: "¿Qué es un programa?",
            content: [
                ContentBlock(type: .HEADING, text: "Programas en KPKN"),
                ContentBlock(type: .PARAGRAPH, text: "Un programa en KPKN es tu plan de entrenamiento organizado. Contiene semanas, sesiones y ejercicios con objetivos medibles."),
                ContentBlock(type: .TIP, text: "Si vienes de otro app, piensa en un programa como una \"rutina\" pero mucho más inteligente."),
                ContentBlock(type: .HEADING, text: "Estructura de un programa"),
                ContentBlock(type: .BULLET, items: [
                    "Macrociclo: el gran bloque (ej: 12 semanas)",
                    "Mesociclo: bloques intermedios (ej: 4 semanas)",
                    "Semanas: con días de entrenamiento asignados",
                    "Sesiones: cada día de entreno con ejercicios",
                ]),
                ContentBlock(type: .CALLOUT, text: "KPKN adapta tu entrenamiento según tu fatiga y recuperación. ¡No necesitas adivinar cuándo descansar!", accentColor: "0xFF448AFF"),
            ],
            quiz: [
                QuizQuestion(id: "pr1-q1", question: "¿Qué contiene un macrociclo?", options: ["Solo ejercicios", "Mesociclos y semanas", "Solo descansos", "Nutrición"], correctIndex: 1, explanation: "Un macrociclo agrupa mesociclos, que a su vez contienen semanas con sesiones."),
                QuizQuestion(id: "pr1-q2", question: "¿Qué hace el ajuste de entrenamiento?", options: ["Cuenta calorías", "Adapta el entrenamiento a tu recuperación", "Crea dietas", "Reproduce videos"], correctIndex: 1, explanation: "El sistema usa tu fatiga y readiness para optimizar tu entrenamiento."),
            ]
        ),
        LearnSubmodule(
            id: "pr-2",
            title: "Crea tu primer programa",
            content: [
                ContentBlock(type: .HEADING, text: "Paso a paso"),
                ContentBlock(type: .PARAGRAPH, text: "KPKN tiene un creador de programas que te guía en cada decisión. Aquí te explicamos qué elegir en cada paso."),
                ContentBlock(type: .HEADING, text: "1. Elige tu objetivo"),
                ContentBlock(type: .BULLET, items: [
                    "Hipertrofia: crecimiento muscular",
                    "Fuerza: levantar más peso",
                    "Potencia: fuerza + velocidad",
                    "Resistencia: aguantar más",
                ]),
                ContentBlock(type: .HEADING, text: "2. Define tu frecuencia"),
                ContentBlock(type: .PARAGRAPH, text: "¿Cuántos días puedes entrenar? KPKN adapta el volumen a tu disponibilidad. 3-4 días es ideal para principiantes."),
                ContentBlock(type: .TIP, text: "Es mejor ser constante 3 días que ir 5 días un mes y dejarlo. Elige una frecuencia realista."),
                ContentBlock(type: .HEADING, text: "3. Selecciona tu split"),
                ContentBlock(type: .PARAGRAPH, text: "El split organiza qué músculos trabajas cada día. Para principiantes, Full Body o Upper/Lower son los más efectivos."),
            ],
            quiz: [
                QuizQuestion(id: "pr2-q1", question: "¿Cuántos días es recomendable para un principiante?", options: ["6-7 días", "3-4 días", "1 día", "Todos los días"], correctIndex: 1, explanation: "3-4 días permite recuperación adecuada y es sostenible a largo plazo."),
                QuizQuestion(id: "pr2-q2", question: "¿Qué split es mejor para principiantes?", options: ["PPL avanzado", "Full Body o Upper/Lower", "Brosplit", "No importa"], correctIndex: 1, explanation: "Full Body y Upper/Lower son los más efectivos para principiantes."),
            ]
        ),
        LearnSubmodule(
            id: "pr-3",
            title: "Tu primera sesión",
            content: [
                ContentBlock(type: .HEADING, text: "Agregar ejercicios"),
                ContentBlock(type: .PARAGRAPH, text: "En el editor de sesiones puedes buscar ejercicios por nombre o músculo. Cada ejercicio tiene métricas que muestran su costo de fatiga."),
                ContentBlock(type: .HEADING, text: "Métricas de fatiga de cada ejercicio"),
                ContentBlock(type: .BULLET, items: [
                    "EFC: costo metabólico/fatiga local (1-5)",
                    "CNC: costo neural central (1-5)",
                    "SSC: costo estructural/espinal (0-2)",
                ]),
                ContentBlock(type: .WARNING, text: "Evita combinar muchos ejercicios con CNC alto en una misma sesión. Tu sistema nervioso necesita recuperarse."),
                ContentBlock(type: .HEADING, text: "Series, reps y RPE"),
                ContentBlock(type: .PARAGRAPH, text: "KPKN te permite definir series objetivo, rango de repeticiones y RPE (esfuerzo percibido) para cada ejercicio. El RPE 7-8 es ideal para la mayoría."),
            ],
            quiz: [
                QuizQuestion(id: "pr3-q1", question: "¿Qué mide el CNC?", options: ["Calorías", "Costo neural central", "Cardio", "Cadencia"], correctIndex: 1, explanation: "CNC mide el costo neural del ejercicio en una escala 1-5."),
                QuizQuestion(id: "pr3-q2", question: "¿Qué RPE es ideal para la mayoría de entrenamientos?", options: ["5-6", "7-8", "10 siempre", "1-2"], correctIndex: 1, explanation: "RPE 7-8 deja margen para progresar sin sobreentrenar."),
            ]
        ),
    ],
    finalQuiz: [
        QuizQuestion(id: "pr-f1", question: "¿Cuál es la estructura de un programa?", options: ["Ejercicio > Serie > Rep", "Macrociclo > Mesociclo > Semana > Sesión", "Solo sesiones sueltas", "No tiene estructura"], correctIndex: 1, explanation: "Los programas siguen una jerarquía desde macrociclo hasta sesión."),
        QuizQuestion(id: "pr-f2", question: "¿Qué frecuencia es ideal para principiantes?", options: ["7 días", "3-4 días", "1 día al mes", "No importa"], correctIndex: 1, explanation: "3-4 días es sostenible y permite recuperación."),
        QuizQuestion(id: "pr-f3", question: "¿Qué hace el EFC?", options: ["Mide peso", "Mide fatiga local/metabólica", "Cuenta reps", "Mide tiempo"], correctIndex: 1, explanation: "EFC (Exercise Fatigue Cost) mide el costo metabólico y fatiga local."),
        QuizQuestion(id: "pr-f4", question: "¿Qué split recomienda KPKN para principiantes?", options: ["PPL 6 días", "Full Body o Upper/Lower", "Solo brazos", "Solo cardio"], correctIndex: 1, explanation: "Full Body y Upper/Lower distribuyen volumen eficientemente."),
        QuizQuestion(id: "pr-f5", question: "¿Qué es RPE?", options: ["Ritmo de pulso", "Esfuerzo percibido (Rate of Perceived Exertion)", "Rutina personalizada", "Registro de progreso"], correctIndex: 1, explanation: "RPE mide qué tan duro sientes un set, del 1 al 10."),
    ],
    isSpecial: true
)

private let fundamentosEntrenamiento = LearnModule(
    id: "fundamentos-entreno",
    title: "Fundamentos del entrenamiento",
    category: .TRAINING,
    shortDescription: "Principios científicos detrás de cada decisión de entrenamiento",
    icon: "💪",
    estimatedMinutes: 15,
    submodules: [
        LearnSubmodule(
            id: "fe-1",
            title: "Principios de sobrecarga",
            content: [
                ContentBlock(type: .HEADING, text: "Sobrecarga progresiva"),
                ContentBlock(type: .PARAGRAPH, text: "Tu cuerpo se adapta al estrés que le impones. Para mejorar, necesitas aumentar gradualmente la demanda. Este es el principio más importante del entrenamiento."),
                ContentBlock(type: .HEADING, text: "Variables de progresión"),
                ContentBlock(type: .BULLET, items: [
                    "Volumen: más series o reps",
                    "Intensidad: más peso",
                    "Densidad: menos descanso",
                    "Frecuencia: entrenar más seguido",
                ]),
                ContentBlock(type: .TIP, text: "No necesitas aumentar todo a la vez. Enfócate en una variable por mesociclo."),
                ContentBlock(type: .CALLOUT, text: "La progresión no es lineal. Habrá semanas donde no avances, y eso es normal. La tendencia general importa más que el día a día.", accentColor: "0xFF43A047"),
            ],
            quiz: [
                QuizQuestion(id: "fe1-q1", question: "¿Qué es sobrecarga progresiva?", options: ["Hacer siempre lo mismo", "Aumentar gradualmente la demanda", "Entrenar hasta el fallo siempre", "No descansar"], correctIndex: 1, explanation: "Sobrecarga progresiva significa aumentar sistemáticamente el estímulo."),
            ]
        ),
        LearnSubmodule(
            id: "fe-2",
            title: "Volumen y frecuencia",
            content: [
                ContentBlock(type: .HEADING, text: "¿Cuánto volumen necesitas?"),
                ContentBlock(type: .PARAGRAPH, text: "El volumen (series por grupo muscular por semana) es el principal driver de hipertrofia. Pero más no siempre es mejor."),
                ContentBlock(type: .BULLET, items: [
                    "MEV (Volumen Mínimo Efectivo): ~6-8 series/semana",
                    "MAV (Volumen Máximo Adaptativo): ~12-18 series/semana",
                    "MRV (Volumen Máximo Recuperable): el límite antes de sobreentrenar",
                ]),
                ContentBlock(type: .HEADING, text: "Frecuencia óptima"),
                ContentBlock(type: .PARAGRAPH, text: "Entrenar cada músculo 2 veces por semana es superior a 1 vez para la mayoría de personas. Distribuir el volumen en más sesiones reduce la fatiga por sesión."),
                ContentBlock(type: .WARNING, text: "Si solo puedes ir 2 veces por semana, full body es la mejor opción. Nunca dejes un músculo sin entrenar una semana."),
            ],
            quiz: [
                QuizQuestion(id: "fe2-q1", question: "¿Qué es MEV?", options: ["Máximo esfuerzo voluntario", "Volumen mínimo efectivo", "Músculo en vacío", "Medición energética"], correctIndex: 1, explanation: "MEV es el mínimo volumen semanal para generar adaptaciones."),
                QuizQuestion(id: "fe2-q2", question: "¿Cuántas veces por semana es óptimo entrenar cada músculo?", options: ["1 vez", "2 veces", "7 veces", "Solo cuando duela"], correctIndex: 1, explanation: "2 veces por semana permite mejor distribución y recuperación."),
            ]
        ),
    ],
    finalQuiz: [
        QuizQuestion(id: "fe-f1", question: "¿Cuál es el principio más importante del entrenamiento?", options: ["Entrenar todos los días", "Sobrecarga progresiva", "Comer mucho", "Tomar suplementos"], correctIndex: 1, explanation: "La sobrecarga progresiva es el motor de toda adaptación."),
        QuizQuestion(id: "fe-f2", question: "¿Qué es MAV?", options: ["Volumen máximo adaptativo", "Mínimo aporte vital", "Músculo activo verificado", "Movimiento angular"], correctIndex: 0, explanation: "MAV es el rango de volumen donde obtienes las mejores adaptaciones."),
        QuizQuestion(id: "fe-f3", question: "¿Cuántas veces por semana es óptimo para cada músculo?", options: ["1", "2", "7", "Depende del humor"], correctIndex: 1, explanation: "2 veces por semana es superior para la mayoría."),
        QuizQuestion(id: "fe-f4", question: "¿Qué pasa si aumentas todo a la vez?", options: ["Progresas más rápido", "Riesgo de sobreentrenamiento", "Nada", "Te haces inmune"], correctIndex: 1, explanation: "Cambiar demasiadas variables a la vez aumenta el riesgo sin beneficio extra."),
    ]
)

private let nutricionDeportiva = LearnModule(
    id: "nutricion-deportiva",
    title: "Nutrición para deportistas",
    category: .NUTRITION,
    shortDescription: "Macros, timing y estrategias alimentarias para rendir mejor",
    icon: "🍝",
    estimatedMinutes: 14,
    submodules: [
        LearnSubmodule(
            id: "nd-1",
            title: "Macronutrientes básicos",
            content: [
                ContentBlock(type: .HEADING, text: "Los 3 macros"),
                ContentBlock(type: .PARAGRAPH, text: "Tu cuerpo necesita tres macronutrientes en cantidades significativas: proteínas, carbohidratos y grasas. Cada uno cumple funciones específicas."),
                ContentBlock(type: .HEADING, text: "Proteína"),
                ContentBlock(type: .PARAGRAPH, text: "El macro más importante para construir músculo. Recomendación: 1.6-2.2g por kg de peso corporal al día. Fuentes: pollo, pescado, huevos, legumbres."),
                ContentBlock(type: .HEADING, text: "Carbohidratos"),
                ContentBlock(type: .PARAGRAPH, text: "Tu principal fuente de energía para entrenar. Sin carbohidratos suficientes, tu rendimiento cae. Recomendación: 3-5g por kg de peso corporal."),
                ContentBlock(type: .HEADING, text: "Grasas"),
                ContentBlock(type: .PARAGRAPH, text: "Esenciales para hormonas y absorción de vitaminas. Nunca bajes de 0.8g por kg. Fuentes: aceite de oliva, frutos secos, aguacate."),
                ContentBlock(type: .TIP, text: "Usa el asistente de nutrición de KPKN para calcular tus macros personalizados según tu objetivo."),
            ],
            quiz: [
                QuizQuestion(id: "nd1-q1", question: "¿Cuánta proteína se recomienda por kg?", options: ["0.5g", "1.6-2.2g", "5g", "No importa"], correctIndex: 1, explanation: "1.6-2.2g/kg es el rango óptimo para la mayoría de atletas."),
                QuizQuestion(id: "nd1-q2", question: "¿Qué macro es principal fuente de energía?", options: ["Proteína", "Carbohidratos", "Grasas", "Vitaminas"], correctIndex: 1, explanation: "Los carbohidratos son el combustible principal para entrenar."),
            ]
        ),
        LearnSubmodule(
            id: "nd-2",
            title: "Timing y distribución",
            content: [
                ContentBlock(type: .HEADING, text: "Cuándo comer"),
                ContentBlock(type: .PARAGRAPH, text: "El timing importa menos que la dieta total del día, pero optimizarlo puede darte un 5-10% extra de rendimiento."),
                ContentBlock(type: .BULLET, items: [
                    "Pre-entreno (1-2h antes): carbohidratos + proteína moderada",
                    "Post-entreno (dentro de 2h): proteína + carbohidratos",
                    "Antes de dormir: proteína de digestión lenta (caseína, queso cottage)",
                ]),
                ContentBlock(type: .HEADING, text: "Distribución de comidas"),
                ContentBlock(type: .PARAGRAPH, text: "Distribuir la proteína en 3-5 comidas al día es superior a concentrarla en 1-2 comidas. Cada comida debe tener 25-40g de proteína."),
                ContentBlock(type: .WARNING, text: "Saltarse comidas después de entrenar \"porque no tienes hambre\" es un error. Tu músculo necesita nutrientes para recuperarse."),
            ],
            quiz: [
                QuizQuestion(id: "nd2-q1", question: "¿Cuánta proteína por comida es ideal?", options: ["5g", "25-40g", "100g", "No importa"], correctIndex: 1, explanation: "25-40g por comida maximiza la síntesis proteica."),
            ]
        ),
    ],
    finalQuiz: [
        QuizQuestion(id: "nd-f1", question: "¿Cuál es la recomendación de proteína por kg?", options: ["0.5g", "1.6-2.2g", "5g", "No necesitas proteína"], correctIndex: 1, explanation: "1.6-2.2g/kg cubre las necesidades de la mayoría."),
        QuizQuestion(id: "nd-f2", question: "¿Qué comer pre-entreno?", options: ["Solo agua", "Carbohidratos + proteína", "Solo grasa", "Nada"], correctIndex: 1, explanation: "Carbohidratos dan energía y proteína apoya la síntesis."),
        QuizQuestion(id: "nd-f3", question: "¿Cuántas comidas con proteína al día?", options: ["1 grande", "3-5 distribuidas", "10 pequeñas", "No importa"], correctIndex: 1, explanation: "Distribuir en 3-5 comidas maximiza la síntesis proteica."),
    ]
)

private let ringsEntrenamiento = LearnModule(
    id: "rings-entrenamiento",
    title: "Entrenamiento con anillas",
    category: .RINGS,
    shortDescription: "Domina las anillas: desde soporte hasta movimientos avanzados",
    icon: "🤸",
    estimatedMinutes: 16,
    submodules: [
        LearnSubmodule(
            id: "re-1",
            title: "Por qué anillas",
            content: [
                ContentBlock(type: .HEADING, text: "Ventajas de las anillas"),
                ContentBlock(type: .PARAGRAPH, text: "Las anillas son la herramienta de calistenia más versátil. Permiten movimiento en todos los planos y exigen una estabilización que ninguna máquina puede replicar."),
                ContentBlock(type: .BULLET, items: [
                    "Libertad de movimiento: rotación natural de hombros",
                    "Estabilización: cada músculo pequeño trabaja",
                    "Progresiones claras: de soporte a planche",
                    "Portabilidad: entrena en cualquier lugar",
                ]),
                ContentBlock(type: .WARNING, text: "Las anillas exigen más de tus tendones. Aumenta la dificultad gradualmente. Nunca saltes progresiones."),
            ],
            quiz: [
                QuizQuestion(id: "re1-q1", question: "¿Qué ventaja tienen las anillas vs máquinas?", options: ["Son más cómodas", "Permiten libertad de movimiento y estabilización", "Son más baratas", "No hay diferencia"], correctIndex: 1, explanation: "Las anillas permiten movimiento libre y exigen estabilización total."),
            ]
        ),
        LearnSubmodule(
            id: "re-2",
            title: "Progresiones básicas",
            content: [
                ContentBlock(type: .HEADING, text: "Soporte en anillas"),
                ContentBlock(type: .PARAGRAPH, text: "El soporte es la base de todo. Mantén los brazos extendidos, hombros abajo y core activo. Empieza con 3x20 segundos."),
                ContentBlock(type: .HEADING, text: "Progresión de dominadas"),
                ContentBlock(type: .BULLET, items: [
                    "Dominada asistida con banda",
                    "Dominada negativa (solo excéntrica)",
                    "Dominada completa en anillas",
                    "Dominada con lastre",
                ]),
                ContentBlock(type: .HEADING, text: "Progresión de fondos"),
                ContentBlock(type: .BULLET, items: [
                    "Fondos asistidos con banda",
                    "Fondos con rodillas flexionadas",
                    "Fondos completos en anillas",
                    "Fondos con rotación a muscle-up",
                ]),
                ContentBlock(type: .TIP, text: "Graba tus primeras sesiones en anillas. La forma importa mucho más que las repeticiones."),
            ],
            quiz: [
                QuizQuestion(id: "re2-q1", question: "¿Cuál es el primer ejercicio en anillas?", options: ["Muscle-up", "Soporte", "Planche", "Iron cross"], correctIndex: 1, explanation: "El soporte es la base: mantenerse arriba con brazos extendidos."),
            ]
        ),
    ],
    finalQuiz: [
        QuizQuestion(id: "re-f1", question: "¿Por qué las anillas son únicas?", options: ["Son baratas", "Libertad de movimiento y estabilización", "Son fáciles", "Solo para profesionales"], correctIndex: 1, explanation: "Las anillas permiten movimiento libre y exigen estabilización en todos los planos."),
        QuizQuestion(id: "re-f2", question: "¿Qué hacer antes de intentar muscle-up?", options: ["Saltar directamente", "Dominar soporte y dominadas", "Solo cardio", "Nada"], correctIndex: 1, explanation: "Necesitas dominar las bases antes de movimientos avanzados."),
        QuizQuestion(id: "re-f3", question: "¿Cuánto tiempo de soporte para empezar?", options: ["2 horas", "3x20 segundos", "1 minuto sin forma", "No importa"], correctIndex: 1, explanation: "3x20s con buena forma es el punto de partida estándar."),
    ]
)

private let mentalidadDeportiva = LearnModule(
    id: "mentalidad-deportiva",
    title: "Mentalidad deportiva",
    category: .MENTAL_HEALTH,
    shortDescription: "Gestión del estrés, motivación y consistencia en el gimnasio",
    icon: "🧠",
    estimatedMinutes: 10,
    submodules: [
        LearnSubmodule(
            id: "md-1",
            title: "La trampa de la motivación",
            content: [
                ContentBlock(type: .HEADING, text: "Motivación vs Disciplina"),
                ContentBlock(type: .PARAGRAPH, text: "La motivación es un estado emocional temporal. Si dependes de ella, dejarás de entrenar. La disciplina es lo que te mantiene consistente."),
                ContentBlock(type: .HEADING, text: "Sistemas > Metas"),
                ContentBlock(type: .PARAGRAPH, text: "En lugar de ponerte metas (\"quiero perder 10kg\"), crea sistemas (\"entreno lunes, miércoles y viernes a las 7am\"). Los sistemas producen resultados sin depender de cómo te sientas."),
                ContentBlock(type: .CALLOUT, text: "La consistencia aburrida vence a la intensidad intermitente. 3 sesiones regulares por 12 semanas superan a 6 sesiones esporádicas.", accentColor: "0xFF7E57C2"),
                ContentBlock(type: .TIP, text: "Regla de los 2 minutos: si no quieres ir al gimnasio, comprométete a ir solo por 2 minutos. El 90% de las veces te quedarás."),
            ],
            quiz: [
                QuizQuestion(id: "md1-q1", question: "¿Qué es más importante que la motivación?", options: ["Suerte", "Disciplina y sistemas", "Suplementos", "Genética"], correctIndex: 1, explanation: "La disciplina y los sistemas producen consistencia a largo plazo."),
            ]
        ),
        LearnSubmodule(
            id: "md-2",
            title: "Manejo del fracaso",
            content: [
                ContentBlock(type: .HEADING, text: "Perder sesiones está bien"),
                ContentBlock(type: .PARAGRAPH, text: "Enfermarte, viajar, obligaciones familiares... perderás sesiones. Lo importante es qué haces después."),
                ContentBlock(type: .BULLET, items: [
                    "No intentes \"compensar\" con sesiones más largas",
                    "Vuelve donde dejaste, no donde deberías estar",
                    "Una semana perdida no arruina 6 meses de progreso",
                    "El peor entrenamiento es el que no haces",
                ]),
                ContentBlock(type: .HEADING, text: "Evitar el perfeccionismo"),
                ContentBlock(type: .PARAGRAPH, text: "\"Todo o nada\" es el enemigo. Si no puedes hacer tu rutina completa, haz la mitad. Si no puedes ir al gimnasio, haz algo en casa. Cualquier movimiento cuenta."),
                ContentBlock(type: .WARNING, text: "Compararte con otros en redes sociales es el camino más rápido a la frustración. Tu única comparación válida eres tú mismo."),
            ],
            quiz: [
                QuizQuestion(id: "md2-q1", question: "¿Qué hacer si pierdes una semana de entrenamiento?", options: ["Hacer sesiones dobles", "Volver donde dejaste", "Empezar de cero", "Dejar de entrenar"], correctIndex: 1, explanation: "Volver donde dejaste es la estrategia más inteligente y sostenible."),
            ]
        ),
    ],
    finalQuiz: [
        QuizQuestion(id: "md-f1", question: "¿Qué es mejor que metas?", options: ["Más metas", "Sistemas", "No tener objetivos", "Motivación"], correctIndex: 1, explanation: "Los sistemas producen resultados consistentes sin depender del estado emocional."),
        QuizQuestion(id: "md-f2", question: "¿Qué es la regla de los 2 minutos?", options: ["Entrenar solo 2 minutos", "Comprometerte a empezar 2 minutos", "Descansar 2 minutos", "Calentar 2 minutos"], correctIndex: 1, explanation: "Comprometerte a 2 minutos suele resultar en una sesión completa."),
        QuizQuestion(id: "md-f3", question: "¿Qué es peor que un mal entrenamiento?", options: ["Uno bueno", "Ninguno", "Uno corto", "Uno largo"], correctIndex: 1, explanation: "El peor entrenamiento es el que no haces. Cualquier movimiento cuenta."),
    ]
)

private let herramientasKpkn = LearnModule(
    id: "herramientas-kpkn",
    title: "Herramientas de KPKN",
    category: .TOOLS,
    shortDescription: "Aprovecha al máximo WikiLab y el asistente de nutrición",
    icon: "🔧",
    estimatedMinutes: 11,
    submodules: [
        LearnSubmodule(
            id: "hk-1",
            title: "WikiLab: tu enciclopedia",
            content: [
                ContentBlock(type: .HEADING, text: "¿Qué es WikiLab?"),
                ContentBlock(type: .PARAGRAPH, text: "WikiLab es la enciclopedia de entrenamiento de KPKN. Contiene bases de datos de ejercicios, músculos, articulaciones, patrones de movimiento y más."),
                ContentBlock(type: .BULLET, items: [
                    "Catálogo de ejercicios con métricas de fatiga",
                    "Mapa muscular interactivo",
                    "Detalle biomecánico de cada ejercicio",
                    "Ejercicios similares por categoría",
                ]),
                ContentBlock(type: .TIP, text: "Usa WikiLab para entender por qué un ejercicio te deja más fatigado que otro. Las métricas de fatiga te lo explican."),
                ContentBlock(type: .HEADING, text: "Creador de ejercicios"),
                ContentBlock(type: .PARAGRAPH, text: "¿Tienes un ejercicio que no está en el catálogo? Crea el tuyo propio. KPKN inferirá automáticamente sus métricas de fatiga basándose en el tipo, equipo y patrón de fuerza."),
            ],
            quiz: [
                QuizQuestion(id: "hk1-q1", question: "¿Qué contiene WikiLab?", options: ["Solo recetas", "Ejercicios, músculos, articulaciones y biomecánica", "Solo videos", "Redes sociales"], correctIndex: 1, explanation: "WikiLab es la enciclopedia completa de entrenamiento de KPKN."),
            ]
        ),
        LearnSubmodule(
            id: "hk-2",
            title: "Adaptación del entrenamiento",
            content: [
                ContentBlock(type: .HEADING, text: "¿Qué adapta KPKN?"),
                ContentBlock(type: .PARAGRAPH, text: "KPKN mide tu fatiga acumulada, estado de recuperación y readiness diaria para adaptar tu entrenamiento."),
                ContentBlock(type: .HEADING, text: "Componentes de la adaptación"),
                ContentBlock(type: .BULLET, items: [
                    "Readiness diaria: cómo de preparado estás hoy",
                    "Baterías de recuperación: muscular, neural, estructural",
                    "Fatiga acumulada: por grupo muscular",
                    "Calibración: aprende de tus respuestas post-sesión",
                ]),
                ContentBlock(type: .CALLOUT, text: "El sistema mejora con el tiempo. Cada vez que completas un cuestionario post-sesión, aprende más sobre tu recuperación personal.", accentColor: "0xFFFF8F00"),
                ContentBlock(type: .HEADING, text: "Cómo usar la readiness"),
                ContentBlock(type: .PARAGRAPH, text: "Antes de cada sesión, KPKN te muestra tu nivel de readiness. Si está bajo, considera reducir volumen o intensidad. Si está alto, es un buen día para empujar."),
            ],
            quiz: [
                QuizQuestion(id: "hk2-q1", question: "¿Qué mide la adaptación?", options: ["Calorías", "Fatiga, recuperación y readiness", "Pasos", "Sueño"], correctIndex: 1, explanation: "Mide fatiga acumulada, recuperación y readiness diaria."),
            ]
        ),
    ],
    finalQuiz: [
        QuizQuestion(id: "hk-f1", question: "¿Qué es WikiLab?", options: ["Un chat", "Enciclopedia de entrenamiento", "Un foro", "Una tienda"], correctIndex: 1, explanation: "WikiLab es la enciclopedia completa con ejercicios, músculos y biomecánica."),
        QuizQuestion(id: "hk-f2", question: "¿Qué mide la adaptación?", options: ["Peso corporal", "Fatiga, recuperación y readiness", "Temperatura", "Nada"], correctIndex: 1, explanation: "Mide tu estado de entrenamiento."),
        QuizQuestion(id: "hk-f3", question: "¿Cómo mejora el sistema?", options: ["Comprando suplementos", "Completando cuestionarios post-sesión", "No mejora", "Con cardio"], correctIndex: 1, explanation: "Aprende de tus respuestas para personalizar las recomendaciones."),
    ]
)
