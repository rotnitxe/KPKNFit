package com.example.kpkn.screens.programdetail

import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.PowerliftingProfile
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramMode
import com.example.kpkn.data.programs.CatalogEntry
import com.example.kpkn.data.programs.CatalogSource
import com.example.kpkn.data.programs.PersonalizedPlanCatalog
import com.example.kpkn.data.programs.PlanLabels
import com.example.kpkn.data.protocols.PROTOCOL_LIBRARY
import com.example.kpkn.data.protocols.PlanProvenance
import com.example.kpkn.data.protocols.PlanProvenanceClass
import com.example.kpkn.data.protocols.definitions.AuthoredPhulPhatRecipes
import com.example.kpkn.data.protocols.isVisibleForApplication
import com.example.kpkn.screens.onboarding.planReviewValue
import com.example.kpkn.screens.programdetail.components.focusModeLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * C.P7 · Cómo se ve un plan ya elegido o activado: la tarjeta «Plan y procedencia» del detalle del programa
 * ([buildProgramPlanDisplaySummary]), su chip ámbar ([planChipLabel]), la línea de máximos de entrenamiento
 * ([trainingMaxLine]), el vocabulario del enfoque del banner ([focusModeLabel]) y la fila «Plan» de la revisión
 * del asistente ([planReviewValue]). Todo son funciones puras: JVM, sin Robolectric ni Compose, sobre el
 * catálogo de producción.
 */
class ProgramPlanDisplaySummaryTest {

    /** Un prefijo de id interno seguido de su nombre (`protocol:texas-method-3d`): nunca debe llegar a la pantalla. */
    private val rawIdPrefix = Regex("""\b(protocol|native|template|original|adapted):[a-z0-9]""")

    private fun entry(id: String): CatalogEntry =
        requireNotNull(PersonalizedPlanCatalog.find(id)) { "Falta la entrada «$id» en el catálogo de planes" }

    private fun program(
        name: String = "Mi programa",
        description: String? = null,
        structureTemplateId: String? = null,
        sourceProtocolId: String? = null,
        planProvenance: PlanProvenance? = null,
        author: String? = null,
        macrocycles: List<Macrocycle> = emptyList(),
    ) = Program(
        id = "programa-de-prueba",
        name = name,
        description = description,
        structureTemplateId = structureTemplateId,
        sourceProtocolId = sourceProtocolId,
        planProvenance = planProvenance,
        author = author,
        macrocycles = macrocycles,
    )

    /** Pictogramas: pares sustitutos, selector de variación (U+FE0F), cierre de keycap (U+20E3) o símbolo gráfico. */
    private fun hasPictogram(text: String): Boolean = text.any { ch ->
        Character.isSurrogate(ch) || ch == '️' || ch == '⃣' ||
            Character.getType(ch) == Character.OTHER_SYMBOL.toInt()
    }

    // ─── (a) nativo propio ───────────────────────────────────────────────────

    @Test
    fun ownNativeProgramShowsTheEditorialNameKpknProvenanceAndOnlyTheNotesBelowTheSummary() {
        val muscle = entry("native:muscle-foundation-v2")
        val summary = buildProgramPlanDisplaySummary(
            program(
                name = "Mi hipertrofia de otoño",
                description = "${muscle.summary}\nNota 1\nNota 2",
                structureTemplateId = muscle.id,
                macrocycles = listOf(
                    Macrocycle(
                        id = "macro",
                        name = "Mi plan",
                        blocks = listOf(Block(id = "bloque", name = "Base", prescriptionOrigin = "KPKN_NATIVE_CURATED")),
                    ),
                ),
            ),
        )

        assertEquals(muscle.id, summary.catalogEntry?.id)
        assertEquals(muscle.displayName, summary.planName)
        assertNotEquals("el nombre del plan no es el que el usuario le puso al programa", "Mi hipertrofia de otoño", summary.planName)
        assertEquals("Plan KPKN", summary.provenanceLabel)
        assertEquals(muscle.attributionLine, summary.sourceLine)
        assertEquals(muscle.summary, summary.summary)
        assertEquals(listOf("Nota 1", "Nota 2"), summary.planNotes)
    }

