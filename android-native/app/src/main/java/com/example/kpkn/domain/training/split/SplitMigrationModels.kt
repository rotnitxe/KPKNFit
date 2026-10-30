package com.example.kpkn.domain.training.split

import com.example.kpkn.data.models.Program
import com.example.kpkn.data.splits.SplitTemplate
import com.example.kpkn.domain.training.SplitTemporalScope

/**
 * Lo que la pantalla de reparto le pide al motor de migración ([SplitMigrationPlanner]). Todo lo que no sea el programa, el
 * reparto, los días y el resolutor tiene un valor por defecto.
 */
data class MigrationRequest(
    val program: Program,
    /** Reparto del catálogo, o el propio de la persona (id `custom` con su patrón de siete etiquetas). */
    val split: SplitTemplate,
    /**
     * Día de la semana (1 = lunes … 7 = domingo) de cada día de entreno del reparto, **en el orden del reparto**: tantos
     * como días de entreno tiene (`SplitCatalogRules.trainingDays`). Para proponerlos: [SplitMigrationPlanner.proposeWeekdays].
     */
    val weekdays: List<Int>,
    val resolver: ExerciseTraitResolver,
    /** La semana que se muestra en la vista previa; su reparto manda sobre las demás semanas del alcance. */
    val baseWeekId: String,
    /** Semanas destino ya resueltas por el alcance (Semana, Varias, Bloque, Programa). Si no incluye la base, se añade. */
    val scopeWeekIds: List<String> = listOf(baseWeekId),
    /** Alcance elegido: decide cómo se escriben las selecciones de reparto del programa (global, de bloque o de semana). */
    val scope: SplitTemporalScope = SplitTemporalScope.SELECTED_WEEKS,
    /** Bloque del alcance «Bloque». Si es null se usa el de la semana base. */
    val blockId: String? = null,
    /** «Inicio de semana» elegido (1 = lunes). null = no cambiarlo. Con calendario lo fija el calendario. */
    val startDay: Int? = null,
    /**
     * Sesiones hechas o con series registradas. Se obtienen del registro de entrenos: es lo mismo que
     * `ProgramDetailViewModel.completedSessionIds` (`ProgramRepository.executedTrainingEvidence(program).sessionIds`, menos la
     * sesión en curso).
     */
    val completedSessionIds: Set<String> = emptySet(),
    /** Día de hoy (1..7), solo para la semana en curso: los días nuevos de esa semana no caen antes. */
    val todayWeekday: Int? = null,
    /** La semana en curso, a la que se aplica [todayWeekday]. Por defecto, la base. */
    val currentWeekId: String? = null,
    /** Decisiones de la persona: clave de unidad ([UnitPlan.key]) → índice de día del reparto (0 = el primero). */
    val placements: Map<String, Int> = emptyMap(),
    /** Tiempo por sesión que la persona dijo tener. Solo recorta series si [fitToTime] está activo. */
    val targetMinutes: Int? = null,
    /** Recorte por tiempo: apagado por defecto; la persona lo activa como decisión explícita. */
    val fitToTime: Boolean = false,
    /** Permiso explícito para repartir un plan de autor (la persona confirmó el aviso del asistente). */
    val allowAuthored: Boolean = false,
    /** Marca de tiempo de las marcas y de la instantánea: fija (0) para que el resultado sea idéntico entre llamadas. */
    val nowMs: Long = 0L,
)

/** Resultado de [SplitMigrationPlanner.plan]. */
sealed interface MigrationPlan {

    /**
     * No se puede repartir. [reason] es la frase para la persona; si [canUseCleanOrPrebuilt], la pantalla ofrece «Sesiones
     * vacías» y «Con ejercicios de plantilla». [diagnostic] es el detalle técnico de un error inesperado (no se muestra).
     */
    data class Refused(
        val reason: String,
        val canUseCleanOrPrebuilt: Boolean,
        val diagnostic: String? = null,
    ) : MigrationPlan

    /**
     * El plan. [program] es **el programa tal como quedará** si se aplica (con su instantánea para deshacer): lo que la
     * persona ve es exactamente lo que se aplica ([SplitMigrationPlanner.apply] devuelve este mismo programa).
     */
    data class Ok(
        val splitId: String,
        val splitName: String,
        /** Las semanas del alcance, en el orden del programa. La base es la de [baseWeekId]. */
        val weeks: List<WeekPlan>,
        val baseWeekId: String,
        val program: Program,
        /** Avisos de la semana base (se recalculan en cada plan) más los del alcance. */
        val warnings: List<PlanWarning>,
        /** Decisiones con propuesta ya aplicada: nunca bloquean. */
        val decisions: List<PlanDecision>,
        /** Frases cortas para mostrar junto al resultado (estructura cambiada, semanas distintas, tiempos…). */
        val notes: List<String>,
        /** Días de entreno que usa el plan en la semana base, en el orden del reparto (los que arrastrar cambia). */
        val weekdays: List<Int>,
    ) : MigrationPlan {
        /** La semana base. */
        val base: WeekPlan get() = weeks.first { it.weekId == baseWeekId }

        /** Semanas que se reescriben (las que no se saltan). */
        val appliedWeeks: List<WeekPlan> get() = weeks.filter { it.mode != WeekMode.SKIPPED }
    }
}

