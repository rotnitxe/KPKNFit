package com.example.kpkn.data.wikilab

import androidx.compose.ui.graphics.Color

private val TRAINING_CONCEPT_SHORT_DESCRIPTIONS: Map<String, String> = mapOf(
    "volumen-entrenamiento" to "Cantidad total de trabajo realizado en una sesión o semana",
    "intensidad" to "Porcentaje de carga relativa al máximo o nivel de esfuerzo",
    "frecuencia" to "Número de veces que se entrena un grupo muscular por semana",
    "rir" to "Cuántas repeticiones te quedan antes de llegar al fallo",
    "rpe" to "Escala subjetiva de esfuerzo percibido del 1 al 10",
    "fallo-muscular" to "Punto donde no puedes completar otra repetición concéntrica",
    "fatiga-sistemica" to "Fatiga acumulada que afecta al organismo completo, no solo al músculo",
    "dano-muscular" to "Microlesiones en las fibras musculares causadas por el entrenamiento",
    "tension-mecanica" to "Fuerza y torque aplicado sobre el músculo durante su contracción",
    "estres-metabolico" to "Acumulación de metabolitos durante series de alta repetición",
    "rom" to "La amplitud total del movimiento articular durante un ejercicio",
    "perfil-resistencia" to "Cómo varía la dificultad y el brazo de momento a lo largo del recorrido",
    "fase-excentrica" to "La fase de descenso controlado donde el músculo se alarga bajo tensión",
    "fase-concentrica" to "La fase de levantamiento donde el músculo se acorta produciendo fuerza",
    "isometria" to "Contracción muscular sin cambio en la longitud del músculo",
    "pliometria" to "Movimientos explosivos que aprovechan el ciclo estiramiento-acortamiento",
    "carga-axial" to "Fuerza que se aplica a lo largo del eje longitudinal del cuerpo o segmento",
    "pesos-libres" to "Barras, mancuernas y kettlebells — resistencia sin guía de trayectoria",
    "maquinas" to "Equipos con trayectoria guiada para aislamiento muscular controlado",
    "poleas" to "Sistemas de cable que mantienen tensión útil en distintos ángulos",
    "fuerza" to "Capacidad neuromuscular de ejercer torque articular contra resistencia",
    "explosividad" to "Capacidad de generar mucha fuerza en el menor tiempo posible",
    "elasticidad" to "Capacidad de almacenar y liberar energía elástica en el complejo músculo-tendón",
    "resistencia-muscular" to "Capacidad de mantener la producción de fuerza durante un período prolongado",
    "sobrecarga-progresiva" to "Incrementar gradualmente el estímulo para forzar adaptaciones continuas",
    "deload" to "Reducción planificada del volumen o intensidad para disipar fatiga acumulada",
    "especificidad" to "Las adaptaciones son específicas al tipo de estímulo aplicado",
)

