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
    /** Body-fat step: a vertical percentage ruler (5–50 %) next to a male/female figure.
     *  Mandatory (no skip); moving the ruler declares a visual estimate. */
    PHYSIQUE,
    /** One option from a closed list. */
    SINGLE_CHOICE,
    /** Several options from a closed list (weekdays, eligibility, discomforts). */
    MULTI_CHOICE,
    /** Plan route: recommended or protocol. No later/manual option (legacy-only step). */
    ROUTE_CHOICE,
    /** Inventory picker: max two related inputs per screen, sub-editor rows allowed (legacy-only steps). */
    INVENTORY_PICKER,
    /** Split picker/editor (legacy-only step: the week board replaced it). */
    SPLIT_EDITOR,
    /** Marks editor rows (legacy-only step: the marks live in [LIFT_MARKS]). */
    MARKS_EDITOR,
    /** Yes/no toggle (legacy-only autoregulation step). */
    TOGGLE,
    /** Explicit confirmation of a derived behavior (legacy-only autoregulation step). */
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

    // ── Entreno v2: controles de símbolos (los dibujan los paquetes visuales; el dato lo escribe el VM) ──
    /** Dónde se entrena: gimnasio, casa, espacios públicos (uno o varios). */
    PLACES,
    /** Material: un símbolo por implemento, con «solo peso corporal» exclusivo. */
    EQUIPMENT_SYMBOLS,
    /** Perfil de objetivo: tres generales y siete disciplinas condicionadas al material. */
    GOAL_PROFILES,
    /** Día de la semana con más energía (uno de siete). */
    FRESH_DAY,
    /** Calendario semanal: de 1 a 7 días, inicio de semana y lugar por día. */
    WEEK_CALENDAR,
    /** Reloj de tiempo por sesión: de 20 a 180 minutos. */
    SESSION_DIAL,
    /** Ejercicios de peso corporal que ya salen, con nivel (aún no / algunas / varias). */
    CAPABILITIES,
    /** Músculos que se quieren mejorar más (hasta 5; omitible). */
    MUSCLE_SYMBOLS,
    /** Marcas de los levantamientos que pregunta el objetivo (kg o lb; cada una opcional). */
    LIFT_MARKS,
    /** Programa: preparación animada y revelado (general) o carrusel de programas (disciplina). */
    PLAN_REVEAL,
    /** Semana armada: sesiones colocadas en sus días y movibles. */
    WEEK_LAYOUT,
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
    /** Frase corta bajo la etiqueta cuando la opción necesita contexto; nula en las demás. */
    val description: String? = null,
)

/**
 * Valores estables del paso EQUATION_SEX que no son un glifo de género: «No lo sé» y las tres
 * respuestas sobre el contexto hormonal. Las consumen el catálogo, el reductor de respuestas, la
 * validación, los resúmenes y la UI, así ninguna repite el literal.
 *
 * Las respuestas hormonales eligen la BASE DE LA ECUACIÓN de energía (femenina, masculina o
 * promedio), no una identidad: el gasto energético depende de la masa libre de grasa y del
 * entorno hormonal, no de cómo se identifica la persona.
 */
object SetupEquationSexValues {
    /** «No lo sé»: abre la consulta hormonal y, a solas, no basta para continuar. */
    const val UNKNOWN = "unknown"
    const val HORMONES_ESTROGEN = "hormones_estrogen"
    const val HORMONES_ANDROGEN = "hormones_androgen"
    const val HORMONES_MIXED = "hormones_mixed"

    /** Las tres respuestas hormonales, en el orden en que se muestran. */
    val HORMONAL: List<String> = listOf(HORMONES_ESTROGEN, HORMONES_ANDROGEN, HORMONES_MIXED)

    /** ¿Debe estar abierta la consulta hormonal con esta selección? Sí con «No lo sé» o con una respuesta hormonal. */
    fun opensHormonalPanel(value: String?): Boolean = value == UNKNOWN || value in HORMONAL
}

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

