package com.example.kpkn.domain.onboarding

import com.example.kpkn.data.models.ApparatusPresence
import com.example.kpkn.data.models.EquipmentAvailability
import com.example.kpkn.data.models.EquipmentCategory
import com.example.kpkn.domain.training.EFFECTIVE_EQUIPMENT_KEYS
import com.example.kpkn.domain.training.TrainingOptions
import com.example.kpkn.domain.training.effectiveEquipment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `EquipmentSymbols`: la traducción entre los símbolos de material del paso AVAILABILITY y la disponibilidad que lee
 * el motor. Lo que más importa es el **ida y vuelta exacto** (`selectedFrom(availabilityOf(S, lugares)) == S`): sin él
 * un símbolo apagado aparecería encendido (o al revés) al volver a pintar el paso.
 */
class EquipmentSymbolsTest {

    private val gym = setOf(TrainingPlace.GYM)
    private val home = setOf(TrainingPlace.HOME)
    private val park = setOf(TrainingPlace.PUBLIC)

    /** Todas las combinaciones no vacías de lugares. */
    private val everyPlaceCombination: List<Set<TrainingPlace>> = (1 until 8).map { mask ->
        TrainingPlace.entries.filterIndexed { index, _ -> mask and (1 shl index) != 0 }.toSet()
    }

    private fun visibleSymbols(places: Set<TrainingPlace>): List<EquipmentSymbolId> =
        EquipmentSymbols.symbolsFor(places) - EquipmentSymbolId.BODYWEIGHT_ONLY

    private fun roundTrip(selection: Set<EquipmentSymbolId>, places: Set<TrainingPlace>): Set<EquipmentSymbolId> =
        EquipmentSymbols.selectedFrom(EquipmentSymbols.availabilityOf(selection, places))

    // ── Ida y vuelta ───────────────────────────────────────────────────────────

    @Test
    fun roundTripIsExactForEverySelectionOfVisibleSymbolsInEveryPlaceCombination() {
        // Exhaustivo en las tres familias de comportamiento (con gimnasio, solo casa/parque con interiores, solo
        // espacios públicos) y por muestreo en el resto de combinaciones, que se comportan igual.
        val exhaustive = listOf(gym, home, park)
        for (places in everyPlaceCombination) {
            val visible = visibleSymbols(places)
            val total = 1 shl visible.size
            val stride = if (places in exhaustive) 1 else 37
            var mask = 0
            while (mask < total) {
                val selection = visible.filterIndexed { index, _ -> mask and (1 shl index) != 0 }.toSet()
                val expected = if (selection.isEmpty()) setOf(EquipmentSymbolId.BODYWEIGHT_ONLY) else selection
                assertEquals("lugares=$places selección=$selection", expected, roundTrip(selection, places))
                mask += stride
            }
        }
    }

    @Test
    fun bodyweightOnlyIsTheEmptySelectionInBothDirections() {
        for (places in everyPlaceCombination) {
            val viaExclusive = roundTrip(setOf(EquipmentSymbolId.BODYWEIGHT_ONLY), places)
            val viaEmpty = roundTrip(emptySet(), places)
            assertEquals(setOf(EquipmentSymbolId.BODYWEIGHT_ONLY), viaExclusive)
            assertEquals(setOf(EquipmentSymbolId.BODYWEIGHT_ONLY), viaEmpty)
            // El motor recibe categorías vacías confirmadas y ninguna llave: solo cuerpo.
            val availability = EquipmentSymbols.availabilityOf(setOf(EquipmentSymbolId.BODYWEIGHT_ONLY), places)
            assertEquals(EquipmentAvailability(), availability)
            assertTrue(EquipmentSymbols.isBodyweightOnly(availability))
        }
    }

