package com.example.kpkn.screens.programs

import com.example.kpkn.data.programs.CatalogEntry
import com.example.kpkn.data.programs.PersonalizedPlanCatalog
import com.example.kpkn.data.protocols.AuthoredSetRange
import com.example.kpkn.data.protocols.SetRecipe
import com.example.kpkn.data.protocols.definitions.AuthoredPhulPhatRecipes
import com.example.kpkn.domain.exercises.catalogv2.CatalogDisplayNames
import com.example.kpkn.domain.onboarding.TrainingGoalProfile
import com.example.kpkn.domain.training.CatalogCompositionTestSupport
import com.example.kpkn.screens.onboarding.design.entreno.plan.PlanFicha
import com.example.kpkn.screens.onboarding.design.entreno.plan.PlanFichaCopy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Arreglos v1 · P3: de [PlanInfoModel] (la hoja de texto «Cómo funciona») a la ficha del asistente del plan del catálogo
 * ([PlanInfoFicha]). JVM puro sobre el catálogo de producción: portada, chips, ejercicios principales con la imagen de KPKN (o sin
 * miniatura), «Tu semana» de lunes a domingo sin perder ningún ejercicio de la hoja, el pie y las secciones que desaparecen sin datos.
 */
class PlanInfoFichaTest {

    @Before
    fun installCatalogMetadata() {
        CatalogCompositionTestSupport.install()
    }

    private val nameIndex: Map<String, String> by lazy {
        CatalogDisplayNames.buildDisplayNameIndex(CatalogCompositionTestSupport.catalog)
            .mapKeys { (key, _) -> key.trim().lowercase() }
    }

    private fun realNames(configurationId: String): String? = nameIndex[configurationId.trim().lowercase()]

    private fun realEquipment(configurationId: String): String? =
        CatalogCompositionTestSupport.metadata.metadata(configurationId)?.equipmentId

    private fun entry(id: String): CatalogEntry = PersonalizedPlanCatalog.find(id) ?: error("Falta la entrada del catálogo «$id»")

    private fun info(entry: CatalogEntry, mode: PlanInfoMode = PlanInfoMode.LIBRARY): PlanInfoModel =
        PlanInfoModelBuilder.build(entry, mode, names = ::realNames, equipmentOf = ::realEquipment)

    private fun ficha(
        entry: CatalogEntry,
        mode: PlanInfoMode = PlanInfoMode.LIBRARY,
        imageOf: (String) -> Int? = { null },
    ): Pair<PlanInfoModel, PlanFicha> {
        val model = info(entry, mode)
        return model to PlanInfoFicha.build(entry, model, names = ::realNames, imageOf = imageOf)
    }

    /** Todo el texto que la ficha enseña. */
    private fun texts(ficha: PlanFicha): List<String> = buildList {
        val card = ficha.detail.card
        addAll(listOf(card.title, card.kicker, card.daysLabel, card.minutesLabel, card.levelLabel, ficha.detail.description))
        addAll(ficha.content.chips)
        ficha.content.exercises.forEach { add(it.name); it.detail?.let(::add) }
        ficha.detail.blocks.forEach { addAll(listOf(it.label, it.weeksLabel, it.detail)) }
        ficha.content.days.forEach { day ->
            add(day.title)
            day.subtitle?.let(::add)
            day.lines.forEach { addAll(listOf(it.name, it.prescription)) }
        }
        addAll(ficha.detail.notes)
        ficha.detail.attribution?.let(::add)
    }

    private val phat by lazy { entry(AuthoredPhulPhatRecipes.PHAT_ORIGINAL_ID) }

    // ─── Portada y cabecera ──────────────────────────────────────────────────

    @Test
    fun theCoverSaysCatalogPlanTheNameDaysMinutesAndLevel() {
        val (model, ficha) = ficha(phat)
        val card = ficha.detail.card
        assertEquals(PlanFichaCopy.EYEBROW_CATALOG, card.kicker)
        assertEquals("Plan del catálogo", card.kicker)
        assertEquals(model.displayName, card.title)
        assertEquals(phat.id, card.id)
        val trainingDays = ficha.content.days.count { !it.rest }
        assertEquals(if (trainingDays == 1) "1 día" else "$trainingDays días", card.daysLabel)
        assertTrue("«${card.minutesLabel}»", card.minutesLabel.matches(Regex("""~\d+ min""")))
        assertEquals(model.levelLabel, card.levelLabel)
        // PHAT es powerbuilding: la portada lleva el color de ese objetivo.
        assertEquals(TrainingGoalProfile.POWERBUILDING, card.profile)
        assertEquals(model.summary, ficha.detail.description)
    }

