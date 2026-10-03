package com.example.kpkn.data.protocols

import com.example.kpkn.data.programs.CatalogSource
import kotlinx.serialization.Serializable

/**
 * Clase editorial de un plan (§10.1/§14.1): Original fiel, Adaptación KPKN,
 * Plan KPKN o LEGACY (sin procedencia declarada).
 */
@Serializable
enum class PlanProvenanceClass {
    ORIGINAL,
    ADAPTED,
    KPKN,
    LEGACY,
}

/** Alcance de un default operativo KPKN aplicado porque el autor no lo define (§10.1 regla 5). */
@Serializable
enum class KpknOperationalDefaultScope {
    /** Calentamiento cuando el autor no lo define. */
    WARMUP,
    /** Descansos cuando el autor no los define. */
    REST,
    /** Elección inicial dentro de rangos publicados (p. ej. mínimo de series). */
    INITIAL_CHOICE,
    /** Nombres/traducciones locales. */
    NAMING,
    OTHER,
}

/**
 * Default operativo KPKN; se muestra como «Configuración inicial KPKN» y nunca
 * se atribuye al autor (§10.1 regla 5).
 */
@Serializable
data class KpknOperationalDefault(
    val scope: KpknOperationalDefaultScope = KpknOperationalDefaultScope.OTHER,
    val note: String = "",
)

/**
 * Cambio por slot registrado al adaptar un plan (§13.4). Todos los campos son
 * defaulted para que JSON crudo sin claves nuevas siga decodificando.
 */
@Serializable
data class PlanSlotChange(
    val slotId: String = "",
    val fromConfigurationId: String? = null,
    val toConfigurationId: String? = null,
    val reason: String = "",
    /** true = mismo patrón (variante válida de la misma definición); false = cambio de patrón documentado. */
    val samePattern: Boolean = false,
    /** Diferencias de ROM/soporte/unidad entre las dos configuraciones. */
    val differences: String? = null,
    val prescriptionBefore: String? = null,
    val prescriptionAfter: String? = null,
    /** true = referencia de carga conservada; false = descartada; null = sin referencia. */
    val loadReferenceKept: Boolean? = null,
)

/**
 * Procedencia editorial de un plan o receta (§14.1). Se persiste como JSON con
 * defaults en `ProgramEntity.data` y en la receta: los payloads antiguos sin
 * estas claves siguen decodificando y `null` sigue distinguiéndose de una
 * instancia declarada (null ≠ vacío).
 *
 * Una instancia vacía clasifica como [PlanProvenanceClass.LEGACY]: no se
 * atribuye autoría ni fidelidad a datos de procedencia ausentes. El origen
 * técnico [technicalOrigin] (TEMPLATE/PROTOCOL/NATIVE) no determina por sí solo
 * la categoría visible Original/Adaptación/Propio (D-101).
 */
@Serializable
data class PlanProvenance(
    /** ID estable del plan publicado (p. ej. `original:phul-ms-2021-r1`). */
    val planId: String? = null,
    /** Receta de la que deriva este contenido. */
    val recipeId: String? = null,
    /** Revisión de la receta/procedencia. */
    val revision: Int = 1,
    val category: PlanProvenanceClass = PlanProvenanceClass.LEGACY,
    /** Origen técnico interno (§10.1); se conserva para compatibilidad. */
    val technicalOrigin: CatalogSource? = null,
    /** Fuente citada (§10.1 regla 4). */
    val sourceTitle: String? = null,
    val sourceUrl: String? = null,
    val sourceAuthor: String? = null,
    /** Edición/fecha de la fuente (p. ej. «M&S 2021-05-26», «Biolayne 2016-05-30»). */
    val sourceEdition: String? = null,
    /** Adaptación: receta padre de la que deriva. */
    val parentId: String? = null,
    val parentRevision: Int? = null,
    /** Cambios por slot frente al original (§13.4). */
    val slotChanges: List<PlanSlotChange> = emptyList(),
    /** Defaults operativos KPKN («Configuración inicial KPKN»), nunca atribuidos al autor. */
    val operationalDefaults: List<KpknOperationalDefault> = emptyList(),
)
