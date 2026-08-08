# Plan — Corregir series unilaterales (auditoría 3)

**Fecha:** 2026-08-09  
**Autor:** orquestador (muse-spark-1.2)  
**Estado:** `pending_approval` (no editar código de producto hasta aprobación explícita)  
**Auditoría fuente:** `docs/audits/2026-08-editor-sesiones/unilateral.md` (133 líneas, solo lectura, 2026-08, alcance `data/models/Session.kt` + `screens/sessioneditor/` + `screens/workout/` + `domain/auge/` + `domain/training/`)  
**Solicitud:** solucionar TODO lo cubierto por la auditoría 3, paso a paso

---

## 1. Resumen ejecutivo

El modelo unilateral es único y el vivo lo interpreta con los mismos campos (`isEffectivelyUnilateral(): Session.kt:441`, `leftTarget/rightTarget: Session.kt:360`, `unilateralSideOrder: Session.kt:250`, `restBetweenSidesSeconds: Session.kt:252`). La base está alineada; las desconexiones graves son:

1. **#1 ALTO — Contaminación drops/rest-pause L→R:** `SetExecutionCard.kt:794` estado `dropSetEnabled/restPauseEnabled` keyed solo por `(exerciseId,setIdx)` sin `side`; tras `commitCapturedRecord` retiene y auto-cambia a lado opuesto (`2357-2359`), aplicando valores de L en R.
2. **#2 ALTO — Borrado descanso entre lados en superset:** `ExerciseEditorCard.kt:409` forza `sideSeconds=null` si superset, `411-417` escribe `restBetweenSidesSeconds=null` → desaparece `BETWEEN_SIDES` (`WorkoutStepRules.kt:248`, `WorkoutSetRecorder.kt:452`).
3. **#3 ALTO — Volumen 2× vs preview + AUGE acumulado:** `WorkoutLog` guarda L/R como 2 sets separados (`WorkoutFinishController.kt:87-92`, `149-151` suma `peso*rep` ambos) mientras preview cuenta 1/set (`VolumeCalculator.kt:208-219`, `SessionEditorAugeComputation.kt:331-367`); `ProgramAnalyticsEngine.kt:634-643` sin awareness de lado.
4. **#4 ALTO — skipSet/skipRound fantasma single-side:** `WorkoutStepNavigator.kt:240-266` ignora `hasLeftOnly/hasRightOnly` (solo `sideOrder`), `186-204` `skipCurrentSupersetRound` marca siempre L+R aunque paso solo L → set fantasma en log `FinishController:91`.

Familia **#5 `isSetDone` exige L+R** (`WorkoutViewModel:1122`, `WorkoutEditingRules:53`) bloquea edición/voz/deuda y fuerza cascada #4/#6/#8.

Sin fijar **#5 núcleo semántico** (qué es “hecho” para single-side) nada de `#4,6,8,9,11` se estabiliza.

---

## 2. Contexto y reproducción

- **#1 repro:** unilateral `L/R` con `SHARED` o `INDEPENDENT`, `set 1/L` con `dropSets=[10kg×6]` → grabar L → auto-cambio a R (`2357`) sin limpiar → grabar R sin tocar drops → `WorkoutSetRecorder:2295` aplica mismos `dropSets` a R (log contaminado).
- **#2 repro:** ejercicio unilateral con `restBetweenSides=30`, añadir a superset (editor) → `ExerciseEditorCard:409` muestra `sideSeconds=null` → pulsar “confirmar descanso” (`411-417`) escribe `null` → en vivo `BETWEEN_SIDES` nunca arranca (`WorkoutStepRules:248` `>0` false).
- **#3 repro:** sesión con 5 ejercicios unilaterales `INDEPENDENT` 3 sets cada uno (30 lados) → preview `VolumeCalculator:208` estima `15` sets, `TimeCoach:849` 15; tras entrenar, `WorkoutLog` tiene 30 `CompletedSet`, `ProgramAnalytics` semanal muestra ~2×, `SessionMuscleFilter` similar.
- **#4 repro:** unilateral single-side `hasLeftOnly=true` (solo `leftTarget`), paso único `ex_0_L` → `skipSet` 1º tap marca `L` skipped, `stillPendingSide` exige `R` (`286-289`) → no avanza; 2º tap marca `R` (inexistente) como skipped → log con `_R` fantasma (`FinishController:91`).
- **#5 repro:** mismo single-side → `isSetDone` exige `_L && _R` (`WorkoutViewModel:1122`) → nunca done → `WorkoutEditingRules:35` early-return `null` → no editable, voz lo lista como pendiente (`VoiceHandler:593`), sugerencia recalcula lado ya completado.
- **#6 repro:** mitad de sesión bilateral → `toggledBilateralUnilateral` OFF borra `left/rightTarget` y `restBetweenSides` (`SessionEditorControls:872-899`); atrás hidrata con claves `_L/_R` en `completedSets` (`Hydrator:122`) → `isWorkoutStepDone:689` no cuenta `_` ↔ `_L/_R`.

---

## 3. Hallazgos verificados (archivo:línea actual, `android-native/app/src/main/java/com/example/kpkn/`)

