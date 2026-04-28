package com.example.kpkn.data.sessions

import com.example.kpkn.data.models.Session
import com.example.kpkn.data.splits.Difficulty
import kotlinx.serialization.Serializable

// ─── Classification ───────────────────────────────────────────────────────────

@Serializable
enum class SessionTemplateTag {
    // Objective
    FUERZA,
    HIPERTROFIA,
    POTENCIA,
    RESISTENCIA,
    RECUPERACION,
    // Split type
    TORSO,
    PIERNA,
    EMPUJE,
    TIRON,
    CUERPO_COMPLETO,
    // Primary muscle group
    PECHO,
    ESPALDA,
    HOMBROS,
    BRAZOS,
    GLUTEOS,
    CUADRICEPS,
    ISQUIOTIBIALES,
    GEMELOS,
    CORE,
    // Powerlifting
    POWERLIFTING,
    SENTADILLA,
    PESO_MUERTO,
    BANCA,
    // Volume / intensity modifiers
    MINIMALISTA,
    ALTO_VOLUMEN,
    ALTA_FRECUENCIA,
    DEPORTIVO,
}

@Serializable
enum class SessionTemplateSourceType {
    /** Predefined by the app, read-only, cannot be deleted. */
    SYSTEM,
    /** Created by the user, editable and deletable. */
    USER,
}

// ─── Core model ───────────────────────────────────────────────────────────────

/**
 * A reusable session blueprint that can be applied to a session in the editor.
 *
 * System templates live in [SESSION_TEMPLATES_SYSTEM] and are never persisted to
 * the database. User templates are persisted via [SessionTemplateRepository].
 *
 * When applied, all internal IDs (Session, SessionPart, Exercise, ExerciseSet)
 * are regenerated via [com.example.kpkn.domain.templates.SessionTemplateEngine]
 * to avoid collisions with existing data.
 */
@Serializable
data class SessionTemplate(
    val id: String,
    val sourceType: SessionTemplateSourceType,
    val name: String,
    val description: String,
    val emoji: String = "💪",
    val tags: List<SessionTemplateTag> = emptyList(),
    val difficulty: Difficulty = Difficulty.INTERMEDIO,
    /** Approximate session duration in minutes, shown as a hint in the picker. */
    val estimatedDurationMinutes: Int? = null,
    /** Cached count for display purposes – not authoritative. */
    val exerciseCount: Int = 0,
    val partCount: Int = 0,
    /** Human-readable summary of main muscle groups (e.g. "Pecho · Hombros · Tríceps"). */
    val muscleGroupsSummary: String = "",
    /** Full session payload. IDs are re-generated on apply. */
    val session: Session,
    val sortOrder: Int = 0,
    val isArchived: Boolean = false,
    /** ISO-8601 string, null for SYSTEM templates. */
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

// ─── Application logic ────────────────────────────────────────────────────────

/**
 * Defines how a template is merged with the current session in the editor.
 *
 * - [REPLACE]: Replaces exercises, parts and warmup entirely. The session identity
 *   (id, name, description, background, etc.) is preserved.
 * - [APPEND]: Template parts and loose exercises are added at the end of the
 *   existing content, preserving the current structure.
 */
enum class SessionTemplateApplyMode {
    REPLACE,
    APPEND,
}

/**
 * Held in [com.example.kpkn.screens.sessioneditor.SessionEditorUiState] while
 * the user is deciding how to merge a template into a non-empty session.
 */
data class SessionTemplateApplyDecision(
    val template: SessionTemplate,
    /** Initial suggestion shown by default in the confirmation dialog. */
    val suggestedMode: SessionTemplateApplyMode = SessionTemplateApplyMode.REPLACE,
)