    @Test
    fun theChipsSayFocusMaterialAndScope() {
        val (model, ficha) = ficha(phat)
        val chips = ficha.content.chips
        assertEquals("Fuerza y músculo", chips.first())
        assertTrue(model.material.isNotEmpty())
        assertTrue("material en un chip: $chips", chips.contains(PlanInfoFicha.materialChip(model.material)))
        assertTrue(chips.contains(model.scopeLabel))
    }

    // ─── «Tu semana» día por día ─────────────────────────────────────────────

    @Test
    fun theWeekGoesFromMondayToSundayWithRestDaysAndKeepsEveryExerciseOfTheSheet() {
        val (model, ficha) = ficha(phat)
        val days = ficha.content.days
        assertEquals(listOf("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom"), days.map { it.label })
        assertEquals((1..7).toList(), days.map { it.day })
        days.filter { it.rest }.forEach { day ->
            assertEquals("Descanso", day.title)
            assertTrue(day.lines.isEmpty())
            assertNull(day.subtitle)
        }
        // Ningún ejercicio de la hoja de texto se pierde: cada línea de la semana tipo es una línea de algún día.
        val sheetLines = (model.typicalWeek as TypicalWeek.Days).days.flatMap { it.lines }
        assertEquals(sheetLines.size, days.flatMap { it.lines }.size)
        // Los nombres de la ficha son los de la hoja (sin la prescripción larga).
        assertEquals(sheetLines.map { it.substringBefore(" · ").trim() }.sorted(), days.flatMap { it.lines }.map { it.name }.sorted())
        // Cada día de entreno dice «N ejercicios · ~M min» y se despliega con prescripciones cortas.
        days.filterNot { it.rest }.forEach { day ->
            assertTrue("«${day.subtitle}»", day.subtitle!!.matches(Regex("""\d+ ejercicios? · ~\d+ min""")))
            day.lines.forEach { line -> assertTrue("«${line.prescription}»", line.prescription.contains("×")) }
        }
        // Llega desplegado el primer día de entreno, como el lienzo.
        assertEquals(days.first { !it.rest }.day, ficha.content.openDay)
    }

    @Test
    fun aGeneratedPlanHasNoFixedWeekAndSaysSo() {
        val native = entry("native:full-body")
        val (model, ficha) = ficha(native)
        assertTrue(model.typicalWeek is TypicalWeek.Generated)
        assertTrue(ficha.content.days.isEmpty())
        assertEquals(PlanInfoModelBuilder.GENERATED_WEEK_TEXT, ficha.content.weekNote)
        // Sin receta no hay ejercicios que enseñar: la sección desaparece.
        assertTrue(ficha.content.exercises.isEmpty())
    }

    // ─── Ejercicios principales e imagen de KPKN ─────────────────────────────

    @Test
    fun mainExercisesAreAtMostSixWithTheKpknImageOrNoThumbnail() {
        // El primero de la lista: el primer principal (T1) de la semana 1.
        val week1 = phat.recipe!!.weeks.first { it.weekNumber == 1 }
        val withImage = week1.days.flatMap { it.slots }.filter { slot -> slot.sets.any { !it.isWarmup } }
            .minBy { it.role.ordinal }.lift.configurationId
        val (_, ficha) = ficha(phat) { configurationId -> if (configurationId == withImage) 4242 else null }
        val exercises = ficha.content.exercises
        assertTrue(exercises.isNotEmpty())
        assertTrue(exercises.size <= 6)
        assertEquals(exercises.size, exercises.map { it.name }.distinct().size)
        // Solo la que KPKN tiene lleva miniatura; el resto, ninguna (nunca otro dibujo en su lugar).
        assertEquals(4242, exercises.first().imageResId)
        assertEquals(1, exercises.count { it.imageResId == 4242 })
        assertTrue(exercises.filter { it.imageResId != 4242 }.all { it.imageResId == null })
        exercises.forEach { assertTrue("«${it.detail}»", it.detail!!.contains("×")) }
    }

