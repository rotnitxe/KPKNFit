package com.example.kpkn.domain.nutrition.v2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

class ConceptCatalogLoaderV2Test {

    private fun fixtureText(): String =
        checkNotNull(ConceptCatalogLoaderV2Test::class.java.classLoader!!.getResourceAsStream("nutrition_v2/concepts_fixture.json")) {
            "falta nutrition_v2/concepts_fixture.json"
        }.use { String(it.readBytes(), Charsets.UTF_8) }

    private val catalog by lazy { ConceptCatalogLoaderV2.parse(fixtureText()) }

    @Test fun loadsFixture() {
        assertEquals("fixture-2026-10-28", catalog.generation)
        assertEquals(13, catalog.concepts.size)
        assertEquals(18, catalog.records.size)
        assertNotNull(catalog.concept("arroz_blanco"))
        assertNull(catalog.concept("no_existe"))
        assertEquals(130.0, catalog.record("r_arroz_cocido")!!.per100g["energy"]!!, 0.0)
        assertNull(catalog.record(null))
        assertEquals(listOf(RecordPortionV2("1 cup", 158.0)), catalog.record("r_arroz_cocido")!!.portions)
        assertEquals(1.03, catalog.record("r_leche_entera")!!.densityGPerMl!!, 0.0)
        assertNull(catalog.record("r_arroz_cocido")!!.densityGPerMl)
    }

    @Test fun parsesEnumsAndNaStates() {
        val arroz = catalog.concept("arroz_blanco")!!
        assertEquals(ConceptKindV2.FOOD, arroz.kind)
        assertEquals(FoodStateV2.COOKED, arroz.defaultState)
        assertEquals(FoodFormV2.NA, arroz.defaultForm)
        assertEquals(listOf(FoodStateV2.COOKED, FoodStateV2.RAW), arroz.variants.map { it.state })
        assertEquals(listOf("r_arroz_cocido", "r_arroz_crudo"), arroz.variants.map { it.recordId })
        assertEquals(ConceptPortionV2("taza", 185.0, FoodStateV2.RAW), arroz.portions[1])

        val huevo = catalog.concept("huevo")!!
        assertNull(huevo.defaultState) // "NA"
        assertTrue(huevo.countable)
        assertEquals(mapOf(SizeV2.S to 44.0, SizeV2.M to 50.0, SizeV2.L to 56.0), huevo.unit!!.grams)
        assertEquals(listOf("unidad"), huevo.unit!!.names)

        assertEquals(ConceptKindV2.DRINK, catalog.concept("leche_entera")!!.kind)
        assertEquals(ConceptKindV2.DISH, catalog.concept("pastel_de_choclo")!!.kind)
        assertEquals(FoodFormV2.CANNED_DRAINED, catalog.concept("atun_en_aceite")!!.defaultForm)
        assertTrue(catalog.hasForm("cafe", FoodFormV2.SUGAR_FREE))
        assertTrue(catalog.hasState("huevo", FoodStateV2.FRIED))
        assertFalse(catalog.hasState("huevo", FoodStateV2.BAKED))
    }

    @Test fun parsesReconstitutionDensityAndComponents() {
        val polvo = catalog.concept("leche_en_polvo_entera")!!
        assertEquals(ReconstitutionV2(13.0, MediumV2.WATER), polvo.reconstitution)
        assertEquals(1.03, catalog.concept("leche_entera")!!.densityGPerMl!!, 0.0)
        assertNull(catalog.concept("arroz_blanco")!!.densityGPerMl)
        assertNull(catalog.concept("arroz_blanco")!!.components)
        val pastel = catalog.concept("pastel_de_choclo")!!
        assertEquals(
            listOf(DishComponentV2("r_choclo", 150.0, "pastelera de choclo"), DishComponentV2(null, 100.0, "pino sin ficha")),
            pastel.components,
        )
        assertEquals("kpkn_pastel_de_choclo", pastel.variants.single().recordId)
    }

