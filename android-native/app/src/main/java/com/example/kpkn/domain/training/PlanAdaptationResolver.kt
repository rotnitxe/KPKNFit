package com.example.kpkn.domain.training

import com.example.kpkn.data.models.EquipmentAvailability
import com.example.kpkn.data.models.EquipmentCategory
import com.example.kpkn.data.models.LoadQuantityConvention
import com.example.kpkn.data.protocols.DayRecipe
import com.example.kpkn.data.protocols.KpknOperationalDefault
import com.example.kpkn.data.protocols.KpknOperationalDefaultScope
import com.example.kpkn.data.protocols.LoadBasis
import com.example.kpkn.data.protocols.PlanLoadReference
import com.example.kpkn.data.protocols.PlanLoadReferenceKind
import com.example.kpkn.data.protocols.PlanLoadReferenceState
import com.example.kpkn.data.protocols.PlanProvenance
import com.example.kpkn.data.protocols.PlanProvenanceClass
import com.example.kpkn.data.protocols.PlanSlotChange
import com.example.kpkn.data.protocols.SetRecipe
import com.example.kpkn.data.protocols.SlotRecipe
import com.example.kpkn.data.protocols.SlotRole
import com.example.kpkn.data.protocols.TrainingPlanRecipe
import com.example.kpkn.data.protocols.WeekRecipe
import com.example.kpkn.data.programs.CatalogLevel
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseConfigurationV2
import java.security.MessageDigest
import kotlin.math.abs
import kotlin.math.ceil

/**
 * Motivos cerrados de §15.2 que este resolver emite (subconjunto del paquete D).
 * La UI distingue así «falta confirmar material» de «esta receta no se puede
 * adaptar» y de «la base de carga no es representable».
 */
enum class PlanAdaptationReason {
    /** Sin alternativa curada viable para el slot (§13.4); el caller elige la estructura propia. */
    NO_VALID_SUBSTITUTION,
    /** Un aparato/soporte necesario fue negado explícitamente. */
    APPARATUS_ABSENT,
    /** Un aparato/soporte necesario falta por confirmar (nunca se niega sin respuesta). */
    APPARATUS_UNKNOWN,
    /** La configuración declarada no existe en el catálogo publicado. */
    UNRESOLVED_CONFIGURATION,
    /** La referencia de carga no aplica a esta configuración/convención/base de reps (§14.2). */
    LOAD_BASIS_UNREPRESENTABLE,
    /** El presupuesto de sesión elegido no cabe en el día más exigente. */
    TIME_BUDGET,
    /** El perfil pedido exige material/estructura que la receta no puede cumplir. */
    PROFILE_MISMATCH,
    /** El nivel declarado por la receta es superior al nivel del usuario. */
    LEVEL_UNSUITABLE,
}

/**
 * Perfil objetivo del candidato (§15.1 GOAL). `COMPLETE_ATHLETE` es el cuarto
 * valor aditivo del wizard; no reutiliza `TrainingReference`, que solo tiene
 * tres disciplinas y no expresa «Atleta completo».
 */
enum class PlanTargetProfile {
    POWERLIFTING,
    HYPERTROPHY,
    POWERBUILDING,
    COMPLETE_ATHLETE,
}

/** Petición pura de adaptación de UNA receta contra equipo/perfil/nivel/tiempo reales. */
data class PlanAdaptationRequest(
    val recipe: TrainingPlanRecipe,
    /** Salida del resolutor único [TrainingOptions.resolveEffectiveEquipment]. */
    val equipment: EffectiveEquipmentResult,
    /**
     * Disponibilidad declarada (§13.1). null = ruta legacy: un material no
     * declarado queda UNKNOWN (nunca se niega sin respuesta explícita).
     */
    val availability: EquipmentAvailability? = null,
    val catalog: ExerciseCatalogV2,
    val targetProfile: PlanTargetProfile? = null,
    /** Nivel del usuario (`BEGINNER`/`INTERMEDIATE`/`ADVANCED`); null = sin comprobar. */
    val level: CatalogLevel? = null,
    /** Minutos disponibles por sesión (§12.2); null = sin presupuesto declarado. */
    val sessionBudgetMinutes: Int? = null,
    /** Ocurrencia de semana (§14.4); solo afecta a la identidad derivada, no a la adaptación. */
    val weekOccurrence: Int = 1,
)

/**
 * Resultado tipado de [PlanAdaptationResolver.adapt] (§13.4/§15.2): una receta
 * derivada con sus cambios por slot, o un motivo estructurado de por qué esa
 * adaptación no es viable. Nunca se devuelve una receta silenciosamente
 * debilitada y nunca se muta la receta original.
 */
sealed class PlanAdaptationResult {
    data class Adapted(
        val recipe: TrainingPlanRecipe,
        val changes: List<PlanSlotChange>,
    ) : PlanAdaptationResult()

    data class NotViable(
        val reason: PlanAdaptationReason,
        val slotId: String? = null,
        val configurationId: String? = null,
        val detail: String = "",
        val missingRequirements: Set<String> = emptySet(),
        val requiredMinutes: Int? = null,
    ) : PlanAdaptationResult()
}

private typealias NotViable = PlanAdaptationResult.NotViable
private typealias Adapted = PlanAdaptationResult.Adapted

/** Veredicto tipado de material de UNA configuración contra el equipo efectivo. */
sealed class ConfigurationAvailability {
    /** Todo el material y los soportes de la configuración están acreditados. */
    data object Available : ConfigurationAvailability()

    /**
     * Falta material: [evidence] es ABSENT (negado/categoría sin ese ítem) o
     * UNKNOWN (falta confirmar); [missing] lista los requisitos concretos para
     * que la UI diga «falta confirmar hack» y no «no sé qué pasa».
     */
    data class Missing(
        val evidence: RequirementEvidence,
        val missing: Set<String>,
    ) : ConfigurationAvailability()

    /** Configuración ausente del catálogo publicado: nunca se afirma compatibilidad. */
    data object Unresolved : ConfigurationAvailability()
}

/** Nivel de la receta: `principiante`/`intermedio`/`avanzado` (§15.1). */
private fun recipeLevelOf(claimedLevel: String?): CatalogLevel? = when (claimedLevel?.trim()?.lowercase()) {
    "principiante", "beginner" -> CatalogLevel.BEGINNER
    "intermedio", "intermediate" -> CatalogLevel.INTERMEDIATE
    "avanzado", "advanced" -> CatalogLevel.ADVANCED
    else -> null
}

private fun CatalogLevel.rank(): Int = when (this) {
    CatalogLevel.BEGINNER -> 0
    CatalogLevel.INTERMEDIATE -> 1
    CatalogLevel.ADVANCED -> 2
}

/** Prescripción legible de UN slot para el informe de §13.4. */
fun prescriptionOf(slot: SlotRecipe): String {
    val working = slot.sets.filter { !it.isWarmup }
    if (working.isEmpty()) return "sin series de trabajo"
    val first = working.first()
    val reps = when {
        first.repsMin != null && first.repsMax != null -> "${first.repsMin}-${first.repsMax}"
        first.reps != null -> "${first.reps}"
        else -> "-"
    }
    val effort = when {
        first.percent != null -> "@${formatPercent(first.percent!!)}%"
        first.rir != null -> "RIR ${first.rir}"
        first.rpe != null -> "RPE ${first.rpe}"
        first.amrap -> "AMRAP"
        else -> "esfuerzo libre"
    }
    return "${working.size}×$reps $effort, descanso ${slot.restSeconds}s"
}

/** Porcentaje sin ceros colgantes: 32.5 → «32.5», 80.0 → «80». */
private fun formatPercent(value: Double): String {
    if (value % 1.0 == 0.0) return value.toLong().toString()
    val raw = value.toString()
    return raw.dropLastWhile { it == '0' }.dropLastWhile { it == '.' }
}

// ─── Sustituciones curadas (§13.4) ───────────────────────────────────────────

/** Candidato curado de sustitución: orden de prioridad, patrón y nota editorial. */
private data class CuratedCandidate(
    val configurationId: String,
    /** 1 = misma definición; 2 = alternativa curada que preserva el patrón; 3 = reserva material-libre. */
    val tier: Int,
    /** true = mismo patrón; false = cambio de patrón documentado en [note]. */
    val samePattern: Boolean,
    val note: String,
)

