package com.example.kpkn.domain.training

import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.data.models.WorkoutLog
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.floor
import kotlin.math.roundToInt

/*
 * Panel de series por músculo y ventana (T5a). Es una PROYECCIÓN de lo ya contado por [VolumeCalculator.calculateCompletedVolume]
 * (misma ventana de calendario, mismo máximo por ocurrencia y rol, mismo divisor): no reimplementa el conteo. Solo lo pide registro a
 * registro para poder decir cuántas sesiones y qué día fue la última de cada ejercicio, y suma los resultados. La suma del desglose por
 * ejercicio ES la cifra de la fila, por construcción.
 *
 * No es recuperación ni carga: son series REGISTRADAS.
 */

/** Ventanas del panel, en semanas completas que terminan hoy (1 semana = los últimos 7 días, 4 = 28 y 12 = 84). */
enum class MuscleVolumeWindow(val weeks: Int) {
    WEEK_1(1),
    WEEKS_4(4),
    WEEKS_12(12);

    val days: Int get() = weeks * 7
}

/**
 * Lo que la persona pide al panel. [averagePerWeek] divide entre las semanas de la ventana (el divisor de [TrainingWindow]); en falso
 * se muestra el total. [mode] y [adjustByIntensity] son los de la pestaña Volumen del programa. Con [programId] solo cuentan los
 * registros de ese programa; sin él, todos.
 */
data class MuscleVolumePanelRequest(
    val window: MuscleVolumeWindow = MuscleVolumeWindow.WEEKS_4,
    val averagePerWeek: Boolean = true,
    val mode: VolumeCountingMode = VolumeCountingMode.DIRECT,
    val adjustByIntensity: Boolean = false,
    val programId: String? = null,
)

/** Un ejercicio que explica la cifra de un músculo: sus series aportadas, las sesiones donde apareció y la última fecha local. */
data class PanelContributor(
    val exerciseKey: String,
    val exerciseName: String,
    val sets: Double,
    val sessions: Int,
    val lastDate: LocalDate?,
)

/** Una cabeza o porción del músculo con su rol. Las porciones comparten series: NO se suman al músculo. */
data class PanelPortion(
    val name: String,
    val role: MuscleRole,
    val sets: Double,
)

/**
 * Intensidad de color de una región del cuerpo. [NO_DATA] (ventana sin registros) es distinto de [ZERO] (ventana con registros y
 * ninguna serie de ese músculo): nada se pinta como «cero perfecto» por ausencia de datos.
 */
enum class VolumeShade { NO_DATA, ZERO, L1, L2, L3, L4, L5 }

/**
 * Una fila del panel. [sets] es null cuando la ventana no tiene registros asignables («sin datos»); un 0.0 es un cero real. [sets] es
 * exactamente la suma de [contributors].
 */
data class MuscleVolumeRow(
    val muscleName: String,
    val sets: Double?,
    val contributors: List<PanelContributor>,
    val portions: List<PanelPortion>,
    val roles: RoleSeparatedMuscleVolume,
    val shade: VolumeShade,
) {
    val hasData: Boolean get() = sets != null
}

/**
 * Resultado del panel. [hasData] es falso si la ventana no tiene ningún registro asignable (y entonces ninguna fila trae cifra);
 * [unassignedCount] son los registros que no se pudieron situar en el calendario y NO suman; [logCount] los que sí cuentan.
 */
data class MuscleVolumePanel(
    val request: MuscleVolumePanelRequest,
    val window: TrainingWindow,
    val rows: List<MuscleVolumeRow>,
    val hasData: Boolean,
    val logCount: Int,
    val unassignedCount: Int,
) {
    /** La mayor cifra de la ventana: la barra de cada fila es relativa a ella. */
    val maxSets: Double get() = rows.maxOfOrNull { it.sets ?: 0.0 } ?: 0.0

    /** Unidad de las cifras: «series/sem» con promedio semanal y «series» con el total de la ventana. */
    val unit: String get() = MuscleVolumeScale.unitLabel(request.averagePerWeek)

    fun row(muscleName: String): MuscleVolumeRow? = rows.firstOrNull { it.muscleName == muscleName }
}

