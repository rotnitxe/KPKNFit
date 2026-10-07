package com.example.kpkn.domain.training.generator

import com.example.kpkn.data.models.EquipmentAvailability
import com.example.kpkn.data.models.Program
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2
import com.example.kpkn.domain.onboarding.CapabilityLevel
import com.example.kpkn.domain.onboarding.CapabilitySkill
import com.example.kpkn.domain.onboarding.LiftMark
import com.example.kpkn.domain.onboarding.MuscleSymbol
import com.example.kpkn.domain.onboarding.TrainingPlace
import com.example.kpkn.domain.training.CardioPreference

/*
 * Tipos públicos del generador de rutinas (Entreno v2, paquete D1).
 *
 * El generador es una función PURA y determinista: la misma [RoutineRequest] (incluida su `variantSeed`) produce
 * siempre la misma [GeneratedRoutine]. No toca red, disco ni reloj; los ids salen de un hash estable de la petición.
 */

/**
 * Qué clase de programa se arma. Los tres `GENERAL_*` son los objetivos generales del wizard; las disciplinas y los
 * `CUSTOM_*` son las versiones «a medida» de los planes que ya tienen autor y sirven cuando ningún plan de autor
 * encaja con los días, minutos o material de la persona.
 */
enum class RoutineMode(val label: String, val isDiscipline: Boolean) {
    GENERAL_STRENGTH_MUSCLE("Fuerza y masa muscular", false),
    GENERAL_HYBRID("Fuerza y cardio", false),
    GENERAL_FUNCTIONAL("Funcional y saludable", false),
    DISCIPLINE_CALISTHENICS("Calistenia", true),
    DISCIPLINE_ARMWRESTLING("Armwrestling", true),
    DISCIPLINE_STRONGMAN("Strongman", true),
    DISCIPLINE_WEIGHTLIFTING_BASE("Base de halterofilia", true),
    CUSTOM_POWERLIFTING("Powerlifting a medida", true),
    CUSTOM_POWERBUILDING("Powerbuilding a medida", true),
    CUSTOM_BODYBUILDING("Culturismo a medida", true),
}

/** Experiencia declarada. Decide dificultad técnica permitida, series, repeticiones objetivo y descansos. */
enum class RoutineLevel { NOVICE, RETURNING, INTERMEDIATE, ADVANCED }

/** Entrada imposible (0 días, minutos fuera de 20..180, días fuera de 1..7). El resto de combinaciones SIEMPRE produce programa. */
class RoutineGenerationException(message: String) : IllegalArgumentException(message)

/**
 * Lo que la persona declaró. Todo lo que no esté aquí no cambia el resultado.
 *
 * - [weekdays]: días de entreno (1 = lunes … 7 = domingo) en el orden del ciclo que empieza en [weekStartDay]
 *   (si llegan desordenados el generador los reordena). Entre 1 y 7.
 * - [freshestDay]: día con más energía; la sesión más exigente cae ahí o, si no se entrena ese día, en el primer día
 *   de entreno posterior (en el ciclo semanal). `null` = el día que sigue al descanso más largo.
 * - [targetMinutes] (20..180) es un RANGO, no un tope: cada sesión queda entre el 85 % y el 110 % del objetivo (y nunca
 *   por debajo de 20 min) o, si el material o los techos de volumen lo impiden, lo más cerca posible con una nota.
 * - Material: [availability] es la unión de todo lo declarado; si hay más de un lugar, [dayPlaces] dice qué lugar usa
 *   cada día y [availabilityByPlace] el material de cada lugar: cada sesión se arma SOLO con el material de SU lugar.
 * - [priorityMuscles] (máx. 5): suben el volumen de ese músculo y lo adelantan en la sesión.
 * - [capabilities]: qué tan bien salen flexiones, dominadas, fondos y pistol; eligen el peldaño de las escaleras de peso corporal.
 * - [marks]: marcas (kg) para prescribir porcentajes del 1RM en los básicos correspondientes. Sin marcas la carga queda
 *   pendiente (se elige en el entrenamiento).
 * - [catalog]: catálogo de ejercicios v2 ya cargado. Cada ejercicio citado sale de aquí.
 * - [variantSeed]: cambia, de forma determinista, entre alternativas equivalentes de una reserva («otra versión»).
 * - [programId]: id del programa generado; si es null se deriva de un hash estable de la petición.
 */
data class RoutineRequest(
    val mode: RoutineMode,
    val weekdays: List<Int>,
    val weekStartDay: Int = weekdays.firstOrNull() ?: 1,
    val freshestDay: Int? = null,
    val targetMinutes: Int,
    val level: RoutineLevel,
    val technique: Int? = null,
    val consistency: Int? = null,
    val strength: Int? = null,
    val mobility: Int? = null,
    val availability: EquipmentAvailability,
    val places: Set<TrainingPlace> = emptySet(),
    val dayPlaces: Map<Int, TrainingPlace> = emptyMap(),
    val availabilityByPlace: Map<TrainingPlace, EquipmentAvailability> = emptyMap(),
    val priorityMuscles: List<MuscleSymbol> = emptyList(),
    val capabilities: Map<CapabilitySkill, CapabilityLevel> = emptyMap(),
    val cardio: CardioPreference? = null,
    val marks: Map<LiftMark, Double> = emptyMap(),
    val catalog: ExerciseCatalogV2,
    val variantSeed: Int = 0,
    val programId: String? = null,
)

