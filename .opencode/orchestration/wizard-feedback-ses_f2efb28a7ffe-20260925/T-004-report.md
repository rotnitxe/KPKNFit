# T-004 / v1 — implementation handoff

**TASK_ID / CONTRACT_VERSION / ROLE:** T-004 / v1 / WORKER  
**STATUS:** SUBMITTED  
**INPUT_BASELINE:** `HEAD=ac3ff1c88d8843aca986d0b5600351b8ff1303cc` plus protected working-tree WIP. T-001 recorded fingerprints matched for scoped targets with supplied fingerprints; initial pre-edit hashes for all edited existing targets are listed below.  
**OUTPUT_CANDIDATE:** Current working tree = captured WIP plus this scoped implementation. No commit created; candidate identity is the per-file post-edit SHA-256 set below.

## ACTUAL_ACTIONS

- Read T-001/T-002 and T-003 v1/v2, including the accepted architecture decisions and exact-machine/load boundaries.
- Ran the exact existing targeted Gradle tests before edits. The process reached Kotlin/main compilation but exceeded the authorized 300-second timeout before the unit-test task reported results.
- Added categorical availability and wired real native selection, settings stock resolution, setup patching, draft seeding, and footprint invalidation. Added regression coverage only in the authorized test targets.
- Ran the exact final targeted command with both new test names. It again exceeded 300 seconds during compilation, before any unit-test result. No retries, longer timeout, or weakened filters were used.
- After that timeout, updated only two `SimpleCyclePersonalizer.kt` comments so they accurately describe categorical precedence; no executable logic changed after the final attempt.
- `git diff --check` returned no whitespace errors (Git emitted only existing LF/CRLF working-copy warnings). No Room, UI route, inventory-question, catalog, recipe, platform, credential, or asset files were edited.

## ARTIFACTS

- `T-004-report.md` — this handoff and hash manifest.
- `T-004-wip-diff.md` — candidate-only delta ledger anchored to pre-edit WIP hashes, rather than a HEAD-only diff.
- `T-004-baseline.log` and `T-004-final.log` — complete captured Gradle output up to each tool timeout.

## ACCEPTANCE_EVIDENCE

- **AC-1 — NOT_RUN:** `TrainingOptions.effectiveEquipment` now makes non-null availability authoritative, always emits `bodyweight`, and projects the closed category set. The exhaustive projection/empty-plus-legacy-stock assertions are in `EquipmentAvailabilityTest` and `EffectiveEquipmentContractTest`; execution did not reach tests.
- **AC-2 — NOT_RUN:** Production `SimpleCyclePersonalizer.personalize` now permits broad `MACHINES` categories for native variants with null or non-null stock, while exact legacy inventory mode and `missingFixedRecipeEquipment` remain strict. Regressions exercise the real personalizer and authored guard in `EffectiveEquipmentContractTest` and `FixedRecipeEquipmentCompatibilityTest`; execution did not reach tests.
- **AC-3 — NOT_RUN:** `Settings.resolvedEquipmentInventory()` preserves explicit inventory precedence, returns empty/unknown stock for category-only settings, and retains legacy fallback only when availability is null. Production warm-up materialization and unchanged raw fields are covered by `SettingsEquipmentInventoryResolutionTest`; execution did not reach tests.
- **AC-4 — NOT_RUN:** Patch application uses `Unchanged` for unanswered/suggested values and `Set(EquipmentAvailability(emptySet()))` for explicit none; new drafts seed Settings availability without declaration, restored null drafts are not merged, preview footprints distinguish null from empty, and backup JSON tests cover absent-null vs empty. Regression cases are present in `SetupAvailabilitySeedTest`, `SetupActivationContractTest`, and `SettingsJsonBackupTest`; execution did not reach tests.
- **AC-5 — PASS (scope/integrity only):** Edits are confined to authorized source/test targets and T-004 report/log outputs. Pre/post hashes are recorded below; `git diff --check` reported no whitespace errors. This does not imply unit-test success.

