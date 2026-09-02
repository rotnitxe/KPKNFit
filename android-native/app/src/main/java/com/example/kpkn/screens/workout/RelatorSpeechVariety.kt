package com.example.kpkn.screens.workout

internal const val RELATOR_SPEECH_MEMORY_SIZE = 12

internal data class RelatorSpeechMemory(
    val fingerprints: List<String> = emptyList(),
) {
    fun record(fingerprint: String): RelatorSpeechMemory {
        val trimmed = fingerprint.trim()
        if (trimmed.isEmpty()) return this
        return copy(fingerprints = (fingerprints + trimmed).takeLast(RELATOR_SPEECH_MEMORY_SIZE))
    }
}

internal data class RelatorVariantPick(
    val text: String,
    val fingerprint: String,
    val suppressed: Boolean = false,
)

internal fun relatorSpeechFingerprint(
    bucket: RelatorSpeechBucket,
    variantIndex: Int,
    extra: String? = null,
): String {
    val key = bucket.varietyKey()
    val suffix = extra?.trim()?.takeIf { it.isNotEmpty() }
    return if (suffix == null) "$key:$variantIndex" else "$key:$suffix:$variantIndex"
}

internal fun RelatorSpeechBucket.varietyKey(): String = when (this) {
    RelatorSpeechBucket.IDLE_FIRST_HIST,
    RelatorSpeechBucket.IDLE_FIRST_NEW,
    RelatorSpeechBucket.IDLE_MID,
    RelatorSpeechBucket.IDLE_LAST,
    RelatorSpeechBucket.IDLE_COMPOUND,
    -> "IDLE_SITUATE"
    RelatorSpeechBucket.TISSUE_INTRA,
    RelatorSpeechBucket.TISSUE_DAY,
    -> "TISSUE"
    else -> name
}

internal val RelatorSpeechBucket.isSituateFamily: Boolean
    get() = varietyKey() == "IDLE_SITUATE"

internal val RelatorSpeechBucket.silencesWhenExhausted: Boolean
    get() = when (this) {
        RelatorSpeechBucket.HIDDEN,
        RelatorSpeechBucket.PR,
        RelatorSpeechBucket.PR_STAR,
        RelatorSpeechBucket.ASSIST_GAP_SET,
        RelatorSpeechBucket.ASSIST_GAP_UNI,
        RelatorSpeechBucket.ASSIST_GAP_SUPERSET,
        RelatorSpeechBucket.ASSIST_GAP_EXERCISE,
        RelatorSpeechBucket.ASSIST_TIME,
        RelatorSpeechBucket.ASSIST_MOBILITY,
        RelatorSpeechBucket.ASSIST_CONFIRM,
        RelatorSpeechBucket.CAUTION_FAILED_SET,
        RelatorSpeechBucket.IDLE_DISCOMFORT,
        -> false
        else -> true
    }

internal fun pickRelatorVariant(
    bucket: RelatorSpeechBucket,
    variants: List<String>,
    memory: RelatorSpeechMemory,
    extra: String? = null,
): RelatorVariantPick {
    val cleaned = variants.map { it.trim() }.filter { it.isNotEmpty() }
    if (cleaned.isEmpty()) {
        return RelatorVariantPick(
            text = "",
            fingerprint = relatorSpeechFingerprint(bucket, 0, extra),
            suppressed = true,
        )
    }
    val indexed = cleaned.mapIndexed { index, text ->
        RelatorVariantPick(
            text = text,
            fingerprint = relatorSpeechFingerprint(bucket, index, extra),
        )
    }
    val last = memory.fingerprints.lastOrNull()
    val unused = indexed.filter { it.fingerprint !in memory.fingerprints }
    val chosen = unused.firstOrNull { it.fingerprint != last } ?: unused.firstOrNull()
    if (chosen != null) return chosen
    if (bucket.silencesWhenExhausted) {
        return indexed.last().copy(suppressed = true)
    }
    return indexed.firstOrNull { it.fingerprint != last } ?: indexed.first()
}

internal fun relatorUtteranceContext(snapshot: LiveRelatorSnapshot): String = listOf(
    snapshot.sessionSpeechKey,
    snapshot.setKey,
    snapshot.phase.name,
    snapshot.speechBucket().name,
    snapshot.lastChangedField.name,
    snapshot.enteredWeightRaw,
    snapshot.enteredReps?.toString().orEmpty(),
    snapshot.enteredIntensity?.toString().orEmpty(),
    snapshot.dropSetCount.toString(),
    snapshot.reachedFailure.toString(),
    snapshot.assistOffer?.stickyKey.orEmpty(),
    snapshot.assistAck?.kind?.name.orEmpty(),
    snapshot.failedSetCaution?.stickyKey.orEmpty(),
).joinToString("|")

internal class RelatorSpeechSession {
    var memory: RelatorSpeechMemory = RelatorSpeechMemory()
        private set
    var shownConceptIds: Set<String> = emptySet()
        private set
    private var lastContext: String? = null
    private var lastResolution: RelatorResolution? = null

    fun resolve(snapshot: LiveRelatorSnapshot, previousText: String? = null): RelatorResolution {
        val context = relatorUtteranceContext(snapshot)
        if (lastContext != null && lastContext != context) {
            commit(lastResolution)
        }
        val live = snapshot.copy(
            speechMemory = memory,
            shownConceptIds = shownConceptIds,
        )
        val resolution = WorkoutLiveRelator.resolve(live, previousText)
        lastContext = context
        lastResolution = resolution
        return resolution
    }

    private fun commit(resolution: RelatorResolution?) {
        if (resolution == null || resolution.holdPrevious) return
        resolution.fingerprint?.let { memory = memory.record(it) }
        resolution.spokenConceptId?.let { shownConceptIds = shownConceptIds + it }
    }
}
