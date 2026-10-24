package com.example.kpkn.domain.onboarding

/**
 * Copy for the WIZCHAT assistant. Every acknowledgement recognises the real
 * answer and explains what it changes, without claiming results, plans or
 * effects that the engines do not guarantee. Variants are deterministic so a
 * reopened draft never rewrites its history.
 */
object WizChatCopyCatalog {
    const val SCRIPT_VERSION = 3

    private val stageReactions = mapOf(
        WizChatStage.PROFILE to listOf("Anotado.", "De acuerdo.", "Recibido."),
        WizChatStage.TRAINING to listOf("Entendido.", "De acuerdo.", "Anotado."),
        WizChatStage.NUTRITION to listOf("Anotado.", "De acuerdo.", "Recibido."),
        WizChatStage.REVIEW to listOf("De acuerdo."),
    )

    private val omittedReactions = listOf(
        "Sin problema, lo dejamos para más adelante.",
        "Tranquilo, seguimos sin eso por ahora.",
        "De acuerdo, no pasa nada.",
    )

    fun intro(userName: String? = null): String {
        val greeting = if (userName.isNullOrBlank()) "¡Hola!" else "¡Hola, $userName!"
        return "$greeting Soy tu guía en KPKN. Te haré unas preguntas sencillas para preparar tu perfil, tu entrenamiento y lo que necesites. ¡Empezamos!"
    }

    fun acknowledgement(
        stage: WizChatStage,
        variantId: String?,
        userName: String? = null,
        questionId: WizChatQuestionId? = null,
        answerText: String? = null,
        nextQuestionId: WizChatQuestionId? = null,
        omitted: Boolean = false,
        imported: Boolean = false,
    ): String {
        if (omitted) return pick(omittedReactions, variantId)
        val transitionHint = nextQuestionId
            ?.takeIf { next -> WizChatGraph.stageFor(next) != stage }
            ?.let { nextStepHint(it) }
        val contextual = questionId?.let { contextualAck(it, answerText, imported) }
        if (contextual != null) {
            val nameLine = if (questionId == WizChatQuestionId.P_NAME && !userName.isNullOrBlank()) "Genial, $userName!" else null
            return listOf(nameLine, contextual, transitionHint).filter { it.isNullOrBlank().not() }.joinToString(" ")
        }
        val reaction = pick(stageReactions[stage] ?: stageReactions.getValue(WizChatStage.PROFILE), variantId)
        return listOf(reaction, transitionHint).filter { !it.isNullOrBlank() }.joinToString(" ")
    }