/**
 * Escala de color del cuerpo. Cinco escalones visuales de 4 series semanales (menos de 4, 4–8, 8–12, 12–16 y 16 o más); son un
 * criterio de lectura para distinguir regiones, NO una prescripción de volumen. Con el total de la ventana, los umbrales se multiplican
 * por sus semanas para que el color de un músculo no cambie solo por ver el total en vez del promedio.
 */
object MuscleVolumeScale {
    /** Techo (exclusivo) de L1 a L4, en series por semana; L5 es de ahí en adelante. */
    val weeklyStepBounds: List<Double> = listOf(4.0, 8.0, 12.0, 16.0)

    fun unitLabel(averagePerWeek: Boolean): String = if (averagePerWeek) "series/sem" else "series"

    fun shade(sets: Double?, window: MuscleVolumeWindow, averagePerWeek: Boolean): VolumeShade {
        if (sets == null) return VolumeShade.NO_DATA
        if (sets <= 0.0) return VolumeShade.ZERO
        val weekly = if (averagePerWeek) sets else sets / window.weeks
        return when {
            weekly < weeklyStepBounds[0] -> VolumeShade.L1
            weekly < weeklyStepBounds[1] -> VolumeShade.L2
            weekly < weeklyStepBounds[2] -> VolumeShade.L3
            weekly < weeklyStepBounds[3] -> VolumeShade.L4
            else -> VolumeShade.L5
        }
    }

    /** Leyenda: cada escalón con su rango en la unidad de la vista. «0» es un cero real; «sin datos» lo añade quien dibuja. */
    fun legend(window: MuscleVolumeWindow, averagePerWeek: Boolean): List<Pair<VolumeShade, String>> {
        val factor = if (averagePerWeek) 1 else window.weeks
        val bounds = weeklyStepBounds.map { (it * factor).roundToInt() }
        return listOf(
            VolumeShade.ZERO to "0",
            VolumeShade.L1 to "menos de ${bounds[0]}",
            VolumeShade.L2 to "${bounds[0]}–${bounds[1]}",
            VolumeShade.L3 to "${bounds[1]}–${bounds[2]}",
            VolumeShade.L4 to "${bounds[2]}–${bounds[3]}",
            VolumeShade.L5 to "${bounds[3]} o más",
        )
    }
}

object MuscleVolumePanelBuilder {

    /**
     * La ventana de calendario del panel: últimos 7, 28 u 84 días con hoy incluido, en [zone]. Con promedio usa el divisor de
     * [TrainingWindow.lastDays] (días / 7); con total, [TrainingWindow.total] (divisor 1).
     */
    fun windowFor(request: MuscleVolumePanelRequest, today: LocalDate, zone: ZoneId): TrainingWindow =
        if (request.averagePerWeek) {
            TrainingWindow.lastDays(request.window.days, today, zone)
        } else {
            TrainingWindow.total(today.minusDays(request.window.days.toLong() - 1), today, zone)
        }

