package com.example.kpkn.data.programs

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
import com.example.kpkn.domain.text.SpanishPlurals
import kotlinx.serialization.Serializable

@Serializable
enum class CatalogSource { TEMPLATE, PROTOCOL, NATIVE }
@Serializable
enum class CatalogLevel { BEGINNER, INTERMEDIATE, ADVANCED }
@Serializable
enum class CatalogDuration { REPEATING_WEEK, FINITE_CYCLE }
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
    val title: String,
    val technicalSubtitle: String,
    val description: String,
    val requiredEquipment: Set<String>,
    val supportedFrequencies: IntRange,
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
) {
    val classification: CatalogClassification
        get() = if (template?.type == ProgramStructure.COMPLEX || (recipe?.distinctBlockCount ?: 1) > 1) CatalogClassification.ADVANCED else CatalogClassification.SIMPLE

    /** Only the plans that really schedule cardio qualify for a strength + cardio goal. */
    val schedulesCardio: Boolean
        get() = source == CatalogSource.NATIVE && sourceId == "strength-cardio"
}

object PersonalizedPlanCatalog {
    const val REVISION = "native-cycle-1"

    private data class NativeSpec(val id: String, val title: String, val description: String, val frequencies: IntRange, val equipment: Set<String>, val level: CatalogLevel = CatalogLevel.BEGINNER)
    private val nativeSpecs = listOf(
        NativeSpec("full-body", "Empieza con todo el cuerpo", "Trabaja los principales grupos musculares en una semana que puedes repetir. Ajustamos la dosis a tu experiencia y al tiempo disponible.", 2..3, setOf("general_gym")),
        NativeSpec("gym-muscle", "Construye músculo en el gimnasio", "Reparte el trabajo de torso y piernas y da prioridad a la zona que quieras desarrollar, sin abandonar el resto del cuerpo.", 3..6, setOf("general_gym"), CatalogLevel.INTERMEDIATE),
        NativeSpec("machine-muscle", "Construye músculo con máquinas", "Una semana de movimientos guiados. Solo utilizamos máquinas; no añadimos barras, mancuernas o poleas que no hayas elegido.", 2..4, setOf("machine")),
        NativeSpec("home-training", "Entrena en casa sin gimnasio", "Aprovecha tus bandas, mancuernas y peso corporal. Te indicamos si falta material para cubrir algún movimiento, sin sustituirlo a escondidas.", 2..4, setOf("bodyweight")),
        NativeSpec("bodyweight", "Domina tu peso corporal", "Organiza fuerza con tu propio peso. Las variantes de tracción necesitan una barra o apoyo estable y experiencia previa.", 2..4, setOf("bodyweight", "support", "pull_up_bar"), CatalogLevel.INTERMEDIATE),
        NativeSpec("strength-cardio", "Fuerza y resistencia", "Combina series de fuerza con cardio de intensidad moderada. La vista previa reserva tiempo para ambas partes.", 1..6, setOf("general_gym")),
        NativeSpec("return-training", "Vuelve a entrenar", "Retoma la constancia con una entrada conservadora. Empezamos por una dosis manejable, no por el máximo volumen.", 2..3, setOf("general_gym")),
        NativeSpec("one-day", "Aprovecha un solo día", "Una sesión equilibrada cuando tu semana deja poco espacio. Priorizamos lo posible sin prometer la frecuencia de un plan de varios días.", 1..1, setOf("general_gym")),
    )

