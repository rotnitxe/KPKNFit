package com.example.kpkn.domain.auge

import com.example.kpkn.data.models.AugeAdaptiveCache
import com.example.kpkn.data.models.LoadAdvisory
import com.example.kpkn.data.models.LoadAdvisoryLevel
import com.example.kpkn.data.models.RecoveryChannelId
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.WeekFields

data class LoadAdvisoryResult(
    val advisories: List<LoadAdvisory>,
    val cache: AugeAdaptiveCache,
)

data class AxialSessionExercise(
    val name: String,
    val axial: Double,
    val replacementGroup: String? = null,
)

object LoadAdvisoryEngine {
    // Los umbrales de aviso (WATCH / ADJUST / UNLOAD) y la histéresis viven en AcwrThresholds, junto a las zonas.

    fun rank(level: LoadAdvisoryLevel): Int = when (level) {
        LoadAdvisoryLevel.NONE -> 0
        LoadAdvisoryLevel.WATCH -> 1
        LoadAdvisoryLevel.ADJUST -> 2
        LoadAdvisoryLevel.UNLOAD -> 3
    }

    fun topAxialExercises(
        upcoming: List<AxialSessionExercise>,
        limit: Int = 2,
    ): List<AxialSessionExercise> = upcoming
        .filter { it.axial >= 0.5 && it.name.isNotBlank() }
        .sortedByDescending { it.axial }
        .distinctBy { it.name.trim().lowercase() }
        .take(limit)

    fun replacementVariant(
        target: AxialSessionExercise,
        catalog: List<AxialSessionExercise>,
    ): AxialSessionExercise? {
        val group = target.replacementGroup?.takeIf { it.isNotBlank() } ?: return null
        return catalog
            .filter { it.replacementGroup == group && it.axial <= 0.3 }
            .filter { it.name.trim().lowercase() != target.name.trim().lowercase() }
            .minByOrNull { it.axial }
    }

    fun contextualize(
        advisory: LoadAdvisory,
        upcoming: List<AxialSessionExercise>,
        catalog: List<AxialSessionExercise> = emptyList(),
    ): LoadAdvisory {
        if (advisory.channel != RecoveryChannelId.STRUCTURE) return advisory
        if (rank(advisory.level) < rank(LoadAdvisoryLevel.ADJUST)) return advisory
        val top = topAxialExercises(upcoming)
        if (top.isEmpty()) return advisory
        val names = when (top.size) {
            1 -> top[0].name
            else -> "${top[0].name} y ${top[1].name}"
        }
        val replacement = top.firstNotNullOfOrNull { replacementVariant(it, catalog) }
        val evidencePrefix = Regex("""Carga axial reciente: \d+ de los últimos 7 días\. """).find(advisory.body)?.value.orEmpty()
        val body = when (advisory.level) {
            LoadAdvisoryLevel.ADJUST -> buildString {
                append(evidencePrefix)
                append("Ajusta la carga axial de $names hoy")
                if (replacement != null) {
                    append(" o sustituye ${top[0].name} por ${replacement.name}")
                } else {
                    append(" o sustituye un ejercicio por una variante menos axial")
                }
                append(".")
            }
            LoadAdvisoryLevel.UNLOAD -> buildString {
                append("Tendencia reciente de Columna: las lecturas al inicio reflejan una carga que conviene atender. Semana ligera: reduce o sustituye $names")
                if (replacement != null) append(" (p. ej. ${replacement.name})")
                append(".")
            }
            else -> advisory.body
        }
        return advisory.copy(body = body)
    }

    fun badgeLabel(
        advisory: LoadAdvisory,
        upcoming: List<AxialSessionExercise> = emptyList(),
    ): String {
        val top = topAxialExercises(upcoming, limit = 1).firstOrNull()
        if (rank(advisory.level) < rank(LoadAdvisoryLevel.ADJUST)) {
            return if (advisory.channel == RecoveryChannelId.STRUCTURE) "Columna: ${advisory.title}" else advisory.title
        }
        val action = when (advisory.level) {
            LoadAdvisoryLevel.ADJUST -> when (advisory.channel) {
                RecoveryChannelId.STRUCTURE -> "Ajusta la carga axial"
                RecoveryChannelId.SYSTEM -> "Deja 1–2 repeticiones en reserva"
                else -> advisory.title
            }
            LoadAdvisoryLevel.UNLOAD -> "Semana ligera"
            else -> advisory.title
        }
        val focus = top?.takeIf { advisory.channel == RecoveryChannelId.STRUCTURE }?.let { " en ${it.name}" }.orEmpty()
        return "${RecoveryBands.channelTitle(advisory.channel)}: $action$focus"
    }

