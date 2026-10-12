# Línea base de las pruebas AUGE antes de la ola Rings v4

Paquete 0.1 (Haiku 5.5). Solo documenta: no hay cambios de código ni de pruebas.

## Cabecera

- **Fecha:** 2026-10-12. Listado de tareas de 05:40:16 a 05:44:53; lotes E1 a E5 de 05:45:31 a 05:56:36.
- **Rama y base:** `r4/0-1` en `C:\kw\v3c`, creada desde `feat/rings-v4`. Hash base: `d946d1bf2` (`git -C /c/kw/v3c rev-parse --short HEAD`).
- **Tarea Gradle:** `:app:testBaseDebugUnitTest`, confirmada con `:app:tasks --all` (`/c/kw/logs/r4-0-1-tasks.log`, línea 95). Antes compila `:app:compileBaseDebugUnitTestKotlin` (línea 166 del listado). `:app:compileBaseDebugKotlin` también existe, pero aquí no se usa.
- **Máquina:** al empezar, 10,4 GB de RAM libre de 31,3 GB. Disco C: 17 GB libres de 699 GB (98 % ocupado). Ninguna espera del envoltorio en E1 a E5.
- **Recuento:** `python -I docs/rings-v4/tools/count_tests.py --clases`, desde la raíz del worktree, justo al terminar cada lote. Salidas en `/c/kw/logs/r4-0-1-E<n>-count.txt`; horas en `r4-0-1-E<n>-inicio.txt` y `-fin.txt`.
- **Tiempo de pared:** desde que se lanza el envoltorio hasta que termina el recuento (incluye la espera de ranura si la hubo).

## Resumen

| Lote | Clases (esperadas / ejecutadas) | Pruebas (esperadas / ejecutadas) | Fallos | Errores | Omitidas | Tiempo de pared | Gradle | Log |
|---|---|---|---|---|---|---|---|---|
| E1 Drenaje | 13 / 13 | 30 / 30 | 0 | 0 | 0 | 8 min 44 s | 8 min 43 s | `r4-0-1-E1.log` |
| E2 Recuperación y anillos | 16 / 16 | 46 / 46 | 0 | 0 | 0 | 25 s | 22 s | `r4-0-1-E2.log` |
| E3 Aprendizaje | 8 / 8 | 32 / 32 | 0 | 0 | 0 | 24 s | 22 s | `r4-0-1-E3.log` |
| E4 Evidencia inicial | 5 / 5 | 51 / 51 | 0 | 0 | 0 | 24 s | 22 s | `r4-0-1-E4.log` |
| E5 Readiness y carga | 15 / 15 | 56 / 56 | 0 | 0 | 0 | 27 s | 25 s | `r4-0-1-E5.log` |
| **Total** | **57 / 57** | **215 / 215** | **0** | **0** | **0** | **≈ 10 min 24 s** | | |

Los recuentos coinciden con lo esperado en los cinco lotes. En todos, `testBaseDebugUnitTest` se ejecutó (no quedó `UP-TO-DATE`) y el filtro no quedó vacío.

## Lotes

Cada comando es el exacto de la ejecución (el envoltorio añade `--no-daemon` y las banderas de memoria). Cada `--tests` lleva el paquete completo.

### E1 · Motor de drenaje

Inicio 05:45:31, fin 05:54:15. Incluye la primera compilación de las pruebas (`compileBaseDebugUnitTestKotlin` se ejecutó). Suma de tiempos de las pruebas en los XML: 0,3 s. Sin fallos.

```
bash /c/kw/tools/gradle_slot.sh /c/kw/v3c/android-native :app:testBaseDebugUnitTest --tests "com.example.kpkn.domain.auge.AugeMuscleNormalizationTest" --tests "com.example.kpkn.domain.auge.AugeMuscleAttributionTest" --tests "com.example.kpkn.domain.auge.AugeMuscleProfileTest" --tests "com.example.kpkn.domain.auge.SessionDrainBoundsTest" --tests "com.example.kpkn.domain.auge.CardioRingDrainBoundsTest" --tests "com.example.kpkn.domain.auge.AugeUnilateralImpactTest" --tests "com.example.kpkn.domain.auge.AugeSupersetDensityTest" --tests "com.example.kpkn.domain.auge.AugeFatigueEngineEffectiveSetTest" --tests "com.example.kpkn.domain.auge.PerformanceImpliedBatteryTest" --tests "com.example.kpkn.domain.auge.AugeEditorCompletedParityTest" --tests "com.example.kpkn.domain.auge.AugePostSessionPreviewImmediateTest" --tests "com.example.kpkn.domain.auge.WorkoutLogAugeImpactSerializationTest" --tests "com.example.kpkn.domain.auge.SleepLogExtendedToSleepLogTest" > /c/kw/logs/r4-0-1-E1.log 2>&1
```

