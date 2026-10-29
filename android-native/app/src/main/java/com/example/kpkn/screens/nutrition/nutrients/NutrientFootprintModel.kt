package com.example.kpkn.screens.nutrition.nutrients

import com.example.kpkn.data.models.LoggedFood
import com.example.kpkn.domain.nutrition.DriKind
import com.example.kpkn.domain.nutrition.DriProfile
import com.example.kpkn.domain.nutrition.DriSex
import com.example.kpkn.domain.nutrition.DriTarget
import com.example.kpkn.domain.nutrition.DriTargets
import com.example.kpkn.domain.nutrition.FootprintSlot
import com.example.kpkn.domain.nutrition.GuideLimit
import com.example.kpkn.domain.nutrition.GuideLimits
import com.example.kpkn.domain.nutrition.NutrientCoverage
import com.example.kpkn.domain.nutrition.NutrientDayTotal
import com.example.kpkn.domain.nutrition.NutrientFoodCoverage
import com.example.kpkn.domain.nutrition.NutrientGroup
import com.example.kpkn.domain.nutrition.NutrientKey
import com.example.kpkn.domain.nutrition.UpperLimitScope
import com.example.kpkn.domain.nutrition.footprintSlots
import com.example.kpkn.domain.nutrition.group
import com.example.kpkn.domain.nutrition.groupedNutrientKeys
import com.example.kpkn.domain.nutrition.isCeiling
import com.example.kpkn.domain.nutrition.nutrients.NutrientId
import com.example.kpkn.domain.nutrition.nutrients.withNutrientSnapshot
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import kotlin.math.roundToInt

/*
 * Modelos puros de «Vitaminas y minerales» (lienzo Registros v4, scenes-micro.js). Sin Compose: se prueban en la JVM.
 * Regla de todo el archivo: «sin dato» nunca es 0. Un total null se pinta como sin dato; un 0 declarado es dato.
 */

/** De qué se habla: una ración (la ficha), una comida (el registro) o el día. Cambia algunas notas de las filas. */
enum class NutrientScope { FOOD, MEAL, DAY }

/** Una barra de la huella. [percent] null = sin referencia o sin dato; [height] 0..1 es lo que se dibuja (null = sin dato). */
data class FootprintBar(
    val slot: FootprintSlot,
    val amount: Double?,
    val percent: Double?,
    val height: Float?,
    val estimatedShare: Float,
    val highlighted: Boolean,
) {
    val key: NutrientKey get() = slot.key
    val ceiling: Boolean get() = slot.key.isCeiling
    val over: Boolean get() = (percent ?: 0.0) > 100.0
}

/** Una píldora de «Destaca en»: nombre y porcentaje; [warn] = un tope que pasa del 25 % («N % del máx.»). */
data class NutrientHighlight(val name: String, val percentText: String, val warn: Boolean)

/**
 * La huella de nutrientes: 24 barras en el orden del lienzo, si es la del día ([day]: lo que llega al 100 % en tinta plena, lo que falta
 * en tenue, sin destacados) y si se dibuja en cantidades relativas ([relative]: sin referencia DRI, sin línea de 100 %).
 */
data class NutrientFootprintModel(
    val bars: List<FootprintBar>,
    val relative: Boolean,
    val day: Boolean,
    /** «Destaca en»: las tres que más aportan y los topes que pasan del 25 %. Vacío en modo relativo. */
    val highlights: List<NutrientHighlight>,
    /** «N de 38 nutrientes con dato» (solo una ración); null si no aplica. */
    val nutrientsWithData: Int? = null,
) {
    /** Etiqueta de un producto que solo trae la tabla de la etiqueta (n-ficha-label). */
    val labelOnly: Boolean get() = nutrientsWithData != null && nutrientsWithData <= NutrientFootprints.LABEL_ONLY_MAX

    companion object {
        val EMPTY = NutrientFootprintModel(footprintSlots.map { FootprintBar(it, null, null, null, 0f, false) }, relative = true, day = false, highlights = emptyList())
    }
}

/** Las cuatro cifras del día: al día · cerca (70–99 %) · por debajo · sobre el máx. Sobre las 24 de la huella. */
data class NutrientDayCounts(val onTrack: Int, val near: Int, val below: Int, val overMax: Int)

