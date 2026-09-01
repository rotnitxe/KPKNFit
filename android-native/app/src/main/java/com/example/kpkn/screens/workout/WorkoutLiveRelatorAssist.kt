package com.example.kpkn.screens.workout

import com.example.kpkn.data.models.MobilityExercise
import com.example.kpkn.data.models.MobilityExerciseCatalog

internal const val RELATOR_TIME_CRUNCH_SECONDS = 300
internal const val RELATOR_TIME_CRUNCH_MIN_SETS = 3

internal enum class RelatorAssistKind {
    GAP_SET,
    GAP_UNI_SIDE,
    GAP_SUPERSET,
    GAP_EXERCISE,
    TIME,
    MOBILITY,
}

internal enum class RelatorAssistActionKind {
    JUMP_TO_SET,
    OMIT_SET,
    JUMP_TO_EXERCISE,
    MOVE_EXERCISE_END,
    JUMP_TO_SIDE,
    CONVERT_DROPSETS,
    HALVE_SETS,
    ADD_MOBILITY,
}

internal data class RelatorAssistAction(
    val kind: RelatorAssistActionKind,
    val label: String,
    val exerciseId: String = "",
    val setIndex: Int = -1,
    val side: String = "",
    val mobilityId: String = "",
    val span: String = "",
)

internal data class RelatorAssistOffer(
    val kind: RelatorAssistKind,
    val text: String,
    val actions: List<RelatorAssistAction>,
    val stickyKey: String,
)

internal data class RelatorAssistExercise(
    val id: String,
    val name: String,
    val setCount: Int,
    val groupId: String?,
    val unilateral: Boolean,
    val isCardio: Boolean,
    val mobilityLabels: List<String> = emptyList(),
)

internal data class RelatorAssistContext(
    val phase: RelatorPhase,
    val family: RelatorFamily,
    val currentExerciseId: String,
    val currentExerciseName: String,
    val currentSetIndex: Int,
    val currentExerciseIndex: Int,
    val activeSide: String?,
    val sessionExercises: List<RelatorAssistExercise>,
    val completedSetKeys: Set<String>,
    val omittedSetKeys: Set<String>,
    val skippedExerciseIds: Set<String>,
    val remainingSeconds: Int?,
)

internal fun relatorParentContextKey(
    exerciseId: String,
    groupId: String?,
    groupMemberCount: Int,
    unilateral: Boolean,
): String = when {
    !groupId.isNullOrBlank() && groupMemberCount > 1 -> "ss:$groupId"
    unilateral -> "ex:$exerciseId:uni"
    else -> "ex:$exerciseId"
}

internal val RelatorAssistKind.speechBucket: RelatorSpeechBucket
    get() = when (this) {
        RelatorAssistKind.GAP_SET -> RelatorSpeechBucket.ASSIST_GAP_SET
        RelatorAssistKind.GAP_UNI_SIDE -> RelatorSpeechBucket.ASSIST_GAP_UNI
        RelatorAssistKind.GAP_SUPERSET -> RelatorSpeechBucket.ASSIST_GAP_SUPERSET
        RelatorAssistKind.GAP_EXERCISE -> RelatorSpeechBucket.ASSIST_GAP_EXERCISE
        RelatorAssistKind.TIME -> RelatorSpeechBucket.ASSIST_TIME
        RelatorAssistKind.MOBILITY -> RelatorSpeechBucket.ASSIST_MOBILITY
    }

internal val RelatorSpeechBucket.isAssist: Boolean
    get() = when (this) {
        RelatorSpeechBucket.ASSIST_GAP_SET,
        RelatorSpeechBucket.ASSIST_GAP_UNI,
        RelatorSpeechBucket.ASSIST_GAP_SUPERSET,
        RelatorSpeechBucket.ASSIST_GAP_EXERCISE,
        RelatorSpeechBucket.ASSIST_TIME,
        RelatorSpeechBucket.ASSIST_MOBILITY,
        -> true
        else -> false
    }