    fun evaluate(
        axial: AxialLoadReport,
        systemic: SystemicLoadReport,
        cache: AugeAdaptiveCache,
        nowMs: Long = AugeClock.nowMs(),
        dismissed: Set<String> = cache.dismissedAdvisoryKeys,
    ): LoadAdvisoryResult {
        val weekKey = isoWeek(nowMs)
        val today = Instant.ofEpochMilli(nowMs).atZone(ZoneOffset.UTC).toLocalDate().toString()
        val previous = cache.lastAdvisoryLevelByChannel
        val sameDay = cache.lastAdvisoryDay == today

        val structureLevel = levelFor(
            acwr = axial.acwr,
            insufficient = axial.insufficientData,
            extraWatch = axial.axialDaysIn4 >= 3,
            extraAdjust = axial.axialDaysIn6 >= 4 || lowStarts(axial.startTrend, 60, 2),
            extraUnload = unloadFromTrend(axial.acwr, axial.startTrend),
            previous = parseLevel(previous[RecoveryChannelId.STRUCTURE.name]),
            allowDrop = !sameDay,
        )
        val energyLevel = levelFor(
            acwr = systemic.acwr,
            insufficient = systemic.insufficientData,
            extraWatch = systemic.sessionsIn7 >= 6,
            extraAdjust = lowStarts(systemic.startTrend, 60, 2),
            extraUnload = unloadFromTrend(systemic.acwr, systemic.startTrend),
            previous = parseLevel(previous[RecoveryChannelId.SYSTEM.name]),
            allowDrop = !sameDay,
        )

        val built = buildList {
            advisory(
                channel = RecoveryChannelId.STRUCTURE,
                level = structureLevel,
                weekKey = weekKey,
                axial = axial,
            )?.let(::add)
            advisory(
                channel = RecoveryChannelId.SYSTEM,
                level = energyLevel,
                weekKey = weekKey,
                systemic = systemic,
            )?.let(::add)
        }.filter { it.id !in dismissed }

        val nextCache = cache.copy(
            lastAdvisoryLevelByChannel = mapOf(
                RecoveryChannelId.STRUCTURE.name to structureLevel.name,
                RecoveryChannelId.SYSTEM.name to energyLevel.name,
            ),
            lastAdvisoryDay = today,
        )
        return LoadAdvisoryResult(advisories = built, cache = nextCache)
    }

    internal fun levelFor(
        acwr: Double?,
        insufficient: Boolean,
        extraWatch: Boolean,
        extraAdjust: Boolean,
        extraUnload: Boolean,
        previous: LoadAdvisoryLevel,
        allowDrop: Boolean,
    ): LoadAdvisoryLevel {
        if (insufficient && acwr == null && !extraWatch && !extraAdjust && !extraUnload) {
            return LoadAdvisoryLevel.NONE
        }
        val raw = when {
            extraUnload -> LoadAdvisoryLevel.UNLOAD
            extraAdjust || (acwr != null && acwr > AcwrThresholds.ADJUST) -> LoadAdvisoryLevel.ADJUST
            extraWatch || (acwr != null && acwr >= AcwrThresholds.WATCH) -> LoadAdvisoryLevel.WATCH
            else -> LoadAdvisoryLevel.NONE
        }
        return applyHysteresis(raw, previous, allowDrop, acwr)
    }

    internal fun applyHysteresis(
        raw: LoadAdvisoryLevel,
        previous: LoadAdvisoryLevel,
        allowDrop: Boolean,
        acwr: Double? = null,
    ): LoadAdvisoryLevel {
        val order = listOf(
            LoadAdvisoryLevel.NONE,
            LoadAdvisoryLevel.WATCH,
            LoadAdvisoryLevel.ADJUST,
            LoadAdvisoryLevel.UNLOAD,
        )
        val rawIdx = order.indexOf(raw)
        val prevIdx = order.indexOf(previous)
        if (rawIdx >= prevIdx) return raw
        val enter = enterThreshold(previous)
        if (enter != null && acwr != null && acwr >= enter * (1.0 - AcwrThresholds.HYSTERESIS)) {
            return previous
        }
        if (!allowDrop) return previous
        return order[(prevIdx - 1).coerceAtLeast(0)]
    }