## VALIDATION

Commands, both run from `android-native/` with `timeout: 300000`:

1. **Pre-edit:** `.\gradlew.bat --no-daemon --console=plain --warning-mode=summary testBaseDebugUnitTest --tests *.EffectiveEquipmentContractTest --tests *.FixedRecipeEquipmentCompatibilityTest --tests *.SettingsEquipmentInventoryResolutionTest --tests *.SetupActivationContractTest --tests *.SettingsJsonBackupTest`
2. **Post-edit:** same command plus `--tests *.EquipmentAvailabilityTest --tests *.SetupAvailabilitySeedTest`.

**Actual outcomes:** both tool invocations timed out at 300 seconds; neither produced a unit-test task summary, test count, or test exit code. Unit tests: **NOT RUN / no result reported**, not PASS. The first log reaches `bundleBaseDebugClassesToRuntimeJar`; the final log ends in main Kotlin compilation warnings. The only source edit after the final attempt was comment wording in `SimpleCyclePersonalizer.kt`, with no executable change. No runtime/device claims are made. `git diff --check` completed without a whitespace-error report.

## PRE-EDIT → CANDIDATE SHA-256

| Scoped path | Pre-edit SHA-256 | Candidate SHA-256 |
|---|---|---|
| `android-native/app/src/main/java/com/example/kpkn/data/models/EquipmentAvailability.kt` | absent | `97F1F6B32ED9128288DD8088521FEAABED8170D01753B0FCC1E482F23ACF538A` |
| `android-native/app/src/main/java/com/example/kpkn/data/models/Settings.kt` | `62C47C1FA0CF405F708B2A7F8BA79660B80312133CA2F75B24FEE5EE7320B6C7` | `BA3306D0A3347A8E292D529F53C2EBA710915CA8336E2284DDEA99320102669A` |
| `android-native/app/src/main/java/com/example/kpkn/data/onboarding/SetupSettingsPatch.kt` | `C10A0C0C2716A3D8A272A138296BF4BFB4DBF1DC327D75A883151B50F74C7637` | `C0240671634E1E3F2C57E12AF99A190F3BC0AC35375B38FA97633F98C93E219E` |
| `android-native/app/src/main/java/com/example/kpkn/domain/training/TrainingOptions.kt` | `77D12D37F5EF19992BBC7F15E191E694D867A8A8F497E04A7CE8AF020F00587D` | `18FABB2E1E01CA3C269A00A67FCD5489B7B4C05328A1E450B34209DEDC67E4B0` |
| `android-native/app/src/main/java/com/example/kpkn/domain/training/SimpleCyclePersonalizer.kt` | `C0E82B0371D4F17C81DDF0A000C4F4C0C2BAC09C86FB156F3D9E71A90F1981EC` | `87F9B42CE428B1D013E640726905D01353162F19D8996CF5316DC074D48EE2D1` |
| `android-native/app/src/main/java/com/example/kpkn/domain/onboarding/SetupStepGraph.kt` | `327220AFC71F458A37FB78AF7FAD6E86193ED6B300BE45B99CDA9B81F0FB8AB6` | `F5EB26E047602AF5DC23B20AE3B0F33BEC4B6851A536CA6B147BFE01ED295773` |
| `android-native/app/src/main/java/com/example/kpkn/screens/onboarding/SetupWizardModels.kt` | `424F8B63742DE9BDE4A6FB1EA3F80DC2404D98E0FD5E63CAC2551C008C73018B` | `A61AF84A9E3D1914AE66D4DDA2F4122250200DDF7AF999939EB21A80211EBE7D` |
| `android-native/app/src/main/java/com/example/kpkn/screens/onboarding/SetupWizardViewModel.kt` | `B27066397C5BB6713354680562DADBD30C1404237AE0CC3912DB38EA0BF26C6C` | `F5F72211EDEB6F44845CE23E3375405F08721E2D5223C1F762378DC819DB5849` |
| `android-native/app/src/test/java/com/example/kpkn/domain/training/EffectiveEquipmentContractTest.kt` | `ECC85D8592E9551B9B23399809F9F3BE4FC3F806808D8F9A20BDEBFC49334129` | `415E6C015BF28863397EF030CC85B91266497A9BD64C5701199098850C58B136` |
| `android-native/app/src/test/java/com/example/kpkn/domain/training/FixedRecipeEquipmentCompatibilityTest.kt` | `90EB7838A848C4052FBA306CDED419E5F88105772AEC495121256E53CF8CB50E` | `8D58EF7BBFE327C245D8040CFCD4289070A3AF60027E7DE9BBEA09A1BF31F714` |
| `android-native/app/src/test/java/com/example/kpkn/data/models/SettingsEquipmentInventoryResolutionTest.kt` | `BFC93137D3A49CDB2972CCBB24284F2FD30FD818227462294887C8547E517284` | `C01A7CAAAFB6F50CA85169F76F613141C90279FFDA8627A525C4EB8282E01B3C` |
| `android-native/app/src/test/java/com/example/kpkn/data/models/EquipmentAvailabilityTest.kt` | absent | `8164647E9B1AC8333C95984D3ADADC913C78BFD5F6B3FEBAAD84F6CB30CC00FA` |
| `android-native/app/src/test/java/com/example/kpkn/data/onboarding/SetupActivationContractTest.kt` | `EC28A54EBE877CCCA32A90DA5CB6EF99788935E68EB651B098FE9FF4A59AE2A5` | `EB6DF1BB476C4616527CD227FBC7D0A0A701B7E71E04A8D2B371071E94A688C1` |
| `android-native/app/src/test/java/com/example/kpkn/data/settings/SettingsJsonBackupTest.kt` | `74CB8DE8E39C0BB7D3FE72787A1C80668DF9FBED00C83F71F82C94FF43ABD3C9` | `6B234AFA01F1C809234E4999DA4BA3980C4D00A0358E3AEA879298C7C62B70EE` |
| `android-native/app/src/test/java/com/example/kpkn/screens/onboarding/SetupAvailabilitySeedTest.kt` | absent | `6AC6C444198678FE2544A88B541F40C74AABE7C4499BBE49C347B24F275BAE08` |

