package com.example.kpkn.data.learn

// ═══════════════════════════════════════════════════════════════════════
// Contenido de los cursos Learn
// ═══════════════════════════════════════════════════════════════════════

val LEARN_MODULES: List<LearnModule> get() = listOf(
    tuPrimeraRutina,
    fundamentosEntrenamiento,
    nutricionDeportiva,
    ringsEntrenamiento,
    mentalidadDeportiva,
    herramientasKpkn,
)

// ─── 1. Tu primera rutina en KPKN (BEGINNER, especial) ─────────────────

private val tuPrimeraRutina = LearnModule(
    id = "primera-rutina",
    title = "Tu primera rutina en KPKN",
    category = LearnCategory.BEGINNER,
    shortDescription = "Guía paso a paso para crear tu primer programa de entrenamiento",
    icon = "\uD83C\uDFCB\uFE0F",
    estimatedMinutes = 12,
    isSpecial = true,
    disclaimer = "Este curso es educativo. Consulta un profesional antes de iniciar un programa de entrenamiento.",
    submodules = listOf(
        LearnSubmodule(
            id = "pr-1",
            title = "¿Qué es un programa?",
            content = listOf(
                ContentBlock(ContentType.HEADING, text = "Programas en KPKN"),
                ContentBlock(ContentType.PARAGRAPH, text = "Un programa en KPKN es tu plan de entrenamiento organizado. Contiene semanas, sesiones y ejercicios con objetivos medibles."),
                ContentBlock(ContentType.TIP, text = "Si vienes de otro app, piensa en un programa como una \"rutina\" pero mucho más inteligente."),
                ContentBlock(ContentType.HEADING, text = "Estructura de un programa"),
                ContentBlock(ContentType.BULLET, items = listOf(
                    "Macrociclo: el gran bloque (ej: 12 semanas)",
                    "Mesociclo: bloques intermedios (ej: 4 semanas)",
                    "Semanas: con días de entrenamiento asignados",
                    "Sesiones: cada día de entreno con ejercicios",
                )),
                ContentBlock(ContentType.CALLOUT, text = "El motor AUGE de KPKN adapta tu entrenamiento según tu fatiga y recuperación. ¡No necesitas adivinar cuándo descansar!", accentColor = 0xFF448AFF),
            ),
            quiz = listOf(
                QuizQuestion("pr1-q1", "¿Qué contiene un macrociclo?", listOf("Solo ejercicios", "Mesociclos y semanas", "Solo descansos", "Nutrición"), 1, "Un macrociclo agrupa mesociclos, que a su vez contienen semanas con sesiones."),
                QuizQuestion("pr1-q2", "¿Qué hace el motor AUGE?", listOf("Cuenta calorías", "Adapta el entrenamiento a tu recuperación", "Crea dietas", "Reproduce videos"), 1, "AUGE mide tu fatiga y readiness para optimizar tu entrenamiento."),
            ),
        ),
        LearnSubmodule(
            id = "pr-2",
            title = "Crea tu primer programa",
            content = listOf(
                ContentBlock(ContentType.HEADING, text = "Paso a paso"),
                ContentBlock(ContentType.PARAGRAPH, text = "KPKN tiene un creador de programas que te guía en cada decisión. Aquí te explicamos qué elegir en cada paso."),
                ContentBlock(ContentType.HEADING, text = "1. Elige tu objetivo"),
                ContentBlock(ContentType.BULLET, items = listOf(
                    "Hipertrofia: crecimiento muscular",
                    "Fuerza: levantar más peso",
                    "Potencia: fuerza + velocidad",
                    "Resistencia: aguantar más",
                )),
                ContentBlock(ContentType.HEADING, text = "2. Define tu frecuencia"),
                ContentBlock(ContentType.PARAGRAPH, text = "¿Cuántos días puedes entrenar? KPKN adapta el volumen a tu disponibilidad. 3-4 días es ideal para principiantes."),
                ContentBlock(ContentType.TIP, text = "Es mejor ser constante 3 días que ir 5 días un mes y dejarlo. Elige una frecuencia realista."),
                ContentBlock(ContentType.HEADING, text = "3. Selecciona tu split"),
                ContentBlock(ContentType.PARAGRAPH, text = "El split organiza qué músculos trabajas cada día. Para principiantes, Full Body o Upper/Lower son los más efectivos."),
            ),
            quiz = listOf(
                QuizQuestion("pr2-q1", "¿Cuántos días es recomendable para un principiante?", listOf("6-7 días", "3-4 días", "1 día", "Todos los días"), 1, "3-4 días permite recuperación adecuada y es sostenible a largo plazo."),
                QuizQuestion("pr2-q2", "¿Qué split es mejor para principiantes?", listOf("PPL avanzado", "Full Body o Upper/Lower", "Brosplit", "No importa"), 1, "Full Body y Upper/Lower son los más efectivos para principiantes."),
            ),
        ),
        LearnSubmodule(
            id = "pr-3",
            title = "Tu primera sesión",
            content = listOf(
                ContentBlock(ContentType.HEADING, text = "Agregar ejercicios"),
                ContentBlock(ContentType.PARAGRAPH, text = "En el editor de sesiones puedes buscar ejercicios por nombre o músculo. Cada ejercicio tiene métricas AUGE que muestran su costo de fatiga."),
                ContentBlock(ContentType.HEADING, text = "Métricas AUGE de cada ejercicio"),
                ContentBlock(ContentType.BULLET, items = listOf(
                    "EFC: costo metabólico/fatiga local (1-5)",
                    "CNC: costo neural central (1-5)",
                    "SSC: costo estructural/espinal (0-2)",
                )),
                ContentBlock(ContentType.WARNING, text = "Evita combinar muchos ejercicios con CNC alto en una misma sesión. Tu sistema nervioso necesita recuperarse."),
                ContentBlock(ContentType.HEADING, text = "Series, reps y RPE"),
                ContentBlock(ContentType.PARAGRAPH, text = "KPKN te permite definir series objetivo, rango de repeticiones y RPE (esfuerzo percibido) para cada ejercicio. El RPE 7-8 es ideal para la mayoría."),
            ),
            quiz = listOf(
                QuizQuestion("pr3-q1", "¿Qué mide el CNC?", listOf("Calorías", "Costo neural central", "Cardio", "Cadencia"), 1, "CNC mide el costo neural del ejercicio en una escala 1-5."),
                QuizQuestion("pr3-q2", "¿Qué RPE es ideal para la mayoría de entrenamientos?", listOf("5-6", "7-8", "10 siempre", "1-2"), 1, "RPE 7-8 deja margen para progresar sin sobreentrenar."),
            ),
        ),
    ),
    finalQuiz = listOf(
        QuizQuestion("pr-f1", "¿Cuál es la estructura de un programa?", listOf("Ejercicio > Serie > Rep", "Macrociclo > Mesociclo > Semana > Sesión", "Solo sesiones sueltas", "No tiene estructura"), 1, "Los programas siguen una jerarquía desde macrociclo hasta sesión."),
        QuizQuestion("pr-f2", "¿Qué frecuencia es ideal para principiantes?", listOf("7 días", "3-4 días", "1 día al mes", "No importa"), 1, "3-4 días es sostenible y permite recuperación."),
        QuizQuestion("pr-f3", "¿Qué hace el EFC?", listOf("Mide peso", "Mide fatiga local/metabólica", "Cuenta reps", "Mide tiempo"), 1, "EFC (Exercise Fatigue Cost) mide el costo metabólico y fatiga local."),
        QuizQuestion("pr-f4", "¿Qué split recomienda KPKN para principiantes?", listOf("PPL 6 días", "Full Body o Upper/Lower", "Solo brazos", "Solo cardio"), 1, "Full Body y Upper/Lower distribuyen volumen eficientemente."),
        QuizQuestion("pr-f5", "¿Qué es RPE?", listOf("Ritmo de pulso", "Esfuerzo percibido (Rate of Perceived Exertion)", "Rutina personalizada", "Registro de progreso"), 1, "RPE mide qué tan duro sientes un set, del 1 al 10."),
    ),
)

