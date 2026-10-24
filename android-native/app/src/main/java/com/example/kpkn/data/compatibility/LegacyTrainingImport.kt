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
                    JsonObject(manual.mapValues { (key, value) ->
                        if (key == "manualMuscleOverridesV2") JsonObject(value.jsonObject.mapValues { (_, override) ->
                            JsonObject(override.jsonObject.filterKeys { it != "automaticBatteryAtAnchor" })
                        }) else value
                    }).toString(), "legacy_0_100_self_report; not_neurological_measurement; not_comparable"))
            }
        }
    }

    fun settings(raw: String): String = JsonObject(parseObject(raw).filterKeys { it !in derivedSettings }.mapValues { (key, value) ->
        if (key == "algorithmSettings" && value is JsonObject) JsonObject(value.filterKeys { it !in derivedSettings && it != "failureFatigueFactor" }) else value
    }).toString()

    private val ongoingDerived = setOf("readinessNeuralOverride", "readinessMuscularOverride", "readinessSpinalOverride", "readinessMuscleOverrides", "lastDrainOverlay", "sessionStressScore", "postSessionPreview", "exerciseReadinessMap", "patternReadiness", "currentAutoRegulation")
    fun ongoingOriginals(raw: String): JsonObject {
        val obj = parseObject(raw)
        val declarations = obj.filterKeys { it in setOf("readinessNeuralOverride", "readinessMuscularOverride", "readinessSpinalOverride", "readinessMuscleOverrides") }
            .filterValues { it != JsonNull && it != JsonObject(emptyMap()) }
        return if (declarations.isEmpty()) JsonObject(emptyMap()) else JsonObject(declarations + obj.filterKeys { it in setOf("programId", "startTime") })
    }
    private fun completedSets(value: JsonElement): JsonObject = JsonObject(value.jsonObject.mapValues { (_,set) ->
        JsonObject(set.jsonObject.filterKeys { it !in setOf("spinalScore", "batteryDrain", "cnsDrain", "spinalDrain", "muscularDrain", "readinessScore") })
    })
    fun ongoing(raw: String): String = JsonObject(parseObject(raw).filterKeys { it !in ongoingDerived }.mapValues { (key,value) ->
        when {
            key == "completedSets" -> completedSets(value)
            key == "godModeUndoStack" && value != JsonNull -> JsonArray(value.jsonArray.map { snapshot ->
                JsonObject(snapshot.jsonObject.mapValues { (k,v) -> if (k == "completedSets") completedSets(v) else v })
            })
            else -> value
        }
    }).toString()

    private val draftRetired = setOf("ringsAnswers", "manualMuscleOverrides", "manualEnergyOverride", "manualStructureOverride", "ringsBatteriesPreview", "ringsCoveragePreview", "ringsMuscleEstimates")
    private fun retiredStep(id: String): Boolean = id.startsWith("RINGS_") || id == "MILESTONE_RINGS"
    fun draftOriginals(raw: String): JsonObject {
        val obj=parseObject(raw)
        val kept=obj.filterKeys { it in draftRetired && !it.endsWith("Preview") && it != "ringsMuscleEstimates" }.toMutableMap()
        for (key in listOf("stepSelections", "stepValues", "stepNumbers", "stepTexts")) {
            (obj[key] as? JsonObject)?.filterKeys(::retiredStep)?.takeIf { it.isNotEmpty() }?.let { kept[key]=JsonObject(it) }
        }
        (obj["wizChat"] as? JsonObject)?.get("acceptedAnswers")?.jsonArray?.filter {
            it.jsonObject["questionId"]?.jsonPrimitive?.content?.startsWith("R_") == true
        }?.takeIf { it.isNotEmpty() }?.let { kept["legacyConversationAnswers"]=JsonArray(it) }
        return JsonObject(kept)
    }
    fun draft(raw: String): String {
        val obj = parseObject(raw)
        return JsonObject(obj.filterKeys { it !in draftRetired }.mapValues { (key,value) ->
            when {
                key == "draftScope" && value.jsonPrimitive.content.lowercase().contains("rings") -> JsonPrimitive("resume")
                key in setOf("stepSelections","stepValues","stepNumbers","stepTexts") && value is JsonObject -> JsonObject(value.filterKeys { !retiredStep(it) })
                key == "stepProgress" -> JsonObject(value.jsonObject.mapValues { (k,v) ->
                    when {
                        k == "currentStepId" && retiredStep(v.jsonPrimitive.content) -> JsonPrimitive("REVIEW_ACTIVATE")
                        k == "block" && v.jsonPrimitive.content == "RINGS" -> JsonPrimitive("REVIEW")
                        k == "completedBlocks" -> JsonArray(v.jsonArray.filterNot { it.jsonPrimitive.content == "RINGS" })
                        k in setOf("visited","pendingReview") -> JsonArray(v.jsonArray.filterNot { retiredStep(it.jsonPrimitive.content) })
                        k == "answers" -> JsonObject(v.jsonObject.filterKeys { !retiredStep(it) })
                        k == "stalePreviews" -> JsonArray(v.jsonArray.filterNot { it.jsonPrimitive.content == "RINGS_BATTERIES" })
                        else -> v
                    }
                })
                key == "wizChat" -> JsonObject(value.jsonObject.mapValues { (k,v) ->
                    when {
                        k == "currentQuestionId" && v.jsonPrimitive.content.startsWith("R_") -> JsonPrimitive("REVIEW")
                        k == "stage" && v.jsonPrimitive.content == "RINGS" -> JsonPrimitive("REVIEW")
                        k == "acceptedAnswers" -> JsonArray(v.jsonArray.filterNot { it.jsonObject["questionId"]?.jsonPrimitive?.content?.startsWith("R_") == true })
                        else -> v
                    }
                })
                else -> value
            }
        }).toString()
    }


    fun initialDeclarations(raw: String): JsonObject? = parseObject(raw)["initialRecoveryEvidence"]
        ?.takeIf { it != JsonNull }?.jsonObject?.let { JsonObject(it.filterKeys { k -> k in evidenceFields }) }

    fun log(raw: String): String {
        val obj = parseObject(raw)
        val legacyEnergy = (obj["energySummary"] as? JsonObject)?.let {
            it["methodVersion"]?.jsonPrimitive?.content?.startsWith("auge-energy") ?: true
        } == true
        return JsonObject(obj.filterKeys { it !in derivedLog && !(it == "energySummary" && legacyEnergy) }.mapValues { (key,value) ->
            when(key) {
                "completedExercises" -> JsonArray(value.jsonArray.map { ex ->
                    JsonObject(ex.jsonObject.filterKeys { it != "totalSpinalScore" }.mapValues { (k,v) -> if (k == "sets") JsonArray(v.jsonArray.map { set ->
                        JsonObject(set.jsonObject.filterKeys { it !in setOf("batteryDrain", "cnsDrain", "spinalDrain", "muscularDrain", "readinessScore", "spinalScore") })
                    }) else v })
                })
                else -> value
            }
        }).toString()
    }
}