    @Test
    fun jumpRopeAndCardioShareACategoryButTheRoundTripKeepsThemApart() {
        // Antes de la llave propia del cardio, elegir solo la cuerda de saltar devolvía también «Cardio».
        assertEquals(
            setOf(EquipmentSymbolId.JUMP_ROPE),
            roundTrip(setOf(EquipmentSymbolId.JUMP_ROPE), gym),
        )
        assertEquals(
            setOf(EquipmentSymbolId.CARDIO),
            roundTrip(setOf(EquipmentSymbolId.CARDIO), gym),
        )
        assertEquals(
            setOf(EquipmentSymbolId.CARDIO, EquipmentSymbolId.JUMP_ROPE),
            roundTrip(setOf(EquipmentSymbolId.CARDIO, EquipmentSymbolId.JUMP_ROPE), home),
        )
        val ropeOnly = EquipmentSymbols.availabilityOf(setOf(EquipmentSymbolId.JUMP_ROPE), gym)
        assertEquals(setOf(EquipmentCategory.CARDIO), ropeOnly.categories)
        assertEquals(ApparatusPresence.PRESENT, ropeOnly.presenceOf(EquipmentSymbols.JUMP_ROPE_KEY))
        assertEquals(ApparatusPresence.ABSENT, ropeOnly.presenceOf(EquipmentSymbols.CARDIO_MACHINE_KEY))
    }

    @Test
    fun symbolsThatShareTheSupportCategoryAreDistinguishedByTheirKeys() {
        val supports = listOf(
            EquipmentSymbolId.RACK, EquipmentSymbolId.BENCH, EquipmentSymbolId.PARALLEL_BARS,
            EquipmentSymbolId.RINGS, EquipmentSymbolId.BOX,
        )
        for (symbol in supports) {
            assertEquals(setOf(symbol), roundTrip(setOf(symbol), gym))
        }
        assertEquals(supports.toSet(), roundTrip(supports.toSet(), gym))
    }

    // ── Semilla y símbolos por lugar ───────────────────────────────────────────

    @Test
    fun seedsDependOnThePlace() {
        // Gimnasio: todo lo habitual (sin anillas, que no son «habituales»).
        assertEquals(
            EquipmentSymbols.selectable.toSet() - EquipmentSymbolId.RINGS,
            EquipmentSymbols.seedFor(gym),
        )
        // Casa no asume nada; un parque, su estructura de calistenia.
        assertTrue(EquipmentSymbols.seedFor(home).isEmpty())
        assertEquals(
            setOf(EquipmentSymbolId.PULL_UP_BAR, EquipmentSymbolId.PARALLEL_BARS),
            EquipmentSymbols.seedFor(park),
        )
        assertEquals(
            EquipmentSymbols.seedFor(gym) + EquipmentSymbols.seedFor(park),
            EquipmentSymbols.seedFor(gym + park),
        )
    }

    @Test
    fun symbolsOfferedFollowThePlacesAndAlwaysEndWithBodyweightOnly() {
        assertTrue(EquipmentSymbols.symbolsFor(emptySet()).isEmpty())
        for (places in everyPlaceCombination) {
            val offered = EquipmentSymbols.symbolsFor(places)
            assertEquals(EquipmentSymbolId.BODYWEIGHT_ONLY, offered.last())
            assertEquals(offered.size, offered.toSet().size)
        }
        // En un parque no hay rack, poleas, máquinas ni cardio de interior; sí barra de dominadas, paralelas y bandas.
        val outdoors = EquipmentSymbols.symbolsFor(park).toSet()
        assertFalse(EquipmentSymbolId.RACK in outdoors)
        assertFalse(EquipmentSymbolId.CABLE in outdoors)
        assertFalse(EquipmentSymbolId.MACHINES in outdoors)
        assertFalse(EquipmentSymbolId.CARDIO in outdoors)
        assertTrue(EquipmentSymbolId.PULL_UP_BAR in outdoors)
        assertTrue(EquipmentSymbolId.PARALLEL_BARS in outdoors)
        assertTrue(EquipmentSymbolId.BANDS in outdoors)
        // Todo lo que ofrece un lugar aparece en la unión.
        assertEquals(
            EquipmentSymbols.symbolsFor(gym).toSet() + EquipmentSymbols.symbolsFor(park),
            EquipmentSymbols.symbolsFor(gym + park).toSet(),
        )
    }

