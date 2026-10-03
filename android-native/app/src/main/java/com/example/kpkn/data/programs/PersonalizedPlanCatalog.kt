package com.example.kpkn.data.programs

import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramStructure
import com.example.kpkn.data.protocols.LiftSlot
import com.example.kpkn.data.protocols.PROTOCOL_LIBRARY
import com.example.kpkn.data.protocols.PlanProvenance
import com.example.kpkn.data.protocols.TrainingPlanRecipe
import com.example.kpkn.data.protocols.isVisibleForApplication
import com.example.kpkn.data.protocols.definitions.AuthoredPhulPhatRecipes
import com.example.kpkn.data.protocols.definitions.AuthoredSourceRecord
import com.example.kpkn.data.protocols.definitions.AuthoredSources
import com.example.kpkn.data.protocols.definitions.NativeProfileKind
import com.example.kpkn.data.protocols.definitions.NativeWeekBuilder
import kotlinx.serialization.Serializable

@Serializable
enum class CatalogSource { TEMPLATE, PROTOCOL, NATIVE }
@Serializable
enum class CatalogLevel { BEGINNER, INTERMEDIATE, ADVANCED }
/**
 * Cómo dura un plan: una semana que se repite, un ciclo de varias semanas que se
 * repite (Texas, 5/3/1, Westside…) o un ciclo finito que termina.
 */
@Serializable
enum class CatalogDuration { REPEATING_WEEK, REPEATING_CYCLE, FINITE_CYCLE }
@Serializable
enum class PublicationState { PUBLISHED, UNAVAILABLE }
@Serializable
enum class CatalogClassification { SIMPLE, ADVANCED }
@Serializable
enum class TrainingFocus { FULL_BODY, GLUTES, LEGS, BACK, CHEST, SHOULDERS, ARMS }
@Serializable
enum class AdaptationPolicy { FIXED_PRESCRIPTION, CURATED_WEEKLY, NONE }

/** Shared training references; goals map to one of these and candidates carry their real discipline. */
@Serializable
enum class TrainingReference { POWERLIFTING, HYPERTROPHY, POWERBUILDING }

/**
 * Capacidad explícita de una entrada (§15.1): el objetivo Atleta completo
 * exige `strength + hypertrophy + power + cardio`, no `schedulesCardio`
 * solamente. Las entradas históricas lo dejan vacío y sus consumidores siguen
 * usando los criterios legacy hasta que migren.
 */
@Serializable
enum class TrainingCapability { STRENGTH, HYPERTROPHY, POWER, CARDIO }

fun com.example.kpkn.data.models.TrainingStyle.toTrainingReference(): TrainingReference = when (this) {
    com.example.kpkn.data.models.TrainingStyle.POWERLIFTER -> TrainingReference.POWERLIFTING
    com.example.kpkn.data.models.TrainingStyle.POWERBUILDER -> TrainingReference.POWERBUILDING
    com.example.kpkn.data.models.TrainingStyle.BODYBUILDER -> TrainingReference.HYPERTROPHY
}

