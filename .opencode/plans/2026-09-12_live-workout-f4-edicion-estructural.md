---
flags: [room]
---

# F4 — Edición estructural en vivo y persistencia al plan

Depende de F0 (remap/poda), F1 (`archivedCompletedExercises` campo), D3 y D6. Cierra: **EST-03, EST-04, EST-05, EST-06, EST-07, EST-08, EST-09, EST-10, EST-11, EST-12, EST-13, EST-14, EST-15, EST-17, EST-18** (EST-16/19 P2-P3).

## Objetivo

Mutar la sesión live no corrompe logs. “Guardar en el plan” hace replay quirúrgico, no volcado del live. God Mode undo restaura de verdad. Ultra Fast preview ≠ apply. Relator no aplica densify a escondidas.

## Rutas

| Ítem | Archivos |
|------|----------|
| Replace + archivo | `WorkoutStructuralPersistenceController.kt:641-745, 591-619`, `WorkoutViewModel.replaceExercise` |
| Replay vs dump | mismo controller `:100-180, 406-419, 113-126, 169-172`, `applySessionMutation` `persistToProgram` default |
| Scopes D6 | `sanitizeLiveEditPersistenceScope` `:1221-1237`, `WorkoutStructureSheetsHost.kt` copy botones `:1410-1531` |
| Modo B | `applyAddSupersetToProgramSession`, `applyReorderAndPromptPersistence` `WorkoutViewModel.kt:2729` |
| God Mode | `WorkoutViewModel.captureGodModeUndoSnapshot` ~3497, `revertGodModeChange` ~3511, `revertPlanAspect`, `WorkoutRoadmapBar.kt:129-130`, `GodModeUndoRulesTest` |
| Ultra Fast | `WorkoutViewModel.kt` apply/revert ~2898-2951, `UltraFastSheet.kt`, `performRelatorAssist` PREVIEW_ULTRAFAST |
| Relator | `WorkoutViewModel.performRelatorAssist` ~3264-3395, `addMobilityExerciseToSession` |
| SS live | `SupersetRules.kt`, `createLiveSuperset` preferred cursor |
| Stubs | `persistExerciseChangesToPlan/Block` `WorkoutViewModel.kt:3130-3131` — **borrar** |

## Impacto

### EST-03 + D3 — Replace archiva, no hereda

Tras F0 el prune borraría las series del id (replace conserva `Exercise.id`). En replace:

1. **Antes** de mutar, snapshot: `CompletedExercise` del original (identidad catálogo + `completedSets` de ese id) → `archivedCompletedExercises`.
2. Limpiar claves live del id (el sucesor parte de 0).
3. Limpiar también `warmupCompleted*`, `omittedSetKeys`, `skippedExerciseIds` de ese id (hoy se heredan).
4. Finish concatena `archivedCompletedExercises` + live `toCompletedExercises` (F2).

No atribuir volumen al ejercicio nuevo.

### EST-04 — Identidad V2 capturada **antes** del replace

`PendingReplacementPersistencePrompt` ya tiene `sourceExerciseDbId`. Añadir `fromCatalogRevision/Definition/Configuration` **en el prompt** en el momento del picker, no leerlos del ejercicio ya sustituido (`commitPendingReplacementPersistence:606-618`).

`toExerciseDbId`: `catalogConfigurationId ?: resolvedCanonical`, no `replacement.id` crudo si es id de catálogo distinto del canónico.

### EST-05 / EST-10 — Default `persistToProgram = true`

`applySessionMutation(..., persistToProgram = true)` vuelca live en cíclico simple y no-op en COMPLEX.

Canon:

- Toda mutación live llama `persistToProgram = false` + `pendingStructuralPersistence` (ya lo hacen add/remove set). Extender a: `addCatalogExerciseToLiveSuperset`, `removeExerciseFromLiveSuperset`, `updateLiveSupersetRest`, `updateExerciseDefinition` (quitar el default true).
- `persistSessionToProgram` **no** se llama desde `applySessionMutation`.
- Borrar stubs vacíos `persistExerciseChangesToPlan/Block`.

### EST-06 / EST-07 — Replay quirúrgico (D6)

Prohibido `persist(liveSession)` en Dissolve/AddExercise/Reorder.

- **AddExercise:** insertar **plantilla** (catálogo / `newExerciseTemplate` del pending), no el live con pesos reales. Si el pending ya tiene `newExerciseTemplate`, usarlo; si no, clonar el live **stripping** `CompletedSet`/pesos ejecutados → dejar `ExerciseSet.weight` planificado.
- **Dissolve:** `SupersetRules.dissolve` sobre la sesión **del programa** (unwrapped del modo), no copiar live. Rest: ya hay test `dissolve_copiesRoundRestAsIndividualRest`; aplicarlo al programa, no al dump.
- **AddSuperset de miembros existentes** (`createLiveSuperset` `newExerciseIds = targetIds`): el abort `:419` se reemplaza por `SupersetRules.createSuperset` sobre ids que **ya** están. Solo insertar plantillas para ids nuevos (`addExercisesAsLiveSuperset`).
- Copy “Guardar permanente” → “Guardar en esta semana” (D6).

### EST-08 — Modo B sin wrapper anidado