/** Una fila de lista (`np-nrow` del lienzo): nombre, cantidad, % y una barra fina de 3 dp. */
data class NutrientRowModel(
    val key: NutrientKey,
    val name: String,
    /** «11,4 mg», «≥ 4,2 mg» o «sin dato». */
    val valueText: String,
    val missing: Boolean,
    /** «142 %» o «—» (sin referencia); null si falta el dato. */
    val percentText: String?,
    /** Tope: la cantidad lleva «· máx.» y la pista de la barra es un contorno. */
    val ceiling: Boolean,
    val over: Boolean,
    val noReference: Boolean,
    val fill: Float,
    val estimatedShare: Float,
    val note: String?,
    /** Algún alimento no trae este dato: el total es «≥» y la pista de la barra va discontinua. */
    val partial: Boolean = false,
)

/** Las listas por grupo y, solo en el día, «A vigilar» (lo que pasa un tope sube arriba). */
data class NutrientListModel(val watch: List<NutrientRowModel>, val groups: List<Pair<NutrientGroup, List<NutrientRowModel>>>) {
    fun rowsOf(group: NutrientGroup): List<NutrientRowModel> = groups.firstOrNull { it.first == group }?.second.orEmpty()

    /** Las filas de un grupo en su orden de pantalla, con lo de [watch] de vuelta en su sitio: el lienzo no separa un «A vigilar». */
    fun orderedRowsOf(group: NutrientGroup): List<NutrientRowModel> {
        val byKey = (watch + groups.flatMap { it.second }).associateBy { it.key }
        return groupedNutrientKeys().firstOrNull { it.first == group }?.second.orEmpty().mapNotNull { byKey[it] }
    }
}

/** Un alimento de «De dónde sale». [fraction] es relativa al que más aporta. */
data class NutrientContributorRow(val name: String, val valueText: String, val fraction: Float, val estimated: Boolean)

/** La hoja de un nutriente (n-nutrient). */
data class NutrientDetailModel(
    val key: NutrientKey,
    val eyebrow: String,
    val name: String,
    val bigValue: String,
    val ofText: String,
    val percentText: String?,
    val ceiling: Boolean,
    val fill: Float?,
    val over: Boolean,
    val alert: String?,
    val contributors: List<NutrientContributorRow>,
    val missingNote: String?,
    val references: List<Pair<String, String>>,
    val notes: List<String>,
    val sourceLine: String,
)

/**
 * Todo lo de una ración para la ficha y la hoja de ajuste: la huella (con «N de 38»), la lista por grupos y, si la ficha es solo de
 * etiqueta, el texto del aviso. [portion] es lo que va en la cabecera de la lista («1 filete · 150 g»).
 */
data class NutrientFoodModel(
    val name: String,
    val portion: String,
    val totals: List<NutrientDayTotal>,
    val footprint: NutrientFootprintModel,
    val list: NutrientListModel,
    val labelOnlyText: String?,
    val profile: DriProfile?,
    val energyKcal: Double?,
) {
    /** «1 filete · 150 g · % de tu día» (o «· cantidades relativas» sin referencia). */
    val listSubtitle: String get() = listOf(portion.takeIf { it.isNotBlank() }, if (profile == null) "cantidades relativas" else "% de tu día").filterNotNull().joinToString(" · ")
}

/** Todo lo de una comida para el registro: huella, cobertura por alimento y lista. */
data class NutrientMealModel(
    val name: String,
    val foodCount: Int,
    val totals: List<NutrientDayTotal>,
    val coverage: NutrientFoodCoverage,
    val footprint: NutrientFootprintModel,
    val list: NutrientListModel,
    val profile: DriProfile?,
    val energyKcal: Double?,
) {
    /** «Desayuno · 4 alimentos · % de tu día». */
    val listSubtitle: String get() = listOf(name.takeIf { it.isNotBlank() }, "$foodCount ${if (foodCount == 1) "alimento" else "alimentos"}",
        if (profile == null) "cantidades relativas" else "% de tu día").filterNotNull().joinToString(" · ")
}

object NutrientFootprints {
    /** Una ración: la ficha de un alimento (n-ficha), su lista (n-ficha-vit/min/fat) y la hoja de ajuste (n-edit). */
    fun food(food: LoggedFood, portion: String, profile: DriProfile?, energyKcal: Double? = null): NutrientFoodModel {
        val totals = NutrientCoverage.summarize(listOf(food))
        val footprint = fromTotals(totals, profile, energyKcal, nutrientsWithData = nutrientsWithData(food))
        return NutrientFoodModel(food.foodName, portion, totals, footprint, list(totals, profile, energyKcal, NutrientScope.FOOD),
            if (footprint.labelOnly) labelOnlyText(food) else null, profile, energyKcal)
    }

