package com.example.kpkn.data.wikilab

/**
 * Editorial provenance for Conceptos Clave.
 *
 * These references are intentionally not rendered in the accordion. They
 * keep the long-form copy auditable and prevent unsupported claims from
 * becoming part of the static learning database.
 */
data class ConceptSourceRef(
    val title: String,
    val publisher: String,
    val url: String,
)

private val resistancePrescription = ConceptSourceRef(
    title = "Resistance training prescription for muscle strength and hypertrophy in healthy adults",
    publisher = "British Journal of Sports Medicine / PubMed",
    url = "https://pubmed.ncbi.nlm.nih.gov/37414459/",
)

private val volumeReview = ConceptSourceRef(
    title = "A systematic review of the effects of different resistance training volumes on muscle hypertrophy",
    publisher = "Journal of Human Kinetics / PubMed",
    url = "https://pubmed.ncbi.nlm.nih.gov/35291645/",
)

private val frequencyReview = ConceptSourceRef(
    title = "How many times per week should a muscle be trained to maximize hypertrophy?",
    publisher = "Journal of Sports Sciences / PubMed",
    url = "https://pubmed.ncbi.nlm.nih.gov/30558493/",
)

private val rirReview = ConceptSourceRef(
    title = "Feasibility and usefulness of repetitions-in-reserve scales",
    publisher = "Sports Medicine / PubMed",
    url = "https://pubmed.ncbi.nlm.nih.gov/38563729/",
)

private val proximityReview = ConceptSourceRef(
    title = "Proximity-to-failure and its influence on hypertrophy, fatigue and muscle damage",
    publisher = "Journal of Sports Sciences / PubMed",
    url = "https://pubmed.ncbi.nlm.nih.gov/35658845/",
)

private val damageReview = ConceptSourceRef(
    title = "The development of skeletal muscle hypertrophy: the role of muscle damage",
    publisher = "European Journal of Applied Physiology / PubMed",
    url = "https://pubmed.ncbi.nlm.nih.gov/29282529/",
)

private val loadMechanismsReview = ConceptSourceRef(
    title = "Load-induced human skeletal muscle hypertrophy: mechanisms, myths and misconceptions",
    publisher = "Sports Medicine / PubMed",
    url = "https://pubmed.ncbi.nlm.nih.gov/41276164/",
)

private val romReview = ConceptSourceRef(
    title = "Effects of range of motion on resistance training adaptations",
    publisher = "Scandinavian Journal of Medicine & Science in Sports / PubMed",
    url = "https://pubmed.ncbi.nlm.nih.gov/34170576/",
)

private val contractionReview = ConceptSourceRef(
    title = "Comparison between eccentric vs. concentric muscle actions on hypertrophy",
    publisher = "Journal of Strength and Conditioning Research / PubMed",
    url = "https://pubmed.ncbi.nlm.nih.gov/39652733/",
)

private val plyometricReview = ConceptSourceRef(
    title = "Effects of plyometric training on physical performance: an umbrella review",
    publisher = "Sports Medicine / PubMed",
    url = "https://pubmed.ncbi.nlm.nih.gov/36625965/",
)

private val equipmentReview = ConceptSourceRef(
    title = "Effect of free-weight vs. machine-based strength training",
    publisher = "BMC Sports Science, Medicine and Rehabilitation / PubMed",
    url = "https://pubmed.ncbi.nlm.nih.gov/37582807/",
)

private val tendonReview = ConceptSourceRef(
    title = "Mechanical, material and morphological adaptations of healthy lower limb tendons",
    publisher = "Sports Medicine / PubMed",
    url = "https://pubmed.ncbi.nlm.nih.gov/35657492/",
)

private val rateOfForceReview = ConceptSourceRef(
    title = "Effects of resistance training movement pattern and velocity on rate of force development",
    publisher = "Sports Medicine / PubMed",
    url = "https://pubmed.ncbi.nlm.nih.gov/32034703/",
)

private val enduranceReview = ConceptSourceRef(
    title = "Effect of resistance training on local muscle endurance",
    publisher = "Archives of Gerontology and Geriatrics / PubMed",
    url = "https://pubmed.ncbi.nlm.nih.gov/36758486/",
)

private val periodizationReview = ConceptSourceRef(
    title = "Effects of periodization on strength and muscle hypertrophy",
    publisher = "Sports Medicine / PubMed",
    url = "https://pubmed.ncbi.nlm.nih.gov/35044672/",
)

private val specificityReview = ConceptSourceRef(
    title = "Task specificity of dynamic resistance training and transferability",
    publisher = "European Journal of Sport Science / PubMed",
    url = "https://pubmed.ncbi.nlm.nih.gov/40314751/",
)

/**
 * Every runtime concept must have at least one source. Keep this map keyed by
 * stable concept ID so editorial updates cannot silently orphan provenance.
 */
val TRAINING_CONCEPT_SOURCES: Map<String, List<ConceptSourceRef>> = mapOf(
    "volumen-entrenamiento" to listOf(resistancePrescription, volumeReview),
    "intensidad" to listOf(resistancePrescription, loadMechanismsReview),
    "frecuencia" to listOf(frequencyReview, resistancePrescription),
    "rir" to listOf(rirReview, proximityReview),
    "rpe" to listOf(rirReview, proximityReview),
    "fallo-muscular" to listOf(proximityReview, resistancePrescription),
    "fatiga-sistemica" to listOf(proximityReview, periodizationReview),
    "dano-muscular" to listOf(damageReview, proximityReview),
    "tension-mecanica" to listOf(loadMechanismsReview, romReview),
    "estres-metabolico" to listOf(loadMechanismsReview, damageReview),
    "rom" to listOf(romReview, loadMechanismsReview),
    "perfil-resistencia" to listOf(loadMechanismsReview, romReview),
    "fase-excentrica" to listOf(contractionReview, damageReview),
    "fase-concentrica" to listOf(contractionReview, rateOfForceReview),
    "isometria" to listOf(specificityReview, rateOfForceReview),
    "pliometria" to listOf(plyometricReview, tendonReview),
    "carga-axial" to listOf(loadMechanismsReview, specificityReview),
    "pesos-libres" to listOf(equipmentReview, specificityReview),
    "maquinas" to listOf(equipmentReview, specificityReview),
    "poleas" to listOf(equipmentReview, loadMechanismsReview),
    "fuerza" to listOf(resistancePrescription, rateOfForceReview),
    "explosividad" to listOf(rateOfForceReview, plyometricReview),
    "elasticidad" to listOf(tendonReview, plyometricReview),
    "resistencia-muscular" to listOf(enduranceReview, resistancePrescription),
    "sobrecarga-progresiva" to listOf(resistancePrescription, periodizationReview),
    "deload" to listOf(periodizationReview, proximityReview),
    "especificidad" to listOf(specificityReview, periodizationReview),
)

fun sourceReferencesForConcept(id: String): List<ConceptSourceRef> =
    TRAINING_CONCEPT_SOURCES[id].orEmpty()
