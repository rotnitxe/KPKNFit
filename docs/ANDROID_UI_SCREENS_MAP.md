# UI Screens Mapping: KPKN Fit Android App (For iOS SwiftUI Porting)

This document provides a detailed breakdown of the user interface (UI) screens, Jetpack Compose layouts, ViewModels, and state interactions of the native Android app. Use this as a direct reference to construct matching SwiftUI Views and ViewModels in Xcode.

---

## 🏠 1. Home Screen (`HomeScreen.kt` & `HomeViewModel.kt`)

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

### 1.1 UI Component breakdown
1.  **`HomeHeaderSection`:**
    *   Greets user based on daytime.
    *   Displays active program indicators, microcycle number, and a deload notice if `shouldSuggestAutoDeload` is true in `AugeSnapshot`.
2.  **`HomeRingsSection` (Concentric Rings Canvas):**
    *   Uses a custom canvas to draw 3 concentric progress rings:
        *   **Outer (Green/Red):** Muscular Battery (overall muscle recovery score).
        *   **Middle (Blue):** CNS Battery (neurological/fatigue score).
        *   **Inner (Orange/Purple):** Spinal Battery (structural score).
    *   Displays overall readiness percentage in the center of the rings, with action recommendations (e.g. "Optimal Readiness: volume normal and good execution").
3.  **`HomeSessionSection`:**
    *   Loads the scheduled session template for the day.
    *   If a session is active, it shows a blinking "Active Session in Progress" banner.
    *   If there is unresolved workout feedback, it shows a warning sheet card urging the user to fill out the recovery feedback.
4.  **`HomeCardsSection`:**
    *   Grid items acting as gateways to `Nutrition`, `WikiLab`, `Profile`, and `Settings`.
5.  **`HomeCornersSection`:**
    *   A compact utility area with water trackers and motivational triggers.

### 1.2 State Flows (`HomeViewModel.kt`)
*   **State Exposed:** `homeState: StateFlow<HomeState>`
    *   `HomeState` contains: `activeProgram`, `currentSession`, `augeSnapshot`, `pendingQuestionnaire`, `isLoading`.
*   **User Interactions / Events:**
    *   `onWaterIncrement()`: Increments water count.
    *   `onRefreshAuge()`: Force recalculates Auge snapshot decay factors.
    *   `onStartSession(sessionId)`: Launches the live workout tracker.

---

## 🥗 2. Nutrition Screen (`NutritionScreen.kt` & `NutritionViewModel.kt`)

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
    *   As user speaks, raw words are captured and passed directly to the `VoiceNutritionRecognizer` parser.
4.  **Database Search Panel:**
    *   A sliding panel sheet showing results from the FTS4 database.
    *   Uses autocomplete suggestions based on historical mapping preferences (`learned_resolutions`).

### 2.2 State Flows (`NutritionViewModel.kt`)
*   **State Exposed:** `nutritionState: StateFlow<NutritionState>`
    *   `NutritionState` contains: `dailyLog` (active consumed list), `macroGoals`, `searchQuery`, `searchResults`, `voiceRecognizerState`.
*   **User Interactions / Events:**
    *   `onLogFoodItem(foodItem, quantityGrams, mealType)`: Saves logged macros to local database.
    *   `onDeleteFoodLog(logId)`: Deletes entry.
    *   `onVoiceInputReceived(spokenText)`: Triggers semantic description parser to extract macros from voice lines.

---

## 🏋️‍♂️ 3. Live Workout Screen (`WorkoutScreen.kt` & `WorkoutViewModel.kt`)

Tracks set performance, manages rest intervals, controls voice dictation, and calculates dynamic plate structures.

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

### 3.1 UI Component breakdown
1.  **`ReadinessGateScreen` (Pre-workout Verification):**
    *   Shows a gatekeeper UI before loading the workout. Checks if targeted muscles are under-recovered or if any pre-workout discomforts are logged. User must check boxes to confirm they are ready.
2.  **Active Exercise Header:**
    *   Shows the current exercise, equipment, canonical involved muscles, target goals, and historical sets.
3.  **`SetExecutionCard` (Set Pager):**
    *   Swipeable horizontal cards representing planned sets.
    *   Contains input boxes for weight, reps, and RPE selector (1-10 slider).
    *   Pressing **"Log"** saves the set, starts the rest timer, triggers TTS audio cues ("Set 1 complete, rest 2 minutes"), and automatically switches to the next set card.
