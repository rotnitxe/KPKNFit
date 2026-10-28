package com.example.kpkn.domain.nutrition.v2

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import java.io.InputStream
import java.util.zip.GZIPInputStream

/**
 * Cargador de `concepts_export.json` (F1b §4 / F2 §6) a [ConceptCatalogV2].
 *
 * Lee el árbol [JsonElement] a mano: tolera campos ausentes y nombres de enum desconocidos (la variante o la entrada
 * que los usa se omite, sin excepción). `"NA"`/`null` en un estado = sin estado. `ref` de variantes y componentes
 * pasa a `recordId`. Ignora claves que no conoce (`ambiguous`, `conceptsSha256`…).
 */
object ConceptCatalogLoaderV2 {

    /** Interpreta [json] (texto del export). Lanza [IllegalArgumentException] si la raíz no es un objeto. */
    fun parse(json: String): ConceptCatalogV2 {
        val root = Json.parseToJsonElement(json) as? JsonObject
            ?: throw IllegalArgumentException("concepts_export.json: la raíz debe ser un objeto")
        val concepts = (root["concepts"] as? JsonArray).orEmpty().mapNotNull { parseConcept(it) }
        val records = LinkedHashMap<String, RecordV2>()
        (root["records"] as? JsonObject)?.forEach { (id, value) ->
            parseRecord(id, value)?.let { records[id] = it }
        }
        return ConceptCatalogV2(root.str("generation").orEmpty(), concepts, records)
    }

    /** Lee el export de [input] (UTF-8), descomprimiendo gzip si [gzip]. No cierra [input]. */
    fun read(input: InputStream, gzip: Boolean = false): ConceptCatalogV2 {
        val stream = if (gzip) GZIPInputStream(input) else input
        return parse(String(stream.readBytes(), Charsets.UTF_8))
    }

    // ─── Conceptos ─────────────────────────────────────────────────────

    private fun parseConcept(element: JsonElement): ConceptV2? {
        val obj = element as? JsonObject ?: return null
        val id = obj.str("id")?.takeIf { it.isNotBlank() } ?: return null
        return ConceptV2(
            id = id,
            name = obj.str("name")?.takeIf { it.isNotBlank() } ?: id,
            kind = enumOrNull<ConceptKindV2>(obj.str("kind")) ?: ConceptKindV2.FOOD,
            category = obj.str("category").orEmpty(),
            aliasesEs = obj.strings("aliasesEs"),
            aliasesEn = obj.strings("aliasesEn"),
            typos = obj.strings("typos"),
            defaultState = obj.str("defaultState")?.let { stateOrNull(it) },
            defaultForm = enumOrNull<FoodFormV2>(obj.str("defaultForm")) ?: FoodFormV2.NA,
            variants = (obj["variants"] as? JsonArray).orEmpty().mapNotNull { parseVariant(it) },
            countable = obj.bool("countable") ?: false,
            unit = parseUnit(obj["unit"]),
            portions = (obj["portions"] as? JsonArray).orEmpty().mapNotNull { parsePortion(it) },
            defaultGrams = obj.num("defaultGrams") ?: 100.0,
            densityGPerMl = obj.num("densityGPerMl"),
            reconstitution = parseReconstitution(obj["reconstitution"]),
            components = (obj["components"] as? JsonArray)?.mapNotNull { parseComponent(it) },
        )
    }

    /** Estado desconocido o «NA» → null. Quien necesite distinguir «NA» de «desconocido» usa [isKnownState]. */
    private fun stateOrNull(name: String): FoodStateV2? = enumOrNull<FoodStateV2>(name)

    private fun isKnownState(name: String?): Boolean =
        name == null || isNa(name) || enumOrNull<FoodStateV2>(name) != null

    private fun parseVariant(element: JsonElement): ConceptVariantV2? {
        val obj = element as? JsonObject ?: return null
        val stateName = obj.str("state")
        if (!isKnownState(stateName)) return null
        val formName = obj.str("form")
        val form = if (formName == null || isNa(formName)) FoodFormV2.NA else enumOrNull<FoodFormV2>(formName) ?: return null
        return ConceptVariantV2(
            state = stateName?.let { stateOrNull(it) },
            form = form,
            recordId = obj.str("ref")?.takeIf { it.isNotBlank() },
            proxy = obj.bool("proxy") ?: false,
        )
    }

