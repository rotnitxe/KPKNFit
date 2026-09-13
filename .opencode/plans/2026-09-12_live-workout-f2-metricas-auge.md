---
flags: [auge]
---

# F2 — Métricas canónicas, volumen y AUGE/RINGS

Depende de F0 (claves sanas) y D1/D2. Cierra: **VOL-01, VOL-02, VOL-03, VOL-05, VOL-06, VOL-07, VOL-08, VOL-09, VOL-10, VOL-11, VOL-13, VOL-14, VOL-16, VOL-17, VOL-18, VOL-19, VOL-21, VOL-22**. VOL-12/15/20 P2-P3 según quepa.

Paridad iOS/backend: **no** se porta en esta ola. Documentar en `docs/ANDROID_ARCHITECTURE_MAP.md` el contrato de serie lógica y kg canónico para el siguiente puerto. No recalibrar sigmoidal ni caps de `AugeFatigueEngine`.

## Objetivo

Un solo builder de `CompletedExercise`. Unilateral, kg, cardio, isométricos y PRs coinciden entre cockpit, sheet, log y AUGE.

## Rutas

| Pieza | Archivos |
|-------|----------|
| Builder único | nuevo `screens/workout/WorkoutCompletedExercises.kt` (puro); callers `WorkoutViewModel.buildLiveCompletedExercises`, `WorkoutScreen` preview (~464), `WorkoutFinishController.kt:118-164` |
| Unilateral D1 | `WorkoutFinishHost.kt:310-311`, `WorkoutFinishController.computeMuscleSetSurplus` (~607-637), `WorkoutStepRules.kt`, `WorkoutVolumeDeltaTest.kt` |
| kg D2 | `WorkoutSetRecorder.kt:171-173`, `AugeFatigueEngine.kt:473-474`, `TrainingEnergyEngine.kt:269-339`, `LoadSuggestionEngine` / `WorkoutPerformanceHomologationEngine.normalizeLoad` |
| Cardio/isométrico | `AugeFatigueEngine.isSetEffective` (:250-258), builder (`cardioDetails` copy) |
| PR buffer | `WorkoutViewModel.evaluateSetEntryV3` (:1368-1379), `cancelWorkout` / `abandonWorkoutWithoutSaving`, `WorkoutFinishController` |
| Overlay drain | `WorkoutV2Body.kt` `drainOverlayState` |
| kcal cardio | `TrainingEnergyEngine` leer `CompletedSet.calories` |

## Impacto

### VOL-03 + VOL-02 — Un `toCompletedExercises(state, session, catalogIndex)`

Parámetros: `completedSets`, `skippedExerciseIds`, `visibleOrAll`, `includeCardioDetails: Boolean` (siempre **true**).

Reglas (tabla de la auditoría, unificar a la columna “vivo” + extras del finish):

- Ejercicios: `sessionForActiveMode(...).allExercises()` menos los skipped **sin** series. Si skipped **con** series, **sí** entran (trabajo hecho).
- Sets: mismas claves que hoy (`id_idx`, `_L`, `_R`) **después** del remap F0.
- `cardioDetails = exercise.cardioDetails` siempre (cierra VOL-02). Test existente `AugeUnitsConsistencyTest.cardioDetails_allowTimeOnlyDrain` pasa a aplicar también al log.
- `supersetRounds` / `supersetExerciseCount` como el finish (no los omitas en vivo).
- Preview del sheet y persistencia llaman la **misma** función.

### VOL-01 + VOL-16 + VOL-17 — Serie lógica (D1)

```kotlin
fun logicalWorkingSetCount(exercise: Exercise, completedSets: Map<String, CompletedSet>): Int
```

Un `setIdx` cuenta 1 si todos los `expectedSidesForSet` están en el mapa y no skipped/warmup. Surplus: planificado = `sets.size` (ya); actual = `logicalWorkingSetCount`, no `completedSets` keys.

Finish “Series: N” = suma de series lógicas no-warmup. Roadmap: slots físicos L/R se quedan (ejecución); el % de sesión usa slots físicos para no mentir “100%” con un solo lado, **o** documentar en UI “1/3 series (L pendiente)”. Canon de progreso: **slots físicos** en roadmap (como hoy), **series lógicas** en volumen/surplus/AUGE. Test: 3 slots unilaterales completos → surplus 0, AUGE ≈ 3 series bilaterales, roadmap 6/6.

Callers de `isSetDone` (NAV-04) se unifican al helper de F3; F2 al menos usa `expectedSidesForSet` en el contador lógico.

### VOL-07 — kg canónico

- `userVitals.weight` ya es kg (`ProfileScreen` convierte al guardar).
- **Borrar** `bw * 0.45359237` en `WorkoutSetRecorder.kt:172`.
- `AugeFatigueEngine` / `TrainingEnergyEngine`: `effectiveLoad` **siempre en kg**. No dividir por 2.204 porque `weightUnit == LBS`. La unidad solo afecta UI de input (F7/VOZ-09).
- Tests: `AugeUnitsConsistencyTest` / `TrainingEnergyEngineTest` con `WeightUnit.LBS` y vitals 80 kg + logged 100 → drain/kcal iguales que KG.

### VOL-08 + D2 — Carga homologada

