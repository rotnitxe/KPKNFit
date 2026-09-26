package com.example.kpkn.domain.nutrition

import com.example.kpkn.data.models.CalculationOrigin
import com.example.kpkn.data.models.DailyGoalSnapshot
import com.example.kpkn.data.models.NutritionPlan
import com.example.kpkn.data.models.PlanDirection
import java.time.LocalDate

/** Procedencia del objetivo canónico resuelto para una fecha. */
enum class NutritionGoalSource {
    /** Evidencia histórica ya fijada para la fecha. */
    DAILY_SNAPSHOT,

    /** Previsión vigente usada como meta del día (hoy). */
    TODAY_FORECAST,

    /** Previsión del plan para una fecha futura. */
    PLAN_FORECAST,
}

/**
 * Objetivo canónico de un día. Un campo nulo es ausencia/desconocido; un 0
 * explícito es un valor legítimo y viaja como 0.
 */
sealed interface NutritionDayGoal {
    /** Objetivo numérico vigente para la fecha. */
    data class PlanDayTarget(
        val calorieTargetKcal: Int?,
        val proteinGoalG: Int?,
        val carbGoalG: Int?,
        val fatGoalG: Int?,
        val direction: PlanDirection?,
        val calculationOrigin: CalculationOrigin,
        val planId: String? = null,
        val source: NutritionGoalSource,
    ) : NutritionDayGoal {
        /** true si queda alguna evidencia numérica; los ceros cuentan como valor. */
        fun hasGoalValues(): Boolean =
            calorieTargetKcal != null || proteinGoalG != null || carbGoalG != null || fatGoalG != null
    }

    /** Día sin metas numéricas: solo seguimiento. */
    object TrackingOnly : NutritionDayGoal

    /** Ausencia explícita de objetivo (pasado sin evidencia). Nunca un default. */
    object NoGoal : NutritionDayGoal
}

data class NutritionGoalResolution(
    val goal: NutritionDayGoal,
    /**
     * Snapshot histórico que queda fijado para la fecha, o null cuando no hay
     * evidencia que fijar: la ausencia histórica se conserva tal cual.
     */
    val fixSnapshot: DailyGoalSnapshot? = null,
)

/**
 * Resolución del objetivo canónico por fecha. Orden exacto:
 *
 * 1. [DailyGoalSnapshot] de la fecha si existe (histórico inmutable).
 * 2. Para hoy: previsión vigente, que antes de usarse como meta del día queda
 *    fijada como snapshot.
 * 3. Para futuro: previsión del plan (aún sin fijar).
 * 4. Para pasado sin snapshot: [NutritionDayGoal.NoGoal], ausencia explícita.
 *    NUNCA el plan activo actual.
 *
 * Un snapshot sin ningún valor numérico denota un día de solo seguimiento
 * ([NutritionDayGoal.TrackingOnly]).
 */
object NutritionGoalResolver {
    fun resolve(
        date: LocalDate,
        today: LocalDate = LocalDate.now(),
        snapshot: DailyGoalSnapshot? = null,
        todayForecast: NutritionDayGoal.PlanDayTarget? = null,
        planForecast: NutritionDayGoal.PlanDayTarget? = null,
        trackingOnly: Boolean = false,
        capturedAtEpochMs: Long = 0L,
    ): NutritionGoalResolution {
        // 1. La evidencia fijada de la fecha manda siempre: es histórica e inmutable.
        if (snapshot != null) {
            val snapshotGoal = NutritionDayGoal.PlanDayTarget(
                calorieTargetKcal = snapshot.calorieTargetKcal,
                proteinGoalG = snapshot.proteinGoalG,
                carbGoalG = snapshot.carbGoalG,
                fatGoalG = snapshot.fatGoalG,
                direction = snapshot.direction,
                calculationOrigin = snapshot.calculationOrigin,
                planId = snapshot.planId,
                source = NutritionGoalSource.DAILY_SNAPSHOT,
            )
            val goal = if (snapshotGoal.hasGoalValues()) snapshotGoal else NutritionDayGoal.TrackingOnly
            return NutritionGoalResolution(goal = goal, fixSnapshot = null)
        }
        // 4. Pasado sin snapshot: ausencia explícita. NUNCA el plan activo actual.
        if (date.isBefore(today)) {
            return NutritionGoalResolution(goal = NutritionDayGoal.NoGoal, fixSnapshot = null)
        }
        // Modo de solo seguimiento: sin metas que fijar ni fabricar.
        if (trackingOnly) {
            return NutritionGoalResolution(goal = NutritionDayGoal.TrackingOnly, fixSnapshot = null)
        }
        return when {
            // 2. Hoy: la previsión vigente queda fijada antes de usarse como meta del día.
            date == today -> {
                if (todayForecast == null) {
                    NutritionGoalResolution(goal = NutritionDayGoal.NoGoal, fixSnapshot = null)
                } else {
                    val goal = todayForecast.copy(source = NutritionGoalSource.TODAY_FORECAST)
                    NutritionGoalResolution(
                        goal = goal,
                        fixSnapshot = dailyGoalSnapshotOf(goal, date, capturedAtEpochMs),
                    )
                }
            }
            // 3. Futuro: previsión del plan, aún sin fijar.
            else -> NutritionGoalResolution(
                goal = planForecast?.copy(source = NutritionGoalSource.PLAN_FORECAST) ?: NutritionDayGoal.NoGoal,
                fixSnapshot = null,
            )
        }
    }
}

