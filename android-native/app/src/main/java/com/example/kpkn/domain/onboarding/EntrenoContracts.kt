package com.example.kpkn.domain.onboarding

import com.example.kpkn.domain.training.ProgramCalendarEngine
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlinx.serialization.Serializable

/*
 * Contratos del módulo de Entreno v2 (ver docs/WIZARD_ENTRENO_V2.md).
 *
 * Solo tipos puros y sus textos de interfaz. Los pasos del wizard, el borrador, los motores y los componentes
 * visuales comparten ESTOS enums para no duplicar vocabulario: nadie define otra lista de lugares, implementos,
 * músculos u objetivos.
 */

/**
 * Dónde se entrena. Se elige uno o varios; el material disponible es la UNIÓN de lo que ofrece cada lugar y,
 * si hay más de uno, cada día de entreno puede asignarse a un lugar concreto.
 */
@Serializable
enum class TrainingPlace(val label: String) {
    GYM("Gimnasio"),
    HOME("En casa"),
    PUBLIC("En espacios públicos"),
}

/**
 * Perfiles del paso GOAL. Los tres primeros son **generales** (el programa lo genera KPKN a partir de tu material,
 * tus días y tu tiempo); los siete últimos son **específicos** (llevan a planes de autor de esa disciplina y dependen
 * del material disponible).
 */
@Serializable
enum class TrainingGoalProfile(val label: String, val tagline: String, val isSpecific: Boolean) {
    STRENGTH_MUSCLE("Fuerza y masa muscular", "Pesas y máquinas con una semana sencilla que se repite.", false),
    STRENGTH_CARDIO("Fuerza y cardio", "Atleta híbrido: días de fuerza, de cardio y mixtos.", false),
    FUNCTIONAL_HEALTH("Funcional y saludable", "Fuerza, cardio, potencia y movilidad para sentirte bien.", false),
    POWERBUILDING("Powerbuilding", "Fuerza máxima y estética a la vez.", true),
    CALISTHENICS("Calistenia", "Domina tu peso corporal.", true),
    BODYBUILDING("Culturismo", "Volumen e hipertrofia al detalle.", true),
    WEIGHTLIFTING("Halterofilia", "Arranque y dos tiempos.", true),
    ARMWRESTLING("Armwrestling", "Antebrazo, agarre y fuerza de mesa.", true),
    STRONGMAN("Strongman", "Fuerza bruta y levantamientos de evento.", true),
    POWERLIFTING("Powerlifting", "Sentadilla, banca y peso muerto.", true);

    companion object {
        val general: List<TrainingGoalProfile> = entries.filterNot { it.isSpecific }
        val specific: List<TrainingGoalProfile> = entries.filter { it.isSpecific }
    }
}

/**
 * Implementos del selector de material. Un símbolo = un implemento reconocible; el motor sigue hablando en
 * categorías y llaves curadas (`EquipmentAvailability`), y `EquipmentSymbols` (dominio) traduce en los dos sentidos.
 *
 * Solo hay símbolo de lo que algún ejercicio o tipo de cardio consume: la cuerda de saltar salió de la cuadrícula
 * (ningún ejercicio del catálogo la usa; comba: pendiente de alta en el catálogo) y «Cardio» abre las máquinas del
 * paso CARDIO_TYPE.
 */
@Serializable
enum class EquipmentSymbolId(val label: String) {
    BARBELL("Barra y discos"),
    RACK("Rack"),
    BENCH("Banco"),
    DUMBBELLS("Mancuernas"),
    KETTLEBELL("Kettlebell"),
    CABLE("Poleas"),
    MACHINES("Máquinas"),
    SMITH("Smith/Multipower"),
    PULL_UP_BAR("Barra de dominadas"),
    PARALLEL_BARS("Paralelas"),
    RINGS("Anillas o TRX"),
    BANDS("Bandas elásticas"),
    BALL("Balón"),
    BOX("Cajón o step"),
    CARDIO("Cardio"),

    /** Exclusivo: sin ningún implemento; se entrena con el cuerpo. */
    BODYWEIGHT_ONLY("Solo peso corporal"),
}

/**
 * Músculos del selector «¿Qué músculos priorizas?». Solo los populares: nada de «redondo mayor» ni
 * «multífidos». El trapecio no tiene símbolo: va incluido en «Espalda». La traducción a los músculos canónicos
 * del motor vive en `MuscleSymbols` (dominio).
 */
@Serializable
enum class MuscleSymbol(val label: String) {
    CHEST("Pecho"),
    BACK("Espalda"),
    SHOULDERS("Hombros"),
    BICEPS("Bíceps"),
    TRICEPS("Tríceps"),
    FOREARMS("Antebrazos"),
    ABS("Abdomen"),
    GLUTES("Glúteos"),
    QUADS("Cuádriceps"),
    HAMSTRINGS("Isquios"),
    CALVES("Pantorrillas"),
}

/** Ejercicios de peso corporal por los que se pregunta («¿Qué ejercicios ya te salen?») para ofrecer variantes más fáciles o más difíciles. */
@Serializable
enum class CapabilitySkill(val label: String) {
    PULL_UP("Dominadas"),
    PUSH_UP("Flexiones"),
    DIP("Fondos"),
    PISTOL_SQUAT("Sentadilla a una pierna"),
}

/** Cuánto te sale un [CapabilitySkill]. El orden es el nivel (0, 1, 2). */
@Serializable
enum class CapabilityLevel(val label: String) {
    NONE("Aún no"),
    SOME("Algunas"),
    MANY("Varias"),
}

