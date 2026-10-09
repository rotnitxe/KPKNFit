package com.example.kpkn.domain.onboarding

import com.example.kpkn.data.models.EquipmentAvailability
import com.example.kpkn.domain.training.ConfigurationEquipmentFilter
import com.example.kpkn.domain.training.TrainingOptions
import com.example.kpkn.domain.training.machineConfigToken
import com.example.kpkn.domain.training.resolveEffectiveEquipment
import com.example.kpkn.domain.training.supportRequirementsFor

/**
 * Una variante de un levantamiento básico del paso TRAINING_VARIANTS. [configId] es la configuración real del catálogo
 * (`exercise_catalog_v2.json`); [equipmentId] es su implemento de catálogo, que el filtro de material necesita; y
 * [takesMark] dice si la marca del levantamiento se traduce a una carga de barra (no en mancuernas ni en búlgaras).
 */
data class LiftVariantOption(
    val configId: String,
    val label: String,
    val equipmentId: String,
    val takesMark: Boolean = true,
)

/**
 * Variantes de los básicos que el paso TRAINING_VARIANTS ofrece por perfil: powerlifting (sentadilla y peso muerto) y
 * powerbuilding (sentadilla, banca, peso muerto y press militar). La primera de cada lista es «la de siempre», la que hoy
 * elige el generador (`MovementPools`).
 *
 * El material se filtra con el MISMO criterio que el generador: [ConfigurationEquipmentFilter.allowsPrecomputed] con
 * [supportRequirementsFor] y el equipo efectivo de [TrainingOptions]. Así una opción solo se ofrece si el generador podría
 * usar esa configuración con el material declarado. Consecuencia de ese criterio: la sentadilla Zercher pide solo barra,
 * porque el generador no le exige rack.
 */
object LiftVariants {

    private val squatHighBar = LiftVariantOption("high_bar_back_squat__barbell", "Barra alta", "barbell")
    private val squatLowBar = LiftVariantOption("low_bar_back_squat__barbell", "Barra baja", "barbell")
    private val squatFront = LiftVariantOption("front_squat__barbell", "Frontal", "barbell")
    private val squatZercher = LiftVariantOption("quads_sentadilla_zercher_barra_recta__default", "Zercher", "barbell")
    private val squatBulgarianDumbbells =
        LiftVariantOption("bulgarian_split_squat__dumbbells", "Búlgara con mancuernas", "dumbbells", takesMark = false)
    private val squatBulgarianBarbell =
        LiftVariantOption("bulgarian_split_squat__barbell", "Búlgara con barra", "barbell", takesMark = false)

    private val benchFlat = LiftVariantOption("bench_press__barbell", "Plana con barra", "barbell")
    private val benchDumbbells = LiftVariantOption("bench_press__dumbbells", "Con mancuernas", "dumbbells", takesMark = false)
    private val benchIncline = LiftVariantOption("incline_bench_press__barbell", "Inclinada", "barbell")
    private val benchCloseGrip = LiftVariantOption("close_grip_bench_press__barbell", "Agarre cerrado", "barbell")

    private val deadliftConventional =
        LiftVariantOption("conventional_deadlift__bilateral__barbell", "Convencional", "barbell")
    private val deadliftSumo = LiftVariantOption("sumo_deadlift__barbell", "Sumo", "barbell")
    private val deadliftRomanian = LiftVariantOption("romanian_deadlift__bilateral__barbell", "Rumano", "barbell")
    private val deadliftHex = LiftVariantOption("conventional_deadlift__bilateral__hex_bar", "Hexagonal", "hex_bar")

    private val pressBarbell = LiftVariantOption("military_press__barbell", "Con barra", "barbell")
    private val pressDumbbells = LiftVariantOption("military_press__dumbbells", "Con mancuernas", "dumbbells", takesMark = false)