// ─── 2. Fundamentos del entrenamiento (TRAINING) ────────────────────────

private val fundamentosEntrenamiento = LearnModule(
    id = "fundamentos-entreno",
    title = "Fundamentos del entrenamiento",
    category = LearnCategory.TRAINING,
    shortDescription = "Principios científicos detrás de cada decisión de entrenamiento",
    icon = "\uD83D\uDCAA",
    estimatedMinutes = 15,
    submodules = listOf(
        LearnSubmodule(
            id = "fe-1",
            title = "Principios de sobrecarga",
            content = listOf(
                ContentBlock(ContentType.HEADING, text = "Sobrecarga progresiva"),
                ContentBlock(ContentType.PARAGRAPH, text = "Tu cuerpo se adapta al estrés que le impones. Para mejorar, necesitas aumentar gradualmente la demanda. Este es el principio más importante del entrenamiento."),
                ContentBlock(ContentType.HEADING, text = "Variables de progresión"),
                ContentBlock(ContentType.BULLET, items = listOf(
                    "Volumen: más series o reps",
                    "Intensidad: más peso",
                    "Densidad: menos descanso",
                    "Frecuencia: entrenar más seguido",
                )),
                ContentBlock(ContentType.TIP, text = "No necesitas aumentar todo a la vez. Enfócate en una variable por mesociclo."),
                ContentBlock(ContentType.CALLOUT, text = "La progresión no es lineal. Habrá semanas donde no avances, y eso es normal. La tendencia general importa más que el día a día.", accentColor = 0xFF43A047),
            ),
            quiz = listOf(
                QuizQuestion("fe1-q1", "¿Qué es sobrecarga progresiva?", listOf("Hacer siempre lo mismo", "Aumentar gradualmente la demanda", "Entrenar hasta el fallo siempre", "No descansar"), 1, "Sobrecarga progresiva significa aumentar sistemáticamente el estímulo."),
            ),
        ),
        LearnSubmodule(
            id = "fe-2",
            title = "Volumen y frecuencia",
            content = listOf(
                ContentBlock(ContentType.HEADING, text = "¿Cuánto volumen necesitas?"),
                ContentBlock(ContentType.PARAGRAPH, text = "El volumen (series por grupo muscular por semana) es el principal driver de hipertrofia. Pero más no siempre es mejor."),
                ContentBlock(ContentType.BULLET, items = listOf(
                    "MEV (Volumen Mínimo Efectivo): ~6-8 series/semana",
                    "MAV (Volumen Máximo Adaptativo): ~12-18 series/semana",
                    "MRV (Volumen Máximo Recuperable): el límite antes de sobreentrenar",
                )),
                ContentBlock(ContentType.HEADING, text = "Frecuencia óptima"),
                ContentBlock(ContentType.PARAGRAPH, text = "Entrenar cada músculo 2 veces por semana es superior a 1 vez para la mayoría de personas. Distribuir el volumen en más sesiones reduce la fatiga por sesión."),
                ContentBlock(ContentType.WARNING, text = "Si solo puedes ir 2 veces por semana, full body es la mejor opción. Nunca dejes un músculo sin entrenar una semana."),
            ),
            quiz = listOf(
                QuizQuestion("fe2-q1", "¿Qué es MEV?", listOf("Máximo esfuerzo voluntario", "Volumen mínimo efectivo", "Músculo en vacío", "Medición energética"), 1, "MEV es el mínimo volumen semanal para generar adaptaciones."),
                QuizQuestion("fe2-q2", "¿Cuántas veces por semana es óptimo entrenar cada músculo?", listOf("1 vez", "2 veces", "7 veces", "Solo cuando duela"), 1, "2 veces por semana permite mejor distribución y recuperación."),
            ),
        ),
    ),
    finalQuiz = listOf(
        QuizQuestion("fe-f1", "¿Cuál es el principio más importante del entrenamiento?", listOf("Entrenar todos los días", "Sobrecarga progresiva", "Comer mucho", "Tomar suplementos"), 1, "La sobrecarga progresiva es el motor de toda adaptación."),
        QuizQuestion("fe-f2", "¿Qué es MAV?", listOf("Volumen máximo adaptativo", "Mínimo aporte vital", "Músculo activo verificado", "Movimiento angular"), 0, "MAV es el rango de volumen donde obtienes las mejores adaptaciones."),
        QuizQuestion("fe-f3", "¿Cuántas veces por semana es óptimo para cada músculo?", listOf("1", "2", "7", "Depende del humor"), 1, "2 veces por semana es superior para la mayoría."),
        QuizQuestion("fe-f4", "¿Qué pasa si aumentas todo a la vez?", listOf("Progresas más rápido", "Riesgo de sobreentrenamiento", "Nada", "Te haces inmune"), 1, "Cambiar demasiadas variables a la vez aumenta el riesgo sin beneficio extra."),
    ),
)

