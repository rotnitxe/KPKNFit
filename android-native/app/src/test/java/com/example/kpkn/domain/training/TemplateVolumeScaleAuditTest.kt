package com.example.kpkn.domain.training

import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.InvolvedMuscle
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.TrainingStyle
import com.example.kpkn.data.models.VolumeRecommendation
import com.example.kpkn.data.sessions.SESSION_TEMPLATES_SYSTEM
import com.example.kpkn.data.splits.SPLIT_TEMPLATES
import com.example.kpkn.domain.templates.CatalogV2TestFixture
import com.example.kpkn.screens.programdetail.components.buildVolumeCalibration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

/**
 * Auditoría diagnóstica (Fase C): demuestra que plantillas compactas/low/hf
 * dejan ~5 series semanales por músculo y que el scaler las lleva a ≥MEV sin
 * romper MRV/H6/H7. No cambia el catálogo.
 */
class TemplateVolumeScaleAuditTest {

    companion object {
        private class SeqIds : IdProvider {
            private var n = 0
            override fun newId(): String = "id_${++n}"
        }

        @BeforeClass
        @JvmStatic
        fun setUpClass() {
            CatalogCompositionTestSupport.install()
        }

        private fun calibrationFixture(): List<VolumeRecommendation> =
            buildVolumeCalibration(
                style = TrainingStyle.BODYBUILDER,
                technique = 2,
                consistency = 2,
                strength = 2,
                mobility = 2,
            ).recommendations
    }

    private fun weekSessionsFor(splitId: String, templateIds: List<String>): List<Session> {
        val templates = templateIds.map { id ->
            SESSION_TEMPLATES_SYSTEM.first { it.id == id }
        }
        val split = SPLIT_TEMPLATES.first { it.id == splitId }
        val forcedMap = mutableMapOf<Int, String>()
        split.pattern.forEachIndexed { index, label ->
            if (label != "Descanso") {
                val match = templates.firstOrNull { t ->
                    t.splitDayLabels.any { it.equals(label, ignoreCase = true) }
                } ?: templates.getOrNull(index % templates.size)
                if (match != null) {
                    forcedMap[index] = match.id
                }
            }
        }
        val prefs = com.example.kpkn.domain.templates.SuggestionPrefs(
            forcedTemplateByDayIndex = forcedMap,
        )
        return SplitApplicationEngine.buildSessionsForSplit(
            splitId = split.id,
            pattern = split.pattern,
            startDay = 1,
            existingSessions = emptyList(),
            migrationMode = SessionMigrationMode.PREBUILT,
            prefs = prefs,
            templates = SESSION_TEMPLATES_SYSTEM,
        )
    }

    @Test
    fun ppl_x6_quad_deficit_diagnostic() {
        val exercises = CatalogV2TestFixture.configurationLookup().values.toList()
        val sessions = weekSessionsFor("ppl_x6", listOf("sys-v3-hf-empuje", "sys-v3-hf-traccion", "sys-v3-hf-pierna"))
        val recommendations = calibrationFixture()
        val before = VolumeCalculator.calculateCanonicalWeeklyMuscleVolumeForSessions(sessions, exercises)
        println("BEFORE $before")
        val result = TemplateVolumeScaler.scaleWeekSessions(sessions, exercises, recommendations, SeqIds(), diagnostic = { println(it) })
        val after = VolumeCalculator.calculateCanonicalWeeklyMuscleVolumeForSessions(result.sessions, exercises)
            .associate { it.muscleName to it.weeklySets }
        println("AFTER $after RESIDUAL ${result.residualDeficitByMuscle} CAPPED ${result.cappedSessions}")
        val floor = TemplateVolumeScaler.targetsFor(recommendations).getValue("Cuádriceps").floor
        assertTrue("ppl_x6/Cuádriceps ${after["Cuádriceps"]} < $floor; residual=${result.residualDeficitByMuscle}",
            after.getValue("Cuádriceps") >= floor)
    }