    private fun nativeEntry(spec: NativeSpec) = CatalogEntry(
        id = "native:${spec.id}", source = CatalogSource.NATIVE, sourceId = spec.id,
        title = spec.title,
        technicalSubtitle = "Semana cíclica · " + if (spec.frequencies.first == spec.frequencies.last) {
            SpanishPlurals.days(spec.frequencies.first)
        } else {
            "${spec.frequencies.first}–${spec.frequencies.last} días"
        },
        description = spec.description, requiredEquipment = spec.equipment,
        supportedFrequencies = spec.frequencies, level = spec.level, duration = CatalogDuration.REPEATING_WEEK,
        supportedFocuses = TrainingFocus.entries.toSet(), adaptation = AdaptationPolicy.CURATED_WEEKLY,
        publication = PublicationState.PUBLISHED, sourceAuthor = "KPKN", sourceRevision = REVISION,
        // SimpleCyclePersonalizer generates hypertrophy cycles; they are never
        // relabelled as another discipline.
        references = setOf(TrainingReference.HYPERTROPHY),
    )

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
        return CatalogEntry(
            id = kind.entryId,
            source = CatalogSource.NATIVE,
            sourceId = kind.sourceId,
            title = kind.title,
            technicalSubtitle = "Ciclo de ${NativeWeekBuilder.WEEKS} semanas · 1–6 días",
            description = kind.description,
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

    private val friendlyMethods = mapOf(
        "gzclp" to "Gana fuerza paso a paso",
        "wendler-531-bbb" to "Fuerza y músculo por ciclos",
        "texas-method" to "Alterna volumen, recuperación e intensidad",
        "smolov-jr" to "Especializa tu fuerza con alta frecuencia",
        "kpkn-native-sbd-4" to "Mejora tus tres levantamientos",
        "phul" to "Combina días de fuerza y músculo",
        "phat" to "Reparte potencia e hipertrofia",
    )

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
        title: String,
        technicalSubtitle: String,
        description: String,
        level: CatalogLevel,
        frequency: Int,
        sourceRevision: String,
        source: AuthoredSourceRecord,
        recipe: TrainingPlanRecipe,
        adapted: Boolean,
    ): CatalogEntry = CatalogEntry(
        id = id,
        source = CatalogSource.PROTOCOL,
        sourceId = sourceId,
        title = title,
        technicalSubtitle = technicalSubtitle,
        description = description,
        // Metadata gruesa de receta fija (§4 E-102): el material real se
        // verifica en la guardia de materialización, nunca en este filtro.
        requiredEquipment = setOf("general_gym"),
        supportedFrequencies = frequency..frequency,
        level = level,
        duration = CatalogDuration.FINITE_CYCLE,
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
    )

    private val authoredEntriesLazy: List<CatalogEntry> by lazy {
        val phul = AuthoredSources.phul
        val phat = AuthoredSources.phat
        listOf(
            authoredEntry(
                id = AuthoredPhulPhatRecipes.PHUL_ORIGINAL_ID,
                sourceId = "phul-ms-2021-r1",
                title = "PHUL original",
                technicalSubtitle = "Original fiel · 4 días · 12 semanas · M&S 2021",
                description = "PHUL de Brandon Campbell tal y como se publica en Muscle & Strength: " +
                    "cuatro días de fuerza e hipertrofia durante doce semanas, con los rangos del autor, " +
                    "esfuerzo con reserva y sin porcentajes. Requiere barra, rack, banco, polea y máquinas.",
                level = CatalogLevel.INTERMEDIATE,
                frequency = 4,
                sourceRevision = "M&S 2021 receta r1",
                source = phul,
                recipe = AuthoredPhulPhatRecipes.phulOriginal,
                adapted = false,
            ),
            authoredEntry(
                id = AuthoredPhulPhatRecipes.PHAT_ORIGINAL_ID,
                sourceId = "phat-biolayne-2016-r1",
                title = "PHAT original",
                technicalSubtitle = "Original fiel · 5 días · 6 semanas · Biolayne 2016",
                description = "PHAT de Layne Norton tal y como se publica en Biolayne (2016): cinco días " +
                    "con tres bloques de velocidad al 65 % de tu carga habitual de 3–5 repeticiones, para " +
                    "atletas acostumbrados a la alta frecuencia. Nivel avanzado; seis semanas de carga.",
                level = CatalogLevel.ADVANCED,
                frequency = 5,
                sourceRevision = "Biolayne 2016 receta r1",
                source = phat,
                recipe = AuthoredPhulPhatRecipes.phatOriginal,
                adapted = false,
            ),
            authoredEntry(
                id = AuthoredPhulPhatRecipes.PHUL_ADAPTED_ID,
                sourceId = "phul-kpkn-r1",
                title = "PHUL adaptado KPKN",
                technicalSubtitle = "Adaptación KPKN · 4 días · 12 semanas",
                description = "El mismo PHUL de cuatro días con las tablas de origen intactas y " +
                    "sustituciones curadas slot a slot cuando falta material. Conserva días y dosis; " +
                    "si algo no puede sustituirse se explica, nunca se recorta en silencio.",
                level = CatalogLevel.INTERMEDIATE,
                frequency = 4,
                sourceRevision = "M&S 2021 receta r1 · adaptación KPKN r1",
                source = phul,
                recipe = AuthoredPhulPhatRecipes.phulAdapted,
                adapted = true,
            ),
            authoredEntry(
                id = AuthoredPhulPhatRecipes.PHAT_ADAPTED_ID,
                sourceId = "phat-kpkn-r1",
                title = "PHAT adaptado KPKN",
                technicalSubtitle = "Adaptación KPKN · 5 días · 6 semanas",
                description = "El mismo PHAT de cinco días con las tablas de origen intactas y " +
                    "sustituciones curadas slot a slot. Si el presupuesto de tiempo no admite el volumen " +
                    "se ofrece el plan propio de fuerza y músculo, nunca un PHAT recortado en silencio.",
                level = CatalogLevel.ADVANCED,
                frequency = 5,
                sourceRevision = "Biolayne 2016 receta r1 · adaptación KPKN r1",
                source = phat,
                recipe = AuthoredPhulPhatRecipes.phatAdapted,
                adapted = true,
            ),
        )
    }

