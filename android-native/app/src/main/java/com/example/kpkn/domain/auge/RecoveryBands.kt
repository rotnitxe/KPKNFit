package com.example.kpkn.domain.auge

import com.example.kpkn.data.models.RecoveryBand
import com.example.kpkn.data.models.RecoveryChannelId

/**
 * Contrato único de semántica de % para los tres rings.
 * 0 % no existe (pisos por AthleteType); 100 % es infrecuente tras entrenar.
 */
object RecoveryBands {
    const val HIGH_MIN = 85
    const val NORMAL_MIN = 70
    const val MODERATE_MIN = 50
    const val LOW_MIN = 35

    /** Umbral a partir del cual no hace falta recortar carga (antes 75). */
    const val ADJUSTMENT_THRESHOLD = NORMAL_MIN

    fun band(score: Int): RecoveryBand = when {
        score >= HIGH_MIN -> RecoveryBand.HIGH
        score >= NORMAL_MIN -> RecoveryBand.NORMAL
        score >= MODERATE_MIN -> RecoveryBand.MODERATE
        score >= LOW_MIN -> RecoveryBand.LOW
        else -> RecoveryBand.CRITICAL
    }

    fun label(band: RecoveryBand): String = when (band) {
        RecoveryBand.HIGH -> "Alta"
        RecoveryBand.NORMAL -> "Normal"
        RecoveryBand.MODERATE -> "Moderada"
        RecoveryBand.LOW -> "Baja"
        RecoveryBand.CRITICAL -> "Crítica"
    }

    fun labelForScore(score: Int): String = label(band(score))

    fun shortReadinessLabel(score: Int): String = when (band(score)) {
        RecoveryBand.HIGH -> "Óptimo"
        RecoveryBand.NORMAL -> "Bueno"
        RecoveryBand.MODERATE -> "Moderado"
        RecoveryBand.LOW -> "Bajo"
        RecoveryBand.CRITICAL -> "Crítico"
    }

    fun meaning(channel: RecoveryChannelId, band: RecoveryBand): String = when (channel) {
        RecoveryChannelId.MUSCULAR -> when (band) {
            RecoveryBand.HIGH -> "Músculos frescos. Puedes empujar volumen o un PR si la sesión lo pide."
            RecoveryBand.NORMAL -> "Estado esperado 24–48 h después de entrenar. Sigue el plan."
            RecoveryBand.MODERATE -> "Aún hay fatiga local. Entrena, pero con 1–2 RIR más o un poco menos de series en el grupo más cargado."
            RecoveryBand.LOW -> "El grupo limitante no está listo. Prioriza técnica y recorta volumen local."
            RecoveryBand.CRITICAL -> "No fuerces volumen local hoy. Descarga o trabajo liviano."
        }
        RecoveryChannelId.SYSTEM -> when (band) {
            RecoveryBand.HIGH -> "Energía alta: habitual con 3–4 sesiones/semana. Buen día para intensidad."
            RecoveryBand.NORMAL -> "Entrena normal, sin ir al límite. Es el rango más común al empezar."
            RecoveryBand.MODERATE -> "Carga neural acumulada. Deja repeticiones en reserva; evita el fallo."
            RecoveryBand.LOW -> "Evita sets al fallo y compuestos muy demandantes."
            RecoveryBand.CRITICAL -> "Sesión ligera o técnica. 0 % no existe: este piso ya es fatiga alta."
        }
        RecoveryChannelId.STRUCTURE -> when (band) {
            RecoveryBand.HIGH -> "Columna y tejidos de soporte toleran carga axial."
            RecoveryBand.NORMAL -> "Carga estructural normal con buena técnica."
            RecoveryBand.MODERATE -> "Modera sentadillas, pesos muertos y variantes agresivas. Baja un poco la carga tope."
            RecoveryBand.LOW -> "Usa variantes estables o menos axiales. La columna recupera más lento que un músculo."
            RecoveryBand.CRITICAL -> "Evita carga axial o trabajo explosivo hoy."
        }
    }

