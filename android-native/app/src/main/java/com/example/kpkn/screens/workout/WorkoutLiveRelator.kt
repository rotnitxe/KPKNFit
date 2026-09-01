package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.LoadModeV2
import com.example.kpkn.data.models.UnitModeV2

internal const val RELATOR_MAX_LINE_CHARS = 140 // two-line visual budget; UI wraps, copy keeps the name
internal const val RELATOR_DEBOUNCE_MS = 400L
internal const val RELATOR_IDLE_ROTATE_MS = 28_000L

internal enum class RelatorPhase {
    HIDDEN,
    MOBILITY,
    WARMUP,
    WORKING,
    REST,
}

internal enum class RelatorFamily {
    PRESS,
    PULL,
    SQUAT,
    HINGE,
    ISOLATION,
    OTHER,
}

internal enum class RelatorChangedField {
    NONE,
    WEIGHT,
    REPS,
    INTENSITY,
    WARMUP_WEIGHT,
    MOBILITY_CHECK,
    MOBILITY_TIMER,
    DROPSET,
    FAILURE,
}

internal enum class RelatorLoadKind {
    LOAD,
    BODYWEIGHT,
    OTHER,
}

internal enum class RelatorUnit {
    REPS,
    TIME,
    OTHER,
}

internal enum class RelatorWeightBand {
    CONSERVATIVE,
    MATCH,
    AGGRESSIVE,
}

internal enum class RelatorEffortZone {
    INEFFECTIVE,
    MEASURED,
    PRODUCTIVE,
    HARD,
    FAILURE,
}

internal enum class RelatorCompound {
    NONE,
    BACK_SQUAT_HIGH,
    BACK_SQUAT_LOW,
    BENCH_BAR,
    DEADLIFT_CONV,
    DEADLIFT_SUMO,
    RDL,
}

internal enum class RelatorSpeechBucket {
    HIDDEN,
    IDLE_MOBILITY,
    IDLE_WARMUP,
    IDLE_WARMUP_LAST,
    IDLE_REST,
    IDLE_FIRST_HIST,
    IDLE_FIRST_NEW,
    IDLE_MID,
    IDLE_LAST,
    IDLE_COMPOUND,
    TISSUE_INTRA,
    TISSUE_DAY,
    WARMUP_WEIGHT_BELOW,
    WARMUP_WEIGHT_ABOVE,
    WEIGHT_BELOW,
    WEIGHT_ABOVE,
    REPS_BELOW,
    REPS_ABOVE,
    TIME,
    EFFORT_INEFFECTIVE,
    EFFORT_MEASURED,
    EFFORT_HARD,
    EFFORT_FAILURE,
    DROPSET_ONE,
    DROPSET_MANY,
    PR,
    PR_STAR,
    IDLE_DISCOMFORT,
    ASSIST_GAP_SET,
    ASSIST_GAP_UNI,
    ASSIST_GAP_SUPERSET,
    ASSIST_GAP_EXERCISE,
    ASSIST_TIME,
    ASSIST_MOBILITY,
}

internal data class LiveRelatorSnapshot(
    val visible: Boolean,
    val phase: RelatorPhase,
    val family: RelatorFamily = RelatorFamily.OTHER,
    val feminine: Boolean = false,
    val exerciseDisplayName: String = "",
    val setIndex: Int = 0,
    val setCount: Int = 1,
    val hasHistory: Boolean = false,
    val warmupIncompleteIndex: Int? = null,
    val warmupCount: Int = 0,
    val warmupIsLastIncomplete: Boolean = false,
    val mobilityCompleted: Int = 0,
    val mobilityTotal: Int = 0,
    val mobilityTimerRunning: Boolean = false,
    val mobilityRemainingSeconds: Int? = null,
    val lastChangedField: RelatorChangedField = RelatorChangedField.NONE,
    val enteredWeight: Double? = null,
    val enteredWeightRaw: String = "",
    val referenceWeight: Double? = null,
    val suggestedWeight: Double? = null,
    val lastLiftedWeight: Double? = null,
    val enteredReps: Double? = null,
    val plannedReps: Double? = null,
    val enteredIntensity: Double? = null,
    val plannedIntensity: Double? = null,
    val intensityMode: IntensityMode? = null,
    val reachedFailure: Boolean = false,
    val plannedFailure: Boolean = false,
    val dropSetCount: Int = 0,
    val plannedDropCount: Int = 0,
    val compound: RelatorCompound = RelatorCompound.NONE,
    val tissueHint: RelatorTissueHint? = null,
    val loadKind: RelatorLoadKind = RelatorLoadKind.LOAD,
    val unit: RelatorUnit = RelatorUnit.REPS,
    val setKey: String = "",
    val parentContextKey: String = "",
    val idleCycle: Int = 0,
    val assistOffer: RelatorAssistOffer? = null,
    val isSuperset: Boolean = false,
    val activeSideLabel: String? = null,
    val sessionLastSet: RelatorSessionSetMemory? = null,
    val historyLastSet: RelatorSessionSetMemory? = null,
    val discomfortHint: RelatorDiscomfortHint? = null,
    val prHint: RelatorPrHint? = null,
)