    // ── Reseed: añadir o quitar lugares ────────────────────────────────────────

    @Test
    fun addingAPlaceKeepsWhatWasChosenAndAddsTheNewPlacesSeed() {
        val chosen = setOf(EquipmentSymbolId.DUMBBELLS, EquipmentSymbolId.BANDS)
        val withPark = EquipmentSymbols.reseed(home, home + park, chosen)
        assertEquals(chosen + setOf(EquipmentSymbolId.PULL_UP_BAR, EquipmentSymbolId.PARALLEL_BARS), withPark)
    }

    @Test
    fun removingAPlaceDropsOnlyWhatNoRemainingPlaceOffers() {
        val selection = EquipmentSymbols.seedFor(gym) + setOf(EquipmentSymbolId.RINGS)
        val afterRemovingGym = EquipmentSymbols.reseed(gym + park, park, selection)
        // Lo de un parque sigue ofreciéndose; el rack, las poleas o las máquinas ya no.
        assertTrue(EquipmentSymbolId.PULL_UP_BAR in afterRemovingGym)
        assertTrue(EquipmentSymbolId.RINGS in afterRemovingGym)
        assertFalse(EquipmentSymbolId.RACK in afterRemovingGym)
        assertFalse(EquipmentSymbolId.MACHINES in afterRemovingGym)
        assertTrue(afterRemovingGym.all { it in EquipmentSymbols.symbolsFor(park) })
    }

    @Test
    fun addingAPlaceReopensABodyweightOnlyChoice() {
        val onlyBody = setOf(EquipmentSymbolId.BODYWEIGHT_ONLY)
        assertEquals(onlyBody, EquipmentSymbols.reseed(home, home, onlyBody))
        val reopened = EquipmentSymbols.reseed(home, home + gym, onlyBody)
        assertFalse(EquipmentSymbolId.BODYWEIGHT_ONLY in reopened)
        assertEquals(EquipmentSymbols.seedFor(gym), reopened)
    }

    @Test
    fun firstSelectionOfAPlaceIsItsSeed() {
        assertEquals(EquipmentSymbols.seedFor(gym), EquipmentSymbols.reseed(emptySet(), gym, emptySet()))
        assertTrue(EquipmentSymbols.reseed(emptySet(), home, emptySet()).isEmpty())
    }

    // ── Exclusividad ───────────────────────────────────────────────────────────

    @Test
    fun toggleKeepsBodyweightOnlyExclusive() {
        val withBarbell = EquipmentSymbols.toggle(emptySet(), EquipmentSymbolId.BARBELL)
        assertEquals(setOf(EquipmentSymbolId.BARBELL), withBarbell)
        // Elegir «solo peso corporal» vacía el resto…
        assertEquals(
            setOf(EquipmentSymbolId.BODYWEIGHT_ONLY),
            EquipmentSymbols.toggle(withBarbell, EquipmentSymbolId.BODYWEIGHT_ONLY),
        )
        // …y elegir un implemento lo retira.
        assertEquals(
            setOf(EquipmentSymbolId.DUMBBELLS),
            EquipmentSymbols.toggle(setOf(EquipmentSymbolId.BODYWEIGHT_ONLY), EquipmentSymbolId.DUMBBELLS),
        )
        // Alternar lo elegido lo apaga.
        assertTrue(EquipmentSymbols.toggle(withBarbell, EquipmentSymbolId.BARBELL).isEmpty())
    }

    // ── Lo que lee el motor ────────────────────────────────────────────────────

    @Test
    fun chosenSymbolsAreAcreditedAndTheVisibleOnesLeftOutAreExplicitlyAbsent() {
        val availability = EquipmentSymbols.availabilityOf(
            setOf(EquipmentSymbolId.BARBELL, EquipmentSymbolId.BENCH, EquipmentSymbolId.DUMBBELLS),
            home,
        )
        assertEquals(
            setOf(EquipmentCategory.BARBELL, EquipmentCategory.SUPPORT, EquipmentCategory.DUMBBELLS),
            availability.categories,
        )
        // El banco acredita el plano y el regulable; el rack, que se vio y no se eligió, queda ausente (no «sin confirmar»).
        assertEquals(ApparatusPresence.PRESENT, availability.presenceOf("bench_flat"))
        assertEquals(ApparatusPresence.PRESENT, availability.presenceOf("bench_adjustable"))
        assertEquals(ApparatusPresence.ABSENT, availability.presenceOf("squat_rack"))
        assertEquals(ApparatusPresence.ABSENT, availability.presenceOf("leg_press"))
        assertEquals(ApparatusPresence.ABSENT, availability.presenceOf("cable_high_low"))
    }

