# KPKN Project Map

> Generated seed for KAUPOLIKAN. Run `/map` or use `kpkn_map` with `action=refresh` after structural changes.

## Product Boundaries

- `android-native/`: primary Android product, Kotlin and Jetpack Compose.
- `ios-native/`: Swift/SwiftUI parity port in progress.
- `backend/`: optional FastAPI adaptive-analysis services; Android remains local-first.
- `data/`: source datasets and catalogs. Large assets must be regenerated through documented scripts.
- `docs/`: architecture, parity, QA, and migration documentation.

## Where To Write

- New Android screen: `android-native/app/src/main/java/com/example/kpkn/screens/<feature>/`.
- Screen state: the feature's `*ViewModel.kt`, exposing read-only `StateFlow`.
- Room changes: `data/db/`, `data/repository/`, migrations, and exported schema together.
- Pure business logic: `domain/`, without `android.*` imports.
- Background or hardware behavior: `services/`; voice changes need focused tests.
- Navigation: `navigation/Navigation.kt` and deep-link/router files when needed.
- Shared AUGE behavior: update Android, iOS, backend, and parity documentation together.

## Critical Systems

- Room database: `android-native/app/src/main/java/com/example/kpkn/data/db/KpknDatabase.kt`, current version v20.
- AUGE engines: `android-native/app/src/main/java/com/example/kpkn/domain/auge/`.
- Nutrition parsing: `domain/nutrition/`, `data/food/`, and `data/remote/ExternalAiService.kt`.
- Voice: `services/workout/`, Vosk assets, foreground service, TTS, and AIDL service boundary.
- Navigation routes: `navigation/Navigation.kt`; screens include home, workout, nutrition, programs, session editor, settings, AUGE, WikiLab, learn, profile, and competitions.
- Architecture source: `CLAUDE.md`, `AGENTS.md`, `docs/ARCHITECTURE.md`, `docs/ANDROID_ARCHITECTURE_MAP.md`, and `docs/ANDROID_UI_SCREENS_MAP.md`.

## Safety Constraints

- Do not read or expose `.env`, keystores, signing passwords, or `.cursor/mcp.json` tokens.
- Prefer offline behavior and preserve local-first data ownership.
- Verify the real code and Room schema when older docs disagree.

<!-- KAUPOLIKAN_DYNAMIC_MAP_START -->
Generated at: 2026-08-23T16:06:46.075Z
Kotlin files: 505
Room version detected in KpknDatabase.kt: 23

### Entities
- ActiveProgramEntity
- AugeAdaptiveCacheEntity
- BodyGoalEntity
- BodyObservationEntity
- CompetitionRecordEntity
- CustomExerciseEntity
- CustomFoodEntity
- DailyGoalSnapshotEntity
- GlobalFoodEntity
- GlobalFoodFtsEntity
- JointEntity
- KineticChainEntity
- LearnedResolutionEntity
- MealTemplateEntity
- MovementPatternEntity
- MuscleGroupEntity
- NutritionActiveStateEntity
- NutritionCalibrationProfileEntity
- NutritionLogEntity
- NutritionPlanEntity
- OngoingWorkoutEntity
- PantryItemEntity
- PendingQuestionnaireEntity
- PerformanceRangeEntity
- PerformanceSnapshotEntity
- PostSessionFeedbackEntity
- ProgramEntity
- SessionTemplateEntity
- SettingsEntity
- SleepLogEntity
- SleepLogExtendedEntity
- TendonEntity
- WellbeingEntity
- WorkoutContextPerformanceEntity
- WorkoutContextProfileEntity
- WorkoutGlobalPerformanceEntity
- WorkoutLogEntity
- WorkoutReplacementDecisionEntity

### Routes
- BodyProgress: nutrition/body-progress
- CompetitionDetail: competition/{competitionId}
- Competitions: competitions
- Concepts: concepts?expand={expandConceptId}
- HealthConnect: settings/health-connect
- Home: home
- MealHistory: nutrition/meal-history
- Nutrition: nutrition
- NutritionAction: nutrition/action/{action}
- NutritionCalibration: nutrition/calibration
- NutritionWizard: nutrition/wizard?mode={mode}&planId={planId}
- Profile: profile
- ProgramDetail: program/{programId}?tab={tab}
- SessionEditor: session-editor/{programId}/{sessionId}?weekId={weekId}&macroIndex={macroIndex}&mesoIndex={mesoIndex}&dayOfWeek={dayOfWeek}&configureCompetition={configureCompetition}
- Settings: settings
- SettingsAuge: settings/auge
- SettingsData: settings/data
- SettingsDiagnostics: settings/diagnostics
- SettingsGeneral: settings/general
- SettingsNotifications: settings/notifications
- SettingsNutrition: settings/nutrition
- SettingsProfile: settings/profile
- SettingsTraining: settings/training
- Training: training
- Workout: workout/{programId}/{sessionId}