internal data class RelatorResolution(
    val text: String?,
    val holdPrevious: Boolean,
    val phaseKey: String,
    val actions: List<RelatorAssistAction> = emptyList(),
)

internal object WorkoutLiveRelator {
    fun resolve(
        snapshot: LiveRelatorSnapshot,
        previousText: String? = null,
    ): RelatorResolution {
        if (!snapshot.visible || snapshot.phase == RelatorPhase.HIDDEN) {
            return RelatorResolution(text = null, holdPrevious = false, phaseKey = RelatorSpeechBucket.HIDDEN.phaseKey())
        }
        val voice = RelatorVoice.of(snapshot.feminine)

        val typingPartialWeight = snapshot.lastChangedField == RelatorChangedField.WEIGHT ||
            snapshot.lastChangedField == RelatorChangedField.WARMUP_WEIGHT
        if (typingPartialWeight &&
            snapshot.loadKind == RelatorLoadKind.LOAD &&
            !snapshot.isPlausibleWeight() &&
            previousText != null
        ) {
            return RelatorResolution(
                text = previousText,
                holdPrevious = true,
                phaseKey = snapshot.speechBucket().phaseKey(),
            )
        }

        val bucket = snapshot.speechBucket()
        val copy = WorkoutLiveRelatorCatalog.copyFor(bucket, snapshot)
        val actions = if (bucket.isAssist) snapshot.assistOffer?.actions.orEmpty() else emptyList()
        return line(copy, snapshot, voice, actions)
    }

    fun allResolvedSamples(longExerciseName: String): List<String> =
        WorkoutLiveRelatorCatalog.enumerateSnapshots(longExerciseName).mapNotNull { sample ->
            resolve(sample).text
        }
}

internal fun RelatorSpeechBucket.phaseKey(): String = name.lowercase()

