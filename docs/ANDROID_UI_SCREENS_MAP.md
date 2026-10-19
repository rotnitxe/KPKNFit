# UI Screens Mapping: KPKN Fit Android App (For iOS SwiftUI Porting)

This document provides a detailed breakdown of the user interface (UI) screens, Jetpack Compose layouts, ViewModels, and state interactions of the native Android app. Use this as a direct reference to construct matching SwiftUI Views and ViewModels in Xcode.

> All paths below are relative to `android-native/app/src/main/java/com/example/kpkn/`.
> Route names refer to the `KpknRoute` objects declared in `navigation/Navigation.kt`.

---

## 🧭 0. Navigation Overview

Single-activity app: `MainActivity.kt` hosts a Compose `NavHost` + bottom navigation bar.

| Route | Screen / Package |
| :--- | :--- |
| `home` | `screens/home/HomeScreen.kt` |
| `training` | `screens/programs/ProgramsScreen.kt` |
| `nutrition` | `screens/nutrition/NutritionScreen.kt` |
| `wikilab` | `screens/wikilab/WikiLabHomeScreen.kt` |
| `program/{programId}` | `screens/programdetail/ProgramDetailScreen.kt` |
| `session-editor/{programId}/{sessionId}?...` | `screens/sessioneditor/SessionEditorScreen.kt` |
| `workout/{programId}/{sessionId}` | `screens/workout/WorkoutScreen.kt` |
| `readiness-gate/{programId}/{sessionId}` | `screens/workout/ReadinessGateScreen.kt` |
| `settings` (+ `/general`, `/profile`, `/nutrition`, `/training`, `/auge`, `/notifications`, `/data`) | `screens/settings/*` |
| `settings/health-connect` (health flavor only) | Health Connect route via `addHealthConnectRoute` |
| `competitions`, `competition/{competitionId}` | `screens/competitions/CompetitionScreen.kt` |
| `profile` | `screens/profile/ProfileScreen.kt` |
| `technique` | `screens/technique/hub/TechniqueHubScreen.kt` (gallery import and recording/analysis settings) |
| `technique/analysis/{mediaId}` | `screens/technique/analysis/TechniqueAnalysisScreen.kt` |
| `technique/trends/{exerciseKey}` | `screens/technique/trends/TechniqueTrendsScreen.kt` |
| `technique/compare/{mediaA}/{mediaB}` | `screens/technique/compare/TechniqueCompareScreen.kt` |
| `wikilab/exercises`, `/exercise-creator`, `/exercise/{exerciseId}` | `screens/wikilab/*` |
| `wikilab/muscles`, `/muscle/{muscleId}` | `screens/wikilab/MuscleCategoryScreen.kt`, `MuscleGroupDetailScreen.kt` |
| `wikilab/joints`, `/joint/{jointId}` | `screens/wikilab/JointsListScreen.kt`, `JointDetailScreen.kt` |
| `wikilab/tendon/{tendonId}` | `screens/wikilab/TendonDetailScreen.kt` |
| `wikilab/patterns`, `/pattern/{patternId}` | `screens/wikilab/PatternsListScreen.kt`, `MovementPatternDetailScreen.kt` |
| `wikilab/chain/{chainId}` | `screens/wikilab/*` (kinetic chain detail) |
| `wikilab/biomechanics` | `screens/wikilab/BiomechanicsScreen.kt` |
| `wikilab/concepts`, `/concept/{conceptId}` | `screens/wikilab/TrainingConceptsScreen.kt` |
| `nutrition/wizard`, `/body-progress`, `/meal-history`, `/action/{action}` | `screens/nutrition/*` |
| `learn`, `learn/course/{courseId}`, `/reader/...`, `/quiz/...`, `/badge/{courseId}` | `screens/learn/*` |

Deep links: `kpkn://` and `https://kpkn.fit` handled by `navigation/DeepLinkRouter.kt` + `KpknDeepLinks.kt`; cross-feature events via `navigation/NavigationBus.kt`.

