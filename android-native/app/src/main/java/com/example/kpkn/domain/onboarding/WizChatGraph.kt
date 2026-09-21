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
)

object WizChatGraph {
    private val profile = listOf(
        WizChatQuestion(WizChatQuestionId.P_NAME, WizChatStage.PROFILE, "¿Cómo quieres que te llame?", WizChatAnswerKind.TEXT, allowSkip = true),
        WizChatQuestion(WizChatQuestionId.P_AGE, WizChatStage.PROFILE, "¿Qué edad tienes?", WizChatAnswerKind.NUMBER, unit = "años", allowSkip = true),
        WizChatQuestion(WizChatQuestionId.P_HEIGHT, WizChatStage.PROFILE, "¿Cuánto mides?", WizChatAnswerKind.NUMBER, unit = "cm", allowSkip = true),
        WizChatQuestion(WizChatQuestionId.P_WEIGHT, WizChatStage.PROFILE, "¿Cuál es tu peso actual?", WizChatAnswerKind.NUMBER, unit = "kg", allowSkip = true),
        WizChatQuestion(WizChatQuestionId.P_EXPERIENCE, WizChatStage.PROFILE, "¿Cómo vienes con el entrenamiento?", WizChatAnswerKind.CHOICE, options = listOf("Estoy empezando", "Estoy volviendo", "Ya entreno con constancia", "Tengo experiencia")),
    )

