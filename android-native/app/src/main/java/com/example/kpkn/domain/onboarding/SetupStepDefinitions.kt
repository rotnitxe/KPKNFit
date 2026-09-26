package com.example.kpkn.domain.onboarding

import com.example.kpkn.data.models.DISCOMFORT_CATALOG
import kotlinx.serialization.Serializable

/**
 * Traditional setup metadata: every step of the four mandatory blocks plus the
 * final review. Question steps carry a big natural question as [SetupStepDefinition.title]
 * (¿Cómo te llamas?, ¿Cuál es tu altura?, …); milestones, result previews and the
 * closing review keep plain labels as an exception. Subtitles carry useful
 * information for the user, never UI instructions.
 *
 * This catalog is deliberately independent of [WizChatGraph]: the legacy graph
 * keeps driving the old conversational screens until they are retired. Here each
 * step declares the control the UI must render, so the presentation layer never
 * has to guess what a step means ([SetupStepDefinition.control]).
 *
 * Legacy reads and migration live in the same model:
 * - [SetupStepDefinition.legacyQuestion] is the WizChat question whose value
 *   populates this step when an old draft is migrated (nullable for new steps).
 * - [SetupStepDefinition.legacyValueMap] translates the legacy Spanish labels
 *   into the stable values used here; values that do not appear are never
 *   copied (they stay PENDING instead of being guessed).
 * - [SetupStepDefinition.legacyOnly] marks steps that must survive for
 *   deserialization and read-only rendering but are outside the productive
 *   route (gender identity, home-equipment detail, nutrition sex, rings action).
 * - [SetupStepDefinition.legacyQuestionRenders] is false when the legacy
 *   question exists but must NOT be rendered as this step's copy (e.g. N_SEX
 *   value migrates to EQUATION_SEX, whose copy is the new basic block one).
 *
 * An answer only counts as explicit when its legacy label is mapped here (or the
 * step declares [SetupStepDefinition.legacyAcceptsIdValue] for generated candidate
 * ids); any other non-blank label stays PENDING.
 *
 * No android.* or Compose types here: this file is pure Kotlin.
 */
@Serializable
enum class SetupInventoryGroup {
    BARBELL,
    PLATES,
    DUMBBELLS,
    KETTLEBELLS,
    MACHINES,
}

/** Identifiable UI control a step needs. Real controls are built by the UI owner. */
enum class SetupControlKind {
    /** Free short text (name). */
    TEXT,
    /** A single number with an optional unit and range. */
    NUMBER,
    /** Body-fat physique selector: measured value or explicit visual estimation
     *  on a male/female figure; unknown may omit the step. */
    PHYSIQUE,
    /** One option from a closed list. */
    SINGLE_CHOICE,
    /** Several options from a closed list (weekdays, eligibility, discomforts). */
    MULTI_CHOICE,
    /** Plan route: recommended or protocol. No later/manual option. */
    ROUTE_CHOICE,
    /** Equipment environment picker; drives which inventory groups appear. */
    ENVIRONMENT_CHOICE,
    /** Inventory picker: max two related inputs per screen, sub-editor rows allowed. */
    INVENTORY_PICKER,
    /** Points bag: 5 points total, max 2 per muscle; only reorders exercises. */
    POINT_BUDGET,
    /** Split picker/editor (recommended split plus editable pattern). */
    SPLIT_EDITOR,
    /** Plan candidates / protocol cards. */
    PLAN_PICKER,
    /** Marks editor rows (squat / bench / deadlift or sub-editor rows). */
    MARKS_EDITOR,
    /** Yes/no toggle (knows marks, autoregulation on). */
    TOGGLE,
    /** Explicit confirmation of a derived behavior (autoregulation). */
    AUTO_CONFIRM,
    /** Manual macro editor: calories+protein and carbs+fat are separate screens. */
    MANUAL_MACROS,
    /** Rows editor: weigh-ins (date+weight) and nutrition history context. */
    EDITOR_ROWS,
    /** Computed preview (nutrition / rings result, plan review). */
    RESULT_PREVIEW,
    /** Block milestone: summary and confirmation of everything before it. */
    MILESTONE,
    /** Final editable review and activation. */
    REVIEW,
}

data class SetupNumericRange(
    val min: Double,
    val max: Double,
    val unit: String? = null,
)

/** One option of a step: STABLE value (what engines receive) + label for the UI. */
data class SetupOptionDefinition(
    val value: String,
    val label: String,
)

data class SetupStepDefinition(
    val id: SetupStepId,
    val block: SetupWizardBlock,
    val kind: SetupStepKind,
    /** Big natural question for question steps; plain label for results/milestones. */
    val title: String,
    /** Subtitle only when it adds user-facing context, never UI instructions. */
    val subtitle: String? = null,
    val control: SetupControlKind,
    val options: List<SetupOptionDefinition> = emptyList(),
    val unit: String? = null,
    val range: SetupNumericRange? = null,
    /** PRIORITIES: total points available in the bag. */
    val budget: Int? = null,
    /** PRIORITIES: maximum points per muscle. */
    val maxPerItem: Int? = null,
    /** INVENTORY/MACROS: maximum related inputs shown at once on the screen (cap 2). */
    val maxRelatedInputs: Int = 2,
    /** Multi-choice values that are exclusive (can not be combined with others). */
    val exclusiveValues: Set<String> = emptySet(),
    val allowSkip: Boolean = false,
    /**
     * Legacy WizChat question whose value populates this step (null => new step).
     * PLAN-style steps answer with a generated candidate id that is not a mapped
     * label: declaring [legacyAcceptsIdValue] treats any non-blank answered value
     * as explicit because the value itself is the stable id.
     */
    val legacyQuestion: WizChatQuestionId? = null,
    /** Legacy Spanish label -> stable value; unmapped legacy answers stay pending. */
    val legacyValueMap: Map<String, String> = emptyMap(),
    /** False when [legacyQuestion] must not render as this step's copy. */
    val legacyQuestionRenders: Boolean = true,
    /** False when the legacy answer is a mapped label or a known candidate id. */
    val legacyAcceptsIdValue: Boolean = false,
    /** Only deserialization/read compatibility; never in a productive route. */
    val legacyOnly: Boolean = false,
) {
    fun option(value: String): SetupOptionDefinition? = options.firstOrNull { it.value == value }

    /** Stable value a legacy label maps to, or null when it must stay pending. */
    fun migratedValue(legacyLabel: String?): String? =
        legacyLabel?.let { legacyValueMap[it] }
}

// ---------------------------------------------------------------------------
// Shared option sets
// ---------------------------------------------------------------------------