    @Test
    fun gymExtrasWithoutASymbolAreOnlyPresentWithAGymAndTheirParentSymbol() {
        val withGym = EquipmentSymbols.availabilityOf(
            setOf(
                EquipmentSymbolId.BARBELL, EquipmentSymbolId.RACK, EquipmentSymbolId.BENCH,
                EquipmentSymbolId.CABLE, EquipmentSymbolId.MACHINES,
            ),
            gym,
        )
        assertEquals(ApparatusPresence.PRESENT, withGym.presenceOf("ez_bar"))
        assertEquals(ApparatusPresence.PRESENT, withGym.presenceOf("low_bar_support"))
        assertEquals(ApparatusPresence.PRESENT, withGym.presenceOf("dual_cable"))
        assertEquals(ApparatusPresence.PRESENT, withGym.presenceOf("rope_attachment"))
        // El predicador es otro banco: cuenta con su categoría (SUPPORT) y la acredita el símbolo «Banco».
        assertEquals(ApparatusPresence.PRESENT, withGym.presenceOf("preacher_bench"))

        val atHome = EquipmentSymbols.availabilityOf(setOf(EquipmentSymbolId.BARBELL, EquipmentSymbolId.BENCH), home)
        assertEquals(ApparatusPresence.ABSENT, atHome.presenceOf("ez_bar"))
        assertEquals(ApparatusPresence.ABSENT, atHome.presenceOf("preacher_bench"))

        val withoutParent = EquipmentSymbols.availabilityOf(setOf(EquipmentSymbolId.DUMBBELLS), gym)
        assertEquals(ApparatusPresence.ABSENT, withoutParent.presenceOf("ez_bar"))
        assertEquals(ApparatusPresence.ABSENT, withoutParent.presenceOf("dual_cable"))
    }

    @Test
    fun everyKeyTheSymbolsWriteIsEitherACuratedEngineKeyOrAnOwnKey() {
        val own = setOf(
            EquipmentSymbols.RINGS_KEY, EquipmentSymbols.BOX_KEY,
            EquipmentSymbols.JUMP_ROPE_KEY, EquipmentSymbols.CARDIO_MACHINE_KEY,
        )
        val curated = EFFECTIVE_EQUIPMENT_KEYS.map { it.key }.toSet()
        for (places in everyPlaceCombination) {
            val all = EquipmentSymbols.availabilityOf(visibleSymbols(places).toSet(), places)
            for (key in all.apparatus.keys + all.supports.keys) {
                assertTrue("la llave «$key» no existe en el vocabulario del motor ni es propia", key in curated || key in own)
            }
        }
    }

    @Test
    fun theGymSeedReachesTheEngineWithTheEquipmentTokensItCurates() {
        val availability = EquipmentSymbols.availabilityOf(EquipmentSymbols.seedFor(gym), gym)
        val tokens = TrainingOptions(availability = availability).effectiveEquipment(emptySet())
        for (token in listOf(
            "bodyweight", "barbell", "dumbbells", "kettlebell", "machine", "cable", "smith_machine", "band",
            "pull_up_bar", "ball", "cardio", "support", "bench", "bench_incline", "rack", "dip_bars",
            "low_bar_support", "ez_bar",
        )) {
            assertTrue("el motor no acredita «$token» con el material de gimnasio", token in tokens)
        }
        // Las máquinas se acreditan por su configuración exacta, nunca por haber marcado «Máquinas».
        assertTrue(tokens.any { it.startsWith("machine_config:") })
    }

