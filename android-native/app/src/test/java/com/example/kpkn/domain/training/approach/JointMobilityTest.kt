package com.example.kpkn.domain.training.approach

import com.example.kpkn.data.models.MobilityExerciseCatalog
import com.example.kpkn.data.models.MobilitySeries
import com.example.kpkn.data.models.MobilityUnit
import com.example.kpkn.domain.training.CatalogCompositionTestSupport
import java.io.File
import java.text.Normalizer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El mapa articulación → movilidad de Entreno v2 se apoya en dos catálogos que viven por separado: las 15
 * articulaciones de `wikilab/joints.json` (las de `jointInvolvement`) y los 162 movimientos de
 * [MobilityExerciseCatalog]. Estas pruebas fallan si un id desaparece, si una articulación se queda sin
 * movimientos o si un movimiento deja de corresponder a su región corporal.
 */
class JointMobilityTest {

    private val allowedEquipment = setOf("Sin material", "Banda elástica", "Palo o banda", "Poste o soporte")

    /** Regiones corporales del catálogo de movilidad que corresponden a cada articulación. */
    private val regionsByJoint: Map<String, Set<String>> = mapOf(
        JointMobility.GLENOHUMERAL to setOf("shoulder"),
        JointMobility.ACROMIOCLAVICULAR to setOf("shoulder", "scapula"),
        JointMobility.STERNOCLAVICULAR to setOf("shoulder", "scapula"),
        JointMobility.SCAPULOTHORACIC to setOf("scapula"),
        JointMobility.ELBOW to setOf("elbow"),
        JointMobility.PROXIMAL_RADIOULNAR to setOf("elbow"),
        JointMobility.WRIST to setOf("wrist"),
        JointMobility.CERVICAL_SPINE to setOf("neck"),
        JointMobility.THORACIC_SPINE to setOf("upper", "spine"),
        JointMobility.LUMBAR_SPINE to setOf("spine", "pelvis", "trunk"),
        JointMobility.SACROILIAC to setOf("pelvis", "hip"),
        JointMobility.HIP to setOf("hip", "pelvis", "leg"),
        JointMobility.KNEE to setOf("knee"),
        JointMobility.ANKLE to setOf("ankle"),
        JointMobility.SUBTALAR to setOf("ankle", "foot"),
    )

    private fun nfc(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFC)

    private fun assetFile(relative: String): File = listOf(
        "src/main/assets/$relative",
        "app/src/main/assets/$relative",
        "android-native/app/src/main/assets/$relative",
        "../android-native/app/src/main/assets/$relative",
    ).map(::File).first { it.exists() }

    private fun wikilabJointIds(): List<String> =
        Json.parseToJsonElement(assetFile("wikilab/joints.json").readText(Charsets.UTF_8))
            .jsonArray.map { nfc(it.jsonObject.getValue("id").jsonPrimitive.content) }

    @Test
    fun the_map_covers_exactly_the_fifteen_joints_of_wikilab() {
        val wikilab = wikilabJointIds()
        assertEquals(15, wikilab.size)
        assertEquals(wikilab.toSet(), JointMobility.allJointIds.map(::nfc).toSet())
        assertEquals("sin ids repetidos", JointMobility.allJointIds.size, JointMobility.allJointIds.toSet().size)
    }

    @Test
    fun every_joint_has_two_or_three_curated_movements_that_exist_in_the_catalog() {
        JointMobility.allJointIds.forEach { joint ->
            val ids = JointMobility.movementIdsFor(joint)
            assertTrue("$joint: entre 2 y 3 movimientos, hay ${ids.size}", ids.size in 2..3)
            assertEquals("$joint: sin movimientos repetidos", ids.size, ids.toSet().size)
            ids.forEach { id ->
                assertNotNull("$joint: el movimiento '$id' ya no existe en MobilityExerciseCatalog", MobilityExerciseCatalog.findById(id))
            }
            assertEquals("$joint: movementsFor resuelve todos los ids", ids, JointMobility.movementsFor(joint).map { it.id })
        }
    }

    @Test
    fun the_catalog_has_the_expected_size_and_the_curated_ids_are_a_small_subset() {
        val all = MobilityExerciseCatalog.getAllMobilityExercises()
        assertEquals(162, all.size)
        val curated = JointMobility.allJointIds.flatMap { JointMobility.movementIdsFor(it) }.toSet()
        assertTrue("el mapa no copia el catálogo entero", curated.size < all.size / 3)
    }

    @Test
    fun every_movement_belongs_to_the_body_region_of_its_joint() {
        JointMobility.allJointIds.forEach { joint ->
            val regions = regionsByJoint.getValue(joint)
            JointMobility.movementsFor(joint).forEach { movement ->
                assertTrue(
                    "$joint: '${movement.id}' es de la región '${movement.bodyRegion}', se esperaba $regions",
                    movement.bodyRegion in regions,
                )
            }
        }
    }

    @Test
    fun movements_need_no_material_except_band_wall_or_a_support() {
        JointMobility.allJointIds.flatMap { JointMobility.movementsFor(it) }.distinctBy { it.id }.forEach { movement ->
            assertTrue(
                "'${movement.id}' pide '${movement.equipment}'",
                movement.equipment in allowedEquipment,
            )
            assertTrue("'${movement.id}' dura entre 30 y 60 s", movement.durationSeconds in 30..60)
        }
        // El primer movimiento de cada articulación importante para cargar nunca pide más que banda o apoyo.
        listOf(
            JointMobility.GLENOHUMERAL, JointMobility.HIP, JointMobility.KNEE, JointMobility.ANKLE,
            JointMobility.THORACIC_SPINE, JointMobility.LUMBAR_SPINE, JointMobility.WRIST, JointMobility.ELBOW,
        ).forEach { joint ->
            assertTrue(JointMobility.movementsFor(joint).first().equipment in allowedEquipment)
        }
    }