private fun c(id: String, tier: Int, samePattern: Boolean, note: String): CuratedCandidate =
    CuratedCandidate(id, tier, samePattern, note)

/** Reserva de tirón horizontal verificada: banda, invertido, gorilla y remo genérico. */
private fun rowCandidates(): List<CuratedCandidate> = listOf(
    c("back_remo_banda__default", 2, true, "Remo con banda: mismo patrón de tracción horizontal sin aparato de remo (§13.4)."),
    c("back_remo_invertido__default", 2, true, "Remo invertido: tracción horizontal con barra baja estable, sin máquina de remo."),
    c("back_remo_gorilla_mancuernas__dumbbells", 2, true, "Remo gorilla con mancuernas: tracción horizontal sin banco ni máquina."),
    c("conventional_row__dumbbells", 2, true, "Remo con mancuernas: mismo patrón de tracción horizontal sin aparato de remo."),
)

private fun squatToGoblet(): List<CuratedCandidate> = listOf(
    c("quads_sentadilla_copa__default", 2, true, "Sentadilla copa: dominante de rodilla alcanzable con mancuernas o peso corporal (§13.4)."),
    c("quads_sentadilla_sin_carga__default", 3, true, "Sentadilla sin carga: dominante de rodilla con peso corporal cuando no hay material."),
)

private fun pullUpToLatPulldown(): List<CuratedCandidate> = listOf(
    c("lat_pulldown__bilateral__cable", 2, true, "Jalón en polea bilateral: mismo patrón de tracción vertical sin barra de dominadas."),
    c("lat_pulldown__bilateral__machine", 2, true, "Jalón en máquina bilateral: mismo patrón de tracción vertical sin barra de dominadas."),
    c("conventional_row__dumbbells", 3, false, "Tracción vertical → horizontal con mancuernas: cambio de patrón documentado (§13.4)."),
    c("back_remo_banda__default", 3, false, "Tracción vertical → horizontal con banda: cambio de patrón documentado (§13.4)."),
)

private fun latPulldownToPullUp(): List<CuratedCandidate> = listOf(
    c("pull_up__pronated__medium", 2, true, "Dominadas pronación media: mismo patrón de tracción vertical con barra estable."),
    c("conventional_row__dumbbells", 3, false, "Tracción vertical → horizontal con mancuernas: cambio de patrón documentado (§13.4)."),
    c("back_remo_banda__default", 3, false, "Tracción vertical → horizontal con banda: cambio de patrón documentado (§13.4)."),
)

/**
 * Tabla curada §13.4 keyed by `configurationId.substringBefore("__")`
 * (definitionId publicado). Solo ids verificados en el catálogo
 * `v2-approved-2026-08-12-a`: nunca se inventa un id (regla STOP).
 *
 * Una entrada con SOLO tier 1 (o vacía) documenta que no hay alternativa
 * cruzada curada: si las variantes de la misma definición no están
 * disponibles el resultado es [PlanAdaptationReason.NO_VALID_SUBSTITUTION] y
 * el caller elige la estructura nativa (p. ej. extensión de cadera para isquios).
 */
private val CURATED_SUBSTITUTIONS: Map<String, List<CuratedCandidate>> = mapOf(
    "bench_press" to listOf(
        c("bench_press__dumbbells", 1, true, "Mancuernas en la misma banca: conserva el empuje y descarta la base RM/TM de barra (§13.4)."),
        c("floor_press__dumbbells", 2, true, "Press en suelo con mancuernas: empuje horizontal sin banca ni barra."),
        c("push_up__flat", 3, false, "Empuje con peso corporal: cambio de patrón documentado; se prescriben reps y RIR (§13.4)."),
    ),
    "quads_prensa_piernas" to squatToGoblet(),
    "quads_sentadilla_hack" to squatToGoblet(),
    "quads_sentadilla_hack_invertida_maquina" to squatToGoblet(),
    "quads_sentadilla_v_squat" to squatToGoblet(),
    "quads_sentadilla_v_squat_invertida_maquina" to squatToGoblet(),
    "quads_sentadilla_copa" to listOf(
        c("quads_sentadilla_sin_carga__default", 2, true, "Sentadilla sin carga: reserva de peso corporal cuando no hay mancuernas."),
    ),
    "high_bar_back_squat" to squatToGoblet(),
    "front_squat" to squatToGoblet(),
    // Curl femoral: SOLO variantes de la misma definición (mismo patrón de
    // flexión de rodilla). Un puente/frog pump es extensión de cadera y no
    // sustituye el curl, así que no figura como candidato.
    "lying_leg_curl" to emptyList(),
    "seated_leg_curl" to emptyList(),
    "standing_leg_curl" to emptyList(),
    "conventional_row" to rowCandidates(),
    "back_remo_banda" to rowCandidates(),
    "pendlay_row" to rowCandidates(),
    "t_bar_row" to rowCandidates(),
    "seal_row" to rowCandidates(),
    "chest_supported_row" to rowCandidates(),
    "back_band_pull_apart" to listOf(
        c("back_superman_suelo__default", 2, false, "Superman en suelo: extensores de columna; NO cuenta como remo/vuelo para volumen de espalda (§13.4)."),
    ),
    "pull_up" to pullUpToLatPulldown(),
    "lat_pulldown" to latPulldownToPullUp(),
    "military_press" to listOf(
        c("push_up__flat", 3, false, "Prensa vertical → empuje horizontal con peso corporal: cambio de patrón documentado (§13.4)."),
    ),
    "romanian_deadlift" to listOf(
        c("glutes_frog_pumps__default", 3, false, "Frog pump: extensión de cadera, no bisagra de cadera equivalente ni mismo 1RM (§13.4)."),
    ),
    "tren_superior_fondos" to listOf(
        c("triceps_fondos_entre_bancos__default", 2, true, "Fondos entre bancas: mismo patrón de extensión de codo con otro apoyo."),
        c("push_up__flat", 3, false, "Empuje con peso corporal: cambio de patrón documentado; se prescriben reps y RIR (§13.4)."),
    ),
    "triceps_fondos_entre_bancos" to listOf(
        c("tren_superior_fondos__default", 2, true, "Fondos en paralelas: mismo patrón de extensión de codo con otro apoyo."),
        c("push_up__flat", 3, false, "Empuve con peso corporal: cambio de patrón documentado; se prescriben reps y RIR (§13.4)."),
    ),
    // Altas M1 a M5 (B.S6): una técnica que ya es una configuración propia (agarre cerrado, pausa, inclinado) conserva las alternativas
    // curadas de la configuración base de la que antes era un parche (`técnica` + base), así que adaptar una receta sin el material
    // exacto cambia el ejercicio igual que antes y no deja el slot sin sustituto.
    "close_grip_bench_press" to listOf(
        c("bench_press__dumbbells", 2, true, "Press de banca con mancuernas: mismo patrón de empuje horizontal sin barra ni rack (§13.4)."),
        c("floor_press__dumbbells", 2, true, "Press en suelo con mancuernas: empuje horizontal sin banca ni barra."),
        c("push_up__flat", 3, false, "Empuje con peso corporal: cambio de patrón documentado; se prescriben reps y RIR (§13.4)."),
    ),
    "paused_back_squat" to squatToGoblet(),
    "close_grip_lat_pulldown" to latPulldownToPullUp(),
    "incline_biceps_curl" to listOf(
        c("biceps_curl_sentado_banco_plano__dumbbells", 2, true, "Curl sentado en banco plano con mancuernas: mismo patrón de flexión de codo sin el banco regulable."),
        c("hammer_curl__dumbbells", 2, true, "Curl martillo con mancuernas: mismo patrón de flexión de codo sin banco."),
    ),
)

/**
 * Gaps del catálogo publicado que bloquean la adaptación (regla STOP): la
 * reserva corporal documentada NO existe como id verificado, así que el slot
 * falla tipado en vez de inventar un id (se reporta al paquete de catálogo).
 */