---

## 🏠 1. Home Screen (`screens/home/HomeScreen.kt` & `HomeViewModel.kt`)

The Home screen is the default landing page. Since Home v1 it uses the wizard's identity: a black surface, the warm ink of the brand, Syne for titles and figures and Inter for reading. It is built only from `WizardColors`, `WizardTypography`, `WizardSpacing` and `SymbolPalette`, plus Home's own pieces in `screens/home/design/`. Sections are separated by air (`HomeSectionGap`, 40 dp), not by cards.

```
+-----------------------------------------------------+
| Masthead: date, greeting, avatar, active program    |  glass top bar appears when it scrolls away
| Week strip: 7 days from the program's start day     |
+-----------------------------------------------------+
| Session hero: today's session, rest, other day,     |
| no program, competition, countdown                  |
| Muscle rings: one per muscle of the session         |
+-----------------------------------------------------+
| RINGS: tower + three channel rows, (i) introduction |  detail = glass sheet
+-----------------------------------------------------+
| Notices (no box), only when there is something      |
| Nutrition today: macro rings + four meals           |  day overlay (glass)
| Body: weight trend, % fat, % muscle, IMC and FFMI   |
+-----------------------------------------------------+
| Your programs: 3:4 posters, active first, "Nuevo"   |
| Learn: concept of the day, "Leer"                   |
+-----------------------------------------------------+
  Bottom bar (MainActivity, I7): active tab in ink, muscle dot
```

### 1.1 Order of the page

`HomeWithProgram` in `HomeScreen.kt` is the `LazyColumn`. Its items, top to bottom:

1. `HomeMasthead` (`screens/home/design/HomeMasthead.kt`): `homeDateLine`, `HomeMastheadState`, profile button.
2. `HomeWeekStrip` (`design/HomeWeekStrip.kt`): only with an active program and days.
3. `HomeSessionSection` (`screens/home/HomeSessionSection.kt`): a pager of `HomeSessionHero` (`design/HomeSessionHero.kt`), with the muscle rings of `HomeMuscleGauges` (`design/HomeMuscleGauges.kt`) below.
4. `HomeRingsSection` (`screens/home/HomeRingsSection.kt`): the tower (`ui/components/rings/KpknTowerRings`) and one `HomeRingRow` per channel (`design/HomeRingRow.kt`). The (i) opens `RingsIntroOverlay` in review mode. The detail is `RingDetailSheet.kt`, with `HomeRingRange`, `HomeRingMuscleGrid` and `HomeRingSparkline` in `design/`.
5. `HomeNotice` (`design/HomeNotice.kt`): the auto-deload advisory and the overtraining notice. Each one appears only when it applies.
6. `HomeNutritionToday` (`design/HomeNutritionToday.kt`, state in `screens/home/HomeNutritionState.kt`) and `HomeBodyToday` (`design/HomeBodyToday.kt`, state in `screens/home/HomeBodyState.kt`).
7. `HomeProgramsSection` (`screens/home/HomeProgramsSection.kt`): posters with `fitPosterTitleSp` and `posterTitleOneLine`, so no title breaks inside a word.
8. `HomeWikiLabSection` (`screens/home/HomeWikiLabSection.kt`): the «APRENDE» concept of the day.

Two overlays hang from the root glass host, not from the list:

- `HomeTopBar` (`HomeScreen.kt`): the glass bar. Its opacity comes from `homeBarAlphaFor`, so it appears only when the masthead leaves the screen. Glass is kept for what floats (this bar, the bottom bar, the day overlay and the sheets); the page itself stays black and continuous.
- `NutritionTodayGlassOverlay` (`HomeScreen.kt`): «Comidas de hoy». Each meal is a row without a box (type, foods and kcal, with a hairline between rows) and «Registrar comida» is the primary pill.

### 1.2 Shared pieces (`screens/home/design/`)