    /** Variantes por perfil y levantamiento, en el orden del paso. Sin entrada, el perfil no pregunta ese levantamiento. */
    private val options: Map<TrainingGoalProfile, Map<LiftMark, List<LiftVariantOption>>> = mapOf(
        TrainingGoalProfile.POWERLIFTING to mapOf(
            LiftMark.SQUAT to listOf(squatHighBar, squatLowBar),
            LiftMark.DEADLIFT to listOf(deadliftConventional, deadliftSumo),
        ),
        TrainingGoalProfile.POWERBUILDING to mapOf(
            LiftMark.SQUAT to listOf(squatHighBar, squatLowBar, squatFront, squatZercher, squatBulgarianDumbbells, squatBulgarianBarbell),
            LiftMark.BENCH to listOf(benchFlat, benchDumbbells, benchIncline, benchCloseGrip),
            LiftMark.DEADLIFT to listOf(deadliftConventional, deadliftSumo, deadliftRomanian, deadliftHex),
            LiftMark.OVERHEAD_PRESS to listOf(pressBarbell, pressDumbbells),
        ),
    )

    /** La de siempre de cada levantamiento: la primera de su lista (la que hoy usa el generador). */
    private val defaults: Map<LiftMark, String> = buildMap {
        for (perLift in options.values) {
            for ((lift, list) in perLift) putIfAbsent(lift, list.first().configId)
        }
    }

    private val allOptions: List<LiftVariantOption> = options.values.flatMap { perLift -> perLift.values.flatten() }.distinct()

    private fun perLiftOf(goal: TrainingGoalProfile?): Map<LiftMark, List<LiftVariantOption>>? = goal?.let { options[it] }

    /** Levantamientos que tienen variantes en el perfil [goal], en el orden del paso (vacío si ninguno). */
    fun liftsFor(goal: TrainingGoalProfile?): List<LiftMark> = perLiftOf(goal)?.keys?.toList().orEmpty()

    /**
     * Variantes de [lift] en el perfil [goal] que el material [availability] permite. Vacío si el perfil no tiene variantes
     * de ese levantamiento o si el material no alcanza a ninguna.
     */
    fun optionsFor(goal: TrainingGoalProfile?, lift: LiftMark, availability: EquipmentAvailability?): List<LiftVariantOption> {
        val candidates = perLiftOf(goal)?.get(lift).orEmpty()
        if (candidates.isEmpty()) return emptyList()
        val tokens = TrainingOptions(availability = availability).resolveEffectiveEquipment(emptySet()).tokens
        return candidates.filter { option -> offeredWith(option, tokens) }
    }

    /** La variante de siempre de [lift] (la que hoy elige el generador). */
    fun defaultFor(lift: LiftMark): String = defaults.getValue(lift)

    /** Nombre visible de la variante [configId], o null si no es una variante del paso. */
    fun labelOf(configId: String): String? = allOptions.firstOrNull { it.configId == configId }?.label

    /** ¿[configId] es una variante de [lift] en algún perfil? */
    fun belongsTo(lift: LiftMark, configId: String): Boolean =
        options.values.any { perLift -> perLift[lift]?.any { it.configId == configId } == true }

    /**
     * La variante que se usa al armar el programa: la guardada si el material la permite todavía; si no, la de siempre. El
     * dato guardado nunca se borra. Null si [lift] no tiene variantes en el perfil [goal].
     */
    fun effectiveFor(goal: TrainingGoalProfile?, lift: LiftMark, stored: String?, availability: EquipmentAvailability?): String? {
        if (perLiftOf(goal)?.get(lift) == null) return null
        val offered = optionsFor(goal, lift, availability).map { it.configId }
        return stored?.takeIf { it in offered } ?: defaultFor(lift)
    }

    /**
     * ¿Admite la marca de [lift] la variante que se usa ([effectiveFor])? Sin variantes, sí: el levantamiento se mide como
     * siempre. Las mancuernas y las búlgaras no se traducen a una carga de barra, así que no admiten marca.
     */
    fun takesMarkFor(goal: TrainingGoalProfile?, lift: LiftMark, stored: String?, availability: EquipmentAvailability?): Boolean {
        val configId = effectiveFor(goal, lift, stored, availability) ?: return true
        return allOptions.firstOrNull { it.configId == configId }?.takesMark ?: true
    }

    /** El filtro de material del generador aplicado a una variante (ver la clase). */
    private fun offeredWith(option: LiftVariantOption, tokens: Set<String>): Boolean =
        ConfigurationEquipmentFilter.allowsPrecomputed(
            equipmentId = option.equipmentId,
            machineToken = machineConfigToken(option.configId),
            supportRequirements = supportRequirementsFor(option.configId),
            tokens = tokens,
        )
}