internal fun LiveRelatorSnapshot.speechBucket(): RelatorSpeechBucket {
    if (!visible || phase == RelatorPhase.HIDDEN) return RelatorSpeechBucket.HIDDEN

    if (phase == RelatorPhase.WARMUP && warmupIsLastIncomplete) {
        if (lastChangedField == RelatorChangedField.WARMUP_WEIGHT && isPlausibleWeight()) {
            val anchor = suggestedWeight ?: referenceWeight
            if (isClearWeightBelow(enteredWeight, anchor)) return RelatorSpeechBucket.WARMUP_WEIGHT_BELOW
            if (isClearWeightAboveSuggested(enteredWeight, anchor)) return RelatorSpeechBucket.WARMUP_WEIGHT_ABOVE
        }
        return RelatorSpeechBucket.IDLE_WARMUP_LAST
    }
    if (phase == RelatorPhase.MOBILITY) {
        if (assistOffer?.kind == RelatorAssistKind.MOBILITY) return RelatorSpeechBucket.ASSIST_MOBILITY
        return RelatorSpeechBucket.IDLE_MOBILITY
    }
    if (phase == RelatorPhase.WARMUP) return RelatorSpeechBucket.IDLE_WARMUP
    if (phase == RelatorPhase.REST) {
        if (shouldSpeakPr()) {
            return if (prHint?.isStar == true) RelatorSpeechBucket.PR_STAR else RelatorSpeechBucket.PR
        }
        if (idleCycle > 0) {
            assistOffer?.takeIf { it.kind != RelatorAssistKind.MOBILITY }?.let { return it.kind.speechBucket }
        }
        if (idleCycle > 0 && discomfortHint != null) return RelatorSpeechBucket.IDLE_DISCOMFORT
        return RelatorSpeechBucket.IDLE_REST
    }

    if (shouldSpeakPr()) {
        return if (prHint?.isStar == true) RelatorSpeechBucket.PR_STAR else RelatorSpeechBucket.PR
    }

    when (lastChangedField) {
        RelatorChangedField.FAILURE ->
            if (reachedFailure && !plannedFailure) return RelatorSpeechBucket.EFFORT_FAILURE
        RelatorChangedField.DROPSET -> when {
            dropSetCount > plannedDropCount && dropSetCount >= 2 -> return RelatorSpeechBucket.DROPSET_MANY
            dropSetCount > plannedDropCount && dropSetCount >= 1 -> return RelatorSpeechBucket.DROPSET_ONE
        }
        RelatorChangedField.INTENSITY -> {
            val zone = relatorEffortZone(enteredIntensity, intensityMode, reachedFailure)
            val plannedZone = relatorEffortZone(plannedIntensity, intensityMode, plannedFailure)
            if (zone != null &&
                zone != RelatorEffortZone.PRODUCTIVE &&
                zone != plannedZone
            ) {
                return zone.toSpeechBucket()
            }
        }
        RelatorChangedField.WEIGHT, RelatorChangedField.WARMUP_WEIGHT -> {
            if (phase == RelatorPhase.WARMUP && lastChangedField == RelatorChangedField.WARMUP_WEIGHT && isPlausibleWeight()) {
                val anchor = suggestedWeight ?: referenceWeight
                if (isClearWeightBelow(enteredWeight, anchor)) return RelatorSpeechBucket.WARMUP_WEIGHT_BELOW
                if (isClearWeightAboveSuggested(enteredWeight, anchor)) return RelatorSpeechBucket.WARMUP_WEIGHT_ABOVE
            }
            if (loadKind == RelatorLoadKind.LOAD && isPlausibleWeight()) {
                if (isClearWeightBelow(enteredWeight, lastLiftedWeight)) return RelatorSpeechBucket.WEIGHT_BELOW
                if (isClearWeightAboveSuggested(enteredWeight, suggestedWeight ?: referenceWeight)) {
                    return RelatorSpeechBucket.WEIGHT_ABOVE
                }
            }
        }
        RelatorChangedField.REPS -> {
            if (unit == RelatorUnit.TIME && isClearRepsDelta(enteredReps, plannedReps)) {
                return RelatorSpeechBucket.TIME
            }
            if (unit == RelatorUnit.REPS && isClearRepsDelta(enteredReps, plannedReps)) {
                val entered = enteredReps ?: return situateWorkingBucket()
                val planned = plannedReps ?: return situateWorkingBucket()
                return if (entered < planned) RelatorSpeechBucket.REPS_BELOW else RelatorSpeechBucket.REPS_ABOVE
            }
        }
        else -> Unit
    }

    if (idleCycle > 0) {
        assistOffer?.takeIf { it.kind != RelatorAssistKind.MOBILITY }?.let { return it.kind.speechBucket }
    }

    if (idleCycle > 0 && discomfortHint != null) return RelatorSpeechBucket.IDLE_DISCOMFORT

    if (idleCycle > 0 && tissueHint != null) {
        return if (tissueHint.window == RelatorTissueWindow.INTRA) {
            RelatorSpeechBucket.TISSUE_INTRA
        } else {
            RelatorSpeechBucket.TISSUE_DAY
        }
    }
    return situateWorkingBucket()
}

private fun LiveRelatorSnapshot.shouldSpeakPr(): Boolean {
    if (prHint == null) return false
    if (phase == RelatorPhase.REST) return true
    return lastChangedField == RelatorChangedField.WEIGHT ||
        lastChangedField == RelatorChangedField.REPS
}

private fun LiveRelatorSnapshot.situateWorkingBucket(): RelatorSpeechBucket = when {
    setIndex <= 0 && hasHistory -> RelatorSpeechBucket.IDLE_FIRST_HIST
    setIndex <= 0 -> RelatorSpeechBucket.IDLE_FIRST_NEW
    setCount > 1 && setIndex >= setCount - 1 -> RelatorSpeechBucket.IDLE_LAST
    else -> RelatorSpeechBucket.IDLE_MID
}

private fun RelatorEffortZone.toSpeechBucket(): RelatorSpeechBucket = when (this) {
    RelatorEffortZone.INEFFECTIVE -> RelatorSpeechBucket.EFFORT_INEFFECTIVE
    RelatorEffortZone.MEASURED -> RelatorSpeechBucket.EFFORT_MEASURED
    RelatorEffortZone.PRODUCTIVE -> RelatorSpeechBucket.IDLE_MID
    RelatorEffortZone.HARD -> RelatorSpeechBucket.EFFORT_HARD
    RelatorEffortZone.FAILURE -> RelatorSpeechBucket.EFFORT_FAILURE
}

