# Flow Validation Ladder

Pick the smallest validation that meaningfully exercises the risk you introduced.

## Fastest Useful Checks

### Local screen copy, spacing, or visual tweaks

Run:

```powershell
cd android-native
.\gradlew.bat :app:compileDebugKotlin
```

Use when:
- the change stays inside one screen file
- no model, repository, or route contract changed

### ViewModel logic or local state updates

Run:

```powershell
cd android-native
.\gradlew.bat :app:compileDebugKotlin
```

Expand manually if the change affects:
- session lookup
- split application
- timer behavior
- finish-workout behavior

## Medium Checks

### Navigation, screen wiring, or repository integration

Run:

```powershell
cd android-native
.\gradlew.bat :app:assembleDebug
```

Use when touching:
- `MainActivity.kt`
- `Navigation.kt`
- `ProgramRepository.kt`
- model/entity conversions
- screen creation arguments

### Shared model or persistence edits

Run:

```powershell
cd android-native
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
```

Use when touching:
- `Program.kt`
- `Session.kt`
- `WorkoutLog.kt`
- Room entities or DAOs
- workout history write paths

## Manual Smoke Recommendations

Use these when the build passes but the flow risk is still meaningful.

### Program editor smoke

- create a new program
- save it
- land back on detail or expected destination
- reopen it and verify name, split, and structure changes persisted

### Session editor smoke

- open a session from a program
- add or edit an exercise
- change a set
- save
- reopen the session and verify the right branch was updated

### Workout smoke

- start a workout
- log at least one set
- verify the timer starts or stops correctly
- finish the workout
- confirm history writes and ongoing workout clears

## Escalate Validation When

- route args changed
- save semantics changed
- nested IDs are created, duplicated, or replaced
- `ProgramRepository` logic changed
- finish-workout logic changed
- workout history, ongoing workout, or timer behavior changed

## Avoid

- running every repo check for a tiny Compose-only tweak
- stopping at compile when persistence or finish-workout behavior changed
- claiming parity without checking saved data or navigation behavior
