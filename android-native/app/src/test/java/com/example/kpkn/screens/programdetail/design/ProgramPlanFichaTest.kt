package com.example.kpkn.screens.programdetail.design

import com.example.kpkn.data.models.Block
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.IntensityMode
import com.example.kpkn.data.models.Macrocycle
import com.example.kpkn.data.models.Mesocycle
import com.example.kpkn.data.models.Program
import com.example.kpkn.data.models.ProgramWeek
import com.example.kpkn.data.models.RepRange
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.programs.PersonalizedPlanCatalog
import com.example.kpkn.data.protocols.definitions.AuthoredPhulPhatRecipes
import com.example.kpkn.domain.training.CatalogCompositionTestSupport
import com.example.kpkn.screens.onboarding.design.entreno.plan.PlanFicha
import com.example.kpkn.screens.onboarding.design.entreno.plan.PlanFichaCopy
import com.example.kpkn.screens.onboarding.design.entreno.plan.PlanFichaRowIcon
import com.example.kpkn.screens.programdetail.MAX_CHANGES
import com.example.kpkn.screens.programdetail.ProgramPlanDisplaySummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

/**
 * Arreglos v1 · P3: «Sobre el programa» como la ficha del asistente en modo lectura (escenas p-about y p-about-end). JVM puro: la
 * portada «Tu programa · Activo», los chips con lo que tenía la hoja (material, sesión más larga, calendario), «Cargas y cambios»,
 * la semana en curso día por día, los ejercicios principales con la imagen de KPKN (o sin miniatura), el origen y «Ver cómo
 * funciona» solo cuando hay ficha del catálogo que abrir. Nunca hay pie de elegir.
 */
class ProgramPlanFichaTest {

    @Before
    fun installCatalogMetadata() {
        CatalogCompositionTestSupport.install()
    }

    // 2026-10-29 es jueves.
    private val thursday = LocalDate.of(2026, 10, 29)

    private fun sets(count: Int, min: Int, max: Int, rir: Int? = 2, rpe: Double? = null): List<ExerciseSet> = List(count) { index ->
        ExerciseSet(
            id = "s$index",
            targetReps = (min + max) / 2,
            targetRepsRange = RepRange(min, max),
            targetRIR = rir,
            targetRPE = rpe,
            intensityMode = if (rir != null) IntensityMode.RIR else null,
        )
    }

    private val bench = Exercise(id = "e-bench", name = "Press de banca", catalogConfigurationId = "cfg-bench", sets = sets(4, 6, 8))
    private val row = Exercise(id = "e-row", name = "Remo con barra", sets = sets(3, 8, 12))
    private val squat = Exercise(id = "e-squat", name = "Sentadilla", sets = sets(3, 5, 5, rir = 3))
    private val deadlift = Exercise(id = "e-dl", name = "Peso muerto", sets = sets(3, 5, 5))

    private val week0 = ProgramWeek(
        id = "w0",
        name = "Semana 1",
        sessions = listOf(
            Session(id = "torso", name = "Torso", exercises = listOf(bench, row), dayOfWeek = 1),
            Session(id = "pierna", name = "Pierna", exercises = listOf(squat), dayOfWeek = 4),
        ),
    )

    private fun program(week1: ProgramWeek = ProgramWeek(
        id = "w1",
        name = "Semana 2",
        sessions = listOf(Session(id = "tiron", name = "Tirón", exercises = listOf(deadlift), dayOfWeek = 2)),
    )): Program = Program(
        id = "p",
        name = "Mi PHAT",
        macrocycles = listOf(
            Macrocycle("m", "Macro", blocks = listOf(Block("b", "Bloque", mesocycles = listOf(Mesocycle("me", "Meso", weeks = listOf(week0, week1)))))),
        ),
    )

    private val changes = listOf(
        "Remo con barra → remo en máquina · Cambio manual",
        "Sentadilla → sentadilla hack · Molestia en la rodilla",
        "Cambio sin detalle",
        "Cuarto cambio · Ajuste",
        "Quinto cambio · Ajuste",
        "Sexto cambio · Ajuste",
    )