    /** Una comida: el módulo del registro (n-reg), su lista (n-reg-list), «Esta comida» (n-home) y «Lo que más sumó» (n-saved). */
    fun meal(name: String, foods: List<LoggedFood>, profile: DriProfile?, energyKcal: Double? = null): NutrientMealModel {
        val totals = NutrientCoverage.summarize(foods)
        return NutrientMealModel(name, foods.size, totals, NutrientCoverage.foodCoverage(foods), fromTotals(totals, profile, energyKcal),
            list(totals, profile, energyKcal, NutrientScope.MEAL), profile, energyKcal)
    }

    /** Los 38 nutrientes que guarda Nutrición v2 por alimento (`NutrientId`). */
    const val TOTAL_NUTRIENTS = 38
    /** Hasta aquí, la ficha se trata como «solo etiqueta» (energía, macros y poco más). */
    const val LABEL_ONLY_MAX = 12

    private const val NIACIN_SHORT = "Preformada: con la que forma tu cuerpo, el total real suele ser mayor."
    const val NIACIN_LONG = "Las fuentes dan niacina preformada; la referencia está en equivalentes (incluye la que el cuerpo forma del triptófano), así que tu total real suele ser mayor."
    const val NO_REFERENCE_ROW = "Sin referencia de ingesta: se muestra el total."
    /** M-D5: la ficha sin perfil adulto compatible lo dice en una línea, sin inventar un 100 %. */
    const val RELATIVE_LINE = "Sin referencia para tu perfil: la huella muestra cantidades relativas, sin un 100 %."
    const val NO_DRI = "Sin referencia DRI: falta un perfil adulto compatible y una ecuación declarada en el plan."
    /** M-D3: fuentes, licencias y aviso al pie de la hoja del día, en letra pequeña. */
    const val SOURCES_FOOTER = "Datos de alimentos: USDA FoodData Central (dominio público), ANSES-Ciqual (Licence Ouverte 2.0) y Open Food Facts (ODbL). " +
        "Referencias de las Academias Nacionales de EE. UU. No sustituye el consejo de un profesional."
    private const val CDRR_SODIUM = 2300.0
    /** Perfil que solo da la escala de las barras en modo relativo; nunca se muestra como referencia. */
    private val scaleProfile = DriProfile(31, DriSex.MALE)

    // ─── Huella ─────────────────────────────────────────────────────────

    /** Huella de una ración o de una comida a partir de sus alimentos (la ficha, el registro, la hoja de ajuste). */
    fun fromFoods(
        foods: List<LoggedFood>,
        profile: DriProfile?,
        energyKcal: Double? = null,
        highlights: Int = 3,
    ): NutrientFootprintModel {
        val totals = NutrientCoverage.summarize(foods)
        return fromTotals(totals, profile, energyKcal, highlights = highlights, day = false,
            nutrientsWithData = foods.singleOrNull()?.let { nutrientsWithData(it) })
    }

    /**
     * Huella de una ficha de catálogo con su vector por 100 g y la cantidad elegida en gramos (se mueve con −/+).
     * [estimated] marca toda la ración como estimada (contorno discontinuo).
     */
    fun fromPer100g(
        name: String,
        per100g: Map<NutrientId, Double>,
        grams: Double,
        profile: DriProfile?,
        energyKcal: Double? = null,
        estimated: Boolean = false,
    ): NutrientFootprintModel {
        val factor = grams / 100.0
        val food = LoggedFood(foodName = name, amount = grams, unit = "g",
            calories = (per100g[NutrientId.ENERGY] ?: 0.0) * factor, protein = (per100g[NutrientId.PROTEIN] ?: 0.0) * factor,
            carbs = (per100g[NutrientId.CARBS] ?: 0.0) * factor, fats = (per100g[NutrientId.FAT] ?: 0.0) * factor,
            isUncertain = estimated).withNutrientSnapshot(per100g, "ficha")
        return fromFoods(listOf(food), profile, energyKcal)
    }