/**
 * Objetivo proyectado por un plan, sin fabricar valores: los ceros explícitos
 * del plan viajan como 0 y solo la ausencia real se expresa como null.
 */
fun planDayTargetOf(plan: NutritionPlan, source: NutritionGoalSource): NutritionDayGoal.PlanDayTarget =
    NutritionDayGoal.PlanDayTarget(
        calorieTargetKcal = plan.calorieTarget,
        proteinGoalG = plan.proteinGoal,
        carbGoalG = plan.carbGoal,
        fatGoalG = plan.fatGoal,
        direction = plan.direction,
        calculationOrigin = plan.calculationOrigin,
        planId = plan.id,
        source = source,
    )

/**
 * Snapshot histórico derivado de un objetivo resuelto. Los ceros explícitos
 * persisten como ceros; null significa ausencia/desconocido.
 */
fun dailyGoalSnapshotOf(
    target: NutritionDayGoal.PlanDayTarget,
    date: LocalDate,
    capturedAtEpochMs: Long,
): DailyGoalSnapshot =
    DailyGoalSnapshot(
        date = date.toString(),
        planId = target.planId,
        calorieTargetKcal = target.calorieTargetKcal,
        proteinGoalG = target.proteinGoalG,
        carbGoalG = target.carbGoalG,
        fatGoalG = target.fatGoalG,
        direction = target.direction,
        calculationOrigin = target.calculationOrigin,
        capturedAtEpochMs = capturedAtEpochMs,
    )

// ─── Previsión semanal versionada ────────────────────────────────────────────

/**
 * Clave del snapshot de cálculo donde viaja la previsión semanal del reparto
 * (`NutritionDayDistribution.T_i = B + α·(E_i − Ē)`) para poder resolver el
 * objetivo de UNA fecha concreta sin re-derivar nada.
 *
 * El nombre de la clave NO cambia (datos ya persistidos); lo que cambia es el
 * formato del valor: `v1` (solo versión de esquema) y `v2` (añade revisión y
 * fecha de efectividad). [decodeWeeklyForecast] sigue leyendo ambos.
 */
const val WEEKLY_FORECAST_KEY = "weeklyForecastV1"

/** Objetivo resuelto de un día de la previsión semanal. */
data class WeeklyForecastDay(
    val date: LocalDate,
    val calorieTargetKcal: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int,
)

/**
 * Previsión semanal completa: además de los días, la REVISIÓN (incrementa con
 * cada reescritura de la misma previsión) y la fecha de efectividad (primer día
 * del periodo que cubre). Los payloads `v1` ya persistidos solo traen la versión
 * de esquema: se leen con revisión 1 y efectividad desconocida (null).
 */
data class WeeklyForecastDocument(
    /** 1 = payload legado `v1|…`; 2 = payload con cabecera `rev`/`eff`. */
    val formatVersion: Int,
    val revision: Int,
    val effectiveDate: LocalDate?,
    val days: List<WeeklyForecastDay>,
)

/**
 * Serializa la previsión semanal como `v2|rev=R|eff=YYYY-MM-DD|YYYY-MM-DD=kcal,protein,carbs,fat|…`.
 * Solo se codifican fechas con objetivo; un reparto vacío produce la cabecera
 * sin entradas. Los lectores legados solo entienden `v1|`, por eso el formato
 * se versiona y no se muta: [decodeWeeklyForecast] acepta ambos.
 */
fun encodeWeeklyForecast(
    targets: List<NutritionEditorDayTarget>,
    revision: Int = 1,
    effectiveDate: LocalDate? = null,
): String {
    val header = buildList {
        add("v2")
        add("rev=$revision")
        effectiveDate?.let { add("eff=$it") }
    }
    val entries = targets
        .sortedBy { it.date }
        .map { target ->
            "${target.date}=${target.calorieTargetKcal},${target.proteinG},${target.carbsG},${target.fatG}"
        }
    return (header + entries).joinToString("|")
}

/**
 * Decodifica la previsión semanal completa; null = formato desconocido o
 * corrupto. `v1|…` (legado, solo versión de esquema) y `v2|rev=…|eff=…|…`.
 */
fun decodeWeeklyForecastDocument(raw: String): WeeklyForecastDocument? = when {
    raw.startsWith("v2|") -> decodeV2(raw)
    raw.startsWith("v1|") -> decodeV1(raw)
    else -> null
}

/** Decodifica solo los días de la previsión; null = formato desconocido o corrupto. */
fun decodeWeeklyForecast(raw: String): List<WeeklyForecastDay>? =
    decodeWeeklyForecastDocument(raw)?.days

