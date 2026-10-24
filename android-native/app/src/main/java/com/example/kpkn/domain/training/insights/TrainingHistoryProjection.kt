package com.example.kpkn.domain.training.insights

import com.example.kpkn.data.models.*
import com.example.kpkn.domain.multisport.SportId
import com.example.kpkn.domain.multisport.tracking.*
import com.example.kpkn.domain.performance.lab.*
import com.example.kpkn.domain.time.TrainingDates
import kotlinx.serialization.json.*

/** Read projection only: all facts retain their existing WorkoutLog/document owner. */
object TrainingHistoryProjection {
    private val json = Json { ignoreUnknownKeys = true }
    private val recordedUnits = mapOf(
        "actualDurationSeconds" to "s", "actualWorkSeconds" to "s", "actualWorkingSeconds" to "s",
        "actualHangSeconds" to "s", "actualRestSecondsAfter" to "s", "actualRestSecondsBefore" to "s",
        "actualExternalDurationSeconds" to "s", "elapsedMillis" to "ms", "movingMillis" to "ms",
        "elapsedDurationMillis" to "ms", "finishTimeMillis" to "ms", "distanceMeters" to "m",
        "elevationGainMeters" to "m", "elevationLossMeters" to "m", "attemptedCount" to "intentos",
        "successfulCount" to "aciertos", "repetitions" to "rep", "holdSeconds" to "s", "splitMillis" to "ms", "completedRounds" to "rondas", "completedRepetitions" to "rep",
    )
    private val excludedBranches = setOf("plan", "references", "complexes", "definition", "format", "prescription",
        "manualFtpReference", "events", "skills", "drills", "progressionProposals", "ruleset", "scaling", "division", "target", "targets", "penalties")

