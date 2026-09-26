package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.exercises.resolveCatalogExerciseInfo
import com.example.kpkn.data.models.KeyDateType
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramKeyDate
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SessionRequirement
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.models.isCompetitionMeet
import com.example.kpkn.data.models.plannedRepAnchor
import com.example.kpkn.domain.calculations.CardioCalorieEngine
import com.example.kpkn.domain.calculations.CardioCalorieInput
import com.example.kpkn.domain.energy.TrainingEnergyEngine
import com.example.kpkn.domain.energy.TrainingEnergyEngine.ResolvedLoadSource
import com.example.kpkn.domain.training.CalendarWeekProjection
import com.example.kpkn.domain.training.ProgramCalendarEngine
import java.time.LocalDate

/**
 * Adaptador REAL del calendario de entrenamiento al gasto previsto por fecha:
 * consume los motores existentes ([ProgramCalendarEngine], [TrainingEnergyEngine],
 * [CardioCalorieEngine]) y NO introduce un estimador paralelo.
 *
 * Reglas de honestidad:
 * - Una sesión sin gasto estimable devuelve [DayExpenditure.NotEstimable], NUNCA
 *   0 (el 0 solo existe como descanso explícito cuando no hay sesión).
 * - `totalKcal.mid > 0` NO basta para estimar: el descanso entre series suma
 *   kcal aunque falte carga externa en algún ejercicio. La estimabilidad exige
 *   evidencia de carga ([TrainingEnergyEngine.resolvePlannedExternalLoadKg]) en
 *   TODAS las series de trabajo, o peso corporal conocido cuando la serie se
 *   apoya en el cuerpo.
 * - Solo cuenta UNA variante por (sesión, fecha): la elegida, o A si no hay
 *   elección; B/C/D son alternativas de A y nunca se suman entre sí.
 * - Las sesiones opcionales solo cuentan si están confirmadas para ESA fecha y
 *   ESA sesión (confirmar una opcional no activa a las demás del día).
 * - El cardio se estima solo con `cardioDetails` y peso corporal válido; la
 *   estimación de fuerza de [TrainingEnergyEngine] ya excluye esos ejercicios,
 *   por lo que no hay doble contabilidad.
 * - Un programa con sesiones que no pueden fecharse (calendario sin ancla o
 *   sesión sin `dayOfWeek`) NUNCA se reporta como descanso: no sabemos qué días
 *   hay entrenamiento.
 */
object NutritionTrainingCalendarAdapter {

    /** Ventana por defecto: hoy + 6 días (semana completa). */
    const val DEFAULT_WINDOW_DAYS = 7L

    /**
     * Sesión materializada de una variante. B/C/D SOLO existen si la variante
     * está materializada en la sesión; A siempre es la sesión base.
     */
    fun variantSessionOf(session: Session, variant: SessionVariant): Session? = when (variant) {
        SessionVariant.A -> session
        SessionVariant.B -> session.sessionB
        SessionVariant.C -> session.sessionC
        SessionVariant.D -> session.sessionD
    }

    /**
     * Gasto calórico previsto de UNA sesión ya materializada (fuerza vía
     * [TrainingEnergyEngine] + cardio vía [CardioCalorieEngine]). null = no
     * estimable: falta carga en alguna serie de trabajo, falta peso corporal
     * para el cardio o el total no cuadra. El 0 nunca es un gasto válido.
     */
    fun estimateVariantKcal(variantSession: Session, settings: Settings): Double? {
        val summary = TrainingEnergyEngine.estimatePlannedSession(variantSession, settings)
        // Fuerza: las contribuciones existen cuando hay series puntuadas, pero
        // `mid > 0` solo garantiza que el motor pudo sumar algo (el descanso
        // entre series suma kcal con solo conocer el peso corporal). Exige
        // además evidencia de carga en TODAS las series de trabajo.
        val strengthKcal = if (summary.exerciseContributions.isNotEmpty()) {
            val mid = summary.totalKcal.mid
            if (mid <= 0) return null
            if (!workingSetsHaveLoadEvidence(variantSession, settings)) return null
            mid.toDouble()
        } else {
            0.0
        }

        val weight = settings.userVitals.weight
        val cardioExercises = (variantSession.exercises + variantSession.parts.flatMap { it.exercises })
            .filter { it.cardioDetails != null }
        // Cardio: solo para ejercicios con cardioDetails. Sin peso válido o con
        // suma 0 el gasto es desconocido, no un descanso.
        val cardioKcal = if (cardioExercises.isNotEmpty()) {
            if (weight == null || !weight.isFinite() || weight <= 0.0) return null
            val total = cardioExercises.sumOf { exercise ->
                val details = exercise.cardioDetails!!
                CardioCalorieEngine.estimate(
                    CardioCalorieInput(
                        details = details,
                        weightKg = weight,
                        durationSeconds = details.effectiveDurationSeconds(),
                    ),
                )
            }
            if (total <= 0.0) return null
            total
        } else {
            0.0
        }

        val total = strengthKcal + cardioKcal
        return if (total > 0.0 && total.isFinite()) total else null
    }

