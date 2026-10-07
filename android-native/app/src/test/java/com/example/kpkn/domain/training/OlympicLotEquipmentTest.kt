package com.example.kpkn.domain.training

import com.example.kpkn.data.models.EquipmentAvailability
import com.example.kpkn.domain.exercises.catalogv2.CatalogReviewStatusV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseConfigurationV2
import com.example.kpkn.domain.onboarding.EquipmentSymbolId
import com.example.kpkn.domain.onboarding.EquipmentSymbols
import com.example.kpkn.domain.onboarding.TrainingPlace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Lote OL-1 de halterofilia y acarreos (catálogo 228/553): qué soporte pide cada alta y que el equipo que acreditan los
 * símbolos del paso de material (resolutor único y filtro único de los paquetes E y E2) las alcanza o no según corresponda.
 *
 * Regla STOP: solo se usa el requisito `rack`, que `supportRequirementsFor` ya conocía; ninguna alta inventa un soporte.
 * Los discos de goma y la plataforma que piden las cargadas y los arranques para soltar la barra no tienen símbolo ni llave:
 * no se exigen ni se acreditan, y el informe del lote lo deja dicho.
 */
class OlympicLotEquipmentTest {

    private val configurations: Map<String, ExerciseConfigurationV2> by lazy {
        CatalogCompositionTestSupport.catalog.families
            .flatMap { it.definitions }
            .flatMap { it.configurations }
            .associateBy { it.id }
    }

    /** Se levantan del suelo con la barra: no piden ningún soporte. */
    private val floorLifts = listOf(
        "power_clean__barbell",
        "hang_power_clean__barbell",
        "squat_clean__barbell",
        "power_snatch__barbell",
        "hang_power_snatch__barbell",
        "squat_snatch__barbell",
        "clean_pull__barbell",
        "snatch_pull__barbell",
    )

    /** Arrancan de un soporte (rack frontal o sobre la cabeza): piden rack. */
    private val rackLifts = listOf(
        "push_jerk__barbell",
        "split_jerk__barbell",
        "overhead_squat__barbell",
    )

    private val zercherCarry = "zercher_carry__barbell"
    private val suitcaseDumbbells = "suitcase_carry__dumbbells"
    private val suitcaseKettlebell = "suitcase_carry__kettlebell"

    private val barbellConfigurations = floorLifts + rackLifts + zercherCarry

    private val home = setOf(TrainingPlace.HOME)

    private val gymWithoutBarbell = EquipmentProfile(
        "gimnasio sin barra", setOf(TrainingPlace.GYM), EquipmentSymbols.seedFor(setOf(TrainingPlace.GYM)) - EquipmentSymbolId.BARBELL,
    )

    private fun availabilityOf(
        vararg symbols: EquipmentSymbolId,
        places: Set<TrainingPlace> = home,
    ): EquipmentAvailability = EquipmentSymbols.availabilityOf(symbols.toSet(), places)

    /** El filtro único del planificador y del generador, con el equipo que resuelve [availability]. */
    private fun reachable(configurationId: String, availability: EquipmentAvailability): Boolean {
        val options = TrainingOptions(availability = availability)
        return ConfigurationEquipmentFilter.allows(
            configuration = configurations.getValue(configurationId),
            tokens = options.effectiveEquipment(emptySet()),
            family = null,
            requireExactMachineConfiguration = ConfigurationEquipmentFilter.requiresExactMachineConfiguration(options),
        )
    }

    private fun reachableFrom(ids: Collection<String>, availability: EquipmentAvailability): Set<String> =
        ids.filterTo(linkedSetOf()) { reachable(it, availability) }

    @Test
    fun every_ol1_addition_exists_as_an_approved_configuration_with_its_implement() {
        barbellConfigurations.forEach { id ->
            val configuration = configurations[id]
            assertNotNull("el catálogo no trae «$id»", configuration)
            assertEquals("«$id» debe ser de barra", "barbell", configuration!!.profile.equipmentId)
            assertEquals("«$id» debe estar aprobada", CatalogReviewStatusV2.APPROVED, configuration.evidence.reviewStatus)
        }
        assertEquals("dumbbells", configurations.getValue(suitcaseDumbbells).profile.equipmentId)
        assertEquals("kettlebell", configurations.getValue(suitcaseKettlebell).profile.equipmentId)
        listOf(suitcaseDumbbells, suitcaseKettlebell).forEach { id ->
            assertEquals(CatalogReviewStatusV2.APPROVED, configurations.getValue(id).evidence.reviewStatus)
        }
    }

    @Test
    fun only_the_jerks_and_the_overhead_squat_ask_for_a_rack() {
        rackLifts.forEach { id ->
            assertEquals("«$id»", setOf(REQUIREMENT_RACK), supportRequirementsFor(id))
        }
        (floorLifts + zercherCarry + suitcaseDumbbells + suitcaseKettlebell).forEach { id ->
            assertTrue("«$id» no debería pedir soporte", supportRequirementsFor(id).isEmpty())
        }
    }

    @Test
    fun a_barbell_alone_opens_the_floor_lifts_and_the_zercher_carry_but_not_the_rack_lifts() {
        val barbellAtHome = availabilityOf(EquipmentSymbolId.BARBELL)
        assertEquals((floorLifts + zercherCarry).toSet(), reachableFrom(barbellConfigurations, barbellAtHome))
    }