Recuento (`--clases`):

```
clases=13 pruebas=30 fallos=0 errores=0 omitidas=0 tiempo_suma=0.3s
  com.example.kpkn.domain.auge.AugeEditorCompletedParityTest: pruebas=1 mal=0 omitidas=0 0.1s
  com.example.kpkn.domain.auge.AugeFatigueEngineEffectiveSetTest: pruebas=4 mal=0 omitidas=0 0.0s
  com.example.kpkn.domain.auge.AugeMuscleAttributionTest: pruebas=3 mal=0 omitidas=0 0.0s
  com.example.kpkn.domain.auge.AugeMuscleNormalizationTest: pruebas=6 mal=0 omitidas=0 0.0s
  com.example.kpkn.domain.auge.AugeMuscleProfileTest: pruebas=1 mal=0 omitidas=0 0.0s
  com.example.kpkn.domain.auge.AugePostSessionPreviewImmediateTest: pruebas=2 mal=0 omitidas=0 0.0s
  com.example.kpkn.domain.auge.AugeSupersetDensityTest: pruebas=1 mal=0 omitidas=0 0.0s
  com.example.kpkn.domain.auge.AugeUnilateralImpactTest: pruebas=1 mal=0 omitidas=0 0.0s
  com.example.kpkn.domain.auge.CardioRingDrainBoundsTest: pruebas=2 mal=0 omitidas=0 0.0s
  com.example.kpkn.domain.auge.PerformanceImpliedBatteryTest: pruebas=2 mal=0 omitidas=0 0.0s
  com.example.kpkn.domain.auge.SessionDrainBoundsTest: pruebas=5 mal=0 omitidas=0 0.1s
  com.example.kpkn.domain.auge.SleepLogExtendedToSleepLogTest: pruebas=1 mal=0 omitidas=0 0.0s
  com.example.kpkn.domain.auge.WorkoutLogAugeImpactSerializationTest: pruebas=1 mal=0 omitidas=0 0.0s
```

### E2 · Recuperación y anillos

Inicio 05:54:29, fin 05:54:54. `compileBaseDebugUnitTestKotlin` ya estaba compilado (`UP-TO-DATE`). Suma de tiempos en XML: 0,6 s. Sin fallos.

```
bash /c/kw/tools/gradle_slot.sh /c/kw/v3c/android-native :app:testBaseDebugUnitTest --tests "com.example.kpkn.domain.auge.AugeRecoveryEngineRingTest" --tests "com.example.kpkn.domain.auge.AugeRecoveryEngineManualOverrideTest" --tests "com.example.kpkn.domain.auge.AugeRecoveryEnginePreWorkoutDiscomfortTest" --tests "com.example.kpkn.domain.auge.AugeRingCharacterizationTest" --tests "com.example.kpkn.domain.auge.AugeRingDrainRealismTest" --tests "com.example.kpkn.domain.auge.OvernightRecoverySensationTest" --tests "com.example.kpkn.domain.auge.AugeTtcRecoveryHoursTest" --tests "com.example.kpkn.domain.auge.NutritionRecoveryEngineTest" --tests "com.example.kpkn.domain.auge.SpinalWarpContinuityTest" --tests "com.example.kpkn.domain.auge.SpinalAxialGateTest" --tests "com.example.kpkn.domain.auge.HardSessionSpinalFloorTest" --tests "com.example.kpkn.domain.auge.CardioSystemicDrainTest" --tests "com.example.kpkn.domain.auge.SpineRingSingleDefinitionTest" --tests "com.example.kpkn.domain.auge.ManualSpinalOverrideSticksTest" --tests "com.example.kpkn.domain.auge.MuscleColdStartParityTest" --tests "com.example.kpkn.domain.auge.ReadinessSheetHomeParityTest" > /c/kw/logs/r4-0-1-E2.log 2>&1
```

Recuento (`--clases`):

