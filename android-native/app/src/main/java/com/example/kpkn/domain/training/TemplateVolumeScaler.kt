package com.example.kpkn.domain.training

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.VolumeRecommendation
import com.example.kpkn.data.models.isCardioPart
import com.example.kpkn.data.programs.KpknMuscleGroup
import com.example.kpkn.data.programs.VolumeLandmarks
import kotlin.math.ceil

/**
 * Escala series de una semana recién aplicada hasta el MAV calibrado.
 *
 * Kotlin puro, sin `android.*`. Nunca añade/quita ejercicios, nunca inventa
 * IDs de catálogo, nunca cambia la selección de plantillas: solo clona sets
 * del mismo ejercicio con UUID nuevo vía [IdProvider].
 *
 * Topes duros por sesión: H7 (12 directas/músculo), H6 (30 efectivas),
 * MAX_SESSION_MINUTES (100). Si una sesión toparía, derrama a otra sesión de
 * la misma semana con ese músculo; si no cabe, para y reporta residuo.
 */
object TemplateVolumeScaler {

    data class MuscleTargets(
        val muscleName: String,
        val floor: Int,
        val target: Int,
        val ceiling: Int,
    )

    data class VolumeScaleResult(
        val sessions: List<Session>,
        val addedSetsByMuscle: Map<String, Int>,
        val residualDeficitByMuscle: Map<String, Double>,
        val cappedSessions: List<String>,
    )

    // Weekly tolerance is collateral work above MAV, never extra direct targets
    // and never permission to cross MRV. Divided for the isolated-session API.
    internal const val WEEKLY_COLLATERAL_TOLERANCE = 2.0
    private const val EPSILON = 0.000001

    // Only collateral indirect work may use this explicit, bounded tolerance.
    const val INDIRECT_MRV_TOLERANCE = 2.0

    fun targetsFor(
        recommendations: List<VolumeRecommendation>,
        @Suppress("UNUSED_PARAMETER") currentWeekly: Map<String, Double> = emptyMap(),
    ): Map<String, MuscleTargets> {
        val personalized = recommendations.groupBy {
            VolumeCalculator.normalizeCanonicalMuscleGroup(it.muscleGroup)
        }
        return VolumeCalculator.standardVolumeMuscles.mapNotNull { canonical ->
            val landmarks = VolumeLandmarks.byGroup.entries
                .filter { landmarkKeyMatches(it.key, canonical) }.map { it.value }
            val recs = personalized[canonical].orEmpty()
            if (landmarks.isEmpty() && recs.isEmpty()) return@mapNotNull null
            // Canonical aliases overlap, so never sum their prescriptions.
            val floor = recs.maxOfOrNull { it.minEffectiveVolume }
                ?: landmarks.maxOf { it.mev }
            val target = recs.maxOfOrNull { it.maxAdaptiveVolume }
                ?: landmarks.maxOf { it.mav }
            val ceiling = minOf(
                recs.minOfOrNull { it.maxRecoverableVolume } ?: Int.MAX_VALUE,
                landmarks.minOfOrNull { it.mrv } ?: Int.MAX_VALUE,
            )
            canonical to MuscleTargets(canonical, floor, target, ceiling)
        }.toMap()
    }

