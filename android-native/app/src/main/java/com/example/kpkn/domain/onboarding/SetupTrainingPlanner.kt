package com.example.kpkn.domain.onboarding

import com.example.kpkn.data.programs.CatalogEntry
import com.example.kpkn.data.programs.CatalogLevel
import com.example.kpkn.data.programs.CatalogSource
import com.example.kpkn.data.programs.PersonalizedPlanCatalog
import com.example.kpkn.data.programs.PlanKind
import com.example.kpkn.data.programs.PublicationState
import com.example.kpkn.data.programs.TrainingCapability
import com.example.kpkn.data.programs.TrainingFocus
import com.example.kpkn.data.programs.TrainingReference
import com.example.kpkn.data.protocols.PROTOCOL_LIBRARY

data class SetupTrainingPlannerInput(
    val reference: TrainingReference?,
    val frequency: Int?,
    val equipment: Set<String>,
    val level: CatalogLevel,
    val focus: TrainingFocus,
    val protocolOnly: Boolean = false,
    val mixedTraining: Boolean = false,
    /**
     * Capacidades que la entrada debe declarar TODAS (prefiltro de Atleta completo:
     * `PlanGoalMatcher.requiredCapabilities(COMPLETE_ATHLETE)`). Vacío = sin filtro,
     * que es el comportamiento de los demás objetivos: se deciden por [reference].
     */
    val requiredCapabilities: Set<TrainingCapability> = emptySet(),
)

/**
 * One adapter for candidate choice; actual materialization remains in the
 * existing engines. Candidates are selected by their real discipline metadata,
 * never by their provenance: a template or protocol is not strength by default.
 *
 * Solo se consideran las entradas listadas (`listed = true`): los nativos históricos
 * ocultos por C.P2b (D2) nunca son candidatos, aunque `PersonalizedPlanCatalog.find`
 * los siga resolviendo para los programas ya activados.
 *
 * **Orden editorial (DEC-w2-06).** La lista sale ordenada por la ficha editorial de
 * cada entrada, no por el material ni por el id. Criterios, de más a menos importante:
 *
 *  1. la entrada declara la disciplina pedida (`reference`);
 *  2. el nivel de la persona está entre los `levels` de la ficha;
 *  3. la clase de plan: los PLAN completos van antes que las especializaciones, los
 *     complementos y las estructuras en blanco (ver [kindPosition]);
 *  4. el `rank` editorial ascendente (propios, originales, plantillas, métodos de
 *     tercero, históricos);
 *  5. el id, solo como desempate final y estable.
 *
 * El orden NO oculta nada por nivel ni por clase (DEC-w1-04): un método avanzado sigue
 * apareciendo para un principiante, simplemente detrás de lo que sí encaja con su nivel.
 * La adaptación KPKN de un original (DEC-w1-06) queda detrás de los propios y los
 * originales por su `rank`.
 */
object SetupTrainingPlanner {
    fun candidates(input: SetupTrainingPlannerInput): List<CatalogEntry> {
        val entries = PersonalizedPlanCatalog.listedEntries().filter { it.publication == PublicationState.PUBLISHED }
        return entries
            .filter { !input.protocolOnly || it.source == CatalogSource.PROTOCOL }
            .filter { it.supportedFrequencies.contains(input.frequency ?: it.supportedFrequencies.first) }
            // El enfoque solo parametriza a los nativos y a las plantillas simples. Los
            // protocolos y las plantillas con receta fija traen su semana escrita: el
            // enfoque no las cambia, así que no las esconde.
            .filter { it.supportedFocuses.contains(input.focus) || it.source == CatalogSource.PROTOCOL || it.hasFixedRecipeTemplate() }
            // Prefiltro de capacidades (Atleta completo): solo pasan las entradas que
            // declaran todas las requeridas. Con el conjunto vacío no filtra nada.
            .filter { it.capabilities.containsAll(input.requiredCapabilities) }
            // Native exercise compatibility is decided by SimpleCyclePersonalizer.
            // En las recetas fijas `requiredEquipment = general_gym` es metadata
            // GRUESA: no autoriza nada por sí sola ni bloquea con inventario
            // finito. Aquí solo se exigen requisitos de material EXPLÍCITOS
            // distintos de `general_gym`; el filtro real del material ocurre en
            // la materialización con `missingFixedRecipeEquipment`, que el
            // wizard invoca antes del preview (así ninguna receta fija se oculta
            // en vano ni se afirma compatible sin verificar).
            .filter { entry ->
                entry.source == CatalogSource.NATIVE ||
                    entry.requiredEquipment.none { requirement ->
                        requirement != "general_gym" && requirement !in input.equipment
                    }
            }
            // A strength + cardio goal only qualifies plans that schedule cardio;
            // the chosen reference then orders them instead of hiding them.
            .filter { entry -> !input.mixedTraining || entry.schedulesCardio }
            .filter { entry -> input.mixedTraining || input.reference == null || input.reference in entry.references }
            .sortedWith(
                compareBy<CatalogEntry>(
                    { if (input.reference != null && input.reference in it.references) 0 else 1 },
                    { if (input.level in it.levels) 0 else 1 },
                    { kindPosition(it.kind) },
                    { it.rank },
                    { it.id },
                ),
            )
    }

    fun fixedRecipeIds(): Set<String> = PROTOCOL_LIBRARY.map { it.id }.toSet()

    /** Plantilla con la semana escrita en una receta fija: el enfoque pedido no la modifica. */
    private fun CatalogEntry.hasFixedRecipeTemplate(): Boolean =
        source == CatalogSource.TEMPLATE && template?.recipe != null

    /**
     * Posición de cada clase de plan en el orden editorial. Es explícita (no el
     * `ordinal` del enum) para que reordenar o ampliar `PlanKind` no cambie en silencio
     * qué va primero: el `when` exhaustivo obliga a decidir la posición de una clase nueva.
     */
    private fun kindPosition(kind: PlanKind): Int = when (kind) {
        PlanKind.PLAN -> 0
        PlanKind.ESPECIALIZACION -> 1
        PlanKind.COMPLEMENTO -> 2
        PlanKind.ESTRUCTURA -> 3
    }
}