private val CATALOG_GAP_NOTES: Map<String, String> = mapOf(
    "calf_raise" to "Sin variante de gemelo con peso corporal publicada (gap: calf_raise__bilateral__bodyweight); solo variantes de barra, máquina, cable y Smith.",
    "reverse_lunge" to "Sin zancada inversa con peso corporal publicada (gap: reverse_lunge__bodyweight); solo variantes de barra, mancuerna, kettlebell, cable y Smith.",
    "forward_lunge" to "Sin zancada con peso corporal publicada; solo variantes de barra, mancuerna, kettlebell, cable y Smith.",
    "walking_lunge" to "Sin zancada caminando con peso corporal publicada; solo variantes de barra, mancuerna, kettlebell, cable y Smith.",
    "lying_leg_curl" to "El curl femoral no tiene equivalencia de patrón: un puente o frog pump es extensión de cadera y no sustituye la flexión de rodilla (§13.4); el caller debe elegir una estructura propia de isquios.",
    "seated_leg_curl" to "El curl femoral no tiene equivalencia de patrón: un puente o frog pump es extensión de cadera y no sustituye la flexión de rodilla (§13.4); el caller debe elegir una estructura propia de isquios.",
    "standing_leg_curl" to "El curl femoral no tiene equivalencia de patrón: un puente o frog pump es extensión de cadera y no sustituye la flexión de rodilla (§13.4); el caller debe elegir una estructura propia de isquios.",
    "push_up" to "Sin elevación de manos publicada (gap: push_up__hands_elevated); se usa la variante plana o de rodillas.",
)

private const val MATERIAL_BODYWEIGHT = "bodyweight"
private const val MATERIAL_MACHINE = "machine"
private const val MATERIAL_CARDIO = "cardio"

private fun configurationsById(catalog: ExerciseCatalogV2): Map<String, ExerciseConfigurationV2> =
    catalog.families
        .flatMap { it.definitions }
        .flatMap { it.configurations }
        .associateBy { it.id }

/**
 * Material de UNA configuración contra el equipo efectivo. Reglas:
 * - `bodyweight` siempre acreditado; requisitos de soporte con el vocabulario
 *   curado [supportRequirementsFor] y la evidencia estructurada del resolver.
 * - Maquinaria SOLO por `machine_config:<id>` exacta: el kind genérico
 *   `machine` nunca aprueba una configuración concreta (leg curl ≠ prensa).
 * - Configuración ausente del catálogo → [ConfigurationAvailability.Unresolved].
 */
fun configurationAvailability(
    configurationId: String,
    equipment: EffectiveEquipmentResult,
    availability: EquipmentAvailability?,
    catalog: ExerciseCatalogV2,
): ConfigurationAvailability {
    val configuration = configurationsById(catalog)[configurationId]
        ?: return ConfigurationAvailability.Unresolved
    // Perfil legacy `general_gym` (solo para leer programas previos): sin faltantes.
    if ("general_gym" in equipment.tokens) return ConfigurationAvailability.Available
    val absent = linkedSetOf<String>()
    val unknown = linkedSetOf<String>()
    requirementsOf(configuration, configurationId).forEach { raw ->
        val kind = normalizeLegacyEquipmentKind(raw)
        when (evidenceOfKind(kind, configurationId, equipment, availability)) {
            RequirementEvidence.PRESENT -> Unit
            RequirementEvidence.ABSENT -> absent += kind
            RequirementEvidence.UNKNOWN -> unknown += kind
        }
    }
    if (absent.isNotEmpty()) return ConfigurationAvailability.Missing(RequirementEvidence.ABSENT, absent)
    if (unknown.isNotEmpty()) return ConfigurationAvailability.Missing(RequirementEvidence.UNKNOWN, unknown)
    return ConfigurationAvailability.Available
}

/** Kind principal + requisitos editoriales + requisitos de soporte (§13.2), sin duplicados. */
private fun requirementsOf(
    configuration: ExerciseConfigurationV2,
    configurationId: String,
): List<String> = buildList {
    val primary = configuration.profile.equipmentId
    if (primary.isNotBlank()) add(primary)
    configuration.profile.richMetadata?.programming?.requiredEquipment?.forEach { entry ->
        if (entry.isNotBlank()) add(entry)
    }
    addAll(supportRequirementsFor(configurationId))
}

private fun evidenceOfKind(
    kind: String,
    configurationId: String,
    equipment: EffectiveEquipmentResult,
    availability: EquipmentAvailability?,
): RequirementEvidence {
    if (kind == MATERIAL_BODYWEIGHT || kind == MATERIAL_CARDIO) return RequirementEvidence.PRESENT
    // Vocabulario de soportes: la evidencia estructurada ya distingue ausente de sin confirmar.
    if (kind in KNOWN_REQUIREMENTS) return equipment.requirements[kind] ?: RequirementEvidence.UNKNOWN
    if (kind == MATERIAL_MACHINE) {
        if (machineConfigToken(configurationId) in equipment.tokens) return RequirementEvidence.PRESENT
        if (availability != null) {
            if (configurationDeniedByAbsentKey(configurationId, availability)) return RequirementEvidence.ABSENT
            // MACHINES confirmada sin la configuración exacta → falta confirmarla
            // («falta confirmar leg curl»); categoría sin marcar → ausente.
            return if (EquipmentCategory.MACHINES in availability.categories) {
                RequirementEvidence.UNKNOWN
            } else {
                RequirementEvidence.ABSENT
            }
        }
        if (MATERIAL_MACHINE in equipment.tokens) return RequirementEvidence.UNKNOWN
        return if (hasConcreteOrigin(equipment)) RequirementEvidence.ABSENT else RequirementEvidence.UNKNOWN
    }
    if (kind in equipment.tokens) return RequirementEvidence.PRESENT
    if (availability != null) return RequirementEvidence.ABSENT
    return if (hasConcreteOrigin(equipment)) RequirementEvidence.ABSENT else RequirementEvidence.UNKNOWN
}

/** true si el resultado trae evidencia concreta (confirmada o inventario declarado). */
private fun hasConcreteOrigin(equipment: EffectiveEquipmentResult): Boolean =
    equipment.origins.values.any { origin ->
        origin == EffectiveEquipmentOrigin.CONFIRMED_APPARATUS ||
            origin == EffectiveEquipmentOrigin.CONFIRMED_SUPPORT ||
            origin == EffectiveEquipmentOrigin.CONFIRMED_CATEGORY ||
            origin == EffectiveEquipmentOrigin.DECLARED_INVENTORY
    }

// ─── Resolver de adaptación (§13.4/§15.2) ───────────────────────────────────

private sealed interface Pick {
    data class Substituted(val candidate: CuratedCandidate, val tier: Int) : Pick
    data class Failed(val error: NotViable) : Pick
}

private data class DayAdaptation(val day: DayRecipe?, val error: NotViable?)

/** Cambio de un slot junto con la etiqueta del día en que ocurrió; solo vive dentro de [PlanAdaptationResolver.adapt]. */
private data class DayChange(val dayLabel: String, val change: PlanSlotChange)

/**
 * Adaptador puro de UNA receta al equipo/perfil/nivel/tiempo reales (§13.4):
 * sustituciones curadas por orden (misma definición → alternativa de patrón →
 * reserva material-libre), cambios de prescripción completos por slot y
 * procedencia ADAPTED con sus cambios. Nunca muta la receta original y nunca
 * devuelve una receta silenciosamente debilitada: si un slot esencial no tiene
 * alternativa viable el resultado es un [PlanAdaptationResult.NotViable] con
 * motivo tipado.
 *
 * Orden de comprobación: perfil → nivel → slots/material → representabilidad
 * de la base de carga → presupuesto de tiempo.
 */
