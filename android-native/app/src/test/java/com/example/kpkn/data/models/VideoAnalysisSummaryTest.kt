package com.example.kpkn.data.models

import com.example.kpkn.data.db.VideoAnalysisEntity
import com.example.kpkn.data.db.dbJson
import com.example.kpkn.data.media.technique.TechniqueReportSamples
import com.example.kpkn.domain.biomechanics.kinematics.ViewKind
import com.example.kpkn.domain.biomechanics.metrics.AnalysisFlag
import com.example.kpkn.domain.biomechanics.metrics.CountConfidence
import com.example.kpkn.domain.biomechanics.metrics.ScaleSource
import com.example.kpkn.domain.biomechanics.metrics.SetTechniqueReport
import com.example.kpkn.domain.biomechanics.metrics.TechniqueAnalysisEngine
import com.example.kpkn.domain.biomechanics.metrics.VelocityUnit
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfileId
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** El resumen de la fila `video_analysis`: del informe a la fila y de la fila al resumen, las claves y los ajustes (§3.9, §3.10). */
class VideoAnalysisSummaryTest {
    private val report = TechniqueReportSamples.fullReport()

    private fun media(
        id: String = "m1",
        exerciseId: String? = "squat",
        canonical: String? = "lower_squat",
        name: String? = "Sentadilla trasera",
        setIndex: Int? = 2,
        side: String? = "left",
        definition: String? = "back_squat",
        configuration: String? = "back_squat__barbell",
    ) = WorkoutMedia(
        id = id,
        kind = WorkoutMediaKind.VIDEO,
        filePath = "/private/workout_media/2026-10/$id.mp4",
        createdAtMs = 1_780_000_000_000L,
        sessionKey = "p::s::1",
        exerciseId = exerciseId,
        canonicalExerciseId = canonical,
        exerciseName = name,
        setIndex = setIndex,
        side = side,
        weightKg = 100.0,
        reps = 8,
        catalogDefinitionId = definition,
        catalogConfigurationId = configuration,
    )

    private fun summary(
        r: SetTechniqueReport = report,
        m: WorkoutMedia = media(),
        nowMs: Long = 1_780_000_900_000L,
    ) = r.toSummary(
        media = m,
        exerciseKey = m.techniqueExerciseKey(),
        configurationKey = m.techniqueConfigurationKey(),
        loadKg = 102.5,
        reportPath = "/private/workout_media/2026-10/${m.id}.analysis.json",
        nowMs = nowMs,
    )

    @Test
    fun a_full_report_becomes_a_done_summary_with_every_measure() {
        val s = summary()

        assertEquals("m1", s.mediaId)
        assertEquals(VideoAnalysisStatus.DONE, s.status)
        assertTrue(s.isDone)
        assertEquals(100, s.progressPct)
        assertNull(s.errorCode)
        assertEquals(TechniqueAnalysisEngine.ENGINE_VERSION, s.engineVersion)
        assertEquals(VideoAnalysisSummary.DEFAULT_POSE_VERSION, s.poseVersion)
        assertNull(s.poseEngine)
        assertEquals(AnalysisProfileId.SQUAT, s.profileId)
        assertEquals("back_squat", s.exerciseKey)
        assertEquals("back_squat__barbell", s.configurationKey)
        assertEquals("Sentadilla trasera", s.exerciseName)
        assertEquals("squat_2_L", s.setKey)
        assertEquals(102.5, s.loadKg!!, 0.0)
        assertEquals(1_780_000_000_000L, s.recordedAtMs)
        assertEquals(59.94, s.sourceFps!!, 0.0)
        assertEquals(29.97, s.analyzedFps!!, 0.0)
        assertEquals(ViewKind.SIDE, s.viewKind)
        assertEquals(CountConfidence.ALTA, s.confidence)
        assertEquals(2, s.repCount)
        assertEquals(1, s.partialReps)
        assertEquals(0, s.failedReps)
        assertEquals(12_500L, s.tutMs)
        assertEquals(1500, s.tempoEccMs)
        assertEquals(500, s.tempoPauseAfterEccMs)
        assertEquals(1250, s.tempoConMs)
        assertEquals(250, s.tempoPauseAfterConMs)
        assertEquals(101.2, s.romMeanDeg!!, 0.0)
        assertEquals(1.8, s.romCvPct!!, 0.0)
        assertEquals(VelocityUnit.MPS, s.velocityUnit)
        assertEquals(12.5, s.velocityLossPct!!, 0.0)
        assertEquals(0.41, s.mcvFirst!!, 0.0)
        assertEquals(0.43, s.mcvBest!!, 0.0)
        assertEquals(0.376, s.mcvLast!!, 0.0)
        assertEquals(0.92, s.peakBest!!, 0.0)
        assertEquals(ScaleSource.PLATE, s.scaleSource)
        assertEquals(450, s.plateDiameterMm)
        assertEquals(3.2, s.barDeviation!!, 0.0)
        assertEquals("cm", s.barDeviationUnit)
        assertEquals(28.5, s.trunkMeanDeg!!, 0.0)
        assertEquals("/private/workout_media/2026-10/m1.analysis.json", s.reportPath)
        assertEquals(1_780_000_900_000L, s.createdAtMs)
        assertEquals(1_780_000_900_000L, s.updatedAtMs)
    }

