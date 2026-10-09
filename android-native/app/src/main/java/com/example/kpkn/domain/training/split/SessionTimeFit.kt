package com.example.kpkn.domain.training.split

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.protocols.SlotRole
import com.example.kpkn.domain.training.SessionDurationEstimator

/**
 * Ajuste al tiempo pedido de un día ya armado. Si la sesión pasa [TOLERANCE] veces los minutos pedidos:
 * 1. quita series a sus accesorios y aislamientos, de una en una, empezando por el de menor prioridad (el último del día)
 *    y sin bajar de [MIN_SETS] series;
 * 2. si aún no cabe, fusiona dos aislamientos del mismo músculo primario en uno, que recibe una serie más.
 * Los principales, los compuestos, las cargas pesadas y los ejercicios de superserie no se tocan. Si sobra tiempo, no se
 * añade nada. Cada cambio queda en [SetTrim] y [ExerciseMerge] para el informe.
 */
internal object SessionTimeFit {

    /** Un día puede pasar el tiempo pedido hasta este factor sin recortarse. */
    const val TOLERANCE = 1.10

    /** Ningún accesorio baja de estas series al recortar. */
    const val MIN_SETS = 2

    /** Roles que nunca se recortan: el principal, la velocidad, el segundo levantamiento y la técnica. */
    private val PROTECTED_ROLES = setOf(SlotRole.T1_MAIN, SlotRole.SPEED, SlotRole.T2_SUPPLEMENTAL, SlotRole.TECHNIQUE)

    /** El día ajustado y lo que se le quitó. */
    class Outcome(val session: Session, val trims: List<SetTrim>, val merges: List<ExerciseMerge>)

    /** ¿Cabe un día de [minutes] en el tiempo pedido, con la tolerancia? */
    fun fits(minutes: Int, targetMinutes: Int): Boolean = minutes <= targetMinutes * TOLERANCE

    fun fit(session: Session, targetMinutes: Int, weekIndex: Int, resolver: ExerciseTraitResolver): Outcome {
        if (fits(minutesOf(session), targetMinutes)) return Outcome(session, emptyList(), emptyList())
        val names = session.allExercises().associate { it.id to it.name }
        var current = session
        // Series quitadas por ejercicio (por id) y fusiones hechas, en el orden en que se hicieron.
        val removed = LinkedHashMap<String, Int>()
        val merges = mutableListOf<ExerciseMerge>()

        while (!fits(minutesOf(current), targetMinutes)) {
            val victim = candidates(current, resolver).lastOrNull { it.exercise.sets.size > MIN_SETS } ?: break
            val victimId = victim.exercise.id
            current = mapExercises(current) { exercise ->
                if (exercise.id == victimId) exercise.copy(sets = exercise.sets.dropLast(1)) else exercise
            }
            removed[victimId] = (removed[victimId] ?: 0) + 1
        }
        while (!fits(minutesOf(current), targetMinutes)) {
            val (keep, drop) = mergeablePair(current, resolver) ?: break
            current = mergedInto(current, keep = keep, drop = drop)
            merges += ExerciseMerge(
                weekIndex = weekIndex,
                dayTitle = session.name,
                removedExerciseId = drop.id,
                removedName = drop.name,
                keptExerciseId = keep.id,
                keptName = keep.name,
            )
            // Lo que se le quitó a un ejercicio que luego se fusiona ya no existe: solo cuenta la fusión.
            removed.remove(drop.id)
        }
        val trims = removed.map { (id, count) -> SetTrim(weekIndex, session.name, id, names[id].orEmpty(), count) }
        return Outcome(current, trims, merges)
    }

    private fun minutesOf(session: Session): Int = SessionDurationEstimator.estimate(session).totalMinutes

    private class Candidate(val exercise: Exercise, val traits: ExerciseTraits)

    /** Accesorios y aislamientos del día, en orden de sesión: el último es el de menor prioridad. */
    private fun candidates(session: Session, resolver: ExerciseTraitResolver): List<Candidate> =
        session.allExercises().distinctBy { it.id }.mapNotNull { exercise ->
            val role = exercise.slotRole
            if (exercise.cardioDetails != null || exercise.sets.isEmpty()) return@mapNotNull null
            if (role != null && role in PROTECTED_ROLES) return@mapNotNull null
            if (exercise.supersetId != null || exercise.supersetGroupRef != null) return@mapNotNull null
            val traits = resolver.traitsOf(exercise) ?: return@mapNotNull null
            val minReps = exercise.sets.mapNotNull { it.targetRepsRange?.max ?: it.targetReps }.minOrNull()
            if (WeekGather.heavyPatternsOf(listOf(traits), minReps).isNotEmpty()) return@mapNotNull null
            // Un compuesto solo se recorta si su rol dice que es accesorio; sin ese rol se deja como está.
            if (traits.isCompound && role != SlotRole.T3_ACCESSORY) return@mapNotNull null
            Candidate(exercise, traits)
        }

    /** Dos aislamientos del mismo músculo primario: se queda el que va antes en el día y se fusiona el que va después. */
    private fun mergeablePair(session: Session, resolver: ExerciseTraitResolver): Pair<Exercise, Exercise>? {
        val isolations = candidates(session, resolver).filter { !it.traits.isCompound }
        for (j in isolations.indices.reversed()) {
            val muscle = isolations[j].traits.primaryMuscles.firstOrNull() ?: continue
            for (i in 0 until j) {
                if (isolations[i].traits.primaryMuscles.firstOrNull() == muscle) {
                    return isolations[i].exercise to isolations[j].exercise
                }
            }
        }
        return null
    }

    /** Quita [drop] del día y da a [keep] una serie más (una copia de su última serie). */
    private fun mergedInto(session: Session, keep: Exercise, drop: Exercise): Session {
        val extra = keep.sets.last().copy(id = "${keep.id}-m${keep.sets.size + 1}")
        return mapExercises(session) { exercise ->
            when (exercise.id) {
                drop.id -> null
                keep.id -> exercise.copy(sets = exercise.sets + extra)
                else -> exercise
            }
        }
    }

    /** La sesión con cada ejercicio transformado (null lo quita), tanto en la lista suelta como en sus partes. */
    private fun mapExercises(session: Session, transform: (Exercise) -> Exercise?): Session = session.copy(
        exercises = session.exercises.mapNotNull(transform),
        parts = session.parts.map { part -> part.copy(exercises = part.exercises.mapNotNull(transform)) },
    )
}
