package com.example.kpkn.screens.sessioneditor

import com.example.kpkn.data.models.Session
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class SessionMergeConflict(
    val path: String,
    val base: JsonElement?,
    val local: JsonElement?,
    val remote: JsonElement?,
)

internal data class SessionThreeWayMerge(
    val merged: JsonElement,
    val conflicts: List<SessionMergeConflict>,
)

/**
 * Three-way merge for an editor draft racing a durable live edit. Object fields merge
 * independently; arrays of identified objects merge by stable `id`, so edits to different
 * exercises or sets survive. Simultaneous edits to one field or conflicting order changes are
 * reported for explicit resolution.
 */
internal fun mergeSessionThreeWay(
    base: Session,
    local: Session,
    remote: Session,
    remoteChoices: Map<String, Boolean> = emptyMap(),
): SessionThreeWayMerge {
    val json = Json { encodeDefaults = true; explicitNulls = false }
    val conflicts = linkedMapOf<String, SessionMergeConflict>()
    val merged = mergeJson(
        base = json.encodeToJsonElement(Session.serializer(), base),
        local = json.encodeToJsonElement(Session.serializer(), local),
        remote = json.encodeToJsonElement(Session.serializer(), remote),
        path = "$",
        remoteChoices = remoteChoices,
        conflicts = conflicts,
    ) ?: JsonNull
    return SessionThreeWayMerge(merged, conflicts.values.toList())
}

private fun mergeJson(
    base: JsonElement?,
    local: JsonElement?,
    remote: JsonElement?,
    path: String,
    remoteChoices: Map<String, Boolean>,
    conflicts: MutableMap<String, SessionMergeConflict>,
): JsonElement? {
    if (local == remote) return local
    if (local == base) return remote
    if (remote == base) return local

    if (base is JsonObject && local is JsonObject && remote is JsonObject) {
        val keys = base.keys + local.keys + remote.keys
        val merged = linkedMapOf<String, JsonElement>()
        keys.forEach { key ->
            val value = mergeJson(
                base[key], local[key], remote[key], "$path.$key", remoteChoices, conflicts,
            )
            if (value != null) merged[key] = value
        }
        return JsonObject(merged)
    }

    if (base is JsonArray && local is JsonArray && remote is JsonArray &&
        (base + local + remote).all { it is JsonObject && (it["id"] as? JsonPrimitive)?.isString == true }
    ) {
        return mergeIdentifiedArrays(base, local, remote, path, remoteChoices, conflicts)
    }

    conflicts[path] = SessionMergeConflict(path, base, local, remote)
    return if (remoteChoices[path] == true) remote else local
}

private fun mergeIdentifiedArrays(
    base: JsonArray,
    local: JsonArray,
    remote: JsonArray,
    path: String,
    remoteChoices: Map<String, Boolean>,
    conflicts: MutableMap<String, SessionMergeConflict>,
): JsonArray {
    fun id(element: JsonElement): String = (element as JsonObject).getValue("id").let { (it as JsonPrimitive).content }
    val baseById = base.associateBy(::id)
    val localById = local.associateBy(::id)
    val remoteById = remote.associateBy(::id)
    val allIds = (baseById.keys + localById.keys + remoteById.keys).toList()
    val mergedById = linkedMapOf<String, JsonElement>()
    allIds.forEach { id ->
        val merged = mergeJson(
            baseById[id], localById[id], remoteById[id], "$path[id=$id]", remoteChoices, conflicts,
        )
        if (merged != null && merged != JsonNull) mergedById[id] = merged
    }

    val baseOrder = base.map(::id)
    val localOrder = local.map(::id)
    val remoteOrder = remote.map(::id)
    val baseMembers = baseOrder.toSet()
    val baseLocalOrder = localOrder.filter { it in baseMembers }
    val baseRemoteOrder = remoteOrder.filter { it in baseMembers }
    val baseOrderStillPresent = baseOrder.filter { it in baseLocalOrder || it in baseRemoteOrder }
    val localReordered = baseLocalOrder != baseOrderStillPresent
    val remoteReordered = baseRemoteOrder != baseOrderStillPresent
    val orderPath = "$path (orden)"
    val chosenBaseOrder = when {
        baseLocalOrder == baseRemoteOrder -> baseLocalOrder
        !localReordered -> baseRemoteOrder
        !remoteReordered -> baseLocalOrder
        else -> {
            conflicts[orderPath] = SessionMergeConflict(
                orderPath,
                JsonArray(baseOrder.map(::JsonPrimitive)),
                JsonArray(localOrder.map(::JsonPrimitive)),
                JsonArray(remoteOrder.map(::JsonPrimitive)),
            )
            if (remoteChoices[orderPath] == true) baseRemoteOrder else baseLocalOrder
        }
    }
    val preferredAdditions = if (remoteChoices[orderPath] == true) remoteOrder else localOrder
    val otherAdditions = if (remoteChoices[orderPath] == true) localOrder else remoteOrder
    val finalOrder = (chosenBaseOrder + preferredAdditions + otherAdditions + allIds)
        .distinct()
        .filter { it in mergedById }
    return JsonArray(finalOrder.mapNotNull(mergedById::get))
}

internal fun SessionMergeConflict.label(): String = path

internal fun SessionMergeConflict.valueText(value: JsonElement?): String = when (value) {
    null -> "eliminado"
    JsonNull -> "vacío"
    is JsonPrimitive -> value.content
    is JsonArray -> value.joinToString(prefix = "[", postfix = "]") { (it as? JsonPrimitive)?.content ?: it.toString() }
    else -> value.toString().take(120)
}
