package com.example.kpkn.domain.auge

import com.example.kpkn.data.models.DailyWellbeingLog
import com.example.kpkn.data.models.ManualMuscleBatteryOverride
import com.example.kpkn.data.models.RecoveryChannelId
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

internal const val MANUAL_ANCHOR_WINDOW_MS = 10L * 24 * 3_600_000L

/** Respaldo de los JSON antiguos: ancla común y, en último término, inicio del día de la fila. */
internal fun DailyWellbeingLog.legacyManualAnchorMs(zone: ZoneId): Long = manualBatteryAnchorMs
    ?: runCatching { LocalDate.parse(date.take(10)).atStartOfDay(zone).toInstant().toEpochMilli() }.getOrDefault(0L)

internal fun DailyWellbeingLog.manualAnchorMs(channel: RecoveryChannelId, zone: ZoneId): Long = when (channel) {
    RecoveryChannelId.SYSTEM -> manualNeuralAnchorMs
    RecoveryChannelId.STRUCTURE -> manualSpinalAnchorMs
    RecoveryChannelId.MUSCULAR -> manualMuscularAnchorMs
} ?: legacyManualAnchorMs(zone)

internal fun DailyWellbeingLog.legacyMuscleAnchorMs(muscle: String, zone: ZoneId): Long =
    manualMuscleAnchorMs.entries.firstOrNull { getAugeMusclePillarId(it.key) == getAugeMusclePillarId(muscle) }?.value
        ?: legacyManualAnchorMs(zone)

/** Fusión pura por canal en (ahora − 10 días, ahora]; conserva el bienestar más reciente. */
internal fun effectiveManualAnchors(
    rows: List<DailyWellbeingLog>,
    nowMs: Long,
    zone: ZoneId,
): DailyWellbeingLog? {
    val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
    val recent = rows.filter { row ->
        runCatching { LocalDate.parse(row.date.take(10)) in today.minusDays(10)..today }.getOrDefault(false)
    }.sortedWith(compareBy<DailyWellbeingLog> { it.date }.thenBy { it.id })
    val base = recent.firstOrNull { it.date == today.toString() }
    val window = (nowMs - MANUAL_ANCHOR_WINDOW_MS + 1)..nowMs
    fun globalValue(row: DailyWellbeingLog, channel: RecoveryChannelId) = when (channel) {
        RecoveryChannelId.SYSTEM -> row.manualNeuralBattery
        RecoveryChannelId.STRUCTURE -> row.manualSpinalBattery
        RecoveryChannelId.MUSCULAR -> row.manualMuscularBattery
    }
    val resets = RecoveryChannelId.entries.mapNotNull { channel ->
        recent.mapNotNull { it.manualBatteryResetMs[channel] }.filter { it in window }.maxOrNull()
            ?.let { channel to it }
    }.toMap()
    val globals = RecoveryChannelId.entries.associateWith { channel ->
        recent.mapNotNull { row -> globalValue(row, channel)?.let { it to row.manualAnchorMs(channel, zone) } }
            .filter { (_, anchor) -> anchor in window && anchor > (resets[channel] ?: Long.MIN_VALUE) }
            .maxByOrNull { it.second }
    }
    val muscleResets = mutableMapOf<String, Long>()
    recent.forEach { row -> row.manualMuscleResetMs.forEach { (key, time) ->
        val muscle = getAugeMusclePillarId(key)
        if (time in window) muscleResets[muscle] = maxOf(muscleResets[muscle] ?: Long.MIN_VALUE, time)
    } }
    data class MuscleAnchor(val battery: Int, val time: Long, val v2: ManualMuscleBatteryOverride?)
    val muscles = mutableMapOf<String, MuscleAnchor>()
    fun accept(key: String, anchor: MuscleAnchor) {
        val muscle = getAugeMusclePillarId(key)
        if (anchor.time in window && anchor.time > (muscleResets[muscle] ?: Long.MIN_VALUE) &&
            anchor.time >= (muscles[muscle]?.time ?: Long.MIN_VALUE)) muscles[muscle] = anchor
    }
    recent.forEach { row ->
        remapMuscleIntMapToPillars(row.manualMuscleBatteries).forEach { (muscle, value) ->
            accept(muscle, MuscleAnchor(value, row.legacyMuscleAnchorMs(muscle, zone), null))
        }
        row.manualMuscleOverridesV2.forEach { (muscle, anchor) ->
            accept(muscle, MuscleAnchor(anchor.battery, anchor.anchorEpochMs, anchor))
        }
    }
    if (base == null && globals.values.all { it == null } && muscles.isEmpty()) return null
    // Sin fila nueva no cambia el resto del bienestar al cruzar medianoche, ni su procedencia.
    return (base ?: recent.last()).copy(
        manualNeuralBattery = globals[RecoveryChannelId.SYSTEM]?.first,
        manualNeuralAnchorMs = globals[RecoveryChannelId.SYSTEM]?.second,
        manualSpinalBattery = globals[RecoveryChannelId.STRUCTURE]?.first,
        manualSpinalAnchorMs = globals[RecoveryChannelId.STRUCTURE]?.second,
        manualMuscularBattery = globals[RecoveryChannelId.MUSCULAR]?.first,
        manualMuscularAnchorMs = globals[RecoveryChannelId.MUSCULAR]?.second,
        manualMuscleBatteries = muscles.filterValues { it.v2 == null }.mapValues { it.value.battery },
        manualMuscleAnchorMs = muscles.filterValues { it.v2 == null }.mapValues { it.value.time },
        manualMuscleOverridesV2 = muscles.mapNotNull { (key, value) -> value.v2?.let { key to it } }.toMap(),
        manualBatteryResetMs = resets,
        manualMuscleResetMs = muscleResets,
    )
}
