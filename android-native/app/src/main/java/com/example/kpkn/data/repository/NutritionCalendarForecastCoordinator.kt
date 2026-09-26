package com.example.kpkn.data.repository

import com.example.kpkn.data.models.DailyGoalSnapshot
import com.example.kpkn.data.models.NutritionPlan
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.models.WorkoutLog
import com.example.kpkn.data.persistence.PersistenceWriteCoordinator
import com.example.kpkn.domain.nutrition.DayExpenditure
import com.example.kpkn.domain.nutrition.NutritionForecastRevisionInput
import com.example.kpkn.domain.nutrition.NutritionSessionInstance
import com.example.kpkn.domain.nutrition.NutritionTrainingCalendarAdapter
import com.example.kpkn.domain.nutrition.NutritionTrainingCalendarInput
import com.example.kpkn.domain.nutrition.WEEKLY_FORECAST_KEY
import com.example.kpkn.domain.nutrition.decodeWeeklyForecastDocument
import com.example.kpkn.domain.nutrition.effectiveOptionalConfirmations
import com.example.kpkn.domain.nutrition.revisedWeeklyForecast
import com.example.kpkn.domain.nutrition.weeklyDistributionModeOf
import com.example.kpkn.domain.nutrition.weeklyForecastPeriodFor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Puerto de persistencia de la previsión semanal. Lo implementa
 * [NutritionRepository] (Room + publicación post-commit); los tests usan una
 * fachada en memoria.
 */
interface NutritionForecastStore {
    /** Id del plan activo observado. */
    val activePlanId: StateFlow<String?>

    /** Planes observados: un cambio EXTERNO (alta/activación/guardado del editor) reactiva la revisión. */
    val plans: StateFlow<List<NutritionPlan>>

    /** Snapshots históricos: evidencia fijada de hoy/pasado (insert-once, nunca se reescriben). */
    val snapshots: StateFlow<List<DailyGoalSnapshot>>

    /**
     * Escritura ATÓMICA de una revisión de previsión. Solo se aplica si el plan
     * [source] sigue siendo exactamente el que se usó para calcularla (nadie lo
     * reescribió en paralelo) y la caché se publica DESPUÉS de confirmar.
     *
     * @return true si la revisión quedó almacenada y publicada.
     */
    suspend fun storeForecastRevision(source: NutritionPlan, revised: NutritionPlan): Boolean
}

/**
 * Publica la revisión en la caché SOLO si la entrada sigue siendo [source]
 * (CAS de caché): un guardado del editor que ya publicó un plan más nuevo
 * jamás queda pisado por una revisión calculada con datos viejos.
 *
 * @return true si la caché quedó actualizada.
 */
fun MutableStateFlow<List<NutritionPlan>>.publishForecastIfSourceUnchanged(
    source: NutritionPlan,
    revised: NutritionPlan,
): Boolean {
    var published = false
    update { plans ->
        val index = plans.indexOfFirst { it.id == revised.id }
        if (index < 0 || plans[index] != source) {
            plans
        } else {
            published = true
            plans.toMutableList().also { it[index] = revised }
        }
    }
    return published
}

/**
 * Vía de escritura de la previsión: el MISMO mutex global que usa el editor
 * (`PersistenceWriteCoordinator`) cubriendo CAS de origen + commit + publicación,
 * de modo que un guardado concurrente no puede quedar ENTRE la escritura de Room
 * y la publicación de la caché. Toda la sección es cancelable sólo hasta el
 * commit; la publicación la envuelve el productor con `NonCancellable`.
 *
 * @param readCurrent fila actual del plan en almacenamiento durable (BD).
 * @param commitRevision escritura durable de la revisión (transacción).
 * @param publish publicación en caché post-commit, dentro de esta misma sección.
 *   Debe ser código NO suspendible (p. ej. `StateFlow.update`): así no espera
 *   hilos externos con el mutex global puesto ni abre una ventana de
 *   cancelación entre el commit y la caché.
 */
class NutritionForecastWriteLane(
    private val readCurrent: suspend (planId: String) -> NutritionPlan?,
    private val commitRevision: suspend (revised: NutritionPlan) -> Unit,
    private val publish: suspend (source: NutritionPlan, revised: NutritionPlan) -> Unit,
    private val mutex: Mutex = PersistenceWriteCoordinator.mutex,
) {
    /**
     * @return true sólo si la revisión se commitió Y se publicó; false = CAS de
     * origen fallido (otro escritor más nuevo), nunca un éxito ficticio.
     */
    suspend fun write(source: NutritionPlan, revised: NutritionPlan): Boolean = mutex.withLock {
        val current = readCurrent(revised.id)
        if (current == null || current != source) return@withLock false
        commitRevision(revised)
        publish(source, revised)
        true
    }
}