    @Test
    fun aHomeWithOnlyDumbbellsDoesNotReachBarbellOrRackTokens() {
        val availability = EquipmentSymbols.availabilityOf(setOf(EquipmentSymbolId.DUMBBELLS), home)
        val tokens = TrainingOptions(availability = availability).effectiveEquipment(emptySet())
        assertTrue("dumbbells" in tokens)
        assertFalse("barbell" in tokens)
        assertFalse("rack" in tokens)
        assertFalse("bench" in tokens)
        assertNull(availability.supports["squat_rack"]?.takeIf { it == ApparatusPresence.PRESENT })
    }

    // ── Lectura de disponibilidades que no salen de los símbolos ────────────────

    @Test
    fun aLegacyAvailabilityOfOnlyCategoriesReadsTheSymbolsWhoseCategoryIsTheirOwn() {
        val legacy = EquipmentAvailability(
            categories = setOf(
                EquipmentCategory.BARBELL, EquipmentCategory.DUMBBELLS, EquipmentCategory.MACHINES,
                EquipmentCategory.CABLE, EquipmentCategory.SUPPORT, EquipmentCategory.PULL_UP_BAR, EquipmentCategory.BAND,
            ),
        )
        // Máquinas, poleas y barra de dominadas tienen una categoría solo suya: la categoría declarada manda aunque
        // sus llaves no consten. Los soportes los comparten varios símbolos y no se adivina cuál es.
        assertEquals(
            setOf(
                EquipmentSymbolId.BARBELL, EquipmentSymbolId.DUMBBELLS, EquipmentSymbolId.MACHINES,
                EquipmentSymbolId.CABLE, EquipmentSymbolId.PULL_UP_BAR, EquipmentSymbolId.BANDS,
            ),
            EquipmentSymbols.selectedFrom(legacy),
        )
        // Con las llaves explícitamente ausentes, el símbolo no se elige aunque su categoría conste.
        val noMachinesInFact = legacy.copy(
            apparatus = mapOf("leg_press" to ApparatusPresence.ABSENT, "hack_squat" to ApparatusPresence.ABSENT),
        )
        assertFalse(EquipmentSymbolId.MACHINES in EquipmentSymbols.selectedFrom(noMachinesInFact))
        assertTrue(EquipmentSymbolId.CABLE in EquipmentSymbols.selectedFrom(noMachinesInFact))
    }

    @Test
    fun aPartialConfirmationOfSupportsReadsAsThoseSymbolsAndNothingMore() {
        val confirmed = EquipmentAvailability(
            categories = setOf(EquipmentCategory.SUPPORT),
            supports = mapOf("squat_rack" to ApparatusPresence.PRESENT, "bench_flat" to ApparatusPresence.PRESENT),
        )
        // Basta una llave presente: «sí, tengo rack y banco plano» ya es rack y banco.
        assertEquals(setOf(EquipmentSymbolId.RACK, EquipmentSymbolId.BENCH), EquipmentSymbols.selectedFrom(confirmed))
        // Un soporte con todas sus llaves ausentes no se elige, aunque la categoría conste por otro símbolo.
        val onlyRack = EquipmentAvailability(
            categories = setOf(EquipmentCategory.SUPPORT),
            supports = mapOf(
                "squat_rack" to ApparatusPresence.PRESENT,
                "bench_flat" to ApparatusPresence.ABSENT, "bench_adjustable" to ApparatusPresence.ABSENT,
            ),
        )
        assertEquals(setOf(EquipmentSymbolId.RACK), EquipmentSymbols.selectedFrom(onlyRack))
        // Sin ninguna llave presente y la categoría compartida, no se adivina.
        assertTrue(EquipmentSymbols.selectedFrom(EquipmentAvailability(categories = setOf(EquipmentCategory.SUPPORT))).isEmpty())
    }

    @Test
    fun nothingDeclaredReadsAsNoSelectionNotAsBodyweight() {
        assertTrue(EquipmentSymbols.selectedFrom(null).isEmpty())
        assertFalse(EquipmentSymbols.isBodyweightOnly(null))
    }
}
