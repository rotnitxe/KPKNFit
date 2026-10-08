package com.example.kpkn.domain.sync

import kotlinx.serialization.json.*
import java.util.UUID

/** Only independent records can be copied; references inside them remain attached to their originals. */
object ConflictCopies {
    val kinds = setOf("program", "workout_log", "competition", "body_observation", "body_goal",
        "custom_food", "custom_exercise", "session_template", "meal_template")

    fun supported(operation: PersonalOperation, payload: JsonObject?): Boolean =
        operation.kind in kinds && !operation.deleted && !((payload?.get("id") as? JsonPrimitive)?.contentOrNull).isNullOrBlank()

    fun candidate(uid: String, conflictId: String, original: PersonalOperation, payload: JsonObject): PersonalOperation {
        require(supported(original, payload)) { "This record cannot be duplicated" }
        val suffix = UUID.nameUUIDFromBytes("kpkn-conflict-copy-v1/$uid/$conflictId".toByteArray(Charsets.UTF_8)).toString()
        val copyId = "copy_$suffix"
        return original.copy(operationId = "resolve_$suffix", entityId = copyId, sourceId = copyId,
            baseRevision = 0, guestImportId = null, payloadRef = null,
            payload = JsonObject(payload + ("id" to JsonPrimitive(copyId))))
    }
}