> Room v20 autoridad (`KpknDatabase.kt:20`) vs `docs/ARCHITECTURE.md:28` v19. Todas las rutas existen; `AugeFatigueEngine:374` (audit 276) desplazado por heurística, `RoadmapBar:310` ya correcta.

| # | Sev | Hallazgo | Evidencia |
|---|---|---|---|
| **1** | ALTO | Drops/rest-pause L contaminan R | `SetExecutionCard.kt:794-824` `remember(exercise.id,setIdx)` sin `side`, `843-867 LaunchedEffect` sin side, `2295-2304` aplica `advanced`, `2339-2345` retiene + `2357-2359,2565` auto `selectSide(right)` |
| **2** | ALTO | Editar descanso en superset borra `restBetweenSidesSeconds` | `ExerciseEditorCard.kt:405-420` `sideSeconds=null` si `isUnilateral&&isSuperset`, `CompactRestBundleButton.kt:407-417` `onConfirm side?.takeIf>0` → null |
| **3** | ALTO | Volumen/AUGE 2× vs preview | `WorkoutFinishController.kt:87-92,149-151` L+R separados; `VolumeCalculator.kt:208-219` 1/set; `AugeFatigueEngine.kt:374-376,480-483` `sideScale 0.5` mitiga solo `drain` por set ( `556 0.5`), no `totalVolume`; `ProgramAnalyticsEngine.kt:634-643` |
| **4** | ALTO | skipSet/skipRound fantasma single-side | `WorkoutStepNavigator.kt:240-266` `sideOrder.firstOrNull incomplete 243` ignora `hasLeftOnly`, `186-204` `skipCurrentSupersetRound` siempre `left+right 188-192` |
| **5** | MEDIO | `isSetDone` single-side nunca done | `WorkoutViewModel.kt:1122-1126` `containsKey(_)+L&&R`; `WorkoutEditingRules.kt:53-57,35` idem → bloquea edición/voz/`finishUpToCurrentPoint:2276` |
| **6** | MEDIO | Toggle mid-session abandona progreso | `WorkoutSessionHydrator.kt:122,242` tal cual; `WorkoutStepNavigator.kt:689-695` `_L` no cuenta para bilateral; `SessionEditorControls:872-899` OFF borra lados |
| **7** | MEDIO | `ExerciseSet.restBetweenSides` muerto | `Session.kt:362`; grep 0 vivo; `SessionEditorControls:888,894` solo `null`; vivo `SetRecorder:452` usa `restBetweenSidesSeconds` |
| **8** | MEDIO | Sugerencia carga/deuda sin side | `WorkoutLoadSuggestionController.kt:687-696` `plannedWorkingWeightForSet` sin side; `WorkoutV2Body.kt:908-912` sin `side` (voz sí `ViewModel:670`); `WorkoutViewModel:989-1001` `inferPlannedTarget` solo set |
| **9** | MEDIO | Progreso inflado | `WorkoutSetRecorder.kt:481-495` `completedSets.size` (L,R cuentan 2) / `sumOf{sets.size}` (1) |
| **10** | MEDIO | Warmup sin side / anchor vacío | `WorkoutLoadSuggestionController.kt:398-410` `side=null` + `plannedWorkingWeight` null si solo `leftTarget`; `WorkoutStepRules:188-204` warmup sin side |
| **11** | MEDIO | Timer voz ignora duración por lado | `WorkoutVoiceCommandHandler.kt:624` `side=null`, `625-627` `targetDuration/plannedTargetV2` set-level |
| **12** | BAJO | Duplicación `isEffectivelyUnilateral`/`isSetDone` | `WorkoutRoadmapBar.kt:310` privado duplicado; `WorkoutEditingRules:53` vs `ViewModel:1122` |
| **13** | BAJO | Código muerto unilateral editor | `SetEditorUnilateralAndSuperset.kt:20-282` 3 composables + `SetEditorCards.kt:31,69,71` 0 callers |
| **14** | BAJO | Selector solo lee `unilateralMode` | `SessionEditorControls.kt:306` `mode!=BILATERAL` desincronía legacy `isUnilateral=true+BILATERAL` |
| **15** | BAJO | `UNILATERAL_DIFFERENTIAL` inalcanzable, SHARED sin efecto vivo | `Session.kt:292` nunca asignado; `InlineSetRow:165-168` espejo solo editor |
| **16** | BAJO | `addSet` vivo no copia `targetValue` | `WorkoutStructureSheetsHost.kt:762-793` `UnilateralTarget` sin `targetValue=plannedTargetV2` vs editor `Structure.kt:406` sí |
| **17** | BAJO | Mensaje desbalance incoherente | `WorkoutViewModel.kt:1431` `Considera trabajo unilateral.` aunque ya unilateral |
| **18** | BAJO | Sin auto-avance L→R tras `BETWEEN_SIDES` | `WorkoutSetRecorder:543-551` `advanceOnFinish=false` + `Orchestrator:236-260` manual |

**Dependencias:** `#5 isSetDone` → `#4 skip`, `#9 progreso`, `#6 toggle`, `#8 sugerencia` (necesita side correcto) → `#3 volumen` (single vs paired 0.5 vs 1) → `#10 duración` → `#11 voz timer`. `Núcleo #5` debe fijarse primero.

