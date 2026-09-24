package com.example.kpkn.domain.onboarding

import com.example.kpkn.data.models.DISCOMFORT_CATALOG

/** Minimal context needed to choose the next node; it deliberately contains no UI state. */
data class WizChatGraphContext(
    val includeTraining: Boolean = true,
    val includeNutrition: Boolean = true,
    val programRouteLater: Boolean = false,
    val trainingPlanSelected: Boolean = false,
    val recentTraining: Boolean? = null,
    val recentTrainingUnknown: Boolean = false,
    val nutritionStarted: Boolean = false,
    val nutritionProfessional: Boolean = false,
    val ringsAction: String? = null,
    val homeEquipmentSelected: Boolean = false,
    val hasTrainingMarks: Boolean = false,
    val includeRings: Boolean = true,
    val mixedTraining: Boolean = false,
    /** True when the goal already implies its training reference (Fuerza/Músculo/Fuerza y músculo). */
    val goalStyleInferred: Boolean = false,
)

object WizChatGraph {
    private val profile = listOf(
        WizChatQuestion(WizChatQuestionId.P_NAME, WizChatStage.PROFILE, "¿Cómo quieres que te llame?", WizChatAnswerKind.TEXT, allowSkip = true),
        WizChatQuestion(WizChatQuestionId.P_GENDER, WizChatStage.PROFILE, "¿Con qué género te identificas?", WizChatAnswerKind.CHOICE, options = listOf("Mujer", "Hombre", "Otro", "Prefiero no responder"), allowSkip = true),
        WizChatQuestion(WizChatQuestionId.P_AGE, WizChatStage.PROFILE, "¿Qué edad tienes?", WizChatAnswerKind.NUMBER, unit = "años", allowSkip = false),
        WizChatQuestion(WizChatQuestionId.P_HEIGHT, WizChatStage.PROFILE, "¿Cuánto mides?", WizChatAnswerKind.NUMBER, unit = "cm", allowSkip = false),
        WizChatQuestion(WizChatQuestionId.P_WEIGHT, WizChatStage.PROFILE, "¿Cuál es tu peso actual?", WizChatAnswerKind.NUMBER, unit = "kg", allowSkip = false),
        WizChatQuestion(WizChatQuestionId.P_EXPERIENCE, WizChatStage.PROFILE, "¿Cómo vienes con el entrenamiento?", WizChatAnswerKind.CHOICE, options = listOf("Estoy empezando", "Estoy volviendo", "Ya entreno con constancia", "Tengo experiencia")),
    )

