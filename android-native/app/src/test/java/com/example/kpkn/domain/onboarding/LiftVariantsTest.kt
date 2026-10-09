package com.example.kpkn.domain.onboarding

import com.example.kpkn.domain.training.CatalogCompositionTestSupport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Las variantes de los básicos apuntan a configuraciones REALES del catálogo, con su implemento, y el material decide cuáles
 * se ofrecen con el mismo criterio que el generador.
 */
class LiftVariantsTest {

    private val catalogConfigurations: Map<String, String> by lazy {
        // id de configuración → implemento de catálogo (`profile.equipmentId`).
        buildMap {
            for (family in CatalogCompositionTestSupport.catalog.families) {
                for (definition in family.definitions) {
                    for (configuration in definition.configurations) {
                        put(configuration.id, configuration.profile.equipmentId)
                    }
                }
            }
        }
    }

    /** Todos los implementos del paso: el equipo más completo que puede declarar la persona. */
    private val fullKit = EquipmentSymbols.availabilityOf(
        EquipmentSymbolId.entries.filterNot { it == EquipmentSymbolId.BODYWEIGHT_ONLY }.toSet(),
        setOf(TrainingPlace.GYM, TrainingPlace.HOME, TrainingPlace.PUBLIC),
    )

    /** Barra sin rack ni banco, en casa: el caso que quita las sentadillas de rack. */
    private val barbellWithoutRack = EquipmentSymbols.availabilityOf(setOf(EquipmentSymbolId.BARBELL), setOf(TrainingPlace.HOME))

    /** Solo mancuernas: sin barra. */
    private val dumbbellsOnly = EquipmentSymbols.availabilityOf(setOf(EquipmentSymbolId.DUMBBELLS), setOf(TrainingPlace.HOME))

    private val powerbuilding = TrainingGoalProfile.POWERBUILDING
    private val powerlifting = TrainingGoalProfile.POWERLIFTING

    @Test
    fun everyOfferedVariantExistsInTheCatalogWithTheSameImplement() {
        for (goal in listOf(powerlifting, powerbuilding)) {
            for (lift in LiftVariants.liftsFor(goal)) {
                for (option in LiftVariants.optionsFor(goal, lift, fullKit)) {
                    val equipment = catalogConfigurations[option.configId]
                    assertTrue("${option.configId} no existe en el catálogo", equipment != null)
                    assertEquals("implemento de ${option.configId}", equipment, option.equipmentId)
                }
            }
        }
    }

    @Test
    fun theOptionsCoverEveryConfigurationOfTheTable() {
        // Con todo el material se ofrecen todas las variantes de la tabla: si una se pierde, la prueba lo dice.
        assertEquals(
            listOf("high_bar_back_squat__barbell", "low_bar_back_squat__barbell"),
            LiftVariants.optionsFor(powerlifting, LiftMark.SQUAT, fullKit).map { it.configId },
        )
        assertEquals(
            listOf("conventional_deadlift__bilateral__barbell", "sumo_deadlift__barbell"),
            LiftVariants.optionsFor(powerlifting, LiftMark.DEADLIFT, fullKit).map { it.configId },
        )
        assertEquals(
            listOf(
                "high_bar_back_squat__barbell", "low_bar_back_squat__barbell", "front_squat__barbell",
                "quads_sentadilla_zercher_barra_recta__default", "bulgarian_split_squat__dumbbells", "bulgarian_split_squat__barbell",
            ),
            LiftVariants.optionsFor(powerbuilding, LiftMark.SQUAT, fullKit).map { it.configId },
        )
        assertEquals(
            listOf("military_press__barbell", "military_press__dumbbells"),
            LiftVariants.optionsFor(powerbuilding, LiftMark.OVERHEAD_PRESS, fullKit).map { it.configId },
        )
    }

    @Test
    fun theFirstVariantOfEachLiftIsTheDefaultTheGeneratorUses() {
        assertEquals("high_bar_back_squat__barbell", LiftVariants.defaultFor(LiftMark.SQUAT))
        assertEquals("bench_press__barbell", LiftVariants.defaultFor(LiftMark.BENCH))
        assertEquals("conventional_deadlift__bilateral__barbell", LiftVariants.defaultFor(LiftMark.DEADLIFT))
        assertEquals("military_press__barbell", LiftVariants.defaultFor(LiftMark.OVERHEAD_PRESS))
        for (lift in LiftVariants.liftsFor(powerbuilding)) {
            assertEquals("$lift", LiftVariants.defaultFor(lift), LiftVariants.optionsFor(powerbuilding, lift, fullKit).first().configId)
        }
    }

    @Test
    fun theLiftsWithVariantsAreTheOnesOfTheProfilesThatAskThem() {
        assertEquals(listOf(LiftMark.SQUAT, LiftMark.DEADLIFT), LiftVariants.liftsFor(powerlifting))
        assertEquals(
            listOf(LiftMark.SQUAT, LiftMark.BENCH, LiftMark.DEADLIFT, LiftMark.OVERHEAD_PRESS),
            LiftVariants.liftsFor(powerbuilding),
        )
        for (profile in TrainingGoalProfile.entries.filterNot { it == powerlifting || it == powerbuilding }) {
            assertTrue("$profile", LiftVariants.liftsFor(profile).isEmpty())
        }
        assertTrue(LiftVariants.liftsFor(null).isEmpty())
    }