---

## 4. Diseño propuesto

### 4.1 Objetivos
- `isSetDone`, `progress`, `skip`, `sugerencia`, `deuda` coherentes para **single-side** vs **paired** (L+R ≈ 1 bilateral, single-side = 0.5 en vivo vs 1.0 preview a decidir y alinear).
- No borrar `restBetweenSidesSeconds` en superset; no contaminar técnicas entre lados; no crear sets fantasma.
- Volumen/preview y drain por lado coherentes (AUGE ya `×0.5` por lado `AugeFatigueEngine:374`).
- Sin migración Room (claves `_L/_R` son `Map` en `OngoingWorkoutState` JSON), sin tocar `backend` salvo documentar.

### 4.2 No objetivos
- No añadir campos a `WarmupSetDefinition` (hoy sin lado, gap producto documentado).
- No reescribir `WorkoutRoadmapBar` (ya correcta `hasLeftOnly/hasRightOnly:310-325`).
- No cambiar `TimeStrategy` ni `VolumeCalculator` fuera de unilateral.
- No tocar `navigation/Navigation.kt`.

### 4.3 Estrategia por fases (orden prerequisitos)

```
F0 Hotfix crítico (data-integrity, sin migración) → F1 Núcleo #5 (isSetDone single-source) → F2 Contabilidad #3/#9 (AUGE) → F3 Vivo por lado #8/#10/#11 → F4 Limpieza #7/#13/#16
   #1 drops R , #2 restBetweenSides , #4 skip fantasma        #5+#12+#14+#15+#6 (toggle)          volumen/drain/progreso/duración              sugerencia/deuda/warmup/voz                  deprecar campos, código muerto
```
`#5` bloquea `F2/F3`; `F0` independiente (integridad) puede ir paralelo.

---

## 5. Cambios detallados por archivo

### F0 — Hotfix crítico (integridad, 2-3h, sin migración)

**A. `screens/workout/components/SetExecutionCard.kt:794,803,805,843-867,2295,2339,2357` — #1 contaminación**
- Keyear estado por `(exerciseId, setIdx, side)`:
  ```kotlin
  val dropKey = Triple(exercise.id, setIdx, side) // side = pendingSide ?: "B"
  var dropSetEnabled by remember(dropKey) { mutableStateOf(false) }
  var dropSets by remember(dropKey) { mutableStateOf<List<DropSetData>>(emptyList()) }
  // idem restPauseEnabled, restPauses, isAmrap, etc.
  LaunchedEffect(exercise.id, setIdx, side) { /* reset */ }
  ```
- En `commitCapturedRecord` y `selectSide(right)`: `dropSetEnabled=false; dropSets=emptyList(); restPauseEnabled=false` si `side` cambia. No retener `true` tras L.
- Mismo para `WorkoutRoadmapBar`/`ios-native/SetExecutionCard.swift:405,830` (parity).

**B. `screens/sessioneditor/components/ExerciseEditorCard.kt:405-420` — #2 borrado descanso**
- Pasar `sideSeconds = exercise.restBetweenSidesSeconds` siempre (aunque `isUnilateral&&isSuperset`), ocultar UI `CompactRestBundleButton` en superset pero **no** forzar `null` en `onConfirm`:
  ```kotlin
  val sideSeconds = exercise.restBetweenSidesSeconds ?: 0 // no null en superset
  // en onConfirm:
  restBetweenSidesSeconds = if (exercise.isEffectivelyUnilateral()) side?.takeIf { it > 0 } ?: exercise.restBetweenSidesSeconds else null
  ```
- Alternativa: desacoplar `Exercise.restBetweenSidesSeconds` (nivel ejercicio) de `isSuperset` (no tocar). Vivo `WorkoutStepRules:248` y `SetRecorder:452` ya leen solo nivel ejercicio, no por set.

**C. `screens/workout/WorkoutStepNavigator.kt:157-207,225-298` — #4 skip fantasma**
- Reusar `expectedSidesForSet` central (ver F1) o lógica `RoadmapBar:313-325` ya correcta:
  ```kotlin
  fun expectedSidesForSet(exercise: Exercise, set: ExerciseSet): List<String> =
      if (!exercise.isEffectivelyUnilateral()) listOf("B")
      else when {
          set.leftTarget!=null && set.rightTarget==null -> listOf("L")
          set.rightTarget!=null && set.leftTarget==null -> listOf("R")
          set.leftTarget!=null && set.rightTarget!=null -> when (exercise.unilateralSideOrder) { RIGHT_LEFT -> listOf("R","L"); else -> listOf("L","R") }
          else -> listOf("L","R") // ambos lados por defecto (paired)
      }
  fun targetSides = expectedSidesForSet(exercise, set)
  // skipSet: marcar solo targetSides incompletos (no sideOrder solo)
  // stillPendingSide: pending == targetSides.any { !isDoneForSide(...) }
  // skipCurrentSupersetRound: por cada paso, marcar left/right según targetSides del set (no siempre ambos)
  ```
- Añadir guard `if (exercise.isEffectivelyUnilateral() && targetSides.size==1) skipSingleSideOnly`.

### F1 — Núcleo #5 single-source (2-3h, bloquea F2/F3)

