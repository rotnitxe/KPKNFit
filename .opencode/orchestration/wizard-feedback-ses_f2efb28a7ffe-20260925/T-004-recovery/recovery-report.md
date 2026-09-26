# T-004 evidence recovery / v2

**TASK_ID / CONTRACT_VERSION / ROLE:** T-004 evidence recovery / v2 / READER  
**STATUS:** BLOCKED  
**INPUT_BASELINE:** `HEAD=ac3ff1c88d8843aca986d0b5600351b8ff1303cc` plus pre-existing working-tree WIP, identified by the pre-edit SHA-256 manifest in `T-004-report.md`.  
**OUTPUT_CANDIDATE:** The T-004 candidate identified by the candidate SHA-256 manifest in `T-004-report.md`.

## ACTUAL_ACTIONS

- Read only the three specified T-004 reports and the fifteen manifest source/test paths. Read-only `git show` was limited to exact manifest paths where useful.
- Verified all fifteen live candidate hashes against `T-004-report.md` before reconstruction. Every hash matched.
- Copied the fifteen current candidate files byte-for-byte under `candidate/` in this recovery directory.
- Made one deterministic inverse reconstruction pass across the twelve existing paths, removing/reverting only the ledger-identified availability additions, replacements, imports, gates, and tests. The three paths recorded as absent were not reconstructed.
- Made a second inverse pass only where a concrete inverse correction was identified. Each preimage was accepted only when its SHA-256 exactly matched the previously recorded pre-edit value.
- Rechecked all fifteen live candidate hashes against both the recorded candidate manifest and the evidence copies after reconstruction and delta generation. Every hash still matched.
- Generated the complete unified delta only for hash-verified preimages and the three paths with an explicitly absent baseline. No tests or builds were run.

## ARTIFACTS

- `candidate/` — byte-preserving copies of all fifteen current candidate files.
- `verified-preimages/` — the three reconstructed preimages whose SHA-256 matches the recorded pre-edit hash exactly.
- `full-recovered-delta.patch` — all hunks for those three verified existing-file pairs, plus full additions for the three paths whose recorded baseline was absent; 485 lines, SHA-256 `C3EE40BBD2F946E73131623D11DD7B6130A3380FCBD069F2A51BC2B2E056759B`.
- `recovered-preimages/` — first-pass inverse outputs. Only the three hash-matching paths listed below are verified; the other nine are not accepted as preimages or used in the delta.
- `attempt-2/` — second-pass copies for four mismatched paths; none matched the recorded pre-edit SHA-256, so none is used in the delta.
- `recovery-report.md` — this handoff and per-path evidence record.

## ACCEPTANCE_EVIDENCE

### AC-1 — PARTIAL; unresolved evidence blocker

For each previously existing path, acceptance required the reconstructed copy to equal the recorded pre-edit SHA-256. Three paths passed exactly and are labelled **recovered and hash-verified preimage**. This proves exact content identity to the recorded hash; these are reconstructions, not originally captured snapshots.

| Existing path | Recorded pre-edit SHA-256 | Candidate SHA-256 | Result |
|---|---|---|---|
| `android-native/app/src/main/java/com/example/kpkn/screens/onboarding/SetupWizardViewModel.kt` | `B27066397C5BB6713354680562DADBD30C1404237AE0CC3912DB38EA0BF26C6C` | `F5F72211EDEB6F44845CE23E3375405F08721E2D5223C1F762378DC819DB5849` | **Recovered and hash-verified preimage** |
| `android-native/app/src/test/java/com/example/kpkn/domain/training/EffectiveEquipmentContractTest.kt` | `ECC85D8592E9551B9B23399809F9F3BE4FC3F806808D8F9A20BDEBFC49334129` | `415E6C015BF28863397EF030CC85B91266497A9BD64C5701199098850C58B136` | **Recovered and hash-verified preimage** |
| `android-native/app/src/test/java/com/example/kpkn/domain/training/FixedRecipeEquipmentCompatibilityTest.kt` | `90EB7838A848C4052FBA306CDED419E5F88105772AEC495121256E53CF8CB50E` | `8D58EF7BBFE327C245D8040CFCD4289070A3AF60027E7DE9BBEA09A1BF31F714` | **Recovered and hash-verified preimage** |

The following three paths have **recorded absent baselines**, so no preimage was required or invented:

- `android-native/app/src/main/java/com/example/kpkn/data/models/EquipmentAvailability.kt`
- `android-native/app/src/test/java/com/example/kpkn/data/models/EquipmentAvailabilityTest.kt`
- `android-native/app/src/test/java/com/example/kpkn/screens/onboarding/SetupAvailabilitySeedTest.kt`

The remaining nine existing preimages are **UNRESOLVED**. Neither deterministic inverse pass reproduced the recorded hash. Their reconstructed contents are not evidence and are excluded from `full-recovered-delta.patch`.

