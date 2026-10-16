package com.example.kpkn.data.models

/**
 * Cierre común de toda ficha de molestia. Va SIEMPRE al final y no se repite dentro de cada texto.
 */
const val PROFESSIONAL_ADVICE: String =
    "Si el dolor es fuerte o no mejora en una semana, consulta a un profesional."

/** Límites de contenido que comprueba `DiscomfortKnowledgeTest` (la interfaz cuenta con ellos). */
const val DISCOMFORT_EXPLANATION_MAX_CHARS: Int = 220
const val DISCOMFORT_CAUSE_MAX_CHARS: Int = 70
const val DISCOMFORT_ADVICE_MAX_CHARS: Int = 220

/**
 * Conocimiento de una molestia del [DISCOMFORT_CATALOG] (todas menos `"none"`).
 *
 * Reglas del contenido: español neutro sin género gramatical, sin diagnosticar ni nombrar lesiones como
 * certeza ("suele relacionarse con", "puede venir de"), sin medicamentos. Es contenido de entrenamiento, no
 * consejo médico; el cierre es [PROFESSIONAL_ADVICE].
 *
 * @property zoneLabel zona en mayúsculas para la etiqueta pequeña de la ficha («RODILLA»).
 * @property zonePhrase zona con artículo para frases («la rodilla»), p. ej. «Si la rodilla avisa…».
 * @property animation sección de la zona que elige la escena animada de la ficha.
 * @property searchAliases sinónimos y formas coloquiales, sin tildes ni mayúsculas.
 */
data class DiscomfortKnowledge(
    val id: String,
    val zoneLabel: String,
    val zonePhrase: String,
    val title: String,
    val explanation: String,
    val commonCauses: List<String>,
    val whatYouCanDo: String,
    val animation: DiscomfortSection,
    val searchAliases: List<String>,
)