object PlanAdaptationResolver {
    fun adapt(request: PlanAdaptationRequest): PlanAdaptationResult {
        val recipe = request.recipe

        request.targetProfile?.let { profile -> profileMismatch(profile, recipe, request)?.let { return it } }

        val recipeLevel = recipeLevelOf(recipe.claimedLevel)
        val userLevel = request.level
        if (recipeLevel != null && userLevel != null && recipeLevel.rank() > userLevel.rank()) {
            return NotViable(
                PlanAdaptationReason.LEVEL_UNSUITABLE,
                detail = "La receta declara nivel '${recipe.claimedLevel}' por encima del nivel del usuario (${userLevel.name.lowercase()}).",
            )
        }

        val changes = mutableListOf<DayChange>()
        var failure: NotViable? = null
        // Las recetas de autor repiten el mismo día en todas sus semanas (PHUL:
        // 12 idénticas): cada día DISTINTO se adapta una sola vez, así el coste no
        // crece con las semanas y un cambio se registra una vez, no 12.
        val dayMemo = HashMap<DayRecipe, DayAdaptation>()
        val adaptedWeeks = recipe.weeks.map { week ->
            if (failure != null) return@map week
            week.copy(
                days = week.days.map { day ->
                    if (failure != null) return@map day
                    val adaptation = dayMemo.getOrPut(day) { adaptDay(day, request, changes) }
                    if (adaptation.error != null) {
                        failure = adaptation.error
                        day
                    } else {
                        adaptation.day ?: day
                    }
                },
            )
        }
        failure?.let { return it }

        budgetViolation(adaptedWeeks, request)?.let { return it }

        if (changes.isEmpty()) return Adapted(recipe, emptyList())

        // Una entrada por (slot, desde, hacia) con el detalle por día (ver mergeChangesAcrossDays).
        val uniqueChanges = mergeChangesAcrossDays(changes)
        val changedConfigurations = uniqueChanges.mapNotNull { it.fromConfigurationId }.toSet()
        val adapted = recipe.copy(
            id = adaptedRecipeId(recipe, uniqueChanges),
            weeks = adaptedWeeks,
            liftSlots = recipe.liftSlots.filterValues { it !in changedConfigurations },
            provenance = adaptedProvenance(recipe, uniqueChanges),
        )
        return Adapted(adapted, uniqueChanges)
    }

    /**
     * Comprueba material de todas las configuraciones de la receta sin
     * adaptarla: sirve a la UI para explicar qué falta antes de proponer cambios.
     */
    fun missingMaterialOf(
        configurationIds: List<String>,
        equipment: EffectiveEquipmentResult,
        availability: EquipmentAvailability?,
        catalog: ExerciseCatalogV2,
    ): Map<String, ConfigurationAvailability> = configurationIds.distinct().associateWith { id ->
        configurationAvailability(id, equipment, availability, catalog)
    }

    private fun configurationAvailability(
        configurationId: String,
        request: PlanAdaptationRequest,
    ): ConfigurationAvailability = configurationAvailability(
        configurationId = configurationId,
        equipment = request.equipment,
        availability = request.availability,
        catalog = request.catalog,
    )

    private fun profileMismatch(
        profile: PlanTargetProfile,
        recipe: TrainingPlanRecipe,
        request: PlanAdaptationRequest,
    ): NotViable? = when (profile) {
        PlanTargetProfile.POWERLIFTING -> {
            val tokens = request.equipment.tokens
            val requirements = request.equipment.requirements
            val blanket = "general_gym" in tokens
            val missing = buildSet {
                if (!blanket && "barbell" !in tokens) add("barbell")
                if (!blanket && requirements[REQUIREMENT_BENCH] != RequirementEvidence.PRESENT) add(REQUIREMENT_BENCH)
                if (!blanket && requirements[REQUIREMENT_RACK] != RequirementEvidence.PRESENT) add(REQUIREMENT_RACK)
            }
            if (missing.isEmpty()) {
                null
            } else {
                NotViable(
                    PlanAdaptationReason.PROFILE_MISMATCH,
                    detail = "El perfil de powerlifting exige barra, banca y rack acreditados para los lifts de competición.",
                    missingRequirements = missing,
                )
            }
        }
        PlanTargetProfile.COMPLETE_ATHLETE -> {
            val hasCardio = recipe.weeks.flatMap { it.days }.any { it.cardioBlocks.isNotEmpty() }
            if (hasCardio) {
                null
            } else {
                NotViable(
                    PlanAdaptationReason.PROFILE_MISMATCH,
                    detail = "El perfil «Atleta completo» exige bloques de cardio reales en la receta (§11.4/§14.1).",
                )
            }
        }
        PlanTargetProfile.HYPERTROPHY, PlanTargetProfile.POWERBUILDING -> null
    }

    private fun adaptDay(
        day: DayRecipe,
        request: PlanAdaptationRequest,
        changes: MutableList<DayChange>,
    ): DayAdaptation {
        val essential = day.minimumDose?.essentialSlotIds.orEmpty().toSet()
        val kept = mutableListOf<SlotRecipe>()
        for (slot in day.slots) {
            val originalId = slot.lift.configurationId
            when (val availability = configurationAvailability(originalId, request)) {
                ConfigurationAvailability.Unresolved -> return DayAdaptation(
                    day = null,
                    error = NotViable(
                        PlanAdaptationReason.UNRESOLVED_CONFIGURATION,
                        slotId = slot.id,
                        configurationId = originalId,
                        detail = "La configuración '$originalId' no existe en el catálogo publicado: nunca se afirma compatibilidad.",
                    ),
                )
                ConfigurationAvailability.Available -> {
                    loadIssue(slot)?.let { issue ->
                        return DayAdaptation(
                            day = null,
                            error = NotViable(
                                PlanAdaptationReason.LOAD_BASIS_UNREPRESENTABLE,
                                slotId = slot.id,
                                configurationId = originalId,
                                detail = issue,
                            ),
                        )
                    }
                    kept += slot
                }
                is ConfigurationAvailability.Missing -> {
                    if (slot.isCompetitionLift) {
                        return DayAdaptation(
                            day = null,
                            error = NotViable(
                                PlanAdaptationReason.NO_VALID_SUBSTITUTION,
                                slotId = slot.id,
                                configurationId = originalId,
                                detail = "Lift de competición '$originalId' sin sustitución posible; material no acreditado (${availability.missing.joinToString()}).",
                                missingRequirements = availability.missing,
                            ),
                        )
                    }
                    if (availability.evidence == RequirementEvidence.UNKNOWN) {
                        return DayAdaptation(
                            day = null,
                            error = NotViable(
                                PlanAdaptationReason.APPARATUS_UNKNOWN,
                                slotId = slot.id,
                                configurationId = originalId,
                                detail = "Falta confirmar material para '$originalId': ${availability.missing.joinToString()}.",
                                missingRequirements = availability.missing,
                            ),
                        )
                    }
                    if (slot.role == SlotRole.SPEED) {
                        // §10.3/§14.2: la potencia va ligada a la carga de trabajo de 3-5
                        // reps del MISMO ejercicio pesado. Sustituirla reescribiría sus
                        // 6×3 en series de RIR y rompería esa base (la «velocidad» de un
                        // ejercicio distinto o de peso corporal no es la del autor): no
                        // hay sustitución equivalente y se ofrece el plan propio.
                        return DayAdaptation(
                            day = null,
                            error = NotViable(
                                PlanAdaptationReason.NO_VALID_SUBSTITUTION,
                                slotId = slot.id,
                                configurationId = originalId,
                                detail = "La potencia (SPEED) '${slot.id}' depende de la carga de trabajo de 3-5 reps de '$originalId' " +
                                    "y no tiene sustitución equivalente con el material declarado (falta: ${availability.missing.joinToString()}).",
                                missingRequirements = availability.missing,
                            ),
                        )
                    }
                    when (val pick = pickCandidate(slot, availability, request)) {
                        is Pick.Substituted -> {
                            val adaptedSlot = applySubstitution(slot, pick, request)
                            changes += DayChange(day.label, changeFor(slot, adaptedSlot, pick, request))
                            kept += adaptedSlot
                        }
                        is Pick.Failed -> {
                            val droppable = slot.role == SlotRole.T3_ACCESSORY &&
                                slot.id !in essential &&
                                pick.error.reason in setOf(
                                    PlanAdaptationReason.NO_VALID_SUBSTITUTION,
                                    PlanAdaptationReason.APPARATUS_ABSENT,
                                )
                            if (droppable) {
                                changes += DayChange(day.label, dropChange(slot, pick.error))
                            } else {
                                return DayAdaptation(day = null, error = pick.error)
                            }
                        }
                    }
                }
            }
        }
        return DayAdaptation(day.copy(slots = kept), null)
    }

