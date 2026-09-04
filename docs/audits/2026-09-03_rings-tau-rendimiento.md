# RINGS: τ desde rendimiento (2026-09-03)

## Qué

AUGE ya invertía sensación→τ. Ahora, al finalizar una sesión reciente (≤6 h), infiere una batería implícita del e1RM/RPE frente al anillo **de entrada** (historial sin el log de hoy) y alimenta el mismo EMA.

## Contratos

- Un canal tocado a mano en ese finish no aprende de rendimiento.
- RPE de hoy ≥ histórico − 1 (deload / kilos bajados por readiness no enseñan).
- ≥3 sesiones previas del mismo `canonicalId` + mismos `selectedAspects`.
- `|implied − predicted| ≥ 8`.
- Horas desde el estímulo previo en `[8, 14×24]`.
- Curva e1RM: `clamp(round(100 − (1 − ratio)×400), 35, 98)`.
- Energía: RPE a misma carga (±2.5% peso, ±2 reps) o fallback CNC ≥ 4.
- Columna: solo `axialLoadFactor ≥ 0.6`.

## Archivos

- `domain/auge/PerformanceImpliedBattery.kt`
- `domain/auge/PerformanceTauLearner.kt`
- `screens/auge/AugeViewModel.kt` (`learnFromLatestFinishedLog`)
- `AugeAdaptiveCache.lastPerformanceLearnLogId`
