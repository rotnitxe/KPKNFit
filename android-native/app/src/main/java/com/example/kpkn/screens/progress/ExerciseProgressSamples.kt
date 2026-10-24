package com.example.kpkn.screens.progress

import com.example.kpkn.data.models.*
import com.example.kpkn.domain.training.*
import java.time.LocalDate
import java.time.ZoneId

object ExerciseProgressSamples {
    fun logs(name: String): List<WorkoutLog> {
        if(name == "empty") return emptyList()
        val count = if(name == "sparse") 1 else 5
        return (0 until count).map { index ->
            val mode = when(name) { "bodyweight" -> LoadModeV2.BODYWEIGHT; "assisted" -> LoadModeV2.ASSISTED; "broken_lineage" -> if(index<2) LoadModeV2.LOAD else LoadModeV2.LASTRE; else -> LoadModeV2.LOAD }
            val unit = if(name == "timed") UnitModeV2.TIME else UnitModeV2.REPS
            val load = when(mode) { LoadModeV2.ASSISTED -> 45.0-index*5; LoadModeV2.BODYWEIGHT -> 0.0; LoadModeV2.LASTRE -> 10.0+index*2; else -> listOf(80.0,85.0,85.0,82.5,90.0)[index] }
            val set = CompletedSet("set-$index",load,5+index,rir=2,timeSeconds=if(unit == UnitModeV2.TIME) 45+index*10 else null,
                recordedPayloadV3=RecordedSetPayload(exerciseId="press",loadInputMode=mode,unitMode=unit,externalLoad=load,
                    assistedLoad=load,completedReps=5+index,durationSeconds=if(unit == UnitModeV2.TIME) 45+index*10 else null))
            WorkoutLog("log-$index","program","session","Sesión ${index+1}","2026-10-${10+index}",40,
                listOf(CompletedExercise("press",when(name) { "assisted" -> "Dominada asistida"; "bodyweight" -> "Dominada"; "timed" -> "Plancha"; else -> "Press de banca" },
                    catalogConfigurationId="sample",variantName="Configuración registrada",sets=listOf(set,set.copy(id="second-$index")))))
        }
    }
    fun state(name: String): ExerciseProgressUiState {
        val logs=logs(name)
        val metric=when(name) { "assisted" -> ExerciseProgressMetric.MAX_WEIGHT; "bodyweight","broken_lineage" -> ExerciseProgressMetric.MAX_REPS; "timed" -> ExerciseProgressMetric.DURATION; else -> ExerciseProgressMetric.E1RM }
        return ExerciseProgressUiState(false,ExerciseProgress.choices(logs),ExerciseProgress.build(logs,"configuration:sample",metric,ExerciseProgressWindow.ALL,LocalDate.of(2026,10,24),ZoneId.of("America/Santiago")),ExerciseProgressWindow.ALL)
    }
}