    /**
     * Orden §13.4: misma definición (tier 1) → alternativa curada de patrón
     * (tier 2) → reserva material-libre (tier 3). El primer candidato con todo
     * el material acreditado gana; si no hay alternativa cruzada curada el
     * motivo es [PlanAdaptationReason.NO_VALID_SUBSTITUTION].
     */
    private fun pickCandidate(
        slot: SlotRecipe,
        missing: ConfigurationAvailability.Missing,
        request: PlanAdaptationRequest,
    ): Pick {
        val originalId = slot.lift.configurationId
        val definitionId = originalId.substringBefore("__")
        val curated = CURATED_SUBSTITUTIONS[definitionId].orEmpty()
        val tier1 = (curated.filter { it.tier == 1 } + sameDefinitionCandidates(definitionId, originalId, request.catalog))
            .distinctBy { it.configurationId }
            .filter { it.configurationId != originalId }
        val tier2 = curated.filter { it.tier == 2 }
        val tier3 = curated.filter { it.tier == 3 }
        val note = CATALOG_GAP_NOTES[definitionId]

        listOf(tier1, tier2, tier3).forEachIndexed { index, candidates ->
            candidates.forEach { candidate ->
                if (configurationAvailability(candidate.configurationId, request) is ConfigurationAvailability.Available) {
                    return Pick.Substituted(candidate, index + 1)
                }
            }
        }

        val missingKinds = missing.missing.joinToString()
        val detail = buildString {
            append("Sin alternativa viable para '$originalId' con el material declarado (falta: $missingKinds). ")
            append(note ?: "El caller debe elegir una estructura nativa equivalente (§13.4).")
        }
        if (tier2.isEmpty() && tier3.isEmpty()) {
            return Pick.Failed(
                NotViable(
                    PlanAdaptationReason.NO_VALID_SUBSTITUTION,
                    slotId = slot.id,
                    configurationId = originalId,
                    detail = detail,
                    missingRequirements = missing.missing,
                ),
            )
        }
        var sawAbsent = false
        var sawUnknown = false
        var sawUnresolved = false
        (tier2 + tier3 + tier1).forEach { candidate ->
            when (val candidateAvailability = configurationAvailability(candidate.configurationId, request)) {
                is ConfigurationAvailability.Missing ->
                    if (candidateAvailability.evidence == RequirementEvidence.ABSENT) {
                        sawAbsent = true
                    } else {
                        sawUnknown = true
                    }
                ConfigurationAvailability.Unresolved -> sawUnresolved = true
                ConfigurationAvailability.Available -> Unit
            }
        }
        val reason = when {
            sawAbsent -> PlanAdaptationReason.APPARATUS_ABSENT
            sawUnknown -> PlanAdaptationReason.APPARATUS_UNKNOWN
            sawUnresolved -> PlanAdaptationReason.UNRESOLVED_CONFIGURATION
            else -> PlanAdaptationReason.NO_VALID_SUBSTITUTION
        }
        return Pick.Failed(
            NotViable(
                reason,
                slotId = slot.id,
                configurationId = originalId,
                detail = detail,
                missingRequirements = missing.missing,
            ),
        )
    }

    /** Variantes de la MISMA definición publicadas (tier 1), en orden de catálogo. */
    private fun sameDefinitionCandidates(
        definitionId: String,
        originalId: String,
        catalog: ExerciseCatalogV2,
    ): List<CuratedCandidate> {
        val definitions = catalog.families.flatMap { it.definitions }
        val variants = definitions.firstOrNull { it.id == definitionId }?.configurations?.map { it.id }
            ?: definitions.flatMap { it.configurations }.map { it.id }
                .filter { it.substringBefore("__") == definitionId }
        return variants
            .filter { it != originalId }
            .map { id -> c(id, 1, true, "Misma definición '$definitionId': variante del mismo patrón de movimiento (§13.4 tier 1).") }
    }

    /**
     * Sustitución completa: configuración nueva, sin lifts de competición ni
     * herencia RM/TM de barra, sin porcentajes/top-set/calentamientos de % y
     * con la prescripción de esfuerzo reescrita para la nueva configuración.
     * El descanso es el prescrito por la receta, elevado al piso H9 de su rol si
     * la nueva configuración lo exige ([substitutedRestSeconds]).
     * Los sets originales nunca se mutan.
     */
    private fun applySubstitution(slot: SlotRecipe, pick: Pick.Substituted, request: PlanAdaptationRequest): SlotRecipe {
        val tier = pick.tier
        val rir = if (request.level == CatalogLevel.BEGINNER) 3 else 2
        val rest = substitutedRestSeconds(slot, pick.candidate.configurationId, request.catalog)
        val working = slot.sets.filter { !it.isWarmup }
        val newSets = working.map { set -> adaptSet(set, tier, rir) }
        return slot.copy(
            lift = slot.lift.copy(configurationId = pick.candidate.configurationId, liftSlot = null),
            sets = newSets,
            restSeconds = rest,
            isCompetitionLift = false,
            explicitReference = null,
            // Solo tier 1 conserva la técnica: en un ejercicio distinto el chip
            // de ejecución deja de describir el movimiento.
            technique = if (tier == 1) slot.technique else null,
            isUnilateral = if (tier == 1) slot.isUnilateral else false,
        )
    }

    private fun adaptSet(set: SetRecipe, tier: Int, rir: Int): SetRecipe {
        var reps = set.reps
        var min = set.repsMin
        var max = set.repsMax
        when (tier) {
            2 -> {
                reps = 8
                min = 8
                max = 12
            }
            3 -> {
                reps = 8
                min = 8
                max = 15
            }
            else -> if (reps == null && min == null && max == null) {
                reps = 8
                min = 8
                max = 12
            }
        }
        return if (set.amrap) {
            SetRecipe(
                reps = reps,
                repsMin = min,
                repsMax = max,
                percent = null,
                rir = null,
                rpe = null,
                amrap = true,
                isTopSet = false,
                loadBasis = LoadBasis.RPE,
                isWarmup = false,
                reference = null,
            )
        } else {
            SetRecipe(
                reps = reps,
                repsMin = min,
                repsMax = max,
                percent = null,
                rir = rir,
                rpe = (10 - rir).toDouble(),
                amrap = false,
                isTopSet = false,
                loadBasis = LoadBasis.RPE,
                isWarmup = false,
                reference = null,
            )
        }
    }

    /**
     * Descanso de un slot sustituido (§12.2/§14.3). Sustituir el ejercicio no
     * cambia el ROL del slot y H9 (HARD) fija un piso por rol: T1 ≥ 180 s,
     * T2/técnica ≥ 120 s, T3 compuesto ≥ 90 s. Cada tier reescribía antes un
     * descanso fijo (120/90 s), de modo que un T1 o un T2 sustituido quedaba
     * bajo su piso y el plan se rechazaba con COMPOSITION (H9) por un defecto
     * del adaptador, no por material insuficiente. Ahora se conserva el
     * descanso prescrito por la receta (una sustitución cambia lo mínimo,
     * §13.4) y solo se eleva al piso cuando la nueva configuración lo exige;
     * nunca se baja («las recetas no se hacen caber bajando descansos», §12.2).
     */
    private fun substitutedRestSeconds(slot: SlotRecipe, configurationId: String, catalog: ExerciseCatalogV2): Int =
        maxOf(slot.restSeconds, restFloorSeconds(slot.role, configurationId, catalog))

    /**
     * Piso H9 del rol con la configuración nueva. Usa la misma clasificación de
     * aislamiento que `SessionCompositionPolicy.checkH9` (familia por
     * `movementPatternId`, `articulationType` e id) y `heavy = false`:
     * [adaptSet] elimina todo porcentaje, así que ninguna serie sustituida
     * cuenta como «pesada» para H9.
     */
    private fun restFloorSeconds(role: SlotRole, configurationId: String, catalog: ExerciseCatalogV2): Int {
        val profile = configurationsById(catalog)[configurationId]?.profile
        val isolation = CompositionTaxonomy.isIsolation(
            family = CompositionTaxonomy.familyOf(profile?.movementPatternId),
            articulationType = profile?.articulationType?.name,
            configurationId = configurationId,
        )
        return SessionCompositionPolicy.minimumRestSeconds(role = role, heavy = false, isolation = isolation)
    }

