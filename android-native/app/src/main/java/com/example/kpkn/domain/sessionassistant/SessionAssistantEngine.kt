package com.example.kpkn.domain.sessionassistant

import com.example.kpkn.data.exercises.resolveCatalogExerciseInfoInIndex
import com.example.kpkn.data.models.*
import com.example.kpkn.data.sessions.SessionTemplate
import com.example.kpkn.domain.training.SessionDurationEstimator
import com.example.kpkn.domain.training.VolumeCalculator
import com.example.kpkn.domain.exercises.ExerciseMuscleResolver

internal fun reduceRestLabel(seconds: Int): String = "Bajar $seconds s de descanso entre series"

/** Recorded/planned counts and the user's explicit time/structure constraints, never a body-state score. */
object SessionAssistantEngine {
    fun evaluate(input: SessionAssistantInput, allTemplates: List<SessionTemplate> = emptyList()): SessionAssistantReport {
        val session = input.session ?: provisionalSession(input)
        val duration = SessionDurationEstimator.estimate(session)
        val volume = calcularVolumenPorMusculo(input)
        val context = SessionAnalysisContextBuilder.build(input, session, duration.totalSeconds)
        val advice = SessionAdvisor.advise(input, session, context, volume)
        return SessionAssistantReport(veredicto=null,scoreEstimado=null,riesgos=emptyList(),ajustes=advice.cards,
            oportunidades=emptyList(),tarjetasFantasma=emptyList(),plantillasCompatibles=emptyList(),
            volumenPorMusculo=volume.volumeMap.mapValues { it.value.flat },umbralesPorMusculo=emptyMap(),
            duracionEstimada=duration.totalMinutes,resumenTexto="Datos del programa y límites que elegiste",
            totalRestSeconds=duration.resistanceRestSeconds,
            estimatedWorkSeconds=(duration.totalSeconds-duration.resistanceRestSeconds).coerceAtLeast(0),notas=advice.notes)
    }
    internal fun provisionalSession(input: SessionAssistantInput): Session =
        (input.weekSessions.firstOrNull { it.id == input.currentSessionId } ?: Session(input.currentSessionId,"Esta sesión"))
            .copy(exercises=input.allExercisesInSession,parts=emptyList())
    internal data class VolumeCalculationResult(val volumeMap: Map<String,MuscularVolumeAccumulator>)
    internal fun calcularVolumenPorMusculo(input: SessionAssistantInput): VolumeCalculationResult {
        val session=input.session ?: provisionalSession(input)
        val result=VolumeCalculator.calculateUnifiedMuscleVolume(listOf(session),input.exerciseIndex.values.toList())
        return VolumeCalculationResult(result.associate { it.muscleName to MuscularVolumeAccumulator(flat=it.displayVolume,effective=it.displayVolume) })
    }
    internal fun resolveExerciseInfo(
        exercise: Exercise,
        exerciseIndex: Map<String, ExerciseMuscleInfo>,
    ): ExerciseMuscleInfo? = resolveCatalogExerciseInfoInIndex(
        index = exerciseIndex,
        catalogConfigurationId = exercise.catalogConfigurationId,
        exerciseDbId = exercise.exerciseDbId,
        exerciseId = exercise.exerciseId,
        exerciseName = exercise.name,
    )

    fun findNextSessionWithMuscles(
        currentSessionId: String,
        weekSessions: List<com.example.kpkn.data.models.Session>,
        muscleIds: List<String>,
        exerciseIndex: Map<String, com.example.kpkn.data.models.ExerciseMuscleInfo>,
    ): com.example.kpkn.data.models.Session? {
        val currentIdx = weekSessions.indexOfFirst { it.id == currentSessionId }
        if (currentIdx == -1) return null

        for (i in (currentIdx + 1) until weekSessions.size) {
            val session = weekSessions[i]
            if (sessionContainsAnyMuscle(session, muscleIds, exerciseIndex)) {
                return session
            }
        }

        for (i in 0 until currentIdx) {
            val session = weekSessions[i]
            if (sessionContainsAnyMuscle(session, muscleIds, exerciseIndex)) {
                return session
            }
        }
        return null
    }

    private fun sessionContainsAnyMuscle(
        session: com.example.kpkn.data.models.Session,
        muscleIds: List<String>,
        exerciseIndex: Map<String, com.example.kpkn.data.models.ExerciseMuscleInfo>,
    ): Boolean {
        for (ex in session.allExercises()) {
            val info = resolveCatalogExerciseInfoInIndex(
                index = exerciseIndex,
                catalogConfigurationId = ex.catalogConfigurationId,
                exerciseDbId = ex.exerciseDbId,
                exerciseId = ex.exerciseId,
                exerciseName = ex.name,
            ) ?: continue
            for (muscle in info.involvedMuscles) {
                if (muscle.role == com.example.kpkn.data.models.MuscleRole.PRIMARY) {
                    val mId = VolumeCalculator.normalizeCanonicalMuscleGroup(muscle.muscle, muscle.emphasis)
                    if (muscleIds.contains(mId)) return true
                }
            }
        }
        return false
    }