    @Test
    fun compact_templates_scale_to_mev_without_breaking_ceilings() {
        val exerciseList = CatalogV2TestFixture.configurationLookup().values.toList()
        val calibration = calibrationFixture()
        val cases = listOf(
            "ppl_x6" to listOf("sys-v3-hf-empuje", "sys-v3-hf-traccion", "sys-v3-hf-pierna"),
            "ul_x6" to listOf("sys-v3-hf-torso", "sys-v3-hf-pierna"),
            "fullbody_x5" to listOf("sys-v3-hf-full"),
        )
        val evidence = mutableListOf<String>()
        cases.forEach { (splitId, templateIds) ->
            val existing = templateIds.filter { id -> SESSION_TEMPLATES_SYSTEM.any { it.id == id } }
            if (existing.isEmpty()) return@forEach
            val sessions = weekSessionsFor(splitId, existing)
            if (sessions.isEmpty()) return@forEach
            val before = VolumeCalculator.calculateCanonicalWeeklyMuscleVolumeForSessions(sessions, exerciseList)
                .associate { it.muscleName to it.weeklySets }
            val targets = TemplateVolumeScaler.targetsFor(calibration)
            val lowBefore = targets.keys.filter { muscle ->
                val current = before[muscle] ?: 0.0
                val hasDirect = sessions.flatMap { it.allExercises() }.any { exercise ->
                    val info = exerciseList.firstOrNull {
                        it.id.equals(exercise.catalogConfigurationId, ignoreCase = true)
                    } ?: return@any false
                    info.involvedMuscles.any {
                        it.role == com.example.kpkn.data.models.MuscleRole.PRIMARY &&
                            VolumeCalculator.normalizeCanonicalMuscleGroup(it.muscle) == muscle
                    }
                }
                hasDirect && current < (targets[muscle]?.floor ?: 0)
            }
            evidence += "$splitId antes: " + before.entries.sortedByDescending { it.value }.take(6)
                .joinToString { (m, v) -> "$m=$v" }
            val result = TemplateVolumeScaler.scaleWeekSessions(sessions, exerciseList, calibration, SeqIds())
            val after = VolumeCalculator.calculateCanonicalWeeklyMuscleVolumeForSessions(result.sessions, exerciseList)
                .associate { it.muscleName to it.weeklySets }
            val scaledTargets = TemplateVolumeScaler.targetsFor(calibration, before)
            val directBefore = TemplateVolumeScaler.measureDirectWeeklyForTest(sessions, exerciseList)
            targets.forEach { (muscle, t) ->
                val st = scaledTargets.getValue(muscle)
                val hasDirect = result.sessions.flatMap { it.allExercises() }.any { exercise ->
                    val info = exerciseList.firstOrNull {
                        it.id.equals(exercise.catalogConfigurationId, ignoreCase = true)
                    } ?: return@any false
                    info.involvedMuscles.any {
                        it.role == com.example.kpkn.data.models.MuscleRole.PRIMARY &&
                            VolumeCalculator.normalizeCanonicalMuscleGroup(it.muscle) == muscle
                    }
                }
                if (!hasDirect) return@forEach
                val current = after[muscle] ?: 0.0
                assertTrue(
                    "$splitId/$muscle bajo MEV tras escalar: $current < ${st.floor} (antes ${before[muscle] ?: 0.0})",
                    current >= st.floor || result.residualDeficitByMuscle.containsKey(muscle),
                )
                // La base se conserva: si ya supera el techo total, el scaler
                // no puede añadir PRIMARY. El directo nuevo nunca usa tolerancia
                // colateral, aunque sea el segundo PRIMARY de otro objetivo.
                val directAfter = TemplateVolumeScaler.measureDirectWeeklyForTest(result.sessions, exerciseList)
                val baseDirect = directBefore[muscle] ?: 0.0
                val finalDirect = directAfter[muscle] ?: 0.0
                val addedDirect = finalDirect - baseDirect
                val baseTotal = before[muscle] ?: 0.0
                if (baseTotal > st.ceiling + 0.001) {
                    assertEquals(
                        "$splitId/$muscle: base total $baseTotal > ${st.ceiling}; no debe añadirse PRIMARY",
                        0.0,
                        addedDirect,
                        0.001,
                    )
                } else {
                    assertTrue(
                        "$splitId/$muscle: directo $finalDirect > ${st.ceiling} (base $baseDirect, añadido $addedDirect)",
                        finalDirect <= st.ceiling + 0.001,
                    )
                }
                assertTrue(
                    "$splitId/$muscle: el scaler no debe recortar la base directa $baseDirect",
                    finalDirect + 0.001 >= baseDirect,
                )
            }
            result.sessions.forEach { session ->
                val effective = session.allExercises().sumOf { VolumeCalculator.countEffectiveSets(it.sets) }
                assertTrue("$splitId/${session.name}: H6 roto ($effective)", effective <= SessionCompositionPolicy.MAX_EFFECTIVE_SETS)
                val perMuscle = mutableMapOf<String, Int>()
                session.allExercises().forEach { exercise ->
                    val info = exerciseList.firstOrNull {
                        it.id.equals(exercise.catalogConfigurationId, ignoreCase = true)
                    } ?: return@forEach
                    val direct = info.involvedMuscles
                        .filter { it.role == com.example.kpkn.data.models.MuscleRole.PRIMARY }
                        .map { VolumeCalculator.normalizeCanonicalMuscleGroup(it.muscle) }
                        .distinct()
                    val sets = VolumeCalculator.countEffectiveSets(exercise.sets)
                    direct.forEach { perMuscle[it] = (perMuscle[it] ?: 0) + sets }
                }
                perMuscle.forEach { (muscle, sets) ->
                    assertTrue(
                        "$splitId/${session.name}/$muscle: H7 roto ($sets)",
                        sets <= SessionCompositionPolicy.MAX_DIRECT_SETS_PER_MUSCLE,
                    )
                }
            }
            assertTrue(
                "$splitId: se esperaba déficit inicial bajo MEV, semanal=${before.entries.sortedByDescending { it.value }.take(4)}",
                lowBefore.isNotEmpty(),
            )
        }
        assertTrue("Sin casos de auditoría: $evidence", evidence.isNotEmpty())
    }

