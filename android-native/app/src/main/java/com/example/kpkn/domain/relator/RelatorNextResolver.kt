package com.example.kpkn.domain.relator

/** Lo que el relator dice que viene después de la serie que acaba de cerrarse (LR1g). */
data class RelatorNextTarget(
    val setNumber: Int? = null,
    val setTotal: Int? = null,
    val exerciseName: String? = null,
    val kg: Double? = null,
    val reps: Int? = null,
)

/**
 * «Siguiente» del descanso (LR1g). Puro:
 * - en superserie, la pareja;
 * - mientras queden series del mismo ejercicio, la siguiente de ese ejercicio;
 * - el ejercicio siguiente solo cuando el actual ya no tiene series.
 * [upcomingIndex] es la serie que toca (0-based), tal como la deja el ViewModel durante el descanso.
 */
object RelatorNextResolver {
    fun resolve(
        upcomingIndex: Int,
        setsTotal: Int,
        setKg: Double?,
        setReps: Int?,
        nextExerciseName: String?,
        nextExerciseKg: Double?,
        supersetPartnerName: String? = null,
        supersetPartnerKg: Double? = null,
    ): RelatorNextTarget {
        val partner = supersetPartnerName?.trim()?.takeIf { it.isNotEmpty() }
        if (partner != null) return RelatorNextTarget(exerciseName = partner, kg = supersetPartnerKg)
        if (upcomingIndex in 0 until setsTotal) {
            return RelatorNextTarget(
                setNumber = upcomingIndex + 1,
                setTotal = setsTotal,
                kg = setKg,
                reps = setReps,
            )
        }
        return RelatorNextTarget(
            exerciseName = nextExerciseName?.trim()?.takeIf { it.isNotEmpty() },
            kg = nextExerciseKg,
        )
    }
}