/**
 * Actualiza la previsión semanal del plan activo cuando cambia el CALENDARIO
 * de entrenamiento, sin abrir el editor nutricional.
 *
 * Contrato:
 * - Observa programa/calendario/prescripciones/vitales + registro de
 *   entrenamiento (opciones confirmadas) + día + plan activo. NUNCA la ingesta
 *   nutricional ni el gasto registrado: la previsión no compensa comidas.
 * - La firma incluye los insumos REALES del plan (id, base calorías/macros y
 *   modo de reparto) y su periodo, pero NO su previsión derivada (revisión /
 *   payload): ésta la escribe el propio coordinador y incluirla cerraría el
 *   bucle. Cambiar de plan con el MISMO calendario y día SÍ reactiva el reforecast.
 * - No hay motor paralelo: el gasto sale de [NutritionTrainingCalendarAdapter]
 *   y el reparto de [com.example.kpkn.domain.nutrition.NutritionDayDistribution]
 *   (vía `revisedWeeklyForecast`), el mismo que usa el editor.
 * - Éxito sólo si se escribió de verdad; los fallos quedan registrados en
 *   [lastError] y reintentan en el siguiente disparo (sin éxitos ficticios ni
 *   bucles de escritura).
 * - Reloj inyectable y tick de día inyectable: los tests controlan la fecha y
 *   pueden cancelar la colección con [stop].
 */