    fun computeProposedDiscounts(
        currentSession: com.example.kpkn.data.models.Session,
        nextSession: com.example.kpkn.data.models.Session,
        targetMuscles: List<String>,
        completedSets: Map<String, com.example.kpkn.data.models.CompletedSet>,
        exerciseIndex: Map<String, com.example.kpkn.data.models.ExerciseMuscleInfo>,
        liveSession: com.example.kpkn.data.models.Session = currentSession,
    ): List<com.example.kpkn.data.models.MuscleAdvance> {
        val muscleAdvances = mutableListOf<com.example.kpkn.data.models.MuscleAdvance>()
        val completedByExercise = mutableMapOf<String, Int>()
        for ((key, _) in completedSets) {
            var exerciseId = key.substringBeforeLast("_")
            if (exerciseId.endsWith("_L") || exerciseId.endsWith("_R")) {
                exerciseId = exerciseId.substringBeforeLast("_")
            }
            completedByExercise[exerciseId] = (completedByExercise[exerciseId] ?: 0) + 1
        }

        for (muscleId in targetMuscles) {
            var plannedSets = 0.0
            for (ex in currentSession.allExercises()) {
                val muscles = ExerciseMuscleResolver.effectiveMusclesForVolume(ex, exerciseIndex)
                for (m in muscles) {
                    if (m.role != com.example.kpkn.data.models.MuscleRole.PRIMARY) continue
                    val mId = VolumeCalculator.normalizeCanonicalMuscleGroup(m.muscle, m.emphasis)
                    if (mId == muscleId) {
                        plannedSets += VolumeCalculator.countEffectiveSets(ex.sets)
                    }
                }
            }

            var actualSets = 0.0
            for (ex in liveSession.allExercises()) {
                val sets = completedByExercise[ex.id] ?: 0
                if (sets == 0) continue
                val muscles = ExerciseMuscleResolver.effectiveMusclesForVolume(ex, exerciseIndex)
                for (m in muscles) {
                    if (m.role != com.example.kpkn.data.models.MuscleRole.PRIMARY) continue
                    val mId = VolumeCalculator.normalizeCanonicalMuscleGroup(m.muscle, m.emphasis)
                    if (mId == muscleId) {
                        actualSets += sets
                    }
                }
            }

            val delta = actualSets - plannedSets
            if (delta <= 0.0) continue

            val proposals = mutableListOf<com.example.kpkn.data.models.VolumeDiscountProposal>()
            var remainingDiscount = delta

            for (ex in nextSession.allExercises()) {
                if (remainingDiscount <= 0.0) break
                var trainsMuscle = false
                for (m in ExerciseMuscleResolver.effectiveMusclesForVolume(ex, exerciseIndex)) {
                    if (m.role != com.example.kpkn.data.models.MuscleRole.PRIMARY) continue
                    val mId = VolumeCalculator.normalizeCanonicalMuscleGroup(m.muscle, m.emphasis)
                    if (mId == muscleId) {
                        trainsMuscle = true
                        break
                    }
                }

                if (trainsMuscle) {
                    val maxDiscount = (ex.sets.size - 1).coerceAtLeast(0).toDouble()
                    if (maxDiscount > 0) {
                        val discount = remainingDiscount.coerceAtMost(maxDiscount)
                        proposals.add(
                            com.example.kpkn.data.models.VolumeDiscountProposal(
                                exerciseId = ex.id,
                                exerciseName = ex.name,
                                currentRole = "PRIMARY",
                                discountSets = discount,
                                reason = "Excedente de volumen de " + muscleId + " (+" + delta.toInt() + " series)",
                            )
                        )
                        remainingDiscount -= discount
                    }
                }
            }

            if (proposals.isNotEmpty()) {
                val readableName = VolumeCalculator.normalizeCanonicalMuscleGroup(muscleId).ifBlank { muscleId }
                muscleAdvances.add(
                    com.example.kpkn.data.models.MuscleAdvance(
                        muscleId = muscleId,
                        muscleName = readableName,
                        currentSets = actualSets,
                        targetSets = plannedSets,
                        deficitSets = delta,
                        targetSessionId = nextSession.id,
                        targetSessionName = nextSession.name,
                        discountProposals = proposals,
                    )
                )
            }
        }
        return muscleAdvances
    }
}
