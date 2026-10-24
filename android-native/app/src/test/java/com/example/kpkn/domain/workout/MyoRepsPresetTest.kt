package com.example.kpkn.domain.workout

import com.example.kpkn.data.models.*
import com.example.kpkn.domain.auge.AugeFatigueEngine
import com.example.kpkn.domain.training.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class MyoRepsPresetTest {
    private val json=Json { encodeDefaults=true;ignoreUnknownKeys=true }
    @Test fun presetAplicaCuatroPorCuatroCon15SegundosYPermiteEditar() {
        val source=ExerciseSet("activation",targetReps=12,targetRIR=1,weight=60.0)
        val myo=MyoRepsPreset.applyTo(source)
        val plan=myo.scheduledTechniquePlan()!!
        assertEquals(ScheduledTechniqueKind.REST_PAUSE,plan.kind);assertEquals(4,plan.followUpCount);assertEquals(4,plan.followUpReps);assertEquals(15,plan.pauseSeconds)
        assertEquals(12,myo.targetReps);assertEquals(60.0,myo.weight!!,0.0)
        val edited=MyoRepsPreset.applyTo(myo,2,5,30).scheduledTechniquePlan()!!
        assertEquals(2,edited.followUpCount);assertEquals(5,edited.followUpReps);assertEquals(30,edited.pauseSeconds)
        assertEquals("myo",edited.style)
    }
    @Test fun jsonNormalPermaneceIdenticoYMyoSobreviveGuardarReabrir() {
        val old="{\"restTime\":15,\"reps\":4}"
        assertEquals(old,json.encodeToString(json.decodeFromString<RestPauseData>(old)))
        val myo=MyoRepsPreset.applyTo(ExerciseSet("s",targetReps=12))
        assertTrue(json.decodeFromString<ExerciseSet>(json.encodeToString(myo)).isMyoReps())
        val recorded=RestPauseData(15,4,"myo")
        assertEquals(recorded,json.decodeFromString<RestPauseData>(json.encodeToString(recorded)))
    }
    @Test fun mismaSerieYMiniSeriesProducenMismoVolumenYFatigaQueRestPause() {
        val ordinary=CompletedSet("set",60.0,12,rpe=9.0,restPauses=List(4) { RestPauseData(15,4) })
        val myo=ordinary.copy(restPauses=ordinary.restPauses.map { it.copy(style="myo") })
        val metrics=AugeMetrics();val tanks=BatteryTanks(100.0,100.0,100.0)
        assertEquals(AugeFatigueEngine.calculateSetBatteryDrain(ordinary,metrics,tanks),AugeFatigueEngine.calculateSetBatteryDrain(myo,metrics,tanks))
        val info=ExerciseMuscleInfo("press","Press",involvedMuscles=listOf(InvolvedMuscle("Pectorales",MuscleRole.PRIMARY,1.0)))
        val window=TrainingWindow(LocalDate.of(2026,10,24),LocalDate.of(2026,10,24),ZoneId.of("UTC"),TrainingWindowType.TOTAL,1.0)
        fun volume(set: CompletedSet)=VolumeCalculator.calculateCompletedVolume(listOf(WorkoutLog("log","p","session","Sesión","2026-10-24",30,
            listOf(CompletedExercise("exercise","Press",exerciseDbId="press",sets=listOf(set))))),mapOf("press" to info),window)
        assertTrue(volume(ordinary).muscles.isNotEmpty());assertEquals(volume(ordinary),volume(myo))
    }
}
