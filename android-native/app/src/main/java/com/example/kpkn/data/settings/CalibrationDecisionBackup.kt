package com.example.kpkn.data.settings

import com.example.kpkn.data.db.CalibrationDecisionEntity
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.*

/** Conserva el JSON público de CAL sin traer su motor, política ni constantes a esta rama. */
internal object CalibrationDecisionBackup {
    fun canonicalRecords(rows: List<CalibrationDecisionEntity>): List<JsonObject> = rows.mapNotNull { row ->
        try {
            Json.parseToJsonElement(row.data).jsonObject.also { record ->
                require(toEntity(record).id == row.id)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // La fila opaca completa sigue viajando en calibrationDecisionRows, incluso si su JSON está en cuarentena.
            null
        }
    }
    fun toEntity(record: JsonObject): CalibrationDecisionEntity {
        val decision=record.getValue("decision").jsonObject
        val context=decision.getValue("context").jsonObject
        val configuration=context.requiredText("configurationId")
        val equipment=context.requiredText("equipmentKey")
        val setup=context.optionalText("setupProfileId").orEmpty()
        val side=context.optionalText("side") ?: "bilateral"
        val convention=context.optionalText("loadQuantityConvention") ?: "UNSPECIFIED"
        // Formato publicado por buildRecordedSetContextKey: longitud prefijada para no confundir separadores en el equipo.
        val key="set-v1|"+listOf(configuration,equipment,setup,side,convention).joinToString("|") { "${it.length}:$it" }
        record.requiredText("exerciseName")
        require(decision.getValue("affectedSets") is JsonArray)
        decision.requiredText("type");decision.requiredText("reason")
        return CalibrationDecisionEntity(
            id=record.requiredText("id"),sourceSetId=record.requiredText("sourceSetId"),sessionLogId=record.requiredText("sessionLogId"),
            programId=record.optionalText("programId"),exerciseConfigId=configuration,contextKey=key,
            capacityKg=decision.optionalNumber("capacityKg"),referenceKg=decision.optionalNumber("referenceKg"),
            referenceSource=decision.requiredText("referenceSource"),ratio=decision.optionalNumber("ratio"),
            factor=requireNotNull(decision.optionalNumber("factor")),scope=decision.requiredText("scope"),
            eligibility=decision.requiredText("eligibility"),affected=decision.getValue("affectedSets").toString(),propagated="[]",
            status=record.optionalText("status") ?: "PROPOSED",createdAt=record.getValue("createdAt").jsonPrimitive.long,
            resolvedAt=record["resolvedAt"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.long,
            estimatorVersion=decision.getValue("estimatorVersion").jsonPrimitive.int,data=record.toString(),
        )
    }
    private fun JsonObject.optionalText(key: String): String? = get(key)?.takeUnless { it is JsonNull }?.jsonPrimitive?.also { require(it.isString) }?.content
    private fun JsonObject.requiredText(key: String): String = requireNotNull(optionalText(key)).also { require(it.isNotBlank()) }
    private fun JsonObject.optionalNumber(key: String): Double? = get(key)?.takeUnless { it is JsonNull }?.jsonPrimitive?.double?.also { require(it.isFinite()) }
}
