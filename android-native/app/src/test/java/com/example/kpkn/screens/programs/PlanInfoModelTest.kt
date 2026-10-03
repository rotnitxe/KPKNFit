package com.example.kpkn.screens.programs

import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.data.programs.CatalogEntry
import com.example.kpkn.data.programs.CatalogSource
import com.example.kpkn.data.programs.PersonalizedPlanCatalog
import com.example.kpkn.data.programs.PlanGlossary
import com.example.kpkn.data.programs.PlanTerm
import com.example.kpkn.data.protocols.AuthoredSetRange
import com.example.kpkn.data.protocols.DayRecipe
import com.example.kpkn.data.protocols.LiftRef
import com.example.kpkn.data.protocols.LoadBasis
import com.example.kpkn.data.protocols.PlanLoadReference
import com.example.kpkn.data.protocols.PlanLoadReferenceKind
import com.example.kpkn.data.protocols.RecipeCardioBlock
import com.example.kpkn.data.protocols.RecipeCardioPosition
import com.example.kpkn.data.protocols.SetRecipe
import com.example.kpkn.data.protocols.SlotRecipe
import com.example.kpkn.data.protocols.SlotRole
import com.example.kpkn.data.protocols.TechniqueModifier
import com.example.kpkn.data.protocols.TrainingPlanRecipe
import com.example.kpkn.data.protocols.WeekRecipe
import com.example.kpkn.domain.exercises.catalogv2.CatalogDisplayNames
import com.example.kpkn.domain.training.CatalogCompositionTestSupport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * C.P8 · Modelo puro de la hoja «Cómo funciona» (JVM puro, sin Robolectric ni Compose).
 *
 * Fija la semana tipo (semana 1 sin calentamientos, series agrupadas, rangos del autor, AMRAP, base de
 * carga en palabras, coma decimal), el material, la fuente, las notas, el glosario y el botón por modo, y
 * que ningún texto del modelo deje escapar ids, códigos de regla ni la leyenda «No afiliado a KPKN».
 */
class PlanInfoModelTest {

    @Before
    fun installCatalogMetadata() {
        CatalogCompositionTestSupport.install()
    }

    // ─── Ayudas ──────────────────────────────────────────────────────────────

    private val nameIndex: Map<String, String> by lazy {
        CatalogDisplayNames.buildDisplayNameIndex(CatalogCompositionTestSupport.catalog)
            .mapKeys { (key, _) -> key.trim().lowercase() }
    }

    private fun realNames(configurationId: String): String? = nameIndex[configurationId.trim().lowercase()]

    private fun realEquipment(configurationId: String): String? =
        CatalogCompositionTestSupport.metadata.metadata(configurationId)?.equipmentId

    private fun entry(id: String): CatalogEntry =
        PersonalizedPlanCatalog.find(id) ?: error("Falta la entrada del catálogo «$id»")

    private fun build(
        entry: CatalogEntry,
        mode: PlanInfoMode = PlanInfoMode.LIBRARY,
        readyWeek: ReadyWeekSnapshot? = null,
    ): PlanInfoModel = PlanInfoModelBuilder.build(
        entry = entry,
        mode = mode,
        names = { id -> realNames(id) },
        equipmentOf = { id -> realEquipment(id) },
        readyWeek = readyWeek,
    )

    private fun days(model: PlanInfoModel): List<DayLines> {
        val week = model.typicalWeek
        assertTrue("la semana tipo debería traer días: $week", week is TypicalWeek.Days)
        return (week as TypicalWeek.Days).days
    }

    private fun lines(model: PlanInfoModel): List<String> = days(model).flatMap { it.lines }

    private fun work(
        reps: Int? = null,
        repsMin: Int? = null,
        repsMax: Int? = null,
        percent: Double? = null,
        rpe: Double? = null,
        rir: Int? = null,
        amrap: Boolean = false,
        isTopSet: Boolean = false,
        basis: LoadBasis = LoadBasis.PERCENT_TM,
        reference: PlanLoadReference? = null,
    ): SetRecipe = SetRecipe(
        reps = reps,
        repsMin = repsMin,
        repsMax = repsMax,
        percent = percent,
        rpe = rpe,
        rir = rir,
        amrap = amrap,
        isTopSet = isTopSet,
        loadBasis = basis,
        reference = reference,
    )

    private fun warm(reps: Int, percent: Double): SetRecipe =
        SetRecipe(reps = reps, percent = percent, isWarmup = true)

    private fun fmt(vararg sets: SetRecipe, range: AuthoredSetRange? = null): String =
        PlanInfoModelBuilder.formatSets(sets.toList(), range)

    private val fakeNames = mapOf(
        "sq" to "Sentadilla",
        "bp" to "Press de banca",
        "paused" to "Sentadilla con pausa",
        "chin" to "Dominadas",
        "press-leg" to "Prensa de piernas",
    )

    private fun slot(
        configurationId: String,
        sets: List<SetRecipe>,
        technique: TechniqueModifier? = null,
        range: AuthoredSetRange? = null,
    ): SlotRecipe = SlotRecipe(
        id = "slot-$configurationId",
        role = SlotRole.T1_MAIN,
        lift = LiftRef(configurationId),
        sets = sets,
        restSeconds = 120,
        technique = technique,
        authoredSetRange = range,
    )

    private fun day(label: String, slots: List<SlotRecipe>, cardio: List<RecipeCardioBlock> = emptyList()): DayRecipe =
        DayRecipe(label = label, slots = slots, cardioBlocks = cardio)

