package com.example.kpkn.screens.onboarding

import com.example.kpkn.data.models.BodyMetric
import com.example.kpkn.data.models.BodyMetricSource
import com.example.kpkn.data.models.BodyObservation
import com.example.kpkn.data.models.BodyObservationMethod
import com.example.kpkn.data.models.BodyObservationQuality
import com.example.kpkn.domain.body.validateBodyValue
import com.example.kpkn.domain.onboarding.SetupStepId
import java.time.LocalDate
import java.time.ZoneId

/**
 * Adaptador de activación: borrador del wizard → payload real del commit.
 *
 * Reglas que este adaptador garantiza (el coordinador valida todas las filas y
 * cualquier inválida revierte el alta completa, así que aquí NO se filtra en
 * silencio: un dato inválido se rechaza con un error explícito):
 *
 * - IDs estables `draftId/tipo/fecha`: repetir el commit con el mismo borrador
 *   produce exactamente las mismas filas (idempotencia).
 * - Solo datos reales y fechados: peso actual declarado, grasa corporal medida
 *   o estimada y pesajes históricos con fecha no futura.
 * - La TENDENCIA y el MÁXIMO PREVIO son contexto, jamás observaciones.
 * - Calidad honesta: estimación visual ⇒ `ESTIMATED`, medida ⇒ `MEASURED`.
 * - Un import de Ajustes nunca inventa un peso nuevo: si el usuario no declaró
 *   el paso, no hay fila de peso actual.
 */
object SetupActivationPayload {

    /** Observaciones corporales reales del alta; lista vacía cuando no hay datos declarados. */
    fun bodyObservations(
        draft: SetupWizardDraft,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): List<BodyObservation> {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val draftKey = draft.draftId.ifBlank { draft.commitId }.ifBlank { "draft" }
        val rows = mutableListOf<BodyObservation>()

        // 1. Peso actual: SOLO declarado en este wizard.
        val declaredWeight = draft.weightKg
        if (SetupStepId.WEIGHT in draft.declaredSteps && declaredWeight != null) {
            val measuredAt = (draft.currentWeightMeasuredAtEpochMs ?: nowEpochMs).coerceAtMost(nowEpochMs)
            val date = draft.currentWeightMeasuredAtEpochMs?.let { dateOf(it, zone) } ?: today
            rows += observation(
                id = "$draftKey/weight/$date",
                metric = BodyMetric.WEIGHT,
                valueSi = declaredWeight,
                unitSi = "kg",
                timestampEpochMs = measuredAt,
                zone = zone,
                quality = BodyObservationQuality.MEASURED,
                nowEpochMs = nowEpochMs,
            )
        }

        // 2. Grasa corporal ACTUAL: medida o estimación visual explícita.
        //    «No lo sé» (y la fuente sin declarar) no produce ninguna fila.
        val bodyFat = draft.bodyFatPercent
        val source = draft.bodyFatSource
        val bodyFatDeclared = source == SetupBodyFatSource.MEASURED ||
            source == SetupBodyFatSource.VISUAL_ESTIMATE
        if (bodyFat != null && bodyFatDeclared) {
            val capturedAt = (draft.bodyFatCapturedAtEpochMs ?: nowEpochMs).coerceAtMost(nowEpochMs)
            val date = draft.bodyFatCapturedAtEpochMs?.let { dateOf(it, zone) } ?: today
            rows += observation(
                id = "$draftKey/bodyfat/$date",
                metric = BodyMetric.BODY_FAT_PERCENT,
                valueSi = bodyFat,
                unitSi = "%",
                timestampEpochMs = capturedAt,
                zone = zone,
                quality = if (source == SetupBodyFatSource.MEASURED) {
                    BodyObservationQuality.MEASURED
                } else {
                    BodyObservationQuality.ESTIMATED
                },
                nowEpochMs = nowEpochMs,
            )
        }

        // 3. Pesajes históricos: fila real con fecha no futura. Un pesaje mal
        //    fechado, sin peso, fuera de rango o con fecha REPETIDA RECHAZA el
        //    alta con mensaje: nunca se descarta en silencio (el usuario creería
        //    haberlo guardado) y el id derivado sigue siendo estable.
        val seenIds = rows.mapTo(mutableSetOf()) { it.id }
        draft.historicalWeighIns.forEach { weighIn ->
            val dateIso = weighIn.dateIso
            val date = parseDate(dateIso)
                ?: throw reject("Hay un pesaje con una fecha que no puedo leer ($dateIso). Corrígelo antes de activar.")
            if (date.isAfter(today)) {
                throw reject("Hay un pesaje con fecha futura ($date). Corrígelo antes de activar.")
            }
            val noon = date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
            val id = "$draftKey/weighin/$date"
            if (!seenIds.add(id)) {
                throw reject("Hay dos pesajes en la misma fecha ($date). Corrígelo antes de activar.")
            }
            rows += observation(
                id = id,
                metric = BodyMetric.WEIGHT,
                valueSi = weighIn.weightKg,
                unitSi = "kg",
                // Hoy a mediodía puede aún no llegar: nunca se envía el futuro.
                timestampEpochMs = minOf(noon, nowEpochMs),
                zone = zone,
                quality = BodyObservationQuality.MEASURED,
                nowEpochMs = nowEpochMs,
            )
        }

        // 4. weightTrend / previousMaximumWeightKg: contexto declarado aparte.
        return rows.distinct()
    }

    /**
     * Fila válida; un valor fuera de rango o una fecha inválida RECHAZA el
     * alta con un error honesto en lugar de filtrarse sin avisar.
     */
    private fun observation(
        id: String,
        metric: BodyMetric,
        valueSi: Double,
        unitSi: String,
        timestampEpochMs: Long,
        zone: ZoneId,
        quality: BodyObservationQuality,
        nowEpochMs: Long,
    ): BodyObservation {
        if (id.isBlank()) throw reject("Una observación corporal se quedó sin identificador. Revisa tus datos antes de activar.")
        if (timestampEpochMs <= 0L) throw reject("Una observación corporal tiene una fecha inválida. Revisa tus datos antes de activar.")
        if (timestampEpochMs > nowEpochMs) throw reject("Una observación corporal tiene una fecha futura. Revisa tus datos antes de activar.")
        if (!valueSi.isFinite()) throw reject("Una observación corporal tiene un valor no numérico. Revisa tus datos antes de activar.")
        val validation = validateBodyValue(metric, valueSi)
        if (!validation.valid) {
            val reason = validation.reason ?: "fuera de rango"
            throw reject("Una observación corporal no es válida ($reason). Revisa tus datos antes de activar.")
        }
        return BodyObservation(
            id = id,
            metric = metric,
            valueSi = valueSi,
            unitSi = unitSi,
            sessionId = null,
            timestampEpochMs = timestampEpochMs,
            zoneId = zone.id,
            source = BodyMetricSource.MANUAL,
            method = BodyObservationMethod.MANUAL,
            quality = quality,
        )
    }

    private fun reject(message: String): IllegalArgumentException = IllegalArgumentException(message)

    private fun parseDate(iso: String?): LocalDate? =
        iso?.takeIf { it.isNotBlank() }?.let { raw ->
            runCatching { LocalDate.parse(raw) }.getOrNull()
        }

    private fun dateOf(epochMs: Long, zone: ZoneId): LocalDate =
        java.time.Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate()
}