- `HomeDesign.kt`: `HomeSectionGap`, `HomeSectionLabel` (eyebrow in `textMuted`), `HomeSectionAction` (text action, 48 dp), `HomePrimaryPill` (56 dp, ink) and `HomeSecondaryPill`, `HomeTypography` (the Home-only styles, all at 13 sp or more), `Modifier.homeEntrance(index)` (first time per process only) and `HomeDateSaver`.
- `HomeReveal.kt` and `HomeFigures.kt`: a figure counts up, and a stroke draws, only the first time the process shows them. With reduced motion, the final value.
- `HomeGlyphButton.kt`: the (i) and close glyphs, in ink.

### 1.3 State (`HomeViewModel.kt`)

- `uiState: StateFlow<HomeUiState>`: name, greeting, active program, today's sessions, primary session, rest day, competition, daily calorie goal, today's nutrition totals, overtrained muscles and programs.
- `bodyToday: StateFlow<HomeBodyState>`, the daily goals (`dailyCalorieGoal`, `dailyProteinGoal`, `dailyCarbGoal`, `dailyFatGoal`), `todayNutritionTotals`, `competitionDatePromptProgramId` and `onboardingState`.
- `heightCm` is the real height from the profile, or null. It is never a default, so the IMC and the FFMI appear only with a real height.
- Program sheet and volume calibration state (`showCreateProgramSheet`, `pending*`).
- Honesty rule: without data the page shows «—», «Sin calibrar» or «Sin objetivo». Nothing is invented.

### 1.4 Tests

- Domain (JVM): `HomeWeekResolverTest`, `HomeSessionResolverTest`, `HomeCompetitionResolverTest`, `HomeBodyStateTest`.
- UI (Robolectric): `HomeProgramsSectionTest`, `HomeRingsSectionTest`, `RingDetailSheetTest`, `HomeNutritionTodayTest`, `HomeSessionHeroTest`, `HomeWeekStripTest`, `CompetitionDatePromptTest`.
- Render sheets (`src/test/java/com/example/kpkn/render/`): they write PNG only when `KPKN_RENDER_DIR` is set. `HomeTopRenderSheetTest`, `HomeHeroRenderSheetTest`, `HomeRingsRenderSheetTest`, `HomeCardsRenderSheetTest`, `HomeProgramsRenderSheetTest`, `HomeBarsRenderSheetTest` (the bars) and `HomeFullRenderSheetTest` (the whole page, four cases, 100 % and 130 %).
- Shared sample data: `src/test/java/com/example/kpkn/screens/home/HomeSamples.kt` (`pageCases()` for the full page).

### 1.5 Known gaps

- Still on Material colours and typography, outside the page body: the recovery recommendation dialog in `HomeScreen.kt` (`KpknAlertDialog`, with `MaterialTheme`) and the «Conceptos clave» screens (`ConceptosClaveScreen.kt`, `ConceptoClaveAccordion.kt`), which MainActivity still opens.
- `SingleRingCanvas` (in `HomeRingsSection.kt`) has no caller inside Home, but four sessioneditor files still import it. It stays until those imports are removed.

---

## 🥗 2. Nutrition Screens (`screens/nutrition/`)

Files: `NutritionScreen.kt` & `NutritionViewModel.kt` (main dashboard), `MealHistoryScreen.kt` (route `nutrition/meal-history`), `BodyProgressScreen.kt` (route `nutrition/body-progress`), and `components/` (`FoodLoggerDrawer.kt`, `NutritionPlanEditorModal.kt`, `NutritionWizardView.kt` — route `nutrition/wizard`).

Displays macro goals, historical items logged, and provides multiple entry methods (Search database, Custom Food creation, and Dictation).