    /** Recognises the chosen value and explains what it changes for the next steps. */
    fun contextualAck(questionId: WizChatQuestionId, answerText: String?, imported: Boolean = false): String? {
        val value = answerText.orEmpty().trim()
        return when (questionId) {
            WizChatQuestionId.P_NAME -> "Con ese nombre te llamo de aquí en adelante."
            WizChatQuestionId.P_GENDER -> when {
                value.contains("Prefiero") -> "Sin problema. Seguimos sin ese dato; no hace falta para nada de lo que viene."
                value == "Otro" -> "Anotado. Lo dejo solo como dato de perfil; para la energía hay una pregunta aparte."
                else -> "Anotado. Lo dejo solo como dato de perfil; para la energía hay una pregunta aparte."
            }
            WizChatQuestionId.P_AGE -> "La edad $value entra en las referencias de energía y recuperación que usemos."
            WizChatQuestionId.P_HEIGHT -> "$value: la estatura también forma parte del cálculo de tu gasto energético."
            WizChatQuestionId.P_WEIGHT -> if (imported) {
                "Confirmas el peso de tu perfil ($value). Con él se calculan tus referencias y se sigue tu progreso."
            } else {
                "El peso ($value) es la base para tus referencias de comida y para seguir tu progreso."
            }
            WizChatQuestionId.P_EXPERIENCE -> when {
                value.contains("empezando") -> "Perfecto, arrancamos desde una base tranquila: primero constancia y técnica."
                value.contains("volviendo") -> "Volvemos a la rutina de forma progresiva, sin exigirte como si nunca hubieras parado."
                value.contains("constancia") -> "Ya tienes base y constancia: podremos dosificar el plan con más detalle."
                else -> "Con tu experiencia previa el plan podrá ser más exigente desde el principio."
            }
            WizChatQuestionId.T_ROUTE -> when {
                value.contains("Recomiéndame") -> "Vale, te propongo opciones y tú eliges la que más te encaje."
                value.contains("protocolo") -> "Te enseño métodos con su progresión definida para que compares sus requisitos."
                value.contains("cero") -> "Tú montas las sesiones y yo me aseguro de que sean ejecutables antes de activarlas."
                else -> "De acuerdo: dejamos tu perfil de volumen preparado y creas el programa cuando quieras."
            }
            WizChatQuestionId.T_GOAL -> when {
                value.contains("Fuerza + cardio") -> "Combinaremos fuerza y cardio. Te preguntaré por el cardio y por si prefieres enfocarte en fuerza, músculo o ambos."
                value.contains("Salud") -> "Buscamos que te sientas mejor en el día a día. Te preguntaré si prefieres enfocarte en fuerza, músculo o ambos."
                value.contains("Fuerza y músculo") -> "Buscaremos fuerza y músculo a la vez. Con ese enfoque ajusto el volumen y busco tus programas."
                value.contains("Fuerza") -> "Nos centraremos en ganar fuerza. Con ese enfoque ajusto el volumen y busco tus programas."
                value.contains("Músculo") -> "Nos centraremos en ganar músculo. Usaré ese enfoque para ajustar el volumen y buscar tus programas."
                else -> "Con ese objetivo ajusto el volumen y busco tus programas."
            }
            WizChatQuestionId.T_STYLE -> when {
                value.contains("Ambos") || value.contains("Powerbuilding") -> "De acuerdo: trabajaremos fuerza y músculo a la vez. Ajusto el volumen a ese enfoque."
                value.contains("Fuerza") || value.contains("Powerlifting") -> "De acuerdo: priorizamos la fuerza. Ajusto el volumen a ese enfoque."
                else -> "De acuerdo: priorizamos el músculo. Ajusto el volumen a ese enfoque."
            }
            WizChatQuestionId.T_VOLUME_TECHNIQUE -> when {
                value.contains("Aprendiendo") -> "La técnica marca cuánto margen dejamos para progresar: reservaré espacio para practicar antes de exigirte carga."
                value.contains("sólida") -> "Con la técnica muy sólida podremos trabajar cargas más exigentes cuando toque."
                else -> "Con la técnica bastante estable podemos dosificar la progresión con normalidad."
            }
            WizChatQuestionId.T_VOLUME_CONSISTENCY -> when {
                value.contains("Irregular") -> "Partimos de una constancia irregular: el volumen irá con margen para que puedas sostenerlo semana a semana."
                value.contains("Muy") -> "Con mucha constancia podemos sostener un volumen más alto y estructurado."
                else -> "Con bastante constancia el volumen puede ir subiendo de forma ordenada."
            }
            WizChatQuestionId.T_VOLUME_STRENGTH -> when {
                value.contains("Inicial") -> "Tu nivel de fuerza actual ancla la carga de partida: empezamos conservadores."
                value.contains("Avanzada") -> "Con fuerza avanzada la carga de partida puede ser más alta y progresar más despacio."
                else -> "Con fuerza intermedia ajustamos la carga de partida a mitad de recorrido."
            }
            WizChatQuestionId.T_VOLUME_MOBILITY -> when {
                value.contains("Limitada") -> "Con movilidad limitada priorizaré variantes cómodas que puedas ejecutar bien."
                value.contains("Amplia") -> "Con buena movilidad abrimos el abanico de variantes disponibles."
                else -> "Con movilidad suficiente trabajamos con las variantes habituales."
            }
            WizChatQuestionId.T_EQUIPMENT -> when {
                value.contains("casa") -> "Entonces trabajaremos con lo que tienes en casa. Ahora te preguntaré por el material para descartar planes que no puedas hacer."
                value.contains("Gimnasio completo") -> "Con gimnasio completo descarto solo lo que necesite material que no sueles tener."
                value.contains("máquinas") -> "Con máquinas como base descarto planes que exijan barra o material libre como requisito."
                value.contains("Sin material") -> "Trabajaremos solo con tu cuerpo: descarto lo que necesite cualquier material."
                else -> "Con ese punto de partida descarto los planes que no puedas ejecutar."
            }
            WizChatQuestionId.T_HOME_EQUIPMENT -> "Con ese material descarto los planes que necesiten algo que no tienes."
            WizChatQuestionId.T_DAYS -> {
                val days = value.toIntOrNull()
                val word = when (days) {
                    1 -> "un día"; 2 -> "dos días"; 3 -> "tres días"; 4 -> "cuatro días"
                    5 -> "cinco días"; 6 -> "seis días"; else -> value
                }
                "Vamos con $word por semana. Buscaré planes que encajen en esos días."
            }
            WizChatQuestionId.T_WEEKDAYS -> "Anoto esos días. Si el plan que elijas tiene otra rotación, te lo indicaré antes de activarlo."
            WizChatQuestionId.T_TIME -> "Busquemos algo que puedas sostener con ese tiempo, sin proponerte sesiones demasiado largas."
            WizChatQuestionId.T_CARDIO_TYPE -> "El cardio de tus sesiones será $value. Lo reservo dentro del tiempo de cada sesión."
            WizChatQuestionId.T_CARDIO_TIME -> "Reservaré esos minutos para el cardio dentro de tus sesiones."
            WizChatQuestionId.T_TRAINING_MAX -> when {
                value.contains("Conozco") -> "Perfecto: en la siguiente pregunta te pediré las marcas que sepas y las usaremos como referencia de carga."
                else -> "Sin problema: trabajaremos sin marcas y no calcularemos kilos que no nos hayas dado."
            }
            WizChatQuestionId.T_MARKS -> "Anoto tus marcas como referencia de carga para esos levantamientos."
            WizChatQuestionId.T_PLAN -> when {
                value == "from-scratch" -> "Hecho: tus sesiones se montarán tal como las has preparado. Revísalo en el siguiente paso."
                else -> "Hecho: preparo ese plan para que lo revises antes de activarlo."
            }
            WizChatQuestionId.T_REVIEW -> "Pásate por el resumen y actívalo cuando quieras."
            WizChatQuestionId.N_START -> when {
                value.contains("profesional") -> "Perfecto: guardaremos tus indicaciones tal cual, sin sustituirlas por un cálculo propio."
                value.contains("después") -> "De acuerdo, lo dejamos preparado para más adelante."
                value.contains("Conservar") -> "De acuerdo: mantenemos el plan que tienes activo."
                else -> "Vale: calculo unas referencias de comida con tus datos y te enseño el resultado antes de activar nada."
            }
            WizChatQuestionId.N_SEX -> when {
                value.contains("Prefiero") -> "Lo dejo sin definir. Sin ese dato no calculamos la energía automáticamente; te lo indicaré en el resultado."
                else -> "Este dato entra solo en la ecuación que estima tu energía; no define tu género ni tu entrenamiento."
            }
            WizChatQuestionId.N_ELIGIBILITY -> when {
                value.contains("Ninguna") -> "Sin condiciones especiales que contemplar antes de sugerirte referencias."
                else -> "Lo tendré en cuenta antes de sugerirte nada. Una recomendación nunca sustituye indicaciones médicas."
            }
            WizChatQuestionId.N_DIRECTION -> when {
                value.contains("Definir") -> "Enfocamos tu alimentación hacia la definición con un ritmo sostenible."
                value.contains("Volumen") -> "Enfocamos tu alimentación hacia ganar peso de forma controlada."
                else -> "Buscamos mantenimiento: estabilidad, sin subir ni bajar de forma deliberada."
            }
            WizChatQuestionId.N_ACTIVITY -> "Con eso estimo tu gasto fuera del entrenamiento, sin sumar tus sesiones dos veces."
            WizChatQuestionId.N_RESULT -> "Estas son tus referencias iniciales: son un punto de partida ajustable, no una medición."
            WizChatQuestionId.REVIEW -> null
        }
    }