    private fun changeFor(
        before: SlotRecipe,
        after: SlotRecipe,
        pick: Pick.Substituted,
        request: PlanAdaptationRequest,
    ): PlanSlotChange {
        val floor = restFloorSeconds(before.role, after.lift.configurationId, request.catalog)
        val restNote = if (after.restSeconds > before.restSeconds) {
            "Descanso elevado de ${before.restSeconds} s a ${after.restSeconds} s: piso H9 del rol ${before.role.name} " +
                "con la nueva configuración (§12.2/§14.3)."
        } else {
            "Descanso conservado en ${after.restSeconds} s (piso H9 del rol ${before.role.name}: $floor s)."
        }
        return PlanSlotChange(
            slotId = before.id,
            fromConfigurationId = before.lift.configurationId,
            toConfigurationId = after.lift.configurationId,
            reason = pick.candidate.note,
            samePattern = pick.candidate.samePattern,
            differences = "Material/soporte y base de carga distintos; series y rango de reps reescritos para la nueva configuración. $restNote",
            prescriptionBefore = prescriptionOf(before),
            prescriptionAfter = prescriptionOf(after),
            loadReferenceKept = if (hasExplicitReference(before)) false else null,
        )
    }

    private fun dropChange(slot: SlotRecipe, error: NotViable): PlanSlotChange = PlanSlotChange(
        slotId = slot.id,
        fromConfigurationId = slot.lift.configurationId,
        toConfigurationId = null,
        reason = "Slot descartado en la adaptación: series a redistribuir por el fitter (§13.4). ${error.detail}",
        samePattern = false,
        differences = "Slot retirado del día; el volumen no se reemplaza en silencio.",
        prescriptionBefore = prescriptionOf(slot),
        prescriptionAfter = "sin series (a redistribuir por el fitter)",
        loadReferenceKept = if (hasExplicitReference(slot)) false else null,
    )

    private fun hasExplicitReference(slot: SlotRecipe): Boolean =
        slot.explicitReference != null || slot.sets.any { it.reference != null }

    /**
     * Una regla de sustitución es la misma en cada semana en que reaparece su
     * slot (los días con RIR distinto por semana generan la misma regla con otra
     * prescripción), y una receta puede repetir el MISMO slot en días distintos
     * (PHAT: hack, ext, curl-lying...). Se registra UNA entrada por
     * (slot, desde, hacia), con la prescripción de la primera ocurrencia.
     *
     * No se emiten entradas por (día, slot): [PlanSlotChange] no tiene
     * coordenada de día, así que dos entradas con el mismo `slotId` y las mismas
     * configuraciones serían indistinguibles (el resumen de procedencia las
     * mostraría repetidas e inflaría el contador de cambios) y añadir un campo
     * cambiaría el contrato de procedencia compartido. El detalle por día se
     * conserva dentro de `differences`: qué días afecta y la prescripción de
     * cada uno. La variación por semana (RIR 2 → 1) sigue fundida en la
     * primera semana.
     */
    private fun mergeChangesAcrossDays(recorded: List<DayChange>): List<PlanSlotChange> =
        recorded
            .groupBy { listOf(it.change.slotId, it.change.fromConfigurationId, it.change.toConfigurationId) }
            .values
            .map { occurrences ->
                val first = occurrences.first().change
                val days = occurrences.distinctBy { it.dayLabel }
                first.copy(differences = listOfNotNull(first.differences, dayScopeNote(days)).joinToString(" "))
            }

    private fun dayScopeNote(days: List<DayChange>): String =
        if (days.size == 1) {
            "Día: «${days.single().dayLabel}»."
        } else {
            "Aparece en ${days.size} días: " + days.joinToString("; ") { day ->
                "«${day.dayLabel}» ${day.change.prescriptionBefore.orEmpty()} → ${day.change.prescriptionAfter.orEmpty()}"
            } + "."
        }

    /**
     * Representabilidad de la base de carga (§14.2): una referencia solo aplica
     * a SU configuración y SU convención; nunca se transfiere barra → mancuernas
     * ni se intercambia lastre con asistencia.
     */
    private fun loadIssue(slot: SlotRecipe): String? {
        val declared = slot.explicitReference
        val declaredConfiguration = declared?.configurationId
        if (declaredConfiguration != null && declaredConfiguration != slot.lift.configurationId) {
            return "Referencia del slot declarada para '$declaredConfiguration' y el slot ejecuta '${slot.lift.configurationId}'."
        }
        slot.sets.forEach { set ->
            val reference = set.reference ?: return@forEach
            if (reference.configurationId != slot.lift.configurationId) {
                return "Referencia de '${reference.configurationId}' aplicada a '${slot.lift.configurationId}': solo aplica a la misma configuración (§14.2)."
            }
            val declaredConvention = declared?.quantityConvention ?: LoadQuantityConvention.UNSPECIFIED
            if (reference.quantityConvention != LoadQuantityConvention.UNSPECIFIED &&
                declaredConvention != LoadQuantityConvention.UNSPECIFIED &&
                reference.quantityConvention != declaredConvention
            ) {
                return "Convención '${reference.quantityConvention}' declarada como '$declaredConvention': lastre y asistencia no se intercambian (§14.2)."
            }
        }
        return null
    }

    /** Presupuesto de tiempo por día (misma medida que H6) + minutos de cardio reales. */
    private fun budgetViolation(
        weeks: List<WeekRecipe>,
        request: PlanAdaptationRequest,
    ): NotViable? {
        val budget = request.sessionBudgetMinutes ?: return null
        weeks.forEach { week ->
            week.days.forEach { day ->
                val seconds = day.slots.sumOf { slot ->
                    slot.sets.count { !it.isWarmup } * (slot.restSeconds + 45).toDouble()
                }
                val cardioMinutes = day.cardioBlocks.sumOf { it.details.effectiveDurationSeconds() } / 60.0
                val required = ceil(seconds / 60.0 + cardioMinutes).toInt()
                if (required > budget) {
                    return NotViable(
                        PlanAdaptationReason.TIME_BUDGET,
                        detail = "El día '${day.label}' necesita ~$required min y el presupuesto es de $budget min (§12.2).",
                        requiredMinutes = required,
                    )
                }
            }
        }
        return null
    }

    private fun adaptedRecipeId(recipe: TrainingPlanRecipe, changes: List<PlanSlotChange>): String {
        val tuples = changes
            .map { change ->
                listOf(
                    change.slotId,
                    change.fromConfigurationId.orEmpty(),
                    change.toConfigurationId.orEmpty(),
                    change.reason,
                ).joinToString(",")
            }
            .sorted()
        val payload = "${recipe.id}|${recipe.contentVersion}|" + tuples.joinToString(";")
        return "${recipe.id}~adapted-" + sha256Hex(payload).take(8)
    }

    private fun adaptedProvenance(recipe: TrainingPlanRecipe, changes: List<PlanSlotChange>): PlanProvenance {
        val parent = recipe.provenance
        val base = parent ?: PlanProvenance()
        // Una receta que YA es una adaptación (p. ej. `adapted:phul-kpkn-r1`) sigue
        // citando a su ORIGINAL como padre: la cadena de procedencia no pasa a
        // «adaptación de una adaptación» por haber sustituido un slot más.
        val inheritedParent = parent?.takeIf { it.category == PlanProvenanceClass.ADAPTED && it.parentId != null }
        return base.copy(
            category = PlanProvenanceClass.ADAPTED,
            parentId = inheritedParent?.parentId ?: recipe.id,
            parentRevision = if (inheritedParent != null) inheritedParent.parentRevision else parent?.revision,
            recipeId = parent?.recipeId ?: recipe.id,
            slotChanges = base.slotChanges + changes,
            operationalDefaults = if (changes.isEmpty()) {
                base.operationalDefaults
            } else {
                base.operationalDefaults + KpknOperationalDefault(
                    KpknOperationalDefaultScope.INITIAL_CHOICE,
                    "Elección inicial de adaptación por slot (§13.4): material, dosis y base de carga reescritos para el equipo declarado.",
                )
            },
        )
    }

    private fun sha256Hex(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> (byte.toInt() and 0xFF).toString(16).padStart(2, '0') }
}

// ─── Resolutor de carga (§14.2 / PHAT §14.2) ────────────────────────────────

/** Rango de reps del trabajo observado que alimenta la carga SPEED de PHAT. */
private const val SPEED_REP_MIN = 3
private const val SPEED_REP_MAX = 5

/**
 * Resultado tipado de la resolución de UNA carga (§14.2): resuelta sobre su
 * base, pendiente de elegir en entrenamiento (null ≠ 0 kg) o no representable
 * para esta configuración/convención/base de reps.
 */
