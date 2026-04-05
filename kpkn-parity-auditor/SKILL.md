---
name: kpkn-parity-auditor
description: Repo-specific guidance for auditing real feature parity between the KPKN PWA source and the `android-native` Kotlin/Compose app. Use when Codex must verify whether a feature, route, logic surface, state model, or UX flow has truly been migrated, distinguish native adaptations from missing work, or produce gap reports and cutover evidence without trusting stale parity claims.
---

# KPKN Parity Auditor

Use this skill when the task is about parity, migration coverage, missing features, cutover readiness, regression risk against the PWA, or any request like:

- "audit this slice against the PWA"
- "what is still missing in Kotlin?"
- "is this really migrated?"
- "document the remaining gaps"
- "compare behavior before cutover"
- "verify whether this Android screen preserves the old logic"

This skill is for auditing behavior, not for blindly counting files or trusting old migration summaries. It helps Codex compare the PWA oracle with the current Kotlin target in `android-native`, identify real gaps, and separate intentional Android-native adaptations from unfinished migration work.

## Repo context

KPKN currently has three relevant implementation worlds:

- The PWA root is still the current behavior oracle for many features:
  - `components/`
  - `services/`
  - `stores/`
  - `hooks/`
  - `routes/`
  - `data/`
  - `workers/`
- The current Kotlin target is [../android-native](../android-native).
- `apps/mobile` and many older migration docs are historical context, not proof of Kotlin parity, unless the user explicitly asks about the React Native target.

Important repo documents that may still be useful as hints:

- [../PLAN_MAESTRO_MIGRACION.md](../PLAN_MAESTRO_MIGRACION.md)
- [../implementation_plan.md](../implementation_plan.md)
- [../docs/parity/pwa-rn-master-matrix.md](../docs/parity/pwa-rn-master-matrix.md)
- [../FILES_MANIFEST.md](../FILES_MANIFEST.md)
- [../program-editor-session-workout-migration.md](../program-editor-session-workout-migration.md)

Use them as evidence leads, not as final truth for `android-native`.

Read only the relevant reference file when needed:

- Read [references/parity-sources-map.md](references/parity-sources-map.md) before choosing anchors or trusting old repo claims.
- Read [references/audit-method.md](references/audit-method.md) before classifying status or writing a parity report.
- Read [references/parity-validation-ladder.md](references/parity-validation-ladder.md) before deciding which checks are enough.

## Core doctrine

- Audit behavior and user outcome, not only file presence.
- The PWA remains the temporary oracle for product behavior unless a slice has been deliberately absorbed into Kotlin and validated.
- `android-native` is the current target to audit for migration readiness.
- `apps/mobile` and RN parity matrices are historical context only for Kotlin work.
- Compile success is not parity.
- Intentional native adaptation is valid only when the user outcome, domain meaning, and critical constraints are preserved.
- One accurate narrow audit is better than a broad but speculative migration report.

## What counts as parity in this repo

- Logic parity:
  - same formulas, thresholds, defaults, state transitions, and side effects for the audited slice
- Workflow parity:
  - the same user job can be completed in Android, even if the screen structure is more native
- Data parity:
  - models preserve meaning and persistence behavior, even if storage technology changed
- Native adapter:
  - Android uses different mechanics, but preserves the same feature outcome
- Partial:
  - the target exists, but key tasks, states, or edge cases are missing
- Missing:
  - there is no meaningful Kotlin equivalent yet

Do not demand pixel-for-pixel parity from Compose when the task is really about behavior, structure, or mobile-native flow.

## Current repo reality

- Several parity documents in this repo were written for `apps/mobile` and React Native, not for `android-native`.
- Some manifests, matrices, or file manifests may overstate parity for the current Kotlin target.
- A file-size comparison is a clue, not evidence.
- A legacy feature may be "covered" in old docs while still missing or partial in Kotlin.
- Some PWA surfaces are now legacy or zombie references. Do not port them forward automatically just because they still exist.

Treat every audit as a fresh comparison between:

1. the actual PWA behavior anchor
2. the actual `android-native` implementation
3. the older repo docs as secondary context

## Workflow

1. Define the audit slice.
   - Decide whether the request is about navigation, AUGE, training flow, nutrition AI, persistence, home/dashboard, WikiLab, settings/profile, or platform/build behavior.
   - Decide whether the goal is:
     - `parity-audit`
     - `gap-report`
     - `cutover-check`
     - `behavior-drift-review`
     - `native-adapter-review`
2. Choose the real target.
   - For current Kotlin migration work, the target is almost always `android-native`.
   - Only use `apps/mobile` as the target if the user explicitly asks for React Native.
3. Build the source map.
   - Find the PWA anchors first.
   - Find the Kotlin landing zones second.
   - Use [references/parity-sources-map.md](references/parity-sources-map.md) and `scripts/build_parity_brief.py`.
4. Inspect behavior before structure.
   - Check entry points, public functions, route transitions, derived state, defaults, constants, persisted data, and user-visible outcomes.
   - Do not start with LOC or folder counts.