    /** Un plan sintético: la ficha de Texas con la receta de [weeks] semanas iguales hechas de [days]. */
    private fun syntheticModel(
        days: List<DayRecipe>,
        weeks: Int = 1,
        equipment: Map<String, String> = emptyMap(),
        names: Map<String, String> = fakeNames,
    ): PlanInfoModel {
        val recipe = TrainingPlanRecipe(
            id = "synthetic",
            weeks = (1..weeks).map { number -> WeekRecipe(weekNumber = number, blockIndex = 0, days = days) },
        )
        val synthetic = entry("protocol:texas-method-3d").copy(recipe = recipe)
        return PlanInfoModelBuilder.build(
            entry = synthetic,
            mode = PlanInfoMode.LIBRARY,
            names = { id -> names[id] },
            equipmentOf = { id -> equipment[id] },
        )
    }

    private fun syntheticLines(vararg slots: SlotRecipe): List<String> =
        lines(syntheticModel(listOf(day("Día A", slots.toList()))))

    // ─── 1 · Series: agrupar, rangos, AMRAP, base de carga ───────────────────

    @Test
    fun identical_sets_are_grouped_and_warmups_do_not_count() {
        assertEquals(
            "5 × 5 al 90 % de tu serie más pesada",
            fmt(*Array(5) { work(reps = 5, percent = 90.0, basis = LoadBasis.PERCENT_OF_TOP_SET) }),
        )
        assertEquals(
            "4 × 4 al 70 % de tu TM",
            fmt(warm(5, 40.0), warm(3, 55.0), warm(1, 65.0), *Array(4) { work(reps = 4, percent = 70.0) }),
        )
        assertEquals("sin series de trabajo no hay texto", "", fmt(warm(5, 40.0), warm(3, 55.0)))
        assertEquals("5 × 5", fmt(*Array(5) { work(reps = 5) }))
    }

    @Test
    fun the_authored_set_range_replaces_the_count_and_reps_show_their_range() {
        val sets = Array(3) { work(reps = 8, repsMin = 8, repsMax = 12, rir = 2) }
        assertEquals("3–4 × 8–12 con 2 repeticiones en reserva", fmt(*sets, range = AuthoredSetRange(3, 4)))
        assertEquals("3 × 8–12 con 2 repeticiones en reserva", fmt(*sets))
        assertEquals(
            "6 × 3 a esfuerzo 7 de 10",
            fmt(*Array(6) { work(reps = 3, rpe = 7.0, basis = LoadBasis.RPE) }, range = AuthoredSetRange(6, 6)),
        )
        assertEquals("rpeSets con repsMax", "3 × 6–8 a esfuerzo 8 de 10", fmt(*Array(3) { work(reps = 6, repsMax = 8, rpe = 8.0, basis = LoadBasis.RPE) }))
    }

    @Test
    fun an_amrap_set_reads_with_a_plus() {
        assertEquals("1 × 3+ al 85 % de tu TM", fmt(work(reps = 3, percent = 85.0, amrap = true)))
        assertEquals(
            "2 × 5 al 75 % y 1 × 5+ al 85 % de tu TM",
            fmt(work(reps = 5, percent = 75.0), work(reps = 5, percent = 75.0), work(reps = 5, percent = 85.0, amrap = true)),
        )
    }

    @Test
    fun the_load_basis_is_said_in_words() {
        assertEquals("3 × 5 al 80 % de tu TM", fmt(*Array(3) { work(reps = 5, percent = 80.0, basis = LoadBasis.PERCENT_TM) }))
        assertEquals("3 × 5 al 80 % de tu 1RM", fmt(*Array(3) { work(reps = 5, percent = 80.0, basis = LoadBasis.PERCENT_1RM) }))
        assertEquals("1 × 2 al 95 % de tu 1RM", fmt(work(reps = 2, percent = 95.0, basis = LoadBasis.PERCENT_DESIRED_MAX)))
        assertEquals("3 × 5 al 80 % de tu serie más pesada", fmt(*Array(3) { work(reps = 5, percent = 80.0, basis = LoadBasis.PERCENT_OF_TOP_SET) }))
        assertEquals("3 × 8 a esfuerzo 8 de 10", fmt(*Array(3) { work(reps = 8, rpe = 8.0, basis = LoadBasis.RPE) }))
        assertEquals("3 × 10 con 2 repeticiones en reserva", fmt(*Array(3) { work(reps = 10, rir = 2, rpe = 8.0, basis = LoadBasis.RPE) }))
        assertEquals("el RIR manda sobre el RPE que se deriva de él", "1 × 10 con 1 repetición en reserva", fmt(work(reps = 10, rir = 1, rpe = 9.0, basis = LoadBasis.RPE)))
        assertEquals("sin repeticiones en reserva", "1 × 12 sin repeticiones en reserva", fmt(work(reps = 12, rir = 0)))
        assertEquals("1 × al máximo de 2 repeticiones", fmt(work(reps = 2, percent = 90.0, isTopSet = true, basis = LoadBasis.REP_MAX)))
        assertEquals("el 100 % de la serie más pesada es esa serie", "1 × 5 como tu serie más pesada", fmt(work(reps = 5, percent = 100.0, isTopSet = true, basis = LoadBasis.PERCENT_OF_TOP_SET)))
    }

