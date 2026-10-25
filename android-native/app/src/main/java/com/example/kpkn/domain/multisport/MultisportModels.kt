package com.example.kpkn.domain.multisport

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.CompositeDecoder
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Stable sport-family identifiers persisted in local training intent. */
@Serializable
enum class SportId {
    STRENGTH,
    HYPERTROPHY,
    POWERLIFTING,
    POWERBUILDING,
    WEIGHTLIFTING,
    CALISTHENICS,
    STRONGMAN,
    ARMWRESTLING,
    RUNNING,
    CYCLING,
    TRAIL,
    ROWING,
    SWIMMING,
    TRIATHLON,
    CROSSFIT,
    HYROX,
    BOXING,
    KICKBOXING,
    MUAY_THAI,
    BJJ,
    WRESTLING,
    JUDO,
    MMA,
    CLIMBING,
    SPRINT,
    JUMP,
    TENNIS,
    PADEL,
    TEAM_SPORT,
}

/** Experience is recorded per sport goal, rather than as a global user level. */
@Serializable
enum class SportExperience {
    UNKNOWN,
    BEGINNER,
    INTERMEDIATE,
    ADVANCED,
    COMPETITIVE,
}

@Serializable
enum class GoalPriority {
    PROGRESS,
    MAINTAIN,
    RECREATION,
}

/** A user-supplied target; its unit is intentionally sport-specific and opaque here. */
@Serializable
data class SportGoalTarget(
    val unit: String,
    val value: Double,
)

@Serializable
data class SportGoal(
    /** Stable identifier used by [TrainingIntent.primaryGoalId]. */
    val id: String,
    val sport: SportId,
    val objective: String,
    val priority: GoalPriority,
    val experience: SportExperience,
    val target: SportGoalTarget? = null,
)

/**
 * Ordered user intent across sports. Goals are copied on input and on access so neither
 * caller-owned mutable lists nor casts of the read-only view can mutate this value.
 * Validation is deliberately separate from deserialization for legacy and future data.
 */
@Serializable(with = TrainingIntentSerializer::class)
class TrainingIntent private constructor(
    val schemaVersion: Int,
    private val orderedGoals: List<SportGoal>,
    private val primaryId: String,
    private val unknownPayload: JsonObject?,
) {
    constructor(
        schemaVersion: Int = CURRENT_SCHEMA_VERSION,
        goals: List<SportGoal>,
        primaryGoalId: String,
    ) : this(schemaVersion, goals.toList(), primaryGoalId, null) {
        require(schemaVersion == CURRENT_SCHEMA_VERSION) {
            "Only the current training intent schema can be constructed or edited"
        }
    }

    val isOpaque: Boolean get() = unknownPayload != null

    /** Detached JSON for unsupported versions; fields must not be interpreted as v1. */
    val opaquePayload: JsonObject?
        get() = unknownPayload?.let { Json.parseToJsonElement(it.toString()).jsonObject }

    val goals: List<SportGoal>
        get() {
            check(!isOpaque) { "Unsupported training intent schema: $schemaVersion" }
            return orderedGoals.toList()
        }

    val primaryGoalId: String
        get() {
            check(!isOpaque) { "Unsupported training intent schema: $schemaVersion" }
            return primaryId
        }

    override fun equals(other: Any?): Boolean =
        this === other || (other is TrainingIntent &&
            schemaVersion == other.schemaVersion &&
            orderedGoals == other.orderedGoals &&
            primaryId == other.primaryId &&
            unknownPayload == other.unknownPayload)

    override fun hashCode(): Int {
        var result = schemaVersion
        result = 31 * result + orderedGoals.hashCode()
        result = 31 * result + primaryId.hashCode()
        result = 31 * result + (unknownPayload?.hashCode() ?: 0)
        return result
    }

    companion object {
        const val CURRENT_SCHEMA_VERSION: Int = 1

        internal fun opaque(version: Int, payload: JsonObject): TrainingIntent = TrainingIntent(
            schemaVersion = version,
            orderedGoals = emptyList(),
            primaryId = "",
            unknownPayload = Json.parseToJsonElement(payload.toString()).jsonObject,
        )
    }
}

/** Explicit serializer keeps the constructor's defensive list copy in effect for JSON too. */
object TrainingIntentSerializer : KSerializer<TrainingIntent> {
    private val goalsSerializer = ListSerializer(SportGoal.serializer())

    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("com.example.kpkn.domain.multisport.TrainingIntent") {
            element<Int>("schemaVersion", isOptional = true)
            element("goals", goalsSerializer.descriptor)
            element<String>("primaryGoalId")
        }

    override fun serialize(encoder: Encoder, value: TrainingIntent) {
        if (value.isOpaque) {
            val jsonEncoder = encoder as? JsonEncoder
                ?: throw SerializationException("Opaque training intent requires JSON encoding")
            jsonEncoder.encodeJsonElement(value.opaquePayload!!)
            return
        }
        encoder.encodeStructure(descriptor) {
            encodeIntElement(descriptor, 0, value.schemaVersion)
            encodeSerializableElement(descriptor, 1, goalsSerializer, value.goals)
            encodeStringElement(descriptor, 2, value.primaryGoalId)
        }
    }

    override fun deserialize(decoder: Decoder): TrainingIntent {
        if (decoder is JsonDecoder) {
            val payload = decoder.decodeJsonElement().jsonObject
            val version = payload["schemaVersion"]?.jsonPrimitive?.int ?: TrainingIntent.CURRENT_SCHEMA_VERSION
            if (version != TrainingIntent.CURRENT_SCHEMA_VERSION) return TrainingIntent.opaque(version, payload)
            val current = decoder.json.decodeFromJsonElement(CurrentTrainingIntentPayload.serializer(), payload)
            return TrainingIntent(current.schemaVersion, current.goals, current.primaryGoalId)
        }
        return decoder.decodeStructure(descriptor) {
            var schemaVersion = TrainingIntent.CURRENT_SCHEMA_VERSION
            var goals: List<SportGoal>? = null
            var primaryGoalId: String? = null

            if (decodeSequentially()) {
                schemaVersion = decodeIntElement(descriptor, 0)
                goals = decodeSerializableElement(descriptor, 1, goalsSerializer)
                primaryGoalId = decodeStringElement(descriptor, 2)
            } else {
                loop@ while (true) {
                    when (val index = decodeElementIndex(descriptor)) {
                        CompositeDecoder.DECODE_DONE -> break@loop
                        0 -> schemaVersion = decodeIntElement(descriptor, index)
                        1 -> goals = decodeSerializableElement(descriptor, index, goalsSerializer)
                        2 -> primaryGoalId = decodeStringElement(descriptor, index)
                        else -> throw SerializationException("Unexpected TrainingIntent field index: $index")
                    }
                }
            }

            TrainingIntent(
                schemaVersion = schemaVersion,
                goals = goals ?: throw SerializationException("Missing TrainingIntent goals"),
                primaryGoalId = primaryGoalId
                    ?: throw SerializationException("Missing TrainingIntent primaryGoalId"),
            )
        }
    }
}

/** Internal v1 decoding shape honors the caller's normal JSON unknown-field policy. */
@Serializable
private data class CurrentTrainingIntentPayload(
    val schemaVersion: Int = TrainingIntent.CURRENT_SCHEMA_VERSION,
    val goals: List<SportGoal>,
    val primaryGoalId: String,
)
