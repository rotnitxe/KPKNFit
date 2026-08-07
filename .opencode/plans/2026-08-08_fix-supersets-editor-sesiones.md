# Plan — Corregir supersets y supersets opcionales (auditoría 2)

**Fecha:** 2026-08-08  
**Autor:** orquestador (muse-spark-1.2)  
**Estado:** `pending_approval` (no editar código de producto hasta aprobación explícita)  
**Auditoría fuente:** `docs/audits/2026-08-editor-sesiones/supersets.md` (204 líneas, solo lectura, 2026-08, alcance `screens/sessioneditor/` + `domain/workout/SupersetRules.kt` + `screens/workout/`)  
**Solicitud:** solucionar TODO lo cubierto por la auditoría 2, paso a paso

---

## 1. Resumen ejecutivo

El vivo deriva los pasos de forma stateless (`WorkoutStepRules.buildSteps` 33-49 via `WorkoutStepNavigator` 89-96 + `visibleExercises` 1510-1516) y el motor `SupersetRules` es compartido editor↔vivo. La arquitectura es sólida; los fallos se concentran en **metadatos no propagados** y **escrituras no transaccionales**:

1. **C1 CRÍTICO — `isOptional` muerto y borrado:** editor guarda `Session.supersetGroups[].isOptional` (`Session.kt:74` + `ViewModelSupersets:280-286` + `SupersetGroupEditorCard:325-408` con promesa *“se muestra solo cuando lo activas”*), vivo lo ignora 100% (`grep screens/workout isOptional = 0`, `domain/workout 0`, `buildSteps 37-45` sin consultar flag) y `SupersetRules.normalizeSession:33-46` lo **omite al reconstruir** (`id, exerciseOrder, restBetween, restAfter, rounds, visualPlacement, roundRest*` sin `isOptional` → default `false`). Se ejecuta en hidratación `WorkoutSessionHydrator:119,234` → `WorkoutViewModel:271,510,1109-1113`, en `createSuperset:179`, `updateRest:277`, `updateRoundRest:304`, `removeExercise:341` y en persistencia vivo `StructuralPersistenceController:316,383-397` → pérdida silenciosa transversal (incluye grupos no tocados). Clones sí preservan (`SessionTemplateEngine:66-72`, `SessionEditorCloneHelpers:68-82` usan `copy`).

2. **C2 CRÍTICO — `rounds < sets` deja sets inalcanzables:** `roundCount 205-210` prioriza `group.rounds`; vivo materializa solo `repeat(rounds)` con `if(roundIdx !in sets.indices) return` (`WorkoutStepRules:118-134`), pero `totalSetsInSession 491-493` cuenta todos los sets (`allExercises.sumOf{sets.size}`) → progreso nunca 100%, AUGE `densityMultiplier 474-480` sesgado. Caminos desync: editor `ScrollRenderer:492-495,616-619,694-698` (`onAddSet/onRemoveSet` sin tocar `rounds`), vivo `WorkoutStructureSheetsHost:332-377` texto libre `take(2)` → `WorkoutViewModel:1834-1849` → `SupersetRules.updateRest:250-294` fija `rounds` sin ajustar sets, creator `SupersetCreatorAndManagerSheets:224` → `createSuperset:100`. Únicos caminos tx: `onAddRound:578-582,706-711` (+1 round + addSet) y `removeSupersetRound:210-237` (quita set + re-indexa maps `:221-227`).

Sin C1 nada es durable: cualquier fix que pase por `normalizeSession` seguirá borrando el flag.

---

## 2. Contexto y reproducción

- **C1 repro:** crear superset opcional en editor (toggle `isOptional=true`), guardar, entrenar la sesión 1 vez (hidratación normaliza) → `isOptional` vuelve a `false` en Room; editar cualquier otra superset (cambiar descanso, quitar miembro, crear otra) → todos los opcionales revierten.
- **C2 repro:** superset A(2 sets) + B(3 sets) con `rounds=2` (grupo creado con 2, luego se añade set a B sin tocar `rounds`, o se edita `rounds` a 1 en vivo por texto libre) → `buildSteps` genera solo 2 rondas → 1 set de B nunca aparece como paso; completar todo lo alcanzable deja `sessionProgress` en `5/6 ≈ 0.83`.
- **A3 repro:** en vivo seleccionar 5 ejercicios y crear superset por UI (≥2) o voz `command.members=5` → dominio `take(4)` corta a 4 sin avisar, handler `WorkoutVoiceCommandHandler:556-559` anuncia *“Superserie creada con A, B, C, D, E”* con 5 nombres, pero solo 4 quedan agrupados.
- **A4 repro:** superset con `restAfter=120`, упражнения con `restTime=90` propio → disolver → `restTime` pasa a `120` (pierde 90); volver a enlazar→disolver no es idempotente.
- **M5:** superset con sets desiguales (A2,B1,C3) → `sameSupersetRound` exige `exerciseId !=` + `roundIdx==targetSetIdx` → en ronda donde B no tiene set, `setRecorder` aplica `SUPERSET_ROUND` en medio del bloque; última ronda `isLastMemberWithSet 122-130` incluye ronda final → `SUPERSET_ROUND` antes de siguiente ejercicio.