private val weekdaysOptions = listOf(
    "1" to "Lunes", "2" to "Martes", "3" to "Miércoles", "4" to "Jueves",
    "5" to "Viernes", "6" to "Sábado", "7" to "Domingo",
).map { (v, l) -> SetupOptionDefinition(v, l) }

private val doorsOptions = (1..6).map { n ->
    SetupOptionDefinition("$n", if (n == 1) "1 día" else "$n días")
}

private val intensityLevels = listOf(
    "1" to "Descansados", "2" to "Algo cargados", "3" to "Moderadamente cargados",
    "4" to "Cargados", "5" to "Muy cargados",
).map { (v, l) -> SetupOptionDefinition(v, l) }

private val energyLevels = listOf(
    "1" to "Con energía", "2" to "Bien", "3" to "Intermedia", "4" to "Baja", "5" to "Agotado",
).map { (v, l) -> SetupOptionDefinition(v, l) }

private val structureLevels = listOf(
    "1" to "Descansada", "2" to "Bien", "3" to "Intermedia", "4" to "Cargada", "5" to "Muy cargada",
).map { (v, l) -> SetupOptionDefinition(v, l) }

/** "No lo sé" keeps feelings answerable without fabricating a 0 level. */
private val feelingsUnknown = SetupOptionDefinition("unknown", "No lo sé")

private fun discomforts(): List<SetupOptionDefinition> = buildList {
    add(SetupOptionDefinition("none", "Sin molestias"))
    DISCOMFORT_CATALOG.filterNot { it.id == "none" }.forEach { add(SetupOptionDefinition(it.id, it.label)) }
    add(SetupOptionDefinition("omit", "Prefiero omitirlo"))
}

/** Legacy labels of every real discomfort so answers migrate as explicit values. */
private fun discomfortLegacyMap(): Map<String, String> = buildMap {
    put("Sin molestias", "none")
    put("Prefiero omitirlo", "omit")
    DISCOMFORT_CATALOG.filterNot { it.id == "none" }.forEach { put(it.label, it.id) }
}

/** Canonical muscles understood by the order engine (SimpleCyclePersonalizer). */
val ORDER_MUSCLE_OPTIONS: List<SetupOptionDefinition> = listOf(
    "Pectorales", "Dorsales", "Deltoides", "Bíceps", "Tríceps", "Cuádriceps",
    "Isquiosurales", "Glúteos", "Pantorrillas", "Abdomen", "Trapecio", "Erectores Espinales",
).map { SetupOptionDefinition(it, it) }

object SetupStepDefinitions {

    private fun opt(vararg pairs: Pair<String, String>) = pairs.map { (v, l) -> SetupOptionDefinition(v, l) }