    @Test
    fun scale_single_session_respects_h7_quota() {
        val exerciseList = CatalogV2TestFixture.configurationLookup().values.toList()
        val calibration = calibrationFixture()
        val template = SESSION_TEMPLATES_SYSTEM.first { it.id == "sys-v3-hf-empuje" }
        val session = template.session
        val result = TemplateVolumeScaler.scaleSingleSession(session, exerciseList, calibration, 2, SeqIds())
        result.sessions.single().allExercises().forEach { _ -> }
        val perMuscle = mutableMapOf<String, Int>()
        result.sessions.single().allExercises().forEach { exercise ->
            val info = exerciseList.firstOrNull {
                it.id.equals(exercise.catalogConfigurationId, ignoreCase = true)
            } ?: return@forEach
            val direct = info.involvedMuscles
                .filter { it.role == com.example.kpkn.data.models.MuscleRole.PRIMARY }
                .map { VolumeCalculator.normalizeCanonicalMuscleGroup(it.muscle) }
                .distinct()
            val sets = VolumeCalculator.countEffectiveSets(exercise.sets)
            direct.forEach { perMuscle[it] = (perMuscle[it] ?: 0) + sets }
        }
        perMuscle.forEach { (muscle, sets) ->
            assertTrue(
                "Sesión suelta/$muscle: H7 roto ($sets)",
                sets <= SessionCompositionPolicy.MAX_DIRECT_SETS_PER_MUSCLE,
            )
        }
    }

    @Test
    fun scale_is_idempotent_without_calibration_residual() {
        val exerciseList = CatalogV2TestFixture.configurationLookup().values.toList()
        val calibration = calibrationFixture()
        val template = SESSION_TEMPLATES_SYSTEM.first { it.id == "sys-v3-hf-full" }
        val first = TemplateVolumeScaler.scaleWeekSessions(
            listOf(template.session),
            exerciseList,
            calibration,
            SeqIds(),
        )
        val second = TemplateVolumeScaler.scaleWeekSessions(first.sessions, exerciseList, calibration, SeqIds())
        assertTrue(
            "Segunda pasada no debe añadir más: ${second.addedSetsByMuscle}",
            second.addedSetsByMuscle.values.sum() == 0,
        )
    }


