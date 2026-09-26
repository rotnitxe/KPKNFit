# T-005 / v1 — independent verification of T-004 availability foundation

**TASK_ID / CONTRACT_VERSION / ROLE:** T-005 / v1 / VERIFIER  
**STATUS:** FAILED — candidate compile blocker and a confirmed-suggestion persistence defect; no acceptance decision issued.  
**INPUT_BASELINE:** `HEAD=ac3ff1c88d8843aca986d0b5600351b8ff1303cc` plus protected pre-existing working-tree WIP, as recorded in `T-001-baseline.json`.  
**OUTPUT_CANDIDATE:** T-004 submitted candidate. All 15 T-004 source/test hashes matched its manifest before and after this verification. Full manifest appears below.

## ACTUAL_ACTIONS

- Read T-004's report and abbreviated WIP-relative delta ledger, T-003 v1/v2 architecture decisions, T-002, and T-001 baseline/report.
- Independently traced the changed category model, projection, actual native and fixed-recipe consumers, Settings load materializer path, setup patch/new-draft/restore/footprint paths, and JSON backup consumers. Inspected the claimed regression tests as source; did not treat their assertions as execution evidence.
- Checked process ownership before the build. The only Java processes were the baseline resident Gradle daemon PID 22064 and its Kotlin compiler daemon PID 23344; no active wrapper/task process was present.
- Ran one authorized separated Kotlin test-source compilation attempt (owned launcher PID 2164) with stdout/stderr redirected to T-005 logs. It exited normally with a candidate test-compilation error. The test task was not run after this diagnosed blocker.
- Rechecked processes and all 15 candidate hashes after the build. No Gradle compile/test process survived; the same two baseline daemons remained. No source/test edits were made.
- Ran `git diff --check`; it returned without whitespace errors (only working-copy LF/CRLF warnings).

## ARTIFACTS

- `T-005-report.md` — this independent verification.
- `T-005-compile.stdout.log` and `T-005-compile.stderr.log` — complete captured output from the one compile attempt.
- Read-only inputs: `T-001-baseline.json`, `T-001-report.md`, `T-002-report.md`, `T-003-report.md`, `T-004-report.md`, `T-004-wip-diff.md`, `T-004-baseline.log`, and `T-004-final.log`.

## ACCEPTANCE_EVIDENCE

- **AC-1 — NOT_RUN (static contract looks consistent):** `EquipmentAvailability` is nullable at Settings/TrainingOptions boundaries and has exactly eleven closed enum categories. `effectiveEquipment` branches first on non-null availability, always emits `bodyweight`, maps only the eleven canonical tokens, and emits neither `general_gym`, `free_weights`, nor `machine_config:*`; explicit empty therefore projects to only `bodyweight`. The null branch retains the prior normalized legacy/inventory logic. `EquipmentAvailabilityTest` and `EffectiveEquipmentContractTest` assert this, but no assertions ran.
- **AC-2 — NOT_RUN (static production path looks consistent):** `SetupWizardViewModel.effectiveEquipmentIds` supplies the shared projection to candidates, native generation, and the fixed-recipe guard. `SimpleCyclePersonalizer.personalize` calls `validateForSelection`; the native filter still requires `equipmentId == "machine"` and only visits approved curated configurations. Exact machine IDs are enforced for null-availability legacy inventory, while categorical `MACHINES` works with null or unrelated non-null stock. `missingFixedRecipeEquipment` still requires `machine_config:<exact-id>` for authored machine recipes, and explicit availability cannot carry the legacy `general_gym` bypass. No prescription-generation code is in the listed T-004 delta. Production-path tests exist but did not run.
- **AC-3 — NOT_RUN (static precedence is correct):** `Settings.resolvedEquipmentInventory()` returns sanitized explicit inventory first; otherwise any non-null availability returns empty/unknown inventory; otherwise it retains the legacy bar/plate fallback. It does not mutate raw Settings fields. `SettingsEquipmentInventoryResolutionTest` exercises the real Settings resolver and `PlanMaterializer` for category-only pending loads, explicit stock, and legacy loads, but did not run.
- **AC-4 — FAIL:** Most static paths are present: the three-state patch uses `Set(EquipmentAvailability(emptySet()))` for explicit none; `SetupCommitCoordinator` applies it to the latest Settings row; new drafts seed Settings availability without declaring it; restored drafts are loaded rather than merged; null and empty differ in draft/Settings JSON; the footprint feeds the EQUIPMENT impact; and backup import serializes the Settings field. However, explicitly confirming an untouched suggestion is discarded by the builder (finding 2 below). The tests for patch/new-draft/restore/footprint/backup/replay did not run. Existing replay coverage is generic; no test directly asserts that replay of an availability-patched receipt leaves the current availability untouched.
- **AC-5 — FAIL:** Candidate identity is verified and `git diff --check` is clean, but the new test source does not compile, so no suites ran. In addition, the supplied T-004 WIP ledger explicitly abbreviates surrounding code and omits unchanged sections; the pre-edit hashes identify the old files but do not reconstruct their contents. Thus I cannot independently prove every omitted pre-existing WIP section was preserved. Scope details and exact hashes are below.