**D. Centralizar `expectedSidesForSet` + `completionKeysForSet` (nuevo `domain/workout/UnilateralRules.kt` o `data/models/Session.kt`):**
```kotlin
fun Exercise.expectedSidesForSet(set: ExerciseSet): List<String> { ... arriba ... }
fun completionKeysForSet(exerciseId:String, setIdx:Int, sides:List<String>): List<String> =
    sides.map { s -> if (s=="B") "${exerciseId}_$setIdx" else "${exerciseId}_${setIdx}_$s" }
```
- Usado por: `WorkoutViewModel.isSetDone:1122`, `WorkoutEditingRules:53`, `WorkoutStepNavigator:689-701 isWorkoutStepDone`, `WorkoutLoadSuggestionController:68`, `WorkoutVoiceCommandHandler:609`, `WorkoutSetRecorder:430-435 pendingSide`, `WorkoutV2Body:1193`.

**E. `screens/workout/WorkoutViewModel.kt:1122-1126` + `WorkoutEditingRules.kt:53-57` + `WorkoutStepNavigator.kt:689-701` — #5**
```kotlin
fun isSetDone(completed: Map<String,CompletedSet>, exId:String, setIdx:Int, exercise:Exercise): Boolean {
    val set = exercise.sets.getOrNull(setIdx) ?: return false
    val sides = exercise.expectedSidesForSet(set)
    return sides.all { side ->
        val key = if (side=="B") "${exId}_$setIdx" else "${exId}_${setIdx}_$side"
        completed.containsKey(key) || completed.containsKey("${exId}_${setIdx}_${side.uppercase()}") // compatibilidad legacy
    }
}
```
- `WorkoutEditingRules:35` `if (!isSetDone(...)) return null` ahora distingue single-side (done con 1 key).
- `WorkoutRoadmapBar:310` eliminar duplicado `isEffectivelyUnilateral` y usar `Session.kt:441`.

**F. `screens/sessioneditor/SessionEditorControls.kt:306` — #14 selector**
```kotlin
val isUnilateral = exercise.isEffectivelyUnilateral() // no solo unilateralMode
// si isUnilateral && unilateralMode==BILATERAL → mostrar chip legacy "Bilateral (legacy)" con snackbar
```

**G. `data/models/Session.kt:292` — #15 diferencial**
- `@Deprecated("UNILATERAL_DIFFERENTIAL no usado; usa UNILATERAL_PAIRED")` o eliminar si `grep` iOS/backend confirma 0 uso (`ios-native Session.swift:*`).

**H. `screens/workout/WorkoutSessionHydrator.kt:122,242` + `WorkoutStepNavigator:689` — #6 toggle migración**
- Al hidratar, si `exercise.isEffectivelyUnilateral()` cambió vs claves en `completedSets`, migrar:
  ```kotlin
  // bilateral -> unilateral: "${id}_${idx}" -> "${id}_${idx}_L" (si L es primer lado)
  // unilateral -> bilateral: "${id}_${idx}_L"/"_R" -> "${id}_${idx}" (si ambos, colapsar a _)
  ```
- Requerir `resolveResumePosition` recalcule con `expectedSidesForSet`. Persistir `editingState.side`.

### F2 — Contabilidad #3/#9 (AUGE, 3-4h, flag AUGE)

**I. Definir `expectedSidesForSet` como fuente de `effectiveSetCount`:**
- `domain/training/VolumeCalculator.kt:208-219` `countEffectiveSets`
  ```kotlin
  fun effectiveSetCount(exercise:Exercise, set:ExerciseSet): Int = exercise.expectedSidesForSet(set).size
  // preview usa esto; bilateral 1, paired 2, single 1
  ```
- `screens/sessioneditor/SessionEditorAugeComputation.kt:331-367` y `domain/sessionassistant/SessionAssistantEngine.kt:194-250` ya cuentan 1/set → cambiar a `effectiveSetCount` y ajustar `AugeFatigueEngine:480 sideScale` coherente (single-side 0.5, paired 0.5+0.5=1.0). Decidir producto: **volumen fisiológico = suma L+R (2×)** o **L+R≈1** (preview actual). Auditoría propone L+R≈1 para drain, pero volumen histórico 2× es fisiológico. **Decisión para plan:** mantener log 2×, preview debe mostrar `peso por lado` y `volumen por lado` (no duplicar texto), y `ProgramAnalyticsEngine:634` `unilateralExerciseRatio` ya cuenta ejercicios, no sets — documentar que 2× es esperado. Si se quiere L+R≈1, cambiar `VolumeCalculator` a `*0.5` por lado y `totalVolume` en `WorkoutFinishController:149` a `*0.5` — no recomendado sin datos.
- `WorkoutSetRecorder:481-495` `sessionProgress` → `totalEffectiveSets = exercises.sumOf { ex -> ex.sets.sumOf { expectedSidesForSet(it).size } }` y `completedEffective = completedSets.size` (ya cuenta lados) → progreso coherente 100% (roadmap `RoadmapBar:210` ya usa `completionKeysForSet`).

**J. `domain/auge/AugeFatigueEngine.kt:374-376,556` — drain single-side**
- `sideScale = when (expectedSidesForSet.size) { 1 -> if (sides.contains("B")) 1.0 else 0.5; 2 -> 0.5; else -> 0.5 }` ya 0.5, correcto.

