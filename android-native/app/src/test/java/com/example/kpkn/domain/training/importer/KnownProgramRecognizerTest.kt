package com.example.kpkn.domain.training.importer

import com.example.kpkn.data.programs.PersonalizedPlanCatalog
import com.example.kpkn.data.programs.PublicationState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Títulos reales de programas (nombres, no contenido) y los planes de KPKN a los que apuntan. */
class KnownProgramRecognizerTest {

    private fun planOf(title: String): String? = KnownProgramRecognizer.recognize(title, emptyList(), emptyList())?.planId

    @Test
    fun twentyRealProgramTitlesAreRecognizedAsTheirPlan() {
        val expected = listOf(
            "nSuns 5/3/1 LP" to "protocol:nsuns-531-lp-4d",
            "GZCLP" to "protocol:gzclp",
            "Smolov Base" to "protocol:smolov",
            "Smolov Jr" to "protocol:smolov-jr",
            "Candito 6 semanas" to "protocol:candito-6",
            "Sheiko 30" to "protocol:sheiko-29-32",
            "Texas Method 3 días" to "protocol:texas-method-3d",
            "Texas Method 4 days" to "protocol:texas-method-4d",
            "PHUL 4 días" to "original:phul-ms-2021-r1",
            "PHAT 5 días" to "original:phat-biolayne-2016-r1",
            "Reddit PPL 6 días" to "protocol:kpkn-ppl-6",
            "Madcow 5×5" to "protocol:madcow-5x5",
            "Juggernaut 2.0" to "protocol:juggernaut-2",
            "Calgary Barbell 16 semanas" to "protocol:calgary-16",
            "TSA 9 semanas" to "protocol:tsa-9",
            "Cube Method" to "protocol:cube-method",
            "Westside Barbell" to "protocol:westside-conjugate",
            "Coan y Philippi" to "protocol:coan-phillipi-dl",
            "GZCL Jacked & Tan 2.0" to "protocol:gzcl-jt-2",
            "The Rippler" to "protocol:gzcl-rippler",
        )
        assertEquals(20, expected.size)
        expected.forEach { (title, planId) -> assertEquals(title, planId, planOf(title)) }
    }

    @Test
    fun theRestOfThePlansOfTheTableAreRecognizedToo() {
        assertEquals("protocol:gzcl-uhf-9", planOf("UHF 9"))
        assertEquals("protocol:smolov-jr", planOf("Smolov Junior 4 semanas"))
        assertEquals("protocol:texas-method-4d", planOf("Texas Method 4 day"))
        assertEquals("protocol:sheiko-29-32", planOf("Sheiko 29 a 32"))
        assertEquals("adapted:phul-kpkn-r1", planOf("PHUL adaptado 4 días"))
        assertEquals("adapted:phat-kpkn-r1", planOf("PHAT adaptado"))
        assertEquals("protocol:kpkn-ppl-6", planOf("Metallicadpa PPL"))
        assertEquals("protocol:calgary-16", planOf("Calgary 16"))
        assertEquals("protocol:gzcl-jt-2", planOf("Jacked and Tan 2"))
        assertEquals("protocol:coan-phillipi-dl", planOf("Coan Philippi"))
    }

    @Test
    fun genericTitlesAreNotRecognized() {
        listOf("Mi rutina", "Push Pull Legs", "Plantilla semanal", "Full body 3 días", "PPL").forEach { title ->
            assertNull(title, planOf(title))
        }
    }

    @Test
    fun theMostSpecificPhraseWinsWithinALevel() {
        assertEquals("protocol:smolov-jr", planOf("Smolov Jr"))
        assertEquals("adapted:phul-kpkn-r1", planOf("PHUL adaptado"))
        assertEquals("protocol:texas-method-4d", planOf("Texas Method 4 días"))
    }

    @Test
    fun theTitleBeatsTheSheetNamesAndTheSheetNamesBeatTheTexts() {
        assertEquals(
            "protocol:texas-method-4d",
            KnownProgramRecognizer.recognize("Texas Method 4 días", listOf("Smolov"), listOf("Sheiko 30"))?.planId,
        )
        assertEquals("protocol:smolov", KnownProgramRecognizer.recognize("Programa", listOf("Smolov"), listOf("Sheiko 30"))?.planId)
        assertEquals("protocol:sheiko-29-32", KnownProgramRecognizer.recognize("", emptyList(), listOf("Sheiko 30"))?.planId)
    }

    @Test
    fun twoDifferentPlansOnTheSameLevelGiveNothing() {
        assertNull(KnownProgramRecognizer.recognize("Smolov y Sheiko 30", emptyList(), emptyList()))
        // El nivel de las hojas decide aunque los textos tengan otro plan.
        assertNull(KnownProgramRecognizer.recognize("", listOf("Smolov", "Sheiko 30"), listOf("GZCLP")))
    }

    @Test
    fun aFileAndAPastedTextAreReadThroughTheirCells() {
        val sheets = listOf(ImportSheet("Hoja 1", listOf(listOf(""), listOf("GZCLP 4x6 Pierna"))))

        assertEquals("protocol:gzclp", KnownProgramRecognizer.recognizeSheets("Programa", sheets)?.planId)
        assertEquals("protocol:gzclp", KnownProgramRecognizer.recognizeText("Programa de fuerza\nGZCLP")?.planId)
        assertNull(KnownProgramRecognizer.recognizeText("Día 1 — Pierna\nSentadilla 4x6"))
    }

    @Test
    fun theTableOnlyPointsAtPlansThatExistAndAreOffered() {
        KnownProgramRecognizer.ALIASES.forEach { (phrase, planId) ->
            val entry = checkNotNull(PersonalizedPlanCatalog.find(planId)) { "«$phrase» apunta a un plan que no existe: $planId" }
            assertTrue("«$phrase» apunta a un plan que no se ofrece: $planId", entry.listed)
            assertEquals("«$phrase» → $planId", PublicationState.PUBLISHED, entry.publication)
        }
    }
}
