package com.example.kpkn.domain.nutrition.v2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GrammarV2Test {

    private fun food(alias: String, id: String, kind: AliasKind = AliasKind.ES): LexEntry =
        LexEntry.of(alias, LexTag.Food(id, kind))!!

    private val foodEntries = listOf(
        food("arroz", "arroz"),
        food("arroz con pollo", "arroz_con_pollo"),
        food("pollo", "pollo"),
        food("huevo", "huevo"),
        food("huevos", "huevo"),
        food("huevos revueltos", "huevo_revuelto"),
        food("manzana", "manzana"),
        food("manzanas", "manzana"),
        food("mantequilla de mani", "mantequilla_mani"),
        food("mantequilla", "mantequilla"),
        food("atun", "atun"),
        food("yogur", "yogur"),
        food("yogures", "yogur"),
        food("leche", "leche"),
        food("cafe", "cafe"),
        food("azucar", "azucar"),
        food("hamburguesa", "hamburguesa"),
        food("pan", "pan"),
        food("palta", "palta"),
        food("tomate", "tomate"),
        food("queso", "queso"),
        food("tostada", "tostada"),
        food("tostadas", "tostada"),
        food("avena", "avena"),
        // Falta de ortografía conocida.
        food("palto", "palta", AliasKind.TYPO),
        // Alias ambiguo: el mismo texto nombra dos conceptos.
        food("choclo", "maiz_grano"),
        food("choclo", "maiz_mazorca"),
        // Inglés.
        food("toast", "tostada", AliasKind.EN),
        food("toasts", "tostada", AliasKind.EN),
        food("coffee", "cafe", AliasKind.EN),
        food("avocado", "palta", AliasKind.EN),
        food("eggs", "huevo", AliasKind.EN),
        food("apple", "manzana", AliasKind.EN),
        food("rice", "arroz", AliasKind.EN),
        food("cheese", "queso", AliasKind.EN),
        food("tomato", "tomate", AliasKind.EN),
    )

    // «en aceite» no está en el vocabulario general: la forma enlatada la aporta el léxico de prueba.
    private val lexicon = LexiconV2(
        ModifierVocabularyV2.entries + foodEntries +
            LexEntry.of("en aceite", LexTag.Form(FoodFormV2.CANNED_UNDRAINED))!!,
    )

    private fun parse(text: String, lex: LexiconV2 = lexicon, catalog: ConceptCatalogV2? = null): List<MentionDraftV2> {
        val tokens = NormalizerV2.tokenize(text)
        return GrammarV2.parse(tokens, lex.tag(tokens), catalog)
    }

    private fun single(text: String): MentionDraftV2 {
        val drafts = parse(text)
        assertEquals("«$text» → $drafts", 1, drafts.size)
        return drafts[0]
    }

    private fun ids(drafts: List<MentionDraftV2>): List<List<String>> = drafts.map { it.conceptIds }

    // ─── Cantidad → alimento ────────────────────────────────────────────

    @Test fun `150 g de pollo en sus tres ordenes`() {
        for (text in listOf("150 g de pollo", "pollo 150g", "150 grs pollo", "pollo de 150 g")) {
            val d = single(text)
            assertEquals(text, listOf("pollo"), d.conceptIds)
            assertEquals(text, QuantityV2.Mass(150.0), d.quantity)
            assertNull(text, d.attachedTo)
            assertFalse(d.excluded)
        }
    }

    @Test fun `el tramo cubre la cantidad y el alimento`() {
        val text = "150 g de pollo"
        val d = single(text)
        assertEquals(0, d.start)
        assertEquals(text.length, d.end)
        assertEquals("pollo", text.substring(d.foodStart, d.foodEnd))
        val withFiller = "Me comí 2 huevos fritos"
        val e = single(withFiller)
        assertEquals(withFiller.indexOf("2"), e.start)
        assertEquals(withFiller.length, e.end)
        assertEquals("huevos", withFiller.substring(e.foodStart, e.foodEnd))
    }

    @Test fun `huevos revueltos como alias de plato y huevos fritos como estado`() {
        val scrambled = single("2 huevos revueltos")
        assertEquals(listOf("huevo_revuelto"), scrambled.conceptIds)
        assertEquals(QuantityV2.Count(2.0), scrambled.quantity)
        val fried = single("2 huevos fritos")
        assertEquals(listOf("huevo"), fried.conceptIds)
        assertEquals(QuantityV2.Count(2.0), fried.quantity)
        assertEquals(FoodStateV2.FRIED, fried.state)
    }

    @Test fun `conteo con tamano antes o despues del alimento`() {
        val spanish = single("una manzana grande")
        assertEquals(listOf("manzana"), spanish.conceptIds)
        assertEquals(QuantityV2.Count(1.0, SizeV2.L), spanish.quantity)
        assertEquals(QuantityV2.Count(1.0, SizeV2.L), single("1 big apple").quantity)
        assertEquals(QuantityV2.Count(2.0, SizeV2.S), single("2 manzanas chicas").quantity)
    }

    @Test fun `medida casera con de y alimento de varias palabras`() {
        val d = single("2 cucharadas de mantequilla de maní")
        assertEquals(listOf("mantequilla_mani"), d.conceptIds)
        assertEquals(QuantityV2.Household(2.0, "cucharada"), d.quantity)
    }

    @Test fun `envase con forma enlatada del lexico`() {
        val d = single("media lata de atún en aceite")
        assertEquals(listOf("atun"), d.conceptIds)
        assertEquals(QuantityV2.Container(0.5, "lata"), d.quantity)
        assertEquals(FoodFormV2.CANNED_UNDRAINED, d.form)
    }

    @Test fun `contenido neto del envase`() {
        val yogurt = single("medio yogur de un envase de 125 g")
        assertEquals(listOf("yogur"), yogurt.conceptIds)
        assertEquals(QuantityV2.Container(0.5, "envase", netGrams = 125.0), yogurt.quantity)
        assertEquals(QuantityV2.Container(1.0, "lata", netGrams = 170.0), single("una lata de atún de 170 g").quantity)
        assertEquals(QuantityV2.Container(1.0, "lata", netGrams = 170.0), single("una lata de 170 g de atún").quantity)
        assertEquals(QuantityV2.Container(1.0, "botella", netMl = 500.0), single("una botella de 500 ml de leche").quantity)
    }

    @Test fun `un alimento de tanto gramos multiplica por el conteo`() {
        assertEquals(QuantityV2.Mass(125.0), single("un yogur de 125 g").quantity)
        assertEquals(QuantityV2.Mass(250.0), single("2 yogures de 125 g").quantity)
    }

    @Test fun `leche en polvo con medida de bebida queda como forma en polvo`() {
        val d = single("un vaso de leche en polvo")
        assertEquals(listOf("leche"), d.conceptIds)
        assertEquals(QuantityV2.Household(1.0, "vaso"), d.quantity)
        assertEquals(FoodFormV2.POWDER_DRY, d.form)
    }

    @Test fun `medio kilo y un cuarto de kilo`() {
        assertEquals(QuantityV2.Mass(500.0), single("medio kilo de arroz").quantity)
        assertEquals(QuantityV2.Mass(250.0), single("un cuarto de kilo de queso").quantity)
        assertEquals(QuantityV2.Volume(1000.0), single("un litro de leche").quantity)
        assertEquals(QuantityV2.Household(1.5, "taza"), single("1 1/2 tazas de arroz").quantity)
    }

    @Test fun `un poco de y sus versiones`() {
        val d = single("un poco de arroz")
        assertEquals(listOf("arroz"), d.conceptIds)
        assertEquals(QuantityV2.Vague, d.quantity)
        assertEquals(QuantityV2.Vague, single("algo de pan").quantity)
        assertEquals(QuantityV2.Vague, single("a bit of rice").quantity)
        assertEquals(QuantityV2.Vague, single("a little of rice").quantity)
        assertEquals(QuantityV2.Vague, single("some cheese").quantity)
    }

    @Test fun `un par y una docena`() {
        assertEquals(QuantityV2.Count(2.0), single("un par de huevos").quantity)
        assertEquals(QuantityV2.Count(12.0), single("una docena de huevos").quantity)
        assertEquals(QuantityV2.Count(24.0), single("2 docenas de huevos").quantity)
        assertEquals(QuantityV2.Count(6.0), single("media docena de huevos").quantity)
    }

    @Test fun `medida casera sin numero vale uno`() {
        assertEquals(QuantityV2.Household(1.0, "taza"), single("taza de café").quantity)
        assertEquals(QuantityV2.Container(1.0, "lata"), single("lata de atún").quantity)
    }

    @Test fun `medida casera con tamano`() {
        assertEquals(QuantityV2.Household(1.0, "taza", SizeV2.L), single("una taza grande de café").quantity)
        assertEquals(QuantityV2.Household(1.0, "taza", SizeV2.L), single("una taza de café grande").quantity)
    }

    @Test fun `medida casera detras del alimento`() {
        assertEquals(QuantityV2.Household(2.0, "taza"), single("arroz 2 tazas").quantity)
    }

    @Test fun `unidad es un conteo`() {
        assertEquals(QuantityV2.Count(3.0), single("3 unidades de pan").quantity)
    }

    @Test fun `un articulo suelto no cuenta sin alimento pegado`() {
        val d = single("pollo y una tarde")
        assertEquals(listOf("pollo"), d.conceptIds)
        assertNull(d.quantity)
        assertNull(single("pollo").quantity)
    }

    @Test fun `hasta dos desconocidos entre la cantidad y el alimento no rompen el enlace`() {
        assertEquals(QuantityV2.Count(2.0), single("2 deliciosas manzanas").quantity)
        assertEquals(QuantityV2.Count(2.0), single("2 muy ricas manzanas").quantity)
        assertNull(single("2 muy muy ricas manzanas").quantity)
    }

    // ─── Modificadores ──────────────────────────────────────────────────

    @Test fun `sin azucar es forma y no exclusion`() {
        val d = single("café sin azúcar")
        assertEquals(listOf("cafe"), d.conceptIds)
        assertEquals(FoodFormV2.SUGAR_FREE, d.form)
        assertFalse(d.excluded)
    }

    @Test fun `con agua y con leche son medio`() {
        val oatmeal = single("avena con agua")
        assertEquals(MediumV2.WATER, oatmeal.medium)
        // «arroz con leche» no es plato en este léxico: «con leche» es un medio del arroz.
        val rice = single("arroz con leche")
        assertEquals(listOf("arroz"), rice.conceptIds)
        assertEquals(MediumV2.MILK, rice.medium)
    }

    @Test fun `el modificador va al alimento mas cercano y en empate al anterior`() {
        val drafts = parse("arroz cocido con pollo frito")
        assertEquals(2, drafts.size)
        assertEquals(FoodStateV2.COOKED, drafts[0].state)
        assertEquals(FoodStateV2.FRIED, drafts[1].state)
        val tie = parse("pollo frito arroz")
        assertEquals(FoodStateV2.FRIED, tie[0].state)
        assertNull(tie[1].state)
    }

    @Test fun `estado de varias palabras`() {
        assertEquals(FoodStateV2.GRILLED, single("pollo a la plancha").state)
        assertEquals(FoodStateV2.BAKED, single("pollo al horno").state)
    }

    // ─── «sin X» ────────────────────────────────────────────────────────

    @Test fun `hamburguesa sin pan excluye el pan`() {
        val drafts = parse("hamburguesa sin pan")
        assertEquals(listOf(listOf("hamburguesa"), listOf("pan")), ids(drafts))
        assertFalse(drafts[0].excluded)
        assertTrue(drafts[1].excluded)
        assertNull(drafts[1].quantity)
        assertNull(drafts[1].attachedTo)
    }

    private fun concept(id: String, name: String, vararg forms: FoodFormV2) = ConceptV2(
        id = id, name = name, kind = ConceptKindV2.FOOD, category = "test",
        aliasesEs = emptyList(), aliasesEn = emptyList(), typos = emptyList(),
        defaultState = null, defaultForm = FoodFormV2.NA,
        variants = forms.map { ConceptVariantV2(null, it, null) },
        countable = false, unit = null, portions = emptyList(), defaultGrams = 100.0,
        densityGPerMl = null, reconstitution = null, components = null,
    )

    @Test fun `sin X es forma si el catalogo dice que la base tiene esa variante`() {
        // Léxico sin la entrada «sin azúcar»: «sin» y «azúcar» llegan por separado.
        val noPhrase = ModifierVocabularyV2.entries.filterNot { it.tokens == listOf("sin", "azucar") }
        val bare = LexiconV2(noPhrase + foodEntries)
        val without = parse("café sin azúcar", bare)
        assertEquals(2, without.size)
        assertTrue(without[1].excluded)

        val catalog = ConceptCatalogV2(
            "t",
            listOf(concept("cafe", "Café", FoodFormV2.NA, FoodFormV2.SUGAR_FREE), concept("azucar", "Azúcar")),
            emptyMap(),
        )
        val withCatalog = parse("café sin azúcar", bare, catalog)
        assertEquals(1, withCatalog.size)
        assertEquals(FoodFormV2.SUGAR_FREE, withCatalog[0].form)
        assertFalse(withCatalog[0].excluded)

        // Si la base no tiene la variante, sigue siendo exclusión.
        val plain = ConceptCatalogV2("t", listOf(concept("cafe", "Café", FoodFormV2.NA), concept("azucar", "Azúcar")), emptyMap())
        val stillExcluded = parse("café sin azúcar", bare, plain)
        assertEquals(2, stillExcluded.size)
        assertTrue(stillExcluded[1].excluded)
    }

    // ─── «X con Y» ──────────────────────────────────────────────────────

    @Test fun `pan con palta y tomate cuelga de pan`() {
        val drafts = parse("pan con palta y tomate")
        assertEquals(listOf(listOf("pan"), listOf("palta"), listOf("tomate")), ids(drafts))
        assertEquals(listOf(null, 0, 0), drafts.map { it.attachedTo })
        assertTrue(drafts.all { it.quantity == null })
    }

    @Test fun `la cantidad de Y solo si la dice su tramo`() {
        val drafts = parse("2 huevos con queso")
        assertEquals(QuantityV2.Count(2.0), drafts[0].quantity)
        assertNull(drafts[1].quantity)
        assertEquals(0, drafts[1].attachedTo)
        val withQuantity = parse("pan con 2 rebanadas de queso")
        assertEquals(QuantityV2.Household(2.0, "rebanada"), withQuantity[1].quantity)
        assertEquals(0, withQuantity[1].attachedTo)
    }

    @Test fun `y con cantidad abre una mencion nueva dentro de una cadena`() {
        val drafts = parse("pan con palta y 2 huevos")
        assertEquals(listOf(null, 0, null), drafts.map { it.attachedTo })
        assertEquals(QuantityV2.Count(2.0), drafts[2].quantity)
    }

    @Test fun `plato conocido da un solo borrador`() {
        val d = single("almorcé arroz con pollo")
        assertEquals(listOf("arroz_con_pollo"), d.conceptIds)
        assertNull(d.attachedTo)
        val text = "almorcé arroz con pollo"
        assertEquals("arroz con pollo", text.substring(d.foodStart, d.foodEnd))
        // Sin el plato en el léxico: dos menciones.
        val plain = LexiconV2(ModifierVocabularyV2.entries + foodEntries.filter { it.tokens != listOf("arroz", "con", "pollo") })
        val drafts = parse(text, plain)
        assertEquals(listOf(listOf("arroz"), listOf("pollo")), ids(drafts))
        assertEquals(listOf(null, 0), drafts.map { it.attachedTo })
    }

    // ─── Cláusulas ──────────────────────────────────────────────────────

    @Test fun `dos huevos y dos tostadas son dos menciones con su cantidad`() {
        val drafts = parse("2 huevos y 2 tostadas")
        assertEquals(listOf(listOf("huevo"), listOf("tostada")), ids(drafts))
        assertEquals(listOf<QuantityV2?>(QuantityV2.Count(2.0), QuantityV2.Count(2.0)), drafts.map { it.quantity })
        assertEquals(listOf(null, null), drafts.map { it.attachedTo })
    }

    @Test fun `la coma separa clausulas y cada una tiene su cantidad`() {
        val drafts = parse("arroz 200 g, pollo 150 g")
        assertEquals(listOf(listOf("arroz"), listOf("pollo")), ids(drafts))
        assertEquals(listOf<QuantityV2?>(QuantityV2.Mass(200.0), QuantityV2.Mass(150.0)), drafts.map { it.quantity })
    }

    @Test fun `y seguido de un alimento sin cadena abre mencion nueva`() {
        val drafts = parse("pollo 150 g y arroz 200 g")
        assertEquals(listOf<QuantityV2?>(QuantityV2.Mass(150.0), QuantityV2.Mass(200.0)), drafts.map { it.quantity })
        val bare = parse("huevos y tostadas")
        assertEquals(listOf(null, null), bare.map { it.attachedTo })
        assertEquals(2, bare.size)
    }

    @Test fun `despues y luego cortan la clausula`() {
        val drafts = parse("2 huevos luego 1 manzana")
        assertEquals(listOf<QuantityV2?>(QuantityV2.Count(2.0), QuantityV2.Count(1.0)), drafts.map { it.quantity })
    }

    // ─── Distribución ───────────────────────────────────────────────────

    @Test fun `dos tostadas una con palta y otra con queso`() {
        val drafts = parse("dos tostadas, una con palta y otra con queso")
        assertEquals(listOf(listOf("tostada"), listOf("palta"), listOf("queso")), ids(drafts))
        assertEquals(QuantityV2.Count(2.0), drafts[0].quantity)
        assertEquals(listOf(null, 0, 0), drafts.map { it.attachedTo })
        assertNull(drafts[1].quantity)
        assertNull(drafts[2].quantity)
    }

    @Test fun `distribucion en una sola clausula y en tres`() {
        val sameClause = parse("2 tostadas una con palta y otra con queso")
        assertEquals(listOf(null, 0, 0), sameClause.map { it.attachedTo })
        val threeClauses = parse("2 tostadas, una con palta, otra con queso")
        assertEquals(listOf(null, 0, 0), threeClauses.map { it.attachedTo })
    }

    @Test fun `distribucion en ingles`() {
        val drafts = parse("two toasts, one with avocado and the other with cheese")
        assertEquals(listOf(listOf("tostada"), listOf("palta"), listOf("queso")), ids(drafts))
        assertEquals(QuantityV2.Count(2.0), drafts[0].quantity)
        assertEquals(listOf(null, 0, 0), drafts.map { it.attachedTo })
    }

    @Test fun `sin conteo mayor o igual a dos no hay distribucion`() {
        val drafts = parse("una tostada, una con palta")
        assertEquals(listOf(listOf("tostada"), listOf("palta")), ids(drafts))
        assertEquals(listOf(null, null), drafts.map { it.attachedTo })
    }

    // ─── Correcciones ───────────────────────────────────────────────────

    @Test fun `eran 150 g no 200 reemplaza la cantidad anterior`() {
        val drafts = parse("pollo 100 g, eran 150 g, no 200")
        assertEquals(1, drafts.size)
        assertEquals(QuantityV2.Mass(150.0), drafts[0].quantity)
        assertEquals("pollo 100 g", "pollo 100 g, eran 150 g, no 200".substring(drafts[0].start, drafts[0].end))
    }

    @Test fun `la marca de correccion puede ir en la clausula anterior`() {
        val drafts = parse("pollo 100 g, perdón, eran 150 g")
        assertEquals(QuantityV2.Mass(150.0), drafts[0].quantity)
        val bare = parse("pollo 100 g, perdón, 150")
        assertEquals(QuantityV2.Mass(150.0), bare[0].quantity)
        val count = parse("2 huevos, digo, 3")
        assertEquals(QuantityV2.Count(3.0), count[0].quantity)
    }

    @Test fun `una correccion de forma o estado reemplaza el del anterior`() {
        val drafts = parse("pollo, perdón, era frito")
        assertEquals(1, drafts.size)
        assertEquals(FoodStateV2.FRIED, drafts[0].state)
    }

    @Test fun `sin mencion previa o sin marca la correccion no hace nada`() {
        assertTrue(parse("eran 150 g, no 200").isEmpty())
        val drafts = parse("pollo 100 g, 150 g")
        assertEquals(QuantityV2.Mass(100.0), drafts[0].quantity)
    }

    @Test fun `una correccion con alimento se lee como mencion normal`() {
        val drafts = parse("pollo 100 g, perdón, 2 manzanas")
        assertEquals(listOf(listOf("pollo"), listOf("manzana")), ids(drafts))
        assertEquals(QuantityV2.Mass(100.0), drafts[0].quantity)
    }

    // ─── Inglés ─────────────────────────────────────────────────────────

    @Test fun `2 slices of toast and a coffee`() {
        val drafts = parse("I had 2 slices of toast and a coffee")
        assertEquals(listOf(listOf("tostada"), listOf("cafe")), ids(drafts))
        assertEquals(QuantityV2.Household(2.0, "rebanada"), drafts[0].quantity)
        assertEquals(QuantityV2.Count(1.0), drafts[1].quantity)
    }

    @Test fun `a cup of coffee y half an avocado`() {
        val cup = single("a cup of coffee")
        assertEquals(listOf("cafe"), cup.conceptIds)
        assertEquals(QuantityV2.Household(1.0, "taza"), cup.quantity)
        val avocado = single("half an avocado")
        assertEquals(listOf("palta"), avocado.conceptIds)
        assertEquals(QuantityV2.Count(0.5), avocado.quantity)
    }

    @Test fun `I had 2 eggs and toast`() {
        val drafts = parse("I had 2 eggs and toast")
        assertEquals(listOf(listOf("huevo"), listOf("tostada")), ids(drafts))
        assertEquals(QuantityV2.Count(2.0), drafts[0].quantity)
        assertNull(drafts[1].quantity)
    }

    @Test fun `ingles toast with avocado and tomato y ejemplos sueltos`() {
        val drafts = parse("toast with avocado and tomato")
        assertEquals(listOf(null, 0, 0), drafts.map { it.attachedTo })
        assertEquals(QuantityV2.Mass(200.0), single("200 g of rice").quantity)
        assertEquals(QuantityV2.Household(2.0, "cucharada"), single("2 tbsp of cheese").quantity)
        assertEquals(FoodStateV2.BAKED, single("baked apple").state)
        assertEquals(FoodFormV2.SUGAR_FREE, single("coffee sugar free").form)
    }

    // ─── Bordes ─────────────────────────────────────────────────────────

    @Test fun `texto vacio o sin alimentos da lista vacia`() {
        assertTrue(parse("").isEmpty())
        assertTrue(parse("   ").isEmpty())
        assertTrue(parse("hola que tal").isEmpty())
        assertTrue(parse("150 g").isEmpty())
        assertTrue(parse(", ; .").isEmpty())
        assertTrue(GrammarV2.parse(emptyList(), emptyList()).isEmpty())
    }

    @Test fun `desconocidos en medio se ignoran`() {
        val drafts = parse("2 xyzzy blabla pollo")
        assertEquals(listOf(listOf("pollo")), ids(drafts))
        assertEquals(QuantityV2.Count(2.0), drafts[0].quantity)
        val between = parse("arroz zzz pollo")
        assertEquals(listOf(listOf("arroz"), listOf("pollo")), ids(between))
    }

    @Test fun `una falta fuzzy pasa al borrador`() {
        val d = single("2 aroz")
        assertEquals(listOf("arroz"), d.conceptIds)
        assertTrue(d.fuzzy)
        assertEquals(QuantityV2.Count(2.0), d.quantity)
        assertFalse(single("2 arroz").fuzzy)
        // Falta conocida del léxico (alias TYPO): no es fuzzy.
        val typo = single("1 palto")
        assertEquals(listOf("palta"), typo.conceptIds)
        assertFalse(typo.fuzzy)
    }

    @Test fun `alias ambiguo lleva todos los conceptos en orden`() {
        val d = single("2 choclo")
        assertEquals(listOf("maiz_grano", "maiz_mazorca"), d.conceptIds)
        assertEquals(QuantityV2.Count(2.0), d.quantity)
    }

    @Test fun `los indices de attachedTo apuntan a la lista de salida`() {
        val drafts = parse("2 huevos, pan con palta")
        assertEquals(listOf(listOf("huevo"), listOf("pan"), listOf("palta")), ids(drafts))
        assertEquals(listOf(null, null, 1), drafts.map { it.attachedTo })
    }

    @Test fun `una cadena de con no se pega a la mencion de otra clausula`() {
        val drafts = parse("con queso")
        assertEquals(1, drafts.size)
        assertNull(drafts[0].attachedTo)
    }

    @Test fun `no antes de un alimento tambien excluye`() {
        val drafts = parse("pan no tomate")
        assertTrue(drafts[1].excluded)
    }
    // ─── Alcance de la negación (ronda 1) ───────────────────────────────

    @Test fun `ni encadena la exclusion`() {
        val drafts = parse("hamburguesa sin tomate ni queso")
        assertEquals(listOf(listOf("hamburguesa"), listOf("tomate"), listOf("queso")), ids(drafts))
        assertEquals(listOf(false, true, true), drafts.map { it.excluded })
    }

    @Test fun `un articulo entre sin y el alimento no rompe la exclusion`() {
        val drafts = parse("hamburguesa sin el tomate")
        assertEquals(listOf(false, true), drafts.map { it.excluded })
        val one = parse("arroz sin un huevo")
        assertEquals(listOf(false, true), one.map { it.excluded })
    }

    @Test fun `me salte y skipped excluyen el alimento`() {
        val es = parse("me salté el pan y comí arroz")
        assertEquals(listOf(listOf("pan"), listOf("arroz")), ids(es))
        assertEquals(listOf(true, false), es.map { it.excluded })
        val en = parse("I skipped the toast")
        assertEquals(listOf(true), en.map { it.excluded })
    }

    @Test fun `ingles didn't have excluye`() {
        val drafts = parse("I didn't have any cheese")
        assertEquals(1, drafts.size)
        assertTrue(drafts[0].excluded)
    }

    @Test fun `un verbo de intencion deja el alimento fuera hasta el pero`() {
        val drafts = parse("iba a pedir arroz con pollo pero me comí una manzana")
        assertEquals(listOf(listOf("arroz_con_pollo"), listOf("manzana")), ids(drafts))
        assertEquals(listOf(true, false), drafts.map { it.excluded })
        val en = parse("I was going to order eggs but had toast")
        assertEquals(listOf(true, false), en.map { it.excluded })
        val thought = parse("pensaba comprar queso")
        assertTrue(thought[0].excluded)
    }

    @Test fun `una intencion sin accion no excluye`() {
        // «quería un café» es una compra o un deseo cumplido; solo «quería pedir/comprar…» queda en el aire.
        val drafts = parse("queria un cafe y comí pan")
        assertFalse(drafts[0].excluded)
        assertFalse(drafts[1].excluded)
    }

    @Test fun `una tercera persona que pide o come no cuenta como comida de la persona`() {
        val drafts = parse("mi hermano pidió pollo, yo comí arroz")
        assertEquals(listOf(listOf("pollo"), listOf("arroz")), ids(drafts))
        assertEquals(listOf(true, false), drafts.map { it.excluded })
        val kids = parse("los niños comieron tostadas")
        assertTrue(kids[0].excluded)
    }

    @Test fun `una tercera persona que sirve a la persona no excluye`() {
        val drafts = parse("mi mamá me sirvió arroz")
        assertEquals(1, drafts.size)
        assertFalse(drafts[0].excluded)
        assertFalse(parse("mi mamá hizo arroz y me lo comí")[0].excluded)
    }

    @Test fun `él seguido de una cantidad es otra persona y el articulo no`() {
        val drafts = parse("yo pollo y él un huevo")
        assertEquals(listOf(listOf("pollo"), listOf("huevo")), ids(drafts))
        assertEquals(listOf(false, true), drafts.map { it.excluded })
        assertFalse(parse("comí el pollo")[0].excluded)
    }

    @Test fun `no fue seguido de un alimento lo excluye`() {
        val drafts = parse("no fue pollo, solo arroz")
        assertEquals(listOf(true, false), drafts.map { it.excluded })
    }

    @Test fun `bueno repite el alimento y sustituye la mencion anterior`() {
        val drafts = parse("me comí una manzana y bueno dos manzanas")
        assertEquals(1, drafts.size)
        assertEquals(QuantityV2.Count(2.0), drafts[0].quantity)
        val other = parse("pollo 100 g y bueno 2 manzanas")
        assertEquals(listOf(listOf("pollo"), listOf("manzana")), ids(other))
    }

    @Test fun `mejor al final corrige la cantidad del mismo alimento`() {
        val drafts = parse("2 manzanas, 3 manzanas mejor")
        assertEquals(1, drafts.size)
        assertEquals(QuantityV2.Count(3.0), drafts[0].quantity)
    }

    @Test fun `mentira corrige sin alimento nuevo`() {
        val drafts = parse("250 ml de leche, no mentira fueron 200 ml")
        assertEquals(1, drafts.size)
        assertEquals(QuantityV2.Volume(200.0), drafts[0].quantity)
    }

    @Test fun `una fruta o bebida en cadena de con y no se pega al plato`() {
        val lex = LexiconV2(
            ModifierVocabularyV2.entries + foodEntries +
                food("platano", "platano") + food("jugo de naranja", "jugo_naranja"),
        )
        fun concept(id: String, kind: ConceptKindV2, category: String) = ConceptV2(
            id = id, name = id, kind = kind, category = category, aliasesEs = emptyList(), aliasesEn = emptyList(),
            typos = emptyList(), defaultState = null, defaultForm = FoodFormV2.NA, variants = emptyList(),
            countable = true, unit = null, portions = emptyList(), defaultGrams = 100.0, densityGPerMl = null,
            reconstitution = null, components = null,
        )
        val catalog = ConceptCatalogV2(
            "t",
            listOf(
                concept("pan", ConceptKindV2.FOOD, "CEREALES_PANES"), concept("queso", ConceptKindV2.FOOD, "LACTEOS"),
                concept("tomate", ConceptKindV2.FOOD, "VERDURAS"), concept("platano", ConceptKindV2.FOOD, "FRUTAS"),
                concept("jugo_naranja", ConceptKindV2.DRINK, "BEBIDAS"), concept("avena", ConceptKindV2.FOOD, "CEREALES_PANES"),
                concept("leche", ConceptKindV2.DRINK, "LACTEOS"),
            ),
            emptyMap(),
        )
        val fruit = parse("avena con queso y platano", lex, catalog)
        assertEquals(listOf(null, 0, null), fruit.map { it.attachedTo })
        val drink = parse("pan con queso y jugo de naranja", lex, catalog)
        assertEquals(listOf(null, 0, null), drink.map { it.attachedTo })
        // Un ingrediente de plato sigue colgando de la base.
        val topping = parse("pan con queso y tomate", lex, catalog)
        assertEquals(listOf(null, 0, 0), topping.map { it.attachedTo })
    }

    @Test fun `mi papa con posesivo es una persona y no el tuberculo`() {
        val lex = LexiconV2(ModifierVocabularyV2.entries + foodEntries + food("papa", "papa") + food("papas", "papa"))
        val drafts = parse("mi papa pidió pollo y yo comí papas", lex)
        assertEquals(listOf(listOf("pollo"), listOf("papa")), ids(drafts))
        assertEquals(listOf(true, false), drafts.map { it.excluded })
        // Sin posesivo sigue siendo el tubérculo.
        assertEquals(listOf(listOf("papa")), ids(parse("200 g de papa", lex)))
    }

    @Test fun `una negacion que repite la base la retira`() {
        val drafts = parse("cené arroz y bueno no fue arroz, fue pollo")
        assertEquals(listOf(listOf("arroz"), listOf("pollo")), ids(drafts))
        assertEquals(listOf(true, false), drafts.map { it.excluded })
    }

    @Test fun `una palta en cadena de con sigue siendo aderezo del plato`() {
        val lex = LexiconV2(ModifierVocabularyV2.entries + foodEntries)
        fun concept(id: String, category: String) = ConceptV2(
            id = id, name = id, kind = ConceptKindV2.FOOD, category = category, aliasesEs = emptyList(), aliasesEn = emptyList(),
            typos = emptyList(), defaultState = null, defaultForm = FoodFormV2.NA, variants = emptyList(),
            countable = true, unit = null, portions = emptyList(), defaultGrams = 100.0, densityGPerMl = null,
            reconstitution = null, components = null,
        )
        val catalog = ConceptCatalogV2(
            "t",
            listOf(concept("pan", "CEREALES_PANES"), concept("queso", "LACTEOS"), concept("palta", "FRUTAS"), concept("manzana", "FRUTAS")),
            emptyMap(),
        )
        assertEquals(listOf(null, 0, 0), parse("pan con queso y palta", lex, catalog).map { it.attachedTo })
        assertEquals(listOf(null, 0, null), parse("pan con queso y manzana", lex, catalog).map { it.attachedTo })
    }
}