val DISCOMFORT_KNOWLEDGE: List<DiscomfortKnowledge> = listOf(
    DiscomfortKnowledge(
        id = "shoulder_anterior",
        zoneLabel = "HOMBRO",
        zonePhrase = "el hombro",
        title = "Molestia en la parte delantera del hombro",
        explanation = "Suele notarse al frente del hombro al empujar o al elevar los brazos. " +
            "Se relaciona con el tendón del bíceps y con los músculos que suben el brazo hacia delante.",
        commonCauses = listOf(
            "Subir rápido el volumen de press y elevaciones frontales.",
            "Bajar la barra muy abierto o muy profundo en el press.",
            "Hombros encogidos o adelantados al empujar.",
            "Poco descanso entre sesiones de empuje.",
        ),
        whatYouCanDo = "Baja un 10 % la carga de los empujes y cambia el press con barra por mancuernas " +
            "con agarre neutro unos días. Acorta el rango si molesta al final del recorrido.",
        animation = DiscomfortSection.SHOULDERS_ARMS,
        searchAliases = listOf(
            "hombro", "hombro delantero", "hombro frontal", "hombro adelante", "frente del hombro",
            "deltoides anterior", "hombro press",
        ),
    ),
    DiscomfortKnowledge(
        id = "shoulder_posterior",
        zoneLabel = "HOMBRO",
        zonePhrase = "el hombro",
        title = "Molestia en la parte de atrás del hombro",
        explanation = "Suele notarse detrás del hombro al tirar, remar o abrir los brazos. " +
            "Se relaciona con el deltoides posterior y con los músculos pequeños que estabilizan la articulación.",
        commonCauses = listOf(
            "Muchas series de remo y pájaros sin pausa entre ellas.",
            "Tirar con los hombros encogidos hacia las orejas.",
            "Subir la carga de los jalones más rápido que la técnica.",
            "Poca movilidad de la parte alta de la espalda.",
        ),
        whatYouCanDo = "Baja un 10 % la carga de remos y jalones, haz una pausa de un segundo al final del " +
            "tirón y deja los hombros abajo. Añade movilidad de la parte alta de la espalda.",
        animation = DiscomfortSection.SHOULDERS_ARMS,
        searchAliases = listOf(
            "hombro", "hombro posterior", "hombro trasero", "hombro atras", "hombro de atras",
            "deltoides posterior", "hombro remo",
        ),
    ),
    DiscomfortKnowledge(
        id = "elbow_medial",
        zoneLabel = "CODO",
        zonePhrase = "el codo",
        title = "Molestia en la parte interna del codo",
        explanation = "Suele notarse en el lado del codo más cercano al cuerpo, sobre todo al agarrar o al hacer " +
            "curl. Se relaciona con los músculos del antebrazo que flexionan la muñeca.",
        commonCauses = listOf(
            "Agarres fuertes y muchas series de curl o de remo.",
            "Muñeca doblada al levantar peso.",
            "Subir el volumen de brazo muy rápido.",
            "Poco descanso entre sesiones de tirón.",
        ),
        whatYouCanDo = "Baja la carga un 10 %, usa un agarre más neutro o correas en los tirones y alarga el " +
            "descanso entre series. Evita estirar con fuerza la zona mientras moleste.",
        animation = DiscomfortSection.SHOULDERS_ARMS,
        searchAliases = listOf(
            "codo", "codo interno", "codo por dentro", "codo de golfista", "codo golfista", "epitroclea",
            "codo curl",
        ),
    ),
    DiscomfortKnowledge(
        id = "elbow_lateral",
        zoneLabel = "CODO",
        zonePhrase = "el codo",
        title = "Molestia en la parte externa del codo",
        explanation = "Suele notarse en el lado externo del codo al agarrar, extender la muñeca o hacer extensiones " +
            "de tríceps. Se relaciona con los músculos del antebrazo que extienden la muñeca.",
        commonCauses = listOf(
            "Agarres anchos y mucho trabajo de antebrazo.",
            "Extensiones de tríceps con la muñeca doblada.",
            "Subir la carga o el volumen de agarre muy rápido.",
            "Poco descanso del antebrazo entre sesiones.",
        ),
        whatYouCanDo = "Baja la carga un 10 %, prueba un agarre neutro o con correas y cambia las extensiones " +
            "por polea con cuerda unos días. Mantén la muñeca recta.",
        animation = DiscomfortSection.SHOULDERS_ARMS,
        searchAliases = listOf(
            "codo", "codo externo", "codo por fuera", "codo de tenista", "codo tenista", "epicondilo",
            "codo extension",
        ),
    ),
    DiscomfortKnowledge(
        id = "wrist_hand",
        zoneLabel = "MUÑECA",
        zonePhrase = "la muñeca",
        title = "Molestia en la muñeca o la mano",
        explanation = "Suele notarse al apoyar el peso, agarrar o doblar la muñeca bajo carga. Se relaciona con " +
            "los tendones del antebrazo y con la posición de la muñeca al empujar.",
        commonCauses = listOf(
            "Empujar con la muñeca doblada hacia atrás.",
            "Agarres finos o barras que giran en la mano.",
            "Subir el peso sin trabajar el agarre.",
            "Mucho apoyo en el suelo en flexiones y planchas.",
        ),
        whatYouCanDo = "Mantén la muñeca recta y alineada con el antebrazo, usa muñequeras o mancuernas con agarre " +
            "neutro y baja la carga un 10 % unos días. Calienta la muñeca antes de empujar.",
        animation = DiscomfortSection.SHOULDERS_ARMS,
        searchAliases = listOf(
            "muneca", "munecas", "mano", "manos", "dedos", "palma", "agarre", "muneca flexion",
        ),
    ),
    DiscomfortKnowledge(
        id = "neck_cervical",
        zoneLabel = "CUELLO",
        zonePhrase = "el cuello",
        title = "Molestia o rigidez en el cuello",
        explanation = "Suele notarse en la nuca o a los lados del cuello, con rigidez al girar la cabeza. Se " +
            "relaciona con la tensión del trapecio y con la posición de la cabeza bajo carga.",
        commonCauses = listOf(
            "Barra apoyada muy alta sobre el cuello en sentadillas.",
            "Encoger los hombros o adelantar la cabeza al esforzarte.",
            "Apretar la mandíbula en las series duras.",
            "Pasar horas con el cuello en mala postura.",
        ),
        whatYouCanDo = "Mantén la cabeza alineada con la columna y la mirada al frente, baja la carga un 10 % en " +
            "los ejercicios con barra sobre los hombros y añade movilidad suave de cuello y trapecio.",
        animation = DiscomfortSection.SPINE_NECK,
        searchAliases = listOf(
            "cuello", "cervical", "cervicales", "nuca", "torticolis", "cuello tenso", "rigidez de cuello",
        ),
    ),
    DiscomfortKnowledge(
        id = "upper_back",
        zoneLabel = "ESPALDA ALTA",
        zonePhrase = "la espalda alta",
        title = "Molestia en la espalda alta",
        explanation = "Suele notarse entre los omóplatos o en la zona alta de la espalda. Se relaciona con el " +
            "trapecio, los romboides y la postura de los hombros durante remos y press.",
        commonCauses = listOf(
            "Remos con el pecho caído o los hombros encogidos.",
            "Muchas horas de silla con la espalda encorvada.",
            "Mucho press de banca sin tirones que lo equilibren.",
            "Carga alta sobre la espalda sin descanso entre series.",
        ),
        whatYouCanDo = "Saca el pecho y baja los hombros antes de cada tirón, equilibra empuje y tirón en la " +
            "semana y baja la carga un 10 % si molesta. Añade movilidad de la zona torácica.",
        animation = DiscomfortSection.SPINE_NECK,
        searchAliases = listOf(
            "espalda alta", "espalda superior", "dorsal alta", "entre omoplatos", "omoplato", "omoplatos",
            "escapulas", "trapecio",
        ),
    ),
    DiscomfortKnowledge(
        id = "lumbar",
        zoneLabel = "ZONA LUMBAR",
        zonePhrase = "la zona lumbar",
        title = "Molestia en la zona lumbar",
        explanation = "Suele notarse en la parte baja de la espalda, sobre todo en bisagras de cadera, sentadillas " +
            "y peso muerto. Se relaciona con los músculos que mantienen la columna estable.",
        commonCauses = listOf(
            "Perder la posición neutra de la columna al bajar la carga.",
            "Subir el peso muerto o la sentadilla muy rápido.",
            "Poco control del abdomen y de los glúteos.",
            "Entrenar con carga tras varias horas de silla.",
        ),
        whatYouCanDo = "Baja la carga un 10 %, reduce la profundidad o cambia a una variante con apoyo, como prensa " +
            "o hip thrust, unos días. Mantén la columna neutra y respira antes de cada repetición.",
        animation = DiscomfortSection.SPINE_NECK,
        searchAliases = listOf(
            "lumbar", "lumbares", "zona lumbar", "lumbago", "espalda baja", "baja espalda",
            "parte baja de la espalda", "cintura", "rinones",
        ),
    ),
    DiscomfortKnowledge(
        id = "hip_front",
        zoneLabel = "CADERA",
        zonePhrase = "la cadera",
        title = "Molestia en la parte delantera de la cadera",
        explanation = "Suele notarse en el pliegue entre el muslo y el abdomen, sobre todo al flexionar mucho en " +
            "sentadillas, zancadas o al subir la pierna. Se relaciona con los flexores de cadera.",
        commonCauses = listOf(
            "Sentadillas muy profundas con la pelvis que se mete hacia abajo.",
            "Muchas horas de silla, que acortan los flexores de cadera.",
            "Elevaciones de pierna sin control del abdomen.",
            "Zancadas largas con poca movilidad de cadera.",
        ),
        whatYouCanDo = "Reduce la profundidad hasta donde no moleste, abre un poco la posición de los pies y añade " +
            "movilidad de cadera. Baja la carga un 10 % en sentadilla y zancada unos días.",
        animation = DiscomfortSection.HIP_PELVIS,
        searchAliases = listOf(
            "cadera", "cadera delantera", "cadera frontal", "cadera adelante", "pliegue de la cadera",
            "flexor de cadera", "flexores de cadera",
        ),
    ),
    DiscomfortKnowledge(
        id = "hip_lateral",
        zoneLabel = "CADERA",
        zonePhrase = "la cadera",
        title = "Molestia en el lateral de la cadera o el glúteo",
        explanation = "Suele notarse en el lado de la cadera o en el glúteo, sobre todo al apoyar una sola pierna. " +
            "Se relaciona con el glúteo medio y con la estabilidad de la pelvis.",
        commonCauses = listOf(
            "Muchas series a una pierna con la pelvis que cae de lado.",
            "Llevar la rodilla hacia dentro al bajar.",
            "Subir de golpe el volumen de zancadas y peso muerto a una pierna.",
            "Poca fuerza del glúteo medio.",
        ),
        whatYouCanDo = "Mantén la pelvis nivelada, usa una variante con apoyo de las dos piernas y baja la carga un " +
            "10 % en zancadas y sentadillas búlgaras. Añade trabajo de glúteo medio con goma.",
        animation = DiscomfortSection.HIP_PELVIS,
        searchAliases = listOf(
            "cadera", "cadera lateral", "cadera de lado", "cadera externa", "lado de la cadera", "gluteo",
            "gluteos", "gluteo medio", "trocanter",
        ),
    ),
    DiscomfortKnowledge(
        id = "adductor_groin",
        zoneLabel = "INGLE",
        zonePhrase = "la ingle",
        title = "Molestia en la ingle o la cara interna del muslo",
        explanation = "Suele notarse en la parte interna del muslo, cerca de la ingle, al abrir las piernas o " +
            "cambiar de dirección. Se relaciona con los músculos aductores.",
        commonCauses = listOf(
            "Sentadillas anchas o sumo con carga y poca movilidad.",
            "Zancadas laterales o estiramientos bruscos sin calentar.",
            "Subir de golpe el volumen de pierna.",
            "Cambios de dirección y saltos laterales repetidos.",
        ),
        whatYouCanDo = "Calienta la cadera antes de abrir las piernas, reduce el ancho de la postura y baja la " +
            "carga un 10 % unos días. Evita estirar con fuerza la zona mientras moleste.",
        animation = DiscomfortSection.HIP_PELVIS,
        searchAliases = listOf(
            "ingle", "aductores", "aductor", "muslo interno", "interior del muslo", "muslo por dentro",
            "entrepierna",
        ),
    ),
    DiscomfortKnowledge(
        id = "hamstring_proximal",
        zoneLabel = "MUSLO TRASERO",
        zonePhrase = "la parte de atrás del muslo",
        title = "Molestia arriba de la parte trasera del muslo",
        explanation = "Suele notarse en la zona alta de atrás del muslo, cerca del glúteo, al sentarte o al hacer " +
            "bisagras de cadera. Se relaciona con el origen de los isquiosurales.",
        commonCauses = listOf(
            "Peso muerto rumano o buenos días con mucha carga y rango.",
            "Subir el volumen de isquios o el peso muy rápido.",
            "Estirar al máximo con la carga ya pesada.",
            "Mucho tiempo de silla con presión sobre la zona.",
        ),
        whatYouCanDo = "Acorta el rango de la bisagra, baja la carga un 10 % y cambia por curl femoral o puente de " +
            "glúteo unos días. Evita estirar con fuerza la zona mientras moleste.",
        animation = DiscomfortSection.HIP_PELVIS,
        searchAliases = listOf(
            "isquios", "isquiotibiales", "isquiosurales", "femoral", "femorales", "muslo trasero",
            "muslo posterior", "parte de atras del muslo",
        ),
    ),
    DiscomfortKnowledge(
        id = "knee_patellar",
        zoneLabel = "RODILLA",
        zonePhrase = "la rodilla",
        title = "Molestia delante de la rodilla",
        explanation = "Suele notarse justo bajo la rótula, al bajar con carga o al subir escaleras. Se relaciona " +
            "con el tendón que une la rótula con la tibia.",
        commonCauses = listOf(
            "Subir el volumen de pierna demasiado rápido.",
            "Rodillas muy por delante con poca movilidad de tobillo.",
            "Saltos y zancadas seguidas sin descanso.",
            "Poco descanso entre sesiones de pierna.",
        ),
        whatYouCanDo = "Baja un 10 % la carga en la próxima sesión de pierna y cambia la zancada por prensa unos " +
            "días. Añade movilidad de tobillo antes de entrenar.",
        animation = DiscomfortSection.KNEE,
        searchAliases = listOf(
            "rodilla", "rodillas", "rotula", "rodilla delantera", "rodilla frontal",
            "delante de la rodilla", "rodilla escaleras", "tendon rotuliano",
        ),
    ),
    DiscomfortKnowledge(
        id = "knee_medial",
        zoneLabel = "RODILLA",
        zonePhrase = "la rodilla",
        title = "Molestia a los lados de la rodilla",
        explanation = "Suele notarse en el lado interno o externo de la rodilla, al girar, bajar en sentadilla o " +
            "apoyar una pierna. Se relaciona con los músculos y tendones que rodean la articulación.",
        commonCauses = listOf(
            "Rodillas que se van hacia dentro al bajar o al subir.",
            "Giros del cuerpo con el pie clavado en el suelo.",
            "Zancadas largas o a una pierna con mucha carga.",
            "Poca fuerza de glúteo y cadera para sostener la rodilla.",
        ),
        whatYouCanDo = "Empuja la rodilla hacia donde apunta el pie, baja la carga un 10 % y trabaja el glúteo " +
            "medio con goma. Cambia las zancadas por prensa o sentadilla con apoyo unos días.",
        animation = DiscomfortSection.KNEE,
        searchAliases = listOf(
            "rodilla", "rodillas", "rodilla interna", "rodilla externa", "rodilla de lado",
            "costado de la rodilla", "lado de la rodilla",
        ),
    ),
    DiscomfortKnowledge(
        id = "achilles",
        zoneLabel = "AQUILES",
        zonePhrase = "el tendón de Aquiles",
        title = "Molestia en el tendón de Aquiles",
        explanation = "Suele notarse en la parte de atrás del tobillo, sobre el talón, sobre todo al empezar a " +
            "moverte o al saltar. Se relaciona con el tendón que une la pantorrilla con el talón.",
        commonCauses = listOf(
            "Subir rápido los saltos, la carrera o las elevaciones de talón.",
            "Calzado con poca amortiguación.",
            "Pantorrillas muy tensas o poca movilidad de tobillo.",
            "Elevaciones de talón con rebote y carga alta.",
        ),
        whatYouCanDo = "Reduce saltos y carrera unos días, haz elevaciones de talón lentas con una pausa abajo y " +
            "baja la carga un 10 %. Calienta la pantorrilla antes de entrenar.",
        animation = DiscomfortSection.ANKLE_FOOT,
        searchAliases = listOf(
            "aquiles", "tendon de aquiles", "talon", "talon de aquiles", "tendon del talon", "tobillo atras",
        ),
    ),
    DiscomfortKnowledge(
        id = "ankle",
        zoneLabel = "TOBILLO",
        zonePhrase = "el tobillo",
        title = "Molestia en el tobillo",
        explanation = "Suele notarse a los lados o al frente del tobillo, al agacharte con carga o al apoyar mal el " +
            "pie. Se relaciona con su movilidad y con los músculos que lo estabilizan.",
        commonCauses = listOf(
            "Poca movilidad al llevar la rodilla hacia delante.",
            "Apoyos inestables o calzado sin sujeción.",
            "Aterrizar mal tras saltos o cambios de dirección.",
            "Mucho volumen de pantorrilla y pierna a la vez.",
        ),
        whatYouCanDo = "Trabaja la movilidad de tobillo antes de la sesión, usa calzado estable y baja la carga un " +
            "10 % en sentadilla y zancada. Evita las superficies inestables unos días.",
        animation = DiscomfortSection.ANKLE_FOOT,
        searchAliases = listOf(
            "tobillo", "tobillos", "tobillo torcido", "torcedura", "tobillo lateral",
        ),
    ),
    DiscomfortKnowledge(
        id = "plantar_foot",
        zoneLabel = "PIE",
        zonePhrase = "el pie",
        title = "Molestia en la planta del pie",
        explanation = "Suele notarse en el arco o bajo el talón, sobre todo al levantarte o al apoyar con carga. Se " +
            "relaciona con la tensión de la planta del pie y de las pantorrillas.",
        commonCauses = listOf(
            "Calzado plano o muy blando para cargar peso.",
            "Subir rápido la carrera o los saltos.",
            "Pantorrillas muy tensas.",
            "Muchas horas de pie antes de entrenar.",
        ),
        whatYouCanDo = "Cambia a un calzado con soporte, masajea la planta con una pelota y baja la carga un 10 % " +
            "en sentadilla y saltos. Calienta pie y pantorrilla antes de entrenar.",
        animation = DiscomfortSection.ANKLE_FOOT,
        searchAliases = listOf(
            "pie", "pies", "planta del pie", "planta", "arco del pie", "suela del pie", "talon plantar",
        ),
    ),
)

val DISCOMFORT_KNOWLEDGE_BY_ID: Map<String, DiscomfortKnowledge> =
    DISCOMFORT_KNOWLEDGE.associateBy { it.id }

/** Conocimiento de una molestia; `null` para `"none"` y para ids desconocidos. */
fun discomfortKnowledge(id: String): DiscomfortKnowledge? = DISCOMFORT_KNOWLEDGE_BY_ID[id]