    private val training = listOf(
        WizChatQuestion(WizChatQuestionId.T_ROUTE, WizChatStage.TRAINING, "¿Quieres que te prepare un plan o prefieres construirlo tú?", WizChatAnswerKind.CHOICE, options = listOf("Recomiéndame un plan", "Elegir un protocolo", "Crear desde cero", "Lo decidiré después")),
        WizChatQuestion(WizChatQuestionId.T_GOAL, WizChatStage.TRAINING, "¿Qué quieres priorizar?", WizChatAnswerKind.CHOICE, options = listOf("Fuerza", "Músculo", "Salud y condición", "Fuerza + cardio")),
        WizChatQuestion(WizChatQuestionId.T_STYLE, WizChatStage.TRAINING, "¿Qué estilo usamos como referencia para tu volumen?", WizChatAnswerKind.CHOICE, options = listOf("Hipertrofia", "Powerbuilding", "Powerlifting")),
        WizChatQuestion(WizChatQuestionId.T_VOLUME_TECHNIQUE, WizChatStage.TRAINING, "¿Cómo sientes tu técnica?", WizChatAnswerKind.CHOICE, options = listOf("Aprendiendo", "Bastante estable", "Muy sólida")),
        WizChatQuestion(WizChatQuestionId.T_VOLUME_CONSISTENCY, WizChatStage.TRAINING, "¿Qué tan constante has sido?", WizChatAnswerKind.CHOICE, options = listOf("Irregular", "Bastante constante", "Muy constante")),
        WizChatQuestion(WizChatQuestionId.T_VOLUME_STRENGTH, WizChatStage.TRAINING, "¿Dónde situarías tu fuerza actual?", WizChatAnswerKind.CHOICE, options = listOf("Inicial", "Intermedia", "Avanzada")),
        WizChatQuestion(WizChatQuestionId.T_VOLUME_MOBILITY, WizChatStage.TRAINING, "¿Cómo está tu movilidad?", WizChatAnswerKind.CHOICE, options = listOf("Limitada", "Suficiente", "Amplia")),
        WizChatQuestion(WizChatQuestionId.T_EQUIPMENT, WizChatStage.TRAINING, "¿Qué equipo tienes disponible?", WizChatAnswerKind.MULTI_CHOICE, options = listOf("Gimnasio completo", "Principalmente máquinas", "Entreno en casa", "Sin material")),
        WizChatQuestion(WizChatQuestionId.T_DAYS, WizChatStage.TRAINING, "¿Cuántos días reales puedes entrenar?", WizChatAnswerKind.NUMBER, unit = "días"),
        WizChatQuestion(WizChatQuestionId.T_WEEKDAYS, WizChatStage.TRAINING, "¿Qué días concretos quieres reservar?", WizChatAnswerKind.MULTI_CHOICE, options = listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo")),
        WizChatQuestion(WizChatQuestionId.T_TIME, WizChatStage.TRAINING, "¿Cuánto tiempo te acomoda dedicar a cada sesión?", WizChatAnswerKind.NUMBER, unit = "min", suggestedValue = "60"),
        WizChatQuestion(WizChatQuestionId.T_TRAINING_MAX, WizChatStage.TRAINING, "¿Conoces tus marcas de referencia?", WizChatAnswerKind.CHOICE, options = listOf("Conozco mis marcas", "Todavía no"), allowSkip = true),
        WizChatQuestion(WizChatQuestionId.T_PLAN, WizChatStage.TRAINING, "Encontré estas opciones compatibles con lo que declaraste.", WizChatAnswerKind.CHOICE),
        WizChatQuestion(WizChatQuestionId.T_REVIEW, WizChatStage.TRAINING, "Este es el plan que se puede ejecutar con tus respuestas.", WizChatAnswerKind.ACTION),
    )

    private val nutrition = listOf(
        WizChatQuestion(WizChatQuestionId.N_START, WizChatStage.NUTRITION, "¿Quieres dejar también tu alimentación configurada?", WizChatAnswerKind.CHOICE, options = listOf("Sí, preparar mis referencias", "Tengo indicaciones de un profesional", "Lo haré después")),
        WizChatQuestion(WizChatQuestionId.N_SEX, WizChatStage.NUTRITION, "¿Qué sexo usaremos únicamente para la ecuación energética?", WizChatAnswerKind.CHOICE, options = listOf("Femenino", "Masculino", "Prefiero no responder")),
        WizChatQuestion(WizChatQuestionId.N_ELIGIBILITY, WizChatStage.NUTRITION, "¿Hay alguna condición que impida una recomendación automática?", WizChatAnswerKind.MULTI_CHOICE, options = listOf("Ninguna de estas", "Embarazo", "Lactancia", "Restricción médica relevante", "No lo sé / prefiero no responder")),
        WizChatQuestion(WizChatQuestionId.N_DIRECTION, WizChatStage.NUTRITION, "¿Qué dirección quieres dar al plan?", WizChatAnswerKind.CHOICE, options = listOf("Definir", "Mantener", "Volumen")),
        WizChatQuestion(WizChatQuestionId.N_ACTIVITY, WizChatStage.NUTRITION, "¿Qué nivel describe mejor tu actividad cotidiana?", WizChatAnswerKind.CHOICE, options = listOf("Tranquilo", "Algo activo", "Activo", "Muy activo")),
        WizChatQuestion(WizChatQuestionId.N_RESULT, WizChatStage.NUTRITION, "Estas son las referencias que el motor puede calcular con tus datos.", WizChatAnswerKind.ACTION),
    )

    private val rings = listOf(
        WizChatQuestion(WizChatQuestionId.R_START, WizChatStage.RINGS, "Ahora situemos tus RINGS. Esto será una estimación inicial, no una medición.", WizChatAnswerKind.CHOICE, options = listOf("Preparar mi punto de partida", "Dejar sin calibrar", "Conservar estimación actual", "Actualizarla", "Quitar estimación inicial")),
        WizChatQuestion(WizChatQuestionId.R_RECENT, WizChatStage.RINGS, "¿Entrenaste durante los últimos siete días?", WizChatAnswerKind.CHOICE, options = listOf("Sí", "No", "No lo sé")),
        WizChatQuestion(WizChatQuestionId.R_SESSIONS, WizChatStage.RINGS, "¿Cuántas sesiones hiciste?", WizChatAnswerKind.CHOICE, options = (1..7).map { "$it" }),
        WizChatQuestion(WizChatQuestionId.R_RECENCY, WizChatStage.RINGS, "¿Cuándo fue tu última sesión?", WizChatAnswerKind.CHOICE, options = listOf("Hoy", "Ayer") + (2..6).map { "Hace $it días" }),
        WizChatQuestion(WizChatQuestionId.R_ACTIVITY, WizChatStage.RINGS, "¿Qué tipo de actividad predominó?", WizChatAnswerKind.CHOICE, options = listOf("Fuerza", "Cardio", "Mixta")),
        WizChatQuestion(WizChatQuestionId.R_INTENSITY, WizChatStage.RINGS, "¿Cómo fue la intensidad?", WizChatAnswerKind.CHOICE, options = listOf("Fácil", "Moderada", "Exigente", "Muy exigente")),
        WizChatQuestion(WizChatQuestionId.R_AXIAL, WizChatStage.RINGS, "¿Hubo carga importante sobre la columna?", WizChatAnswerKind.CHOICE, options = listOf("Sí", "No", "No lo sé")),
        WizChatQuestion(WizChatQuestionId.R_FEELINGS_MUSCLE, WizChatStage.RINGS, "¿Cómo se sienten tus músculos?", WizChatAnswerKind.CHOICE, options = listOf("Descansados", "Algo cargados", "Moderadamente cargados", "Cargados", "Muy cargados")),
        WizChatQuestion(WizChatQuestionId.R_FEELINGS_ENERGY, WizChatStage.RINGS, "¿Cómo está tu energía?", WizChatAnswerKind.CHOICE, options = listOf("Con energía", "Bien", "Intermedia", "Baja", "Agotado")),
        WizChatQuestion(WizChatQuestionId.R_FEELINGS_STRUCTURE, WizChatStage.RINGS, "¿Cómo está tu columna / estructura?", WizChatAnswerKind.CHOICE, options = listOf("Descansada", "Bien", "Intermedia", "Cargada", "Muy cargada")),
        WizChatQuestion(WizChatQuestionId.R_DISCOMFORT, WizChatStage.RINGS, "¿Hay alguna molestia que debamos registrar?", WizChatAnswerKind.MULTI_CHOICE, options = listOf("Sin molestias") + DISCOMFORT_CATALOG.filterNot { it.id == "none" }.map { it.label } + "Prefiero omitirlo"),
        WizChatQuestion(WizChatQuestionId.R_RESULT, WizChatStage.RINGS, "Este es tu punto de partida según el mismo pipeline que usa Home.", WizChatAnswerKind.ACTION),
    )

    private val review = WizChatQuestion(WizChatQuestionId.REVIEW, WizChatStage.REVIEW, "Tu configuración está lista. ¿Quieres activarla ahora?", WizChatAnswerKind.ACTION)

    fun question(id: WizChatQuestionId): WizChatQuestion? = (profile + training + nutrition + rings).firstOrNull { it.id == id } ?: review.takeIf { it.id == id }

    fun firstFor(context: WizChatGraphContext): WizChatQuestionId = WizChatQuestionId.P_NAME

    fun next(current: WizChatQuestionId, context: WizChatGraphContext): WizChatQuestionId? = when (current) {
        WizChatQuestionId.P_NAME -> WizChatQuestionId.P_AGE
        WizChatQuestionId.P_AGE -> WizChatQuestionId.P_HEIGHT
        WizChatQuestionId.P_HEIGHT -> WizChatQuestionId.P_WEIGHT
        WizChatQuestionId.P_WEIGHT -> WizChatQuestionId.P_EXPERIENCE
        WizChatQuestionId.P_EXPERIENCE -> if (context.includeTraining) WizChatQuestionId.T_ROUTE else if (context.includeNutrition) WizChatQuestionId.N_START else WizChatQuestionId.R_START
        WizChatQuestionId.T_ROUTE -> if (context.programRouteLater) WizChatQuestionId.T_REVIEW else WizChatQuestionId.T_GOAL
        WizChatQuestionId.T_GOAL -> WizChatQuestionId.T_STYLE
        WizChatQuestionId.T_STYLE -> WizChatQuestionId.T_VOLUME_TECHNIQUE
        WizChatQuestionId.T_VOLUME_TECHNIQUE -> WizChatQuestionId.T_VOLUME_CONSISTENCY
        WizChatQuestionId.T_VOLUME_CONSISTENCY -> WizChatQuestionId.T_VOLUME_STRENGTH
        WizChatQuestionId.T_VOLUME_STRENGTH -> WizChatQuestionId.T_VOLUME_MOBILITY
        WizChatQuestionId.T_VOLUME_MOBILITY -> WizChatQuestionId.T_EQUIPMENT
        WizChatQuestionId.T_EQUIPMENT -> WizChatQuestionId.T_DAYS
        WizChatQuestionId.T_DAYS -> WizChatQuestionId.T_WEEKDAYS
        WizChatQuestionId.T_WEEKDAYS -> WizChatQuestionId.T_TIME
        WizChatQuestionId.T_TIME -> WizChatQuestionId.T_TRAINING_MAX
        WizChatQuestionId.T_TRAINING_MAX -> WizChatQuestionId.T_PLAN
        WizChatQuestionId.T_PLAN -> if (context.trainingPlanSelected || context.programRouteLater) WizChatQuestionId.T_REVIEW else WizChatQuestionId.T_PLAN
        WizChatQuestionId.T_REVIEW -> if (context.includeNutrition) WizChatQuestionId.N_START else WizChatQuestionId.R_START
        WizChatQuestionId.N_START -> when {
            !context.includeNutrition -> WizChatQuestionId.R_START
            context.nutritionProfessional -> WizChatQuestionId.N_RESULT
            context.nutritionStarted -> WizChatQuestionId.N_SEX
            else -> WizChatQuestionId.R_START
        }
        WizChatQuestionId.N_SEX -> WizChatQuestionId.N_ELIGIBILITY
        WizChatQuestionId.N_ELIGIBILITY -> WizChatQuestionId.N_DIRECTION
        WizChatQuestionId.N_DIRECTION -> WizChatQuestionId.N_ACTIVITY
        WizChatQuestionId.N_ACTIVITY -> WizChatQuestionId.N_RESULT
        WizChatQuestionId.N_RESULT -> WizChatQuestionId.R_START
        WizChatQuestionId.R_START -> {
            val action = context.ringsAction?.trim()?.lowercase().orEmpty()
            if (action == "omit" || action == "keep" || action == "preserve" || action == "remove" || action.contains("dejar") || action.contains("conservar") || action.contains("quitar") || action.contains("uncalibrated")) WizChatQuestionId.REVIEW else WizChatQuestionId.R_RECENT
        }
        WizChatQuestionId.R_RECENT -> when {
            context.recentTraining == true -> WizChatQuestionId.R_SESSIONS
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