    @Test
    fun withoutABarbellNoBarbellVariantIsOffered() {
        for (goal in listOf(powerlifting, powerbuilding)) {
            for (lift in LiftVariants.liftsFor(goal)) {
                val offered = LiftVariants.optionsFor(goal, lift, dumbbellsOnly)
                assertTrue(
                    "$goal $lift sin barra no debe ofrecer barra: $offered",
                    offered.none { it.equipmentId == "barbell" || it.configId.endsWith("__barbell") },
                )
            }
        }
        // Con mancuernas sí se ofrecen las suyas (no piden nada más que el implemento).
        assertTrue(
            LiftVariants.optionsFor(powerbuilding, LiftMark.OVERHEAD_PRESS, dumbbellsOnly).any { it.configId == "military_press__dumbbells" },
        )
    }

    @Test
    fun theRackSquatsNeedARackAndTheZercherOnlyABarbell() {
        // Criterio del generador (`supportRequirementsFor`): las sentadillas de barra descargadas de un soporte piden rack; la
        // Zercher se levanta del suelo y no lo pide.
        val squats = LiftVariants.optionsFor(powerbuilding, LiftMark.SQUAT, barbellWithoutRack).map { it.configId }
        assertFalse("barra alta necesita rack", "high_bar_back_squat__barbell" in squats)
        assertFalse("barra baja necesita rack", "low_bar_back_squat__barbell" in squats)
        assertFalse("frontal necesita rack", "front_squat__barbell" in squats)
        assertTrue("Zercher solo pide barra", "quads_sentadilla_zercher_barra_recta__default" in squats)
        assertTrue("la búlgara con barra solo pide barra", "bulgarian_split_squat__barbell" in squats)
    }

    @Test
    fun aSavedVariantIsReadAsTheDefaultWhenTheMaterialNoLongerAllowsIt() {
        val lowBar = "low_bar_back_squat__barbell"
        // Con rack, la baja se lee tal cual.
        assertEquals(lowBar, LiftVariants.effectiveFor(powerlifting, LiftMark.SQUAT, lowBar, fullKit))
        // Sin rack, la baja ya no se ofrece: se lee la de siempre, y el dato guardado no se borra (lo decide quien llama).
        assertEquals(
            "high_bar_back_squat__barbell",
            LiftVariants.effectiveFor(powerlifting, LiftMark.SQUAT, lowBar, barbellWithoutRack),
        )
        // Sin variante guardada, la de siempre.
        assertEquals("high_bar_back_squat__barbell", LiftVariants.effectiveFor(powerlifting, LiftMark.SQUAT, null, fullKit))
        // Un perfil sin variantes de ese levantamiento no tiene nada que leer.
        assertNull(LiftVariants.effectiveFor(TrainingGoalProfile.STRONGMAN, LiftMark.SQUAT, lowBar, fullKit))
    }

    @Test
    fun theMarkOfADumbbellOrBulgarianVariantIsNotAskedButTheBarbellOneIs() {
        // Las mancuernas y las búlgaras no se traducen a una carga de barra: su levantamiento no pregunta marca.
        assertFalse(LiftVariants.takesMarkFor(powerbuilding, LiftMark.OVERHEAD_PRESS, "military_press__dumbbells", fullKit))
        assertFalse(LiftVariants.takesMarkFor(powerbuilding, LiftMark.SQUAT, "bulgarian_split_squat__barbell", fullKit))
        assertTrue(LiftVariants.takesMarkFor(powerbuilding, LiftMark.OVERHEAD_PRESS, "military_press__barbell", fullKit))
        assertTrue(LiftVariants.takesMarkFor(powerbuilding, LiftMark.SQUAT, null, fullKit))
        // Si la variante guardada ya no es posible con el material, se mide la de siempre: la marca vuelve a preguntarse.
        assertTrue(LiftVariants.takesMarkFor(powerbuilding, LiftMark.SQUAT, "bulgarian_split_squat__dumbbells", barbellWithoutRack))
    }

    @Test
    fun aVariantOnlyBelongsToTheLiftItWasMadeFor() {
        assertTrue(LiftVariants.belongsTo(LiftMark.SQUAT, "low_bar_back_squat__barbell"))
        assertFalse(LiftVariants.belongsTo(LiftMark.SQUAT, "conventional_deadlift__bilateral__barbell"))
        assertFalse(LiftVariants.belongsTo(LiftMark.DEADLIFT, "low_bar_back_squat__barbell"))
        assertEquals("Barra baja", LiftVariants.labelOf("low_bar_back_squat__barbell"))
        assertEquals("Búlgara con mancuernas", LiftVariants.labelOf("bulgarian_split_squat__dumbbells"))
        assertNull(LiftVariants.labelOf("no_existe__barbell"))
    }
}
