package com.example.kpkn.domain.training

import com.example.kpkn.data.models.Program
import com.example.kpkn.domain.exercises.catalogv2.ExerciseConfigurationV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2

/**
 * Marcador devuelto por [missingFixedRecipeEquipment] cuando una configuración
 * del programa NO se puede verificar (sin id o ausente del catálogo actual).
 * Nunca se afirma compatibilidad sin datos: el conjunto resultante queda
 * distinto de vacío y la UI puede explicarlo con «material no catalogado».
 */
const val FIXED_RECIPE_UNVERIFIABLE_MATERIAL: String = "material no catalogado"

/** Peso corporal: no necesita material, nunca figura como faltante. */
private const val BODYWEIGHT_EQUIPMENT = "bodyweight"

/** Kind de maquinaria genérica: exige configuración exacta, nunca presencia. */
private const val MACHINE_EQUIPMENT = "machine"

/** Reclamo legacy «todo el gimnasio»: solo existe con perfil legacy (inventario null). */
private const val LEGACY_GENERAL_GYM = "general_gym"

/**
 * Dependencia de soporte/apoyo que una configuración expresa fuera de su
 * `equipmentId`. Es la ÚNICA expresión disponible en el proyecto: el catálogo
 * no publica rack/banco/soporte en `richMetadata.programming.requiredEquipment`
 * (todos sus valores son kinds de material), así que esta regla compartida es
 * la que materializa esos requisitos. La consumen el filtro real de
 * [SimpleCyclePersonalizer] y [missingFixedRecipeEquipment] para que la ruta
 * nativa y la receta fija no diverjan.
 */
internal fun supportDependencyFor(configurationId: String): String? = when (configurationId) {
    "pull_up__pronated__medium", "pull_up__supinated__medium" -> "pull_up_bar"
    "back_remo_invertido__default", "hams_curl_nordic_peso_corporal__default" -> "support"
    "curl_isquios_con_balon__default" -> "ball"
    else -> null
}

/**
 * Resultado tipado de la disponibilidad de material de una receta fija
 * (plantilla/protocolo) frente al equipo efectivo del usuario. Tipo puro para
 * la UI de altas (UI6): `available == true` solo cuando TODAS las
 * configuraciones se verificaron y su material está declarado.
 */
data class FixedRecipeEquipmentAvailability(
    /** true solo con verificación completa y todo el material declarado. */
    val available: Boolean,
    /** Kinds de material que la receta exige y el equipo efectivo no declara. */
    val missingEquipment: Set<String>,
    /** Configuraciones no verificables (id ausente del programa o del catálogo). */
    val unverifiableConfigurations: Set<String>,
)

/**
 * Guardia de material real para recetas fijas, consumida por el wizard antes
 * del preview (M2: `missingFixedRecipeEquipment(program, effectiveIds, catalog)`).
 *
 * Contrato:
 * - **No altera nada**: función pura sobre [Program]; la receta de autor
 *   (splits, dosis, orden) queda intacta por construcción.
 * - Revisa TODAS las `sessions` → `allExercises()` → `catalogConfigurationId`
 *   contra el catálogo ACTUAL: kind principal (`profile.equipmentId`), requisitos
 *   editoriales (`profile.richMetadata.programming.requiredEquipment`) y la
 *   dependencia de soporte compartida ([supportDependencyFor]).
 * - `bodyweight` nunca es faltante (no necesita material).
 * - Perfil legacy con `general_gym` (inventario null) → se conserva el
 *   comportamiento anterior: sin faltantes.
 * - Inventario finito (sin `general_gym`) → lo que la receta exige y no está
 *   declarado se devuelve como faltante: no se afirma compatibilidad ni se
 *   sustituye material. **Maquinaria por configuración EXACTA**: un requisito
 *   `machine` solo se satisface con su token `machine_config:<id>` (leg curl ≠
 *   chest press ≠ prensa); la presencia genérica `machine` del set y un
 *   `equipmentKind` sin `configurationId` no bastan. `cable` y `smith_machine`
 *   sí valen como estación multi-ejercicio declarada.
 * - Configuración no verificable → nunca afirma compatible
 *   ([FIXED_RECIPE_UNVERIFIABLE_MATERIAL] en el resultado combinado).
 *
 * Límite exacto: la verificación es de material declarado (maquinaria por su
 * configuración exacta); aquí NO se resuelven cargas ni cantidades finitas
 * (eso es `realizeWarmupLoads` + `WarmupFeasibility`, que además emparejan
 * `configurationId` ↔ ejercicio).
 */