    @Test fun absentOptionalFieldsTakeDefaults() {
        val c = ConceptCatalogLoaderV2.parse("""{"concepts": [{"id": "x"}]}""")
        val x = c.concept("x")!!
        assertEquals("x", x.name)
        assertEquals(ConceptKindV2.FOOD, x.kind)
        assertEquals("", c.generation)
        assertTrue(x.aliasesEs.isEmpty() && x.aliasesEn.isEmpty() && x.typos.isEmpty())
        assertNull(x.defaultState)
        assertEquals(FoodFormV2.NA, x.defaultForm)
        assertTrue(x.variants.isEmpty())
        assertFalse(x.countable)
        assertNull(x.unit)
        assertTrue(x.portions.isEmpty())
        assertEquals(100.0, x.defaultGrams, 0.0)
        assertNull(x.densityGPerMl)
        assertNull(x.reconstitution)
        assertNull(x.components)
        assertTrue(c.records.isEmpty())
        // Concepto sin id: se omite.
        assertTrue(ConceptCatalogLoaderV2.parse("""{"concepts": [{"name": "sin id"}]}""").concepts.isEmpty())
    }

    @Test fun unknownEnumVariantsAreDroppedWithoutException() {
        val json = """
            {"concepts": [{"id": "y", "kind": "RARO", "defaultState": "ASADO_RARO", "defaultForm": "XYZ",
              "variants": [
                {"state": "COOKED", "form": "NA", "ref": "a"},
                {"state": "RARO", "form": "NA", "ref": "b"},
                {"state": "NA", "form": "FORMA_RARA", "ref": "c"},
                {"state": null, "form": "SUGAR_FREE", "ref": "d", "proxy": true},
                {"state": "raw", "ref": "e"},
                {"form": "LIQUID"}],
              "unit": {"names": ["unidad"], "grams": {"XL": 9, "M": 50}},
              "reconstitution": {"gramsPer100ml": 13, "medium": "ACEITE"}}]}
        """.trimIndent()
        val y = ConceptCatalogLoaderV2.parse(json).concept("y")!!
        assertEquals(ConceptKindV2.FOOD, y.kind)
        assertNull(y.defaultState)
        assertEquals(FoodFormV2.NA, y.defaultForm)
        assertEquals(listOf("a", "d", "e", null), y.variants.map { it.recordId })
        assertEquals(listOf(false, true, false, false), y.variants.map { it.proxy })
        assertEquals(FoodStateV2.RAW, y.variants[2].state)
        assertEquals(FoodFormV2.LIQUID, y.variants[3].form)
        assertEquals(mapOf(SizeV2.M to 50.0), y.unit!!.grams)
        assertNull(y.reconstitution)
    }

    @Test fun readsPlainAndGzipStreams() {
        val bytes = fixtureText().toByteArray(Charsets.UTF_8)
        val plain = ConceptCatalogLoaderV2.read(ByteArrayInputStream(bytes))
        assertEquals(13, plain.concepts.size)
        val zipped = ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(bytes) } }.toByteArray()
        val fromGzip = ConceptCatalogLoaderV2.read(ByteArrayInputStream(zipped), gzip = true)
        assertEquals(plain.generation, fromGzip.generation)
        assertEquals(plain.concepts.keys, fromGzip.concepts.keys)
        assertEquals(plain.records.keys, fromGzip.records.keys)
    }

    @Test fun rootThatIsNotAnObjectIsRejected() {
        var failed = false
        try {
            ConceptCatalogLoaderV2.parse("[]")
        } catch (_: IllegalArgumentException) {
            failed = true
        }
        assertTrue(failed)
    }

    @Test fun realExportLoadsWhenPresent() {
        val file = java.io.File("C:/kw/nutri-build/concepts_export.json")
        assumeTrue(file.isFile)
        val real = file.inputStream().use { ConceptCatalogLoaderV2.read(it) }
        assertTrue(real.concepts.size > 1000)
        assertTrue(real.records.size > 1000)
        // Cada ficha enlazada por una variante existe en el export.
        val missing = real.concepts.values.flatMap { c -> c.variants.mapNotNull { it.recordId } }.count { it !in real.records }
        assertEquals(0, missing)
    }
}
