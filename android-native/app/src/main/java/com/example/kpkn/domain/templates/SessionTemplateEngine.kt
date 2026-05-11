package com.example.kpkn.domain.templates

import com.example.kpkn.data.models.*
import com.example.kpkn.data.sessions.SessionTemplate
import com.example.kpkn.data.sessions.SessionTemplateApplyMode
import java.util.UUID

/**
 * Pure, stateless engine for applying session templates.
 *
 * Key guarantee: every internal ID (Session, SessionPart, Exercise, ExerciseSet,
 * WarmupSetDefinition, WarmupExercise) is regenerated with a fresh UUID so that
 * applied sessions never share IDs with existing program data, workout logs, or
 * other applied templates.
 *
 * Superset links *within* the cloned content are preserved by remapping the
 * original supersetId strings to new ones.  Cross-session superset links are
 * stripped because they cannot be valid after cloning.
 */
object SessionTemplateEngine {

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * Applies [template] to [targetSession] according to [mode].
     *
 * - [SessionTemplateApplyMode.REPLACE]: replaces exercises, parts, warmup,
 *   name, and description.  The session's id and background image are
 *   preserved; name and description come from the template.
     * - [SessionTemplateApplyMode.APPEND]: appends template parts and loose
     *   exercises at the end of the existing content.
     */
    fun applyTemplate(
        template: SessionTemplate,
        targetSession: Session,
        mode: SessionTemplateApplyMode,
    ): Session = when (mode) {
        SessionTemplateApplyMode.REPLACE -> applyReplace(template, targetSession)
        SessionTemplateApplyMode.APPEND -> applyAppend(template, targetSession)
    }

    /**
     * Returns `true` when [session] contains at least one exercise (loose or
     * inside a part), which is used to decide whether to prompt the user for
     * a merge mode before applying a template.
     */
    fun sessionHasContent(session: Session): Boolean =
        session.exercises.isNotEmpty() || session.parts.any { it.exercises.isNotEmpty() }

    /**
     * Clones the entire content of [source] – exercises, parts, and warmup –
     * with fresh UUIDs.  The returned [Session] has a new id; all other identity
     * and metadata fields are copied from [source].
     */
    fun cloneSessionContent(source: Session): Session {
        val supersetIdMap = mutableMapOf<String, String>()
        val clonedParts = source.parts.map { part ->
            part.copy(
                id = UUID.randomUUID().toString(),
                exercises = part.exercises.map { cloneExercise(it, supersetIdMap) },
            )
        }
        val clonedExercises = source.exercises.map { cloneExercise(it, supersetIdMap) }
        val clonedWarmup = source.warmup.map { it.copy(id = UUID.randomUUID().toString()) }
        return source.copy(
            id = UUID.randomUUID().toString(),
            parts = clonedParts,
            exercises = clonedExercises,
            warmup = clonedWarmup,
        )
    }

    // ─── Internal helpers ─────────────────────────────────────────────────────

    private fun applyReplace(template: SessionTemplate, target: Session): Session {
        val cloned = cloneSessionContent(template.session)
        return target.copy(
            name = cloned.name,
            description = cloned.description,
            exercises = cloned.exercises,
            parts = cloned.parts,
            warmup = cloned.warmup,
        )
    }

    private fun applyAppend(template: SessionTemplate, target: Session): Session {
        val cloned = cloneSessionContent(template.session)
        return target.copy(
            name = cloned.name,
            description = cloned.description,
            parts = target.parts + cloned.parts,
            exercises = target.exercises + cloned.exercises,
            warmup = target.warmup + cloned.warmup,
        )
    }

    /**
     * Clones a single exercise with a fresh id and fresh set/warmupSet ids.
     *
     * [supersetIdMap] is shared across a single cloning call so that two
     * exercises that were linked in the same superset remain linked in the clone,
     * while all superset IDs from outside the cloned scope are stripped.
     */
    private fun cloneExercise(
        exercise: Exercise,
        supersetIdMap: MutableMap<String, String>,
    ): Exercise {
        val newSupersetId = exercise.supersetId?.let {
            supersetIdMap.getOrPut(it) { UUID.randomUUID().toString() }
        }
        return exercise.copy(
            id = UUID.randomUUID().toString(),
            supersetId = newSupersetId,
            sets = exercise.sets.map { it.copy(id = UUID.randomUUID().toString()) },
            warmupSets = exercise.warmupSets.map { it.copy(id = UUID.randomUUID().toString()) },
            // Strip execution/performance state that belongs to actual workout logs
            consolidatedWeight = null,
            reference1RM = null,
            calculated1RM = null,
            prFor1RM = null,
        )
    }
}