    /** Context for active training and nutrition questions. */
    fun prefaces(questionId: WizChatQuestionId): List<String> = when (questionId) {
        WizChatQuestionId.N_SEX -> listOf(
            "Para estimar tu energía necesito un dato más: el sexo que usamos solo en esa ecuación.",
        )
        WizChatQuestionId.T_STYLE -> listOf(
            "Una última pregunta de enfoque y con eso ajusto el volumen.",
        )
        else -> emptyList()
    }

    fun nextStepHint(nextQuestionId: WizChatQuestionId?, userName: String? = null): String {
        if (nextQuestionId == null) return "Ya casi tenemos tu configuración lista."
        return when (nextQuestionId) {
            WizChatQuestionId.P_NAME -> "Empezamos por lo más básico."
            WizChatQuestionId.P_GENDER, WizChatQuestionId.P_AGE, WizChatQuestionId.P_HEIGHT,
            WizChatQuestionId.P_WEIGHT, WizChatQuestionId.P_EXPERIENCE ->
                "Ahora unos datos tuyos para que todo encaje contigo."
            WizChatQuestionId.T_ROUTE -> "Pasamos a tu entrenamiento: te propongo cómo armar la semana."
            WizChatQuestionId.T_GOAL, WizChatQuestionId.T_STYLE,
            WizChatQuestionId.T_VOLUME_TECHNIQUE, WizChatQuestionId.T_VOLUME_CONSISTENCY,
            WizChatQuestionId.T_VOLUME_STRENGTH, WizChatQuestionId.T_VOLUME_MOBILITY ->
                "Seguimos con el entrenamiento: un par de preguntas sobre tu estilo."
            WizChatQuestionId.T_EQUIPMENT, WizChatQuestionId.T_HOME_EQUIPMENT ->
                "Ahora veamos dónde y con qué puedes entrenar."
            WizChatQuestionId.T_DAYS, WizChatQuestionId.T_WEEKDAYS, WizChatQuestionId.T_TIME ->
                "Revisemos cuántos días y cuánto tiempo tienes de verdad."
            WizChatQuestionId.T_CARDIO_TYPE, WizChatQuestionId.T_CARDIO_TIME ->
                "Un momentito más sobre el cardio y seguimos."
            WizChatQuestionId.T_TRAINING_MAX, WizChatQuestionId.T_MARKS ->
                "Si conoces tus marcas, las usamos; si no, también está bien."
            WizChatQuestionId.T_PLAN -> "Enseguida te enseño opciones de plan que encajan contigo."
            WizChatQuestionId.T_REVIEW -> "Ya casi puedes ver el plan armado con tus respuestas."
            WizChatQuestionId.N_START -> "Si quieres, también dejamos lista tu alimentación."
            WizChatQuestionId.N_SEX, WizChatQuestionId.N_ELIGIBILITY,
            WizChatQuestionId.N_DIRECTION, WizChatQuestionId.N_ACTIVITY ->
                "Seguimos con un par de datos para tus referencias de comida."
            WizChatQuestionId.N_RESULT -> "Ya casi ves tus referencias de alimentación."
            WizChatQuestionId.REVIEW -> "Vamos al resumen: ahí podrás activar todo."
        }
    }

    fun progressLabel(stage: WizChatStage): String = when (stage) {
        WizChatStage.PROFILE -> "Tus datos"
        WizChatStage.TRAINING -> "Tu entrenamiento"
        WizChatStage.NUTRITION -> "Tu alimentación"
        WizChatStage.REVIEW -> "Resumen y activación"
    }

    private fun pick(options: List<String>, variantId: String?): String {
        if (options.isEmpty()) return "Anotado."
        val index = variantId?.takeLast(2)?.toIntOrNull(16)?.mod(options.size) ?: 0
        return options[index]
    }
}
