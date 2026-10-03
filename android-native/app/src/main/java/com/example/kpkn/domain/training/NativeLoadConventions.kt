package com.example.kpkn.domain.training

import com.example.kpkn.data.models.LoadQuantityConvention

/**
 * Convención de cantidad de carga (§14.2) y familia de material de una
 * configuración, derivadas SIEMPRE del `equipmentId` del catálogo v2 (nunca de
 * subcadenas del id ni del nombre). Dominio puro: sin `android.*`.
 *
 * - barra y derivadas, discos, máquina, polea y Smith → [LoadQuantityConvention.TOTAL_EXTERNAL]
 *   (kg totales de la barra con discos, o kg de la pila).
 * - mancuernas y kettlebell → [LoadQuantityConvention.PER_IMPLEMENT] (kg por mano).
 * - peso corporal, bandas, TRX y similares → [LoadQuantityConvention.UNSPECIFIED]: no
 *   tienen carga externa cuantificable de forma inequívoca; lastre/asistencia no se
 *   inventan desde el catálogo (dependen del modo de carga que registre el atleta).
 */
object NativeLoadConventions {
    /** Familia de material que decide qué stock del inventario puede ofrecer el siguiente paso. */
    enum class StockKind { DUMBBELL, KETTLEBELL, BARBELL, MACHINE, NONE }

    private val BARBELL_EQUIPMENT = setOf("barbell", "ez_bar", "hex_bar", "safety_bar", "t_bar", "h_bar")
    private val STACK_EQUIPMENT = setOf("machine", "cable", "smith_machine")
    private val PLATE_EQUIPMENT = setOf("plate")
    private val IMPLEMENT_EQUIPMENT = setOf("dumbbells", "kettlebell")

    /** Convención declarada por el equipo del catálogo; UNSPECIFIED si no hay carga externa inequívoca. */
    fun forEquipment(equipmentId: String?): LoadQuantityConvention {
        val id = equipmentId?.trim()?.lowercase().orEmpty()
        return when {
            id.isEmpty() -> LoadQuantityConvention.UNSPECIFIED
            id in IMPLEMENT_EQUIPMENT -> LoadQuantityConvention.PER_IMPLEMENT
            id in BARBELL_EQUIPMENT || id in STACK_EQUIPMENT || id in PLATE_EQUIPMENT ->
                LoadQuantityConvention.TOTAL_EXTERNAL
            else -> LoadQuantityConvention.UNSPECIFIED
        }
    }

    /**
     * Familia de stock de una configuración. Con `equipmentId` del catálogo manda
     * el catálogo. Solo cuando el catálogo no conoce el id (datos legados o ids
     * sintéticos) se recurre al id como último recurso, con la misma regla que
     * usaba el runtime antes de leer el equipo del catálogo.
     */
    fun stockKindFor(equipmentId: String?, configurationId: String): StockKind {
        val id = equipmentId?.trim()?.lowercase().orEmpty()
        if (id.isNotEmpty()) {
            return when {
                id == "dumbbells" -> StockKind.DUMBBELL
                id == "kettlebell" -> StockKind.KETTLEBELL
                id == "barbell" -> StockKind.BARBELL // solo la barra declarada en el inventario tiene tara conocida; EZ/hex/safety/T/H: carga por elegir
                id in STACK_EQUIPMENT -> StockKind.MACHINE
                else -> StockKind.NONE
            }
        }
        val config = configurationId.lowercase()
        return when {
            "dumbbell" in config -> StockKind.DUMBBELL
            "kettlebell" in config -> StockKind.KETTLEBELL
            "machine" in config || "cable" in config || "smith" in config -> StockKind.MACHINE
            "barbell" in config -> StockKind.BARBELL
            else -> StockKind.NONE
        }
    }
}
