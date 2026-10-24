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
        require(observations.groupBy { it.sourceIdentity }.values.all { versions -> versions.distinct().size == 1 }) {
            "Observaciones con identidad repetida y contenido distinto: se conserva el respaldo para recuperación"
        }
        return JsonObject(root.filterKeys { it !in sections && it != "adaptiveCache" }.toMutableMap().apply {
            put("trainingObservations", json.encodeToJsonElement(ListSerializer(TrainingObservationEntity.serializer()), observations.distinctBy { it.sourceIdentity }))
            root["settings"]?.let { put("settings",json.parseToJsonElement(LegacyTrainingImport.settings(it.toString()))) }
            root["workoutLogs"]?.let { put("workoutLogs",JsonArray(it.jsonArray.map { log -> json.parseToJsonElement(LegacyTrainingImport.log(log.toString())) })) }
        }).toString()
    }

    /** New backups contain only neutral originals, never retired sections/results. */
    fun export(raw: String): String = normalize(raw)
}