    fun action(channel: RecoveryChannelId, score: Int): String = when (channel) {
        RecoveryChannelId.MUSCULAR -> when (band(score)) {
            RecoveryBand.HIGH -> "Puedes meter volumen alto si la sesión lo pide."
            RecoveryBand.NORMAL -> "Volumen normal y buena ejecución."
            RecoveryBand.MODERATE -> "Modera series duras en el músculo más cargado."
            RecoveryBand.LOW -> "Prioriza técnica y recorta volumen local."
            RecoveryBand.CRITICAL -> "No fuerces volumen local hoy."
        }
        RecoveryChannelId.SYSTEM -> when (band(score)) {
            RecoveryBand.HIGH -> "Buen día para intensidad y coordinación."
            RecoveryBand.NORMAL -> "Empuja normal, sin necesidad de ir al límite."
            RecoveryBand.MODERATE -> "Mejor dejar alguna repetición en reserva."
            RecoveryBand.LOW -> "Evita sets al fallo y compuestos muy demandantes."
            RecoveryBand.CRITICAL -> "Haz una sesión ligera o técnica."
        }
        RecoveryChannelId.STRUCTURE -> when (band(score)) {
            RecoveryBand.HIGH -> "Toleras bien carga axial y tensión conectiva."
            RecoveryBand.NORMAL -> "Carga estructural normal con buena técnica."
            RecoveryBand.MODERATE -> "Conviene moderar impacto axial y variantes agresivas."
            RecoveryBand.LOW -> "Usa variantes estables o menos compresivas."
            RecoveryBand.CRITICAL -> "Evita carga axial o trabajo explosivo hoy."
        }
    }

    fun description(channel: RecoveryChannelId): String = when (channel) {
        RecoveryChannelId.MUSCULAR -> "Promedio del estado de todos tus músculos hoy."
        RecoveryChannelId.SYSTEM -> "Qué tanta intensidad, coordinación y producción de fuerza toleras hoy."
        RecoveryChannelId.STRUCTURE -> "Carga axial, piso articular y el estado de dorsales, erectores/lumbar y trapecio."
    }

    fun channelTitle(channel: RecoveryChannelId): String = when (channel) {
        RecoveryChannelId.MUSCULAR -> "Músculos"
        RecoveryChannelId.SYSTEM -> "Energía"
        RecoveryChannelId.STRUCTURE -> "Columna"
    }

    fun headline(band: RecoveryBand): String = when (band) {
        RecoveryBand.HIGH -> "Listo para empujar"
        RecoveryBand.NORMAL -> "Buen estado para entrenar"
        RecoveryBand.MODERATE -> "Día para moderar"
        RecoveryBand.LOW -> "Llega cargado"
        RecoveryBand.CRITICAL -> "Prioriza recuperación"
    }

    fun recommendation(band: RecoveryBand): String = when (band) {
        RecoveryBand.HIGH -> "Hoy puedes entrenar normal o fuerte si la sesión lo pide."
        RecoveryBand.NORMAL -> "Hoy conviene entrenar normal, dejando algo en reserva."
        RecoveryBand.MODERATE -> "Hoy conviene moderar volumen o intensidad según el ring más bajo."
        RecoveryBand.LOW -> "Hoy conviene priorizar técnica, variantes estables y menos carga."
        RecoveryBand.CRITICAL -> "Hoy conviene descargar o hacer solo trabajo liviano."
    }

    fun dailyReadinessLabel(band: RecoveryBand): Pair<String, com.example.kpkn.data.models.ReadinessColor> = when (band) {
        RecoveryBand.HIGH -> "Óptimo para entrenar" to com.example.kpkn.data.models.ReadinessColor.GREEN
        RecoveryBand.NORMAL -> "Buen estado" to com.example.kpkn.data.models.ReadinessColor.GREEN
        RecoveryBand.MODERATE -> "Moderado" to com.example.kpkn.data.models.ReadinessColor.YELLOW
        RecoveryBand.LOW -> "Cargado" to com.example.kpkn.data.models.ReadinessColor.YELLOW
        RecoveryBand.CRITICAL -> "Descanso recomendado" to com.example.kpkn.data.models.ReadinessColor.RED
    }

    /** ARGB opaco para UI (domain no importa Compose). */
    fun colorArgb(score: Int): Long = when (band(score)) {
        RecoveryBand.HIGH, RecoveryBand.NORMAL -> 0xFF22C55E
        RecoveryBand.MODERATE -> 0xFFFACC15
        RecoveryBand.LOW, RecoveryBand.CRITICAL -> 0xFFEF4444
    }

    fun hoursUntilNormal(
        score: Int,
        tauHours: Double,
        warpedHoursAlready: Double = 0.0,
    ): Int? {
        if (score >= NORMAL_MIN) return 0
        val fatigue = (100.0 - score).coerceIn(1.0, 99.9)
        val targetFatigue = 100.0 - NORMAL_MIN
        if (fatigue <= targetFatigue) return 0
        val k = AugeUtils.TAU_K_95 / tauHours.coerceAtLeast(1.0)
        val remainingEffective = -kotlin.math.ln(targetFatigue / fatigue) / k
        val hours = (remainingEffective - warpedHoursAlready).coerceAtLeast(0.0)
        return hours.roundToInt().coerceAtLeast(1)
    }

    private fun Double.roundToInt(): Int = kotlin.math.round(this).toInt()
}