    fun entries(): List<CatalogEntry> {
        val templates = PROGRAM_TEMPLATES.map { template ->
            val days = template.recipe?.daysPerWeek?.takeIf { it > 0 }
            CatalogEntry(
                id = "template:${template.id}", source = CatalogSource.TEMPLATE, sourceId = template.id,
                title = when (template.id) {
                    "simple-1" -> "Tu semana de entrenamiento"
                    "simple-ab" -> "Alterna dos semanas"
                    "simple-4" -> "Organiza cuatro semanas"
                    else -> template.name
                },
                technicalSubtitle = "${SpanishPlurals.weeks(template.weeks)} · " +
                    if (template.type == ProgramStructure.SIMPLE) "una fase" else SpanishPlurals.blocks(template.blockNames.size),
                description = template.description, requiredEquipment = setOf("general_gym"),
                supportedFrequencies = days?.let { it..it } ?: (1..6),
                level = if (template.type == ProgramStructure.SIMPLE) CatalogLevel.BEGINNER else CatalogLevel.ADVANCED,
                duration = if (template.weeks == 1) CatalogDuration.REPEATING_WEEK else CatalogDuration.FINITE_CYCLE,
                supportedFocuses = setOf(TrainingFocus.FULL_BODY), adaptation = AdaptationPolicy.FIXED_PRESCRIPTION,
                publication = PublicationState.PUBLISHED, template = template, sourceAuthor = "KPKN",
                references = templateReferences(template),
            )
        }
        val protocols = PROTOCOL_LIBRARY.filter { it.isVisibleForApplication }.map { protocol ->
            val weeks = protocol.recipe!!.weeks.size
            val days = protocol.recipe.daysPerWeek
            CatalogEntry(
                id = "protocol:${protocol.id}", source = CatalogSource.PROTOCOL, sourceId = protocol.id,
                title = friendlyMethods[protocol.id] ?: "Progresa con ${protocol.name}",
                technicalSubtitle = "${protocol.name} · ${SpanishPlurals.days(days)} · " +
                    if (protocol.recipe.repeats) "ciclo de ${SpanishPlurals.weeks(weeks)}" else SpanishPlurals.weeks(weeks),
                description = "Una planificación de ${SpanishPlurals.days(days)} por semana con una progresión definida. Conservamos el orden y las dosis del método; puedes revisar requisitos y detalle técnico antes de elegirlo.",
                requiredEquipment = setOf("general_gym"), supportedFrequencies = days..days,
                level = when (protocol.fidelitySpec?.claimedLevel?.lowercase()) {
                    "principiante", "beginner" -> CatalogLevel.BEGINNER
                    "avanzado", "advanced" -> CatalogLevel.ADVANCED
                    else -> CatalogLevel.INTERMEDIATE
                },
                duration = if (weeks == 1 && protocol.recipe.repeats) CatalogDuration.REPEATING_WEEK else CatalogDuration.FINITE_CYCLE,
                supportedFocuses = setOf(TrainingFocus.FULL_BODY), adaptation = AdaptationPolicy.FIXED_PRESCRIPTION,
                publication = PublicationState.PUBLISHED, sourceAuthor = protocol.author,
                sourceUrl = protocol.source.primaryUrl, sourceRevision = protocol.source.revision ?: protocol.source.catalogRevision,
                disclaimer = protocol.source.disclaimer, recipe = protocol.recipe,
                references = protocolReferences(protocol),
            )
        }
        return nativeSpecs.map(::nativeEntry) + ownProfileEntries.map(::ownProfileEntry) + templates + protocols + authoredEntriesLazy
    }

    fun find(id: String): CatalogEntry? = entries().firstOrNull { it.id == id }

    fun classify(source: CatalogSource, structure: ProgramStructure, blockCount: Int, mesocycleCount: Int, repeats: Boolean, weeksDiffer: Boolean): CatalogClassification =
        if (blockCount > 1 || mesocycleCount > 1) CatalogClassification.ADVANCED else CatalogClassification.SIMPLE
}