    @Test
    fun an_explicit_load_reference_wins_over_the_generic_basis() {
        val habitual = PlanLoadReference(
            kind = PlanLoadReferenceKind.OBSERVED_WORKING_SET,
            configurationId = "x",
            repMin = 3,
            repMax = 5,
        )
        assertEquals(
            "6 × 3 al 65 % de tu carga habitual de 3–5 repeticiones",
            fmt(*Array(6) { work(reps = 3, percent = 65.0, basis = LoadBasis.RPE, reference = habitual) }),
        )
        val oneRm = PlanLoadReference(kind = PlanLoadReferenceKind.EXERCISE_1RM, configurationId = "x")
        assertEquals("1 × 3 al 80 % de tu 1RM", fmt(work(reps = 3, percent = 80.0, basis = LoadBasis.RPE, reference = oneRm)))
        assertEquals("un porcentaje sin base que decir no se inventa", "1 × 3 a esfuerzo 8 de 10", fmt(work(reps = 3, percent = 65.0, rpe = 8.0, basis = LoadBasis.RPE)))
    }

    @Test
    fun percentages_use_a_decimal_comma_without_useless_zeros() {
        assertEquals("62,5", PlanInfoModelBuilder.formatNumber(62.5))
        assertEquals("90", PlanInfoModelBuilder.formatNumber(90.0))
        assertEquals("100", PlanInfoModelBuilder.formatNumber(100.0))
        assertEquals("87,5", PlanInfoModelBuilder.formatNumber(87.5))
        assertEquals("46,3", PlanInfoModelBuilder.formatNumber(46.25))
        assertEquals("7,5", PlanInfoModelBuilder.formatNumber(7.5))
        assertEquals("0", PlanInfoModelBuilder.formatNumber(0.0))
        val text = fmt(work(reps = 5, percent = 62.5), work(reps = 5, percent = 87.5))
        assertTrue("62,5 % y 87,5 % deben salir enteros: $text", text.contains("62,5 %") && text.contains("87,5 %"))
        assertFalse("nada de truncar a 62 u 87: $text", Regex("""\b(62|87) %""").containsMatchIn(text))
        assertFalse("nada de punto decimal: $text", text.contains('.'))
    }

    @Test
    fun a_ramp_lists_its_sets_and_says_the_shared_base_once() {
        val ramp = listOf(50.0, 62.5, 75.0, 87.5, 100.0).map { work(reps = 5, percent = it) }
        assertEquals(
            "1 × 5 al 50 %, 1 × 5 al 62,5 %, 1 × 5 al 75 %, 1 × 5 al 87,5 % y 1 × 5 al 100 % de tu TM",
            fmt(*ramp.toTypedArray()),
        )
        assertEquals(
            "3 × 5 al 70 % y 2 × 3 al 80 % de tu TM",
            fmt(*Array(3) { work(reps = 5, percent = 70.0) }, *Array(2) { work(reps = 3, percent = 80.0) }),
        )
    }

    @Test
    fun different_bases_are_each_said_in_full() {
        assertEquals(
            "1 × 5 al 70 % de tu TM y 1 × 3 a esfuerzo 8 de 10",
            fmt(work(reps = 5, percent = 70.0), work(reps = 3, rpe = 8.0, basis = LoadBasis.RPE)),
        )
        assertEquals(
            "un esfuerzo que acompaña al porcentaje va tras una coma",
            "1 × 5 al 75 % de tu TM, con 2 repeticiones en reserva",
            fmt(work(reps = 5, percent = 75.0, rir = 2, rpe = 8.0)),
        )
        assertEquals(
            "1 × 5 al 75 % de tu TM, con 2 repeticiones en reserva · 1 × 3 al 85 % de tu TM, con 1 repetición en reserva",
            fmt(work(reps = 5, percent = 75.0, rir = 2), work(reps = 3, percent = 85.0, rir = 1)),
        )
    }

    // ─── 2 · Líneas de la semana tipo ────────────────────────────────────────

    @Test
    fun texas_3d_shows_five_by_five_at_ninety_percent_of_the_top_set() {
        val texas = entry("protocol:texas-method-3d")
        val model = build(texas)
        val week = days(model)
        assertEquals(listOf("Volumen 5x5", "Recuperación", "Intensidad PR"), week.map { it.label })
        val allLines = week.flatMap { it.lines }
        assertTrue(
            "el lunes lleva 5 × 5 al 90 % de tu serie más pesada: $allLines",
            allLines.any { it.endsWith(" · 5 × 5 al 90 % de tu serie más pesada") },
        )
        assertTrue("el miércoles lleva 2 × 5 al 80 %: $allLines", allLines.any { it.endsWith(" · 2 × 5 al 80 % de tu serie más pesada") })
        assertTrue("el viernes es la serie más pesada: $allLines", allLines.any { it.endsWith(" · 1 × 5 como tu serie más pesada") })
        assertTrue("el peso muerto del lunes va al 70 % de tu TM: $allLines", allLines.any { it.endsWith(" · 1 × 5 al 70 % de tu TM") })
        assertTrue("las dominadas AMRAP: $allLines", allLines.any { it.endsWith(" · 3 × 8+ a esfuerzo 8 de 10") })
        assertEquals("Texas repite su ciclo de 4 semanas", "Así es la semana 1 de 4.", (model.typicalWeek as TypicalWeek.Days).caption)
    }

