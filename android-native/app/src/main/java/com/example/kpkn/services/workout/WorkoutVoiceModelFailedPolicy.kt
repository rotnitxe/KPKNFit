package com.example.kpkn.services.workout

object WorkoutVoiceModelFailedPolicy {
    const val MODEL_UNAVAILABLE_MESSAGE = "Modelo de voz no disponible"
    const val MIC_PERMISSION_MESSAGE = "Concede el micrófono"

    fun shouldAttemptNativeFallback(
        nativeAvailable: Boolean,
        alreadyAttempted: Boolean,
    ): Boolean = nativeAvailable && !alreadyAttempted

    fun isFatalEngineError(message: String): Boolean {
        val lower = message.lowercase()
        return MODEL_UNAVAILABLE_MESSAGE.lowercase() in lower ||
            MIC_PERMISSION_MESSAGE.lowercase() in lower ||
            "concede el microfono" in lower
    }
}

internal enum class VoiceHostResumeAction {
    NONE,
    DISABLE_PERMISSION,
    ENABLE,
    REENABLE_RECOVERY,
}

internal fun resolveVoiceHostResumeAction(
    voiceSessionEnabled: Boolean,
    controllerEnabled: Boolean,
    stage: VoicePipelineStage,
    hasAudioPermission: Boolean,
): VoiceHostResumeAction {
    if (!voiceSessionEnabled) return VoiceHostResumeAction.NONE
    if (!hasAudioPermission) return VoiceHostResumeAction.DISABLE_PERMISSION
    if (!controllerEnabled) return VoiceHostResumeAction.ENABLE
    return if (stage == VoicePipelineStage.ERROR_RECOVERY || stage == VoicePipelineStage.MIC_BUSY) {
        VoiceHostResumeAction.REENABLE_RECOVERY
    } else {
        VoiceHostResumeAction.NONE
    }
}

internal fun voiceGrammarExerciseAliases(
    currentName: String?,
    nextName: String?,
    sessionNames: List<String>,
    userAliases: Collection<String>,
    limit: Int = 24,
): Set<String> {
    val ordered = linkedSetOf<String>()
    fun add(raw: String?) {
        raw?.trim()?.takeIf { it.isNotBlank() }?.let { ordered += it }
    }
    add(currentName)
    add(nextName)
    sessionNames.forEach(::add)
    userAliases.forEach(::add)
    return ordered.take(limit).toSet()
}