data class CatalogEntry(
    val id: String,
    val source: CatalogSource,
    val sourceId: String,
    @Deprecated("Usa displayName", ReplaceWith("displayName"))
    val title: String,
    @Deprecated(
        "Usa PlanLabels.subtitle(entry)",
        ReplaceWith("PlanLabels.subtitle(this)", "com.example.kpkn.data.programs.PlanLabels"),
    )
    val technicalSubtitle: String,
    @Deprecated("Usa summary", ReplaceWith("summary"))
    val description: String,
    val requiredEquipment: Set<String>,
    val supportedFrequencies: IntRange,
    /**
     * Nivel base: el menor de [levels]. El planner lo usa hoy para ordenar; las
     * familias nativas históricas conservan el suyo aunque [levels] sea «todos»
     * (el orden del planner no cambia hasta C.P3).
     */
    val level: CatalogLevel,
    val duration: CatalogDuration,
    val supportedFocuses: Set<TrainingFocus>,
    val adaptation: AdaptationPolicy,
    val publication: PublicationState,
    val sourceAuthor: String? = null,
    val sourceUrl: String? = null,
    val sourceRevision: String? = null,
    val disclaimer: String? = null,
    val recipe: TrainingPlanRecipe? = null,
    val template: ProgramTemplateOption? = null,
    val references: Set<TrainingReference> = emptySet(),
    /**
     * Procedencia editorial de las entradas autoradas (§10/§14.1): clase
     * ORIGINAL/ADAPTED, fuente, edición y defaults operativos. `null` en las
     * entradas heredadas, que conservan su procedencia técnica sin cambios.
     */
    val provenance: PlanProvenance? = null,
    /**
     * Ficha editorial de la fuente (§10.1 regla 4): edición, fecha de consulta,
     * reglas efectivas y «Configuración inicial KPKN», separados para poder
     * mostrarlos sin mezclarlos con la prescripción del autor.
     */
    val authoredSource: AuthoredSourceRecord? = null,
    /**
     * Capacidades explícitas (§15.1). Vacío en las entradas históricas: su
     * elegibilidad sigue decidiéndose con los criterios legacy (`references`,
     * `schedulesCardio`) hasta que el consumidor migre.
     */
    val capabilities: Set<TrainingCapability> = emptySet(),
    /**
     * Ficha editorial única (texto de usuario, orden, niveles, atribución): sale
     * de [PlanEditorialTable]. El valor por defecto se deriva de los alias para
     * que quien construye la entrada a mano (fixtures) siga compilando.
     */
    val editorial: PlanEditorial = PlanEditorial(
        displayName = title,
        summary = description,
        origin = PlanOrigin.KPKN,
        rank = 10_000,
        levels = setOf(level),
    ),
    /** Semanas del ciclo (1 en las semanas que se repiten); null si la entrada se construyó a mano. */
    val durationWeeks: Int? = null,
) {
    val displayName: String get() = editorial.displayName
    val shortName: String get() = editorial.shortName
    val summary: String get() = editorial.summary
    val kind: PlanKind get() = editorial.kind
    val origin: PlanOrigin get() = editorial.origin
    val rank: Int get() = editorial.rank
    val levels: Set<CatalogLevel> get() = editorial.levels
    val attributionLine: String? get() = editorial.attributionLine
    val notes: List<String> get() = editorial.notes
    val listed: Boolean get() = editorial.listed

    /**
     * Términos del glosario: los editoriales más los que se deducen de la receta
     * (la de la propia entrada o, en las plantillas, la de la plantilla).
     */
    val terms: Set<PlanTerm> get() = editorial.terms + derivedTerms(recipe ?: template?.recipe)

    val classification: CatalogClassification
        get() = if (template?.type == ProgramStructure.COMPLEX || (recipe?.distinctBlockCount ?: 1) > 1) CatalogClassification.ADVANCED else CatalogClassification.SIMPLE

    /** Only the plans that really schedule cardio qualify for a strength + cardio goal. */
    val schedulesCardio: Boolean
        get() = source == CatalogSource.NATIVE && sourceId == "strength-cardio"
}

object PersonalizedPlanCatalog {
    const val REVISION = "native-cycle-1"

    // Los textos de las ocho familias históricas viven en PlanEditorialTable;
    // aquí solo quedan días, material y nivel base.
    private data class NativeSpec(val id: String, val frequencies: IntRange, val equipment: Set<String>, val level: CatalogLevel = CatalogLevel.BEGINNER)
    private val nativeSpecs = listOf(
        NativeSpec("full-body", 2..3, setOf("general_gym")),
        NativeSpec("gym-muscle", 3..6, setOf("general_gym"), CatalogLevel.INTERMEDIATE),
        NativeSpec("machine-muscle", 2..4, setOf("machine")),
        NativeSpec("home-training", 2..4, setOf("bodyweight")),
        NativeSpec("bodyweight", 2..4, setOf("bodyweight", "support", "pull_up_bar"), CatalogLevel.INTERMEDIATE),
        NativeSpec("strength-cardio", 1..6, setOf("general_gym")),
        NativeSpec("return-training", 2..3, setOf("general_gym")),
        NativeSpec("one-day", 1..1, setOf("general_gym")),
    )