```
+-----------------------------------------------------+
| [   KCAL   ]     [  PROT  ]    [  CARB  ]   [ FAT ]  |
|   1820 / 2200     140/160g      200/240g     60/75g |
+-----------------------------------------------------+
| MEALS LIST (Breakfast, Lunch, Dinner, Snacks)       |
| -> Eggs (2x) - 14g Prot / 12g Fat / 0g Carb        |
+-----------------------------------------------------+
| VOICE BAR: [ "Dictate meal..." (Tap to speak) ]    |
+-----------------------------------------------------+
```

### 2.1 UI Component breakdown

1.  **Macro Indicator Header:**
    *   Draws progress bars or rings for Calories, Proteins, Carbohydrates, and Fats.
    *   Colors match standard conventions: Calories = Purple/Red, Protein = Blue, Carbs = Orange/Yellow, Fats = Green/Teal.
2.  **Meal Grouping List:**
    *   Accordion list categorized by: `BREAKFAST`, `LUNCH`, `DINNER`, `SNACK`.
    *   Each row shows the food name, quantity in grams, and detailed macro content. Slidable to delete or copy.
3.  **Voice Bar (Speech Dictation Component):**
    *   A microphone button triggering Android's SpeechRecognizer overlays.
    *   As user speaks, raw words are captured and passed directly to the `VoiceNutritionRecognizer` parser (`data/voice/`).
4.  **Database Search Panel:**
    *   A sliding panel sheet showing results from the FTS4 database (`global_foods_fts`).
    *   Uses autocomplete suggestions based on historical mapping preferences (`learned_resolutions` table).
5.  **`FoodLoggerDrawer` (quick-add drawer):** Fast manual entry of foods/quantities.
6.  **`NutritionPlanEditorModal` / `NutritionWizardView`:** Plan creation/editing wizard and goal configuration.
7.  **`MealHistoryScreen` / `BodyProgressScreen`:** Historical meal logs and body-composition progress tracking.

### 2.2 State Flows (`NutritionViewModel.kt`)

*   **State Exposed:** `nutritionState: StateFlow<NutritionState>`
    *   `NutritionState` contains: `dailyLog` (active consumed list), `macroGoals`, `searchQuery`, `searchResults`, `voiceRecognizerState`.
*   **User Interactions / Events:**
    *   `onLogFoodItem(foodItem, quantityGrams, mealType)`: Saves logged macros to local database.
    *   `onDeleteFoodLog(logId)`: Deletes entry.
    *   `onVoiceInputReceived(spokenText)`: Triggers semantic description parser to extract macros from voice lines.

---

## 🏋️‍♂️ 3. Live Workout Screen (`screens/workout/WorkoutScreen.kt` & `WorkoutViewModel.kt`)

Tracks set performance, manages rest intervals, controls voice dictation, and calculates dynamic plate structures. Route `workout/{programId}/{sessionId}`; pre-workout gate at `readiness-gate/{programId}/{sessionId}`.

```
+-----------------------------------------------------+
| Active Exercise: Barbell Bench Press                |
| Target: 4 sets x 8 reps @ 80kg                      |
+-----------------------------------------------------+
| Set Pager (Swipeable cards representing sets)       |
| Set 1: [ 80kg ]  x  [ 8 reps ]  RPE: [ 8.5 ]  [Log] |
+-----------------------------------------------------+
| Barbell Plate Layout Visualizer: (||[20][10]====||) |
+-----------------------------------------------------+
| Adaptive Rest Clock: 02:14                          |
+-----------------------------------------------------+
```

### 3.1 UI Component breakdown (files in `screens/workout/` and `screens/workout/components/`)

1.  **`ReadinessGateScreen.kt` (Pre-workout Verification):**
    *   Shows a gatekeeper UI before loading the workout. Checks if targeted muscles are under-recovered or if any pre-workout discomforts are logged. User must check boxes to confirm they are ready.
2.  **Active Exercise Header:**
    *   Shows the current exercise, equipment, canonical involved muscles, target goals, and historical sets.
