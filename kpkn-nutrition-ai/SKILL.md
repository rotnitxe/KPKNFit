---
name: kpkn-nutrition-ai
description: Translate, refine, debug, and safely evolve KPKN nutrition AI in android-native, especially free-form food logging, FoodLoggerDrawer UX, Local AI nutrition parsing, deterministic fallback behavior, food resolution, and NutritionRepository persistence. Use when porting or validating behavior from the PWA nutrition pipeline into Kotlin/Compose without cloning the web implementation.
---

# KPKN Nutrition AI

## Mission

Work on KPKN nutrition AI as a reliable Android product, not as a demo parser.

Preserve the parts that make the feature trustworthy:
- explicit analyze flow
- robust fallback when local AI is unavailable
- correct transformation from parsed items to `LoggedFood`
- stable save semantics in `NutritionRepository`
- Spanish/Chilean food parsing behavior
- review-aware results instead of false certainty

Adapt the interaction and runtime behavior to Android:
- Compose-native logging UI
- mobile-friendly review/edit flows
- on-device model lifecycle
- Gradle/build awareness
- asset-path verification instead of blind trust in legacy scripts

## Load These References Only When Needed

- Read `references/nutrition-ai-surface-map.md` for the real PWA-to-Android file map and repo traps.
- Read `references/nutrition-ai-translation-rules.md` when deciding what to preserve, reinterpret, or defer.
- Read `references/nutrition-ai-validation-ladder.md` when choosing validation.

## Start Here Every Time

1. Inspect the Android target first:
   - `android-native/app/src/main/java/com/example/kpkn/screens/nutrition/components/FoodLoggerDrawer.kt`
   - `android-native/app/src/main/java/com/example/kpkn/data/localai/LocalAiNutritionParserBridge.kt`
   - `android-native/app/src/main/java/com/example/kpkn/data/localai/LocalAiManager.kt`
   - `android-native/app/src/main/java/com/example/kpkn/data/repository/NutritionRepository.kt`
   - `android-native/app/src/main/java/com/example/kpkn/data/models/NutritionModels.kt`
   - `android-native/app/src/main/java/com/example/kpkn/domain/nutrition/FoodParser.kt`
   - `android-native/app/src/main/java/com/example/kpkn/domain/nutrition/MacroCalculator.kt`
2. Inspect the Android screen wiring if relevant:
   - `android-native/app/src/main/java/com/example/kpkn/screens/nutrition/NutritionScreen.kt`
   - `android-native/app/src/main/java/com/example/kpkn/screens/nutrition/NutritionViewModel.kt`
   - `android-native/app/src/main/java/com/example/kpkn/MainActivity.kt`
3. Inspect the matching PWA oracle only after you know the Kotlin landing zone:
   - `services/aiNutritionParser.ts`
   - `services/localAiService.ts`
   - `components/nutrition/RegisterFoodDrawer.tsx`
   - `tests/nutritionLoggingRegression.ts`
4. Decide which surface you are changing:
   - food logging UI
   - parser/orchestration
   - local model runtime
   - persistence/repository
   - nutrition screen/viewmodel wiring
5. Change the smallest seam that restores or advances correctness.

## Core Rules

- `parseFreeFormNutrition(...)` must remain safe for callers. Prefer graceful fallback over throwing.
- Keep the analyze action explicit. Do not move heavy parsing/inference into every keystroke.
- Preserve the `ParsedMealDescription` and `ParsedMealItem` contract unless every consumer changes with it.
- Treat `FoodItem` -> `LoggedFood` scaling and macro math as behavior-critical.
- Preserve `NutritionRepository` write-through behavior: update StateFlow immediately and persist in the background.
- Do not assume the local model is packaged. The no-model path is a first-class path.
- Respect current Android simplifications if they are intentional. The PWA is an oracle for behavior, not a requirement to port every telemetry or UI subsystem.
- Be careful with repo-level docs and scripts: some still point to legacy `android/` or `apps/mobile` paths rather than `android-native/`.

## Risk-Aware Editing Strategy

- For a UI-only tweak, stay inside `FoodLoggerDrawer.kt` or `NutritionScreen.kt` if the change is local.
- For parser changes, trace the full path before editing:
  - description input
  - `parseFreeFormNutrition(...)`
  - AI/deterministic merge
  - tag resolution
  - `LoggedFood` creation
  - `NutritionLog` save
- For local model runtime changes, verify:
  - initialization path in `MainActivity.kt`
  - expected asset location in `LocalAiManager.kt`
  - timeout behavior
  - no-model fallback behavior
- For persistence changes, verify both StateFlow behavior and Room-backed writes.
- If the Android implementation is intentionally thinner than the PWA, extend only the slice the task truly needs.

## Workflow By Area

### Food Logger UI

- Start with `FoodLoggerDrawer.kt`.
- Keep typing responsive and analysis explicit.
- Preserve the distinction between unresolved, estimated, and ready-to-save items.
- Make review affordances obvious when `reviewRequired` or fuzzy resolution is present.
- Do not save raw parsed items directly; always save resolved `LoggedFood` output.

### Parser And Orchestration

- Start with `LocalAiNutritionParserBridge.kt`, `FoodParser.kt`, and the PWA `services/aiNutritionParser.ts`.
- Preserve deterministic parsing as the safety net.
- Treat AI as a structured supplement, not the only engine.
- If changing merge logic, watch for duplicate foods, dropped foods, or false confidence.
- Keep `analysisEngine`, `modelVersion`, `containsEstimatedItems`, and `requiresReview` semantics coherent.