---

## 3. Hallazgos verificados (archivo:línea actual, `android-native/app/src/main/java/com/example/kpkn/`)

> Verificación `investigador` 2026-08-08, Room v20 autoridad (`KpknDatabase.kt` v20, `docs/ARCHITECTURE.md:28` dice v19 desactualizado). Rutas absolutas: `android-native/app/src/main/java/com/example/kpkn/...` salvo tests.

| # | Sev | Hallazgo | Evidencia |
|---|---|---|---|
| **C1** | 🔴 CRÍTICO | `isOptional` ignorado en vivo + borrado por `normalizeSession` | `Session.kt:74`; `SupersetGroupEditorCard.kt:325-361,381-384` promesa; `SessionEditorViewModelSupersets.kt:280-286` toggle; `SupersetRules.kt:33-46` omite `isOptional` (copia 8 campos, falta 9º); `WorkoutSessionHydrator.kt:119,234` + `WorkoutViewModel.kt:1109-1114` normaliza B/C/D; `SupersetRules.kt:179,277,304,341`; `WorkoutStructuralPersistenceController.kt:316,383-397` + `canPersistLiveStructuralChanges:390`; clones `SessionTemplateEngine.kt:66-72` ok |
| **C2** | 🔴 CRÍTICO | `rounds < sets` → pasos inexistentes + progreso <100% | `SupersetRules.kt:205-210` `roundCount`; `WorkoutStepRules.kt:118-134` `repeat(rounds)` + `if(roundIdx !in indices) return`; `WorkoutSetRecorder.kt:491-493` `totalSetsInSession`; `SupersetRules.kt:250-294` `updateRest`; `WorkoutStructureSheetsHost.kt:332-377` `roundsText.take(2)`; `WorkoutViewModel.kt:1834-1849` |
| **A3** | 🟠 ALTO | >4 miembros trunca silencioso, feedback engañoso | `SupersetRules.kt:11` `Max=4` + `:90` `take(4)`; `SupersetCreatorAndManagerSheets.kt:122` `enabled <4`; `WorkoutViewModel.kt:1782` cap 4; pero `WorkoutStructureSheetsHost.kt:673` solo `≥2` y `WorkoutVoiceCommandHandler.kt:551-560` anuncia `members.size` completo |
| **A4** | 🟠 ALTO | `dissolve` destruye `restTime` original | `SupersetRules.kt:348-366` `restAfterSuperset → restTime` `:350,358`; test `SupersetRulesTest.kt:172-177` codifica comportamiento destructivo; usado `SessionEditorViewModelSupersets.kt:276-278`, `WorkoutViewModel.kt:1824-1832` |
| **M5** | 🟡 MEDIO | INTRA vs ROUND asimétrico + ROUND tras última ronda | `WorkoutSetRecorder.kt:441-444` `sameSupersetRound`; `WorkoutStepRules.kt:122-130` `isLastMemberWithSet`; `SessionListItems` vs `WorkoutV2Body` expectativa |
| **M6** | 🟡 MEDIO | `moveSupersetGroupToIndex` cosmético | `SessionEditorViewModelSupersets.kt:292-301` reordena `supersetGroups` pero vivo `WorkoutStepRules.kt:37-46` ordena por posición primer miembro en `visibleExercises` |
| **M7** | 🟡 MEDIO | `moveExercise`/`moveExerciseFreely` desarma bracket | `WorkoutViewModel.kt:1851-1869` → `WorkoutStructuralEditor.kt:45-71` + `SessionListItems` bracket por posición |
| **B8** | 🟢 BAJO | Defaults de descanso divergentes por ruta | `SessionEditorViewModelSupersets.kt:22-23` 60/120 hardcode vs `:117-121` `ruleDefaults`; `WorkoutViewModel.kt:466,1752` 60/120; `Session.kt:45-58` legacy 60/120 |

