package com.example.kpkn.data.compatibility

import kotlinx.serialization.json.*

/** Known catalog paths only. User notes, identifiers and original result payloads are never word-filtered. */
object LegacyExerciseCoefficientImport {
    private val fields = setOf("efc", "cnc", "ssc", "ttc", "axialLoadFactor", "fatigueTier")
    private val json = Json { ignoreUnknownKeys = true }
    private fun rich(value: JsonObject): JsonObject = JsonObject(value.filterKeys { it != "fatigue" }.mapValues { (key, child) ->
        if (key == "programming" && child is JsonObject) JsonObject(child.filterKeys { it != "fatigueCost" }) else child
    })
    private fun profile(value: JsonObject): JsonObject = JsonObject(value.filterKeys { it !in fields }.mapValues { (key, child) ->
        if (key == "richMetadata" && child is JsonObject) rich(child) else child
    })
    fun snapshot(raw: String): String {
        val obj = json.parseToJsonElement(raw).jsonObject
        return JsonObject(obj.mapValues { (key, child) ->
            if (key == "resolvedProfile" && child is JsonObject) profile(child) else child
        }).toString()
    }
    fun customExercise(raw: String): String {
        val obj = json.parseToJsonElement(raw).jsonObject
        return JsonObject(obj.filterKeys { it !in fields }.mapValues { (key, child) ->
            if (key == "catalogRichMetadataJson" && child != JsonNull)
                JsonPrimitive(rich(json.parseToJsonElement(child.jsonPrimitive.content).jsonObject).toString()) else child
        }).toString()
    }
    fun completedExercise(value: JsonObject): JsonObject = JsonObject(value.mapValues { (key, child) ->
        if (key == "resolvedProfileSnapshotJson" && child != JsonNull) JsonPrimitive(snapshot(child.jsonPrimitive.content)) else child
    })
}