    // ─── (b) protocolo ───────────────────────────────────────────────────────

    @Test
    fun protocolProgramShowsTheCatalogNameItsProvenanceAndTheAttributionLine() {
        val madcow = entry("protocol:madcow-5x5")
        val summary = buildProgramPlanDisplaySummary(
            program(name = "Madcow de Juan", sourceProtocolId = "madcow-5x5", structureTemplateId = "madcow-5x5"),
        )

        assertEquals("Madcow 5×5", summary.planName)
        assertEquals(madcow.displayName, summary.planName)
        assertEquals(PlanLabels.provenanceLabel(madcow), summary.provenanceLabel)
        assertTrue("«${summary.provenanceLabel}»", summary.provenanceLabel.startsWith("Versión KPKN"))
        assertEquals(madcow.attributionLine, summary.sourceLine)
        assertEquals(madcow.summary, summary.summary)
        assertTrue(summary.planNotes.isEmpty())
    }

    // ─── (c) autorado ────────────────────────────────────────────────────────

    @Test
    fun authoredProgramShowsOriginalFaithfulAndTheAttributionOfItsSource() {
        val phul = entry(AuthoredPhulPhatRecipes.PHUL_ORIGINAL_ID)
        val summary = buildProgramPlanDisplaySummary(
            program(planProvenance = PlanProvenance(planId = phul.id, category = PlanProvenanceClass.ORIGINAL)),
        )

        assertEquals(phul.displayName, summary.planName)
        assertTrue("«${summary.provenanceLabel}»", summary.provenanceLabel.startsWith("Original fiel"))
        assertEquals(phul.attributionLine, summary.sourceLine)
        assertNotNull(summary.sourceLine)
    }

    @Test
    fun authoredAdaptationShowsAdaptacionKpkn() {
        val adapted = entry(AuthoredPhulPhatRecipes.PHAT_ADAPTED_ID)
        val summary = buildProgramPlanDisplaySummary(program(structureTemplateId = adapted.id))

        assertEquals(adapted.displayName, summary.planName)
        assertTrue("«${summary.provenanceLabel}»", summary.provenanceLabel.startsWith("Adaptación KPKN"))
        assertEquals(adapted.attributionLine, summary.sourceLine)
    }

    // ─── (d) a mano ──────────────────────────────────────────────────────────

    @Test
    fun handMadeProgramIsCalledSoAndShowsNoIds() {
        val summary = buildProgramPlanDisplaySummary(program(name = "Mi rutina"))

        assertNull(summary.catalogEntry)
        assertEquals("Mi rutina", summary.planName)
        assertEquals("Programa creado a mano", summary.provenanceLabel)
        assertNull(summary.sourceLine)
        assertNull(summary.summary)
        assertTrue(summary.planNotes.isEmpty())
        assertNotEquals("Procedencia no declarada", summary.provenanceLabel)
    }

    @Test
    fun aProgramWithOnlyAnAuthorIsSavedAndNamesTheAuthor() {
        val summary = buildProgramPlanDisplaySummary(program(name = "Rutina de Ana", author = "Ana Pérez"))

        assertEquals("Programa guardado", summary.provenanceLabel)
        assertEquals("Autor · Ana Pérez", summary.sourceLine)
    }

    // ─── (e) método que ya no existe ─────────────────────────────────────────

    @Test
    fun anUnknownProtocolIdIsAnEarlierVersionOfAMethodAndTheIdNeverShows() {
        val unknownId = "texas-method-3d-retirado"
        val summary = buildProgramPlanDisplaySummary(program(name = "Texas viejo", sourceProtocolId = unknownId))

        assertNull(summary.catalogEntry)
        assertEquals("Texas viejo", summary.planName)
        assertEquals("Versión anterior de un método", summary.provenanceLabel)
        assertFalse("«${summary.sourceLine}» enseña el id", summary.sourceLine.orEmpty().contains(unknownId))
        assertNull(summary.sourceLine)
    }