**Dependencias:** `C1` bloquea todo (cualquier `normalize` borra). `C1 → C2+A4` (C2 y A4 pasan por `normalize`). `C2 → M5` (clasificación depende de sets reales). `A3` independiente paralelo. `M6` depende de entender `M7` (reorden real es `moveGroup:368-399`).

---

## 4. Diseño propuesto

### 4.1 Objetivos
- `isOptional` preservado end-to-end (sin pérdida en hidratación/edición/persistencia vivo) y semántica vivo definida (activación bajo demanda o retirar UI).
- `rounds` transaccional con sets: nunca `rounds < max(sets)` sin añadir sets, ni `rounds` ↓ sin quitar sets/re-indexar maps; `buildSteps` y `sessionProgress` coherentes 100%.
- Cap 4 honesto en vivo (UI y voz) y `dissolve` no destructivo.
- Sin migración Room, sin tocar `domain/auge` fórmulas (solo `densityMultiplier` coherente), `services/workout` mínimo.

### 4.2 No objetivos
- No reescribir `WorkoutStepRules.buildSteps` derivado (stateless) ni `SessionEditorAugeComputation`.
- No cambiar Room v20 (supersetGroups es JSON en `Session.supersetGroups`, no entidad).
- No tocar `backend/` fastAPI salvo documentar parity si reusa `roundCount`.

### 4.3 Estrategia por fases (orden prerequisitos)

```
F0 Guard P0 (30m) → F1 C2 transaccional (4-6h) → F2 A3+A4 (2-3h) → F3 M5/M6/M7 (2-3h) → F4 B8 (1h)
 └─ C1 isOptional ──┘   └─ rounds ↔ sets ──┘   └─ cap + dissolve ──┘  └─ clasificación + reorden ──┘
```
`F0` debe ir primero o todo `F1` seguirá borrando.

---

## 5. Cambios detallados por archivo

### F0 — Guard P0 `isOptional` (30 min, bloqueante)

**A. `domain/workout/SupersetRules.kt:33-46` — propagar flag**
```kotlin
// Antes: SupersetGroup(id, exerciseOrder, restBetweenExercises, restAfterSuperset, rounds, visualPlacement, roundRest...)
// Después:
existing?.let {
    SupersetGroup(
        id = it.id,
        exerciseOrder = ...,
        restBetweenExercises = ...,
        restAfterSuperset = ...,
        rounds = ...,
        visualPlacement = ...,
        roundRestBetweenExercises = ...,
        roundRestAfterSuperset = ...,
        isOptional = it.isOptional, // <-- ADD
    )
} ?: ...
// Si se reconstruye por id inexistente, buscar en session.supersetGroups original y copiar isOptional ?: false
```
- También en `createSuperset:80-193` pasar `isOptional = draft.isOptional ?: existing?.isOptional ?: false` (si se crea con `isOptional` en draft).
- Añadir test `normalizeSession_preservesIsOptional` y `hydration_preservesIsOptional`.

**B. `screens/sessioneditor/components/SupersetGroupEditorCard.kt:325-408` — feature-flag**
- Envolver toggle + diálogo informativo en `if (BuildConfig.ENABLE_SUPERSET_OPTIONAL)` o `settings.supersetOptionalEnabled` (default `false` hasta definir semántica vivo). Si `false`, ocultar check `325-361` y mostrar `Chip` “Próximamente” en lugar de promesa `381-384`. Evita prometer activación inexistente.
- Alternativa si se decide implementar vivo: mantener UI y pasar a F0b (ver 5.1).

**C. Vivo — decidir semántica (una de dos, documentar en plan, no ambas):**
- **Opción 1 (recomendada, mínima):** vivo **ignora** `isOptional` pero ya no lo borra. Superset opcional se ejecuta siempre como superset normal (degradación segura). No cambia `WorkoutStepRules`.
- **Opción 2 (completa):** implementar activación bajo demanda — añadir `WorkoutStepRules.buildSteps` rama: si `group.isOptional && sessionState.optionalSupersetEnabled[ groupId ] != true` → emitir pasos como ejercicios separados (no `SupersetStep`), con UI `Chip` “Activar superset”. Requiere `WorkoutViewModel` estado `optionalSupersetEnabled: Map<String,Boolean>` + voz “activar superset”. No incluido en F0; abrir follow-up `docs/audits/supersets-optional-vivo.md`.

**F0 elige Opción 1** (preservar dato + degradación segura) para no bloquear `F1`.

### F1 — C2 `rounds` transaccional (4-6h)

