package com.example.kpkn.screens.sessioneditor

import com.example.kpkn.domain.auge.SessionDrainCharacterizationFixtures
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.util.TimeZone

/** Caracterización del editor re-anclada por 2.2b2; véanse las tablas antes→después del informe. */
@RunWith(Parameterized::class)
class SessionEditorAugeCharacterizationTest(private val caseId: String) {
    private lateinit var previousZone: TimeZone

    @Before fun fijarZona() {
        previousZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone(SessionDrainCharacterizationFixtures.zone))
    }

    @After fun restaurarZona() { TimeZone.setDefault(previousZone) }

    @Test fun conservaProyeccionesEInsights() {
        val f = SessionDrainCharacterizationFixtures
        val planned = f.planned.getValue(caseId).let { session ->
            if (caseId == "cardio") session.copy(exercises = session.exercises.filter { it.cardioDetails != null })
            else session
        }
        val result = computeSessionAugeComputation(
            session = planned, exerciseIndex = f.db, settings = f.settings,
            programLogs = emptyList(), programId = "p", mesoIndex = 0,
        )
        val actual = buildString {
            appendLine("drain=${result.drain}")
            appendLine("muscular=${result.muscleDrainProjection.toSortedMap()}")
            appendLine("energy=${result.muscleEnergyDrain.toSortedMap()}")
            appendLine("spinal=${result.muscleSpinalDrain.toSortedMap()}")
            appendLine("insights=${result.exerciseInsights}")
            appendLine("sets=${result.setCount};duration=${result.durationMinutes};difficulty=${result.difficulty};rpe=${f.number(result.averageRpe)};axial=${f.number(result.totalSpinalLoad)};elbow=${result.elbowStress};knee=${result.kneeStress}")
            appendLine("volume=${result.volumeMap.toSortedMap()}")
            appendLine("roles=${result.muscleRoleMap.toSortedMap()}")
            appendLine("recommendations=${result.muscleRecommendationContext.toSortedMap()}")
        }.trimEnd()
        val expected = expectedSnapshots.getValue(caseId)
        println("[2.2b1-editor][$caseId]\n$actual\n[/2.2b1-editor]")
        assertEquals(caseId, expected, actual)
    }

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}")
        fun cases(): List<Array<String>> = expectedSnapshots.keys.map { arrayOf(it) }

        private val expectedSnapshots = mapOf(
            "unilateral" to """
                drain=PredictedDrain(cns=61, muscular=40, spinal=9)
                muscular={Cuádriceps=27, Erectores Espinales=1, Glúteos=13}
                energy={Cuádriceps=39, Erectores Espinales=2, Glúteos=20}
                spinal={Cuádriceps=3, Erectores Espinales=1, Glúteos=2}
                insights=[SessionEditorAugeExerciseInsight(exerciseId=lunge-uni, name=Zancada, muscular=56, cns=95, spinal=1, total=51, suggestion=Carga neural elevada. Reducir RPE o %1RM deja más margen para el resto de la sesión.), SessionEditorAugeExerciseInsight(exerciseId=squat, name=Sentadilla, muscular=7, cns=61, spinal=5, total=24, suggestion=null)]
                sets=10;duration=33;difficulty=5;rpe=8.500000[4620974692658839552];axial=4.400000[4616639978017495450];elbow=0;knee=0
                volume={Cuádriceps=AugeVolumeAccumulator(flat=10.0, effective=10.0, fail=0.0), Erectores Espinales=AugeVolumeAccumulator(flat=0.8, effective=0.8, fail=0.0), Glúteos=AugeVolumeAccumulator(flat=5.0, effective=5.0, fail=0.0)}
                roles={Cuádriceps=MuscleRoleBreakdown(primary=10.0, secondary=0.0, stabilizer=0.0, neutralizer=0.0), Erectores Espinales=MuscleRoleBreakdown(primary=0.0, secondary=0.0, stabilizer=2.0, neutralizer=0.0), Glúteos=MuscleRoleBreakdown(primary=0.0, secondary=10.0, stabilizer=0.0, neutralizer=0.0)}
                recommendations={Cuádriceps=MuscleRecommendationContext(breakdown=MuscleRoleBreakdown(primary=0.0, secondary=0.0, stabilizer=0.0, neutralizer=0.0), usesPercent=false, usesRir=false, usesFailure=false), Erectores Espinales=MuscleRecommendationContext(breakdown=MuscleRoleBreakdown(primary=0.0, secondary=0.0, stabilizer=0.0, neutralizer=0.0), usesPercent=false, usesRir=false, usesFailure=false), Glúteos=MuscleRecommendationContext(breakdown=MuscleRoleBreakdown(primary=0.0, secondary=0.0, stabilizer=0.0, neutralizer=0.0), usesPercent=false, usesRir=false, usesFailure=false)}
            """.trimIndent(),
            "superserie" to """
                drain=PredictedDrain(cns=65, muscular=62, spinal=8)
                muscular={Bíceps=4, Deltoides=7, Dorsales=7, Erectores Espinales=3, Pectorales=20, Trapecio=4, Tríceps=17}
                energy={Bíceps=3, Deltoides=9, Dorsales=6, Erectores Espinales=2, Pectorales=26, Trapecio=3, Tríceps=17}
                spinal={Bíceps=1, Dorsales=1, Erectores Espinales=0, Trapecio=1}
                insights=[SessionEditorAugeExerciseInsight(exerciseId=bench, name=Press banca, muscular=52, cns=75, spinal=0, total=42, suggestion=null), SessionEditorAugeExerciseInsight(exerciseId=row, name=Remo, muscular=56, cns=68, spinal=3, total=42, suggestion=null), SessionEditorAugeExerciseInsight(exerciseId=incline, name=Press inclinado, muscular=49, cns=64, spinal=0, total=38, suggestion=null)]
                sets=15;duration=18;difficulty=5;rpe=8.500000[4620974692658839552];axial=3.000000[4613937818241073152];elbow=0;knee=0
                volume={Bíceps=AugeVolumeAccumulator(flat=2.5, effective=2.5, fail=0.0), Deltoides=AugeVolumeAccumulator(flat=1.75, effective=1.75, fail=0.0), Dorsales=AugeVolumeAccumulator(flat=5.0, effective=5.0, fail=0.0), Erectores Espinales=AugeVolumeAccumulator(flat=2.0, effective=2.0, fail=0.0), Pectorales=AugeVolumeAccumulator(flat=5.0, effective=5.0, fail=0.0), Trapecio=AugeVolumeAccumulator(flat=2.5, effective=2.5, fail=0.0), Tríceps=AugeVolumeAccumulator(flat=5.750000000000001, effective=5.750000000000001, fail=0.0)}
                roles={Bíceps=MuscleRoleBreakdown(primary=0.0, secondary=5.0, stabilizer=0.0, neutralizer=0.0), Deltoides=MuscleRoleBreakdown(primary=0.0, secondary=5.0, stabilizer=0.0, neutralizer=0.0), Dorsales=MuscleRoleBreakdown(primary=5.0, secondary=0.0, stabilizer=0.0, neutralizer=0.0), Erectores Espinales=MuscleRoleBreakdown(primary=0.0, secondary=0.0, stabilizer=5.0, neutralizer=0.0), Pectorales=MuscleRoleBreakdown(primary=5.0, secondary=0.0, stabilizer=0.0, neutralizer=0.0), Trapecio=MuscleRoleBreakdown(primary=0.0, secondary=5.0, stabilizer=0.0, neutralizer=0.0), Tríceps=MuscleRoleBreakdown(primary=5.0, secondary=5.0, stabilizer=0.0, neutralizer=0.0)}
                recommendations={Bíceps=MuscleRecommendationContext(breakdown=MuscleRoleBreakdown(primary=0.0, secondary=0.0, stabilizer=0.0, neutralizer=0.0), usesPercent=false, usesRir=false, usesFailure=false), Deltoides=MuscleRecommendationContext(breakdown=MuscleRoleBreakdown(primary=0.0, secondary=0.0, stabilizer=0.0, neutralizer=0.0), usesPercent=false, usesRir=false, usesFailure=false), Dorsales=MuscleRecommendationContext(breakdown=MuscleRoleBreakdown(primary=0.0, secondary=0.0, stabilizer=0.0, neutralizer=0.0), usesPercent=false, usesRir=false, usesFailure=false), Erectores Espinales=MuscleRecommendationContext(breakdown=MuscleRoleBreakdown(primary=0.0, secondary=0.0, stabilizer=0.0, neutralizer=0.0), usesPercent=false, usesRir=false, usesFailure=false), Pectorales=MuscleRecommendationContext(breakdown=MuscleRoleBreakdown(primary=0.0, secondary=0.0, stabilizer=0.0, neutralizer=0.0), usesPercent=false, usesRir=false, usesFailure=false), Trapecio=MuscleRecommendationContext(breakdown=MuscleRoleBreakdown(primary=0.0, secondary=0.0, stabilizer=0.0, neutralizer=0.0), usesPercent=false, usesRir=false, usesFailure=false), Tríceps=MuscleRecommendationContext(breakdown=MuscleRoleBreakdown(primary=0.0, secondary=0.0, stabilizer=0.0, neutralizer=0.0), usesPercent=false, usesRir=false, usesFailure=false)}
            """.trimIndent(),
            "cardio" to """
                drain=PredictedDrain(cns=4, muscular=5, spinal=1)
                muscular={Bíceps=1, Core=1, Cuádriceps=2, Dorsales=2}
                energy={Bíceps=1, Core=1, Cuádriceps=2, Dorsales=2}
                spinal={Bíceps=0, Core=0, Cuádriceps=0, Dorsales=0}
                insights=[SessionEditorAugeExerciseInsight(exerciseId=row-cardio, name=Remo cardio, muscular=5, cns=5, spinal=1, total=4, suggestion=null)]
                sets=1;duration=16;difficulty=4;rpe=8.000000[4620693217682128896];axial=0.698011[4604462346979622614];elbow=0;knee=0
                volume={}
                roles={}
                recommendations={}
            """.trimIndent(),
            "axial" to """
                drain=PredictedDrain(cns=52, muscular=39, spinal=9)
                muscular={Bíceps=3, Deltoides=7, Dorsales=5, Erectores Espinales=5, Glúteos=3, Isquiosurales=5, Trapecio=8, Tríceps=3}
                energy={Bíceps=1, Deltoides=9, Dorsales=2, Erectores Espinales=8, Glúteos=6, Isquiosurales=11, Trapecio=10, Tríceps=4}
                spinal={Bíceps=0, Deltoides=1, Dorsales=0, Erectores Espinales=1, Glúteos=1, Isquiosurales=1, Trapecio=1, Tríceps=0}
                insights=[SessionEditorAugeExerciseInsight(exerciseId=deadlift, name=Peso muerto, muscular=17, cns=33, spinal=4, total=18, suggestion=null), SessionEditorAugeExerciseInsight(exerciseId=ohp, name=Press militar, muscular=21, cns=36, spinal=1, total=19, suggestion=null), SessionEditorAugeExerciseInsight(exerciseId=row, name=Remo, muscular=22, cns=25, spinal=1, total=16, suggestion=null)]
                sets=12;duration=52;difficulty=9;rpe=9.375000[4621467273868083200];axial=8.800000[4621143577644865945];elbow=0;knee=0
                volume={Bíceps=AugeVolumeAccumulator(flat=1.5, effective=1.5, fail=0.0), Deltoides=AugeVolumeAccumulator(flat=4.0, effective=4.0, fail=4.0), Dorsales=AugeVolumeAccumulator(flat=3.0, effective=3.0, fail=0.0), Erectores Espinales=AugeVolumeAccumulator(flat=4.2, effective=4.2, fail=3.0), Glúteos=AugeVolumeAccumulator(flat=2.5, effective=2.5, fail=2.5), Isquiosurales=AugeVolumeAccumulator(flat=5.0, effective=5.0, fail=5.0), Trapecio=AugeVolumeAccumulator(flat=5.5, effective=5.5, fail=4.0), Tríceps=AugeVolumeAccumulator(flat=1.8, effective=1.8, fail=1.8)}
                roles={Bíceps=MuscleRoleBreakdown(primary=0.0, secondary=3.0, stabilizer=0.0, neutralizer=0.0), Deltoides=MuscleRoleBreakdown(primary=4.0, secondary=0.0, stabilizer=0.0, neutralizer=0.0), Dorsales=MuscleRoleBreakdown(primary=3.0, secondary=0.0, stabilizer=0.0, neutralizer=0.0), Erectores Espinales=MuscleRoleBreakdown(primary=0.0, secondary=5.0, stabilizer=3.0, neutralizer=0.0), Glúteos=MuscleRoleBreakdown(primary=0.0, secondary=5.0, stabilizer=0.0, neutralizer=0.0), Isquiosurales=MuscleRoleBreakdown(primary=5.0, secondary=0.0, stabilizer=0.0, neutralizer=0.0), Trapecio=MuscleRoleBreakdown(primary=0.0, secondary=7.0, stabilizer=5.0, neutralizer=0.0), Tríceps=MuscleRoleBreakdown(primary=0.0, secondary=4.0, stabilizer=0.0, neutralizer=0.0)}
                recommendations={Bíceps=MuscleRecommendationContext(breakdown=MuscleRoleBreakdown(primary=0.0, secondary=0.0, stabilizer=0.0, neutralizer=0.0), usesPercent=true, usesRir=false, usesFailure=false), Deltoides=MuscleRecommendationContext(breakdown=MuscleRoleBreakdown(primary=0.0, secondary=0.0, stabilizer=0.0, neutralizer=0.0), usesPercent=true, usesRir=false, usesFailure=false), Dorsales=MuscleRecommendationContext(breakdown=MuscleRoleBreakdown(primary=0.0, secondary=0.0, stabilizer=0.0, neutralizer=0.0), usesPercent=true, usesRir=false, usesFailure=false), Erectores Espinales=MuscleRecommendationContext(breakdown=MuscleRoleBreakdown(primary=0.0, secondary=0.0, stabilizer=0.0, neutralizer=0.0), usesPercent=true, usesRir=false, usesFailure=false), Glúteos=MuscleRecommendationContext(breakdown=MuscleRoleBreakdown(primary=0.0, secondary=0.0, stabilizer=0.0, neutralizer=0.0), usesPercent=true, usesRir=false, usesFailure=false), Isquiosurales=MuscleRecommendationContext(breakdown=MuscleRoleBreakdown(primary=0.0, secondary=0.0, stabilizer=0.0, neutralizer=0.0), usesPercent=true, usesRir=false, usesFailure=false), Trapecio=MuscleRecommendationContext(breakdown=MuscleRoleBreakdown(primary=0.0, secondary=0.0, stabilizer=0.0, neutralizer=0.0), usesPercent=true, usesRir=false, usesFailure=false), Tríceps=MuscleRecommendationContext(breakdown=MuscleRoleBreakdown(primary=0.0, secondary=0.0, stabilizer=0.0, neutralizer=0.0), usesPercent=true, usesRir=false, usesFailure=false)}
            """.trimIndent(),
            "sin-ficha" to """
                drain=PredictedDrain(cns=48, muscular=45, spinal=12)
                muscular={Deltoides=1, Pectorales=4, Tríceps=2}
                energy={Deltoides=1, Pectorales=3, Tríceps=1}
                spinal={}
                insights=[SessionEditorAugeExerciseInsight(exerciseId=custom, name=Movimiento propio, muscular=26, cns=32, spinal=2, total=20, suggestion=null), SessionEditorAugeExerciseInsight(exerciseId=hinge, name=Peso muerto custom, muscular=32, cns=37, spinal=6, total=25, suggestion=null), SessionEditorAugeExerciseInsight(exerciseId=bench, name=Press banca, muscular=16, cns=19, spinal=0, total=12, suggestion=null)]
                sets=9;duration=23;difficulty=5;rpe=8.500000[4620974692658839552];axial=0.000000[0];elbow=0;knee=0
                volume={Deltoides=AugeVolumeAccumulator(flat=0.7, effective=0.7, fail=0.0), Pectorales=AugeVolumeAccumulator(flat=2.0, effective=2.0, fail=0.0), Tríceps=AugeVolumeAccumulator(flat=0.9, effective=0.9, fail=0.0)}
                roles={Deltoides=MuscleRoleBreakdown(primary=0.0, secondary=2.0, stabilizer=0.0, neutralizer=0.0), Pectorales=MuscleRoleBreakdown(primary=2.0, secondary=0.0, stabilizer=0.0, neutralizer=0.0), Tríceps=MuscleRoleBreakdown(primary=0.0, secondary=2.0, stabilizer=0.0, neutralizer=0.0)}
                recommendations={Deltoides=MuscleRecommendationContext(breakdown=MuscleRoleBreakdown(primary=0.0, secondary=0.0, stabilizer=0.0, neutralizer=0.0), usesPercent=false, usesRir=false, usesFailure=false), Pectorales=MuscleRecommendationContext(breakdown=MuscleRoleBreakdown(primary=0.0, secondary=0.0, stabilizer=0.0, neutralizer=0.0), usesPercent=false, usesRir=false, usesFailure=false), Tríceps=MuscleRecommendationContext(breakdown=MuscleRoleBreakdown(primary=0.0, secondary=0.0, stabilizer=0.0, neutralizer=0.0), usesPercent=false, usesRir=false, usesFailure=false)}
            """.trimIndent(),
            "filtrado-rir" to """
                drain=PredictedDrain(cns=33, muscular=20, spinal=0)
                muscular={Deltoides=4, Pectorales=11, Tríceps=5}
                energy={Deltoides=7, Pectorales=19, Tríceps=8}
                spinal={}
                insights=[SessionEditorAugeExerciseInsight(exerciseId=bench, name=Press banca, muscular=26, cns=47, spinal=0, total=24, suggestion=null)]
                sets=4;duration=19;difficulty=4;rpe=7.225000[4619820645254325862];axial=0.000000[0];elbow=0;knee=0
                volume={Deltoides=AugeVolumeAccumulator(flat=1.4, effective=1.12, fail=0.0), Pectorales=AugeVolumeAccumulator(flat=4.0, effective=3.2, fail=0.0), Tríceps=AugeVolumeAccumulator(flat=1.8, effective=1.44, fail=0.0)}
                roles={Deltoides=MuscleRoleBreakdown(primary=0.0, secondary=4.0, stabilizer=0.0, neutralizer=0.0), Pectorales=MuscleRoleBreakdown(primary=4.0, secondary=0.0, stabilizer=0.0, neutralizer=0.0), Tríceps=MuscleRoleBreakdown(primary=0.0, secondary=4.0, stabilizer=0.0, neutralizer=0.0)}
                recommendations={Deltoides=MuscleRecommendationContext(breakdown=MuscleRoleBreakdown(primary=0.0, secondary=0.0, stabilizer=0.0, neutralizer=0.0), usesPercent=false, usesRir=true, usesFailure=false), Pectorales=MuscleRecommendationContext(breakdown=MuscleRoleBreakdown(primary=0.0, secondary=0.0, stabilizer=0.0, neutralizer=0.0), usesPercent=false, usesRir=true, usesFailure=false), Tríceps=MuscleRecommendationContext(breakdown=MuscleRoleBreakdown(primary=0.0, secondary=0.0, stabilizer=0.0, neutralizer=0.0), usesPercent=false, usesRir=true, usesFailure=false)}
            """.trimIndent(),
        )
    }
}
