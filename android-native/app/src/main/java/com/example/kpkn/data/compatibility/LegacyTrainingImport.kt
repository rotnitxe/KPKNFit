package com.example.kpkn.data.compatibility

import kotlinx.serialization.json.*

/** Isolated v1-v6 importer. Key allowlists are local to known records, never a global word filter. */
object LegacyTrainingImport {
    private val json = Json { ignoreUnknownKeys = true }
    fun isRetiredSetupMode(value: String): Boolean = value == "RINGS_ONLY"
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
                        if (key == "manualMuscleOverridesV2" && value is JsonObject) JsonObject(value.mapValues { (_, override) ->
                            (override as? JsonObject)?.let { overrideObj ->
                                JsonObject(overrideObj.filterKeys { it != "automaticBatteryAtAnchor" })
                            } ?: override
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
    private fun completedSets(value: JsonElement): JsonElement =
        (value as? JsonObject)?.let { setsObj ->
            JsonObject(setsObj.mapValues { (_, set) ->
                (set as? JsonObject)?.let { setObj ->
                    JsonObject(setObj.filterKeys { it !in setOf("spinalScore", "batteryDrain", "cnsDrain", "spinalDrain", "muscularDrain", "readinessScore") })
                } ?: set
            })
        } ?: value
    fun ongoing(raw: String): String = JsonObject(parseObject(raw).filterKeys { it !in ongoingDerived }.mapValues { (key,value) ->
        when {
            key == "completedSets" && value is JsonObject -> completedSets(value)
            key == "godModeUndoStack" && value is JsonArray -> JsonArray(value.map { snapshot ->
                (snapshot as? JsonObject)?.let { snapshotObj ->
                    JsonObject(snapshotObj.mapValues { (k,v) -> if (k == "completedSets") completedSets(v) else v })
                } ?: snapshot
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
        ((obj["wizChat"] as? JsonObject)?.get("acceptedAnswers") as? JsonArray)?.filter {
            ((it as? JsonObject)?.get("questionId") as? JsonPrimitive)?.contentOrNull?.startsWith("R_") == true
        }?.takeIf { it.isNotEmpty() }?.let { kept["legacyConversationAnswers"]=JsonArray(it) }
        return JsonObject(kept)
    }
    fun draft(raw: String): String {
        val obj = parseObject(raw)
        return JsonObject(obj.filterKeys { it !in draftRetired }.mapValues { (key,value) ->
            when {
                key == "draftScope" && (value as? JsonPrimitive)?.contentOrNull?.lowercase()?.contains("rings") == true -> JsonPrimitive("resume")
                key in setOf("stepSelections","stepValues","stepNumbers","stepTexts") && value is JsonObject -> JsonObject(value.filterKeys { !retiredStep(it) })
                key == "stepProgress" && value is JsonObject -> JsonObject(value.mapValues { (k,v) ->
                    when {
                        k == "currentStepId" && (v as? JsonPrimitive)?.contentOrNull?.let(::retiredStep) == true -> JsonPrimitive("REVIEW_ACTIVATE")
                        k == "block" && (v as? JsonPrimitive)?.contentOrNull == "RINGS" -> JsonPrimitive("REVIEW")
                        k == "completedBlocks" && v is JsonArray -> JsonArray(v.filterNot { (it as? JsonPrimitive)?.contentOrNull == "RINGS" })
                        k in setOf("visited","pendingReview") && v is JsonArray -> JsonArray(v.filterNot { (it as? JsonPrimitive)?.contentOrNull?.let(::retiredStep) == true })
                        k == "answers" && v is JsonObject -> JsonObject(v.filterKeys { !retiredStep(it) })
                        k == "stalePreviews" && v is JsonArray -> JsonArray(v.filterNot { (it as? JsonPrimitive)?.contentOrNull == "RINGS_BATTERIES" })
                        else -> v
                    }
                }.toMutableMap().apply {
                    if ((value["currentStepId"] as? JsonPrimitive)?.contentOrNull?.let(::retiredStep) == true &&
                        (value["origin"] as? JsonPrimitive)?.contentOrNull in setOf(null,"NOT_CONVERTIBLE"))
                        put("origin",JsonPrimitive("MIGRATED_FROM_RETIRED_STAGE"))
                })
                key == "wizChat" && value is JsonObject -> JsonObject(value.mapValues { (k,v) ->
                    when {
                        k == "currentQuestionId" && (v as? JsonPrimitive)?.contentOrNull?.startsWith("R_") == true -> JsonPrimitive("REVIEW")
                        k == "stage" && (v as? JsonPrimitive)?.contentOrNull == "RINGS" -> JsonPrimitive("REVIEW")
                        k == "completedStages" && v is JsonArray -> JsonArray(v.filterNot { (it as? JsonPrimitive)?.contentOrNull == "RINGS" })
                        k == "acceptedAnswers" && v is JsonArray -> JsonArray(v.filterNot { ((it as? JsonObject)?.get("questionId") as? JsonPrimitive)?.contentOrNull?.startsWith("R_") == true })
                        else -> v
                    }
                })
                else -> value
            }
        }).toString()
    }


    fun initialDeclarations(raw: String): JsonObject? = parseObject(raw)["initialRecoveryEvidence"]
        ?.takeIf { it != JsonNull }?.let { it as? JsonObject }?.let { JsonObject(it.filterKeys { k -> k in evidenceFields }) }

    fun log(raw: String): String {
        val obj = parseObject(raw)
        val legacyEnergy = (obj["energySummary"] as? JsonObject)?.let {
            com.example.kpkn.data.models.isRetiredTrainingEnergyMethod(it["methodVersion"]?.jsonPrimitive?.contentOrNull)
        } == true
        return JsonObject(obj.filterKeys { it !in derivedLog && !(it == "energySummary" && legacyEnergy) }.mapValues { (key,value) ->
            when {
                key == "completedExercises" && value is JsonArray -> JsonArray(value.map { ex ->
                    (ex as? JsonObject)?.let { exObj ->
                        JsonObject(LegacyExerciseCoefficientImport.completedExercise(exObj).filterKeys { it != "totalSpinalScore" }.mapValues { (k,v) ->
                            if (k == "sets" && v is JsonArray) JsonArray(v.map { set ->
                                (set as? JsonObject)?.let { setObj ->
                                    JsonObject(setObj.filterKeys { it !in setOf("batteryDrain", "cnsDrain", "spinalDrain", "muscularDrain", "readinessScore", "spinalScore") })
                                } ?: set
                            }) else v
                        })
                    } ?: ex
                })
                else -> value
            }
        }).toString()
    }
}
