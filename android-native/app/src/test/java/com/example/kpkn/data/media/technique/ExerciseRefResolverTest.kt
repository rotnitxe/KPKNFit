package com.example.kpkn.data.media.technique

import com.example.kpkn.data.models.WorkoutMedia
import com.example.kpkn.data.models.WorkoutMediaKind
import com.example.kpkn.data.repository.CaptureOverridesFile
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfileId
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfileResolver
import com.example.kpkn.domain.biomechanics.profiles.ExerciseRef
import com.example.kpkn.domain.biomechanics.profiles.ProfileResolution
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2
import com.example.kpkn.domain.exercises.catalogv2.ExerciseCatalogV2Loader
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Del medio y el catálogo real (`exercise_catalog_v2.json`) al `ExerciseRef` completo con el que se resuelve el perfil. */
class ExerciseRefResolverTest {
    @Rule
    @JvmField
    val folder = TemporaryFolder()

    private val catalog: ExerciseCatalogV2 by lazy {
        val file = listOf(File("src/main/assets/exercise_catalog_v2.json"), File("app/src/main/assets/exercise_catalog_v2.json")).first { it.exists() }
        ExerciseCatalogV2Loader.decodeApproved(file.readText())
    }
    private val resolver by lazy { ExerciseRefResolver(catalog) }

    /** El `ExerciseRef` parcial que arma el Worker: ids del medio, nombre y `isCustom = sin ids`. */
    private fun partial(definitionId: String? = null, configurationId: String? = null, name: String = "", override: AnalysisProfileId? = null) =
        ExerciseRef(definitionId, configurationId, null, null, null, name, isCustom = definitionId == null && configurationId == null, userProfileOverride = override)

    private fun media(
        definitionId: String? = null,
        configurationId: String? = null,
        canonical: String? = null,
        name: String? = null,
    ) = WorkoutMedia(
        id = "m1", kind = WorkoutMediaKind.VIDEO, filePath = "x.mp4", createdAtMs = 1L, canonicalExerciseId = canonical, exerciseName = name,
        catalogDefinitionId = definitionId, catalogConfigurationId = configurationId,
    )

    @Test
    fun aCatalogConfigurationGivesItsPatternEquipmentAndLaterality() {
        val ref = resolver.resolve(
            partial("high_bar_back_squat", "high_bar_back_squat__barbell", "Sentadilla trasera"),
            media("high_bar_back_squat", "high_bar_back_squat__barbell"),
        )
        assertEquals("high_bar_back_squat", ref.definitionId)
        assertEquals("high_bar_back_squat__barbell", ref.configurationId)
        assertEquals("knee_dominant", ref.movementPatternId)
        assertEquals("barbell", ref.equipmentId)
        assertEquals("BILATERAL", ref.laterality)
        assertEquals("Sentadilla trasera", ref.name)
        assertFalse(ref.isCustom)
        assertNull(ref.guessedMovementPatternId)

        // Con eso el resolvedor de perfiles acierta y sabe que el disco es la señal (barra).
        val profile = (AnalysisProfileResolver.resolve(ref) as ProfileResolution.Supported).profile
        assertEquals(AnalysisProfileId.SQUAT, profile.id)
        assertTrue(profile.usesPlate)

        // Una mancuerna del mismo ejercicio: el mismo perfil, sin disco.
        val dumbbells = resolver.resolve(partial(null, "bench_press__dumbbells"), media(null, "bench_press__dumbbells"))
        assertEquals("dumbbells", dumbbells.equipmentId)
        val bench = (AnalysisProfileResolver.resolve(dumbbells) as ProfileResolution.Supported).profile
        assertEquals(AnalysisProfileId.BENCH, bench.id)
        assertFalse(bench.usesPlate)
    }

    @Test
    fun theWholeCatalogResolvesByConfigurationToItsOwnProfileData() {
        var checked = 0
        for (family in catalog.families) for (definition in family.definitions) for (configuration in definition.configurations) {
            val ref = resolver.resolve(partial(definition.id, configuration.id, definition.canonicalName), null)
            assertEquals(configuration.id, ref.configurationId)
            assertEquals(configuration.profile.movementPatternId, ref.movementPatternId)
            assertEquals(configuration.profile.equipmentId, ref.equipmentId)
            assertEquals(configuration.profile.laterality.name, ref.laterality)
            assertFalse(configuration.id, ref.isCustom)
            // Ningún ejercicio del catálogo pide elegir el perfil: o se mide o se explica por qué no.
            assertFalse(configuration.id, AnalysisProfileResolver.resolve(ref) is ProfileResolution.NeedsUserChoice)
            checked++
        }
        assertTrue("catálogo vacío: $checked", checked > 500)
    }

    @Test
    fun aDefinitionOnlyGivesThePatternButNotTheEquipment() {
        val ref = resolver.resolve(partial("bench_press", null, ""), media("bench_press", null, name = "Banca"))
        assertEquals("bench_press", ref.definitionId)
        assertNull(ref.configurationId)
        assertEquals("horizontal_push", ref.movementPatternId)
        assertNull("no se sabe qué configuración se hizo", ref.equipmentId)
        assertEquals("Banca", ref.name)
        assertFalse(ref.isCustom)
    }