    // Roles aprobados del lote 6, declarados dentro del fixture: estos tests
    // no presuponen que los assets compartidos ya incorporen la curación.
    // Glúteo mayor PRIMARY + medio STABILIZER conservan el máximo canónico 1.0.
    private fun approvedLote6Fixture(configurationId: String): ExerciseMuscleInfo {
        val quad = InvolvedMuscle("Cuádriceps", MuscleRole.PRIMARY)
        val glute = InvolvedMuscle("Glúteo Mayor", MuscleRole.PRIMARY)
        val hams = InvolvedMuscle("Isquiosurales", MuscleRole.SECONDARY)
        val calves = InvolvedMuscle("Pantorrillas", MuscleRole.SECONDARY)
        val adductors = InvolvedMuscle("Aductores", MuscleRole.SECONDARY)
        val gluteMed = InvolvedMuscle("Glúteo Medio", MuscleRole.STABILIZER)
        val core = InvolvedMuscle("Core", MuscleRole.STABILIZER)
        val erectors = InvolvedMuscle("Erectores Espinales", MuscleRole.STABILIZER)
        val forearms = InvolvedMuscle("Antebrazo", MuscleRole.STABILIZER)
        val deltoids = InvolvedMuscle("Deltoides", MuscleRole.STABILIZER)
        val muscles = when (configurationId) {
            "quads_sentadilla_cosaca__default" ->
                listOf(quad, glute, adductors, calves, gluteMed, core, erectors)
            "quads_zancada_inversa_maquina_hack__default" ->
                listOf(quad, glute, hams, calves, gluteMed, adductors)
            "glutes_step_up_gluteo__default" ->
                listOf(glute, quad, hams, calves, gluteMed, erectors, core, forearms, deltoids, adductors)
            "glutes_zancada_cruzada__default" ->
                listOf(glute, quad, hams, calves, gluteMed, core, erectors, forearms, adductors, deltoids)
            else -> error("No approved lote 6 fixture for $configurationId")
        }
        return ExerciseMuscleInfo(id = configurationId, name = configurationId, involvedMuscles = muscles)
    }

    private fun scaleFixtureExercise(
        id: String,
        sets: Int,
        muscles: List<InvolvedMuscle>,
        configurationId: String? = null,
    ): Exercise = Exercise(
        id = id,
        name = id,
        catalogConfigurationId = configurationId,
        effectiveMuscles = muscles,
        sets = List(sets) { ExerciseSet(id = "$id-set-$it", targetReps = 10) },
        restTime = 60,
    )

    private fun fixtureWeeklyVolume(
        sessions: List<Session>,
        exercises: List<ExerciseMuscleInfo>,
    ): Map<String, Double> = VolumeCalculator
        .calculateCanonicalWeeklyMuscleVolumeForSessions(sessions, exercises)
        .associate { it.muscleName to it.weeklySets }