3.  **`components/SetExecutionCard.kt` + `WorkoutSetPager.kt` (Set Pager):**
    *   Swipeable horizontal cards representing planned sets.
    *   Contains input boxes for weight, reps, and RPE selector (1-10 slider).
    *   Pressing **"Log"** saves the set, starts the rest timer, triggers TTS audio cues ("Set 1 complete, rest 2 minutes"), and automatically switches to the next set card.
4.  **`BarbellPlateVisualizer.kt`:**
    *   Draws a graphical barbell showing exactly what plates (20kg, 10kg, 5kg, etc.) should be loaded on each side of the bar to match the target set weight (math from `domain/calculations/PlateCalculator.kt`).
5.  **`WorkoutAdaptiveRest.kt` + `components/WorkoutRestOverlay.kt` (Timer overlay):**
    *   Exposes a circular countdown clock.
    *   Features skip, pause, and add/subtract time buttons (+30s / -30s).
    *   Blinks and plays audio bells when the timer reaches 0.
6.  **Supporting workout files:** `WorkoutCoachMessages.kt`, `WorkoutGuidanceComponents.kt`, `WorkoutContextComponents.kt`, `WorkoutContinuityComponents.kt`, `WorkoutSetTransitionComponents.kt`, `WorkoutRestComponents.kt`, `WorkoutRestRecoveryModel.kt`, `components/WorkoutRoadmapBar.kt`, `components/WorkoutCommandDock.kt`, `components/WorkoutWarmupCards.kt`, `components/SetAdjustmentOverlay.kt`, `components/VolumeAdvanceModal.kt`, `components/ProtectedWorkoutBottomSheet.kt`, `components/MinimalMuscleSlider.kt`, `components/WorkoutReadinessSheet.kt`, `WorkoutVoiceInput.kt`/`WorkoutVoiceUi.kt`, `WorkoutShareService.kt`.
7.  **Pure-rule helpers (unit-tested):** `WorkoutStepRules.kt`, `WorkoutEditingRules.kt`, `WorkoutLoadSuggestionRules.kt`, `WorkoutUnsavedChangesRules.kt`, `WorkoutSessionContracts.kt`, `WorkoutReadinessBridge.kt`, `WorkoutFeedbackModels.kt`, `WorkoutVisualModels.kt`, `WorkoutUiCommon.kt`.

### 3.2 State Flows (`WorkoutViewModel.kt`)

*   **State Exposed:** `workoutState: StateFlow<WorkoutState>`
    *   `WorkoutState` contains: `activeSession`, `currentExerciseIndex`, `currentSetIndex`, `loggedSets`, `ongoingWorkoutState` (cached to the `ongoing_workout` table so state survives app kills), `restTimerSecondsRemaining`, `isVoiceActive`, `plateLayout`.
*   **Key Logic Engines Linked:**
    *   **Auto Load Adjustment:** If a user logs a set with RPE > target RPE, the VM triggers `WorkoutLoadSuggestionRules` to suggest a weight drop for the next set (e.g. "Target RPE exceeded. Drop weight to 75kg?").
    *   **Continuous Voice Controller:** Captures commands like "Log 80 by 8 reps RPE 9" to automate set inputs hands-free (via `services/workout/WorkoutContinuousVoiceEngine.kt`).

---

## 🧬 4. Auge Details & Sheets (`screens/auge/`)

Files: `ReadinessSheet.kt`, `PostExerciseSheet.kt`, `PostSessionSheet.kt`, `AugeViewModel.kt`.

Shows the physiological diagnostic overview from the AUGE Engine.

### 4.1 UI Component breakdown

1.  **Readiness Verdict Panel (`ReadinessSheet.kt`):**
    *   Large color badge showing readiness status: `GREEN` (Go heavy), `YELLOW` (Caution, leave reps in reserve), `RED` (Deload or rest).
    *   Lists limiting physiological factors (e.g. "Primary Limiter: Low Spinal Battery (20%) due to deadlifts 24h ago").