    /**
     * Evidencia de carga por serie (NO un cálculo calórico paralelo): se
     * consulta la MISMA resolución que usa el motor al puntuar
     * ([TrainingEnergyEngine.resolvePlannedExternalLoadKg]) y manda su
     * [TrainingEnergyEngine.ResolvedLoadSource]:
     *
     * - Fuente externa resuelta (peso explícito, %1RM, 1RM+intensidad,
     *   sugerida): la carga del implemento es conocida ⇒ estimable.
     * - `BODYWEIGHT_ONLY`: la fuente declara **0 kg externos de verdad** (el
     *   cuerpo es la carga) ⇒ estimable sólo con peso corporal conocido y
     *   participación corporal real (si no, el motor no la puntúa).
     * - `MISSING`: carga externa **desconocida**. La participación corporal
     *   parcial NO la tapa: una sentadilla con %1RM sin RM resuelto tiene
     *   `externalLoad == null` y `bodyweightParticipation > 0`, pero el
     *   implemento falta ⇒ gasto desconocido ⇒ la sesión no es estimable y el
     *   reparto cae a uniforme provisional (nunca a un número fabricado).
     *
     * Una serie de trabajo (con reps o duración) sin esa evidencia deja la
     * sesión sin estimar, aunque otras series hayan aportado kcal
     * (`totalKcal.mid > 0` por sí solo no prueba nada).
     */
    private fun workingSetsHaveLoadEvidence(session: Session, settings: Settings): Boolean {
        val weight = settings.userVitals.weight
        val bodyweightKnown = weight != null && weight.isFinite() && weight > 0.0
        val strengthExercises = (session.exercises + session.parts.flatMap { it.exercises })
            .filter { it.cardioDetails == null }
        for (exercise in strengthExercises) {
            val dbInfo = resolveCatalogExerciseInfo(
                catalogConfigurationId = exercise.catalogConfigurationId,
                exerciseDbId = exercise.exerciseDbId,
                exerciseId = exercise.exerciseId,
                exerciseName = exercise.name,
            )
            val bodyweightPart = TrainingEnergyEngine.bodyweightParticipation(dbInfo)
            for (set in exercise.sets) {
                val isWorkingSet = set.plannedRepAnchor() != null || set.targetDuration != null
                if (!isWorkingSet) continue
                val resolved = TrainingEnergyEngine.resolvePlannedExternalLoadKg(
                    exercise,
                    set,
                    settings.weightUnit,
                )
                val externalLoad = resolved.externalLoadKg
                val hasEvidence = when (resolved.source) {
                    // Carga del implemento resuelta por la fuente del motor.
                    ResolvedLoadSource.EXPLICIT_WEIGHT,
                    ResolvedLoadSource.PERCENT_1RM,
                    ResolvedLoadSource.ONE_RM_INTENSITY,
                    ResolvedLoadSource.SUGGESTED_FROM_1RM,
                    -> externalLoad != null && externalLoad > 0.0
                    // 0 kg externos declarados por la propia fuente: el cuerpo
                    // es la carga, pero sólo cuenta si el motor la puntúa.
                    ResolvedLoadSource.BODYWEIGHT_ONLY -> bodyweightPart > 0.0 && bodyweightKnown
                    // Externo desconocido: el cuerpo parcial NO lo tapa.
                    ResolvedLoadSource.MISSING -> false
                }
                if (!hasEvidence) return false
            }
        }
        return true
    }

