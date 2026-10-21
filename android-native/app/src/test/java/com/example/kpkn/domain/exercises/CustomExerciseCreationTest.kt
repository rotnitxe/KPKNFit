package com.example.kpkn.domain.exercises

import com.example.kpkn.data.db.dbJson
import com.example.kpkn.data.exercises.catalogv2.toLegacySelection
import com.example.kpkn.data.models.ExerciseCover
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.InvolvedMuscle
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.domain.auge.AugeFatigueEngine
import com.example.kpkn.domain.exercises.catalogv2.ExerciseSelectionV2
import com.example.kpkn.testsupport.MotCatalog
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MOT · E2/E3/E4 (AUD1 §3.3–§3.5) con el catálogo real: datos de UNA referencia confirmada con su procedencia, abstención con datos
 * desconocidos (la carga axial `null`, no 0), descripción solo desde una configuración confirmada, y datos manuales, portada y UUID
 * conservados al editar.
 */
class CustomExerciseCreationTest {
    private val v2 = MotCatalog.catalog
    private val snapshot = MotCatalog.legacyDefaultCatalog

    private fun create(name: String, request: SmartCreateRequest? = null) =
        SmartExerciseCreator.preview(request ?: SmartCreateRequest(name = name, implementoId = ""), snapshot, v2)

    private fun reference(definitionId: String, configurationId: String): ExerciseMuscleInfo =
        v2.toLegacySelection(ExerciseSelectionV2(definitionId, configurationId, v2.catalogRevision))!!

    @Test
    fun confirmedReference_copiesItsDataExactly_andKeepsProvenanceApartFromIdentity() {
        val created = create("sentadilla barra baja").exercise
        val ref = reference("low_bar_back_squat", "low_bar_back_squat__barbell")
        assertEquals("Barra", created.equipment)
        assertEquals(ref.efc, created.efc)
        assertEquals(ref.cnc, created.cnc)
        assertEquals(ref.ssc, created.ssc)
        assertEquals(ref.axialLoadFactor, created.axialLoadFactor)
        assertEquals(0.7, created.axialLoadFactor!!, 1e-9)
        assertEquals(ref.involvedMuscles.map { it.muscle to it.role }, created.involvedMuscles.map { it.muscle to it.role })
        assertTrue(created.id.startsWith("custom:"))
        // La identidad del propio no es la del catálogo; la procedencia va en un campo aparte.
        assertNull(created.catalogDefinitionId)
        assertNull(created.catalogConfigurationId)
        val provenance = created.creationReference!!
        assertEquals("low_bar_back_squat", provenance.definitionId)
        assertEquals("low_bar_back_squat__barbell", provenance.configurationId)
        assertEquals(v2.catalogRevision, provenance.catalogRevision)
        assertTrue(provenance.confirmed)
    }

    @Test
    fun descriptions_comeOnlyFromTheConfirmedConfiguration_andNeverBorrowOtherMuscles() {
        // D1, D2, D5, D7 de AUD1 §3.4, con el motor corregido.
        val squat = create("sentadila trasera barra alta").exercise
        val squatText = squat.description.orEmpty()
        assertTrue(squatText, squatText.contains("Cuádriceps"))
        listOf("Pectorales", "Dorsales", "Trapecio").forEach { assertFalse("$it en «$squatText»", squatText.contains(it)) }
        assertEquals("GENERATED", squat.descriptionOrigin)

        val rdl = create("peso mueto rumano con mancuernas").exercise
        val rdlText = rdl.description.orEmpty()
        assertTrue(rdlText, rdlText.contains("Isquiosurales"))
        listOf("Bíceps", "Tríceps", "Pectorales").forEach { assertFalse("$it en «$rdlText»", rdlText.contains(it)) }
        assertEquals("Mancuerna", rdl.equipment)

        val bayesian = create("bayesian curl polea").exercise
        assertEquals("Polea", bayesian.equipment)
        assertTrue(bayesian.description.orEmpty().contains("polea"))

        val legCurl = create("curl femoral sentado maquina").exercise
        assertTrue(legCurl.description.orEmpty().contains("Isquiosurales"))
        assertFalse(legCurl.description.orEmpty().contains("Bíceps"))
        assertTrue(legCurl.involvedMuscles.none { it.muscle == "Bíceps" })
    }

    @Test
    fun ambiguousNames_createWithUnknownData_notWithInventedData() {
        listOf("dragón lunar mancuernas", "mi ejercicio inventado", "XQZ 123", "sentadilla").forEach { name ->
            val preview = create(name)
            val created = preview.exercise
            assertTrue("«$name» pide completar los datos", preview.manualRecommended)
            assertTrue("«$name»: ${created.involvedMuscles}", created.involvedMuscles.isEmpty())
            assertNull("«$name»: la carga axial desconocida no es 0", created.axialLoadFactor)
            assertNull(created.efc)
            assertNull(created.cnc)
            assertNull(created.ssc)
            assertNull(created.ttc)
            assertNull("«$name»: sin referencia no hay texto", created.description)
            assertNull(created.descriptionOrigin)
            assertNull(created.creationReference)
            assertNull(created.movementPattern)
        }
        // El material escrito se respeta aunque lo demás sea desconocido: sin sesgo hacia mancuernas.
        assertEquals("Mancuerna", create("dragón lunar mancuernas").exercise.equipment)
        assertEquals("Barra", create("qwrt xyzw con barra").exercise.equipment)
        assertNull("sin material escrito, el material queda desconocido", create("qwrt xyzw").exercise.equipment)
    }