    private fun landmarkKeyMatches(key: KpknMuscleGroup, canonical: String): Boolean {
        val c = canonical.lowercase()
        return when (key) {
            KpknMuscleGroup.CHEST -> c == "pectorales"
            KpknMuscleGroup.BACK_LATS -> c == "dorsales"
            KpknMuscleGroup.BACK_UPPER -> c == "trapecio" || c == "romboides"
            KpknMuscleGroup.QUADS -> c == "cuádriceps"
            KpknMuscleGroup.HAMS -> c == "isquiosurales"
            KpknMuscleGroup.GLUTES -> c == "glúteos"
            KpknMuscleGroup.ERECTORS -> c == "erectores espinales"
            KpknMuscleGroup.DELT_FRONT -> c == "deltoides"
            KpknMuscleGroup.DELT_LATERAL -> c == "deltoides"
            KpknMuscleGroup.DELT_REAR -> c == "deltoides"
            KpknMuscleGroup.BICEPS -> c == "bíceps"
            KpknMuscleGroup.TRICEPS -> c == "tríceps"
            KpknMuscleGroup.CALVES -> c == "pantorrillas"
            KpknMuscleGroup.CORE -> c == "core" || c == "abdomen"
            KpknMuscleGroup.FOREARMS -> c == "antebrazo"
            KpknMuscleGroup.NECK -> c == "cuello"
            KpknMuscleGroup.ADDUCTORS -> c == "aductores"
        }
    }

    private fun landmarkMrvFor(canonical: String): Int? {
        val mrvs = VolumeLandmarks.byGroup.entries
            .filter { landmarkKeyMatches(it.key, canonical) }
            .map { it.value.mrv }
        return mrvs.minOrNull()
    }

    fun scaleWeekSessions(
        sessions: List<Session>,
        exerciseList: List<ExerciseMuscleInfo>,
        recommendations: List<VolumeRecommendation>,
        idProvider: IdProvider = UuidIdProvider,
        diagnostic: ((String) -> Unit)? = null,
    ): VolumeScaleResult {
        if (sessions.isEmpty() || recommendations.isEmpty()) {
            return VolumeScaleResult(sessions, emptyMap(), emptyMap(), emptyList())
        }
        return scaleWithTargets(sessions, exerciseList, targetsFor(recommendations), idProvider, diagnostic)
    }

    private val COMPOUND_PRIORITY = listOf(
        "Pectorales",
        "Dorsales",
        "Cuádriceps",
        "Isquiosurales",
    )