/** Cómo se reparte una semana del alcance respecto a la base. */
enum class WeekMode {
    /** La semana que se muestra: su reparto manda. */
    BASE,

    /** Mismas sesiones y mismas unidades que la base: cada ejercicio cae en el mismo día (la progresión no se rompe). */
    PARALLEL,

    /** Trae otros ejercicios: se reparte por su cuenta, prefiriendo el día donde cayó el mismo ejercicio en la base. */
    INDEPENDENT,

    /** No se toca ([WeekPlan.skippedReason] dice por qué). */
    SKIPPED,
}

/** Qué le pasa a una sesión en el plan. */
enum class DayKind {
    /** Día nuevo del reparto con sus ejercicios repartidos. */
    OPEN,

    /** Sesión ya hecha: se queda tal cual y ocupa el día del reparto que más se le parece (con check, sin asa). */
    LOCKED,

    /** Sesión que no se toca y no ocupa un día del reparto (opcional, de competición, de otro deporte, solo movilidad/core). */
    EXTRA,
}

/** Un día de la semana tal como queda: título, día real, sesión, ejercicios y minutos. */
data class DayPlan(
    /** Índice del día en el reparto (0 = el primero); −1 en las sesiones [DayKind.EXTRA]. */
    val dayIndex: Int,
    /** Día de la semana (1..7). */
    val weekday: Int,
    /** Etiqueta del reparto («Torso»); en una sesión hecha, su propio nombre. */
    val label: String,
    /** Título de la sesión («Torso A»). */
    val title: String,
    val sessionId: String,
    val kind: DayKind,
    val exerciseCount: Int,
    val minutes: Int,
    /** Músculos principales del día («Pecho · Hombros · Tríceps»). */
    val focus: String,
    /** Rol/color/inicial del día para la tira. */
    val mark: DayMark,
    val exercises: List<String>,
) {
    /** `true` = con check y sin asa de arrastre. */
    val locked: Boolean get() = kind != DayKind.OPEN
}

/** Qué pasó con un ejercicio. */
enum class MoveKind {
    /** Se queda en el día donde ya estaba su sesión (puede cambiar de título: «Torso» → «Torso A»). */
    STAYS,

    /** Va a otro día del reparto. */
    MOVED,

    /** Es de una sesión hecha: no se mueve. */
    LOCKED,
}

/** De dónde venía un ejercicio y a qué día fue. */
data class PlannedMove(
    val exerciseId: String,
    val name: String,
    val fromTitle: String,
    val fromWeekday: Int?,
    val toTitle: String,
    val toWeekday: Int,
    val kind: MoveKind,
    /** Clave de la unidad que viaja (un ejercicio, una superserie entera, un cardio). */
    val unitKey: String,
)

/** Una unidad que el motor mueve entera (un ejercicio, una superserie, un cardio): para las decisiones y el mapa de movimientos. */
data class UnitPlan(
    val key: String,
    val exerciseNames: List<String>,
    val isCardio: Boolean,
    /** Día del reparto (índice) en el que cayó. */
    val dayIndex: Int,
)

/** El plan de una semana del alcance. */
data class WeekPlan(
    val weekId: String,
    val weekName: String,
    val mode: WeekMode,
    /** Por qué se salta (solo con [WeekMode.SKIPPED]): «ya hecha», «sin sesiones», «incluye una competición»… */
    val skippedReason: String? = null,
    /** Los días ordenados por día de la semana (los del reparto y las sesiones extra). */
    val days: List<DayPlan> = emptyList(),
    val moves: List<PlannedMove> = emptyList(),
    /** Series semanales por grupo ([SplitMuscleGroups]), antes → después. */
    val volume: Map<String, Pair<Double, Double>> = emptyMap(),
    /** Frecuencia por grupo (sesiones con ≥ 2 series ponderadas), antes → después. */
    val frequency: Map<String, Pair<Int, Int>> = emptyMap(),
    /** Series quitadas a accesorios para que quepan en el tiempo pedido (solo con el recorte por tiempo activo). */
    val trims: List<SetTrim> = emptyList(),
    /** Aislamientos fusionados por el mismo motivo. */
    val merges: List<ExerciseMerge> = emptyList(),
    /** La mayor diferencia, en series, de un músculo entre antes y después: 0 salvo recortes. */
    val volumeDeviation: Double = 0.0,
    /** Es una semana de descarga: conserva su prescripción reducida y nunca se recorta. */
    val isDeload: Boolean = false,
    val warnings: List<PlanWarning> = emptyList(),
    /** Las unidades de la semana y el día en que cayeron (la base los usa para arrastrar sin recalcular). */
    val units: List<UnitPlan> = emptyList(),
) {
    val applied: Boolean get() = mode != WeekMode.SKIPPED
}

