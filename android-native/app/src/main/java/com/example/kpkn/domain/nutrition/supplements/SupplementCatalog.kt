package com.example.kpkn.domain.nutrition.supplements

/** Etiqueta de evidencia de un suplemento (decisión aprobada: las etiquetas sí). */
enum class SupplementEvidence(val label: String) {
    SOLID("Evidencia sólida"),
    MODERATE("Evidencia moderada"),
    AS_NEEDED("Según tu necesidad"),
    LIMITED("Evidencia limitada"),
}

/** Agrupación del catálogo en la hoja «Añadir suplemento». */
enum class SupplementGroup(val label: String) {
    PERFORMANCE("Rendimiento"),
    HEALTH("Salud"),
    REST("Descanso y estrés"),
}

/** Los 7 momentos del día en que se ordena la pila. El índice es el que guarda `SupplementPilaItem.moment`. */
val SUPPLEMENT_MOMENTS: List<String> = listOf(
    "Al despertar", "Con el desayuno", "Con la comida", "Antes de entrenar", "Tras entrenar", "Con la cena", "Antes de dormir",
)

/**
 * Una entrada del catálogo (criterio: ISSN para rendimiento, NIH ODS / EFSA para micronutrientes; sin marcas). [cap] es el techo diario
 * en [unit] (null = sin techo propio); [capScopeSupplementsOnly] marca el techo que solo cuenta lo tomado como suplemento (magnesio).
 * [noun] = la unidad es un sustantivo contable («1 porción», «2 porciones»).
 */
data class SupplementDef(
    val id: String,
    val name: String,
    val shortName: String,
    val group: SupplementGroup,
    val iconKey: String,
    val unit: String,
    val unitPlural: String? = null,
    val noun: Boolean = false,
    val defaultDose: Double,
    val step: Double,
    val min: Double,
    val max: Double,
    val typical: String,
    val evidence: SupplementEvidence,
    val defaultMoment: Int,
    val cap: Double?,
    val capText: String,
    val feeds: String,
    val capScopeSupplementsOnly: Boolean = false,
)