    @Test
    fun anUnknownIdWithAnAuthorStillSaysEarlierVersionAndHidesTheId() {
        val unknownId = "metodo-ya-retirado"
        val summary = buildProgramPlanDisplaySummary(program(sourceProtocolId = unknownId, author = "Ana Pérez"))

        assertEquals("Versión anterior de un método", summary.provenanceLabel)
        assertEquals("Autor · Ana Pérez", summary.sourceLine)
        assertFalse(summary.sourceLine.orEmpty().contains(unknownId))
    }

    @Test
    fun anUnknownStructureIdAloneIsAlsoAnEarlierVersionOfAMethod() {
        val summary = buildProgramPlanDisplaySummary(program(structureTemplateId = "native:plan-retirado"))

        assertNull(summary.catalogEntry)
        assertEquals("Versión anterior de un método", summary.provenanceLabel)
        assertNull(summary.sourceLine)
    }

    @Test
    fun withoutAnEntryTheDeclaredProvenanceClassDecidesTheLabelAndTheSourceLine() {
        fun summaryOf(category: PlanProvenanceClass) = buildProgramPlanDisplaySummary(
            program(
                planProvenance = PlanProvenance(
                    category = category,
                    sourceTitle = "Muscle & Strength",
                    sourceAuthor = "Brandon Campbell",
                    sourceEdition = "M&S 2021",
                ),
            ),
        )

        assertEquals("Original fiel", summaryOf(PlanProvenanceClass.ORIGINAL).provenanceLabel)
        assertEquals("Adaptación KPKN", summaryOf(PlanProvenanceClass.ADAPTED).provenanceLabel)
        assertEquals("Plan KPKN", summaryOf(PlanProvenanceClass.KPKN).provenanceLabel)
        // LEGACY sin ningún id ni autor: lo único que se sabe es que se creó a mano.
        assertEquals("Programa creado a mano", summaryOf(PlanProvenanceClass.LEGACY).provenanceLabel)
        assertEquals(
            "Muscle & Strength · Brandon Campbell · M&S 2021",
            summaryOf(PlanProvenanceClass.ORIGINAL).sourceLine,
        )
    }

    // ─── (f) las 55 entradas ─────────────────────────────────────────────────

    /** Programas sintéticos con las referencias que los motores guardan de verdad para [entry]. */
    private fun syntheticProgramsFor(entry: CatalogEntry): List<Program> = when {
        entry.authoredSource != null -> listOf(
            program(planProvenance = PlanProvenance(planId = entry.id)),
            program(structureTemplateId = entry.id),
        )
        entry.source == CatalogSource.NATIVE -> listOf(program(structureTemplateId = entry.id))
        entry.source == CatalogSource.TEMPLATE -> listOf(
            program(structureTemplateId = entry.id),
            program(structureTemplateId = entry.sourceId, sourceProtocolId = entry.sourceId),
        )
        else -> listOf(
            program(sourceProtocolId = entry.sourceId),
            program(structureTemplateId = entry.sourceId, sourceProtocolId = entry.sourceId),
        )
    }

    @Test
    fun everyCatalogEntryShowsItsEditorialNameAndNeverAnUndeclaredProvenanceOrARawId() {
        val entries = PersonalizedPlanCatalog.entries()
        assertTrue("el catálogo debe traer las 55 entradas: ${entries.size}", entries.size >= 55)
        entries.forEach { entry ->
            syntheticProgramsFor(entry).forEach { synthetic ->
                val summary = buildProgramPlanDisplaySummary(synthetic)
                assertEquals("${entry.id}: la entrada resuelta", entry.id, summary.catalogEntry?.id)
                assertEquals("${entry.id}: nombre del plan", entry.displayName, summary.planName)
                assertEquals("${entry.id}: procedencia", PlanLabels.provenanceLabel(entry), summary.provenanceLabel)
                assertEquals("${entry.id}: fuente", entry.attributionLine, summary.sourceLine)
                assertEquals("${entry.id}: qué haces", entry.summary, summary.summary)
                listOf(summary.planName, summary.provenanceLabel, summary.sourceLine.orEmpty()).forEach { text ->
                    assertFalse("${entry.id}: «$text» dice «Procedencia no declarada»", text.contains("Procedencia no declarada"))
                    assertFalse("${entry.id}: «$text» enseña un id interno", rawIdPrefix.containsMatchIn(text))
                    assertFalse("${entry.id}: «$text» contiene el id completo", text.contains(entry.id))
                }
            }
        }
    }