    val definitions: Map<SetupStepId, SetupStepDefinition> = listOf(
        // -------------------------------------------------------------------
        // Bloque 1: Datos básicos
        // -------------------------------------------------------------------
        SetupStepDefinition(
            id = SetupStepId.NAME, block = SetupWizardBlock.BASICS, kind = SetupStepKind.QUESTION,
            title = "¿Cómo te llamas?", control = SetupControlKind.TEXT,
            allowSkip = true, legacyQuestion = WizChatQuestionId.P_NAME,
        ),
        SetupStepDefinition(
            id = SetupStepId.AGE, block = SetupWizardBlock.BASICS, kind = SetupStepKind.QUESTION,
            title = "¿Qué edad tienes?", control = SetupControlKind.NUMBER, unit = "años",
            range = SetupNumericRange(13.0, 100.0, "años"),
            legacyQuestion = WizChatQuestionId.P_AGE,
        ),
        SetupStepDefinition(
            id = SetupStepId.HEIGHT, block = SetupWizardBlock.BASICS, kind = SetupStepKind.QUESTION,
            title = "¿Cuál es tu altura?", control = SetupControlKind.NUMBER, unit = "cm",
            range = SetupNumericRange(100.0, 250.0, "cm"),
            legacyQuestion = WizChatQuestionId.P_HEIGHT,
        ),
        SetupStepDefinition(
            id = SetupStepId.WEIGHT, block = SetupWizardBlock.BASICS, kind = SetupStepKind.QUESTION,
            title = "¿Cuál es tu peso?", control = SetupControlKind.NUMBER, unit = "kg",
            range = SetupNumericRange(20.0, 500.0, "kg"),
            legacyQuestion = WizChatQuestionId.P_WEIGHT,
        ),
        SetupStepDefinition(
            id = SetupStepId.EQUATION_SEX, block = SetupWizardBlock.BASICS, kind = SetupStepKind.QUESTION,
            title = "¿Cuál es tu sexo de cálculo?",
            subtitle = "Solo se usa para calcular tu gasto energético. No sustituye tu género.",
            control = SetupControlKind.SINGLE_CHOICE,
            options = opt(
                "female" to "Femenino", "male" to "Masculino",
                // Desconocimiento EXPLÍCITO: permitido en nutrición manual
                // ("yo traigo mis números" / solo registro); la cadena
                // automática lo sigue tratando como dato faltante.
                "unknown" to "No lo sé",
            ),
            // El valor migra desde la pregunta de nutrición legacy N_SEX cuando
            // es explícita; "Prefiero no responder" No migra y queda pendiente.
            legacyQuestion = WizChatQuestionId.N_SEX,
            legacyValueMap = mapOf("Femenino" to "female", "Masculino" to "male"),
            legacyQuestionRenders = false,
        ),
        SetupStepDefinition(
            id = SetupStepId.BODY_FAT, block = SetupWizardBlock.BASICS, kind = SetupStepKind.QUESTION,
            title = "¿Cuál es tu grasa corporal?",
            subtitle = "Usa la estimación visual con la figura. Si tienes una medición, escríbela.",
            control = SetupControlKind.PHYSIQUE, unit = "%",
            range = SetupNumericRange(3.0, 60.0, "%"),
            options = opt(
                "measured" to "Lo tengo medido",
                "visual" to "Estimación visual con la figura",
                "unknown" to "No lo sé / omitir",
            ),
            allowSkip = true,
        ),
        SetupStepDefinition(
            id = SetupStepId.MILESTONE_BASICS, block = SetupWizardBlock.BASICS, kind = SetupStepKind.MILESTONE,
            title = "Datos básicos", control = SetupControlKind.MILESTONE,
        ),

        // -------------------------------------------------------------------
        // Bloque 2: Entreno
        // -------------------------------------------------------------------
        SetupStepDefinition(
            id = SetupStepId.EXPERIENCE, block = SetupWizardBlock.TRAINING, kind = SetupStepKind.QUESTION,
            title = "¿Cuánta experiencia tienes?", control = SetupControlKind.SINGLE_CHOICE,
            options = opt(
                "new" to "Estoy empezando",
                "returning" to "Estoy volviendo",
                "intermediate" to "Ya entreno con constancia",
                "advanced" to "Tengo experiencia",
            ),
            legacyQuestion = WizChatQuestionId.P_EXPERIENCE,
            legacyValueMap = mapOf(
                "Estoy empezando" to "new", "Estoy volviendo" to "returning",
                "Ya entreno con constancia" to "intermediate", "Tengo experiencia" to "advanced",
            ),
        ),
        SetupStepDefinition(
            id = SetupStepId.ROUTE, block = SetupWizardBlock.TRAINING, kind = SetupStepKind.QUESTION,
            title = "¿Cómo quieres empezar?",
            subtitle = "Elige entre un plan recomendado o un protocolo de catálogo.",
            control = SetupControlKind.ROUTE_CHOICE,
            options = opt(
                "recommended" to "Qué me lo recomiendes",
                "protocol" to "Elegir un protocolo",
            ),
            legacyQuestion = WizChatQuestionId.T_ROUTE,
            legacyValueMap = mapOf(
                "Recomiéndame un plan" to "recommended",
                "Elegir un protocolo" to "protocol",
                // "Crear desde cero" / "Lo decidiré después" NO migran (quedan pendientes).
            ),
        ),
        SetupStepDefinition(
            id = SetupStepId.GOAL, block = SetupWizardBlock.TRAINING, kind = SetupStepKind.QUESTION,
            title = "¿Cuál es tu objetivo?", control = SetupControlKind.SINGLE_CHOICE,
            options = opt(
                "strength" to "Fuerza",
                "muscle" to "Músculo",
                "strength_muscle" to "Fuerza y músculo",
                "health" to "Salud y condición",
                "mixed" to "Fuerza + cardio",
            ),
            legacyQuestion = WizChatQuestionId.T_GOAL,
            legacyValueMap = mapOf(
                "Fuerza" to "strength", "Músculo" to "muscle", "Fuerza y músculo" to "strength_muscle",
                "Salud y condición" to "health", "Fuerza + cardio" to "mixed",
            ),
        ),
        SetupStepDefinition(
            id = SetupStepId.STYLE, block = SetupWizardBlock.TRAINING, kind = SetupStepKind.QUESTION,
            title = "¿Qué prefieres ganar?", control = SetupControlKind.SINGLE_CHOICE,
            options = opt(
                "powerlifter" to "Fuerza",
                "bodybuilder" to "Músculo",
                "powerbuilder" to "Ambos",
            ),
            legacyQuestion = WizChatQuestionId.T_STYLE,
            legacyValueMap = mapOf("Fuerza" to "powerlifter", "Músculo" to "bodybuilder", "Ambos" to "powerbuilder"),
        ),
        SetupStepDefinition(
            id = SetupStepId.VOLUME_TECHNIQUE, block = SetupWizardBlock.TRAINING, kind = SetupStepKind.QUESTION,
            title = "¿Cómo sientes tu técnica?", control = SetupControlKind.SINGLE_CHOICE,
            options = opt("1" to "Aprendiendo", "2" to "Bastante estable", "3" to "Muy sólida"),
            legacyQuestion = WizChatQuestionId.T_VOLUME_TECHNIQUE,
            legacyValueMap = mapOf("Aprendiendo" to "1", "Bastante estable" to "2", "Muy sólida" to "3"),
        ),
        SetupStepDefinition(
            id = SetupStepId.VOLUME_CONSISTENCY, block = SetupWizardBlock.TRAINING, kind = SetupStepKind.QUESTION,
            title = "¿Qué tan constante has sido?", control = SetupControlKind.SINGLE_CHOICE,
            options = opt("1" to "Irregular", "2" to "Bastante constante", "3" to "Muy constante"),
            legacyQuestion = WizChatQuestionId.T_VOLUME_CONSISTENCY,
            legacyValueMap = mapOf("Irregular" to "1", "Bastante constante" to "2", "Muy constante" to "3"),
        ),
        SetupStepDefinition(
            id = SetupStepId.VOLUME_STRENGTH, block = SetupWizardBlock.TRAINING, kind = SetupStepKind.QUESTION,
            title = "¿Cómo ves tu fuerza hoy?", control = SetupControlKind.SINGLE_CHOICE,
            options = opt("1" to "Inicial", "2" to "Intermedia", "3" to "Avanzada"),
            legacyQuestion = WizChatQuestionId.T_VOLUME_STRENGTH,
            legacyValueMap = mapOf("Inicial" to "1", "Intermedia" to "2", "Avanzada" to "3"),
        ),
        SetupStepDefinition(
            id = SetupStepId.VOLUME_MOBILITY, block = SetupWizardBlock.TRAINING, kind = SetupStepKind.QUESTION,
            title = "¿Cómo está tu movilidad?", control = SetupControlKind.SINGLE_CHOICE,
            options = opt("1" to "Limitada", "2" to "Suficiente", "3" to "Amplia"),
            legacyQuestion = WizChatQuestionId.T_VOLUME_MOBILITY,
            legacyValueMap = mapOf("Limitada" to "1", "Suficiente" to "2", "Amplia" to "3"),
        ),
        SetupStepDefinition(
            id = SetupStepId.EQUIPMENT, block = SetupWizardBlock.TRAINING, kind = SetupStepKind.QUESTION,
            title = "¿Dónde entrenas?",
            subtitle = "En el gimnasio no hace falta inventariar discos ni máquinas. Después marcas solo lo que sí tienes a mano.",
            control = SetupControlKind.ENVIRONMENT_CHOICE,
            options = opt(
                "gym" to "Gimnasio completo",
                "machines" to "Principalmente máquinas",
                "home" to "Entreno en casa",
                "none" to "Sin material",
            ),
            legacyQuestion = WizChatQuestionId.T_EQUIPMENT,
            legacyValueMap = mapOf(
                "Gimnasio completo" to "gym", "Principalmente máquinas" to "machines",
                "Entreno en casa" to "home", "Sin material" to "none",
            ),
        ),
        SetupStepDefinition(
            id = SetupStepId.INVENTORY_BARBELL, block = SetupWizardBlock.TRAINING, kind = SetupStepKind.QUESTION,
            title = "¿Qué barra y rack tienes?",
            subtitle = "El peso de la barra (suele ser 20 kg) y qué soportes o rack tienes.",
            control = SetupControlKind.INVENTORY_PICKER, maxRelatedInputs = 2,
            options = opt(
                "barbell" to "Barra",
                "rack" to "Soportes / rack",
                "bench" to "Banco plano",
            ),
        ),
        SetupStepDefinition(
            id = SetupStepId.INVENTORY_PLATES, block = SetupWizardBlock.TRAINING, kind = SetupStepKind.QUESTION,
            title = "¿Qué discos tienes?",
            subtitle = "Anota el peso de cada disco y cuántos tienes; con dos por lado suele bastar.",
            control = SetupControlKind.INVENTORY_PICKER, maxRelatedInputs = 2,
            options = opt(
                "small_plates" to "Discos pequeños (≤ 10 kg)",
                "heavy_plates" to "Discos pesados (≥ 15 kg)",
                "increments" to "Incrementos (1.25–2.5 kg)",
            ),
        ),
        SetupStepDefinition(
            id = SetupStepId.INVENTORY_DUMBBELLS, block = SetupWizardBlock.TRAINING, kind = SetupStepKind.QUESTION,
            title = "¿Qué mancuernas tienes?",
            subtitle = "Peso de cada mancuerna; las fijas suelen venir por parejas.",
            control = SetupControlKind.INVENTORY_PICKER, maxRelatedInputs = 2,
            options = opt(
                "adjustable" to "Mancuernas ajustables",
                "up_to_12" to "Fijas hasta 12 kg",
                "up_to_20" to "Fijas hasta 20 kg",
                "over_20" to "Fijas de más de 20 kg",
            ),
        ),
        SetupStepDefinition(
            id = SetupStepId.INVENTORY_KETTLEBELLS, block = SetupWizardBlock.TRAINING, kind = SetupStepKind.QUESTION,
            title = "¿Qué kettlebells tienes?",
            subtitle = "Peso de cada kettlebell; normalmente basta una de cada peso.",
            control = SetupControlKind.INVENTORY_PICKER, maxRelatedInputs = 2,
            options = opt(
                "kettlebell_8" to "Kettlebell 8 kg",
                "kettlebell_12" to "Kettlebell 12 kg",
                "kettlebell_16" to "Kettlebell 16 kg",
                "kettlebell_20" to "Kettlebell 20 kg",
                "kettlebell_24" to "Kettlebell 24 kg",
                "kettlebell_32" to "Kettlebell 32 kg",
            ),
        ),
        SetupStepDefinition(
            id = SetupStepId.INVENTORY_MACHINES, block = SetupWizardBlock.TRAINING, kind = SetupStepKind.QUESTION,
            title = "¿Qué máquinas y poleas tienes?",
            subtitle = "Nombres aproximados sirven; con la máquina y su rango te basta.",
            control = SetupControlKind.INVENTORY_PICKER, maxRelatedInputs = 2,
            options = opt(
                "cable" to "Polea / banco de poleas",
                "leg_press" to "Prensa de piernas",
                "hack_squat" to "Hack squat",
                "upper_machines" to "Máquinas de tren superior",
            ),
        ),
        SetupStepDefinition(
            id = SetupStepId.AVAILABILITY,
            block = SetupWizardBlock.TRAINING,
            kind = SetupStepKind.QUESTION,
            title = "¿Qué material tienes disponible?",
            subtitle = "Marca categorías, no kilos ni cantidades. Si no hay nada, el plan usa peso corporal.",
            control = SetupControlKind.MULTI_CHOICE,
            options = opt(
                "BARBELL" to "Barras olímpicas",
                "DUMBBELLS" to "Mancuernas",
                "KETTLEBELL" to "Kettlebells",
                "MACHINES" to "Máquinas",
                "CABLE" to "Poleas",
                "SMITH_MACHINE" to "Multipower",
                "BAND" to "Bandas",
                "PULL_UP_BAR" to "Barra de dominadas",
                "SUPPORT" to "Bancos y soportes",
                "BALL" to "Balón",
                "CARDIO" to "Cardio",
                "bodyweight_only" to "Solo peso corporal",
            ),
            exclusiveValues = setOf("bodyweight_only"),
        ),
        SetupStepDefinition(
            id = SetupStepId.DAYS, block = SetupWizardBlock.TRAINING, kind = SetupStepKind.QUESTION,
            title = "¿Cuántos días entrenas a la semana?", control = SetupControlKind.SINGLE_CHOICE,
            options = doorsOptions,
            legacyQuestion = WizChatQuestionId.T_DAYS,
            legacyValueMap = (1..6).associate { "$it" to "$it" },
        ),
        SetupStepDefinition(
            id = SetupStepId.WEEKDAYS, block = SetupWizardBlock.TRAINING, kind = SetupStepKind.QUESTION,
            title = "¿Qué días quieres entrenar?", control = SetupControlKind.MULTI_CHOICE,
            options = weekdaysOptions,
            legacyQuestion = WizChatQuestionId.T_WEEKDAYS,
            legacyValueMap = mapOf(
                "Lunes" to "1", "Martes" to "2", "Miércoles" to "3", "Jueves" to "4",
                "Viernes" to "5", "Sábado" to "6", "Domingo" to "7",
            ),
        ),
        SetupStepDefinition(
            id = SetupStepId.SESSION_TIME, block = SetupWizardBlock.TRAINING, kind = SetupStepKind.QUESTION,
            title = "¿Cuánto tiempo tienes por sesión?", control = SetupControlKind.NUMBER, unit = "min",
            range = SetupNumericRange(20.0, 100.0, "min"),
            legacyQuestion = WizChatQuestionId.T_TIME,
        ),
        SetupStepDefinition(
            id = SetupStepId.CARDIO_TYPE, block = SetupWizardBlock.TRAINING, kind = SetupStepKind.QUESTION,
            title = "¿Qué cardio quieres incluir?", control = SetupControlKind.SINGLE_CHOICE,
            options = opt(
                "WALK" to "Caminar",
                "RUN_OUTDOOR" to "Correr al aire libre",
                "BIKE_OUTDOOR" to "Bicicleta al aire libre",
            ),
            legacyQuestion = WizChatQuestionId.T_CARDIO_TYPE,
            legacyValueMap = mapOf(
                "Caminar" to "WALK", "Correr al aire libre" to "RUN_OUTDOOR",
                "Bicicleta al aire libre" to "BIKE_OUTDOOR",
            ),
        ),
        SetupStepDefinition(
            id = SetupStepId.CARDIO_TIME, block = SetupWizardBlock.TRAINING, kind = SetupStepKind.QUESTION,
            title = "¿Cuántos minutos de cardio?", control = SetupControlKind.SINGLE_CHOICE,
            options = opt("10" to "10 min", "15" to "15 min", "20" to "20 min", "30" to "30 min"),
            legacyQuestion = WizChatQuestionId.T_CARDIO_TIME,
            legacyValueMap = mapOf("10 min" to "10", "15 min" to "15", "20 min" to "20", "30 min" to "30"),
        ),
        SetupStepDefinition(
            id = SetupStepId.PRIORITIES, block = SetupWizardBlock.TRAINING, kind = SetupStepKind.QUESTION,
            title = "¿Qué te gusta más entrenar o quieres mejorar?",
            subtitle = "Puedes elegir todo el cuerpo o un enfoque. Solo cambia el orden de los ejercicios.",
            control = SetupControlKind.POINT_BUDGET,
            options = ORDER_MUSCLE_OPTIONS,
            budget = 5,
            maxPerItem = 2,
        ),
        SetupStepDefinition(
            id = SetupStepId.SPLIT, block = SetupWizardBlock.TRAINING, kind = SetupStepKind.QUESTION,
            title = "¿Cómo quieres repartir la semana?",
            subtitle = "Una propuesta destacada, alternativas para mirar y el resto con buscador.",
            control = SetupControlKind.SPLIT_EDITOR,
            options = opt("recommended" to "Recomendado para ti", "custom" to "Personalizado"),
        ),
        SetupStepDefinition(
            id = SetupStepId.PLAN, block = SetupWizardBlock.TRAINING, kind = SetupStepKind.QUESTION,
            title = "¿Qué plan eliges?",
            subtitle = "El candidato aparece sin necesidad de marcas completas: las marcas solo refinan cargas.",
            control = SetupControlKind.PLAN_PICKER,
            legacyQuestion = WizChatQuestionId.T_PLAN,
            // La respuesta legacy es el id del candidato generado, no una etiqueta mapeada.
            legacyAcceptsIdValue = true,
        ),
        SetupStepDefinition(
            id = SetupStepId.TRAINING_MAX, block = SetupWizardBlock.TRAINING, kind = SetupStepKind.QUESTION,
            title = "¿Conoces tus marcas?",
            subtitle = "Con una basta; sin marcas el plan sigue siendo válido.",
            control = SetupControlKind.TOGGLE,
            options = opt("yes" to "Sí, conozco mis marcas", "no" to "Todavía no"),
            legacyQuestion = WizChatQuestionId.T_TRAINING_MAX,
            legacyValueMap = mapOf("Conozco mis marcas" to "yes", "Todavía no" to "no"),
        ),
        SetupStepDefinition(
            id = SetupStepId.TRAINING_MARKS, block = SetupWizardBlock.TRAINING, kind = SetupStepKind.QUESTION,
            title = "¿Cuáles son tus marcas?",
            subtitle = "Sentadilla, banca y peso muerto; con una basta para empezar.",
            control = SetupControlKind.MARKS_EDITOR,
            options = opt("squat" to "Sentadilla", "bench" to "Banca", "deadlift" to "Peso muerto"),
            legacyQuestion = WizChatQuestionId.T_MARKS,
        ),
        SetupStepDefinition(
            id = SetupStepId.AUTOREGULATION, block = SetupWizardBlock.TRAINING, kind = SetupStepKind.QUESTION,
            title = "¿Ajusto tu semana automáticamente?",
            subtitle = "Si lo activas, KPKN ajusta series y pesos cada semana según tu respuesta.",
            control = SetupControlKind.TOGGLE,
            options = opt("on" to "Sí, ajusta mi semana", "off" to "No, lo controlo yo"),
        ),
        SetupStepDefinition(
            id = SetupStepId.AUTOREGULATION_CONFIRM, block = SetupWizardBlock.TRAINING, kind = SetupStepKind.QUESTION,
            title = "¿Confirmas el ajuste automático?",
            subtitle = "Los cambios se aplican por confirmación y siempre puedes revertirlos. No cambia tus marcas declaradas.",
            control = SetupControlKind.AUTO_CONFIRM,
            options = opt("confirmed" to "Confirmado", "review_only" to "Solo revisar"),
        ),
        SetupStepDefinition(
            id = SetupStepId.WARMUPS, block = SetupWizardBlock.TRAINING, kind = SetupStepKind.QUESTION,
            title = "¿Qué calentamientos quieres?",
            subtitle = "Se añaden al inicio de cada ejercicio.",
            control = SetupControlKind.SINGLE_CHOICE,
            options = opt(
                "standard" to "Estándar",
                "light" to "Ligeros y cortos",
                "none" to "Sin calentamiento extra",
            ),
        ),
        SetupStepDefinition(
            id = SetupStepId.TRAINING_REVIEW, block = SetupWizardBlock.TRAINING, kind = SetupStepKind.QUESTION,
            title = "Revisión del plan", control = SetupControlKind.RESULT_PREVIEW,
            legacyQuestion = WizChatQuestionId.T_REVIEW,
        ),
        SetupStepDefinition(
            id = SetupStepId.MILESTONE_TRAINING, block = SetupWizardBlock.TRAINING, kind = SetupStepKind.MILESTONE,
            title = "Entreno", control = SetupControlKind.MILESTONE,
        ),

        // -------------------------------------------------------------------
        // Bloque 3: Nutrición
        // -------------------------------------------------------------------
        SetupStepDefinition(
            id = SetupStepId.NUTRITION_START, block = SetupWizardBlock.NUTRITION, kind = SetupStepKind.QUESTION,
            title = "¿Cómo quieres preparar tu nutrición?",
            subtitle = "Puedes calcular tus referencias, traer tus números o quedarte solo con el registro de comidas.",
            control = SetupControlKind.SINGLE_CHOICE,
            options = opt(
                "automatic" to "Calcula mis referencias",
                "self_defined" to "Yo traigo mis números",
                "tracking_only" to "Solo registrar comidas",
            ),
            legacyQuestion = WizChatQuestionId.N_START,
            legacyValueMap = mapOf("Sí, preparar mis referencias" to "automatic"),
            // "Tengo indicaciones de un profesional" y "Lo haré después" no migran:
            // el modo profesional legacy sobrevive como nutritiónProfessional.
        ),
        SetupStepDefinition(
            id = SetupStepId.NUTRITION_ELIGIBILITY, block = SetupWizardBlock.NUTRITION, kind = SetupStepKind.QUESTION,
            title = "¿Hay algo que debamos tener en cuenta?",
            subtitle = "Condiciones de salud que puedan afectar a tu gasto energético.",
            control = SetupControlKind.MULTI_CHOICE,
            options = opt(
                "none" to "Ninguna de estas",
                "pregnancy" to "Embarazo",
                "lactation" to "Lactancia",
                "medical_restriction" to "Restricción médica relevante",
                "unknown" to "No lo sé / prefiero no responder",
            ),
            exclusiveValues = setOf("none", "unknown"),
            legacyQuestion = WizChatQuestionId.N_ELIGIBILITY,
            legacyValueMap = mapOf(
                "Ninguna de estas" to "none", "Embarazo" to "pregnancy",
                "Lactancia" to "lactation", "Restricción médica relevante" to "medical_restriction",
                "No lo sé / prefiero no responder" to "unknown",
            ),
        ),
        SetupStepDefinition(
            id = SetupStepId.NUTRITION_DIRECTION, block = SetupWizardBlock.NUTRITION, kind = SetupStepKind.QUESTION,
            title = "¿Hacia dónde quieres llevar tu alimentación?",
            subtitle = "Opcional: define si quieres definir, mantener o hacer volumen.",
            control = SetupControlKind.SINGLE_CHOICE,
            options = opt(
                "deficit" to "Definir",
                "maintenance" to "Mantener",
                "surplus" to "Volumen",
            ),
            allowSkip = true,
            legacyQuestion = WizChatQuestionId.N_DIRECTION,
            legacyValueMap = mapOf("Definir" to "deficit", "Mantener" to "maintenance", "Volumen" to "surplus"),
        ),
        SetupStepDefinition(
            id = SetupStepId.NUTRITION_RHYTHM, block = SetupWizardBlock.NUTRITION, kind = SetupStepKind.QUESTION,
            title = "¿A qué ritmo quieres llegar?",
            subtitle = "Suave, medio o rápido: el ritmo con el que quieres avanzar.",
            control = SetupControlKind.SINGLE_CHOICE,
            options = opt(
                "slow" to "Suave",
                "medium" to "Medio",
                "fast" to "Rápido",
            ),
        ),
        SetupStepDefinition(
            id = SetupStepId.NUTRITION_TARGET, block = SetupWizardBlock.NUTRITION, kind = SetupStepKind.QUESTION,
            title = "¿Cuál es tu peso objetivo?",
            subtitle = "Opcional: es tu peso meta personal, no una grasa objetivo.",
            control = SetupControlKind.NUMBER, unit = "kg",
            range = SetupNumericRange(20.0, 500.0, "kg"),
            allowSkip = true,
        ),
        SetupStepDefinition(
            id = SetupStepId.NUTRITION_HISTORY_CONTEXT, block = SetupWizardBlock.NUTRITION, kind = SetupStepKind.QUESTION,
            title = "¿Cómo ha ido tu peso?",
            subtitle = "Opcional: tendencia reciente y máximo anterior como contexto del plan; no son registros de pesaje.",
            control = SetupControlKind.EDITOR_ROWS,
            options = opt("trend" to "Tendencia reciente", "max_previous" to "Máximo anterior (kg)"),
            allowSkip = true,
        ),
        SetupStepDefinition(
            id = SetupStepId.NUTRITION_ACTIVITY, block = SetupWizardBlock.NUTRITION, kind = SetupStepKind.QUESTION,
            title = "¿Qué tan activo eres en el día a día?", control = SetupControlKind.SINGLE_CHOICE,
            options = opt(
                "INACTIVE" to "Tranquilo",
                "LOW_ACTIVE" to "Algo activo",
                "ACTIVE" to "Activo",
                "VERY_ACTIVE" to "Muy activo",
            ),
            legacyQuestion = WizChatQuestionId.N_ACTIVITY,
            legacyValueMap = mapOf(
                "Tranquilo" to "INACTIVE", "Algo activo" to "LOW_ACTIVE",
                "Activo" to "ACTIVE", "Muy activo" to "VERY_ACTIVE",
            ),
        ),
        SetupStepDefinition(
            id = SetupStepId.NUTRITION_MANUAL_CALORIES, block = SetupWizardBlock.NUTRITION, kind = SetupStepKind.QUESTION,
            title = "¿Cuántas calorías y proteína tomas?",
            subtitle = "Tus valores reales, nunca una estimación.",
            control = SetupControlKind.MANUAL_MACROS, maxRelatedInputs = 2,
            options = opt("calories" to "Calorías (kcal)", "protein" to "Proteína (g)"),
        ),
        SetupStepDefinition(
            id = SetupStepId.NUTRITION_MANUAL_CARBS_FAT, block = SetupWizardBlock.NUTRITION, kind = SetupStepKind.QUESTION,
            title = "¿Cuántos hidratos y grasas tomas?",
            subtitle = "Tus valores reales, nunca una estimación.",
            control = SetupControlKind.MANUAL_MACROS, maxRelatedInputs = 2,
            options = opt("carbs" to "Hidratos (g)", "fat" to "Grasas (g)"),
        ),
        SetupStepDefinition(
            id = SetupStepId.NUTRITION_DISTRIBUTION, block = SetupWizardBlock.NUTRITION, kind = SetupStepKind.QUESTION,
            title = "¿Cómo repartes tu semana?",
            control = SetupControlKind.SINGLE_CHOICE,
            options = opt(
                "uniform" to "Uniforme todos los días",
                "variable" to "Variable según tu calendario",
            ),
        ),
        SetupStepDefinition(
            id = SetupStepId.NUTRITION_WEIGH_INS, block = SetupWizardBlock.NUTRITION, kind = SetupStepKind.QUESTION,
            title = "¿Quieres añadir tus últimos pesajes?",
            subtitle = "Opcional. Solo datos reales con fecha; la tendencia se declara aparte, nunca se inventa de aquí.",
            control = SetupControlKind.EDITOR_ROWS,
            allowSkip = true,
        ),
        SetupStepDefinition(
            id = SetupStepId.NUTRITION_RESULT, block = SetupWizardBlock.NUTRITION, kind = SetupStepKind.QUESTION,
            title = "Tus referencias", control = SetupControlKind.RESULT_PREVIEW,
            legacyQuestion = WizChatQuestionId.N_RESULT, legacyQuestionRenders = false,
        ),
        SetupStepDefinition(
            id = SetupStepId.MILESTONE_NUTRITION, block = SetupWizardBlock.NUTRITION, kind = SetupStepKind.MILESTONE,
            title = "Nutrición", control = SetupControlKind.MILESTONE,
        ),

        // -------------------------------------------------------------------
        // Bloque 4: RINGS
        // -------------------------------------------------------------------
        SetupStepDefinition(
            id = SetupStepId.RINGS_RECENT, block = SetupWizardBlock.RINGS, kind = SetupStepKind.QUESTION,
            title = "¿Entrenaste en los últimos 7 días?",
            subtitle = "«No lo sé» no es lo mismo que no haber entrenado.",
            control = SetupControlKind.SINGLE_CHOICE,
            options = opt("yes" to "Sí", "no" to "No", "unknown" to "No lo sé"),
            legacyQuestion = WizChatQuestionId.R_RECENT,
            legacyValueMap = mapOf("Sí" to "yes", "No" to "no", "No lo sé" to "unknown"),
        ),
        SetupStepDefinition(
            id = SetupStepId.RINGS_SESSIONS, block = SetupWizardBlock.RINGS, kind = SetupStepKind.QUESTION,
            title = "¿Cuántas sesiones hiciste?", control = SetupControlKind.SINGLE_CHOICE,
            options = (1..7).map { SetupOptionDefinition("$it", "$it") },
            legacyQuestion = WizChatQuestionId.R_SESSIONS,
            legacyValueMap = (1..7).associate { "$it" to "$it" },
        ),
        SetupStepDefinition(
            id = SetupStepId.RINGS_RECENCY, block = SetupWizardBlock.RINGS, kind = SetupStepKind.QUESTION,
            title = "¿Cuándo fue tu última sesión?", control = SetupControlKind.SINGLE_CHOICE,
            options = listOf(
                SetupOptionDefinition("0", "Hoy"), SetupOptionDefinition("1", "Ayer"),
            ) + (2..6).map { SetupOptionDefinition("$it", "Hace $it días") },
            legacyQuestion = WizChatQuestionId.R_RECENCY,
            legacyValueMap = buildMap {
                put("Hoy", "0"); put("Ayer", "1")
                (2..6).forEach { put("Hace $it días", "$it") }
            },
        ),
        SetupStepDefinition(
            id = SetupStepId.RINGS_ACTIVITY, block = SetupWizardBlock.RINGS, kind = SetupStepKind.QUESTION,
            title = "¿Qué predominó en tus sesiones?", control = SetupControlKind.SINGLE_CHOICE,
            options = opt("STRENGTH" to "Fuerza", "CARDIO" to "Cardio", "MIXED" to "Mixta"),
            legacyQuestion = WizChatQuestionId.R_ACTIVITY,
            legacyValueMap = mapOf("Fuerza" to "STRENGTH", "Cardio" to "CARDIO", "Mixta" to "MIXED"),
        ),
        SetupStepDefinition(
            id = SetupStepId.RINGS_INTENSITY, block = SetupWizardBlock.RINGS, kind = SetupStepKind.QUESTION,
            title = "¿Cómo sentiste la intensidad?", control = SetupControlKind.SINGLE_CHOICE,
            options = opt(
                "EASY" to "Fácil", "MODERATE" to "Moderada", "HARD" to "Exigente", "VERY_HARD" to "Muy exigente",
            ),
            legacyQuestion = WizChatQuestionId.R_INTENSITY,
            legacyValueMap = mapOf(
                "Fácil" to "EASY", "Moderada" to "MODERATE", "Exigente" to "HARD", "Muy exigente" to "VERY_HARD",
            ),
        ),
        SetupStepDefinition(
            id = SetupStepId.RINGS_AXIAL, block = SetupWizardBlock.RINGS, kind = SetupStepKind.QUESTION,
            title = "¿Hubo cargas pesadas para la espalda?",
            subtitle = "Cargas pesadas para la espalda (sentadilla, peso muerto…).",
            control = SetupControlKind.SINGLE_CHOICE,
            options = opt("yes" to "Sí", "no" to "No", "unknown" to "No lo sé"),
            legacyQuestion = WizChatQuestionId.R_AXIAL,
            legacyValueMap = mapOf("Sí" to "yes", "No" to "no", "No lo sé" to "unknown"),
        ),
        SetupStepDefinition(
            id = SetupStepId.RINGS_MUSCLE_FEELING, block = SetupWizardBlock.RINGS, kind = SetupStepKind.QUESTION,
            title = "¿Cómo se sienten tus músculos?",
            subtitle = "Tu percepción manda; «No lo sé» es una respuesta válida y no inventa un nivel.",
            control = SetupControlKind.SINGLE_CHOICE,
            options = intensityLevels + feelingsUnknown,
            allowSkip = true,
            legacyQuestion = WizChatQuestionId.R_FEELINGS_MUSCLE,
            legacyValueMap = intensityLevels.associate { it.label to it.value },
        ),
        SetupStepDefinition(
            id = SetupStepId.RINGS_ENERGY_FEELING, block = SetupWizardBlock.RINGS, kind = SetupStepKind.QUESTION,
            title = "¿Cómo está tu energía?",
            subtitle = "Tu percepción manda; «No lo sé» es una respuesta válida y no inventa un nivel.",
            control = SetupControlKind.SINGLE_CHOICE,
            options = energyLevels + feelingsUnknown,
            allowSkip = true,
            legacyQuestion = WizChatQuestionId.R_FEELINGS_ENERGY,
            legacyValueMap = energyLevels.associate { it.label to it.value },
        ),
        SetupStepDefinition(
            id = SetupStepId.RINGS_STRUCTURE_FEELING, block = SetupWizardBlock.RINGS, kind = SetupStepKind.QUESTION,
            title = "¿Cómo está tu columna?",
            subtitle = "Tu percepción manda; «No lo sé» es una respuesta válida y no inventa un nivel.",
            control = SetupControlKind.SINGLE_CHOICE,
            options = structureLevels + feelingsUnknown,
            allowSkip = true,
            legacyQuestion = WizChatQuestionId.R_FEELINGS_STRUCTURE,
            legacyValueMap = structureLevels.associate { it.label to it.value },
        ),
        SetupStepDefinition(
            id = SetupStepId.RINGS_DISCOMFORT, block = SetupWizardBlock.RINGS, kind = SetupStepKind.QUESTION,
            title = "¿Hay alguna molestia que debamos tener en cuenta?",
            subtitle = "Dinos qué te molesta; puedes responder «Sin molestias» u omitirlo.",
            control = SetupControlKind.MULTI_CHOICE,
            options = discomforts(),
            exclusiveValues = setOf("none", "omit"),
            allowSkip = true,
            legacyQuestion = WizChatQuestionId.R_DISCOMFORT,
            legacyValueMap = discomfortLegacyMap(),
        ),
        SetupStepDefinition(
            id = SetupStepId.RINGS_RESULT, block = SetupWizardBlock.RINGS, kind = SetupStepKind.QUESTION,
            title = "Tus RINGS", control = SetupControlKind.RESULT_PREVIEW,
            legacyQuestion = WizChatQuestionId.R_RESULT, legacyQuestionRenders = false,
        ),
        SetupStepDefinition(
            id = SetupStepId.MILESTONE_RINGS, block = SetupWizardBlock.RINGS, kind = SetupStepKind.MILESTONE,
            title = "Rings", control = SetupControlKind.MILESTONE,
        ),

        // -------------------------------------------------------------------
        // Revisión y activación
        // -------------------------------------------------------------------
        SetupStepDefinition(
            id = SetupStepId.REVIEW_ACTIVATE, block = SetupWizardBlock.REVIEW, kind = SetupStepKind.REVIEW,
            title = "Revisión y activación",
            subtitle = "Todo es editable antes de activar.",
            control = SetupControlKind.REVIEW,
            legacyQuestion = WizChatQuestionId.REVIEW, legacyQuestionRenders = false,
        ),

        // -------------------------------------------------------------------
        // Legacy read-only (fuera de la ruta productiva)
        // -------------------------------------------------------------------
        SetupStepDefinition(
            id = SetupStepId.GENDER, block = SetupWizardBlock.BASICS, kind = SetupStepKind.QUESTION,
            title = "Identidad de género (legacy)",
            subtitle = "Solo lectura de borradores antiguos. Nunca se convierte en sexo de cálculo.",
            control = SetupControlKind.SINGLE_CHOICE,
            options = opt("female" to "Mujer", "male" to "Hombre", "other" to "Otro", "unspecified" to "Prefiero no responder"),
            allowSkip = true,
            legacyQuestion = WizChatQuestionId.P_GENDER, legacyOnly = true,
        ),
        SetupStepDefinition(
            id = SetupStepId.HOME_EQUIPMENT, block = SetupWizardBlock.TRAINING, kind = SetupStepKind.QUESTION,
            title = "Material en casa (legacy)",
            control = SetupControlKind.MULTI_CHOICE,
            options = opt(
                "bodyweight" to "Peso corporal", "bands" to "Bandas", "dumbbells" to "Mancuernas",
                "pull_up" to "Barra de dominadas", "support" to "Apoyo estable", "none" to "Sin material",
            ),
            exclusiveValues = setOf("none"),
            legacyQuestion = WizChatQuestionId.T_HOME_EQUIPMENT, legacyOnly = true,
        ),
        SetupStepDefinition(
            id = SetupStepId.NUTRITION_SEX, block = SetupWizardBlock.NUTRITION, kind = SetupStepKind.QUESTION,
            title = "Sexo de cálculo (legacy)",
            subtitle = "Valores explícitos migran a «Sexo de cálculo» del bloque de datos básicos.",
            control = SetupControlKind.SINGLE_CHOICE,
            options = opt("female" to "Femenino", "male" to "Masculino", "unspecified" to "Prefiero no responder"),
            legacyQuestion = WizChatQuestionId.N_SEX, legacyOnly = true,
        ),
        SetupStepDefinition(
            id = SetupStepId.RINGS_START, block = SetupWizardBlock.RINGS, kind = SetupStepKind.QUESTION,
            title = "Inicio de RINGS (legacy)", control = SetupControlKind.SINGLE_CHOICE,
            legacyQuestion = WizChatQuestionId.R_START, legacyOnly = true,
        ),
    ).associateBy { it.id }

