---
name: kpkn-auge-engine
description: Repo-specific guidance for migrating, auditing, validating, and extending the KPKN AUGE engine across the PWA source and the `android-native` Kotlin target. Use when Codex works on readiness, fatigue, recovery, muscle batteries, spinal/articular systems, AUGE-derived training logic, or parity analysis between the web AUGE services and the native Kotlin engine.
---

# KPKN AUGE Engine

Use this skill for the most behavior-sensitive part of KPKN: AUGE. Treat the PWA as the current behavior oracle for AUGE logic and `android-native` as the final product target.

## Repo context

- The current AUGE facade in the PWA is [../services/auge.ts](../services/auge.ts).
- Most heavy logic currently lives in:
  - [../services/fatigueService.ts](../services/fatigueService.ts)
  - [../services/recoveryService.ts](../services/recoveryService.ts)
  - [../services/volumeCalculator.ts](../services/volumeCalculator.ts)
  - [../services/computeWorkerService.ts](../services/computeWorkerService.ts)
- The current Kotlin landing zones are:
  - [../android-native/app/src/main/java/com/example/kpkn/domain/auge/AugeFatigueEngine.kt](../android-native/app/src/main/java/com/example/kpkn/domain/auge/AugeFatigueEngine.kt)
  - [../android-native/app/src/main/java/com/example/kpkn/domain/auge/AugeRecoveryEngine.kt](../android-native/app/src/main/java/com/example/kpkn/domain/auge/AugeRecoveryEngine.kt)
  - [../android-native/app/src/main/java/com/example/kpkn/data/models/AugeModels.kt](../android-native/app/src/main/java/com/example/kpkn/data/models/AugeModels.kt)
  - [../android-native/app/src/main/java/com/example/kpkn/data/repository/AugeRepository.kt](../android-native/app/src/main/java/com/example/kpkn/data/repository/AugeRepository.kt)
  - [../android-native/app/src/main/java/com/example/kpkn/screens/auge/AugeViewModel.kt](../android-native/app/src/main/java/com/example/kpkn/screens/auge/AugeViewModel.kt)
- Related training-volume logic also touches [../android-native/app/src/main/java/com/example/kpkn/domain/training/VolumeCalculator.kt](../android-native/app/src/main/java/com/example/kpkn/domain/training/VolumeCalculator.kt).

Read only the relevant reference file when needed:
- Read [references/auge-surface-map.md](references/auge-surface-map.md) before choosing where a port should land.
- Read [references/auge-invariants.md](references/auge-invariants.md) before changing formulas, thresholds, or recovery/readiness behavior.
- Read [references/auge-validation-ladder.md](references/auge-validation-ladder.md) before deciding validation scope or claiming parity.

## Doctrine

- Preserve AUGE behavior before polishing AUGE presentation.
- Preserve formulas, thresholds, decay curves, recovery windows, and battery semantics unless the task explicitly changes them.
- Translate platform mechanics, not domain meaning.
- Keep AUGE logic in domain/repository layers, not in Compose UI or view glue.
- Prefer small, testable parity-preserving steps over broad cleanup.

## What AUGE is in KPKN

AUGE is not a single function. It is a coordinated engine covering:

- set-level fatigue and drain
- per-exercise metrics and heuristics
- session stress and predicted drain
- per-muscle battery recovery
- global batteries: muscular, CNS/CNC, spinal
- readiness verdicts
- sleep and stress modulation
- nutrition influence on recovery
- post-session feedback loops
- pending questionnaires
- articular/tendon/structural extensions

If a task touches any of those behaviors, use this skill.

## Current source of truth strategy

- PWA remains the current behavior oracle for AUGE.
- Kotlin is the future product source of truth once a slice is validated and absorbed.
- Do not infer AUGE behavior from the UI alone.
- Start from the engine files and only then inspect the screens/components consuming them.
- When PWA behavior and current Kotlin behavior diverge, note the divergence explicitly and decide whether the task is:
  - parity restoration
  - intentional native adaptation
  - deliberate algorithm change

## Workflow

1. Locate the exact AUGE slice.
   - Determine whether the task is set-level fatigue, session stress, muscle recovery, global batteries, readiness, articular systems, storage, or UI consumption.
2. Inspect the canonical PWA path first.
   - Use [references/auge-surface-map.md](references/auge-surface-map.md) to find the right source file.
   - If the PWA service delegates to `@kpkn/shared-domain`, inspect the PWA wrapper carefully and preserve its KPKN-specific glue, not just the shared call.
3. Inspect the Kotlin landing zone second.
   - Find the corresponding engine/model/repository/viewmodel code in `android-native`.
   - Check whether the Kotlin side already has a partial implementation or gap.
4. Classify the task.
   - `parity-audit`
   - `logic-port`
   - `gap-fill`
   - `behavior-fix`
   - `ui-consumer-fix`
5. Preserve invariants first.
   - Read [references/auge-invariants.md](references/auge-invariants.md) before touching formulas or thresholds.
   - Never "simplify" AUGE math casually.
6. Edit the smallest coherent slice.
   - Prefer one engine path or one derived behavior at a time.
   - Avoid mixing formula changes with UI refactors.
7. Validate with the narrowest useful check.
   - Use [references/auge-validation-ladder.md](references/auge-validation-ladder.md).
   - If the task changes behavior, produce parity evidence, not just a compile.

## Surface ownership