    /** Huella a partir de totales ya calculados (el día usa los de la hoja, con [day] = true y sin destacados). */
    fun fromTotals(
        totals: List<NutrientDayTotal>,
        profile: DriProfile?,
        energyKcal: Double? = null,
        highlights: Int = 3,
        day: Boolean = false,
        nutrientsWithData: Int? = null,
    ): NutrientFootprintModel {
        val byKey = totals.associateBy { it.nutrient }
        val relative = profile == null
        val raw = footprintSlots.map { slot ->
            val total = byKey[slot.key]
            val amount = total?.totalKnown
            val percent = if (relative) null else amount?.let { a -> reference(slot.key, profile, energyKcal)?.let { a / it * 100.0 } }
            Triple(slot, total, percent)
        }
        val scale = if (relative) raw.mapNotNull { (slot, total, _) -> total?.totalKnown?.let { a -> scaleReference(slot.key)?.let { a / it } } }.maxOrNull() else null
        val top = if (day || relative) emptySet() else topKeys(raw.map { it.first.key to it.third }, highlights)
        val bars = raw.map { (slot, total, percent) ->
            val amount = total?.totalKnown
            val height = when {
                amount == null -> null
                relative -> scaleReference(slot.key)?.let { ref -> if (scale != null && scale > 0.0) (amount / ref / scale).toFloat().coerceIn(0f, 1f) else 0f } ?: 0f
                percent == null -> 0f
                else -> (percent.coerceAtMost(100.0) / 100.0).toFloat()
            }
            FootprintBar(slot, amount, percent, height, estimatedShare(total), slot.key in top)
        }
        val lines = if (relative) emptyList() else highlightLines(bars)
        return NutrientFootprintModel(bars, relative, day, lines, nutrientsWithData)
    }

    /** «Destaca en»: las tres de más % (sin topes) y los topes que pasan del 25 % («N % del máx.»). */
    private fun highlightLines(bars: List<FootprintBar>): List<NutrientHighlight> {
        val top = topKeys(bars.map { it.key to it.percent }, 3)
        val best = top.mapNotNull { key -> bars.first { it.key == key }.let { b -> b.percent?.let { NutrientHighlight(b.key.label, percentText(it), false) } } }
        val watch = bars.filter { it.ceiling && (it.percent ?: 0.0) >= 25.0 }.map { NutrientHighlight(it.key.label, "${percentText(it.percent!!)} del máx.", true) }
        return best + watch
    }

    private fun topKeys(values: List<Pair<NutrientKey, Double?>>, n: Int): Set<NutrientKey> =
        if (n <= 0) emptySet() else values.filter { (key, pct) -> pct != null && pct > 0.0 && !key.isCeiling }
            .sortedByDescending { it.second }.take(n).map { it.first }.toSet()

    /** Cuántos de los 38 nutrientes trae un alimento: energía y macros siempre, agua si viene en la ficha, y cada clave con dato. */
    fun nutrientsWithData(food: LoggedFood): Int {
        if (food.isQuickEntry) return 4
        val keys = NutrientCoverage.summarize(listOf(food)).count { it.totalKnown != null }
        val water = if (food.nutrientsPer100g?.containsKey(NutrientId.WATER.key) == true || food.waterMl > 0.0) 1 else 0
        return 4 + water + keys
    }

    /** Cifras del día sobre las 24 de la huella: los topes solo cuentan si se pasan. */
    fun dayCounts(model: NutrientFootprintModel): NutrientDayCounts {
        var ok = 0; var near = 0; var low = 0; var over = 0
        model.bars.forEach { bar ->
            val pct = bar.percent ?: return@forEach
            when {
                bar.ceiling -> if (pct > 100.0) over++
                pct >= 100.0 -> ok++
                pct >= 70.0 -> near++
                else -> low++
            }
        }
        return NutrientDayCounts(ok, near, low, over)
    }

    // ─── Cobertura ──────────────────────────────────────────────────────

    /** Módulo del día: «Cobertura: 86 % de las kcal con dato · 2 alimentos sin ficha». Null sin alimentos. */
    fun dayModuleCoverageLine(coverage: NutrientFoodCoverage): String? {
        if (coverage.foods == 0) return null
        val base = "Cobertura: ${percentText(coverage.declaredFraction * 100.0)} ${if (coverage.weightedByEnergy) "de las kcal" else "de los alimentos"} con dato"
        val missing = coverage.foodsWithoutData
        return if (missing == 0) base else "$base · $missing ${if (missing == 1) "alimento" else "alimentos"} sin ficha"
    }