private fun line(
    template: String,
    snapshot: LiveRelatorSnapshot,
    voice: RelatorVoice,
    actions: List<RelatorAssistAction> = emptyList(),
): RelatorResolution {
    val rendered = renderRelatorTemplate(
        template,
        snapshot.shortExerciseName(),
        voice,
        mustKeep = actions.map { it.clickableSpan() },
    )
    return RelatorResolution(
        text = rendered,
        holdPrevious = false,
        phaseKey = snapshot.speechBucket().phaseKey(),
        actions = actions,
    )
}

internal data class RelatorVoice(
    val conservative: String,
    val ready: String,
    val safe: String,
    val slow: String,
    val prepared: String,
) {
    companion object {
        fun of(feminine: Boolean): RelatorVoice = if (feminine) {
            RelatorVoice(
                conservative = "conservadora",
                ready = "lista",
                safe = "segura",
                slow = "lenta",
                prepared = "preparada",
            )
        } else {
            RelatorVoice(
                conservative = "conservador",
                ready = "listo",
                safe = "seguro",
                slow = "lento",
                prepared = "preparado",
            )
        }
    }
}

internal val RelatorChangedField.isReaction: Boolean
    get() = this == RelatorChangedField.WEIGHT ||
        this == RelatorChangedField.REPS ||
        this == RelatorChangedField.INTENSITY ||
        this == RelatorChangedField.WARMUP_WEIGHT ||
        this == RelatorChangedField.DROPSET ||
        this == RelatorChangedField.FAILURE

internal fun LiveRelatorSnapshot.idlePhaseKey(): String = speechBucket().phaseKey()

internal fun LiveRelatorSnapshot.shortExerciseName(): String {
    val raw = exerciseDisplayName.trim()
    if (raw.isEmpty()) return "ejercicio"
    return raw.split(" · ").first().trim().ifBlank { "ejercicio" }
}

internal fun LiveRelatorSnapshot.weightAnchor(): Double? =
    lastLiftedWeight?.takeIf { it > 0.0 }
        ?: suggestedWeight?.takeIf { it > 0.0 }
        ?: referenceWeight?.takeIf { it > 0.0 }

internal fun LiveRelatorSnapshot.isPlausibleWeight(): Boolean {
    if (loadKind != RelatorLoadKind.LOAD) return false
    val raw = enteredWeightRaw.trim().replace(',', '.')
    if (raw.isEmpty()) return false
    val value = enteredWeight ?: raw.toDoubleOrNull() ?: return false
    if (value <= 0.0) return false
    val reference = weightAnchor()
    val significant = raw.count { it.isDigit() }
    if (reference == null) return significant >= 2 || raw.contains('.')
    val ratio = value / reference
    if (ratio in 0.25..2.5) return true
    return significant >= 2 && ratio in 0.15..3.0
}

internal fun relatorEffortZone(
    value: Double?,
    mode: IntensityMode?,
    reachedFailure: Boolean,
): RelatorEffortZone? {
    if (reachedFailure || mode == IntensityMode.FAILURE) return RelatorEffortZone.FAILURE
    if (value == null) return null
    return when (mode) {
        IntensityMode.RIR -> when {
            value > 4.0 -> RelatorEffortZone.INEFFECTIVE
            value >= 3.0 -> RelatorEffortZone.MEASURED
            value >= 1.0 -> RelatorEffortZone.PRODUCTIVE
            else -> RelatorEffortZone.HARD
        }
        else -> when {
            value < 6.0 -> RelatorEffortZone.INEFFECTIVE
            value <= 7.0 -> RelatorEffortZone.MEASURED
            value <= 8.5 -> RelatorEffortZone.PRODUCTIVE
            value < 10.0 -> RelatorEffortZone.HARD
            else -> RelatorEffortZone.FAILURE
        }
    }
}

internal fun isClearWeightBelow(entered: Double?, lastLifted: Double?): Boolean {
    val live = entered ?: return false
    val anchor = lastLifted?.takeIf { it > 0.0 } ?: return false
    val required = maxOf(5.0, anchor * 0.08)
    return (anchor - live) >= required - 1e-6
}