    private fun parseUnit(element: JsonElement?): CountUnitV2? {
        val obj = element as? JsonObject ?: return null
        val grams = LinkedHashMap<SizeV2, Double>()
        (obj["grams"] as? JsonObject)?.forEach { (key, value) ->
            val size = enumOrNull<SizeV2>(key) ?: return@forEach
            (value as? JsonPrimitive)?.takeUnless { it is JsonNull }?.let { primitive ->
                (primitive.doubleOrNull ?: primitive.content.toDoubleOrNull())?.let { grams[size] = it }
            }
        }
        if (grams.isEmpty()) return null
        return CountUnitV2(obj.strings("names"), grams)
    }

    private fun parsePortion(element: JsonElement): ConceptPortionV2? {
        val obj = element as? JsonObject ?: return null
        val label = obj.str("label")?.takeIf { it.isNotBlank() } ?: return null
        val grams = obj.num("grams") ?: return null
        return ConceptPortionV2(label, grams, obj.str("state")?.let { stateOrNull(it) })
    }

    private fun parseReconstitution(element: JsonElement?): ReconstitutionV2? {
        val obj = element as? JsonObject ?: return null
        val gramsPer100ml = obj.num("gramsPer100ml") ?: return null
        val medium = when (obj.str("medium")?.trim()?.uppercase()) {
            "AGUA", "WATER" -> MediumV2.WATER
            "LECHE", "MILK" -> MediumV2.MILK
            else -> return null
        }
        return ReconstitutionV2(gramsPer100ml, medium)
    }

    private fun parseComponent(element: JsonElement): DishComponentV2? {
        val obj = element as? JsonObject ?: return null
        val grams = obj.num("grams") ?: return null
        val ref = (obj.str("ref") ?: obj.str("recordId"))?.takeIf { it.isNotBlank() }
        return DishComponentV2(ref, grams, obj.str("note").orEmpty())
    }

    // ─── Fichas ────────────────────────────────────────────────────────

    private fun parseRecord(id: String, element: JsonElement): RecordV2? {
        val obj = element as? JsonObject ?: return null
        val per100g = LinkedHashMap<String, Double>()
        (obj["per100g"] as? JsonObject)?.forEach { (key, value) ->
            val primitive = value as? JsonPrimitive ?: return@forEach
            if (primitive is JsonNull) return@forEach
            (primitive.doubleOrNull ?: primitive.content.toDoubleOrNull())?.let { per100g[key] = it }
        }
        val portions = (obj["portions"] as? JsonArray).orEmpty().mapNotNull { portion ->
            val p = portion as? JsonObject ?: return@mapNotNull null
            val label = p.str("label") ?: return@mapNotNull null
            val grams = p.num("grams") ?: return@mapNotNull null
            RecordPortionV2(label, grams)
        }
        return RecordV2(id, obj.str("name") ?: id, per100g, portions, obj.num("densityGPerMl"))
    }

    // ─── Utilidades del árbol ──────────────────────────────────────────

    private fun isNa(name: String): Boolean = name.isBlank() || name.equals("NA", ignoreCase = true)

    /** Enum por nombre (sin distinguir mayúsculas); «NA»/vacío/desconocido → null. */
    private inline fun <reified E : Enum<E>> enumOrNull(name: String?): E? {
        if (name == null || isNa(name)) return null
        return enumValues<E>().firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }
    }

    private fun JsonObject.str(key: String): String? {
        val primitive = this[key] as? JsonPrimitive ?: return null
        return if (primitive is JsonNull) null else primitive.content
    }

    private fun JsonObject.strings(key: String): List<String> =
        (this[key] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.takeUnless { p -> p is JsonNull }?.content }

    private fun JsonObject.num(key: String): Double? {
        val primitive = this[key] as? JsonPrimitive ?: return null
        if (primitive is JsonNull) return null
        return primitive.doubleOrNull ?: primitive.content.toDoubleOrNull()
    }

    private fun JsonObject.bool(key: String): Boolean? {
        val primitive = this[key] as? JsonPrimitive ?: return null
        if (primitive is JsonNull) return null
        return primitive.booleanOrNull ?: primitive.content.lowercase().let { if (it == "true") true else if (it == "false") false else null }
    }
}