**D. `domain/workout/SupersetRules.kt:250-294` `updateRest(groupId, restBetween, restAfter, rounds)`**
- Si `rounds != null` y `rounds != existing.rounds`:
  ```kotlin
  val maxSets = session.allExercises().filter { it.supersetGroupRefOrLegacyId()==groupId }.maxOfOrNull { it.sets.size } ?: 1
  val targetRounds = rounds.coerceAtLeast(1).coerceAtMost(12) // cap razonable
  if (targetRounds < maxSets) {
      // Opción A (recomendada): auto-añadir sets faltantes hasta targetRounds? No, eso reduciría volumen.
      // Opción B: clamp rounds = maxSets (asegura alcanzabilidad) y avisar.
      // Elegir B: rounds = maxOf(targetRounds, maxSets) // nunca inalcanzable
  }
  // Si targetRounds > maxSets: añadir sets faltantes a cada miembro (inyectar con createNextSetTemplate) o dejar rounds > sets como inocuo pero documentar.
  // Si targetRounds < maxSets: NO permitir; returns con error o clamp.
  // Regenerar roundRest* 0..targetRounds-1 preservando overrides :262-267 ya hace, pero ahora con targetRounds corregido.
  ```
- Misma lógica en `createSuperset:100` (si `rounds < maxSets` de los miembros pasados, clamp) y `updateRoundRest:296-316` no cambia rounds.
- Añadir `validateRounds(session, groupId, rounds): Result` usada por UI.

**E. `screens/sessioneditor/SessionEditorViewModelSupersets.kt:190-198` `updateSupersetRest`**
- Validar `rounds >= maxSets` antes de `SupersetRules.updateRest`; si no, `updateUi { snackbar = "No puedes dejar rondas < sets (${maxSets}). Añade rondas con + o quita sets." }` y no persistir.

**F. `screens/workout/WorkoutStructureSheetsHost.kt:332-379` — campo rondas vivo**
- Validación `onDone`: `val parsed = roundsText.toIntOrNull() ?: return; val maxSets = ...; if(parsed < maxSets) showError("mín $maxSets por sets existentes") else viewModel.updateLiveSupersetRest(rounds=parsed)`. Limitar `take(2)` ya está.

**G. `screens/workout/WorkoutViewModel.kt:1834-1849` `updateLiveSupersetRest`**
- Delegar validación a `SupersetRules.updateRest` (ya clamp) y si clamp ocurrió, emitir `snackbarMessage`.

**H. `screens/workout/WorkoutSetRecorder.kt:491-493` `totalSetsInSession`**
- No cambia (ahora `rounds` nunca < sets, denominador coherente). `densityMultiplier:474-480` ya usa `supersetRounds` correcto.

**I. `screens/sessioneditor/SessionEditorScrollRenderer.kt:492-495,616-619,694-698` `onAddSet/onRemoveSet` por miembro**
- `onAddSet` ya no toca rounds → bien, pero si `maxSets` supera `rounds`, UI debe ofrecer `onAddRound` o auto-bump rounds. Añadir en `SupersetGroupEditorCard:115` `LaunchedEffect(members maxSets, group.rounds) { if(maxSets > (group.rounds ?: maxSets)) viewModel.updateSupersetRest(rounds=maxSets) }` o mostrar warning “Rondas desactualizadas”.

### F2 — A3 cap 4 honesto + A4 dissolve no destructivo (2-3h)

**J. A3 vivo UI `screens/workout/WorkoutStructureSheetsHost.kt:612-681`**
- `items` selección: `val capped = selectedIds.take(4)`; si `selectedIds.size > 4` mostrar `Text("Máximo 4 ejercicios por superset")` y deshabilitar más selección (ya `SupersetCreatorAndManagerSheets.kt:122` hace, replicar).
- Voz: `WorkoutVoiceCommandHandler.kt:551-560` si `command.members.size > 4` → `speak("Solo se agruparon los 4 primeros, ${members.take(4).joinToString()}")` y `createLiveSuperset(members.take(4))`.

**K. A4 `domain/workout/SupersetRules.kt:348-366` `dissolve`**
- Guardar `restTime` original: solo sobrescribir si `exercise.restTime == null` o si `exercise.restTime == group.restAfterSuperset`? Mejor: conservar `restTime` previo:
  ```kotlin
  val preservedRest = exercise.restTime // no tocar
  // o si se quiere mantener post-superset como default solo cuando no había:
  val newRest = exercise.restTime ?: group.restAfterSuperset ?: 90
  ```