internal fun isClearWeightAboveSuggested(entered: Double?, suggested: Double?): Boolean {
    val live = entered ?: return false
    val anchor = suggested?.takeIf { it > 0.0 } ?: return false
    val delta = live - anchor
    return delta >= 5.0 - 1e-6 && delta >= anchor * 0.06 - 1e-6
}

internal fun isClearRepsDelta(entered: Double?, planned: Double?): Boolean {
    val live = entered ?: return false
    val target = planned?.takeIf { it > 0.0 } ?: return false
    val required = maxOf(3.0, target * 0.30)
    return kotlin.math.abs(live - target) >= required - 1e-6
}

internal fun LiveRelatorSnapshot.weightBandOrNull(): RelatorWeightBand? {
    val entered = enteredWeight ?: return null
    if (isClearWeightBelow(entered, lastLiftedWeight ?: referenceWeight)) {
        return RelatorWeightBand.CONSERVATIVE
    }
    if (isClearWeightAboveSuggested(entered, suggestedWeight ?: referenceWeight)) {
        return RelatorWeightBand.AGGRESSIVE
    }
    return RelatorWeightBand.MATCH
}

internal fun detectRelatorCompound(
    exerciseName: String,
    movementPatternId: String? = null,
    axialLoadFactor: Double? = null,
): RelatorCompound {
    val name = exerciseName.lowercase()
    val pattern = movementPatternId.orEmpty().lowercase()
    val hay = "$name $pattern"
    fun has(vararg keys: String) = keys.any { it in hay }
    if (has("rdl", "rumano", "romanian")) return RelatorCompound.RDL
    if (has("sumo") && has("muerto", "deadlift", "dead lift")) return RelatorCompound.DEADLIFT_SUMO
    if (has("peso muerto", "deadlift", "dead lift") && !has("rumano", "rdl")) {
        return RelatorCompound.DEADLIFT_CONV
    }
    val benchLike = has("banca", "bench") &&
        !has("mancuerna", "dumbbell", "máquina", "maquina", "machine", "smith")
    if (benchLike && (has("barra", "barbell", "bar ") || axialLoadFactor != null && axialLoadFactor >= 0.6 || has("press"))) {
        if (has("barra", "barbell") || (!has("mancuerna") && has("banca", "bench"))) {
            return RelatorCompound.BENCH_BAR
        }
    }
    val squatLike = has("sentadilla", "squat") &&
        !has("frontal", "front", "goblet", "búlgara", "bulgara", "split", "hack")
    if (squatLike && has("trasera", "back", "alta", "baja", "high-bar", "low-bar", "high bar", "low bar")) {
        return if (has("baja", "low-bar", "low bar", "lowbar")) {
            RelatorCompound.BACK_SQUAT_LOW
        } else {
            RelatorCompound.BACK_SQUAT_HIGH
        }
    }
    if (squatLike && (axialLoadFactor != null && axialLoadFactor >= 0.7)) {
        return RelatorCompound.BACK_SQUAT_HIGH
    }
    return RelatorCompound.NONE
}

internal fun renderRelatorTemplate(
    template: String,
    exerciseName: String,
    voice: RelatorVoice,
    mustKeep: List<String> = emptyList(),
): String {
    fun apply(name: String): String = template
        .replace("{ex}", name)
        .replace("{conservative}", voice.conservative)
        .replace("{ready}", voice.ready)
        .replace("{safe}", voice.safe)
        .replace("{slow}", voice.slow)
        .replace("{prepared}", voice.prepared)

    fun keeps(text: String): Boolean = mustKeep.all { needle ->
        needle.isBlank() || text.contains(needle, ignoreCase = true)
    }

    val full = apply(exerciseName)
    if (keeps(full)) return full
    val shortened = apply(
        if (exerciseName.length > 28) exerciseName.take(27).trimEnd() + "…" else exerciseName,
    )
    if (keeps(shortened)) return shortened
    return full
}

internal fun formatRelatorLoad(value: Double): String {
    val scaled = kotlin.math.round(value * 10.0) / 10.0
    val asLong = scaled.toLong()
    return if (kotlin.math.abs(scaled - asLong) < 1e-6) asLong.toString() else scaled.toString()
}

internal fun formatRelatorDelta(value: Double): String {
    val amount = formatRelatorLoad(kotlin.math.abs(value))
    return if (value >= 0) "+$amount" else "−$amount"
}