enum class WarningSeverity { INFO, ATTENTION }

/**
 * Un aviso del plan. La pantalla escribe la frase con [kind] y [params]; los textos de ejemplo del lienzo:
 * `DAYS_CHANGED` «Este reparto necesita 5 días y tu semana tiene 4», `FREQ_DOWN` «Pierdes frecuencia en Espalda: de 3 a 2»…
 */
data class PlanWarning(
    val kind: WarningKind,
    val severity: WarningSeverity = WarningSeverity.ATTENTION,
    val params: Map<String, Any> = emptyMap(),
) {
    /** Frase en español (sin la inicial del reparto), por si la pantalla no quiere escribir las suyas. */
    fun text(): String = PlanWarnings.sentence(this)
}

enum class WarningKind {
    /** `added`: List<Int> días nuevos, `removed`: List<Int> días que dejan de entrenarse, `from`/`to`: número de días. */
    DAYS_CHANGED,

    /** `muscle`: String, `from`: Int, `to`: Int. */
    FREQ_DOWN,

    /** `muscles`: List<String> grupos que ganan frecuencia. */
    FREQ_UP,

    /** `a`, `b`: títulos de las dos sesiones seguidas, `muscle`: grupo grande que cargan los dos. */
    ADJACENT_HEAVY,

    /** `title`: String, `count`: Int ejercicios de fuerza (< 3). */
    SHORT_DAY,

    /** `title`: String, `minutes`: Int, `target`: Int. */
    TIME_OVER,

    /** `names`: List<String> ejercicios que no se pudieron clasificar (van al día más corto). */
    UNCLASSIFIED,

    /** `names`: List<String> ejercicios que no encajan del todo con su día (era la única forma de llenarlo). */
    MISFIT,

    /** `sessionIds`: List<String> sesiones hechas que se conservan. */
    LOCKED_KEPT,

    /** `weekId`: String, `reason`: String. */
    SKIPPED_WEEK,

    /** `weekId`: String: la semana de descarga conserva su prescripción reducida. */
    DELOAD_KEPT,
}

/** Una decisión con propuesta: el plan ya lleva la propuesta aplicada, así que nunca bloquea. */
sealed interface PlanDecision {

    /**
     * Un ejercicio sin día natural (cardio, remate de core, sin clasificar o que no encaja): va al día [proposedDay]. Para
     * cambiarlo, la pantalla pone `placements[unitKey] = día` en la petición y vuelve a pedir el plan.
     */
    data class PlaceUnit(
        val unitKey: String,
        val exerciseNames: List<String>,
        val reason: PlaceReason,
        /** Índices de día del reparto donde puede ir. */
        val options: List<Int>,
        val proposedDay: Int,
        /** `true` si el día ya lo eligió la persona (está en `placements`). */
        val chosen: Boolean,
    ) : PlanDecision

    /**
     * El tiempo por sesión: algún día se pasa de [targetMinutes]. La propuesta es [applied] = `false` (dejarlo así); con
     * `fitToTime = true` se quitan [wouldRemoveSets] series de accesorios ([trims]) y los principales no se tocan.
     */
    data class FitTime(
        val targetMinutes: Int,
        /** Días (título → minutos) que pasan de 1,10 × el tiempo pedido sin recorte. */
        val overDays: Map<String, Int>,
        val wouldRemoveSets: Int,
        val wouldMergeExercises: Int,
        /** Días que aún se pasarían aunque se recorte. */
        val stillOverDays: Map<String, Int>,
        val applied: Boolean,
    ) : PlanDecision
}

enum class PlaceReason { CARDIO, CORE, UNCLASSIFIED, MISFIT }

/** Resultado de [SplitMigrationPlanner.apply]. */
sealed interface MigrationOutcome {
    data class Applied(val program: Program, val plan: MigrationPlan.Ok) : MigrationOutcome
    data class NotApplied(val refusal: MigrationPlan.Refused) : MigrationOutcome
}

/** ¿Se puede deshacer el último cambio de reparto? */
enum class UndoState {
    /** No hay nada que deshacer. */
    NONE,

    /** Se puede deshacer: las semanas siguen como las dejó el cambio. */
    AVAILABLE,

    /** Hay un cambio guardado pero ya se editó alguna de sus semanas: deshacer pisaría esas ediciones. */
    EDITED,
}