- Cambiar `:350,358` a no sobrescribir cuando `exercise.restTime != null && exercise.restTime != group.restAfterSuperset`. Actualizar test `SupersetRulesTest:172-177` (antes esperaba copia, ahora espera preservar). Documentar en `SupersetGroupEditorCard` tooltip.

### F3 — M5/M6/M7 (2-3h)

**L. M5 `screens/workout/WorkoutSetRecorder.kt:441-449` + `WorkoutStepRules.kt:122-130`**
- `sameSupersetRound` ya exige `exerciseId !=`; con sets desiguales, si `nextStepForRest` es otro miembro misma ronda pero ese miembro no tiene set en esa ronda (`roundIdx !in indices`), `WorkoutStepRules` no habría emitido paso para él → `sameSupersetRound` debe verificar `exercise.sets.indices.contains(roundIdx)` antes de clasificar `SUPERSET_INTRA`. Ajustar `restKind` a `SUPERSET_ROUND` solo cuando `targetSetIdx` corresponde a último miembro con set en esa ronda (`isLastMemberWithSet` ya lo hace 122-124). Revisar último set de última ronda: si `isLastMemberWithSet` y es última ronda del bloque, devolver `STANDARD` en lugar de `SUPERSET_ROUND` para no disparar `restAfterSuperset` antes de siguiente ejercicio (evita doble descanso).
- Añadir test `WorkoutStepRulesTest.buildSteps_asymmetricSets_restKind`.

**M. M6 `screens/sessioneditor/SessionEditorViewModelSupersets.kt:292-301`**
- Marcar `@Deprecated("Usa moveSupersetGroupToPart — el orden de grupos no afecta vivo")`, hacer `no-op` con `Log.w` + snackbar “El orden de supersets se define por posición de ejercicios”. Mantener para no romper `MoveSupersetGroupToIndex` callers pero sin efecto.

**N. M7 `domain/workout/WorkoutStructuralEditor.kt:45-71` + `screens/workout/WorkoutViewModel.kt:1851-1869`**
- `moveExerciseById` si `exerciseId` es miembro de superset y `target` cae dentro del mismo bloque pero desordenando, emitir `snackbar` “Mover un miembro suelta el bracket — usa Reordenar superset”. O implementar `moveSupersetBlock` que mueve `exerciseOrder` del grupo y los ejercicios físicamente como bloque (`SupersetRules.moveGroup:368-399`). Misma para `SessionEditorViewModelSupersets.kt:324-353` `moveExerciseFreely`.

### F4 — B8 defaults unificados (1h, opcional)

**O. `screens/sessioneditor/SessionEditorViewModelSupersets.kt:22-23` 60/120 hardcode**
- Extraer `object SupersetDefaults { const val BETWEEN=60; const val AFTER=120 }` o leer `ruleDefaults.supersetBetweenRestSeconds` / `supersetRoundRestSeconds` si `applyToNewItems` o `settings`. Usar mismo en `WorkoutViewModel.kt:466,1752` y `Session.kt:45-58` fallback.

---

## 6. Impacto por plataforma y banderas

| Plataforma | Impacto | Detalle |
|---|---|---|
| **Android** | **Sí — directo** | Solo `domain/workout/SupersetRules.kt` + `data/models/Session.kt` (no Room) + `screens/sessioneditor/*` + `screens/workout/*`. No toca `domain/auge` fórmulas (solo `WorkoutSetRecorder:474` densidad coherente). |
| **iOS** | **Sí — parity** | `ios-native/` si tiene `SupersetRules.swift` y `WorkoutStepRules.swift` replicar F0-F2 (isOptional + roundCount). Dejar nota `docs/IOS_PARITY.md` si aún no existe editor drag. |
| **Backend** | **Nulo** | `backend/` FastAPI análisis opcional no tiene superset logic; solo documentar si reusa `roundCount`. |

**Banderas:**

| Bandera | Afectada | Valor |
|---|---|---|
| **Room** | **No** | `supersetGroups` es JSON en `Session` (`ProgramEntity` + `KpknDatabase.kt` v20); no migración. `SupersetGroup.isOptional` ya en schema JSON, no en tabla. |
| **AUGE** | **Sí (F1)** | `WorkoutSetRecorder.kt:474-480` `densityMultiplier` + `totalSetsInSession 491` + progreso/AUGE coherentes tras fix C2. No cambia `domain/auge` constantes. |
| **Voz** | **Sí (A3, M5)** | `WorkoutVoiceCommandHandler.kt:551-560` cap honesto + `WorkoutRestTimerOrchestrator.kt:111` `restKind` + `WorkoutSetRecorder.kt:445-450`. |