    private fun scaleWithTargets(
        sessions: List<Session>,
        exerciseList: List<ExerciseMuscleInfo>,
        targets: Map<String, MuscleTargets>,
        idProvider: IdProvider,
        diagnostic: ((String) -> Unit)? = null,
    ): VolumeScaleResult {
        val exerciseIndex = exerciseList.associateBy { it.id.lowercase() }
        val weekly = VolumeCalculator.calculateCanonicalWeeklyMuscleVolumeForSessions(sessions, exerciseList)
            .associate { it.muscleName to it.weeklySets }
        if (targets.isEmpty()) {
            return VolumeScaleResult(sessions, emptyMap(), emptyMap(), emptyList())
        }
        val hasDeficit = targets.any { (muscle, t) -> (weekly[muscle] ?: 0.0) < t.target }
        if (!hasDeficit) {
            return VolumeScaleResult(sessions, emptyMap(), emptyMap(), emptyList())
        }
        // Use the same direct + secondary + stabilizer accounting as the UI.
        val projected = weekly.toMutableMap()
        val sortedMuscles = targets.keys.sortedWith(
            compareBy(
                { muscle ->
                    val idx = COMPOUND_PRIORITY.indexOf(muscle)
                    if (idx >= 0) idx else 99
                },
                { muscle -> muscle },
            ),
        )

        val accumulators = sessions.map { SessionAccumulator(it, exerciseIndex) }
        val added = mutableMapOf<String, Int>()
        val capped = linkedSetOf<String>()

        fun runScalePass(targetSelector: (MuscleTargets) -> Int) {
            sortedMuscles.forEach { muscle ->
                val t = targets.getValue(muscle)
                val targetVol = targetSelector(t)
                var remaining = ceil((minOf(targetVol, t.ceiling) - (projected[muscle] ?: 0.0)).coerceAtLeast(0.0)).toInt()
                if (remaining <= 0) return@forEach
                val candidates = accumulators
                    .flatMap { acc -> acc.directExercisesFor(muscle).map { acc to it } }
                if (candidates.isEmpty()) return@forEach
                val candidatesByRank = candidates
                    .groupBy { (acc, exercise) -> acc.primaryRank(muscle, exercise) }
                    .toSortedMap()

                for ((_, rankCandidates) in candidatesByRank) {
                    var progress = true
                    while (remaining > 0 && progress) {
                        progress = false
                        val sortedBySets = rankCandidates.sortedWith(
                            compareBy(
                                { (acc, exercise) -> acc.directSetsFor(muscle, exercise) },
                                { (_, exercise) -> exercise.id },
                            ),
                        )
                        for ((acc, exercise) in sortedBySets) {
                            if (remaining <= 0) break
                            val contributions = acc.contributions(exercise)
                            diagnostic?.invoke("TRY target=$muscle session=${acc.session.name} exercise=${exercise.catalogConfigurationId} sets=${acc.directSetsFor(muscle, exercise)} contributions=$contributions projected=$projected targets=$targets")
                            // A bounded allowance applies only to indirect collateral work.
                            // Existing excess is preserved, never used to raise the ceiling.
                            val blocked = contributions.any { (affected, contribution) ->
                                val target = targets[affected] ?: return@any false
                                val limit = if (affected != muscle) {
                                    target.target.toDouble() + INDIRECT_MRV_TOLERANCE
                                } else {
                                    target.ceiling.toDouble()
                                }
                                val rejected = (projected[affected] ?: 0.0) + contribution > limit + EPSILON
                                if (rejected) diagnostic?.invoke("REJECT target=$muscle exercise=${exercise.catalogConfigurationId} affected=$affected before=${projected[affected]} delta=$contribution limit=$limit")
                                rejected
                            }
                            if (blocked) {
                                capped.add(acc.session.id)
                                continue
                            }
                            if (acc.canAddDirectSet(muscle, exercise)) {
                                acc.addClonedSet(exercise, idProvider)
                                contributions.forEach { (affected, contribution) ->
                                    projected[affected] = (projected[affected] ?: 0.0) + contribution
                                }
                                diagnostic?.invoke("ACCEPT target=$muscle exercise=${exercise.catalogConfigurationId} projected=$projected")
                                remaining--
                                progress = true
                                added[muscle] = (added[muscle] ?: 0) + 1
                            } else {
                                capped.add(acc.session.id)
                            }
                        }
                    }
                }
            }
        }

        // Pass 1: Floor (MEV) to prevent compound/early groups from starving synergists below minimum.
        runScalePass { it.floor }

        // Pass 2: Calibrated target (MAV) up to ceiling.
        runScalePass { it.target }
        val resultSessions = accumulators.map { it.session }
        val residual = linkedMapOf<String, Double>()
        val after = VolumeCalculator.calculateCanonicalWeeklyMuscleVolumeForSessions(resultSessions, exerciseList)
            .associate { it.muscleName to it.weeklySets }
        targets.forEach { (muscle, t) ->
            val current = after[muscle] ?: 0.0
            if (current < t.target && current < t.ceiling) {
                residual[muscle] = ((t.target - current) * 10).toInt() / 10.0
            }
        }
        return VolumeScaleResult(resultSessions, added, residual, capped.toList())
    }

