package com.example.kpkn.domain.training

import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.TrainingStyle
import com.example.kpkn.data.models.VolumeRecommendation
import com.example.kpkn.data.sessions.SESSION_TEMPLATES_SYSTEM
import com.example.kpkn.data.splits.SPLIT_TEMPLATES
import com.example.kpkn.domain.templates.CatalogV2TestFixture
import com.example.kpkn.screens.programdetail.components.buildVolumeCalibration
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
                // MRV sobre el directo que AÑADE el scaler (la base del catálogo
                // nunca se recorta). La proyección total del scaler respeta el
                // techo con tolerancia WEEKLY_COLLATERAL_TOLERANCE para trabajo
                // colateral indirecto (p.ej. glúteos de sentadilla en ppl_x6).
                val directAfter = TemplateVolumeScaler.measureDirectWeeklyForTest(result.sessions, exerciseList)
                val addedDirect = (directAfter[muscle] ?: 0.0) - (directBefore[muscle] ?: 0.0)
                assertTrue(
                    "$splitId/$muscle: el scaler añadió directo sobre el techo ($addedDirect con techo ${st.ceiling} y base directa ${directBefore[muscle] ?: 0.0})",
                    (directBefore[muscle] ?: 0.0) + addedDirect <=
                        st.ceiling + TemplateVolumeScaler.WEEKLY_COLLATERAL_TOLERANCE + 0.001,
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
}