    @Test
    fun a_recipe_with_warmups_never_shows_them_in_the_week() {
        val sbd = build(entry("protocol:kpkn-native-sbd-4"))
        val firstDay = days(sbd).first()
        assertTrue("la sentadilla va con series de trabajo: ${firstDay.lines}", firstDay.lines.first().endsWith(" · 4 × 4 al 70 % de tu TM"))
        lines(sbd).forEach { line ->
            assertFalse("«$line» enseña un calentamiento (40 % o 55 %)", line.contains("40 %") || line.contains("55 %"))
        }
        val synthetic = syntheticLines(
            slot("sq", listOf(warm(5, 11.0), warm(3, 22.0), warm(1, 33.0)) + List(3) { work(reps = 8, repsMin = 8, repsMax = 12, rir = 2) }, range = AuthoredSetRange(3, 4)),
            slot("bp", listOf(warm(5, 11.0), warm(3, 22.0))),
        )
        assertEquals(listOf("Sentadilla · 3–4 × 8–12 con 2 repeticiones en reserva"), synthetic)
        val onlyWarmups = syntheticModel(listOf(day("Solo calentar", listOf(slot("bp", listOf(warm(5, 11.0)))))))
        assertEquals("un día sin nada que enseñar no sale", TypicalWeek.Unavailable, onlyWarmups.typicalWeek)
    }

    @Test
    fun madcow_week_one_reads_the_ramp_with_decimal_commas() {
        val model = build(entry("protocol:madcow-5x5"))
        val allLines = lines(model)
        assertTrue(
            "la rampa de la semana 1 termina en 92,5 % de tu TM: $allLines",
            allLines.any { it.contains(" y 1 × 5 al 92,5 % de tu TM") },
        )
        allLines.forEach { line -> assertFalse("«$line» lleva punto decimal", Regex("""\d\.\d""").containsMatchIn(line)) }
    }

    @Test
    fun the_phat_speed_blocks_say_where_their_load_comes_from() {
        val model = build(entry("original:phat-biolayne-2016-r1"))
        val speed = lines(model).filter { it.contains("de tu carga habitual") }
        assertTrue("PHAT lleva bloques de series rápidas: ${lines(model)}", speed.isNotEmpty())
        speed.forEach { line ->
            assertTrue(
                "«$line»",
                line.endsWith(" · 6 × 3 al 65 % de tu carga habitual de 3–5 repeticiones (a máxima velocidad)"),
            )
        }
    }

    @Test
    fun phul_original_shows_the_authors_ranges() {
        val model = build(entry("original:phul-ms-2021-r1"))
        val firstDay = days(model).first()
        assertEquals("Superior fuerza", firstDay.label)
        assertTrue(
            "la banca publica 3–4 × 3–5 con una repetición o dos en reserva: ${firstDay.lines}",
            firstDay.lines.first().endsWith(" · 3–4 × 3–5 con 2 repeticiones en reserva"),
        )
        assertEquals(4, days(model).size)
        assertEquals("Así es la semana 1 de 12.", (model.typicalWeek as TypicalWeek.Days).caption)
    }

    @Test
    fun technique_goes_in_parentheses_unless_the_name_already_says_it() {
        val plain = List(3) { work(reps = 5) }
        assertEquals(
            listOf("Press de banca · 3 × 5 (pausa de dos segundos)", "Sentadilla con pausa · 3 × 5"),
            syntheticLines(
                slot("bp", plain, technique = TechniqueModifier.PAUSE_2S),
                slot("paused", plain, technique = TechniqueModifier.PAUSE_2S),
            ),
        )
        TechniqueModifier.entries.forEach { technique ->
            val line = syntheticLines(slot("bp", plain, technique = technique)).single()
            assertTrue("«$line» debe llevar la técnica entre paréntesis", line.endsWith(")") && line.contains(" ("))
            assertFalse("«$line» deja escapar el nombre interno", line.contains(technique.name) || line.contains('_'))
        }
    }

    @Test
    fun cardio_blocks_become_a_line_and_respect_their_position() {
        val treadmill = RecipeCardioBlock(id = "c1", details = CardioDetails(type = CardioType.TREADMILL, targetDurationSeconds = 20 * 60))
        val bike = RecipeCardioBlock(
            id = "c2",
            details = CardioDetails(type = CardioType.BIKE_STATIONARY, targetDurationSeconds = 10 * 60 + 30),
            position = RecipeCardioPosition.BEFORE_STRENGTH,
        )
        val model = syntheticModel(listOf(day("Con cardio", listOf(slot("sq", List(3) { work(reps = 5) })), cardio = listOf(treadmill, bike))))
        assertEquals(
            listOf("Cardio: Bici estática 11 min", "Sentadilla · 3 × 5", "Cardio: Cinta 20 min"),
            lines(model),
        )
    }

    @Test
    fun an_unknown_exercise_never_shows_its_id() {
        val model = syntheticModel(listOf(day("Día A", listOf(slot("quads_ejercicio_inexistente__default", List(2) { work(reps = 10) })))))
        val line = lines(model).single()
        assertEquals("${PlanInfoModelBuilder.UNKNOWN_EXERCISE} · 2 × 10", line)
        assertFalse(line.contains('_'))
    }

    @Test
    fun the_caption_only_appears_in_plans_with_more_than_one_week() {
        val oneWeek = syntheticModel(listOf(day("Día A", listOf(slot("sq", List(3) { work(reps = 5) })))), weeks = 1)
        assertNull((oneWeek.typicalWeek as TypicalWeek.Days).caption)
        val fourWeeks = syntheticModel(listOf(day("Día A", listOf(slot("sq", List(3) { work(reps = 5) })))), weeks = 4)
        assertEquals("Así es la semana 1 de 4.", (fourWeeks.typicalWeek as TypicalWeek.Days).caption)
    }