fun fixedRecipeEquipmentAvailability(
    program: Program,
    equipment: Set<String>,
    catalog: ExerciseCatalogV2,
): FixedRecipeEquipmentAvailability {
    // Compatibilidad legacy: `general_gym` en el perfil significa inventario
    // null y el comportamiento anterior (todo compatible) se conserva.
    if (LEGACY_GENERAL_GYM in equipment) {
        return FixedRecipeEquipmentAvailability(available = true, missingEquipment = emptySet(), unverifiableConfigurations = emptySet())
    }
    val declared = equipment.mapTo(LinkedHashSet<String>()) { normalizeLegacyEquipmentKind(it) }
    val configurations = configurationsById(catalog)
    val missing = linkedSetOf<String>()
    val unverifiable = linkedSetOf<String>()
    program.macrocycles
        .flatMap { it.blocks }
        .flatMap { it.mesocycles }
        .flatMap { it.weeks }
        .flatMap { it.sessions }
        .flatMap { it.allExercises() }
        .forEach { exercise ->
            // Cardio y movilidad no cargan material de fuerza: otra puerta de
            // disponibilidad (el catálogo de cardio no es este guardia).
            if (exercise.cardioDetails != null) return@forEach
            if (exercise.mobilitySeries.isNotEmpty() || exercise.mobilityConfig != null) return@forEach
            val configurationId = exercise.catalogConfigurationId
                ?: exercise.canonicalExerciseId
                ?: exercise.exerciseId
                ?: exercise.exerciseDbId
            if (configurationId == null) {
                // Sin identidad verificable: no se afirma compatibilidad.
                unverifiable += exercise.id
                return@forEach
            }
            val configuration = configurations[configurationId]
            if (configuration == null) {
                unverifiable += configurationId
                return@forEach
            }
            val machineConfigDeclared = machineConfigToken(configurationId) in declared
            requirementsOf(configuration, configurationId).forEach { requirement ->
                val kind = normalizeLegacyEquipmentKind(requirement)
                if (kind == BODYWEIGHT_EQUIPMENT) return@forEach
                // Misma regla que el filtro del motor: la maquinaria exige la
                // configuración EXACTA declarada (`machine_config:<id>`); la
                // presencia genérica `machine` del set no aprueba nada
                // (leg curl ≠ chest press ≠ prensa) y `equipmentKind = machine`
                // sin `configurationId` tampoco. `general_gym` (perfil legacy)
                // ya salió por el atajo de arriba.
                if (kind == MACHINE_EQUIPMENT) {
                    if (!machineConfigDeclared) missing += kind
                    return@forEach
                }
                if (kind in declared) return@forEach
                missing += kind
            }
        }
    return FixedRecipeEquipmentAvailability(
        available = missing.isEmpty() && unverifiable.isEmpty(),
        missingEquipment = missing,
        unverifiableConfigurations = unverifiable,
    )
}

/**
 * Material faltante de una receta fija: kinds de material no declarados más el
 * marcador [FIXED_RECIPE_UNVERIFIABLE_MATERIAL] cuando algo no se pudo
 * verificar. Vacío = compatible con lo declarado; NO vacío = no se afirma
 * compatibilidad. Firma exacta consumida por el wizard (M2).
 */
fun missingFixedRecipeEquipment(
    program: Program,
    equipment: Set<String>,
    catalog: ExerciseCatalogV2,
): Set<String> {
    val availability = fixedRecipeEquipmentAvailability(program, equipment, catalog)
    if (availability.unverifiableConfigurations.isEmpty()) return availability.missingEquipment
    return availability.missingEquipment + FIXED_RECIPE_UNVERIFIABLE_MATERIAL
}

/** Kind principal + requisitos editoriales + dependencia de soporte, sin duplicados. */
private fun requirementsOf(
    configuration: ExerciseConfigurationV2,
    configurationId: String,
): List<String> = buildList {
    val primary = configuration.profile.equipmentId
    if (primary.isNotBlank()) add(primary)
    configuration.profile.richMetadata?.programming?.requiredEquipment?.forEach { entry ->
        if (entry.isNotBlank()) add(entry)
    }
    val support = supportDependencyFor(configurationId)
    if (support != null) add(support)
}

private fun configurationsById(catalog: ExerciseCatalogV2): Map<String, ExerciseConfigurationV2> =
    catalog.families
        .flatMap { it.definitions }
        .flatMap { it.configurations }
        .associateBy { it.id }
