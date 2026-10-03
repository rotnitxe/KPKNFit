package com.example.kpkn.data.programs

import com.example.kpkn.data.models.WeekExecutionKind
import com.example.kpkn.data.protocols.LoadBasis
import com.example.kpkn.data.protocols.SlotRole
import com.example.kpkn.data.protocols.TechniqueModifier
import com.example.kpkn.data.protocols.TrainingPlanRecipe

/**
 * Qué clase de plan es una entrada del catálogo (§1.1 del diseño editorial).
 *
 * - [PLAN]: un plan completo que se elige para entrenar.
 * - [ESPECIALIZACION]: trabaja un solo levantamiento (p. ej. Smolov: solo sentadilla).
 * - [COMPLEMENTO]: se suma a otro plan, no lo sustituye (p. ej. Coan y Philippi).
 * - [ESTRUCTURA]: esqueleto en blanco que el usuario rellena (plantillas simples).
 *
 * Todo lo que no es [PLAN] se ordena después de los planes (`rank >= 900`).
 */
enum class PlanKind { PLAN, ESPECIALIZACION, COMPLEMENTO, ESTRUCTURA }

/** De dónde sale el plan; decide la etiqueta de procedencia que ve el usuario. */
enum class PlanOrigin {
    /** Plan propio de KPKN (nativos, plantillas y planes KPKN con receta fija). */
    KPKN,

    /** Original fiel de un autor (PHUL y PHAT originales). */
    ORIGINAL,

    /** Adaptación KPKN de un original, con sustituciones de material. */
    ADAPTED,

    /** Versión KPKN de un método publicado por un tercero. */
    KPKN_VERSION,

    /** Versión heredada que sigue disponible para quien ya la usa. */
    LEGACY_VERSION,
}

/** Términos técnicos que la hoja del plan puede explicar en su glosario. */
enum class PlanTerm {
    ONE_RM,
    TM,
    AMRAP,
    RPE,
    RIR,
    DUP,
    MEV_MRV,
    SBD,
    TIERS,
    TOP_SET,
    SPEED,
    DELOAD,
    CYCLE,
    POWERLIFTING,
    POWERBUILDING,
    UL_PPL,
}

/**
 * Ficha editorial única de una entrada del catálogo: todo el texto de usuario
 * y la información de orden que las pantallas necesitan. La fuente de verdad
 * es [PlanEditorialTable]; [CatalogEntry] la expone con getters.
 */
data class PlanEditorial(
    /** Título único de usuario: sin ids, sin inglés gratuito y sin los días (salvo Texas). */
    val displayName: String,
    /** Qué haces, para quién es y qué necesitas, en 2 a 4 frases. */
    val summary: String,
    val kind: PlanKind = PlanKind.PLAN,
    val origin: PlanOrigin,
    /** Orden editorial ascendente; reemplaza el desempate por id. */
    val rank: Int,
    /** Niveles para los que sirve el plan; los adaptativos declaran los tres. */
    val levels: Set<CatalogLevel>,
    val attributionLine: String? = null,
    /** «Notas del método» en lenguaje llano. */
    val notes: List<String> = emptyList(),
    /** Términos editoriales; los que se deducen de la receta se suman en [CatalogEntry.terms]. */
    val terms: Set<PlanTerm> = emptySet(),
    /** Override editorial de disciplina. En este paso SIEMPRE null (C.P2b lo activa). */
    val references: Set<TrainingReference>? = null,
    /** false = oculto de las listas. En este paso SIEMPRE true (C.P2b oculta los históricos). */
    val listed: Boolean = true,
) {
    init {
        require(levels.isNotEmpty()) { "PlanEditorial.levels no puede estar vacío: «$displayName»" }
    }

    /** Nombre corto: el título sin el paréntesis final (por ejemplo, sin los días). */
    val shortName: String get() = displayName.substringBefore(" (")
}

/**
 * Términos que se deducen de una receta fija, sin texto escrito a mano. Solo
 * cuentan las series de trabajo: los calentamientos no definen el método.
 *
 * - AMRAP: alguna serie al máximo de repeticiones.
 * - RPE / RIR: alguna serie con esfuerzo percibido o repeticiones en reserva.
 * - TM: algún porcentaje sobre el máximo de entrenamiento.
 * - ONE_RM: algún porcentaje sobre el 1RM o sobre el máximo deseado.
 * - TOP_SET: alguna serie top o porcentaje sobre la serie top.
 * - SPEED: algún ejercicio de velocidad (técnica o rol SPEED).
 * - DELOAD: alguna semana de descarga.
 * - CYCLE: la receta se repite.
 */
fun derivedTerms(recipe: TrainingPlanRecipe?): Set<PlanTerm> {
    if (recipe == null) return emptySet()
    val terms = mutableSetOf<PlanTerm>()
    if (recipe.repeats) terms += PlanTerm.CYCLE
    recipe.weeks.forEach { week ->
        if (week.kind == WeekExecutionKind.DELOAD) terms += PlanTerm.DELOAD
        week.days.forEach { day ->
            day.slots.forEach { slot ->
                if (slot.technique == TechniqueModifier.SPEED || slot.role == SlotRole.SPEED) terms += PlanTerm.SPEED
                slot.sets.filterNot { it.isWarmup }.forEach { set ->
                    if (set.amrap) terms += PlanTerm.AMRAP
                    if (set.rpe != null) terms += PlanTerm.RPE
                    if (set.rir != null) terms += PlanTerm.RIR
                    if (set.isTopSet || set.loadBasis == LoadBasis.PERCENT_OF_TOP_SET) terms += PlanTerm.TOP_SET
                    val hasPercent = set.percent != null
                    if (hasPercent && set.loadBasis == LoadBasis.PERCENT_TM) terms += PlanTerm.TM
                    if (hasPercent && (set.loadBasis == LoadBasis.PERCENT_1RM || set.loadBasis == LoadBasis.PERCENT_DESIRED_MAX)) {
                        terms += PlanTerm.ONE_RM
                    }
                }
            }
        }
    }
    return terms
}