    @Test
    fun english_day_labels_are_said_in_spanish() {
        val sets = List(3) { work(reps = 5) }
        val labels = listOf(
            "Upper A", "Lower B", "ME Upper", "DE Lower", "Upper Power", "Lower Hypertrophy", "Power Lower", "S1",
            "Sentadilla pesada", "Upper plan extra",
        )
        val model = syntheticModel(labels.map { day(it, listOf(slot("sq", sets))) })
        assertEquals(
            listOf(
                "Torso A", "Pierna B", "Torso, esfuerzo máximo", "Pierna, esfuerzo dinámico", "Torso, fuerza",
                "Pierna, hipertrofia", "Pierna, fuerza", "Sesión 1", "Sentadilla pesada", "Upper plan extra",
            ),
            days(model).map { it.label },
        )
    }

    @Test
    fun days_that_share_a_label_are_numbered() {
        val sets = List(3) { work(reps = 5) }
        val model = syntheticModel(
            listOf("Sesión", "Sesión", "Descarga", "Sesión").map { day(it, listOf(slot("sq", sets))) },
        )
        assertEquals(listOf("Sesión 1", "Sesión 2", "Descarga", "Sesión 3"), days(model).map { it.label })
    }

    @Test
    fun repeated_exercise_names_in_a_day_are_told_apart_by_their_material() {
        val sets = List(3) { work(reps = 10) }
        val names = mapOf(
            "conventional_row__cable" to "Remo Convencional",
            "conventional_row__dumbbells" to "Remo Convencional",
            "calf_raise__bilateral__machine" to "Elevación de Talones",
            "calf_raise__bilateral__seated_machine" to "Elevación de Talones",
            "sq" to "Sentadilla",
            "mystery-a" to "Ejercicio",
            "mystery-b" to "Ejercicio",
        )
        val model = syntheticModel(
            days = listOf(
                day(
                    "Día A",
                    listOf(
                        slot("conventional_row__cable", sets),
                        slot("conventional_row__dumbbells", sets),
                        slot("calf_raise__bilateral__machine", sets),
                        slot("calf_raise__bilateral__seated_machine", sets),
                        slot("sq", sets),
                        slot("sq", List(2) { work(reps = 5) }),
                        slot("mystery-a", sets),
                        slot("mystery-b", sets),
                    ),
                ),
            ),
            equipment = mapOf(
                "conventional_row__cable" to "cable",
                "conventional_row__dumbbells" to "dumbbells",
                "calf_raise__bilateral__machine" to "machine",
                "calf_raise__bilateral__seated_machine" to "machine",
                "sq" to "barbell",
            ),
            names = names,
        )
        assertEquals(
            listOf(
                "Remo Convencional (polea alta y baja) · 3 × 10",
                "Remo Convencional (mancuerna) · 3 × 10",
                "Elevación de Talones (gemelo de pie) · 3 × 10",
                "Elevación de Talones (gemelo sentado) · 3 × 10",
                "Sentadilla · 3 × 10",
                "Sentadilla · 2 × 5",
                "Ejercicio · 3 × 10",
                "Ejercicio · 3 × 10",
            ),
            lines(model),
        )
    }

    @Test
    fun the_authored_plans_never_repeat_a_line_inside_a_day() {
        listOf(
            "original:phul-ms-2021-r1",
            "original:phat-biolayne-2016-r1",
            "adapted:phul-kpkn-r1",
            "adapted:phat-kpkn-r1",
        ).forEach { id ->
            days(build(entry(id))).forEach { day ->
                assertEquals("$id · ${day.label}: líneas repetidas en ${day.lines}", day.lines.size, day.lines.distinct().size)
            }
        }
        val phulLines = lines(build(entry("original:phul-ms-2021-r1")))
        assertTrue(phulLines.any { it.startsWith("Remo Convencional (polea alta y baja) · ") })
        assertTrue(phulLines.any { it.startsWith("Remo Convencional (mancuerna) · ") })
        assertTrue(phulLines.any { it.startsWith("Elevación de Talones (gemelo sentado) · ") })
        assertTrue(phulLines.any { it.startsWith("Elevación de Talones (prensa de piernas) · ") })
    }

    // ─── 3 · Planes sin receta ───────────────────────────────────────────────

    @Test
    fun a_native_plan_is_generated_unless_the_wizard_hands_over_a_ready_week() {
        val native = entry("native:muscle-foundation-v2")
        assertEquals(TypicalWeek.Generated("Se genera con tus días, tu tiempo y tu material."), build(native).typicalWeek)
        assertEquals(
            "una semana vacía no cuenta como semana lista",
            TypicalWeek.Generated(PlanInfoModelBuilder.GENERATED_WEEK_TEXT),
            build(native, readyWeek = ReadyWeekSnapshot(listOf(ReadySession("Torso A", emptyList())))).typicalWeek,
        )
        val ready = ReadyWeekSnapshot(
            listOf(
                ReadySession(
                    label = "Torso A",
                    exercises = listOf(
                        ReadyExercise("Press de banca", "3 × 8–12 con 2 repeticiones en reserva"),
                        ReadyExercise("Remo", ""),
                    ),
                ),
                ReadySession("Pierna A", listOf(ReadyExercise("Prensa de piernas", "4 × 10"))),
            ),
        )
        val model = build(native, mode = PlanInfoMode.WIZARD, readyWeek = ready)
        assertEquals(
            TypicalWeek.Days(
                listOf(
                    DayLines("Torso A", listOf("Press de banca · 3 × 8–12 con 2 repeticiones en reserva", "Remo")),
                    DayLines("Pierna A", listOf("Prensa de piernas · 4 × 10")),
                ),
            ),
            model.typicalWeek,
        )
        assertTrue("sin receta no se afirma material", model.material.isEmpty())
    }

