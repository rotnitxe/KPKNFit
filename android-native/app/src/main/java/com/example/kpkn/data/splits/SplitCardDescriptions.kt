package com.example.kpkn.data.splits

/**
 * Descripción breve de cada reparto del catálogo (una frase: qué es, para quién y cuánto pide), la que se muestra en su
 * tarjeta del catálogo. Aprobadas con el lienzo de Pulido v1 (punto 23, «Reparto»): se usan tal cual. Salen solo del patrón,
 * las ventajas y las precauciones de cada reparto.
 *
 * Los repartos que no estén aquí (el propio de la persona) usan [SplitTemplate.description].
 */
val SPLIT_CARD_DESCRIPTIONS: Map<String, String> = mapOf(
    "ul_x4" to "Dos sesiones de torso y dos de pierna. Cada músculo dos veces por semana, con 48 a 72 h de recuperación.",
    "ppl_ul" to "Torso y pierna al empezar; luego empuje, tirón y pierna. Cinco días que combinan frecuencia y volumen.",
    "fullbody_x3" to "Tres sesiones de cuerpo completo con un día libre entre cada una. Cada músculo tres veces por semana.",
    "ppl_x6" to "Empuje, tirón y pierna, dos veces cada uno. El máximo volumen y la mayor fatiga: para quien ya recupera bien.",
    "ul_x6" to "Torso y pierna, tres veces cada uno. Frecuencia 3 por músculo; hay que vigilar la fatiga.",
    "ppl_arnold" to "Empuje, tirón y pierna, más un día de pecho y espalda y otro de hombro y brazo. Mucho volumen y énfasis estético.",
    "phat_hybrid" to "Dos días de torso, dos de pierna y un cierre de cuerpo completo para los puntos débiles.",
    "ant_post_x4" to "Divide el cuerpo por cadenas: anterior y posterior, dos veces cada una.",
    "arnold_ul" to "Pecho y espalda, pierna, hombro y brazo, y cierre de torso y pierna. Casi todo a frecuencia 2.",
    "ant_post_x6" to "Cadena anterior y posterior, tres veces cada una. Frecuencia 3 por cadena y fatiga sistémica muy alta.",
    "bro_split" to "Pecho, espalda, pierna, hombros y brazos: un grupo por día. Mucho volumen por sesión, pero cada músculo una vez por semana.",
    "hybrid_fb_ap" to "Dos días de cuerpo completo, uno de cadena anterior y uno de posterior. Cuatro días con frecuencia y especificidad.",
    "minimalist_x2" to "Dos sesiones de cuerpo completo, separadas. La dosis mínima efectiva cuando casi no hay tiempo.",
    "weekend_warrior" to "Solo sábado y domingo: torso con cuerpo completo y pierna con cuerpo completo. Para agendas extremas.",
    "glute_focus" to "Tres días de tren inferior con énfasis en glúteo, más torso ligero y hombros. Glúteo tres veces por semana.",
    "beach_body" to "Tres días de torso y una pierna de mantenimiento. Prioriza la estética del tren superior.",
    "fullbody_x5" to "Cuerpo completo cinco días. Frecuencia máxima y fatiga extrema: solo para avanzados.",
    "push_pull_x4" to "Empuje con cuádriceps y tirón con isquios, dos veces cada uno. La pierna se integra dentro de dos días.",
    "texas_method" to "Tres días de ondulación: volumen, recuperación e intensidad, con sentadilla y banca.",
    "smolov_base" to "Especialización brutal en un levantamiento, cuatro sesiones por semana. Solo ciclos cortos y con tolerancia alta.",
    "pl_sbd_x3" to "Sentadilla, banca y peso muerto tres días por semana. Máxima especificidad y fatiga articular alta.",
    "pl_hf_bench" to "Sentadilla tres veces, banca cuatro y peso muerto dos. Especializa la banca; vigila los hombros.",
    "pl_classic_4" to "Sentadilla con banca, peso muerto, banca de volumen y sentadilla con peso muerto. Base clásica de powerlifting.",
    "sheiko_3day" to "Sentadilla y banca, peso muerto y banca, y otra vez sentadilla y banca. Volumen muy alto y técnica refinada.",
    "westside_conjugate" to "Esfuerzo máximo y esfuerzo dinámico, para tren inferior y superior. Cambias de variantes constantemente.",
    "cube_method" to "Rotas esfuerzos: pesado, explosivo y repeticiones, con un día de accesorios. Evita los estancamientos.",
    "531_bbb" to "Press militar, peso muerto, banca y sentadilla, uno por día. Progresión lenta y sostenible.",
    "madcow_5x5" to "Volumen, recuperación e intensidad en tres días. Progresión lineal para intermedios.",
    "korte_3x3" to "Solo sentadilla, banca y peso muerto en tres sesiones: volumen, técnica e intensidad. Sin accesorios.",
    "nsuns_4day" to "Banca con press militar, sentadilla con sumo, banca cerrada y peso muerto con frontal. Volumen alto de 5/3/1.",
    "ul_arms" to "Torso y pierna dos veces, y un día dedicado a brazos y hombros.",
    "heavy_light" to "Tres sesiones de cuerpo completo: pesada, liviana y moderada. Ondulas la intensidad para recuperar bien.",
    "ppl_x3" to "Un día de empuje, uno de tirón y uno de pierna. Muy fácil de organizar; cada patrón, una vez por semana.",
    "fullbody_x4" to "Cuatro sesiones de cuerpo completo, de dos en dos. El puente entre el x3 y el x5.",
    "ul_fb_x3" to "Torso, pierna y cuerpo completo en tres días. Frecuencia 2 práctica si solo tienes tres días.",
)

/** La descripción para la tarjeta de [split]: la aprobada del catálogo o, si no hay, la de su plantilla. */
val SplitTemplate.cardDescription: String
    get() = SPLIT_CARD_DESCRIPTIONS[id] ?: description