sealed class PlanLoadResolution {
    data class Resolved(
        /** Carga objetivo (para SPEED, el extremo inferior del rango). */
        val loadKg: Double,
        /** Rango vivo visible para el usuario; nunca se colapsa a un único kg inventado. */
        val targetRange: ClosedFloatingPointRange<Double>,
        /** Etiqueta de la base: «carga de trabajo de 3-5 reps», «1RM», etc. */
        val basisLabel: String,
        /** Carga base de la que parte el cálculo (1RM, TM o trabajo observado). */
        val baseLoadKg: Double,
        val sourceReference: PlanLoadReference,
        val quantityConvention: LoadQuantityConvention,
    ) : PlanLoadResolution()

    data class Pending(
        val detail: String,
        val targetRir: Int? = null,
        val repRange: IntRange? = null,
    ) : PlanLoadResolution()

    data class Unrepresentable(
        val reason: PlanAdaptationReason = PlanAdaptationReason.LOAD_BASIS_UNREPRESENTABLE,
        val detail: String = "",
    ) : PlanLoadResolution()
}

/**
 * Serie de trabajo de 3-5 reps OBSERVADA en el slot pesado enlazado (PHAT
 * §14.2): la única fuente válida de una base de velocidad.
 */
data class ObservedWorkingSet(
    val configurationId: String,
    val quantityConvention: LoadQuantityConvention = LoadQuantityConvention.UNSPECIFIED,
    val loadKg: Double,
    val reps: Int,
    val sourceSlotId: String? = null,
    val sourceProgramId: String? = null,
    val sourceRunId: String? = null,
    val sourceWeekOccurrence: Int? = null,
    val capturedAtMs: Long? = null,
)

/** Carga elegida explícitamente por el usuario y su cambio de prescripción. */
data class ExplicitLightLoad(
    val reference: PlanLoadReference,
    val change: PlanSlotChange,
)

/** Redondeo de una carga pedida dentro de un rango contra material real. */
sealed class LoadRounding {
    /** Hay material realizable dentro del rango: el más cercano al límite inferior. */
    data class Realized(val loadKg: Double) : LoadRounding()

    /** Sin inventario declarado: el rango sigue visible en la prescripción. */
    data object RangeKept : LoadRounding()

    /** Nada alcanzable dentro del rango: se reporta la discrepancia para adaptar. */
    data class Discrepancy(val nearestKg: Double) : LoadRounding()
}

/**
 * Resolutor de carga por configuración y convención (§14.2) — 3 call sites:
 * inicio de workout, registro de una serie pesada enlazada y reconstrucción de
 * la semana pendiente. Tres reglas duras:
 *
 * 1. Solo aplica a la MISMA `configurationId` y la MISMA `quantityConvention`
 *    (con `UNSPECIFIED` de destino se acepta la convención de la referencia);
 *    nunca hay ×2/÷2 implícitos entre convenciones.
 * 2. Una carga de trabajo de 3-5 reps NUNCA se guarda en `reference1RM` (que
 *    sigue significando 1RM) y nunca se deriva de un TM/1RM: para SPEED solo
 *    vale un [PlanLoadReferenceKind.OBSERVED_WORKING_SET] observado.
 * 3. Sin referencia el peso queda [PlanLoadResolution.Pending]: null ≠ 0 kg ni NaN.
 */
object PlanLoadResolver {
    /** Fracción por defecto de la serie observada 3-5 reps para SPEED (65 %). */
    const val SPEED_DEFAULT_FRACTION = 0.65

    /** Fracción superior del rango SPEED (70 %). */
    const val SPEED_MAX_FRACTION = 0.70

    /** Etiqueta visible de la base SPEED: «carga de trabajo de 3-5 reps». */
    const val SPEED_BASIS_LABEL = "carga de trabajo de 3-5 reps"

    /** Rango SPEED [65 %, 70 %] de la carga de trabajo observada. */
    fun speedRange(baseLoadKg: Double): ClosedFloatingPointRange<Double> {
        require(baseLoadKg.isFinite() && baseLoadKg > 0.0) { "baseLoadKg debe ser un kg positivo finito" }
        return (baseLoadKg * SPEED_DEFAULT_FRACTION)..(baseLoadKg * SPEED_MAX_FRACTION)
    }

    /**
     * Carga SPEED de PHAT: 65-70 % de la última serie de trabajo de 3-5 reps
     * completada en el slot pesado enlazado de MISMA configuración/convención.
     * Con [observation] valida la ventana de reps; sin ella usa [reference];
     * sin ninguna → [PlanLoadResolution.Pending].
     */
    fun resolveSpeedLoad(
        reference: PlanLoadReference?,
        targetConfigurationId: String,
        targetConvention: LoadQuantityConvention,
        observation: ObservedWorkingSet? = null,
        pendingRir: Int? = null,
        pendingRepRange: IntRange? = null,
    ): PlanLoadResolution {
        if (observation != null) {
            conventionIssue(observation.quantityConvention, targetConvention)?.let {
                return PlanLoadResolution.Unrepresentable(detail = it)
            }
            if (observation.configurationId != targetConfigurationId) {
                return PlanLoadResolution.Unrepresentable(
                    detail = "Serie observada de '${observation.configurationId}' para '$targetConfigurationId': solo aplica a la misma configuración (§14.2).",
                )
            }
            if (observation.reps < SPEED_REP_MIN || observation.reps > SPEED_REP_MAX) {
                return PlanLoadResolution.Unrepresentable(
                    detail = "La serie observada tiene ${observation.reps} reps: la base de velocidad exige trabajo de $SPEED_REP_MIN-$SPEED_REP_MAX reps (§14.2).",
                )
            }
            val kg = observation.loadKg
            if (!kg.isFinite() || kg <= 0.0) {
                return PlanLoadResolution.Unrepresentable(detail = "Serie observada sin carga válida ($kg).")
            }
            val source = reference?.takeIf {
                it.kind == PlanLoadReferenceKind.OBSERVED_WORKING_SET &&
                    it.configurationId == targetConfigurationId
            } ?: captureObservedWorkingSet(
                configurationId = observation.configurationId,
                quantityConvention = observation.quantityConvention,
                loadKg = kg,
                reps = observation.reps,
                sourceSlotId = observation.sourceSlotId,
                sourceProgramId = observation.sourceProgramId,
                sourceRunId = observation.sourceRunId,
                sourceWeekOccurrence = observation.sourceWeekOccurrence,
                capturedAtMs = observation.capturedAtMs,
            )
                ?: return PlanLoadResolution.Unrepresentable(detail = "Serie observada no capturable ($kg kg × ${observation.reps} reps).")
            val range = speedRange(kg)
            return PlanLoadResolution.Resolved(
                loadKg = range.start,
                targetRange = range,
                basisLabel = SPEED_BASIS_LABEL,
                baseLoadKg = kg,
                sourceReference = source,
                quantityConvention = if (targetConvention == LoadQuantityConvention.UNSPECIFIED) {
                    observation.quantityConvention
                } else {
                    targetConvention
                },
            )
        }

        val current = reference ?: return PlanLoadResolution.Pending(
            detail = "Sin referencia de carga: el peso se elige en entrenamiento (null ≠ 0 kg).",
            targetRir = pendingRir,
            repRange = pendingRepRange,
        )
        if (current.configurationId != targetConfigurationId) {
            return PlanLoadResolution.Unrepresentable(
                detail = "Referencia de '${current.configurationId}' para '$targetConfigurationId': la carga de 3-5 reps solo aplica a su configuración (§14.2).",
            )
        }
        conventionIssue(current.quantityConvention, targetConvention)?.let {
            return PlanLoadResolution.Unrepresentable(detail = it)
        }
        when (current.kind) {
            PlanLoadReferenceKind.EXERCISE_1RM, PlanLoadReferenceKind.EXERCISE_TM ->
                return PlanLoadResolution.Unrepresentable(
                    detail = "Sin equivalencia entre 1RM/TM y la carga de trabajo de $SPEED_REP_MIN-$SPEED_REP_MAX reps (§14.2): se captura la serie observada.",
                )
            PlanLoadReferenceKind.BODYWEIGHT_EXTERNAL ->
                return PlanLoadResolution.Unrepresentable(
                    detail = "Lastre/asistencia no es trabajo observado de $SPEED_REP_MIN-$SPEED_REP_MAX reps: no basea la carga de velocidad.",
                )
            PlanLoadReferenceKind.OBSERVED_WORKING_SET -> Unit
        }
        if (current.state == PlanLoadReferenceState.PENDING) {
            return PlanLoadResolution.Pending(
                detail = "Referencia de trabajo observado pendiente de registrar: el peso se elige en entrenamiento (null ≠ 0 kg).",
                targetRir = pendingRir,
                repRange = pendingRepRange,
            )
        }
        val repMin = current.repMin
        val repMax = current.repMax
        if (repMin == null || repMax == null || repMin > repMax || repMin < SPEED_REP_MIN || repMax > SPEED_REP_MAX) {
            return PlanLoadResolution.Unrepresentable(
                detail = "Ventana de reps declarada ${repMin ?: "-"}-${repMax ?: "-"} fuera de $SPEED_REP_MIN-$SPEED_REP_MAX: no es una base de velocidad representable (§14.2).",
            )
        }
        val kg = current.capturedLoadKg
        if (kg == null || !kg.isFinite() || kg <= 0.0) {
            return PlanLoadResolution.Unrepresentable(detail = "Referencia capturada sin carga válida ($kg).")
        }
        val range = speedRange(kg)
        return PlanLoadResolution.Resolved(
            loadKg = range.start,
            targetRange = range,
            basisLabel = SPEED_BASIS_LABEL,
            baseLoadKg = kg,
            sourceReference = current,
            quantityConvention = if (targetConvention == LoadQuantityConvention.UNSPECIFIED) {
                current.quantityConvention
            } else {
                targetConvention
            },
        )
    }