    private fun summary(
        withEntry: Boolean = true,
        summaryText: String? = "Cinco días: dos de fuerza y tres de músculo.",
        pendingLoads: Int = 2,
        changeList: List<String> = changes,
    ) = ProgramPlanDisplaySummary(
        catalogEntry = if (withEntry) PersonalizedPlanCatalog.find(AuthoredPhulPhatRecipes.PHAT_ORIGINAL_ID) else null,
        planName = "PHAT",
        provenanceLabel = "Plan original",
        sourceLine = "Layne Norton",
        summary = summaryText,
        planNotes = listOf("Las series rápidas van al 65 % de tu carga habitual."),
        requiredMaterial = "barra, banco y rack",
        changes = changeList,
        daysPerWeek = "5 días",
        maxSessionMinutes = 75,
        weeklyVolume = "",
        pendingLoadCount = pendingLoads,
    )

    private fun ficha(
        summary: ProgramPlanDisplaySummary = summary(),
        program: Program? = program(),
        description: String? = null,
        calendarRange: String? = "6 oct – 16 nov",
        isActive: Boolean = true,
        canOpen: Boolean = true,
        currentWeekId: String? = null,
        today: LocalDate = thursday,
        imageOf: (Exercise) -> Int? = { null },
    ): PlanFicha = programPlanFicha(
        summary = summary,
        program = program,
        description = description,
        calendarRange = calendarRange,
        isActive = isActive,
        canOpenPlanInfo = canOpen,
        currentWeekId = currentWeekId,
        today = today,
        imageOf = imageOf,
    )

    // ─── Portada y chips ─────────────────────────────────────────────────────

    @Test
    fun theCoverSaysYourActiveProgramWithItsNameGoalAndWeeks() {
        val card = ficha().detail.card
        assertEquals(PlanFichaCopy.EYEBROW_PROGRAM_ACTIVE, card.kicker)
        assertEquals("Tu programa · Activo", card.kicker)
        assertEquals("Mi PHAT", card.title)
        assertNull("sin perfil: el acento verde del lienzo", card.profile)
        assertEquals("Fuerza y músculo", card.daysLabel)
        assertEquals("2 semanas", card.minutesLabel)
        assertEquals("con un bloque no se dice", "", card.levelLabel)
        assertEquals("Tu programa", ficha(isActive = false).detail.card.kicker)
        // Sin programa cargado, el nombre del plan.
        assertEquals("PHAT", ficha(program = null).detail.card.title)
    }

    @Test
    fun theChipsKeepMaterialLongestSessionAndCalendar() {
        assertEquals(listOf("Barra, banco y rack", "Sesión más larga ≈ 75 min", "6 oct – 16 nov"), ficha().content.chips)
        assertEquals(listOf("Barra, banco y rack", "Sesión más larga ≈ 75 min"), ficha(calendarRange = null).content.chips)
    }

    @Test
    fun theDescriptionIsTheCatalogSummaryOrTheFirstLineOfTheProgram() {
        assertEquals("Cinco días: dos de fuerza y tres de músculo.", ficha().detail.description)
        assertEquals(
            "Primera línea",
            ficha(summary = summary(summaryText = null), description = "\n  Primera línea\nSegunda").detail.description,
        )
        assertEquals("", ficha(summary = summary(summaryText = null)).detail.description)
    }

    // ─── Cargas y cambios, notas, origen y acciones ──────────────────────────

    @Test
    fun loadsAndChangesGoInTheirOwnSection() {
        val sections = ficha().content.sections
        assertEquals(1, sections.size)
        val section = sections.single()
        assertEquals(PlanFichaCopy.LOADS_AND_CHANGES, section.title)
        val rows = section.rows
        assertEquals(1 + MAX_CHANGES, rows.size)
        assertEquals(PlanFichaRowIcon.TARGET, rows.first().icon)
        assertEquals(PlanFichaCopy.REFERENCE_LOADS, rows.first().title)
        assertEquals("2 por resolver", rows.first().subtitle)
        assertTrue(rows.drop(1).all { it.icon == PlanFichaRowIcon.SWAP })
        assertEquals("Remo con barra → remo en máquina", rows[1].title)
        assertEquals("Cambio manual", rows[1].subtitle)
        assertEquals("Cambio sin detalle", rows[3].title)
        assertEquals("", rows[3].subtitle)
        // Sin cargas por resolver ni cambios, la sección desaparece.
        assertTrue(ficha(summary = summary(pendingLoads = 0, changeList = emptyList())).content.sections.isEmpty())
    }

