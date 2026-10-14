package com.example.kpkn.domain.biomechanics.profiles

import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfileId.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AnalysisProfileResolverTest {

    private fun ref(
        definitionId: String? = null,
        pattern: String? = null,
        equipment: String? = "barbell",
        name: String = "Ejercicio de prueba",
        laterality: String? = "BILATERAL",
        isCustom: Boolean = false,
        override: AnalysisProfileId? = null,
        guess: String? = null,
    ) = ExerciseRef(
        definitionId = definitionId,
        configurationId = definitionId?.let { "${it}__$equipment" },
        movementPatternId = pattern,
        equipmentId = equipment,
        laterality = laterality,
        name = name,
        isCustom = isCustom,
        userProfileOverride = override,
        guessedMovementPatternId = guess,
    )

    private fun supported(ref: ExerciseRef): ProfileResolution.Supported {
        val resolution = AnalysisProfileResolver.resolve(ref)
        if (resolution !is ProfileResolution.Supported) fail("Se esperaba Supported para $ref y salió $resolution")
        return resolution as ProfileResolution.Supported
    }

    private fun unsupported(ref: ExerciseRef): ProfileResolution.Unsupported {
        val resolution = AnalysisProfileResolver.resolve(ref)
        if (resolution !is ProfileResolution.Unsupported) fail("Se esperaba Unsupported para $ref y salió $resolution")
        return resolution as ProfileResolution.Unsupported
    }

    private class Case(val definitionId: String, val pattern: String, val equipment: String, val expected: AnalysisProfileId)

    private fun case(definitionId: String, pattern: String, equipment: String, expected: AnalysisProfileId) =
        Case(definitionId, pattern, equipment, expected)

    /** Pares escritos a mano: un caso típico por patrón soportado y todas las excepciones por definición de la tabla. */
    private val supportedCases = listOf(
        // Un caso típico por patrón soportado.
        case("high_bar_back_squat", "knee_dominant", "barbell", SQUAT),
        case("sumo_squat", "knee_hip_dominant", "dumbbells", SQUAT),
        case("sissy_squat", "knee_dominant_lengthened", "bodyweight", MACHINE_SQUAT),
        case("bulgarian_split_squat", "unilateral_knee_dominant", "dumbbells", LUNGE),
        case("glutes_zancada_cruzada", "unilateral_hip_dominant", "dumbbells", LUNGE),
        case("conventional_deadlift", "deadlift", "barbell", DEADLIFT),
        case("hams_peso_muerto_sumo_deficit", "hip_hinge_deficit", "barbell", DEADLIFT),
        case("romanian_deadlift", "romanian_deadlift", "dumbbells", HINGE),
        case("hams_peso_muerto_rumano_sumo_deficit", "romanian_deadlift_deficit", "barbell", HINGE),
        case("good_morning", "hip_hinge", "barbell", HINGE),
        case("stiff_leg_deadlift", "hip_hinge_lengthened", "barbell", HINGE),
        case("hip_thrust", "hip_extension", "barbell", HIP_THRUST),
        case("glutes_frog_pumps", "hip_extension_external_rotation", "bodyweight", HIP_THRUST),
        case("back_extension_lumbar", "spinal_extension", "machine", BACK_EXTENSION),
        case("bench_press", "horizontal_push", "barbell", BENCH),
        case("military_press", "vertical_push", "barbell", OHP),
        case("pull_up", "vertical_pull", "bodyweight", PULL_VERTICAL),
        case("deltoides_remo_menton", "vertical_pull_abduction", "barbell", PULL_VERTICAL),
        case("conventional_row", "horizontal_pull", "barbell", ROW),
        case("standing_biceps_curl", "elbow_flexion", "dumbbells", CURL),
        case("triceps_press_frances", "elbow_extension", "ez_bar", TRICEPS_EXTENDED_REST),
        case("standing_lateral_raise", "shoulder_abduction_full_rom", "dumbbells", RAISE),
        case("back_y_raises", "shoulder_abduction_diagonal", "dumbbells", RAISE),
        case("deltoides_elevaciones_frontales", "shoulder_flexion", "dumbbells", RAISE),
        case("quads_extension_cuadriceps", "knee_extension", "machine", LEG_EXTENSION),
        case("seated_leg_curl", "knee_flexion", "machine", LEG_CURL),
        case("calf_raise", "plantar_flexion", "machine", CALF),
        case("back_encogimientos", "scapular_elevation", "dumbbells", SHRUG),
        // Excepciones por definición: máquinas de sentadilla.
        case("quads_prensa_piernas", "knee_dominant", "machine", MACHINE_SQUAT),
        case("quads_sentadilla_hack", "knee_dominant", "machine", MACHINE_SQUAT),
        case("quads_sentadilla_hack_invertida_maquina", "knee_dominant", "machine", MACHINE_SQUAT),
        case("quads_sentadilla_v_squat", "knee_dominant", "machine", MACHINE_SQUAT),
        case("quads_sentadilla_v_squat_invertida_maquina", "knee_dominant", "machine", MACHINE_SQUAT),
        case("pendulum_squat", "knee_dominant", "machine", MACHINE_SQUAT),
        case("pendulum_squat", "unilateral_knee_dominant", "machine", MACHINE_SQUAT),
        case("quads_zancada_inversa_maquina_hack", "unilateral_knee_dominant", "machine", MACHINE_SQUAT),
        case("quads_zancada_inversa_maquina_v_squat", "unilateral_knee_dominant", "machine", MACHINE_SQUAT),
        // belt_squat vale SQUAT en sus dos patrones.
        case("belt_squat", "knee_dominant", "machine", SQUAT),
        case("belt_squat", "unilateral_knee_dominant", "machine", SQUAT),
        // Anderson sigue siendo SQUAT (su arranque se prueba aparte).
        case("quads_sentadilla_anderson", "knee_dominant", "barbell", SQUAT),
        case("quads_sentadilla_anderson_frontal_barra_recta", "knee_dominant", "barbell", SQUAT),
        // Subidas al cajón.
        case("step_up", "unilateral_knee_dominant", "dumbbells", STEP_UP),
        case("quads_step_up_cajon_frontal", "unilateral_knee_dominant", "barbell", STEP_UP),
        case("quads_step_up_cajon_zercher", "unilateral_knee_dominant", "barbell", STEP_UP),
        case("glutes_step_up_gluteo", "unilateral_hip_dominant", "dumbbells", STEP_UP),
        // Bisagra con déficit y extensiones de cadera.
        case("hams_peso_muerto_piernas_rigidas_deficit", "hip_hinge_deficit", "barbell", HINGE),
        case("glutes_hiperextension_45", "hip_extension", "plate", BACK_EXTENSION),
        case("glutes_hiperextension_45_zercher", "hip_extension", "barbell", BACK_EXTENSION),
        // Empujes que dejan de ser banca.
        case("push_up", "horizontal_push", "bodyweight", PUSH_UP),
        case("knee_push_up", "horizontal_push", "bodyweight", PUSH_UP),
        case("diamond_push_up", "horizontal_push", "bodyweight", PUSH_UP),
        case("archer_push_up", "horizontal_push", "bodyweight", PUSH_UP),
        case("pike_push_up", "vertical_push", "bodyweight", PUSH_UP),
        case("flat_chest_fly", "horizontal_push", "dumbbells", FLY),
        case("incline_chest_fly", "horizontal_push", "dumbbells", FLY),
        case("decline_chest_fly", "horizontal_push", "dumbbells", FLY),
        case("tren_superior_fondos", "horizontal_push", "bodyweight", DIP),
        case("tren_superior_press_inclinado_maquina_convergente", "horizontal_push", "machine", BENCH),
        case("tren_superior_press_pecho_maquina_convergente", "horizontal_push", "machine", BENCH),
        case("tren_superior_press_unilateral_polea", "horizontal_push", "cable", BENCH),
        case("tren_superior_press_banda_resistencia", "horizontal_push", "band", BENCH),
        // Abducción horizontal y tríceps.
        case("rear_delt_raise", "horizontal_abduction", "dumbbells", RAISE),
        case("triceps_pushdown", "elbow_extension", "cable", TRICEPS_FLEXED_REST),
        case("triceps_patada", "elbow_extension", "dumbbells", TRICEPS_FLEXED_REST),
        case("katana_extension", "elbow_extension", "cable", TRICEPS_FLEXED_REST),
        case("crossbody_triceps_extension", "elbow_extension", "cable", TRICEPS_FLEXED_REST),
        case("triceps_press_maquina", "elbow_extension", "machine", TRICEPS_FLEXED_REST),
        case("triceps_flexiones_esfinge", "elbow_extension", "bodyweight", TRICEPS_FLEXED_REST),
        case("triceps_fondos_entre_bancos", "elbow_extension", "bodyweight", DIP),
    )

    @Test
    fun handWrittenPairsResolveToTheirProfile() {
        assertTrue("Pares escritos a mano: ${supportedCases.size}", supportedCases.size >= 40)
        val mismatches = mutableListOf<String>()
        for (case in supportedCases) {
            val resolution = AnalysisProfileResolver.resolve(ref(case.definitionId, case.pattern, case.equipment))
            val actual = (resolution as? ProfileResolution.Supported)?.profile?.id
            if (actual != case.expected) {
                mismatches += "${case.definitionId} (${case.pattern}, ${case.equipment}): esperado ${case.expected}, salió $resolution"
            }
        }
        assertTrue("Discrepancias:\n" + mismatches.joinToString("\n"), mismatches.isEmpty())
    }

    @Test
    fun theTypicalCasePerSupportedPatternCoversAllTwentyEightDefaultPatterns() {
        val supportedPatterns = ProfileExpectations.byPattern.filterValues { it.profile != null }.keys
        val covered = supportedCases.map { it.pattern }.toSet()
        assertEquals(28, supportedPatterns.size)
        assertTrue("Patrones soportados sin caso típico: ${supportedPatterns - covered}", covered.containsAll(supportedPatterns))
    }

    @Test
    fun startPhaseChangesOnlyInTheListedExceptions() {
        // Anderson: los datos dan concéntrica primero.
        assertEquals(StartPhase.CONCENTRIC_FIRST, supported(ref("quads_sentadilla_anderson", "knee_dominant", "barbell")).profile.startPhase)
        assertEquals(
            StartPhase.CONCENTRIC_FIRST,
            supported(ref("quads_sentadilla_anderson_frontal_barra_recta", "knee_dominant", "barbell")).profile.startPhase,
        )
        assertEquals(StartPhase.ECCENTRIC_FIRST, supported(ref("high_bar_back_squat", "knee_dominant", "barbell")).profile.startPhase)
        // Máquinas, polea y banda: se empuja desde el pecho.
        for ((definition, equipment) in listOf(
            "tren_superior_press_inclinado_maquina_convergente" to "machine",
            "tren_superior_press_pecho_maquina_convergente" to "machine",
            "tren_superior_press_unilateral_polea" to "cable",
            "tren_superior_press_banda_resistencia" to "band",
        )) {
            assertEquals(definition, StartPhase.CONCENTRIC_FIRST, supported(ref(definition, "horizontal_push", equipment)).profile.startPhase)
        }
        // Banca con barra, mancuernas y Smith sigue arrancando bajando.
        for (equipment in listOf("barbell", "dumbbells", "smith_machine", "kettlebell")) {
            assertEquals(equipment, StartPhase.ECCENTRIC_FIRST, supported(ref("bench_press", "horizontal_push", equipment)).profile.startPhase)
        }
        // Y el mismo press en máquina o polea (§5: «máquina/cable/banda: C») arranca subiendo aunque la definición sea la de banca.
        for (equipment in listOf("machine", "cable")) {
            assertEquals(equipment, StartPhase.CONCENTRIC_FIRST, supported(ref("bench_press", "horizontal_push", equipment)).profile.startPhase)
            assertEquals(equipment, StartPhase.CONCENTRIC_FIRST, supported(ref("incline_bench_press", "horizontal_push", equipment)).profile.startPhase)
        }
    }

    @Test
    fun unsupportedCasesCarryTheirStableCodeAndSpanishText() {
        val cases = listOf(
            // Excepciones por definición.
            Triple("wall_sit", "knee_dominant", UnsupportedReasons.ISOMETRIC),
            Triple("walking_lunge", "unilateral_knee_dominant", UnsupportedReasons.WALKING),
            Triple("quads_zancada_caminando_frontal_barra_recta", "unilateral_knee_dominant", UnsupportedReasons.WALKING),
            Triple("quads_zancada_caminando_zercher_barra_recta", "unilateral_knee_dominant", UnsupportedReasons.WALKING),
            Triple("zercher_carry", "spinal_extension", UnsupportedReasons.WALKING),
            Triple("back_superman_suelo", "spinal_extension", UnsupportedReasons.NOT_YET),
            Triple("glutes_patada_gluteo", "hip_extension", UnsupportedReasons.NOT_YET),
            Triple("reverse_hyper", "hip_extension", UnsupportedReasons.NOT_YET),
            Triple("tren_superior_cruce_poleas", "horizontal_push", UnsupportedReasons.MACHINE_PLANE),
            Triple("push_jerk", "vertical_push", UnsupportedReasons.OLYMPIC),
            Triple("split_jerk", "vertical_push", UnsupportedReasons.OLYMPIC),
            Triple("lying_pullover", "vertical_pull", UnsupportedReasons.NOT_YET),
            Triple("pullover", "vertical_pull", UnsupportedReasons.NOT_YET),
            Triple("seated_machine_pullover", "vertical_pull", UnsupportedReasons.NOT_YET),
            Triple("negative_pull_up", "vertical_pull", UnsupportedReasons.ECCENTRIC_ONLY_V2),
            Triple("glute_ham_raise", "knee_flexion", UnsupportedReasons.ECCENTRIC_ONLY_V2),
            // Patrones sin soporte.
            Triple("power_clean", "hip_hinge_explosive", UnsupportedReasons.OLYMPIC),
            Triple("hams_swing_kettlebell_dos_manos", "hip_hinge_explosive", UnsupportedReasons.OLYMPIC),
            Triple("core_plancha", "anti_extension_isometric", UnsupportedReasons.CORE),
            Triple("core_crunch_maquina", "trunk_flexion", UnsupportedReasons.CORE),
            Triple("core_elevacion_piernas", "hip_flexion", UnsupportedReasons.CORE),
            Triple("forearms_paseo_del_granjero", "isometric_grip", UnsupportedReasons.GRIP),
            Triple("forearms_curl_muneca_sentado", "wrist_flexion", UnsupportedReasons.GRIP),
            Triple("neck_extension_cuello", "neck_extension", UnsupportedReasons.NECK),
            Triple("neck_flexion_cuello", "neck_flexion", UnsupportedReasons.NECK),
            Triple("hip_adduction", "hip_adduction", UnsupportedReasons.MACHINE_PLANE),
            Triple("hip_abduction", "hip_abduction", UnsupportedReasons.MACHINE_PLANE),
            Triple("copenhagen_plank", "hip_adduction_dynamic", UnsupportedReasons.MACHINE_PLANE),
            Triple("quads_sentadilla_cosaca", "lateral_knee_dominant", UnsupportedReasons.MACHINE_PLANE),
            Triple("reverse_pec_fly", "horizontal_abduction", UnsupportedReasons.MACHINE_PLANE),
            Triple("back_encogimientos_kelso", "scapular_retraction", UnsupportedReasons.MACHINE_PLANE),
            Triple("hams_curl_nordic_peso_corporal", "eccentric_knee_flexion", UnsupportedReasons.ECCENTRIC_ONLY_V2),
            Triple("quads_reverse_nordic_peso_corporal", "biarticular_lengthened", UnsupportedReasons.ECCENTRIC_ONLY_V2),
            Triple("deltoides_press_landmine_unilateral", "diagonal_push", UnsupportedReasons.NOT_YET),
            Triple("calves_tibial_anterior", "ankle_dorsiflexion", UnsupportedReasons.NOT_YET),
            Triple("back_dominadas_escapulares", "scapular_depression", UnsupportedReasons.NOT_YET),
        )
        for ((definition, pattern, reason) in cases) {
            val resolution = unsupported(ref(definition, pattern, "bodyweight"))
            assertEquals(definition, reason, resolution.reason)
            assertTrue(definition, resolution.text.isNotBlank())
            assertEquals(UnsupportedReasons.textOf(reason), resolution.text)
        }
    }

    @Test
    fun theFixedUnsupportedTextsAreTheOnesOfTheBrief() {
        assertEquals("Los levantamientos olímpicos aún no se analizan", UnsupportedReasons.textOf("olympic"))
        assertEquals("Los ejercicios isométricos aún no se analizan", UnsupportedReasons.textOf("isometric"))
        assertEquals("Este movimiento no se ve bien de lado", UnsupportedReasons.textOf("machine_plane"))
        assertEquals("Graba sin desplazarte para poder contar", UnsupportedReasons.textOf("walking"))
        // Los nueve códigos del brief más «not_yet», cada uno con un texto distinto y en tipo frase.
        val codes = setOf("olympic", "isometric", "core", "grip", "neck", "machine_plane", "walking", "eccentric_only_v2", "unknown_pattern", "not_yet")
        assertEquals(codes, UnsupportedReasons.texts.keys)
        assertEquals(codes.size, UnsupportedReasons.texts.values.toSet().size)
        UnsupportedReasons.texts.values.forEach { text ->
            assertTrue(text, text.first().isUpperCase() && !text.endsWith("."))
        }
    }

    @Test
    fun usesPlateDependsOnTheMaterialAndOnTheProfileHavingAPlateSignal() {
        // Con barra: sí. Con mancuernas: no.
        assertTrue(supported(ref("high_bar_back_squat", "knee_dominant", "barbell")).profile.usesPlate)
        assertFalse(supported(ref("front_squat", "knee_dominant", "dumbbells")).profile.usesPlate)
        assertTrue(supported(ref("bench_press", "horizontal_push", "smith_machine")).profile.usesPlate)
        assertFalse(supported(ref("bench_press", "horizontal_push", "dumbbells")).profile.usesPlate)
        assertTrue(supported(ref("conventional_deadlift", "deadlift", "hex_bar")).profile.usesPlate)
        assertTrue(supported(ref("high_bar_back_squat", "knee_dominant", "safety_bar")).profile.usesPlate)
        assertTrue(supported(ref("t_bar_row", "horizontal_pull", "t_bar")).profile.usesPlate)
        assertFalse(supported(ref("t_bar_row", "horizontal_pull", "machine")).profile.usesPlate)
        assertFalse(supported(ref("conventional_deadlift", "deadlift", "dumbbells")).profile.usesPlate)
        assertTrue(supported(ref("military_press", "vertical_push", "barbell")).profile.usesPlate)
        assertFalse(supported(ref("military_press", "vertical_push", "cable")).profile.usesPlate)
        assertTrue(supported(ref("hip_thrust", "hip_extension", "barbell")).profile.usesPlate)
        assertTrue(supported(ref("romanian_deadlift", "romanian_deadlift", "barbell")).profile.usesPlate)
        // Sin señal de disco en el perfil no hay disco aunque haya barra.
        assertFalse(supported(ref("standing_biceps_curl", "elbow_flexion", "barbell")).profile.usesPlate)
        assertFalse(supported(ref("step_up", "unilateral_knee_dominant", "barbell")).profile.usesPlate)
        assertFalse(supported(ref("calf_raise", "plantar_flexion", "smith_machine")).profile.usesPlate)
        // Sin material conocido, tampoco.
        assertFalse(supported(ref("high_bar_back_squat", "knee_dominant", null)).profile.usesPlate)
    }

    @Test
    fun theFramingAsksForThePlateOnlyWhenTheFourPlateProfilesUseIt() {
        assertTrue(supported(ref("high_bar_back_squat", "knee_dominant", "barbell")).profile.framing.needsPlate)
        assertFalse(supported(ref("front_squat", "knee_dominant", "dumbbells")).profile.framing.needsPlate)
        assertTrue(supported(ref("conventional_deadlift", "deadlift", "barbell")).profile.framing.needsPlate)
        assertFalse(supported(ref("conventional_deadlift", "deadlift", "dumbbells")).profile.framing.needsPlate)
        assertTrue(supported(ref("bench_press", "horizontal_push", "barbell")).profile.framing.needsPlate)
        assertFalse(supported(ref("bench_press", "horizontal_push", "dumbbells")).profile.framing.needsPlate)
        assertTrue(supported(ref("military_press", "vertical_push", "barbell")).profile.framing.needsPlate)
        // HINGE, HIP_THRUST, LUNGE y ROW usan el disco como señal, pero el encuadre no lo exige.
        val hinge = supported(ref("romanian_deadlift", "romanian_deadlift", "barbell")).profile
        assertTrue(hinge.usesPlate)
        assertFalse(hinge.framing.needsPlate)
        assertFalse(supported(ref("hip_thrust", "hip_extension", "barbell")).profile.framing.needsPlate)
    }

    @Test
    fun mvtOnlyForTheListedExceptions() {
        fun mvt(definition: String, pattern: String, equipment: String?) = supported(ref(definition, pattern, equipment)).profile.mvtMps

        // Sentadilla trasera con barra o Smith: 0,30. Frontal, hexagonal o con otro material: null.
        assertEquals(0.30, mvt("high_bar_back_squat", "knee_dominant", "barbell")!!, 0.0)
        assertEquals(0.30, mvt("high_bar_back_squat", "knee_dominant", "smith_machine")!!, 0.0)
        assertEquals(0.30, mvt("low_bar_back_squat", "knee_dominant", "barbell")!!, 0.0)
        assertEquals(0.30, mvt("low_bar_back_squat", "knee_dominant", "smith_machine")!!, 0.0)
        assertEquals(0.30, mvt("paused_back_squat", "knee_dominant", "barbell")!!, 0.0)
        assertNull(mvt("front_squat", "knee_dominant", "barbell"))
        assertNull(mvt("high_bar_back_squat", "knee_dominant", "safety_bar"))
        assertNull(mvt("sumo_squat", "knee_hip_dominant", "barbell"))
        // Banca: 0,17 con barra o Smith.
        assertEquals(0.17, mvt("bench_press", "horizontal_push", "barbell")!!, 0.0)
        assertEquals(0.17, mvt("bench_press", "horizontal_push", "smith_machine")!!, 0.0)
        assertEquals(0.17, mvt("paused_bench_press", "horizontal_push", "barbell")!!, 0.0)
        assertNull(mvt("bench_press", "horizontal_push", "dumbbells"))
        assertNull(mvt("incline_bench_press", "horizontal_push", "barbell"))
        assertNull(mvt("close_grip_bench_press", "horizontal_push", "barbell"))
        // Peso muerto: 0,15 solo convencional y sumo con barra.
        assertEquals(0.15, mvt("conventional_deadlift", "deadlift", "barbell")!!, 0.0)
        assertEquals(0.15, mvt("sumo_deadlift", "deadlift", "barbell")!!, 0.0)
        assertNull(mvt("conventional_deadlift", "deadlift", "hex_bar"))
        assertNull(mvt("conventional_deadlift", "deadlift", "smith_machine"))
        assertNull(mvt("conventional_deadlift", "deadlift", "dumbbells"))
        assertNull(mvt("romanian_deadlift", "romanian_deadlift", "barbell"))
        // Press militar con barra: 0,19.
        assertEquals(0.19, mvt("military_press", "vertical_push", "barbell")!!, 0.0)
        assertNull(mvt("military_press", "vertical_push", "dumbbells"))
        assertNull(mvt("seated_shoulder_press", "vertical_push", "barbell"))
        // Remo con foca y barra: 0,50.
        assertEquals(0.50, mvt("seal_row", "horizontal_pull", "barbell")!!, 0.0)
        assertNull(mvt("seal_row", "horizontal_pull", "dumbbells"))
        assertNull(mvt("conventional_row", "horizontal_pull", "barbell"))
        // Sin material o sin definición no hay MVT.
        assertNull(mvt("high_bar_back_squat", "knee_dominant", null))
        assertNull(supported(ref(definitionId = null, pattern = "knee_dominant", equipment = "barbell")).profile.mvtMps)
        // El resto de los perfiles base no traen MVT.
        assertTrue(AnalysisProfiles.all.all { it.mvtMps == null })
    }

    @Test
    fun theUserOverrideWinsOverEverything() {
        // Sobre una excepción que no se soporta.
        val overWallSit = supported(ref("wall_sit", "knee_dominant", "bodyweight", override = SQUAT))
        assertEquals(SQUAT, overWallSit.profile.id)
        assertEquals("user", overWallSit.source)
        // Sobre la tabla por patrón.
        val overBench = supported(ref("bench_press", "horizontal_push", "barbell", override = ROW))
        assertEquals(ROW, overBench.profile.id)
        assertEquals("user", overBench.source)
        // Sobre un patrón sin soporte y sobre un ejercicio propio.
        assertEquals(CURL, supported(ref(pattern = "neck_flexion", equipment = "cable", override = CURL)).profile.id)
        assertEquals(RAISE, supported(ref(pattern = null, equipment = null, isCustom = true, override = RAISE)).profile.id)
        // El material sigue mandando en el disco, y la MVT solo vale con el perfil al que pertenece.
        val squat = supported(ref("high_bar_back_squat", "knee_dominant", "barbell", override = SQUAT)).profile
        assertTrue(squat.usesPlate)
        assertTrue(squat.framing.needsPlate)
        assertEquals(0.30, squat.mvtMps!!, 0.0)
        assertNull(supported(ref("high_bar_back_squat", "knee_dominant", "barbell", override = HINGE)).profile.mvtMps)
        assertNull(supported(ref(null, null, "dumbbells", isCustom = true, override = SQUAT)).profile.mvtMps)
        assertFalse(supported(ref(null, null, "dumbbells", isCustom = true, override = SQUAT)).profile.usesPlate)
    }

    @Test
    fun aCustomExerciseNeedsTheUserToChooseAndOffersItsGuess() {
        fun guess(patternId: String?, name: String = "Mi ejercicio"): AnalysisProfileId? {
            val resolution = AnalysisProfileResolver.resolve(
                ref(pattern = null, equipment = null, name = name, isCustom = true, guess = patternId),
            )
            if (resolution !is ProfileResolution.NeedsUserChoice) fail("Se esperaba NeedsUserChoice y salió $resolution")
            return (resolution as ProfileResolution.NeedsUserChoice).guess
        }
        assertEquals(SQUAT, guess("knee_dominant"))
        assertEquals(HINGE, guess("hip_hinge"))
        assertEquals(BENCH, guess("horizontal_push"))
        assertEquals(OHP, guess("vertical_push"))
        assertEquals(ROW, guess("horizontal_pull"))
        assertEquals(PULL_VERTICAL, guess("vertical_pull"))
        assertEquals(CURL, guess("elbow_flexion"))
        assertEquals(TRICEPS_EXTENDED_REST, guess("elbow_extension"))
        // Los ids de ExercisePatternDetector que el catálogo no tiene: solo «shoulder_abduction» da perfil.
        assertEquals(RAISE, guess("shoulder_abduction"))
        assertNull(guess("anti_extension"))
        assertNull(guess("anti_rotation"))
        assertNull(guess("core_flexion"))
        // Un patrón conocido pero sin soporte no sugiere nada.
        assertNull(guess("horizontal_abduction"))
        assertNull(guess("hip_abduction"))
        assertNull(guess("hip_adduction"))
        // Sin conjetura, o con un id que no existe, tampoco.
        assertNull(guess(null))
        assertNull(guess("no_existe"))
        // El nombre nunca resuelve solo: ni siquiera uno que el detector entendería.
        assertNull(guess(null, name = "Sentadilla búlgara"))
        assertNull(guess(null, name = "Curl femoral"))
    }

    @Test
    fun anExerciseWithoutPatternAsksTheUserEvenIfItIsNotCustom() {
        val resolution = AnalysisProfileResolver.resolve(ref(definitionId = "algo", pattern = null, name = "Press banca", isCustom = false))
        assertEquals(ProfileResolution.NeedsUserChoice(null), resolution)
        val withGuess = AnalysisProfileResolver.resolve(ref(pattern = null, isCustom = false, guess = "knee_dominant"))
        assertEquals(ProfileResolution.NeedsUserChoice(SQUAT), withGuess)
    }

    @Test
    fun anUnknownPatternOfACatalogExerciseIsUnsupportedButACustomOneAsks() {
        val catalogExercise = unsupported(ref("ejercicio_nuevo", "patron_nuevo", "barbell"))
        assertEquals(UnsupportedReasons.UNKNOWN_PATTERN, catalogExercise.reason)
        assertEquals("Este tipo de ejercicio aún no se analiza", catalogExercise.text)
        val custom = AnalysisProfileResolver.resolve(ref(pattern = "patron_nuevo", isCustom = true, guess = "vertical_pull"))
        assertEquals(ProfileResolution.NeedsUserChoice(PULL_VERTICAL), custom)
    }

    @Test
    fun theSourceSaysWhereTheProfileCameFrom() {
        assertEquals("pattern", supported(ref("high_bar_back_squat", "knee_dominant", "barbell")).source)
        assertEquals("pattern", supported(ref(null, "knee_dominant", "barbell")).source)
        assertEquals("definition", supported(ref("quads_prensa_piernas", "knee_dominant", "machine")).source)
        assertEquals("definition", supported(ref("belt_squat", "knee_dominant", "machine")).source)
        assertEquals("user", supported(ref("high_bar_back_squat", "knee_dominant", "barbell", override = LUNGE)).source)
    }

    @Test
    fun theDefinitionExceptionBeatsThePatternAndNeedsNoPattern() {
        // pendulum_squat es del patrón LUNGE (unilateral_knee_dominant) y aun así sale MACHINE_SQUAT.
        assertEquals(MACHINE_SQUAT, supported(ref("pendulum_squat", "unilateral_knee_dominant", "machine")).profile.id)
        // La excepción vale aunque el patrón falte o sea desconocido.
        assertEquals(STEP_UP, supported(ref("step_up", null, "dumbbells")).profile.id)
        assertEquals(UnsupportedReasons.ISOMETRIC, unsupported(ref("wall_sit", "patron_nuevo", "bodyweight")).reason)
    }

    @Test
    fun theNameAndTheLateralityNeverDecide() {
        val names = listOf("Sentadilla", "Curl femoral", "Encogimientos", "Hip thrust", "Elevaciones laterales", "")
        for (laterality in listOf("BILATERAL", "UNILATERAL", "NOT_APPLICABLE", null)) {
            for (name in names) {
                val resolution = AnalysisProfileResolver.resolve(
                    ref("seated_leg_curl", "knee_flexion", "machine", name = name, laterality = laterality),
                )
                assertEquals(LEG_CURL, (resolution as ProfileResolution.Supported).profile.id)
            }
        }
        // Y un ejercicio con nombre de sentadilla pero patrón de curl sigue siendo un curl.
        assertEquals(LEG_CURL, supported(ref("seated_leg_curl", "knee_flexion", "machine", name = "Sentadilla")).profile.id)
    }

    @Test
    fun theResolverChangesOnlyTheMaterialFieldsOfTheBaseProfile() {
        val base = AnalysisProfiles.of(BENCH)
        assertEquals(base, supported(ref("bench_press", "horizontal_push", "dumbbells")).profile)
        val withBarbell = supported(ref("bench_press", "horizontal_push", "barbell")).profile
        assertEquals(
            base.copy(usesPlate = true, framing = base.framing.copy(needsPlate = true), mvtMps = 0.17),
            withBarbell,
        )
        val withMachine = supported(ref("tren_superior_press_pecho_maquina_convergente", "horizontal_push", "machine")).profile
        assertEquals(base.copy(startPhase = StartPhase.CONCENTRIC_FIRST), withMachine)
        // Un perfil sin ajustes de material sale idéntico al de la tabla.
        assertEquals(AnalysisProfiles.of(CALF), supported(ref("calf_raise", "plantar_flexion", "machine")).profile)
    }
}
