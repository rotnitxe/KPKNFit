package com.example.kpkn.domain.training

import com.example.kpkn.data.models.ApparatusPresence
import com.example.kpkn.data.models.EquipmentAvailability
import com.example.kpkn.data.models.EquipmentCategory
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
 * Lote BW-1 de peso corporal (catálogo 215/539): qué soporte pide cada alta y que el equipo que acreditan los símbolos del
 * paso de material (resolutor único y filtro único de los paquetes E y E2) las alcanza o no según corresponda.
 *
 * Regla STOP: solo se usan requisitos que `supportRequirementsFor` ya conoce (`support`, `pull_up_bar`); ninguna alta
 * inventa un soporte nuevo. Las altas cuyo implemento es `bodyweight` están siempre acreditadas, así que el requisito de
 * soporte es lo único que impide ofrecerlas a quien no tiene dónde apoyarse.
 *
 * Lo que acreditan las anillas (`trx`, `rings`) y el cajón (`plyo_box`) lo fijan `SYMBOL_EQUIPMENT_KEYS` y
 * `EffectiveEquipmentResolverContractTest`; aquí solo se comprueba la parte de BW-1: el cajón es además un apoyo elevado
 * (`support`) y las anillas no lo son.
 */
class BodyweightLotEquipmentTest {

    private val configurations: Map<String, ExerciseConfigurationV2> by lazy {
        CatalogCompositionTestSupport.catalog.families
            .flatMap { it.definitions }
            .flatMap { it.configurations }
            .associateBy { it.id }
    }

    /** Altas que necesitan un apoyo elevado estable (banco, cajón, escalón o sofá). */
    private val elevatedSupport = listOf(
        "pike_push_up__feet_elevated",
        "step_up__bodyweight",
        "bulgarian_split_squat__bodyweight",
    )

    /** Altas que se hacen en el suelo, de pie o contra una pared: no piden ningún soporte. */
    private val noSupport = listOf(
        "pike_push_up__flat",
        "diamond_push_up__default",
        "archer_push_up__default",
        "dead_bug__default",
        "bird_dog__default",
        "side_plank__default",
        "hollow_body_hold__default",
        "wall_sit__default",
        "forward_lunge__bodyweight",
        "walking_lunge__bodyweight",
        "sumo_squat__bodyweight",
        "sissy_squat__bodyweight",
        "romanian_deadlift__unilateral__bodyweight",
        "good_morning__bilateral__bodyweight",
    )

    private val suspensionConfigurations = setOf(
        "biceps_curl_trx__supinated",
        "quads_sentadilla_pistola_asistida_trx__default",
        "triceps_extension__default",
    )

    private val home = setOf(TrainingPlace.HOME)

    private fun availabilityOf(
        vararg symbols: EquipmentSymbolId,
        places: Set<TrainingPlace> = home,
    ): EquipmentAvailability = EquipmentSymbols.availabilityOf(symbols.toSet(), places)

    private fun tokensOf(availability: EquipmentAvailability): Set<String> =
        TrainingOptions(availability = availability).effectiveEquipment(emptySet())

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

    @Test
    fun every_bw1_addition_exists_as_an_approved_bodyweight_configuration() {
        (elevatedSupport + noSupport + "negative_pull_up__default").forEach { id ->
            val configuration = configurations[id]
            assertNotNull("el catálogo no trae «$id»", configuration)
            assertEquals("«$id» debe ser de peso corporal", "bodyweight", configuration!!.profile.equipmentId)
            assertEquals("«$id» debe estar aprobada", CatalogReviewStatusV2.APPROVED, configuration.evidence.reviewStatus)
        }
    }

    @Test
    fun the_negative_pull_up_needs_the_pull_up_bar() {
        assertEquals(setOf(REQUIREMENT_PULL_UP_BAR), supportRequirementsFor("negative_pull_up__default"))
    }

    @Test
    fun the_elevated_support_additions_ask_for_the_elevated_support() {
        elevatedSupport.forEach { id ->
            assertEquals("«$id»", setOf(REQUIREMENT_SUPPORT), supportRequirementsFor(id))
        }
    }

    @Test
    fun the_other_bw1_additions_ask_for_no_support() {
        noSupport.forEach { id ->
            assertTrue("«$id» no debería pedir soporte", supportRequirementsFor(id).isEmpty())
        }
    }

    @Test
    fun a_person_with_only_the_body_reaches_the_floor_additions_but_not_the_support_dependent_ones() {
        val bodyOnly = EquipmentAvailability()
        assertEquals(setOf("bodyweight"), tokensOf(bodyOnly))
        noSupport.forEach { id -> assertTrue("«$id» debe alcanzarse sin material", reachable(id, bodyOnly)) }
        (elevatedSupport + "negative_pull_up__default").forEach { id ->
            assertFalse("«$id» no debe ofrecerse sin soporte", reachable(id, bodyOnly))
        }
    }