// ─── 3. Nutrición para deportistas (NUTRITION) ──────────────────────────

private val nutricionDeportiva = LearnModule(
    id = "nutricion-deportiva",
    title = "Nutrición para deportistas",
    category = LearnCategory.NUTRITION,
    shortDescription = "Macros, timing y estrategias alimentarias para rendir mejor",
    icon = "\uD83C\uDF5D",
    estimatedMinutes = 14,
    submodules = listOf(
        LearnSubmodule(
            id = "nd-1",
            title = "Macronutrientes básicos",
            content = listOf(
                ContentBlock(ContentType.HEADING, text = "Los 3 macros"),
                ContentBlock(ContentType.PARAGRAPH, text = "Tu cuerpo necesita tres macronutrientes en cantidades significativas: proteínas, carbohidratos y grasas. Cada uno cumple funciones específicas."),
                ContentBlock(ContentType.HEADING, text = "Proteína"),
                ContentBlock(ContentType.PARAGRAPH, text = "El macro más importante para construir músculo. Recomendación: 1.6-2.2g por kg de peso corporal al día. Fuentes: pollo, pescado, huevos, legumbres."),
                ContentBlock(ContentType.HEADING, text = "Carbohidratos"),
                ContentBlock(ContentType.PARAGRAPH, text = "Tu principal fuente de energía para entrenar. Sin carbohidratos suficientes, tu rendimiento cae. Recomendación: 3-5g por kg de peso corporal."),
                ContentBlock(ContentType.HEADING, text = "Grasas"),
                ContentBlock(ContentType.PARAGRAPH, text = "Esenciales para hormonas y absorción de vitaminas. Nunca bajes de 0.8g por kg. Fuentes: aceite de oliva, frutos secos, aguacate."),
                ContentBlock(ContentType.TIP, text = "Usa el asistente de nutrición de KPKN para calcular tus macros personalizados según tu objetivo."),
            ),
            quiz = listOf(
                QuizQuestion("nd1-q1", "¿Cuánta proteína se recomienda por kg?", listOf("0.5g", "1.6-2.2g", "5g", "No importa"), 1, "1.6-2.2g/kg es el rango óptimo para la mayoría de atletas."),
                QuizQuestion("nd1-q2", "¿Qué macro es principal fuente de energía?", listOf("Proteína", "Carbohidratos", "Grasas", "Vitaminas"), 1, "Los carbohidratos son el combustible principal para entrenar."),
            ),
        ),
        LearnSubmodule(
            id = "nd-2",
            title = "Timing y distribución",
            content = listOf(
                ContentBlock(ContentType.HEADING, text = "Cuándo comer"),
                ContentBlock(ContentType.PARAGRAPH, text = "El timing importa menos que la dieta total del día, pero optimizarlo puede darte un 5-10% extra de rendimiento."),
                ContentBlock(ContentType.BULLET, items = listOf(
                    "Pre-entreno (1-2h antes): carbohidratos + proteína moderada",
                    "Post-entreno (dentro de 2h): proteína + carbohidratos",
                    "Antes de dormir: proteína de digestión lenta (caseína, queso cottage)",
                )),
                ContentBlock(ContentType.HEADING, text = "Distribución de comidas"),
                ContentBlock(ContentType.PARAGRAPH, text = "Distribuir la proteína en 3-5 comidas al día es superior a concentrarla en 1-2 comidas. Cada comida debe tener 25-40g de proteína."),
                ContentBlock(ContentType.WARNING, text = "Saltarse comidas después de entrenar \"porque no tienes hambre\" es un error. Tu músculo necesita nutrientes para recuperarse."),
            ),
            quiz = listOf(
                QuizQuestion("nd2-q1", "¿Cuánta proteína por comida es ideal?", listOf("5g", "25-40g", "100g", "No importa"), 1, "25-40g por comida maximiza la síntesis proteica."),
            ),
        ),
    ),
    finalQuiz = listOf(
        QuizQuestion("nd-f1", "¿Cuál es la recomendación de proteína por kg?", listOf("0.5g", "1.6-2.2g", "5g", "No necesitas proteína"), 1, "1.6-2.2g/kg cubre las necesidades de la mayoría."),
        QuizQuestion("nd-f2", "¿Qué comer pre-entreno?", listOf("Solo agua", "Carbohidratos + proteína", "Solo grasa", "Nada"), 1, "Carbohidratos dan energía y proteína apoya la síntesis."),
        QuizQuestion("nd-f3", "¿Cuántas comidas con proteína al día?", listOf("1 grande", "3-5 distribuidas", "10 pequeñas", "No importa"), 1, "Distribuir en 3-5 comidas maximiza la síntesis proteica."),
    ),
)

