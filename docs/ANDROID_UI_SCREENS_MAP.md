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

The Home Screen is the default landing page. It orchestrates user state, highlights daily tasks, and provides entrance gateways to the rest of the application.

```
+-----------------------------------------------------+
| Greeting, Vitals Alert & Active Program Status      |
+-----------------------------------------------------+
|                                                     |
|                 (   MY RINGS   )                    |
|          Muscular, CNS, Spinal Batteries            |
|                                                     |
+-----------------------------------------------------+
| Today's Workout Session Launcher / Deload Alert     |
+-----------------------------------------------------+
| Compact WikiLab Preview | Water Log & Shortcuts     |
+-----------------------------------------------------+
```

### 1.1 UI Component breakdown (all in `screens/home/`)

1.  **`HomeHeaderSection.kt`:**
    *   Greets user based on daytime.
    *   Displays active program indicators, microcycle number, and a deload notice if `shouldSuggestAutoDeload` is true in `AugeSnapshot`.
2.  **`HomeRingsSection.kt` (Concentric Rings Canvas):**
    *   Uses a custom canvas to draw 3 concentric progress rings:
        *   **Outer (Green/Red):** Muscular Battery (overall muscle recovery score).
        *   **Middle (Blue):** CNS Battery (neurological/fatigue score).
        *   **Inner (Orange/Purple):** Spinal Battery (structural score).
    *   Displays overall readiness percentage in the center of the rings, with action recommendations (e.g. "Optimal Readiness: volume normal and good execution").
3.  **`HomeSessionSection.kt`:**
    *   Loads the scheduled session template for the day.
    *   If a session is active, it shows a blinking "Active Session in Progress" banner.
    *   If there is unresolved workout feedback, it shows a warning sheet card urging the user to fill out the recovery feedback.
4.  **`HomeCardsSection.kt` / `HomeProgramsSection.kt`:**
    *   Grid items acting as gateways to `Nutrition`, `WikiLab`, `Profile`, and `Settings`; active program quick launcher (`CreateProgramCard.kt` when no program exists).
5.  **`HomeCornersSection.kt`:**
    *   A compact utility area with water trackers and motivational triggers.
6.  **`HomeWikiLabSection.kt` / `AnimatedIconBackground.kt`:**
    *   Compact WikiLab preview and decorative animated backgrounds.

### 1.2 State Flows (`HomeViewModel.kt`)

*   **State Exposed:** `homeState: StateFlow<HomeState>`
    *   `HomeState` contains: `activeProgram`, `currentSession`, `augeSnapshot`, `pendingQuestionnaire`, `isLoading`.
*   **User Interactions / Events:**
    *   `onWaterIncrement()`: Increments water count.
    *   `onRefreshAuge()`: Force recalculates Auge snapshot decay factors.
    *   `onStartSession(sessionId)`: Launches the live workout tracker.

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
2.  **Session Editor (`screens/sessioneditor/`):**
    *   **Files:** `SessionEditorScreen.kt`, `SessionEditorViewModel.kt`, `SessionEditorContracts.kt`, `SessionEditorRulesEngine.kt` (pure rules, unit-tested), `VariantFlowSheet.kt`, `components/`.
    *   **Drag-and-Drop Builder:** Interface for sequencing exercises within a session.
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
