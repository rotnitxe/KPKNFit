package com.example.kpkn.data.sessions

import com.example.kpkn.data.models.CardioDetails
import com.example.kpkn.data.models.CardioType
import com.example.kpkn.data.models.Exercise
import com.example.kpkn.data.models.ExerciseMuscleInfo
import com.example.kpkn.data.models.ExerciseSet
import com.example.kpkn.data.models.InvolvedMuscle
import com.example.kpkn.data.models.MobilityConfig
import com.example.kpkn.data.models.MobilityMode
import com.example.kpkn.data.models.MobilitySeries
import com.example.kpkn.data.models.MuscleRole
import com.example.kpkn.data.models.Session
import com.example.kpkn.data.models.SessionPart
import com.example.kpkn.data.models.WarmupExercise
import com.example.kpkn.domain.templates.SessionTemplateAudit
import com.example.kpkn.domain.templates.SessionTemplateQualityRules
import com.example.kpkn.domain.templates.TemplateQualitySeverity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionTemplateMultimodalQualityTest {

    private val strengthInfo = ExerciseMuscleInfo(
        id = "barbell_compound",
        name = "Compuesto",
        type = "Básico",
        articulationType = "MULTIARTICULAR",
        technicalDifficulty = 3.0,
        involvedMuscles = listOf(InvolvedMuscle("Pectorales", MuscleRole.PRIMARY)),
    )

    private val index = mapOf(strengthInfo.id to strengthInfo)

    private fun exercise(
        id: String = "e1",
        restSeconds: Int? = null,
        reps: Int = 5,
        rpe: Double = 8.0,
    ) = Exercise(
        id = id,
        name = "Compuesto",
        exerciseDbId = strengthInfo.id,
        exerciseId = strengthInfo.id,
        sets = listOf(
            ExerciseSet("$id-set-1", targetReps = reps, targetRPE = rpe),
            ExerciseSet("$id-set-2", targetReps = reps, targetRPE = rpe),
        ),
        restTime = restSeconds,
        targetSessionGoal = if (reps <= 6) "Fuerza" else "Hipertrofia",
    )

    private fun template(session: Session) = SessionTemplate(
        id = "quality-${session.id}",
        sourceType = SessionTemplateSourceType.USER,
        name = session.name,
        description = "fixture",
        session = session,
    )

    @Test
    fun heavyCompoundRest_requires180_butDoesNotFlagHypertrophy() {
        val short = SessionTemplateQualityRules.audit(
            template(Session("short", "Fuerza", exercises = listOf(exercise(restSeconds = 150)))),
            index,
        )
        assertTrue(short.p1.any { it.code == "HEAVY_COMPOUND_REST_SHORT" })

        val compliant = SessionTemplateQualityRules.audit(
            template(Session("ok", "Fuerza", exercises = listOf(exercise(restSeconds = 180)))),
            index,
        )
        assertFalse(compliant.p1.any { it.code == "HEAVY_COMPOUND_REST_SHORT" })

        val hypertrophy = SessionTemplateQualityRules.audit(
            template(Session("hypertrophy", "Hipertrofia", exercises = listOf(exercise(restSeconds = 90, reps = 10)))),
            index,
        )
        assertFalse(hypertrophy.p1.any { it.code == "HEAVY_COMPOUND_REST_SHORT" })
    }

    @Test
    fun auditCountsWarmupOnlySessionDuration() {
        val result = SessionTemplateAudit.audit(
            Session(
                id = "warmup",
                name = "Calentamiento",
                warmup = listOf(WarmupExercise("w", "Bicicleta", duration = 300, sets = 1)),
            ),
            emptyMap(),
        )
        assertEquals(5, result.estimatedDurationMinutes)
    }

    @Test
    fun auditTreatsExplicitWarmupDurationAsTotal_notPerSet() {
        val result = SessionTemplateAudit.audit(
            Session(
                id = "warmup-total",
                name = "Calentamiento con rondas",
                warmup = listOf(WarmupExercise("w", "Bicicleta", duration = 300, sets = 3)),
            ),
            emptyMap(),
        )
        // 300 s is the item's total duration; sets must not multiply it to 15 min.
        assertEquals(5, result.estimatedDurationMinutes)
    }

    @Test
    fun auditCountsMobilityPartConfigWithoutDoubleCounting() {
        val result = SessionTemplateAudit.audit(
            Session(
                id = "mobility",
                name = "Movilidad",
                parts = listOf(
                    SessionPart(
                        id = "mobility-part",
                        name = "Movilidad",
                        isMobilityGroup = true,
                        mobilityConfig = MobilityConfig(MobilityMode.ENFOCADO, totalMinutes = 8),
                    ),
                ),
            ),
            emptyMap(),
        )
        assertEquals(8, result.estimatedDurationMinutes)
    }

    @Test
    fun auditCountsCardioPartTargetAndAllExercises() {
        val cardio = Exercise(
            id = "cardio",
            name = "Cinta",
            cardioDetails = CardioDetails(CardioType.TREADMILL, targetDurationSeconds = 600),
        )
        val result = SessionTemplateAudit.audit(
            Session(
                id = "cardio-session",
                name = "Cardio",
                parts = listOf(
                    SessionPart(
                        id = "cardio-part",
                        name = "Cardio",
                        isCardioGroup = true,
                        targetDurationMinutes = 12,
                        exercises = listOf(cardio),
                    ),
                ),
            ),
            emptyMap(),
        )
        // The part target is a floor; it must not be added on top of the
        // cardio exercise's own 10-minute duration.
        assertEquals(12, result.estimatedDurationMinutes)

        val report = SessionTemplateQualityRules.audit(template(
            Session(
                id = "cardio-quality",
                name = "Cardio",
                parts = listOf(
                    SessionPart(
                        id = "cardio-part",
                        name = "Cardio",
                        isCardioGroup = true,
                        targetDurationMinutes = 12,
                        exercises = listOf(cardio),
                    ),
                ),
            ),
        ), emptyMap())
        assertFalse(report.issues.any { it.severity == TemplateQualitySeverity.P0 })
    }

    @Test
    fun strengthExerciseWithoutSets_isP0_and_modalitiesRemainExecutableWithoutStrengthSets() {
        val missing = SessionTemplateQualityRules.audit(
            template(
                Session(
                    id = "missing-strength",
                    name = "Fuerza incompleta",
                    exercises = listOf(
                        Exercise(
                            id = "strength-no-sets",
                            name = "Press banca sin receta",
                            exerciseDbId = strengthInfo.id,
                        ),
                    ),
                ),
            ),
            index,
        )
        assertTrue(missing.p0.any { it.code == "STRENGTH_PRESCRIPTION_MISSING" })

        val cardio = SessionTemplateQualityRules.audit(
            template(
                Session(
                    id = "cardio-part-no-exercise-sets",
                    name = "Cardio",
                    parts = listOf(
                        SessionPart(
                            id = "cardio-part",
                            name = "Cardio",
                            isCardioGroup = true,
                            targetDurationMinutes = 10,
                            exercises = listOf(
                                Exercise(
                                    id = "cardio-placeholder",
                                    name = "Cinta",
                                    cardioDetails = CardioDetails(
                                        CardioType.TREADMILL,
                                        targetDurationSeconds = 600,
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
            emptyMap(),
        )
        assertFalse(cardio.p0.any { it.code == "STRENGTH_PRESCRIPTION_MISSING" })
    }

    @Test
    fun visible_modality_placeholders_fail_closed_with_central_execution_gate() {
        val targetOnlyCardio = SessionTemplateQualityRules.audit(
            template(
                Session(
                    id = "cardio-target-only",
                    name = "Cardio sin receta",
                    parts = listOf(
                        SessionPart(
                            id = "cardio-part",
                            name = "Cardio",
                            isCardioGroup = true,
                            targetDurationMinutes = 20,
                        ),
                    ),
                ),
            ),
            emptyMap(),
        )
        assertTrue(targetOnlyCardio.p0.any { it.code == "SESSION_EXECUTION_MISSING" })

        val targetOnlyMobility = SessionTemplateQualityRules.audit(
            template(
                Session(
                    id = "mobility-target-only",
                    name = "Movilidad sin receta",
                    parts = listOf(
                        SessionPart(
                            id = "mobility-part",
                            name = "Movilidad",
                            isMobilityGroup = true,
                            targetDurationMinutes = 20,
                        ),
                    ),
                ),
            ),
            emptyMap(),
        )
        assertTrue(targetOnlyMobility.p0.any { it.code == "SESSION_EXECUTION_MISSING" })

        val mobilityWithoutUnit = SessionTemplateQualityRules.audit(
            template(
                Session(
                    id = "mobility-series-placeholder",
                    name = "Movilidad incompleta",
                    parts = listOf(
                        SessionPart(
                            id = "mobility-part",
                            name = "Movilidad",
                            isMobilityGroup = true,
                            mobilitySeries = listOf(
                                MobilitySeries("series", name = "Cadera", sets = 2),
                            ),
                        ),
                    ),
                ),
            ),
            emptyMap(),
        )
        assertTrue(mobilityWithoutUnit.p0.any { it.code == "SESSION_EXECUTION_MISSING" })

        val emptyWarmup = SessionTemplateQualityRules.audit(
            template(
                Session(
                    id = "warmup-placeholder",
                    name = "Calentamiento incompleto",
                    warmup = listOf(WarmupExercise("warmup", "Bici")),
                ),
            ),
            emptyMap(),
        )
        assertTrue(emptyWarmup.p0.any { it.code == "SESSION_EXECUTION_MISSING" })
    }

    @Test
    fun invalid_mobility_and_warmup_are_p0_even_when_strength_is_valid() {
        val mixedMobility = SessionTemplateQualityRules.audit(
            template(
                Session(
                    id = "mixed-mobility",
                    name = "Fuerza con movilidad incompleta",
                    exercises = listOf(exercise()),
                    parts = listOf(
                        SessionPart(
                            id = "mobility-part",
                            name = "Movilidad",
                            isMobilityGroup = true,
                            mobilitySeries = listOf(
                                MobilitySeries("missing-unit", name = "Cadera", sets = 2),
                            ),
                        ),
                    ),
                ),
            ),
            index,
        )
        assertTrue(mixedMobility.p0.any { it.code == "MOBILITY_DURATION_INVALID" })

        val mixedWarmup = SessionTemplateQualityRules.audit(
            template(
                Session(
                    id = "mixed-warmup",
                    name = "Fuerza con calentamiento incompleto",
                    exercises = listOf(exercise()),
                    warmup = listOf(WarmupExercise("empty-warmup", "Bici")),
                ),
            ),
            index,
        )
        assertTrue(mixedWarmup.p0.any { it.code == "WARMUP_DURATION_INVALID" })
    }

    @Test
    fun mixed_strength_cards_fail_per_exercise_not_only_when_the_whole_session_is_empty() {
        val report = SessionTemplateQualityRules.audit(
            template(
                Session(
                    id = "mixed-strength",
                    name = "Fuerza mixta",
                    exercises = listOf(
                        exercise(id = "valid-strength"),
                        Exercise(
                            id = "mode-only-strength",
                            name = "Press sin objetivo",
                            sets = listOf(
                                ExerciseSet(
                                    id = "mode-only-set",
                                    intensityMode = com.example.kpkn.data.models.IntensityMode.RPE,
                                ),
                            ),
                        ),
                    ),
                ),
            ),
            index,
        )

        assertTrue(report.p0.any { it.code == "STRENGTH_PRESCRIPTION_MISSING" })
    }

    @Test
    fun target_only_modal_parts_fail_even_when_strength_makes_session_executable() {
        val cardioReport = SessionTemplateQualityRules.audit(
            template(
                Session(
                    id = "mixed-target-cardio",
                    name = "Fuerza + cardio sin receta",
                    exercises = listOf(exercise()),
                    parts = listOf(
                        SessionPart(
                            id = "cardio-part",
                            name = "Cardio",
                            isCardioGroup = true,
                            targetDurationMinutes = 20,
                        ),
                    ),
                ),
            ),
            index,
        )
        assertTrue(cardioReport.p0.any { it.code == "CARDIO_DURATION_INVALID" })

        val mobilityReport = SessionTemplateQualityRules.audit(
            template(
                Session(
                    id = "mixed-target-mobility",
                    name = "Fuerza + movilidad sin receta",
                    exercises = listOf(exercise()),
                    parts = listOf(
                        SessionPart(
                            id = "mobility-part",
                            name = "Movilidad",
                            isMobilityGroup = true,
                            targetDurationMinutes = 20,
                        ),
                    ),
                ),
            ),
            index,
        )
        assertTrue(mobilityReport.p0.any { it.code == "MOBILITY_DURATION_INVALID" })
    }
}