// ─── 4. RINGS: Entrenamiento con anillas (RINGS) ────────────────────────

private val ringsEntrenamiento = LearnModule(
    id = "rings-entrenamiento",
    title = "Entrenamiento con anillas",
    category = LearnCategory.RINGS,
    shortDescription = "Domina las anillas: desde soporte hasta movimientos avanzados",
    icon = "\uD83E\uDD38",
    estimatedMinutes = 16,
    submodules = listOf(
        LearnSubmodule(
            id = "re-1",
            title = "Por qué anillas",
            content = listOf(
                ContentBlock(ContentType.HEADING, text = "Ventajas de las anillas"),
                ContentBlock(ContentType.PARAGRAPH, text = "Las anillas son la herramienta de calistenia más versátil. Permiten movimiento en todos los planos y exigen una estabilización que ninguna máquina puede replicar."),
                ContentBlock(ContentType.BULLET, items = listOf(
                    "Libertad de movimiento: rotación natural de hombros",
                    "Estabilización: cada músculo pequeño trabaja",
                    "Progresiones claras: de soporte a planche",
                    "Portabilidad: entrena en cualquier lugar",
                )),
                ContentBlock(ContentType.WARNING, text = "Las anillas exigen más de tus tendones. Aumenta la dificultad gradualmente. Nunca saltes progresiones."),
            ),
            quiz = listOf(
                QuizQuestion("re1-q1", "¿Qué ventaja tienen las anillas vs máquinas?", listOf("Son más cómodas", "Permiten libertad de movimiento y estabilización", "Son más baratas", "No hay diferencia"), 1, "Las anillas permiten movimiento libre y exigen estabilización total."),
            ),
        ),
        LearnSubmodule(
            id = "re-2",
            title = "Progresiones básicas",
            content = listOf(
                ContentBlock(ContentType.HEADING, text = "Soporte en anillas"),
                ContentBlock(ContentType.PARAGRAPH, text = "El soporte es la base de todo. Mantén los brazos extendidos, hombros abajo y core activo. Empieza con 3x20 segundos."),
                ContentBlock(ContentType.HEADING, text = "Progresión de dominadas"),
                ContentBlock(ContentType.BULLET, items = listOf(
                    "Dominada asistida con banda",
                    "Dominada negativa (solo excéntrica)",
                    "Dominada completa en anillas",
                    "Dominada con lastre",
                )),
                ContentBlock(ContentType.HEADING, text = "Progresión de fondos"),
                ContentBlock(ContentType.BULLET, items = listOf(
                    "Fondos asistidos con banda",
                    "Fondos con rodillas flexionadas",
                    "Fondos completos en anillas",
                    "Fondos con rotación a muscle-up",
                )),
                ContentBlock(ContentType.TIP, text = "Graba tus primeras sesiones en anillas. La forma importa mucho más que las repeticiones."),
            ),
            quiz = listOf(
                QuizQuestion("re2-q1", "¿Cuál es el primer ejercicio en anillas?", listOf("Muscle-up", "Soporte", "Planche", "Iron cross"), 1, "El soporte es la base: mantenerse arriba con brazos extendidos."),
            ),
        ),
    ),
    finalQuiz = listOf(
        QuizQuestion("re-f1", "¿Por qué las anillas son únicas?", listOf("Son baratas", "Libertad de movimiento y estabilización", "Son fáciles", "Solo para profesionales"), 1, "Las anillas permiten movimiento libre y exigen estabilación en todos los planos."),
        QuizQuestion("re-f2", "¿Qué hacer antes de intentar muscle-up?", listOf("Saltar directamente", "Dominar soporte y dominadas", "Solo cardio", "Nada"), 1, "Necesitas dominar las bases antes de movimientos avanzados."),
        QuizQuestion("re-f3", "¿Cuánto tiempo de soporte para empezar?", listOf("2 horas", "3x20 segundos", "1 minuto sin forma", "No importa"), 1, "3x20s con buena forma es el punto de partida estándar."),
    ),
)