### Local AI Runtime

- Start with `LocalAiManager.kt` and `MainActivity.kt`.
- Preserve thread safety, init idempotence, timeouts, and no-model behavior.
- Verify actual asset expectations before changing model-loading logic.
- Prefer making runtime status more observable over making the parser depend on hidden assumptions.

### Repository And Screen Wiring

- Start with `NutritionRepository.kt`, `NutritionViewModel.kt`, and `NutritionScreen.kt`.
- Preserve add/delete/duplicate plan and log behavior.
- Keep derived state in the ViewModel; avoid moving totals or plan logic into composables.
- If touching `NutritionScreen.kt`, avoid expanding scope into unrelated nutrition planning UX unless requested.

## Heuristics For Deciding When Not To Refactor

- Do not port the full PWA telemetry, food-memory, or search stack just to fix a local parser or UI bug.
- Do not rewrite the repository or screen architecture during a parser task.
- Do not replace a simple deterministic fallback with a more complex AI path unless the task explicitly requires it.
- Do not rewrite `FoodLoggerDrawer.kt` from scratch because it is long; isolate the touched concern instead.
- Do not expand a model-runtime task into a whole build-system migration unless packaging truly blocks the feature.

## Do Not Do These Things

- Do not make local AI mandatory for the feature to work.
- Do not run heavy analysis on each character typed.
- Do not trust legacy `local-ai:stage-model` or `local-ai:check-model` scripts as proof that `android-native` is correctly packaged.
- Do not silently change `analysisEngine` or `modelVersion` semantics.
- Do not save zero-macro placeholders as if they were valid resolved foods unless the UI clearly marks them for review.
- Do not break Spanish/Chilean parsing tokens, aliases, or protected food phrases while chasing a small bug.
- Do not move parser/business logic into Compose callbacks because it is convenient.
- Do not claim end-to-end success based only on the button flow; verify parse output, resolved foods, and repository writes.

## When To Ask For Or Note Assumptions

Proceed autonomously for local fixes, but explicitly note assumptions when:

- the PWA and Android pipelines intentionally differ
- a task depends on local-model packaging and the repo still contains legacy Android paths
- the requested change would alter `ParsedMealDescription` semantics
- you preserve a simplified Android behavior instead of porting a PWA subsystem
- telemetry, manual-correction memory, or assisted-resolution behavior is only partially present in Kotlin

When you proceed on an assumption, document it in the final summary instead of blocking early unless the risk is high.

## Checklists

### Before Editing

- Identify whether the task is UI, parser, runtime, or persistence.
- Read the Android target first.
- Trace the save path into `NutritionRepository`.
- Inspect the PWA oracle only for behavior and edge cases.
- Check whether the task depends on model assets, build packaging, or runtime status.

### Before Touching The Parser

- Read `NutritionModels.kt`, `FoodParser.kt`, and `LocalAiNutritionParserBridge.kt`.
- Confirm whether the change affects parse contract, merge strategy, or output metadata.
- Preserve deterministic fallback behavior.
- Check whether `reviewRequired`, `analysisEngine`, or `modelVersion` semantics change.

### Before Touching Local AI Runtime

- Read `LocalAiManager.kt` and the init call in `MainActivity.kt`.
- Confirm expected asset path and model name.
- Verify whether the repo's staging/check scripts target the same Android app you are editing.
- Preserve timeout protection and thread safety.

### Before Touching Food Logger UI

- Keep typing and editing responsive.
- Preserve explicit analyze behavior.
- Verify how parsed items are resolved into `LoggedFood`.
- Make sure review-required items remain editable and visible.

### After Editing

- Re-read the full flow from input to saved `NutritionLog`.
- Check for accidental parser crashes or swallowed failures.
- Check whether unresolved or estimated items are labeled appropriately.
- Check whether repository state and screen state still agree.
- Note any legacy-script/path mismatch or parity gap you intentionally left alone.

## Validation Strategy

- Start with the narrowest useful check.
- Use the PWA regression test as an oracle when parser behavior or food matching is involved.
- Expand validation when touching runtime loading, build packaging, repository writes, or parse contracts.
- Use `py scripts/build_nutrition_ai_brief.py --changed ...` inside this skill folder when the task spans several files.
- Use `py scripts/suggest_nutrition_ai_checks.py --changed ...` for quick validation guidance.

Typical commands from the repo root:

```powershell
npm run test:nutrition-logging
cd android-native
.\gradlew.bat :app:compileDebugKotlin
.\gradlew.bat :app:assembleDebug
```

Treat `npm run local-ai:check-model` as a legacy-path sanity check, not definitive proof for `android-native`.

## Android-Specific Failure Patterns In This Flow

- the UI works, but parsing always falls back because no model is actually packaged for `android-native`
- `LocalAiManager` expects `install-time-models/...` but the staged assets live elsewhere
- AI and deterministic merge creates duplicate foods or drops one side of a composite dish
- parsed items look right, but `LoggedFood` scaling or macro overrides are wrong
- review-required items are saved as if they were fully trustworthy
- repository writes succeed, but the screen reads the wrong state source
- startup warmup exists, but runtime status is never surfaced when debugging parser behavior

## Definition Of Done

The task is done when:

- parser and save semantics are still correct
- fallback behavior still works when the local model is missing or slow
- the targeted Kotlin files compile
- any relevant PWA nutrition regression oracle was checked
- unresolved or estimated nutrition results are handled honestly in the UI
- assumptions, parity gaps, or legacy-path caveats are explicitly noted
