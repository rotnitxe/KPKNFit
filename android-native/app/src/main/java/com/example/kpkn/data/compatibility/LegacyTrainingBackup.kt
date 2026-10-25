package com.example.kpkn.data.compatibility

import com.example.kpkn.data.db.TrainingObservationEntity
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.*

/** Legacy backup boundary: read originals before typed decoding could inject defaults. */
object LegacyTrainingBackup {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val sections = mapOf("wellbeingLogs" to "auge_wellbeing", "sleepLogs" to "auge_sleep",
        "sleepLogsExtended" to "auge_sleep_extended", "postSessionFeedback" to "auge_feedback")

    fun normalize(raw: String): String {
        val root = LegacyTrainingImport.parseObject(raw)
        val version = root["schemaVersion"]?.jsonPrimitive?.intOrNull ?: 1
        require(version in 1..7) { "Formato de respaldo no compatible" }
        val observations = root["trainingObservations"]?.takeIf { it != JsonNull }?.let {
            json.decodeFromJsonElement(ListSerializer(TrainingObservationEntity.serializer()), it)
        }.orEmpty().toMutableList()
        for ((section, table) in sections) {
            root[section]?.takeIf { it != JsonNull }?.jsonArray?.forEach { value ->
                val obj = value.jsonObject
                val identity = obj[if (table == "auge_feedback") "logId" else "id"]?.jsonPrimitive?.content
                require(!identity.isNullOrBlank()) { "No se puede preservar una observación sin identidad ($section)" }
                LegacyTrainingImport.originals(table, identity, obj["date"]?.jsonPrimitive?.content.orEmpty(), value.toString()).forEach {
                    observations += TrainingObservationEntity(it.identity,it.kind,it.date,it.payload,it.provenance)
                }
            }
        }
        root["settings"]?.let { settings ->
            LegacyTrainingImport.initialDeclarations(settings.toString())?.let {
                observations += TrainingObservationEntity("settings:1:initial_declarations", "historical_onboarding", "", it.toString(),
                    "historical_original; scales_not_comparable; not_measured_marks")
            }
        }
        val drafts = root["setupDrafts"]?.takeIf { it != JsonNull }?.jsonArray?.map { row ->
            val draft = row.jsonObject
            val rawDraft = draft["payloadJson"]?.jsonPrimitive?.content ?: error("Borrador sin contenido recuperable")
            val original = LegacyTrainingImport.draftOriginals(rawDraft)
            if (original.isNotEmpty()) observations += TrainingObservationEntity(
                "setup_drafts:${draft.getValue("draftId").jsonPrimitive.content}:retired_answers", "historical_onboarding_answers", "",
                JsonObject(original + ("sourceUpdatedAtEpochMs" to (draft["updatedAtEpochMs"] ?: JsonNull))).toString(),
                "historical_original; original_scale; not_comparable")
            JsonObject(draft + ("payloadJson" to JsonPrimitive(LegacyTrainingImport.draft(rawDraft))))
        }
        root["ongoingWorkout"]?.takeIf { it != JsonNull }?.let { ongoing ->
            val original = LegacyTrainingImport.ongoingOriginals(ongoing.toString())
            if (original.isNotEmpty()) observations += TrainingObservationEntity(
                "ongoing:1:${ongoing.jsonObject["startTime"]}", "historical_session_context", "", original.toString(),
                "historical_original; original_scale; not_comparable")
        }
        require(observations.groupBy { it.sourceIdentity }.values.all { versions -> versions.distinct().size == 1 }) {
            "Observaciones con identidad repetida y contenido distinto: se conserva el respaldo para recuperación"
        }
        return JsonObject(root.filterKeys { it !in sections && it != "adaptiveCache" }.toMutableMap().apply {
            put("trainingObservations", json.encodeToJsonElement(ListSerializer(TrainingObservationEntity.serializer()), observations.distinctBy { it.sourceIdentity }))
            drafts?.let { put("setupDrafts", JsonArray(it)) }
            root["programs"]?.let { programs ->
                put("programs", JsonArray(programs.jsonArray.map { json.parseToJsonElement(LegacyProgramProposalImport.program(it.toString())) }))
            }
            root["customExercises"]?.takeIf { it != JsonNull }?.let { exercises ->
                put("customExercises", JsonArray(exercises.jsonArray.map { json.parseToJsonElement(LegacyExerciseCoefficientImport.customExercise(it.toString())) }))
            }
            root["ongoingWorkout"]?.takeIf { it != JsonNull }?.let { put("ongoingWorkout",json.parseToJsonElement(LegacyTrainingImport.ongoing(it.toString()))) }
            root["settings"]?.let { put("settings",json.parseToJsonElement(LegacyTrainingImport.settings(it.toString()))) }
            root["workoutLogs"]?.let { put("workoutLogs",JsonArray(it.jsonArray.map { log -> json.parseToJsonElement(LegacyTrainingImport.log(log.toString())) })) }
        }).toString()
    }

    /** New backups contain only neutral originals, never retired sections/results. */
    fun export(raw: String): String = normalize(raw)
}