    private fun nativeEntry(spec: NativeSpec): CatalogEntry {
        val id = "native:${spec.id}"
        val editorial = PlanEditorialTable.forId(id)
        return CatalogEntry(
            id = id, source = CatalogSource.NATIVE, sourceId = spec.id,
            title = editorial.displayName,
            technicalSubtitle = PlanLabels.subtitle(CatalogDuration.REPEATING_WEEK, 1, editorial.levels),
            description = editorial.summary, requiredEquipment = spec.equipment,
            supportedFrequencies = spec.frequencies, level = spec.level, duration = CatalogDuration.REPEATING_WEEK,
            supportedFocuses = TrainingFocus.entries.toSet(), adaptation = AdaptationPolicy.CURATED_WEEKLY,
            publication = PublicationState.PUBLISHED, sourceAuthor = "KPKN", sourceRevision = REVISION,
            // SimpleCyclePersonalizer generates hypertrophy cycles; they are never
            // relabelled as another discipline.
            references = setOf(TrainingReference.HYPERTROPHY),
            editorial = editorial, durationWeeks = 1,
        )
    }

    // ─── Cuatro perfiles propios §11.1 (paquete F, T-004a) ──────────────────

    private data class OwnProfileEntry(
        val kind: NativeProfileKind,
        val references: Set<TrainingReference>,
        val capabilities: Set<TrainingCapability>,
    )

    private val ownProfileEntries = listOf(
        OwnProfileEntry(
            NativeProfileKind.STRENGTH,
            setOf(TrainingReference.POWERLIFTING),
            setOf(TrainingCapability.STRENGTH),
        ),
        OwnProfileEntry(
            NativeProfileKind.MUSCLE,
            setOf(TrainingReference.HYPERTROPHY),
            setOf(TrainingCapability.HYPERTROPHY),
        ),
        OwnProfileEntry(
            NativeProfileKind.POWERBUILDING,
            setOf(TrainingReference.POWERBUILDING),
            setOf(TrainingCapability.STRENGTH, TrainingCapability.HYPERTROPHY),
        ),
        // Atleta completo no cuelga de ninguna disciplina legacy: su capability
        // explícita es la combinación completa (§15.1) y `references` vacío
        // evita colarse en los objetivos de una sola disciplina.
        OwnProfileEntry(
            NativeProfileKind.COMPLETE_ATHLETE,
            emptySet(),
            setOf(TrainingCapability.STRENGTH, TrainingCapability.HYPERTROPHY, TrainingCapability.POWER, TrainingCapability.CARDIO),
        ),
    )

    /**
     * Los cuatro planes propios (§11.1): seis semanas con descarga, días 1..6,
     * dosis por nivel §11.2 y calendarios §11.3/§11.4 construidos por
     * `SimpleCyclePersonalizer` desde `NativeProfileSpecs`. Conservan los ocho
     * nativos históricos y `native:strength-cardio` intactos.
     */
    private fun ownProfileEntry(spec: OwnProfileEntry): CatalogEntry {
        val kind = spec.kind
        val editorial = PlanEditorialTable.forId(kind.entryId)
        return CatalogEntry(
            id = kind.entryId,
            source = CatalogSource.NATIVE,
            sourceId = kind.sourceId,
            title = editorial.displayName,
            technicalSubtitle = PlanLabels.subtitle(CatalogDuration.FINITE_CYCLE, NativeWeekBuilder.WEEKS, editorial.levels),
            description = editorial.summary,
            // Metadata gruesa: el material real se decide en el motor (§11.1).
            requiredEquipment = setOf("general_gym"),
            supportedFrequencies = 1..6,
            level = CatalogLevel.BEGINNER,
            duration = CatalogDuration.FINITE_CYCLE,
            supportedFocuses = TrainingFocus.entries.toSet(),
            adaptation = AdaptationPolicy.CURATED_WEEKLY,
            publication = PublicationState.PUBLISHED,
            sourceAuthor = "KPKN",
            sourceRevision = REVISION,
            references = spec.references,
            capabilities = spec.capabilities,
            editorial = editorial,
            durationWeeks = NativeWeekBuilder.WEEKS,
        )
    }

    private fun recipeReferences(recipe: TrainingPlanRecipe?): Set<TrainingReference> {
        val lifts = recipe?.liftSlots?.keys.orEmpty()
        return if (lifts.containsAll(setOf(LiftSlot.SQUAT, LiftSlot.BENCH, LiftSlot.DEADLIFT))) {
            setOf(TrainingReference.POWERLIFTING)
        } else {
            emptySet()
        }
    }

