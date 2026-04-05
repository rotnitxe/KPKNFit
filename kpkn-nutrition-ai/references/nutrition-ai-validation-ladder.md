# Nutrition AI Validation Ladder

Choose validation based on the real risk you introduced.

## Fastest Useful Checks

### PWA parser oracle changes

Run:

```powershell
npm run test:nutrition-logging
```

Use when touching:
- `services/aiNutritionParser.ts`
- parser behavior you are trying to preserve in Kotlin
- food matching/parsing assumptions that should still match the web oracle

### Kotlin UI or parser-flow changes

Run:

```powershell
cd android-native
.\gradlew.bat :app:compileDebugKotlin
```

Use when touching:
- `FoodLoggerDrawer.kt`
- `NutritionScreen.kt`
- `NutritionViewModel.kt`
- `LocalAiNutritionParserBridge.kt`
- `FoodParser.kt`
- `MacroCalculator.kt`

## Medium Checks

### Runtime loading, repository, or model changes

Run:

```powershell
cd android-native
.\gradlew.bat :app:assembleDebug
```

Use when touching:
- `LocalAiManager.kt`
- `MainActivity.kt`
- `NutritionRepository.kt`
- Room entities or DAOs
- model-loading paths
- build config related to the local model

## Manual Smoke Recommendations

### No-model path

- open the nutrition logger
- enter a simple meal description
- analyze
- confirm the flow still returns editable tags instead of failing hard

### Save path

- resolve or edit at least one food
- save the log
- verify it appears in the screen state backed by `NutritionRepository`

### Model-ready path

- only if you know the model is actually packaged for `android-native`
- confirm runtime status is ready
- analyze a meal description
- verify the result is marked as local-AI-assisted rather than silently deterministic

## Legacy Script Caveat

`npm run local-ai:check-model` and `npm run local-ai:stage-model` currently inspect or stage legacy `android/` and `apps/mobile` paths.

Use them as context only. Do not treat them as authoritative for `android-native` unless you first confirm they now target that app.

## Escalate Validation When

- parser contract fields change
- local-model packaging or asset paths change
- repository persistence changes
- unresolved/estimated-item handling changes
- the task explicitly aims for PWA parity in nutrition parsing