class NutritionCalendarForecastCoordinator(
    private val store: NutritionForecastStore,
    private val activeProgramId: Flow<String?>,
    private val programs: Flow<List<Program>>,
    private val settings: Flow<Settings>,
    /** Registro de entrenamiento: única fuente de opcionales confirmadas. */
    private val workoutLogs: Flow<List<WorkoutLog>> = flowOf(emptyList()),
    private val scope: CoroutineScope,
    private val clock: () -> LocalDate = { LocalDate.now() },
    private val dayTick: Flow<LocalDate> = dailyForecastTick(clock),
) {
    private val mutex = Mutex()

    @Volatile
    private var job: Job? = null

    /** Firma del último estado evaluado; solo la memoria evita reevaluaciones. */
    @Volatile
    private var lastSignature: String? = null

    /** Último fallo de escritura/publicación, visible para diagnóstico. */
    private val _lastError = MutableStateFlow<Throwable?>(null)
    val lastError: StateFlow<Throwable?> = _lastError.asStateFlow()

    /** Arranque idempotente (la colección sobrevive a varios llamados). */
    fun start(): Job = job ?: scope.launch {
        val planPart = combine(store.activePlanId, store.plans, dayTick) { activePlanId, planList, day ->
            PlanPart(activePlanId = activePlanId, planList = planList, day = day)
        }
        combine(activeProgramId, programs, settings, workoutLogs, planPart) {
                activeId,
                programList,
                currentSettings,
                logList,
                part,
            ->
            Trigger(
                program = programList.firstOrNull { it.id == activeId },
                settings = currentSettings,
                plan = part.planList.firstOrNull { it.id == part.activePlanId },
                logs = logList,
                day = part.day,
            )
        }.collect { trigger -> mutex.withLock { evaluate(trigger) } }
    }.also { assigned -> job = assigned }

    /** Detiene la colección (cancelación limpia; el scope dueño no se toca). */
    fun stop() {
        job?.cancel()
        job = null
    }

    /**
     * Evalúa UN cambio y persiste la revisión si hace falta. Devuelve true solo
     * cuando escribió; los tests lo llaman directamente con el reloj inyectado.
     */
    suspend fun reforecastNow(): Boolean = reevaluate()

    /** Dispara [evaluate] con mutuo exclusión (colección y llamada manual). */
    private suspend fun reevaluate(): Boolean = mutex.withLock { evaluate(currentTrigger()) }

    private suspend fun currentTrigger(): Trigger {
        val activeId = activeProgramId.first()
        return Trigger(
            program = programs.first().firstOrNull { it.id == activeId },
            settings = settings.first(),
            plan = store.plans.value.firstOrNull { it.id == store.activePlanId.value },
            logs = workoutLogs.first(),
            day = clock(),
        )
    }

    private suspend fun evaluate(trigger: Trigger): Boolean {
        val plan = trigger.plan ?: return false
        val priorDocument = plan.calculationSnapshot?.inputs?.get(WEEKLY_FORECAST_KEY)
            ?.let(::decodeWeeklyForecastDocument)
            ?: return false
        // Horizonte y ventana sobre el PERIODO de la previsión vigente (no
        // «hoy..+6»): la firma cubre programa + prescripciones + vitales + día.
        val period = weeklyForecastPeriodFor(priorDocument.days, trigger.day)
        // Misma unión pura que alta y editor: registro real + confirmaciones
        // manuales del calendario (observadas vía `programs`).
        val confirmations = effectiveOptionalConfirmations(
            logs = trigger.logs,
            program = trigger.program,
        )
        val calendar = NutritionTrainingCalendarAdapter.adapt(
            NutritionTrainingCalendarInput(
                program = trigger.program,
                settings = trigger.settings,
                today = period.first(),
                windowDays = period.size.toLong(),
                confirmedOptionalSessions = confirmations,
            ),
        )
        val signature = signatureOf(trigger, period, confirmations, calendar.expendituresByDate)
        if (signature == lastSignature) return false
        val revised = revisedWeeklyForecast(
            NutritionForecastRevisionInput(
                plan = plan,
                today = trigger.day,
                expenditures = calendar.expendituresByDate,
                snapshots = store.snapshots.value,
            ),
        ) ?: run {
            // Nada que cambiar: se memoriza la firma para no reevaluar en cada emisión.
            lastSignature = signature
            return false
        }
        // Se memoriza ANTES de escribir: la propia publicación reactiva la
        // colección y debe caer en el cortafirma, no en otra escritura. Si la
        // escritura falla, se deshace para reintentar en el próximo disparo.
        val previous = lastSignature
        lastSignature = signature
        val outcome = runCatching { store.storeForecastRevision(source = plan, revised = revised) }
        val failure = outcome.exceptionOrNull()
        if (failure != null) {
            if (failure is CancellationException) throw failure
            lastSignature = previous
            _lastError.value = failure
            return false
        }
        val stored = outcome.getOrDefault(false)
        if (stored) {
            _lastError.value = null
        } else {
            lastSignature = previous
        }
        return stored
    }

    /**
     * Firma de los insumos REALES: día, programa, periodo, identidad/base/modo
     * del plan y gasto por fecha (que ya incorpora calendario, prescripciones,
     * variante, vitales y opcionales confirmadas). Excluye el payload de la
     * previsión (derivado por este coordinador) para no bucear en escrituras.
     */
    private fun signatureOf(
        trigger: Trigger,
        period: List<LocalDate>,
        confirmations: Set<NutritionSessionInstance>,
        expenditures: Map<LocalDate, DayExpenditure>,
    ): String = buildString {
        append("day=").append(trigger.day).append('|')
        append("program=").append(trigger.program?.id ?: "-").append('|')
        append("period=")
        period.forEach { append(it).append(',') }
        append('|')
        val plan = trigger.plan
        append("plan=").append(plan?.id ?: "-").append('|')
        append("base=")
            .append(plan?.calorieTarget).append(':')
            .append(plan?.proteinGoal).append(':')
            .append(plan?.carbGoal).append(':')
            .append(plan?.fatGoal).append('|')
        append("mode=").append(
            weeklyDistributionModeOf(plan?.calculationSnapshot?.inputs ?: emptyMap()),
        ).append('|')
        append("confirm=")
        confirmations.sortedWith(compareBy({ it.date }, { it.sessionId })).forEach {
            append(it.sessionId).append('@').append(it.date).append(';')
        }
        append('|')
        expenditures.toSortedMap().forEach { (date, expenditure) ->
            append(date)
            append('=')
            when (expenditure) {
                DayExpenditure.Rest -> append('R')
                DayExpenditure.NotEstimable -> append('N')
                is DayExpenditure.Estimated -> append(expenditure.kcal.toRawBits())
            }
            append(';')
        }
    }

    private data class Trigger(
        val program: Program?,
        val settings: Settings,
        val plan: NutritionPlan?,
        val logs: List<WorkoutLog>,
        val day: LocalDate,
    )

    private data class PlanPart(
        val activePlanId: String?,
        val planList: List<NutritionPlan>,
        val day: LocalDate,
    )
}

/**
 * Tick diario inyectable: emite el día del reloj y espera al inicio del día
 * siguiente. Los tests lo sustituyen por un `StateFlow` controlado.
 */
fun dailyForecastTick(clock: () -> LocalDate): Flow<LocalDate> = flow {
    while (true) {
        emit(clock())
        val zone = ZoneId.systemDefault()
        val now = Instant.now()
        val nextMidnight = now.atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant()
        delay(Duration.between(now, nextMidnight).toMillis().coerceAtLeast(1L))
    }
}
