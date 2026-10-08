package com.example.kpkn.domain.sync

import kotlinx.serialization.json.*

/** Pending paid edits remain durable while ordinary data and deletion continue to synchronize. */
object SyncCapabilities {
    private val paid = setOf("nutrition_log", "nutrition_plan", "nutrition_active_state", "pantry_item",
        "meal_template", "custom_food", "learned_resolution", "nutrition_calibration", "workout_media")

    fun requiresPro(kind: String, payload: JsonObject?, deleted: Boolean): Boolean {
        if (deleted) return false
        if (kind in paid) return true
        if (kind != "program") return false
        if (payload == null) return true
        return runCatching {
            fun children(value: JsonObject, name: String): List<JsonObject> = value[name]?.let { element ->
                if (element == JsonNull) emptyList() else element.jsonArray.map { it.jsonObject }
            }.orEmpty()
            val weeks = children(payload, "macrocycles").flatMap { children(it, "blocks") }
                .flatMap { children(it, "mesocycles") }.flatMap { children(it, "weeks") }
            (payload["structure"]?.jsonPrimitive?.content ?: "SIMPLE") != "SIMPLE" || weeks.size > 1 ||
                children(payload, "loops").isNotEmpty() ||
                listOf("calendarization", "schedulePlan").any { payload[it]?.let { value -> value != JsonNull && value != JsonPrimitive(false) } == true } ||
                weeks.any { children(it, "sessions").size > 5 }
        }.getOrDefault(true)
    }
}
