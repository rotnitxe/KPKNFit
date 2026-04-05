---
name: kpkn-program-workout-flow
description: Translate, refine, debug, and safely evolve KPKN training-flow work in android-native, especially ProgramEditor, SessionEditor, WorkoutScreen, split selection, program structure, session editing, live workout logging, and related repository/navigation/model code. Use when migrating behavior from the PWA into Kotlin/Compose without cloning web UX, or when reviewing/fixing regressions in KPKN program, session, or workout flows.
---

# KPKN Program Workout Flow

## Mission

Work on KPKN's training flow as a native Android product.

Use the PWA as a behavior oracle for business intent, data flow, and edge cases. Do not treat the PWA layout, drawer structure, hover behavior, or desktop density as a contract.

Preserve what makes the feature correct:
- program hierarchy
- session editing behavior
- workout logging semantics
- history continuity
- repository persistence
- navigation argument contracts

Adapt what should be native:
- Compose layout
- input ergonomics
- sheet/dialog presentation
- one-handed workout interactions
- mobile-first information density

## Load These References Only When Needed

- Read `references/flow-surface-map.md` when you need the real PWA-to-Android file map.
- Read `references/native-translation-rules.md` when deciding what to preserve, reinterpret, or drop.
- Read `references/flow-validation-ladder.md` when deciding the narrowest useful validation.

## Start Here Every Time

1. Inspect the Android target first:
   - `android-native/app/src/main/java/com/example/kpkn/screens/programeditor`
   - `android-native/app/src/main/java/com/example/kpkn/screens/sessioneditor`
   - `android-native/app/src/main/java/com/example/kpkn/screens/workout`
2. Trace the affected data and wiring before editing:
   - `android-native/app/src/main/java/com/example/kpkn/data/models/Program.kt`
   - `android-native/app/src/main/java/com/example/kpkn/data/models/Session.kt`
   - `android-native/app/src/main/java/com/example/kpkn/data/models/WorkoutLog.kt`
   - `android-native/app/src/main/java/com/example/kpkn/data/repository/ProgramRepository.kt`
   - `android-native/app/src/main/java/com/example/kpkn/navigation/Navigation.kt`
   - `android-native/app/src/main/java/com/example/kpkn/MainActivity.kt`
3. Inspect the matching PWA source only after you know the Android landing zone.
4. Decide which surface you are changing:
   - program creation and structure
   - session authoring
   - live workout state
   - finish-workout logging
   - navigation and wiring
   - repository or model persistence
5. Make the smallest viable change in the smallest viable seam.

## Core Rules

- Prefer surgical edits in existing Kotlin files before creating new architecture.
- Treat `ProgramRepository` as the operational source of truth for program history, ongoing workout state, and write-through persistence.
- Respect the current route contracts in `Navigation.kt` and `MainActivity.kt`.
- Keep IDs stable for existing program, week, session, exercise, and set entities unless the user is explicitly creating a new object or duplicating one.
- Prefer `copy()` and explicit nested list rebuilding over mutable in-place edits.
- Keep UI text consistent with the app's current Spanish product language unless asked to change copy.
- Use existing helpers such as `Session.allExercises()` and `calculateUnifiedMuscleVolume(...)` instead of re-deriving equivalent logic in the UI.
- Keep ViewModels responsible for flow state and persistence orchestration; keep composables focused on rendering, input capture, and lightweight UI state.

## Risk-Aware Editing Strategy

- For a local UI polish change, stay inside the existing screen file if the logic is already local and stable.
- For a repeated chunk that is making the file harder to reason about, extract a focused composable or helper in the same package rather than inventing a new layer.
- For a behavior change, trace the full path before editing:
  - user action
  - composable callback
  - ViewModel mutation
  - repository write
  - model shape
  - navigation or post-save behavior
- For nested program/session edits, identify the exact branch being replaced and update only that branch.
- For workout-flow changes, verify both in-memory UI state and `ProgramRepository.ongoingWorkout` or history writes.

## Translation Rules For This Repo

- Translate intent, not markup. A PWA drawer, side sheet, or dense editor table usually becomes a Compose screen, bottom sheet, alert dialog, chip row, or expandable card.
- Preserve functional parity for:
  - split selection outcome
  - program structure edits
  - goal/event persistence
  - session exercise/set editing
  - workout set logging
  - rest timing
  - finish-workout log creation
- Reinterpret interaction patterns for Android:
  - hover or desktop affordances become explicit taps
  - giant side panels become staged mobile surfaces
  - tightly packed tables become rows or cards
  - framer-motion sheet choreography becomes simple Material motion
- Do not import PWA complexity that exists only because of web constraints.
- If Android already diverged intentionally and the result is better for mobile, keep the Android pattern unless parity depends on the old behavior.

## Workflow By Area

### Program Editor

- Start with `ProgramEditorViewModel.kt`, `ProgramEditorScreen.kt`, `ProgramCreatorWizard.kt`, and `SplitSelectorSheet.kt`.
- Preserve the wizard-vs-advanced distinction unless the requested task explicitly changes it.
- Treat split application, macro/block/mesocycle structure, goals, and events as model mutations first and UI problems second.
- If changing structure editing, verify whether the change should affect only the current draft or also split application and save behavior.
- If touching volume display, prefer `VolumeCalculator` rather than embedding calculations in composables.

### Session Editor

- Start with `SessionEditorViewModel.kt` and `SessionEditorScreen.kt`.
- Preserve the ability to resolve a session back to its owning program/week path before saving.
- Prefer incremental edits to parts, exercises, and sets instead of replacing the whole session model blindly.
- Treat exercise picking as a controlled insertion path that should stay compatible with `ExerciseDatabase.kt`.
- Keep add/remove/reorder-like behavior localized and deterministic; do not rebuild unrelated parts of the session tree.

