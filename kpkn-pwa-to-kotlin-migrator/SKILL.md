---
name: kpkn-pwa-to-kotlin-migrator
description: Repo-specific migration guidance for translating KPKN Fit PWA features, logic, UX flows, data models, and UI modules into the native Kotlin/Compose app in `android-native/`. Use when Codex is migrating, porting, adapting, or validating behavior from the web codebase into Android and must preserve product intent and critical logic while avoiding literal web-to-native cloning.
---

# KPKN PWA to Kotlin Migrator

Use this skill when the task is not just "rewrite TypeScript in Kotlin", but migrate KPKN behavior into a native Android shape that fits Compose, Android navigation, Kotlin models, Room/DataStore, and mobile-first UX.

## Repo context

- Source product surface lives mainly in the PWA root: `components/`, `services/`, `stores/`, `hooks/`, `workers/`, `routes/`, `data/`, and `contexts/`.
- Native target lives in [../android-native](../android-native) with Gradle Kotlin DSL, Compose, Material 3, Navigation Compose, Room, KSP, and Kotlin serialization.
- The Android codebase already has layered landing zones:
  - `data/` for persistence, models, repositories, static catalogs, and device-facing integrations
  - `domain/` for calculations, AUGE, nutrition, biomechanics, and training logic
  - `screens/` for feature entry points and screen-specific UI
  - `ui/` for shared Compose theme/components
  - `navigation/` for routes and flow wiring
- Treat the existing migration docs as live repo knowledge:
  - [../PLAN_MAESTRO_MIGRACION.md](../PLAN_MAESTRO_MIGRACION.md)
  - [../program-editor-session-workout-migration.md](../program-editor-session-workout-migration.md)
  - [../PORT_SUMMARY.md](../PORT_SUMMARY.md)
  - [../WIDGET_MIGRATION_SUMMARY.md](../WIDGET_MIGRATION_SUMMARY.md)

Read the relevant reference file only when needed:
- Read [references/translation-principles.md](references/translation-principles.md) before touching UI/UX-heavy ports.
- Read [references/migration-slice-checklist.md](references/migration-slice-checklist.md) when planning a feature slice or phased migration.
- Read [references/parity-and-cutover.md](references/parity-and-cutover.md) when deciding validation scope, parity level, or release readiness.

## Core doctrine: translate, do not clone

- Preserve user outcome, product intent, core math, domain behavior, and critical flows.
- Translate interaction patterns into native Android/Compose forms instead of reproducing web scaffolding.
- Preserve information hierarchy, not literal layout geometry.
- Preserve logic invariants, not framework-specific implementation details.
- Prefer Android-native navigation, gestures, sheets, cards, scaffolds, and state ownership.
- Reject ports that feel like a web page trapped inside Compose.

## What must stay equivalent

- AUGE and training math
- nutrition parsing and plan logic when applicable
- program/session/workout semantics
- data model meaning and persistence behavior
- validation rules and business constraints
- Spanish product language, unless the task explicitly changes copy
- high-value flows such as onboarding, session editing, workout execution, program creation, logging, and readiness/recovery insights

## What should usually be adapted

- desktop-leaning layouts into thumb-friendly mobile flows
- sidebars into top bars, segmented views, tabs, or sheets
- drawers/modals into `ModalBottomSheet`, dialogs, or dedicated screens
- hover/tooltips into visible affordances, supporting text, or long-press only when justified
- dense data tables into cards, grouped sections, steppers, or progressive disclosure
- CSS motion and transitions into restrained Compose animations
- sprawling single-screen web workflows into small screen-sized navigation steps when clarity improves

## Workflow

1. Inspect the source feature before editing.
   - Find the PWA entry component, supporting services, stores/hooks, routes, data files, and any worker usage.
   - Identify what is product-critical versus merely presentational.
   - Identify whether the source includes logic worth extracting first.
2. Inspect the native target before designing the port.
   - Find the closest Android feature package, screen, repository, domain class, model, and navigation route.
   - Reuse existing Android-native patterns already present in `android-native`.
3. Classify the migration slice.
   - `logic-first`: formulas, derivations, mapping, parser behavior, scheduling, readiness/fatigue, volume, analytics
   - `workflow-first`: onboarding, wizard, editor, active session, multi-step flow
   - `ui-translation`: dashboards, cards, visual summaries, detail views
   - `integration-first`: storage, sync, AI bridge, notifications, background work, assets