internal fun relatorLoadKind(mode: LoadModeV2?): RelatorLoadKind = when (mode) {
    LoadModeV2.LOAD, LoadModeV2.LASTRE, null -> RelatorLoadKind.LOAD
    LoadModeV2.BODYWEIGHT -> RelatorLoadKind.BODYWEIGHT
    LoadModeV2.ASSISTED -> RelatorLoadKind.OTHER
}

internal fun relatorUnit(mode: UnitModeV2?): RelatorUnit = when (mode) {
    UnitModeV2.REPS, null -> RelatorUnit.REPS
    UnitModeV2.TIME -> RelatorUnit.TIME
    UnitModeV2.DISTANCE, UnitModeV2.CUSTOM -> RelatorUnit.OTHER
}

internal fun detectRelatorChangedField(
    phase: RelatorPhase,
    draftDirty: Boolean,
    weightText: String?,
    repsText: String?,
    intensityText: String?,
    prevWeight: String?,
    prevReps: String?,
    prevIntensity: String?,
    warmupDraft: String,
    prevWarmupDraft: String,
    mobilityDone: Int,
    prevMobilityDone: Int,
    timerRunning: Boolean,
    prevTimerRunning: Boolean,
    previousField: RelatorChangedField,
    dropCount: Int = 0,
    prevDropCount: Int = 0,
    reachedFailure: Boolean = false,
    prevReachedFailure: Boolean = false,
): RelatorChangedField {
    if (phase == RelatorPhase.WARMUP && warmupDraft != prevWarmupDraft) {
        return if (warmupDraft.isNotBlank()) RelatorChangedField.WARMUP_WEIGHT else RelatorChangedField.NONE
    }
    if (phase == RelatorPhase.MOBILITY && mobilityDone != prevMobilityDone) {
        return RelatorChangedField.MOBILITY_CHECK
    }
    if (phase == RelatorPhase.MOBILITY && timerRunning && timerRunning != prevTimerRunning) {
        return RelatorChangedField.MOBILITY_TIMER
    }
    if (dropCount != prevDropCount) {
        return RelatorChangedField.DROPSET
    }
    if (reachedFailure != prevReachedFailure) {
        return RelatorChangedField.FAILURE
    }
    if (draftDirty) {
        when {
            weightText != prevWeight -> return RelatorChangedField.WEIGHT
            repsText != prevReps -> return RelatorChangedField.REPS
            intensityText != prevIntensity -> return RelatorChangedField.INTENSITY
        }
        return previousField.takeIf { it.isReaction } ?: RelatorChangedField.NONE
    }
    if (phase == RelatorPhase.WARMUP && warmupDraft.isNotBlank() && previousField == RelatorChangedField.WARMUP_WEIGHT) {
        return RelatorChangedField.WARMUP_WEIGHT
    }
    return RelatorChangedField.NONE
}

internal fun relatorFamilyFrom(
    exerciseName: String,
    jointIds: List<String>,
    movementPatternId: String?,
): RelatorFamily {
    val name = exerciseName.lowercase()
    val pattern = movementPatternId.orEmpty().lowercase()
    val joints = jointIds.map { it.lowercase() }
    fun hasJoint(vararg keys: String) = joints.any { joint -> keys.any { key -> joint.contains(key) } }
    fun named(vararg keys: String) = keys.any { it in name || it in pattern }

    when {
        named("remo", "row", "jalon", "pull", "dominada", "chin") -> return RelatorFamily.PULL
        named("sentadilla", "squat", "zancada", "lunge") -> return RelatorFamily.SQUAT
        named("peso muerto", "deadlift", "rumano", "hinge", "buenos días", "good morning") ->
            return RelatorFamily.HINGE
        named("press", "banca", "bench", "militar", "fondos", "dip") -> return RelatorFamily.PRESS
        named("curl", "extension", "extensión", "raise", "laterales", "fly", "aperturas") ->
            return RelatorFamily.ISOLATION
    }
    return when {
        hasJoint("shoulder", "glenohumeral") && hasJoint("scapula") && named("row", "remo") -> RelatorFamily.PULL
        hasJoint("shoulder", "glenohumeral") -> RelatorFamily.PRESS
        hasJoint("hip", "coxofemoral") && hasJoint("knee") -> RelatorFamily.SQUAT
        hasJoint("spine", "lumbar") || (hasJoint("hip") && named("hinge", "muerto")) -> RelatorFamily.HINGE
        else -> RelatorFamily.OTHER
    }
}
