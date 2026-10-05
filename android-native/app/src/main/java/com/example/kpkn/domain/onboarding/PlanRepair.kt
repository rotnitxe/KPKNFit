package com.example.kpkn.domain.onboarding

import com.example.kpkn.data.models.ApparatusPresence
import com.example.kpkn.data.models.EquipmentAvailability
import com.example.kpkn.data.models.EquipmentCategory
import com.example.kpkn.domain.training.EFFECTIVE_EQUIPMENT_KEYS

/**
 * Paquete A · A.C1 (curaduría de programas, 2026-10-03) — reparaciones de UN toque de un plan propio
 * rechazado. Las propone [PlanRepairAdvisor.suggest] y las aplica el ViewModel del wizard con la API
 * que ya existe (`updateStep`, `setStepNumber`, `setStepChoice`, `editStep`): este tipo solo dice QUÉ
 * cambiar, nunca cambia nada por sí mismo.
 *
 * Qué NO es ninguna de estas reparaciones (decisiones del dueño, DEC-w1-01 y DEC-w2-02):
 *  - No recorta el cardio del Atleta ni los descansos dentro del generador: [SetCardioMinutes] es una
 *    elección de la persona (solo hacia abajo y solo en Atleta completo), no un recorte silencioso.
 *  - No inventa variantes de «fuerza relativa» para quien no tiene material: [SwitchGoal] redirige de
 *    forma honesta a otro objetivo cuyo plan propio ya existe y ya cabe.
 *  - Nunca apunta a Atleta completo (exige cardio y minutos de cardio que la persona no ha elegido).
 *
 * Las reparaciones se devuelven en una lista ordenada: la primera se aplica antes que la segunda
 * (p. ej. confirmar un aparato y, con el material ya confirmado, subir los minutos). Lista vacía = el
 * rechazo no tiene una reparación de un toque.
 */
sealed interface PlanRepair {

    /** Sube los minutos por sesión a [minutes] (el mínimo exacto con el que el plan sí cabe). */
    data class SetMinutes(val minutes: Int) : PlanRepair

    /** Baja los minutos de cardio del Atleta a [minutes] (un valor de los que el plan ofrece). */
    data class SetCardioMinutes(val minutes: Int) : PlanRepair

    /**
     * Confirma como «Sí, tengo» las llaves [keys] del panel de material (las que el rechazo no pudo
     * confirmar) y marca las categorías [categories] que esas llaves necesitan para mostrarse y contar.
     * [applyTo] es la escritura pura sobre la disponibilidad: la usan por igual el asesor (para probar
     * el plan con el material confirmado) y el ViewModel (para aplicar la reparación).
     */
    data class ConfirmApparatus(val keys: List<String>, val categories: Set<EquipmentCategory>) : PlanRepair {

        /**
         * Disponibilidad con las [categories] añadidas y cada llave de [keys] en `PRESENT`. Una llave
         * que no es del panel se ignora; una ausencia que la persona había declarado SE sobrescribe si la
         * llave viene en [keys]. El asesor no las propone: de las llaves que acreditan un requisito elige la que
         * sigue sin responder ([PlanRepairAdvisor.confirmableKeyFor]), nunca una que la persona negó.
         */
        fun applyTo(availability: EquipmentAvailability): EquipmentAvailability {
            val withCategories = availability.copy(categories = availability.categories + categories)
            return keys.fold(withCategories) { current, key ->
                val spec = EFFECTIVE_EQUIPMENT_KEYS.firstOrNull { it.key == key } ?: return@fold current
                SetupApparatusPanel.withPresence(
                    current,
                    key,
                    ApparatusPresence.PRESENT,
                    SetupApparatusPanel.isSupport(spec.category),
                ) ?: current
            }
        }
    }

    /**
     * Cambia el objetivo a [goal] (nunca Atleta completo). Si en el destino el plan solo falla por
     * tiempo, [alsoMinutes] trae los minutos exactos que lo arreglan y se aplican junto con el cambio.
     */
    data class SwitchGoal(val goal: PlanGoalProfile, val alsoMinutes: Int? = null) : PlanRepair

    /** Quita el reparto de días elegido para que el plan use su calendario propio. */
    data object ClearSplit : PlanRepair
}