    private fun enterThreshold(level: LoadAdvisoryLevel): Double? = when (level) {
        LoadAdvisoryLevel.WATCH -> AcwrThresholds.WATCH
        LoadAdvisoryLevel.ADJUST -> AcwrThresholds.ADJUST
        LoadAdvisoryLevel.UNLOAD -> AcwrThresholds.UNLOAD
        LoadAdvisoryLevel.NONE -> null
    }

    private fun advisory(
        channel: RecoveryChannelId,
        level: LoadAdvisoryLevel,
        weekKey: String,
        axial: AxialLoadReport? = null,
        systemic: SystemicLoadReport? = null,
    ): LoadAdvisory? {
        if (level == LoadAdvisoryLevel.NONE) return null
        if (level == LoadAdvisoryLevel.WATCH) {
            val body = if (channel == RecoveryChannelId.STRUCTURE) {
                "Carga axial reciente algo alta (${axial?.axialDaysIn7 ?: 0} de 7 días). Vigílala en el detalle de Columna."
            } else {
                "Señales recientes de carga de Energía elevadas. No hace falta cambiar el plan todavía."
            }
            return LoadAdvisory(
                id = "${channel.name}|$weekKey|WATCH",
                channel = channel,
                level = level,
                title = RecoveryBands.channelTitle(channel),
                body = body,
                weekKey = weekKey,
            )
        }
        val (title, body) = when {
            channel == RecoveryChannelId.STRUCTURE && level == LoadAdvisoryLevel.ADJUST ->
                "Columna pide un respiro" to
                    "Carga axial reciente: ${axial?.axialDaysIn7 ?: 0} de los últimos 7 días. Ajusta la carga axial hoy: reduce o sustituye sentadilla o peso muerto por una variante menos axial."
            channel == RecoveryChannelId.STRUCTURE && level == LoadAdvisoryLevel.UNLOAD ->
                "Descarga de columna" to
                    "Tendencia reciente de Columna: las lecturas al inicio reflejan una carga que conviene atender. Semana ligera: reduce o sustituye el ejercicio más axial."
            channel == RecoveryChannelId.SYSTEM && level == LoadAdvisoryLevel.ADJUST ->
                "Energía acumulada" to
                    "Señales recientes de carga de Energía. Deja 1–2 repeticiones en reserva y evita el fallo. ${systemic?.sessionsIn7 ?: 0} sesiones en 7 días."
            else ->
                "Descarga de energía" to
                    "Tendencia reciente de Energía: las lecturas al inicio reflejan una carga que conviene atender. Semana ligera: reduce los compuestos demandantes."
        }
        return LoadAdvisory(
            id = "${channel.name}|$weekKey|${level.name}",
            channel = channel,
            level = level,
            title = title,
            body = body,
            weekKey = weekKey,
        )
    }

    private fun lowStarts(trend: List<Int>, threshold: Int, needed: Int): Boolean {
        if (trend.size < needed) return false
        return trend.takeLast(needed).all { it < threshold }
    }

    private fun unloadFromTrend(acwr: Double?, trend: List<Int>): Boolean {
        if (lowStarts(trend, 50, 3)) return true
        if (acwr == null || acwr <= AcwrThresholds.UNLOAD || trend.size < 3) return false
        val last = trend.takeLast(3)
        return last[0] >= last[1] && last[1] >= last[2] && last[0] - last[2] >= 5
    }

    private fun parseLevel(raw: String?): LoadAdvisoryLevel =
        runCatching { LoadAdvisoryLevel.valueOf(raw ?: "NONE") }.getOrDefault(LoadAdvisoryLevel.NONE)

    private fun isoWeek(nowMs: Long): String {
        val date = Instant.ofEpochMilli(nowMs).atZone(ZoneOffset.UTC).toLocalDate()
        val week = date.get(WeekFields.ISO.weekOfWeekBasedYear())
        val year = date.get(WeekFields.ISO.weekBasedYear())
        return "%04d-W%02d".format(year, week)
    }
}
