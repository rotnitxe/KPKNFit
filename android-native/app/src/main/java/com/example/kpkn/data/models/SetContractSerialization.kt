package com.example.kpkn.data.models

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonTransformingSerializer
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Legacy flags are read only. New JSON always writes purpose and an explicit nullable minimum. */
@OptIn(ExperimentalSerializationApi::class)
object ExerciseSetSerializer : JsonTransformingSerializer<ExerciseSet>(ExerciseSet.generatedSerializer()) {
    override fun transformDeserialize(element: JsonElement): JsonElement = migrateSet(element as JsonObject)

    override fun transformSerialize(element: JsonElement): JsonElement {
        val fields = (element as JsonObject).toMutableMap()
        fields.putIfAbsent("purpose", JsonPrimitive(SetPurpose.NONE.name))
        fields.putIfAbsent("amrapMinimumReps", JsonNull)
        return JsonObject(fields)
    }
}

internal fun migrateSet(raw: JsonObject, legacyExerciseAmrap: Boolean = false): JsonObject {
    val fields = raw.toMutableMap()
    val purpose = raw["purpose"]?.jsonPrimitive?.contentOrNull ?: when {
        raw["isCalibrator"]?.jsonPrimitive?.booleanOrNull == true -> SetPurpose.CALIBRATOR.name
        raw["isAmrap"]?.jsonPrimitive?.booleanOrNull == true ||
            raw["intensityMode"]?.jsonPrimitive?.contentOrNull == IntensityMode.AMRAP.name ||
            legacyExerciseAmrap -> SetPurpose.AMRAP.name
        else -> SetPurpose.NONE.name
    }
    fields["purpose"] = JsonPrimitive(purpose)
    if (!raw.containsKey("amrapMinimumReps")) {
        val minimum = if (!raw.containsKey("purpose") && purpose != SetPurpose.NONE.name) {
            (raw["targetRepsRange"] as? JsonObject)?.get("min")?.jsonPrimitive?.intOrNull
                ?: raw["targetReps"]?.jsonPrimitive?.intOrNull
                ?: raw["plannedTargetV2"]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()?.toInt()
        } else null
        fields["amrapMinimumReps"] = minimum?.takeIf { it > 0 }?.let(::JsonPrimitive) ?: JsonNull
    }
    if (purpose == SetPurpose.CALIBRATOR.name) {
        if (fields["calibrationScope"] == null || fields["calibrationScope"] == JsonNull) {
            fields["calibrationScope"] = JsonPrimitive(CalibrationScope.SESSION.name)
        }
    } else {
        fields["calibrationScope"] = JsonNull
    }
    fields.remove("isAmrap")
    fields.remove("isCalibrator")
    return JsonObject(fields)
}

/** Applies to every JSON entry point, including nested Room data, backups and drafts. */
@OptIn(ExperimentalSerializationApi::class)
object ExerciseSerializer : JsonTransformingSerializer<Exercise>(Exercise.generatedSerializer()) {
    override fun transformDeserialize(element: JsonElement): JsonElement {
        val raw = element as JsonObject
        val fields = raw.toMutableMap()
        fields.remove("isCalibratorAmrap")
        val sets = raw["sets"] as? kotlinx.serialization.json.JsonArray
        if (sets != null) {
            val legacyAmrap = raw["trainingMode"]?.jsonPrimitive?.contentOrNull == TrainingMode.AMRAP.name
            fields["sets"] = kotlinx.serialization.json.JsonArray(sets.map { migrateSet(it as JsonObject, legacyAmrap) })
            // Old PERCENT_TM exercises stored TM in reference1RM. An explicit new key,
            // including null, prevents repeating the conversion on subsequent reads.
            if (!raw.containsKey("referenceTrainingMax") && sets.any {
                    (it as JsonObject)["loadBasis"]?.jsonPrimitive?.contentOrNull == "PERCENT_TM"
                }) {
                fields["referenceTrainingMax"] = raw["reference1RM"] ?: JsonNull
                fields["reference1RM"] = JsonNull
            }
        }
        return JsonObject(fields)
    }

    override fun transformSerialize(element: JsonElement): JsonElement = JsonObject(
        (element as JsonObject).toMutableMap().apply { putIfAbsent("referenceTrainingMax", JsonNull) },
    )
}

/** A false flag present in old JSON is evidence of an opt-out, not an absent legacy field. */
@OptIn(ExperimentalSerializationApi::class)
object CompletedSetSerializer : JsonTransformingSerializer<CompletedSet>(CompletedSet.generatedSerializer()) {
    override fun transformDeserialize(element: JsonElement): JsonElement {
        val raw = element as JsonObject
        if (raw.containsKey("purpose") || raw["amrapPerformed"]?.jsonPrimitive?.booleanOrNull != false) return raw
        return JsonObject(raw + ("purpose" to JsonPrimitive(SetPurpose.NONE.name)))
    }

    override fun transformSerialize(element: JsonElement): JsonElement = JsonObject(
        // Preserve unknown-purpose snapshots assembled from genuinely old logs. In particular,
        // encoding defaults must not turn a missing flag into evidence that it was disabled.
        (element as JsonObject).toMutableMap().apply { putIfAbsent("purpose", JsonNull) },
    )
}