---

## 7. Pruebas a ejecutar

### 7.1 Unit (JVM, `android-native/`, `testBaseDebugUnitTest`)

- **C1:** `SupersetRulesTest.normalizeSession_preservesIsOptional` + `hydration_preservesIsOptional` + `createSuperset_preservesIsOptional` (verificar que `isOptional` sobrevive a `normalizeSession`, `updateRest`, `removeExercise`, `StructuralPersistenceController`).
- **C2:** `SupersetRulesTest.roundsClampToMaxSets` (A2/B1/C3 3,2,3 sets, rounds=2 → clamp 3), `WorkoutStepRulesTest.buildSteps_roundsLessThanSets_never100Percent` (assert pasos incompletos y `totalSets` coherente), `SupersetRulesTest.updateRest_addsSetsWhenIncreasingRounds`, `SupersetRulesTest.removeSupersetRound_reindexesMaps`.
- **A3:** `WorkoutViewModelTest.createLiveSuperset_capsAtFourAndShows honest feedback` (mock `WorkoutStructureSheetsHost` validation).
- **A4:** `SupersetRulesTest.dissolve_preservesOriginalRestTime` (cambiar expectativa `172-177` de copiar `restAfter` a preservar).
- **M5:** `WorkoutStepRulesTest.buildSteps_asymmetricSets_restKindIntraVsRound` + `WorkoutSetRecorderTest.restKind_lastRoundIsStandard` .
- **Existentes a reverificar:** `SupersetRulesTest` (todos), `WorkoutStepRulesTest` (todos), `WorkoutSessionRulesTest` (23 tests), `SessionEditorViewModelSupersetsTest` si existe.

Comandos:
```bash
gradlew.bat testBaseDebugUnitTest --tests "*SupersetRulesTest*"
gradlew.bat testBaseDebugUnitTest --tests "*WorkoutStepRulesTest*"
gradlew.bat testBaseDebugUnitTest --tests "*WorkoutSessionRulesTest*"
gradlew.bat test --tests "*SessionEditor*Superset*"
```

### 7.2 Compose / Instrumented (si hay `androidTest`)
- Editor: crear superset opcional → guardar → reabrir → toggle sigue `true` (no revertido).
- Editor: añadir set a miembro sin tocar `rounds` → `rounds` auto-clamp o warning, `sessionProgress` 100% alcanzable.
- Vivo: crear superset con 5 seleccionados → solo 4 agrupados + snackbar honesto; `dissolve` conserva `restTime` original.

### 7.3 Manual QA — checklist dispositivo
1. Opcional preservado: crear opcional, entrenar 1 vez, volver a editor → sigue opcional; cambiar descanso de otro grupo → sigue opcional.
2. Rounds tx: grupo 3 miembros con 2,3,2 sets → `rounds=2` → intentar guardar con `rounds=1` → bloqueado o clamp a 3; progreso 100% tras completar; `+ Ronda` añade set a cada miembro con ids nuevos.
3. Cap 4: vivo UI seleccionar 5 → 4º bloquea, mensaje; voz “crea superset con A B C D E” → habla “solo 4 agrupados”.
4. Dissolve: superset `restAfter 120`, ejercicio `restTime 90` → disolver → `restTime` sigue 90.
5. Reorden cosmético: `moveSupersetGroupToIndex` → no cambia orden vivo (verificado por posición primer miembro).

### 7.4 Build
- `gradlew.bat compileBaseDebugKotlin` (targeted, `compileDebugKotlin` ambiguo) — verificar wiring no rompe.
- `gradlew.bat assembleBaseDebug --offline` QA final.
- `gradlew.bat testBaseDebugUnitTest` completo si hay tiempo.

---

## 8. Documentación a actualizar

- `docs/audits/2026-08-editor-sesiones/supersets.md` §1-2 → marcar C1/C2 mitigados con links a `SupersetRules.kt:33-46` y `roundCount:205`.
- `docs/ARCHITECTURE.md` / `docs/ANDROID_ARCHITECTURE_MAP.md`: aclarar que `isOptional` es degradación segura (no activación) hasta F0b; `rounds` transaccional con sets.
- `docs/ANDROID_UI_SCREENS_MAP.md`: sección SessionEditor supersets — cap 4, rounds tx, dissolve preserva `restTime`.
- `docs/IOS_PARITY.md` o `docs/paridad/auge-matrix.md`: fila `Superset isOptional + rounds ↔ sets — Android parity OK, iOS pending`.
- `.opencode/kpkn-map.md`: regenerar vía `/map` si se añaden tests.
- `.opencode/memory/MEMORY.md`: anotar decisión C1 degradación segura y C2 clamp.