    /** Comida: «3 con ficha completa · 1 estimado» (y «· N sin ficha» si alguno no trae nada). */
    fun mealCoverageLine(coverage: NutrientFoodCoverage): String {
        val parts = mutableListOf("${coverage.declaredFoods} con ficha completa")
        if (coverage.estimatedFoods > 0) parts += "${coverage.estimatedFoods} ${if (coverage.estimatedFoods == 1) "estimado" else "estimados"}"
        if (coverage.foodsWithoutData > 0) parts += "${coverage.foodsWithoutData} sin ficha"
        return parts.joinToString(" · ")
    }

    /** «Lo que más sumó: selenio, riboflavina y B12» (n-saved) o null si no hay nada que destacar. */
    fun savedLine(model: NutrientFootprintModel): String? {
        val names = model.highlights.filterNot { it.warn }.map { shortName(it.name) }
        if (names.isEmpty()) return null
        return "Lo que más sumó: ${joinEs(names)}"
    }

    /** El aviso de n-ficha-label: qué declara la etiqueta y que el resto no cuenta como 0. */
    fun labelOnlyText(food: LoggedFood): String {
        val count = nutrientsWithData(food)
        val totals = NutrientCoverage.summarize(listOf(food)).filter { it.totalKnown != null }.map { it.nutrient }
        val extras = buildList {
            if (NutrientKey.SUGARS in totals || NutrientKey.ADDED_SUGARS in totals) add("azúcares")
            if (NutrientKey.SAT_FAT in totals) add("grasa saturada")
            if (NutrientKey.FIBER in totals) add("fibra")
            totals.filterNot { it in setOf(NutrientKey.SUGARS, NutrientKey.ADDED_SUGARS, NutrientKey.SAT_FAT, NutrientKey.FIBER, NutrientKey.SODIUM) }
                .forEach { add(lowerFirst(it.label)) }
            if (NutrientKey.SODIUM in totals) add("sal")
        }
        val list = if (extras.size <= 5) ": ${joinEs(listOf("energía", "macros") + extras)}" else ""
        return "La etiqueta declara $count de $TOTAL_NUTRIENTS nutrientes$list. El resto queda sin dato y no cuenta como 0 en tu día."
    }

    // ─── Listas ─────────────────────────────────────────────────────────

    /** Las filas por grupo (M-D4) y, en el día, «A vigilar» arriba con lo que pasa un tope o un límite superior total. */
    fun list(totals: List<NutrientDayTotal>, profile: DriProfile?, energyKcal: Double?, scope: NutrientScope): NutrientListModel {
        val byKey = totals.associateBy { it.nutrient }
        val foodMissing = if (scope == NutrientScope.FOOD) NutrientKey.entries.count { byKey[it]?.totalKnown == null } else 0
        val rows = groupedNutrientKeys().map { (group, keys) ->
            var firstNoRef = true
            group to keys.map { key ->
                val row = row(key, byKey[key], profile, energyKcal, scope, foodMissing, noRefNote = profile != null && when {
                    group == NutrientGroup.FATS -> key == NutrientKey.EPA_DHA
                    else -> firstNoRef
                })
                if (row.noReference && !row.missing && group != NutrientGroup.FATS) firstNoRef = false
                row
            }
        }
        if (scope != NutrientScope.DAY) return NutrientListModel(emptyList(), rows)
        val watchKeys = totals.filter { total -> watched(total, profile, energyKcal) }.map { it.nutrient }.toSet()
        val watch = rows.flatMap { it.second }.filter { it.key in watchKeys }
        return NutrientListModel(watch, rows.map { (g, list) -> g to list.filterNot { it.key in watchKeys } })
    }

    private fun watched(total: NutrientDayTotal, profile: DriProfile?, energyKcal: Double?): Boolean {
        val amount = total.totalKnown ?: return false
        if (profile == null) return false
        val key = total.nutrient
        if (key.isCeiling) return reference(key, profile, energyKcal)?.let { amount > it } == true
        return DriTargets.lookup(key, profile)?.exceedsKnownTotal(amount) == true
    }

