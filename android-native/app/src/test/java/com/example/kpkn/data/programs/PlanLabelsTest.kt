package com.example.kpkn.data.programs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * C.P1 · Etiquetas derivadas de [PlanLabels]: nivel, duración, frecuencia,
 * subtítulo, línea de metadatos y procedencia. Funciones puras (JVM, sin
 * Robolectric). Las que usan entradas reales leen el catálogo de producción.
 */
class PlanLabelsTest {

    /** Cantidad uno con un sustantivo en plural: justo lo que la concordancia evita. */
    private val oneWithPlural = Regex("""\b1 (días|semanas)\b""")

    private fun entry(id: String): CatalogEntry =
        requireNotNull(PersonalizedPlanCatalog.find(id)) { "Falta la entrada «$id» en el catálogo de planes" }

    // ─── Nivel ───────────────────────────────────────────────────────────────

    @Test
    fun catalogLevelLabelsAreInSpanish() {
        assertEquals("Principiante", CatalogLevel.BEGINNER.label)
        assertEquals("Intermedio", CatalogLevel.INTERMEDIATE.label)
        assertEquals("Avanzado", CatalogLevel.ADVANCED.label)
    }

    @Test
    fun levelLabelNamesOneLevelTwoContiguousLevelsOrAll() {
        assertEquals("Principiante", PlanLabels.levelLabel(setOf(CatalogLevel.BEGINNER)))
        assertEquals("Intermedio", PlanLabels.levelLabel(setOf(CatalogLevel.INTERMEDIATE)))
        assertEquals("Avanzado", PlanLabels.levelLabel(setOf(CatalogLevel.ADVANCED)))
        assertEquals("Todos los niveles", PlanLabels.levelLabel(CatalogLevel.entries.toSet()))
        // El orden en que se declara el conjunto no cambia la etiqueta.
        assertEquals(
            "Principiante a intermedio",
            PlanLabels.levelLabel(setOf(CatalogLevel.INTERMEDIATE, CatalogLevel.BEGINNER)),
        )
        assertEquals(
            "Intermedio a avanzado",
            PlanLabels.levelLabel(setOf(CatalogLevel.ADVANCED, CatalogLevel.INTERMEDIATE)),
        )
    }

    @Test
    fun levelLabelNeverShowsEnumNamesEvenForANonContiguousPair() {
        val label = PlanLabels.levelLabel(setOf(CatalogLevel.BEGINNER, CatalogLevel.ADVANCED))
        assertEquals("Principiante y avanzado", label)
        assertFalse(label.contains("BEGINNER") || label.contains("ADVANCED"))
    }

    // ─── Duración ────────────────────────────────────────────────────────────

    @Test
    fun durationLabelNamesTheRepeatingWeekTheRepeatingCycleAndTheFiniteCycle() {
        assertEquals("Semana que se repite", PlanLabels.durationLabel(CatalogDuration.REPEATING_WEEK, 1))
        assertEquals("Ciclo de 4 semanas que se repite", PlanLabels.durationLabel(CatalogDuration.REPEATING_CYCLE, 4))
        assertEquals("Ciclo de 12 semanas que se repite", PlanLabels.durationLabel(CatalogDuration.REPEATING_CYCLE, 12))
        assertEquals("12 semanas", PlanLabels.durationLabel(CatalogDuration.FINITE_CYCLE, 12))
        assertEquals("6 semanas", PlanLabels.durationLabel(CatalogDuration.FINITE_CYCLE, 6))
    }

    @Test
    fun durationLabelAgreesInNumberForASingleWeek() {
        assertEquals("1 semana", PlanLabels.durationLabel(CatalogDuration.FINITE_CYCLE, 1))
        assertEquals("Ciclo de 1 semana que se repite", PlanLabels.durationLabel(CatalogDuration.REPEATING_CYCLE, 1))
        CatalogDuration.entries.forEach { duration ->
            val label = PlanLabels.durationLabel(duration, 1)
            assertFalse("«$label» mezcla 1 con un plural", oneWithPlural.containsMatchIn(label))
        }
    }

    // ─── Frecuencia ──────────────────────────────────────────────────────────

    @Test
    fun frequencyLabelUsesTheSingularForOneDay() {
        assertEquals("1 día por semana", PlanLabels.frequencyLabel(1..1))
        assertEquals("4 días por semana", PlanLabels.frequencyLabel(4..4))
        assertEquals("6 días por semana", PlanLabels.frequencyLabel(6..6))
    }

    @Test
    fun frequencyLabelWritesRangesWithAnEnDashAndAlwaysInThePlural() {
        // Raya (U+2013), no guion.
        assertEquals("2–3 días por semana", PlanLabels.frequencyLabel(2..3))
        assertEquals("1–6 días por semana", PlanLabels.frequencyLabel(1..6))
        assertTrue(PlanLabels.frequencyLabel(3..6).contains("–"))
        assertFalse(PlanLabels.frequencyLabel(3..6).contains("-"))
    }

    // ─── Subtítulo, línea de metadatos y procedencia sobre entradas reales ────