    @Test
    fun a_ready_week_is_ignored_by_plans_that_have_a_recipe() {
        val ready = ReadyWeekSnapshot(listOf(ReadySession("Otra semana", listOf(ReadyExercise("Otro ejercicio", "9 × 9")))))
        val withReady = build(entry("protocol:texas-method-3d"), PlanInfoMode.WIZARD, ready)
        assertEquals(build(entry("protocol:texas-method-3d"), PlanInfoMode.WIZARD).typicalWeek, withReady.typicalWeek)
        assertFalse(lines(withReady).any { it.contains("Otro ejercicio") })
    }

    @Test
    fun blank_structures_have_no_typical_week_and_say_what_they_are() {
        val model = build(entry("template:simple-1"))
        assertEquals(TypicalWeek.Unavailable, model.typicalWeek)
        assertEquals("Estructura", model.kindLabel)
        assertTrue(model.material.isEmpty())
    }

    // ─── 4 · Cabecera, botón y glosario ──────────────────────────────────────

    @Test
    fun the_header_comes_from_the_editorial_card() {
        PersonalizedPlanCatalog.entries().forEach { entry ->
            val model = build(entry)
            assertEquals(entry.displayName, model.displayName)
            assertEquals(entry.summary, model.summary)
            assertEquals(entry.notes, model.notes)
            assertFalse("${entry.id}: la procedencia no puede estar vacía", model.provenanceLabel.isBlank())
            assertFalse("${entry.id}: el nivel no puede estar vacío", model.levelLabel.isBlank())
        }
        val texasEntry = entry("protocol:texas-method-3d")
        val texas = build(texasEntry)
        assertTrue(texas.provenanceLabel, texas.provenanceLabel.startsWith("Versión KPKN del método de "))
        assertEquals("Intermedio", texas.levelLabel)
        assertTrue("Texas lleva su nota del método", texas.notes.isNotEmpty() && texas.notes == texasEntry.notes)
        assertEquals("Original fiel · Brandon Campbell", build(entry("original:phul-ms-2021-r1")).provenanceLabel)
        assertEquals("Plan KPKN", build(entry("native:strength-foundation-v2")).provenanceLabel)
    }

    @Test
    fun the_kind_label_only_exists_when_the_plan_is_not_a_whole_plan() {
        assertNull(build(entry("protocol:texas-method-3d")).kindLabel)
        assertNull(build(entry("native:strength-foundation-v2")).kindLabel)
        assertEquals("Especialización", build(entry("protocol:smolov")).kindLabel)
        assertEquals("Complemento", build(entry("protocol:coan-phillipi-dl")).kindLabel)
        assertEquals("Estructura", build(entry("template:simple-ab")).kindLabel)
    }

    @Test
    fun the_primary_action_depends_on_the_mode() {
        val texas = entry("protocol:texas-method-3d")
        assertEquals("Configurar este plan", build(texas, PlanInfoMode.LIBRARY).primaryActionLabel)
        assertEquals("Elegir este plan", build(texas, PlanInfoMode.WIZARD).primaryActionLabel)
        assertNull(build(texas, PlanInfoMode.READ_ONLY).primaryActionLabel)
    }

    @Test
    fun the_glossary_of_a_plan_with_training_max_also_explains_the_one_rep_max() {
        val texas = build(entry("protocol:texas-method-3d"))
        val terms = texas.glossary.map { it.term }
        assertTrue("Texas usa porcentajes del TM: $terms", PlanTerm.TM in terms)
        assertTrue("el TM se explica con el 1RM: $terms", PlanTerm.ONE_RM in terms)
        assertEquals("en el orden estable del glosario", terms.sortedBy { it.ordinal }, terms)
        assertEquals(PlanGlossary.entryFor(PlanTerm.TM), texas.glossary.first { it.term == PlanTerm.TM })
        assertEquals("rm-tm", texas.glossary.first { it.term == PlanTerm.ONE_RM }.conceptId)
        val phul = build(entry("original:phul-ms-2021-r1")).glossary.map { it.term }
        assertFalse("PHUL no usa porcentajes: $phul", PlanTerm.TM in phul || PlanTerm.ONE_RM in phul)
        assertTrue(PlanTerm.RIR in phul)
    }

    // ─── 5 · Material ────────────────────────────────────────────────────────

    @Test
    fun a_barbell_bench_asks_for_the_bar_the_bench_and_the_rack() {
        val model = syntheticModel(
            days = listOf(day("Día A", listOf(slot("bench_press__barbell", List(3) { work(reps = 5) })))),
            equipment = mapOf("bench_press__barbell" to "barbell"),
        )
        assertEquals(listOf("Barra", "Banco", "Rack de sentadilla"), model.material)
    }