    @Test
    fun the_pose_data_and_the_creation_date_are_kept_when_the_caller_knows_them() {
        val s = report.toSummary(
            media = media(),
            exerciseKey = "back_squat",
            configurationKey = null,
            loadKg = null,
            reportPath = "/x.analysis.json",
            nowMs = 9_000L,
            poseVersion = 3,
            poseEngine = "mediapipe-pose/full/GPU",
            createdAtMs = 5_000L,
        )

        assertEquals(3, s.poseVersion)
        assertEquals("mediapipe-pose/full/GPU", s.poseEngine)
        assertEquals(5_000L, s.createdAtMs)
        assertEquals(9_000L, s.updatedAtMs)
        assertNull(s.configurationKey)
        assertNull(s.loadKg)
    }

    @Test
    fun a_report_with_no_measures_leaves_every_measure_empty_instead_of_zero() {
        val s = summary(r = TechniqueReportSamples.minimalReport(), m = media(setIndex = null, side = null, name = null))

        assertEquals(VideoAnalysisStatus.DONE, s.status)
        assertEquals(0, s.repCount)
        assertEquals(CountConfidence.BAJA, s.confidence)
        assertEquals(ViewKind.UNKNOWN, s.viewKind)
        assertNull(s.setKey)
        assertNull(s.exerciseName)
        for (value in listOf(s.tutMs, s.tempoEccMs, s.tempoConMs, s.romMeanDeg, s.romCvPct, s.velocityUnit, s.velocityLossPct, s.mcvFirst, s.mcvBest, s.mcvLast, s.peakBest, s.scaleSource, s.plateDiameterMm, s.barDeviation, s.barDeviationUnit, s.trunkMeanDeg, s.sourceFps)) {
            assertNull(value)
        }
        assertEquals(VideoAnalysisSummary.EMPTY_FLAGS_JSON, s.flagsJson)
        assertTrue(s.flags.isEmpty())
        assertTrue(s.observations.isEmpty())
    }

    @Test
    fun numbers_that_are_not_finite_are_stored_as_no_data() {
        val broken = report.copy(
            mcvBest = Double.NaN,
            romMeanDeg = Double.POSITIVE_INFINITY,
            sourceFps = Double.NaN,
            velocityLossPct = Double.NEGATIVE_INFINITY,
            barDeviation = Double.NaN,
            trunkMeanDeg = Double.NaN,
        )

        val s = broken.toSummary(media(), "back_squat", null, Double.NaN, "/x.analysis.json", 1L)

        assertNull(s.mcvBest)
        assertNull(s.romMeanDeg)
        assertNull(s.sourceFps)
        assertNull(s.velocityLossPct)
        assertNull(s.barDeviation)
        assertNull(s.trunkMeanDeg)
        assertNull(s.loadKg)
        assertEquals(0.41, s.mcvFirst!!, 0.0)
    }

    @Test
    fun the_flags_and_the_observations_travel_in_compact_json() {
        val s = summary()

        assertFalse("compacto, sin saltos de línea", '\n' in s.flagsJson)
        assertTrue(s.flagsJson.startsWith("{\"flags\":[\"MULTI_PERSON\",\"PLATE_ELLIPSE\",\"ROLL_CORRECTED\"],\"observations\":[{"))
        assertEquals(setOf(AnalysisFlag.MULTI_PERSON, AnalysisFlag.ROLL_CORRECTED, AnalysisFlag.PLATE_ELLIPSE), s.flags)
        assertEquals(report.observations, s.observations)
        assertEquals("tronco_inclinado", s.observations.first().code)
        assertEquals(3_000_000L, s.observations.first().atUs)
        assertNull(s.observations.last().atUs)
    }