    @Test
    fun subtitleIsDurationDotLevelWithoutDays() {
        assertEquals("Semana que se repite · Todos los niveles", PlanLabels.subtitle(entry("native:one-day")))
        assertEquals("6 semanas · Todos los niveles", PlanLabels.subtitle(entry("native:muscle-foundation-v2")))
        assertEquals("12 semanas · Principiante", PlanLabels.subtitle(entry("template:power-12-3")))
        assertEquals(
            "Ciclo de 4 semanas que se repite · Intermedio",
            PlanLabels.subtitle(entry("protocol:texas-method-3d")),
        )
        assertEquals("16 semanas · Avanzado", PlanLabels.subtitle(entry("protocol:sheiko-29-32")))
    }

    @Test
    fun metaLineIsFrequencyDurationLevel() {
        assertEquals(
            "1 día por semana · Semana que se repite · Todos los niveles",
            PlanLabels.metaLine(entry("native:one-day")),
        )
        assertEquals(
            "2–3 días por semana · Semana que se repite · Todos los niveles",
            PlanLabels.metaLine(entry("native:full-body")),
        )
        assertEquals(
            "3 días por semana · Ciclo de 4 semanas que se repite · Intermedio",
            PlanLabels.metaLine(entry("protocol:texas-method-3d")),
        )
    }

    @Test
    fun noEntryEverShowsOneWithAPluralNounInItsLabels() {
        PersonalizedPlanCatalog.entries().forEach { entry ->
            val subtitle = PlanLabels.subtitle(entry)
            val meta = PlanLabels.metaLine(entry)
            assertFalse("${entry.id}: «$subtitle»", oneWithPlural.containsMatchIn(subtitle))
            assertFalse("${entry.id}: «$meta»", oneWithPlural.containsMatchIn(meta))
            // Los días van en el motivo del wizard, nunca en el subtítulo.
            assertFalse("${entry.id}: el subtítulo «$subtitle» cita días", subtitle.contains("día"))
            assertEquals("${entry.id}: separadores del subtítulo", 1, subtitle.split(PlanLabels.SEPARATOR).size - 1)
            assertEquals("${entry.id}: separadores de la línea de metadatos", 2, meta.split(PlanLabels.SEPARATOR).size - 1)
        }
    }

    @Test
    fun provenanceLabelFollowsTheOriginOfEachPlan() {
        assertEquals("Plan KPKN", PlanLabels.provenanceLabel(entry("native:muscle-foundation-v2")))
        assertEquals("Plan KPKN", PlanLabels.provenanceLabel(entry("template:body-12-3")))
        assertEquals("Plan KPKN", PlanLabels.provenanceLabel(entry("protocol:kpkn-ppl-6")))
        assertEquals(
            "Original fiel · Brandon Campbell",
            PlanLabels.provenanceLabel(entry("original:phul-ms-2021-r1")),
        )
        assertEquals(
            "Adaptación KPKN · desde Layne Norton",
            PlanLabels.provenanceLabel(entry("adapted:phat-kpkn-r1")),
        )
        assertEquals("Versión anterior", PlanLabels.provenanceLabel(entry("protocol:phul-verified")))

        val texas = entry("protocol:texas-method-3d")
        assertEquals(PlanOrigin.KPKN_VERSION, texas.origin)
        assertEquals(
            "Versión KPKN del método de ${texas.sourceAuthor}",
            PlanLabels.provenanceLabel(texas),
        )
    }

    @Test
    fun everyEntryHasAProvenanceLabelWithoutEnumNames() {
        PersonalizedPlanCatalog.entries().forEach { entry ->
            val label = PlanLabels.provenanceLabel(entry)
            assertTrue("${entry.id}: etiqueta vacía", label.isNotBlank())
            assertFalse("${entry.id}: «$label» filtra un nombre de enum", label.contains("_VERSION") || label == entry.origin.name)
        }
    }

    // ─── Entradas construidas a mano (fixtures de otros tests) ────────────────

    @Test
    fun aHandBuiltEntryGetsADerivedEditorialWithoutChangingItsConstructor() {
        val handBuilt = CatalogEntry(
            id = "native:test",
            source = CatalogSource.NATIVE,
            sourceId = "test",
            title = "Plan",
            technicalSubtitle = "Semana cíclica",
            description = "Descripción",
            requiredEquipment = setOf("general_gym"),
            supportedFrequencies = 2..6,
            level = CatalogLevel.INTERMEDIATE,
            duration = CatalogDuration.REPEATING_WEEK,
            supportedFocuses = setOf(TrainingFocus.FULL_BODY),
            adaptation = AdaptationPolicy.CURATED_WEEKLY,
            publication = PublicationState.PUBLISHED,
        )
        assertEquals("Plan", handBuilt.displayName)
        assertEquals("Descripción", handBuilt.summary)
        assertEquals(setOf(CatalogLevel.INTERMEDIATE), handBuilt.levels)
        assertEquals(PlanOrigin.KPKN, handBuilt.origin)
        assertEquals(PlanKind.PLAN, handBuilt.kind)
        assertTrue(handBuilt.listed)
        assertTrue(handBuilt.terms.isEmpty())
        assertEquals("Semana que se repite · Intermedio", PlanLabels.subtitle(handBuilt))
        assertEquals(
            "2–6 días por semana · Semana que se repite · Intermedio",
            PlanLabels.metaLine(handBuilt),
        )
    }
}
