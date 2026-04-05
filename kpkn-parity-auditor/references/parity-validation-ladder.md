# KPKN Parity Validation Ladder

Choose the lightest validation that can honestly support the parity claim being made.

## Level 0: Code audit only

Use when:

- the task is documentation, matrix updates, or gap classification
- no code changed

Evidence:

- traced PWA anchor
- traced Kotlin anchor
- explicit status and rationale

Do not claim:

- runtime parity
- persistence parity
- build readiness

## Level 1: Kotlin compile sanity

Use when:

- Kotlin code changed, but the slice is still narrow

Typical commands:

```powershell
cd android-native
.\gradlew.bat :app:compileDebugKotlin
```

Supports claims like:

- the edited Kotlin slice still compiles
- the audit fix did not obviously break the target module

## Level 2: Slice-safe validation

Use when:

- navigation, manifest, repository, persistence, or screen wiring changed

Typical commands:

```powershell
cd android-native
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
```

Use only the commands that the slice justifies.

## Level 3: Behavior evidence

Use when:

- logic or workflow parity is being claimed
- parser, AUGE, or save/resume behavior changed

Possible checks:

```powershell
npx tsc --noEmit
npm run test:nutrition-logging
cd android-native
.\gradlew.bat :app:compileDebugKotlin
```

Behavior evidence may also include:

- representative PWA vs Kotlin scenario comparison
- targeted smoke flows in the Android app
- updated or added unit tests where the repo already supports them

## Level 4: Cutover-ready evidence

Use when:

- the report is used for release or deprecation decisions
- a major slice is being marked ready to leave the PWA behind

Expected evidence:

- slice-safe build validation
- task-completion verification
- remaining gaps explicitly documented
- stale docs called out or updated

## Area-specific heuristics

### Navigation and route wiring

- Minimum useful check:
  - `:app:assembleDebug`
- Manual evidence:
  - enter the flow through the intended route
  - verify back behavior and required arguments

### AUGE, readiness, recovery

- Minimum useful check:
  - `:app:compileDebugKotlin`
- Better evidence:
  - targeted scenario comparison against PWA behavior

### Program, session, workout flow

- Minimum useful check:
  - `:app:compileDebugKotlin`
- Widen when repository or models changed:
  - `:app:assembleDebug`
  - `:app:testDebugUnitTest`
- Manual evidence:
  - create or edit a program
  - open a session
  - start and finish a workout if the touched slice reaches execution

### Nutrition AI

- Minimum useful check:
  - `npx tsc --noEmit` when PWA oracle changed
  - `npm run test:nutrition-logging` for parser semantics
  - Kotlin compile for `android-native`
- Manual evidence:
  - unresolved vs estimated food handling
  - save path integrity

### Persistence and data models

- Minimum useful check:
  - `:app:assembleDebug`
- Better evidence:
  - confirm the stored state can be read back through the relevant screen or repository path

## Anti-patterns

- Running the full repo validation suite for a docs-only audit
- Claiming parity after only a compile
- Updating parity docs without checking live code
- Skipping PWA reference checks when the whole point is behavior comparison