    @Test
    fun a_flag_this_version_does_not_know_or_a_corrupt_payload_reads_without_failing() {
        val s = summary()

        val future = s.copy(flagsJson = """{"flags":["MULTI_PERSON","BANDERA_DEL_FUTURO"],"observations":[],"extra":1}""")
        assertEquals(setOf(AnalysisFlag.MULTI_PERSON), future.flags)

        for (broken in listOf("", "no es json", "{", "[]", "null")) {
            val damaged = s.copy(flagsJson = broken)
            assertTrue("«$broken»", damaged.flags.isEmpty())
            assertTrue("«$broken»", damaged.observations.isEmpty())
        }
    }

    @Test
    fun the_summary_survives_the_trip_through_the_room_row() {
        val s = summary()

        val entity = s.toEntity()

        // Los enums se guardan por nombre y la columna de la vista se llama viewKind.
        assertEquals("DONE", entity.status)
        assertEquals("SQUAT", entity.profileId)
        assertEquals("SIDE", entity.viewKind)
        assertEquals("ALTA", entity.confidence)
        assertEquals("MPS", entity.velocityUnit)
        assertEquals("PLATE", entity.scaleSource)
        assertEquals(s, entity.toSummary())
        assertEquals(entity, entity.toSummary().toEntity())
    }

    @Test
    fun a_row_written_by_another_version_reads_without_failing() {
        val row = VideoAnalysisEntity(
            mediaId = "m1",
            status = "ESTADO_NUEVO",
            progressPct = 10,
            engineVersion = 9,
            poseVersion = 9,
            profileId = "PERFIL_NUEVO",
            exerciseKey = "squat",
            recordedAtMs = 1L,
            viewKind = "VISTA_NUEVA",
            confidence = "CONFIANZA_NUEVA",
            velocityUnit = "UNIDAD_NUEVA",
            scaleSource = "ESCALA_NUEVA",
            flagsJson = "{}",
            createdAtMs = 1L,
            updatedAtMs = 1L,
        )

        val s = row.toSummary()

        // Un estado desconocido no puede quedarse «en curso» para siempre.
        assertEquals(VideoAnalysisStatus.FAILED, s.status)
        for (value in listOf(s.profileId, s.viewKind, s.confidence, s.velocityUnit, s.scaleSource)) assertNull(value)
        assertEquals(9, s.engineVersion)
    }

    @Test
    fun a_pending_row_carries_only_the_state_and_the_keys() {
        val s = VideoAnalysisSummary.pending(
            media = media(),
            status = VideoAnalysisStatus.QUEUED,
            exerciseKey = "back_squat",
            configurationKey = "back_squat__barbell",
            loadKg = 100.0,
            nowMs = 7_000L,
        )

        assertEquals(VideoAnalysisStatus.QUEUED, s.status)
        assertFalse(s.isDone)
        assertEquals(0, s.progressPct)
        assertEquals(TechniqueAnalysisEngine.ENGINE_VERSION, s.engineVersion)
        assertEquals(VideoAnalysisSummary.EMPTY_FLAGS_JSON, s.flagsJson)
        assertEquals("squat_2_L", s.setKey)
        assertEquals(1_780_000_000_000L, s.recordedAtMs)
        assertEquals(7_000L, s.createdAtMs)
        assertEquals(7_000L, s.updatedAtMs)
        for (value in listOf(s.repCount, s.confidence, s.profileId, s.reportPath, s.romMeanDeg, s.errorCode)) assertNull(value)

        val failed = VideoAnalysisSummary.pending(
            media = media(),
            status = VideoAnalysisStatus.FAILED,
            exerciseKey = "back_squat",
            configurationKey = null,
            loadKg = null,
            nowMs = 8_000L,
            progressPct = 140,
            errorCode = "decoder_died",
        )
        assertEquals("decoder_died", failed.errorCode)
        assertEquals("el avance no pasa de 100", 100, failed.progressPct)
        assertEquals(failed, failed.toEntity().toSummary())
    }