4. Decide what to preserve exactly and what to reinterpret.
   - Write a short migration brief before editing when the slice is non-trivial.
   - Use `scripts/build_migration_brief.py` to structure that brief quickly.
5. Port logic before polishing UI when the feature contains domain behavior.
   - Move formulas and state transformations into `domain/` or `data/repository/` first.
   - Keep composables thin and driven by Kotlin models/state.
6. Translate the experience into Android form.
   - Recompose flow structure to fit smaller screens and Android interaction patterns.
   - Prefer native affordances over literal visual parity.
7. Validate the slice at the smallest useful scope.
   - Compile, unit test, and manually review the specific Android path.
   - Preserve parity where required and document intentional deviations.

## KPKN source-to-target mapping

- `services/*.ts`
  - Pure calculations and decision engines usually land in `android-native/.../domain/`
  - persistence/network/device orchestration usually lands in `android-native/.../data/repository/`
- `stores/*.ts`, `contexts/*.tsx`, `hooks/*.ts`
  - usually translate into repository-backed `ViewModel` state, `StateFlow`, Room/DataStore, or targeted Kotlin helpers
  - do not recreate React-style hooks just to mirror the source
- `components/**/*.tsx`
  - screen-level flows usually land in `screens/<feature>/`
  - reusable native UI belongs in `ui/` or a feature `components/` package under `screens/`
- `routes/`, `navigation`, view switching
  - translate into `navigation/Navigation.kt` routes and explicit screen transitions
- `workers/computeWorker.ts`
  - translate into domain-side async/coroutine-friendly execution, batching, background work, or a documented main-thread fallback
- `data/*.ts`
  - translate into Kotlin objects, JSON assets, Room seed data, or repository-managed catalogs depending on usage

## UI and UX translation rules

- Start from the user job, not the web layout.
- Prefer a strong mobile primary action and smaller supporting actions.
- Group long forms into steps if that reduces cognitive load.
- Turn desktop "all controls visible" editors into sectional editing with sensible defaults.
- Let navigation carry complexity when one gigantic screen would harm usability.
- Keep touch targets, spacing, and scroll behavior native.
- Preserve the emotional tone and product identity without copying every card boundary, shadow, or panel split.
- If the PWA uses visual density that hurts mobile readability, simplify while preserving access to the same information.
- If an interaction relied on hover, wide tables, or horizontal space, redesign it explicitly instead of squeezing it into Compose.

## Logic migration rules

- Preserve formulas, thresholds, defaults, and business invariants unless the task explicitly changes them.
- Normalize data model translation before changing algorithms.
- Prefer Kotlin `data class`, sealed hierarchies, and explicit nullability over Java-like or "ported TS" styles.
- Port one logical unit at a time and verify against the source.
- Keep algorithmic work out of composables.
- Avoid mixing UI adaptation with math refactors in the same step.
- When the PWA logic is tangled with UI state, extract the domain intent first, then port.

## State and data rules

- Translate "React hook + store selector + local component state" into clear native ownership:
  - long-lived feature state in repository + `ViewModel`
  - ephemeral screen state in `ViewModel` or local Compose state depending on lifetime
  - persisted user/app data in Room/DataStore or the current repository pattern
- Do not port `localStorage`, web persistence adapters, or browser lifecycle assumptions literally.
- Prefer `StateFlow` and immutable UI state models for screen state.
- Use coroutines instead of callback-style ports when the native layer is coroutine-first.
- Treat migration of stored JSON/data structures as compatibility-sensitive work.

## Android landing-zone heuristics

- If the source file mostly calculates, score it toward `domain/`.
- If it coordinates persistence, sync, or external IO, score it toward `data/repository/`.
- If it renders a whole feature or route, score it toward `screens/`.
- If it is a reusable visual primitive, score it toward `ui/` or feature-local `components/`.
- If a single PWA file mixes all of the above, split it during migration instead of carrying the coupling forward.

## Risk-aware migration strategy

- Prefer vertical slices over giant subsystem rewrites.
- Migrate logic and tests before expensive UI polish.
- Keep old and new behavior easy to compare while the slice is in flight.
- Do not expand scope from "port this feature" into "rewrite the app architecture".
- Preserve existing Android patterns even if the PWA solved the same problem differently.
- If the target already has partial implementation, extend it instead of replacing it wholesale.
- When the Android target is clearly more mature than the PWA implementation in one area, keep the Android pattern and port only the missing behavior.