> Código y esquema Room v20 son autoridad si docs dicen v19.

---

## 9. Riesgos y mitigaciones

| Riesgo | Prob | Impacto | Mitigación |
|---|---|---|---|
| `isOptional` clamp a `false` rompe supersets opcionales ya creados por usuarios (dato perdido histórico) | Media | Pérdida dato ya ocurrida por `normalize` | Migración soft: en `normalizeSession` si `existing.isOptional != null` preservar, si `null` default `false`; no re-escribir JSON histórico sin flag. |
| `rounds` clamp a `maxSets` aumenta volumen sin pedir (añadir sets) vs dejar `rounds>sets` inocuo | Media | UX confuso | Elegir clamp `maxSets` (asegura alcanzabilidad) y mostrar snackbar; no auto-añadir sets silencioso. Test `maxSets=3, rounds=1` → `rounds=3`. |
| `dissolve` preservar `restTime` deja `restTime` viejo que era `restAfter` de superset anterior (heredado) | Baja | Descanso inconsistente | Solo preservar si `exercise.restTime != null && exercise.restTime != group.restAfterSuperset`; si `null` usar `group.restAfterSuperset` como antes. |
| Vivo cap 4 honesto requiere cambiar `WorkoutVoiceCommandHandler` y `WorkoutStructureSheetsHost` strings — i18n | Baja | Texto | Usar strings en `res/values` ya existentes; no hardcode inglés. |
| `updateRest` con `rounds` clamp re-indexa `roundRest*` maps `0..rounds-1` — overrides huérfanos | Baja | Descanso por ronda perdido | Ya regenera `:262-267` preservando overrides `0..min(old,new)-1`; test `reindexesMaps`. |
| `moveSupersetGroupToIndex` deprecated rompe callers que esperaban reorden visual | Baja | UI | Mantener no-op con `Log.w` + snackbar, no crash; migrar callers a `moveSupersetGroupToPart`. |
| Tests `SupersetRulesTest:172` esperan `restAfter→restTime` → fallarán tras fix A4 | Alta | CI rojo | Actualizar test en mismo commit con nuevo comportamiento + comentario. |
| Thread safety `weeklyMetricsCache` no aplica (esta auditoría no toca AUGE cache) | — | — | No aplica. |

---

## 10. Criterios de aceptación

- [ ] `isOptional` sobrevive a `normalizeSession`, hidratación, `createSuperset`, `updateRest`, `removeExercise`, `StructuralPersistenceController` (test `preservesIsOptional` verde) y UI oculta promesa si flag off.
- [ ] `rounds` nunca `< max(sets)` sin clamp/error; `buildSteps` con `rounds=3` y miembros 2,3,2 sets genera pasos para todos los sets (3 rondas) y `sessionProgress` llega a 1.0.
- [ ] Vivo UI con 5 seleccionados → solo 4 agrupados + mensaje honesto; voz anuncia recorte.
- [ ] `dissolve` conserva `restTime` original cuando existía (test `preservesOriginalRestTime` verde).
- [ ] `moveSupersetGroupToIndex` no reordena vivo (deprecado, log + snackbar).
- [ ] `compileBaseDebugKotlin` + `assembleBaseDebug` verdes; `testBaseDebugUnitTest` con nuevos tests pasa; `Perfetto` no aplica (no perf).
- [ ] Docs `ANDROID_UI_SCREENS_MAP.md` y `MEMORY.md` actualizados.

---

## 11. Plan de entrega (requiere aprobación)

1. **Aprobación explícita** de este plan (pipeline `request_approval` → `construction`). No editar código hasta `pipeline.start`.
2. `constructor_kpkn` ejecuta en rama corta, commits atómicos por archivo:
   - `domain/workout/SupersetRules.kt` (isOptional + rounds clamp + dissolve + tests)
   - `screens/sessioneditor/SessionEditorViewModelSupersets.kt` + `screens/sessioneditor/components/SupersetGroupEditorCard.kt`
   - `screens/workout/WorkoutStructureSheetsHost.kt` + `WorkoutViewModel.kt` + `WorkoutVoiceCommandHandler.kt`
   - `screens/workout/WorkoutSetRecorder.kt` + `WorkoutStepRules.kt` (M5) + `WorkoutStructuralEditor.kt`