/** Additional practical context for the long Conceptos Clave body. */
private val TRAINING_CONCEPT_EDITORIAL_APPENDICES: Map<String, String> = mapOf(
    "volumen-entrenamiento" to "En la práctica, cuenta el trabajo que realmente puedes recuperar, no solo el que cabe en la hoja. Una serie cerca del límite puede pesar más que varias muy cómodas, y el mismo número cambia según el ejercicio. Mira tendencias de rendimiento y medidas corporales antes de añadir otra serie.",
    "intensidad" to "También conviene preguntar siempre: ¿intensidad de qué? Una carga puede ser alta respecto de tu máximo, pero una serie puede sentirse moderada si quedan muchas repeticiones. Nombrar la referencia evita discusiones y ayuda a elegir el estímulo sin confundir dificultad local con esfuerzo global.",
    "frecuencia" to "Una distribución práctica debe dejar espacio para que cada sesión conserve repeticiones de calidad. Si dividir el trabajo reduce la fatiga de una sola jornada, puede ser una mejora aunque el total semanal no cambie. Si multiplicar días solo añade desplazamientos y series mediocres, no aporta una ventaja automática.",
    "rir" to "La estimación suele ser más consistente en ejercicios conocidos y con repeticiones estables. En movimientos complejos, la técnica puede fallar antes que el músculo objetivo, por lo que el RIR debe describir la repetición que aún sería válida, no una repetición forzada con compensaciones.",
    "rpe" to "Usar la escala resulta más fácil cuando anotas qué significó cada número para ti. Un RPE 8 en una sentadilla y en un curl no tiene la misma carga respiratoria ni la misma precisión, pero ambos pueden informar si la sesión está dentro del plan. La conversación entre sensación y registro es la herramienta.",
    "fallo-muscular" to "La definición cambia si el ejercicio permite una repetición parcial, una pausa o una ayuda externa; por eso conviene describir la regla antes de comparar series. En la mayoría de tareas, dejar una o dos repeticiones limpias ofrece suficiente estímulo y hace más predecible la semana siguiente.",
    "fatiga-sistemica" to "No todo cansancio exige una descarga y no toda fatiga se siente como sueño. Compara varios indicadores con tu línea habitual: rendimiento, coordinación, ganas de entrenar y recuperación entre sesiones. Una decisión prudente reduce la demanda durante unos días y observa si la capacidad vuelve, en lugar de etiquetar una causa con certeza.",
    "dano-muscular" to "El daño no es el objetivo que se debe perseguir para demostrar que una sesión funcionó. Un estímulo novedoso puede causar más molestias aunque el crecimiento no sea mayor, y repetirlo sin adaptación puede limitar el entrenamiento posterior. La progresión gradual permite aprender qué dosis toleras sin convertir el dolor en métrica.",
    "tension-mecanica" to "La sensación de tensión no identifica por sí sola qué tejido recibe más carga. Cambiar apoyo, estabilidad o rango puede desplazar el torque, pero la respuesta depende de la persona y de la técnica. El concepto sirve para formular mejores preguntas sobre una variante, no para prometer aislamiento absoluto.",
    "estres-metabolico" to "La congestión puede ser una señal entretenida, pero no sustituye una serie bien ejecutada. Los descansos deben permitir repetir el objetivo de la tarea; acortarlos hasta perder rango convierte el estrés en una limitación. Observa la calidad de las repeticiones junto con la sensación local.",
    "rom" to "Para comparar semanas, registra una referencia sencilla: profundidad, posición de la articulación o punto de contacto. Si el recorrido cambia al subir la carga, la progresión no es equivalente. El mejor rango es el que puedes controlar y repetir, ampliándolo gradualmente cuando la capacidad y la tolerancia lo permiten.",
    "perfil-resistencia" to "Dos ejercicios pueden compartir un patrón y cargarlo en momentos diferentes. Combinar perfiles permite practicar una zona fuerte y otra que normalmente queda descargada, pero la selección debe confirmarse observando la trayectoria real y no solo la etiqueta del equipo. La anatomía individual puede cambiar el punto difícil.",
    "fase-excentrica" to "Una bajada controlada no significa moverse artificialmente lento durante toda la sesión. La velocidad debe permitir reconocer la posición y absorber la carga; si una pausa o una excéntrica larga reduce demasiado el peso y el volumen, puede no ser la mejor herramienta para el objetivo actual.",
    "fase-concentrica" to "La intención de acelerar y la velocidad visible no son iguales: una carga pesada puede moverse despacio aunque la intención sea máxima. En la práctica, completa la fase sin perder la posición y usa la velocidad como señal de esfuerzo o potencia cuando la tarea tenga una métrica adecuada.",
    "isometria" to "Una posición sostenida también puede ser específica para el ángulo y la dirección de la fuerza. Las isometrías encajan cuando quieres practicar control o mantener una capacidad sin desplazamiento, pero deben progresar con tiempo, tensión o dificultad y no presentarse como sustituto universal del recorrido dinámico.",
    "pliometria" to "La calidad manda: cuando los aterrizajes pierden coordinación, la tarea ya no practica potencia con la misma intención. Empieza con contactos que puedas absorber y aumenta complejidad, altura o velocidad de forma separada. La recuperación entre intentos es parte del estímulo, no un descanso opcional.",
    "carga-axial" to "La misma carga externa puede generar demandas axiales distintas según la posición y el apoyo. Comparar variantes solo por kilos oculta esa diferencia. Describe dirección, postura y tolerancia antes de decidir; la carga no es buena ni mala sin el contexto de la persona y de la tarea.",
    "pesos-libres" to "Su libertad de trayectoria puede ser una ventaja para ajustar el movimiento, pero también puede hacer que la estabilidad limite antes al músculo que quieres trabajar. Una máquina o una polea no es una versión inferior: es otra forma de repartir la atención, la fuerza y el margen técnico.",
    "maquinas" to "El ajuste importa tanto como la etiqueta de la máquina. Una trayectoria guiada que no coincide con tus proporciones puede sentirse incómoda, mientras que otra permite repetir el rango con menos distracciones. Prueba la posición, observa la respuesta articular y progresa solo cuando puedas mantener la línea de movimiento.",
    "poleas" to "La polea cambia el ángulo de la resistencia sin eliminar la necesidad de estabilizarse. La altura, la distancia y el recorrido del cable modifican el brazo de momento, por lo que dos configuraciones con el mismo peso pueden sentirse muy distintas. Registra la configuración si quieres comparar resultados.",
    "fuerza" to "La fuerza que puedes expresar depende de la tarea, la posición, la velocidad y la práctica específica. Aumentar el número de la barra no siempre significa que todo el sistema sea más fuerte; puede ser una mejora técnica o una adaptación a esa variante. Define la prueba antes de interpretar el cambio.",
    "explosividad" to "La explosividad combina fuerza y velocidad, pero no se reduce a moverse deprisa con cualquier carga. Una acción puede ser rápida y producir poca fuerza o lenta por una carga alta. Elige una tarea donde la intención y la métrica coincidan, y detén la serie cuando la potencia se degrade.",
    "elasticidad" to "La energía que vuelve al movimiento depende de la rigidez y del tiempo, pero también de la coordinación y de cómo se aplica la fuerza. Mejorar el uso del ciclo de estiramiento-acortamiento no equivale a ganar todo el rango articular. Separa esas capacidades al planificar y evaluar.",
    "resistencia-muscular" to "Para medirla, fija la tarea: misma carga, mismo rango, mismo ritmo o una prueba relativa. De lo contrario, más repeticiones pueden reflejar una mejora de fuerza, una pausa diferente o una técnica más eficiente. Entrena la duración que necesitas y deja que el descanso acompañe esa intención.",
    "sobrecarga-progresiva" to "La progresión debe ser suficientemente pequeña para conservar el movimiento y suficientemente clara para generar una nueva demanda. Puedes alternar repeticiones, carga y series, pero cambia una variable cada vez que necesites saber qué funcionó. Si la técnica se deteriora, vuelve a la última dosis que podías repetir.",
    "deload" to "La mejor descarga es la mínima reducción que recupera calidad y ganas de entrenar. Anota qué bajaste y cómo respondió tu rendimiento; esa información convierte el descanso en aprendizaje para el siguiente bloque. Si aparecen dolor intenso, síntomas persistentes o una caída marcada, no lo reduzcas todo a fatiga de entrenamiento.",
    "especificidad" to "La especificidad guía el equilibrio entre construir capacidades y expresarlas. Una variante puede aumentar músculo o fuerza general sin parecerse a la prueba, mientras que otra practica la coordinación exacta. Alternarlas por fases evita tanto entrenar solo el gesto final como perder la transferencia que realmente buscas.",
)