internal fun pickRelatorAssistOffer(ctx: RelatorAssistContext): RelatorAssistOffer? {
    if (ctx.phase == RelatorPhase.HIDDEN || ctx.currentExerciseId.isBlank()) return null
    if (ctx.phase == RelatorPhase.WORKING || ctx.phase == RelatorPhase.REST) {
        gapSetOffer(ctx)?.let { return it }
        gapUniSideOffer(ctx)?.let { return it }
        gapSupersetOffer(ctx)?.let { return it }
        gapExerciseOffer(ctx)?.let { return it }
        timeCrunchOffer(ctx)?.let { return it }
    }
    if (ctx.phase == RelatorPhase.MOBILITY) {
        mobilityOffer(ctx)?.let { return it }
    }
    return null
}

internal fun relatorSetResolved(
    exercise: RelatorAssistExercise,
    setIndex: Int,
    completedSetKeys: Set<String>,
    omittedSetKeys: Set<String>,
): Boolean {
    if (setIndex !in 0 until exercise.setCount) return true
    if (WorkoutStepRules.omittedSetKey(exercise.id, setIndex) in omittedSetKeys) return true
    val keys = if (exercise.unilateral) {
        listOf(
            WorkoutStepRules.workingStepKey(exercise.id, setIndex, "left"),
            WorkoutStepRules.workingStepKey(exercise.id, setIndex, "right"),
        )
    } else {
        listOf(WorkoutStepRules.workingStepKey(exercise.id, setIndex))
    }
    return keys.all { it in completedSetKeys }
}

internal fun suggestedMobilityForFamily(family: RelatorFamily): Pair<MobilityExercise, String>? {
    val preferredId = when (family) {
        RelatorFamily.SQUAT -> "mob_dead_bug_reach"
        RelatorFamily.PRESS -> "mob_wall_slides"
        RelatorFamily.PULL -> "mob_thread_needle"
        RelatorFamily.HINGE -> "mob_90_90_breathing"
        RelatorFamily.ISOLATION, RelatorFamily.OTHER -> return null
    }
    val preferredRegions = when (family) {
        RelatorFamily.SQUAT -> setOf("trunk")
        RelatorFamily.PRESS -> setOf("shoulder")
        RelatorFamily.PULL -> setOf("upper", "scapula")
        RelatorFamily.HINGE -> setOf("pelvis", "hip")
        RelatorFamily.ISOLATION, RelatorFamily.OTHER -> return null
    }
    val drill = MobilityExerciseCatalog.findById(preferredId)
        ?: MobilityExerciseCatalog.getAllMobilityExercises().firstOrNull { it.bodyRegion in preferredRegions }
        ?: return null
    return drill to shortMobilityCatalogName(drill)
}

internal fun shortMobilityCatalogName(drill: MobilityExercise): String {
    val name = drill.name.trim()
    val cut = name.split(Regex("\\s+con\\s+", RegexOption.IGNORE_CASE)).first().trim()
    val base = cut.ifBlank { name }
    if (base.length <= 28) return base
    val glue = setOf("en", "de", "con", "a", "la", "el", "del", "al", "y")
    val words = base.split(Regex("\\s+")).filter { it.isNotBlank() }
    val kept = mutableListOf<String>()
    for (word in words) {
        val next = (kept + word).joinToString(" ")
        if (next.length > 28) break
        kept += word
    }
    while (kept.size > 1 && kept.last().lowercase() in glue) kept.removeAt(kept.lastIndex)
    return kept.joinToString(" ").ifBlank { base.take(28).trimEnd() }
}

internal data class RelatorTextSpan(
    val start: Int,
    val endExclusive: Int,
    val action: RelatorAssistAction,
)

internal fun RelatorAssistAction.clickableSpan(): String = span.ifBlank { label }.trim()