```
clases=16 pruebas=46 fallos=0 errores=0 omitidas=0 tiempo_suma=0.6s
  com.example.kpkn.domain.auge.AugeRecoveryEngineManualOverrideTest: pruebas=3 mal=0 omitidas=0 0.2s
  com.example.kpkn.domain.auge.AugeRecoveryEnginePreWorkoutDiscomfortTest: pruebas=2 mal=0 omitidas=0 0.0s
  com.example.kpkn.domain.auge.AugeRecoveryEngineRingTest: pruebas=14 mal=0 omitidas=0 0.2s
  com.example.kpkn.domain.auge.AugeRingCharacterizationTest: pruebas=2 mal=0 omitidas=0 0.0s
  com.example.kpkn.domain.auge.AugeRingDrainRealismTest: pruebas=6 mal=0 omitidas=0 0.0s
  com.example.kpkn.domain.auge.AugeTtcRecoveryHoursTest: pruebas=2 mal=0 omitidas=0 0.0s
  com.example.kpkn.domain.auge.CardioSystemicDrainTest: pruebas=1 mal=0 omitidas=0 0.0s
  com.example.kpkn.domain.auge.HardSessionSpinalFloorTest: pruebas=1 mal=0 omitidas=0 0.0s
  com.example.kpkn.domain.auge.ManualSpinalOverrideSticksTest: pruebas=1 mal=0 omitidas=0 0.0s
  com.example.kpkn.domain.auge.MuscleColdStartParityTest: pruebas=1 mal=0 omitidas=0 0.0s
  com.example.kpkn.domain.auge.NutritionRecoveryEngineTest: pruebas=7 mal=0 omitidas=0 0.0s
  com.example.kpkn.domain.auge.OvernightRecoverySensationTest: pruebas=2 mal=0 omitidas=0 0.0s
  com.example.kpkn.domain.auge.ReadinessSheetHomeParityTest: pruebas=1 mal=0 omitidas=0 0.0s
  com.example.kpkn.domain.auge.SpinalAxialGateTest: pruebas=1 mal=0 omitidas=0 0.0s
  com.example.kpkn.domain.auge.SpinalWarpContinuityTest: pruebas=1 mal=0 omitidas=0 0.0s
  com.example.kpkn.domain.auge.SpineRingSingleDefinitionTest: pruebas=1 mal=0 omitidas=0 0.0s
```

### E3 · Aprendizaje

Inicio 05:55:04, fin 05:55:28. Compilación de pruebas `UP-TO-DATE`. Suma de tiempos en XML: 0,2 s. Sin fallos.

```
bash /c/kw/tools/gradle_slot.sh /c/kw/v3c/android-native :app:testBaseDebugUnitTest --tests "com.example.kpkn.domain.auge.AugeAdaptiveEngineTest" --tests "com.example.kpkn.domain.auge.AdaptiveCalibrationAppliesTest" --tests "com.example.kpkn.domain.auge.PerformanceTauObservationTest" --tests "com.example.kpkn.domain.auge.SystemTauRoundTripTest" --tests "com.example.kpkn.domain.auge.TauObservationCapTest" --tests "com.example.kpkn.domain.auge.PerformanceTauProgressionBiasTest" --tests "com.example.kpkn.domain.auge.ManualSpinalLearningAxialGateTest" --tests "com.example.kpkn.domain.auge.AdaptiveCacheSchema3MigrationTest" > /c/kw/logs/r4-0-1-E3.log 2>&1
```

Recuento (`--clases`):

```
clases=8 pruebas=32 fallos=0 errores=0 omitidas=0 tiempo_suma=0.2s
  com.example.kpkn.domain.auge.AdaptiveCacheSchema3MigrationTest: pruebas=1 mal=0 omitidas=0 0.0s
  com.example.kpkn.domain.auge.AdaptiveCalibrationAppliesTest: pruebas=6 mal=0 omitidas=0 0.1s
  com.example.kpkn.domain.auge.AugeAdaptiveEngineTest: pruebas=11 mal=0 omitidas=0 0.0s
  com.example.kpkn.domain.auge.ManualSpinalLearningAxialGateTest: pruebas=1 mal=0 omitidas=0 0.0s
  com.example.kpkn.domain.auge.PerformanceTauObservationTest: pruebas=10 mal=0 omitidas=0 0.1s
  com.example.kpkn.domain.auge.PerformanceTauProgressionBiasTest: pruebas=1 mal=0 omitidas=0 0.0s
  com.example.kpkn.domain.auge.SystemTauRoundTripTest: pruebas=1 mal=0 omitidas=0 0.0s
  com.example.kpkn.domain.auge.TauObservationCapTest: pruebas=1 mal=0 omitidas=0 0.0s
```

### E4 · Evidencia inicial y arranque

Inicio 05:55:36, fin 05:56:00. Compilación de pruebas `UP-TO-DATE`. Suma de tiempos en XML: 0,3 s. Sin fallos.

