# T-004 correction round 1 handoff

**TASK_ID / CONTRACT_VERSION / ROLE:** T-004 / v2 correction / WORKER  
**STATUS:** SUBMITTED  
**INPUT_BASELINE:** Original 15-file T-004 candidate, verified against both `T-004-report.md` and byte-preserving `T-004-recovery/candidate/` before correction edits.  
**OUTPUT_CANDIDATE:** That candidate with the three authorized correction targets changed; full 15-path input/output SHA-256 manifest is below and in `T-004-r1-candidate.sha256`.

## ACTUAL_ACTIONS

- Verified all 15 live T-004 candidate files matched both the report manifest and `T-004-recovery/candidate/` before editing.
- Before editing, copied the three correction targets as raw bytes into `T-004-r1-before/` and verified each snapshot hash against the live candidate and its recovery copy. The hashes are in `T-004-r1-before/manifest.sha256`.
- Added the missing real import of `com.example.kpkn.domain.onboarding.SetupTrainingOptions`.
- Added regressions that call the actual `SetupWizardDraft.confirmCurrentStep(HOME_EQUIPMENT)` and the real `SetupWizardViewModel.buildSettingsPatch` seam. They check that an untouched suggestion is `Unchanged`, confirmation records `SUGGESTED` without making the step declared, and the confirmed suggestion becomes `Set(categories)`. The empty suggestion case is also covered. Existing explicit-empty and null/unanswered assertions remain.
- Added a Room-backed `SetupCommitCoordinator` receipt replay regression. It changes Settings availability independently after the first receipt and checks replay preserves that change, preserves numeric stock, and does not duplicate program or receipt rows.
- Ran the confirmed-suggestion tests before the production fix. Unit-test compilation succeeded and both new confirmation assertions showed the expected RED (`Unchanged` instead of `Set`).
- Updated only `SetupWizardViewModel.buildSettingsPatch` to recognize the existing persistable per-step provenance as well as the existing declared-answer gate. `isStepDeclared` was not changed.
- Ran the final separated unit-test compilation and the seven requested suites. Saved all seven final XML files and their test totals.
- Generated a complete actual before/after unified diff from the byte-preserving correction snapshots to the final live files. The diff covers exactly the three authorized paths.

## ARTIFACTS

- `T-004-r1-before/` — byte-preserving pre-correction snapshots for all three edited paths plus `manifest.sha256`.
- `T-004-r1-complete-delta.patch` — complete before/after diff for the three edited paths; SHA-256 `782623FEBFC11766BD49E95984220EE06466226D399BA2F537E26F6A4337DFCB` (169 lines, three file sections).
- `T-004-r1-candidate.sha256` — final SHA-256 values for all 15 T-004 candidate paths.
- `T-004-r1-test-xml/` — byte-preserving copies of all seven final test XML reports and `summary.txt`.
- `T-004-r1-prefx-compile.stdout.log`, `T-004-r1-prefx-compile.stderr.log` — compile log for the tests-only pre-fix candidate.
- `T-004-r1-prefx-redtest.stdout.log`, `T-004-r1-prefx-redtest.stderr.log` — pre-fix focused test log showing both expected failures.
- `T-004-r1-final-compile.stdout.log`, `T-004-r1-final-compile.stderr.log` — final separated compilation output.
- `T-004-r1-final-tests.stdout.log`, `T-004-r1-final-tests.stderr.log` — final seven-suite output.
- `T-004-r1-report.md` — this handoff.
- `T-004-recovery/candidate/` and the original T-004 reports were left unchanged.

## ACCEPTANCE_EVIDENCE

- **AC-1 — PASS:** The closed categorical projection and precedence regressions executed in `EquipmentAvailabilityTest` and `EffectiveEquipmentContractTest`; both suites passed.
- **AC-2 — PASS:** Native machine selection and exact authored-machine guard regressions executed in `EffectiveEquipmentContractTest` and `FixedRecipeEquipmentCompatibilityTest`; both suites passed.
- **AC-3 — PASS:** Numeric-stock precedence, category-only unknown load resolution, and legacy fallback regressions executed in `SettingsEquipmentInventoryResolutionTest`; the suite passed.
- **AC-4 — PASS:** The new pre-fix RED then final GREEN demonstrate confirmed suggestions persist without becoming declared; empty suggestions persist as explicit empty; null/unanswered remains `Unchanged`; explicit declared empty remains `Set`. The Room-backed receipt replay regression passed and confirmed later Settings availability, numeric stock, one program row, one receipt row, and the active-program row remain correct. The setup, backup, and JSON suites also passed.
- **AC-5 — PASS for this correction round:** The test sources compile; all seven suites completed; all 15 paths were verified against the original T-004 candidate before editing; 12 non-target files remain byte-identical to that candidate; three before snapshots match their original candidate hashes; and the complete correction delta is retained. The separate historical recovery report still identifies nine original T-004 preimages as unresolved; this round did not attempt to reconstruct them.

## VALIDATION

