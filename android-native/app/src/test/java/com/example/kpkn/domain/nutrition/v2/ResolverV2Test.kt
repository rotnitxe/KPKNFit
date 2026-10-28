package com.example.kpkn.domain.nutrition.v2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResolverV2Test {

    private val catalog: ConceptCatalogV2 by lazy { fixtureCatalog() }
    private val resolver by lazy { ResolverV2(catalog) }

    private fun fixtureCatalog(): ConceptCatalogV2 =
        checkNotNull(ResolverV2Test::class.java.classLoader!!.getResourceAsStream("nutrition_v2/concepts_fixture.json")) {
            "falta nutrition_v2/concepts_fixture.json"
        }.use { ConceptCatalogLoaderV2.read(it) }

    /** Copia del catálogo con [id] transformado por [change]. */
    private fun catalogWith(id: String, change: (ConceptV2) -> ConceptV2): ConceptCatalogV2 =
        ConceptCatalogV2(
            catalog.generation,
            catalog.concepts.values.map { if (it.id == id) change(it) else it },
            catalog.records,
        )

    private val text = "texto de prueba"

    private fun draft(
        vararg ids: String,
        quantity: QuantityV2? = null,
        state: FoodStateV2? = null,
        form: FoodFormV2? = null,
        excluded: Boolean = false,
        fuzzy: Boolean = false,
        attachedTo: Int? = null,
    ) = MentionDraftV2(
        start = 0, end = 5, foodStart = 0, foodEnd = 5, conceptIds = ids.toList(), quantity = quantity,
        state = state, form = form, excluded = excluded, fuzzy = fuzzy, attachedTo = attachedTo,
    )

    private fun one(d: MentionDraftV2, r: ResolverV2 = resolver): MentionV2 = r.resolve(text, listOf(d)).single()

    private fun grams(d: MentionDraftV2, r: ResolverV2 = resolver): Double = one(d, r).grams!!

    private fun assertGrams(expected: Double, d: MentionDraftV2, r: ResolverV2 = resolver) =
        assertEquals(expected, grams(d, r), 1e-6)

    // ─── Gramos por tipo de cantidad ───────────────────────────────────

    @Test fun mass_gramsDirectAndMacros() {
        val m = one(draft("arroz_blanco", quantity = QuantityV2.Mass(150.0)))
        assertEquals("arroz_blanco", m.conceptId)
        assertEquals("Arroz blanco", m.conceptName)
        assertEquals("r_arroz_cocido", m.recordId)
        assertEquals(150.0, m.grams!!, 1e-9)
        assertEquals(QuantityKindV2.MASS, m.quantityKind)
        assertEquals(FoodStateV2.COOKED, m.state)
        assertEquals(FoodFormV2.NA, m.form)
        assertEquals(195.0, m.eatenKcal!!, 1e-6)
        assertEquals(4.05, m.eatenProtein!!, 1e-6)
        assertEquals(0.45, m.eatenFat!!, 1e-6)
        assertEquals(42.3, m.eatenCarbs!!, 1e-6)
        assertEquals(130.0, m.per100g["energy"]!!, 0.0)
        assertEquals(1.0, m.confidence, 1e-9)
        assertTrue(m.questions.isEmpty())
        assertTrue(m.autoAccepted)
        assertFalse(m.excluded)
        assertEquals("texto", m.text)
    }

    @Test fun volume_usesConceptDensityThenRecordThenOne() {
        assertGrams(257.5, draft("leche_entera", quantity = QuantityV2.Volume(250.0)))
        assertEquals(QuantityKindV2.VOLUME, one(draft("leche_entera", quantity = QuantityV2.Volume(250.0))).quantityKind)
        // Sin densidad en el concepto ni en la ficha: 1,0.
        assertGrams(200.0, draft("cafe", quantity = QuantityV2.Volume(200.0)))
        // Densidad de la ficha cuando el concepto no la trae.
        val withRecordDensity = ConceptCatalogV2(
            "g", catalog.concepts.values.map { if (it.id == "cafe") it.copy(densityGPerMl = null) else it },
            catalog.records + ("r_cafe" to catalog.records.getValue("r_cafe").copy(densityGPerMl = 1.2)),
        )
        assertGrams(240.0, draft("cafe", quantity = QuantityV2.Volume(200.0)), ResolverV2(withRecordDensity))
    }

    @Test fun count_withSizeAndFallbacks() {
        assertGrams(112.0, draft("huevo", quantity = QuantityV2.Count(2.0, SizeV2.L)))
        assertGrams(50.0, draft("huevo", quantity = QuantityV2.Count(1.0)))
        assertGrams(132.0, draft("huevo", quantity = QuantityV2.Count(3.0, SizeV2.S)))
        assertEquals(QuantityKindV2.COUNT, one(draft("huevo", quantity = QuantityV2.Count(1.0))).quantityKind)
        assertEquals(0.9, one(draft("huevo", quantity = QuantityV2.Count(1.0))).confidence, 1e-9)
        // Tamaño que falta en la unidad: el M.
        assertGrams(200.0, draft("marraqueta", quantity = QuantityV2.Count(2.0, SizeV2.L)))
        // Medio pan.
        assertGrams(50.0, draft("marraqueta", quantity = QuantityV2.Count(0.5)))
        // Sin `unit`: porción «unidad» del concepto; si no, defaultGrams.
        val withPortion = catalogWith("manzana") {
            it.copy(unit = null, portions = listOf(ConceptPortionV2("unidad", 170.0)))
        }
        assertGrams(340.0, draft("manzana", quantity = QuantityV2.Count(2.0)), ResolverV2(withPortion))
        val bare = catalogWith("manzana") { it.copy(unit = null, portions = emptyList()) }
        assertGrams(364.0, draft("manzana", quantity = QuantityV2.Count(2.0)), ResolverV2(bare))
    }

    @Test fun household_byConceptPortionPreferringVariantState() {
        assertGrams(160.0, draft("arroz_blanco", quantity = QuantityV2.Household(1.0, "taza")))
        assertGrams(185.0, draft("arroz_blanco", quantity = QuantityV2.Household(1.0, "taza"), state = FoodStateV2.RAW))
        assertGrams(24.0, draft("arroz_blanco", quantity = QuantityV2.Household(2.0, "cucharada")))
        assertGrams(200.0, draft("leche_entera", quantity = QuantityV2.Household(1.0, "vaso")))
        // Tamaño: L ×1,33 y S ×0,75.
        assertGrams(266.0, draft("leche_entera", quantity = QuantityV2.Household(1.0, "vaso", SizeV2.L)))
        assertGrams(150.0, draft("leche_entera", quantity = QuantityV2.Household(1.0, "vaso", SizeV2.S)))
        val m = one(draft("arroz_blanco", quantity = QuantityV2.Household(1.0, "taza")))
        assertEquals(QuantityKindV2.HOUSEHOLD, m.quantityKind)
        assertEquals(0.9, m.confidence, 1e-9)
    }

    @Test fun household_byRecordPortionTranslation() {
        // «cup» de la ficha, sin cantidad inicial.
        assertGrams(237.0, draft("cafe", quantity = QuantityV2.Household(1.0, "taza")))
        // «1 cup, sliced».
        assertGrams(109.0, draft("manzana", quantity = QuantityV2.Household(1.0, "taza")))
        // «2 tbsp» = 30 g → 15 g por cucharada.
        assertGrams(15.0, draft("yogur", quantity = QuantityV2.Household(1.0, "cucharada")))
        assertGrams(45.0, draft("yogur", quantity = QuantityV2.Household(3.0, "cucharada")))
        // «1 medium» para unidad.
        assertGrams(182.0, draft("manzana", quantity = QuantityV2.Household(1.0, "unidad")))
        // «1 slice» para rebanada y lámina.
        assertGrams(25.0, draft("marraqueta", quantity = QuantityV2.Household(1.0, "rebanada")))
        // Fracción al inicio de la etiqueta.
        val fraction = ConceptCatalogV2(
            "g", catalog.concepts.values.toList(),
            catalog.records + ("r_cafe" to catalog.records.getValue("r_cafe").copy(portions = listOf(RecordPortionV2("1/2 cup", 100.0)))),
        )
        assertGrams(200.0, draft("cafe", quantity = QuantityV2.Household(1.0, "taza")), ResolverV2(fraction))
    }

    @Test fun household_genericWhenNothingElseMatches() {
        // Bebida: densidad 1,0.
        assertGrams(150.0, draft("cafe", quantity = QuantityV2.Household(1.0, "copa")))
        // No bebida, sin densidad: 0,85.
        assertGrams(102.0, draft("arroz_blanco", quantity = QuantityV2.Household(1.0, "cucharon")))
        // Polvo: 0,6.
        assertGrams(72.0, draft("leche_en_polvo_entera", quantity = QuantityV2.Household(1.0, "cucharon")))
        // Medidas en gramos.
        assertGrams(300.0, draft("huevo", quantity = QuantityV2.Household(1.0, "plato")))
        assertGrams(60.0, draft("manzana", quantity = QuantityV2.Household(2.0, "punado")))
        assertGrams(0.4, draft("manzana", quantity = QuantityV2.Household(1.0, "pizca")))
        // Porción = defaultGrams; unidad = regla de conteo.
        assertGrams(182.0, draft("manzana", quantity = QuantityV2.Household(1.0, "porcion")))
        assertGrams(100.0, draft("huevo", quantity = QuantityV2.Household(2.0, "unidad")))
    }

    @Test fun container_netContentBeatsEverything() {
        assertGrams(120.0, draft("atun_en_aceite", quantity = QuantityV2.Container(1.0, "lata")))
        assertGrams(160.0, draft("atun_en_aceite", quantity = QuantityV2.Container(2.0, "lata", netGrams = 80.0)))
        assertGrams(300.0, draft("cafe", quantity = QuantityV2.Container(1.0, "botella", netMl = 300.0)))
        assertGrams(62.5, draft("yogur", quantity = QuantityV2.Container(0.5, "envase", netGrams = 125.0)))
        assertEquals(QuantityKindV2.CONTAINER, one(draft("yogur", quantity = QuantityV2.Container(1.0, "envase"))).quantityKind)
    }

    @Test fun container_recordPortionThenGeneric() {
        // Sin porción «lata» en el concepto: «1 can» de la ficha de la forma pedida.
        val noPortion = catalogWith("atun_en_aceite") { it.copy(portions = emptyList()) }
        val r = ResolverV2(noPortion)
        assertGrams(142.0, draft("atun_en_aceite", quantity = QuantityV2.Container(1.0, "lata")), r)
        assertGrams(170.0, draft("atun_en_aceite", quantity = QuantityV2.Container(1.0, "lata"), form = FoodFormV2.CANNED_UNDRAINED), r)
        // Genéricos.
        assertGrams(350.0, draft("cafe", quantity = QuantityV2.Container(1.0, "lata")))
        assertGrams(206.0, draft("leche_entera", quantity = QuantityV2.Container(1.0, "caja")))
        assertGrams(10.0, draft("marraqueta", quantity = QuantityV2.Container(1.0, "sobre")))
        assertGrams(125.0, draft("marraqueta", quantity = QuantityV2.Container(1.0, "envase")))
        assertGrams(170.0, draft("marraqueta", quantity = QuantityV2.Container(1.0, "lata")))
    }

    @Test fun vague_halfOfDefaultGrams() {
        val m = one(draft("arroz_blanco", quantity = QuantityV2.Vague))
        assertEquals(90.0, m.grams!!, 1e-9)
        assertEquals(QuantityKindV2.VAGUE, m.quantityKind)
        assertEquals(0.7, m.confidence, 1e-9)
    }

    @Test fun noQuantity_looseAndAttached() {
        val loose = one(draft("arroz_blanco"))
        assertEquals(180.0, loose.grams!!, 1e-9)
        assertEquals(QuantityKindV2.DEFAULT, loose.quantityKind)
        assertEquals(0.7, loose.confidence, 1e-9)
        // Contable suelto: una unidad M.
        assertGrams(50.0, draft("huevo"))
        assertGrams(150.0, draft("palta"))
        // Colgada: la menor porción entre cucharada/rebanada/lámina/porción.
        assertGrams(25.0, draft("palta", attachedTo = 0))
        // Colgada sin esas porciones: la mitad de defaultGrams.
        assertGrams(91.0, draft("manzana", attachedTo = 0))
    }

    // ─── Reconstitución ────────────────────────────────────────────────

    @Test fun reconstitution_glassOfPowderIsPreparedMilk() {
        val m = one(draft("leche_en_polvo_entera", quantity = QuantityV2.Household(1.0, "vaso")))
        assertEquals("r_leche_recon", m.recordId)
        assertEquals(FoodFormV2.RECONSTITUTED, m.form)
        assertEquals(206.0, m.grams!!, 1e-6)
        assertEquals(QuantityKindV2.HOUSEHOLD, m.quantityKind)
        assertEquals(61.0 * 2.06, m.eatenKcal!!, 1e-6)
        // Volumen y envase de bebida también.
        assertGrams(257.5, draft("leche_en_polvo_entera", quantity = QuantityV2.Volume(250.0)))
        assertGrams(515.0, draft("leche_en_polvo_entera", quantity = QuantityV2.Container(1.0, "botella")))
        // Pedir explícitamente «en polvo» con un vaso sigue siendo la bebida.
        assertEquals(
            "r_leche_recon",
            one(draft("leche_en_polvo_entera", quantity = QuantityV2.Household(1.0, "vaso"), form = FoodFormV2.POWDER_DRY)).recordId,
        )
    }

    @Test fun reconstitution_tablespoonsAndMassStayDryPowder() {
        val m = one(draft("leche_en_polvo_entera", quantity = QuantityV2.Household(2.0, "cucharada")))
        assertEquals("r_leche_polvo", m.recordId)
        assertEquals(FoodFormV2.POWDER_DRY, m.form)
        assertEquals(14.0, m.grams!!, 1e-9)
        assertEquals(496.0 * 0.14, m.eatenKcal!!, 1e-6)
        assertEquals("r_leche_polvo", one(draft("leche_en_polvo_entera", quantity = QuantityV2.Mass(30.0))).recordId)
        assertEquals("r_leche_polvo", one(draft("leche_en_polvo_entera", quantity = QuantityV2.Container(1.0, "sobre"))).recordId)
        // Forma pedida «preparada» con masa: ficha reconstituida, sin regla de bebida.
        val prepared = one(draft("leche_en_polvo_entera", quantity = QuantityV2.Mass(200.0), form = FoodFormV2.RECONSTITUTED))
        assertEquals("r_leche_recon", prepared.recordId)
        assertEquals(200.0, prepared.grams!!, 1e-9)
    }

    @Test fun reconstitution_withoutPreparedVariantUsesPowderAndRatio() {
        val noPrepared = catalogWith("leche_en_polvo_entera") { c ->
            c.copy(variants = c.variants.filter { it.form != FoodFormV2.RECONSTITUTED })
        }
        val m = one(draft("leche_en_polvo_entera", quantity = QuantityV2.Household(1.0, "vaso")), ResolverV2(noPrepared))
        assertEquals("r_leche_polvo", m.recordId)
        assertEquals(26.0, m.grams!!, 1e-9)
        assertEquals(496.0 * 0.26, m.eatenKcal!!, 1e-6)
    }

    // ─── Variante ──────────────────────────────────────────────────────

    @Test fun variant_grilledFallsBackToCooked() {
        val m = one(draft("arroz_blanco", quantity = QuantityV2.Mass(100.0), state = FoodStateV2.GRILLED))
        assertEquals("r_arroz_cocido", m.recordId)
        assertEquals(FoodStateV2.COOKED, m.state)
    }

    @Test fun variant_statedAndFormedChoices() {
        assertEquals("r_arroz_crudo", one(draft("arroz_blanco", quantity = QuantityV2.Mass(100.0), state = FoodStateV2.RAW)).recordId)
        assertEquals("r_huevo_frito", one(draft("huevo", quantity = QuantityV2.Count(1.0), state = FoodStateV2.FRIED)).recordId)
        assertEquals("r_cafe", one(draft("cafe", quantity = QuantityV2.Volume(100.0), form = FoodFormV2.SUGAR_FREE)).recordId)
        assertEquals(FoodFormV2.SUGAR_FREE, one(draft("cafe", quantity = QuantityV2.Volume(100.0), form = FoodFormV2.SUGAR_FREE)).form)
        // Forma por defecto del concepto.
        assertEquals("r_atun_escurrido", one(draft("atun_en_aceite", quantity = QuantityV2.Mass(100.0))).recordId)
        assertEquals(
            "r_atun_sin_escurrir",
            one(draft("atun_en_aceite", quantity = QuantityV2.Mass(100.0), form = FoodFormV2.CANNED_UNDRAINED)).recordId,
        )
    }

    @Test fun variant_dishUsesDerivedRecord() {
        val m = one(draft("pastel_de_choclo", quantity = QuantityV2.Household(1.0, "plato")))
        assertEquals("kpkn_pastel_de_choclo", m.recordId)
        assertEquals(350.0, m.grams!!, 1e-9)
        assertEquals(490.0, m.eatenKcal!!, 1e-6)
    }

    @Test fun noVariantWithRecord_stillComputesGrams() {
        val noRecords = catalogWith("arroz_blanco") { c -> c.copy(variants = c.variants.map { it.copy(recordId = null) }) }
        val m = one(draft("arroz_blanco", quantity = QuantityV2.Household(1.0, "taza")), ResolverV2(noRecords))
        assertNull(m.recordId)
        assertTrue(m.per100g.isEmpty())
        assertNull(m.eatenKcal)
        assertNull(m.eatenProtein)
        assertNull(m.eatenFat)
        assertNull(m.eatenCarbs)
        assertEquals(160.0, m.grams!!, 1e-9)
        assertFalse(m.autoAccepted)
    }

    // ─── Preguntas y confianza ─────────────────────────────────────────

    @Test fun identityQuestion_whenCandidatesDifferMaterially() {
        val m = one(draft("churrasco_corte", "churrasco_sandwich", quantity = QuantityV2.Mass(150.0)))
        assertEquals("churrasco_corte", m.conceptId)
        assertEquals(listOf(QuestionV2(QuestionDimensionV2.IDENTITY, listOf("churrasco_corte", "churrasco_sandwich"))), m.questions)
        assertEquals(0.6, m.confidence, 1e-9)
        assertFalse(m.autoAccepted)
        assertEquals("r_churrasco_corte", m.recordId)
    }

    @Test fun identityQuestion_absentWhenNotMaterial() {
        val m = one(draft("churrasco_corte", "churrasco_sandwich", quantity = QuantityV2.Mass(20.0)))
        assertTrue(m.questions.isEmpty())
        assertEquals(1.0, m.confidence, 1e-9)
        assertTrue(m.autoAccepted)
    }

    @Test fun stateQuestion_onlyWhenNoStateAnywhereAndMaterial() {
        val big = one(draft("huevo", quantity = QuantityV2.Count(4.0)))
        val question = big.questions.single()
        assertEquals(QuestionDimensionV2.STATE, question.dimension)
        assertEquals(setOf("COOKED", "RAW", "FRIED"), question.options.toSet())
        assertFalse(big.autoAccepted)
        // Un huevo: diferencias por debajo del umbral.
        assertTrue(one(draft("huevo", quantity = QuantityV2.Count(1.0))).questions.isEmpty())
        // Con estado dicho, o con estado por defecto del concepto: no se pregunta.
        assertTrue(one(draft("huevo", quantity = QuantityV2.Count(4.0), state = FoodStateV2.RAW)).questions.isEmpty())
        assertTrue(one(draft("arroz_blanco", quantity = QuantityV2.Mass(300.0))).questions.isEmpty())
    }

    @Test fun formQuestion_whenFormIsOpenAndMaterial() {
        val open = catalogWith("atun_en_aceite") { it.copy(defaultForm = FoodFormV2.NA) }
        val m = one(draft("atun_en_aceite", quantity = QuantityV2.Mass(200.0)), ResolverV2(open))
        val q = m.questions.single()
        assertEquals(QuestionDimensionV2.FORM, q.dimension)
        assertEquals(setOf("CANNED_DRAINED", "CANNED_UNDRAINED"), q.options.toSet())
        // Con la forma dicha no se pregunta; con la forma por defecto distinta de NA tampoco.
        assertTrue(one(draft("atun_en_aceite", quantity = QuantityV2.Mass(200.0), form = FoodFormV2.CANNED_DRAINED), ResolverV2(open)).questions.isEmpty())
        assertTrue(one(draft("atun_en_aceite", quantity = QuantityV2.Mass(200.0))).questions.isEmpty())
    }

    @Test fun confidence_fuzzyAndQuantityFactors() {
        assertEquals(0.85, one(draft("arroz_blanco", quantity = QuantityV2.Mass(100.0), fuzzy = true)).confidence, 1e-9)
        assertEquals(0.85 * 0.9, one(draft("huevo", quantity = QuantityV2.Count(1.0), fuzzy = true)).confidence, 1e-9)
        assertEquals(0.7, one(draft("arroz_blanco")).confidence, 1e-9)
    }

    // ─── Excluidos y varias menciones ──────────────────────────────────

    @Test fun excluded_hasNoGramsNoQuestionsNotAccepted() {
        val m = one(draft("huevo", quantity = QuantityV2.Count(4.0), excluded = true))
        assertTrue(m.excluded)
        assertNull(m.grams)
        assertNull(m.recordId)
        assertNull(m.eatenKcal)
        assertTrue(m.questions.isEmpty())
        assertFalse(m.autoAccepted)
        assertEquals("huevo", m.conceptId)
    }

    @Test fun resolvesEveryDraftInOrderAndSlicesText() {
        val source = "2 huevos y arroz"
        val drafts = listOf(
            MentionDraftV2(0, 8, 2, 8, listOf("huevo"), QuantityV2.Count(2.0, null)),
            MentionDraftV2(11, 16, 11, 16, listOf("arroz_blanco"), null),
        )
        val result = resolver.resolve(source, drafts)
        assertEquals(listOf("2 huevos", "arroz"), result.map { it.text })
        assertEquals(listOf("huevo", "arroz_blanco"), result.map { it.conceptId })
        assertEquals(100.0, result[0].grams!!, 1e-9)
        assertEquals(180.0, result[1].grams!!, 1e-9)
    }

    // ─── Ronda 1: fracciones de pieza, plurales, estado/forma pedidos y preguntas de cantidad ───────────

    private fun typed(text: String, id: String, quantity: QuantityV2? = null, state: FoodStateV2? = null, form: FoodFormV2? = null) =
        MentionDraftV2(0, text.length, 0, text.length, listOf(id), quantity, state = state, form = form)

    private fun resolveText(text: String, d: MentionDraftV2, r: ResolverV2 = resolver): MentionV2 = r.resolve(text, listOf(d)).single()

    /** Concepto cuya unidad es un trozo de una pieza mayor, con porciones de pieza entera. */
    private fun pieCatalog() = catalogWith("palta") {
        it.copy(
            unit = CountUnitV2(listOf("trozo", "porcion"), mapOf(SizeV2.S to 80.0, SizeV2.M to 100.0, SizeV2.L to 120.0)),
            portions = listOf(
                ConceptPortionV2("trozo", 100.0),
                ConceptPortionV2("tarta chica", 600.0),
                ConceptPortionV2("tarta mediana", 800.0),
                ConceptPortionV2("tarta grande", 1000.0),
            ),
            defaultGrams = 200.0,
        )
    }

    @Test fun fraction_ofSlicedPieceIsAFractionOfTheWholePiece() {
        val r = ResolverV2(pieCatalog())
        val quarter = one(draft("palta", quantity = QuantityV2.Count(0.25)), r)
        assertEquals(200.0, quarter.grams!!, 1e-9) // 0,25 de la tarta mediana (800 g), no de un trozo
        assertFalse(quarter.autoAccepted)
        assertEquals(QuestionDimensionV2.QUANTITY, quarter.questions.single().dimension)
        // Piezas enteras o varios trozos: sigue siendo el trozo.
        assertEquals(200.0, one(draft("palta", quantity = QuantityV2.Count(2.0)), r).grams!!, 1e-9)
        assertEquals(100.0, one(draft("palta", quantity = QuantityV2.Count(1.0)), r).grams!!, 1e-9)
        // Unidad que ya es la pieza (manzana): la mitad es media unidad.
        assertGrams(91.0, draft("manzana", quantity = QuantityV2.Count(0.5)))
        // Sin porción de pieza entera: se queda con el trozo.
        val noWhole = catalogWith("palta") { it.copy(unit = CountUnitV2(listOf("trozo"), mapOf(SizeV2.M to 100.0))) }
        assertGrams(25.0, draft("palta", quantity = QuantityV2.Count(0.25)), ResolverV2(noWhole))
    }

    @Test fun plural_withoutNumberAssumesSeveralUnits() {
        val apples = resolveText("manzanas", typed("manzanas", "manzana"))
        assertEquals(364.0, apples.grams!!, 1e-9) // dos unidades M
        assertEquals(QuestionDimensionV2.QUANTITY, apples.questions.single().dimension)
        assertFalse(apples.autoAccepted)
        val one = resolveText("manzana", typed("manzana", "manzana"))
        assertEquals(182.0, one.grams!!, 1e-9)
        assertTrue(one.questions.isEmpty())
        // Unidad pequeña: tres.
        val small = catalogWith("manzana") { it.copy(unit = CountUnitV2(listOf("unidad"), mapOf(SizeV2.M to 12.0))) }
        assertEquals(36.0, resolveText("manzanas", typed("manzanas", "manzana"), ResolverV2(small)).grams!!, 1e-9)
        // Verduras en plural sin número: un acompañamiento de una porción, no varias piezas.
        val veg = catalogWith("manzana") { it.copy(category = "VERDURAS") }
        val side = resolveText("manzanas", typed("manzanas", "manzana"), ResolverV2(veg))
        assertEquals(182.0, side.grams!!, 1e-9)
        assertTrue(side.questions.isEmpty())
        // Con número o no contable: no se supone nada.
        assertEquals(182.0, resolveText("1 manzanas", typed("1 manzanas", "manzana", QuantityV2.Count(1.0))).grams!!, 1e-9)
        assertEquals(180.0, resolveText("arvejas", typed("arvejas", "arroz_blanco")).grams!!, 1e-9)
    }

    @Test fun attached_pieceFruitIsNotAGarnishSlice() {
        val slice = catalogWith("manzana") { it.copy(portions = listOf(ConceptPortionV2("rebanada", 8.0))) }
        assertGrams(91.0, draft("manzana", attachedTo = 0), ResolverV2(slice))
        // Una verdura de aderezo conserva su porción chica.
        val garnish = catalogWith("manzana") { it.copy(category = "VERDURAS", portions = listOf(ConceptPortionV2("rebanada", 8.0))) }
        assertGrams(8.0, draft("manzana", attachedTo = 0), ResolverV2(garnish))
        // Una porción de aderezo razonable respecto de la unidad se respeta (la palta de arriba: 25 g).
        assertGrams(25.0, draft("palta", attachedTo = 0))
    }

    @Test fun fallback_dietFormUsesTheOtherDietVariant() {
        val onlyLight = catalogWith("cafe") { c -> c.copy(variants = c.variants.map { if (it.form == FoodFormV2.SUGAR_FREE) it.copy(form = FoodFormV2.LIGHT) else it }) }
        val m = one(draft("cafe", quantity = QuantityV2.Volume(300.0), form = FoodFormV2.SUGAR_FREE), ResolverV2(onlyLight))
        assertEquals(FoodFormV2.LIGHT, m.form)
        assertTrue(m.questions.isEmpty())
    }

    @Test fun fallback_missingFriedStateAsksState() {
        // El arroz de la prueba no tiene variante frita: se usa la cocida y se pregunta (freír suma grasa).
        val m = one(draft("arroz_blanco", quantity = QuantityV2.Mass(300.0), state = FoodStateV2.FRIED))
        assertEquals(FoodStateV2.COOKED, m.state)
        assertEquals(QuestionDimensionV2.STATE, m.questions.single().dimension)
        assertFalse(m.autoAccepted)
        // Poca energía: no es material.
        assertTrue(one(draft("arroz_blanco", quantity = QuantityV2.Mass(20.0), state = FoodStateV2.FRIED)).questions.isEmpty())
        // Estados de cocción seca se dan por equivalentes.
        assertTrue(one(draft("arroz_blanco", quantity = QuantityV2.Mass(300.0), state = FoodStateV2.BAKED)).questions.isEmpty())
    }

    @Test fun fallback_missingDietFormAsksForm() {
        val m = one(draft("leche_entera", quantity = QuantityV2.Volume(400.0), form = FoodFormV2.SUGAR_FREE))
        assertEquals(FoodFormV2.NA, m.form)
        assertEquals(QuestionDimensionV2.FORM, m.questions.single().dimension)
        assertFalse(m.autoAccepted)
    }

    @Test fun quantityQuestion_onlyForMaterialGuessedAmounts() {
        // Sin cantidad en algo que no se cuenta: porción por defecto supuesta.
        val loose = one(draft("arroz_blanco"))
        assertEquals(QuestionDimensionV2.QUANTITY, loose.questions.single().dimension)
        assertFalse(loose.autoAccepted)
        // Poca energía: no se pregunta.
        assertTrue(one(draft("cafe")).questions.isEmpty())
        // Número dicho: no se pregunta.
        assertTrue(one(draft("arroz_blanco", quantity = QuantityV2.Household(1.0, "taza"))).questions.isEmpty())
        // Contable sin número: «un» X; sin pregunta, pero sin aceptar solo.
        assertTrue(one(draft("manzana")).questions.isEmpty())
        assertFalse(one(draft("manzana")).autoAccepted)
        assertTrue(one(draft("manzana", quantity = QuantityV2.Count(1.0))).autoAccepted)
        // Lo colgado sin cantidad no se pregunta (por confianza queda por confirmar).
        val attached = one(draft("arroz_blanco", attachedTo = 0))
        assertTrue(attached.questions.isEmpty())
        assertFalse(attached.autoAccepted)
        // Coincidencia aproximada sobre una cantidad estimada: por confirmar.
        assertFalse(one(draft("manzana", quantity = QuantityV2.Count(2.0), fuzzy = true)).autoAccepted)
        assertTrue(one(draft("arroz_blanco", quantity = QuantityV2.Mass(100.0), fuzzy = true)).autoAccepted)
        // Un poco de algo.
        assertEquals(QuestionDimensionV2.QUANTITY, one(draft("arroz_blanco", quantity = QuantityV2.Vague)).questions.single().dimension)
    }

    // ─── La ficha sigue a la porción cuando el texto no dice el estado ─────

    /** Arroz como la avena: crudo por defecto; «taza» y «cucharada» crudas; «plato» y «tazon» solo cocidos. */
    private fun oatLike() = catalogWith("arroz_blanco") {
        it.copy(
            defaultState = FoodStateV2.RAW,
            portions = listOf(
                ConceptPortionV2("cucharada", 7.0, FoodStateV2.RAW),
                ConceptPortionV2("taza", 90.0, FoodStateV2.RAW),
                ConceptPortionV2("taza", 234.0, FoodStateV2.COOKED),
                ConceptPortionV2("plato", 250.0, FoodStateV2.COOKED),
            ),
        )
    }

    @Test fun portionState_plateOnlyCookedAdoptsCooked() {
        val r = ResolverV2(oatLike())
        val m = one(draft("arroz_blanco", quantity = QuantityV2.Household(1.0, "plato")), r)
        assertEquals(FoodStateV2.COOKED, m.state)
        assertEquals("r_arroz_cocido", m.recordId)
        assertEquals(250.0, m.grams!!, 1e-9)
        assertEquals(325.0, m.eatenKcal!!, 1e-6)
        // Un envase sin contenido neto sigue la misma regla.
        val c = one(draft("arroz_blanco", quantity = QuantityV2.Container(1.0, "plato")), r)
        assertEquals(FoodStateV2.COOKED, c.state)
    }

    @Test fun portionState_cupWithRawPortionKeepsRaw() {
        val m = one(draft("arroz_blanco", quantity = QuantityV2.Household(1.0, "taza")), ResolverV2(oatLike()))
        assertEquals(FoodStateV2.RAW, m.state)
        assertEquals("r_arroz_crudo", m.recordId)
        assertEquals(90.0, m.grams!!, 1e-9)
    }

    @Test fun portionState_statedStateIsNeverChanged() {
        val r = ResolverV2(oatLike())
        val m = one(draft("arroz_blanco", quantity = QuantityV2.Household(1.0, "plato"), state = FoodStateV2.RAW), r)
        assertEquals(FoodStateV2.RAW, m.state)
        assertEquals("r_arroz_crudo", m.recordId)
        assertEquals(250.0, m.grams!!, 1e-9)
    }

    // ─── Ronda N49: fracción de pieza por tamaño y snack de bolsa ──────────────────────────────

    private fun plainConcept(
        id: String,
        unit: CountUnitV2? = null,
        portions: List<ConceptPortionV2> = emptyList(),
        defaultGrams: Double = 100.0,
    ) = ConceptV2(
        id = id, name = id, kind = ConceptKindV2.FOOD, category = "TEST", aliasesEs = emptyList(), aliasesEn = emptyList(),
        typos = emptyList(), defaultState = null, defaultForm = FoodFormV2.NA, variants = emptyList(),
        countable = unit != null, unit = unit, portions = portions, defaultGrams = defaultGrams, densityGPerMl = null,
        reconstitution = null, components = null,
    )

    @Test fun fractionOfAWholeItemFollowsTheSize() {
        val pie = plainConcept(
            "tarta_t",
            unit = CountUnitV2(listOf("trozo", "porcion"), mapOf(SizeV2.S to 90.0, SizeV2.M to 110.0, SizeV2.L to 135.0)),
            portions = listOf(
                ConceptPortionV2("trozo", 110.0), ConceptPortionV2("tarta individual", 330.0),
                ConceptPortionV2("tarta mediana", 770.0), ConceptPortionV2("tarta familiar", 1100.0),
            ),
        )
        val r = ResolverV2(ConceptCatalogV2("t", listOf(pie), emptyMap()))
        // Sin tamaño: la mediana. «Familiar» o «grande»: la mayor. «Chica»: la individual.
        assertGrams(192.5, draft("tarta_t", quantity = QuantityV2.Count(0.25)), r)
        assertGrams(275.0, draft("tarta_t", quantity = QuantityV2.Count(0.25, SizeV2.L)), r)
        assertGrams(82.5, draft("tarta_t", quantity = QuantityV2.Count(0.25, SizeV2.S)), r)
        assertGrams(385.0, draft("tarta_t", quantity = QuantityV2.Count(0.5, SizeV2.M)), r)
        // Un conteo entero sigue siendo trozos.
        assertGrams(180.0, draft("tarta_t", quantity = QuantityV2.Count(2.0, SizeV2.S)), r)
    }

    @Test fun aPackOfFriesIsTheBaggedSnack() {
        val homemade = plainConcept("papas_fritas_caseras", portions = listOf(ConceptPortionV2("paquete", 150.0)), defaultGrams = 150.0)
        val bagged = plainConcept("papas_fritas_bolsa", portions = listOf(ConceptPortionV2("paquete", 150.0)), defaultGrams = 30.0)
        val r = ResolverV2(ConceptCatalogV2("t", listOf(homemade, bagged), emptyMap()))
        assertEquals("papas_fritas_bolsa", one(draft("papas_fritas_caseras", quantity = QuantityV2.Container(0.5, "paquete")), r).conceptId)
        assertEquals("papas_fritas_caseras", one(draft("papas_fritas_caseras", quantity = QuantityV2.Household(1.0, "plato")), r).conceptId)
        assertEquals("papas_fritas_caseras", one(draft("papas_fritas_caseras"), r).conceptId)
        // Sin el hermano envasado en el catálogo no se cambia nada.
        val alone = ResolverV2(ConceptCatalogV2("t", listOf(homemade), emptyMap()))
        assertEquals("papas_fritas_caseras", one(draft("papas_fritas_caseras", quantity = QuantityV2.Container(0.5, "paquete")), alone).conceptId)
    }
}