private fun decodeV1(raw: String): WeeklyForecastDocument? {
    val body = raw.removePrefix("v1|")
    val days = if (body.isBlank()) {
        emptyList()
    } else {
        runCatching { parseForecastEntries(body) }.getOrNull() ?: return null
    }
    return WeeklyForecastDocument(formatVersion = 1, revision = 1, effectiveDate = null, days = days)
}

private fun decodeV2(raw: String): WeeklyForecastDocument? {
    val tokens = raw.split("|")
    if (tokens.firstOrNull() != "v2") return null
    var revision = 1
    var effectiveDate: LocalDate? = null
    var index = 1
    while (index < tokens.size) {
        val token = tokens[index]
        when {
            token.startsWith("rev=") -> revision = token.removePrefix("rev=").toIntOrNull()?.coerceAtLeast(1) ?: 1
            token.startsWith("eff=") -> effectiveDate = runCatching {
                LocalDate.parse(token.removePrefix("eff="))
            }.getOrNull()
            else -> break
        }
        index++
    }
    val body = tokens.drop(index).joinToString("|")
    val days = if (body.isBlank()) {
        emptyList()
    } else {
        runCatching { parseForecastEntries(body) }.getOrNull() ?: return null
    }
    return WeeklyForecastDocument(formatVersion = 2, revision = revision, effectiveDate = effectiveDate, days = days)
}

private fun parseForecastEntries(body: String): List<WeeklyForecastDay> =
    body.split("|").mapNotNull { entry ->
        val equals = entry.indexOf('=')
        if (equals <= 0) return@mapNotNull null
        val date = LocalDate.parse(entry.substring(0, equals))
        val parts = entry.substring(equals + 1).split(',')
        if (parts.size != 4) return@mapNotNull null
        WeeklyForecastDay(
            date = date,
            calorieTargetKcal = parts[0].toInt(),
            proteinG = parts[1].toInt(),
            carbsG = parts[2].toInt(),
            fatG = parts[3].toInt(),
        )
    }

/**
 * Objetivo proyectado por un plan PARA UNA FECHA: usa el día de la previsión
 * semanal versionada cuando existe (objetivo de reparto ya resuelto) y cae al
 * objetivo plano del plan en caso contrario. Nunca fabrica valores: las fechas
 * fuera de la previsión son el objetivo medio del plan, no un default.
 */
fun planDayTargetForDate(
    plan: NutritionPlan,
    date: LocalDate,
    source: NutritionGoalSource,
): NutritionDayGoal.PlanDayTarget {
    val forecastDay = plan.calculationSnapshot?.inputs?.get(WEEKLY_FORECAST_KEY)
        ?.let(::decodeWeeklyForecast)
        ?.firstOrNull { it.date == date }
    return if (forecastDay != null) {
        NutritionDayGoal.PlanDayTarget(
            calorieTargetKcal = forecastDay.calorieTargetKcal,
            proteinGoalG = forecastDay.proteinG,
            carbGoalG = forecastDay.carbsG,
            fatGoalG = forecastDay.fatG,
            direction = plan.direction,
            calculationOrigin = plan.calculationOrigin,
            planId = plan.id,
            source = source,
        )
    } else {
        planDayTargetOf(plan, source)
    }
}

/**
 * Objetivos calóricos YA FIJADOS para hoy y el pasado: solo evidencia histórica
 * del mismo plan, nunca el plan activo actual ni defaults. Vacío cuando no hay
 * plan o no hay snapshots que fijar. Estos objetivos nunca se mueven y consumen
 * presupuesto del reparto.
 */
fun fixedTargetsFor(
    plan: NutritionPlan?,
    today: LocalDate,
    window: List<LocalDate>,
    snapshots: List<DailyGoalSnapshot>,
): Map<LocalDate, Int> {
    if (plan == null) return emptyMap()
    return window
        .filter { !it.isAfter(today) }
        .mapNotNull { date ->
            val snapshot = snapshots.firstOrNull { it.date == date.toString() && it.planId == plan.id }
                ?: return@mapNotNull null
            snapshot.calorieTargetKcal?.let { date to it }
        }
        .toMap()
}

/**
 * Reparto vigente del plan para las fechas (desde la previsión semanal
 * versionada), para devolverlo intacto cuando un cambio de calendario no tiene
 * solución válida ([NutritionDistributionStatus.KEPT_PREVIOUS]). Vacío si el
 * plan no tiene previsión.
 */
fun previousTargetsFor(plan: NutritionPlan?, dates: List<LocalDate>): Map<LocalDate, Int> {
    if (plan == null) return emptyMap()
    val forecast = plan.calculationSnapshot?.inputs?.get(WEEKLY_FORECAST_KEY)
        ?.let(::decodeWeeklyForecast)
        ?: return emptyMap()
    val byDate = forecast.associateBy { it.date }
    return dates.mapNotNull { date -> byDate[date]?.let { date to it.calorieTargetKcal } }.toMap()
}
