package com.example.kpkn.domain.training

import com.example.kpkn.data.protocols.definitions.NativeDayArchetype
import com.example.kpkn.data.protocols.definitions.NativeProfileCalendars
import com.example.kpkn.data.protocols.definitions.NativeProfileKind
import com.example.kpkn.data.protocols.definitions.NativeSlotKey
import com.example.kpkn.data.splits.SPLIT_TEMPLATES
import com.example.kpkn.data.splits.SplitPublicationStatus
import com.example.kpkn.data.splits.SplitTag
import com.example.kpkn.data.splits.SplitTemplate
import com.example.kpkn.data.splits.isVisibleForApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Paquete A · E2 (D6, DEC-w2-04 parte 2): la tabla «calendario propio ↔ reparto equivalente».
 *
 * Fija tres cosas, todas sin generar un solo programa (JVM puro):
 *  1. la tabla exacta del plan de curaduría (perfil × días × tirón);
 *  2. que cada testigo es un reparto real del catálogo de repartos, visible, publicado por KPKN y con los días que dice;
 *  3. que la tabla no se contradice con los calendarios reales de `NativeProfileCalendars`: el día i del plan es del
 *     tipo que dice la etiqueta i del reparto (torso, pierna, empuje, tirón, cuerpo completo o SBD).
 *
 * Que el generador acepte el testigo y rechace cualquier otro reparto lo prueba `OwnPlanPrioritiesAndSplitTest`.
 */
class NativeProfileSplitWitnessTest {

    private data class Row(val profile: NativeProfileKind, val hasPull: Boolean, val byDays: List<String?>)

    /** La tabla del plan 00 §6 (fila E2) en forma de cuadro: el elemento i de [Row.byDays] es el de i + 1 días. */
    private val table = listOf(
        Row(NativeProfileKind.MUSCLE, true, listOf(null, "minimalist_x2", "fullbody_x3", "ul_x4", "ppl_ul", "ppl_x6")),
        Row(NativeProfileKind.MUSCLE, false, listOf(null, "minimalist_x2", "fullbody_x3", "ul_x4", null, "ul_x6")),
        Row(NativeProfileKind.POWERBUILDING, true, listOf(null, "minimalist_x2", "fullbody_x3", "ul_x4", "ppl_ul", "ppl_x6")),
        Row(NativeProfileKind.POWERBUILDING, false, listOf(null, "minimalist_x2", "fullbody_x3", "ul_x4", "ppl_ul", "ppl_x6")),
        Row(NativeProfileKind.STRENGTH, true, listOf(null, null, "pl_sbd_x3", null, null, null)),
        Row(NativeProfileKind.STRENGTH, false, listOf(null, null, "pl_sbd_x3", null, null, null)),
        Row(NativeProfileKind.COMPLETE_ATHLETE, true, listOf(null, null, null, null, null, null)),
        Row(NativeProfileKind.COMPLETE_ATHLETE, false, listOf(null, null, null, null, null, null)),
    )

    private fun template(id: String): SplitTemplate = checkNotNull(SPLIT_TEMPLATES.firstOrNull { it.id == id }) {
        "el reparto $id no existe en el catálogo de repartos"
    }

    private fun trainingLabels(template: SplitTemplate, startDay: Int = 1): List<String> =
        SplitApplicationEngine.patternToTrainingDays(template.pattern, startDay).map { it.label }