### Workout Flow

- Start with `WorkoutViewModel.kt` and `WorkoutScreen.kt`.
- Preserve the state machine order:
  - load session
  - start ongoing workout
  - log set
  - sync ongoing state
  - run or stop rest timer
  - move to next set or exercise
  - finish workout
  - write history
  - clear ongoing workout
- Keep `session.allExercises()` semantics consistent if the session contains parts.
- Treat `lastLog`, ghost data, stress calculation, finish sheet data, and repository writes as behavior-critical, not cosmetic.

## Heuristics For Deciding When Not To Refactor

- Do not split a large screen into many files just because it is large. Split only the area you are actively changing if that extraction reduces risk.
- Do not introduce use cases, reducers, or a new repository layer for a small bugfix.
- Do not rewrite current `ViewModelProvider.Factory` usage unless the task is explicitly about dependency injection or construction.
- Do not "modernize" a working flow just to match an ideal architecture.
- Do not replace stable navigation or persistence code during a feature port unless the current code blocks correctness.
- If a problem is localized to one section or callback, fix that seam first.

## Do Not Do These Things

- Do not copy PWA layout patterns literally into Compose.
- Do not port CSS-era structure, pseudo-sidebar logic, or desktop-only density into Android just because it exists in the web source.
- Do not create a second shadow source of truth outside `ProgramRepository` for the same program/workout state.
- Do not regenerate nested IDs on save for existing entities; that breaks references, logs, and navigation continuity.
- Do not mutate deeply nested lists in place and assume Compose or StateFlow will notice.
- Do not move business logic into composables because it is faster in the moment.
- Do not compute program volume, workout stress, or history-derived hints inline if a domain helper already exists or should exist.
- Do not declare parity based only on visuals. Validate saved data, route arguments, and logs.
- Do not pull unrelated AUGE, AI, or analytics changes into a program/session/workout task unless the request explicitly needs them.

## When To Ask For Or Note Assumptions

Proceed autonomously when the change is local, but explicitly note assumptions when:

- the PWA and Android behaviors disagree and product intent is not obvious
- a requested change would alter save semantics, route contracts, or ID stability
- a mobile-native adaptation intentionally diverges from the PWA interaction
- an incomplete exercise catalog or missing metadata means only partial parity is possible
- you preserve an imperfect existing Android pattern because refactoring it would expand risk beyond the task

When you proceed on an assumption, state the assumption in the final summary instead of blocking early unless the risk is genuinely high.

## Checklists

### Before Editing

- Identify the target area: program editor, session editor, workout, or wiring.
- Read the matching Android files first.
- Trace the data model and repository path.
- Inspect the matching PWA files only for intent and edge cases.
- Decide whether the change is behavior, UI, persistence, or navigation.
- Confirm whether IDs, logs, or route args are affected.

### Before Touching Program Structure

- Confirm whether the change affects draft-only state or persisted programs.
- Check whether split application, wizard defaults, and advanced editor behavior must stay aligned.
- Preserve block, mesocycle, week, and session identity unless duplication is intended.
- Avoid wiping goals, events, or nested weeks accidentally when applying a structure change.

### Before Touching Session Editing

- Verify how the session is found inside the program tree.
- Confirm whether the session can contain top-level exercises, parts, or both.
- Preserve set defaults and rest-time semantics when cloning or adding sets.
- Make sure picker changes still map cleanly to `ExerciseDatabase.kt`.

### Before Touching Workout State

- Verify how the screen loads the session and starts the ongoing workout.
- Confirm whether the change affects rest timer, ghost data, AUGE stress, finish flow, or history writes.
- Preserve the completed-set key strategy unless the task explicitly migrates it everywhere.
- Check whether `session.allExercises()` is part of the affected behavior.

### After Editing

- Re-read the final mutation path for the changed flow.
- Check for accidental ID churn or branch replacement.
- Check that navigation arguments and return behavior still make sense.
- Check that UI state resets or persists only where intended.
- Note any intentional parity gaps or native-only adaptations.

## Validation Strategy

- Start with the narrowest useful command.
- Do not run the whole world for a local screen tweak.
- Expand validation when the edit touches models, repository logic, navigation, finish-workout behavior, or persistence.
- Use `py scripts/suggest_flow_checks.py --changed ...` inside this skill folder if you want a quick recommendation.
- Use `py scripts/build_flow_brief.py --changed ...` when a task spans multiple files and you need a working brief before editing.

Typical commands from the repo root:

```powershell
cd android-native
.\gradlew.bat :app:compileDebugKotlin
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
```

Read `references/flow-validation-ladder.md` before expanding validation beyond the minimum.

## Android-Specific Failure Patterns In This Flow

- Route argument mismatch between `Navigation.kt`, `MainActivity.kt`, and the screen factory
- Session lookup finds the wrong week or fails to persist back into the correct branch
- Split or structure changes accidentally discard nested weeks, goals, or events
- Workout UI advances correctly but `ProgramRepository.ongoingWorkout` or history is stale
- Finish-workout flow writes logs but forgets to clear ongoing workout
- Ghost data or stress calculations silently use the wrong exercise identity
- UI compiles but keyboard, bottom sheet, or long-form editing ergonomics regress badly on mobile

## Definition Of Done

The task is done when:

- the targeted Kotlin flow compiles
- the changed behavior is correct at the relevant model and repository seam
- route and save semantics still hold
- no accidental ID churn or persistence regression was introduced
- any manual review item, parity gap, or assumption is explicitly noted
- validation matched the real risk of the change rather than stopping at "it looks right"