## VALIDATION

### Authorized compile attempt

Run from `android-native/`, with a 600000 ms budget and stdout/stderr redirected to the authorized T-005 logs:

```powershell
.\gradlew.bat --no-daemon --console=plain --warning-mode=summary compileBaseDebugUnitTestKotlin
```

**Exit: 1.** Gradle reached `:app:compileBaseDebugUnitTestKotlin` and failed compilation in the new `SetupAvailabilitySeedTest.kt` at lines 90, 110, 126, and 134 with `Unresolved reference 'SetupTrainingOptions'`. The alias exists as `com.example.kpkn.domain.onboarding.SetupTrainingOptions` in `SetupTrainingOptions.kt`; the test file is in `com.example.kpkn.screens.onboarding` and has no import for it. Main Kotlin compilation and test KSP completed; the failure is in test-source compilation. This file was absent in the T-001 baseline and is a T-004 addition, so this is a candidate-specific failure, not a demonstrated baseline failure.

### Unit-test attempt and XML totals

The authorized command was **NOT RUN** because the separated compile produced the diagnosed source-compilation blocker:

```powershell
.\gradlew.bat --no-daemon --console=plain --warning-mode=summary testBaseDebugUnitTest --tests *.EffectiveEquipmentContractTest --tests *.FixedRecipeEquipmentCompatibilityTest --tests *.SettingsEquipmentInventoryResolutionTest --tests *.SetupActivationContractTest --tests *.SettingsJsonBackupTest --tests *.EquipmentAvailabilityTest --tests *.SetupAvailabilitySeedTest
```

No test methods executed; there are no execution totals, skipped-test totals, named test failures, or XML totals attributable to T-005. This is **NOT RUN**, not a zero-test PASS.

### Prior timeout diagnosis

- `T-004-baseline.log` ends after `:app:bundleBaseDebugClassesToRuntimeJar`; `T-004-final.log` ends during main Kotlin compiler warnings. Neither log contains a test-task summary or test count.
- These logs show Gradle progressing through compilation, not a completed test task with only a stuck output pipe. They do not include a post-timeout process inventory, so they cannot prove whether a process survived those historical tool timeouts.
- T-005's separated compile produced a normal Gradle failure after reaching unit-test Kotlin compilation (`BUILD FAILED in 4m`), rather than a tool pipe timeout. Owned launcher PID 2164 exited. Afterward, the only Java processes were the two resident daemons already listed in T-001; no Gradle wrapper/task remained. A separate `cmd.exe` process had no Gradle-wrapper/compile/test command signature and was not stopped.
- `git diff --check`: exit 0; only LF/CRLF working-copy warnings were emitted.

## SCOPE_CHECK

- HEAD remained `ac3ff1c88d8843aca986d0b5600351b8ff1303cc`.
- The following candidate SHA-256 values matched T-004's manifest both before and after validation:

| Candidate path | SHA-256 |
|---|---|
| `android-native/app/src/main/java/com/example/kpkn/data/models/EquipmentAvailability.kt` | `97F1F6B32ED9128288DD8088521FEAABED8170D01753B0FCC1E482F23ACF538A` |
| `android-native/app/src/main/java/com/example/kpkn/data/models/Settings.kt` | `BA3306D0A3347A8E292D529F53C2EBA710915CA8336E2284DDEA99320102669A` |
| `android-native/app/src/main/java/com/example/kpkn/data/onboarding/SetupSettingsPatch.kt` | `C0240671634E1E3F2C57E12AF99A190F3BC0AC35375B38FA97633F98C93E219E` |
| `android-native/app/src/main/java/com/example/kpkn/domain/training/TrainingOptions.kt` | `18FABB2E1E01CA3C269A00A67FCD5489B7B4C05328A1E450B34209DEDC67E4B0` |
| `android-native/app/src/main/java/com/example/kpkn/domain/training/SimpleCyclePersonalizer.kt` | `87F9B42CE428B1D013E640726905D01353162F19D8996CF5316DC074D48EE2D1` |
| `android-native/app/src/main/java/com/example/kpkn/domain/onboarding/SetupStepGraph.kt` | `F5EB26E047602AF5DC23B20AE3B0F33BEC4B6851A536CA6B147BFE01ED295773` |
| `android-native/app/src/main/java/com/example/kpkn/screens/onboarding/SetupWizardModels.kt` | `A61AF84A9E3D1914AE66D4DDA2F4122250200DDF7AF999939EB21A80211EBE7D` |
| `android-native/app/src/main/java/com/example/kpkn/screens/onboarding/SetupWizardViewModel.kt` | `F5F72211EDEB6F44845CE23E3375405F08721E2D5223C1F762378DC819DB5849` |
| `android-native/app/src/test/java/com/example/kpkn/domain/training/EffectiveEquipmentContractTest.kt` | `415E6C015BF28863397EF030CC85B91266497A9BD64C5701199098850C58B136` |
| `android-native/app/src/test/java/com/example/kpkn/domain/training/FixedRecipeEquipmentCompatibilityTest.kt` | `8D58EF7BBFE327C245D8040CFCD4289070A3AF60027E7DE9BBEA09A1BF31F714` |
| `android-native/app/src/test/java/com/example/kpkn/data/models/SettingsEquipmentInventoryResolutionTest.kt` | `C01A7CAAAFB6F50CA85169F76F613141C90279FFDA8627A525C4EB8282E01B3C` |
| `android-native/app/src/test/java/com/example/kpkn/data/models/EquipmentAvailabilityTest.kt` | `8164647E9B1AC8333C95984D3ADADC913C78BFD5F6B3FEBAAD84F6CB30CC00FA` |
| `android-native/app/src/test/java/com/example/kpkn/data/onboarding/SetupActivationContractTest.kt` | `EB6DF1BB476C4616527CD227FBC7D0A0A701B7E71E04A8D2B371071E94A688C1` |
| `android-native/app/src/test/java/com/example/kpkn/data/settings/SettingsJsonBackupTest.kt` | `6B234AFA01F1C809234E4999DA4BA3980C4D00A0358E3AEA879298C7C62B70EE` |
| `android-native/app/src/test/java/com/example/kpkn/screens/onboarding/SetupAvailabilitySeedTest.kt` | `6AC6C444198678FE2544A88B541F40C74AABE7C4499BBE49C347B24F275BAE08` |

- The T-004 candidate delta list is confined to those 15 source/test paths plus its report/log artifacts. The current worktree also contains extensive unrelated dirty WIP and protected artifacts. T-001 recorded the scoped pre-existing paths and summarized protected global work, but did not retain a complete root status snapshot; I therefore do not claim that the abbreviated T-004 ledger proves every unrelated WIP path unchanged. T-005 itself changed only its report/log outputs and normal Gradle build outputs.
- No UI, route, stock-question, recipe, Room schema, platform, or dataset change is part of this candidate. The original onboarding stock-interrogation replacement and later separate KPKN alternative remain outside this foundation review's scope; this candidate alone does not complete those later plan items.

## LIMITATIONS_AND_RISKS