    // ─── 1. La tabla ───────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun the_table_is_exactly_the_one_of_the_plan() {
        table.forEach { row ->
            row.byDays.forEachIndexed { index, expected ->
                val days = index + 1
                assertEquals(
                    "${row.profile} con $days días y ${if (row.hasPull) "con" else "sin"} tirón",
                    expected,
                    NativeProfileSplitWitness.witnessSplitId(row.profile, days, row.hasPull),
                )
            }
        }
    }

    @Test
    fun the_default_assumes_pull_is_available_and_only_muscle_depends_on_it() {
        NativeProfileKind.entries.forEach { profile ->
            (1..6).forEach { days ->
                assertEquals(
                    "$profile/$days: el valor por defecto es «con tirón»",
                    NativeProfileSplitWitness.witnessSplitId(profile, days, hasPull = true),
                    NativeProfileSplitWitness.witnessSplitId(profile, days),
                )
                if (profile != NativeProfileKind.MUSCLE) {
                    assertEquals(
                        "$profile/$days: el tirón no cambia su calendario",
                        NativeProfileSplitWitness.witnessSplitId(profile, days, hasPull = true),
                        NativeProfileSplitWitness.witnessSplitId(profile, days, hasPull = false),
                    )
                }
            }
        }
        // Fuera de 1..6 días no hay calendario propio, y por tanto tampoco reparto.
        NativeProfileKind.entries.forEach { profile ->
            listOf(0, 7, 8, -1).forEach { days -> assertNull("$profile/$days", NativeProfileSplitWitness.witnessSplitId(profile, days)) }
        }
    }

    @Test
    fun the_athlete_and_the_one_day_calendars_never_have_an_equivalent_split() {
        listOf(true, false).forEach { pull ->
            (1..6).forEach { days ->
                assertNull("Atleta/$days", NativeProfileSplitWitness.witnessSplitId(NativeProfileKind.COMPLETE_ATHLETE, days, pull))
            }
            NativeProfileKind.entries.forEach { profile ->
                assertNull("$profile con 1 día no tiene reparto de un solo día", NativeProfileSplitWitness.witnessSplitId(profile, 1, pull))
            }
        }
    }

    @Test
    fun accepts_is_true_only_for_the_witness() {
        assertTrue(NativeProfileSplitWitness.accepts(NativeProfileKind.MUSCLE, 4, true, "ul_x4"))
        assertFalse(NativeProfileSplitWitness.accepts(NativeProfileKind.MUSCLE, 4, true, "ant_post_x4"))
        assertFalse(NativeProfileSplitWitness.accepts(NativeProfileKind.MUSCLE, 6, true, "ul_x6"))
        assertTrue(NativeProfileSplitWitness.accepts(NativeProfileKind.MUSCLE, 6, false, "ul_x6"))
        assertFalse("sin testigo no se acepta nada", NativeProfileSplitWitness.accepts(NativeProfileKind.COMPLETE_ATHLETE, 4, true, "ul_x4"))
    }

    @Test
    fun the_list_of_witnesses_is_derived_from_the_function_and_has_no_duplicates() {
        val witnesses = NativeProfileSplitWitness.allWitnesses()
        // Fuerza 1 (3 días) + Músculo (2, 3, 4 comunes; 5 con tirón; 6 con y sin tirón) + Fuerza y músculo (2..6).
        assertEquals(1 + (3 + 1 + 2) + 5, witnesses.size)
        assertEquals("sin duplicados", witnesses.size, witnesses.distinct().size)
        witnesses.forEach { witness ->
            val pulls = witness.pull?.let { listOf(it) } ?: listOf(true, false)
            pulls.forEach { pull ->
                assertEquals(
                    "$witness",
                    witness.splitId,
                    NativeProfileSplitWitness.witnessSplitId(witness.profile, witness.days, pull),
                )
            }
        }
        // Y al revés: todo par con reparto equivalente está en la lista.
        NativeProfileKind.entries.forEach { profile ->
            (1..6).forEach { days ->
                listOf(true, false).forEach { pull ->
                    val id = NativeProfileSplitWitness.witnessSplitId(profile, days, pull) ?: return@forEach
                    assertTrue(
                        "$profile/$days/pull=$pull ($id) falta en la lista",
                        witnesses.any { it.profile == profile && it.days == days && it.splitId == id && (it.pull == null || it.pull == pull) },
                    )
                }
            }
        }
        assertEquals(
            "los pares que dependen del tirón son solo los de Músculo con 5 y 6 días",
            listOf(
                NativeProfileSplitWitness.Witness(NativeProfileKind.MUSCLE, 5, true, "ppl_ul"),
                NativeProfileSplitWitness.Witness(NativeProfileKind.MUSCLE, 6, true, "ppl_x6"),
                NativeProfileSplitWitness.Witness(NativeProfileKind.MUSCLE, 6, false, "ul_x6"),
            ),
            witnesses.filter { it.pull != null },
        )
    }

    // ─── 2. Cada testigo es un reparto real, visible y con los días que dice ───────────────────────────────

    @Test
    fun every_witness_is_a_published_visible_split_with_the_days_it_claims() {
        NativeProfileSplitWitness.allWitnesses().forEach { witness ->
            val split = template(witness.splitId)
            assertTrue("${witness.splitId} debe ser visible para la aplicación", split.isVisibleForApplication)
            assertEquals(
                "${witness.splitId} lo publica KPKN (receta verificada día por día)",
                SplitPublicationStatus.KPKN_NATIVE,
                split.publicationStatus,
            )
            // El número de días de entrenamiento no depende del día de inicio de la semana.
            (1..7).forEach { startDay ->
                assertEquals(
                    "${witness.splitId} con inicio en el día $startDay",
                    witness.days,
                    trainingLabels(split, startDay).size,
                )
            }
            assertTrue("${witness.splitId} no puede ser el reparto en blanco", witness.splitId != "custom")
        }
    }

    @Test
    fun only_the_strength_witness_is_a_powerlifting_split_so_hiding_them_outside_strength_never_hides_a_witness() {
        NativeProfileSplitWitness.allWitnesses().forEach { witness ->
            val isPowerlifting = SplitTag.POWERLIFTING in template(witness.splitId).tags
            assertEquals(
                "${witness.profile}/${witness.days}: ${witness.splitId} es de powerlifting solo en Fuerza",
                witness.profile == NativeProfileKind.STRENGTH,
                isPowerlifting,
            )
        }
    }

    // ─── 3. La tabla coincide con los calendarios reales ───────────────────────────────────────────────────

    private val lower = setOf(NativeSlotKey.S, NativeSlotKey.D, NativeSlotKey.U, NativeSlotKey.G, NativeSlotKey.BG, NativeSlotKey.PS)
    private val push = setOf(NativeSlotKey.B, NativeSlotKey.O, NativeSlotKey.L, NativeSlotKey.T, NativeSlotKey.PB)
    private val pull = setOf(NativeSlotKey.R, NativeSlotKey.V, NativeSlotKey.A)
    private val sbd = setOf(NativeSlotKey.S, NativeSlotKey.B, NativeSlotKey.D)

    /** El core (C) y la extensión de espalda (SM) no deciden de qué tipo es un día. */
    private fun decisive(keys: List<NativeSlotKey>): List<NativeSlotKey> = keys.filter { it in lower || it in push || it in pull }

    private fun share(keys: List<NativeSlotKey>, group: Set<NativeSlotKey>): Double {
        val relevant = decisive(keys)
        return if (relevant.isEmpty()) 0.0 else relevant.count { it in group }.toDouble() / relevant.size
    }

    /**
     * ¿La etiqueta [label] del reparto describe un día con estos slots? «Torso», «Pierna», «Empuje» y «Tirón» exigen que
     * al menos el 75 % de los slots decisivos sean de ese tipo (el día de pierna sin tirón de Músculo cambia una
     * sentadilla por una flexión para no pasar del límite de glúteo, DEV-r2-01: 3 de 4 slots siguen siendo de pierna);
     * «Cuerpo completo» pide trabajo de pierna y de torso, y «SBD» al menos dos de sentadilla, banca y peso muerto.
     */
    private fun describes(label: String, keys: List<NativeSlotKey>): Boolean {
        val text = label.lowercase()
        val hasLower = keys.any { it in lower }
        val hasUpper = keys.any { it in push || it in pull }
        return when {
            "cuerpo completo" in text || "full body" in text -> hasLower && hasUpper
            "sbd" in text -> keys.count { it in sbd } >= 2
            "torso" in text -> share(keys, push + pull) >= 0.75
            "pierna" in text -> share(keys, lower) >= 0.75
            "empuje" in text -> share(keys, push) >= 0.75
            "tirón" in text -> share(keys, pull) >= 0.75
            else -> false
        }
    }

    private fun calendarOf(profile: NativeProfileKind, days: Int, hasPull: Boolean): List<NativeDayArchetype> = when (profile) {
        NativeProfileKind.STRENGTH -> NativeProfileCalendars.strength(days)
        NativeProfileKind.MUSCLE -> NativeProfileCalendars.muscle(days, hasPull)
        NativeProfileKind.POWERBUILDING -> NativeProfileCalendars.powerbuilding(days)
        NativeProfileKind.COMPLETE_ATHLETE -> NativeProfileCalendars.athlete(days, hasPull)
    }

    @Test
    fun every_witness_describes_the_real_calendar_day_by_day() {
        val problems = mutableListOf<String>()
        NativeProfileSplitWitness.allWitnesses().forEach { witness ->
            val labels = trainingLabels(template(witness.splitId))
            val pulls = witness.pull?.let { listOf(it) } ?: listOf(true, false)
            pulls.forEach { hasPull ->
                val calendar = calendarOf(witness.profile, witness.days, hasPull)
                val context = "${witness.profile}/${witness.days} días/${if (hasPull) "con" else "sin"} tirón/${witness.splitId}"
                if (calendar.size != labels.size) {
                    problems += "$context: el calendario tiene ${calendar.size} días y el reparto ${labels.size}"
                    return@forEach
                }
                calendar.forEachIndexed { index, archetype ->
                    val keys = archetype.slots.map { it.key }
                    if (!describes(labels[index], keys)) {
                        problems += "$context: el día ${index + 1} del plan (${archetype.name}: $keys) no es «${labels[index]}»"
                    }
                }
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun the_calendar_check_is_not_vacuous_a_wrong_split_fails_it() {
        // Músculo con 4 días es torso, pierna, torso, pierna: «Anterior / posterior» (cadenas) y «Empuje, tirón, pierna»
        // no lo describen. Si esta comprobación aceptara cualquier reparto de 4 días no detectaría una tabla mal hecha.
        val calendar = calendarOf(NativeProfileKind.MUSCLE, 4, true)
        val upperLower = trainingLabels(template("ul_x4"))
        val pushPull = trainingLabels(template("push_pull_x4"))
        val chains = trainingLabels(template("ant_post_x4"))
        assertTrue(calendar.indices.all { describes(upperLower[it], calendar[it].slots.map { slot -> slot.key }) })
        assertFalse(calendar.indices.all { describes(pushPull[it], calendar[it].slots.map { slot -> slot.key }) })
        assertFalse(calendar.indices.all { describes(chains[it], calendar[it].slots.map { slot -> slot.key }) })
    }
}
