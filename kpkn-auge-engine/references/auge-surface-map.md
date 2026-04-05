# AUGE Surface Map

Use this file to locate the right source and target ownership before editing AUGE behavior.

## Public surface

| Surface | Current source | Current Kotlin target | Notes |
|---|---|---|---|
| Public AUGE facade | `services/auge.ts` | no single facade file yet | Start here to see the official PWA surface. |
| Set drain / effective RPE / predicted session drain / completed session stress | `services/fatigueService.ts` | `domain/auge/AugeFatigueEngine.kt` | High-risk behavior surface. |
| Muscle battery / systemic fatigue / readiness / global batteries / sleep recommendations | `services/recoveryService.ts` | `domain/auge/AugeRecoveryEngine.kt` | Main recovery/readiness port. |
| Volume thresholds, muscle normalization, unified muscle volume | `services/volumeCalculator.ts` | `domain/training/VolumeCalculator.kt` plus adjacent Kotlin models | AUGE-adjacent; changes here can drift AUGE outcomes. |
| Async compute wrappers | `services/computeWorkerService.ts` | coroutine/background execution patterns | Preserve behavior, not worker mechanics. |
| Wellbeing, sleep, feedback, pending questionnaires | PWA stores/services | `data/repository/AugeRepository.kt` | Keep storage separate from engine math. |
| Battery/readiness/result models | PWA `types.ts` + service return shapes | `data/models/AugeModels.kt` | Prefer explicit Kotlin models over ad hoc maps. |
| UI orchestration | PWA hooks/components | `screens/auge/AugeViewModel.kt` | Recompute and expose state only. |

## Practical ownership rules

- Start any audit from `services/auge.ts` to discover the intended public API.
- When a PWA AUGE function wraps `@kpkn/shared-domain`, inspect the wrapper carefully for KPKN-specific glue and fallback behavior.
- Put formulas, thresholds, decays, and battery math in `domain/auge/`.
- Put Room/DataStore persistence and retrieval in `data/repository/`.
- Put screen state exposure in `AugeViewModel`, not engine rules.

## Current repo-specific risk notes

- `AugeViewModel.kt` currently recomputes with `exerciseDb = emptyMap()`. That means any Kotlin result depending on exercise metadata can be behaviorally incomplete even if the engine code compiles.
- The PWA exposes more AUGE-related public functions than the current Kotlin engine surface. Use `scripts/compare_auge_surface.py` to inspect the gap quickly.
- Articular, tendon, and structural readiness extensions exist on the PWA side and should not be accidentally collapsed into muscular or spinal batteries.