    fun row(
        key: NutrientKey,
        total: NutrientDayTotal?,
        profile: DriProfile?,
        energyKcal: Double?,
        scope: NutrientScope,
        foodMissing: Int = 0,
        noRefNote: Boolean = false,
    ): NutrientRowModel {
        val amount = total?.totalKnown
        val missingFoods = total?.missingCount ?: 0
        val partial = amount != null && missingFoods > 0 && scope != NutrientScope.FOOD
        val ref = if (profile == null) null else reference(key, profile, energyKcal)
        val pct = if (amount != null && ref != null) amount / ref * 100.0 else null
        val ceiling = key.isCeiling
        val noRef = amount != null && pct == null
        val valueText = if (amount == null) "sin dato" else "${if (partial) "≥ " else ""}${nutrientNumber(amount)} ${shortUnit(key)}"
        val note = specificNote(key, amount, profile, energyKcal, scope, foodMissing) ?: when {
            noRef && noRefNote -> NO_REFERENCE_ROW
            partial -> "$missingFoods ${if (missingFoods == 1) "alimento" else "alimentos"} sin dato: puede ser más"
            else -> null
        }
        return NutrientRowModel(
            key = key, name = key.label, valueText = valueText, missing = amount == null,
            percentText = when { amount == null -> null; pct == null -> "—"; else -> percentText(pct) },
            ceiling = ceiling, over = (pct ?: 0.0) > 100.0, noReference = noRef,
            fill = ((pct ?: 0.0).coerceIn(0.0, 100.0) / 100.0).toFloat(), estimatedShare = estimatedShare(total), note = note, partial = partial,
        )
    }

    private fun specificNote(key: NutrientKey, amount: Double?, profile: DriProfile?, energyKcal: Double?, scope: NutrientScope, foodMissing: Int): String? {
        if (amount == null) {
            // Solo en la ficha de una ración casi completa: un hueco suelto se explica; una etiqueta corta ya lo dice en su aviso.
            return if (scope == NutrientScope.FOOD && foodMissing in 1..3) "La ficha no trae ${lowerFirst(key.label)}. No cuenta como 0: tus totales lo dirán." else null
        }
        if (key == NutrientKey.NIACIN) return NIACIN_SHORT
        if (profile == null) return null
        return when (key) {
            NutrientKey.SODIUM -> if (scope == NutrientScope.FOOD) "Tope diario: 2.300 mg (CDRR). Se lee como parte del máximo, no como meta."
                else "Máximo recomendado: 2.300 mg (CDRR)${DriTargets.lookup(key, profile)?.let { " · AI: ${nutrientNumber(it.value)} mg" }.orEmpty()}"
            NutrientKey.CAFFEINE, NutrientKey.SAT_FAT, NutrientKey.TRANS_FAT, NutrientKey.ADDED_SUGARS ->
                GuideLimits.lookup(key, profile)?.let { guideNote(key, it, energyKcal) }
            else -> DriTargets.lookup(key, profile)?.takeIf { it.exceedsKnownTotal(amount) }?.upperLimit?.let { "Pasa el límite superior total: ${nutrientNumber(it)} ${shortUnit(key)}" }
        }
    }

    private fun guideNote(key: NutrientKey, guide: GuideLimit, energyKcal: Double?): String {
        val source = guideSource(guide)
        guide.maxAmount?.let { return "Tope de la guía: ${nutrientNumber(it)} ${shortUnit(key)} · $source" }
        val share = "${nutrientNumber((guide.maxEnergyFraction ?: 0.0) * 100.0)} % de la energía"
        val ceiling = guide.ceiling(energyKcal)
        return if (ceiling != null && energyKcal != null) "Tope de la guía: $share (${nutrientNumber(ceiling)} ${shortUnit(key)} con ${nutrientNumber(energyKcal)} kcal) · $source"
        else "Tope de la guía: $share · $source"
    }

    // ─── Hoja de un nutriente ───────────────────────────────────────────