    @Test
    fun a_curated_machine_is_named_as_the_panel_names_it() {
        val model = syntheticModel(
            days = listOf(
                day(
                    "Día A",
                    listOf(
                        slot("quads_prensa_piernas__bilateral", List(3) { work(reps = 10) }),
                        slot("lat_pulldown__bilateral__cable", List(3) { work(reps = 10) }),
                        slot("chest_supported_row__machine", List(3) { work(reps = 10) }),
                        slot("preacher_curl__ez_bar", List(3) { work(reps = 10) }),
                    ),
                ),
            ),
            equipment = mapOf(
                "quads_prensa_piernas__bilateral" to "machine",
                "lat_pulldown__bilateral__cable" to "cable",
                "chest_supported_row__machine" to "machine",
                "preacher_curl__ez_bar" to "ez_bar",
            ),
        )
        assertEquals(
            listOf("Prensa de piernas", "Polea alta y baja", "Máquina", "Barra EZ", "Banco predicador"),
            model.material,
        )
    }

    @Test
    fun material_is_unique_in_order_of_appearance_and_covers_every_week() {
        val sets = List(3) { work(reps = 5) }
        val recipe = TrainingPlanRecipe(
            id = "synthetic",
            weeks = listOf(
                WeekRecipe(1, 0, days = listOf(day("A", listOf(slot("sq", sets), slot("bp", sets))))),
                WeekRecipe(2, 0, days = listOf(day("A", listOf(slot("sq", sets), slot("chin", sets))))),
            ),
        )
        val model = PlanInfoModelBuilder.build(
            entry = entry("protocol:texas-method-3d").copy(recipe = recipe),
            mode = PlanInfoMode.LIBRARY,
            names = { id -> fakeNames[id] },
            equipmentOf = { id -> mapOf("sq" to "barbell", "bp" to "dumbbells", "chin" to "bodyweight")[id] },
        )
        assertEquals(listOf("Barra", "Mancuerna"), model.material)
    }

    @Test
    fun pull_ups_ask_for_the_bar_and_not_for_body_weight() {
        val model = syntheticModel(
            days = listOf(day("Día A", listOf(slot("pull_up__pronated__medium", List(3) { work(reps = 8) })))),
            equipment = mapOf("pull_up__pronated__medium" to "bodyweight"),
        )
        assertEquals(listOf("Barra de dominadas"), model.material)
    }

    @Test
    fun only_body_weight_says_so_and_unresolved_exercises_claim_nothing() {
        val sets = List(3) { work(reps = 15) }
        val bodyweight = syntheticModel(
            days = listOf(day("Día A", listOf(slot("reverse_lunge__bodyweight", sets)))),
            equipment = mapOf("reverse_lunge__bodyweight" to "bodyweight"),
        )
        assertEquals(listOf(PlanInfoModelBuilder.BODYWEIGHT_ONLY), bodyweight.material)
        val unresolved = syntheticModel(days = listOf(day("Día A", listOf(slot("reverse_lunge__bodyweight", sets)))))
        assertTrue("sin saber el implemento no se afirma ni «solo tu peso corporal»", unresolved.material.isEmpty())
    }

    @Test
    fun texas_needs_the_bar_the_bench_and_the_rack() {
        val material = build(entry("protocol:texas-method-3d")).material
        assertTrue("$material", material.containsAll(listOf("Barra", "Banco", "Rack de sentadilla")))
        assertEquals("sin repetidos", material.size, material.toSet().size)
        assertFalse("nada de ids: $material", material.any { it.contains('_') })
    }

    // ─── 6 · Fuente ──────────────────────────────────────────────────────────

    @Test
    fun a_third_party_method_shows_its_attribution_and_an_external_link() {
        val texas = entry("protocol:texas-method-3d")
        val source = build(texas).source
        assertNotNull(source)
        source!!
        assertEquals(texas.attributionLine, source.attributionLine)
        assertTrue(source.attributionLine!!.contains("No afiliado a"))
        assertEquals(texas.sourceUrl, source.url)
        assertEquals("Ver la fuente original (startingstrength.com)", source.urlLabel)
        assertNull(source.editionLine)
        assertNull(source.authoredRules)
        assertNull(source.kpknDefaults)
    }

    @Test
    fun an_authored_plan_adds_its_edition_and_the_two_folds() {
        val phul = entry("original:phul-ms-2021-r1")
        val source = build(phul).source
        assertNotNull(source)
        source!!
        assertTrue(source.attributionLine!!.startsWith("Original fiel de Brandon Campbell"))
        assertEquals("https://www.muscleandstrength.com/workouts/phul-workout", source.url)
        assertEquals("Ver la fuente original (muscleandstrength.com)", source.urlLabel)
        assertEquals(phul.authoredSource!!.editionWithConsult, source.editionLine)
        assertTrue(source.editionLine!!.contains("fuente consultada"))
        assertEquals(phul.authoredSource!!.effectiveRules, source.authoredRules)
        assertEquals(phul.authoredSource!!.kpknDefaults, source.kpknDefaults)
        assertTrue(source.authoredRules!!.isNotEmpty() && source.kpknDefaults!!.isNotEmpty())
        val adapted = build(entry("adapted:phul-kpkn-r1")).source
        assertTrue(adapted!!.attributionLine!!.startsWith("Adaptación KPKN del original de Brandon Campbell"))
        assertNotNull(adapted.authoredRules)
    }