Commands ran from `android-native/` with stdout/stderr redirected to the owned log files and the owned launcher awaited directly. No broad process cleanup was used.

1. **Pre-fix test-source compilation:**

   ```powershell
   .\gradlew.bat --no-daemon --console=plain --warning-mode=summary compileBaseDebugUnitTestKotlin
   ```

   Exit **0**; the tests-only correction candidate compiled.

2. **Expected pre-fix RED observation:**

   ```powershell
   .\gradlew.bat --no-daemon --console=plain --warning-mode=summary testBaseDebugUnitTest --tests *.SetupAvailabilitySeedTest
   ```

   The suite XML was observed before the final run replaced Gradle's generated XML: **6 tests, 0 skipped, 2 failures, 0 errors**. Both failures were the new confirmed non-empty/empty suggestions expecting `Set(...)` but receiving `Unchanged`. The captured stdout includes both failing test names; Windows PowerShell surfaced the underlying nonzero Gradle result as a native-command error, so that pre-fix log has no final `BUILD FAILED` summary.

3. **Final separated test-source compilation:**

   ```powershell
   .\gradlew.bat --no-daemon --console=plain --warning-mode=summary compileBaseDebugUnitTestKotlin
   ```

   Exit **0**; Gradle reported `BUILD SUCCESSFUL in 4m 16s`.

4. **Final requested seven suites:**

   ```powershell
   .\gradlew.bat --no-daemon --console=plain --warning-mode=summary testBaseDebugUnitTest --tests *.EffectiveEquipmentContractTest --tests *.FixedRecipeEquipmentCompatibilityTest --tests *.SettingsEquipmentInventoryResolutionTest --tests *.SetupActivationContractTest --tests *.SettingsJsonBackupTest --tests *.EquipmentAvailabilityTest --tests *.SetupAvailabilitySeedTest
   ```

   Exit **0**; Gradle reported `BUILD SUCCESSFUL in 51s`. XML totals: **62 tests, 0 skipped, 0 failures, 0 errors**.

| Suite | Tests | Skipped | Failures | Errors |
|---|---:|---:|---:|---:|
| `EffectiveEquipmentContractTest` | 13 | 0 | 0 | 0 |
| `FixedRecipeEquipmentCompatibilityTest` | 10 | 0 | 0 | 0 |
| `SettingsEquipmentInventoryResolutionTest` | 5 | 0 | 0 | 0 |
| `SetupActivationContractTest` | 22 | 0 | 0 | 0 |
| `SettingsJsonBackupTest` | 4 | 0 | 0 | 0 |
| `EquipmentAvailabilityTest` | 2 | 0 | 0 | 0 |
| `SetupAvailabilitySeedTest` | 6 | 0 | 0 | 0 |
| **Total** | **62** | **0** | **0** | **0** |

## SCOPE_CHECK

Only these source/test paths changed:

- `android-native/app/src/main/java/com/example/kpkn/screens/onboarding/SetupWizardViewModel.kt`
- `android-native/app/src/test/java/com/example/kpkn/screens/onboarding/SetupAvailabilitySeedTest.kt`
- `android-native/app/src/test/java/com/example/kpkn/data/onboarding/SetupActivationContractTest.kt`

The other 12 paths from the original 15-file T-004 candidate retain their original candidate hashes. No Room/schema, catalog, recipe, UI, route, platform, asset, or stock-model file was changed.

## INPUT_BASELINE / OUTPUT_CANDIDATE SHA-256