private fun enrichTrainingConceptDescription(id: String, description: String): String =
    TRAINING_CONCEPT_EDITORIAL_APPENDICES[id]
        ?.let { "$description\n\n$it" }
        ?: description

/**
 * Training Concepts Database — "Conceptos Clave" del entrenamiento.
 *
 * The rich educational body is used by Conceptos Clave. The compact
 * [shortDescription] remains available for legacy compact surfaces and is
 * deliberately separate from canonical exercise muscle/joint/pattern intros.
 */
data class TrainingConcept(
    val id: String,
    val name: String,
    val category: ConceptCategory,
    var description: String,
    val shortDescription: String = TRAINING_CONCEPT_SHORT_DESCRIPTIONS[id].orEmpty(),
) {
    init {
        description = enrichTrainingConceptDescription(id, description)
    }
}

enum class ConceptCategory(
    val label: String,
    val color: Color,
    val icon: String,
) {
    LOAD_MANAGEMENT("Gestión de Carga", Color(0xFFE53935), "barbell"),
    INTENSITY("Intensidad y Esfuerzo", Color(0xFFFF8F00), "flame"),
    FATIGUE("Fatiga y Recuperación", Color(0xFF9C27B0), "recovery"),
    HYPERTROPHY("Mecanismos de Hipertrofia", Color(0xFF1E88E5), "muscle"),
    MOVEMENT("Mecánica del Movimiento", Color(0xFF43A047), "movement"),
    EQUIPMENT("Equipamiento y Medios", Color(0xFF00897B), "equipment"),
    QUALITIES("Cualidades Físicas", Color(0xFF5C6BC0), "qualities"),
    PERIODIZATION("Periodización", Color(0xFF795548), "calendar"),
}

