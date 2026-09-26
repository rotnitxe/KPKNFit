package com.example.kpkn.domain.training

import com.example.kpkn.data.models.Program
import com.example.kpkn.data.protocols.SlotSource
import com.example.kpkn.data.protocols.TrainingPlanRecipe

/**
 * Origen de bloque que marca un programa curado por el motor nativo
 * ([SimpleCyclePersonalizer]). Vive aquí para que la detección de origen
 * nativo y el contrato de orden compartan una única definición.
 */
internal const val KPKN_NATIVE_CURATED_ORIGIN = "KPKN_NATIVE_CURATED"

/** true si el programa contiene al menos un bloque curado por el motor nativo. */
internal fun Program.hasNativeCuratedBlock(): Boolean =
    macrocycles.flatMap { it.blocks }.any { it.prescriptionOrigin == KPKN_NATIVE_CURATED_ORIGIN }

/**
 * true SOLO cuando [recipe] es la receta propia de un programa nativo: bloque
 * nativo + la receta persistida en el programa + todos sus slots KPKN_DEFAULT.
 *
 * La atribución de origen no se altera: nunca se decide por `sourceProtocolId`
 * (los templates sí lo llevan) ni por `SlotSource.KPKN_DEFAULT` aislado (los
 * arquetipos KPKN aparecen dentro de recetas de autor). Así, aplicar un
 * protocolo o una plantilla encima de un plan nativo —o rematerializar con
 * otra receta— NO convierte esa receta ajena en contenido nativo: su base se
 * preserva intacta.
 */
internal fun Program.isNativeCuratedRecipe(recipe: TrainingPlanRecipe): Boolean {
    if (!hasNativeCuratedBlock()) return false
    if (sourceRecipe?.id != recipe.id) return false
    return recipe.weeks.all { week ->
        week.days.all { day -> day.slots.all { it.source == SlotSource.KPKN_DEFAULT } }
    }
}

/** Quién es dueño del orden de ejercicios de un programa. */
enum class OrderOwnership {
    /**
     * La receta (de autor o de plantilla) fija el orden de sus slots: esta ruta
     * nunca lo reescribe y la bolsa de orden no se aplica sobre ella.
     */
    RECIPE_FIXED,

    /**
     * El generador nativo ([SimpleCyclePersonalizer]) aplica la bolsa al
     * construir las sesiones; la bolsa realmente aplicada queda en
     * [Program.planOrderPriorities].
     */
    NATIVE_GENERATOR,

    /** Programa sin receta ni origen nativo (p. ej. plan montado a mano). */
    NOT_MANAGED,
}

/** Resultado honesto de la bolsa de prioridades de orden en una ruta concreta. */
enum class OrderPrioritiesStatus {
    /** No se pidió ninguna bolsa (o solo ceros). */
    NOT_REQUESTED,

    /** La bolsa incumple el contrato (más de 2 por músculo, más de 5 o negativos). */
    INVALID,

    /** La bolsa pedida coincide con la realmente aplicada al generar el programa. */
    APPLIED,

    /** La receta fija el orden: la bolsa NO se aplicó y su orden se conserva intacto. */
    NOT_APPLIED_RECIPE_FIXED,

    /** El programa no gestiona el orden con la bolsa (manual o sin generar). */
    NOT_APPLIED_NOT_MANAGED,

    /** El motor aplica bolsas, pero no coincide con la que se aplicó al generar. */
    NOT_APPLIED_BAG_MISMATCH,
}

/**
 * Resultado público que la UI debe consultar antes de afirmar que la bolsa de
 * prioridades de orden quedó aplicada: `applied == false` significa que la
 * ruta consultada no la aplicó (y [reasons] explica por qué), nunca un
 * «aplicado» supuesto. La bolsa SOLO ordena ejercicios: ni la receta, ni
 * ejercicios, series, repeticiones, intensidades ni frecuencia se tocan aquí.
 */
