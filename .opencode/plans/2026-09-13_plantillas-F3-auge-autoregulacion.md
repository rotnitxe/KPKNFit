---
flags: [auge]
---

# F3 — Autorregulación AUGE semanal

## Rutas

- Nuevo `domain/training/ProgramAutoregulationEngine.kt`
- `ProgramProgressEngine` gancho al completar la semana
- `PendingProgramActionType.CONFIRM_AUTOREGULATION`
- `Program.autoregulationMode`
- `ProgramRepository.buildTransitionContext` con readiness real

## Impacto

- AUGE propone; usuario confirma. AUTO aplica y notifica.
- `rematerializeWeek` no reescribe semanas ejecutadas.

## Pruebas

- `ProgramAutoregulationEngineTest`, `ProgramProgressEngineTest`, `BlockTransitionEngineTest`, JSON compat de `PendingProgramActionType`

## Riesgos

- WorkoutLog no persiste readiness: se deriva de `AugeRecoveryEngine` + wellbeing de la semana.