    @Test
    fun withoutAnyKpknImageNoRowHasAThumbnail() {
        val (_, ficha) = ficha(phat) { null }
        assertTrue(ficha.content.exercises.all { it.imageResId == null })
    }

    // ─── Secciones que no se inventan, notas, atribución y pie ───────────────

    @Test
    fun noReasonsAreInventedAndTheNotesAndSourceAreKept() {
        val (model, ficha) = ficha(phat)
        assertTrue("el catálogo no trae «Por qué este programa»", ficha.detail.reasons.isEmpty())
        assertEquals(model.notes, ficha.detail.notes)
        val attribution = ficha.detail.attribution.orEmpty()
        val source = model.source?.attributionLine
        if (source != null) assertTrue(attribution.startsWith(source)) else assertTrue(attribution.startsWith(model.provenanceLabel))
    }

    @Test
    fun theLibraryShowsConfigureThisPlanAndReadOnlyHasNoFooter() {
        assertEquals("Configurar este plan", ficha(phat).second.content.primaryLabel)
        assertNull(ficha(phat, PlanInfoMode.READ_ONLY).second.content.primaryLabel)
    }

    @Test
    fun noInternalIdsReachTheScreen() {
        val rawIdPrefix = Regex("""\b(protocol|native|template|original|adapted):[a-z0-9]""")
        listOf(phat, entry("native:full-body")).forEach { catalogEntry ->
            val all = texts(ficha(catalogEntry).second)
            all.forEach { text ->
                assertFalse("«$text»", rawIdPrefix.containsMatchIn(text))
                assertFalse("«$text»", text.contains("__"))
            }
        }
    }

    // ─── Prescripción corta y chips ──────────────────────────────────────────

    @Test
    fun theShortPrescriptionCountsRepsAndSharedEffort() {
        val work = SetRecipe(repsMin = 3, repsMax = 5, rir = 2)
        assertEquals("3 × 3–5 · RIR 2", PlanInfoFicha.shortPrescription(List(3) { work }))
        // Los calentamientos no cuentan.
        assertEquals("3 × 3–5 · RIR 2", PlanInfoFicha.shortPrescription(listOf(SetRecipe(reps = 10, isWarmup = true)) + List(3) { work }))
        // El rango de series del autor manda si todas son iguales.
        assertEquals("3–4 × 8–12", PlanInfoFicha.shortPrescription(List(3) { SetRecipe(repsMin = 8, repsMax = 12) }, AuthoredSetRange(3, 4)))
        // Series distintas se listan; el AMRAP lleva «+»; un esfuerzo que no comparten no se dice.
        assertEquals(
            "1 × 5, 1 × 3, 1 × 1+",
            PlanInfoFicha.shortPrescription(listOf(SetRecipe(reps = 5, rir = 2), SetRecipe(reps = 3, rir = 1), SetRecipe(reps = 1, amrap = true))),
        )
        assertEquals("5 × 5 · 75 %", PlanInfoFicha.shortPrescription(List(5) { SetRecipe(reps = 5, percent = 75.0) }))
        assertEquals("3 × 8 · RPE 8,5", PlanInfoFicha.shortPrescription(List(3) { SetRecipe(reps = 8, rpe = 8.5) }))
        assertEquals("", PlanInfoFicha.shortPrescription(emptyList()))
    }

    @Test
    fun theMaterialGoesInOneChip() {
        assertEquals("Barra, banco, rack de sentadilla", PlanInfoFicha.materialChip(listOf("Barra", "Banco", "Rack de sentadilla")))
        assertNull(PlanInfoFicha.materialChip(emptyList()))
        assertEquals("Barra, banco y 2 más", PlanInfoFicha.materialChip(listOf("Barra", "Banco", "Rack de sentadilla", "Polea")))
        assertEquals("Gimnasio completo", PlanInfoFicha.materialChip(listOf("Barra", "Banco", "Rack", "Polea", "Mancuerna", "Prensa de piernas")))
    }
}