**K. `domain/sessionassistant/TimeCoachEngine.kt:849-852` `effectiveSetCount`**
- `1 + (if (exercise.isEffectivelyUnilateral() && restBetweenSidesSeconds>0) 0.2 else 0)` por descanso entre lados.

### F3 — Vivo por lado #8/#10/#11/#17/#18 (2-3h, flag VOZ)

**L. `WorkoutLoadSuggestionController.kt:687-696` + `WorkoutV2Body.kt:908-912` — #8**
```kotlin
fun plannedWorkingWeightForSet(ex:Exercise, setIdx:Int, side:String?): Double? {
    val set = ex.sets.getOrNull(setIdx) ?: return null
    val sideTarget = if (side=="L") set.leftTarget else if (side=="R") set.rightTarget else null
    return sideTarget?.weight ?: sideTarget?.targetRPE?.let { ... } ?: set.weight // priorizar lado
}
 // V2Body:
 val sideArg = cardSide // "L"/"R"/null disponible 914-917
 val suggestion = remember(exercise, setIdx, sideArg) { controller.plannedWorkingWeightForSet(ex, setIdx, sideArg) }
```

**M. `WorkoutViewModel.kt:989-1001` `inferPlannedTarget/intensity`**
- Añadir `side: String?` param y leer `leftTarget/rightTarget` si no null.

**N. `WorkoutVoiceCommandHandler.kt:618-652` — #11 timer voz**
```kotlin
val pendingSide = ports.getPendingSide() // o expectedSidesForSet.firstOrNull incomplete
val draft = ports.getSetDraft(exercise.id, setIdx, pendingSide)
val targetDuration = exercise.sets[setIdx].let { s ->
    (if (pendingSide=="L") s.leftTarget?.targetDuration else if (pendingSide=="R") s.rightTarget?.targetDuration else null) ?: s.targetDuration ?: s.plannedTargetV2?.toInt()
}
```

**O. `WorkoutLoadSuggestionController.kt:398-410` warmup anchor**
- `getWarmupWorkingWeightAnchor` fallback: `plannedWeight ?: leftTarget?.weight ?: rightTarget?.weight`.

**P. Mensaje desbalance `#17`**
- `WorkoutViewModel.kt:1431` `if (isUnilateral) "Considera ajustar carga por lado" else "Considera trabajo unilateral."`

**Q. Auto-avance `#18` (opcional)**
- `WorkoutSetRecorder.kt:543-551` `startRestTimer(... advanceOnFinish = isBetweenSides)` + `Orchestrator:236-260` `if (advanceOnFinish && completedSidesForSet==1) swipeToOtherSide`.

### F4 — Limpieza #7/#13/#16 (1h)

**R. `data/models/Session.kt:362` `ExerciseSet.restBetweenSides` muerto**
- `@Deprecated("Usa Exercise.restBetweenSidesSeconds")` y dejar `null` en `SessionEditorControls:888,894` (ya hace). Si se elimina, migración Room v20→v21: `KpknDatabase.kt` no tiene tabla sets (JSON), solo borrar campo JSON; no migration SQL pero bump `version = 21` y `autoMigrations`.

**S. `screens/sessioneditor/components/SetEditorUnilateralAndSuperset.kt:20-282`**
- Borrar 3 composables sin refs + `SetEditorCards.kt:31,69,71` (`SetSideMode`, `RemoveSide`, `ToggleLink`) si `grep` confirma 0 callers fuera de `screens/sessioneditor`.

**T. `screens/workout/WorkoutStructureSheetsHost.kt:762-793` — #16**
- Incluir `targetValue = set.plannedTargetV2` en `baseTarget` (añadir `targetValue = lastSet?.plannedTargetV2`).

---

## 6. Impacto por plataforma y banderas

| Plataforma | Impacto | Detalle |
|---|---|---|
| **Android** | **Sí — directo** | `data/models/Session.kt` (helper), `screens/sessioneditor/*` (editor unilateral), `screens/workout/*` (vivo, recorder, navigator, hydrator, finish, voice), `domain/auge/` (drain), `domain/training/` (volumen), `domain/sessionassistant/` (TimeCoach). No toca `navigation/Navigation.kt` salvo quizá `WorkoutV2Body` pager. |
| **iOS** | **Sí — parity** | `ios-native/... Session.swift:517` `isUnilateral`, `WorkoutStepRules.swift:214,228,248`, `WorkoutSetRecorder.swift`, `SetExecutionCard.swift:405,830,1129` contaminación, `VolumeCalculator.swift` parity. Replicar `expectedSidesForSet` Swift. |
| **Backend** | **Bajo** | Solo si `backend/engines/volume.py` o `fatigue_engine.py` recalculan volumen/drain bulk; alinear `sideScale` y `effectiveSetCount`. |

**Banderas:**