    @Test
    fun aMediaWithOnlyACanonicalIdIsResolvedInsteadOfBeingCustom() {
        // Un vídeo anterior a v29: sin ids de catálogo, el Worker lo arma como «propio».
        val before = partial(name = "Press banca")
        assertTrue(before.isCustom)
        val byConfiguration = resolver.resolve(before, media(canonical = "Bench_Press__Barbell"))
        assertFalse(byConfiguration.isCustom)
        assertEquals("bench_press__barbell", byConfiguration.configurationId)
        assertEquals("barbell", byConfiguration.equipmentId)

        val byDefinition = resolver.resolve(before, media(canonical = "bench_press"))
        assertFalse(byDefinition.isCustom)
        assertEquals("bench_press", byDefinition.definitionId)
        assertEquals("horizontal_push", byDefinition.movementPatternId)
    }

    @Test
    fun customAndUnknownExercisesAreCustomWithAGuessFromTheName() {
        val custom = resolver.resolve(partial(name = "Press banca con pausa"), media(canonical = "custom:press banca con pausa", name = "Press banca con pausa"))
        assertTrue(custom.isCustom)
        assertNull(custom.movementPatternId)
        assertEquals("horizontal_push", custom.guessedMovementPatternId)
        assertEquals(ProfileResolution.NeedsUserChoice(AnalysisProfileId.BENCH), AnalysisProfileResolver.resolve(custom))

        // Un id que el catálogo no conoce (otra revisión): propio, y no se pierde el id.
        val stale = resolver.resolve(partial("ya_no_existe", "ya_no_existe__barbell", "Remo con barra"), null)
        assertTrue(stale.isCustom)
        assertEquals("ya_no_existe", stale.definitionId)
        assertEquals("horizontal_pull", stale.guessedMovementPatternId)

        // Sin ninguna pista, no hay conjetura.
        val unknown = resolver.resolve(partial(name = "Cosa rara"), null)
        assertTrue(unknown.isCustom)
        assertNull(unknown.guessedMovementPatternId)
        assertEquals(ProfileResolution.NeedsUserChoice(null), AnalysisProfileResolver.resolve(unknown))
    }

    @Test
    fun aRetiredConfigurationResolvesToItsReplacement() {
        val ref = resolver.resolve(partial("walking_lunge", "walking_lunge__smith_machine", "Zancada caminando"), null)
        assertEquals("forward_lunge__smith_machine", ref.configurationId)
        assertEquals("smith_machine", ref.equipmentId)
        assertFalse(ref.isCustom)
    }

    @Test
    fun theUsersProfileChoiceWinsAndComesFromTheRequestOrTheCaptureFile() {
        val capture = File(folder.root, "clip.capture.json")
        // El de la petición gana al del archivo.
        CaptureOverridesFile.update(capture) { it.copy(userProfileOverride = AnalysisProfileId.CURL) }
        val fromRequest = resolver.resolve(partial(name = "Algo", override = AnalysisProfileId.ROW), null, capture)
        assertEquals(AnalysisProfileId.ROW, fromRequest.userProfileOverride)

        // Sin el de la petición, el del archivo de captura.
        val fromFile = resolver.resolve(partial(name = "Algo"), null, capture)
        assertEquals(AnalysisProfileId.CURL, fromFile.userProfileOverride)
        assertEquals(ProfileResolution.Supported::class, AnalysisProfileResolver.resolve(fromFile)::class)

        // Sin ninguno, ninguno; y un ejercicio del catálogo conserva el perfil elegido.
        assertNull(resolver.resolve(partial(name = "Algo"), null, File(folder.root, "otro.capture.json")).userProfileOverride)
        val catalogOne = resolver.resolve(partial(null, "bench_press__barbell", override = AnalysisProfileId.OHP), null)
        assertEquals(AnalysisProfileId.OHP, catalogOne.userProfileOverride)
        assertEquals("barbell", catalogOne.equipmentId)
    }

    @Test
    fun withoutACatalogTheIdsAndWhatTheRequestKnowsTravelUntouched() {
        val none = ExerciseRefResolver(null)
        assertFalse(none.hasCatalog)
        val request = ExerciseRef("wall_sit", null, "knee_dominant", "barbell", "BILATERAL", "Sentada", isCustom = false)
        val ref = none.resolve(request, null)
        assertEquals("wall_sit", ref.definitionId)
        assertEquals("knee_dominant", ref.movementPatternId)
        assertEquals("barbell", ref.equipmentId)
        assertEquals("BILATERAL", ref.laterality)
        assertFalse(ref.isCustom)
        // Y el resolvedor de perfiles aún acierta por definición.
        assertEquals(ProfileResolution.Unsupported("isometric"), AnalysisProfileResolver.resolve(ref))
        // Sin ids es propio.
        assertTrue(none.resolve(ExerciseRef(null, null, null, null, null, "Remo", isCustom = true), null).isCustom)
    }
}