```
bash /c/kw/tools/gradle_slot.sh /c/kw/v3c/android-native :app:testBaseDebugUnitTest --tests "com.example.kpkn.domain.auge.InitialRecoveryEvidencePolicyTest" --tests "com.example.kpkn.domain.auge.InitialRecoveryEvidenceV2Test" --tests "com.example.kpkn.domain.auge.AugePerMuscleInitialEvidenceTest" --tests "com.example.kpkn.domain.auge.RingsIntroSimulatorTest" --tests "com.example.kpkn.domain.auge.WellbeingSaveMergeTest" > /c/kw/logs/r4-0-1-E4.log 2>&1
```

Recuento (`--clases`):

```
clases=5 pruebas=51 fallos=0 errores=0 omitidas=0 tiempo_suma=0.3s
  com.example.kpkn.domain.auge.AugePerMuscleInitialEvidenceTest: pruebas=6 mal=0 omitidas=0 0.2s
  com.example.kpkn.domain.auge.InitialRecoveryEvidencePolicyTest: pruebas=13 mal=0 omitidas=0 0.1s
  com.example.kpkn.domain.auge.InitialRecoveryEvidenceV2Test: pruebas=8 mal=0 omitidas=0 0.0s
  com.example.kpkn.domain.auge.RingsIntroSimulatorTest: pruebas=14 mal=0 omitidas=0 0.0s
  com.example.kpkn.domain.auge.WellbeingSaveMergeTest: pruebas=10 mal=0 omitidas=0 0.0s
```

### E5 · Readiness, avisos y carga

Inicio 05:56:09, fin 05:56:36. Compilación de pruebas `UP-TO-DATE`. Suma de tiempos en XML: 0,3 s. Sin fallos.

```
bash /c/kw/tools/gradle_slot.sh /c/kw/v3c/android-native :app:testBaseDebugUnitTest --tests "com.example.kpkn.domain.auge.ExerciseReadinessEngineTest" --tests "com.example.kpkn.domain.auge.ExerciseReadinessEngineArticularTest" --tests "com.example.kpkn.domain.auge.ExerciseReadinessBlendPinTest" --tests "com.example.kpkn.domain.auge.AxialLoadMonitorTest" --tests "com.example.kpkn.domain.auge.SystemicLoadMonitorTest" --tests "com.example.kpkn.domain.auge.OvertrainingDetectorTest" --tests "com.example.kpkn.domain.auge.DiscomfortSuggestionEngineTest" --tests "com.example.kpkn.domain.auge.AugeUnitsConsistencyTest" --tests "com.example.kpkn.domain.auge.AugeLanguageConsistencyTest" --tests "com.example.kpkn.domain.auge.RecoveryBandsSingleSourceTest" --tests "com.example.kpkn.domain.auge.RingProjectionTest" --tests "com.example.kpkn.domain.auge.PersonalBaselineTest" --tests "com.example.kpkn.domain.auge.LoadAdvisoryHysteresisTest" --tests "com.example.kpkn.domain.auge.AdvisoryDismissalTest" --tests "com.example.kpkn.domain.auge.LoadAdvisorySessionContextTest" > /c/kw/logs/r4-0-1-E5.log 2>&1
```

Recuento (`--clases`):

```
clases=15 pruebas=56 fallos=0 errores=0 omitidas=0 tiempo_suma=0.3s
  com.example.kpkn.domain.auge.AdvisoryDismissalTest: pruebas=1 mal=0 omitidas=0 0.1s
  com.example.kpkn.domain.auge.AugeLanguageConsistencyTest: pruebas=6 mal=0 omitidas=0 0.1s
  com.example.kpkn.domain.auge.AugeUnitsConsistencyTest: pruebas=6 mal=0 omitidas=0 0.0s
  com.example.kpkn.domain.auge.AxialLoadMonitorTest: pruebas=1 mal=0 omitidas=0 0.0s
  com.example.kpkn.domain.auge.DiscomfortSuggestionEngineTest: pruebas=9 mal=0 omitidas=0 0.0s
  com.example.kpkn.domain.auge.ExerciseReadinessBlendPinTest: pruebas=1 mal=0 omitidas=0 0.0s
  com.example.kpkn.domain.auge.ExerciseReadinessEngineArticularTest: pruebas=6 mal=0 omitidas=0 0.0s
  com.example.kpkn.domain.auge.ExerciseReadinessEngineTest: pruebas=17 mal=0 omitidas=0 0.0s
  com.example.kpkn.domain.auge.LoadAdvisoryHysteresisTest: pruebas=1 mal=0 omitidas=0 0.0s
  com.example.kpkn.domain.auge.LoadAdvisorySessionContextTest: pruebas=2 mal=0 omitidas=0 0.0s
  com.example.kpkn.domain.auge.OvertrainingDetectorTest: pruebas=2 mal=0 omitidas=0 0.0s
  com.example.kpkn.domain.auge.PersonalBaselineTest: pruebas=1 mal=0 omitidas=0 0.0s
  com.example.kpkn.domain.auge.RecoveryBandsSingleSourceTest: pruebas=1 mal=0 omitidas=0 0.0s
  com.example.kpkn.domain.auge.RingProjectionTest: pruebas=1 mal=0 omitidas=0 0.0s
  com.example.kpkn.domain.auge.SystemicLoadMonitorTest: pruebas=1 mal=0 omitidas=0 0.0s
```

