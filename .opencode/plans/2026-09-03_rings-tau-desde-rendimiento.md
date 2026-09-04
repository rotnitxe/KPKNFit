---
flags: [auge]
---

# Plan: RINGS aprenden del rendimiento (τ desde e1RM/RPE)

Alcance: **Android only**. Sin Room. Un hallazgo → solo τ. No reactivar drain multipliers.

## Rutas

| Fase | Qué |
|------|-----|
| F0–F1 | `PerformanceImpliedBattery` + tests de curva e1RM/RPE |
| F2–F5 | `PerformanceTauLearner`: músculo PRIMARY, Energía (RPE emparejado / CNC), Columna (axial ≥ 0.6) |
| F6 | `AugeViewModel.recompute` aprende una vez por log recién finalizado (≤6 h) |
| F7 | Copy `HomeRingsSection` + evento `auge/tau_from_performance` |

## Impacto

Tras 3+ sesiones del mismo ejercicio con RPE comparable, τ muscular/CNS/espinal puede moverse. El drenaje de **hoy** no cambia. Calibración manual del mismo finish gana ese canal.

## Pruebas

```
testBaseDebugUnitTest --tests com.example.kpkn.domain.auge.PerformanceImpliedBatteryTest --tests com.example.kpkn.domain.auge.PerformanceTauObservationTest --tests com.example.kpkn.domain.auge.AugeAdaptiveEngineTest
```

## Riesgos

Bucle autorregulación mitigado por gate RPE ±1. Variantes con distinto `selectedAspects` no mezclan baseline. Logs viejos al abrir la app no reenseñan (stamp `lastPerformanceLearnLogId`).