    // -----------------------------------------------------------------------
    // Accessors
    // -----------------------------------------------------------------------

    fun of(step: SetupStepId): SetupStepDefinition? = definitions[step]

    fun title(step: SetupStepId): String = of(step)?.title ?: step.name

    fun subtitle(step: SetupStepId): String? = of(step)?.subtitle

    fun control(step: SetupStepId): SetupControlKind = of(step)?.control ?: SetupControlKind.SINGLE_CHOICE

    fun options(step: SetupStepId): List<SetupOptionDefinition> = of(step)?.options.orEmpty()

    fun optionValues(step: SetupStepId): Set<String> = options(step).mapTo(mutableSetOf()) { it.value }

    fun isLegacyOnly(step: SetupStepId): Boolean = of(step)?.legacyOnly == true

    /** Legacy question -> stable step for migration; legacy-only steps are excluded. */
    val legacyMigrationTargets: Map<WizChatQuestionId, SetupStepId> = buildMap {
        definitions.values.filter { it.legacyQuestion != null && !it.legacyOnly }
            .forEach { put(it.legacyQuestion!!, it.id) }
        // N_SEX (nutrition, legacy) -> EQUATION_SEX (basic block); explicit only.
        put(WizChatQuestionId.N_SEX, SetupStepId.EQUATION_SEX)
    }