5. Compare along the right dimensions.
   - logic and formulas
   - state ownership and persistence
   - navigation and workflow completion
   - UI task coverage and information hierarchy
   - side effects and integrations
   - performance-sensitive behavior when relevant
6. Classify each surface deliberately.
   - Use the status taxonomy in [references/audit-method.md](references/audit-method.md).
   - Distinguish `partial`, `missing`, `behavior-drift`, and `native-adapter`.
7. Produce an actionable parity result.
   - Report what is present, what is missing, what differs intentionally, and what still needs verification.
   - Recommend the smallest next step.
8. Validate only as far as the audit requires.
   - Use [references/parity-validation-ladder.md](references/parity-validation-ladder.md) and `scripts/suggest_parity_checks.py`.
   - A docs-only parity audit does not need a full Android build.
   - A parity claim after code changes usually needs at least a Kotlin compile and targeted evidence.

## What to compare in every serious audit

- Public surface:
  - routes, exported functions, viewmodel actions, repository entry points, screens, and visible user tasks
- Models and defaults:
  - field names, enum/state variants, nullability semantics, default values, fallbacks, thresholds
- State and persistence:
  - where state lives, how it survives app restarts, and whether writes/reads are equivalent in meaning
- Workflow completion:
  - can the user finish the same job, not just open a similarly named screen
- UI and UX coverage:
  - information needed to complete the task, native affordances, missing controls, missing states
- Integrations and side effects:
  - AI, notifications, storage, background work, sync, permissions, device bridges
- Known edge cases:
  - empty state, partial state, stale draft, retry/error state, offline fallback, large-history behavior

## Deliverable expectations

When the task is an audit, prefer a compact deliverable with:

- audited slice
- target surface
- PWA anchors
- Kotlin anchors
- status per sub-surface
- evidence used
- intentional native adaptations
- known gaps
- recommended next action

If the task is broader, produce a matrix, but keep it evidence-driven. Do not inflate certainty.

## Risk-aware parity strategy

- Audit the highest-risk domain behavior first:
  - AUGE and readiness
  - program/session/workout execution
  - nutrition parser and persistence
  - navigation and saved state
- Defer low-value visual nitpicks until functional coverage is understood.
- When architecture is imperfect, audit the current flow honestly rather than trying to redesign it during the review.
- If a Kotlin surface is clearly partial, document the gap before proposing refactors.
- If old docs say "full parity" but the code does not support it, believe the code.

## Before editing during a parity task

- Confirm whether the request is audit-only or includes code changes.
- Identify the exact PWA anchor and exact Kotlin target.
- Decide what kind of parity is being tested: logic, workflow, data, UI, or platform integration.
- Define the evidence needed to justify any status claim.
- Use `scripts/build_parity_brief.py` if the surface is non-trivial.

## Before declaring parity

- Verify actual task completion, not naming similarity.
- Verify defaults, fallbacks, and edge-state behavior.
- Verify that any native adaptation still preserves user outcome.
- Check whether older docs are talking about `apps/mobile` rather than `android-native`.
- Prefer "partial" over overclaiming.

## After editing

- Re-check the status classification for the changed slice.
- Run the narrowest validation that supports the new claim.
- Update or note stale parity docs if the change invalidates them.
- Call out any remaining unknowns, especially where the PWA behavior is still split across multiple files.

## Validation

- Use `scripts/build_parity_brief.py` to gather anchors and evidence goals before a deep audit.
- Use `scripts/suggest_parity_checks.py` to pick the smallest useful validation commands.
- Typical Kotlin-side commands:
  - `cd android-native`
  - `.\gradlew.bat :app:compileDebugKotlin`
  - `.\gradlew.bat :app:assembleDebug`
  - `.\gradlew.bat :app:testDebugUnitTest`
- Typical PWA-side reference checks:
  - `npx tsc --noEmit`
  - `npm run test:nutrition-logging`
- Do not run every suite just because the word "parity" appears.
- If the task only documents gaps, a careful code audit may be enough.
- If the task changes logic or storage, widen validation accordingly.

## Do not do these things

- Do not trust old parity matrices over current code.
- Do not declare parity because file names look equivalent.
- Do not treat compile success as proof of behavioral coverage.
- Do not label a major workflow difference as a native adaptation unless the user job is still fully supported.
- Do not port dead PWA behavior forward just to increase checklist counts.
- Do not broaden an audit into a large refactor unless the user asked for implementation.
- Do not compare only UI screenshots when the real risk is in logic or persistence.
- Do not ignore missing edge states, loading states, or recovery paths.

## When to note assumptions

- Note assumptions when the PWA behavior is spread across multiple services and UI layers.
- Note assumptions when older docs point to `apps/mobile` but the task is for `android-native`.
- Note assumptions when a Kotlin feature is present but the persistence or recovery path is still unclear.
- If you proceed without asking, choose the narrowest parity-preserving interpretation and state it.

## Definition of done

- The audit target is clearly defined as PWA vs `android-native`, or explicitly another target if requested.
- Status claims are backed by code inspection or validation evidence, not by repo folklore.
- Intentional native adaptations are distinguished from real missing work.
- The report or fix names the remaining gaps, unknowns, and next step honestly.
- Validation scope matches the risk of the claim being made.