    @Test
    fun a_kpkn_page_is_never_offered_as_a_source_link() {
        val texas = entry("protocol:texas-method-3d")
        val kpkn = build(texas.copy(sourceUrl = "https://kpkn.fit/protocols/texas")).source
        assertNotNull(kpkn)
        assertNull(kpkn!!.url)
        assertNull(kpkn.urlLabel)
        assertEquals(texas.attributionLine, kpkn.attributionLine)
    }

    @Test
    fun a_plan_with_no_attribution_no_link_and_no_authored_source_has_no_source_section() {
        val texas = entry("protocol:texas-method-3d")
        val bare = texas.copy(sourceUrl = null, editorial = texas.editorial.copy(attributionLine = null))
        assertNull(build(bare).source)
        val own = build(entry("template:power-12-3")).source
        assertEquals("Plan propio de KPKN.", own!!.attributionLine)
        assertNull(own.url)
    }

    @Test
    fun only_http_and_https_links_to_other_sites_are_external() {
        assertEquals("example.com", PlanInfoModelBuilder.externalUrlHost("https://www.Example.com/a_b?x=1#y"))
        assertEquals("startingstrength.com", PlanInfoModelBuilder.externalUrlHost(" http://startingstrength.com:8080/article "))
        assertNull(PlanInfoModelBuilder.externalUrlHost("https://kpkn.fit/protocols/x"))
        assertNull(PlanInfoModelBuilder.externalUrlHost("https://app.kpkn.fit/x"))
        assertNull(PlanInfoModelBuilder.externalUrlHost("ftp://example.com/x"))
        assertNull(PlanInfoModelBuilder.externalUrlHost("javascript:alert(1)"))
        assertNull(PlanInfoModelBuilder.externalUrlHost("https://localhost/x"))
        assertNull(PlanInfoModelBuilder.externalUrlHost(""))
        assertNull(PlanInfoModelBuilder.externalUrlHost(null))
    }

    // ─── 7 · Ningún código interno en ningún plan ────────────────────────────

    @Test
    fun every_catalog_entry_builds_in_every_mode_without_leaking_internal_codes() {
        PersonalizedPlanCatalog.entries().forEach { entry ->
            PlanInfoMode.entries.forEach { mode ->
                val model = build(entry, mode)
                allTexts(model).forEach { text ->
                    assertFalse("${entry.id} ($mode): texto vacío", text.isBlank())
                    FORBIDDEN.forEach { (what, regex) ->
                        assertFalse("${entry.id} ($mode): «$text» contiene $what", regex.containsMatchIn(text))
                    }
                }
            }
        }
    }

    @Test
    fun every_entry_with_a_recipe_resolves_every_exercise_name() {
        PersonalizedPlanCatalog.entries()
            .filter { (it.recipe ?: it.template?.recipe) != null }
            .forEach { entry ->
                val model = build(entry)
                val week = model.typicalWeek
                assertTrue("${entry.id}: tiene receta y debe traer su semana tipo, trae $week", week is TypicalWeek.Days)
                lines(model).forEach { line ->
                    assertFalse("${entry.id}: «$line» tiene un ejercicio sin nombre", line.contains(PlanInfoModelBuilder.UNKNOWN_EXERCISE))
                }
            }
    }

    @Test
    fun every_entry_without_a_recipe_is_generated_or_blank() {
        PersonalizedPlanCatalog.entries()
            .filter { (it.recipe ?: it.template?.recipe) == null }
            .forEach { entry ->
                val week = build(entry).typicalWeek
                if (entry.source == CatalogSource.NATIVE) {
                    assertEquals("${entry.id}", TypicalWeek.Generated(PlanInfoModelBuilder.GENERATED_WEEK_TEXT), week)
                } else {
                    assertEquals("${entry.id}", TypicalWeek.Unavailable, week)
                }
            }
    }

    // ─── Utilidades de los tests ─────────────────────────────────────────────

    private fun allTexts(model: PlanInfoModel): List<String> = buildList {
        add(model.displayName)
        add(model.provenanceLabel)
        add(model.levelLabel)
        model.kindLabel?.let { add(it) }
        add(model.summary)
        when (val week = model.typicalWeek) {
            is TypicalWeek.Generated -> add(week.text)
            is TypicalWeek.Days -> {
                week.caption?.let { add(it) }
                week.days.forEach { day ->
                    add(day.label)
                    addAll(day.lines)
                }
            }
            TypicalWeek.Unavailable -> Unit
        }
        addAll(model.material)
        model.source?.let { source ->
            source.attributionLine?.let { add(it) }
            source.urlLabel?.let { add(it) }
            source.editionLine?.let { add(it) }
            source.authoredRules?.let { addAll(it) }
            source.kpknDefaults?.let { addAll(it) }
        }
        addAll(model.notes)
        model.glossary.forEach { entry ->
            add(entry.title)
            add(entry.definition)
        }
        model.primaryActionLabel?.let { add(it) }
    }

    private companion object {
        /** Lo que un texto de usuario no puede llevar (la dirección del enlace no es texto: no se revisa). */
        val FORBIDDEN: Map<String, Regex> = mapOf(
            "un guion bajo" to Regex("_"),
            "un id de regla w*" to Regex("""\b[wW]\d"""),
            "un código de regla H*" to Regex("""\bH\d"""),
            "configurationId" to Regex("configurationId", RegexOption.IGNORE_CASE),
            "«No afiliado a KPKN»" to Regex("No afiliado a KPKN", RegexOption.IGNORE_CASE),
            "un id con prefijo (palabra:palabra)" to Regex("""[A-Za-z]:[A-Za-z]"""),
        )
    }
}