    /** Estimaciones por variante de UNA sesión; B/C/D solo si están materializadas. */
    fun variantEstimatesOf(session: Session, settings: Settings): Map<SessionVariant, Double> {
        val estimates = linkedMapOf<SessionVariant, Double>()
        for (variant in listOf(SessionVariant.A, SessionVariant.B, SessionVariant.C, SessionVariant.D)) {
            val candidate = variantSessionOf(session, variant) ?: continue
            estimateVariantKcal(candidate, settings)?.let { estimates[variant] = it }
        }
        return estimates
    }

    /**
     * Variante que cuenta para UNA sesión en UNA fecha. null/A → A. B/C/D solo
     * cuando la variante materializada existe; si no, se cae a A (igual que el
     * WorkoutViewModel: `sessionB ?: base`). Nunca apunta a una clave sin
     * estimación salvo que la variante exista pero no sea estimable.
     *
     * Precedencia: elección explícita de la entrada por (sesión, fecha) →
     * variante declarada en la [ProgramWeek] del programa → A.
     */
    fun chosenVariantOf(
        session: Session,
        plannedVariant: SessionVariant?,
        weekVariant: SessionVariant? = null,
    ): SessionVariant = when (plannedVariant ?: weekVariant) {
        null, SessionVariant.A -> SessionVariant.A
        SessionVariant.B -> if (session.sessionB != null) SessionVariant.B else SessionVariant.A
        SessionVariant.C -> if (session.sessionC != null) SessionVariant.C else SessionVariant.A
        SessionVariant.D -> if (session.sessionD != null) SessionVariant.D else SessionVariant.A
    }

