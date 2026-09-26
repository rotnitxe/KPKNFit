# T-006 / v1 — original edit record recovery

**TASK_ID / CONTRACT_VERSION / ROLE:** T-006 / v1 / READER  
**STATUS:** BLOCKED  
**INPUT_BASELINE:** `HEAD=ac3ff1c88d8843aca986d0b5600351b8ff1303cc` plus protected pre-existing working-tree WIP; pre-edit SHA-256 values are recorded in `T-004-report.md`.  
**OUTPUT_CANDIDATE:** The immutable archived T-004 copies under `T-004-recovery/candidate/`, whose 15 SHA-256 values matched the candidate manifest in `T-004-report.md` during this task.

## ACTUAL_ACTIONS

- Read the specified T-004 implementation and recovery reports and used only the archived candidate copies for source bytes.
- Inspected OpenCode database schemas read-only. In `C:\Users\valen\.local\share\opencode\opencode.db`, the exact worker session `ses_f25ab534fffeZj8ycBOWU0XW5Y` exists for directory `C:/Users/valen/Documents/KPKNFit`.
- Located chronological `patch` tool inputs in that session's `session_message` records (`data.content[].state.input.patchText`). Extracted sequence/call metadata, completion state, patch-input SHA-256, and the exact in-scope paths; see `record-index.md`.
- Excluded failed patch call sequence 543 from replay. Applied-record candidates through sequence 995 include the comment-only `SimpleCyclePersonalizer.kt` update recorded by T-004. Later source-correction edits were not included.
- Verified all 15 archived candidate-copy hashes against the T-004 candidate manifest before attempting reverse application.
- Reversed completed patch records in memory against the archived candidate. Exact pre-edit SHA-256 matches were obtained for `Settings.kt` and `SetupSettingsPatch.kt`; the inverse pass stopped at `TrainingOptions.kt` because multiple same-path update sections in one call were not processed in reverse section order. No guessed reconstruction was accepted.
- Did not write source, test, index, configuration, database, snapshot, or candidate files. No tests/builds were run.

## ARTIFACTS

- `record-index.md` — provenance and metadata for the in-scope original patch calls, including patch-input SHA-256 values and affected paths. **It is an index only; the raw `patchText` payloads were not copied into this evidence directory.**
- `recovery-report.md` — this report.
- No recovered preimage copies or complete baseline-to-candidate delta were produced; those required artifacts remain blocked.

## ACCEPTANCE_EVIDENCE

### AC-1 — original records located

PASS for availability/provenance: original patch-tool records are accessible in the read-only OpenCode database, scoped to the exact worker session. The candidate-boundary source edits are the successful records through sequence 995. The complete record index is in `record-index.md`. Automatic snapshots were not needed or used.

### AC-2 — exact preimage hash checks

The current original-record inverse pass verified:

| Path | Recorded pre-edit SHA-256 | Recovered SHA-256 | Result |
|---|---|---|---|
| `android-native/app/src/main/java/com/example/kpkn/data/models/Settings.kt` | `62C47C1FA0CF405F708B2A7F8BA79660B80312133CA2F75B24FEE5EE7320B6C7` | `62C47C1FA0CF405F708B2A7F8BA79660B80312133CA2F75B24FEE5EE7320B6C7` | PASS |
| `android-native/app/src/main/java/com/example/kpkn/data/onboarding/SetupSettingsPatch.kt` | `C10A0C0C2716A3D8A272A138296BF4BFB4DBF1DC327D75A883151B50F74C7637` | `C10A0C0C2716A3D8A272A138296BF4BFB4DBF1DC327D75A883151B50F74C7637` | PASS |

The inverse pass also reversed the recorded addition of `EquipmentAvailability.kt` to an absent baseline. The previously documented three hash-matching preimages in `T-004-recovery/recovery-report.md` remain that report's evidence; this pass did not independently re-run their inverses.

Seven of the nine paths previously unresolved by T-004 remain unresolved in this task:

1. `android-native/app/src/main/java/com/example/kpkn/domain/training/TrainingOptions.kt` — replay stopped here due same-call section ordering.
2. `android-native/app/src/main/java/com/example/kpkn/domain/training/SimpleCyclePersonalizer.kt`
3. `android-native/app/src/main/java/com/example/kpkn/domain/onboarding/SetupStepGraph.kt`
4. `android-native/app/src/main/java/com/example/kpkn/screens/onboarding/SetupWizardModels.kt`
5. `android-native/app/src/test/java/com/example/kpkn/data/models/SettingsEquipmentInventoryResolutionTest.kt`
6. `android-native/app/src/test/java/com/example/kpkn/data/onboarding/SetupActivationContractTest.kt`
7. `android-native/app/src/test/java/com/example/kpkn/data/settings/SettingsJsonBackupTest.kt`

No mismatching or approximate preimage is claimed as PASS.

### AC-3 — evidence-only scope and candidate stability

The only workspace writes made by this task are the two evidence reports in the new T-006 evidence directory. Archived candidate copies were read only; all 15 matched their recorded candidate hashes before reverse replay. No source or test files were written or modified by this task. No private reasoning/message prose or secrets were selected or reported.

## VALIDATION

- Archived candidate hashes: **15/15 matched** the T-004 candidate manifest.
- Authenticated existing-file preimages newly checked in this pass: **2 exact SHA-256 matches**.
- Recorded absent baseline reversed in this pass: **1** (`EquipmentAvailability.kt`). The other two absent baselines remain as documented by T-004; their reverse was not reached in this pass.
- Original patch-record call metadata: **9 completed patch records** and **1 failed, non-applied patch record** affecting scoped paths were indexed.
- Full baseline-to-candidate delta: **not produced**.
- Tests/builds: not run; this was a read-only evidence recovery.

## SCOPE_CHECK

- Database connections used SQLite `mode=ro`; exact session ID was used for session-record queries. No database writes or snapshot operations occurred.
- Candidate source/test copies were not altered. No live source file was read or modified.
- One storage-inventory command inadvertently enumerated filenames under `storage/session_diff` for unrelated session IDs. It printed filenames/length metadata only; no unrelated session records or file contents were opened. This was broader metadata enumeration than intended and is disclosed here.
- No authentication files, credentials, environment values, private reasoning transcripts, tests, Gradle, ADB, or agents were used.

## LIMITATIONS_AND_RISKS

Original patch records are available and authenticated by database provenance, but this bounded attempt did not finish replaying them or persist raw patch payloads, recovered preimage copies, and the full delta under the authorized evidence directory. The unresolved seven paths therefore remain blocking evidence gaps. The previously failed inverse attempt was not repeated as a guess; the new route used original tool inputs and stopped on a concrete replay-order defect. The storage metadata enumeration noted above is a scope deviation, not evidence about the source files.

## BLOCKER

The exact original patch inputs were located, but the inverse replay routine stopped on `TrainingOptions.kt` before validating the remaining seven previously unresolved paths. The contract's bounded storage-discovery/query budget was exhausted. Consequently, this handoff cannot claim a complete authenticated delta or complete recovery artifacts.

## DETAILED_REPORT

See `record-index.md` for original sequence numbers, call IDs, tool statuses, patch-input hashes, and exact affected source/test paths. The two verified preimages above match the pre-edit hashes in `T-004-report.md` byte-for-byte by SHA-256. The prior T-004 report continues to govern the other three already hash-verified reconstructions and the three recorded absent baselines. This task does not accept or change any implementation.
