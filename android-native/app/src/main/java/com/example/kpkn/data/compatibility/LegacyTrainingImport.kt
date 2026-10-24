package com.example.kpkn.data.compatibility

import kotlinx.serialization.json.*

/** Isolated v1-v6 importer. Key allowlists are local to known records, never a global word filter. */
object LegacyTrainingImport {
    private val json = Json { ignoreUnknownKeys = true }
    private val wellbeingFields = setOf("id", "date", "sleepQuality", "stressLevel", "doms", "motivation",
        "sleepHours", "moodState", "workIntensity", "studyIntensity", "notes", "preWorkoutDiscomforts", "source", "capturedFields")
    private val manualFields = setOf("manualMuscularBattery", "manualNeuralBattery", "manualSpinalBattery",
        "manualMuscleBatteries", "manualMuscleOverridesV2", "manualBatteryAnchorMs")
    private val evidenceFields = setOf("capturedAtMs", "coveredFromMs", "coveredToMs", "sessions", "activityType",
        "intensity", "zones", "sensations", "sourceId", "muscleScope", "axialExposure", "activityTypeState")
    private val derivedSettings = setOf("augePredictionBias", "initialRecoveryEvidence", "augeEnableNutritionTracking",
        "augeEnableSleepTracking", "augeRecoverySensitivity", "augeFatigueSensitivity", "augeReadinessThreshold",
        "augeAutoDeload", "augeShowAlertsInSession")
    private val derivedLog = setOf("sessionStressScore", "muscularImpactV2", "ringStartSnapshot", "postSessionPreview")

    data class Original(val identity: String, val kind: String, val date: String, val payload: String, val provenance: String)

    fun parseObject(raw: String): JsonObject = json.parseToJsonElement(raw).jsonObject

    fun originals(table: String, identity: String, date: String, raw: String): List<Original> {
        val obj = parseObject(raw) // Invalid source aborts the enclosing transaction; never silently discard.
        val kept = when(table) {
            "auge_wellbeing" -> JsonObject(obj.filterKeys { it in wellbeingFields })
            "auge_sleep", "auge_sleep_extended", "auge_feedback" -> obj
            else -> return emptyList()
        }
        return buildList {
            add(Original("$table:$identity", table.removePrefix("auge_"), date, kept.toString(),
                "historical_original; original_scale; defaults_not_confirmed"))
            if (table == "auge_wellbeing") {
                val manual = obj.filterKeys { it in manualFields }.filterValues { it != JsonNull && it != JsonObject(emptyMap()) }
                if (manual.isNotEmpty()) add(Original("$table:$identity:manual", "historical_self_report", date,
                    JsonObject(manual).toString(), "legacy_0_100_self_report; not_neurological_measurement; not_comparable"))
            }
        }
    }

    fun settings(raw: String): String = JsonObject(parseObject(raw).filterKeys { it !in derivedSettings }.mapValues { (key, value) ->
        if (key == "algorithmSettings" && value is JsonObject) JsonObject(value.filterKeys { it !in derivedSettings && it != "failureFatigueFactor" }) else value
    }).toString()

    private val ongoingDerived = setOf("readinessNeuralOverride", "readinessMuscularOverride", "readinessSpinalOverride", "readinessMuscleOverrides", "lastDrainOverlay", "sessionStressScore", "postSessionPreview", "exerciseReadinessMap", "patternReadiness", "currentAutoRegulation")
    fun ongoing(raw: String): String = JsonObject(parseObject(raw).filterKeys { it !in ongoingDerived }.mapValues { (key,value) ->
        if (key == "completedSets") JsonObject(value.jsonObject.mapValues { (_,set) ->
            JsonObject(set.jsonObject.filterKeys { it != "spinalScore" })
        }) else value
    }).toString()

    private val draftRetired = setOf("ringsAnswers", "manualMuscleOverrides", "manualEnergyOverride", "manualStructureOverride", "ringsBatteriesPreview", "ringsCoveragePreview", "ringsMuscleEstimates")
    fun draftOriginals(raw: String): JsonObject = JsonObject(parseObject(raw).filterKeys { it in draftRetired && !it.endsWith("Preview") && it != "ringsMuscleEstimates" })
    fun draft(raw: String): String {
        val obj = parseObject(raw)
        return JsonObject(obj.filterKeys { it !in draftRetired }.mapValues { (key,value) ->
            when {
                key == "draftScope" && value.jsonPrimitive.content.lowercase().contains("rings") -> JsonPrimitive("resume")
                key == "stepProgress" -> JsonObject(value.jsonObject.mapValues { (k,v) ->
                    if (k == "stalePreviews") JsonArray(v.jsonArray.filterNot { it.jsonPrimitive.content == "RINGS_BATTERIES" }) else v
                })
                else -> value
            }
        }).toString()
    }


    fun initialDeclarations(raw: String): JsonObject? = parseObject(raw)["initialRecoveryEvidence"]
        ?.takeIf { it != JsonNull }?.jsonObject?.let { JsonObject(it.filterKeys { k -> k in evidenceFields }) }

    fun log(raw: String): String {
        val obj = parseObject(raw)
        return JsonObject(obj.filterKeys { it !in derivedLog }.mapValues { (key,value) ->
            when(key) {
                "completedExercises" -> JsonArray(value.jsonArray.map { ex ->
                    JsonObject(ex.jsonObject.mapValues { (k,v) -> if (k == "sets") JsonArray(v.jsonArray.map { set ->
                        JsonObject(set.jsonObject.filterKeys { it !in setOf("batteryDrain", "cnsDrain", "spinalDrain", "muscularDrain", "readinessScore", "spinalScore") })
                    }) else v })
                })
                else -> value
            }
        }).toString()
    }
}