### ViewModels
- android-native/app/src/main/java/com/example/kpkn/screens/auge/AugeViewModel.kt
- android-native/app/src/main/java/com/example/kpkn/screens/home/HomeViewModel.kt
- android-native/app/src/main/java/com/example/kpkn/screens/nutrition/BodyProgressViewModel.kt
- android-native/app/src/main/java/com/example/kpkn/screens/nutrition/NutritionCalibrationViewModel.kt
- android-native/app/src/main/java/com/example/kpkn/screens/nutrition/NutritionViewModel.kt
- android-native/app/src/main/java/com/example/kpkn/screens/nutrition/NutritionWizardViewModel.kt
- android-native/app/src/main/java/com/example/kpkn/screens/programdetail/ProgramDetailViewModel.kt
- android-native/app/src/main/java/com/example/kpkn/screens/programs/ProgramsViewModel.kt
- android-native/app/src/main/java/com/example/kpkn/screens/sessioneditor/SessionEditorViewModel.kt
- android-native/app/src/main/java/com/example/kpkn/screens/settings/SettingsViewModel.kt
- android-native/app/src/main/java/com/example/kpkn/screens/workout/WorkoutViewModel.kt

### Repositories
- android-native/app/src/main/java/com/example/kpkn/data/repository/AugeRepository.kt
- android-native/app/src/main/java/com/example/kpkn/data/repository/BodyProgressRepository.kt
- android-native/app/src/main/java/com/example/kpkn/data/repository/CompetitionRepository.kt
- android-native/app/src/main/java/com/example/kpkn/data/repository/CustomExerciseRepository.kt
- android-native/app/src/main/java/com/example/kpkn/data/repository/NutritionCalibrationRepository.kt
- android-native/app/src/main/java/com/example/kpkn/data/repository/NutritionRepository.kt
- android-native/app/src/main/java/com/example/kpkn/data/repository/ProgramRepository.kt
- android-native/app/src/main/java/com/example/kpkn/data/repository/SessionTemplateRepository.kt
- android-native/app/src/main/java/com/example/kpkn/data/repository/WikiLabRepository.kt

### AUGE files
- android-native/app/src/main/java/com/example/kpkn/domain/auge/AugeAdaptiveEngine.kt
- android-native/app/src/main/java/com/example/kpkn/domain/auge/AugeClassifiers.kt
- android-native/app/src/main/java/com/example/kpkn/domain/auge/AugeFatigueEngine.kt
- android-native/app/src/main/java/com/example/kpkn/domain/auge/AugeMuscleCapacityEngine.kt
- android-native/app/src/main/java/com/example/kpkn/domain/auge/AugeMuscleNormalization.kt
- android-native/app/src/main/java/com/example/kpkn/domain/auge/AugeRecoveryEngine.kt
- android-native/app/src/main/java/com/example/kpkn/domain/auge/AugeTtcEngine.kt
- android-native/app/src/main/java/com/example/kpkn/domain/auge/AugeUtils.kt
- android-native/app/src/main/java/com/example/kpkn/domain/auge/CardioRingDrainEngine.kt
- android-native/app/src/main/java/com/example/kpkn/domain/auge/DiscomfortAggregationEngine.kt
- android-native/app/src/main/java/com/example/kpkn/domain/auge/DiscomfortSuggestionEngine.kt
- android-native/app/src/main/java/com/example/kpkn/domain/auge/ExerciseFatigueIndex.kt
- android-native/app/src/main/java/com/example/kpkn/domain/auge/ExerciseReadinessEngine.kt
- android-native/app/src/main/java/com/example/kpkn/domain/auge/InterferenceEngine.kt
- android-native/app/src/main/java/com/example/kpkn/domain/auge/MuscularSessionImpactEngine.kt
- android-native/app/src/main/java/com/example/kpkn/domain/auge/NutritionRecoveryEngine.kt
- android-native/app/src/main/java/com/example/kpkn/domain/auge/OvertrainingDetector.kt
- android-native/app/src/main/java/com/example/kpkn/domain/auge/SessionIntensityEngine.kt
- android-native/app/src/main/java/com/example/kpkn/domain/auge/SessionMuscleFilter.kt
<!-- KAUPOLIKAN_DYNAMIC_MAP_END -->