val TRAINING_CONCEPTS_DATABASE: List<TrainingConcept> = listOf(
    TrainingConcept(
        id = "volumen-entrenamiento",
        name = "Volumen de Entrenamiento",
        category = ConceptCategory.LOAD_MANAGEMENT,
        description = """
            El volumen es la cantidad acumulada de trabajo que realizas. Puede describirse con series, repeticiones, carga externa, tiempo bajo tensión o una combinación de esas medidas. No existe una única fórmula válida para todos los objetivos: contar toneladas puede ser útil en algunos levantamientos, mientras que las series exigentes por grupo muscular suelen ser más interpretables para hipertrofia.

            El volumen importa porque determina buena parte del estímulo y también de la recuperación necesaria. Más trabajo no equivale automáticamente a más adaptación; llega un punto en que las series adicionales aportan poco y consumen recursos que podrían mejorar la técnica o el rendimiento. Conviene observar la tendencia de varias semanas, distinguir series realmente estimulantes de calentamientos y ajustar según rendimiento, sueño, molestias y evolución. El volumen adecuado es el que puedes repetir con calidad y progresar sin que la fatiga desborde tu capacidad de recuperación.
        """.trimIndent(),
    ),
    TrainingConcept(
        id = "intensidad",
        name = "Intensidad",
        category = ConceptCategory.LOAD_MANAGEMENT,
        description = """
            En entrenamiento de fuerza, intensidad describe lo demandante que es una carga respecto de tu capacidad actual. A menudo se expresa como porcentaje de una repetición máxima, pero también puede referirse a la dificultad relativa de una serie, a la velocidad de ejecución o a la proximidad del fallo. Por eso una misma cifra de kilos no representa la misma intensidad para dos personas ni para el mismo atleta en días distintos.

            La intensidad orienta qué adaptaciones se practican. Cargas altas exigen producir mucha fuerza y suelen ser específicas para mejorar levantamientos pesados; cargas moderadas o ligeras también pueden estimular hipertrofia si se acercan lo suficiente al límite, aunque con distinta sensación y coste técnico. No debe confundirse intensidad con volumen ni con esfuerzo percibido. Interpretarla junto con repeticiones, RIR, técnica y velocidad permite elegir una carga desafiante, pero suficientemente estable para repetir el estímulo.
        """.trimIndent(),
    ),
    TrainingConcept(
        id = "frecuencia",
        name = "Frecuencia de Entrenamiento",
        category = ConceptCategory.LOAD_MANAGEMENT,
        description = """
            La frecuencia es cuántas veces entrenas una cualidad, un movimiento o un grupo muscular dentro de un periodo, normalmente una semana. No es solo el número de visitas al gimnasio: una sesión cuenta para un músculo cuando recibe un estímulo relevante. Distribuir el trabajo en más días puede cambiar la calidad de las series, la práctica técnica y la fatiga de cada sesión.

            La frecuencia no tiene un valor mágico separado del volumen total. Cuando el volumen semanal se iguala, distintas distribuciones pueden producir resultados parecidos, así que la mejor opción depende del tiempo disponible, la recuperación y la tolerancia a sesiones largas. Aumentarla puede ayudar a repartir muchas series, practicar una habilidad con más regularidad o evitar que el rendimiento caiga al final de una sesión. Reducirla puede ser razonable cuando el descanso, el trabajo o la vida cotidiana limitan la recuperación.
        """.trimIndent(),
    ),
    TrainingConcept(
        id = "rir",
        name = "RIR (Repeticiones en Reserva)",
        category = ConceptCategory.INTENSITY,
        description = """
            RIR significa repeticiones en reserva: una estimación de cuántas repeticiones técnicamente válidas podrías haber completado al terminar una serie. RIR 0 representa el fallo concéntrico de esa tarea; RIR 2 indica que probablemente quedaban dos repeticiones. Es una predicción, no una medición directa, y su precisión cambia con el ejercicio, la experiencia, la velocidad y el estado de fatiga.

            Usar RIR ayuda a autorregular la carga sin depender de un porcentaje fijo que puede quedar desfasado. Permite mantener un esfuerzo exigente mientras se conserva margen para la técnica, y hace visible cuándo una sesión está saliendo más fácil o más difícil de lo esperado. No conviene tratarlo como una verdad exacta ni compararlo entre ejercicios muy distintos. La interpretación mejora si registras repeticiones, carga, velocidad y sensación, y si ocasionalmente contrastas la estimación con series cercanas al límite de forma segura.
        """.trimIndent(),
    ),
    TrainingConcept(
        id = "rpe",
        name = "RPE (Percepción del Esfuerzo)",
        category = ConceptCategory.INTENSITY,
        description = """
            RPE es una escala subjetiva que resume cuánto esfuerzo percibiste en una serie, sesión o tarea. En fuerza suele usarse una escala de 1 a 10, donde los valores altos indican que queda poco margen; en ese contexto puede relacionarse con RIR, pero no son sinónimos perfectos. RPE integra señales como respiración, tensión, velocidad, concentración y sensación de dificultad.

            Su valor está en capturar la realidad del día. La misma carga puede sentirse distinta por sueño insuficiente, estrés, dolor o acumulación de trabajo, y una escala fija no conoce esas variaciones. Aun así, el RPE es vulnerable a expectativas y experiencia: una persona puede sobreestimarlo o subestimarlo, especialmente lejos del fallo. Úsalo para complementar datos objetivos, no para reemplazarlos. Registrar el RPE junto con carga, repeticiones y técnica permite decidir si conviene mantener, reducir o aumentar el estímulo.
        """.trimIndent(),
    ),
    TrainingConcept(
        id = "fallo-muscular",
        name = "Fallo Muscular",
        category = ConceptCategory.INTENSITY,
        description = """
            El fallo muscular es el punto en que ya no puedes completar otra repetición con la acción concéntrica y la técnica definidas. Es específico del ejercicio y de la ejecución: detenerte porque la postura se degrada no es exactamente lo mismo que agotar la capacidad de producir la fase prevista. También conviene diferenciar el fallo momentáneo de una serie simplemente muy difícil o lenta.

            Llegar al fallo puede aumentar la sensación de esfuerzo y confirmar que una carga ligera está cerca del límite, pero no es requisito permanente para progresar. La evidencia disponible sugiere que entrenar siempre hasta ese punto puede elevar fatiga, daño y malestar sin mejorar proporcionalmente la hipertrofia. En ejercicios complejos o técnicamente sensibles, dejar margen suele proteger la calidad y facilitar la recuperación. La decisión depende del objetivo, el ejercicio, el momento del programa y la capacidad de controlar la repetición final.
        """.trimIndent(),
    ),
    TrainingConcept(
        id = "fatiga-sistemica",
        name = "Fatiga Sistémica",
        category = ConceptCategory.FATIGUE,
        description = """
            La fatiga sistémica es la reducción temporal de la capacidad de rendir que afecta a varios sistemas del organismo, no solo al músculo que trabajaste. Puede incluir menor activación neural, cambios en la coordinación, sensación de agotamiento, alteraciones del sueño o una menor disposición para entrenar. No es una sustancia única ni puede deducirse con certeza a partir de un solo síntoma.

            Se acumula cuando la demanda del entrenamiento y de la vida supera repetidamente la recuperación disponible. Sesiones muy voluminosas, cargas altas, poco sueño, estrés y déficit energético pueden interactuar, pero la relación no es lineal. La señal práctica es un patrón persistente: rendimiento que cae, técnica que se deteriora y esfuerzo que aumenta con cargas habituales. Reducir temporalmente la demanda, mejorar el descanso y revisar la distribución semanal suele ser más útil que perseguir una cifra abstracta de fatiga.
        """.trimIndent(),
    ),
    TrainingConcept(
        id = "dano-muscular",
        name = "Daño Muscular",
        category = ConceptCategory.FATIGUE,
        description = """
            El daño muscular describe alteraciones estructurales y funcionales que pueden aparecer después de un esfuerzo no habitual o muy exigente. Se manifiesta junto con dolor muscular de aparición tardía, pérdida temporal de fuerza, inflamación y cambios en la sensibilidad; ninguna de esas señales, por sí sola, mide con precisión cuánto tejido se ha dañado. El cuerpo repara y remodela esas estructuras como parte de la respuesta al entrenamiento.

            Sentir más agujetas no significa haber creado más crecimiento. El daño puede acompañar una sesión efectiva, pero también interferir con el rendimiento posterior y alargar la recuperación. La adaptación repetida suele reducir la respuesta dañina a un mismo estímulo, mientras que ejercicios nuevos, grandes estiramientos bajo carga o aumentos bruscos la elevan. Conviene progresar gradualmente, observar la función y no usar el dolor como objetivo. Si el dolor es intenso, focal o persiste, deja de ser una simple señal de entrenamiento y merece evaluación.
        """.trimIndent(),
    ),
    TrainingConcept(
        id = "tension-mecanica",
        name = "Tensión Mecánica (y Torque Articular)",
        category = ConceptCategory.HYPERTROPHY,
        description = """
            La tensión mecánica es la fuerza que soportan las fibras musculares cuando intentan producir o resistir movimiento. En una articulación, esa demanda se relaciona con el torque: la fuerza externa multiplicada por su brazo de momento respecto del eje articular. Cambiar la carga, el ángulo, la trayectoria o la posición del segmento puede modificar cuánto esfuerzo debe generar un músculo aunque el peso no cambie.

            La tensión es una señal central para la adaptación, pero no es una propiedad aislada del número escrito en la mancuerna. Importan la longitud muscular, el rango recorrido, la estabilidad, la coordinación y la cercanía al límite. Dos variantes con el mismo peso pueden distribuir el torque de manera distinta y producir sensaciones diferentes. Interpretar este concepto ayuda a elegir ejercicios que permitan aplicar fuerza con control y progresar, sin convertir una explicación biomecánica en una promesa de que un ángulo específico garantiza crecimiento.
        """.trimIndent(),
    ),
    TrainingConcept(
        id = "estres-metabolico",
        name = "Estrés Metabólico",
        category = ConceptCategory.HYPERTROPHY,
        description = """
            El estrés metabólico es el conjunto de cambios locales que aparecen cuando una serie sostenida aumenta la demanda energética: se acumulan metabolitos, cambia el pH, aumenta la perfusión y aparece la sensación de congestión o ardor. Suele ser más evidente con repeticiones altas, descansos cortos o tensión mantenida, aunque la sensación no permite cuantificarlo de forma exacta.

            Puede contribuir al estímulo de una serie, pero no debe presentarse como una causa independiente y garantizada de hipertrofia. La tensión que pueden producir las fibras, el volumen recuperable y la progresión siguen siendo piezas fundamentales. Buscar únicamente el ardor puede llevar a reducir la carga, acortar demasiado los descansos o deteriorar la técnica sin mejorar el resultado. Úsalo como una descripción de la respuesta del ejercicio, no como un marcador de calidad. Una serie puede ser productiva con poca congestión y una serie muy congestionada puede estar mal dosificada.
        """.trimIndent(),
    ),
    TrainingConcept(
        id = "rom",
        name = "ROM (Rango de Movimiento)",
        category = ConceptCategory.MOVEMENT,
        description = """
            El ROM es la amplitud articular que recorres durante una repetición, desde la posición inicial hasta la final. Puede expresarse en grados, distancia o referencias técnicas como profundidad y extensión. No existe un ROM idéntico para todas las personas: la anatomía, el control, la movilidad, el ejercicio y el objetivo cambian qué recorrido es seguro y útil. Un recorrido amplio solo cuenta si se mantiene la posición y la intención de la tarea.

            En general, entrenar con un ROM completo o con músculos trabajando a longitudes largas favorece muchas adaptaciones, especialmente en la parte inferior del cuerpo, pero la evidencia no justifica convertirlo en una regla rígida. Un ROM parcial puede ser deliberado para practicar una zona concreta, superar un punto débil o acomodar una limitación. Lo importante es distinguir una adaptación planificada de acortar el recorrido para escapar de la dificultad. Registra el ROM de forma consistente para que la progresión compare repeticiones realmente equivalentes.
        """.trimIndent(),
    ),
    TrainingConcept(
        id = "perfil-resistencia",
        name = "Perfil de Resistencia y Curvas de Fuerza",
        category = ConceptCategory.MOVEMENT,
        description = """
            El perfil de resistencia describe cómo cambia la dificultad externa a lo largo del recorrido. La gravedad, una leva, una polea, una banda o la posición de la carga determinan el brazo de momento y, por tanto, el torque que una articulación debe producir en cada ángulo. La curva de fuerza describe cómo varía la capacidad del músculo para ejercer fuerza en esas mismas posiciones; ambas curvas no siempre coinciden.

            Esta relación explica por qué un ejercicio puede sentirse pesado al inicio, en el punto medio o cerca del final, y por qué dos variantes con el mismo peso no son equivalentes. Analizarla ayuda a combinar movimientos que carguen distintas zonas y a elegir una resistencia que no obligue a compensar en el tramo más débil. No permite predecir con exactitud qué músculo crecerá ni reemplaza observar la técnica. La curva real depende de la antropometría, el equipamiento y la ejecución de cada persona.
        """.trimIndent(),
    ),
    TrainingConcept(
        id = "fase-excentrica",
        name = "Fase Excéntrica",
        category = ConceptCategory.MOVEMENT,
        description = """
            La fase excéntrica ocurre cuando un músculo produce fuerza mientras se alarga, como al bajar una sentadilla o frenar una carga. El tejido puede resistir fuerzas elevadas con un coste energético relativamente bajo, pero eso no significa que cualquier descenso lento sea automáticamente superior. La velocidad, el rango, la carga y la capacidad de controlar la posición determinan qué estímulo recibe la persona.

            Una excéntrica controlada mejora la percepción del recorrido, permite mantener tensión y ayuda a aprender dónde empieza la compensación. También puede generar más daño y agujetas cuando se introduce de forma brusca, sobre todo en posiciones largas o con cargas nuevas. La literatura no demuestra que separar la fase excéntrica de la concéntrica garantice más hipertrofia en todos los músculos. Úsala para conservar una repetición reproducible y para practicar la absorción de fuerza, sin convertir el movimiento lento en un fin independiente de la técnica.
        """.trimIndent(),
    ),
    TrainingConcept(
        id = "fase-concentrica",
        name = "Fase Concéntrica",
        category = ConceptCategory.MOVEMENT,
        description = """
            La fase concéntrica es la parte de la repetición en la que el músculo se acorta mientras supera la resistencia, como al levantarse de una sentadilla o empujar una carga. La velocidad observada depende de la fuerza disponible, la carga, la posición y la intención. Una repetición lenta no siempre significa que se haya elegido una velocidad lenta: puede ser el resultado de acercarse al límite.

            La intención de mover con decisión puede mejorar la coordinación y la producción de fuerza, incluso cuando una carga pesada se desplaza despacio. En cambio, acelerar sin control puede cambiar la trayectoria y trasladar la demanda a otras estructuras. Para hipertrofia, la fase concéntrica debe completar el recorrido con una técnica que puedas repetir; para fuerza o potencia, además interesa que la tarea y la velocidad se parezcan al objetivo. La calidad de la acción importa más que perseguir una cifra de velocidad aislada.
        """.trimIndent(),
    ),
    TrainingConcept(
        id = "isometria",
        name = "Isometría",
        category = ConceptCategory.MOVEMENT,
        description = """
            Una contracción isométrica produce fuerza sin un cambio visible de longitud del complejo músculo-tendón ni movimiento articular apreciable. Puede consistir en sostener una posición, empujar contra un objeto inmóvil o detener una carga en un punto concreto. Aunque la articulación parezca quieta, existe esfuerzo interno y la tensión puede variar según el ángulo y la intención.

            Las isometrías son útiles para practicar control, tolerar posiciones, mantener fuerza cuando el movimiento está limitado y trabajar cerca de un ángulo específico. Sus adaptaciones son relativamente específicas al ángulo entrenado y se transfieren de manera desigual a otros recorridos. No sustituyen automáticamente al trabajo dinámico si el objetivo exige mover una carga. La dosis debe considerar cuánto tiempo se sostiene, cuánta fuerza se intenta producir, el descanso y la respuesta articular. El temblor o el ardor indican demanda, no garantizan una adaptación concreta.
        """.trimIndent(),
    ),
    TrainingConcept(
        id = "pliometria",
        name = "Pliometría",
        category = ConceptCategory.MOVEMENT,
        description = """
            La pliometría utiliza acciones rápidas en las que una fase de estiramiento precede a un acortamiento explosivo. Saltos, rebotes y lanzamientos aprovechan la interacción entre músculo, tendón, coordinación y sistema nervioso para producir fuerza en poco tiempo. La calidad de los contactos, la intención y la capacidad de aterrizar importan tanto como la altura o la distancia alcanzada.

            Es una herramienta para desarrollar potencia, rigidez funcional y coordinación, no una categoría que deba añadirse a cualquier sesión sin preparación. El impacto y la velocidad aumentan la exigencia sobre tendones y articulaciones; por eso conviene progresar desde tareas simples hacia contactos más intensos, con volumen bajo y descansos suficientes. Una sesión pliométrica pierde su propósito cuando la fatiga convierte cada salto en una repetición lenta y desorganizada. La selección debe respetar la experiencia, el suelo, el calzado y la tolerancia individual.
        """.trimIndent(),
    ),
    TrainingConcept(
        id = "carga-axial",
        name = "Carga Axial",
        category = ConceptCategory.MOVEMENT,
        description = """
            La carga axial es una fuerza aplicada aproximadamente a lo largo del eje longitudinal de un segmento o del cuerpo. En una sentadilla, una barra sobre los hombros genera compresión que debe gestionar la columna y el resto de la cadena; en otros ejercicios, la dirección y la postura redistribuyen parte de esa demanda. No es una etiqueta de “buena” o “mala” por sí misma: describe una condición mecánica.

            El organismo se adapta a cargas progresivas, pero la respuesta depende de magnitud, velocidad, técnica, anatomía, historial y recuperación. Comprender la carga axial ayuda a comparar variantes y a decidir cuándo una persona necesita más apoyo, menos fatiga o una trayectoria distinta. No permite diagnosticar riesgo individual ni convertir un ejercicio en peligroso solo por tener compresión. La ejecución estable, la progresión gradual y la ausencia de dolor relevante son más informativas que una clasificación aislada del movimiento.
        """.trimIndent(),
    ),
    TrainingConcept(
        id = "pesos-libres",
        name = "Pesos Libres",
        category = ConceptCategory.EQUIPMENT,
        description = """
            Los pesos libres incluyen barras, mancuernas, kettlebells y otras resistencias que no obligan a seguir una trayectoria fija. La persona debe producir fuerza y coordinar el peso en varias direcciones, por lo que la estabilidad y la habilidad técnica pueden formar parte importante de la tarea. Esa libertad permite ajustar el recorrido, trabajar unilateralmente y transferir la práctica a patrones específicos.

            No son intrínsecamente superiores a las máquinas para ganar músculo. La comparación directa muestra adaptaciones de hipertrofia similares cuando el entrenamiento está bien dosificado, mientras que las mejoras de fuerza tienden a ser específicas del implemento y del test. Elegir pesos libres tiene sentido si necesitas práctica coordinativa, amplitud ajustable o una progresión disponible; elegir otra herramienta puede ser mejor si la estabilidad limita el músculo objetivo. El criterio es la calidad del estímulo, no la apariencia del equipo.
        """.trimIndent(),
    ),
    TrainingConcept(
        id = "maquinas",
        name = "Máquinas de Entrenamiento",
        category = ConceptCategory.EQUIPMENT,
        description = """
            Las máquinas guían parte de la trayectoria y ofrecen apoyos que reducen la demanda de estabilización. Esto puede facilitar que una persona concentre el esfuerzo en una articulación o grupo muscular, repita una posición y entrene cerca del límite con menor exigencia técnica global. La guía, sin embargo, no hace que el movimiento sea automáticamente adecuado: el ajuste debe respetar las proporciones y el rango de cada usuario.

            Las máquinas son una herramienta útil para acumular volumen, trabajar después de ejercicios complejos o mantener una variante cuando el equilibrio y el control son el factor limitante. La fuerza que desarrollas se expresa especialmente en patrones parecidos a la máquina usada, aunque la hipertrofia puede ser comparable a la obtenida con pesos libres. Evalúa comodidad, rango, estabilidad, progresión y respuesta articular. Si una máquina obliga a una trayectoria dolorosa, su seguridad no se arregla con que el diseño sea guiado.
        """.trimIndent(),
    ),
    TrainingConcept(
        id = "poleas",
        name = "Poleas y Cables",
        category = ConceptCategory.EQUIPMENT,
        description = """
            Las poleas transmiten la resistencia mediante un cable y permiten cambiar el ángulo, el agarre y la posición del cuerpo con relativa libertad. La tensión que percibes depende de la relación de poleas, la dirección del cable, la distancia a la torre y la curva mecánica del ejercicio; no siempre es constante ni idéntica en todo el recorrido. La etiqueta “tensión constante” simplifica demasiado el sistema.

            Su versatilidad facilita ajustar la línea de fuerza, entrenar unilateralmente y mantener una resistencia útil en posiciones donde una mancuerna perdería ventaja por la gravedad. También permite progresiones pequeñas y configuraciones que acomodan limitaciones de movilidad. No existe una superioridad universal frente a barras o máquinas: la herramienta debe permitir una trayectoria estable, suficiente rango y una carga progresable. Observa dónde aumenta el torque y si el cuerpo puede sostener esa posición sin compensar antes de decidir que una polea es la mejor opción.
        """.trimIndent(),
    ),
    TrainingConcept(
        id = "fuerza",
        name = "Fuerza y Generación de Torque",
        category = ConceptCategory.QUALITIES,
        description = """
            La fuerza es la capacidad de producir tensión para acelerar, sostener o frenar una resistencia. En un movimiento articular, esa tensión se transforma en torque según la distancia entre la línea de fuerza y el eje de rotación. Por eso ser fuerte en un ejercicio no significa ejercer la misma fuerza en todos los ángulos ni trasladarla sin cambios a otra tarea. La coordinación y la posición son parte de la expresión de fuerza.

            La fuerza máxima, la fuerza rápida y la fuerza repetida son cualidades relacionadas, pero no intercambiables. Las cargas altas y la práctica específica suelen mejorar el rendimiento en pruebas pesadas; el volumen, la técnica y la velocidad de entrenamiento modifican la adaptación. Medirla con un solo levantamiento puede confundir capacidad del músculo con habilidad del patrón. Interpreta los avances junto con rango, control, velocidad y consistencia, y evita usar una cifra aislada para definir todo el estado físico de una persona.
        """.trimIndent(),
    ),
    TrainingConcept(
        id = "explosividad",
        name = "Explosividad / Potencia",
        category = ConceptCategory.QUALITIES,
        description = """
            La potencia es la velocidad con la que se realiza trabajo; en términos simples, combina fuerza y velocidad. La explosividad describe la capacidad de aplicar mucha fuerza en poco tiempo, aunque en el lenguaje cotidiano ambas palabras suelen mezclarse. Un atleta puede ser muy fuerte y no expresar esa fuerza rápidamente, o mover una carga ligera con rapidez sin tener una gran fuerza máxima.

            Mejorarla requiere practicar acciones rápidas con una resistencia que permita conservar intención y técnica: saltos, lanzamientos, levantamientos derivados o repeticiones dinámicas son ejemplos distintos, no intercambiables. El objetivo no es completar más repeticiones cuando la velocidad ya cayó, sino producir acciones de calidad y descansar lo suficiente. La transferencia depende de la similitud entre el entrenamiento y la tarea que quieres mejorar. La potencia debe evaluarse con una métrica adecuada al movimiento, porque la sensación de rapidez no siempre refleja la producción real.
        """.trimIndent(),
    ),
    TrainingConcept(
        id = "elasticidad",
        name = "Elasticidad",
        category = ConceptCategory.QUALITIES,
        description = """
            La elasticidad es la capacidad de un tejido o sistema para deformarse ante una carga y recuperar parte de su forma, almacenando y devolviendo energía. En el movimiento humano participan músculos, tendones, fascias y estructuras articulares; no son resortes independientes ni responden igual. Un tendón más rígido puede transmitir fuerza con menos deformación, mientras que la flexibilidad describe principalmente el rango disponible, no la cantidad de energía devuelta.

            La contribución elástica depende del tiempo de contacto, la amplitud, la velocidad, la rigidez de la superficie y la coordinación. Entrenar saltos o acciones reactivas puede mejorar cómo se usa el ciclo de estiramiento-acortamiento, pero no convierte automáticamente a una persona en más rápida. Las adaptaciones tendinosas son graduales y específicas de la carga. Conviene separar movilidad, tolerancia al estiramiento y retorno de energía al hablar del concepto, porque medir una de esas propiedades no permite inferir las otras.
        """.trimIndent(),
    ),
    TrainingConcept(
        id = "resistencia-muscular",
        name = "Resistencia Muscular",
        category = ConceptCategory.QUALITIES,
        description = """
            La resistencia muscular es la capacidad de mantener o repetir una producción de fuerza durante un tiempo o número de repeticiones determinado. Puede ser local, como sostener una contracción de un músculo, o depender de varios grupos y del sistema cardiorrespiratorio. También hay que distinguir resistencia absoluta, donde se repite una carga fija, de resistencia relativa, donde la carga se relaciona con la fuerza máxima actual.

            Mejorarla no significa simplemente usar siempre muchas repeticiones. La respuesta depende de la carga, el descanso, el ritmo, la tarea y la especificidad de la prueba. Una persona puede aumentar las repeticiones con un peso fijo porque ganó fuerza, sin que eso implique que mejoró en una prueba relativa. El entrenamiento de resistencia puede elevar la tolerancia al esfuerzo y la capacidad funcional, pero debe dosificarse para no convertir todas las sesiones en trabajo fatigante. Define primero qué duración y qué tarea quieres sostener.
        """.trimIndent(),
    ),
    TrainingConcept(
        id = "sobrecarga-progresiva",
        name = "Sobrecarga Progresiva",
        category = ConceptCategory.PERIODIZATION,
        description = """
            La sobrecarga progresiva es aumentar de forma planificada el desafío que recibe una persona cuando el estímulo anterior ya se ha adaptado. La progresión puede ocurrir con más carga, repeticiones, series, rango, frecuencia, velocidad o una ejecución más estable; no obliga a subir kilos en cada sesión. Para que exista sobrecarga, el cambio debe ser suficientemente relevante y mantenerse dentro de la capacidad de recuperación.

            El principio funciona porque el cuerpo responde al contexto completo, no a una cifra aislada. Si aumentas el peso y pierdes recorrido o control, quizá no hayas aumentado el estímulo útil. Si subes volumen mientras el rendimiento cae durante semanas, la progresión dejó de ser sostenible. Registra la variante, la técnica y la percepción del esfuerzo para comparar exposiciones equivalentes. Una progresión inteligente alterna fases de avance, mantenimiento y descarga, y acepta que el ritmo cambia con la experiencia, el sueño, la nutrición y el objetivo.
        """.trimIndent(),
    ),
    TrainingConcept(
        id = "deload",
        name = "Deload (Semana de Descarga)",
        category = ConceptCategory.PERIODIZATION,
        description = """
            Un deload es una reducción planificada y temporal de la demanda para disipar fatiga sin abandonar la práctica. Puede lograrse con menos series, cargas más ligeras, más RIR, menos ejercicios o una combinación; no existe una receta universal ni tiene que durar exactamente una semana. La intención es conservar patrones y calidad mientras baja el coste de recuperación.

            La descarga resulta más útil cuando responde a una tendencia, no a un calendario automático. Rendimiento estancado, técnica inestable, sueño alterado, molestias crecientes o esfuerzo inusualmente alto pueden indicar que conviene reducir. Si todo va bien, descargar por costumbre quizá solo quite oportunidades de entrenar. Tampoco debe convertirse en reposo absoluto cuando el movimiento ligero ayuda. Al volver, retoma una dosis que puedas repetir y evalúa si la reducción cambió realmente la capacidad de rendir; esa información mejora la siguiente planificación.
        """.trimIndent(),
    ),
    TrainingConcept(
        id = "especificidad",
        name = "Especificidad",
        category = ConceptCategory.PERIODIZATION,
        description = """
            La especificidad significa que las adaptaciones se parecen a las demandas que practicas. La fuerza mejora especialmente en el patrón, rango, velocidad, tipo de contracción y herramienta que entrenas; la resistencia también depende de la duración y del ritmo de la tarea. Ganar capacidad general puede ayudar, pero la transferencia se reduce cuando cambia demasiado el contexto de la prueba o del deporte.

            Este principio no exige copiar exactamente el gesto objetivo en cada sesión. El entrenamiento general puede construir masa, fuerza y tolerancia, mientras que el trabajo específico enseña a expresarlas donde importan. El equilibrio cambia según la fase: aprender una técnica, aumentar músculo o preparar una competición no requieren la misma mezcla. Confundir especificidad con exclusividad lleva a practicar siempre el gesto final y acumular fatiga innecesaria. Define primero qué resultado quieres transferir y selecciona variantes que compartan las demandas relevantes, no solo el aspecto visual.
        """.trimIndent(),
    ),
)

/** Get all unique categories present in the database. */
fun getConceptCategories(): List<ConceptCategory> =
    TRAINING_CONCEPTS_DATABASE.map { it.category }.distinct().sortedBy { it.ordinal }

/** Compatibility search over the current identity and educational body. */
fun searchConcepts(query: String): List<TrainingConcept> {
    if (query.isBlank()) return TRAINING_CONCEPTS_DATABASE
    val q = query.trim().lowercase()
    return TRAINING_CONCEPTS_DATABASE.filter { concept ->
        concept.name.lowercase().contains(q) ||
            concept.description.lowercase().contains(q) ||
            concept.category.label.lowercase().contains(q)
    }
}