/**
 * Canonical muscles understood by the order engine (SimpleCyclePersonalizer). Cada [MuscleSymbol] del paso
 * PRIORITIES tiene aquí su músculo canónico (`MuscleSymbols.canonical`); «Erectores Espinales» no tiene símbolo y se
 * conserva para leer borradores antiguos. «Antebrazo» (singular) es el nombre que usan el catálogo y el volumen.
 */
val ORDER_MUSCLE_OPTIONS: List<SetupOptionDefinition> = listOf(
    "Pectorales", "Dorsales", "Deltoides", "Bíceps", "Tríceps", "Antebrazo", "Cuádriceps",
    "Isquiosurales", "Glúteos", "Pantorrillas", "Abdomen", "Trapecio", "Erectores Espinales",
).map { SetupOptionDefinition(it, it) }

object SetupStepDefinitions {

    private fun opt(vararg pairs: Pair<String, String>) = pairs.map { (v, l) -> SetupOptionDefinition(v, l) }

    // Copy de la página larga (lo exige SetupStepCopyRulesTest): en los pasos de pregunta el
    // título es una pregunta de hasta 40 caracteres y el subtítulo, una frase de hasta 100.
    // Los nombres de bloque y de vista previa son etiquetas y quedan fuera de esa regla.
    val definitions: Map<SetupStepId, SetupStepDefinition> = listOf(
        // -------------------------------------------------------------------
        // Bloque 1: Datos básicos
        // -------------------------------------------------------------------
        SetupStepDefinition(
            id = SetupStepId.NAME, block = SetupWizardBlock.BASICS, kind = SetupStepKind.QUESTION,
            title = "Pon tu alias", control = SetupControlKind.TEXT,
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
            title = "¿Cuál es tu género?",
            // El porqué largo vive en el diálogo «Por qué lo preguntamos» del paso (SetupBasicSteps).
            subtitle = "Lo usamos para calcular tu gasto energético.",
            control = SetupControlKind.SINGLE_CHOICE,
            // Las cuatro primeras son los glifos de la fila de género. Las tres hormonales están en el catálogo
            // para que selección, validación, resúmenes y rehidratación las reconozcan, pero la UI no las pinta
            // en esa fila: salen en el panel que abre «No lo sé».
            options = opt(
                "female" to "Mujer",
                "male" to "Hombre",
                "trans_male" to "Hombre trans",
                "trans_female" to "Mujer trans",
            ) + listOf(
                SetupOptionDefinition(
                    SetupEquationSexValues.HORMONES_ESTROGEN,
                    "Predominan los estrógenos",
                    "Por ejemplo, ciclo menstrual o terapia con estrógenos.",
                ),
                SetupOptionDefinition(
                    SetupEquationSexValues.HORMONES_ANDROGEN,
                    "Predominan los andrógenos",
                    "Por ejemplo, testosterona propia o terapia con testosterona.",
                ),
                SetupOptionDefinition(
                    SetupEquationSexValues.HORMONES_MIXED,
                    "Un equilibrio o no lo sé",
                    "Calculamos con el promedio de ambas ecuaciones.",
                ),
                SetupOptionDefinition(SetupEquationSexValues.UNKNOWN, "No lo sé"),
            ),
            // El valor migra desde la pregunta de nutrición legacy N_SEX cuando
            // es explícita; "Prefiero no responder" No migra y queda pendiente.
            legacyQuestion = WizChatQuestionId.N_SEX,
            legacyValueMap = mapOf("Femenino" to "female", "Masculino" to "male"),
            legacyQuestionRenders = false,
        ),
        SetupStepDefinition(
            id = SetupStepId.BODY_FAT, block = SetupWizardBlock.BASICS, kind = SetupStepKind.QUESTION,
            // 41 caracteres: es la única pregunta que pasa de 40 (SetupStepCopyRulesTest admite hasta 44).
            title = "¿Cuál es tu porcentaje de grasa corporal?",
            // Sin subtítulo: la figura y la regla se explican solas (la nota, el estado y el campo manual se retiraron).
            subtitle = null,
            control = SetupControlKind.PHYSIQUE, unit = "%",
            range = SetupNumericRange(3.0, 60.0, "%"),
            // Fuentes que puede traer un borrador antiguo. El paso actual solo declara «visual» (mover la regla):
            // ya no hay medición escrita ni «omitir», pero esos borradores se siguen leyendo.
            options = opt(
                "measured" to "Lo tengo medido",
                "visual" to "Estimación visual con la figura",
                "unknown" to "No lo sé / omitir",
            ),
            // Obligatorio: la validación exige un porcentaje declarado entre 3 y 60.
            allowSkip = false,
        ),
        SetupStepDefinition(
            id = SetupStepId.MILESTONE_BASICS, block = SetupWizardBlock.BASICS, kind = SetupStepKind.MILESTONE,
            title = "Datos básicos", control = SetupControlKind.MILESTONE,
        ),

        // -------------------------------------------------------------------
        // Bloque 2: Entreno (v2). Textos finales en docs/entreno-v2/COPY.md.
        // Ruta: EXPERIENCE, EQUIPMENT, AVAILABILITY, GOAL, FRESH_DAY, WEEKDAYS, SESSION_TIME, [CARDIO_*],
        // [VOLUME_TECHNIQUE], VOLUME_CONSISTENCY, VOLUME_STRENGTH, VOLUME_MOBILITY, [CAPABILITIES], PRIORITIES,
        // [TRAINING_MAX], PLAN, [WEEK_LAYOUT]. Programa/semana/material/lugar: los mismos nombres en todo el módulo.
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
            id = SetupStepId.EQUIPMENT, block = SetupWizardBlock.TRAINING, kind = SetupStepKind.QUESTION,
            title = "¿Dónde entrenas?",
            subtitle = "Elige uno o varios lugares.",
            control = SetupControlKind.PLACES,
            options = TrainingPlace.entries.map { place -> SetupOptionDefinition(EntrenoStepValues.placeValue(place), place.label) },
            legacyQuestion = WizChatQuestionId.T_EQUIPMENT,
            // El material de cada respuesta antigua se conserva tal cual en la disponibilidad; aquí solo el lugar.
            legacyValueMap = mapOf(
                "Gimnasio completo" to "gym", "Principalmente máquinas" to "gym",
                "Entreno en casa" to "home", "Sin material" to "home",
            ),
        ),
        SetupStepDefinition(
            id = SetupStepId.AVAILABILITY,
            block = SetupWizardBlock.TRAINING,
            kind = SetupStepKind.QUESTION,
            title = "¿Con qué material entrenas?",
            subtitle = "Marca lo que tienes y lo que quieres usar.",
            control = SetupControlKind.EQUIPMENT_SYMBOLS,
            // Un símbolo por implemento; cuáles se ofrecen depende de los lugares (`EquipmentSymbols.symbolsFor`).
            options = EquipmentSymbolId.entries.map { symbol -> SetupOptionDefinition(symbol.name, symbol.label) },
            exclusiveValues = setOf(EquipmentSymbolId.BODYWEIGHT_ONLY.name),
        ),
        SetupStepDefinition(
            id = SetupStepId.GOAL, block = SetupWizardBlock.TRAINING, kind = SetupStepKind.QUESTION,
            title = "¿Cuál es tu objetivo?",
            subtitle = "Elige un perfil general o una disciplina.",
            control = SetupControlKind.GOAL_PROFILES,
            // Tres perfiles generales y siete disciplinas (estas dependen del material: `TrainingGoalRequirements`).
            options = TrainingGoalProfile.entries.map { profile ->
                SetupOptionDefinition(EntrenoStepValues.goalValue(profile), profile.label, profile.tagline)
            },
            legacyQuestion = WizChatQuestionId.T_GOAL,
            // Las respuestas antiguas se leen como el perfil que hoy les corresponde; nada se confirma solo.
            legacyValueMap = mapOf(
                "Fuerza" to EntrenoStepValues.goalValue(TrainingGoalProfile.POWERLIFTING),
                "Músculo" to EntrenoStepValues.goalValue(TrainingGoalProfile.BODYBUILDING),
                "Fuerza y músculo" to EntrenoStepValues.goalValue(TrainingGoalProfile.STRENGTH_MUSCLE),
                "Salud y condición" to EntrenoStepValues.goalValue(TrainingGoalProfile.FUNCTIONAL_HEALTH),
                "Fuerza + cardio" to EntrenoStepValues.goalValue(TrainingGoalProfile.STRENGTH_CARDIO),
                "Atleta completo" to EntrenoStepValues.goalValue(TrainingGoalProfile.STRENGTH_CARDIO),
            ),
        ),
        SetupStepDefinition(
            id = SetupStepId.FRESH_DAY, block = SetupWizardBlock.TRAINING, kind = SetupStepKind.QUESTION,
            title = "¿Qué día llegas con más energía?",
            subtitle = "Tu sesión más fuerte caerá ese día.",
            control = SetupControlKind.FRESH_DAY,
            options = weekdaysOptions,
        ),
        SetupStepDefinition(
            id = SetupStepId.WEEKDAYS, block = SetupWizardBlock.TRAINING, kind = SetupStepKind.QUESTION,
            title = "¿Qué días puedes entrenar?",
            subtitle = "Entre 1 y 7. El programa se adapta a tu semana.",
            control = SetupControlKind.WEEK_CALENDAR,
            options = weekdaysOptions,
            legacyQuestion = WizChatQuestionId.T_WEEKDAYS,
            legacyValueMap = mapOf(
                "Lunes" to "1", "Martes" to "2", "Miércoles" to "3", "Jueves" to "4",
                "Viernes" to "5", "Sábado" to "6", "Domingo" to "7",
            ),
        ),
        SetupStepDefinition(
            id = SetupStepId.SESSION_TIME, block = SetupWizardBlock.TRAINING, kind = SetupStepKind.QUESTION,
            title = "¿Cuánto tiempo tienes por sesión?",
            subtitle = "Es un rango: el programa se ajusta a ti.",
            control = SetupControlKind.SESSION_DIAL, unit = "min",
            range = SetupNumericRange(EntrenoStepValues.SESSION_MINUTES_MIN.toDouble(), EntrenoStepValues.SESSION_MINUTES_MAX.toDouble(), "min"),
            legacyQuestion = WizChatQuestionId.T_TIME,
        ),
        SetupStepDefinition(
            id = SetupStepId.CARDIO_TYPE, block = SetupWizardBlock.TRAINING, kind = SetupStepKind.QUESTION,
            title = "¿Qué cardio quieres incluir?", control = SetupControlKind.SINGLE_CHOICE,
            // Todas las respuestas posibles; cuáles se ofrecen depende del material y los lugares (`CardioChoices.optionsFor`).
            options = CardioChoice.entries.map { choice -> SetupOptionDefinition(choice.name, choice.label, choice.hint) },
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
            id = SetupStepId.CAPABILITIES, block = SetupWizardBlock.TRAINING, kind = SetupStepKind.QUESTION,
            title = "¿Qué ejercicios ya te salen?",
            subtitle = "Así elegimos variantes a tu medida.",
            control = SetupControlKind.CAPABILITIES,
            // Qué ejercicios se ofrecen depende del material (`CapabilityRules.skillsFor`); los niveles, de `CapabilityLevel`.
            options = CapabilitySkill.entries.map { skill -> SetupOptionDefinition(skill.name, skill.label) },
        ),
        SetupStepDefinition(
            id = SetupStepId.PRIORITIES, block = SetupWizardBlock.TRAINING, kind = SetupStepKind.QUESTION,
            title = "¿Qué músculos priorizas?",
            subtitle = "Elige hasta 5. Puedes omitir este paso.",
            control = SetupControlKind.MUSCLE_SYMBOLS,
            // Valores estables = músculos canónicos del motor de orden; cada símbolo escribe un punto en el suyo.
            options = ORDER_MUSCLE_OPTIONS,
            budget = MuscleSymbols.MAX_SELECTION,
            maxPerItem = 1,
        ),
        SetupStepDefinition(
            id = SetupStepId.TRAINING_MAX, block = SetupWizardBlock.TRAINING, kind = SetupStepKind.QUESTION,
            title = "¿Conoces tus marcas?",
            subtitle = "Con una basta. Sin marcas, el programa sigue siendo válido.",
            control = SetupControlKind.LIFT_MARKS,
            // Qué levantamientos se preguntan lo decide `MarksContext.liftsFor`; cada marca es opcional.
            options = LiftMark.entries.map { lift -> SetupOptionDefinition(lift.name, lift.label) },
            // «¿Conoces tus marcas?» antiguo (Sí / Todavía no): cualquier respuesta cuenta como declarada; las marcas
            // que dio viajan de `powerliftingProfile` a `liftMarks` y la selección sale de ellas, no de un valor.
            legacyQuestion = WizChatQuestionId.T_TRAINING_MAX,
            legacyAcceptsIdValue = true,
        ),
        SetupStepDefinition(
            id = SetupStepId.PLAN, block = SetupWizardBlock.TRAINING, kind = SetupStepKind.QUESTION,
            // El título cambia con el perfil (`wizardPageCopy`): con una disciplina es «Elige tu programa».
            title = "Tu programa a medida",
            subtitle = "Armado con tu material, tus días y tu tiempo.",
            control = SetupControlKind.PLAN_REVEAL,
            legacyQuestion = WizChatQuestionId.T_PLAN,
            // La respuesta legacy es el id del candidato generado, no una etiqueta mapeada.
            legacyAcceptsIdValue = true,
        ),
        SetupStepDefinition(
            id = SetupStepId.WEEK_LAYOUT, block = SetupWizardBlock.TRAINING, kind = SetupStepKind.QUESTION,
            title = "Así queda tu semana",
            subtitle = "Mueve las sesiones a los días que prefieras.",
            control = SetupControlKind.WEEK_LAYOUT,
        ),
        SetupStepDefinition(
            id = SetupStepId.MILESTONE_TRAINING, block = SetupWizardBlock.TRAINING, kind = SetupStepKind.MILESTONE,
            title = "Entreno", control = SetupControlKind.MILESTONE,
        ),

