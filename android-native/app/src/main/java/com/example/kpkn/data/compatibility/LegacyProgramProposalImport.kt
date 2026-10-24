package com.example.kpkn.data.compatibility

import kotlinx.serialization.json.*

/** Known persisted proposal paths only; never rewrites sessions, weights, dates, notes or run identity. */
object LegacyProgramProposalImport {
    private val retained = setOf("ADJUST_TM", "ADJUST_XRM", "PROMOTE_TM")
    private fun proposal(value: JsonElement): Boolean = value.jsonObject["kind"]?.jsonPrimitive?.content in retained
    private fun run(value: JsonElement): JsonElement {
        if (value == JsonNull) return value
        val obj = value.jsonObject
        return JsonObject(obj.toMutableMap().apply {
            (obj["pendingAction"] as? JsonObject)?.let { action ->
                when (action["type"]?.jsonPrimitive?.content) {
                    "CONFIRM_DELOAD" -> put("pendingAction", JsonNull)
                    "CONFIRM_AUTOREGULATION" -> {
                        val proposals = action["proposals"]?.jsonArray.orEmpty().filter(::proposal)
                        put("pendingAction", if (proposals.isEmpty()) JsonNull else
                            JsonObject(action + mapOf("proposals" to JsonArray(proposals),
                                "message" to JsonPrimitive("Revisa las propuestas del protocolo; se aplican solo con tu confirmación."))))
                    }
                }
            }
            // Mixed audit entries remain original audit records, with retired derived kinds removed.
            (obj["autoregulationAudit"] as? JsonArray)?.let { entries ->
                put("autoregulationAudit", JsonArray(entries.mapNotNull { entry ->
                    val audit = entry.jsonObject
                    val kinds = audit["kinds"]?.jsonArray.orEmpty().filter { it.jsonPrimitive.content in retained }
                    if (kinds.isEmpty()) null else JsonObject(audit + ("kinds" to JsonArray(kinds)))
                }))
            }
        })
    }
    fun program(raw: String): String {
        val obj = LegacyTrainingImport.parseObject(raw)
        return JsonObject(obj.toMutableMap().apply {
            obj["runState"]?.let { put("runState", run(it)) }
            (obj["calendarBreaks"] as? JsonArray)?.let { breaks -> put("calendarBreaks", JsonArray(breaks.map { item ->
                val row = item.jsonObject
                JsonObject(row.toMutableMap().apply { row["pausedRunState"]?.let { put("pausedRunState", run(it)) } })
            })) }
        }).toString()
    }
}