    fun facts(logs: List<WorkoutLog>, nowMs: Long): List<TrainingFact> = buildList {
        logs.groupBy { it.id }.values.mapNotNull { it.distinct().singleOrNull() }.forEach { log ->
            val time = TrainingDates.logDateMs(log).takeIf { it in 1..nowMs } ?: return@forEach
            val sports = log.sportDocuments.mapNotNull { doc -> SportId.entries.firstOrNull { it.name == doc.sportId } }.distinct()
            // A generic strength log has no inferred specialized discipline.
            val declaredSport = SportId.entries.firstOrNull { it.name == log.recordedSportId }
            val sport = if (sports.isNotEmpty()) sports.singleOrNull()?.takeIf { declaredSport == null || it == declaredSport }
                else if (log.recordedSportId != null) declaredSport
                else if (log.completedExercises.isNotEmpty() && log.completedExercises.all { it.cardioDetails == null }) SportId.STRENGTH else null
            fun logFact(metric: String, value: Double?, unit: String, origin: FactOrigin, method: String?, protocol: String? = null) {
                if (sport == null || value == null || !value.isFinite() || value < 0.0) return
                add(TrainingFact("log:${log.id}:$metric", log.id, time, sport, metric, value, unit, origin,
                    null, method, protocol))
            }
            if (log.actualDurationSeconds != null) logFact("Duración de sesión", log.actualDurationSeconds.toDouble(), "s",
                FactOrigin.OBSERVED, log.actualDurationSource)
            else logFact("Duración de sesión (precisión original)", log.durationMinutes.toDouble(), "min",
                FactOrigin.HISTORICAL_AMBIGUOUS, null)
            logFact("Esfuerzo global declarado", log.sessionEffortRpe, "puntos 0–10", FactOrigin.SELF_REPORTED,
                "athlete_reported", log.sessionEffortProtocol)
            if (log.sessionEffortRpe != null && log.sessionEffortProtocol != null && log.actualDurationSeconds != null && log.actualDurationSource != null) {
                logFact("Esfuerzo × duración", log.sessionEffortRpe * log.actualDurationSeconds / 60.0, "u.a. (puntos·min)",
                    FactOrigin.DESCRIPTIVE_CALCULATION, "explicit_session_rpe_times_seconds_div_60", log.sessionEffortProtocol)
            }
            log.completedExercises.forEach exercises@{ exercise ->
                val exerciseSport = when(exercise.cardioDetails?.type) {
                    CardioType.RUN_OUTDOOR, CardioType.TREADMILL, CardioType.CURVED_TREADMILL -> SportId.RUNNING
                    CardioType.BIKE_OUTDOOR, CardioType.BIKE_STATIONARY, CardioType.AIR_BIKE -> SportId.CYCLING
                    CardioType.ROW_MACHINE -> SportId.ROWING
                    null -> sport
                    else -> null
                } ?: return@exercises
                exercise.sets.filterNot { it.skipped || it.isWarmup }.forEach { set ->
                    fun setFact(metric: String, value: Double, unit: String) {
                        if (!value.isFinite() || value < 0.0) return
                        add(TrainingFact("log:${log.id}:exercise:${exercise.exerciseId}:set:${set.id}:$metric", log.id, time,
                            exerciseSport, "${exercise.exerciseName} · $metric", value, unit, FactOrigin.HISTORICAL_AMBIGUOUS,
                            exercise.performanceProfileId ?: exercise.catalogConfigurationId, null, null,
                            set.contextKey?.takeIf { it.isNotBlank() }?.let { mapOf("recorded_context" to it) }.orEmpty()))
                    }
                    val payload = set.recordedPayloadV3
                    if (exercise.cardioDetails == null) {
                        val load = if (payload?.loadInputMode == LoadModeV2.ASSISTED) payload.assistedLoad else payload?.externalLoad
                        if (load != null) setFact(if (payload?.loadInputMode == LoadModeV2.ASSISTED) "Asistencia registrada" else "Carga externa registrada", load, "kg")
                        else if (payload == null && set.weight > 0.0) setFact("Carga histórica registrada", set.weight, "kg")
                        if (payload?.unitMode == UnitModeV2.REPS) payload.completedReps?.let { setFact("Repeticiones registradas", it.toDouble(), "rep") }
                        else if (payload == null && set.reps > 0 && set.timeSeconds == null) setFact("Repeticiones históricas", set.reps.toDouble(), "rep")
                    }
                    if (payload?.intensityAdjusted == true) set.rpe?.let { setFact("RPE de serie declarado", it, "puntos") }
                    set.timeSeconds?.let { setFact("Tiempo registrado", it.toDouble(), "s") }
                    set.distanceKm?.let { setFact("Distancia registrada", it, "km") }
                }
            }
            val documents = log.sportDocuments.groupBy { it.id }.values.mapNotNull { it.distinct().singleOrNull() }
            documents.forEach documents@{ document ->
                if (document.purpose !in setOf("RESULT", "OBSERVATION") ||
                    NativeSportTrackingEngines.registry.validate(document) != SportDocumentValidation.Valid) return@documents
                val documentSport = SportId.entries.firstOrNull { it.name == document.sportId } ?: return@documents
                if (document.engineId == PerformanceLabEngine.id) {
                    val payload = json.decodeFromJsonElement<PerformanceLabPayload>(document.payload)
                    payload.marks.filter { it.status == PerformanceLabTrialStatus.VALID }.forEach marks@{ mark ->
                        val value = mark.value ?: return@marks
                        val captured = mark.capturedAtEpochMillis ?: time
                        if (captured !in 1..nowMs) return@marks
                        val origin = when(mark.provenance) {
                            PerformanceLabProvenance.INSTRUMENT_MEASURED, PerformanceLabProvenance.MANUAL_MEASURED,
                            PerformanceLabProvenance.IMPORTED_MEASURED -> FactOrigin.OBSERVED
                            PerformanceLabProvenance.DERIVED -> FactOrigin.DESCRIPTIVE_CALCULATION
                            else -> FactOrigin.ESTIMATED
                        }
                        add(TrainingFact("document:${document.id}:mark:${mark.id}", log.id, captured, documentSport,
                            mark.metric.name, value, mark.unit.name, origin, mark.dimension,
                            "${mark.provenance}:${mark.context.method}", "${mark.protocol.id}@${mark.protocol.version}",
                            mark.context.conditions + mapOf("instrument" to mark.context.instrument, "surface" to mark.context.surface,
                                "method" to mark.context.method) + (mark.formulaId?.let { mapOf("formula" to it) }.orEmpty())))
                    }
                } else {
                    // Generic fallback is deliberately conservative: registered result documents only,
                    // exact field units, no recursive totals or inferred protocol/context.
                    fun visit(element: JsonElement, path: String, inheritedUnit: String? = null, inheritedTime: Long = time) {
                        when (element) {
                            is JsonArray -> element.forEachIndexed { index, child -> visit(child, "$path[$index]", inheritedUnit, inheritedTime) }
                            is JsonObject -> {
                                if (listOf("status", "quality").any { (element[it] as? JsonPrimitive)?.content in setOf("FAILED", "INVALIDATED", "UNRECORDED", "MISSED") }) return
                                val capturedAt = (element["capturedAtEpochMillis"] as? JsonPrimitive)?.longOrNull
                                if (capturedAt != null && capturedAt !in 1..nowMs) return
                                val explicitUnit = (element["unit"] as? JsonPrimitive)?.content ?: inheritedUnit
                                val quantity = (element["value"] as? JsonPrimitive)?.takeUnless { it.isString }?.doubleOrNull
                                val quantityUnits = setOf("KILOGRAMS", "POUNDS", "KILOGRAM", "POUND", "METERS", "FEET", "SECONDS", "MINUTES",
                                    "METER", "CENTIMETER", "SECOND", "MILLISECOND", "METER_PER_SECOND", "REPETITIONS", "CALORIES", "METERS", "SECONDS",
                                    "BEATS_PER_MINUTE", "WATTS", "RUNNING_STEPS_PER_MINUTE", "REVOLUTIONS_PER_MINUTE",
                                    "STROKES_PER_MINUTE", "METERS_PER_SECOND", "SECONDS_PER_KILOMETER")
                                if (explicitUnit in quantityUnits && quantity != null && quantity.isFinite() && quantity >= 0.0) {
                                    add(TrainingFact("document:${document.id}:$path.value", log.id, capturedAt ?: inheritedTime, documentSport,
                                        path, quantity, checkNotNull(explicitUnit), FactOrigin.HISTORICAL_AMBIGUOUS, null, null, null))
                                }
                                element.forEach { (key, child) ->
                                    if (key in excludedBranches || key.startsWith("target") || key.startsWith("planned") || key.startsWith("declared")) return@forEach
                                    val unit = recordedUnits[key]
                                    val value = (child as? JsonPrimitive)?.takeUnless { it.isString }?.doubleOrNull
                                    if (unit != null && value != null && value.isFinite() && value >= 0.0) {
                                        // The log is the sole owner of whole-session duration; no duplicate point.
                                        if (key != "actualDurationSeconds") add(TrainingFact("document:${document.id}:$path.$key", log.id,
                                            capturedAt ?: inheritedTime, documentSport, "$path.$key", value, unit, FactOrigin.HISTORICAL_AMBIGUOUS,
                                            null, null, null))
                                    } else if (child is JsonObject || child is JsonArray) visit(child, "$path.$key", explicitUnit, capturedAt ?: inheritedTime)
                                }
                            }
                            else -> Unit
                        }
                    }
                    visit(document.payload, document.engineId)
                }
            }
        }
    }
}