internal fun relatorActionSpans(text: String, actions: List<RelatorAssistAction>): List<RelatorTextSpan> {
    if (text.isBlank() || actions.isEmpty()) return emptyList()
    val used = BooleanArray(text.length)
    val found = mutableListOf<RelatorTextSpan>()
    for (action in actions.sortedByDescending { it.clickableSpan().length }) {
        val needle = action.clickableSpan()
        if (needle.isEmpty()) continue
        val idx = text.indexOf(needle, ignoreCase = true)
        if (idx < 0) continue
        val end = idx + needle.length
        if ((idx until end).any { it in used.indices && used[it] }) continue
        for (i in idx until end) used[i] = true
        found += RelatorTextSpan(idx, end, action)
    }
    return found.sortedBy { it.start }
}

internal sealed class RelatorInlinePiece {
    data class Copy(val text: String) : RelatorInlinePiece()
    data class Action(val action: RelatorAssistAction, val label: String) : RelatorInlinePiece()
}

internal fun relatorInlinePieces(text: String, actions: List<RelatorAssistAction>): List<RelatorInlinePiece> {
    val spans = relatorActionSpans(text, actions)
    if (text.isBlank()) return emptyList()
    if (spans.isEmpty()) return listOf(RelatorInlinePiece.Copy(text))
    val pieces = mutableListOf<RelatorInlinePiece>()
    var cursor = 0
    spans.forEach { span ->
        if (span.start > cursor) {
            pieces += RelatorInlinePiece.Copy(text.substring(cursor, span.start))
        }
        pieces += RelatorInlinePiece.Action(span.action, text.substring(span.start, span.endExclusive))
        cursor = span.endExclusive
    }
    if (cursor < text.length) pieces += RelatorInlinePiece.Copy(text.substring(cursor))
    return pieces.filterNot { piece -> piece is RelatorInlinePiece.Copy && piece.text.isEmpty() }
}

private fun parentMembers(ctx: RelatorAssistContext): List<RelatorAssistExercise> {
    val current = ctx.sessionExercises.firstOrNull { it.id == ctx.currentExerciseId } ?: return emptyList()
    val groupId = current.groupId
    return if (!groupId.isNullOrBlank()) {
        val members = ctx.sessionExercises.filter { it.groupId == groupId && !it.isCardio }
        if (members.size > 1) members else listOf(current)
    } else {
        listOf(current)
    }
}

private fun gapSetOffer(ctx: RelatorAssistContext): RelatorAssistOffer? {
    val members = parentMembers(ctx)
    if (members.isEmpty()) return null
    val currentSet = ctx.currentSetIndex
    for (setIdx in 0 until currentSet) {
        for (member in members) {
            if (relatorSetResolved(member, setIdx, ctx.completedSetKeys, ctx.omittedSetKeys)) continue
            val n = setIdx + 1
            val side = if (member.unilateral) {
                val leftKey = WorkoutStepRules.workingStepKey(member.id, setIdx, "left")
                if (leftKey !in ctx.completedSetKeys) "left" else "right"
            } else {
                ""
            }
            return RelatorAssistOffer(
                kind = RelatorAssistKind.GAP_SET,
                text = "No has registrado la serie $n. Vuelve, o márcala omitida.",
                actions = listOf(
                    RelatorAssistAction(
                        kind = RelatorAssistActionKind.JUMP_TO_SET,
                        label = "Vuelve",
                        exerciseId = member.id,
                        setIndex = setIdx,
                        side = side,
                        span = "Vuelve",
                    ),
                    RelatorAssistAction(
                        kind = RelatorAssistActionKind.OMIT_SET,
                        label = "márcala omitida",
                        exerciseId = member.id,
                        setIndex = setIdx,
                        span = "márcala omitida",
                    ),
                ),
                stickyKey = "gap-set:${member.id}:$setIdx",
            )
        }
    }
    return null
}

