# Auditoría RINGS residuales Android — 2026-09-03

## Veredicto

Android (`android-native/domain/auge/`) es **canónico**. Backend FastAPI y iOS divergen; no son runtime de la app móvil.

## Cerrado en este ciclo (B9–B17)

| ID | Fix |
|----|-----|
| B9 | Fallback capacidad suma `stressUnits`, no `immediateDrainPct` |
| B10 | ACWR solo logs V2; excluye `sessionStressScore` legacy |
| B11 | Unilateral `accumulatedSets += 0.5` en `MuscularSessionImpactEngine` |
| B12 | Filtra `logDateMs <= 0`; τ learning cap 14 días |
| B13 | Sin `sleepHours ?: 7.5` en readiness save; dashboard null = sin dato |
| B14 | Contrato pin: time-only sin `cardioDetails` no drena (by-design) |
| B15 | Contrato pin: blend readiness en tests, pesos sin cambio |
| B16 | `hoursToRecovery` usa mismo `k = 2/recoveryHours` que decay (48/60) |
| B17 | `weeksCount` por span de fechas; copy sin “crónico” |

## No reabierto (decisiones Aug 29)

- Curvas sigmoidal, caps 32/32/35, `SESSION_DECAY_K`, tanks ATHLETE_CAPACITY
- Sleep AUGE, feedback 24 h, sliders Settings
- Paridad iOS / backend fórmulas

## Referencias

- Plan: `.opencode/plans/2026-09-03_rings-drenaje-unidades-android.md`
- Auditoría fuente: `.commandcode/plans/rings-drenaje-recuperacion-auditoria.md`
- Backend: `backend/engines/adaptive_engine.py` marcado no canónico en docstring