    /**
     * Convierte el programa calendarizado real en cargas planificadas y gasto
     * por fecha cubriendo TODA la ventana.
     *
     * Días sin sesión = [DayExpenditure.Rest] solo cuando el programa permite
     * afirmar que NO hay entrenamiento (semana calendariada sin sesión ese día,
     * o programa sin sesiones). Si hay sesiones que no pueden fecharse, esos
     * días pasan a [DayExpenditure.NotEstimable].
     */
    fun adapt(input: NutritionTrainingCalendarInput): NutritionTrainingCalendarResult {
        val window = (0L until input.windowDays.coerceAtLeast(1L)).map { input.today.plusDays(it) }
        // Sin programa no hay entrenamiento que proyectar: descanso explícito.
        val program = input.program ?: return NutritionTrainingCalendarResult(
            enabled = false,
            sessions = emptyList(),
            expendituresByDate = window.associateWith { DayExpenditure.Rest },
            plannedWindow = window,
        )
        val weeksById = programWeeksById(program)
        val projection = ProgramCalendarEngine.project(program)
        if (!projection.enabled) {
            // Calendario sin ancla: hay programa pero ninguna fecha es
            // programable. No se afirma «descanso» en ningún día si hay
            // sesiones; solo los programas sin sesiones son descanso explícito.
            val hasSessions = weeksById.values.any { it.sessions.isNotEmpty() }
            val fallback = if (hasSessions) DayExpenditure.NotEstimable else DayExpenditure.Rest
            return NutritionTrainingCalendarResult(
                enabled = false,
                sessions = emptyList(),
                expendituresByDate = window.associateWith { fallback },
                plannedWindow = window,
            )
        }

        // UNA sola fuente de verdad para las opcionales: evidencia real del
        // registro + confirmaciones manuales guardadas en el calendario. La
        // confirmación persistida del programa manda sobre anulaciones externas.
        val confirmations = effectiveOptionalConfirmations(
            program = program,
            extra = input.confirmedOptionalSessions,
        )
        val plannedVariants = input.plannedVariants + plannedVariantsOf(program)

        val windowDatesByWeekId = linkedMapOf<String, MutableList<LocalDate>>()
        for (date in window) {
            val weekId = projection.weekForDate(date)?.weekId ?: continue
            windowDatesByWeekId.getOrPut(weekId) { mutableListOf() }.add(date)
        }

        val sessions = mutableListOf<PlannedSessionLoad>()
        val unplacedDates = mutableSetOf<LocalDate>()
        for ((weekId, dates) in windowDatesByWeekId) {
            val week = projection.weeks.firstOrNull { it.weekId == weekId } ?: continue
            val programWeek = weeksById[weekId] ?: continue
            val weekVariant = programWeek.variant?.let { SessionVariant.valueOf(it.name) }
            for (session in programWeek.sessions) {
                val scheduledDate = sessionDateOf(session, week, program)
                if (scheduledDate == null) {
                    // Sesión sin scheduling: no podemos decir «descanso» en los
                    // días libres de esta semana.
                    unplacedDates += dates
                    continue
                }
                if (scheduledDate !in dates) continue
                // Opcionales: solo cuentan si están confirmadas para ESA sesión
                // Y ESA fecha (confirmar una no activa a las demás del día).
                val optional = session.requirement == SessionRequirement.OPTIONAL
                val instance = NutritionSessionInstance(session.id, scheduledDate)
                val confirmed = instance in confirmations
                if (optional && !confirmed) continue
                sessions += PlannedSessionLoad(
                    sessionId = session.id,
                    date = scheduledDate,
                    variantEstimatesKcal = variantEstimatesOf(session, input.settings),
                    chosenVariant = chosenVariantOf(
                        session = session,
                        plannedVariant = plannedVariants[instance],
                        weekVariant = weekVariant,
                    ),
                    optional = optional,
                    confirmedForDate = confirmed,
                )
            }
        }

        val byDate = NutritionDayDistribution.sessionExpendituresByDate(sessions)
        return NutritionTrainingCalendarResult(
            enabled = true,
            sessions = sessions,
            expendituresByDate = window.associateWith { date ->
                byDate[date]
                    ?: if (date in unplacedDates) DayExpenditure.NotEstimable else DayExpenditure.Rest
            },
            plannedWindow = window,
        )
    }

    /** Índice de semanas ejecutables por id (la proyección solo da fechas). */
    private fun programWeeksById(program: Program): Map<String, ProgramWeek> =
        program.macrocycles
            .flatMap { macro -> macro.blocks }
            .flatMap { block -> block.mesocycles }
            .flatMap { meso -> meso.weeks }
            .associateBy { it.id }

    /**
     * ¿Ocurre REALMENTE [sessionId] el [date] como sesión OPCAIONAL? Reutiliza
     * la MISMA proyección y la MISMA regla de fecha que el gasto previsto
     * (nunca se fabrica un weekday aislado): la sesión debe existir en la semana
     * que contiene esa fecha, ser `OPTIONAL` y caer exactamente en ella.
     * Es la validación que exige cualquier confirmación manual durable.
     */
    fun optionalOccurrenceOf(program: Program?, date: LocalDate, sessionId: String): Boolean {
        if (program == null || sessionId.isBlank()) return false
        val projection = ProgramCalendarEngine.project(program)
        if (!projection.enabled) return false
        val week = projection.weekForDate(date) ?: return false
        val programWeek = programWeeksById(program)[week.weekId] ?: return false
        val session = programWeek.sessions.firstOrNull { it.id == sessionId } ?: return false
        if (session.requirement != SessionRequirement.OPTIONAL) return false
        return sessionDateOf(session, week, program) == date
    }

    /**
     * Fecha planificada de una sesión dentro de su semana. Con `dayOfWeek` se
     * usa DIRECTAMENTE el mapa de fechas reales de la semana (nunca el fallback
     * a `week.startDate` de [ProgramCalendarEngine.scheduledDateFor], que
     * fabricaría una carga fantasma para sesiones sin día). Sin `dayOfWeek`, una
     * sesión de competición resuelve la fecha del evento y debe caer dentro de la
     * semana; cualquier otra sesión sin día no es programable (null).
     */
    private fun sessionDateOf(session: Session, week: CalendarWeekProjection, program: Program): LocalDate? {
        val day = session.dayOfWeek
        if (day != null) {
            return week.trainingDayDates[day.coerceIn(1, 7)]
        }
        if (session.isCompetitionMeet) {
            val event = competitionEventDate(program, session) ?: return null
            return event.takeIf { week.contains(it) }
        }
        return null
    }