// ─── 5. Mentalidad deportiva (MENTAL_HEALTH) ────────────────────────────

private val mentalidadDeportiva = LearnModule(
    id = "mentalidad-deportiva",
    title = "Mentalidad deportiva",
    category = LearnCategory.MENTAL_HEALTH,
    shortDescription = "Gestión del estrés, motivación y consistencia en el gimnasio",
    icon = "\uD83E\uDDE0",
    estimatedMinutes = 10,
    submodules = listOf(
        LearnSubmodule(
            id = "md-1",
            title = "La trampa de la motivación",
            content = listOf(
                ContentBlock(ContentType.HEADING, text = "Motivación vs Disciplina"),
                ContentBlock(ContentType.PARAGRAPH, text = "La motivación es un estado emocional temporal. Si dependes de ella, dejarás de entrenar. La disciplina es lo que te mantiene consistente."),
                ContentBlock(ContentType.HEADING, text = "Sistemas > Metas"),
                ContentBlock(ContentType.PARAGRAPH, text = "En lugar de ponerte metas (\"quiero perder 10kg\"), crea sistemas (\"entreno lunes, miércoles y viernes a las 7am\"). Los sistemas producen resultados sin depender de cómo te sientas."),
                ContentBlock(ContentType.CALLOUT, text = "La consistencia aburrida vence a la intensidad intermitente. 3 sesiones regulares por 12 semanas superan a 6 sesiones esporádicas.", accentColor = 0xFF7E57C2),
                ContentBlock(ContentType.TIP, text = "Regla de los 2 minutos: si no quieres ir al gimnasio, comprométete a ir solo por 2 minutos. El 90% de las veces te quedarás."),
            ),
            quiz = listOf(
                QuizQuestion("md1-q1", "¿Qué es más importante que la motivación?", listOf("Suerte", "Disciplina y sistemas", "Suplementos", "Genética"), 1, "La disciplina y los sistemas producen consistencia a largo plazo."),
            ),
        ),
        LearnSubmodule(
            id = "md-2",
            title = "Manejo del fracaso",
            content = listOf(
                ContentBlock(ContentType.HEADING, text = "Perder sesiones está bien"),
                ContentBlock(ContentType.PARAGRAPH, text = "Enfermarte, viajar, obligaciones familiares... perderás sesiones. Lo importante es qué haces después."),
                ContentBlock(ContentType.BULLET, items = listOf(
                    "No intentes \"compensar\" con sesiones más largas",
                    "Vuelve donde dejaste, no donde deberías estar",
                    "Una semana perdida no arruina 6 meses de progreso",
                    "El peor entrenamiento es el que no haces",
                )),
                ContentBlock(ContentType.HEADING, text = "Evitar el perfeccionismo"),
                ContentBlock(ContentType.PARAGRAPH, text = "\"Todo o nada\" es el enemigo. Si no puedes hacer tu rutina completa, haz la mitad. Si no puedes ir al gimnasio, haz algo en casa. Cualquier movimiento cuenta."),
                ContentBlock(ContentType.WARNING, text = "Compararte con otros en redes sociales es el camino más rápido a la frustración. Tu única comparación válida eres tú mismo."),
            ),
            quiz = listOf(
                QuizQuestion("md2-q1", "¿Qué hacer si pierdes una semana de entrenamiento?", listOf("Hacer sesiones dobles", "Volver donde dejaste", "Empezar de cero", "Dejar de entrenar"), 1, "Volver donde dejaste es la estrategia más inteligente y sostenible."),
            ),
        ),
    ),
    finalQuiz = listOf(
        QuizQuestion("md-f1", "¿Qué es mejor que metas?", listOf("Más metas", "Sistemas", "No tener objetivos", "Motivación"), 1, "Los sistemas producen resultados consistentes sin depender del estado emocional."),
        QuizQuestion("md-f2", "¿Qué es la regla de los 2 minutos?", listOf("Entrenar solo 2 minutos", "Comprometerte a empezar 2 minutos", "Descansar 2 minutos", "Calentar 2 minutos"), 1, "Comprometerte a 2 minutos suele resultar en una sesión completa."),
        QuizQuestion("md-f3", "¿Qué es peor que un mal entrenamiento?", listOf("Uno bueno", "Ninguno", "Uno corto", "Uno largo"), 1, "El peor entrenamiento es el que no haces. Cualquier movimiento cuenta."),
    ),
)