/** Levantamientos cuya marca (1RM o mejor serie) se puede declarar. Qué se pregunta depende del objetivo. */
@Serializable
enum class LiftMark(val label: String) {
    SQUAT("Sentadilla"),
    BENCH("Press banca"),
    DEADLIFT("Peso muerto"),
    OVERHEAD_PRESS("Press militar"),
    SNATCH("Arranque"),
    CLEAN_AND_JERK("Dos tiempos"),
}

/**
 * Grupos de ejercicios que la persona prefiere evitar («¿Qué ejercicios prefieres evitar?»). Cada grupo se traduce a
 * definiciones del catálogo en `AvoidGroups` (dominio); el motor cambia esos ejercicios por otros que trabajen lo mismo.
 */
@Serializable
enum class AvoidGroup(val label: String) {
    BARBELL_SQUAT("Sentadilla con barra"),
    DEADLIFT("Peso muerto"),
    BENCH_PRESS("Press banca"),
    OVERHEAD_PRESS("Press militar"),
    PULL_UPS("Dominadas"),
    DIPS("Fondos"),
    LUNGES("Zancadas"),
    HIP_THRUST("Hip thrust"),
    JUMPS("Saltos e impacto"),
    RUNNING("Correr"),
}

/** Cuánto cardio aguantas hoy («¿Cómo está tu cardio hoy?»): fija la intensidad y la duración del cardio del alta. */
@Serializable
enum class CardioBase(val label: String) {
    LOW("Me cuesta 10 min"),
    MODERATE("Aguanto 20–30 min"),
    HIGH("Corro 45 min"),
    INTERVALS("Hago series rápidas"),
}

/** Estilo en la mesa de armwrestling («¿Cuál es tu estilo en la mesa?»): cambia el trabajo de antebrazo. */
@Serializable
enum class ArmStyle(val label: String) {
    TOP_ROLL("Top roll"),
    HOOK("Hook"),
    PRESS("Press"),
    UNSURE("Aún no lo sé"),
}

/**
 * Cómo se tiene el programa («¿Cómo quieres tu programa?», solo con experiencia «Tengo experiencia»): que KPKN recomiende uno,
 * armarlo más adelante o importar el tuyo. El texto es el de su símbolo.
 */
@Serializable
enum class ProgramSource(val label: String) {
    RECOMMEND("Que KPKN me recomiende uno"),
    LATER("Lo armaré yo más adelante"),
    IMPORT("Importar mi programa"),
}

/**
 * ¿Compites? («¿Compites en powerlifting?»): sí con fecha, sí sin fecha o no. Solo lo pregunta el paso COMPETITION, con
 * disciplina de competición y experiencia intermedia o avanzada. El texto es el de su símbolo.
 */
@Serializable
enum class CompetitionAnswer(val label: String) {
    WITH_DATE("Sí, tengo fecha"),
    NO_DATE("Sí, sin fecha"),
    NO("No compito"),
}

/**
 * Reglas puras de «¿Compites?»: qué disciplinas lo preguntan, qué fechas valen y cuántas semanas hay hasta la competición
 * (la misma cuenta que `PeriodLayout`, que es la que fija el pico y la puesta a punto del programa).
 */
object CompetitionRules {
    /** Disciplinas con competición. Powerbuilding no tiene: su programa no lleva pico. */
    val disciplines: Set<TrainingGoalProfile> = setOf(
        TrainingGoalProfile.POWERLIFTING, TrainingGoalProfile.WEIGHTLIFTING, TrainingGoalProfile.STRONGMAN,
        TrainingGoalProfile.BODYBUILDING, TrainingGoalProfile.ARMWRESTLING, TrainingGoalProfile.CALISTHENICS,
    )

    /** Disciplinas pesadas: su puesta a punto dura dos semanas en el periodizado largo (como en `PeriodLayout`). */
    val heavy: Set<TrainingGoalProfile> = setOf(
        TrainingGoalProfile.POWERLIFTING, TrainingGoalProfile.WEIGHTLIFTING, TrainingGoalProfile.STRONGMAN,
    )

    /** Longitud máxima del nombre del evento. */
    const val MEET_NAME_MAX = 40

    /** Primera fecha válida: dentro de 14 días. */
    const val MIN_DAYS_AHEAD = 14L

    /** Última fecha válida: dentro de 18 meses. */
    const val MAX_MONTHS_AHEAD = 18L

    /** Fechas válidas desde [today], ambos extremos incluidos. */
    fun dateRange(today: LocalDate): ClosedRange<LocalDate> =
        today.plusDays(MIN_DAYS_AHEAD)..today.plusMonths(MAX_MONTHS_AHEAD)

    /** La fecha local `yyyy-MM-dd`, o null si no es una fecha. */
    fun parseMeetDate(iso: String?): LocalDate? = iso?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    /** La fecha existe y cae dentro de [dateRange] desde [today]. */
    fun isValidMeetDate(iso: String?, today: LocalDate): Boolean = parseMeetDate(iso)?.let { it in dateRange(today) } == true

    /** Semanas del programa hasta la competición, ambas incluidas, con las semanas que empiezan en [weekStartDay]. */
    fun weeksUntil(today: LocalDate, meetDate: LocalDate, weekStartDay: Int): Int {
        val first = ProgramCalendarEngine.alignToWeekStart(today, weekStartDay)
        val last = ProgramCalendarEngine.alignToWeekStart(meetDate, weekStartDay)
        return (ChronoUnit.DAYS.between(first, last) / 7).toInt() + 1
    }

    /** La fecha para leer: «14 de marzo de 2027». */
    fun dateLabel(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale("es")))
}

/** Opciones del paso de programa que están disponibles en esta versión del asistente. */
internal object ProgramSourceOptions {
    /** La importación (lector, vista previa y constructor) está encendida desde C3c; apagarla oculta la opción «Importar». */
    const val IMPORT_ENABLED = true
}