    @Test
    fun unknownAxial_keepsTheEnginesHeuristic_insteadOfCertifyingNoLoad() {
        // null = «sin dato»: la compuerta axial del motor conserva la heurística; 0 = «declarado sin carga» la apagaría (AUD1 §1.3).
        assertEquals(1.0, AugeFatigueEngine.spinalAxialGate(create("dragón lunar mancuernas").exercise.axialLoadFactor), 1e-9)
        assertEquals(0.0, AugeFatigueEngine.spinalAxialGate(0.0), 1e-9)
    }

    @Test
    fun theUsersOwnDescriptionAndMuscles_alwaysWin() {
        val mine = "Mi sentadilla con pausa de dos segundos abajo."
        val override = listOf(InvolvedMuscle("Cuádriceps", MuscleRole.PRIMARY, 0.9))
        val created = create(
            "sentadilla barra baja",
            SmartCreateRequest(name = "sentadilla barra baja", implementoId = "", description = mine, musclesOverride = override),
        ).exercise
        assertEquals(mine, created.description)
        assertEquals("USER", created.descriptionOrigin)
        assertEquals(listOf("Cuádriceps"), created.involvedMuscles.map { it.muscle })
        assertEquals(0.9, created.involvedMuscles.single().volumeContribution!!, 1e-9)
    }

    @Test
    fun editing_keepsManualData_cover_media_andTheUuid() {
        // Un propio sin referencia al que la persona le escribió los datos a mano y le eligió una foto de portada.
        val manual = SmartExerciseCreator.createAutomatic("movimiento propio xyz", snapshot, v2).copy(
            efc = 3.1, cnc = 2.2, ssc = 0.4, axialLoadFactor = 0.0,
            involvedMuscles = listOf(InvolvedMuscle("Dorsales", MuscleRole.PRIMARY, 1.0)),
            images = listOf("foto-propia"),
            cover = ExerciseCover.userPhoto("media-uuid-1"),
        )
        val edited = SmartExerciseCreator.preview(
            SmartCreateRequest(name = "movimiento propio xyz", implementoId = "", existingId = manual.id, existing = manual),
            snapshot,
            v2,
        ).exercise
        assertEquals(manual.id, edited.id)
        assertEquals(3.1, edited.efc!!, 1e-9)
        assertEquals(2.2, edited.cnc!!, 1e-9)
        assertEquals(0.0, edited.axialLoadFactor!!, 1e-9)
        assertEquals(listOf("Dorsales"), edited.involvedMuscles.map { it.muscle })
        assertEquals(listOf("foto-propia"), edited.images)
        assertEquals(ExerciseCover.userPhoto("media-uuid-1"), edited.cover)
    }

    @Test
    fun anExerciseBornFromAReference_isReDerivedWhenRenamed_butKeepsItsCover() {
        val born = create("sentadilla barra baja").exercise.copy(cover = ExerciseCover.userPhoto("m1"))
        val renamed = SmartExerciseCreator.preview(
            SmartCreateRequest(name = "front squat barra", implementoId = "", existingId = born.id, existing = born),
            snapshot,
            v2,
        ).exercise
        assertEquals(born.id, renamed.id)
        assertEquals("front_squat", renamed.creationReference!!.definitionId)
        assertEquals(ExerciseCover.userPhoto("m1"), renamed.cover)
    }

    @Test
    fun theNewFieldsSurviveTheDatabaseJson_andOldJsonStillDecodes() {
        val created = create("sentadilla barra baja").exercise.copy(cover = ExerciseCover.catalogReference("low_bar_back_squat", "low_bar_back_squat__barbell", v2.catalogRevision))
        val json = dbJson.encodeToString(created)
        val decoded = dbJson.decodeFromString<ExerciseMuscleInfo>(json)
        assertEquals(created, decoded)
        // Un propio guardado antes de MOT no trae los campos nuevos.
        val legacy = """{"id":"custom:old","name":"Viejo","isCustom":true}"""
        val old = dbJson.decodeFromString<ExerciseMuscleInfo>(legacy)
        assertNull(old.creationReference)
        assertNull(old.cover)
        assertNull(old.descriptionOrigin)
    }

    @Test
    fun theReferencesApi_givesThreeCandidatesWithReasons_forTheNewCreator() {
        val result = SmartExerciseCreator.findReferences("press banca con barra", snapshot, v2)
        assertTrue(result.candidates.size in 1..3)
        assertTrue(result.candidates.all { it.reasons.isNotEmpty() })
        assertEquals("bench_press", result.candidates.first().definitionId)
        // Con la variante explícita del formulario (estación, lateralidad) el motor la tiene en cuenta.
        val explicit = SmartExerciseCreator.findReferences(
            "curl femoral", snapshot, v2, SmartCreateRequest(name = "curl femoral", implementoId = "machine", estacionId = "seated"),
        )
        assertEquals("seated_leg_curl", explicit.candidates.first().definitionId)
    }

    @Test
    fun snapshotWithoutTheV2Catalog_stillConfirmsByIdentity() {
        val created = SmartExerciseCreator.createAutomatic("sentadilla barra baja", snapshot).copy()
        assertEquals("Barra", created.equipment)
        assertNotNull(created.creationReference)
        assertEquals("low_bar_back_squat", created.creationReference!!.definitionId)
    }

    @Test
    fun describeConfirmedReference_needsAReferenceAndPrimaries() {
        assertNull(describeConfirmedReference(null, "Barra"))
        val ref = reference("low_bar_back_squat", "low_bar_back_squat__barbell")
        val text = describeConfirmedReference(ref, "Barra")!!
        assertTrue(text, text.contains("barra"))
        assertTrue(text, text.contains("orientado a"))
        assertTrue(text, text.contains(ref.executionCues!!.first().trim()))
        assertNull(describeConfirmedReference(ref, "Barra", muscles = emptyList()))
    }
}