| T-004 candidate path | Input baseline SHA-256 | Round-1 output SHA-256 |
|---|---|---|
| `android-native/app/src/main/java/com/example/kpkn/data/models/EquipmentAvailability.kt` | `97F1F6B32ED9128288DD8088521FEAABED8170D01753B0FCC1E482F23ACF538A` | `97F1F6B32ED9128288DD8088521FEAABED8170D01753B0FCC1E482F23ACF538A` |
| `android-native/app/src/main/java/com/example/kpkn/data/models/Settings.kt` | `BA3306D0A3347A8E292D529F53C2EBA710915CA8336E2284DDEA99320102669A` | `BA3306D0A3347A8E292D529F53C2EBA710915CA8336E2284DDEA99320102669A` |
| `android-native/app/src/main/java/com/example/kpkn/data/onboarding/SetupSettingsPatch.kt` | `C0240671634E1E3F2C57E12AF99A190F3BC0AC35375B38FA97633F98C93E219E` | `C0240671634E1E3F2C57E12AF99A190F3BC0AC35375B38FA97633F98C93E219E` |
| `android-native/app/src/main/java/com/example/kpkn/domain/training/TrainingOptions.kt` | `18FABB2E1E01CA3C269A00A67FCD5489B7B4C05328A1E450B34209DEDC67E4B0` | `18FABB2E1E01CA3C269A00A67FCD5489B7B4C05328A1E450B34209DEDC67E4B0` |
| `android-native/app/src/main/java/com/example/kpkn/domain/training/SimpleCyclePersonalizer.kt` | `87F9B42CE428B1D013E640726905D01353162F19D8996CF5316DC074D48EE2D1` | `87F9B42CE428B1D013E640726905D01353162F19D8996CF5316DC074D48EE2D1` |
| `android-native/app/src/main/java/com/example/kpkn/domain/onboarding/SetupStepGraph.kt` | `F5EB26E047602AF5DC23B20AE3B0F33BEC4B6851A536CA6B147BFE01ED295773` | `F5EB26E047602AF5DC23B20AE3B0F33BEC4B6851A536CA6B147BFE01ED295773` |
| `android-native/app/src/main/java/com/example/kpkn/screens/onboarding/SetupWizardModels.kt` | `A61AF84A9E3D1914AE66D4DDA2F4122250200DDF7AF999939EB21A80211EBE7D` | `A61AF84A9E3D1914AE66D4DDA2F4122250200DDF7AF999939EB21A80211EBE7D` |
| `android-native/app/src/main/java/com/example/kpkn/screens/onboarding/SetupWizardViewModel.kt` | `F5F72211EDEB6F44845CE23E3375405F08721E2D5223C1F762378DC819DB5849` | `347146233D7B19679139774F76346DE19C2953ED6423C5ABBC952BB86C011A22` |
| `android-native/app/src/test/java/com/example/kpkn/domain/training/EffectiveEquipmentContractTest.kt` | `415E6C015BF28863397EF030CC85B91266497A9BD64C5701199098850C58B136` | `415E6C015BF28863397EF030CC85B91266497A9BD64C5701199098850C58B136` |
| `android-native/app/src/test/java/com/example/kpkn/domain/training/FixedRecipeEquipmentCompatibilityTest.kt` | `8D58EF7BBFE327C245D8040CFCD4289070A3AF60027E7DE9BBEA09A1BF31F714` | `8D58EF7BBFE327C245D8040CFCD4289070A3AF60027E7DE9BBEA09A1BF31F714` |
| `android-native/app/src/test/java/com/example/kpkn/data/models/SettingsEquipmentInventoryResolutionTest.kt` | `C01A7CAAAFB6F50CA85169F76F613141C90279FFDA8627A525C4EB8282E01B3C` | `C01A7CAAAFB6F50CA85169F76F613141C90279FFDA8627A525C4EB8282E01B3C` |
| `android-native/app/src/test/java/com/example/kpkn/data/models/EquipmentAvailabilityTest.kt` | `8164647E9B1AC8333C95984D3ADADC913C78BFD5F6B3FEBAAD84F6CB30CC00FA` | `8164647E9B1AC8333C95984D3ADADC913C78BFD5F6B3FEBAAD84F6CB30CC00FA` |
| `android-native/app/src/test/java/com/example/kpkn/data/onboarding/SetupActivationContractTest.kt` | `EB6DF1BB476C4616527CD227FBC7D0A0A701B7E71E04A8D2B371071E94A688C1` | `4C0C779583D869B800AD8BDC33D3D4443FA121CC89CD3856BC4D5C4E19549B8F` |
| `android-native/app/src/test/java/com/example/kpkn/data/settings/SettingsJsonBackupTest.kt` | `6B234AFA01F1C809234E4999DA4BA3980C4D00A0358E3AEA879298C7C62B70EE` | `6B234AFA01F1C809234E4999DA4BA3980C4D00A0358E3AEA879298C7C62B70EE` |
| `android-native/app/src/test/java/com/example/kpkn/screens/onboarding/SetupAvailabilitySeedTest.kt` | `6AC6C444198678FE2544A88B541F40C74AABE7C4499BBE49C347B24F275BAE08` | `5CFFE3DCC7D052909C087226BD3ED4B9B7501552EE497FA31E820B77EC3CA376` |

## LIMITATIONS_AND_RISKS

The correction diff is complete against the byte-preserving original T-004 candidate copies. It is not a reconstruction of T-004's earlier pre-edit WIP. The separate `T-004-recovery/recovery-report.md` still records nine historical preimages as unresolved; this correction round did not attempt their restoration. The pre-fix targeted-test XML was superseded by the final Gradle run; its counters and failures were read before replacement, and the owned pre-fix stdout log retains the two failing test names.

## BLOCKER

None for the scoped correction and requested validation.

## DETAILED_REPORT

The persistence defect was that the patch builder recognized declared equipment but ignored a confirmed seeded suggestion. In this candidate, `confirmCurrentStep` records `SetupAnswerProvenance.SUGGESTED` in `stepProgress.answers` while leaving the step undeclared. The builder now checks that existing per-step persistable provenance in addition to the existing declared-answer path. An unconfirmed suggestion has no such answer record, so it remains `Unchanged`; nullable availability also remains `Unchanged`; a confirmed non-empty or empty suggestion becomes `Set(value)` without changing `isStepDeclared` semantics.

The receipt test exercises the actual Room-backed coordinator and Settings DAO. It commits availability with an activated program, changes availability independently afterward, replays the same receipt, and verifies the later availability and numeric stock survive with one program entity and one receipt. The final focused suites cover the original categorical selection/load/setup contracts together with these corrections.