2.  **Articular Readiness Breakdown:**
    *   List view mapping joints to estimated recovery times (TTC). Shows joint safety flags.
3.  **Post-Exercise / Post-Session Questionnaire sheets (`PostExerciseSheet.kt`, `PostSessionSheet.kt`):**
    *   Interactive panels loaded after saving logs, using 1-5 sliders for DOMS, joint pain checkbox triggers, and quality checks. Persisted to the `auge_feedback` table.

---

## 📁 5. Additional Feature Screens

1.  **Program Management (`screens/programs/` & `screens/programdetail/`):**
    *   **Programs List (`ProgramsScreen.kt` + `ProgramsViewModel.kt`):** Vertical scroll of active, historical, and premium templates.
    *   **Program Detail View (`ProgramDetailScreen.kt` + `ProgramDetailViewModel.kt` + `components/`):** A calendar-like microcycle view (`MainTab`). Includes volume charts displaying accumulated sets per muscle group per week.
    *   **Plantillas y protocolos:** No existe una ruta independiente `ProtocolsView`. El browse, preview y apply viven en `MacrocycleEditor` dentro de `TemplatesProtocolsSheet`; `ProgramProtocolEngine` es el compilador único compartido por ese flujo.
2.  **Session Editor (`screens/sessioneditor/`):**
     *   **Files:** `SessionEditorScreen.kt`, `SessionEditorViewModel.kt`, `SessionEditorContracts.kt`, `SessionEditorRulesEngine.kt` (pure rules, unit-tested), `SessionEditorDragController.kt`, `SessionEditorScrollRenderer.kt`, `VariantFlowSheet.kt`, `components/` (`GroupEditorCard.kt`, `ExerciseEditorCard.kt`, `SupersetGroupEditorCard.kt`).
     *   **Drag-and-Drop Builder (2026-08-06 fix):** Window-coords (`boundsInWindow`) frozen at drag start but compensated on scroll via `applyScrollDelta` (-delta for content-up). Uses `LazyListState` `layoutInfo` + `onGloballyPositioned` for visible items only; virtualized items that enter viewport via auto-scroll are registered via `registerExerciseBoundsDuringDrag` / `registerPartBoundsDuringDrag` and expand `frozen*` zones with `mergeBounds`. Auto-scroll triggers at 96dp from viewport edge at 18dp per 16ms via `listState.scroll { scrollBy(delta) }` and shifts frozen rects. Header drag fixed: `GroupEditorCard` now respects `dragOffsetY` (`translationY = if(isDragging) dragOffsetY else 0f`).
     *   **Supersets & Drop-sets:** UI to bundle adjacent exercises into supersets (creates visual brackets connecting cards).
     *   **Parameter Editor:** Inputs to customize default rest times, RPE targets, and reps.
3.  **WikiLab Encyclopedia (`screens/wikilab/`):**
    *   **Home:** `WikiLabHomeScreen.kt` (route `wikilab`) + `WikiLabScreen.kt`.
    *   **Anatomical Explorer:** Grid of categories: Muscles (`MuscleCategoryScreen.kt`, `MuscleGroupDetailScreen.kt`), Joints (`JointsListScreen.kt`, `JointDetailScreen.kt`), Tendons (`TendonDetailScreen.kt`), Movement Patterns (`PatternsListScreen.kt`, `MovementPatternDetailScreen.kt`).
    *   **Exercise Library:** `ExerciseDetailScreen.kt` + `ExerciseDetailComposables.kt`, `CustomExerciseCreatorScreen.kt`, `WikiLabExerciseSupport.kt`, `WikiLabInsightSupport.kt`.
    *   **Biomechanics & Concepts:** `BiomechanicsScreen.kt` + `WikiLabBiomechVisuals.kt`, `TrainingConceptsScreen.kt`.
    *   **Detail Pages:** Show structural descriptions, origins/insertions, and related exercises.
