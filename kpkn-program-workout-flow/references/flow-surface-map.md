# Flow Surface Map

## Canonical Intent Source

Use `program-editor-session-workout-migration.md` as the main migration brief for this feature family. It captures the intended user flow, scope boundaries, phased implementation order, and explicit exclusions.

## PWA Behavior Sources

Load only the files relevant to the current task.

### High-level wiring

- `App.tsx`

### Program creation and editing

- `components/ProgramEditor.tsx`
- `components/program-editor/ProgramEditorAdvanced.tsx`

### Session editing

- `components/SessionEditor.tsx`
- `components/AdvancedExercisePickerModal.tsx`
- `components/session-editor/SessionEditorHeader.tsx`

### Live workout and finish flow

- `components/WorkoutSession.tsx`
- `components/FinishWorkoutModal.tsx`
- `components/workout/WorkoutDrawer.tsx`

## Android Target Surfaces

### Program editor

- `android-native/app/src/main/java/com/example/kpkn/screens/programeditor/ProgramEditorScreen.kt`
- `android-native/app/src/main/java/com/example/kpkn/screens/programeditor/ProgramEditorViewModel.kt`
- `android-native/app/src/main/java/com/example/kpkn/screens/programeditor/ProgramCreatorWizard.kt`
- `android-native/app/src/main/java/com/example/kpkn/screens/programeditor/SplitSelectorSheet.kt`

### Session editor

- `android-native/app/src/main/java/com/example/kpkn/screens/sessioneditor/SessionEditorScreen.kt`
- `android-native/app/src/main/java/com/example/kpkn/screens/sessioneditor/SessionEditorViewModel.kt`

### Workout

- `android-native/app/src/main/java/com/example/kpkn/screens/workout/WorkoutScreen.kt`
- `android-native/app/src/main/java/com/example/kpkn/screens/workout/WorkoutViewModel.kt`

## Shared Android Dependencies

### Models

- `android-native/app/src/main/java/com/example/kpkn/data/models/Program.kt`
- `android-native/app/src/main/java/com/example/kpkn/data/models/Session.kt`
- `android-native/app/src/main/java/com/example/kpkn/data/models/WorkoutLog.kt`
- `android-native/app/src/main/java/com/example/kpkn/data/models/ExerciseMuscleInfo.kt`

### Repository and persistence

- `android-native/app/src/main/java/com/example/kpkn/data/repository/ProgramRepository.kt`
- `android-native/app/src/main/java/com/example/kpkn/data/db/Entities.kt`
- `android-native/app/src/main/java/com/example/kpkn/data/db/Daos.kt`
- `android-native/app/src/main/java/com/example/kpkn/data/db/KpknDatabase.kt`

### Supporting datasets and domain helpers

- `android-native/app/src/main/java/com/example/kpkn/data/exercises/ExerciseDatabase.kt`
- `android-native/app/src/main/java/com/example/kpkn/data/splits/SplitTemplates.kt`
- `android-native/app/src/main/java/com/example/kpkn/domain/training/VolumeCalculator.kt`

### Navigation and app wiring

- `android-native/app/src/main/java/com/example/kpkn/navigation/Navigation.kt`
- `android-native/app/src/main/java/com/example/kpkn/MainActivity.kt`

## Behavior-Critical Invariants

### Program editor

- Existing program IDs and nested structure IDs should survive normal edits.
- Split changes should not silently wipe unrelated data unless that reset is explicit and user-visible.
- Wizard defaults should produce a valid persisted `Program`, not just a temporary UI object.

### Session editor

- Saving must update the correct session inside the correct program branch.
- Added exercises must remain compatible with `ExerciseDatabase.kt` lookups where relevant.
- Set cloning should preserve sensible defaults rather than resetting every field blindly.

### Workout flow

- `startWorkout()`, `updateOngoingWorkout()`, `addWorkoutLog()`, and `clearOngoingWorkout()` must stay coherent.
- `Session.allExercises()` flattening rules must remain stable if the screen logic depends on them.
- Finish-workout behavior is not complete unless history is written and ongoing state is cleared.

## Safe Reading Order

For most tasks, this order keeps context tight:

1. Android screen or ViewModel being changed
2. `ProgramRepository.kt`
3. Relevant model file
4. `Navigation.kt` or `MainActivity.kt` if routing is involved
5. Matching PWA file only if behavior or UX intent is unclear
6. `program-editor-session-workout-migration.md` if the task spans multiple areas