    /**
     * Resolución genérica de UNA base de carga (1RM, TM, trabajo observado o
     * lastre/asistencia) para su configuración y convención. La convención
     * `UNSPECIFIED` de destino acepta la de la referencia; cualquier otra debe
     * coincidir exactamente (lastre 20 kg ≠ asistencia 20 kg).
     */
    fun resolveReference(
        reference: PlanLoadReference?,
        targetConfigurationId: String,
        targetConvention: LoadQuantityConvention,
    ): PlanLoadResolution {
        if (reference == null) {
            return PlanLoadResolution.Pending("Sin referencia de carga: el peso se elige en entrenamiento (null ≠ 0 kg).")
        }
        if (reference.configurationId != targetConfigurationId) {
            return PlanLoadResolution.Unrepresentable(
                detail = "Referencia de '${reference.configurationId}' para '$targetConfigurationId': solo aplica a la misma configuración (§14.2).",
            )
        }
        conventionIssue(reference.quantityConvention, targetConvention)?.let {
            return PlanLoadResolution.Unrepresentable(detail = it)
        }
        val label = when (reference.kind) {
            PlanLoadReferenceKind.EXERCISE_1RM -> "1RM de la misma configuración"
            PlanLoadReferenceKind.EXERCISE_TM -> "training max de la misma configuración"
            PlanLoadReferenceKind.OBSERVED_WORKING_SET -> SPEED_BASIS_LABEL
            PlanLoadReferenceKind.BODYWEIGHT_EXTERNAL -> "lastre/asistencia externa declarada"
        }
        if (reference.state == PlanLoadReferenceState.PENDING) {
            return PlanLoadResolution.Pending(
                "Referencia '$label' pendiente: el peso se elige en entrenamiento (null ≠ 0 kg).",
            )
        }
        val kg = reference.capturedLoadKg
        if (kg == null || !kg.isFinite() || kg <= 0.0) {
            return PlanLoadResolution.Unrepresentable(detail = "Referencia capturada sin carga válida ($kg).")
        }
        return PlanLoadResolution.Resolved(
            loadKg = kg,
            targetRange = kg..kg,
            basisLabel = label,
            baseLoadKg = kg,
            sourceReference = reference,
            quantityConvention = if (targetConvention == LoadQuantityConvention.UNSPECIFIED) {
                reference.quantityConvention
            } else {
                targetConvention
            },
        )
    }

    /**
     * Captura de UNA serie de trabajo para usarla como base (registro de una
     * serie pesada enlazada). Devuelve null salvo que la serie sea de 3-5 reps
     * con una carga finita positiva: nunca se guarda otra cosa como base.
     */
    fun captureObservedWorkingSet(
        configurationId: String,
        quantityConvention: LoadQuantityConvention = LoadQuantityConvention.UNSPECIFIED,
        loadKg: Double,
        reps: Int,
        sourceSlotId: String? = null,
        sourceProgramId: String? = null,
        sourceRunId: String? = null,
        sourceWeekOccurrence: Int? = null,
        capturedAtMs: Long? = null,
    ): PlanLoadReference? {
        if (reps < SPEED_REP_MIN || reps > SPEED_REP_MAX) return null
        if (!loadKg.isFinite() || loadKg <= 0.0) return null
        return PlanLoadReference(
            kind = PlanLoadReferenceKind.OBSERVED_WORKING_SET,
            configurationId = configurationId,
            quantityConvention = quantityConvention,
            sourceSlotId = sourceSlotId,
            repMin = reps,
            repMax = reps,
            sourceProgramId = sourceProgramId,
            sourceRunId = sourceRunId,
            sourceWeekOccurrence = sourceWeekOccurrence,
            state = PlanLoadReferenceState.CAPTURED,
            capturedLoadKg = loadKg,
            capturedAtMs = capturedAtMs,
        )
    }

    /**
     * Carga ligera elegida explícitamente por el usuario: se captura en la
     * referencia (sin tocar la original) y se registra el cambio de
     * prescripción para la auditoría §13.4. null = kg no válido.
     */
    fun explicitLightLoadChange(
        reference: PlanLoadReference,
        chosenLoadKg: Double,
        slotId: String,
        prescriptionBefore: String? = null,
        prescriptionAfter: String? = null,
    ): ExplicitLightLoad? {
        if (!chosenLoadKg.isFinite() || chosenLoadKg <= 0.0) return null
        val captured = reference.copy(
            state = PlanLoadReferenceState.CAPTURED,
            capturedLoadKg = chosenLoadKg,
        )
        val change = PlanSlotChange(
            slotId = slotId,
            fromConfigurationId = reference.configurationId,
            toConfigurationId = reference.configurationId,
            reason = "Carga ligera elegida explícitamente por el usuario sobre la referencia declarada (§14.2).",
            samePattern = true,
            differences = "Solo cambia la carga elegida; configuración, convención y base intactas.",
            prescriptionBefore = prescriptionBefore,
            prescriptionAfter = prescriptionAfter,
            loadReferenceKept = true,
        )
        return ExplicitLightLoad(captured, change)
    }

    /**
     * Realización de una carga pedida dentro de [range] contra material real:
     * el kg más cercano al límite inferior si hay stock en el rango; sin
     * inventario el rango sigue visible; si nada cae dentro se reporta la
     * discrepancia (§14.2 redondeo).
     */
    fun roundWithinRange(range: ClosedFloatingPointRange<Double>, realizable: List<Double>): LoadRounding {
        val epsilon = 1e-6
        val finite = realizable.filter { it.isFinite() && it > 0.0 }
        val inside = finite.filter { it >= range.start - epsilon && it <= range.endInclusive + epsilon }
        if (inside.isNotEmpty()) {
            return LoadRounding.Realized(inside.minBy { abs(it - range.start) })
        }
        if (finite.isEmpty()) return LoadRounding.RangeKept
        val nearest = finite.minBy { value ->
            when {
                value < range.start -> range.start - value
                value > range.endInclusive -> value - range.endInclusive
                else -> 0.0
            }
        }
        return LoadRounding.Discrepancy(nearest)
    }

    /** Convención inaplicable (§14.2): null = compatible. */
    private fun conventionIssue(
        referenceConvention: LoadQuantityConvention,
        targetConvention: LoadQuantityConvention,
    ): String? {
        if (targetConvention == LoadQuantityConvention.UNSPECIFIED ||
            referenceConvention == LoadQuantityConvention.UNSPECIFIED
        ) {
            return null
        }
        if (referenceConvention == targetConvention) return null
        return "Convención '$referenceConvention' no aplica a '$targetConvention': lastre, asistencia y carga externa total no se intercambian (§14.2)."
    }
}
