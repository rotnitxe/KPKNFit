package com.example.kpkn.domain.entitlements

enum class PlanLevel { FREE, PRO }

/** Compatibility for the independently implemented program import and activation flows. */
object CapabilityCatalog {
    const val FREE_MAX_SESSIONS_PER_WEEK = 5
    const val FREE_MAX_PROGRAM_WEEKS = 1
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
