# Auditoría 1/5 — Modelos temporales y motores de dominio puro

> **Pista:** Loops, fechas clave, calendarización, progresión cíclica, programa simple/avanzado · **Severidades:** P0 corrupción/crash, P1 funcionalidad rota, P2 rendimiento/mantenibilidad, MEJORA oportunidad

## Resumen ejecutivo

El sistema temporal distingue bien **Simple CYCLIC vs CALENDARIZED vs Avanzado multi-bloque** y congela el ciclo con `pausedCyclicSnapshot` (modelo sólido). El riesgo principal es **consistencia**: 4 resolutores de “qué semana toca hoy” y el **triple estado de loops** (`loops[]` + `loopState` + `loopOccurrences[]`) sin `syncOccurrences` en todos los caminos. `LoopEngine` ya existe como reconciliador canónico — falta blindarlo como única vía de mutación. `schedulePlan` compite con `timelineStartDate`/`calendarization`.

## Tabla de hallazgos

| ID | Sev | Título | Archivo:línea |
|---|---|---|---|
| M-01 | P1 | Triple estado loops: `loops`+`loopState`+`loopOccurrences` desfasable | `Program.kt:25-26,55` `ProgramScheduleModels.kt:49-63` `LoopEngine.kt:48-346` `LoopsView.kt` |
| M-02 | P1 | Loops no modelados en programa calendarizado / `CalendarBreak` | `Program.kt:43,63,326-332` `ProgramMigrationEngine.kt:67-73` `ProgramKeyDateEngine.kt` |
| M-03 | P2 | 4 resolutores de “semana actual” con lógicas parecidas no idénticas | `ProgramProgressEngine.kt:39-84` `ProgramCalendarEngine.kt:79-89` `ProgramActiveStateEngine.kt:11-74` `HomeSessionResolver.kt:80-244` |
| M-04 | P2 | `schedulePlan` vs `timelineStartDate`/`calendarization` compiten; `LocalDate.parse` sin try/catch en `nextSimpleCalendarStart:691` | `ProgramScheduleModels.kt:12-20` `Program.kt:684-694` `ProgramCalendarEngine.kt:70-77` |
| M-05 | P2 | `validateTemporalStructure` detecta `CALENDARIZED_WITH_LOOPS` solo como diagnóstico, no barrera | `Program.kt:327-332` |
| M-06 | MEJORA | Unificar `ProgramCurrentWeekResolver` + política explícita de loops pausados en break | `ProgramProgressEngine.kt` + `HomeSessionResolver.kt` |

## Hallazgos detallados

### M-01 — Triple estado loops (P1)
**Evidencia:** `Program.loops`, `Program.loopState` (`currentCycle`, `cancelled`, `postponed`, `cancelledOccurrences`) y `Program.loopOccurrences[]` coexisten. `LoopEngine.syncOccurrences` es la reconciliación canónica; `ProgramMigrationEngine.kt:75-82` y `ProgramProgressEngine.kt:73-84` la invocan, pero `LoopsView`/`MacrocycleEditor` pueden hacer `copy(loops=...)` sin regenerar `loopOccurrences`.

**Impacto:** ciclo / badge “en N días” incorrecto, loop que no dispara tras ser borrado hasta próximo sync.

**Dirección:** centralizar en `LoopEngine.upsertLoop/deleteLoop/postpone/cancelOccurrence` como única vía, añadir `LoopEngine.validate` y test `LoopTriplicationTest`.

### M-02 — Loops en calendarizado (P1)
**Evidencia:** `SimpleProgramKind.CALENDARIZED` limpia `loops/loopState/loopOccurrences` en `alignTemporalMetadata:380-382`, pero no define qué pasa con un deload que cae dentro del break. `ProgramProgressEngine.quoteNextCycle` no compensa ciclos “perdidos”.

**Dirección:** definir política “loops pausados durante break; al `restorePausedCyclicProgram` recalcular `currentCycle` sin contar ciclos de break” + test con break 3 semanas que solape `repeatEvery=4`.

### M-03 — Semana actual duplicada (P2)
Duplicación entre `ProgramProgressEngine.resolveCurrentWeekInstances`, `ProgramCalendarEngine.project/scheduledDateFor`, `ProgramActiveStateEngine.repairForProgram`, `HomeSessionResolver.resolveWeekLocation`. Mejora en uno no se propaga.

**Dirección:** nuevo `ProgramCurrentWeekResolver` con 3 vistas (instancias cíclicas / proyección fechada / today) + test cruzado Home vs Detail.

### M-04 — SSoT temporal
`resolvedSchedulePlan()` intenta unificar, pero hay caminos que leen `timelineStartDate` directo y otros `schedulePlan.anchorDate`. `nextSimpleCalendarStart:691` hace `LocalDate.parse` sin `try/catch`.

**Dirección:** elegir `schedulePlan` como SSoT, migrar `timelineStartDate→anchorDate` si null, envolver parse con `parseIsoDate` + `AppClock`.

## Cobertura tests y gaps
Existentes: `LoopEngineTest`, `LoopOccurrenceOperationalTest`, `LoopRuntimeIntegrationTest`, `ProgramCalendarEngineTest`, `ProgramProgressEngineTest`, `ProgramKeyDateEngineTest`, `ProgramActiveStateEngineTest`, `ProgramMigrationEngineTest`, `ProgramHierarchyIndexTest`. Gaps: `LoopTriplicationTest`, break+loop cada 4, test cruzado Home/Detail mismo `today`.

## Preguntas abiertas
1. ¿Loops dentro de `CalendarBreak` se omiten, posponen o recalibran al volver?
2. ¿Tamaño máximo aceptable del JSON `Program` antes de normalizar?
