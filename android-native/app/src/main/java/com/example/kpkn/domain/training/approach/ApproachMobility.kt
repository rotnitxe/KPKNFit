package com.example.kpkn.domain.training.approach

import com.example.kpkn.data.models.MobilitySeries

/**
 * Elige la movilidad previa de un ejercicio con [JointMobility]: hasta [Budget.perJoint] movimientos por
 * articulación nueva, de 30 a 45 s cada uno y con un tope de tiempo total por ejercicio (4 min como máximo, en el
 * nivel novato). Cuanto menos experiencia, más movilidad: el avanzado recibe un movimiento por articulación y
 * menos tiempo. Un movimiento que mueve varias articulaciones (sentadilla profunda) cuenta para todas ellas.
 */
internal object ApproachMobility {

    /** Tiempo máximo de movilidad, movimientos por articulación y tope por movimiento, según el nivel. */
    data class Budget(val maxSeconds: Int, val perJoint: Int, val maxMovementSeconds: Int)

    private const val MIN_MOVEMENT_SECONDS = 30
    private const val BRIEF_MAX_SECONDS = 40

    /** Presupuesto de la movilidad previa del PRIMER ejercicio pesado. */
    fun firstExerciseBudget(level: ApproachLevel): Budget = when (level) {
        ApproachLevel.NOVICE -> Budget(maxSeconds = 240, perJoint = 2, maxMovementSeconds = 45)
        ApproachLevel.INTERMEDIATE -> Budget(maxSeconds = 150, perJoint = 2, maxMovementSeconds = 40)
        ApproachLevel.ADVANCED -> Budget(maxSeconds = 120, perJoint = 1, maxMovementSeconds = 35)
    }

    /** Presupuesto de la movilidad de la articulación nueva del segundo o tercer ejercicio pesado. */
    fun laterExerciseBudget(level: ApproachLevel): Budget = when (level) {
        ApproachLevel.NOVICE -> Budget(maxSeconds = 120, perJoint = 2, maxMovementSeconds = 45)
        ApproachLevel.INTERMEDIATE -> Budget(maxSeconds = 90, perJoint = 2, maxMovementSeconds = 40)
        ApproachLevel.ADVANCED -> Budget(maxSeconds = 60, perJoint = 1, maxMovementSeconds = 35)
    }

    /**
     * Movilidad previa de las articulaciones [joints] (en orden de importancia: las dos primeras se consideran las
     * principales y son las únicas que reciben un segundo movimiento).
     */
    fun forJoints(ownerName: String, joints: List<String>, budget: Budget): List<MobilitySeries> {
        if (joints.isEmpty()) return emptyList()
        val chosen = LinkedHashMap<String, Int>()
        val coverage = HashMap<String, Int>()
        var total = 0

        fun tryAdd(id: String): Boolean {
            val movement = JointMobility.movement(id) ?: return false
            if (id in chosen) return false
            val seconds = movement.durationSeconds.coerceIn(MIN_MOVEMENT_SECONDS, budget.maxMovementSeconds)
            if (total + seconds > budget.maxSeconds) return false
            chosen[id] = seconds
            total += seconds
            JointMobility.jointsOf(id).forEach { joint -> coverage[joint] = (coverage[joint] ?: 0) + 1 }
            return true
        }

        // Primera pasada: cada articulación nueva queda cubierta por al menos un movimiento.
        joints.forEach { joint ->
            if ((coverage[joint] ?: 0) >= 1) return@forEach
            JointMobility.movementIdsFor(joint).firstOrNull { tryAdd(it) }
        }
        // Segunda pasada: un segundo movimiento para las articulaciones principales, mientras quepa.
        if (budget.perJoint >= 2) {
            joints.take(MAIN_JOINTS).forEach { joint ->
                while ((coverage[joint] ?: 0) < budget.perJoint) {
                    if (JointMobility.movementIdsFor(joint).firstOrNull { tryAdd(it) } == null) break
                }
            }
        }
        return chosen.mapNotNull { (id, seconds) ->
            JointMobility.movement(id)?.let { JointMobility.seriesFor(it, ownerName, seconds) }
        }
    }

    /** Movilidad breve de la articulación principal: un solo movimiento de 30–40 s (primer ejercicio ligero). */
    fun brief(ownerName: String, mainJoint: String): List<MobilitySeries> {
        val movement = JointMobility.movementsFor(mainJoint).firstOrNull() ?: return emptyList()
        val seconds = movement.durationSeconds.coerceIn(MIN_MOVEMENT_SECONDS, BRIEF_MAX_SECONDS)
        return listOf(JointMobility.seriesFor(movement, ownerName, seconds))
    }

    private const val MAIN_JOINTS = 2
}