    /** [context] es lo que sigue al tipo en el eyebrow: «hoy», «desayuno», «esta ración». */
    fun detail(total: NutrientDayTotal, profile: DriProfile?, energyKcal: Double?, context: String): NutrientDetailModel {
        val key = total.nutrient
        val amount = total.totalKnown
        val partial = amount != null && total.missingCount > 0
        val target = DriTargets.lookup(key, profile)
        val guide = if (profile != null && key.isCeiling && key != NutrientKey.SODIUM) GuideLimits.lookup(key, profile) else null
        val ref = if (profile == null) null else reference(key, profile, energyKcal)
        val pct = if (amount != null && ref != null) amount / ref * 100.0 else null
        val unit = shortUnit(key)
        val maxContribution = total.contributors.maxOfOrNull { it.amount }?.takeIf { it > 0.0 }
        val alert = when {
            amount == null || profile == null -> null
            key == NutrientKey.SODIUM && amount > CDRR_SODIUM -> "El total conocido supera el máximo recomendado."
            guide != null && guide.exceeds(amount, energyKcal) -> "El total conocido supera el tope de la guía."
            target?.exceedsKnownTotal(amount) == true -> "El total conocido supera el límite superior de ingesta total."
            else -> null
        }
        val missingNote = when {
            amount == null && !key.inCatalog -> "Sin dato en el catálogo; una ficha propia puede aportarlo."
            amount == null -> if (total.missingFoods.isEmpty()) null else "Ningún alimento trae dato de ${lowerFirst(key.label)}. No cuenta como 0."
            total.missingFoods.size == 1 -> "${total.missingFoods.first()} no trae dato de ${lowerFirst(key.label)}: el total puede ser mayor."
            total.missingFoods.size > 1 -> "${total.missingFoods.size} alimentos no traen dato de ${lowerFirst(key.label)}: el total puede ser mayor."
            else -> null
        }
        val notes = buildList {
            if (profile == null) add(NO_DRI)
            else if (target == null && guide == null && key != NutrientKey.SODIUM) add(NO_REFERENCE_ROW)
            if (key == NutrientKey.NIACIN) add(NIACIN_LONG)
        }
        return NutrientDetailModel(
            key = key,
            eyebrow = "${groupSingular(key.group)} · $context",
            name = key.label,
            bigValue = amount?.let { "${if (partial) "≥ " else ""}${nutrientNumber(it, bigFigure = true)}" } ?: "—",
            ofText = when {
                amount == null -> "sin dato"
                ref != null -> "$unit de ${nutrientNumber(ref)} $unit"
                else -> unit
            },
            percentText = pct?.let { if (key.isCeiling) "${percentText(it)} del máx." else percentText(it) },
            ceiling = key.isCeiling,
            fill = pct?.let { (it.coerceIn(0.0, 100.0) / 100.0).toFloat() },
            over = (pct ?: 0.0) > 100.0,
            alert = alert,
            contributors = total.contributors.map { c ->
                NutrientContributorRow(if (c.groupedFoods > 1) "${c.name} (${c.groupedFoods})" else c.name, "${nutrientNumber(c.amount)} $unit",
                    maxContribution?.let { (c.amount / it).toFloat().coerceIn(0f, 1f) } ?: 0f, c.estimated)
            },
            missingNote = missingNote,
            references = if (profile == null) emptyList() else references(key, target, guide, energyKcal),
            notes = notes,
            sourceLine = "${when { guide != null -> guideSource(guide) + ". "; target != null -> "Academias Nacionales de EE. UU. "; else -> "" }}No sustituye el consejo de un profesional.",
        )
    }

    private fun references(key: NutrientKey, target: DriTarget?, guide: GuideLimit?, energyKcal: Double?): List<Pair<String, String>> = buildList {
        val unit = shortUnit(key)
        if (target != null) add((if (target.kind == DriKind.RDA) "RDA" else "AI") to "${nutrientNumber(target.value)} $unit")
        if (key == NutrientKey.SODIUM) add("Máximo recomendado (CDRR)" to "${nutrientNumber(CDRR_SODIUM)} mg")
        else if (target != null) {
            val ul = target.upperLimit?.let { "${nutrientNumber(it)} ${target.upperLimitUnit.orEmpty()}".trim() }
            when (target.scope) {
                UpperLimitScope.TOTAL -> ul?.let { add("Límite superior total" to it) }
                UpperLimitScope.SUPPLEMENTS_ONLY -> ul?.let { add("Límite para suplementos o formas añadidas (no se compara con el total)" to it) }
                UpperLimitScope.PREFORMED_ONLY -> ul?.let { add("Límite de retinol (no se compara con la vitamina A total)" to it) }
                UpperLimitScope.NONE -> add("Límite superior" to "No establecido")
            }
        }
        if (guide != null) {
            val ceiling = guide.ceiling(energyKcal)
            add("Tope de la guía" to when {
                guide.maxAmount != null -> "${nutrientNumber(guide.maxAmount)} $unit"
                ceiling != null && energyKcal != null -> "${nutrientNumber(ceiling)} $unit (${nutrientNumber((guide.maxEnergyFraction ?: 0.0) * 100.0)} % de ${nutrientNumber(energyKcal)} kcal)"
                else -> "${nutrientNumber((guide.maxEnergyFraction ?: 0.0) * 100.0)} % de la energía"
            })
        }
    }

    // ─── Hoja del día ───────────────────────────────────────────────────