    @Test
    fun the_box_is_an_elevated_support_and_reaches_the_three_additions_that_ask_for_one() {
        val availability = availabilityOf(EquipmentSymbolId.BOX)
        val result = TrainingOptions(availability = availability).resolveEffectiveEquipment(emptySet())
        assertTrue("el cajón acredita su propio token", SymbolEquipmentKeys.PLYO_BOX in result.tokens)
        assertTrue("y es un apoyo elevado", REQUIREMENT_SUPPORT in result.tokens)
        assertEquals(RequirementEvidence.PRESENT, result.requirements[REQUIREMENT_SUPPORT])
        assertEquals(EffectiveEquipmentOrigin.CONFIRMED_SUPPORT, result.origins[REQUIREMENT_SUPPORT])
        elevatedSupport.forEach { id -> assertTrue("«$id» con cajón", reachable(id, availability)) }
        assertFalse("el cajón no acredita la barra de dominadas", reachable("negative_pull_up__default", availability))
        assertFalse("el cajón no acredita la suspensión", "trx" in result.tokens)
    }

    @Test
    fun the_bench_symbol_also_reaches_the_elevated_support_additions() {
        val availability = availabilityOf(EquipmentSymbolId.BENCH)
        elevatedSupport.forEach { id -> assertTrue("«$id» con banco", reachable(id, availability)) }
    }

    @Test
    fun the_pull_up_bar_symbol_reaches_the_negative_pull_up() {
        val availability = availabilityOf(EquipmentSymbolId.PULL_UP_BAR, places = setOf(TrainingPlace.PUBLIC))
        assertTrue(reachable("negative_pull_up__default", availability))
        // La barra sola no es un apoyo elevado: la pica con los pies elevados sigue pidiendo banco, cajón o escalón.
        assertFalse(reachable("pike_push_up__feet_elevated", availability))
    }

    @Test
    fun the_rings_open_the_three_suspension_configurations_but_are_not_an_elevated_support() {
        val availability = availabilityOf(EquipmentSymbolId.RINGS)
        val tokens = tokensOf(availability)
        assertTrue("trx" in tokens && "rings" in tokens)
        suspensionConfigurations.forEach { id ->
            assertEquals("el implemento de «$id»", "trx", configurations.getValue(id).profile.equipmentId)
            assertTrue("«$id» debe ser alcanzable con anillas", reachable(id, availability))
        }
        // Unas anillas colgadas no son un apoyo elevado.
        assertFalse(REQUIREMENT_SUPPORT in tokens)
        elevatedSupport.forEach { id -> assertFalse("«$id» no se alcanza solo con anillas", reachable(id, availability)) }
        // Y sin ellas no hay suspensión: ni el cajón, ni el banco, ni el parque la acreditan.
        listOf(
            availabilityOf(EquipmentSymbolId.BOX),
            availabilityOf(EquipmentSymbolId.BENCH),
            availabilityOf(EquipmentSymbolId.PULL_UP_BAR, EquipmentSymbolId.PARALLEL_BARS, places = setOf(TrainingPlace.PUBLIC)),
        ).forEach { other ->
            suspensionConfigurations.forEach { id -> assertFalse("«$id» sin anillas", reachable(id, other)) }
        }
    }

    @Test
    fun the_gym_seed_brings_the_box_and_so_the_elevated_support_but_not_the_rings() {
        val gym = setOf(TrainingPlace.GYM)
        val seeded = EquipmentSymbols.availabilityOf(EquipmentSymbols.seedFor(gym), gym)
        val tokens = tokensOf(seeded)
        assertTrue(SymbolEquipmentKeys.PLYO_BOX in tokens && REQUIREMENT_SUPPORT in tokens)
        assertFalse("trx" in tokens)
        elevatedSupport.forEach { id -> assertTrue("«$id» en el gimnasio", reachable(id, seeded)) }
    }

    @Test
    fun the_box_key_needs_the_support_category_and_a_present_answer_to_be_an_elevated_support() {
        val boxKey = SymbolEquipmentKeys.PLYO_BOX
        // La llave sola, sin la categoría de soportes confirmada, no acredita nada.
        val withoutCategory = tokensOf(
            EquipmentAvailability(
                categories = setOf(EquipmentCategory.DUMBBELLS),
                supports = mapOf(boxKey to ApparatusPresence.PRESENT),
            ),
        )
        assertFalse(boxKey in withoutCategory)
        assertFalse(REQUIREMENT_SUPPORT in withoutCategory)

        // Con la categoría pero con la llave negada o sin confirmar tampoco: se parte de lo que el wizard escribe al elegir
        // el símbolo y solo se cambia la respuesta de su propia llave.
        listOf(ApparatusPresence.ABSENT, ApparatusPresence.UNKNOWN).forEach { presence ->
            val box = availabilityOf(EquipmentSymbolId.BOX)
            val tokens = tokensOf(box.copy(supports = box.supports + (boxKey to presence)))
            assertFalse("plyo_box $presence", boxKey in tokens)
            assertFalse("plyo_box $presence", REQUIREMENT_SUPPORT in tokens)
        }
    }
}