    fun build(
        logs: List<WorkoutLog>,
        exerciseIndex: Map<String, ExerciseMuscleInfo>,
        request: MuscleVolumePanelRequest,
        today: LocalDate,
        zone: ZoneId,
    ): MuscleVolumePanel {
        val window = windowFor(request, today, zone)
        val selection = window.selectLogs(logs, request.programId)
        val hasData = selection.logs.isNotEmpty()
        val accumulators = linkedMapOf<String, MuscleAccumulator>()

        selection.logs.forEach { log ->
            val date = TrainingWindow.calendarDate(log.actualDate ?: log.date, zone)
            // Un registro por vez con la contabilidad canónica: así se conoce la sesión y la fecha de cada aporte.
            val result = VolumeCalculator.calculateCompletedVolume(
                logs = listOf(log),
                exerciseIndex = exerciseIndex,
                window = window,
                mode = request.mode,
                adjustByIntensity = request.adjustByIntensity,
                programId = request.programId,
            )
            result.muscles.forEach { (muscle, breakdown) ->
                val acc = accumulators.getOrPut(muscle) { MuscleAccumulator() }
                breakdown.exercises.forEach { exercise ->
                    val contributor = acc.contributors.getOrPut(exercise.exerciseId) { ContributorAccumulator(exercise.exerciseName) }
                    contributor.sets += exercise.sets
                    contributor.sessions += 1
                    if (date != null && (contributor.lastDate == null || date.isAfter(contributor.lastDate))) contributor.lastDate = date
                }
                breakdown.portions.forEach { portion ->
                    val key = portion.name to portion.role
                    acc.portions[key] = (acc.portions[key] ?: 0.0) + portion.sets
                }
                acc.direct += breakdown.roles.directSets
                acc.secondary += breakdown.roles.secondarySets
                acc.stabilizer += breakdown.roles.stabilizerSets
            }
        }

        val canonicalOrder = VolumeCalculator.standardVolumeMuscles.toList()
        val rows = canonicalOrder.map { muscle ->
            val acc = accumulators[muscle]
            val contributors = acc?.contributors?.map { (key, value) ->
                PanelContributor(key, value.name, value.sets, value.sessions, value.lastDate)
            }.orEmpty().sortedWith(compareByDescending<PanelContributor> { it.sets }.thenBy { it.exerciseName })
            val sets = if (hasData) contributors.sumOf { it.sets } else null
            MuscleVolumeRow(
                muscleName = muscle,
                sets = sets,
                contributors = if (hasData) contributors else emptyList(),
                portions = if (hasData) {
                    acc?.portions?.map { (key, value) -> PanelPortion(key.first, key.second, value) }.orEmpty()
                        .sortedWith(compareByDescending<PanelPortion> { it.sets }.thenBy { it.name })
                } else {
                    emptyList()
                },
                roles = if (acc != null && hasData) RoleSeparatedMuscleVolume(acc.direct, acc.secondary, acc.stabilizer) else RoleSeparatedMuscleVolume(),
                shade = MuscleVolumeScale.shade(sets, request.window, request.averagePerWeek),
            )
        }.sortedWith(compareByDescending<MuscleVolumeRow> { it.sets ?: 0.0 }.thenBy { canonicalOrder.indexOf(it.muscleName) })

        return MuscleVolumePanel(
            request = request,
            window = window,
            rows = rows,
            hasData = hasData,
            logCount = selection.logs.size,
            unassignedCount = selection.unassignedCount,
        )
    }

    /**
     * Reparte el redondeo a décimas para que las líneas visibles sumen la cifra visible de su total (método del mayor resto). Devuelve
     * décimas enteras: 12,34 → 123. La suma de lo devuelto es `round(suma × 10)`.
     */
    fun reconcileTenths(values: List<Double>): List<Int> {
        val tenths = values.map { floor(it * 10.0 + 1e-9).toInt() }.toMutableList()
        val target = (values.sum() * 10.0).roundToInt()
        val order = values.indices.sortedByDescending { values[it] * 10.0 - tenths[it] }
        order.take((target - tenths.sum()).coerceAtLeast(0)).forEach { tenths[it]++ }
        return tenths
    }

    private class MuscleAccumulator {
        val contributors = linkedMapOf<String, ContributorAccumulator>()
        val portions = linkedMapOf<Pair<String, MuscleRole>, Double>()
        var direct = 0.0
        var secondary = 0.0
        var stabilizer = 0.0
    }

    private class ContributorAccumulator(val name: String) {
        var sets = 0.0
        var sessions = 0
        var lastDate: LocalDate? = null
    }
}
