package com.example.kpkn.domain.training

import com.example.kpkn.data.models.*
import com.example.kpkn.domain.performance.StrengthModel
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ExerciseProgressTest {
    private val today = LocalDate.of(2026,10,24)
    private fun set(weight: Double = 100.0, reps: Int = 5, mode: LoadModeV2 = LoadModeV2.LOAD) = CompletedSet("s",weight,reps,rir=2,
        recordedPayloadV3 = RecordedSetPayload(exerciseId="e",loadInputMode=mode,externalLoad=weight,assistedLoad=weight,completedReps=reps))
    private fun log(id: String, day: String, sets: List<CompletedSet>, config: String="bar") = WorkoutLog(id,"p","session", "Sesión $id",day,30,
        listOf(CompletedExercise("e","Press",catalogConfigurationId=config,sets=sets)))
    private fun report(logs: List<WorkoutLog>, metric: ExerciseProgressMetric=ExerciseProgressMetric.E1RM,
        window: ExerciseProgressWindow=ExerciseProgressWindow.ALL) = ExerciseProgress.build(logs,"configuration:bar",metric,window,today,ZoneId.of("America/Santiago"))
    @Test fun e1rmCoincideConModeloUnicoConYSinReserva() {
        listOf(set(),set().copy(rir=null)).forEach { set ->
            assertEquals(StrengthModel.estimate1RM(set),report(listOf(log("a","2026-10-01",listOf(set)))).points.single().value,0.0)
        }
    }
    @Test fun explicacionUsaComaDecimalYExponeLaReservaSinIdsInternos() {
        val text = report(listOf(log("a", "2026-10-01", listOf(set(82.5))))).points.single().explanation
        assertTrue(text, text.startsWith("82,5 kg × 5 @ RIR 2 → e1RM "))
        assertTrue(text, text.endsWith(" kg estimados"))
        assertFalse(text, text.contains("strength-v"))
        assertFalse(text, text.contains("82.5"))
    }
    @Test fun prNoDuplicaEmpatesYRecuperaMarcaTrasUnaBajada() {
        val logs = listOf(100.0,100.0,90.0,110.0).mapIndexed { i,w -> log("$i","2026-10-0${i+1}",listOf(set(w))) }
        val result = report(logs)
        assertEquals(listOf(true,false,false,true),result.points.map { it.isPr })
        assertEquals(result.points.last().value,result.records.first { it.metric==ExerciseProgressMetric.E1RM }.point.value,0.0)
    }
    @Test fun asistenciaMenorEsMejorYNuncaSeRotulaComoE1rm() {
        val logs = listOf(40.0,30.0).mapIndexed { i,w -> log("$i","2026-10-0${i+1}",listOf(set(w,mode=LoadModeV2.ASSISTED))) }
        val result = report(logs,ExerciseProgressMetric.MAX_WEIGHT)
        assertFalse(ExerciseProgressMetric.E1RM in result.available)
        assertTrue(result.points.last().isPr);assertFalse(result.points.last().higherIsBetter)
        assertEquals(30.0,result.records.first { it.metric==ExerciseProgressMetric.MAX_WEIGHT }.point.value,0.0)
    }
    @Test fun pesoCorporalLastreYCargaTienenTrayectoriasSeparadas() {
        val logs = listOf(LoadModeV2.LOAD,LoadModeV2.BODYWEIGHT,LoadModeV2.LASTRE).mapIndexed { i,m -> log("$i","2026-10-0${i+1}",listOf(set(if(m==LoadModeV2.BODYWEIGHT)0.0 else 10.0,mode=m))) }
        val result=report(logs,ExerciseProgressMetric.MAX_REPS)
        assertEquals(3,result.points.map { it.lineage }.distinct().size);assertEquals(2,result.points.count { it.breaksBefore })
        assertTrue(result.insufficient)
    }
    @Test fun calentamientosSaltadasFallidasYRepsInvalidasNoCuentan() {
        val valid=set();val result=report(listOf(log("a","2026-10-01",listOf(valid,valid.copy(isWarmup=true),valid.copy(skipped=true),valid.copy(isFailedSet=true),valid.copy(reps=0,recordedPayloadV3=null)))),ExerciseProgressMetric.SETS)
        assertEquals(1.0,result.points.single().value,0.0)
    }
    @Test fun configuracionesDelMismoMovimientoNoSeMezclan() {
        val logs=listOf(log("a","2026-10-01",listOf(set())),log("b","2026-10-02",listOf(set(200.0)),"dumbbells"))
        assertEquals(1,report(logs).points.size);assertEquals(2,ExerciseProgress.choices(logs).size)
    }
    @Test fun registrosDeTiempoYDistanciaSeConservanConSusUnidades() {
        val timed=set(0.0).copy(timeSeconds=60,recordedPayloadV3=RecordedSetPayload(exerciseId="e",loadInputMode=LoadModeV2.BODYWEIGHT,unitMode=UnitModeV2.TIME,durationSeconds=60))
        val distance=set(0.0).copy(distanceKm=2.5,recordedPayloadV3=RecordedSetPayload(exerciseId="e",loadInputMode=LoadModeV2.BODYWEIGHT,unitMode=UnitModeV2.DISTANCE))
        val logs=listOf(log("a","2026-10-01",listOf(timed)),log("b","2026-10-02",listOf(distance)))
        assertEquals(60.0,report(logs,ExerciseProgressMetric.DURATION).points.single().value,0.0)
        assertEquals(2500.0,report(logs,ExerciseProgressMetric.DISTANCE).points.single().value,0.0)
    }
    @Test fun medianocheUsaZonaExplicitaYNoCuentaElFuturo() {
        val logs=listOf(log("a","2026-10-24T01:00:00Z",listOf(set())),log("b","2026-10-26",listOf(set())))
        assertEquals(LocalDate.of(2026,10,23),report(logs).points.single().date)
    }
    @Test fun intervalosCalendarioYPrPrevioAlIntervaloSeRespetan() {
        val logs=listOf(log("a","2025-10-23",listOf(set(110.0))),log("b","2026-07-24",listOf(set())),log("c","2026-09-24",listOf(set())),log("d","2026-10-24",listOf(set())))
        assertEquals(2,report(logs,window=ExerciseProgressWindow.MONTH).points.size)
        assertEquals(3,report(logs,window=ExerciseProgressWindow.QUARTER).points.size)
        assertEquals(3,report(logs,window=ExerciseProgressWindow.YEAR).points.size)
        assertEquals(4,report(logs).points.size)
        assertFalse(report(logs,window=ExerciseProgressWindow.MONTH).points.last().isPr)
    }
    @Test fun mismaConfiguracionConOtroContextoCortaLaLinea() {
        val logs=listOf(log("a","2026-10-01",listOf(set().copy(machineBrand="A"))),log("b","2026-10-02",listOf(set().copy(machineBrand="B"))))
        assertTrue(report(logs).points.last().breaksBefore)
    }
    @Test fun mejorSerieAsistidaMuestraCargaRealYRespetaLasReps() {
        val logs=listOf(log("a","2026-10-01",listOf(set(40.0,5,LoadModeV2.ASSISTED))),
            log("b","2026-10-02",listOf(set(30.0,5,LoadModeV2.ASSISTED))))
        val result=report(logs,ExerciseProgressMetric.BEST_SET)
        assertEquals(30.0,result.points.last().value,0.0);assertTrue(result.points.last().isPr)
        assertFalse(result.points.last().higherIsBetter)
    }
    @Test fun recordsDeRepeticionesConservanCadaCargaDeLaMismaSesion() {
        val result=report(listOf(log("a","2026-10-01",listOf(set(50.0,12),set(100.0,5)))),ExerciseProgressMetric.MAX_REPS)
        assertEquals(2,result.points.size)
        assertEquals(setOf(50.0,100.0),result.records.filter { it.metric==ExerciseProgressMetric.MAX_REPS }.map { it.point.bestSet.weight }.toSet())
        assertTrue(result.points.last().breaksBefore)
    }
}