    /** Fecha del evento de competición: fecha explícita o key date (misma regla que el motor). */
    private fun competitionEventDate(program: Program, session: Session): LocalDate? {
        ProgramCalendarEngine.parseIsoDate(session.competitionDetails?.competitionDate)?.let { return it }
        val keyDate: ProgramKeyDate? = program.keyDates.firstOrNull { it.id == session.competitionKeyDateId }
            ?: program.keyDates.firstOrNull { it.type == KeyDateType.COMPETITION }
        return ProgramCalendarEngine.parseIsoDate(keyDate?.eventDate)
            ?: ProgramCalendarEngine.parseIsoDate(keyDate?.startDate)
    }
}

/**
 * Confirmaciones REALES de sesiones opcionales por (sesión, fecha), derivadas
 * del registro de entrenamiento YA existente (`WorkoutLog`: `programId`,
 * `sessionId`, `date`/`actualDate`). Es la ÚNICA evidencia productiva: aquí no
 * se inventa ninguna confirmación y una opcional sin registro sigue fuera.
 *
 * Criterio exacto: para cada log del programa se toma la fecha real
 * (`actualDate` si existe, si no `date`), se recorta a día ISO y se empareja
 * con `sessionId`; las entradas se deduplican por (sesión, fecha). Un log de
 * otro programa se ignora. Como hoy no existe UI que «confirme» una opcional
 * futura, las fechas futuras quedan sin confirmar (gasto no estimado en vez de
 * un descanso fabricado) hasta que ese productor exista.
 *
 * Origen productivo del RESTO de la entrada del adaptador:
 * - Variante elegida: `ProgramWeek.variant` del programa (ya leída dentro de
 *   [NutritionTrainingCalendarAdapter.adapt]); `plannedVariants` es SÓLO una
 *   anulación explícita por (sesión, fecha) para quien la guarde.
 *   `OngoingWorkoutState.activeMode` es el modo EN EJECUCIÓN de una sesión en
 *   curso: runtime, no una previsión, y por eso no se usa aquí.
 * - Opcionales: este mismo conjunto; nadie más lo escribe (sin UI de
 *   confirmación todavía, las futuras quedan sin confirmar, no inventadas).
 */
fun confirmedOptionalSessionsOf(
    logs: Collection<com.example.kpkn.data.models.WorkoutLog>,
    programId: String?,
): Set<NutritionSessionInstance> {
    if (programId == null) return emptySet()
    val instances = linkedSetOf<NutritionSessionInstance>()
    for (log in logs) {
        if (log.programId != programId) continue
        val raw = log.actualDate ?: log.date
        val iso = raw.take(10)
        val date = runCatching { LocalDate.parse(iso) }.getOrNull() ?: continue
        instances += NutritionSessionInstance(log.sessionId, date)
    }
    return instances
}

/**
 * Confirmaciones manuales guardadas en el propio programa, ya validadas:
 * día ISO interpretable y sesión no vacía (una clave exacta por sesión+fecha).
 * Un registro corrupto se ignora en lugar de fabricar una sesión.
 */
fun confirmedOptionalSessionsOf(program: Program?): Set<NutritionSessionInstance> =
    validConfirmationsOf(program)
        .map { NutritionSessionInstance(it.sessionId, it.date) }
        .toSet()

/**
 * Variante fijada por esas confirmaciones: UN registro por sesión+fecha (el
 * PRIMERO manda, los duplicados posteriores se ignoran). Si ese primer registro
 * no trae `variantKey` o trae una clave ilegible, se fija **A** explícitamente
 * (no se salta para coger la B de un duplicado): «A si no eligió». Así jamás
 * conviven B y C para la misma instancia.
 */