    private val training = listOf(
        WizChatQuestion(WizChatQuestionId.T_ROUTE, WizChatStage.TRAINING, "¿Quieres que te arme el plan o prefieres construirlo tú?", WizChatAnswerKind.CHOICE, options = listOf("Recomiéndame un plan", "Elegir un protocolo", "Crear desde cero", "Lo decidiré después")),
        WizChatQuestion(WizChatQuestionId.T_GOAL, WizChatStage.TRAINING, "¿Qué te importa más ahora mismo?", WizChatAnswerKind.CHOICE, options = listOf("Fuerza", "Músculo", "Fuerza y músculo", "Salud y condición", "Fuerza + cardio")),
        WizChatQuestion(WizChatQuestionId.T_STYLE, WizChatStage.TRAINING, "¿Qué prefieres ganar?", WizChatAnswerKind.CHOICE, options = listOf("Fuerza", "Músculo", "Ambos")),
        WizChatQuestion(WizChatQuestionId.T_VOLUME_TECHNIQUE, WizChatStage.TRAINING, "¿Cómo sientes tu técnica en los ejercicios?", WizChatAnswerKind.CHOICE, options = listOf("Aprendiendo", "Bastante estable", "Muy sólida")),
        WizChatQuestion(WizChatQuestionId.T_VOLUME_CONSISTENCY, WizChatStage.TRAINING, "¿Qué tan constante has sido entrenando?", WizChatAnswerKind.CHOICE, options = listOf("Irregular", "Bastante constante", "Muy constante")),
        WizChatQuestion(WizChatQuestionId.T_VOLUME_STRENGTH, WizChatStage.TRAINING, "¿Cómo ves tu fuerza hoy?", WizChatAnswerKind.CHOICE, options = listOf("Inicial", "Intermedia", "Avanzada")),
        WizChatQuestion(WizChatQuestionId.T_VOLUME_MOBILITY, WizChatStage.TRAINING, "¿Cómo está tu movilidad?", WizChatAnswerKind.CHOICE, options = listOf("Limitada", "Suficiente", "Amplia")),
        WizChatQuestion(WizChatQuestionId.T_EQUIPMENT, WizChatStage.TRAINING, "¿Dónde sueles entrenar?", WizChatAnswerKind.CHOICE, options = listOf("Gimnasio completo", "Principalmente máquinas", "Entreno en casa", "Sin material")),
        WizChatQuestion(WizChatQuestionId.T_HOME_EQUIPMENT, WizChatStage.TRAINING, "¿Qué tienes de verdad en casa?", WizChatAnswerKind.MULTI_CHOICE, options = listOf("Peso corporal", "Bandas", "Mancuernas", "Barra de dominadas", "Apoyo estable", "Sin material")),
        WizChatQuestion(WizChatQuestionId.T_DAYS, WizChatStage.TRAINING, "¿Cuántos días a la semana puedes entrenar de verdad?", WizChatAnswerKind.CHOICE, options = (1..6).map(Int::toString)),
        WizChatQuestion(WizChatQuestionId.T_WEEKDAYS, WizChatStage.TRAINING, "¿Qué días quieres reservar para entrenar?", WizChatAnswerKind.MULTI_CHOICE, options = listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo")),
        WizChatQuestion(WizChatQuestionId.T_TIME, WizChatStage.TRAINING, "¿Cuánto tiempo tienes por sesión?", WizChatAnswerKind.NUMBER, unit = "min", suggestedValue = "60"),
        WizChatQuestion(WizChatQuestionId.T_CARDIO_TYPE, WizChatStage.TRAINING, "¿Qué tipo de cardio te apetece incluir?", WizChatAnswerKind.CHOICE, options = listOf("Caminar", "Correr al aire libre", "Bicicleta al aire libre")),
        WizChatQuestion(WizChatQuestionId.T_CARDIO_TIME, WizChatStage.TRAINING, "¿Cuántos minutos le quieres dedicar al cardio?", WizChatAnswerKind.CHOICE, options = listOf("10 min", "15 min", "20 min", "30 min")),
        WizChatQuestion(WizChatQuestionId.T_TRAINING_MAX, WizChatStage.TRAINING, "¿Conoces tus marcas (peso que mueves en sentadilla, banca…)?", WizChatAnswerKind.CHOICE, options = listOf("Conozco mis marcas", "Todavía no"), allowSkip = true),
        WizChatQuestion(WizChatQuestionId.T_MARKS, WizChatStage.TRAINING, "Cuéntame las marcas que sí sepas. Con una basta.", WizChatAnswerKind.ACTION),
        WizChatQuestion(WizChatQuestionId.T_PLAN, WizChatStage.TRAINING, "Encontré algunas opciones que encajan contigo. Elige la que prefieras.", WizChatAnswerKind.CHOICE),
        WizChatQuestion(WizChatQuestionId.T_REVIEW, WizChatStage.TRAINING, "Este es el plan armado con tus respuestas. Revísalo cuando quieras.", WizChatAnswerKind.ACTION),
    )

    private val nutrition = listOf(
        WizChatQuestion(WizChatQuestionId.N_START, WizChatStage.NUTRITION, "¿Quieres dejar también lista tu alimentación?", WizChatAnswerKind.CHOICE, options = listOf("Sí, preparar mis referencias", "Tengo indicaciones de un profesional", "Lo haré después")),
        WizChatQuestion(WizChatQuestionId.N_SEX, WizChatStage.NUTRITION, "¿Qué sexo usamos solo para calcular tu energía?", WizChatAnswerKind.CHOICE, options = listOf("Femenino", "Masculino", "Prefiero no responder")),
        WizChatQuestion(WizChatQuestionId.N_ELIGIBILITY, WizChatStage.NUTRITION, "¿Hay algo que debamos tener en cuenta antes de sugerirte una recomendación?", WizChatAnswerKind.MULTI_CHOICE, options = listOf("Ninguna de estas", "Embarazo", "Lactancia", "Restricción médica relevante", "No lo sé / prefiero no responder")),
        WizChatQuestion(WizChatQuestionId.N_DIRECTION, WizChatStage.NUTRITION, "¿Hacia dónde quieres llevar tu alimentación?", WizChatAnswerKind.CHOICE, options = listOf("Definir", "Mantener", "Volumen")),
        WizChatQuestion(WizChatQuestionId.N_ACTIVITY, WizChatStage.NUTRITION, "¿Qué tan activo eres en el día a día?", WizChatAnswerKind.CHOICE, options = listOf("Tranquilo", "Algo activo", "Activo", "Muy activo")),
        WizChatQuestion(WizChatQuestionId.N_RESULT, WizChatStage.NUTRITION, "Con tus datos puedo calcular estas referencias. Echales un vistazo.", WizChatAnswerKind.ACTION),
    )

    private val rings = listOf(
        WizChatQuestion(WizChatQuestionId.R_START, WizChatStage.RINGS, "Ahora situemos tus RINGS: es una estimación inicial de tu punto de partida, no una medición.", WizChatAnswerKind.CHOICE, options = listOf("Preparar mi punto de partida", "Dejar sin calibrar", "Conservar estimación actual", "Actualizarla", "Quitar estimación inicial")),
        WizChatQuestion(WizChatQuestionId.R_RECENT, WizChatStage.RINGS, "¿Entrenaste en los últimos siete días?", WizChatAnswerKind.CHOICE, options = listOf("Sí", "No", "No lo sé")),
        WizChatQuestion(WizChatQuestionId.R_SESSIONS, WizChatStage.RINGS, "¿Cuántas sesiones hiciste?", WizChatAnswerKind.CHOICE, options = (1..7).map { "$it" }),
        WizChatQuestion(WizChatQuestionId.R_RECENCY, WizChatStage.RINGS, "¿Cuándo fue tu última sesión?", WizChatAnswerKind.CHOICE, options = listOf("Hoy", "Ayer") + (2..6).map { "Hace $it días" }),
        WizChatQuestion(WizChatQuestionId.R_ACTIVITY, WizChatStage.RINGS, "¿Qué predominó en esas sesiones?", WizChatAnswerKind.CHOICE, options = listOf("Fuerza", "Cardio", "Mixta")),
        WizChatQuestion(WizChatQuestionId.R_INTENSITY, WizChatStage.RINGS, "¿Cómo sentiste la intensidad?", WizChatAnswerKind.CHOICE, options = listOf("Fácil", "Moderada", "Exigente", "Muy exigente")),
        WizChatQuestion(WizChatQuestionId.R_AXIAL, WizChatStage.RINGS, "¿Hubo cargas pesadas para la espalda (sentadilla, peso muerto…)?", WizChatAnswerKind.CHOICE, options = listOf("Sí", "No", "No lo sé")),
        WizChatQuestion(WizChatQuestionId.R_FEELINGS_MUSCLE, WizChatStage.RINGS, "¿Cómo se sienten tus músculos?", WizChatAnswerKind.CHOICE, options = listOf("Descansados", "Algo cargados", "Moderadamente cargados", "Cargados", "Muy cargados")),
        WizChatQuestion(WizChatQuestionId.R_FEELINGS_ENERGY, WizChatStage.RINGS, "¿Cómo está tu energía?", WizChatAnswerKind.CHOICE, options = listOf("Con energía", "Bien", "Intermedia", "Baja", "Agotado")),
        WizChatQuestion(WizChatQuestionId.R_FEELINGS_STRUCTURE, WizChatStage.RINGS, "¿Cómo está tu columna?", WizChatAnswerKind.CHOICE, options = listOf("Descansada", "Bien", "Intermedia", "Cargada", "Muy cargada")),
        WizChatQuestion(WizChatQuestionId.R_DISCOMFORT, WizChatStage.RINGS, "¿Hay alguna molestia que debamos tener en cuenta?", WizChatAnswerKind.MULTI_CHOICE, options = listOf("Sin molestias") + DISCOMFORT_CATALOG.filterNot { it.id == "none" }.map { it.label } + "Prefiero omitirlo"),
        WizChatQuestion(WizChatQuestionId.R_RESULT, WizChatStage.RINGS, "Este es el punto de partida que declaraste para tus RINGS.", WizChatAnswerKind.ACTION),
    )

    private val review = WizChatQuestion(WizChatQuestionId.REVIEW, WizChatStage.REVIEW, "Tu configuración está lista. ¿Quieres activarla ahora?", WizChatAnswerKind.ACTION)

    fun question(id: WizChatQuestionId): WizChatQuestion? = (profile + training + nutrition + rings).firstOrNull { it.id == id } ?: review.takeIf { it.id == id }

    fun firstFor(context: WizChatGraphContext): WizChatQuestionId = when {
        !context.includeTraining && !context.includeNutrition -> WizChatQuestionId.R_START
        !context.includeTraining -> WizChatQuestionId.N_START
        !context.includeNutrition -> WizChatQuestionId.P_NAME
        else -> WizChatQuestionId.P_NAME
    }

    fun next(current: WizChatQuestionId, context: WizChatGraphContext): WizChatQuestionId? = when (current) {
        WizChatQuestionId.P_NAME -> WizChatQuestionId.P_GENDER
        WizChatQuestionId.P_GENDER -> WizChatQuestionId.P_AGE
        WizChatQuestionId.P_AGE -> WizChatQuestionId.P_HEIGHT
        WizChatQuestionId.P_HEIGHT -> WizChatQuestionId.P_WEIGHT
        WizChatQuestionId.P_WEIGHT -> if (context.includeTraining) WizChatQuestionId.P_EXPERIENCE else if (context.includeNutrition) WizChatQuestionId.N_START else WizChatQuestionId.R_START
        WizChatQuestionId.P_EXPERIENCE -> if (context.includeTraining) WizChatQuestionId.T_ROUTE else if (context.includeNutrition) WizChatQuestionId.N_START else WizChatQuestionId.R_START
        WizChatQuestionId.T_ROUTE -> if (context.programRouteLater) WizChatQuestionId.T_STYLE else WizChatQuestionId.T_GOAL
        WizChatQuestionId.T_GOAL -> if (context.goalStyleInferred) WizChatQuestionId.T_VOLUME_TECHNIQUE else WizChatQuestionId.T_STYLE
        WizChatQuestionId.T_STYLE -> WizChatQuestionId.T_VOLUME_TECHNIQUE
        WizChatQuestionId.T_VOLUME_TECHNIQUE -> WizChatQuestionId.T_VOLUME_CONSISTENCY
        WizChatQuestionId.T_VOLUME_CONSISTENCY -> WizChatQuestionId.T_VOLUME_STRENGTH
        WizChatQuestionId.T_VOLUME_STRENGTH -> WizChatQuestionId.T_VOLUME_MOBILITY
        WizChatQuestionId.T_VOLUME_MOBILITY -> if (context.programRouteLater) WizChatQuestionId.T_REVIEW else WizChatQuestionId.T_EQUIPMENT
        WizChatQuestionId.T_EQUIPMENT -> if (context.homeEquipmentSelected) WizChatQuestionId.T_HOME_EQUIPMENT else WizChatQuestionId.T_DAYS
        WizChatQuestionId.T_HOME_EQUIPMENT -> WizChatQuestionId.T_DAYS
        WizChatQuestionId.T_DAYS -> WizChatQuestionId.T_WEEKDAYS
        WizChatQuestionId.T_WEEKDAYS -> WizChatQuestionId.T_TIME
        WizChatQuestionId.T_TIME -> if (context.mixedTraining) WizChatQuestionId.T_CARDIO_TYPE else WizChatQuestionId.T_TRAINING_MAX
        WizChatQuestionId.T_CARDIO_TYPE -> WizChatQuestionId.T_CARDIO_TIME
        WizChatQuestionId.T_CARDIO_TIME -> WizChatQuestionId.T_TRAINING_MAX
        WizChatQuestionId.T_TRAINING_MAX -> if (context.hasTrainingMarks) WizChatQuestionId.T_MARKS else WizChatQuestionId.T_PLAN
        WizChatQuestionId.T_MARKS -> WizChatQuestionId.T_PLAN
        WizChatQuestionId.T_PLAN -> if (context.trainingPlanSelected || context.programRouteLater) WizChatQuestionId.T_REVIEW else WizChatQuestionId.T_PLAN
        WizChatQuestionId.T_REVIEW -> if (context.includeNutrition) WizChatQuestionId.N_START else if (context.includeRings) WizChatQuestionId.R_START else WizChatQuestionId.REVIEW
        WizChatQuestionId.N_START -> when {
            !context.includeNutrition -> if (context.includeRings) WizChatQuestionId.R_START else WizChatQuestionId.REVIEW
            context.nutritionProfessional -> WizChatQuestionId.N_RESULT
            context.nutritionStarted -> WizChatQuestionId.N_SEX
            else -> if (context.includeRings) WizChatQuestionId.R_START else WizChatQuestionId.REVIEW
        }
        WizChatQuestionId.N_SEX -> WizChatQuestionId.N_ELIGIBILITY
        WizChatQuestionId.N_ELIGIBILITY -> WizChatQuestionId.N_DIRECTION
        WizChatQuestionId.N_DIRECTION -> WizChatQuestionId.N_ACTIVITY
        WizChatQuestionId.N_ACTIVITY -> WizChatQuestionId.N_RESULT
        WizChatQuestionId.N_RESULT -> if (context.includeRings) WizChatQuestionId.R_START else WizChatQuestionId.REVIEW
        WizChatQuestionId.R_START -> {
            val action = context.ringsAction?.trim()?.lowercase().orEmpty()
            if (action == "omit" || action == "keep" || action == "preserve" || action == "remove" || action.contains("dejar") || action.contains("conservar") || action.contains("quitar") || action.contains("uncalibrated")) WizChatQuestionId.REVIEW else WizChatQuestionId.R_RECENT
        }
        WizChatQuestionId.R_RECENT -> when {
            context.recentTraining == true -> WizChatQuestionId.R_SESSIONS
            // El desconocimiento NO es una ausencia de entrenamiento: se saltan solo
            // las preguntas de historial, pero se siguen preguntando las tres
            // sensaciones y las molestias. Así es posible una calibración parcial con
            // check-in real sin inventar sesiones ni fabricar evidencia.
            else -> WizChatQuestionId.R_FEELINGS_MUSCLE
        }
        WizChatQuestionId.R_SESSIONS -> WizChatQuestionId.R_RECENCY
        WizChatQuestionId.R_RECENCY -> WizChatQuestionId.R_ACTIVITY
        WizChatQuestionId.R_ACTIVITY -> WizChatQuestionId.R_INTENSITY
        WizChatQuestionId.R_INTENSITY -> WizChatQuestionId.R_AXIAL
        WizChatQuestionId.R_AXIAL -> WizChatQuestionId.R_FEELINGS_MUSCLE
        WizChatQuestionId.R_FEELINGS_MUSCLE -> WizChatQuestionId.R_FEELINGS_ENERGY
        WizChatQuestionId.R_FEELINGS_ENERGY -> WizChatQuestionId.R_FEELINGS_STRUCTURE
        WizChatQuestionId.R_FEELINGS_STRUCTURE -> WizChatQuestionId.R_DISCOMFORT
        WizChatQuestionId.R_DISCOMFORT -> WizChatQuestionId.R_RESULT
        WizChatQuestionId.R_RESULT -> WizChatQuestionId.REVIEW
        WizChatQuestionId.REVIEW -> null
    }

    fun stageFor(id: WizChatQuestionId): WizChatStage = question(id)?.stage ?: WizChatStage.REVIEW
}