    private fun templateReferences(template: ProgramTemplateOption): Set<TrainingReference> {
        val fromLabel = when (template.trackLabel?.trim()?.lowercase()) {
            "powerlifting" -> setOf(TrainingReference.POWERLIFTING)
            "powerbuilding" -> setOf(TrainingReference.POWERBUILDING)
            "culturismo", "hipertrofia" -> setOf(TrainingReference.HYPERTROPHY)
            else -> emptySet()
        }
        return fromLabel.ifEmpty { recipeReferences(template.recipe) }
    }

    private fun protocolReferences(protocol: com.example.kpkn.data.protocols.Protocol): Set<TrainingReference> {
        val tags = protocol.tags.map { it.trim().lowercase() }
        val fromTags = buildSet {
            if (tags.any { it.contains("powerlifting") || it == "sbd" }) add(TrainingReference.POWERLIFTING)
            if (tags.any { it.contains("powerbuilding") }) add(TrainingReference.POWERBUILDING)
            if (tags.any { it.contains("hipertrofia") || it.contains("culturismo") }) add(TrainingReference.HYPERTROPHY)
        }
        return fromTags.ifEmpty { recipeReferences(protocol.recipe) }
    }

    /** Nivel declarado por una receta («principiante», «intermedio», «avanzado»); null si no lo declara. */
    private fun claimedCatalogLevel(claimedLevel: String?): CatalogLevel? = when (claimedLevel?.trim()?.lowercase()) {
        "principiante", "beginner" -> CatalogLevel.BEGINNER
        "intermedio", "intermediate" -> CatalogLevel.INTERMEDIATE
        "avanzado", "advanced" -> CatalogLevel.ADVANCED
        else -> null
    }

    /**
     * Duración de una receta fija: una semana que se repite, un ciclo de varias
     * semanas que se repite, o un ciclo finito que termina.
     */
    private fun recipeDuration(recipe: TrainingPlanRecipe): CatalogDuration = when {
        recipe.repeats && recipe.weeks.size == 1 -> CatalogDuration.REPEATING_WEEK
        recipe.repeats && recipe.weeks.size > 1 -> CatalogDuration.REPEATING_CYCLE
        else -> CatalogDuration.FINITE_CYCLE
    }

    // ─── Entradas autoradas §10.1 (paquete E) ────────────────────────────────

    /**
     * Etiqueta de §10.1 regla 2 para una búsqueda/deep link por un ID antiguo:
     * la entrada histórica sigue resolviendo y el lookup informa esto, sin
     * regenerar la edición nueva sobre el programa viejo.
     */
    const val LEGACY_VERSION_LABEL = "Versión anterior"

    /** ID antiguo → entrada nueva recomendada en su lugar (§10.1 regla 2). */
    private val legacyPlanSuccessors: Map<String, String> = mapOf(
        "protocol:phul-verified" to AuthoredPhulPhatRecipes.PHUL_ORIGINAL_ID,
        "protocol:phat-verified" to AuthoredPhulPhatRecipes.PHAT_ORIGINAL_ID,
    )

    /** Resultado tipado de [lookup] para una búsqueda por ID de plan. */
    data class PlanCatalogLookup(
        val entry: CatalogEntry,
        /** true = versión histórica KPKN resoluble pero ya no recomendada. */
        val isLegacyVersion: Boolean,
        /** [LEGACY_VERSION_LABEL] cuando [isLegacyVersion]; null en otro caso. */
        val versionLabel: String?,
        /** Entrada nueva que recomienda su lugar; null si no es histórico. */
        val successorId: String?,
    )

    /**
     * Búsqueda por ID de plan (§10.1 regla 2): acepta el id completo
     * (`protocol:phul-verified`) o el id antiguo desnudo (`phul-verified`) y
     * **siempre** resuelve la entrada histórica; el lookup informa
     * [LEGACY_VERSION_LABEL] y apunta a la entrada nueva. El programa/receta
     * guardado se conserva sin mutación: este lookup no cambia snapshots.
     */
    fun lookup(id: String): PlanCatalogLookup? {
        val entry = find(id) ?: find("protocol:$id") ?: return null
        val successor = legacyPlanSuccessors[entry.id]
        return PlanCatalogLookup(
            entry = entry,
            isLegacyVersion = successor != null,
            versionLabel = successor?.let { LEGACY_VERSION_LABEL },
            successorId = successor,
        )
    }

