# KPKN Project Map

> Generated seed for KAUPOLIKAN. Run `/map` or use `kpkn_map` with `action=refresh` after structural changes.

## Product Boundaries

- `android-native/`: primary Android product, Kotlin and Jetpack Compose.
- `ios-native/`: Swift/SwiftUI parity port in progress.
- `backend/`: optional FastAPI history/volume services; Android remains local-first.
- `data/`: source datasets and catalogs. Large assets must be regenerated through documented scripts.
- `docs/`: architecture, parity, QA, and migration documentation.

## Where To Write

- New Android screen: `android-native/app/src/main/java/com/example/kpkn/screens/<feature>/`.
- Screen state: the feature's `*ViewModel.kt`, exposing read-only `StateFlow`.
- Room changes: `data/db/`, `data/repository/`, migrations, and exported schema together.
- Pure business logic: `domain/`, without `android.*` imports.
- Background or hardware behavior: `services/`; voice changes need focused tests.
- Navigation: `navigation/Navigation.kt` and deep-link/router files when needed.
- Shared recorded-training contracts: update Android, iOS, backend, and parity documentation together.

## Critical Systems

- Room database: `android-native/app/src/main/java/com/example/kpkn/data/db/KpknDatabase.kt`, current version **v35**. Recipes/TM/autoregulation live in `ProgramEntity` JSON (no extra tables). Workout media: `workout_media` + `filesDir/workout_media`.
- Recorded training: `domain/training/insights/`; originals: `data/repository/TrainingObservationRepository.kt`. Retired engines are absent; compatibility importers only preserve original records.
- Nutrition parsing: `domain/nutrition/`, `data/food/`, and `data/remote/ExternalAiService.kt`.
- Voice: `services/workout/`, Vosk assets, foreground service, TTS, and AIDL service boundary.
- Navigation routes: `navigation/Navigation.kt`; screens include home, workout, nutrition, programs, session editor, settings, WikiLab, learn, profile, and competitions.
- Architecture source: `CLAUDE.md`, `AGENTS.md`, `docs/ARCHITECTURE.md`, `docs/ANDROID_ARCHITECTURE_MAP.md`, and `docs/ANDROID_UI_SCREENS_MAP.md`.

## Safety Constraints

- Do not read or expose `.env`, keystores, signing passwords, or `.cursor/mcp.json` tokens.
- Prefer offline behavior and preserve local-first data ownership.
- Verify the real code and Room schema when older docs disagree.

<!-- KAUPOLIKAN_DYNAMIC_MAP_START -->
Generated from the applied local working tree: 2026-10-24T16:38:35.048295+00:00
Kotlin main files: 1617
Room version: 35; preservation migrations and historical schemas are authoritative.
Training recipes: data/protocols/TrainingPlanRecipe.kt → domain/training/PlanMaterializer.kt. Protocol proposals require explicit confirmation. No physiological readiness, drain or injury predictor.

### Entities
- ProgramEntity
- WorkoutLogEntity
- CompetitionRecordEntity
- SettingsEntity
- TrainingObservationEntity
- ActiveProgramEntity
- OngoingWorkoutEntity
- WorkoutContextPerformanceEntity
- WorkoutGlobalPerformanceEntity
- WorkoutContextProfileEntity
- WorkoutTagEntity
- WorkoutReplacementDecisionEntity
- NutritionLogEntity
- NutritionPlanEntity
- NutritionActiveStateEntity
- BodyObservationEntity
- BodyGoalEntity
- PantryItemEntity
- MealTemplateEntity
- CustomFoodEntity
- MuscleGroupEntity
- JointEntity
- TendonEntity
- MovementPatternEntity
- KineticChainEntity
- GlobalFoodEntity
- GlobalFoodFtsEntity
- CustomExerciseEntity
- SessionTemplateEntity
- LearnedResolutionEntity
- NutritionCalibrationProfileEntity
- DailyGoalSnapshotEntity
- PerformanceRangeEntity
- PerformanceSnapshotEntity
- WorkoutMediaEntity
- WorkoutMediaSessionAssociationEntity
- SetupDraftEntity
- SetupCommitReceiptEntity
- VideoAnalysisEntity
- CalibrationDecisionEntity