    @Test
    fun theNotesAndTheOriginAreKept() {
        val ficha = ficha()
        assertEquals(listOf("Las series rápidas van al 65 % de tu carga habitual."), ficha.detail.notes)
        assertEquals("Plan original · Layne Norton", ficha.detail.attribution)
        assertTrue("no se inventan razones", ficha.detail.reasons.isEmpty())
    }

    @Test
    fun readModeHasNoFooterAndHowItWorksOnlyWithACatalogEntry() {
        assertNull(ficha().content.primaryLabel)
        assertEquals("Ver cómo funciona", ficha().content.secondaryLabel)
        assertNull(ficha(canOpen = false).content.secondaryLabel)
        assertNull(ficha(summary = summary(withEntry = false)).content.secondaryLabel)
    }

    // ─── Tu semana ───────────────────────────────────────────────────────────

    @Test
    fun theWeekIsTheShownWeekDayByDayWithShortPrescriptions() {
        val days = ficha().content.days
        assertEquals(listOf("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom"), days.map { it.label })
        assertEquals(listOf(false, true, true, false, true, true, true), days.map { it.rest })
        val monday = days[0]
        assertEquals("Torso", monday.title)
        assertEquals(listOf("Press de banca", "Remo con barra"), monday.lines.map { it.name })
        assertEquals(listOf("4 × 6–8 · RIR 2", "3 × 8–12 · RIR 2"), monday.lines.map { it.prescription })
        assertTrue("«${monday.subtitle}»", monday.subtitle!!.startsWith("2 ejercicios"))
        assertEquals("3 × 5 · RIR 3", days[3].lines.single().prescription)
        assertEquals("Descanso", days[1].title)
    }

    @Test
    fun todayOpensIfItIsATrainingDayOtherwiseTheFirstOne() {
        assertEquals(4, ficha(today = thursday).content.openDay)
        assertEquals(1, ficha(today = thursday.minusDays(2)).content.openDay)
    }

    @Test
    fun withoutAProgramThereIsNoWeekNorExercises() {
        val ficha = ficha(program = null)
        assertTrue(ficha.content.days.isEmpty())
        assertTrue(ficha.content.exercises.isEmpty())
        assertTrue(ficha.detail.blocks.isEmpty())
        assertNull(ficha.content.openDay)
    }

    // ─── Ejercicios principales e imagen de KPKN ─────────────────────────────

    @Test
    fun mainExercisesCarryTheKpknImageOrNoThumbnail() {
        val exercises = ficha(imageOf = { exercise -> if (exercise.catalogConfigurationId == "cfg-bench") 7 else null }).content.exercises
        assertEquals(listOf("Press de banca", "Sentadilla"), exercises.map { it.name })
        assertEquals(listOf(7, null), exercises.map { it.imageResId })
        assertEquals("Torso · 4 × 6–8 · RIR 2", exercises.first().detail)
    }

    // ─── La semana enseñada y la prescripción ────────────────────────────────

    @Test
    fun theShownWeekIsTheCurrentOneThenTodaysThenTheFirst() {
        val plain = program()
        assertEquals("w1", shownWeekOf(plain, "w1", thursday)?.id)
        assertEquals("w0", shownWeekOf(plain, null, thursday)?.id)
        assertEquals("w0", shownWeekOf(plain, "no-existe", thursday)?.id)
        val dated = program(
            ProgramWeek(
                id = "w1",
                name = "Semana 2",
                sessions = listOf(Session(id = "tiron", name = "Tirón", exercises = listOf(deadlift), dayOfWeek = 2)),
                trainingDayDates = mapOf(2 to "2026-10-27"),
            ),
        )
        assertEquals("w1", shownWeekOf(dated, null, thursday)?.id)
        assertEquals("Tirón", ficha(program = dated).content.days[1].title)
    }

    @Test
    fun theExercisePrescriptionIsShort() {
        assertEquals("4 × 6–8 · RIR 2", exercisePrescription(bench))
        assertEquals("3 × 8 · RPE 8,5", exercisePrescription(Exercise(id = "x", name = "X", sets = sets(3, 8, 8, rir = null, rpe = 8.5))))
        assertEquals("3 × 10", exercisePrescription(Exercise(id = "y", name = "Y", sets = sets(3, 10, 10, rir = null))))
        assertEquals("", exercisePrescription(Exercise(id = "z", name = "Z")))
        assertNotNull(ficha().detail.blocks.firstOrNull())
    }
}