Cambiar `normalizeLoad` / equivalente que escribe `CompletedSet.weight`:

- BODYWEIGHT → `userWeightKg` (no 0).
- LASTRE → `userWeightKg + extra`.
- ASSISTED → `(userWeightKg - assistance).coerceAtLeast(0)` (no asistencia positiva).

Si `userWeightKg == null` y BODYWEIGHT: no registrar (mensaje “indica tu peso”) o caer a 0 con flag; no inventar 70 kg.

Tonelaje sheet/share/log = `sum(weight * effectiveRepEquivalent)` con esa carga. Extraer `sessionTonnage` / `sessionLogicalSetCount` (VOL-09).

### VOL-10 — Drops / rest-pause en tonelaje

`sessionTonnage` suma `drop.weight * drop.reps` y rest-pause. AUGE ya suma reps extra; no doble-contar series. CLUSTER/myo: no vender en vivo (F3).

### VOL-11 — `isSetEffective`

Hoy time-only + weight 0 + reps 0 → false (protege cinta **y** mata plancha).

```kotlin
if (set.reps <= 0 && set.weight <= 0.0 && hasTime) {
    return set.cardioDetails != null  // o el CompletedExercise padre tiene cardioDetails
}
```

`CompletedSet` no tiene `cardioDetails`; el filtro vive en el engine de sesión (`calculateCompletedSessionDrain`) que ya recorre `CompletedExercise`. Mover el guard time-only **ahí**: si `exercise.cardioDetails != null` → `CardioRingDrainEngine`; si no y TIME → `calculateSetBatteryDrain` (reps sintéticas tiempo/5, ya implementadas). `isSetEffective` para fuerza TIME+peso0 → **true** si RPE≥6.

Test nuevo: plancha 45 s, weight 0, sin cardioDetails → drain > 0. Cinta con cardioDetails → no pasa por reps sintéticas.

### VOL-05 + VOL-06 — PR buffer

`evaluateSetEntryV3` **no** hace `upsertContextPerformanceState` / `upsertGlobalPerformance` en Room al registrar. Actualiza solo caches in-memory (`contextualPerformanceCache`). Persist Room en `finalizeWorkout` (junto a `PerformanceRangeStore.persistFinishedSessionPerformance`). `abandon`/`cancel` tiran el cache.

PR: `isPr = priorCount >= 1 && metric > priorBest` (no el primer sample). Excluir warmup/skipped/failed.

### VOL-13 + VOL-22 — Drain vivo vs finish

`liveDrainSummary()` pasa `catalogExerciseIndex()` igual que finish. El overlay Compose: asignar `drainOverlayState` desde el mismo `SetDrain` que ya usa el recorder (`WorkoutSetRecorder.kt:572-598`) o borrar la UI muerta. Preferir asignar (promesa del cockpit).

### VOL-18 — kcal cardio

`TrainingEnergyEngine`: si `set.calories > 0`, usarlas (CardioCalorieEngine ya las escribió). No 0 por falta de `dbInfo`.

### VOL-19 — Intensidad

`SessionIntensityEngine` working sets: incluir TIME con `timeSeconds > 0` y BODYWEIGHT con reps > 0. Default 7.0 solo si **no hay** working sets.

### VOL-21 — Failed 0 reps

No `reps.coerceAtLeast(1)` para failed vacíos. Drain 0 si skipped o failed sin trabajo.

### VOL-14 — eRM al finish

Filtrar warmup, skipped, `weight<=0`. Un lado ≠ dos samples de setCount de volumen. Sin conversión lb (ya kg).

## Pruebas

| Test | Qué |
|------|-----|
| `WorkoutCompletedExercisesParityTest` | vivo/preview/finish iguales en cardioDetails, skipped-con-series, rounds |
| `WorkoutVolumeDeltaTest` | caso unilateral 3 slots L+R → surplus 0 |
| `AugeUnilateralImpactTest` | pin existente |
| `AugeFatigueEngineEffectiveSetTest` | plancha efectiva; cardio time-only no efectivo como fuerza |
| `AugeUnitsConsistencyTest` | LBS no divide cargas ya en kg |
| `WorkoutPerformanceHomologationEngineTest` | primer sample no PR; abandon no escribe (fake repo) |
| `TrainingEnergyEngineTest` | `set.calories` cardio; BODYWEIGHT usa vitals kg |

Pins: `AugeRingDrainRealismTest`, `CardioRingDrainBoundsTest`, `SessionDrainBoundsTest`.

```bash
./gradlew --no-daemon --console=plain testBaseDebugUnitTest --tests '*Auge*' --tests '*WorkoutVolume*' --tests '*CompletedExercise*' --tests '*TrainingEnergy*' --tests '*Homologation*'
```

## Riesgos

- Cambiar `CompletedSet.weight` de 0 a BW **cambia drain histórico de sesiones nuevas**, no reescribe logs viejos (correcto).
- Surplus unilateral a 0 puede sorprender a quien usaba el bug para “avanzar volumen”. Es el bug VOL-17.
- iOS seguirá divergente hasta el puerto; documentarlo.

## Fuera de alcance

Recalibrar curvas, iOS/backend runtime, overlay copy de Home rings, GPS fórmula de `CardioCalorieEngine`.