| Bandera | Afectada | Valor |
|---|---|---|
| **Room** | **No (F0-F3)** | `completedSets` es `Map<String,CompletedSet>` en `OngoingWorkoutState` JSON (`data/db/` no tabla sets); unilateral fields son JSON en `Session`. Solo F4 si se elimina `restBetweenSides` requiere bump v20→v21 `KpknDatabase.kt` (campo JSON, autoMigration). |
| **AUGE** | **Sí (F2)** | `AugeFatigueEngine` + `VolumeCalculator` + `TimeCoachEngine` mantienen `domain/` puro (`import android.*` prohibido). Parity iOS/backend obligatorio. |
| **Voz** | **Sí (F0#1, F3#11)** | `SetExecutionCard` drops + `WorkoutVoiceCommandHandler` timer + `services/workout/WorkoutVoiceCommandHandler` + `WorkoutRestTimerOrchestrator`. Requiere tests foco voz. |

---

## 7. Pruebas a ejecutar

### 7.1 Unit (JVM, `android-native/`, `testBaseDebugUnitTest`)

- **F0 #1:** `SetExecutionCardUnilateralDropsTest` — `isDropSet` L no contamina R (side key). Mock `SetExecutionCard` state por `(id,idx,side)`.
- **F0 #2:** `ExerciseEditorCardTest.restBetweenSidesPreservedInSuperset` — editar `restTime` en superset no borra `restBetweenSidesSeconds` (assert `30` permanece).
- **F0 #4:** `WorkoutStepNavigatorTest.skipSetSingleSideHasLeftOnly` — `hasLeftOnly` true → skip solo `L`, no crea `_R` fantasma; `skipCurrentSupersetRoundSingleSide`.
- **F1 #5:** `UnilateralRulesTest.expectedSidesForSet_pairedVsSingle` + `WorkoutViewModelTest.isSetDone_singleSide` (L solo con `_L` → done), `WorkoutEditingRulesTest.isSetDone_singleSide_editable`, `WorkoutStepNavigatorTest.isWorkoutStepDone_bilateralAfterUnilateral`.
- **F1 #6:** `WorkoutSessionHydratorTest.toggleMidSession_migration` — `completedSets` `_` → `_L` y viceversa.
- **F2 #3/#9:** `VolumeCalculatorTest.effectiveSetCount_paired2_single1_bilateral1` + `WorkoutSetRecorderTest.sessionProgress_unilateralPaired` (15 sets → 30 effective, progress 100% tras 30), `SessionEditorAugeComputationTest.volumePreviewMatchesLog`.
- **F3 #8:** `WorkoutLoadSuggestionControllerTest.plannedWorkingWeightForSet_leftTarget` — `leftTarget.weight=80` vs `set.weight= null` → `80` con side L.
- **Existentes a reverificar:** `WorkoutStepRulesTest:35-50` (restKind), `SessionEditorControlsTest`, `AugeFatigueEngineTest`.

Comandos:
```bash
gradlew.bat testBaseDebugUnitTest --tests "*UnilateralRulesTest*"
gradlew.bat testBaseDebugUnitTest --tests "*WorkoutStepNavigatorTest*"
gradlew.bat testBaseDebugUnitTest --tests "*WorkoutSetRecorderTest*"
gradlew.bat testBaseDebugUnitTest --tests "*VolumeCalculatorTest*"
gradlew.bat test --tests "*Workout*Unilateral*"
```

### 7.2 Compose / Instrumented
- Editor: toggle unilateral ON/OFF mid-session con 2 lados completados → progreso no abandona (F1#6).
- Vivo: L/R drops independientes (F0#1) con `SetExecutionCard` en unilateral 2 cards.

### 7.3 Manual QA — checklist
1. Unilateral paired drops: `L` con drop, `R` sin drop → logs separados (`WorkoutLog` side L/R).
2. Superset descanso: crear unilateral `restBetweenSides=30`, añadir a superset, editar `restTime` → `30` sigue.
3. Single-side skip: `leftTarget` solo → `skip` 1 tap marca done, 2º tap no crea fantasma, log sin `_R`.
4. Single-side `isSetDone`: `leftTarget` solo + `_L` → editable, no bloqueado.
5. Volumen: 3 ejercicios paired 3 sets → `totalVolume` 18 lados vs preview 9 sets → documentar 2× fisiológico o ajustar.

### 7.4 Build
- `gradlew.bat compileBaseDebugKotlin` (targeted) — verificar wiring no rompe.
- `gradlew.bat assembleBaseDebug --offline` QA final.
- `gradlew.bat testBaseDebugUnitTest` completo si hay tiempo.

---

## 8. Documentación a actualizar

- `docs/audits/2026-08-editor-sesiones/unilateral.md` §1-6 → marcar #1-#5 mitigados con links a `UnilateralRules.kt` y `AugeFatigueEngine:374`.
- `docs/ARCHITECTURE.md` / `docs/ANDROID_ARCHITECTURE_MAP.md`: aclarar `expectedSidesForSet` central + `isSetDone` single-side + progreso `effectiveSets`.
- `docs/ANDROID_UI_SCREENS_MAP.md`: sección SessionEditor unilateral — toggle, sides, restBetweenSides.
- `docs/IOS_PARITY.md` o `docs/paridad/auge-matrix.md`: fila `Unilateral expectedSides — Android parity OK, iOS pending, backend N/A` + `volume 2× fisiológico`.
- `.opencode/kpkn-map.md`: regenerar vía `/map` si se añade `UnilateralRules.kt`.
- `.opencode/memory/MEMORY.md`: anotar decisión single-side vs paired y `#1`/`#2` hotfixes.
- `app/schemas/` no cambia (JSON, no Room bump salvo F4).

> Código y esquema Room v20 son autoridad si docs dicen v19.

---

## 9. Riesgos y mitigaciones

| Riesgo | Prob | Impacto | Mitigación |
|---|---|---|---|
| `isSetDone` single-side rompe logs históricos con 2 keys `_L` y `_R` donde 1 es fantasma de #4 | Media | Histórico inflado | Migración no destructiva: `isSetDone` con `sides.size==1` considera done si **cualquier** `_L/_R` existe (compat), y `skipSet` futuro no crea fantasma. Limpiar logs con script `WorkoutLog` dedupe si `_R` sin `expectedSides`. |
| Volumen 2× decisión producto (suma L+R vs L+R≈1) no consensuada → preview/log divergen de nuevo | Alta | Métrica | Documentar en plan y `VolumeCalculator` comentario que 2× es fisiológico; `TimeCoach` usa `effectiveSets`; no cambiar `totalVolume` sin PO. |
| `restBetweenSidesSeconds` duración 0 vs null semántica (0 = sin descanso, null = no unilateral) | Baja | `BETWEEN_SIDES` no arranca | `expectedSidesForSet` devuelve `[]` si no unilateral; `WorkoutStepRules:248` ya `?:0 >0` check correcto; preservar null vs 0 en editor. |
| `expectedSidesForSet` central olvidado en algún call site (sugerencia, deuda, voz) → deriva | Media | Incoherencia | Buscar global `isEffectivelyUnilateral` y `leftTarget/rightTarget` tras F1 y reemplazar por `expectedSidesForSet`; test `grep` 0 hits. |
| `WorkoutSessionHydrator` migración bilateral↔unilateral con claves `_` vs `_L` pierde `draft` | Media | Resumen incorrecto | Hidratar con `completedSets` tal cual + recalcular `isSetDone` con `exercise` actual; si `exercise` ahora unilateral, `_` se mapea a `L` (primer lado). Test `toggleMidSession_migration`. |
| `SetExecutionCard` side key cambia `rememberSaveable` → pérdida de `dropSetEnabled` al rotar | Baja | UX | Usar `rememberSaveable(dropKey)` con `SAVER` para `List<DropSetData>`; testar rotación. |
| `UNILATERAL_DIFFERENTIAL` aún en JSON histórico → deserialización `Unknown` | Baja | Crash | Mantener enum pero `@Deprecated` y mapear a `UNILATERAL_PAIRED` en `normalizedIdentityFields` (`SessionEditorSessionHelpers:343`). |
| `AugeFatigueEngine` sideScale 0.5 para single-side puede subestimar fatiga vs bilateral | Baja | AUGE | Validar con `OvertrainingDetector` y `SessionIntensityEngine`; si datos reales muestran single-side 0.7, ajustar `sideScale` en F2 con feature flag. |
| iOS `Session.swift:656` descanso entre lados duplicado si no se replica fix #2 | Media | Paridad | Aplicar mismo `if isSuperset { sideSeconds = existing }` en Swift. |

---

## 10. Criterios de aceptación

- [ ] `SetExecutionCard` drops L no contaminan R (side key, test `UnilateralDropsTest` verde).
- [ ] Editar `restTime` en superset no borra `restBetweenSidesSeconds=30` (test `preservedInSuperset` verde).
- [ ] `skipSet` single-side `hasLeftOnly` → solo `_L` skipped, sin `_R` fantasma, sin log fantasma.
- [ ] `isSetDone` single-side con `_L` → done, editable, voz no lo lista pendiente.
- [ ] Toggle mid-session `bilateral→paired` migra `_` → `_L`, `paired→bilateral` colapsa `_L/_R` → `_`.
- [ ] `sessionProgress` unilateral paired 15 sets → 30 effective, 100% tras 30 lados (no 15).
- [ ] `plannedWorkingWeightForSet` con `leftTarget.weight=80` y side `L` → 80 (no `set.weight`).
- [ ] `compileBaseDebugKotlin` + `assembleBaseDebug` verdes; `testBaseDebugUnitTest` con nuevos tests pasa.

---

## 11. Plan de entrega (requiere aprobación)

1. **Aprobación explícita** de este plan (pipeline `request_approval` → `construction`). No editar código hasta `pipeline.start`.
2. `constructor_kpkn` ejecuta por fases en rama corta, commits atómicos por archivo:
   - F0: `SetExecutionCard.kt` (side key) + `ExerciseEditorCard.kt` (restBetweenSides) + `WorkoutStepNavigator.kt` (skip)
   - F1: `Session.kt`/`UnilateralRules.kt` + `WorkoutViewModel.kt` + `WorkoutEditingRules.kt` + `WorkoutStepNavigator.kt`
   - F2: `VolumeCalculator.kt` + `ProgramAnalyticsEngine.kt` + `AugeFatigueEngine.kt` + `TimeCoachEngine.kt` + `WorkoutSetRecorder.kt`
   - F3: `WorkoutLoadSuggestionController.kt` + `WorkoutV2Body.kt` + `WorkoutVoiceCommandHandler.kt`
   - F4: `Session.kt:362` deprecar + `SetEditorUnilateralAndSuperset.kt` borrar + `WorkoutStructureSheetsHost.kt`
3. Añadir tests `UnilateralRulesTest`, `WorkoutStepNavigatorUnilateralTest`, `VolumeCalculatorUnilateralTest`.
4. `gradlew.bat compileBaseDebugKotlin` → `gradlew.bat testBaseDebugUnitTest --tests "*Unilateral*"` → `gradlew.bat assembleBaseDebug --offline`.
5. Auditor revisa diff vs plan; `pipeline submit_audit` → `auditing`.
6. Si auditor pide warmup unilateral, abrir follow-up `docs/audits/unilateral-warmup.md`.

---

## 12. Alternativas descartadas

- **Añadir `leftTarget/rightTarget` a `WarmupSetDefinition`:** no hay UI vivo ni preview; gap producto idéntico editor/vivo, no divergencia — dejar para roadmap.
- **Contar volumen single-side como 0.5:** `AugeFatigueEngine` ya 0.5 para drain, pero volumen histórico 2× es fisiológico (2 lados trabajan); 0.5 subestimaría. Mantener 2× y documentar.
- **Migrar `ExerciseSet.restBetweenSides` a tabla Room:** JSON en `Session`, no tabla; deprecar sin migración v21 es suficiente (bump opcional).
- **Reescribir `WorkoutRoadmapBar` desde cero:** ya correcta `hasLeftOnly/hasRightOnly:310-325` — reusar como referencia, no reescribir.
- **Eliminar `UNILATERAL_DIFFERENTIAL` del enum:** rompería deserialización JSON histórico; mejor `@Deprecated` y mapear a `PAIRED`.

---

## 13. Referencias exactas (para auditor)

- `android-native/app/src/main/java/com/example/kpkn/data/models/Session.kt:248-252,292-294,305-313,360-362,370-376,441-442`
- `android-native/app/src/main/java/com/example/kpkn/screens/sessioneditor/SessionEditorControls.kt:300-347,546-571,848-902`
- `android-native/app/src/main/java/com/example/kpkn/screens/sessioneditor/components/ExerciseEditorCard.kt:405-420,532-585`
- `android-native/app/src/main/java/com/example/kpkn/screens/sessioneditor/SessionEditorViewModelStructure.kt:393-439,402-410`
- `android-native/app/src/main/java/com/example/kpkn/screens/sessioneditor/components/InlineSetRow.kt:160-171,493-501`
- `android-native/app/src/main/java/com/example/kpkn/screens/sessioneditor/components/SetEditorUnilateralAndSuperset.kt:20-282`
- `android-native/app/src/main/java/com/example/kpkn/data/models/WorkoutLog.kt:90-113,136`
- `android-native/app/src/main/java/com/example/kpkn/screens/workout/WorkoutStepRules.kt:188-204,206-255,214,228-236,248-253`
- `android-native/app/src/main/java/com/example/kpkn/screens/workout/WorkoutSetRecorder.kt:129,430-435,445-452,481-495,543-551`
- `android-native/app/src/main/java/com/example/kpkn/screens/workout/WorkoutStepNavigator.kt:157-207,225-298,286-291,689-701`
- `android-native/app/src/main/java/com/example/kpkn/screens/workout/WorkoutV2Body.kt:380-403,908-912,941-942,1193-1223`
- `android-native/app/src/main/java/com/example/kpkn/screens/workout/WorkoutEditingRules.kt:34-57`
- `android-native/app/src/main/java/com/example/kpkn/screens/workout/WorkoutLoadSuggestionController.kt:398-410,658-663,687-696`
- `android-native/app/src/main/java/com/example/kpkn/screens/workout/WorkoutSessionHydrator.kt:122,242`
- `android-native/app/src/main/java/com/example/kpkn/screens/workout/WorkoutFinishController.kt:87-92,149-151`
- `android-native/app/src/main/java/com/example/kpkn/screens/workout/WorkoutVoiceCommandHandler.kt:618-652,593-610`
- `android-native/app/src/main/java/com/example/kpkn/screens/workout/components/SetExecutionCard.kt:684-721,794-824,1129,2295-2304,2339-2359,2529-2585`
- `android-native/app/src/main/java/com/example/kpkn/screens/workout/components/WorkoutRoadmapBar.kt:210-213,310-325`
- `android-native/app/src/main/java/com/example/kpkn/domain/auge/AugeFatigueEngine.kt:374-376,480-483,556`
- `android-native/app/src/main/java/com/example/kpkn/domain/training/VolumeCalculator.kt:208-219,241-283,227-293`
- `android-native/app/src/main/java/com/example/kpkn/domain/sessionassistant/TimeCoachEngine.kt:849-852`
- `android-native/app/src/main/java/com/example/kpkn/domain/exercises/ExerciseMuscleResolver.kt`

---

> **Siguiente paso:** aprobar este plan para pasar a `construction`. No se editará código de producto hasta `pipeline.start` + `request_approval` confirmados. Código y esquema Room v20 son autoridad si docs dicen v19.
