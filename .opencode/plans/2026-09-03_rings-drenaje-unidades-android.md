---
flags: [auge]
---

# Plan RINGS: residuales Android (B9–B17)

Fuente: auditoría `rings-drenaje-recuperacion` (2026-09-03). Alcance: **solo Android**.

## Objetivo

Unificar unidades V2 (`stressUnits`), filtrar timestamps inválidos, eliminar sueño fantasma 7.5 h, alinear TTC `hoursToRecovery` con decay, y corregir overtraining copy/detector — **sin** recalibrar curvas de drenaje.

## Rutas

| Fase | Archivos |
|------|----------|
| F0 | `domain/auge/*Test.kt` (nuevos tests caracterización) |
| F1 | `AugeRecoveryEngine.kt`, `AugeMuscleCapacityEngine.kt` |
| F2 | `MuscularSessionImpactEngine.kt` |
| F3 | `AugeRecoveryEngine.kt`, `AugeMuscleCapacityEngine.kt`, `AugeViewModel.kt` |
| F4 | `WorkoutSessionOverlaysHost.kt`, `AugeModels.kt`, `AugeViewModel.kt`, dashboard copy |
| F5 | `AugeTtcEngine.kt` |
| F6 | `OvertrainingDetector.kt`, `VolumeView.kt` |
| F7 | `docs/audits/`, `backend/engines/adaptive_engine.py` (docstring) |

## Impacto

- ACWR/capacidad: solo logs V2 con `stressUnits`; legacy `sessionStressScore` excluido.
- Unilateral L+R ≈ bilateral en impacto V2.
- Readiness ya no reinyecta `sleepHours=7.5`.
- TTC UI usa `k = 2/recoveryHours` (48 o 60 h).
- Program detail: copy coherente con Home (“Posible exceso de volumen”).
- Sin migración Room. iOS sin cambios.

## Pruebas

```bash
powershell -NoProfile -File .opencode/scripts/run-gradle.ps1 -Tasks "testBaseDebugUnitTest --tests '*Auge*' --tests '*Overtraining*' --tests '*ExerciseReadiness*' --tests '*CardioRing*'"
```

Pins que no deben romperse: `AugeRingDrainRealismTest`, `OvernightRecoverySensationTest`.

## Riesgos

- ACWR `null` más frecuente hasta ≥4 logs V2 en 14+ días (preferible a deloads erróneos).
- Unilateral: test L+R vs bilateral es guarda contra doble penalización.
- Fallback capacidad puede mover Columna en historiales mixtos — verificar `AugeRecoveryEngineRingTest`.

## Fuera de alcance

iOS, backend runtime, recalibrar sigmoidal/caps, ExerciseReadinessEngine weights, captura DOMS 24 h.
