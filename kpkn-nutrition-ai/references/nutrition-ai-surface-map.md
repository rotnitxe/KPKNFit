# Nutrition AI Surface Map

## Canonical Docs

- `MODELOS.md`
- `docs/local-ai-functiongemma-android.md`

## Important Repo Trap

Several docs and npm scripts still point to legacy `android/` or `apps/mobile` staging targets.

This skill is for the Kotlin app in `android-native/`.

Do not assume that:
- `npm run local-ai:stage-model`
- `npm run local-ai:check-model`
- or older docs that mention `android/`

prove anything about the current `android-native/` packaging path. Verify the real landing zone before editing runtime or asset-loading code.

## PWA Oracle Files

Load only what the task needs.

### Parser and runtime bridge

- `services/aiNutritionParser.ts`
- `services/localAiService.ts`

### Food logging UX

- `components/nutrition/RegisterFoodDrawer.tsx`

### Telemetry and assisted-resolution extras

- `services/nutritionAiTelemetryService.ts`
- `services/foodSearchService.ts`

### Regression oracle

- `tests/nutritionLoggingRegression.ts`

## Android Target Surfaces

### Nutrition screen and UI

- `android-native/app/src/main/java/com/example/kpkn/screens/nutrition/NutritionScreen.kt`
- `android-native/app/src/main/java/com/example/kpkn/screens/nutrition/NutritionViewModel.kt`
- `android-native/app/src/main/java/com/example/kpkn/screens/nutrition/components/FoodLoggerDrawer.kt`
- `android-native/app/src/main/java/com/example/kpkn/screens/nutrition/components/NutritionPlanEditorModal.kt`
- `android-native/app/src/main/java/com/example/kpkn/screens/nutrition/components/NutritionWizardView.kt`

### Local AI runtime and parser bridge

- `android-native/app/src/main/java/com/example/kpkn/data/localai/LocalAiNutritionParserBridge.kt`
- `android-native/app/src/main/java/com/example/kpkn/data/localai/LocalAiManager.kt`
- `android-native/app/src/main/java/com/example/kpkn/MainActivity.kt`

### Shared data and persistence

- `android-native/app/src/main/java/com/example/kpkn/data/repository/NutritionRepository.kt`
- `android-native/app/src/main/java/com/example/kpkn/data/models/NutritionModels.kt`
- `android-native/app/src/main/java/com/example/kpkn/data/food/FoodDatabase.kt`
- `android-native/app/src/main/java/com/example/kpkn/domain/nutrition/FoodParser.kt`
- `android-native/app/src/main/java/com/example/kpkn/domain/nutrition/MacroCalculator.kt`
- `android-native/app/src/main/java/com/example/kpkn/data/db/Entities.kt`
- `android-native/app/src/main/java/com/example/kpkn/data/db/Daos.kt`

## Behavior-Critical Invariants

### Parser contract

- `parseFreeFormNutrition(...)` should return a valid `ParsedMealDescription` and degrade gracefully.
- `analysisEngine`, `modelVersion`, `containsEstimatedItems`, and `requiresReview` carry product meaning and should not drift casually.

### UI contract

- typing in the logger should stay responsive
- analysis should be explicit
- estimated or unresolved items should remain honest and editable

### Save contract

- the final save path should produce a `NutritionLog` containing `LoggedFood`
- repository updates should hit both StateFlow and Room-backed persistence

### Runtime contract

- local model absence is a supported path
- runtime loading errors should not break nutrition logging entirely

## Known Parity Gap To Treat Deliberately

The PWA currently has richer telemetry/manual-correction infrastructure around nutrition AI. The Kotlin app has the core parser/runtime flow, but not the same surrounding instrumentation. Port that deliberately only when the task truly needs it.
