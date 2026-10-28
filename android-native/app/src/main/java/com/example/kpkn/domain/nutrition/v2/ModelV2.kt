package com.example.kpkn.domain.nutrition.v2

// ---- Catálogo: concepts_export.json en el examen y los tests; el paquete SQLite en la app ----

enum class ConceptKindV2 { FOOD, DISH, DRINK, SUPPLEMENT, CONDIMENT }

/** Ficha de nutrientes por 100 g; claves = NutrientId.key («energy», «protein»…); ausente = sin dato. */
data class RecordV2(
    val id: String,
    val name: String,
    val per100g: Map<String, Double>,
    val portions: List<RecordPortionV2> = emptyList(),
    val densityGPerMl: Double? = null,
)

/** Porción de la ficha tal como viene de la fuente («cup», «1 slice», «can»). */
data class RecordPortionV2(val label: String, val grams: Double)

/** Variante de un concepto: [state] null = NA; [recordId] null = sin ficha (noMatch). */
data class ConceptVariantV2(
    val state: FoodStateV2?,
    val form: FoodFormV2,
    val recordId: String?,
    val proxy: Boolean = false,
)

data class CountUnitV2(val names: List<String>, val grams: Map<SizeV2, Double>)

data class ConceptPortionV2(val label: String, val grams: Double, val state: FoodStateV2? = null)

data class ReconstitutionV2(val gramsPer100ml: Double, val medium: MediumV2)

data class DishComponentV2(val recordId: String?, val grams: Double, val note: String = "")

data class ConceptV2(
    val id: String,
    val name: String,
    val kind: ConceptKindV2,
    val category: String,
    val aliasesEs: List<String>,
    val aliasesEn: List<String>,
    val typos: List<String>,
    val defaultState: FoodStateV2?,
    val defaultForm: FoodFormV2,
    val variants: List<ConceptVariantV2>,
    val countable: Boolean,
    val unit: CountUnitV2?,
    val portions: List<ConceptPortionV2>,
    val defaultGrams: Double,
    val densityGPerMl: Double?,
    val reconstitution: ReconstitutionV2?,
    val components: List<DishComponentV2>?,
)

class ConceptCatalogV2(
    val generation: String,
    conceptList: List<ConceptV2>,
    val records: Map<String, RecordV2>,
) {
    val concepts: Map<String, ConceptV2> = conceptList.associateBy { it.id }

    fun concept(id: String): ConceptV2? = concepts[id]

    fun record(id: String?): RecordV2? = id?.let { records[it] }

    fun hasForm(conceptId: String, form: FoodFormV2): Boolean =
        concepts[conceptId]?.variants?.any { it.form == form } == true

    fun hasState(conceptId: String, state: FoodStateV2): Boolean =
        concepts[conceptId]?.variants?.any { it.state == state } == true
}

// ---- Gramática (F2-b) → resolutor (F2-c) ----

sealed interface QuantityV2 {
    /** «150 g», «medio kilo». */
    data class Mass(val grams: Double) : QuantityV2

    /** «200 ml», «un litro». */
    data class Volume(val ml: Double) : QuantityV2

    /** «2 huevos», «una manzana grande», «medio pan» (n = 0.5). */
    data class Count(val n: Double, val size: SizeV2? = null) : QuantityV2

    /** «2 cucharadas», «una taza», «un plato»: [label] = etiqueta canónica de LexTag.Household. */
    data class Household(val n: Double, val label: String, val size: SizeV2? = null) : QuantityV2

    /** «una lata», «media botella», «medio yogur de un envase de 125 g» (contenido neto si el texto lo dice). */
    data class Container(val n: Double, val label: String, val netGrams: Double? = null, val netMl: Double? = null) : QuantityV2

    /** «un poco de», «algo de», «a bit of». */
    data object Vague : QuantityV2
}

/** Mención que arma la gramática; los tramos son desplazamientos en el texto original. */
data class MentionDraftV2(
    val start: Int,
    val end: Int,
    val foodStart: Int,
    val foodEnd: Int,
    val conceptIds: List<String>,
    val quantity: QuantityV2?,
    val state: FoodStateV2? = null,
    val form: FoodFormV2? = null,
    val medium: MediumV2? = null,
    val excluded: Boolean = false,
    val fuzzy: Boolean = false,
    val attachedTo: Int? = null,
)

// ---- Resolutor (F2-c) ----

enum class QuantityKindV2 { MASS, VOLUME, COUNT, HOUSEHOLD, CONTAINER, VAGUE, DEFAULT }

enum class QuestionDimensionV2 { IDENTITY, QUANTITY, STATE, FORM }

/** Pregunta material: [options] = ids de concepto (IDENTITY) o nombres de estado/forma/porción. */
data class QuestionV2(val dimension: QuestionDimensionV2, val options: List<String>)

data class MentionV2(
    val draft: MentionDraftV2,
    val text: String,
    val conceptId: String?,
    val conceptName: String?,
    val recordId: String?,
    val grams: Double?,
    val quantityKind: QuantityKindV2,
    val state: FoodStateV2?,
    val form: FoodFormV2,
    val per100g: Map<String, Double>,
    val eatenKcal: Double?,
    val eatenProtein: Double?,
    val eatenFat: Double?,
    val eatenCarbs: Double?,
    val excluded: Boolean,
    val confidence: Double,
    val questions: List<QuestionV2>,
    val autoAccepted: Boolean,
)

data class MealParseV2(val text: String, val mentions: List<MentionV2>)