    /** Bajo el título de la hoja del día: «Hoy · % de la referencia de tu perfil» (o «Miércoles 28 · …»; sin perfil, «cantidades relativas»). */
    fun daySheetSubtitle(date: String, profile: DriProfile?, today: LocalDate): String =
        "${dayContext(date, today).replaceFirstChar { it.uppercase() }} · ${if (profile == null) "cantidades relativas" else "% de la referencia de tu perfil"}"

    /** Lo que sigue al tipo en el eyebrow de la hoja de un nutriente: «hoy» o «miércoles 28». */
    fun dayContext(date: String, today: LocalDate): String {
        val day = runCatching { LocalDate.parse(date.take(10)) }.getOrNull() ?: return "hoy"
        return if (day == today) "hoy" else "${WEEKDAYS[day.dayOfWeek.value - 1].lowercase()} ${day.dayOfMonth}"
    }

    // ─── Utilidades ─────────────────────────────────────────────────────

    /** La referencia con la que se calcula el %: la DRI, el CDRR del sodio o el tope de la guía (cafeína, grasas, azúcares añadidos). */
    fun reference(key: NutrientKey, profile: DriProfile?, energyKcal: Double?): Double? {
        if (profile == null) return null
        return when {
            key == NutrientKey.SODIUM -> CDRR_SODIUM
            key.isCeiling -> GuideLimits.lookup(key, profile)?.ceiling(energyKcal)
            else -> DriTargets.lookup(key, profile)?.value
        }?.takeIf { it.isFinite() && it > 0.0 }
    }

    private fun scaleReference(key: NutrientKey): Double? = reference(key, scaleProfile, 2000.0)

    private fun estimatedShare(total: NutrientDayTotal?): Float {
        val amount = total?.totalKnown ?: return 0f
        if (!(amount > 0.0)) return 0f
        return (total.estimatedAmount / amount).toFloat().coerceIn(0f, 1f)
    }

    private fun guideSource(guide: GuideLimit): String = when {
        "efsa" in guide.sourceUrl -> "EFSA"
        "fda.gov" in guide.sourceUrl -> "FDA"
        else -> "OMS"
    }

    private fun groupSingular(group: NutrientGroup): String = when (group) {
        NutrientGroup.VITAMINS -> "Vitamina"
        NutrientGroup.MINERALS -> "Mineral"
        NutrientGroup.FATS -> "Grasa"
        NutrientGroup.CARBS -> "Carbohidrato"
        NutrientGroup.OTHER -> "Nutriente"
    }

    /** «Vitamina B12» → «B12», «Riboflavina» → «riboflavina» (n-saved: «selenio, riboflavina y B12»). */
    private fun shortName(label: String): String = if (label.startsWith("Vitamina B")) label.removePrefix("Vitamina ") else lowerFirst(label)

    private val WEEKDAYS = listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo")
}

/** Unidad corta de las filas, como el lienzo: «µg RAE» → «µg», «mg NE» → «mg». */
internal fun shortUnit(key: NutrientKey): String = key.unit.substringBefore(' ')

/** «175 %». */
internal fun percentText(value: Double): String = "${value.roundToInt()} %"

/**
 * Cifras del lienzo (`fmtN`): 2 decimales bajo 1, 1 bajo 10, enteros desde 10; coma decimal y punto de miles; sin ceros sobrantes.
 * Con [bigFigure] (la cifra grande de la hoja de un nutriente, «11,4») lleva un decimal hasta 100.
 */
fun nutrientNumber(value: Double, bigFigure: Boolean = false): String {
    if (!value.isFinite()) return "—"
    val decimals = when { value < 1.0 -> 2; value < (if (bigFigure) 100.0 else 10.0) -> 1; else -> 0 }
    val plain = BigDecimal.valueOf(value).setScale(decimals, RoundingMode.HALF_UP).toPlainString()
    val integer = plain.substringBefore('.')
    val fraction = plain.substringAfter('.', "").trimEnd('0')
    val negative = integer.startsWith("-")
    val digits = integer.removePrefix("-")
    val grouped = digits.reversed().chunked(3).joinToString(".").reversed()
    return (if (negative) "-" else "") + grouped + if (fraction.isNotEmpty()) ",$fraction" else ""
}

internal fun lowerFirst(text: String): String = text.replaceFirstChar { it.lowercase() }

internal fun joinEs(items: List<String>): String = when (items.size) {
    0 -> ""
    1 -> items[0]
    else -> items.dropLast(1).joinToString(", ") + " y " + items.last()
}