// ─── 6. Herramientas de KPKN (TOOLS) ────────────────────────────────────

private val herramientasKpkn = LearnModule(
    id = "herramientas-kpkn",
    title = "Herramientas de KPKN",
    category = LearnCategory.TOOLS,
    shortDescription = "Aprovecha al máximo WikiLab, AUGE y el asistente de nutrición",
    icon = "\uD83D\uDD27",
    estimatedMinutes = 11,
    submodules = listOf(
        LearnSubmodule(
            id = "hk-1",
            title = "WikiLab: tu enciclopedia",
            content = listOf(
                ContentBlock(ContentType.HEADING, text = "¿Qué es WikiLab?"),
                ContentBlock(ContentType.PARAGRAPH, text = "WikiLab es la enciclopedia de entrenamiento de KPKN. Contiene bases de datos de ejercicios, músculos, articulaciones, patrones de movimiento y más."),
                ContentBlock(ContentType.BULLET, items = listOf(
                    "Catálogo de ejercicios con métricas AUGE",
                    "Mapa muscular interactivo",
                    "Detalle biomecánico de cada ejercicio",
                    "Ejercicios similares por categoría",
                )),
                ContentBlock(ContentType.TIP, text = "Usa WikiLab para entender por qué un ejercicio te deja más fatigado que otro. Las métricas AUGE te lo explican."),
                ContentBlock(ContentType.HEADING, text = "Creador de ejercicios"),
                ContentBlock(ContentType.PARAGRAPH, text = "¿Tienes un ejercicio que no está en el catálogo? Crea el tuyo propio. El motor AUGE inferirá automáticamente sus métricas de fatiga basándose en el tipo, equipo y patrón de fuerza."),
            ),
            quiz = listOf(
                QuizQuestion("hk1-q1", "¿Qué contiene WikiLab?", listOf("Solo recetas", "Ejercicios, músculos, articulaciones y biomecánica", "Solo videos", "Redes sociales"), 1, "WikiLab es la enciclopedia completa de entrenamiento de KPKN."),
            ),
        ),
        LearnSubmodule(
            id = "hk-2",
            title = "AUGE: tu motor de adaptación",
            content = listOf(
                ContentBlock(ContentType.HEADING, text = "¿Qué es AUGE?"),
                ContentBlock(ContentType.PARAGRAPH, text = "AUGE es el motor de inteligencia de KPKN. Mide tu fatiga acumulada, estado de recuperación y readiness diaria para adaptar tu entrenamiento."),
                ContentBlock(ContentType.HEADING, text = "Componentes de AUGE"),
                ContentBlock(ContentType.BULLET, items = listOf(
                    "Readiness diaria: cómo de preparado estás hoy",
                    "Baterías de recuperación: muscular, neural, estructural",
                    "Fatiga acumulada: por grupo muscular",
                    "Calibración: aprende de tus respuestas post-sesión",
                )),
                ContentBlock(ContentType.CALLOUT, text = "AUGE mejora con el tiempo. Cada vez que completas un cuestionario post-sesión, el sistema aprende más sobre tu recuperación personal.", accentColor = 0xFFFF8F00),
                ContentBlock(ContentType.HEADING, text = "Cómo usar la readiness"),
                ContentBlock(ContentType.PARAGRAPH, text = "Antes de cada sesión, AUGE te muestra tu nivel de readiness. Si está bajo, considera reducir volumen o intensidad. Si está alto, es un buen día para empujar."),
            ),
            quiz = listOf(
                QuizQuestion("hk2-q1", "¿Qué mide AUGE?", listOf("Calorías", "Fatiga, recuperación y readiness", "Pasos", "Sueño"), 1, "AUGE mide fatiga acumulada, recuperación y readiness diaria."),
            ),
        ),
    ),
    finalQuiz = listOf(
        QuizQuestion("hk-f1", "¿Qué es WikiLab?", listOf("Un chat", "Enciclopedia de entrenamiento", "Un foro", "Una tienda"), 1, "WikiLab es la enciclopedia completa con ejercicios, músculos y biomecánica."),
        QuizQuestion("hk-f2", "¿Qué mide AUGE?", listOf("Peso corporal", "Fatiga, recuperación y readiness", "Temperatura", "Nada"), 1, "AUGE es el motor de adaptación que mide tu estado de entrenamiento."),
        QuizQuestion("hk-f3", "¿Cómo mejora AUGE?", listOf("Comprando suplementos", "Completando cuestionarios post-sesión", "No mejora", "Con cardio"), 1, "AUGE aprende de tus respuestas para personalizar las recomendaciones."),
    ),
)