/** Catálogo de 11 (sin ashwagandha, hierro y melatonina: decisión aprobada). */
object SupplementCatalog {
    val entries: List<SupplementDef> = listOf(
        SupplementDef(
            "creatina", "Creatina monohidrato", "Creatina", SupplementGroup.PERFORMANCE, "cube", "g",
            defaultDose = 5.0, step = 1.0, min = 1.0, max = 30.0,
            typical = "3–5 g al día", evidence = SupplementEvidence.SOLID, defaultMoment = 1, cap = null,
            capText = "Sin techo fijo: 3–5 g al día es lo estudiado.",
            feeds = "Suma a la creatina del día y a su racha. No cuenta como vitamina ni mineral.",
        ),
        SupplementDef(
            "cafeina", "Cafeína", "Cafeína", SupplementGroup.PERFORMANCE, "bolt", "mg",
            defaultDose = 100.0, step = 25.0, min = 25.0, max = 400.0,
            typical = "3 mg por kg: 100–200 mg", evidence = SupplementEvidence.SOLID, defaultMoment = 3, cap = 400.0,
            capText = "Máximo 400 mg/día (6 mg/kg, tope 400 mg EFSA/FDA).",
            feeds = "Suma a la cafeína del día, junto con la de tus comidas y bebidas.",
        ),
        SupplementDef(
            "proteina", "Proteína en polvo (whey o vegetal)", "Proteína", SupplementGroup.PERFORMANCE, "shake", "g",
            defaultDose = 25.0, step = 5.0, min = 5.0, max = 60.0,
            typical = "20–40 g por toma", evidence = SupplementEvidence.SOLID, defaultMoment = 4, cap = null,
            capText = "Sin techo propio: cuenta dentro de tu proteína del día.",
            feeds = "Suma proteína y calorías a tu día (estimadas por gramo).",
        ),
        SupplementDef(
            "betaalanina", "Beta-alanina", "Beta-alanina", SupplementGroup.PERFORMANCE, "caps", "g",
            defaultDose = 1.6, step = 0.4, min = 0.4, max = 6.4,
            typical = "3,2–6,4 g al día, repartidos", evidence = SupplementEvidence.MODERATE, defaultMoment = 2, cap = null,
            capText = "Hasta 6,4 g/día en lo estudiado. El hormigueo es habitual.",
            feeds = "Solo registro: no cambia tu huella de nutrientes.",
        ),
        SupplementDef(
            "citrulina", "Citrulina (L-citrulina)", "Citrulina", SupplementGroup.PERFORMANCE, "citrus", "g",
            defaultDose = 6.0, step = 1.0, min = 1.0, max = 12.0,
            typical = "6–8 g antes de entrenar", evidence = SupplementEvidence.MODERATE, defaultMoment = 3, cap = null,
            capText = "Sin techo fijo: 6–8 g es lo estudiado.",
            feeds = "Solo registro: no cambia tu huella de nutrientes.",
        ),
        SupplementDef(
            "electrolitos", "Electrolitos", "Electrolitos", SupplementGroup.PERFORMANCE, "lyte", "porción",
            unitPlural = "porciones", noun = true, defaultDose = 1.0, step = 1.0, min = 1.0, max = 4.0,
            typical = "1 porción en esfuerzos largos o con calor", evidence = SupplementEvidence.MODERATE, defaultMoment = 3, cap = null,
            capText = "Depende de la etiqueta; el sodio ya cuenta en tu día.",
            feeds = "Suma sodio, potasio y magnesio según la etiqueta que configures.",
        ),
        SupplementDef(
            "vitd", "Vitamina D", "Vitamina D", SupplementGroup.HEALTH, "sun", "µg",
            defaultDose = 10.0, step = 5.0, min = 5.0, max = 100.0,
            typical = "10–25 µg (400–1.000 UI)", evidence = SupplementEvidence.AS_NEEDED, defaultMoment = 1, cap = 100.0,
            capText = "Límite superior 100 µg/día (NIH, EFSA).",
            feeds = "Suma a Vitamina D en vitaminas y minerales. Se compara con el límite total.",
        ),
        SupplementDef(
            "omega3", "Omega-3 (EPA + DHA)", "Omega-3", SupplementGroup.HEALTH, "fish", "g",
            defaultDose = 1.0, step = 0.25, min = 0.25, max = 5.0,
            typical = "0,25–2 g de EPA + DHA", evidence = SupplementEvidence.MODERATE, defaultMoment = 2, cap = 5.0,
            capText = "Hasta 5 g/día de EPA + DHA en suplemento (EFSA).",
            feeds = "Suma a Omega-3 EPA + DHA en vitaminas y minerales.",
        ),
        SupplementDef(
            "magnesio", "Magnesio", "Magnesio", SupplementGroup.HEALTH, "atom", "mg",
            defaultDose = 300.0, step = 50.0, min = 50.0, max = 700.0,
            typical = "200–400 mg", evidence = SupplementEvidence.AS_NEEDED, defaultMoment = 6, cap = 350.0,
            capText = "Límite 350 mg/día para suplementos (NIH).",
            feeds = "Suma a Magnesio. Se compara con el límite para suplementos, no con el total.",
            capScopeSupplementsOnly = true,
        ),
        SupplementDef(
            "zinc", "Zinc", "Zinc", SupplementGroup.HEALTH, "hex", "mg",
            defaultDose = 10.0, step = 5.0, min = 2.5, max = 40.0,
            typical = "8–15 mg", evidence = SupplementEvidence.AS_NEEDED, defaultMoment = 5, cap = 40.0,
            capText = "Límite superior 40 mg/día, sumando comida y suplementos.",
            feeds = "Suma a Zinc en vitaminas y minerales. Se compara con el límite total.",
        ),
        SupplementDef(
            "multi", "Multivitamínico", "Multivitamínico", SupplementGroup.HEALTH, "multi", "comprimido",
            unitPlural = "comprimidos", noun = true, defaultDose = 1.0, step = 1.0, min = 1.0, max = 3.0,
            typical = "1 comprimido al día, con comida", evidence = SupplementEvidence.AS_NEEDED, defaultMoment = 1, cap = null,
            capText = "Sigue la dosis de la etiqueta.",
            feeds = "Suma a las vitaminas y minerales de su etiqueta, que rellenas al configurarlo.",
        ),
    )

    fun byId(id: String): SupplementDef? = entries.firstOrNull { it.id == id }

    /** Los propios guardan su nombre y unidad junto a la pila, sin modificar el catálogo editorial. */
    fun resolve(item: com.example.kpkn.data.models.SupplementPilaItem): SupplementDef? {
        byId(item.id)?.let { return it }
        if (!item.id.startsWith("propio-")) return null
        val name = item.customName?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val unit = item.customUnit?.takeIf { it in setOf("g", "mg", "\u00b5g", "ud") } ?: return null
        return SupplementDef(id = item.id, name = name, shortName = name, group = SupplementGroup.HEALTH, iconKey = "caps",
            unit = unit, defaultDose = item.dose, step = if (unit == "mg") 50.0 else 1.0, min = 0.01, max = 9999.0,
            typical = "Según tu etiqueta", evidence = SupplementEvidence.LIMITED, defaultMoment = item.moment.coerceIn(SUPPLEMENT_MOMENTS.indices),
            cap = null, capText = "Sigue la dosis de tu etiqueta.", feeds = "Solo registro: no cambia tu huella de nutrientes.")
    }
}