- Runtime assertions are unavailable because test Kotlin compilation fails before execution. No test outcome is inferred from source assertions.
- Candidate preservation is verified by hashes, but the abbreviated WIP-relative delta is insufficient to establish that all omitted old sections were untouched.
- T-004's UI did not provide a user-facing categorical entry control; that is explicitly outside this foundation task. The static production path is exercised by the newly added tests only when a draft already has availability.

## BLOCKER

Candidate test compilation fails on four unresolved `SetupTrainingOptions` references. This diagnosed blocker prevented the one authorized test attempt. No source fix or retry was made.

## DETAILED_REPORT

### Finding 1 — new test target does not compile

- **Expected:** `compileBaseDebugUnitTestKotlin` completes so the authorized suites can execute.
- **Observed:** Gradle exits 1 at `SetupAvailabilitySeedTest.kt:90,110,126,134` with unresolved `SetupTrainingOptions`.
- **Location / reproducible check:** `android-native/app/src/test/java/com/example/kpkn/screens/onboarding/SetupAvailabilitySeedTest.kt` lacks an import for the alias declared at `android-native/app/src/main/java/com/example/kpkn/domain/onboarding/SetupTrainingOptions.kt:33`. Reproduced by the authorized compile command above; stderr is `T-005-compile.stderr.log`.
- **Candidate identity:** new T-004 test file, candidate SHA-256 `6AC6C444198678FE2544A88B541F40C74AABE7C4499BBE49C347B24F275BAE08`; absent in T-001 baseline.

### Finding 2 — explicit confirmation of a seeded suggestion is not persisted

- **Expected:** a new draft seeded with a stored gym-category suggestion remains undeclared before interaction, but once the user explicitly confirms that suggested availability, the Settings patch persists the confirmed categories.
- **Observed:** `SetupWizardDraft.confirmCurrentStep` records an untouched step as `SUGGESTED` / `ESTIMATED` (`SetupWizardModels.kt:549-552`) and writes the mirror as `SUGGESTED_ACCEPTED` (`:599`); it does not add the step to `declaredSteps`. `isStepDeclared` only recognizes `declaredSteps` or a legacy mirror whose source is `DECLARED` (`SetupWizardModels.kt:325-330`). `buildSettingsPatch` gates availability solely on `draft.isStepDeclared(HOME_EQUIPMENT)` and returns `Unchanged` otherwise (`SetupWizardViewModel.kt:1656-1662`). Therefore a confirmed but untouched suggestion is indistinguishable from an unconfirmed suggestion at this persistence gate and is discarded.
- **Reproducible source trace:** initialize a new draft with non-empty `Settings.equipmentAvailability`; confirm HOME_EQUIPMENT without editing it; observe that the mirror is `SUGGESTED_ACCEPTED` while `isStepDeclared` remains false; invoke the builder; the resulting field is `SetupPatchField.Unchanged`, not `Set(the confirmed suggestion)`. This was independently derived from production code; the candidate's tests do not contain this post-confirmation case and did not execute.
- **Candidate identity:** `SetupWizardViewModel.kt` candidate SHA-256 `F5F72211EDEB6F44845CE23E3375405F08721E2D5223C1F762378DC819DB5849`; `SetupWizardModels.kt` candidate SHA-256 `A61AF84A9E3D1914AE66D4DDA2F4122250200DDF7AF999939EB21A80211EBE7D`.

### Positive static evidence and boundaries

- The eleven enum categories map one-to-one to the agreed closed tokens; explicit empty beats legacy `general_gym` and inventory in the selection projection. Null availability retains the old projection path.
- Categorical `MACHINES` reaches approved native machine variants with either null or unrelated invalid stock; the exact authored machine guard remains unchanged and does not accept bare `machine`. The T-004 fixed-recipe test snapshots the authored program and asserts the missing exact machine requirement.
- Selection validation skips only `inventoryHonestyReasons` when availability is non-null. AUTO confirmation, priority constraints, and warm-up validation remain in the shared validation path.
- Settings resolution preserves explicit numeric stock precedence and raw legacy bar/plate fields; category-only Settings resolve to unknown stock rather than guessed quantities. The stock and availability patch fields remain separate.
- T-004 introduced no kg/count/range fields in `EquipmentAvailability` and no recipe/prescription change. No change to the stock inventory model, Room schema, or backup envelope was listed.
