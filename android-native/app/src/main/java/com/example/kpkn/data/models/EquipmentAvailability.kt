package com.example.kpkn.data.models

import kotlinx.serialization.Serializable

/**
 * Presencia tri-estado de UN aparato o soporte concreto (plan §13.1/§13.2):
 * - [UNKNOWN] (o la clave ausente del mapa) no acredita presencia y tampoco
 *   niega la evidencia exacta previa: solo muestra que falta confirmar.
 * - [PRESENT] habilita únicamente el mapeo curado de esa clave (nunca el kind
 *   genérico de la categoría, que sigue delimitando el resto).
 * - [ABSENT] elimina el aparato y sus configuraciones, AUNQUE exista
 *   inventario previo: una ausencia explícita es la respuesta más reciente.
 */
@Serializable
enum class ApparatusPresence { UNKNOWN, PRESENT, ABSENT }

/**
 * Categorías de equipo confirmadas + presencia concreta de aparatos/soportes
 * (§13.1). Ausencia de [EquipmentAvailability] = desconocido/legacy; una
 * instancia con categorías vacías CONFIRMADA significa solo cuerpo, y los
 * campos nuevos ausentes decodifican `{}` (clave ausente = [ApparatusPresence.UNKNOWN]).
 *
 * No introduce inventario de pesos: los mapas solo declaran presencia, sin
 * cantidades. [EquipmentInventory] y su stock no cambian aquí.
 */
@Serializable
data class EquipmentAvailability(
    val categories: Set<EquipmentCategory> = emptySet(),
    val apparatus: Map<String, ApparatusPresence> = emptyMap(),
    val supports: Map<String, ApparatusPresence> = emptyMap(),
) {
    /**
     * Presencia combinada de una clave: una ausencia explícita gana sobre una
     * presencia (nunca se afirma material que el usuario negó) y cualquier
     * otra combinación —incluida la ausencia de la clave— queda en UNKNOWN.
     */
    fun presenceOf(key: String): ApparatusPresence {
        val inApparatus = apparatus[key]
        val inSupports = supports[key]
        return when {
            inApparatus == ApparatusPresence.ABSENT || inSupports == ApparatusPresence.ABSENT -> ApparatusPresence.ABSENT
            inApparatus == ApparatusPresence.PRESENT || inSupports == ApparatusPresence.PRESENT -> ApparatusPresence.PRESENT
            else -> ApparatusPresence.UNKNOWN
        }
    }

    /** true cuando el usuario declaró presencia o ausencia de ALGÚN ítem concreto. */
    val hasExplicitPresence: Boolean
        get() = apparatus.values.any { it != ApparatusPresence.UNKNOWN } ||
            supports.values.any { it != ApparatusPresence.UNKNOWN }
}

/** Closed set of equipment categories the setup flow can declare. */
@Serializable
enum class EquipmentCategory {
    BARBELL,
    DUMBBELLS,
    KETTLEBELL,
    MACHINES,
    CABLE,
    SMITH_MACHINE,
    BAND,
    SUPPORT,
    PULL_UP_BAR,
    BALL,
    CARDIO,
}
