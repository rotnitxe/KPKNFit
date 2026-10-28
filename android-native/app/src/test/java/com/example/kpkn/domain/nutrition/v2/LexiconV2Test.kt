package com.example.kpkn.domain.nutrition.v2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class LexiconV2Test {

    private fun food(alias: String, id: String, kind: AliasKind = AliasKind.ES): LexEntry =
        LexEntry.of(alias, LexTag.Food(id, kind))!!

    private val lexicon = LexiconV2.withModifiers(
        listOf(
            food("arroz", "arroz"),
            food("arroz con leche", "arroz_con_leche"),
            food("pollo", "pollo"),
            food("leche", "leche"),
            food("cafe", "cafe"),
            food("azucar", "azucar"),
            food("huevo", "huevo"),
            food("huevos", "huevo"),
            food("tostada", "tostada"),
            food("palta", "palta"),
            food("mantequilla", "mantequilla"),
            food("tomate", "tomate"),
            food("tres leches", "tres_leches"),
            food("eggs", "huevo", AliasKind.EN),
            food("scrambled eggs", "huevo_revuelto", AliasKind.EN),
            food("toast", "tostada", AliasKind.EN),
            // Alias ambiguo: el mismo texto nombra dos conceptos.
            food("choclo", "maiz_grano"),
            food("choclo", "maiz_mazorca"),
        ),
    )

    private fun spans(text: String, lex: LexiconV2 = lexicon): List<TaggedSpan> = lex.tag(NormalizerV2.tokenize(text))

    private fun foodIds(span: TaggedSpan): List<String> = span.tags.filterIsInstance<LexTag.Food>().map { it.conceptId }

    // ─── Cobertura y coincidencia más larga ─────────────────────────────

    @Test fun `cada token pertenece a un tramo y los tramos van en orden`() {
        val text = "Me comí 200g de arroz con pollo, 2 tazas ??? zzzz"
        val tokens = NormalizerV2.tokenize(text)
        val result = lexicon.tag(tokens)
        var expected = 0
        for (span in result) {
            assertEquals(expected, span.first)
            assertTrue(span.last >= span.first)
            expected = span.last + 1
        }
        assertEquals(tokens.size, expected)
    }

    @Test fun `un texto vacio no da tramos`() {
        assertTrue(lexicon.tag(emptyList()).isEmpty())
    }

    @Test fun `la coincidencia mas larga gana - arroz con leche contra arroz`() {
        val result = spans("arroz con leche")
        assertEquals(1, result.size)
        assertEquals(listOf("arroz_con_leche"), foodIds(result[0]))
        assertEquals(0, result[0].first)
        assertEquals(2, result[0].last)
        assertFalse(result[0].fuzzy)
    }

    @Test fun `si la frase larga no se completa se queda con el prefijo mas largo`() {
        val result = spans("arroz con pollo")
        assertEquals(3, result.size)
        assertEquals(listOf("arroz"), foodIds(result[0]))
        assertEquals(listOf<LexTag>(LexTag.Connector(ConnectorKind.WITH)), result[1].tags)
        assertEquals(listOf("pollo"), foodIds(result[2]))
    }

    @Test fun `con leche es un medio cuando no hay plato conocido`() {
        val plain = LexiconV2.withModifiers(listOf(food("arroz", "arroz"), food("leche", "leche")))
        val result = spans("arroz con leche", plain)
        assertEquals(2, result.size)
        assertEquals(listOf("arroz"), foodIds(result[0]))
        assertEquals(listOf<LexTag>(LexTag.Medium(MediumV2.MILK)), result[1].tags)
        assertEquals(1, result[1].first)
        assertEquals(2, result[1].last)
    }

    @Test fun `un alias ambiguo lleva todas sus etiquetas`() {
        val result = spans("choclo")
        assertEquals(1, result.size)
        assertEquals(listOf("maiz_grano", "maiz_mazorca"), foodIds(result[0]))
    }

    @Test fun `el mismo alias dos veces con la misma etiqueta no se duplica`() {
        val lex = LexiconV2(listOf(food("pan", "pan"), food("pan", "pan")))
        assertEquals(1, spans("pan", lex)[0].tags.size)
    }

    @Test fun `palabras desconocidas quedan sin etiquetas`() {
        val result = spans("zzzz")
        assertEquals(1, result.size)
        assertTrue(result[0].tags.isEmpty())
        assertFalse(result[0].fuzzy)
    }

    // ─── Modificadores ──────────────────────────────────────────────────

    @Test fun `a la plancha es un estado de varias palabras`() {
        val result = spans("pollo a la plancha")
        assertEquals(2, result.size)
        assertEquals(listOf<LexTag>(LexTag.State(FoodStateV2.GRILLED)), result[1].tags)
        assertEquals(1, result[1].first)
        assertEquals(3, result[1].last)
    }

    @Test fun `sin azucar es una forma y sin solo es negacion`() {
        val form = spans("cafe sin azucar")
        assertEquals(2, form.size)
        assertEquals(listOf<LexTag>(LexTag.Form(FoodFormV2.SUGAR_FREE)), form[1].tags)
        val negation = spans("cafe sin leche")
        assertEquals(3, negation.size)
        assertEquals(listOf<LexTag>(LexTag.Negation), negation[1].tags)
        assertEquals(listOf("leche"), foodIds(negation[2]))
        assertEquals(listOf<LexTag>(LexTag.Form(FoodFormV2.SUGAR_FREE)), spans("sugar-free")[0].tags)
    }

    @Test fun `unidades de masa y volumen con su equivalente`() {
        val result = spans("200g 1.5 kg 2 l 250ml 3 oz")
        val masses = result.flatMap { it.tags }.filterIsInstance<LexTag.MassUnit>().map { it.grams }
        val volumes = result.flatMap { it.tags }.filterIsInstance<LexTag.VolumeUnit>().map { it.ml }
        assertEquals(listOf(1.0, 1000.0, 28.35), masses)
        assertEquals(listOf(1000.0, 1.0), volumes)
    }

    @Test fun `medidas caseras y envases con etiqueta canonica`() {
        assertEquals(listOf<LexTag>(LexTag.Household("taza")), spans("tazas")[0].tags)
        assertEquals(listOf<LexTag>(LexTag.Household("cucharada")), spans("cda")[0].tags)
        assertEquals(listOf<LexTag>(LexTag.Household("cucharadita")), spans("tsp")[0].tags)
        assertEquals(listOf<LexTag>(LexTag.Household("punado")), spans("puñado")[0].tags)
        assertEquals(listOf<LexTag>(LexTag.Container("lata")), spans("lata")[0].tags)
        assertEquals(listOf<LexTag>(LexTag.Container("schop")), spans("pinta")[0].tags)
        assertEquals(listOf<LexTag>(LexTag.Size(SizeV2.L)), spans("grande")[0].tags)
        assertEquals(listOf<LexTag>(LexTag.State(FoodStateV2.RAW)), spans("crudo")[0].tags)
        assertEquals(listOf<LexTag>(LexTag.Form(FoodFormV2.POWDER_DRY)), spans("en polvo")[0].tags)
        assertEquals(listOf<LexTag>(LexTag.Medium(MediumV2.WATER)), spans("with water")[0].tags)
    }

    @Test fun `relleno y correcciones`() {
        val filler = spans("Me comí 200g de arroz")
        assertEquals(listOf<LexTag>(LexTag.Filler), filler[0].tags)
        assertEquals(1, filler[0].last)
        assertEquals(listOf<LexTag>(LexTag.Correction), spans("perdón")[0].tags)
        // «no espera» (corrección) gana a «no» (negación).
        assertEquals(listOf<LexTag>(LexTag.Correction), spans("no espera")[0].tags)
        assertEquals(listOf<LexTag>(LexTag.Negation), spans("no")[0].tags)
        assertEquals(listOf<LexTag>(LexTag.Filler), spans("i had")[0].tags)
    }

    // ─── Cantidades ─────────────────────────────────────────────────────

    @Test fun `los numeros del normalizador se etiquetan como cantidad`() {
        val result = spans("1,5 tazas")
        assertEquals(listOf<LexTag>(LexTag.Number(1.5, weak = false)), result[0].tags)
        assertEquals(listOf<LexTag>(LexTag.Household("taza")), result[1].tags)
        assertEquals(listOf<LexTag>(LexTag.Number(250.0, weak = false)), spans("doscientos cincuenta")[0].tags)
    }

    @Test fun `articulos son cantidad debil`() {
        assertEquals(listOf<LexTag>(LexTag.Number(1.0, weak = true)), spans("un")[0].tags)
        assertEquals(listOf<LexTag>(LexTag.Number(1.0, weak = true)), spans("a")[0].tags)
    }

    @Test fun `fracciones habladas y docenas`() {
        val quarter = spans("un cuarto de kilo")
        assertEquals(listOf<LexTag>(LexTag.Number(0.25, weak = false)), quarter[0].tags)
        assertEquals(listOf<LexTag>(LexTag.Preposition), quarter[1].tags)
        assertEquals(listOf<LexTag>(LexTag.MassUnit(1000.0)), quarter[2].tags)
        assertEquals(listOf<LexTag>(LexTag.Number(0.75, weak = false)), spans("tres cuartos")[0].tags)
        assertEquals(listOf<LexTag>(LexTag.Number(6.0, weak = false)), spans("media docena")[0].tags)
        assertEquals(listOf<LexTag>(LexTag.Number(6.0, weak = false)), spans("half a dozen")[0].tags)
        assertEquals(listOf<LexTag>(LexTag.Number(0.5, weak = false)), spans("medio")[0].tags)
        assertEquals(listOf<LexTag>(LexTag.Number(2.0, weak = false)), spans("par")[0].tags)
        val half = spans("half a cup")
        assertEquals(listOf<LexTag>(LexTag.Number(0.5, weak = false)), half[0].tags)
        assertEquals(listOf<LexTag>(LexTag.Household("taza")), half[1].tags)
    }

    @Test fun `un plato con numero hablado en su nombre se reconoce igual`() {
        val result = spans("tres leches")
        assertEquals(1, result.size)
        assertEquals(listOf("tres_leches"), foodIds(result[0]))
    }

    // ─── Signos ─────────────────────────────────────────────────────────

    @Test fun `los signos llevan su etiqueta y el mas suma conector`() {
        val result = spans("pollo, arroz + palta")
        assertEquals(listOf<LexTag>(LexTag.Punct(",")), result[1].tags)
        assertEquals(listOf<LexTag>(LexTag.Connector(ConnectorKind.AND), LexTag.Punct("+")), result[3].tags)
    }

    // ─── Inglés ─────────────────────────────────────────────────────────

    @Test fun `frase en ingles con alias de prueba`() {
        val result = spans("2 scrambled eggs with toast")
        assertEquals(4, result.size)
        assertEquals(listOf<LexTag>(LexTag.Number(2.0, weak = false)), result[0].tags)
        assertEquals(listOf<LexTag>(LexTag.Food("huevo_revuelto", AliasKind.EN)), result[1].tags)
        assertEquals(1, result[1].first)
        assertEquals(2, result[1].last)
        assertEquals(listOf<LexTag>(LexTag.Connector(ConnectorKind.WITH)), result[2].tags)
        assertEquals(listOf<LexTag>(LexTag.Food("tostada", AliasKind.EN)), result[3].tags)
    }

    // ─── Corrección de faltas ───────────────────────────────────────────

    @Test fun `una falta cercana y unica se corrige y se marca fuzzy`() {
        val result = spans("aroz")
        assertEquals(1, result.size)
        assertEquals(listOf("arroz"), foodIds(result[0]))
        assertTrue(result[0].fuzzy)
    }

    @Test fun `la falta dentro de una frase larga marca el tramo completo`() {
        val result = spans("aroz con leche")
        assertEquals(1, result.size)
        assertEquals(listOf("arroz_con_leche"), foodIds(result[0]))
        assertTrue(result[0].fuzzy)
        assertFalse(spans("arroz con leche")[0].fuzzy)
    }

    @Test fun `una palabra de tres letras no se corrige`() {
        // «pan» no existe y «pa» tampoco; ninguna llega a 4 letras.
        val lex = LexiconV2(listOf(food("pan", "pan"), food("pana", "pana")))
        assertTrue(spans("pa", lex)[0].tags.isEmpty())
        assertTrue(spans("pam", lex)[0].tags.isEmpty())
        assertTrue(spans("arz", lexicon)[0].tags.isEmpty())
    }

    @Test fun `si el candidato mas cercano no es unico no se corrige`() {
        val lex = LexiconV2(listOf(food("casa", "casa"), food("cosa", "cosa")))
        val result = spans("cusa", lex)[0]
        assertTrue(result.tags.isEmpty())
        assertFalse(result.fuzzy)
        // Con un solo vecino sí corrige.
        assertEquals(listOf("casa"), foodIds(spans("casu", lex)[0]))
    }

    @Test fun `entre dos candidatos gana el mas cercano si es unico`() {
        val lex = LexiconV2(listOf(food("mantequilla", "m1"), food("mantecado", "m2")))
        val result = spans("mantequila", lex)[0]
        assertEquals(listOf("m1"), foodIds(result))
        assertTrue(result.fuzzy)
    }

    @Test fun `de 8 letras en adelante se admiten dos cambios y antes solo uno`() {
        // «mantequiya» (10 letras) está a 2 de «mantequilla»; «tomaxx» (6) está a 2 de «tomate».
        assertEquals(listOf("mantequilla"), foodIds(spans("mantequiya")[0]))
        assertTrue(spans("tomaxx")[0].tags.isEmpty())
        assertEquals(listOf("tomate"), foodIds(spans("tomatx")[0]))
    }

    @Test fun `no se corrigen numeros ni palabras que ya estan en el vocabulario`() {
        assertEquals(listOf<LexTag>(LexTag.Household("taza")), spans("taza")[0].tags)
        assertFalse(spans("taza")[0].fuzzy)
        assertTrue(spans("2024")[0].tags.all { it is LexTag.Number })
    }

    @Test fun `distancia de edicion acotada`() {
        assertEquals(0, LexiconV2.boundedDistance("arroz", "arroz", 2))
        assertEquals(1, LexiconV2.boundedDistance("aroz", "arroz", 2))
        assertEquals(1, LexiconV2.boundedDistance("arroz", "arros", 2))
        assertEquals(2, LexiconV2.boundedDistance("abcdef", "abxdey", 2))
        assertEquals(3, LexiconV2.boundedDistance("abcdef", "xyzdef", 2))
        assertEquals(3, LexiconV2.boundedDistance("a", "abcdefg", 2))
        assertEquals(1, LexiconV2.boundedDistance("casa", "cas", 1))
    }

    @Test fun `un lexico vacio etiqueta todo como desconocido salvo numeros y signos`() {
        val empty = LexiconV2(emptyList())
        val result = spans("hola 2 ,", empty)
        assertTrue(result[0].tags.isEmpty())
        assertEquals(listOf<LexTag>(LexTag.Number(2.0, weak = false)), result[1].tags)
        assertEquals(listOf<LexTag>(LexTag.Punct(",")), result[2].tags)
        assertNull(result.firstOrNull { it.fuzzy })
    }

    // ─── Rendimiento ────────────────────────────────────────────────────

    @Test fun `tag de 30 tokens con 20000 alias es rapido`() {
        val random = Random(20261028L)
        val alphabet = "abcdefghijklmnopqrstuvwxyz"
        fun word(): String {
            val length = 3 + random.nextInt(9)
            return buildString { repeat(length) { append(alphabet[random.nextInt(alphabet.length)]) } }
        }
        val vocabulary = ArrayList<String>()
        val aliases = ArrayList<LexEntry>()
        repeat(20_000) { index ->
            val tokens = List(1 + random.nextInt(2)) { word() }
            vocabulary += tokens
            aliases += LexEntry(tokens, LexTag.Food("c$index", AliasKind.ES))
        }
        val lex = LexiconV2.withModifiers(aliases)

        fun typo(source: String): String {
            val chars = source.toCharArray()
            chars[random.nextInt(chars.size)] = alphabet[random.nextInt(alphabet.length)]
            return String(chars)
        }
        fun sentence(): List<TokenV2> {
            val words = ArrayList<String>()
            repeat(5) { words += vocabulary[random.nextInt(vocabulary.size)] }
            repeat(5) { words += typo(vocabulary[random.nextInt(vocabulary.size)].padEnd(5, 'x')) }
            repeat(5) { words += word() }
            val glue = listOf("200", "g", "de", "2", "tazas", "con", "y", ",", "a la plancha", "sin", "azucar", "1,5", "kg", "+", "un")
            val text = (words + glue + words.take(3)).shuffled(random).take(30).joinToString(" ")
            return NormalizerV2.tokenize(text)
        }

        repeat(15) { lex.tag(sentence()) }
        val times = ArrayList<Double>()
        var tokenCount = 0
        repeat(40) {
            val tokens = sentence()
            tokenCount = tokens.size
            val start = System.nanoTime()
            val result = lex.tag(tokens)
            times += (System.nanoTime() - start) / 1e6
            assertEquals(tokens.size, result.sumOf { it.last - it.first + 1 })
        }
        val sorted = times.sorted()
        val median = sorted[sorted.size / 2]
        println("[F2a-perf] 20000 alias, ~$tokenCount tokens, frases con falta nueva: mediana=%.2f ms, max=%.2f ms".format(median, sorted.last()))
        assertTrue("mediana $median ms", median < 50.0)

        // Misma frase otra vez: las correcciones ya están en memoria.
        val again = sentence()
        lex.tag(again)
        val start = System.nanoTime()
        lex.tag(again)
        val repeated = (System.nanoTime() - start) / 1e6
        println("[F2a-perf] frase repetida (correcciones en memoria): %.3f ms".format(repeated))
        assertTrue("repetida $repeated ms", repeated < 50.0)
    }
}