    private fun assertOtherPrimaryCeilingBlocksAddition(
        configurationIds: List<String>,
        targetMuscle: String,
        otherPrimary: String,
    ) {
        configurationIds.forEach { configurationId ->
            // 4 SECONDARY sets: total = 3 at MRV; 6: total = 4 above MRV.
            // Direct subtotal stays 1, so a direct-only ceiling check misses both.
            listOf(4, 6).forEach { indirectSets ->
                val info = approvedLote6Fixture(configurationId)
                val candidate = scaleFixtureExercise("candidate", 1, info.involvedMuscles, configurationId)
                val sessions = listOf(
                    Session(id = "candidate-session", name = "Candidate", exercises = listOf(candidate)),
                    Session(
                        id = "indirect-background",
                        name = "Independent indirect background",
                        exercises = listOf(scaleFixtureExercise(
                            "background", indirectSets, listOf(InvolvedMuscle(otherPrimary, MuscleRole.SECONDARY)),
                        )),
                    ),
                )
                val exercises = listOf(info)
                val recommendations = listOf(
                    VolumeRecommendation(targetMuscle, 2, 2, 4),
                    VolumeRecommendation(otherPrimary, 0, 3, 3),
                )
                val before = fixtureWeeklyVolume(sessions, exercises)
                val directBefore = TemplateVolumeScaler.measureDirectWeeklyForTest(sessions, exercises)
                val otherTarget = TemplateVolumeScaler.targetsFor(recommendations).getValue(otherPrimary)
                assertEquals(3, otherTarget.ceiling)
                assertEquals(1.0, before.getValue(targetMuscle), 0.001)
                assertEquals(1.0, directBefore.getValue(otherPrimary), 0.001)
                assertTrue(before.getValue(otherPrimary) >= otherTarget.ceiling)
                // Adding one would keep direct <= MRV, but take total > MRV.
                assertTrue(directBefore.getValue(otherPrimary) + 1.0 <= otherTarget.ceiling)
                assertTrue(before.getValue(otherPrimary) + 1.0 > otherTarget.ceiling)

                val result = TemplateVolumeScaler.scaleWeekSessions(sessions, exercises, recommendations, SeqIds())
                val label = "$configurationId/$targetMuscle->$otherPrimary/background=$indirectSets"
                assertEquals("$label: PRIMARY collateral must block the clone", sessions, result.sessions)
                assertEquals("$label: no direct target set added", 0, result.addedSetsByMuscle[targetMuscle] ?: 0)
                assertEquals("$label: report unmet target", 1.0, result.residualDeficitByMuscle.getValue(targetMuscle), 0.001)
                assertTrue("$label: report capped candidate", "candidate-session" in result.cappedSessions)
                assertEquals("$label: base excess is preserved", before, fixtureWeeklyVolume(result.sessions, exercises))
            }
        }
    }

    @Test
    fun lote6_cosaca_and_hack_cannot_use_collateral_tolerance_for_primary_glutes() {
        assertOtherPrimaryCeilingBlocksAddition(
            configurationIds = listOf(
                "quads_sentadilla_cosaca__default",
                "quads_zancada_inversa_maquina_hack__default",
            ),
            targetMuscle = "Cuádriceps",
            otherPrimary = "Glúteos",
        )
    }

    @Test
    fun lote6_glute_step_up_and_curtsy_cannot_use_collateral_tolerance_for_primary_quads() {
        assertOtherPrimaryCeilingBlocksAddition(
            configurationIds = listOf("glutes_step_up_gluteo__default", "glutes_zancada_cruzada__default"),
            targetMuscle = "Glúteos",
            otherPrimary = "Cuádriceps",
        )
    }

    @Test
    fun lote6_multi_primary_adds_to_the_exact_other_primary_ceiling_then_reports_residual() {
        val info = approvedLote6Fixture("quads_sentadilla_cosaca__default")
        val candidate = scaleFixtureExercise("candidate", 2, info.involvedMuscles, info.id)
        val sessions = listOf(Session(id = "candidate-session", name = "Candidate", exercises = listOf(candidate)))
        val exercises = listOf(info)
        val recommendations = listOf(
            VolumeRecommendation("Cuádriceps", 4, 4, 4),
            VolumeRecommendation("Glúteos", 0, 2, 3),
        )
        val result = TemplateVolumeScaler.scaleWeekSessions(sessions, exercises, recommendations, SeqIds())
        val after = fixtureWeeklyVolume(result.sessions, exercises)
        assertEquals("One clone fits exactly in glute MRV", 3, result.sessions.single().exercises.single().sets.size)
        assertEquals(3.0, after.getValue("Glúteos"), 0.001)
        assertEquals(3.0, after.getValue("Cuádriceps"), 0.001)
        assertEquals(1, result.addedSetsByMuscle.getValue("Cuádriceps"))
        assertEquals(1.0, result.residualDeficitByMuscle.getValue("Cuádriceps"), 0.001)
        assertTrue("candidate-session" in result.cappedSessions)
        val second = TemplateVolumeScaler.scaleWeekSessions(result.sessions, exercises, recommendations, SeqIds())
        assertEquals("At the reached ceiling a new pass cannot add PRIMARY", result.sessions, second.sessions)
        assertTrue(second.addedSetsByMuscle.isEmpty())
    }

