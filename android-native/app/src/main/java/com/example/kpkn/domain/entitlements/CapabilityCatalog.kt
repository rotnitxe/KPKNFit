package com.example.kpkn.domain.entitlements

/** Commercial policy approved by the owner. Reading and controlling existing data is always available. */
enum class Capability {
    WEEKLY_TRAINING, VOICE, DATA_SYNC, ENERGY_RING,
    ADVANCED_PROGRAMS, MUSCLE_RING, SPINAL_RING, NUTRITION, WORKOUT_MEDIA,
    READ_HISTORY, EXPORT_DATA, DELETE_DATA,
}

enum class PlanLevel { FREE, PRO }

object CapabilityCatalog {
    const val FREE_MAX_SESSIONS_PER_WEEK = 5
    const val FREE_MAX_PROGRAM_WEEKS = 1
    const val TRIAL_DAYS = 14
    private val free = setOf(Capability.WEEKLY_TRAINING, Capability.VOICE, Capability.DATA_SYNC,
        Capability.ENERGY_RING, Capability.READ_HISTORY, Capability.EXPORT_DATA, Capability.DELETE_DATA)
    fun permits(capability: Capability, level: PlanLevel): Boolean = capability in free || level == PlanLevel.PRO

    /** Validate creation/structural edits; this must never be used to refuse reading old programs. */
    fun validateProgramStructure(level: PlanLevel, weeks: Int, sessionsPerWeek: List<Int>): String? {
        require(weeks >= 1 && sessionsPerWeek.size == weeks && sessionsPerWeek.all { it >= 0 })
        if (level == PlanLevel.PRO) return null
        return when {
            weeks > FREE_MAX_PROGRAM_WEEKS -> "advanced_program_requires_pro"
            sessionsPerWeek.any { it > FREE_MAX_SESSIONS_PER_WEEK } -> "free_weekly_session_limit"
            else -> null
        }
    }
}