/** Patrones de movimiento que el generador sabe cubrir. Sirven para el informe de cobertura y para las notas de límites. */
enum class RoutinePattern(val label: String) {
    SQUAT("Sentadilla"),
    SINGLE_LEG("Zancada y trabajo a una pierna"),
    HINGE("Bisagra de cadera"),
    GLUTE("Glúteo"),
    HORIZONTAL_PUSH("Empuje horizontal"),
    VERTICAL_PUSH("Empuje vertical"),
    HORIZONTAL_PULL("Tracción horizontal"),
    VERTICAL_PULL("Tracción vertical"),
    CHEST_ISOLATION("Pecho aislado"),
    SHOULDER_LATERAL("Hombro lateral"),
    REAR_DELT("Hombro posterior"),
    BICEPS("Bíceps"),
    TRICEPS("Tríceps"),
    TRAPS("Trapecio"),
    GRIP("Agarre y antebrazo"),
    QUAD_ISOLATION("Cuádriceps aislado"),
    HAMSTRING_CURL("Femoral aislado"),
    CALF("Pantorrilla"),
    CORE_STABILITY("Core: flexión y estabilidad"),
    CORE_ROTATION("Core: rotación y lateral"),
    BACK_EXTENSION("Extensión de espalda"),
    CARRY("Acarreo"),
    POWER("Potencia"),
    CARDIO("Cardio"),
    MOBILITY("Movilidad"),
}

/** Clase de sesión: las de cardio y movilidad quedan exentas del mínimo de ejercicios de fuerza. */
enum class RoutineSessionKind { STRENGTH, MIXED, CARDIO, MOBILITY }

/** Resultado del generador. */
data class GeneratedRoutine(
    /** Programa listo para activar: semana cíclica (una sola semana que se repite) con las sesiones en sus días reales. */
    val program: Program,
    /** Lo que muestra la pantalla de «revelado». */
    val summary: RoutineSummary,
    /** Límites honestos y accionables («Sin barra de dominadas ni bandas no hay tracción: …»). */
    val notes: List<String>,
    /** Medidas para pruebas y para el informe de cobertura. */
    val report: RoutineReport,
)

/** Para la pantalla de «revelado»: nombre sugerido, frase de una línea, resumen por día y 3–5 razones. */
data class RoutineSummary(
    val suggestedName: String,
    val oneLiner: String,
    val days: List<RoutineDaySummary>,
    /** Ejercicios principales de la semana (nombres), sin repetir. */
    val mainExercises: List<String>,
    /** Entre 3 y 5 razones de «por qué este plan»: días → reparto, minutos → estructura, prioridades, variantes. */
    val reasons: List<String>,
    /** Día de la semana (1..7) de la sesión más exigente. */
    val mainSessionDay: Int,
    /** `true` en las disciplinas cuyo catálogo no cubre la disciplina entera: se rotula «versión inicial». */
    val isInitialVersion: Boolean = false,
    /** Qué falta exactamente para que deje de ser «versión inicial» (vacío si no aplica). */
    val initialVersionMissing: List<String> = emptyList(),
)

data class RoutineDaySummary(
    val dayOfWeek: Int,
    val sessionId: String,
    val title: String,
    val focus: String,
    val estimatedMinutes: Int,
    val exerciseCount: Int,
    val mainExercises: List<String>,
    val isMain: Boolean,
    val kind: RoutineSessionKind,
    val place: TrainingPlace?,
)

/** Medidas del resultado (pensadas para pruebas, no para la pantalla). */
data class RoutineReport(
    /** Patrones realmente presentes en la semana. */
    val patternsCovered: Set<RoutinePattern>,
    /** Patrones que el reparto pedía y el material/catálogo no permitió cubrir en NINGUNA sesión. */
    val patternsMissing: Set<RoutinePattern>,
    /** Series directas (músculo principal) por semana y músculo canónico, calculadas con `VolumeCalculator`. */
    val weeklyDirectSets: Map<String, Double>,
    /** Series indirectas (secundarias y estabilizadoras) por semana y músculo canónico. */
    val weeklyIndirectSets: Map<String, Double>,
    /** Techo duro semanal de series directas por músculo (MRV, ajustado por nivel): nunca se supera. */
    val weeklyDirectCeilings: Map<String, Int>,
    /** Objetivo blando semanal de series directas por músculo (MAV, ajustado por nivel): hasta ahí se añaden series para llenar minutos. */
    val weeklyDirectTargets: Map<String, Int> = emptyMap(),
    /** Volumen mínimo efectivo semanal (MEV, personalizado) por músculo: por debajo de esto el estímulo es de mantenimiento. */
    val weeklyMinimums: Map<String, Int> = emptyMap(),
    /** Minutos estimados por sesión (con `SessionDurationEstimator`), en el orden de la semana. */
    val sessionMinutes: List<Int>,
    /** Rango objetivo de minutos por sesión: [85 %, 110 %] del objetivo y nunca menos de 20. */
    val minutesWindow: IntRange,
    val sessions: List<RoutineSessionReport>,
    /** Día de la semana de la sesión más exigente. */
    val mainSessionDay: Int,
    /** Veces que el ajuste a minutos no pudo entrar en la ventana (con su nota). */
    val minutesMisses: Int,
)

data class RoutineSessionReport(
    val dayOfWeek: Int,
    val title: String,
    val kind: RoutineSessionKind,
    val estimatedMinutes: Int,
    val strengthExerciseCount: Int,
    val hasCardio: Boolean,
    val hasMobility: Boolean,
    val patterns: List<RoutinePattern>,
    val configurationIds: List<String>,
    val place: TrainingPlace?,
    val inWindow: Boolean,
)