        // -------------------------------------------------------------------
        // Entreno: pasos retirados de la ruta (solo lectura de borradores antiguos). El enum y estas definiciones
        // se conservan; ya no son preguntas del alta: la semana, el material y el calentamiento los resuelve el
        // programa (autorregulación «sugerir y confirmar» y aproximación obligatorias).
        // -------------------------------------------------------------------
        SetupStepDefinition(
            id = SetupStepId.ROUTE, block = SetupWizardBlock.TRAINING, kind = SetupStepKind.QUESTION,
            title = "¿Cómo quieres empezar?",
            subtitle = "Elige entre un plan recomendado o un protocolo de catálogo.",
            control = SetupControlKind.ROUTE_CHOICE,
            options = opt(
                "recommended" to "Recomiéndame un plan",
                "protocol" to "Elegir un protocolo",
            ),
            legacyQuestion = WizChatQuestionId.T_ROUTE,
            legacyValueMap = mapOf(
                "Recomiéndame un plan" to "recommended",
                "Elegir un protocolo" to "protocol",
                // "Crear desde cero" / "Lo decidiré después" NO migran (quedan pendientes).
            ),
            legacyOnly = true,
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
            legacyOnly = true,
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
            id = SetupStepId.DAYS, block = SetupWizardBlock.TRAINING, kind = SetupStepKind.QUESTION,
            title = "¿Cuántos días entrenas a la semana?", control = SetupControlKind.SINGLE_CHOICE,
            options = doorsOptions,
            legacyQuestion = WizChatQuestionId.T_DAYS,
            legacyValueMap = (1..6).associate { "$it" to "$it" },
            // Ahora los días se derivan de los días elegidos del calendario (`selectedWeekdays.size`).
            legacyOnly = true,
        ),
        SetupStepDefinition(
            id = SetupStepId.SPLIT, block = SetupWizardBlock.TRAINING, kind = SetupStepKind.QUESTION,
            title = "¿Cómo quieres repartir la semana?",
            subtitle = "Una propuesta destacada, alternativas para mirar y el resto con buscador.",
            control = SetupControlKind.SPLIT_EDITOR,
            options = opt("recommended" to "Recomendado para ti", "custom" to "Personalizado"),
            legacyOnly = true,
        ),
        SetupStepDefinition(
            id = SetupStepId.TRAINING_MARKS, block = SetupWizardBlock.TRAINING, kind = SetupStepKind.QUESTION,
            title = "¿Cuáles son tus marcas?",
            subtitle = "Sentadilla, banca y peso muerto; con una basta para empezar.",
            control = SetupControlKind.MARKS_EDITOR,
            options = opt("squat" to "Sentadilla", "bench" to "Banca", "deadlift" to "Peso muerto"),
            legacyQuestion = WizChatQuestionId.T_MARKS,
            // Las marcas viven ahora en TRAINING_MAX (`liftMarks`).
            legacyOnly = true,
        ),
        SetupStepDefinition(
            id = SetupStepId.AUTOREGULATION, block = SetupWizardBlock.TRAINING, kind = SetupStepKind.QUESTION,
            title = "¿Ajusto tu semana automáticamente?",
            subtitle = "Si lo activas, KPKN ajusta series y pesos cada semana según tu respuesta.",
            control = SetupControlKind.TOGGLE,
            options = opt("on" to "Sí, ajusta mi semana", "off" to "No, lo controlo yo"),
            // Ahora siempre «sugerir y confirmar» (PROPOSE).
            legacyOnly = true,
        ),
        SetupStepDefinition(
            id = SetupStepId.AUTOREGULATION_CONFIRM, block = SetupWizardBlock.TRAINING, kind = SetupStepKind.QUESTION,
            title = "¿Confirmas el ajuste automático?",
            subtitle = "Los cambios se aplican al confirmarlos, puedes revertirlos y tus marcas declaradas no cambian.",
            control = SetupControlKind.AUTO_CONFIRM,
            options = opt("confirmed" to "Confirmado", "review_only" to "Solo revisar"),
            legacyOnly = true,
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
            // La aproximación y la movilidad son obligatorias y las arma el programa.
            legacyOnly = true,
        ),
        SetupStepDefinition(
            id = SetupStepId.TRAINING_REVIEW, block = SetupWizardBlock.TRAINING, kind = SetupStepKind.QUESTION,
            title = "Revisión del plan", control = SetupControlKind.RESULT_PREVIEW,
            legacyQuestion = WizChatQuestionId.T_REVIEW,
            // Sustituida por la semana armada (WEEK_LAYOUT) y la fila de programa de la revisión final.
            legacyOnly = true,
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
            title = "¿Cuál es tu objetivo de nutrición?",
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
            title = "Tu plan de alimentación", control = SetupControlKind.RESULT_PREVIEW,
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
            title = "¿Tienes alguna molestia?",
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