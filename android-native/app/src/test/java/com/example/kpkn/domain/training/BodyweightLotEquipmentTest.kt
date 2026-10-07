package com.example.kpkn.domain.training

import com.example.kpkn.data.models.ApparatusPresence
import com.example.kpkn.data.models.EquipmentAvailability
import com.example.kpkn.data.models.EquipmentCategory
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
 * Cableado del lote BW-1 de peso corporal (catálogo 215/539): qué soporte pide cada alta y cómo llegan al motor las
 * anillas («Anillas o TRX») y el cajón («Cajón o step») del paso de material.
 *
 * Regla STOP: solo se usan requisitos que `supportRequirementsFor` ya conoce (`support`, `pull_up_bar`); ninguna alta
 * inventa un soporte nuevo. Las altas cuyo implemento es `bodyweight` están siempre acreditadas, así que el requisito de
 * soporte es lo único que impide ofrecerlas a quien no tiene dónde apoyarse.
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

    private fun tokensOf(
        vararg symbols: EquipmentSymbolId,
        places: Set<TrainingPlace> = home,
    ): Set<String> = TrainingOptions(availability = EquipmentSymbols.availabilityOf(symbols.toSet(), places))
        .effectiveEquipment(emptySet())

    /** Réplica del filtro de material del motor: el implemento de la configuración y sus requisitos de soporte. */
    private fun reachable(configurationId: String, tokens: Set<String>): Boolean {
        val configuration = configurations.getValue(configurationId)
        return configuration.profile.equipmentId in tokens && supportRequirementsFor(configurationId).all { it in tokens }
    }

    @Test
    fun every_bw1_addition_exists_as_an_approved_bodyweight_configuration() {
        (elevatedSupport + noSupport + "negative_pull_up__default").forEach { id ->
            val configuration = configurations[id]
            assertNotNull("el catálogo no trae «$id»", configuration)
            assertEquals("«$id» debe ser de peso corporal", "bodyweight", configuration!!.profile.equipmentId)
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
        val tokens = tokensOf()
        assertEquals(setOf("bodyweight"), tokens)
        noSupport.forEach { id -> assertTrue("«$id» debe alcanzarse sin material", reachable(id, tokens)) }
        (elevatedSupport + "negative_pull_up__default").forEach { id ->
            assertFalse("«$id» no debe ofrecerse sin soporte", reachable(id, tokens))
        }
    }

    @Test
    fun the_box_symbol_accredits_the_elevated_support_and_reaches_the_three_additions() {
        val availability = EquipmentSymbols.availabilityOf(setOf(EquipmentSymbolId.BOX), home)
        val result = TrainingOptions(availability = availability).resolveEffectiveEquipment(emptySet())
        assertTrue("el cajón acredita el apoyo elevado", REQUIREMENT_SUPPORT in result.tokens)
        assertEquals(RequirementEvidence.PRESENT, result.requirements[REQUIREMENT_SUPPORT])
        assertEquals(EffectiveEquipmentOrigin.CONFIRMED_SUPPORT, result.origins[REQUIREMENT_SUPPORT])
        elevatedSupport.forEach { id -> assertTrue("«$id» con cajón", reachable(id, result.tokens)) }
        assertFalse("el cajón no acredita la barra de dominadas", reachable("negative_pull_up__default", result.tokens))
        assertFalse("el cajón no acredita la suspensión", IMPLEMENT_TRX in result.tokens)
    }

    @Test
    fun the_bench_symbol_also_reaches_the_elevated_support_additions() {
        val tokens = tokensOf(EquipmentSymbolId.BENCH)
        elevatedSupport.forEach { id -> assertTrue("«$id» con banco", reachable(id, tokens)) }
    }

    @Test
    fun the_pull_up_bar_symbol_reaches_the_negative_pull_up() {
        val tokens = tokensOf(EquipmentSymbolId.PULL_UP_BAR, places = setOf(TrainingPlace.PUBLIC))
        assertTrue(reachable("negative_pull_up__default", tokens))
        // La barra sola no es un apoyo elevado: la pica con los pies elevados sigue pidiendo banco, cajón o escalón.
        assertFalse(reachable("pike_push_up__feet_elevated", tokens))
    }

    @Test
    fun the_rings_symbol_accredits_trx_and_reaches_the_three_suspension_configurations() {
        val suspension = configurations.values.filter { it.profile.equipmentId == IMPLEMENT_TRX }.map { it.id }.toSet()
        assertEquals("las configuraciones de suspensión del catálogo", suspensionConfigurations, suspension)

        val availability = EquipmentSymbols.availabilityOf(setOf(EquipmentSymbolId.RINGS), home)
        val result = TrainingOptions(availability = availability).resolveEffectiveEquipment(emptySet())
        assertTrue("las anillas acreditan el implemento trx", IMPLEMENT_TRX in result.tokens)
        assertEquals(EffectiveEquipmentOrigin.CONFIRMED_SUPPORT, result.origins[IMPLEMENT_TRX])
        suspensionConfigurations.forEach { id ->
            assertTrue("«$id» debe ser alcanzable con anillas", reachable(id, result.tokens))
        }
        // Unas anillas colgadas no son un apoyo elevado.
        assertFalse(REQUIREMENT_SUPPORT in result.tokens)
        elevatedSupport.forEach { id -> assertFalse("«$id» no se alcanza solo con anillas", reachable(id, result.tokens)) }
    }

    @Test
    fun trx_is_not_accredited_without_the_rings_symbol() {
        listOf(
            emptyArray<EquipmentSymbolId>(),
            arrayOf(EquipmentSymbolId.BENCH),
            arrayOf(EquipmentSymbolId.BOX),
            arrayOf(EquipmentSymbolId.PULL_UP_BAR, EquipmentSymbolId.PARALLEL_BARS),
        ).forEach { symbols ->
            val tokens = tokensOf(*symbols, places = setOf(TrainingPlace.GYM, TrainingPlace.HOME, TrainingPlace.PUBLIC))
            assertFalse("sin anillas no hay trx: ${symbols.toList()}", IMPLEMENT_TRX in tokens)
            suspensionConfigurations.forEach { id -> assertFalse("«$id» sin anillas", reachable(id, tokens)) }
        }
        // El gimnasio de serie no trae anillas: hay que elegirlas.
        val gym = setOf(TrainingPlace.GYM)
        val seeded = TrainingOptions(
            availability = EquipmentSymbols.availabilityOf(EquipmentSymbols.seedFor(gym), gym),
        ).effectiveEquipment(emptySet())
        assertFalse(IMPLEMENT_TRX in seeded)
        // …pero el cajón sí viene de serie y acredita el apoyo elevado.
        assertTrue(REQUIREMENT_SUPPORT in seeded)
    }

    @Test
    fun the_own_keys_need_the_support_category_and_a_present_answer() {
        // La llave sola, sin la categoría de soportes confirmada, no acredita nada (como el resto de llaves de soporte).
        val withoutCategory = TrainingOptions(
            availability = EquipmentAvailability(
                categories = setOf(EquipmentCategory.DUMBBELLS),
                supports = mapOf(SUPPORT_KEY_RINGS to ApparatusPresence.PRESENT, SUPPORT_KEY_PLYO_BOX to ApparatusPresence.PRESENT),
            ),
        ).effectiveEquipment(emptySet())
        assertFalse(IMPLEMENT_TRX in withoutCategory)
        assertFalse(REQUIREMENT_SUPPORT in withoutCategory)

        // Con la categoría pero con la llave negada o sin confirmar tampoco: se parte de lo que el wizard escribe al elegir
        // el símbolo y solo se cambia la respuesta de su propia llave.
        listOf(ApparatusPresence.ABSENT, ApparatusPresence.UNKNOWN).forEach { presence ->
            val rings = EquipmentSymbols.availabilityOf(setOf(EquipmentSymbolId.RINGS), home)
            val ringsTokens = TrainingOptions(
                availability = rings.copy(supports = rings.supports + (SUPPORT_KEY_RINGS to presence)),
            ).effectiveEquipment(emptySet())
            assertFalse("rings $presence", IMPLEMENT_TRX in ringsTokens)

            val box = EquipmentSymbols.availabilityOf(setOf(EquipmentSymbolId.BOX), home)
            val boxTokens = TrainingOptions(
                availability = box.copy(supports = box.supports + (SUPPORT_KEY_PLYO_BOX to presence)),
            ).effectiveEquipment(emptySet())
            assertFalse("plyo_box $presence", REQUIREMENT_SUPPORT in boxTokens)
        }
    }

    @Test
    fun the_resolver_keys_match_the_wizard_symbol_keys() {
        assertEquals(EquipmentSymbols.RINGS_KEY, SUPPORT_KEY_RINGS)
        assertEquals(EquipmentSymbols.BOX_KEY, SUPPORT_KEY_PLYO_BOX)
    }
}