    /** Steps whose legacy question is still a renderable 1:1 copy. */
    val legacyRenderable: Map<SetupStepId, WizChatQuestionId> = definitions.values
        .filter { it.legacyQuestion != null && !it.legacyOnly && it.legacyQuestionRenders }
        .associate { it.id to it.legacyQuestion!! }

    /**
     * Stable value a legacy label maps to for [questionId], or null when the
     * answer must stay pending (e.g. N_SEX "Prefiero no responder").
     */
    fun migratedValue(questionId: WizChatQuestionId, legacyLabel: String?): String? {
        val step = legacyMigrationTargets[questionId] ?: return null
        return of(step)?.migratedValue(legacyLabel)
    }

    /** Inventory step shown for a given group. */
    fun stepOf(group: SetupInventoryGroup): SetupStepId = when (group) {
        SetupInventoryGroup.BARBELL -> SetupStepId.INVENTORY_BARBELL
        SetupInventoryGroup.PLATES -> SetupStepId.INVENTORY_PLATES
        SetupInventoryGroup.DUMBBELLS -> SetupStepId.INVENTORY_DUMBBELLS
        SetupInventoryGroup.KETTLEBELLS -> SetupStepId.INVENTORY_KETTLEBELLS
        SetupInventoryGroup.MACHINES -> SetupStepId.INVENTORY_MACHINES
    }

    /** Block headline copy: clean, non-conversational. */
    fun blockTitle(block: SetupWizardBlock): String = when (block) {
        SetupWizardBlock.BASICS -> "Datos básicos"
        SetupWizardBlock.TRAINING -> "Entreno"
        SetupWizardBlock.NUTRITION -> "Nutrición"
        SetupWizardBlock.RINGS -> "Rings"
        SetupWizardBlock.REVIEW -> "Revisión"
    }
}