## Fallos preexistentes

Ninguno. Los 215 casos de las 57 clases pasan en la base `d946d1bf2`. Ningún recuento muestra líneas `FALLO`.

## Observaciones

1. **Reloj real o zona del equipo.** Grep en `android-native/app/src/test/java/com/example/kpkn/domain/auge/` de `System.currentTimeMillis`, `Instant.now`, `ZoneId.systemDefault`, `TimeZone.getDefault`, `Calendar.getInstance`, `Date()`, `LocalDate.now`, `LocalDateTime.now`, `ZonedDateTime.now`, `LocalTime.now` y `System.nanoTime`. Solo aparecen `System.currentTimeMillis()` (18 líneas) y `LocalDate.now()` (21 líneas), en 8 archivos, todos en los lotes E1 a E3:
   - E1: `SessionDrainBoundsTest.kt:130` (`currentTimeMillis`).
   - E2: `AugeRecoveryEngineManualOverrideTest.kt:41` (`currentTimeMillis`), `:44` y `:100` (`LocalDate.now`).
   - E2: `AugeRecoveryEnginePreWorkoutDiscomfortTest.kt:51` (`currentTimeMillis`), `:57`, `:71`, `:94` y `:106` (`LocalDate.now`).
   - E2: `AugeRecoveryEngineRingTest.kt:115` y `:138` (`LocalDate.now`), `:119`, `:142`, `:144`, `:185`, `:250` y `:277` (`currentTimeMillis`).
   - E2: `AugeRingDrainRealismTest.kt:79`, `:106`, `:130`, `:153`, `:179` y `:214` (`currentTimeMillis`).
   - E2: `AugeTtcRecoveryHoursTest.kt:18` y `:63` (`currentTimeMillis`).
   - E2: `NutritionRecoveryEngineTest.kt:61`, `:75`, `:77`, `:78`, `:90`, `:92`, `:106`, `:108`, `:121`, `:133`, `:165` y `:167` (`LocalDate.now`).
   - E3: `AdaptiveCalibrationAppliesTest.kt:79` (`currentTimeMillis`) y `:82` (`LocalDate.now`).

   Riesgo: `LocalDate.now()` depende del día y de la zona del equipo, así que una corrida a medianoche o en otra zona puede dejar las entradas en un día distinto del que usa el motor. Hoy todas pasan. No he comprobado caso por caso si el motor recibe el reloj como parámetro; eso queda para el paquete 1.7 (regla 2 del brief común).
2. **Pruebas omitidas:** ninguna (`omitidas=0` en los cinco lotes). En `T/domain/auge/` no hay `@Ignore`, `@Disabled`, `Assume*`, `@RunWith` ni pruebas parametrizadas.
3. **Conteo estático frente a ejecución:** el número de `@Test` por clase, contado sobre el fuente con awk, coincide con los XML de Gradle en las 57 clases. `AxialFixtures` (en `AugeRingsAudit2026Test.kt`) es una clase auxiliar sin `@Test` y no entra en ningún lote.
4. **Fuera de alcance:** el lote E6 no se ha ejecutado, porque el brief 0.1 lo excluye y lo supervisa el orquestador. La fila 0.1 del plan lo menciona, pero el brief es el que manda aquí. Tampoco se ha corrido la suite completa.
5. **Tiempos:** E1 incluye la primera compilación de pruebas (8 min 43 s). E2 a E5 reutilizan esa compilación y tardan 22 a 25 s de Gradle. El listado de tareas esperó 250 s por ranura del envoltorio (`[gradle_slot] ranura 1 tras esperar 250s`), por eso su tiempo de pared (4 min 37 s) supera los 13 s de Gradle.
6. **Disco:** 17 GB libres de 699 GB (98 % ocupado) durante toda la ejecución, sin errores de espacio. Es el margen que queda para las compilaciones de los siguientes paquetes.

## Cifras que cambian respecto a la base

Ninguna: no se ha modificado código.