4.  **`BarbellPlateVisualizer`:**
    *   Draws a graphical barbell showing exactly what plates (20kg, 10kg, 5kg, etc.) should be loaded on each side of the bar to match the target set weight.
5.  **`WorkoutAdaptiveRest` (Timer overlay):**
    *   Exposes a circular countdown clock.
    *   Features skip, pause, and add/subtract time buttons (+30s / -30s).
    *   Blinks and plays audio bells when the timer reaches 0.

### 3.2 State Flows (`WorkoutViewModel.kt`)
*   **State Exposed:** `workoutState: StateFlow<WorkoutState>`
    *   `WorkoutState` contains: `activeSession`, `currentExerciseIndex`, `currentSetIndex`, `loggedSets`, `ongoingWorkoutState` (cached to database so state survives app kills), `restTimerSecondsRemaining`, `isVoiceActive`, `plateLayout`.
*   **Key Logic Engines Linked:**
    *   **Auto Load Adjustment:** If a user logs a set with RPE > target RPE, the VM triggers `WorkoutLoadSuggestionRules` to suggest a weight drop for the next set (e.g. "Target RPE exceeded. Drop weight to 75kg?").
    *   **Continuous Voice Controller:** Captures commands like "Log 80 by 8 reps RPE 9" to automate set inputs hands-free.

---

## 🧬 4. Auge Details & Sheets (`ReadinessSheet.kt`)

Shows the physiological diagnostic overview from the AUGE Engine.

### 4.1 UI Component breakdown
1.  **Readiness Verdict Panel:**
    *   Large color badge showing readiness status: `GREEN` (Go heavy), `YELLOW` (Caution, leave reps in reserve), `RED` (Deload or rest).
    *   Lists limiting physiological factors (e.g. "Primary Limiter: Low Spinal Battery (20%) due to deadlifts 24h ago").
2.  **Articular Readiness Breakdown:**
    *   List view mapping joints to estimated recovery times (TTC). Shows joint safety flags.
3.  **Post-Exercise / Post-Session Questionnaire sheets:**
    *   Interactive panels loaded after saving logs, using 1-5 sliders for DOMS, joint pain checkbox triggers, and quality checks.

---

## 📁 5. Additional Feature Screens

1.  **Program Management (`programs` & `programdetail`):**
    *   **Programs List:** Vertical scroll of active, historical, and premium templates.
    *   **Program Detail View:** A calendar-like microcycle view. Includes volume charts displaying accumulated sets per muscle group per week.
2.  **Session Editor (`sessioneditor`):**
    *   **Drag-and-Drop Builder:** Interface for sequencing exercises within a session.
    *   **Supersets & Drop-sets:** UI to bundle adjacent exercises into supersets (creates visual brackets connecting cards).
    *   **Parameter Editor:** Inputs to customize default rest times, RPE targets, and reps.
3.  **WikiLab Encyclopedia (`wikilab`):**
    *   **Anatomical Explorer:** Grid of categories: Muscles, Joints, Tendons, Movement Patterns.
    *   **Detail Pages:** Shows 3D-like structural descriptions, origins/insertions, and related exercises.
4.  **Settings & Overrides (`settings`):**
    *   **Manual Battery Sliders:** 0-100% sliders to manually force the Muscular, CNS, or Spinal batteries to a specific value (overriding the engine).
    *   **Theme & Haptics:** Toggles for Dark/Light mode and haptic engine intensity.
    *   **Data Backup:** Export/Import JSON database files.
5.  **Profile & Social (`profile` & `competitions`):**
    *   **Profile:** Tracks user body weight, lifting levels, and milestones.
    *   **Competitions:** Leaderboards tracking total volume lifted or readiness consistency compared to other users.

---

## 🛠️ 6. Swift (iOS) Presentation Parity Strategy

To achieve visual and functional parity in SwiftUI:

1.  **Home Rings (SwiftUI Canvas):**
    *   Use SwiftUI `Path` and `trim(from:to:)` to draw overlapping concentric rings. Wrap this in a custom view named `MyRingsView.swift`.
2.  **Live Activities / Lock Screen timers:**
    *   Implement **ActivityKit** in iOS to show the rest timer as a Live Activity. This replaces Android's `WorkoutRestForegroundService` notification widget.
3.  **State Management (UDF):**
    *   Implement `@Observable` (iOS 17+) or `ObservableObject` ViewModels. Expose `@Published` state variables matching the Android `StateFlow` structures exactly.
