package com.example.kpkn.domain.training.quality

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.isCardio

enum class SlopRule { DUPLICATE_CONFIGURATION, DUPLICATE_DEFINITION, SAME_MOVEMENT_FAMILY, INTERNAL_NAME, EMPTY_EXERCISE, EMPTY_SESSION }

data class SlopViolation(val rule: SlopRule, val sessionName: String, val detail: String)

/** Defectos que nunca deben verse en una sesión de un programa (auditoría del 27-oct). */
object SessionSlopRules {
    /** Configuraciones que la persona ve como «el mismo ejercicio» aunque el catálogo las separe. */
    private val MOVEMENT_FAMILIES: Map<String, String> = mapOf(
        "low_bar_back_squat__barbell" to "back_squat",
        "high_bar_back_squat__barbell" to "back_squat",
    )

    fun familyOf(configurationId: String?): String? = configurationId?.let { MOVEMENT_FAMILIES[it] }

    /**
     * true si elegir [candidateConfigurationId] repetiría en la sesión alguna de las configuraciones [takenConfigurationIds]
     * (misma configuración, misma definición, o misma familia de movimiento). Es el criterio de las reglas
     * DUPLICATE_CONFIGURATION / DUPLICATE_DEFINITION / SAME_MOVEMENT_FAMILY, para que el motor lo evite al elegir.
     */
    fun collides(candidateConfigurationId: String, takenConfigurationIds: Collection<String>): Boolean {
        val definition = candidateConfigurationId.substringBefore("__")
        val family = familyOf(candidateConfigurationId)
        return takenConfigurationIds.any { taken ->
            taken == candidateConfigurationId ||
                taken.substringBefore("__") == definition ||
                (family != null && familyOf(taken) == family)
        }
    }

    /**
     * Una aparición repetida está permitida solo si tiene identidad propia (F-15): enlazada a otra aparición o con el rótulo
     * del autor. El rótulo cuenta porque `normalizedIdentityFields()` borra el enlace a sí mismo al persistir el programa.
     */
    private fun linked(e: Exercise): Boolean = e.relativeToCanonicalExerciseId != null || e.occurrenceLabel != null

    fun violations(session: Session): List<SlopViolation> {
        val out = mutableListOf<SlopViolation>()
        val strength = session.allExercises().filterNot { it.isCardio }
        if (session.allExercises().isEmpty()) out += SlopViolation(SlopRule.EMPTY_SESSION, session.name, "sesión sin ejercicios")
        strength.filter { it.sets.isEmpty() }.forEach { out += SlopViolation(SlopRule.EMPTY_EXERCISE, session.name, it.name) }
        fun repeated(key: (Exercise) -> String?, rule: SlopRule) {
            strength.groupBy(key).filterKeys { it != null }.forEach { (k, list) ->
                // Como máximo una aparición sin identidad propia (la base); da igual si va antes o después (velocidad primero).
                if (list.size > 1 && list.count { !linked(it) } > 1) out += SlopViolation(rule, session.name, "$k ×${list.size}: ${list.map { it.name }}")
            }
        }
        repeated({ it.catalogConfigurationId }, SlopRule.DUPLICATE_CONFIGURATION)
        repeated({ it.catalogDefinitionId }, SlopRule.DUPLICATE_DEFINITION)
        repeated({ familyOf(it.catalogConfigurationId) }, SlopRule.SAME_MOVEMENT_FAMILY)
        session.allExercises().forEach { e ->
            if (e.name.contains("__") || INTERNAL_NAME.matches(e.name)) out += SlopViolation(SlopRule.INTERNAL_NAME, session.name, e.name)
        }
        return out
    }

    /** Nombres internos: ids de catálogo o nombres de enum en minúsculas inglesas («bike stationary», «walk», «treadmill»). */
    private val INTERNAL_NAME = Regex("^(treadmill|elliptical|row machine|bike stationary|run outdoor|bike outdoor|walk|stair climber|air bike|ski erg|curved treadmill|sled|[a-z]+(_[a-z]+)+)$")
}