fun plannedVariantsOf(program: Program?): Map<NutritionSessionInstance, SessionVariant> {
    val variants = linkedMapOf<NutritionSessionInstance, SessionVariant>()
    for (confirmation in validConfirmationsOf(program)) {
        val instance = NutritionSessionInstance(confirmation.sessionId, confirmation.date)
        if (instance in variants) continue
        val declared = confirmation.variantKey
            ?.let { key -> runCatching { SessionVariant.valueOf(key) }.getOrNull() }
        variants[instance] = declared ?: SessionVariant.A
    }
    return variants
}

/**
 * Unión pura y única de las DOS fuentes legítimas de confirmación: evidencia
 * real del registro de entrenamiento + confirmaciones manuales del calendario
 * (+ refuerzo explícito que aporte el consumidor). Es la que usan alta,
 * editor y coordinador, para que los tres vean exactamente lo mismo.
 */
fun effectiveOptionalConfirmations(
    logs: Collection<com.example.kpkn.data.models.WorkoutLog> = emptyList(),
    program: Program? = null,
    extra: Set<NutritionSessionInstance> = emptySet(),
): Set<NutritionSessionInstance> =
    confirmedOptionalSessionsOf(logs, program?.id) +
        confirmedOptionalSessionsOf(program) +
        extra

private data class ValidConfirmation(
    val sessionId: String,
    val date: LocalDate,
    val variantKey: String?,
)

private fun validConfirmationsOf(program: Program?): List<ValidConfirmation> =
    program?.optionalSessionConfirmations.orEmpty().mapNotNull { raw ->
        if (raw.sessionId.isBlank()) return@mapNotNull null
        val date = runCatching { LocalDate.parse(raw.dayIso.trim()) }.getOrNull() ?: return@mapNotNull null
        ValidConfirmation(
            sessionId = raw.sessionId.trim(),
            date = date,
            variantKey = raw.variantKey?.trim()?.takeIf { it.isNotBlank() },
        )
    }

/**
 * Identidad de UNA sesión planificada en UNA fecha. Toda elección que depende
 * de la sesión concreta (variante elegida, confirmación de opcional) se clavea
 * así: la misma sesión en dos fechas son dos instancias distintas y confirmar
 * una opcional NO activa a las otras del mismo día.
 */
data class NutritionSessionInstance(
    val sessionId: String,
    val date: LocalDate,
)

/** Entrada del adaptador de calendario de entrenamiento → gasto nutricional. */
data class NutritionTrainingCalendarInput(
    /** Programa activo; null = calendario desactivado (sin entrenamiento). */
    val program: Program?,
    val settings: Settings,
    /** Ancla de la ventana; por defecto hoy. */
    val today: LocalDate = LocalDate.now(),
    /** Días de la ventana a proyectar; por defecto [NutritionTrainingCalendarAdapter.DEFAULT_WINDOW_DAYS]. */
    val windowDays: Long = NutritionTrainingCalendarAdapter.DEFAULT_WINDOW_DAYS,
    /**
     * Variante elegida por (sesión, fecha). Ausente → la variante declarada en
     * la `ProgramWeek` del programa; sin ninguna → A. Nunca se suman
     * alternativas entre sí.
     */
    val plannedVariants: Map<NutritionSessionInstance, SessionVariant> = emptyMap(),
    /** Sesiones opcionales confirmadas por (sesión, fecha); fuera de este set se omiten. */
    val confirmedOptionalSessions: Set<NutritionSessionInstance> = emptySet(),
)

/** Resultado del adaptador: cargas planificadas y gasto por fecha de la ventana. */
data class NutritionTrainingCalendarResult(
    val enabled: Boolean,
    /** Sesiones planificadas de la ventana (opcionales solo confirmadas). */
    val sessions: List<PlannedSessionLoad>,
    /** Gasto por fecha cubriendo TODA la ventana; días sin sesión = [DayExpenditure.Rest] o [DayExpenditure.NotEstimable]. */
    val expendituresByDate: Map<LocalDate, DayExpenditure>,
    /** Fechas de la ventana [today, today + windowDays). */
    val plannedWindow: List<LocalDate>,
)