| Unresolved path | Recorded pre-edit SHA-256 | Last attempted reconstruction SHA-256 | Inverse passes |
|---|---|---|---:|
| `android-native/app/src/main/java/com/example/kpkn/data/models/Settings.kt` | `62C47C1FA0CF405F708B2A7F8BA79660B80312133CA2F75B24FEE5EE7320B6C7` | `3327BA17CE41D08D153E58A05E95AB2AD4519E7CEF5631A12B6938E9289443DF` | 1 |
| `android-native/app/src/main/java/com/example/kpkn/data/onboarding/SetupSettingsPatch.kt` | `C10A0C0C2716A3D8A272A138296BF4BFB4DBF1DC327D75A883151B50F74C7637` | `C95E948B8732544107167DA79F6D8278A4C04C545F9DD8FAAF5055628D0F3197` | 2 |
| `android-native/app/src/main/java/com/example/kpkn/domain/training/TrainingOptions.kt` | `77D12D37F5EF19992BBC7F15E191E694D867A8A8F497E04A7CE8AF020F00587D` | `FC919D3237FEA215B74AB3CDD508F0E9A3CD3CC53C2F1435422A6CE2B724B104` | 2 |
| `android-native/app/src/main/java/com/example/kpkn/domain/training/SimpleCyclePersonalizer.kt` | `C0E82B0371D4F17C81DDF0A000C4F4C0C2BAC09C86FB156F3D9E71A90F1981EC` | `08C810808AA2B5FDE98275F977EF7B6CA9F53077C9FDCFA55AFF02E36940E605` | 1 |
| `android-native/app/src/main/java/com/example/kpkn/domain/onboarding/SetupStepGraph.kt` | `327220AFC71F458A37FB78AF7FAD6E86193ED6B300BE45B99CDA9B81F0FB8AB6` | `6F7E09E17A53EFF571FECB100D8C43187F5A52480C2972334AD182CDA99E715A` | 1 |
| `android-native/app/src/main/java/com/example/kpkn/screens/onboarding/SetupWizardModels.kt` | `424F8B63742DE9BDE4A6FB1EA3F80DC2404D98E0FD5E63CAC2551C008C73018B` | `EA248ADDB9E128E6F59A4EBDED88AE00EDBA4936D6B914E175046DD1D79A0E45` | 1 |
| `android-native/app/src/test/java/com/example/kpkn/data/models/SettingsEquipmentInventoryResolutionTest.kt` | `BFC93137D3A49CDB2972CCBB24284F2FD30FD818227462294887C8547E517284` | `27F155464287A438284E2E16A582CF41C7D1A6007B26A59EB149B36B29E206A2` | 2 |
| `android-native/app/src/test/java/com/example/kpkn/data/onboarding/SetupActivationContractTest.kt` | `EC28A54EBE877CCCA32A90DA5CB6EF99788935E68EB651B098FE9FF4A59AE2A5` | `052ABA78733A8E5BCCC210F79DB315982A43860DEC61FA7BEF628BB4C46BE577` | 1 |
| `android-native/app/src/test/java/com/example/kpkn/data/settings/SettingsJsonBackupTest.kt` | `74CB8DE8E39C0BB7D3FE72787A1C80668DF9FBED00C83F71F82C94FF43ABD3C9` | `3449D4F66F40D98961F1A53D44FDFCFAD124B039E5DBF27AE3EB694812CFDB23` | 2 |

### AC-2 — PASS for verified scope; full 15-path delta remains incomplete

`full-recovered-delta.patch` contains all hunks for the three hash-verified existing-file pairs and complete `/dev/null` additions for the three paths whose baseline was absent. All fifteen current production/test hashes still equal the candidate hashes recorded in `T-004-report.md`. A complete pre-existing-WIP delta cannot be claimed for the nine unresolved paths.

## VALIDATION

- Initial candidate precondition: **15/15 hashes matched** the recorded candidate manifest.
- Final source/snapshot stability: **15/15 live hashes and 15/15 candidate-copy hashes matched** the recorded candidate manifest.
- Recovered preimages: **3/12 existing paths hash-matched exactly**; nine did not. No line-ending-normalized comparison was accepted.
- Absent baselines: **3/3** handled without a preimage.
- Unified delta: includes all hunks for the three hash-verified pairs and complete additions for the three absent-baseline files.
- Tests/builds: not run; this was an evidence-only task.

## SCOPE_CHECK

- No source or test path was written. Candidate files were read and copied only.
- All created artifacts are inside this new `T-004-recovery/` directory.
- No prior reports/logs, Git index, database, settings, or other user data were changed.
- No tests, Gradle, process control, ADB, or agents were used.

## LIMITATIONS_AND_RISKS

The abbreviated ledger plus the available current bytes were sufficient to recover and verify three existing preimages, but not to recreate the remaining nine bytes exactly. A hash mismatch is unresolved evidence, not an approximate PASS; the failed reconstructions must not be used to claim preserved WIP or produce a full delta. No claim is made that any reconstructed copy was originally captured.

## BLOCKER

Missing exact pre-edit content for the nine unresolved paths. Their candidate-specific full deltas against the recorded WIP baseline cannot be authenticated from the authorized evidence available in this task.

## DETAILED_REPORT

This recovery proves exact pre-edit content identity only for the three listed matches by equality with their previously recorded SHA-256 values. `full-recovered-delta.patch` is correspondingly limited to those verified preimages plus the three new-file additions with recorded absent baselines. The other nine paths remain explicitly unresolved; the evidence copy folder records inverse attempts but does not promote any mismatching output to a preimage. Candidate source/test hashes remained stable across the work.