    @Test
    fun the_shoulder_hip_ankle_and_thoracic_picks_follow_the_editorial_choice() {
        assertEquals(
            "hombro: rotaciones con banda y «pass-through»",
            listOf("mob_shoulder_band_rotation", "mob_stick_dislocates"),
            JointMobility.movementIdsFor(JointMobility.GLENOHUMERAL).take(2),
        )
        assertEquals(
            "escápula: círculos escapulares",
            "mob_scapular_circles_quadruped",
            JointMobility.movementIdsFor(JointMobility.SCAPULOTHORACIC).first(),
        )
        assertEquals(
            listOf("mob_supported_deep_squat", "mob_90_90_hip", "mob_bridge_articulation"),
            JointMobility.movementIdsFor(JointMobility.HIP),
        )
        assertEquals("mob_ankle_dorsiflexion_wall", JointMobility.movementIdsFor(JointMobility.ANKLE).first())
        assertEquals("mob_quadruped_thoracic_rotation", JointMobility.movementIdsFor(JointMobility.THORACIC_SPINE).first())
    }

    @Test
    fun a_multi_joint_movement_reports_every_joint_it_mobilizes() {
        val deepSquat = JointMobility.jointsOf("mob_supported_deep_squat")
        assertTrue(deepSquat.containsAll(listOf(JointMobility.HIP, JointMobility.KNEE, JointMobility.ANKLE)))
        val wallSlides = JointMobility.jointsOf("mob_wall_slides")
        assertTrue(wallSlides.containsAll(listOf(JointMobility.GLENOHUMERAL, JointMobility.SCAPULOTHORACIC)))
        // Cada movimiento curado cubre la articulación para la que está listado.
        JointMobility.allJointIds.forEach { joint ->
            JointMobility.movementIdsFor(joint).forEach { id ->
                assertTrue("$id cubre $joint", joint in JointMobility.jointsOf(id))
            }
        }
        assertTrue("un id desconocido no cubre nada", JointMobility.jointsOf("mob_no_existe").isEmpty())
    }

    @Test
    fun movements_outside_the_curated_lists_are_translated_from_the_catalog_joints() {
        // Un movimiento que el autor eligió a mano (no está en el mapa) cubre lo que dice su ficha.
        val trapezius = JointMobility.jointsOf("mob_upper_trap_breathing")
        assertTrue(JointMobility.CERVICAL_SPINE in trapezius)
        assertTrue(JointMobility.SCAPULOTHORACIC in trapezius)
        assertTrue(JointMobility.WRIST in JointMobility.jointsOf("mob_reverse_prayer"))
    }

    @Test
    fun series_for_a_movement_has_the_same_shape_the_editor_creates() {
        val movement = checkNotNull(JointMobility.movement("mob_shoulder_band_rotation"))
        val series = JointMobility.seriesFor(movement, "Press banca", seconds = 40)
        assertEquals("mob_shoulder_band_rotation", series.id)
        assertEquals("mob_shoulder_band_rotation", series.exerciseDbId)
        assertEquals("mob_shoulder_band_rotation", series.catalogConfigurationId)
        assertEquals(movement.name, series.name)
        assertEquals(1, series.sets)
        assertEquals(40, series.durationSeconds)
        assertEquals(MobilityUnit.SECONDS, series.unit)
        assertEquals(listOf("shoulder"), series.bodyZones)
        assertEquals("Movilidad asociada a Press banca", series.notes)
        assertTrue("es ejecutable: series y duración", series.sets > 0 && (series.durationSeconds ?: 0) > 0)
    }

    @Test
    fun covered_joints_come_from_the_catalog_identity_of_each_series() {
        val deepSquat = JointMobility.seriesFor(checkNotNull(JointMobility.movement("mob_supported_deep_squat")), "Sentadilla", 40)
        val custom = MobilitySeries(id = "u-1", name = "Movilidad propia", durationSeconds = 30, unit = MobilityUnit.SECONDS)
        assertTrue(JointMobility.jointsCoveredBy(listOf(deepSquat)).containsAll(listOf(JointMobility.HIP, JointMobility.KNEE, JointMobility.ANKLE)))
        assertTrue("una movilidad sin identidad de catálogo no cubre nada conocido", JointMobility.jointsCoveredBy(listOf(custom)).isEmpty())
        assertTrue(JointMobility.jointsCoveredBy(emptyList()).isEmpty())
    }

    @Test
    fun every_joint_of_the_exercise_catalog_has_curated_mobility() {
        // Las 15 articulaciones que el catálogo de ejercicios usa en jointInvolvement tienen mapa.
        val usedByCatalog = CatalogCompositionTestSupport.catalog.families
            .flatMap { it.definitions }.flatMap { it.configurations }
            .flatMap { it.profile.jointInvolvement }.map { nfc(it.jointId) }.toSet()
        val mapped = JointMobility.allJointIds.map(::nfc).toSet()
        assertTrue("articulaciones del catálogo sin mapa: ${usedByCatalog - mapped}", mapped.containsAll(usedByCatalog))
    }
}