`applyAddSupersetToProgramSession` recibe ya `modeSession` unwrapped; **no** volver a `withModeSession`.

`applyReorderAndPromptPersistence`: claves canónicas desde `sessionForActiveMode`, no `withModeSession { it }` que en B devuelve el wrapper A.

### EST-09 — Scopes visibles

UI solo SESSION_ONLY / PERMANENT (semana) / BLOCK_MATCHING. Quitar etiqueta mesociclo. `MESOCYCLE_MATCHING` branch → eliminar o alias interno a SESSION_ONLY sin copy.

Reemplazo PERMANENT: copy distinta “en todo el programa”.

### EST-11 / EST-12 — God Mode undo real

Snapshot **completo**:

```kotlin
GodModeUndoSnapshot(
  label, session, skipped, omitted,
  currentExerciseIdx, currentSetIdx, activeStepKey,
  completedSets, setDrafts, manualLoadOverrides,  // nuevos
)
```

`revertGodModeChange(stackIndex)` restaura **ese** snapshot (no `diffSessionPlan` por índice de aspecto). Truncar stack con `godModeUndoStackAfterRevert` (el helper existe y el VM no lo usa).

`WorkoutRoadmapBar`: invocar `onRevertGodModeAction` desde el board/banner (hoy el lambda se pasa y nunca se llama). Back de God Mode expandido → compact (F7 también).

`revertPlanAspect` (diff): si se mantiene para “aspectos del plan”, debe remapear `completedSets` con F0 o delegar al snapshot. No dos undos contradictorios. Preferir **solo** stack de snapshots en vivo; el diff puede quedar para mostrar la lista de cambios.

### EST-13 — Ultra Fast

- `previewUltraFast()` ≠ `applyUltraFast()`. Relator `PREVIEW_ULTRAFAST` llama preview + sheet, no apply.
- `customSetCounts` del sheet llegan a `apply`.
- `override == false` **excluye** aislamientos.
- Apply: snapshot `completedSets` + session (ya hay `ultraFastSnapshot` de session). Recortar sets **sin** cambiar UUID de los que sobreviven; densificar crea ids nuevos **después** de los logueados. Remap F0 poda índices recortados (series hechas fuera del take se archivan o se drop — canon: **no densificar ni recortar índices ya logueados**; el engine respeta `setFullyCompleted`).
- Revert: restaura session snapshot **y** `completedSets` snapshot.

### EST-14 — Cursor de superserie

`createLiveSuperset` / join: `preferredExerciseId` = ejercicio **actual** si es miembro; si no, primer incompleto del grupo, no `targetIds.first()`.

Rounds = max sets (ya). No pad (documentado). Cap 4 se queda.

### EST-15 — Modo A/B

Al `setActiveMode`, filtrar mapas a ids del modo destino **o** namespaced `completedSets` por modo. Finish ya usa `sessionForActiveMode` (series del otro modo no salen). Canon: al cambiar de modo, no borrar el mapa del modo origen (guardar `completedSetsByMode` o prefijar). Mínimo viable: no compartir ids (el editor ya clona B con ids nuevos); si `sessionB == null` y B muestra A, **bloquear** el switch o copiar explícitamente. Test con B materializado.

### EST-17 — Relator

| Acción | Contrato |
|--------|----------|
| PREVIEW_ULTRAFAST | preview + sheet |
| MOVE_EXERCISE_END / CONVERT_DROPSETS / HALVE_SETS | `persistToProgram=false`, `pushGodModeUndo`, remap F0 |
| ADD_MOBILITY | `withModeSession`, no persistir programa sin prompt |
| `addMobilityExerciseToSession` | igual |

### EST-18

No cablear `SetPropagationRules` en vivo salvo que el add-set ya copie la última serie (hoy lo hace). No reindex al añadir al final (F0 cubre).

## Pruebas

| Test | Qué |
|------|-----|
| `WorkoutStructuralPersistenceReplayTest` | AddSuperset de ids existentes escribe grupo en programa; no no-op |
| mismo | Dissolve no persiste pesos live |
| `ReplaceExerciseArchiveTest` | series originales en archive; sucesor 0; finish las incluye |
| `ReplacementPromptIdentityTest` | fromCatalog* del original |
| `GodModeUndoRulesTest` | VM restaura completedSets; helper usado |
| `UltraFastLiveTest` | preview no muta; apply no recorta índices logueados; revert restaura maps |
| `RelatorAssistPreviewTest` | PREVIEW no llama apply |

```bash
./gradlew --no-daemon --console=plain testBaseDebugUnitTest --tests '*Structural*' --tests '*GodMode*' --tests '*UltraFast*' --tests '*Replacement*' --tests '*RelatorAssist*' --tests '*SessionPlanAspect*'
```

## Riesgos

- Replay mal hecho deja el programa sin el cambio que la UI prometió (EST-07 hoy). Tests de “sí escribe el grupo” son la guarda.
- Archivar replace aumenta el log: AUGE verá el original + el sucesor. Correcto (D3).
- `completedSetsByMode` es el cambio más delicado de F4; si B siempre tiene ids nuevos, el mínimo (bloquear B-sin-materializar) basta.

## Fuera de alcance

Session editor drag, generación de semanas futuras de `ExerciseReplacementDecisionV2` más allá de la escritura, iOS.