### Routes
- Home: `home`
- Training: `training`
- Nutrition: `nutrition`
- BodyProgress: `nutrition/body-progress`
- Concepts: `concepts?expand={expandConceptId}`
- ProgramDetail: `program/{programId}`
- ProgramEditor: `program-editor?programId={programId}`
- ImportProgram: `program-import`
- SessionEditor: `session-editor/{programId}/{sessionId}?weekId={weekId}&macroIndex={macroIndex}&mesoIndex={mesoIndex}&dayOfWeek={dayOfWeek}`
- Workout: `workout/{programId}/{sessionId}`
- Competitions: `competitions`
- CompetitionDetail: `competition/{competitionId}`
- Settings: `settings`
- SettingsGeneral: `settings/general`
- SettingsProfile: `settings/profile`
- SettingsNutrition: `settings/nutrition`
- SettingsTraining: `settings/training`
- Historial original de bienestar: `training_observations` (sin predictores AUGE).
- SettingsNotifications: `settings/notifications`
- SettingsData: `settings/data`
- SettingsDiagnostics: `settings/diagnostics`
- Profile: `profile`
- WorkoutAlbums: `profile/albums`
- WorkoutAlbumDetail: `profile/albums/{albumKey}`
- WorkoutMediaViewer: `media/{mediaId}`
- NutritionPlanEditor: `nutrition/plan-editor?planId={planId}&draftId={draftId}`
- NutritionWizard: `nutrition/wizard?mode={mode}&planId={planId}`
- SetupEntry: `setup/entry`
- SetupVisualGate: `setup/visual-gate`
- SetupWizard: `setup/wizard?mode={mode}&draftId={draftId}&planId={planId}`
- NutritionCalibration: `nutrition/calibration`
- MealHistory: `nutrition/meal-history`
- NutritionAction: `nutrition/action/{action}`
- TechniqueHub: `technique`
- TechniqueAnalysis: `technique/analysis/{mediaId}`
- TechniqueTrends: `technique/trends/{exerciseKey}`
- TechniqueCompare: `technique/compare/{mediaA}/{mediaB}`
- HealthConnect: `settings/health-connect`

### ViewModels
- `screens/albums/WorkoutAlbumsViewModel.kt`
- `screens/competitions/wizard/CompetitionWizardViewModel.kt`
- `screens/home/HomeViewModel.kt`
- `screens/nutrition/BodyProgressViewModel.kt`
- `screens/nutrition/components/FoodLoggerViewModel.kt`
- `screens/nutrition/MealHistoryViewModel.kt`
- `screens/nutrition/NutritionCalibrationViewModel.kt`
- `screens/nutrition/NutritionPlanEditorViewModel.kt`
- `screens/nutrition/NutritionViewModel.kt`
- `screens/onboarding/SetupWizardViewModel.kt`
- `screens/programdetail/ProgramDetailViewModel.kt`
- `screens/programs/import/ImportProgramViewModel.kt`
- `screens/programs/ProgramEditorViewModel.kt`
- `screens/programs/ProgramsViewModel.kt`
- `screens/sessioneditor/design/CustomExerciseEditorViewModel.kt`
- `screens/sessioneditor/SessionEditorViewModel.kt`
- `screens/settings/SettingsViewModel.kt`
- `screens/technique/analysis/TechniqueAnalysisViewModel.kt`
- `screens/technique/compare/TechniqueCompareViewModel.kt`
- `screens/technique/hub/TechniqueHubViewModel.kt`
- `screens/technique/importer/VideoImportViewModel.kt`
- `screens/technique/trends/TechniqueTrendsViewModel.kt`
- `screens/workout/WorkoutViewModel.kt`

### Repositories
- `data/repository/BodyProgressRepository.kt`
- `data/repository/CalibrationDecisionRepository.kt`
- `data/repository/CompetitionRepository.kt`
- `data/repository/CustomExerciseRepository.kt`
- `data/repository/NutritionCalibrationRepository.kt`
- `data/repository/NutritionRepository.kt`
- `data/repository/ProgramRepository.kt`
- `data/repository/SessionTemplateRepository.kt`
- `data/repository/TrainingObservationRepository.kt`
- `data/repository/VideoAnalysisRepository.kt`
- `data/repository/WikiLabRepository.kt`
- `data/repository/WorkoutMediaRepository.kt`

### Recorded training engines
- `domain/training/insights/TrainingHistoryProjection.kt`
- `domain/training/insights/TrainingInsightsEngine.kt`

### Compatibility and verification
- `data/compatibility/LegacyTrainingImport.kt` and `LegacyTrainingBackup.kt`: isolated original-data recovery; never reactivate retired engines.
- `scripts/verify_post_rings_retirement.py`: active-product boundary with exact compatibility exceptions.
- `docs/post-rings/README.md` and `docs/research/training-evidence/VERIFICACION_LOCAL_20261024.md`: current implementation and evidence limits.
<!-- KAUPOLIKAN_DYNAMIC_MAP_END -->