data class OrderPrioritiesCapabilities(
    /** Bolsa tal y como la pidió el caller. */
    val requested: Map<String, Int>,
    /** Bolsa normalizada (músculos canónicos) o null si incumple el contrato. */
    val normalizedRequested: Map<String, Int>?,
    /** Bolsa realmente aplicada al generar este programa ([Program.planOrderPriorities]). */
    val appliedBag: Map<String, Int>,
    val ownership: OrderOwnership,
    val status: OrderPrioritiesStatus,
    val reasons: List<String>,
) {
    /** true solo cuando la bolsa pedida es, de hecho, la aplicada. */
    val applied: Boolean get() = status == OrderPrioritiesStatus.APPLIED
}

/**
 * Contrato puro de la bolsa de prioridades de orden aplicable a cualquier
 * llamada de materialización (`PlanMaterializer.materialize`,
 * `PlanMaterializer.rematerializeWeek`, `OnboardingPlanGenerator.generate`).
 *
 * - Recetas de autor/plantilla → [OrderOwnership.RECIPE_FIXED]: el orden del
 *   autor se preserva completo y la bolsa NO se aplica
 *   ([OrderPrioritiesStatus.NOT_APPLIED_RECIPE_FIXED]).
 * - Programa nativo → [OrderOwnership.NATIVE_GENERATOR]: la bolsa la aplica el
 *   generador antes de persistir; aquí solo se verifica que la pedida sea la
 *   registrada en [Program.planOrderPriorities].
 * - Bolsa fuera de contrato → [OrderPrioritiesStatus.INVALID] con el motivo.
 */
object OrderPrioritiesContract {

    fun capabilitiesOf(
        program: Program,
        recipe: TrainingPlanRecipe? = program.sourceRecipe,
        options: TrainingOptions = TrainingOptions(),
    ): OrderPrioritiesCapabilities {
        val requested = options.orderPriorities
        val normalized = orderPointsFromBag(requested)
        val appliedBag = program.planOrderPriorities.orEmpty()
        val effectiveRecipe = recipe ?: program.sourceRecipe
        val ownership = when {
            effectiveRecipe != null && recipeFixesOrder(effectiveRecipe) -> OrderOwnership.RECIPE_FIXED
            program.hasNativeCuratedBlock() -> OrderOwnership.NATIVE_GENERATOR
            effectiveRecipe != null -> OrderOwnership.RECIPE_FIXED
            else -> OrderOwnership.NOT_MANAGED
        }
        val reasons = mutableListOf<String>()
        val status = when {
            normalized == null -> {
                reasons += "La bolsa de prioridades de orden no es válida: máximo 2 puntos por músculo, 5 en total y ningún punto negativo."
                OrderPrioritiesStatus.INVALID
            }
            normalized.isEmpty() -> OrderPrioritiesStatus.NOT_REQUESTED
            ownership == OrderOwnership.RECIPE_FIXED -> {
                reasons += "La receta de este programa fija su orden de ejercicios: la bolsa no se aplica en esta ruta y el orden de la receta se conserva intacto."
                OrderPrioritiesStatus.NOT_APPLIED_RECIPE_FIXED
            }
            ownership == OrderOwnership.NOT_MANAGED -> {
                reasons += "Este programa no fue generado por la ruta nativa: su orden no lo gestiona la bolsa de prioridades."
                OrderPrioritiesStatus.NOT_APPLIED_NOT_MANAGED
            }
            appliedBag.isEmpty() -> {
                reasons += "El programa no registra ninguna bolsa de orden aplicada al generarlo."
                OrderPrioritiesStatus.NOT_APPLIED_BAG_MISMATCH
            }
            appliedBag == normalized -> OrderPrioritiesStatus.APPLIED
            else -> {
                reasons += "La bolsa pedida no coincide con la bolsa aplicada al generar el programa."
                OrderPrioritiesStatus.NOT_APPLIED_BAG_MISMATCH
            }
        }
        return OrderPrioritiesCapabilities(
            requested = requested,
            normalizedRequested = normalized,
            appliedBag = appliedBag,
            ownership = ownership,
            status = status,
            reasons = reasons,
        )
    }

    /** true cuando la receta fija su orden (cualquier slot de autor). */
    internal fun recipeFixesOrder(recipe: TrainingPlanRecipe): Boolean =
        recipe.weeks.any { week -> week.days.any { day -> day.slots.any { it.source == SlotSource.AUTHOR } } }
}