private fun gapUniSideOffer(ctx: RelatorAssistContext): RelatorAssistOffer? {
    val current = ctx.sessionExercises.firstOrNull { it.id == ctx.currentExerciseId } ?: return null
    if (!current.unilateral) return null
    val setIdx = ctx.currentSetIndex
    if (relatorSetResolved(current, setIdx, ctx.completedSetKeys, ctx.omittedSetKeys)) return null
    val leftKey = WorkoutStepRules.workingStepKey(current.id, setIdx, "left")
    val rightKey = WorkoutStepRules.workingStepKey(current.id, setIdx, "right")
    val leftDone = leftKey in ctx.completedSetKeys
    val rightDone = rightKey in ctx.completedSetKeys
    val active = ctx.activeSide?.lowercase()
    val missingSide = when {
        active == "right" && !leftDone -> "left"
        active == "left" && !rightDone && leftDone -> "right"
        active == "right" && leftDone && !rightDone -> return null
        else -> return null
    }
    val label = if (missingSide == "left") "izquierdo" else "derecho"
    return RelatorAssistOffer(
        kind = RelatorAssistKind.GAP_UNI_SIDE,
        text = "Falta el lado $label de esta serie. Ve al lado $label, o márcalo omitido.",
        actions = listOf(
            RelatorAssistAction(
                kind = RelatorAssistActionKind.JUMP_TO_SIDE,
                label = "Ve al lado $label",
                exerciseId = current.id,
                setIndex = setIdx,
                side = missingSide,
                span = "Ve al lado $label",
            ),
            RelatorAssistAction(
                kind = RelatorAssistActionKind.OMIT_SET,
                label = "márcalo omitido",
                exerciseId = current.id,
                setIndex = setIdx,
                span = "márcalo omitido",
            ),
        ),
        stickyKey = "gap-uni:${current.id}:$setIdx:$missingSide",
    )
}

private fun gapSupersetOffer(ctx: RelatorAssistContext): RelatorAssistOffer? {
    val current = ctx.sessionExercises.firstOrNull { it.id == ctx.currentExerciseId } ?: return null
    val members = parentMembers(ctx)
    if (members.size < 2) return null
    val round = ctx.currentSetIndex
    val partner = members.firstOrNull { member ->
        member.id != current.id &&
            !relatorSetResolved(member, round, ctx.completedSetKeys, ctx.omittedSetKeys)
    } ?: return null
    val shortName = shortAssistName(partner.name)
    return RelatorAssistOffer(
        kind = RelatorAssistKind.GAP_SUPERSET,
        text = "En esta ronda falta $shortName. Ve a $shortName, o márcala omitida.",
        actions = listOf(
            RelatorAssistAction(
                kind = RelatorAssistActionKind.JUMP_TO_SET,
                label = "Ve a $shortName",
                exerciseId = partner.id,
                setIndex = round,
                span = "Ve a $shortName",
            ),
            RelatorAssistAction(
                kind = RelatorAssistActionKind.OMIT_SET,
                label = "márcala omitida",
                exerciseId = partner.id,
                setIndex = round,
                span = "márcala omitida",
            ),
        ),
        stickyKey = "gap-ss:${partner.id}:$round",
    )
}

private fun gapExerciseOffer(ctx: RelatorAssistContext): RelatorAssistOffer? {
    if (ctx.currentExerciseIndex <= 0) return null
    val previous = (ctx.currentExerciseIndex - 1 downTo 0)
        .map { ctx.sessionExercises[it] }
        .firstOrNull { !it.isCardio }
        ?: return null
    val skipped = previous.id in ctx.skippedExerciseIds
    val hasIncomplete = previous.setCount > 0 &&
        (0 until previous.setCount).any { setIdx ->
            !relatorSetResolved(previous, setIdx, ctx.completedSetKeys, ctx.omittedSetKeys)
        }
    if (!hasIncomplete && !skipped) return null
    val shortName = shortAssistName(previous.name)
    val text = if (skipped) {
        "Saltaste $shortName. Recupéralo ahora, o muévelo al final."
    } else {
        "Quedó pendiente $shortName. Vuelve, o muévelo al final."
    }
    return RelatorAssistOffer(
        kind = RelatorAssistKind.GAP_EXERCISE,
        text = text,
        actions = listOf(
            RelatorAssistAction(
                kind = RelatorAssistActionKind.JUMP_TO_EXERCISE,
                label = if (skipped) "Recupéralo ahora" else "Vuelve",
                exerciseId = previous.id,
                span = if (skipped) "Recupéralo ahora" else "Vuelve",
            ),
            RelatorAssistAction(
                kind = RelatorAssistActionKind.MOVE_EXERCISE_END,
                label = "muévelo al final",
                exerciseId = previous.id,
                span = "muévelo al final",
            ),
        ),
        stickyKey = "gap-ex:${previous.id}",
    )
}