    private fun authoredEntry(
        id: String,
        sourceId: String,
        level: CatalogLevel,
        frequency: Int,
        sourceRevision: String,
        source: AuthoredSourceRecord,
        recipe: TrainingPlanRecipe,
    ): CatalogEntry {
        val editorial = PlanEditorialTable.forId(id)
        val duration = recipeDuration(recipe)
        val weeks = recipe.weeks.size
        return CatalogEntry(
            id = id,
            source = CatalogSource.PROTOCOL,
            sourceId = sourceId,
            title = editorial.displayName,
            technicalSubtitle = PlanLabels.subtitle(duration, weeks, editorial.levels),
            description = editorial.summary,
            // Metadata gruesa de receta fija (§4 E-102): el material real se
            // verifica en la guardia de materialización, nunca en este filtro.
            requiredEquipment = setOf("general_gym"),
            supportedFrequencies = frequency..frequency,
            level = level,
            duration = duration,
            supportedFocuses = setOf(TrainingFocus.FULL_BODY),
            adaptation = AdaptationPolicy.FIXED_PRESCRIPTION,
            publication = PublicationState.PUBLISHED,
            sourceAuthor = source.author,
            sourceUrl = source.sourceUrl,
            sourceRevision = sourceRevision,
            disclaimer = "No afiliado a ${source.author}",
            recipe = recipe,
            // PHUL/PHAT son powerbuilding real con días de hipertrofia: se ofrecen
            // en Fuerza y músculo y en Músculo; nunca como powerlifting (§11.1).
            references = setOf(TrainingReference.POWERBUILDING, TrainingReference.HYPERTROPHY),
            provenance = recipe.provenance,
            authoredSource = source,
            editorial = editorial,
            durationWeeks = weeks,
        )
    }

    private val authoredEntriesLazy: List<CatalogEntry> by lazy {
        val phul = AuthoredSources.phul
        val phat = AuthoredSources.phat
        listOf(
            authoredEntry(
                id = AuthoredPhulPhatRecipes.PHUL_ORIGINAL_ID,
                sourceId = "phul-ms-2021-r1",
                level = CatalogLevel.INTERMEDIATE,
                frequency = 4,
                sourceRevision = "M&S 2021 receta r1",
                source = phul,
                recipe = AuthoredPhulPhatRecipes.phulOriginal,
            ),
            authoredEntry(
                id = AuthoredPhulPhatRecipes.PHAT_ORIGINAL_ID,
                sourceId = "phat-biolayne-2016-r1",
                level = CatalogLevel.ADVANCED,
                frequency = 5,
                sourceRevision = "Biolayne 2016 receta r1",
                source = phat,
                recipe = AuthoredPhulPhatRecipes.phatOriginal,
            ),
            authoredEntry(
                id = AuthoredPhulPhatRecipes.PHUL_ADAPTED_ID,
                sourceId = "phul-kpkn-r1",
                level = CatalogLevel.INTERMEDIATE,
                frequency = 4,
                sourceRevision = "M&S 2021 receta r1 · adaptación KPKN r1",
                source = phul,
                recipe = AuthoredPhulPhatRecipes.phulAdapted,
            ),
            authoredEntry(
                id = AuthoredPhulPhatRecipes.PHAT_ADAPTED_ID,
                sourceId = "phat-kpkn-r1",
                level = CatalogLevel.ADVANCED,
                frequency = 5,
                sourceRevision = "Biolayne 2016 receta r1 · adaptación KPKN r1",
                source = phat,
                recipe = AuthoredPhulPhatRecipes.phatAdapted,
            ),
        )
    }

