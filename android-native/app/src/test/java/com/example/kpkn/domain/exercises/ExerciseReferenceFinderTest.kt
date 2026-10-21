package com.example.kpkn.domain.exercises

import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2Resolver
import com.example.kpkn.testsupport.MotCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MOT · E1/E2/E7 (AUD1 §3): el motor único de parecidos frente a la búsqueda del catálogo, con el corpus de 36 nombres de la
 * auditoría, un conjunto de validación y un corpus fresco. Imprime las tablas de medición (`println`) para el informe.
 */
class ExerciseReferenceFinderTest {
    private val catalog = MotCatalog.catalog
    private val finder = ExerciseReferenceFinder.forCatalog(catalog)
    private val resolver = ExerciseCatalogV2Resolver(catalog)

    private class Measure(val top1: Int, val top3: Int, val p3: Int, val n: Int, val misses: List<String>) {
        fun pct(x: Int) = "%.1f %%".format(100.0 * x / n)
        override fun toString() = "top-1 $top1/$n = ${pct(top1)} · top-3 $top3/$n = ${pct(top3)} · P@3 $p3/${3 * n}"
    }

    private fun measureFinder(corpus: List<NameCase>): Measure {
        var top1 = 0
        var top3 = 0
        var p3 = 0
        val misses = mutableListOf<String>()
        corpus.forEach { case ->
            val ids = finder.find(case.name).candidates.map { it.definitionId }
            val ok1 = ids.firstOrNull() in case.accepted
            if (ok1) top1++ else misses += "${case.number}. ${case.name} → ${ids.joinToString()}"
            if (ids.any { it in case.accepted }) top3++
            p3 += ids.count { it in case.accepted }
        }
        return Measure(top1, top3, p3, corpus.size, misses)
    }

    private fun measureSearch(corpus: List<NameCase>): Measure {
        var top1 = 0
        var top3 = 0
        var p3 = 0
        val misses = mutableListOf<String>()
        corpus.forEach { case ->
            val ids = resolver.search(case.name).take(3).map { it.definitionId }
            val ok1 = ids.firstOrNull() in case.accepted
            if (ok1) top1++ else misses += "${case.number}. ${case.name} → ${ids.joinToString()}"
            if (ids.any { it in case.accepted }) top3++
            p3 += ids.count { it in case.accepted }
        }
        return Measure(top1, top3, p3, corpus.size, misses)
    }

    @Test
    fun aud1Corpus_reachesTheCatalogSearch_andBeatsItsFailures() {
        val search = measureSearch(AUD1_CORPUS)
        val engine = measureFinder(AUD1_CORPUS)
        println("MOT-E7 AUD1 36 · búsqueda V2: $search")
        println("MOT-E7 AUD1 36 · motor unificado: $engine")
        engine.misses.forEach { println("MOT-E7   fallo del motor: $it") }
        // Línea base de AUD1 §3.2 sobre este mismo asset: búsqueda 26/36 (72,2 %) y 31/36 (86,1 %).
        assertEquals("la búsqueda V2 debe seguir en 26/36", 26, search.top1)
        assertTrue("el motor debe alcanzar a la búsqueda: $engine vs $search", engine.top1 >= search.top1 && engine.top3 >= search.top3)
        assertTrue("P@1 ≥ 72,2 %: $engine", engine.top1 >= 26)
        assertTrue("acierto@3 ≥ 86,1 %: $engine", engine.top3 >= 31)
    }

    @Test
    fun heldOutAndFreshCorpora_generalizeBeyondTheTuningSet() {
        val held = measureFinder(HELD_OUT_CORPUS)
        val fresh = measureFinder(FRESH_CORPUS)
        println("MOT-E7 validación ${held.n}: $held")
        held.misses.forEach { println("MOT-E7   fallo (validación): $it") }
        println("MOT-E7 fresco ${fresh.n}: $fresh")
        fresh.misses.forEach { println("MOT-E7   fallo (fresco): $it") }
        assertTrue("validación: $held", held.top1.toDouble() / held.n >= 0.85 && held.top3.toDouble() / held.n >= 0.90)
        assertTrue("fresco: $fresh", fresh.top1.toDouble() / fresh.n >= 0.80 && fresh.top3.toDouble() / fresh.n >= 0.90)
    }

    @Test
    fun everyConfirmedReference_isCorrect_onAllCorpora() {
        var confirmed = 0
        var total = 0
        val wrong = mutableListOf<String>()
        (AUD1_CORPUS + HELD_OUT_CORPUS + FRESH_CORPUS).forEach { case ->
            total++
            val result = finder.find(case.name)
            if (result.outcome == ReferenceOutcome.CONFIRMED) {
                confirmed++
                val top = result.confirmed!!
                if (top.definitionId !in case.accepted) wrong += "${case.number}. ${case.name} → ${top.definitionId}"
                assertNotNull("una referencia confirmada trae su configuración exacta", top.configurationId)
                assertTrue(top.satisfiesVariant)
            }
        }
        println("MOT-E7 confirmaciones: $confirmed/$total; incorrectas: $wrong")
        assertTrue("las referencias confirmadas no pueden ser erróneas: $wrong", wrong.isEmpty())
        assertTrue("debe confirmar los nombres claros: $confirmed/$total", confirmed >= total / 2)
    }