3. Añadir `SupersetRulesTest` + `WorkoutStepRulesTest` casos.
4. `gradlew.bat compileBaseDebugKotlin` → `gradlew.bat testBaseDebugUnitTest --tests "*Superset*"` → `gradlew.bat assembleBaseDebug`.
5. Auditor revisa diff vs plan; `pipeline submit_audit` → `auditing`.
6. Si se decide F0b (activación opcional vivo), abrir follow-up `docs/audits/supersets-optional-vivo.md`.

---

## 12. Alternativas descartadas

- **Eliminar `isOptional` del modelo y migrar JSON:** borraría dato de usuarios que ya lo usan (pérdida irreversible); mejor preservar + degradación segura.
- **Auto-añadir sets al subir `rounds` silenciosamente:** `createSuperset` con `rounds=3` y miembros de 2 sets añadiría 1 set por miembro sin pedir — mejor clamp `maxSets` y dejar `rounds>sets` inocuo (paso inexistente salta) o añadir con `onAddRound` explícito.
- **Reescribir `buildSteps` para emitir superset de 1 miembro como STANDARD:** ya es stateless y correcto; solo documentar que superset visible de 1 es degenerado por skips temporales, no persistido.
- **Migrar Room para `isOptional` a columna:** innecesario, `supersetGroups` es JSON en `Session` (`exercises` + `supersetGroups` serializados), no entidad `SupersetGroupEntity`.

---

## 13. Referencias exactas (para auditor)

- `android-native/app/src/main/java/com/example/kpkn/data/models/Session.kt:34,64-75`
- `android-native/app/src/main/java/com/example/kpkn/domain/workout/SupersetRules.kt:11,13-77,80-193,195-210,212-248,250-294,296-316,318-346,348-366,368-399`
- `android-native/app/src/main/java/com/example/kpkn/screens/sessioneditor/SessionEditorViewModelSupersets.kt:22-23,53-59,117-121,165-188,190-208,210-237,239-245,276-278,280-286,292-301,324-353`
- `android-native/app/src/main/java/com/example/kpkn/screens/sessioneditor/components/SupersetGroupEditorCard.kt:114-115,183,292-311,325-361,370-408`
- `android-native/app/src/main/java/com/example/kpkn/screens/sessioneditor/components/sheets/SupersetCreatorAndManagerSheets.kt:122,224-232,381-386`
- `android-native/app/src/main/java/com/example/kpkn/screens/sessioneditor/SessionEditorScrollRenderer.kt:226-283,492-495,578-582,694-698`
- `android-native/app/src/main/java/com/example/kpkn/screens/sessioneditor/SessionListItems.kt`
- `android-native/app/src/main/java/com/example/kpkn/screens/workout/WorkoutStepRules.kt:33-49,94-97,118-134,206-255`
- `android-native/app/src/main/java/com/example/kpkn/screens/workout/WorkoutStepNavigator.kt:89-96,155-190,431-466`
- `android-native/app/src/main/java/com/example/kpkn/screens/workout/WorkoutViewModel.kt:271,510,1109-1114,1510-1516,1752-1772,1774-1816,1824-1832,1834-1849,1851-1869`
- `android-native/app/src/main/java/com/example/kpkn/screens/workout/WorkoutSessionHydrator.kt:118-120,234`
- `android-native/app/src/main/java/com/example/kpkn/screens/workout/WorkoutSetRecorder.kt:430-493,474-480`
- `android-native/app/src/main/java/com/example/kpkn/screens/workout/WorkoutStructuralPersistenceController.kt:309-397,390`
- `android-native/app/src/main/java/com/example/kpkn/screens/workout/WorkoutStructureSheetsHost.kt:208,332-379,612-681`
- `android-native/app/src/main/java/com/example/kpkn/screens/workout/WorkoutVoiceCommandHandler.kt:551-560,618-652`
- `android-native/app/src/main/java/com/example/kpkn/domain/workout/WorkoutStructuralEditor.kt:45-71,164-187`
- `android-native/app/src/main/java/com/example/kpkn/data/models/Session.kt:37-58` legacy fallback 60/120
- Tests: `app/src/test/java/com/example/kpkn/domain/workout/SupersetRulesTest.kt:72-77,172-177,204-247`, `WorkoutStepRulesTest.kt:89-136`, `WorkoutSessionRulesTest.kt`

---

> **Siguiente paso:** aprobar este plan para pasar a `construction`. No se editará código de producto hasta `pipeline.start` + `request_approval` confirmados. Código y esquema Room v20 son autoridad si docs dicen v19.