- Use the PWA facade [../services/auge.ts](../services/auge.ts) to discover the official public surface.
- Use [../services/fatigueService.ts](../services/fatigueService.ts) for set drain, stress, and predicted session behavior.
- Use [../services/recoveryService.ts](../services/recoveryService.ts) for muscle batteries, systemic fatigue, global batteries, readiness, and sleep recommendations.
- Use [../services/volumeCalculator.ts](../services/volumeCalculator.ts) when AUGE behavior depends on volume heuristics, muscle normalization, or volume thresholds.
- Use Kotlin `domain/auge/` for pure engines.
- Use Kotlin `data/models/AugeModels.kt` for typed battery/readiness/result models.
- Use Kotlin `data/repository/AugeRepository.kt` for persistence of wellbeing, sleep, feedback, and pending questionnaire state.
- Use Kotlin `screens/auge/AugeViewModel.kt` only as orchestration; do not move engine math there.

## AUGE porting order

When porting or fixing a non-trivial AUGE slice, prefer this order:

1. models and enums
2. constants, thresholds, and helper normalization
3. set-level drain logic
4. session-level aggregation
5. muscle-level recovery
6. global batteries and readiness
7. repository/viewmodel integration
8. UI consumers

Do not start with the cards or dashboards when the underlying score is uncertain.

## Review stance

- Treat every numeric literal as suspicious until you know which invariant it belongs to.
- Look for behavior drift, not just syntax drift.
- Distinguish intentionally simplified Kotlin code from incomplete Kotlin code.
- Review defaults and fallbacks carefully; AUGE often hides major behavior there.
- Check whether the target is using real exercise metadata or placeholder/empty maps before trusting results.

## Known risk patterns in this repo

- Kotlin currently has partial AUGE coverage, not full surface parity with the PWA facade.
- `AugeViewModel` currently recomputes with `exerciseDb = emptyMap()`, which can materially affect exercise-aware calculations and recovery/fatigue behavior.
- The PWA uses worker-backed async wrappers for heavy compute. Kotlin uses coroutine/background execution patterns instead. Preserve behavior, not worker API shape.
- The PWA wraps some `@kpkn/shared-domain` logic with KPKN-specific glue. Do not assume the shared-domain call alone is enough.
- Volume and muscle-normalization logic can leak into AUGE behavior indirectly. Keep an eye on `volumeCalculator.ts` and canonical muscle mapping.

## Before editing

- Identify the exact public behavior being changed.
- Identify which PWA function defines that behavior today.
- Identify the corresponding Kotlin landing zone or confirm it is missing.
- List the invariants involved.
- Decide what parity evidence will prove the change is correct.

## Before changing formulas or thresholds

- Read [references/auge-invariants.md](references/auge-invariants.md).
- Verify whether the number lives in fatigue, recovery, readiness, or volume logic.
- Check whether the source behavior depends on weighted sleep windows, decay curves, adaptive cache, DOMS/stress caps, or athlete-type floors.
- Avoid touching multiple scoring systems in one pass unless the task explicitly needs that.

## Before changing repository or viewmodel glue

- Confirm whether the issue is really persistence/orchestration and not engine logic.
- Keep `AugeRepository` focused on storage, not calculations.
- Keep `AugeViewModel` focused on recomputation and state exposure, not formula ownership.
- Widen validation if the change affects saved wellbeing, sleep logs, feedbacks, or pending questionnaire lifecycle.

## Before changing UI consumers

- Verify the score or battery is already correct in the engine.
- Keep presentation labels, colors, and guidance consistent with engine outputs.
- Do not patch a wrong AUGE score in the UI layer.
- If the UI needs additional breakdowns, derive them from engine output or add clear engine-side models rather than embedding math in Compose.

## Validation

- Prefer targeted engine validation before app-level validation.
- Typical target-side commands:
  - `cd android-native && .\gradlew.bat :app:testDebugUnitTest`
  - `cd android-native && .\gradlew.bat :app:assembleDebug`
- Typical source-side checks when parity matters:
  - `npx tsc --noEmit`
  - source spot-checks against representative PWA scenarios
- Use `scripts/compare_auge_surface.py` to inspect public-surface drift between the PWA facade and Kotlin engine files.
- Use `scripts/suggest_auge_checks.py` to pick the narrowest useful validation based on touched files.
- Read [references/auge-validation-ladder.md](references/auge-validation-ladder.md) before escalating to broader checks.

## Do not do these things

- Do not change AUGE numbers because the UI "feels off" without tracing the source formula.
- Do not move AUGE math into `AugeViewModel`, screens, cards, or composables.
- Do not trust Kotlin parity if the target still uses placeholder or missing exercise metadata.
- Do not collapse muscular, CNS, spinal, and articular concepts into one generic score.
- Do not treat the PWA worker wrappers as the algorithm itself.
- Do not rewrite fatigue, readiness, and recovery together during a small bug fix unless the bug spans them.
- Do not replace nuanced thresholds with rounded simplifications for convenience.
- Do not claim AUGE parity from compile success alone.

## When to note assumptions

- Note assumptions when a Kotlin implementation intentionally simplifies a PWA behavior.
- Note assumptions when the PWA facade re-exports behavior from multiple files and it is unclear which layer owns the invariant.
- Note assumptions when the target lacks required metadata such as exercise DB mappings, adaptive cache, or nutrition coupling.
- If you proceed without asking, choose the most local parity-preserving option and state it clearly.

## Definition of done

- The affected AUGE slice preserves the intended KPKN behavior or documents an intentional change.
- Engine ownership remains in `domain/auge/` or clearly justified repository code.
- Numeric thresholds, decay curves, and status mappings have been treated deliberately.
- Validation includes behavior evidence when the task changes AUGE outputs.
- Any remaining parity gaps or target limitations are called out explicitly.