private fun timeCrunchOffer(ctx: RelatorAssistContext): RelatorAssistOffer? {
    val remaining = ctx.remainingSeconds ?: return null
    if (remaining <= 0 || remaining > RELATOR_TIME_CRUNCH_SECONDS) return null
    val leftover = remainingIncompleteSetsFromHere(ctx)
    if (leftover < RELATOR_TIME_CRUNCH_MIN_SETS) return null
    val minutes = (remaining / 60).coerceAtLeast(1)
    return RelatorAssistOffer(
        kind = RelatorAssistKind.TIME,
        text = "Te quedan $minutes min. Convierte a dropsets, o reduce series a la mitad.",
        actions = listOf(
            RelatorAssistAction(
                kind = RelatorAssistActionKind.CONVERT_DROPSETS,
                label = "Convierte a dropsets",
                span = "Convierte a dropsets",
            ),
            RelatorAssistAction(
                kind = RelatorAssistActionKind.HALVE_SETS,
                label = "reduce series a la mitad",
                span = "reduce series a la mitad",
            ),
        ),
        stickyKey = "time:$leftover",
    )
}

private fun mobilityOffer(ctx: RelatorAssistContext): RelatorAssistOffer? {
    val current = ctx.sessionExercises.firstOrNull { it.id == ctx.currentExerciseId } ?: return null
    val (drill, span) = suggestedMobilityForFamily(ctx.family) ?: return null
    if (alreadyHasMobility(current, drill)) return null
    val why = when (ctx.family) {
        RelatorFamily.SQUAT -> "para el core"
        RelatorFamily.PRESS -> "para el manguito"
        RelatorFamily.PULL -> "para la torácica"
        RelatorFamily.HINGE -> "para la pelvis"
        else -> "para calentar"
    }
    return RelatorAssistOffer(
        kind = RelatorAssistKind.MOBILITY,
        text = "Añade $span a la movilidad de {ex}, $why.",
        actions = listOf(
            RelatorAssistAction(
                kind = RelatorAssistActionKind.ADD_MOBILITY,
                label = span,
                exerciseId = current.id,
                mobilityId = drill.id,
                span = span,
            ),
        ),
        stickyKey = "mob:${current.id}:${drill.id}",
    )
}

private fun remainingIncompleteSetsFromHere(ctx: RelatorAssistContext): Int {
    var count = 0
    ctx.sessionExercises.forEachIndexed { index, exercise ->
        if (exercise.isCardio) return@forEachIndexed
        if (exercise.id in ctx.skippedExerciseIds) return@forEachIndexed
        if (index < ctx.currentExerciseIndex) return@forEachIndexed
        val start = if (exercise.id == ctx.currentExerciseId) ctx.currentSetIndex else 0
        for (setIdx in start until exercise.setCount) {
            if (!relatorSetResolved(exercise, setIdx, ctx.completedSetKeys, ctx.omittedSetKeys)) {
                count++
            }
        }
    }
    return count
}

private fun alreadyHasMobility(exercise: RelatorAssistExercise, drill: MobilityExercise): Boolean {
    val hay = exercise.mobilityLabels.joinToString(" ").lowercase()
    if (hay.isBlank()) return false
    if (drill.id.lowercase() in hay) return true
    if (drill.name.lowercase() in hay) return true
    return drill.aliases.any { alias -> alias.lowercase() in hay }
}

private fun shortAssistName(raw: String): String {
    val first = raw.split(" · ").first().trim()
    val words = first.split(Regex("\\s+")).filter { it.isNotBlank() }
    val compact = if (words.size <= 2) words.joinToString(" ") else words.take(2).joinToString(" ")
    return if (compact.length <= 16) compact else compact.take(15).trimEnd() + "…"
}