    fun entries(): List<CatalogEntry> {
        val templates = PROGRAM_TEMPLATES.map { template ->
            val id = "template:${template.id}"
            val editorial = PlanEditorialTable.forId(id)
            val days = template.recipe?.daysPerWeek?.takeIf { it > 0 }
            val duration = if (template.weeks == 1) CatalogDuration.REPEATING_WEEK else CatalogDuration.FINITE_CYCLE
            CatalogEntry(
                id = id, source = CatalogSource.TEMPLATE, sourceId = template.id,
                title = editorial.displayName,
                technicalSubtitle = PlanLabels.subtitle(duration, template.weeks, editorial.levels),
                description = editorial.summary, requiredEquipment = setOf("general_gym"),
                supportedFrequencies = days?.let { it..it } ?: (1..6),
                // El nivel de una plantilla compleja sale de su receta (claimedLevel);
                // las plantillas simples (sin receta) valen para cualquier nivel.
                level = claimedCatalogLevel(template.recipe?.claimedLevel)
                    ?: if (template.type == ProgramStructure.SIMPLE) CatalogLevel.BEGINNER else CatalogLevel.ADVANCED,
                duration = duration,
                supportedFocuses = setOf(TrainingFocus.FULL_BODY), adaptation = AdaptationPolicy.FIXED_PRESCRIPTION,
                publication = PublicationState.PUBLISHED, template = template, sourceAuthor = "KPKN",
                references = templateReferences(template),
                editorial = editorial, durationWeeks = template.weeks,
            )
        }
        val protocols = PROTOCOL_LIBRARY.filter { it.isVisibleForApplication }.map { protocol ->
            val id = "protocol:${protocol.id}"
            val editorial = PlanEditorialTable.forId(id)
            val weeks = protocol.recipe!!.weeks.size
            val days = protocol.recipe.daysPerWeek
            val duration = recipeDuration(protocol.recipe)
            CatalogEntry(
                id = id, source = CatalogSource.PROTOCOL, sourceId = protocol.id,
                title = editorial.displayName,
                technicalSubtitle = PlanLabels.subtitle(duration, weeks, editorial.levels),
                description = editorial.summary,
                requiredEquipment = setOf("general_gym"), supportedFrequencies = days..days,
                level = claimedCatalogLevel(protocol.fidelitySpec?.claimedLevel) ?: CatalogLevel.INTERMEDIATE,
                duration = duration,
                supportedFocuses = setOf(TrainingFocus.FULL_BODY), adaptation = AdaptationPolicy.FIXED_PRESCRIPTION,
                publication = PublicationState.PUBLISHED, sourceAuthor = protocol.author,
                sourceUrl = protocol.source.primaryUrl, sourceRevision = protocol.source.revision ?: protocol.source.catalogRevision,
                disclaimer = protocol.source.disclaimer, recipe = protocol.recipe,
                references = protocolReferences(protocol),
                editorial = editorial, durationWeeks = weeks,
            )
        }
        return nativeSpecs.map(::nativeEntry) + ownProfileEntries.map(::ownProfileEntry) + templates + protocols + authoredEntriesLazy
    }

    fun find(id: String): CatalogEntry? = entries().firstOrNull { it.id == id }

    /**
     * Entrada del catálogo a la que pertenece un programa ya creado, o null si
     * el programa no viene del catálogo (creado a mano o de una versión que ya
     * no existe). Se prueba, en este orden, y gana la primera que resuelve:
     *
     * 1. `planProvenance.planId`: el id publicado exacto de los planes de autor.
     * 2. `structureTemplateId`: el id de la entrada (`native:...`, planes de autor)
     *    o el id desnudo de la receta o plantilla (`texas-method-3d`, `power-12-3`),
     *    que los motores rellenan con `recipe.id` o `template.id`.
     * 3. `sourceProtocolId`: el id desnudo del protocolo o de la plantilla.
     *
     * Un id desnudo se busca con los prefijos `protocol:`, `template:` y `native:`.
     */
    fun findForProgram(program: Program): CatalogEntry? {
        val all = entries()
        fun byId(id: String): CatalogEntry? = all.firstOrNull { it.id == id }
        fun byAnyId(id: String): CatalogEntry? =
            byId(id) ?: byId("protocol:$id") ?: byId("template:$id") ?: byId("native:$id")
        return program.planProvenance?.planId?.let(::byId)
            ?: program.structureTemplateId?.let(::byAnyId)
            ?: program.sourceProtocolId?.let(::byAnyId)
    }

    fun classify(source: CatalogSource, structure: ProgramStructure, blockCount: Int, mesocycleCount: Int, repeats: Boolean, weeksDiffer: Boolean): CatalogClassification =
        if (blockCount > 1 || mesocycleCount > 1) CatalogClassification.ADVANCED else CatalogClassification.SIMPLE
}