4.  **Settings & Overrides (`screens/settings/`):**
    *   **Hub:** `SettingsScreen.kt` + `SettingsViewModel.kt` + `components/` (`SettingsCategoryRow.kt`, `SettingsListItems.kt`, `SettingsProfileHeader.kt`).
    *   **Subscreens (one route each):** `SettingsGeneralScreen.kt`, `SettingsProfileScreen.kt`, `SettingsNutritionScreen.kt`, `SettingsTrainingScreen.kt`, `SettingsAugeScreen.kt`, `SettingsNotificationsScreen.kt`, `SettingsDataScreen.kt`.
    *   **Manual Battery Sliders:** 0-100% sliders to manually force the Muscular, CNS, or Spinal batteries to a specific value (overriding the engine).
    *   **Theme & Haptics:** Toggles for Dark/Light mode and haptic engine intensity.
    *   **Data Backup:** Export/Import JSON database files (via `data/db/DatabaseBackupHelper.kt`).
5.  **Learn / Education (`screens/learn/`):**
    *   `LearnHomeScreen.kt` (course list), `LearnCourseScreen.kt`, `LearnReaderScreen.kt`, `LearnQuizScreen.kt`, `LearnBadgeScreen.kt`. Content comes from `data/learn/` and `LearnRepository`.
6.  **Profile & Social (`screens/profile/` & `screens/competitions/`):**
    *   **Profile (`ProfileScreen.kt`):** Tracks user body weight, lifting levels, and milestones.
    *   **Competitions (`CompetitionScreen.kt`):** Leaderboards tracking total volume lifted or readiness consistency compared to other users.

---

## 🎨 6. Shared UI System (`ui/`)

*   **Theme (`ui/theme/`):** `Color.kt`, `Theme.kt`, `Type.kt` — pitch-black backgrounds with neon yellow/cyan/magenta accents; high-contrast dark mode first.
*   **Shared components (`ui/components/`):** `SharedComponents.kt`, `KpknSnackbar.kt`, custom icon set in `ui/components/icons/`.
*   **Localization (`ui/locale/LocaleManager.kt`):** ES/EN string resolution.
*   **Glassmorphism:** Haze library effects wired in `MainActivity.kt`.

---

## Video technique

Profile and the media viewer open the technique hub/analysis destinations. The hub groups analyzed exercises and only schedules old videos on explicit request. Gallery import uses the system photo picker, the catalog's TECHNIQUE/SINGLE result contract, optional load and a persisted import draft. Capture is hosted full-screen by the live workout rather than a separate navigation route.

Analysis exposes available repetition, timing, ROM and calibrated plate metrics with the synchronized overlay player. Trends require exact configuration identity for load–velocity estimates; comparison aligns the first repetition and preserves each video's coordinate scale. ViewModels publish read-only StateFlow and keep media/sidecar work off the UI thread. Validation and device limitations are recorded in `video-technique/ESTADO.md`; these mappings do not assert that acceptance gates passed.

## 🛠️ 7. Swift (iOS) Presentation Parity Strategy

To achieve visual and functional parity in SwiftUI (`ios-native/KPKNFit/`):

1.  **Home Rings (SwiftUI Canvas):**
    *   Use SwiftUI `Path` and `trim(from:to:)` to draw overlapping concentric rings. Wrap this in a custom view named `MyRingsView.swift`.
2.  **Live Activities / Lock Screen timers:**
    *   Implement **ActivityKit** in iOS to show the rest timer as a Live Activity. This replaces Android's `WorkoutRestForegroundService` notification widget.
3.  **State Management (UDF):**
    *   Implement `@Observable` (iOS 17+) or `ObservableObject` ViewModels. Expose `@Published` state variables matching the Android `StateFlow` structures exactly.
4.  **Navigation:**
    *   Map each `KpknRoute` to a SwiftUI `NavigationStack` destination enum, keeping the same route hierarchy (see §0).