    /**
     * Cuota por sesión aislada (editor): ceil(targetSemanal / frecuencia),
     * con frecuencia = días del split con ese arquetipo (2 por defecto).
     * Nunca más allá de H7.
     */
    fun scaleSingleSession(
        session: Session,
        exerciseList: List<ExerciseMuscleInfo>,
        recommendations: List<VolumeRecommendation>,
        plannedFrequency: Int = 2,
        idProvider: IdProvider = UuidIdProvider,
    ): VolumeScaleResult {
        if (recommendations.isEmpty()) {
            return VolumeScaleResult(listOf(session), emptyMap(), emptyMap(), emptyList())
        }
        val frequency = plannedFrequency.coerceAtLeast(1)
        val adjusted = recommendations.map { rec ->
            rec.copy(
                minEffectiveVolume = ceil(rec.minEffectiveVolume.toDouble() / frequency).toInt().coerceAtLeast(1),
                maxAdaptiveVolume = ceil(rec.maxAdaptiveVolume.toDouble() / frequency).toInt().coerceAtLeast(1),
                maxRecoverableVolume = ceil(rec.maxRecoverableVolume.toDouble() / frequency).toInt().coerceAtLeast(1),
            )
        }
        val result = scaleWeekSessions(listOf(session), exerciseList, adjusted, idProvider)
        return result.copy(sessions = result.sessions.take(1))
    }

    private class SessionAccumulator(
        session: Session,
        private val exerciseIndex: Map<String, ExerciseMuscleInfo>,
    ) {
        var session: Session = session
            private set
        private val directCache = mutableMapOf<String, Map<String, Double>>()

        private fun directMap(exercise: Exercise): Map<String, Double> =
            directCache.getOrPut(exercise.id) {
                // effectiveMuscles del propio Exercise tiene prioridad (tests y
                // overrides); si viene vacío se resuelve por catálogo.
                val muscles = if (!exercise.effectiveMuscles.isNullOrEmpty()) {
                    exercise.effectiveMuscles!!.filter {
                        it.role != com.example.kpkn.data.models.MuscleRole.NEUTRALIZER
                    }
                } else {
                    com.example.kpkn.domain.exercises.ExerciseMuscleResolver
                        .effectiveMusclesForVolume(exercise, exerciseIndex)
                }
                VolumeCalculator.buildPerExerciseMuscleContributions(muscles)
                    .filter { (_, contribution) -> contribution >= 1.0 }
            }

        fun directExercisesFor(muscle: String): List<Exercise> =
            session.allExercises().filter { (directMap(it)[muscle] ?: 0.0) >= 1.0 }

        fun primaryRank(muscle: String, exercise: Exercise): Int {
            val muscles = if (!exercise.effectiveMuscles.isNullOrEmpty()) {
                exercise.effectiveMuscles!!.filter {
                    it.role != com.example.kpkn.data.models.MuscleRole.NEUTRALIZER
                }
            } else {
                com.example.kpkn.domain.exercises.ExerciseMuscleResolver
                    .effectiveMusclesForVolume(exercise, exerciseIndex)
            }
            val primaryMuscles = muscles
                .filter { it.role == com.example.kpkn.data.models.MuscleRole.PRIMARY }
                .map { VolumeCalculator.normalizeCanonicalMuscleGroup(it.muscle, it.emphasis) }
            val index = primaryMuscles.indexOf(muscle)
            return if (index >= 0) index else 99
        }

        fun contributions(exercise: Exercise): Map<String, Double> =
            VolumeCalculator.buildPerExerciseMuscleContributions(
                com.example.kpkn.domain.exercises.ExerciseMuscleResolver
                    .effectiveMusclesForVolume(exercise, exerciseIndex),
            )

        fun directContributions(exercise: Exercise): Map<String, Double> = directMap(exercise)

        fun directSetsFor(muscle: String, exercise: Exercise): Int {
            val current = findExercise(exercise.id) ?: return 0
            return VolumeCalculator.countEffectiveSets(current.sets)
        }

        fun canAddDirectSet(muscle: String, exercise: Exercise): Boolean {
            val current = findExercise(exercise.id) ?: return false
            if (current.sets.isEmpty()) return false
            val primaryMuscles = directMap(exercise)
                .filter { (_, contribution) -> contribution >= 1.0 }
                .keys
                .toList()
                .ifEmpty { listOf(muscle) }
            if (!primaryMuscles.all { canAddDirectSetForMuscle(it, exercise, current) }) return false
            return true
        }

        private fun canAddDirectSetForMuscle(muscle: String, exercise: Exercise, current: Exercise): Boolean {
            val perMuscle = session.allExercises().sumOf { other ->
                val sets = VolumeCalculator.countEffectiveSets(
                    if (other.id == current.id) current.sets else other.sets,
                )
                val contribution = directCache[other.id]?.get(muscle)
                    ?: com.example.kpkn.domain.exercises.ExerciseMuscleResolver
                        .effectiveMusclesForVolume(other, exerciseIndex)
                        .let { VolumeCalculator.buildPerExerciseMuscleContributions(it)[muscle] ?: 0.0 }
                if (contribution >= 1.0) sets else 0
            }
            if (perMuscle + 1 > SessionCompositionPolicy.MAX_DIRECT_SETS_PER_MUSCLE) return false
            val totalEffective = session.allExercises().sumOf { other ->
                VolumeCalculator.countEffectiveSets(if (other.id == current.id) current.sets else other.sets)
            }
            if (totalEffective + 1 > SessionCompositionPolicy.MAX_EFFECTIVE_SETS) return false
            val minutes = estimateSessionMinutes(session, mapOf(current.id to current.sets.size + 1))
            return minutes <= SessionCompositionPolicy.MAX_SESSION_MINUTES
        }

        /** Contribución directa del set recién clonado (para proyectar el MRV). */
        var lastAddedContribution: Double = 0.0
            private set

        fun addClonedSet(exercise: Exercise, idProvider: IdProvider) {
            val current = findExercise(exercise.id) ?: return
            val template = current.sets.lastOrNull() ?: return
            val updated = current.copy(sets = current.sets + template.copy(id = idProvider.newId()))
            replaceExercise(updated)
            directCache[updated.id] = directCache.getValue(exercise.id)
        }

        fun lastAddedContribution(muscle: String, exercise: Exercise): Double {
            val current = findExercise(exercise.id) ?: return 1.0
            return directMap(current)[muscle] ?: 1.0
        }

        private fun findExercise(id: String): Exercise? =
            session.allExercises().firstOrNull { it.id == id }

        private fun replaceExercise(updated: Exercise) {
            session = session.copy(
                exercises = session.exercises.map { if (it.id == updated.id) updated else it },
                parts = session.parts.map { part ->
                    part.copy(exercises = part.exercises.map { if (it.id == updated.id) updated else it })
                },
            )
        }
    }