    @Test
    fun a_rack_opens_the_jerks_and_the_overhead_squat() {
        val barbellAndRack = availabilityOf(EquipmentSymbolId.BARBELL, EquipmentSymbolId.RACK)
        assertEquals(barbellConfigurations.toSet(), reachableFrom(barbellConfigurations, barbellAndRack))
        // Un rack sin barra no abre nada de este lote.
        assertTrue(reachableFrom(barbellConfigurations, availabilityOf(EquipmentSymbolId.RACK)).isEmpty())
    }

    @Test
    fun the_full_gym_opens_every_barbell_addition_and_a_gym_without_a_rack_loses_only_the_rack_lifts() {
        assertEquals(
            barbellConfigurations.toSet(),
            reachableFrom(barbellConfigurations, EquipmentProfiles.gymFull.availability),
        )
        assertEquals(
            (floorLifts + zercherCarry).toSet(),
            reachableFrom(barbellConfigurations, EquipmentProfiles.gymWithoutRack.availability),
        )
    }

    @Test
    fun nothing_without_a_barbell_reaches_the_barbell_additions() {
        listOf(
            EquipmentProfiles.bodyOnly.availability,
            EquipmentProfiles.parkSeed.availability,
            EquipmentProfiles.homeDumbbellsBench.availability,
            EquipmentProfiles.homeRingsBox.availability,
            gymWithoutBarbell.availability,
        ).forEach { availability ->
            assertTrue(reachableFrom(barbellConfigurations, availability).isEmpty())
        }
    }

    @Test
    fun the_suitcase_carry_follows_the_weight_the_person_has() {
        val dumbbells = availabilityOf(EquipmentSymbolId.DUMBBELLS)
        val kettlebell = availabilityOf(EquipmentSymbolId.KETTLEBELL)
        assertTrue(reachable(suitcaseDumbbells, dumbbells))
        assertFalse(reachable(suitcaseKettlebell, dumbbells))
        assertTrue(reachable(suitcaseKettlebell, kettlebell))
        assertFalse(reachable(suitcaseDumbbells, kettlebell))
        listOf(EquipmentProfiles.bodyOnly, EquipmentProfiles.parkSeed).forEach { profile ->
            assertFalse(reachable(suitcaseDumbbells, profile.availability))
            assertFalse(reachable(suitcaseKettlebell, profile.availability))
        }
        // El gimnasio completo trae mancuernas y kettlebells: abre las dos.
        assertTrue(reachable(suitcaseDumbbells, EquipmentProfiles.gymFull.availability))
        assertTrue(reachable(suitcaseKettlebell, EquipmentProfiles.gymFull.availability))
    }

    @Test
    fun the_modeled_difficulties_stay_under_the_approved_ceiling_and_keep_the_olympic_lifts_out_of_beginner_plans() {
        assertEquals(barbellConfigurations.size + 2, modeledDifficulty.size)
        modeledDifficulty.forEach { (id, expected) ->
            val difficulty = configurations.getValue(id).profile.technicalDifficulty
            assertEquals("«$id»", expected, difficulty, 0.0)
            assertTrue("«$id» ($difficulty) supera el tope aprobado del catálogo", difficulty <= APPROVED_CEILING)
        }
        // El planificador y el generador no arrancan a un novato por encima de 5,2: los levantamientos olímpicos (y la
        // sentadilla de arranque) quedan fuera; los dos acarreos, no.
        (floorLifts + rackLifts).forEach { id ->
            assertTrue("«$id» debe quedar por encima del tope de los novatos", configurations.getValue(id).profile.technicalDifficulty > NOVICE_CEILING)
        }
        listOf(zercherCarry, suitcaseDumbbells, suitcaseKettlebell).forEach { id ->
            assertTrue("«$id» es apta para novatos", configurations.getValue(id).profile.technicalDifficulty <= NOVICE_CEILING)
        }
        // La cargada y el arranque completos son los más técnicos del catálogo, a la par del tope aprobado.
        assertEquals(APPROVED_CEILING, configurations.getValue("squat_clean__barbell").profile.technicalDifficulty, 0.0)
        assertEquals(APPROVED_CEILING, configurations.getValue("squat_snatch__barbell").profile.technicalDifficulty, 0.0)
    }

    private companion object {
        /** Tope de dificultad técnica que ya tiene el catálogo aprobado (la familia del press de banca). */
        const val APPROVED_CEILING = 7.0

        /** Por encima de esto el planificador y el generador no arrancan a un novato (salvo los básicos de la reserva). */
        const val NOVICE_CEILING = 5.2

        /**
         * Dificultad técnica modelada del lote (sección 4 del informe): puntuación editorial entre las anclas aprobadas 4,5 (paseo
         * del granjero), 5,0, 6,0, 6,8 y el tope 7,0; 6,5 es el punto medio de 6,0 y 6,8, como la de la flexión arquero de BW-1.
         */
        val modeledDifficulty = mapOf(
            "power_clean__barbell" to 6.5,
            "hang_power_clean__barbell" to 6.0,
            "squat_clean__barbell" to 7.0,
            "power_snatch__barbell" to 6.8,
            "hang_power_snatch__barbell" to 6.5,
            "squat_snatch__barbell" to 7.0,
            "clean_pull__barbell" to 6.0,
            "snatch_pull__barbell" to 6.0,
            "push_jerk__barbell" to 6.0,
            "split_jerk__barbell" to 6.5,
            "overhead_squat__barbell" to 6.0,
            "zercher_carry__barbell" to 5.0,
            "suitcase_carry__dumbbells" to 4.5,
            "suitcase_carry__kettlebell" to 4.5,
        )
    }
}