## Before editing

- Identify the exact source files that define the feature's behavior.
- Identify the Kotlin landing zone and nearest analogous Android implementation.
- Decide whether the slice is logic-first, workflow-first, UI-translation, or integration-first.
- Write down:
  - what must remain equivalent
  - what should be translated
  - what will intentionally not be carried over
- Check whether root migration docs already mention this slice.

## Before porting logic

- Compare source models and target models field by field.
- Identify constants, defaults, thresholds, enum/state variants, and hidden assumptions.
- Decide whether the logic should live in `domain/`, `data/repository/`, or both.
- Plan a parity check for at least one representative dataset or scenario.
- Avoid touching visual code until the logic boundary is clearer.

## Before translating UI/UX

- Identify the core user tasks on the screen.
- Identify which web affordances are not mobile-native.
- Choose the native Compose structure first: screen, scaffold, sheet, pager, tabs, cards, form sections, bottom actions.
- Keep content priority clear on small screens.
- Decide whether a wizard/stepper/navigation split would be better than one giant replica screen.
- Read [references/translation-principles.md](references/translation-principles.md) when in doubt.

## Before touching storage, sync, or platform integration

- Check whether the web behavior assumes browser APIs, service workers, local files, or web auth/session behavior.
- Find the Android-native equivalent already used in the repo.
- Preserve business intent, not API shape.
- Widen validation when persistence formats, sync behavior, notifications, or AI/native bridges change.

## After editing

- Re-read the diff for signs of literal cloning or web leakage.
- Run `scripts/scan_web_leakage.py` on edited Kotlin files when the port came from UI-heavy PWA code.
- Confirm composables do not contain hidden domain logic that should live elsewhere.
- Confirm models, defaults, and copy stay coherent with the feature's Android flow.
- Note any intentional deviations from the PWA and why they improve the native result.

## Validation

- Validate the smallest Android path that proves the migrated slice works.
- Typical target-side commands:
  - `cd android-native && .\gradlew.bat :app:assembleDebug`
  - `cd android-native && .\gradlew.bat :app:testDebugUnitTest`
  - `cd android-native && .\gradlew.bat :app:connectedDebugAndroidTest`
- Typical source-side reference checks when parity matters:
  - `npx tsc --noEmit`
  - `npm run build`
  - `npm run test:nutrition-logging`
- If the migration changed only Android code, do not run the entire web validation suite out of habit.
- If the migration ports critical formulas, compare representative inputs/outputs or add/adjust tests before declaring parity.
- Use [references/parity-and-cutover.md](references/parity-and-cutover.md) to choose parity level and sign-off depth.

## Do not do these things

- Do not recreate React component trees as one-to-one Compose trees.
- Do not port `useEffect`, `useMemo`, `useRef`, or store selectors mechanically into Kotlin equivalents without rethinking ownership.
- Do not imitate CSS layout hacks with deeply nested `Box` structures when the flow should be redesigned.
- Do not copy web names like `Drawer`, `Panel`, or `Modal` if the Android result is actually a screen, sheet, or dialog.
- Do not keep web-only assumptions such as hover, viewport width, browser persistence, or DOM event timing.
- Do not put domain math into composables because the source component happened to calculate inline.
- Do not chase pixel-perfect parity when it harms Android usability.
- Do not "modernize everything" while migrating one slice.
- Do not silently change formulas or defaults under the excuse of adaptation.

## When to note assumptions

- Note assumptions when source behavior is ambiguous, duplicated, or inconsistent between PWA files.
- Note assumptions when the native target already diverges and you must choose whether to preserve source or current Android behavior.
- Note assumptions when data compatibility, AI behavior, onboarding flow, or persistence semantics are uncertain.
- If you proceed without asking, choose the most local and reversible option and state it clearly.

## Definition of done

- The migrated slice feels native to the Android app rather than copied from the web.
- Critical behavior, formulas, and user outcomes are preserved or intentionally documented when adapted.
- The Kotlin landing zones make architectural sense for `android-native`.
- Compose/UI code is screen-appropriate and not overloaded with domain logic.
- Validation has been run at the narrowest useful scope.
- Any intentional PWA-to-Android divergence is explained as a native adaptation, not left implicit.
