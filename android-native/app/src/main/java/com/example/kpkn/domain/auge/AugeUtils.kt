package com.example.kpkn.domain.auge

import com.example.kpkn.data.models.AthleteType
import com.example.kpkn.data.models.PhysiologicalFloor
import com.example.kpkn.data.models.Settings
import com.example.kpkn.data.models.WorkoutLog
import java.time.Instant
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * Utilidades compartidas para el sistema AUGE (baterías, recuperación, predicción de fatiga).
 */
internal object AugeUtils {

    /**
     * Devuelve el piso fisiológico mínimo para las baterías de acuerdo al tipo de atleta.
     * Estos valores representan el límite máximo de fatiga "segura" que puede tolerar el atleta.
     */
    fun physiologicalFloor(settings: Settings): PhysiologicalFloor = when (settings.athleteType) {
        AthleteType.POWERLIFTER, AthleteType.WEIGHTLIFTER -> PhysiologicalFloor(muscular = 15, cns = 20, spinal = 12)
        AthleteType.BODYBUILDER, AthleteType.POWERBUILDER -> PhysiologicalFloor(muscular = 18, cns = 22, spinal = 14)
        AthleteType.CALISTHENICS -> PhysiologicalFloor(muscular = 20, cns = 24, spinal = 16)
        AthleteType.HYBRID, AthleteType.ZERCHER_LIFTER -> PhysiologicalFloor(muscular = 20, cns = 25, spinal = 18)
        AthleteType.ENTHUSIAST -> PhysiologicalFloor(muscular = 22, cns = 26, spinal = 18)
    }

    /**
     * Convierte la fecha del WorkoutLog (generalmente ISO-8601 o yyyy-MM-dd) a milisegundos desde epoch.
     */
    fun logDateMs(log: WorkoutLog): Long = try {
        java.time.OffsetDateTime.parse(log.date).toInstant().toEpochMilli()
    } catch (e: Exception) {
        try {
            Instant.parse(log.date).toEpochMilli()
        } catch (e2: Exception) {
            try {
                java.time.LocalDate.parse(log.date.take(10))
                    .atStartOfDay(java.time.ZoneId.systemDefault())
                    .toInstant().toEpochMilli()
            } catch (e3: Exception) { 0L }
        }
    }

    fun parseIsoMs(dateString: String): Long = try {
        java.time.OffsetDateTime.parse(dateString).toInstant().toEpochMilli()
    } catch (e: Exception) {
        try {
            Instant.parse(dateString).toEpochMilli()
        } catch (e2: Exception) {
            try {
                java.time.LocalDate.parse(dateString.take(10))
                    .atStartOfDay(java.time.ZoneId.systemDefault())
                    .toInstant().toEpochMilli()
            } catch (e3: Exception) { 0L }
        }
    }

    fun clamp(v: Double, lo: Double, hi: Double): Double = min(hi, max(lo, v))

    fun safeExp(v: Double): Double {
        val r = exp(v)
        return if (r.isNaN() || r.isInfinite()) 0.0 else r
    }

    /**
     * Curva de desaceleración para baterías por debajo de 30%.
     * Suaviza el drenaje de los RINGS evitando que marquen 0% artificialmente.
     * B_final = 30 * sqrt(B_raw / 30)  cuando B_raw < 30
     */
    fun decelerateBattery(battery: Double): Double {
        val b = battery.coerceIn(0.0, 100.0)
        return if (b < 30.0) 30.0 * kotlin.math.sqrt(b / 30.0) else b
    }
}