    @Test
    fun secondary_and_stabilizer_collateral_can_use_only_the_bounded_indirect_tolerance() {
        listOf(
            Triple(MuscleRole.SECONDARY, "Isquiosurales", 4.0),
            Triple(MuscleRole.STABILIZER, "Core", 3.8),
        ).forEach { (role, collateralMuscle, expectedTotal) ->
            // Synthetic accounting fixtures: no new catalog identity or anatomy.
            val candidate = scaleFixtureExercise(
                "candidate", 1,
                listOf(InvolvedMuscle("Cuádriceps", MuscleRole.PRIMARY), InvolvedMuscle(collateralMuscle, role)),
            )
            val sessions = listOf(
                Session(id = "candidate-session", name = "Candidate", exercises = listOf(candidate)),
                Session(id = "background-session", name = "Background", exercises = listOf(
                    scaleFixtureExercise("background", 3, listOf(InvolvedMuscle(collateralMuscle, MuscleRole.PRIMARY))),
                )),
            )
            val recommendations = listOf(
                VolumeRecommendation("Cuádriceps", 3, 3, 4),
                VolumeRecommendation(collateralMuscle, 0, 2, 4),
            )
            val before = fixtureWeeklyVolume(sessions, emptyList())
            val result = TemplateVolumeScaler.scaleWeekSessions(sessions, emptyList(), recommendations, SeqIds())
            val after = fixtureWeeklyVolume(result.sessions, emptyList())
            assertTrue("$role: baseline is above MAV", before.getValue(collateralMuscle) > 2.0)
            assertEquals("$role: permit one indirect collateral clone", 2, result.sessions.first().exercises.single().sets.size)
            assertEquals("$role: account the indirect addition", expectedTotal, after.getValue(collateralMuscle), 0.001)
            assertTrue("$role: stay within explicit tolerance", after.getValue(collateralMuscle) <= 2.0 + TemplateVolumeScaler.INDIRECT_MRV_TOLERANCE + 0.001)
            assertEquals("$role: the next clone exceeds bounded tolerance", 1.0, result.residualDeficitByMuscle.getValue("Cuádriceps"), 0.001)
            assertTrue("candidate-session" in result.cappedSessions)
            assertEquals("$role: background remains unchanged", sessions.last(), result.sessions.last())
        }
    }

    @Test
    fun primary_role_with_fractional_override_still_cannot_use_indirect_tolerance() {
        val approved = approvedLote6Fixture("quads_sentadilla_cosaca__default")
        // Deliberate persisted override in this test, not an anatomy downgrade:
        // PRIMARY 0.5 must be protected by role even though directMap filters <1.
        val overrideMuscles = approved.involvedMuscles.map {
            if (it.muscle == "Glúteo Mayor") it.copy(volumeContribution = 0.5) else it
        }
        val staleInfo = approved.copy(involvedMuscles = approved.involvedMuscles.map {
            if (it.muscle == "Glúteo Mayor") it.copy(role = MuscleRole.SECONDARY) else it
        })
        val candidate = scaleFixtureExercise("candidate", 1, overrideMuscles, approved.id)
        val sessions = listOf(
            Session(id = "candidate-session", name = "Candidate", exercises = listOf(candidate)),
            Session(id = "background-session", name = "Background", exercises = listOf(
                scaleFixtureExercise("background", 5, listOf(InvolvedMuscle("Glúteos", MuscleRole.SECONDARY))),
            )),
        )
        val recommendations = listOf(
            VolumeRecommendation("Cuádriceps", 2, 2, 4),
            VolumeRecommendation("Glúteos", 0, 3, 3),
        )
        val before = fixtureWeeklyVolume(sessions, listOf(staleInfo))
        assertEquals("Effective override yields total at MRV", 3.0, before.getValue("Glúteos"), 0.001)
        assertEquals("Fractional PRIMARY is absent from current direct-only helper", 0.0,
            TemplateVolumeScaler.measureDirectWeeklyForTest(sessions, listOf(staleInfo))["Glúteos"] ?: 0.0, 0.001)
        val result = TemplateVolumeScaler.scaleWeekSessions(sessions, listOf(staleInfo), recommendations, SeqIds())
        assertEquals("Effective PRIMARY role wins over stale index and contribution threshold", sessions, result.sessions)
        assertEquals(1.0, result.residualDeficitByMuscle.getValue("Cuádriceps"), 0.001)
        assertTrue("candidate-session" in result.cappedSessions)
    }
}