    fun measureDirectWeeklyForTest(
        sessions: List<Session>,
        exerciseList: List<ExerciseMuscleInfo>,
    ): Map<String, Double> {
        val exerciseIndex = exerciseList.associateBy { it.id.lowercase() }
        return measureDirectWeekly(sessions.map { SessionAccumulator(it, exerciseIndex) })
    }

    private fun measureDirectWeekly(accumulators: List<SessionAccumulator>): Map<String, Double> {
        val out = mutableMapOf<String, Double>()
        accumulators.forEach { acc ->
            acc.session.allExercises().forEach { exercise ->
                val sets = VolumeCalculator.countEffectiveSets(exercise.sets).toDouble()
                if (sets <= 0) return@forEach
                acc.directContributions(exercise).forEach { (muscle, contribution) ->
                    out[muscle] = (out[muscle] ?: 0.0) + sets * contribution
                }
            }
        }
        return out
    }

    internal fun estimateSessionMinutes(
        session: Session,
        overrideSetCounts: Map<String, Int> = emptyMap(),
    ): Int {
        var seconds = 0
        session.allExercises().forEach { exercise ->
            val setCount = overrideSetCounts[exercise.id] ?: exercise.sets.size
            val setup = 90
            val rest = exercise.restTime?.takeIf { it > 0 } ?: 90
            val execution = setCount * 45
            val restTotal = if (setCount > 1) rest * (setCount - 1) else 0
            seconds += setup + execution + restTotal
        }
        return seconds / 60
    }
}