    // ─── Notas del plan ──────────────────────────────────────────────────────

    @Test
    fun planNotesSkipTheSummaryTheInheritedDescriptionBlankLinesAndRepeats() {
        val texas = entry("protocol:texas-method-3d")
        val notes = planNotesOf(
            description = "${texas.summary}\n\n  Nota A  \nNota B\nNota A\n${texas.summary}\n   \nNota C",
            entry = texas,
        )

        assertEquals(listOf("Nota A", "Nota B", "Nota C"), notes)
    }

    @Test
    fun planNotesKeepTheOrderAndDoNotSplitASummaryThatSpansSeveralLines() {
        val multiLineSummary = "Primera frase del resumen.\nSegunda frase en otra línea."
        val base = entry("native:muscle-foundation-v2")
        val withMultiLineSummary = base.copy(editorial = base.editorial.copy(summary = multiLineSummary))

        val notes = planNotesOf("$multiLineSummary\nNota uno\nNota dos", withMultiLineSummary)

        assertEquals(listOf("Nota uno", "Nota dos"), notes)
    }

    @Test
    fun aDescriptionWrittenByAnEarlierVersionOfTheEntryIsStillTheDescriptionNotANote() {
        val muscle = entry("native:muscle-foundation-v2")
        val notes = planNotesOf("Descripción de una versión anterior del plan.\nNota 1\nNota 2", muscle)

        assertEquals(listOf("Nota 1", "Nota 2"), notes)
    }

    @Test
    fun withoutAnEntryTheFirstLineIsTheDescriptionAndTheRestAreNotes() {
        assertEquals(listOf("Nota suelta", "Otra nota"), planNotesOf("Mi rutina de verano\nNota suelta\nOtra nota", null))
        assertTrue(planNotesOf("Solo una descripción", null).isEmpty())
        assertTrue(planNotesOf(null, null).isEmpty())
        assertTrue(planNotesOf("   \n  ", null).isEmpty())
    }

    @Test
    fun theSummaryWithoutNotesLeavesNoNotes() {
        val muscle = entry("native:muscle-foundation-v2")

        assertTrue(planNotesOf(muscle.summary, muscle).isEmpty())
        assertTrue(planNotesOf("${muscle.summary}\n", muscle).isEmpty())
    }

    // ─── Chip ámbar ──────────────────────────────────────────────────────────

    @Test
    fun theChipShowsTheShortNameWithoutDaysOrEmoji() {
        assertEquals("Texas Method", planChipLabel(program(sourceProtocolId = "texas-method-3d")))
        assertEquals("5/3/1 Boring But Big", planChipLabel(program(sourceProtocolId = "wendler-531-bbb")))
        assertEquals(entry("native:muscle-foundation-v2").shortName, planChipLabel(program(structureTemplateId = "native:muscle-foundation-v2")))
    }

    @Test
    fun theChipOfEveryEntryIsItsShortNameAndHasNoPictograms() {
        PersonalizedPlanCatalog.entries().forEach { entry ->
            syntheticProgramsFor(entry).forEach { synthetic ->
                val chip = planChipLabel(synthetic)
                assertEquals("${entry.id}: chip", entry.shortName, chip)
                assertFalse("${entry.id}: el chip «$chip» lleva un pictograma", hasPictogram(chip.orEmpty()))
                assertFalse("${entry.id}: el chip «$chip» enseña los días", chip.orEmpty().contains("días"))
            }
        }
    }

    @Test
    fun theChipOfAHiddenProtocolIsItsNameWithoutTheEmoji() {
        val hidden = PROTOCOL_LIBRARY.first { !it.isVisibleForApplication }
        val hiddenProgram = program(sourceProtocolId = hidden.id)
        assertNull("el protocolo oculto no debe tener entrada en el catálogo", PersonalizedPlanCatalog.findForProgram(hiddenProgram))

        val chip = planChipLabel(hiddenProgram)

        assertEquals(hidden.name, chip)
        assertFalse(hasPictogram(chip.orEmpty()))
        assertTrue(hidden.emoji.isNotBlank())
        assertFalse(chip.orEmpty().contains(hidden.emoji))
    }

