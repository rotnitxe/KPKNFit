package com.example.kpkn.data.programs

import com.example.kpkn.data.models.ProgramStructure
import com.example.kpkn.data.protocols.LiftSlot
import com.example.kpkn.data.protocols.PROTOCOL_LIBRARY
import com.example.kpkn.data.protocols.TrainingPlanRecipe
import com.example.kpkn.data.protocols.isVisibleForApplication
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
        NativeSpec("strength-cardio", "Fuerza y resistencia", "Combina series de fuerza con cardio de intensidad moderada. La vista previa reserva tiempo para ambas partes.", 2..4, setOf("general_gym")),
        NativeSpec("return-training", "Vuelve a entrenar", "Retoma la constancia con una entrada conservadora. Empezamos por una dosis manejable, no por el máximo volumen.", 2..3, setOf("general_gym")),
        NativeSpec("one-day", "Aprovecha un solo día", "Una sesión equilibrada cuando tu semana deja poco espacio. Priorizamos lo posible sin prometer la frecuencia de un plan de varios días.", 1..1, setOf("general_gym")),
    )

    private fun nativeEntry(spec: NativeSpec) = CatalogEntry(
        id = "native:${spec.id}", source = CatalogSource.NATIVE, sourceId = spec.id,
        title = spec.title,
        technicalSubtitle = "Semana cíclica · ${if (spec.frequencies.first == spec.frequencies.last) spec.frequencies.first.toString() else "${spec.frequencies.first}–${spec.frequencies.last}"} días",
        description = spec.description, requiredEquipment = spec.equipment,
        supportedFrequencies = spec.frequencies, level = spec.level, duration = CatalogDuration.REPEATING_WEEK,
        supportedFocuses = TrainingFocus.entries.toSet(), adaptation = AdaptationPolicy.CURATED_WEEKLY,
        publication = PublicationState.PUBLISHED, sourceAuthor = "KPKN", sourceRevision = REVISION,
        // SimpleCyclePersonalizer generates hypertrophy cycles; they are never
        // relabelled as another discipline.
        references = setOf(TrainingReference.HYPERTROPHY),
    )

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
                technicalSubtitle = "${template.weeks} semanas · ${if (template.type == ProgramStructure.SIMPLE) "una fase" else "${template.blockNames.size} bloques"}",
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
                technicalSubtitle = "${protocol.name} · $days días · ${if (protocol.recipe.repeats) "ciclo de $weeks semanas" else "$weeks semanas"}",
                description = "Una planificación de $days días por semana con una progresión definida. Conservamos el orden y las dosis del método; puedes revisar requisitos y detalle técnico antes de elegirlo.",
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
        return nativeSpecs.map(::nativeEntry) + templates + protocols
    }

    fun find(id: String): CatalogEntry? = entries().firstOrNull { it.id == id }

    fun classify(source: CatalogSource, structure: ProgramStructure, blockCount: Int, mesocycleCount: Int, repeats: Boolean, weeksDiffer: Boolean): CatalogClassification =
        if (blockCount > 1 || mesocycleCount > 1) CatalogClassification.ADVANCED else CatalogClassification.SIMPLE
}