    @Test
    fun exerciseKey_prefers_the_catalog_definition_then_the_canonical_id_then_the_normalised_name() {
        assertEquals("back_squat", media().techniqueExerciseKey())
        assertEquals("lower_squat", media(definition = null).techniqueExerciseKey())
        assertEquals("lower_squat", media(definition = "  ").techniqueExerciseKey())
        assertEquals("name:sentadilla bulgara", media(definition = null, canonical = null, name = "  Sentadilla   BÚLGARA ").techniqueExerciseKey())
        assertEquals("name:", media(definition = null, canonical = "", name = null).techniqueExerciseKey())
    }

    @Test
    fun configurationKey_is_the_catalog_configuration_or_null() {
        assertEquals("back_squat__barbell", media().techniqueConfigurationKey())
        assertNull(media(configuration = null).techniqueConfigurationKey())
        assertNull(media(configuration = "").techniqueConfigurationKey())
    }

    @Test
    fun the_name_is_normalised_without_accents_or_case() {
        assertEquals("press de banca inclinado", normalizeExerciseName("Press de Banca Inclinado"))
        assertEquals("flexion de rodilla", normalizeExerciseName("Flexión de  rodilla"))
        assertEquals("curl femoral", normalizeExerciseName("\tCURL\u00A0Femoral\n"))
        assertEquals("", normalizeExerciseName(null))
        assertEquals("", normalizeExerciseName("   "))
    }

    @Test
    fun setKey_is_the_workout_set_key_with_the_side_normalised() {
        assertEquals("squat_2", media(side = null).techniqueSetKey())
        assertEquals("squat_2_L", media(side = "left").techniqueSetKey())
        assertEquals("squat_2_R", media(side = "right").techniqueSetKey())
        assertEquals("squat_0", media(setIndex = 0, side = null).techniqueSetKey())
        assertNull(media(setIndex = null).techniqueSetKey())
        assertNull(media(exerciseId = null).techniqueSetKey())
        assertNull(media(exerciseId = " ").techniqueSetKey())
    }

    // ---- Ajustes de la técnica en vídeo (§3.10) -----------------------------------------------------------------------

    @Test
    fun the_technique_video_settings_start_with_the_documented_defaults() {
        val settings = Settings()

        assertEquals(TechniqueVideoSettings(analyzeVideos = true, prefer60Fps = true, plateDiameterMm = 450, maxRecordSeconds = 180), settings.techniqueVideo)
    }

    @Test
    fun settings_saved_before_this_field_existed_load_with_the_defaults() {
        // Los Settings se guardan con encodeDefaults: se simula uno antiguo quitándole el campo nuevo.
        val current = Json.parseToJsonElement(dbJson.encodeToString(Settings())).jsonObject
        assertTrue("el campo nuevo se guarda con encodeDefaults", "techniqueVideo" in current)
        val old = JsonObject(current.filterKeys { it != "techniqueVideo" })
        assertFalse("techniqueVideo" in old)

        val loaded = dbJson.decodeFromJsonElement(Settings.serializer(), old)

        assertEquals(TechniqueVideoSettings(), loaded.techniqueVideo)
        assertEquals(Settings().username, loaded.username)
    }

    @Test
    fun custom_technique_video_settings_round_trip_and_leave_the_rest_of_the_settings_alone() {
        val custom = Settings(
            username = "Valentina",
            techniqueVideo = TechniqueVideoSettings(analyzeVideos = false, prefer60Fps = false, plateDiameterMm = 400, maxRecordSeconds = 90),
        )

        val back = dbJson.decodeFromString(Settings.serializer(), dbJson.encodeToString(custom))

        assertEquals(custom.techniqueVideo, back.techniqueVideo)
        assertEquals(400, back.techniqueVideo.plateDiameterMm)
        assertEquals("Valentina", back.username)
        // La bandera vieja no se toca: VT9 retira sus lecturas.
        assertEquals(Settings().workoutFeatureFlags, back.workoutFeatureFlags)
    }

    @Test
    fun a_new_settings_json_read_by_the_current_code_ignores_fields_it_does_not_know() {
        val json = """{"techniqueVideo":{"analyzeVideos":false,"campoFuturo":1},"campoFuturo2":"x"}"""

        val loaded = dbJson.decodeFromString(Settings.serializer(), json)

        assertFalse(loaded.techniqueVideo.analyzeVideos)
        assertEquals(450, loaded.techniqueVideo.plateDiameterMm)
        assertEquals(180, loaded.techniqueVideo.maxRecordSeconds)
    }
}