    @Test
    fun theChipIsAbsentWhenTheProgramComesFromNoMethod() {
        assertNull(planChipLabel(program()))
        assertNull(planChipLabel(program(sourceProtocolId = "metodo-ya-retirado")))
    }

    // ─── (g) línea de máximos de entrenamiento ───────────────────────────────

    @Test
    fun trainingMaxLineUsesFullLiftNamesAndTheUnit() {
        val profile = PowerliftingProfile(squatTM = 180.0, benchTM = 120.0, deadliftTM = 220.0)

        assertEquals("TM: sentadilla 180 · banca 120 · peso muerto 220 kg", trainingMaxLine(profile))
    }

    @Test
    fun trainingMaxLineKeepsDecimalsWithACommaAndDropsUselessZeros() {
        val profile = PowerliftingProfile(squatTM = 182.0, benchTM = 110.5, deadliftTM = 220.25)

        assertEquals("TM: sentadilla 182 · banca 110,5 · peso muerto 220,3 kg", trainingMaxLine(profile))
        assertFalse(trainingMaxLine(profile).contains('.'))
    }

    @Test
    fun trainingMaxLineWritesADashForAMissingValue() {
        assertEquals(
            "TM: sentadilla 180 · banca — · peso muerto 220 kg",
            trainingMaxLine(PowerliftingProfile(squatTM = 180.0, benchTM = null, deadliftTM = 220.0)),
        )
        // Sin ningún dato no hay unidad que acompañar.
        assertEquals("TM: sentadilla — · banca — · peso muerto —", trainingMaxLine(null))
        assertEquals("TM: sentadilla — · banca — · peso muerto —", trainingMaxLine(PowerliftingProfile()))
        assertEquals(
            "TM: sentadilla — · banca — · peso muerto —",
            trainingMaxLine(PowerliftingProfile(squatTM = Double.NaN, benchTM = Double.POSITIVE_INFINITY)),
        )
    }

    @Test
    fun trainingMaxLineNeverUsesTheOldAbbreviations() {
        val line = trainingMaxLine(PowerliftingProfile(squatTM = 100.0, benchTM = 80.0, deadliftTM = 120.0))

        assertFalse(line.contains("SQ"))
        assertFalse(line.contains("BP"))
        assertFalse(line.contains("DL"))
    }

    // ─── Enfoque del banner ──────────────────────────────────────────────────

    @Test
    fun theBannerFocusUsesTheWizardVocabulary() {
        assertEquals("Fuerza", focusModeLabel(ProgramMode.POWERLIFTING))
        assertEquals("Fuerza y músculo", focusModeLabel(ProgramMode.POWERBUILDING))
        assertEquals("Músculo", focusModeLabel(ProgramMode.HYPERTROPHY))
        val labels = ProgramMode.entries.map(::focusModeLabel)
        assertEquals("cada enfoque tiene su propia etiqueta", labels.size, labels.toSet().size)
        listOf("Powerlifting", "Powerbuilding", "Hipertrofia").forEach { old ->
            assertFalse("«$old» ya no es un enfoque que se diga", old in labels)
        }
    }

    // ─── Fila «Plan» de la revisión ──────────────────────────────────────────

    @Test
    fun theReviewPlanRowShowsTheEditorialNameOfTheChosenPlan() {
        val muscle = entry("native:muscle-foundation-v2")

        // El programa de la vista previa se llama «Plan de Ana»: no es lo que se enseña.
        assertEquals(muscle.displayName, planReviewValue(muscle, "Plan de Ana"))
        assertEquals("Madcow 5×5", planReviewValue(entry("protocol:madcow-5x5"), "Plan de Ana"))
    }

    @Test
    fun theReviewPlanRowKeepsTheProgramNameWithoutACatalogPlan() {
        assertEquals("Mi rutina", planReviewValue(null, "Mi rutina"))
        assertNull(planReviewValue(null, "  "))
        assertNull(planReviewValue(null, ""))
    }
}
