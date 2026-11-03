package com.example.kpkn.domain.cardio

import com.example.kpkn.data.models.CardioType

/**
 * Pulido v1 · CARDIO DEL EDITOR · punto 9: el contenido de la ficha de cada modalidad de cardio (`CE_GUIDE` de `scenes-ce.js`, documento
 * «Textos de las 12 fichas»). Contenido NUEVO: no existía nada parecido en la app. [formats] son ids de `CardioFeaturedTemplates`.
 */
data class CardioFichaGuide(
    /** Qué es. */
    val what: String,
    /** Qué entrena (etiquetas). */
    val trains: List<String>,
    /** La línea específica de la máquina sobre cómo medir el esfuerzo (las cinco zonas son las de `CardioZones`). */
    val effort: String,
    /** Plantillas recomendadas (ids de `CardioFeaturedTemplates`). */
    val formats: List<String>,
    /** Técnica y seguridad. */
    val tips: List<String>,
)

object CardioFichaGuides {
    /** La ficha de cada una de las 12 modalidades. */
    val all: Map<CardioType, CardioFichaGuide> = mapOf(
    CardioType.TREADMILL to CardioFichaGuide(
        what = "Banda motorizada para correr o caminar con la velocidad y la inclinación que elijas, sin clima ni tráfico. Es la forma más fácil de mantener un ritmo exacto.",
        trains = listOf("Resistencia aeróbica", "Piernas", "Glúteos con inclinación"),
        effort = "En la cinta manda el ritmo. RPE 5–6 es el paso que te deja hablar a trompicones; RPE 8 es un ritmo que sostienes unos 20 minutos.",
        formats = listOf("steady_z2_30", "hiit_one_on_one_off", "hiit_pyramid_1_2_3"),
        tips = listOf(
            "Empieza caminando 2–3 minutos y sube la velocidad poco a poco.",
            "Mira al frente, no a los pies. Hombros sueltos y pasos cortos bajo la cadera.",
            "Con inclinación alta no te agarres a las barras: pierdes trabajo y falseas el gasto.",
            "Baja la velocidad antes de bajarte; no pares la banda en seco.",
        ),
    ),
    CardioType.ELLIPTICAL to CardioFichaGuide(
        what = "Pedales deslizantes que mueven piernas y brazos sin impacto. Buena para sumar volumen aeróbico cuando rodillas o caderas piden descanso.",
        trains = listOf("Resistencia aeróbica", "Piernas", "Glúteos", "Brazos y espalda"),
        effort = "Manda la resistencia y la cadencia, no la velocidad. RPE 6 es conversación entrecortada; para llegar a RPE 8 sube la resistencia, no solo el ritmo.",
        formats = listOf("steady_z2_30", "hiit_30_30", "hiit_one_on_one_off"),
        tips = listOf(
            "Postura erguida: no cargues el peso del cuerpo en las asas.",
            "Empuja con los talones y mantén los pies planos en los pedales.",
            "Pedalea hacia atrás unos minutos para variar el trabajo de glúteo e isquios.",
            "Mira al frente; evita encorvarte sobre la consola.",
        ),
    ),
    CardioType.ROW_MACHINE to CardioFichaGuide(
        what = "Ergómetro de remo: la fuerza sale de piernas, tronco y brazos en una palada continua. Es de los cardios que más músculo reparte.",
        trains = listOf("Resistencia aeróbica", "Piernas", "Espalda", "Core"),
        effort = "El monitor da el ritmo por 500 m; aquí guíate por el RPE. RPE 6 es una palada larga y controlada; RPE 9 son series cortas con toda la potencia.",
        formats = listOf("steady_z2_30", "hiit_30_30", "hiit_pyramid_1_2_3"),
        tips = listOf(
            "Primero empujan las piernas, luego se abre el tronco y al final tiran los brazos. Al volver, al revés.",
            "Espalda larga, sin redondear; el tirón termina a la altura del esternón.",
            "Freno (damper) entre 3 y 5: más no es más rápido, solo más pesado.",
            "Agarre relajado y muñecas neutras.",
        ),
    ),
    CardioType.BIKE_STATIONARY to CardioFichaGuide(
        what = "Bicicleta fija con resistencia regulable y pedaleo de bajo impacto. Ideal para base aeróbica y para intervalos controlados.",
        trains = listOf("Resistencia aeróbica", "Cuádriceps", "Glúteos"),
        effort = "Combina cadencia (80–100 rpm) y resistencia. RPE 5–6 es el ritmo de base; en los esfuerzos fuertes sube la resistencia sin bajar de unas 70 rpm.",
        formats = listOf("steady_z2_30", "hiit_tabata_20_10", "hiit_wingate_power"),
        tips = listOf(
            "Sillín a la altura de la cadera: con el pedal abajo, la rodilla casi extendida.",
            "Espalda larga y manos relajadas en el manillar.",
            "Si te duelen las rodillas, sube el sillín o baja la resistencia.",
            "Pedalea redondo: empuja y tira, sin golpes.",
        ),
    ),
    CardioType.STAIR_CLIMBER to CardioFichaGuide(
        what = "Escaleras sin fin: subes peldaño a peldaño al ritmo que eliges. Cardio exigente para piernas y glúteos con poco impacto.",
        trains = listOf("Resistencia aeróbica", "Glúteos", "Cuádriceps", "Gemelos"),
        effort = "El pulso sube rápido. RPE 6 con peldaños completos es un buen ritmo de base; los intervalos cortos de RPE 8–9 se notan enseguida en los glúteos.",
        formats = listOf("steady_z2_30", "hiit_one_on_one_off", "hiit_pyramid_1_2_3"),
        tips = listOf(
            "Apoya el pie entero en el peldaño, no solo la punta.",
            "Torso erguido, con una ligera inclinación. No te cuelgues de las barras.",
            "Mejor peldaños completos que muy rápidos y cortos.",
            "Empieza con 5 minutos suaves: el pulso sube más de lo que parece.",
        ),
    ),
    CardioType.SKI_ERG to CardioFichaGuide(
        what = "Ergómetro de esquí de fondo: tiras de dos asas hacia abajo con brazos, tronco y cadera. Reparte el esfuerzo por todo el tren superior y el core.",
        trains = listOf("Resistencia aeróbica", "Dorsales", "Core", "Hombros"),
        effort = "Mide metros y vatios. RPE 6 son tirones fluidos y respirables; RPE 9, bloques de 30–60 segundos con toda la potencia.",
        formats = listOf("hiit_30_30", "hiit_tabata_20_10", "steady_z2_30"),
        tips = listOf(
            "Empieza arriba con los brazos extendidos; al tirar, flexiona cadera y rodillas como una bisagra, no solo los brazos.",
            "Core firme y espalda larga; no te redondees al final del tirón.",
            "Deja que el cordón vuelva solo: aprovecha la subida para soltar.",
            "Agarre suave: la fuerza viene del cuerpo, no de los dedos.",
        ),
    ),
    CardioType.RUN_OUTDOOR to CardioFichaGuide(
        what = "Correr al aire libre con distancia y ritmo medidos por GPS. El terreno, el clima y los desniveles añaden variedad (y esfuerzo).",
        trains = listOf("Resistencia aeróbica", "Piernas", "Core"),
        effort = "El ritmo cambia con la cuesta y el viento: guíate por el RPE y por tu zona de pulso, no solo por el reloj. RPE 5 es poder hablar en frases.",
        formats = listOf("steady_z2_30", "hiit_one_on_one_off", "hiit_pyramid_1_2_3"),
        tips = listOf(
            "Pasos cortos y ligeros, aterrizando bajo tu centro de gravedad.",
            "Los primeros 5–10 minutos, suaves; sube el ritmo poco a poco.",
            "Lleva el móvil con el GPS activo, calzado adecuado y buena visibilidad.",
            "En las cuestas acorta el paso y mantén el esfuerzo, no la velocidad.",
        ),
    ),
    CardioType.BIKE_OUTDOOR to CardioFichaGuide(
        what = "Ciclismo en carretera o camino con distancia y ritmo registrados por GPS. Permite tiradas largas de base aeróbica.",
        trains = listOf("Resistencia aeróbica", "Cuádriceps", "Glúteos", "Gemelos"),
        effort = "El viento y las cuestas mandan: usa el RPE y la zona de pulso. RPE 5–6 es el ritmo de fondo; los tramos de RPE 8, solo en subidas o bloques cortos.",
        formats = listOf("steady_z2_30", "hiit_pyramid_1_2_3", "hiit_one_on_one_off"),
        tips = listOf(
            "Casco siempre, y luces si hay poca luz.",
            "Cadencia cómoda (80–95 rpm): cambia de marcha antes de la cuesta.",
            "Codos flexibles y cuello relajado.",
            "Respeta el tráfico y no uses el móvil mientras pedaleas.",
        ),
    ),
    CardioType.WALK to CardioFichaGuide(
        what = "Caminata continua o por intervalos con GPS. La forma más amable de sumar actividad: sirve de base, de recuperación activa o de cardio principal.",
        trains = listOf("Resistencia aeróbica", "Piernas", "Glúteos"),
        effort = "RPE 3–4 es conversar sin problema. Subir cuestas o acelerar el paso lleva a RPE 5–6; si puedes cantar, vas muy suave.",
        formats = listOf("steady_z2_30", "hiit_one_on_one_off", "hiit_pyramid_1_2_3"),
        tips = listOf(
            "Postura alta, mirada al frente y paso natural.",
            "Brazos sueltos en vaivén y hombros relajados.",
            "Calzado cómodo, y agua si pasas de 45 minutos.",
            "Alterna paso rápido y suave para trabajar más sin cansarte de golpe.",
        ),
    ),
    CardioType.AIR_BIKE to CardioFichaGuide(
        what = "Bici de brazos y piernas con un ventilador: cuanto más fuerte empujas, más resistencia. Muy buena para sprints cortos y esfuerzos máximos.",
        trains = listOf("Todo el cuerpo", "Resistencia", "Potencia"),
        effort = "No hay niveles: la resistencia la pones tú. RPE 9–10 durante 10–30 segundos es un sprint de verdad; recupera pedaleando suave.",
        formats = listOf("hiit_tabata_20_10", "hiit_micro_sit_alactic", "hiit_wingate_power"),
        tips = listOf(
            "Empieza cada esfuerzo con una arrancada fuerte y mantén la cadencia.",
            "Empuja y tira con los brazos a la vez que pedaleas.",
            "Espalda erguida y agarre firme; no te encorves al fatigarte.",
            "Tras un sprint no pares en seco: sigue pedaleando suave.",
        ),
    ),
    CardioType.CURVED_TREADMILL to CardioFichaGuide(
        what = "Cinta curva y autopropulsada: la banda se mueve con tus pasos, sin motor. Marcas tú el ritmo, ideal para sprints y para trabajar la técnica de carrera.",
        trains = listOf("Resistencia", "Potencia", "Piernas", "Core"),
        effort = "No hay velocidad fijada: cuanto más empujas, más rápido va. RPE 8–9 en tiradas cortas ya es muy intenso; no lo compares con una cinta motorizada.",
        formats = listOf("hiit_30_30", "hiit_tabata_20_10", "hiit_micro_sit_alactic"),
        tips = listOf(
            "Apoya el antepié y empuja la banda hacia atrás, con el torso ligeramente inclinado.",
            "Brazos activos: marcan el ritmo.",
            "Entra caminando y acelera; sal igual, nunca de golpe.",
            "Si el pie resbala, acorta el paso y baja la inclinación del torso.",
        ),
    ),
    CardioType.SLED to CardioFichaGuide(
        what = "Trineo cargado que empujas o arrastras sobre una distancia corta. Trabajo de piernas y potencia con muy poco impacto articular.",
        trains = listOf("Piernas", "Glúteos", "Core", "Potencia"),
        effort = "Se mide por distancia, no por tiempo: tramos de 10–30 m. RPE 8–9 es empujar fuerte esos metros y descansar entre tramos.",
        formats = listOf("hiit_micro_sit_alactic", "hiit_30_30", "hiit_tabata_20_10"),
        tips = listOf(
            "Torso inclinado hacia delante y brazos extendidos; empuja con las piernas.",
            "Pasos cortos y potentes, sin arrastrar los pies.",
            "Empieza con poco peso y sube cuando la técnica sea limpia.",
            "Descansa lo suficiente entre tramos: manda la calidad.",
        ),
    ),
    )

    fun of(type: CardioType): CardioFichaGuide = all.getValue(type)
}