    @Test
    fun ambiguousOrUnknownNames_abstain_withAnExplanation() {
        val names = ABSTENTION_CONTROLS + HELD_OUT_ABSTENTIONS + FRESH_ABSTENTIONS +
            listOf("press de piernas con banda", "press raro de una variante inventada", "sentadilla", "curl")
        names.forEach { name ->
            val result = finder.find(name)
            assertTrue("«$name» no debe certificar una referencia: ${result.outcome} ${result.candidates.map { it.definitionId }}", result.confirmed == null)
            assertTrue("«$name» debe explicar la abstención", result.reason.isNotBlank())
        }
    }

    @Test
    fun explicitMaterialInTheName_prevails_andPicksTheExactConfiguration() {
        val low = finder.find("sentadilla barra baja")
        assertEquals("barbell", low.parsed.implement)
        assertEquals(ReferenceOutcome.CONFIRMED, low.outcome)
        assertEquals("low_bar_back_squat__barbell", low.confirmed!!.configurationId)

        val bench = finder.find("bench press dumbbells")
        assertEquals(ReferenceOutcome.CONFIRMED, bench.outcome)
        assertEquals("bench_press__dumbbells", bench.confirmed!!.configurationId)

        val pallof = finder.find("pallof press cable")
        assertEquals("core_press_pallof", pallof.confirmed?.definitionId)

        val trap = finder.find("deadlift trap bar")
        assertEquals("conventional_deadlift", trap.candidates.first().definitionId)
        assertEquals("hex_bar", trap.parsed.implement)
    }

    @Test
    fun aliasesPointAtIdentities_notAtVagueTokens() {
        assertEquals("romanian_deadlift", finder.find("RDL con barra").candidates.first().definitionId)
        assertEquals("deltoides_face_pull", finder.find("facepull polea").candidates.first().definitionId)
        assertEquals("deltoides_face_pull", finder.find("face pull").candidates.first().definitionId)
        assertEquals("triceps_press_frances", finder.find("rompecraneos barra ez").candidates.first().definitionId)
        assertEquals("core_rueda_abdominal", finder.find("ab wheel").candidates.first().definitionId)
        // «chin ups supinas»: el alias fija la identidad Y el agarre supino.
        val chin = finder.find("chin ups supinas")
        assertEquals("pull_up", chin.candidates.first().definitionId)
        assertEquals("supinated", chin.parsed.restrictions["grip_type"])
        assertTrue(chin.candidates.first().configurationId!!.contains("supinated"))
    }

    @Test
    fun hingeAndCurlAreNotConfused_withAnotherMovement() {
        // D2: «peso mueto rumano con mancuernas» tenía como primer parecido un curl Zottman.
        val rdl = finder.find("peso mueto rumano con mancuernas")
        assertEquals("romanian_deadlift", rdl.candidates.first().definitionId)
        assertEquals(1, rdl.parsed.maxTypoDistance)
        // D7: el curl femoral es flexión de rodilla, no de codo.
        val legCurl = finder.find("curl femoral sentado maquina")
        assertEquals("seated_leg_curl", legCurl.candidates.first().definitionId)
        assertFalse(legCurl.candidates.any { it.definitionId.contains("biceps") })
        // D3: «bench press» no cae en un press genérico de empuje vertical.
        assertEquals("bench_press", finder.find("bench press dumbbells").candidates.first().definitionId)
    }

    @Test
    fun aVariantTheCatalogLacks_isNotInvented_itStaysSimilarWithAReason() {
        // Las dominadas existen con peso corporal (agarres y anchos), no en máquina: no se fabrica esa configuración.
        val result = finder.find("dominadas con maquina")
        assertEquals(ReferenceOutcome.SIMILAR, result.outcome)
        val top = result.candidates.first()
        assertEquals("pull_up", top.definitionId)
        assertFalse(top.satisfiesVariant)
        assertNull(top.configurationId)
        assertTrue(result.reason, result.reason.contains("variante"))
        // Lo mismo con un alias: el Pallof solo existe en polea.
        val pallof = finder.find("pallof press con mancuernas")
        assertNull(pallof.confirmed)
        assertEquals("core_press_pallof", pallof.candidates.first().definitionId)
    }

    @Test
    fun distanceTwoTypos_areSuggested_neverCertified() {
        val result = finder.find("curl martiyo mancuernas")
        assertEquals("hammer_curl", result.candidates.first().definitionId)
        assertTrue(result.parsed.maxTypoDistance >= 2)
        assertTrue(result.confirmed == null)
    }

    @Test
    fun customExercises_areCandidatesWithTheSameContract() {
        val own = com.example.kpkn.data.models.ExerciseMuscleInfo(
            id = "custom:11111111-1111-1111-1111-111111111111",
            name = "Remo Yates",
            alias = "yates row, remo yates con barra",
            equipment = "Barra",
            isCustom = true,
        )
        val withOwn = ExerciseReferenceFinder.forCatalog(catalog, listOf(own))
        val result = withOwn.find("remo yates")
        assertEquals(own.id, result.candidates.first().definitionId)
        assertTrue(result.candidates.first().isCustom)
    }
}
