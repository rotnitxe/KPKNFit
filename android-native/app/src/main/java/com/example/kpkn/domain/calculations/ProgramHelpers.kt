package com.example.kpkn.domain.calculations

import com.example.kpkn.data.models.Program

// ─── ProgramHelpers ───────────────────────────────────────────────────────────
// 1:1 port of utils/programHelpers.ts

/** Returns the absolute week index (0-based) across all blocks and mesocycles. */
fun getAbsoluteWeekIndex(program: Program, weekId: String): Int {
    var idx = 0
    for (macro in program.macrocycles) {
        for (block in macro.blocks) {
            for (meso in block.mesocycles) {
                for (week in meso.weeks) {
                    if (week.id == weekId) return idx
                    idx++
                }
            }
        }
    }
    return 0
}

/** Total weeks across the full program. */
fun getTotalWeeks(program: Program): Int =
    program.macrocycles.sumOf { macro ->
        macro.blocks.sumOf { block ->
            block.mesocycles.sumOf { meso -> meso.weeks.size }
        }
    }

/** Day name (Monday=1 … Sunday=7). */
fun getDayName(dayOfWeek: Int): String = when (dayOfWeek) {
    1 -> "Lunes"
    2 -> "Martes"
    3 -> "Miércoles"
    4 -> "Jueves"
    5 -> "Viernes"
    6 -> "Sábado"
    7 -> "Domingo"
    else -> "Día $dayOfWeek"
}

/** Short day name. */
fun getShortDayName(dayOfWeek: Int): String = when (dayOfWeek) {
    1 -> "Lun"
    2 -> "Mar"
    3 -> "Mié"
    4 -> "Jue"
    5 -> "Vie"
    6 -> "Sáb"
    7 -> "Dom"
    else -> "D$dayOfWeek"
}

/** Current day of week as 1–7 (Mon–Sun), matching PWA convention. */
fun getCurrentDayOfWeek(): Int {
    val jsDayMap = intArrayOf(7, 1, 2, 3, 4, 5, 6) // JS 0=Sun → 7; 1=Mon → 1
    val cal = java.util.Calendar.getInstance()
    val jsDay = cal.get(java.util.Calendar.DAY_OF_WEEK) - 1 // Calendar.SUNDAY=1 → 0
    return jsDayMap[jsDay]
}

/** Today's date string as ISO yyyy-MM-dd. */
fun getTodayDateString(): String {
    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
    return sdf.format(java.util.Date())
}

/** Counts total exercises in a session (handles parts). */
fun getSessionExerciseCount(session: com.example.kpkn.data.models.Session): Int {
    if (session.parts.isNotEmpty()) {
        return session.parts.sumOf { it.exercises.size }
    }
    return session.exercises.size
}