## SCOPE_CHECK

All prior dirty WIP was retained; pre-existing `SetupSettingsPatch.kt` inventory and nutrition patch code was extended in place. Changed production/test paths are only those listed in the hash table plus the two new model/test files. The two report/log artifacts are under the authorized T-004 output folder.

## LIMITATIONS_AND_RISKS

Selection and stock remain independent: availability does not write numeric stock, and it emits neither blanket `general_gym` nor exact machine-config tokens. Legacy null-availability behavior and fixed authored requirements remain intact in source and regression cases. Room schema stays unchanged because Settings/drafts use JSON serialization defaults. Neither test invocation reached test execution before its contract timeout; no test result or runtime behavior is claimed.

## BLOCKER

None to implementation submission. Validation is incomplete solely because both authorized Gradle invocations timed out before test results; this is explicitly **NOT RUN**, not PASS.

## DETAILED_REPORT

The architecture keeps two separate durable facts: nullable categorical availability controls native exercise selection, while nullable numeric inventory continues to govern reachable loads. Explicit categories win over both legacy equipment IDs and any stock rows for selection; the resolver independently gives non-null stock precedence, then maps category-only Settings to unknown stock, then falls back to old Settings defaults only when availability is absent. Fixed authored machine requirements still consume their original exact-config guard. Setup patching applies to the latest Settings row and changes availability independently of stock; new drafts copy a suggestion without declaring it, and restored drafts are not merged. The data-class JSON defaults preserve compatibility without a Room migration.
