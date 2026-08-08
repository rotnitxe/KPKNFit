# Plan 01 — Integridad temporal: loops, calendarización, fechas clave y modelo simple/avanzado

> **Auditoría origen:** `00-auditoria-program-detail.md` · **Hallazgos:** PD-01, PD-06, PD-07, PD-15 · **Esfuerzo estimado:** M (3–5 días) · **Riesgo si no se hace:** loops desfasados, “semana actual” distinta entre Home y Program Detail, fechas clave que no afectan calendario real.

## Objetivo

Dejar el **tiempo del programa** con una sola fuente de verdad y un ciclo de vida de loops determinista, sin duplicar lógica de “qué semana toca hoy” en 4 sitios distintos.

## Alcance y no-objetivos

- **Sí:** `Program` (Simple CYCLIC/CALENDARIZED vs Avanzado multi-bloque), `LoopEngine`/`LoopOccurrence`/`LoopState`, `ProgramCalendarEngine.project`, `ProgramProgressEngine.resolveCurrentWeekInstances`, `ProgramActiveStateEngine.repairForProgram`, `HomeSessionResolver`, `ProgramKeyDateEngine`, `ProgramMigrationEngine`, `schedulePlan` vs `timelineStartDate`/`calendarization`.
- **No:** generación de contenido de sesiones (Plan 02), UI de `MacrocycleEditor` (Plan 03), persistencia Room detallada (Plan 03).

## Hallazgos que motiva (resumen)

- **PD-01** triplicado `loops` + `loopState` + `loopOccurrences` con `syncOccurrences` no siempre invocado.
- **PD-06** loops en programa calendarizado sin política (omitir/posponer/recuperar).
- **PD-07** resolución de semana actual en 4 motores con lógicas parecidas pero no idénticas.
- **PD-15** `schedulePlan` coexiste con `timelineStartDate`/`calendarization`; caminos leen uno u otro.

## Tareas

### T1 — Centralizar mutación de loops bajo `LoopEngine` (P1)
- **Archivos:** `domain/training/LoopEngine.kt:72-346`, `data/models/Program.kt:25-26,55`, `screens/programdetail/components/LoopsView.kt`, `MacrocycleEditor.kt`
- **Hacer:**
  - Exponer `upsertLoop/deleteLoop/postpone/cancelOccurrence/cancelRule` como **única** vía para tocar `loops/loopState`; que cada una retorne `Program` ya reconciliado con `syncOccurrences` + `materializeLoopWeeks` cuando aplique.
  - Eliminar cualquier `program.copy(loops = ...)` directo en UI; reemplazar por llamadas al engine.
  - Añadir `LoopEngine.validate(program): List<LoopIssue>` y usarlo en `validateTemporalStructure`.
- **Aceptación:**
  - Test `LoopTriplicationTest` falla si se muta `loops` sin regenerar `loopOccurrences`.
  - `LoopsView` y `MacrocycleEditor` compilan sin `copy(loops` directo.

### T2 — Política loops en `CALENDARIZED` + `CalendarBreak` (P1)
- **Archivos:** `Program.kt:43,63,326-332,449-470`, `ProgramKeyDateEngine.kt`, `ProgramMigrationEngine.kt:67-73`
- **Hacer:**
  - Definir y documentar política: “loops PAUSADOS durante break fechado; al `restorePausedCyclicProgram` se recalcula `currentCycle` sin contar ciclos de break” (o alternativa explícita).
  - Implementar `resolveLoopWeekInstancesForCycle` que ignore ciclos dentro de `CalendarBreak`.
  - Test `LoopRuntimeIntegrationTest` con break de 3 semanas que solape un `repeatEvery=4`.
- **Aceptación:** historia de usuario “creo break de 4 semanas, mi deload cada 4 no se dispara dentro del break y al volver cae en el ciclo correcto” pasa.

### T3 — Unificar resolución de “semana actual” (P2)
- **Archivos:** `ProgramProgressEngine.kt:39-84`, `ProgramCalendarEngine.kt:79-89,190-236`, `ProgramActiveStateEngine.kt:11-74`, `HomeSessionResolver.kt:80-244`
- **Hacer:**
  - Nuevo `ProgramCurrentWeekResolver` con 3 vistas: `cyclicInstances(program, cycle)`, `datedProjection(program)`, `todayItem(program, activeState, history, today, ongoing)`.
  - Migrar `ProgramProgressEngine.resolveCurrentWeekInstances` y `HomeSessionResolver.resolveWeekLocation` a delegar ahí; mantener wrappers `@Deprecated` una versión.
  - Test cruzado: mismo `Program` + mismo `today` → `currentWeekId` idéntico en Detail y Home.
- **Aceptación:** no hay divergencia en `currentWeekId` entre `ProgramDetailViewModel.currentWeeks` y `HomeViewModel.todaySessions`.

### T4 — Unificar `schedulePlan` como SSoT temporal (P2)
- **Archivos:** `ProgramScheduleModels.kt:12-20`, `Program.kt:43,52,684-694`, `ProgramCalendarEngine.kt:70-77`
- **Hacer:**
  - `resolvedSchedulePlan()` pasa a ser canónico; `timelineStartDate` y `calendarization` se derivan (getters) o se migran en `ProgramMigrationEngine`.
  - Auditar todos `LocalDate.parse(raw)` sin `try/catch` (p.ej. `nextSimpleCalendarStart:691`) y envolver con `parseIsoDate` + `AppClock`.
  - Migración que copie `timelineStartDate → schedulePlan.anchorDate` si `schedulePlan.anchorDate` es null.
- **Aceptación:** ningún `Program` persistido queda con `schedulePlan.anchorDate != timelineStartDate` cuando ambos no-null.

## Validación

```powershell
powershell -NoProfile -File .opencode/scripts/run-gradle.ps1 -Tasks "testBaseDebugUnitTest --tests '*.LoopEngineTest' --tests '*.LoopOccurrenceOperationalTest' --tests '*.LoopRuntimeIntegrationTest' --tests '*.ProgramCalendarEngineTest' --tests '*.ProgramProgressEngineTest' --tests '*.ProgramKeyDateEngineTest' --tests '*.ProgramActiveStateEngineTest' --tests '*.HomeSessionResolverTest'"
powershell -NoProfile -File .opencode/scripts/run-gradle.ps1 -Tasks "testBaseDebugUnitTest --tests '*.ProgramStructureContractTest' --tests '*.ProgramMigrationEngineTest'"
```

## Riesgos y mitigación

- **iOS parity:** replicar política de loops en `ios-native/KPKNFit/Domain/Training/` tras validar en Android.
- **Datos existentes:** snapshot `pausedCyclicSnapshot` debe seguir deserializando; añadir defaults, no romper JSON viejo (ver `KpknDatabase` v20).

## Métricas de éxito

- 0 `program.copy(loops` fuera de `LoopEngine`.
- 0 divergencias `currentWeekId` en test cruzado (100 programas sintéticos).
- Cobertura loops ≥ 90% ramas (postpone/cancel/excluded).
